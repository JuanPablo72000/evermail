package com.juanpablo.evermail.util;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.ErrorCode;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
 * <p>
 * Every failure here is reported as CryptoException, not
 * OAuthAuthenticationException — none of these operations are part of the
 * OAuth handshake itself, even though the keys they protect originated from
 * an OAuth login.
 */
public class SecurityUtil {

    private static final String KEYRING_SERVICE_NAME = "Evermail";
    private static final String AES_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // Reused across calls instead of `new SecureRandom()` per call: the seeding
    // cost is paid once, and this instance is safe for concurrent use.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Keyring keyring;

    public SecurityUtil() throws CryptoException {
        try {
            this.keyring = Keyring.create();
        } catch (Exception e) {
            throw new CryptoException(
                    ErrorCode.CRYPTO_OPERATION_FAILED,
                    "Failed to initialize OS keyring",
                    e
            );
        }
    }

    public SecretKey generateAesKey() throws CryptoException {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGenerator.init(AppConstants.AES_KEY_SIZE_BITS);
            return keyGenerator.generateKey();

        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException(
                    ErrorCode.CRYPTO_OPERATION_FAILED,
                    "Failed to generate AES key",
                    e
            );
        }
    }

    public void storeAesKey(String accountId, SecretKey key) throws CryptoException {
        try {
            String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
            keyring.setPassword(KEYRING_SERVICE_NAME, accountId, encodedKey);

        } catch (PasswordAccessException e) {
            throw new CryptoException(
                    ErrorCode.CRYPTO_OPERATION_FAILED,
                    "Failed to store AES key in OS keyring for account: " + accountId,
                    e
            );
        }
    }

    public SecretKey retrieveAesKey(String accountId) throws CryptoException {
        try {
            String encodedKey = keyring.getPassword(KEYRING_SERVICE_NAME, accountId);
            byte[] rawKey = Base64.getDecoder().decode(encodedKey);
            return new SecretKeySpec(rawKey, AES_ALGORITHM);

        } catch (PasswordAccessException e) {
            throw new CryptoException(
                    ErrorCode.CRYPTO_OPERATION_FAILED,
                    "Failed to retrieve AES key from OS keyring for account: " + accountId,
                    e
            );
        }
    }

    /**
     * Encrypts arbitrary binary data (e.g. an attachment's raw bytes).
     * Used by FileUtil, which never converts file content to String/Base64
     * to avoid the ~33% size overhead and the extra in-memory copy that
     * conversion would require.
     */
    public byte[] encrypt(byte[] plainData, SecretKey key) throws CryptoException {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            byte[] cipherText = cipher.doFinal(plainData);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return buffer.array();

        } catch (Exception e) {
            throw new CryptoException(
                    ErrorCode.CRYPTO_OPERATION_FAILED,
                    "Failed to encrypt binary data",
                    e
            );
        }
    }

    /**
     * Decrypts binary data produced by {@link #encrypt(byte[], SecretKey)}.
     */
    public byte[] decrypt(byte[] encryptedData, SecretKey key) throws CryptoException {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            return cipher.doFinal(cipherText);

        } catch (Exception e) {
            throw new CryptoException(
                    ErrorCode.CRYPTO_OPERATION_FAILED,
                    "Failed to decrypt binary data",
                    e
            );
        }
    }

    /**
     * Encrypts text (tokens, mail body content). Delegates to the byte[]
     * overload and Base64-encodes the result, since every encrypted text
     * column in SQLite is stored as a plain TEXT string.
     */
    public String encrypt(String plainText, SecretKey key) throws CryptoException {
        byte[] combined = encrypt(plainText.getBytes(StandardCharsets.UTF_8), key);
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts text produced by {@link #encrypt(String, SecretKey)}.
     */
    public String decrypt(String encryptedText, SecretKey key) throws CryptoException {
        byte[] decoded = Base64.getDecoder().decode(encryptedText);
        byte[] plainBytes = decrypt(decoded, key);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    public boolean isTokenExpired(LocalDateTime tokenExpiresAt) {
        if (tokenExpiresAt == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(tokenExpiresAt);
    }
}