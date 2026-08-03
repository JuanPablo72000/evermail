package com.juanpablo.evermail.config;

import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.OAuthAuthenticationException;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;

/**
 * Loads and caches OAuth client credentials from the .env file at
 * construction time. Intended to be instantiated exactly once during
 * application startup and injected into whichever classes need it
 * (e.g. AuthService), following the same manual dependency-injection
 * pattern used across the rest of Evermail.
 */
public class EnvConfig {

    private final String googleClientId;
    private final String googleClientSecret;
    private final String microsoftClientId;
    private final String microsoftClientSecret;

    public EnvConfig() throws OAuthAuthenticationException {
        try {
            Dotenv dotenv = Dotenv.load();

            this.googleClientId = requireValue(dotenv, "GOOGLE_CLIENT_ID");
            this.googleClientSecret = requireValue(dotenv, "GOOGLE_CLIENT_SECRET");
            this.microsoftClientId = requireValue(dotenv, "MICROSOFT_CLIENT_ID");
            this.microsoftClientSecret = requireValue(dotenv, "MICROSOFT_CLIENT_SECRET");

        } catch (DotenvException e) {
            throw new OAuthAuthenticationException(
                    ErrorCode.OAUTH_INVALID_CREDENTIALS,
                    "Failed to load .env file",
                    e
            );
        }
    }

    private String requireValue(Dotenv dotenv, String key) throws OAuthAuthenticationException {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) {
            throw new OAuthAuthenticationException(
                    ErrorCode.OAUTH_INVALID_CREDENTIALS,
                    "Missing required .env variable: " + key
            );
        }
        return value;
    }

    public String getGoogleClientId() {
        return googleClientId;
    }

    public String getGoogleClientSecret() {
        return googleClientSecret;
    }

    public String getMicrosoftClientId() {
        return microsoftClientId;
    }

    public String getMicrosoftClientSecret() {
        return microsoftClientSecret;
    }
}