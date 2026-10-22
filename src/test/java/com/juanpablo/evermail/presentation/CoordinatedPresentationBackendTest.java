package com.juanpablo.evermail.presentation;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the real coordinator-to-service adapter, with only the I/O services replaced. */
class CoordinatedPresentationBackendTest {
    private final Account account = new Account(UUID.randomUUID(), OAuthProvider.GOOGLE, "subject", "user@example.com",
            "User", "key", AccountStatus.ACTIVE);
    private final UUID mailId = UUID.randomUUID();
    private final List<String> calls = new ArrayList<>();
    private ComposeRequest sent;
    private boolean markedRead;
    private final InboxService inbox = new InboxService(null, null, null) {
        @Override public InboxPage readCached(UUID id, InboxCursor cursor) {
            assertEquals(account.getId(), id); calls.add("cache");
            return new InboxPage(List.of(), null, false, false, false);
        }
        @Override public InboxPage refresh(UUID id, Deadline deadline) {
            assertEquals(account.getId(), id); calls.add("refresh");
            return new InboxPage(List.of(), null, false, false, false);
        }
        @Override public InboxPage loadMore(UUID id, InboxCursor cursor, Deadline deadline) {
            assertEquals(account.getId(), id); assertEquals(id, cursor.getAccountId()); calls.add("more");
            return new InboxPage(List.of(), null, false, false, false);
        }
        @Override public MailContent loadContent(UUID id, UUID mail, Deadline deadline) {
            assertEquals(account.getId(), id); assertEquals(mailId, mail); calls.add("content");
            return new MailContent(mailId, "Body", "<p>Body</p><script>alert(1)</script>", false, false, List.of());
        }
        @Override public void markRead(UUID id, UUID mail) {
            assertEquals(account.getId(), id); assertEquals(mailId, mail);
            markedRead = true;
        }
    };
    private final MailSendService sender = new MailSendService(null, null, null, null) {
        @Override public SendResult send(ComposeRequest request, Deadline deadline) {
            sent = request;
            return new SendResult(request.getSubmissionId(), DeliveryState.UNKNOWN, null, null);
        }
    };
    private final ApplicationCoordinator coordinator = new ApplicationCoordinator(() -> new BackendAccess() {
        public StartupResult start(Deadline deadline) { return new StartupResult(account, StartupStatus.READY); }
        public Account login(OAuthProvider provider, CancellationToken token) { return account; }
        public void logout(UUID id, Deadline deadline) {}
        public InboxService inbox() { return inbox; }
        public MailSendService sender() { return sender; }
        public void close() {}
    }, 4);
    private final CoordinatedPresentationBackend backend = new CoordinatedPresentationBackend(coordinator);

    @BeforeEach void start() throws Exception { assertTrue(result(backend.start()).succeeded()); }
    @AfterEach void close() throws Exception { backend.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS); }
    private <T> OperationResult<T> result(OperationHandle<T> handle) throws Exception {
        return handle.result().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test void dispatchesCacheRefreshAndPaginationToTheirDistinctServices() throws Exception {
        assertTrue(result(backend.inbox(PresentationBackend.InboxAction.CACHE, null)).succeeded());
        assertTrue(result(backend.inbox(PresentationBackend.InboxAction.REFRESH, null)).succeeded());
        assertTrue(result(backend.inbox(PresentationBackend.InboxAction.MORE, new InboxCursor(account.getId(), 1, 50))).succeeded());
        assertEquals(List.of("cache", "refresh", "more"), calls);
    }

    @Test void preparesSafeContentOffTheViewAndMarksOnlyThatMessageRead() throws Exception {
        OperationResult<MailPresentation> result = result(backend.content(mailId));
        assertTrue(result.succeeded());
        assertFalse(result.value().getDocument().contains("<script"));
        assertEquals("Body", result.value().getContent().getPlainText());
        assertTrue(markedRead);
    }

    @Test void buildsRequestUsingActiveAccountAndPreservesUncertainDelivery() throws Exception {
        UUID submission = UUID.randomUUID();
        OperationResult<SendResult> result = result(backend.send(submission,
                new ComposeDraft("to@example.com", "", "", "Subject", "Body")));
        assertEquals(account.getId(), sent.getAccountId());
        assertEquals(submission, sent.getSubmissionId());
        assertEquals(DeliveryState.UNKNOWN, result.value().getState());
    }
}
