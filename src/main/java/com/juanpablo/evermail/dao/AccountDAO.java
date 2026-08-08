package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccountDAO {

    private final SqliteConnectionProvider connectionProvider;

    public AccountDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Account findById(int idAccount) throws DatabaseException {
        String sql = "SELECT id_account, id_profile, id_address, signature, account_name, "
                + "access_token, refresh_token, token_expires_at FROM account WHERE id_account = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idAccount);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return null;
                    }
                    return mapRow(resultSet);
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public List<Account> findAll() throws DatabaseException {
        String sql = "SELECT id_account, id_profile, id_address, signature, account_name, "
                + "access_token, refresh_token, token_expires_at FROM account";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                List<Account> accounts = new ArrayList<>();
                while (resultSet.next()) {
                    accounts.add(mapRow(resultSet));
                }
                return accounts;
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public int insert(Account account) throws DatabaseException {
        String sql = "INSERT INTO account (id_profile, id_address, signature, account_name, "
                + "access_token, refresh_token, token_expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, account.getIdProfile());
                statement.setInt(2, account.getIdAddress());
                statement.setString(3, account.getSignature());
                statement.setString(4, account.getAccountName());
                statement.setString(5, account.getAccessToken());
                statement.setString(6, account.getRefreshToken());
                setTokenExpiresAt(statement, 7, account.getTokenExpiresAt());

                statement.executeUpdate();

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                    throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "No se generó un id_account al insertar.");
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void update(Account account) throws DatabaseException {
        String sql = "UPDATE account SET id_profile = ?, id_address = ?, signature = ?, account_name = ?, "
                + "access_token = ?, refresh_token = ?, token_expires_at = ? WHERE id_account = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, account.getIdProfile());
                statement.setInt(2, account.getIdAddress());
                statement.setString(3, account.getSignature());
                statement.setString(4, account.getAccountName());
                statement.setString(5, account.getAccessToken());
                statement.setString(6, account.getRefreshToken());
                setTokenExpiresAt(statement, 7, account.getTokenExpiresAt());
                statement.setInt(8, account.getIdAccount());

                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void delete(int idAccount) throws DatabaseException {
        String sql = "DELETE FROM account WHERE id_account = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idAccount);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    private void setTokenExpiresAt(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value.toString());
        }
    }

    private Account mapRow(ResultSet resultSet) throws SQLException {
        Account account = new Account();
        account.setIdAccount(resultSet.getInt("id_account"));
        account.setIdProfile(resultSet.getInt("id_profile"));
        account.setIdAddress(resultSet.getInt("id_address"));
        account.setSignature(resultSet.getString("signature"));
        account.setAccountName(resultSet.getString("account_name"));
        account.setAccessToken(resultSet.getString("access_token"));
        account.setRefreshToken(resultSet.getString("refresh_token"));

        String tokenExpiresAt = resultSet.getString("token_expires_at");
        account.setTokenExpiresAt(tokenExpiresAt != null ? LocalDateTime.parse(tokenExpiresAt) : null);

        return account;
    }
}