package com.juanpablo.evermail.presentation;

import com.juanpablo.evermail.model.*;
import java.util.*;

/** Plain-text composition. Sender always comes from the authenticated account, never an editable field. */
public record ComposeDraft(String to, String cc, String bcc, String subject, String message) {
    public ComposeDraft {
        to = Objects.requireNonNullElse(to, "");
        cc = Objects.requireNonNullElse(cc, "");
        bcc = Objects.requireNonNullElse(bcc, "");
        subject = Objects.requireNonNullElse(subject, "");
        message = Objects.requireNonNullElse(message, "");
    }
    public static ComposeDraft empty() { return new ComposeDraft("", "", "", "", ""); }
    public boolean isEmpty() { return to.isBlank() && cc.isBlank() && bcc.isBlank() && subject.isBlank() && message.isBlank(); }

    public ComposeRequest request(UUID accountId, UUID submissionId) {
        List<Recipient> recipients = new ArrayList<>();
        add(recipients, to, RecipientType.TO);
        add(recipients, cc, RecipientType.CC);
        add(recipients, bcc, RecipientType.BCC);
        return new ComposeRequest(submissionId, accountId, subject, message, recipients);
    }

    private static void add(List<Recipient> recipients, String input, RecipientType type) {
        // Bare addresses separated by commas or semicolons; validation stays in ComposeService.
        for (String value : input.split("[,;]")) {
            if (!value.isBlank()) recipients.add(new Recipient(value.strip(), value.strip(), null, type));
        }
    }

    @Override public String toString() { return "ComposeDraft[private]"; }
}
