package finder.rank;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-ranks programs with mergesort. Higher weighted score comes first.
 * When scores tie, the program name decides, in alphabetical order.
 */
public final class WeightedMergeSort {
    private WeightedMergeSort() {}

    public static List<WeightModel.ScoredProgram> rank(List<finder.crawl.Program> programs, WeightModel.Weights weights) {
        List<WeightModel.ScoredProgram> scored = WeightModel.score(programs, weights);
        WeightModel.ScoredProgram[] items = scored.toArray(WeightModel.ScoredProgram[]::new);
        sort(items);
        List<WeightModel.ScoredProgram> ordered = new ArrayList<>(items.length);
        for (WeightModel.ScoredProgram item : items) {
            ordered.add(item);
        }
        return ordered;
    }

    static void sort(WeightModel.ScoredProgram[] items) {
        if (items == null || items.length < 2) {
            return;
        }
        WeightModel.ScoredProgram[] tmp = new WeightModel.ScoredProgram[items.length];
        mergesort(items, tmp, 0, items.length);
    }

    private static void mergesort(WeightModel.ScoredProgram[] items, WeightModel.ScoredProgram[] tmp, int lo, int hi) {
        if (hi - lo <= 1) {
            return;
        }
        int mid = (lo + hi) >>> 1;
        mergesort(items, tmp, lo, mid);
        mergesort(items, tmp, mid, hi);
        int left = lo;
        int right = mid;
        int out = lo;
        while (left < mid && right < hi) {
            if (comesBefore(items[left], items[right])) {
                tmp[out++] = items[left++];
            } else {
                tmp[out++] = items[right++];
            }
        }
        while (left < mid) {
            tmp[out++] = items[left++];
        }
        while (right < hi) {
            tmp[out++] = items[right++];
        }
        for (int i = lo; i < hi; i++) {
            items[i] = tmp[i];
        }
    }

    private static boolean comesBefore(WeightModel.ScoredProgram left, WeightModel.ScoredProgram right) {
        int byScore = Double.compare(left.score(), right.score());
        if (byScore != 0) {
            return byScore > 0;
        }
        String leftName = left.program().getName();
        String rightName = right.program().getName();
        int byName = leftName.compareTo(rightName);
        if (byName != 0) {
            return byName < 0;
        }
        return true;
    }
}
