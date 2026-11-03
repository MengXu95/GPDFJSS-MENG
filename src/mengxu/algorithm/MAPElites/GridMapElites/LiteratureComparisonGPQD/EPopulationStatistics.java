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
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseFitness;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots;

public final class EPopulationStatistics extends Statistics {
    private BufferedWriter generations;
    private Properties manifest;

    public static String genotypeHash(Individual[] individuals) { return multisetHash(individuals, false); }
    public static String evaluatedPopulationHash(Individual[] individuals) { return multisetHash(individuals, true); }
    private static String multisetHash(Individual[] individuals, boolean evaluated) {
        if (individuals == null || individuals.length == 0) { throw new IllegalArgumentException("Empty initial population"); }
        List<String> tuples = new ArrayList<>();
        for (Individual member : individuals) {
            GPIndividual individual = (GPIndividual) member;
            if (individual.trees.length != 2 || (evaluated && !individual.evaluated)) {
                throw new IllegalArgumentException("Two-tree evaluated initialization required");
            }
            String seq = individual.trees[0].child.makeLispTree(), rout = individual.trees[1].child.makeLispTree();
            String tuple = seq.length() + ":" + seq + rout.length() + ":" + rout;
            if (evaluated) {
                double raw = member.fitness.fitness();
                tuple += ":" + (EAdapterSupport.finite(raw) ? Double.toHexString(raw) : "FAIL");
            }
            tuples.add(tuple);
        }
        Collections.sort(tuples);
        StringBuilder text = new StringBuilder();
        for (String tuple : tuples) { text.append(tuple.length()).append(':').append(tuple).append('\n'); }
        return ArchiveCandidate.sha256(text.toString());
    }

    @Override public void preInitializationStatistics(EvolutionState raw) {
        super.preInitializationStatistics(raw);
        EPopulationState state = (EPopulationState) raw;
        try {
            manifest = EAdapterSupport.begin(state, state.outputRoot(), "POPULATION", "final_population.stat");
            manifest.setProperty("normalization", "none; same-batch raw scalar/case selection, raw exported objectives");
            manifest.setProperty("evaluation-phase", "population after previous breeding and seed rotation; all members including elites reevaluated");
            manifest.setProperty("selection", state.methodLabel().startsWith("Lexicase")
                    ? "unchanged PopulationLexicaseSelection / EpsilonTTGenNoRepeatCandidateLexicaseSelection"
                    : "standard ec.select.TournamentSelection; size=7; scalar raw fitness only; diagnostic cases ignored");
            manifest.setProperty("parent-source", "current evaluated population only");
            manifest.setProperty("initial-training-seed", Long.toString(state.initialTrainingSeed));
            generations = EFiles.create(state.outputRoot().resolve("generations.csv"));
            EFiles.row(generations, "run_id", "method", "generation", "training_seed", "population_size",
                    "finite_members", "failed_members", "best_raw_objective", "finite_mean_raw_objective",
                    "fitness_evaluations", "failed_training_evaluations", "case_value_slots",
                    "manual_reference_simulations", "descriptor_reference_simulations", "total_training_simulations",
                    "evaluation_seconds", "elapsed_seconds", "snapshot", "effective_lexicase_candidates");
            EFiles.write(EAdapterSupport.marker(state.outputRoot()), manifest, true);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public void postEvaluationStatistics(EvolutionState raw) {
        super.postEvaluationStatistics(raw);
        EPopulationState state = (EPopulationState) raw;
        Individual[] population = state.population.subpops[0].individuals;
        int finite = 0;
        double mean = 0, best = Double.POSITIVE_INFINITY;
        for (Individual individual : population) {
            if (((LexicaseFitness) individual.fitness).isValidEvaluation()) {
                finite++;
                double value = individual.fitness.fitness();
                mean += (value - mean) / finite; best = Math.min(best, value);
            }
        }
        if (finite == 0) { throw new IllegalStateException("FAILED: all population members failed in generation " + state.generation); }
        long expected = Math.multiplyExact((long) state.generation + 1, population.length);
        if (state.model().simulationCount() != expected) {
            throw new IllegalStateException("Actual population simulation count differs from the evaluated-batch budget");
        }
        boolean last = state.generation == state.numGenerations - 1;
        boolean snapshot = state.generation % state.snapshotInterval() == 0 || last;
        double elapsed = (System.nanoTime() - state.startedNanos) / 1e9;
        try {
            if (state.generation == 0) {
                save(state, "initial_population");
                manifest.setProperty("initial-genotype-sha256", genotypeHash(population));
                manifest.setProperty("initial-evaluated-population-sha256", evaluatedPopulationHash(population));
            }
            if (snapshot) { save(state, String.format(Locale.ROOT, "population.gen%04d", state.generation)); }
            if (last) { save(state, "final_population"); }
            long simulations = state.model().simulationCount();
            long slots = Math.multiplyExact(simulations, state.model().caseCount());
            EFiles.row(generations, state.runId(), state.methodLabel(), state.generation,
                    state.model().trainingSeed(), population.length, finite, population.length - finite,
                    best, mean, simulations, state.model().failedCount(), slots, 0, 0, simulations,
                    state.evaluationSeconds(), elapsed, snapshot, state.methodLabel().startsWith("Lexicase")
                            ? Math.min(finite, EAdapterSupport.integer(state, "candidate-size")) : 0);
            generations.flush();
            manifest.setProperty("last-evaluated-generation", Integer.toString(state.generation));
            manifest.setProperty("fitness-evaluations", Long.toString(simulations));
            manifest.setProperty("failed-training-evaluations", Long.toString(state.model().failedCount()));
            manifest.setProperty("manual-reference-simulations", "0");
            manifest.setProperty("descriptor-reference-simulations", "0");
            manifest.setProperty("total-training-simulations", Long.toString(simulations));
            manifest.setProperty("case-value-slots", Long.toString(slots));
            manifest.setProperty("final-members", Integer.toString(population.length));
            manifest.setProperty("stored-finite-members", Integer.toString(finite));
            manifest.setProperty("final-training-seed", Long.toString(state.model().trainingSeed()));
            manifest.setProperty("elapsed-training-seconds", Double.toString(elapsed));
            EFiles.write(EAdapterSupport.marker(state.outputRoot()), manifest, true);
            if (snapshot) {
                state.progress.println("  " + EAdapterSupport.text(state, "e.case-key") + " generation=" + state.generation
                        + " evaluations=" + simulations + " finite=" + finite + " elapsed="
                        + String.format(Locale.ROOT, "%.1f", elapsed) + "s");
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private static void save(EPopulationState state, String stem) throws IOException {
        Individual[] individuals = state.population.subpops[0].individuals;
        Path snapshot = state.outputRoot().resolve(stem + ".stat");
        PopulationSnapshots.write(snapshot, state.methodLabel(), state.runId(), state.generation,
                state.model().trainingSeed(), individuals);
        try (BufferedWriter writer = EFiles.create(state.outputRoot().resolve(stem + ".csv"))) {
            List<Object> header = new ArrayList<>();
            Collections.addAll(header, "candidate_id", "run_id", "method", "generation", "training_seed",
                    "population_index", "raw_objective", "valid", "source_comparison_score", "tree_hash");
            for (int i = 0; i < state.model().caseCount(); i++) { header.add("case_" + i); }
            Collections.addAll(header, "sequencing", "routing");
            EFiles.row(writer, header.toArray());
            for (int index = 0; index < individuals.length; index++) {
                GPIndividual individual = (GPIndividual) individuals[index];
                LexicaseFitness fitness = (LexicaseFitness) individual.fitness;
                String seq = individual.trees[0].child.makeLispTree(), rout = individual.trees[1].child.makeLispTree();
                ArchiveCandidate candidate = new ArchiveCandidate(snapshot, index, fitness.fitness(), seq, rout);
                List<Object> row = new ArrayList<>();
                Collections.addAll(row, candidate.id(), state.runId(), state.methodLabel(), state.generation,
                        state.model().trainingSeed(), index, fitness.fitness(), fitness.isValidEvaluation(),
                        fitness.fitness(), candidate.treeHash());
                for (double[] value : fitness.multiInstanceMultiObjectiveFitness) { row.add(value[0]); }
                Collections.addAll(row, seq, rout);
                EFiles.row(writer, row.toArray());
            }
        }
    }

    public void closeResources() throws IOException {
        if (generations != null) {
            BufferedWriter closing = generations; generations = null; closing.close();
        }
    }
    @Override public void finalStatistics(EvolutionState raw, int result) {
        super.finalStatistics(raw, result);
        EPopulationState state = (EPopulationState) raw;
        try {
            closeResources();
            if (state.generation != state.numGenerations - 1
                    || !"final_population.stat".equals(manifest.getProperty("final-file"))
                    || Long.parseLong(manifest.getProperty("fitness-evaluations")) !=
                    Math.multiplyExact((long) state.numGenerations, state.population.subpops[0].individuals.length)) {
                throw new IllegalStateException("Population search did not finish its evaluated-batch budget");
            }
            manifest.setProperty("status", "SEARCH_COMPLETE");
            manifest.setProperty("elapsed-training-seconds", Double.toString((System.nanoTime() - state.startedNanos) / 1e9));
            EFiles.write(EAdapterSupport.marker(state.outputRoot()), manifest, true);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
