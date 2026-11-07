package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Collections;
import mengxu.algorithm.MAPElites.GridMapElites.gridPCmap;
import mengxu.complexsimulation.ruleevaluation.MultipleTreeMultipleRuleHeterogeneousEvaluationModel;
import yimei.jss.jobshop.Objective;
import yimei.jss.simulation.Simulation;

public final class ECumulativeChecks {
    private ECumulativeChecks() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) { throw new IllegalArgumentException("ECumulativeChecks <cumulative-study.properties>"); }
        ECumulativeReplay.Options options = ECumulativeReplay.Options.parse(new String[]{"--config", args[0]});
        nativeArchive();
        normaliser(options.study);
        ECheckpointAnalysis.Moments moments = new ECheckpointAnalysis.Moments();
        moments.add(1); moments.add(3);
        same(moments.mean(), 2, "Run-level mean");
        same(moments.sd(), Math.sqrt(2), "Sample SD denominator");
        try {
            ECumulativeReplay.Options.parse(new String[]{"--config", args[0], "--smoke", "--verify-run-zero"});
            throw new AssertionError("Multiple modes accepted");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().contains("one execution mode"), "Explicit invalid-mode diagnostic");
        }
        Path temporary = Files.createTempDirectory("cumulative-readonly-check-");
        ECumulativeReplay.Options summary = ECumulativeReplay.Options.parse(
                new String[]{"--config", args[0], "--summarise", "--resume"});
        summary.output = temporary.resolve("must-not-be-created");
        try {
            ECumulativeReplay.run(summary);
            throw new AssertionError("Summary accepted nonexistent replay");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().contains("No completed replay"), "Explicit missing-summary diagnostic");
            check(!Files.exists(summary.output), "Read-only summary must not create output");
        } finally { Files.delete(temporary); }
        System.out.println("PASS: native accumulation, strict cell replacement/ties, duplicates, failed retained cells,"
                + " archive mean/minimum, capacity-one coverage beyond500, legacy benchmark phase, run sample SD."
                + " Unit work: 9 benchmark simulations and 6 native descriptor replays; no GP evolution.");
    }

    static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
    static void same(double actual, double expected, String message) {
        check(Double.compare(actual, expected) == 0, message + ": " + actual + " != " + expected);
    }
    static Individual individual(double value) {
        GPIndividual member = new GPIndividual(); member.trees = new GPTree[0];
        MultiObjectiveFitness fitness = new MultiObjectiveFitness();
        fitness.objectives = new double[]{value}; fitness.maximize = new boolean[]{false};
        member.fitness = fitness; return member;
    }
    static double[] coordinate(int cell) {
        int x = cell / 81, y = cell % 81;
        double[] result = new double[8];
        for (int j = 3; j >= 0; j--) { result[j] = x % 3; x /= 3; result[j + 4] = y % 3; y /= 3; }
        return result;
    }
    static void add(ECumulativeMap.NativeState state, int cell, double value) {
        state.gridPCmap.updateGridMap(state, new Individual[]{individual(value)},
                new double[][]{coordinate(cell)}, new double[]{value});
    }
    static void nativeArchive() {
        ECumulativeMap.NativeState state = new ECumulativeMap.NativeState();
        state.penalty_level = 0; state.reevaluateGridMap = false; state.gridPCmap = new gridPCmap(81, 3, 1);
        add(state, 0, 4); add(state, 0, 8); add(state, 1, 6);
        same(state.gridPCmap.calculateMeanPerformance(), 5, "Cell elites, not population multiplicity");
        state.generation = 1; add(state, 0, 2); add(state, 2, 10);
        same(state.gridPCmap.calculateMeanPerformance(), 6, "Previous cells persist across generations");
        same(state.gridPCmap.calculateBestPerformance(state), 2, "Native archived minimum");
        state.generation = 2; add(state, 0, 2);
        check(state.gridPCmap.indsFromGen_each_cell[0][0].get(0) == 1, "Ties keep the earlier retained entry");
        add(state, 3, Double.POSITIVE_INFINITY);
        same(state.gridPCmap.calculateMeanPerformance(), 6, "Native mean excludes failed cell scores");
        check(state.gridPCmap.getNumberIndividualsInGridMap() == 4, "Failed occupied cells count for coverage");
        for (int cell = 4; cell <= 600; cell++) { add(state, cell, 2); }
        check(state.gridPCmap.getNumberIndividualsInGridMap() == 601, "Cumulative map exceeds population size");
        check(100.0 * state.gridPCmap.getNumberIndividualsInGridMap() / 6561 > 50000.0 / 6561,
                "No contemporary-population coverage cap");
        gridPCmap empty = new gridPCmap(81, 3, 1);
        check(Double.isNaN(empty.calculateMeanPerformance()), "Empty native finite cohort is not zero");
    }
    static void normaliser(EConfiguration c) {
        for (int run : new int[]{0, 7, 29}) {
            EvolutionState raw = new EvolutionState(); raw.output = Evolve.buildOutput();
            try {
                raw.parameters = ETraining.parameters(c, ECumulativeReplay.item(run),
                        Path.of("unused-cumulative-unit-output"), "unused", "unused");
                MultipleTreeMultipleRuleHeterogeneousEvaluationModel nativeModel =
                        new MultipleTreeMultipleRuleHeterogeneousEvaluationModel();
                nativeModel.setup(raw, new Parameter("eval.problem.eval-model"));
                Simulation simulation = nativeModel.getSchedulingSet().getSimulations().get(0);
                simulation.reset();
                nativeModel.getSchedulingSet().lowerBoundsFromBenchmarkRule(
                        Collections.singletonList(Objective.MEAN_TARDINESS));
                double expected = nativeModel.getSchedulingSet().getObjectiveLowerBoundMtx().getEntry(0, 0);
                ECumulativeMap map = new ECumulativeMap(c, c.trainingSeed(ECumulativeReplay.item(run)), raw.parameters);
                same(map.benchmark(), expected, "Native reference after post-evaluation reset");
                same(map.benchmark(), expected, "Fixed legacy reference repeat");
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            } finally { raw.output.close(); }
        }
    }
}
