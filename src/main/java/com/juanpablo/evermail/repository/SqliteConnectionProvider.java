package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.config.StorageBudget;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

public class SqliteConnectionProvider implements AutoCloseable {
    private final Connection connection;
    private final SqliteStorage storage;
    private final FileChannel lockChannel;
    private final FileLock lock;

    public SqliteConnectionProvider(Path path) throws DatabaseException {
        this(StorageBudget.local(path));
    }

    public SqliteConnectionProvider(StorageBudget budget) throws DatabaseException {
        Connection opened = null;
        FileChannel channel = null;
        FileLock acquired = null;
        try {
            budget.checkOpening();
            Path absolute = budget.database();
            channel = FileChannel.open(absolute.resolveSibling(absolute.getFileName() + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            acquired = channel.tryLock();
            if (acquired == null) throw new IllegalStateException("Database is already in use");
            opened = DriverManager.getConnection("jdbc:sqlite:" + absolute);
            storage = new SqliteStorage(budget);
            storage.configure(opened);
            try (var statement = opened.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=500");
            }
            connection = opened;
            lockChannel = channel;
            lock = acquired;
        } catch (Exception e) {
            for (AutoCloseable resource : new AutoCloseable[]{opened, acquired, channel}) {
                if (resource != null) try { resource.close(); } catch (Exception close) { e.addSuppressed(close); }
            }
            if (e instanceof DatabaseException domain) throw domain;
            throw new DatabaseException(ErrorCode.DB_CONNECTION_FAILED, "Cannot open local database", e);
        }
    }

    Connection connection() {
        return connection;
    }

    SqliteStorage storage() { return storage; }

    @Override
    public void close() throws DatabaseException {
        try {
            try { connection.close(); }
            finally {
                try { if (lock.isValid()) lock.release(); }
                finally { lockChannel.close(); }
            }
        } catch (Exception e) {
            throw new DatabaseException(ErrorCode.DB_CONNECTION_FAILED, "Cannot close database", e);
        }
    }
}
