package com.juanpablo.evermail.presentation;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.navigation.NavigationRules;
import com.juanpablo.evermail.navigation.NavigationRules.Route;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static com.juanpablo.evermail.presentation.MailViewState.*;
import static com.juanpablo.evermail.presentation.PresentationBackend.InboxAction;

/** Headless presentation. Invoke actions on the same serial dispatcher used for notifications. */
public final class MailPresenter {
    private enum Slot { SESSION, INBOX, READER, SEND }
    private final PresentationBackend backend;
    private final Executor dispatcher;
    private final Consumer<MailViewState> listener;
    private final EnumMap<Slot, OperationHandle<?>> running = new EnumMap<>(Slot.class);
    private final EnumMap<Slot, Long> revisions = new EnumMap<>(Slot.class);
    private long version;
    private Route requested = Route.INBOX;
    private boolean disposed, sessionBusy, inboxBusy, hasMore, stale, logoutConfirmation, discardConfirmation;
    private OperationError sessionError, inboxError, readerError, sendError;
    private List<MailHeader> items = List.of();
    private InboxCursor cursor;
    private UUID selected;
    private MailHeader header;
    private MailPresentation content;
    private ReaderPhase readerPhase = ReaderPhase.EMPTY;
    private BodyMode mode = BodyMode.TEXT;
    private ComposeDraft draft = ComposeDraft.empty();
    private UUID submissionId = UUID.randomUUID();
    private SendPhase sendPhase = SendPhase.EDITING;

    public MailPresenter(PresentationBackend backend, Executor dispatcher, Consumer<MailViewState> listener) {
        this.backend = Objects.requireNonNull(backend);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.listener = Objects.requireNonNull(listener);
        version = backend.session().version();
    }

    public synchronized MailViewState state() {
        synchronizeSession();
        return snapshot();
    }

    private MailViewState snapshot() {
        return new MailViewState(disposed ? Route.CLOSED : NavigationRules.resolve(backend.session(), requested),
                backend.session(), sessionBusy, sessionError,
                new Inbox(items, inboxBusy, hasMore, stale, inboxError),
                new Reader(selected, header, content, readerPhase, mode, readerError),
                new Compose(draft, sendPhase, sendError, discardConfirmation), logoutConfirmation);
    }

    private void publish() { listener.accept(snapshot()); }

    public synchronized void start() {
        synchronizeSession();
        if (disposed || sessionBusy || (backend.session().phase() != SessionSnapshot.Phase.NEW
                && backend.session().phase() != SessionSnapshot.Phase.FAILED)) return;
        cancelAll(); clearAccountData();
        OperationHandle<StartupResult> task = backend.start();
        version = backend.session().version();
        sessionBusy = true; sessionError = null;
        publish();
        bind(Slot.SESSION, task, result -> {
            sessionBusy = false; sessionError = result.error();
            if (result.succeeded() && backend.session().hasSession()) beginInbox(InboxAction.CACHE);
        });
    }

    public synchronized void login(OAuthProvider provider) {
        synchronizeSession();
        if (disposed || sessionBusy || sendPhase == SendPhase.SENDING) return;
        SessionSnapshot before = backend.session();
        if (before.phase() != SessionSnapshot.Phase.LOGIN_REQUIRED && !before.hasSession()) return;
        cancelAll(); inboxBusy = false; clearReader(); requested = Route.INBOX;
        OperationHandle<Account> task = backend.login(provider);
        version = backend.session().version();
        sessionBusy = true; sessionError = null;
        publish();
        bind(Slot.SESSION, task, result -> {
            sessionBusy = false; sessionError = result.error();
            if (result.succeeded()) {
                if (before.account() == null || !before.account().getId().equals(result.value().getId())) clearAccountData();
                requested = Route.INBOX;
                beginInbox(InboxAction.CACHE);
            }
        });
    }

    public synchronized void cancelLogin() {
        if (backend.session().phase() == SessionSnapshot.Phase.AUTHENTICATING) {
            OperationHandle<?> task = running.get(Slot.SESSION);
            if (task != null) task.cancel();
        }
    }

    public synchronized void requestLogout() {
        if (!available()) return;
        logoutConfirmation = true;
        publish();
    }

    public synchronized void cancelLogout() { logoutConfirmation = false; publish(); }

    public synchronized void confirmLogout() {
        if (!available() || !logoutConfirmation) return;
        cancelAll(); clearAccountData();
        OperationHandle<Void> task = backend.logout();
        version = backend.session().version();
        sessionBusy = true; sessionError = null;
        publish();
        bind(Slot.SESSION, task, result -> { sessionBusy = false; sessionError = result.error(); });
    }

    public synchronized void loadInbox() {
        if (available() && !inboxBusy) beginInbox(InboxAction.CACHE);
    }

    public synchronized void refreshInbox() {
        if (available() && !inboxBusy) beginInbox(InboxAction.REFRESH);
    }

    public synchronized void loadMore() {
        if (available() && !inboxBusy && hasMore && cursor != null) beginInbox(InboxAction.MORE);
    }

    private void beginInbox(InboxAction action) {
        inboxBusy = true; inboxError = null;
        OperationHandle<InboxPage> task = backend.inbox(action, action == InboxAction.MORE ? cursor : null);
        publish();
        bind(Slot.INBOX, task, result -> {
            inboxBusy = false;
            if (result.succeeded()) {
                InboxPage page = result.value();
                LinkedHashMap<UUID, MailHeader> merged = new LinkedHashMap<>();
                if (action == InboxAction.MORE) items.forEach(mail -> merged.put(mail.getId(), mail));
                page.getItems().forEach(mail -> merged.put(mail.getId(), mail));
                items = List.copyOf(merged.values());
                cursor = page.getNextCursor(); hasMore = page.isHasMore() && cursor != null; stale = page.isStale();
            } else {
                inboxError = result.error();
                stale = true;
            }
            // Cached data is displayed first. Reauthorization/offline startup never forces a network call.
            if (action == InboxAction.CACHE && backend.session().phase() == SessionSnapshot.Phase.READY)
                beginInbox(InboxAction.REFRESH);
        });
    }

    public synchronized void openMail(UUID id) {
        if (!available() || items.stream().noneMatch(mail -> mail.getId().equals(id))) return;
        cancel(Slot.READER);
        selected = id; header = null; content = null; readerError = null; mode = BodyMode.TEXT;
        requested = Route.READER; readerPhase = ReaderPhase.HEADER_LOADING;
        publish();
        bind(Slot.READER, backend.header(id), result -> {
            if (!result.succeeded()) { readerError = result.error(); readerPhase = ReaderPhase.ERROR; return; }
            header = result.value(); readerPhase = ReaderPhase.CONTENT_LOADING;
            publish();
            bind(Slot.READER, backend.content(id), body -> {
                if (!body.succeeded()) { readerError = body.error(); readerPhase = ReaderPhase.ERROR; return; }
                content = body.value(); readerPhase = ReaderPhase.READY;
                mode = content.getDocument() == null ? BodyMode.TEXT : BodyMode.HTML;
                header = header.withRead(true);
                items = items.stream().map(mail -> mail.getId().equals(id) ? mail.withRead(true) : mail).toList();
            });
        });
    }

    public synchronized void retryReader() { if (selected != null) openMail(selected); }

    public synchronized void backToInbox() {
        if (!available()) return;
        cancel(Slot.READER); clearReader(); requested = Route.INBOX; publish();
    }

    public synchronized void showText() {
        if (available() && content != null) { mode = BodyMode.TEXT; publish(); }
    }

    public synchronized void showFormatted() {
        if (available() && content != null && content.getDocument() != null) { mode = BodyMode.HTML; publish(); }
    }

    public synchronized void editDraft(ComposeDraft value) {
        if (!available() || !snapshot().compose().editable()) return;
        if (!draft.equals(value)) { draft = Objects.requireNonNull(value); submissionId = UUID.randomUUID(); }
        sendPhase = SendPhase.EDITING; sendError = null; discardConfirmation = false;
        publish();
    }

    public synchronized void requestDiscard() {
        if (!available() || sendPhase == SendPhase.SENDING) return;
        discardConfirmation = !draft.isEmpty(); publish();
    }

    public synchronized void cancelDiscard() { discardConfirmation = false; publish(); }

    public synchronized void confirmDiscard() {
        if (!available() || !discardConfirmation || sendPhase == SendPhase.SENDING) return;
        resetDraft(); publish();
    }

    public synchronized void send() {
        if (!available() || !snapshot().compose().canSend()) return;
        sendPhase = SendPhase.SENDING; sendError = null; discardConfirmation = false;
        OperationHandle<SendResult> task = backend.send(submissionId, draft);
        publish();
        bind(Slot.SEND, task, result -> {
            if (!result.succeeded()) {
                sendError = result.error();
                ErrorCode code = sendError.code();
                sendPhase = code == ErrorCode.CANCELLED || code == ErrorCode.DELIVERY_UNKNOWN
                        || code == ErrorCode.LOCAL_SAVE_PENDING || code == null ? SendPhase.UNKNOWN : SendPhase.FAILED;
                return;
            }
            SendResult delivery = result.value();
            switch (delivery.getState()) {
                case RECORDED -> { resetDraft(); sendPhase = SendPhase.SENT; }
                case ACCEPTED -> {
                    sendPhase = SendPhase.ACCEPTED;
                    sendError = new OperationError(ErrorCode.LOCAL_SAVE_PENDING,
                            "El proveedor aceptó el correo; su guardado local está pendiente. No vuelvas a enviarlo.");
                }
                case UNKNOWN, SENDING, PENDING -> {
                    sendPhase = SendPhase.UNKNOWN;
                    sendError = new OperationError(ErrorCode.DELIVERY_UNKNOWN,
                            "No se pudo confirmar el envío. Comprueba el correo antes de volver a enviarlo.");
                }
                case FAILED -> {
                    sendPhase = SendPhase.FAILED;
                    // A confirmed rejection may be submitted again explicitly with a fresh identity.
                    submissionId = UUID.randomUUID();
                    sendError = new OperationError(delivery.getError(), "El proveedor rechazó el envío. Revisa el correo.");
                }
            }
        });
    }

    public synchronized CompletionStage<OperationResult<Void>> closeAsync() {
        disposed = true; cancelAll(); clearAccountData(); sessionBusy = false;
        CompletionStage<OperationResult<Void>> result = backend.closeAsync();
        version = backend.session().version(); publish();
        return result;
    }

    private boolean available() { synchronizeSession(); return !disposed && !sessionBusy && backend.session().hasSession(); }

    private void synchronizeSession() {
        if (version == backend.session().version()) return;
        cancelAll(); clearAccountData(); sessionBusy = false; sessionError = null;
        version = backend.session().version();
    }

    private void clearReader() {
        selected = null; header = null; content = null; readerError = null;
        readerPhase = ReaderPhase.EMPTY; mode = BodyMode.TEXT;
    }
    private void resetDraft() {
        draft = ComposeDraft.empty(); submissionId = UUID.randomUUID();
        sendPhase = SendPhase.EDITING; sendError = null; discardConfirmation = false;
    }
    private void clearAccountData() {
        items = List.of(); cursor = null; hasMore = false; stale = false; inboxError = null;
        inboxBusy = false; logoutConfirmation = false; requested = Route.INBOX;
        clearReader(); resetDraft();
    }
    private void cancel(Slot slot) {
        revisions.merge(slot, 1L, Long::sum);
        OperationHandle<?> previous = running.remove(slot);
        if (previous != null) previous.cancel();
    }
    private void cancelAll() { for (Slot slot : Slot.values()) cancel(slot); }

    private <T> void bind(Slot slot, OperationHandle<T> task, Consumer<OperationResult<T>> accept) {
        cancel(slot);
        long revision = revisions.get(slot);
        running.put(slot, task);
        task.result().whenComplete((result, failure) -> dispatcher.execute(() -> {
            synchronized (MailPresenter.this) {
                if (disposed || revisions.get(slot) != revision) return;
                if (task.sessionVersion() != backend.session().version()) {
                    synchronizeSession(); publish(); return;
                }
                running.remove(slot);
                OperationResult<T> outcome = failure == null ? result : OperationResult.failure(
                        new OperationError(null, "No se pudo completar la operación."));
                accept.accept(outcome);
                publish();
            }
        }));
    }
}
