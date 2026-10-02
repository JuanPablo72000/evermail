package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OAuthBoundaryTest {
    @Test void loopbackRejectsForgedCallbacksAndDoesNotReflectSecrets() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        AtomicReference<URI> local = new AtomicReference<>();
        OAuthClient client = new OAuthClient(http, uri -> {
            assertEquals("https", uri.getScheme());
            Map<String,String> auth = OAuthClient.parseQuery(uri.getRawQuery());
            assertEquals("code", auth.get("response_type"));
            assertEquals("S256", auth.get("code_challenge_method"));
            assertEquals(43, auth.get("code_challenge").length());
            assertFalse(auth.containsKey("code_verifier"));
            assertFalse(auth.containsKey("client_secret"));
            assertNotEquals(auth.get("state"),auth.get("nonce"));
            URI callback = URI.create(auth.get("redirect_uri")); local.set(callback);
            assertEquals("127.0.0.1",callback.getHost());
            assertEquals("http",callback.getScheme());
            for (String query : List.of("state=forged&code=PRIVATE_CODE",
                    "state="+auth.get("state")+"&code=PRIVATE_CODE&error=denied",
                    "state="+auth.get("state")+"&code=PRIVATE_CODE&access_token=PRIVATE_TOKEN")) {
                var response = http.send(HttpRequest.newBuilder(URI.create(callback+"?"+query)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(400,response.statusCode());
                assertFalse(response.body().contains("PRIVATE"));
                assertEquals("no-store",response.headers().firstValue("cache-control").orElseThrow());
                assertEquals("no-referrer",response.headers().firstValue("referrer-policy").orElseThrow());
            }
            var denied = http.send(HttpRequest.newBuilder(URI.create(callback+"?state="+auth.get("state")+"&error=access_denied"))
                    .GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,denied.statusCode());
        },Clock.systemUTC());
        var failure = assertThrows(OAuthAuthenticationException.class,
                () -> client.authorize(new ProviderConfig(OAuthProvider.GOOGLE,"public-id","PRIVATE_CLIENT_SECRET"),new CancellationToken()));
        assertEquals(ErrorCode.OAUTH_PROVIDER_ERROR,failure.getErrorCode());
        assertFalse(failure.toString().contains("PRIVATE"));
        assertThrows(java.io.IOException.class, () -> http.send(HttpRequest.newBuilder(local.get()).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding()));
    }

    @Test void cancellationStopsLocalListenerAndRedirectingHttpClientsAreRejected() {
        CancellationToken cancel = new CancellationToken(); AtomicReference<URI> callback = new AtomicReference<>();
        HttpClient http = HttpClient.newHttpClient();
        OAuthClient client = new OAuthClient(http, uri -> {
            callback.set(URI.create(OAuthClient.parseQuery(uri.getRawQuery()).get("redirect_uri"))); cancel.cancel();
        },Clock.systemUTC());
        assertEquals(ErrorCode.CANCELLED,assertThrows(EvermailException.class,
                () -> client.authorize(new ProviderConfig(OAuthProvider.GOOGLE,"id","secret"),cancel)).getErrorCode());
        assertThrows(java.io.IOException.class, () -> http.send(HttpRequest.newBuilder(callback.get()).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding()));
        assertThrows(IllegalArgumentException.class, () -> new OAuthClient(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(),uri -> {},Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> OAuthClient.parseQuery("state=a&state=b"));
    }

    @Test void transportRequiresTlsIdentityVerificationAndNeverEnablesAuthDebugging() throws Exception {
        Properties imap = MailSessionProvider.imapProperties(1000), smtp = MailSessionProvider.smtpProperties(1000);
        assertEquals("true",imap.getProperty("mail.imaps.ssl.enable"));
        assertEquals("true",imap.getProperty("mail.imaps.ssl.checkserveridentity"));
        assertEquals("true",smtp.getProperty("mail.smtp.starttls.required"));
        assertEquals("true",smtp.getProperty("mail.smtp.ssl.checkserveridentity"));
        for (Properties props : List.of(imap,smtp)) {
            assertEquals("false",props.getProperty("mail.debug"));
            assertEquals("false",props.getProperty("mail.debug.auth"));
        }
        assertEquals("XOAUTH2",imap.getProperty("mail.imaps.auth.mechanisms"));
        assertEquals("XOAUTH2",smtp.getProperty("mail.smtp.auth.mechanisms"));
        EnvConfig config = new EnvConfig(k -> k.equals("MICROSOFT_CLIENT_ID") ? "id" : null);
        assertEquals(53682,config.loadProvider(OAuthProvider.MICROSOFT).getRedirectPort());
        assertNull(config.loadProvider(OAuthProvider.MICROSOFT).getClientSecret());
        for (String invalid : List.of("0","80","65536","not-a-port")) {
            EnvConfig bad = new EnvConfig(k -> k.endsWith("_REDIRECT_PORT") ? invalid : "id");
            assertThrows(ConfigurationException.class, () -> bad.loadProvider(OAuthProvider.MICROSOFT));
        }
    }
}
