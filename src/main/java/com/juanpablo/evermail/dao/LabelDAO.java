package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.Label;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class LabelDAO {

    private final SqliteConnectionProvider connectionProvider;

    public LabelDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Label findById(int idLabel) throws DatabaseException {
        String sql = "SELECT id_label, id_account, name FROM label WHERE id_label = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idLabel);
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

    public List<Label> findByAccount(int idAccount) throws DatabaseException {
        String sql = "SELECT id_label, id_account, name FROM label WHERE id_account = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idAccount);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Label> labels = new ArrayList<>();
                    while (resultSet.next()) {
                        labels.add(mapRow(resultSet));
                    }
                    return labels;
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public int insert(Label label) throws DatabaseException {
        String sql = "INSERT INTO label (id_account, name) VALUES (?, ?)";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, label.getIdAccount());
                statement.setString(2, label.getName());

                statement.executeUpdate();

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                    throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "No se generó un id_label al insertar.");
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void delete(int idLabel) throws DatabaseException {
        String sql = "DELETE FROM label WHERE id_label = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idLabel);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    private Label mapRow(ResultSet resultSet) throws SQLException {
        Label label = new Label();
        label.setIdLabel(resultSet.getInt("id_label"));
        label.setIdAccount(resultSet.getInt("id_account"));
        label.setName(resultSet.getString("name"));
        return label;
    }
}