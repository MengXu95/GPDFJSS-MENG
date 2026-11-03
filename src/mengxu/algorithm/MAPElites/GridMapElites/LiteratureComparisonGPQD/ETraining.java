package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Evolve;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD.DcDescriptorBank;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.ArchiveParentSelection;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.UniformFiles;

public final class ETraining {
    private ETraining() { }
    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("ETraining --config <e-study.properties> [--resume] [--dry-run] [--set key=value ...]");
            return;
        }
        run(EConfiguration.parse(args));
    }

    public static ParameterDatabase parameters(EConfiguration config, EConfiguration.Case item,
                                                Path output, String family, String caseHash) throws IOException {
        ParameterDatabase values = new ParameterDatabase((item.method == EConfiguration.Method.QDGP
                ? config.archiveParams : config.populationParams).toFile());
        set(values, "seed.0", config.gpSeed(item));
        set(values, "generations", config.generations);
        set(values, "pop.subpop.0.size", config.population);
        set(values, "breed.elite.0", config.elitism);
        set(values, "eval.problem.eval-model.sim-seed", config.trainingSeed(item));
        set(values, "eval.problem.eval-model.rotate-sim-seed", item.regime == EConfiguration.Regime.rotating);
        set(values, "eval.problem.eval-model.objectives.0", item.scenario.objective.getName());
        String simulation = "eval.problem.eval-model.sim-models.0.";
        set(values, simulation + "num-machines", config.machines);
        set(values, simulation + "num-jobs", config.jobs);
        set(values, simulation + "warmup-jobs", config.warmup);
        set(values, simulation + "min-num-operations", 1);
        set(values, simulation + "max-num-operations", config.machines);
        set(values, simulation + "util-level", item.scenario.utilisation);
        set(values, simulation + "due-date-factor", config.dueFactor);
        set(values, "num-case", config.cases);
        set(values, "candidate-size", Math.min(config.population, 500));
        set(values, "select.tournament.size", 7);
        set(values, "tournament-size", 7);
        set(values, "lexqd.method", item.label());
        set(values, "lexqd.run-id", item.run);
        set(values, "lexqd.snapshot-interval", config.snapshotInterval);
        set(values, "lexqd.output-root", output);
        set(values, "lexqd.seed-step", config.seedStep);
        if (item.method == EConfiguration.Method.GP) {
            for (String key : new String[]{"breed.reproduce.source.0", "gp.koza.xover.source.0", "gp.koza.mutate.source.0"}) {
                set(values, key, "ec.select.TournamentSelection");
            }
        }
        set(values, "e.output-root", output);
        set(values, "e.family-hash", family);
        set(values, "e.case-hash", caseHash);
        set(values, "e.case-key", item.key());
        set(values, "e.method", item.method.name());
        set(values, "e.regime", item.regime.name());
        set(values, "e.scenario-id", item.scenario.id);
        set(values, "e.objective", item.scenario.objective.getName());
        set(values, "e.run-id", item.run);
        set(values, "e.gp-seed", config.gpSeed(item));
        set(values, "e.training-seed", config.trainingSeed(item));
        set(values, "e.descriptor-seed", config.descriptorSeed(item));
        set(values, "e.sampling-seed", config.samplingSeed(item));
        set(values, "e.snapshot-interval", config.snapshotInterval);
        set(values, "e.machines", config.machines);
        set(values, "e.recorded-jobs", config.jobs);
        set(values, "e.warmup-jobs", config.warmup);
        set(values, "e.utilisation", item.scenario.utilisation);
        set(values, "e.due-factor", config.dueFactor);
        set(values, "e.descriptor-jobs", config.descriptorJobs);
        set(values, "e.descriptor-warmup", config.descriptorWarmup);
        set(values, "e.seed-step", config.seedStep);
        validate(values, item);
        return values;
    }

    private static void set(ParameterDatabase values, String key, Object value) {
        values.set(new Parameter(key), value.toString());
    }
    private static void validate(ParameterDatabase values, EConfiguration.Case item) {
        boolean grid = item.method == EConfiguration.Method.QDGP;
        String state = grid ? EGridState.class.getName() : EPopulationState.class.getName();
        if (!state.equals(values.getString(new Parameter("state"), null))
                || values.getBoolean(new Parameter("checkpoint"), null, false)
                || values.getBoolean(new Parameter("quit-on-run-complete"), null, true)
                || values.getIntWithDefault(new Parameter("evalthreads"), null, 0) != 1
                || values.getIntWithDefault(new Parameter("breedthreads"), null, 0) != 1
                || values.getDouble(new Parameter("pop.subpop.0.species.pipe.source.0.prob"), null) != .85
                || values.getDouble(new Parameter("pop.subpop.0.species.pipe.source.1.prob"), null) != .15
                || values.getDouble(new Parameter("pop.subpop.0.species.pipe.source.2.prob"), null) != 0) {
            throw new IllegalArgumentException("Use matched one-thread, 0.85/0.15/0 generation-budget presets");
        }
        String selection = grid ? ArchiveParentSelection.class.getName()
                : item.method == EConfiguration.Method.GP ? "ec.select.TournamentSelection"
                : "mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationLexicaseSelection";
        for (String key : new String[]{"breed.reproduce.source.0", "gp.koza.xover.source.0", "gp.koza.mutate.source.0"}) {
            if (!selection.equals(values.getString(new Parameter(key), null))) {
                throw new IllegalArgumentException("Unexpected selector for " + item.method + ": " + key);
            }
        }
    }

    public static String fingerprint(EConfiguration config) throws IOException {
        Properties science = config.trainingProperties();
        science.setProperty("protocol", EConfiguration.PROTOCOL);
        science.setProperty("fingerprint-scope", "search/evaluation/seed/control components; dispatcher selection and generic persistence excluded");
        for (EConfiguration.Method method : EConfiguration.Method.values()) {
            for (EConfiguration.Regime regime : EConfiguration.Regime.values()) {
                StringWriter text = new StringWriter();
                parameters(config, new EConfiguration.Case(method, regime, EConfiguration.Scenario.T085, 0),
                        Path.of("fingerprint-output"), "fingerprint", "fingerprint").list(new PrintWriter(text), false);
                science.setProperty("resolved." + method + "." + regime, ArchiveCandidate.sha256(text.toString()));
            }
        }
        return EFiles.fingerprint(science, EConfiguration.class, EPopulationState.class, EPopulationStatistics.class,
                EGridState.class, EGridStatistics.class, EGridMap.class, EGridEvaluation.class, EManagedState.class,
                EAdapterSupport.class,
                DcDescriptorBank.class,
                mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseEvolutionState.class,
                mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.OneInstanceEvaluation.class,
                mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseFitness.class,
                mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationLexicaseSelection.class,
                mengxu.algorithm.lexicaseselection.EpsilonTTGenNoRepeatCandidateLexicaseSelection.class,
                mengxu.algorithm.lexicaseselection.GPRuleEvolutionStateOneInstanceMultiCase.class,
                mengxu.algorithm.MAPElites.GridMapElites.GPRuleEvolutionStateGridMAPElites.class,
                mengxu.algorithm.MAPElites.GridMapElites.ParentSelectionGridMAPElites.class,
                mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.IsolatedGridMap.class,
                ArchiveParentSelection.class,
                yimei.jss.algorithm.multipletreegp.AllIndexAllSwapCrossoverPipeline.class,
                mengxu.algorithm.MAPElites.GridMapElites.AllIndexAllSwapCrossoverPipelineGridMAPElites.class,
                mengxu.algorithm.MAPElites.GridMapElites.MutationPipelineGridMAPElites.class,
                mengxu.algorithm.MAPElites.GridMapElites.gridPCmap.class,
                yimei.jss.gp.GPRuleEvolutionState.class, yimei.jss.rule.operation.evolved.GPRule.class,
                yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem.class,
                yimei.jss.ruleevaluation.MultipleRuleEvaluationModel.class,
                mengxu.complexsimulation.ruleevaluation.MultipleTreeMultipleRuleHeterogeneousEvaluationModel.class,
                mengxu.complexsimulation.HeterogeneousSimulation.class, yimei.jss.simulation.DynamicSimulation.class,
                yimei.jss.simulation.Simulation.class, yimei.jss.simulation.state.SystemState.class,
                yimei.jss.jobshop.Job.class, yimei.jss.jobshop.Operation.class,
                yimei.jss.jobshop.OperationOption.class, yimei.jss.jobshop.WorkCenter.class,
                yimei.jss.niching.SequencingPhenoCharacterisation.class,
                yimei.jss.niching.RoutingPhenoCharacterisation.class,
                yimei.jss.gp.terminal.JobShopAttribute.class, yimei.jss.gp.terminal.AttributeGPNode.class,
                yimei.jss.gp.terminal.ConstantTerminal.class, yimei.jss.gp.function.Add.class,
                yimei.jss.gp.function.Sub.class, yimei.jss.gp.function.Mul.class, yimei.jss.gp.function.Div.class,
                yimei.jss.gp.function.Min.class, yimei.jss.gp.function.Max.class,
                ec.simple.SimpleBreeder.class, ec.simple.SimpleEvaluator.class, ec.gp.GPInitializer.class);
    }

    public static String caseFingerprint(EConfiguration config, EConfiguration.Case item, String family) throws IOException {
        Properties identity = new Properties();
        identity.setProperty("family-hash", family);
        identity.setProperty("case-key", item.key());
        identity.setProperty("gp-seed", Long.toString(config.gpSeed(item)));
        identity.setProperty("training-seed", Long.toString(config.trainingSeed(item)));
        return EFiles.fingerprint(identity);
    }

    static void run(EConfiguration config) throws IOException {
        run(config, null);
    }

    static void runParallel(EParallelOptions options) throws IOException {
        run(options.config, options);
    }

    private static void run(EConfiguration config, EParallelOptions parallel) throws IOException {
        List<EConfiguration.Case> requested = config.requestedCases();
        if (config.dryRun) {
            for (EConfiguration.Scenario scene : config.scenarios) {
                System.out.println(scene.id + ": " + config.methods + " x " + config.regimes + " x " + config.runs);
            }
            for (EConfiguration.Case item : requested) {
                parameters(config, item, Path.of("preflight-not-created"), "preflight", "preflight");
            }
            System.out.println("E TRAINING PREFLIGHT PASSED: requested=" + requested.size()
                    + ", fitness calls/run=" + config.nominalEvaluations()
                    + "; no simulations/output. Full optional universe=" + 36L * config.runs);
            if (parallel != null) {
                System.out.println("Parallel execution: up to " + parallel.workers
                        + " independent seed JVMs, -Xmx" + parallel.workerHeap + " each.");
                if (Files.exists(config.trainingRoot.resolve("manifest.properties"))) {
                    Properties saved = EFiles.read(config.trainingRoot.resolve("manifest.properties"));
                    if (!fingerprint(config).equals(saved.getProperty("family-hash"))) {
                        throw new IOException("Parallel dispatch rejected: existing scientific fingerprint differs");
                    }
                    System.out.println("Existing training scientific fingerprint MATCHES; no results changed.");
                }
            }
            return;
        }
        checkedRoot(config.trainingRoot, config.populationParams);
        String family = fingerprint(config);
        Path root = config.trainingRoot;
        Properties manifest;
        if (Files.exists(root)) {
            if (!config.resume) { throw new IOException("Existing training output requires --resume"); }
            manifest = EFiles.read(root.resolve("manifest.properties"));
            if (!family.equals(manifest.getProperty("family-hash"))) {
                throw new IOException("Scientific controls/source/runtime changed; choose a NEW E output root, never overwrite old results");
            }
        } else {
            Files.createDirectories(root.getParent()); Files.createDirectory(root);
            EFiles.write(root.resolve("resolved-study.properties"), config.properties(), false);
            manifest = new Properties();
            manifest.setProperty("protocol", EConfiguration.PROTOCOL);
            manifest.setProperty("family-hash", family);
            manifest.setProperty("runs", Integer.toString(config.runs));
            manifest.setProperty("optional-case-universe", Long.toString(36L * config.runs));
            manifest.setProperty("completion-scope", "last requested case set; additional matched methods/regimes may be added");
            manifest.setProperty("scientific-fingerprint-policy", "selectors and generic I/O are excluded; kernels/controls/seeds/resolved parameters included");
            manifest.setProperty("data.resolved-study.properties.sha256", EFiles.hash(root.resolve("resolved-study.properties")));
            manifest.setProperty("status", "INITIALIZED");
            EFiles.write(root.resolve("manifest.properties"), manifest, false);
        }
        try (FileChannel channel = FileChannel.open(root.resolve(".training.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) { throw new IOException("Another training process owns this E root"); }
            try {
                EFiles.verifyArtifacts(root, manifest);
                if (manifest.containsKey("report.path")) {
                    Path previousReport = EFiles.mapped(root, manifest.getProperty("report.path"));
                    EFiles.checkHash(previousReport.resolve("manifest.properties"), manifest.getProperty("report.manifest-sha256"));
                    EFiles.verifyArtifacts(previousReport, EFiles.read(previousReport.resolve("manifest.properties")));
                }
                int executed = 0;
                List<EConfiguration.Case> pending = new ArrayList<>();
                for (EConfiguration.Case item : requested) {
                    String key = item.key();
                    String mapped = manifest.getProperty(key + ".path");
                    if (mapped != null) {
                        Path directory = EFiles.mapped(root, mapped);
                        Path savedPath = directory.resolve("manifest.properties");
                        if (Files.isRegularFile(savedPath)) {
                            Properties saved = EFiles.read(savedPath);
                            if ("COMPLETE".equals(saved.getProperty("status"))) {
                                validateCase(config, item, family, saved);
                                if (manifest.containsKey(key + ".manifest-sha256")) {
                                    EFiles.checkHash(savedPath, manifest.getProperty(key + ".manifest-sha256"));
                                } else {
                                    System.out.println("ADOPT verified sealed run after interrupted metadata commit: " + key);
                                    manifest.setProperty("status", "RECOVERED_COMMIT");
                                }
                                EFiles.verifyArtifacts(directory, saved);
                                paired(manifest, item, saved);
                                manifest.setProperty(key + ".manifest-sha256", EFiles.hash(savedPath));
                                System.out.println("VERIFIED completed " + key);
                                continue;
                            }
                            if (manifest.containsKey(key + ".manifest-sha256")) {
                                throw new IOException("A previously completed case became incomplete: " + key);
                            }
                        } else if (manifest.containsKey(key + ".manifest-sha256")) {
                            throw new IOException("A previously completed case manifest is missing: " + key);
                        }
                    }
                    if (parallel != null) {
                        pending.add(item);
                        continue;
                    }
                    manifest.setProperty("status", "RUNNING");
                    Path directory = fresh(root, item.stem());
                    manifest.setProperty(key + ".path", directory.getFileName().toString());
                    EFiles.write(root.resolve("manifest.properties"), manifest, true);
                    System.out.println("START " + key + "; fitness budget=" + config.nominalEvaluations());
                    execute(config, item, family, directory);
                    Properties saved = EFiles.read(directory.resolve("manifest.properties"));
                    validateCase(config, item, family, saved);
                    paired(manifest, item, saved);
                    manifest.setProperty(key + ".manifest-sha256", EFiles.hash(directory.resolve("manifest.properties")));
                    EFiles.write(root.resolve("manifest.properties"), manifest, true);
                    executed++;
                }
                if (parallel != null && !pending.isEmpty()) {
                    executed += executeParallel(config, parallel, pending, family, manifest);
                }
                if (executed > 0 || !"COMPLETE".equals(manifest.getProperty("status"))
                        || !selection(config).equals(manifest.getProperty("requested-selection"))) {
                    report(config, manifest);
                    manifest.setProperty("requested-case-count", Integer.toString(requested.size()));
                    manifest.setProperty("requested-selection", selection(config));
                    manifest.setProperty("status", "COMPLETE");
                    manifest.remove("failure");
                    EFiles.write(root.resolve("manifest.properties"), manifest, true);
                }
                System.out.println("E TRAINING COMPLETE: requested=" + requested.size() + ", executed=" + executed
                        + ", total completed in root=" + manifest.getProperty("completed-case-count")
                        + ". Reports: " + manifest.getProperty("report.path"));
            } catch (IOException | RuntimeException failure) {
                manifest.setProperty("status", "FAILED"); manifest.setProperty("failure", failure.toString());
                try { EFiles.write(root.resolve("manifest.properties"), manifest, true); }
                catch (IOException reportFailure) { failure.addSuppressed(reportFailure); }
                throw failure;
            }
        }
    }

    public static void validateCase(EConfiguration config, EConfiguration.Case item, String family,
                                    Properties saved) throws IOException {
        if (!"COMPLETE".equals(saved.getProperty("status"))
                || !family.equals(saved.getProperty("family-hash"))
                || !caseFingerprint(config, item, family).equals(saved.getProperty("case-hash"))
                || !item.key().equals(saved.getProperty("case-key"))
                || !item.method.name().equals(saved.getProperty("method"))
                || !item.regime.name().equals(saved.getProperty("regime"))
                || !item.scenario.id.equals(saved.getProperty("scenario-id"))
                || !Integer.toString(item.run).equals(saved.getProperty("run-id"))
                || !item.kind().equals(saved.getProperty("output-kind"))
                || !item.finalFile().equals(saved.getProperty("final-file"))
                || Long.parseLong(EFiles.required(saved, "fitness-evaluations")) != config.nominalEvaluations()) {
            throw new IOException("Incomplete/incompatible case: " + item.key());
        }
    }

    static void execute(EConfiguration config, EConfiguration.Case item, String family, Path directory) throws IOException {
        Properties marker = new Properties();
        marker.setProperty("protocol", EConfiguration.PROTOCOL); marker.setProperty("family-hash", family);
        marker.setProperty("case-hash", caseFingerprint(config, item, family));
        marker.setProperty("case-key", item.key()); marker.setProperty("method", item.method.name());
        marker.setProperty("regime", item.regime.name()); marker.setProperty("scenario-id", item.scenario.id);
        marker.setProperty("run-id", Integer.toString(item.run)); marker.setProperty("objective", item.scenario.objective.getName());
        marker.setProperty("utilisation", Double.toString(item.scenario.utilisation));
        marker.setProperty("gp-seed", Long.toString(config.gpSeed(item)));
        marker.setProperty("training-seed", Long.toString(config.trainingSeed(item)));
        marker.setProperty("descriptor-seed", Long.toString(config.descriptorSeed(item)));
        marker.setProperty("sampling-seed", Long.toString(config.samplingSeed(item)));
        marker.setProperty("status", "RUNNING");
        EFiles.write(directory.resolve("manifest.properties"), marker, false);
        PrintStream console = System.out, errors = System.err;
        EvolutionState state = null;
        try {
            try (PrintStream log = new PrintStream(Files.newOutputStream(directory.resolve("training.log"),
                    StandardOpenOption.CREATE_NEW), true, StandardCharsets.UTF_8.name())) {
                System.setOut(log); System.setErr(log);
                try {
                    state = Evolve.initialize(parameters(config, item, directory, family, marker.getProperty("case-hash")), 0);
                    if (!(state instanceof EManagedState)) { throw new IllegalStateException("Use managed E states"); }
                    ((EManagedState) state).setProgress(console);
                    state.run(EvolutionState.C_STARTED_FRESH);
                } finally {
                    try { if (state instanceof EManagedState) { ((EManagedState) state).closeResources(); } }
                    finally { if (state != null) { Evolve.cleanup(state); } }
                }
                if (log.checkError()) { throw new IOException("Training log write failed"); }
            } finally { System.setOut(console); System.setErr(errors); }
            Properties saved = EFiles.read(directory.resolve("manifest.properties"));
            if (!"SEARCH_COMPLETE".equals(saved.getProperty("status"))) {
                throw new IOException("Search ended before the complete case was sealed");
            }
            try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path path : files) if (Files.isRegularFile(path) && !path.getFileName().toString().equals("manifest.properties")) {
                    saved.setProperty("data." + path.getFileName() + ".sha256", EFiles.hash(path));
                }
            }
            saved.setProperty("status", "COMPLETE");
            EFiles.write(directory.resolve("manifest.properties"), saved, true);
        } catch (IOException | RuntimeException failure) {
            Properties saved = EFiles.read(directory.resolve("manifest.properties"));
            saved.setProperty("status", "FAILED"); saved.setProperty("failure", failure.toString());
            try { EFiles.write(directory.resolve("manifest.properties"), saved, true); }
            catch (IOException reporting) { failure.addSuppressed(reporting); }
            throw failure;
        }
    }

    private static int executeParallel(EConfiguration config, EParallelOptions options,
                                       List<EConfiguration.Case> pending, String family, Properties manifest) throws IOException {
        Path root = config.trainingRoot;
        Path execution = fresh(root, "parallel-training");
        Path configuration = execution.resolve("worker-config.properties");
        EFiles.write(configuration, config.properties(), false);
        java.util.Map<String, EConfiguration.Case> indexed = new java.util.LinkedHashMap<>();
        List<EProcessPool.Work> work = new ArrayList<>();
        for (EConfiguration.Case item : pending) {
            indexed.put(item.key(), item);
            work.add(new EProcessPool.Work(item.key(),
                    item.method + "." + item.regime + "." + item.scenario.id));
        }
        manifest.setProperty("last-parallel-execution.path", execution.getFileName().toString());
        manifest.setProperty("parallel-workers", Integer.toString(options.workers));
        manifest.setProperty("parallel-worker-heap", options.workerHeap);
        manifest.setProperty("execution-mode", "case-isolated JVMs; coordinator-only root publication");
        manifest.setProperty("status", "RUNNING");
        EFiles.write(root.resolve("manifest.properties"), manifest, true);
        EProcessPool.Summary result = EProcessPool.run(work, options, execution,
                (job, ordinal) -> {
                    EConfiguration.Case item = indexed.get(job.key);
                    Path attempt = fresh(root, item.stem());
                    manifest.setProperty(item.key() + ".path", attempt.getFileName().toString());
                    EFiles.write(root.resolve("manifest.properties"), manifest, true);
                    Properties request = new Properties();
                    request.setProperty("protocol", "e-parallel-training-job-v1");
                    request.setProperty("configuration", configuration.toAbsolutePath().toString());
                    request.setProperty("case-key", item.key());
                    request.setProperty("family-hash", family);
                    request.setProperty("case-hash", caseFingerprint(config, item, family));
                    request.setProperty("attempt-directory", attempt.toAbsolutePath().toString());
                    Path requestFile = execution.resolve(String.format(java.util.Locale.ROOT, "job-%06d.properties", ordinal));
                    EFiles.write(requestFile, request, false);
                    System.out.println("DISPATCH " + item.key() + " -> " + attempt.getFileName());
                    return new EProcessPool.Job(job, ETrainingWorker.class.getName(), requestFile,
                            execution.resolve(String.format(java.util.Locale.ROOT, "worker-%06d.log", ordinal)));
                },
                finished -> {
                    EConfiguration.Case item = indexed.get(finished.job.work.key);
                    if (finished.exitCode != 0) {
                        throw new IOException("Seed worker failed: " + item.key() + ", exit=" + finished.exitCode
                                + ", log=" + finished.job.consoleLog);
                    }
                    Path attempt = EFiles.mapped(root, manifest.getProperty(item.key() + ".path"));
                    Properties saved = EFiles.read(attempt.resolve("manifest.properties"));
                    validateCase(config, item, family, saved);
                    EFiles.verifyArtifacts(attempt, saved);
                    paired(manifest, item, saved);
                    manifest.setProperty(item.key() + ".manifest-sha256", EFiles.hash(attempt.resolve("manifest.properties")));
                    EFiles.write(root.resolve("manifest.properties"), manifest, true);
                    System.out.println("COMMITTED seed " + item.key() + ", worker PID=" + finished.pid);
                });
        manifest.setProperty("parallel-max-active", Integer.toString(result.maxActive));
        return result.completed;
    }

    private static void paired(Properties root, EConfiguration.Case item, Properties saved) throws IOException {
        for (String name : new String[]{"initial-genotype-sha256", "initial-evaluated-population-sha256"}) {
            String key = "paired." + item.scenario.id + ".run." + item.run + "." + name;
            String actual = EFiles.required(saved, name);
            if (root.containsKey(key) && !actual.equals(root.getProperty(key))) {
                throw new IOException("Unmatched initial populations/job streams across methods/regimes: " + key);
            }
            root.setProperty(key, actual);
        }
    }

    private static void report(EConfiguration config, Properties root) throws IOException {
        Path directory = fresh(config.trainingRoot, "training-report");
        List<String> paths = new ArrayList<>();
        for (String key : root.stringPropertyNames()) {
            if (key.endsWith(".path") && key.contains(".run.")) { paths.add(key); }
        }
        Collections.sort(paths);
        int completed = 0;
        try (BufferedWriter writer = EFiles.create(directory.resolve("training_runs.csv"))) {
            EFiles.row(writer, "case_key", "method", "regime", "scenario", "run_id", "output_kind", "final_members",
                    "stored_finite_members", "fitness_evaluations", "manual_reference_simulations",
                    "descriptor_reference_simulations", "total_training_simulations", "training_seconds", "attempt_path");
            for (String key : paths) {
                Path run = EFiles.mapped(config.trainingRoot, root.getProperty(key));
                Properties values = EFiles.read(run.resolve("manifest.properties"));
                if (!"COMPLETE".equals(values.getProperty("status"))) { continue; }
                String stem = key.substring(0, key.length() - 5);
                EFiles.checkHash(run.resolve("manifest.properties"), root.getProperty(stem + ".manifest-sha256"));
                EFiles.verifyArtifacts(run, values);
                EFiles.row(writer, values.getProperty("case-key"), values.getProperty("method"), values.getProperty("regime"),
                        values.getProperty("scenario-id"), values.getProperty("run-id"), values.getProperty("output-kind"),
                        values.getProperty("final-members"), values.getProperty("stored-finite-members"),
                        values.getProperty("fitness-evaluations"), values.getProperty("manual-reference-simulations"),
                        values.getProperty("descriptor-reference-simulations"), values.getProperty("total-training-simulations"),
                        values.getProperty("elapsed-training-seconds"), run.getFileName());
                completed++;
            }
        }
        Properties report = new Properties();
        report.setProperty("status", "COMPLETE"); report.setProperty("family-hash", root.getProperty("family-hash"));
        report.setProperty("completed-case-count", Integer.toString(completed));
        report.setProperty("data.training_runs.csv.sha256", EFiles.hash(directory.resolve("training_runs.csv")));
        EFiles.write(directory.resolve("manifest.properties"), report, false);
        root.setProperty("report.path", directory.getFileName().toString());
        root.setProperty("report.manifest-sha256", EFiles.hash(directory.resolve("manifest.properties")));
        root.setProperty("completed-case-count", Integer.toString(completed));
    }

    public static Path fresh(Path root, String stem) throws IOException {
        Path directory = root.resolve(stem);
        for (int attempt = 2; Files.exists(directory); attempt++) { directory = root.resolve(stem + "-attempt-" + attempt); }
        Files.createDirectory(directory); return directory;
    }
    private static String selection(EConfiguration config) {
        return config.methods + "/" + config.regimes + "/" + config.scenarios;
    }
    private static void checkedRoot(Path root, Path preset) throws IOException {
        Path source = preset.getParent();
        while (source != null && (source.getFileName() == null || !source.getFileName().toString().equals("src"))) {
            source = source.getParent();
        }
        if (source == null) { throw new IOException("The E preset must be within the project source tree"); }
        Path resolved = Files.exists(root) ? root.toRealPath() : UniformFiles.checkedOutput(root, source);
        for (String name : new String[]{"src", "bin", "out", ".git"}) {
            if (resolved.startsWith(source.getParent().resolve(name))) { throw new IOException("Output may not be inside project " + name); }
        }
    }
}
