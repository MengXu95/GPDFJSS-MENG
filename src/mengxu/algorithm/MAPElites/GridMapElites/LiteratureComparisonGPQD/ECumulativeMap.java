package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.util.ParameterDatabase;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.GridMapElites.GPRuleEvolutionStateGridMAPElites;
import mengxu.algorithm.MAPElites.GridMapElites.gridPCmap;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.niching.phenotypicForSurrogateV1;
import ec.gp.GPIndividual;

final class ECumulativeMap {
    static final int SIDE = 81;
    final NativeState state = new NativeState();
    final HeterogeneousSimulation reference;
    final SchedulingSet referenceSet;
    long membersSeen;
    int referenceSimulations;

    static final class NativeState extends GPRuleEvolutionStateGridMAPElites {
        void probes(HeterogeneousSimulation simulation) { configurePhenoCharacterisation(simulation); }
    }

    ECumulativeMap(EConfiguration config, long seed, ParameterDatabase parameters) {
        reference = new HeterogeneousSimulation(seed, null, null, config.machines, config.jobs,
                config.warmup, 1, config.machines, .85, config.dueFactor, false);
        referenceSet = new SchedulingSet(Collections.singletonList(reference),
                Collections.singletonList(1), Collections.singletonList(Objective.MEAN_TARDINESS));
        state.parameters = parameters;
        state.penalty_level = 0;
        state.reevaluateGridMap = false;
        state.probes(reference);
        if (state.base != 3 || state.gridSIze != SIDE) {
            throw new IllegalStateException("Native descriptor resolution changed");
        }
        state.gridPCmap = new gridPCmap(SIDE, 3, 1);
        state.population = new Population();
        state.population.subpops = new Subpopulation[]{new Subpopulation()};
    }

    double benchmark() {
        // Native population evaluation ends with reset(); retain that phase before the native benchmark.
        reference.reset();
        referenceSet.lowerBoundsFromBenchmarkRule(Collections.singletonList(Objective.MEAN_TARDINESS));
        referenceSimulations++;
        double value = referenceSet.getObjectiveLowerBoundMtx().getEntry(0, 0);
        if (!Double.isFinite(value) || value <= 0 || value >= Double.MAX_VALUE) {
            throw new IllegalStateException("Native ATC/WIQ reference failed: " + value);
        }
        return value;
    }

    double[] update(int generation, Individual[] population) {
        if (generation != referenceSimulations) {
            throw new IllegalStateException("Every generation must update the cumulative map exactly once");
        }
        state.generation = generation;
        double normaliser = benchmark();
        // Sort a separate array, exactly as the native map path does, without reordering Lexicase parents.
        state.population.subpops[0].individuals = population.clone();
        PopulationUtils.sort(state.population);
        Individual[] ordered = state.population.subpops[0].individuals;
        double[][] coordinates = phenotypicForSurrogateV1
                .phenotypicPopulationFixedDecisions(state, state.phenoCharacterisation, false)[0];
        double[] scores = new double[ordered.length];
        for (int i = 0; i < ordered.length; i++) {
            scores[i] = ordered[i].fitness.fitness() / normaliser;
            if (Double.isNaN(scores[i]) || scores[i] < 0 || coordinates[i].length != 8) {
                throw new IllegalStateException("Invalid native map input at generation " + generation);
            }
            for (int j = 0; j < 8; j++) {
                coordinates[i][j] -= 1;
                if (coordinates[i][j] < 0 || coordinates[i][j] > 2
                        || coordinates[i][j] != Math.rint(coordinates[i][j])) {
                    throw new IllegalStateException("Invalid native rank coordinate");
                }
            }
        }
        state.gridPCmap.updateGridMap(state, ordered, coordinates, scores);
        membersSeen += population.length;
        int occupied = state.gridPCmap.getNumberIndividualsInGridMap(), finite = 0;
        for (int x = 0; x < SIDE; x++) for (int y = 0; y < SIDE; y++) {
            if (state.gridPCmap.fitnesses_each_cell[x][y].size() > 1) {
                throw new IllegalStateException("Cell capacity must be one");
            }
            for (double score : state.gridPCmap.fitnesses_each_cell[x][y]) {
                if (score < Double.POSITIVE_INFINITY && score < Double.MAX_VALUE) { finite++; }
            }
        }
        double mean = state.gridPCmap.calculateMeanPerformance();
        double best = state.gridPCmap.calculateBestPerformance(state);
        if (finite == 0 || !Double.isFinite(mean) || !Double.isFinite(best)) {
            throw new IllegalStateException("Native archive has no reportable finite quality");
        }
        return new double[]{100.0 * occupied / (SIDE * SIDE), mean, best,
                occupied, finite, occupied - finite, normaliser};
    }

    void save(Path path) throws IOException {
        try (BufferedWriter writer = EFiles.create(path)) {
            EFiles.row(writer, "sequencing_cell", "routing_cell", "source_generation",
                    "normalised_training_fitness", "tree_hash", "sequencing", "routing");
            for (int x = 0; x < SIDE; x++) for (int y = 0; y < SIDE; y++) {
                if (state.gridPCmap.inds_each_cell[x][y].isEmpty()) { continue; }
                GPIndividual member = (GPIndividual) state.gridPCmap.inds_each_cell[x][y].get(0);
                String seq = member.trees[0].child.makeLispTree(), rout = member.trees[1].child.makeLispTree();
                double score = state.gridPCmap.fitnesses_each_cell[x][y].get(0);
                ArchiveCandidate candidate = new ArchiveCandidate(path, 0, score, seq, rout);
                EFiles.row(writer, x, y, state.gridPCmap.indsFromGen_each_cell[x][y].get(0),
                        score, candidate.treeHash(), seq, rout);
            }
        }
    }
}
