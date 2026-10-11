package finder.rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orders URLs by how often the query words occur.
 * The ordering structure is a binary heap, not a library sort.
 */
public final class KeywordRanker {
    public List<Hit> rank(Map<String, Integer> scores, Map<String, String> urlToName) {
        BinaryHeap<Hit> heap = new BinaryHeap<>((left, right) -> {
            int byScore = Integer.compare(left.score(), right.score());
            if (byScore != 0) {
                return byScore;
            }
            return right.name().compareTo(left.name());
        });
        if (scores != null) {
            for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                int score = entry.getValue() == null ? 0 : entry.getValue();
                if (score <= 0 || entry.getKey() == null) {
                    continue;
                }
                String name = urlToName == null ? entry.getKey() : urlToName.getOrDefault(entry.getKey(), entry.getKey());
                heap.push(new Hit(entry.getKey(), name, score));
            }
        }
        List<Hit> ordered = new ArrayList<>();
        while (!heap.isEmpty()) {
            ordered.add(heap.pop());
        }
        return ordered;
    }

    public record Hit(String url, String name, int score) {}
}
