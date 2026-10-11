package finder.index;

import finder.text.Tokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hash map from token to the list of URL and position postings.
 * A search looks up each query word instead of scanning every document.
 */
public final class InvertedIndex {
    private final Map<String, List<Posting>> postings = new HashMap<>();
    private final Set<String> urls = new HashSet<>();

    public void addDocument(String url, String text) {
        if (url == null || url.isBlank()) {
            return;
        }
        urls.add(url);
        List<String> tokens = Tokens.words(text);
        for (int i = 0; i < tokens.size(); i++) {
            postings.computeIfAbsent(tokens.get(i), key -> new ArrayList<>())
                    .add(new Posting(url, i));
        }
    }

    public boolean knowsUrl(String url) {
        return url != null && urls.contains(url);
    }

    public Set<String> urls() {
        return Set.copyOf(urls);
    }

    public List<Posting> postings(String word) {
        List<Posting> found = postings.get(normalize(word));
        if (found == null) {
            return List.of();
        }
        return List.copyOf(found);
    }

    public int frequency(String word, String url) {
        if (word == null || url == null) {
            return 0;
        }
        int count = 0;
        for (Posting posting : postings(word)) {
            if (url.equals(posting.url())) {
                count++;
            }
        }
        return count;
    }

    /** Total occurrences of one word across every document. Used by the Chapter 1 benchmark. */
    public int totalFrequency(String word) {
        return postings(word).size();
    }

    /**
     * Sum of occurrences of the query tokens on each URL.
     * URLs that contain none of the tokens are absent.
     */
    public Map<String, Integer> keywordScores(List<String> queryTokens) {
        Map<String, Integer> scores = new HashMap<>();
        if (queryTokens == null) {
            return scores;
        }
        for (String token : queryTokens) {
            for (Posting posting : postings(token)) {
                scores.merge(posting.url(), 1, Integer::sum);
            }
        }
        return scores;
    }

    public int documentCount() {
        return urls.size();
    }

    private static String normalize(String word) {
        if (word == null) {
            return "";
        }
        return word.toLowerCase(java.util.Locale.ROOT);
    }
}
