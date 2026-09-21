package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.OutboxRepository;
import java.util.UUID;

public class MailSendService {
    private final MailGateway gateway;
    private final OutboxRepository outbox;
    private final ComposeService compose;
    private final AccountCoordinator coordinator;

    public MailSendService(MailGateway gateway, OutboxRepository outbox, ComposeService compose, AccountCoordinator coordinator) {
        this.gateway = gateway;
        this.outbox = outbox;
        this.compose = compose;
        this.coordinator = coordinator;
    }

    public SendResult send(ComposeRequest request, Deadline deadline) throws EvermailException {
        ComposeRequest valid = compose.validate(request);
        return coordinator.exclusive(valid.getAccountId(), deadline, () -> deliver(valid, deadline));
    }

    private SendResult deliver(ComposeRequest request, Deadline deadline) throws EvermailException {
        UUID accountId = request.getAccountId();
        UUID id = request.getSubmissionId();
        OutboxMessage message = outbox.prepare(request);
        if (message.getState() == DeliveryState.ACCEPTED || message.getState() == DeliveryState.RECORDED) {
            return outbox.commitSent(accountId, id);
        }
        if (message.getState() != DeliveryState.PENDING) {
            return new SendResult(id, message.getState() == DeliveryState.SENDING ? DeliveryState.UNKNOWN : message.getState(),
                    null, message.getState() == DeliveryState.SENDING ? ErrorCode.DELIVERY_UNKNOWN : message.getLastError());
        }
        // Connection/authentication failures occur before claim and cannot have delivered a message.
        try (SmtpSession session = gateway.openSmtp(accountId, deadline)) {
            deadline.check();
            if (!outbox.claim(accountId, id)) {
                OutboxMessage current = outbox.find(accountId, id);
                return new SendResult(id, current.getState(), null, current.getLastError());
            }
            SmtpSession.Outcome outcome;
            try {
                outcome = session.submit(message, deadline);
            } catch (Exception e) {
                // Conservatively uncertain once the durable SENDING marker exists.
                outcome = SmtpSession.Outcome.UNKNOWN;
            }
            DeliveryState state = switch (outcome) {
                case ACCEPTED -> DeliveryState.ACCEPTED;
                case REJECTED -> DeliveryState.FAILED;
                case UNKNOWN -> DeliveryState.UNKNOWN;
            };
            ErrorCode error = outcome == SmtpSession.Outcome.REJECTED ? ErrorCode.SMTP_REJECTED
                    : outcome == SmtpSession.Outcome.UNKNOWN ? ErrorCode.DELIVERY_UNKNOWN : null;
            try {
                outbox.transition(accountId, id, state, error);
            } catch (EvermailException e) {
                // Persisted SENDING recovers as UNKNOWN; never repeat SMTP after a local failure.
                return new SendResult(id, DeliveryState.UNKNOWN, null, ErrorCode.LOCAL_SAVE_PENDING);
            }
            if (state == DeliveryState.ACCEPTED) {
                try {
                    return outbox.commitSent(accountId, id);
                } catch (EvermailException e) {
                    return new SendResult(id, DeliveryState.ACCEPTED, null, ErrorCode.LOCAL_SAVE_PENDING);
                }
            }
            return new SendResult(id, state, null, error);
        }
    }

    public void recover(UUID accountId) throws EvermailException {
        coordinator.exclusive(accountId, Deadline.after(AppConstants.STARTUP_BUDGET), () -> {
            for (var row : outbox.recoverable(accountId)) {
                if (row.getState() == DeliveryState.SENDING) {
                    outbox.transition(accountId, row.getId(), DeliveryState.UNKNOWN, ErrorCode.DELIVERY_UNKNOWN);
                } else if (row.getState() == DeliveryState.ACCEPTED) {
                    outbox.commitSent(accountId, row.getId());
                }
            }
            return null;
        });
    }
}
