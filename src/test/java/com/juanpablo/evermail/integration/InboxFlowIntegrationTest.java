package com.juanpablo.evermail.integration;

import com.juanpablo.evermail.application.SessionSnapshot;
import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.presentation.MailViewState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class InboxFlowIntegrationTest {
    @TempDir Path directory;

    @Test void startupShowsCacheBeforeRefreshAndMergesOverlappingUids() throws Exception {
        try (var app = new IntegrationHarness(directory.resolve("inbox.db"))) {
            Account account = app.seed();
            var gate = new ScriptedMailGateway.Gate();
            app.gateway.latestGate = gate;
            app.presenter.start();
            var cached = app.await(s -> s.inbox().items().size() == 50 && s.inbox().loading());
            assertTrue(gate.entered.await(5, TimeUnit.SECONDS));
            assertEquals(SessionSnapshot.Phase.READY, cached.session().phase());
            assertEquals(account.getId(), cached.session().account().getId());
            assertEquals(50, cached.inbox().items().getFirst().getRemoteId().getUid());
            Map<Long, UUID> original = cached.inbox().items().stream().collect(Collectors.toMap(
                    m -> m.getRemoteId().getUid(), MailHeader::getId));
            assertEquals(50, app.count("mail"));

            gate.release.countDown();
            var refreshed = app.await(s -> !s.inbox().loading());
            assertNull(refreshed.inbox().error());
            assertFalse(refreshed.inbox().stale());
            assertEquals(50, refreshed.inbox().items().size());
            assertEquals(60, refreshed.inbox().items().getFirst().getRemoteId().getUid());
            assertEquals(11, refreshed.inbox().items().getLast().getRemoteId().getUid());
            assertEquals(50, refreshed.inbox().items().stream().map(MailHeader::getId).distinct().count());
            refreshed.inbox().items().stream().filter(m -> original.containsKey(m.getRemoteId().getUid()))
                    .forEach(m -> assertEquals(original.get(m.getRemoteId().getUid()), m.getId()));
            assertEquals(60, app.count("mail"));
        }
    }

    @Test void offlineRefreshKeepsCacheReadableAndRecoversWithoutChangingSession() throws Exception {
        try (var app = new IntegrationHarness(directory.resolve("offline.db"))) {
            Account account = app.seed();
            MailHeader mail = app.context.getMails().readInbox(account.getId(), null, 50).getItems().getFirst();
            String body = "Private cached message for offline reading";
            app.context.getMails().saveContent(account.getId(), mail.getId(), new RemoteMailContent(body, List.of()));
            String cipher = app.context.getTransactions().read(c -> Sql.one(c,
                    "SELECT body_cipher FROM mail WHERE id_mail = ?", rs -> rs.getString(1), mail.getId().toString()));
            assertNotNull(cipher);
            assertFalse(cipher.contains(body));
            app.gateway.offline = true;
            var offline = app.startReady();
            long sessionVersion = offline.session().version();
            assertNotNull(offline.inbox().error());
            assertTrue(offline.inbox().stale());
            assertEquals(50, offline.inbox().items().size());

            app.presenter.openMail(mail.getId());
            var reader = app.await(s -> s.reader().phase() == MailViewState.ReaderPhase.READY);
            assertEquals(body, reader.reader().content().getContent().getPlainText());
            assertEquals(0, app.gateway.bodyRequests.get());
            assertTrue(app.context.getMails().findHeader(account.getId(), mail.getId()).isRead());

            app.gateway.offline = false;
            app.presenter.backToInbox();
            app.presenter.refreshInbox();
            var recovered = app.await(s -> !s.inbox().loading());
            assertNull(recovered.inbox().error());
            assertFalse(recovered.inbox().stale());
            assertEquals(sessionVersion, recovered.session().version());
            assertEquals(account.getId(), recovered.session().account().getId());
            assertEquals(60, recovered.inbox().items().getFirst().getRemoteId().getUid());
            assertEquals(60, app.count("mail"));
            assertEquals(mail.getId(), recovered.inbox().items().stream()
                    .filter(m -> m.getRemoteId().equals(mail.getRemoteId())).findFirst().orElseThrow().getId());
        }
    }
}
