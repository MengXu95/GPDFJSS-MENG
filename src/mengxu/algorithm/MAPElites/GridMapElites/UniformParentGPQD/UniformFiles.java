package mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

public final class UniformFiles {
    private UniformFiles() { }

    public static BufferedWriter create(Path path) throws IOException {
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    public static Path checkedOutput(Path requested, Path protectedRoot) throws IOException {
        Path output = requested.toAbsolutePath().normalize();
        if (output.getParent() == null || Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Refusing to overwrite output: " + output);
        }
        Deque<Path> missing = new ArrayDeque<>();
        Path ancestor = output;
        while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            if (ancestor.getParent() == null) { throw new IOException("No existing output ancestor: " + output); }
            missing.push(ancestor.getFileName());
            ancestor = ancestor.getParent();
        }
        Path resolved = ancestor.toRealPath();
        while (!missing.isEmpty()) { resolved = resolved.resolve(missing.pop()); }
        if (resolved.startsWith(protectedRoot.toRealPath())) {
            throw new IllegalArgumentException("Output must be outside the protected source/input directory");
        }
        return resolved;
    }

    public static void row(BufferedWriter writer, Object... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) { writer.write(','); }
            String value = String.valueOf(fields[i]);
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                writer.write("\"" + value.replace("\"", "\"\"") + "\"");
            } else { writer.write(value); }
        }
        writer.newLine();
    }

    public static void manifest(Path path, Properties values, boolean replace) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                replace ? new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING}
                        : new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW})) {
            values.store(writer, "Archive-parent comparison; inherited QDGP evaluation and admission");
        }
    }
}
