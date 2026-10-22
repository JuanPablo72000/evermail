package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

public class SqliteConnectionProvider implements AutoCloseable {
    private final Connection connection;

    public SqliteConnectionProvider(Path path) throws DatabaseException {
        try {
            Path absolute = path.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=500");
            }
        } catch (Exception e) {
            throw new DatabaseException(ErrorCode.DB_CONNECTION_FAILED, "Cannot open local database", e);
        }
    }

    Connection connection() {
        return connection;
    }

    @Override
    public void close() throws DatabaseException {
        try {
            connection.close();
        } catch (Exception e) {
            throw new DatabaseException(ErrorCode.DB_CONNECTION_FAILED, "Cannot close database", e);
        }
    }
}
