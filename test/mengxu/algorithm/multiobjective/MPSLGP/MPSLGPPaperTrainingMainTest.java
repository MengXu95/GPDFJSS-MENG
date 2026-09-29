package mengxu.algorithm.multiobjective.MPSLGP;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class MPSLGPPaperTrainingMainTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("mpslgp-runner-test-");
        try {
            testProfiles();
            testArguments();
            testSerialization();
            testDryRun(temp.resolve("dry"));
            testCompletionAndRetry(temp.resolve("resume"));
            testFailures(temp.resolve("fail"));
            testLock(temp.resolve("lock"));
            testParallel(temp.resolve("parallel"));
            System.out.println("PASS: MPSLGPPaperTrainingMainTest (" + assertions + " assertions)");
        } finally {
            try (Stream<Path> paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void testProfiles() throws Exception {
        MPSLGPPaperTrainingMain.Config c = MPSLGPPaperTrainingMain.Config.parse(new String[0]);
        check(c.algorithms.size() == 7 && c.scenarios.size() == 8 && c.runs == 30, "paper matrix");
        check(c.generations == 50 && c.population == 1000 && c.preferences == 101, "paper budget");
        check(c.indicators.equals(Arrays.asList("IGD")) && c.parallel == 1, "safe defaults");
        Map<String, String> base = MPSLGPPaperTrainingMain.loadParameters(c.param);
        for (String algorithm : c.algorithms) {
            for (String scenario : Arrays.asList("R1", "H3")) {
                MPSLGPPaperTrainingMain.Job job = new MPSLGPPaperTrainingMain.Job(
                        c, algorithm, "IGD", MPSLGPPaperTrainingMain.SCENARIOS.get(scenario), 2, base);
                Map<String, String> p = job.settings;
                check(p.get("compare-criteria").equals("IGD"), "indicator override");
                check(p.get("generations").equals("50") && p.get("pop.subpop.0.size").equals("1000"), "fixed shared budget");
                check(p.get("eval.problem.0.eval-model.sim-models.0.min-num-operations").equals("2"), "paper minimum operations");
                check(p.get("mpslgp.transfer-start-generation").equals("15"), "aligned transfer start");
                check(p.get("output-top-N").equals("1"), "one heuristic per task");
                check(p.get("pop.subpop.0.species.pipe.source.0").endsWith("OneTreeCrossoverPipeline"), "matched operator");
                check(p.get("mpslgp.task-allocation-offset").equals(scenario.equals("R1") ? "0" : "2"), "rotated quotas");
                double budget = Double.parseDouble(p.get("mpslgp.transfer-probability"));
                if (!algorithm.equals("no-transfer")) {
                    check(Math.abs(budget - (scenario.equals("R1") ? .8 : .4)) < 1e-12, "effective fixed budget");
                }
                switch (algorithm) {
                    case "no-transfer":
                        check(p.get("mpslgp.adaptive-transfer").equals("false") && budget == 0, "no transfer");
                        break;
                    case "fixed-transfer": check(p.get("mpslgp.adaptive-transfer").equals("false"), "fixed transfer"); break;
                    case "task-pair": check(p.get("mpslgp.preference-regions").equals("1"), "task pair"); break;
                    case "survival-only":
                        check(p.get("mpslgp.transfer-improvement-weight").equals("0.0")
                                && p.get("mpslgp.transfer-no-improvement-penalty").equals("0.0"), "survival only");
                        break;
                    case "no-gate": check(p.get("mpslgp.use-no-transfer-gate").equals("false"), "gate ablation"); break;
                    case "primary-task": check(p.get("mpslgp.contribution-aware-task-inheritance").equals("false"), "inheritance ablation"); break;
                    default: check(p.get("mpslgp.use-no-transfer-gate").equals("true"), "full method");
                }
                if (scenario.equals("R1")) {
                    check(!p.containsKey("eval.problem.2"), "no stale third task");
                } else {
                    check(job.quotas().equals("[333, 333, 334]"), "remainder allocation");
                }
            }
        }
        int[] coverage = new int[11];
        for (MPSLGPPaperTrainingMain.Scenario scenario : MPSLGPPaperTrainingMain.SCENARIOS.values()) {
            for (int task : scenario.tasks) { coverage[task]++; }
        }
        for (int task = 1; task <= 10; task++) { check(coverage[task] > 0, "scenario " + task + " covered"); }
    }

    private static void testArguments() {
        MPSLGPPaperTrainingMain.Config smoke = MPSLGPPaperTrainingMain.Config.parse(
                new String[]{"--smoke", "--skip", "existing", "--indicators", "gd,hv", "--scenarios", "r5"});
        check(smoke.skipExisting && smoke.population == 24 && smoke.generations == 3, "smoke and skip alias");
        check(smoke.results.endsWith("smoke") && smoke.transferStart == 1, "isolated smoke settings");
        check(smoke.indicators.equals(Arrays.asList("GD", "HV")) && smoke.scenarios.equals(Arrays.asList("R5")), "selectors");
        for (String[] bad : Arrays.asList(new String[]{"--runs"}, new String[]{"--runs", "0"},
                new String[]{"--algorithms", "PSLGP"}, new String[]{"--algorithms", "full,full"},
                new String[]{"--scenarios", "R6"}, new String[]{"--indicators", "bad"},
                new String[]{"--temperature", "NaN"}, new String[]{"--transfer-budget", "1.1"},
                new String[]{"--start-run", "2147483647", "--runs", "2"},
                new String[]{"--skip", "anything"}, new String[]{"--heap", "2g -cp bad"})) {
            boolean failed = false;
            try { MPSLGPPaperTrainingMain.Config.parse(bad); }
            catch (IllegalArgumentException e) { failed = true; }
            check(failed, "invalid args rejected: " + Arrays.toString(bad));
        }
    }

    private static void testSerialization() throws Exception {
        Map<String, String> values = new TreeMap<>();
        values.put("path", "C:\\folder with spaces\\\u6587\u4ef6");
        values.put("stat.file", "$job.0.out.stat");
        Properties props = new Properties();
        props.load(new StringReader(MPSLGPPaperTrainingMain.serialize(values)));
        check(props.getProperty("path").equals(values.get("path")), "Windows and Unicode path round trip");
        check(props.getProperty("stat.file").equals("$job.0.out.stat"), "ECJ cwd prefix retained");
    }

    private static void testDryRun(Path root) throws Exception {
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        int status = run(root, text, (command, directory, progress) -> {
            throw new AssertionError("Dry run started a process");
        }, "--dry-run", "--scenarios", "R5");
        check(status == 0, "dry run status");
        Path run = root.resolve("full/IGD/R5/run00");
        check(Files.isRegularFile(run.resolve("effective.params")), "dry configuration exported");
        check(!Files.exists(run.resolve("completed.properties")), "dry run is not completion");
        check(!Files.exists(run.resolve("attempt-0001")), "dry run does not create attempt");
        Properties p = properties(run.resolve("effective.params"));
        check(p.getProperty("eval.problem.1.eval-model.sim-models.0.util-level").equals("0.85"), "R5 covers scenario 9");
        check(text.toString("UTF-8").contains("[DRY-RUN] algorithm=full indicator=IGD scenario=R5 run=0"), "identified dry progress");
    }

    private static void testCompletionAndRetry(Path root) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MPSLGPPaperTrainingMain.ProcessRunner fake = (command, directory, progress) -> {
            calls.incrementAndGet();
            progress.accept("Generation 0");
            success(directory);
            return 0;
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        check(run(root, output, fake, "--skip-existing") == 0 && calls.get() == 1, "first successful attempt");
        Path directory = root.resolve("full/IGD/R1/run00");
        check(Files.exists(directory.resolve("completed.properties")), "success marker");
        String progress = output.toString("UTF-8");
        check(progress.contains("[START] algorithm=full indicator=IGD scenario=R1 run=0 seed=0")
                && progress.contains("[GEN]") && progress.contains("[DONE]"), "live identified progress");
        check(run(root, output, fake, "--skip", "existing") == 0 && calls.get() == 1, "verified success skipped");
        check(run(root, output, fake) == 1 && calls.get() == 1, "explicit skip required for completed work");
        Path oldStats = directory.resolve("attempt-0001/job.0.out.stat");
        Files.writeString(oldStats, "truncated");
        check(run(root, output, fake, "--skip-existing") == 0 && calls.get() == 2, "corrupt success retried");
        check(Files.readString(oldStats).equals("truncated"), "old attempt preserved");
        check(properties(directory.resolve("completed.properties")).getProperty("attempt").equals("attempt-0002"), "new attempt recorded");
        check(run(root, output, fake, "--skip-existing", "--population", "1200") == 1 && calls.get() == 2,
                "changed configuration does not get skipped or overwritten");
    }

    private static void testFailures(Path root) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        check(run(root, output, (command, directory, progress) -> 7, "--skip-existing") == 1, "child nonzero exit fails");
        Path directory = root.resolve("full/IGD/R1/run00");
        check(!Files.exists(directory.resolve("completed.properties")), "no completion on failure");
        check(run(root, output, (command, attempt, progress) -> {
            success(attempt);
            return 0;
        }, "--skip-existing") == 0, "failed run resumes");
        check(Files.exists(directory.resolve("attempt-0001/status.properties"))
                && Files.exists(directory.resolve("attempt-0002/status.properties")), "failure and success both retained");
        Path incomplete = root.resolve("empty");
        check(run(incomplete, output, (command, attempt, progress) -> 0) == 1, "zero exit without artifacts fails");
    }

    private static void testLock(Path root) throws Exception {
        Path directory = root.resolve("full/IGD/R1/run00");
        Files.createDirectories(directory);
        try (FileChannel channel = FileChannel.open(directory.resolve("run.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            check(run(root, new ByteArrayOutputStream(), (command, attempt, progress) -> {
                throw new AssertionError("Locked job executed");
            }, "--skip-existing") == 1, "concurrent run lock enforced");
        }
    }

    private static void testParallel(Path root) throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        int status = MPSLGPPaperTrainingMain.execute(new String[]{"--results", root.toString(),
                "--algorithms", "full", "--scenarios", "R1", "--runs", "4", "--parallel", "2"},
                new PrintStream(new ByteArrayOutputStream()), (command, directory, progress) -> {
                    int count = active.incrementAndGet();
                    peak.accumulateAndGet(count, Math::max);
                    try {
                        Thread.sleep(25);
                        success(directory);
                        done.incrementAndGet();
                        return 0;
                    } finally { active.decrementAndGet(); }
                });
        check(status == 0 && done.get() == 4, "all parallel runs complete");
        check(peak.get() == 2, "parallelism limit");
    }

    private static int run(Path root, ByteArrayOutputStream output,
                           MPSLGPPaperTrainingMain.ProcessRunner runner, String... extra) throws Exception {
        List<String> arguments = new ArrayList<>(Arrays.asList("--results", root.toString(),
                "--algorithms", "full", "--runs", "1"));
        if (!Arrays.asList(extra).contains("--scenarios")) {
            arguments.addAll(Arrays.asList("--scenarios", "R1"));
        }
        arguments.addAll(Arrays.asList(extra));
        return MPSLGPPaperTrainingMain.execute(arguments.toArray(new String[0]),
                new PrintStream(output, true, "UTF-8"), runner);
    }

    private static Properties properties(Path path) throws IOException {
        Properties props = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { props.load(reader); }
        return props;
    }

    private static void success(Path directory) throws IOException {
        Properties p = properties(directory.resolve("effective.params"));
        int tasks = Integer.parseInt(p.getProperty("mpslgp.num-tasks"));
        int last = Integer.parseInt(p.getProperty("generations")) - 1;
        String seed = p.getProperty("seed.0");
        StringBuilder stats = new StringBuilder("Generation: " + last + "\nPARETO FRONTS BY MPSLGP TASK\n");
        for (int task = 0; task < tasks; task++) {
            stats.append("MPSLGP Task ").append(task).append(" Top 1 individuals:\n");
        }
        Files.writeString(directory.resolve("job." + seed + ".out.stat"), stats.toString());
        Files.writeString(directory.resolve("job." + seed + ".front.stat"), "0 1.0 1.0\n");
        Files.writeString(directory.resolve("training.log"), "Generation " + last + "\nComplete\n");
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) { throw new AssertionError(description); }
    }
}
