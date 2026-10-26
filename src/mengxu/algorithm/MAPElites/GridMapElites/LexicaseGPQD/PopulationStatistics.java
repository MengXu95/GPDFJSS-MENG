package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import ec.EvolutionState;
import ec.Individual;
import ec.Statistics;
import ec.gp.GPIndividual;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;

public final class PopulationStatistics extends Statistics {
    private final Properties manifest = new Properties();
    private BufferedWriter generations;
    private long start;

    @Override public void preInitializationStatistics(EvolutionState rawState) {
        super.preInitializationStatistics(rawState);
        LexicaseEvolutionState state = (LexicaseEvolutionState) rawState;
        start = System.nanoTime();
        try {
            Path resolved = state.outputRoot.resolve("resolved.params");
            try (PrintWriter writer = new PrintWriter(LexicaseFiles.create(resolved))) {
                state.parameters.list(writer, false);
                if (writer.checkError()) { throw new IOException("Unable to write resolved parameters"); }
            }
            manifest.setProperty("method", state.method);
            manifest.setProperty("implementation-version", "population-lexicase-jobid-v1");
            manifest.setProperty("run-id", Integer.toString(state.runId));
            manifest.setProperty("gp-seed", Long.toString(state.jobSeed));
            manifest.setProperty("source-kind", "population; not a QD archive");
            manifest.setProperty("history-archive", "none; snapshots must not be pooled without a separate declared protocol");
            manifest.setProperty("selector", "EpsilonTTGenNoRepeatCandidateLexicaseSelection; guarded population adapter");
            manifest.setProperty("case-definition", "equal fixed job-ID blocks over all recorded jobs, after warmup");
            manifest.setProperty("case-count", Integer.toString(state.model().caseCount()));
            manifest.setProperty("regime", state.model().isRotatable() ? "rotating" : "fixed");
            manifest.setProperty("shop-policy", "shop fixed at initial simulation seed; job-stream seed reset before every individual");
            manifest.setProperty("scalar-quality", "native whole-instance objective; no cross-generation raw-best selection");
            manifest.setProperty("case-cost", "case values from one simulation, not separate simulations");
            manifest.setProperty("resolved-params-sha256", LexicaseFiles.hash(resolved));
            manifest.setProperty("java-version", System.getProperty("java.runtime.version"));
            manifest.setProperty("status", "RUNNING");
            LexicaseFiles.manifest(state.outputRoot.resolve("manifest.properties"), manifest, false);
            generations = LexicaseFiles.create(state.outputRoot.resolve("generations.csv"));
            LexicaseFiles.row(generations, "run_id", "method", "generation", "training_seed", "population_size",
                    "valid", "failed", "best_raw_objective", "finite_mean_raw_objective",
                    "simulation_count", "case_value_slots", "evaluation_seconds", "elapsed_seconds", "snapshot",
                    "effective_lexicase_candidates");
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @Override public void postEvaluationStatistics(EvolutionState rawState) {
        super.postEvaluationStatistics(rawState);
        LexicaseEvolutionState state = (LexicaseEvolutionState) rawState;
        Individual[] population = state.population.subpops[0].individuals;
        int valid = 0;
        double best = Double.POSITIVE_INFINITY;
        double mean = 0;
        for (Individual individual : population) {
            if (((LexicaseFitness) individual.fitness).isValidEvaluation()) {
                valid++;
                double value = individual.fitness.fitness();
                best = Math.min(best, value);
                mean += (value - mean) / valid;
            }
        }
        boolean last = state.generation == state.numGenerations - 1;
        boolean snapshot = state.generation % state.snapshotInterval == 0 || last;
        try {
            if (snapshot) {
                String name = String.format(java.util.Locale.ROOT, "population.gen%04d", state.generation);
                save(state, name);
            }
            if (last) { save(state, "final_population"); }
            LexicaseFiles.row(generations, state.runId, state.method, state.generation,
                    state.model().trainingSeed(), population.length, valid, population.length - valid,
                    best, valid == 0 ? "NA" : mean, state.model().simulationCount(),
                    Math.multiplyExact(state.model().simulationCount(), state.model().caseCount()),
                    state.evaluationSeconds, (System.nanoTime() - start) / 1e9, snapshot,
                    Math.min(valid, state.parameters.getIntWithDefault(new ec.util.Parameter("candidate-size"), null, 0)));
            generations.flush();
            manifest.setProperty("last-completed-generation", Integer.toString(state.generation));
            manifest.setProperty("simulation-count", Long.toString(state.model().simulationCount()));
            manifest.setProperty("failed-evaluations", Long.toString(state.model().failedCount()));
            LexicaseFiles.manifest(state.outputRoot.resolve("manifest.properties"), manifest, true);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static void save(LexicaseEvolutionState state, String name) throws IOException {
        Individual[] individuals = state.population.subpops[0].individuals;
        Path snapshot = state.outputRoot.resolve(name + ".stat");
        PopulationSnapshots.write(snapshot, state.method, state.runId, state.generation,
                state.model().trainingSeed(), individuals);
        try (BufferedWriter writer = LexicaseFiles.create(state.outputRoot.resolve(name + ".csv"))) {
            List<Object> header = new ArrayList<>();
            java.util.Collections.addAll(header, "run_id", "method", "generation", "training_seed",
                    "population_index", "raw_objective", "valid", "tree_hash");
            for (int i = 0; i < state.model().caseCount(); i++) { header.add("case_" + i); }
            java.util.Collections.addAll(header, "sequencing", "routing");
            LexicaseFiles.row(writer, header.toArray());
            for (int index = 0; index < individuals.length; index++) {
                GPIndividual individual = (GPIndividual) individuals[index];
                LexicaseFitness fitness = (LexicaseFitness) individual.fitness;
                String seq = individual.trees[0].child.makeLispTree();
                String rout = individual.trees[1].child.makeLispTree();
                ArchiveCandidate candidate = new ArchiveCandidate(snapshot, index, fitness.fitness(), seq, rout);
                List<Object> row = new ArrayList<>();
                java.util.Collections.addAll(row, state.runId, state.method, state.generation,
                        state.model().trainingSeed(), index, fitness.fitness(), fitness.isValidEvaluation(), candidate.treeHash());
                for (double[] value : fitness.multiInstanceMultiObjectiveFitness) { row.add(value[0]); }
                java.util.Collections.addAll(row, seq, rout);
                LexicaseFiles.row(writer, row.toArray());
            }
        }
    }

    @Override public void finalStatistics(EvolutionState rawState, int result) {
        super.finalStatistics(rawState, result);
        LexicaseEvolutionState state = (LexicaseEvolutionState) rawState;
        try {
            if (generations != null) { generations.close(); }
            manifest.setProperty("status", "COMPLETE");
            LexicaseFiles.manifest(state.outputRoot.resolve("manifest.properties"), manifest, true);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    void failed(LexicaseEvolutionState state, RuntimeException failure) {
        try {
            if (generations != null) { generations.close(); }
            manifest.setProperty("status", "FAILED");
            manifest.setProperty("failure", failure.toString());
            Path output = state.outputRoot.resolve("manifest.properties");
            LexicaseFiles.manifest(output, manifest, java.nio.file.Files.exists(output));
        } catch (IOException reportingFailure) { failure.addSuppressed(reportingFailure); }
    }
}
