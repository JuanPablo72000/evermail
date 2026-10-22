package com.juanpablo.evermail.util;

import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.MailFetchException;
import com.juanpablo.evermail.model.Recipient;
import com.juanpablo.evermail.model.RecipientType;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTML;
import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.text.MutableAttributeSet;
import java.io.StringReader;
import java.util.*;

public final class MimeUtil {
    private MimeUtil() {
    }

    public static String extractReadableText(Part message) throws MailFetchException {
        try {
            return extract(message, 0);
        } catch (Exception e) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Cannot decode message body", e);
        }
    }

    private static String extract(Part part, int depth) throws Exception {
        if (depth > 40) {
            throw new IllegalArgumentException("MIME nesting limit exceeded");
        }
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
            return "";
        }
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("text/html")) {
            return htmlToText((String) part.getContent());
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            boolean alternative = part.isMimeType("multipart/alternative");
            if (alternative) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    Part child = multipart.getBodyPart(i);
                    if (child.isMimeType("text/plain") && child.getFileName() == null
                            && !Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition())) {
                        return extract(child, depth + 1);
                    }
                }
            }
            List<String> pieces = new ArrayList<>();
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = extract(multipart.getBodyPart(i), depth + 1);
                if (!text.isBlank()) {
                    if (alternative) {
                        return text;
                    }
                    pieces.add(text);
                }
            }
            return String.join("\n", pieces);
        }
        return "";
    }

    public static String htmlToText(String html) throws Exception {
        StringBuilder output = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            private int suppressed;

            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
                    suppressed++;
                } else if (tag.isBlock() && !output.isEmpty()) {
                    output.append('\n');
                }
            }

            @Override
            public void handleEndTag(HTML.Tag tag, int pos) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
                    suppressed = Math.max(0, suppressed - 1);
                }
            }

            @Override
            public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                if (tag == HTML.Tag.BR) {
                    output.append('\n');
                }
            }

            @Override
            public void handleText(char[] data, int pos) {
                if (suppressed == 0) {
                    output.append(data);
                }
            }
        }, true);
        return output.toString().strip();
    }

    public static List<Recipient> extractRecipients(Message message) throws MailFetchException {
        try {
            var found = new LinkedHashMap<String, Recipient>();
            Message.RecipientType[] types = {Message.RecipientType.TO, Message.RecipientType.CC, Message.RecipientType.BCC};
            RecipientType[] roles = {RecipientType.TO, RecipientType.CC, RecipientType.BCC};
            for (int i = 0; i < types.length; i++) {
                Address[] addresses = message.getRecipients(types[i]);
                if (addresses == null) {
                    continue;
                }
                for (Address raw : addresses) {
                    if (raw instanceof InternetAddress address) {
                        try {
                            String normalized = EmailValidator.normalize(address.getAddress());
                            found.putIfAbsent(normalized, new Recipient(normalized, normalized, address.getPersonal(), roles[i]));
                        } catch (Exception ignored) {
                            // A malformed remote recipient must not hide an otherwise readable message.
                        }
                    }
                }
            }
            return List.copyOf(found.values());
        } catch (MessagingException e) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Cannot decode recipients", e);
        }
    }
}
