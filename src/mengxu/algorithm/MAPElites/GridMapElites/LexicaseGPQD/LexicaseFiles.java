package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

final class LexicaseFiles {
    private LexicaseFiles() { }

    static BufferedWriter create(Path path) throws IOException {
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    static Path checkedOutput(Path requested, Path protectedRoot) throws IOException {
        Path output = requested.toAbsolutePath().normalize();
        if (output.getParent() == null || Files.exists(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Refusing to overwrite output: " + output);
        }
        java.util.Deque<Path> missing = new java.util.ArrayDeque<>();
        Path ancestor = output;
        while (!Files.exists(ancestor, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (ancestor.getParent() == null) { throw new IOException("No existing output ancestor: " + output); }
            missing.push(ancestor.getFileName());
            ancestor = ancestor.getParent();
        }
        Path resolved = ancestor.toRealPath();
        while (!missing.isEmpty()) { resolved = resolved.resolve(missing.pop()); }
        if (resolved.startsWith(protectedRoot.toRealPath())) {
            throw new IllegalArgumentException("Output must be outside protected input/source directory: " + protectedRoot);
        }
        return resolved;
    }

    static void row(BufferedWriter writer, Object... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) { writer.write(','); }
            String value = String.valueOf(fields[i]);
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                writer.write('\"');
                writer.write(value.replace("\"", "\"\""));
                writer.write('\"');
            } else { writer.write(value); }
        }
        writer.newLine();
    }

    static String hash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); }
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) { hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static void manifest(Path path, Properties values, boolean replace) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                replace ? new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING}
                        : new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW})) {
            values.store(writer, "Lexicase population baseline; no cumulative archive");
        }
    }
}
