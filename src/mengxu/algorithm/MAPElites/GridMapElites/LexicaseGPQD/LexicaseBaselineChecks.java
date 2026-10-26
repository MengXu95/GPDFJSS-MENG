package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.gp.GPIndividual;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import yimei.jss.jobshop.Job;
import yimei.jss.jobshop.Objective;

public final class LexicaseBaselineChecks {
    private LexicaseBaselineChecks() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) { throw new IllegalArgumentException("LexicaseBaselineChecks <module-directory>"); }
        Path module = Paths.get(args[0]).toRealPath();
        Path root = Files.createTempDirectory("lexicase-population-checks-");
        try {
            caseChecks();
            defaultSimulationChecks(module);
            fitnessAndSelectionChecks();
            Path fixed = root.resolve("fixed");
            Path rotating = root.resolve("rotating");
            Path otherInterval = root.resolve("other-interval");
            train(module, fixed, true, "mean-tardiness", 4, 20, 2);
            train(module, rotating, false, "mean-tardiness", 4, 20, 2);
            train(module, otherInterval, true, "mean-tardiness", 4, 20, 999);
            checkRun(fixed, false, 4, 20);
            checkRun(rotating, true, 4, 20);
            check(Files.exists(fixed.resolve("population.gen0000.stat"))
                    && !Files.exists(fixed.resolve("population.gen0001.stat"))
                    && Files.exists(fixed.resolve("population.gen0002.stat"))
                    && Files.exists(fixed.resolve("population.gen0003.stat")), "Periodic and final snapshots");
            check(LexicaseFiles.hash(fixed.resolve("final_population.stat"))
                    .equals(LexicaseFiles.hash(otherInterval.resolve("final_population.stat"))),
                    "Logging frequency must not change the search");
            check(LexicaseFiles.hash(fixed.resolve("population.gen0000.stat"))
                    .equals(LexicaseFiles.hash(otherInterval.resolve("population.gen0000.stat"))),
                    "Reproducible initialization and evaluation");
            PopulationSnapshots.Snapshot firstFixed = PopulationSnapshots.read(fixed.resolve("population.gen0000.stat"));
            PopulationSnapshots.Snapshot firstRotating = PopulationSnapshots.read(rotating.resolve("population.gen0000.stat"));
            for (int i = 0; i < firstFixed.candidates().size(); i++) {
                check(firstFixed.candidates().get(i).treeHash().equals(firstRotating.candidates().get(i).treeHash()),
                        "Matched initialization across regimes");
                close(firstFixed.candidates().get(i).storedFitness(), firstRotating.candidates().get(i).storedFitness());
            }
            for (String objective : Arrays.asList("max-flowtime", "mean-weighted-flowtime")) {
                Path output = root.resolve(objective);
                train(module, output, true, objective, 2, 10, 20);
                checkRun(output, false, 2, 10);
            }
            try {
                train(module, fixed, true, "mean-tardiness", 4, 20, 2);
                throw new AssertionError("Existing output was accepted");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("overwrite"), "No-overwrite protection");
            }
            LexicaseComparisonChecks.runChecks(root);
            System.out.println("LEXICASE BASELINE CHECKS PASSED: both regimes, 3 objectives, snapshots and common evaluation");
        } finally {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.delete(file);
                }
            }
        }
    }

    private static void caseChecks() {
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Job job = new Job(100 + i, new ArrayList<>(), 0, 0, 0, 2);
            job.setCompletionTime(10 * (i + 1));
            jobs.add(job);
        }
        Collections.reverse(jobs);
        double[] tardiness = OneInstanceEvaluation.caseValues(jobs, 100, 4, 2, Objective.MEAN_TARDINESS);
        close(tardiness[0], 15); close(tardiness[1], 35);
        close(OneInstanceEvaluation.aggregate(tardiness, Objective.MEAN_TARDINESS), 25);
        double[] weighted = OneInstanceEvaluation.caseValues(jobs, 100, 4, 2, Objective.MEAN_WEIGHTED_FLOWTIME);
        close(weighted[0], 30); close(weighted[1], 70);
        double[] maximum = OneInstanceEvaluation.caseValues(jobs, 100, 4, 2, Objective.MAX_FLOWTIME);
        close(maximum[0], 20); close(maximum[1], 40);
        try {
            OneInstanceEvaluation.caseValues(jobs, 100, 4, 3, Objective.MEAN_TARDINESS);
            throw new AssertionError("Unequal case blocks were accepted");
        } catch (IllegalArgumentException expected) { check(expected.getMessage().contains("equally"), "Case size guard"); }
        jobs.set(0, jobs.get(1));
        try {
            OneInstanceEvaluation.caseValues(jobs, 100, 4, 2, Objective.MEAN_TARDINESS);
            throw new AssertionError("Duplicate jobs were accepted");
        } catch (IllegalArgumentException expected) { check(expected.getMessage().contains("duplicate"), "Job identity guard"); }
    }

    private static LexicaseFitness fitness(double raw, double first, double second) {
        LexicaseFitness fitness = new LexicaseFitness();
        fitness.setEvaluation(raw, new double[]{first, second}, Objective.MEAN_TARDINESS, 0, 17);
        return fitness;
    }

    private static void defaultSimulationChecks(Path module) throws IOException {
        ec.EvolutionState state = new ec.EvolutionState();
        state.parameters = new ParameterDatabase(module.resolve("lexicase-gpf.params").toFile());
        OneInstanceEvaluation model = new OneInstanceEvaluation();
        model.setup(state, new Parameter("eval.problem.eval-model"));
        List<yimei.jss.rule.AbstractRule> rules = Arrays.asList(
                new yimei.jss.rule.operation.weighted.WSPT(yimei.jss.rule.RuleType.SEQUENCING),
                new yimei.jss.rule.workcenter.basic.WIQ(yimei.jss.rule.RuleType.ROUTING));
        LexicaseFitness first = new LexicaseFitness();
        model.evaluate(Collections.singletonList(first), rules, state);
        check(first.isValidEvaluation() && first.multiInstanceMultiObjectiveFitness.length == 25,
                "1000 recorded jobs plus 500 warmup jobs produce 25 valid cases");
        state.generation = 1;
        LexicaseFitness second = new LexicaseFitness();
        model.evaluate(Collections.singletonList(second), rules, state);
        close(first.fitness(), second.fitness());
        for (int i = 0; i < 25; i++) { close(first.getFitness(i, 0), second.getFitness(i, 0)); }
        check(model.simulationCount() == 2, "One simulation per case vector, including the first individual");
    }

    private static void fitnessAndSelectionChecks() {
        LexicaseFitness value = fitness(2, 1, 3);
        close(value.getObjectives()[0], 2);
        close(value.fitness(), 2);
        LexicaseFitness copy = (LexicaseFitness) value.clone();
        copy.multiInstanceMultiObjectiveFitness[0][0] = 99;
        close(value.getFitness(0, 0), 1);
        LexicaseEvolutionState state = new LexicaseEvolutionState();
        state.parameters = new ParameterDatabase();
        String[][] settings = {{"num-case", "2"}, {"candidate-size", "3"}, {"pop.subpop.0.size", "3"},
                {"tournament-size", "7"}, {"elitist-selection-rate", "1"}, {"pro-lexicase", "1"}};
        for (String[] setting : settings) { state.parameters.set(new Parameter(setting[0]), setting[1]); }
        state.random = new MersenneTwisterFast[]{new MersenneTwisterFast(42)};
        state.population = new Population();
        state.population.subpops = new Subpopulation[]{new Subpopulation()};
        state.population.subpops[0].individuals = new Individual[3];
        for (int i = 0; i < 3; i++) {
            GPIndividual individual = new GPIndividual();
            individual.fitness = i == 0 ? fitness(1, 1, 1) : i == 1 ? fitness(10, 10, 10)
                    : fitness(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            state.population.subpops[0].individuals[i] = individual;
        }
        state.allGenerationCaseNumber.add(new double[2]);
        PopulationLexicaseSelection selector = new PopulationLexicaseSelection();
        selector.setup(state, new Parameter("select.lexicase"));
        for (int i = 0; i < 50; i++) { check(selector.produce(0, state, 0) == 0, "Finite lexicase case filtering"); }
        for (Individual individual : state.population.subpops[0].individuals) {
            individual.fitness = fitness(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
        try {
            selector.produce(0, state, 0);
            throw new AssertionError("All-failed population was selected");
        } catch (IllegalStateException expected) { check(expected.getMessage().contains("No valid"), "Explicit failed-population error"); }
    }

    private static void train(Path module, Path output, boolean fixed, String objective,
                              int generations, int population, int interval) throws IOException {
        List<String> arguments = new ArrayList<>(Arrays.asList("--params",
                module.resolve(fixed ? "lexicase-gpf.params" : "lexicase-gp.params").toString(),
                "--output-root", output.toString()));
        String[] settings = {"generations=" + generations, "pop.subpop.0.size=" + population,
                "candidate-size=" + population, "breed.elite.0=2", "seed.0=19",
                "eval.problem.eval-model.sim-seed=17", "num-case=4",
                "eval.problem.eval-model.objectives.0=" + objective,
                "eval.problem.eval-model.sim-models.0.num-jobs=40",
                "eval.problem.eval-model.sim-models.0.warmup-jobs=0",
                "lexqd.snapshot-interval=" + interval};
        for (String setting : settings) { arguments.add("--set"); arguments.add(setting); }
        LexicaseQDRun.main(arguments.toArray(new String[0]));
    }

    private static void checkRun(Path output, boolean rotating, int count, int population) throws IOException {
        List<String> rows = Files.readAllLines(output.resolve("generations.csv"), StandardCharsets.UTF_8);
        check(rows.size() == count + 1, "One metrics row per evaluated batch");
        for (int i = 0; i < count; i++) {
            String[] row = rows.get(i + 1).split(",");
            check(Integer.parseInt(row[2]) == i, "Zero-based generation");
            check(Long.parseLong(row[3]) == 17L + (rotating ? i * 10000L : 0), "Training instance rotation");
            check(Long.parseLong(row[9]) == (long) (i + 1) * population, "Exact simulation accounting, including elites");
        }
        PopulationSnapshots.Snapshot snapshot = PopulationSnapshots.read(output.resolve("final_population.stat"));
        check(snapshot.candidates().size() == population && snapshot.generation() == count - 1, "Complete final population");
        check(snapshot.trainingSeed() == 17L + (rotating ? (count - 1) * 10000L : 0), "No stale final fitness");
        for (mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate candidate : snapshot.candidates()) {
            check(Double.isFinite(candidate.storedFitness()), "Finite bounded integration fitness");
        }
        String manifest = new String(Files.readAllBytes(output.resolve("manifest.properties")), StandardCharsets.UTF_8);
        check(manifest.contains("status=COMPLETE") && manifest.contains("history-archive=none"), "Honest completed output metadata");
    }

    private static void close(double actual, double expected) {
        check(Math.abs(actual - expected) < 1e-8, "Expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
