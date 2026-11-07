package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.Statistics;
import ec.gp.GPIndividual;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseFitness;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.UniformFiles;

public final class ECumulativeReplay {
    static final String PROTOCOL = "lexicase-fixed-native-cumulative-v1";
    private ECumulativeReplay() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || Arrays.equals(args, new String[]{"--help"})) {
            System.out.println("ECumulativeReplay --config FILE [--preflight|--smoke|--verify-run-zero|--summarise] [--resume]");
            return;
        }
        if (args.length == 2 && args[0].equals("--request")) {
            try (EWorkerLease lease = EWorkerLease.start()) { worker(EFiles.read(Path.of(args[1]))); }
            return;
        }
        run(Options.parse(args));
    }

    static void require(boolean valid, String message) throws IOException {
        if (!valid) { throw new IOException(message); }
    }

    static final class Options {
        Path config, source, output, legacy;
        EConfiguration study;
        EParallelOptions parallel;
        boolean resume, preflight, summarise;
        int runs = 30, generations = 201;
        String mode = "FORMAL";

        static Options parse(String[] args) throws IOException {
            Options result = new Options();
            int modes = 0;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--config":
                        require(result.config == null && i + 1 < args.length, "Use --config FILE once");
                        result.config = Path.of(args[++i]).toRealPath(); break;
                    case "--resume": require(!result.resume, "Duplicate --resume"); result.resume = true; break;
                    case "--preflight": result.preflight = true; modes++; break;
                    case "--summarise": result.summarise = true; modes++; break;
                    case "--smoke": result.mode = "SMOKE"; result.runs = 5; result.generations = 3; modes++; break;
                    case "--verify-run-zero":
                        result.mode = "PARITY_CHECK"; result.runs = 1; result.generations = 21; modes++; break;
                    default: throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }
            require(result.config != null && modes <= 1, "Specify one configuration and at most one execution mode");
            Properties p = EFiles.read(result.config);
            require(p.size() == 7 && p.stringPropertyNames().containsAll(Arrays.asList("source-root",
                    "output-root", "smoke-output-root", "parity-output-root", "legacy-gpf-params", "workers", "worker-heap")),
                    "Unexpected cumulative configuration keys");
            result.source = resolve(result.config, EFiles.required(p, "source-root")).toRealPath();
            result.legacy = resolve(result.config, EFiles.required(p, "legacy-gpf-params")).toRealPath();
            String key = result.mode.equals("SMOKE") ? "smoke-output-root"
                    : result.mode.equals("PARITY_CHECK") ? "parity-output-root" : "output-root";
            result.output = resolve(result.config, EFiles.required(p, key)).toAbsolutePath().normalize();
            Path original = result.source.resolve("training").resolve("resolved-study.properties").toRealPath();
            result.parallel = EParallelOptions.parse(new String[]{"--config", original.toString(),
                    "--workers", EFiles.required(p, "workers"), "--worker-heap", EFiles.required(p, "worker-heap")});
            result.study = result.parallel.config;
            return result;
        }

        static Path resolve(Path config, String value) {
            Path path = Path.of(value);
            return path.isAbsolute() ? path : config.getParent().resolve(path);
        }
    }

    static EConfiguration.Case item(int run) {
        return new EConfiguration.Case(EConfiguration.Method.LEXICASE, EConfiguration.Regime.fixed,
                EConfiguration.Scenario.T085, run);
    }

    static Path sourceCase(Options options, int run) throws IOException {
        Path root = options.source.resolve("training");
        return EFiles.mapped(root, EFiles.required(EFiles.read(root.resolve("manifest.properties")), item(run).key() + ".path"));
    }

    static String identity(Options options) throws IOException {
        EConfiguration c = options.study;
        require(c.runs == 30 && c.generations == 201 && c.population == 500 && c.cases == 25
                && c.snapshotInterval == 20 && c.jobs == 1000 && c.warmup == 500 && c.machines == 10
                && c.dueFactor == 1.5, "Original completed E controls changed");
        Path root = options.source.resolve("training");
        Properties training = EFiles.read(root.resolve("manifest.properties"));
        require("COMPLETE".equals(training.getProperty("status")), "Original training is incomplete");
        String family = ETraining.fingerprint(c);
        require(family.equals(EFiles.required(training, "family-hash")), "Original E scientific/runtime fingerprint differs");
        ParameterDatabase old = new ParameterDatabase(options.legacy.toFile());
        require(old.getBoolean(new Parameter("using-manual-rule-normalisation"), null, false)
                && !old.getBoolean(new Parameter("map-update-immediate"), null, true)
                && !old.getBoolean(new Parameter("reevaluate-grid-map"), null, true)
                && !old.getBoolean(new Parameter("parents-only-from-GridMap"), null, true)
                && old.getBoolean(new Parameter("parents-only-from-pop"), null, false)
                && old.getDoubleWithDefault(new Parameter("penalty-level"), null, Double.NaN) == 0,
                "Historical GPf accumulation controls do not match this protocol");
        require(old.getIntWithDefault(new Parameter("number-accept"), null, 1) == 1, "Requires cell capacity one");
        for (int run = 0; run < 30; run++) {
            Path source = sourceCase(options, run);
            EFiles.checkHash(source.resolve("manifest.properties"),
                    EFiles.required(training, item(run).key() + ".manifest-sha256"));
            Properties marker = EFiles.read(source.resolve("manifest.properties"));
            ETraining.validateCase(c, item(run), family, marker);
            EFiles.verifyArtifacts(source, marker);
        }
        Properties p = new Properties();
        p.setProperty("protocol", PROTOCOL); p.setProperty("mode", options.mode);
        p.setProperty("source-family", family); p.setProperty("source-root", options.source.toString());
        p.setProperty("source-root-manifest-sha256", EFiles.hash(root.resolve("manifest.properties")));
        p.setProperty("legacy-params-sha256", EFiles.hash(options.legacy));
        p.setProperty("runs", Integer.toString(options.runs)); p.setProperty("generations", Integer.toString(options.generations));
        p.setProperty("map", "native D4/C3/capacity1/penalty0/all-generations/sorted-copy/strict-improvement");
        p.setProperty("reference", "native SchedulingSet.lowerBoundsFromBenchmarkRule after post-evaluation reset");
        return EFiles.fingerprint(p, ECumulativeReplay.class, ECumulativeMap.class,
                EProcessPool.class, EWorkerLease.class, yimei.jss.jobshop.SchedulingSet.class,
                yimei.jss.helper.PopulationUtils.class, yimei.jss.niching.phenotypicForSurrogateV1.class);
    }

    static void run(Options options) throws IOException {
        String identity = identity(options);
        System.out.println("CHECKED: original E family, all 30 source cases, native historical controls. " + options.mode
                + ": " + options.runs + " runs x " + options.generations + " generations x 500 fitness evaluations.");
        Path sourceTree = options.study.populationParams;
        while (sourceTree != null && !sourceTree.getFileName().toString().equals("src")) { sourceTree = sourceTree.getParent(); }
        require(sourceTree != null, "Cannot identify protected source tree");
        Path root = Files.exists(options.output) ? options.output.toRealPath()
                : UniformFiles.checkedOutput(options.output, sourceTree);
        require(!root.startsWith(options.source) && !options.source.startsWith(root),
                "Replay output must be separate and non-nested with original results");
        for (String name : new String[]{"src", "bin", "out", ".git"}) {
            require(!root.startsWith(sourceTree.getParent().resolve(name)), "Output enters protected project directory");
        }
        Path existingMarker = root.resolve("manifest.properties");
        if (Files.exists(root)) {
            require(Files.isRegularFile(existingMarker)
                    && PROTOCOL.equals(EFiles.read(existingMarker).getProperty("protocol")),
                    "Refusing an existing directory not owned by this replay protocol");
        }
        require(!options.summarise || Files.isRegularFile(existingMarker), "No completed replay exists to summarise");
        if (options.preflight) {
            System.out.println("PREFLIGHT PASS; no evolution, reference simulations, or output writes.");
            return;
        }
        Files.createDirectories(root);
        try (FileChannel channel = FileChannel.open(root.resolve(".cumulative.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = channel.tryLock()) {
            require(lock != null, "Another cumulative coordinator owns this output root");
            Path markerPath = root.resolve("manifest.properties");
            Properties marker;
            if (Files.exists(markerPath)) {
                require(options.resume, "Existing root requires --resume");
                marker = EFiles.read(markerPath);
                require(identity.equals(marker.getProperty("identity")), "Replay controls/source/runtime changed; use a fresh root");
            } else {
                require(!options.summarise, "No completed replay exists to summarise");
                marker = new Properties(); marker.setProperty("protocol", PROTOCOL);
                marker.setProperty("identity", identity); marker.setProperty("mode", options.mode);
                marker.setProperty("status", "RUNNING");
                EFiles.write(markerPath, marker, false);
            }
            List<EProcessPool.Work> pending = new ArrayList<>();
            for (int run = 0; run < options.runs; run++) {
                String mapped = marker.getProperty("run." + run + ".path");
                if (mapped != null) {
                    Path attempt = EFiles.mapped(root, mapped);
                    if (Files.isRegularFile(attempt.resolve("manifest.properties"))) {
                        Properties saved = EFiles.read(attempt.resolve("manifest.properties"));
                        if ("COMPLETE".equals(saved.getProperty("status"))) {
                            validateCompleted(options, identity, run, attempt);
                            System.out.println("SKIP verified completed replay run " + run);
                            continue;
                        }
                    }
                }
                pending.add(new EProcessPool.Work(Integer.toString(run), "Lexicase-GPf-T085"));
            }
            require(!options.summarise || pending.isEmpty(), "Cannot summarise incomplete replay");
            if (pending.isEmpty() && marker.getProperty("report.path") != null) {
                Path report = EFiles.mapped(root, marker.getProperty("report.path"));
                EFiles.checkHash(report.resolve("manifest.properties"), EFiles.required(marker, "report.manifest-sha256"));
                EFiles.verifyArtifacts(report, EFiles.read(report.resolve("manifest.properties")));
                System.out.println("SKIP: all cases and report verified; no simulations or result rewrites.");
                return;
            }
            marker.setProperty("status", "RUNNING"); EFiles.write(markerPath, marker, true);
            Path execution = fresh(root, "execution");
            try {
                EProcessPool.run(pending, options.parallel, execution, (work, ordinal) -> {
                    int run = Integer.parseInt(work.key);
                    Path attempt = fresh(root, String.format(Locale.ROOT, "run-%03d", run));
                    marker.setProperty("run." + run + ".path", attempt.getFileName().toString());
                    EFiles.write(markerPath, marker, true);
                    Properties request = new Properties();
                    request.setProperty("protocol", PROTOCOL);
                    request.setProperty("configuration", options.config.toString());
                    request.setProperty("mode", options.mode); request.setProperty("identity", identity);
                    request.setProperty("attempt", attempt.toString()); request.setProperty("run", work.key);
                    request.setProperty("output-root", root.toString());
                    Path file = execution.resolve("request-" + ordinal + ".properties");
                    EFiles.write(file, request, false);
                    return new EProcessPool.Job(work, ECumulativeReplay.class.getName(), file,
                            execution.resolve("worker-" + ordinal + ".log"));
                }, result -> {
                    require(result.exitCode == 0, "Cumulative worker failed: " + result.job.consoleLog);
                    int run = Integer.parseInt(result.job.work.key);
                    Path attempt = EFiles.mapped(root, marker.getProperty("run." + run + ".path"));
                    validateCompleted(options, identity, run, attempt);
                    marker.setProperty("run." + run + ".manifest-sha256", EFiles.hash(attempt.resolve("manifest.properties")));
                    EFiles.write(markerPath, marker, true);
                });
                require(identity.equals(identity(options)), "Original inputs or implementation changed during replay");
                Path report = summarise(options, root, marker, identity);
                marker.setProperty("report.path", report.getFileName().toString());
                marker.setProperty("report.manifest-sha256", EFiles.hash(report.resolve("manifest.properties")));
                marker.setProperty("status", "COMPLETE");
                EFiles.write(markerPath, marker, true);
                System.out.println("COMPLETE " + options.mode + ": " + report);
            } catch (IOException | RuntimeException failure) {
                marker.setProperty("status", "FAILED"); marker.setProperty("failure", failure.toString());
                try { EFiles.write(markerPath, marker, true); } catch (IOException reporting) { failure.addSuppressed(reporting); }
                throw failure;
            }
        }
    }

    static Path fresh(Path root, String prefix) throws IOException {
        for (int n = 1; n < 10000; n++) {
            Path path = root.resolve(prefix + "-attempt-" + String.format(Locale.ROOT, "%04d", n));
            if (!Files.exists(path)) { return Files.createDirectory(path); }
        }
        throw new IOException("Attempt namespace exhausted");
    }

    static void worker(Properties request) throws IOException {
        require(PROTOCOL.equals(request.getProperty("protocol")), "Unrecognised worker request");
        List<String> args = new ArrayList<>(Arrays.asList("--config", EFiles.required(request, "configuration")));
        String mode = EFiles.required(request, "mode");
        if (mode.equals("SMOKE")) { args.add("--smoke"); }
        else if (mode.equals("PARITY_CHECK")) { args.add("--verify-run-zero"); }
        else { require(mode.equals("FORMAL"), "Invalid worker mode"); }
        Options options = Options.parse(args.toArray(new String[0]));
        String identity = identity(options);
        require(identity.equals(request.getProperty("identity")), "Worker identity changed after dispatch");
        int run = Integer.parseInt(EFiles.required(request, "run"));
        require(run >= 0 && run < options.runs, "Worker run outside requested cohort");
        Path root = Path.of(EFiles.required(request, "output-root")).toRealPath();
        Path attempt = Path.of(EFiles.required(request, "attempt")).toRealPath();
        Properties rootMarker = EFiles.read(root.resolve("manifest.properties"));
        require(root.equals(options.output.toRealPath()) && attempt.getParent().equals(root)
                && attempt.getFileName().toString().equals(rootMarker.getProperty("run." + run + ".path"))
                && identity.equals(rootMarker.getProperty("identity")), "Worker does not own this mapping");
        Path source = sourceCase(options, run);
        String sourceManifestHash = EFiles.hash(source.resolve("manifest.properties"));
        Properties original = EFiles.read(source.resolve("manifest.properties"));
        Properties marker = new Properties();
        for (String key : new String[]{"protocol", "family-hash", "case-hash", "case-key", "method", "regime",
                "scenario-id", "run-id", "objective", "utilisation"}) {
            marker.setProperty(key, EFiles.required(original, key));
        }
        marker.setProperty("status", "RUNNING");
        EFiles.write(attempt.resolve("manifest.properties"), marker, false);
        EvolutionState state = null;
        PrintStream console = System.out, errors = System.err;
        try {
            try (PrintStream log = new PrintStream(Files.newOutputStream(attempt.resolve("training.log"),
                    StandardOpenOption.CREATE_NEW), true, StandardCharsets.UTF_8.name())) {
                System.setOut(log); System.setErr(log);
                try {
                    ParameterDatabase parameters = ETraining.parameters(options.study, item(run), attempt,
                            original.getProperty("family-hash"), original.getProperty("case-hash"));
                    parameters.set(new Parameter("generations"), Integer.toString(options.generations));
                    parameters.set(new Parameter("lexqd.snapshot-interval"), "1");
                    parameters.set(new Parameter("e.snapshot-interval"), "1");
                    parameters.set(new Parameter("stat.num-children"), "1");
                    parameters.set(new Parameter("stat.child.0"), Observer.class.getName());
                    parameters.set(new Parameter("cumulative.source"), source.toString());
                    parameters.set(new Parameter("cumulative.configuration"), options.config.toString());
                    state = Evolve.initialize(parameters, 0);
                    require(state instanceof EPopulationState, "Expected unchanged E population adapter");
                    ((EPopulationState) state).setProgress(console);
                    state.run(EvolutionState.C_STARTED_FRESH);
                } finally {
                    try {
                        if (state != null && state.statistics.children != null) {
                            for (Statistics child : state.statistics.children) {
                                if (child instanceof Observer) { ((Observer) child).close(); }
                            }
                        }
                    } finally {
                        try { if (state instanceof EManagedState) { ((EManagedState) state).closeResources(); } }
                        finally { if (state != null) { Evolve.cleanup(state); } }
                    }
                }
                if (log.checkError()) { throw new IOException("Replay log write failed"); }
            } finally { System.setOut(console); System.setErr(errors); }
            marker = EFiles.read(attempt.resolve("manifest.properties"));
            require("SEARCH_COMPLETE".equals(marker.getProperty("status")), "Replay did not finish");
            require(Files.readAllLines(attempt.resolve("cumulative_generations.csv")).size() == options.generations + 1,
                    "Missing all-generation cumulative records");
            EFiles.verifyArtifacts(source, original);
            EFiles.checkHash(source.resolve("manifest.properties"), sourceManifestHash);
            marker.setProperty("replay-identity", identity); marker.setProperty("replay-mode", options.mode);
            marker.setProperty("source-manifest-sha256", sourceManifestHash);
            marker.setProperty("source-case-path", source.toString());
            marker.setProperty("accumulation", "all 500 members at every generation; capacity-one native cell elites");
            marker.setProperty("archive-feedback", "none; Lexicase population selection unchanged");
            marker.setProperty("descriptor-reference-simulations", "2");
            marker.setProperty("manual-reference-simulations", Integer.toString(options.generations));
            marker.setProperty("replay-fitness-evaluations", Integer.toString(options.generations * 500));
            marker.setProperty("total-replay-simulations", Integer.toString(options.generations * 501 + 2));
            marker.setProperty("total-training-simulations", Integer.toString(options.generations * 501 + 2));
            marker.setProperty("original-evaluator-counter-policy", "generations.csv preserves unchanged E evaluator counters; observer reference work is additional and in cumulative_generations.csv");
            try (var files = Files.list(attempt)) {
                for (Path path : files.filter(Files::isRegularFile).toList()) {
                    if (!path.getFileName().toString().equals("manifest.properties")) {
                        marker.setProperty("data." + path.getFileName() + ".sha256", EFiles.hash(path));
                    }
                }
            }
            marker.setProperty("status", "COMPLETE"); EFiles.write(attempt.resolve("manifest.properties"), marker, true);
            System.out.println("REPLAY COMPLETE run=" + run + " mode=" + options.mode);
        } catch (IOException | RuntimeException | Error failure) {
            EAdapterSupport.failed(attempt, failure);
            throw failure;
        }
    }

    static void validateCompleted(Options options, String identity, int run, Path attempt) throws IOException {
        Properties saved = EFiles.read(attempt.resolve("manifest.properties"));
        require("COMPLETE".equals(saved.getProperty("status")) && identity.equals(saved.getProperty("replay-identity"))
                && options.mode.equals(saved.getProperty("replay-mode"))
                && Integer.toString(options.generations * 500).equals(saved.getProperty("replay-fitness-evaluations"))
                && EFiles.hash(sourceCase(options, run).resolve("manifest.properties"))
                        .equals(saved.getProperty("source-manifest-sha256")), "Incompatible completed replay: " + attempt);
        EFiles.verifyArtifacts(attempt, saved);
    }

    public static final class Observer extends Statistics {
        private BufferedWriter writer;
        private Path source, output;
        private ECumulativeMap map;
        private List<List<String>> originalGenerations;
        private List<String> generationHeader;

        @Override public void preInitializationStatistics(EvolutionState raw) {
            super.preInitializationStatistics(raw);
            EPopulationState state = (EPopulationState) raw;
            try {
                source = Path.of(EAdapterSupport.text(state, "cumulative.source")).toRealPath();
                output = state.outputRoot();
                Options options = Options.parse(new String[]{"--config", EAdapterSupport.text(state, "cumulative.configuration")});
                map = new ECumulativeMap(options.study, state.model().trainingSeed(), state.parameters);
                List<String> lines = Files.readAllLines(source.resolve("generations.csv"));
                generationHeader = ECheckpointAnalysis.csvLine(lines.get(0));
                originalGenerations = new ArrayList<>();
                for (String line : lines.subList(1, lines.size())) { originalGenerations.add(ECheckpointAnalysis.csvLine(line)); }
                writer = EFiles.create(output.resolve("cumulative_generations.csv"));
                EFiles.row(writer, "run_id", "generation", "coverage_pct", "mean_normalised", "best_normalised",
                        "occupied_cells", "finite_cells", "failed_cells", "normaliser", "population_members_seen",
                        "original_snapshot_match", "evaluated_population_sha256", "benchmark_simulations",
                        "descriptor_reference_simulations", "total_replay_simulations");
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        @Override public void postEvaluationStatistics(EvolutionState raw) {
            super.postEvaluationStatistics(raw);
            EPopulationState state = (EPopulationState) raw;
            Individual[] members = state.population.subpops[0].individuals;
            try {
                require(members.length == 500, "Incomplete replay population");
                int finite = 0; double mean = 0, best = Double.POSITIVE_INFINITY;
                for (Individual member : members) {
                    if (((LexicaseFitness) member.fitness).isValidEvaluation()) {
                        finite++; double value = member.fitness.fitness();
                        mean += (value - mean) / finite; best = Math.min(best, value);
                    }
                }
                List<String> old = originalGenerations.get(state.generation);
                equal(old, "generation", state.generation); equal(old, "training_seed", state.model().trainingSeed());
                equal(old, "finite_members", finite); equal(old, "failed_members", 500 - finite);
                equal(old, "best_raw_objective", best); equal(old, "finite_mean_raw_objective", mean);
                equal(old, "fitness_evaluations", state.model().simulationCount());
                if (state.generation % 20 == 0) { verifySnapshot(source, state.generation, members); }
                String before = EPopulationStatistics.evaluatedPopulationHash(members);
                double[] values = map.update(state.generation, members);
                require(before.equals(EPopulationStatistics.evaluatedPopulationHash(members)), "Map observer changed population values");
                EFiles.row(writer, state.runId(), state.generation, values[0], values[1], values[2],
                        (int) values[3], (int) values[4], (int) values[5], values[6], map.membersSeen,
                        state.generation % 20 == 0 ? "EXACT" : "NOT_SAVED_ORIGINALLY", before,
                        map.referenceSimulations, 2, state.model().simulationCount() + map.referenceSimulations + 2);
                writer.flush();
                if (state.generation % 20 == 0 || state.generation == state.numGenerations - 1) {
                    map.save(output.resolve(String.format(Locale.ROOT, "archive.gen%04d.csv", state.generation)));
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        private void equal(List<String> row, String name, double value) throws IOException {
            int index = generationHeader.indexOf(name);
            require(index >= 0 && Double.compare(Double.parseDouble(row.get(index)), value) == 0,
                    "Replay differs from original generation aggregate: " + name);
        }

        void close() throws IOException { if (writer != null) { BufferedWriter old = writer; writer = null; old.close(); } }
        @Override public void finalStatistics(EvolutionState raw, int result) {
            super.finalStatistics(raw, result);
            try { close(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }

    static void verifySnapshot(Path source, int generation, Individual[] population) throws IOException {
        Path path = source.resolve(String.format(Locale.ROOT, "population.gen%04d.csv", generation));
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<String> header = ECheckpointAnalysis.csvLine(reader.readLine());
            for (int i = 0; i < population.length; i++) {
                String line = reader.readLine(); require(line != null, "Original snapshot ended early");
                List<String> row = ECheckpointAnalysis.csvLine(line);
                GPIndividual member = (GPIndividual) population[i];
                LexicaseFitness fitness = (LexicaseFitness) member.fitness;
                require(Integer.parseInt(row.get(header.indexOf("population_index"))) == i
                        && Double.compare(Double.parseDouble(row.get(header.indexOf("raw_objective"))), fitness.fitness()) == 0
                        && Boolean.parseBoolean(row.get(header.indexOf("valid"))) == fitness.isValidEvaluation()
                        && row.get(header.indexOf("sequencing")).equals(member.trees[0].child.makeLispTree())
                        && row.get(header.indexOf("routing")).equals(member.trees[1].child.makeLispTree()),
                        "Replay genotype/objective/order differs at generation " + generation + ", member " + i);
                for (int j = 0; j < 25; j++) {
                    require(Double.compare(Double.parseDouble(row.get(header.indexOf("case_" + j))),
                            fitness.multiInstanceMultiObjectiveFitness[j][0]) == 0, "Replay case-vector mismatch");
                }
            }
            require(reader.readLine() == null, "Original snapshot has additional members");
        }
    }

    static Path summarise(Options options, Path root, Properties marker, String identity) throws IOException {
        Path report = fresh(root, "report");
        ECheckpointAnalysis.Moments[][] groups = new ECheckpointAnalysis.Moments[options.generations][3];
        for (int g = 0; g < groups.length; g++) for (int m = 0; m < 3; m++) {
            groups[g][m] = new ECheckpointAnalysis.Moments();
        }
        try (BufferedWriter all = EFiles.create(report.resolve("all_runs.csv"))) {
            for (int run = 0; run < options.runs; run++) {
                Path attempt = EFiles.mapped(root, EFiles.required(marker, "run." + run + ".path"));
                validateCompleted(options, identity, run, attempt);
                List<String> lines = Files.readAllLines(attempt.resolve("cumulative_generations.csv"));
                require(lines.size() == options.generations + 1, "Missing cumulative generations");
                if (run == 0) { all.write(lines.get(0)); all.newLine(); }
                for (int g = 0; g < options.generations; g++) {
                    List<String> row = ECheckpointAnalysis.csvLine(lines.get(g + 1));
                    require(Integer.parseInt(row.get(0)) == run && Integer.parseInt(row.get(1)) == g,
                            "Cumulative generation order differs");
                    for (int m = 0; m < 3; m++) { groups[g][m].add(Double.parseDouble(row.get(m + 2))); }
                    all.write(lines.get(g + 1)); all.newLine();
                }
            }
        }
        try (BufferedWriter table = EFiles.create(report.resolve("table_fragments.csv"))) {
            EFiles.row(table, "generation", "method", "runs", "coverage_pct_mean", "coverage_pct_sample_sd",
                    "mean_normalised_mean", "mean_normalised_sample_sd", "best_normalised_mean", "best_normalised_sample_sd");
            for (int g = 20; g < options.generations; g += 20) {
                EFiles.row(table, g, "Lexicase-GPf", options.runs, groups[g][0].mean(), groups[g][0].sd(),
                        groups[g][1].mean(), groups[g][1].sd(), groups[g][2].mean(), groups[g][2].sd());
            }
        }
        Properties p = new Properties(); p.setProperty("protocol", PROTOCOL); p.setProperty("identity", identity);
        p.setProperty("mode", options.mode); p.setProperty("publication-ready", Boolean.toString(options.mode.equals("FORMAL")));
        p.setProperty("runs", Integer.toString(options.runs)); p.setProperty("generations", Integer.toString(options.generations));
        p.setProperty("fitness-evaluations", Integer.toString(options.runs * options.generations * 500));
        p.setProperty("benchmark-simulations", Integer.toString(options.runs * options.generations));
        p.setProperty("descriptor-reference-simulations", Integer.toString(options.runs * 2));
        p.setProperty("metric-policy", "Native cumulative capacity-one map: occupied/6561, finite retained-cell mean, native minimum; run mean/sample SD");
        p.setProperty("comparison-limit", "Map/metric definitions align with saved historical source; original training cohorts/binary provenance remain different");
        p.setProperty("status", "COMPLETE");
        p.setProperty("data.all_runs.csv.sha256", EFiles.hash(report.resolve("all_runs.csv")));
        p.setProperty("data.table_fragments.csv.sha256", EFiles.hash(report.resolve("table_fragments.csv")));
        EFiles.write(report.resolve("manifest.properties"), p, false);
        return report;
    }
}
