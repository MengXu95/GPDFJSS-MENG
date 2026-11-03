package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PushbackReader;
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
import mengxu.algorithm.MAPElites.FuzzySystem.revision.SchedulingEvaluator;
import yimei.jss.jobshop.Objective;

/** Separate, resumable testing; neither held-out results nor common PCs feed training or selection. */
public final class DcTest {
    public static final String PROTOCOL = "qdgpf-dc-test-v1";
    private static final String BASELINE = "d4-c3";
    private static final int COMMON_D = 4, COMMON_C = 3;
    private static final String NATIVE_HEADER = "candidate_id,run_id,d,c,generation,seq_cell,rout_cell,"
            + "descriptor_digits_zero_based,raw_training_fitness,stored_comparison_score,"
            + "birth_generation,tree_hash,sequencing,routing";
    private static final String[] METRICS = {
        "native_coverage_all_retained", "native_occupied_cells_all_retained", "native_retained_members",
        "stored_finite_members", "stored_failure_members",
        "unique_retained_expression_pairs", "duplicate_expression_members", "unique_stored_finite_pairs",
        "best_raw_fixed_training_fitness", "quality_sample_size", "quality_mean_validation_objective",
        "quality_failed_rule_count", "quality_finite_rule_count", "quality_finite_instance_count",
        "quality_finite_conditional_rule_mean", "quality_finite_conditional_rule_median",
        "representative_mean_test_objective", "test_failure_count", "quality_failure_count",
        "common_coverage_all_retained", "common_occupied_cells_all_retained", "common_projected_members",
        "common_projection_failures", "common_coverage_stored_finite", "common_occupied_cells_stored_finite",
        "common_projected_stored_finite_members", "common_projection_stored_finite_failures",
        "training_seconds", "peak_observed_used_heap_bytes",
        "fitness_evaluations", "manual_reference_simulations", "descriptor_reference_simulations"
    };

    private DcTest() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("DcTest --config <dc-study.properties> [--resume] [--dry-run] [--set key=value ...]");
            System.out.println("Default: 50 independent tests of the best RAW FIXED TRAINING member; quality is a"
                    + " <=200 unique stored-finite pair SAMPLE ESTIMATE on 5 common validation instances."
                    + " quality-sample=0 exhausts stored-finite unique pairs, never all-archive quality."
                    + " Common PCs are independently frozen at d4,c3. Descriptions only; no p-values.");
            return;
        }
        run(DcConfiguration.parse(args));
    }

    interface EvaluationService {
        String identity();
        Outcome evaluate(ArchiveCandidate candidate, long seed) throws IOException;
    }

    static final class Outcome {
        final double value;
        final String failure;

        Outcome(double value, String failure) {
            if (failure == null || (Double.isFinite(value) && value >= 0 && value < Double.MAX_VALUE
                    ? !failure.isEmpty() : value != Double.POSITIVE_INFINITY || failure.trim().isEmpty())) {
                throw new IllegalArgumentException("Outcome must be finite nonnegative with no failure, or +Infinity with a reason");
            }
            this.value = value; this.failure = failure;
        }
    }

    interface ProjectionService {
        double[] coordinates(ArchiveCandidate candidate);
        void write(Path path) throws IOException;
        long simulationCount();
    }

    interface ProjectionFactory {
        String identity();
        ProjectionService collect(DcConfiguration config) throws IOException;
    }

    private static final class RealEvaluator implements EvaluationService {
        private final SchedulingEvaluator evaluator;
        RealEvaluator(DcConfiguration c) {
            evaluator = new SchedulingEvaluator(c.machines, c.jobs, c.warmup,
                    c.utilisation, c.dueFactor, Objective.MEAN_TARDINESS);
        }
        @Override public String identity() { return "SchedulingEvaluator.mean-tardiness.recording-failures.v1"; }
        @Override public Outcome evaluate(ArchiveCandidate candidate, long seed) {
            try {
                SchedulingEvaluator.Evaluation result = evaluator.evaluateRecordingFailures(candidate, seed, 1);
                return new Outcome(result.values().get(0), result.failures().get(0));
            } catch (RuntimeException failure) {
                return new Outcome(Double.POSITIVE_INFINITY, "SCHEDULING_EXCEPTION: " + failure);
            }
        }
    }

    private static final class RealProjectionFactory implements ProjectionFactory {
        @Override public String identity() { return "IndependentDcDescriptorBank.d4-c3.v1"; }
        @Override public ProjectionService collect(DcConfiguration c) {
            return new RealProjection(DcDescriptorBank.collect(c.commonDescriptorSeed, c.commonSamplingSeed,
                    c.machines, c.descriptorJobs, c.descriptorWarmup, c.utilisation, c.dueFactor, COMMON_D, COMMON_C));
        }
    }

    private static final class RealProjection implements ProjectionService {
        private final DcDescriptorBank bank;
        RealProjection(DcDescriptorBank bank) { this.bank = bank; }
        @Override public double[] coordinates(ArchiveCandidate candidate) {
            return bank.coordinates(candidate.rules(), COMMON_D, COMMON_C);
        }
        @Override public void write(Path path) throws IOException { bank.write(path, COMMON_D, COMMON_C); }
        @Override public long simulationCount() { return bank.simulationCount(); }
    }

    public static void run(DcConfiguration config) throws IOException {
        run(config, new RealEvaluator(config), new RealProjectionFactory());
    }

    static void run(DcConfiguration config, EvaluationService evaluator) throws IOException {
        run(config, evaluator, new RealProjectionFactory());
    }

    static void run(DcConfiguration config, EvaluationService evaluator, ProjectionFactory factory) throws IOException {
        if (config.qualitySample > 200) { throw new IllegalArgumentException("quality-sample must be 0 (all finite unique) or 1..200"); }
        Inputs inputs = Inputs.read(config);
        Properties controls = config.properties();
        controls.setProperty("training-study-hash", inputs.studyHash);
        controls.setProperty("training-manifest-sha256", inputs.hashes.get(inputs.root.resolve("manifest.properties")));
        controls.setProperty("evaluation-service", nonempty(evaluator.identity(), "evaluation-service identity"));
        controls.setProperty("projection-service", nonempty(factory.identity(), "projection-service identity"));
        controls.setProperty("protocol", PROTOCOL);
        String fingerprint = DcFiles.fingerprint(controls, DcTest.class, DcConfiguration.class, DcFiles.class,
                DcDescriptorBank.class, ArchiveCandidate.class, FinalArchiveReader.class, SchedulingEvaluator.class,
                evaluator.getClass(), factory.getClass());
        Path root = checkedRoot(config, inputs.root);
        if (config.dryRun) {
            if (Files.exists(root)) {
                if (!config.resume) { throw new IOException("Existing test root requires --resume"); }
                Properties saved = DcFiles.read(root.resolve("manifest.properties"));
                verifyRoot(root, saved, fingerprint, inputs);
                completed(root, saved, fingerprint, inputs);
            }
            inputs.verify();
            System.out.println("DC TEST PREFLIGHT PASSED: " + inputs.cases.size() + " verified libraries; no simulations/output.");
            return;
        }
        Properties initial;
        if (Files.exists(root)) {
            if (!config.resume) { throw new IOException("Existing test root requires --resume; use a fresh sibling test root"); }
            initial = DcFiles.read(root.resolve("manifest.properties"));
            verifyRoot(root, initial, fingerprint, inputs);
        } else {
            Files.createDirectories(root.getParent());
            Files.createDirectory(root);
            DcFiles.write(root.resolve("resolved-test.properties"), config.properties(), false);
            initial = new Properties();
            initial.setProperty("protocol", PROTOCOL);
            initial.setProperty("study-hash", fingerprint);
            initial.setProperty("training-study-hash", inputs.studyHash);
            initial.setProperty("training-manifest-sha256", inputs.hashes.get(inputs.root.resolve("manifest.properties")));
            initial.setProperty("scenario", config.scenario());
            initial.setProperty("num-runs", Integer.toString(config.runs));
            initial.setProperty("num-settings", Integer.toString(config.settings.size()));
            initial.setProperty("evaluation-service", evaluator.identity());
            initial.setProperty("projection-service", factory.identity());
            initial.setProperty("quality-mode", qualityMode(config));
            initial.setProperty("common-d", "4"); initial.setProperty("common-c", "3");
            initial.setProperty("status", "INITIALIZED");
            artifact(initial, root, "resolved-test.properties");
            DcFiles.write(root.resolve("manifest.properties"), initial, false);
        }
        try (FileChannel channel = FileChannel.open(root.resolve(".test.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock acquired;
            try { acquired = channel.tryLock(); }
            catch (OverlappingFileLockException failure) { throw new IOException("Test root is already locked", failure); }
            if (acquired == null) { throw new IOException("Another test process owns this output"); }
            try (FileLock lock = acquired) {
                Properties manifest = DcFiles.read(root.resolve("manifest.properties"));
                verifyRoot(root, manifest, fingerprint, inputs);
                Map<String, Properties> completed = completed(root, manifest, fingerprint, inputs);
                if ("COMPLETE".equals(manifest.getProperty("status"))) {
                    if (completed.size() != inputs.cases.size()) { throw new IOException("COMPLETE test study has missing cases"); }
                    inputs.verify();
                    System.out.println("DC TEST COMPLETE verified; no simulations or output changed: " + root);
                    return;
                }
                manifest.setProperty("status", "RUNNING");
                DcFiles.write(root.resolve("manifest.properties"), manifest, true);
                try {
                    ProjectionService projection = null;
                    if (completed.size() != inputs.cases.size()) {
                        projection = prepareCommon(root, manifest, config, factory, fingerprint);
                    }
                    for (CaseInput input : inputs.cases) {
                        String key = key(input.setting, input.run);
                        if (completed.containsKey(key)) { continue; }
                        input.verify();
                        Path directory = fresh(root, input.setting.id() + "-run-" + input.run + "-attempt-");
                        manifest.setProperty(key + ".path", directory.getFileName().toString());
                        manifest.remove(key + ".manifest-sha256");
                        DcFiles.write(root.resolve("manifest.properties"), manifest, true);
                        Properties result = execute(directory, input, config, evaluator, projection, fingerprint,
                                commonHash(root, manifest));
                        input.verify();
                        manifest.setProperty(key + ".manifest-sha256", DcFiles.hash(directory.resolve("manifest.properties")));
                        DcFiles.write(root.resolve("manifest.properties"), manifest, true);
                        completed.put(key, result);
                        System.out.println("DC TEST completed " + key + "; test failures="
                                + result.getProperty("test_failure_count") + ", quality failures="
                                + result.getProperty("quality_failure_count") + ", projection failures="
                                + result.getProperty("common_projection_failures"));
                    }
                    aggregate(root, manifest, inputs, completed, config);
                    inputs.verify();
                    manifest.setProperty("status", "COMPLETE");
                    manifest.remove("failure");
                    DcFiles.write(root.resolve("manifest.properties"), manifest, true);
                } catch (IOException | RuntimeException | Error failure) {
                    manifest.setProperty("status", "FAILED");
                    manifest.setProperty("failure", failure.toString());
                    try { DcFiles.write(root.resolve("manifest.properties"), manifest, true); }
                    catch (IOException reporting) { failure.addSuppressed(reporting); }
                    throw failure;
                }
            }
        }
    }

    private static Path checkedRoot(DcConfiguration config, Path training) throws IOException {
        Path target = config.testRoot.toAbsolutePath().normalize();
        Path existing = target;
        while (existing != null && !Files.exists(existing)) { existing = existing.getParent(); }
        if (existing == null || !Files.isDirectory(existing)) { throw new IOException("Test root has no directory ancestor"); }
        Path resolved = existing.toRealPath().resolve(existing.relativize(target)).normalize();
        if (resolved.startsWith(training) || training.startsWith(resolved)) {
            throw new IOException("Training and test outputs must be separate, non-nested directories");
        }
        Path source = config.paramsPath;
        while (source != null && !"src".equalsIgnoreCase(String.valueOf(source.getFileName()))) { source = source.getParent(); }
        if (source != null) {
            Path project = source.getParent();
            for (String name : new String[]{"src", "bin", "out", ".git"}) {
                Path protectedPath = project.resolve(name).toAbsolutePath().normalize();
                if (Files.exists(protectedPath)) { protectedPath = protectedPath.toRealPath(); }
                if (resolved.startsWith(protectedPath)) { throw new IOException("Test output may not be inside project " + name); }
            }
        }
        return resolved;
    }

    private static void verifyRoot(Path root, Properties manifest, String fingerprint, Inputs inputs) throws IOException {
        equal(manifest, "protocol", PROTOCOL);
        equal(manifest, "study-hash", fingerprint);
        equal(manifest, "training-study-hash", inputs.studyHash);
        if (!Arrays.asList("INITIALIZED", "RUNNING", "FAILED", "COMPLETE").contains(required(manifest, "status"))) {
            throw new IOException("Unknown test-root status");
        }
        requireArtifacts(manifest, "resolved-test.properties");
        DcFiles.verifyArtifacts(root, manifest);
        if ("COMPLETE".equals(manifest.getProperty("status"))) {
            requireArtifacts(manifest, "run_results.csv", "summary.csv", "paired_differences.csv",
                    "paired_summary.csv", "results_guide.properties");
            Path report = DcFiles.mapped(root, required(manifest, "report.path"));
            DcFiles.checkHash(report.resolve("manifest.properties"), required(manifest, "report.manifest-sha256"));
            Properties saved = DcFiles.read(report.resolve("manifest.properties"));
            equal(saved, "status", "COMPLETE"); equal(saved, "study-hash", fingerprint);
            DcFiles.verifyArtifacts(report, saved);
        }
        if (manifest.containsKey("common.path")) {
            Path common = DcFiles.mapped(root, manifest.getProperty("common.path"));
            DcFiles.checkHash(common.resolve("manifest.properties"), required(manifest, "common.manifest-sha256"));
            Properties saved = DcFiles.read(common.resolve("manifest.properties"));
            equal(saved, "status", "COMPLETE");
            equal(saved, "study-hash", fingerprint);
            requireArtifacts(saved, "common_descriptor.csv");
            DcFiles.verifyArtifacts(common, saved);
        }
        for (String name : manifest.stringPropertyNames()) {
            if (name.startsWith("bank.") && name.endsWith(".path")) {
                String prefix = name.substring(0, name.length() - 5);
                Path attempt = DcFiles.mapped(root, manifest.getProperty(name));
                DcFiles.checkHash(attempt.resolve("manifest.properties"), required(manifest, prefix + ".manifest-sha256"));
                DcFiles.verifyArtifacts(attempt, DcFiles.read(attempt.resolve("manifest.properties")));
            }
        }
    }

    private static Map<String, Properties> completed(Path root, Properties manifest,
                                                     String fingerprint, Inputs inputs) throws IOException {
        Map<String, Properties> result = new LinkedHashMap<>();
        for (CaseInput input : inputs.cases) {
            String key = key(input.setting, input.run);
            if (!manifest.containsKey(key + ".path")) {
                if ("COMPLETE".equals(manifest.getProperty("status"))) { throw new IOException("Missing completed mapping: " + key); }
                continue;
            }
            Path directory = DcFiles.mapped(root, manifest.getProperty(key + ".path"));
            Path savedPath = directory.resolve("manifest.properties");
            if (!Files.isRegularFile(savedPath)) {
                if (manifest.containsKey(key + ".manifest-sha256")) { throw new IOException("Missing completed case manifest: " + key); }
                continue;
            }
            Properties saved = DcFiles.read(savedPath);
            if (!"COMPLETE".equals(saved.getProperty("status"))) {
                if (!Arrays.asList("RUNNING", "FAILED").contains(saved.getProperty("status"))) {
                    throw new IOException("Unknown case-attempt status: " + key);
                }
                if (manifest.containsKey(key + ".manifest-sha256") || "COMPLETE".equals(manifest.getProperty("status"))) {
                    throw new IOException("Previously COMPLETE case became incomplete: " + key);
                }
                continue;
            }
            equal(saved, "protocol", PROTOCOL); equal(saved, "study-hash", fingerprint);
            equal(saved, "setting-id", input.setting.id()); integer(saved, "run-id", input.run);
            equal(saved, "input-run-manifest-sha256", input.hashes.get(input.directory.resolve("manifest.properties")));
            equal(saved, "common-bank-sha256", commonHash(root, manifest));
            if (manifest.containsKey(key + ".manifest-sha256")) {
                DcFiles.checkHash(savedPath, manifest.getProperty(key + ".manifest-sha256"));
            } else if ("COMPLETE".equals(manifest.getProperty("status"))) { throw new IOException("Missing completed case hash: " + key); }
            DcFiles.verifyArtifacts(directory, saved);
            requireArtifacts(saved, "test_instance_outcomes.csv", "quality_validation_instances.csv",
                    "quality_sample.csv", "member_projection.csv", "common_cells.csv");
            for (String metric : METRICS) { value(saved, metric); }
            if (!manifest.containsKey(key + ".manifest-sha256")) {
                manifest.setProperty(key + ".manifest-sha256", DcFiles.hash(savedPath));
            }
            result.put(key, saved);
        }
        return result;
    }

    private static ProjectionService prepareCommon(Path root, Properties manifest, DcConfiguration config,
                                                   ProjectionFactory factory, String fingerprint) throws IOException {
        String expected = manifest.containsKey("common.path") ? commonHash(root, manifest) : null;
        Path directory = fresh(root, "common-attempt-");
        Properties saved = new Properties();
        saved.setProperty("protocol", PROTOCOL); saved.setProperty("study-hash", fingerprint);
        saved.setProperty("status", "RUNNING");
        saved.setProperty("simulation-count-status", "UNKNOWN_IF_INTERRUPTED");
        saved.setProperty("simulation-seed", Long.toString(config.commonDescriptorSeed));
        saved.setProperty("sampling-seed", Long.toString(config.commonSamplingSeed));
        saved.setProperty("d", "4"); saved.setProperty("c", "3");
        DcFiles.write(directory.resolve("manifest.properties"), saved, false);
        try {
            ProjectionService projection = factory.collect(config);
            if (projection.simulationCount() < 0) { throw new IOException("Invalid common reference simulation count"); }
            saved.setProperty("reference-simulations", Long.toString(projection.simulationCount()));
            saved.setProperty("simulation-count-status", "RECORDED");
            projection.write(directory.resolve("common_descriptor.csv"));
            String hash = DcFiles.hash(directory.resolve("common_descriptor.csv"));
            if (expected != null && !expected.equals(hash)) { throw new IOException("Replayed common frozen bank differs from original"); }
            artifact(saved, directory, "common_descriptor.csv");
            saved.setProperty("status", "COMPLETE");
            DcFiles.write(directory.resolve("manifest.properties"), saved, true);
            String prefix = "bank." + directory.getFileName();
            manifest.setProperty(prefix + ".path", directory.getFileName().toString());
            manifest.setProperty(prefix + ".manifest-sha256", DcFiles.hash(directory.resolve("manifest.properties")));
            if (expected == null) {
                manifest.setProperty("common.path", directory.getFileName().toString());
                manifest.setProperty("common.manifest-sha256", DcFiles.hash(directory.resolve("manifest.properties")));
            }
            DcFiles.write(root.resolve("manifest.properties"), manifest, true);
            return projection;
        } catch (IOException | RuntimeException | Error failure) {
            saved.setProperty("status", "FAILED"); saved.setProperty("failure", failure.toString());
            try { DcFiles.write(directory.resolve("manifest.properties"), saved, true); }
            catch (IOException reporting) { failure.addSuppressed(reporting); }
            throw failure;
        }
    }

    private static String commonHash(Path root, Properties manifest) throws IOException {
        Path directory = DcFiles.mapped(root, required(manifest, "common.path"));
        Properties bank = DcFiles.read(directory.resolve("manifest.properties"));
        return required(bank, "data.common_descriptor.csv.sha256");
    }

    static final class CaseInput {
        final DcConfiguration.Setting setting;
        final int run;
        final Path directory;
        final Properties manifest;
        final FinalArchiveReader.Archive archive;
        final Map<Path, String> hashes = new LinkedHashMap<>();
        final Map<String, int[]> nativeCells = new LinkedHashMap<>();
        CaseInput(DcConfiguration.Setting setting, int run, Path directory, Properties manifest,
                  FinalArchiveReader.Archive archive) {
            this.setting = setting; this.run = run; this.directory = directory; this.manifest = manifest; this.archive = archive;
        }
        void verify() throws IOException { for (Map.Entry<Path, String> e : hashes.entrySet()) { DcFiles.checkHash(e.getKey(), e.getValue()); } }
    }

    static final class Inputs {
        final DcConfiguration config;
        final Path root;
        final String studyHash;
        final List<CaseInput> cases = new ArrayList<>();
        final Map<Path, String> hashes = new LinkedHashMap<>();
        Inputs(DcConfiguration config, Path root, String studyHash) { this.config = config; this.root = root; this.studyHash = studyHash; }
        static Inputs read(DcConfiguration c) throws IOException {
            if (c.settings.stream().noneMatch(setting -> setting.id().equals(BASELINE))) {
                throw new IOException("Include d4:3 to provide the paired descriptive baseline");
            }
            Path root = c.trainingRoot.toRealPath();
            Properties manifest = DcFiles.read(root.resolve("manifest.properties"));
            equal(manifest, "protocol", DcConfiguration.PROTOCOL); equal(manifest, "status", "COMPLETE");
            equal(manifest, "scenario", c.scenario());
            integer(manifest, "num-runs", c.runs); integer(manifest, "num-settings", c.settings.size());
            if (manifest.containsKey("nominal-evaluations-per-run")) {
                integer(manifest, "nominal-evaluations-per-run", c.nominalEvaluations());
            }
            String studyHash = required(manifest, "study-hash");
            if (!studyHash.equals(DcTraining.fingerprint(c))) {
                throw new IOException("Original training configuration/parameter inputs/source/runtime changed");
            }
            Properties savedTraining = DcConfiguration.parse(new String[]{"--config",
                    root.resolve("resolved-study.properties").toString()}).trainingProperties();
            if (!savedTraining.equals(c.trainingProperties())) { throw new IOException("Training properties differ from saved resolved-study.properties"); }
            requireArtifacts(manifest, "resolved-study.properties");
            DcFiles.verifyArtifacts(root, manifest);
            Inputs result = new Inputs(c, root, studyHash);
            result.capture(root, manifest, result.hashes);
            result.hashes.put(c.sourcePath, DcFiles.hash(c.sourcePath));
            result.hashes.put(c.paramsPath, DcFiles.hash(c.paramsPath));
            Set<Path> mapped = new HashSet<>();
            Map<Integer, String> initial = new TreeMap<>();
            Map<String, String> snapshots = new TreeMap<>();
            for (DcConfiguration.Setting setting : c.settings) {
                for (int run = 0; run < c.runs; run++) {
                    String key = key(setting, run);
                    Path directory = DcFiles.mapped(root, required(manifest, key + ".path")).toRealPath();
                    if (!mapped.add(directory)) { throw new IOException("Duplicate training run mapping"); }
                    DcFiles.checkHash(directory.resolve("manifest.properties"), required(manifest, key + ".manifest-sha256"));
                    Properties saved = DcFiles.read(directory.resolve("manifest.properties"));
                    equal(saved, "status", "COMPLETE"); equal(saved, "study-hash", studyHash);
                    equal(saved, "method", "QDGPf"); equal(saved, "scenario", c.scenario());
                    integer(saved, "d", setting.d); integer(saved, "c", setting.c); integer(saved, "run-id", run);
                    integer(saved, "grid-side", setting.side()); integer(saved, "grid-cells", setting.cells());
                    integer(saved, "cell-capacity", 1); integer(saved, "nominal-evaluations", c.nominalEvaluations());
                    integer(saved, "fitness-evaluations", c.nominalEvaluations());
                    integer(saved, "gp-seed", Math.addExact(c.gpSeedBase, run));
                    integer(saved, "training-seed", Math.addExact(c.trainingSeedBase, run));
                    integer(saved, "descriptor-seed", Math.addExact(c.descriptorSeedBase, run));
                    integer(saved, "sampling-seed", Math.addExact(c.samplingSeedBase, run));
                    count(saved, "manual-reference-simulations"); count(saved, "descriptor-reference-simulations");
                    if (saved.containsKey("total-training-simulations")) {
                        integer(saved, "total-training-simulations", Math.addExact(c.nominalEvaluations(),
                                Math.addExact(count(saved, "manual-reference-simulations"),
                                        count(saved, "descriptor-reference-simulations"))));
                    }
                    count(saved, "peak-used-heap-bytes"); finite(saved, "elapsed-training-seconds");
                    requireArtifacts(saved, "job." + run + ".out.stat", "job." + run + ".out.csv",
                            "initial_population.stat", "snapshot_bank.csv", "training_descriptor.csv",
                            "generations.csv", "archive_curve.csv", "resolved.params");
                    DcFiles.verifyArtifacts(directory, saved);
                    String initialHash = required(saved, "data.initial_population.stat.sha256");
                    String previous = initial.putIfAbsent(run, initialHash);
                    if (previous != null && !previous.equals(initialHash)) { throw new IOException("Paired settings have different initial populations"); }
                    String snapshotHash = required(saved, "data.snapshot_bank.csv.sha256");
                    String priorBank = snapshots.putIfAbsent(run + ":" + setting.c, snapshotHash);
                    if (priorBank != null && !priorBank.equals(snapshotHash)) {
                        throw new IOException("Settings with the same run and c do not share the nested snapshot bank");
                    }
                    FinalArchiveReader.Archive archive = new FinalArchiveReader().read(directory.resolve("job." + run + ".out.stat"));
                    integer(saved, "native-retained-members", archive.declaredCount());
                    integer(saved, "native-occupied-cells", archive.declaredCount());
                    if (archive.declaredCount() > setting.cells() || archive.declaredCount() > c.nominalEvaluations()) {
                        throw new IOException("Native archive exceeds cell-capacity-one geometry or evaluation budget");
                    }
                    double best = Double.POSITIVE_INFINITY;
                    int finite = 0;
                    for (ArchiveCandidate candidate : archive.candidates()) {
                        candidate.rules();
                        if (candidate.hasFiniteStoredFitness()) { finite++; best = Math.min(best, candidate.storedFitness()); }
                    }
                    if (Double.compare(number(saved, "best-training-fitness"), best) != 0) { throw new IOException("Best RAW training fitness disagrees with archive"); }
                    if (saved.containsKey("finite-stored-members")) { integer(saved, "finite-stored-members", finite); }
                    CaseInput input = new CaseInput(setting, run, directory, saved, archive);
                    result.capture(directory, saved, input.hashes);
                    readNative(input, c.generations - 1);
                    result.cases.add(input);
                }
            }
            result.verify();
            return result;
        }
        private void capture(Path directory, Properties manifest, Map<Path, String> target) throws IOException {
            target.put(directory.resolve("manifest.properties"), DcFiles.hash(directory.resolve("manifest.properties")));
            for (String property : manifest.stringPropertyNames()) {
                if (property.startsWith("data.") && property.endsWith(".sha256")) {
                    target.put(directory.resolve(property.substring(5, property.length() - 7)), manifest.getProperty(property));
                }
            }
        }
        void verify() throws IOException {
            for (Map.Entry<Path, String> e : hashes.entrySet()) { DcFiles.checkHash(e.getKey(), e.getValue()); }
            for (CaseInput input : cases) { input.verify(); }
            if (!studyHash.equals(DcTraining.fingerprint(config))) { throw new IOException("Original training inputs changed during testing"); }
        }
    }

    private static void readNative(CaseInput input, int generation) throws IOException {
        List<List<String>> rows = csv(input.directory.resolve("job." + input.run + ".out.csv"), NATIVE_HEADER);
        if (rows.size() != input.archive.declaredCount()) { throw new IOException("Native CSV/archive member count mismatch"); }
        Set<Integer> cells = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() != 14) { throw new IOException("Invalid native CSV row"); }
            ArchiveCandidate a = input.archive.candidates().get(i);
            if (!a.id().equals(row.get(0)) || !a.treeHash().equals(row.get(11))
                    || !a.sequencing().equals(row.get(12).trim().replaceAll("\\s+", " "))
                    || !a.routing().equals(row.get(13).trim().replaceAll("\\s+", " "))) {
                throw new IOException("Native CSV/archive identity mismatch");
            }
            if (Long.parseLong(row.get(1)) != input.run || Long.parseLong(row.get(2)) != input.setting.d
                    || Long.parseLong(row.get(3)) != input.setting.c || Long.parseLong(row.get(4)) != generation
                    || Double.compare(Double.parseDouble(row.get(8)), a.storedFitness()) != 0) {
                throw new IOException("Native CSV controls/raw fitness mismatch");
            }
            int x = Integer.parseInt(row.get(5)), y = Integer.parseInt(row.get(6));
            String[] digits = row.get(7).split(";", -1);
            double[] values = new double[digits.length];
            for (int j = 0; j < digits.length; j++) { values[j] = Double.parseDouble(digits[j]); }
            int[] actual = cells(values, input.setting.d, input.setting.c);
            if (x != actual[0] || y != actual[1] || !cells.add(x * input.setting.side() + y)) {
                throw new IOException("Invalid/duplicate native cell coordinates");
            }
            double comparison = Double.parseDouble(row.get(9));
            int birth = Integer.parseInt(row.get(10));
            if (Double.isNaN(comparison) || comparison < 0 || birth < 0 || birth > generation) { throw new IOException("Invalid native comparison/birth metadata"); }
            input.nativeCells.put(a.id(), new int[]{x, y});
        }
    }

    static int[] cells(double[] digits, int d, int c) {
        if (digits == null || digits.length != 2 * d) { throw new IllegalArgumentException("Expected 2*d descriptor digits"); }
        int[] result = new int[2];
        for (int role = 0; role < 2; role++) {
            for (int i = 0; i < d; i++) {
                double digit = digits[role * d + i];
                if (!Double.isFinite(digit) || digit != Math.rint(digit) || digit < 0 || digit >= c) {
                    throw new IllegalArgumentException("Non-integer/out-of-alphabet descriptor digit");
                }
                result[role] = Math.addExact(Math.multiplyExact(result[role], c), (int) digit);
            }
        }
        return result;
    }

    static ArchiveCandidate representative(List<ArchiveCandidate> candidates) {
        return candidates.stream().filter(ArchiveCandidate::hasFiniteStoredFitness)
                .min(Comparator.comparingDouble(ArchiveCandidate::storedFitness)
                        .thenComparingInt(ArchiveCandidate::ordinal)).orElse(null);
    }

    static List<ArchiveCandidate> qualitySample(List<ArchiveCandidate> candidates, int size, long seed) {
        if (size < 0) { throw new IllegalArgumentException("Negative quality sample"); }
        Map<String, ArchiveCandidate> unique = new TreeMap<>();
        for (ArchiveCandidate a : candidates) {
            if (!a.hasFiniteStoredFitness()) { continue; }
            ArchiveCandidate old = unique.get(a.treeHash());
            if (old != null && (!old.sequencing().equals(a.sequencing()) || !old.routing().equals(a.routing()))) {
                throw new IllegalArgumentException("Expression-pair hash collision");
            }
            if (old == null || a.storedFitness() < old.storedFitness()
                    || (a.storedFitness() == old.storedFitness() && a.ordinal() < old.ordinal())) { unique.put(a.treeHash(), a); }
        }
        List<ArchiveCandidate> values = new ArrayList<>(unique.values());
        Collections.shuffle(values, new Random(seed));
        return new ArrayList<>(values.subList(0, size == 0 ? values.size() : Math.min(size, values.size())));
    }

    private static Properties execute(Path directory, CaseInput input, DcConfiguration config,
                                      EvaluationService evaluator, ProjectionService projection,
                                      String fingerprint, String bankHash) throws IOException {
        Properties result = new Properties();
        result.setProperty("protocol", PROTOCOL); result.setProperty("study-hash", fingerprint);
        result.setProperty("setting-id", input.setting.id()); result.setProperty("run-id", Integer.toString(input.run));
        result.setProperty("input-run-manifest-sha256", input.hashes.get(input.directory.resolve("manifest.properties")));
        result.setProperty("training-study-hash", required(input.manifest, "study-hash"));
        result.setProperty("common-bank-sha256", bankHash); result.setProperty("status", "RUNNING");
        result.setProperty("quality-mode", qualityMode(config));
        result.setProperty("method", "QDGPf"); result.setProperty("scenario", config.scenario());
        result.setProperty("native-grid-side", Integer.toString(input.setting.side()));
        result.setProperty("native-grid-cells", Integer.toString(input.setting.cells()));
        result.setProperty("common-grid-side", Integer.toString(DcDescriptorBank.side(COMMON_D, COMMON_C)));
        result.setProperty("common-grid-cells", Integer.toString(Math.multiplyExact(
                DcDescriptorBank.side(COMMON_D, COMMON_C), DcDescriptorBank.side(COMMON_D, COMMON_C))));
        result.setProperty("validation-seed", Long.toString(config.validationSeed));
        result.setProperty("test-seed", Long.toString(config.testSeed));
        result.setProperty("validation-instances", Integer.toString(config.validationInstances));
        result.setProperty("test-instances", Integer.toString(config.testInstances));
        result.setProperty("quality-sample-limit", Integer.toString(config.qualitySample));
        result.setProperty("evaluation-count-status", "UNKNOWN_IF_INTERRUPTED; completed instance rows are preserved");
        result.setProperty("representative-policy", "MIN_RAW_FIXED_TRAINING_THEN_ORDINAL; NO_VALIDATION_OR_TEST_RESELECTION");
        result.setProperty("quality-sampling-seed", Long.toString(config.qualitySamplingSeed + input.run));
        DcFiles.write(directory.resolve("manifest.properties"), result, false);
        try {
            List<ArchiveCandidate> all = input.archive.candidates();
            ArchiveCandidate best = representative(all);
            List<ArchiveCandidate> selected = qualitySample(all, config.qualitySample, config.qualitySamplingSeed + input.run);
            int uniqueFinite = qualitySample(all, 0, 0).size();
            int finiteStored = (int) all.stream().filter(ArchiveCandidate::hasFiniteStoredFitness).count();
            List<String> plan = new ArrayList<>();
            plan.add("representative=" + (best == null ? "NONE" : best.id()));
            for (ArchiveCandidate candidate : selected) { plan.add(candidate.id() + ":" + candidate.treeHash()); }
            result.setProperty("selection-frozen-before-evaluation", "true");
            result.setProperty("selection-plan-sha256", ArchiveCandidate.sha256(String.join("\n", plan)));
            result.setProperty("planned-quality-sample-size", Integer.toString(selected.size()));
            result.setProperty("representative-id", best == null ? "NONE" : best.id());
            result.setProperty("representative-tree-hash", best == null ? "NONE" : best.treeHash());
            DcFiles.write(directory.resolve("manifest.properties"), result, true);
            List<Double> testValues = new ArrayList<>(), qualityValues = new ArrayList<>();
            int testFailures = 0, qualityFailedPairs = 0;
            long qualityFailures = 0;
            try (BufferedWriter tests = DcFiles.create(directory.resolve("test_instance_outcomes.csv"))) {
                DcFiles.row(tests, "setting_id", "run_id", "candidate_id", "tree_hash", "raw_fixed_training_fitness",
                        "instance_index", "simulation_seed", "mean_tardiness", "status", "failure_reason");
                if (best != null) {
                    for (int i = 0; i < config.testInstances; i++) {
                        long seed = Math.addExact(config.testSeed, i);
                        Outcome outcome = evaluator.evaluate(best, seed);
                        testValues.add(outcome.value);
                        if (!outcome.failure.isEmpty()) { testFailures++; }
                        DcFiles.row(tests, input.setting.id(), input.run, best.id(), best.treeHash(), best.storedFitness(),
                                i, seed, outcome.value, outcome.failure.isEmpty() ? "FINITE" : "TEST_FAILED", outcome.failure);
                    }
                }
            }
            try (BufferedWriter validation = DcFiles.create(directory.resolve("quality_validation_instances.csv"));
                 BufferedWriter sample = DcFiles.create(directory.resolve("quality_sample.csv"))) {
                DcFiles.row(validation, "setting_id", "run_id", "candidate_id", "tree_hash", "sample_index",
                        "instance_index", "simulation_seed", "mean_tardiness", "status", "failure_reason");
                DcFiles.row(sample, "setting_id", "run_id", "sample_index", "candidate_id", "tree_hash",
                        "raw_fixed_training_fitness", "mean_validation_objective", "validation_failure_count",
                        "validation_evaluation_count", "quality_mode");
                for (int j = 0; j < selected.size(); j++) {
                    ArchiveCandidate a = selected.get(j);
                    List<Double> values = new ArrayList<>();
                    int failures = 0;
                    for (int i = 0; i < config.validationInstances; i++) {
                        long seed = Math.addExact(config.validationSeed, i);
                        Outcome outcome = evaluator.evaluate(a, seed);
                        values.add(outcome.value);
                        if (!outcome.failure.isEmpty()) { failures++; qualityFailures++; }
                        DcFiles.row(validation, input.setting.id(), input.run, a.id(), a.treeHash(), j, i, seed,
                                outcome.value, outcome.failure.isEmpty() ? "FINITE" : "VALIDATION_FAILED", outcome.failure);
                    }
                    if (failures > 0) { qualityFailedPairs++; }
                    double mean = mean(values);
                    qualityValues.add(mean);
                    DcFiles.row(sample, input.setting.id(), input.run, j, a.id(), a.treeHash(), a.storedFitness(),
                            numeric(mean), failures, config.validationInstances, qualityMode(config));
                }
            }
            Set<String> selectedHashes = new HashSet<>();
            for (ArchiveCandidate a : selected) { selectedHashes.add(a.treeHash()); }
            Map<String, Projected> cache = new LinkedHashMap<>();
            Map<Integer, int[]> occupied = new TreeMap<>();
            int projected = 0, projectionFailures = 0, projectedFinite = 0, failedFiniteProjection = 0;
            try (BufferedWriter members = DcFiles.create(directory.resolve("member_projection.csv"))) {
                DcFiles.row(members, "setting_id", "run_id", "candidate_id", "tree_hash", "raw_fixed_training_fitness",
                        "stored_status", "native_seq_cell", "native_rout_cell", "quality_pair_selected",
                        "representative_selected", "common_d", "common_c", "common_seq_cell", "common_rout_cell",
                        "common_digits_zero_based", "projection_status", "projection_failure_reason");
                for (ArchiveCandidate a : all) {
                    Projected pc = cache.get(a.treeHash());
                    if (pc == null) {
                        try {
                            double[] digits = projection.coordinates(a);
                            pc = new Projected(cells(digits, COMMON_D, COMMON_C), digits, "");
                        } catch (RuntimeException failure) {
                            pc = new Projected(null, null, "COMMON_PROJECTION_FAILED: " + failure);
                        }
                        cache.put(a.treeHash(), pc);
                    }
                    if (pc.failure.isEmpty()) {
                        projected++;
                        if (a.hasFiniteStoredFitness()) { projectedFinite++; }
                        int key = pc.cell[0] * DcDescriptorBank.side(COMMON_D, COMMON_C) + pc.cell[1];
                        int[] counts = occupied.computeIfAbsent(key, ignored -> new int[3]);
                        counts[0]++; counts[a.hasFiniteStoredFitness() ? 1 : 2]++;
                    } else {
                        projectionFailures++;
                        if (a.hasFiniteStoredFitness()) { failedFiniteProjection++; }
                    }
                    int[] nativeCell = input.nativeCells.get(a.id());
                    DcFiles.row(members, input.setting.id(), input.run, a.id(), a.treeHash(), a.storedFitness(),
                            a.hasFiniteStoredFitness() ? "STORED_FINITE" : "STORED_FAILURE", nativeCell[0], nativeCell[1],
                            selectedHashes.contains(a.treeHash()), best != null && best.id().equals(a.id()),
                            COMMON_D, COMMON_C, pc.cell == null ? "NA" : pc.cell[0], pc.cell == null ? "NA" : pc.cell[1],
                            pc.digits == null ? "" : digits(pc.digits),
                            pc.failure.isEmpty() ? "PROJECTED" : "PROJECTION_FAILED", pc.failure);
                }
            }
            try (BufferedWriter writer = DcFiles.create(directory.resolve("common_cells.csv"))) {
                DcFiles.row(writer, "common_d", "common_c", "seq_cell", "rout_cell", "retained_members",
                        "stored_finite_members", "stored_failure_members", "projection_basis");
                int side = DcDescriptorBank.side(COMMON_D, COMMON_C);
                for (Map.Entry<Integer, int[]> entry : occupied.entrySet()) {
                    int[] n = entry.getValue();
                    DcFiles.row(writer, COMMON_D, COMMON_C, entry.getKey() / side, entry.getKey() % side,
                            n[0], n[1], n[2], "ALL_RETAINED_NO_ELITE_RESELECTION");
                }
            }
            put(result, "native_coverage", all.size() / (double) input.setting.cells());
            put(result, "native_coverage_all_retained", all.size() / (double) input.setting.cells());
            put(result, "native_occupied_cells_all_retained", all.size());
            put(result, "native_retained_members", all.size()); put(result, "stored_finite_members", finiteStored);
            put(result, "stored_failure_members", all.size() - finiteStored);
            put(result, "unique_retained_expression_pairs", cache.size());
            put(result, "duplicate_expression_members", all.size() - cache.size());
            put(result, "unique_stored_finite_pairs", uniqueFinite); put(result, "quality_sample_size", selected.size());
            put(result, "best_raw_fixed_training_fitness", best == null ? Double.POSITIVE_INFINITY : best.storedFitness());
            put(result, "quality_mean_validation_objective", mean(qualityValues));
            put(result, "quality_failed_rule_count", qualityFailedPairs);
            put(result, "quality_finite_rule_count", selected.size() - qualityFailedPairs);
            put(result, "quality_finite_instance_count",
                    Math.subtractExact(Math.multiplyExact((long) selected.size(), config.validationInstances), qualityFailures));
            put(result, "quality_finite_conditional_rule_mean", finiteMean(qualityValues));
            put(result, "quality_finite_conditional_rule_median", finiteMedian(qualityValues));
            put(result, "representative_mean_test_objective", mean(testValues));
            put(result, "test_failure_count", testFailures); put(result, "quality_failure_count", qualityFailures);
            put(result, "common_coverage_all_retained", occupied.size() / (double) Math.multiplyExact(
                    DcDescriptorBank.side(COMMON_D, COMMON_C), DcDescriptorBank.side(COMMON_D, COMMON_C)));
            put(result, "common_occupied_cells_all_retained", occupied.size());
            put(result, "common_projected_members", projected); put(result, "common_projection_failures", projectionFailures);
            long finiteCells = occupied.values().stream().filter(counts -> counts[1] > 0).count();
            put(result, "common_occupied_cells_stored_finite", finiteCells);
            put(result, "common_coverage_stored_finite", finiteCells / (double) Math.multiplyExact(
                    DcDescriptorBank.side(COMMON_D, COMMON_C), DcDescriptorBank.side(COMMON_D, COMMON_C)));
            put(result, "common_projected_stored_finite_members", projectedFinite);
            put(result, "common_projection_stored_finite_failures", failedFiniteProjection);
            put(result, "training_seconds", finite(input.manifest, "elapsed-training-seconds"));
            put(result, "peak_observed_used_heap_bytes", count(input.manifest, "peak-used-heap-bytes"));
            put(result, "fitness_evaluations", count(input.manifest, "fitness-evaluations"));
            put(result, "manual_reference_simulations", count(input.manifest, "manual-reference-simulations"));
            put(result, "descriptor_reference_simulations", count(input.manifest, "descriptor-reference-simulations"));
            result.setProperty("representative-id", best == null ? "NONE" : best.id());
            result.setProperty("representative-tree-hash", best == null ? "NONE" : best.treeHash());
            result.setProperty("representative-status", best == null ? "NO_FINITE_STORED_MEMBER"
                    : testFailures > 0 ? "TEST_FAILURES_PRESERVED" : "TEST_FINITE");
            result.setProperty("quality-status", selected.isEmpty() ? "NO_FINITE_STORED_PAIR"
                    : qualityFailures > 0 ? "VALIDATION_FAILURES_PRESERVED" : "VALIDATION_FINITE");
            result.setProperty("test-evaluation-count", Integer.toString(testValues.size()));
            result.setProperty("quality-evaluation-count", Long.toString((long) selected.size() * config.validationInstances));
            result.setProperty("quality-failed-pairs", Integer.toString(qualityFailedPairs));
            result.setProperty("quality-finite-only-pair-mean", numeric(finiteMean(qualityValues)));
            result.setProperty("test-finite-only-instance-mean", numeric(finiteMean(testValues)));
            result.setProperty("evaluation-count-status", "RECORDED_COMPLETE");
            for (String file : new String[]{"test_instance_outcomes.csv", "quality_validation_instances.csv",
                    "quality_sample.csv", "member_projection.csv", "common_cells.csv"}) { artifact(result, directory, file); }
            input.verify();
            result.setProperty("status", "COMPLETE");
            DcFiles.write(directory.resolve("manifest.properties"), result, true);
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            result.setProperty("status", "FAILED"); result.setProperty("failure", failure.toString());
            try { DcFiles.write(directory.resolve("manifest.properties"), result, true); }
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

    private static void aggregate(Path root, Properties manifest, Inputs inputs, Map<String, Properties> completed,
                                  DcConfiguration config) throws IOException {
        Path directory = fresh(root, "report-attempt-");
        try (BufferedWriter rows = DcFiles.create(directory.resolve("run_results.csv"));
             BufferedWriter summary = DcFiles.create(directory.resolve("summary.csv"));
             BufferedWriter pairs = DcFiles.create(directory.resolve("paired_differences.csv"));
             BufferedWriter paired = DcFiles.create(directory.resolve("paired_summary.csv"))) {
            List<Object> header = new ArrayList<>(Arrays.asList("setting_id", "d", "c", "run_id", "native_grid_side",
                    "native_grid_cells", "common_grid_side", "common_grid_cells", "quality_mode",
                    "representative_id", "representative_status", "quality_status", "test_evaluation_count",
                    "quality_evaluation_count", "quality_failed_pairs"));
            header.addAll(Arrays.asList(METRICS));
            DcFiles.row(rows, header.toArray());
            Object[] descriptionHeader = {"setting_id", "baseline", "metric", "quality_mode", "run_count", "failure_run_count", "finite_run_count",
                "nonfinite_run_count", "missing_run_count", "all_run_mean", "all_run_sample_sd", "all_run_min",
                "all_run_max", "finite_only_mean", "finite_only_sample_sd", "inference"};
            DcFiles.row(summary, descriptionHeader); DcFiles.row(paired, descriptionHeader);
            DcFiles.row(pairs, "setting_id", "baseline", "run_id", "metric", "quality_mode", "setting_value", "baseline_value",
                    "setting_minus_baseline", "status");
            for (DcConfiguration.Setting setting : config.settings) {
                for (int run = 0; run < config.runs; run++) {
                    Properties result = completed.get(key(setting, run));
                    List<Object> row = new ArrayList<>(Arrays.asList(setting.id(), setting.d, setting.c, run,
                            result.getProperty("native-grid-side"), result.getProperty("native-grid-cells"),
                            result.getProperty("common-grid-side"), result.getProperty("common-grid-cells"),
                            qualityMode(config), result.getProperty("representative-id"),
                            result.getProperty("representative-status"), result.getProperty("quality-status"),
                            result.getProperty("test-evaluation-count"), result.getProperty("quality-evaluation-count"),
                            result.getProperty("quality-failed-pairs")));
                    for (String metric : METRICS) { row.add(result.getProperty(metric)); }
                    DcFiles.row(rows, row.toArray());
                }
                for (String metric : METRICS) {
                    List<Double> values = new ArrayList<>(), differences = new ArrayList<>();
                    String failureKey = metric.equals("representative_mean_test_objective") ? "test_failure_count"
                            : metric.equals("quality_mean_validation_objective") ? "quality_failure_count" : null;
                    int failureRuns = 0, pairedFailureRuns = 0;
                    for (int run = 0; run < config.runs; run++) {
                        Properties current = completed.get(key(setting, run));
                        Properties base = completed.get(BASELINE + ".run." + run);
                        double actual = value(current, metric);
                        double baseline = value(base, metric);
                        if (failureKey != null) {
                            boolean failed = count(current, failureKey) > 0;
                            boolean baselineFailed = count(base, failureKey) > 0;
                            if (failed) { failureRuns++; }
                            if (failed || baselineFailed) { pairedFailureRuns++; }
                        }
                        values.add(actual);
                        double difference = Double.isFinite(actual) && Double.isFinite(baseline) ? actual - baseline : Double.NaN;
                        differences.add(difference);
                        if (!setting.id().equals(BASELINE)) {
                            DcFiles.row(pairs, setting.id(), BASELINE, run, metric, qualityMode(config), numeric(actual), numeric(baseline),
                                    numeric(difference), Double.isFinite(difference) ? "FINITE_PAIRED_DIFFERENCE" : "NONFINITE_OUTCOME_NO_DIFFERENCE");
                        }
                    }
                    describe(summary, setting.id(), "NONE", metric, qualityMode(config), values,
                            failureKey == null ? null : failureRuns);
                    if (!setting.id().equals(BASELINE)) {
                        describe(paired, setting.id(), BASELINE, metric, qualityMode(config), differences,
                                failureKey == null ? null : pairedFailureRuns);
                    }
                }
            }
        }
        Properties guide = new Properties();
        guide.setProperty("protocol", PROTOCOL);
        guide.setProperty("representative", "Best finite RAW FIXED TRAINING fitness, then archive ordinal."
                + " All held-out instances use this same representative. Never test-based reselection.");
        guide.setProperty("quality", qualityMode(config) + ": "
                + (config.qualitySample == 0 ? "all unique STORED_FINITE expression pairs"
                : "uniform without-replacement sample of at most " + config.qualitySample + " unique STORED_FINITE expression pairs")
                + " per library."
                + " Every sampled pair uses the same validation instances. Failures stay Infinity; no replacement."
                + " This is NOT full-archive quality. Validation outcomes do not influence representative or projection.");
        guide.setProperty("quality-finite-conditional", "Sample draw occurs before validation. quality_failed_rule_count"
                + " counts sampled rules with at least one failed validation instance; quality_failure_count counts"
                + " failed instances. The primary quality_mean_validation_objective includes all drawn rules and is"
                + " Infinity after any failure. quality_finite_conditional_rule_mean/median describe only the"
                + " quality_finite_rule_count rules whose complete validation mean is finite. These are conditional"
                + " descriptions, not an unconditional sample estimator or full-archive quality."
                + " quality_finite_instance_count also includes finite instances from rules that failed other instances.");
        guide.setProperty("common-projection", "Independent common d4,c3 frozen bank; all retained memberships including"
                + " stored failures are attempted. Candidate-specific projection failures remain explicit rows."
                + " Common cell occupancy includes all successful projections, with no elite reselection and no training feedback."
                + " Native coverage/counts labelled all_retained include stored failures."
                + " common_coverage_stored_finite is a separately labelled source-fitness-restricted description,"
                + " never a replacement for common_coverage_all_retained. Validation outcomes do not affect either."
                + " Native coverage has setting-specific denominators; common coverage always uses the same 81x81 geometry.");
        guide.setProperty("resources", "Training counters/timing/peak observed used heap are copied from verified"
                + " training manifests. Observed used heap is not peak process RSS. Completed-case evaluation"
                + " attempts and common-bank reference replays are counted separately from training."
                + " Interrupted attempt totals may be unknown and are never silently reported as zero.");
        guide.setProperty("resume", "Only COMPLETE case manifests with verified artifacts are reused. Incomplete/failed"
                + " attempts are preserved, never overwritten. Pending-case resume replays the common bank once and"
                + " requires its export hash to equal the original frozen bank; replay overhead is recorded separately."
                + " A fully COMPLETE resume performs no simulations or writes.");
        guide.setProperty("inference", "DESCRIPTIVE ONLY. Independent units are runs; default n=5."
                + " Report mean/sample SD/min/max and paired descriptive differences vs d4-c3 by run ID."
                + " No p-values or significance/equivalence claims. With n=5 nonzero pairs, the smallest exact"
                + " two-sided Wilcoxon p is 0.0625, not below 0.05.");
        guide.setProperty("nonfinite", "All run blocks retained. Any missing/nonfinite value disables sample SD;"
                + " any missing value gives NA all-run mean; Infinity is preserved otherwise. Finite-only descriptions"
                + " are explicitly labelled, never an inferential subset. Infinity-minus-Infinity has NA difference.");
        guide.setProperty("failure_run_count", "For failure-inclusive test and quality objective summaries, count"
                + " runs with at least one failed evaluation; paired summaries count run pairs where either side failed."
                + " Other metric rows have NA in this column. Failed runs remain in run_count and all-run mean/SD."
                + " Missing representatives/samples are separately represented by missing_run_count, not silently dropped.");
        try (BufferedWriter writer = DcFiles.create(directory.resolve("results_guide.properties"))) {
            for (String property : new java.util.TreeSet<>(guide.stringPropertyNames())) {
                writer.write(property + "=" + guide.getProperty(property) + "\n");
            }
        }
        Properties report = new Properties();
        report.setProperty("status", "COMPLETE"); report.setProperty("protocol", PROTOCOL);
        report.setProperty("study-hash", manifest.getProperty("study-hash"));
        for (String file : new String[]{"run_results.csv", "summary.csv", "paired_differences.csv",
                "paired_summary.csv", "results_guide.properties"}) { artifact(report, directory, file); }
        inputs.verify();
        DcFiles.write(directory.resolve("manifest.properties"), report, false);
        manifest.setProperty("report.path", directory.getFileName().toString());
        manifest.setProperty("report.manifest-sha256", DcFiles.hash(directory.resolve("manifest.properties")));
        // Root aggregate files are published once; later resumes can only verify identical bytes.
        for (String file : new String[]{"run_results.csv", "summary.csv", "paired_differences.csv",
                "paired_summary.csv", "results_guide.properties"}) {
            Path target = root.resolve(file);
            if (Files.exists(target)) { DcFiles.checkHash(target, DcFiles.hash(directory.resolve(file))); }
            else { Files.copy(directory.resolve(file), target); }
            artifact(manifest, root, file);
        }
        long referenceCalls = 0, referenceAttempts = 0, unknownReferenceAttempts = 0;
        try (java.nio.file.DirectoryStream<Path> attempts = Files.newDirectoryStream(root, "common-attempt-*")) {
            for (Path bank : attempts) {
                if (!bank.getFileName().toString().matches("common-attempt-[0-9]{4,}")
                        || !Files.isDirectory(bank) || !bank.toRealPath().startsWith(root.toRealPath())) {
                    throw new IOException("Unexpected common-bank attempt mapping");
                }
                referenceAttempts++;
                Path saved = bank.resolve("manifest.properties");
                Properties status = Files.isRegularFile(saved) ? DcFiles.read(saved) : new Properties();
                if ("RECORDED".equals(status.getProperty("simulation-count-status"))) {
                    referenceCalls = Math.addExact(referenceCalls, count(status, "reference-simulations"));
                } else { unknownReferenceAttempts++; }
            }
        }
        manifest.setProperty("recorded-common-reference-simulations", Long.toString(referenceCalls));
        manifest.setProperty("common-reference-attempt-count", Long.toString(referenceAttempts));
        manifest.setProperty("common-reference-unknown-attempts", Long.toString(unknownReferenceAttempts));
        long testCount = 0, validationCount = 0, testFailures = 0, validationFailures = 0, projectionFailures = 0;
        long testFailedRuns = 0, qualityFailedRuns = 0, failedQualityRules = 0;
        for (Properties result : completed.values()) {
            testCount = Math.addExact(testCount, count(result, "test-evaluation-count"));
            validationCount = Math.addExact(validationCount, count(result, "quality-evaluation-count"));
            testFailures = Math.addExact(testFailures, count(result, "test_failure_count"));
            validationFailures = Math.addExact(validationFailures, count(result, "quality_failure_count"));
            projectionFailures = Math.addExact(projectionFailures, count(result, "common_projection_failures"));
            if (count(result, "test_failure_count") > 0) { testFailedRuns++; }
            if (count(result, "quality_failure_count") > 0) { qualityFailedRuns++; }
            failedQualityRules = Math.addExact(failedQualityRules, count(result, "quality_failed_rule_count"));
        }
        manifest.setProperty("completed-case-test-evaluation-count", Long.toString(testCount));
        manifest.setProperty("completed-case-quality-evaluation-count", Long.toString(validationCount));
        manifest.setProperty("completed-case-test-failure-count", Long.toString(testFailures));
        manifest.setProperty("completed-case-quality-failure-count", Long.toString(validationFailures));
        manifest.setProperty("completed-case-common-projection-failure-count", Long.toString(projectionFailures));
        manifest.setProperty("completed-case-test-failure-run-count", Long.toString(testFailedRuns));
        manifest.setProperty("completed-case-quality-failure-run-count", Long.toString(qualityFailedRuns));
        manifest.setProperty("completed-case-quality-failed-rule-count", Long.toString(failedQualityRules));
        manifest.setProperty("recorded-common-reference-simulation-scope",
                "ALL_ATTEMPTS_WITH_RECORDED_COUNTS; UNKNOWN_INTERRUPTED_ATTEMPTS_SEPARATE");
        manifest.setProperty("interrupted-attempt-simulation-counts", "UNKNOWN_IF_INTERRUPTED; inspect preserved attempt manifests");
    }

    private static void describe(BufferedWriter writer, String setting, String baseline, String metric, String qualityMode,
                                 List<Double> values, Integer failureRuns) throws IOException {
        List<Double> finite = new ArrayList<>();
        int missing = 0, nonfinite = 0;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            if (Double.isNaN(value)) { missing++; }
            else {
                min = Math.min(min, value); max = Math.max(max, value);
                if (Double.isFinite(value)) { finite.add(value); } else { nonfinite++; }
            }
        }
        DcFiles.row(writer, setting, baseline, metric, qualityMode, values.size(), failureRuns == null ? "NA" : failureRuns,
                finite.size(), nonfinite, missing,
                numeric(mean(values)), numeric(missing + nonfinite == 0 ? sd(values) : Double.NaN),
                numeric(missing == 0 ? min : Double.NaN), numeric(missing == 0 ? max : Double.NaN),
                numeric(mean(finite)), numeric(sd(finite)), "DESCRIPTIVE_ONLY_NO_P_VALUES");
    }

    static double mean(List<Double> values) {
        if (values.isEmpty() || values.stream().anyMatch(v -> Double.isNaN(v))) { return Double.NaN; }
        if (values.stream().anyMatch(v -> !Double.isFinite(v))) {
            boolean positive = values.stream().anyMatch(v -> v == Double.POSITIVE_INFINITY);
            boolean negative = values.stream().anyMatch(v -> v == Double.NEGATIVE_INFINITY);
            return positive && negative ? Double.NaN : positive ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        double scale = 0, sum = 0;
        for (double v : values) { scale = Math.max(scale, Math.abs(v)); }
        if (scale == 0) { return 0; }
        for (double v : values) { sum += v / scale / values.size(); }
        return Math.max(-1, Math.min(1, sum)) * scale;
    }

    static double sd(List<Double> values) {
        if (values.size() < 2 || values.stream().anyMatch(v -> !Double.isFinite(v))) { return Double.NaN; }
        double scale = 0;
        for (double v : values) { scale = Math.max(scale, Math.abs(v)); }
        if (scale == 0) { return 0; }
        double centre = mean(values) / scale, sum = 0;
        for (double v : values) { double delta = v / scale - centre; sum += delta * delta; }
        return scale * Math.sqrt(sum / (values.size() - 1));
    }

    private static double finiteMean(List<Double> values) {
        List<Double> finite = new ArrayList<>();
        for (double value : values) { if (Double.isFinite(value)) { finite.add(value); } }
        return mean(finite);
    }
    static double finiteMedian(List<Double> values) {
        List<Double> finite = new ArrayList<>();
        for (double value : values) { if (Double.isFinite(value)) { finite.add(value); } }
        if (finite.isEmpty()) { return Double.NaN; }
        Collections.sort(finite);
        int middle = finite.size() / 2;
        return finite.size() % 2 == 1 ? finite.get(middle) : finite.get(middle - 1) * 0.5 + finite.get(middle) * 0.5;
    }
    private static String qualityMode(DcConfiguration c) {
        return c.qualitySample == 0 ? "EXHAUSTIVE_STORED_FINITE_UNIQUE_ONLY" : "SAMPLE_ESTIMATE_NOT_FULL_ARCHIVE_QUALITY";
    }
    private static String digits(double[] digits) {
        List<String> parts = new ArrayList<>();
        for (double digit : digits) { parts.add(Integer.toString((int) digit)); }
        return String.join(";", parts);
    }
    private static String key(DcConfiguration.Setting setting, int run) { return setting.id() + ".run." + run; }
    private static String numeric(double value) { return Double.isNaN(value) ? "NA" : Double.toString(value); }
    private static void put(Properties result, String key, double value) { result.setProperty(key, numeric(value)); }
    private static void put(Properties result, String key, long value) { result.setProperty(key, Long.toString(value)); }
    private static double value(Properties values, String key) {
        String text = required(values, key);
        return text.equals("NA") ? Double.NaN : Double.parseDouble(text);
    }
    private static double number(Properties p, String key) { return Double.parseDouble(required(p, key)); }
    private static double finite(Properties p, String key) {
        double value = number(p, key);
        if (!Double.isFinite(value) || value < 0) { throw new IllegalArgumentException("Expected finite nonnegative " + key); }
        return value;
    }
    private static long count(Properties p, String key) {
        String value = required(p, key);
        if (!value.matches("[0-9]+")) { throw new IllegalArgumentException("Expected nonnegative integer " + key); }
        return Long.parseLong(value);
    }
    private static void integer(Properties p, String key, long expected) throws IOException {
        if (count(p, key) != expected) { throw new IOException("Expected " + key + "=" + expected); }
    }
    private static String required(Properties p, String key) {
        return nonempty(p.getProperty(key), "property " + key);
    }
    private static String nonempty(String value, String label) {
        if (value == null || value.trim().isEmpty()) { throw new IllegalArgumentException("Missing " + label); }
        return value;
    }
    private static void equal(Properties p, String key, String expected) throws IOException {
        if (!expected.equals(required(p, key))) { throw new IOException("Expected " + key + "=" + expected); }
    }
    private static void artifact(Properties p, Path directory, String file) throws IOException {
        p.setProperty("data." + file + ".sha256", DcFiles.hash(directory.resolve(file)));
    }
    private static void requireArtifacts(Properties p, String... names) throws IOException {
        for (String name : names) {
            if (!required(p, "data." + name + ".sha256").matches("[a-f0-9]{64}")) { throw new IOException("Invalid artifact hash: " + name); }
        }
    }
    private static Path fresh(Path root, String prefix) throws IOException {
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            Path next = root.resolve(prefix + String.format(java.util.Locale.ROOT, "%04d", i));
            if (!Files.exists(next, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { return Files.createDirectory(next); }
        }
        throw new IOException("Attempt namespace exhausted");
    }
    static List<List<String>> csv(Path path, String header) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader source = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String actual = source.readLine();
            if (!header.equals(actual)) { throw new IOException("CSV header mismatch: " + path); }
            PushbackReader reader = new PushbackReader(source, 1);
            while (true) {
                List<String> cells = new ArrayList<>();
                StringBuilder cell = new StringBuilder();
                boolean quoted = false, closed = false, any = false, ended = false;
                int c;
                while ((c = reader.read()) != -1) {
                    any = true;
                    if (quoted) {
                        if (c == '"') {
                            int next = reader.read();
                            if (next == '"') { cell.append('"'); }
                            else {
                                quoted = false; closed = true;
                                if (next != -1) { reader.unread(next); }
                            }
                        } else { cell.append((char) c); }
                    } else if (c == ',') { cells.add(cell.toString()); cell.setLength(0); closed = false; }
                    else if (c == '\n' || c == '\r') {
                        if (c == '\r') {
                            int next = reader.read();
                            if (next != '\n' && next != -1) { reader.unread(next); }
                        }
                        ended = true; break;
                    }
                    else if (closed) { throw new IOException("Characters after CSV quote: " + path); }
                    else if (c == '"') {
                        if (cell.length() != 0) { throw new IOException("Quote inside CSV value: " + path); }
                        quoted = true;
                    } else { cell.append((char) c); }
                }
                if (quoted) { throw new IOException("Unclosed CSV quote: " + path); }
                if (!any) { return rows; }
                cells.add(cell.toString()); rows.add(cells);
                if (!ended) { return rows; }
            }
        }
    }
}
