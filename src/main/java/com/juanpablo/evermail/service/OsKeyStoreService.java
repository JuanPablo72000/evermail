package com.juanpablo.evermail.service;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.util.SecurityUtil;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class OsKeyStoreService implements KeyStoreService, AutoCloseable {
    interface PasswordStore extends AutoCloseable {
        String get(String service, String user) throws PasswordAccessException;
        void set(String service, String user, String value) throws PasswordAccessException;
        void remove(String service, String user) throws PasswordAccessException;
    }

    private final PasswordStore store;
    private final SecurityUtil security;
    private static final String SERVICE = "Evermail";

    public OsKeyStoreService(SecurityUtil security) throws CryptoException {
        this.security = security;
        try {
            Keyring keyring = Keyring.create();
            store = new PasswordStore() {
                public String get(String service, String user) throws PasswordAccessException {
                    return keyring.getPassword(service, user);
                }
                public void set(String service, String user, String value) throws PasswordAccessException {
                    keyring.setPassword(service, user, value);
                }
                public void remove(String service, String user) throws PasswordAccessException {
                    keyring.deletePassword(service, user);
                }
                public void close() throws Exception {
                    keyring.close();
                }
            };
        } catch (Exception e) {
            throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Credential store unavailable", e);
        }
    }

    OsKeyStoreService(SecurityUtil security, PasswordStore store) {
        this.security = security;
        this.store = store;
    }

    @Override
    public synchronized void create(String keyRef) throws CryptoException {
        try {
            String existing = optionalPassword(keyRef);
            if (existing != null) {
                throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Key reference already exists");
            }
            store.set(SERVICE, keyRef, Base64.getEncoder().encodeToString(security.generateKey().getEncoded()));
        } catch (PasswordAccessException e) {
            throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Cannot store account key", e);
        }
    }

    @Override
    public synchronized SecretKey read(String keyRef) throws CryptoException {
        try {
            String encoded = optionalPassword(keyRef);
            if (encoded == null) {
                throw new CryptoException(ErrorCode.KEY_NOT_FOUND, "Account key is unavailable");
            }
            byte[] raw = Base64.getDecoder().decode(encoded);
            if (raw.length != 32) {
                throw new IllegalArgumentException("Invalid key length");
            }
            return new SecretKeySpec(raw, "AES");
        } catch (PasswordAccessException | IllegalArgumentException e) {
            throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Cannot read account key", e);
        }
    }

    @Override
    public synchronized void delete(String keyRef) throws CryptoException {
        try {
            if (optionalPassword(keyRef) != null) {
                store.remove(SERVICE, keyRef);
            }
        } catch (PasswordAccessException e) {
            if (!isMissingCredential(e)) {
                throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Cannot remove account key", e);
            }
        }
    }

    private String optionalPassword(String keyRef) throws PasswordAccessException {
        try {
            return store.get(SERVICE, keyRef);
        } catch (PasswordAccessException e) {
            if (isMissingCredential(e)) {
                return null;
            }
            throw e;
        }
    }

    static boolean isMissingCredential(PasswordAccessException error) {
        // java-keyring 1.0.4 exposes Win32 ERROR_NOT_FOUND only through this exact message.
        return System.getProperty("os.name", "").startsWith("Windows")
                && "Error code 1168".equals(error.getMessage());
    }

    @Override
    public void close() throws Exception {
        store.close();
    }
}
