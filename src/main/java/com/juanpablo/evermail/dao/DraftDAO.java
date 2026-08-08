package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.Draft;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure CRUD over the draft table. bodyPlainText is received and returned
 * exactly as stored — already encrypted as an opaque String. This class
 * never touches SecurityUtil nor a SecretKey; that orchestration lives
 * exclusively in DraftRepository.
 */
public class DraftDAO {

    private final SqliteConnectionProvider connectionProvider;

    public DraftDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Draft findById(int idDraft) throws DatabaseException {
        String sql = "SELECT id_draft, id_account, subject, body_plain_text, last_edited "
                + "FROM draft WHERE id_draft = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idDraft);
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

    public List<Draft> findByAccount(int idAccount) throws DatabaseException {
        String sql = "SELECT id_draft, id_account, subject, body_plain_text, last_edited "
                + "FROM draft WHERE id_account = ? ORDER BY last_edited DESC";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idAccount);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Draft> drafts = new ArrayList<>();
                    while (resultSet.next()) {
                        drafts.add(mapRow(resultSet));
                    }
                    return drafts;
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public int insert(Draft draft) throws DatabaseException {
        String sql = "INSERT INTO draft (id_account, subject, body_plain_text, last_edited) VALUES (?, ?, ?, ?)";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, draft.getIdAccount());
                statement.setString(2, draft.getSubject());
                statement.setString(3, draft.getBodyPlainText());
                statement.setString(4, draft.getLastEdited().toString());

                statement.executeUpdate();

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                    throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "No se generó un id_draft al insertar.");
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void update(Draft draft) throws DatabaseException {
        String sql = "UPDATE draft SET subject = ?, body_plain_text = ?, last_edited = ? WHERE id_draft = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, draft.getSubject());
                statement.setString(2, draft.getBodyPlainText());
                statement.setString(3, draft.getLastEdited().toString());
                statement.setInt(4, draft.getIdDraft());

                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void delete(int idDraft) throws DatabaseException {
        String sql = "DELETE FROM draft WHERE id_draft = ?";

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

    private Draft mapRow(ResultSet resultSet) throws SQLException {
        Draft draft = new Draft();
        draft.setIdDraft(resultSet.getInt("id_draft"));
        draft.setIdAccount(resultSet.getInt("id_account"));
        draft.setSubject(resultSet.getString("subject"));
        draft.setBodyPlainText(resultSet.getString("body_plain_text"));
        draft.setLastEdited(LocalDate.parse(resultSet.getString("last_edited")));
        return draft;
    }
}