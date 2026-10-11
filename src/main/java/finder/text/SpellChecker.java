package finder.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Suggests vocabulary words within edit distance 2.
 * Used when a search hits nothing in the inverted index.
 */
public final class SpellChecker {
    public static final int MAX_DISTANCE = 2;
    private final Vocabulary vocabulary;

    public SpellChecker(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public List<Suggestion> suggest(String word) {
        return suggest(word, MAX_DISTANCE, 8);
    }

    public List<Suggestion> suggest(String word, int maxDistance, int limit) {
        List<Suggestion> found = new ArrayList<>();
        if (word == null || word.isBlank() || vocabulary == null) {
            return found;
        }
        String needle = word.toLowerCase(java.util.Locale.ROOT);
        for (String candidate : vocabulary.tree().allWords()) {
            if (candidate.equals(needle)) {
                continue;
            }
            int distance = EditDistance.distance(needle, candidate, maxDistance);
            if (distance <= maxDistance) {
                found.add(new Suggestion(candidate, distance));
            }
        }
        found.sort(Comparator.comparingInt(Suggestion::distance).thenComparing(Suggestion::word));
        if (found.size() > limit) {
            return new ArrayList<>(found.subList(0, limit));
        }
        return found;
    }

    public record Suggestion(String word, int distance) {}
}
