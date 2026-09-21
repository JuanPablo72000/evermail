package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.With;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

@Value
@With
@ToString(onlyExplicitlyIncluded = true)
public class MailHeader {
    UUID id;
    UUID accountId;
    MailDirection direction;
    RemoteMailId remoteId;
    String messageId;
    UUID outboundId;
    String senderEmail;
    String senderName;
    String subject;
    Instant occurredAt;
    boolean read;
    boolean bodyCached;
}
