package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.Statistics;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
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
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseFitness;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.UniformFiles;

import static mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD.ECumulativeReplay.require;
import static mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD.ECumulativeReplay.fresh;

public final class ERotatingCumulativeReplay {
    static final String PROTOCOL = "lexgp-rotating-native-cumulative-v1";
    private ERotatingCumulativeReplay() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || Arrays.equals(args, new String[]{"--help"})) {
            System.out.println("ERotatingCumulativeReplay --config FILE [--preflight|--smoke|--verify-run-zero|--summarise] [--resume]");
            return;
        }
        if (args.length == 2 && args[0].equals("--request")) {
            try (EWorkerLease lease = EWorkerLease.start()) { worker(EFiles.read(Path.of(args[1]))); }
            return;
        }
        run(Options.parse(args));
    }

    static final class Options {
        Path config, source, output, legacy;
        EConfiguration study;
        EParallelOptions parallel;
        boolean resume, preflight, summarise;
        int runs = 30, generations = 201;
        String mode = "FORMAL";

        static Options parse(String[] args) throws IOException {
            Options result = new Options(); int modes = 0;
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
                    "output-root", "smoke-output-root", "parity-output-root", "legacy-gp-params", "workers", "worker-heap")),
                    "Unexpected rotating cumulative configuration keys");
            result.source = ECumulativeReplay.Options.resolve(result.config, EFiles.required(p, "source-root")).toRealPath();
            result.legacy = ECumulativeReplay.Options.resolve(result.config, EFiles.required(p, "legacy-gp-params")).toRealPath();
            String key = result.mode.equals("SMOKE") ? "smoke-output-root"
                    : result.mode.equals("PARITY_CHECK") ? "parity-output-root" : "output-root";
            result.output = ECumulativeReplay.Options.resolve(result.config, EFiles.required(p, key)).toAbsolutePath().normalize();
            result.parallel = EParallelOptions.parse(new String[]{"--config",
                    result.source.resolve("training").resolve("resolved-study.properties").toRealPath().toString(),
                    "--workers", EFiles.required(p, "workers"), "--worker-heap", EFiles.required(p, "worker-heap")});
            result.study = result.parallel.config;
            return result;
        }
    }

    static EConfiguration.Case item(int run) {
        return new EConfiguration.Case(EConfiguration.Method.LEXICASE, EConfiguration.Regime.rotating,
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
                && c.dueFactor == 1.5 && c.seedStep == 10000, "Original E controls changed");
        Path trainingRoot = options.source.resolve("training");
        Properties training = EFiles.read(trainingRoot.resolve("manifest.properties"));
        require("COMPLETE".equals(training.getProperty("status")), "Original training is incomplete");
        String family = ETraining.fingerprint(c);
        require(family.equals(EFiles.required(training, "family-hash")), "Original E scientific/runtime identity changed");
        ParameterDatabase old = new ParameterDatabase(options.legacy.toFile());
        require(old.getBoolean(new Parameter("eval.problem.eval-model.rotate-sim-seed"), null, false)
                && old.getBoolean(new Parameter("using-manual-rule-normalisation"), null, false)
                && !old.getBoolean(new Parameter("map-update-immediate"), null, true)
                && !old.getBoolean(new Parameter("reevaluate-grid-map"), null, true)
                && !old.getBoolean(new Parameter("parents-only-from-GridMap"), null, true)
                && old.getBoolean(new Parameter("parents-only-from-pop"), null, false)
                && old.getDoubleWithDefault(new Parameter("penalty-level"), null, Double.NaN) == ERotatingCumulativeMap.AGE_PENALTY
                && old.getIntWithDefault(new Parameter("number-accept"), null, 1) == 1,
                "Historical rotating GP map controls differ: require native age5/capacity1/no reevaluation");
        for (int run = 0; run < 30; run++) {
            Path source = sourceCase(options, run);
            EFiles.checkHash(source.resolve("manifest.properties"),
                    EFiles.required(training, item(run).key() + ".manifest-sha256"));
            Properties marker = EFiles.read(source.resolve("manifest.properties"));
            ETraining.validateCase(c, item(run), family, marker);
            require("rotating".equals(marker.getProperty("regime"))
                    && Long.toString(c.trainingSeed(item(run)) + 200 * c.seedStep)
                            .equals(marker.getProperty("final-training-seed")), "Original rotating seed schedule differs");
            EFiles.verifyArtifacts(source, marker);
        }
        Properties p = new Properties();
        p.setProperty("protocol", PROTOCOL); p.setProperty("mode", options.mode);
        p.setProperty("source-family", family); p.setProperty("source-root", options.source.toString());
        p.setProperty("source-manifest-sha256", EFiles.hash(trainingRoot.resolve("manifest.properties")));
        p.setProperty("legacy-params-sha256", EFiles.hash(options.legacy));
        p.setProperty("runs", Integer.toString(options.runs)); p.setProperty("generations", Integer.toString(options.generations));
        p.setProperty("map", "native D4/C3/capacity1/age5/no-reevaluation/all-generations/sorted-copy");
        p.setProperty("reference", "native current-job-seed post-evaluation reset/setters/rerun; fixed initial shop");
        p.setProperty("best", "native age-selected member stored score; not minimum stored score");
        return EFiles.fingerprint(p, ERotatingCumulativeReplay.class, ERotatingCumulativeMap.class,
                ECumulativeReplay.class, ECumulativeMap.class, EProcessPool.class, EWorkerLease.class,
                yimei.jss.jobshop.SchedulingSet.class, yimei.jss.helper.PopulationUtils.class,
                yimei.jss.niching.phenotypicForSurrogateV1.class);
    }

    static Path checkedRoot(Options options) throws IOException {
        Path sourceTree = options.study.populationParams;
        while (sourceTree != null && !sourceTree.getFileName().toString().equals("src")) { sourceTree = sourceTree.getParent(); }
        require(sourceTree != null, "Cannot identify protected source tree");
        Path root = Files.exists(options.output) ? options.output.toRealPath()
                : UniformFiles.checkedOutput(options.output, sourceTree);
        require(!root.startsWith(options.source) && !options.source.startsWith(root),
                "Replay output must be separate and non-nested with original E inputs");
        for (String name : new String[]{"src", "bin", "out", ".git"}) {
            require(!root.startsWith(sourceTree.getParent().resolve(name)), "Output enters protected project directory");
        }
        Path marker = root.resolve("manifest.properties");
        if (Files.exists(root)) {
            require(Files.isRegularFile(marker) && PROTOCOL.equals(EFiles.read(marker).getProperty("protocol")),
                    "Existing directory is not owned by this rotating replay; never reuse fixed outputs");
        }
        require(!options.summarise || Files.isRegularFile(marker), "No completed rotating replay exists to summarise");
        return root;
    }

    static void run(Options options) throws IOException {
        String identity = identity(options);
        Path root = checkedRoot(options);
        System.out.println("CHECKED rotating LexGP " + options.mode + ": " + options.runs + " runs x "
                + options.generations + " generations x 500; source E family unchanged, native age=5.");
        if (options.preflight) {
            System.out.println("PREFLIGHT PASS; no simulations or output writes.");
            return;
        }
        Files.createDirectories(root);
        try (FileChannel channel = FileChannel.open(root.resolve(".cumulative.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = channel.tryLock()) {
            require(lock != null, "Another rotating replay coordinator owns this root");
            Path markerPath = root.resolve("manifest.properties");
            Properties marker;
            if (Files.exists(markerPath)) {
                require(options.resume, "Existing root requires --resume");
                marker = EFiles.read(markerPath);
                require(identity.equals(marker.getProperty("identity")), "Replay/source/runtime changed; choose a fresh root");
            } else {
                marker = new Properties(); marker.setProperty("protocol", PROTOCOL);
                marker.setProperty("mode", options.mode); marker.setProperty("identity", identity);
                marker.setProperty("status", "RUNNING"); EFiles.write(markerPath, marker, false);
            }
            List<EProcessPool.Work> pending = new ArrayList<>();
            for (int run = 0; run < options.runs; run++) {
                String name = marker.getProperty("run." + run + ".path");
                if (name != null) {
                    Path attempt = EFiles.mapped(root, name);
                    if (Files.isRegularFile(attempt.resolve("manifest.properties"))
                            && "COMPLETE".equals(EFiles.read(attempt.resolve("manifest.properties")).getProperty("status"))) {
                        validateCompleted(options, identity, run, attempt);
                        System.out.println("SKIP verified rotating replay run " + run);
                        continue;
                    }
                }
                pending.add(new EProcessPool.Work(Integer.toString(run), "LexGP-rotating-T085"));
            }
            require(!options.summarise || pending.isEmpty(), "Cannot summarise incomplete rotating replay");
            if (pending.isEmpty() && marker.getProperty("report.path") != null) {
                Path report = EFiles.mapped(root, EFiles.required(marker, "report.path"));
                EFiles.checkHash(report.resolve("manifest.properties"), EFiles.required(marker, "report.manifest-sha256"));
                EFiles.verifyArtifacts(report, EFiles.read(report.resolve("manifest.properties")));
                System.out.println("SKIP: every case/report verified; no workers, simulations, or result rewrites.");
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
                    request.setProperty("protocol", PROTOCOL); request.setProperty("configuration", options.config.toString());
                    request.setProperty("mode", options.mode); request.setProperty("identity", identity);
                    request.setProperty("run", work.key); request.setProperty("attempt", attempt.toString());
                    request.setProperty("output-root", root.toString());
                    Path file = execution.resolve("request-" + ordinal + ".properties");
                    EFiles.write(file, request, false);
                    return new EProcessPool.Job(work, ERotatingCumulativeReplay.class.getName(), file,
                            execution.resolve("worker-" + ordinal + ".log"));
                }, result -> {
                    require(result.exitCode == 0, "Rotating worker failed: " + result.job.consoleLog);
                    int run = Integer.parseInt(result.job.work.key);
                    Path attempt = EFiles.mapped(root, marker.getProperty("run." + run + ".path"));
                    validateCompleted(options, identity, run, attempt);
                    marker.setProperty("run." + run + ".manifest-sha256", EFiles.hash(attempt.resolve("manifest.properties")));
                    EFiles.write(markerPath, marker, true);
                });
                require(identity.equals(identity(options)), "Inputs or implementation changed during replay");
                Path report = summarise(options, root, marker, identity);
                marker.setProperty("report.path", report.getFileName().toString());
                marker.setProperty("report.manifest-sha256", EFiles.hash(report.resolve("manifest.properties")));
                marker.setProperty("status", "COMPLETE"); EFiles.write(markerPath, marker, true);
                System.out.println("COMPLETE " + options.mode + ": " + report);
            } catch (IOException | RuntimeException failure) {
                marker.setProperty("status", "FAILED"); marker.setProperty("failure", failure.toString());
                try { EFiles.write(markerPath, marker, true); } catch (IOException reporting) { failure.addSuppressed(reporting); }
                throw failure;
            }
        }
    }

    static void worker(Properties request) throws IOException {
        require(PROTOCOL.equals(request.getProperty("protocol")), "Unrecognised rotating worker request");
        List<String> arguments = new ArrayList<>(Arrays.asList("--config", EFiles.required(request, "configuration")));
        String mode = EFiles.required(request, "mode");
        if (mode.equals("SMOKE")) { arguments.add("--smoke"); }
        else if (mode.equals("PARITY_CHECK")) { arguments.add("--verify-run-zero"); }
        else { require(mode.equals("FORMAL"), "Invalid worker mode"); }
        Options options = Options.parse(arguments.toArray(new String[0]));
        String identity = identity(options);
        require(identity.equals(request.getProperty("identity")), "Worker identity changed after dispatch");
        int run = Integer.parseInt(EFiles.required(request, "run"));
        require(run >= 0 && run < options.runs, "Worker run outside requested cohort");
        Path root = Path.of(EFiles.required(request, "output-root")).toRealPath();
        Path attempt = Path.of(EFiles.required(request, "attempt")).toRealPath();
        Properties owner = EFiles.read(root.resolve("manifest.properties"));
        require(root.equals(options.output.toRealPath()) && attempt.getParent().equals(root)
                && identity.equals(owner.getProperty("identity"))
                && attempt.getFileName().toString().equals(owner.getProperty("run." + run + ".path")),
                "Worker does not own this rotating attempt");
        Path source = sourceCase(options, run);
        String sourceHash = EFiles.hash(source.resolve("manifest.properties"));
        Properties original = EFiles.read(source.resolve("manifest.properties"));
        Properties marker = new Properties();
        for (String key : new String[]{"protocol", "family-hash", "case-hash", "case-key", "method", "regime",
                "scenario-id", "run-id", "objective", "utilisation"}) {
            marker.setProperty(key, EFiles.required(original, key));
        }
        marker.setProperty("status", "RUNNING"); EFiles.write(attempt.resolve("manifest.properties"), marker, false);
        EvolutionState state = null; PrintStream console = System.out, errors = System.err;
        try {
            try (PrintStream log = new PrintStream(Files.newOutputStream(attempt.resolve("training.log"),
                    StandardOpenOption.CREATE_NEW), true, StandardCharsets.UTF_8.name())) {
                System.setOut(log); System.setErr(log);
                try {
                    ParameterDatabase p = ETraining.parameters(options.study, item(run), attempt,
                            original.getProperty("family-hash"), original.getProperty("case-hash"));
                    p.set(new Parameter("generations"), Integer.toString(options.generations));
                    p.set(new Parameter("lexqd.snapshot-interval"), "1"); p.set(new Parameter("e.snapshot-interval"), "1");
                    p.set(new Parameter("stat.num-children"), "1"); p.set(new Parameter("stat.child.0"), Observer.class.getName());
                    p.set(new Parameter("rotating-cumulative.source"), source.toString());
                    p.set(new Parameter("rotating-cumulative.configuration"), options.config.toString());
                    state = Evolve.initialize(p, 0);
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
                if (log.checkError()) { throw new IOException("Rotating replay log write failed"); }
            } finally { System.setOut(console); System.setErr(errors); }
            marker = EFiles.read(attempt.resolve("manifest.properties"));
            require("SEARCH_COMPLETE".equals(marker.getProperty("status"))
                    && Files.readAllLines(attempt.resolve("cumulative_generations.csv")).size() == options.generations + 1,
                    "Rotating replay did not finish its complete generation budget");
            EFiles.verifyArtifacts(source, original); EFiles.checkHash(source.resolve("manifest.properties"), sourceHash);
            marker.setProperty("replay-protocol", PROTOCOL); marker.setProperty("replay-identity", identity);
            marker.setProperty("replay-mode", options.mode); marker.setProperty("source-manifest-sha256", sourceHash);
            marker.setProperty("source-case-path", source.toString()); marker.setProperty("age-penalty", "5");
            marker.setProperty("age-penalty-scope", "Observational archive only; original LexGP population selection is unchanged");
            marker.setProperty("archive-feedback", "none; unchanged rotating LexGP selection");
            marker.setProperty("archive-reevaluation", "false");
            marker.setProperty("replay-fitness-evaluations", Integer.toString(options.generations * 500));
            marker.setProperty("manual-reference-simulations", Integer.toString(options.generations));
            marker.setProperty("descriptor-reference-simulations", "2");
            marker.setProperty("total-replay-simulations", Integer.toString(options.generations * 501 + 2));
            marker.setProperty("total-training-simulations", Integer.toString(options.generations * 501 + 2));
            marker.setProperty("best-policy", "Stored score of native age-selected member; min_stored_normalised is separate");
            marker.setProperty("counter-policy", "E generations.csv counters exclude observer references; cumulative_generations.csv includes them");
            try (var files = Files.list(attempt)) {
                for (Path path : files.filter(Files::isRegularFile).toList()) {
                    if (!path.getFileName().toString().equals("manifest.properties")) {
                        marker.setProperty("data." + path.getFileName() + ".sha256", EFiles.hash(path));
                    }
                }
            }
            marker.setProperty("status", "COMPLETE"); EFiles.write(attempt.resolve("manifest.properties"), marker, true);
            System.out.println("ROTATING REPLAY COMPLETE run=" + run + " mode=" + mode);
        } catch (IOException | RuntimeException | Error failure) {
            EAdapterSupport.failed(attempt, failure); throw failure;
        }
    }

    static void validateCompleted(Options options, String identity, int run, Path attempt) throws IOException {
        Properties p = EFiles.read(attempt.resolve("manifest.properties"));
        require("COMPLETE".equals(p.getProperty("status")) && PROTOCOL.equals(p.getProperty("replay-protocol"))
                && identity.equals(p.getProperty("replay-identity")) && options.mode.equals(p.getProperty("replay-mode"))
                && "rotating".equals(p.getProperty("regime")) && "5".equals(p.getProperty("age-penalty"))
                && Integer.toString(options.generations * 500).equals(p.getProperty("replay-fitness-evaluations"))
                && EFiles.hash(sourceCase(options, run).resolve("manifest.properties"))
                        .equals(p.getProperty("source-manifest-sha256")), "Incompatible completed rotating replay: " + attempt);
        EFiles.verifyArtifacts(attempt, p);
    }

    public static final class Observer extends Statistics {
        private BufferedWriter writer;
        private ERotatingCumulativeMap map;
        private Path source, output;
        private List<String> header;
        private List<List<String>> original;

        @Override public void preInitializationStatistics(EvolutionState raw) {
            super.preInitializationStatistics(raw);
            EPopulationState state = (EPopulationState) raw;
            try {
                require(state.model().isRotatable(), "Rotating observer refuses fixed training");
                source = Path.of(EAdapterSupport.text(state, "rotating-cumulative.source")).toRealPath();
                output = state.outputRoot();
                Options options = Options.parse(new String[]{"--config",
                        EAdapterSupport.text(state, "rotating-cumulative.configuration")});
                map = new ERotatingCumulativeMap(options.study, state.model().trainingSeed(), state.parameters);
                List<String> lines = Files.readAllLines(source.resolve("generations.csv"));
                header = ECheckpointAnalysis.csvLine(lines.get(0)); original = new ArrayList<>();
                for (String line : lines.subList(1, lines.size())) { original.add(ECheckpointAnalysis.csvLine(line)); }
                writer = EFiles.create(output.resolve("cumulative_generations.csv"));
                EFiles.row(writer, "run_id", "generation", "coverage_pct", "mean_normalised", "best_normalised",
                        "occupied_cells", "finite_cells", "failed_cells", "normaliser", "population_members_seen",
                        "original_snapshot_match", "evaluated_population_sha256", "benchmark_simulations",
                        "descriptor_reference_simulations", "total_replay_simulations", "training_seed",
                        "min_stored_normalised", "age_adjusted_best", "native_best_source_generation");
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        @Override public void postEvaluationStatistics(EvolutionState raw) {
            super.postEvaluationStatistics(raw);
            EPopulationState state = (EPopulationState) raw;
            Individual[] members = state.population.subpops[0].individuals;
            try {
                require(members.length == 500, "Incomplete rotating population");
                int finite = 0; double mean = 0, best = Double.POSITIVE_INFINITY;
                for (Individual member : members) {
                    if (((LexicaseFitness) member.fitness).isValidEvaluation()) {
                        finite++; double value = member.fitness.fitness();
                        mean += (value - mean) / finite; best = Math.min(best, value);
                    }
                }
                List<String> row = original.get(state.generation);
                equal(row, "generation", state.generation); equal(row, "training_seed", state.model().trainingSeed());
                equal(row, "finite_members", finite); equal(row, "failed_members", 500 - finite);
                equal(row, "best_raw_objective", best); equal(row, "finite_mean_raw_objective", mean);
                equal(row, "fitness_evaluations", state.model().simulationCount());
                equal(row, "failed_training_evaluations", state.model().failedCount());
                equal(row, "case_value_slots", Math.multiplyExact(state.model().simulationCount(), 25));
                if (state.generation % 20 == 0) { ECumulativeReplay.verifySnapshot(source, state.generation, members); }
                String before = EPopulationStatistics.evaluatedPopulationHash(members);
                ERotatingCumulativeMap.Metrics m = map.update(state.generation, state.model().trainingSeed(), members);
                require(before.equals(EPopulationStatistics.evaluatedPopulationHash(members)), "Observer changed LexGP population");
                EFiles.row(writer, state.runId(), state.generation, m.coverage(), m.mean(), m.best(), m.occupied(),
                        m.finite(), m.failed(), m.normaliser(), map.membersSeen,
                        state.generation % 20 == 0 ? "EXACT" : "NOT_SAVED_ORIGINALLY", before,
                        map.referenceSimulations, 2, state.model().simulationCount() + map.referenceSimulations + 2,
                        state.model().trainingSeed(), m.minimumStored(), m.bestAged(), m.bestGeneration());
                writer.flush();
                if (state.generation % 20 == 0 || state.generation == state.numGenerations - 1) {
                    map.save(output.resolve(String.format(Locale.ROOT, "archive.gen%04d.csv", state.generation)));
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        private void equal(List<String> row, String name, double value) throws IOException {
            int index = header.indexOf(name);
            require(index >= 0 && Double.compare(Double.parseDouble(row.get(index)), value) == 0,
                    "Original rotating generation aggregate differs: " + name);
        }
        void close() throws IOException { if (writer != null) { BufferedWriter old = writer; writer = null; old.close(); } }
        @Override public void finalStatistics(EvolutionState raw, int result) {
            super.finalStatistics(raw, result);
            try { close(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }

    static Path summarise(Options options, Path root, Properties marker, String identity) throws IOException {
        Path report = fresh(root, "report");
        ECheckpointAnalysis.Moments[][] groups = new ECheckpointAnalysis.Moments[options.generations][4];
        for (int g = 0; g < groups.length; g++) for (int m = 0; m < 4; m++) {
            groups[g][m] = new ECheckpointAnalysis.Moments();
        }
        try (BufferedWriter all = EFiles.create(report.resolve("all_runs.csv"))) {
            for (int run = 0; run < options.runs; run++) {
                Path attempt = EFiles.mapped(root, EFiles.required(marker, "run." + run + ".path"));
                validateCompleted(options, identity, run, attempt);
                List<String> lines = Files.readAllLines(attempt.resolve("cumulative_generations.csv"));
                require(lines.size() == options.generations + 1, "Missing rotating generations");
                if (run == 0) { all.write(lines.get(0)); all.newLine(); }
                for (int g = 0; g < options.generations; g++) {
                    List<String> row = ECheckpointAnalysis.csvLine(lines.get(g + 1));
                    require(Integer.parseInt(row.get(0)) == run && Integer.parseInt(row.get(1)) == g,
                            "Unexpected rotating summary order");
                    for (int m = 0; m < 3; m++) { groups[g][m].add(Double.parseDouble(row.get(m + 2))); }
                    groups[g][3].add(Double.parseDouble(row.get(16)));
                    all.write(lines.get(g + 1)); all.newLine();
                }
            }
        }
        try (BufferedWriter table = EFiles.create(report.resolve("table_fragments.csv"))) {
            EFiles.row(table, "generation", "method", "runs", "coverage_pct_mean", "coverage_pct_sample_sd",
                    "mean_normalised_mean", "mean_normalised_sample_sd", "best_normalised_mean", "best_normalised_sample_sd",
                    "min_stored_normalised_mean", "min_stored_normalised_sample_sd");
            for (int g = 20; g < options.generations; g += 20) {
                EFiles.row(table, g, "LexGP", options.runs, groups[g][0].mean(), groups[g][0].sd(),
                        groups[g][1].mean(), groups[g][1].sd(), groups[g][2].mean(), groups[g][2].sd(),
                        groups[g][3].mean(), groups[g][3].sd());
            }
        }
        Properties p = new Properties(); p.setProperty("protocol", PROTOCOL); p.setProperty("identity", identity);
        p.setProperty("mode", options.mode); p.setProperty("regime", "rotating");
        p.setProperty("publication-ready", Boolean.toString(options.mode.equals("FORMAL")));
        p.setProperty("runs", Integer.toString(options.runs)); p.setProperty("generations", Integer.toString(options.generations));
        p.setProperty("fitness-evaluations", Integer.toString(options.runs * options.generations * 500));
        p.setProperty("benchmark-simulations", Integer.toString(options.runs * options.generations));
        p.setProperty("descriptor-reference-simulations", Integer.toString(options.runs * 2));
        p.setProperty("age-penalty", "5"); p.setProperty("archive-reevaluation", "false");
        p.setProperty("mean-policy", "Arithmetic mean of finite retained birth-instance reference-normalised scores, not current-instance or held-out quality");
        p.setProperty("best-policy", "Stored score returned by native age-selected Best; not minimum stored score; minimum exported separately");
        p.setProperty("comparison-limit", "Source-level historical rotating map definitions align; cohorts/binary provenance remain different");
        p.setProperty("data.all_runs.csv.sha256", EFiles.hash(report.resolve("all_runs.csv")));
        p.setProperty("data.table_fragments.csv.sha256", EFiles.hash(report.resolve("table_fragments.csv")));
        p.setProperty("status", "COMPLETE"); EFiles.write(report.resolve("manifest.properties"), p, false);
        return report;
    }
}
