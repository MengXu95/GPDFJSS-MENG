package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class RevisionCsv implements AutoCloseable {
    private final BufferedWriter writer;

    RevisionCsv(Path path, Object... header) throws IOException {
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        row(header);
    }

    void row(Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) { writer.write(','); }
            String value = values[i] == null ? "" : values[i].toString();
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                writer.write('"');
                writer.write(value.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(value);
            }
        }
        writer.newLine();
    }

    @Override
    public void close() throws IOException { writer.close(); }

    static String fileHash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = input.read(buffer)) != -1) { digest.update(buffer, 0, size); }
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
