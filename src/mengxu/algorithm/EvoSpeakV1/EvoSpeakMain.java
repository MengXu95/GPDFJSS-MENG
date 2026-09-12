package mengxu.algorithm.EvoSpeakV1;

import ec.EvolutionState;
import ec.Evolve;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class EvoSpeakMain {
    private EvoSpeakMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("EvoSpeakMain [-file <params>] [-p name=value ...]\n"
                    + "No arguments: src/mengxu/algorithm/EvoSpeakV1/evospeak.params.\n"
                    + "Set llm.provider/model/endpoint and the API-key environment variable; or evospeak.source=file.");
            return;
        }
        EvoSpeakConfig config = EvoSpeakConfig.fromArgs(args);
        LlmClient client = config.text("evospeak.source", "llm").equals("llm") ? new LlmClient(config) : null;
        Path result = run(config, client);
        System.out.println("EvoSpeakV1 completed: " + result);
    }

    public static Path run(EvoSpeakConfig config, LlmClient client) throws IOException {
        if (config.text("evospeak.source", "llm").equals("llm") && client == null) {
            throw new IllegalArgumentException("A configured LLM client is required for evospeak.source=llm.");
        }
        Path root = config.path("evospeak.output-directory", "runs");
        Files.createDirectories(root);
        Path directory = Files.createTempDirectory(root, "run-");
        config.set("stat.file", directory.resolve("evolution.out.stat"));
        JSONObject status = new JSONObject().put("started", Instant.now().toString()).put("state", "preparing")
                .put("objectiveMode", config.text("evospeak.objective-mode", "multi"))
                .put("source", config.text("evospeak.source", "llm"))
                .put("provider", config.text("llm.provider", "" )).put("model", config.text("llm.model", ""))
                .put("seed", config.text("seed.0", "0"));
            JSONObject settings = new JSONObject();
            for (String key : new String[]{"evospeak.objective-mode", "evospeak.objective.0", "evospeak.objective.1",
                "evospeak.weight.0", "evospeak.weight.1", "evospeak.normalization", "pop.subpop.0.size", "generations",
                "evospeak.validation.jobs", "evospeak.validation.warmup", "evospeak.validation.seeds",
                "evospeak.max-tree-depth", "evospeak.max-tree-nodes", "evospeak.batch-size", "evospeak.max-batches",
                "eval.problem.eval-model.sim-models.0.util-level", "eval.problem.eval-model.sim-models.0.due-date-factor",
                "eval.problem.eval-model.sim-models.0.num-jobs", "eval.problem.eval-model.sim-models.0.warmup-jobs",
                "eval.problem.eval-model.sim-models.0.num-machines", "eval.problem.eval-model.rotate-sim-seed"}) {
                settings.put(key, config.text(key, ""));
            }
            status.put("settings", settings);
        writeStatus(directory, status);
        EvoSpeakEvolutionState state = null;
        try {
            state = (EvoSpeakEvolutionState) Evolve.initialize(config.parameters, 0);
            state.output.setThrowsErrors(true);
            state.config = config;
            state.client = client;
            state.directory = directory;
            state.job = new Object[]{0};
            state.output.message("EvoSpeakV1 output directory: " + directory);
            state.run(EvolutionState.C_STARTED_FRESH);
            status.put("normalizedWeights", new JSONArray(((WeightedFitness) state.population.subpops[0].species.f_prototype).getWeights()));
            status.put("state", config.flag("evospeak.run-gp", true) ? "completed" : "validated");
            return directory;
        } catch (RuntimeException error) {
            status.put("state", "failed").put("error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            throw error;
        } finally {
            status.put("finished", Instant.now().toString()).put("llmRequests", client == null ? 0 : client.requestCount());
            writeStatus(directory, status);
            if (state != null) {
                state.output.close();
            }
        }
    }

    private static void writeStatus(Path directory, JSONObject status) throws IOException {
        Files.writeString(directory.resolve("status.json"), status.toString(2), StandardCharsets.UTF_8);
    }
}