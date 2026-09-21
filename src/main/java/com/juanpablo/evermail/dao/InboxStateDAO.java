package com.juanpablo.evermail.dao;

import lombok.Value;
import java.sql.*;
import java.util.UUID;

public class InboxStateDAO {
    @Value
    public static class Row {
        long validity;
        long lower;
        long upper;
        boolean hasMore;
        long syncedAt;
    }

    public Row find(Connection c, UUID id) throws SQLException {
        return Sql.one(c, "SELECT * FROM inbox_state WHERE id_account=?", rs -> new Row(rs.getLong("uid_validity"),
                rs.getLong("oldest_fetched_uid"), rs.getLong("newest_fetched_uid"),
                rs.getInt("has_more") == 1, rs.getLong("last_synced_at")), id);
    }

    public void save(Connection c, UUID id, Row row) throws SQLException {
        Sql.update(c, """
                INSERT INTO inbox_state VALUES(?,?,?,?,?,?) ON CONFLICT(id_account) DO UPDATE SET
                uid_validity=excluded.uid_validity,oldest_fetched_uid=excluded.oldest_fetched_uid,
                newest_fetched_uid=excluded.newest_fetched_uid,has_more=excluded.has_more,last_synced_at=excluded.last_synced_at
                """, id, row.getValidity(), row.getLower(), row.getUpper(), row.isHasMore() ? 1 : 0, row.getSyncedAt());
    }
}
