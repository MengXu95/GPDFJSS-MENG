package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Evolve;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import yimei.jss.rule.AbstractRule;
import yimei.jss.simulation.DynamicSimulation;

/** Standalone bounded checks; references only, never GP evolution or formal training markers. */
public final class ECheckpointChecks {
    private ECheckpointChecks() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) { throw new IllegalArgumentException("ECheckpointChecks <completed-E-root>"); }
        EConfiguration c = EConfiguration.parse(new String[]{"--config",
                Path.of(args[0]).resolve("training").resolve("resolved-study.properties").toString()});
        accounting(); numeric(); csv(); projectionFailures(); referenceParity(c);
        System.out.println("PASS: whole-population/duplicates/coverage/failures/normalisation/cache/fixed-shop/native EGrid reset parity");
    }
    static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
    static void same(double actual, double expected, String message) {
        check(Double.compare(actual, expected) == 0, message + " actual=" + actual + " expected=" + expected);
    }
    static ECheckpointAnalysis.Projection pc(int seq, int rout) {
        return new ECheckpointAnalysis.Projection(new double[8], new int[]{seq, rout}, "");
    }
    static void accounting() {
        ECheckpointAnalysis.SnapshotMetrics m = new ECheckpointAnalysis.SnapshotMetrics();
        m.add(2, pc(0, 0)); m.add(100, pc(0, 0)); m.add(2, pc(1, 0));
        check(Math.abs(m.conditionalMean() - 104.0 / 3) < 1e-12, "All members, not two cell winners");
        check(m.members == 3 && m.finiteN == 3 && m.occupied.size() == 2, "Duplicate multiplicity retained");
        same(m.coverage(), 200.0 / 6561, "Fixed coverage denominator");
        for (int i = 3; i < 500; i++) { m.add(1, pc(i / 81, i % 81)); }
        check(m.members == 500 && m.occupied.size() <= 500 && m.coverage() <= 50000.0 / 6561,
                "500-member snapshot cannot exceed population coverage cap");
        ECheckpointAnalysis.SnapshotMetrics failed = new ECheckpointAnalysis.SnapshotMetrics();
        failed.add(Double.POSITIVE_INFINITY, pc(0, 0)); failed.add(Double.MAX_VALUE, pc(1, 0));
        check(failed.finiteN == 0 && failed.failedN == 2 && Double.isNaN(failed.best)
                && Double.isNaN(failed.conditionalMean()), "No finite score is undefined, never zero");
        same(failed.inclusiveMean(), Double.POSITIVE_INFINITY, "Failure-inclusive mean");
        failed.add(4, new ECheckpointAnalysis.Projection(null, null, "NUMERIC_FAILURE"));
        check(failed.finiteN == 1 && failed.projected == 2 && failed.projectionFailures == 1
                && failed.members == failed.projected + failed.projectionFailures, "Projection independent of quality mean");
        same(failed.conditionalMean(), 4, "Finite raw retained despite PC failure");
    }
    static void numeric() {
        same(ECheckpointAnalysis.normalise(6, 3), 2, "raw/reference");
        same(ECheckpointAnalysis.normalise(Double.MAX_VALUE, 3), Double.POSITIVE_INFINITY, "MAX_DOUBLE failure");
        same(ECheckpointAnalysis.normalise(Double.POSITIVE_INFINITY, 3), Double.POSITIVE_INFINITY, "Infinite failure");
        for (double ref : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
            try { ECheckpointAnalysis.normalise(5, ref); throw new AssertionError("Invalid reference accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        ECheckpointAnalysis.Moments moments = new ECheckpointAnalysis.Moments();
        moments.add(1); moments.add(3); same(moments.mean(), 2, "Run mean");
        same(moments.sd(), Math.sqrt(2), "Sample, not population SD");
        moments.add(Double.POSITIVE_INFINITY);
        check(moments.mean() == Double.POSITIVE_INFINITY && Double.isNaN(moments.sd()), "Infinity group SD unavailable");
        check(Arrays.equals(ETest.cells(new double[]{0, 1, 2, 0, 2, 1, 0, 2}), new int[]{15, 65}), "Zero-based PC digits");
        try { ETest.cells(new double[]{Double.NaN, 0, 0, 0, 0, 0, 0, 0}); throw new AssertionError("NaN cell"); }
        catch (IllegalArgumentException expected) { }
    }
    static void csv() throws Exception {
        check(ECheckpointAnalysis.csvLine("a,\"b,c\",\"d\"\"e\",").equals(Arrays.asList("a", "b,c", "d\"e", "")),
                "CSV quoting");
    }
    static void projectionFailures() {
        ETest.ProjectionService bank = new ETest.ProjectionService() {
            public double[] coordinates(ArchiveCandidate candidate) { throw new IllegalArgumentException("nonfinite priority"); }
            public void write(Path path) { }
            public long simulationCount() { return 0; }
        };
        ArchiveCandidate member = new ArchiveCandidate(Path.of("fixture.stat"), 0, 7, "PT", "WIQ");
        ECheckpointAnalysis.Projection p = ECheckpointAnalysis.Projection.of(bank, member);
        check(p.cell == null && p.digits == null && p.failure.contains("nonfinite priority"),
                "Unprojectable member must be retained without fabricated coordinates");
    }
    static void referenceParity(EConfiguration c) throws Exception {
        for (int run : new int[]{0, 7, 29}) {
            EConfiguration.Case item = new EConfiguration.Case(EConfiguration.Method.QDGP,
                    EConfiguration.Regime.rotating, EConfiguration.Scenario.T085, run);
            EvolutionState state = new EvolutionState();
            state.output = Evolve.buildOutput();
            state.parameters = ETraining.parameters(c, item, Path.of("unused-checkpoint-check"), "unused", "unused");
            EGridEvaluation model = new EGridEvaluation();
            model.setup(state, new Parameter("eval.problem.eval-model"));
            DynamicSimulation nativeSimulation = (DynamicSimulation) model.getSchedulingSet().getSimulations().get(0);
            ECheckpointAnalysis.ReferenceCache refs = new ECheckpointAnalysis.ReferenceCache(
                    new ECheckpointAnalysis.Benchmark(c, c.trainingSeed(item)));
            double[] initialRates = refs.benchmark.getSystemState().getWorkCenters().stream()
                    .mapToDouble(w -> w.getProcessingRate()).toArray();
            double[] initialTransfers = new double[c.machines];
            for (int machine = 0; machine < c.machines; machine++) {
                initialTransfers[machine] = refs.benchmark.getEntryWorkCenterTranserTime(machine);
                same(initialRates[machine], nativeSimulation.getSystemState().getWorkCenters().get(machine).getProcessingRate(),
                        "Paired initial shop processing rate");
            }
            for (int generation : new int[]{0, 20, 200}) {
                long seed = c.trainingSeed(item) + generation * c.seedStep;
                double reference = refs.get(seed);
                same(refs.get(seed), reference, "Reference cache repeat");
                nativeSimulation.reset(seed);
                MultiObjectiveFitness fitness = new MultiObjectiveFitness();
                fitness.objectives = new double[]{0}; fitness.maximize = new boolean[]{false};
                fitness.minObjective = new double[]{0}; fitness.maxObjective = new double[]{Double.MAX_VALUE};
                java.util.List<AbstractRule> rules = Arrays.asList(ECheckpointAnalysis.OBJECTIVE.benchmarkSequencingRule(),
                        ECheckpointAnalysis.OBJECTIVE.benchmarkRoutingRule());
                state.generation = generation;
                model.evaluate(Collections.singletonList(fitness), rules, state);
                same(reference, fitness.fitness(), "Exact native EGrid benchmark evaluation run=" + run + " gen=" + generation);
                for (int machine = 0; machine < c.machines; machine++) {
                    same(initialRates[machine], refs.benchmark.getSystemState().getWorkCenters().get(machine).getProcessingRate(),
                            "Rotating job streams preserve initial shop");
                    same(initialTransfers[machine], refs.benchmark.getEntryWorkCenterTranserTime(machine),
                            "Rotating streams preserve transfer-time controls");
                }
            }
            check(refs.benchmark.simulations == 3, "Rotating cache requires one reference per distinct seed");
            refs.get(c.trainingSeed(item)); refs.get(c.trainingSeed(item));
            check(refs.benchmark.simulations == 3, "Fixed repeated seed uses cached reference");
            state.output.close();
            System.out.println("PASS exact native benchmark equality run=" + run + " generations=0,20,200");
        }
    }
}
