package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class MailContent {
    UUID mailId;
    String plainText;
    List<Recipient> recipients;

    public MailContent(UUID mailId, String plainText, List<Recipient> recipients) {
        this.mailId = mailId;
        this.plainText = plainText;
        this.recipients = List.copyOf(recipients);
    }
}
