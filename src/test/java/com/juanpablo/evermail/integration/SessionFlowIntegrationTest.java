package com.juanpablo.evermail.integration;

import com.juanpablo.evermail.navigation.NavigationRules.Route;
import com.juanpablo.evermail.presentation.MailViewState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class SessionFlowIntegrationTest {
    @TempDir Path directory;

    @Test void closingWindowClearsInboxButKeepsAccountAndKey() throws Exception {
        Path database = directory.resolve("close.db");
        com.juanpablo.evermail.model.Account account;
        com.juanpablo.evermail.support.BackendFixture.MemoryKeys keys;
        try (var app = new IntegrationHarness(database)) {
            account = app.seed(); keys = app.keys;
            var ready = app.startReady();
            app.presenter.openMail(ready.inbox().items().getFirst().getId());
            app.await(s -> s.reader().phase() == MailViewState.ReaderPhase.READY);
        }
        assertTrue(keys.values.containsKey(account.getKeyRef()));
        try (var db = new com.juanpablo.evermail.repository.TransactionManager(
                new com.juanpablo.evermail.repository.SqliteConnectionProvider(database))) {
            assertEquals(0L, db.read(c -> com.juanpablo.evermail.dao.Sql.one(c,
                    "SELECT COUNT(*) FROM mail WHERE direction='INBOX'", r -> r.getLong(1))).longValue());
            assertEquals(1L, db.read(c -> com.juanpablo.evermail.dao.Sql.one(c,
                    "SELECT COUNT(*) FROM account", r -> r.getLong(1))).longValue());
        }
    }

    @Test void logoutClearsPresentationAndDiscardsLateBodyBeforeDeletingLocalAccount() throws Exception {
        try (var app = new IntegrationHarness(directory.resolve("logout.db"))) {
            var account = app.seed();
            var gate = new ScriptedMailGateway.Gate();
            app.gateway.contentGate = gate;
            var ready = app.startReady();
            app.presenter.openMail(ready.inbox().items().getFirst().getId());
            app.await(s -> s.reader().phase() == MailViewState.ReaderPhase.HEADER_LOADING);
            assertTrue(gate.entered.await(5, TimeUnit.SECONDS));
            app.presenter.requestLogout();
            app.presenter.confirmLogout();
            int afterLogout = app.notifications.size();
            assertTrue(app.presenter.state().inbox().items().isEmpty());
            assertNull(app.presenter.state().reader().content());
            assertNull(app.presenter.state().session().account());

            gate.release.countDown();
            app.await(s -> s.route() == Route.LOGIN && !s.sessionBusy());
            app.drainCallbacks();
            assertNull(app.presenter.state().sessionError());
            for (var state : app.notifications.subList(afterLogout, app.notifications.size())) {
                assertTrue(state.inbox().items().isEmpty());
                assertNull(state.reader().content());
                assertNull(state.reader().header());
            }
            assertFalse(app.keys.values.containsKey(account.getKeyRef()));
            for (String table : new String[]{"account", "mail", "mail_recipient", "inbox_state", "outbox_message", "outbox_recipient"})
                assertEquals(0, app.count(table), table + " must be empty after logout");
            assertEquals(1, app.gateway.bodyRequests.get());
            assertEquals(0, app.gateway.activeSessions.get());
        }
    }
}
