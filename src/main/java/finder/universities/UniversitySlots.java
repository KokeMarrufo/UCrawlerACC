package finder.universities;

import finder.crawl.DetailScraper;

import java.util.ArrayList;
import java.util.List;

/**
 * UBC plus four unassigned universities.
 * The empty slots use the same columns. They do not include a crawler or invented programs.
 */
public final class UniversitySlots {
    private UniversitySlots() {}

    public static List<UniversitySlot> all() {
        return List.of(
                new UniversitySlot("ubc", "University of British Columbia", "ubc_programs.csv", true),
                new UniversitySlot("slot-2", "University slot 2", null, false),
                new UniversitySlot("slot-3", "University slot 3", null, false),
                new UniversitySlot("slot-4", "University slot 4", null, false),
                new UniversitySlot("slot-5", "University slot 5", null, false));
    }

    public static List<String> columns() {
        List<String> columns = new ArrayList<>();
        columns.add("Specialization");
        columns.add("Program Name");
        columns.add("Faculty");
        columns.add("URL");
        columns.addAll(DetailScraper.COLUMNS);
        return List.copyOf(columns);
    }

    public record UniversitySlot(String id, String name, String csvFileName, boolean crawlerPresent) {}
}
