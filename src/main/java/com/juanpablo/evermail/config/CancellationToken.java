package com.juanpablo.evermail.config;

import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.SessionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public void check() throws SessionException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new SessionException(ErrorCode.CANCELLED, "Operation cancelled");
        }
    }
}
