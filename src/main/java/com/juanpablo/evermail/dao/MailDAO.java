package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.Mail;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure CRUD over the mail table. bodyPlainText/bodyHTML are received and
 * returned exactly as stored — already encrypted as opaque Strings. This
 * class never touches SecurityUtil nor a SecretKey; that orchestration
 * lives exclusively in MailRepository.
 */
public class MailDAO {

    private final SqliteConnectionProvider connectionProvider;

    public MailDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Mail findById(int idMail) throws DatabaseException {
        String sql = "SELECT id_mail, id_account, id_sender_address, id_reply_to_mail, server_message_id, "
                + "subject, body_plain_text, body_html, date_received FROM mail WHERE id_mail = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idMail);
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

    /**
     * Returns the most recent mails for an account, newest first, capped at
     * {@code limit} — used to satisfy the "last 50 received/sent emails"
     * requirement without loading the entire history into memory.
     */
    public List<Mail> findByAccount(int idAccount, int limit) throws DatabaseException {
        String sql = "SELECT id_mail, id_account, id_sender_address, id_reply_to_mail, server_message_id, "
                + "subject, body_plain_text, body_html, date_received FROM mail "
                + "WHERE id_account = ? ORDER BY date_received DESC LIMIT ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idAccount);
                statement.setInt(2, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Mail> mails = new ArrayList<>();
                    while (resultSet.next()) {
                        mails.add(mapRow(resultSet));
                    }
                    return mails;
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public int insert(Mail mail) throws DatabaseException {
        String sql = "INSERT INTO mail (id_account, id_sender_address, id_reply_to_mail, server_message_id, "
                + "subject, body_plain_text, body_html, date_received) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, mail.getIdAccount());
                statement.setInt(2, mail.getIdSenderAddress());
                setNullableInt(statement, 3, mail.getIdReplyToMail());
                statement.setString(4, mail.getServerMessageId());
                statement.setString(5, mail.getSubject());
                statement.setString(6, mail.getBodyPlainText());
                statement.setString(7, mail.getBodyHTML());
                statement.setString(8, mail.getDateReceived().toString());

                statement.executeUpdate();

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                    throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "No se generó un id_mail al insertar.");
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void delete(int idMail) throws DatabaseException {
        String sql = "DELETE FROM mail WHERE id_mail = ?";

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

    private void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private Mail mapRow(ResultSet resultSet) throws SQLException {
        Mail mail = new Mail();
        mail.setIdMail(resultSet.getInt("id_mail"));
        mail.setIdAccount(resultSet.getInt("id_account"));
        mail.setIdSenderAddress(resultSet.getInt("id_sender_address"));

        int idReplyToMail = resultSet.getInt("id_reply_to_mail");
        mail.setIdReplyToMail(resultSet.wasNull() ? null : idReplyToMail);

        mail.setServerMessageId(resultSet.getString("server_message_id"));
        mail.setSubject(resultSet.getString("subject"));
        mail.setBodyPlainText(resultSet.getString("body_plain_text"));
        mail.setBodyHTML(resultSet.getString("body_html"));
        mail.setDateReceived(LocalDate.parse(resultSet.getString("date_received")));

        return mail;
    }
}