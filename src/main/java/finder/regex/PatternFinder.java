package finder.regex;

import finder.universities.SearchDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Finds a regular expression in page text and returns each match with its URL.
 * Invalid patterns are reported. They are not thrown through to the portal.
 */
public final class PatternFinder {
    private static final int MAX_MATCHES = 50;

    private PatternFinder() {}

    public static Outcome find(List<SearchDocument> documents, String regex) {
        if (regex == null || regex.isBlank()) {
            return Outcome.error("Enter a regular expression.");
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException ex) {
            return Outcome.error("invalid regular expression: " + ex.getDescription());
        }
        List<Match> matches = new ArrayList<>();
        if (documents != null) {
            for (SearchDocument document : documents) {
                if (document == null || document.text() == null) {
                    continue;
                }
                Matcher matcher = pattern.matcher(document.text());
                while (matcher.find()) {
                    if (matches.size() >= MAX_MATCHES) {
                        return new Outcome("", matches, true);
                    }
                    matches.add(new Match(
                            document.url(),
                            document.name(),
                            matcher.group(),
                            snippet(document.text(), matcher.start(), matcher.end())));
                }
            }
        }
        return new Outcome("", matches, false);
    }

    private static String snippet(String text, int start, int end) {
        int from = Math.max(0, start - 24);
        int to = Math.min(text.length(), end + 24);
        String piece = text.substring(from, to).replaceAll("\\s+", " ").trim();
        if (from > 0) {
            piece = "..." + piece;
        }
        if (to < text.length()) {
            piece = piece + "...";
        }
        return piece;
    }

    public record Match(String url, String name, String text, String snippet) {}

    public record Outcome(String error, List<Match> matches, boolean truncated) {
        public static Outcome error(String error) {
            return new Outcome(error, List.of(), false);
        }

        public boolean ok() {
            return error == null || error.isEmpty();
        }
    }
}
