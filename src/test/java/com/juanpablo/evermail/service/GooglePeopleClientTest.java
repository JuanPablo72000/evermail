package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.model.AvatarPhoto;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GooglePeopleClientTest {
    static byte[] png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
    private static GooglePeopleClient.Response json(int status, String value) {
        return new GooglePeopleClient.Response(status, value.getBytes(StandardCharsets.UTF_8));
    }
    private static Deadline deadline() { return Deadline.after(Duration.ofSeconds(5)); }
    private static String person(String email, String url) {
        return "{\"emailAddresses\":[{\"value\":\"" + email + "\"}],\"photos\":[{\"url\":\"" + url + "\"}]}";
    }

    @Test void paginatesBothSourcesAndMatchesExactEmailWithoutSendingTokenToPhotos() throws Exception {
        List<String> calls = new ArrayList<>();
        byte[] image = png();
        var client = new GooglePeopleClient((uri, token, maximum, d) -> {
            calls.add(uri.toString());
            if (uri.getHost().equals("people.googleapis.com")) {
                assertEquals("secret", token);
                assertFalse(uri.toString().contains("secret"));
                if (uri.getPath().endsWith("connections")) return json(200, "{\"connections\":["
                        + person("alice+tag@example.com", "https://lh3.googleusercontent.com/wrong") + "]}");
                if (uri.getQuery().contains("pageToken=")) return json(200, "{\"otherContacts\":["
                        + person("Alice@example.com", "https://lh3.googleusercontent.com/right") + "]}");
                return json(200, "{\"otherContacts\":[],\"nextPageToken\":\"next / page\"}");
            }
            assertNull(token);
            assertEquals("/right", uri.getPath());
            return new GooglePeopleClient.Response(200, image);
        }, Clock.systemUTC());
        AvatarPhoto result = client.lookup("secret", " alice@example.com ", deadline());
        assertEquals(AvatarPhoto.Status.AVAILABLE, result.status());
        assertEquals(96, ImageIO.read(new ByteArrayInputStream(result.png())).getWidth());
        assertEquals(4, calls.size());
        client.lookup("secret", "unknown@example.com", deadline());
        assertEquals(4, calls.size(), "Directory misses do not repeat API requests");
        client.reset();
        client.lookup("secret", "alice@example.com", deadline());
        assertEquals(8, calls.size());
    }

    @Test void missingPermissionInOneSourceDoesNotHidePhotoFromTheOther() throws Exception {
        byte[] image = png();
        var client = new GooglePeopleClient((uri, token, maximum, d) -> {
            if (uri.getPath().endsWith("connections")) return json(403, "ACCESS_TOKEN_SCOPE_INSUFFICIENT");
            if (token != null) return json(200, "{\"otherContacts\":["
                    + person("alice@example.com", "https://lh3.googleusercontent.com/a") + "]}");
            return new GooglePeopleClient.Response(200, image);
        }, Clock.systemUTC());
        assertEquals(AvatarPhoto.Status.AVAILABLE, client.lookup("secret", "alice@example.com", deadline()).status());
        assertEquals(AvatarPhoto.Status.PERMISSION_REQUIRED, client.lookup("secret", "bob@example.com", deadline()).status());
    }

    @Test void rateLimitAndDisabledApiAreNotReportedAsMissingContactOrConsent() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var client = new GooglePeopleClient((uri, token, maximum, d) -> {
            calls.incrementAndGet();
            return uri.getPath().endsWith("connections") ? json(429, "quota") : json(403, "SERVICE_DISABLED");
        }, Clock.systemUTC());
        assertEquals(AvatarPhoto.Status.UNAVAILABLE, client.lookup("secret", "a@example.com", deadline()).status());
        client.lookup("secret", "b@example.com", deadline());
        assertEquals(2, calls.get());
    }

    @Test void ignoresDefaultPhotosAndUntrustedHosts() throws Exception {
        for (String url : List.of("http://lh3.googleusercontent.com/a", "https://googleusercontent.com.evil.test/a",
                "https://localhost/a", "https://lh3.googleusercontent.com:8443/a",
                "https://user@lh3.googleusercontent.com/a", "file:///a", "data:image/png;base64,abcd"))
            assertNull(GooglePeopleClient.photoUri(url));
        assertNotNull(GooglePeopleClient.photoUri("https://lh3.googleusercontent.com/a"));
        var client = new GooglePeopleClient((uri, token, maximum, d) -> {
            assertNotNull(token, "Must never download these photo URLs");
            return json(200, "{\"connections\":[" + person("a@example.com", "https://evil.test/a") + ","
                    + person("a@example.com", "https://lh3.googleusercontent.com/default").replace("\"url\":", "\"default\":true,\"url\":") + "]}");
        }, Clock.systemUTC());
        assertEquals(AvatarPhoto.Status.ABSENT, client.lookup("secret", "a@example.com", deadline()).status());
    }

    @Test void validatesRasterAndSizeAndProducesSmallPng() throws Exception {
        assertNull(GooglePeopleClient.thumbnail("<svg/>".getBytes(StandardCharsets.UTF_8)));
        assertNull(GooglePeopleClient.thumbnail(new byte[1024 * 1024 + 1]));
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2049, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        assertNull(GooglePeopleClient.thumbnail(output.toByteArray()));
        byte[] thumbnail = GooglePeopleClient.thumbnail(png());
        assertNotNull(thumbnail);
        assertTrue(thumbnail.length <= 65_536);
        assertEquals(96, ImageIO.read(new ByteArrayInputStream(thumbnail)).getHeight());
    }

    @Test void repeatedPageTokensStopAtThePageLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var client = new GooglePeopleClient((uri, token, maximum, d) -> {
            calls.incrementAndGet(); return json(200, "{\"nextPageToken\":\"loop\"}");
        }, Clock.systemUTC());
        assertEquals(AvatarPhoto.Status.UNAVAILABLE, client.lookup("secret", "a@example.com", deadline()).status());
        assertEquals(20, calls.get());
    }

    @Test void diagnosticsReportCountsWithoutContactDataAndDifferentiateNoMatchesFromPermissionFailures() throws Exception {
        var client = new GooglePeopleClient((uri, token, maximum, d) -> {
            if (uri.getPath().endsWith("connections")) return json(200, "{\"connections\":["
                    + person("private@example.com", "https://lh3.googleusercontent.com/private-photo") + "]}");
            return json(403, "ACCESS_TOKEN_SCOPE_INSUFFICIENT");
        }, Clock.systemUTC());
        var reports = client.diagnose("secret", deadline());
        assertEquals(200, reports.getFirst().httpStatus());
        assertEquals(1, reports.getFirst().people());
        assertEquals(1, reports.getFirst().addressesWithPhotos());
        assertEquals(AvatarPhoto.Status.PERMISSION_REQUIRED, reports.getLast().status());
        assertFalse(reports.toString().contains("private"));
        assertFalse(reports.toString().contains("secret"));
        assertFalse(reports.toString().contains("https://"));
    }
}
