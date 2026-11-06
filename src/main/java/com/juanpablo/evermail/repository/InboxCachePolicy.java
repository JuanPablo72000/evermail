package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Only inbox bodies are disposable. Headers, accounts, sent mail and outbox are never victims. */
final class InboxCachePolicy {
    private final Map<UUID, Long> access = new HashMap<>();
    private final Map<UUID, Integer> downloads = new HashMap<>();
    private long sequence;
    private final java.util.concurrent.atomic.AtomicReference<UUID> selected = new java.util.concurrent.atomic.AtomicReference<>();

    void select(UUID id) { selected.set(id); }
    void release(UUID id) { selected.updateAndGet(current -> Objects.equals(current, id) ? null : current); }
    void touch(UUID id) { access.put(id, ++sequence); }
    void reserve(UUID id) { downloads.merge(id, 1, Integer::sum); }
    void releaseReservation(UUID id) { downloads.computeIfPresent(id, (key, count) -> count == 1 ? null : count - 1); }

    UUID victim(Connection connection, UUID downloading) throws SQLException {
        if (Sql.one(connection, "SELECT name FROM sqlite_master WHERE type='table' AND name='mail'",
                row -> row.getString(1)) == null) return null;
        record Candidate(UUID id, boolean recent) { }
        List<Candidate> candidates = Sql.list(connection, """
                SELECT m.id_mail, m.id_mail IN (
                    SELECT recent.id_mail FROM mail recent
                    WHERE recent.id_account=m.id_account AND recent.direction='INBOX'
                    ORDER BY recent.remote_uid DESC LIMIT 50) AS recent
                FROM mail m
                WHERE m.direction='INBOX' AND m.body_cipher IS NOT NULL
                ORDER BY m.occurred_at,m.remote_uid
                """, row -> new Candidate(UUID.fromString(row.getString(1)), row.getBoolean(2)));
        return candidates.stream()
                .filter(item -> !item.id().equals(selected.get()) && !item.id().equals(downloading))
                .filter(item -> !downloads.containsKey(item.id()))
                .min(Comparator.comparing(Candidate::recent)
                        .thenComparingLong(item -> item.recent() ? 0 : access.getOrDefault(item.id(), 0L)))
                .map(Candidate::id).orElse(null);
    }

    void forgotten(UUID id) { access.remove(id); }
}
