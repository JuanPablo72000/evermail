package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteConnectionProvider {

    private final Connection connection;

    public SqliteConnectionProvider() throws DatabaseException {
        try {
            ensureDbDirectoryExists();
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + AppConstants.DB_PATH);
            enableForeignKeys();
        } catch (SQLException e) {
            throw new DatabaseException(ErrorCode.DB_CONNECTION_FAILED, e);
        }
    }

    public synchronized Connection getConnection() {
        return connection;
    }

    public void close() throws DatabaseException {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new DatabaseException(ErrorCode.DB_CONNECTION_FAILED, e);
        }
    }

    private void ensureDbDirectoryExists() throws DatabaseException {
        File dbFile = new File(AppConstants.DB_PATH);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new DatabaseException(ErrorCode.DB_CONNECTION_FAILED,
                    "No se pudo crear el directorio de datos: " + parentDir.getAbsolutePath());
        }
    }

    private void enableForeignKeys() throws DatabaseException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
        } catch (SQLException e) {
            throw new DatabaseException(ErrorCode.DB_CONNECTION_FAILED, e);
        }
    }
}