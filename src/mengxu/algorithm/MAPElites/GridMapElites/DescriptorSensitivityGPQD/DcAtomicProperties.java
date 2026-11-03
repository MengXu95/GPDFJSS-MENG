package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.NoSuchFileException;
import java.util.Properties;

/**
 * Atomic UTF-8 manifest publication, without changes to training or recovery semantics.
 * A ready temporary is retained on publication failure and identified in the exception.
 */
public final class DcAtomicProperties {
    public static final String VERSION = "atomic-manifest-retry-v1";
    private static final int DEFAULT_ATTEMPTS = 40;
    private static final String COMMENT = "QDGPf descriptor sensitivity; separate training/test stages";

    private DcAtomicProperties() { }

    @FunctionalInterface
    interface MoveOperation {
        void move(Path temporary, Path target) throws IOException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    /**
     * Serialize and close a same-directory temporary before publication. Replacement
     * uses ATOMIC_MOVE/REPLACE_EXISTING, retrying only AccessDeniedException from move
     * (40 attempts, at most 9,350 ms of backoff). No non-atomic fallback is used.
     *
     * For create-only publication, createLink is the atomic no-clobber operation:
     * ATOMIC_MOVE may overwrite an existing destination even without REPLACE_EXISTING.
     * Unsupported hard links fail explicitly, without falling back to a racy rename.
     */
    public static void write(Path target, Properties values, boolean replace) throws IOException {
        write(target, values, replace,
                (temporary, destination) -> Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING),
                Thread::sleep, DEFAULT_ATTEMPTS);
    }

    /** Test seam; the injected mover must perform an atomic replacement or fail. */
    static void write(Path target, Properties values, boolean replace,
                      MoveOperation mover, Sleeper sleeper, int attempts) throws IOException {
        if (target == null || values == null || mover == null || sleeper == null || attempts < 1) {
            throw new IllegalArgumentException("Target, properties, atomic mover, sleeper and positive attempt budget required");
        }
        Path destination = target.toAbsolutePath().normalize();
        if (destination.getFileName() == null) {
            throw new IllegalArgumentException("The properties target must name a file");
        }
        checkInterrupted(destination, null, 0, attempts, null);
        if (!replace) {
            rejectExisting(destination);
        }
        Path temporary = Files.createTempFile(destination.getParent(), ".dc-", ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            values.store(writer, COMMENT);
        } catch (IOException | RuntimeException | Error failure) {
            cleanupIncomplete(temporary, failure);
            log("terminal error; serialization failed; target=" + destination + "; incomplete=" + temporary + "; cause=" + failure);
            if (failure instanceof ClosedByInterruptException) {
                Thread.currentThread().interrupt();
                InterruptedIOException interrupted = new InterruptedIOException("Properties serialization interrupted; target="
                        + destination + "; incomplete=" + temporary + "; attempts=0; incomplete temporary cleanup attempted");
                interrupted.initCause(failure);
                throw interrupted;
            }
            throw failure;
        }

        if (!replace) {
            checkInterrupted(destination, temporary, 0, attempts, null);
            try {
                Files.createLink(destination, temporary);
            } catch (IOException | UnsupportedOperationException failure) {
                throw failed(destination, temporary, 1, 1, failure);
            }
            try {
                Files.delete(temporary);
            } catch (IOException failure) {
                IOException reported = new IOException("Properties published atomically, but temporary-link cleanup failed; "
                        + "target=" + destination + "; pending=" + temporary + "; attempts=1/1", failure);
                log(reported.getMessage());
                throw reported;
            }
            return;
        }

        AccessDeniedException lastDenial = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            checkInterrupted(destination, temporary, attempt - 1, attempts, lastDenial);
            try {
                mover.move(temporary, destination);
            } catch (AccessDeniedException denial) {
                lastDenial = denial;
                if (attempt == attempts) {
                    throw failed(destination, temporary, attempt, attempts, denial);
                }
                long delay = attempt == 1 ? 50L : attempt == 2 ? 100L : attempt == 3 ? 200L : 250L;
                if (attempt == 1) {
                    log("first retry after move AccessDeniedException; target=" + destination + "; pending=" + temporary
                            + "; attempts=" + attempt + "/" + attempts + "; delay_ms=" + delay + "; cause=" + denial);
                }
                checkInterrupted(destination, temporary, attempt, attempts, denial);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted(destination, temporary, attempt, attempts, interrupted, denial);
                }
                continue;
            } catch (IOException failure) {
                throw failed(destination, temporary, attempt, attempts, failure);
            }
            if (lastDenial != null) {
                log("recovered atomic publication; target=" + destination + "; attempts=" + attempt + "/" + attempts);
            }
            return;
        }
        throw new AssertionError("Atomic publication exhausted its attempt loop without a result");
    }

    private static void rejectExisting(Path target) throws IOException {
        try {
            Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            return;
        }
        FileAlreadyExistsException failure = new FileAlreadyExistsException(target.toString(), null,
                "Refusing to overwrite create-only properties target");
        log("terminal error; target=" + target + "; attempts=0; cause=" + failure);
        throw failure;
    }

    private static void cleanupIncomplete(Path temporary, Throwable primary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException | RuntimeException cleanup) {
            if (cleanup != primary) {
                primary.addSuppressed(cleanup);
            }
        }
    }

    private static IOException failed(Path target, Path temporary, int attempt, int budget, Throwable cause) {
        IOException failure = new IOException("Atomic properties publication failed; target=" + target
                + "; pending=" + temporary + "; attempts=" + attempt + "/" + budget
                + "; fully written temporary retained", cause);
        log("terminal error; " + failure.getMessage() + "; cause=" + cause);
        return failure;
    }

    private static void checkInterrupted(Path target, Path temporary, int attempt, int budget,
                                         AccessDeniedException lastDenial) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw interrupted(target, temporary, attempt, budget,
                    new InterruptedException("Thread interrupted before atomic publication/retry"), lastDenial);
        }
    }

    private static InterruptedIOException interrupted(Path target, Path temporary, int attempt, int budget,
                                                      InterruptedException cause, AccessDeniedException lastDenial) {
        InterruptedIOException failure = new InterruptedIOException("Atomic properties publication interrupted; target="
                + target + "; pending=" + (temporary == null ? "not-created" : temporary)
                + "; attempts=" + attempt + "/" + budget
                + (temporary == null ? "; serialization not started" : "; fully written temporary retained"));
        failure.initCause(cause);
        if (lastDenial != null) {
            failure.addSuppressed(lastDenial);
        }
        log("terminal error; " + failure.getMessage());
        return failure;
    }

    private static void log(String message) {
        System.err.println("DcAtomicProperties[" + VERSION + "]: " + message);
    }
}
