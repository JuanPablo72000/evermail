package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class RemoteInboxPage {
    long uidValidity;
    long lowerUid;
    long upperUid;
    List<RemoteMessage> messages;
    boolean hasMore;

    public RemoteInboxPage(long uidValidity, long lowerUid, long upperUid, List<RemoteMessage> messages, boolean hasMore) {
        this.uidValidity = uidValidity;
        this.lowerUid = lowerUid;
        this.upperUid = upperUid;
        this.messages = List.copyOf(messages);
        this.hasMore = hasMore;
    }
}
