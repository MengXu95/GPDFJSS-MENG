package mengxu.ruleanalysis.MPSLGP;

import mengxu.ruleanalysis.MPSLGP.ParetoSetLearningMultipleTreeResultFileReader.SelectedTaskHeuristic;
import yimei.jss.jobshop.Objective;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.*;

/** Tests one final training-selected rule pair per task using the original PSLGP test protocol. */
public final class MPSLGPPaperTestMain {
    static final String FORMAT = "mpslgp-task-test-v1";
    private static final Set<Process> ACTIVE = ConcurrentHashMap.newKeySet();
    private MPSLGPPaperTestMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--worker".equals(args[0])) {
            worker(Paths.get(args[1]));
        } else if (execute(args, System.out, MPSLGPPaperTestMain::runProcess) != 0) {
            throw new IllegalStateException("Testing failed. Inspect the batch summary and per-task attempt logs.");
        }
    }

    interface ProcessRunner {
        int run(List<String> command, Path directory, Consumer<String> progress)
                throws IOException, InterruptedException;
    }

    static int execute(String[] args, PrintStream out, ProcessRunner runner) throws Exception {
        Config config = Config.parse(args);
        if (config.help) { usage(out); return 0; }
        List<Job> jobs = plan(config);
        Files.createDirectories(config.output.resolve("batches"));
        Path batch = Files.createTempDirectory(config.output.resolve("batches"), "batch-");
        writePlan(batch, jobs);
        out.println("MPSLGP test only: no training and no independent PSLGP runs.");
        out.println("Verified task jobs: " + jobs.size() + "; preferences=" + config.preferences
                + "; replications=" + config.replications + "; test seed=" + MPSLGPTaskEvaluator.simSeed);
        out.println("Same PSLGP simulator: dynamic-job-shop / missing-utilisation-1.5; 5000 measured + 1000 warm-up jobs.");
        out.println("Plan: " + batch.resolve("paper-test-plan.csv"));
        out.println("Output: " + config.output + "; parallel=" + config.parallel + "; smoke=" + config.smoke);
        out.println("Compare only matching test protocols and acknowledge any training-budget or model-selection differences.");

        ExecutorService pool = Executors.newFixedThreadPool(config.parallel);
        ExecutorCompletionService<Result> completion = new ExecutorCompletionService<>(pool);
        AtomicInteger completed = new AtomicInteger();
        Thread shutdown = new Thread(() -> {
            pool.shutdownNow();
            for (Process process : ACTIVE) { process.destroy(); }
        }, "mpslgp-test-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        List<Result> results = new ArrayList<>();
        int failures = 0;
        try (BufferedWriter summary = Files.newBufferedWriter(batch.resolve("paper-test-summary.csv"), StandardCharsets.UTF_8)) {
            summary.write("Algorithm,Indicator,Scenario,Run,Task,BenchmarkScenario,Status,Seconds,Output,Message\n");
            for (Job job : jobs) {
                completion.submit(() -> {
                    Result result;
                    try { result = job.execute(out, runner); }
                    catch (IOException | IllegalArgumentException e) {
                        out.println("[TEST FAILED] " + job.identity() + " " + e.getMessage());
                        result = new Result(job, "FAILED", 0, job.directory, e.toString());
                    }
                    out.println("[TEST PROGRESS " + completed.incrementAndGet() + "/" + jobs.size()
                            + "] " + result.status + " " + job.identity());
                    return result;
                });
            }
            pool.shutdown();
            for (int i = 0; i < jobs.size(); i++) {
                Result result = completion.take().get();
                results.add(result);
                summary.write(csv(result.job.algorithm, result.job.indicator, result.job.scenario,
                        result.job.run, result.job.task, result.job.spec.get("benchmark.scenario"), result.status,
                        result.seconds, result.directory, result.message) + "\n");
                summary.flush();
                if ("FAILED".equals(result.status)) { failures++; }
            }
        } finally {
            pool.shutdownNow();
            for (Process process : ACTIVE) { process.destroy(); }
            Runtime.getRuntime().removeShutdownHook(shutdown);
        }
        if (!config.dryRun && failures == 0) { aggregate(batch, results); }
        out.println("Finished " + jobs.size() + " task jobs; failures=" + failures
                + "; summary=" + batch.resolve("paper-test-summary.csv"));
        return failures;
    }

    static List<Job> plan(Config config) throws IOException {
        List<Job> jobs = new ArrayList<>();
        for (String algorithm : config.algorithms) {
            for (String indicator : config.indicators) {
                for (String scenario : config.scenarios) {
                    for (int run = config.startRun; run < config.startRun + config.runs; run++) {
                        Path directory = config.training.resolve(algorithm).resolve(indicator).resolve(scenario)
                                .resolve(String.format(Locale.ROOT, "run%02d", run));
                        Properties completed = readProperties(directory.resolve("completed.properties"));
                        Properties metadata = readProperties(directory.resolve("run.properties"));
                        require(completed, "status", "COMPLETED");
                        require(completed, "exit.code", "0");
                        require(metadata, "algorithm", algorithm);
                        require(metadata, "indicator", indicator);
                        require(metadata, "scenario", scenario);
                        require(metadata, "run", Integer.toString(run));
                        require(completed, "configuration.sha256", required(metadata, "configuration.sha256"));
                        String attemptName = required(completed, "attempt");
                        if (!attemptName.matches("attempt-\\d{4,}")) {
                            throw new IOException("Invalid training attempt path in " + directory);
                        }
                        Path attempt = directory.resolve(attemptName);
                        Path parameterFile = attempt.resolve("effective.params");
                        Path stats = attempt.resolve("job." + run + ".out.stat");
                        require(completed, "sha256.effective.params", fileHash(parameterFile));
                        require(completed, "sha256." + stats.getFileName(), fileHash(stats));
                        Properties parameters = readProperties(parameterFile);
                        require(parameters, "seed.0", Integer.toString(run));
                        require(parameters, "compare-criteria", indicator);
                        require(parameters, "terminal-normalisation", "2");
                        require(parameters, "num-trees", "2");
                        int tasks = positive(parameters, "mpslgp.num-tasks");
                        int generation = positive(parameters, "generations") - 1;
                        if (!config.tasks.isEmpty() && Collections.max(config.tasks) >= tasks) {
                            throw new IOException("Selected task index is not present in " + directory);
                        }
                        for (int task = 0; task < tasks; task++) {
                            if (config.tasks.isEmpty() || config.tasks.contains(task)) {
                                jobs.add(new Job(config, algorithm, indicator, scenario, run, task,
                                        generation, stats, parameterFile, parameters, metadata));
                            }
                        }
                    }
                }
            }
        }
        if (jobs.isEmpty()) { throw new IOException("No task jobs selected."); }
        return jobs;
    }

    static final class Job {
        final Config config;
        final String algorithm, indicator, scenario, setName, csvName;
        final int run, task, generation;
        final Path directory;
        final Map<String, String> spec = new TreeMap<>();
        final String serialized, fingerprint;

        Job(Config config, String algorithm, String indicator, String scenario, int run, int task, int generation,
            Path stats, Path parametersPath, Properties parameters, Properties metadata) throws IOException {
            this.config = config; this.algorithm = algorithm; this.indicator = indicator;
            this.scenario = scenario; this.run = run; this.task = task; this.generation = generation;
            directory = config.output.resolve(algorithm).resolve(indicator).resolve(scenario)
                    .resolve(String.format(Locale.ROOT, "run%02d", run)).resolve(String.format(Locale.ROOT, "task%02d", task));
            String prefix = "eval.problem." + task + ".eval-model";
            require(parameters, prefix + ".objectives", "2");
            String obj0 = required(parameters, prefix + ".objectives.0");
            String obj1 = required(parameters, prefix + ".objectives.1");
            String utilization = required(parameters, prefix + ".sim-models.0.util-level");
            String dueDate = required(parameters, prefix + ".sim-models.0.due-date-factor");
            int benchmark = benchmark(obj0, obj1, utilization, dueDate);
            setName = "missing-" + Double.toString(Double.parseDouble(utilization)) + "-1.5";
            csvName = MPSLGPTaskEvaluator.csvName(setName, run);
            SelectedTaskHeuristic selected = ParetoSetLearningMultipleTreeResultFileReader
                    .readFinalTaskHeuristic(stats, task, generation);
            spec.put("format", FORMAT);
            spec.put("algorithm", algorithm); spec.put("indicator", indicator); spec.put("scenario", scenario);
            spec.put("run", Integer.toString(run)); spec.put("task", Integer.toString(task));
            spec.put("generation", Integer.toString(generation));
            spec.put("benchmark.scenario", Integer.toString(benchmark));
            spec.put("objective.0", obj0); spec.put("objective.1", obj1);
            spec.put("test.set", setName); spec.put("test.scenario", "dynamic-job-shop");
            spec.put("test.seed", Long.toString(MPSLGPTaskEvaluator.simSeed));
            spec.put("preferences", Integer.toString(config.preferences));
            spec.put("replications", Integer.toString(config.replications));
            spec.put("terminal.normalisation", "2");
            spec.put("smoke", Boolean.toString(config.smoke));
            spec.put("source.stats", stats.toString()); spec.put("source.stats.sha256", fileHash(stats));
            spec.put("source.params", parametersPath.toString()); spec.put("source.params.sha256", fileHash(parametersPath));
            spec.put("source.configuration.sha256", required(metadata, "configuration.sha256"));
            spec.put("training.population.total", required(parameters, "pop.subpop.0.size"));
            spec.put("training.generations", required(parameters, "generations"));
            spec.put("sequencing.expression", selected.getSequencingRule().getLispString());
            spec.put("routing.expression", selected.getRoutingRule().getLispString());
            spec.put("training.objective.0", Double.toString(selected.getTrainingObjectives()[0]));
            spec.put("training.objective.1", Double.toString(selected.getTrainingObjectives()[1]));
            spec.put("output.csv", csvName);
            serialized = serialize(spec);
            fingerprint = sha256(serialized.getBytes(StandardCharsets.UTF_8));
        }

        String identity() {
            return "algorithm=" + algorithm + " indicator=" + indicator + " scenario=" + scenario
                    + " run=" + run + " task=" + task + " benchmark=" + spec.get("benchmark.scenario");
        }

        Result execute(PrintStream out, ProcessRunner runner) throws IOException, InterruptedException {
            Files.createDirectories(directory);
            try (FileChannel channel = FileChannel.open(directory.resolve("test.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock lock;
                try { lock = channel.tryLock(); }
                catch (OverlappingFileLockException e) { throw new IOException("Test is already running: " + directory, e); }
                if (lock == null) { throw new IOException("Test is locked by another process: " + directory); }
                try (FileLock held = lock) { return executeLocked(out, runner); }
            }
        }

        private Result executeLocked(PrintStream out, ProcessRunner runner) throws IOException, InterruptedException {
            Path configFile = directory.resolve("test.properties");
            if (Files.exists(configFile)) {
                if (!serialized.equals(Files.readString(configFile, StandardCharsets.UTF_8))) {
                    throw new IOException("Changed training source or test configuration; choose another --output: " + directory);
                }
            } else {
                atomicWrite(configFile, serialized);
            }
            atomicWrite(directory.resolve("selected-heuristic.properties"), selectedProperties(spec));
            Path markerFile = directory.resolve("completed.properties");
            if (Files.exists(markerFile)) {
                Properties marker = readProperties(markerFile);
                require(marker, "configuration.sha256", fingerprint);
                String previous = required(marker, "attempt");
                if (!previous.matches("attempt-\\d{4,}")) { throw new IOException("Invalid completed test attempt."); }
                Path attempt = directory.resolve(previous);
                String invalid = verifyCompletion(marker, attempt, out);
                if (invalid == null) {
                    if (!config.skipExisting && !config.dryRun) {
                        throw new IOException("Test already completed; use --skip-existing: " + directory);
                    }
                    out.println("[TEST SKIP] " + identity() + " verified outputs=" + attempt);
                    return new Result(this, "SKIPPED", 0, attempt, "Verified matching test and source hashes");
                }
                out.println("[TEST RETRY] " + identity() + " " + invalid + "; preserving the old attempt");
            }
            if (config.dryRun) {
                out.println("[TEST DRY-RUN] " + identity() + " set=" + setName + " source=" + spec.get("source.stats"));
                return new Result(this, "PLANNED", 0, directory, "No simulation started");
            }
            Path attempt = nextAttempt(directory);
            atomicWrite(attempt.resolve("test.properties"), serialized);
            atomicWrite(attempt.resolve("selected-heuristic.properties"), selectedProperties(spec));
            List<String> command = Arrays.asList(javaExecutable(), "-Xmx" + config.heap,
                    "--add-opens=java.base/java.lang=ALL-UNNAMED", "-cp", absoluteClassPath(),
                    MPSLGPPaperTestMain.class.getName(), "--worker", attempt.resolve("test.properties").toString());
            atomicWrite(attempt.resolve("command.txt"), "Working directory: " + attempt + "\n" + commandText(command) + "\n");
            out.println("[TEST START] " + identity() + " set=" + setName + " preferences=" + config.preferences
                    + " replications=" + config.replications + " output=" + attempt);
            long start = System.nanoTime();
            try {
                int exit = runner.run(command, attempt, line -> out.println("[TEST PREFERENCE] " + identity() + " " + line));
                if (exit != 0) { throw new IOException("Worker exit code " + exit + "; log=" + attempt.resolve("testing.log")); }
                int invalidRows = validateOutputs(attempt, spec);
                verifySource(spec);
                long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
                Map<String, String> result = new TreeMap<>();
                result.put("status", "COMPLETED");
                result.put("configuration.sha256", fingerprint);
                result.put("attempt", attempt.getFileName().toString());
                result.put("finished", Instant.now().toString());
                result.put("duration.seconds", Long.toString(seconds));
                result.put("invalid.preference.rows", Integer.toString(invalidRows));
                result.put("valid.preference.rows", Integer.toString(config.preferences - invalidRows));
                for (String artifact : artifacts()) { result.put("sha256." + artifact, fileHash(attempt.resolve(artifact))); }
                atomicWrite(attempt.resolve("status.properties"), serialize(result));
                atomicWrite(markerFile, serialize(result));
                reportInvalidRows(out, invalidRows, attempt);
                out.println("[TEST DONE] " + identity() + " seconds=" + seconds);
                return new Result(this, "COMPLETED", seconds, attempt,
                        "Validated PSLGP-compatible CSV; invalid preference rows=" + invalidRows);
            } catch (IOException | InterruptedException e) {
                Map<String, String> status = new TreeMap<>();
                status.put("status", "FAILED"); status.put("reason", e.toString());
                status.put("configuration.sha256", fingerprint);
                atomicWrite(attempt.resolve("status.properties"), serialize(status));
                if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); }
                throw e;
            }
        }

        private List<String> artifacts() {
            return Arrays.asList("test.properties", "selected-heuristic.properties", csvName, "preference-results.csv", "testing.log");
        }

        private String verifyCompletion(Properties marker, Path attempt, PrintStream out) throws IOException {
            if (!"COMPLETED".equals(marker.getProperty("status"))) { return "No successful test record"; }
            for (String artifact : artifacts()) {
                Path file = attempt.resolve(artifact);
                if (!Files.isRegularFile(file) || Files.size(file) == 0) { return "Missing artifact " + artifact; }
                if (!fileHash(file).equals(marker.getProperty("sha256." + artifact))) { return "Changed artifact " + artifact; }
            }
            int invalidRows = validateOutputs(attempt, spec);
            if (marker.containsKey("invalid.preference.rows")
                    && !Integer.toString(invalidRows).equals(marker.getProperty("invalid.preference.rows"))) {
                return "Invalid-preference count no longer matches outputs";
            }
            if (marker.containsKey("valid.preference.rows")
                    && !Integer.toString(config.preferences - invalidRows).equals(marker.getProperty("valid.preference.rows"))) {
                return "Valid-preference count no longer matches outputs";
            }
            reportInvalidRows(out, invalidRows, attempt);
            return null;
        }

        private void reportInvalidRows(PrintStream out, int invalidRows, Path attempt) {
            if (invalidRows > 0) {
                out.println("[TEST WARNING] " + identity() + " invalid preference rows=" + invalidRows
                        + "; fitness=-1 retained; completion means all preferences recorded, not all outcomes valid; output=" + attempt);
            }
        }
    }

    private static void worker(Path configFile) throws IOException {
        Properties input = readProperties(configFile);
        require(input, "test.seed", Long.toString(MPSLGPTaskEvaluator.simSeed));
        require(input, "test.scenario", "dynamic-job-shop");
        require(input, "terminal.normalisation", "2");
        Map<String, String> spec = new TreeMap<>();
        for (String key : input.stringPropertyNames()) { spec.put(key, input.getProperty(key)); }
        if (!FORMAT.equals(spec.get("format"))) { throw new IOException("Unsupported test configuration format."); }
        verifySource(spec);
        int task = Integer.parseInt(spec.get("task"));
        int generation = Integer.parseInt(spec.get("generation"));
        SelectedTaskHeuristic selected = ParetoSetLearningMultipleTreeResultFileReader.readFinalTaskHeuristic(
                Paths.get(spec.get("source.stats")), task, generation);
        if (!selected.getSequencingRule().getLispString().equals(spec.get("sequencing.expression"))
                || !selected.getRoutingRule().getLispString().equals(spec.get("routing.expression"))) {
            throw new IOException("Selected rule expressions no longer match the test configuration.");
        }
        MPSLGPTaskEvaluator evaluator = new MPSLGPTaskEvaluator(spec.get("test.set"),
                Arrays.asList(Objective.get(spec.get("objective.0")), Objective.get(spec.get("objective.1"))),
                Integer.parseInt(spec.get("preferences")), Integer.parseInt(spec.get("replications")));
        evaluator.evaluate(selected, Integer.parseInt(spec.get("run")), configFile.toAbsolutePath().getParent(), System.out::println);
        verifySource(spec);
    }

    private static void verifySource(Map<String, String> spec) throws IOException {
        for (String source : Arrays.asList("source.stats", "source.params")) {
            if (!fileHash(Paths.get(spec.get(source))).equals(spec.get(source + ".sha256"))) {
                throw new IOException("Training input changed: " + spec.get(source));
            }
        }
    }

    static int validateOutputs(Path attempt, Map<String, String> spec) throws IOException {
        List<String> rows = Files.readAllLines(attempt.resolve(spec.get("output.csv")), StandardCharsets.UTF_8);
        List<String> details = Files.readAllLines(attempt.resolve("preference-results.csv"), StandardCharsets.UTF_8);
        int count = Integer.parseInt(spec.get("preferences"));
        if (rows.size() != count + 1 || details.size() != count + 1
                || !rows.get(0).equals(MPSLGPTaskEvaluator.CSV_HEADER)
                || !details.get(0).equals(MPSLGPTaskEvaluator.PREFERENCE_HEADER)) {
            throw new IOException("Incomplete or incompatible test CSVs in " + attempt);
        }
        int invalidRows = 0;
        try {
            String structure = null;
            for (int i = 0; i < count; i++) {
                String[] row = rows.get(i + 1).split(",", -1);
                String[] detail = details.get(i + 1).split(",", -1);
                if (row.length != 11 || detail.length != 5 || !row[0].equals(spec.get("run"))
                        || !row[1].equals(spec.get("generation")) || !row[6].equals("2")
                        || Integer.parseInt(detail[0]) != i) { throw new IOException("Incorrect row identity at preference " + i); }
                String sizes = String.join(",", Arrays.copyOfRange(row, 2, 6));
                if (structure != null && !structure.equals(sizes)) { throw new IOException("Heuristic changed across preferences."); }
                structure = sizes;
                for (int n : new int[]{2, 3, 4, 5}) {
                    int minimum = n == 2 || n == 4 ? 1 : 0;
                    if (Integer.parseInt(row[n]) < minimum) { throw new IOException("Invalid tree structure counts."); }
                }
                double first = i / (double)(count - 1);
                double second = (count - 1 - i) / (double)(count - 1);
                double weight0 = Double.parseDouble(detail[1]), weight1 = Double.parseDouble(detail[2]);
                if (!Double.isFinite(weight0) || !Double.isFinite(weight1)
                        || Math.abs(weight0 - first) > 1e-12 || Math.abs(weight1 - second) > 1e-12) {
                    throw new IOException("Wrong preference grid at row " + i);
                }
                boolean invalid = false;
                for (int objective = 0; objective < 2; objective++) {
                    double train = Double.parseDouble(row[7 + 2 * objective]);
                    double test = Double.parseDouble(row[8 + 2 * objective]);
                    if (Double.compare(train, Double.parseDouble(spec.get("training.objective." + objective))) != 0
                            || !MPSLGPTaskEvaluator.isRecordedObjective(test)
                            || Double.compare(test, Double.parseDouble(detail[3 + objective])) != 0) {
                        throw new IOException("Invalid/inconsistent objective at preference " + i);
                    }
                    invalid |= test == MPSLGPTaskEvaluator.INVALID_FITNESS;
                }
                if (invalid) { invalidRows++; }
            }
        } catch (NumberFormatException e) { throw new IOException("Invalid numeric field in test CSV.", e); }
        return invalidRows;
    }

    private static String selectedProperties(Map<String, String> spec) {
        Map<String, String> selected = new TreeMap<>();
        for (String key : Arrays.asList("algorithm", "indicator", "scenario", "run", "task", "benchmark.scenario",
                "generation", "source.stats", "source.stats.sha256", "sequencing.expression", "routing.expression",
                "training.objective.0", "training.objective.1")) { selected.put(key, spec.get(key)); }
        selected.put("selection", "First training-ranked individual in final task-specific Top N section; no test-based selection");
        return serialize(selected);
    }

    private static int benchmark(String objective0, String objective1, String utilization, String dueDate) throws IOException {
        int offset;
        if (objective0.equals("max-flowtime") && objective1.equals("max-weighted-tardiness")) { offset = 0; }
        else if (objective0.equals("max-weighted-flowtime") && objective1.equals("max-tardiness")) { offset = 5; }
        else { throw new IOException("Objective pair is not one of the PSLGP paper tasks: " + objective0 + "/" + objective1); }
        double load, due;
        try { load = Double.parseDouble(utilization); due = Double.parseDouble(dueDate); }
        catch (NumberFormatException e) { throw new IOException("Invalid training scenario metadata.", e); }
        int index = (int)Math.round((load - .70) / .05);
        if (!Double.isFinite(load) || Math.abs(due - 1.5) > 1e-12 || !Double.isFinite(due)
                || index < 0 || index > 4 || Math.abs(load - (.70 + .05 * index)) > 1e-9) {
            throw new IOException("Training workload or due-date factor is incompatible with the paper benchmark.");
        }
        return offset + index + 1;
    }

    static final class Config {
        Path training = Paths.get("src", "mengxu", "ruleanalysis", "MPSLGP", "paper-runs");
        Path output = Paths.get("src", "mengxu", "ruleanalysis", "MPSLGP", "paper-tests");
        List<String> algorithms = Arrays.asList("full"), indicators = Arrays.asList("IGD"), scenarios = Arrays.asList("R1", "H3");
        Set<Integer> tasks = new LinkedHashSet<>();
        int runs = 5, startRun = 0, preferences = 200, replications = 30, parallel = 1;
        String heap = "2g";
        boolean skipExisting, dryRun, smoke, help;

        static Config parse(String[] args) {
            Config c = new Config();
            c.smoke = Arrays.asList(args).contains("--smoke");
            if (c.smoke) { c.preferences = 3; c.replications = 1; }
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (!seen.add(arg)) { throw new IllegalArgumentException("Repeated option: " + arg); }
                switch (arg) {
                    case "--help": case "-h": c.help = true; break;
                    case "--dry-run": c.dryRun = true; break;
                    case "--skip-existing": c.skipExisting = true; break;
                    case "--smoke": break;
                    default:
                        if (i + 1 == args.length || args[i + 1].startsWith("--")) { throw new IllegalArgumentException("Missing value for " + arg); }
                        String value = args[++i];
                        switch (arg) {
                            case "--training-root": case "--results": c.training = Paths.get(value); break;
                            case "--output": c.output = Paths.get(value); break;
                            case "--algorithms": c.algorithms = names(value, "[a-z][a-z0-9-]*"); break;
                            case "--indicators": c.indicators = names(value.toUpperCase(Locale.ROOT), "IGD|HV|GD"); break;
                            case "--scenarios": c.scenarios = names(value.toUpperCase(Locale.ROOT), "R[1-5]|H[1-3]"); break;
                            case "--runs": c.runs = number(arg, value, 1); break;
                            case "--start-run": c.startRun = number(arg, value, 0); break;
                            case "--preferences": c.preferences = number(arg, value, 2); break;
                            case "--replications": c.replications = number(arg, value, 1); break;
                            case "--parallel": c.parallel = number(arg, value, 1); break;
                            case "--tasks":
                                for (String task : value.split(",", -1)) {
                                    if (!c.tasks.add(number(arg, task.trim(), 0))) { throw new IllegalArgumentException("Duplicate task index."); }
                                }
                                break;
                            case "--heap":
                                if (!value.matches("[1-9]\\d*[mMgG]")) { throw new IllegalArgumentException("Use --heap 2g or another JVM heap size."); }
                                c.heap = value; break;
                            case "--skip":
                                if (!value.equals("existing")) { throw new IllegalArgumentException("Use --skip-existing."); }
                                c.skipExisting = true; break;
                            default: throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                }
            }
            if ((long)c.startRun + c.runs > Integer.MAX_VALUE) { throw new IllegalArgumentException("Run range is too large."); }
            c.training = c.training.toAbsolutePath().normalize();
            c.output = c.output.toAbsolutePath().normalize();
            if (c.output.startsWith(c.training) || c.training.startsWith(c.output)) {
                throw new IllegalArgumentException("Training and test output trees must be separate.");
            }
            if (c.smoke) { c.output = c.output.resolve("smoke"); }
            return c;
        }
    }

    private static List<String> names(String text, String pattern) {
        Set<String> names = new LinkedHashSet<>();
        for (String name : text.split(",", -1)) {
            name = name.trim();
            if (!name.matches(pattern) || !names.add(name)) { throw new IllegalArgumentException("Invalid or duplicate selector: " + name); }
        }
        return new ArrayList<>(names);
    }

    private static int number(String option, String value, int minimum) {
        try {
            int result = Integer.parseInt(value);
            if (result >= minimum) { return result; }
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid integer for " + option + ": " + value, e); }
        throw new IllegalArgumentException(option + " must be >= " + minimum);
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) { throw new IOException("Missing metadata: " + key); }
        return value;
    }

    private static void require(Properties properties, String key, String expected) throws IOException {
        if (!required(properties, key).equals(expected)) { throw new IOException("Metadata mismatch for " + key + "; expected " + expected); }
    }

    private static int positive(Properties properties, String key) throws IOException {
        try {
            int value = Integer.parseInt(required(properties, key));
            if (value > 0) { return value; }
        } catch (NumberFormatException e) { throw new IOException("Invalid metadata integer: " + key, e); }
        throw new IOException("Nonpositive metadata: " + key);
    }

    private static Path nextAttempt(Path directory) throws IOException {
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            Path path = directory.resolve(String.format(Locale.ROOT, "attempt-%04d", i));
            if (!Files.exists(path)) { return Files.createDirectory(path); }
        }
        throw new IOException("Too many test attempts in " + directory);
    }

    private static void writePlan(Path batch, List<Job> jobs) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(batch.resolve("paper-test-plan.csv"), StandardCharsets.UTF_8)) {
            writer.write("Algorithm,Indicator,Scenario,Run,Task,BenchmarkScenario,Objective0,Objective1,TestSet,Preferences,Replications,SourceStats,Output\n");
            for (Job job : jobs) {
                writer.write(csv(job.algorithm, job.indicator, job.scenario, job.run, job.task,
                        job.spec.get("benchmark.scenario"), job.spec.get("objective.0"), job.spec.get("objective.1"),
                        job.setName, job.config.preferences, job.config.replications, job.spec.get("source.stats"), job.directory) + "\n");
            }
        }
    }

    private static void aggregate(Path batch, List<Result> results) throws IOException {
        Map<Path, List<Result>> groups = new TreeMap<>();
        for (Result result : results) {
            Job job = result.job;
            Path target = batch.resolve("aggregated").resolve(job.algorithm).resolve(job.indicator).resolve(job.scenario)
                    .resolve(String.format(Locale.ROOT, "task%02d", job.task)).resolve(job.setName + "-ParetoFront.csv");
            groups.computeIfAbsent(target, key -> new ArrayList<>()).add(result);
        }
        for (Map.Entry<Path, List<Result>> group : groups.entrySet()) {
            group.getValue().sort(Comparator.comparingInt(value -> value.job.run));
            StringBuilder csv = new StringBuilder(MPSLGPTaskEvaluator.CSV_HEADER).append('\n');
            for (Result result : group.getValue()) {
                validateOutputs(result.directory, result.job.spec);
                List<String> lines = Files.readAllLines(result.directory.resolve(result.job.csvName), StandardCharsets.UTF_8);
                for (int i = 1; i < lines.size(); i++) { csv.append(lines.get(i)).append('\n'); }
            }
            Files.createDirectories(group.getKey().getParent());
            atomicWrite(group.getKey(), csv.toString());
        }
    }

    private static final class Result {
        final Job job;
        final String status, message;
        final long seconds;
        final Path directory;
        Result(Job job, String status, long seconds, Path directory, String message) {
            this.job = job; this.status = status; this.seconds = seconds; this.directory = directory; this.message = message;
        }
    }

    private static int runProcess(List<String> command, Path directory, Consumer<String> progress)
            throws IOException, InterruptedException {
        Process child = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        ACTIVE.add(child);
        try (BufferedReader input = new BufferedReader(new java.io.InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter log = Files.newBufferedWriter(directory.resolve("testing.log"), StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) {
                log.write(line); log.newLine();
                if (line.startsWith("Preference ")) { log.flush(); progress.accept(line); }
            }
            return child.waitFor();
        } finally {
            ACTIVE.remove(child);
            if (child.isAlive()) {
                child.destroy();
                if (!child.waitFor(3, TimeUnit.SECONDS)) { child.destroyForcibly(); }
            }
        }
    }

    private static void usage(PrintStream out) {
        out.println("Usage: mengxu.ruleanalysis.MPSLGP.MPSLGPPaperTestMain [options]");
        out.println("Default: full / IGD / R1,H3 / seeds 0-4 / one selected heuristic per task.");
        out.println("  --training-root PATH (alias --results)  Completed training root, never modified");
        out.println("  --output PATH                          Separate test output root");
        out.println("  --algorithms LIST --indicators LIST --scenarios LIST --tasks LIST");
        out.println("  --runs N --start-run N --preferences N --replications N   Defaults: 5,0,200,30");
        out.println("  --parallel N --heap 2g --skip-existing (or --skip existing)");
        out.println("  --dry-run   Verify training and export test configurations without simulations");
        out.println("  --smoke     Three preferences, one replication, same simulator; isolated smoke output");
        out.println("Exactly one training-ranked final heuristic is tested. Ensemble/test-based selection is not supported.");
    }
}
