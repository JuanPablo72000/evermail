package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.model.*;
import lombok.Value;
import lombok.ToString;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class AccountDAO {
    @Value
    @ToString(onlyExplicitlyIncluded = true)
    public static class Row {
        Account account;
        String accessCipher;
        String refreshCipher;
        Instant expiresAt;
    }

    public Row find(Connection connection, UUID id) throws SQLException {
        return Sql.one(connection, "SELECT * FROM account WHERE id_account=?", this::map, id);
    }

    public Row findByIdentity(Connection connection, Identity identity) throws SQLException {
        return Sql.one(connection, "SELECT * FROM account WHERE provider=? AND provider_subject=?", this::map,
                identity.getProvider(), identity.getSubject());
    }

    public List<Row> list(Connection connection) throws SQLException {
        return Sql.list(connection, "SELECT * FROM account ORDER BY id_account", this::map);
    }

    public void insert(Connection connection, Row row) throws SQLException {
        Account account = row.getAccount();
        Sql.update(connection, "INSERT INTO account VALUES(?,?,?,?,?,?,?,?,?,?)",
                account.getId(), account.getProvider(), account.getProviderSubject(), account.getEmail(),
                account.getDisplayName(), account.getKeyRef(), row.getAccessCipher(), row.getRefreshCipher(),
                row.getExpiresAt(), account.getStatus());
    }

    public void update(Connection connection, Row row) throws SQLException {
        Account account = row.getAccount();
        if (Sql.update(connection, """
                UPDATE account SET provider_subject=?,email=?,display_name=?,access_token_cipher=?,
                refresh_token_cipher=?,token_expires_at=?,status=? WHERE id_account=?
                """, account.getProviderSubject(), account.getEmail(), account.getDisplayName(),
                row.getAccessCipher(), row.getRefreshCipher(), row.getExpiresAt(), account.getStatus(), account.getId()) != 1) {
            throw new SQLException("Account not found");
        }
    }

    private Row map(ResultSet rs) throws SQLException {
        Account account = new Account(UUID.fromString(rs.getString("id_account")),
                OAuthProvider.valueOf(rs.getString("provider")), rs.getString("provider_subject"),
                rs.getString("email"), rs.getString("display_name"), rs.getString("key_ref"),
                AccountStatus.valueOf(rs.getString("status")));
        Long expiry = rs.getObject("token_expires_at") == null ? null : rs.getLong("token_expires_at");
        return new Row(account, rs.getString("access_token_cipher"), rs.getString("refresh_token_cipher"),
                expiry == null ? null : Instant.ofEpochMilli(expiry));
    }
}
