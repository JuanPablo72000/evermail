package com.juanpablo.evermail.integration;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.presentation.*;
import com.juanpablo.evermail.repository.DatabaseMigrator;
import com.juanpablo.evermail.service.*;
import com.juanpablo.evermail.support.BackendFixture.MemoryKeys;
import com.juanpablo.evermail.util.SecurityUtil;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Each instance owns its worker and SQLite connection; callbacks run on the test thread. */
final class IntegrationHarness implements AutoCloseable {
    final BackendContext context;
    final MemoryKeys keys;
    final ScriptedMailGateway gateway;
    final CoordinatedPresentationBackend backend;
    final MailPresenter presenter;
    final List<MailViewState> notifications = new ArrayList<>();
    private final BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();

    IntegrationHarness(Path database) throws Exception {
        this(database, new MemoryKeys(new SecurityUtil()), new ScriptedMailGateway());
    }

    IntegrationHarness(Path database, MemoryKeys keys, ScriptedMailGateway gateway) throws Exception {
        this.keys = keys;
        this.gateway = gateway;
        OAuthGateway oauth = new OAuthGateway() {
            public AuthorizationResult authorize(ProviderConfig config, CancellationToken cancel) {
                throw new IllegalStateException("Unexpected OAuth authorization");
            }
            public OAuthCredentials refresh(ProviderConfig config, OAuthCredentials previous, Deadline deadline) {
                throw new IllegalStateException("Unexpected OAuth refresh");
            }
        };
        context = new BackendContext(database, keys, oauth, new EnvConfig(name -> "configured"),
                (auth, accounts) -> gateway);
        ApplicationCoordinator coordinator = new ApplicationCoordinator(() -> BackendAccess.from(context), 8);
        backend = new CoordinatedPresentationBackend(coordinator);
        presenter = new MailPresenter(backend, callbacks::add, notifications::add);
    }

    Account seed() throws Exception {
        new DatabaseMigrator(context.getTransactions()).migrate();
        Account account = context.getAccounts().beginProvisioning(
                new Identity(OAuthProvider.GOOGLE, "test|owner", "owner@example.com", "Owner"));
        keys.create(account.getKeyRef());
        context.getAccounts().activate(account,
                new OAuthCredentials("test-access", "test-refresh", Instant.now().plusSeconds(3600)));
        context.getMails().saveInboxPage(account.getId(), page(1, 50));
        gateway.page = page(11, 60);
        return context.getAccounts().find(account.getId());
    }

    static RemoteInboxPage page(long first, long last) {
        List<RemoteMessage> messages = new ArrayList<>();
        for (long uid = last; uid >= first; uid--) {
            messages.add(new RemoteMessage(new RemoteMailHeader(new RemoteMailId(7, uid),
                    "<" + uid + "@example.com>", "sender@example.com", "Sender", "Mail " + uid,
                    Instant.parse("2026-09-20T12:00:00Z").plusSeconds(uid)),
                    List.of(new Recipient("owner@example.com", "owner@example.com", "Owner", RecipientType.TO))));
        }
        return new RemoteInboxPage(7, first, last, messages, true);
    }

    MailViewState await(Predicate<MailViewState> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.test(presenter.state())) {
            long remaining = end - System.nanoTime();
            assertTrue(remaining > 0, "Timed out waiting for presentation state: " + presenter.state());
            Runnable callback = callbacks.poll(remaining, TimeUnit.NANOSECONDS);
            assertNotNull(callback, "No completion before timeout; state: " + presenter.state());
            callback.run();
        }
        return presenter.state();
    }

    MailViewState startReady() throws Exception {
        presenter.start();
        return await(s -> s.inbox().items().size() == 50 && !s.inbox().loading());
    }

    long count(String table) throws Exception {
        return context.getTransactions().read(c -> Sql.one(c, "SELECT COUNT(*) FROM " + table, rs -> rs.getLong(1)));
    }

    void drainCallbacks() {
        Runnable callback;
        while ((callback = callbacks.poll()) != null) callback.run();
    }

    public void close() throws Exception {
        gateway.releaseAll();
        try {
            assertTrue(presenter.closeAsync().toCompletableFuture().get(10, TimeUnit.SECONDS).succeeded());
            drainCallbacks();
            assertEquals(0, gateway.activeSessions.get(), "Every opened transport must close");
        } finally {
            // Also covers a failed test before coordinator startup acquired ownership.
            context.close();
        }
    }
}
