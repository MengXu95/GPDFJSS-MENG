package mengxu.ruleanalysis.MPSLGP;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.*;

public final class MPSLGPTestSummaryMainTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        MPSLGPPaperTestMain.Config defaults = MPSLGPTestSummaryMain.selection(new String[]{"paper-tests", "summary"});
        check(defaults.runs == 5 && defaults.startRun == 0
                && defaults.scenarios.equals(Arrays.asList("R1", "H3")), "preserve five-seed selection");
        check(defaults.algorithms.equals(Arrays.asList("full")) && defaults.indicators.equals(Arrays.asList("IGD"))
                && defaults.preferences == 200 && defaults.replications == 30 && !defaults.smoke, "formal Full protocol");
        check(defaults.training.endsWith("paper-runs"), "default sibling training root");
        check(MPSLGPTestSummaryMain.invalidOutcomePolicy(new String[]{"paper-tests", "summary"}).equals("reject"),
                "invalid outcomes rejected unless explicitly authorized");
        MPSLGPPaperTestMain.Config full = MPSLGPTestSummaryMain.selection(new String[]{"paper-tests", "summary",
                "--scenarios", "R1,R2,R3,R4,R5,H1,H2,H3", "--runs", "30", "--start-run", "0"});
        check(full.scenarios.size() == 8 && full.runs == 30 && full.startRun == 0, "complete paper selection");
        MPSLGPPaperTestMain.Config subset = MPSLGPTestSummaryMain.selection(new String[]{"paper-tests", "summary",
                "--scenarios", "R2,H2", "--runs", "3", "--start-run", "10", "--training-root", "custom-training"});
        check(subset.training.endsWith("custom-training") && subset.runs == 3 && subset.startRun == 10,
                "explicit source and shifted seed range");
        for (String[] invalid : Arrays.asList(new String[0], new String[]{"paper-tests"},
                new String[]{"paper-tests", "summary", "--runs", "1"},
                new String[]{"paper-tests", "summary", "--runs", "0"},
                new String[]{"paper-tests", "summary", "--runs"},
                new String[]{"paper-tests", "summary", "--runs", "3", "--runs", "5"},
                new String[]{"paper-tests", "summary", "--scenarios", "R1,R1"},
                new String[]{"paper-tests", "summary", "--scenarios", "R6"},
                new String[]{"paper-tests", "summary", "--smoke"},
                new String[]{"paper-tests", "summary", "--algorithms", "no-transfer"},
                new String[]{"paper-tests", "summary", "--invalid-outcomes", "ignore"},
                new String[]{"paper-tests", "summary", "--invalid-outcomes"},
                new String[]{"paper-tests", "summary", "--invalid-outcomes", "valid-only", "--invalid-outcomes", "reject"},
                new String[]{"paper-tests", "summary", "--start-run", "-1"},
                new String[]{"paper-tests", "summary", "--start-run", "2147483647", "--runs", "30"})) {
            boolean rejected = false;
            try { MPSLGPTestSummaryMain.selection(invalid); }
            catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "reject " + Arrays.toString(invalid));
        }
        Path temp = Files.createTempDirectory("mpslgp-summary-test-");
        Path marker = temp.resolve("completed.properties");
        try {
            Files.writeString(marker, "status=COMPLETED\n");
            MPSLGPTestSummaryMain.requireCompletions(Arrays.asList(marker));
            boolean rejected = false;
            try {
                MPSLGPTestSummaryMain.requireCompletions(Arrays.asList(marker, temp.resolve("missing1"),
                        temp.resolve("missing2")));
            } catch (IOException e) {
                rejected = e.getMessage().contains("2 of 3") && e.getMessage().contains("No summary was written");
            }
            check(rejected, "report complete missing count without writing partial results");
        } finally {
            Files.deleteIfExists(marker);
            Files.delete(temp);
        }
        testFullSummary();
        MPSLGPTestSummaryMain.main(new String[]{"--self-test"});
        System.out.println("PASS: MPSLGPTestSummaryMainTest (" + assertions + " assertions)");
    }

    private static void testFullSummary() throws Exception {
        Path temp = Files.createTempDirectory("mpslgp-summary-full-fixture-");
        try {
            Path training = temp.resolve("paper-runs"), tests = temp.resolve("paper-tests"), output = temp.resolve("summary");
            String[] groups = {"R1", "R2", "R3", "R4", "R5", "H1", "H2", "H3"};
            int[][] targets = {{1,2}, {3,4}, {6,7}, {1,3,5}, {8,9}, {1,10}, {5,6}, {1,5,8}};
            for (int group = 0; group < groups.length; group++) {
                for (int seed = 0; seed < 30; seed++) { trainingFixture(training, groups[group], targets[group], seed); }
            }
            String[] args = {tests.toString(), output.toString(), "--scenarios", String.join(",", groups), "--runs", "30"};
            List<MPSLGPPaperTestMain.Job> jobs = MPSLGPPaperTestMain.plan(MPSLGPTestSummaryMain.selection(args));
            check(jobs.size() == 540, "18 group/task targets times 30 seeds");
            for (MPSLGPPaperTestMain.Job job : jobs) { testFixture(job); }
            MPSLGPTestSummaryMain.main(args);
            check(Files.readAllLines(output.resolve("per-run.csv")).size() == 541, "540 per-run rows");
            for (String file : Arrays.asList("mean-std.csv", "table-vii-mpslgp.csv", "table-viii.csv")) {
                List<String> rows = Files.readAllLines(output.resolve(file));
                check(rows.size() == 19, "18 summary rows in " + file);
                String[] header = rows.get(0).split(",");
                int runColumn = Arrays.asList(header).indexOf("Runs");
                for (String row : rows.subList(1, rows.size())) {
                    check(row.split(",")[runColumn].equals("30"), "30 seeds per row in " + file);
                }
            }
            Properties protocol = readProperties(output.resolve("protocol.properties"));
            check(protocol.getProperty("task.runs").equals("540")
                    && protocol.getProperty("preference.rows").equals("108000"), "full protocol counts");
            for (String row : Files.readAllLines(output.resolve("table-viii.csv")).subList(1, 19)) {
                String[] fields = row.split(",");
                check(Double.parseDouble(fields[3]) == 200 && Double.parseDouble(fields[4]) == 0
                        && Double.parseDouble(fields[5]) == 200 && Double.parseDouble(fields[6]) == 0
                        && Double.parseDouble(fields[7]) == 3 && Double.parseDouble(fields[9]) == 3,
                        "known preference responses and tree sizes");
            }
            boolean rejected = false;
            try { MPSLGPTestSummaryMain.main(args); }
            catch (IOException e) { rejected = e.getMessage().contains("Use a new output directory"); }
            check(rejected, "never overwrite an existing reference pool");
            testFixture(jobs.get(0), true);
            String[] invalidArgs = args.clone();
            Path invalidOutput = temp.resolve("summary-with-invalid-fitness");
            invalidArgs[1] = invalidOutput.toString();
            rejected = false;
            try { MPSLGPTestSummaryMain.main(invalidArgs); }
            catch (IOException e) { rejected = e.getMessage().contains("-1 invalid-fitness sentinel"); }
            check(rejected && !Files.exists(invalidOutput),
                    "sentinel cannot enter normalization or quality indicators and no partial summary is written");
            String[] conditional = Arrays.copyOf(invalidArgs, invalidArgs.length + 2);
            Path conditionalOutput = temp.resolve("conditional-summary");
            conditional[1] = conditionalOutput.toString();
            conditional[conditional.length - 2] = "--invalid-outcomes";
            conditional[conditional.length - 1] = "valid-only";
            MPSLGPTestSummaryMain.main(conditional);
            check(Files.readAllLines(conditionalOutput.resolve("per-run.csv")).size() == 541,
                    "valid-only policy retains all seeds");
            Properties conditionalProtocol = readProperties(conditionalOutput.resolve("protocol.properties"));
            check(conditionalProtocol.getProperty("invalid.preference.rows").equals("1")
                    && conditionalProtocol.getProperty("valid.preference.rows").equals("107999"), "conditional valid and invalid totals");
            check(Files.readAllLines(conditionalOutput.resolve("invalid-preferences.csv")).size() == 2,
                    "every excluded preference is audited");
            String[] first = Files.readAllLines(conditionalOutput.resolve("per-run.csv")).get(1).split(",");
            check(Double.parseDouble(first[7]) == 199 && Double.parseDouble(first[9]) >= 0
                    && Double.parseDouble(first[11]) >= 0, "sentinel is excluded from outcome counts and objective minima");
            check(Files.readAllLines(conditionalOutput.resolve("validity-summary.csv")).size() == 19,
                    "conditional validity summary has all 18 targets");
            testFixture(jobs.get(0), 200);
            Path allInvalidOutput = temp.resolve("all-invalid-summary");
            conditional[1] = allInvalidOutput.toString();
            rejected = false;
            try { MPSLGPTestSummaryMain.main(conditional); }
            catch (IOException e) { rejected = e.getMessage().contains("No valid preferences"); }
            check(rejected && !Files.exists(allInvalidOutput), "valid-only policy never silently drops an all-invalid seed");
        } finally {
            try (Stream<Path> paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) { Files.delete(path); }
            }
        }
    }

    static void trainingFixture(Path root, String group, int[] targets, int seed) throws IOException {
        Path run = root.resolve("full").resolve("IGD").resolve(group).resolve(String.format("run%02d", seed));
        Path attempt = run.resolve("attempt-0001");
        Files.createDirectories(attempt);
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("seed.0", Integer.toString(seed)); parameters.put("compare-criteria", "IGD");
        parameters.put("terminal-normalisation", "2"); parameters.put("num-trees", "2");
        parameters.put("generations", "50"); parameters.put("pop.subpop.0.size", "1000");
        parameters.put("mpslgp.num-tasks", Integer.toString(targets.length));
        parameters.put("mpslgp.adaptive-transfer", "true"); parameters.put("mpslgp.use-no-transfer-gate", "true");
        StringBuilder stats = new StringBuilder("Generation: 49\nTop N individuals by MPSLGP Task:\n\n");
        for (int task = 0; task < targets.length; task++) {
            int target = targets[task];
            String key = "eval.problem." + task + ".eval-model";
            parameters.put(key + ".objectives", "2");
            parameters.put(key + ".objectives.0", target <= 5 ? "max-flowtime" : "max-weighted-flowtime");
            parameters.put(key + ".objectives.1", target <= 5 ? "max-weighted-tardiness" : "max-tardiness");
            parameters.put(key + ".sim-models.0.util-level", Double.toString(.70 + ((target - 1) % 5) * .05));
            parameters.put(key + ".sim-models.0.due-date-factor", "1.5");
            stats.append("MPSLGP Task ").append(task).append(" Top 1 individuals:\n")
                    .append("Evaluated: true\nFitness: [1.0 2.0]\nTree 0:\n(+ PT Pone)\nTree 1:\n(+ WIQ Ptwo)\n\n");
        }
        stats.append("PARETO FRONTS BY MPSLGP TASK\n");
        atomicWrite(attempt.resolve("effective.params"), serialize(parameters));
        atomicWrite(attempt.resolve("job." + seed + ".out.stat"), stats.toString());
        Map<String, String> metadata = new TreeMap<>();
        metadata.put("algorithm", "full"); metadata.put("indicator", "IGD"); metadata.put("scenario", group);
        metadata.put("run", Integer.toString(seed)); metadata.put("configuration.sha256", "synthetic-test-fixture");
        atomicWrite(run.resolve("run.properties"), serialize(metadata));
        Map<String, String> completion = new TreeMap<>();
        completion.put("status", "COMPLETED"); completion.put("exit.code", "0"); completion.put("attempt", "attempt-0001");
        completion.put("configuration.sha256", "synthetic-test-fixture");
        completion.put("duration.seconds", Integer.toString((seed + 1) * 60));
        completion.put("sha256.effective.params", fileHash(attempt.resolve("effective.params")));
        completion.put("sha256.job." + seed + ".out.stat", fileHash(attempt.resolve("job." + seed + ".out.stat")));
        atomicWrite(run.resolve("completed.properties"), serialize(completion));
    }

    private static void testFixture(MPSLGPPaperTestMain.Job job) throws IOException {
        testFixture(job, false);
    }

    private static void testFixture(MPSLGPPaperTestMain.Job job, boolean invalid) throws IOException {
        testFixture(job, invalid ? 1 : 0);
    }

    static void testFixture(MPSLGPPaperTestMain.Job job, int invalidCount) throws IOException {
        Path attempt = job.directory.resolve("attempt-0001");
        Files.createDirectories(attempt);
        atomicWrite(attempt.resolve("test.properties"), job.serialized);
        atomicWrite(attempt.resolve("selected-heuristic.properties"), "selection=synthetic-test-fixture\n");
        atomicWrite(attempt.resolve("testing.log"), "Synthetic fixture; no simulations performed.\n");
        StringBuilder rows = new StringBuilder(MPSLGPTaskEvaluator.CSV_HEADER).append('\n');
        StringBuilder details = new StringBuilder(MPSLGPTaskEvaluator.PREFERENCE_HEADER).append('\n');
        for (int i = 0; i < 200; i++) {
            double first = i / 199.0, second = (199 - i) / 199.0;
            double objective0 = job.run + first, objective1 = job.run + second;
            if (i < invalidCount) { objective0 = objective1 = -1; }
            rows.append(job.run).append(",49,3,2,3,2,2,1.0,").append(objective0)
                    .append(",2.0,").append(objective1).append('\n');
            details.append(i).append(',').append(first).append(',').append(second).append(',')
                    .append(objective0).append(',').append(objective1).append('\n');
        }
        atomicWrite(attempt.resolve(job.csvName), rows.toString());
        atomicWrite(attempt.resolve("preference-results.csv"), details.toString());
        Map<String, String> completion = new TreeMap<>();
        completion.put("status", "COMPLETED"); completion.put("attempt", "attempt-0001");
        completion.put("configuration.sha256", job.fingerprint); completion.put("duration.seconds", "1");
        for (String file : Arrays.asList("test.properties", "selected-heuristic.properties", job.csvName,
                "preference-results.csv", "testing.log")) {
            completion.put("sha256." + file, fileHash(attempt.resolve(file)));
        }
        atomicWrite(job.directory.resolve("completed.properties"), serialize(completion));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new AssertionError(message); }
    }
}
