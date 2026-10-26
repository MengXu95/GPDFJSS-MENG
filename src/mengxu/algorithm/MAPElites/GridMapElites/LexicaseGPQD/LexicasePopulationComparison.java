package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import static mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseFiles.create;
import static mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseFiles.row;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.FinalArchiveReader;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.Objective;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.rule.operation.weighted.WSPT;
import yimei.jss.rule.workcenter.basic.WIQ;

/**
 * Offline comparison of a complete final population and a true final QD GridMAP.
 * No historical archive, selection, training, or feedback is performed.
 *
 * Required: --population FILE --qd-archive FILE --output-root NEW_DIRECTORY.
 * Optional: --objective mean-tardiness|max-flowtime|mean-weighted-flowtime,
 * --utilisation .85 --due-factor 1.5 --seed 718341 --instances 5
 * --recorded-jobs 1000 --warmup-jobs 500 --machines 10.
 * Standalone --help or no CLI arguments prints usage without reading inputs,
 * creating outputs, or running simulations.
 *
 * The output parent must exist. All outputs use CREATE_NEW. Output directories
 * underneath either real input parent are forbidden, including via symlinks.
 * The post-hoc common grid is analysis only, not either method's native output.
 * COMPLETE means all members were analysed; explicitly INVALID outcomes can
 * still occur. FAILED identifies an incomplete analysis; partial CSVs must not
 * be treated as complete results. See manifest.csv for costs and semantics.
 */
public final class LexicasePopulationComparison {
    static final String POPULATION = "actual_final_population";
    static final String QD = "actual_final_qd_gridmap_archive";
    static final int GRID_SIDE = 81;
    static final int MAX_MEMBERS_PER_INPUT = 100000;
    static final long MAX_INSTANCE_ROWS = 1000000L;
    static final long MAX_SIMULATIONS = 1000000L;
    static final long MAX_RECORDED_PLUS_WARMUP_JOBS = 1000000000L;

    private LexicasePopulationComparison() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && "--help".equals(args[0]))) {
            Options defaults = new Options();
            System.out.println("Usage: LexicasePopulationComparison --population FILE --qd-archive FILE"
                    + " --output-root NEW_DIRECTORY [options]\n"
                    + "       LexicasePopulationComparison --help\n"
                    + "Required (no defaults): --population FILE --qd-archive FILE --output-root NEW_DIRECTORY\n"
                    + "Optional defaults (override with --name value):\n"
                    + "  --objective " + defaults.objective.getName() + "\n"
                    + "    Choices: mean-tardiness|max-flowtime|mean-weighted-flowtime\n"
                    + "  --utilisation " + defaults.utilisation + "\n"
                    + "  --due-factor " + defaults.dueFactor + "\n"
                    + "  --seed " + defaults.seed + "\n"
                    + "  --instances " + defaults.instances + "\n"
                    + "  --recorded-jobs " + defaults.recordedJobs + "\n"
                    + "  --warmup-jobs " + defaults.warmupJobs + "\n"
                    + "  --machines " + defaults.machines + "\n"
                    + "Standalone --help or no arguments prints usage only; no file access or simulations.\n"
                    + "Output parent must already exist; output must be NEW and outside both input directories.\n"
                    + "Outputs: manifest.csv, members.csv, instance-outcomes.csv, collection-summary.csv,\n"
                    + "         post-hoc-common-grid.csv, validation-seeds.csv.\n"
                    + "Raw whole-instance validation; common grids are POST-HOC analysis only, never training feedback.");
            return;
        }
        Path output = run(args);
        System.out.println("COMPLETE: " + output
                + " (inspect invalid counts; common grids are post-hoc only)");
    }

    public static Path run(String... args) throws IOException {
        Options options = Options.parse(args);
        Path population = options.population.toRealPath();
        Path qd = options.qdArchive.toRealPath();
        if (!Files.isRegularFile(population) || !Files.isRegularFile(qd)) {
            throw new IllegalArgumentException("Both inputs must be regular files");
        }
        Path output = newOutput(options.outputRoot, population, qd);
        Files.createDirectory(output);
        long start = System.nanoTime();
        Costs costs = new Costs();
        Map<String, String> manifest = options.manifest(population, qd, output);
        Throwable failure = null;
        boolean complete = false;
        try {
            manifest.put("population_sha256_before", hash(population));
            manifest.put("qd_archive_sha256_before", hash(qd));
            PopulationSnapshots.Snapshot snapshot = PopulationSnapshots.read(population);
            List<ArchiveCandidate> populationMembers = snapshot.candidates();
            List<ArchiveCandidate> qdMembers = new FinalArchiveReader().read(qd).candidates();
            manifest.put("population_generation", Integer.toString(snapshot.generation()));
            manifest.put("population_training_seed", Long.toString(snapshot.trainingSeed()));
            manifest.put("population_members", Integer.toString(populationMembers.size()));
            manifest.put("qd_archive_members", Integer.toString(qdMembers.size()));
            Map<String, ArchiveCandidate> unique = new LinkedHashMap<>();
            addMembers(unique, populationMembers);
            addMembers(unique, qdMembers);
            long memberships = (long) populationMembers.size() + qdMembers.size();
            costs.membershipInstanceRows = Math.multiplyExact(memberships, options.instances);
            costs.plannedSimulations = Math.multiplyExact((long) unique.size(), options.instances);
            costs.plannedJobs = Math.multiplyExact(costs.plannedSimulations,
                    (long) options.recordedJobs + options.warmupJobs);
            costs.uniquePairs = unique.size();
            costs.plannedCacheHits = memberships - unique.size();
            require(costs.membershipInstanceRows <= MAX_INSTANCE_ROWS,
                    "Per-member instance row budget exceeded: " + costs.membershipInstanceRows);
            require(costs.plannedSimulations <= MAX_SIMULATIONS,
                    "Simulation budget exceeded: " + costs.plannedSimulations);
            require(costs.plannedJobs <= MAX_RECORDED_PLUS_WARMUP_JOBS,
                    "Recorded-plus-warmup job budget exceeded: " + costs.plannedJobs);
            writeSeeds(output.resolve("validation-seeds.csv"), options);
            CommonDescriptor descriptor = new CommonDescriptor(costs);
            Map<String, Evaluation> cache = new LinkedHashMap<>();
            for (List<ArchiveCandidate> collection : Arrays.asList(populationMembers, qdMembers)) {
                for (ArchiveCandidate candidate : collection) {
                    String key = pairKey(candidate);
                    if (cache.containsKey(key)) { costs.cacheHits++; }
                    else { cache.put(key, evaluate(candidate, options, descriptor, costs)); }
                }
            }
            try (BufferedWriter members = create(output.resolve("members.csv"));
                 BufferedWriter instances = create(output.resolve("instance-outcomes.csv"));
                 BufferedWriter summary = create(output.resolve("collection-summary.csv"))) {
                row(members, "native_collection", "member_index", "source", "source_ordinal",
                        "tree_pair_sha256", "stored_training_fitness_PROVENANCE_NOT_COMPARABLE",
                        "validation_status", "finite_instances", "invalid_instances",
                        "common_validation_mean_ALL_instances", "mean_FINITE_instances_ONLY",
                        "median_FINITE_instances_ONLY", "post_hoc_descriptor_status",
                        "seq_axis", "routing_axis", "seq_reference_ranks", "routing_reference_ranks",
                        "descriptor_error", "sequencing_expression", "routing_expression");
                row(instances, "native_collection", "member_index", "source_ordinal", "tree_pair_sha256",
                        "instance_index", "seed", "status", "raw_objective", "throughput",
                        "completed_recorded_jobs", "clock_time", "error");
                row(summary, "native_collection", "members", "unique_expression_pairs",
                        "unique_sequencing_trees", "unique_routing_trees", "finite_ALL_instances_members",
                        "invalid_members", "finite_instance_memberships", "invalid_instance_memberships",
                        "mean_FINITE_COMPLETE_members_ONLY", "median_FINITE_COMPLETE_members_ONLY",
                        "best_FINITE_COMPLETE_member", "post_hoc_occupied_cells",
                        "post_hoc_coverage_fraction_of_6561", "invalid_descriptors");
                CollectionResult populationResult = exportCollection(POPULATION, populationMembers,
                        cache, options, members, instances, summary);
                CollectionResult qdResult = exportCollection(QD, qdMembers,
                        cache, options, members, instances, summary);
                writeGrid(output.resolve("post-hoc-common-grid.csv"), populationResult, qdResult);
            }
            complete = true;
        } catch (IOException | RuntimeException | Error problem) {
            failure = problem;
        } finally {
            try {
                manifest.put("population_sha256_after", hash(population));
                manifest.put("qd_archive_sha256_after", hash(qd));
                boolean unchanged = manifest.get("population_sha256_after")
                        .equals(manifest.get("population_sha256_before"))
                        && manifest.get("qd_archive_sha256_after")
                        .equals(manifest.get("qd_archive_sha256_before"));
                manifest.put("inputs_unchanged", Boolean.toString(unchanged));
                if (!unchanged) { throw new IOException("An input changed during comparison"); }
            } catch (IOException | RuntimeException integrityFailure) {
                complete = false;
                if (failure == null) { failure = integrityFailure; }
                else { failure.addSuppressed(integrityFailure); }
            }
            manifest.put("status", complete && failure == null ? "COMPLETE" : "FAILED");
            manifest.put("failure", failure == null ? "" : error(failure));
            manifest.putAll(costs.manifest());
            manifest.put("elapsed_nanoseconds", Long.toString(System.nanoTime() - start));
            try {
                writeManifest(output.resolve("manifest.csv"), manifest);
            } catch (IOException manifestFailure) {
                if (failure == null) { failure = manifestFailure; }
                else { failure.addSuppressed(manifestFailure); }
            }
        }
        if (failure instanceof IOException) { throw (IOException) failure; }
        if (failure instanceof RuntimeException) { throw (RuntimeException) failure; }
        if (failure instanceof Error) { throw (Error) failure; }
        return output;
    }

    private static void addMembers(Map<String, ArchiveCandidate> unique, List<ArchiveCandidate> members) {
        require(!members.isEmpty() && members.size() <= MAX_MEMBERS_PER_INPUT,
                "Each input must contain 1.." + MAX_MEMBERS_PER_INPUT + " members");
        for (ArchiveCandidate candidate : members) { unique.putIfAbsent(pairKey(candidate), candidate); }
    }

    private static String pairKey(ArchiveCandidate candidate) {
        return candidate.sequencing() + "\n" + candidate.routing();
    }

    private static Evaluation evaluate(ArchiveCandidate candidate, Options options,
                                       CommonDescriptor descriptor, Costs costs) {
        Evaluation result = new Evaluation();
        try {
            GPRule[] rules = candidate.rules();
            result.sequencingRanks = descriptor.sequencing.characterise(rules[0]);
            result.routingRanks = descriptor.routing.characterise(rules[1]);
            result.x = encodeRanks(result.sequencingRanks);
            result.y = encodeRanks(result.routingRanks);
        } catch (RuntimeException invalidDescriptor) {
            result.descriptorError = error(invalidDescriptor);
            costs.invalidDescriptors++;
        }
        for (int instance = 0; instance < options.instances; instance++) {
            long seed = Math.addExact(options.seed, instance);
            HeterogeneousSimulation simulation = null;
            Outcome outcome;
            costs.simulationsAttempted++;
            try {
                GPRule[] rules = candidate.rules();
                simulation = HeterogeneousSimulation.standardMissing(seed, rules[0], rules[1],
                        options.machines, options.recordedJobs, options.warmupJobs,
                        options.utilisation, options.dueFactor);
                simulation.rerun();
                costs.rerunsReturned++;
                double value = simulation.objectiveValue(options.objective);
                int completed = simulation.getSystemState().getJobsCompleted().size();
                double clock = simulation.getSystemState().getClockTime();
                boolean valid = validOutcome(value, simulation.getThroughput(), completed, clock,
                        options.recordedJobs)
                        && !simulation.getSystemState().getJobsCompleted().contains(null);
                outcome = new Outcome(seed, valid, Double.toString(value),
                        simulation.getThroughput(), completed, Double.toString(clock),
                        valid ? "" : "Incomplete schedule, invalid clock, or invalid raw objective");
            } catch (RuntimeException invalidRun) {
                outcome = new Outcome(seed, false, "",
                        simulation == null ? -1 : simulation.getThroughput(),
                        simulation == null ? -1 : simulation.getSystemState().getJobsCompleted().size(),
                        simulation == null ? "" : Double.toString(simulation.getSystemState().getClockTime()),
                        error(invalidRun));
            }
            result.outcomes.add(outcome);
            if (outcome.valid) { costs.finiteOutcomes++; }
            else { costs.invalidOutcomes++; }
        }
        return result;
    }

    static boolean validOutcome(double raw, int throughput, int completed, double clock, int expected) {
        return Double.isFinite(raw) && raw >= 0 && raw < Double.MAX_VALUE
                && throughput == expected && completed == expected
                && Double.isFinite(clock) && clock >= 0 && clock < Double.MAX_VALUE;
    }

    static int encodeRanks(int[] ranks) {
        require(ranks != null && ranks.length == 4, "Exactly four reference ranks are required");
        int cell = 0;
        for (int rank : ranks) {
            require(rank >= 1 && rank <= 3, "Reference ranks must be in 1..3");
            cell = cell * 3 + rank - 1;
        }
        return cell;
    }

    static int occupied(int[][] cells) {
        int count = 0;
        for (int[] column : cells) {
            for (int members : column) { if (members > 0) { count++; } }
        }
        return count;
    }

    private static final class CommonDescriptor {
        final PhenoCharacterisation sequencing;
        final PhenoCharacterisation routing;

        CommonDescriptor(Costs costs) {
            // The argument selects the heterogeneous branch; each factory makes its OWN run.
            HeterogeneousSimulation reference = HeterogeneousSimulation.standardMissing(0,
                    new WSPT(RuleType.SEQUENCING), new WIQ(RuleType.ROUTING), 10, 500, 0, .99, 4);
            costs.descriptorFactoriesAttempted++;
            sequencing = SequencingPhenoCharacterisation.defaultPhenoCharacterisation(reference, 3, 4);
            costs.descriptorFactoriesReturned++;
            costs.descriptorFactoriesAttempted++;
            routing = RoutingPhenoCharacterisation.defaultPhenoCharacterisation(reference, 3, 4);
            costs.descriptorFactoriesReturned++;
        }
    }

    private static final class Outcome {
        final long seed;
        final boolean valid;
        final String raw;
        final int throughput;
        final int completed;
        final String clock;
        final String failure;

        Outcome(long seed, boolean valid, String raw, int throughput, int completed,
                String clock, String failure) {
            this.seed = seed;
            this.valid = valid;
            this.raw = raw;
            this.throughput = throughput;
            this.completed = completed;
            this.clock = clock;
            this.failure = failure;
        }
    }

    private static final class Evaluation {
        final List<Outcome> outcomes = new ArrayList<>();
        int[] sequencingRanks;
        int[] routingRanks;
        int x = -1;
        int y = -1;
        String descriptorError = "";

        boolean mapped() { return descriptorError.isEmpty() && x >= 0 && y >= 0; }

        Stats finiteStats() {
            List<Double> values = new ArrayList<>();
            for (Outcome outcome : outcomes) {
                if (outcome.valid) { values.add(Double.parseDouble(outcome.raw)); }
            }
            return new Stats(values);
        }
    }

    static final class Stats {
        final int count;
        final double mean;
        final double median;
        final double best;

        Stats(List<Double> values) {
            List<Double> sorted = new ArrayList<>(values);
            for (double value : sorted) {
                require(Double.isFinite(value) && value >= 0 && value < Double.MAX_VALUE,
                        "Stats accept only finite, nonnegative, non-sentinel values");
            }
            Collections.sort(sorted);
            count = sorted.size();
            double average = 0;
            for (int i = 0; i < count; i++) { average += (sorted.get(i) - average) / (i + 1); }
            mean = count == 0 ? Double.NaN : average;
            median = count == 0 ? Double.NaN : count % 2 == 1 ? sorted.get(count / 2)
                    : sorted.get(count / 2 - 1) / 2 + sorted.get(count / 2) / 2;
            best = count == 0 ? Double.NaN : sorted.get(0);
        }
    }

    private static final class CollectionResult {
        final int[][] counts = new int[GRID_SIDE][GRID_SIDE];
        final int[][] finiteCounts = new int[GRID_SIDE][GRID_SIDE];
        final double[][] best = new double[GRID_SIDE][GRID_SIDE];

        CollectionResult() {
            for (double[] column : best) { Arrays.fill(column, Double.NaN); }
        }
    }

    private static CollectionResult exportCollection(String label, List<ArchiveCandidate> candidates,
            Map<String, Evaluation> cache, Options options, BufferedWriter members,
            BufferedWriter instances, BufferedWriter summary) throws IOException {
        CollectionResult grid = new CollectionResult();
        Set<String> pairs = new HashSet<>();
        Set<String> sequencing = new HashSet<>();
        Set<String> routing = new HashSet<>();
        List<Double> completeMeans = new ArrayList<>();
        long finiteInstances = 0;
        long invalidInstances = 0;
        int invalidDescriptors = 0;
        for (int index = 0; index < candidates.size(); index++) {
            ArchiveCandidate candidate = candidates.get(index);
            Evaluation evaluation = cache.get(pairKey(candidate));
            Stats stats = evaluation.finiteStats();
            boolean valid = stats.count == options.instances;
            pairs.add(pairKey(candidate));
            sequencing.add(candidate.sequencing());
            routing.add(candidate.routing());
            if (valid) { completeMeans.add(stats.mean); }
            finiteInstances += stats.count;
            invalidInstances += options.instances - stats.count;
            if (evaluation.mapped()) {
                int x = evaluation.x;
                int y = evaluation.y;
                grid.counts[x][y]++;
                if (valid) {
                    grid.finiteCounts[x][y]++;
                    if (Double.isNaN(grid.best[x][y]) || stats.mean < grid.best[x][y]) {
                        grid.best[x][y] = stats.mean;
                    }
                }
            } else { invalidDescriptors++; }
            row(members, label, index, candidate.source(), candidate.ordinal(), candidate.treeHash(),
                    candidate.storedFitness(), valid ? "VALID" : "INVALID", stats.count,
                    options.instances - stats.count, valid ? number(stats.mean) : "",
                    number(stats.mean), number(stats.median), evaluation.mapped() ? "VALID" : "INVALID",
                    evaluation.mapped() ? evaluation.x : "", evaluation.mapped() ? evaluation.y : "",
                    ranks(evaluation.sequencingRanks), ranks(evaluation.routingRanks),
                    evaluation.descriptorError, candidate.sequencing(), candidate.routing());
            for (int i = 0; i < evaluation.outcomes.size(); i++) {
                Outcome outcome = evaluation.outcomes.get(i);
                row(instances, label, index, candidate.ordinal(), candidate.treeHash(), i, outcome.seed,
                        outcome.valid ? "VALID" : "INVALID", outcome.raw, outcome.throughput,
                        outcome.completed, outcome.clock, outcome.failure);
            }
        }
        Stats stats = new Stats(completeMeans);
        int cells = occupied(grid.counts);
        row(summary, label, candidates.size(), pairs.size(), sequencing.size(), routing.size(),
                stats.count, candidates.size() - stats.count, finiteInstances, invalidInstances,
                number(stats.mean), number(stats.median), number(stats.best), cells,
                cells / (double) (GRID_SIDE * GRID_SIDE), invalidDescriptors);
        return grid;
    }

    private static void writeGrid(Path output, CollectionResult population, CollectionResult qd)
            throws IOException {
        try (BufferedWriter writer = create(output)) {
            row(writer, "post_hoc_seq_axis", "post_hoc_routing_axis", "actual_final_population_count",
                    "actual_final_population_finite_COMPLETE_members",
                    "actual_final_population_best_COMMON_validation_mean",
                    "actual_final_qd_gridmap_archive_count", "actual_final_qd_gridmap_finite_COMPLETE_members",
                    "actual_final_qd_gridmap_best_COMMON_validation_mean");
            for (int x = 0; x < GRID_SIDE; x++) {
                for (int y = 0; y < GRID_SIDE; y++) {
                    row(writer, x, y, population.counts[x][y], population.finiteCounts[x][y],
                            number(population.best[x][y]), qd.counts[x][y], qd.finiteCounts[x][y],
                            number(qd.best[x][y]));
                }
            }
        }
    }

    private static void writeSeeds(Path path, Options options) throws IOException {
        try (BufferedWriter writer = create(path)) {
            row(writer, "instance_index", "explicit_seed_for_BOTH_collections");
            for (int i = 0; i < options.instances; i++) { row(writer, i, Math.addExact(options.seed, i)); }
        }
    }

    static Path newOutput(Path requested, Path population, Path qd) throws IOException {
        Path resolved = LexicaseFiles.checkedOutput(requested, population.toRealPath().getParent());
        resolved = LexicaseFiles.checkedOutput(resolved, qd.toRealPath().getParent());
        require(Files.isDirectory(resolved.getParent()), "Output parent must already be a directory");
        return resolved;
    }

    private static String ranks(int[] values) { return values == null ? "" : Arrays.toString(values); }
    private static String number(double value) { return Double.isFinite(value) ? Double.toString(value) : ""; }
    private static String error(Throwable failure) {
        return failure.getClass().getName() + ": " + String.valueOf(failure.getMessage());
    }

    static String hash(Path path) throws IOException {
        return LexicaseFiles.hash(path);
    }

    private static void writeManifest(Path file, Map<String, String> values) throws IOException {
        try (BufferedWriter writer = create(file)) {
            row(writer, "key", "value");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                row(writer, entry.getKey(), entry.getValue());
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalArgumentException(message); }
    }

    private static final class Costs {
        long membershipInstanceRows;
        long plannedSimulations;
        long plannedJobs;
        long uniquePairs;
        long plannedCacheHits;
        long cacheHits;
        long simulationsAttempted;
        long rerunsReturned;
        long finiteOutcomes;
        long invalidOutcomes;
        long invalidDescriptors;
        int descriptorFactoriesAttempted;
        int descriptorFactoriesReturned;

        Map<String, String> manifest() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("planned_member_instance_rows_including_duplicates", Long.toString(membershipInstanceRows));
            values.put("planned_unique_pair_simulations", Long.toString(plannedSimulations));
            values.put("planned_unique_pair_recorded_plus_warmup_jobs", Long.toString(plannedJobs));
            values.put("unique_expression_pairs_across_BOTH_inputs", Long.toString(uniquePairs));
            values.put("planned_duplicate_member_cache_reuses", Long.toString(plannedCacheHits));
            values.put("member_cache_hits_including_cross_input_duplicates", Long.toString(cacheHits));
            values.put("scheduling_simulations_attempted", Long.toString(simulationsAttempted));
            values.put("scheduling_reruns_returned", Long.toString(rerunsReturned));
            values.put("unique_pair_instance_finite_outcomes", Long.toString(finiteOutcomes));
            values.put("unique_pair_instance_invalid_outcomes", Long.toString(invalidOutcomes));
            values.put("unique_pair_invalid_descriptors", Long.toString(invalidDescriptors));
            values.put("descriptor_reference_factories_attempted", Integer.toString(descriptorFactoriesAttempted));
            values.put("descriptor_reference_factories_returned", Integer.toString(descriptorFactoriesReturned));
            values.put("descriptor_reference_recorded_jobs_per_factory", "500");
            return values;
        }
    }

    static final class Options {
        Path population;
        Path qdArchive;
        Path outputRoot;
        Objective objective = Objective.MEAN_TARDINESS;
        double utilisation = .85;
        double dueFactor = 1.5;
        long seed = 718341;
        int instances = 5;
        int recordedJobs = 1000;
        int warmupJobs = 500;
        int machines = 10;

        static Options parse(String... args) {
            require(args != null && args.length % 2 == 0,
                    "Options require --name value pairs; see LexicasePopulationComparison JavaDoc");
            Options options = new Options();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < args.length; i += 2) {
                String key = args[i];
                String value = args[i + 1];
                require(key != null && value != null && !value.trim().isEmpty(),
                        "Empty option name/value");
                require(seen.add(key), "Duplicate option: " + key);
                switch (key) {
                    case "--population": options.population = Paths.get(value); break;
                    case "--qd-archive": options.qdArchive = Paths.get(value); break;
                    case "--output-root": options.outputRoot = Paths.get(value); break;
                    case "--objective":
                        require(value.equals("mean-tardiness") || value.equals("max-flowtime")
                                || value.equals("mean-weighted-flowtime"), "Unsupported objective: " + value);
                        options.objective = Objective.get(value);
                        break;
                    case "--utilisation": options.utilisation = decimal(key, value); break;
                    case "--due-factor": options.dueFactor = decimal(key, value); break;
                    case "--seed": options.seed = whole(key, value); break;
                    case "--instances": options.instances = count(key, value, 1, 10000); break;
                    case "--recorded-jobs": options.recordedJobs = count(key, value, 1, 1000000); break;
                    case "--warmup-jobs": options.warmupJobs = count(key, value, 0, 1000000); break;
                    case "--machines": options.machines = count(key, value, 1, 1000); break;
                    default: throw new IllegalArgumentException("Unknown option: " + key);
                }
            }
            require(options.population != null && options.qdArchive != null && options.outputRoot != null,
                    "Required: --population FILE --qd-archive FILE --output-root NEW_DIRECTORY");
            require(options.utilisation > 0 && options.utilisation < 1, "Utilisation must be in (0,1)");
            require(options.dueFactor > 0, "Due factor must be positive");
            require(options.seed >= 0, "Seed must be a nonnegative signed long");
            require(options.seed <= Long.MAX_VALUE - (options.instances - 1L), "Validation seed range overflows");
            return options;
        }

        private static long whole(String key, String value) {
            require(value.matches("[0-9]+"), key + " requires a nonnegative integer");
            try { return Long.parseLong(value); }
            catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(key + " is outside the signed-long range", invalid);
            }
        }

        private static int count(String key, String value, int minimum, int maximum) {
            long parsed = whole(key, value);
            require(parsed >= minimum && parsed <= maximum,
                    key + " must be in " + minimum + ".." + maximum);
            return (int) parsed;
        }

        private static double decimal(String key, String value) {
            double parsed;
            try { parsed = Double.parseDouble(value); }
            catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(key + " requires a finite number", invalid);
            }
            require(Double.isFinite(parsed), key + " requires a finite number");
            return parsed;
        }

        Map<String, String> manifest(Path population, Path qd, Path output) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("utility", LexicasePopulationComparison.class.getName());
            values.put("population", population.toString());
            values.put("qd_archive", qd.toString());
            values.put("output_root", output.toString());
            values.put("objective", objective.getName());
            values.put("objective_scale", "raw minimisation; never stored training fitness or normalised fitness");
            values.put("utilisation", Double.toString(utilisation));
            values.put("due_factor", Double.toString(dueFactor));
            values.put("seed", Long.toString(seed));
            values.put("seed_schedule", "seed+i; 0<=i<instances; same explicit seeds for both full collections");
            values.put("instances", Integer.toString(instances));
            values.put("recorded_jobs", Integer.toString(recordedJobs));
            values.put("warmup_jobs", Integer.toString(warmupJobs));
            values.put("machines", Integer.toString(machines));
            values.put("validation_simulation", "fresh HeterogeneousSimulation.standardMissing per unique expression pair and seed; rerun()");
            values.put("cache_key", "exact whitespace-normalised sequencing expression + newline + routing expression");
            values.put("native_outputs", "complete actual final population versus complete actual final QD GridMAP; not equivalent native structures");
            values.put("training_fitness", "provenance ONLY; not quality-comparable across rotating regimes");
            values.put("archive_policy", "no cumulative historical archive; no training feedback");
            values.put("common_grid", "POST-HOC EVALUATION ONLY; 81x81; count includes duplicate memberships");
            values.put("coverage", "occupied cells / 6561; includes mapped members with INVALID scheduling outcomes");
            values.put("grid_quality", "minimum raw COMMON validation mean over ALL instances of VALID members; blank if none");
            values.put("finite_statistics", "explicit finite-only statistics; failed members excluded from complete-member quality statistics, never replaced by zero");
            values.put("descriptor_encoding", "four reference ranks per role: ranks 1..3 -> digits 0..2; first rank most significant; base 3");
            values.put("descriptor_trajectories", "TWO INDEPENDENT factory-owned reference runs, not one validation trajectory");
            values.put("descriptor_sequencing", "HeterogeneousSimulation.standardFull; seed=0; machines=10; recorded=500; warmup=0; utilisation=0.99; due-factor=1.0; WSPT/WIQ");
            values.put("descriptor_routing", "HeterogeneousSimulation.standardMissing; seed=0; machines=10; recorded=500; warmup=0; utilisation=0.99; due-factor=4.0; WSPT/WIQ");
            values.put("descriptor_sampling", "existing defaultPhenoCharacterisation(referenceHeterogeneousSimulation,3,4); queue/options exactly 3; shuffle seed=8295342; first four");
            values.put("descriptor_marker_argument", "unexecuted standardMissing heterogeneous marker; factories construct their own simulations");
            values.put("bound_members_per_input", Integer.toString(MAX_MEMBERS_PER_INPUT));
            values.put("bound_member_instance_rows", Long.toString(MAX_INSTANCE_ROWS));
            values.put("bound_unique_pair_simulations", Long.toString(MAX_SIMULATIONS));
            values.put("bound_validation_recorded_plus_warmup_jobs", Long.toString(MAX_RECORDED_PLUS_WARMUP_JOBS));
            values.put("bound_counts", "instances=1..10000; recorded-jobs=1..1000000; warmup-jobs=0..1000000; machines=1..1000");
            values.put("completion_policy", "COMPLETE includes explicitly INVALID outcomes; FAILED means incomplete analysis, do not use partial results");
            values.put("blank_quality", "no eligible finite values; NEVER a zero imputation");
            return values;
        }
    }
}
