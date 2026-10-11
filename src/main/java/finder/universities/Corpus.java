package finder.universities;

import finder.crawl.CatalogLoader;
import finder.crawl.DetailScraper;
import finder.crawl.PageStore;
import finder.crawl.Program;
import finder.index.InvertedIndex;
import finder.text.HtmlParser;
import finder.text.Tokens;
import finder.text.Vocabulary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline catalog. Saved HTML is parsed when a crawl has left a file.
 * Otherwise the document text is the CSV fields that were actually extracted.
 * Those fields are not a substitute description of the program page.
 */
public final class Corpus {
    private final List<UniversitySlots.UniversitySlot> slots;
    private final List<Program> programs;
    private final List<SearchDocument> documents;
    private final InvertedIndex index = new InvertedIndex();
    private final Vocabulary vocabulary = new Vocabulary();
    private final Map<String, Program> byUrl = new LinkedHashMap<>();
    private final Map<String, Program> byId = new LinkedHashMap<>();
    private final Map<String, SearchDocument> documentByUrl = new LinkedHashMap<>();

    private Corpus(List<UniversitySlots.UniversitySlot> slots, List<Program> programs, List<SearchDocument> documents) {
        this.slots = List.copyOf(slots);
        this.programs = List.copyOf(programs);
        this.documents = List.copyOf(documents);
        for (Program program : this.programs) {
            if (!program.getUrl().isBlank()) {
                byUrl.putIfAbsent(program.getUrl(), program);
            }
            String id = program.getDetail("Program ID");
            if (!id.isBlank()) {
                byId.putIfAbsent(id, program);
            }
        }
        for (SearchDocument document : this.documents) {
            if (!document.url().isBlank()) {
                documentByUrl.putIfAbsent(document.url(), document);
            }
            index.addDocument(document.url(), document.text());
            vocabulary.addText(document.text());
        }
    }

    public static Corpus load(Path dataDir) throws IOException {
        List<UniversitySlots.UniversitySlot> slots = UniversitySlots.all();
        List<Program> programs = new ArrayList<>();
        List<SearchDocument> documents = new ArrayList<>();
        for (UniversitySlots.UniversitySlot slot : slots) {
            if (!slot.crawlerPresent() || slot.csvFileName() == null) {
                continue;
            }
            Path csv = dataDir.resolve(slot.csvFileName());
            if (Files.notExists(csv)) {
                System.out.println("No catalog file for " + slot.name() + " at " + csv);
                continue;
            }
            for (Program program : CatalogLoader.load(csv)) {
                programs.add(program);
                documents.add(documentFor(dataDir, slot, program));
            }
        }
        return new Corpus(slots, programs, documents);
    }

    public static Corpus of(List<UniversitySlots.UniversitySlot> slots, List<Program> programs, List<SearchDocument> documents) {
        return new Corpus(
                slots == null ? List.of() : slots,
                programs == null ? List.of() : programs,
                documents == null ? List.of() : documents);
    }

    public static SearchDocument documentFor(Path dataDir, UniversitySlots.UniversitySlot slot, Program program) throws IOException {
        String id = PageStore.sanitize(program.getDetail("Program ID"));
        Path html = dataDir.resolve("pages").resolve(id + ".html");
        Path text = dataDir.resolve("pages").resolve(id + ".txt");
        if (Files.isRegularFile(html)) {
            HtmlParser.ParsedPage parsed = HtmlParser.read(html);
            String url = parsed.url().isBlank() ? program.getUrl() : parsed.url();
            return new SearchDocument(slot.id(), program.getDetail("Program ID"), program.getName(), url, parsed.text(), true);
        }
        if (Files.isRegularFile(text)) {
            String body = Files.readString(text, StandardCharsets.UTF_8);
            return new SearchDocument(slot.id(), program.getDetail("Program ID"), program.getName(), program.getUrl(), body, true);
        }
        return new SearchDocument(
                slot.id(),
                program.getDetail("Program ID"),
                program.getName(),
                program.getUrl(),
                textFromFields(program),
                false);
    }

    public static String textFromFields(Program program) {
        StringBuilder text = new StringBuilder();
        text.append(program.getSpecialization()).append('\n');
        text.append(program.getName()).append('\n');
        text.append(program.getFaculty()).append('\n');
        for (String column : DetailScraper.COLUMNS) {
            String value = program.getDetail(column);
            if (!value.isEmpty()) {
                text.append(column).append(' ').append(value).append('\n');
            }
        }
        return text.toString();
    }

    public static int scanCount(List<SearchDocument> documents, String word) {
        if (documents == null || word == null || word.isBlank()) {
            return 0;
        }
        String needle = word.toLowerCase(java.util.Locale.ROOT);
        int count = 0;
        for (SearchDocument document : documents) {
            for (String token : Tokens.words(document.text())) {
                if (needle.equals(token)) {
                    count++;
                }
            }
        }
        return count;
    }

    public List<UniversitySlots.UniversitySlot> slots() {
        return slots;
    }

    public List<Program> programs() {
        return programs;
    }

    public List<SearchDocument> documents() {
        return documents;
    }

    public InvertedIndex index() {
        return index;
    }

    public Vocabulary vocabulary() {
        return vocabulary;
    }

    public Program programByUrl(String url) {
        return byUrl.get(url);
    }

    public Program programById(String id) {
        return id == null ? null : byId.get(id);
    }

    public SearchDocument documentByUrl(String url) {
        return documentByUrl.get(url);
    }

    public List<Program> programsForUniversity(String universityId) {
        if (universityId == null || universityId.isBlank()) {
            return programs;
        }
        List<Program> found = new ArrayList<>();
        for (SearchDocument document : documents) {
            if (!universityId.equals(document.universityId())) {
                continue;
            }
            Program program = byUrl.get(document.url());
            if (program != null) {
                found.add(program);
            }
        }
        return found;
    }

    public Map<String, String> namesByUrl() {
        Map<String, String> names = new LinkedHashMap<>();
        for (SearchDocument document : documents) {
            names.put(document.url(), document.name());
        }
        return names;
    }
}
