package com.juanpablo.evermail.service;

import com.google.gson.*;
import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.model.Identity;
import com.juanpablo.evermail.util.EmailValidator;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

public class OidcTokenValidator {
    @FunctionalInterface
    public interface KeySource {
        JsonObject fetch(OAuthProvider provider, Deadline deadline) throws EvermailException;
    }

    private final KeySource keys;
    private final Clock clock;

    public OidcTokenValidator(KeySource keys, Clock clock) {
        this.keys = keys;
        this.clock = clock;
    }

    public Identity validate(String token, ProviderConfig config, String nonce, Deadline deadline) throws EvermailException {
        try {
            if (token == null || token.length() > 65536) {
                throw new IllegalArgumentException();
            }
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException();
            }
            JsonObject header = json(parts[0]);
            JsonObject claims = json(parts[1]);
            if (!"RS256".equals(string(header, "alg"))) {
                throw new IllegalArgumentException();
            }
            String kid = string(header, "kid");
            JsonObject matching = null;
            for (JsonElement element : keys.fetch(config.getProvider(), deadline).getAsJsonArray("keys")) {
                JsonObject candidate = element.getAsJsonObject();
                if (kid.equals(string(candidate, "kid")) && "RSA".equals(string(candidate, "kty"))
                        && (!candidate.has("use") || "sig".equals(string(candidate, "use")))) {
                    matching = candidate;
                    break;
                }
            }
            if (matching == null) {
                throw new IllegalArgumentException();
            }
            var factory = KeyFactory.getInstance("RSA");
            var key = factory.generatePublic(new RSAPublicKeySpec(integer(matching, "n"), integer(matching, "e")));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new IllegalArgumentException();
            }
            String issuer = string(claims, "iss");
            if (config.getProvider() == OAuthProvider.GOOGLE) {
                if (!Set.of("https://accounts.google.com", "accounts.google.com").contains(issuer)) {
                    throw new IllegalArgumentException();
                }
                issuer = "https://accounts.google.com";
            } else {
                String tenant = string(claims, "tid");
                UUID.fromString(tenant);
                if (!issuer.equals("https://login.microsoftonline.com/" + tenant + "/v2.0")) {
                    throw new IllegalArgumentException();
                }
            }
            JsonElement audience = claims.get("aud");
            boolean audienceMatches = audience != null && (audience.isJsonArray()
                    ? audience.getAsJsonArray().asList().stream().anyMatch(a -> config.getClientId().equals(a.getAsString()))
                    : config.getClientId().equals(audience.getAsString()));
            if (!audienceMatches || (claims.has("azp") && !config.getClientId().equals(string(claims, "azp")))
                    || (audience.isJsonArray() && audience.getAsJsonArray().size() > 1 && !claims.has("azp"))) {
                throw new IllegalArgumentException();
            }
            long now = Instant.now(clock).getEpochSecond();
            if (!claims.has("exp") || claims.get("exp").getAsLong() <= now
                    || (claims.has("nbf") && claims.get("nbf").getAsLong() > now + 60)
                    || !Objects.equals(nonce, string(claims, "nonce"))) {
                throw new IllegalArgumentException();
            }
            String subject = string(claims, "sub");
            if (subject.isBlank()) {
                throw new IllegalArgumentException();
            }
            String email = claims.has("email") ? string(claims, "email") : string(claims, "preferred_username");
            if (config.getProvider() == OAuthProvider.GOOGLE
                    && (!claims.has("email_verified") || !claims.get("email_verified").getAsBoolean())) {
                throw new IllegalArgumentException();
            }
            email = EmailValidator.normalize(email);
            return new Identity(config.getProvider(), issuer + "|" + subject, email,
                    claims.has("name") ? string(claims, "name") : email);
        } catch (EvermailException e) {
            throw e;
        } catch (Exception e) {
            // Never attach the raw JWT or JSON response to a public error.
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_TOKEN_INVALID, "Identity token validation failed");
        }
    }

    private static JsonObject json(String encoded) {
        return JsonParser.parseString(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String string(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing claim");
        }
        return object.get(field).getAsString();
    }

    private static BigInteger integer(JsonObject object, String field) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(string(object, field)));
    }
}
