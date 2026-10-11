package finder.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a saved HTML file and returns visible text and the page URL.
 * No Selenium. Script and style contents are not part of the text.
 */
public final class HtmlParser {
    private static final Pattern SOURCE_URL = Pattern.compile(
            "(?is)<meta[^>]*name\\s*=\\s*[\"']finder-source-url[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>"
                    + "|(?is)<meta[^>]*content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*name\\s*=\\s*[\"']finder-source-url[\"'][^>]*>");
    private static final Pattern CANONICAL = Pattern.compile(
            "(?is)<link[^>]*rel\\s*=\\s*[\"']canonical[\"'][^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern STYLE = Pattern.compile("(?is)<style\\b[^>]*>.*?</style>");
    private static final Pattern NOSCRIPT = Pattern.compile("(?is)<noscript\\b[^>]*>.*?</noscript>");
    private static final Pattern BLOCK = Pattern.compile("(?is)</?(p|div|br|li|tr|h[1-6]|section|article|header|footer|table)\\b[^>]*>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");

    private HtmlParser() {}

    public static ParsedPage read(Path file) throws IOException {
        if (file == null || Files.notExists(file) || Files.isDirectory(file)) {
            throw new MissingPageException(file);
        }
        return parseHtml(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static ParsedPage parseHtml(String html) {
        if (html == null) {
            html = "";
        }
        return new ParsedPage(extractUrl(html), visibleText(html));
    }

    public static String extractUrl(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher meta = SOURCE_URL.matcher(html);
        if (meta.find()) {
            String first = meta.group(1);
            String second = meta.group(2);
            if (first != null) {
                return decode(first).trim();
            }
            if (second != null) {
                return decode(second).trim();
            }
        }
        Matcher canonical = CANONICAL.matcher(html);
        if (canonical.find()) {
            return decode(canonical.group(1)).trim();
        }
        return "";
    }

    public static String visibleText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = COMMENT.matcher(html).replaceAll(" ");
        text = SCRIPT.matcher(text).replaceAll(" ");
        text = STYLE.matcher(text).replaceAll(" ");
        text = NOSCRIPT.matcher(text).replaceAll(" ");
        text = BLOCK.matcher(text).replaceAll(" ");
        text = TAG.matcher(text).replaceAll(" ");
        text = decode(text);
        text = text.replace('\u00a0', ' ');
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String decode(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '&') {
                out.append(c);
                continue;
            }
            int semi = text.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 12) {
                out.append(c);
                continue;
            }
            String entity = text.substring(i + 1, semi);
            Character decoded = decodeEntity(entity);
            if (decoded == null) {
                out.append(c);
            } else {
                out.append(decoded.charValue());
                i = semi;
            }
        }
        return out.toString();
    }

    private static Character decodeEntity(String entity) {
        return switch (entity) {
            case "amp" -> '&';
            case "lt" -> '<';
            case "gt" -> '>';
            case "quot" -> '"';
            case "apos", "#39", "#039" -> '\'';
            case "nbsp", "#160" -> ' ';
            default -> numericEntity(entity);
        };
    }

    private static Character numericEntity(String entity) {
        try {
            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                return (char) Integer.parseInt(entity.substring(2), 16);
            }
            if (entity.startsWith("#")) {
                return (char) Integer.parseInt(entity.substring(1));
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return null;
    }

    public record ParsedPage(String url, String text) {}
}
