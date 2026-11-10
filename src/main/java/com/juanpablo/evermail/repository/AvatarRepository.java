package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.exception.EvermailException;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.KeyStoreService;
import com.juanpablo.evermail.util.SecurityUtil;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Session cache in the same budgeted SQLite database, encrypted with the account key. */
public final class AvatarRepository {
    private final TransactionManager transactions;
    private final AccountRepository accounts;
    private final SecurityUtil security;
    private final KeyStoreService keys;

    public AvatarRepository(TransactionManager transactions, AccountRepository accounts, SecurityUtil security, KeyStoreService keys) {
        this.transactions = transactions; this.accounts = accounts; this.security = security; this.keys = keys;
    }

    public static UUID senderId(String email) {
        return UUID.nameUUIDFromBytes(AvatarPhoto.address(email).getBytes(StandardCharsets.UTF_8));
    }

    public AvatarPhoto read(UUID accountId, String email, Instant now) throws EvermailException {
        UUID id = senderId(email);
        byte[] cipher = transactions.read(c -> Sql.one(c,
                "SELECT photo_cipher FROM avatar_cache WHERE id_account=? AND sender_id=? AND expires_at>?",
                row -> row.getBytes(1), accountId, id, now.toEpochMilli()));
        if (cipher == null) return null;
        byte[] png = security.decryptBinary(cipher, keys.read(accounts.require(accountId).getKeyRef()),
                new CryptoContext(accountId, id, "avatar"));
        return new AvatarPhoto(png, png.length == 0 ? AvatarPhoto.Status.ABSENT : AvatarPhoto.Status.AVAILABLE);
    }

    public TransactionManager.CacheReservation reserve(String email) throws EvermailException {
        return transactions.reserveAvatar(senderId(email), 96 * 1024);
    }

    public void save(UUID accountId, String email, AvatarPhoto photo, Instant now,
                     TransactionManager.CacheReservation reservation) throws EvermailException {
        UUID id = senderId(email);
        byte[] cipher = security.encryptBinary(photo.png(), keys.read(accounts.require(accountId).getKeyRef()),
                new CryptoContext(accountId, id, "avatar"));
        long expires = now.plusSeconds(photo.status() == AvatarPhoto.Status.AVAILABLE ? 86400 : 600).toEpochMilli();
        reservation.saveAvatar(cipher.length + 16 * 1024L, c -> {
            // Global count bound complements the shared physical byte limit.
            Sql.update(c, "DELETE FROM avatar_cache WHERE rowid IN (SELECT rowid FROM avatar_cache ORDER BY updated_at DESC LIMIT -1 OFFSET 127)");
            Sql.update(c, """
                    INSERT INTO avatar_cache(id_account,sender_id,photo_cipher,expires_at,updated_at) VALUES(?,?,?,?,?)
                    ON CONFLICT(id_account,sender_id) DO UPDATE SET photo_cipher=excluded.photo_cipher,
                    expires_at=excluded.expires_at,updated_at=excluded.updated_at
                    """, accountId, id, cipher, expires, now.toEpochMilli());
            return null;
        });
    }
}
