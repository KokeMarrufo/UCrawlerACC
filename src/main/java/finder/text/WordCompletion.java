package finder.text;

import java.util.List;

/** Prefix completion by walking the AVL vocabulary. An empty prefix returns nothing. */
public final class WordCompletion {
    private final Vocabulary vocabulary;

    public WordCompletion(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public List<String> complete(String prefix) {
        return complete(prefix, 12);
    }

    public List<String> complete(String prefix, int limit) {
        if (prefix == null || prefix.isBlank() || vocabulary == null) {
            return List.of();
        }
        String normalized = prefix.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> words = vocabulary.tree().wordsWithPrefix(normalized);
        if (words.size() > limit) {
            return List.copyOf(words.subList(0, limit));
        }
        return words;
    }
}
