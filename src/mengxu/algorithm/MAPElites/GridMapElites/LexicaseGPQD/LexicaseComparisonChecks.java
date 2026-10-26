package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.multiobjective.MultiObjectiveFitness;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import yimei.jss.rule.operation.evolved.GPRule;

/**
 * Deterministic, isolated checks, not training. runPureChecks() has no I/O or
 * simulations. runChecks(existingRoot) additionally creates its own NEW child
 * directory and runs the bounded native-reader/export integration twice:
 * two unique pairs x two seeds x 24 recorded jobs, plus the two factory-owned
 * 500-job descriptor runs per comparison. Fixtures and reports remain below
 * the supplied root. CLI: LexicaseComparisonChecks --root NEW_DIRECTORY.
 */
public final class LexicaseComparisonChecks {
    private LexicaseComparisonChecks() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !"--root".equals(args[0])) {
            throw new IllegalArgumentException("Usage: LexicaseComparisonChecks --root NEW_DIRECTORY");
        }
        Path root = Paths.get(args[1]).toAbsolutePath().normalize();
        Files.createDirectory(root);
        runChecks(root);
        System.out.println("LEXICASE COMPARISON CHECKS PASSED: " + root);
    }

    public static void runPureChecks() {
        check(LexicasePopulationComparison.encodeRanks(new int[]{1, 1, 1, 1}) == 0, "Base-three origin");
        check(LexicasePopulationComparison.encodeRanks(new int[]{3, 3, 3, 3}) == 80, "Base-three upper bound");
        check(LexicasePopulationComparison.encodeRanks(new int[]{1, 2, 3, 1}) == 15,
                "First rank is the most significant digit");
        expectIllegal(() -> LexicasePopulationComparison.encodeRanks(new int[]{0, 1, 1, 1}));
        expectIllegal(() -> LexicasePopulationComparison.encodeRanks(new int[]{4, 1, 1, 1}));
        expectIllegal(() -> LexicasePopulationComparison.encodeRanks(new int[]{1, 1, 1}));
        expectIllegal(() -> LexicasePopulationComparison.encodeRanks(null));
        int[][] counts = new int[81][81];
        counts[0][0] = 11;
        counts[80][80] = 2;
        check(LexicasePopulationComparison.occupied(counts) == 2, "Coverage counts distinct cells, not 13 members");
        LexicasePopulationComparison.Stats stats =
                new LexicasePopulationComparison.Stats(Arrays.asList(8.0, 2.0, 4.0, 6.0));
        check(stats.count == 4 && stats.mean == 5 && stats.median == 5 && stats.best == 2, "Finite statistics");
        check(Double.isNaN(new LexicasePopulationComparison.Stats(new ArrayList<Double>()).mean),
                "No finite observations is not zero");
        expectIllegal(() -> new LexicasePopulationComparison.Stats(Arrays.asList(Double.POSITIVE_INFINITY)));
        expectIllegal(() -> new LexicasePopulationComparison.Stats(Arrays.asList(Double.MAX_VALUE)));
        check(LexicasePopulationComparison.validOutcome(0, 24, 24, 100, 24), "Genuine zero objective is valid");
        check(!LexicasePopulationComparison.validOutcome(0, 23, 24, 100, 24),
                "Incomplete throughput cannot masquerade as zero objective");
        check(!LexicasePopulationComparison.validOutcome(5, 24, 23, 100, 24), "Incomplete recorded jobs");
        check(!LexicasePopulationComparison.validOutcome(Double.NaN, 24, 24, 100, 24), "NaN is invalid");
        check(!LexicasePopulationComparison.validOutcome(Double.POSITIVE_INFINITY, 24, 24, 100, 24),
                "Infinite raw objective is invalid");
        check(!LexicasePopulationComparison.validOutcome(Double.MAX_VALUE, 24, 24, 100, 24),
                "Sentinel raw objective is invalid");
        check(!LexicasePopulationComparison.validOutcome(-1, 24, 24, 100, 24), "Negative raw objective");
        check(!LexicasePopulationComparison.validOutcome(0, 24, 24, Double.MAX_VALUE, 24), "Failed clock sentinel");
        check(!LexicasePopulationComparison.validOutcome(0, 24, 24, Double.NaN, 24), "NaN clock is invalid");
        String[] base = {"--population", "population.stat", "--qd-archive", "qd.stat",
                "--output-root", "new-output"};
        expectIllegal(() -> LexicasePopulationComparison.Options.parse());
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--unknown", "1")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--seed")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--seed", "-1")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--seed", "1.0")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--seed", "9223372036854775808")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base,
                "--seed", Long.toString(Long.MAX_VALUE), "--instances", "2")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base,
                "--seed", "1", "--seed", "2")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--instances", "0")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--instances", "10001")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--recorded-jobs", "0")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--warmup-jobs", "-1")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--machines", "0")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--utilisation", "NaN")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--utilisation", "1")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--due-factor", "Infinity")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--due-factor", "0")));
        expectIllegal(() -> LexicasePopulationComparison.Options.parse(join(base, "--objective", "makespan")));
        check(LexicasePopulationComparison.Options.parse(join(base, "--seed", Long.toString(Long.MAX_VALUE),
                "--instances", "1")).seed == Long.MAX_VALUE, "Last representable single seed");
        for (String objective : Arrays.asList("mean-tardiness", "max-flowtime", "mean-weighted-flowtime")) {
            check(LexicasePopulationComparison.Options.parse(join(base, "--objective", objective))
                    .objective.getName().equals(objective), "Supported raw objective");
        }
    }

    public static void runChecks(Path root) throws IOException {
        runPureChecks();
        check(Files.isDirectory(root), "Harness root must already be a directory");
        String help = usageOutput("--help");
        check(help.startsWith("Usage: LexicasePopulationComparison"), "Standalone CLI help returns usage");
        check(help.equals(usageOutput()), "No CLI arguments returns the same usage without requiring inputs");
        for (String documented : Arrays.asList("--population FILE", "--qd-archive FILE",
                "--output-root NEW_DIRECTORY", "--objective mean-tardiness",
                "mean-tardiness|max-flowtime|mean-weighted-flowtime", "--utilisation 0.85",
                "--due-factor 1.5", "--seed 718341", "--instances 5", "--recorded-jobs 1000",
                "--warmup-jobs 500", "--machines 10", "manifest.csv", "members.csv",
                "instance-outcomes.csv", "collection-summary.csv", "post-hoc-common-grid.csv",
                "validation-seeds.csv", "POST-HOC")) {
            check(help.contains(documented), "Usage documents option/default/output: " + documented);
        }
        Path work = Files.createDirectory(root.resolve("lexicase-comparison-checks"));
        Path populationDirectory = Files.createDirectory(work.resolve("population-input"));
        Path qdDirectory = Files.createDirectory(work.resolve("qd-input"));
        Path population = populationDirectory.resolve("final_population.stat");
        Path qd = qdDirectory.resolve("job.0.out.stat");
        Individual a = individual(population, "PT", "WIQ", 9000000);
        Individual b = individual(population, "(/ PT W)", "WIQ", 8000000);
        PopulationSnapshots.write(population, "Lexicase-GPf", 0, 2, 37, new Individual[]{a, a, b});
        write(qd, "Generation-best text is not the archive.\nAll individuals in GridMAP:\nGridMAP number: 2\n"
                + qdMember("PT", "WIQ", 7) + qdMember("(/ PT W)", "WIQ", 11));
        check(PopulationSnapshots.read(population).candidates().size() == 3, "Native population fixture");
        String populationHash = LexicasePopulationComparison.hash(population);
        String qdHash = LexicasePopulationComparison.hash(qd);
        Path output = work.resolve("comparison");
        LexicasePopulationComparison.run(arguments(population, qd, output));
        Map<String, String> manifest = manifest(output.resolve("manifest.csv"));
        check("COMPLETE".equals(manifest.get("status")), "Explicit complete manifest");
        check("true".equals(manifest.get("inputs_unchanged")), "Input immutability assertion");
        check("3".equals(manifest.get("population_members")), "Full population membership, including duplicate");
        check("2".equals(manifest.get("qd_archive_members")), "Full true QD archive membership");
        check("2".equals(manifest.get("unique_expression_pairs_across_BOTH_inputs")), "Cross-input pair cache");
        check("3".equals(manifest.get("member_cache_hits_including_cross_input_duplicates")), "Duplicate cache hits");
        check("4".equals(manifest.get("scheduling_simulations_attempted")), "Only two pairs times two explicit seeds");
        check(Long.parseLong(manifest.get("unique_pair_instance_finite_outcomes"))
                + Long.parseLong(manifest.get("unique_pair_instance_invalid_outcomes")) == 4,
                "Every unique scheduling attempt has a finite or invalid outcome");
        check("10".equals(manifest.get("planned_member_instance_rows_including_duplicates")),
                "All ten instance memberships retained");
        check("2".equals(manifest.get("descriptor_reference_factories_returned")), "Two independent reference factories");
        check(manifest.get("descriptor_sequencing").contains("standardFull")
                && manifest.get("descriptor_sequencing").contains("due-factor=1.0")
                && manifest.get("descriptor_routing").contains("standardMissing")
                && manifest.get("descriptor_routing").contains("due-factor=4.0"), "Honest reference settings");
        check("2".equals(manifest.get("population_generation"))
                && "37".equals(manifest.get("population_training_seed")), "Population metadata provenance");
        List<Map<String, String>> members = records(output.resolve("members.csv"));
        List<Map<String, String>> outcomes = records(output.resolve("instance-outcomes.csv"));
        List<Map<String, String>> summaries = records(output.resolve("collection-summary.csv"));
        List<Map<String, String>> cells = records(output.resolve("post-hoc-common-grid.csv"));
        check(members.size() == 5 && outcomes.size() == 10 && summaries.size() == 2 && cells.size() == 6561,
                "Complete exported member, instance, collection and cell counts");
        long reportedInstanceMemberships = 0;
        for (Map<String, String> summary : summaries) {
            reportedInstanceMemberships += Long.parseLong(summary.get("finite_instance_memberships"))
                    + Long.parseLong(summary.get("invalid_instance_memberships"));
            check(Integer.parseInt(summary.get("finite_ALL_instances_members"))
                    + Integer.parseInt(summary.get("invalid_members"))
                    == Integer.parseInt(summary.get("members")), "Finite and failed member accounting");
        }
        check(reportedInstanceMemberships == 10, "No finite or failed memberships silently omitted");
        for (Map<String, String> member : members) {
            int finite = Integer.parseInt(member.get("finite_instances"));
            int invalid = Integer.parseInt(member.get("invalid_instances"));
            check(finite + invalid == 2, "Every member retains both requested instances");
            if (member.get("validation_status").equals("INVALID")) {
                check(invalid > 0 && member.get("common_validation_mean_ALL_instances").isEmpty(),
                        "Failed member cannot acquire an imputed complete mean");
            } else {
                check(finite == 2 && Double.isFinite(Double.parseDouble(
                        member.get("common_validation_mean_ALL_instances"))), "Valid member has finite common quality");
            }
        }
        check(members.get(0).get("common_validation_mean_ALL_instances")
                .equals(members.get(1).get("common_validation_mean_ALL_instances"))
                && members.get(0).get("common_validation_mean_ALL_instances")
                .equals(members.get(3).get("common_validation_mean_ALL_instances")), "Identical pair reuses raw quality");
        check(!members.get(0).get("stored_training_fitness_PROVENANCE_NOT_COMPARABLE")
                .equals(members.get(3).get("stored_training_fitness_PROVENANCE_NOT_COMPARABLE")),
                "Different stored fitness remains provenance only");
        int populationSum = 0;
        int qdSum = 0;
        int populationCells = 0;
        int qdCells = 0;
        for (Map<String, String> cell : cells) {
            int populationCount = Integer.parseInt(cell.get("actual_final_population_count"));
            int qdCount = Integer.parseInt(cell.get("actual_final_qd_gridmap_archive_count"));
            populationSum += populationCount;
            qdSum += qdCount;
            if (populationCount > 0) { populationCells++; }
            if (qdCount > 0) { qdCells++; }
        }
        check(populationSum == 3 && qdSum == 2, "Post-hoc cells retain all duplicate memberships");
        check(populationCells == Integer.parseInt(summaries.get(0).get("post_hoc_occupied_cells"))
                && qdCells == Integer.parseInt(summaries.get(1).get("post_hoc_occupied_cells")),
                "Reported coverage is distinct-cell counting");
        check(populationCells <= 2 && populationCells == qdCells, "Shared pairs have the same common grid");
        check(Double.parseDouble(summaries.get(0).get("post_hoc_coverage_fraction_of_6561"))
                == populationCells / 6561.0, "Coverage denominator is the common 81x81 grid");
        check(LexicasePopulationComparison.POPULATION.equals(summaries.get(0).get("native_collection"))
                && LexicasePopulationComparison.QD.equals(summaries.get(1).get("native_collection")),
                "Native population and QD map labels are not conflated");
        for (Map<String, String> outcome : outcomes) {
            String status = outcome.get("status");
            check(status.equals("VALID") || status.equals("INVALID"), "Every outcome is explicitly classified");
            check(Long.parseLong(outcome.get("seed"))
                    == 718341L + Integer.parseInt(outcome.get("instance_index")), "Explicit seed schedule");
            if (status.equals("INVALID")) {
                check(!outcome.get("error").isEmpty(), "Failure reason is not omitted");
            } else {
                check(Double.isFinite(Double.parseDouble(outcome.get("raw_objective")))
                        && "24".equals(outcome.get("throughput"))
                        && "24".equals(outcome.get("completed_recorded_jobs")), "Finite complete scheduling outcome");
            }
        }
        Path repeat = work.resolve("repeat-comparison");
        LexicasePopulationComparison.run(arguments(population, qd, repeat));
        for (String file : Arrays.asList("members.csv", "instance-outcomes.csv", "collection-summary.csv",
                "post-hoc-common-grid.csv", "validation-seeds.csv")) {
            check(Arrays.equals(Files.readAllBytes(output.resolve(file)), Files.readAllBytes(repeat.resolve(file))),
                    "Deterministic repeat: " + file);
        }
        String manifestHash = LexicasePopulationComparison.hash(output.resolve("manifest.csv"));
        expectIllegal(() -> LexicasePopulationComparison.run(arguments(population, qd, output)));
        check(manifestHash.equals(LexicasePopulationComparison.hash(output.resolve("manifest.csv"))),
                "No overwrite of an existing comparison");
        Path populationForbidden = populationDirectory.resolve("forbidden-output");
        Path qdForbidden = qdDirectory.resolve("forbidden-output");
        expectIllegal(() -> LexicasePopulationComparison.run(arguments(population, qd, populationForbidden)));
        expectIllegal(() -> LexicasePopulationComparison.run(arguments(population, qd, qdForbidden)));
        check(!Files.exists(populationForbidden) && !Files.exists(qdForbidden), "No output inside either input parent");
        Path invalidSeedOutput = work.resolve("invalid-seed");
        expectIllegal(() -> LexicasePopulationComparison.run(join(required(population, qd, invalidSeedOutput),
                "--seed", Long.toString(Long.MAX_VALUE), "--instances", "2")));
        check(!Files.exists(invalidSeedOutput), "Invalid seed rejected before any output writes");
        Path malformedDirectory = Files.createDirectory(work.resolve("malformed-input"));
        Path malformed = malformedDirectory.resolve("not-a-population.stat");
        write(malformed, "All individuals in GridMAP:\nGridMAP number: 1\n" + qdMember("PT", "WIQ", 2));
        Path failedOutput = work.resolve("failed-comparison");
        try {
            LexicasePopulationComparison.run(arguments(malformed, qd, failedOutput));
            throw new AssertionError("A QD archive must not be accepted as a native population");
        } catch (IOException expected) {
            Map<String, String> failed = manifest(failedOutput.resolve("manifest.csv"));
            check("FAILED".equals(failed.get("status")) && !failed.get("failure").isEmpty(),
                    "Explicit failed manifest for a rejected native input");
            check("0".equals(failed.get("scheduling_simulations_attempted")), "No hidden evaluation on reader failure");
        }
        check(populationHash.equals(LexicasePopulationComparison.hash(population))
                && qdHash.equals(LexicasePopulationComparison.hash(qd)), "Both inputs remain byte-identical");
    }

    private static Individual individual(Path source, String sequencing, String routing, double fitness) {
        GPRule[] rules = new ArchiveCandidate(source, 0, fitness, sequencing, routing).rules();
        GPIndividual individual = new GPIndividual();
        individual.trees = new GPTree[]{rules[0].getGPTree(), rules[1].getGPTree()};
        for (GPTree tree : individual.trees) { tree.owner = individual; }
        MultiObjectiveFitness nativeFitness = new MultiObjectiveFitness();
        nativeFitness.setObjectives(null, new double[]{fitness});
        individual.fitness = nativeFitness;
        individual.evaluated = true;
        return individual;
    }

    private static String qdMember(String sequencing, String routing, double fitness) {
        return "Evaluated: true\nFitness: [" + fitness + "]\nTree 0:\n"
                + sequencing + "\nTree 1:\n" + routing + "\n";
    }

    private static String[] required(Path population, Path qd, Path output) {
        return new String[]{"--population", population.toString(), "--qd-archive", qd.toString(),
                "--output-root", output.toString()};
    }

    private static String[] arguments(Path population, Path qd, Path output) {
        return join(required(population, qd, output), "--instances", "2", "--recorded-jobs", "24",
                "--warmup-jobs", "0", "--machines", "3", "--utilisation", ".5");
    }

    private static String[] join(String[] first, String... rest) {
        String[] result = Arrays.copyOf(first, first.length + rest.length);
        System.arraycopy(rest, 0, result, first.length, rest.length);
        return result;
    }

    private static String usageOutput(String... args) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try (PrintStream capture = new PrintStream(bytes, false, StandardCharsets.UTF_8.name())) {
            System.setOut(capture);
            try {
                LexicasePopulationComparison.main(args);
            } finally {
                System.setOut(original);
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void write(Path path, String text) throws IOException {
        Files.write(path, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
    }

    private static Map<String, String> manifest(Path file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, String> row : records(file)) {
            check(result.put(row.get("key"), row.get("value")) == null, "Unique manifest keys");
        }
        return result;
    }

    private static List<Map<String, String>> records(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        check(!lines.isEmpty(), "CSV has a header: " + file);
        List<String> header = csvLine(lines.get(0));
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = csvLine(lines.get(i));
            check(fields.size() == header.size(), "CSV column count: " + file + ":" + (i + 1));
            Map<String, String> record = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) {
                record.put(header.get(column), fields.get(column));
            }
            result.add(record);
        }
        return result;
    }

    private static List<String> csvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else { quoted = !quoted; }
            } else if (ch == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else { field.append(ch); }
        }
        check(!quoted, "Fixture CSV has balanced quotes");
        fields.add(field.toString());
        return fields;
    }

    private interface CheckedAction { void run() throws IOException; }

    private static void expectIllegal(CheckedAction action) {
        try {
            action.run();
            throw new AssertionError("Expected strict argument/path validation failure");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage() != null, "Validation failure has a diagnostic");
        } catch (IOException unexpected) {
            throw new AssertionError("Expected validation, not an I/O failure", unexpected);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
