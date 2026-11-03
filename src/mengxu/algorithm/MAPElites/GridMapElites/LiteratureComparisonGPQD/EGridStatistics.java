package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Individual;
import ec.Statistics;
import ec.gp.GPIndividual;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots;

public final class EGridStatistics extends Statistics {
    private BufferedWriter generations;
    private Properties manifest;
    private long evaluationStart;

    @Override public void preInitializationStatistics(EvolutionState raw) {
        super.preInitializationStatistics(raw);
        EGridState state = (EGridState) raw;
        try {
            manifest = EAdapterSupport.begin(state, state.outputRoot, "GRID_ARCHIVE", "final_archive.stat");
            state.descriptorBank.write(state.outputRoot.resolve("snapshot_bank.csv"), 4, 3);
            state.descriptorBank.write(state.outputRoot.resolve("training_descriptor.csv"), 4, 3);
            manifest.setProperty("normalization", "true; manual-reference division ONLY for candidate/archive comparisons; raw fitness untouched");
            manifest.setProperty("evaluation-phase", "native immediate offspring evaluation/live archive admission BEFORE rotation; population elites reevaluated AFTER rotation");
            manifest.setProperty("selection", "unchanged ArchiveParentSelection; hybrid random p=0.5 and archive-size-scaled dynamic tournament");
            manifest.setProperty("parent-source", "native live grid archive, including first offspring batch");
            manifest.setProperty("archive-reevaluation", "false; only inherited population elite reevaluation, historical archive keeps birth fitness");
            manifest.setProperty("descriptor-d", "4"); manifest.setProperty("descriptor-c", "3");
            manifest.setProperty("descriptor-seed", EAdapterSupport.text(state, "e.descriptor-seed"));
            manifest.setProperty("sampling-seed", EAdapterSupport.text(state, "e.sampling-seed"));
            manifest.setProperty("grid-side", Integer.toString(state.gridSIze));
            manifest.setProperty("native-grid-cells", Integer.toString(Math.multiplyExact(state.gridSIze, state.gridSIze)));
            manifest.setProperty("cell-capacity", "1");
            manifest.setProperty("initial-training-seed", Long.toString(state.initialTrainingSeed));
            manifest.setProperty("descriptor-reference-simulations", Long.toString(state.descriptorBank.simulationCount()));
            generations = EFiles.create(state.outputRoot.resolve("generations.csv"));
            EFiles.row(generations, "run_id", "method", "generation", "training_seed", "population_size",
                    "finite_population_members", "native_grid_cells", "native_occupied_cells", "retained_members",
                    "native_coverage", "finite_raw_members", "finite_mean_raw_objective", "best_raw_objective",
                    "fitness_evaluations", "failed_training_evaluations", "case_value_slots",
                    "manual_reference_simulations", "descriptor_reference_simulations", "total_training_simulations",
                    "evaluation_seconds", "elapsed_seconds", "snapshot");
            EFiles.write(EAdapterSupport.marker(state.outputRoot), manifest, true);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public void preEvaluationStatistics(EvolutionState raw) {
        super.preEvaluationStatistics(raw);
        EGridState state = (EGridState) raw;
        evaluationStart = System.nanoTime();
        if (!(state.gridPCmap instanceof EGridMap)) {
            if (state.generation != 0 || state.gridPCmap == null || state.gridPCmap.getNumberIndividualsInGridMap() != 0) {
                throw new IllegalStateException("Only the native empty generation-zero map may be wrapped");
            }
            state.gridPCmap = new EGridMap(state.gridSIze, state.base);
        }
    }

    @Override public void postEvaluationStatistics(EvolutionState raw) {
        super.postEvaluationStatistics(raw);
        EGridState state = (EGridState) raw;
        EGridMap map = (EGridMap) state.gridPCmap;
        int count = map.occupiedCount();
        if (count != map.getNumberIndividualsInGridMap() || map.grid_size != 81) {
            throw new IllegalStateException("Native capacity-one occupied-cell geometry mismatch");
        }
        int populationFinite = 0;
        for (Individual individual : state.population.subpops[0].individuals) {
            if (!individual.evaluated) { throw new IllegalStateException("Unevaluated QD population member"); }
            if (EAdapterSupport.finite(individual.fitness.fitness())) { populationFinite++; }
        }
        if (populationFinite == 0) { throw new IllegalStateException("FAILED: all QD population evaluations failed in generation " + state.generation); }
        int finite = 0;
        double mean = 0, best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            double value = map.individualAt(i).fitness.fitness();
            if (EAdapterSupport.finite(value)) {
                finite++; mean += (value - mean) / finite; best = Math.min(best, value);
            }
        }
        if (finite == 0) { throw new IllegalStateException("FAILED: no finite stored QD archive member"); }
        int population = state.population.subpops[0].individuals.length;
        long expected = Math.multiplyExact((long) state.generation + 1, population);
        if (state.model().fitnessCalls() != expected || state.model().countInd != expected
                || state.model().simulationCount() != expected) {
            throw new IllegalStateException("Actual QD evaluation/simulation count differs from the native evaluated-batch budget");
        }
        boolean last = state.generation == state.numGenerations - 1;
        boolean snapshot = state.generation % state.snapshotInterval == 0 || last;
        long references = state.model().referenceSimulationCount();
        long descriptorReferences = state.descriptorBank.simulationCount();
        long total = Math.addExact(state.model().simulationCount(), Math.addExact(references, descriptorReferences));
        double elapsed = (System.nanoTime() - state.startedNanos) / 1e9;
        double evaluationSeconds = (System.nanoTime() - evaluationStart) / 1e9;
        try {
            if (state.generation == 0) {
                Individual[] initial = state.population.subpops[0].individuals;
                PopulationSnapshots.write(state.outputRoot.resolve("initial_population.stat"), method(state),
                        state.runId, 0, state.initialTrainingSeed, initial);
                saveInitialCsv(state);
                manifest.setProperty("initial-genotype-sha256", EPopulationStatistics.genotypeHash(initial));
                manifest.setProperty("initial-evaluated-population-sha256", EPopulationStatistics.evaluatedPopulationHash(initial));
            }
            if (snapshot) { save(state, String.format(Locale.ROOT, "archive.gen%04d", state.generation)); }
            if (last) { save(state, "final_archive"); }
            EFiles.row(generations, state.runId, method(state), state.generation, state.trainingSeed(), population,
                    populationFinite, state.gridSIze * state.gridSIze, count, count,
                    count / (double) (state.gridSIze * state.gridSIze), finite, mean, best,
                    state.model().fitnessCalls(), state.model().failedCount(), state.model().caseSlots(),
                    references, descriptorReferences, total, evaluationSeconds, elapsed, snapshot);
            generations.flush();
            manifest.setProperty("last-evaluated-generation", Integer.toString(state.generation));
            manifest.setProperty("fitness-evaluations", Long.toString(state.model().fitnessCalls()));
            manifest.setProperty("failed-training-evaluations", Long.toString(state.model().failedCount()));
            manifest.setProperty("manual-reference-simulations", Long.toString(references));
            manifest.setProperty("total-training-simulations", Long.toString(total));
            manifest.setProperty("case-value-slots", Long.toString(state.model().caseSlots()));
            manifest.setProperty("native-occupied-cells", Integer.toString(count));
            manifest.setProperty("final-members", Integer.toString(count));
            manifest.setProperty("stored-finite-members", Integer.toString(finite));
            manifest.setProperty("final-training-seed", Long.toString(state.trainingSeed()));
            manifest.setProperty("elapsed-training-seconds", Double.toString(elapsed));
            EFiles.write(EAdapterSupport.marker(state.outputRoot), manifest, true);
            if (snapshot) {
                state.progress.println("  " + EAdapterSupport.text(state, "e.case-key") + " generation=" + state.generation
                        + " evaluations=" + expected + " occupied=" + count + " elapsed="
                        + String.format(Locale.ROOT, "%.1f", elapsed) + "s");
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private static String method(EGridState state) { return state.model().isRotatable() ? "QDGP" : "QDGPf"; }

    private static void saveInitialCsv(EGridState state) throws IOException {
        try (BufferedWriter writer = EFiles.create(state.outputRoot.resolve("initial_population.csv"))) {
            List<Object> header = new ArrayList<>();
            Collections.addAll(header, "population_index", "raw_objective", "valid", "evaluation_seed",
                    "birth_training_seed", "source_comparison_score", "tree_hash");
            for (int i = 0; i < state.model().caseCount(); i++) { header.add("case_" + i); }
            Collections.addAll(header, "sequencing", "routing"); EFiles.row(writer, header.toArray());
            Individual[] initial = state.population.subpops[0].individuals;
            for (int i = 0; i < initial.length; i++) {
                GPIndividual individual = (GPIndividual) initial[i];
                EGridEvaluation.EvaluationRecord record = state.model().record(individual);
                String seq = individual.trees[0].child.makeLispTree(), rout = individual.trees[1].child.makeLispTree();
                ArchiveCandidate candidate = new ArchiveCandidate(state.outputRoot.resolve("initial_population.stat"),
                        i, individual.fitness.fitness(), seq, rout);
                List<Object> row = new ArrayList<>();
                Collections.addAll(row, i, individual.fitness.fitness(), EAdapterSupport.finite(record.raw),
                        record.seed, record.seed, individual.fitness.fitness() / state.previousBaseline, candidate.treeHash());
                for (double value : record.cases()) { row.add(value); }
                Collections.addAll(row, seq, rout); EFiles.row(writer, row.toArray());
            }
        }
    }

    private static void save(EGridState state, String stem) throws IOException {
        EGridMap map = (EGridMap) state.gridPCmap;
        Path archive = state.outputRoot.resolve(stem + ".stat");
        try (BufferedWriter writer = EFiles.create(archive);
             BufferedWriter csv = EFiles.create(state.outputRoot.resolve(stem + ".csv"))) {
            writer.write("Producer: " + method(state) + "-literature\nAll individuals in GridMAP:\nGridMAP number: "
                    + map.occupiedCount() + "\n");
            List<Object> header = new ArrayList<>();
            Collections.addAll(header, "candidate_id", "run_id", "method", "generation", "seq_cell", "rout_cell",
                    "descriptor_digits_zero_based", "raw_training_fitness", "source_comparison_score",
                    "birth_generation", "birth_training_seed", "current_training_seed", "tree_hash");
            for (int i = 0; i < state.model().caseCount(); i++) { header.add("case_" + i); }
            Collections.addAll(header, "sequencing", "routing"); EFiles.row(csv, header.toArray());
            for (int i = 0; i < map.occupiedCount(); i++) {
                GPIndividual individual = (GPIndividual) map.individualAt(i);
                if (!individual.evaluated) { throw new IllegalStateException("Unevaluated archive member"); }
                EGridEvaluation.EvaluationRecord record = map.recordAt(i);
                double rawFitness = individual.fitness.fitness();
                if (EAdapterSupport.finite(record.raw) != EAdapterSupport.finite(rawFitness)
                        || (EAdapterSupport.finite(rawFitness) && Double.compare(record.raw, rawFitness) != 0)
                        || record.generation != map.generationAt(i)) {
                    throw new IllegalStateException("Archive birth diagnostics no longer match the stored native member");
                }
                String seq = individual.trees[0].child.makeLispTree(), rout = individual.trees[1].child.makeLispTree();
                ArchiveCandidate candidate = new ArchiveCandidate(archive, i, individual.fitness.fitness(), seq, rout);
                int key = map.keyAt(i), x = key / map.grid_size, y = key % map.grid_size;
                double[] digits = map.PCs_each_cell[x][y].get(0);
                int[] actual = map.calculateUniqueValue(digits);
                if (digits.length != 8 || actual[0] != x || actual[1] != y) {
                    throw new IllegalStateException("Exported native coordinates disagree with descriptor digits");
                }
                StringBuilder encoded = new StringBuilder();
                for (double digit : digits) {
                    if (digit != Math.rint(digit) || digit < 0 || digit >= 3) {
                        throw new IllegalStateException("Descriptor digit outside base three");
                    }
                    if (encoded.length() > 0) { encoded.append(';'); } encoded.append((int) digit);
                }
                writer.write("Evaluated: true\nFitness: [" + individual.fitness.fitness()
                        + "]\nTree 0:\n" + seq + "\nTree 1:\n" + rout + "\n");
                List<Object> row = new ArrayList<>();
                Collections.addAll(row, candidate.id(), state.runId, method(state), state.generation, x, y,
                        encoded, candidate.storedFitness(), map.scoreAt(i), map.generationAt(i), record.seed,
                        state.trainingSeed(), candidate.treeHash());
                for (double value : record.cases()) { row.add(value); }
                Collections.addAll(row, seq, rout); EFiles.row(csv, row.toArray());
            }
        }
    }

    void flushMetrics() {
        try { if (generations != null) { generations.flush(); } }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    public void closeResources() throws IOException {
        if (generations != null) { BufferedWriter closing = generations; generations = null; closing.close(); }
    }
    @Override public void finalStatistics(EvolutionState raw, int result) {
        super.finalStatistics(raw, result);
        EGridState state = (EGridState) raw;
        try {
            closeResources();
            if (state.generation != state.numGenerations || state.model().fitnessCalls() !=
                    Math.multiplyExact((long) state.numGenerations, state.population.subpops[0].individuals.length)) {
                throw new IllegalStateException("QD search did not finish its native evaluated-batch budget");
            }
            manifest.setProperty("status", "SEARCH_COMPLETE");
            manifest.setProperty("elapsed-training-seconds", Double.toString((System.nanoTime() - state.startedNanos) / 1e9));
            EFiles.write(EAdapterSupport.marker(state.outputRoot), manifest, true);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
