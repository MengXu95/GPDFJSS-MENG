package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.gp.GPIndividual;
import ec.util.ParameterDatabase;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.GridMapElites.gridPCmap;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.niching.phenotypicForSurrogateV1;

final class ERotatingCumulativeMap {
    static final int SIDE = 81;
    static final double AGE_PENALTY = 5;
    final ECumulativeMap.NativeState state = new ECumulativeMap.NativeState();
    final BenchmarkSimulation reference;
    final SchedulingSet referenceSet;
    final long initialSeed, seedStep;
    final List<Double> references = new ArrayList<>();
    long membersSeen;
    int referenceSimulations;

    record Metrics(double coverage, double mean, double best, int occupied, int finite, int failed,
                   double normaliser, double minimumStored, double bestAged, int bestGeneration) { }

    static final class BenchmarkSimulation extends HeterogeneousSimulation {
        int completed;
        BenchmarkSimulation(EConfiguration c, long seed) {
            super(seed, null, null, c.machines, c.jobs, c.warmup, 1, c.machines, .85, c.dueFactor, false);
        }
        @Override public void run() {
            super.run();
            completed = getThroughput();
        }
    }

    ERotatingCumulativeMap(EConfiguration config, long initialSeed, ParameterDatabase parameters) {
        this.initialSeed = initialSeed; seedStep = config.seedStep;
        reference = new BenchmarkSimulation(config, initialSeed);
        referenceSet = new SchedulingSet(Collections.singletonList(reference),
                Collections.singletonList(1), Collections.singletonList(Objective.MEAN_TARDINESS));
        state.parameters = parameters;
        state.penalty_level = AGE_PENALTY;
        state.reevaluateGridMap = false;
        state.probes(reference);
        if (state.base != 3 || state.gridSIze != SIDE) {
            throw new IllegalStateException("Native D4/C3 descriptor resolution changed");
        }
        state.gridPCmap = new gridPCmap(SIDE, 3, 1);
        state.population = new Population();
        state.population.subpops = new Subpopulation[]{new Subpopulation()};
    }

    double benchmark(long currentSeed) {
        // Reset the current job stream, not the shop; preserve the native setter/rerun reference phase.
        reference.reset(currentSeed);
        referenceSet.lowerBoundsFromBenchmarkRule(Collections.singletonList(Objective.MEAN_TARDINESS));
        referenceSimulations++;
        double value = referenceSet.getObjectiveLowerBoundMtx().getEntry(0, 0);
        if (reference.completed != reference.getNumJobsRecorded()
                || !Double.isFinite(value) || value <= 0 || value >= Double.MAX_VALUE) {
            throw new IllegalStateException("Native rotating ATC/WIQ reference failed: " + value);
        }
        return value;
    }

    Metrics update(int generation, long currentSeed, Individual[] population) {
        long expectedSeed = Math.addExact(initialSeed, Math.multiplyExact((long) generation, seedStep));
        if (generation != referenceSimulations || generation != references.size()
                || currentSeed != expectedSeed || population.length != 500) {
            throw new IllegalStateException("Missing generation, population, or native rotating seed");
        }
        state.generation = generation;
        double normaliser = benchmark(currentSeed);
        references.add(normaliser);
        // Native sorting is observational: the actual LexGP population/parent order stays unchanged.
        state.population.subpops[0].individuals = population.clone();
        PopulationUtils.sort(state.population);
        Individual[] ordered = state.population.subpops[0].individuals;
        double[][] coordinates = phenotypicForSurrogateV1
                .phenotypicPopulationFixedDecisions(state, state.phenoCharacterisation, false)[0];
        double[] scores = new double[ordered.length];
        for (int i = 0; i < ordered.length; i++) {
            scores[i] = ordered[i].fitness.fitness() / normaliser;
            if (Double.isNaN(scores[i]) || scores[i] < 0 || coordinates[i].length != 8) {
                throw new IllegalStateException("Invalid native rotating-map input");
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
        return metrics(state, normaliser);
    }

    static Metrics metrics(ECumulativeMap.NativeState state, double normaliser) {
        gridPCmap map = state.gridPCmap;
        int occupied = 0, finite = 0, selectedGeneration = state.generation;
        double minimum = Double.POSITIVE_INFINITY, selected = Double.MAX_VALUE;
        for (int x = 0; x < SIDE; x++) for (int y = 0; y < SIDE; y++) {
            if (map.fitnesses_each_cell[x][y].size() > 1) { throw new IllegalStateException("Expected capacity one"); }
            if (map.fitnesses_each_cell[x][y].isEmpty()) { continue; }
            occupied++;
            double score = map.fitnesses_each_cell[x][y].get(0);
            int born = map.indsFromGen_each_cell[x][y].get(0);
            if (Double.isNaN(score) || score < 0 || born < 0 || born > state.generation) {
                throw new IllegalStateException("Invalid retained entry");
            }
            if (score < Double.POSITIVE_INFINITY && score < Double.MAX_VALUE) {
                finite++; minimum = Math.min(minimum, score);
            }
            if (map.betterThanBasedOnFitAndGen(score, born, selected, selectedGeneration, 0, state, 0)) {
                selected = score; selectedGeneration = born;
            }
        }
        double mean = map.calculateMeanPerformance(), best = map.calculateBestPerformance(state);
        double aged = selected * Math.pow(1 + state.penalty_level, state.generation - selectedGeneration);
        if (finite == 0 || !Double.isFinite(mean) || !Double.isFinite(best)
                || best >= Double.MAX_VALUE || !Double.isFinite(aged)
                || Double.compare(best, selected) != 0 || occupied != map.getNumberIndividualsInGridMap()) {
            throw new IllegalStateException("Unreportable or inconsistent native rotating-map metrics");
        }
        return new Metrics(100.0 * occupied / (SIDE * SIDE), mean, best, occupied, finite,
                occupied - finite, normaliser, minimum, aged, selectedGeneration);
    }

    void save(Path path) throws IOException {
        try (BufferedWriter writer = EFiles.create(path)) {
            EFiles.row(writer, "sequencing_cell", "routing_cell", "source_generation", "source_training_seed",
                    "raw_training_objective", "source_normaliser", "normalised_training_fitness",
                    "age_adjusted_fitness", "tree_hash", "sequencing", "routing");
            for (int x = 0; x < SIDE; x++) for (int y = 0; y < SIDE; y++) {
                if (state.gridPCmap.inds_each_cell[x][y].isEmpty()) { continue; }
                GPIndividual member = (GPIndividual) state.gridPCmap.inds_each_cell[x][y].get(0);
                int born = state.gridPCmap.indsFromGen_each_cell[x][y].get(0);
                double score = state.gridPCmap.fitnesses_each_cell[x][y].get(0);
                double raw = member.fitness.fitness(), sourceReference = references.get(born);
                if (Double.compare(raw / sourceReference, score) != 0) {
                    throw new IllegalStateException("Retained raw/reference score provenance differs");
                }
                String seq = member.trees[0].child.makeLispTree(), rout = member.trees[1].child.makeLispTree();
                ArchiveCandidate candidate = new ArchiveCandidate(path, 0, score, seq, rout);
                EFiles.row(writer, x, y, born, Math.addExact(initialSeed, Math.multiplyExact((long) born, seedStep)),
                        raw, sourceReference, score, score * Math.pow(1 + AGE_PENALTY, state.generation - born),
                        candidate.treeHash(), seq, rout);
            }
        }
    }
}
