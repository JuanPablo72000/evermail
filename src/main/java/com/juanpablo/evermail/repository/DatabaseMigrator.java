package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.exception.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DatabaseMigrator {
    private final TransactionManager transactions;
    private static final int VERSION = 1;

    public DatabaseMigrator(TransactionManager transactions) {
        this.transactions = transactions;
    }

    public void migrate() throws EvermailException {
        try (var stream = getClass().getResourceAsStream("/db/schema.sql")) {
            if (stream == null) {
                throw new IllegalStateException("Missing schema");
            }
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            transactions.write(connection -> {
                try (var statement = connection.createStatement()) {
                    int version;
                    try (var result = statement.executeQuery("PRAGMA user_version")) {
                        result.next();
                        version = result.getInt(1);
                    }
                    if (version > VERSION) {
                        throw new DatabaseException(ErrorCode.DB_MIGRATION_FAILED, "Database was created by a newer application");
                    }
                    if (version == VERSION) {
                        return null;
                    }
                    boolean legacy = false;
                    try (var result = statement.executeQuery("PRAGMA table_info(account)")) {
                        while (result.next()) {
                            legacy |= result.getString("name").equals("id_profile");
                        }
                    }
                    if (legacy) {
                        // Preserve the entire previous database, including ciphertext and relationships.
                        for (String table : List.of("app_profile", "email_address", "account", "mail", "draft",
                                "attachment", "label", "mail_label", "mail_address", "draft_address")) {
                            try (var exists = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
                                exists.setString(1, table);
                                try (var result = exists.executeQuery()) {
                                    if (result.next()) {
                                        statement.execute("ALTER TABLE " + table + " RENAME TO legacy_" + table);
                                    }
                                }
                            }
                        }
                    }
                    for (String sql : schema.split(";")) {
                        if (!sql.isBlank()) {
                            statement.execute(sql);
                        }
                    }
                    statement.execute("PRAGMA user_version=" + VERSION);
                }
                return null;
            });
        } catch (EvermailException e) {
            throw e;
        } catch (Exception e) {
            throw new DatabaseException(ErrorCode.DB_MIGRATION_FAILED, "Database migration failed", e);
        }
    }
}
