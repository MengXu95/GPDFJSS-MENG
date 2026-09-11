package mengxu.algorithm.multiobjective.MPSLGP;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.regex.Pattern;

public class MPSLGPPaperTrainingMain {
    private static final int DEFAULT_RUNS = 3;
    private static final int DEFAULT_PARALLELISM = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), DEFAULT_RUNS));
    private static final Path DEFAULT_RESULTS_ROOT = Paths.get("D:\\javaProject\\GPDFJSS-MENG\\src\\mengxu\\ruleanalysis\\MPSLGP\\results");
    private static final Path DEFAULT_PARAM_FILE = Paths.get("D:\\javaProject\\GPDFJSS-MENG\\src\\mengxu\\algorithm\\multiobjective\\MPSLGP\\multipletreegp-dynamic-MPSLGP-3tasks.params");
    private static final String GPRUN_MAIN_CLASS = "yimei.jss.gp.GPRun";

    private static final String F_MAX = "max-flowtime";
    private static final String WT_MAX = "max-weighted-tardiness";
    private static final String WF_MAX = "max-weighted-flowtime";
    private static final String T_MAX = "max-tardiness";

    public static void main(String[] args) throws Exception {
        Config config = Config.fromArgs(args);
        List<Scenario> scenarios = selectedScenarios(config.scenarioNames);

        Files.createDirectories(config.resultsRoot);
        writePlan(config, scenarios);

        List<TrainingJob> jobs = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            for (int run = 0; run < config.runs; run++) {
                jobs.add(new TrainingJob(scenario, run, config));
            }
        }

        System.out.println("MPSLGP paper training runner");
        System.out.println("Scenarios: " + scenarioNames(scenarios));
        System.out.println("Runs per scenario: " + config.runs);
        System.out.println("Parallel workers: " + config.parallelism);
        System.out.println("Results root: " + config.resultsRoot.toAbsolutePath());
        System.out.println("Parameter file: " + config.paramFile.toAbsolutePath());

        if (config.dryRun) {
            for (TrainingJob job : jobs) {
                job.prepare();
                System.out.println(String.join(" ", job.command()));
            }
            System.out.println("Dry run finished. No training process was started.");
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(config.parallelism);
        List<Future<RunResult>> futures = new ArrayList<>();
        for (TrainingJob job : jobs) {
            futures.add(executor.submit(job));
        }
        executor.shutdown();

        List<RunResult> results = new ArrayList<>();
        boolean failed = false;
        for (Future<RunResult> future : futures) {
            RunResult result = future.get();
            results.add(result);
            System.out.println(result.summaryLine());
            if (result.exitCode != 0) {
                failed = true;
            }
        }

        writeSummary(config.resultsRoot, results);
        if (failed) {
            throw new IllegalStateException("At least one MPSLGP training run failed. See training-summary.csv and run logs under " + config.resultsRoot.toAbsolutePath());
        }
    }

    private static List<Scenario> allScenarios() {
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(new Scenario("R1", "Related two-task, adjacent utilization, Fmax-WTmax", task(F_MAX, WT_MAX, 0.70), task(F_MAX, WT_MAX, 0.75)));
        scenarios.add(new Scenario("R2", "Related two-task, adjacent utilization, Fmax-WTmax", task(F_MAX, WT_MAX, 0.80), task(F_MAX, WT_MAX, 0.85)));
        scenarios.add(new Scenario("R3", "Related two-task, adjacent utilization, WFmax-Tmax", task(WF_MAX, T_MAX, 0.70), task(WF_MAX, T_MAX, 0.75)));
        scenarios.add(new Scenario("R4", "Related three-task, low-medium-high utilization, Fmax-WTmax", task(F_MAX, WT_MAX, 0.70), task(F_MAX, WT_MAX, 0.80), task(F_MAX, WT_MAX, 0.90)));
        scenarios.add(new Scenario("H1", "Heterogeneous two-task, different objectives and distant utilization", task(F_MAX, WT_MAX, 0.70), task(WF_MAX, T_MAX, 0.90)));
        scenarios.add(new Scenario("H2", "Heterogeneous two-task, reversed objective/load relation", task(F_MAX, WT_MAX, 0.90), task(WF_MAX, T_MAX, 0.70)));
        scenarios.add(new Scenario("H3", "Heterogeneous three-task, mixed objectives and utilization", task(F_MAX, WT_MAX, 0.70), task(F_MAX, WT_MAX, 0.90), task(WF_MAX, T_MAX, 0.80)));
        return scenarios;
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

    private static void writePlan(Config config, List<Scenario> scenarios) throws IOException {
        Path planFile = config.resultsRoot.resolve("paper-training-plan.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(planFile)) {
            writer.write("Created," + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.newLine();
            writer.write("ParamFile," + config.paramFile.toAbsolutePath());
            writer.newLine();
            writer.write("Runs," + config.runs);
            writer.newLine();
            writer.write("Parallelism," + config.parallelism);
            writer.newLine();
            writer.write("Scenario,Description,Task,Objective0,Objective1,Utilization");
            writer.newLine();
            for (Scenario scenario : scenarios) {
                for (int taskIndex = 0; taskIndex < scenario.tasks.size(); taskIndex++) {
                    Task task = scenario.tasks.get(taskIndex);
                    writer.write(scenario.name + "," + scenario.description + "," + taskIndex + "," + task.objective0 + "," + task.objective1 + "," + task.utilization);
                    writer.newLine();
                }
            }
        }
    }

    private static void writeSummary(Path resultsRoot, List<RunResult> results) throws IOException {
        Path summaryFile = resultsRoot.resolve("training-summary.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(summaryFile)) {
            writer.write("Scenario,Run,ExitCode,DurationSeconds,RunDirectory");
            writer.newLine();
            for (RunResult result : results) {
                writer.write(result.scenarioName + "," + result.run + "," + result.exitCode + "," + result.durationSeconds + "," + result.runDirectory.toAbsolutePath());
                writer.newLine();
            }
        }
    }

    private static class TrainingJob implements Callable<RunResult> {
        private final Scenario scenario;
        private final int run;
        private final Config config;
        private Path runDirectory;

        private TrainingJob(Scenario scenario, int run, Config config) {
            this.scenario = scenario;
            this.run = run;
            this.config = config;
        }

        @Override
        public RunResult call() throws Exception {
            prepare();
            long start = System.currentTimeMillis();
            ProcessBuilder builder = new ProcessBuilder(command());
            builder.directory(runDirectory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.to(runDirectory.resolve("training.log").toFile()));
            Process process = builder.start();
            int exitCode = process.waitFor();
            long durationSeconds = (System.currentTimeMillis() - start) / 1000L;
            return new RunResult(scenario.name, run, exitCode, durationSeconds, runDirectory);
        }

        private void prepare() throws IOException {
            runDirectory = config.resultsRoot.resolve(scenario.name).resolve(String.format(Locale.ROOT, "run%02d", run));
            Files.createDirectories(runDirectory);
            writeScenarioReadme();
        }

        private List<String> command() {
            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-cp");
            command.add(resolvedClassPath());
            command.add(GPRUN_MAIN_CLASS);
            command.add("-file");
            command.add(config.paramFile.toAbsolutePath().toString());
            addParameter(command, "seed.0", String.valueOf(run));
            addParameter(command, "mpslgp.num-tasks", String.valueOf(scenario.tasks.size()));
            addParameter(command, "stat.file", runDirectory.resolve("job." + run + ".out.stat").toAbsolutePath().toString());
            addParameter(command, "stat.front", runDirectory.resolve("job." + run + ".front.stat").toAbsolutePath().toString());
            addBaseProblem(command, scenario.tasks.get(0));
            for (int taskIndex = 0; taskIndex < scenario.tasks.size(); taskIndex++) {
                addTaskProblem(command, taskIndex, scenario.tasks.get(taskIndex));
            }
            return command;
        }

        private void writeScenarioReadme() throws IOException {
            Path readme = runDirectory.resolve("scenario.txt");
            try (BufferedWriter writer = Files.newBufferedWriter(readme)) {
                writer.write("Scenario: " + scenario.name);
                writer.newLine();
                writer.write("Description: " + scenario.description);
                writer.newLine();
                writer.write("Run: " + run);
                writer.newLine();
                for (int taskIndex = 0; taskIndex < scenario.tasks.size(); taskIndex++) {
                    Task task = scenario.tasks.get(taskIndex);
                    writer.write("Task " + taskIndex + ": " + task.objective0 + ", " + task.objective1 + ", util=" + task.utilization);
                    writer.newLine();
                }
            }
        }
    }

    private static void addBaseProblem(List<String> command, Task task) {
        addEvaluationProblem(command, "eval.problem", task);
    }

    private static void addTaskProblem(List<String> command, int taskIndex, Task task) {
        addParameter(command, "eval.problem." + taskIndex, "yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem");
        addEvaluationProblem(command, "eval.problem." + taskIndex, task);
    }

    private static void addEvaluationProblem(List<String> command, String prefix, Task task) {
        addParameter(command, prefix + ".data", "yimei.jss.gp.data.DoubleData");
        addParameter(command, prefix + ".eval-model", "mengxu.complexsimulation.ruleevaluation.MultipleTreeMultipleRuleHeterogeneousEvaluationModel");
        addParameter(command, prefix + ".eval-model.objectives", "2");
        addParameter(command, prefix + ".eval-model.objectives.0", task.objective0);
        addParameter(command, prefix + ".eval-model.objectives.1", task.objective1);
        addParameter(command, prefix + ".eval-model.sim-models", "1");
        addParameter(command, prefix + ".eval-model.sim-models.0.util-level", String.valueOf(task.utilization));
        addParameter(command, prefix + ".eval-model.sim-models.0.num-jobs", "5000");
        addParameter(command, prefix + ".eval-model.sim-models.0.warmup-jobs", "1000");
        addParameter(command, prefix + ".eval-model.sim-models.0.replications", "1");
        addParameter(command, prefix + ".eval-model.sim-models.0.num-machines", "10");
        addParameter(command, prefix + ".eval-model.rotate-sim-seed", "true");
        addParameter(command, prefix + ".eval-model.sim-models.0.due-date-factor", "1.5");
    }

    private static void addParameter(List<String> command, String key, String value) {
        command.add("-p");
        command.add(key + "=" + value);
    }

    private static String javaExecutable() {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path java = javaHome.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        return java.toAbsolutePath().toString();
    }

    private static String resolvedClassPath() {
        String classPath = System.getProperty("java.class.path");
        String[] entries = classPath.split(Pattern.quote(File.pathSeparator));
        List<String> resolvedEntries = new ArrayList<>();
        Path currentDirectory = Paths.get("").toAbsolutePath();
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            Path path = Paths.get(entry);
            if (!path.isAbsolute()) {
                path = currentDirectory.resolve(path).normalize();
            }
            resolvedEntries.add(path.toString());
        }
        return String.join(File.pathSeparator, resolvedEntries);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static class Config {
        private int runs = DEFAULT_RUNS;
        private int parallelism = DEFAULT_PARALLELISM;
        private Path resultsRoot = DEFAULT_RESULTS_ROOT;
        private Path paramFile = DEFAULT_PARAM_FILE;
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
                else if ("--param".equals(arg)) {
                    config.paramFile = Paths.get(args[++index]);
                }
                else if ("--scenarios".equals(arg)) {
                    config.scenarioNames.addAll(Arrays.asList(args[++index].split(",")));
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
            return config;
        }

        private static void printUsageAndExit() {
            System.out.println("Usage: java mengxu.ruleanalysis.MPSLGP.MPSLGPPaperTrainingMain [options]");
            System.out.println("  --runs N            Runs per scenario. Default: " + DEFAULT_RUNS);
            System.out.println("  --parallel N        Number of parallel Java training processes. Default: " + DEFAULT_PARALLELISM);
            System.out.println("  --results PATH      Result root. Default: " + DEFAULT_RESULTS_ROOT);
            System.out.println("  --param PATH        MPSLGP parameter file. Default: " + DEFAULT_PARAM_FILE);
            System.out.println("  --scenarios R1,H1   Comma-separated subset. Default: all R1,R2,R3,R4,H1,H2,H3");
            System.out.println("  --dry-run           Print commands and create run folders without training.");
            System.exit(0);
        }
    }

    private static class Scenario {
        private final String name;
        private final String description;
        private final List<Task> tasks;

        private Scenario(String name, String description, Task... tasks) {
            this.name = name;
            this.description = description;
            this.tasks = Arrays.asList(tasks);
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

    private static class RunResult {
        private final String scenarioName;
        private final int run;
        private final int exitCode;
        private final long durationSeconds;
        private final Path runDirectory;

        private RunResult(String scenarioName, int run, int exitCode, long durationSeconds, Path runDirectory) {
            this.scenarioName = scenarioName;
            this.run = run;
            this.exitCode = exitCode;
            this.durationSeconds = durationSeconds;
            this.runDirectory = runDirectory;
        }

        private String summaryLine() {
            return scenarioName + " run " + run + " exit=" + exitCode + " duration=" + durationSeconds + "s dir=" + runDirectory.toAbsolutePath();
        }
    }
}