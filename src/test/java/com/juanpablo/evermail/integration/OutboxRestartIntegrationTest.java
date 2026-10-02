package com.juanpablo.evermail.integration;

import com.juanpablo.evermail.model.DeliveryState;
import com.juanpablo.evermail.presentation.*;
import com.juanpablo.evermail.support.BackendFixture.MemoryKeys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class OutboxRestartIntegrationTest {
    @TempDir Path directory;

    @Test void unknownDeliverySurvivesReopeningDatabaseAndIsNeverAutomaticallyResubmitted() throws Exception {
        Path database = directory.resolve("restart.db");
        var draft = new ComposeDraft("target@example.com", "", "", "Restart test", "Private message");
        UUID accountId, submissionId;
        MemoryKeys keys;
        ScriptedMailGateway gateway;
        try (var first = new IntegrationHarness(database)) {
            accountId = first.seed().getId();
            first.startReady();
            first.gateway.loseAcknowledgement = true;
            first.presenter.editDraft(draft);
            first.presenter.send();
            var uncertain = first.await(s -> s.compose().phase() == MailViewState.SendPhase.UNKNOWN);
            assertEquals(draft, uncertain.compose().draft());
            gateway = first.gateway;
            keys = first.keys;
            submissionId = gateway.submitted.getId();
            assertEquals(1, gateway.submissions.get());
            assertEquals(DeliveryState.UNKNOWN, first.context.getOutbox().find(accountId, submissionId).getState());
            assertEquals(1, first.count("outbox_message"));
        }

        // A fresh object graph and connection, retaining only the persisted database and test key store.
        try (var restarted = new IntegrationHarness(database, keys, gateway)) {
            var ready = restarted.startReady();
            assertEquals(accountId, ready.session().account().getId());
            assertEquals(1, gateway.submissions.get(), "Startup recovery must not resend uncertain deliveries");
            assertEquals(DeliveryState.UNKNOWN, restarted.context.getOutbox().find(accountId, submissionId).getState());
            var retry = restarted.backend.send(submissionId, draft).result().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(retry.succeeded());
            assertEquals(DeliveryState.UNKNOWN, retry.value().getState());
            assertEquals(1, gateway.submissions.get(), "The same submission identity must not reach SMTP twice");
            assertEquals(1, restarted.count("outbox_message"));
            long sent = restarted.context.getTransactions().read(c -> com.juanpablo.evermail.dao.Sql.one(c,
                    "SELECT COUNT(*) FROM mail WHERE direction = 'SENT'", rs -> rs.getLong(1)));
            assertEquals(0, sent, "An uncertain delivery must not appear as confirmed sent mail");
        }
    }
}
