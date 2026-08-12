package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.MailLabel;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MailLabelDAO {

    private final SqliteConnectionProvider connectionProvider;

    public MailLabelDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<MailLabel> findByMail(int idMail) throws DatabaseException {
        String sql = "SELECT id_mail, id_label, is_read FROM mail_label WHERE id_mail = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idMail);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<MailLabel> mailLabels = new ArrayList<>();
                    while (resultSet.next()) {
                        mailLabels.add(mapRow(resultSet));
                    }
                    return mailLabels;
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void insert(MailLabel mailLabel) throws DatabaseException {
        String sql = "INSERT INTO mail_label (id_mail, id_label, is_read) VALUES (?, ?, ?)";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, mailLabel.getIdMail());
                statement.setInt(2, mailLabel.getIdLabel());
                statement.setInt(3, mailLabel.isRead() ? 1 : 0);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void updateIsRead(int idMail, int idLabel, boolean isRead) throws DatabaseException {
        String sql = "UPDATE mail_label SET is_read = ? WHERE id_mail = ? AND id_label = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, isRead ? 1 : 0);
                statement.setInt(2, idMail);
                statement.setInt(3, idLabel);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void delete(int idMail, int idLabel) throws DatabaseException {
        String sql = "DELETE FROM mail_label WHERE id_mail = ? AND id_label = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idMail);
                statement.setInt(2, idLabel);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    /**
     * Counts mail_label rows for the given label where is_read = 0. Added to
     * support LabelRepository.getUnreadCount() (see uml-repositories.md, note
     * 6) — no prior method here allowed filtering by id_label, only by id_mail.
     */
    public int countUnreadByLabel(int idLabel) throws DatabaseException {
        String sql = "SELECT COUNT(*) FROM mail_label WHERE id_label = ? AND is_read = 0";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idLabel);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getInt(1);
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    private MailLabel mapRow(ResultSet resultSet) throws SQLException {
        MailLabel mailLabel = new MailLabel();
        mailLabel.setIdMail(resultSet.getInt("id_mail"));
        mailLabel.setIdLabel(resultSet.getInt("id_label"));
        mailLabel.setRead(resultSet.getInt("is_read") == 1);
        return mailLabel;
    }
}