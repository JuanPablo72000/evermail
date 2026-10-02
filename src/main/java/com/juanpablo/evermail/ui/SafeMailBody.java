package com.juanpablo.evermail.ui;

import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.model.MailPresentation;
import com.juanpablo.evermail.presentation.MailViewState.BodyMode;
import com.juanpablo.evermail.util.HtmlMail;
import java.net.URI;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.concurrent.Worker;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.events.EventTarget;

/** Renders only the sanitized document prepared by the backend. No session or credentials enter this view. */
public final class SafeMailBody extends StackPane {
    private final TextArea text = new TextArea();
    private final WebView web = new WebView();
    private final PauseTransition timeout = new PauseTransition(Duration.millis(AppConstants.CONTENT_BUDGET.toMillis()));
    private final Consumer<URI> openLink;
    private final Consumer<String> notice;
    private MailPresentation current;
    private BodyMode mode;
    private boolean htmlActive;

    public SafeMailBody(Consumer<URI> openLink, Consumer<String> notice) {
        this.openLink = openLink; this.notice = notice;
        setMinSize(0, 0);
        text.setEditable(false); text.setWrapText(true); text.getStyleClass().add("reader-text");
        web.setMinSize(0, 0); web.setContextMenuEnabled(false);
        var engine = web.getEngine();
        engine.setJavaScriptEnabled(false);
        engine.setCreatePopupHandler(features -> null);
        engine.setConfirmHandler(message -> false);
        engine.setPromptHandler(prompt -> null);
        engine.locationProperty().addListener((o, old, location) -> {
            if (location != null && !location.isEmpty() && !location.equals("about:blank")) fallback();
        });
        engine.getLoadWorker().stateProperty().addListener((o, old, state) -> {
            if (!htmlActive) return;
            if (state == Worker.State.SUCCEEDED) {
                timeout.stop(); attachLinks(); getChildren().setAll(web);
            } else if (state == Worker.State.FAILED) fallback();
        });
        timeout.setOnFinished(e -> fallback());
        getChildren().setAll(text);
    }

    public void show(MailPresentation presentation, BodyMode requestedMode) {
        if (current == presentation && mode == requestedMode) return;
        clear();
        current = presentation; mode = requestedMode;
        if (presentation == null) return;
        text.setText(presentation.getContent().getPlainText());
        if (requestedMode == BodyMode.HTML && presentation.getDocument() != null) {
            htmlActive = true;
            timeout.playFromStart();
            web.getEngine().loadContent(presentation.getDocument(), "text/html");
        }
    }

    private void fallback() {
        htmlActive = false; timeout.stop(); web.getEngine().getLoadWorker().cancel();
        getChildren().setAll(text);
        notice.accept("No se pudo mostrar el formato. Se muestra el texto disponible.");
    }

    private void attachLinks() {
        var doc = web.getEngine().getDocument();
        if (doc == null) return;
        MailPresentation expected = current;
        ((EventTarget) doc).addEventListener("click", event -> {
            event.preventDefault();
            if (!htmlActive || current == null || current != expected || doc != web.getEngine().getDocument()) return;
            Node node = event.getTarget() instanceof Node value ? value : null;
            while (node instanceof Element element) {
                String href = element.getAttribute("data-evermail-link");
                if (HtmlMail.safeLink(href)) { openLink.accept(URI.create(href)); return; }
                node = node.getParentNode();
            }
        }, true);
    }

    public void clear() {
        htmlActive = false; timeout.stop(); current = null; mode = null;
        web.getEngine().getLoadWorker().cancel(); web.getEngine().loadContent("");
        text.clear(); getChildren().setAll(text);
    }
}
