package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** A worker watches its exact coordinator identity, including PID reuse. */
public final class EWorkerLease implements AutoCloseable {
    public static final String PARENT_PID = "e.parallel.parent-pid";
    public static final String PARENT_START = "e.parallel.parent-start";
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread watcher;

    private EWorkerLease(long pid, Instant start) {
        watcher = new Thread(() -> {
            while (!closed.get()) {
                if (!matches(pid, start)) {
                    if (!closed.get()) {
                        System.err.println("E worker lease lost coordinator PID " + pid
                                + " (expected start " + start + "); halting this worker to prevent an orphan.");
                        System.err.flush();
                        Runtime.getRuntime().halt(74);
                    }
                    return;
                }
                try { Thread.sleep(200); }
                catch (InterruptedException stopped) { return; }
            }
        }, "e-worker-parent-lease");
        watcher.setDaemon(true);
        watcher.start();
    }

    private EWorkerLease() { watcher = null; }

    /** Without either property this is an inert lease, for ordinary non-pool execution. */
    public static EWorkerLease start() throws IOException {
        String pidText = System.getProperty(PARENT_PID);
        String startText = System.getProperty(PARENT_START);
        if (pidText == null && startText == null) { return new EWorkerLease(); }
        if (pidText == null || startText == null) {
            throw new IOException("Incomplete worker coordinator lease properties");
        }
        final long pid;
        final Instant start;
        try {
            pid = Long.parseLong(pidText);
            start = Instant.parse(startText);
            if (pid <= 0 || pid == ProcessHandle.current().pid()) {
                throw new IllegalArgumentException("Invalid coordinator PID");
            }
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            throw new IOException("Invalid worker coordinator identity: " + pidText + " / " + startText, invalid);
        }
        if (!matches(pid, start)) {
            throw new IOException("Worker coordinator is absent or has a different start instant: PID " + pid);
        }
        return new EWorkerLease(pid, start);
    }

    private static boolean matches(long pid, Instant expected) {
        Optional<ProcessHandle> parent = ProcessHandle.of(pid);
        return parent.isPresent() && parent.get().isAlive()
                && parent.get().info().startInstant().map(expected::equals).orElse(false);
    }

    @Override public void close() {
        closed.set(true);
        if (watcher != null) { watcher.interrupt(); }
    }
}
