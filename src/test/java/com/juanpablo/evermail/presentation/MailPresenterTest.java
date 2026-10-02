package com.juanpablo.evermail.presentation;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.navigation.NavigationRules.Route;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static com.juanpablo.evermail.presentation.MailViewState.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit event queue exercises late callbacks without threads, network or JavaFX. */
class MailPresenterTest {
    private final Account account = new Account(UUID.randomUUID(), OAuthProvider.GOOGLE, "subject",
            "user@example.com", "User", "key", AccountStatus.ACTIVE);
    private final FakeBackend backend = new FakeBackend();
    private final Queue<Runnable> events = new ArrayDeque<>();
    private final List<MailViewState> notifications = new ArrayList<>();
    private final MailPresenter presenter = new MailPresenter(backend, events::add, notifications::add);
    private final ComposeDraft draft = new ComposeDraft("to@example.com", "cc@example.com", "bcc@example.com", "Subject", "Private body");

    private void flush() { while (!events.isEmpty()) events.remove().run(); }
    private void ready(SessionSnapshot.Phase phase) {
        backend.session = new SessionSnapshot(phase, account, 1);
        presenter.state();
    }
    private MailHeader mail(long uid) {
        return new MailHeader(new UUID(0, uid), account.getId(), MailDirection.INBOX, new RemoteMailId(1, uid),
                "id", null, "sender@example.com", "Sender", "Subject", Instant.EPOCH, false, true);
    }
    private InboxPage page(boolean more, MailHeader... mails) {
        return new InboxPage(List.of(mails), more ? new InboxCursor(account.getId(), 1, 50) : null, more, false, false);
    }
    private void inbox(MailHeader... mails) {
        ready(SessionSnapshot.Phase.OFFLINE);
        presenter.loadInbox(); backend.inboxes.getLast().complete(page(false, mails)); flush();
    }
    private void body(UUID id, boolean html) {
        MailContent content = new MailContent(id, "Private body", html ? "<p>Body</p>" : null, false, false, List.of());
        backend.contents.getLast().complete(new MailPresentation(content, html ? "<html>Body</html>" : null)); flush();
    }

    @Test void startupRoutesToLoginOnlyAfterDispatchedCompletion() {
        presenter.start();
        assertEquals(Route.LOADING, presenter.state().route());
        backend.session = new SessionSnapshot(SessionSnapshot.Phase.LOGIN_REQUIRED, null, backend.session.version());
        backend.startup.complete(new StartupResult(null, StartupStatus.LOGIN_REQUIRED));
        assertTrue(presenter.state().sessionBusy());
        flush();
        assertEquals(Route.LOGIN, presenter.state().route());
        assertFalse(presenter.state().sessionBusy());
    }

    @Test void onlineStartupDisplaysCacheBeforeRefreshAndKeepsCacheWhenNetworkFails() {
        presenter.start();
        backend.session = new SessionSnapshot(SessionSnapshot.Phase.READY, account, backend.session.version());
        backend.startup.complete(new StartupResult(account, StartupStatus.READY)); flush();
        assertEquals(List.of(PresentationBackend.InboxAction.CACHE), backend.actions);
        backend.inboxes.getLast().complete(page(false, mail(1))); flush();
        assertEquals(1, presenter.state().inbox().items().size());
        assertEquals(PresentationBackend.InboxAction.REFRESH, backend.actions.getLast());
        backend.inboxes.getLast().fail(ErrorCode.IMAP_CONNECTION_FAILED); flush();
        assertEquals(1, presenter.state().inbox().items().size());
        assertTrue(presenter.state().inbox().stale());
        assertNotNull(presenter.state().inbox().error());
        assertFalse(presenter.state().inbox().empty());
    }

    @Test void offlineCacheDoesNotAutomaticallyContactProvider() {
        inbox();
        assertEquals(List.of(PresentationBackend.InboxAction.CACHE), backend.actions);
        assertTrue(presenter.state().inbox().empty());
        assertFalse(presenter.state().inbox().canLoadMore());
    }

    @Test void paginationAppendsWithoutDuplicatesAndPreventsDoubleClicks() {
        ready(SessionSnapshot.Phase.OFFLINE);
        presenter.loadInbox(); backend.inboxes.getLast().complete(page(true, mail(2), mail(1))); flush();
        presenter.loadMore(); presenter.loadMore();
        assertEquals(2, backend.inboxes.size());
        assertNotNull(backend.lastCursor);
        backend.inboxes.getLast().complete(page(false, mail(1), mail(0))); flush();
        assertEquals(List.of(mail(2).getId(), mail(1).getId(), mail(0).getId()),
                presenter.state().inbox().items().stream().map(MailHeader::getId).toList());
        presenter.loadMore();
        assertEquals(2, backend.inboxes.size());
    }

    @Test void failedPaginationPreservesCursorAndExistingItemsForRetry() {
        ready(SessionSnapshot.Phase.OFFLINE);
        presenter.loadInbox(); backend.inboxes.getLast().complete(page(true, mail(1))); flush();
        presenter.loadMore(); InboxCursor previous = backend.lastCursor;
        backend.inboxes.getLast().fail(ErrorCode.NETWORK_TIMEOUT); flush();
        assertEquals(1, presenter.state().inbox().items().size());
        presenter.loadMore();
        assertEquals(previous, backend.lastCursor);
    }

    @Test void readerLoadsHeaderThenBodyAndSwitchesFormatsWithoutMoreRequests() {
        inbox(mail(1)); presenter.openMail(mail(1).getId());
        assertEquals(ReaderPhase.HEADER_LOADING, presenter.state().reader().phase());
        assertTrue(backend.contents.isEmpty());
        backend.headers.getLast().complete(mail(1)); flush();
        assertEquals(ReaderPhase.CONTENT_LOADING, presenter.state().reader().phase());
        body(mail(1).getId(), true);
        assertEquals(BodyMode.HTML, presenter.state().reader().mode());
        assertTrue(presenter.state().inbox().items().getFirst().isRead());
        presenter.showText(); assertEquals(BodyMode.TEXT, presenter.state().reader().mode());
        presenter.showFormatted(); assertEquals(BodyMode.HTML, presenter.state().reader().mode());
        assertEquals(1, backend.contents.size());
    }

    @Test void plainOnlyMailCannotSwitchToNonexistentHtml() {
        inbox(mail(1)); presenter.openMail(mail(1).getId());
        backend.headers.getLast().complete(mail(1)); flush(); body(mail(1).getId(), false);
        presenter.showFormatted(); assertEquals(BodyMode.TEXT, presenter.state().reader().mode());
    }

    @Test void selectingAnotherMailIgnoresOldHeaderEvenIfItCompletesLate() {
        inbox(mail(1), mail(2));
        presenter.openMail(mail(1).getId()); Pending<MailHeader> old = backend.headers.getLast();
        presenter.openMail(mail(2).getId());
        old.complete(mail(1)); flush();
        assertTrue(old.cancelled);
        assertTrue(backend.contents.isEmpty());
        backend.headers.getLast().complete(mail(2)); flush(); body(mail(2).getId(), false);
        assertEquals(mail(2).getId(), presenter.state().reader().mailId());
    }

    @Test void returningToInboxCancelsReaderAndRetainsDraftAndList() {
        inbox(mail(1)); presenter.editDraft(draft); presenter.openMail(mail(1).getId());
        backend.headers.getLast().complete(mail(1)); flush();
        presenter.backToInbox(); body(mail(1).getId(), true);
        assertEquals(Route.INBOX, presenter.state().route());
        assertNull(presenter.state().reader().content());
        assertEquals(draft, presenter.state().compose().draft());
        assertEquals(1, presenter.state().inbox().items().size());
    }

    @Test void contentFailureKeepsHeaderAndAllowsExplicitRetry() {
        inbox(mail(1)); presenter.openMail(mail(1).getId());
        backend.headers.getLast().complete(mail(1)); flush();
        backend.contents.getLast().fail(ErrorCode.NETWORK_TIMEOUT); flush();
        assertEquals(mail(1), presenter.state().reader().header());
        assertEquals(ReaderPhase.ERROR, presenter.state().reader().phase());
        presenter.retryReader(); assertEquals(2, backend.headers.size());
    }

    @Test void logoutNeedsConfirmationAndClearsSensitiveDataImmediately() {
        inbox(mail(1)); presenter.editDraft(draft);
        presenter.requestLogout(); presenter.cancelLogout(); presenter.confirmLogout();
        assertNull(backend.logout);
        presenter.requestLogout(); presenter.confirmLogout();
        assertTrue(presenter.state().inbox().items().isEmpty());
        assertTrue(presenter.state().compose().draft().isEmpty());
        backend.session = new SessionSnapshot(SessionSnapshot.Phase.LOGIN_REQUIRED, null, backend.session.version());
        backend.logout.complete(null); flush();
        assertEquals(Route.LOGIN, presenter.state().route());
    }

    @Test void sessionVersionIsCheckedAfterCallbackHasWaitedInUiQueue() {
        ready(SessionSnapshot.Phase.OFFLINE);
        presenter.loadInbox(); backend.inboxes.getLast().complete(page(false, mail(1)));
        backend.session = new SessionSnapshot(SessionSnapshot.Phase.LOGIN_REQUIRED, null, 2);
        flush();
        assertEquals(Route.LOGIN, presenter.state().route());
        assertTrue(presenter.state().inbox().items().isEmpty());
    }

    @Test void sendPreventsDoubleSubmissionAndPreservesIdentityAcrossConnectionFailure() {
        ready(SessionSnapshot.Phase.READY); presenter.editDraft(draft);
        presenter.send(); presenter.send(); presenter.editDraft(ComposeDraft.empty());
        assertEquals(1, backend.sends.size()); assertEquals(draft, presenter.state().compose().draft());
        UUID first = backend.submissions.getFirst();
        backend.sends.getLast().fail(ErrorCode.SMTP_CONNECTION_FAILED); flush();
        presenter.send(); assertEquals(first, backend.submissions.getLast());
    }

    @Test void uncertainDeliveryRetainsDraftAndLocksResendAndEditing() {
        ready(SessionSnapshot.Phase.READY); presenter.editDraft(draft); presenter.send();
        backend.sends.getLast().complete(new SendResult(backend.submissions.getLast(), DeliveryState.UNKNOWN, null, ErrorCode.DELIVERY_UNKNOWN)); flush();
        assertEquals(SendPhase.UNKNOWN, presenter.state().compose().phase());
        presenter.send(); presenter.editDraft(ComposeDraft.empty());
        assertEquals(1, backend.sends.size()); assertEquals(draft, presenter.state().compose().draft());
    }

    @Test void acceptedDeliveryWithPendingSaveNeverEnablesResend() {
        ready(SessionSnapshot.Phase.READY); presenter.editDraft(draft); presenter.send();
        backend.sends.getLast().complete(new SendResult(backend.submissions.getLast(), DeliveryState.ACCEPTED, null, ErrorCode.LOCAL_SAVE_PENDING)); flush();
        assertEquals(SendPhase.ACCEPTED, presenter.state().compose().phase());
        assertFalse(presenter.state().compose().canSend());
        assertEquals(draft, presenter.state().compose().draft());
    }

    @Test void confirmedSuccessClearsDraftAndNextMessageUsesNewIdentity() {
        ready(SessionSnapshot.Phase.READY); presenter.editDraft(draft); presenter.send();
        UUID first = backend.submissions.getLast();
        backend.sends.getLast().complete(new SendResult(first, DeliveryState.RECORDED, UUID.randomUUID(), null)); flush();
        assertEquals(SendPhase.SENT, presenter.state().compose().phase());
        assertTrue(presenter.state().compose().draft().isEmpty());
        presenter.editDraft(draft); presenter.send(); assertNotEquals(first, backend.submissions.getLast());
    }

    @Test void rejectionKeepsDraftButExplicitRetryGetsNewIdentity() {
        ready(SessionSnapshot.Phase.READY); presenter.editDraft(draft); presenter.send();
        UUID first = backend.submissions.getLast();
        backend.sends.getLast().complete(new SendResult(first, DeliveryState.FAILED, null, ErrorCode.SMTP_REJECTED)); flush();
        assertEquals(draft, presenter.state().compose().draft());
        presenter.send(); assertNotEquals(first, backend.submissions.getLast());
    }

    @Test void discardRequiresConfirmationAndDoesNotCancelASendingMessage() {
        ready(SessionSnapshot.Phase.READY); presenter.editDraft(draft);
        presenter.requestDiscard(); presenter.cancelDiscard(); presenter.confirmDiscard();
        assertEquals(draft, presenter.state().compose().draft());
        presenter.requestDiscard(); presenter.confirmDiscard(); assertTrue(presenter.state().compose().draft().isEmpty());
        presenter.editDraft(draft); presenter.send(); presenter.requestDiscard(); presenter.confirmDiscard();
        assertEquals(draft, presenter.state().compose().draft());
    }

    @Test void failedReauthorizationKeepsDraftAndAllowsInboxToLoadAgain() {
        ready(SessionSnapshot.Phase.REAUTH_REQUIRED); presenter.editDraft(draft); presenter.loadInbox();
        presenter.login(OAuthProvider.GOOGLE);
        backend.session = new SessionSnapshot(SessionSnapshot.Phase.REAUTH_REQUIRED, account, backend.session.version());
        backend.login.fail(ErrorCode.CANCELLED); flush();
        assertEquals(draft, presenter.state().compose().draft());
        assertFalse(presenter.state().inbox().loading());
        presenter.loadInbox(); assertEquals(2, backend.inboxes.size());
    }

    @Test void closeRejectsLateCallbacksAndRemovesPrivateData() {
        inbox(mail(1)); presenter.editDraft(draft); presenter.refreshInbox();
        presenter.closeAsync(); backend.inboxes.getLast().complete(page(false, mail(1))); flush();
        assertEquals(Route.CLOSED, presenter.state().route());
        assertTrue(presenter.state().inbox().items().isEmpty());
        assertTrue(presenter.state().compose().draft().isEmpty());
    }

    private static final class Pending<T> implements OperationHandle<T> {
        final long version;
        final CompletableFuture<OperationResult<T>> future = new CompletableFuture<>();
        boolean cancelled;
        Pending(long version) { this.version = version; }
        public long sessionVersion() { return version; }
        public CompletionStage<OperationResult<T>> result() { return future; }
        public boolean cancel() { cancelled = true; return true; }
        void complete(T value) { future.complete(OperationResult.success(value)); }
        void fail(ErrorCode code) { future.complete(OperationResult.failure(new OperationError(code, "Test error"))); }
    }

    private final class FakeBackend implements PresentationBackend {
        SessionSnapshot session = new SessionSnapshot(SessionSnapshot.Phase.NEW, null, 0);
        Pending<StartupResult> startup;
        Pending<Account> login;
        Pending<Void> logout;
        List<Pending<InboxPage>> inboxes = new ArrayList<>();
        List<InboxAction> actions = new ArrayList<>();
        List<Pending<MailHeader>> headers = new ArrayList<>();
        List<Pending<MailPresentation>> contents = new ArrayList<>();
        List<Pending<SendResult>> sends = new ArrayList<>();
        List<UUID> submissions = new ArrayList<>();
        InboxCursor lastCursor;
        public SessionSnapshot session() { return session; }
        public OperationHandle<StartupResult> start() {
            session = new SessionSnapshot(SessionSnapshot.Phase.STARTING, null, session.version() + 1);
            return startup = new Pending<>(session.version());
        }
        public OperationHandle<Account> login(OAuthProvider provider) {
            session = new SessionSnapshot(SessionSnapshot.Phase.AUTHENTICATING, session.account(), session.version() + 1);
            return login = new Pending<>(session.version());
        }
        public OperationHandle<Void> logout() {
            session = new SessionSnapshot(SessionSnapshot.Phase.LOGGING_OUT, null, session.version() + 1);
            return logout = new Pending<>(session.version());
        }
        public OperationHandle<InboxPage> inbox(InboxAction action, InboxCursor cursor) {
            actions.add(action); lastCursor = cursor;
            Pending<InboxPage> pending = new Pending<>(session.version()); inboxes.add(pending); return pending;
        }
        public OperationHandle<MailHeader> header(UUID id) {
            Pending<MailHeader> pending = new Pending<>(session.version()); headers.add(pending); return pending;
        }
        public OperationHandle<MailPresentation> content(UUID id) {
            Pending<MailPresentation> pending = new Pending<>(session.version()); contents.add(pending); return pending;
        }
        public OperationHandle<SendResult> send(UUID id, ComposeDraft draft) {
            submissions.add(id);
            Pending<SendResult> pending = new Pending<>(session.version()); sends.add(pending); return pending;
        }
        public CompletionStage<OperationResult<Void>> closeAsync() {
            session = new SessionSnapshot(SessionSnapshot.Phase.CLOSED, null, session.version() + 1);
            return CompletableFuture.completedFuture(OperationResult.success(null));
        }
    }
}
