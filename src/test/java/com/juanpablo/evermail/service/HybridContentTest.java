package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.DatabaseMigrator;
import com.juanpablo.evermail.support.BackendFixture;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HybridContentTest extends BackendFixture {
    private UUID seed() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
        return mails.readInbox(account.getId(), null, 50).getItems().getFirst().getId();
    }

    @Test void roundTripsEncryptedHtmlAndUsesOfflineCacheUnderContentBudget() throws Exception {
        UUID id = seed();
        gateway.remoteContent = new RemoteMailContent("PRIVATE", "<h2>PRIVATE HTML</h2>", true, List.of());
        InboxService service = new InboxService(gateway, mails, coordinator);
        var downloaded = service.loadContent(account.getId(), id, Deadline.after(AppConstants.CONTENT_BUDGET));
        assertEquals("<h2>PRIVATE HTML</h2>", downloaded.getHtml());
        assertTrue(downloaded.isBlockedRemoteImages());
        String cipher = transactions.read(c -> Sql.one(c, "SELECT body_cipher FROM mail WHERE id_mail=?", r -> r.getString(1), id));
        assertFalse(cipher.contains("PRIVATE"));
        accounts.setStatus(account.getId(), AccountStatus.REAUTH_REQUIRED);
        gateway.failContent = true;
        Deadline deadline = Deadline.after(AppConstants.CONTENT_BUDGET);
        var cached = service.loadContent(account.getId(), id, deadline);
        var display = new MailPresentationService().prepare(cached, deadline);
        assertTrue(display.getDocument().contains("PRIVATE HTML"));
        assertEquals(1, gateway.inboxRequests.get());
        deadline.check();
    }

    @Test void v1MigrationPreservesCiphertextAndOffersExplicitUpgrade() throws Exception {
        UUID id = seed();
        String cipher = security.encrypt("Old text", keys.read(account.getKeyRef()), new CryptoContext(account.getId(), id, "body"));
        transactions.write(c -> {
            Sql.update(c, "UPDATE mail SET body_cipher=? WHERE id_mail=?", cipher, id);
            Sql.update(c, "ALTER TABLE mail DROP COLUMN body_format");
            Sql.update(c, "PRAGMA user_version=1"); return null;
        });
        new DatabaseMigrator(transactions).migrate(); new DatabaseMigrator(transactions).migrate();
        assertEquals(cipher, transactions.read(c -> Sql.one(c, "SELECT body_cipher FROM mail WHERE id_mail=?", r -> r.getString(1), id)));
        InboxService service = new InboxService(gateway, mails, coordinator);
        var old = service.loadContent(account.getId(), id, budget());
        assertTrue(old.isLegacyTextOnly()); assertEquals("Old text", old.getPlainText());
        assertEquals(0, gateway.inboxRequests.get());
        gateway.failContent = true;
        assertThrows(MailFetchException.class, () -> service.reloadContent(account.getId(), id, budget()));
        assertEquals("Old text", mails.readContent(account.getId(), id).getPlainText());
        gateway.failContent = false;
        gateway.remoteContent = new RemoteMailContent("New text", "<b>New text</b>", false, List.of());
        assertFalse(service.reloadContent(account.getId(), id, budget()).isLegacyTextOnly());
        assertEquals("<b>New text</b>", mails.readContent(account.getId(), id).getHtml());
    }

    @Test void plainOutgoingBodyRemainsPlainAndDoesNotNeedFormatUpgrade() throws Exception {
        sender.send(request(UUID.randomUUID()), budget());
        UUID id = transactions.read(c -> Sql.one(c, "SELECT id_mail FROM mail WHERE direction='SENT'", r -> UUID.fromString(r.getString(1))));
        MailContent content = mails.readContent(account.getId(), id);
        assertEquals("Private body", content.getPlainText());
        assertNull(content.getHtml()); assertFalse(content.isLegacyTextOnly());
    }
}
