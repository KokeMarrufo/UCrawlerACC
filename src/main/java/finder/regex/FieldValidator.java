package finder.regex;

import finder.crawl.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks structured CSV cells. This is not the page-text pattern search.
 * Empty tuition and empty dates are missing data, not failures.
 * An empty program id is a failure.
 */
public final class FieldValidator {
    public static final String TUITION = "^\\$?\\d{1,3}(,\\d{3})*(\\.\\d{2})?$";
    public static final String DATE =
            "^(?:(?:0?[1-9]|[12]\\d|3[01]) )?(January|February|March|April|May|June|July|August|September|October|November|December) \\d{4}$";
    public static final String PROGRAM_ID = "^[A-Z][A-Z0-9]{3,11}(-[A-Z0-9]{1,4})?$";

    public static final List<String> TUITION_COLUMNS = List.of(
            "Tuition Domestic (First Year)",
            "Tuition International (First Year)",
            "Tuition per Year Domestic",
            "Tuition per Year International",
            "Tuition per Credit Domestic",
            "Tuition per Credit International");

    public static final List<String> DATE_COLUMNS = List.of(
            "Intake",
            "Application Open Date",
            "Canadian Application Deadline",
            "Canadian Transcript Deadline",
            "Canadian Referee Deadline",
            "International Application Deadline",
            "International Transcript Deadline",
            "International Referee Deadline");

    private static final Pattern TUITION_PATTERN = Pattern.compile(TUITION);
    private static final Pattern DATE_PATTERN = Pattern.compile(DATE);
    private static final Pattern PROGRAM_ID_PATTERN = Pattern.compile(PROGRAM_ID);

    private FieldValidator() {}

    public static boolean validTuition(String value) {
        return value != null && (value.isEmpty() || TUITION_PATTERN.matcher(value).matches());
    }

    public static boolean validDate(String value) {
        return value != null && (value.isEmpty() || DATE_PATTERN.matcher(value).matches());
    }

    public static boolean validProgramId(String value) {
        return value != null && PROGRAM_ID_PATTERN.matcher(value).matches();
    }

    public static Check check(String kind, String value) {
        String cell = value == null ? "" : value;
        return switch (kind == null ? "" : kind) {
            case "tuition" -> new Check(kind, cell, validTuition(cell),
                    cell.isEmpty() ? "Empty tuition is allowed." : (validTuition(cell) ? "Matches the tuition pattern." : "Does not match the tuition pattern."));
            case "date" -> new Check(kind, cell, validDate(cell),
                    cell.isEmpty() ? "Empty date is allowed." : (validDate(cell) ? "Matches the date pattern." : "Does not match the date pattern."));
            case "programId" -> new Check(kind, cell, validProgramId(cell),
                    validProgramId(cell) ? "Matches the program id pattern." : "Does not match the program id pattern. Example: VGMMSC-CA.");
            default -> new Check(kind == null ? "" : kind, cell, false, "Choose tuition, date, or program id.");
        };
    }

    public static Report validate(List<Program> programs) {
        List<Failure> failures = new ArrayList<>();
        int checked = 0;
        if (programs != null) {
            for (Program program : programs) {
                String id = program.getDetail("Program ID");
                checked++;
                if (!validProgramId(id)) {
                    failures.add(new Failure(program.getName(), "Program ID", id, "program id"));
                }
                for (String column : TUITION_COLUMNS) {
                    String value = program.getDetail(column);
                    if (value.isEmpty()) {
                        continue;
                    }
                    checked++;
                    if (!validTuition(value)) {
                        failures.add(new Failure(program.getName(), column, value, "tuition"));
                    }
                }
                for (String column : DATE_COLUMNS) {
                    String value = program.getDetail(column);
                    if (value.isEmpty()) {
                        continue;
                    }
                    checked++;
                    if (!validDate(value)) {
                        failures.add(new Failure(program.getName(), column, value, "date"));
                    }
                }
            }
        }
        return new Report(checked, failures);
    }

    public record Failure(String programName, String column, String value, String kind) {}

    public record Report(int cellsChecked, List<Failure> failures) {}

    public record Check(String kind, String value, boolean valid, String message) {}
}
