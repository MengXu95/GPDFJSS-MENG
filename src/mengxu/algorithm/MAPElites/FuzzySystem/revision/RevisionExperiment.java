package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyHeuristicSelector;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyInferenceSystem;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyMembershipFunction;
import mengxu.algorithm.MAPElites.FuzzySystem.Heuristic;
import mengxu.algorithm.MAPElites.FuzzySystem.UserPreferenceVector;

public final class RevisionExperiment {
    public static final String PROTOCOL = "paper-v2";

    private RevisionExperiment() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("RevisionExperiment --input-root <read-only NewResults3> "
                    + "--output-root <new directory> [--config <properties>] "
                    + "[--method NfixIns] [--scenario mean-tardiness-0.85-1.5] "
                    + "[--runs 0:29] [--pool-size 500] [--top-k 3] [--dry-run]");
            System.out.println("New priority-output sensitivity; explicit legacy profile/normalisation "
                    + "options do not reproduce the old phenotype-distance metric.");
            return;
        }
        run(RevisionConfiguration.fromArgs(args));
    }

    public static void run(RevisionConfiguration configuration) throws IOException {
        Map<Integer, FinalArchiveReader.Archive> archives = load(configuration);
        if (configuration.dryRun) {
            System.out.println("DRY RUN PASSED: no simulation or output files were created.");
            return;
        }
        Files.createDirectories(configuration.outputRoot.getParent());
        Files.createDirectory(configuration.outputRoot);
        Properties manifest = new Properties();
        manifest.putAll(configuration.options());
        manifest.setProperty("protocol", PROTOCOL);
        manifest.setProperty("resolved-input-root", configuration.inputRoot.toString());
        manifest.setProperty("resolved-archive-directory", configuration.archiveDirectory.toString());
        manifest.setProperty("model-input-centres", "0.2,0.5,0.8; sigma=0.15");
        manifest.setProperty("model-si-range", configuration.legacyNormalisation ? "0:200" : "0:100");
        manifest.setProperty("sensitivity-version", OutputSensitivityCalculator.VERSION);
        manifest.setProperty("complexity-version", "weighted-nodes-case-correct-v2");
        manifest.setProperty("selection", "FUZZY_PROFILE_MATCH,MANUAL_WS,MANUAL_TARGET,RANDOM");
        manifest.setProperty("si-role", "diagnostic; not used by FUZZY_PROFILE_MATCH");
        manifest.setProperty("fitness-pool-order", "finite stored fitness, then original archive ordinal");
        manifest.setProperty("performance-for-selection", "validation objective; not stored training fitness");
        manifest.setProperty("validation-seeds", seedDescription(configuration.validationSeed,
                configuration.validationInstances));
        manifest.setProperty("test-seeds", seedDescription(configuration.testSeed, configuration.testInstances));
        manifest.setProperty("historical-training-seed-provenance", "must be verified by the author");
        manifest.setProperty("sensitivity-units", "raw priority per absolute terminal unit");
        manifest.setProperty("sensitivity-aggregation", "mean over probe/unique-terminal pairs; mean of two roles");
        manifest.setProperty("metrics-scope", configuration.allMetrics ? "all finite archive members" : "candidate pool only");
        manifest.setProperty("status", "RUNNING");
        for (Map.Entry<Integer, FinalArchiveReader.Archive> entry : archives.entrySet()) {
            manifest.setProperty("archive." + entry.getKey() + ".path",
                    entry.getValue().candidates().get(0).source().toString());
            manifest.setProperty("archive." + entry.getKey() + ".sha256",
                    entry.getValue().sourceHash());
        }
        writeManifest(configuration.outputRoot, manifest, false);
        System.err.println("AUTHOR CHECK: confirm that the explicit probe/validation/test seeds "
                + "are disjoint from historical training before using these results in the paper.");
        try {
            execute(configuration, archives, manifest);
            manifest.setProperty("status", "COMPLETE");
            writeManifest(configuration.outputRoot, manifest, true);
        } catch (IOException | RuntimeException e) {
            manifest.setProperty("status", "FAILED");
            manifest.setProperty("failure", e.getClass().getSimpleName() + ": " + e.getMessage());
            try {
                writeManifest(configuration.outputRoot, manifest, true);
            } catch (IOException reportFailure) {
                e.addSuppressed(reportFailure);
            }
            throw e;
        }
    }

    private static Map<Integer, FinalArchiveReader.Archive> load(RevisionConfiguration configuration)
            throws IOException {
        Map<Integer, FinalArchiveReader.Archive> archives = new LinkedHashMap<>();
        FinalArchiveReader reader = new FinalArchiveReader();
        for (int run : configuration.runs) {
            Path path = configuration.archiveDirectory.resolve("job." + run + ".out.stat").toRealPath();
            if (!path.startsWith(configuration.inputRoot)) {
                throw new IllegalArgumentException("Input archive escapes the protected root: " + path);
            }
            FinalArchiveReader.Archive archive = reader.read(path);
            long finite = archive.candidates().stream().filter(ArchiveCandidate::hasFiniteStoredFitness).count();
            if (finite < configuration.poolSize) {
                throw new IllegalArgumentException(path + " has " + finite + " finite candidates, not "
                        + configuration.poolSize + ". Choose an explicit smaller pool shared by all methods.");
            }
            for (ArchiveCandidate candidate : archive.candidates()) {
                try {
                    candidate.rules();
                } catch (IllegalArgumentException invalidRule) {
                    throw new IOException(path + ": invalid trees in archive member "
                            + candidate.ordinal(), invalidRule);
                }
            }
            System.out.println("run=" + run + ", declared=" + archive.declaredCount()
                    + ", finite=" + finite + ", excluded stored failures=" + (archive.declaredCount() - finite));
            archives.put(run, archive);
        }
        return archives;
    }

    private static void execute(RevisionConfiguration configuration,
                                Map<Integer, FinalArchiveReader.Archive> archives,
                                Properties manifest) throws IOException {
        long probeStart = System.nanoTime();
        SensitivityProbeSet probes = SensitivityProbeSet.collect(configuration.probeSeed,
                configuration.probeSamplingSeed, configuration.machines, configuration.probeJobs,
                configuration.probeWarmup, configuration.utilisation, configuration.dueFactor,
                configuration.probeCandidates, configuration.probeCount);
        manifest.setProperty("probe-collection-seconds", seconds(probeStart));
        manifest.setProperty("probe-simulation-count", "2");
        manifest.setProperty("simulation-count-scope", "validation and test; probe simulations counted separately");
        exportProbes(configuration.outputRoot, probes);
        Map<String, UserPreferenceVector> profiles = configuration.legacyProfiles
                ? UserPreferenceVector.legacyCases() : UserPreferenceVector.revisionCases();
        exportProfiles(configuration.outputRoot, profiles);
        try (RevisionCsv raw = new RevisionCsv(configuration.outputRoot.resolve("archive_members.csv"),
                    "run_id", "candidate_id", "archive_ordinal", "tree_hash", "stored_fitness",
                    "candidate_pool", "status", "sequencing", "routing");
             RevisionCsv metrics = new RevisionCsv(configuration.outputRoot.resolve("candidate_metrics.csv"),
                    "run_id", "candidate_id", "candidate_pool", "stored_fitness",
                    "validation_objective", "complexity", "sensitivity", "metric_version");
             RevisionCsv selections = new RevisionCsv(configuration.outputRoot.resolve("selections.csv"),
                    "run_id", "case_id", "method", "rank", "candidate_id", "score",
                    "validation_objective", "test_objective", "complexity", "sensitivity", "si_diagnostic");
             RevisionCsv testCases = new RevisionCsv(configuration.outputRoot.resolve("test_instances.csv"),
                    "run_id", "candidate_id", "instance_index", "instance_seed", "test_objective");
             RevisionCsv results = new RevisionCsv(configuration.outputRoot.resolve("run_results.csv"),
                    "run_id", "case_id", "method", "selected_count", "validation_objective",
                    "test_objective", "complexity", "sensitivity")) {
            SchedulingEvaluator evaluator = new SchedulingEvaluator(configuration.machines,
                    configuration.jobs, configuration.warmup, configuration.utilisation,
                    configuration.dueFactor, configuration.objective);
            for (Map.Entry<Integer, FinalArchiveReader.Archive> entry : archives.entrySet()) {
                long runStart = System.nanoTime();
                executeRun(configuration, entry.getKey(), entry.getValue(), probes, profiles,
                        evaluator, raw, metrics, selections, testCases, results);
                manifest.setProperty("run." + entry.getKey() + ".analysis-seconds", seconds(runStart));
                manifest.setProperty("simulation-count", Long.toString(evaluator.simulationCount()));
                writeManifest(configuration.outputRoot, manifest, true);
            }
        }
        for (Map.Entry<Integer, FinalArchiveReader.Archive> entry : archives.entrySet()) {
            String current = RevisionCsv.fileHash(entry.getValue().candidates().get(0).source());
            if (!current.equals(manifest.getProperty("archive." + entry.getKey() + ".sha256"))) {
                throw new IllegalStateException("Input archive changed during evaluation: run " + entry.getKey());
            }
        }
    }

    private static void executeRun(RevisionConfiguration configuration, int run,
                                   FinalArchiveReader.Archive archive, SensitivityProbeSet probes,
                                   Map<String, UserPreferenceVector> profiles,
                                   SchedulingEvaluator evaluator, RevisionCsv raw, RevisionCsv metricOutput,
                                   RevisionCsv selections, RevisionCsv testCases, RevisionCsv results) throws IOException {
        List<ArchiveCandidate> finite = new ArrayList<>();
        for (ArchiveCandidate candidate : archive.candidates()) {
            if (candidate.hasFiniteStoredFitness()) { finite.add(candidate); }
        }
        finite.sort(Comparator.comparingDouble(ArchiveCandidate::storedFitness)
                .thenComparingInt(ArchiveCandidate::ordinal));
        List<ArchiveCandidate> pool = new ArrayList<>(finite.subList(0, configuration.poolSize));
        java.util.Set<Integer> poolOrdinals = new java.util.HashSet<>();
        for (ArchiveCandidate candidate : pool) { poolOrdinals.add(candidate.ordinal()); }
        for (ArchiveCandidate candidate : archive.candidates()) {
            raw.row(run, candidate.id(), candidate.ordinal(), candidate.treeHash(), candidate.storedFitness(),
                    poolOrdinals.contains(candidate.ordinal()), candidate.hasFiniteStoredFitness()
                            ? "FINITE" : "EXCLUDED_STORED_FAILURE", candidate.sequencing(), candidate.routing());
        }
        Map<Integer, RevisionMetrics> byOrdinal = new LinkedHashMap<>();
        for (ArchiveCandidate candidate : configuration.allMetrics ? finite : pool) {
            double validation = evaluator.evaluate(candidate, configuration.validationSeed,
                    configuration.validationInstances).mean();
            RevisionMetrics row = RevisionMetrics.compute(candidate, probes, configuration.epsilon, validation);
            byOrdinal.put(candidate.ordinal(), row);
            metricOutput.row(run, candidate.id(), poolOrdinals.contains(candidate.ordinal()),
                    candidate.storedFitness(), validation, row.complexity(), row.sensitivity(),
                    OutputSensitivityCalculator.VERSION);
        }
        List<RevisionMetrics> poolMetrics = new ArrayList<>();
        List<Double> performances = new ArrayList<>();
        List<Double> complexities = new ArrayList<>();
        List<Double> sensitivities = new ArrayList<>();
        for (ArchiveCandidate candidate : pool) {
            RevisionMetrics row = byOrdinal.get(candidate.ordinal());
            poolMetrics.add(row);
            performances.add(row.validationObjective());
            complexities.add(row.complexity());
            sensitivities.add(row.sensitivity());
        }
        FuzzyInferenceSystem model = configuration.legacyNormalisation
                ? FuzzyModelFactory.createLegacy() : FuzzyModelFactory.createRevision();
        FuzzyHeuristicSelector selector = new FuzzyHeuristicSelector(model, configuration.legacyNormalisation
                ? FuzzyHeuristicSelector.NormalizationMode.LEGACY_RECIPROCAL
                : FuzzyHeuristicSelector.NormalizationMode.MIN_MAX_ALL);
        selector.buildLibrary(performances, complexities, sensitivities);
        Map<String, SchedulingEvaluator.Evaluation> testCache = new LinkedHashMap<>();
        java.util.Set<String> exportedTests = new java.util.HashSet<>();
        for (Map.Entry<String, UserPreferenceVector> profile : profiles.entrySet()) {
            Map<String, List<Heuristic>> selected = new LinkedHashMap<>();
            selected.put("FUZZY_PROFILE_MATCH", selector.selectByPreference(profile.getValue(), configuration.topK));
            selected.put("MANUAL_WS", manual(selector.getHeuristicLibrary(), profile.getValue(),
                    model, configuration.topK, false));
            selected.put("MANUAL_TARGET", manual(selector.getHeuristicLibrary(), profile.getValue(),
                    model, configuration.topK, true));
            long randomSeed = configuration.selectionSeed ^ ((long) run << 32) ^ profile.getKey().hashCode();
            selected.put("RANDOM", selector.selectRandomly(configuration.topK, new Random(randomSeed)));
            for (Map.Entry<String, List<Heuristic>> method : selected.entrySet()) {
                double valMean = 0;
                double testMean = 0;
                double complexityMean = 0;
                double sensitivityMean = 0;
                int rank = 0;
                for (Heuristic heuristic : method.getValue()) {
                    RevisionMetrics row = poolMetrics.get(heuristic.getId());
                    SchedulingEvaluator.Evaluation test = testCache.get(row.candidate().treeHash());
                    if (test == null) {
                        test = evaluator.evaluate(row.candidate(), configuration.testSeed,
                                configuration.testInstances);
                        testCache.put(row.candidate().treeHash(), test);
                    }
                    if (exportedTests.add(row.candidate().id())) {
                        for (int i = 0; i < test.values().size(); i++) {
                            testCases.row(run, row.candidate().id(), i,
                                    Math.addExact(configuration.testSeed, i), test.values().get(i));
                        }
                    }
                    rank++;
                    Object score = method.getKey().equals("RANDOM") ? ""
                            : method.getKey().equals("FUZZY_PROFILE_MATCH")
                            ? selector.getPreferenceSimilarity(heuristic, profile.getValue())
                            : manualScore(heuristic, profile.getValue(), model,
                                    method.getKey().equals("MANUAL_TARGET"));
                    selections.row(run, profile.getKey(), method.getKey(), rank,
                            row.candidate().id(), score, row.validationObjective(), test.mean(),
                            row.complexity(), row.sensitivity(), heuristic.getSelectionIndex());
                    valMean += (row.validationObjective() - valMean) / rank;
                    testMean += (test.mean() - testMean) / rank;
                    complexityMean += (row.complexity() - complexityMean) / rank;
                    sensitivityMean += (row.sensitivity() - sensitivityMean) / rank;
                }
                results.row(run, profile.getKey(), method.getKey(), rank,
                        valMean, testMean, complexityMean, sensitivityMean);
            }
        }
        System.out.println("Completed run " + run + ": pool=" + pool.size()
                + ", unique tested expressions=" + testCache.size());
    }

    private static List<Heuristic> manual(List<Heuristic> library, UserPreferenceVector profile,
                                          FuzzyInferenceSystem model, int topK, boolean target) {
        List<Heuristic> ranked = new ArrayList<>(library);
        ranked.sort(Comparator.comparingDouble((Heuristic h) -> manualScore(h, profile, model, target))
                .reversed().thenComparingInt(Heuristic::getId));
        return new ArrayList<>(ranked.subList(0, topK));
    }

    private static double manualScore(Heuristic heuristic, UserPreferenceVector profile,
                                      FuzzyInferenceSystem model, boolean targetMatching) {
        double score = 0;
        double weightSum = 0;
        for (Map.Entry<String, String> term : profile.getPreferences().entrySet()) {
            double weight = profile.getWeight(term.getKey());
            double utility = heuristic.getNormalizedCriteria().get(term.getKey());
            if (targetMatching) {
                FuzzyMembershipFunction membership = model.getInputVariables().get(term.getKey())
                        .getMembershipFunctions().stream()
                        .filter(m -> m.getLinguisticTerm().equals(term.getValue()))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown target term"));
                utility = 1.0 - Math.abs(utility - membership.getCenter());
            }
            score += weight * utility;
            weightSum += weight;
        }
        if (!(weightSum > 0) || !Double.isFinite(score)) {
            throw new IllegalArgumentException("Invalid manual preference score");
        }
        return score / weightSum;
    }

    private static void exportProfiles(Path output, Map<String, UserPreferenceVector> profiles) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(output.resolve("profiles.csv"),
                "case_id", "variable", "target_term", "importance_weight")) {
            for (Map.Entry<String, UserPreferenceVector> entry : profiles.entrySet()) {
                for (Map.Entry<String, String> term : entry.getValue().getPreferences().entrySet()) {
                    csv.row(entry.getKey(), term.getKey(), term.getValue(), entry.getValue().getWeight(term.getKey()));
                }
            }
        }
    }

    private static void exportProbes(Path output, SensitivityProbeSet probes) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(output.resolve("probes.csv"),
                "role", "probe_id", "terminal", "raw_value")) {
            writeProbes(csv, "SEQUENCING", probes.sequencing());
            writeProbes(csv, "ROUTING", probes.routing());
        }
    }

    private static void writeProbes(RevisionCsv csv, String role, List<SensitivityProbeSet.Probe> probes)
            throws IOException {
        for (SensitivityProbeSet.Probe probe : probes) {
            for (Map.Entry<String, Double> entry : probe.values().entrySet()) {
                csv.row(role, probe.id(), entry.getKey(), entry.getValue());
            }
        }
    }

    private static void writeManifest(Path root, Properties properties, boolean replace) throws IOException {
        try (Writer writer = Files.newBufferedWriter(root.resolve("manifest.properties"),
                StandardCharsets.UTF_8, replace
                        ? new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING}
                        : new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW})) {
            properties.store(writer, "QDGP revision experiment: explicit paper-v2 protocol");
        }
    }

    private static String seedDescription(long first, int count) {
        return first + ":" + Math.addExact(first, count - 1) + " (one fresh simulation per explicit seed)";
    }

    private static String seconds(long start) {
        return Double.toString((System.nanoTime() - start) / 1_000_000_000.0);
    }
}
