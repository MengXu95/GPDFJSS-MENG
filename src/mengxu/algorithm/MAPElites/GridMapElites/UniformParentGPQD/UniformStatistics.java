package mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD;

import ec.EvolutionState;
import ec.Individual;
import ec.Statistics;
import ec.gp.GPIndividual;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots;

public final class UniformStatistics extends Statistics {
    private BufferedWriter metrics;
    private final Properties manifest = new Properties();
    private long start;

    @Override public void preInitializationStatistics(EvolutionState raw) {
        super.preInitializationStatistics(raw);
        UniformParentState state = (UniformParentState) raw;
        start = state.startedNanos == 0 ? System.nanoTime() : state.startedNanos;
        try {
            try (PrintWriter writer = new PrintWriter(UniformFiles.create(state.outputRoot.resolve("resolved.params")))) {
                state.parameters.list(writer, false);
                if (writer.checkError()) { throw new IOException("Unable to write resolved parameters"); }
            }
            manifest.setProperty("implementation-version", "archive-parent-source-lifecycle-v1");
            manifest.setProperty("method", state.label);
            manifest.setProperty("run-id", Integer.toString(state.runId));
            manifest.setProperty("gp-seed", Long.toString(state.jobSeed));
            manifest.setProperty("initial-training-seed", Long.toString(state.initialSeed));
            manifest.setProperty("regime", state.model().isRotatable() ? "rotating" : "fixed");
            manifest.setProperty("parent-random-probability", Double.toString(state.randomParentSelectionProbability));
            manifest.setProperty("age-penalty-level", Double.toString(state.penalty_level));
            manifest.setProperty("manual-reference-normalization", Boolean.toString(state.usingManualRuleNormalisation));
            manifest.setProperty("setup-seconds", Double.toString(state.setupSeconds));
            manifest.setProperty("cell-capacity", "1");
            manifest.setProperty("descriptor", "inherited QDGP: 4 sequencing and 4 routing ranks; 3 candidates");
            manifest.setProperty("parent-indexing", "uniform occupied-cell insertion-order index; selected parents cloned");
            manifest.setProperty("initial-parent-source", "archive, including first offspring after initialization");
            manifest.setProperty("quality-policy", "inherited QDGP normalization, age adjustment and immediate admission");
            manifest.setProperty("evaluation-policy", "inherited QDGP batch, elite reevaluation and simulator reset behavior");
            manifest.setProperty("cost-scope", "fitness calls + manual reference calls + 2 descriptor reference simulations");
            manifest.setProperty("diagnostics", "inherited diversity computation retained; archive metrics exported in generations.csv");
            manifest.setProperty("historical-equivalence", "not certified; use matching controls and independent validation");
            manifest.setProperty("java-version", System.getProperty("java.runtime.version"));
            manifest.setProperty("status", "RUNNING");
            UniformFiles.manifest(state.outputRoot.resolve("manifest.properties"), manifest, false);
            metrics = UniformFiles.create(state.outputRoot.resolve("generations.csv"));
            UniformFiles.row(metrics, "generation", "training_seed", "archive_size", "occupied_cells", "coverage",
                    "finite_raw_members", "finite_mean_raw_training", "fitness_evaluations",
                    "manual_reference_simulations", "descriptor_reference_simulations", "total_simulations",
                    "elapsed_seconds", "snapshot");
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @Override public void preEvaluationStatistics(EvolutionState raw) {
        super.preEvaluationStatistics(raw);
        UniformParentState state = (UniformParentState) raw;
        if (!(state.gridPCmap instanceof IsolatedGridMap)) {
            if (state.generation != 0 || state.gridPCmap == null
                    || state.gridPCmap.getNumberIndividualsInGridMap() != 0) {
                throw new IllegalStateException("Only the empty initialized map may be wrapped");
            }
            state.gridPCmap = new IsolatedGridMap(state.gridSIze, state.base);
        }
    }

    @Override public void postEvaluationStatistics(EvolutionState raw) {
        super.postEvaluationStatistics(raw);
        UniformParentState state = (UniformParentState) raw;
        IsolatedGridMap map = (IsolatedGridMap) state.gridPCmap;
        if (map.getNumberIndividualsInGridMap() != map.occupiedCount()) {
            throw new IllegalStateException("Occupied-cell index disagrees with the capacity-one archive");
        }
        boolean last = state.generation == state.numGenerations - 1;
        boolean snapshot = state.generation % state.snapshotInterval == 0 || last;
        int valid = 0;
        double mean = 0;
        for (int i = 0; i < map.occupiedCount(); i++) {
            double value = map.individualAt(i).fitness.fitness();
            if (Double.isFinite(value) && value < Double.MAX_VALUE) {
                valid++;
                mean += (value - mean) / valid;
            }
        }
        try {
            if (state.generation == 0) {
                PopulationSnapshots.write(state.outputRoot.resolve("initial_population.stat"), state.label,
                        state.runId, 0, state.trainingSeed(), state.population.subpops[0].individuals);
            }
            if (snapshot) {
                save(state, String.format(java.util.Locale.ROOT, "archive.gen%04d", state.generation));
            }
            if (last) { save(state, "job." + state.runId + ".out"); }
            long total = Math.addExact(Math.addExact((long) state.model().countInd, state.baselineCalls), 2);
            UniformFiles.row(metrics, state.generation, state.trainingSeed(), map.getNumberIndividualsInGridMap(),
                    map.occupiedCount(), map.occupiedCount() / (double) (map.grid_size * map.grid_size),
                    valid, valid == 0 ? "NA" : mean, state.model().countInd, state.baselineCalls, 2, total,
                    (System.nanoTime() - start) / 1e9, snapshot);
            metrics.flush();
            manifest.setProperty("last-evaluated-generation", Integer.toString(state.generation));
            manifest.setProperty("fitness-evaluations", Integer.toString(state.model().countInd));
            manifest.setProperty("manual-reference-simulations", Long.toString(state.baselineCalls));
            manifest.setProperty("total-simulations", Long.toString(total));
            UniformFiles.manifest(state.outputRoot.resolve("manifest.properties"), manifest, true);
            if (last && valid == 0) { throw new IllegalStateException("No finite scheduling rule in the final archive"); }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static void save(UniformParentState state, String name) throws IOException {
        IsolatedGridMap map = (IsolatedGridMap) state.gridPCmap;
        Path path = state.outputRoot.resolve(name + ".stat");
        try (BufferedWriter writer = UniformFiles.create(path)) {
            writer.write("Producer: " + state.label + "\nGeneration: " + state.generation
                    + "\nAll individuals in GridMAP:\nGridMAP number: " + map.occupiedCount() + "\n");
            for (int i = 0; i < map.occupiedCount(); i++) {
                GPIndividual individual = (GPIndividual) map.individualAt(i);
                if (!individual.evaluated) { throw new IllegalStateException("Unevaluated rule in archive snapshot"); }
                writer.write("Evaluated: true\nFitness: [" + individual.fitness.fitness()
                        + "]\nTree 0:\n" + individual.trees[0].child.makeLispTree()
                        + "\nTree 1:\n" + individual.trees[1].child.makeLispTree() + "\n");
            }
        }
        try (BufferedWriter writer = UniformFiles.create(state.outputRoot.resolve(name + ".csv"))) {
            UniformFiles.row(writer, "run_id", "method", "generation", "seq_cell", "rout_cell", "raw_training_fitness",
                    "stored_comparison_score", "age_adjusted_score", "birth_generation", "birth_seed",
                    "tree_hash", "sequencing", "routing");
            for (int i = 0; i < map.occupiedCount(); i++) {
                GPIndividual individual = (GPIndividual) map.individualAt(i);
                int key = map.keyAt(i);
                int born = map.generationAt(i);
                String seq = individual.trees[0].child.makeLispTree();
                String rout = individual.trees[1].child.makeLispTree();
                ArchiveCandidate candidate = new ArchiveCandidate(path, i, individual.fitness.fitness(), seq, rout);
                long seed = state.model().isRotatable()
                        ? Math.addExact(state.initialSeed, Math.multiplyExact((long) born, 10000L)) : state.initialSeed;
                UniformFiles.row(writer, state.runId, state.label, state.generation, key / map.grid_size,
                        key % map.grid_size, individual.fitness.fitness(), map.scoreAt(i),
                        map.scoreAt(i) * Math.pow(1 + state.penalty_level, state.generation - born),
                        born, seed, candidate.treeHash(), seq, rout);
            }
        }
    }

    void flushMetrics() {
        try { if (metrics != null) { metrics.flush(); } }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @Override public void finalStatistics(EvolutionState raw, int result) {
        super.finalStatistics(raw, result);
        UniformParentState state = (UniformParentState) raw;
        try {
            if (metrics != null) { metrics.close(); }
            manifest.setProperty("status", "COMPLETE");
            manifest.setProperty("total-wall-seconds", Double.toString((System.nanoTime() - start) / 1e9));
            UniformFiles.manifest(state.outputRoot.resolve("manifest.properties"), manifest, true);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    void failed(UniformParentState state, RuntimeException failure) {
        try {
            if (metrics != null) { metrics.close(); }
            manifest.setProperty("status", "FAILED");
            manifest.setProperty("failure", failure.toString());
            Path path = state.outputRoot.resolve("manifest.properties");
            UniformFiles.manifest(path, manifest, Files.exists(path));
        } catch (IOException reportingFailure) { failure.addSuppressed(reportingFailure); }
    }
}
