package mengxu.ruleanalysis.MPSLGP;

import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import yimei.jss.gp.PreferenceStateAccess;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.UniqueTerminalsGatherer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BaselineGenerationTestMain {
    private static final Pattern GENERATION = Pattern.compile("Generation: ([0-9]+)");
    private static final Pattern FITNESS = Pattern.compile("Fitness: \\[([^\\]]+)\\]");
    private static final String ROOT = "C:\\Users\\xumen\\Desktop\\2026Paper\\PSLGP\\FinalResultsForPaper\\FinalResultsForPaper";

    private BaselineGenerationTestMain() { }

    enum Method {
        PSLGP_I("PSLIGD_BR"), NSGPII("fNSGPII"), MOGP_D("fMOGPD");
        final String folder;
        Method(String folder) { this.folder = folder; }
        static Method parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_').replace('/', '_'));
        }
    }

    static final class Candidate {
        final GPRule sequencing, routing;
        final double[] fitness;
        Candidate(String sequencing, String routing, double[] fitness) {
            this.sequencing = GPRule.readFromLispExpression(RuleType.SEQUENCING, sequencing);
            this.routing = GPRule.readFromLispExpression(RuleType.ROUTING, routing);
            this.fitness = fitness.clone();
        }
    }

    static List<Candidate> readCandidates(List<String> lines, Path source, int requested, Method method,
                                         boolean bestIndividual) throws IOException {
        String heading = method == Method.PSLGP_I
                ? (bestIndividual ? "Best Individual:" : "Top N individuals:")
                : "Pareto Front of Subpopulation 0";
        int generation = -1;
        boolean generationFound = false;
        List<Candidate> selected = Collections.emptyList();
        for (int position = 0; position < lines.size(); position++) {
            String line = lines.get(position).trim();
            Matcher header = GENERATION.matcher(line);
            if (header.matches()) {
                generation = Integer.parseInt(header.group(1));
                generationFound |= generation == requested;
            }
            if (generation != requested || !line.equals(heading)) { continue; }
            List<Candidate> block = new ArrayList<>();
            int cursor = position + 1;
            while (cursor < lines.size() && !lines.get(cursor).trim().isEmpty()) {
                if (GENERATION.matcher(lines.get(cursor).trim()).matches()) { break; }
                if (!lines.get(cursor).trim().equals("Evaluated: true")) { cursor++; continue; }
                cursor++;
                if (cursor >= lines.size()) { throw new IOException("Incomplete fitness in " + source); }
                Matcher fitness = FITNESS.matcher(lines.get(cursor++).trim());
                if (!fitness.matches()) { throw new IOException("Malformed fitness in " + source); }
                String[] values = fitness.group(1).trim().split("\\s+");
                if (values.length != 2) { throw new IOException("Expected two objectives in " + source); }
                double[] objectives = {Double.parseDouble(values[0]), Double.parseDouble(values[1])};
                while (cursor < lines.size() && !lines.get(cursor).trim().equals("Tree 0:")) {
                    if (lines.get(cursor).trim().isEmpty() || GENERATION.matcher(lines.get(cursor).trim()).matches()) {
                        throw new IOException("Missing Tree 0 in " + source);
                    }
                    cursor++;
                }
                if (cursor + 3 >= lines.size() || !lines.get(cursor + 2).trim().equals("Tree 1:")) {
                    throw new IOException("Incomplete dual-tree individual in " + source);
                }
                block.add(new Candidate(lines.get(cursor + 1).trim(), lines.get(cursor + 3).trim(), objectives));
                cursor += 4;
            }
            if (!block.isEmpty()) {
                selected = method == Method.PSLGP_I ? Collections.singletonList(block.get(0)) : block;
                if (method == Method.PSLGP_I) { break; }
            }
        }
        if (selected.isEmpty()) {
            throw new IOException(source + ": " + (generationFound ? "generation " + requested + " has no " + heading
                    : "generation " + requested + " is absent")
                    + ". No different generation, archive, or single-best front is substituted.");
        }
        return selected;
    }

    static final class Config {
        Path training = Paths.get(ROOT), output = Paths.get("src", "mengxu", "ruleanalysis", "MPSLGP", "baseline-tests-generation49");
        List<Method> methods = Collections.singletonList(Method.PSLGP_I);
        List<Integer> scenarios = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        int generation = 49, runs = 30, startRun = 0, preferences = 200, replications = 30, parallel = 5;
        String heap = "2g";
        boolean bestIndividual = true, dryRun, skipExisting, smoke;
        Config(String[] arguments) {
            for (int index = 0; index < arguments.length; index++) {
                String argument = arguments[index];
                if (argument.equals("--dry-run")) { dryRun = true; continue; }
                if (argument.equals("--skip-existing")) { skipExisting = true; continue; }
                if (argument.equals("--smoke")) { smoke = true; continue; }
                if (index + 1 >= arguments.length) { throw new IllegalArgumentException("Missing value for " + argument); }
                String value = arguments[++index];
                switch (argument) {
                    case "--training-root": training = Paths.get(value); break;
                    case "--output": output = Paths.get(value); break;
                    case "--generation": generation = Integer.parseInt(value); break;
                    case "--runs": runs = Integer.parseInt(value); break;
                    case "--start-run": startRun = Integer.parseInt(value); break;
                    case "--preferences": preferences = Integer.parseInt(value); break;
                    case "--replications": replications = Integer.parseInt(value); break;
                    case "--parallel": parallel = Integer.parseInt(value); break;
                    case "--heap": heap = value; break;
                    case "--methods":
                        methods = new ArrayList<>();
                        for (String method : value.split(",")) { methods.add(Method.parse(method)); }
                        break;
                    case "--scenarios":
                        scenarios = new ArrayList<>();
                        for (String scenario : value.split(",")) { scenarios.add(Integer.parseInt(scenario)); }
                        break;
                    case "--pslgp-source":
                        if (!value.equals("top-n") && !value.equals("best-individual")) {
                            throw new IllegalArgumentException("--pslgp-source must be top-n or best-individual");
                        }
                        bestIndividual = value.equals("best-individual"); break;
                    default: throw new IllegalArgumentException("Unknown argument " + argument);
                }
            }
            if (smoke) { preferences = 3; replications = 1; output = output.resolve("smoke"); }
            if (methods.size() != 1 || methods.get(0) != Method.PSLGP_I) {
                throw new IllegalArgumentException("This retest runner currently supports only PSLGP-I; NSGPII and MOGP/D are disabled.");
            }
            if (generation < 0 || runs < 1 || startRun < 0 || parallel < 1 || preferences < 2 || replications < 1
                    || !heap.matches("[1-9][0-9]*[mgMG]") || methods.isEmpty() || scenarios.isEmpty()
                    || scenarios.stream().anyMatch(scenario -> scenario < 1 || scenario > 10)) {
                throw new IllegalArgumentException("Invalid generation, run, scenario, evaluation or worker configuration");
            }
            if (!smoke && (preferences != 200 || replications != 30)) {
                throw new IllegalArgumentException("Non-paper evaluation counts require --smoke and an isolated smoke directory");
            }
            training = training.toAbsolutePath().normalize(); output = output.toAbsolutePath().normalize();
            if (output.startsWith(training) || training.startsWith(output)) {
                throw new IllegalArgumentException("Test output must be separate from the training archive");
            }
        }
    }

    static final class Job {
        final Config config;
        final Path directory;
        final Map<String, String> spec = new TreeMap<>();
        final String fingerprint;
        Job(Config config, Method method, int scenario, int run) throws IOException {
            this.config = config;
            String utilization = new String[]{"0.70", "0.75", "0.80", "0.85", "0.90"}[(scenario - 1) % 5];
            String pair = scenario <= 5 ? "max-flowtime-max-weighted-tardiness" : "max-weighted-flowtime-max-tardiness";
            Path base = config.training.resolve(method.folder).resolve("multipletreegp-dynamic");
            Path source = base.resolve(pair + "-" + utilization + "-1.5").resolve("job." + run + ".out.stat");
            Path parameters = base.resolve("params").resolve("multipletreegp-dynamic.params");
            String sourceHash = PaperExperimentFiles.fileHash(source);
            List<Candidate> candidates = readCandidates(Files.readAllLines(source, StandardCharsets.UTF_8), source,
                    config.generation, method, config.bestIndividual);
            if (!sourceHash.equals(PaperExperimentFiles.fileHash(source))) { throw new IOException("Source changed " + source); }
            directory = config.output.resolve(method.name()).resolve("S" + scenario)
                    .resolve(String.format(Locale.ROOT, "run%02d", run)).resolve("generation" + config.generation);
            spec.put("format", "baseline-generation-test-v1");
            spec.put("method", method.name()); spec.put("scenario", Integer.toString(scenario));
            spec.put("run", Integer.toString(run)); spec.put("generation", Integer.toString(config.generation));
            spec.put("selection", method == Method.PSLGP_I ? (config.bestIndividual ? "recorded Best Individual" : "first recorded Top N individual")
                    : "serialized population Pareto front, not archive or best-of-run");
            spec.put("source.stats", source.toString()); spec.put("source.stats.sha256", sourceHash);
            spec.put("source.params", parameters.toString()); spec.put("source.params.sha256", PaperExperimentFiles.fileHash(parameters));
            spec.put("test.set", "missing-" + utilization + "-1.5");
            spec.put("objective.0", scenario <= 5 ? "max-flowtime" : "max-weighted-flowtime");
            spec.put("objective.1", scenario <= 5 ? "max-weighted-tardiness" : "max-tardiness");
            spec.put("preferences", Integer.toString(config.preferences)); spec.put("replications", Integer.toString(config.replications));
            spec.put("test.seed", Long.toString(MPSLGPTaskEvaluator.simSeed)); spec.put("smoke", Boolean.toString(config.smoke));
            spec.put("rule.count", Integer.toString(candidates.size()));
            spec.put("output.csv", "missing-" + utilization + "-1.5-" + run + "-ParetoFront.csv");
            for (int index = 0; index < candidates.size(); index++) {
                Candidate candidate = candidates.get(index);
                spec.put("rule." + index + ".sequencing", candidate.sequencing.getLispString());
                spec.put("rule." + index + ".routing", candidate.routing.getLispString());
                spec.put("rule." + index + ".fitness.0", Double.toString(candidate.fitness[0]));
                spec.put("rule." + index + ".fitness.1", Double.toString(candidate.fitness[1]));
            }
            fingerprint = PaperExperimentFiles.sha256(PaperExperimentFiles.serialize(spec).getBytes(StandardCharsets.UTF_8));
        }
        void run() throws Exception {
            Path completed = directory.resolve("completed.properties");
            if (Files.exists(directory)) {
                if (config.skipExisting && Files.isRegularFile(completed)) {
                    Properties marker = PaperExperimentFiles.readProperties(completed);
                    if (fingerprint.equals(marker.getProperty("configuration.sha256"))
                            && "COMPLETED".equals(marker.getProperty("status"))
                            && PaperExperimentFiles.fileHash(directory.resolve(spec.get("output.csv"))).equals(marker.getProperty("csv.sha256"))) {
                        System.out.println("[SKIP VERIFIED] " + directory); return;
                    }
                }
                throw new IOException("Output already exists or is incomplete; use another --output: " + directory);
            }
            Files.createDirectories(directory.getParent()); Files.createDirectory(directory);
            Path input = directory.resolve("test.properties");
            PaperExperimentFiles.atomicWrite(input, PaperExperimentFiles.serialize(spec));
            List<String> command = Arrays.asList(PaperExperimentFiles.javaExecutable(), "-Xmx" + config.heap,
                    "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-cp", PaperExperimentFiles.absoluteClassPath(),
                    BaselineGenerationTestMain.class.getName(), "--worker", input.toString());
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(directory.resolve("testing.log").toFile()).start();
            try {
                if (process.waitFor() != 0) { throw new IOException("Test worker failed: " + directory.resolve("testing.log")); }
            } finally { if (process.isAlive()) { process.destroyForcibly(); } }
            verifySources(spec);
            Map<String, String> marker = new TreeMap<>();
            marker.put("status", "COMPLETED"); marker.put("configuration.sha256", fingerprint);
            marker.put("actual.generation", spec.get("generation"));
            marker.put("csv.sha256", PaperExperimentFiles.fileHash(directory.resolve(spec.get("output.csv"))));
            PaperExperimentFiles.atomicWrite(completed, PaperExperimentFiles.serialize(marker));
            System.out.println("[COMPLETED] " + directory);
        }
    }

    private static void verifySources(Map<String, String> spec) throws IOException {
        for (String key : Arrays.asList("source.stats", "source.params")) {
            if (!PaperExperimentFiles.fileHash(Paths.get(spec.get(key))).equals(spec.get(key + ".sha256"))) {
                throw new IOException("Training source changed: " + spec.get(key));
            }
        }
    }

    private static void worker(Path input) throws IOException {
        Properties properties = PaperExperimentFiles.readProperties(input);
        Map<String, String> spec = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) { spec.put(key, properties.getProperty(key)); }
        verifySources(spec);
        Method method = Method.parse(spec.get("method"));
        if (method != Method.PSLGP_I) { throw new IOException("Only PSLGP-I workers are enabled in this retest runner."); }
        PreferenceStateAccess.verifyRuntime();
        int run = Integer.parseInt(spec.get("run")), generation = Integer.parseInt(spec.get("generation"));
        int count = Integer.parseInt(spec.get("rule.count")), preferences = Integer.parseInt(spec.get("preferences"));
        Path source = Paths.get(spec.get("source.stats"));
        List<Candidate> recorded = readCandidates(Files.readAllLines(source, StandardCharsets.UTF_8), source,
                generation, method, spec.get("selection").equals("recorded Best Individual"));
        if (recorded.size() != count) { throw new IOException("Selected population size changed"); }
        for (int index = 0; index < count; index++) {
            Candidate candidate = recorded.get(index);
            if (!candidate.sequencing.getLispString().equals(spec.get("rule." + index + ".sequencing"))
                    || !candidate.routing.getLispString().equals(spec.get("rule." + index + ".routing"))
                    || Double.compare(candidate.fitness[0], Double.parseDouble(spec.get("rule." + index + ".fitness.0"))) != 0
                    || Double.compare(candidate.fitness[1], Double.parseDouble(spec.get("rule." + index + ".fitness.1"))) != 0) {
                throw new IOException("Selected generation/rule identity differs from training source");
            }
        }
        List<Objective> objectives = Arrays.asList(Objective.get(spec.get("objective.0")), Objective.get(spec.get("objective.1")));
        MPSLGPTaskEvaluator evaluator = new MPSLGPTaskEvaluator(spec.get("test.set"), objectives, preferences,
                Integer.parseInt(spec.get("replications")));
        SchedulingSet set = evaluator.generateTestSet();
        MPSLGPTaskEvaluator.prepareFinalRuleTestSet(set);
        double[][] vectors = evaluator.preferenceVectors();
        GPRuleEvolutionStatePSL state = MPSLGPTaskEvaluator.evaluationState(vectors);
        Path output = input.toAbsolutePath().getParent();
        String header = MPSLGPTaskEvaluator.CSV_HEADER;
        if (method == Method.PSLGP_I) { header = header.replace("SeqRuleSize", "ind,SeqRuleSize"); }
        try (BufferedWriter csv = Files.newBufferedWriter(output.resolve(spec.get("output.csv")), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
             BufferedWriter details = Files.newBufferedWriter(output.resolve("test-outcomes.csv"), StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE_NEW)) {
            csv.write(header + "\n");
            details.write("CandidateIndex,PreferenceIndex,Weight0,Weight1,TestFitness0,TestFitness1\n");
            for (int index = 0; index < count; index++) {
                Candidate candidate = new Candidate(spec.get("rule." + index + ".sequencing"), spec.get("rule." + index + ".routing"),
                        new double[]{Double.parseDouble(spec.get("rule." + index + ".fitness.0")), Double.parseDouble(spec.get("rule." + index + ".fitness.1"))});
                int samples = method == Method.PSLGP_I ? preferences : 1;
                for (int preference = 0; preference < samples; preference++) {
                    double[] result;
                    if (method == Method.PSLGP_I) {
                        result = MPSLGPTaskEvaluator.evaluatePreference(candidate.sequencing, candidate.routing, set, objectives, state, preference);
                    } else {
                        MultiObjectiveFitness fitness = new MultiObjectiveFitness();
                        candidate.sequencing.calcFitness(fitness, null, set, candidate.routing, objectives);
                        result = fitness.getObjectives();
                    }
                    if (result.length != 2 || !MPSLGPTaskEvaluator.isRecordedObjective(result[0])
                            || !MPSLGPTaskEvaluator.isRecordedObjective(result[1])) { throw new IOException("Invalid test objectives"); }
                    List<Object> fields = new ArrayList<>(Arrays.asList(run, generation));
                    if (method == Method.PSLGP_I) { fields.add(index); }
                    fields.addAll(Arrays.asList(candidate.sequencing.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL),
                            candidate.sequencing.getGPTree().child.numNodes(new UniqueTerminalsGatherer()),
                            candidate.routing.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL),
                            candidate.routing.getGPTree().child.numNodes(new UniqueTerminalsGatherer()), 2,
                            candidate.fitness[0], result[0], candidate.fitness[1], result[1]));
                    csv.write(PaperExperimentFiles.csv(fields.toArray()) + "\n");
                    details.write(PaperExperimentFiles.csv(index, method == Method.PSLGP_I ? preference : -1,
                            method == Method.PSLGP_I ? vectors[preference][0] : "", method == Method.PSLGP_I ? vectors[preference][1] : "",
                            result[0], result[1]) + "\n");
                }
                System.out.println("Tested candidate " + (index + 1) + "/" + count + " generation=" + generation);
            }
        }
        verifySources(spec);
    }

    private static void selfTest() throws IOException {
        Config defaults = new Config(new String[0]);
        if (!defaults.methods.equals(Collections.singletonList(Method.PSLGP_I)) || !defaults.bestIndividual || defaults.parallel != 5) {
            throw new AssertionError("PSLGP-I saved-best and five-worker defaults");
        }
        for (String unsupported : Arrays.asList("NSGPII", "MOGP-D", "PSLGP-I,NSGPII")) {
            boolean rejectedMethod = false;
            try { new Config(new String[]{"--methods", unsupported}); }
            catch (IllegalArgumentException expected) { rejectedMethod = true; }
            if (!rejectedMethod) { throw new AssertionError("Disabled method accepted: " + unsupported); }
        }
        String record = "Evaluated: true\nFitness: [2.0 3.0]\nTree 0:\nPT\nTree 1:\nNIQ\n";
        List<String> lines = Arrays.asList(("Generation: 48\nBest Individual:\n" + record + "\nGeneration: 49\n"
                + "Top N individuals:\n" + record + record + "\nGeneration: 50\nBest Individual:\n" + record).split("\n", -1));
        Path source = Paths.get("self-test.stat");
        if (readCandidates(lines, source, 49, Method.PSLGP_I, false).size() != 1) { throw new AssertionError("Top-N rank0"); }
        List<String> best = Arrays.asList(("Generation: 49\nBest Individual:\nSubpopulation 0:\n" + record).split("\n", -1));
        if (readCandidates(best, source, 49, Method.PSLGP_I, true).size() != 1) { throw new AssertionError("Explicit best"); }
        boolean rejected = false;
        try { readCandidates(best, source, 50, Method.PSLGP_I, true); } catch (IOException expected) { rejected = true; }
        if (!rejected) { throw new AssertionError("No generation fallback"); }
        System.out.println("PASS: PSLGP-I only, saved generation49 best, five-worker defaults, Top-N opt-in, disabled methods and generation fallback rejection.");
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && arguments[0].equals("--self-test")) { selfTest(); return; }
        if (arguments.length == 2 && arguments[0].equals("--worker")) { worker(Paths.get(arguments[1])); return; }
        Config config = new Config(arguments);
        List<Job> jobs = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Method method : config.methods) {
            for (int scenario : config.scenarios) {
                for (int run = config.startRun; run < config.startRun + config.runs; run++) {
                    try { jobs.add(new Job(config, method, scenario, run)); }
                    catch (IOException failure) { missing.add(failure.getMessage()); }
                }
            }
        }
        System.out.println("[PREFLIGHT] ready=" + jobs.size() + " missing=" + missing.size() + " generation=" + config.generation);
        for (String message : missing.subList(0, Math.min(10, missing.size()))) { System.err.println(message); }
        if (!missing.isEmpty()) { throw new IOException("Missing requested-generation outputs; no tests started and no result files written."); }
        if (config.dryRun) { System.out.println("PASS: dry run; no simulations or output writes."); return; }
        ExecutorService pool = Executors.newFixedThreadPool(config.parallel);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Job job : jobs) { futures.add(pool.submit(() -> { job.run(); return null; })); }
            for (Future<?> future : futures) { future.get(); }
        } finally { pool.shutdownNow(); }
    }
}