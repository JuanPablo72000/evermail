package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.KeyStoreService;
import com.juanpablo.evermail.util.*;
import java.time.Instant;
import java.util.*;

public class OutboxRepository {
    private final TransactionManager transactions;
    private final AccountRepository accounts;
    private final KeyStoreService keys;
    private final SecurityUtil security;
    private final OutboxDAO outbox = new OutboxDAO();
    private final RecipientDAO recipients = new RecipientDAO();
    private final MailDAO mails = new MailDAO();

    public OutboxRepository(TransactionManager transactions, AccountRepository accounts, KeyStoreService keys, SecurityUtil security) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.keys = keys;
        this.security = security;
    }

    public OutboxMessage prepare(ComposeRequest request) throws EvermailException {
        List<Recipient> normalized = EmailValidator.validateRecipients(request.getRecipients());
        OutboxMessage existing = find(request.getAccountId(), request.getSubmissionId());
        if (existing != null) {
            verifySame(existing, request, normalized);
            return existing;
        }
        Account account = accounts.require(request.getAccountId());
        String encrypted = security.encrypt(request.getPlainText(), keys.read(account.getKeyRef()),
                new CryptoContext(account.getId(), request.getSubmissionId(), "outbox"));
        Instant now = Instant.now();
        OutboxDAO.Row row = new OutboxDAO.Row(request.getSubmissionId(), account.getId(),
                "<" + request.getSubmissionId() + "@evermail.local>", request.getSubject(), encrypted,
                DeliveryState.PENDING, now, now, null);
        boolean created = transactions.write(c -> {
            if (outbox.find(c, account.getId(), request.getSubmissionId()) != null) {
                return false;
            }
            outbox.insert(c, row);
            recipients.replace(c, row.getId(), normalized, true);
            return true;
        });
        OutboxMessage result = find(account.getId(), row.getId());
        if (!created) {
            verifySame(result, request, normalized);
        }
        return result;
    }

    private void verifySame(OutboxMessage existing, ComposeRequest request, List<Recipient> normalized) throws ValidationException {
        var oldRecipients = existing.getRecipients().stream().map(r -> r.getAddressKey() + "/" + r.getType()).sorted().toList();
        var newRecipients = normalized.stream().map(r -> r.getAddressKey() + "/" + r.getType()).sorted().toList();
        if (!existing.getSubject().equals(request.getSubject()) || !existing.getPlainText().equals(request.getPlainText())
                || !oldRecipients.equals(newRecipients)) {
            throw new ValidationException(ErrorCode.IDEMPOTENCY_CONFLICT, "This submission already has different content");
        }
    }

    public OutboxMessage find(UUID accountId, UUID id) throws EvermailException {
        Account account = accounts.require(accountId);
        OutboxDAO.Row row = transactions.read(c -> outbox.find(c, accountId, id));
        if (row == null) {
            return null;
        }
        String plain = security.decrypt(row.getBodyCipher(), keys.read(account.getKeyRef()), new CryptoContext(accountId, id, "outbox"));
        return new OutboxMessage(id, accountId, row.getMessageId(), row.getSubject(), plain,
                transactions.read(c -> recipients.find(c, id, true)), row.getState(), row.getCreatedAt(), row.getUpdatedAt(), row.getError());
    }

    public boolean claim(UUID accountId, UUID id) throws EvermailException {
        return transactions.write(c -> outbox.transition(c, accountId, id, DeliveryState.PENDING, DeliveryState.SENDING, null));
    }

    public void transition(UUID accountId, UUID id, DeliveryState next, ErrorCode error) throws EvermailException {
        if (!Set.of(DeliveryState.ACCEPTED, DeliveryState.FAILED, DeliveryState.UNKNOWN).contains(next)) {
            throw new IllegalArgumentException("Invalid delivery transition");
        }
        transactions.write(c -> {
            if (!outbox.transition(c, accountId, id, DeliveryState.SENDING, next, error)) {
                throw new SessionException(ErrorCode.SESSION_UNAVAILABLE, "Delivery state changed");
            }
            return null;
        });
    }

    public SendResult commitSent(UUID accountId, UUID id) throws EvermailException {
        OutboxMessage message = find(accountId, id);
        if (message == null || (message.getState() != DeliveryState.ACCEPTED && message.getState() != DeliveryState.RECORDED)) {
            throw new SessionException(ErrorCode.SESSION_UNAVAILABLE, "No confirmed delivery to record");
        }
        Account account = accounts.require(accountId);
        UUID mailId = UUID.nameUUIDFromBytes(("sent/" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String cipher = security.encrypt(message.getPlainText(), keys.read(account.getKeyRef()), new CryptoContext(accountId, mailId, "body"));
        return transactions.write(c -> {
            OutboxDAO.Row row = outbox.find(c, accountId, id);
            if (row.getState() == DeliveryState.RECORDED) {
                return new SendResult(id, DeliveryState.RECORDED, mailId, null);
            }
            if (row.getState() != DeliveryState.ACCEPTED) {
                throw new SessionException(ErrorCode.SESSION_UNAVAILABLE, "Delivery is not confirmed");
            }
            mails.insert(c, new MailDAO.Row(new MailHeader(mailId, accountId, MailDirection.SENT, null,
                    row.getMessageId(), id, account.getEmail(), account.getDisplayName(), row.getSubject(),
                    row.getUpdatedAt(), true, true), cipher));
            recipients.replace(c, mailId, message.getRecipients(), false);
            outbox.transition(c, accountId, id, DeliveryState.ACCEPTED, DeliveryState.RECORDED, null);
            return new SendResult(id, DeliveryState.RECORDED, mailId, null);
        });
    }

    public List<OutboxDAO.Row> recoverable(UUID accountId) throws EvermailException {
        return transactions.read(c -> outbox.recoverable(c, accountId));
    }
}
