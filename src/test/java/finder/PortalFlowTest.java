package finder;

import finder.crawl.Program;
import finder.index.SearchFrequency;
import finder.portal.ApplicationStore;
import finder.portal.AuthStore;
import finder.portal.PortalServer;
import finder.portal.SearchService;
import finder.universities.Corpus;
import finder.universities.SearchDocument;
import finder.universities.UniversitySlots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalFlowTest {
    @Test
    void studentAndAdminFlow(@TempDir Path dir) throws Exception {
        assertTrue(PortalServer.DEFAULT_PORT != 3000 && PortalServer.DEFAULT_PORT != 5173 && PortalServer.DEFAULT_PORT != 8080);

        Program alpha = program("Alpha & Ice <script>", "AAAA-AA", "https://example.test/alpha", "3,000.00");
        Program beta = program("Beta Studies", "BBBB-BB", "https://example.test/beta", "1,000.00");
        List<SearchDocument> documents = List.of(
                new SearchDocument("ubc", "AAAA-AA", alpha.getName(), alpha.getUrl(),
                        "Alpha studies glaciers and glaciers and glaciers.", false),
                new SearchDocument("ubc", "BBBB-BB", beta.getName(), beta.getUrl(),
                        "Beta studies glaciers.", false));
        Corpus corpus = Corpus.of(UniversitySlots.all(), List.of(alpha, beta), documents);
        AuthStore auth = new AuthStore(dir.resolve("users.tsv"));
        auth.load();
        auth.ensureSeed();
        assertTrue(auth.login("student", "student"));
        assertTrue(auth.login("admin", "admin"));
        assertFalse(auth.login("student", "wrong"));
        assertEquals("", auth.register("newstudent", "newstudent"));
        assertTrue(auth.login("newstudent", "newstudent"));
        String users = Files.readString(dir.resolve("users.tsv"));
        assertFalse(users.contains("newstudent\tstudent\tnewstudent"));
        assertTrue(users.lines().anyMatch(line -> line.startsWith("student\tstudent\t") && line.split("\t")[3].length() == 64));

        ApplicationStore applications = new ApplicationStore(dir.resolve("applications.csv"));
        applications.load();
        applications.add("AAAA-AA", "other", "hidden application");
        SearchFrequency frequency = new SearchFrequency(dir.resolve("search-frequency.tsv"));
        frequency.load();
        SearchService search = new SearchService(corpus, frequency);

        try (PortalServer portal = PortalServer.start("127.0.0.1", 0, search, auth, applications)) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + portal.port();

            HttpResponse<String> home = client.send(get(base + "/"), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, home.statusCode());
            assertTrue(home.body().contains("student / student"));

            HttpResponse<String> badLogin = client.send(post(base + "/login", "username=student&password=nope"), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, badLogin.statusCode());

            HttpResponse<String> login = client.send(post(base + "/login", "username=student&password=student"), HttpResponse.BodyHandlers.ofString());
            assertEquals(303, login.statusCode());
            assertEquals("/student", login.headers().firstValue("location").orElse(""));
            String studentCookie = cookie(login);

            HttpResponse<String> page = client.send(get(base + "/student", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("offers / applications"));
            assertTrue(page.body().contains("renormalized"));
            assertTrue(page.body().contains("Alpha &amp; Ice &lt;script&gt;"));
            assertFalse(page.body().contains("Alpha & Ice <script>"));
            assertTrue(page.body().contains("CSV fields"));
            assertTrue(page.body().contains("University slot 2"));

            HttpResponse<String> empty = client.send(get(base + "/student?q=", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(empty.body().contains("empty query"));

            HttpResponse<String> keyword = client.send(get(base + "/student?q=glaciers", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(keyword.body().indexOf("Alpha &amp; Ice") < keyword.body().indexOf("Beta Studies"));
            assertTrue(keyword.body().contains(">glaciers<"));

            HttpResponse<String> weighted = client.send(get(
                    base + "/student?q=glaciers&weighted=1&wCost=1&wTime=0&wAdmit=0&tuition=domestic",
                    studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(weighted.body().indexOf("Beta Studies") < weighted.body().indexOf("Alpha &amp; Ice"));

            HttpResponse<String> spelled = client.send(get(base + "/student?q=glacirs", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(spelled.body().contains("glaciers"));
            assertTrue(spelled.body().contains("Nearby words"));

            HttpResponse<String> slot = client.send(get(base + "/student?school=slot-2", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(slot.body().contains("A crawler has not been added."));

            HttpResponse<String> unknown = client.send(get(
                    base + "/student?freqWord=glaciers&freqUrl=https%3A%2F%2Fexample.test%2Fmissing",
                    studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(unknown.body().contains("unknown URL"));

            HttpResponse<String> knownCount = client.send(get(
                    base + "/student?freqWord=glaciers&freqUrl=https%3A%2F%2Fexample.test%2Falpha",
                    studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(knownCount.body().contains("occurs 3 time"));

            HttpResponse<String> badPattern = client.send(get(base + "/student?pattern=%5B", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(badPattern.body().contains("invalid regular expression"));

            HttpResponse<String> blocked = client.send(get(base + "/api/applications"), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, blocked.statusCode());

            HttpResponse<String> mine = client.send(get(base + "/api/applications", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, mine.statusCode());
            assertFalse(mine.body().contains("hidden application"));

            HttpResponse<String> applied = client.send(post(base + "/apply", "programId=BBBB-BB&details=Starting+in+September", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(303, applied.statusCode());
            HttpResponse<String> afterApply = client.send(get(base + "/api/applications", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(afterApply.body().contains("BBBB-BB"));
            assertTrue(afterApply.body().contains("pending"));
            assertFalse(afterApply.body().contains("other"));

            HttpResponse<String> studentAdmin = client.send(get(base + "/admin", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, studentAdmin.statusCode());

            HttpResponse<String> adminLogin = client.send(post(base + "/login", "username=admin&password=admin"), HttpResponse.BodyHandlers.ofString());
            String adminCookie = cookie(adminLogin);
            assertEquals("/admin", adminLogin.headers().firstValue("location").orElse(""));
            HttpResponse<String> adminPage = client.send(get(base + "/admin", adminCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(adminPage.body().contains("hidden application"));
            assertTrue(adminPage.body().contains("BBBB-BB"));

            String time = applications.forStudent("student").get(0).time();
            String decision = "programId=BBBB-BB&student=student&status=accepted&time=" + java.net.URLEncoder.encode(time, java.nio.charset.StandardCharsets.UTF_8);
            HttpResponse<String> decided = client.send(post(base + "/decision", decision, adminCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(303, decided.statusCode());

            HttpResponse<String> updated = client.send(get(base + "/api/applications", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(updated.body().contains("accepted"));
            assertFalse(updated.body().contains("other"));

            HttpResponse<String> validation = client.send(get(base + "/validate?check=programId&value=VGMMSC-CA", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(validation.body().contains("Matches the program id pattern."));
            HttpResponse<String> badId = client.send(get(base + "/validate?check=programId&value=bad", studentCookie), HttpResponse.BodyHandlers.ofString());
            assertTrue(badId.body().contains("Does not match"));
        }
    }

    private static Program program(String name, String id, String url, String domestic) {
        Program program = new Program("Science", name, "Faculty of Science", url);
        program.setDetail("Program ID", id);
        program.setDetail("Tuition Domestic (First Year)", domestic);
        program.setDetail("Min Time to Completion", "2 years");
        program.setDetail("Offers", "5");
        program.setDetail("Applications", "10");
        return program;
    }

    private static HttpRequest get(String url) {
        return get(url, null);
    }

    private static HttpRequest get(String url, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return builder.build();
    }

    private static HttpRequest post(String url, String body) {
        return post(url, body, null);
    }

    private static HttpRequest post(String url, String body, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return builder.build();
    }

    private static String cookie(HttpResponse<String> response) {
        String header = response.headers().firstValue("Set-Cookie").orElseThrow();
        return header.split(";", 2)[0];
    }
}
