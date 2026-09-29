package mengxu.algorithm.multiobjective.MPSLGP;

import ec.util.ParameterDatabase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Runs the paper's MPSLGP comparisons; existing independent PSLGP results are never retrained. */
public final class MPSLGPPaperTrainingMain {
    static final Path DEFAULT_PARAM = Paths.get("src", "mengxu", "algorithm", "multiobjective",
            "MPSLGP", "multipletreegp-dynamic-MPSLGP-3tasks.params");
    static final Path DEFAULT_RESULTS = Paths.get("src", "mengxu", "ruleanalysis", "MPSLGP", "paper-runs");
    private static final String PACKAGE = "mengxu.algorithm.multiobjective.MPSLGP.";
    private static final String FORMAT = "mpslgp-paper-training-v1";
    private static final Set<Process> ACTIVE_PROCESSES = ConcurrentHashMap.newKeySet();

    private MPSLGPPaperTrainingMain() { }

    public static void main(String[] args) throws Exception {
        int failures = execute(args, System.out, MPSLGPPaperTrainingMain::runProcess);
        if (failures != 0) {
            throw new IllegalStateException(failures + " training job(s) failed. See the printed summary and attempt logs.");
        }
    }

    interface ProcessRunner {
        int run(List<String> command, Path directory, Consumer<String> progress)
                throws IOException, InterruptedException;
    }

    static int execute(String[] args, PrintStream out, ProcessRunner runner) throws Exception {
        Config config = Config.parse(args);
        if (config.help) {
            usage(out);
            return 0;
        }
        Map<String, String> base = loadParameters(config.param);
        List<Job> jobs = new ArrayList<>();
        for (String algorithm : config.algorithms) {
            for (String indicator : config.indicators) {
                for (String scenario : config.scenarios) {
                    for (int run = config.startRun; run < config.startRun + config.runs; run++) {
                        jobs.add(new Job(config, algorithm, indicator, SCENARIOS.get(scenario), run, base));
                    }
                }
            }
        }
        Files.createDirectories(config.results);
        Path batches = config.results.resolve("batches");
        Files.createDirectories(batches);
        Path batch = Files.createTempDirectory(batches, "batch-");
        writePlan(batch, jobs);
        out.println("MPSLGP paper training only; independent PSLGP is EXCLUDED.");
        out.println("Algorithms: " + config.algorithms + "; indicators: " + config.indicators);
        out.println("Scenarios: " + config.scenarios + "; runs: " + config.runs
                + "; first seed: " + config.startRun + "; total jobs: " + jobs.size());
        out.println("Population=" + config.population + " TOTAL across tasks; evaluated generations="
                + config.generations + " (including generation 0); parallel=" + config.parallel
                + "; child heap=" + config.heap + "; smoke=" + config.smoke);
        out.println("Results: " + config.results);
        out.println("Configuration table: " + batch.resolve("algorithm-configurations.csv"));
        out.println("Batch summary: " + batch.resolve("training-summary.csv"));
        out.println("Existing PSLGP results must have compatible budgets, operators and test protocols before comparison.");

        ExecutorService executor = Executors.newFixedThreadPool(config.parallel);
        ExecutorCompletionService<Result> completion = new ExecutorCompletionService<>(executor);
        AtomicInteger completed = new AtomicInteger();
        Thread shutdown = new Thread(() -> {
            executor.shutdownNow();
            for (Process process : ACTIVE_PROCESSES) {
                process.destroy();
            }
        }, "mpslgp-training-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        int failures = 0;
        try (BufferedWriter summary = Files.newBufferedWriter(batch.resolve("training-summary.csv"), StandardCharsets.UTF_8)) {
            summary.write("Algorithm,Indicator,Scenario,Run,Seed,Status,ExitCode,DurationSeconds,Directory,Message\n");
            for (Job job : jobs) {
                completion.submit(() -> {
                    Result result;
                    try {
                        result = job.execute(out, runner);
                    } catch (IOException | IllegalArgumentException e) {
                        out.println("[FAILED] " + job.identity() + " " + e.getMessage());
                        result = new Result(job, "FAILED", -1, 0, job.directory, e.toString());
                    }
                    out.println("[PROGRESS " + completed.incrementAndGet() + "/" + jobs.size()
                            + "] " + result.status + " " + job.identity());
                    return result;
                });
            }
            executor.shutdown();
            for (int i = 0; i < jobs.size(); i++) {
                Future<Result> future = completion.take();
                Result result = future.get();
                summary.write(result.csv());
                summary.newLine();
                summary.flush();
                if ("FAILED".equals(result.status)) {
                    failures++;
                }
            }
        } finally {
            executor.shutdownNow();
            for (Process process : ACTIVE_PROCESSES) {
                process.destroy();
            }
            Runtime.getRuntime().removeShutdownHook(shutdown);
        }
        out.println("Finished " + jobs.size() + " jobs; failures=" + failures + "; summary=" + batch.resolve("training-summary.csv"));
        return failures;
    }

    static final Map<String, String> ALGORITHMS = new LinkedHashMap<>();
    static final Map<String, Scenario> SCENARIOS = new LinkedHashMap<>();
    static {
        ALGORITHMS.put("full", "Preference-region gate, offspring feedback and contribution-aware inheritance");
        ALGORITHMS.put("no-transfer", "Same-task mating only; otherwise the same task-local learner");
        ALGORITHMS.put("fixed-transfer", "Constant effective transfer probability, no utility-based gate");
        ALGORITHMS.put("task-pair", "Adaptive transfer with K=1, without preference-region distinctions");
        ALGORITHMS.put("survival-only", "Adaptive transfer rewarded only by surviving offspring");
        ALGORITHMS.put("no-gate", "Fixed total transfer budget with utility-based donor selection");
        ALGORITHMS.put("primary-task", "Full transfer controller; children always retain the primary task");
        scenario("R1", 1, 2);
        scenario("R2", 3, 4);
        scenario("R3", 6, 7);
        scenario("R4", 1, 3, 5);
        scenario("R5", 8, 9);
        scenario("H1", 1, 10);
        scenario("H2", 5, 6);
        scenario("H3", 1, 5, 8);
    }

    private static void scenario(String name, int... tasks) {
        SCENARIOS.put(name, new Scenario(name, tasks));
    }

    static final class Scenario {
        final String name;
        final int[] tasks;
        Scenario(String name, int... tasks) {
            this.name = name;
            this.tasks = tasks.clone();
        }
    }

    static final class Job {
        final Config config;
        final String algorithm;
        final String indicator;
        final Scenario scenario;
        final int run;
        final Path directory;
        final Map<String, String> settings;
        final String snapshot;
        final String fingerprint;

        Job(Config config, String algorithm, String indicator, Scenario scenario, int run, Map<String, String> base) {
            this.config = config;
            this.algorithm = algorithm;
            this.indicator = indicator;
            this.scenario = scenario;
            this.run = run;
            directory = config.results.resolve(algorithm).resolve(indicator).resolve(scenario.name)
                    .resolve(String.format(Locale.ROOT, "run%02d", run));
            settings = parameters(this, base);
            snapshot = serialize(settings);
            fingerprint = sha256((FORMAT + "\n" + snapshot).getBytes(StandardCharsets.UTF_8));
        }

        String identity() {
            return "algorithm=" + algorithm + " indicator=" + indicator + " scenario=" + scenario.name
                    + " run=" + run + " seed=" + run;
        }

        Result execute(PrintStream out, ProcessRunner runner) throws IOException, InterruptedException {
            Files.createDirectories(directory);
            try (FileChannel channel = FileChannel.open(directory.resolve("run.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock lock;
                try {
                    lock = channel.tryLock();
                } catch (OverlappingFileLockException e) {
                    throw new IOException("Run is already locked: " + directory, e);
                }
                if (lock == null) {
                    throw new IOException("Another process is running " + directory);
                }
                try (FileLock held = lock) {
                    return executeLocked(out, runner);
                }
            }
        }

        private Result executeLocked(PrintStream out, ProcessRunner runner) throws IOException, InterruptedException {
            Path effective = directory.resolve("effective.params");
            if (Files.exists(effective)) {
                if (!snapshot.equals(Files.readString(effective, StandardCharsets.UTF_8))) {
                    throw new IOException("Configuration mismatch; use a different --results directory: " + directory);
                }
            } else {
                if (Files.exists(directory.resolve("completed.properties"))) {
                    throw new IOException("Completion record exists without its configuration: " + directory);
                }
                atomicWrite(effective, snapshot);
            }
            Map<String, String> metadata = new TreeMap<>();
            metadata.put("format", FORMAT);
            metadata.put("configuration.sha256", fingerprint);
            metadata.put("algorithm", algorithm);
            metadata.put("description", ALGORITHMS.get(algorithm));
            metadata.put("indicator", indicator);
            metadata.put("scenario", scenario.name);
            metadata.put("benchmark.tasks", Arrays.toString(scenario.tasks));
            metadata.put("run", Integer.toString(run));
            metadata.put("seed", Integer.toString(run));
            metadata.put("smoke", Boolean.toString(config.smoke));
            metadata.put("source.params", config.param.toString());
            metadata.put("java.version", System.getProperty("java.version"));
            metadata.put("population.total", Integer.toString(config.population));
            metadata.put("task.population.quotas", quotas());
            atomicWrite(directory.resolve("run.properties"), serialize(metadata));
            atomicWrite(directory.resolve("command.txt"), "Working directory: " + directory
                    + "\n" + commandText(command(directory)) + "\n");

            Path completion = directory.resolve("completed.properties");
            if (Files.exists(completion)) {
                Properties marker = readProperties(completion);
                if (!fingerprint.equals(marker.getProperty("configuration.sha256"))) {
                    throw new IOException("Completion fingerprint does not match this configuration: " + directory);
                }
                String invalid = invalidCompletion(marker);
                if (invalid == null) {
                    if (!config.skipExisting && !config.dryRun) {
                        throw new IOException("Run already completed; add --skip-existing or use a different --results: " + directory);
                    }
                    out.println("[SKIP] " + identity() + " verified completed outputs");
                    return new Result(this, "SKIPPED", 0, 0,
                            directory.resolve(marker.getProperty("attempt")), "Verified completion and artifact hashes");
                }
                out.println("[RETRY] " + identity() + " " + invalid + "; preserving the previous attempt");
            }
            if (config.dryRun) {
                out.println("[DRY-RUN] " + identity() + " configuration=" + effective);
                return new Result(this, "PLANNED", 0, 0, directory, "No training process started");
            }
            Path attempt = nextAttempt(directory);
            atomicWrite(attempt.resolve("effective.params"), snapshot);
            List<String> command = command(attempt);
            atomicWrite(attempt.resolve("command.txt"), "Working directory: " + attempt
                    + "\n" + commandText(command) + "\n");
            out.println("[START] " + identity() + " tasks=" + Arrays.toString(scenario.tasks)
                    + " quota=" + quotas() + " directory=" + attempt);
            out.println("[CONFIG] " + identity() + " " + controlSummary(settings));
            Instant started = Instant.now();
            long start = System.nanoTime();
            try {
                int exit = runner.run(command, attempt, line -> out.println("[GEN] " + identity() + " " + line));
                long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
                String invalid = exit == 0 ? invalidOutputs(attempt) : "Training process exited with code " + exit;
                Map<String, String> outcome = new TreeMap<>();
                outcome.put("configuration.sha256", fingerprint);
                outcome.put("attempt", attempt.getFileName().toString());
                outcome.put("started", started.toString());
                outcome.put("finished", Instant.now().toString());
                outcome.put("exit.code", Integer.toString(exit));
                outcome.put("duration.seconds", Long.toString(seconds));
                if (invalid != null) {
                    outcome.put("status", "FAILED");
                    outcome.put("reason", invalid);
                    atomicWrite(attempt.resolve("status.properties"), serialize(outcome));
                    out.println("[FAILED] " + identity() + " " + invalid + "; log=" + attempt.resolve("training.log"));
                    return new Result(this, "FAILED", exit == 0 ? -1 : exit, seconds, attempt, invalid);
                }
                outcome.put("status", "COMPLETED");
                for (String artifact : artifacts()) {
                    outcome.put("sha256." + artifact, fileHash(attempt.resolve(artifact)));
                }
                atomicWrite(attempt.resolve("status.properties"), serialize(outcome));
                atomicWrite(completion, serialize(outcome));
                out.println("[DONE] " + identity() + " elapsed=" + seconds + "s");
                return new Result(this, "COMPLETED", 0, seconds, attempt, "Validated final generation and task outputs");
            } catch (IOException | InterruptedException e) {
                Map<String, String> failure = new TreeMap<>();
                failure.put("status", "FAILED");
                failure.put("configuration.sha256", fingerprint);
                failure.put("reason", e.toString());
                atomicWrite(attempt.resolve("status.properties"), serialize(failure));
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw e;
            }
        }

        private String invalidCompletion(Properties marker) throws IOException {
            if (!"COMPLETED".equals(marker.getProperty("status")) || !"0".equals(marker.getProperty("exit.code"))) {
                return "No successful completion record";
            }
            String name = marker.getProperty("attempt", "");
            if (!name.matches("attempt-\\d{4,}")) {
                return "Invalid attempt directory in completion record";
            }
            Path attempt = directory.resolve(name);
            for (String artifact : artifacts()) {
                Path path = attempt.resolve(artifact);
                if (!Files.isRegularFile(path) || Files.size(path) == 0) {
                    return "Missing/empty artifact " + artifact;
                }
                if (!fileHash(path).equals(marker.getProperty("sha256." + artifact))) {
                    return "Changed artifact " + artifact;
                }
            }
            return invalidOutputs(attempt);
        }

        private String invalidOutputs(Path attempt) throws IOException {
            for (String artifact : artifacts()) {
                if (!Files.isRegularFile(attempt.resolve(artifact)) || Files.size(attempt.resolve(artifact)) == 0) {
                    return "Missing/empty output " + artifact;
                }
            }
            String stats = Files.readString(attempt.resolve(statName()), StandardCharsets.UTF_8);
            if (!Pattern.compile("(?m)^Generation: " + (config.generations - 1) + "\\s*$").matcher(stats).find()) {
                return "Final evaluated generation is missing from statistics";
            }
            if (!stats.contains("PARETO FRONTS BY MPSLGP TASK")) {
                return "Final task fronts are missing from statistics";
            }
            for (int task = 0; task < scenario.tasks.length; task++) {
                if (!stats.contains("MPSLGP Task " + task + " Top 1 individuals:")) {
                    return "No selected final heuristic for task " + task;
                }
            }
            return null;
        }

        String statName() { return "job." + run + ".out.stat"; }
        String frontName() { return "job." + run + ".front.stat"; }
        List<String> artifacts() {
            return Arrays.asList("effective.params", "training.log", statName(), frontName());
        }
        String quotas() {
            int tasks = scenario.tasks.length;
            int[] result = new int[tasks];
            Arrays.fill(result, config.population / tasks);
            for (int i = 0; i < config.population % tasks; i++) {
                result[(i + run % tasks) % tasks]++;
            }
            return Arrays.toString(result);
        }
        List<String> command(Path attempt) {
            return Arrays.asList(javaExecutable(), "-Xmx" + config.heap,
                    "--add-opens=java.base/java.lang=ALL-UNNAMED", "-cp", absoluteClassPath(),
                    "yimei.jss.gp.GPRun", "-file", attempt.resolve("effective.params").toString());
        }
    }

    static Map<String, String> parameters(Job job, Map<String, String> base) {
        Config c = job.config;
        Map<String, String> p = new TreeMap<>(base);
        p.keySet().removeIf(key -> key.startsWith("parent.") || key.matches("eval\\.problem\\.\\d+.*"));
        if (p.containsKey("filePath")) {
            throw new IllegalArgumentException("Static filePath evaluation is incompatible with the paper's dynamic scenarios.");
        }
        put(p, "jobs", 1, "evalthreads", 1, "breedthreads", 1, "seed.0", job.run,
                "checkpoint", false, "quit-on-run-complete", false,
                "state", PACKAGE + "GPRuleEvolutionStatePSL", "init", PACKAGE + "PSLInitializer",
                "breed", PACKAGE + "PSLBreederKeepParetoFront",
                "eval", PACKAGE + "KNNsurrogateClearingPSLEvaluatorbasedonHV",
                "stat", PACKAGE + "PSLMultiObjectiveStatisticsTopN",
                "stat.file", "$" + job.statName(), "stat.front", "$" + job.frontName(),
                "stat.do-generation", true, "stat.do-final", true, "stat.do-message", true,
                "generations", c.generations, "pop.subpop.0.size", c.population,
                "pop.subpops", 1, "breed.elite.0", c.smoke ? 2 : 10,
                "select.tournament.size", 7, "gp.koza.half.min-depth", 2, "gp.koza.half.max-depth", 6,
                "gp.koza.half.growp", 0.5, "gp.koza.xover.maxdepth", 8, "gp.koza.mutate.maxdepth", 8,
                "gp.koza.ns.terminals", 0.1, "gp.koza.ns.nonterminals", 0.9,
                "pop.subpop.0.species.ind.numtrees", 2, "num-trees", 2,
                "pop.subpop.0.species.pipe.source.0", "yimei.jss.algorithm.multipletreegp.OneTreeCrossoverPipeline",
                "pop.subpop.0.species.pipe.source.0.prob", 0.80,
                "pop.subpop.0.species.pipe.source.1.prob", 0.15,
                "pop.subpop.0.species.pipe.source.2.prob", 0.05,
                "num-preferences-for-HV", c.preferences, "numPreferences", 1, "using_PSL", true,
                "use-brood-recombination", true, "use-multi-criteria-selection", true, "niching", true,
                "compare-criteria", job.indicator, "normalisation", 1, "tchebycheff", 0,
                "terminals-from", "relative_with_preference",
                "output-top-N", 1, "topN-from-pareto-front", false,
                "mpslgp.num-tasks", job.scenario.tasks.length,
                "mpslgp.task-allocation-offset", job.run % job.scenario.tasks.length,
                "mpslgp.adaptive-transfer", true, "mpslgp.use-no-transfer-gate", true,
                "mpslgp.preference-regions", c.regions, "mpslgp.transfer-start-generation", c.transferStart,
                "mpslgp.max-transfer-probability", c.budget,
                "mpslgp.transfer-probability", c.budget / (job.scenario.tasks.length - 1),
                "mpslgp.scale-transfer-budget-by-donor-count", true,
                "mpslgp.min-transfer-probability", 0.0,
                "mpslgp.adaptive-transfer-learning-rate", c.learningRate,
                "mpslgp.adaptive-transfer-temperature", c.temperature,
                "mpslgp.no-transfer-utility", 0.10,
                "mpslgp.transfer-improvement-weight", 1.0,
                "mpslgp.transfer-survival-weight", 0.1,
                "mpslgp.transfer-no-improvement-penalty", 0.10,
                "mpslgp.contribution-aware-task-inheritance", true,
                "mpslgp.donor-task-inheritance-threshold", c.inheritanceThreshold);
        switch (job.algorithm) {
            case "full": break;
            case "no-transfer":
                put(p, "mpslgp.adaptive-transfer", false, "mpslgp.transfer-probability", 0.0,
                        "mpslgp.max-transfer-probability", 0.0);
                break;
            case "fixed-transfer": p.put("mpslgp.adaptive-transfer", "false"); break;
            case "task-pair": p.put("mpslgp.preference-regions", "1"); break;
            case "survival-only":
                put(p, "mpslgp.transfer-improvement-weight", 0.0,
                        "mpslgp.transfer-no-improvement-penalty", 0.0);
                break;
            case "no-gate": p.put("mpslgp.use-no-transfer-gate", "false"); break;
            case "primary-task": p.put("mpslgp.contribution-aware-task-inheritance", "false"); break;
            default: throw new IllegalArgumentException("Unsupported algorithm: " + job.algorithm);
        }
        problem(p, "eval.problem", job.scenario.tasks[0], c);
        for (int i = 0; i < job.scenario.tasks.length; i++) {
            problem(p, "eval.problem." + i, job.scenario.tasks[i], c);
        }
        return Collections.unmodifiableMap(p);
    }

    private static void problem(Map<String, String> p, String prefix, int scenario, Config c) {
        String model = prefix + ".eval-model";
        String sim = model + ".sim-models.0";
        put(p, prefix, "yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem",
                prefix + ".data", "yimei.jss.gp.data.DoubleData",
                model, "mengxu.complexsimulation.ruleevaluation.MultipleTreeMultipleRuleHeterogeneousEvaluationModel",
                model + ".objectives", 2,
                model + ".objectives.0", scenario <= 5 ? "max-flowtime" : "max-weighted-flowtime",
                model + ".objectives.1", scenario <= 5 ? "max-weighted-tardiness" : "max-tardiness",
                model + ".sim-models", 1, model + ".sim-seed", 0,
                model + ".rotate-sim-seed", true,
                sim + ".util-level", String.format(Locale.ROOT, "%.2f", 0.70 + ((scenario - 1) % 5) * 0.05),
                sim + ".num-jobs", c.jobs, sim + ".warmup-jobs", c.warmup,
                sim + ".replications", 1, sim + ".num-machines", 10,
                sim + ".min-num-operations", 2, sim + ".max-num-operations", 10,
                sim + ".due-date-factor", 1.5);
    }

    static void put(Map<String, String> map, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i].toString(), pairs[i + 1].toString());
        }
    }

    static Map<String, String> loadParameters(Path path) throws IOException {
        ParameterDatabase database = new ParameterDatabase(path.toFile());
        StringWriter buffer = new StringWriter();
        database.list(new PrintWriter(buffer), false);
        Map<String, String> parameters = new TreeMap<>();
        for (String line : buffer.toString().split("\\R")) {
            int separator = line.indexOf(" = ");
            if (separator < 1) {
                throw new IOException("Cannot flatten parameter line: " + line);
            }
            parameters.put(line.substring(0, separator), line.substring(separator + 3));
        }
        return parameters;
    }

    static final class Config {
        int runs = 30, startRun = 0, parallel = 1, population = 1000, generations = 50;
        int jobs = 5000, warmup = 1000, preferences = 101, regions = 5, transferStart = 15;
        double budget = 0.8, learningRate = 0.1, temperature = 1.0, inheritanceThreshold = 0.65;
        Path param = DEFAULT_PARAM, results = DEFAULT_RESULTS;
        String heap = "2g";
        boolean skipExisting, dryRun, smoke, help;
        List<String> algorithms = new ArrayList<>(ALGORITHMS.keySet());
        List<String> scenarios = new ArrayList<>(SCENARIOS.keySet());
        List<String> indicators = new ArrayList<>(Collections.singletonList("IGD"));

        static Config parse(String[] args) {
            Config c = new Config();
            c.smoke = Arrays.asList(args).contains("--smoke");
            if (c.smoke) {
                c.population = 24; c.generations = 3; c.jobs = 200; c.warmup = 50;
                c.preferences = 5; c.transferStart = 1;
            }
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (!seen.add(arg)) {
                    throw new IllegalArgumentException("Repeated option: " + arg);
                }
                switch (arg) {
                    case "--help": case "-h": c.help = true; break;
                    case "--skip-existing": c.skipExisting = true; break;
                    case "--dry-run": c.dryRun = true; break;
                    case "--smoke": break;
                    default:
                        if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                            throw new IllegalArgumentException("Missing value for " + arg);
                        }
                        String value = args[++i];
                        switch (arg) {
                            case "--skip":
                                if (!"existing".equals(value)) {
                                    throw new IllegalArgumentException("Use --skip-existing or --skip existing");
                                }
                                c.skipExisting = true; break;
                            case "--runs": c.runs = integer(arg, value, 1); break;
                            case "--start-run": c.startRun = integer(arg, value, 0); break;
                            case "--parallel": c.parallel = integer(arg, value, 1); break;
                            case "--population": c.population = integer(arg, value, 10); break;
                            case "--generations": c.generations = integer(arg, value, 1); break;
                            case "--jobs": c.jobs = integer(arg, value, 1); break;
                            case "--warmup": c.warmup = integer(arg, value, 0); break;
                            case "--preferences": c.preferences = integer(arg, value, 3); break;
                            case "--regions": c.regions = integer(arg, value, 1); break;
                            case "--transfer-start": c.transferStart = integer(arg, value, 0); break;
                            case "--transfer-budget": c.budget = decimal(arg, value, 0, 1, true); break;
                            case "--learning-rate": c.learningRate = decimal(arg, value, 0, 1, false); break;
                            case "--temperature": c.temperature = decimal(arg, value, 0, Double.MAX_VALUE, false); break;
                            case "--inheritance-threshold": c.inheritanceThreshold = decimal(arg, value, 0, 1, true); break;
                            case "--results": c.results = Paths.get(value); break;
                            case "--param": c.param = Paths.get(value); break;
                            case "--heap":
                                if (!value.matches("[1-9]\\d*[mMgG]")) {
                                    throw new IllegalArgumentException("--heap requires a JVM size such as 2g or 512m");
                                }
                                c.heap = value.toLowerCase(Locale.ROOT); break;
                            case "--algorithms":
                                c.algorithms = select(value, ALGORITHMS.keySet(), false, arg); break;
                            case "--scenarios":
                                c.scenarios = select(value, SCENARIOS.keySet(), true, arg); break;
                            case "--indicators":
                                c.indicators = select(value, new LinkedHashSet<>(Arrays.asList("IGD", "HV", "GD")), true, arg); break;
                            default: throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                }
            }
            if ((long)c.startRun + c.runs > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Run range exceeds valid integer seeds");
            }
            c.param = c.param.toAbsolutePath().normalize();
            c.results = c.results.toAbsolutePath().normalize();
            if (c.smoke) {
                c.results = c.results.resolve("smoke");
            }
            if (!c.help && !Files.isRegularFile(c.param)) {
                throw new IllegalArgumentException("Parameter file not found: " + c.param + "; use the repository as working directory.");
            }
            return c;
        }
    }

    private static int integer(String option, String value, int minimum) {
        try {
            int number = Integer.parseInt(value);
            if (number >= minimum) { return number; }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " requires an integer >= " + minimum + ": " + value, e);
        }
        throw new IllegalArgumentException(option + " requires an integer >= " + minimum);
    }

    private static double decimal(String option, String value, double min, double max, boolean inclusiveMin) {
        final double number;
        try { number = Double.parseDouble(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid " + option + ": " + value, e); }
        if (!Double.isFinite(number) || number > max || (inclusiveMin ? number < min : number <= min)) {
            throw new IllegalArgumentException("Invalid " + option + ": " + value);
        }
        return number;
    }

    private static List<String> select(String value, Set<String> valid, boolean upper, String option) {
        if ("all".equalsIgnoreCase(value)) { return new ArrayList<>(valid); }
        Set<String> selected = new LinkedHashSet<>();
        for (String name : value.split(",", -1)) {
            name = upper ? name.trim().toUpperCase(Locale.ROOT) : name.trim().toLowerCase(Locale.ROOT);
            if (!valid.contains(name) || !selected.add(name)) {
                throw new IllegalArgumentException("Invalid/duplicate " + option + " value '" + name + "'; choose " + valid);
            }
        }
        return new ArrayList<>(selected);
    }

    private static void writePlan(Path batch, List<Job> jobs) throws IOException {
        Set<String> profiles = new LinkedHashSet<>();
        try (BufferedWriter plan = Files.newBufferedWriter(batch.resolve("paper-training-plan.csv"), StandardCharsets.UTF_8);
             BufferedWriter configs = Files.newBufferedWriter(batch.resolve("algorithm-configurations.csv"), StandardCharsets.UTF_8)) {
            plan.write("Algorithm,Indicator,Scenario,Run,Seed,BenchmarkTasks,TaskPopulationQuotas,ConfigurationHash,Directory\n");
            configs.write("Algorithm,Indicator,Tasks,Description,EffectiveConfiguration\n");
            for (Job job : jobs) {
                plan.write(csv(job.algorithm, job.indicator, job.scenario.name, job.run, job.run,
                        Arrays.toString(job.scenario.tasks), job.quotas(), job.fingerprint, job.directory));
                plan.newLine();
                String key = job.algorithm + "|" + job.indicator + "|" + job.scenario.tasks.length;
                if (profiles.add(key)) {
                    configs.write(csv(job.algorithm, job.indicator, job.scenario.tasks.length,
                            ALGORITHMS.get(job.algorithm), controlSummary(job.settings)));
                    configs.newLine();
                }
            }
        }
    }

    private static String controlSummary(Map<String, String> p) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : p.entrySet()) {
            if (entry.getKey().startsWith("mpslgp.") || Arrays.asList("generations", "pop.subpop.0.size",
                    "compare-criteria", "num-preferences-for-HV", "breed.elite.0").contains(entry.getKey())) {
                values.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        int tasks = Integer.parseInt(p.get("mpslgp.num-tasks"));
        double effective = Double.parseDouble(p.get("mpslgp.max-transfer-probability")) / (tasks - 1);
        values.add("effective-adaptive-budget=" + effective);
        return String.join("; ", values);
    }

    static final class Result {
        final Job job;
        final String status;
        final int exit;
        final long seconds;
        final Path directory;
        final String message;
        Result(Job job, String status, int exit, long seconds, Path directory, String message) {
            this.job = job; this.status = status; this.exit = exit;
            this.seconds = seconds; this.directory = directory; this.message = message;
        }
        String csv() {
            return MPSLGPPaperTrainingMain.csv(job.algorithm, job.indicator, job.scenario.name,
                    job.run, job.run, status, exit, seconds, directory, message);
        }
    }

    private static String csv(Object... values) {
        List<String> fields = new ArrayList<>();
        for (Object value : values) {
            fields.add("\"" + value.toString().replace("\"", "\"\"") + "\"");
        }
        return String.join(",", fields);
    }

    static String serialize(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            result.append(escape(entry.getKey())).append('=').append(escape(entry.getValue())).append('\n');
        }
        return result.toString();
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c < 32 || c > 126) {
                escaped.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
            } else {
                if ("\\ :=#!".indexOf(c) >= 0) { escaped.append('\\'); }
                escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    static void atomicWrite(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Path nextAttempt(Path directory) throws IOException {
        for (int number = 1; number < Integer.MAX_VALUE; number++) {
            Path attempt = directory.resolve(String.format(Locale.ROOT, "attempt-%04d", number));
            if (!Files.exists(attempt)) {
                return Files.createDirectory(attempt);
            }
        }
        throw new IOException("Too many attempts in " + directory);
    }

    static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) { hex.append(String.format(Locale.ROOT, "%02x", b & 255)); }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String fileHash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) { hex.append(String.format(Locale.ROOT, "%02x", b & 255)); }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static int runProcess(List<String> command, Path directory, Consumer<String> progress)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        ACTIVE_PROCESSES.add(process);
        try (BufferedReader input = new BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter log = Files.newBufferedWriter(directory.resolve("training.log"), StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) {
                log.write(line);
                log.newLine();
                if (line.matches("(?i)^\\s*Generation\\s*:?\\s*\\d+.*")) {
                    log.flush();
                    progress.accept(line.trim());
                }
            }
            return process.waitFor();
        } finally {
            ACTIVE_PROCESSES.remove(process);
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) { process.destroyForcibly(); }
            }
        }
    }

    private static String javaExecutable() {
        return Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
    }

    private static String absoluteClassPath() {
        List<String> entries = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator), -1)) {
            entries.add(Paths.get(entry.isEmpty() ? "." : entry).toAbsolutePath().normalize().toString());
        }
        return String.join(File.pathSeparator, entries);
    }

    private static String commandText(List<String> command) {
        List<String> quoted = new ArrayList<>();
        for (String argument : command) { quoted.add("'" + argument.replace("'", "''") + "'"); }
        return "& " + String.join(" ", quoted);
    }

    private static void usage(PrintStream out) {
        out.println("Usage: mengxu.algorithm.multiobjective.MPSLGP.MPSLGPPaperTrainingMain [options]");
        out.println("Training only. Independent PSLGP is excluded. Defaults: 7 algorithms x 8 scenarios x 30 runs, IGD.");
        out.println("  --algorithms LIST   " + ALGORITHMS.keySet() + " (default all)");
        out.println("  --indicators LIST   IGD,HV,GD (default IGD)");
        out.println("  --scenarios LIST    " + SCENARIOS.keySet() + " (default all)");
        out.println("  --runs N --start-run N --parallel N   Defaults: 30, 0, 1");
        out.println("  --results PATH --param PATH --heap 2g");
        out.println("  --skip-existing     Skip only matching, verified successful runs; --skip existing also accepted");
        out.println("  --dry-run           Save configurations and plans without launching Java training");
        out.println("  --smoke             Small non-paper budget, saved below results/smoke");
        out.println("  --population N --generations N --jobs N --warmup N --preferences N");
        out.println("  --transfer-start N --regions N --transfer-budget X --learning-rate X");
        out.println("  --temperature X --inheritance-threshold X");
        out.println("Changed configurations require a different result root. Failed attempts are retained and retried.");
    }
}
