package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MigrationTest {
    @TempDir
    Path directory;

    @Test
    void versionZeroRowsArePreservedAndMigrationCanRunTwice() throws Exception {
        try (TransactionManager transactions = new TransactionManager(new SqliteConnectionProvider(directory.resolve("legacy.db")))) {
            transactions.write(c -> {
                Sql.update(c, "CREATE TABLE account(id_account INTEGER PRIMARY KEY,id_profile INTEGER,access_token TEXT)");
                Sql.update(c, "CREATE TABLE mail(id_mail INTEGER PRIMARY KEY,id_account INTEGER REFERENCES account(id_account),body_plain_text TEXT)");
                Sql.update(c, "INSERT INTO account VALUES(1,1,'encrypted-token')");
                Sql.update(c, "INSERT INTO mail VALUES(1,1,'encrypted-body')");
                return null;
            });
            DatabaseMigrator migrator = new DatabaseMigrator(transactions);
            migrator.migrate();
            migrator.migrate();
            assertEquals("encrypted-token", transactions.read(c -> Sql.one(c, "SELECT access_token FROM legacy_account", rs -> rs.getString(1))));
            assertEquals("encrypted-body", transactions.read(c -> Sql.one(c, "SELECT body_plain_text FROM legacy_mail", rs -> rs.getString(1))));
            assertEquals(0, (long) transactions.read(c -> Sql.one(c, "SELECT COUNT(*) FROM account", rs -> rs.getLong(1))));
        }
    }

    @Test
    void newerDatabaseVersionIsRefusedWithoutDowngrading() throws Exception {
        try (TransactionManager transactions = new TransactionManager(new SqliteConnectionProvider(directory.resolve("future.db")))) {
            transactions.write(c -> { Sql.update(c, "PRAGMA user_version=999"); return null; });
            DatabaseException error = assertThrows(DatabaseException.class, () -> new DatabaseMigrator(transactions).migrate());
            assertEquals(ErrorCode.DB_MIGRATION_FAILED, error.getErrorCode());
            assertEquals(999, (int) transactions.read(c -> Sql.one(c, "PRAGMA user_version", rs -> rs.getInt(1))));
        }
    }
}
