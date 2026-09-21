package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.model.*;
import lombok.Value;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class MailDAO {
    @Value
    public static class Row {
        MailHeader header;
        @lombok.ToString.Exclude
        String bodyCipher;
    }

    public Row find(Connection c, UUID accountId, UUID id) throws SQLException {
        return Sql.one(c, "SELECT * FROM mail WHERE id_account=? AND id_mail=?", this::map, accountId, id);
    }

    public Row findRemote(Connection c, UUID accountId, RemoteMailId remote) throws SQLException {
        return Sql.one(c, "SELECT * FROM mail WHERE id_account=? AND direction='INBOX' AND uid_validity=? AND remote_uid=?",
                this::map, accountId, remote.getUidValidity(), remote.getUid());
    }

    public List<Row> page(Connection c, UUID accountId, long validity, long before, long lower, int size) throws SQLException {
        return Sql.list(c, """
                SELECT * FROM mail WHERE id_account=? AND direction='INBOX' AND uid_validity=?
                AND remote_uid<? AND remote_uid>=? ORDER BY remote_uid DESC LIMIT ?
                """, this::map, accountId, validity, before, lower, size);
    }

    public void insert(Connection c, Row row) throws SQLException {
        MailHeader h = row.getHeader();
        Sql.update(c, "INSERT INTO mail VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", h.getId(), h.getAccountId(), h.getDirection(),
                h.getRemoteId() == null ? null : h.getRemoteId().getUid(),
                h.getRemoteId() == null ? null : h.getRemoteId().getUidValidity(),
                h.getMessageId(), h.getOutboundId(), h.getSenderEmail(), h.getSenderName(),
                h.getSubject(), h.getOccurredAt(), row.getBodyCipher(), h.isRead() ? 1 : 0);
    }

    private Row map(ResultSet rs) throws SQLException {
        MailDirection direction = MailDirection.valueOf(rs.getString("direction"));
        String outbound = rs.getString("outbound_id");
        String body = rs.getString("body_cipher");
        return new Row(new MailHeader(UUID.fromString(rs.getString("id_mail")), UUID.fromString(rs.getString("id_account")),
                direction, direction == MailDirection.INBOX ? new RemoteMailId(rs.getLong("uid_validity"), rs.getLong("remote_uid")) : null,
                rs.getString("message_id"), outbound == null ? null : UUID.fromString(outbound),
                rs.getString("sender_email"), rs.getString("sender_name"), rs.getString("subject"),
                Instant.ofEpochMilli(rs.getLong("occurred_at")), rs.getInt("is_read") == 1, body != null), body);
    }
}
