package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import ec.EvolutionState;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.IsolatedGridMap;

public final class DcGridMap extends IsolatedGridMap {
    public DcGridMap(int side, int candidates) { super(side, candidates); }

    @Override public void writeAllFitnessInGridMapToCSV(EvolutionState state) {
        if (!(state instanceof DcState)) { throw new IllegalArgumentException("Use DcState"); }
    }

    @Override public void saveCoverageAndMeanPerformanceAbdBestPerformanceToCSV(EvolutionState raw, boolean output) {
        if (!(raw instanceof DcState)) { throw new IllegalArgumentException("Use DcState"); }
        super.saveCoverageAndMeanPerformanceAbdBestPerformanceToCSV(raw, false);
        if (output) {
            DcState state = (DcState) raw;
            try (BufferedWriter writer = DcFiles.create(state.outputRoot.resolve("archive_curve.csv"))) {
                DcFiles.row(writer, "generation", "native_coverage", "stored_comparison_mean", "stored_comparison_best");
                for (int i = 0; i < allResults.size(); i++) {
                    List<Double> row = allResults.get(i);
                    DcFiles.row(writer, i, row.get(0),
                            Double.isFinite(row.get(1)) ? row.get(1) : "NA", row.get(2));
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
}
