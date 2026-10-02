package com.juanpablo.evermail.application;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static com.juanpablo.evermail.application.SessionSnapshot.Phase.*;

/** Serializes backend work and session transitions. Does not start JavaFX or load any FXML. */
public final class ApplicationCoordinator {
    @FunctionalInterface public interface BackendFactory { BackendAccess open() throws Exception; }
    @FunctionalInterface public interface AccountOperation<T> {
        T run(BackendAccess backend, Account account, Deadline deadline) throws Exception;
    }
    @FunctionalInterface private interface Work<T> { T run(CancellationToken cancellation) throws Exception; }
    @FunctionalInterface private interface Finish<T> { void accept(OperationResult<T> result); }

    private final BackendFactory factory;
    private final ExecutorService worker;
    private final int capacity;
    private final Set<Job<?>> pending = new HashSet<>();
    private final CompletableFuture<OperationResult<Void>> closed = new CompletableFuture<>();
    private volatile SessionSnapshot session = new SessionSnapshot(NEW, null, 0);
    private BackendAccess backend;

    public ApplicationCoordinator() { this(BackendAccess::open, 32); }

    /** Capacity limits normal work; one reserved slot lets logout invalidate a full queue. */
    public ApplicationCoordinator(BackendFactory factory, int capacity) {
        this.factory = Objects.requireNonNull(factory);
        if (capacity < 1) throw new IllegalArgumentException("A positive capacity is required");
        this.capacity = capacity;
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "evermail-backend");
            thread.setDaemon(true);
            return thread;
        });
    }

    public SessionSnapshot session() { return session; }

    public synchronized OperationHandle<StartupResult> start() {
        if (session.phase() != NEW && session.phase() != FAILED) return unavailable();
        transition(STARTING, null);
        return enqueue(cancel -> {
            Deadline deadline = Deadline.after(AppConstants.STARTUP_BUDGET);
            if (backend == null) backend = factory.open();
            cancel.check();
            deadline.check();
            StartupResult result = Objects.requireNonNull(backend.start(deadline));
            Objects.requireNonNull(result.getStatus());
            if (result.getStatus() == StartupStatus.READY || result.getStatus() == StartupStatus.OFFLINE)
                Objects.requireNonNull(result.getAccount());
            return result;
        }, result -> {
            if (!result.succeeded()) { setPhase(FAILED, null); return; }
            StartupResult value = result.value();
            switch (value.getStatus()) {
                case LOGIN_REQUIRED -> setPhase(LOGIN_REQUIRED, null);
                case RECOVERY_REQUIRED -> setPhase(FAILED, null);
                case READY, OFFLINE -> setPhase(value.getAccount().getStatus() == AccountStatus.REAUTH_REQUIRED
                        ? REAUTH_REQUIRED : value.getStatus() == StartupStatus.READY ? READY : OFFLINE, value.getAccount());
            }
        }, true);
    }

    public synchronized OperationHandle<Account> login(OAuthProvider provider) {
        Objects.requireNonNull(provider);
        if (session.phase() != LOGIN_REQUIRED && !session.hasSession()) return unavailable();
        SessionSnapshot previous = session;
        transition(AUTHENTICATING, previous.account());
        return enqueue(cancel -> Objects.requireNonNull(backend.login(provider, cancel)), result -> {
            if (result.succeeded()) setPhase(READY, result.value());
            else setPhase(previous.phase(), previous.account());
        }, true);
    }

    public synchronized OperationHandle<Void> logout() {
        if (!session.hasSession()) return unavailable();
        Account account = session.account();
        transition(LOGGING_OUT, null);
        return enqueue(cancel -> {
            backend.logout(account.getId(), Deadline.after(AppConstants.STARTUP_BUDGET));
            return null;
        }, result -> setPhase(result.succeeded() ? LOGIN_REQUIRED : FAILED, null), false);
    }

    /** The budget starts on the worker, not while waiting in the queue. No automatic retries. */
    public synchronized <T> OperationHandle<T> submit(Duration budget, AccountOperation<T> operation) {
        Objects.requireNonNull(operation);
        if (budget.isZero() || budget.isNegative()) throw new IllegalArgumentException("A positive budget is required");
        if (!session.hasSession()) return unavailable();
        if (pending.size() >= capacity) return busy();
        Account account = session.account();
        return enqueue(cancel -> operation.run(backend, account, Deadline.after(budget)), result -> {
            if (result.error() != null && result.error().code() == ErrorCode.REAUTH_REQUIRED)
                setPhase(REAUTH_REQUIRED, account.withStatus(AccountStatus.REAUTH_REQUIRED));
        }, true);
    }

    /** Non-blocking. Cancels pending work, then closes resources only after the active operation exits. */
    public synchronized CompletionStage<OperationResult<Void>> closeAsync() {
        if (session.phase() == CLOSED) return closed.minimalCompletionStage();
        transition(CLOSED, null);
        worker.execute(() -> {
            OperationResult<Void> result;
            try {
                if (backend != null) backend.close();
                result = OperationResult.success(null);
            } catch (Exception error) {
                result = OperationResult.failure(OperationError.from(error));
            }
            closed.complete(result);
        });
        worker.shutdown();
        return closed.minimalCompletionStage();
    }

    private void transition(SessionSnapshot.Phase phase, Account account) {
        session = new SessionSnapshot(phase, account, session.version() + 1);
        pending.forEach(Job::requestCancellation);
    }

    private void setPhase(SessionSnapshot.Phase phase, Account account) {
        session = new SessionSnapshot(phase, account, session.version());
    }

    private <T> OperationHandle<T> enqueue(Work<T> work, Finish<T> finish, boolean cancellable) {
        Job<T> job = new Job<>(session.version(), work, finish, cancellable);
        pending.add(job);
        worker.execute(job);
        return job;
    }

    private <T> OperationHandle<T> unavailable() {
        return rejected(new OperationError(ErrorCode.SESSION_UNAVAILABLE, "La sesión no está disponible para esta operación."));
    }

    private <T> OperationHandle<T> busy() {
        return rejected(new OperationError(ErrorCode.LOCAL_TIMEOUT, "Hay demasiadas operaciones pendientes. Espera a que terminen."));
    }

    private <T> OperationHandle<T> rejected(OperationError error) {
        long version = session.version();
        return new OperationHandle<>() {
            public long sessionVersion() { return version; }
            public CompletionStage<OperationResult<T>> result() {
                return CompletableFuture.completedFuture(OperationResult.<T>failure(error)).minimalCompletionStage();
            }
            public boolean cancel() { return false; }
        };
    }

    private final class Job<T> implements Runnable, OperationHandle<T> {
        private final long version;
        private final Work<T> work;
        private final Finish<T> finish;
        private final boolean cancellable;
        private final CancellationToken cancellation = new CancellationToken();
        private final CompletableFuture<OperationResult<T>> completion = new CompletableFuture<>();
        private Thread running;
        private boolean cancelled;
        private boolean finished;

        Job(long version, Work<T> work, Finish<T> finish, boolean cancellable) {
            this.version = version; this.work = work; this.finish = finish; this.cancellable = cancellable;
        }

        public CompletionStage<OperationResult<T>> result() { return completion.minimalCompletionStage(); }
        public long sessionVersion() { return version; }

        public boolean cancel() {
            synchronized (ApplicationCoordinator.this) {
                if (!cancellable || finished) return false;
                requestCancellation();
                return true;
            }
        }

        private void requestCancellation() {
            cancelled = true;
            cancellation.cancel();
            if (running != null) running.interrupt();
        }

        public void run() {
            OperationResult<T> result;
            synchronized (ApplicationCoordinator.this) { running = Thread.currentThread(); }
            try {
                cancellation.check();
                result = OperationResult.success(work.run(cancellation));
            } catch (Exception failure) {
                result = OperationResult.failure(OperationError.from(failure));
            }
            synchronized (ApplicationCoordinator.this) {
                running = null;
                Thread.interrupted();
                if (cancelled || version != session.version())
                    result = OperationResult.failure(new OperationError(ErrorCode.CANCELLED, "La operación fue cancelada."));
                if (version == session.version() && session.phase() != CLOSED) finish.accept(result);
                pending.remove(this);
                finished = true;
            }
            completion.complete(result);
        }
    }
}
