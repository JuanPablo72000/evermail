package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.support.BackendFixture;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest extends BackendFixture {
    @Test
    void freshDatabaseIsInitializedAndMigrationIsRepeatable() throws Exception {
        new DatabaseMigrator(transactions).migrate();
        assertEquals(1, count("account"));
        assertEquals(0, count("mail"));
        assertEquals(2, (int) transactions.read(c -> Sql.one(c, "PRAGMA user_version", rs -> rs.getInt(1))));
    }

    @Test
    void multiStatementFailureRollsBackEverything() throws Exception {
        assertThrows(DatabaseException.class, () -> transactions.write(c -> {
            Sql.update(c, "UPDATE account SET display_name='changed' WHERE id_account=?", account.getId());
            Sql.update(c, "INSERT INTO table_that_does_not_exist VALUES(1)");
            return null;
        }));
        assertEquals("Person", accounts.find(account.getId()).getDisplayName());
    }

    @Test
    void readsSenderNameAndPaginatesWithoutDuplicates() throws Exception {
        mails.saveInboxPage(account.getId(), page(10, 51, 100, true));
        InboxPage first = mails.readInbox(account.getId(), null, 50);
        assertEquals(50, first.getItems().size());
        assertEquals("Sender", first.getItems().getFirst().getSenderName());
        assertEquals(100, first.getItems().getFirst().getRemoteId().getUid());
        mails.saveInboxPage(account.getId(), page(10, 1, 50, false));
        InboxPage second = mails.readInbox(account.getId(), first.getNextCursor(), 50);
        assertEquals(50, second.getItems().size());
        assertEquals(50, second.getItems().getFirst().getRemoteId().getUid());
        assertFalse(second.isHasMore());
        mails.saveInboxPage(account.getId(), page(10, 51, 100, true));
        assertEquals(100, count("mail"));
    }

    @Test
    void newMessagesDoNotShiftAnExistingCursor() throws Exception {
        mails.saveInboxPage(account.getId(), page(4, 51, 100, true));
        InboxCursor cursor = mails.readInbox(account.getId(), null, 50).getNextCursor();
        mails.saveInboxPage(account.getId(), page(4, 1, 50, false));
        mails.saveInboxPage(account.getId(), page(4, 52, 101, true));
        assertEquals(50, mails.readInbox(account.getId(), cursor, 50).getItems().getFirst().getRemoteId().getUid());
    }

    @Test
    void uidValidityInvalidatesOnlyIncomingAndRejectsOldCursor() throws Exception {
        sender.send(request(UUID.randomUUID()), budget());
        mails.saveInboxPage(account.getId(), page(1, 51, 100, true));
        InboxCursor old = mails.readInbox(account.getId(), null, 50).getNextCursor();
        mails.saveInboxPage(account.getId(), page(2, 1, 3, false));
        assertEquals(4, count("mail"));
        MailFetchException error = assertThrows(MailFetchException.class, () -> mails.readInbox(account.getId(), old, 50));
        assertEquals(ErrorCode.CURSOR_INVALID, error.getErrorCode());
    }

    @Test
    void bodyIsEncryptedAndObjectsRemainPlainAfterFailure() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
        UUID id = mails.readInbox(account.getId(), null, 50).getItems().getFirst().getId();
        RemoteMailContent content = new RemoteMailContent("TOP SECRET", List.of());
        mails.saveContent(account.getId(), id, content);
        String cipher = transactions.read(c -> Sql.one(c, "SELECT body_cipher FROM mail WHERE id_mail=?", rs -> rs.getString(1), id));
        assertFalse(cipher.contains("TOP SECRET"));
        assertEquals("TOP SECRET", mails.readContent(account.getId(), id).getPlainText());
        transactions.write(c -> { Sql.update(c, "CREATE TRIGGER fail_body BEFORE UPDATE OF body_cipher ON mail BEGIN SELECT RAISE(ABORT,'test'); END"); return null; });
        assertThrows(DatabaseException.class, () -> mails.saveContent(account.getId(), id, content));
        assertEquals("TOP SECRET", content.getPlainText());
        assertEquals("TOP SECRET", mails.readContent(account.getId(), id).getPlainText());
    }

    @Test
    void failedPageCannotAdvanceCoverageOrLeavePartialRows() throws Exception {
        RemoteMessage invalid = new RemoteMessage(new RemoteMailHeader(new RemoteMailId(7, 99), null, null, null, "", java.time.Instant.now()), List.of());
        RemoteInboxPage broken = new RemoteInboxPage(7, 1, 2, List.of(page(7, 1, 1, false).getMessages().getFirst(), invalid), false);
        assertThrows(MailFetchException.class, () -> mails.saveInboxPage(account.getId(), broken));
        assertEquals(0, count("mail"));
        assertTrue(mails.readInbox(account.getId(), null, 50).isNeedsRemote());
    }

    @Test
    void accountCannotReadAnotherAccountsMail() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
        UUID id = mails.readInbox(account.getId(), null, 50).getItems().getFirst().getId();
        Account other = createAccount("second@example.com");
        assertThrows(MailFetchException.class, () -> mails.readContent(other.getId(), id));
        assertThrows(MailFetchException.class, () -> mails.markRead(other.getId(), id));
    }

    @Test
    void removedMessagesAreReconciledAndReadStateSurvivesRefresh() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 3, false));
        UUID first = mails.readInbox(account.getId(), null, 50).getItems().getFirst().getId();
        mails.markRead(account.getId(), first);
        mails.saveInboxPage(account.getId(), new RemoteInboxPage(1, 1, 3,
                page(1, 1, 3, false).getMessages().stream().filter(m -> m.getHeader().getRemoteId().getUid() != 2).toList(), false));
        assertEquals(2, count("mail"));
        assertTrue(mails.findHeader(account.getId(), first).isRead());
    }

    @Test
    void refreshGapDoesNotClaimUnfetchedCoverage() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 50, false));
        mails.saveInboxPage(account.getId(), page(1, 101, 150, true));
        InboxCursor cursor = mails.readInbox(account.getId(), null, 50).getNextCursor();
        InboxPage gap = mails.readInbox(account.getId(), cursor, 50);
        assertTrue(gap.isNeedsRemote());
        assertTrue(gap.getItems().isEmpty());
        assertEquals(100, count("mail"));
    }
}
