package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.support.BackendFixture;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionCacheTest extends BackendFixture {
    @Test void cleanupReclaimsDiskResetsReadAndPreservesAccountKeysAndUncertainSend() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
        UUID id = mails.readInbox(account.getId(), null, 50).getItems().getFirst().getId();
        mails.saveContent(account.getId(), id, new RemoteMailContent("x".repeat(900_000), List.of()));
        mails.markRead(account.getId(), id);
        gateway.outcome = com.juanpablo.evermail.service.SmtpSession.Outcome.UNKNOWN;
        sender.send(request(UUID.randomUUID()), budget());
        long outboxBefore = count("outbox_message");
        long sizeBefore = Files.size(directory.resolve("test.db"));
        mails.clearInboxCache();
        assertTrue(Files.size(directory.resolve("test.db")) < sizeBefore / 2);
        assertEquals(0, count("inbox_state"));
        assertTrue(mails.readInbox(account.getId(), null, 50).getItems().isEmpty());
        assertEquals(outboxBefore, count("outbox_message"));
        assertNotNull(accounts.require(account.getId()));
        assertNotNull(keys.read(account.getKeyRef()));
        mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
        var restored = mails.readInbox(account.getId(), null, 50).getItems().getFirst();
        assertFalse(restored.isRead());
        assertNull(mails.readContent(account.getId(), restored.getId()));
        mails.clearInboxCache(); // Repeatable.
    }
}
