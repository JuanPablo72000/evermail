package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.config.StorageBudget;
import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.support.BackendFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageLimitTest extends BackendFixture {
    private static final long LIMIT = 4 * 1024 * 1024;
    private Path database;
    private StorageBudget storage;
    private final java.util.concurrent.atomic.AtomicInteger measurements = new java.util.concurrent.atomic.AtomicInteger();

    @BeforeEach void useSmallRealDiskBudget() throws Exception {
        transactions.close();
        database = directory.resolve("test.db");
        Path app = Files.write(directory.resolve("application.bin"), new byte[128 * 1024]);
        storage = new StorageBudget(database, List.of(app), LIMIT, 64 * 1024, ignored -> {
            measurements.incrementAndGet();
            return 100 * LIMIT;
        });
        transactions = new TransactionManager(new SqliteConnectionProvider(storage));
        accounts = new AccountRepository(transactions, security, keys);
        mails = new MailRepository(transactions, accounts, security, keys);
        outbox = new OutboxRepository(transactions, accounts, keys, security);
    }

    private List<MailHeader> headers(int count) throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, count, false));
        List<MailHeader> result = new ArrayList<>();
        InboxCursor cursor = null;
        do {
            InboxPage page = mails.readInbox(account.getId(), cursor, 50);
            result.addAll(page.getItems());
            cursor = page.getNextCursor();
        } while (cursor != null);
        return result;
    }

    private void save(MailHeader header) throws Exception {
        mails.saveContent(account.getId(), header.getId(), new RemoteMailContent("x".repeat(120_000), List.of()));
    }

    @Test void markingReadMeasuresDiskOnceAndRechecksBeforeTheNextWrite() throws Exception {
        MailHeader header = headers(1).getFirst();
        measurements.set(0);
        mails.markRead(account.getId(), header.getId());
        assertEquals(1, measurements.get());
        mails.markRead(account.getId(), header.getId());
        assertEquals(2, measurements.get());
        assertTrue(mails.findHeader(account.getId(), header.getId()).isRead());
    }

    @Test void pressureAutomaticallyEvictsBodiesIncludingRecentOnesButKeepsOpenMailAndHeaders() throws Exception {
        List<MailHeader> headers = headers(12);
        MailHeader open = headers.getLast();
        save(open);
        mails.protectBody(open.getId());
        for (MailHeader header : headers.reversed()) {
            save(header);
            assertTrue(Files.size(database) <= storage.databaseLimit());
        }
        assertNotNull(mails.readContent(account.getId(), open.getId()));
        assertNotNull(mails.readContent(account.getId(), headers.getFirst().getId()));
        assertEquals(12, count("mail"));
        assertTrue(transactions.read(c -> Sql.one(c,
                "SELECT COUNT(*) FROM mail WHERE body_cipher IS NULL", row -> row.getInt(1))) > 0);
        assertTrue(physicalSize() < LIMIT);
    }

    @Test void evictionPrefersAdditionalPagesAndPreservesSentMailPendingSendsAndKeys() throws Exception {
        List<MailHeader> headers = headers(60);
        OutboxMessage pending = outbox.prepare(request(UUID.randomUUID()));
        OutboxMessage accepted = outbox.prepare(request(UUID.randomUUID()));
        assertTrue(outbox.claim(account.getId(), accepted.getId()));
        outbox.transition(account.getId(), accepted.getId(), DeliveryState.ACCEPTED, null);
        SendResult sent = outbox.commitSent(account.getId(), accepted.getId());
        MailHeader old = headers.getLast();
        save(old);
        for (MailHeader recent : headers.subList(0, 8)) save(recent);
        assertNull(mails.readContent(account.getId(), old.getId()));
        assertNotNull(mails.findHeader(account.getId(), old.getId()));
        assertNotNull(mails.readContent(account.getId(), sent.getSentMailId()));
        assertEquals(DeliveryState.PENDING, outbox.find(account.getId(), pending.getId()).getState());
        assertNotNull(keys.read(account.getKeyRef()));
    }

    @Test void reservationIsReleasedOnCancellationAndOversizedWritesRollBack() throws Exception {
        MailHeader header = headers(1).getFirst();
        long reservationBytes = storage.databaseLimit() - Files.size(database) - 8192;
        try (var reservation = transactions.reserveCache(header.getId(), reservationBytes)) {
            assertEquals(ErrorCode.STORAGE_LIMIT_REACHED, assertThrows(EvermailException.class,
                    () -> transactions.reserveCache(UUID.randomUUID(), 64 * 1024)).getErrorCode());
        }
        save(header);
        String before = mails.readContent(account.getId(), header.getId()).getPlainText();
        assertEquals(ErrorCode.STORAGE_LIMIT_REACHED, assertThrows(EvermailException.class,
                () -> transactions.write(c -> {
                    Sql.update(c, "UPDATE account SET display_name='should roll back'");
                    Sql.update(c, "UPDATE mail SET body_cipher=hex(zeroblob(2000000))");
                    return null;
                })).getErrorCode());
        assertEquals("Person", accounts.find(account.getId()).getDisplayName());
        assertEquals(before, mails.readContent(account.getId(), header.getId()).getPlainText());
        assertTrue(physicalSize() < LIMIT);
    }

    @Test void clearingCachePhysicallyShrinksDatabaseAndReopenRetainsTheLimit() throws Exception {
        for (MailHeader header : headers(5)) save(header);
        long before = Files.size(database);
        mails.clearInboxCache();
        assertTrue(Files.size(database) < before);
        assertEquals(1, count("account"));
        transactions.close();
        transactions = new TransactionManager(new SqliteConnectionProvider(storage));
        assertEquals(1L, (long) transactions.read(c -> SqliteStorage.pragma(c, "auto_vacuum")));
        assertTrue(transactions.read(c -> SqliteStorage.pragma(c, "max_page_count")) * 4096 <= storage.databaseLimit());
    }

    @Test void secondWriterCannotOpenTheSameDatabase() {
        assertThrows(DatabaseException.class, () -> new SqliteConnectionProvider(storage));
    }

    @Test void optionalPhotoCannotEvictMailAndItsReservationIsReleased() throws Exception {
        MailHeader header = headers(1).getFirst(); save(header);
        long tooLarge = storage.databaseLimit();
        assertEquals(ErrorCode.STORAGE_LIMIT_REACHED, assertThrows(EvermailException.class,
                () -> transactions.reserveAvatar(UUID.randomUUID(), tooLarge)).getErrorCode());
        assertNotNull(mails.readContent(account.getId(), header.getId()));
        try (var reservation = transactions.reserveAvatar(UUID.randomUUID(), 96 * 1024)) { }
        save(header);
        assertTrue(physicalSize() < LIMIT);
    }

    @Test void mailWritesReclaimPhotosBeforeMessageBodies() throws Exception {
        MailHeader header = headers(1).getFirst(); save(header);
        var photos = new AvatarRepository(transactions, accounts, security, keys);
        var photo = new AvatarPhoto(new byte[65_536], AvatarPhoto.Status.AVAILABLE);
        for (int i = 0; i < 3; i++) {
            String email = "sender" + i + "@example.com";
            try (var reservation = photos.reserve(email)) {
                photos.save(account.getId(), email, photo, java.time.Instant.now(), reservation);
            }
        }
        long nearLimit = storage.databaseLimit() - Files.size(database) + 8192;
        try (var reservation = transactions.reserveCache(header.getId(), nearLimit)) {
            assertTrue(count("avatar_cache") < 3);
            assertNotNull(mails.readContent(account.getId(), header.getId()));
        }
        assertTrue(physicalSize() < LIMIT);
    }

    @Test void largerApplicationTriggersCleanupBeforeNewWrites() throws Exception {
        List<MailHeader> headers = headers(6);
        for (MailHeader header : headers) save(header);
        long before = Files.size(database);
        Files.write(directory.resolve("application.bin"), new byte[1700 * 1024]);
        mails.markRead(account.getId(), headers.getFirst().getId());
        assertTrue(Files.size(database) < before);
        assertTrue(Files.size(database) <= storage.databaseLimit());
        assertEquals(6, count("mail"));
    }

    // This temporary schema is created at runtime and is not available to IntelliJ's SQL resolver.
    @SuppressWarnings("SqlResolve")
    @Test void convertsOldDatabaseToPhysicalReclamationWithoutLosingContent() throws Exception {
        Path old = directory.resolve("old.db");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + old);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA auto_vacuum=NONE");
            statement.execute("CREATE TABLE preserved(value TEXT)");
            statement.execute("INSERT INTO preserved VALUES('retained')");
        }
        try (var converted = new TransactionManager(new SqliteConnectionProvider(old))) {
            assertEquals(1L, (long) converted.read(c -> SqliteStorage.pragma(c, "auto_vacuum")));
            assertEquals("retained", converted.read(c -> Sql.one(c, "SELECT value FROM preserved", row -> row.getString(1))));
        }
    }

    private long physicalSize() throws Exception {
        try (var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> {
                try { return Files.size(file); } catch (Exception error) { throw new RuntimeException(error); }
            }).sum();
        }
    }
}
