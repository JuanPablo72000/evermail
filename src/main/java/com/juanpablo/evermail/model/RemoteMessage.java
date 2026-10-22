package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class RemoteMessage {
    RemoteMailHeader header;
    List<Recipient> recipients;

    public RemoteMessage(RemoteMailHeader header, List<Recipient> recipients) {
        this.header = header;
        this.recipients = List.copyOf(recipients);
    }
}
