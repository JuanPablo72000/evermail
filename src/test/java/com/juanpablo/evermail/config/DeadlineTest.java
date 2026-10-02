package com.juanpablo.evermail.config;

import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.SessionException;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DeadlineTest {
    @Test
    void reportsRemainingBudgetAndExpiresAtTheBoundary() throws Exception {
        AtomicLong now = new AtomicLong(100);
        Deadline deadline = new Deadline(Duration.ofMillis(5), now::get);
        now.addAndGet(Duration.ofMillis(4).toNanos());
        assertEquals(Duration.ofMillis(1), deadline.remaining());
        now.addAndGet(Duration.ofMillis(1).toNanos());
        assertEquals(ErrorCode.LOCAL_TIMEOUT,
                assertThrows(SessionException.class, deadline::check).getErrorCode());
    }

    @Test
    void interruptionIsCancellationAndDoesNotClearTheInterruptFlag() {
        Deadline deadline = Deadline.after(Duration.ofSeconds(5));
        try {
            Thread.currentThread().interrupt();
            assertEquals(ErrorCode.CANCELLED,
                    assertThrows(SessionException.class, deadline::check).getErrorCode());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void subMillisecondTimeoutNeverBecomesAnUnlimitedZeroTimeout() throws Exception {
        assertEquals(1, new Deadline(Duration.ofNanos(1), () -> 0).remainingMillis());
    }

    @Test
    void remainingMillisIsCappedForNetworkApis() throws Exception {
        assertEquals(Integer.MAX_VALUE,
                new Deadline(Duration.ofDays(30), () -> 0).remainingMillis());
    }

    @Test
    void monotonicCounterWraparoundPreservesTheBudget() throws Exception {
        AtomicLong now = new AtomicLong(Long.MAX_VALUE - 10);
        Deadline deadline = new Deadline(Duration.ofNanos(20), now::get);
        now.addAndGet(15);
        assertEquals(Duration.ofNanos(5), deadline.remaining());
    }

    @Test
    void rejectsNonPositiveBudgets() {
        assertThrows(IllegalArgumentException.class, () -> Deadline.after(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Deadline.after(Duration.ofSeconds(-1)));
    }
}
