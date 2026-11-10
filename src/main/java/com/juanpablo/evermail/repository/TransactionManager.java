package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.exception.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import com.juanpablo.evermail.config.StorageBudget;
import com.juanpablo.evermail.dao.Sql;

public class TransactionManager implements AutoCloseable {
    private static final long WRITE_HEADROOM = 64 * 1024;
    private final SqliteConnectionProvider provider;
    private final InboxCachePolicy cache = new InboxCachePolicy();
    private long reservedBytes;

    public TransactionManager(SqliteConnectionProvider provider) {
        this.provider = provider;
    }

    @FunctionalInterface
    public interface DbWork<T> {
        T execute(Connection connection) throws Exception;
    }

    public synchronized <T> T read(DbWork<T> work) throws EvermailException {
        try {
            return work.execute(provider.connection());
        } catch (EvermailException e) {
            throw e;
        } catch (Exception e) {
            throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Local query failed", e);
        }
    }

    public synchronized <T> T write(DbWork<T> work) throws EvermailException {
        try {
            reclaimSpace(null, WRITE_HEADROOM, false);
        } catch (Exception error) {
            throw databaseError(error);
        }
        return executeTransaction(work);
    }

    /** Known large writes reclaim their entire estimated growth before beginning the transaction. */
    public synchronized <T> T write(long anticipatedBytes, DbWork<T> work) throws EvermailException {
        makeRoom(null, anticipatedBytes);
        return executeTransaction(work);
    }

    private <T> T executeTransaction(DbWork<T> work) throws EvermailException {
        Connection connection = provider.connection();
        try {
            if (!connection.getAutoCommit()) {
                throw new SQLException("Nested transactions are not supported");
            }
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                if (e instanceof EvermailException domain) {
                    throw domain;
                }
                throw databaseError(e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    /** A reservation survives network I/O and prevents other writes from spending its allowance. */
    public synchronized CacheReservation reserveCache(UUID mailId, long bytes) throws EvermailException {
        makeRoom(mailId, bytes);
        reservedBytes += bytes;
        cache.reserve(mailId);
        return new CacheReservation(mailId, bytes);
    }

    public final class CacheReservation implements AutoCloseable {
        private final UUID mailId;
        private final long bytes;
        private boolean closed;

        private CacheReservation(UUID mailId, long bytes) { this.mailId = mailId; this.bytes = bytes; }

        public <T> T save(long actualBytes, DbWork<T> work) throws EvermailException {
            synchronized (TransactionManager.this) {
                if (closed) throw new IllegalStateException("Reservation already released");
                close();
                makeRoom(mailId, actualBytes);
                // Keep the replacement's old body protected until its transaction commits.
                T result = executeTransaction(work);
                cache.touch(mailId);
                return result;
            }
        }

        public <T> T saveAvatar(long actualBytes, DbWork<T> work) throws EvermailException {
            synchronized (TransactionManager.this) {
                if (closed) throw new IllegalStateException("Reservation already released");
                close();
                // Recheck disk changes without ever reclaiming a mail body for an optional photo.
                try (var replacement = reserveAvatar(mailId, actualBytes)) {
                    return executeTransaction(work);
                }
            }
        }

        @Override public void close() {
            synchronized (TransactionManager.this) {
                if (!closed) {
                    reservedBytes -= bytes;
                    cache.releaseReservation(mailId);
                    closed = true;
                }
            }
        }
    }

    /** Optional photos may displace photos, but never evict a readable message to make room. */
    public synchronized CacheReservation reserveAvatar(UUID senderId, long bytes) throws EvermailException {
        try {
            Connection connection = provider.connection();
            long maximum = provider.storage().targetBytes(reservedBytes);
            while (databaseBytes(connection) + bytes > maximum) {
                if (!discardAvatar(connection)) throw StorageBudget.exhausted();
                maximum = provider.storage().targetBytes(reservedBytes);
            }
            provider.storage().enforceMaximum(connection, maximum);
            reservedBytes += bytes;
            cache.reserve(senderId);
            return new CacheReservation(senderId, bytes);
        } catch (Exception error) { throw databaseError(error); }
    }

    private boolean discardAvatar(Connection connection) throws Exception {
        if (Sql.one(connection, "SELECT name FROM sqlite_master WHERE type='table' AND name='avatar_cache'",
                row -> row.getString(1)) == null) return false;
        Long rowId = Sql.one(connection, "SELECT rowid FROM avatar_cache ORDER BY updated_at LIMIT 1", row -> row.getLong(1));
        if (rowId == null) return false;
        provider.storage().enforceCleanup(connection);
        executeTransaction(c -> Sql.update(c, "DELETE FROM avatar_cache WHERE rowid=?", rowId));
        return true;
    }

    private void makeRoom(UUID mailId, long bytes) throws EvermailException {
        reclaimSpace(mailId, bytes, true);
    }

    private void reclaimSpace(UUID mailId, long bytes, boolean required) throws EvermailException {
        if (bytes < 0) throw new IllegalArgumentException("Negative reservation");
        try {
            Connection connection = provider.connection();
            long maximum = provider.storage().targetBytes(reservedBytes);
            while (databaseBytes(connection) + bytes > maximum) {
                if (discardAvatar(connection)) {
                    maximum = provider.storage().targetBytes(reservedBytes);
                    continue;
                }
                UUID victim = cache.victim(connection, mailId);
                if (victim == null) {
                    if (required || databaseBytes(connection) > maximum) throw StorageBudget.exhausted();
                    break;
                }
                provider.storage().enforceCleanup(connection);
                discardCachedBody(victim);
                maximum = provider.storage().targetBytes(reservedBytes);
            }
            provider.storage().enforceMaximum(connection, maximum);
        } catch (Exception error) {
            throw databaseError(error);
        }
    }

    private long databaseBytes(Connection connection) throws SQLException {
        return SqliteStorage.pragma(connection, "page_count") * SqliteStorage.pragma(connection, "page_size");
    }

    private void discardCachedBody(UUID mailId) throws EvermailException {
        executeTransaction(connection -> {
            Sql.update(connection, "UPDATE mail SET body_cipher=NULL,body_format=0 WHERE id_mail=? AND direction='INBOX'", mailId);
            return null;
        });
        cache.forgotten(mailId);
    }

    public synchronized void touchBody(UUID mailId) { cache.touch(mailId); }
    public void selectBody(UUID mailId) { cache.select(mailId); }
    public void releaseBody(UUID mailId) { cache.release(mailId); }

    /** Quantity bound complements the byte budget. Recent mail is preferred; the reader is protected. */
    public synchronized void trimInboxBodies() throws EvermailException {
        try {
            Connection connection = provider.connection();
            while (Sql.one(connection, "SELECT COUNT(*) FROM mail WHERE direction='INBOX' AND body_cipher IS NOT NULL",
                    row -> row.getLong(1)) > 100) {
                UUID victim = cache.victim(connection, null);
                if (victim == null) return;
                discardCachedBody(victim);
            }
        } catch (Exception error) {
            throw databaseError(error);
        }
    }

    private static EvermailException databaseError(Exception error) {
        if (error instanceof EvermailException domain) return domain;
        // SQLITE_FULL includes both the page ceiling and a full device. Never expose raw SQL text.
        if (error instanceof SQLException sql && (sql.getErrorCode() & 0xff) == 13) {
            return new DatabaseException(ErrorCode.STORAGE_LIMIT_REACHED, "SQLite storage limit reached", error);
        }
        return new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Local transaction failed", error);
    }

    @Override
    public synchronized void close() throws DatabaseException {
        provider.close();
    }
}
