package com.juanpablo.evermail.config;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StorageBudgetTest {
    @TempDir Path directory;

    @Test void countsApplicationDataSidecarsAndOperationReserveWithoutDoubleCounting() throws Exception {
        Path app = Files.createDirectories(directory.resolve("app"));
        Path data = Files.createDirectories(directory.resolve("data"));
        Files.write(app.resolve("runtime.bin"), new byte[8192]);
        Path database = Files.write(data.resolve("mail.db"), new byte[4096]);
        Files.write(data.resolve("mail.db-wal"), new byte[4096]);
        Files.write(data.resolve("image.bin"), new byte[4096]);
        var budget = new StorageBudget(database, List.of(app, app.resolve("runtime.bin"), data),
                100_000, 10_000, ignored -> 1_000_000);
        assertEquals((100_000 - 16_384 - 10_000) / 3, budget.databaseLimit());
    }

    @Test void freeDiskSpaceCanBeMoreRestrictiveThanTheTwoGigabyteBudget() throws Exception {
        Path database = Files.write(directory.resolve("mail.db"), new byte[4096]);
        var budget = new StorageBudget(database, List.of(), StorageBudget.TOTAL_BYTES, 4096, ignored -> 30_000);
        assertEquals(10_000, budget.databaseLimit());
    }

    @Test void oversizedInstallationAndUnmeasurableStorageFailClosed() throws Exception {
        Path app = Files.write(directory.resolve("app.exe"), new byte[50_000]);
        var budget = new StorageBudget(directory.resolve("mail.db"), List.of(app),
                40_000, 4096, ignored -> 1_000_000);
        assertEquals(ErrorCode.STORAGE_LIMIT_REACHED,
                assertThrows(DatabaseException.class, budget::databaseLimit).getErrorCode());
        var unavailable = new StorageBudget(directory.resolve("mail.db"), List.of(),
                100_000, 4096, ignored -> { throw new java.io.IOException("offline disk"); });
        assertEquals(ErrorCode.STORAGE_LIMIT_REACHED,
                assertThrows(DatabaseException.class, unavailable::databaseLimit).getErrorCode());
    }
}
