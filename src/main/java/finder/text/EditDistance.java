package finder.text;

/** Levenshtein distance with an early stop once the limit is exceeded. */
public final class EditDistance {
    private EditDistance() {}

    public static int distance(String left, String right, int limit) {
        if (left == null || right == null) {
            return limit + 1;
        }
        if (left.equals(right)) {
            return 0;
        }
        if (Math.abs(left.length() - right.length()) > limit) {
            return limit + 1;
        }
        int rows = left.length();
        int cols = right.length();
        int[] previous = new int[cols + 1];
        int[] current = new int[cols + 1];
        for (int j = 0; j <= cols; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= rows; i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= cols; j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                int insert = current[j - 1] + 1;
                int delete = previous[j] + 1;
                int replace = previous[j - 1] + cost;
                current[j] = Math.min(insert, Math.min(delete, replace));
                if (current[j] < rowMin) {
                    rowMin = current[j];
                }
            }
            if (rowMin > limit) {
                return limit + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[cols] > limit ? limit + 1 : previous[cols];
    }
}
