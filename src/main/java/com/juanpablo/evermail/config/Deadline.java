package com.juanpablo.evermail.config;

import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.SessionException;
import java.time.Duration;

public final class Deadline {
    private final long endNanos;

    private Deadline(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("A positive budget is required");
        }
        endNanos = System.nanoTime() + duration.toNanos();
    }

    public static Deadline after(Duration duration) {
        return new Deadline(duration);
    }

    public Duration remaining() throws SessionException {
        long remaining = endNanos - System.nanoTime();
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
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
