package finder.universities;

import finder.crawl.DetailScraper;

import java.util.ArrayList;
import java.util.List;

/**
 * UBC plus four unassigned universities.
 * The empty slots use the same columns. They do not include a crawler or invented programs.
 * A teammate can add data/slot-2.csv (and slot-3, slot-4, slot-5) with those columns.
 * The portal loads that file on startup. Until the file exists, the slot stays empty.
 */
public final class UniversitySlots {
    private UniversitySlots() {}

    public static List<UniversitySlot> all() {
        return List.of(
                new UniversitySlot("ubc", "University of British Columbia", "ubc_programs.csv", true),
                new UniversitySlot("slot-2", "University slot 2", "slot-2.csv", false),
                new UniversitySlot("slot-3", "University slot 3", "slot-3.csv", false),
                new UniversitySlot("slot-4", "University slot 4", "slot-4.csv", false),
                new UniversitySlot("slot-5", "University slot 5", "slot-5.csv", false));
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
