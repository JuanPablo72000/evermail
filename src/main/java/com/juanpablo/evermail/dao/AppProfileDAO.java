package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.AppProfile;
import com.juanpablo.evermail.repository.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AppProfileDAO {

    private final SqliteConnectionProvider connectionProvider;

    public AppProfileDAO(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public AppProfile findById(int idProfile) throws DatabaseException {
        String sql = "SELECT id_profile, theme, sync_interval_minutes, language, notifications_enabled "
                + "FROM app_profile WHERE id_profile = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idProfile);
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

    public int insert(AppProfile profile) throws DatabaseException {
        String sql = "INSERT INTO app_profile (theme, sync_interval_minutes, language, notifications_enabled) "
                + "VALUES (?, ?, ?, ?)";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, profile.getTheme());
                statement.setInt(2, profile.getSyncIntervalMinutes());
                statement.setString(3, profile.getLanguage());
                statement.setInt(4, profile.isNotificationsEnabled() ? 1 : 0);

                statement.executeUpdate();

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                    throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "No se generó un id_profile al insertar.");
                }
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void update(AppProfile profile) throws DatabaseException {
        String sql = "UPDATE app_profile SET theme = ?, sync_interval_minutes = ?, language = ?, "
                + "notifications_enabled = ? WHERE id_profile = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, profile.getTheme());
                statement.setInt(2, profile.getSyncIntervalMinutes());
                statement.setString(3, profile.getLanguage());
                statement.setInt(4, profile.isNotificationsEnabled() ? 1 : 0);
                statement.setInt(5, profile.getIdProfile());

                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    public void delete(int idProfile) throws DatabaseException {
        String sql = "DELETE FROM app_profile WHERE id_profile = ?";

        synchronized (connectionProvider) {
            Connection connection = connectionProvider.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, idProfile);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, e);
            }
        }
    }

    private AppProfile mapRow(ResultSet resultSet) throws SQLException {
        AppProfile profile = new AppProfile();
        profile.setIdProfile(resultSet.getInt("id_profile"));
        profile.setTheme(resultSet.getString("theme"));
        profile.setSyncIntervalMinutes(resultSet.getInt("sync_interval_minutes"));
        profile.setLanguage(resultSet.getString("language"));
        profile.setNotificationsEnabled(resultSet.getInt("notifications_enabled") == 1);
        return profile;
    }
}