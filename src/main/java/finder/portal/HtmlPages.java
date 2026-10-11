package finder.portal;

import finder.crawl.Program;
import finder.rank.WeightModel;
import finder.regex.FieldValidator;
import finder.regex.PatternFinder;
import finder.text.SpellChecker;
import finder.universities.UniversitySlots;

import java.util.List;
import java.util.Locale;

public final class HtmlPages {
    private static final String CSS = """
            :root {
              --ink: #1c2430;
              --muted: #5c675f;
              --paper: #f3efe6;
              --card: #fffdf8;
              --line: #d9d1c3;
              --teal: #0f4c5c;
              --teal-dark: #0a333d;
              --clay: #8c3a32;
              --ok: #1f6b45;
              --warn: #8a5a12;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              font-family: "Segoe UI", "Helvetica Neue", sans-serif;
              color: var(--ink);
              background: var(--paper);
              line-height: 1.45;
            }
            header {
              display: flex;
              justify-content: space-between;
              gap: 1rem;
              align-items: flex-end;
              padding: 1.25rem 1.5rem;
              background: var(--teal);
              color: #f7f4ee;
            }
            header h1 { margin: 0; font-size: 1.55rem; font-weight: 650; letter-spacing: -0.02em; }
            header p { margin: 0.15rem 0 0; color: #d5e4e4; }
            nav { display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; }
            nav a, .button, button, .slots a, .chips a {
              font: inherit;
              border: 1px solid transparent;
              background: #f7f4ee;
              color: var(--teal-dark);
              border-radius: 999px;
              padding: 0.35rem 0.75rem;
              text-decoration: none;
              cursor: pointer;
            }
            button.danger { background: var(--clay); color: white; }
            button.ok { background: var(--ok); color: white; }
            main { max-width: 1100px; margin: 0 auto; padding: 1.25rem 1rem 3rem; }
            section {
              background: var(--card);
              border: 1px solid var(--line);
              border-radius: 14px;
              padding: 1rem 1.1rem 1.15rem;
              margin-bottom: 1rem;
            }
            h2 { margin: 0 0 0.6rem; font-size: 1.15rem; }
            label { display: block; font-size: 0.92rem; margin-bottom: 0.55rem; }
            input, select, textarea {
              width: 100%;
              font: inherit;
              padding: 0.45rem 0.55rem;
              border: 1px solid var(--line);
              border-radius: 8px;
              background: white;
              color: var(--ink);
            }
            textarea { min-height: 5rem; }
            .row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.6rem; }
            .checks { display: flex; flex-wrap: wrap; gap: 0.8rem; align-items: center; margin: 0.4rem 0 0.7rem; }
            .checks label { margin: 0; display: flex; gap: 0.35rem; align-items: center; }
            .checks input { width: auto; }
            .error { color: var(--clay); font-weight: 650; }
            .ok-text { color: var(--ok); font-weight: 650; }
            .muted { color: var(--muted); }
            pre.formula {
              white-space: pre-wrap;
              background: #f6f1e6;
              border-radius: 8px;
              padding: 0.75rem;
              margin: 0.4rem 0 0;
              font-size: 0.86rem;
            }
            table { width: 100%; border-collapse: collapse; font-size: 0.92rem; }
            th, td { text-align: left; vertical-align: top; padding: 0.4rem 0.45rem; border-bottom: 1px solid var(--line); }
            th { font-size: 0.78rem; letter-spacing: 0.03em; text-transform: uppercase; color: var(--muted); }
            .scroll { overflow-x: auto; }
            .chips { display: flex; flex-wrap: wrap; gap: 0.35rem; min-height: 1.6rem; margin-top: 0.4rem; }
            .chip { border-radius: 999px; }
            .status { font-weight: 700; text-transform: capitalize; }
            .status.pending { color: var(--warn); }
            .status.accepted { color: var(--ok); }
            .status.rejected { color: var(--clay); }
            .slots { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-bottom: 0.7rem; }
            .slots a.current { background: var(--teal); color: white; }
            .split { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
            .login-card { max-width: 32rem; margin: 2rem auto; }
            .actions { display: flex; gap: 0.35rem; }
            .actions form { margin: 0; }
            @media (max-width: 800px) {
              header { display: block; }
              nav { margin-top: 0.8rem; }
              .row, .split { grid-template-columns: 1fr; }
            }
            """;

    private HtmlPages() {}

    public static String login(String error, String notice) {
        String body = """
                <section class="login-card">
                  <h2>Sign in</h2>
                  <p>Students search the catalog, set weights, and apply. Admins accept or reject those applications.</p>
                  %s
                  %s
                  <form method="post" action="/login">
                    <label>Username <input name="username" autocomplete="username" required></label>
                    <label>Password <input name="password" type="password" autocomplete="current-password" required></label>
                    <button type="submit">Sign in</button>
                  </form>
                  <p class="muted">Demo accounts: student / student, and admin / admin.</p>
                </section>
                <section class="login-card">
                  <h2>Create a student account</h2>
                  <form method="post" action="/register">
                    <label>Username <input name="username" autocomplete="username" required></label>
                    <label>Password <input name="password" type="password" autocomplete="new-password" required></label>
                    <button type="submit">Register</button>
                  </form>
                </section>
                """.formatted(banner(error, true), banner(notice, false));
        return shell("Sign in", "", "", body, "");
    }

    public static String student(StudentView view) {
        StringBuilder body = new StringBuilder();
        body.append(banner(view.error, true));
        body.append(banner(view.notice, false));
        body.append("""
                <section id="search">
                  <h2>Search</h2>
                  <p class="muted">The index looks up each word. A binary heap orders pages by how often those words occur. Weights are a separate re-rank, done with mergesort.</p>
                  <form method="get" action="/student">
                    <input type="hidden" name="school" value="%s">
                    <label>Words <input id="q" name="q" value="%s" autocomplete="off"></label>
                    <div class="chips" id="complete"></div>
                    <div class="row">
                      <label>Cost weight <input name="wCost" value="%s" inputmode="decimal"></label>
                      <label>Time weight <input name="wTime" value="%s" inputmode="decimal"></label>
                      <label>Admission weight <input name="wAdmit" value="%s" inputmode="decimal"></label>
                      <label>Tuition used for cost
                        <select name="tuition">
                          <option value="domestic"%s>Domestic first year</option>
                          <option value="international"%s>International first year</option>
                        </select>
                      </label>
                    </div>
                    <div class="checks">
                      <label><input type="checkbox" name="weighted" value="1"%s> Re-rank matches with these weights</label>
                      <button type="submit">Search</button>
                    </div>
                  </form>
                  <h3>Weight formula</h3>
                  <pre class="formula">%s</pre>
                  %s
                  %s
                  %s
                </section>
                """.formatted(
                Web.escape(view.school),
                Web.escape(view.query),
                Web.escape(view.wCost),
                Web.escape(view.wTime),
                Web.escape(view.wAdmit),
                "international".equals(view.tuition) ? "" : " selected",
                "international".equals(view.tuition) ? " selected" : "",
                view.weighted ? " checked" : "",
                Web.escape(WeightModel.FORMULA),
                view.fieldNote,
                suggestions(view),
                hits(view)));

        body.append("<section id=\"programs\"><h2>Programs</h2>");
        body.append("<div class=\"slots\">");
        for (UniversitySlots.UniversitySlot slot : view.slots) {
            String current = slot.id().equals(view.school) ? " current" : "";
            body.append("<a class=\"").append(current.trim()).append("\" href=\"/student?school=")
                    .append(Web.escape(slot.id())).append("\">")
                    .append(Web.escape(slot.name())).append("</a>");
        }
        body.append("</div>");
        UniversitySlots.UniversitySlot currentSlot = null;
        for (UniversitySlots.UniversitySlot slot : view.slots) {
            if (slot.id().equals(view.school)) {
                currentSlot = slot;
            }
        }
        if (currentSlot == null || !currentSlot.crawlerPresent()) {
            body.append("<p>No programs in this slot. The columns are the same as UBC: ")
                    .append(Web.escape(String.join(", ", UniversitySlots.columns().subList(0, 4))))
                    .append(", then the detail fields. A crawler has not been added.</p>");
        }
        body.append("<div class=\"scroll\"><table><thead><tr>");
        body.append("<th>Program</th><th>Id</th><th>Faculty</th><th>Domestic</th><th>International</th><th>Min time</th><th>Offer rate</th>");
        body.append("</tr></thead><tbody>");
        if (view.programs.isEmpty()) {
            body.append("<tr><td colspan=\"7\">Nothing loaded for this university.</td></tr>");
        }
        for (Program program : view.programs) {
            body.append("<tr><td>").append(Web.escape(program.getName()))
                    .append("</td><td>").append(Web.escape(program.getDetail("Program ID")))
                    .append("</td><td>").append(Web.escape(program.getFaculty()))
                    .append("</td><td>").append(Web.escape(blank(program.getDetail("Tuition Domestic (First Year)"))))
                    .append("</td><td>").append(Web.escape(blank(program.getDetail("Tuition International (First Year)"))))
                    .append("</td><td>").append(Web.escape(months(program)))
                    .append("</td><td>").append(Web.escape(offer(program)))
                    .append("</td></tr>");
        }
        body.append("</tbody></table></div></section>");

        body.append("""
                <section id="apply">
                  <h2>Apply</h2>
                  <form method="post" action="/apply">
                    <label>Program
                      <select name="programId" required>
                        <option value="">Choose a program</option>
                        %s
                      </select>
                    </label>
                    <label>Details <textarea name="details" required placeholder="Why this program, and when you want to start."></textarea></label>
                    <button type="submit">Submit application</button>
                  </form>
                </section>
                """.formatted(programOptions(view.programs)));

        body.append("""
                <section id="applications">
                  <h2>Your applications</h2>
                  <p class="muted">This list checks for a decision every few seconds. <span id="polled">Waiting for the first check.</span></p>
                  <div class="scroll"><table>
                    <thead><tr><th>Program</th><th>Details</th><th>Status</th><th>Time</th></tr></thead>
                    <tbody id="app-rows">%s</tbody>
                  </table></div>
                </section>
                """.formatted(studentRows(view.applications)));

        body.append("""
                <section id="tools">
                  <h2>Word count, patterns, and past searches</h2>
                  <div class="split">
                    <form method="get" action="/student#tools">
                      <input type="hidden" name="school" value="%s">
                      <h3>Frequency on one URL</h3>
                      <label>Word <input name="freqWord" value="%s"></label>
                      <label>URL
                        <select name="freqUrl">
                          <option value="">Choose a page</option>
                          %s
                        </select>
                      </label>
                      <button type="submit">Count</button>
                      <p>%s</p>
                    </form>
                    <form method="get" action="/student#tools">
                      <input type="hidden" name="school" value="%s">
                      <h3>Pattern in page text</h3>
                      <label>Regular expression <input name="pattern" value="%s" placeholder="VGMMSC-[A-Z]+"></label>
                      <button type="submit">Find pattern</button>
                      <p>%s</p>
                    </form>
                  </div>
                  %s
                  <p><a href="/validate">Check tuition, dates, and program ids</a></p>
                  <h3>Words searched before</h3>
                  %s
                </section>
                """.formatted(
                Web.escape(view.school),
                Web.escape(view.freqWord),
                urlOptions(view.programs, view.freqUrl),
                Web.escape(view.freqMessage),
                Web.escape(view.school),
                Web.escape(view.pattern),
                Web.escape(view.patternMessage),
                patternTable(view.patternMatches),
                historyTable(view.history)));

        String script = """
                <script>
                const q = document.querySelector('#q');
                const chips = document.querySelector('#complete');
                let timer;
                function esc(s) {
                  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
                }
                if (q) {
                  q.addEventListener('input', () => {
                    clearTimeout(timer);
                    const parts = q.value.trim().split(/\\s+/);
                    const prefix = parts[parts.length - 1] || '';
                    timer = setTimeout(async () => {
                      if (!prefix) { chips.innerHTML = ''; return; }
                      const res = await fetch('/api/complete?prefix=' + encodeURIComponent(prefix));
                      if (!res.ok) return;
                      const words = await res.json();
                      chips.innerHTML = words.map(w => '<button type="button" class="chip">' + esc(w) + '</button>').join('');
                      chips.querySelectorAll('button').forEach(btn => btn.addEventListener('click', () => {
                        parts[parts.length - 1] = btn.textContent;
                        q.value = parts.join(' ');
                        q.focus();
                      }));
                    }, 150);
                  });
                }
                async function poll() {
                  const res = await fetch('/api/applications');
                  const stamp = document.querySelector('#polled');
                  if (!res.ok) { if (stamp) stamp.textContent = 'Could not check decisions.'; return; }
                  const rows = await res.json();
                  const body = document.querySelector('#app-rows');
                  if (!body) return;
                  if (!rows.length) {
                    body.innerHTML = '<tr><td colspan="4">You have not applied to a program yet.</td></tr>';
                  } else {
                    body.innerHTML = rows.map(r => '<tr><td>' + esc(r.programId) + '</td><td>' + esc(r.details)
                      + '</td><td class="status ' + esc(r.status) + '">' + esc(r.status) + '</td><td>' + esc(r.time) + '</td></tr>').join('');
                  }
                  if (stamp) stamp.textContent = 'Updated at ' + new Date().toLocaleTimeString() + '.';
                }
                poll();
                setInterval(poll, 3000);
                </script>
                """;
        return shell("Student portal", view.username, "student", body.toString(), script);
    }

    public static String admin(String username, String notice, String error, List<ApplicationStore.Application> applications) {
        String body = """
                <section>
                  <h2>All applications</h2>
                  %s
                  %s
                  <p class="muted">Accept or reject here. The student page reads the same file and picks up the decision on its next check. <span id="polled">Waiting for the first check.</span></p>
                  <div class="scroll"><table>
                    <thead><tr><th>Time</th><th>Student</th><th>Program</th><th>Details</th><th>Status</th><th></th></tr></thead>
                    <tbody id="admin-rows">%s</tbody>
                  </table></div>
                </section>
                """.formatted(banner(error, true), banner(notice, false), adminRows(applications));
        String script = """
                <script>
                function esc(s) {
                  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
                }
                function actions(r) {
                  const hidden = '<input type="hidden" name="programId" value="' + esc(r.programId)
                    + '"><input type="hidden" name="student" value="' + esc(r.student)
                    + '"><input type="hidden" name="time" value="' + esc(r.time) + '">';
                  return '<div class="actions"><form method="post" action="/decision">' + hidden
                    + '<input type="hidden" name="status" value="accepted"><button class="ok" type="submit">Accept</button></form>'
                    + '<form method="post" action="/decision">' + hidden
                    + '<input type="hidden" name="status" value="rejected"><button class="danger" type="submit">Reject</button></form></div>';
                }
                async function poll() {
                  const res = await fetch('/api/admin/applications');
                  const stamp = document.querySelector('#polled');
                  if (!res.ok) { if (stamp) stamp.textContent = 'Could not refresh.'; return; }
                  const rows = await res.json();
                  const body = document.querySelector('#admin-rows');
                  if (!rows.length) body.innerHTML = '<tr><td colspan="6">No applications yet.</td></tr>';
                  else body.innerHTML = rows.map(r => '<tr><td>' + esc(r.time) + '</td><td>' + esc(r.student)
                    + '</td><td>' + esc(r.programId) + '</td><td>' + esc(r.details)
                    + '</td><td class="status ' + esc(r.status) + '">' + esc(r.status) + '</td><td>' + actions(r) + '</td></tr>').join('');
                  if (stamp) stamp.textContent = 'Updated at ' + new Date().toLocaleTimeString() + '.';
                }
                poll();
                setInterval(poll, 3000);
                </script>
                """;
        return shell("Admin portal", username, "admin", body, script);
    }

    public static String validate(String username, FieldValidator.Report report, FieldValidator.Check check) {
        StringBuilder failures = new StringBuilder();
        if (report.failures().isEmpty()) {
            failures.append("<p class=\"ok-text\">No failing cells. ")
                    .append(report.cellsChecked())
                    .append(" cells were checked.</p>");
        } else {
            failures.append("<p class=\"error\">").append(report.failures().size()).append(" failing cells out of ")
                    .append(report.cellsChecked()).append(" checked.</p>");
            failures.append("<div class=\"scroll\"><table><thead><tr><th>Program</th><th>Column</th><th>Value</th><th>Rule</th></tr></thead><tbody>");
            for (FieldValidator.Failure failure : report.failures()) {
                failures.append("<tr><td>").append(Web.escape(failure.programName()))
                        .append("</td><td>").append(Web.escape(failure.column()))
                        .append("</td><td>").append(Web.escape(failure.value()))
                        .append("</td><td>").append(Web.escape(failure.kind()))
                        .append("</td></tr>");
            }
            failures.append("</tbody></table></div>");
        }
        String checkBlock = "";
        if (check != null) {
            checkBlock = "<p class=\"" + (check.valid() ? "ok-text" : "error") + "\">"
                    + Web.escape(check.message()) + "</p>";
        }
        String body = """
                <section>
                  <h2>Validate one value</h2>
                  <p class="muted">This checks the shape of a cell. It does not search page text. Pattern search is on the student page.</p>
                  <form method="get" action="/validate">
                    <div class="row">
                      <label>Kind
                        <select name="check">
                          <option value="tuition">Tuition</option>
                          <option value="date">Date</option>
                          <option value="programId">Program id</option>
                        </select>
                      </label>
                      <label>Value <input name="value" placeholder="VGMMSC-CA"></label>
                    </div>
                    <button type="submit">Check</button>
                  </form>
                  %s
                  <h3>Patterns</h3>
                  <pre class="formula">tuition: %s
                date: %s
                program id: %s</pre>
                </section>
                <section>
                  <h2>Catalog scan</h2>
                  %s
                </section>
                """.formatted(
                checkBlock,
                Web.escape(FieldValidator.TUITION),
                Web.escape(FieldValidator.DATE),
                Web.escape(FieldValidator.PROGRAM_ID),
                failures);
        return shell("Validation", username, "student", body, "");
    }

    public static String message(String title, String text, int ignored) {
        return shell(title, "", "", "<section><h2>" + Web.escape(title) + "</h2><p>" + Web.escape(text) + "</p></section>", "");
    }

    private static String shell(String title, String username, String role, String body, String script) {
        String nav;
        if (username == null || username.isEmpty()) {
            nav = "";
        } else {
            StringBuilder links = new StringBuilder();
            links.append("<a href=\"/student\">Search</a>");
            if ("admin".equals(role)) {
                links.append("<a href=\"/admin\">Applications</a>");
            }
            links.append("<a href=\"/validate\">Validation</a>");
            links.append("<form method=\"post\" action=\"/logout\"><button type=\"submit\">Log out</button></form>");
            nav = links.toString();
        }
        String who = username == null || username.isEmpty() ? "" : "<p>Signed in as " + Web.escape(username) + "</p>";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>%s</style>
                </head>
                <body>
                <header>
                  <div>
                    <h1>Graduate Program Finder</h1>
                    %s
                  </div>
                  <nav>%s</nav>
                </header>
                <main>%s</main>
                %s
                </body>
                </html>
                """.formatted(Web.escape(title), CSS, who, nav, body, script);
    }

    private static String banner(String text, boolean error) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return "<p class=\"" + (error ? "error" : "ok-text") + "\">" + Web.escape(text) + "</p>";
    }

    private static String suggestions(StudentView view) {
        if (view.suggestions == null || view.suggestions.isEmpty()) {
            if (view.searched && view.hits.isEmpty() && (view.error == null || view.error.isBlank())) {
                return "<p>No programs match, and nothing in the vocabulary is within edit distance 2.</p>";
            }
            return "";
        }
        StringBuilder html = new StringBuilder("<p>No programs match. Nearby words:</p><div class=\"chips\">");
        for (SpellChecker.Suggestion suggestion : view.suggestions) {
            html.append("<a class=\"chip\" href=\"/student?q=").append(Web.escape(suggestion.word()))
                    .append("&school=").append(Web.escape(view.school)).append("\">")
                    .append(Web.escape(suggestion.word()))
                    .append(" (").append(suggestion.distance()).append(")</a>");
        }
        html.append("</div>");
        return html.toString();
    }

    private static String hits(StudentView view) {
        if (!view.searched || view.hits.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<div class=\"scroll\"><table><thead><tr>");
        html.append("<th>Program</th><th>Keyword hits</th><th>Weight score</th><th>Weights used</th><th>Tuition</th><th>Months</th><th>Offer rate</th>");
        html.append("</tr></thead><tbody>");
        for (HitRow hit : view.hits) {
            html.append("<tr><td><a href=\"").append(Web.escape(hit.url)).append("\">")
                    .append(Web.escape(hit.name)).append("</a><br><span class=\"muted\">")
                    .append(Web.escape(hit.programId)).append(" · ").append(Web.escape(hit.university))
                    .append("</span></td><td>").append(hit.keywordScore)
                    .append("</td><td>").append(Web.escape(hit.weightScore))
                    .append("</td><td>").append(Web.escape(hit.breakdown))
                    .append("</td><td>").append(Web.escape(hit.tuition))
                    .append("</td><td>").append(Web.escape(hit.months))
                    .append("</td><td>").append(Web.escape(hit.offerRate))
                    .append("</td></tr>");
        }
        html.append("</tbody></table></div>");
        return html.toString();
    }

    private static String programOptions(List<Program> programs) {
        StringBuilder html = new StringBuilder();
        for (Program program : programs) {
            String id = program.getDetail("Program ID");
            if (id.isBlank()) {
                continue;
            }
            html.append("<option value=\"").append(Web.escape(id)).append("\">")
                    .append(Web.escape(id + " — " + program.getName()))
                    .append("</option>");
        }
        return html.toString();
    }

    private static String urlOptions(List<Program> programs, String selected) {
        StringBuilder html = new StringBuilder();
        for (Program program : programs) {
            String url = program.getUrl();
            if (url.isBlank()) {
                continue;
            }
            String mark = url.equals(selected) ? " selected" : "";
            html.append("<option value=\"").append(Web.escape(url)).append('"').append(mark).append('>')
                    .append(Web.escape(program.getName()))
                    .append("</option>");
        }
        return html.toString();
    }

    private static String studentRows(List<ApplicationStore.Application> applications) {
        if (applications == null || applications.isEmpty()) {
            return "<tr><td colspan=\"4\">You have not applied to a program yet.</td></tr>";
        }
        StringBuilder html = new StringBuilder();
        for (ApplicationStore.Application application : applications) {
            html.append("<tr><td>").append(Web.escape(application.programId()))
                    .append("</td><td>").append(Web.escape(application.details()))
                    .append("</td><td class=\"status ").append(Web.escape(application.status())).append("\">")
                    .append(Web.escape(application.status()))
                    .append("</td><td>").append(Web.escape(application.time()))
                    .append("</td></tr>");
        }
        return html.toString();
    }

    private static String adminRows(List<ApplicationStore.Application> applications) {
        if (applications == null || applications.isEmpty()) {
            return "<tr><td colspan=\"6\">No applications yet.</td></tr>";
        }
        StringBuilder html = new StringBuilder();
        for (ApplicationStore.Application application : applications) {
            html.append("<tr><td>").append(Web.escape(application.time()))
                    .append("</td><td>").append(Web.escape(application.student()))
                    .append("</td><td>").append(Web.escape(application.programId()))
                    .append("</td><td>").append(Web.escape(application.details()))
                    .append("</td><td class=\"status ").append(Web.escape(application.status())).append("\">")
                    .append(Web.escape(application.status()))
                    .append("</td><td><div class=\"actions\">")
                    .append(decisionForm(application, "accepted", "Accept", "ok"))
                    .append(decisionForm(application, "rejected", "Reject", "danger"))
                    .append("</div></td></tr>");
        }
        return html.toString();
    }

    private static String decisionForm(ApplicationStore.Application application, String status, String label, String style) {
        return "<form method=\"post\" action=\"/decision\">"
                + "<input type=\"hidden\" name=\"programId\" value=\"" + Web.escape(application.programId()) + "\">"
                + "<input type=\"hidden\" name=\"student\" value=\"" + Web.escape(application.student()) + "\">"
                + "<input type=\"hidden\" name=\"time\" value=\"" + Web.escape(application.time()) + "\">"
                + "<input type=\"hidden\" name=\"status\" value=\"" + status + "\">"
                + "<button class=\"" + style + "\" type=\"submit\">" + label + "</button></form>";
    }

    private static String patternTable(List<PatternFinder.Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<div class=\"scroll\"><table><thead><tr><th>URL</th><th>Match</th><th>Snippet</th></tr></thead><tbody>");
        for (PatternFinder.Match match : matches) {
            html.append("<tr><td>").append(Web.escape(match.url()))
                    .append("</td><td>").append(Web.escape(match.text()))
                    .append("</td><td>").append(Web.escape(match.snippet()))
                    .append("</td></tr>");
        }
        html.append("</tbody></table></div>");
        return html.toString();
    }

    private static String historyTable(List<finder.index.SearchFrequency.Entry> history) {
        if (history == null || history.isEmpty()) {
            return "<p>No searches have been recorded yet.</p>";
        }
        StringBuilder html = new StringBuilder("<div class=\"scroll\"><table><thead><tr><th>Word</th><th>Times searched</th></tr></thead><tbody>");
        for (finder.index.SearchFrequency.Entry entry : history) {
            html.append("<tr><td>").append(Web.escape(entry.word()))
                    .append("</td><td>").append(entry.count())
                    .append("</td></tr>");
        }
        html.append("</tbody></table></div>");
        return html.toString();
    }

    private static String months(Program program) {
        Double months = WeightModel.parseMonths(program.getDetail("Min Time to Completion"));
        if (months == null) {
            return "—";
        }
        return String.format(Locale.US, "%.1f mo", months);
    }

    private static String offer(Program program) {
        Double rate = WeightModel.offerRate(program);
        if (rate == null) {
            return "—";
        }
        return String.format(Locale.US, "%.0f%%", rate * 100.0);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public static final class HitRow {
        public String name = "";
        public String programId = "";
        public String university = "";
        public String url = "";
        public int keywordScore;
        public String weightScore = "";
        public String breakdown = "";
        public String tuition = "";
        public String months = "";
        public String offerRate = "";
    }

    public static final class StudentView {
        public String username = "";
        public String school = "ubc";
        public String query = "";
        public String error = "";
        public String notice = "";
        public boolean searched;
        public boolean weighted;
        public String tuition = "domestic";
        public String wCost = "1";
        public String wTime = "1";
        public String wAdmit = "1";
        public String fieldNote = "";
        public List<HitRow> hits = List.of();
        public List<SpellChecker.Suggestion> suggestions = List.of();
        public List<Program> programs = List.of();
        public List<UniversitySlots.UniversitySlot> slots = List.of();
        public List<ApplicationStore.Application> applications = List.of();
        public List<finder.index.SearchFrequency.Entry> history = List.of();
        public String freqWord = "";
        public String freqUrl = "";
        public String freqMessage = "";
        public String pattern = "";
        public String patternMessage = "";
        public List<PatternFinder.Match> patternMatches = List.of();
    }
}
