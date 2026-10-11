package finder.crawl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One graduate program. The first four fields come from the listing table;
 * everything else is filled in later from the program's detail page.
 */
public class Program {
    // Columns from the listing table, set once when the row is read
    private final String specialization;
    private final String name;
    private final String faculty;
    private final String url;

    // Everything scraped from the detail page, keyed by CSV column name
    private final Map<String, String> details = new LinkedHashMap<>();

    public Program(String specialization, String name, String faculty, String url) {
        this.specialization = specialization == null ? "" : specialization;
        this.name = name == null ? "" : name;
        this.faculty = faculty == null ? "" : faculty;
        this.url = url == null ? "" : url;
    }

    // Getters for the listing columns
    public String getSpecialization() { return specialization; }
    public String getName() { return name; }
    public String getFaculty() { return faculty; }
    public String getUrl() { return url; }

    // Stores one detail value, e.g. setDetail("Graduation Rate", "83.3%").
    public void setDetail(String column, String value) {
        details.put(column, value == null ? "" : value);
    }

    // Returns a detail value, or "" if the page didn't have it (keeps CSV cells empty, not "null").
    public String getDetail(String column) {
        String value = details.get(column);
        return value == null ? "" : value;
    }

    // Short description used when printing a program to the console.
    @Override
    public String toString() {
        return name + " | " + faculty;
    }
}
