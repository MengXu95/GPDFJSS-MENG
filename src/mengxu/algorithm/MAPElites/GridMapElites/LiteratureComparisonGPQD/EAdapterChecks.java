package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.FinalArchiveReader;
import mengxu.algorithm.MAPElites.GridMapElites.GPRuleEvolutionStateGridMAPElites;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseEvolutionState;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseFitness;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.OneInstanceEvaluation;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots;
import yimei.jss.jobshop.Job;
import yimei.jss.jobshop.Objective;

/** Bounded software checks only: 12 individuals, 3 batches, all six scenes and both regimes. */
public final class EAdapterChecks {
    private EAdapterChecks() { }
    private static final int POPULATION = 12, GENERATIONS = 3, CASES = 4;

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("EAdapterChecks <module-directory> [new-check-output-directory]");
        }
        Path module = Path.of(args[0]).toRealPath();
        Path root = args.length == 2 ? Path.of(args[1]).toAbsolutePath().normalize()
                : Path.of("e-adapter-checks-" + System.currentTimeMillis()).toAbsolutePath().normalize();
        Files.createDirectory(root);
        caseChecks();
        evaluationResetCheck(module);
        allFailedChecks();
        scalarSelectionCheck();
        check(EPopulationState.class.getMethod("evolve").getDeclaringClass() == LexicaseEvolutionState.class,
                "Population adapter must inherit the exact Lexicase evolve implementation");
        check(EGridState.class.getMethod("evolve").getDeclaringClass() == GPRuleEvolutionStateGridMAPElites.class,
                "Grid adapter must inherit the exact native QD evolve implementation");
        Map<EConfiguration.Method, Path> intervalBaselines = new HashMap<>();
        for (EConfiguration.Scenario scenario : EConfiguration.Scenario.values()) {
            for (EConfiguration.Regime regime : EConfiguration.Regime.values()) {
                String genotype = null, evaluated = null;
                for (EConfiguration.Method method : EConfiguration.Method.values()) {
                    EConfiguration.Case item = new EConfiguration.Case(method, regime, scenario, 0);
                    Path attempt = root.resolve(item.stem());
                    train(module, attempt, item, 1);
                    Properties manifest = EFiles.read(attempt.resolve("manifest.properties"));
                    verify(attempt, item, manifest);
                    if (genotype == null) {
                        genotype = manifest.getProperty("initial-genotype-sha256");
                        evaluated = manifest.getProperty("initial-evaluated-population-sha256");
                    } else {
                        check(genotype.equals(manifest.getProperty("initial-genotype-sha256")),
                                "Matched initial genotypes across methods for " + item.key());
                        check(evaluated.equals(manifest.getProperty("initial-evaluated-population-sha256")),
                                "Matched initial raw evaluations across methods for " + item.key());
                    }
                    if (scenario == EConfiguration.Scenario.T085 && regime == EConfiguration.Regime.rotating) {
                        intervalBaselines.put(method, attempt);
                    }
                }
            }
        }
        for (EConfiguration.Method method : EConfiguration.Method.values()) {
            EConfiguration.Case item = new EConfiguration.Case(method, EConfiguration.Regime.rotating,
                    EConfiguration.Scenario.T085, 0);
            Path sparse = root.resolve("sparse-" + method.name());
            train(module, sparse, item, 99);
            Path baseline = intervalBaselines.get(method);
            check(EFiles.hash(sparse.resolve(item.finalFile())).equals(EFiles.hash(baseline.resolve(item.finalFile()))),
                    "Logging interval must not change final native search output: " + method);
            check(EFiles.hash(sparse.resolve("initial_population.stat")).equals(
                    EFiles.hash(baseline.resolve("initial_population.stat"))), "Logging-independent initialization");
            String prefix = method == EConfiguration.Method.QDGP ? "archive" : "population";
            check(Files.exists(sparse.resolve(prefix + ".gen0000.stat"))
                    && !Files.exists(sparse.resolve(prefix + ".gen0001.stat"))
                    && Files.exists(sparse.resolve(prefix + ".gen0002.stat")), "Generation-zero/last snapshot policy");
        }
        legacyBehaviorChecks(module, root);
        System.out.println("E ADAPTER CHECKS PASSED: six scenarios x both regimes x three methods; paired initial genotype/raw fitness,"
                + " cases, counters, native readers, rotation, failure guards and log-interval independence. Output: " + root);
    }

    private static void legacyBehaviorChecks(Path module, Path root) throws IOException {
        for (EConfiguration.Regime regime : EConfiguration.Regime.values()) {
            Path output = root.resolve("legacy-" + regime.name());
            List<String> arguments = new ArrayList<>();
            Collections.addAll(arguments, "--params", module.getParent().resolve("LexicaseGPQD").resolve(
                    regime == EConfiguration.Regime.fixed ? "lexicase-gpf.params" : "lexicase-gp.params").toString(),
                    "--output-root", output.toString());
            for (String setting : new String[]{"generations=3", "pop.subpop.0.size=12", "candidate-size=12",
                    "breed.elite.0=2", "seed.0=19", "eval.problem.eval-model.sim-seed=17", "num-case=4",
                    "eval.problem.eval-model.objectives.0=mean-tardiness",
                    "eval.problem.eval-model.sim-models.0.num-jobs=100",
                    "eval.problem.eval-model.sim-models.0.warmup-jobs=50", "lexqd.snapshot-interval=1"}) {
                arguments.add("--set"); arguments.add(setting);
            }
            PrintStream console = System.out;
            try (PrintStream log = new PrintStream(root.resolve("legacy-" + regime.name() + ".log").toFile(),
                    StandardCharsets.UTF_8.name())) {
                System.setOut(log);
                mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseQDRun.main(arguments.toArray(new String[0]));
            } finally { System.setOut(console); }
            EConfiguration.Case item = new EConfiguration.Case(EConfiguration.Method.LEXICASE, regime,
                    EConfiguration.Scenario.T085, 0);
            check(EFiles.hash(output.resolve("final_population.stat"))
                    .equals(EFiles.hash(root.resolve(item.stem()).resolve("final_population.stat"))),
                    "Default legacy Lexicase behavior unchanged under extension hooks: " + regime);
            check("COMPLETE".equals(EFiles.read(output.resolve("manifest.properties")).getProperty("status")),
                    "Default legacy statistics still seals its original COMPLETE status");
        }
    }

    public static void train(Path module, Path output, EConfiguration.Case item, int interval) throws Exception {
        Files.createDirectory(output);
        Properties marker = new Properties();
        marker.setProperty("status", "RUNNING");
        marker.setProperty("family-hash", "bounded-adapter-checks");
        marker.setProperty("case-hash", ArchiveCandidate.sha256(item.key()));
        marker.setProperty("case-key", item.key());
        marker.setProperty("method", item.method.name()); marker.setProperty("regime", item.regime.name());
        marker.setProperty("scenario-id", item.scenario.id); marker.setProperty("run-id", Integer.toString(item.run));
        marker.setProperty("objective", item.scenario.objective.getName());
        marker.setProperty("utilisation", Double.toString(item.scenario.utilisation));
        EFiles.write(output.resolve("manifest.properties"), marker, false);
        ParameterDatabase parameters = new ParameterDatabase(module.resolve(
                item.method == EConfiguration.Method.QDGP ? "e-archive.params" : "e-population.params").toFile());
        for (String key : marker.stringPropertyNames()) {
            if (!key.equals("status") && !key.equals("objective")) { put(parameters, "e." + key, marker.getProperty(key)); }
        }
        long seed = 17 + item.scenario.ordinal() * 100000L;
        String[][] settings = {
                {"e.output-root", output.toString()}, {"e.gp-seed", "19"}, {"e.training-seed", Long.toString(seed)},
                {"e.descriptor-seed", Long.toString(seed + 5000000)}, {"e.sampling-seed", Long.toString(seed + 7000000)},
                {"e.snapshot-interval", Integer.toString(interval)}, {"e.machines", "10"},
                {"e.recorded-jobs", "100"}, {"e.warmup-jobs", "50"}, {"e.due-factor", "1.5"},
                {"e.descriptor-jobs", "500"}, {"e.descriptor-warmup", "0"}, {"e.seed-step", "10000"},
                {"lexqd.output-root", output.toString()}, {"lexqd.method", item.label()}, {"lexqd.run-id", "0"},
                {"lexqd.snapshot-interval", Integer.toString(interval)}, {"lexqd.seed-step", "10000"},
                {"generations", Integer.toString(GENERATIONS)}, {"pop.subpop.0.size", Integer.toString(POPULATION)},
                {"breed.elite.0", "2"}, {"seed.0", "19"}, {"num-case", Integer.toString(CASES)},
                {"candidate-size", Integer.toString(POPULATION)}, {"select.tournament.size", "7"},
                {"eval.problem.eval-model.sim-seed", Long.toString(seed)},
                {"eval.problem.eval-model.rotate-sim-seed", Boolean.toString(item.regime == EConfiguration.Regime.rotating)},
                {"eval.problem.eval-model.objectives.0", item.scenario.objective.getName()},
                {"eval.problem.eval-model.sim-models.0.num-machines", "10"},
                {"eval.problem.eval-model.sim-models.0.num-jobs", "100"},
                {"eval.problem.eval-model.sim-models.0.warmup-jobs", "50"},
                {"eval.problem.eval-model.sim-models.0.util-level", Double.toString(item.scenario.utilisation)},
                {"eval.problem.eval-model.sim-models.0.due-date-factor", "1.5"},
                {"eval.problem.eval-model.sim-models.0.min-num-operations", "1"},
                {"eval.problem.eval-model.sim-models.0.max-num-operations", "10"},
                {"eval.problem.eval-model.sim-models.0.replications", "1"},
                {"checkpoint", "false"}, {"quit-on-run-complete", "false"}, {"eval.num-tests", "1"}
        };
        for (String[] setting : settings) { put(parameters, setting[0], setting[1]); }
        if (item.method == EConfiguration.Method.GP) {
            for (String key : new String[]{"breed.reproduce.source.0", "gp.koza.xover.source.0", "gp.koza.mutate.source.0"}) {
                put(parameters, key, "ec.select.TournamentSelection");
            }
        }
        PrintStream console = System.out;
        EvolutionState state = null;
        Throwable primary = null;
        try (PrintStream log = new PrintStream(output.resolve("training.log").toFile(), StandardCharsets.UTF_8.name())) {
            System.setOut(log);
            try {
                state = Evolve.initialize(parameters, 0);
                ((EManagedState) state).setProgress(console);
                state.run(EvolutionState.C_STARTED_FRESH);
                hashChecks(state.population.subpops[0].individuals);
            } catch (Exception | Error failure) { primary = failure; throw failure; }
            finally {
                try {
                    if (state instanceof EManagedState) { ((EManagedState) state).closeResources(); }
                } catch (IOException close) {
                    if (primary != null) { primary.addSuppressed(close); } else { throw close; }
                } finally {
                    if (state != null) { Evolve.cleanup(state); }
                    System.setOut(console);
                }
            }
        } finally { System.setOut(console); }
    }

    private static void verify(Path output, EConfiguration.Case item, Properties manifest) throws Exception {
        check("SEARCH_COMPLETE".equals(manifest.getProperty("status")), "Only driver may seal COMPLETE");
        for (String key : new String[]{"fitness-evaluations", "failed-training-evaluations", "manual-reference-simulations",
                "descriptor-reference-simulations", "total-training-simulations", "elapsed-training-seconds",
                "final-members", "stored-finite-members", "initial-genotype-sha256", "initial-evaluated-population-sha256",
                "initial-training-seed", "final-training-seed", "evaluation-phase", "normalization", "age-penalty",
                "reset-policy", "selection", "data-adaptation"}) { EFiles.required(manifest, key); }
        check(item.kind().equals(manifest.getProperty("output-kind")), "Declared output kind");
        check(item.finalFile().equals(manifest.getProperty("final-file")), "Declared native final file");
        check(Long.parseLong(manifest.getProperty("fitness-evaluations")) == (long) POPULATION * GENERATIONS,
                "Actual evaluated-batch accounting includes population elites");
        check(Long.parseLong(manifest.getProperty("case-value-slots")) == (long) POPULATION * GENERATIONS * CASES,
                "Real diagnostic case slots");
        long seed = Long.parseLong(manifest.getProperty("initial-training-seed"));
        long finalSeed = seed + (item.regime == EConfiguration.Regime.rotating ? (GENERATIONS - 1L) * 10000 : 0);
        check(Long.parseLong(manifest.getProperty("final-training-seed")) == finalSeed, "Native rotation count");
        long reference = Long.parseLong(manifest.getProperty("manual-reference-simulations"));
        long descriptor = Long.parseLong(manifest.getProperty("descriptor-reference-simulations"));
        check(Long.parseLong(manifest.getProperty("total-training-simulations"))
                == (long) POPULATION * GENERATIONS + reference + descriptor, "No hidden simulations");
        PopulationSnapshots.Snapshot initial = PopulationSnapshots.read(output.resolve("initial_population.stat"));
        check(initial.candidates().size() == POPULATION && initial.generation() == 0
                && initial.trainingSeed() == seed, "Paired complete initial population snapshot");
        List<Map<String, String>> generations = csv(output.resolve("generations.csv"));
        check(generations.size() == GENERATIONS, "One metrics row per evaluated batch");
        for (int i = 0; i < GENERATIONS; i++) {
            Map<String, String> row = generations.get(i);
            check(Integer.parseInt(row.get("generation")) == i, "Generation sequence");
            check(Long.parseLong(row.get("training_seed")) ==
                    seed + (item.regime == EConfiguration.Regime.rotating ? i * 10000L : 0), "Per-generation rotation");
            check(Long.parseLong(row.get("fitness_evaluations")) == (i + 1L) * POPULATION, "Per-batch fitness count");
        }
        if (item.method == EConfiguration.Method.QDGP) {
            FinalArchiveReader.Archive archive = new FinalArchiveReader().read(output.resolve(item.finalFile()));
            check(archive.declaredCount() == Integer.parseInt(manifest.getProperty("final-members")), "Native archive reader");
            check(Integer.parseInt(manifest.getProperty("native-grid-cells")) == 6561, "True 81x81 capacity-one grid");
            check(archive.declaredCount() == Integer.parseInt(manifest.getProperty("native-occupied-cells")), "Native cells");
            check(reference == GENERATIONS + (item.regime == EConfiguration.Regime.rotating ? GENERATIONS - 1 : 0),
                    "Actual reference runs agree with inherited QD phase timing");
            check(descriptor == 2, "Two independent descriptor reference replays");
            for (Map<String, String> row : csv(output.resolve("final_archive.csv"))) {
                long birthSeed = Long.parseLong(row.get("birth_training_seed"));
                int birthGeneration = Integer.parseInt(row.get("birth_generation"));
                check(birthSeed == seed + (item.regime == EConfiguration.Regime.rotating ? birthGeneration * 10000L : 0),
                        "QD offspring birth seeds reflect evaluation BEFORE rotation");
                check(Long.parseLong(row.get("current_training_seed")) == finalSeed, "Separate current vs birth seed");
                verifyCases(row, "raw_training_fitness", item.scenario.objective);
            }
        } else {
            PopulationSnapshots.Snapshot snapshot = PopulationSnapshots.read(output.resolve(item.finalFile()));
            check(snapshot.candidates().size() == POPULATION && snapshot.trainingSeed() == finalSeed
                    && snapshot.generation() == GENERATIONS - 1, "Complete final population, no history union");
            check(reference == 0 && descriptor == 0, "Population models do not simulate archive references");
            for (Map<String, String> row : csv(output.resolve("final_population.csv"))) {
                verifyCases(row, "raw_objective", item.scenario.objective);
                close(Double.parseDouble(row.get("raw_objective")), Double.parseDouble(row.get("source_comparison_score")));
            }
        }
        for (Map<String, String> row : csv(output.resolve("initial_population.csv"))) {
            verifyCases(row, "raw_objective", item.scenario.objective);
        }
    }

    private static void verifyCases(Map<String, String> row, String rawKey, Objective objective) {
        double raw = Double.parseDouble(row.get(rawKey));
        if (!EAdapterSupport.finite(raw)) {
            for (int i = 0; i < CASES; i++) { check(!EAdapterSupport.finite(Double.parseDouble(row.get("case_" + i))), "Failed case"); }
            return;
        }
        double[] values = new double[CASES];
        for (int i = 0; i < CASES; i++) { values[i] = Double.parseDouble(row.get("case_" + i)); }
        close(OneInstanceEvaluation.aggregate(values, objective), raw);
    }
    private static void caseChecks() {
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Job job = new Job(100 + i, new ArrayList<>(), 0, 0, 0, 2);
            job.setCompletionTime(10 * (i + 1)); jobs.add(job);
        }

        Collections.reverse(jobs);
        for (Objective objective : new Objective[]{Objective.MEAN_TARDINESS, Objective.MAX_FLOWTIME,
                Objective.MEAN_WEIGHTED_FLOWTIME}) {
            double[] values = OneInstanceEvaluation.caseValues(jobs, 100, 4, 2, objective);
            close(OneInstanceEvaluation.aggregate(values, objective),
                    objective == Objective.MAX_FLOWTIME ? 40 : objective == Objective.MEAN_TARDINESS ? 25 : 50);
        }
        jobs.set(0, jobs.get(1));
        try { OneInstanceEvaluation.caseValues(jobs, 100, 4, 2, Objective.MEAN_TARDINESS); throw new AssertionError("Duplicate ID"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("duplicate"), "Duplicate-ID guard"); }
    }

    private static void evaluationResetCheck(Path module) throws IOException {
        EvolutionState state = new EvolutionState();
        state.output = Evolve.buildOutput();
        state.parameters = new ParameterDatabase(module.resolve("e-population.params").toFile());
        put(state.parameters, "e.seed-step", "10000");
        put(state.parameters, "num-case", "4");
        put(state.parameters, "eval.problem.eval-model.sim-seed", "17");
        put(state.parameters, "eval.problem.eval-model.sim-models.0.num-jobs", "100");
        put(state.parameters, "eval.problem.eval-model.sim-models.0.warmup-jobs", "50");
        OneInstanceEvaluation population = new OneInstanceEvaluation();
        EGridEvaluation grid = new EGridEvaluation();
        Parameter base = new Parameter("eval.problem.eval-model");
        population.setup(state, base); grid.setup(state, base);
        check(grid.trainingSeed() == population.trainingSeed(), "Paired evaluator current seeds");
        mengxu.complexsimulation.HeterogeneousSimulation popSimulation =
                (mengxu.complexsimulation.HeterogeneousSimulation) population.getSchedulingSet().getSimulations().get(0);
        mengxu.complexsimulation.HeterogeneousSimulation gridSimulation =
                (mengxu.complexsimulation.HeterogeneousSimulation) grid.getSchedulingSet().getSimulations().get(0);
        for (int i = 0; i < 10; i++) {
            close(popSimulation.getSystemState().getWorkCenters().get(i).getProcessingRate(),
                    gridSimulation.getSystemState().getWorkCenters().get(i).getProcessingRate());
            close(popSimulation.getEntryWorkCenterTranserTime(i), gridSimulation.getEntryWorkCenterTranserTime(i));
        }
        List<yimei.jss.rule.AbstractRule> rules = java.util.Arrays.asList(
                new yimei.jss.rule.operation.weighted.WSPT(yimei.jss.rule.RuleType.SEQUENCING),
                new yimei.jss.rule.workcenter.basic.WIQ(yimei.jss.rule.RuleType.ROUTING));
        popSimulation.setSequencingRule(rules.get(0)); popSimulation.setRoutingRule(rules.get(1));
        gridSimulation.setSequencingRule(rules.get(0)); gridSimulation.setRoutingRule(rules.get(1));
        popSimulation.reset(17); gridSimulation.reset(17);
        close(popSimulation.getSystemState().getJobsInSystem().get(0).getArrivalTime(),
                gridSimulation.getSystemState().getJobsInSystem().get(0).getArrivalTime());
        popSimulation.run(); gridSimulation.run();
        close(popSimulation.objectiveValue(Objective.MEAN_TARDINESS),
                gridSimulation.objectiveValue(Objective.MEAN_TARDINESS));
        double directRaw = popSimulation.objectiveValue(Objective.MEAN_TARDINESS);
        LexicaseFitness first = new LexicaseFitness();
        population.evaluate(Collections.singletonList(first), rules, state);
        close(first.fitness(), directRaw);
        ec.multiobjective.MultiObjectiveFitness second = new ec.multiobjective.MultiObjectiveFitness();
        second.setup(state, new Parameter("pop.subpop.0.species.fitness"));
        check(grid.trainingSeed() == population.trainingSeed(), "Paired evaluator seeds before native delegate");
        grid.evaluate(Collections.singletonList(second), rules, state);
        close(first.fitness(), second.fitness());
        LexicaseFitness repeated = new LexicaseFitness();
        population.evaluate(Collections.singletonList(repeated), rules, state);
        close(first.fitness(), repeated.fitness());
        grid.evaluate(Collections.singletonList(second), rules, state);
        close(first.fitness(), second.fitness());
        state.output.close();
    }

    private static void hashChecks(Individual[] individuals) {
        Individual[] reversed = individuals.clone();
        Collections.reverse(java.util.Arrays.asList(reversed));
        check(EPopulationStatistics.genotypeHash(individuals).equals(EPopulationStatistics.genotypeHash(reversed)),
                "Genotype multiset ignores population sorting");
        check(EPopulationStatistics.evaluatedPopulationHash(individuals)
                .equals(EPopulationStatistics.evaluatedPopulationHash(reversed)), "Evaluated tuple multiset ignores order");
        Individual member = (Individual) individuals[0].clone();
        check(!EPopulationStatistics.genotypeHash(new Individual[]{member})
                .equals(EPopulationStatistics.genotypeHash(new Individual[]{member, member})), "Multiset retains duplicates");
        String failedHash = null;
        for (double raw : new double[]{Double.MAX_VALUE, Double.POSITIVE_INFINITY}) {
            member.fitness = new ec.multiobjective.MultiObjectiveFitness() {
                @Override public double fitness() { return raw; }
            };
            String hash = EPopulationStatistics.evaluatedPopulationHash(new Individual[]{member});
            if (failedHash == null) { failedHash = hash; }
            else { check(failedHash.equals(hash), "Failed native fitness values canonicalize to FAIL"); }
        }
    }

    private static void scalarSelectionCheck() {
        EvolutionState state = new EvolutionState();
        state.parameters = new ParameterDatabase();
        put(state.parameters, "select.tournament.size", "7");
        state.population = new Population(); state.population.subpops = new Subpopulation[]{new Subpopulation()};
        state.population.subpops[0].individuals = new Individual[2];
        for (int i = 0; i < 2; i++) {
            Individual member = new ec.gp.GPIndividual();
            LexicaseFitness fitness = new LexicaseFitness();
            fitness.setEvaluation(i + 1, new double[]{100 - i * 99, 100 - i * 99},
                    Objective.MEAN_TARDINESS, 0, 17);
            member.fitness = fitness; state.population.subpops[0].individuals[i] = member;
        }
        ec.select.TournamentSelection selection = new ec.select.TournamentSelection();
        selection.setup(state, new Parameter("select.tournament"));
        state.random = new ec.util.MersenneTwisterFast[]{new ec.util.MersenneTwisterFast(42)};
        int[] before = new int[100];
        for (int i = 0; i < before.length; i++) { before[i] = selection.produce(0, state, 0); }
        for (int i = 0; i < 2; i++) {
            LexicaseFitness fitness = (LexicaseFitness) state.population.subpops[0].individuals[i].fitness;
            fitness.multiInstanceMultiObjectiveFitness = new double[][]{{i * 1000}, {i * 1000}};
        }
        state.random[0] = new ec.util.MersenneTwisterFast(42);
        for (int i = 0; i < before.length; i++) {
            check(before[i] == selection.produce(0, state, 0), "Scalar GP tournament ignores diagnostic case vectors");
        }
    }

    private static void allFailedChecks() {
        LexicaseFitness failed = new LexicaseFitness();
        failed.setEvaluation(Double.POSITIVE_INFINITY, new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY},
                Objective.MEAN_TARDINESS, 0, 17);
        Individual individual = new ec.gp.GPIndividual(); individual.fitness = failed; individual.evaluated = true;
        Population population = new Population(); population.subpops = new Subpopulation[]{new Subpopulation()};
        population.subpops[0].individuals = new Individual[]{individual};
        EPopulationState state = new EPopulationState(); state.population = population;
        EPopulationStatistics populationStatistics = new EPopulationStatistics();
        populationStatistics.children = new ec.Statistics[0];
        try { populationStatistics.postEvaluationStatistics(state); throw new AssertionError("All-failed population"); }
        catch (IllegalStateException expected) { check(expected.getMessage().contains("all population"), "All-failed population guard"); }
        EGridState grid = new EGridState(); grid.population = population; grid.gridPCmap = new EGridMap(81, 3);
        EGridStatistics gridStatistics = new EGridStatistics();
        gridStatistics.children = new ec.Statistics[0];
        try { gridStatistics.postEvaluationStatistics(grid); throw new AssertionError("All-failed grid"); }
        catch (IllegalStateException expected) { check(expected.getMessage().contains("all QD population"), "All-failed grid guard"); }
    }
    private static List<Map<String, String>> csv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> header = fields(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = fields(lines.get(i));
            check(values.size() == header.size(), "CSV width " + path);
            Map<String, String> row = new HashMap<>();
            for (int j = 0; j < header.size(); j++) { row.put(header.get(j), values.get(j)); }
            rows.add(row);
        }
        return rows;
    }
    private static List<String> fields(String line) {
        List<String> values = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
                else { quoted = !quoted; }
            } else if (ch == ',' && !quoted) { values.add(field.toString()); field.setLength(0); }
            else { field.append(ch); }
        }
        check(!quoted, "Unclosed CSV quote"); values.add(field.toString()); return values;
    }
    private static void put(ParameterDatabase parameters, String key, String value) { parameters.set(new Parameter(key), value); }
    private static void close(double actual, double expected) {
        check(Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)), "Expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
