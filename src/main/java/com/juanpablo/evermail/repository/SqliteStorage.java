package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.config.StorageBudget;
import java.sql.Connection;
import java.sql.SQLException;

/** SQLite-specific enforcement; callers serialize it with transactions on the same connection. */
final class SqliteStorage {
    private final StorageBudget budget;

    SqliteStorage(StorageBudget budget) { this.budget = budget; }

    void configure(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=DELETE");
            statement.execute("PRAGMA temp_store=MEMORY");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA auto_vacuum=FULL");
        }
        if (pragma(connection, "auto_vacuum") != 1) {
            // Existing databases need a one-time rebuild. The budget reserves both extra copies.
            enforce(connection, 0);
            try (var statement = connection.createStatement()) { statement.execute("VACUUM"); }
        } else {
            // An application update or lower disk space may require cache cleanup on the first write.
            enforceCleanup(connection);
        }
    }

    long targetBytes(long reservedBytes) throws Exception { return budget.databaseLimit() - reservedBytes; }

    void enforceCleanup(Connection connection) throws Exception {
        long pages = Math.max(1, pragma(connection, "page_count"));
        if (pages * pragma(connection, "page_size") > budget.cleanupLimit()) throw StorageBudget.exhausted();
        try (var statement = connection.createStatement()) { statement.execute("PRAGMA max_page_count=" + pages); }
    }

    long enforce(Connection connection, long reservedBytes) throws Exception {
        return enforceMaximum(connection, targetBytes(reservedBytes));
    }

    /** Reuse the measurement made immediately before this write, after any cache cleanup. */
    long enforceMaximum(Connection connection, long maximumBytes) throws Exception {
        long pageSize = pragma(connection, "page_size");
        long maximum = maximumBytes / pageSize;
        if (maximum < Math.max(1, pragma(connection, "page_count"))) throw StorageBudget.exhausted();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA max_page_count=" + maximum)) {
            if (!result.next() || result.getLong(1) > maximum) throw StorageBudget.exhausted();
        }
        return maximum * pageSize;
    }

    static long pragma(Connection connection, String name) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA " + name)) {
            if (!result.next()) throw new SQLException("Missing SQLite storage information");
            return result.getLong(1);
        }
    }
}
