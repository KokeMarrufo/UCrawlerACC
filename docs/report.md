# Graduate program finder

## 1. Group, members, and variant

The variant is graduate programs at Canadian universities.

The group name is not written in this repository. It is not invented here. The member whose Java files are in this repository is Koke Marrufo. UBC is the crawled site. The other four universities are slots with the same columns. A teammate adds `data/slot-2.csv`, `data/slot-3.csv`, `data/slot-4.csv`, or `data/slot-5.csv`. A missing file stays empty. No program row is invented.

Task, copied from the project variant: search graduate programs, with a crawler, an HTML parser, spell checking, word completion, frequency count, search frequency, page ranking, inverted indexing, data validation with regular expressions, and finding patterns with regular expressions.

## 2. Contribution and Java files

Koke Marrufo, this repository:

| Piece | Files |
|---|---|
| Crawler | `finder.crawl.Main`, `PageInteractions`, `DetailScraper`, `CsvWriter`, `Program`, `DriverConfig`, `PageStore`, `CatalogLoader`, `CsvRows` |
| HTML parser | `finder.text.HtmlParser`, `Tokens` |
| Inverted index and frequency | `finder.index.InvertedIndex`, `Posting`, `FrequencyCount` |
| Search frequency | `finder.index.SearchFrequency` |
| Vocabulary, completion, spelling | `finder.text.AvlTree`, `Vocabulary`, `WordCompletion`, `EditDistance`, `SpellChecker` |
| Keyword rank | `finder.rank.BinaryHeap`, `KeywordRanker` |
| Weighted rank and fit | `finder.rank.WeightModel`, `WeightedMergeSort`, `ProgramFit` |
| Regular expressions | `finder.regex.FieldValidator`, `PatternFinder` |
| Universities | `finder.universities.Corpus`, `SearchDocument`, `UniversitySlots` |
| Timing | `finder.analysis.IndexBenchmark` |
| Portal shell | `finder.portal.PortalServer`, `SearchService`, `HtmlPages`, `AuthStore`, `ApplicationStore`, `Web` |
| Tests | `finder.FeatureTests`, `finder.PortalFlowTest` |

Other members have not added crawlers or rows. Their slot is the CSV file above. Portal handlers call the classes. They are not claimed as a separate feature.

## 3. Additional features

- Weighted comparison of first-year tuition, minimum completion time, and offer rate. Lower cost and shorter time rank higher. A higher offer rate ranks higher. A missing criterion is dropped and the other weights are renormalized.
- Fit a budget: a tuition cap and a month cap remove programs that do not fit, then mergesort orders the rest and each row says why it was kept.
- Student and admin pages: register, salted passwords, applications with status pending, accepted, or rejected, and a student list that polls for a decision.
- A timing button that compares one full scan with the index, only when asked.

## 4. Data structures and algorithms

- Hash map: inverted index, and the saved search counts.
- AVL tree: vocabulary, used for prefix completion and as the dictionary for spelling.
- Edit distance, with a length prune: spelling suggestions at distance 2.
- Binary heap: keyword ranking. More occurrences come first.
- Mergesort: weighted order, including the fit list and a blank search that ranks one university.
- Regular expressions: one checker for tuition, dates, and program ids; a separate finder over page text. A pattern that can lock up is refused first.
- Array list and linked hash map: the crawler's program list and one program's columns, as in the original extractor.

## 5. How to see each feature

The portal is the live demo. Sign in as `student` / `student` or `admin` / `admin`.

| Feature | Where |
|---|---|
| Crawler output | `data/ubc_programs.csv`, 61 rows. A new crawl also writes `data/pages/`. |
| Search and heap rank | Student page, words filled in. |
| Weights and mergesort | Student page, weights on. A blank word box ranks the whole university. |
| Fit | Student page, Fit a budget. Domestic cap `10000` and `24` months keeps 37 of the 61 UBC rows. |
| Completion | Type a prefix in the word box. |
| Spelling | A word with no hits, such as `astronmy`. |
| Frequency | Word count on one URL. |
| Search frequency | Words searched before, under the tools. |
| Pattern search | Pattern box. `VGMMSC-[A-Z]+` runs. A nested plus is refused. |
| Validation | Validation page. |
| Applications | Apply, then admin accept or reject. The student table updates. |
| Index versus scan | Time the word science. |
| Empty university | University slot 2. |

Saved page files are not in the seed. Search uses the extracted cells until a crawl writes them. Those cells are not a composed description.

## 6. References

- Course notes on algorithm analysis, search trees, and sorting.
- UBC graduate degree programs: https://www.grad.ubc.ca/prospective-students/graduate-degree-programs
- Selenium WebDriver, live crawl only.
