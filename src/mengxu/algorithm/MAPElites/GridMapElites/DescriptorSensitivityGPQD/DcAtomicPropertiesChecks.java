package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Standalone deterministic fault injection plus real Windows deny-delete sharing checks. */
public final class DcAtomicPropertiesChecks {
    private static final byte[] OLD = "old=unchanged\r\n".getBytes(StandardCharsets.UTF_8);

    private DcAtomicPropertiesChecks() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: DcAtomicPropertiesChecks [new-fixture-directory]");
        }
        boolean temporary = args.length == 0;
        Path fixture = temporary ? Files.createTempDirectory("dc-atomic-properties-checks-")
                : Files.createDirectory(Paths.get(args[0]).toAbsolutePath().normalize());
        try {
            runChecks(fixture);
            System.out.println("DC ATOMIC PROPERTIES CHECKS PASSED");
            if (!temporary) {
                System.out.println("Retained fixture/error/Windows-lock evidence: " + fixture);
            }
        } finally {
            if (temporary) {
                try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(fixture)) {
                    for (Path file : files) {
                        Files.delete(file);
                    }
                }
                Files.delete(fixture);
            }
        }
    }

    /** Existing empty directory; all test targets are new files inside this directory. */
    public static void runChecks(Path fixture) throws Exception {
        if (fixture == null || !Files.isDirectory(fixture)) {
            throw new IllegalArgumentException("An existing empty fixture directory is required");
        }
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(fixture)) {
            require(!files.iterator().hasNext(), "fixture directory must be empty");
        }
        checkRecovery(fixture);
        checkPermanentDenial(fixture);
        checkOtherIo(fixture);
        checkSerializationFailure(fixture);
        checkInterruptions(fixture);
        checkCreateOnly(fixture);
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            checkWindowsSharing(fixture);
        } else {
            System.out.println("REAL WINDOWS LOCK CHECKS SKIPPED: operating system is not Windows");
        }
    }

    private static void checkRecovery(Path fixture) throws Exception {
        Path target = oldTarget(fixture, "fake-recovery.properties");
        Properties values = values();
        int[] moves = {0};
        List<Long> delays = new ArrayList<>();
        Set<Path> before = pending(fixture);
        String text;
        try (StderrCapture capture = new StderrCapture()) {
            DcAtomicProperties.write(target, values, true, (ready, destination) -> {
                moves[0]++;
                sameDirectory(ready, destination);
                sameProperties(values, load(ready), "fully serialized before every move");
                if (moves[0] <= 2) {
                    oldBytes(target);
                    throw new AccessDeniedException(ready.toString(), destination.toString(), "injected deny-delete");
                }
                atomicMove(ready, destination);
            }, delays::add, 4);
            text = capture.text();
        }
        require(moves[0] == 3 && delays.equals(Arrays.asList(50L, 100L)), "two denials then success/backoff");
        require(occurrences(text, "first retry") == 1 && occurrences(text, "recovered atomic publication") == 1
                && !text.contains("terminal error"), "first retry and recovery are explicit, not spammed");
        sameProperties(values, load(target), "recovered UTF-8 target");
        require(before.equals(pending(fixture)), "successful move leaves no pending temporary");
        Files.write(fixture.resolve("fake-recovery.stderr.txt"), text.getBytes(StandardCharsets.UTF_8));
        DcAtomicProperties.write(target, values, true);
        sameProperties(values, load(target), "public default atomic replacement");
    }

    private static void checkPermanentDenial(Path fixture) throws Exception {
        Path target = oldTarget(fixture, "fake-permanent.properties");
        Properties values = values();
        int[] moves = {0};
        Path[] ready = {null};
        AccessDeniedException[] last = {null};
        List<Long> delays = new ArrayList<>();
        IOException failure;
        String text;
        try (StderrCapture capture = new StderrCapture()) {
            failure = expectIo(() -> DcAtomicProperties.write(target, values, true, (source, destination) -> {
                ready[0] = source;
                moves[0]++;
                last[0] = new AccessDeniedException(source.toString(), destination.toString(), "permanent injected denial");
                throw last[0];
            }, delays::add, 5));
            text = capture.text();
        }
        require(moves[0] == 5 && delays.equals(Arrays.asList(50L, 100L, 200L, 250L)), "bounded retry/backoff budget");
        require(failure.getCause() == last[0], "last denial preserved as the terminal cause");
        require(occurrences(text, "first retry") == 1 && occurrences(text, "terminal error") == 1,
                "permanent error is explicit");
        oldBytes(target);
        readyEvidence(failure, ready[0], target, 5, values);
        errorFile(fixture.resolve("fake-permanent-error.txt"), failure);
        Files.write(fixture.resolve("fake-permanent.stderr.txt"), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void checkOtherIo(Path fixture) throws Exception {
        for (boolean unsupported : new boolean[]{false, true}) {
            Path target = oldTarget(fixture, unsupported ? "atomic-unsupported.properties" : "other-io.properties");
            Properties values = values();
            int[] moves = {0};
            int[] sleeps = {0};
            Path[] ready = {null};
            IOException marker = unsupported
                    ? new AtomicMoveNotSupportedException("source", "destination", "injected unsupported atomic move")
                    : new IOException("injected non-sharing I/O failure");
            IOException failure = expectIo(() -> DcAtomicProperties.write(target, values, true, (source, destination) -> {
                moves[0]++;
                ready[0] = source;
                throw marker;
            }, delay -> sleeps[0]++, 40));
            require(moves[0] == 1 && sleeps[0] == 0 && failure.getCause() == marker,
                    "other IOException is not retried and no non-atomic fallback runs");
            oldBytes(target);
            readyEvidence(failure, ready[0], target, 1, values);
        }
    }

    private static void checkSerializationFailure(Path fixture) throws Exception {
        Path target = oldTarget(fixture, "serialization-failure.properties");
        Set<Path> before = pending(fixture);
        IOException marker = new IOException("injected serialization failure");
        Properties broken = new Properties() {
            @Override
            public void store(Writer writer, String comments) throws IOException {
                writer.write("partial=yes\n");
                throw marker;
            }
        };
        IOException actual = expectIo(() -> DcAtomicProperties.write(target, broken, true,
                (ready, destination) -> { throw new AssertionError("Incomplete serialization reached move"); },
                delay -> { throw new AssertionError("Serialization failure was retried"); }, 3));
        require(actual == marker, "serialization exception is not masked");
        require(before.equals(pending(fixture)), "incomplete temporary removed after writer close");
        oldBytes(target);
        Properties invalid = new Properties();
        invalid.put("not-a-string", Integer.valueOf(17));
        try {
            DcAtomicProperties.write(target, invalid, true);
            throw new AssertionError("Non-string properties unexpectedly serialized");
        } catch (ClassCastException expected) {
            require(before.equals(pending(fixture)), "runtime serialization failure also cleans incomplete temporary");
        }
        oldBytes(target);
    }

    private static void checkInterruptions(Path fixture) throws Exception {
        require(!Thread.currentThread().isInterrupted(), "test starts without an interrupt");
        Path target = oldTarget(fixture, "interrupted-retry.properties");
        Properties values = values();
        Path[] ready = {null};
        int[] moves = {0};
        InterruptedException marker = new InterruptedException("injected interrupted sleeper");
        try {
            IOException failure = expectIo(() -> DcAtomicProperties.write(target, values, true, (source, destination) -> {
                ready[0] = source;
                moves[0]++;
                throw new AccessDeniedException(source.toString(), destination.toString(), "deny before interrupt");
            }, delay -> { throw marker; }, 40));
            require(failure instanceof InterruptedIOException && failure.getCause() == marker,
                    "sleep interruption is an InterruptedIOException with its cause");
            require(Thread.currentThread().isInterrupted() && moves[0] == 1, "interrupt flag preserved, no second move");
            Thread.interrupted();
            readyEvidence(failure, ready[0], target, 1, values);
            oldBytes(target);
        } finally {
            Thread.interrupted();
        }

        Set<Path> before = pending(fixture);
        Path initial = oldTarget(fixture, "initially-interrupted.properties");
        try {
            Thread.currentThread().interrupt();
            IOException failure = expectIo(() -> DcAtomicProperties.write(initial, values, true,
                    (source, destination) -> { throw new AssertionError("Pre-interrupted thread attempted publication"); },
                    delay -> { throw new AssertionError("Pre-interrupted thread slept"); }, 40));
            require(failure instanceof InterruptedIOException && Thread.currentThread().isInterrupted(),
                    "initial interrupt is preserved");
            Thread.interrupted();
            require(before.equals(pending(fixture)), "pre-interrupted call does not start serialization");
            oldBytes(initial);
        } finally {
            Thread.interrupted();
        }
    }

    private static void checkCreateOnly(Path fixture) throws Exception {
        Path existing = oldTarget(fixture, "create-only-existing.properties");
        Set<Path> before = pending(fixture);
        IOException failure = expectIo(() -> DcAtomicProperties.write(existing, values(), false,
                (ready, target) -> { throw new AssertionError("Create-only guard called replacement mover"); },
                delay -> { throw new AssertionError("Create-only guard retried"); }, 40));
        require(failure instanceof FileAlreadyExistsException && before.equals(pending(fixture)),
                "existing target rejected before creating a temporary");
        oldBytes(existing);

        Path created = fixture.resolve("create-only-new.properties");
        DcAtomicProperties.write(created, values(), false);
        sameProperties(values(), load(created), "create-only atomic hard-link publication");
        require(before.equals(pending(fixture)), "successful create-only publication cleans its temporary link");

        Path raced = fixture.resolve("create-only-race.properties");
        Properties values = values();
        Properties competingCreator = new Properties() {
            @Override
            public void store(Writer writer, String comments) throws IOException {
                values.store(writer, comments);
                Files.write(raced, OLD, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
        };
        IOException collision = expectIo(() -> DcAtomicProperties.write(raced, competingCreator, false));
        require(collision.getCause() instanceof FileAlreadyExistsException, "racing creator wins without being overwritten");
        oldBytes(raced);
        Set<Path> added = pending(fixture);
        added.removeAll(before);
        require(added.size() == 1, "losing create-only publication retains its ready temporary");
        readyEvidence(collision, added.iterator().next(), raced, 1, values);
    }

    private static void checkWindowsSharing(Path fixture) throws Exception {
        Properties evidence = new Properties();
        evidence.setProperty("version", DcAtomicProperties.VERSION);
        evidence.setProperty("os", System.getProperty("os.name"));
        evidence.setProperty("share", "FileShare.ReadWrite; FileShare.Delete excluded");
        Properties values = values();
        Path target = oldTarget(fixture, "real-transient.properties");
        int[] denials = {0};
        int[] moves = {0};
        String text;
        try (LockHolder holder = LockHolder.start(fixture, target, "real-transient", 300);
             StderrCapture capture = new StderrCapture()) {
            holder.awaitReady();
            DcAtomicProperties.write(target, values, true, (ready, destination) -> {
                moves[0]++;
                sameProperties(values, load(ready), "closed complete temporary under real Windows sharing denial");
                try {
                    atomicMove(ready, destination);
                } catch (AccessDeniedException denied) {
                    denials[0]++;
                    if (denials[0] == 1) {
                        holder.release();
                    }
                    throw denied;
                }
            }, Thread::sleep, 40);
            holder.awaitReleased();
            text = capture.text();
        }
        require(denials[0] >= 1 && moves[0] == denials[0] + 1,
                "at least one actual Windows AccessDeniedException, then successful atomic move");
        require(text.contains("first retry") && text.contains("recovered atomic publication"), "real retry/recovery logged");
        sameProperties(values, load(target), "real Windows lock release publishes new content");
        Files.write(fixture.resolve("real-transient.stderr.txt"), text.getBytes(StandardCharsets.UTF_8));
        evidence.setProperty("transient.access-denials", Integer.toString(denials[0]));
        evidence.setProperty("transient.move-attempts", Integer.toString(moves[0]));
        evidence.setProperty("transient.release-delay-ms", "300");
        System.out.println("REAL WINDOWS TRANSIENT: AccessDeniedException count=" + denials[0]
                + ", atomic move attempts=" + moves[0] + ", updated content verified");

        Path persistent = oldTarget(fixture, "real-permanent.properties");
        int[] persistentDenials = {0};
        Path[] pending = {null};
        IOException terminal;
        try (LockHolder holder = LockHolder.start(fixture, persistent, "real-permanent", 0)) {
            holder.awaitReady();
            terminal = expectIo(() -> DcAtomicProperties.write(persistent, values, true, (ready, destination) -> {
                pending[0] = ready;
                try {
                    atomicMove(ready, destination);
                } catch (AccessDeniedException denied) {
                    persistentDenials[0]++;
                    throw denied;
                }
            }, Thread::sleep, 4));
            require(persistentDenials[0] == 4 && terminal.getCause() instanceof AccessDeniedException,
                    "real persistent sharing denial exhausts exactly the bounded budget");
            require(!Files.exists(holder.released), "persistent lock still held at terminal failure");
            oldBytes(persistent);
            readyEvidence(terminal, pending[0], persistent, 4, values);
        }
        oldBytes(persistent);
        readyEvidence(terminal, pending[0], persistent, 4, values);
        errorFile(fixture.resolve("real-permanent-error.txt"), terminal);
        evidence.setProperty("persistent.access-denials", Integer.toString(persistentDenials[0]));
        evidence.setProperty("persistent.old-bytes-intact", "true");
        evidence.setProperty("persistent.ready-temp-readable", "true");
        evidence.setProperty("persistent.target", persistent.toAbsolutePath().toString());
        evidence.setProperty("persistent.pending", pending[0].toAbsolutePath().toString());
        try (Writer writer = Files.newBufferedWriter(fixture.resolve("windows-lock-evidence.properties"),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            evidence.store(writer, "Actual bounded Windows FileStream deny-delete sharing checks");
        }
        System.out.println("REAL WINDOWS PERSISTENT: AccessDeniedException count=" + persistentDenials[0]
                + ", old bytes intact and fully readable pending temporary retained");
    }

    private static Properties values() {
        Properties values = new Properties();
        values.setProperty("status", "complete");
        values.setProperty("unicode", "snow \u96ea; caf\u00e9; \u03bb");
        values.setProperty("escaped key:=!", "line one\nline two\t\\value");
        char[] large = new char[32768];
        Arrays.fill(large, 'x');
        values.setProperty("buffer-crossing-value", new String(large));
        return values;
    }

    private static Path oldTarget(Path fixture, String name) throws IOException {
        Path target = fixture.resolve(name);
        Files.write(target, OLD, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return target;
    }

    private static void oldBytes(Path target) throws IOException {
        require(Arrays.equals(OLD, Files.readAllBytes(target)), "old target bytes intact: " + target);
    }

    private static Properties load(Path path) throws IOException {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        return values;
    }

    private static void sameProperties(Properties expected, Properties actual, String label) {
        require(expected.equals(actual), label);
    }

    private static void sameDirectory(Path ready, Path target) {
        require(ready.isAbsolute() && target.isAbsolute()
                && ready.getParent().equals(target.getParent()), "absolute same-directory publication paths");
    }

    private static void readyEvidence(IOException failure, Path ready, Path target, int attempts,
                                      Properties expected) throws IOException {
        require(ready != null && ready.isAbsolute() && Files.isRegularFile(ready), "ready temporary retained");
        require(failure.getMessage().contains(ready.toString())
                && failure.getMessage().contains(target.toAbsolutePath().toString())
                && failure.getMessage().contains("attempts=" + attempts + "/"), "contextual paths/attempt count");
        sameDirectory(ready, target.toAbsolutePath());
        sameProperties(expected, load(ready), "fully readable pending properties");
    }

    private static Set<Path> pending(Path fixture) throws IOException {
        Set<Path> paths = new HashSet<>();
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(fixture, ".dc-*.tmp")) {
            for (Path file : files) {
                paths.add(file.toAbsolutePath().normalize());
            }
        }
        return paths;
    }

    private static void atomicMove(Path ready, Path target) throws IOException {
        Files.move(ready, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static IOException expectIo(IoOperation operation) throws Exception {
        try {
            operation.run();
        } catch (IOException expected) {
            return expected;
        }
        throw new AssertionError("Expected IOException");
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }

    private static void errorFile(Path path, IOException failure) throws IOException {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        Files.write(path, text.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Atomic properties check failed: " + label);
        }
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws Exception;
    }

    private static final class StderrCapture implements AutoCloseable {
        private final PrintStream original = System.err;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final PrintStream capture = new PrintStream(bytes, true, "UTF-8");

        StderrCapture() throws IOException {
            System.setErr(capture);
        }

        String text() throws IOException {
            capture.flush();
            return bytes.toString("UTF-8");
        }

        @Override
        public void close() {
            System.setErr(original);
            capture.close();
        }
    }

    private static final class LockHolder implements AutoCloseable {
        private final Process process;
        private final Path ready;
        private final Path release;
        private final Path released;
        private final Path log;

        private LockHolder(Process process, Path ready, Path release, Path released, Path log) {
            this.process = process;
            this.ready = ready;
            this.release = release;
            this.released = released;
            this.log = log;
        }

        static LockHolder start(Path fixture, Path target, String stem, int releaseDelay) throws IOException {
            Path ready = fixture.resolve(stem + ".ready");
            Path release = fixture.resolve(stem + ".release");
            Path released = fixture.resolve(stem + ".released");
            Path log = fixture.resolve(stem + ".powershell.log");
            String script = "$ErrorActionPreference='Stop'; "
                    + "$lock=[System.IO.File]::Open(" + quote(target) + ",[System.IO.FileMode]::Open,"
                    + "[System.IO.FileAccess]::ReadWrite,[System.IO.FileShare]::ReadWrite); "
                    + "try { [System.IO.File]::WriteAllText(" + quote(ready) + ",'FileShare.ReadWrite; no Delete'); "
                    + "Write-Output 'Holding target with FileShare.ReadWrite and no FileShare.Delete'; "
                    + "$clock=[System.Diagnostics.Stopwatch]::StartNew(); "
                    + "while (-not [System.IO.File]::Exists(" + quote(release) + ")) { "
                    + "if ($clock.ElapsedMilliseconds -gt 15000) { throw 'Bounded release wait expired' }; "
                    + "Start-Sleep -Milliseconds 10 }; Start-Sleep -Milliseconds " + releaseDelay
                    + " } finally { $lock.Dispose() }; "
                    + "[System.IO.File]::WriteAllText(" + quote(released) + ",'released')";
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            return new LockHolder(process, ready, release, released, log);
        }

        void awaitReady() throws IOException, InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(ready)) {
                if (!process.isAlive() || System.nanoTime() >= deadline) {
                    throw new IOException("Windows lock holder did not become ready; log=" + log);
                }
                Thread.sleep(10);
            }
        }

        void release() throws IOException {
            if (!Files.exists(release)) {
                Files.write(release, new byte[]{1}, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
        }

        void awaitReleased() throws IOException, InterruptedException {
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0 || !Files.exists(released)) {
                throw new IOException("Windows lock holder did not release successfully; log=" + log);
            }
        }

        @Override
        public void close() throws IOException {
            release();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        throw new IOException("Cannot stop owned Windows lock-holder process; log=" + log);
                    }
                    throw new IOException("Owned Windows lock holder exceeded its bounded close wait; log=" + log);
                }
                if (process.exitValue() != 0 || !Files.exists(released)) {
                    throw new IOException("Windows lock holder failed; log=" + log);
                }
            } catch (InterruptedException interrupted) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                InterruptedIOException failure = new InterruptedIOException("Interrupted closing owned Windows lock holder");
                failure.initCause(interrupted);
                throw failure;
            }
        }

        private static String quote(Path path) {
            return "'" + path.toAbsolutePath().toString().replace("'", "''") + "'";
        }
    }
}
