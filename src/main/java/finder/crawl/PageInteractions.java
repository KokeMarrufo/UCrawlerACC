package finder.crawl;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.List;

/**
 * Interacts with the form controls on the UBC program listing page:
 * the keyword search text box, the filter dropdowns and their buttons.
 */
public class PageInteractions {

    /**
     * Types a keyword into the "Search Programs" text box, clicks Search,
     * prints the matching program titles, then goes back to the listing page.
     */
    public static void keywordSearch(WebDriver driver, WebDriverWait wait, String keyword) {
        // Text box: clear anything already in it, then type the keyword
        WebElement searchBox = wait.until(ExpectedConditions.elementToBeClickable(By.id("edit-search")));
        searchBox.clear();
        searchBox.sendKeys(keyword);

        // Button: submit the search
        driver.findElement(By.id("edit-submit-solr-program-search")).click();

        // The results page lists matching programs, wait until they appear
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(".view-id-gps_program_search_solr .views-row")));
        List<WebElement> results = driver.findElements(
                By.cssSelector(".view-id-gps_program_search_solr .views-field-title"));

        System.out.println("Keyword search for \"" + keyword + "\" found " + results.size() + " programs, e.g.:");
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            System.out.println("  - " + results.get(i).getText().trim());
        }

        // Browser history: go back to the listing page and wait for its table
        driver.navigate().back();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("table tbody tr")));
    }

    /**
     * Picks options in the Faculty and Degree level dropdowns and clicks Filter.
     * Passing "- Any -" for both keeps every program in the table.
     */
    public static void applyFilters(WebDriver driver, WebDriverWait wait, String faculty, String level) {
        WebElement oldTable = driver.findElement(By.cssSelector("table"));

        chooseOption(driver, wait, "edit-field-faculty-ref-target-id-verf", faculty);
        chooseOption(driver, wait, "edit-lev", level);

        // Button: apply the filters, the page reloads with a new table
        driver.findElement(By.id("edit-submit-gps-program-overview")).click();
        wait.until(ExpectedConditions.stalenessOf(oldTable));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("table tbody tr")));

        System.out.println("Applied filters: Faculty = " + faculty + ", Degree level = " + level);
    }

    /**
     * The site hides the real select element and shows a "Chosen" widget instead
     * (a link plus a list of options), so Selenium's Select class can't click it.
     * We act like a user: open the widget, then click the option with the right text.
     */
    private static void chooseOption(WebDriver driver, WebDriverWait wait, String selectId, String optionText) {
        // Chosen builds its widget id from the select id, with "-" turned into "_"
        String widgetId = selectId.replace('-', '_') + "_chosen";

        // Open the dropdown
        WebElement opener = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#" + widgetId + " a.chosen-single")));
        opener.click();

        // Click the option whose visible text matches
        By option = By.xpath("//div[@id='" + widgetId + "']//li[contains(@class,'active-result')]"
                + "[normalize-space()=" + xpathLiteral(optionText) + "]");
        wait.until(ExpectedConditions.elementToBeClickable(option)).click();
    }

    // Quotes text for XPath, handling apostrophes like the one in "Master's".
    private static String xpathLiteral(String text) {
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        return "concat('" + text.replace("'", "', \"'\", '") + "')";
    }
}
