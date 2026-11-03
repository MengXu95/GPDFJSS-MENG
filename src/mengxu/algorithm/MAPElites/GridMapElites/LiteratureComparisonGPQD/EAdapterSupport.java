package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.util.Parameter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class EAdapterSupport {
    private EAdapterSupport() { }

    static String text(EvolutionState state, String key) {
        String value = state.parameters.getString(new Parameter(key), null);
        if (value == null || value.isEmpty()) { throw new IllegalArgumentException("Missing parameter: " + key); }
        return value;
    }
    static int integer(EvolutionState state, String key) { return Integer.parseInt(text(state, key)); }
    static long seed(EvolutionState state, String key) { return Long.parseLong(text(state, key)); }
    static double decimal(EvolutionState state, String key) { return Double.parseDouble(text(state, key)); }

    static Path marker(Path root) { return root.resolve("manifest.properties"); }

    static Properties prepared(EvolutionState state, Path root, String method) throws IOException {
        if (!Files.isDirectory(root)) { throw new IOException("Attempt directory must be prepared by the study driver"); }
        Properties marker = EFiles.read(marker(root));
        if (!"RUNNING".equals(marker.getProperty("status"))) { throw new IOException("Attempt is not RUNNING"); }
        for (String key : new String[]{"family-hash", "case-hash", "case-key", "method", "regime",
                "scenario-id", "run-id", "utilisation"}) {
            if (!text(state, "e." + key).equals(EFiles.required(marker, key))) {
                throw new IOException("Prepared attempt metadata mismatch: " + key);
            }
        }
        if (!method.equals(marker.getProperty("method"))) { throw new IOException("Incorrect method adapter"); }
        String objective = text(state, "eval.problem.eval-model.objectives.0");
        if (!objective.equals(EFiles.required(marker, "objective"))) { throw new IOException("Objective mismatch"); }
        return marker;
    }

    static Properties begin(EvolutionState state, Path root, String kind, String finalFile) throws IOException {
        Properties manifest = EFiles.read(marker(root));
        try (PrintWriter writer = new PrintWriter(EFiles.create(root.resolve("resolved.params")))) {
            state.parameters.list(writer, false);
            if (writer.checkError()) { throw new IOException("Cannot save resolved parameters"); }
        }
        manifest.setProperty("output-kind", kind);
        manifest.setProperty("final-file", finalFile);
        manifest.setProperty("history-archive", "none; periodic snapshots are diagnostics, never a history union");
        manifest.setProperty("case-definition", "equal fixed job-ID blocks over recorded jobs after warmup");
        manifest.setProperty("case-cost", "diagnostic case vector from the fitness simulation; no extra case simulations");
        manifest.setProperty("raw-objective-policy", "export native unnormalized whole-instance objective");
        manifest.setProperty("reset-policy", "reset every training simulation to its current seed before EVERY individual");
        manifest.setProperty("shop-policy", "fixed shop generated at initial training seed; reset controls job streams");
        manifest.setProperty("paired-initialization", "separate paired GP seed; sorted genotype/evaluated-tuple multisets ignore population order");
        manifest.setProperty("birth-seed-equivalence", "initial evaluated batch is paired; later per-generation birth seeds are NOT identical across methods");
        manifest.setProperty("data-adaptation", "fixed: no seed rotation; rotating: native method phase, seed step 10000");
        manifest.setProperty("age-penalty", "0");
        manifest.setProperty("case-count", text(state, "num-case"));
        manifest.setProperty("java-runtime", System.getProperty("java.runtime.version"));
        manifest.setProperty("resolved-params-sha256", EFiles.hash(root.resolve("resolved.params")));
        manifest.setProperty("nominal-fitness-evaluations", Long.toString(Math.multiplyExact(
                (long) state.numGenerations, integer(state, "pop.subpop.0.size"))));
        EFiles.write(marker(root), manifest, true);
        return manifest;
    }

    static boolean finite(double value) { return Double.isFinite(value) && value < Double.MAX_VALUE && value >= 0; }

    static void failed(Path root, Throwable failure) {
        if (root == null || !Files.isRegularFile(marker(root))) { return; }
        try {
            Properties manifest = EFiles.read(marker(root));
            manifest.setProperty("status", "FAILED");
            manifest.setProperty("failure", failure.toString());
            EFiles.write(marker(root), manifest, true);
        } catch (IOException reporting) { failure.addSuppressed(reporting); }
    }
}
