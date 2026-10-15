package mengxu.ruleanalysis.MPSLGP;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.RuleTypeV2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.*;

public final class MPSLGPPaperTestMainTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("mpslgp-test-runner-");
        try {
            Path training = temp.resolve("training");
            fixture(training, "R1", new int[]{1, 2});
            fixture(training, "H3", new int[]{1, 5, 8});
            testArguments();
            testPlan(training, temp.resolve("plan"));
            testResume(training, temp.resolve("resume"));
            testInvalidFitness();
            testSentinelCompletion(training, temp.resolve("sentinel"));
            testFailure(training, temp.resolve("failure"));
            testLock(training, temp.resolve("locked"));
            testCsvValidation(temp.resolve("csv-validation"));
            testDatasetProtocol();
            if (Arrays.asList(args).contains("--simulation")) { testNumericalParity(); }
            System.out.println("PASS: MPSLGPPaperTestMainTest (" + assertions + " assertions)");
        } finally {
            try (Stream<Path> paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) { Files.delete(path); }
            }
        }
    }

    private static void testArguments() {
        MPSLGPPaperTestMain.Config c = MPSLGPPaperTestMain.Config.parse(new String[0]);
        check(c.preferences == 200 && c.replications == 30 && c.runs == 5, "formal testing defaults");
        check(c.scenarios.equals(Arrays.asList("R1", "H3")) && c.algorithms.equals(Arrays.asList("full")), "requested selection");
        check(c.parallel == 1 && c.indicators.equals(Arrays.asList("IGD")), "safe indicator and parallel defaults");
        c = MPSLGPPaperTestMain.Config.parse(new String[]{"--smoke", "--skip", "existing"});
        check(c.preferences == 3 && c.replications == 1 && c.output.endsWith("smoke") && c.skipExisting, "isolated smoke and skip alias");
        for (String[] invalid : Arrays.asList(new String[]{"--preferences", "1"}, new String[]{"--replications", "0"},
                new String[]{"--tasks", "-1"}, new String[]{"--tasks", "0,0"},
                new String[]{"--runs"}, new String[]{"--scenarios", "R1,R1"},
                new String[]{"--indicators", "BAD"}, new String[]{"--ensemble"},
                new String[]{"--heap", "2g -cp bad"}, new String[]{"--start-run", "2147483647", "--runs", "1"},
                new String[]{"--training-root", "out\\data", "--output", "out\\data\\tests"})) {
            boolean rejected = false;
            try { MPSLGPPaperTestMain.Config.parse(invalid); }
            catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "reject " + Arrays.toString(invalid));
        }
    }

    private static void testPlan(Path training, Path output) throws Exception {
        List<String> args = arguments(training, output, "--dry-run");
        MPSLGPPaperTestMain.Config c = MPSLGPPaperTestMain.Config.parse(args.toArray(new String[0]));
        List<MPSLGPPaperTestMain.Job> jobs = MPSLGPPaperTestMain.plan(c);
        check(jobs.size() == 5, "two plus three task jobs");
        check(jobs.get(0).setName.equals("missing-0.7-1.5"), "R1 task0 dataset");
        check(jobs.get(1).setName.equals("missing-0.75-1.5"), "R1 task1 dataset");
        check(jobs.get(4).spec.get("benchmark.scenario").equals("8")
                && jobs.get(4).spec.get("objective.0").equals("max-weighted-flowtime"), "H3 task2 mapping");
        check(!jobs.get(0).spec.get("sequencing.expression").equals(jobs.get(1).spec.get("sequencing.expression")), "task-specific rule selection");
        Map<Path, String> before = hashes(training);
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        check(MPSLGPPaperTestMain.execute(args.toArray(new String[0]), new PrintStream(log),
                (command, directory, progress) -> { throw new AssertionError("Dry run launched a child"); }) == 0, "dry run");
        check(hashes(training).equals(before), "dry run never changes training artifacts");
        check(Files.exists(output.resolve("full/IGD/H3/run00/task02/selected-heuristic.properties")), "selection exported");
        try (Stream<Path> paths = Files.walk(output)) {
            check(paths.noneMatch(path -> path.getFileName().toString().startsWith("attempt-")), "dry run has no attempts");
        }
        check(log.toString("UTF-8").contains("[TEST DRY-RUN]"), "identified planning output");
    }

    private static void testResume(Path training, Path output) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MPSLGPPaperTestMain.ProcessRunner fake = (command, directory, progress) -> {
            calls.incrementAndGet(); progress.accept("Preference 1/3"); fakeOutputs(directory); return 0;
        };
        Map<Path, String> before = hashes(training);
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        String[] args = arguments(training, output, "--skip-existing").toArray(new String[0]);
        check(MPSLGPPaperTestMain.execute(args, new PrintStream(log), fake) == 0 && calls.get() == 5, "first test executes all tasks");
        Path legacyMarker = output.resolve("full/IGD/R1/run00/task00/completed.properties");
        Properties marker = readProperties(legacyMarker);
        Map<String, String> legacy = new TreeMap<>();
        for (String key : marker.stringPropertyNames()) {
            if (!key.equals("invalid.preference.rows") && !key.equals("valid.preference.rows")) {
                legacy.put(key, marker.getProperty(key));
            }
        }
        atomicWrite(legacyMarker, serialize(legacy));
        check(MPSLGPPaperTestMain.execute(args, new PrintStream(log), fake) == 0 && calls.get() == 5, "completed tests skipped");
        check(log.toString("UTF-8").contains("[TEST PREFERENCE] algorithm=full indicator=IGD scenario=H3")
                && log.toString("UTF-8").contains("[TEST SKIP]"), "progress names task context");
        Path root = output.resolve("full/IGD/R1/run00/task00");
        Path old = root.resolve("attempt-0001/missing-0.7-1.5-0-ParetoFront.csv");
        Files.writeString(old, "truncated");
        check(MPSLGPPaperTestMain.execute(args, new PrintStream(log), fake) == 0 && calls.get() == 6, "corrupt output retested");
        check(Files.readString(old).equals("truncated"), "failed output preserved");
        check(readProperties(root.resolve("completed.properties")).getProperty("attempt").equals("attempt-0002"), "new completion");
        List<String> changed = arguments(training, output, "--skip-existing");
        changed.set(changed.indexOf("--preferences") + 1, "4");
        check(MPSLGPPaperTestMain.execute(changed.toArray(new String[0]), new PrintStream(log), fake) == 5
                && calls.get() == 6, "configuration mismatch does not overwrite or simulate");
        check(hashes(training).equals(before), "testing leaves every training artifact unchanged");
        try (Stream<Path> paths = Files.walk(output.resolve("batches"))) {
            long aggregateCount = paths.filter(path -> path.getFileName().toString().endsWith("-ParetoFront.csv")).count();
            check(aggregateCount == 15, "three successful batches each aggregate five distinct target tasks");
        }
    }

    private static void testInvalidFitness() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        GPRule rule = new GPRule(RuleType.SEQUENCING, null) {
            @Override
            public void calcFitnessWithPreference(Fitness fitness, EvolutionState state, SchedulingSet set,
                                                   AbstractRule other, List<Objective> objectives, int index) {
                calls.incrementAndGet();
                ((MultiObjectiveFitness)fitness).setObjectives(state,
                        index == 1 ? new double[]{-1, -1} : new double[]{10 + index, 20 + index});
            }
        };
        GPRuleEvolutionStatePSL state = MPSLGPTaskEvaluator.evaluationState(new double[][]{{0,1}, {.5,.5}, {1,0}});
        for (int i = 0; i < 3; i++) {
            double[] result = MPSLGPTaskEvaluator.evaluatePreference(rule, null, null, objectives(), state, i);
            check(Arrays.equals(result, i == 1 ? new double[]{-1,-1} : new double[]{10+i,20+i}),
                    "retain legacy fitness at preference " + i);
        }
        check(calls.get() == 3, "evaluation continues after an all-invalid preference");
    }

    private static void testSentinelCompletion(Path training, Path output) throws Exception {
        List<String> arguments = arguments(training, output, "--tasks", "0", "--skip-existing");
        arguments.set(arguments.indexOf("--scenarios") + 1, "R1");
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        MPSLGPPaperTestMain.ProcessRunner fake = (command, directory, progress) -> {
            calls.incrementAndGet();
            fakeOutputs(directory);
            setTestFitness(directory, 1, -1, -1);
            return 0;
        };
        String[] args = arguments.toArray(new String[0]);
        check(MPSLGPPaperTestMain.execute(args, new PrintStream(log), fake) == 0,
                "sentinel rows do not fail an otherwise complete task");
        Path directory = output.resolve("full/IGD/R1/run00/task00");
        Properties completion = readProperties(directory.resolve("completed.properties"));
        check(completion.getProperty("invalid.preference.rows").equals("1")
                && completion.getProperty("valid.preference.rows").equals("2"), "explicit valid and invalid row counts");
        check(completion.getProperty("status").equals("COMPLETED"), "completed means all preferences recorded");
        check(MPSLGPPaperTestMain.execute(args, new PrintStream(log), fake) == 0 && calls.get() == 1,
                "completed sentinel-bearing task skipped without repeating simulations");
        check(log.toString("UTF-8").contains("[TEST WARNING]")
                && log.toString("UTF-8").contains("invalid preference rows=1"), "invalid outcomes explicitly reported");
        try (Stream<Path> files = Files.walk(output.resolve("batches"))) {
            Path[] aggregates = files.filter(path -> path.getFileName().toString().endsWith("-ParetoFront.csv"))
                    .toArray(Path[]::new);
            check(aggregates.length == 2, "initial and resumed batches aggregate recorded rows");
            for (Path aggregate : aggregates) {
                List<String> rows = Files.readAllLines(aggregate);
                check(rows.size() == 4 && rows.get(2).contains(",-1.0,")
                        && rows.get(3).contains(",10.0,"), "aggregation keeps sentinel and subsequent preference");
            }
        }
    }

    private static void testFailure(Path training, Path output) throws Exception {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        String[] args = arguments(training, output).toArray(new String[0]);
        check(MPSLGPPaperTestMain.execute(args, new PrintStream(log), (command, directory, progress) -> 7) == 5,
                "worker failures counted");
        try (Stream<Path> paths = Files.walk(output)) {
            check(paths.noneMatch(path -> path.getFileName().toString().equals("completed.properties")), "no success markers for failures");
        }
        check(MPSLGPPaperTestMain.execute(args, new PrintStream(log), (command, directory, progress) -> {
            fakeOutputs(directory); return 0;
        }) == 0, "incomplete task tests retried");
        String[] missing = arguments(training.resolve("absent"), output.resolveSibling("missing-output")).toArray(new String[0]);
        boolean rejected = false;
        try { MPSLGPPaperTestMain.execute(missing, new PrintStream(log), (command, directory, progress) -> 0); }
        catch (IOException e) { rejected = true; }
        check(rejected, "missing training fails before tests start");
    }

    private static void testLock(Path training, Path output) throws Exception {
        Path directory = output.resolve("full/IGD/R1/run00/task00");
        Files.createDirectories(directory);
        List<String> args = arguments(training, output, "--tasks", "0");
        args.set(args.indexOf("--scenarios") + 1, "R1");
        try (FileChannel channel = FileChannel.open(directory.resolve("test.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            check(MPSLGPPaperTestMain.execute(args.toArray(new String[0]), new PrintStream(new ByteArrayOutputStream()),
                    (command, path, progress) -> { throw new AssertionError("Locked job ran"); }) == 1, "task ownership lock");
        }
    }

    private static void testCsvValidation(Path directory) throws Exception {
        Files.createDirectories(directory);
        Map<String, String> spec = new TreeMap<>();
        spec.put("preferences", "3"); spec.put("run", "0"); spec.put("generation", "2");
        spec.put("training.objective.0", "1.0"); spec.put("training.objective.1", "2.0");
        spec.put("output.csv", "test.csv");
        atomicWrite(directory.resolve("test.properties"), serialize(spec));
        fakeOutputs(directory);
        MPSLGPPaperTestMain.validateOutputs(directory, spec);
        check(true, "exact compatible output accepted");
        Path details = directory.resolve("preference-results.csv");
        String original = Files.readString(details);
        Files.writeString(details, original.replace("0,0.0,1.0", "0,NaN,1.0"));
        boolean failed = false;
        try { MPSLGPPaperTestMain.validateOutputs(directory, spec); }
        catch (IOException e) { failed = true; }
        check(failed, "NaN preference rejected");
        Files.writeString(details, original);
        setTestFitness(directory, 1, -1, 20);
        check(MPSLGPPaperTestMain.validateOutputs(directory, spec) == 1, "single-objective sentinel row accepted and counted");
        setTestFitness(directory, 1, -1, -1);
        check(MPSLGPPaperTestMain.validateOutputs(directory, spec) == 1, "two sentinels count as one invalid preference");
        for (double invalid : new double[]{-2, -.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            setTestFitness(directory, 1, invalid, 20);
            failed = false;
            try { MPSLGPPaperTestMain.validateOutputs(directory, spec); }
            catch (IOException e) { failed = true; }
            check(failed, "reject non-sentinel invalid fitness " + invalid);
        }
        fakeOutputs(directory);
        List<String> rows = Files.readAllLines(directory.resolve("test.csv"));
        rows.remove(rows.size() - 1);
        Files.write(directory.resolve("test.csv"), rows);
        failed = false;
        try { MPSLGPPaperTestMain.validateOutputs(directory, spec); }
        catch (IOException e) { failed = true; }
        check(failed, "partial preference front rejected");
    }

    private static void testDatasetProtocol() {
        List<Objective> objectives = objectives();
        MPSLGPTaskEvaluator current = new MPSLGPTaskEvaluator("missing-0.7-1.5", objectives, 200, 30);
        Baseline baseline = new Baseline(objectives, 200);
        double[][] expected = baseline.vectors(), actual = current.preferenceVectors();
        for (int i = 0; i < 200; i++) { check(Arrays.equals(expected[i], actual[i]), "preference " + i + " matches PSLGP exactly"); }
        actual[0][0] = 123;
        check(current.preferenceVectors()[0][0] == 0, "defensive preference copy");
        SchedulingSet a = current.generateTestSet(), b = baseline.generateTestSet();
        check(a.getReplications().equals(b.getReplications()) && a.getReplications().equals(Collections.singletonList(30)), "same 30 test replications");
        check(a.getSimulations().size() == 1 && a.getSimulations().get(0).getClass().equals(b.getSimulations().get(0).getClass()), "same simulator class");
        check(a.getSimulations().get(0).getNumJobsRecorded() == 5000 && b.getSimulations().get(0).getNumJobsRecorded() == 5000, "same 5000 measured jobs");
        check(MPSLGPTaskEvaluator.simSeed == 968356, "same baseline test seed");
        check(MPSLGPTaskEvaluator.CSV_HEADER.equals("Run,ParetoFromGeneration,SeqRuleSize,SeqRuleUniqueTerminals,"
                + "RoutRuleSize,RoutRuleUniqueTerminals,Obj,TrainFitness0,TestFitness0,TrainFitness1,TestFitness1"), "exact baseline CSV schema");
    }

    private static void testNumericalParity() throws IOException {
        List<Objective> objectives = objectives();
        MPSLGPTaskEvaluator current = new MPSLGPTaskEvaluator("missing-0.7-1.5", objectives, 3, 2);
        Baseline baseline = new Baseline(objectives, 3);
        SchedulingSet currentSet = current.generateTestSet(), baselineSet = baseline.generateTestSet();
        MPSLGPTaskEvaluator.prepareFinalRuleTestSet(currentSet);
        baselineSet.setReplications(Collections.singletonList(2));
        GPRule aSeq = GPRule.readFromLispExpression(RuleType.SEQUENCING, "(+ PT (* Pone SL))");
        GPRule aRout = GPRule.readFromLispExpression(RuleType.ROUTING, "(+ WIQ (* Ptwo PT))");
        GPRule bSeq = GPRule.readFromLispExpression(RuleType.SEQUENCING, aSeq.getLispString());
        GPRule bRout = GPRule.readFromLispExpression(RuleType.ROUTING, aRout.getLispString());
        GPRuleEvolutionStatePSL currentState = MPSLGPTaskEvaluator.evaluationState(current.preferenceVectors());
        GPRuleEvolutionStatePSL referenceState = new GPRuleEvolutionStatePSL();
        referenceState.initializer = new PSLInitializer();
        ((PSLInitializer)referenceState.initializer).weights = baseline.vectors();
        referenceState.terminalNormalisation = 2;
        referenceState.evaluatePreferenceIndex = 0;
        bSeq.calcFitnessWithPreference(new MultiObjectiveFitness(), referenceState, baselineSet,
                bRout, objectives, 0);
        double[] initial = null;
        for (int index : new int[]{0, 1, 2, 0}) {
            double[] result = MPSLGPTaskEvaluator.evaluatePreference(aSeq, aRout, currentSet, objectives, currentState, index);
            referenceState.evaluatePreferenceIndex = index;
            MultiObjectiveFitness expected = new MultiObjectiveFitness();
            bSeq.calcFitnessWithPreference(expected, referenceState, baselineSet, bRout, objectives, index);
            check(Arrays.equals(result, expected.getObjectives()), "bit-identical PSLGP test objectives at preference " + index);
            if (initial == null) { initial = result; }
            else if (index == 0) {
                check(Arrays.equals(initial, result),
                        "repeat preference: " + Arrays.toString(initial) + " versus " + Arrays.toString(result));
            }
        }
    }

    private static List<Objective> objectives() {
        return Arrays.asList(Objective.get("max-flowtime"), Objective.get("max-weighted-tardiness"));
    }

    private static final class Baseline extends mengxu.ruleanalysis.ParetoSetLearning.ParetoSetLearningMultiTreeRuleTestMeng {
        Baseline(List<Objective> objectives, int count) {
            super("", RuleTypeV2.get("simple-rule"), 1, "dynamic-job-shop", "missing-0.7-1.5", objectives, 2, count);
            generatePreferences(count);
        }
        double[][] vectors() { return preferences; }
    }

    private static List<String> arguments(Path training, Path output, String... extra) {
        List<String> args = new ArrayList<>(Arrays.asList("--training-root", training.toString(), "--output", output.toString(),
                "--scenarios", "R1,H3", "--runs", "1", "--preferences", "3", "--replications", "1"));
        args.addAll(Arrays.asList(extra));
        return args;
    }

    private static void fixture(Path root, String scenario, int[] benchmarks) throws IOException {
        Path run = root.resolve("full").resolve("IGD").resolve(scenario).resolve("run00");
        Path attempt = run.resolve("attempt-0001");
        Files.createDirectories(attempt);
        Map<String, String> p = new TreeMap<>();
        p.put("seed.0", "0"); p.put("compare-criteria", "IGD"); p.put("terminal-normalisation", "2");
        p.put("num-trees", "2"); p.put("generations", "3"); p.put("pop.subpop.0.size", "24");
        p.put("mpslgp.num-tasks", Integer.toString(benchmarks.length));
        StringBuilder stats = new StringBuilder("Generation: 2\nTop N individuals by MPSLGP Task:\n\n");
        for (int task = 0; task < benchmarks.length; task++) {
            int benchmark = benchmarks[task];
            String key = "eval.problem." + task + ".eval-model";
            p.put(key + ".objectives", "2");
            p.put(key + ".objectives.0", benchmark <= 5 ? "max-flowtime" : "max-weighted-flowtime");
            p.put(key + ".objectives.1", benchmark <= 5 ? "max-weighted-tardiness" : "max-tardiness");
            p.put(key + ".sim-models.0.util-level", Double.toString(.70 + ((benchmark - 1) % 5) * .05));
            p.put(key + ".sim-models.0.due-date-factor", "1.5");
            String seq = task == 0 ? "(+ PT Pone)" : task == 1 ? "(+ SL Ptwo)" : "(- WKR Pone)";
            stats.append("MPSLGP Task ").append(task).append(" Top 1 individuals:\n")
                    .append("Evaluated: true\nFitness: [1.0 2.0]\nTree 0:\n").append(seq)
                    .append("\nTree 1:\n(+ WIQ Ptwo)\n\n");
        }
        stats.append("PARETO FRONTS BY MPSLGP TASK\n");
        atomicWrite(attempt.resolve("effective.params"), serialize(p));
        atomicWrite(attempt.resolve("job.0.out.stat"), stats.toString());
        Map<String, String> metadata = new TreeMap<>();
        metadata.put("algorithm", "full"); metadata.put("indicator", "IGD"); metadata.put("scenario", scenario);
        metadata.put("run", "0"); metadata.put("configuration.sha256", "test-fixture");
        atomicWrite(run.resolve("run.properties"), serialize(metadata));
        metadata = new TreeMap<>();
        metadata.put("status", "COMPLETED"); metadata.put("exit.code", "0"); metadata.put("attempt", "attempt-0001");
        metadata.put("configuration.sha256", "test-fixture");
        metadata.put("sha256.effective.params", fileHash(attempt.resolve("effective.params")));
        metadata.put("sha256.job.0.out.stat", fileHash(attempt.resolve("job.0.out.stat")));
        atomicWrite(run.resolve("completed.properties"), serialize(metadata));
    }

    private static void fakeOutputs(Path output) throws IOException {
        Properties spec = readProperties(output.resolve("test.properties"));
        int count = Integer.parseInt(spec.getProperty("preferences"));
        String prefix = spec.getProperty("run") + "," + spec.getProperty("generation") + ",3,2,3,2,2,";
        StringBuilder rows = new StringBuilder(MPSLGPTaskEvaluator.CSV_HEADER).append('\n');
        StringBuilder weights = new StringBuilder(MPSLGPTaskEvaluator.PREFERENCE_HEADER).append('\n');
        for (int i = 0; i < count; i++) {
            rows.append(prefix).append(spec.getProperty("training.objective.0")).append(",10.0,")
                    .append(spec.getProperty("training.objective.1")).append(",20.0\n");
            weights.append(i).append(',').append(i / (double)(count - 1)).append(',')
                    .append((count - 1 - i) / (double)(count - 1)).append(",10.0,20.0\n");
        }
        atomicWrite(output.resolve(spec.getProperty("output.csv")), rows.toString());
        atomicWrite(output.resolve("preference-results.csv"), weights.toString());
        atomicWrite(output.resolve("testing.log"), "Test worker completed.\n");
    }

    private static void setTestFitness(Path output, int index, double first, double second) throws IOException {
        Properties spec = readProperties(output.resolve("test.properties"));
        Path csv = output.resolve(spec.getProperty("output.csv")), detail = output.resolve("preference-results.csv");
        List<String> rows = Files.readAllLines(csv), details = Files.readAllLines(detail);
        String[] row = rows.get(index + 1).split(","), weights = details.get(index + 1).split(",");
        row[8] = weights[3] = Double.toString(first);
        row[10] = weights[4] = Double.toString(second);
        rows.set(index + 1, String.join(",", row)); details.set(index + 1, String.join(",", weights));
        Files.write(csv, rows); Files.write(detail, details);
    }

    private static Map<Path, String> hashes(Path root) throws IOException {
        Map<Path, String> hashes = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).toArray(Path[]::new)) { hashes.put(path, fileHash(path)); }
        }
        return hashes;
    }

    private static void check(boolean condition, String name) {
        assertions++;
        if (!condition) { throw new AssertionError(name); }
    }
}
