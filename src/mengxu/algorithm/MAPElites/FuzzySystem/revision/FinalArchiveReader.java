package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FinalArchiveReader {
    public static final class Archive {
        private final int declaredCount;
        private final List<ArchiveCandidate> candidates;
        private final String sourceHash;

        private Archive(int declaredCount, List<ArchiveCandidate> candidates, String sourceHash) {
            this.declaredCount = declaredCount;
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.sourceHash = sourceHash;
        }

        public int declaredCount() { return declaredCount; }
        public List<ArchiveCandidate> candidates() { return candidates; }
        public String sourceHash() { return sourceHash; }
    }

    public Archive read(Path source) throws IOException {
        String sourceHash = RevisionCsv.fileHash(source);
        try (BufferedReader input = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            Cursor cursor = new Cursor(source, input);
            String line;
            do {
                line = cursor.next();
            } while (line != null && !line.trim().equals("All individuals in GridMAP:"));
            if (line == null) {
                throw cursor.error("Final GridMAP section is missing; generation-best rules are not an archive");
            }
            line = cursor.nonBlank();
            if (line == null || !line.trim().startsWith("GridMAP number:")) {
                throw cursor.error("Missing GridMAP count");
            }
            int count;
            try {
                count = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            } catch (NumberFormatException e) {
                throw cursor.error("Invalid GridMAP count", e);
            }
            if (count <= 0) {
                throw cursor.error("The final archive must contain at least one rule");
            }
            List<ArchiveCandidate> candidates = new ArrayList<>();
            for (int ordinal = 0; ordinal < count; ordinal++) {
                line = cursor.nonBlank();
                if (line == null || !line.trim().equals("Evaluated: true")) {
                    throw cursor.error("Expected evaluated archive member " + ordinal);
                }
                String fitnessLine = cursor.nonBlank();
                if (fitnessLine == null || !fitnessLine.trim().matches("Fitness:\\s*\\[[^\\]]+\\]")) {
                    throw cursor.error("Expected one scalar fitness for archive member " + ordinal);
                }
                double fitness;
                try {
                    fitness = Double.parseDouble(fitnessLine.substring(
                            fitnessLine.indexOf('[') + 1, fitnessLine.indexOf(']')).trim());
                } catch (NumberFormatException e) {
                    throw cursor.error("Invalid scalar archive fitness", e);
                }
                cursor.expect("Tree 0:");
                String sequencing = cursor.expression();
                cursor.expect("Tree 1:");
                String routing = cursor.expression();
                try {
                    candidates.add(new ArchiveCandidate(source, ordinal, fitness, sequencing, routing));
                } catch (IllegalArgumentException e) {
                    throw cursor.error("Invalid archive member " + ordinal, e);
                }
            }
            line = cursor.nonBlank();
            if (line != null && !line.trim().equals("Best Individual of Run:")) {
                throw cursor.error("Archive count does not match the section boundary: " + line.trim());
            }
            if (!sourceHash.equals(RevisionCsv.fileHash(source))) {
                throw cursor.error("Source archive changed during reading");
            }
            return new Archive(count, candidates, sourceHash);
        }
    }

    private static final class Cursor {
        private final Path source;
        private final BufferedReader input;
        private int lineNumber;

        Cursor(Path source, BufferedReader input) {
            this.source = source;
            this.input = input;
        }

        String next() throws IOException {
            String value = input.readLine();
            if (value != null) { lineNumber++; }
            return value;
        }

        String nonBlank() throws IOException {
            String value;
            do { value = next(); } while (value != null && value.trim().isEmpty());
            return value;
        }

        void expect(String expected) throws IOException {
            String actual = nonBlank();
            if (actual == null || !actual.trim().equals(expected)) {
                throw error("Expected " + expected + ", found " + actual);
            }
        }

        String expression() throws IOException {
            String first = nonBlank();
            if (first == null) { throw error("Missing tree expression"); }
            StringBuilder expression = new StringBuilder(first.trim());
            int balance = balance(first);
            while (balance > 0) {
                String continuation = next();
                if (continuation == null || continuation.trim().startsWith("Tree ")
                        || continuation.trim().startsWith("Evaluated:")) {
                    throw error("Unclosed tree expression");
                }
                expression.append(' ').append(continuation.trim());
                balance += balance(continuation);
            }
            if (balance != 0) { throw error("Unbalanced tree expression"); }
            return expression.toString();
        }

        int balance(String value) {
            int result = 0;
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == '(') { result++; }
                if (value.charAt(i) == ')') { result--; }
            }
            return result;
        }

        IOException error(String message) {
            return new IOException(source + ":" + lineNumber + ": " + message);
        }

        IOException error(String message, Exception cause) {
            return new IOException(source + ":" + lineNumber + ": " + message, cause);
        }
    }
}
