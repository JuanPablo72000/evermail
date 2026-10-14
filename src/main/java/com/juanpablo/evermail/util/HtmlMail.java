package com.juanpablo.evermail.util;

import com.juanpablo.evermail.config.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.safety.*;
import java.net.URI;
import java.util.*;

/** Allowlist sanitizer. Only explicit image resolvers can access resources; the viewer cannot. */
public final class HtmlMail {
    public record SafeBody(String html, boolean blockedRemoteImages) { }
    @FunctionalInterface public interface ImageResolver { String resolve(String cid) throws Exception; }
    private HtmlMail() { }

    public static SafeBody sanitize(String raw, ImageResolver resolver, Deadline deadline) throws Exception {
        return sanitize(raw, resolver, null, false, deadline);
    }

    public static SafeBody sanitize(String raw, ImageResolver resolver, ImageResolver remote,
                                    boolean preserveRemote, Deadline deadline) throws Exception {
        deadline.check();
        if (raw.length() > AppConstants.MAX_BODY_BYTES * 4) throw new IllegalArgumentException("HTML exceeds limit");
        Document source = Jsoup.parse(raw);
        deadline.check();
        if (source.getAllElements().size() > AppConstants.MAX_HTML_NODES) throw new IllegalArgumentException("HTML exceeds node limit");
        Resources resources = new Resources(resolver, remote, preserveRemote);
        MailCss css = new MailCss(resources::background, deadline);
        StringBuilder stylesheet = new StringBuilder();
        for (Element style : source.select("style")) {
            // A print-only sheet is not a screen stylesheet.
            String media = style.attr("media").strip().toLowerCase(Locale.ROOT);
            if (media.isEmpty() || media.equals("all") || media.equals("screen")) stylesheet.append(css.stylesheet(style.data()));
            else stylesheet.append(css.stylesheet("@media " + media + "{" + style.data() + "}"));
        }
        source.select("script,style,iframe,object,embed,form,input,button,textarea,select,svg,math,link,meta,base,template,noscript").remove();
        // Preserve body formatting in a wrapper; avoid accumulating wrappers on repeated cleaning.
        if (!(source.body().childrenSize() == 1 && source.body().child(0).hasClass(MailCss.ROOT))) {
            Element wrapper = new Element("div");
            for (String attr : List.of("style", "class", "id", "bgcolor", "background", "dir", "align"))
                if (source.body().hasAttr(attr)) wrapper.attr(attr, source.body().attr(attr));
            wrapper.addClass(MailCss.ROOT);
            wrapper.insertChildren(0, source.body().childNodesCopy());
            source.body().empty().appendChild(wrapper);
        }
        Safelist allowed = Safelist.relaxed().addTags("div", "span", "font", "hr", "h1", "h2", "h3", "h4", "h5", "h6", "center", "colgroup", "col")
                .addAttributes(":all", "style", "align", "bgcolor", "class", "id", "dir", "background")
                .addAttributes("table", "width", "height", "border", "cellpadding", "cellspacing")
                .addAttributes("td", "width", "height", "valign").addAttributes("th", "width", "height", "valign")
                .addAttributes("tr", "valign").addAttributes("col", "width", "span")
                .addAttributes("img", "width", "height", "border")
                .addAttributes("font", "color", "face", "size")
                .removeAttributes("a", "href").removeAttributes("img", "src");
        // Capture only known URL types ourselves. No sender data-* attributes survive cleaning.
        Map<String, String> imageSources = new HashMap<>(), links = new HashMap<>();
        int index = 0;
        for (Element element : source.select("img,a")) {
            String id = "em-" + index++;
            element.attr("data-evermail-key", id);
            if (element.normalName().equals("img")) imageSources.put(id, element.hasAttr("src")
                    ? element.attr("src") : element.attr("data-evermail-image"));
            else links.put(id, element.hasAttr("href") ? element.attr("href") : element.attr("data-evermail-link"));
        }
        allowed.addAttributes("img", "data-evermail-key").addAttributes("a", "data-evermail-key");
        Document cleaned = new Cleaner(allowed).clean(source);
        for (Element el : cleaned.body().getAllElements()) {
            deadline.check();
            for (String dimension : List.of("width", "height", "colspan", "rowspan", "span", "size", "border", "cellpadding", "cellspacing")) {
                if (el.hasAttr(dimension) && !el.attr(dimension).matches("[0-9]{1,4}%?")) el.removeAttr(dimension);
            }
            for (String attr : List.of("id", "class")) {
                if (el.hasAttr(attr) && (el.attr(attr).length() > 512 || !el.attr(attr).matches("[a-zA-Z0-9_ \\-]+"))) el.removeAttr(attr);
            }
            for (String attr : List.of("align", "valign", "dir"))
                if (el.hasAttr(attr) && !el.attr(attr).matches("(?i)left|right|center|justify|top|middle|bottom|baseline|ltr|rtl|auto")) el.removeAttr(attr);
            String background = null;
            if (el.hasAttr("background")) { background = resources.background(el.attr("background")); el.removeAttr("background"); }
            String styles = css.inline(el.attr("style"));
            if (background != null) styles = "background-image:url(\"" + background.replace("\"", "%22") + "\");" + styles;
            if (styles.isEmpty()) el.removeAttr("style"); else el.attr("style", styles);
            if (el.normalName().equals("a")) {
                String href = links.get(el.attr("data-evermail-key"));
                if (safeLink(href)) el.attr("data-evermail-link", href);
                el.removeAttr("data-evermail-key");
            }
            if (el.normalName().equals("img")) {
                String src = imageSources.getOrDefault(el.attr("data-evermail-key"), "").strip();
                el.removeAttr("data-evermail-key");
                if (preserveRemote && safeImageUrl(src)) {
                    el.attr("data-evermail-image", src); resources.blocked = true; continue;
                }
                String data = resources.image(src);
                if (data != null) {
                    el.attr("src", data);
                } else {
                    el.replaceWith(new TextNode("[" + (el.attr("alt").isBlank() ? "Imagen no disponible" : el.attr("alt")) + "]"));
                }
            }
        }
        deadline.check();
        if (!stylesheet.isEmpty()) cleaned.body().prependChild(new Element("style").appendChild(new DataNode(stylesheet.toString())));
        cleaned.outputSettings().prettyPrint(false);
        return new SafeBody(cleaned.body().html(), resources.blocked);
    }

    /** Shared byte budget and URL deduplication for CSS backgrounds, CID and img elements. */
    private static final class Resources {
        private final ImageResolver cid, remote;
        private final boolean preserve;
        private final Map<String, String> resolved = new HashMap<>();
        private int chars;
        private boolean blocked;
        Resources(ImageResolver cid, ImageResolver remote, boolean preserve) { this.cid = cid; this.remote = remote; this.preserve = preserve; }
        String background(String src) throws Exception {
            if (src.startsWith("evermail-remote:")) src = src.substring("evermail-remote:".length());
            src = src.strip();
            if (preserve && safeImageUrl(src)) { blocked = true; return "evermail-remote:" + src; }
            return image(src);
        }
        String image(String src) throws Exception {
            boolean external = safeImageUrl(src);
            String data = null;
            if (chars < AppConstants.MAX_INLINE_TOTAL_BYTES * 4 / 3) {
                if (!resolved.containsKey(src)) {
                    if (src.regionMatches(true, 0, "cid:", 0, 4)) data = cid.resolve(src.substring(4));
                    else if (src.startsWith("data:")) data = src;
                    else if (external && remote != null) data = remote.resolve(src);
                    resolved.put(src, InlineImages.validateDataUri(data));
                }
                data = resolved.get(src);
            }
            if (data == null || chars + data.length() > AppConstants.MAX_INLINE_TOTAL_BYTES * 4 / 3) {
                if (external || (!src.isEmpty() && !src.startsWith("cid:") && !src.startsWith("data:"))) blocked = true;
                return null;
            }
            chars += data.length();
            return data;
        }
    }

    public static boolean safeLink(String value) {
        if (value == null || value.length() > 4096) return false;
        try {
            URI uri = URI.create(value);
            return Set.of("https", "http").contains(Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }

    public static boolean safeImageUrl(String value) {
        if (!safeLink(value)) return false;
        URI uri = URI.create(value);
        return "https".equalsIgnoreCase(uri.getScheme()) && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    public static String document(String safeHtml) {
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<style>body{font-family:Arial,sans-serif;font-size:16px;margin:0;color:#222;background:white}"
                + ".evermail-mail-root{overflow-wrap:break-word}"
                + "img{max-width:100%;height:auto}table{max-width:100%}"
                + "a{color:#162adf;text-decoration:underline}a[data-evermail-link]{cursor:pointer}"
                + "@media(max-width:600px){table{max-width:100%!important}img{max-width:100%!important;height:auto!important}}"
                + "</style>"
                + "</head><body>" + safeHtml + "</body></html>";
    }
}
