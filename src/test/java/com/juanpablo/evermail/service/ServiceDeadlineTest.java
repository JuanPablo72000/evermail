package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.*;
import com.juanpablo.evermail.support.BackendFixture;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deadline regressions using the existing isolated SQLite fixture, never real accounts. */
class ServiceDeadlineTest extends BackendFixture {
    private final ManualDeadline clock = new ManualDeadline(Duration.ofSeconds(5));

    private AuthService auth() {
        return new AuthService(accounts, keys, null, null, coordinator, Clock.systemUTC());
    }

    @Test
    void expiredLogoutKeepsAccountAndKeyIntact() throws Exception {
        clock.expire();
        assertEquals(ErrorCode.LOCAL_TIMEOUT, assertThrows(SessionException.class,
                () -> auth().logout(account.getId(), clock.deadline())).getErrorCode());
        assertEquals(AccountStatus.ACTIVE, accounts.find(account.getId()).getStatus());
        assertNotNull(keys.read(account.getKeyRef()));
    }

    @Test
    void expiredLegacyImportDoesNotAccessStorage() {
        clock.expire();
        LegacyImporter importer = new LegacyImporter(null, null, null, null);
        assertThrows(SessionException.class, () -> importer.importAccounts(clock.deadline()));
    }

    @Test
    void expiredSessionRestoreDoesNotReportLoginRequiredForAnEmptyStore() throws Exception {
        auth().logout(account.getId());
        clock.expire();
        assertThrows(SessionException.class, () -> auth().restoreSession(clock.deadline()));
    }

    @Test
    void recoveryStopsBetweenAccountsWithoutDeletingTheRemainingAccount() throws Exception {
        Account a = accounts.beginProvisioning(new Identity(OAuthProvider.GOOGLE, "a", "a@example.com", "A"));
        Account b = accounts.beginProvisioning(new Identity(OAuthProvider.GOOGLE, "b", "b@example.com", "B"));
        keys.create(a.getKeyRef());
        keys.create(b.getKeyRef());
        List<UUID> closed = new ArrayList<>();
        AuthService service = new AuthService(accounts, keys, null, null, coordinator, Clock.systemUTC()) {
            @Override public void logout(UUID id, Deadline deadline) throws EvermailException {
                assertSame(clock.deadline(), deadline);
                super.logout(id, deadline);
                closed.add(id);
                clock.expire();
            }
        };
        assertThrows(SessionException.class, () -> service.recoverAccountLifecycle(clock.deadline()));
        assertEquals(1, closed.size());
        UUID remaining = closed.getFirst().equals(a.getId()) ? b.getId() : a.getId();
        assertNotNull(accounts.find(remaining));
        assertEquals(2, count("account"));
    }

    @Test
    void outboxRecoveryStopsBetweenMessagesAndCanResumeWithoutSending() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        outbox.prepare(request(first));
        outbox.prepare(request(second));
        outbox.claim(account.getId(), first);
        outbox.claim(account.getId(), second);
        OutboxRepository expiringOutbox = new OutboxRepository(transactions, accounts, keys, security) {
            @Override public void transition(UUID accountId, UUID id, DeliveryState state, ErrorCode error)
                    throws EvermailException {
                super.transition(accountId, id, state, error);
                clock.expire();
            }
        };
        MailSendService service = new MailSendService(gateway, expiringOutbox, new ComposeService(), coordinator);
        assertThrows(SessionException.class, () -> service.recover(account.getId(), clock.deadline()));
        assertEquals(1, outbox.recoverable(account.getId()).size());
        sender.recover(account.getId(), budget());
        assertEquals(DeliveryState.UNKNOWN, outbox.find(account.getId(), first).getState());
        assertEquals(DeliveryState.UNKNOWN, outbox.find(account.getId(), second).getState());
        assertEquals(0, gateway.submissions.get());
    }

    @Test
    void expiredPaginationDoesNotReturnEvenAnAlreadyCachedPage() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 100, false));
        InboxPage first = mails.readInbox(account.getId(), null, 50);
        clock.expire();
        InboxService inbox = new InboxService(gateway, mails, coordinator);
        assertThrows(SessionException.class,
                () -> inbox.loadMore(account.getId(), first.getNextCursor(), clock.deadline()));
        assertEquals(0, gateway.inboxRequests.get());
    }

    @Test
    void paginationDoesNotPersistAResponseReceivedAfterTheDeadline() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 51, 100, true));
        InboxPage first = mails.readInbox(account.getId(), null, 50);
        FakeGateway delayed = new FakeGateway() {
            @Override public InboxSession openInbox(UUID id, Deadline deadline) {
                return new InboxSession() {
                    public RemoteInboxPage fetchLatest(int size, Deadline budget) { throw new AssertionError(); }
                    public RemoteInboxPage fetchBefore(InboxCursor cursor, int size, Deadline budget) {
                        clock.expire();
                        return page(1, 1, 50, false);
                    }
                    public RemoteMailContent fetchContent(RemoteMailId remote, Deadline budget) { throw new AssertionError(); }
                    public void close() {}
                };
            }
        };
        InboxService inbox = new InboxService(delayed, mails, coordinator);
        assertThrows(SessionException.class,
                () -> inbox.loadMore(account.getId(), first.getNextCursor(), clock.deadline()));
        assertEquals(50, count("mail"));
    }

    @Test
    void cancelledSendDoesNotPrepareAnOutboxEntryOrContactSmtp() throws Exception {
        ComposeRequest request = request(UUID.randomUUID());
        try {
            Thread.currentThread().interrupt();
            assertEquals(ErrorCode.CANCELLED, assertThrows(SessionException.class,
                    () -> sender.send(request, clock.deadline())).getErrorCode());
        } finally {
            Thread.interrupted();
        }
        assertEquals(0, count("outbox_message"));
        assertEquals(0, gateway.submissions.get());
    }
}
