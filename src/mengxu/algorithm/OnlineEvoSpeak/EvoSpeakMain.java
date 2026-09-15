package mengxu.algorithm.OnlineEvoSpeak;

import ec.EvolutionState;
import ec.Evolve;
import ec.util.Parameter;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EvoSpeakMain {
    private EvoSpeakMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("EvoSpeakMain [--check-connection | --check-auth | --export-prompt <output.md>] [-file <params>] [-p name=value ...]\n"
                    + "No arguments: src/mengxu/algorithm/OnlineEvoSpeak/evospeak.params.\n"
                    + "Set llm.provider/api/model/endpoint and llm.api-key in private local params or its environment variable.\n"
                    + "--check-connection sends only an unauthenticated HEAD request.\n"
                    + "--check-auth sends an authenticated GET to the same OpenAI-compatible or Azure v1 service's models endpoint.\n"
                    + "--export-prompt writes the exact first-batch system/user prompts without API access or a GP run.\n"
                    + "Neither check submits prompts, model generation or a GP run.");
            return;
        }
        List<String> configurationArgs = new ArrayList<>(Arrays.asList(args));
        boolean checkConnection = configurationArgs.remove("--check-connection");
        boolean checkAuthentication = configurationArgs.remove("--check-auth");
        Path promptOutput = null;
        int promptOption = configurationArgs.indexOf("--export-prompt");
        if (promptOption >= 0) {
            if (promptOption + 1 >= configurationArgs.size() || configurationArgs.get(promptOption + 1).startsWith("-")) {
                throw new IllegalArgumentException("--export-prompt requires a new output Markdown path.");
            }
            configurationArgs.remove(promptOption);
            promptOutput = Paths.get(configurationArgs.remove(promptOption));
        }
        if ((checkConnection ? 1 : 0) + (checkAuthentication ? 1 : 0) + (promptOutput == null ? 0 : 1) > 1) {
            throw new IllegalArgumentException("Select only one of --check-connection, --check-auth or --export-prompt.");
        }
        EvoSpeakConfig config = EvoSpeakConfig.fromArgs(configurationArgs.toArray(new String[0]));
        if (promptOutput != null) {
            System.out.println("Offline LLM prompt exported: " + exportPrompt(config, promptOutput));
            return;
        }
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
        System.out.println("OnlineEvoSpeak completed: " + result);
    }

    public static Path run(EvoSpeakConfig config, LlmClient client) throws IOException {
        config = config.copy();
        if (config.text("evospeak.source", "llm").equals("llm") && client == null) {
            throw new IllegalArgumentException("A configured LLM client is required for evospeak.source=llm.");
        }
        if (config.parameters.exists(new Parameter("pop.file"), null)) {
            throw new IllegalArgumentException("Use evospeak.population-file with evospeak.source=file instead of pop.file.");
        }
        long runId = Long.parseLong(config.text("seed.0", "0"));
        String prefix = "job." + runId;
        Path root = config.path("evospeak.output-directory", "runs");
        Files.createDirectories(root);
        Path directory = Files.createTempDirectory(root, prefix + "-");
        Path initialPopulationFile = directory.resolve("generated-population.txt");
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
            .put("initialPopulationFile", initialPopulationFile.toString()).put("initialPopulationLoaded", false)
            .put("timeFile", timeFile.toString()).put("cumulativeTimeFile", cumulativeTimeFile.toString())
                .put("objectiveMode", config.text("evospeak.objective-mode", "multi"))
                .put("source", config.text("evospeak.source", "llm"))
                .put("provider", config.text("llm.provider", "" )).put("model", config.text("llm.model", ""))
                .put("api", config.text("llm.api", "chat-completions"))
                .put("configuredModelVersion", config.text("llm.model-version", ""))
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
            status.put("normalizedWeights", new JSONArray(prepareInitialPopulation(config, client, directory)));
            status.put("state", "population-ready");
            writeStatus(directory, status);
            if (!config.flag("evospeak.run-gp", true)) {
                status.put("state", "validated");
                System.out.println("Initial population TXT saved: " + initialPopulationFile + ". GP disabled by evospeak.run-gp=false.");
                return directory;
            }
            config.set("pop.subpop.0.file", initialPopulationFile);
            config.set("pop.subpop.0.extra-behavior", "truncate");
            state = (EvoSpeakEvolutionState) Evolve.initialize(config.parameters, 0);
            state.output.setThrowsErrors(true);
            state.config = config;
            state.client = client;
            state.directory = directory;
            state.job = new Object[]{runId};
            state.output.message("OnlineEvoSpeak output directory: " + directory);
            state.run(EvolutionState.C_STARTED_FRESH);
            status.put("state", "completed");
            return directory;
        } catch (IOException | RuntimeException error) {
            status.put("state", "failed").put("error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            throw error;
        } finally {
            status.put("finished", Instant.now().toString()).put("llmRequests", client == null ? 0 : client.requestCount());
            status.put("initialPopulationLoaded", state != null && state.initialPopulationLoaded);
            writeStatus(directory, status);
            if (state != null) {
                state.output.close();
            }
        }
    }

    public static Path exportPrompt(EvoSpeakConfig config, Path output) throws IOException {
        Path target = output.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            throw new java.nio.file.FileAlreadyExistsException(target.toString());
        }
        EvoSpeakEvolutionState state = preparationState(config);
        try {
            int count = Math.min(config.integer("evospeak.batch-size", 10), config.integer("pop.subpop.0.size", 100));
            PopulationFactory factory = new PopulationFactory(state, config, null, null);
            String userPrompt = factory.generationPrompt(count);
            String document = "# Offline LLM Prompt\n\n"
                    + "Exact first-batch messages generated by OnlineEvoSpeak. No LLM request or GP run was made.\n\n"
                    + "## System Prompt\n\n```text\n" + factory.systemPrompt() + "\n```\n\n"
                    + "## User Prompt\n\n```text\n" + userPrompt + "\n```\n";
            Files.createDirectories(target.getParent());
            Files.writeString(target, document, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return target;
        } finally {
            state.output.close();
        }
    }

    private static EvoSpeakEvolutionState preparationState(EvoSpeakConfig config) {
        EvoSpeakConfig preparation = config.copy();
        preparation.set("stat", "ec.Statistics");
        preparation.set("stat.num-children", 0);
        EvoSpeakEvolutionState state = (EvoSpeakEvolutionState) Evolve.initialize(preparation.parameters, 0);
        state.output.setThrowsErrors(true);
        try {
            state.setup(state, null);
            state.population = state.initializer.setupPopulation(state, 0);
            return state;
        } catch (RuntimeException | Error error) {
            state.output.close();
            throw error;
        }
    }

    private static double[] prepareInitialPopulation(EvoSpeakConfig config, LlmClient client, Path directory) throws IOException {
        EvoSpeakEvolutionState state = preparationState(config);
        try {
            state.output.message("OnlineEvoSpeak stage 1/2: preparing and validating initial population from "
                    + config.text("evospeak.source", "llm") + ".");
            new PopulationFactory(state, config, client, directory).create();
            state.output.message("Validated initial population TXT saved: " + directory.resolve("generated-population.txt"));
            return ((WeightedFitness) state.population.subpops[0].species.f_prototype).getWeights();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OnlineEvoSpeak cancelled before GP initialization.", error);
        } finally {
            state.output.close();
        }
    }

    private static void writeStatus(Path directory, JSONObject status) throws IOException {
        Files.writeString(directory.resolve("status.json"), status.toString(2), StandardCharsets.UTF_8);
    }
}