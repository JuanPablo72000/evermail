package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class ComposeRequest {
    UUID submissionId;
    UUID accountId;
    String subject;
    String plainText;
    List<Recipient> recipients;

    public ComposeRequest(UUID submissionId, UUID accountId, String subject, String plainText, List<Recipient> recipients) {
        this.submissionId = submissionId;
        this.accountId = accountId;
        this.subject = subject;
        this.plainText = plainText;
        this.recipients = List.copyOf(recipients);
    }
}
