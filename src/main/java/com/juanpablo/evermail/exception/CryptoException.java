package com.juanpablo.evermail.exception;

/**
 * Thrown when an AES-256-GCM encryption/decryption operation fails, or when
 * the OS-native keyring (Windows Credential Manager / macOS Keychain / Linux
 * Secret Service) cannot store or retrieve a key. Kept separate from
 * OAuthAuthenticationException because these failures are unrelated to the
 * OAuth handshake itself — conflating them could cause a Service to react to
 * a local encryption failure as if the user's session were invalid.
 */
public class CryptoException extends EvermailException {

    public CryptoException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CryptoException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public CryptoException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public CryptoException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}