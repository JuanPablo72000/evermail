package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.KeyStoreService;
import com.juanpablo.evermail.util.SecurityUtil;
import lombok.Value;
import lombok.ToString;
import java.time.*;
import java.util.*;

/**
 * Version-zero rows remain in legacy_* tables, encrypted with their original keys.
 * IMAP UIDs are never invented from Message-ID; new inbox sync builds authoritative identity.
 */
public class LegacyImporter {
    private final TransactionManager transactions;
    private final AccountRepository accounts;
    private final SecurityUtil security;
    private final KeyStoreService keys;

    @Value
    @ToString(onlyExplicitlyIncluded = true)
    private static class LegacyAccount {
        int id;
        Identity identity;
        String access;
        String refresh;
        String expiry;
    }

    public LegacyImporter(TransactionManager transactions, AccountRepository accounts, SecurityUtil security, KeyStoreService keys) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.security = security;
        this.keys = keys;
    }

    public void importAccounts() throws EvermailException {
        boolean exists = transactions.read(c -> Sql.one(c,
                "SELECT name FROM sqlite_master WHERE type='table' AND name='legacy_account'", rs -> rs.getString(1)) != null);
        if (!exists) {
            return;
        }
        transactions.write(c -> {
            Sql.update(c, """
                    CREATE TABLE IF NOT EXISTS legacy_account_map(
                    legacy_id INTEGER PRIMARY KEY, account_id TEXT NOT NULL UNIQUE,
                    FOREIGN KEY(account_id) REFERENCES account(id_account) ON DELETE CASCADE)
                    """);
            return null;
        });
        List<LegacyAccount> old = transactions.read(c -> Sql.list(c, """
                SELECT a.*,e.email FROM legacy_account a JOIN legacy_email_address e ON e.id_address=a.id_address
                WHERE a.id_account NOT IN(SELECT legacy_id FROM legacy_account_map)
                """, rs -> new LegacyAccount(rs.getInt("id_account"),
                new Identity(OAuthProvider.valueOf(rs.getString("provider")), "legacy:" + rs.getString("email"),
                        rs.getString("email"), rs.getString("account_name")), rs.getString("access_token"),
                rs.getString("refresh_token"), rs.getString("token_expires_at"))));
        for (LegacyAccount previous : old) {
            var oldKey = keys.read(String.valueOf(previous.getId()));
            OAuthCredentials tokens = new OAuthCredentials(security.decryptLegacy(previous.getAccess(), oldKey),
                    security.decryptLegacy(previous.getRefresh(), oldKey),
                    LocalDateTime.parse(previous.getExpiry()).atZone(ZoneId.systemDefault()).toInstant());
            Account account = accounts.findByIdentity(previous.getIdentity());
            if (account == null) {
                account = accounts.beginProvisioning(previous.getIdentity());
                keys.create(account.getKeyRef());
            }
            accounts.activate(account, tokens);
            // Legacy tokens did not have a verified issuer+subject; require explicit reauthorization.
            accounts.setStatus(account.getId(), AccountStatus.REAUTH_REQUIRED);
            UUID accountId = account.getId();
            transactions.write(c -> {
                Sql.update(c, "INSERT INTO legacy_account_map VALUES(?,?)", previous.getId(), accountId);
                return null;
            });
        }
    }

    @Value
    public static class ArchivedHeader {
        int id;
        String subject;
        String date;
        String sender;
    }

    public List<ArchivedHeader> listArchived(UUID accountId) throws EvermailException {
        accounts.require(accountId);
        if (accounts.legacyKeyRef(accountId) == null) {
            return List.of();
        }
        return transactions.read(c -> Sql.list(c, """
                SELECT m.id_mail,m.subject,m.date_received,e.email FROM legacy_account_map a
                JOIN legacy_mail m ON m.id_account=a.legacy_id
                JOIN legacy_email_address e ON e.id_address=m.id_sender_address
                WHERE a.account_id=? ORDER BY m.date_received DESC,m.id_mail DESC
                """, rs -> new ArchivedHeader(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)), accountId));
    }

    /** Read-only access to preserved old bodies, separate from the UID-addressed inbox. */
    public String readArchivedBody(UUID accountId, int legacyMailId) throws EvermailException {
        accounts.require(accountId);
        String[] row = transactions.read(c -> Sql.one(c, """
                SELECT a.legacy_id,m.body_plain_text FROM legacy_account_map a
                JOIN legacy_mail m ON m.id_account=a.legacy_id WHERE a.account_id=? AND m.id_mail=?
                """, rs -> new String[]{rs.getString(1), rs.getString(2)}, accountId, legacyMailId));
        if (row == null) {
            throw new MailFetchException(ErrorCode.MAIL_NOT_FOUND, "Archived message not found");
        }
        return row[1] == null ? "" : security.decryptLegacy(row[1], keys.read(row[0]));
    }
}
