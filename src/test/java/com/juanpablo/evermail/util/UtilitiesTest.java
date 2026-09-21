package com.juanpablo.evermail.util;

import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UtilitiesTest {
    @Test
    void encryptionUsesRandomNonceAndAuthenticatedRecordContext() throws Exception {
        SecurityUtil security = new SecurityUtil();
        var key = security.generateKey();
        CryptoContext context = new CryptoContext(UUID.randomUUID(), UUID.randomUUID(), "body");
        String first = security.encrypt("private", key, context);
        String second = security.encrypt("private", key, context);
        assertNotEquals(first, second);
        assertEquals("private", security.decrypt(first, key, context));
        assertThrows(CryptoException.class, () -> security.decrypt(first, key, context.withRecordId(UUID.randomUUID())));
        assertThrows(CryptoException.class, () -> security.decrypt(first + "invalid", key, context));
        assertThrows(CryptoException.class, () -> security.decrypt("legacy", key, context));
    }

    @Test
    void nestedAlternativePrefersPlainTextAndIgnoresAttachments() throws Exception {
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("Readable", "UTF-8");
        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<b>Alternative</b>", "text/html");
        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(html);
        alternative.addBodyPart(plain);
        MimeBodyPart nested = new MimeBodyPart();
        nested.setContent(alternative);
        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setText("Attachment secret");
        attachment.setFileName("notes.txt");
        attachment.setDisposition(Part.ATTACHMENT);
        MimeMultipart mixed = new MimeMultipart();
        mixed.addBodyPart(nested);
        mixed.addBodyPart(attachment);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setContent(mixed);
        message.saveChanges();
        assertEquals("Readable", MimeUtil.extractReadableText(message));
    }

    @Test
    void htmlBecomesTextWithoutScriptsOrRemoteResources() throws Exception {
        String text = MimeUtil.htmlToText("<html><head><style>secret-css</style></head><body><script>secret-script</script><p>Hello &amp; world</p><img src='https://invalid.example/tracker'></body></html>");
        assertTrue(text.contains("Hello & world"));
        assertFalse(text.contains("secret-script"));
        assertFalse(text.contains("secret-css"));
        assertFalse(text.contains("<p>"));
    }

    @Test
    void addressNormalizationPreservesLocalPartAndRejectsInjection() throws Exception {
        assertEquals("User+tag@example.com", EmailValidator.normalize(" User+tag@EXAMPLE.COM "));
        assertThrows(ValidationException.class, () -> EmailValidator.normalize("a@example.com\r\nBcc: target@example.com"));
        assertThrows(ValidationException.class, () -> EmailValidator.normalize("a..b@example.com"));
        assertThrows(ValidationException.class, () -> EmailValidator.normalize("not-an-email"));
    }

    @Test
    void secretsDoNotAppearInGeneratedToString() {
        OAuthCredentials tokens = new OAuthCredentials("SECRET_ACCESS", "SECRET_REFRESH", java.time.Instant.now());
        assertFalse(tokens.toString().contains("SECRET"));
    }
}
