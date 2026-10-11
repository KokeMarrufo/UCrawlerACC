package finder.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Letters-only tokens, lowercased, length at least 2. Shared by the index and the vocabulary. */
public final class Tokens {
    private Tokens() {}

    public static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                current.append(c);
            } else {
                keep(out, current);
            }
        }
        keep(out, current);
        return out;
    }

    private static void keep(List<String> out, StringBuilder current) {
        if (current.length() >= 2) {
            out.add(current.toString());
        }
        current.setLength(0);
    }
}
