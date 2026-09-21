package com.juanpablo.evermail.service;

import com.google.gson.*;
import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.model.Identity;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class OAuthClient implements OAuthGateway {
    @FunctionalInterface
    public interface Browser {
        void open(URI uri) throws Exception;
    }

    private final HttpClient http;
    private final Browser browser;
    private final Clock clock;
    private final OidcTokenValidator validator;
    private static final SecureRandom RANDOM = new SecureRandom();

    public OAuthClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
                uri -> Desktop.getDesktop().browse(uri), Clock.systemUTC());
    }

    public OAuthClient(HttpClient http, Browser browser, Clock clock) {
        this.http = http;
        this.browser = browser;
        this.clock = clock;
        validator = new OidcTokenValidator((provider, deadline) -> getJson(URI.create(provider == OAuthProvider.GOOGLE
                ? "https://www.googleapis.com/oauth2/v3/certs"
                : "https://login.microsoftonline.com/common/discovery/v2.0/keys"), deadline), clock);
    }

    @Override
    public AuthorizationResult authorize(ProviderConfig config, CancellationToken cancel) throws EvermailException {
        String verifier = random();
        String state = random();
        String nonce = random();
        HttpServer server = null;
        ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().factory());
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            String redirect = "http://127.0.0.1:" + server.getAddress().getPort() + "/callback";
            CompletableFuture<Map<String, String>> callback = new CompletableFuture<>();
            server.createContext("/callback", exchange -> {
                try {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                    boolean valid = exchange.getRequestMethod().equals("GET")
                            && exchange.getRequestURI().getPath().equals("/callback")
                            && state.equals(query.get("state"))
                            && (query.containsKey("code") || query.containsKey("error"));
                    byte[] body = (valid ? "Authorization received. Return to Evermail." : "Invalid authorization callback.")
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(valid ? 200 : 400, body.length);
                    exchange.getResponseBody().write(body);
                    if (valid) {
                        callback.complete(query);
                    }
                } catch (Exception ignored) {
                    // Malformed or unrelated requests must not consume the pending authorization.
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(executor);
            server.start();
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("response_type", "code");
            parameters.put("client_id", config.getClientId());
            parameters.put("redirect_uri", redirect);
            parameters.put("scope", String.join(" ", config.getProvider().getScopes()));
            parameters.put("state", state);
            parameters.put("nonce", nonce);
            parameters.put("code_challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))));
            parameters.put("code_challenge_method", "S256");
            parameters.put("prompt", config.getProvider() == OAuthProvider.GOOGLE ? "consent select_account" : "select_account");
            if (config.getProvider() == OAuthProvider.GOOGLE) {
                parameters.put("access_type", "offline");
            }
            try {
                browser.open(URI.create(config.getProvider().getAuthorizationEndpoint() + "?" + form(parameters)));
            } catch (Exception e) {
                throw new OAuthAuthenticationException(ErrorCode.OAUTH_BROWSER_UNAVAILABLE, "Cannot open authorization browser");
            }
            Deadline human = Deadline.after(AppConstants.OAUTH_AUTHORIZATION_TIMEOUT);
            Map<String, String> result;
            while (true) {
                cancel.check();
                try {
                    result = callback.get(Math.min(200, human.remainingMillis()), TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException ignored) {
                    try {
                        human.check();
                    } catch (SessionException e) {
                        throw new OAuthAuthenticationException(ErrorCode.OAUTH_TIMEOUT, "Authorization timed out");
                    }
                }
            }
            if (result.containsKey("error")) {
                throw new OAuthAuthenticationException(ErrorCode.OAUTH_PROVIDER_ERROR, "Authorization was not granted");
            }
            cancel.check();
            Map<String, String> tokenParameters = clientParameters(config);
            tokenParameters.put("grant_type", "authorization_code");
            tokenParameters.put("code", result.get("code"));
            tokenParameters.put("redirect_uri", redirect);
            tokenParameters.put("code_verifier", verifier);
            Deadline network = Deadline.after(Duration.ofSeconds(15));
            JsonObject tokens = post(config, tokenParameters, network);
            Identity identity = validator.validate(tokens.has("id_token") ? tokens.get("id_token").getAsString() : null,
                    config, nonce, network);
            return new AuthorizationResult(identity, credentials(tokens, null));
        } catch (EvermailException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthAuthenticationException(ErrorCode.CANCELLED, "Authorization cancelled");
        } catch (Exception e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_CONNECTION_FAILED, "Authorization failed");
        } finally {
            if (server != null) {
                server.stop(0);
            }
            executor.shutdownNow();
        }
    }

    @Override
    public OAuthCredentials refresh(ProviderConfig config, OAuthCredentials previous, Deadline deadline) throws EvermailException {
        Map<String, String> parameters = clientParameters(config);
        parameters.put("grant_type", "refresh_token");
        parameters.put("refresh_token", previous.getRefreshToken());
        return credentials(post(config, parameters, deadline), previous.getRefreshToken());
    }

    private Map<String, String> clientParameters(ProviderConfig config) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("client_id", config.getClientId());
        if (config.getProvider().requiresClientSecret()) {
            parameters.put("client_secret", config.getClientSecret());
        }
        return parameters;
    }

    private JsonObject post(ProviderConfig config, Map<String, String> parameters, Deadline deadline) throws EvermailException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.getProvider().getTokenEndpoint()))
                    .timeout(deadline.remaining()).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(parameters))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                boolean rejected = false;
                try {
                    JsonObject error = JsonParser.parseString(response.body()).getAsJsonObject();
                    rejected = error.has("error") && "invalid_grant".equals(error.get("error").getAsString());
                } catch (Exception ignored) {
                }
                throw new OAuthAuthenticationException(rejected ? ErrorCode.REAUTH_REQUIRED : ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED,
                        "Provider rejected the token request");
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (EvermailException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthAuthenticationException(ErrorCode.CANCELLED, "Token request interrupted");
        } catch (Exception e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_CONNECTION_FAILED, "Token request failed");
        }
    }

    private JsonObject getJson(URI uri, Deadline deadline) throws EvermailException {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(deadline.remaining()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException();
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthAuthenticationException(ErrorCode.CANCELLED, "Identity verification interrupted");
        } catch (Exception e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_CONNECTION_FAILED, "Cannot verify provider identity");
        }
    }

    private OAuthCredentials credentials(JsonObject json, String previousRefresh) throws OAuthAuthenticationException {
        try {
            String access = json.get("access_token").getAsString();
            String refresh = json.has("refresh_token") ? json.get("refresh_token").getAsString() : previousRefresh;
            long seconds = json.get("expires_in").getAsLong();
            if (access.isBlank() || seconds <= 0 || seconds > 86400 * 30) {
                throw new IllegalArgumentException();
            }
            return new OAuthCredentials(access, refresh, Instant.now(clock).plusSeconds(seconds));
        } catch (Exception e) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_TOKEN_INVALID, "Invalid token response");
        }
    }

    static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] pieces = pair.split("=", 2);
                if (pieces.length == 2) {
                    String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8);
                    if (result.putIfAbsent(key, URLDecoder.decode(pieces[1], StandardCharsets.UTF_8)) != null) {
                        throw new IllegalArgumentException("Duplicate callback parameter");
                    }
                }
            }
        }
        return result;
    }

    private static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }
}
