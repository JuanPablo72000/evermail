package com.juanpablo.evermail.util;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import java.io.*;
import java.util.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class HybridMimeTest {
    private Deadline budget() { return Deadline.after(AppConstants.CONTENT_BUDGET); }
    private MimeBodyPart text(String value, String type) throws Exception {
        MimeBodyPart part = new MimeBodyPart(); part.setContent(value, type + "; charset=UTF-8"); return part;
    }
    private MimeMessage message(MimeMultipart content) throws Exception {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        msg.setContent(content); msg.saveChanges(); return msg;
    }

    @Test void retainsAlternativesWithoutDuplicatingThemAndSkipsAttachments() throws Exception {
        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(text("Texto alternativo", "text/plain"));
        alternative.addBodyPart(text("<h2>Versión HTML</h2><table><tr><td>Dato</td></tr></table>", "text/html"));
        MimeBodyPart wrapper = new MimeBodyPart(); wrapper.setContent(alternative);
        MimeBodyPart attachment = text("SECRETO ADJUNTO", "text/html"); attachment.setFileName("data.html");
        MimeMultipart mixed = new MimeMultipart("mixed"); mixed.addBodyPart(wrapper); mixed.addBodyPart(attachment);
        var result = HybridMime.extract(message(mixed), budget());
        assertEquals("Texto alternativo", result.getPlainText());
        assertTrue(result.getHtml().contains("<table>"));
        assertFalse(result.getHtml().contains("Texto alternativo"));
        assertFalse(result.getHtml().contains("SECRETO"));
        assertFalse(result.toString().contains("alternativo"));
    }

    @Test void htmlOnlyHasReadableFallbackAndBlocksActiveContentAndNetwork() throws Exception {
        MimeMultipart mixed = new MimeMultipart();
        mixed.addBodyPart(text("<base href='https://tracker.invalid'><script>secret()</script><style>@import 'https://tracker.invalid';</style>"
                + "<form action='https://tracker.invalid'><input></form><svg onload='bad()'></svg><iframe src='file:///secret'></iframe>"
                + "<p onclick='bad()' style='color:#aabbcc;background:url(https://tracker.invalid);position:fixed'>Hola &amp; mundo</p>"
                + "<img src='https://tracker.invalid/pixel'><img src='file:///secret'><a href='javascript:bad()'>Mal</a>"
                + "<a href='https://example.com'>Bien</a>", "text/html"));
        var result = HybridMime.extract(message(mixed), budget());
        assertTrue(result.getPlainText().contains("Hola & mundo"));
        assertTrue(result.isBlockedRemoteImages());
        var doc = Jsoup.parse(result.getHtml());
        assertEquals(0, doc.select("script,style,iframe,svg,form,base,[onclick],[href],img[src]").size());
        assertFalse(result.getHtml().contains("tracker.invalid"));
        assertFalse(result.getHtml().contains("position"));
        assertTrue(result.getHtml().contains("color:#aabbcc"));
        assertEquals("https://example.com", doc.select("a[data-evermail-link]").attr("data-evermail-link"));
    }

    @Test void resolvesReferencedCidOnlyWithoutGeneralAttachmentDownloads() throws Exception {
        byte[] png;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
            png = output.toByteArray();
        }
        MimeBodyPart inline = new MimeBodyPart();
        inline.setDataHandler(new jakarta.activation.DataHandler(new jakarta.mail.util.ByteArrayDataSource(png, "image/png")));
        inline.setHeader("Content-ID", "<logo>"); inline.setDisposition(Part.INLINE); inline.setFileName("logo.png");
        MimeBodyPart unused = new MimeBodyPart() {
            @Override public InputStream getInputStream() { throw new AssertionError("Unreferenced image was downloaded"); }
        };
        unused.setHeader("Content-Type", "image/png"); unused.setHeader("Content-ID", "<unused>");
        MimeMultipart related = new MimeMultipart("related");
        related.addBodyPart(text("<p>Hola</p><img src='cid:logo' alt='Logo'><img src='cid:missing' alt='Ausente'>", "text/html"));
        related.addBodyPart(inline);
        // Save normal parts before adding the sentinel; saving could serialize its body.
        MimeMessage msg = message(related);
        related.addBodyPart(unused);
        var result = HybridMime.extract(msg, budget());
        assertTrue(result.getHtml().contains("data:image/png;base64,"));
        assertTrue(result.getHtml().contains("[Ausente]"));
        assertFalse(result.getHtml().contains("cid:"));
    }

    @Test void decodesDeclaredCharset() throws Exception {
        String raw = "MIME-Version: 1.0\r\nContent-Type: text/html; charset=ISO-8859-1\r\n\r\n<p>Español</p>";
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
        assertEquals("Español", HybridMime.extract(msg, budget()).getPlainText());
    }

    @Test void rejectsOversizedBodyAndHonorsAlreadyExpiredDeadline() throws Exception {
        MimeMultipart mixed = new MimeMultipart(); mixed.addBodyPart(text("a".repeat(AppConstants.MAX_BODY_BYTES + 1), "text/plain"));
        MimeMessage msg = message(mixed);
        assertThrows(MailFetchException.class, () -> HybridMime.extract(msg, budget()));
        Deadline expired = Deadline.after(Duration.ofNanos(1));
        assertEquals(ErrorCode.LOCAL_TIMEOUT, assertThrows(SessionException.class, () -> HybridMime.extract(msg, expired)).getErrorCode());
    }

    @Test void rejectsNonRasterDataAndUnsafeLinks() {
        assertNull(InlineImages.validateDataUri("data:image/svg+xml;base64,PHN2Zz4="));
        assertNull(InlineImages.validateDataUri("data:image/png;base64,PHN2Zz4="));
        assertFalse(HtmlMail.safeLink("file:///secret"));
        assertFalse(HtmlMail.safeLink("https://user:password@example.com"));
        assertTrue(HtmlMail.safeLink("https://example.com/path"));
    }
}
