package finder;

import finder.analysis.IndexBenchmark;
import finder.crawl.CatalogLoader;
import finder.crawl.DetailScraper;
import finder.crawl.DriverConfig;
import finder.crawl.Main;
import finder.crawl.PageStore;
import finder.crawl.Program;
import finder.index.FrequencyCount;
import finder.index.InvertedIndex;
import finder.index.SearchFrequency;
import finder.rank.BinaryHeap;
import finder.rank.KeywordRanker;
import finder.rank.WeightModel;
import finder.rank.WeightedMergeSort;
import finder.regex.FieldValidator;
import finder.regex.PatternFinder;
import finder.text.AvlTree;
import finder.text.EditDistance;
import finder.text.HtmlParser;
import finder.text.MissingPageException;
import finder.text.SpellChecker;
import finder.text.Vocabulary;
import finder.text.WordCompletion;
import finder.universities.Corpus;
import finder.universities.SearchDocument;
import finder.universities.UniversitySlots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureTests {
    @Test
    void parserReadsVisibleTextAndUrl() throws Exception {
        Path html = Path.of("src/test/resources/fixtures/alpha.html");
        HtmlParser.ParsedPage page = HtmlParser.read(html);
        assertEquals("https://example.test/alpha", page.url());
        assertTrue(page.text().contains("Alpha studies glaciers and glaciers."));
        assertFalse(page.text().contains("secret"));
        assertFalse(page.text().contains("color"));
    }

    @Test
    void missingPageFileIsReported() {
        MissingPageException error = assertThrows(MissingPageException.class,
                () -> HtmlParser.read(Path.of("src/test/resources/fixtures/does-not-exist.html")));
        assertTrue(error.getMessage().toLowerCase().contains("missing page file"));
    }

    @Test
    void savedPageRoundTripUsesTheParser(@TempDir Path dir) throws Exception {
        String html = """
                <html><head><title>Sample</title></head>
                <body><p>Harbor lights.</p><script>hiddenToken</script></body></html>
                """;
        PageStore.Saved saved = PageStore.saveHtml("VGMMSC-CA", "https://example.test/sample", html, "about block", dir);
        assertTrue(Files.exists(saved.html()));
        assertTrue(Files.exists(saved.text()));
        HtmlParser.ParsedPage page = HtmlParser.read(saved.html());
        assertEquals("https://example.test/sample", page.url());
        assertTrue(page.text().contains("Harbor lights."));
        assertFalse(page.text().contains("hiddenToken"));
        assertEquals(page.text(), Files.readString(saved.text()));
    }

    @Test
    void indexFrequencyAndHeapRank() throws Exception {
        HtmlParser.ParsedPage alpha = HtmlParser.read(Path.of("src/test/resources/fixtures/alpha.html"));
        HtmlParser.ParsedPage beta = HtmlParser.read(Path.of("src/test/resources/fixtures/beta.html"));
        HtmlParser.ParsedPage gamma = HtmlParser.read(Path.of("src/test/resources/fixtures/gamma.html"));
        InvertedIndex index = new InvertedIndex();
        index.addDocument(alpha.url(), alpha.text());
        index.addDocument(beta.url(), beta.text());
        index.addDocument(gamma.url(), gamma.text());

        assertEquals(2, index.frequency("glaciers", alpha.url()));
        assertEquals(1, index.frequency("glaciers", beta.url()));
        assertEquals(0, index.frequency("glaciers", gamma.url()));
        assertTrue(index.postings("glaciers").stream().anyMatch(posting -> posting.position() >= 0));

        FrequencyCount counts = new FrequencyCount(index);
        assertEquals(2, counts.count("glaciers", alpha.url()).count());
        assertEquals("unknown URL", counts.count("glaciers", "https://example.test/missing").error());
        assertTrue(counts.count("glaciers", gamma.url()).ok());
        assertEquals(0, counts.count("glaciers", gamma.url()).count());

        List<KeywordRanker.Hit> glacierRank = new KeywordRanker().rank(
                index.keywordScores(List.of("glaciers")),
                Map.of(alpha.url(), "Alpha", beta.url(), "Beta", gamma.url(), "Gamma"));
        assertEquals(alpha.url(), glacierRank.get(0).url());
        assertTrue(glacierRank.get(0).score() > glacierRank.get(1).score());

        List<KeywordRanker.Hit> harborRank = new KeywordRanker().rank(
                index.keywordScores(List.of("harbor")),
                Map.of(beta.url(), "Beta", gamma.url(), "Gamma"));
        assertEquals(gamma.url(), harborRank.get(0).url());
        assertEquals(3, harborRank.get(0).score());
    }

    @Test
    void heapPopsHighestFirst() {
        BinaryHeap<Integer> heap = new BinaryHeap<>(Integer::compareTo);
        heap.push(1);
        heap.push(3);
        heap.push(2);
        assertEquals(3, heap.pop());
        assertEquals(2, heap.pop());
        assertEquals(1, heap.pop());
        assertTrue(heap.isEmpty());
    }

    @Test
    void avlCompletionAndSpellCheck() {
        Vocabulary vocabulary = new Vocabulary();
        AvlTree tree = vocabulary.tree();
        for (int i = 0; i <= 30; i++) {
            tree.insert(String.format("word%02d", i));
        }
        assertEquals(31, tree.size());
        assertTrue(tree.height() < 20, "AVL height should stay well below a linked chain");
        assertEquals(10, tree.wordsWithPrefix("word0").size());
        assertTrue(new WordCompletion(vocabulary).complete("").isEmpty());
        assertTrue(new WordCompletion(vocabulary).complete("word1").contains("word10"));

        Vocabulary small = new Vocabulary();
        small.addText("glacier harbor alpha");
        SpellChecker checker = new SpellChecker(small);
        assertEquals("glacier", checker.suggest("glacir").get(0).word());
        assertEquals(1, checker.suggest("glacir").get(0).distance());
        assertTrue(checker.suggest("zzzzqq").isEmpty());
        assertEquals(3, EditDistance.distance("kitten", "sitting", 10));
        assertTrue(EditDistance.distance("abc", "xyz", 2) > 2);
    }

    @Test
    void searchFrequencyIsSaved(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("search-frequency.tsv");
        SearchFrequency frequency = new SearchFrequency(file);
        frequency.load();
        frequency.record(List.of("glacier", "harbor"));
        frequency.record(List.of("glacier"));
        SearchFrequency reloaded = new SearchFrequency(file);
        reloaded.load();
        assertEquals(2, reloaded.count("glacier"));
        assertEquals(1, reloaded.count("harbor"));
        assertEquals("glacier", reloaded.ranking().get(0).word());
    }

    @Test
    void weightedMergesortPrefersLowerCostAndRenormalizes() {
        Program cheap = program("Alpha", "AAAA-AA", "https://example.test/a", "1,000.00", "1 years", "8", "10");
        Program costly = program("Beta", "BBBB-BB", "https://example.test/b", "3,000.00", "2 years", "1", "10");
        Program middle = program("Gamma", "CCCC-CC", "https://example.test/c", "2,000.00", "1 years", "", "");
        List<WeightModel.ScoredProgram> byCost = WeightedMergeSort.rank(
                List.of(costly, cheap, middle),
                new WeightModel.Weights(1, 0, 0, false));
        assertEquals("Alpha", byCost.get(0).program().getName());
        assertEquals("Gamma", byCost.get(1).program().getName());
        assertEquals("Beta", byCost.get(2).program().getName());

        Program tiedLow = program("Alpha", "AAAA-AA", "https://example.test/a", "100.00", "1 years", "8", "10");
        Program slow = program("Beta", "BBBB-BB", "https://example.test/b", "100.00", "2 years", "2", "10");
        Program noAdmit = program("Gamma", "CCCC-CC", "https://example.test/c", "100.00", "1 years", "", "");
        List<WeightModel.ScoredProgram> mixed = WeightedMergeSort.rank(
                List.of(slow, noAdmit, tiedLow),
                new WeightModel.Weights(1, 1, 1, false));
        assertEquals("Alpha", mixed.get(0).program().getName());
        assertEquals("Gamma", mixed.get(1).program().getName());
        assertEquals("Beta", mixed.get(2).program().getName());
        WeightModel.Breakdown gamma = mixed.get(1).breakdown();
        assertTrue(gamma.admissionDropped());
        assertEquals(0.5, gamma.costWeight(), 1e-9);
        assertEquals(0.5, gamma.timeWeight(), 1e-9);
        assertEquals(0.0, gamma.admitWeight(), 1e-9);
        assertTrue(WeightModel.FORMULA.contains("offers / applications"));
        assertTrue(WeightModel.FORMULA.contains("renormalized"));
        assertEquals(12.0, WeightModel.parseMonths("1 years"), 1e-9);
        assertEquals(20.4, WeightModel.parseMonths("1.7 years"), 1e-9);
    }

    @Test
    void regexValidationAndPatternSearchStaySeparate() {
        assertTrue(FieldValidator.validProgramId("VGMMSC-CA"));
        assertTrue(FieldValidator.validProgramId("VGMMASCBMEG"));
        assertFalse(FieldValidator.validProgramId("bad id"));
        assertFalse(FieldValidator.validProgramId(""));
        assertTrue(FieldValidator.validTuition("5,738.52"));
        assertTrue(FieldValidator.validTuition("$5,738.52"));
        assertTrue(FieldValidator.validTuition(""));
        assertFalse(FieldValidator.validTuition("12.5"));
        assertFalse(FieldValidator.validTuition("free"));
        assertTrue(FieldValidator.validDate("1 October 2026"));
        assertTrue(FieldValidator.validDate("September 2027"));
        assertTrue(FieldValidator.validDate(""));
        assertFalse(FieldValidator.validDate("2026-10-01"));

        Program bad = program("Bad", "nope", "https://example.test/bad", "twelve", "soon", "1", "2");
        bad.setDetail("Canadian Application Deadline", "yesterday");
        FieldValidator.Report report = FieldValidator.validate(List.of(bad));
        assertTrue(report.failures().size() >= 3);

        List<SearchDocument> docs = List.of(new SearchDocument(
                "ubc", "VGMMSC-CA", "Sample", "https://example.test/alpha",
                "code 12345 appears near VGMMSC-CA", false));
        PatternFinder.Outcome found = PatternFinder.find(docs, "\\d{5}");
        assertTrue(found.ok());
        assertEquals("12345", found.matches().get(0).text());
        assertEquals("https://example.test/alpha", found.matches().get(0).url());
        PatternFinder.Outcome broken = PatternFinder.find(docs, "[");
        assertFalse(broken.ok());
        assertTrue(broken.error().contains("invalid regular expression"));
    }

    @Test
    void catalogLoadsOfflineAndEmptySlotsShareColumns() throws Exception {
        assertFalse(Files.readString(Path.of("src/main/java/finder/crawl/Main.java")).contains("/Users/jorgemarrufo"));
        assertTrue(List.of(Main.DEGREE_LEVELS).contains("Master's"));
        assertTrue(List.of(Main.DEGREE_LEVELS).contains("Doctoral"));
        assertNull(DriverConfig.resolveValue("  ", null, ""));
        assertEquals("/tmp/driver", DriverConfig.resolveValue(null, "/tmp/driver", "/other"));
        assertEquals("/opt/driver", DriverConfig.resolveValue("/opt/driver", "/tmp/driver", "/other"));

        List<Program> programs = CatalogLoader.load(Path.of("data/ubc_programs.csv"));
        assertEquals(61, programs.size());
        assertEquals(34, DetailScraper.COLUMNS.size());
        assertEquals(38, UniversitySlots.columns().size());
        Program first = programs.get(0);
        assertEquals("Master of Science in Astronomy (MSc)", first.getName());
        assertEquals("VGMMSC-CA", first.getDetail("Program ID"));
        assertEquals("", first.getDetail("Minimum Funding per Year"));
        assertEquals("", first.getDetail("Not a column"));
        assertTrue(FieldValidator.validate(programs).failures().isEmpty());

        List<UniversitySlots.UniversitySlot> slots = UniversitySlots.all();
        assertEquals(5, slots.size());
        assertEquals(4, slots.stream().filter(slot -> !slot.crawlerPresent()).count());
        assertTrue(slots.stream().filter(slot -> !slot.crawlerPresent()).allMatch(slot -> slot.csvFileName() == null));

        Corpus corpus = Corpus.load(Path.of("data"));
        assertEquals(61, corpus.documents().size());
        assertTrue(corpus.documents().stream().noneMatch(SearchDocument::savedPage));
        assertTrue(corpus.programsForUniversity("slot-2").isEmpty());
        assertFalse(corpus.index().keywordScores(List.of("astronomy")).isEmpty());
        SpellChecker checker = new SpellChecker(corpus.vocabulary());
        assertTrue(checker.suggest("astronmy").stream().anyMatch(suggestion -> suggestion.word().equals("astronomy")));
        assertTrue(new WordCompletion(corpus.vocabulary()).complete("astro").contains("astronomy"));

        IndexBenchmark.Timing timing = IndexBenchmark.compare(corpus.documents(), corpus.index(), "science", 20);
        assertTrue(timing.countsMatch());
        assertTrue(timing.scanCount() > 0);
        assertTrue(timing.scanNanos() >= 0);
        assertTrue(timing.indexNanos() >= 0);
    }

    @Test
    void emptyQueryDoesNotSearchOrRecord(@TempDir Path dir) throws Exception {
        SearchFrequency frequency = new SearchFrequency(dir.resolve("freq.tsv"));
        frequency.load();
        Corpus corpus = Corpus.of(UniversitySlots.all(), List.of(), List.of(new SearchDocument(
                "ubc", "AAAA-AA", "Alpha", "https://example.test/alpha", "alpha glaciers", false)));
        finder.portal.SearchService service = new finder.portal.SearchService(corpus, frequency);
        finder.portal.SearchService.SearchOutcome outcome = service.search("   ", false, new WeightModel.Weights(1, 1, 1, false));
        assertTrue(outcome.isEmptyQuery());
        assertTrue(outcome.hits().isEmpty());
        assertEquals(0, frequency.count("alpha"));
        assertTrue(service.complete("gla").contains("glaciers"));
        assertTrue(service.complete("").isEmpty());
    }

    private static Program program(String name, String id, String url, String domestic, String minTime, String offers, String applications) {
        Program program = new Program(name, name, "Faculty of Science", url);
        program.setDetail("Program ID", id);
        program.setDetail("Tuition Domestic (First Year)", domestic);
        program.setDetail("Tuition International (First Year)", domestic);
        program.setDetail("Min Time to Completion", minTime);
        program.setDetail("Offers", offers);
        program.setDetail("Applications", applications);
        return program;
    }

    @Test
    void fullScanMatchesTheIndexOnFixtures() throws Exception {
        List<SearchDocument> documents = new ArrayList<>();
        InvertedIndex index = new InvertedIndex();
        for (String name : List.of("alpha.html", "beta.html", "gamma.html")) {
            HtmlParser.ParsedPage page = HtmlParser.read(Path.of("src/test/resources/fixtures", name));
            documents.add(new SearchDocument("ubc", name, name, page.url(), page.text(), true));
            index.addDocument(page.url(), page.text());
        }
        IndexBenchmark.Timing timing = IndexBenchmark.compare(documents, index, "harbor", 30);
        assertTrue(timing.countsMatch());
        assertEquals(4, timing.count());
    }
}
