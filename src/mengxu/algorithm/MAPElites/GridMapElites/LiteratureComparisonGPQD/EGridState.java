package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.util.Parameter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD.DcDescriptorBank;
import mengxu.algorithm.MAPElites.GridMapElites.GPRuleEvolutionStateGridMAPElites;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

public final class EGridState extends GPRuleEvolutionStateGridMAPElites implements EManagedState {
    Path outputRoot;
    int runId, snapshotInterval;
    long initialTrainingSeed, startedNanos;
    DcDescriptorBank descriptorBank;
    PrintStream progress = System.out;

    public EGridEvaluation model() {
        return (EGridEvaluation) ((MultipleTreeRuleOptimizationProblem) evaluator.p_problem).getEvaluationModel();
    }
    public long trainingSeed() { return model().trainingSeed(); }

    @Override protected void configurePhenoCharacterisation(Simulation ignored) {
        base = 3;
        gridSIze = DcDescriptorBank.side(4, 3);
        descriptorBank = DcDescriptorBank.collect(EAdapterSupport.seed(this, "e.descriptor-seed"),
                EAdapterSupport.seed(this, "e.sampling-seed"), EAdapterSupport.integer(this, "e.machines"),
                EAdapterSupport.integer(this, "e.descriptor-jobs"), EAdapterSupport.integer(this, "e.descriptor-warmup"),
                EAdapterSupport.decimal(this, "e.utilisation"), EAdapterSupport.decimal(this, "e.due-factor"), 4, 3);
        phenoCharacterisation = descriptorBank.characterisations(4, 3);
    }
    @Override public void setup(EvolutionState raw, Parameter base) {
        super.setup(raw, base);
        runId = EAdapterSupport.integer(this, "e.run-id");
        snapshotInterval = EAdapterSupport.integer(this, "e.snapshot-interval");
        outputRoot = Path.of(EAdapterSupport.text(this, "e.output-root")).toAbsolutePath().normalize();
        if (!(statistics instanceof EGridStatistics) || evalthreads != 1 || breedthreads != 1
                || num_accept != 1 || !use_grid_map || !parentsOnlyFromGridMap || parentsOnlyFromPop
                || !mapUpdateImmediate || reevaluateGridMap || penalty_level != 0
                || !usingManualRuleNormalisation || randomParentSelectionProbability != .5
                || EAdapterSupport.integer(this, "pop.subpops") != 1
                || EAdapterSupport.integer(this, "pop.subpop.0.species.ind.numtrees") != 2
                || parameters.getIntWithDefault(new Parameter("eval.num-tests"), null, 1) != 1
                || parameters.exists(new Parameter("evaluations"), null)
                || parameters.getBoolean(new Parameter("checkpoint"), null, false)
                || parameters.getBoolean(new Parameter("quit-on-run-complete"), null, false)
                || EAdapterSupport.decimal(this, "pop.subpop.0.species.pipe.source.0.prob") != .85
                || EAdapterSupport.decimal(this, "pop.subpop.0.species.pipe.source.1.prob") != .15
                || EAdapterSupport.decimal(this, "pop.subpop.0.species.pipe.source.2.prob") != 0
                || runId < 0 || snapshotInterval < 1
                || model().isRotatable() != "rotating".equals(EAdapterSupport.text(this, "e.regime"))) {
            throw new IllegalArgumentException("Use native live-admission capacity-one QD, hybrid p=.5, normalization=true, age=0");
        }
        for (String key : new String[]{"breed.reproduce.source.0", "gp.koza.xover.source.0", "gp.koza.mutate.source.0"}) {
            if (!"mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.ArchiveParentSelection"
                    .equals(EAdapterSupport.text(this, key))) {
                throw new IllegalArgumentException("Reuse ArchiveParentSelection without changing native breeding");
            }
        }
        initialTrainingSeed = trainingSeed();
        if (initialTrainingSeed != EAdapterSupport.seed(this, "e.training-seed")
                || jobSeed != EAdapterSupport.seed(this, "e.gp-seed")) {
            throw new IllegalArgumentException("Declared paired training seed differs from the QD simulator");
        }
        try { EAdapterSupport.prepared(this, outputRoot, "QDGP"); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    @Override public double calculateManualRuleFitness() {
        double result = super.calculateManualRuleFitness();
        if (!EAdapterSupport.finite(result) || result <= 0) {
            throw new IllegalStateException("Manual reference objective must be finite and positive");
        }
        return result;
    }
    @Override public void writeDiversityToFile() { ((EGridStatistics) statistics).flushMetrics(); }
    @Override public void setProgress(PrintStream value) { progress = Objects.requireNonNull(value); }
    @Override public void run(int condition) {
        if (condition != C_STARTED_FRESH) { throw new IllegalArgumentException("Only a fresh prepared attempt is supported"); }
        startedNanos = System.nanoTime();
        try {
            startFresh();
            int result = R_NOTDONE;
            while (result == R_NOTDONE) { result = evolve(); }
            finish(result);
        } catch (RuntimeException | Error failure) {
            try { closeResources(); } catch (IOException close) { failure.addSuppressed(close); }
            EAdapterSupport.failed(outputRoot, failure);
            throw failure;
        }
    }
    @Override public void closeResources() throws IOException {
        if (statistics instanceof EGridStatistics) { ((EGridStatistics) statistics).closeResources(); }
    }
}
