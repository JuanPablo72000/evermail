package com.juanpablo.evermail.application;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.*;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.juanpablo.evermail.application.SessionSnapshot.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

/** No JavaFX toolkit, real OAuth, database, system key store, sleeps or global executors. */
class ApplicationCoordinatorTest {
    private final Account account = new Account(UUID.randomUUID(), OAuthProvider.GOOGLE, "subject",
            "test@example.com", "Test", "test-key", AccountStatus.ACTIVE);
    private final FakeBackend backend = new FakeBackend();
    private final AtomicInteger opens = new AtomicInteger();
    private final AtomicReference<Thread> openThread = new AtomicReference<>();
    private final List<CountDownLatch> gates = new ArrayList<>();
    private ApplicationCoordinator coordinator;

    @BeforeEach void setup() {
        coordinator = new ApplicationCoordinator(() -> {
            opens.incrementAndGet();
            openThread.set(Thread.currentThread());
            return backend;
        }, 2);
    }

    @AfterEach void cleanup() throws Exception {
        gates.forEach(CountDownLatch::countDown);
        coordinator.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private CountDownLatch gate() {
        CountDownLatch latch = new CountDownLatch(1);
        gates.add(latch);
        return latch;
    }

    private <T> OperationResult<T> result(OperationHandle<T> handle) throws Exception {
        return handle.result().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private void ready() throws Exception {
        backend.startup = new StartupResult(account, StartupStatus.READY);
        assertTrue(result(coordinator.start()).succeeded());
    }

    // Deliberately represents a backend call that cannot be interrupted immediately.
    private static void awaitIgnoringInterrupts(CountDownLatch gate) throws Exception {
        boolean done = false;
        while (!done) {
            try { done = gate.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { continue; }
            if (!done) throw new TimeoutException("Test gate was not released");
        }
    }

    @Test void initializesLazilyOnWorkerAndRestoresOfflineSession() throws Exception {
        assertEquals(0, opens.get());
        backend.startup = new StartupResult(account, StartupStatus.OFFLINE);
        assertTrue(result(coordinator.start()).succeeded());
        assertEquals(OFFLINE, coordinator.session().phase());
        assertEquals(account, coordinator.session().account());
        assertNotSame(Thread.currentThread(), openThread.get());
        assertSame(openThread.get(), backend.startThread);
        assertEquals(1, opens.get());
    }

    @Test void rejectsConcurrentStartupAndUnauthenticatedWork() throws Exception {
        CountDownLatch entered = gate(), release = gate();
        backend.startHook = () -> { entered.countDown(); awaitIgnoringInterrupts(release); };
        OperationHandle<StartupResult> first = coordinator.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertFalse(result(coordinator.start()).succeeded());
        AtomicBoolean ran = new AtomicBoolean();
        assertFalse(result(coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> ran.getAndSet(true))).succeeded());
        release.countDown();
        assertTrue(result(first).succeeded());
        assertEquals(LOGIN_REQUIRED, coordinator.session().phase());
        assertFalse(ran.get());
    }

    @Test void factoryFailureCanBeRetriedWithoutExposingExceptionText() throws Exception {
        coordinator.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        AtomicInteger attempts = new AtomicInteger();
        coordinator = new ApplicationCoordinator(() -> {
            if (attempts.incrementAndGet() == 1) throw new Exception("secret-token");
            return backend;
        }, 2);
        OperationResult<StartupResult> failed = result(coordinator.start());
        assertFalse(failed.succeeded());
        assertFalse(failed.error().message().contains("secret-token"));
        assertEquals(FAILED, coordinator.session().phase());
        assertTrue(result(coordinator.start()).succeeded());
        assertEquals(2, attempts.get());
    }

    @Test void loginAndLogoutUpdateSessionAndCannotCancelDestructiveLogout() throws Exception {
        result(coordinator.start());
        assertTrue(result(coordinator.login(OAuthProvider.GOOGLE)).succeeded());
        assertEquals(READY, coordinator.session().phase());
        OperationHandle<Void> logout = coordinator.logout();
        assertFalse(logout.cancel());
        assertTrue(result(logout).succeeded());
        assertEquals(account.getId(), backend.loggedOut);
        assertEquals(LOGIN_REQUIRED, coordinator.session().phase());
        assertNull(coordinator.session().account());
    }

    @Test void cancellationPreventsLateLoginFromRestoringSession() throws Exception {
        result(coordinator.start());
        CountDownLatch entered = gate(), release = gate();
        backend.loginHook = () -> { entered.countDown(); awaitIgnoringInterrupts(release); };
        OperationHandle<Account> login = coordinator.login(OAuthProvider.GOOGLE);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTrue(login.cancel());
        assertEquals(AUTHENTICATING, coordinator.session().phase());
        release.countDown();
        assertEquals(ErrorCode.CANCELLED, result(login).error().code());
        assertEquals(LOGIN_REQUIRED, coordinator.session().phase());
        assertNull(coordinator.session().account());
        assertFalse(login.cancel());
        assertTrue(result(coordinator.login(OAuthProvider.GOOGLE)).succeeded());
    }

    @Test void sessionChangeDiscardsRunningAndQueuedResultsEvenWhenQueueIsFull() throws Exception {
        ready();
        CountDownLatch entered = gate(), release = gate();
        OperationHandle<String> running = coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> {
            entered.countDown(); awaitIgnoringInterrupts(release); return "old session content";
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        AtomicBoolean queuedRan = new AtomicBoolean();
        OperationHandle<Boolean> queued = coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> queuedRan.getAndSet(true));
        OperationHandle<Void> logout = coordinator.logout();
        assertEquals(LOGGING_OUT, coordinator.session().phase());
        assertNotEquals(running.sessionVersion(), coordinator.session().version());
        assertEquals(logout.sessionVersion(), coordinator.session().version());
        release.countDown();
        assertEquals(ErrorCode.CANCELLED, result(running).error().code());
        assertEquals(ErrorCode.CANCELLED, result(queued).error().code());
        assertFalse(queuedRan.get());
        assertTrue(result(logout).succeeded());
        assertEquals(LOGIN_REQUIRED, coordinator.session().phase());
    }

    @Test void boundsPendingWorkAndStartsBudgetOnlyWhenExecuting() throws Exception {
        ready();
        CountDownLatch entered = gate(), release = gate();
        OperationHandle<Void> running = coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> {
            entered.countDown(); awaitIgnoringInterrupts(release); return null;
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        AtomicReference<Deadline> actual = new AtomicReference<>();
        OperationHandle<Integer> queued = coordinator.submit(Duration.ofSeconds(30), (b, a, d) -> {
            actual.set(d); assertEquals(account, a); return d.remainingMillis();
        });
        assertNull(actual.get());
        assertFalse(result(coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> 3)).succeeded());
        release.countDown();
        assertTrue(result(running).succeeded());
        assertTrue(result(queued).value() > 25000);
        assertNotNull(actual.get());
    }

    @Test void authorizationFailurePreservesLocalSessionForCachedReading() throws Exception {
        ready();
        OperationResult<Void> failure = result(coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> {
            throw new OAuthAuthenticationException(ErrorCode.REAUTH_REQUIRED, "internal details");
        }));
        assertEquals(ErrorCode.REAUTH_REQUIRED, failure.error().code());
        assertEquals(REAUTH_REQUIRED, coordinator.session().phase());
        assertTrue(coordinator.session().hasSession());
        assertEquals(account.getId(), result(coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> a.getId())).value());
    }

    @Test void uncertainSendIsReturnedUnchangedAndNeverRetried() throws Exception {
        ready();
        AtomicInteger sends = new AtomicInteger();
        SendResult unknown = new SendResult(UUID.randomUUID(), DeliveryState.UNKNOWN, null, ErrorCode.DELIVERY_UNKNOWN);
        OperationResult<SendResult> outcome = result(coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> {
            sends.incrementAndGet(); return unknown;
        }));
        assertSame(unknown, outcome.value());
        assertEquals(1, sends.get());
    }

    @Test void failedLogoutRequiresRecoveryRatherThanReusingTheOldSession() throws Exception {
        ready();
        backend.logoutFails = true;
        assertFalse(result(coordinator.logout()).succeeded());
        assertEquals(FAILED, coordinator.session().phase());
        assertNull(coordinator.session().account());
        assertFalse(result(coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> 1)).succeeded());
    }

    @Test void malformedStartupResultFailsWithoutLeavingStartupPermanentlyBusy() throws Exception {
        backend.startup = new StartupResult(null, StartupStatus.READY);
        assertFalse(result(coordinator.start()).succeeded());
        assertEquals(FAILED, coordinator.session().phase());
        backend.startup = new StartupResult(null, StartupStatus.LOGIN_REQUIRED);
        assertTrue(result(coordinator.start()).succeeded());
    }

    @Test void cancelsQueuedWorkWithoutInvokingItAndKeepsSessionUsable() throws Exception {
        ready();
        CountDownLatch entered = gate(), release = gate();
        OperationHandle<Void> running = coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> {
            entered.countDown(); awaitIgnoringInterrupts(release); return null;
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        AtomicBoolean invoked = new AtomicBoolean();
        OperationHandle<Boolean> queued = coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> invoked.getAndSet(true));
        assertTrue(queued.cancel());
        release.countDown();
        assertTrue(result(running).succeeded());
        assertEquals(ErrorCode.CANCELLED, result(queued).error().code());
        assertFalse(invoked.get());
        assertEquals(READY, coordinator.session().phase());
    }

    @Test void closeWaitsForActiveWorkBeforeClosingResourcesAndRejectsNewWork() throws Exception {
        ready();
        CountDownLatch entered = gate(), release = gate();
        OperationHandle<Void> task = coordinator.submit(Duration.ofSeconds(1), (b, a, d) -> {
            entered.countDown(); awaitIgnoringInterrupts(release); return null;
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        CompletableFuture<OperationResult<Void>> closing = coordinator.closeAsync().toCompletableFuture();
        assertFalse(closing.isDone());
        assertEquals(0, backend.closes.get());
        assertEquals(CLOSED, coordinator.session().phase());
        assertFalse(result(coordinator.start()).succeeded());
        release.countDown();
        assertEquals(ErrorCode.CANCELLED, result(task).error().code());
        assertTrue(closing.get(5, TimeUnit.SECONDS).succeeded());
        assertTrue(coordinator.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS).succeeded());
        assertEquals(1, backend.closes.get());
        assertSame(backend.startThread, backend.closeThread);
    }

    @Test void closeReportsResourceFailureWithoutLeakingItsDetails() throws Exception {
        ready();
        backend.closeFails = true;
        OperationResult<Void> result = coordinator.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertFalse(result.succeeded());
        assertFalse(result.error().message().contains("secret"));
        assertEquals(CLOSED, coordinator.session().phase());
    }

    @FunctionalInterface interface Hook { void run() throws Exception; }

    private final class FakeBackend implements BackendAccess {
        StartupResult startup = new StartupResult(null, StartupStatus.LOGIN_REQUIRED);
        Hook startHook = () -> {}, loginHook = () -> {};
        Thread startThread, closeThread;
        UUID loggedOut;
        boolean logoutFails, closeFails;
        AtomicInteger closes = new AtomicInteger();
        public StartupResult start(Deadline deadline) throws Exception {
            startThread = Thread.currentThread(); startHook.run(); return startup;
        }
        public Account login(OAuthProvider provider, CancellationToken token) throws Exception {
            loginHook.run(); return account;
        }
        public void logout(UUID id, Deadline deadline) throws Exception {
            if (logoutFails) throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "secret");
            loggedOut = id;
        }
        public InboxService inbox() { throw new UnsupportedOperationException(); }
        public MailSendService sender() { throw new UnsupportedOperationException(); }
        public void close() throws Exception {
            closeThread = Thread.currentThread(); closes.incrementAndGet();
            if (closeFails) throw new Exception("secret");
        }
    }
}
