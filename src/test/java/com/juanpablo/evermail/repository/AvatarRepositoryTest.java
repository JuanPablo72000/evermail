package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.model.AvatarPhoto;
import com.juanpablo.evermail.support.BackendFixture;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AvatarRepositoryTest extends BackendFixture {
    private AvatarRepository repository() { return new AvatarRepository(transactions, accounts, security, keys); }
    private final Instant now = Instant.parse("2026-11-06T12:00:00Z");

    @Test void encryptsBinariesAndBindsThemToAccountAndSender() throws Exception {
        var repository = repository();
        var photo = new AvatarPhoto(new byte[]{1, 2, 3, 4}, AvatarPhoto.Status.AVAILABLE);
        try (var reservation = repository.reserve("sender@example.com")) {
            repository.save(account.getId(), "sender@example.com", photo, now, reservation);
        }
        byte[] cipher = transactions.read(c -> Sql.one(c, "SELECT photo_cipher FROM avatar_cache", row -> row.getBytes(1)));
        assertFalse(Arrays.equals(photo.png(), cipher));
        assertArrayEquals(photo.png(), repository.read(account.getId(), "SENDER@example.com", now).png());
        var second = createAccount("second@example.com");
        assertNull(repository.read(second.getId(), "sender@example.com", now));
        transactions.write(c -> Sql.update(c, "UPDATE avatar_cache SET sender_id=?", AvatarRepository.senderId("other@example.com")));
        assertThrows(CryptoException.class, () -> repository.read(account.getId(), "other@example.com", now));
    }

    @Test void expiresNegativeResultsAndClearsPhotosWithoutDeletingAccountOrKeys() throws Exception {
        var repository = repository();
        try (var reservation = repository.reserve("sender@example.com")) {
            repository.save(account.getId(), "sender@example.com", AvatarPhoto.empty(AvatarPhoto.Status.ABSENT), now, reservation);
        }
        assertEquals(AvatarPhoto.Status.ABSENT, repository.read(account.getId(), "sender@example.com", now.plusSeconds(599)).status());
        assertNull(repository.read(account.getId(), "sender@example.com", now.plusSeconds(600)));
        mails.clearInboxCache();
        assertEquals(0, count("avatar_cache"));
        assertEquals(1, count("account"));
        assertNotNull(keys.read(account.getKeyRef()));
    }

    @Test void deletingAccountCascadesToItsPhotos() throws Exception {
        var repository = repository();
        try (var reservation = repository.reserve("sender@example.com")) {
            repository.save(account.getId(), "sender@example.com", AvatarPhoto.empty(AvatarPhoto.Status.ABSENT), now, reservation);
        }
        accounts.deleteLocal(account.getId());
        assertEquals(0, count("avatar_cache"));
    }

    @Test void upgradesVersionTwoWithoutLosingAccountsOrMessages() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 3, false));
        transactions.write(c -> {
            Sql.update(c, "DROP TABLE avatar_cache"); Sql.update(c, "PRAGMA user_version=2"); return null;
        });
        new DatabaseMigrator(transactions).migrate();
        assertEquals(1, count("account")); assertEquals(3, count("mail")); assertEquals(0, count("avatar_cache"));
        assertEquals(3, (int) transactions.read(c -> Sql.one(c, "PRAGMA user_version", row -> row.getInt(1))));
    }
}
