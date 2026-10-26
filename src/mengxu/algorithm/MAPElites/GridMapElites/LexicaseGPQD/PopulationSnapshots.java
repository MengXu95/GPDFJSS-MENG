package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import ec.Individual;
import ec.gp.GPIndividual;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;

public final class PopulationSnapshots {
    private PopulationSnapshots() { }

    public static final class Snapshot {
        private final int generation;
        private final long trainingSeed;
        private final List<ArchiveCandidate> candidates;
        Snapshot(int generation, long trainingSeed, List<ArchiveCandidate> candidates) {
            this.generation = generation;
            this.trainingSeed = trainingSeed;
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        }
        public int generation() { return generation; }
        public long trainingSeed() { return trainingSeed; }
        public List<ArchiveCandidate> candidates() { return candidates; }
    }

    public static void write(Path path, String method, int runId, int generation, long seed,
                             Individual[] individuals) throws IOException {
        if (individuals == null || individuals.length == 0 || method == null || method.trim().isEmpty()
                || runId < 0 || generation < 0) { throw new IllegalArgumentException("Invalid population snapshot metadata"); }
        try (BufferedWriter writer = LexicaseFiles.create(path)) {
            writer.write("Method: " + method + "\nRun: " + runId + "\nGeneration: " + generation
                    + "\nTraining seed: " + seed + "\nAll individuals in Population:\nPopulation number: "
                    + individuals.length + "\n");
            for (Individual individual : individuals) {
                if (!(individual instanceof GPIndividual) || ((GPIndividual) individual).trees.length != 2
                        || !individual.evaluated) {
                    throw new IllegalArgumentException("An evaluated two-tree population is required");
                }
                GPIndividual rule = (GPIndividual) individual;
                writer.write("Evaluated: true\nFitness: [" + individual.fitness.fitness() + "]\nTree 0:\n"
                        + rule.trees[0].child.makeLispTree() + "\nTree 1:\n"
                        + rule.trees[1].child.makeLispTree() + "\n");
            }
        }
    }

    public static Snapshot read(Path file) throws IOException {
        String before = LexicaseFiles.hash(file);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            required(reader, "Method: ");
            Integer.parseInt(required(reader, "Run: "));
            int generation = Integer.parseInt(required(reader, "Generation: "));
            long seed = Long.parseLong(required(reader, "Training seed: "));
            if (!"All individuals in Population:".equals(reader.readLine())) {
                throw new IllegalArgumentException("Missing population header");
            }
            int count = Integer.parseInt(required(reader, "Population number: "));
            if (count < 1 || generation < 0) { throw new IllegalArgumentException("Invalid population count/generation"); }
            List<ArchiveCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (!"Evaluated: true".equals(reader.readLine())) {
                    throw new IllegalArgumentException("Unevaluated/truncated member " + i);
                }
                String fitness = required(reader, "Fitness: [");
                if (!fitness.endsWith("]")) { throw new IllegalArgumentException("Invalid scalar fitness"); }
                double raw = Double.parseDouble(fitness.substring(0, fitness.length() - 1));
                if (!"Tree 0:".equals(reader.readLine())) { throw new IllegalArgumentException("Missing sequencing tree"); }
                String sequencing = reader.readLine();
                if (!"Tree 1:".equals(reader.readLine())) { throw new IllegalArgumentException("Missing routing tree"); }
                String routing = reader.readLine();
                ArchiveCandidate candidate = new ArchiveCandidate(file, i, raw, sequencing, routing);
                candidate.rules();
                candidates.add(candidate);
            }
            if (reader.readLine() != null) { throw new IllegalArgumentException("Unexpected trailing population data"); }
            if (!before.equals(LexicaseFiles.hash(file))) { throw new IOException("Population changed while reading: " + file); }
            return new Snapshot(generation, seed, candidates);
        } catch (IllegalArgumentException e) {
            throw new IOException(file + ": invalid population snapshot: " + e.getMessage(), e);
        }
    }

    private static String required(BufferedReader reader, String prefix) throws IOException {
        String value = reader.readLine();
        if (value == null || !value.startsWith(prefix)) { throw new IllegalArgumentException("Expected " + prefix); }
        return value.substring(prefix.length());
    }
}
