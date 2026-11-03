package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.FinalArchiveReader;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.PairedStatistics;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.SchedulingEvaluator;
import mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD.DcDescriptorBank;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots;

/** Incremental testing of actual final outputs; source training remains read-only. */
public final class ETest {
    public static final String PROTOCOL = "literature-comparison-testing-v1";
    static final String POLICY = "VALIDATION_SELECTED_FROM_SUBSET";
    static final int COMMON_SIDE = 81, COMMON_CELLS = 6561;
    static final String[] REPORT_FILES = {"run_results.csv", "summary.csv", "primary_comparisons.csv",
        "paired_differences.csv", "paired_summary.csv", "comparison_status.properties", "results_guide.properties"};
    static final String[] METRICS = {"final_members", "stored_finite_members", "stored_failure_members",
        "unique_stored_finite_pairs", "duplicate_expression_members", "subset_available_pairs", "subset_size",
        "subset_insufficient_case", "validation_evaluation_count", "validation_failure_count",
        "validation_failed_rule_count", "quality_mean_validation_objective",
        "quality_finite_conditional_rule_mean", "quality_finite_conditional_rule_median", "quality_finite_rule_count",
        "test_scheduled_count", "test_executed_count", "test_unexecuted_count", "test_failure_count",
        "test_failed_run", "no_representative_run", "mean_test_objective",
        "common_coverage_all_retained", "common_coverage_stored_finite",
        "common_occupied_cells_all_retained", "common_occupied_cells_stored_finite", "common_projection_failures",
        "native_occupied_cells", "native_grid_cells", "native_coverage",
        "training_seconds", "fitness_evaluations", "failed_training_evaluations",
        "manual_reference_simulations", "descriptor_reference_simulations", "total_training_simulations"};

    private ETest() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("ETest --config <e-study.properties> [--resume] [--dry-run] [--set key=value ...]");
            System.out.println("Default scope: 360 Lexicase fixed/rotating cases, all six scenarios."
                    + " Uniform min(200, unique stored-finite) frozen subset; 5 validation instances select"
                    + " one finite validation minimum within that subset, then 50 independent held-out slots."
                    + " VALIDATION_SELECTED_FROM_SUBSET, never global/output best."
                    + " Optional matched GP/QDGP can extend the same family without redoing completed Lexicase cases.");
            return;
        }
        run(EConfiguration.parse(args));
    }

    interface EvaluationService {
        String identity();
        Outcome evaluate(EConfiguration.Case item, ArchiveCandidate candidate, long seed) throws IOException;
    }
    static final class Outcome {
        final double value;
        final String failure;
        Outcome(double value, String failure) {
            if (failure == null || (Double.isFinite(value) && value >= 0 && value < Double.MAX_VALUE
                    ? !failure.isEmpty() : value != Double.POSITIVE_INFINITY || failure.trim().isEmpty())) {
                throw new IllegalArgumentException("Expected finite nonnegative outcome or Infinity with explicit failure");
            }
            this.value = value; this.failure = failure;
        }
    }
    interface ProjectionService {
        double[] coordinates(ArchiveCandidate member);
        void write(Path path) throws IOException;
        long simulationCount();
    }
    interface ProjectionFactory {
        String identity();
        ProjectionService collect(EConfiguration c, EConfiguration.Scenario scenario) throws IOException;
    }
    static final class RealEvaluator implements EvaluationService {
        private final Map<EConfiguration.Scenario, SchedulingEvaluator> evaluators = new LinkedHashMap<>();
        RealEvaluator(EConfiguration c) {
            for (EConfiguration.Scenario scenario : EConfiguration.Scenario.values()) {
                evaluators.put(scenario, new SchedulingEvaluator(c.machines, c.jobs, c.warmup,
                        scenario.utilisation, c.dueFactor, scenario.objective));
            }
        }
        @Override public String identity() { return "NATIVE_WHOLE_OBJECTIVE_RECORDING_FAILURES_V1"; }
        @Override public Outcome evaluate(EConfiguration.Case item, ArchiveCandidate candidate, long seed) {
            try {
                SchedulingEvaluator.Evaluation result = evaluators.get(item.scenario).evaluateRecordingFailures(candidate, seed, 1);
                return new Outcome(result.values().get(0), result.failures().get(0));
            } catch (RuntimeException failure) {
                return new Outcome(Double.POSITIVE_INFINITY, "SCHEDULING_EXCEPTION: " + failure);
            }
        }
    }
    static final class RealProjectionFactory implements ProjectionFactory {
        @Override public String identity() { return "INDEPENDENT_D4_C3_PER_SCENARIO_V1"; }
        @Override public ProjectionService collect(EConfiguration c, EConfiguration.Scenario scenario) {
            return new RealProjection(DcDescriptorBank.collect(c.commonSeed(scenario), c.commonSamplingSeed(scenario),
                    c.machines, c.descriptorJobs, c.descriptorWarmup, scenario.utilisation, c.dueFactor, 4, 3));
        }
    }
    private static final class RealProjection implements ProjectionService {
        private final DcDescriptorBank bank;
        RealProjection(DcDescriptorBank bank) { this.bank = bank; }
        @Override public double[] coordinates(ArchiveCandidate a) { return bank.coordinates(a.rules(), 4, 3); }
        @Override public void write(Path path) throws IOException { bank.write(path, 4, 3); }
        @Override public long simulationCount() { return bank.simulationCount(); }
    }

    public static void run(EConfiguration c) throws IOException { run(c, new RealEvaluator(c), new RealProjectionFactory()); }
    static void run(EConfiguration c, EvaluationService evaluator, ProjectionFactory factory) throws IOException {
        if (c.validationCandidates > 200) { throw new IOException("validation-candidates must be 1..200; no full-output mode in E"); }
        Path root = checkedRoot(c);
        Properties previous = Files.exists(root.resolve("manifest.properties"))
                ? EFiles.read(root.resolve("manifest.properties")) : null;
        if (Files.exists(root) && (previous == null || !c.resume)) {
            throw new IOException("Existing test root requires --resume and a valid manifest");
        }
        Inputs inputs = Inputs.read(c, targets(c, root, previous));
        String evaluationFamily = evaluationFamily(c, inputs.family, evaluator, factory);
        if (previous != null) { verifyRoot(root, previous, evaluationFamily, inputs.family); }
        if (c.dryRun) {
            if (previous != null) { completed(root, previous, evaluationFamily, inputs); }
            inputs.verify();
            System.out.println("E TEST PREFLIGHT PASSED: " + c.requestedCases().size()
                    + " requested cases; all targets/caches verified; no simulations or output created.");
            return;
        }
        if (previous == null) {
            previous = initialize(c, root, inputs.family, evaluationFamily, evaluator, factory);
        }
        try (FileChannel channel = FileChannel.open(root.resolve(".test.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock acquired;
            try { acquired = channel.tryLock(); }
            catch (OverlappingFileLockException failure) { throw new IOException("Test root is locked", failure); }
            if (acquired == null) { throw new IOException("Another evaluator owns the test root"); }
            try (FileLock lock = acquired) {
                Properties manifest = EFiles.read(root.resolve("manifest.properties"));
                verifyRoot(root, manifest, evaluationFamily, inputs.family);
                for (EConfiguration.Case item : universe(c)) {
                    if (cachedComplete(root, manifest, item) && !inputs.cases.containsKey(item.key())) {
                        throw new IOException("Completed-case registry changed during preflight; retry without concurrent testing");
                    }
                }
                Map<String, Properties> results = completed(root, manifest, evaluationFamily, inputs);
                boolean pending = c.requestedCases().stream().anyMatch(item -> !results.containsKey(item.key()));
                if (!pending && "COMPLETE".equals(manifest.getProperty("status"))) {
                    inputs.verify();
                    System.out.println("E TEST COMPLETE requested scope verified; zero simulations/writes; cached cases=" + results.size());
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
                    Map<EConfiguration.Scenario, ProjectionService> banks = new LinkedHashMap<>();
                    for (EConfiguration.Case item : c.requestedCases()) {
                        if (results.containsKey(item.key())) { continue; }
                        EFiles.checkHash(inputs.root.resolve("manifest.properties"), inputs.rootHash);
                        Source source = inputs.cases.get(item.key());
                        source.verify();
                        ProjectionService bank = banks.get(item.scenario);
                        if (bank == null) {
                            bank = common(root, manifest, c, item.scenario, factory, evaluationFamily);
                            banks.put(item.scenario, bank);
                        }
                        Path attempt = fresh(root, item.stem() + "-attempt-");
                        manifest.setProperty(item.key() + ".path", attempt.getFileName().toString());
                        manifest.remove(item.key() + ".manifest-sha256");
                        EFiles.write(root.resolve("manifest.properties"), manifest, true);
                        Properties result = evaluate(attempt, source, c, evaluator, bank, evaluationFamily,
                                bankHash(root, manifest, item.scenario));
                        source.verify();
                        EFiles.checkHash(inputs.root.resolve("manifest.properties"), inputs.rootHash);
                        manifest.setProperty(item.key() + ".manifest-sha256", EFiles.hash(attempt.resolve("manifest.properties")));
                        EFiles.write(root.resolve("manifest.properties"), manifest, true);
                        results.put(item.key(), result);
                        System.out.println("E TEST completed " + item.key() + "; representative=" + result.getProperty("representative-status"));
                    }
                    inputs.verify();
                    report(root, manifest, c, inputs, results);
                    inputs.verify();
                    manifest.setProperty("status", "COMPLETE");
                    manifest.remove("failure");
                    EFiles.write(root.resolve("manifest.properties"), manifest, true);
                } catch (IOException | RuntimeException | Error failure) {
                    manifest.setProperty("status", "FAILED"); manifest.setProperty("failure", failure.toString());
                    try { EFiles.write(root.resolve("manifest.properties"), manifest, true); }
                    catch (IOException reporting) { failure.addSuppressed(reporting); }
                    throw failure;
                }
            }
        }
    }

    static String evaluationFamily(EConfiguration c, String trainingFamily, EvaluationService evaluator,
                                  ProjectionFactory factory) throws IOException {
        Properties controls = c.evaluationProperties();
        controls.setProperty("training-family-hash", trainingFamily);
        controls.setProperty("evaluation-policy", POLICY);
        controls.setProperty("evaluation-service", nonempty(evaluator.identity()));
        controls.setProperty("projection-service", nonempty(factory.identity()));
        return EFiles.fingerprint(controls, ETest.class, EComparison.class, EConfiguration.class,
                SchedulingEvaluator.class, ArchiveCandidate.class, FinalArchiveReader.class, PopulationSnapshots.class,
                DcDescriptorBank.class, PairedStatistics.class, evaluator.getClass(), factory.getClass());
    }

    static Map<String, EConfiguration.Case> targets(EConfiguration c, Path root, Properties previous) throws IOException {
        Map<String, EConfiguration.Case> targets = new LinkedHashMap<>();
        for (EConfiguration.Case item : c.requestedCases()) { targets.put(item.key(), item); }
        if (previous != null) {
            for (EConfiguration.Case item : universe(c)) {
                if (cachedComplete(root, previous, item)) { targets.put(item.key(), item); }
            }
        }
        return targets;
    }

    static Properties initialize(EConfiguration c, Path root, String trainingFamily, String evaluationFamily,
                                 EvaluationService evaluator, ProjectionFactory factory) throws IOException {
        Files.createDirectories(root.getParent()); Files.createDirectory(root);
        EFiles.write(root.resolve("resolved-evaluation-family.properties"), c.properties(), false);
        Properties manifest = new Properties();
        manifest.setProperty("protocol", PROTOCOL);
        manifest.setProperty("evaluation-family-hash", evaluationFamily);
        manifest.setProperty("training-family-hash", trainingFamily);
        manifest.setProperty("selection-policy", POLICY);
        manifest.setProperty("evaluation-service", evaluator.identity());
        manifest.setProperty("projection-service", factory.identity());
        manifest.setProperty("family-scope", "SELECTORS_AND_WHOLE_TRAINING_ROOT_MANIFEST_SHA_EXCLUDED");
        manifest.setProperty("common-d", "4"); manifest.setProperty("common-c", "3");
        manifest.setProperty("status", "INITIALIZED");
        artifact(manifest, root, "resolved-evaluation-family.properties");
        EFiles.write(root.resolve("manifest.properties"), manifest, false);
        return manifest;
    }

    static List<EConfiguration.Case> universe(EConfiguration c) {
        List<EConfiguration.Case> result = new ArrayList<>();
        for (EConfiguration.Method method : EConfiguration.Method.values()) for (EConfiguration.Regime regime : EConfiguration.Regime.values()) {
            for (EConfiguration.Scenario scenario : EConfiguration.Scenario.values()) for (int run = 0; run < c.runs; run++) {
                result.add(new EConfiguration.Case(method, regime, scenario, run));
            }
        }
        return result;
    }
    static boolean cachedComplete(Path root, Properties manifest, EConfiguration.Case item) throws IOException {
        if (!manifest.containsKey(item.key() + ".path")) { return false; }
        if (manifest.containsKey(item.key() + ".manifest-sha256")) { return true; }
        Path directory = EFiles.mapped(root, manifest.getProperty(item.key() + ".path"));
        Path marker = directory.resolve("manifest.properties");
        return Files.isRegularFile(marker) && "COMPLETE".equals(EFiles.read(marker).getProperty("status"));
    }

    static Path checkedRoot(EConfiguration c) throws IOException {
        Path absolute = c.testRoot.toAbsolutePath().normalize(), ancestor = absolute;
        while (ancestor != null && !Files.exists(ancestor)) { ancestor = ancestor.getParent(); }
        if (ancestor == null || !Files.isDirectory(ancestor)) { throw new IOException("No existing test-root directory ancestor"); }
        Path result = ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize();
        Path training = c.trainingRoot.toRealPath();
        if (result.startsWith(training) || training.startsWith(result)) { throw new IOException("Test/training roots must be non-nested"); }
        for (Path params : new Path[]{c.populationParams, c.archiveParams}) {
            Path src = params;
            while (src != null && !"src".equalsIgnoreCase(String.valueOf(src.getFileName()))) { src = src.getParent(); }
            if (src != null) {
                for (String name : new String[]{"src", "bin", "out", ".git"}) {
                    Path protectedPath = src.getParent().resolve(name).toAbsolutePath().normalize();
                    if (Files.exists(protectedPath)) { protectedPath = protectedPath.toRealPath(); }
                    if (result.startsWith(protectedPath)) { throw new IOException("Test output is inside protected project " + name); }
                }
            }
        }
        return result;
    }

    static final class Source {
        final EConfiguration.Case item;
        final Path directory;
        final Properties manifest;
        final Map<Path, String> hashes = new LinkedHashMap<>();
        final String manifestHash;
        Source(EConfiguration.Case item, Path directory, Properties manifest) throws IOException {
            this.item = item; this.directory = directory; this.manifest = manifest;
            manifestHash = EFiles.hash(directory.resolve("manifest.properties"));
            capture(directory, manifest, hashes);
        }
        List<ArchiveCandidate> load(EConfiguration c) throws IOException {
            verify();
            List<ArchiveCandidate> all;
            if (item.method == EConfiguration.Method.QDGP) {
                FinalArchiveReader.Archive archive = new FinalArchiveReader().read(directory.resolve(item.finalFile()));
                all = archive.candidates();
                long occupied = count(manifest, "native-occupied-cells"), capacity = count(manifest, "native-grid-cells");
                long side = count(manifest, "grid-side");
                if (side < 1 || Math.multiplyExact(side, side) != capacity
                        || occupied != all.size() || occupied > capacity || capacity < 1) {
                    throw new IOException("Invalid native QD geometry");
                }
            } else {
                PopulationSnapshots.Snapshot snapshot = PopulationSnapshots.read(directory.resolve(item.finalFile()));
                if (snapshot.generation() != c.generations - 1
                        || snapshot.trainingSeed() != count(manifest, "final-training-seed")) {
                    throw new IOException("Final population generation/seed mismatch");
                }
                try (BufferedReader reader = Files.newBufferedReader(directory.resolve(item.finalFile()), StandardCharsets.UTF_8)) {
                    if (!("Method: " + item.label()).equals(reader.readLine())) { throw new IOException("Final population method label mismatch"); }
                    if (!("Run: " + item.run).equals(reader.readLine())) { throw new IOException("Population run ID mismatch"); }
                }
                all = snapshot.candidates();
                if (all.size() != c.population) { throw new IOException("Final POPULATION size differs from configured population"); }
            }
            exact(manifest, "final-members", all.size());
            if (all.size() > c.nominalEvaluations()) { throw new IOException("Final output exceeds evaluation budget"); }
            long finite = 0;
            for (ArchiveCandidate a : all) { a.rules(); if (a.hasFiniteStoredFitness()) { finite++; } }
            exact(manifest, "stored-finite-members", finite);
            verify();
            return all;
        }
        void verify() throws IOException { verifyHashes(hashes); }
    }

    static final class Inputs {
        final EConfiguration config;
        final Path root;
        final String family, rootHash;
        final Map<String, Source> cases = new LinkedHashMap<>();
        final Map<Path, String> hashes = new LinkedHashMap<>();
        Inputs(EConfiguration c, Path root, String family) throws IOException {
            config = c; this.root = root; this.family = family;
            rootHash = EFiles.hash(root.resolve("manifest.properties"));
        }
        static Inputs read(EConfiguration c, Map<String, EConfiguration.Case> targets) throws IOException {
            Path root = c.trainingRoot.toRealPath();
            Properties manifest = EFiles.read(root.resolve("manifest.properties"));
            equal(manifest, "protocol", EConfiguration.PROTOCOL); equal(manifest, "status", "COMPLETE");
            String family = ETraining.fingerprint(c);
            equal(manifest, "family-hash", family);
            requireArtifacts(manifest, "resolved-study.properties");
            EFiles.verifyArtifacts(root, manifest);
            EConfiguration saved = EConfiguration.parse(new String[]{"--config", root.resolve("resolved-study.properties").toString()});
            if (!saved.trainingProperties().equals(c.trainingProperties())) { throw new IOException("Original training-family controls differ"); }
            Inputs result = new Inputs(c, root, family);
            capture(root, manifest, result.hashes);
            if (manifest.containsKey("report.path")) {
                Path report = EFiles.mapped(root, manifest.getProperty("report.path"));
                EFiles.checkHash(report.resolve("manifest.properties"), EFiles.required(manifest, "report.manifest-sha256"));
                Properties reportManifest = EFiles.read(report.resolve("manifest.properties"));
                equal(reportManifest, "status", "COMPLETE"); equal(reportManifest, "family-hash", family);
                EFiles.verifyArtifacts(report, reportManifest);
                capture(report, reportManifest, result.hashes);
            }
            for (Path path : new Path[]{c.sourcePath, c.populationParams, c.archiveParams}) {
                result.hashes.put(path, EFiles.hash(path));
            }
            Map<String, String> genotypes = new LinkedHashMap<>(), evaluated = new LinkedHashMap<>();
            Set<Path> paths = new HashSet<>();
            for (EConfiguration.Case item : targets.values()) {
                Path directory = EFiles.mapped(root, EFiles.required(manifest, item.key() + ".path")).toRealPath();
                if (!paths.add(directory)) { throw new IOException("Two training cases map to the same directory"); }
                EFiles.checkHash(directory.resolve("manifest.properties"), EFiles.required(manifest, item.key() + ".manifest-sha256"));
                Properties source = EFiles.read(directory.resolve("manifest.properties"));
                ETraining.validateCase(c, item, family, source);
                equal(source, "objective", item.scenario.objective.getName());
                if (number(source, "utilisation") != item.scenario.utilisation) { throw new IOException("Scenario utilisation mismatch"); }
                exact(source, "gp-seed", c.gpSeed(item)); exact(source, "training-seed", c.trainingSeed(item));
                exact(source, "descriptor-seed", c.descriptorSeed(item)); exact(source, "sampling-seed", c.samplingSeed(item));
                exact(source, "initial-training-seed", c.trainingSeed(item));
                long last = item.regime == EConfiguration.Regime.fixed ? c.trainingSeed(item)
                        : Math.addExact(c.trainingSeed(item), Math.multiplyExact(c.generations - 1L, c.seedStep));
                exact(source, "final-training-seed", last);
                if (count(source, "failed-training-evaluations") > c.nominalEvaluations()) { throw new IOException("Invalid failed training count"); }
                long total = Math.addExact(c.nominalEvaluations(), Math.addExact(count(source, "manual-reference-simulations"),
                        count(source, "descriptor-reference-simulations")));
                exact(source, "total-training-simulations", total);
                finite(source, "elapsed-training-seconds");
                requireArtifacts(source, item.finalFile());
                EFiles.verifyArtifacts(directory, source);
                for (String field : new String[]{"initial-genotype-sha256", "initial-evaluated-population-sha256"}) {
                    if (!EFiles.required(source, field).matches("[a-f0-9]{64}")) { throw new IOException("Invalid pairing hash"); }
                }
                String block = item.scenario.id + ":" + item.run;
                consistent(genotypes, block, EFiles.required(source, "initial-genotype-sha256"));
                consistent(evaluated, block, EFiles.required(source, "initial-evaluated-population-sha256"));
                Source input = new Source(item, directory, source);
                input.load(c); // Validate every target before any simulation; do not retain all outputs in heap.
                result.cases.put(item.key(), input);
            }
            result.verify();
            return result;
        }
        void verify() throws IOException {
            verifyHashes(hashes);
            for (Source source : cases.values()) { source.verify(); }
            if (!family.equals(ETraining.fingerprint(config))) { throw new IOException("Scientific training inputs changed during testing"); }
        }
    }

    private static void consistent(Map<String, String> map, String key, String value) throws IOException {
        String old = map.putIfAbsent(key, value);
        if (old != null && !old.equals(value)) { throw new IOException("Incompatible matched initial inputs for block " + key); }
    }
    private static void capture(Path directory, Properties manifest, Map<Path, String> hashes) throws IOException {
        hashes.put(directory.resolve("manifest.properties"), EFiles.hash(directory.resolve("manifest.properties")));
        for (String key : manifest.stringPropertyNames()) {
            if (key.startsWith("data.") && key.endsWith(".sha256")) {
                hashes.put(directory.resolve(key.substring(5, key.length() - 7)), manifest.getProperty(key));
            }
        }
    }
    private static void verifyHashes(Map<Path, String> hashes) throws IOException {
        for (Map.Entry<Path, String> entry : hashes.entrySet()) { EFiles.checkHash(entry.getKey(), entry.getValue()); }
    }

    static void verifyRoot(Path root, Properties manifest, String family, String trainingFamily) throws IOException {
        equal(manifest, "protocol", PROTOCOL); equal(manifest, "evaluation-family-hash", family);
        equal(manifest, "training-family-hash", trainingFamily);
        if (!Arrays.asList("INITIALIZED", "RUNNING", "FAILED", "COMPLETE").contains(EFiles.required(manifest, "status"))) {
            throw new IOException("Invalid evaluation-root status");
        }
        requireArtifacts(manifest, "resolved-evaluation-family.properties");
        EFiles.verifyArtifacts(root, manifest);
        for (String key : manifest.stringPropertyNames()) {
            if ((key.startsWith("common.") || key.startsWith("bank.")) && key.endsWith(".path")) {
                String prefix = key.substring(0, key.length() - 5);
                Path path = EFiles.mapped(root, manifest.getProperty(key));
                EFiles.checkHash(path.resolve("manifest.properties"), EFiles.required(manifest, prefix + ".manifest-sha256"));
                Properties bank = EFiles.read(path.resolve("manifest.properties"));
                equal(bank, "status", "COMPLETE"); equal(bank, "evaluation-family-hash", family);
                requireArtifacts(bank, "common_descriptor.csv"); EFiles.verifyArtifacts(path, bank);
            }
        }
        if (manifest.containsKey("report.path")) {
            Path report = EFiles.mapped(root, manifest.getProperty("report.path"));
            EFiles.checkHash(report.resolve("manifest.properties"), EFiles.required(manifest, "report.manifest-sha256"));
            Properties reportManifest = EFiles.read(report.resolve("manifest.properties"));
            equal(reportManifest, "status", "COMPLETE"); equal(reportManifest, "evaluation-family-hash", family);
            requireArtifacts(reportManifest, REPORT_FILES); EFiles.verifyArtifacts(report, reportManifest);
        } else if ("COMPLETE".equals(manifest.getProperty("status"))) { throw new IOException("COMPLETE root lacks report"); }
        if (manifest.stringPropertyNames().stream().anyMatch(key ->
                key.startsWith("parallel-execution.") && key.endsWith(".path"))) {
            EParallelTest.verifyExecutions(root, manifest);
        }
    }

    static Map<String, Properties> completed(Path root, Properties manifest, String family, Inputs inputs) throws IOException {
        Map<String, Properties> completed = new LinkedHashMap<>();
        for (Source source : inputs.cases.values()) {
            EConfiguration.Case item = source.item;
            if (!manifest.containsKey(item.key() + ".path")) { continue; }
            Path path = EFiles.mapped(root, manifest.getProperty(item.key() + ".path"));
            Path file = path.resolve("manifest.properties");
            boolean anchored = manifest.containsKey(item.key() + ".manifest-sha256");
            if (!Files.isRegularFile(file)) {
                if (anchored) { throw new IOException("Missing COMPLETE case manifest"); }
                continue;
            }
            Properties result = EFiles.read(file);
            if (!"COMPLETE".equals(result.getProperty("status"))) {
                if (anchored || !Arrays.asList("RUNNING", "FAILED").contains(result.getProperty("status"))) {
                    throw new IOException("Corrupt COMPLETE case/status");
                }
                continue;
            }
            if (anchored) { EFiles.checkHash(file, manifest.getProperty(item.key() + ".manifest-sha256")); }
            equal(result, "evaluation-family-hash", family); equal(result, "training-family-hash", inputs.family);
            equal(result, "case-key", item.key()); equal(result, "source-case-hash", source.manifest.getProperty("case-hash"));
            equal(result, "input-manifest-sha256", source.manifestHash);
            for (String field : new String[]{"method", "regime", "scenario-id", "run-id", "objective", "utilisation",
                    "gp-seed", "training-seed", "initial-training-seed", "final-training-seed",
                    "initial-genotype-sha256", "initial-evaluated-population-sha256", "output-kind"}) {
                equal(result, field, EFiles.required(source.manifest, field));
            }
            equal(result, "representative-policy", POLICY);
            equal(result, "common-bank-sha256", bankHash(root, manifest, item.scenario));
            requireArtifacts(result, "subset_plan.csv", "validation_instances.csv", "validation_rules.csv",
                    "test_instance_outcomes.csv", "member_projection.csv", "common_cells.csv");
            EFiles.verifyArtifacts(path, result);
            for (String metric : METRICS) { value(result, metric); }
            if (!anchored) { manifest.setProperty(item.key() + ".manifest-sha256", EFiles.hash(file)); }
            completed.put(item.key(), result);
        }
        return completed;
    }

    static String commonKey(EConfiguration.Scenario scenario) { return "common." + scenario.id; }
    static String bankHash(Path root, Properties manifest, EConfiguration.Scenario scenario) throws IOException {
        Path directory = EFiles.mapped(root, EFiles.required(manifest, commonKey(scenario) + ".path"));
        return EFiles.required(EFiles.read(directory.resolve("manifest.properties")), "data.common_descriptor.csv.sha256");
    }
    static ProjectionService common(Path root, Properties manifest, EConfiguration c,
                                             EConfiguration.Scenario scenario, ProjectionFactory factory, String family) throws IOException {
        String prefix = commonKey(scenario);
        String original = manifest.containsKey(prefix + ".path") ? bankHash(root, manifest, scenario) : null;
        Path path = fresh(root, "bank-" + scenario.id + "-attempt-");
        Properties bank = new Properties();
        bank.setProperty("protocol", PROTOCOL); bank.setProperty("evaluation-family-hash", family);
        bank.setProperty("scenario-id", scenario.id); bank.setProperty("d", "4"); bank.setProperty("c", "3");
        bank.setProperty("objective", scenario.objective.getName());
        bank.setProperty("utilisation", Double.toString(scenario.utilisation));
        bank.setProperty("simulation-seed", Long.toString(c.commonSeed(scenario)));
        bank.setProperty("sampling-seed", Long.toString(c.commonSamplingSeed(scenario)));
        bank.setProperty("simulation-count-status", "UNKNOWN_IF_INTERRUPTED"); bank.setProperty("status", "RUNNING");
        EFiles.write(path.resolve("manifest.properties"), bank, false);
        try {
            ProjectionService projection = factory.collect(c, scenario);
            if (projection.simulationCount() < 0) { throw new IOException("Invalid reference simulation count"); }
            bank.setProperty("reference-simulations", Long.toString(projection.simulationCount()));
            bank.setProperty("simulation-count-status", "RECORDED");
            projection.write(path.resolve("common_descriptor.csv"));
            artifact(bank, path, "common_descriptor.csv");
            if (original != null && !original.equals(bank.getProperty("data.common_descriptor.csv.sha256"))) {
                throw new IOException("Replayed per-scenario common bank differs from the original frozen bank");
            }
            bank.setProperty("status", "COMPLETE");
            EFiles.write(path.resolve("manifest.properties"), bank, true);
            String attempt = "bank." + path.getFileName();
            manifest.setProperty(attempt + ".path", path.getFileName().toString());
            manifest.setProperty(attempt + ".manifest-sha256", EFiles.hash(path.resolve("manifest.properties")));
            if (original == null) {
                manifest.setProperty(prefix + ".path", path.getFileName().toString());
                manifest.setProperty(prefix + ".manifest-sha256", EFiles.hash(path.resolve("manifest.properties")));
            }
            EFiles.write(root.resolve("manifest.properties"), manifest, true);
            return projection;
        } catch (IOException | RuntimeException | Error failure) {
            bank.setProperty("status", "FAILED"); bank.setProperty("failure", failure.toString());
            try { EFiles.write(path.resolve("manifest.properties"), bank, true); }
            catch (IOException reporting) { failure.addSuppressed(reporting); }
            throw failure;
        }
    }

    static List<ArchiveCandidate> subset(List<ArchiveCandidate> all, int cap, long seed) {
        if (cap < 1 || cap > 200) { throw new IllegalArgumentException("Subset cap must be 1..200"); }
        Map<String, ArchiveCandidate> unique = new TreeMap<>();
        for (ArchiveCandidate member : all) {
            if (!member.hasFiniteStoredFitness()) { continue; }
            ArchiveCandidate previous = unique.get(member.treeHash());
            if (previous != null && (!previous.sequencing().equals(member.sequencing())
                    || !previous.routing().equals(member.routing()))) { throw new IllegalArgumentException("Expression hash collision"); }
            if (previous == null || member.ordinal() < previous.ordinal()) { unique.put(member.treeHash(), member); }
        }
        List<ArchiveCandidate> shuffled = new ArrayList<>(unique.values());
        Collections.shuffle(shuffled, new Random(seed));
        return new ArrayList<>(shuffled.subList(0, Math.min(cap, shuffled.size())));
    }
    static ArchiveCandidate select(List<ArchiveCandidate> subset, Map<String, Double> means) {
        return subset.stream().filter(a -> Double.isFinite(means.get(a.treeHash())))
                .min(Comparator.comparingDouble((ArchiveCandidate a) -> means.get(a.treeHash()))
                        .thenComparing(ArchiveCandidate::treeHash).thenComparingInt(ArchiveCandidate::ordinal)).orElse(null);
    }

    static Properties evaluate(Path directory, Source source, EConfiguration c, EvaluationService evaluator,
                                       ProjectionService bank, String family, String bankHash) throws IOException {
        EConfiguration.Case item = source.item;
        Properties result = new Properties();
        for (String field : new String[]{"method", "regime", "scenario-id", "run-id", "objective", "utilisation",
                "gp-seed", "training-seed", "initial-training-seed", "final-training-seed",
                "initial-genotype-sha256", "initial-evaluated-population-sha256", "output-kind"}) {
            result.setProperty(field, EFiles.required(source.manifest, field));
        }
        result.setProperty("protocol", PROTOCOL); result.setProperty("evaluation-family-hash", family);
        result.setProperty("training-family-hash", EFiles.required(source.manifest, "family-hash"));
        result.setProperty("case-key", item.key()); result.setProperty("source-case-hash", source.manifest.getProperty("case-hash"));
        result.setProperty("input-manifest-sha256", source.manifestHash);
        result.setProperty("input-final-sha256", source.hashes.get(source.directory.resolve(item.finalFile())));
        result.setProperty("common-bank-sha256", bankHash);
        result.setProperty("representative-policy", POLICY);
        result.setProperty("validation-seed", Long.toString(c.validationSeed(item.scenario)));
        result.setProperty("test-seed", Long.toString(c.testSeed(item.scenario)));
        result.setProperty("subset-seed", Long.toString(c.subsetSeed(item)));
        result.setProperty("subset-frozen-before-validation", "true");
        result.setProperty("evaluation-count-status", "PARTIAL_IF_INTERRUPTED; consult preserved completed rows");
        result.setProperty("status", "RUNNING");
        EFiles.write(directory.resolve("manifest.properties"), result, false);
        try {
            List<ArchiveCandidate> all = source.load(c);
            List<ArchiveCandidate> draw = subset(all, c.validationCandidates, c.subsetSeed(item));
            int finiteStored = (int) all.stream().filter(ArchiveCandidate::hasFiniteStoredFitness).count();
            int available = (int) all.stream().filter(ArchiveCandidate::hasFiniteStoredFitness).map(ArchiveCandidate::treeHash).distinct().count();
            List<String> plan = new ArrayList<>();
            try (BufferedWriter writer = EFiles.create(directory.resolve("subset_plan.csv"))) {
                EFiles.row(writer, "case_key", "sample_index", "candidate_id", "tree_hash", "stored_fitness", "status");
                for (int i = 0; i < draw.size(); i++) {
                    ArchiveCandidate member = draw.get(i);
                    EFiles.row(writer, item.key(), i, member.id(), member.treeHash(), member.storedFitness(), "FROZEN_BEFORE_VALIDATION");
                    plan.add(member.id() + ":" + member.treeHash());
                }
            }
            result.setProperty("subset-plan-sha256", ArchiveCandidate.sha256(String.join("\n", plan)));
            result.setProperty("subset-available-pairs", Integer.toString(available));
            result.setProperty("subset-requested-cap", Integer.toString(c.validationCandidates));
            artifact(result, directory, "subset_plan.csv");
            EFiles.write(directory.resolve("manifest.properties"), result, true);
            Map<String, Double> validationMeans = new LinkedHashMap<>();
            List<Double> quality = new ArrayList<>();
            long validationFailures = 0;
            int failedRules = 0;
            try (BufferedWriter instances = EFiles.create(directory.resolve("validation_instances.csv"));
                 BufferedWriter rules = EFiles.create(directory.resolve("validation_rules.csv"))) {
                EFiles.row(instances, "case_key", "sample_index", "candidate_id", "tree_hash", "instance_index",
                        "simulation_seed", "objective", "value", "status", "failure_reason");
                EFiles.row(rules, "case_key", "sample_index", "candidate_id", "tree_hash", "mean_validation_objective",
                        "failed_instances", "evaluated_instances", "quality_scope");
                for (int index = 0; index < draw.size(); index++) {
                    ArchiveCandidate member = draw.get(index);
                    List<Double> values = new ArrayList<>();
                    int failures = 0;
                    for (int i = 0; i < c.validationInstances; i++) {
                        long seed = Math.addExact(c.validationSeed(item.scenario), i);
                        Outcome outcome = evaluator.evaluate(item, member, seed);
                        values.add(outcome.value);
                        if (!outcome.failure.isEmpty()) { failures++; validationFailures++; }
                        EFiles.row(instances, item.key(), index, member.id(), member.treeHash(), i, seed,
                                item.scenario.objective.getName(), outcome.value,
                                outcome.failure.isEmpty() ? "FINITE" : "VALIDATION_FAILED", outcome.failure);
                    }
                    if (failures > 0) { failedRules++; }
                    double mean = mean(values);
                    validationMeans.put(member.treeHash(), mean); quality.add(mean);
                    EFiles.row(rules, item.key(), index, member.id(), member.treeHash(), numeric(mean), failures,
                            c.validationInstances, "SUBSET_SAMPLE_ESTIMATE_NOT_FULL_OUTPUT_QUALITY");
                }
            }
            ArchiveCandidate representative = select(draw, validationMeans);
            result.setProperty("representative-id", representative == null ? "NONE" : representative.id());
            result.setProperty("representative-tree-hash", representative == null ? "NONE" : representative.treeHash());
            result.setProperty("representative-status", representative == null ? "NO_REPRESENTATIVE" : POLICY);
            result.setProperty("no-representative-reason", representative != null ? "NONE"
                    : draw.isEmpty() ? "NO_UNIQUE_STORED_FINITE_PAIR" : "ALL_FROZEN_SUBSET_VALIDATION_MEANS_NONFINITE");
            EFiles.write(directory.resolve("manifest.properties"), result, true);
            List<Double> test = new ArrayList<>();
            int testFailures = 0;
            try (BufferedWriter writer = EFiles.create(directory.resolve("test_instance_outcomes.csv"))) {
                EFiles.row(writer, "case_key", "candidate_id", "tree_hash", "instance_index", "simulation_seed",
                        "objective", "value", "executed", "status", "failure_reason");
                for (int i = 0; i < c.testInstances; i++) {
                    long seed = Math.addExact(c.testSeed(item.scenario), i);
                    if (representative == null) {
                        EFiles.row(writer, item.key(), "NONE", "NONE", i, seed, item.scenario.objective.getName(),
                                "Infinity", false, "NOT_EXECUTED_NO_REPRESENTATIVE", result.getProperty("no-representative-reason"));
                    } else {
                        Outcome outcome = evaluator.evaluate(item, representative, seed);
                        test.add(outcome.value);
                        if (!outcome.failure.isEmpty()) { testFailures++; }
                        EFiles.row(writer, item.key(), representative.id(), representative.treeHash(), i, seed,
                                item.scenario.objective.getName(), outcome.value, true,
                                outcome.failure.isEmpty() ? "FINITE" : "TEST_FAILED", outcome.failure);
                    }
                }
            }
            Map<String, Projected> cache = new LinkedHashMap<>();
            Map<Integer, long[]> buckets = new TreeMap<>();
            int failedProjection = 0;
            try (BufferedWriter writer = EFiles.create(directory.resolve("member_projection.csv"))) {
                EFiles.row(writer, "case_key", "output_kind", "candidate_id", "tree_hash", "stored_fitness", "stored_status",
                        "common_d", "common_c", "seq_cell", "rout_cell", "digits", "status", "failure_reason");
                for (ArchiveCandidate member : all) {
                    Projected pc = cache.get(member.treeHash());
                    if (pc == null) {
                        try {
                            double[] digits = bank.coordinates(member);
                            pc = new Projected(cells(digits), digits, "");
                        } catch (RuntimeException failure) { pc = new Projected(null, null, "PROJECTION_FAILED: " + failure); }
                        cache.put(member.treeHash(), pc);
                    }
                    if (pc.cell == null) { failedProjection++; }
                    else {
                        long[] counts = buckets.computeIfAbsent(pc.cell[0] * COMMON_SIDE + pc.cell[1], ignored -> new long[3]);
                        counts[0]++; counts[member.hasFiniteStoredFitness() ? 1 : 2]++;
                    }
                    EFiles.row(writer, item.key(), item.kind(), member.id(), member.treeHash(), member.storedFitness(),
                            member.hasFiniteStoredFitness() ? "STORED_FINITE" : "STORED_FAILURE", 4, 3,
                            pc.cell == null ? "NA" : pc.cell[0], pc.cell == null ? "NA" : pc.cell[1],
                            pc.digits == null ? "" : digits(pc.digits), pc.cell == null ? "FAILED" : "PROJECTED", pc.failure);
                }
            }
            try (BufferedWriter writer = EFiles.create(directory.resolve("common_cells.csv"))) {
                EFiles.row(writer, "case_key", "seq_cell", "rout_cell", "members", "stored_finite_members",
                        "stored_failure_members", "basis");
                for (Map.Entry<Integer, long[]> entry : buckets.entrySet()) {
                    long[] n = entry.getValue();
                    EFiles.row(writer, item.key(), entry.getKey() / COMMON_SIDE, entry.getKey() % COMMON_SIDE,
                            n[0], n[1], n[2], "ALL_ACTUAL_FINAL_MEMBERS_NO_ELITE_RESELECTION");
                }
            }
            put(result, "final_members", all.size()); put(result, "stored_finite_members", finiteStored);
            put(result, "stored_failure_members", all.size() - finiteStored);
            put(result, "unique_stored_finite_pairs", available);
            put(result, "duplicate_expression_members", all.size() - cache.size());
            put(result, "subset_available_pairs", available); put(result, "subset_size", draw.size());
            put(result, "subset_insufficient_case", available < c.validationCandidates ? 1 : 0);
            put(result, "validation_evaluation_count", (long) draw.size() * c.validationInstances);
            put(result, "validation_failure_count", validationFailures); put(result, "validation_failed_rule_count", failedRules);
            put(result, "quality_mean_validation_objective", mean(quality));
            List<Double> finiteRules = finiteValues(quality);
            put(result, "quality_finite_rule_count", finiteRules.size());
            put(result, "quality_finite_conditional_rule_mean", mean(finiteRules));
            put(result, "quality_finite_conditional_rule_median", median(finiteRules));
            put(result, "test_scheduled_count", c.testInstances); put(result, "test_executed_count", test.size());
            put(result, "test_unexecuted_count", c.testInstances - test.size()); put(result, "test_failure_count", testFailures);
            put(result, "test_failed_run", representative == null || testFailures > 0 ? 1 : 0);
            put(result, "no_representative_run", representative == null ? 1 : 0);
            put(result, "mean_test_objective", representative == null ? Double.POSITIVE_INFINITY : mean(test));
            put(result, "common_occupied_cells_all_retained", buckets.size());
            put(result, "common_coverage_all_retained", buckets.size() / (double) COMMON_CELLS);
            long finiteCells = buckets.values().stream().filter(n -> n[1] > 0).count();
            put(result, "common_occupied_cells_stored_finite", finiteCells);
            put(result, "common_coverage_stored_finite", finiteCells / (double) COMMON_CELLS);
            put(result, "common_projection_failures", failedProjection);
            boolean grid = item.method == EConfiguration.Method.QDGP;
            put(result, "native_occupied_cells", grid ? count(source.manifest, "native-occupied-cells") : Double.NaN);
            put(result, "native_grid_cells", grid ? count(source.manifest, "native-grid-cells") : Double.NaN);
            put(result, "native_coverage", grid ? count(source.manifest, "native-occupied-cells")
                    / (double) count(source.manifest, "native-grid-cells") : Double.NaN);
            put(result, "training_seconds", finite(source.manifest, "elapsed-training-seconds"));
            for (String field : new String[]{"fitness-evaluations", "failed-training-evaluations",
                    "manual-reference-simulations", "descriptor-reference-simulations", "total-training-simulations"}) {
                put(result, field.replace('-', '_'), count(source.manifest, field));
            }
            result.setProperty("outcome-status", representative == null || testFailures + validationFailures + failedProjection > 0
                    ? "COMPLETE_WITH_RECORDED_FAILURES" : "ALL_FINITE");
            result.setProperty("evaluation-count-status", "RECORDED_COMPLETE");
            for (String file : new String[]{"subset_plan.csv", "validation_instances.csv", "validation_rules.csv",
                    "test_instance_outcomes.csv", "member_projection.csv", "common_cells.csv"}) { artifact(result, directory, file); }
            source.verify();
            result.setProperty("status", "COMPLETE"); EFiles.write(directory.resolve("manifest.properties"), result, true);
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            result.setProperty("status", "FAILED"); result.setProperty("failure", failure.toString());
            try { EFiles.write(directory.resolve("manifest.properties"), result, true); }
            catch (IOException reporting) { failure.addSuppressed(reporting); }
            throw failure;
        }
    }

    private static final class Projected {
        final int[] cell;
        final double[] digits;
        final String failure;
        Projected(int[] cell, double[] digits, String failure) {
            this.cell = cell; this.digits = digits == null ? null : digits.clone(); this.failure = failure;
        }
    }
    static int[] cells(double[] digits) {
        if (digits == null || digits.length != 8) { throw new IllegalArgumentException("Expected independent d4,c3 vector"); }
        int[] cell = new int[2];
        for (int role = 0; role < 2; role++) for (int i = 0; i < 4; i++) {
            double digit = digits[role * 4 + i];
            if (!Double.isFinite(digit) || digit != Math.rint(digit) || digit < 0 || digit >= 3) {
                throw new IllegalArgumentException("Invalid zero-based common rank");
            }
            cell[role] = cell[role] * 3 + (int) digit;
        }
        return cell;
    }

    static void report(Path root, Properties manifest, EConfiguration c, Inputs inputs,
                               Map<String, Properties> results) throws IOException {
        Path report = fresh(root, "report-attempt-");
        EFiles.write(report.resolve("requested-scope.properties"), c.properties(), false);
        try (BufferedWriter rows = EFiles.create(report.resolve("run_results.csv"));
             BufferedWriter summary = EFiles.create(report.resolve("summary.csv"))) {
            List<Object> header = new ArrayList<>(Arrays.asList("case_key", "method", "regime", "scenario_id",
                    "objective", "run_id", "output_kind", "selection_policy", "representative_id", "representative_status"));
            header.addAll(Arrays.asList(METRICS)); EFiles.row(rows, header.toArray());
            descriptionHeader(summary, false);
            for (EConfiguration.Method method : EConfiguration.Method.values()) for (EConfiguration.Regime regime : EConfiguration.Regime.values()) {
                for (EConfiguration.Scenario scenario : EConfiguration.Scenario.values()) {
                    List<Properties> group = new ArrayList<>();
                    for (int run = 0; run < c.runs; run++) {
                        String key = new EConfiguration.Case(method, regime, scenario, run).key();
                        Properties result = results.get(key);
                        if (result == null) { continue; }
                        group.add(result);
                        List<Object> row = new ArrayList<>(Arrays.asList(key, method, regime, scenario.id, scenario.objective.getName(),
                                run, result.getProperty("output-kind"), POLICY, result.getProperty("representative-id"),
                                result.getProperty("representative-status")));
                        for (String metric : METRICS) { row.add(result.getProperty(metric)); }
                        EFiles.row(rows, row.toArray());
                    }
                    if (!group.isEmpty()) {
                        for (String metric : METRICS) {
                            List<Double> values = new ArrayList<>();
                            for (Properties result : group) { values.add(value(result, metric)); }
                            describe(summary, method.name(), regime.name(), scenario.id, "NONE", metric, values);
                        }
                    }
                }
            }
        }
        Properties comparisons = EComparison.write(report, c, results);
        Properties guide = new Properties();
        guide.setProperty("protocol", PROTOCOL);
        guide.setProperty("selection-policy", POLICY);
        guide.setProperty("selection", "Uniform without-replacement min(requested cap, unique stored-finite pairs),"
                + " frozen before any validation. Minimum FINITE mean validation objective within that subset,"
                + " tie by tree hash, then ordinal. Never global/output best, no training-best test anchor,"
                + " no validation-failure replacement and no test-based reselection.");
        guide.setProperty("no-representative", "If no finite validation mean exists, all held-out slots are scheduled"
                + " but executed=false, value=Infinity, NOT_EXECUTED_NO_REPRESENTATIVE. Executed/failure/unexecuted"
                + " denominators stay separate. No arbitrary fallback; the run remains in all summaries.");
        guide.setProperty("quality", "SUBSET SAMPLE ESTIMATE, not full-output/repertoire quality. Any failed validation"
                + " instance makes its rule mean Infinity and hence failure-inclusive subset mean Infinity."
                + " Finite-conditional rule mean/median/count are labelled descriptions, not unconditional estimators.");
        guide.setProperty("common", "Independent frozen d4,c3 bank per scenario, shared across methods/regimes."
                + " All ACTUAL FINAL members, including stored failures and duplicate memberships, are projected."
                + " Stored-finite coverage is separate; no validation filtering, snapshot union, elite reselection or feedback."
                + " POPULATION has no native-QD coverage: native_* is NA, never zero or an archive label.");
        guide.setProperty("objective", "Native whole simulation objective selected by scenario. MAX_FLOWTIME is"
                + " simulation.objectiveValue(MAX_FLOWTIME), never an arithmetic average of training case maxima.");
        guide.setProperty("incremental", "Training selectors are not the universe. COMPLETE training means requested"
                + " source selection complete. Evaluation family excludes selectors and whole training-root manifest SHA."
                + " Every completed case is pinned by its own source manifest/artifacts. Current root SHA is captured"
                + " only for within-invocation immutability. No concurrent training/testing."
                + " Reports contain ALL completed cases, not only the latest requested selection.");
        guide.setProperty("resume", "Root lock; immutable case/bank/report attempts. Completed caches are hash-verified."
                + " Pending scenario banks replay once per invocation and must match the original frozen hash."
                + " A completely satisfied scope on a COMPLETE root performs zero evaluation, replay or writes."
                + " Reports are immutable attempt directories. Only the atomic root properties publication"
                + " commits report.path plus report.manifest-sha256. Interrupted/unpublished report directories"
                + " are preserved, not adopted or overwritten. Corrupt referenced reports/cases are fatal.");
        guide.setProperty("report-location", "Read manifest.properties at the test root, resolve report.path"
                + " safely relative to that root, verify report.manifest-sha256, then read the report's"
                + " run_results.csv/summary.csv/comparison files. No root CSV copies are created or required."
                + " Each report aggregates ALL registered completed cases, not only the latest selector scope."
                + " Training reports likewise follow training manifest report.path when present;"
                + " training_runs.csv is never required at the training root.");
        guide.setProperty("validity", "Use the authoritative referenced report only when root status is COMPLETE"
                + " and all referenced hashes verify. RUNNING/FAILED roots have not committed a completed publication."
                + " A COMPLETE Lex-only scope can still have comparison-status=COMPARISON_NOT_READY.");
        guide.setProperty("comparison", "QDGP vs GP and LEXICASE, blocked by scenario/regime/run."
                + " Missing controls => COMPARISON_NOT_READY, no synthetic comparison."
                + " Only 30-run matched primary held-out objectives receive exact conditional signed-rank (wilcox zeros,"
                + " averaged ties) and Holm alpha=.05. Family=6 scenarios*2 comparators=12 per regime."
                + " Unavailable p slots count as 1 for correction only; displayed p/effect/reject stay NA."
                + " Any nonfinite paired outcome disables inference for the ENTIRE contrast; never test a finite subset."
                + " Positive rank-biserial means smaller QDGP objective. All other metrics descriptive."
                + " Native coverage across unlike output kinds is not an applicable paired metric."
                + " Matching requires paired initial source inputs and common validation/test seeds;"
                + " later individual birth-seed phases remain native and are not asserted identical across methods."
                + " Non-rejection is not equivalence; no cross-regime/scenario pooling or causal cost claims.");
        try (BufferedWriter writer = EFiles.create(report.resolve("results_guide.properties"))) {
            for (String field : new java.util.TreeSet<>(guide.stringPropertyNames())) {
                writer.write(field + "=" + guide.getProperty(field) + "\n");
            }
        }
        Properties record = new Properties();
        record.setProperty("protocol", PROTOCOL); record.setProperty("status", "COMPLETE");
        record.setProperty("evaluation-family-hash", manifest.getProperty("evaluation-family-hash"));
        record.setProperty("source-root-manifest-sha256", inputs.rootHash);
        for (String file : REPORT_FILES) { artifact(record, report, file); }
        artifact(record, report, "requested-scope.properties");
        inputs.verify();
        EFiles.write(report.resolve("manifest.properties"), record, false);
        // The caller commits this pointer with all counters in one atomic root-properties write.
        manifest.setProperty("report.path", report.getFileName().toString());
        manifest.setProperty("report.manifest-sha256", EFiles.hash(report.resolve("manifest.properties")));
        manifest.putAll(comparisons);
        manifest.setProperty("completed-case-count", Integer.toString(results.size()));
        manifest.setProperty("completion-scope", "ALL_REGISTERED_COMPLETED_CASES; NOT_THE_1080_CASE_UNIVERSE");
        long scheduled = 0, executed = 0, unexecuted = 0, failures = 0, validation = 0, validationFailures = 0, noRep = 0;
        long failedRules = 0, insufficient = 0, failedRuns = 0, validationFailureRuns = 0, selected = 0;
        long smallestSubset = Long.MAX_VALUE;
        for (Properties result : results.values()) {
            scheduled += count(result, "test_scheduled_count"); executed += count(result, "test_executed_count");
            unexecuted += count(result, "test_unexecuted_count"); failures += count(result, "test_failure_count");
            validation += count(result, "validation_evaluation_count"); validationFailures += count(result, "validation_failure_count");
            noRep += count(result, "no_representative_run");
            failedRules += count(result, "validation_failed_rule_count");
            insufficient += count(result, "subset_insufficient_case");
            failedRuns += count(result, "test_failed_run");
            if (count(result, "validation_failure_count") > 0) { validationFailureRuns++; }
            selected += count(result, "subset_size");
            smallestSubset = Math.min(smallestSubset, count(result, "subset_size"));
        }
        manifest.setProperty("test-scheduled-count", Long.toString(scheduled));
        manifest.setProperty("test-executed-count", Long.toString(executed));
        manifest.setProperty("test-unexecuted-count", Long.toString(unexecuted));
        manifest.setProperty("test-failure-count", Long.toString(failures));
        manifest.setProperty("validation-evaluation-count", Long.toString(validation));
        manifest.setProperty("validation-failure-count", Long.toString(validationFailures));
        manifest.setProperty("validation-failed-rule-count", Long.toString(failedRules));
        manifest.setProperty("validation-failure-runs", Long.toString(validationFailureRuns));
        manifest.setProperty("test-failed-runs", Long.toString(failedRuns));
        manifest.setProperty("subset-insufficient-cases", Long.toString(insufficient));
        manifest.setProperty("subset-min-size", results.isEmpty() ? "NA" : Long.toString(smallestSubset));
        manifest.setProperty("subset-total-selected-pairs", Long.toString(selected));
        manifest.setProperty("no-representative-cases", Long.toString(noRep));
        manifest.setProperty("outcome-status", failures + validationFailures + noRep > 0 ? "COMPLETE_WITH_RECORDED_FAILURES" : "ALL_FINITE");
        accountReferenceAttempts(root, manifest);
        if (manifest.stringPropertyNames().stream().anyMatch(key ->
                key.startsWith("parallel-execution.") && key.endsWith(".path"))) {
            EParallelTest.account(root, manifest);
        }
    }

    static void accountReferenceAttempts(Path root, Properties manifest) throws IOException {
        long references = 0, unknownBanks = 0;
        try (java.nio.file.DirectoryStream<Path> paths = Files.newDirectoryStream(root, "bank-*-attempt-*")) {
            for (Path path : paths) {
                if (!path.toRealPath().startsWith(root.toRealPath())) { throw new IOException("Bank attempt escapes output"); }
                Path marker = path.resolve("manifest.properties");
                Properties bank = Files.isRegularFile(marker) ? EFiles.read(marker) : new Properties();
                if ("RECORDED".equals(bank.getProperty("simulation-count-status"))) { references += count(bank, "reference-simulations"); }
                else { unknownBanks++; }
            }
        }
        manifest.setProperty("recorded-common-reference-simulations", Long.toString(references));
        manifest.setProperty("unknown-common-reference-attempts", Long.toString(unknownBanks));
        manifest.setProperty("interrupted-case-evaluation-counts", "UNKNOWN_IF_INTERRUPTED; preserved attempt rows are not replaced");
    }

    static void descriptionHeader(BufferedWriter writer, boolean paired) throws IOException {
        EFiles.row(writer, "method", "regime", "scenario_id", "baseline", "metric", "run_count", "finite_count",
                "nonfinite_count", "missing_count", "all_run_mean", "all_run_sample_sd", "all_run_min", "all_run_max",
                "finite_only_mean", "finite_only_sample_sd", "scope");
    }
    static void describe(BufferedWriter writer, String method, String regime, String scenario, String baseline,
                         String metric, List<Double> values) throws IOException {
        List<Double> finite = finiteValues(values);
        int missing = (int) values.stream().filter(v -> Double.isNaN(v)).count();
        int infinite = values.size() - finite.size() - missing;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            if (!Double.isNaN(value)) { min = Math.min(min, value); max = Math.max(max, value); }
        }
        EFiles.row(writer, method, regime, scenario, baseline, metric, values.size(), finite.size(), infinite, missing,
                numeric(mean(values)), numeric(sd(values)), numeric(missing == 0 ? min : Double.NaN),
                numeric(missing == 0 ? max : Double.NaN), numeric(mean(finite)), numeric(sd(finite)),
                metric.startsWith("quality_") ? "SUBSET_SAMPLE_ESTIMATE; FINITE_CONDITIONAL_WHERE_LABELLED"
                        : "RUN_LEVEL_DESCRIPTIVE");
    }
    static double mean(List<Double> values) {
        if (values.isEmpty() || values.stream().anyMatch(v -> Double.isNaN(v))) { return Double.NaN; }
        if (values.stream().anyMatch(v -> v == Double.POSITIVE_INFINITY)) { return Double.POSITIVE_INFINITY; }
        return PairedStatistics.mean(array(values));
    }
    static double sd(List<Double> values) {
        return values.stream().allMatch(Double::isFinite) ? PairedStatistics.sampleSd(array(values)) : Double.NaN;
    }
    private static double[] array(List<Double> values) {
        double[] output = new double[values.size()];
        for (int i = 0; i < output.length; i++) { output[i] = values.get(i); }
        return output;
    }
    private static List<Double> finiteValues(List<Double> values) {
        List<Double> finite = new ArrayList<>();
        for (double value : values) { if (Double.isFinite(value)) { finite.add(value); } }
        return finite;
    }
    private static double median(List<Double> finite) {
        if (finite.isEmpty()) { return Double.NaN; }
        List<Double> sorted = new ArrayList<>(finite); Collections.sort(sorted);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : sorted.get(mid - 1) * .5 + sorted.get(mid) * .5;
    }
    static double value(Properties values, String key) {
        String text = values.getProperty(key);
        if (text == null) { throw new IllegalArgumentException("Missing result metric " + key); }
        return text.equals("NA") ? Double.NaN : Double.parseDouble(text);
    }
    static String numeric(double value) { return Double.isNaN(value) ? "NA" : Double.toString(value); }
    private static void put(Properties p, String key, long value) { p.setProperty(key, Long.toString(value)); }
    private static void put(Properties p, String key, double value) { p.setProperty(key, numeric(value)); }
    private static String nonempty(String value) {
        if (value == null || value.trim().isEmpty()) { throw new IllegalArgumentException("Missing service identity"); }
        return value;
    }
    private static double number(Properties p, String key) throws IOException { return Double.parseDouble(EFiles.required(p, key)); }
    private static double finite(Properties p, String key) throws IOException {
        double value = number(p, key);
        if (!Double.isFinite(value) || value < 0) { throw new IOException("Invalid finite metric " + key); }
        return value;
    }
    static long count(Properties p, String key) throws IOException {
        String value = EFiles.required(p, key);
        if (!value.matches("[0-9]+")) { throw new IOException("Invalid count " + key); }
        return Long.parseLong(value);
    }
    private static void equal(Properties p, String key, String expected) throws IOException {
        if (!expected.equals(EFiles.required(p, key))) { throw new IOException("Expected " + key + "=" + expected); }
    }
    private static void exact(Properties p, String key, long expected) throws IOException {
        if (count(p, key) != expected) { throw new IOException("Expected " + key + "=" + expected); }
    }
    private static void artifact(Properties p, Path directory, String file) throws IOException {
        p.setProperty("data." + file + ".sha256", EFiles.hash(directory.resolve(file)));
    }
    private static void requireArtifacts(Properties p, String... files) throws IOException {
        for (String file : files) {
            if (!EFiles.required(p, "data." + file + ".sha256").matches("[a-f0-9]{64}")) { throw new IOException("Invalid artifact hash"); }
        }
    }
    static Path fresh(Path root, String prefix) throws IOException {
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            Path path = root.resolve(prefix + String.format(java.util.Locale.ROOT, "%04d", i));
            if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { return Files.createDirectory(path); }
        }
        throw new IOException("Attempt namespace exhausted");
    }
    private static String digits(double[] digits) {
        List<String> fields = new ArrayList<>();
        for (double value : digits) { fields.add(Integer.toString((int) value)); }
        return String.join(";", fields);
    }
}
