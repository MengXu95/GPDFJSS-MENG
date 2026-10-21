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

public final class MPSLGPTransferAblationMainTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        MPSLGPTransferAblationMain.Config defaults = MPSLGPTransferAblationMain.Config.parse(new String[0]);
        check(defaults.scope().algorithms.equals(Arrays.asList("full", "no-transfer"))
                && defaults.scope().runs == 30 && defaults.scope().scenarios.size() == 8, "full formal comparison defaults");
        check(defaults.validOnly, "established conditional valid-preference protocol");
        MPSLGPTransferAblationMain.Config three = MPSLGPTransferAblationMain.Config.parse(
                new String[]{"--algorithms", "full,no-transfer,fixed-transfer"});
        check(three.scope().algorithms.size() == 3
                && three.output.endsWith("full-vs-no-vs-fixed-transfer-thirty-seeds"), "separate three-method scope/output");
        for (String[] invalid : Arrays.asList(new String[]{"--runs", "1"}, new String[]{"--runs", "61"},
                new String[]{"--runs"}, new String[]{"--runs", "3", "--runs", "4"},
                new String[]{"--scenarios", "R6"}, new String[]{"--invalid-outcomes", "ignore"},
                new String[]{"--algorithms", "fixed-transfer"}, new String[]{"--algorithms", "full,full"},
                new String[]{"--algorithms", "full,unknown"})) {
            boolean rejected = false;
            try { MPSLGPTransferAblationMain.Config.parse(invalid); }
            catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "reject " + Arrays.toString(invalid));
        }
        Path temp = Files.createTempDirectory("mpslgp-ablation-test-");
        try {
            Path training = temp.resolve("paper-runs"), tests = temp.resolve("paper-tests"), output = temp.resolve("ablation");
            for (int seed = 0; seed < 2; seed++) {
                fixture(training, "R1", new int[]{1,2}, seed);
                fixture(training, "H3", new int[]{1,5,8}, seed);
            }
            String[] options = {"--tests", tests.toString(), "--training-root", training.toString(),
                    "--scenarios", "R1,H3", "--runs", "2", "--output", output.toString()};
            List<MPSLGPPaperTestMain.Job> jobs = MPSLGPPaperTestMain.plan(MPSLGPTransferAblationMain.Config.parse(options).scope());
            for (MPSLGPPaperTestMain.Job job : jobs) {
                MPSLGPTestSummaryMainTest.testFixture(job, job.algorithm.equals("no-transfer") && job.task == 1 && job.run == 1 ? 1 : 0);
                if (job.algorithm.equals("no-transfer")) { shiftTest(job, 10); }
            }
            MPSLGPTransferAblationMain.main(options);
            check(rows(output, "per-run.csv").size() == 20, "20 task/method/seed rows");
            check(rows(output, "mean-std.csv").size() == 10 && rows(output, "table-comparison.csv").size() == 5,
                    "two methods and five paired targets");
            check(rows(output, "paired-tests.csv").size() == 15 && rows(output, "paired-gains.csv").size() == 30,
                    "three metrics per target and paired seed");
            check(rows(output, "validity-summary.csv").size() == 10 && rows(output, "invalid-preferences.csv").size() == 2,
                    "conditional exclusions counted, not dropped seeds");
            for (Map<String, String> row : rows(output, "per-run.csv")) {
                check(Double.parseDouble(row.get("HVJoint")) >= 0 && Double.parseDouble(row.get("HVJoint")) <= 1,
                        "normalized HV range");
                check(Double.parseDouble(row.get("IGDJoint")) >= 0 && Double.parseDouble(row.get("GDJoint")) >= 0,
                        "nonnegative distances");
            }
            for (Map<String, String> row : rows(output, "paired-tests.csv")) {
                check(Double.parseDouble(row.get("MeanGain")) > 0 && row.get("Runs").equals("2"),
                        "known inferior no-transfer synthetic outcomes and correct pairing");
                check(Double.parseDouble(row.get("HolmPValue")) == 1
                        && row.get("Decision").equals("Not significant"), "small synthetic sample not declared significant");
            }
            Properties protocol = readProperties(output.resolve("protocol.properties"));
            check(protocol.getProperty("no-transfer.invalid.rows").equals("2")
                    && protocol.getProperty("full.invalid.rows").equals("0"), "method-specific invalid counts");
            check(protocol.getProperty("multiple.testing").contains("15"), "joint correction family");
            check(protocol.getProperty("scenario.1.max").contains("12.0"), "bounds pool both methods, including inferior outcomes");
            check(Files.readString(output.resolve("table-rows.tex")).contains("\\textbf{"), "formatted comparison table");
            boolean rejected = false;
            try { MPSLGPTransferAblationMain.main(options); }
            catch (IOException e) { rejected = e.getMessage().contains("new --output"); }
            check(rejected, "never overwrite previous references");
            String[] rejectInvalid = Arrays.copyOf(options, options.length + 2);
            rejectInvalid[Arrays.asList(rejectInvalid).indexOf("--output") + 1] = temp.resolve("reject-invalid").toString();
            rejectInvalid[rejectInvalid.length - 2] = "--invalid-outcomes"; rejectInvalid[rejectInvalid.length - 1] = "reject";
            rejected = false;
            try { MPSLGPTransferAblationMain.main(rejectInvalid); }
            catch (IOException e) { rejected = e.getMessage().contains("invalid-fitness sentinel"); }
            check(rejected && !Files.exists(temp.resolve("reject-invalid")), "explicit reject policy writes no partial analysis");
            String[] threeOptions = Arrays.copyOf(options, options.length + 2);
            Path threeOutput = temp.resolve("three-methods");
            threeOptions[Arrays.asList(threeOptions).indexOf("--output") + 1] = threeOutput.toString();
            threeOptions[threeOptions.length - 2] = "--algorithms";
            threeOptions[threeOptions.length - 1] = "full,no-transfer,fixed-transfer";
            List<MPSLGPPaperTestMain.Job> threeJobs = MPSLGPPaperTestMain.plan(
                    MPSLGPTransferAblationMain.Config.parse(threeOptions).scope());
            for (MPSLGPPaperTestMain.Job job : threeJobs) {
                if (job.algorithm.equals("fixed-transfer")) {
                    MPSLGPTestSummaryMainTest.testFixture(job, job.task == 0 && job.run == 1 ? 2 : 0);
                    shiftTest(job, 5);
                }
            }
            MPSLGPTransferAblationMain.main(threeOptions);
            check(rows(threeOutput, "per-run.csv").size() == 30 && rows(threeOutput, "mean-std.csv").size() == 15,
                    "all three methods retain every seed");
            check(rows(threeOutput, "paired-tests.csv").size() == 30
                    && rows(threeOutput, "paired-gains.csv").size() == 60, "Full paired against each control");
            check(rows(threeOutput, "paired-test-summary.csv").size() == 6, "metric summaries separate both controls");
            check(rows(threeOutput, "table-comparison.csv").get(0).containsKey("FixedTransferHV"),
                    "three-column comparison for every metric");
            Properties threeProtocol = readProperties(threeOutput.resolve("protocol.properties"));
            check(threeProtocol.getProperty("multiple.testing").contains("30")
                    && threeProtocol.getProperty("fixed-transfer.invalid.rows").equals("4"),
                    "one correction family and fixed-transfer invalid audit");
            Properties full = readProperties(training.resolve("full/IGD/R1/run00/attempt-0001/effective.params"));
            Properties none = readProperties(training.resolve("no-transfer/IGD/R1/run00/attempt-0001/effective.params"));
            MPSLGPTransferAblationMain.requireMatchedTraining(full, none);
            Properties fixed = readProperties(training.resolve("fixed-transfer/IGD/R1/run00/attempt-0001/effective.params"));
            MPSLGPTransferAblationMain.requireMatchedTraining(full, fixed, "fixed-transfer");
            fixed.setProperty("mpslgp.transfer-probability", "0.2");
            rejected = false;
            try { MPSLGPTransferAblationMain.requireMatchedTraining(full, fixed, "fixed-transfer"); }
            catch (IOException e) { rejected = e.getMessage().contains("Non-ablation training parameter"); }
            check(rejected, "fixed control cannot silently change its transfer budget");
            none.setProperty("select.tournament.size", "8");
            rejected = false;
            try { MPSLGPTransferAblationMain.requireMatchedTraining(full, none); }
            catch (IOException e) { rejected = e.getMessage().contains("Non-ablation training parameter"); }
            check(rejected, "unrelated training differences cannot be silently compared");
            System.out.println("PASS: MPSLGPTransferAblationMainTest (" + assertions + " assertions)");
        } finally {
            try (Stream<Path> paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) { Files.delete(path); }
            }
        }
    }

    private static void fixture(Path training, String group, int[] targets, int seed) throws IOException {
        MPSLGPTestSummaryMainTest.trainingFixture(training, group, targets, seed);
        Path full = training.resolve("full/IGD").resolve(group).resolve(String.format("run%02d", seed));
        Path fullAttempt = full.resolve("attempt-0001");
        Properties source = readProperties(fullAttempt.resolve("effective.params"));
        source.setProperty("mpslgp.transfer-probability", Double.toString(.8 / (targets.length - 1)));
        source.setProperty("mpslgp.max-transfer-probability", "0.8");
        writeProps(fullAttempt.resolve("effective.params"), source);
        Properties completed = readProperties(full.resolve("completed.properties"));
        completed.setProperty("sha256.effective.params", fileHash(fullAttempt.resolve("effective.params")));
        writeProps(full.resolve("completed.properties"), completed);
        for (String algorithm : Arrays.asList("no-transfer", "fixed-transfer")) {
            Path control = training.resolve(algorithm).resolve("IGD").resolve(group).resolve(String.format("run%02d", seed));
            Path attempt = control.resolve("attempt-0001");
            Files.createDirectories(attempt);
            Properties parameters = readProperties(fullAttempt.resolve("effective.params"));
            parameters.setProperty("mpslgp.adaptive-transfer", "false");
            if (algorithm.equals("no-transfer")) {
                parameters.setProperty("mpslgp.transfer-probability", "0.0");
                parameters.setProperty("mpslgp.max-transfer-probability", "0.0");
            }
            writeProps(attempt.resolve("effective.params"), parameters);
            Files.copy(fullAttempt.resolve("job." + seed + ".out.stat"), attempt.resolve("job." + seed + ".out.stat"));
            Properties metadata = readProperties(full.resolve("run.properties"));
            metadata.setProperty("algorithm", algorithm);
            writeProps(control.resolve("run.properties"), metadata);
            completed.setProperty("sha256.effective.params", fileHash(attempt.resolve("effective.params")));
            writeProps(control.resolve("completed.properties"), completed);
        }
    }

    private static void shiftTest(MPSLGPPaperTestMain.Job job, double shift) throws IOException {
        Path attempt = job.directory.resolve("attempt-0001"), file = attempt.resolve(job.csvName);
        Path detail = attempt.resolve("preference-results.csv");
        List<String> rows = Files.readAllLines(file), details = Files.readAllLines(detail);
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i).split(","), weights = details.get(i).split(",");
            for (int objective = 0; objective < 2; objective++) {
                double value = Double.parseDouble(row[8 + 2 * objective]);
                if (value >= 0) { row[8 + 2 * objective] = weights[3 + objective] = Double.toString(value + shift); }
            }
            rows.set(i, String.join(",", row)); details.set(i, String.join(",", weights));
        }
        Files.write(file, rows); Files.write(detail, details);
        Path marker = job.directory.resolve("completed.properties");
        Properties completion = readProperties(marker);
        completion.setProperty("sha256." + job.csvName, fileHash(file));
        completion.setProperty("sha256.preference-results.csv", fileHash(detail));
        writeProps(marker, completion);
    }

    private static List<Map<String, String>> rows(Path output, String name) throws IOException {
        return MPSLGPJointTaskAnalysisMain.readCsv(output.resolve(name));
    }
    private static void writeProps(Path path, Properties properties) throws IOException {
        Map<String, String> values = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) { values.put(key, properties.getProperty(key)); }
        atomicWrite(path, serialize(values));
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new AssertionError(message); }
    }
}
