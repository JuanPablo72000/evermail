package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.Attachment;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class AttachmentDAO {

    private final SqliteConnectionProvider connectionProvider;

    public AttachmentDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<Attachment> findByMail(int idMail) throws DatabaseException {
        String sql = "SELECT id_attachment, id_mail, file_name, mime_type, size_bytes, file_path "
                + "FROM attachment WHERE id_mail = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idMail);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Attachment> attachments = new ArrayList<>();
                    while (resultSet.next()) {
                        attachments.add(mapRow(resultSet));
                    }
                    return attachments;
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    /**
     * Inserts attachment metadata during inbox sync. filePath is typically
     * null at this point — the file is only downloaded on demand, later,
     * via {@link #updateFilePath(int, String)}.
     */
    public int insert(Attachment attachment) throws DatabaseException {
        String sql = "INSERT INTO attachment (id_mail, file_name, mime_type, size_bytes, file_path) "
                + "VALUES (?, ?, ?, ?, ?)";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, attachment.getIdMail());
                statement.setString(2, attachment.getFileName());
                statement.setString(3, attachment.getMimeType());
                statement.setInt(4, attachment.getSizeBytes());
                setNullableFilePath(statement, 5, attachment.getFilePath());

                statement.executeUpdate();

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                    throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "No se generó un id_attachment al insertar.");
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    /**
     * Persists the on-disk path once FileUtil.downloadAttachment() has
     * finished writing the encrypted file — closes the gap in the
     * on-demand download flow (insert happens without a path; this fills
     * it in afterward).
     */
    public void updateFilePath(int idAttachment, String filePath) throws DatabaseException {
        String sql = "UPDATE attachment SET file_path = ? WHERE id_attachment = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, filePath);
                statement.setInt(2, idAttachment);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void delete(int idAttachment) throws DatabaseException {
        String sql = "DELETE FROM attachment WHERE id_attachment = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idAttachment);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    private void setNullableFilePath(PreparedStatement statement, int index, String filePath) throws SQLException {
        if (filePath == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, filePath);
        }
    }

    private Attachment mapRow(ResultSet resultSet) throws SQLException {
        Attachment attachment = new Attachment();
        attachment.setIdAttachment(resultSet.getInt("id_attachment"));
        attachment.setIdMail(resultSet.getInt("id_mail"));
        attachment.setFileName(resultSet.getString("file_name"));
        attachment.setMimeType(resultSet.getString("mime_type"));
        attachment.setSizeBytes(resultSet.getInt("size_bytes"));
        attachment.setFilePath(resultSet.getString("file_path"));
        return attachment;
    }
}