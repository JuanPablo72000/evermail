package com.juanpablo.evermail.ui;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.navigation.NavigationRules.Route;
import com.juanpablo.evermail.presentation.*;
import com.juanpablo.evermail.presentation.MailViewState.*;
import com.juanpablo.evermail.util.HtmlMail;
import java.io.IOException;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.*;

/** JavaFX boundary: state and user actions only; services and credentials remain behind the presenter. */
public final class MailWindow {
    private final Stage stage;
    private final Runnable exit;
    private final Consumer<URI> browser;
    private final BorderPane root = new BorderPane();
    private final View login = load("Login-Screen.fxml"), loading = load("Loading-Screen.fxml"),
            main = load("Main-Screen.fxml"), reader = load("Reader-Screen.fxml");
    private final Label status = new Label(), sendStatus = new Label(), readerStatus = new Label();
    private final FlowPane banner = new FlowPane(12, 8);
    private final Button retry = new AnimatedButton(), authorize = new AnimatedButton(), cancelLogin = new AnimatedButton();
    private final ListView<MailHeader> list;
    private final SafeMailBody body;
    private final MailPresenter presenter;
    private MailViewState state;
    private Stage confirmation;
    private boolean rendering, closing, suppressDialogClose;
    private long version = -1;
    private Route route;
    private List<MailHeader> displayed = List.of();

    public MailWindow(Stage stage, PresentationBackend backend, Consumer<URI> browser, Runnable exit) {
        this.stage = stage; this.browser = browser; this.exit = exit;
        root.getStylesheets().add(getClass().getResource("/com/juanpablo/evermail/css/Common.css").toExternalForm());
        root.getStyleClass().add("evermail-view");
        status.setWrapText(true); status.setMaxWidth(700);
        retry.setId("retryOperation"); retry.setText("Reintentar");
        authorize.setId("reauthorize"); authorize.setText("Volver a autorizar");
        cancelLogin.setId("cancelLogin"); cancelLogin.setText("Cancelar autorización");
        banner.getStyleClass().add("operation-banner");
        banner.getChildren().addAll(status,retry,authorize,cancelLogin); root.setTop(banner);
        sendStatus.setWrapText(true); sendStatus.setId("sendStatus");
        readerStatus.setWrapText(true); readerStatus.setId("readerStatus");
        ((VBox)main.node("compose")).getChildren().add(1, sendStatus);
        ((VBox)reader.node("reader")).getChildren().add(3, readerStatus);
        body = new SafeMailBody(this::openLink, message -> { readerStatus.setText(message); visible(readerStatus,true); });
        ((StackPane)reader.node("bodyHost")).getChildren().setAll(body);
        @SuppressWarnings("unchecked") ListView<MailHeader> inbox = (ListView<MailHeader>) main.node("mailList");
        list = inbox; list.setCellFactory(v -> new MailCell());
        presenter = new MailPresenter(backend, Platform::runLater, this::render);
        wire();
        wipePrivateViews();
        render(presenter.state());
        stage.setOnCloseRequest(event -> { event.consume(); close(); });
    }

    public Parent root() { return root; }
    public void start() { presenter.start(); }

    private void wire() {
        login.button("googleButton").setOnAction(e -> presenter.login(OAuthProvider.GOOGLE));
        login.button("microsoftButton").setOnAction(e -> presenter.login(OAuthProvider.MICROSOFT));
        cancelLogin.setOnAction(e -> presenter.cancelLogin());
        authorize.setOnAction(e -> { if (state.session().account() != null) presenter.login(state.session().account().getProvider()); });
        retry.setOnAction(e -> {
            if (route == Route.RECOVERY) presenter.start();
            else if (route == Route.READER) presenter.retryReader();
            else presenter.refreshInbox();
        });
        main.button("refreshButton").setOnAction(e -> presenter.refreshInbox());
        main.button("loadMoreButton").setOnAction(e -> presenter.loadMore());
        main.button("sendButton").setOnAction(e -> presenter.send());
        main.button("discardButton").setOnAction(e -> presenter.requestDiscard());
        main.button("ccButton").setOnAction(e -> main.field("ccField").requestFocus());
        main.button("bccButton").setOnAction(e -> main.field("bccField").requestFocus());
        for (String field : List.of("toField","ccField","bccField","subjectField","messageField"))
            main.field(field).textProperty().addListener((o,a,b) -> {
                if (!rendering && !closing) presenter.editDraft(new ComposeDraft(main.field("toField").getText(),
                        main.field("ccField").getText(), main.field("bccField").getText(),
                        main.field("subjectField").getText(), main.field("messageField").getText()));
            });
        list.setOnMouseClicked(e -> { if (e.getClickCount() == 2) openSelected(); });
        list.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) { openSelected(); e.consume(); } });
        list.setAccessibleText("Bandeja de entrada. Abre un correo con doble clic o Enter.");
        reader.button("backButton").setOnAction(e -> presenter.backToInbox());
        reader.button("textButton").setOnAction(e -> presenter.showText());
        reader.button("formattedButton").setOnAction(e -> presenter.showFormatted());
        for (View view : List.of(main,reader)) {
            view.button("logoutButton").setOnAction(e -> presenter.requestLogout());
            view.button("inboxButton").setOnAction(e -> presenter.backToInbox());
            for (String id : List.of("notesButton","calendarButton","sentButton","draftsButton")) future(view.node(id));
        }
        for (String id : List.of("previousButton","nextButton","replyButton","forwardButton","deleteButton","archiveButton")) future(reader.node(id));
        future(main.node("searchField"));
    }
    private void openSelected() {
        MailHeader selected = list.getSelectionModel().getSelectedItem();
        if (selected != null) presenter.openMail(selected.getId());
    }
    private static void future(Node control) {
        control.setDisable(true);
        if (control instanceof Control c) c.setTooltip(new Tooltip("Disponible en una versión futura"));
    }

    private void render(MailViewState next) {
        if (closing) return;
        rendering = true;
        try {
            state = next;
            if (version != next.session().version()) { wipePrivateViews(); dismissConfirmation(); version = next.session().version(); }
            boolean privateView = next.session().hasSession();
            if (!privateView) wipePrivateViews();
            if (route != next.route()) {
                if (next.route() != Route.READER) body.clear();
                route = next.route();
                root.setCenter(switch (route) {
                    case LOGIN -> login.root;
                    case INBOX -> main.root;
                    case READER -> reader.root;
                    default -> loading.root;
                });
            }
            login.button("googleButton").setDisable(next.sessionBusy());
            login.button("microsoftButton").setDisable(next.sessionBusy());
            boolean authenticating = next.session().phase() == SessionSnapshot.Phase.AUTHENTICATING;
            visible(cancelLogin, authenticating); visible(authorize, privateView && next.session().phase() == SessionSnapshot.Phase.REAUTH_REQUIRED);
            boolean failure = route == Route.RECOVERY || route == Route.READER && next.reader().error() != null
                    || route == Route.INBOX && next.inbox().error() != null;
            visible(retry, failure && !next.sessionBusy() && !next.inbox().loading());
            String message = next.sessionError() != null ? next.sessionError().message()
                    : authenticating ? "Autoriza el acceso en tu navegador. Puedes cancelar aquí."
                    : next.session().phase() == SessionSnapshot.Phase.REAUTH_REQUIRED ? "La cuenta necesita autorización. Puedes consultar la copia local."
                    : next.session().phase() == SessionSnapshot.Phase.OFFLINE ? "Sesión local disponible. Actualiza la bandeja para intentar conectar."
                    : route == Route.RECOVERY ? "No se pudo iniciar Evermail. Revisa el almacenamiento local y vuelve a intentar."
                    : "";
            if (route == Route.INBOX && next.inbox().error() != null) message = next.inbox().error().message() + " Se conserva la copia local.";
            if (route == Route.READER && next.reader().error() != null) message = next.reader().error().message();
            status.setText(message);
            visible(banner, !message.isEmpty() || retry.isVisible() || authorize.isVisible() || cancelLogin.isVisible());
            loading.label("loadingMessage").setText(route == Route.RECOVERY ? "No se pudo iniciar Evermail"
                    : next.session().phase() == SessionSnapshot.Phase.LOGGING_OUT ? "Cerrando sesión…" : "Iniciando tu experiencia de Correo");
            loading.label("loadingDetail").setText(route == Route.RECOVERY ? "Puedes reintentar sin borrar tus datos." : "Preparando el almacenamiento y la sesión…");
            ((ProgressBar)loading.node("loadingProgress")).setProgress(route == Route.RECOVERY ? 0 : -1);
            if (privateView) { renderAccount(next.session().account()); renderInbox(next.inbox()); renderCompose(next.compose()); renderReader(next.reader()); }
            if (!next.logoutConfirmation() && !next.compose().discardConfirmation()) dismissConfirmation();
            if (next.logoutConfirmation() || next.compose().discardConfirmation()) {
                long expected = version;
                Platform.runLater(() -> { if (!closing && version == expected && confirmation == null) confirmIfRequested(); });
            }
        } finally { rendering = false; }
    }

    private void renderAccount(Account account) {
        String name = account.getDisplayName() == null || account.getDisplayName().isBlank() ? account.getEmail() : account.getDisplayName();
        for (View view : List.of(main,reader)) {
            view.label("accountName").setText(name); view.label("accountEmail").setText(account.getEmail());
            view.label("accountAvatar").setText(initial(name));
        }
        main.field("fromField").setText(account.getEmail());
    }
    private void renderInbox(Inbox inbox) {
        if (!displayed.equals(inbox.items())) {
            UUID selected = list.getSelectionModel().getSelectedItem() == null ? null : list.getSelectionModel().getSelectedItem().getId();
            displayed = inbox.items(); list.getItems().setAll(displayed);
            if (selected != null) displayed.stream().filter(m -> m.getId().equals(selected)).findFirst().ifPresent(m -> list.getSelectionModel().select(m));
        }
        main.label("mailCount").setText(inbox.items().size() + " Correos Cargados");
        main.label("updatedAt").setText(inbox.loading() ? "Actualizando…" : inbox.stale() ? "Copia local; actualización pendiente" : "Bandeja disponible");
        main.button("refreshButton").setDisable(inbox.loading());
        main.button("loadMoreButton").setDisable(!inbox.canLoadMore());
        ((Label)list.getPlaceholder()).setText(inbox.loading() ? "Cargando correos…" : inbox.error() != null ? "No se pudo cargar la bandeja" : "No hay correos para mostrar");
    }
    private void renderCompose(Compose compose) {
        ComposeDraft draft = compose.draft();
        String[] names = {"toField","ccField","bccField","subjectField","messageField"};
        String[] values = {draft.to(),draft.cc(),draft.bcc(),draft.subject(),draft.message()};
        for (int i=0;i<names.length;i++) {
            TextInputControl field = main.field(names[i]);
            if (!field.getText().equals(values[i])) field.setText(values[i]);
            field.setEditable(compose.editable());
        }
        main.button("sendButton").setDisable(!compose.canSend());
        main.button("discardButton").setDisable(compose.phase() == SendPhase.SENDING || compose.draft().isEmpty());
        String message = compose.error() != null ? compose.error().message() : switch (compose.phase()) {
            case SENDING -> "Enviando… No cierres la aplicación hasta conocer el resultado.";
            case SENT -> "El proveedor aceptó el correo y se guardó localmente.";
            default -> "";
        };
        sendStatus.setText(message); visible(sendStatus,!message.isEmpty());
    }
    private void renderReader(Reader data) {
        MailHeader header = data.header();
        reader.label("readerSubject").setText(header == null ? "Cargando correo…" : "Asunto: " + header.getSubject());
        reader.label("senderName").setText(header == null ? "" : Objects.toString(header.getSenderName(),header.getSenderEmail()));
        reader.label("senderEmail").setText(header == null ? "" : header.getSenderEmail());
        reader.label("senderAvatar").setText(header == null ? "" : initial(reader.label("senderName").getText()));
        reader.label("sentAt").setText(header == null || header.getOccurredAt() == null ? "" : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault()).format(header.getOccurredAt()));
        reader.label("recipients").setText(data.content() == null ? "" : "Para: " + data.content().getContent().getRecipients().stream().map(Recipient::getEmail).collect(java.util.stream.Collectors.joining(", ")));
        reader.button("formattedButton").setDisable(!data.hasHtml());
        reader.button("textButton").setDisable(data.content() == null);
        String message = data.error() != null ? data.error().message()
                : data.phase() == ReaderPhase.HEADER_LOADING ? "Cargando encabezado…"
                : data.phase() == ReaderPhase.CONTENT_LOADING ? "Cargando contenido…"
                : data.content() != null && data.content().getContent().isBlockedRemoteImages() ? "Imágenes externas bloqueadas por privacidad." : "";
        readerStatus.setText(message); visible(readerStatus,!message.isEmpty());
        body.show(data.content(),data.mode());
    }

    private void wipePrivateViews() {
        boolean previous = rendering; rendering = true;
        try {
            body.clear(); displayed = List.of(); list.getItems().clear(); list.getSelectionModel().clearSelection();
            for (View view : List.of(main,reader)) for (String id : List.of("accountName","accountEmail","accountAvatar")) view.label(id).setText("");
            for (String id : List.of("fromField","toField","ccField","bccField","subjectField","messageField","searchField")) main.field(id).clear();
            for (String id : List.of("readerSubject","senderName","senderEmail","senderAvatar","sentAt","recipients")) reader.label(id).setText("");
            sendStatus.setText(""); readerStatus.setText("");
        } finally { rendering = previous; }
    }

    private void confirmIfRequested() {
        if (state.logoutConfirmation()) {
            View view = load("Logout-Dialog.fxml");
            Stage dialog = dialog("Cerrar sesión",view.root,560,540);
            view.button("cancelButton").setOnAction(e -> { dismissConfirmation(); presenter.cancelLogout(); });
            view.button("confirmLogoutButton").setOnAction(e -> { dismissConfirmation(); presenter.confirmLogout(); });
            dialog.setOnCloseRequest(e -> { e.consume(); dismissConfirmation(); presenter.cancelLogout(); });
        } else if (state.compose().discardConfirmation()) {
            Label message = new Label(state.compose().phase() == SendPhase.UNKNOWN || state.compose().phase() == SendPhase.ACCEPTED
                    ? "El correo puede haber sido aceptado. Descartar el texto no cancela ni revierte el envío. ¿Descartar?"
                    : "¿Descartar el texto de este correo?");
            message.setWrapText(true);
            Button cancel = new AnimatedButton(), accept = new AnimatedButton();
            cancel.setText("Cancelar"); accept.setText("Descartar"); accept.setId("confirmDiscard");
            VBox content = new VBox(18,message,new FlowPane(12,8,cancel,accept)); content.getStyleClass().add("confirmation-content");
            Stage dialog = dialog("Descartar correo",content,460,220);
            cancel.setOnAction(e -> { dismissConfirmation(); presenter.cancelDiscard(); });
            accept.setOnAction(e -> { dismissConfirmation(); presenter.confirmDiscard(); });
            dialog.setOnCloseRequest(e -> { e.consume(); dismissConfirmation(); presenter.cancelDiscard(); });
        }
    }
    private Stage dialog(String title, Parent content, double width, double height) {
        Stage dialog = new Stage(); dialog.initOwner(stage); dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(title); dialog.setMinWidth(360); dialog.setMinHeight(220);
        Scene scene = new Scene(content,width,height); scene.getStylesheets().add(root.getStylesheets().getFirst());
        dialog.setScene(scene); confirmation = dialog;
        dialog.setOnHidden(e -> {
            if (suppressDialogClose || closing) return;
            confirmation = null;
            if (state.logoutConfirmation()) presenter.cancelLogout();
            else if (state.compose().discardConfirmation()) presenter.cancelDiscard();
        });
        dialog.show(); return dialog;
    }
    private void dismissConfirmation() {
        if (confirmation == null) return;
        suppressDialogClose = true;
        try { confirmation.close(); confirmation = null; } finally { suppressDialogClose = false; }
    }

    private void openLink(URI uri) {
        if (!HtmlMail.safeLink(uri.toString()) || closing || route != Route.READER) return;
        try { browser.accept(uri); }
        catch (RuntimeException failure) { readerStatus.setText("No se pudo abrir el enlace en el navegador."); visible(readerStatus,true); }
    }

    public CompletionStage<OperationResult<Void>> close() {
        if (closing) return presenter.closeAsync();
        closing = true; dismissConfirmation(); wipePrivateViews();
        root.setCenter(loading.root); banner.setDisable(true);
        loading.label("loadingMessage").setText("Cerrando Evermail…");
        loading.label("loadingDetail").setText("Esperando a que terminen las operaciones y se liberen los recursos.");
        CompletionStage<OperationResult<Void>> result = presenter.closeAsync();
        state = null;
        result.whenComplete((outcome,failure) -> Platform.runLater(() -> {
            if (failure == null && outcome.succeeded()) finishClose();
            else {
                status.setText("No se pudieron cerrar todos los recursos correctamente. La sesión visible se ha limpiado.");
                banner.setDisable(false); visible(banner,true); visible(authorize,false); visible(cancelLogin,false);
                visible(retry,true); retry.setText("Salir"); retry.setOnAction(e -> finishClose());
            }
        }));
        return result;
    }
    private void finishClose() { stage.setOnCloseRequest(null); stage.close(); exit.run(); }
    static String initial(String name) { return name == null || name.isBlank() ? "?" : name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(Locale.ROOT); }
    private static void visible(Node node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }
    private static View load(String resource) {
        try { return new View(FXMLLoader.load(MailWindow.class.getResource("/com/juanpablo/evermail/fxml/"+resource))); }
        catch (IOException e) { throw new java.io.UncheckedIOException("No se pudo cargar la interfaz",e); }
    }
    private record View(Parent root) {
        Node node(String id) {
            Parent content = root instanceof ScrollPane scroll ? (Parent)scroll.getContent() : root;
            return Objects.requireNonNull(content.lookup("#"+id),id);
        }
        Button button(String id) { return (Button)node(id); }
        Label label(String id) { return (Label)node(id); }
        TextInputControl field(String id) { return (TextInputControl)node(id); }
    }
}
