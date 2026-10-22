package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class RemoteMailContent {
    String plainText;
    String html;
    boolean blockedRemoteImages;
    List<Recipient> recipients;

    public RemoteMailContent(String plainText, List<Recipient> recipients) {
        this(plainText, null, false, recipients);
    }

    public RemoteMailContent(String plainText, String html, boolean blockedRemoteImages, List<Recipient> recipients) {
        this.plainText = plainText;
        this.html = html;
        this.blockedRemoteImages = blockedRemoteImages;
        this.recipients = List.copyOf(recipients);
    }
}
