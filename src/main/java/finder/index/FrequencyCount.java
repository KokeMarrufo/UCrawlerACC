package finder.index;

/**
 * How many times one word occurs on one URL, read from the inverted index.
 * An unknown URL is an error, not a count of zero.
 */
public final class FrequencyCount {
    private final InvertedIndex index;

    public FrequencyCount(InvertedIndex index) {
        this.index = index;
    }

    public Result count(String word, String url) {
        if (url == null || url.isBlank() || index == null || !index.knowsUrl(url)) {
            return Result.error("unknown URL");
        }
        if (word == null || word.isBlank()) {
            return Result.error("empty word");
        }
        return Result.ok(index.frequency(word, url));
    }

    public record Result(boolean ok, int count, String error) {
        public static Result ok(int count) {
            return new Result(true, count, "");
        }

        public static Result error(String error) {
            return new Result(false, 0, error);
        }
    }
}
