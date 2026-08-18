package com.juanpablo.evermail.exception;

/**
 * Enumeration of all error codes used across Evermail's checked exceptions.
 * Codes are grouped by prefix to keep the enum simple and centralized,
 * instead of defining a dedicated enum per exception type.
 */
public enum ErrorCode {

    // OAuth
    OAUTH_TOKEN_EXPIRED,
    OAUTH_CONNECTION_FAILED,
    OAUTH_INVALID_CREDENTIALS,
    OAUTH_TIMEOUT,
    OAUTH_STATE_MISMATCH,
    OAUTH_PROVIDER_ERROR,
    OAUTH_TOKEN_EXCHANGE_FAILED,
    OAUTH_REFRESH_FAILED,
    OAUTH_BROWSER_UNAVAILABLE,
    OAUTH_USERINFO_FAILED,

    // SMTP
    SMTP_CONNECTION_FAILED,
    SMTP_SEND_REJECTED,

    // IMAP
    IMAP_CONNECTION_FAILED,
    IMAP_FOLDER_NOT_FOUND,
    IMAP_FETCH_FAILED,

    // Database
    DB_CONNECTION_FAILED,
    DB_QUERY_FAILED,

    // Attachment
    ATTACHMENT_NOT_FOUND,
    ATTACHMENT_TOO_LARGE,

    // Security
    CRYPTO_OPERATION_FAILED
}