package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class AccountCoordinator {
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface Work<T> {
        T run() throws EvermailException;
    }

    public <T> T exclusive(UUID accountId, Deadline deadline, Work<T> work) throws EvermailException {
        ReentrantLock lock = locks.computeIfAbsent(accountId, ignored -> new ReentrantLock(true));
        try {
            if (!lock.tryLock(deadline.remainingMillis(), TimeUnit.MILLISECONDS)) {
                throw new SessionException(ErrorCode.LOCAL_TIMEOUT, "Account operation is busy");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SessionException(ErrorCode.CANCELLED, "Operation interrupted", e);
        }
        try {
            deadline.check();
            return work.run();
        } finally {
            lock.unlock();
        }
    }
}
