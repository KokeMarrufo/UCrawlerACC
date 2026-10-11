package finder.portal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import finder.analysis.IndexBenchmark;
import finder.crawl.Program;
import finder.index.FrequencyCount;
import finder.index.SearchFrequency;
import finder.rank.ProgramFit;
import finder.rank.WeightModel;
import finder.regex.FieldValidator;
import finder.regex.PatternFinder;
import finder.universities.Corpus;
import finder.universities.SearchDocument;
import finder.universities.UniversitySlots;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Student and admin portals on the JDK HTTP server. */
public final class PortalServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 8747;

    private final HttpServer server;
    private final ExecutorService executor;
    private final SearchService search;
    private final AuthStore auth;
    private final ApplicationStore applications;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private PortalServer(String host, int port, SearchService search, AuthStore auth, ApplicationStore applications) throws IOException {
        this.search = search;
        this.auth = auth;
        this.applications = applications;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.executor = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "portal");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("portal.host", "0.0.0.0");
        int port = Integer.parseInt(firstNonBlank(
                System.getProperty("portal.port"),
                System.getenv("PORTAL_PORT"),
                Integer.toString(DEFAULT_PORT)));
        java.nio.file.Path data = java.nio.file.Path.of(System.getProperty("finder.data", "data"));
        Corpus corpus = Corpus.load(data);
        AuthStore authStore = new AuthStore(data.resolve("users.tsv"));
        authStore.load();
        authStore.ensureSeed();
        ApplicationStore applicationStore = new ApplicationStore(data.resolve("applications.csv"));
        applicationStore.load();
        SearchFrequency frequency = new SearchFrequency(data.resolve("search-frequency.tsv"));
        frequency.load();
        SearchService searchService = new SearchService(corpus, frequency);
        PortalServer portal = start(host, port, searchService, authStore, applicationStore);
        System.out.println("Graduate Program Finder");
        System.out.println("Listening on http://" + host + ":" + portal.port());
        System.out.println("Open http://127.0.0.1:" + portal.port());
        System.out.println("Accounts: student / student and admin / admin");
        System.out.println("Loaded " + corpus.programs().size() + " programs into the index.");
        Thread.currentThread().join();
    }

    public static PortalServer start(String host, int port, SearchService search, AuthStore auth, ApplicationStore applications) throws IOException {
        PortalServer portal = new PortalServer(host, port, search, auth, applications);
        portal.server.start();
        return portal;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("/favicon.ico".equals(path)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            Session session = session(exchange);
            switch (path) {
                case "/" -> home(exchange, method, session);
                case "/login" -> login(exchange, method);
                case "/register" -> register(exchange, method);
                case "/logout" -> logout(exchange, session);
                case "/student" -> student(exchange, method, session);
                case "/admin" -> admin(exchange, method, session);
                case "/apply" -> apply(exchange, method, session);
                case "/decision" -> decision(exchange, method, session);
                case "/validate" -> validate(exchange, method, session);
                case "/api/applications" -> applicationsApi(exchange, session, false);
                case "/api/admin/applications" -> applicationsApi(exchange, session, true);
                case "/api/complete" -> complete(exchange, session);
                default -> Web.html(exchange, 404, HtmlPages.message("Not found", "That page is not part of the finder.", 404));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                Web.html(exchange, 500, HtmlPages.message(
                        "Something went wrong",
                        ex.getMessage() == null ? "Request failed." : ex.getMessage(),
                        500));
            } catch (Exception ignored) {
                exchange.close();
            }
        }
    }

    private void home(HttpExchange exchange, String method, Session session) throws IOException {
        if (!"GET".equals(method)) {
            Web.html(exchange, 405, HtmlPages.message("Method not allowed", "Use the form on the sign-in page.", 405));
            return;
        }
        if (session != null) {
            Web.redirect(exchange, "admin".equals(session.role) ? "/admin" : "/student", null);
            return;
        }
        Web.html(exchange, 200, HtmlPages.login("", ""));
    }

    private void login(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            Web.redirect(exchange, "/", null);
            return;
        }
        Map<String, String> form = Web.form(Web.readBody(exchange));
        String username = form.getOrDefault("username", "").trim();
        String password = form.getOrDefault("password", "");
        if (!auth.login(username, password)) {
            Web.html(exchange, 401, HtmlPages.login("Those credentials do not match an account.", ""));
            return;
        }
        String token = token();
        String role = auth.role(username);
        sessions.put(token, new Session(username, role));
        Web.redirect(exchange, "admin".equals(role) ? "/admin" : "/student", Web.cookie(token));
    }

    private void register(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            Web.redirect(exchange, "/", null);
            return;
        }
        Map<String, String> form = Web.form(Web.readBody(exchange));
        String username = form.getOrDefault("username", "").trim();
        String error = auth.register(username, form.getOrDefault("password", ""));
        if (!error.isEmpty()) {
            Web.html(exchange, 400, HtmlPages.login(error, ""));
            return;
        }
        String token = token();
        sessions.put(token, new Session(username, AuthStore.STUDENT));
        Web.redirect(exchange, "/student?notice=registered", Web.cookie(token));
    }

    private void logout(HttpExchange exchange, Session session) throws IOException {
        String token = Web.sessionId(exchange);
        if (!token.isEmpty()) {
            sessions.remove(token);
        }
        Web.redirect(exchange, "/", Web.clearCookie());
    }

    private void student(HttpExchange exchange, String method, Session session) throws IOException {
        if (session == null) {
            Web.redirect(exchange, "/", null);
            return;
        }
        if (!"GET".equals(method)) {
            Web.html(exchange, 405, HtmlPages.message("Method not allowed", "Search uses the form.", 405));
            return;
        }
        Map<String, String> query = Web.form(exchange.getRequestURI().getRawQuery());
        HtmlPages.StudentView view = new HtmlPages.StudentView();
        view.username = session.username;
        view.school = query.getOrDefault("school", "ubc");
        if (search.corpus().slots().stream().noneMatch(slot -> slot.id().equals(view.school))) {
            view.school = "ubc";
        }
        view.slots = search.corpus().slots();
        view.programs = search.corpus().programsForUniversity(view.school);
        view.applications = applications.forStudent(session.username);
        view.notice = notice(query.get("notice"));
        view.wCost = query.getOrDefault("wCost", "1");
        view.wTime = query.getOrDefault("wTime", "1");
        view.wAdmit = query.getOrDefault("wAdmit", "1");
        view.tuition = query.getOrDefault("tuition", "domestic");
        view.weighted = "1".equals(query.get("weighted"));
        if (search.corpus().documents().stream().anyMatch(document -> !document.savedPage())) {
            view.fieldNote = "<p class=\"muted\">Indexed from the CSV fields because no saved page file is in data/pages for these programs. A crawl writes the HTML and text files. The indexed words are the extracted cells, not a written description.</p>";
        }
        if (query.containsKey("q")) {
            view.query = query.getOrDefault("q", "");
            view.searched = true;
            Double cost = parseWeight(view.wCost);
            Double time = parseWeight(view.wTime);
            Double admission = parseWeight(view.wAdmit);
            if (cost == null || time == null || admission == null) {
                view.error = "Weights have to be numbers.";
            } else {
                WeightModel.Weights weights = new WeightModel.Weights(cost, time, admission, "international".equals(view.tuition));
                boolean rankWholeList = view.weighted && view.query.isBlank();
                SearchService.SearchOutcome outcome = rankWholeList
                        ? search.rankCatalog(view.programs, weights)
                        : search.search(view.query, view.weighted, weights);
                view.error = outcome.error() == null ? "" : outcome.error();
                view.suggestions = outcome.suggestions();
                List<HtmlPages.HitRow> rows = new ArrayList<>();
                for (SearchService.Ranked ranked : outcome.hits()) {
                    rows.add(toRow(ranked, "international".equals(view.tuition)));
                }
                view.hits = rows;
            }
        }
        view.maxTuition = query.getOrDefault("maxTuition", "");
        view.maxMonths = query.getOrDefault("maxMonths", "");
        if ("1".equals(query.get("fit"))) {
            Double cost = parseWeight(view.wCost);
            Double time = parseWeight(view.wTime);
            Double admission = parseWeight(view.wAdmit);
            if (cost == null || time == null || admission == null) {
                view.fitError = "Weights have to be numbers.";
            } else {
                WeightModel.Weights weights = new WeightModel.Weights(
                        cost, time, admission, "international".equals(view.tuition));
                ProgramFit.Outcome fit = ProgramFit.recommend(view.programs, view.maxTuition, view.maxMonths, weights);
                view.fitError = fit.error();
                view.fitSummary = fit.summary();
                List<HtmlPages.HitRow> rows = new ArrayList<>();
                for (ProgramFit.Pick pick : fit.picks()) {
                    HtmlPages.HitRow row = new HtmlPages.HitRow();
                    Program program = pick.scored().program();
                    row.name = program.getName();
                    row.programId = program.getDetail("Program ID");
                    row.url = program.getUrl();
                    row.weightScore = Double.isInfinite(pick.scored().score())
                            ? ""
                            : String.format(Locale.US, "%.3f", pick.scored().score());
                    row.breakdown = pick.reason() + " " + pick.scored().breakdown().describe();
                    row.tuition = WeightModel.firstYearTuition(program, "international".equals(view.tuition));
                    Double months = WeightModel.parseMonths(program.getDetail("Min Time to Completion"));
                    row.months = months == null ? "" : String.format(Locale.US, "%.1f", months);
                    Double rate = WeightModel.offerRate(program);
                    row.offerRate = rate == null ? "" : String.format(Locale.US, "%.0f%%", rate * 100.0);
                    rows.add(row);
                }
                view.fits = rows;
            }
        }
        if ("1".equals(query.get("benchmark"))) {
            IndexBenchmark.Timing timing = IndexBenchmark.compare(
                    search.corpus().documents(), search.corpus().index(), "science", 40);
            view.benchmarkText = String.format(Locale.US,
                    "word: science%nrepeats: %d (totals, not one lookup)%nfull scan: %.3f ms%nindex lookup: %.3f ms%ncount: %d%ncounts match: %s",
                    timing.repeats(), timing.scanMillis(), timing.indexMillis(), timing.count(), timing.countsMatch());
        }
        if (query.containsKey("freqWord") || query.containsKey("freqUrl")) {
            view.freqWord = query.getOrDefault("freqWord", "");
            view.freqUrl = query.getOrDefault("freqUrl", "");
            FrequencyCount.Result result = search.frequency(view.freqWord, view.freqUrl);
            view.freqMessage = result.ok()
                    ? view.freqWord + " occurs " + result.count() + " time(s) on that page."
                    : result.error();
        }
        if (query.containsKey("pattern")) {
            view.pattern = query.getOrDefault("pattern", "");
            PatternFinder.Outcome outcome = PatternFinder.find(search.corpus().documents(), view.pattern);
            if (!outcome.ok()) {
                view.patternMessage = outcome.error();
            } else {
                view.patternMatches = outcome.matches();
                view.patternMessage = outcome.matches().size() + " match(es)" + (outcome.truncated() ? ". Stopped at 50." : ".");
            }
        }
        view.history = search.searchFrequency().ranking();
        Web.html(exchange, 200, HtmlPages.student(view));
    }

    private void admin(HttpExchange exchange, String method, Session session) throws IOException {
        if (session == null) {
            Web.redirect(exchange, "/", null);
            return;
        }
        if (!"admin".equals(session.role)) {
            Web.html(exchange, 403, HtmlPages.message("Admins only", "A student account cannot open the decision page.", 403));
            return;
        }
        if (!"GET".equals(method)) {
            Web.html(exchange, 405, HtmlPages.message("Method not allowed", "Use the buttons on the admin page.", 405));
            return;
        }
        Map<String, String> query = Web.form(exchange.getRequestURI().getRawQuery());
        Web.html(exchange, 200, HtmlPages.admin(session.username, notice(query.get("notice")), "", applications.all()));
    }

    private void apply(HttpExchange exchange, String method, Session session) throws IOException {
        if (session == null) {
            Web.redirect(exchange, "/", null);
            return;
        }
        if (!"POST".equals(method)) {
            Web.redirect(exchange, "/student#apply", null);
            return;
        }
        Map<String, String> form = Web.form(Web.readBody(exchange));
        String programId = form.getOrDefault("programId", "").trim();
        Program program = search.corpus().programById(programId);
        if (program == null) {
            Web.redirect(exchange, "/student?notice=missing-program#apply", null);
            return;
        }
        String details = form.getOrDefault("details", "").trim();
        if (details.isEmpty()) {
            Web.redirect(exchange, "/student?notice=missing-details#apply", null);
            return;
        }
        applications.add(programId, session.username, details);
        Web.redirect(exchange, "/student?notice=applied#applications", null);
    }

    private void decision(HttpExchange exchange, String method, Session session) throws IOException {
        if (session == null || !"admin".equals(session.role)) {
            Web.html(exchange, 403, HtmlPages.message("Admins only", "Only an admin can accept or reject an application.", 403));
            return;
        }
        if (!"POST".equals(method)) {
            Web.redirect(exchange, "/admin", null);
            return;
        }
        Map<String, String> form = Web.form(Web.readBody(exchange));
        boolean saved = applications.decide(
                form.getOrDefault("programId", ""),
                form.getOrDefault("student", ""),
                form.getOrDefault("time", ""),
                form.getOrDefault("status", ""));
        Web.redirect(exchange, saved ? "/admin?notice=saved" : "/admin?notice=bad-decision", null);
    }

    private void validate(HttpExchange exchange, String method, Session session) throws IOException {
        if (session == null) {
            Web.redirect(exchange, "/", null);
            return;
        }
        if (!"GET".equals(method)) {
            Web.redirect(exchange, "/validate", null);
            return;
        }
        Map<String, String> query = Web.form(exchange.getRequestURI().getRawQuery());
        FieldValidator.Check check = null;
        if (query.containsKey("check")) {
            check = FieldValidator.check(query.get("check"), query.getOrDefault("value", ""));
        }
        FieldValidator.Report report = FieldValidator.validate(search.corpus().programs());
        Web.html(exchange, 200, HtmlPages.validate(session.username, report, check));
    }

    private void applicationsApi(HttpExchange exchange, Session session, boolean admin) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            Web.json(exchange, 405, "{\"error\":\"method\"}");
            return;
        }
        if (session == null) {
            Web.json(exchange, 401, "{\"error\":\"sign in\"}");
            return;
        }
        if (admin && !"admin".equals(session.role)) {
            Web.json(exchange, 403, "{\"error\":\"admins only\"}");
            return;
        }
        List<ApplicationStore.Application> rows = admin
                ? applications.all()
                : applications.forStudent(session.username);
        Web.json(exchange, 200, applicationsJson(rows));
    }

    private void complete(HttpExchange exchange, Session session) throws IOException {
        if (session == null) {
            Web.json(exchange, 401, "[]");
            return;
        }
        Map<String, String> query = Web.form(exchange.getRequestURI().getRawQuery());
        List<String> words = search.complete(query.getOrDefault("prefix", ""));
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(Web.jsonString(words.get(i)));
        }
        json.append(']');
        Web.json(exchange, 200, json.toString());
    }

    private HtmlPages.HitRow toRow(SearchService.Ranked ranked, boolean international) {
        HtmlPages.HitRow row = new HtmlPages.HitRow();
        row.url = ranked.keyword().url();
        row.name = ranked.keyword().name();
        row.keywordScore = ranked.keyword().score();
        row.keywordText = row.keywordScore < 0 ? "—" : Integer.toString(row.keywordScore);
        SearchDocument document = search.corpus().documentByUrl(row.url);
        Program program = search.corpus().programByUrl(row.url);
        if (document != null) {
            row.programId = document.programId();
            row.university = universityName(document.universityId());
        }
        if (program != null) {
            if (row.programId == null || row.programId.isBlank()) {
                row.programId = program.getDetail("Program ID");
            }
            row.tuition = blank(WeightModel.firstYearTuition(program, international));
            Double months = WeightModel.parseMonths(program.getDetail("Min Time to Completion"));
            row.months = months == null ? "—" : String.format(Locale.US, "%.1f", months);
            Double rate = WeightModel.offerRate(program);
            row.offerRate = rate == null ? "—" : String.format(Locale.US, "%.0f%%", rate * 100.0);
        }
        if (ranked.weighted() == null) {
            row.weightScore = "—";
            row.breakdown = "keyword order only";
        } else if (Double.isInfinite(ranked.weighted().score())) {
            row.weightScore = "no comparable fields";
            row.breakdown = ranked.weighted().breakdown().describe();
        } else {
            row.weightScore = String.format(Locale.US, "%.3f", ranked.weighted().score());
            String how = row.keywordScore < 0
                    ? "mergesort on this university's programs; "
                    : "mergesort after the keyword list; ";
            row.breakdown = how + ranked.weighted().breakdown().describe();
        }
        return row;
    }

    private String universityName(String id) {
        for (UniversitySlots.UniversitySlot slot : search.corpus().slots()) {
            if (slot.id().equals(id)) {
                return slot.name();
            }
        }
        return id == null ? "" : id;
    }

    private Session session(HttpExchange exchange) {
        String token = Web.sessionId(exchange);
        if (token.isEmpty()) {
            return null;
        }
        return sessions.get(token);
    }

    private String token() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    private static String applicationsJson(List<ApplicationStore.Application> rows) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            ApplicationStore.Application row = rows.get(i);
            json.append('{')
                    .append("\"programId\":").append(Web.jsonString(row.programId())).append(',')
                    .append("\"student\":").append(Web.jsonString(row.student())).append(',')
                    .append("\"details\":").append(Web.jsonString(row.details())).append(',')
                    .append("\"status\":").append(Web.jsonString(row.status())).append(',')
                    .append("\"time\":").append(Web.jsonString(row.time()))
                    .append('}');
        }
        return json.append(']').toString();
    }

    private static String notice(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "applied" -> "Application submitted. Status is pending.";
            case "registered" -> "Account created. You are signed in.";
            case "missing-program" -> "That program id is not in the catalog.";
            case "missing-details" -> "Add a few details before submitting.";
            case "saved" -> "Decision saved.";
            case "bad-decision" -> "That application was not found.";
            default -> "";
        };
    }

    private static Double parseWeight(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1.0;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return Integer.toString(DEFAULT_PORT);
    }

    private record Session(String username, String role) {}
}
