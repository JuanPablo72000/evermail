package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.KeyStoreService;
import com.juanpablo.evermail.util.SecurityUtil;
import java.util.*;

public class AccountRepository {
    private final TransactionManager transactions;
    private final AccountDAO dao = new AccountDAO();
    private final SecurityUtil security;
    private final KeyStoreService keys;

    public AccountRepository(TransactionManager transactions, SecurityUtil security, KeyStoreService keys) {
        this.transactions = transactions;
        this.security = security;
        this.keys = keys;
    }

    public Account find(UUID id) throws EvermailException {
        AccountDAO.Row row = transactions.read(c -> dao.find(c, id));
        return row == null ? null : row.getAccount();
    }

    public Account require(UUID id) throws EvermailException {
        Account account = find(id);
        if (account == null || account.getStatus() == AccountStatus.DISCONNECTING
                || account.getStatus() == AccountStatus.PROVISIONING) {
            throw new SessionException(ErrorCode.SESSION_UNAVAILABLE, "Account is unavailable");
        }
        return account;
    }

    public Account findByIdentity(Identity identity) throws EvermailException {
        AccountDAO.Row row = transactions.read(c -> dao.findByIdentity(c, identity));
        return row == null ? null : row.getAccount();
    }

    public List<Account> list() throws EvermailException {
        return transactions.read(c -> dao.list(c).stream().map(AccountDAO.Row::getAccount).toList());
    }

    public Account beginProvisioning(Identity identity) throws EvermailException {
        Account account = new Account(UUID.randomUUID(), identity.getProvider(), identity.getSubject(),
                identity.getEmail(), identity.getDisplayName(), UUID.randomUUID().toString(), AccountStatus.PROVISIONING);
        transactions.write(c -> {
            dao.insert(c, new AccountDAO.Row(account, null, null, null));
            return null;
        });
        return account;
    }

    public void activate(Account account, OAuthCredentials credentials) throws EvermailException {
        if (credentials.getRefreshToken() == null || credentials.getRefreshToken().isBlank()
                || credentials.getAccessToken() == null || credentials.getExpiresAt() == null) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_INVALID_CREDENTIALS, "Reusable credentials are required");
        }
        var key = keys.read(account.getKeyRef());
        String access = security.encrypt(credentials.getAccessToken(), key, new CryptoContext(account.getId(), account.getId(), "access"));
        String refresh = security.encrypt(credentials.getRefreshToken(), key, new CryptoContext(account.getId(), account.getId(), "refresh"));
        transactions.write(c -> {
            AccountDAO.Row current = dao.find(c, account.getId());
            if (current == null || current.getAccount().getStatus() == AccountStatus.DISCONNECTING) {
                throw new SessionException(ErrorCode.SESSION_UNAVAILABLE, "Account is closing");
            }
            dao.update(c, new AccountDAO.Row(account.withStatus(AccountStatus.ACTIVE), access, refresh, credentials.getExpiresAt()));
            return null;
        });
    }

    public OAuthCredentials readCredentials(UUID id) throws EvermailException {
        Account account = require(id);
        AccountDAO.Row row = transactions.read(c -> dao.find(c, id));
        var key = keys.read(account.getKeyRef());
        if (row.getAccessCipher() == null || row.getRefreshCipher() == null) {
            throw new OAuthAuthenticationException(ErrorCode.REAUTH_REQUIRED, "Authorization is required");
        }
        return new OAuthCredentials(security.decrypt(row.getAccessCipher(), key, new CryptoContext(id, id, "access")),
                security.decrypt(row.getRefreshCipher(), key, new CryptoContext(id, id, "refresh")), row.getExpiresAt());
    }

    public void setStatus(UUID id, AccountStatus status) throws EvermailException {
        transactions.write(c -> {
            if (Sql.update(c, "UPDATE account SET status=? WHERE id_account=?", status, id) != 1) {
                throw new SessionException(ErrorCode.SESSION_UNAVAILABLE, "Account not found");
            }
            return null;
        });
    }

    public String legacyKeyRef(UUID id) throws EvermailException {
        return transactions.read(c -> {
            boolean hasMap = Sql.one(c, "SELECT name FROM sqlite_master WHERE type='table' AND name='legacy_account_map'",
                    rs -> rs.getString(1)) != null;
            return hasMap ? Sql.one(c, "SELECT legacy_id FROM legacy_account_map WHERE account_id=?",
                    rs -> rs.getString(1), id) : null;
        });
    }

    public void deleteLocal(UUID id) throws EvermailException {
        String legacy = legacyKeyRef(id);
        transactions.write(c -> {
            if (legacy != null) {
                Sql.update(c, "DELETE FROM legacy_account WHERE id_account=?", Integer.parseInt(legacy));
                // The legacy account row is gone, so it cannot be reimported on the next startup.
                Sql.update(c, "DELETE FROM legacy_account_map WHERE account_id=?", id);
            }
            Sql.update(c, "DELETE FROM mail WHERE id_account=?", id);
            Sql.update(c, "DELETE FROM outbox_message WHERE id_account=?", id);
            Sql.update(c, "DELETE FROM account WHERE id_account=?", id);
            return null;
        });
    }
}
