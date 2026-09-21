package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.EvermailException;
import com.juanpablo.evermail.model.OutboxMessage;

public interface SmtpSession extends AutoCloseable {
    enum Outcome {
        ACCEPTED, REJECTED, UNKNOWN
    }

    Outcome submit(OutboxMessage message, Deadline deadline) throws EvermailException;
    @Override
    void close();
}
