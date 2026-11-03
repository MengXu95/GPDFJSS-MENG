package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import ec.EvolutionState;
import ec.util.Parameter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import mengxu.algorithm.MAPElites.GridMapElites.GPRuleEvolutionStateGridMAPElites;
import yimei.jss.jobshop.Objective;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

public final class DcState extends GPRuleEvolutionStateGridMAPElites {
    Path outputRoot;
    int d, c, runId, snapshotInterval;
    long descriptorSeed, samplingSeed, initialTrainingSeed, baselineCalls, startedNanos;
    long peakUsedHeap;
    double setupSeconds;
    String studyHash;
    DcDescriptorBank descriptorBank;
    PrintStream progress = System.out;

    public MultipleRuleEvaluationModel model() {
        return (MultipleRuleEvaluationModel) ((MultipleTreeRuleOptimizationProblem) evaluator.p_problem).getEvaluationModel();
    }

    public long trainingSeed() {
        return ((DynamicSimulation) model().getSchedulingSet().getSimulations().get(0)).getSeed();
    }

    @Override protected void configurePhenoCharacterisation(Simulation ignored) {
        d = integer("dc.d"); c = integer("dc.c");
        base = c;
        gridSIze = DcDescriptorBank.side(d, c);
        descriptorSeed = longValue("dc.descriptor-seed");
        samplingSeed = longValue("dc.sampling-seed");
        descriptorBank = DcDescriptorBank.collect(descriptorSeed, samplingSeed, integer("dc.machines"),
                integer("dc.descriptor-jobs"), integer("dc.descriptor-warmup"),
                decimal("dc.utilisation"), decimal("dc.due-factor"), 5, c);
        phenoCharacterisation = descriptorBank.characterisations(d, c);
    }

    @Override public void setup(EvolutionState raw, Parameter base) {
        long start = System.nanoTime();
        super.setup(raw, base);
        setupSeconds = (System.nanoTime() - start) / 1e9;
        runId = integer("dc.run-id");
        snapshotInterval = integer("dc.snapshot-interval");
        studyHash = text("dc.study-hash");
        outputRoot = Path.of(text("dc.output-root")).toAbsolutePath().normalize();
        if (!Files.isDirectory(outputRoot) || !(statistics instanceof DcStatistics)
                || evalthreads != 1 || breedthreads != 1 || num_accept != 1 || !use_grid_map
                || !parentsOnlyFromGridMap || parentsOnlyFromPop || !mapUpdateImmediate || reevaluateGridMap
                || model().isRotatable() || penalty_level != 0 || !usingManualRuleNormalisation
                || randomParentSelectionProbability != .5
                || model().getObjectives().size() != 1 || model().getObjectives().get(0) != Objective.MEAN_TARDINESS
                || model().getSchedulingSet().getSimulations().size() != 1
                || model().getSchedulingSet().getReplications().get(0) != 1) {
            throw new IllegalArgumentException("Use the fixed-instance, capacity-one, hybrid-parent QDGPf sensitivity preset");
        }
        if (decimal("pop.subpop.0.species.pipe.source.0.prob") != .85
                || decimal("pop.subpop.0.species.pipe.source.1.prob") != .15
                || decimal("pop.subpop.0.species.pipe.source.2.prob") != 0
                || parameters.exists(new Parameter("evaluations"), null)
                || parameters.getBoolean(new Parameter("checkpoint"), null, false)
                || parameters.getBoolean(new Parameter("quit-on-run-complete"), null, false)) {
            throw new IllegalArgumentException("Keep the approved 0.85/0.15/0 operators and generation-defined evaluation budget");
        }
        initialTrainingSeed = trainingSeed();
        if (initialTrainingSeed != longValue("dc.training-seed")) {
            throw new IllegalStateException("Simulator seed differs from the declared paired training seed");
        }
        try {
            java.util.Properties marker = DcFiles.read(outputRoot.resolve("manifest.properties"));
            if (!studyHash.equals(marker.getProperty("study-hash")) || !"RUNNING".equals(marker.getProperty("status"))) {
                throw new IOException("Training directory was not prepared by DcTraining");
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public double calculateManualRuleFitness() {
        baselineCalls++;
        double result = super.calculateManualRuleFitness();
        if (!Double.isFinite(result) || result <= 0 || result >= Double.MAX_VALUE) {
            throw new IllegalStateException("Reference fitness must be finite and positive");
        }
        return result;
    }

    void measureHeap() {
        Runtime runtime = Runtime.getRuntime();
        peakUsedHeap = Math.max(peakUsedHeap, runtime.totalMemory() - runtime.freeMemory());
    }

    @Override public void writeDiversityToFile() { ((DcStatistics) statistics).flushMetrics(); }

    @Override public void run(int condition) {
        if (condition != C_STARTED_FRESH) { throw new IllegalArgumentException("Only a fresh per-attempt run is supported"); }
        startedNanos = System.nanoTime();
        startFresh();
        int result = R_NOTDONE;
        while (result == R_NOTDONE) { result = super.evolve(); }
        finish(result);
    }

    private String text(String key) {
        String value = parameters.getString(new Parameter(key), null);
        if (value == null) { throw new IllegalArgumentException("Missing parameter: " + key); }
        return value;
    }
    private int integer(String key) { return Integer.parseInt(text(key)); }
    private long longValue(String key) { return Long.parseLong(text(key)); }
    private double decimal(String key) { return Double.parseDouble(text(key)); }
}
