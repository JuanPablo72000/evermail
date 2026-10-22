package com.juanpablo.evermail.util;

import com.helger.css.ECSSVersion;
import com.helger.css.decl.*;
import com.helger.css.handler.DoNothingCSSParseExceptionCallback;
import com.helger.css.reader.*;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;
import com.helger.css.reader.errorhandler.DoNothingCSSInterpretErrorHandler;
import com.helger.css.writer.CSSWriterSettings;
import com.juanpablo.evermail.config.Deadline;
import java.util.*;
import java.util.regex.Pattern;

/** Parse CSS into an AST, then emit only a bounded, static email layout subset. */
final class MailCss {
    static final String ROOT = "evermail-mail-root";
    private static final CSSWriterSettings WRITER = new CSSWriterSettings(ECSSVersion.CSS30, true);
    private static final Set<String> PROPERTIES = Set.of(
            "color", "background", "background-color", "background-image", "background-position", "background-size", "background-repeat",
            "font", "font-family", "font-size", "font-weight", "font-style", "font-variant", "line-height", "letter-spacing", "word-spacing",
            "text-align", "text-decoration", "text-transform", "text-indent", "vertical-align", "white-space", "word-break", "overflow-wrap",
            "width", "height", "min-width", "max-width", "min-height", "max-height", "box-sizing",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "border", "border-width", "border-style", "border-color", "border-radius", "border-collapse", "border-spacing",
            "border-top", "border-right", "border-bottom", "border-left", "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
            "border-top-style", "border-right-style", "border-bottom-style", "border-left-style",
            "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
            "table-layout", "display", "float", "clear", "list-style-type", "list-style-position", "direction");
    private static final Pattern COMPOUND = Pattern.compile("(?:[a-zA-Z][a-zA-Z0-9-]*|\\*)?(?:[.#][a-zA-Z_][a-zA-Z0-9_-]*)*(?::(?:first-child|last-child|only-child))?");
    private static final String WIDTH = "\\((?:min-|max-)?width:[0-9]{1,4}(?:\\.[0-9]{1,2})?(?:px|em)\\)";
    private static final Pattern MEDIA = Pattern.compile("(?:(?:only )?(?:screen|all)(?: and " + WIDTH + ")*|" + WIDTH + "(?: and " + WIDTH + ")*)");
    private final HtmlMail.ImageResolver images;
    private final Deadline deadline;
    private int inputChars, rules, declarations;

    MailCss(HtmlMail.ImageResolver images, Deadline deadline) { this.images = images; this.deadline = deadline; }

    private CSSReaderSettings settings() {
        return new CSSReaderSettings().setCSSVersion(ECSSVersion.CSS30).setUseSourceLocation(false)
                .setCustomErrorHandler(new DoNothingCSSParseErrorHandler())
                .setInterpretErrorHandler(new DoNothingCSSInterpretErrorHandler())
                .setCustomExceptionHandler(new DoNothingCSSParseExceptionCallback());
    }

    String stylesheet(String css) throws Exception {
        if (!acceptInput(css)) return "";
        var parsed = CSSReader.readFromStringReader(css, settings());
        return parsed == null ? "" : rules(parsed.getAllRules(), 0);
    }

    String inline(String css) throws Exception {
        if (!acceptInput(css)) return "";
        var parsed = CSSReaderDeclarationList.readFromString(css, settings());
        return parsed == null ? "" : declarations(parsed.getAllDeclarations());
    }

    // Bound parser input and nesting before invoking the parser, including malformed input.
    private boolean acceptInput(String css) throws Exception {
        deadline.check();
        if (css.isBlank()) return false;
        inputChars += css.length();
        if (inputChars > 3_000_000 || css.indexOf('\\') >= 0 || css.indexOf('<') >= 0 || css.indexOf('>') >= 0 && !css.contains("{")) return false;
        int depth = 0;
        for (int i = 0; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '(' || c == '{') { if (++depth > 16) return false; }
            else if (c == ')' || c == '}') depth = Math.max(0, depth - 1);
        }
        return true;
    }

    private String rules(List<ICSSTopLevelRule> input, int depth) throws Exception {
        if (depth > 2) return "";
        StringBuilder output = new StringBuilder();
        for (var rule : input) {
            deadline.check();
            if (++rules > 512) break;
            if (rule instanceof CSSStyleRule style) {
                List<String> selectors = new ArrayList<>();
                for (var selector : style.getAllSelectors()) {
                    if (selectors.size() == 32) break;
                    String safe = selector(selector.getAsCSSString(WRITER, 0));
                    if (safe != null) selectors.add(safe);
                }
                if (!selectors.isEmpty()) {
                    String body = declarations(style.getAllDeclarations());
                    if (!body.isEmpty()) output.append(String.join(",", selectors)).append('{').append(body).append('}');
                }
            } else if (rule instanceof CSSMediaRule media) {
                List<String> queries = new ArrayList<>();
                for (var query : media.getAllMediaQueries()) {
                    String value = query.getAsCSSString(WRITER, 0).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("\\s*:\\s*", ":").strip();
                    if (value.length() <= 160 && MEDIA.matcher(value).matches()) queries.add(value);
                }
                if (!queries.isEmpty() && queries.size() <= 8) {
                    String body = rules(media.getAllRules(), depth + 1);
                    if (!body.isEmpty()) output.append("@media ").append(String.join(",", queries)).append('{').append(body).append('}');
                }
            }
            // @import, @font-face, animation, supports and unknown rules never reach the viewer.
        }
        return output.toString();
    }

    private String selector(String value) {
        if (value.length() > 256) return null;
        String[] parts = value.strip().split("\\s*[>+~]\\s*|\\s+");
        if (parts.length > 8) return null;
        for (String part : parts) if (part.isEmpty() || !COMPOUND.matcher(part).matches()) return null;
        // The original body is represented by an inner wrapper, not by the viewer's body.
        return value.replaceAll("(?i)\\bhtml\\s+(?:>\\s*)?body\\b", "body")
                .replaceAll("(?i)(?<![a-zA-Z0-9_.#-])(?:html|body)(?![a-zA-Z0-9_-])", "." + ROOT);
    }

    private String declarations(List<CSSDeclaration> input) throws Exception {
        StringBuilder output = new StringBuilder();
        for (var declaration : input) {
            deadline.check();
            if (++declarations > 12000) break;
            String name = declaration.getProperty().toLowerCase(Locale.ROOT);
            if (!PROPERTIES.contains(name)) continue;
            String value = expression(declaration.getExpression(), name, 0);
            if (value == null || value.isBlank()) continue;
            // Layout stays inside normal flow; no fixed positioning, overlays or clipping.
            if (name.equals("display") && !Set.of("none", "block", "inline", "inline-block", "table", "inline-table", "table-row", "table-cell", "table-row-group", "table-header-group", "table-footer-group", "list-item").contains(value)) continue;
            output.append(name).append(':').append(value);
            if (declaration.isImportant()) output.append("!important");
            output.append(';');
        }
        return output.toString();
    }

    private String expression(CSSExpression expression, String property, int depth) throws Exception {
        if (expression == null || expression.getMemberCount() > 32 || depth > 2) return null;
        StringBuilder output = new StringBuilder();
        for (var member : expression.getAllMembers()) {
            String value;
            if (member instanceof CSSExpressionMemberTermURI uri) {
                if (!property.equals("background") && !property.equals("background-image")) return null;
                String image = images.resolve(uri.getURIString());
                value = image == null ? "none" : "url(\"" + image.replace("\"", "%22").replace("\\", "%5C") + "\")";
            } else if (member instanceof CSSExpressionMemberFunction function) {
                String name = function.getFunctionName().toLowerCase(Locale.ROOT);
                if (!Set.of("rgb", "rgba", "hsl", "hsla").contains(name)) return null;
                String args = expression(function.getExpression(), "color", depth + 1);
                if (args == null || !args.matches("[0-9.,% +/\\-]+")) return null;
                value = name + "(" + args + ")";
            } else if (member instanceof CSSExpressionMemberTermSimple simple) {
                value = simple.getValue();
                if (value.length() > 150 || !value.matches("[a-zA-Z0-9#.% '\"_-]+")) return null;
                if (value.matches("-[0-9].*")) return null;
                if (value.matches("[0-9.].*")) {
                    var number = Pattern.compile("([0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(px|em|rem|pt|%|)?").matcher(value);
                    if (!number.matches()) return null;
                    double amount = Double.parseDouble(number.group(1));
                    String unit = Objects.toString(number.group(2), "");
                    if (amount > (unit.equals("em") || unit.equals("rem") ? 100 : 4096)) return null;
                }
            } else {
                value = member.getAsCSSString(WRITER, 0);
                if (!value.equals(",") && !value.equals("/")) return null;
            }
            if (!output.isEmpty()) output.append(' ');
            output.append(value);
        }
        return output.toString();
    }
}
