package com.juanpablo.evermail.util;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.RemoteMailContent;
import jakarta.mail.*;
import jakarta.mail.internet.ContentType;
import org.jsoup.nodes.Entities;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded MIME traversal; reads body alternatives and referenced inline rasters only. */
public final class HybridMime {
    private record Body(String plain, String html) { }
    private final Deadline deadline;
    private final Map<String, Part> images = new HashMap<>();
    private int parts, textBytes, imageBytes;
    private HybridMime(Deadline deadline) { this.deadline = deadline; }

    public static RemoteMailContent extract(Part message, Deadline deadline) throws EvermailException {
        try {
            HybridMime parser = new HybridMime(deadline);
            Body body = parser.walk(message, 0);
            String plain = body.plain();
            if (plain == null) plain = body.html() == null ? "" : MimeUtil.htmlToText(body.html());
            HtmlMail.SafeBody safe = body.html() == null ? null : HtmlMail.sanitize(body.html(), parser::image, deadline);
            deadline.check();
            return new RemoteMailContent(plain, safe == null ? null : safe.html(), safe != null && safe.blockedRemoteImages(),
                    message instanceof Message mail ? MimeUtil.extractRecipients(mail) : List.of());
        } catch (EvermailException e) { throw e; }
        catch (Exception e) { throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Cannot decode message body", e); }
    }

    private Body walk(Part part, int depth) throws Exception {
        deadline.check();
        if (++parts > AppConstants.MAX_MIME_PARTS || depth > 30) throw new IOException("MIME structure exceeds limit");
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) return new Body(null, null);
        String[] cid = part.getHeader("Content-ID");
        if (part.isMimeType("image/*") && cid != null && cid.length > 0) {
            images.putIfAbsent(normalizeCid(cid[0]), part);
            return new Body(null, null);
        }
        if (part.getFileName() != null) return new Body(null, null);
        if (part.isMimeType("text/plain")) return new Body(text(part), null);
        if (part.isMimeType("text/html")) return new Body(null, text(part));
        if (!part.isMimeType("multipart/*")) return new Body(null, null);
        Multipart multi = (Multipart) part.getContent();
        if (multi.getCount() > AppConstants.MAX_MIME_PARTS) throw new IOException("Too many MIME parts");
        if (part.isMimeType("multipart/related")) {
            if (multi.getCount() == 0) return new Body(null, null);
            String start = new ContentType(part.getContentType()).getParameter("start");
            int root = 0;
            for (int i = 0; i < multi.getCount(); i++) {
                String[] id = multi.getBodyPart(i).getHeader("Content-ID");
                if (start != null && id != null && normalizeCid(start).equals(normalizeCid(id[0]))) root = i;
            }
            for (int i = 0; i < multi.getCount(); i++) if (i != root) collectImages(multi.getBodyPart(i), depth + 1);
            return walk(multi.getBodyPart(root), depth + 1);
        }
        boolean alternative = part.isMimeType("multipart/alternative");
        String plain = null, html = null;
        List<Body> pieces = new ArrayList<>();
        for (int i = 0; i < multi.getCount(); i++) {
            Body next = walk(multi.getBodyPart(i), depth + 1);
            if (alternative) {
                if (next.plain() != null) plain = next.plain();
                if (next.html() != null) html = next.html();
            } else if (next.plain() != null || next.html() != null) pieces.add(next);
        }
        if (alternative) return new Body(plain, html);
        StringBuilder plainParts = new StringBuilder(), htmlParts = new StringBuilder();
        boolean hasHtml = pieces.stream().anyMatch(p -> p.html() != null);
        for (Body piece : pieces) {
            String text = piece.plain() != null ? piece.plain() : MimeUtil.htmlToText(piece.html());
            if (!plainParts.isEmpty()) plainParts.append('\n');
            plainParts.append(text);
            if (hasHtml) htmlParts.append(piece.html() != null ? piece.html() : "<pre>" + Entities.escape(text) + "</pre>");
        }
        return new Body(pieces.isEmpty() ? null : plainParts.toString(), hasHtml ? htmlParts.toString() : null);
    }

    private String text(Part part) throws Exception {
        int remaining = AppConstants.MAX_BODY_BYTES - textBytes;
        byte[] bytes = read(part, remaining);
        textBytes += bytes.length;
        String charset = new ContentType(part.getContentType()).getParameter("charset");
        Charset encoding;
        try { encoding = charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset); }
        catch (IllegalArgumentException e) { encoding = StandardCharsets.UTF_8; }
        return new String(bytes, encoding);
    }

    private void collectImages(Part part, int depth) throws Exception {
        deadline.check();
        if (++parts > AppConstants.MAX_MIME_PARTS || depth > 30) throw new IOException("MIME structure exceeds limit");
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) return;
        String[] ids = part.getHeader("Content-ID");
        if (part.isMimeType("image/*") && ids != null && ids.length > 0) images.putIfAbsent(normalizeCid(ids[0]), part);
        else if (part.isMimeType("multipart/*")) {
            Multipart nested = (Multipart) part.getContent();
            if (nested.getCount() > AppConstants.MAX_MIME_PARTS) throw new IOException("Too many MIME parts");
            for (int i = 0; i < nested.getCount(); i++) collectImages(nested.getBodyPart(i), depth + 1);
        }
    }

    private byte[] read(Part part, int limit) throws Exception {
        deadline.check();
        if (limit < 0 || part.getSize() > limit) throw new IOException("MIME part exceeds size limit");
        try (InputStream input = part.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) {
                deadline.check();
                if (out.size() + size > limit) throw new IOException("Decoded MIME part exceeds size limit");
                out.write(buffer, 0, size);
            }
            deadline.check();
            return out.toByteArray();
        }
    }

    private String image(String cid) throws Exception {
        Part part = images.get(normalizeCid(cid));
        if (part == null) return null;
        int limit = Math.min(AppConstants.MAX_INLINE_IMAGE_BYTES, AppConstants.MAX_INLINE_TOTAL_BYTES - imageBytes);
        if (limit <= 0 || part.getSize() > limit) return null;
        try {
            byte[] bytes = read(part, limit);
            imageBytes += bytes.length;
            return InlineImages.dataUri(bytes);
        } catch (IOException e) { deadline.check(); return null; }
    }

    private static String normalizeCid(String cid) {
        String clean = cid.strip();
        return clean.startsWith("<") && clean.endsWith(">") ? clean.substring(1, clean.length() - 1) : clean;
    }
}
