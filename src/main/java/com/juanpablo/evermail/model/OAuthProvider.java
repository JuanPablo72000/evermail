package com.juanpablo.evermail.model;

import java.util.List;

/**
 * OAuth 2.0 providers supported by Evermail, plus the mail server endpoints
 * each provider exposes for IMAP/SMTP access via XOAUTH2.
 *
 * <p>Centralizing both the OAuth endpoints and the mail server hosts here lets
 * AuthService and MailSessionProvider be a single algorithm parameterized by
 * provider, instead of divergent per-provider code paths.
 */
public enum OAuthProvider {

    GOOGLE(
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            List.of("https://mail.google.com/", "openid", "email", "profile"),
            true,
            "imap.gmail.com", 993,
            "smtp.gmail.com", 587
    ),

    MICROSOFT(
            "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            List.of(
                    "https://outlook.office.com/IMAP.AccessAsUser.All",
                    "https://outlook.office.com/SMTP.Send",
                    "offline_access", "openid", "email", "profile"
            ),
            false,
            "outlook.office365.com", 993,
            "smtp.office365.com", 587
    );

    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final List<String> scopes;
    private final boolean requiresClientSecret;
    private final String imapHost;
    private final int imapPort;
    private final String smtpHost;
    private final int smtpPort;

    OAuthProvider(String authorizationEndpoint, String tokenEndpoint, List<String> scopes,
                  boolean requiresClientSecret,
                  String imapHost, int imapPort, String smtpHost, int smtpPort) {
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.scopes = scopes;
        this.requiresClientSecret = requiresClientSecret;
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
    }

    public String getAuthorizationEndpoint() { return authorizationEndpoint; }
    public String getTokenEndpoint() { return tokenEndpoint; }
    public List<String> getScopes() { return scopes; }
    public boolean requiresClientSecret() { return requiresClientSecret; }
    public String getImapHost() { return imapHost; }
    public int getImapPort() { return imapPort; }
    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
}