package finder.crawl;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens a program's detail page and fills in the extra fields
 * on the Program object (tuition, deadlines, stats, etc.).
 *
 * Values are read with textContent instead of getText(), so content inside
 * collapsed sections is included without having to click "Open All".
 */
public class DetailScraper {

    // CSV columns filled by this class, in output order.
    public static final List<String> COLUMNS = Arrays.asList(
            // Quick Facts box
            "Academic Unit", "Mode of Delivery", "Program Components", "Program ID",
            "Tuition Domestic (First Year)", "Tuition International (First Year)", "Median Funding",
            // Stats bar under the overview
            "Minimum Funding per Year", "Enrolled Students", "Graduation Rate", "Avg Years to Completion",
            // Meet Deadlines (first intake listed)
            "Intake", "Application Open Date",
            "Canadian Application Deadline", "Canadian Transcript Deadline", "Canadian Referee Deadline",
            "International Application Deadline", "International Transcript Deadline", "International Referee Deadline",
            // Tuition table
            "Application Fee Domestic", "Application Fee International", "Installments per Year",
            "Tuition per Year Domestic", "Tuition per Year International",
            "Tuition per Credit Domestic", "Tuition per Credit International", "Student Fees (Yearly)",
            // Enrolment table (most recent year)
            "Enrolment Year", "Applications", "Offers", "New Registrations", "Total Enrolment",
            // Completion Rates and Times
            "Min Time to Completion", "Max Time to Completion");

    public static void scrape(WebDriver driver, WebDriverWait wait, Program program) {
        driver.get(program.getUrl());

        // Wait until the main title is visible, so we know the page loaded
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("h1")));

        readQuickFacts(driver, program);
        readStatsBar(driver, program);
        readDeadlines(driver, program);
        readTuitionTable(driver, program);
        readEnrolment(driver, program);
        readCompletion(driver, program);
        String about = readAbout(driver);
        try {
            String source = driver.getPageSource();
            PageStore.saveProgram(program, source == null ? "" : source, about);
        } catch (IOException e) {
            System.out.println("Could not save page text for " + program.getUrl() + ": " + e.getMessage());
        }
    }

    // Quick Facts: each fact is a title span and a content span.
    private static void readQuickFacts(WebDriver driver, Program p) {
        for (WebElement fact : driver.findElements(By.cssSelector(".quick-facts-wrapper"))) {
            String title = text(fact, ".quick-facts-linked-title");
            String value = text(fact, ".quick-facts-linked-content");

            switch (title) {
                case "Academic Unit":      p.setDetail("Academic Unit", value); break;
                case "Mode of delivery":   p.setDetail("Mode of Delivery", value); break;
                case "Program Components": p.setDetail("Program Components", value); break;
                case "Median Funding":     p.setDetail("Median Funding", value); break;
                case "Tuition (First Year)":
                    // Looks like "Domestic: 5,738.52 | International: 10,081.65"
                    p.setDetail("Tuition Domestic (First Year)", find(value, "Domestic:\\s*([\\d,.]+)"));
                    p.setDetail("Tuition International (First Year)", find(value, "International:\\s*([\\d,.]+)"));
                    break;
                default: break; // Faculty and Specialization already come from the listing table
            }
        }

        // Program Identifier sits in its own block under a heading
        List<WebElement> id = driver.findElements(By.cssSelector(".block-views-blockgps-program-fields-block-46 .field-content"));
        if (!id.isEmpty()) {
            p.setDetail("Program ID", text(id.get(0)));
        }
    }

    // The row of big numbers: funding, enrolled students, graduation rate, years.
    private static void readStatsBar(WebDriver driver, Program p) {
        for (WebElement stat : driver.findElements(By.cssSelector(".program-factoid"))) {
            String label = text(stat, ".data-subtitle");
            String value = text(stat, ".data-value");

            if (label.startsWith("Minimum funding")) p.setDetail("Minimum Funding per Year", value);
            else if (label.startsWith("Enrolled students")) p.setDetail("Enrolled Students", value);
            else if (label.startsWith("Graduation rate")) p.setDetail("Graduation Rate", value);
            else if (label.startsWith("Average years")) p.setDetail("Avg Years to Completion", value);
        }
    }

    // Meet Deadlines: each date has its own field class, so we read them by class.
    private static void readDeadlines(WebDriver driver, Program p) {
        List<WebElement> intakes = driver.findElements(By.cssSelector(".view-id-gps_sits_ipo .views-row"));
        if (intakes.isEmpty()) {
            return; // some programs have no upcoming intake configured yet
        }
        WebElement intake = intakes.get(0);

        p.setDetail("Intake", text(intake, "h4 time"));
        p.setDetail("Application Open Date", text(intake, ".views-field-ipo-beg2 .field-content"));
        p.setDetail("Canadian Application Deadline", text(intake, ".views-field-ipo-end2 .field-content"));
        p.setDetail("Canadian Transcript Deadline", text(intake, ".views-field-ipo-udf4 .field-content"));
        p.setDetail("Canadian Referee Deadline", text(intake, ".views-field-ipo-udf6 .field-content"));
        p.setDetail("International Application Deadline", text(intake, ".views-field-ipo-udf1 .field-content"));
        p.setDetail("International Transcript Deadline", text(intake, ".views-field-ipo-udf5 .field-content"));
        p.setDetail("International Referee Deadline", text(intake, ".views-field-ipo-udf7 .field-content"));
    }

    // Tuition table: label in the first cell, domestic and international in the next two.
    private static void readTuitionTable(WebDriver driver, Program p) {
        for (WebElement row : driver.findElements(By.cssSelector(".views-field-field-prog-tuition table tr"))) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() < 2) continue;

            String label = text(cells.get(0));
            String domestic = text(cells.get(1));
            String international = cells.size() > 2 ? text(cells.get(2)) : "";

            if (label.startsWith("Application Fee")) {
                p.setDetail("Application Fee Domestic", domestic);
                p.setDetail("Application Fee International", international);
            } else if (label.startsWith("Installments per year")) {
                p.setDetail("Installments per Year", domestic);
            } else if (label.startsWith("Tuition per year")) {
                p.setDetail("Tuition per Year Domestic", domestic);
                p.setDetail("Tuition per Year International", international);
            } else if (label.startsWith("Tuition per credit")) {
                // Certificates charge per credit instead of per year
                p.setDetail("Tuition per Credit Domestic", domestic);
                p.setDetail("Tuition per Credit International", international);
            } else if (label.startsWith("Student Fees")) {
                p.setDetail("Student Fees (Yearly)", domestic); // one cell spans both columns
            }
        }
    }

    // Enrolment table: we keep the first (most recent) year column.
    private static void readEnrolment(WebDriver driver, Program p) {
        List<WebElement> tables = driver.findElements(By.cssSelector(".views-field-field-prog-gear-overview-1 table"));
        if (tables.isEmpty()) {
            return; // certificates and new programs have no enrolment data
        }
        WebElement table = tables.get(0);

        List<WebElement> years = table.findElements(By.cssSelector("thead th"));
        if (years.size() > 1) {
            p.setDetail("Enrolment Year", text(years.get(1)));
        }

        for (WebElement row : table.findElements(By.cssSelector("tbody tr"))) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() < 2) continue;

            String label = text(cells.get(0));
            if (COLUMNS.contains(label)) {
                p.setDetail(label, text(cells.get(1))); // Applications, Offers, New Registrations, Total Enrolment
            }
        }
    }

    // Completion Rates and Times: the min and max are only written in a sentence.
    private static void readCompletion(WebDriver driver, Program p) {
        List<WebElement> blocks = driver.findElements(By.cssSelector(".views-field-field-prog-gear-compl-overview-1 .field-content"));
        if (blocks.isEmpty()) {
            return;
        }
        String sentence = text(blocks.get(0));
        p.setDetail("Min Time to Completion", find(sentence, "minimum time to completion is ([\\d.]+ years)"));
        p.setDetail("Max Time to Completion", find(sentence, "maximum time is ([\\d.]+ years)"));
    }

    /**
     * Overview / about block on the program page, when the listing page has one.
     * It is not a new CSV column. The saved HTML and visible text include it.
     */
    private static String readAbout(WebDriver driver) {
        List<WebElement> blocks = driver.findElements(By.cssSelector(".views-field-field-prog-overview"));
        if (blocks.isEmpty()) {
            return "";
        }
        StringBuilder about = new StringBuilder();
        for (WebElement block : blocks) {
            String value = text(block);
            if (!value.isEmpty()) {
                if (about.length() > 0) {
                    about.append('\n');
                }
                about.append(value);
            }
        }
        return about.toString();
    }

    // Text of the first element matching the selector inside parent, or "" if missing.
    private static String text(SearchContext parent, String css) {
        List<WebElement> found = parent.findElements(By.cssSelector(css));
        return found.isEmpty() ? "" : text(found.get(0));
    }

    // textContent works on hidden elements too, unlike getText().
    private static String text(WebElement element) {
        String raw = element.getAttribute("textContent");
        if (raw == null) return "";
        return raw.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    // Returns the first regex group match, or an empty string.
    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }
}
