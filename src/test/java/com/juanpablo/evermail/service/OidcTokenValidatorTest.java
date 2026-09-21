package com.juanpablo.evermail.service;

import com.google.gson.*;
import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.OAuthAuthenticationException;
import com.juanpablo.evermail.model.OAuthProvider;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OidcTokenValidatorTest {
    private KeyPair pair;
    private OidcTokenValidator validator;
    private ProviderConfig config;
    private final Instant now = Instant.parse("2026-09-20T12:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        JsonObject key = new JsonObject();
        key.addProperty("kid", "test");
        key.addProperty("kty", "RSA");
        key.addProperty("n", encode(unsigned(publicKey.getModulus().toByteArray())));
        key.addProperty("e", encode(unsigned(publicKey.getPublicExponent().toByteArray())));
        JsonArray array = new JsonArray();
        array.add(key);
        JsonObject jwks = new JsonObject();
        jwks.add("keys", array);
        validator = new OidcTokenValidator((provider, deadline) -> jwks, Clock.fixed(now, ZoneOffset.UTC));
        config = new ProviderConfig(OAuthProvider.GOOGLE, "client", "secret");
    }

    private byte[] unsigned(byte[] bytes) {
        return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private JsonObject claims() {
        JsonObject claims = new JsonObject();
        claims.addProperty("iss", "https://accounts.google.com");
        claims.addProperty("aud", "client");
        claims.addProperty("sub", "subject");
        claims.addProperty("email", "user@example.com");
        claims.addProperty("email_verified", true);
        claims.addProperty("nonce", "nonce");
        claims.addProperty("exp", now.plusSeconds(300).getEpochSecond());
        return claims;
    }

    private String token(JsonObject claims) throws Exception {
        String header = encode("{\"alg\":\"RS256\",\"kid\":\"test\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encode(claims.toString().getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + encode(signer.sign());
    }

    @Test
    void validSignedIdentityIsAccepted() throws Exception {
        assertEquals("https://accounts.google.com|subject",
                validator.validate(token(claims()), config, "nonce", Deadline.after(Duration.ofSeconds(5))).getSubject());
    }

    @Test
    void wrongAudienceIsRejectedEvenWithValidSignature() throws Exception {
        JsonObject claims = claims();
        claims.addProperty("aud", "other-client");
        String token = token(claims);
        assertThrows(OAuthAuthenticationException.class, () -> validator.validate(token, config, "nonce", Deadline.after(Duration.ofSeconds(5))));
    }

    @Test
    void expiredTokenAndWrongNonceAreRejected() throws Exception {
        JsonObject expired = claims();
        expired.addProperty("exp", now.minusSeconds(1).getEpochSecond());
        String token = token(expired);
        assertThrows(OAuthAuthenticationException.class, () -> validator.validate(token, config, "nonce", Deadline.after(Duration.ofSeconds(5))));
        String valid = token(claims());
        assertThrows(OAuthAuthenticationException.class, () -> validator.validate(valid, config, "wrong", Deadline.after(Duration.ofSeconds(5))));
    }

    @Test
    void alteredPayloadCannotPassSignatureValidation() throws Exception {
        String[] pieces = token(claims()).split("\\.");
        JsonObject changed = claims();
        changed.addProperty("email", "attacker@example.com");
        String tampered = pieces[0] + "." + encode(changed.toString().getBytes(StandardCharsets.UTF_8)) + "." + pieces[2];
        assertThrows(OAuthAuthenticationException.class, () -> validator.validate(tampered, config, "nonce", Deadline.after(Duration.ofSeconds(5))));
    }
}
