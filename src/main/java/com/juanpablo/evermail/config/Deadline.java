package com.juanpablo.evermail.config;

import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.SessionException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class Deadline {
    private final long endNanos;
    private final LongSupplier nanoTime;

    Deadline(Duration duration, LongSupplier nanoTime) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("A positive budget is required");
        }
        this.nanoTime = Objects.requireNonNull(nanoTime);
        endNanos = nanoTime.getAsLong() + duration.toNanos();
    }

    public static Deadline after(Duration duration) {
        return new Deadline(duration, System::nanoTime);
    }

    public Duration remaining() throws SessionException {
        if (Thread.currentThread().isInterrupted()) {
            throw new SessionException(ErrorCode.CANCELLED, "Operation interrupted");
        }
        long remaining = endNanos - nanoTime.getAsLong();
        if (remaining <= 0) {
            throw new SessionException(ErrorCode.LOCAL_TIMEOUT, "Operation deadline exceeded");
        }
        return Duration.ofNanos(remaining);
    }

    public int remainingMillis() throws SessionException {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, remaining().toMillis()));
    }

    public void check() throws SessionException {
        remaining();
    }
}
