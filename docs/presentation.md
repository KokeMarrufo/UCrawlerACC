# Presentation

Variant: graduate programs at Canadian universities.

The group name is not written in this repository, so it is not invented here. The member whose Java files are in this repository is Koke Marrufo. UBC is the crawled university. The other four universities stay empty until a teammate adds `data/slot-2.csv`, `data/slot-3.csv`, `data/slot-4.csv`, or `data/slot-5.csv`.

Spoken pace used for the timing check: 140 words per minute. The spoken script is the block below. The live demo is included in those words.

<!-- SPOKEN-START -->
This is the graduate program finder. The variant is graduate programs at Canadian universities. The member whose files are in this repository is Koke Marrufo. The group name is not in the source, so I will not invent one or invent teammates. UBC is the university that was crawled. Four other universities use the same columns. Their files are slot-2.csv through slot-5.csv. If a file is missing, that slot stays empty.

I own the crawler and the search features that sit on its output. The crawler classes are Main, PageInteractions, DetailScraper, CsvWriter, and Program, plus DriverConfig, PageStore, CatalogLoader, and CsvRows, all in finder.crawl. The HTML parser is HtmlParser. The index is InvertedIndex. The same program also runs the rest of the checklist, so one demo can show every feature. AvlTree and WordCompletion do prefixes. EditDistance and SpellChecker do suggestions. FrequencyCount and SearchFrequency do the two counts. BinaryHeap and KeywordRanker order pages by keyword hits. WeightedMergeSort and WeightModel order by cost, time, and admission. FieldValidator and PatternFinder are the two regular-expression features. The extra feature, the one that is not on the checklist, is ProgramFit. The portal classes only call these. The pages are the window for the demo. A window by itself is not a graded feature.

The crawler opens the UBC graduate listing, selects Faculty of Science or Faculty of Applied Science, then Master's or Doctoral, and walks each results page. Doctoral is the PhD level on that dropdown. There is no label spelled PhD. ChromeDriver comes from a property or an environment variable. The old hardcoded path is gone. If the driver is missing, the crawl stops and says how to set it. It does not open a browser. Each program page is saved as HTML and as visible text. One failed page is logged and skipped. The CSV is still written.

The parser reads a saved file with no browser. It drops scripts, styles, and comments, and it returns the visible text and the URL. Until a crawl has saved those files, search indexes the extracted CSV cells. Those cells are real. They are not a written description.

The inverted index is a hash map from a word to the pages and the positions where that word occurs. A search looks the word up. It does not read every file. That is the point of an index in the analysis chapter: the cost follows the hits, not the size of every page. Frequency count asks that map how many positions sit on one URL. An unknown URL is an error. A known URL with no hits is zero.

Keyword rank uses a binary heap. The page where the query words occur more times comes out first. Ties break by program name. That is the ranking the brief asked for. A heap fits because we need the highest count next, not a fully sorted copy of the map on every step of the build.

Word completion and spell check share one vocabulary, stored in an AVL tree, because both need the words in order. A hash set would answer "is this word present" and would not walk a prefix. Completion walks the tree. If the index returns nothing, spell check suggests words within edit distance 2. A word whose length is already more than 2 away is skipped, so that comparison does not run on the whole dictionary.

Search frequency writes each searched word and its count to a file, so the history is still there after a restart. A blank box and a one-letter box are not searches, and they are not recorded. A word is at least two letters.

Weights are a second order, not a replacement for the heap. The score is a weighted sum of three pieces. Cost is first-year tuition, domestic or international, and a lower number scores higher. Time is the minimum completion time in months, and shorter scores higher. Admission is offers divided by applications, and a higher rate scores higher. If a number is missing, that weight is dropped and the other weights are scaled so they still add up to one. The order is mergesort. Equal scores keep the earlier name. Leave the word box blank, turn the weights on, and the same mergesort orders every program at that university. The formula is printed on the page.

Fit a budget is the extra, and it is the part a student would actually use. You type the most you can pay in the first year and the most months you can spend. Any program over either cap is removed before the sort. A program with no tuition or no completion time is removed, because a fit cannot be promised. Mergesort then orders what remains, with the same formula, and each row says why it was kept. On the 61 saved UBC rows, a domestic cap of 10,000 and a cap of 24 months keeps 37 programs. The cheapest first-year domestic tuition in that file is 5,738.52.

Regular expressions are two features, and they are not the same method. Validation checks the shape of tuition, dates, and program ids such as VGMMSC-CA. Pattern search is a different action over the page text. A pattern that can lock up, such as a group followed by a plus, is refused before it runs. VGMMSC followed by a character class still works.

The student and admin pages are the demo shell. Passwords are salted hashes in a local file. Seeded accounts are student and admin. A student can register. A student sees only their own applications. An admin sees all of them and can accept or reject. The student page polls, so the decision shows up without a manual reload.

I will sign in as student. I will search the word astronomy and show the heap order. I will misspell a word and show the suggestion from edit distance. I will clear the box, turn the weights on, and show the formula beside the full list. I will open Fit a budget, enter 10000 and 24, and show that the rows that remain are inside both caps. I will count one word on one URL. I will run the pattern for a program id, then a pattern that is refused. I will open validation and show that the saved cells pass. I will submit one application, accept it as admin, and show the student list update. I will press the timing button once. The full scan and the index must report the same count, and the index must be the faster of the two. I will open university slot 2 and show that no crawler has been added, and the file name it is waiting for.

The structures are each tied to one job. A hash map holds the index. An AVL tree holds the vocabulary. A binary heap ranks keywords. Mergesort ranks cost, time, and admission. Edit distance checks spelling. References are the course chapters on algorithm analysis, search trees, and sorting, the UBC graduate program listing, and Selenium, which is used only by the live crawl.
<!-- SPOKEN-END -->

## What the demo clicks

1. Sign in as `student` / `student` at the portal.
2. Search `astronomy`.
3. Search a misspelling such as `astronmy` and read the suggestion.
4. Clear the words, turn weights on, and read the formula.
5. Fit a budget: tuition cap `10000`, month cap `24`, domestic.
6. Count one word on one program URL.
7. Pattern `VGMMSC-[A-Z]+`, then a pattern the page refuses.
8. Open validation.
9. Submit an application, accept it as `admin` / `admin`, return to the student list.
10. Press "Time the word science" once.
11. Open university slot 2.

## References

- Course notes: algorithm analysis, search trees (AVL), sorting (mergesort).
- UBC graduate degree programs listing: https://www.grad.ubc.ca/prospective-students/graduate-degree-programs
- Selenium WebDriver, used only by `finder.crawl.Main`.
