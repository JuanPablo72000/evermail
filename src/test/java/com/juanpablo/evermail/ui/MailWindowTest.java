package com.juanpablo.evermail.ui;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.presentation.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MailWindowTest {
    private final FakeBackend backend = new FakeBackend();
    private final AtomicInteger exits = new AtomicInteger();
    private MailWindow window;
    private Stage stage;
    private final Account account = new Account(UUID.randomUUID(),OAuthProvider.GOOGLE,"subject","private@example.com","Private User","key",AccountStatus.ACTIVE);
    private final MailHeader mail = new MailHeader(UUID.randomUUID(),account.getId(),MailDirection.INBOX,new RemoteMailId(1,1),null,null,
            "sender@example.com","Sender","Private subject",Instant.now(),false,false);

    @BeforeAll static void initializeFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(10,TimeUnit.SECONDS));
    }
    private static <T> T fx(Callable<T> task) throws Exception {
        FutureTask<T> result = new FutureTask<>(task); Platform.runLater(result); return result.get(15,TimeUnit.SECONDS);
    }
    @BeforeEach void createWindow() throws Exception {
        fx(() -> {
            stage = new Stage(); window = new MailWindow(stage,backend,uri -> {},exits::incrementAndGet);
            stage.setScene(new Scene(window.root(),1280,720)); window.start(); return null;
        });
    }
    @AfterEach void cleanup() throws Exception {
        fx(() -> { backend.closed.complete(OperationResult.success(null)); window.close(); return null; });
        fx(() -> null);
    }
    private Node node(String id) {
        window.root().applyCss(); window.root().layout();
        return Objects.requireNonNull(window.root().lookup("#"+id),id);
    }
    private Button button(String id) { return (Button)node(id); }
    private TextInputControl field(String id) { return (TextInputControl)node(id); }
    private void ready() throws Exception {
        fx(() -> { backend.session = new SessionSnapshot(SessionSnapshot.Phase.OFFLINE,account,backend.session.version());
            backend.startup.complete(new StartupResult(account,StartupStatus.OFFLINE)); return null; });
        fx(() -> { backend.inbox.complete(new InboxPage(List.of(mail),null,false,true,false)); return null; });
        fx(() -> null);
    }

    @Test void loginButtonsInvokeBothProvidersAndSupportCancellation() throws Exception {
        fx(() -> { backend.session = new SessionSnapshot(SessionSnapshot.Phase.LOGIN_REQUIRED,null,backend.session.version());
            backend.startup.complete(new StartupResult(null,StartupStatus.LOGIN_REQUIRED)); return null; });
        for (OAuthProvider provider : OAuthProvider.values()) {
            fx(() -> {
                button(provider == OAuthProvider.GOOGLE ? "googleButton" : "microsoftButton").fire();
                assertEquals(provider,backend.provider); assertTrue(button("googleButton").isDisabled());
                button("cancelLogin").fire(); return null;
            });
            fx(() -> { assertFalse(button("googleButton").isDisabled()); assertNull(window.root().lookup("#accountEmail")); return null; });
        }
    }

    @Test void uncertainSendLocksFormAndDoesNotResubmit() throws Exception {
        ready();
        fx(() -> {
            field("toField").setText("recipient@example.com"); field("subjectField").setText("Subject"); field("messageField").setText("Private draft");
            assertEquals(account.getEmail(),field("fromField").getText()); assertFalse(field("fromField").isEditable());
            button("sendButton").fire(); assertEquals(1,backend.sends);
            backend.send.complete(new SendResult(backend.submission,DeliveryState.UNKNOWN,null,ErrorCode.DELIVERY_UNKNOWN)); return null;
        });
        fx(() -> {
            assertTrue(button("sendButton").isDisabled()); assertFalse(field("messageField").isEditable());
            assertEquals("Private draft",field("messageField").getText()); button("sendButton").fire(); assertEquals(1,backend.sends);
            assertTrue(((Label)node("sendStatus")).getText().contains("confirmar")); return null;
        });
    }

    @Test void logoutClearsHiddenDraftUndoHistoryAndRejectsLateReaderContent() throws Exception {
        ready();
        TextInputControl draft = fx(() -> { TextInputControl value = field("messageField"); value.replaceText(0,0,"PRIVATE_DRAFT"); return value; });
        fx(() -> {
            ListView<?> list = (ListView<?>)node("mailList"); list.getSelectionModel().select(0);
            list.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.ENTER,false,false,false,false));
            backend.header.complete(mail); return null;
        });
        fx(() -> { button("logoutButton").fire(); return null; });
        fx(() -> {
            Stage dialog = (Stage)Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getTitle().equals("Cerrar sesión")).findFirst().orElseThrow();
            dialog.getScene().getRoot().applyCss();
            ((Button)dialog.getScene().lookup("#confirmLogoutButton")).fire();
            assertEquals("",draft.getText()); draft.undo(); assertEquals("",draft.getText());
            backend.content.complete(new MailPresentation(new MailContent(mail.getId(),"LATE_PRIVATE_BODY",List.of()),null));
            backend.session = new SessionSnapshot(SessionSnapshot.Phase.LOGIN_REQUIRED,null,backend.session.version()); backend.logout.complete(null);
            return null;
        });
        fx(() -> { assertFalse(button("googleButton").isDisabled()); assertNull(window.root().lookup("#bodyHost")); assertEquals("",draft.getText()); return null; });
    }

    @Test void windowWaitsForBackendCloseAfterImmediatelyClearingPrivateFields() throws Exception {
        ready();
        TextInputControl draft = fx(() -> { TextInputControl value = field("messageField"); value.setText("PRIVATE_DRAFT"); window.close(); return value; });
        fx(() -> { assertEquals("",draft.getText()); assertEquals(0,exits.get()); backend.closed.complete(OperationResult.success(null)); return null; });
        fx(() -> { assertEquals(1,exits.get()); return null; });
    }

    @Test void recordedSendClearsDraftAndRefreshAndPaginationUseBackend() throws Exception {
        ready();
        fx(() -> {
            button("refreshButton").fire(); assertEquals(PresentationBackend.InboxAction.REFRESH,backend.action);
            backend.inbox.complete(new InboxPage(List.of(mail),new InboxCursor(account.getId(),1,1),true,false,false)); return null;
        });
        fx(() -> { button("loadMoreButton").fire(); assertEquals(PresentationBackend.InboxAction.MORE,backend.action);
            backend.inbox.complete(new InboxPage(List.of(),null,false,false,false)); return null; });
        fx(() -> { field("toField").setText("recipient@example.com"); field("messageField").setText("Draft"); button("sendButton").fire();
            backend.send.complete(new SendResult(backend.submission,DeliveryState.RECORDED,UUID.randomUUID(),null)); return null; });
        fx(() -> { assertEquals("",field("messageField").getText()); assertTrue(button("sendButton").isDisabled()); return null; });
    }

    private static final class Pending<T> implements OperationHandle<T> {
        final long version; final CompletableFuture<OperationResult<T>> result = new CompletableFuture<>(); Runnable onCancel = () -> {};
        Pending(long version) { this.version = version; }
        public long sessionVersion() { return version; }
        public CompletionStage<OperationResult<T>> result() { return result; }
        public boolean cancel() { onCancel.run(); return true; }
        void complete(T value) { result.complete(OperationResult.success(value)); }
    }
    private static final class FakeBackend implements PresentationBackend {
        SessionSnapshot session = new SessionSnapshot(SessionSnapshot.Phase.NEW,null,0);
        Pending<StartupResult> startup; Pending<Account> login; Pending<Void> logout;
        Pending<InboxPage> inbox; Pending<MailHeader> header; Pending<MailPresentation> content; Pending<SendResult> send;
        OAuthProvider provider; InboxAction action; UUID submission; int sends;
        CompletableFuture<OperationResult<Void>> closed = new CompletableFuture<>();
        public SessionSnapshot session() { return session; }
        public OperationHandle<StartupResult> start() { session = new SessionSnapshot(SessionSnapshot.Phase.STARTING,null,session.version()+1); return startup = new Pending<>(session.version()); }
        public OperationHandle<Account> login(OAuthProvider value) {
            provider=value; session = new SessionSnapshot(SessionSnapshot.Phase.AUTHENTICATING,null,session.version()+1);
            login = new Pending<>(session.version()); login.onCancel = () -> {
                session = new SessionSnapshot(SessionSnapshot.Phase.LOGIN_REQUIRED,null,session.version());
                login.result.complete(OperationResult.failure(new OperationError(ErrorCode.CANCELLED,"Cancelado")));
            }; return login;
        }
        public OperationHandle<Void> logout() { session = new SessionSnapshot(SessionSnapshot.Phase.LOGGING_OUT,null,session.version()+1); return logout = new Pending<>(session.version()); }
        public OperationHandle<InboxPage> inbox(InboxAction value, InboxCursor cursor) { action=value; return inbox = new Pending<>(session.version()); }
        public OperationHandle<MailHeader> header(UUID id) { return header = new Pending<>(session.version()); }
        public OperationHandle<MailPresentation> content(UUID id) { return content = new Pending<>(session.version()); }
        public OperationHandle<SendResult> send(UUID id, ComposeDraft draft) { sends++; submission=id; return send = new Pending<>(session.version()); }
        public CompletionStage<OperationResult<Void>> closeAsync() { session = new SessionSnapshot(SessionSnapshot.Phase.CLOSED,null,session.version()+1); return closed; }
    }
}
