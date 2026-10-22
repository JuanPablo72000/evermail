package com.juanpablo.evermail.integration;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Only the remote boundary is simulated; services and persistence remain real. */
final class ScriptedMailGateway implements MailGateway {
    volatile RemoteInboxPage page;
    volatile boolean offline, loseAcknowledgement;
    volatile Gate latestGate, contentGate;
    volatile OutboxMessage submitted;
    final AtomicInteger submissions = new AtomicInteger();
    final AtomicInteger bodyRequests = new AtomicInteger();
    final AtomicInteger activeSessions = new AtomicInteger();

    static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        void pass() throws MailFetchException {
            entered.countDown();
            boolean interrupted = false;
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            try {
                while (true) {
                    long remaining = end - System.nanoTime();
                    if (remaining <= 0) throw new MailFetchException(ErrorCode.NETWORK_TIMEOUT, "Test gate timed out");
                    try {
                        if (!release.await(remaining, TimeUnit.NANOSECONDS))
                            throw new MailFetchException(ErrorCode.NETWORK_TIMEOUT, "Test gate timed out");
                        return;
                    } catch (InterruptedException ignored) {
                        // Model a transport that returns late even after cancellation.
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    public InboxSession openInbox(UUID accountId, Deadline deadline) {
        activeSessions.incrementAndGet();
        return new InboxSession() {
            public RemoteInboxPage fetchLatest(int size, Deadline budget) throws EvermailException {
                if (latestGate != null) latestGate.pass();
                if (offline) throw new MailFetchException(ErrorCode.NETWORK_TIMEOUT, "Simulated offline transport");
                return page;
            }
            public RemoteInboxPage fetchBefore(InboxCursor cursor, int size, Deadline budget) {
                throw new IllegalStateException("Pagination is outside these scenarios");
            }
            public RemoteMailContent fetchContent(RemoteMailId id, Deadline budget) throws EvermailException {
                bodyRequests.incrementAndGet();
                if (contentGate != null) contentGate.pass();
                if (offline) throw new MailFetchException(ErrorCode.NETWORK_TIMEOUT, "Simulated offline transport");
                return new RemoteMailContent("Late remote body", List.of());
            }
            public void close() { activeSessions.decrementAndGet(); }
        };
    }

    public SmtpSession openSmtp(UUID accountId, Deadline deadline) {
        activeSessions.incrementAndGet();
        return new SmtpSession() {
            public Outcome submit(OutboxMessage message, Deadline budget) throws EvermailException {
                submitted = message;
                submissions.incrementAndGet();
                if (loseAcknowledgement) throw new MailSendException(ErrorCode.NETWORK_TIMEOUT, "SMTP acknowledgement lost");
                return Outcome.ACCEPTED;
            }
            public void close() { activeSessions.decrementAndGet(); }
        };
    }

    void releaseAll() {
        if (latestGate != null) latestGate.release.countDown();
        if (contentGate != null) contentGate.release.countDown();
    }
}
