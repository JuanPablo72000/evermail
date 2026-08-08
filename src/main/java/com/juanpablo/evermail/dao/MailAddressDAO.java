package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.MailAddress;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MailAddressDAO {

    private final SqliteConnectionProvider connectionProvider;

    public MailAddressDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<MailAddress> findByMail(int idMail) throws DatabaseException {
        String sql = "SELECT id_mail, id_address, recipient_type FROM mail_address WHERE id_mail = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idMail);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<MailAddress> mailAddresses = new ArrayList<>();
                    while (resultSet.next()) {
                        mailAddresses.add(mapRow(resultSet));
                    }
                    return mailAddresses;
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void insertBatch(List<MailAddress> addresses) throws DatabaseException {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO mail_address (id_mail, id_address, recipient_type) VALUES (?, ?, ?)";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (MailAddress address : addresses) {
                        statement.setInt(1, address.getIdMail());
                        statement.setInt(2, address.getIdAddress());
                        statement.setString(3, address.getRecipientType());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void delete(int idMail) throws DatabaseException {
        String sql = "DELETE FROM mail_address WHERE id_mail = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idMail);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    private MailAddress mapRow(ResultSet resultSet) throws SQLException {
        MailAddress mailAddress = new MailAddress();
        mailAddress.setIdMail(resultSet.getInt("id_mail"));
        mailAddress.setIdAddress(resultSet.getInt("id_address"));
        mailAddress.setRecipientType(resultSet.getString("recipient_type"));
        return mailAddress;
    }
}