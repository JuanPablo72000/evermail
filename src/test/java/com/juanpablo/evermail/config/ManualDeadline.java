package com.juanpablo.evermail.config;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** Per-test monotonic clock: no sleeps, shared state, or changes to the system clock. */
public final class ManualDeadline {
    private final AtomicLong nanos = new AtomicLong();
    private final Duration budget;
    private final Deadline deadline;

    public ManualDeadline(Duration budget) {
        this.budget = budget;
        deadline = new Deadline(budget, nanos::get);
    }

    public Deadline deadline() {
        return deadline;
    }

    public void expire() {
        nanos.set(budget.toNanos());
    }
}
