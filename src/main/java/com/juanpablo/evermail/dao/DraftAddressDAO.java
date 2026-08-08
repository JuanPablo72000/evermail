package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.DraftAddress;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DraftAddressDAO {

    private final SqliteConnectionProvider connectionProvider;

    public DraftAddressDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<DraftAddress> findByDraft(int idDraft) throws DatabaseException {
        String sql = "SELECT id_draft, id_address, recipient_type FROM draft_address WHERE id_draft = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idDraft);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<DraftAddress> draftAddresses = new ArrayList<>();
                    while (resultSet.next()) {
                        draftAddresses.add(mapRow(resultSet));
                    }
                    return draftAddresses;
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void insertBatch(List<DraftAddress> addresses) throws DatabaseException {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO draft_address (id_draft, id_address, recipient_type) VALUES (?, ?, ?)";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (DraftAddress address : addresses) {
                        statement.setInt(1, address.getIdDraft());
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

    public void delete(int idDraft) throws DatabaseException {
        String sql = "DELETE FROM draft_address WHERE id_draft = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idDraft);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    private DraftAddress mapRow(ResultSet resultSet) throws SQLException {
        DraftAddress draftAddress = new DraftAddress();
        draftAddress.setIdDraft(resultSet.getInt("id_draft"));
        draftAddress.setIdAddress(resultSet.getInt("id_address"));
        draftAddress.setRecipientType(resultSet.getString("recipient_type"));
        return draftAddress;
    }
}