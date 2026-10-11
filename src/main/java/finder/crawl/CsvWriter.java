package finder.crawl;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Saves the scraped programs to a CSV file that opens in Excel.
public class CsvWriter {

    public static void save(List<Program> programs, String filePath) throws IOException {
        Path path = Paths.get(filePath);

        // Create the folder (e.g. "output") if it doesn't exist yet
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            // BOM so Excel reads the file as UTF-8 (accents, en dashes, etc.)
            out.print("\uFEFF");

            // Header row: the 4 listing columns, then every detail column
            List<String> header = new ArrayList<>(Arrays.asList("Specialization", "Program Name", "Faculty", "URL"));
            header.addAll(DetailScraper.COLUMNS);
            out.println(toCsvLine(header));

            // One row per program
            for (Program p : programs) {
                List<String> row = new ArrayList<>(Arrays.asList(
                        p.getSpecialization(), p.getName(), p.getFaculty(), p.getUrl()));
                for (String column : DetailScraper.COLUMNS) {
                    row.add(p.getDetail(column));
                }
                out.println(toCsvLine(row));
            }
        }

        System.out.println("Saved " + programs.size() + " programs to " + path.toAbsolutePath());
    }

    // Joins values into one CSV line, quoting each one.
    private static String toCsvLine(List<String> values) {
        List<String> quoted = new ArrayList<>();
        for (String v : values) {
            quoted.add(csv(v));
        }
        return String.join(",", quoted);
    }

    /**
     * Wraps a value in quotes and escapes inner quotes, so commas inside
     * program names (e.g. "Ancient Culture, Religion and Ethnicity")
     * don't break the columns.
     */
    private static String csv(String value) {
        if (value == null) value = "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
