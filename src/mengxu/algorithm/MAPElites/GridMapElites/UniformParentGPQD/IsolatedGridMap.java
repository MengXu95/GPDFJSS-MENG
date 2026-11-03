package mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD;

import ec.EvolutionState;
import ec.Individual;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import mengxu.algorithm.MAPElites.GridMapElites.gridPCmap;

public class IsolatedGridMap extends gridPCmap {
    private final List<Integer> occupied = new ArrayList<>();
    private final boolean[] present;

    public IsolatedGridMap(int side, int base) {
        super(side, base, 1);
        present = new boolean[Math.multiplyExact(side, side)];
    }

    private void track(double[] descriptor) {
        int[] location = calculateUniqueValue(descriptor);
        int key = location[0] * grid_size + location[1];
        if (!inds_each_cell[location[0]][location[1]].isEmpty() && !present[key]) {
            present[key] = true;
            occupied.add(key);
        }
    }

    @Override public void updateGridMap(EvolutionState state, Individual[] individuals,
                                        double[][] descriptors, double[] scores) {
        super.updateGridMap(state, individuals, descriptors, scores);
        for (double[] descriptor : descriptors) { track(descriptor); }
    }

    @Override public void updateGridMapWithIndividual(EvolutionState state, Individual individual,
                                                      double[] descriptor, double score) {
        super.updateGridMapWithIndividual(state, individual, descriptor, score);
        track(descriptor);
    }

    public int occupiedCount() { return occupied.size(); }
    public int keyAt(int index) { return occupied.get(index); }
    public Individual individualAt(int index) {
        int key = keyAt(index);
        return inds_each_cell[key / grid_size][key % grid_size].get(0);
    }
    public double scoreAt(int index) {
        int key = keyAt(index);
        return fitnesses_each_cell[key / grid_size][key % grid_size].get(0);
    }
    public int generationAt(int index) {
        int key = keyAt(index);
        return indsFromGen_each_cell[key / grid_size][key % grid_size].get(0);
    }

    @Override public void writeAllFitnessInGridMapToCSV(EvolutionState state) {
        // The statistics hook writes interval-controlled snapshots into the isolated output.
        if (!(state instanceof UniformParentState)) {
            throw new IllegalArgumentException("Isolated output requires UniformParentState");
        }
    }

    @Override public void saveCoverageAndMeanPerformanceAbdBestPerformanceToCSV(EvolutionState state, boolean output) {
        super.saveCoverageAndMeanPerformanceAbdBestPerformanceToCSV(state, false);
        if (output) {
            UniformParentState owner = (UniformParentState) state;
            try (BufferedWriter writer = UniformFiles.create(owner.outputRoot.resolve("archive_curve.csv"))) {
                UniformFiles.row(writer, "Generation", "Coverage", "Mean", "Best");
                for (int i = 0; i < allResults.size(); i++) {
                    List<Double> values = allResults.get(i);
                    UniformFiles.row(writer, i, values.get(0),
                            Double.isFinite(values.get(1)) ? values.get(1) : "NA", values.get(2));
                }
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }
}
