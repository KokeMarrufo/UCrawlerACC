# Defense answers

These are answers about the files that implement each feature. They are not a walk through the whole project.

**Why is the index a hash map, and what does a search cost?**
`InvertedIndex` maps a token to a list of postings, each a URL and a position. A query hashes the word and reads that list. It does not open every document. `IndexBenchmark` checks the claim: the same word counted by scanning every document and by the map must match, and the map is the one that stays cheap as the repeat count grows. An unknown URL in `FrequencyCount` is an error. A known URL that is simply absent from that word's list is zero.

**Why an AVL tree for completion, instead of the same hash map?**
The index answers "where does this exact word occur." Completion needs every word that starts with a prefix, in order. `AvlTree` keeps the vocabulary sorted and balanced, so `WordCompletion` can walk from the prefix to the last word that still matches. A hash map does not give that walk. Spell check uses the same tree as its dictionary, so there is one vocabulary, not a second copy with a different order.

**What does edit distance 2 actually skip?**
`SpellChecker` only suggests a vocabulary word when `EditDistance` is at most 2. Before that comparison, a word whose length already differs by more than 2 is dropped. The distance cannot be smaller than the length gap, so those words cannot be suggestions. The suggestion is used only when the index returned no page.

**Why a heap for keywords and mergesort for weights?**
`KeywordRanker` needs the highest occurrence count first. `BinaryHeap` is that order. `WeightedMergeSort` needs a full order of a scored list, with a stable tie break on the name, and the comparison is the weighted score from `WeightModel`. A heap would not show the whole ranking. Mergesort does. Lower tuition and fewer months raise the score. A higher offer rate raises it. If offers or applications is missing, that weight is removed and the remaining weights are divided by their sum so they still add to one. The same rule applies when tuition or time is missing.

**What does fit change that the weight sort does not?**
`ProgramFit` removes programs before the sort. A program over the tuition cap or over the month cap is not ranked. A program with no parseable tuition or no completion time is not ranked, because the cap cannot be checked. The survivors go through the same mergesort. The sentence on each row is the comparison that kept it: the tuition, the months, the caps, and the offer rate when both counts exist.

**Where are the two regular-expression features, and why are they split?**
`FieldValidator` checks a cell: tuition with thousands separators, an English month and a year, or a program id such as `VGMMSC-CA`. An empty tuition or date is allowed. An empty program id is not. `PatternFinder` compiles a different expression and runs it on page text. It refuses a pattern that is too long or that repeats a group with a quantifier, including `(a+)+`, before `Pattern.compile`. Validation never calls that finder.

**What happens if ChromeDriver is missing, or one program page fails?**
`DriverConfig` reads `webdriver.chrome.driver`, then `CHROMEDRIVER`, then `CHROME_DRIVER_PATH`. A blank value means there is no driver. `Main` prints how to set one and returns. It does not use a machine-specific path. In `DetailScraper`, one page that fails is logged and skipped. If the browser session dies, the detail pass stops and the CSV already collected is still written.

**How does a student avoid seeing someone else's application?**
`ApplicationStore.forStudent` filters on the signed-in name. The student page and `/api/applications` use that list. `/admin` and `/api/admin/applications` require the admin role. A decision is stored as pending, accepted, or rejected, with the program id, the student, the details, and the time. The student page asks for its own rows on a timer, so an accept shows up on that page.
