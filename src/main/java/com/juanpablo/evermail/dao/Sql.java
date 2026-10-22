package com.juanpablo.evermail.dao;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class Sql {
    private Sql() {
    }

    @FunctionalInterface
    public interface Mapper<T> {
        T map(ResultSet result) throws SQLException;
    }

    public static int update(Connection connection, String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, arguments)) {
            return statement.executeUpdate();
        }
    }

    public static <T> List<T> list(Connection connection, String sql, Mapper<T> mapper, Object... arguments) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, arguments);
             ResultSet results = statement.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (results.next()) {
                rows.add(mapper.map(results));
            }
            return rows;
        }
    }

    public static <T> T one(Connection connection, String sql, Mapper<T> mapper, Object... arguments) throws SQLException {
        List<T> rows = list(connection, sql, mapper, arguments);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... arguments) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < arguments.length; i++) {
                Object value = arguments[i];
                if (value instanceof UUID || value instanceof Enum<?>) {
                    value = value.toString();
                } else if (value instanceof Instant instant) {
                    value = instant.toEpochMilli();
                }
                statement.setObject(i + 1, value);
            }
            return statement;
        } catch (SQLException e) {
            statement.close();
            throw e;
        }
    }
}
