package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OAuthTokenTransportTest {
    @Test void refreshCredentialsAreSentOnlyInAnHttpsPostBody() throws Exception {
        RecordingClient transport = new RecordingClient("Bearer");
        OAuthClient client = new OAuthClient(transport,uri -> fail("Refresh must not open a browser"),Clock.systemUTC());
        OAuthCredentials tokens = client.refresh(new ProviderConfig(OAuthProvider.GOOGLE,"client","PRIVATE_CLIENT_SECRET"),
                new OAuthCredentials("old","PRIVATE_REFRESH",Instant.EPOCH),Deadline.after(Duration.ofSeconds(2)));
        assertEquals("https",transport.request.uri().getScheme());
        assertEquals("oauth2.googleapis.com",transport.request.uri().getHost());
        assertNull(transport.request.uri().getRawQuery()); assertEquals("POST",transport.request.method());
        assertFalse(transport.request.headers().toString().contains("PRIVATE"));
        assertEquals("PRIVATE_REFRESH",OAuthClient.parseQuery(transport.body).get("refresh_token"));
        assertEquals("PRIVATE_CLIENT_SECRET",OAuthClient.parseQuery(transport.body).get("client_secret"));
        assertFalse(tokens.toString().contains("PRIVATE"));
        assertEquals("PRIVATE_REFRESH",tokens.getRefreshToken());
    }
    @Test void unsupportedTokenTypeIsRejectedWithoutEchoingResponse() {
        OAuthClient client = new OAuthClient(new RecordingClient("unsupported"),uri -> {},Clock.systemUTC());
        OAuthAuthenticationException failure = assertThrows(OAuthAuthenticationException.class,
                () -> client.refresh(new ProviderConfig(OAuthProvider.MICROSOFT,"client",null),
                        new OAuthCredentials("old","PRIVATE_REFRESH",Instant.EPOCH),Deadline.after(Duration.ofSeconds(2))));
        assertEquals(ErrorCode.OAUTH_TOKEN_INVALID,failure.getErrorCode()); assertFalse(failure.toString().contains("PRIVATE"));
    }
    /** No sockets are opened. Captures the actual request built by OAuthClient. */
    private static final class RecordingClient extends HttpClient {
        private final String type;
        HttpRequest request; String body;
        RecordingClient(String type) { this.type=type; }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            this.request=request;
            StringBuilder encoded = new StringBuilder();
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
                public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(ByteBuffer buffer) { encoded.append(StandardCharsets.UTF_8.decode(buffer)); }
                public void onError(Throwable failure) { throw new AssertionError(failure); }
                public void onComplete() { body=encoded.toString(); }
            });
            @SuppressWarnings("unchecked") T json = (T)("{\"access_token\":\"PRIVATE_ACCESS\",\"token_type\":\""+type+"\",\"expires_in\":3600}");
            return new HttpResponse<>() {
                public int statusCode() { return 200; }
                public HttpRequest request() { return request; }
                public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                public HttpHeaders headers() { return HttpHeaders.of(Map.of(),(a,b)->true); }
                public T body() { return json; }
                public Optional<SSLSession> sslSession() { return Optional.empty(); }
                public URI uri() { return request.uri(); }
                public Version version() { return Version.HTTP_2; }
            };
        }
        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        public Optional<Duration> connectTimeout() { return Optional.empty(); }
        public Redirect followRedirects() { return Redirect.NEVER; }
        public Optional<ProxySelector> proxy() { return Optional.empty(); }
        public SSLContext sslContext() { try { return SSLContext.getDefault(); } catch (Exception e) { throw new AssertionError(e); } }
        public SSLParameters sslParameters() { return new SSLParameters(); }
        public Optional<Authenticator> authenticator() { return Optional.empty(); }
        public Version version() { return Version.HTTP_2; }
        public Optional<Executor> executor() { return Optional.empty(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h) { throw new UnsupportedOperationException(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p) { throw new UnsupportedOperationException(); }
    }
}
