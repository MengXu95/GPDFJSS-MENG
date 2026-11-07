package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.Objective;

/** Read-only snapshot analysis: no selection, evolution, archive admission or GP simulation. */
public final class ECheckpointAnalysis {
    public static final String PROTOCOL = "e-checkpoint-full-population-posthoc-v1";
    static final int CELLS = 6561, MEMBERS = 500;
    static final Objective OBJECTIVE = Objective.MEAN_TARDINESS;
    private ECheckpointAnalysis() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("ECheckpointAnalysis --input-root <completed-E-root> --output-root <NEW-directory>");
            return;
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !Arrays.asList("--input-root", "--output-root").contains(args[i])
                    || options.put(args[i], args[i + 1]) != null) {
                throw new IllegalArgumentException("Expected --input-root and --output-root exactly once");
            }
        }
        if (options.size() != 2) { throw new IllegalArgumentException("Both roots are required"); }
        run(Path.of(options.get("--input-root")), Path.of(options.get("--output-root")),
                range(0, 29, 1), range(0, 200, 20));
    }

    static List<Integer> range(int first, int last, int step) {
        List<Integer> result = new ArrayList<>();
        for (int i = first; i <= last; i += step) { result.add(i); }
        return result;
    }

    static void require(boolean valid, String message) throws IOException {
        if (!valid) { throw new IOException(message); }
    }

    static void equal(Properties p, String key, Object expected) throws IOException {
        require(expected.toString().equals(EFiles.required(p, key)), "Incompatible " + key + ": " + p.getProperty(key));
    }

    static boolean finite(double value) {
        return Double.isFinite(value) && value >= 0 && value < Double.MAX_VALUE;
    }

    static double normalise(double raw, double reference) {
        if (!finite(reference) || reference <= 0) {
            throw new IllegalArgumentException("Benchmark objective must be finite and positive, not " + reference);
        }
        if (!finite(raw)) { return Double.POSITIVE_INFINITY; }
        double result = raw / reference;
        if (!finite(result)) { throw new IllegalArgumentException("Normalised finite objective overflow"); }
        return result;
    }

    /** Construct the shop once at the initial seed; changing the job stream never regenerates the shop. */
    static final class Benchmark extends HeterogeneousSimulation {
        int simulations;
        Benchmark(EConfiguration c, long initialSeed) {
            super(initialSeed, null, null, c.machines, c.jobs, c.warmup, 1, c.machines, .85, c.dueFactor, false);
        }
        double evaluate(long currentSeed) {
            setSequencingRule(OBJECTIVE.benchmarkSequencingRule());
            setRoutingRule(OBJECTIVE.benchmarkRoutingRule());
            // Routing setters/resetState generate jobs. Match EGridEvaluation's final reseed before run().
            reset(currentSeed);
            simulations++;
            super.run();
            double result = objectiveValue(OBJECTIVE);
            if (getThroughput() != getNumJobsRecorded() || !finite(result) || result <= 0) {
                throw new IllegalStateException("Incomplete/nonpositive/nonfinite ATC/WIQ benchmark: " + result);
            }
            return result;
        }
    }

    static final class ReferenceCache {
        final Benchmark benchmark;
        final Map<Long, Double> values = new LinkedHashMap<>();
        ReferenceCache(Benchmark benchmark) { this.benchmark = benchmark; }
        double get(long seed) { return values.computeIfAbsent(seed, benchmark::evaluate); }
    }

    static final class Projection {
        final double[] digits;
        final int[] cell;
        final String failure;
        Projection(double[] digits, int[] cell, String failure) {
            this.digits = digits; this.cell = cell; this.failure = failure;
        }
        static Projection of(ETest.ProjectionService bank, ArchiveCandidate candidate) {
            try {
                double[] digits = bank.coordinates(candidate);
                return new Projection(digits, ETest.cells(digits), "");
            } catch (RuntimeException failure) {
                return new Projection(null, null, failure.getClass().getName() + ": " + failure.getMessage());
            }
        }
        String digits() {
            if (digits == null) { return ""; }
            return Arrays.stream(digits).mapToObj(v -> Integer.toString((int) v)).collect(Collectors.joining(";"));
        }
    }

    static final class SnapshotMetrics {
        int members, finiteN, failedN, projected, projectionFailures;
        final Set<Integer> occupied = new HashSet<>(), finiteOccupied = new HashSet<>();
        double finiteMean, best = Double.NaN;
        void add(double raw, Projection p) {
            members++;
            if (finite(raw)) {
                finiteN++;
                finiteMean += (raw - finiteMean) / finiteN;
                best = Double.isNaN(best) ? raw : Math.min(best, raw);
            } else { failedN++; }
            if (p.cell == null) { projectionFailures++; }
            else {
                projected++;
                int key = p.cell[0] * 81 + p.cell[1];
                occupied.add(key);
                if (finite(raw)) { finiteOccupied.add(key); }
            }
        }
        double conditionalMean() { return finiteN == 0 ? Double.NaN : finiteMean; }
        double inclusiveMean() { return failedN > 0 ? Double.POSITIVE_INFINITY : conditionalMean(); }
        double coverage() { return 100.0 * occupied.size() / CELLS; }
        double[] values(double reference) {
            return new double[]{coverage(), 100.0 * finiteOccupied.size() / CELLS, conditionalMean(), best,
                score(conditionalMean(), reference), score(best, reference), inclusiveMean(),
                failedN > 0 ? Double.POSITIVE_INFINITY : score(conditionalMean(), reference),
                finiteN, failedN, projected, projectionFailures};
        }
    }

    static double score(double value, double reference) {
        return Double.isNaN(value) ? Double.NaN : normalise(value, reference);
    }

    static final String[] METRICS = {"coverage_pct_all", "coverage_pct_stored_finite",
        "conditional_finite_raw_mean", "best_finite_raw", "conditional_finite_normalised_mean",
        "best_finite_normalised", "failure_inclusive_raw_mean", "failure_inclusive_normalised_mean",
        "finite_n", "failed_n", "projected_n", "projection_failed_n"};

    static final class Moments {
        int n, defined, infinite;
        double mean, m2;
        void add(double value) {
            n++;
            if (Double.isNaN(value)) { return; }
            if (!Double.isFinite(value)) { infinite++; return; }
            defined++;
            double delta = value - mean;
            mean += delta / defined;
            m2 += delta * (value - mean);
        }
        double mean() { return infinite > 0 ? Double.POSITIVE_INFINITY : defined == 0 ? Double.NaN : mean; }
        double sd() { return infinite > 0 || defined < 2 ? Double.NaN : Math.sqrt(m2 / (defined - 1)); }
    }

    static Object number(double value) { return Double.isNaN(value) ? "NA" : value; }

    static final class Ledger {
        final Map<Path, String> hashes = new TreeMap<>();
        void capture(Path path) throws IOException { hashes.putIfAbsent(path.toRealPath(), EFiles.hash(path)); }
        void manifest(Path directory, Properties manifest) throws IOException {
            capture(directory.resolve("manifest.properties"));
            for (String key : manifest.stringPropertyNames()) {
                if (key.startsWith("data.") && key.endsWith(".sha256")) {
                    Path path = EFiles.mapped(directory, key.substring(5, key.length() - 7));
                    EFiles.checkHash(path, manifest.getProperty(key));
                    capture(path);
                }
            }
        }
        void writeVerified(Path output) throws IOException {
            try (BufferedWriter writer = EFiles.create(output)) {
                EFiles.row(writer, "path", "sha256_before", "sha256_after", "unchanged");
                for (Map.Entry<Path, String> item : hashes.entrySet()) {
                    String after = EFiles.hash(item.getKey());
                    require(item.getValue().equals(after), "Input changed during analysis: " + item.getKey());
                    EFiles.row(writer, item.getKey(), item.getValue(), after, true);
                }
            }
        }
    }

    static final class Source {
        final EConfiguration.Case item;
        final Path directory, testDirectory;
        final Properties manifest, testManifest;
        Source(EConfiguration.Case item, Path directory, Path testDirectory,
               Properties manifest, Properties testManifest) {
            this.item = item; this.directory = directory; this.testDirectory = testDirectory;
            this.manifest = manifest; this.testManifest = testManifest;
        }
    }

    static List<Source> sources(EConfiguration c, Path input, Ledger ledger, List<Integer> runs) throws IOException {
        Path training = input.resolve("training").toRealPath(), test = input.resolve("test").toRealPath();
        Properties trainingRoot = EFiles.read(training.resolve("manifest.properties"));
        Properties testRoot = EFiles.read(test.resolve("manifest.properties"));
        equal(trainingRoot, "status", "COMPLETE"); equal(testRoot, "status", "COMPLETE");
        equal(trainingRoot, "protocol", EConfiguration.PROTOCOL); equal(testRoot, "protocol", ETest.PROTOCOL);
        ledger.manifest(training, trainingRoot); ledger.manifest(test, testRoot);
        String family = EFiles.required(trainingRoot, "family-hash");
        require(family.equals(ETraining.fingerprint(c)), "Runtime scientific classes differ from completed E training family");
        List<Source> result = new ArrayList<>();
        for (EConfiguration.Regime regime : EConfiguration.Regime.values()) for (int run : runs) {
            EConfiguration.Case item = new EConfiguration.Case(EConfiguration.Method.LEXICASE, regime,
                    EConfiguration.Scenario.T085, run);
            Path directory = EFiles.mapped(training, EFiles.required(trainingRoot, item.key() + ".path"));
            EFiles.checkHash(directory.resolve("manifest.properties"), EFiles.required(trainingRoot, item.key() + ".manifest-sha256"));
            Properties manifest = EFiles.read(directory.resolve("manifest.properties"));
            ETraining.validateCase(c, item, family, manifest);
            equal(manifest, "objective", "mean-tardiness"); equal(manifest, "utilisation", .85);
            equal(manifest, "initial-training-seed", c.trainingSeed(item));
            equal(manifest, "final-members", MEMBERS);
            equal(manifest, "shop-policy", "fixed shop generated at initial training seed; reset controls job streams");
            ledger.manifest(directory, manifest);
            Path testDirectory = EFiles.mapped(test, EFiles.required(testRoot, item.key() + ".path"));
            EFiles.checkHash(testDirectory.resolve("manifest.properties"), EFiles.required(testRoot, item.key() + ".manifest-sha256"));
            Properties testManifest = EFiles.read(testDirectory.resolve("manifest.properties"));
            equal(testManifest, "status", "COMPLETE");
            ledger.manifest(testDirectory, testManifest);
            result.add(new Source(item, directory, testDirectory, manifest, testManifest));
        }
        return result;
    }

    static void run(Path inputRoot, Path outputRoot, List<Integer> runs, List<Integer> generations) throws IOException {
        Path input = inputRoot.toRealPath(), output = outputRoot.toAbsolutePath().normalize();
        require(!Files.exists(output), "Analysis output MUST be a new directory");
        require(!output.startsWith(input.resolve("training")) && !output.startsWith(input.resolve("test"))
                && !input.startsWith(output), "Analysis must be separate from training/test inputs");
        require(!runs.isEmpty() && new HashSet<>(runs).size() == runs.size()
                && runs.stream().allMatch(v -> v >= 0 && v < 30), "Expected distinct run IDs in 0..29");
        require(!generations.isEmpty() && new HashSet<>(generations).size() == generations.size()
                && generations.stream().allMatch(v -> v >= 0 && v <= 200 && v % 20 == 0), "Expected saved checkpoints");
        Files.createDirectory(output);
        Properties provenance = new Properties();
        provenance.setProperty("protocol", PROTOCOL);
        provenance.setProperty("status", "RUNNING");
        provenance.setProperty("input-root", input.toString());
        provenance.setProperty("runs", runs.toString()); provenance.setProperty("generations", generations.toString());
        EFiles.write(output.resolve("manifest.properties"), provenance, false);
        long started = System.nanoTime();
        try {
            EConfiguration c = EConfiguration.parse(new String[]{"--config",
                input.resolve("training").resolve("resolved-study.properties").toString()});
            require(c.runs == 30 && c.population == MEMBERS && c.generations == 201 && c.snapshotInterval == 20
                    && c.machines == 10 && c.jobs == 1000 && c.warmup == 500 && c.dueFactor == 1.5
                    && c.descriptorJobs == 1000 && c.descriptorWarmup == 500
                    && c.commonSeed(EConfiguration.Scenario.T085) == 300000000L
                    && c.commonSamplingSeed(EConfiguration.Scenario.T085) == 360000000L,
                    "This protocol requires the completed formal E controls");
            Ledger ledger = new Ledger();
            List<Source> sources = sources(c, input, ledger, runs);
            captureImplementation(c, ledger);
            Properties testRoot = EFiles.read(input.resolve("test").resolve("manifest.properties"));
            String bankKey = "common.mean-tardiness-u085";
            Path formalBank = EFiles.mapped(input.resolve("test"), EFiles.required(testRoot, bankKey + ".path"));
            EFiles.checkHash(formalBank.resolve("manifest.properties"), EFiles.required(testRoot, bankKey + ".manifest-sha256"));
            Properties bankManifest = EFiles.read(formalBank.resolve("manifest.properties"));
            equal(bankManifest, "status", "COMPLETE"); equal(bankManifest, "d", 4); equal(bankManifest, "c", 3);
            equal(bankManifest, "simulation-seed", c.commonSeed(EConfiguration.Scenario.T085));
            equal(bankManifest, "sampling-seed", c.commonSamplingSeed(EConfiguration.Scenario.T085));
            ledger.manifest(formalBank, bankManifest);
            ETest.ProjectionService bank = new ETest.RealProjectionFactory().collect(c, EConfiguration.Scenario.T085);
            bank.write(output.resolve("common_descriptor.csv"));
            EFiles.checkHash(output.resolve("common_descriptor.csv"),
                    EFiles.required(bankManifest, "data.common_descriptor.csv.sha256"));
            require(bank.simulationCount() == 2, "Expected exactly two descriptor reference replays");
            execute(c, output, sources, generations, bank, provenance);
            ledger.writeVerified(output.resolve("input_hashes.csv"));
            guide(output);
            provenance.setProperty("common-bank-sha256", EFiles.hash(output.resolve("common_descriptor.csv")));
            provenance.setProperty("source-training-family", EFiles.read(input.resolve("training").resolve("manifest.properties")).getProperty("family-hash"));
            provenance.setProperty("descriptor-reference-simulations", Long.toString(bank.simulationCount()));
            provenance.setProperty("gp-training-simulations", "0");
            provenance.setProperty("validation-simulations", "0"); provenance.setProperty("heldout-test-simulations", "0");
            provenance.setProperty("benchmark-sequencing-class", OBJECTIVE.benchmarkSequencingRule().getClass().getName());
            provenance.setProperty("benchmark-routing-class", OBJECTIVE.benchmarkRoutingRule().getClass().getName());
            provenance.setProperty("scope", runs.equals(range(0, 29, 1)) && generations.equals(range(0, 200, 20))
                    ? "FORMAL_30_RUNS_BOTH_REGIMES_T085_660_SNAPSHOTS" : "BOUNDED_CHECK_NOT_FORMAL");
            provenance.setProperty("input-files-verified-before-and-after", Integer.toString(ledger.hashes.size()));
            provenance.setProperty("elapsed-seconds", Double.toString((System.nanoTime() - started) / 1e9));
            try (var paths = Files.list(output)) {
                for (Path path : paths.filter(p -> !p.getFileName().toString().equals("manifest.properties")).sorted().toList()) {
                    provenance.setProperty("data." + path.getFileName() + ".sha256", EFiles.hash(path));
                }
            }
            EFiles.verifyArtifacts(output, provenance);
            provenance.setProperty("status", "COMPLETE");
            EFiles.write(output.resolve("manifest.properties"), provenance, true);
            System.out.println("COMPLETE " + output + " snapshots=" + provenance.getProperty("snapshots")
                    + " members=" + provenance.getProperty("member-records")
                    + " benchmark-simulations=" + provenance.getProperty("normaliser-reference-simulations"));
        } catch (IOException | RuntimeException | Error failure) {
            provenance.setProperty("status", "FAILED");
            provenance.setProperty("failure", failure.toString());
            try { EFiles.write(output.resolve("manifest.properties"), provenance, true); }
            catch (IOException writeFailure) { failure.addSuppressed(writeFailure); }
            throw failure;
        }
    }

    static void captureImplementation(EConfiguration c, Ledger ledger) throws IOException {
        ledger.capture(c.populationParams); ledger.capture(c.archiveParams);
        Path sourceRoot = c.populationParams.getParent();
        while (sourceRoot != null && !sourceRoot.getFileName().toString().equals("src")) { sourceRoot = sourceRoot.getParent(); }
        require(sourceRoot != null, "Cannot locate source root for provenance");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) { ledger.capture(path); }
        }
    }

    static void execute(EConfiguration c, Path output, List<Source> sources, List<Integer> generations,
                        ETest.ProjectionService bank, Properties provenance) throws IOException {
        Map<String, Map<Integer, List<double[]>>> grouped = new LinkedHashMap<>();
        long snapshots = 0, records = 0, failedRecords = 0, projectionFailures = 0, referenceSimulations = 0;
        int finalChecks = 0;
        try (BufferedWriter members = EFiles.create(output.resolve("all_members.csv"));
             BufferedWriter rows = EFiles.create(output.resolve("all_runs.csv"));
             BufferedWriter references = EFiles.create(output.resolve("normalisers.csv"));
             BufferedWriter errors = EFiles.create(output.resolve("errors.csv"));
             BufferedWriter parity = EFiles.create(output.resolve("final_projection_parity.csv"))) {
            EFiles.row(members, "group", "regime", "run", "generation", "training_seed", "population_index", "candidate_id",
                    "tree_hash", "raw_objective", "normaliser", "comparison_normalised", "stored_finite",
                    "seq_cell", "rout_cell", "pc_digits", "projection_status", "projection_failure");
            List<Object> header = new ArrayList<>(Arrays.asList("group", "regime", "run", "generation", "training_seed",
                    "members", "occupied_cells_all", "occupied_cells_stored_finite", "normaliser", "unique_tree_pairs",
                    "duplicate_members", "finite_score_status"));
            header.addAll(Arrays.asList(METRICS)); EFiles.row(rows, header.toArray());
            EFiles.row(references, "group", "run", "generation", "initial_shop_seed", "current_job_stream_seed",
                    "objective", "sequencing_rule", "routing_rule", "reference_objective", "new_simulations", "cache_hit");
            EFiles.row(errors, "group", "run", "generation", "candidate_id", "kind", "detail");
            EFiles.row(parity, "group", "run", "members", "all_occupied", "finite_occupied", "finite_n",
                    "projection_failed_n", "formal_member_projection_sha256", "status");
            for (Source source : sources) {
                String group = source.item.regime == EConfiguration.Regime.fixed ? "Lex-f" : "Lex-rot";
                Map<Integer, List<double[]>> groupRows = grouped.computeIfAbsent(group, ignored -> new TreeMap<>());
                ReferenceCache cache = new ReferenceCache(new Benchmark(c, c.trainingSeed(source.item)));
                Map<String, Projection> pcCache = new LinkedHashMap<>();
                for (int generation : generations) {
                    String stem = String.format(java.util.Locale.ROOT, "population.gen%04d", generation);
                    PopulationSnapshots.Snapshot snapshot = PopulationSnapshots.read(source.directory.resolve(stem + ".stat"));
                    long seed = c.trainingSeed(source.item) + (source.item.regime == EConfiguration.Regime.rotating
                            ? generation * c.seedStep : 0);
                    require(snapshot.generation() == generation && snapshot.trainingSeed() == seed
                            && snapshot.candidates().size() == MEMBERS, "Snapshot size/generation/current seed differs: " + stem);
                    verifySnapshotCsv(source, stem, generation, seed, snapshot.candidates());
                    int before = cache.benchmark.simulations;
                    double reference = cache.get(seed);
                    EFiles.row(references, group, source.item.run, generation, c.trainingSeed(source.item), seed,
                            OBJECTIVE.getName(), OBJECTIVE.benchmarkSequencingRule().getName(),
                            OBJECTIVE.benchmarkRoutingRule().getName(), reference, cache.benchmark.simulations - before,
                            before == cache.benchmark.simulations);
                    SnapshotMetrics metric = new SnapshotMetrics();
                    List<Projection> pcs = new ArrayList<>();
                    for (ArchiveCandidate member : snapshot.candidates()) {
                        Projection pc = pcCache.computeIfAbsent(member.treeHash(), ignored -> Projection.of(bank, member));
                        pcs.add(pc); metric.add(member.storedFitness(), pc);
                        EFiles.row(members, group, source.item.regime, source.item.run, generation, seed, member.ordinal(),
                                member.id(), member.treeHash(), member.storedFitness(), reference,
                                normalise(member.storedFitness(), reference), member.hasFiniteStoredFitness(),
                                pc.cell == null ? "NA" : pc.cell[0], pc.cell == null ? "NA" : pc.cell[1], pc.digits(),
                                pc.cell == null ? "FAILED" : "PROJECTED", pc.failure);
                        if (pc.cell == null) { EFiles.row(errors, group, source.item.run, generation, member.id(), "PROJECTION_FAILED", pc.failure); }
                    }
                    require(metric.members == MEMBERS && metric.finiteN + metric.failedN == MEMBERS
                            && metric.projected + metric.projectionFailures == MEMBERS && metric.occupied.size() <= MEMBERS,
                            "Full-population accounting failed");
                    if (metric.finiteN == 0) {
                        EFiles.row(errors, group, source.item.run, generation, "", "NO_FINITE_TRAINING_SCORE",
                                "Conditional mean and best are undefined (NA); failure-inclusive mean is Infinity");
                    }
                    long unique = snapshot.candidates().stream().map(ArchiveCandidate::treeHash).distinct().count();
                    List<Object> fields = new ArrayList<>(Arrays.asList(group, source.item.regime, source.item.run,
                            generation, seed, metric.members, metric.occupied.size(), metric.finiteOccupied.size(),
                            reference, unique, MEMBERS - unique, metric.finiteN == 0 ? "UNDEFINED_NO_FINITE" : "FINITE_CONDITIONAL"));
                    double[] values = metric.values(reference);
                    for (double value : values) { fields.add(number(value)); }
                    EFiles.row(rows, fields.toArray()); groupRows.computeIfAbsent(generation, ignored -> new ArrayList<>()).add(values);
                    if (generation == 200) {
                        verifyFinal(source, snapshot.candidates(), pcs, metric);
                        EFiles.row(parity, group, source.item.run, metric.members, metric.occupied.size(),
                                metric.finiteOccupied.size(), metric.finiteN, metric.projectionFailures,
                                EFiles.hash(source.testDirectory.resolve("member_projection.csv")), "EXACT_ALL_500_MEMBERS");
                        finalChecks++;
                    }
                    snapshots++; records += metric.members; failedRecords += metric.failedN;
                    projectionFailures += metric.projectionFailures;
                }
                referenceSimulations += cache.benchmark.simulations;
                System.out.println(group + " run=" + source.item.run + " checkpoints=" + generations.size()
                        + " reference-simulations=" + cache.benchmark.simulations);
            }
        }
        require(snapshots == (long) sources.size() * generations.size() && records == snapshots * MEMBERS,
                "Output snapshot/member count differs from requested scope");
        try (BufferedWriter summary = EFiles.create(output.resolve("summary.csv"));
             BufferedWriter tables = EFiles.create(output.resolve("table_fragments.csv"))) {
            EFiles.row(summary, "group", "generation", "metric", "run_n", "defined_finite_run_n", "infinite_run_n",
                    "undefined_run_n", "mean", "sample_sd");
            EFiles.row(tables, "generation", "group", "runs", "coverage_pct_mean", "coverage_pct_sample_sd",
                    "conditional_finite_normalised_mean", "conditional_finite_normalised_mean_sample_sd",
                    "best_finite_normalised_mean", "best_finite_normalised_sample_sd",
                    "failure_inclusive_normalised_mean", "failure_inclusive_normalised_sample_sd",
                    "failed_members_total", "projection_failures_total");
            for (var group : grouped.entrySet()) for (var generation : group.getValue().entrySet()) {
                Moments[] moments = new Moments[METRICS.length];
                for (int i = 0; i < moments.length; i++) {
                    moments[i] = new Moments();
                    for (double[] values : generation.getValue()) { moments[i].add(values[i]); }
                    Moments m = moments[i];
                    EFiles.row(summary, group.getKey(), generation.getKey(), METRICS[i], m.n, m.defined,
                            m.infinite, m.n - m.defined - m.infinite, number(m.mean()), number(m.sd()));
                }
                if (generation.getKey() > 0) {
                    EFiles.row(tables, generation.getKey(), group.getKey(), generation.getValue().size(),
                            number(moments[0].mean()), number(moments[0].sd()), number(moments[4].mean()), number(moments[4].sd()),
                            number(moments[5].mean()), number(moments[5].sd()), number(moments[7].mean()), number(moments[7].sd()),
                            generation.getValue().stream().mapToInt(v -> (int) v[9]).sum(),
                            generation.getValue().stream().mapToInt(v -> (int) v[11]).sum());
                }
            }
        }
        provenance.setProperty("snapshots", Long.toString(snapshots));
        provenance.setProperty("member-records", Long.toString(records));
        provenance.setProperty("failed-member-records", Long.toString(failedRecords));
        provenance.setProperty("projection-failed-member-records", Long.toString(projectionFailures));
        provenance.setProperty("normaliser-reference-simulations", Long.toString(referenceSimulations));
        provenance.setProperty("gen200-final-projection-parity-checks", Integer.toString(finalChecks));
    }

    static void verifySnapshotCsv(Source source, String stem, int generation, long seed,
                                  List<ArchiveCandidate> candidates) throws IOException {
        List<Map<String, String>> csv = readCsv(source.directory.resolve(stem + ".csv"));
        require(csv.size() == MEMBERS, "Snapshot CSV must retain all 500 members");
        for (int i = 0; i < MEMBERS; i++) {
            Map<String, String> row = csv.get(i); ArchiveCandidate member = candidates.get(i);
            require(row.get("candidate_id").equals(member.id()) && row.get("tree_hash").equals(member.treeHash())
                    && row.get("run_id").equals(Integer.toString(source.item.run))
                    && row.get("generation").equals(Integer.toString(generation))
                    && row.get("training_seed").equals(Long.toString(seed))
                    && row.get("population_index").equals(Integer.toString(i))
                    && Double.compare(Double.parseDouble(row.get("raw_objective")), member.storedFitness()) == 0
                    && row.get("valid").equals(Boolean.toString(member.hasFiniteStoredFitness()))
                    && row.get("sequencing").trim().equals(member.sequencing())
                    && row.get("routing").trim().equals(member.routing()), "Snapshot CSV/stat member mismatch at " + i);
        }
    }

    static void verifyFinal(Source source, List<ArchiveCandidate> members, List<Projection> pcs,
                            SnapshotMetrics metric) throws IOException {
        PopulationSnapshots.Snapshot finalSnapshot = PopulationSnapshots.read(source.directory.resolve("final_population.stat"));
        List<Map<String, String>> rows = readCsv(source.testDirectory.resolve("member_projection.csv"));
        require(rows.size() == MEMBERS && finalSnapshot.candidates().size() == MEMBERS, "Final member count differs");
        for (int i = 0; i < MEMBERS; i++) {
            ArchiveCandidate a = members.get(i), b = finalSnapshot.candidates().get(i);
            Map<String, String> row = rows.get(i); Projection p = pcs.get(i);
            require(a.id().equals(b.id()) && a.treeHash().equals(b.treeHash())
                    && Double.compare(a.storedFitness(), b.storedFitness()) == 0
                    && a.id().equals(row.get("candidate_id")) && a.treeHash().equals(row.get("tree_hash"))
                    && Double.compare(a.storedFitness(), Double.parseDouble(row.get("stored_fitness"))) == 0
                    && row.get("stored_status").equals(a.hasFiniteStoredFitness() ? "STORED_FINITE" : "STORED_FAILURE")
                    && row.get("seq_cell").equals(p.cell == null ? "NA" : Integer.toString(p.cell[0]))
                    && row.get("rout_cell").equals(p.cell == null ? "NA" : Integer.toString(p.cell[1]))
                    && row.get("digits").equals(p.digits())
                    && row.get("status").equals(p.cell == null ? "FAILED" : "PROJECTED"),
                    "GEN200/formal-test projection mismatch: " + source.item.key() + " member " + i);
        }
        equal(source.testManifest, "common_occupied_cells_all_retained", metric.occupied.size());
        equal(source.testManifest, "common_occupied_cells_stored_finite", metric.finiteOccupied.size());
        equal(source.testManifest, "common_projection_failures", metric.projectionFailures);
        equal(source.testManifest, "stored_finite_members", metric.finiteN);
    }

    static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String first = reader.readLine(); require(first != null, "Empty CSV " + path);
            List<String> header = csvLine(first);
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> values = csvLine(line);
                require(values.size() == header.size(), "CSV width differs in " + path);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) { row.put(header.get(i), values.get(i)); }
                rows.add(row);
            }
        }
        return rows;
    }

    static List<String> csvLine(String line) throws IOException {
        List<String> values = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
                else { quoted = !quoted; }
            } else if (ch == ',' && !quoted) { values.add(field.toString()); field.setLength(0); }
            else { field.append(ch); }
        }
        require(!quoted, "Unclosed CSV quote"); values.add(field.toString()); return values;
    }

    static void guide(Path output) throws IOException {
        Properties guide = new Properties();
        guide.setProperty("cohort", "Lex-f/Lex-rot: saved single-generation full population, ALL 500 members including duplicates and failures. NO cell-winner filtering; NO cross-generation union.");
        guide.setProperty("historical-cohort", "Old Tables 15-17: fixed GPf/QDGPf/QDf2 cumulative historical map cohorts; reported 30-run mean/sample SD, not one seed. Historical binary provenance is not fully certified.");
        guide.setProperty("comparison-limit", "Supplemental unmatched cohorts only: cumulative versus snapshot and historical normaliser seed/binary differ. NOT a newly matched controlled QD comparison; no metric-ranking claim.");
        guide.setProperty("coverage", "100*occupied cells/6561 on the EXACT formal E independent D4/C3 bank. Each cell counted once; failed projections retained but have no fabricated cell. Counts projected_n+projection_failed_n=500.");
        guide.setProperty("descriptor-reference", "Native DcDescriptorBank with WSPT/WIQ reference PCs, simulation seed 300000000, sampling seed 360000000; fresh replay CSV SHA-256 must equal formal test bank exactly.");
        guide.setProperty("quality", "Training objective of ALL 500 saved members, independently of projection validity. Stored MAX_DOUBLE and nonfinite are failures. Duplicates retain their multiplicity.");
        guide.setProperty("conditional", "CONDITIONAL finite-only within-population arithmetic mean mimics legacy gridPCmap finite-only semantics, NOT average cell winners. finite_n/failed_n are always exported. With no finite score, mean/best are NA, never zero.");
        guide.setProperty("failure-inclusive", "Primary failure-inclusive mean is Infinity whenever ANY of the 500 members fails; group mean Infinity and sample SD NA when any run is infinite. Conditional means must not hide these failures.");
        guide.setProperty("best", "Minimum finite stored training objective across the full population, undefined if no finite score; not validation-selected or held-out test performance.");
        guide.setProperty("normalisation", "Tables16/17 supplemental score=raw training objective / positive finite ATC+WIQ benchmark objective under current E training reset semantics, same initial shop seed and current job stream. This is posthoc training normalisation, NOT validation/test quality.");
        guide.setProperty("normalisation-reset", "HeterogeneousSimulation constructed once at initial shop seed=100000000+run; benchmark rules from Objective; reset(currentSeed) AFTER setRoutingRule immediately before run, matching EGridEvaluation training evaluation. No legacy reference-phase RNG-advance emulation.");
        guide.setProperty("normalisation-cache", "Fixed cache one benchmark per run; rotating one per saved generation seed=initial+10000*g, including generation 0. Formal total 30+330=360 benchmark simulations; 2 descriptor replays separate. No GP training/evolution simulations.");
        guide.setProperty("summary", "Mean and SAMPLE SD of 30 per-run statistics for each group/generation; percentages, not fractions. all_runs.csv has all checkpoints including 0; table_fragments.csv contains reported 20..200 only.");
        guide.setProperty("completion", "COMPLETE is atomic only after snapshot accounting, CSV/stat identity, exact GEN200/formal projection parity, and input/output SHA verification. A failure leaves FAILED, never a success-shaped fallback.");
        EFiles.write(output.resolve("metrics_guide.properties"), guide, false);
    }
}
