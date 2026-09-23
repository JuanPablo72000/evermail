package com.juanpablo.evermail.controller;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.facade.MailFacade;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.util.HtmlMail;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.events.EventTarget;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Reusable reader for the future inbox screen. All backend work stays off the FX thread. */
public final class MailReaderPane extends BorderPane implements AutoCloseable {
    private final MailFacade facade;
    private final Consumer<URI> openLink;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), Thread.ofPlatform().daemon().name("evermail-reader-", 0).factory());
    private final Label heading = new Label(), metadata = new Label(), status = new Label();
    private final TextArea text = new TextArea();
    private final WebView web = new WebView();
    private final Button plain = new Button("Ver texto"), formatted = new Button("Ver formato"), retry = new Button("Reintentar"), upgrade = new Button("Recuperar formato");
    private final PauseTransition timeout = new PauseTransition();
    private Task<?> pending;
    private UUID accountId, mailId;
    private long generation;
    private MailPresentation presentation;
    private boolean showingHtml, markedRead;

    public MailReaderPane(MailFacade facade, Runnable back, Consumer<URI> openLink) {
        this.facade = facade;
        this.openLink = openLink;
        Button previous = new Button("Volver");
        previous.setOnAction(e -> { clear(); back.run(); });
        plain.setOnAction(e -> showPlain());
        formatted.setOnAction(e -> renderHtml(Deadline.after(AppConstants.CONTENT_BUDGET)));
        retry.setOnAction(e -> { if (accountId != null) open(accountId, mailId); });
        upgrade.setOnAction(e -> loadBody(true));
        heading.setWrapText(true); metadata.setWrapText(true); status.setWrapText(true);
        setTop(new VBox(8, new FlowPane(8, 8, previous, plain, formatted, retry, upgrade), heading, metadata, status));
        text.setEditable(false); text.setWrapText(true);
        web.setContextMenuEnabled(false);
        web.getEngine().setJavaScriptEnabled(false);
        web.getEngine().setCreatePopupHandler(features -> null);
        web.getEngine().setConfirmHandler(message -> false);
        web.getEngine().getLoadWorker().stateProperty().addListener((obs, before, state) -> {
            if (!showingHtml) return;
            if (state == Worker.State.SUCCEEDED) {
                timeout.stop();
                attachLinks();
                setCenter(web);
                markRead();
            } else if (state == Worker.State.FAILED) {
                showPlain();
                status.setText("No se pudo mostrar el formato. Se muestra el texto disponible.");
            }
        });
        // No mail markup receives a URL, script bridge, popup or arbitrary browser navigation.
        clear();
    }

    public void open(UUID account, UUID mail) {
        checkThread();
        clear();
        accountId = account; mailId = mail;
        long token = generation;
        status.setText("Cargando encabezado…");
        Task<MailHeader> task = facade.openHeaderTask(account, mail);
        arm(AppConstants.OPEN_HEADER_BUDGET.toMillis(), token, "El encabezado tardó demasiado. Puedes reintentar.");
        task.setOnSucceeded(e -> {
            if (token != generation) return;
            timeout.stop();
            MailHeader header = task.getValue();
            heading.setText(header.getSubject());
            metadata.setText(header.getSenderName() + " <" + header.getSenderEmail() + "> · " + header.getOccurredAt());
            loadBody(false);
        });
        task.setOnFailed(e -> fail(token, "No se pudo abrir el correo. Puedes reintentar."));
        submit(task, token);
    }

    private void loadBody(boolean reload) {
        if (accountId == null) return;
        long token = ++generation;
        if (pending != null) pending.cancel(true);
        showingHtml = false;
        web.getEngine().getLoadWorker().cancel();
        Deadline deadline = Deadline.after(AppConstants.CONTENT_BUDGET);
        status.setText(reload ? "Recuperando formato del proveedor…" : "Cargando contenido…");
        upgrade.setDisable(true);
        arm(AppConstants.CONTENT_BUDGET.toMillis(), token, "El contenido tardó demasiado. Puedes reintentar.");
        Task<MailPresentation> task = facade.presentationTask(accountId, mailId, reload, deadline);
        task.setOnSucceeded(e -> {
            if (token != generation) return;
            presentation = task.getValue();
            MailContent content = presentation.getContent();
            text.setText(content.getPlainText());
            metadata.setText(metadata.getText().split("\nPara:", 2)[0] + "\nPara: "
                    + content.getRecipients().stream().map(Recipient::getEmail).collect(java.util.stream.Collectors.joining(", ")));
            formatted.setDisable(presentation.getDocument() == null);
            plain.setDisable(false);
            upgrade.setDisable(!content.isLegacyTextOnly());
            status.setText(content.isLegacyTextOnly() ? "Copia antigua en texto. Recuperar formato requiere conexión y autorización."
                    : content.isBlockedRemoteImages() ? "Imágenes externas bloqueadas por privacidad." : "");
            if (presentation.getDocument() == null) showPlain();
            else renderHtml(deadline);
        });
        task.setOnFailed(e -> fail(token, "No se pudo cargar el contenido. Revisa tu conexión o vuelve a autorizar la cuenta."));
        submit(task, token);
    }

    private void renderHtml(Deadline deadline) {
        if (presentation == null || presentation.getDocument() == null) return;
        try {
            arm(deadline.remainingMillis(), generation, "El formato tardó demasiado. Se muestra el texto disponible.");
            showingHtml = true;
            web.getEngine().loadContent(presentation.getDocument(), "text/html");
        } catch (Exception e) { showPlain(); }
    }

    private void showPlain() {
        showingHtml = false;
        timeout.stop();
        web.getEngine().getLoadWorker().cancel();
        setCenter(text);
        if (presentation != null) markRead();
    }

    private void markRead() {
        if (markedRead || accountId == null) return;
        markedRead = true;
        Task<Void> task = facade.markReadTask(accountId, mailId);
        long token = generation;
        task.setOnFailed(e -> { if (token == generation) { markedRead = false; status.setText("Correo visible; no se pudo guardar la marca de leído."); } });
        try { worker.execute(task); }
        catch (RejectedExecutionException e) { markedRead = false; }
    }

    private void attachLinks() {
        var document = web.getEngine().getDocument();
        if (document == null) return;
        ((EventTarget) document).addEventListener("click", event -> {
            event.preventDefault();
            Node node = event.getTarget() instanceof Node n ? n : null;
            while (node instanceof Element element) {
                if (element.hasAttribute("data-evermail-link")) {
                    String href = element.getAttribute("data-evermail-link");
                    if (HtmlMail.safeLink(href)) openLink.accept(URI.create(href));
                    return;
                }
                node = node.getParentNode();
            }
        }, true);
    }

    private void arm(long millis, long token, String message) {
        timeout.stop(); timeout.setDuration(Duration.millis(millis));
        timeout.setOnFinished(e -> {
            if (token != generation) return;
            generation++;
            if (pending != null) pending.cancel(true);
            showPlain(); status.setText(message); retry.setDisable(false);
            upgrade.setDisable(presentation == null || !presentation.getContent().isLegacyTextOnly());
        });
        timeout.playFromStart();
    }

    private void fail(long token, String message) {
        if (token != generation) return;
        showPlain(); status.setText(message); retry.setDisable(false);
        upgrade.setDisable(presentation == null || !presentation.getContent().isLegacyTextOnly());
    }

    private void submit(Task<?> task, long token) {
        pending = task;
        worker.purge();
        try { worker.execute(task); }
        catch (RejectedExecutionException e) { fail(token, "Hay operaciones pendientes. Vuelve a intentarlo."); }
    }

    /** Call on navigation/logout: invalidates late results and releases displayed plaintext. */
    public void clear() {
        checkThread(); generation++; timeout.stop();
        if (pending != null) pending.cancel(true);
        pending = null; presentation = null; accountId = null; mailId = null; markedRead = false; showingHtml = false;
        web.getEngine().getLoadWorker().cancel(); web.getEngine().loadContent("");
        text.clear(); heading.setText(""); metadata.setText(""); status.setText("");
        plain.setDisable(true); formatted.setDisable(true); upgrade.setDisable(true); retry.setDisable(true);
        setCenter(text);
    }

    private static void checkThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Reader must be used on the FX thread");
    }

    @Override public void close() { clear(); worker.shutdownNow(); }
}
