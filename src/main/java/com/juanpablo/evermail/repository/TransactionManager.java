package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.exception.*;
import java.sql.Connection;
import java.sql.SQLException;

public class TransactionManager implements AutoCloseable {
    private final SqliteConnectionProvider provider;

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
                throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Local transaction failed", e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Cannot complete local transaction", e);
        }
    }

    @Override
    public synchronized void close() throws DatabaseException {
        provider.close();
    }
}
