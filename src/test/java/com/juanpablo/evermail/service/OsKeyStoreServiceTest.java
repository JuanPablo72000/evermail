package com.juanpablo.evermail.service;

import com.github.javakeyring.PasswordAccessException;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OsKeyStoreServiceTest {
    private static class Passwords implements OsKeyStoreService.PasswordStore {
        final Map<String, String> values = new HashMap<>();
        String missingError;
        @Override
        public String get(String service, String user) throws PasswordAccessException {
            if (!values.containsKey(user) && missingError != null) {
                throw new PasswordAccessException(missingError);
            }
            return values.get(user);
        }
        @Override
        public void set(String service, String user, String value) {
            values.put(user, value);
        }
        @Override
        public void remove(String service, String user) {
            values.remove(user);
        }
        @Override
        public void close() {
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsNotFoundAllowsCreationAndIdempotentDeletion() throws Exception {
        Passwords passwords = new Passwords();
        passwords.missingError = "Error code 1168";
        try (OsKeyStoreService keys = new OsKeyStoreService(new SecurityUtil(), passwords)) {
            keys.create("reference");
            assertEquals(32, keys.read("reference").getEncoded().length);
            keys.delete("reference");
            keys.delete("reference");
            CryptoException error = assertThrows(CryptoException.class, () -> keys.read("reference"));
            assertEquals(ErrorCode.KEY_NOT_FOUND, error.getErrorCode());
        }
    }

    @Test
    void accessDeniedDoesNotOverwriteOrTreatMissingAsSuccess() throws Exception {
        Passwords passwords = new Passwords();
        passwords.missingError = "Error code 5";
        try (OsKeyStoreService keys = new OsKeyStoreService(new SecurityUtil(), passwords)) {
            assertThrows(CryptoException.class, () -> keys.create("reference"));
            assertThrows(CryptoException.class, () -> keys.delete("reference"));
            assertTrue(passwords.values.isEmpty());
        }
    }

    @Test
    void existingKeyIsNeverOverwritten() throws Exception {
        Passwords passwords = new Passwords();
        try (OsKeyStoreService keys = new OsKeyStoreService(new SecurityUtil(), passwords)) {
            keys.create("reference");
            byte[] first = keys.read("reference").getEncoded();
            assertThrows(CryptoException.class, () -> keys.create("reference"));
            assertArrayEquals(first, keys.read("reference").getEncoded());
        }
    }
}
