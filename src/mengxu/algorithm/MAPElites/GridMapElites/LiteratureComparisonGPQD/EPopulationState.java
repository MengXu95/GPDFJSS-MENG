package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Statistics;
import ec.util.Parameter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseEvolutionState;

public final class EPopulationState extends LexicaseEvolutionState implements EManagedState {
    PrintStream progress = System.out;
    long initialTrainingSeed;
    long startedNanos;

    @Override protected boolean acceptsStatistics(Statistics value) { return value instanceof EPopulationStatistics; }
    @Override protected boolean acceptsMethod(String value) {
        return "GPf".equals(value) || "GP".equals(value)
                || "LexicaseGPf".equals(value) || "LexicaseGP".equals(value);
    }
    @Override protected boolean isRotatingMethod(String value) {
        return "GP".equals(value) || "LexicaseGP".equals(value);
    }
    @Override protected void prepareOutput() {
        try {
            String family = method.startsWith("Lexicase") ? "LEXICASE" : "GP";
            if (!outputRoot.equals(Path.of(EAdapterSupport.text(this, "e.output-root")).toAbsolutePath().normalize())
                    || runId != EAdapterSupport.integer(this, "e.run-id")
                    || snapshotInterval != EAdapterSupport.integer(this, "e.snapshot-interval")) {
                throw new IllegalArgumentException("Population aliases disagree with the prepared E attempt");
            }
            EAdapterSupport.prepared(this, outputRoot, family);
            outputOwned = true;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    @Override public void setup(EvolutionState raw, Parameter base) {
        super.setup(raw, base);
        initialTrainingSeed = model().trainingSeed();
        if (initialTrainingSeed != EAdapterSupport.seed(this, "e.training-seed")
                || jobSeed != EAdapterSupport.seed(this, "e.gp-seed")
                || model().isRotatable() != "rotating".equals(EAdapterSupport.text(this, "e.regime"))
                || EAdapterSupport.seed(this, "lexqd.seed-step") != EAdapterSupport.seed(this, "e.seed-step")
                || EAdapterSupport.seed(this, "e.seed-step") != 10000) {
            throw new IllegalArgumentException("Declared paired population seed/regime differs from evaluator");
        }
        boolean lexicase = method.startsWith("Lexicase");
        String selector = lexicase
                ? "mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationLexicaseSelection"
                : "ec.select.TournamentSelection";
        for (String key : new String[]{"breed.reproduce.source.0", "gp.koza.xover.source.0", "gp.koza.mutate.source.0"}) {
            if (!selector.equals(EAdapterSupport.text(this, key))) {
                throw new IllegalArgumentException("Incorrect population selector: " + key);
            }
        }
        if (!lexicase && EAdapterSupport.integer(this, "select.tournament.size") != 7) {
            throw new IllegalArgumentException("GP control requires scalar tournament size seven");
        }
        if (parameters.getBoolean(new Parameter("use-grid-map"), null, false)
                || parameters.getBoolean(new Parameter("using-manual-rule-normalisation"), null, false)
                || EAdapterSupport.decimal(this, "pop.subpop.0.species.pipe.source.0.prob") != .85
                || EAdapterSupport.decimal(this, "pop.subpop.0.species.pipe.source.1.prob") != .15
                || EAdapterSupport.decimal(this, "pop.subpop.0.species.pipe.source.2.prob") != 0) {
            throw new IllegalArgumentException("Population selection must use raw within-batch fitness without an archive");
        }
    }
    public Path outputRoot() { return outputRoot; }
    public String methodLabel() { return method; }
    public int runId() { return runId; }
    public int snapshotInterval() { return snapshotInterval; }
    public double evaluationSeconds() { return evaluationSeconds; }
    @Override public void setProgress(PrintStream value) { progress = Objects.requireNonNull(value); }
    @Override public void run(int condition) {
        startedNanos = System.nanoTime();
        try { super.run(condition); }
        catch (Error failure) {
            try { closeResources(); } catch (IOException close) { failure.addSuppressed(close); }
            if (outputOwned) { EAdapterSupport.failed(outputRoot, failure); }
            throw failure;
        }
    }
    @Override protected void failedRun(RuntimeException failure) {
        try { closeResources(); } catch (IOException close) { failure.addSuppressed(close); }
        if (outputOwned) { EAdapterSupport.failed(outputRoot, failure); }
    }
    @Override public void closeResources() throws IOException {
        if (statistics instanceof EPopulationStatistics) { ((EPopulationStatistics) statistics).closeResources(); }
    }
}
