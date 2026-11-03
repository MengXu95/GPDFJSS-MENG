package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyHeuristicSelector;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyInferenceSystem;
import mengxu.algorithm.MAPElites.FuzzySystem.Heuristic;
import mengxu.algorithm.MAPElites.FuzzySystem.UserPreferenceVector;

public final class ExperimentAStudy {
    public static final String PROTOCOL = "experiment-a-v2-importance";
    static final String RESULTS_HEADER = "run_id,case_id,method,selected_count,mean_validation_objective,"
            + "mean_test_objective,mean_complexity,mean_sensitivity,test_failure_count,test_evaluation_count";
    private static final List<String> ARTIFACTS = Arrays.asList("profiles.csv", "probes.csv", "archive_members.csv",
            "candidate_metrics.csv", "selections.csv", "test_instances.csv", "run_results.csv");

    interface EvaluationService {
        SchedulingEvaluator.Evaluation evaluate(ArchiveCandidate candidate, long seed, int instances);
        long count();
    }

    private ExperimentAStudy() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("ExperimentAStudy --config <properties> [--resume] [--dry-run] [revision options]");
            System.out.println("Both regimes use common-validation top-pool selection; test failures are recorded without reselection.");
            return;
        }
        List<String> options = new ArrayList<>();
        boolean resume = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--resume")) {
                if (resume) { throw new IllegalArgumentException("Duplicate --resume"); }
                resume = true;
            } else {
                String key = args[i];
                options.add(key);
                if (!key.equals("--dry-run")) {
                    if (++i >= args.length) { throw new IllegalArgumentException("Missing option value: " + key); }
                    options.add(args[i]);
                }
            }
        }
        run(RevisionConfiguration.forStudy(options.toArray(new String[0])), resume, ExperimentAStudy::physicalEvaluator);
    }

    static final class PhysicalEvaluation implements EvaluationService {
        private final SchedulingEvaluator evaluator;
        PhysicalEvaluation(RevisionConfiguration config) {
            evaluator = new SchedulingEvaluator(config.machines, config.jobs, config.warmup,
                    config.utilisation, config.dueFactor, config.objective);
        }
        @Override public SchedulingEvaluator.Evaluation evaluate(ArchiveCandidate candidate, long seed, int count) {
            return evaluator.evaluateRecordingFailures(candidate, seed, count);
        }
        @Override public long count() { return evaluator.simulationCount(); }
    }
    private static EvaluationService physicalEvaluator(RevisionConfiguration config) { return new PhysicalEvaluation(config); }

    static void run(RevisionConfiguration config, boolean resume,
                    Function<RevisionConfiguration, EvaluationService> factory) throws IOException {
        if (!config.importanceProfiles || config.legacyNormalisation) {
            throw new IllegalArgumentException("Main experiment A requires importance-v1 and all-minmax; old target matching is diagnostic only");
        }
        if (!config.method.equals("NfixIns") && !config.method.equals("NrotateInsNoReeva")) {
            throw new IllegalArgumentException("Declare another library's training regime before adding it to experiment A");
        }
        Map<Integer, FinalArchiveReader.Archive> archives = RevisionExperiment.load(config);
        long unique = 0;
        for (FinalArchiveReader.Archive archive : archives.values()) {
            long count = archive.candidates().stream().filter(ArchiveCandidate::hasFiniteStoredFitness)
                    .map(ArchiveCandidate::treeHash).distinct().count();
            if (count < config.poolSize) { throw new IllegalArgumentException("Insufficient unique stored-finite candidates"); }
            unique += count;
        }
        if (config.dryRun) {
            System.out.println("EXPERIMENT A PREFLIGHT PASSED: " + archives.size() + " archives; " + unique
                    + " unique stored-finite rule pairs; nominal validation calls=" + unique * config.validationInstances
                    + ". Numeric eligibility is checked during evaluation. No simulations or outputs created.");
            return;
        }
        String fingerprint = ExperimentAFiles.fingerprint(config, archives);
        Path root = config.outputRoot;
        Properties manifest;
        if (Files.exists(root)) {
            if (!resume) { throw new IllegalArgumentException("Existing study output requires --resume: " + root); }
            manifest = ExperimentAFiles.read(root.resolve("manifest.properties"));
            if (!PROTOCOL.equals(manifest.getProperty("protocol"))
                    || !fingerprint.equals(manifest.getProperty("study-hash"))) {
                throw new IOException("Study/configuration/implementation/input fingerprint mismatch; use a new output directory");
            }
            for (Map.Entry<String, String> option : config.options().entrySet()) {
                if (!option.getKey().equals("output-root") && !option.getKey().equals("dry-run")
                        && !option.getValue().equals(manifest.getProperty(option.getKey()))) {
                    throw new IOException("Changed study metadata: " + option.getKey());
                }
            }
        } else {
            Files.createDirectories(root.getParent());
            Files.createDirectory(root);
            manifest = new Properties();
            manifest.putAll(config.options());
            manifest.setProperty("protocol", PROTOCOL);
            manifest.setProperty("study-hash", fingerprint);
            manifest.setProperty("run-ids", config.runs.stream().map(String::valueOf).collect(Collectors.joining(",")));
            manifest.setProperty("num-runs", Integer.toString(config.runs.size()));
            manifest.setProperty("candidate-pool-policy", "unique stored-finite rules; common-validation top-k after numeric metric eligibility");
            manifest.setProperty("pool-order", "mean validation objective then original archive ordinal");
            manifest.setProperty("validation-failure-policy", "exclude and report; pool size must still be satisfied");
            manifest.setProperty("test-failure-policy", "positive infinity and explicit failed counts; no reselection or omitted runs");
            manifest.setProperty("normalization", "poolwise reverse-minmax P; minmax 1/(1+C),1/(1+S); constants=0.5");
            manifest.setProperty("historical-training-seeds", "AUTHOR MUST VERIFY separation from probe, validation and test seeds");
            manifest.setProperty("fuzzy-selector", "FUZZY_IMPORTANCE");
            manifest.setProperty("selectors", "FUZZY_IMPORTANCE,MANUAL_WS,RANDOM");
            manifest.setProperty("primary-baseline", "MANUAL_WS");
            manifest.setProperty("importance-semantics", "all three directions beneficial; weights express relative priority, not desired quality regions");
            manifest.setProperty("fuzzy-utility", "normalized Gaussian memberships times ordered grades 0,0.5,1; centers 0.2,0.5,0.8; shared sigma 0.15");
            manifest.setProperty("si-role", "fixed legacy diagnostic internal only; omitted from personalized decision exports");
            manifest.setProperty("cost-scope", "completed run attempts plus shared probes; incomplete attempts retained separately");
            manifest.setProperty("status", "RUNNING");
            ExperimentAFiles.write(root.resolve("manifest.properties"), manifest);
        }
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(root.resolve(".study.lock"),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
            java.nio.channels.FileLock acquired;
            try { acquired = channel.tryLock(); }
            catch (java.nio.channels.OverlappingFileLockException active) {
                throw new IllegalStateException("Study is already running: " + root, active);
            }
            if (acquired == null) { throw new IllegalStateException("Study is already running: " + root); }
            try (java.nio.channels.FileLock held = acquired) {
                executeStudy(config, archives, root, manifest, fingerprint, factory);
            }
        }
    }

    private static void executeStudy(RevisionConfiguration config,
                                     Map<Integer, FinalArchiveReader.Archive> archives, Path root,
                                     Properties manifest, String fingerprint,
                                     Function<RevisionConfiguration, EvaluationService> factory) throws IOException {
        try {
            Map<String, UserPreferenceVector> profiles = UserPreferenceVector.importanceCases();
            SensitivityProbeSet probes = sharedInputs(config, root, manifest, profiles);
            boolean testFailures = false;
            List<Path> completed = new ArrayList<>();
            for (Map.Entry<Integer, FinalArchiveReader.Archive> item : archives.entrySet()) {
                int run = item.getKey();
                Path previous = mappedRun(root, manifest.getProperty("run." + run + ".path"), run);
                if (previous != null && Files.isRegularFile(previous.resolve("manifest.properties"))) {
                    Properties saved = ExperimentAFiles.read(previous.resolve("manifest.properties"));
                    if (!fingerprint.equals(saved.getProperty("study-hash"))
                            || !item.getValue().sourceHash().equals(saved.getProperty("archive-sha256"))) {
                        throw new IOException("Run fingerprint mismatch: " + previous);
                    }
                    if (ExperimentAFiles.complete(saved)) {
                        verifyRun(previous, saved);
                        completed.add(previous);
                        testFailures |= saved.getProperty("status").equals("COMPLETE_WITH_TEST_FAILURES");
                        System.out.println("Verified completed run " + run + "; no simulations repeated.");
                        continue;
                    }
                }
                Path directory = root.resolve("run-" + run);
                int attempt = 2;
                while (Files.exists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    directory = root.resolve("run-" + run + "-attempt-" + attempt++);
                }
                manifest.setProperty("run." + run + ".path", directory.getFileName().toString());
                manifest.setProperty("status", "RUNNING");
                ExperimentAFiles.write(root.resolve("manifest.properties"), manifest);
                Files.createDirectory(directory);
                Properties saved = executeRun(config, run, item.getValue(), directory, root,
                        fingerprint, profiles, probes, factory.apply(config));
                completed.add(directory);
                testFailures |= saved.getProperty("status").equals("COMPLETE_WITH_TEST_FAILURES");
            }
            List<String> aggregate = new ArrayList<>();
            aggregate.add(RESULTS_HEADER);
            long simulationCount = 2;
            for (Path directory : completed) {
                List<String> lines = Files.readAllLines(directory.resolve("run_results.csv"), StandardCharsets.UTF_8);
                if (lines.isEmpty() || !lines.get(0).equals(RESULTS_HEADER)) { throw new IOException("Incompatible result schema"); }
                aggregate.addAll(lines.subList(1, lines.size()));
                simulationCount += Long.parseLong(ExperimentAFiles.read(directory.resolve("manifest.properties"))
                        .getProperty("simulation-count"));
            }
            Path results = root.resolve("run_results.csv");
            if (Files.exists(results)) {
                if (!Files.readAllLines(results, StandardCharsets.UTF_8).equals(aggregate)) {
                    throw new IOException("Existing aggregate does not match verified run artifacts");
                }
            } else {
                Files.write(results, aggregate, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            for (FinalArchiveReader.Archive archive : archives.values()) {
                ExperimentAFiles.checkHash(archive.candidates().get(0).source(), archive.sourceHash());
            }
            manifest.setProperty("results-sha256", RevisionCsv.fileHash(results));
            manifest.setProperty("completed-attempt-simulation-count", Long.toString(simulationCount));
            manifest.remove("failure");
            manifest.setProperty("status", testFailures ? "COMPLETE_WITH_TEST_FAILURES" : "COMPLETE");
            ExperimentAFiles.write(root.resolve("manifest.properties"), manifest);
            System.out.println("EXPERIMENT A FINISHED: " + config.method + ", runs=" + completed.size()
                    + ", status=" + manifest.getProperty("status"));
        } catch (IOException | RuntimeException failure) {
            manifest.setProperty("status", "FAILED");
            manifest.setProperty("failure", failure.toString());
            try { ExperimentAFiles.write(root.resolve("manifest.properties"), manifest); }
            catch (IOException reportFailure) { failure.addSuppressed(reportFailure); }
            throw failure;
        }
    }

    private static Path mappedRun(Path root, String name, int run) throws IOException {
        if (name == null) { return null; }
        if (!name.matches("run-" + run + "(?:-attempt-(?:[2-9]|[1-9][0-9]+))?")) {
            throw new IOException("Invalid run directory mapping");
        }
        Path path = root.resolve(name);
        if (Files.exists(path) && !path.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("Run directory escapes study root");
        }
        return path;
    }

    private static SensitivityProbeSet sharedInputs(RevisionConfiguration config, Path root, Properties manifest,
                                                     Map<String, UserPreferenceVector> profiles) throws IOException {
        if (!Files.exists(root.resolve("profiles.csv"))) {
            if (manifest.containsKey("profiles-sha256")) { throw new IOException("Frozen profiles are missing"); }
            RevisionExperiment.exportProfiles(root, profiles, true);
            manifest.setProperty("profiles-sha256", RevisionCsv.fileHash(root.resolve("profiles.csv")));
            ExperimentAFiles.write(root.resolve("manifest.properties"), manifest);
        }
        ExperimentAFiles.checkHash(root.resolve("profiles.csv"), manifest.getProperty("profiles-sha256"));
        if (!Files.exists(root.resolve("probes.csv"))) {
            if (manifest.containsKey("probes-sha256")) { throw new IOException("Frozen probes are missing"); }
            long start = System.nanoTime();
            SensitivityProbeSet probes = SensitivityProbeSet.collect(config.probeSeed, config.probeSamplingSeed,
                    config.machines, config.probeJobs, config.probeWarmup, config.utilisation, config.dueFactor,
                    config.probeCandidates, config.probeCount);
            RevisionExperiment.exportProbes(root, probes);
            manifest.setProperty("probes-sha256", RevisionCsv.fileHash(root.resolve("probes.csv")));
            manifest.setProperty("probe-collection-seconds", Double.toString((System.nanoTime() - start) / 1e9));
            manifest.setProperty("probe-simulation-count", "2");
            ExperimentAFiles.write(root.resolve("manifest.properties"), manifest);
        }
        ExperimentAFiles.checkHash(root.resolve("probes.csv"), manifest.getProperty("probes-sha256"));
        SensitivityProbeSet probes = ExperimentAFiles.readProbes(root.resolve("probes.csv"));
        int expected = Math.multiplyExact(config.probeCount, config.probeCandidates);
        if (probes.sequencing().size() != expected || probes.routing().size() != expected) {
            throw new IOException("Frozen probe count does not match protocol");
        }
        return probes;
    }

    private static Properties executeRun(RevisionConfiguration config, int run, FinalArchiveReader.Archive archive,
                                          Path directory, Path root, String fingerprint,
                                          Map<String, UserPreferenceVector> profiles, SensitivityProbeSet probes,
                                          EvaluationService evaluator) throws IOException {
        Properties manifest = new Properties();
        manifest.setProperty("study-hash", fingerprint);
        manifest.setProperty("archive-sha256", archive.sourceHash());
        manifest.setProperty("run-id", Integer.toString(run));
        manifest.setProperty("status", "RUNNING");
        ExperimentAFiles.write(directory.resolve("manifest.properties"), manifest);
        long start = System.nanoTime();
        try {
            Files.copy(root.resolve("profiles.csv"), directory.resolve("profiles.csv"));
            Files.copy(root.resolve("probes.csv"), directory.resolve("probes.csv"));
            List<RevisionMetrics> pool = ExperimentAPool.prepare(directory, archive, config, probes, evaluator);
            long validationCalls = evaluator.count();
            boolean failedTests = selections(directory, config, run, pool, profiles, evaluator);
            ExperimentAFiles.checkHash(archive.candidates().get(0).source(), archive.sourceHash());
            manifest.setProperty("simulation-count", Long.toString(evaluator.count()));
            manifest.setProperty("validation-simulations", Long.toString(validationCalls));
            manifest.setProperty("test-simulations", Long.toString(evaluator.count() - validationCalls));
            manifest.setProperty("elapsed-seconds", Double.toString((System.nanoTime() - start) / 1e9));
            for (String name : ARTIFACTS) { manifest.setProperty("data." + name + ".sha256", RevisionCsv.fileHash(directory.resolve(name))); }
            manifest.setProperty("status", failedTests ? "COMPLETE_WITH_TEST_FAILURES" : "COMPLETE");
            ExperimentAFiles.write(directory.resolve("manifest.properties"), manifest);
            System.out.println("Completed experiment-A run " + run + ", pool=" + pool.size()
                    + ", validation calls=" + validationCalls + ", test calls=" + (evaluator.count() - validationCalls)
                    + ", status=" + manifest.getProperty("status"));
            return manifest;
        } catch (IOException | RuntimeException failure) {
            manifest.setProperty("status", "FAILED");
            manifest.setProperty("failure", failure.toString());
            manifest.setProperty("simulation-count", Long.toString(evaluator.count()));
            try { ExperimentAFiles.write(directory.resolve("manifest.properties"), manifest); }
            catch (IOException reportFailure) { failure.addSuppressed(reportFailure); }
            throw failure;
        }
    }

    private static void verifyRun(Path directory, Properties manifest) throws IOException {
        for (String name : ARTIFACTS) {
            ExperimentAFiles.checkHash(directory.resolve(name), manifest.getProperty("data." + name + ".sha256"));
        }
    }

    private static boolean selections(Path output, RevisionConfiguration config, int run,
                                       List<RevisionMetrics> pool, Map<String, UserPreferenceVector> profiles,
                                       EvaluationService evaluator) throws IOException {
        FuzzyInferenceSystem model = FuzzyModelFactory.createRevision();
        FuzzyHeuristicSelector selector = new FuzzyHeuristicSelector(model, FuzzyHeuristicSelector.NormalizationMode.MIN_MAX_ALL,
                FuzzyHeuristicSelector.PreferenceMode.MONOTONE_IMPORTANCE);
        selector.buildLibrary(pool.stream().map(RevisionMetrics::validationObjective).collect(Collectors.toList()),
                pool.stream().map(RevisionMetrics::complexity).collect(Collectors.toList()),
                pool.stream().map(RevisionMetrics::sensitivity).collect(Collectors.toList()));
        Map<String, SchedulingEvaluator.Evaluation> cache = new LinkedHashMap<>();
        try (RevisionCsv selections = new RevisionCsv(output.resolve("selections.csv"),
                    "run_id", "case_id", "method", "rank", "candidate_id", "score",
                    "validation_objective", "test_objective", "complexity", "sensitivity", "test_failure_count",
                    "normalized_performance", "normalized_interpretability", "normalized_stability",
                    "fuzzy_desirability_performance", "fuzzy_desirability_interpretability", "fuzzy_desirability_stability");
             RevisionCsv test = new RevisionCsv(output.resolve("test_instances.csv"),
                    "run_id", "candidate_id", "instance_index", "seed", "objective", "status", "reason");
             RevisionCsv results = new RevisionCsv(output.resolve("run_results.csv"), (Object[]) RESULTS_HEADER.split(","))) {
            for (Map.Entry<String, UserPreferenceVector> profile : profiles.entrySet()) {
                Map<String, List<Heuristic>> methods = new LinkedHashMap<>();
                methods.put("FUZZY_IMPORTANCE", selector.selectByPreference(profile.getValue(), config.topK));
                methods.put("MANUAL_WS", RevisionExperiment.manual(selector.getHeuristicLibrary(), profile.getValue(), model, config.topK, false));
                methods.put("RANDOM", selector.selectRandomly(config.topK,
                        new Random(config.selectionSeed ^ ((long) run << 32) ^ profile.getKey().hashCode())));
                for (Map.Entry<String, List<Heuristic>> method : methods.entrySet()) {
                    List<Double> objectives = new ArrayList<>();
                    double validation = 0, complexity = 0, sensitivity = 0;
                    long failures = 0;
                    int rank = 0;
                    for (Heuristic selected : method.getValue()) {
                        RevisionMetrics row = pool.get(selected.getId());
                        SchedulingEvaluator.Evaluation evaluated = cache.get(row.candidate().treeHash());
                        if (evaluated == null) {
                            evaluated = evaluator.evaluate(row.candidate(), config.testSeed, config.testInstances);
                            validateEvaluation(evaluated, config.testInstances);
                            cache.put(row.candidate().treeHash(), evaluated);
                            for (int i = 0; i < evaluated.values().size(); i++) {
                                String reason = evaluated.failures().get(i);
                                test.row(run, row.candidate().id(), i, Math.addExact(config.testSeed, i),
                                        evaluated.values().get(i), reason.isEmpty() ? "OK" : "NUMERIC_FAILURE", reason);
                            }
                        }
                        rank++;
                        Object score = method.getKey().equals("RANDOM") ? ""
                                : method.getKey().equals("FUZZY_IMPORTANCE")
                                ? selector.getPreferenceSimilarity(selected, profile.getValue())
                                : RevisionExperiment.manualScore(selected, profile.getValue(), model,
                                        method.getKey().equals("MANUAL_TARGET"));
                        Map<String, Double> normalized = selected.getNormalizedCriteria();
                        Map<String, Double> utilities = selector.getImportanceUtilities(selected);
                        selections.row(run, profile.getKey(), method.getKey(), rank, row.candidate().id(), score,
                                row.validationObjective(), evaluated.mean(), row.complexity(), row.sensitivity(),
                                evaluated.failedCount(), normalized.get("Performance"), normalized.get("Interpretability"),
                                normalized.get("Stability"), utilities.get("Performance"), utilities.get("Interpretability"),
                                utilities.get("Stability"));
                        objectives.add(evaluated.mean());
                        validation += (row.validationObjective() - validation) / rank;
                        complexity += (row.complexity() - complexity) / rank;
                        sensitivity += (row.sensitivity() - sensitivity) / rank;
                        failures += evaluated.failedCount();
                    }
                    if (rank != config.topK) { throw new IllegalStateException("Selector did not return the declared top-k"); }
                    results.row(run, profile.getKey(), method.getKey(), rank, validation, testMean(objectives),
                            complexity, sensitivity, failures, Math.multiplyExact(config.topK, config.testInstances));
                }
            }
        }
        return cache.values().stream().anyMatch(value -> value.failedCount() > 0);
    }

    static double testMean(List<Double> values) {
        if (values.stream().anyMatch(value -> value == Double.POSITIVE_INFINITY)) { return Double.POSITIVE_INFINITY; }
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow(() -> new IllegalArgumentException("No selected rules"));
    }

    static void validateEvaluation(SchedulingEvaluator.Evaluation value, int instances) {
        if (value == null || value.values().size() != instances || value.failures().size() != instances) {
            throw new IllegalStateException("Incomplete scheduling-evaluation result");
        }
        for (int i = 0; i < instances; i++) {
            double objective = value.values().get(i);
            boolean failed = !value.failures().get(i).isEmpty();
            if ((failed && objective != Double.POSITIVE_INFINITY)
                    || (!failed && (!Double.isFinite(objective) || objective < 0))) {
                throw new IllegalStateException("Failure status and objective disagree");
            }
        }
        double expected = testMean(value.values());
        if (Double.compare(expected, value.mean()) != 0
                && (!Double.isFinite(expected) || !Double.isFinite(value.mean())
                || Math.abs(expected - value.mean()) > 1e-10 * Math.max(1.0, Math.abs(expected)))) {
            throw new IllegalStateException("Scheduling mean disagrees with instance-level outcomes");
        }
    }
}
