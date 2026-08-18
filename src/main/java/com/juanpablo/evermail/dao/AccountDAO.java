package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.OAuthProvider;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Direct access to the {@code account} table. Pure CRUD — it persists the
 * (already-encrypted) token columns as opaque strings and the provider as a
 * plain TEXT value. It has no knowledge of OAuth or encryption; that
 * orchestration lives in {@code AccountRepository} (see uml-dao.md, note 4).
 */
public class AccountDAO {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SqliteConnectionProvider connectionProvider;

    public AccountDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public synchronized int insert(Account account) throws DatabaseException {
        String sql = """
                INSERT INTO account (id_profile, id_address, provider, signature, account_name,
                                     access_token, refresh_token, token_expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connectionProvider.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, account.getIdProfile());
            ps.setInt(2, account.getIdAddress());
            ps.setString(3, account.getProvider().name());
            ps.setString(4, account.getSignature());
            ps.setString(5, account.getAccountName());
            ps.setString(6, account.getAccessToken());
            ps.setString(7, account.getRefreshToken());
            ps.setString(8, account.getTokenExpiresAt().format(DATE_FORMAT));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED,
                        "Insert into account did not return a generated key");
            }
        } catch (SQLException e) {
            throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Failed to insert account", e);
        }
    }

    public synchronized Account findById(int idAccount) throws DatabaseException {
        String sql = "SELECT * FROM account WHERE id_account = ?";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setInt(1, idAccount);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Failed to find account by id", e);
        }
    }

    public synchronized List<Account> findAll() throws DatabaseException {
        String sql = "SELECT * FROM account";
        List<Account> accounts = new ArrayList<>();
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(mapRow(rs));
            }
            return accounts;
        } catch (SQLException e) {
            throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Failed to list accounts", e);
        }
    }

    public synchronized void update(Account account) throws DatabaseException {
        String sql = """
                UPDATE account
                SET id_profile = ?, id_address = ?, provider = ?, signature = ?, account_name = ?,
                    access_token = ?, refresh_token = ?, token_expires_at = ?
                WHERE id_account = ?
                """;
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setInt(1, account.getIdProfile());
            ps.setInt(2, account.getIdAddress());
            ps.setString(3, account.getProvider().name());
            ps.setString(4, account.getSignature());
            ps.setString(5, account.getAccountName());
            ps.setString(6, account.getAccessToken());
            ps.setString(7, account.getRefreshToken());
            ps.setString(8, account.getTokenExpiresAt().format(DATE_FORMAT));
            ps.setInt(9, account.getIdAccount());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Failed to update account", e);
        }
    }

    public synchronized void delete(int idAccount) throws DatabaseException {
        String sql = "DELETE FROM account WHERE id_account = ?";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setInt(1, idAccount);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Failed to delete account", e);
        }
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        Account account = new Account();
        account.setIdAccount(rs.getInt("id_account"));
        account.setIdProfile(rs.getInt("id_profile"));
        account.setIdAddress(rs.getInt("id_address"));
        account.setProvider(OAuthProvider.valueOf(rs.getString("provider")));
        account.setSignature(rs.getString("signature"));
        account.setAccountName(rs.getString("account_name"));
        account.setAccessToken(rs.getString("access_token"));
        account.setRefreshToken(rs.getString("refresh_token"));
        account.setTokenExpiresAt(LocalDateTime.parse(rs.getString("token_expires_at"), DATE_FORMAT));
        return account;
    }
}