package finder.index;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hash map of searched words to how many times each was searched.
 * The map is rewritten to a file on every update so it survives a restart.
 */
public final class SearchFrequency {
    private final Path file;
    private final Map<String, Integer> counts = new HashMap<>();

    public SearchFrequency(Path file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        counts.clear();
        if (file == null || Files.notExists(file)) {
            return;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab <= 0) {
                continue;
            }
            String word = line.substring(0, tab).trim();
            String raw = line.substring(tab + 1).trim();
            try {
                int count = Integer.parseInt(raw);
                if (!word.isEmpty() && count > 0) {
                    counts.put(word, count);
                }
            } catch (NumberFormatException ex) {
                System.out.println("Skipping search-frequency line: " + line);
            }
        }
    }

    public synchronized void record(List<String> words) throws IOException {
        if (words == null || words.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            counts.merge(word, 1, Integer::sum);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public synchronized int count(String word) {
        if (word == null) {
            return 0;
        }
        return counts.getOrDefault(word, 0);
    }

    public synchronized List<Entry> ranking() {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        entries.sort(Comparator.comparingInt(Entry::count).reversed().thenComparing(Entry::word));
        return entries;
    }

    public synchronized void save() throws IOException {
        if (file == null) {
            return;
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        List<Entry> entries = ranking();
        StringBuilder body = new StringBuilder();
        for (Entry entry : entries) {
            body.append(entry.word()).append('\t').append(entry.count()).append('\n');
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, body.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public record Entry(String word, int count) {}
}
