package com.juanpablo.evermail.config;

import java.time.Duration;

public final class AppConstants {
    public static final int MAX_EMAILS_DISPLAYED = 50;
    public static final int AES_KEY_SIZE_BITS = 256;
    public static final int MAX_BODY_BYTES = 1_048_576;
    public static final int MAX_INLINE_IMAGE_BYTES = 524_288;
    public static final int MAX_INLINE_TOTAL_BYTES = 2_097_152;
    public static final int MAX_MIME_PARTS = 256;
    public static final int MAX_HTML_NODES = 20_000;
    public static final Duration STARTUP_BUDGET = Duration.ofSeconds(5);
    public static final Duration INBOX_BUDGET = Duration.ofSeconds(30);
    public static final Duration OPEN_HEADER_BUDGET = Duration.ofSeconds(2);
    public static final Duration CONTENT_BUDGET = Duration.ofSeconds(30);
    public static final Duration SEND_BUDGET = Duration.ofSeconds(60);
    public static final Duration OAUTH_AUTHORIZATION_TIMEOUT = Duration.ofMinutes(3);
    public static final Duration TOKEN_REFRESH_MARGIN = Duration.ofSeconds(60);

    private AppConstants() {
    }
}
