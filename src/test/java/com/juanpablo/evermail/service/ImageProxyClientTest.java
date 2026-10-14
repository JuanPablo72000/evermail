package com.juanpablo.evermail.service;

import com.google.gson.JsonParser;
import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.util.HtmlMail;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageProxyClientTest {
    private Deadline budget() { return Deadline.after(Duration.ofSeconds(5)); }
    private byte[] png() throws Exception {
        var output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, 1), "png", output);
        return output.toByteArray();
    }

    @Test void postsOnlyToProxyAndRendersValidatedDataWithoutKeyOrRemoteSources() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        byte[] image = png();
        server.createContext("/image", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("test-key", exchange.getRequestHeaders().getFirst("x-functions-key"));
            String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("https://origin.invalid/picture", JsonParser.parseString(body).getAsJsonObject().get("url").getAsString());
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, image.length);
            exchange.getResponseBody().write(image); exchange.close();
        });
        server.start();
        try {
            var client = new ImageProxyClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/image"), "test-key");
            var stored = HtmlMail.sanitize("<p>Hello</p><img src='https://origin.invalid/picture'><img src='https://origin.invalid/picture'>",
                    cid -> null, null, true, budget());
            assertFalse(stored.html().contains(" src="));
            var content = new MailContent(UUID.randomUUID(), "Hello", stored.html(), true, false, List.of());
            var service = new MailPresentationService(client);
            var early = service.prepareWithoutImages(content, budget());
            assertEquals(0, calls.get());
            assertTrue(early.getDocument().contains("Hello"));
            assertFalse(early.getDocument().contains("origin.invalid"));
            var result = service.prepare(content, budget());
            assertEquals(1, calls.get());
            assertTrue(result.getDocument().contains("data:image/png;base64,"));
            assertFalse(result.getDocument().contains("origin.invalid"));
            assertFalse(result.getDocument().contains("test-key"));
            assertFalse(result.getContent().isBlockedRemoteImages());
        } finally { server.stop(0); }
    }

    @Test void downloadsThreeImagesConcurrentlyAndDeduplicatesBeforeRendering() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(3);
        server.setExecutor(executor);
        var entered = new java.util.concurrent.CountDownLatch(3);
        var calls = new AtomicInteger();
        var overlapped = new java.util.concurrent.atomic.AtomicBoolean(true);
        byte[] image = png();
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); entered.countDown();
            try {
                if (!entered.await(3, java.util.concurrent.TimeUnit.SECONDS)) overlapped.set(false);
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, image.length);
                exchange.getResponseBody().write(image);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new ImageProxyClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test");
            String html = "<p>Readable</p><img src='https://origin.invalid/1'><img src='https://origin.invalid/2'>"
                    + "<img src='https://origin.invalid/3'><img src='https://origin.invalid/1'>";
            var content = new MailContent(UUID.randomUUID(), "Readable", html, true, false, List.of());
            var service = new MailPresentationService(client);
            assertTrue(service.prepareWithoutImages(content, budget()).getDocument().contains("Readable"));
            assertEquals(0, calls.get());
            var finalBody = service.prepare(content, Deadline.after(Duration.ofSeconds(10)));
            assertTrue(overlapped.get(), "Requests must overlap, not wait serially");
            assertEquals(3, calls.get());
            assertEquals(4, org.jsoup.Jsoup.parse(finalBody.getDocument()).select("img[src^=data:image/]").size());
            assertFalse(finalBody.getContent().isBlockedRemoteImages());
        } finally { server.stop(0); executor.shutdownNow(); }
    }

    @Test void rejectsErrorsRedirectsNonImagesAndOversizedBodies() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            byte[] body = path.equals("/large") ? new byte[AppConstants.MAX_INLINE_IMAGE_BYTES + 1] : "not an image".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Location", "https://origin.invalid/redirect");
            exchange.sendResponseHeaders(path.equals("/redirect") ? 302 : path.equals("/error") ? 401 : 200, body.length);
            try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
        });
        server.start();
        try {
            for (String path : List.of("/large", "/redirect", "/error", "/invalid")) {
                var client = new ImageProxyClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path), "test-key");
                assertNull(client.download("https://origin.invalid/picture", budget()));
            }
            assertEquals(4, calls.get());
        } finally { server.stop(0); }
    }

    @Test void missingConfigurationAndUnsafeUrlsKeepReadableFallback() throws Exception {
        var client = new ImageProxyClient(new EnvConfig(name -> null));
        assertFalse(client.configured());
        assertFalse(new ImageProxyClient(new EnvConfig(name -> name.equals("IMAGE_PROXY_URL") ? "http://insecure.invalid" : "key")).configured());
        var content = new MailContent(UUID.randomUUID(), "Hello", "<p>Hello</p><img src='https://origin.invalid/picture'>", true, false, List.of());
        var result = new MailPresentationService(client).prepare(content, budget());
        assertTrue(result.getDocument().contains("Hello"));
        assertTrue(result.getContent().isBlockedRemoteImages());
        assertFalse(result.getDocument().contains("origin.invalid"));
        assertNull(client.download("file:///secret", budget()));
        assertNull(client.download("https://user:password@origin.invalid/image", budget()));
    }

    @Test void boundsTimeEvenWhenResponseBodyStalls() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, 100);
            try { Thread.sleep(1200); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new ImageProxyClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-key");
            assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                    assertNull(client.download("https://origin.invalid/image", Deadline.after(Duration.ofMillis(800)))));
        } finally { server.stop(0); }
    }
}
