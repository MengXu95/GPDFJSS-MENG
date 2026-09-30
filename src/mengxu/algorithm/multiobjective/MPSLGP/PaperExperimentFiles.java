package mengxu.algorithm.multiobjective.MPSLGP;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Shared deterministic configuration and artifact handling for paper training and testing. */
public final class PaperExperimentFiles {
    private PaperExperimentFiles() { }

    public static String serialize(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            result.append(escape(entry.getKey())).append('=').append(escape(entry.getValue())).append('\n');
        }
        return result.toString();
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c < 32 || c > 126) {
                escaped.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
            } else {
                if ("\\ :=#!".indexOf(c) >= 0) { escaped.append('\\'); }
                escaped.append(c);
            }
        }
        return escaped.toString();
    }

    public static Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    public static void atomicWrite(Path target, String content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) { result.append(String.format(Locale.ROOT, "%02x", b & 255)); }
        return result.toString();
    }

    public static String sha256(byte[] bytes) { return hex(digest().digest(bytes)); }

    public static String fileHash(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); }
        }
        return hex(digest.digest());
    }

    public static String javaExecutable() {
        return Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
    }

    public static String absoluteClassPath() {
        List<String> entries = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator), -1)) {
            entries.add(Paths.get(entry.isEmpty() ? "." : entry).toAbsolutePath().normalize().toString());
        }
        return String.join(File.pathSeparator, entries);
    }

    public static String commandText(List<String> command) {
        List<String> quoted = new ArrayList<>();
        for (String argument : command) { quoted.add("'" + argument.replace("'", "''") + "'"); }
        return "& " + String.join(" ", quoted);
    }

    public static String csv(Object... values) {
        List<String> fields = new ArrayList<>();
        for (Object value : values) { fields.add("\"" + value.toString().replace("\"", "\"\"") + "\""); }
        return String.join(",", fields);
    }
}
