package com.juanpablo.evermail.util;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.OAuthAuthenticationException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Handles AES-256-GCM encryption at rest (OAuth tokens, mail body content,
 * attachments) and manages the per-account AES key through the OS-native
 * credential store (Windows Credential Manager / macOS Keychain / Linux
 * Secret Service), via the java-keyring library.
 * <p>
 * Requires a constructor because it initializes the keyring dependency once
 * and reuses it across every call — unlike the other classes in this
 * package, which are fully static.
 */
public class SecurityUtil {

    private static final String KEYRING_SERVICE_NAME = "Evermail";
    private static final String AES_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final Keyring keyring;

    public SecurityUtil() throws OAuthAuthenticationException {
        try {
            this.keyring = Keyring.create();
        } catch (Exception e) {
            throw new OAuthAuthenticationException(
                    ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Failed to initialize OS keyring",
                    e
            );
        }
    }

    public SecretKey generateAesKey() throws OAuthAuthenticationException {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGenerator.init(AppConstants.AES_KEY_SIZE_BITS);
            return keyGenerator.generateKey();

        } catch (NoSuchAlgorithmException e) {
            throw new OAuthAuthenticationException(
                    ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Failed to generate AES key",
                    e
            );
        }
    }

    public void storeAesKey(String accountId, SecretKey key) throws OAuthAuthenticationException {
        try {
            String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
            keyring.setPassword(KEYRING_SERVICE_NAME, accountId, encodedKey);

        } catch (PasswordAccessException e) {
            throw new OAuthAuthenticationException(
                    ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Failed to store AES key in OS keyring for account: " + accountId,
                    e
            );
        }
    }

    public SecretKey retrieveAesKey(String accountId) throws OAuthAuthenticationException {
        try {
            String encodedKey = keyring.getPassword(KEYRING_SERVICE_NAME, accountId);
            byte[] rawKey = Base64.getDecoder().decode(encodedKey);
            return new SecretKeySpec(rawKey, AES_ALGORITHM);

        } catch (PasswordAccessException e) {
            throw new OAuthAuthenticationException(
                    ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Failed to retrieve AES key from OS keyring for account: " + accountId,
                    e
            );
        }
    }

    public String encrypt(String plainText, SecretKey key) throws OAuthAuthenticationException {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes());

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new OAuthAuthenticationException(
                    ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Failed to encrypt data",
                    e
            );
        }
    }

    public String decrypt(String encryptedText, SecretKey key) throws OAuthAuthenticationException {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes);

        } catch (Exception e) {
            throw new OAuthAuthenticationException(
                    ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Failed to decrypt data",
                    e
            );
        }
    }

    public boolean isTokenExpired(LocalDateTime tokenExpiresAt) {
        if (tokenExpiresAt == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(tokenExpiresAt);
    }
}