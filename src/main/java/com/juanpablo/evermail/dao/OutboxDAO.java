package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.exception.ErrorCode;
import lombok.Value;
import lombok.ToString;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class OutboxDAO {
    @Value
    @ToString(onlyExplicitlyIncluded = true)
    public static class Row {
        UUID id;
        UUID accountId;
        String messageId;
        String subject;
        String bodyCipher;
        DeliveryState state;
        Instant createdAt;
        Instant updatedAt;
        ErrorCode error;
    }

    public Row find(Connection c, UUID accountId, UUID id) throws SQLException {
        return Sql.one(c, "SELECT * FROM outbox_message WHERE id_account=? AND id_outbox=?", this::map, accountId, id);
    }

    public void insert(Connection c, Row row) throws SQLException {
        Sql.update(c, "INSERT INTO outbox_message VALUES(?,?,?,?,?,?,?,?,?)", row.getId(), row.getAccountId(),
                row.getMessageId(), row.getSubject(), row.getBodyCipher(), row.getState(),
                row.getCreatedAt(), row.getUpdatedAt(), row.getError());
    }

    public boolean transition(Connection c, UUID accountId, UUID id, DeliveryState expected,
                              DeliveryState next, ErrorCode error) throws SQLException {
        return Sql.update(c, "UPDATE outbox_message SET state=?,last_error_code=?,updated_at=? WHERE id_account=? AND id_outbox=? AND state=?",
                next, error, Instant.now(), accountId, id, expected) == 1;
    }

    public List<Row> recoverable(Connection c, UUID accountId) throws SQLException {
        return Sql.list(c, "SELECT * FROM outbox_message WHERE id_account=? AND state IN ('SENDING','ACCEPTED')",
                this::map, accountId);
    }

    private Row map(ResultSet rs) throws SQLException {
        String error = rs.getString("last_error_code");
        return new Row(UUID.fromString(rs.getString("id_outbox")), UUID.fromString(rs.getString("id_account")),
                rs.getString("message_id"), rs.getString("subject"), rs.getString("body_cipher"),
                DeliveryState.valueOf(rs.getString("state")), Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("updated_at")), error == null ? null : ErrorCode.valueOf(error));
    }
}
