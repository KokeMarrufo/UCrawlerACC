package finder.crawl;

/**
 * ChromeDriver location for the live crawl.
 * A property wins, then CHROMEDRIVER, then CHROME_DRIVER_PATH.
 * There is no built-in path. The offline portal does not call this.
 */
public final class DriverConfig {
    private DriverConfig() {}

    public static String resolve() {
        return resolveValue(
                System.getProperty("webdriver.chrome.driver"),
                System.getenv("CHROMEDRIVER"),
                System.getenv("CHROME_DRIVER_PATH"));
    }

    public static String resolveValue(String property, String envChromedriver, String envPath) {
        String chosen = firstNonBlank(property, envChromedriver, envPath);
        return chosen;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
