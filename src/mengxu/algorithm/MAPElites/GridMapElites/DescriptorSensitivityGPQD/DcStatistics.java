package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import ec.EvolutionState;
import ec.Statistics;
import ec.gp.GPIndividual;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots;

public final class DcStatistics extends Statistics {
    private BufferedWriter metrics;
    private Properties manifest;

    @Override public void preInitializationStatistics(EvolutionState raw) {
        super.preInitializationStatistics(raw);
        DcState state = (DcState) raw;
        try {
            manifest = DcFiles.read(state.outputRoot.resolve("manifest.properties"));
            try (PrintWriter writer = new PrintWriter(DcFiles.create(state.outputRoot.resolve("resolved.params")))) {
                state.parameters.list(writer, false);
                if (writer.checkError()) { throw new IOException("Cannot save resolved parameters"); }
            }
            state.descriptorBank.write(state.outputRoot.resolve("snapshot_bank.csv"), 5, state.c);
            state.descriptorBank.write(state.outputRoot.resolve("training_descriptor.csv"), state.d, state.c);
            manifest.setProperty("method", "QDGPf");
            manifest.setProperty("regime", "fixed");
            manifest.setProperty("grid-side", Integer.toString(state.gridSIze));
            manifest.setProperty("grid-cells", Integer.toString(Math.multiplyExact(state.gridSIze, state.gridSIze)));
            manifest.setProperty("cell-capacity", "1");
            manifest.setProperty("descriptor-reference-simulations", Long.toString(state.descriptorBank.simulationCount()));
            manifest.setProperty("nominal-evaluations", Long.toString((long) state.numGenerations
                    * state.parameters.getInt(new ec.util.Parameter("pop.subpop.0.size"), null)));
            manifest.setProperty("setup-seconds", Double.toString(state.setupSeconds));
            manifest.setProperty("parent-policy", "source hybrid: random probability 0.5, scaled adaptive tournament; cloned archive parents");
            manifest.setProperty("initial-parent-source", "archive, including the first offspring batch");
            manifest.setProperty("historical-equivalence", "fresh controlled experiment; not identical historical trajectories");
            manifest.setProperty("java-runtime", System.getProperty("java.runtime.version"));
            manifest.setProperty("peak-memory-policy", "maximum observed JVM used heap at generation boundaries; not process peak RSS");
            metrics = DcFiles.create(state.outputRoot.resolve("generations.csv"));
            DcFiles.row(metrics, "generation", "d", "c", "grid_side", "grid_cells", "occupied_cells",
                    "retained_members", "native_coverage", "finite_raw_members", "finite_mean_raw_training",
                    "fitness_evaluations", "manual_reference_simulations", "descriptor_reference_simulations",
                    "total_simulations", "elapsed_seconds", "observed_used_heap_bytes", "snapshot");
            DcFiles.write(state.outputRoot.resolve("manifest.properties"), manifest, true);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public void preEvaluationStatistics(EvolutionState raw) {
        super.preEvaluationStatistics(raw);
        DcState state = (DcState) raw;
        if (!(state.gridPCmap instanceof DcGridMap)) {
            if (state.generation != 0 || state.gridPCmap == null || state.gridPCmap.getNumberIndividualsInGridMap() != 0) {
                throw new IllegalStateException("Only the empty initial grid can be wrapped");
            }
            state.gridPCmap = new DcGridMap(state.gridSIze, state.c);
        }
    }

    @Override public void postEvaluationStatistics(EvolutionState raw) {
        super.postEvaluationStatistics(raw);
        DcState state = (DcState) raw;
        DcGridMap map = (DcGridMap) state.gridPCmap;
        int count = map.occupiedCount();
        if (count != map.getNumberIndividualsInGridMap() || map.grid_size != state.gridSIze) {
            throw new IllegalStateException("Native occupied-cell count/geometry mismatch");
        }
        int finite = 0;
        double mean = 0, best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            double fitness = map.individualAt(i).fitness.fitness();
            if (Double.isFinite(fitness) && fitness < Double.MAX_VALUE) {
                finite++;
                mean += (fitness - mean) / finite;
                best = Math.min(best, fitness);
            }
        }
        boolean last = state.generation == state.numGenerations - 1;
        boolean snapshot = state.generation % state.snapshotInterval == 0 || last;
        state.measureHeap();
        try {
            if (state.generation == 0) {
                PopulationSnapshots.write(state.outputRoot.resolve("initial_population.stat"), "QDGPf-dc",
                        state.runId, 0, state.initialTrainingSeed, state.population.subpops[0].individuals);
            }
            if (snapshot) { save(state, String.format(Locale.ROOT, "archive.gen%04d", state.generation)); }
            if (last) { save(state, "job." + state.runId + ".out"); }
            int population = state.population.subpops[0].individuals.length;
            long expected = Math.multiplyExact((long) state.generation + 1, population);
            if (state.model().countInd != expected) {
                throw new IllegalStateException("Actual fitness-call count " + state.model().countInd
                        + " differs from the declared evaluated-batch budget " + expected);
            }
            long total = (long) state.model().countInd + state.baselineCalls + state.descriptorBank.simulationCount();
            double elapsed = (System.nanoTime() - state.startedNanos) / 1e9;
            DcFiles.row(metrics, state.generation, state.d, state.c, state.gridSIze,
                    Math.multiplyExact(state.gridSIze, state.gridSIze), count, count,
                    count / (double) (state.gridSIze * state.gridSIze), finite, finite == 0 ? "NA" : mean,
                    state.model().countInd, state.baselineCalls, state.descriptorBank.simulationCount(),
                    total, elapsed, state.peakUsedHeap, snapshot);
            metrics.flush();
            manifest.setProperty("last-evaluated-generation", Integer.toString(state.generation));
            manifest.setProperty("fitness-evaluations", Long.toString(expected));
            manifest.setProperty("manual-reference-simulations", Long.toString(state.baselineCalls));
            manifest.setProperty("total-training-simulations", Long.toString(total));
            manifest.setProperty("native-occupied-cells", Integer.toString(count));
            manifest.setProperty("native-retained-members", Integer.toString(count));
            manifest.setProperty("finite-stored-members", Integer.toString(finite));
            manifest.setProperty("best-training-fitness", Double.toString(best));
            manifest.setProperty("peak-used-heap-bytes", Long.toString(state.peakUsedHeap));
            DcFiles.write(state.outputRoot.resolve("manifest.properties"), manifest, true);
            if (snapshot) {
                state.progress.println("  " + state.d + "," + state.c + " run=" + state.runId
                        + " generation=" + state.generation + " evaluations=" + expected
                        + " occupied=" + count + " elapsed=" + String.format(Locale.ROOT, "%.1f", elapsed) + "s");
            }
            if (last && finite == 0) { throw new IllegalStateException("Final archive has no finite-training rule"); }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private static void save(DcState state, String stem) throws IOException {
        DcGridMap map = (DcGridMap) state.gridPCmap;
        java.nio.file.Path archive = state.outputRoot.resolve(stem + ".stat");
        try (BufferedWriter writer = DcFiles.create(archive);
             BufferedWriter csv = DcFiles.create(state.outputRoot.resolve(stem + ".csv"))) {
            writer.write("Producer: QDGPf-dc\nAll individuals in GridMAP:\nGridMAP number: " + map.occupiedCount() + "\n");
            DcFiles.row(csv, "candidate_id", "run_id", "d", "c", "generation", "seq_cell", "rout_cell",
                    "descriptor_digits_zero_based", "raw_training_fitness", "stored_comparison_score",
                    "birth_generation", "tree_hash", "sequencing", "routing");
            for (int i = 0; i < map.occupiedCount(); i++) {
                GPIndividual individual = (GPIndividual) map.individualAt(i);
                if (!individual.evaluated) { throw new IllegalStateException("Unevaluated archive member"); }
                String seq = individual.trees[0].child.makeLispTree(), route = individual.trees[1].child.makeLispTree();
                ArchiveCandidate candidate = new ArchiveCandidate(archive, i, individual.fitness.fitness(), seq, route);
                int key = map.keyAt(i), x = key / map.grid_size, y = key % map.grid_size;
                double[] digits = map.PCs_each_cell[x][y].get(0);
                int[] actual = map.calculateUniqueValue(digits);
                if (digits.length != 2 * state.d || actual[0] != x || actual[1] != y) {
                    throw new IllegalStateException("Exported native coordinates disagree with descriptor digits");
                }
                StringBuilder encoded = new StringBuilder();
                for (double digit : digits) {
                    if (digit != Math.rint(digit) || digit < 0 || digit >= state.c) {
                        throw new IllegalStateException("Descriptor digit outside the base-c alphabet");
                    }
                    if (encoded.length() > 0) { encoded.append(';'); }
                    encoded.append((int) digit);
                }
                writer.write("Evaluated: true\nFitness: [" + individual.fitness.fitness()
                        + "]\nTree 0:\n" + seq + "\nTree 1:\n" + route + "\n");
                DcFiles.row(csv, candidate.id(), state.runId, state.d, state.c, state.generation, x, y,
                        encoded, candidate.storedFitness(), map.scoreAt(i), map.generationAt(i),
                        candidate.treeHash(), seq, route);
            }
        }
    }

    void flushMetrics() {
        try { if (metrics != null) { metrics.flush(); } }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    void closeMetrics() throws IOException { if (metrics != null) { metrics.close(); metrics = null; } }

    @Override public void finalStatistics(EvolutionState raw, int result) {
        super.finalStatistics(raw, result);
        DcState state = (DcState) raw;
        try {
            closeMetrics();
            manifest.setProperty("status", "SEARCH_COMPLETE");
            manifest.setProperty("elapsed-training-seconds", Double.toString((System.nanoTime() - state.startedNanos) / 1e9));
            DcFiles.write(state.outputRoot.resolve("manifest.properties"), manifest, true);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
