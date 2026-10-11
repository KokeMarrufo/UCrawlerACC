package finder.rank;

import finder.crawl.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weighted score for one set of programs.
 *
 * score = wc' * costScore + wt' * timeScore + wa' * admitScore
 * costScore = (maxCost - cost) / (maxCost - minCost)
 * timeScore = (maxMonths - months) / (maxMonths - minMonths)
 * admitScore = (rate - minRate) / (maxRate - minRate)
 * rate = offers / applications
 *
 * Lower first-year tuition and a shorter minimum completion time rank higher.
 * A higher offer rate ranks higher.
 * If a criterion is missing for a program, that weight is dropped and the remaining
 * weights are renormalized: w' = w / sum of the weights still used.
 */
public final class WeightModel {
    public static final String FORMULA = """
            score = wc' * costScore + wt' * timeScore + wa' * admitScore
            costScore = (maxCost - cost) / (maxCost - minCost)
            timeScore = (maxMonths - months) / (maxMonths - minMonths)
            admitScore = (rate - minRate) / (maxRate - minRate)
            rate = offers / applications
            cost is first-year tuition, domestic or international.
            time is the minimum completion time in months.
            Lower cost and shorter time rank higher. A higher offer rate ranks higher.
            If offers or applications is missing, admission is dropped and the other weights are renormalized.
            The same drop-and-renormalize rule applies when tuition or completion time is missing.""";

    private static final Pattern YEARS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*years?");
    private static final Pattern MONEY = Pattern.compile("^\\$?(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?)$");

    private WeightModel() {}

    public static List<ScoredProgram> score(List<Program> programs, Weights weights) {
        List<Program> list = programs == null ? List.of() : programs;
        Weights used = weights == null ? new Weights(1, 1, 1, false) : weights;
        double[] costs = new double[list.size()];
        double[] months = new double[list.size()];
        double[] rates = new double[list.size()];
        boolean[] hasCost = new boolean[list.size()];
        boolean[] hasTime = new boolean[list.size()];
        boolean[] hasAdmit = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Program program = list.get(i);
            Double cost = parseMoney(firstYearTuition(program, used.international()));
            Double time = parseMonths(program.getDetail("Min Time to Completion"));
            Double rate = offerRate(program);
            if (cost != null) {
                costs[i] = cost;
                hasCost[i] = true;
            }
            if (time != null) {
                months[i] = time;
                hasTime[i] = true;
            }
            if (rate != null) {
                rates[i] = rate;
                hasAdmit[i] = true;
            }
        }
        Double minCost = min(costs, hasCost);
        Double maxCost = max(costs, hasCost);
        Double minTime = min(months, hasTime);
        Double maxTime = max(months, hasTime);
        Double minRate = min(rates, hasAdmit);
        Double maxRate = max(rates, hasAdmit);

        List<ScoredProgram> scored = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            boolean useCost = hasCost[i] && used.cost() > 0;
            boolean useTime = hasTime[i] && used.time() > 0;
            boolean useAdmit = hasAdmit[i] && used.admission() > 0;
            double weightSum = (useCost ? used.cost() : 0) + (useTime ? used.time() : 0) + (useAdmit ? used.admission() : 0);
            double costWeight = weightSum == 0 || !useCost ? 0 : used.cost() / weightSum;
            double timeWeight = weightSum == 0 || !useTime ? 0 : used.time() / weightSum;
            double admitWeight = weightSum == 0 || !useAdmit ? 0 : used.admission() / weightSum;
            double costScore = useCost ? normalizeLowerBetter(costs[i], minCost, maxCost) : 0;
            double timeScore = useTime ? normalizeLowerBetter(months[i], minTime, maxTime) : 0;
            double admitScore = useAdmit ? normalizeHigherBetter(rates[i], minRate, maxRate) : 0;
            double total = costWeight * costScore + timeWeight * timeScore + admitWeight * admitScore;
            if (weightSum == 0) {
                total = Double.NEGATIVE_INFINITY;
            }
            Breakdown breakdown = new Breakdown(
                    hasCost[i] ? costs[i] : null,
                    hasTime[i] ? months[i] : null,
                    hasAdmit[i] ? rates[i] : null,
                    costWeight,
                    timeWeight,
                    admitWeight,
                    costScore,
                    timeScore,
                    admitScore,
                    !useAdmit,
                    !useCost,
                    !useTime);
            scored.add(new ScoredProgram(list.get(i), total, breakdown));
        }
        return scored;
    }

    public static String firstYearTuition(Program program, boolean international) {
        if (program == null) {
            return "";
        }
        String column = international ? "Tuition International (First Year)" : "Tuition Domestic (First Year)";
        return program.getDetail(column);
    }

    public static Double parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = MONEY.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Double parseMonths(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = YEARS.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!matcher.find()) {
            return null;
        }
        return Double.parseDouble(matcher.group(1)) * 12.0;
    }

    public static Double offerRate(Program program) {
        if (program == null) {
            return null;
        }
        String offers = program.getDetail("Offers");
        String applications = program.getDetail("Applications");
        if (offers.isBlank() || applications.isBlank()) {
            return null;
        }
        try {
            double offerCount = Double.parseDouble(offers.replace(",", ""));
            double applicationCount = Double.parseDouble(applications.replace(",", ""));
            if (applicationCount <= 0) {
                return null;
            }
            return offerCount / applicationCount;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double normalizeLowerBetter(double value, Double min, Double max) {
        if (min == null || max == null || Double.compare(max, min) == 0) {
            return 1.0;
        }
        return (max - value) / (max - min);
    }

    private static double normalizeHigherBetter(double value, Double min, Double max) {
        if (min == null || max == null || Double.compare(max, min) == 0) {
            return 1.0;
        }
        return (value - min) / (max - min);
    }

    private static Double min(double[] values, boolean[] present) {
        Double best = null;
        for (int i = 0; i < values.length; i++) {
            if (present[i] && (best == null || values[i] < best)) {
                best = values[i];
            }
        }
        return best;
    }

    private static Double max(double[] values, boolean[] present) {
        Double best = null;
        for (int i = 0; i < values.length; i++) {
            if (present[i] && (best == null || values[i] > best)) {
                best = values[i];
            }
        }
        return best;
    }

    public record Weights(double cost, double time, double admission, boolean international) {
        public boolean anyPositive() {
            return cost > 0 || time > 0 || admission > 0;
        }
    }

    public record Breakdown(
            Double cost,
            Double months,
            Double offerRate,
            double costWeight,
            double timeWeight,
            double admitWeight,
            double costScore,
            double timeScore,
            double admitScore,
            boolean admissionDropped,
            boolean costDropped,
            boolean timeDropped) {

        public String describe() {
            StringBuilder text = new StringBuilder();
            text.append(String.format(Locale.US, "cost weight %.2f", costWeight));
            if (costDropped) {
                text.append(" (dropped)");
            }
            text.append(String.format(Locale.US, ", time weight %.2f", timeWeight));
            if (timeDropped) {
                text.append(" (dropped)");
            }
            text.append(String.format(Locale.US, ", admission weight %.2f", admitWeight));
            if (admissionDropped) {
                text.append(" (dropped)");
            }
            return text.toString();
        }
    }

    public record ScoredProgram(Program program, double score, Breakdown breakdown) {}
}
