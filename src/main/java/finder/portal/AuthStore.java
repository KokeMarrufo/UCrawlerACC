package finder.portal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local accounts. Passwords are salted SHA-256 hashes, never stored in clear text. */
public final class AuthStore {
    public static final String STUDENT = "student";
    public static final String ADMIN = "admin";

    private final Path file;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public AuthStore(Path file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        users.clear();
        if (file == null || Files.notExists(file)) {
            return;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t");
            if (parts.length != 4) {
                System.out.println("Skipping account line that does not have four fields.");
                continue;
            }
            users.put(parts[0], new User(parts[0], parts[1], parts[2], parts[3]));
        }
    }

    public synchronized void ensureSeed() throws IOException {
        boolean changed = false;
        if (!users.containsKey(STUDENT)) {
            users.put(STUDENT, hashed(STUDENT, STUDENT, STUDENT));
            changed = true;
        }
        if (!users.containsKey(ADMIN)) {
            users.put(ADMIN, hashed(ADMIN, ADMIN, ADMIN));
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public synchronized boolean login(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        User user = users.get(username.trim());
        if (user == null) {
            return false;
        }
        byte[] expected = decodeHex(user.hashHex);
        byte[] actual = hash(decodeHex(user.saltHex), password);
        return expected.length > 0 && MessageDigest.isEqual(expected, actual);
    }

    public synchronized String role(String username) {
        User user = users.get(username);
        return user == null ? "" : user.role;
    }

    public synchronized String register(String username, String password) throws IOException {
        if (username == null || !username.matches("[A-Za-z][A-Za-z0-9._-]{2,31}")) {
            return "Usernames are 3 to 32 characters: a letter, then letters, digits, dot, underscore, or hyphen.";
        }
        if (password == null || password.length() < 4 || password.length() > 100) {
            return "Use a password of 4 to 100 characters.";
        }
        if (password.indexOf('\t') >= 0 || password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            return "Passwords cannot contain tabs or line breaks.";
        }
        if (users.containsKey(username)) {
            return "That username is already registered.";
        }
        if (ADMIN.equals(username)) {
            return "That username is already registered.";
        }
        users.put(username, hashed(username, STUDENT, password));
        save();
        return "";
    }

    private User hashed(String username, String role, String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String saltHex = hex(salt);
        String hashHex = hex(hash(salt, password));
        return new User(username, role, saltHex, hashHex);
    }

    private static byte[] hash(byte[] salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private synchronized void save() throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        StringBuilder body = new StringBuilder();
        body.append("# username role salt hash\n");
        for (User user : users.values()) {
            body.append(user.username).append('\t')
                    .append(user.role).append('\t')
                    .append(user.saltHex).append('\t')
                    .append(user.hashHex).append('\n');
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, body.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    private static byte[] decodeHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return new byte[0];
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return new byte[0];
            }
            out[i] = (byte) ((high << 4) + low);
        }
        return out;
    }

    private record User(String username, String role, String saltHex, String hashHex) {}
}
