package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExperimentAWorkflowChecks {
    private ExperimentAWorkflowChecks() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) { throw new IllegalArgumentException("ExperimentAWorkflowChecks takes no arguments"); }
        Path root = Files.createTempDirectory("fuzzy-a-workflow-");
        try {
            ImportanceSemanticsChecks.runChecks();
            Path input = root.resolve("input");
            Path archives = input.resolve("NfixIns").resolve("multipletreegp-dynamic")
                    .resolve("mean-tardiness-0.85-1.5");
            Files.createDirectories(archives);
            for (int run = 0; run < 2; run++) {
                Files.write(archives.resolve("job." + run + ".out.stat"), fixture().getBytes(StandardCharsets.UTF_8));
            }
            String before = RevisionCsv.fileHash(archives.resolve("job.0.out.stat"));
            RevisionConfiguration config = configuration(input, root.resolve("controlled"), "0:1");
            ExperimentAStudy.run(config, false, value -> new FakeEvaluation(value, false));
            check(ExperimentAFiles.read(config.outputRoot.resolve("manifest.properties")).getProperty("status")
                    .equals("COMPLETE_WITH_TEST_FAILURES"), "Held-out failures are preserved");
            List<String> rows = Files.readAllLines(config.outputRoot.resolve("run_results.csv"), StandardCharsets.UTF_8);
            check(rows.size() == 37, "Two runs x six profiles x three methods");
            check(rows.stream().anyMatch(row -> row.contains("Infinity")), "Test failure must remain in the result");
            String exclusions = new String(Files.readAllBytes(config.outputRoot.resolve("run-0")
                    .resolve("candidate_metrics.csv")), StandardCharsets.UTF_8);
            check(exclusions.contains("EXCLUDED_VALIDATION_FAILURE")
                    && exclusions.contains("EXCLUDED_METRIC_FAILURE"), "Exclusions must have explicit reasons");
            String selected = new String(Files.readAllBytes(config.outputRoot.resolve("run-0").resolve("selections.csv")),
                    StandardCharsets.UTF_8);
            auditImportanceScores(config.outputRoot.resolve("run-0").resolve("selections.csv"));
            String failedCandidate = new FinalArchiveReader().read(archives.resolve("job.0.out.stat")).candidates().get(0).id();
            check(selected.contains(failedCandidate), "Test failure must not trigger reselection");
            String resultsHash = RevisionCsv.fileHash(config.outputRoot.resolve("run_results.csv"));
            ExperimentAStudy.run(config, true, value -> { throw new AssertionError("Completed run was re-evaluated"); });
            check(resultsHash.equals(RevisionCsv.fileHash(config.outputRoot.resolve("run_results.csv"))), "Resume preserves raw data");
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                    config.outputRoot.resolve(".study.lock"), java.nio.file.StandardOpenOption.WRITE);
                 java.nio.channels.FileLock held = channel.lock()) {
                try {
                    ExperimentAStudy.run(config, true, value -> { throw new AssertionError("Concurrent study was launched"); });
                    throw new AssertionError("Active study lock was ignored");
                } catch (IllegalStateException expected) {
                    check(expected.getMessage().contains("already running"), "Explicit concurrent-run protection");
                }
            }

            ExperimentAAnalysis.main(new String[]{"--input", config.outputRoot.toString(), "--output-root",
                    root.resolve("controlled-analysis").toString(), "--expected-runs", "2",
                    "--expected-pool-size", "3", "--expected-top-k", "1", "--expected-test-instances", "2",
                    "--bootstrap-samples", "100"});

            RevisionConfiguration recovery = configuration(input, root.resolve("recovery"), "0:1");
            AtomicInteger created = new AtomicInteger();
            try {
                ExperimentAStudy.run(recovery, false, value -> new FakeEvaluation(value, created.incrementAndGet() == 2));
                throw new AssertionError("Injected interrupted run did not fail");
            } catch (IllegalStateException expected) { check(expected.getMessage().contains("injected"), "Unexpected failure"); }
            AtomicInteger resumed = new AtomicInteger();
            ExperimentAStudy.run(recovery, true, value -> {
                resumed.incrementAndGet();
                return new FakeEvaluation(value, false);
            });
            check(resumed.get() == 1 && Files.isDirectory(recovery.outputRoot.resolve("run-1"))
                    && Files.isDirectory(recovery.outputRoot.resolve("run-1-attempt-2")), "Resume must keep partial attempts");
            ExperimentAStudy.main(physicalArguments(input, root.resolve("physical")));
            check(ExperimentAFiles.complete(ExperimentAFiles.read(root.resolve("physical").resolve("manifest.properties"))),
                    "Physical scheduling smoke study completed");
            check(before.equals(RevisionCsv.fileHash(archives.resolve("job.0.out.stat"))), "Historical input integrity");

            Files.write(config.outputRoot.resolve("run-0").resolve("selections.csv"),
                    Arrays.asList("tampered"), StandardCharsets.UTF_8);
            try {
                ExperimentAStudy.run(config, true, value -> { throw new AssertionError("Tampered data were recomputed"); });
                throw new AssertionError("Changed completed artifact was accepted");
            } catch (IOException expected) { check(expected.getMessage().contains("artifact"), "Integrity failure context"); }
            ExperimentAAnalysisChecks.runChecks(root);
            System.out.println("EXPERIMENT A WORKFLOW CHECKS PASSED: fair pools, exclusions, no test reselection, resume, integrity and analysis");
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.delete(path);
                }
            }
        }
    }

    private static final class FakeEvaluation implements ExperimentAStudy.EvaluationService {
        private final RevisionConfiguration config;
        private final boolean interrupt;
        private long calls;
        FakeEvaluation(RevisionConfiguration config, boolean interrupt) { this.config = config; this.interrupt = interrupt; }
        @Override public SchedulingEvaluator.Evaluation evaluate(ArchiveCandidate candidate, long seed, int instances) {
            if (interrupt) { throw new IllegalStateException("injected interrupted evaluation"); }
            calls += instances;
            List<Double> values = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            boolean validation = seed == config.validationSeed;
            for (int i = 0; i < instances; i++) {
                boolean failed = i == 0 && ((validation && candidate.ordinal() == 4)
                        || (!validation && candidate.ordinal() == 0));
                values.add(failed ? Double.POSITIVE_INFINITY : candidate.ordinal() + 1.0);
                failures.add(failed ? "injected numeric outcome" : "");
            }
            return new SchedulingEvaluator.Evaluation(values, ExperimentAStudy.testMean(values), failures);
        }
        @Override public long count() { return calls; }
    }

    private static void auditImportanceScores(Path path) throws IOException {
        List<String> rows = Files.readAllLines(path, StandardCharsets.UTF_8);
        check(!rows.get(0).contains("si_diagnostic"), "Fixed SI must not be presented as a personalized score");
        java.util.Map<String, mengxu.algorithm.MAPElites.FuzzySystem.UserPreferenceVector> profiles =
                mengxu.algorithm.MAPElites.FuzzySystem.UserPreferenceVector.importanceCases();
        for (String row : rows.subList(1, rows.size())) {
            String[] fields = row.split(",", -1);
            check(fields.length == 17, "Auditable z and fuzzy-desirability columns");
            if (fields[2].equals("RANDOM")) { check(fields[5].isEmpty(), "Random has no preference score"); continue; }
            mengxu.algorithm.MAPElites.FuzzySystem.UserPreferenceVector profile = profiles.get(fields[1]);
            String[] variables = {"Performance", "Interpretability", "Stability"};
            double numerator = 0, denominator = 0;
            int offset = fields[2].equals("FUZZY_IMPORTANCE") ? 14 : 11;
            for (int i = 0; i < variables.length; i++) {
                double weight = profile.getWeight(variables[i]);
                numerator += weight * Double.parseDouble(fields[offset + i]);
                denominator += weight;
            }
            check(Math.abs(Double.parseDouble(fields[5]) - numerator / denominator) < 1e-12,
                    "Exported preference score must reconstruct from its actual utilities and weights");
        }
    }

    private static RevisionConfiguration configuration(Path input, Path output, String runs) throws IOException {
        List<String> args = new ArrayList<>(Arrays.asList(physicalArguments(input, output)));
        int index = args.indexOf("--runs");
        args.set(index + 1, runs);
        return RevisionConfiguration.forStudy(args.toArray(new String[0]));
    }

    private static String[] physicalArguments(Path input, Path output) {
        return new String[]{"--input-root", input.toString(), "--output-root", output.toString(),
                "--runs", "0", "--pool-size", "3", "--top-k", "1", "--metrics-scope", "all",
                "--validation-instances", "1", "--test-instances", "2", "--recorded-jobs", "40",
                "--warmup-jobs", "0", "--probe-jobs", "200", "--probe-warmup", "0",
                "--probe-count", "1", "--probe-candidates", "2"};
    }

    private static String fixture() {
        StringBuilder text = new StringBuilder("All individuals in GridMAP:\nGridMAP number: 6\n");
        String[] seq = {"PT", "(Max PT WIQ)", "(Min PT WIQ)", "(/ 1 -0.0)", "W", "PT"};
        String[] rout = {"WIQ", "W", "NIQ", "WIQ", "PT", "PT"};
        for (int i = 0; i < seq.length; i++) {
            text.append("Evaluated: true\nFitness: [").append(i == 5 ? "Infinity" : 9000000 + i)
                    .append("]\nTree 0:\n").append(seq[i]).append("\nTree 1:\n").append(rout[i]).append('\n');
        }
        return text.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
