package finder.portal;

import finder.crawl.Program;
import finder.index.FrequencyCount;
import finder.index.SearchFrequency;
import finder.rank.KeywordRanker;
import finder.rank.WeightModel;
import finder.rank.WeightedMergeSort;
import finder.text.SpellChecker;
import finder.text.Tokens;
import finder.text.WordCompletion;
import finder.universities.Corpus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal search: inverted index, heap ranking, optional weighted mergesort,
 * word completion, spell check when nothing matches, and a saved search count.
 */
public final class SearchService {
    private final Corpus corpus;
    private final SearchFrequency searchFrequency;
    private final FrequencyCount frequencyCount;
    private final SpellChecker spellChecker;
    private final WordCompletion wordCompletion;
    private final KeywordRanker keywordRanker = new KeywordRanker();

    public SearchService(Corpus corpus, SearchFrequency searchFrequency) {
        this.corpus = corpus;
        this.searchFrequency = searchFrequency;
        this.frequencyCount = new FrequencyCount(corpus.index());
        this.spellChecker = new SpellChecker(corpus.vocabulary());
        this.wordCompletion = new WordCompletion(corpus.vocabulary());
    }

    public Corpus corpus() {
        return corpus;
    }

    public SearchFrequency searchFrequency() {
        return searchFrequency;
    }

    public List<String> complete(String prefix) {
        return wordCompletion.complete(prefix);
    }

    public FrequencyCount.Result frequency(String word, String url) {
        return frequencyCount.count(word, url);
    }

    public SearchOutcome search(String rawQuery, boolean weighted, WeightModel.Weights weights) throws IOException {
        if (rawQuery == null || rawQuery.isBlank()) {
            return SearchOutcome.emptyQuery();
        }
        List<String> tokens = Tokens.words(rawQuery);
        if (tokens.isEmpty()) {
            return SearchOutcome.emptyQuery();
        }
        searchFrequency.record(tokens);
        if (corpus.documents().isEmpty()) {
            return new SearchOutcome("No documents are loaded, so the index is empty.", List.of(), List.of(), true);
        }
        Map<String, Integer> scores = corpus.index().keywordScores(tokens);
        if (scores.isEmpty()) {
            List<SpellChecker.Suggestion> suggestions = new ArrayList<>();
            for (String token : tokens) {
                for (SpellChecker.Suggestion suggestion : spellChecker.suggest(token)) {
                    if (suggestions.stream().noneMatch(existing -> existing.word().equals(suggestion.word()))) {
                        suggestions.add(suggestion);
                    }
                }
            }
            return new SearchOutcome("", suggestions, List.of(), true);
        }
        List<KeywordRanker.Hit> hits = keywordRanker.rank(scores, corpus.namesByUrl());
        if (weighted) {
            if (weights == null || !weights.anyPositive()
                    || weights.cost() < 0 || weights.time() < 0 || weights.admission() < 0) {
                return new SearchOutcome(
                        "Weights must be zero or positive, and at least one weight has to be above zero.",
                        List.of(),
                        keywordOnly(hits),
                        true);
            }
            return new SearchOutcome("", List.of(), weightReorder(hits, weights), true);
        }
        return new SearchOutcome("", List.of(), keywordOnly(hits), true);
    }

    private List<Ranked> keywordOnly(List<KeywordRanker.Hit> hits) {
        List<Ranked> ranked = new ArrayList<>();
        for (KeywordRanker.Hit hit : hits) {
            ranked.add(new Ranked(hit, null));
        }
        return ranked;
    }

    private List<Ranked> weightReorder(List<KeywordRanker.Hit> hits, WeightModel.Weights weights) {
        List<Program> programs = new ArrayList<>();
        Map<Program, KeywordRanker.Hit> byProgram = new IdentityHashMap<>();
        List<Ranked> unmatched = new ArrayList<>();
        for (KeywordRanker.Hit hit : hits) {
            Program program = corpus.programByUrl(hit.url());
            if (program == null) {
                unmatched.add(new Ranked(hit, null));
            } else {
                programs.add(program);
                byProgram.put(program, hit);
            }
        }
        List<Ranked> ranked = new ArrayList<>();
        for (WeightModel.ScoredProgram scored : WeightedMergeSort.rank(programs, weights)) {
            ranked.add(new Ranked(byProgram.get(scored.program()), scored));
        }
        ranked.addAll(unmatched);
        return ranked;
    }

    public record Ranked(KeywordRanker.Hit keyword, WeightModel.ScoredProgram weighted) {}

    public record SearchOutcome(
            String error,
            List<SpellChecker.Suggestion> suggestions,
            List<Ranked> hits,
            boolean recorded) {

        public static SearchOutcome emptyQuery() {
            return new SearchOutcome("empty query", List.of(), List.of(), false);
        }

        public boolean isEmptyQuery() {
            return "empty query".equals(error);
        }
    }
}
