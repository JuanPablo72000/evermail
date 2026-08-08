package com.juanpablo.evermail.config;

/**
 * Static configuration values used across Evermail.
 * Never instantiated — all members are compile-time constants derived
 * from the non-functional requirements defined in the MVP and from
 * limits consumed by other packages (e.g. FileUtil, SqliteConnectionProvider).
 */
public final class AppConstants {

    public static final int MAX_EMAILS_DISPLAYED = 50;
    public static final long MAX_ATTACHMENT_SIZE_BYTES = 25L * 1024 * 1024; // 25 MB
    public static final int LOGIN_TIMEOUT_SECONDS = 2;
    public static final int INBOX_LOAD_TIMEOUT_SECONDS = 3;
    public static final int MAIL_OPEN_TIMEOUT_SECONDS = 5;
    public static final String DB_PATH = System.getenv("APPDATA") + "\\Evermail\\evermail.db";
    public static final int DB_POOL_SIZE = 5;
    public static final int AES_KEY_SIZE_BITS = 256;

    private AppConstants() {
        // Prevents instantiation — this class only holds static constants.
    }
}