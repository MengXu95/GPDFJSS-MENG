package mengxu.algorithm.EvoSpeakV1;

import ec.EvolutionState;
import ec.Evolve;
import ec.util.Parameter;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EvoSpeakMain {
    private EvoSpeakMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("EvoSpeakMain [--check-connection | --check-auth] [-file <params>] [-p name=value ...]\n"
                    + "No arguments: src/mengxu/algorithm/EvoSpeakV1/evospeak.params.\n"
                    + "Set llm.provider/api/model/endpoint and llm.api-key in private local params or its environment variable.\n"
                    + "--check-connection sends only an unauthenticated HEAD request.\n"
                    + "--check-auth sends an authenticated GET to the same OpenAI-compatible or Azure v1 service's models endpoint.\n"
                    + "Neither check submits prompts, model generation or a GP run.");
            return;
        }
        List<String> configurationArgs = new ArrayList<>(Arrays.asList(args));
        boolean checkConnection = configurationArgs.remove("--check-connection");
        boolean checkAuthentication = configurationArgs.remove("--check-auth");
        if (checkConnection && checkAuthentication) {
            throw new IllegalArgumentException("Select only one of --check-connection or --check-auth.");
        }
        EvoSpeakConfig config = EvoSpeakConfig.fromArgs(configurationArgs.toArray(new String[0]));
        if (checkConnection) {
            System.out.println(LlmClient.checkConnection(config));
            return;
        }
        if (checkAuthentication) {
            System.out.println(LlmClient.checkAuthentication(config));
            return;
        }
        LlmClient client = config.text("evospeak.source", "llm").equals("llm") ? new LlmClient(config) : null;
        Path result = run(config, client);
        System.out.println("EvoSpeakV1 completed: " + result);
    }

    public static Path run(EvoSpeakConfig config, LlmClient client) throws IOException {
        config = config.copy();
        if (config.text("evospeak.source", "llm").equals("llm") && client == null) {
            throw new IllegalArgumentException("A configured LLM client is required for evospeak.source=llm.");
        }
        long runId = Long.parseLong(config.text("seed.0", "0"));
        String prefix = "job." + runId;
        Path root = config.path("evospeak.output-directory", "runs");
        Files.createDirectories(root);
        Path directory = Files.createTempDirectory(root, prefix + "-");
        Path statisticsFile = config.text("stat.file", "$out.stat").equals("$out.stat")
            ? directory.resolve(prefix + ".out.stat")
            : config.parameters.getFile(new Parameter("stat.file"), null).toPath().toAbsolutePath().normalize();
        Path timeFile = statisticsFile.resolveSibling(prefix + ".time.csv");
        Path cumulativeTimeFile = statisticsFile.resolveSibling(prefix + ".timeSumGen.csv");
        for (Path resultFile : new Path[]{statisticsFile, timeFile, cumulativeTimeFile}) {
            if (Files.exists(resultFile)) {
                throw new java.nio.file.FileAlreadyExistsException(resultFile.toString(), null,
                        "Choose a different run ID or results directory to avoid replacing existing results.");
            }
        }
        Files.createDirectories(statisticsFile.getParent());
        config.set("stat.file", statisticsFile);
        JSONObject status = new JSONObject().put("started", Instant.now().toString()).put("state", "preparing")
            .put("runId", runId).put("statisticsFile", statisticsFile.toString()).put("artifactDirectory", directory.toString())
            .put("timeFile", timeFile.toString()).put("cumulativeTimeFile", cumulativeTimeFile.toString())
                .put("objectiveMode", config.text("evospeak.objective-mode", "multi"))
                .put("source", config.text("evospeak.source", "llm"))
                .put("provider", config.text("llm.provider", "" )).put("model", config.text("llm.model", ""))
                .put("api", config.text("llm.api", "chat-completions"))
                .put("seed", config.text("seed.0", "0"));
            JSONObject settings = new JSONObject();
            for (String key : new String[]{"evospeak.objective-mode", "evospeak.objective.0", "evospeak.objective.1",
                "evospeak.weight.0", "evospeak.weight.1", "evospeak.normalization", "pop.subpop.0.size", "generations",
                "evospeak.validation.jobs", "evospeak.validation.warmup", "evospeak.validation.seeds",
                "evospeak.max-tree-depth", "evospeak.max-tree-nodes", "evospeak.batch-size", "evospeak.max-batches",
                "evospeak.examples-file", "evospeak.max-examples", "evospeak.generation-language",
                "llm.proxy", "llm.connect-timeout-seconds", "llm.timeout-seconds",
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
            state.job = new Object[]{runId};
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