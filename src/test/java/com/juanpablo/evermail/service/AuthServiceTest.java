package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.support.BackendFixture;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest extends BackendFixture {
    private final AtomicInteger refreshes = new AtomicInteger();

    private AuthService auth(boolean rejectRefresh, String refreshToken) {
        OAuthGateway oauth = new OAuthGateway() {
            @Override
            public AuthorizationResult authorize(ProviderConfig config, CancellationToken cancel) {
                return new AuthorizationResult(new Identity(account.getProvider(), account.getProviderSubject(),
                        account.getEmail(), "Updated"), new OAuthCredentials("new-access", refreshToken, Instant.now().plusSeconds(3600)));
            }
            @Override
            public OAuthCredentials refresh(ProviderConfig config, OAuthCredentials previous, Deadline deadline) throws EvermailException {
                refreshes.incrementAndGet();
                if (rejectRefresh) {
                    throw new OAuthAuthenticationException(ErrorCode.REAUTH_REQUIRED, "Rejected");
                }
                return new OAuthCredentials("fresh", previous.getRefreshToken(), Instant.now().plusSeconds(3600));
            }
        };
        return new AuthService(accounts, keys, oauth, new EnvConfig(name -> name.endsWith("_REDIRECT_PORT") ? null : "configured"),
                coordinator, Clock.systemUTC());
    }

    @Test
    void reauthorizationPreservesKeyAndExistingEncryptedMail() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
        UUID mailId = mails.readInbox(account.getId(), null, 50).getItems().getFirst().getId();
        mails.saveContent(account.getId(), mailId, new RemoteMailContent("Old content", List.of()));
        var originalKey = keys.read(account.getKeyRef());
        Account updated = auth(false, null).login(OAuthProvider.GOOGLE, new CancellationToken());
        assertEquals(account.getId(), updated.getId());
        assertArrayEquals(originalKey.getEncoded(), keys.read(updated.getKeyRef()).getEncoded());
        assertEquals("refresh", accounts.readCredentials(account.getId()).getRefreshToken());
        assertEquals("Old content", mails.readContent(account.getId(), mailId).getPlainText());
        assertEquals(1, count("account"));
    }

    @Test
    void simultaneousRefreshesUseSingleRotatingTokenRequest() throws Exception {
        accounts.activate(account, new OAuthCredentials("expired", "refresh", Instant.now().minusSeconds(1)));
        AuthService auth = auth(false, "refresh");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> auth.ensureCredentials(account.getId(), budget()));
            var second = executor.submit(() -> auth.ensureCredentials(account.getId(), budget()));
            assertEquals("fresh", first.get().getAccessToken());
            assertEquals("fresh", second.get().getAccessToken());
        }
        assertEquals(1, refreshes.get());
    }

    @Test
    void rejectedRefreshRequiresAuthorizationWithoutErasingCache() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
        accounts.activate(account, new OAuthCredentials("expired", "refresh", Instant.now().minusSeconds(1)));
        assertThrows(OAuthAuthenticationException.class, () -> auth(true, null).ensureCredentials(account.getId(), budget()));
        assertEquals(AccountStatus.REAUTH_REQUIRED, accounts.find(account.getId()).getStatus());
        assertEquals(1, count("mail"));
        assertNotNull(keys.read(account.getKeyRef()));
    }

    @Test
    void restoringExpiredSessionUsesLocalCacheWithoutNetwork() throws Exception {
        accounts.activate(account, new OAuthCredentials("expired", "refresh", Instant.now().minusSeconds(1)));
        assertEquals(StartupStatus.OFFLINE, auth(false, null).restoreSession(budget()).getStatus());
        assertEquals(0, refreshes.get());
    }

    @Test
    void logoutDeletesOwnedRowsAndKeyAndIsIdempotent() throws Exception {
        sender.send(request(UUID.randomUUID()), budget());
        AuthService auth = auth(false, "refresh");
        auth.logout(account.getId());
        auth.logout(account.getId());
        assertEquals(0, count("account"));
        assertEquals(0, count("mail"));
        assertEquals(0, count("outbox_message"));
        assertThrows(CryptoException.class, () -> keys.read(account.getKeyRef()));
    }

    @Test
    void onlySelectedProviderConfigurationIsRequired() throws Exception {
        EnvConfig config = new EnvConfig(name -> name.equals("MICROSOFT_CLIENT_ID") ? "client" : null);
        ProviderConfig microsoft = config.loadProvider(OAuthProvider.MICROSOFT);
        assertNull(microsoft.getClientSecret());
        assertThrows(ConfigurationException.class, () -> config.loadProvider(OAuthProvider.GOOGLE));
    }

    @Test
    void invalidCallbackDoesNotAcceptDuplicateStateOrCorruptEncodedCode() {
        assertEquals("a+b=c", OAuthClient.parseQuery("code=a%2Bb%3Dc&state=s").get("code"));
        assertThrows(IllegalArgumentException.class, () -> OAuthClient.parseQuery("state=a&state=b"));
    }

    @Test
    void protocolPropertiesEnforceOAuthAndTls() {
        Properties imap = MailSessionProvider.imapProperties(1000);
        assertEquals("XOAUTH2", imap.getProperty("mail.imaps.auth.mechanisms"));
        assertNull(imap.getProperty("mail.imap.auth.mechanisms"));
        Properties smtp = MailSessionProvider.smtpProperties(1000);
        assertEquals("true", smtp.getProperty("mail.smtp.starttls.required"));
        assertEquals("false", smtp.getProperty("mail.smtp.sendpartial"));
        assertEquals("true", smtp.getProperty("mail.smtp.ssl.checkserveridentity"));
    }
}
