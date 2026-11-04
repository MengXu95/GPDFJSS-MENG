package mengxu.ruleanalysis.MPSLGP;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.ParameterDatabase;
import mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles;
import yimei.jss.gp.GPRun;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.*;

public final class NSGPIIPaperExperimentMain {
    private static final Map<String, int[]> GROUPS = new LinkedHashMap<>();
    private static final java.util.Set<Process> ACTIVE = ConcurrentHashMap.newKeySet();
    static {
        GROUPS.put("R1", new int[]{1, 2}); GROUPS.put("R2", new int[]{3, 4});
        GROUPS.put("R3", new int[]{6, 7}); GROUPS.put("R4", new int[]{1, 3, 5});
        GROUPS.put("R5", new int[]{8, 9}); GROUPS.put("H1", new int[]{1, 10});
        GROUPS.put("H2", new int[]{5, 6}); GROUPS.put("H3", new int[]{1, 5, 8});
    }
    private NSGPIIPaperExperimentMain() { }

    public static final class CountingProblem extends MultipleTreeRuleOptimizationProblem {
        private final AtomicLong calls = new AtomicLong();
        @Override public void evaluate(EvolutionState state, Individual individual, int subpopulation, int threadnum) {
            calls.incrementAndGet();
            super.evaluate(state, individual, subpopulation, threadnum);
        }
    }

    public static final class CountingStatistics extends ec.multiobjective.MultiObjectiveStatistics {
        @Override public void postEvaluationStatistics(EvolutionState state) {
            super.postEvaluationStatistics(state);
            try {
                Path path = Paths.get(state.parameters.getString(new ec.util.Parameter("nsgpii.evaluation-count-file"), null));
                long calls = ((CountingProblem) state.evaluator.p_problem).calls.get();
                atomicWrite(path, "individual.evaluation.calls=" + calls + "\nactual.generation=" + state.generation + "\n");
            } catch (IOException failure) { throw new IllegalStateException("Cannot record evaluation count", failure); }
        }
    }

    static final class Config {
        String action = "plan", budget = "matched", heap = "2g";
        List<String> groups = new ArrayList<>(GROUPS.keySet());
        List<Integer> scenarios = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        int runs = 30, start = 0, parallel = 10, totalPopulation = 1000, smokeGenerations = 3;
        boolean smoke, skipExisting;
        Path template = Paths.get("C:/Users/xumen/Desktop/2026Paper/PSLGP/FinalResultsForPaper/FinalResultsForPaper/fNSGPII/multipletreegp-dynamic/params/multipletreegp-dynamic.params");
        Path output = Paths.get("src/mengxu/ruleanalysis/MPSLGP/nsgpii-generation49");
        Config(String[] arguments) {
            for (int index = 0; index < arguments.length; index++) {
                String argument = arguments[index];
                if (argument.equals("--smoke")) { smoke = true; continue; }
                if (argument.equals("--skip-existing")) { skipExisting = true; continue; }
                if (index + 1 >= arguments.length) { throw new IllegalArgumentException("Missing value for " + argument); }
                String value = arguments[++index];
                switch (argument) {
                    case "--action": action = value; break;
                    case "--budget": budget = value; break;
                    case "--groups": groups = Arrays.asList(value.split(",")); break;
                    case "--scenarios":
                        scenarios = new ArrayList<>();
                        for (String scenario : value.split(",")) { scenarios.add(Integer.parseInt(scenario)); }
                        break;
                    case "--runs": runs = Integer.parseInt(value); break;
                    case "--start-run": start = Integer.parseInt(value); break;
                    case "--parallel": parallel = Integer.parseInt(value); break;
                    case "--heap": heap = value; break;
                    case "--population-total": totalPopulation = Integer.parseInt(value); break;
                    case "--smoke-generations": smokeGenerations = Integer.parseInt(value); break;
                    case "--template": template = Paths.get(value); break;
                    case "--output": output = Paths.get(value); break;
                    default: throw new IllegalArgumentException("Unknown argument " + argument);
                }
            }
            if (!Arrays.asList("plan", "train", "test").contains(action) || !Arrays.asList("matched", "independent").contains(budget)
                    || runs < 1 || start < 0 || parallel < 1 || totalPopulation < 3 || smokeGenerations < 3 || smokeGenerations > 50
                    || (!smoke && smokeGenerations != 3) || !heap.matches("[1-9][0-9]*[mgMG]")
                    || groups.isEmpty() || groups.stream().anyMatch(group -> !GROUPS.containsKey(group))
                    || scenarios.isEmpty() || scenarios.stream().anyMatch(scenario -> scenario < 1 || scenario > 10)
                    || scenarios.stream().distinct().count() != scenarios.size()
                    || groups.stream().distinct().count() != groups.size()) { throw new IllegalArgumentException("Invalid NSGPII experiment configuration"); }
            template = template.toAbsolutePath().normalize(); output = output.toAbsolutePath().normalize();
            if (smoke) { output = output.resolve("smoke"); }
            output = output.resolve(budget);
            if (output.startsWith(template.getParent().getParent().getParent())) { throw new IllegalArgumentException("Output must be outside the historical archive"); }
        }
        int generations() { return smoke ? smokeGenerations : 50; }
        int groupPopulation() { return smoke ? Math.min(totalPopulation, 24) : totalPopulation; }
        int population(String group, int task, int seed) {
            int total = groupPopulation();
            if (budget.equals("independent")) { return total; }
            int tasks = GROUPS.get(group).length;
            int offset = (task - seed % tasks + tasks) % tasks;
            return total / tasks + (offset < total % tasks ? 1 : 0);
        }
    }

    static Map<String, String> loadTemplate(Path path) throws IOException {
        ParameterDatabase database = new ParameterDatabase(path.toFile());
        StringWriter buffer = new StringWriter();
        database.list(new PrintWriter(buffer), false);
        Map<String, String> result = new TreeMap<>();
        for (String line : buffer.toString().split("\\R")) {
            int separator = line.indexOf(" = ");
            if (separator > 0) { result.put(line.substring(0, separator), line.substring(separator + 3)); }
        }
        result.keySet().removeIf(key -> key.startsWith("parent."));
        if (!"mengxu.algorithm.multiobjective.GPRuleEvolutionStateMO".equals(result.get("state"))
                || !"mengxu.algorithm.multiobjective.NSGPII.NSGA2BreederNoEnvironmentalSelection".equals(result.get("breed"))
                || !"mengxu.algorithm.multiobjective.NSGPII.NSGA2EvaluatorNoEnvironmentalSelection".equals(result.get("eval"))
                || !"ec.multiobjective.MultiObjectiveStatistics".equals(result.get("stat"))
                || !"relative".equals(result.get("terminals-from"))) { throw new IOException("Template is not the expected native NSGPII configuration"); }
        return result;
    }

    static void put(Map<String, String> values, Object... pairs) {
        for (int index = 0; index < pairs.length; index += 2) { values.put(pairs[index].toString(), pairs[index + 1].toString()); }
    }

    static final class Job {
        final Config config;
        final String group;
        final int task, scenario, seed, population;
        final Path directory, training;
        final Map<String, String> parameters, identity = new TreeMap<>();
        final String fingerprint;
        Job(Config config, Map<String, String> base, String group, int task, int seed) throws IOException {
            this.config = config; this.group = group; this.task = task; this.seed = seed;
            scenario = GROUPS.get(group)[task]; population = config.population(group, task, seed);
                Path instance = config.budget.equals("independent") ? config.output.resolve("Independent").resolve("S" + scenario)
                    : config.output.resolve(group).resolve("task" + task + "-S" + scenario);
                directory = instance.resolve(String.format(Locale.ROOT, "run%02d", seed));
            training = directory.resolve("training");
            parameters = new TreeMap<>(base);
            String model = "eval.problem.eval-model", sim = model + ".sim-models.0";
            put(parameters, "jobs", 1, "seed.0", seed, "evalthreads", 1, "breedthreads", 1,
                    "generations", config.generations(), "pop.subpop.0.size", population,
                    "checkpoint", false, "quit-on-run-complete", false, "breed.elite.0", 0,
                    "stat.file", "$out.stat", "stat.front", "$front.stat", "stat.do-generation", true,
                    "stat.do-final", true, "stat.do-middle", true, "print-gap", config.generations() - 1,
                    "stat", CountingStatistics.class.getName(),
                    "eval.problem", CountingProblem.class.getName(),
                    "nsgpii.evaluation-count-file", training.resolve("evaluation-count.properties"),
                    model, "mengxu.complexsimulation.ruleevaluation.MultipleTreeMultipleRuleHeterogeneousEvaluationModel",
                    model + ".objectives", 2, model + ".objectives.0", scenario <= 5 ? "max-flowtime" : "max-weighted-flowtime",
                    model + ".objectives.1", scenario <= 5 ? "max-weighted-tardiness" : "max-tardiness",
                    model + ".sim-models", 1, model + ".sim-seed", 0, model + ".rotate-sim-seed", true,
                    sim + ".util-level", String.format(Locale.ROOT, "%.2f", .70 + ((scenario - 1) % 5) * .05),
                    sim + ".num-jobs", config.smoke ? 200 : 5000, sim + ".warmup-jobs", config.smoke ? 50 : 1000,
                    sim + ".replications", 1, sim + ".num-machines", 10, sim + ".min-num-operations", 2,
                    sim + ".max-num-operations", 10, sim + ".due-date-factor", 1.5);
            put(identity, "method", "NSGPII", "group", group, "task", task, "scenario", scenario,
                    "seed", seed, "population", population, "generations", config.generations(),
                    "budget", config.budget, "group.population.total", config.groupPopulation(),
                    "template", config.template, "template.sha256", fileHash(config.template),
                    "selection", "entire final-generation population Pareto front; no external archive",
                    "test.replications", config.smoke ? 1 : 30, "test.seed", MPSLGPTaskEvaluator.simSeed,
                    "smoke", config.smoke);
            fingerprint = sha256((serialize(identity) + serialize(parameters)).getBytes(StandardCharsets.UTF_8));
        }
        String label() { return "NSGPII " + group + " task=" + task + " S" + scenario + " seed=" + seed + " N=" + population; }
        List<BaselineGenerationTestMain.Candidate> finalFront() throws IOException {
            Path stats = training.resolve("out.stat");
            List<String> lines = Files.readAllLines(stats, StandardCharsets.UTF_8);
            int last = -1;
            for (String line : lines) {
                Matcher header = Pattern.compile("Generation: ([0-9]+)").matcher(line.trim());
                if (header.matches()) { last = Integer.parseInt(header.group(1)); }
            }
            if (last != config.generations() - 1) { throw new IOException("Wrong actual final generation " + last + ": " + stats); }
            return BaselineGenerationTestMain.readCandidates(lines, stats, last, BaselineGenerationTestMain.Method.NSGPII, false);
        }
        void verifyTraining() throws IOException {
            Properties marker = readProperties(training.resolve("completed.properties"));
            if (!"COMPLETED".equals(marker.getProperty("status")) || !fingerprint.equals(marker.getProperty("configuration.sha256"))
                    || !fileHash(training.resolve("out.stat")).equals(marker.getProperty("stats.sha256"))
                    || !fileHash(training.resolve("effective.params")).equals(marker.getProperty("params.sha256"))
                    || !fileHash(config.template).equals(identity.get("template.sha256"))) { throw new IOException("Training completion/source mismatch: " + directory); }
            finalFront();
            Properties counts = readProperties(training.resolve("evaluation-count.properties"));
            if (!counts.getProperty("individual.evaluation.calls", "0").equals(marker.getProperty("individual.evaluation.calls"))
                    || !Integer.toString(config.generations() - 1).equals(counts.getProperty("actual.generation"))
                    || (marker.containsKey("counts.sha256") && !fileHash(training.resolve("evaluation-count.properties")).equals(marker.getProperty("counts.sha256")))) {
                throw new IOException("Final evaluation count metadata does not match training completion: " + directory);
            }
        }
        void train() throws Exception {
            if (Files.exists(training)) {
                if (config.skipExisting) { verifyTraining(); System.out.println("[SKIP TRAIN] " + label()); return; }
                throw new IOException("Training output exists; use --skip-existing or another --output: " + training);
            }
            Files.createDirectories(directory); Files.createDirectory(training);
            atomicWrite(training.resolve("effective.params"), serialize(parameters));
            atomicWrite(training.resolve("experiment.properties"), serialize(identity));
            List<String> command = workerCommand("--train-worker", training.resolve("effective.params"));
            long started = System.nanoTime();
            execute(command, training, training.resolve("training.log"), label());
            List<BaselineGenerationTestMain.Candidate> front = finalFront();
            Properties counts = readProperties(training.resolve("evaluation-count.properties"));
            if (Long.parseLong(counts.getProperty("individual.evaluation.calls", "0")) <= 0) { throw new IOException("No evaluation count recorded"); }
            Map<String, String> marker = new TreeMap<>();
            put(marker, "status", "COMPLETED", "configuration.sha256", fingerprint,
                    "actual.final.generation", config.generations() - 1, "final.front.size", front.size(),
                    "individual.evaluation.calls", counts.getProperty("individual.evaluation.calls"),
                    "training.wall.seconds", (System.nanoTime() - started) / 1e9,
                    "counts.sha256", fileHash(training.resolve("evaluation-count.properties")),
                    "stats.sha256", fileHash(training.resolve("out.stat")), "params.sha256", fileHash(training.resolve("effective.params")));
            atomicWrite(training.resolve("completed.properties"), serialize(marker));
        }
        List<String> workerCommand(String flag, Path input) {
            return Arrays.asList(javaExecutable(), "-Xmx" + config.heap, "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "-cp", absoluteClassPath(), NSGPIIPaperExperimentMain.class.getName(), flag, input.toString());
        }
        void test() throws Exception {
            verifyTraining();
            Path output = directory.resolve("test-generation" + (config.generations() - 1));
            Path markerPath = output.resolve("completed.properties");
            if (Files.exists(output)) {
                if (config.skipExisting && Files.isRegularFile(markerPath)) {
                    Properties marker = readProperties(markerPath);
                    if (fingerprint.equals(marker.getProperty("configuration.sha256")) && "COMPLETED".equals(marker.getProperty("status"))
                            && fileHash(output.resolve("test-results.csv")).equals(marker.getProperty("csv.sha256"))) {
                        validateTestRows(output.resolve("test-results.csv"));
                        System.out.println("[SKIP TEST] " + label()); return;
                    }
                }
                throw new IOException("Test output exists/incomplete; preserve it and use another root: " + output);
            }
            Files.createDirectory(output);
            Map<String, String> spec = new TreeMap<>(identity);
            put(spec, "actual.generation", config.generations() - 1, "source.stats", training.resolve("out.stat"),
                    "source.stats.sha256", fileHash(training.resolve("out.stat")),
                    "objective.0", parameters.get("eval.problem.eval-model.objectives.0"),
                    "objective.1", parameters.get("eval.problem.eval-model.objectives.1"),
                    "test.set", "missing-" + parameters.get("eval.problem.eval-model.sim-models.0.util-level") + "-1.5",
                    "rule.count", finalFront().size());
            atomicWrite(output.resolve("test.properties"), serialize(spec));
            execute(workerCommand("--test-worker", output.resolve("test.properties")), output, output.resolve("testing.log"), label());
            if (!fileHash(training.resolve("out.stat")).equals(spec.get("source.stats.sha256"))) { throw new IOException("Training source changed during test"); }
            int tested = validateTestRows(output.resolve("test-results.csv"));
            Map<String, String> marker = new TreeMap<>();
            put(marker, "status", "COMPLETED", "configuration.sha256", fingerprint, "actual.generation", config.generations() - 1,
                    "rules.tested", tested, "csv.sha256", fileHash(output.resolve("test-results.csv")));
            atomicWrite(markerPath, serialize(marker));
        }
        int validateTestRows(Path csv) throws IOException {
            List<BaselineGenerationTestMain.Candidate> front = finalFront();
            List<String> rows = Files.readAllLines(csv, StandardCharsets.UTF_8);
            if (rows.size() != front.size() + 1 || !rows.get(0).equals("Run,Generation,CandidateIndex,SeqRuleSize,RoutRuleSize,TrainFitness0,TrainFitness1,TestFitness0,TestFitness1")) {
                throw new IOException("Incomplete final-front test rows or wrong schema");
            }
            for (int index = 0; index < front.size(); index++) {
                String[] fields = BaselineGenerationTestMain.numericCsvRow(rows.get(index + 1), 9);
                BaselineGenerationTestMain.Candidate candidate = front.get(index);
                if (!fields[0].equals(Integer.toString(seed)) || !fields[1].equals(Integer.toString(config.generations() - 1))
                        || !fields[2].equals(Integer.toString(index))
                        || Integer.parseInt(fields[3]) != candidate.sequencing.getGPTree().child.numNodes(ec.gp.GPNode.NODESEARCH_ALL)
                        || Integer.parseInt(fields[4]) != candidate.routing.getGPTree().child.numNodes(ec.gp.GPNode.NODESEARCH_ALL)
                        || Double.compare(Double.parseDouble(fields[5]), candidate.fitness[0]) != 0
                        || Double.compare(Double.parseDouble(fields[6]), candidate.fitness[1]) != 0
                        || !MPSLGPTaskEvaluator.isRecordedObjective(Double.parseDouble(fields[7]))
                        || !MPSLGPTaskEvaluator.isRecordedObjective(Double.parseDouble(fields[8]))) {
                    throw new IOException("Wrong run/generation/rule identity or objectives in final-front test row " + index);
                }
            }
            return front.size();
        }
    }

    private static void execute(List<String> command, Path cwd, Path log, String identity) throws Exception {
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        ACTIVE.add(process);
        System.out.println("[START] " + identity + " pid=" + process.pid() + " log=" + log);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) { throw new InterruptedException("Interrupted " + identity); }
                writer.write(line); writer.newLine(); writer.flush();
                if (line.startsWith("Generation") || line.startsWith("[NSGPII") || line.startsWith("WARNING") || line.startsWith("FATAL")) {
                    System.out.println("[" + identity + "] " + line);
                }
            }
            if (process.waitFor() != 0) { throw new IOException("Worker failed; see " + log); }
        } finally { ACTIVE.remove(process); if (process.isAlive()) { process.destroyForcibly(); } }
    }

    private static void testWorker(Path input) throws IOException {
        Properties spec = readProperties(input);
        Path source = Paths.get(spec.getProperty("source.stats"));
        if (!fileHash(source).equals(spec.getProperty("source.stats.sha256"))) { throw new IOException("Training stats hash mismatch"); }
        int generation = Integer.parseInt(spec.getProperty("actual.generation"));
        List<BaselineGenerationTestMain.Candidate> candidates = BaselineGenerationTestMain.readCandidates(
                Files.readAllLines(source, StandardCharsets.UTF_8), source, generation, BaselineGenerationTestMain.Method.NSGPII, false);
        if (candidates.size() != Integer.parseInt(spec.getProperty("rule.count"))) { throw new IOException("Final front size mismatch"); }
        List<Objective> objectives = Arrays.asList(Objective.get(spec.getProperty("objective.0")), Objective.get(spec.getProperty("objective.1")));
        SchedulingSet set = new MPSLGPTaskEvaluator(spec.getProperty("test.set"), objectives, 200,
                Integer.parseInt(spec.getProperty("test.replications"))).generateTestSet();
        MPSLGPTaskEvaluator.prepareFinalRuleTestSet(set);
        try (BufferedWriter writer = Files.newBufferedWriter(input.getParent().resolve("test-results.csv"), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW)) {
            writer.write("Run,Generation,CandidateIndex,SeqRuleSize,RoutRuleSize,TrainFitness0,TrainFitness1,TestFitness0,TestFitness1\n");
            for (int index = 0; index < candidates.size(); index++) {
                BaselineGenerationTestMain.Candidate candidate = candidates.get(index);
                MultiObjectiveFitness fitness = new MultiObjectiveFitness();
                candidate.sequencing.calcFitness(fitness, null, set, candidate.routing, objectives);
                double[] values = fitness.getObjectives();
                if (values.length != 2 || !MPSLGPTaskEvaluator.isRecordedObjective(values[0]) || !MPSLGPTaskEvaluator.isRecordedObjective(values[1])) {
                    throw new IOException("Invalid final-front test objectives");
                }
                writer.write(csv(spec.getProperty("seed"), generation, index,
                        candidate.sequencing.getGPTree().child.numNodes(ec.gp.GPNode.NODESEARCH_ALL),
                        candidate.routing.getGPTree().child.numNodes(ec.gp.GPNode.NODESEARCH_ALL),
                        candidate.fitness[0], candidate.fitness[1], values[0], values[1]) + "\n");
                writer.flush();
                System.out.println("[NSGPII TEST] rule=" + (index + 1) + "/" + candidates.size() + " actualGeneration=" + generation);
            }
        }
        if (!fileHash(source).equals(spec.getProperty("source.stats.sha256"))) { throw new IOException("Training source changed during evaluation"); }
    }

    private static void selfTest() {
        Config matched = new Config(new String[0]);
        for (int seed = 0; seed < 30; seed++) {
            for (String group : GROUPS.keySet()) {
                int sum = 0;
                for (int task = 0; task < GROUPS.get(group).length; task++) { sum += matched.population(group, task, seed); }
                if (sum != 1000) { throw new AssertionError("Group budget " + group); }
            }
        }
        if (matched.parallel != 10 || matched.generations() != 50 || matched.population("R1", 0, 0) != 500
                || matched.population("R4", 0, 0) != 334 || matched.population("R4", 1, 1) != 334
                || new Config(new String[]{"--budget", "independent"}).population("R4", 0, 0) != 1000) {
            throw new AssertionError("Generation/parallel/quota defaults");
        }
        System.out.println("PASS: native NSGPII experiment plan,50 evaluated generations,final49,ten workers,500/500 and rotating334/333/333 budgets.");
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && arguments[0].equals("--self-test")) { selfTest(); return; }
        if (arguments.length == 2 && arguments[0].equals("--train-worker")) { GPRun.main(new String[]{"-file", arguments[1]}); return; }
        if (arguments.length == 2 && arguments[0].equals("--test-worker")) { testWorker(Paths.get(arguments[1])); return; }
        Config config = new Config(arguments);
        Map<String, String> base = loadTemplate(config.template);
        String templateHash = fileHash(config.template);
        List<Job> jobs = new ArrayList<>();
        java.util.Set<Integer> independentScenarios = new java.util.HashSet<>();
        for (String group : config.groups) {
            for (int task = 0; task < GROUPS.get(group).length; task++) {
                if (!config.scenarios.contains(GROUPS.get(group)[task])) { continue; }
            if (config.budget.equals("independent") && !independentScenarios.add(GROUPS.get(group)[task])) { continue; }
                for (int seed = config.start; seed < config.start + config.runs; seed++) { jobs.add(new Job(config, base, group, task, seed)); }
            }
        }
        System.out.println("[PLAN] action=" + config.action + " budget=" + config.budget + " jobs=" + jobs.size()
                + " parallel=" + config.parallel + " evaluatedGenerations=" + config.generations() + " finalGeneration=" + (config.generations() - 1));
        for (Job job : jobs) { System.out.println("[INSTANCE] " + job.label() + " output=" + job.directory); }
        if (jobs.isEmpty()) { throw new IllegalArgumentException("Selected groups do not contain the requested scenarios"); }
        if (config.action.equals("plan")) {
            if (!fileHash(config.template).equals(templateHash)) { throw new IOException("Template changed during planning"); }
            System.out.println("PASS: plan only; no training, tests or result directories created."); return;
        }
        if (config.action.equals("test")) { for (Job job : jobs) { job.verifyTraining(); } }
        else {
            for (Job job : jobs) {
                if (Files.exists(job.training)) {
                    if (!config.skipExisting) { throw new IOException("Training output exists: " + job.training); }
                    job.verifyTraining();
                }
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(config.parallel);
        Thread shutdown = new Thread(() -> { pool.shutdownNow(); ACTIVE.forEach(Process::destroy); }, "nsgpii-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        int failed = 0;
        try {
            ExecutorCompletionService<String> completion = new ExecutorCompletionService<>(pool);
            for (Job job : jobs) {
                completion.submit(() -> {
                    try {
                        if (config.action.equals("train")) { job.train(); } else { job.test(); }
                        return "COMPLETED " + job.label();
                    } catch (Exception failure) { return "FAILED " + job.label() + " " + failure; }
                });
            }
            for (int index = 0; index < jobs.size(); index++) {
                Future<String> future = completion.take(); String result = future.get();
                if (result.startsWith("FAILED")) { failed++; }
                System.out.println("[PROGRESS] " + (index + 1) + "/" + jobs.size() + " " + result);
            }
        } finally {
            pool.shutdownNow(); ACTIVE.forEach(Process::destroyForcibly); Runtime.getRuntime().removeShutdownHook(shutdown);
        }
        if (!fileHash(config.template).equals(templateHash)) { throw new IOException("Historical template changed"); }
        if (failed > 0) { throw new IOException(failed + " NSGPII jobs failed; preserved outputs/logs, no historical results overwritten."); }
    }
}