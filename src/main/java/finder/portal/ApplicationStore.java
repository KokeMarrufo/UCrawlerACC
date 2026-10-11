package finder.portal;

import finder.crawl.CsvRows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Applications file: program id, student, details, status, time.
 * Status is pending, accepted, or rejected.
 */
public final class ApplicationStore {
    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String REJECTED = "rejected";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS 'UTC'");

    private final Path file;
    private final List<Application> applications = new ArrayList<>();

    public ApplicationStore(Path file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        applications.clear();
        if (file == null || Files.notExists(file)) {
            return;
        }
        List<List<String>> rows = CsvRows.read(file);
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() < 5) {
                continue;
            }
            if (i == 0 && "programId".equals(row.get(0))) {
                continue;
            }
            String status = row.get(3);
            if (!PENDING.equals(status) && !ACCEPTED.equals(status) && !REJECTED.equals(status)) {
                System.out.println("Skipping application with status " + status);
                continue;
            }
            applications.add(new Application(row.get(0), row.get(1), row.get(2), status, row.get(4)));
        }
    }

    public synchronized Application add(String programId, String student, String details) throws IOException {
        String safeDetails = details == null ? "" : details.replace('\r', ' ').replace('\n', ' ').trim();
        Application application = new Application(
                programId,
                student,
                safeDetails,
                PENDING,
                ZonedDateTime.now(ZoneOffset.UTC).format(TIME));
        applications.add(application);
        save();
        return application;
    }

    public synchronized boolean decide(String programId, String student, String time, String status) throws IOException {
        if (!ACCEPTED.equals(status) && !REJECTED.equals(status) && !PENDING.equals(status)) {
            return false;
        }
        for (int i = 0; i < applications.size(); i++) {
            Application current = applications.get(i);
            if (current.programId().equals(programId)
                    && current.student().equals(student)
                    && current.time().equals(time)) {
                applications.set(i, new Application(programId, student, current.details(), status, time));
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized List<Application> forStudent(String student) {
        List<Application> mine = new ArrayList<>();
        for (Application application : applications) {
            if (application.student().equals(student)) {
                mine.add(application);
            }
        }
        return mine;
    }

    public synchronized List<Application> all() {
        return List.copyOf(applications);
    }

    private void save() throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        StringBuilder body = new StringBuilder();
        body.append("programId,student,details,status,time\n");
        for (Application application : applications) {
            body.append(CsvRows.quote(application.programId())).append(',')
                    .append(CsvRows.quote(application.student())).append(',')
                    .append(CsvRows.quote(application.details())).append(',')
                    .append(CsvRows.quote(application.status())).append(',')
                    .append(CsvRows.quote(application.time())).append('\n');
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, body.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public record Application(String programId, String student, String details, String status, String time) {}
}
