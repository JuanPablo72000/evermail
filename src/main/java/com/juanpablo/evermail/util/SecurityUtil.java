package com.juanpablo.evermail.util;

import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.CryptoContext;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class SecurityUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    public SecretKey generateKey() throws CryptoException {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (Exception e) {
            throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Unable to generate key", e);
        }
    }

    public String encrypt(String plainText, SecretKey key, CryptoContext context) throws CryptoException {
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(context));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        } catch (Exception e) {
            throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Unable to encrypt field", e);
        }
    }

    public String decrypt(String envelope, SecretKey key, CryptoContext context) throws CryptoException {
        if (envelope == null || !envelope.startsWith("v1:")) {
            throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Unsupported encrypted format");
        }
        return decryptBytes(envelope.substring(3), key, aad(context));
    }

    /** Only the explicit legacy importer may use unauthenticated-context v0 data. */
    public String decryptLegacy(String envelope, SecretKey key) throws CryptoException {
        return decryptBytes(envelope, key, null);
    }

    private String decryptBytes(String envelope, SecretKey key, byte[] context) throws CryptoException {
        try {
            byte[] bytes = Base64.getDecoder().decode(envelope);
            if (bytes.length < 28) {
                throw new IllegalArgumentException("Truncated envelope");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, bytes, 0, 12));
            if (context != null) {
                cipher.updateAAD(context);
            }
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Unable to authenticate encrypted field", e);
        }
    }

    private byte[] aad(CryptoContext context) {
        return (context.getAccountId() + "/" + context.getRecordId() + "/" + context.getFieldName())
                .getBytes(StandardCharsets.UTF_8);
    }
}
