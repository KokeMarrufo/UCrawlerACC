package finder.crawl;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import org.openqa.selenium.WindowType;
import java.time.Duration;


public class Main {

    // Keyword typed into the "Search Programs" text box
    static final String SEARCH_KEYWORD = "computer science";

    // We crawl Master's programs from these faculties and combine them into one CSV.
    // The faculty dropdown only takes one value, so the filter runs once per faculty.
    // I chose these two faculties as they are the closest to MAC (Master of Applied Computing) related programs in UBC
    static final String[] FACULTIES = {"Faculty of Science", "Faculty of Applied Science"};

    // The listing page exposes "Master's" and "Doctoral". Doctoral is the PhD level.
    // There is no dropdown label spelled "PhD".
    public static final String[] DEGREE_LEVELS = {"Master's", "Doctoral"};

    static final String LISTING_URL = "https://www.grad.ubc.ca/prospective-students/graduate-degree-programs";

    public static final String CSV_PATH = "data/ubc_programs.csv";

    public static void main(String[] args) throws InterruptedException, IOException {

        String driverPath = DriverConfig.resolve();
        if (driverPath == null) {
            System.out.println("ChromeDriver path is not set.");
            System.out.println("Set -Dwebdriver.chrome.driver=/path/to/chromedriver, or the CHROMEDRIVER or CHROME_DRIVER_PATH environment variable.");
            System.out.println("The portal does not need a browser. It loads " + CSV_PATH + ".");
            return;
        }
        System.setProperty("webdriver.chrome.driver", driverPath);

        // Opens a new chrome window that selenium controls
        WebDriver driver = new ChromeDriver();
        // Wide window so the desktop table layout is shown, not the mobile one
        //UBC has a responsive design so if the page is shown at the regular size chromedriver uses to open pages
        //it might trigger the responsive card view
        driver.manage().window().setSize(new Dimension(1920, 1080));
        // Maximum wait time of 10 seconds, it waits for things to appear and gives up after 10 seconds
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        driver.get(LISTING_URL); //opens the UBC program list page
        List<Program> programs = new ArrayList<>(); //an empty list that collects every program from all faculties

        // ---- Interaction phase: use the search text box and its button ----
        PageInteractions.keywordSearch(driver, wait, SEARCH_KEYWORD);

        // ---- Listing phase: filter by each faculty and degree level, then read every page of results ----
        for (String faculty : FACULTIES) {
            for (String level : DEGREE_LEVELS) {
                // The filter refreshes the table with AJAX and leaves the dropdowns unusable,
                // so we need to start from a fresh copy of the listing page for each faculty
                driver.get(LISTING_URL);
                //We pass the current faculty and also filter by the degree level each iteration
                //if we want to crawl more faculties we just need to add them to the FACULTIES array
                PageInteractions.applyFilters(driver, wait, faculty, level);
                //I get the current size of the programs before crawling the list
                // so that I can print how many programs on the currently crawled faculty there are
                int before = programs.size();
                crawlListing(driver, wait, programs);// function that reads the results table and keeps clicking next until the last page
                System.out.println(faculty + " / " + level + ": " + (programs.size() - before) + " programs"); //show how many programs of the current faculty (console)
            }
        }
        // Prints the number of programs collected, just on the console
        System.out.println("Total programs collected: " + programs.size());

        // ---- Detail phase: visit each program page in a separate tab ----
        String listingTab = driver.getWindowHandle();//saves the ID of the current tab so I can switch back to it at the end
        // Open a new tab and switch focus to it, this is to navigate to each program's details without losing UBC's main page
        // from the previous step
        driver.switchTo().newWindow(WindowType.TAB);

        int max = programs.size(); // every program that we filtered on the previous step
        //looping each program
        for (int i = 0; i < max; i++) {
            Program p = programs.get(i); //get the index i item from the program list
            if (p.getUrl().isEmpty()) continue; //if the program doesn't have a detail's url we skip it

            try {
                DetailScraper.scrape(driver, wait, p); // Call the detail scraper method
                System.out.println("Detail " + (i + 1) + "/" + max + ": " + p.getName());
                Thread.sleep(1000); // short pause between requests to avoid going too heavy on UBC's server
            } catch (NoSuchSessionException e) {
                // The browser is gone, stop here but still save what we have
                System.out.println("Browser session ended, stopping detail phase");
                break;
            } catch (Exception e) {
                // if one program page fails, print it and move on to the next one instead of stopping
                System.out.println("Failed on " + p.getUrl() + ": " + e.getMessage());
            }
        }

        // Close the detail tab, switch back to the listing tab, then close the browser
        // (ignore errors if the browser already closed on its own)
        try {
            driver.close();
            driver.switchTo().window(listingTab);
            System.out.println("Back on the listing tab: " + driver.getTitle());
        } catch (Exception e) {
            // the tabs are already gone
        }
        try {
            driver.quit();
        } catch (Exception e) {
            // nothing to clean up
        }

        // ---- Save phase: write everything to a CSV file ----
        // A live crawl overwrites the seeded catalog. Page HTML and text are already in data/pages/.
        CsvWriter.save(programs, CSV_PATH);
    }

    /**
     * Reads the current results table, then keeps clicking Next until the last page.
     * Receives the web driver as parameter
     * wait: will allow us to wait for something to happen or 10 seconds before giving up
     * programs: the list where each program found gets added
     */
    public static void crawlListing(WebDriver driver, WebDriverWait wait, List<Program> programs) {
        while (true) {
            // find the table element
            WebElement table = driver.findElement(By.cssSelector("table"));

            // method to read every row of the current table of the page being shown
            readPage(driver, programs);

            // It checks if there is still a next button on the page
            //So if nextLinks is empty it means the findElements couldn't find the button so it must be the last page
            List<WebElement> nextLinks = driver.findElements(By.cssSelector(".visible-desktop li.pager__item--next a"));
            if (nextLinks.isEmpty()) {
                break; // last page, no Next link
            }

            // Click on the next button
            nextLinks.get(0).click();
            //I wait until the previous table is gone from the page, so we know the click worked and the page is loading
            wait.until(ExpectedConditions.stalenessOf(table));
            //I  wait until the contents specifically the body of the new table is visible before proceeding
            // also limited to 10 seconds.
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("table tbody tr")));
        }
    }

    /**
     * Reads every row of the program table on the current page and adds
     * one Program object per row to the list. It only reads text and links,
     * it never leaves the page, so the pagination loop keeps working.
     */
    public static void readPage(WebDriver driver, List<Program> programs) {
        //First we find our table element
        WebElement table = driver.findElement(By.cssSelector("table"));
        //Then we create a list of rows contained on the table
        List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));

        //We loop each row that we obtained on the table
        for (WebElement row : rows) {
            // get all the cells (td tags) in this row and put them in a list
            List<WebElement> cells = row.findElements(By.tagName("td"));

            // Skip any row that doesn't have the 3 expected columns
            //just a safeguard in case a row is corrupted
            if (cells.size() < 3) {
                continue;
            }

            //the first column is the specialization
            String specialization = cells.get(0).getText().trim();
            // Also get the faculty column which is at index 2
            String faculty = cells.get(2).getText().trim();

            // The program name cell contains a link to the detail page
            List<WebElement> links = cells.get(1).findElements(By.tagName("a"));
            String name = cells.get(1).getText().trim();
            //if the links list is empty we just pass an empty string
            //if not we pass the href value, which is the one that contains the URL
            String url = links.isEmpty() ? "" : links.get(0).getAttribute("href");

            //Now we just add a new Program with the values we previously found on each row
            programs.add(new Program(specialization, name, faculty, url));
        }

        System.out.println("Read " + rows.size() + " rows from this page");
    }
}
