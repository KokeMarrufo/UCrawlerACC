package finder.rank;

import finder.crawl.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A student names a first-year tuition cap and a maximum number of months.
 * Programs over either cap are dropped. Programs missing tuition or completion
 * time are dropped too, because a fit cannot be promised. The programs that
 * remain are ordered with the same weighted mergesort as the catalog rank.
 */
public final class ProgramFit {
    private ProgramFit() {}

    public record Pick(WeightModel.ScoredProgram scored, String reason) {}

    public record Outcome(String error, String summary, List<Pick> picks) {}

    public static Outcome recommend(
            List<Program> programs,
            String tuitionCapRaw,
            String monthCapRaw,
            WeightModel.Weights weights) {
        if (weights == null || !weights.anyPositive()
                || weights.cost() < 0 || weights.time() < 0 || weights.admission() < 0) {
            return new Outcome(
                    "Weights must be zero or positive, and at least one weight has to be above zero.",
                    "",
                    List.of());
        }
        Double tuitionCap = parsePositive(tuitionCapRaw);
        Double monthCap = parsePositive(monthCapRaw);
        if (tuitionCap == null) {
            return new Outcome("Tuition cap has to be a positive amount, such as 10000 or 10,000.00.", "", List.of());
        }
        if (monthCap == null) {
            return new Outcome("Month cap has to be a positive number of months, such as 24.", "", List.of());
        }

        List<Program> fits = new ArrayList<>();
        int skipped = 0;
        if (programs != null) {
            for (Program program : programs) {
                Double tuition = WeightModel.parseMoney(WeightModel.firstYearTuition(program, weights.international()));
                Double months = WeightModel.parseMonths(program.getDetail("Min Time to Completion"));
                if (tuition == null || months == null) {
                    skipped++;
                    continue;
                }
                if (tuition <= tuitionCap && months <= monthCap) {
                    fits.add(program);
                }
            }
        }
        if (fits.isEmpty()) {
            return new Outcome(
                    "",
                    "No program fits that tuition cap and month cap. "
                            + skipped + " program(s) were skipped because tuition or completion time is missing.",
                    List.of());
        }
        List<Pick> picks = new ArrayList<>();
        for (WeightModel.ScoredProgram scored : WeightedMergeSort.rank(fits, weights)) {
            picks.add(new Pick(scored, reason(scored.program(), weights.international(), tuitionCap, monthCap)));
        }
        String summary = picks.size() + " program(s) fit. Mergesort orders them with the weights. "
                + skipped + " program(s) were skipped because tuition or completion time is missing.";
        return new Outcome("", summary, picks);
    }

    static Double parsePositive(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().replace("$", "").replace(",", "").replace(" ", "");
        if (!trimmed.matches("\\d+(\\.\\d+)?")) {
            return null;
        }
        try {
            double value = Double.parseDouble(trimmed);
            if (value <= 0 || Double.isInfinite(value)) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String reason(Program program, boolean international, double tuitionCap, double monthCap) {
        String who = international ? "International" : "Domestic";
        String tuition = WeightModel.firstYearTuition(program, international);
        String time = program.getDetail("Min Time to Completion");
        Double rate = WeightModel.offerRate(program);
        String offer = rate == null
                ? "Offer rate is missing, so admission is dropped for this row."
                : String.format(Locale.US, "Offer rate is %.0f%%.", rate * 100.0);
        return String.format(Locale.US,
                "%s first year %s is at or under %.2f. Minimum time %s is at or under %.1f months. %s",
                who, tuition, tuitionCap, time, monthCap, offer);
    }
}
