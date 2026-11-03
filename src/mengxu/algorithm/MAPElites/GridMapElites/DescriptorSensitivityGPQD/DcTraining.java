package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

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
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.GridMapElites.GPRuleEvolutionStateGridMAPElites;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.ArchiveParentSelection;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.IsolatedGridMap;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.UniformFiles;

public final class DcTraining {
    private DcTraining() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("DcTraining --config <dc-study.properties> [--resume] [--dry-run] [--set key=value ...]");
            return;
        }
        run(DcConfiguration.parse(args));
    }

    public static String caseKey(DcConfiguration.Setting setting, int run) {
        return setting.id() + ".run." + run;
    }

    public static ParameterDatabase parameters(DcConfiguration config, DcConfiguration.Setting setting,
                                               int run, Path output, String studyHash) throws IOException {
        ParameterDatabase values = new ParameterDatabase(config.paramsPath.toFile());
        set(values, "state", DcState.class.getName());
        set(values, "stat", DcStatistics.class.getName());
        set(values, "seed.0", Math.addExact(config.gpSeedBase, run));
        set(values, "generations", config.generations);
        set(values, "pop.subpop.0.size", config.population);
        set(values, "breed.elite.0", config.elitism);
        set(values, "eval.problem.eval-model.sim-seed", Math.addExact(config.trainingSeedBase, run));
        String simulation = "eval.problem.eval-model.sim-models.0.";
        set(values, simulation + "num-machines", config.machines);
        set(values, simulation + "num-jobs", config.jobs);
        set(values, simulation + "warmup-jobs", config.warmup);
        set(values, simulation + "util-level", config.utilisation);
        set(values, simulation + "due-date-factor", config.dueFactor);
        set(values, "eval.problem.eval-model.objectives.0", "mean-tardiness");
        set(values, "dc.output-root", output);
        set(values, "dc.study-hash", studyHash);
        set(values, "dc.d", setting.d);
        set(values, "dc.c", setting.c);
        set(values, "dc.run-id", run);
        set(values, "dc.training-seed", Math.addExact(config.trainingSeedBase, run));
        set(values, "dc.descriptor-seed", Math.addExact(config.descriptorSeedBase, run));
        set(values, "dc.sampling-seed", Math.addExact(config.samplingSeedBase, run));
        set(values, "dc.snapshot-interval", config.snapshotInterval);
        set(values, "dc.machines", config.machines);
        set(values, "dc.descriptor-jobs", config.descriptorJobs);
        set(values, "dc.descriptor-warmup", config.descriptorWarmup);
        set(values, "dc.utilisation", config.utilisation);
        set(values, "dc.due-factor", config.dueFactor);
        validatePreset(values);
        return values;
    }

    private static void set(ParameterDatabase parameters, String key, Object value) {
        parameters.set(new Parameter(key), value.toString());
    }

    private static void validatePreset(ParameterDatabase values) {
        for (String key : new String[]{"gp.koza.xover.source.0", "gp.koza.mutate.source.0", "breed.reproduce.source.0"}) {
            if (!ArchiveParentSelection.class.getName().equals(values.getString(new Parameter(key), null))) {
                throw new IllegalArgumentException("Use the archive-index-safe parent selector: " + key);
            }
        }
        if (values.getBoolean(new Parameter("eval.problem.eval-model.rotate-sim-seed"), null, true)
                || values.getInt(new Parameter("number-accept"), null) != 1
                || values.getDouble(new Parameter("random-parent-selection-probability"), null) != .5
                || values.getDouble(new Parameter("penalty-level"), null) != 0
                || values.getDouble(new Parameter("pop.subpop.0.species.pipe.source.0.prob"), null) != .85
                || values.getDouble(new Parameter("pop.subpop.0.species.pipe.source.1.prob"), null) != .15
                || values.getDouble(new Parameter("pop.subpop.0.species.pipe.source.2.prob"), null) != 0) {
            throw new IllegalArgumentException("The C study changes d,c only; use the approved QDGPf source controls");
        }
    }

    public static String fingerprint(DcConfiguration config) throws IOException {
        Properties controls = config.trainingProperties();
        StringWriter resolved = new StringWriter();
        parameters(config, config.settings.get(0), 0, Path.of("fingerprint-output"), "fingerprint")
                .list(new PrintWriter(resolved), false);
        controls.setProperty("resolved-parameters-sha256", ArchiveCandidate.sha256(resolved.toString()));
        controls.setProperty("protocol", DcConfiguration.PROTOCOL);
        return DcFiles.fingerprint(controls, DcTraining.class, DcConfiguration.class,
                DcFiles.class, DcState.class, DcStatistics.class, DcGridMap.class, DcDescriptorBank.class,
                GPRuleEvolutionStateGridMAPElites.class, IsolatedGridMap.class, ArchiveParentSelection.class,
                UniformFiles.class, mengxu.complexsimulation.HeterogeneousSimulation.class,
                mengxu.complexsimulation.ruleevaluation.MultipleTreeMultipleRuleHeterogeneousEvaluationModel.class,
                yimei.jss.ruleevaluation.MultipleRuleEvaluationModel.class,
                yimei.jss.niching.SequencingPhenoCharacterisation.class,
                yimei.jss.niching.RoutingPhenoCharacterisation.class,
                yimei.jss.simulation.DynamicSimulation.class,
                mengxu.algorithm.MAPElites.GridMapElites.gridPCmap.class,
                mengxu.algorithm.MAPElites.GridMapElites.AllIndexAllSwapCrossoverPipelineGridMAPElites.class,
                mengxu.algorithm.MAPElites.GridMapElites.MutationPipelineGridMAPElites.class,
                mengxu.algorithm.MAPElites.GridMapElites.ParentSelectionGridMAPElites.class,
                mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots.class,
                yimei.jss.gp.GPRuleEvolutionState.class,
                yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem.class,
                yimei.jss.rule.operation.evolved.GPRule.class,
                yimei.jss.gp.terminal.JobShopAttribute.class,
                yimei.jss.gp.terminal.AttributeGPNode.class,
                yimei.jss.gp.terminal.ConstantTerminal.class,
                yimei.jss.gp.function.Add.class, yimei.jss.gp.function.Sub.class,
                yimei.jss.gp.function.Mul.class, yimei.jss.gp.function.Div.class,
                yimei.jss.gp.function.Min.class, yimei.jss.gp.function.Max.class,
                yimei.jss.simulation.Simulation.class,
                yimei.jss.simulation.state.SystemState.class,
                yimei.jss.simulation.event.JobArrivalEvent.class,
                yimei.jss.simulation.event.ProcessFinishEvent.class,
                yimei.jss.jobshop.Job.class, yimei.jss.jobshop.Operation.class,
                yimei.jss.jobshop.OperationOption.class, yimei.jss.jobshop.WorkCenter.class);
    }

    static void run(DcConfiguration config) throws IOException {
        if (config.dryRun) {
            for (DcConfiguration.Setting setting : config.settings) {
                parameters(config, setting, 0, Path.of("preflight-not-created"), "preflight");
                System.out.println(setting.id() + ": " + setting.side() + " x " + setting.side()
                        + " = " + setting.cells() + " cells; runs=" + config.runs);
            }
            System.out.println("PREFLIGHT PASSED: " + config.settings.size() * config.runs
                    + " fixed-instance training runs, " + config.nominalEvaluations()
                    + " fitness calls/run; no simulations or output created.");
            return;
        }
        checkOutput(config.trainingRoot, config.paramsPath);
        String fingerprint = fingerprint(config);
        Path root = config.trainingRoot;
        Properties manifest;
        if (Files.exists(root)) {
            if (!config.resume) { throw new IOException("Existing training output requires --resume"); }
            manifest = DcFiles.read(root.resolve("manifest.properties"));
            if (!fingerprint.equals(manifest.getProperty("study-hash"))) {
                throw new IOException("Changed training controls/source/runtime/parameters; choose a fresh training root");
            }
        } else {
            Files.createDirectories(root.getParent());
            Files.createDirectory(root);
            DcFiles.write(root.resolve("resolved-study.properties"), config.properties(), false);
            manifest = new Properties();
            manifest.setProperty("protocol", DcConfiguration.PROTOCOL);
            manifest.setProperty("study-hash", fingerprint);
            manifest.setProperty("scenario", config.scenario());
            manifest.setProperty("num-runs", Integer.toString(config.runs));
            manifest.setProperty("num-settings", Integer.toString(config.settings.size()));
            manifest.setProperty("nominal-evaluations-per-run", Long.toString(config.nominalEvaluations()));
            manifest.setProperty("data.resolved-study.properties.sha256", DcFiles.hash(root.resolve("resolved-study.properties")));
            manifest.setProperty("status", "INITIALIZED");
            DcFiles.write(root.resolve("manifest.properties"), manifest, false);
        }
        try (FileChannel channel = FileChannel.open(root.resolve(".training.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) { throw new IOException("Another training process owns this output"); }
            try {
                DcFiles.verifyArtifacts(root, manifest);
                boolean wasComplete = "COMPLETE".equals(manifest.getProperty("status"));
                for (DcConfiguration.Setting setting : config.settings) {
                    for (int run = 0; run < config.runs; run++) {
                        String key = caseKey(setting, run);
                        Path directory = null;
                        if (manifest.containsKey(key + ".path")) {
                            directory = DcFiles.mapped(root, manifest.getProperty(key + ".path"));
                            Path savedPath = directory.resolve("manifest.properties");
                            if (Files.isRegularFile(savedPath)) {
                                Properties saved = DcFiles.read(savedPath);
                                if ("COMPLETE".equals(saved.getProperty("status"))
                                        && fingerprint.equals(saved.getProperty("study-hash"))) {
                                    if (wasComplete) { DcFiles.checkHash(savedPath, manifest.getProperty(key + ".manifest-sha256")); }
                                    DcFiles.verifyArtifacts(directory, saved);
                                    verifyPairedInputs(manifest, setting, run, saved);
                                    System.out.println("VERIFIED completed " + key);
                                    continue;
                                }
                            }
                            if (wasComplete) { throw new IOException("A COMPLETE study has an incomplete/missing run"); }
                        } else if (wasComplete) { throw new IOException("COMPLETE study lacks expected run mapping"); }
                        manifest.setProperty("status", "RUNNING");
                        String stem = setting.id() + "-run-" + run;
                        directory = root.resolve(stem);
                        for (int attempt = 2; Files.exists(directory); attempt++) {
                            directory = root.resolve(stem + "-attempt-" + attempt);
                        }
                        Files.createDirectory(directory);
                        manifest.setProperty(key + ".path", directory.getFileName().toString());
                        DcFiles.write(root.resolve("manifest.properties"), manifest, true);
                        System.out.println("START " + key + ": " + setting.side() + " x " + setting.side()
                                + ", fitness budget=" + config.nominalEvaluations());
                        execute(config, setting, run, directory, fingerprint);
                        Properties saved = DcFiles.read(directory.resolve("manifest.properties"));
                        verifyPairedInputs(manifest, setting, run, saved);
                        manifest.setProperty(key + ".manifest-sha256", DcFiles.hash(directory.resolve("manifest.properties")));
                        DcFiles.write(root.resolve("manifest.properties"), manifest, true);
                    }
                }
                if (!wasComplete) {
                    aggregate(config, manifest);
                    manifest.setProperty("status", "COMPLETE");
                    manifest.remove("failure");
                    DcFiles.write(root.resolve("manifest.properties"), manifest, true);
                }
                System.out.println("TRAINING COMPLETE AND VERIFIED: " + root
                        + ". Run DcTest separately; no held-out tests were used during training.");
            } catch (IOException | RuntimeException failure) {
                manifest.setProperty("status", "FAILED");
                manifest.setProperty("failure", failure.toString());
                try { DcFiles.write(root.resolve("manifest.properties"), manifest, true); }
                catch (IOException reporting) { failure.addSuppressed(reporting); }
                throw failure;
            }
        }
    }

    private static void verifyPairedInputs(Properties manifest, DcConfiguration.Setting setting,
                                           int run, Properties saved) throws IOException {
        for (String[] pair : new String[][]{
                {"paired.initial-population.run." + run, "data.initial_population.stat.sha256"},
                {"paired.snapshot-bank.c" + setting.c + ".run." + run, "data.snapshot_bank.csv.sha256"}}) {
            String actual = saved.getProperty(pair[1]);
            if (actual == null) { throw new IOException("Missing paired-input artifact"); }
            String previous = manifest.getProperty(pair[0]);
            if (previous != null && !previous.equals(actual)) {
                throw new IOException("Settings do not share identical initial population or nested snapshot bank");
            }
            manifest.setProperty(pair[0], actual);
        }
    }

    private static void execute(DcConfiguration config, DcConfiguration.Setting setting, int run,
                                Path directory, String fingerprint) throws IOException {
        Properties marker = new Properties();
        marker.setProperty("protocol", DcConfiguration.PROTOCOL);
        marker.setProperty("study-hash", fingerprint);
        marker.setProperty("status", "RUNNING");
        marker.setProperty("d", Integer.toString(setting.d));
        marker.setProperty("c", Integer.toString(setting.c));
        marker.setProperty("run-id", Integer.toString(run));
        marker.setProperty("scenario", config.scenario());
        marker.setProperty("gp-seed", Long.toString(config.gpSeedBase + run));
        marker.setProperty("training-seed", Long.toString(config.trainingSeedBase + run));
        marker.setProperty("descriptor-seed", Long.toString(config.descriptorSeedBase + run));
        marker.setProperty("sampling-seed", Long.toString(config.samplingSeedBase + run));
        DcFiles.write(directory.resolve("manifest.properties"), marker, false);
        PrintStream console = System.out, errors = System.err;
        DcState state = null;
        try {
            try (PrintStream log = new PrintStream(Files.newOutputStream(directory.resolve("training.log"),
                    StandardOpenOption.CREATE_NEW), true, StandardCharsets.UTF_8.name())) {
                System.setOut(log); System.setErr(log);
                try {
                    state = (DcState) Evolve.initialize(parameters(config, setting, run, directory, fingerprint), 0);
                    state.progress = console;
                    state.run(EvolutionState.C_STARTED_FRESH);
                } finally {
                    try {
                        if (state != null && state.statistics instanceof DcStatistics) {
                            ((DcStatistics) state.statistics).closeMetrics();
                        }
                    } finally { if (state != null) { Evolve.cleanup(state); } }
                }
                if (log.checkError()) { throw new IOException("Training log write failed"); }
            } finally { System.setOut(console); System.setErr(errors); }
            Properties saved = DcFiles.read(directory.resolve("manifest.properties"));
            if (!"SEARCH_COMPLETE".equals(saved.getProperty("status"))
                    || Long.parseLong(saved.getProperty("fitness-evaluations")) != config.nominalEvaluations()) {
                throw new IOException("Training stopped without the complete evaluation budget");
            }
            try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path path : files) {
                    if (Files.isRegularFile(path) && !path.getFileName().toString().equals("manifest.properties")) {
                        saved.setProperty("data." + path.getFileName() + ".sha256", DcFiles.hash(path));
                    }
                }
            }
            saved.setProperty("status", "COMPLETE");
            DcFiles.write(directory.resolve("manifest.properties"), saved, true);
        } catch (IOException | RuntimeException failure) {
            Properties saved = DcFiles.read(directory.resolve("manifest.properties"));
            saved.setProperty("status", "FAILED");
            saved.setProperty("failure", failure.toString());
            try { DcFiles.write(directory.resolve("manifest.properties"), saved, true); }
            catch (IOException reporting) { failure.addSuppressed(reporting); }
            throw failure;
        }
    }

    private static void aggregate(DcConfiguration config, Properties manifest) throws IOException {
        Path output = config.trainingRoot.resolve("training_runs.csv");
        Path temporary = config.trainingRoot.resolve("training_runs-aggregate.tmp");
        Files.deleteIfExists(temporary);
        try (BufferedWriter writer = DcFiles.create(temporary)) {
            DcFiles.row(writer, "setting_id", "d", "c", "run_id", "grid_side", "grid_cells",
                    "native_occupied_cells", "native_retained_members", "finite_stored_members", "native_coverage",
                    "best_training_fitness", "fitness_evaluations", "manual_reference_simulations",
                    "descriptor_reference_simulations", "total_training_simulations", "training_seconds",
                    "peak_observed_used_heap_bytes", "attempt_path");
            for (DcConfiguration.Setting setting : config.settings) {
                for (int run = 0; run < config.runs; run++) {
                    String key = caseKey(setting, run);
                    Path directory = DcFiles.mapped(config.trainingRoot, manifest.getProperty(key + ".path"));
                    Properties saved = DcFiles.read(directory.resolve("manifest.properties"));
                    DcFiles.verifyArtifacts(directory, saved);
                    manifest.setProperty(key + ".manifest-sha256", DcFiles.hash(directory.resolve("manifest.properties")));
                    int occupied = Integer.parseInt(saved.getProperty("native-occupied-cells"));
                    DcFiles.row(writer, setting.id(), setting.d, setting.c, run, setting.side(), setting.cells(),
                            occupied, saved.getProperty("native-retained-members"), saved.getProperty("finite-stored-members"),
                            occupied / (double) setting.cells(), saved.getProperty("best-training-fitness"),
                            saved.getProperty("fitness-evaluations"), saved.getProperty("manual-reference-simulations"),
                            saved.getProperty("descriptor-reference-simulations"), saved.getProperty("total-training-simulations"),
                            saved.getProperty("elapsed-training-seconds"), saved.getProperty("peak-used-heap-bytes"),
                            directory.getFileName());
                }
            }
        }
        if (Files.exists(output)) {
            if (!DcFiles.hash(output).equals(DcFiles.hash(temporary))) {
                throw new IOException("Existing training aggregate disagrees with verified completed runs");
            }
            Files.delete(temporary);
        } else {
            Files.move(temporary, output, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }
        manifest.setProperty("data.training_runs.csv.sha256", DcFiles.hash(output));
    }

    private static void checkOutput(Path output, Path params) throws IOException {
        Path source = params.getParent();
        while (source != null && (source.getFileName() == null
                || !source.getFileName().toString().equalsIgnoreCase("src"))) { source = source.getParent(); }
        if (source == null) { throw new IOException("Parameter preset must reside in the project source tree"); }
        Path project = source.getParent();
        Path resolved = Files.exists(output) ? output.toRealPath() : UniformFiles.checkedOutput(output, source);
        for (String name : new String[]{"src", "bin", "out", ".git"}) {
            if (resolved.startsWith(project.resolve(name).toAbsolutePath().normalize())) {
                throw new IOException("Output may not be inside project " + name);
            }
        }
    }
}
