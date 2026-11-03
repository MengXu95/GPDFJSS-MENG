package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Bounded real five-seed checks plus the existing injected scientific-kernel regressions. */
public final class EParallelTestChecks {
    private EParallelTestChecks() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--request")) {
            try (EWorkerLease lease = EWorkerLease.start()) { fakeWorker(Path.of(args[1])); }
            return;
        }
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !args[2].equals("--both-regimes"))) {
            throw new IllegalArgumentException("EParallelTestChecks <sealed 5-seed config> <NEW check root> [--both-regimes]");
        }
        EConfiguration config = EConfiguration.parse(args.length == 3
                ? new String[]{"--config", args[0], "--set", "regimes=fixed,rotating"}
                : new String[]{"--config", args[0]});
        runChecks(config, Path.of(args[1]));
        System.out.println("E PARALLEL TEST CHECKS PASSED: shared kernel/family, five-seed group barriers,"
                + " numeric CSV equivalence, failure drain, skip, resume and explicit replay accounting");
    }

    public static void runChecks(Path configuration, Path root) throws IOException {
        runChecks(EConfiguration.parse(new String[]{"--config", configuration.toString()}), root);
    }

    public static void runChecks(EConfiguration base, Path root) throws IOException {
        List<EConfiguration.Case> selected = base.requestedCases();
        if (base.runs != 5 || base.methods.size() != 1 || base.scenarios.size() != 1
                || selected.size() != 5 * base.regimes.size() || selected.size() > 10) {
            throw new IOException("Provide five seeds per group, one method/scenario, and one or both regimes; never formal scope");
        }
        Map<String, Set<Integer>> runIds = new LinkedHashMap<>();
        for (EConfiguration.Case item : selected) {
            runIds.computeIfAbsent(group(item), ignored -> new HashSet<>()).add(item.run);
        }
        for (Set<Integer> ids : runIds.values()) {
            if (!ids.equals(Set.of(0, 1, 2, 3, 4))) { throw new IOException("Each group must contain five distinct configured seed IDs"); }
        }
        if (Files.exists(root)) { throw new IOException("A NEW check root is required"); }
        if (!"COMPLETE".equals(EFiles.read(base.trainingRoot.resolve("manifest.properties")).getProperty("status"))) {
            throw new IOException("Checks refuse non-COMPLETE source training");
        }
        Files.createDirectories(root);
        Path module = base.populationParams.getParent();
        ETestChecks.runChecks(module, root.resolve("injected-kernel-checks"));
        Path seqFile = configuration(base, root, "sequential");
        Path parFile = configuration(base, root, "parallel");
        EConfiguration sequential = EConfiguration.parse(new String[]{"--config", seqFile.toString()});
        EParallelOptions parallel = options(parFile, false, 5);
        String seqFamily = ETest.evaluationFamily(sequential, ETraining.fingerprint(sequential),
                new ETest.RealEvaluator(sequential), new ETest.RealProjectionFactory());
        String parFamily = ETest.evaluationFamily(parallel.config, ETraining.fingerprint(parallel.config),
                new ETest.RealEvaluator(parallel.config), new ETest.RealProjectionFactory());
        check(seqFamily.equals(parFamily), "Scheduler/root switches cannot enter the scientific family");
        Map<String, EConfiguration.Case> source = new LinkedHashMap<>();
        for (EConfiguration.Case item : selected) { source.put(item.key(), item); }
        ETest.Inputs unchanged = ETest.Inputs.read(sequential, source);
        ETest.run(sequential);
        completedSequentialToParallel(seqFile, sequential);
        EParallelTest.run(parallel);
        compare(sequential, parallel.config);
        completedParallelToSequential(parFile, parallel.config);
        sealedExecutionIntegrity(parFile, parallel.config);
        Properties parallelRoot = EFiles.read(parallel.config.testRoot.resolve("manifest.properties"));
        Path execution = EFiles.mapped(parallel.config.testRoot, parallelRoot.getProperty("last-parallel-execution"));
        Properties pool = EFiles.read(execution.resolve("execution.properties"));
        check(pool.getProperty("launched").equals(Integer.toString(selected.size()))
                && pool.getProperty("completed").equals(Integer.toString(selected.size()))
                && pool.getProperty("max-active").equals("5"), "Five independent child JVMs, bounded by the requested capacity");
        Set<String> pids = new HashSet<>();
        String previousGroup = null;
        long previousFinished = 0;
        for (int i = 1; i <= selected.size(); i++) {
            String prefix = "process." + i + ".";
            pids.add(EFiles.required(pool, prefix + "pid"));
            String currentGroup = EFiles.required(pool, prefix + "group");
            long started = Long.parseLong(EFiles.required(pool, prefix + "started-millis"));
            long finished = Long.parseLong(EFiles.required(pool, prefix + "finished-millis"));
            if (previousGroup != null && !previousGroup.equals(currentGroup)) {
                check(started >= previousFinished, "Next regime group cannot launch before every prior-group exit");
            }
            previousFinished = Math.max(previousFinished, finished);
            previousGroup = currentGroup;
        }
        check(pids.size() == selected.size(), "Distinct worker PIDs, not in-process threads");
        check(parallelRoot.getProperty("recorded-common-reference-simulations").equals("2")
                && parallelRoot.getProperty("parallel-worker-recorded-replay-simulations").equals(Integer.toString(2 * selected.size()))
                && parallelRoot.getProperty("recorded-reference-simulations-total").equals(Integer.toString(2 + 2 * selected.size()))
                && parallelRoot.getProperty("parallel-worker-replay-unknown-attempts").equals("0"),
                "Per-worker replay simulations are real overhead, not hidden behind the coordinator's two");
        String before = EFiles.hash(parallel.config.testRoot.resolve("manifest.properties"));
        long executions = executionCount(parallel.config.testRoot);
        EParallelTest.run(options(parFile, true, 1));
        check(before.equals(EFiles.hash(parallel.config.testRoot.resolve("manifest.properties")))
                && executions == executionCount(parallel.config.testRoot), "Fully complete resume creates no workers or metadata despite changed admin flags");
        partialSequentialToParallel(base, root, sequential);
        failDrainResume(base, root, sequential);
        poolSkipCheck(parallel, root.resolve("pool-skip-check"));
        unchanged.verify();
    }

    private static void completedSequentialToParallel(Path file, EConfiguration c) throws IOException {
        Map<String, String> before = tree(c.testRoot);
        Properties previous = EFiles.read(c.testRoot.resolve("manifest.properties"));
        long executions = executionCount(c.testRoot);
        String output = capture(() -> EParallelTest.run(options(file, true, 5)));
        check(output.contains("zero workers/evaluation/replay/writes")
                && executions == executionCount(c.testRoot) && before.equals(tree(c.testRoot)),
                "SAME ROOT sequential -> parallel: zero-work branch, no workers/replay/writes, every file/hash/timestamp unchanged");
        unchangedRegistry(previous, EFiles.read(c.testRoot.resolve("manifest.properties")), c);
        System.out.println("SAME-ROOT PASS 1: complete sequential -> parallel; zero-work return, unchanged full tree/counters/case anchors/report.");
    }

    private static void completedParallelToSequential(Path file, EConfiguration c) throws IOException {
        EConfiguration resume = EConfiguration.parse(new String[]{"--config", file.toString(), "--resume"});
        Map<String, String> before = tree(c.testRoot);
        Properties previous = EFiles.read(c.testRoot.resolve("manifest.properties"));
        String output = capture(() -> ETest.run(resume, new ETest.RealEvaluator(resume), new ETest.RealProjectionFactory()));
        check(output.contains("zero simulations/writes") && before.equals(tree(c.testRoot)),
                "SAME ROOT parallel -> sequential with EXACT real classes: zero-work branch and no output/counter/replay changes");
        unchangedRegistry(previous, EFiles.read(c.testRoot.resolve("manifest.properties")), c);
        System.out.println("SAME-ROOT PASS 2: complete parallel -> sequential with exact real classes; zero-work return, unchanged full tree/counters/report.");
    }

    private static void unchangedRegistry(Properties before, Properties after, EConfiguration c) {
        for (String field : new String[]{"evaluation-family-hash", "report.path", "report.manifest-sha256",
                "test-executed-count", "validation-evaluation-count", "recorded-common-reference-simulations",
                "parallel-worker-recorded-replay-simulations", "recorded-reference-simulations-total"}) {
            check(java.util.Objects.equals(before.getProperty(field), after.getProperty(field)),
                    "Complete cross-launcher resume changed recorded counters/pointer: " + field);
        }
        for (EConfiguration.Case item : c.requestedCases()) {
            check(before.getProperty(item.key() + ".manifest-sha256").equals(after.getProperty(item.key() + ".manifest-sha256")),
                    "Complete cross-launcher resume changed case manifest anchor: " + item.key());
        }
    }

    public static final class AdministrativeIntegrityCheck {
        private AdministrativeIntegrityCheck() { }
        public static void main(String[] args) throws IOException {
            if (args.length != 1) { throw new IllegalArgumentException("Provide a completed private-check configuration"); }
            Path file = Path.of(args[0]);
            sealedExecutionIntegrity(file, EConfiguration.parse(new String[]{"--config", file.toString(), "--resume"}));
        }
    }

    private static void sealedExecutionIntegrity(Path file, EConfiguration c) throws IOException {
        Properties registry = EFiles.read(c.testRoot.resolve("manifest.properties"));
        Path execution = EFiles.mapped(c.testRoot, EFiles.required(registry, "last-parallel-execution"));
        Path journal = execution.resolve("test-worker-0001.result.properties");
        byte[] intact = Files.readAllBytes(journal);
        Files.writeString(journal, "checks-corrupt-provenance=true\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Map<String, String> corrupted = tree(c.testRoot);
        try {
            rejectArtifact(() -> EParallelTest.run(options(file, true, 5)), journal);
            EConfiguration resume = EConfiguration.parse(new String[]{"--config", file.toString(), "--resume"});
            rejectArtifact(() -> ETest.run(resume, new ETest.RealEvaluator(resume), new ETest.RealProjectionFactory()), journal);
            check(corrupted.equals(tree(c.testRoot)), "Sealed-result hash rejection cannot repair/rewrite the cache");
        } finally {
            Files.write(journal, intact);
        }
    }

    private static void partialSequentialToParallel(EConfiguration base, Path root, EConfiguration sequential) throws IOException {
        Path file = configuration(base, root, "partial-sequential-cache");
        EParallelOptions target = options(file, true, 5);
        EConfiguration c = target.config;
        Properties source = EFiles.read(sequential.testRoot.resolve("manifest.properties"));
        ETest.RealEvaluator evaluator = new ETest.RealEvaluator(c);
        ETest.RealProjectionFactory factory = new ETest.RealProjectionFactory();
        String family = ETest.evaluationFamily(c, ETraining.fingerprint(c), evaluator, factory);
        check(family.equals(source.getProperty("evaluation-family-hash")), "Clone only CURRENT identical scientific code/config, never arbitrary old hashes");
        Properties registry = ETest.initialize(c, c.testRoot, ETraining.fingerprint(c), family, evaluator, factory);
        EConfiguration.Scenario scenario = c.requestedCases().get(0).scenario;
        String common = ETest.commonKey(scenario);
        Path sourceBank = EFiles.mapped(sequential.testRoot, EFiles.required(source, common + ".path"));
        Path bank = c.testRoot.resolve(sourceBank.getFileName());
        copyDirectory(sourceBank, bank);
        registry.setProperty(common + ".path", bank.getFileName().toString());
        registry.setProperty(common + ".manifest-sha256", EFiles.hash(bank.resolve("manifest.properties")));
        registry.setProperty("bank." + bank.getFileName() + ".path", bank.getFileName().toString());
        registry.setProperty("bank." + bank.getFileName() + ".manifest-sha256", EFiles.hash(bank.resolve("manifest.properties")));
        Map<String, String> anchors = new LinkedHashMap<>();
        for (EConfiguration.Case item : c.requestedCases().subList(0, 4)) {
            Path original = EFiles.mapped(sequential.testRoot, source.getProperty(item.key() + ".path"));
            Path copied = c.testRoot.resolve(original.getFileName());
            copyDirectory(original, copied);
            String digest = EFiles.hash(copied.resolve("manifest.properties"));
            anchors.put(item.key(), digest);
            registry.setProperty(item.key() + ".path", copied.getFileName().toString());
            registry.setProperty(item.key() + ".manifest-sha256", digest);
        }
        registry.setProperty("status", "FAILED");
        EFiles.write(c.testRoot.resolve("manifest.properties"), registry, true);
        check(!registry.containsKey("report.path"), "Minimal partial sequential registry has no final report");
        EParallelTest.run(target);
        Properties full = EFiles.read(c.testRoot.resolve("manifest.properties"));
        for (Map.Entry<String, String> entry : anchors.entrySet()) {
            check(entry.getValue().equals(full.getProperty(entry.getKey() + ".manifest-sha256")),
                    "Parallel resume must preserve copied sealed sequential case " + entry.getKey());
        }
        Properties pool = EFiles.read(EFiles.mapped(c.testRoot, full.getProperty("last-parallel-execution")).resolve("execution.properties"));
        check(pool.getProperty("launched").equals(Integer.toString(c.requestedCases().size() - 4)),
                "Partial sequential -> parallel dispatches only remaining cases, not the four sealed inputs");
        Set<String> dispatched = new HashSet<>();
        for (int i = 1; i <= c.requestedCases().size() - 4; i++) {
            dispatched.add(EFiles.required(pool, "process." + i + ".key"));
        }
        Set<String> expected = new HashSet<>();
        for (EConfiguration.Case item : c.requestedCases()) {
            if (!anchors.containsKey(item.key())) { expected.add(item.key()); }
        }
        check(dispatched.equals(expected), "Actual worker keys exclude all four sealed sequential cases");
        compare(sequential, c);
        System.out.println("SAME-ROOT PASS 3: partial sequential -> parallel; four sealed cases unchanged, only remaining "
                + dispatched.size() + " keys dispatched, complete numeric parity.");
    }

    private static Path configuration(EConfiguration base, Path root, String name) throws IOException {
        Properties p = base.properties();
        p.setProperty("test-output-root", root.resolve(name).toAbsolutePath().toString());
        p.setProperty("validation-candidates", "2"); p.setProperty("validation-instances", "1"); p.setProperty("test-instances", "2");
        Path file = root.resolve(name + ".properties");
        EFiles.write(file, p, false);
        return file;
    }
    private static EParallelOptions options(Path file, boolean resume, int workers) throws IOException {
        List<String> args = new ArrayList<>(List.of("--config", file.toString(), "--workers", Integer.toString(workers),
                "--worker-heap", "2g"));
        if (resume) { args.add("--resume"); }
        return EParallelOptions.parse(args.toArray(new String[0]));
    }
    private static String group(EConfiguration.Case item) { return item.method + "." + item.regime + "." + item.scenario.id; }

    private static void compare(EConfiguration sequential, EConfiguration parallel) throws IOException {
        Properties left = EFiles.read(sequential.testRoot.resolve("manifest.properties"));
        Properties right = EFiles.read(parallel.testRoot.resolve("manifest.properties"));
        check(left.getProperty("status").equals("COMPLETE") && right.getProperty("status").equals("COMPLETE")
                && left.getProperty("evaluation-family-hash").equals(right.getProperty("evaluation-family-hash")),
                "Sequential and parallel use the exact same new evaluation family");
        for (EConfiguration.Case item : sequential.requestedCases()) {
            Path a = EFiles.mapped(sequential.testRoot, left.getProperty(item.key() + ".path"));
            Path b = EFiles.mapped(parallel.testRoot, right.getProperty(item.key() + ".path"));
            for (String file : new String[]{"subset_plan.csv", "validation_instances.csv", "validation_rules.csv",
                    "test_instance_outcomes.csv", "member_projection.csv", "common_cells.csv"}) {
                check(EFiles.hash(a.resolve(file)).equals(EFiles.hash(b.resolve(file))), "Scientific case CSV bytes differ: " + item.key() + "/" + file);
            }
            Properties pa = EFiles.read(a.resolve("manifest.properties")), pb = EFiles.read(b.resolve("manifest.properties"));
            for (String field : ETest.METRICS) { check(pa.getProperty(field).equals(pb.getProperty(field)), "Numeric metric differs: " + field); }
            for (String field : new String[]{"representative-id", "representative-tree-hash", "subset-plan-sha256",
                    "initial-genotype-sha256", "initial-evaluated-population-sha256"}) {
                check(pa.getProperty(field).equals(pb.getProperty(field)), "Scientific choice/initial input differs: " + field);
            }
        }
        Path a = EFiles.mapped(sequential.testRoot, left.getProperty("report.path"));
        Path b = EFiles.mapped(parallel.testRoot, right.getProperty("report.path"));
        for (String file : ETest.REPORT_FILES) {
            if (file.endsWith(".csv")) { check(EFiles.hash(a.resolve(file)).equals(EFiles.hash(b.resolve(file))), "Numeric report CSV differs: " + file); }
        }
    }

    private static void failDrainResume(EConfiguration base, Path root, EConfiguration sequential) throws IOException {
        Path file = configuration(base, root, "fault");
        EParallelOptions options = options(file, false, 5);
        boolean failed = false;
        try {
            EParallelTest.run(options, (item, request) -> {
                if (item.run == 0) {
                    request.setProperty("checks-action", "FAIL_AFTER_FIVE_PREPARES");
                    return EParallelTestChecks.class.getName();
                }
                return EParallelTestWorker.class.getName();
            });
        } catch (EProcessPool.BatchFailure expected) { failed = true; }
        check(failed, "Injected worker process failure must fail the coordinator");
        Properties partial = EFiles.read(options.config.testRoot.resolve("manifest.properties"));
        check(partial.getProperty("status").equals("FAILED"), "Failed execution never publishes completed report state");
        Map<String, String> committed = new LinkedHashMap<>();
        for (EConfiguration.Case item : options.config.requestedCases()) {
            String digest = partial.getProperty(item.key() + ".manifest-sha256");
            if (digest != null) { committed.put(item.key(), digest); }
        }
        check(committed.size() == 4, "All four successful workers are drained and saved after the first failure");
        long executionsBefore = executionCount(options.config.testRoot);
        EConfiguration resume = EConfiguration.parse(new String[]{"--config", file.toString(), "--resume"});
        ETest.run(resume, new ETest.RealEvaluator(resume), new ETest.RealProjectionFactory());
        Properties full = EFiles.read(options.config.testRoot.resolve("manifest.properties"));
        for (Map.Entry<String, String> entry : committed.entrySet()) {
            check(entry.getValue().equals(full.getProperty(entry.getKey() + ".manifest-sha256")), "Resume must not rewrite a successful sibling");
        }
        check(executionCount(options.config.testRoot) == executionsBefore,
                "Partial parallel -> sequential recovery never launches a new worker execution");
        check(full.getProperty("parallel-worker-recorded-replay-simulations").equals("8")
                && full.getProperty("recorded-common-reference-simulations").equals("4")
                && full.getProperty("recorded-reference-simulations-total").equals("12"),
                "Failure/sequential-resume replay overhead must refresh combined total: coordinator="
                        + full.getProperty("recorded-common-reference-simulations") + ", worker="
                        + full.getProperty("parallel-worker-recorded-replay-simulations") + ", combined="
                        + full.getProperty("recorded-reference-simulations-total"));
        compare(sequential, options.config);
        rejectIncompatibleAndCorrupt(file, options.config);
        System.out.println("SAME-ROOT PASS 4: partial parallel -> sequential; four worker successes unchanged,"
                + " no new worker execution, complete numeric parity, replay total 4+8=12."
                + " Incompatible controls/corrupt results rejected by both launchers without writes.");
    }

    private static void rejectIncompatibleAndCorrupt(Path file, EConfiguration c) throws IOException {
        Map<String, String> before = tree(c.testRoot);
        EConfiguration changed = EConfiguration.parse(new String[]{"--config", file.toString(), "--resume",
                "--set", "validation-instances=2"});
        reject(() -> ETest.run(changed, new ETest.RealEvaluator(changed), new ETest.RealProjectionFactory()),
                "Sequential must not ignore incompatible evaluation controls");
        EParallelOptions bad = EParallelOptions.parse(new String[]{"--config", file.toString(), "--resume",
                "--set", "validation-instances=2", "--workers", "5"});
        reject(() -> EParallelTest.run(bad), "Parallel must not ignore incompatible evaluation controls");
        check(before.equals(tree(c.testRoot)), "Incompatible cache checks make no writes");
        Properties registry = EFiles.read(c.testRoot.resolve("manifest.properties"));
        Path attempt = EFiles.mapped(c.testRoot, registry.getProperty(c.requestedCases().get(0).key() + ".path"));
        Files.writeString(attempt.resolve("validation_rules.csv"), "CORRUPTED_CHECK_OUTPUT\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Map<String, String> corrupted = tree(c.testRoot);
        EConfiguration resume = EConfiguration.parse(new String[]{"--config", file.toString(), "--resume"});
        reject(() -> ETest.run(resume, new ETest.RealEvaluator(resume), new ETest.RealProjectionFactory()),
                "Sequential cannot blindly skip corrupt completed output");
        reject(() -> EParallelTest.run(options(file, true, 5)), "Parallel cannot blindly skip corrupt completed output");
        check(corrupted.equals(tree(c.testRoot)), "Corruption is rejected without repair or reevaluation");
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectory(target);
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(source)) {
            for (Path entry : entries) {
                if (Files.isSymbolicLink(entry)) { throw new IOException("Checks refuse symlink cache clone"); }
                if (Files.isDirectory(entry)) { copyDirectory(entry, target.resolve(entry.getFileName())); }
                else { Files.copy(entry, target.resolve(entry.getFileName()), java.nio.file.StandardCopyOption.COPY_ATTRIBUTES); }
            }
        }
    }
    private static Map<String, String> tree(Path root) throws IOException {
        Map<String, String> result = new java.util.TreeMap<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                result.put(root.relativize(path).toString(), EFiles.hash(path) + ":" + Files.size(path)
                        + ":" + Files.getLastModifiedTime(path));
            }
        }
        return result;
    }
    private interface CheckedAction { void run() throws IOException; }
    private static String capture(CheckedAction action) throws IOException {
        java.io.PrintStream old = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream sink = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(sink);
            try { action.run(); } finally { System.setOut(old); }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
    private static void reject(CheckedAction action, String message) throws IOException {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    private static void rejectArtifact(CheckedAction action, Path artifact) throws IOException {
        try { action.run(); }
        catch (IOException expected) {
            check(expected.getMessage().contains(artifact.getFileName().toString()),
                    "Rejection must identify the corrupted sealed result, not an unrelated family mismatch");
            return;
        }
        throw new AssertionError("Corrupt sealed result was blindly skipped: " + artifact);
    }

    private static void poolSkipCheck(EParallelOptions options, Path execution) throws IOException {
        Files.createDirectory(execution);
        List<EProcessPool.Work> work = new ArrayList<>();
        for (int i = 0; i < 6; i++) { work.add(new EProcessPool.Work("checks-" + i, i < 5 ? "first-group" : "must-not-start")); }
        List<EProcessPool.Result> exits = new ArrayList<>();
        try {
            EProcessPool.run(work, options, execution, (item, ordinal) -> {
                check(ordinal <= 5, "Later groups must not be prepared after failure");
                Properties p = new Properties();
                p.setProperty("checks-action", ordinal == 1 ? "FAIL_AFTER_FIVE_PREPARES" : "SUCCESS_AFTER_DELAY");
                p.setProperty("case-key", item.key); p.setProperty("execution-dir", execution.toString());
                p.setProperty("result-journal", execution.resolve("stub-" + ordinal + ".result.properties").toString());
                Path request = execution.resolve("test-worker-" + String.format(java.util.Locale.ROOT, "%04d", ordinal) + ".request.properties");
                EFiles.write(request, p, false);
                return new EProcessPool.Job(item, EParallelTestChecks.class.getName(), request, execution.resolve("stub-" + ordinal + ".log"));
            }, exits::add);
            throw new AssertionError("Process pool must report injected failure");
        } catch (EProcessPool.BatchFailure expected) {
            check(expected.summary.launched == 5 && expected.summary.completed == 5 && expected.summary.maxActive == 5,
                    "Failure stops new groups, drains the original five processes");
            check(exits.size() == 5 && exits.stream().filter(r -> r.exitCode == 0).count() == 4,
                    "Callbacks include both failure and every successful exit");
        }
    }

    private static void fakeWorker(Path path) throws IOException {
        Properties request = EFiles.read(path);
        String action = EFiles.required(request, "checks-action");
        Path execution = Path.of(EFiles.required(request, "execution-dir"));
        if (action.equals("FAIL_AFTER_FIVE_PREPARES")) {
            long deadline = System.nanoTime() + 30_000_000_000L;
            while (true) {
                int count = 0;
                try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(execution, "test-worker-*.request.properties")) {
                    for (Path ignored : files) { count++; }
                }
                if (count == 5) { break; }
                if (System.nanoTime() > deadline) { throw new IOException("Checks preparation barrier timed out"); }
                pause(20);
            }
            Properties journal = new Properties();
            journal.setProperty("status", "FAILED"); journal.setProperty("case-key", request.getProperty("case-key"));
            journal.setProperty("replay-count-status", "NOT_STARTED"); journal.setProperty("replay-reference-simulations", "0");
            journal.setProperty("failure", "CHECKS_ONLY_FORCED_PROCESS_EXIT");
            EFiles.write(Path.of(EFiles.required(request, "result-journal")), journal, false);
            throw new IOException("CHECKS_ONLY_FORCED_PROCESS_EXIT after preparing five distinct seeds");
        }
        if (!action.equals("SUCCESS_AFTER_DELAY")) { throw new IOException("Unknown private checks action"); }
        pause(1000);
    }
    private static void pause(long milliseconds) throws IOException {
        try { Thread.sleep(milliseconds); }
        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IOException("Checks worker interrupted", stopped); }
    }
    private static long executionCount(Path root) throws IOException {
        long count = 0;
        try (java.nio.file.DirectoryStream<Path> dirs = Files.newDirectoryStream(root, "parallel-test-execution-*")) {
            for (Path ignored : dirs) { count++; }
        }
        return count;
    }
    private static void check(boolean valid, String message) { if (!valid) { throw new AssertionError(message); } }
}
