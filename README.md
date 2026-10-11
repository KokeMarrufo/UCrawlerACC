# Graduate Program Finder

Java back end for searching graduate programs. The UBC crawler is the one that already ran for Assignment 1. Search, ranking, spell check, and the student and admin pages work offline from `data/ubc_programs.csv`. Four other university slots stay empty until a teammate adds `data/slot-2.csv` (and slot-3, slot-4, slot-5) with the same columns. There is no crawler for them, and no invented programs.

A window is not required. The portal is a small JDK web server so a demo can log in, search, apply, and accept or reject.

## What you need

- JDK 21
- Maven
- VS Code with the Extension Pack for Java, if you want the launch configs in `.vscode/`
- Chrome and ChromeDriver only for a live crawl. The portal and `mvn test` do not open a browser.

## Run the portal in VS Code

1. Open this folder.
2. Wait until Java finishes importing the Maven project.
3. Run the launch config **Graduate Program Finder portal**.

It listens on `0.0.0.0:8747`. Open [http://127.0.0.1:8747](http://127.0.0.1:8747).

Demo accounts: `student` / `student` and `admin` / `admin`. Students can register. Passwords are stored as salted SHA-256 hashes in `data/users.tsv`.

## Run the portal with Maven

```bash
mvn -q exec:java
```

Other mains:

```bash
mvn -q exec:java -Dexec.mainClass=finder.analysis.IndexBenchmark
mvn -q exec:java -Dexec.mainClass=finder.crawl.Main -Dwebdriver.chrome.driver=/path/to/chromedriver
```

`CHROMEDRIVER` or `CHROME_DRIVER_PATH` works instead of the system property. There is no hardcoded driver path.

`PORTAL_PORT` or `-Dportal.port=8747` changes the port. Do not use 3000, 5173, or 8080 if other tools are already bound there.

## Tests

```bash
mvn test
```

Tests use HTML fixtures and the checked-in CSV. They do not contact UBC.

## Data

- `data/ubc_programs.csv` is the 61-row UBC extract. A live crawl overwrites it and writes `data/pages/<program-id>.html` and `.txt`.
- Until those page files exist, search indexes the CSV cells (name, faculty, tuition, dates, and the other extracted columns). That is not a written program description.
- `data/applications.csv` stores program id, student, details, status (`pending`, `accepted`, or `rejected`), and time.
- `data/search-frequency.tsv` counts words that have been searched.

## What the pages do

- Search uses the hash inverted index, then a binary heap ordered by how often the query words occur. Leave the box blank and turn weights on to order every program in the selected university.
- The analysis section can time one full scan against the index. That measurement runs only when asked.
- If nothing matches, spell check suggests vocabulary words within edit distance 2. Completion walks the same AVL tree while you type.
- Optional weights re-rank the matches with mergesort. Cost is first-year tuition (domestic or international). Time is the minimum completion time in months. Admission is offers divided by applications when both numbers exist. Lower cost and shorter time rank higher. A higher offer rate ranks higher. If a number is missing, that criterion is dropped and the other weights are renormalized. The formula is on the student page.
- Fit a budget: a tuition cap and a month cap drop programs that do not fit. Mergesort orders the rest. Programs missing tuition or completion time are left out.
- Frequency count is per word per URL. Pattern search is separate from the tuition, date, and program-id checks (for example `VGMMSC-CA`).
- A student sees only their applications. The list polls every few seconds. An admin sees every application and can accept or reject.

## Live crawl

`finder.crawl.Main` still filters Faculty of Science and Faculty of Applied Science. The degree dropdown on the UBC listing exposes Master's and Doctoral. Doctoral is the PhD level; the page does not have a label spelled "PhD". Each detail page is saved under `data/pages/`, including the about / overview block when the page has one. Column names are unchanged. Missing cells stay empty strings.

## Layout

- `finder.crawl` crawler, CSV load, page files
- `finder.text` HTML parser, AVL vocabulary, completion, spell check
- `finder.index` inverted index, per-URL frequency, search history
- `finder.rank` binary heap and weighted mergesort
- `finder.regex` cell validation and pattern search
- `finder.analysis` full-scan versus index timings
- `finder.universities` UBC plus four empty slots
- `finder.portal` sign-in, applications, and pages

Eclipse can import the same Maven project. This repo is edited in VS Code.
