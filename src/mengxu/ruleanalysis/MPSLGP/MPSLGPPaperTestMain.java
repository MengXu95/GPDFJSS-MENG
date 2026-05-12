package mengxu.ruleanalysis.MPSLGP;

import yimei.jss.ruleanalysis.RuleTypeV2;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MPSLGPPaperTestMain {
    private static final int DEFAULT_RUNS = 30;
    private static final int DEFAULT_PREFERENCES = 3;
    private static final int DEFAULT_TOP_N = 5;
    private static final int DEFAULT_NUM_TREES = 2;
    private static final int DEFAULT_PARALLELISM = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), DEFAULT_RUNS));
    private static final Path DEFAULT_RESULTS_ROOT = Paths.get("D:\\javaProject\\GPDFJSS-MENG\\src\\mengxu\\ruleanalysis\\MPSLGP\\results");
    private static final String DEFAULT_RULE_TYPE = "simple-rule";
    private static final String DEFAULT_TEST_SCENARIO = "dynamic-job-shop";
    private static final double DEFAULT_DUE_DATE_FACTOR = 1.5;

    private static final String F_MAX = "max-flowtime";
    private static final String WT_MAX = "max-weighted-tardiness";
    private static final String WF_MAX = "max-weighted-flowtime";
    private static final String T_MAX = "max-tardiness";

    public static void main(String[] args) throws Exception {
        Config config = Config.fromArgs(args);
        RuleTypeV2 ruleType = RuleTypeV2.get(config.ruleTypeName);
        if (ruleType == null) {
            throw new IllegalArgumentException("Unknown rule type: " + config.ruleTypeName);
        }

        List<Scenario> scenarios = selectedScenarios(config.scenarioNames);
        List<TestJob> jobs = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            for (int run = 0; run < config.runs; run++) {
                Path runDirectory = config.resultsRoot.resolve(scenario.name).resolve(String.format(Locale.ROOT, "run%02d", run));
                Scenario runScenario = loadScenario(runDirectory, scenario);
                for (int taskIndex = 0; taskIndex < runScenario.tasks.size(); taskIndex++) {
                    jobs.add(new TestJob(runScenario, run, taskIndex, runScenario.tasks.get(taskIndex), ruleType, config));
                }
            }
        }

        Files.createDirectories(config.resultsRoot);
        writePlan(config, jobs);

        System.out.println("MPSLGP paper test runner");
        System.out.println("Scenarios: " + scenarioNames(scenarios));
        System.out.println("Runs per scenario: " + config.runs);
        System.out.println("Parallel workers: " + config.parallelism);
        System.out.println("Results root: " + config.resultsRoot.toAbsolutePath());
        System.out.println("Test jobs: " + jobs.size());

        if (config.dryRun) {
            for (TestJob job : jobs) {
                System.out.println(job.dryRunLine());
            }
            System.out.println("Dry run finished. No test was executed.");
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(config.parallelism);
        List<Future<TestResultRecord>> futures = new ArrayList<>();
        for (TestJob job : jobs) {
            futures.add(executor.submit(job));
        }
        executor.shutdown();

        List<TestResultRecord> results = new ArrayList<>();
        boolean failed = false;
        for (Future<TestResultRecord> future : futures) {
            TestResultRecord result = future.get();
            results.add(result);
            System.out.println(result.summaryLine());
            if (!result.success && !result.skipped) {
                failed = true;
            }
        }

        writeSummary(config.resultsRoot, results);
        if (failed) {
            throw new IllegalStateException("At least one MPSLGP test job failed. See paper-test-summary.csv under " + config.resultsRoot.toAbsolutePath());
        }
    }

    private static List<Scenario> allScenarios() {
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(new Scenario("R1", task(F_MAX, WT_MAX, 0.70), task(F_MAX, WT_MAX, 0.75)));
        scenarios.add(new Scenario("R2", task(F_MAX, WT_MAX, 0.80), task(F_MAX, WT_MAX, 0.85)));
        scenarios.add(new Scenario("R3", task(WF_MAX, T_MAX, 0.70), task(WF_MAX, T_MAX, 0.75)));
        scenarios.add(new Scenario("R4", task(F_MAX, WT_MAX, 0.70), task(F_MAX, WT_MAX, 0.80), task(F_MAX, WT_MAX, 0.90)));
        scenarios.add(new Scenario("H1", task(F_MAX, WT_MAX, 0.70), task(WF_MAX, T_MAX, 0.90)));
        scenarios.add(new Scenario("H2", task(F_MAX, WT_MAX, 0.90), task(WF_MAX, T_MAX, 0.70)));
        scenarios.add(new Scenario("H3", task(F_MAX, WT_MAX, 0.70), task(F_MAX, WT_MAX, 0.90), task(WF_MAX, T_MAX, 0.80)));
        return scenarios;
    }

    private static Scenario loadScenario(Path runDirectory, Scenario fallbackScenario) throws IOException {
        Path scenarioFile = runDirectory.resolve("scenario.txt");
        if (!Files.exists(scenarioFile)) {
            return fallbackScenario;
        }

        String scenarioName = fallbackScenario.name;
        List<Task> tasks = new ArrayList<>();
        for (String line : Files.readAllLines(scenarioFile)) {
            if (line.startsWith("Scenario:")) {
                scenarioName = line.substring("Scenario:".length()).trim();
            }
            else if (line.startsWith("Task ")) {
                tasks.add(parseTask(line));
            }
        }
        if (tasks.isEmpty()) {
            return fallbackScenario;
        }
        return new Scenario(scenarioName, tasks);
    }

    private static Task parseTask(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Invalid task line in scenario.txt: " + line);
        }
        String[] parts = line.substring(colon + 1).split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid task line in scenario.txt: " + line);
        }
        String objective0 = parts[0].trim();
        String objective1 = parts[1].trim();
        String utilizationPart = parts[2].trim();
        String marker = "util=";
        int markerIndex = utilizationPart.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("Invalid utilization in scenario.txt: " + line);
        }
        double utilization = Double.parseDouble(utilizationPart.substring(markerIndex + marker.length()).trim());
        return task(objective0, objective1, utilization);
    }

    private static Task task(String objective0, String objective1, double utilization) {
        return new Task(objective0, objective1, utilization);
    }

    private static List<Scenario> selectedScenarios(Set<String> requestedNames) {
        List<Scenario> all = allScenarios();
        if (requestedNames.isEmpty()) {
            return all;
        }
        Map<String, Scenario> byName = new LinkedHashMap<>();
        for (Scenario scenario : all) {
            byName.put(scenario.name.toLowerCase(Locale.ROOT), scenario);
        }
        List<Scenario> selected = new ArrayList<>();
        for (String requestedName : requestedNames) {
            Scenario scenario = byName.get(requestedName.toLowerCase(Locale.ROOT));
            if (scenario == null) {
                throw new IllegalArgumentException("Unknown scenario: " + requestedName + ". Valid scenarios are " + byName.keySet());
            }
            selected.add(scenario);
        }
        return selected;
    }

    private static String scenarioNames(List<Scenario> scenarios) {
        List<String> names = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            names.add(scenario.name);
        }
        return String.join(",", names);
    }

    private static void writePlan(Config config, List<TestJob> jobs) throws IOException {
        Path planFile = config.resultsRoot.resolve("paper-test-plan.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(planFile)) {
            writer.write("Created," + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.newLine();
            writer.write("Runs," + config.runs);
            writer.newLine();
            writer.write("Parallelism," + config.parallelism);
            writer.newLine();
            writer.write("Preferences," + config.preferences);
            writer.newLine();
            writer.write("TopN," + config.topN);
            writer.newLine();
            writer.write("Scenario,Run,Task,Objective0,Objective1,Utilization,TrainStat,OutputCsv");
            writer.newLine();
            for (TestJob job : jobs) {
                writer.write(job.planLine());
                writer.newLine();
            }
        }
    }

    private static void writeSummary(Path resultsRoot, List<TestResultRecord> results) throws IOException {
        Path summaryFile = resultsRoot.resolve("paper-test-summary.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(summaryFile)) {
            writer.write("Scenario,Run,Task,TestSetName,Success,Skipped,DurationSeconds,Message,OutputCsv");
            writer.newLine();
            for (TestResultRecord result : results) {
                writer.write(result.csvLine());
                writer.newLine();
            }
        }
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static class TestJob implements Callable<TestResultRecord> {
        private final Scenario scenario;
        private final int run;
        private final int taskIndex;
        private final Task task;
        private final RuleTypeV2 ruleType;
        private final Config config;
        private final Path runDirectory;
        private final Path statFile;
        private final String testSetName;

        private TestJob(Scenario scenario, int run, int taskIndex, Task task, RuleTypeV2 ruleType, Config config) {
            this.scenario = scenario;
            this.run = run;
            this.taskIndex = taskIndex;
            this.task = task;
            this.ruleType = ruleType;
            this.config = config;
            this.runDirectory = config.resultsRoot.resolve(scenario.name).resolve(String.format(Locale.ROOT, "run%02d", run));
            this.statFile = runDirectory.resolve("job." + run + ".out.stat");
            this.testSetName = "missing-" + formatNumber(task.utilization) + "-" + formatNumber(config.dueDateFactor);
        }

        @Override
        public TestResultRecord call() {
            long start = System.currentTimeMillis();
            Path outputCsv = outputCsv();
            try {
                if (!Files.exists(statFile)) {
                    String message = "Missing training stat file: " + statFile.toAbsolutePath();
                    if (config.skipMissing) {
                        return TestResultRecord.skipped(scenario.name, run, taskIndex, testSetName, elapsedSeconds(start), message, outputCsv);
                    }
                    return TestResultRecord.failed(scenario.name, run, taskIndex, testSetName, elapsedSeconds(start), message, outputCsv);
                }

                String trainPath = runDirectory.toAbsolutePath() + File.separator;
                ParetoSetLearningMultiTreeRuleTestMeng test = new ParetoSetLearningMultiTreeRuleTestMeng(
                        trainPath,
                        ruleType,
                        config.runs,
                        config.testScenario,
                        testSetName,
                        config.numTrees,
                        config.preferences);
                test.addObjective(task.objective0);
                test.addObjective(task.objective1);
                test.writeEachRunToCSV(run, config.topN, config.ensemble);

                boolean outputExists = Files.exists(outputCsv);
                String message = outputExists ? "OK" : "Test finished but expected output CSV was not found.";
                return new TestResultRecord(scenario.name, run, taskIndex, testSetName, outputExists, false, elapsedSeconds(start), message, outputCsv);
            }
            catch (Exception exception) {
                return TestResultRecord.failed(scenario.name, run, taskIndex, testSetName, elapsedSeconds(start), exception.toString(), outputCsv);
            }
        }

        private String dryRunLine() {
            return scenario.name + " run=" + run + " task=" + taskIndex
                    + " objectives=" + task.objective0 + "/" + task.objective1
                    + " testSet=" + testSetName
                    + " stat=" + statFile.toAbsolutePath()
                    + " output=" + outputCsv().toAbsolutePath();
        }

        private String planLine() {
            return scenario.name + "," + run + "," + taskIndex + "," + task.objective0 + "," + task.objective1 + "," + task.utilization + ","
                    + statFile.toAbsolutePath() + "," + outputCsv().toAbsolutePath();
        }

        private Path outputCsv() {
            return runDirectory.resolve("test").resolve(testSetName + "-" + run + "-ParetoFront.csv");
        }

        private long elapsedSeconds(long start) {
            return (System.currentTimeMillis() - start) / 1000L;
        }
    }

    private static String formatNumber(double value) {
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(value);
    }

    private static class Config {
        private int runs = DEFAULT_RUNS;
        private int preferences = DEFAULT_PREFERENCES;
        private int topN = DEFAULT_TOP_N;
        private int numTrees = DEFAULT_NUM_TREES;
        private int parallelism = DEFAULT_PARALLELISM;
        private Path resultsRoot = DEFAULT_RESULTS_ROOT;
        private String ruleTypeName = DEFAULT_RULE_TYPE;
        private String testScenario = DEFAULT_TEST_SCENARIO;
        private double dueDateFactor = DEFAULT_DUE_DATE_FACTOR;
        private boolean ensemble = false;
        private boolean skipMissing = false;
        private boolean dryRun = false;
        private Set<String> scenarioNames = new LinkedHashSet<>();

        private static Config fromArgs(String[] args) {
            Config config = new Config();
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if ("--runs".equals(arg)) {
                    config.runs = Integer.parseInt(args[++index]);
                }
                else if ("--parallel".equals(arg)) {
                    config.parallelism = Integer.parseInt(args[++index]);
                }
                else if ("--results".equals(arg)) {
                    config.resultsRoot = Paths.get(args[++index]);
                }
                else if ("--scenarios".equals(arg)) {
                    config.scenarioNames.addAll(Arrays.asList(args[++index].split(",")));
                }
                else if ("--preferences".equals(arg)) {
                    config.preferences = Integer.parseInt(args[++index]);
                }
                else if ("--topN".equals(arg)) {
                    config.topN = Integer.parseInt(args[++index]);
                }
                else if ("--numTrees".equals(arg)) {
                    config.numTrees = Integer.parseInt(args[++index]);
                }
                else if ("--ruleType".equals(arg)) {
                    config.ruleTypeName = args[++index];
                }
                else if ("--testScenario".equals(arg)) {
                    config.testScenario = args[++index];
                }
                else if ("--dueDateFactor".equals(arg)) {
                    config.dueDateFactor = Double.parseDouble(args[++index]);
                }
                else if ("--ensemble".equals(arg)) {
                    config.ensemble = true;
                }
                else if ("--skip-missing".equals(arg)) {
                    config.skipMissing = true;
                }
                else if ("--dry-run".equals(arg)) {
                    config.dryRun = true;
                }
                else if ("--help".equals(arg) || "-h".equals(arg)) {
                    printUsageAndExit();
                }
                else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (config.runs < 1) {
                throw new IllegalArgumentException("--runs must be positive.");
            }
            if (config.parallelism < 1) {
                throw new IllegalArgumentException("--parallel must be positive.");
            }
            if (config.preferences < 2) {
                throw new IllegalArgumentException("--preferences must be at least 2 for two-objective preference sampling.");
            }
            if (config.topN < 1) {
                throw new IllegalArgumentException("--topN must be positive.");
            }
            return config;
        }

        private static void printUsageAndExit() {
            System.out.println("Usage: java mengxu.ruleanalysis.MPSLGP.MPSLGPPaperTestMain [options]");
            System.out.println("  --runs N              Runs per scenario. Default: " + DEFAULT_RUNS);
            System.out.println("  --parallel N          Number of parallel test jobs. Default: " + DEFAULT_PARALLELISM);
            System.out.println("  --results PATH        Training/result root. Default: " + DEFAULT_RESULTS_ROOT);
            System.out.println("  --scenarios R1,H1     Comma-separated subset. Default: all R1,R2,R3,R4,H1,H2,H3");
            System.out.println("  --preferences N       Number of test preferences. Default: " + DEFAULT_PREFERENCES);
            System.out.println("  --topN N              Number of top individuals per preference. Default: " + DEFAULT_TOP_N);
            System.out.println("  --ruleType NAME       Rule type. Default: " + DEFAULT_RULE_TYPE);
            System.out.println("  --testScenario NAME   Scheduling scenario. Default: " + DEFAULT_TEST_SCENARIO);
            System.out.println("  --dueDateFactor X     Due-date factor in test-set names. Default: " + DEFAULT_DUE_DATE_FACTOR);
            System.out.println("  --ensemble            Evaluate ensemble top-N output.");
            System.out.println("  --skip-missing        Skip missing run outputs instead of marking them failed.");
            System.out.println("  --dry-run             Print test jobs without executing tests.");
            System.exit(0);
        }
    }

    private static class Scenario {
        private final String name;
        private final List<Task> tasks;

        private Scenario(String name, Task... tasks) {
            this.name = name;
            this.tasks = Arrays.asList(tasks);
        }

        private Scenario(String name, List<Task> tasks) {
            this.name = name;
            this.tasks = tasks;
        }
    }

    private static class Task {
        private final String objective0;
        private final String objective1;
        private final double utilization;

        private Task(String objective0, String objective1, double utilization) {
            this.objective0 = objective0;
            this.objective1 = objective1;
            this.utilization = utilization;
        }
    }

    private static class TestResultRecord {
        private final String scenarioName;
        private final int run;
        private final int taskIndex;
        private final String testSetName;
        private final boolean success;
        private final boolean skipped;
        private final long durationSeconds;
        private final String message;
        private final Path outputCsv;

        private TestResultRecord(String scenarioName, int run, int taskIndex, String testSetName,
                                 boolean success, boolean skipped, long durationSeconds, String message, Path outputCsv) {
            this.scenarioName = scenarioName;
            this.run = run;
            this.taskIndex = taskIndex;
            this.testSetName = testSetName;
            this.success = success;
            this.skipped = skipped;
            this.durationSeconds = durationSeconds;
            this.message = message;
            this.outputCsv = outputCsv;
        }

        private static TestResultRecord failed(String scenarioName, int run, int taskIndex, String testSetName,
                                               long durationSeconds, String message, Path outputCsv) {
            return new TestResultRecord(scenarioName, run, taskIndex, testSetName, false, false, durationSeconds, message, outputCsv);
        }

        private static TestResultRecord skipped(String scenarioName, int run, int taskIndex, String testSetName,
                                                long durationSeconds, String message, Path outputCsv) {
            return new TestResultRecord(scenarioName, run, taskIndex, testSetName, false, true, durationSeconds, message, outputCsv);
        }

        private String summaryLine() {
            if (skipped) {
                return scenarioName + " run " + run + " task " + taskIndex + " skipped: " + message;
            }
            return scenarioName + " run " + run + " task " + taskIndex + " success=" + success + " duration=" + durationSeconds + "s output=" + outputCsv.toAbsolutePath();
        }

        private String csvLine() {
            return scenarioName + "," + run + "," + taskIndex + "," + testSetName + "," + success + "," + skipped + ","
                    + durationSeconds + "," + csvEscape(message) + "," + outputCsv.toAbsolutePath();
        }
    }
}