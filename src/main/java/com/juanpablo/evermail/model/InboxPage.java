package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class InboxPage {
    List<MailHeader> items;
    InboxCursor nextCursor;
    boolean hasMore;
    boolean stale;
    boolean needsRemote;

    public InboxPage(List<MailHeader> items, InboxCursor nextCursor, boolean hasMore, boolean stale, boolean needsRemote) {
        this.items = List.copyOf(items);
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.stale = stale;
        this.needsRemote = needsRemote;
    }
}
