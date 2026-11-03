package mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD;

import ec.EvolutionState;
import ec.util.Parameter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import mengxu.algorithm.MAPElites.GridMapElites.GPRuleEvolutionStateGridMAPElites;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.jobshop.Objective;

public final class UniformParentState extends GPRuleEvolutionStateGridMAPElites {
    Path outputRoot;
    String label;
    int runId;
    int snapshotInterval;
    long baselineCalls;
    long initialSeed;
    long startedNanos;
    double setupSeconds;
    private boolean ownsOutput;

    public MultipleRuleEvaluationModel model() {
        return (MultipleRuleEvaluationModel) ((MultipleTreeRuleOptimizationProblem) evaluator.p_problem).getEvaluationModel();
    }
    public long trainingSeed() {
        return ((DynamicSimulation) model().getSchedulingSet().getSimulations().get(0)).getSeed();
    }

    @Override public void setup(EvolutionState state, Parameter base) {
        long setupStart = System.nanoTime();
        super.setup(state, base);
        setupSeconds = (System.nanoTime() - setupStart) / 1e9;
        label = parameters.getString(new Parameter("uniform.label"), null);
        boolean rotating = model().isRotatable();
        if (label == null || !(label.equals("UniformParentGPf") || label.equals("UniformParentGP")
                || label.equals("MatchedQDGPf") || label.equals("MatchedQDGP"))
                || label.endsWith("f") == rotating) {
            throw new IllegalArgumentException("Invalid fixed/rotating method label");
        }
        if (evalthreads != 1 || breedthreads != 1 || num_accept != 1 || !use_grid_map
                || !parentsOnlyFromGridMap || parentsOnlyFromPop || !mapUpdateImmediate || reevaluateGridMap
                || parameters.getIntWithDefault(new Parameter("pop.subpops"), null, 0) != 1
                || parameters.getIntWithDefault(new Parameter("pop.subpop.0.species.ind.numtrees"), null, 0) != 2
                || parameters.getIntWithDefault(new Parameter("eval.num-tests"), null, 1) != 1
                || parameters.exists(new Parameter("evaluations"), null)
                || parameters.getBoolean(new Parameter("checkpoint"), null, false)
                || parameters.getBoolean(new Parameter("quit-on-run-complete"), null, false)
                || parameters.getDoubleWithDefault(new Parameter("pop.subpop.0.species.pipe.source.2.prob"), null, -1) != 0) {
            throw new IllegalArgumentException("Use the single-thread, capacity-one, immediate-update, generation-budget presets");
        }
        if (!Double.isFinite(penalty_level) || penalty_level < 0 || (!rotating && penalty_level != 0)
                || (rotating && !usingManualRuleNormalisation)
                || !Double.isFinite(randomParentSelectionProbability)
                || randomParentSelectionProbability < 0 || randomParentSelectionProbability > 1
                || (label.startsWith("Uniform") && randomParentSelectionProbability != 1)) {
            throw new IllegalArgumentException("Invalid age/normalization or uniform-parent configuration");
        }
        if (model().getObjectives().size() != 1 || model().getSchedulingSet().getSimulations().size() != 1
                || model().getSchedulingSet().getReplications().get(0) != 1) {
            throw new IllegalArgumentException("One objective, instance and replication are required for exact cost accounting");
        }
        Objective objective = model().getObjectives().get(0);
        if (objective != Objective.MEAN_TARDINESS && objective != Objective.MAX_FLOWTIME
                && objective != Objective.MEAN_WEIGHTED_FLOWTIME) {
            throw new IllegalArgumentException("Use one of the three paper scheduling objectives");
        }
        if (!(statistics instanceof UniformStatistics)) { throw new IllegalArgumentException("Use isolated statistics"); }
        initialSeed = trainingSeed();
        runId = parameters.getIntWithDefault(new Parameter("uniform.run-id"), null, 0);
        snapshotInterval = parameters.getIntWithDefault(new Parameter("uniform.snapshot-interval"), null, 20);
        String output = parameters.getString(new Parameter("uniform.output-root"), null);
        if (output == null || runId < 0 || snapshotInterval < 1) { throw new IllegalArgumentException("Invalid output/run configuration"); }
        outputRoot = Paths.get(output).toAbsolutePath().normalize();
        try {
            if (Files.exists(outputRoot) || outputRoot.getParent() == null) {
                throw new IllegalArgumentException("Refusing to overwrite output: " + outputRoot);
            }
            Files.createDirectories(outputRoot.getParent());
            Files.createDirectory(outputRoot);
            ownsOutput = true;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @Override public double calculateManualRuleFitness() {
        baselineCalls++;
        double result = super.calculateManualRuleFitness();
        if (!Double.isFinite(result) || result <= 0 || result >= Double.MAX_VALUE) {
            throw new IllegalStateException("Reference objective must be finite and positive for normalization");
        }
        return result;
    }

    @Override public void writeDiversityToFile() {
        // Required archive metrics are redirected to generations.csv, not the legacy working-directory sidecar.
        ((UniformStatistics) statistics).flushMetrics();
    }

    @Override public void run(int condition) {
        if (condition != C_STARTED_FRESH) { throw new UnsupportedOperationException("Only fresh isolated runs are supported"); }
        startedNanos = System.nanoTime();
        try {
            startFresh();
            int result = R_NOTDONE;
            while (result == R_NOTDONE) { result = super.evolve(); }
            finish(result);
        } catch (RuntimeException failure) {
            if (ownsOutput && statistics instanceof UniformStatistics) {
                ((UniformStatistics) statistics).failed(this, failure);
            }
            throw failure;
        }
    }
}
