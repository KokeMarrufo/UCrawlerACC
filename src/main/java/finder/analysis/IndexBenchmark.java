package finder.analysis;

import finder.index.InvertedIndex;
import finder.universities.Corpus;
import finder.universities.SearchDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Chapter 1 timing: a full scan of every document versus one inverted-index lookup.
 * This is a measurement, not a portal feature.
 */
public final class IndexBenchmark {
    private IndexBenchmark() {}

    public static void main(String[] args) throws IOException {
        Path dataDir = Path.of(System.getProperty("finder.data", "data"));
        Corpus corpus = Corpus.load(dataDir);
        System.out.println("Chapter 1: full scan versus inverted index");
        System.out.println("documents: " + corpus.documents().size());
        if (corpus.documents().isEmpty()) {
            System.out.println("No documents loaded. Nothing to time.");
            return;
        }
        String[] words = args.length > 0 ? args : new String[] {"science", "master", "engineering"};
        int repeats = 300;
        System.out.println("repeats: " + repeats + " (the times below are totals, not a single lookup)");
        System.out.printf("%-16s %12s %12s %10s%n", "word", "scan_ms", "index_ms", "count");
        for (String word : words) {
            Timing timing = compare(corpus.documents(), corpus.index(), word, repeats);
            System.out.printf("%-16s %12.3f %12.3f %10d%n",
                    timing.word(), timing.scanMillis(), timing.indexMillis(), timing.count());
        }
    }

    public static Timing compare(List<SearchDocument> documents, InvertedIndex index, String word, int repeats) {
        int times = Math.max(1, repeats);
        for (int i = 0; i < 5; i++) {
            Corpus.scanCount(documents, word);
            index.totalFrequency(word);
        }
        long scanStart = System.nanoTime();
        int scanTotal = 0;
        for (int i = 0; i < times; i++) {
            scanTotal = Corpus.scanCount(documents, word);
        }
        long scanNanos = System.nanoTime() - scanStart;
        long indexStart = System.nanoTime();
        int indexTotal = 0;
        for (int i = 0; i < times; i++) {
            indexTotal = index.totalFrequency(word);
        }
        long indexNanos = System.nanoTime() - indexStart;
        return new Timing(word, documents.size(), times, scanNanos, indexNanos, scanTotal, indexTotal);
    }

    public record Timing(
            String word,
            int documents,
            int repeats,
            long scanNanos,
            long indexNanos,
            int scanCount,
            int indexCount) {

        public boolean countsMatch() {
            return scanCount == indexCount;
        }

        public int count() {
            return indexCount;
        }

        public double scanMillis() {
            return scanNanos / 1_000_000.0;
        }

        public double indexMillis() {
            return indexNanos / 1_000_000.0;
        }
    }
}
