package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mengxu.algorithm.MAPElites.FuzzySystem.PaperVerificationTest;
import yimei.jss.gp.data.DoubleData;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

public final class RevisionChecks {
    private RevisionChecks() { }

    public static void main(String[] args) throws Exception {
        boolean includeSmoke = false;
        Path report = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--smoke")) {
                includeSmoke = true;
            } else if (args[i].equals("--report") && i + 1 < args.length) {
                report = java.nio.file.Paths.get(args[++i]).toAbsolutePath().normalize();
                if (Files.exists(report)) { throw new IllegalArgumentException("Report already exists: " + report); }
            } else {
                throw new IllegalArgumentException("Usage: RevisionChecks [--smoke] [--report <new file>]");
            }
        }
        try {
            FuzzyCoreChecks.runChecks();
            nativeOperatorChecks();
            sensitivityChecks();
            terminalPolicyChecks();
            Path root = Files.createTempDirectory("qdgp-revision-checks-");
            try {
                archiveAndPathChecks(root);
                syntheticDiagnosticChecks(root);
                if (includeSmoke) { smoke(root); }
            } finally {
                try (Stream<Path> paths = Files.walk(root)) {
                    List<Path> ordered = paths.sorted(java.util.Comparator.reverseOrder()).collect(Collectors.toList());
                    for (Path path : ordered) { Files.delete(path); }
                }
            }
            String result = "REVISION CHECKS PASSED"
                    + (includeSmoke ? " (including bounded scheduling smoke test)" : "");
            if (report != null) {
                Files.write(report, Collections.singletonList(result), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            System.out.println(result);
        } catch (Exception | AssertionError failure) {
            if (report != null && !Files.exists(report)) {
                java.io.StringWriter details = new java.io.StringWriter();
                failure.printStackTrace(new java.io.PrintWriter(details));
                try {
                    Files.write(report, details.toString().getBytes(StandardCharsets.UTF_8),
                            java.nio.file.StandardOpenOption.CREATE_NEW);
                } catch (IOException reportFailure) {
                    failure.addSuppressed(reportFailure);
                }
            }
            throw failure;
        }
    }

    private static void nativeOperatorChecks() {
        double[][] operands = {{-2, 2}, {2, -2}, {2, 2}, {0.0, -0.0},
                {-0.0, 0.0}, {0.0, 0.0}, {-0.0, -0.0}};
        for (String operator : Arrays.asList("Max", "Min", "max", "min", "MAX", "mIn")) {
            for (double[] pair : operands) {
                compareNativePriority("(" + operator + " " + Double.toString(pair[0])
                        + " " + Double.toString(pair[1]) + ")");
            }
        }
        compareNativePriority("(/ 2 (Min (* (- 2 2) -1) (- 2 2)))");
        compareNativePriority("(/ 2 (Max 0.0 -0.0))");
        compareNativePriority("(/ 0 (Max 0.0 -0.0))");
    }

    private static void compareNativePriority(String expression) {
        GPRule rule = PriorityExpression.parseRule(RuleType.SEQUENCING, expression);
        DoubleData nativeResult = new DoubleData();
        rule.getGPTree().child.eval(null, 0, nativeResult, null, null, null);
        if (Double.isFinite(nativeResult.value)) {
            double actual = PriorityExpression.evaluate(rule.getGPTree().child, Collections.emptyMap());
            check(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(nativeResult.value),
                    "Native GP operator mismatch: " + expression);
        } else {
            try {
                PriorityExpression.evaluate(rule.getGPTree().child, Collections.emptyMap());
                throw new AssertionError("Non-finite native GP priority was accepted: " + expression);
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("Non-finite priority"), "Native non-finite priority guard");
            }
        }
    }

    private static void sensitivityChecks() {
        Map<String, Double> original = new LinkedHashMap<>();
        original.put("PT", 2.0);
        original.put("WIQ", 3.0);
        original.put("rDD", -1.0);
        SensitivityProbeSet.Probe probe = new SensitivityProbeSet.Probe("fixture-0", original);
        original.put("PT", 999.0);
        check(probe.values().get("PT") == 2.0, "Probe snapshot must be immutable");
        List<SensitivityProbeSet.Probe> samples = Collections.singletonList(probe);
        OutputSensitivityCalculator calculator = new OutputSensitivityCalculator(1.0e-4);
        GPRule sum = GPRule.readFromLispExpression(RuleType.SEQUENCING, "(+ PT WIQ)");
        close(calculator.calculateTree(sum.getGPTree().child, samples), 1.0, "linear derivative");
        GPRule repeated = GPRule.readFromLispExpression(RuleType.SEQUENCING, "(+ PT PT)");
        close(calculator.calculateTree(repeated.getGPTree().child, samples), 2.0, "repeated-terminal derivative");
        GPRule constant = GPRule.readFromLispExpression(RuleType.SEQUENCING, "7");
        close(calculator.calculateTree(constant.getGPTree().child, samples), 0.0, "constant derivative");
        GPRule routing = GPRule.readFromLispExpression(RuleType.ROUTING, "(* 2 PT)");
        close(calculator.calculate(new GPRule[]{sum, routing},
                new SensitivityProbeSet(samples, samples)), 1.5, "role-average derivative");
        GPRule max = GPRule.readFromLispExpression(RuleType.SEQUENCING, "(Max PT WIQ)");
        close(PriorityExpression.evaluate(max.getGPTree().child, probe.values()), 3.0, "Max priority");
        GPRule divide = GPRule.readFromLispExpression(RuleType.SEQUENCING, "(/ PT 0)");
        close(PriorityExpression.evaluate(divide.getGPTree().child, probe.values()), 1.0, "protected division");
        GPRule lowerCase = PriorityExpression.parseRule(RuleType.SEQUENCING, "(max PT WIQ)");
        close(PriorityExpression.evaluate(lowerCase.getGPTree().child, probe.values()), 3.0, "canonical operator parsing");
        GPRule signedZero = PriorityExpression.parseRule(RuleType.SEQUENCING,
                "(/ PT (Min (* (- PT PT) rDD) (- PT PT)))");
        close(PriorityExpression.evaluate(signedZero.getGPTree().child, probe.values()),
                1.0, "Min signed-zero protected division");
        close(calculator.calculateTree(signedZero.getGPTree().child, samples),
                0.0, "Min signed-zero sensitivity");
        try {
            PriorityExpression.parseRule(RuleType.SEQUENCING, "(+ PT WIQ NOR)");
            throw new AssertionError("Extra GP arguments were silently ignored");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("binary"), "Strict operator arity");
        }
    }

    private static void terminalPolicyChecks() {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String terminal : SensitivityProbeSet.supportedTerminals()) { values.put(terminal, 2.0); }
        SensitivityProbeSet.Probe probe = new SensitivityProbeSet.Probe("supported-terminals", values);
        for (String terminal : SensitivityProbeSet.supportedTerminals()) {
            GPRule rule = PriorityExpression.parseRule(RuleType.SEQUENCING, terminal);
            check(PriorityExpression.terminals(rule.getGPTree().child).contains(terminal),
                    "Supported terminal must parse and validate: " + terminal);
            close(new OutputSensitivityCalculator(0.001).calculateTree(rule.getGPTree().child,
                    Collections.singletonList(probe)), 1.0, "Supported terminal derivative: " + terminal);
        }
        try {
            SensitivityProbeSet.supportedTerminals().set(0, "DD");
            throw new AssertionError("Supported-terminal policy is mutable");
        } catch (UnsupportedOperationException expected) {
            check(SensitivityProbeSet.supportedTerminals().contains("NIQ"), "Stable terminal policy");
        }
        for (String terminal : Arrays.asList("DD", "Pone", "UNKNOWN")) {
            try {
                PriorityExpression.parseRule(RuleType.SEQUENCING, terminal);
                throw new AssertionError("Unsupported terminal was parsed: " + terminal);
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("Unsupported revision terminal: " + terminal),
                        "Explicit unsupported-terminal error");
            }
        }
        GPRule unsupported = GPRule.readFromLispExpression(RuleType.SEQUENCING, "DD");
        try {
            new OutputSensitivityCalculator(0.001).calculateTree(unsupported.getGPTree().child,
                    Collections.singletonList(probe));
            throw new AssertionError("Externally parsed unsupported terminal was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("Unsupported revision terminal: DD"),
                    "Shared terminal policy for external trees");
        }
    }

    private static void archiveAndPathChecks(Path root) throws IOException {
        Path input = root.resolve("input");
        Path archiveDirectory = input.resolve("NfixIns/multipletreegp-dynamic/mean-tardiness-0.85-1.5");
        Files.createDirectories(archiveDirectory);
        Path archive = archiveDirectory.resolve("job.0.out.stat");
        Files.write(archive, fixture(true).getBytes(StandardCharsets.UTF_8));
        String hash = RevisionCsv.fileHash(archive);
        FinalArchiveReader.Archive read = new FinalArchiveReader().read(archive);
        check(read.declaredCount() == 3 && read.candidates().size() == 3, "Final archive count");
        check(read.candidates().get(0).sequencing().equals("(+ PT WIQ)"), "Ignore generation-best block");
        check(read.candidates().get(1).routing().equals("W"), "Bare terminal parsing");
        check(!read.candidates().get(2).hasFiniteStoredFitness(), "Preserve and classify Infinity");
        check(!read.candidates().get(0).id().equals(read.candidates().get(1).id()), "Stable candidate identities");
        String[] dryRun = {"--input-root", input.toString(), "--runs", "0", "--pool-size", "2",
                "--top-k", "1", "--dry-run"};
        RevisionExperiment.run(RevisionConfiguration.fromArgs(dryRun));
        check(hash.equals(RevisionCsv.fileHash(archive)), "Dry run must not change inputs");
        Path unsupported = archiveDirectory.resolve("job.1.out.stat");
        Files.write(unsupported, fixture(true).replace("(+ PT WIQ)", "DD").getBytes(StandardCharsets.UTF_8));
        String unsupportedHash = RevisionCsv.fileHash(unsupported);
        Path rejectedOutput = root.resolve("unsupported-output");
        for (boolean dry : Arrays.asList(true, false)) {
            List<String> arguments = new java.util.ArrayList<>(Arrays.asList("--input-root", input.toString(),
                    "--output-root", rejectedOutput.toString(), "--runs", "1", "--pool-size", "2", "--top-k", "1"));
            if (dry) { arguments.add("--dry-run"); }
            try {
                RevisionExperiment.run(RevisionConfiguration.fromArgs(arguments.toArray(new String[0])));
                throw new AssertionError("Unsupported archive terminal passed preflight");
            } catch (IOException expected) {
                check(expected.getMessage().contains("job.1.out.stat")
                        && expected.getCause() instanceof IllegalArgumentException
                        && expected.getCause().getMessage().contains("Unsupported revision terminal: DD"),
                        "Contextual unsupported archive error");
            }
            check(!Files.exists(rejectedOutput), "Unsupported inputs must fail before output creation");
            check(unsupportedHash.equals(RevisionCsv.fileHash(unsupported)), "Rejected input must remain unchanged");
        }
        Path malformed = archiveDirectory.resolve("bad.stat");
        Files.write(malformed, fixture(true).replace("GridMAP number: 3", "GridMAP number: 4")
                .getBytes(StandardCharsets.UTF_8));
        try {
            new FinalArchiveReader().read(malformed);
            throw new AssertionError("Truncated/count-mismatched archive was accepted");
        } catch (IOException expected) {
            check(expected.getMessage().contains("Expected evaluated"), "Contextual parser failure");
        }
        try {
            RevisionConfiguration.checkedOutput(input.resolve("new-output"), input.toRealPath());
            throw new AssertionError("Output inside input root was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("outside"), "Protected input-root guard");
        }
        try {
            RevisionConfiguration.fromArgs(new String[]{"--input-root", input.toString(), "--dry-run",
                    "--pool-size", "2", "--top-k", "1", "--validation-seed", "10", "--test-seed", "10"});
            throw new AssertionError("Overlapping validation/test seeds were accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("overlap"), "Disjoint test-set guard");
        }
    }

    private static void syntheticDiagnosticChecks(Path root) throws IOException {
        Path first = root.resolve("synthetic-first.csv");
        Path second = root.resolve("synthetic-second.csv");
        for (Path output : Arrays.asList(first, second)) {
            PaperVerificationTest diagnostic = new PaperVerificationTest(42);
            diagnostic.runSyntheticVerification(2, 20, 3, 42);
            diagnostic.exportToCSV(output.toString());
            List<String> rows = Files.readAllLines(output, StandardCharsets.UTF_8);
            check(rows.size() == 13, "Six legacy profiles times two methods");
            for (String row : rows.subList(1, rows.size())) {
                String[] fields = row.split(",");
                check(fields.length == 8, "Stable synthetic CSV format");
                for (int column = 2; column <= 4; column++) {
                    double value = Double.parseDouble(fields[column]);
                    check(Double.isFinite(value) && value >= 0, "Valid synthetic metric");
                }
            }
        }
        String originalHash = RevisionCsv.fileHash(first);
        check(originalHash.equals(RevisionCsv.fileHash(second)), "Reproducible synthetic diagnostic");
        try {
            PaperVerificationTest.main(new String[]{first.toString()});
            throw new AssertionError("Legacy diagnostic overwrote its output");
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            check(originalHash.equals(RevisionCsv.fileHash(first)), "Preserve existing diagnostic output");
        }
        try {
            new PaperVerificationTest(42).runSyntheticVerification(0, 20, 3, 42);
            throw new AssertionError("Invalid synthetic run count was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("Positive run/candidate"), "Explicit synthetic configuration error");
        }
    }

    private static void smoke(Path root) throws IOException {
        Path input = root.resolve("input");
        Path output = root.resolve("smoke-output");
        RevisionConfiguration configuration = RevisionConfiguration.fromArgs(new String[]{
            "--input-root", input.toString(), "--output-root", output.toString(),
            "--runs", "0", "--pool-size", "2", "--top-k", "1",
            "--validation-instances", "1", "--test-instances", "1",
            "--recorded-jobs", "20", "--warmup-jobs", "0",
            "--probe-jobs", "200", "--probe-warmup", "0",
            "--probe-count", "1", "--probe-candidates", "2"
        });
        Path archive = configuration.archiveDirectory.resolve("job.0.out.stat");
        String before = RevisionCsv.fileHash(archive);
        RevisionExperiment.run(configuration);
        List<String> results = Files.readAllLines(output.resolve("run_results.csv"), StandardCharsets.UTF_8);
        check(results.size() == 25, "Six cases times four selectors");
        for (String line : results.subList(1, results.size())) {
            String[] fields = line.split(",");
            check(Double.parseDouble(fields[5]) < 1_000_000, "Test objective must not be stored training fitness");
            check(!line.contains("NaN") && !line.contains("Infinity"), "Finite smoke-test metrics");
        }
        check(before.equals(RevisionCsv.fileHash(archive)), "Experiment must not modify input archive");
        String manifest = new String(Files.readAllBytes(output.resolve("manifest.properties")), StandardCharsets.UTF_8);
        check(manifest.contains("status=COMPLETE"), "Completed experiment manifest");
        try {
            RevisionConfiguration.fromArgs(new String[]{"--input-root", input.toString(),
                    "--output-root", output.toString()});
            throw new AssertionError("Existing output accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("overwrite"), "No-overwrite guard");
        }
    }

    private static String fixture(boolean includeFailure) {
        return "Generation: 0\nBest individual:\nFitness: [1]\nTree 0:\nPT\nTree 1:\nWIQ\n"
                + "All individuals in GridMAP:\nGridMAP number: " + (includeFailure ? 3 : 2) + "\n"
                + "Evaluated: true\nFitness: [9999991]\nTree 0:\n(+ PT WIQ)\nTree 1:\nWIQ\n"
                + "Evaluated: true\nFitness: [9999992]\nTree 0:\n(Max PT WIQ)\nTree 1:\nW\n"
                + (includeFailure ? "Evaluated: true\nFitness: [Infinity]\nTree 0:\nPT\nTree 1:\nWIQ\n" : "")
                + "\nBest Individual of Run:\nSubpopulation 0:\nEvaluated: true\nFitness: [0.1]\n";
    }

    private static void close(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < 1.0e-6, label + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
