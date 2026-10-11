package finder.crawl;

import finder.text.HtmlParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes data/pages/&lt;id&gt;.html and .txt during a crawl.
 * The text file is the visible text from {@link HtmlParser}, not a hand-written description.
 */
public final class PageStore {
    private PageStore() {}

    public static Path pagesDir() {
        return Path.of(System.getProperty("finder.pages", "data/pages"));
    }

    public static void saveProgram(Program program, String html, String about) throws IOException {
        String id = program.getDetail("Program ID");
        if (id.isBlank()) {
            id = slug(program.getUrl());
        }
        saveHtml(id, program.getUrl(), html, about, pagesDir());
    }

    public static Saved saveHtml(String id, String url, String html, String about, Path dir) throws IOException {
        if (html == null) {
            throw new IOException("No HTML to save for " + id);
        }
        String safeId = sanitize(id);
        Path root = dir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path htmlPath = root.resolve(safeId + ".html").normalize();
        Path txtPath = root.resolve(safeId + ".txt").normalize();
        if (!htmlPath.startsWith(root) || !txtPath.startsWith(root)) {
            throw new IOException("Refusing page id that escapes the pages directory: " + id);
        }
        String withUrl = ensureUrlMeta(html, url == null ? "" : url);
        Files.writeString(htmlPath, withUrl, StandardCharsets.UTF_8);
        HtmlParser.ParsedPage parsed = HtmlParser.parseHtml(withUrl);
        Files.writeString(txtPath, parsed.text(), StandardCharsets.UTF_8);
        if (about != null && !about.isBlank()) {
            System.out.println("About section included for " + safeId);
        }
        System.out.println("Saved page " + htmlPath.getFileName() + " and " + txtPath.getFileName());
        return new Saved(safeId, htmlPath, txtPath);
    }

    public static String sanitize(String id) {
        if (id == null || id.isBlank()) {
            return "page";
        }
        String safe = id.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            return "page";
        }
        return safe;
    }

    private static String slug(String url) {
        if (url == null || url.isBlank()) {
            return "page";
        }
        int slash = url.lastIndexOf('/');
        String last = slash >= 0 ? url.substring(slash + 1) : url;
        int query = last.indexOf('?');
        if (query >= 0) {
            last = last.substring(0, query);
        }
        return last.isBlank() ? "page" : last;
    }

    static String ensureUrlMeta(String html, String url) {
        if (html.contains("name=\"finder-source-url\"") || html.contains("name='finder-source-url'")) {
            return html;
        }
        String meta = "<meta name=\"finder-source-url\" content=\"" + escapeAttribute(url) + "\">";
        int head = indexOfIgnoreCase(html, "<head>");
        if (head >= 0) {
            int insert = head + "<head>".length();
            return html.substring(0, insert) + meta + html.substring(insert);
        }
        return "<head>" + meta + "</head>" + html;
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase().indexOf(needle.toLowerCase());
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    public record Saved(String id, Path html, Path text) {}
}
