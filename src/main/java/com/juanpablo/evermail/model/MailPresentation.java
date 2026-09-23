package com.juanpablo.evermail.model;

/** Sanitized display result, prepared off the FX thread. Deliberately no body in toString. */
public final class MailPresentation {
    private final MailContent content;
    private final String document;
    public MailPresentation(MailContent content, String document) { this.content = content; this.document = document; }
    public MailContent getContent() { return content; }
    public String getDocument() { return document; }
}
