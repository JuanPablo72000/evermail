package com.juanpablo.evermail.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.config.EnvConfig;
import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.OAuthAuthenticationException;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.AppProfile;
import com.juanpablo.evermail.model.EmailAddress;
import com.juanpablo.evermail.model.OAuthProvider;
import com.juanpablo.evermail.repository.AccountRepository;
import com.juanpablo.evermail.util.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Single owner of the OAuth 2.0 Authorization Code flow with PKCE, for both
 * Google and Microsoft. Implemented manually against each provider's documented
 * endpoints using the JDK's {@link HttpClient} plus a short-lived loopback
 * {@link HttpServer} to capture the redirect — deliberately avoiding an external
 * OAuth SDK so the protocol itself stays visible (a portfolio decision, see
 * uml-service.md).
 *
 * <p>All methods are synchronous and blocking; the {@code facade} layer wraps
 * them in {@code Task<T>} so the browser-based login never freezes the JavaFX
 * Application Thread.
 *
 * <p>Encryption is NOT this class's concern: it hands plain-text tokens to
 * {@link AccountRepository#create} / {@link AccountRepository#save}, which own
 * the AES encryption orchestration. The only {@link SecurityUtil} method used
 * here is {@code isTokenExpired}, keeping expiration logic centralized.
 */
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String REDIRECT_HOST = "127.0.0.1";
    private static final String REDIRECT_PATH = "/callback";
    private static final String CODE_CHALLENGE_METHOD = "S256";

    // Default profile for a brand-new account. These are MVP defaults and could
    // later move to AppConstants or become user-selectable during onboarding.
    private static final String DEFAULT_THEME = "light";
    private static final int DEFAULT_SYNC_INTERVAL_MINUTES = 15;
    private static final String DEFAULT_LANGUAGE = "en";
    private static final boolean DEFAULT_NOTIFICATIONS_ENABLED = true;

    private final AccountRepository accountRepository;
    private final EnvConfig envConfig;
    private final SecurityUtil securityUtil;
    private final HttpClient httpClient;
    private final Gson gson;

    public AuthService(AccountRepository accountRepository,
                       EnvConfig envConfig,
                       SecurityUtil securityUtil) {
        this.accountRepository = accountRepository;
        this.envConfig = envConfig;
        this.securityUtil = securityUtil;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    /**
     * Runs the full Authorization Code + PKCE flow for the given provider,
     * resolves the user's identity, and persists a brand-new Account.
     *
     * @return the persisted Account, with its tokens left in plain text
     *         (encryption at rest is handled inside AccountRepository.create).
     */
    public Account login(OAuthProvider provider)
            throws OAuthAuthenticationException, DatabaseException, CryptoException {

        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state = generateState();

        HttpServer server = null;
        try {
            // Ephemeral port (0) so we never collide with a port already in use.
            server = HttpServer.create(new InetSocketAddress(REDIRECT_HOST, 0), 0);
            int port = server.getAddress().getPort();
            String redirectUri = "http://" + REDIRECT_HOST + ":" + port + REDIRECT_PATH;

            CompletableFuture<CallbackResult> callbackFuture = new CompletableFuture<>();
            server.createContext(REDIRECT_PATH, exchange -> handleCallback(exchange, callbackFuture));
            server.start();

            String authorizationUrl = buildAuthorizationUrl(provider, redirectUri, state, codeChallenge);
            openInBrowser(authorizationUrl);

            CallbackResult callback = waitForCallback(callbackFuture);

            if (callback.error() != null) {
                throw new OAuthAuthenticationException(ErrorCode.OAUTH_PROVIDER_ERROR,
                        "OAuth provider returned an error: " + callback.error());
            }
            if (!state.equals(callback.state())) {
                throw new OAuthAuthenticationException(ErrorCode.OAUTH_STATE_MISMATCH,
                        "OAuth state parameter mismatch — possible CSRF, aborting login");
            }

            TokenData tokens = exchangeCodeForTokens(provider, callback.code(), redirectUri, codeVerifier);
            UserIdentity identity = extractIdentity(tokens.idToken());

            Account account = new Account();
            account.setProvider(provider);
            account.setAccessToken(tokens.accessToken());
            account.setRefreshToken(tokens.refreshToken());
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokens.expiresIn()));
            account.setAccountName(identity.displayName());

            AppProfile profile = buildDefaultProfile();
            EmailAddress emailAddress = buildInternalAddress(identity.email());

            return accountRepository.create(account, profile, emailAddress);

        } catch (IOException e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Network error during OAuth login", e);
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    /**
     * Checks the account's access token and refreshes it only if expired.
     * Expects {@code account} to already carry decrypted tokens (as returned by
     * AccountRepository.getById / getAll). Re-encryption happens inside
     * AccountRepository.save.
     */
    public void refreshTokenIfNeeded(Account account)
            throws OAuthAuthenticationException, DatabaseException, CryptoException {

        if (!securityUtil.isTokenExpired(account.getTokenExpiresAt())) {
            return;
        }

        OAuthProvider provider = account.getProvider();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", account.getRefreshToken());
        params.put("client_id", getClientId(provider));
        if (provider.requiresClientSecret()) {
            params.put("client_secret", getClientSecret(provider));
        }

        JsonObject json = postToTokenEndpoint(provider, params, ErrorCode.OAUTH_REFRESH_FAILED);
        TokenData tokens = parseTokenResponse(json);

        account.setAccessToken(tokens.accessToken());
        account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokens.expiresIn()));
        // Some providers rotate the refresh_token on each use; keep the new one if present.
        if (tokens.refreshToken() != null) {
            account.setRefreshToken(tokens.refreshToken());
        }

        accountRepository.save(account);
    }

    /**
     * Clears the local session by deleting the account row. The schema cascades
     * this to mail/drafts/labels. Server-side token revocation is out of scope
     * for the MVP (documented TODO), and removing the AES key from the OS keyring
     * is also a TODO (SecurityUtil has no deleteAesKey yet).
     */
    public void logout(Account account) throws DatabaseException {
        accountRepository.delete(account.getIdAccount());
    }

    // ---------------------------------------------------------------------
    // Authorization URL + browser
    // ---------------------------------------------------------------------

    private String buildAuthorizationUrl(OAuthProvider provider, String redirectUri,
                                         String state, String codeChallenge) {
        StringBuilder url = new StringBuilder(provider.getAuthorizationEndpoint());
        url.append("?response_type=code");
        url.append("&client_id=").append(urlEncode(getClientId(provider)));
        url.append("&redirect_uri=").append(urlEncode(redirectUri));
        url.append("&scope=").append(urlEncode(String.join(" ", provider.getScopes())));
        url.append("&state=").append(urlEncode(state));
        url.append("&code_challenge=").append(urlEncode(codeChallenge));
        url.append("&code_challenge_method=").append(CODE_CHALLENGE_METHOD);
        if (provider == OAuthProvider.GOOGLE) {
            // Required for Google to return a refresh_token on the code exchange.
            url.append("&access_type=offline");
        }
        return url.toString();
    }

    private void openInBrowser(String url) throws OAuthAuthenticationException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_BROWSER_UNAVAILABLE,
                    "The system browser is not supported on this platform");
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_BROWSER_UNAVAILABLE,
                    "Failed to open the system browser", e);
        }
    }

    // ---------------------------------------------------------------------
    // Loopback redirect handling
    // ---------------------------------------------------------------------

    private void handleCallback(HttpExchange exchange, CompletableFuture<CallbackResult> future) {
        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            future.complete(new CallbackResult(params.get("code"), params.get("state"), params.get("error")));

            byte[] body = """
                    <html><body style="font-family:sans-serif;text-align:center;margin-top:4rem">
                    <h2>Login complete</h2>
                    <p>You can close this window and return to Evermail.</p>
                    </body></html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } catch (IOException e) {
            future.completeExceptionally(e);
        } finally {
            exchange.close();
        }
    }

    private CallbackResult waitForCallback(CompletableFuture<CallbackResult> future)
            throws OAuthAuthenticationException {
        try {
            return future.get(AppConstants.LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_TIMEOUT,
                    "Timed out waiting for the OAuth redirect");
        } catch (ExecutionException e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Failed to receive the OAuth redirect", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_CONNECTION_FAILED,
                    "OAuth login was interrupted", e);
        }
    }

    // ---------------------------------------------------------------------
    // Token endpoint (code exchange + refresh)
    // ---------------------------------------------------------------------

    private TokenData exchangeCodeForTokens(OAuthProvider provider, String code,
                                            String redirectUri, String codeVerifier)
            throws OAuthAuthenticationException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", redirectUri);
        params.put("client_id", getClientId(provider));
        if (provider.requiresClientSecret()) {
            params.put("client_secret", getClientSecret(provider));
        }
        params.put("code_verifier", codeVerifier);

        JsonObject json = postToTokenEndpoint(provider, params, ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED);
        return parseTokenResponse(json);
    }

    private JsonObject postToTokenEndpoint(OAuthProvider provider, Map<String, String> params,
                                           ErrorCode failureCode) throws OAuthAuthenticationException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(provider.getTokenEndpoint()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildFormBody(params)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OAuthAuthenticationException(failureCode,
                        "Token endpoint returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return gson.fromJson(response.body(), JsonObject.class);
        } catch (IOException e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Network error calling the token endpoint", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_CONNECTION_FAILED,
                    "Token endpoint call was interrupted", e);
        }
    }

    private TokenData parseTokenResponse(JsonObject json) throws OAuthAuthenticationException {
        if (!json.has("access_token")) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED,
                    "Token response is missing access_token: " + json);
        }
        String accessToken = json.get("access_token").getAsString();
        String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
        long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
        String idToken = json.has("id_token") ? json.get("id_token").getAsString() : null;
        return new TokenData(accessToken, refreshToken, expiresIn, idToken);
    }

    // ---------------------------------------------------------------------
    // User identity (from the id_token JWT — avoids a second userinfo call and
    // sidesteps Microsoft's one-resource-per-token restriction for Graph)
    // ---------------------------------------------------------------------

    private UserIdentity extractIdentity(String idToken) throws OAuthAuthenticationException {
        if (idToken == null || idToken.isBlank()) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_USERINFO_FAILED,
                    "Token response has no id_token; cannot resolve the user's identity. "
                            + "Ensure the 'openid' scope is requested.");
        }
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_USERINFO_FAILED, "Malformed id_token");
        }
        // The payload is trusted here because it arrived directly from the provider's
        // token endpoint over TLS — we are not verifying signatures for a third party.
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JsonObject claims = gson.fromJson(payload, JsonObject.class);

        String email = claims.has("email") ? claims.get("email").getAsString()
                : (claims.has("preferred_username") ? claims.get("preferred_username").getAsString() : null);
        if (email == null) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_USERINFO_FAILED,
                    "id_token does not contain an email claim");
        }
        String name = claims.has("name") ? claims.get("name").getAsString() : email;
        return new UserIdentity(email, name);
    }

    // ---------------------------------------------------------------------
    // PKCE + helpers
    // ---------------------------------------------------------------------

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String codeVerifier) throws OAuthAuthenticationException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_CONNECTION_FAILED,
                    "SHA-256 is not available for PKCE", e);
        }
    }

    private String generateState() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildFormBody(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String getClientId(OAuthProvider provider) {
        return provider == OAuthProvider.GOOGLE
                ? envConfig.getGoogleClientId()
                : envConfig.getMicrosoftClientId();
    }

    private String getClientSecret(OAuthProvider provider) {
        return provider == OAuthProvider.GOOGLE
                ? envConfig.getGoogleClientSecret()
                : envConfig.getMicrosoftClientSecret();
    }

    private AppProfile buildDefaultProfile() {
        AppProfile profile = new AppProfile();
        profile.setTheme(DEFAULT_THEME);
        profile.setSyncIntervalMinutes(DEFAULT_SYNC_INTERVAL_MINUTES);
        profile.setLanguage(DEFAULT_LANGUAGE);
        profile.setNotificationsEnabled(DEFAULT_NOTIFICATIONS_ENABLED);
        return profile;
    }

    private EmailAddress buildInternalAddress(String email) {
        EmailAddress address = new EmailAddress();
        address.setEmail(email);
        address.setInternal(true);
        return address;
    }

    // ---------------------------------------------------------------------
    // Local value carriers
    // ---------------------------------------------------------------------

    private record CallbackResult(String code, String state, String error) {}

    private record TokenData(String accessToken, String refreshToken, long expiresIn, String idToken) {}

    private record UserIdentity(String email, String displayName) {}
}