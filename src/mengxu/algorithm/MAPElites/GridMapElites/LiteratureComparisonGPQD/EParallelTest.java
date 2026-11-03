package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Coordinator-only publication around the unchanged ETest scientific kernel. */
public final class EParallelTest {
    private EParallelTest() { }

    @FunctionalInterface
    interface JobPolicy {
        String mainClass(EConfiguration.Case item, Properties request) throws IOException;
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("EParallelTest --config <e-study.properties> [--resume] [--dry-run]"
                    + " [--workers 5] [--worker-heap 2g] [--set key=value ...]");
            System.out.println("Separate leased JVM per case; method/regime/scenario barriers; at most five run seeds."
                    + " Scientific configuration and evaluation-family hash are identical to ETest."
                    + " Extra worker common-bank replays are explicitly counted outside scientific case CSVs.");
            return;
        }
        run(EParallelOptions.parse(args));
    }

    public static void run(EParallelOptions options) throws IOException {
        run(options, (item, request) -> EParallelTestWorker.class.getName());
    }

    static void run(EParallelOptions options, JobPolicy policy) throws IOException {
        EConfiguration c = options.config;
        if (c.validationCandidates > 200) { throw new IOException("validation-candidates must be 1..200"); }
        Path training = c.trainingRoot.toRealPath();
        Path trainingLock = training.resolve(".training.lock");
        if (!Files.isRegularFile(trainingLock)) { throw new IOException("Sealed producer root lacks its .training.lock guard"); }
        try (FileChannel sourceChannel = FileChannel.open(trainingLock, StandardOpenOption.WRITE);
             FileLock sourceLock = acquire(sourceChannel, "Source training is active; testing requires exclusive sealed training input")) {
            Path root = ETest.checkedRoot(c);
            Properties previous = Files.exists(root.resolve("manifest.properties"))
                    ? EFiles.read(root.resolve("manifest.properties")) : null;
            if (Files.exists(root) && (previous == null || !c.resume)) {
                throw new IOException("Existing test root requires --resume and a valid compatible manifest");
            }
            ETest.RealEvaluator evaluator = new ETest.RealEvaluator(c);
            ETest.RealProjectionFactory factory = new ETest.RealProjectionFactory();
            ETest.Inputs inputs = ETest.Inputs.read(c, ETest.targets(c, root, previous));
            String family = ETest.evaluationFamily(c, inputs.family, evaluator, factory);
            if (previous != null) {
                ETest.verifyRoot(root, previous, family, inputs.family);
                verifyExecutions(root, previous);
            }
            if (c.dryRun) {
                if (previous != null) { ETest.completed(root, previous, family, inputs); }
                inputs.verify();
                System.out.println("E PARALLEL TEST PREFLIGHT PASSED; no workers, simulations, or output created.");
                return;
            }
            if (previous == null) { previous = ETest.initialize(c, root, inputs.family, family, evaluator, factory); }
            Path lockFile = root.resolve(".test.lock");
            if ("COMPLETE".equals(previous.getProperty("status")) && !Files.isRegularFile(lockFile)) {
                throw new IOException("COMPLETE root lacks its test lock; refusing a zero-write repair");
            }
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = acquire(channel, "Another test coordinator owns this output")) {
                Properties manifest = EFiles.read(root.resolve("manifest.properties"));
                ETest.verifyRoot(root, manifest, family, inputs.family);
                verifyExecutions(root, manifest);
                for (EConfiguration.Case item : ETest.universe(c)) {
                    if (ETest.cachedComplete(root, manifest, item) && !inputs.cases.containsKey(item.key())) {
                        throw new IOException("Completed registry changed during preflight; retry without concurrent testing");
                    }
                }
                Map<String, Properties> results = ETest.completed(root, manifest, family, inputs);
                List<EProcessPool.Work> pending = new ArrayList<>();
                for (EConfiguration.Case item : c.requestedCases()) {
                    if (!results.containsKey(item.key())) {
                        pending.add(new EProcessPool.Work(item.key(), item.method + "." + item.regime + "." + item.scenario.id));
                    }
                }
                if (pending.isEmpty() && "COMPLETE".equals(manifest.getProperty("status"))) {
                    inputs.verify();
                    System.out.println("E PARALLEL TEST COMPLETE scope verified: zero workers/evaluation/replay/writes.");
                    return;
                }
                manifest.setProperty("status", "RUNNING");
                manifest.setProperty("last-requested-methods", c.methods.toString());
                manifest.setProperty("last-requested-regimes", c.regimes.toString());
                manifest.setProperty("last-requested-scenarios", c.scenarios.toString());
                manifest.setProperty("last-requested-case-count", Integer.toString(c.requestedCases().size()));
                manifest.setProperty("source-root-manifest-sha256-this-invocation", inputs.rootHash);
                EFiles.write(root.resolve("manifest.properties"), manifest, true);
                try {
                    if (!pending.isEmpty()) {
                        Execution execution = new Execution(root, manifest, c, inputs, family, factory, results, policy, options);
                        execution.run(pending);
                    }
                    inputs.verify();
                    ETest.report(root, manifest, c, inputs, results);
                    account(root, manifest);
                    inputs.verify();
                    manifest.setProperty("status", "COMPLETE");
                    manifest.remove("failure");
                    EFiles.write(root.resolve("manifest.properties"), manifest, true);
                } catch (IOException | RuntimeException | Error failure) {
                    manifest.setProperty("status", "FAILED"); manifest.setProperty("failure", failure.toString());
                    try {
                        account(root, manifest);
                        EFiles.write(root.resolve("manifest.properties"), manifest, true);
                    } catch (IOException reporting) { failure.addSuppressed(reporting); }
                    throw failure;
                }
            }
        }
    }

    private static FileLock acquire(FileChannel channel, String message) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) { throw new IOException(message); }
            return lock;
        } catch (OverlappingFileLockException busy) { throw new IOException(message, busy); }
    }

    private static final class Execution {
        final Path root, directory, marker, configuration;
        final Properties manifest, state = new Properties();
        final EConfiguration config;
        final ETest.Inputs inputs;
        final String family;
        final ETest.RealProjectionFactory factory;
        final Map<String, Properties> results;
        final Map<String, Task> tasks = new LinkedHashMap<>();
        final Set<EConfiguration.Scenario> preparedScenes = new HashSet<>();
        final JobPolicy policy;
        final EParallelOptions options;
        final String rootKey;

        Execution(Path root, Properties manifest, EConfiguration c, ETest.Inputs inputs, String family,
                  ETest.RealProjectionFactory factory, Map<String, Properties> results,
                  JobPolicy policy, EParallelOptions options) throws IOException {
            this.root = root; this.manifest = manifest; config = c; this.inputs = inputs; this.family = family;
            this.factory = factory; this.results = results; this.policy = policy; this.options = options;
            directory = ETest.fresh(root, "parallel-test-execution-");
            marker = directory.resolve("test-execution.properties");
            configuration = directory.resolve("configuration.properties");
            EFiles.write(configuration, c.properties(), false);
            state.setProperty("protocol", "e-parallel-test-execution-v1");
            state.setProperty("evaluation-family-hash", family);
            state.setProperty("training-family-hash", inputs.family);
            state.setProperty("source-root-manifest-sha256", inputs.rootHash);
            state.setProperty("coordinator-pid", Long.toString(ProcessHandle.current().pid()));
            state.setProperty("coordinator-start", ProcessHandle.current().info().startInstant()
                    .orElseThrow(() -> new IOException("Coordinator start instant unavailable")).toString());
            state.setProperty("workers-requested", Integer.toString(options.workers));
            state.setProperty("worker-heap", options.workerHeap);
            state.setProperty("group-policy", "METHOD_REGIME_SCENARIO_BARRIER; DISTINCT_RUN_IDS; MAX_FIVE");
            state.setProperty("status", "RUNNING");
            EFiles.write(marker, state, false);
            rootKey = "parallel-execution." + directory.getFileName();
            manifest.setProperty(rootKey + ".path", directory.getFileName().toString());
            manifest.setProperty("last-parallel-execution", directory.getFileName().toString());
            EFiles.write(root.resolve("manifest.properties"), manifest, true);
        }

        void run(List<EProcessPool.Work> work) throws IOException {
            try {
                EProcessPool.Summary summary = EProcessPool.run(work, options, directory, this::prepare, this::complete);
                counters(summary);
                seal("COMPLETE", null);
            } catch (IOException | RuntimeException | Error failure) {
                if (failure instanceof EProcessPool.BatchFailure) {
                    counters(((EProcessPool.BatchFailure) failure).summary);
                }
                try { seal("FAILED", failure); }
                catch (IOException reporting) { failure.addSuppressed(reporting); }
                throw failure;
            }
        }

        EProcessPool.Job prepare(EProcessPool.Work work, int ordinal) throws IOException {
            EConfiguration.Case item = EParallelTestWorker.find(config, work.key);
            ETest.Source source = inputs.cases.get(item.key());
            EFiles.checkHash(inputs.root.resolve("manifest.properties"), inputs.rootHash);
            source.verify();
            if (preparedScenes.add(item.scenario)) {
                ETest.common(root, manifest, config, item.scenario, factory, family);
            }
            Path attempt = ETest.fresh(root, item.stem() + "-attempt-");
            String number = String.format(java.util.Locale.ROOT, "%04d", ordinal);
            Path requestFile = directory.resolve("test-worker-" + number + ".request.properties");
            Path journal = directory.resolve("test-worker-" + number + ".result.properties");
            Path replay = directory.resolve("test-worker-" + number + ".replay.csv");
            Path console = directory.resolve("test-worker-" + number + ".console.log");
            manifest.setProperty(item.key() + ".path", attempt.getFileName().toString());
            manifest.remove(item.key() + ".manifest-sha256");
            EFiles.write(root.resolve("manifest.properties"), manifest, true);
            Properties request = new Properties();
            request.setProperty("protocol", EParallelTestWorker.REQUEST_PROTOCOL);
            request.setProperty("configuration", configuration.toString());
            request.setProperty("configuration-sha256", EFiles.hash(configuration));
            request.setProperty("case-key", item.key());
            request.setProperty("attempt-dir", attempt.toString());
            request.setProperty("execution-dir", directory.toString());
            request.setProperty("result-journal", journal.toString());
            request.setProperty("replay-file", replay.toString());
            request.setProperty("evaluation-family-hash", family);
            request.setProperty("training-family-hash", inputs.family);
            request.setProperty("training-root-manifest-sha256", inputs.rootHash);
            request.setProperty("source-manifest-sha256", source.manifestHash);
            request.setProperty("source-case-hash", EFiles.required(source.manifest, "case-hash"));
            request.setProperty("common-bank-sha256", ETest.bankHash(root, manifest, item.scenario));
            request.setProperty("common-bank-dir", EFiles.mapped(root,
                    EFiles.required(manifest, ETest.commonKey(item.scenario) + ".path")).toString());
            request.setProperty("coordinator-pid", state.getProperty("coordinator-pid"));
            request.setProperty("coordinator-start", state.getProperty("coordinator-start"));
            String mainClass = policy.mainClass(item, request);
            request.setProperty("worker-main-class", mainClass);
            EFiles.write(requestFile, request, false);
            tasks.put(item.key(), new Task(source, attempt, requestFile, journal, ordinal));
            state.setProperty("request." + number + ".path", requestFile.getFileName().toString());
            state.setProperty("prepared-requests", Integer.toString(tasks.size()));
            EFiles.write(marker, state, true);
            return new EProcessPool.Job(work, mainClass, requestFile, console);
        }

        void complete(EProcessPool.Result exited) throws IOException {
            Task task = tasks.get(exited.job.work.key);
            if (task == null || !task.request.equals(exited.job.requestFile)) { throw new IOException("Unknown worker exit"); }
            Properties receipt = new Properties();
            receipt.setProperty("case-key", task.source.item.key());
            receipt.setProperty("pid", Long.toString(exited.pid));
            receipt.setProperty("exit-code", Integer.toString(exited.exitCode));
            receipt.setProperty("started-millis", Long.toString(exited.startedMillis));
            receipt.setProperty("finished-millis", Long.toString(exited.finishedMillis));
            try {
                if (exited.exitCode != 0) {
                    receipt.setProperty("status", "PROCESS_FAILED_CASE_NOT_COMMITTED");
                    return; // The pool stops new dispatch, but still invokes this callback for all other exits.
                }
                Properties journal = EFiles.read(task.journal);
                EParallelTestWorker.require(journal, "status", "COMPLETE");
                EParallelTestWorker.require(journal, "case-key", task.source.item.key());
                EParallelTestWorker.require(journal, "evaluation-family-hash", family);
                EParallelTestWorker.require(journal, "training-family-hash", inputs.family);
                EParallelTestWorker.require(journal, "attempt-dir", task.attempt.toString());
                EParallelTestWorker.require(journal, "worker-pid", Long.toString(exited.pid));
                EFiles.checkHash(task.attempt.resolve("manifest.properties"), EFiles.required(journal, "case-manifest-sha256"));
                task.source.verify();
                EFiles.checkHash(inputs.root.resolve("manifest.properties"), inputs.rootHash);
                ETest.Inputs one = new ETest.Inputs(config, inputs.root, inputs.family);
                one.cases.put(task.source.item.key(), task.source);
                Properties accepted = ETest.completed(root, manifest, family, one).get(task.source.item.key());
                if (accepted == null) { throw new IOException("Zero-exit worker did not seal a verifiable COMPLETE case"); }
                results.put(task.source.item.key(), accepted);
                receipt.setProperty("status", "VERIFIED_CASE_COMMITTED");
                receipt.setProperty("case-manifest-sha256", manifest.getProperty(task.source.item.key() + ".manifest-sha256"));
                EFiles.write(root.resolve("manifest.properties"), manifest, true);
            } catch (IOException | RuntimeException failure) {
                receipt.setProperty("status", "VERIFICATION_FAILED_CASE_NOT_COMMITTED");
                receipt.setProperty("failure", failure.toString());
                throw failure;
            } finally {
                EFiles.write(directory.resolve(String.format(java.util.Locale.ROOT,
                        "test-worker-%04d.acceptance.properties", task.ordinal)), receipt, false);
            }
        }

        void counters(EProcessPool.Summary summary) {
            state.setProperty("pool-launched", Integer.toString(summary.launched));
            state.setProperty("pool-completed", Integer.toString(summary.completed));
            state.setProperty("pool-max-active", Integer.toString(summary.maxActive));
            state.setProperty("pool-failures", Integer.toString(summary.failures));
        }
        void seal(String status, Throwable failure) throws IOException {
            state.setProperty("status", status);
            if (failure != null) { state.setProperty("failure", failure.toString()); }
            try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) {
                    if (Files.isRegularFile(file) && !file.equals(marker)) {
                        state.setProperty("data." + file.getFileName() + ".sha256", EFiles.hash(file));
                    }
                }
            }
            EFiles.write(marker, state, true);
            manifest.setProperty(rootKey + ".manifest-sha256", EFiles.hash(marker));
            EFiles.write(root.resolve("manifest.properties"), manifest, true);
        }
    }

    private static final class Task {
        final ETest.Source source;
        final Path attempt, request, journal;
        final int ordinal;
        Task(ETest.Source source, Path attempt, Path request, Path journal, int ordinal) {
            this.source = source; this.attempt = attempt; this.request = request; this.journal = journal; this.ordinal = ordinal;
        }
    }

    static void verifyExecutions(Path root, Properties manifest) throws IOException {
        for (String key : manifest.stringPropertyNames()) {
            if (!key.startsWith("parallel-execution.") || !key.endsWith(".path")) { continue; }
            String prefix = key.substring(0, key.length() - 5);
            Path directory = EFiles.mapped(root, manifest.getProperty(key));
            Path marker = directory.resolve("test-execution.properties");
            if (manifest.containsKey(prefix + ".manifest-sha256")) {
                EFiles.checkHash(marker, manifest.getProperty(prefix + ".manifest-sha256"));
                EFiles.verifyArtifacts(directory, EFiles.read(marker));
            } else {
                Path pool = directory.resolve("execution.properties");
                if (!Files.isRegularFile(pool)) { continue; }
                Properties processes = EFiles.read(pool);
                for (String property : processes.stringPropertyNames()) {
                    if (!property.startsWith("process.") || !property.endsWith(".pid")) { continue; }
                    String record = property.substring(0, property.length() - 3);
                    long pid = Long.parseLong(processes.getProperty(property));
                    String start = processes.getProperty(record + "start-instant");
                    ProcessHandle child = ProcessHandle.of(pid).orElse(null);
                    if (child != null && child.isAlive() && start != null && !start.equals("unavailable")
                            && child.info().startInstant().map(Instant::toString).map(start::equals).orElse(false)) {
                        throw new IOException("A previous leased test worker is still exiting: PID " + pid + "; retry after it exits");
                    }
                }
            }
        }
    }

    static void account(Path root, Properties manifest) throws IOException {
        ETest.accountReferenceAttempts(root, manifest);
        long known = 0, unknown = 0, notStarted = 0, notLaunched = 0, requests = 0;
        for (String key : manifest.stringPropertyNames()) {
            if (!key.startsWith("parallel-execution.") || !key.endsWith(".path")) { continue; }
            Path directory = EFiles.mapped(root, manifest.getProperty(key));
            Properties state = EFiles.read(directory.resolve("test-execution.properties"));
            for (String field : state.stringPropertyNames()) {
                if (!field.startsWith("request.") || !field.endsWith(".path")) { continue; }
                requests++;
                Path request = EFiles.mapped(directory, state.getProperty(field));
                Properties job = EFiles.read(request);
                Path journal = Path.of(EFiles.required(job, "result-journal")).toAbsolutePath().normalize();
                if (!journal.getParent().equals(directory)) { throw new IOException("Journal accounting path escapes execution"); }
                if (Files.isRegularFile(journal)) {
                    Properties result = EFiles.read(journal);
                    if ("RECORDED".equals(result.getProperty("replay-count-status"))) {
                        known = Math.addExact(known, ETest.count(result, "replay-reference-simulations"));
                    } else if ("NOT_STARTED".equals(result.getProperty("replay-count-status"))) {
                        notStarted++;
                    } else { unknown++; }
                } else {
                    int ordinal = Integer.parseInt(field.substring(8, field.length() - 5));
                    if (state.containsKey("pool-launched") && ordinal > Integer.parseInt(state.getProperty("pool-launched"))) {
                        notStarted++; notLaunched++;
                    } else { unknown++; }
                }
            }
        }
        manifest.setProperty("parallel-worker-requests", Long.toString(requests));
        manifest.setProperty("parallel-worker-recorded-replay-simulations", Long.toString(known));
        manifest.setProperty("parallel-worker-replay-unknown-attempts", Long.toString(unknown));
        manifest.setProperty("parallel-worker-replay-not-started-attempts", Long.toString(notStarted));
        manifest.setProperty("parallel-worker-requests-not-launched", Long.toString(notLaunched));
        long coordinator = manifest.containsKey("recorded-common-reference-simulations")
                ? ETest.count(manifest, "recorded-common-reference-simulations") : 0;
        manifest.setProperty("recorded-reference-simulations-total", Long.toString(Math.addExact(coordinator, known)));
        long unknownCoordinator = ETest.count(manifest, "unknown-common-reference-attempts");
        manifest.setProperty("reference-simulation-total-status", unknown + unknownCoordinator == 0 ? "EXACT_RECORDED"
                : "LOWER_BOUND_UNKNOWN_INTERRUPTED_ATTEMPTS");
        manifest.setProperty("parallel-replay-policy", "EXTRA_PER_WORKER_REPLAYS; NOT_INCLUDED_IN_SCIENTIFIC_CASE_CSVS");
    }
}
