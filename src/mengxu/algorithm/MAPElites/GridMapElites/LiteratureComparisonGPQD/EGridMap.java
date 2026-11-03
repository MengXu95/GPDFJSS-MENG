package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Individual;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.IsolatedGridMap;

public final class EGridMap extends IsolatedGridMap {
    private final EGridEvaluation.EvaluationRecord[] records;

    public EGridMap(int side, int candidates) {
        super(side, candidates);
        if (side != 81 || candidates != 3) { throw new IllegalArgumentException("The literature grid is 81x81, c=3"); }
        records = new EGridEvaluation.EvaluationRecord[Math.multiplyExact(side, side)];
    }

    private Individual stored(int[] location) {
        return inds_each_cell[location[0]][location[1]].isEmpty()
                ? null : inds_each_cell[location[0]][location[1]].get(0);
    }
    private void observe(EvolutionState raw, Individual source, int[] location, Individual before) {
        if (stored(location) != before) {
            records[location[0] * grid_size + location[1]] = ((EGridState) raw).model().record(source);
        }
    }
    @Override public void updateGridMap(EvolutionState raw, Individual[] individuals,
                                       double[][] descriptors, double[] scores) {
        // Preserve the native ordered bulk-admission loop, including collisions, without changing its comparator.
        for (int i = 0; i < individuals.length; i++) {
            int[] location = calculateUniqueValue(descriptors[i]);
            Individual before = stored(location);
            super.updateGridMap(raw, new Individual[]{individuals[i]},
                    new double[][]{descriptors[i]}, new double[]{scores[i]});
            observe(raw, individuals[i], location, before);
        }
    }
    @Override public void updateGridMapWithIndividual(EvolutionState raw, Individual individual,
                                                      double[] descriptor, double score) {
        int[] location = calculateUniqueValue(descriptor);
        Individual before = stored(location);
        super.updateGridMapWithIndividual(raw, individual, descriptor, score);
        observe(raw, individual, location, before);
    }
    public EGridEvaluation.EvaluationRecord recordAt(int index) {
        EGridEvaluation.EvaluationRecord result = records[keyAt(index)];
        if (result == null) { throw new IllegalStateException("Missing archive birth-evaluation metadata"); }
        return result;
    }
    @Override public void writeAllFitnessInGridMapToCSV(EvolutionState state) {
        if (!(state instanceof EGridState)) { throw new IllegalArgumentException("Use EGridState"); }
    }
    @Override public void saveCoverageAndMeanPerformanceAbdBestPerformanceToCSV(EvolutionState raw, boolean output) {
        if (!(raw instanceof EGridState)) { throw new IllegalArgumentException("Use EGridState"); }
        super.saveCoverageAndMeanPerformanceAbdBestPerformanceToCSV(raw, false);
        if (!output) { return; }
        EGridState state = (EGridState) raw;
        try (BufferedWriter writer = EFiles.create(state.outputRoot.resolve("archive_curve.csv"))) {
            EFiles.row(writer, "generation", "native_coverage", "stored_comparison_mean", "stored_comparison_best");
            for (int i = 0; i < allResults.size(); i++) {
                List<Double> values = allResults.get(i);
                EFiles.row(writer, i, values.get(0),
                        Double.isFinite(values.get(1)) ? values.get(1) : "NA", values.get(2));
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
