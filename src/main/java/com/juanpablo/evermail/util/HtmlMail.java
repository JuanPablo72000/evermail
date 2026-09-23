package com.juanpablo.evermail.util;

import com.juanpablo.evermail.config.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.safety.*;
import java.net.URI;
import java.util.*;

/** Offline allowlist sanitizer. Sender markup never controls network access or navigation. */
public final class HtmlMail {
    public record SafeBody(String html, boolean blockedRemoteImages) { }
    @FunctionalInterface public interface ImageResolver { String resolve(String cid) throws Exception; }
    private static final Set<String> CSS = Set.of("color", "background-color", "font-size", "font-family", "font-weight",
            "font-style", "text-decoration", "text-align", "vertical-align", "line-height", "letter-spacing",
            "width", "height", "max-width", "padding", "padding-top", "padding-bottom", "padding-left", "padding-right",
            "margin", "margin-top", "margin-bottom", "margin-left", "margin-right", "border", "border-color",
            "border-width", "border-style", "border-collapse", "border-radius");
    private HtmlMail() { }

    public static SafeBody sanitize(String raw, ImageResolver resolver, Deadline deadline) throws Exception {
        deadline.check();
        if (raw.length() > AppConstants.MAX_BODY_BYTES * 4) throw new IllegalArgumentException("HTML exceeds limit");
        Document source = Jsoup.parse(raw);
        deadline.check();
        if (source.getAllElements().size() > AppConstants.MAX_HTML_NODES) throw new IllegalArgumentException("HTML exceeds node limit");
        source.select("script,style,iframe,object,embed,form,input,button,textarea,select,svg,math,link,meta,base,template,noscript").remove();
        Safelist allowed = Safelist.relaxed().addTags("div", "span", "font", "hr", "h1", "h2", "h3", "h4", "h5", "h6")
                .addAttributes(":all", "style", "align", "bgcolor")
                .addAttributes("font", "color", "face", "size")
                .removeAttributes("a", "href").removeAttributes("img", "src");
        // Capture only known URL types ourselves. No sender data-* attributes survive cleaning.
        Map<String, String> imageSources = new HashMap<>(), links = new HashMap<>();
        int index = 0;
        for (Element element : source.select("img,a")) {
            String id = "em-" + index++;
            element.attr("id", id);
            if (element.normalName().equals("img")) imageSources.put(id, element.attr("src"));
            else links.put(id, element.hasAttr("href") ? element.attr("href") : element.attr("data-evermail-link"));
        }
        allowed.addAttributes("img", "id").addAttributes("a", "id");
        Document cleaned = new Cleaner(allowed).clean(source);
        boolean blocked = false;
        int imageChars = 0;
        Map<String, String> resolved = new HashMap<>();
        for (Element el : cleaned.body().getAllElements()) {
            deadline.check();
            for (String dimension : List.of("width", "height", "colspan", "rowspan", "size")) {
                if (el.hasAttr(dimension) && !el.attr(dimension).matches("[0-9]{1,3}%?")) el.removeAttr(dimension);
            }
            if (el.hasAttr("style")) {
                StringBuilder styles = new StringBuilder();
                for (String declaration : el.attr("style").split(";")) {
                    String[] pair = declaration.split(":", 2);
                    if (pair.length != 2) continue;
                    String name = pair[0].strip().toLowerCase(Locale.ROOT), value = pair[1].strip();
                    // Deliberately no escapes, URL functions, variables, imports, positioning or arbitrary functions.
                    if (CSS.contains(name) && value.length() <= 150
                            && value.matches("[a-zA-Z0-9#.,% '" + '"' + "-]+")
                            && !value.matches(".*[0-9]{5,}.*")
                            && !value.toLowerCase(Locale.ROOT).contains("expression")) styles.append(name).append(':').append(value).append(';');
                }
                el.attr("style", styles.toString());
            }
            if (el.normalName().equals("a")) {
                String href = links.get(el.id());
                if (safeLink(href)) el.attr("data-evermail-link", href);
                el.removeAttr("id");
            }
            if (el.normalName().equals("img")) {
                String src = imageSources.getOrDefault(el.id(), "").strip();
                el.removeAttr("id");
                String data = null;
                if (src.regionMatches(true, 0, "cid:", 0, 4)) {
                    if (!resolved.containsKey(src)) resolved.put(src, resolver.resolve(src.substring(4)));
                    data = resolved.get(src);
                } else if (src.startsWith("data:")) data = InlineImages.validateDataUri(src);
                else if (!src.isEmpty()) blocked = true;
                if (data != null && imageChars + data.length() <= AppConstants.MAX_INLINE_TOTAL_BYTES * 4 / 3) {
                    imageChars += data.length();
                    el.attr("src", data);
                } else {
                    el.replaceWith(new TextNode("[" + (el.attr("alt").isBlank() ? "Imagen no disponible" : el.attr("alt")) + "]"));
                }
            }
        }
        deadline.check();
        cleaned.outputSettings().prettyPrint(false);
        return new SafeBody(cleaned.body().html(), blocked);
    }

    public static boolean safeLink(String value) {
        if (value == null || value.length() > 4096) return false;
        try {
            URI uri = URI.create(value);
            return Set.of("https", "http").contains(Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }

    public static String document(String safeHtml) {
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">"
                + "<style>body{font:16px/1.55 sans-serif;margin:20px;overflow-wrap:anywhere;color:#222;background:white}"
                + "img{max-width:100%;height:auto}table{max-width:100%}a[data-evermail-link]{color:#162adf;text-decoration:underline;cursor:pointer}</style>"
                + "</head><body>" + safeHtml + "</body></html>";
    }
}
