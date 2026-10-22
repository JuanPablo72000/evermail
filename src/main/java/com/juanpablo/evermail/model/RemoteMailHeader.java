package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.With;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

@Value
@With
@ToString(onlyExplicitlyIncluded = true)
public class RemoteMailHeader {
    RemoteMailId remoteId;
    String messageId;
    String senderEmail;
    String senderName;
    String subject;
    Instant occurredAt;
}
