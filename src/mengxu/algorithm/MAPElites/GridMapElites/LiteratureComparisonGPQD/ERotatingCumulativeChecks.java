package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import mengxu.algorithm.MAPElites.GridMapElites.gridPCmap;
import mengxu.complexsimulation.ruleevaluation.MultipleTreeMultipleRuleHeterogeneousEvaluationModel;
import yimei.jss.jobshop.Objective;
import yimei.jss.simulation.DynamicSimulation;

public final class ERotatingCumulativeChecks {
    private ERotatingCumulativeChecks() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) { throw new IllegalArgumentException("ERotatingCumulativeChecks <rotating-config>"); }
        ERotatingCumulativeReplay.Options options = ERotatingCumulativeReplay.Options.parse(
                new String[]{"--config", args[0]});
        aging(); references(options.study); guards(args[0]);
        ECheckpointAnalysis.Moments moments = new ECheckpointAnalysis.Moments();
        moments.add(1); moments.add(3);
        same(moments.mean(), 2, "Run mean"); same(moments.sd(), Math.sqrt(2), "Sample SD");
        System.out.println("PASS: native age5 replacement/ties, native Best versus stored minimum,"
                + " failed cells/coverage/mean, rotated-job legacy benchmark parity, fixed shop, mode/output guards."
                + " Unit cost:18 benchmark and6 descriptor-reference simulations; no GP evolution.");
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
    static void add(ECumulativeMap.NativeState state, int cell, double value) {
        int x = cell / 81, y = cell % 81;
        double[] digits = new double[8];
        for (int j = 3; j >= 0; j--) { digits[j] = x % 3; x /= 3; digits[j + 4] = y % 3; y /= 3; }
        state.gridPCmap.updateGridMap(state, new Individual[]{individual(value)}, new double[][]{digits},
                new double[]{value});
    }
    static void aging() {
        ECumulativeMap.NativeState state = new ECumulativeMap.NativeState();
        state.penalty_level = 5; state.reevaluateGridMap = false; state.gridPCmap = new gridPCmap(81, 3, 1);
        add(state, 0, 1); state.generation = 1; add(state, 1, 2);
        ERotatingCumulativeMap.Metrics m = ERotatingCumulativeMap.metrics(state, 10);
        same(m.mean(), 1.5, "Mean is stored-score mean, not age-adjusted mean");
        same(m.minimumStored(), 1, "Stored minimum retained separately");
        same(m.best(), 2, "Native Best selects recent2 over old1 aged to6");
        same(m.bestAged(), 2, "Native selected member aged score");
        check(m.bestGeneration() == 1, "Native selected generation");
        add(state, 0, 2);
        same(state.gridPCmap.fitnesses_each_cell[0][0].get(0), 2, "New worse raw score may replace aged old score");
        check(state.gridPCmap.indsFromGen_each_cell[0][0].get(0) == 1, "Aged replacement updates birth");
        state.generation = 2; add(state, 0, 12);
        check(state.gridPCmap.indsFromGen_each_cell[0][0].get(0) == 1, "Equal aged score does not replace");
        add(state, 5, Double.POSITIVE_INFINITY); add(state, 0, Double.POSITIVE_INFINITY);
        m = ERotatingCumulativeMap.metrics(state, 10);
        check(m.occupied() == 3 && m.finite() == 2 && m.failed() == 1, "Failed occupied cells retained");
        same(m.mean(), 2, "Native mean excludes failed cells");
        same(m.coverage(), 300.0 / 6561, "Coverage counts occupied cells once");
        for (int i = 0; i <= 600; i++) { add(state, i, 2); }
        check(state.gridPCmap.getNumberIndividualsInGridMap() == 601, "Cumulative map has no500-member cap");
        state.gridPCmap = new gridPCmap(81, 3, 1);
        try {
            ERotatingCumulativeMap.metrics(state, 1);
            throw new AssertionError("Empty native quality accepted");
        } catch (IllegalStateException expected) { check(expected.getMessage().contains("Unreportable"), "Empty-quality error"); }
    }
    static void references(EConfiguration c) throws IOException {
        for (int run : new int[]{0, 7, 29}) {
            EvolutionState raw = new EvolutionState(); raw.output = Evolve.buildOutput();
            try {
                raw.parameters = ETraining.parameters(c, ERotatingCumulativeReplay.item(run),
                        Path.of("unused-rotating-unit-output"), "unused", "unused");
                MultipleTreeMultipleRuleHeterogeneousEvaluationModel nativeModel =
                        new MultipleTreeMultipleRuleHeterogeneousEvaluationModel();
                nativeModel.setup(raw, new Parameter("eval.problem.eval-model"));
                DynamicSimulation nativeSimulation = (DynamicSimulation) nativeModel.getSchedulingSet().getSimulations().get(0);
                long initial = c.trainingSeed(ERotatingCumulativeReplay.item(run));
                ERotatingCumulativeMap map = new ERotatingCumulativeMap(c, initial, raw.parameters);
                double[] rates = map.reference.getSystemState().getWorkCenters().stream()
                        .mapToDouble(w -> w.getProcessingRate()).toArray();
                double first = Double.NaN, last = Double.NaN;
                for (int generation : new int[]{0, 20, 200}) {
                    long seed = initial + generation * c.seedStep;
                    nativeSimulation.reset(seed);
                    nativeModel.getSchedulingSet().lowerBoundsFromBenchmarkRule(
                            Collections.singletonList(Objective.MEAN_TARDINESS));
                    double expected = nativeModel.getSchedulingSet().getObjectiveLowerBoundMtx().getEntry(0, 0);
                    double actual = map.benchmark(seed);
                    same(actual, expected, "Native rotating reference at generation " + generation);
                    same(map.reference.getSeed(), seed, "Current job-stream seed");
                    for (int machine = 0; machine < c.machines; machine++) {
                        same(map.reference.getSystemState().getWorkCenters().get(machine).getProcessingRate(),
                                rates[machine], "Reference shop unchanged by seed rotation");
                        same(nativeSimulation.getSystemState().getWorkCenters().get(machine).getProcessingRate(),
                                rates[machine], "Native and replay physical shops match");
                    }
                    if (generation == 0) { first = actual; }
                    last = actual;
                }
                check(Double.compare(first, last) != 0, "Rotating benchmark must not accidentally cache initial reference");
            } finally { raw.output.close(); }
        }
    }
    static void guards(String config) throws IOException {
        try {
            ERotatingCumulativeReplay.Options.parse(new String[]{"--config", config, "--smoke", "--verify-run-zero"});
            throw new AssertionError("Conflicting modes accepted");
        } catch (IOException expected) { check(expected.getMessage().contains("one execution mode"), "Mode diagnostic"); }
        ERotatingCumulativeReplay.Options options = ERotatingCumulativeReplay.Options.parse(
                new String[]{"--config", config, "--summarise", "--resume"});
        Path temporary = Files.createTempDirectory("rotating-summary-readonly-");
        options.output = temporary.resolve("must-not-exist");
        try {
            ERotatingCumulativeReplay.checkedRoot(options);
            throw new AssertionError("Missing summary accepted");
        } catch (IOException expected) {
            check(expected.getMessage().contains("No completed rotating replay"), "Summary diagnostic");
            check(!Files.exists(options.output), "Read-only summary created output");
        } finally { Files.delete(temporary); }
        options.output = options.source.getParent().resolve("experiment-E-lexicase-cumulative-v1");
        check(Files.isRegularFile(options.output.resolve("manifest.properties")), "Completed fixed output must exist");
        try {
            ERotatingCumulativeReplay.checkedRoot(options);
            throw new AssertionError("Fixed outputs reused by rotating replay");
        } catch (IOException expected) { check(expected.getMessage().contains("not owned"), "Fixed-output protection"); }
    }
}
