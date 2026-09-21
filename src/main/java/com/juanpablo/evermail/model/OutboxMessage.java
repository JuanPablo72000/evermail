package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class OutboxMessage {
    UUID id;
    UUID accountId;
    String messageId;
    String subject;
    String plainText;
    List<Recipient> recipients;
    DeliveryState state;
    Instant createdAt;
    Instant updatedAt;
    com.juanpablo.evermail.exception.ErrorCode lastError;

    public OutboxMessage(UUID id, UUID accountId, String messageId, String subject, String plainText, List<Recipient> recipients, DeliveryState state, Instant createdAt, Instant updatedAt, com.juanpablo.evermail.exception.ErrorCode lastError) {
        this.id = id;
        this.accountId = accountId;
        this.messageId = messageId;
        this.subject = subject;
        this.plainText = plainText;
        this.recipients = List.copyOf(recipients);
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastError = lastError;
    }
}
