package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.*;
import com.juanpablo.evermail.support.BackendFixture.MemoryKeys;
import com.juanpablo.evermail.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyImporterTest {
    @TempDir
    Path directory;

    private String encryptLegacy(String value, SecretKey key) throws Exception {
        byte[] nonce = new byte[12];
        new java.security.SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
    }

    @Test
    void importsCredentialsPreservesArchiveAndReauthorizesWithoutChangingKey() throws Exception {
        SecurityUtil security = new SecurityUtil();
        MemoryKeys keys = new MemoryKeys(security);
        keys.create("7");
        try (TransactionManager transactions = new TransactionManager(new SqliteConnectionProvider(directory.resolve("old.db")))) {
            transactions.write(c -> {
                Sql.update(c, "CREATE TABLE email_address(id_address INTEGER PRIMARY KEY,email TEXT)");
                Sql.update(c, """
                        CREATE TABLE account(id_account INTEGER PRIMARY KEY,id_profile INTEGER,id_address INTEGER,
                        provider TEXT,account_name TEXT,access_token TEXT,refresh_token TEXT,token_expires_at TEXT)
                        """);
                Sql.update(c, """
                        CREATE TABLE mail(id_mail INTEGER PRIMARY KEY,id_account INTEGER REFERENCES account(id_account) ON DELETE CASCADE,
                        id_sender_address INTEGER,subject TEXT,date_received TEXT,body_plain_text TEXT)
                        """);
                Sql.update(c, "INSERT INTO email_address VALUES(1,'user@example.com')");
                Sql.update(c, "INSERT INTO account VALUES(7,1,1,'GOOGLE','User',?,?,?)",
                        encryptLegacy("old-access", keys.read("7")), encryptLegacy("old-refresh", keys.read("7")),
                        LocalDateTime.now().plusHours(1).toString());
                Sql.update(c, "INSERT INTO mail VALUES(3,7,1,'Archived','2026-09-19',?)",
                        encryptLegacy("Original body", keys.read("7")));
                return null;
            });
            new DatabaseMigrator(transactions).migrate();
            AccountRepository accounts = new AccountRepository(transactions, security, keys);
            LegacyImporter importer = new LegacyImporter(transactions, accounts, security, keys);
            importer.importAccounts();
            importer.importAccounts();
            Account migrated = accounts.list().getFirst();
            assertEquals(1, accounts.list().size());
            assertEquals(AccountStatus.REAUTH_REQUIRED, migrated.getStatus());
            assertEquals("old-refresh", accounts.readCredentials(migrated.getId()).getRefreshToken());
            assertEquals(1, importer.listArchived(migrated.getId()).size());
            assertEquals("Original body", importer.readArchivedBody(migrated.getId(), 3));
            byte[] keyBefore = keys.read(migrated.getKeyRef()).getEncoded();
            OAuthGateway oauth = new OAuthGateway() {
                public AuthorizationResult authorize(ProviderConfig config, CancellationToken cancel) {
                    return new AuthorizationResult(new Identity(OAuthProvider.GOOGLE, "verified|subject", "user@example.com", "User"),
                            new OAuthCredentials("new-access", "new-refresh", Instant.now().plusSeconds(3600)));
                }
                public OAuthCredentials refresh(ProviderConfig config, OAuthCredentials previous, Deadline deadline) {
                    return previous;
                }
            };
            AuthService auth = new AuthService(accounts, keys, oauth, new EnvConfig(name -> name.endsWith("_REDIRECT_PORT") ? null : "configured"),
                    new AccountCoordinator(), Clock.systemUTC());
            Account authorized = auth.login(OAuthProvider.GOOGLE, new CancellationToken());
            assertEquals(migrated.getId(), authorized.getId());
            assertArrayEquals(keyBefore, keys.read(authorized.getKeyRef()).getEncoded());
            assertEquals("verified|subject", authorized.getProviderSubject());
            assertEquals("Original body", importer.readArchivedBody(authorized.getId(), 3));
            auth.logout(authorized.getId());
            importer.importAccounts();
            assertTrue(accounts.list().isEmpty());
            assertFalse(keys.values.containsKey("7"));
            assertEquals(0, (long) transactions.read(c -> Sql.one(c, "SELECT COUNT(*) FROM legacy_mail", rs -> rs.getLong(1))));
        }
    }
}
