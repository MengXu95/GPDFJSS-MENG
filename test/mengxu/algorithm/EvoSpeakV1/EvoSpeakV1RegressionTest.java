package mengxu.algorithm.EvoSpeakV1;

import ec.EvolutionState;
import ec.Evolve;
import ec.gp.GPIndividual;
import ec.util.Output;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import yimei.jss.gp.GPRuleEvolutionState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;

public class EvoSpeakV1RegressionTest {
    public static void main(String[] args) throws Exception {
        testWeightedFitness();
        testPopulationParsing();
        testLlmProviders();
        testOfflinePipeline();
        testOnlinePipelineAndFailureGate();
        System.out.println("EvoSpeakV1 regression tests passed.");
    }

    private static WeightedFitness fitness(int objectives, double firstWeight, double secondWeight) {
        EvolutionState state = new EvolutionState();
        state.parameters = new ParameterDatabase();
        state.output = new Output(true);
        state.parameters.set(new Parameter("fitness.num-objectives"), String.valueOf(objectives));
        state.parameters.set(new Parameter("fitness.maximize"), "false");
        state.parameters.set(new Parameter("evospeak.weight.0"), String.valueOf(firstWeight));
        state.parameters.set(new Parameter("evospeak.weight.1"), String.valueOf(secondWeight));
        WeightedFitness fitness = new WeightedFitness();
        fitness.setup(state, new Parameter("fitness"));
        return fitness;
    }

    private static void testWeightedFitness() {
        WeightedFitness single = fitness(1, 0.0, 0.0);
        single.objectives[0] = 12.0;
        requireClose(12.0, single.fitness(), "Single objective");
        WeightedFitness multiple = fitness(2, 1.0, 3.0);
        multiple.objectives = new double[]{10.0, 20.0};
        requireClose(17.5, multiple.fitness(), "Unnormalized weighted objectives");
        multiple.setBaselines(new double[]{10.0, 10.0});
        requireClose(1.75, multiple.fitness(), "Normalized weighted objectives");
        WeightedFitness copy = (WeightedFitness) multiple.clone();
        copy.setBaselines(new double[]{1.0, 1.0});
        requireClose(1.75, multiple.fitness(), "Cloned fitness isolation");
        WeightedFitness boundary = fitness(2, 1.0, 0.0);
        boundary.objectives = new double[]{2.0, Double.POSITIVE_INFINITY};
        requireClose(2.0, boundary.fitness(), "Zero weight must not produce NaN");
        boolean rejected = false;
        try {
            fitness(2, 0.0, 0.0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "All-zero weights must be rejected.");
    }

    private static GPRuleEvolutionState parsingState() throws Exception {
        EvoSpeakConfig config = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                "state=yimei.jss.gp.GPRuleEvolutionState", "eval=ec.simple.SimpleEvaluator",
                "stat=ec.Statistics", "pop.subpop.0.size=6", "generations=3");
        GPRuleEvolutionState state = (GPRuleEvolutionState) Evolve.initialize(config.parameters, 0);
        state.output.setThrowsErrors(true);
        state.startFresh();
        return state;
    }

    private static void testPopulationParsing() throws Exception {
        GPRuleEvolutionState state = parsingState();
        try {
            GPIndividual individual = RulePopulation.parse(state, new RulePopulation.Rules("(/ PT W)", "(+ WIQ TRANT)"), 8, 255);
            require(RulePopulation.rules(individual).sequencing.equals("(/ PT W)"), "Expression round trip");
            JSONObject facts = RuleKnowledge.facts(state, individual);
            require(facts.getJSONObject("sequencing").getJSONObject("terminalsUsed").has("W"), "Explanation facts must include actual used terminals.");
            require(!facts.getJSONObject("sequencing").getJSONObject("terminalsUsed").has("TRANT"), "Terminals must not be attributed to the wrong tree.");
            List<String> invalid = Arrays.asList("(+ PT)", "(+ PT W NOR)", "(+ PT W))", "(+ PT UNKNOWN)", "PT W", "(exec PT W)", "");
            for (String expression : invalid) {
                boolean rejected = false;
                try {
                    RulePopulation.parse(state, new RulePopulation.Rules(expression, "WIQ"), 8, 255);
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                require(rejected, "Invalid expression accepted: " + expression);
            }
            Path file = Files.createTempFile("evospeak-population-", ".txt");
            try {
                RulePopulation.write(file, state, Arrays.asList(individual));
                List<RulePopulation.Rules> rules = RulePopulation.read(file);
                require(rules.size() == 1, "Serialized population count");
                RulePopulation.parse(state, rules.get(0), 8, 255);
                ec.Subpopulation loaded = (ec.Subpopulation) state.population.subpops[0].emptyClone();
                try (java.io.LineNumberReader reader = new java.io.LineNumberReader(Files.newBufferedReader(file))) {
                    loaded.readSubpopulation(state, reader);
                }
                require(loaded.individuals.length == 1 && loaded.individuals[0].fitness instanceof WeightedFitness,
                        "Generated file must also be readable by the native ECJ population loader.");
            } finally {
                Files.deleteIfExists(file);
            }
            List<RulePopulation.Rules> legacy = RulePopulation.read(Paths.get(
                    "src/mengxu/algorithm/EvoSpeak/WarmStart/population_100_0.5_MO.txt"));
            require(legacy.size() == 100, "Legacy population must be readable without trusting placeholder fitness.");
            int validLegacy = 0;
            for (RulePopulation.Rules pair : legacy) {
                try {
                    RulePopulation.parse(state, pair, 8, 255);
                    validLegacy++;
                } catch (IllegalArgumentException rejected) {
                }
            }
            System.out.println("Legacy population syntax review: " + validLegacy + "/" + legacy.size() + " pairs pass strict depth-8 parsing.");
                EvoSpeakConfig validation = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                    "evospeak.validation.jobs=200", "evospeak.validation.warmup=20", "evospeak.validation.seeds=17001");
                RuleValidator.Result result = new RuleValidator(state, validation).validate(
                    new RulePopulation.Rules("(/ PT W)", "WIQ"));
                require(!result.individual.evaluated, "Validation fitness must not be reused as training fitness.");
                require(result.evidence.getJSONArray("evaluations").length() == 1, "Validation evidence must include each seed.");
                validation.set("evospeak.validation.max-decisions", 1);
                boolean timedOut = false;
                try {
                new RuleValidator(state, validation).validate(new RulePopulation.Rules("PT", "WIQ"));
                } catch (IllegalArgumentException expected) {
                timedOut = true;
                }
                require(timedOut, "Validation must enforce the decision budget.");
        } finally {
            state.output.close();
        }
    }

    private static void testLlmProviders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            JSONObject body = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response;
            if (exchange.getRequestHeaders().containsKey("x-api-key")) {
                require(body.has("system") && body.has("max_tokens"), "Anthropic request schema");
                response = new JSONObject().put("stop_reason", "end_turn").put("content", new JSONArray().put(
                        new JSONObject().put("type", "text").put("text", "provider-ok"))).toString();
            } else if (body.has("options")) {
                require(!body.getBoolean("stream"), "Ollama must use a nonstreaming response");
                response = new JSONObject().put("message", new JSONObject().put("content", "provider-ok")).toString();
            } else {
                response = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", "stop")
                        .put("message", new JSONObject().put("content", "provider-ok")))).toString();
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            for (String provider : new String[]{"openai-compatible", "azure-openai", "anthropic", "ollama"}) {
                EvoSpeakConfig config = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                        "llm.provider=" + provider, "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/chat");
                String response = new LlmClient(config, name -> "test-key").complete("Test instructions", "Test prompt");
                require(response.equals("provider-ok"), "Provider response decoding: " + provider);
            }
                server.createContext("/analysis", exchange -> {
                JSONObject request = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String prompt = request.getJSONArray("messages").getJSONObject(1).getString("content");
                require(prompt.contains("terminalsUsed") && prompt.contains("Job importance weight"), "Analysis must send verified terminal semantics.");
                JSONObject explanation = new JSONObject().put("sequencing", "PT/W favors short processing and larger positive job weights.")
                    .put("routing", "WIQ favors the machine with less queued work.")
                    .put("interaction", "Routing limits congestion; sequencing prioritizes processing within a selected queue.")
                    .put("limitations", "These are qualitative interpretations, not measured improvements.");
                byte[] response = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", "stop")
                    .put("message", new JSONObject().put("content", explanation.toString())))).toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
                });
                EvoSpeakConfig analysis = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                    "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/analysis");
                Path analysisDirectory = Files.createTempDirectory("evospeak-analysis-test-");
                Path population = analysisDirectory.resolve("rules.json");
                Files.writeString(population, new JSONObject().put("individuals", new JSONArray()
                    .put(new RulePopulation.Rules("(/ PT W)", "WIQ").json())).toString());
                Path report = analysisDirectory.resolve("report.md");
                RuleAnalysisMain.analyze(analysis, new LlmClient(analysis, name -> "test-key"), population, report);
                String content = Files.readString(report);
                require(content.contains("Sequencing Rule") && content.contains("Routing Rule") && content.contains("| W |"), "Report must cover both trees and terminals.");
                require(content.contains("Analysis complete"), "Report completion marker");
                server.createContext("/unauthorized", exchange -> {
                    byte[] response = "do-not-log-test-key".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(401, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
                analysis.set("llm.endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/unauthorized");
                LlmClient unauthorized = new LlmClient(analysis, name -> "test-key");
                boolean rejected = false;
                try {
                    unauthorized.complete("Test", "Test");
                } catch (java.io.IOException expected) {
                    rejected = expected.getMessage().contains("401") && !expected.getMessage().contains("do-not-log-test-key");
                }
                require(rejected && unauthorized.requestCount() == 1, "Authentication failures must fail without retrying or exposing response bodies.");
                boolean missingKey = false;
                try {
                    new LlmClient(analysis, name -> null);
                } catch (IllegalArgumentException expected) {
                    missingKey = true;
                }
                require(missingKey, "Missing credentials must fail before sending a request.");
                server.createContext("/truncated", exchange -> {
                    byte[] response = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", "length")
                            .put("message", new JSONObject().put("content", "{\"individuals\":")))).toString().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
                analysis.set("llm.endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/truncated");
                boolean truncated = false;
                try {
                    new LlmClient(analysis, name -> "test-key").complete("Test", "Test");
                } catch (java.io.IOException expected) {
                    truncated = expected.getMessage().contains("truncated");
                }
                require(truncated, "Truncated model output must never be accepted as complete.");
        } finally {
            server.stop(0);
        }
    }

        private static void testOfflinePipeline() throws Exception {
        Path directory = Files.createTempDirectory("evospeak-offline-test-");
        Path source = directory.resolve("rules.json");
        Files.writeString(source, new JSONObject().put("individuals", new JSONArray()
            .put(new RulePopulation.Rules("PT", "WIQ").json())
            .put(new RulePopulation.Rules("(/ PT W)", "(+ WIQ TRANT)").json())).toString());
        EvoSpeakConfig config = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
            "evospeak.source=file", "evospeak.population-file=" + source.toString().replace('\\', '/'),
            "evospeak.output-directory=" + directory.toString().replace('\\', '/'),
            "pop.subpop.0.size=2", "breed.elite.0=1", "generations=2",
            "evospeak.validation.jobs=100", "evospeak.validation.warmup=10", "evospeak.validation.seeds=17001",
            "eval.problem.eval-model.sim-models.0.num-jobs=200", "eval.problem.eval-model.sim-models.0.warmup-jobs=20");
        Path run = EvoSpeakMain.run(config, null);
        require(RulePopulation.read(run.resolve("generated-population.txt")).size() == 2, "Validated warm-start size");
        require(Files.readAllLines(run.resolve("generations.jsonl")).size() == 2, "GP must run after validation.");
        require(Files.exists(run.resolve("best-rules.txt")), "Final interpretable rules must be saved.");
        require(new JSONObject(Files.readString(run.resolve("status.json"))).getString("state").equals("completed"), "Run completion status");
        }

        private static void testOnlinePipelineAndFailureGate() throws Exception {
            AtomicInteger requests = new AtomicInteger();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/generate", exchange -> {
                JSONObject request = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String prompt = request.getJSONArray("messages").getJSONObject(1).getString("content");
                int batch = requests.getAndIncrement();
                JSONArray individuals = new JSONArray();
                if (batch == 0) {
                    individuals.put(new RulePopulation.Rules("PT", "WIQ").json())
                            .put(new RulePopulation.Rules("(+ PT UNKNOWN)", "WIQ").json());
                } else {
                    require(prompt.contains("Unknown") || prompt.contains("UNKNOWN"), "Rejected rules must be fed back to the LLM.");
                    individuals.put(new RulePopulation.Rules("(/ PT W)", "(+ WIQ TRANT)").json());
                }
                byte[] response = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", "stop")
                        .put("message", new JSONObject().put("content", new JSONObject().put("individuals", individuals).toString()))))
                        .toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            try {
                Path root = Files.createTempDirectory("evospeak-online-test-");
                EvoSpeakConfig config = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                        "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/generate",
                        "evospeak.output-directory=" + root.toString().replace('\\', '/'),
                        "evospeak.batch-size=2", "evospeak.max-batches=2", "evospeak.objective-mode=single",
                        "evospeak.normalization=false", "pop.subpop.0.size=2", "breed.elite.0=1", "generations=2",
                        "evospeak.validation.jobs=100", "evospeak.validation.warmup=10", "evospeak.validation.seeds=17001",
                        "eval.problem.eval-model.sim-models.0.num-jobs=200", "eval.problem.eval-model.sim-models.0.warmup-jobs=20");
                Path run = EvoSpeakMain.run(config, new LlmClient(config, name -> "test-key"));
                require(requests.get() == 2, "One rejected candidate must trigger a replacement batch.");
                JSONObject validation = new JSONObject(Files.readString(run.resolve("validation-report.json")));
                require(validation.getInt("accepted") == 2 && validation.getJSONArray("checks").length() == 3, "Validation audit counts");
                JSONObject firstGeneration = new JSONObject(Files.readAllLines(run.resolve("generations.jsonl")).get(0));
                require(firstGeneration.getJSONArray("objectives").length() == 1, "Single-objective mode must reach actual GP evaluation.");
                requireClose(firstGeneration.getJSONArray("objectives").getDouble(0), firstGeneration.getDouble("weightedFitness"),
                        "Single-objective raw training fitness");
                requests.set(0);
                config.set("evospeak.max-batches", 1);
                boolean blocked = false;
                try {
                    EvoSpeakMain.run(config, new LlmClient(config, name -> "test-key"));
                } catch (java.io.UncheckedIOException expected) {
                    blocked = true;
                }
                require(blocked, "Insufficient feasible individuals must block GP.");
                try (java.util.stream.Stream<Path> runs = Files.list(root)) {
                    Path failed = runs.filter(Files::isDirectory).filter(path -> !path.equals(run)).findFirst().orElseThrow();
                    require(!Files.exists(failed.resolve("generations.jsonl")), "A failed validation must not run any GP generations.");
                    require(!Files.exists(failed.resolve("generated-population.txt")), "A failed validation must not publish a usable population.");
                    require(new JSONObject(Files.readString(failed.resolve("status.json"))).getString("state").equals("failed"), "Failed run status");
                }
            } finally {
                server.stop(0);
            }
        }

        private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) < 1.0e-9,
                message + ": expected " + expected + ", got " + actual);
    }
}