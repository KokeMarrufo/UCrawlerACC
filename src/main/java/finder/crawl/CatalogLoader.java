package finder.crawl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the saved CSV with no browser and no Selenium.
 * Missing cells become empty strings.
 */
public final class CatalogLoader {
    private CatalogLoader() {}

    public static List<Program> load(Path csv) throws IOException {
        List<List<String>> rows = CsvRows.read(csv);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> header = rows.get(0);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i), i);
        }
        List<Program> programs = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Program program = new Program(
                    cell(row, columns, "Specialization"),
                    cell(row, columns, "Program Name"),
                    cell(row, columns, "Faculty"),
                    cell(row, columns, "URL"));
            for (String column : DetailScraper.COLUMNS) {
                program.setDetail(column, cell(row, columns, column));
            }
            programs.add(program);
        }
        return programs;
    }

    private static String cell(List<String> row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value;
    }
}
