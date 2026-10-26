package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RevisionArchiveAnalysis {
    public static final class TrainingDiagnostics {
        public final List<List<Double>> storedPerformance = new ArrayList<>();
        public final List<List<Double>> complexity = new ArrayList<>();
        public final List<List<Double>> sensitivity = new ArrayList<>();
    }

    private RevisionArchiveAnalysis() { }

    public static TrainingDiagnostics analyze(String directory, int runs, int maximumCandidates, int trees) {
        if (runs < 1 || maximumCandidates < 1 || trees != 2) {
            throw new IllegalArgumentException("Positive counts and a two-tree rule representation are required");
        }
        try {
            Path archiveDirectory = Paths.get(directory).toRealPath();
            String scenario = archiveDirectory.getFileName().toString();
            int last = scenario.lastIndexOf('-');
            int previous = scenario.lastIndexOf('-', last - 1);
            if (previous < 0) {
                throw new IllegalArgumentException("Archive directory must use the paper scenario name");
            }
            double utilisation = Double.parseDouble(scenario.substring(previous + 1, last));
            double dueFactor = Double.parseDouble(scenario.substring(last + 1));
            SensitivityProbeSet probes = SensitivityProbeSet.collect(777, 8295342,
                    10, 1000, 500, utilisation, dueFactor, 3, 20);
            TrainingDiagnostics result = new TrainingDiagnostics();
            FinalArchiveReader reader = new FinalArchiveReader();
            for (int run = 0; run < runs; run++) {
                FinalArchiveReader.Archive archive = reader.read(
                        archiveDirectory.resolve("job." + run + ".out.stat"));
                List<ArchiveCandidate> finite = new ArrayList<>();
                for (ArchiveCandidate candidate : archive.candidates()) {
                    if (candidate.hasFiniteStoredFitness()) { finite.add(candidate); }
                }
                if (finite.isEmpty()) {
                    throw new IllegalArgumentException("No finite archived rules in run " + run);
                }
                finite.sort(Comparator.comparingDouble(ArchiveCandidate::storedFitness)
                        .thenComparingInt(ArchiveCandidate::ordinal));
                List<Double> performance = new ArrayList<>();
                List<Double> complexity = new ArrayList<>();
                List<Double> sensitivity = new ArrayList<>();
                for (ArchiveCandidate candidate : finite.subList(0, Math.min(maximumCandidates, finite.size()))) {
                    RevisionMetrics row = RevisionMetrics.compute(candidate, probes, 1.0e-3,
                            candidate.storedFitness());
                    performance.add(candidate.storedFitness());
                    complexity.add(row.complexity());
                    sensitivity.add(row.sensitivity());
                }
                result.storedPerformance.add(performance);
                result.complexity.add(complexity);
                result.sensitivity.add(sensitivity);
                System.out.println("TRAINING-METRIC DIAGNOSTIC ONLY: run=" + run
                        + ", archive=" + archive.declaredCount() + ", finite=" + finite.size()
                        + ", analysed=" + performance.size() + ", sensitivity="
                        + OutputSensitivityCalculator.VERSION);
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the final archive", e);
        }
    }

    public static void launch(String[] args) {
        try {
            RevisionExperiment.main(args);
        } catch (IOException e) {
            throw new UncheckedIOException("Revision experiment failed", e);
        }
    }

    public static void runHeldOut(String trainPath, String outputDirectory, int runs,
                                 int poolSize, int topK, int trees) {
        if (runs < 1 || trees != 2) {
            throw new IllegalArgumentException("A positive run count and two GP trees are required");
        }
        Path archive = Paths.get(trainPath).toAbsolutePath().normalize();
        Path dynamic = archive.getParent();
        if (dynamic == null || !dynamic.getFileName().toString().equals("multipletreegp-dynamic")
                || dynamic.getParent() == null || dynamic.getParent().getParent() == null) {
            throw new IllegalArgumentException("Use RevisionExperiment with an explicit input-root for nonstandard layouts");
        }
        Path method = dynamic.getParent();
        launch(new String[]{
            "--input-root", method.getParent().toString(),
            "--archive-dir", archive.toString(),
            "--output-root", outputDirectory,
            "--method", method.getFileName().toString(),
            "--scenario", archive.getFileName().toString(),
            "--runs", "0:" + (runs - 1),
            "--pool-size", Integer.toString(poolSize),
            "--top-k", Integer.toString(topK)
        });
    }
}
