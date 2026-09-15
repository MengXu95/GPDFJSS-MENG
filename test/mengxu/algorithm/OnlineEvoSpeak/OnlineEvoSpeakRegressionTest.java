package mengxu.algorithm.OnlineEvoSpeak;

import ec.EvolutionState;
import ec.Evolve;
import ec.gp.GPIndividual;
import ec.util.Output;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.gp.GPMain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;

public class OnlineEvoSpeakRegressionTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--population-format-only")) {
            testStrictPopulationText();
            testMultiObjectivePrompt();
            testSingleObjectivePrompt();
            testPopulationParsing();
            testOnlinePipelineAndFailureGate();
            System.out.println("OnlineEvoSpeak strict population TXT regression tests passed.");
            return;
        }
        if (args.length == 1 && args[0].equals("--single-prompt-only")) {
            testSingleObjectivePrompt();
            testMultiObjectivePrompt();
            System.out.println("OnlineEvoSpeak single-objective prompt regression tests passed.");
            return;
        }
        if (args.length == 1 && args[0].equals("--multi-prompt-only")) {
            testMultiObjectiveTextResponse();
            testMultiObjectivePrompt();
            System.out.println("OnlineEvoSpeak multi-objective text regression tests passed.");
            return;
        }
        if (args.length == 1 && args[0].equals("--prompt-only")) {
            testPromptExport();
            System.out.println("OnlineEvoSpeak offline prompt regression tests passed.");
            return;
        }
        if (args.length == 1 && args[0].equals("--responses-only")) {
            testResponsesApi();
            System.out.println("OnlineEvoSpeak Responses API regression tests passed.");
            return;
        }
        if (args.length == 1 && args[0].equals("--auth-only")) {
            testLlmProviders();
            System.out.println("OnlineEvoSpeak authentication regression tests passed.");
            return;
        }
        if (args.length == 1 && args[0].equals("--network-only")) {
            testProxyTransport();
            System.out.println("OnlineEvoSpeak network regression tests passed.");
            return;
        }
        testWeightedFitness();
        testStrictPopulationText();
        testParameterProfiles();
        testMultiObjectiveTextResponse();
        testMultiObjectivePrompt();
        testSingleObjectivePrompt();
        testPromptExport();
        testPopulationParsing();
        testLlmProviders();
        testResponsesApi();
        testProxyTransport();
        testOfflinePipeline();
        testGPMainRuns();
        testOnlinePipelineAndFailureGate();
        System.out.println("OnlineEvoSpeak regression tests passed.");
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

    private static void testStrictPopulationText() throws Exception {
        for (int objectives : new int[]{1, 2}) {
            String text = plainPopulationResponse(twoRuleResponse(), objectives);
            List<RulePopulation.Rules> rules = RulePopulation.fromGeneratedText(text, 2, objectives);
            require(rules.size() == 2 && rules.get(1).sequencing.equals("(/ PT W)")
                    && rules.get(1).routing.equals("(+ WIQ TRANT)"), "Pure ECJ TXT must preserve the two expression lines per individual.");
            require(RulePopulation.fromGeneratedText("\uFEFF" + text.replace("\n", "\r\n"), 2, objectives).size() == 2,
                    "A UTF-8 BOM and Windows line endings do not change the population format.");
            for (String invalid : new String[]{"```text\n" + text + "```", "<START>\n" + text + "<END>",
                    text + "Insights: these rules may help.\n", twoRuleResponse().toString(), textGenerationResponse(twoRuleResponse(), objectives),
                    text.replace("Number of Individuals: i2|", "Number of Individuals: i1|"),
                    text.replace("Individual Number: i1|", "Number of Individuals: i1|"),
                    text.replace("Individual Number: i1|", "Individual Number: i0|"), text.replace("Evaluated: F", "Evaluated: T"),
                    text.replace("Tree 1:", "Tree 2:"), text.replace("d0|0.0|", "d12345678901234560104|2570.0|"),
                    plainPopulationResponse(twoRuleResponse(), objectives == 1 ? 2 : 1),
                    text.replace("(/ PT W)", "(/ PT\n W)"), text + text}) {
                boolean rejected = false;
                try {
                    RulePopulation.fromGeneratedText(invalid, 2, objectives);
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                require(rejected, "Malformed or annotated output must not be accepted as a strict initial population TXT.");
            }
        }
        GPRuleEvolutionState state = parsingState();
        Path file = Files.createTempFile("onlineevospeak-reference-layout-", ".txt");
        try {
            for (int objectives : new int[]{1, 2}) {
                state.population.subpops[0].species.f_prototype = fitness(objectives, 1.0, 1.0);
                List<GPIndividual> individuals = new java.util.ArrayList<>();
                for (RulePopulation.Rules rules : RulePopulation.fromJson(twoRuleResponse())) {
                    individuals.add(RulePopulation.parse(state, rules, 8, 255));
                }
                RulePopulation.write(file, state, individuals);
                require(Files.readString(file).replace("\r\n", "\n").equals(plainPopulationResponse(twoRuleResponse(), objectives)),
                        "Native writer output must match the reference blank lines, field order and unindented one-line trees.");
                ec.Subpopulation loaded = (ec.Subpopulation) state.population.subpops[0].emptyClone();
                try (java.io.LineNumberReader reader = new java.io.LineNumberReader(Files.newBufferedReader(file))) {
                    loaded.readSubpopulation(state, reader);
                }
                require(loaded.individuals.length == 2 && !loaded.individuals[0].evaluated
                        && ((WeightedFitness) loaded.individuals[0].fitness).getObjectives().length == objectives,
                        "Reference-style TXT must remain readable by native ECJ with the correct objective dimension.");
            }
        } finally {
            state.output.close();
            Files.deleteIfExists(file);
        }
    }

    private static void assertPopulationTemplate(String prompt, int count, int objectives) {
        String header = "Number of Individuals: i" + count + "|\n";
        int start = prompt.lastIndexOf("\n\n" + header);
        require(start >= 0, "The prompt must end with a complete population template for the current batch count.");
        String template = prompt.substring(start + 2);
        require(template.lines().filter(line -> line.startsWith("Number of Individuals:")).count() == 1
                && template.lines().filter(line -> line.startsWith("Individual Number:")).count() == count
                && template.endsWith("ROUTING_RULE_" + (count - 1) + "\n"),
                "The response template must list all records once, including the final index, without omitted-record examples.");
        String filled = template.replaceAll("SEQUENCING_RULE_\\d+", "PT").replaceAll("ROUTING_RULE_\\d+", "WIQ");
        require(RulePopulation.fromGeneratedText(filled, count, objectives).size() == count,
                "Replacing only expression placeholders must yield a valid pure population TXT.");
    }

    private static String plainPopulationResponse(JSONObject response, int objectives) {
        JSONArray individuals = response.getJSONArray("individuals");
        String fitness = objectives == 1 ? "Fitness: [d0|0.0|]" : "Fitness: [d0|0.0| d0|0.0|]";
        StringBuilder text = new StringBuilder("Number of Individuals: i").append(individuals.length()).append("|\n");
        for (int index = 0; index < individuals.length(); index++) {
            JSONObject individual = individuals.getJSONObject(index);
            text.append("\nIndividual Number: i").append(index).append("|\nEvaluated: F\n").append(fitness)
                    .append("\nTree 0:\n").append(individual.getString("sequencing"))
                    .append("\nTree 1:\n").append(individual.getString("routing")).append('\n');
        }
        return text.toString();
    }

    private static void testMultiObjectiveTextResponse() throws Exception {
        JSONObject expected = twoRuleResponse();
        String text = textGenerationResponse(expected);
        JSONObject parsed = RulePopulation.generationObject(text);
        require(parsed.similar(expected), "ECJ-style rules, bullet insights and explanations must retain the JSON validation contract.");
        require(RulePopulation.generationObject("```text\r\n" + text.replace("\n", "\r\n") + "\r\n```").similar(expected),
                "Text replies must tolerate one surrounding Markdown fence and Windows line endings.");
        require(RulePopulation.generationObject(expected.toString()).similar(expected), "Existing JSON replies remain supported.");
        Path responseFile = Files.createTempFile("onlineevospeak-text-response-", ".txt");
        try {
            Files.writeString(responseFile, text);
            List<RulePopulation.Rules> rules = RulePopulation.read(responseFile);
            require(rules.size() == 2 && rules.get(1).sequencing.equals("(/ PT W)")
                    && rules.get(1).routing.equals("(+ WIQ TRANT)"), "Offline reply import must separate explanations from GP expressions.");
        } finally {
            Files.deleteIfExists(responseFile);
        }
        for (String invalid : new String[]{text.replace("<END>", ""), text + "\nUnrequested text",
                text.replace("i2|", "i3|"), text.replace("Individual Number: i1|", "Individual Number: i0|"),
                text.replace("## Insights Extraction", "## Missing Insights"), text.replace("Expected objective trade-off:", "Missing trade-off:"),
                text.replace("Evaluated: F", "Evaluated: T"), text.replace("Reference IDs: [1]", "Reference IDs: invalid")}) {
            boolean rejected = false;
            try {
                RulePopulation.generationObject(invalid);
            } catch (IllegalArgumentException | org.json.JSONException expectedFailure) {
                rejected = true;
            }
            require(rejected, "Malformed annotated ECJ generation responses must fail before rule validation.");
        }
    }

        private static void testMultiObjectivePrompt() throws Exception {
        EvoSpeakConfig config = new EvoSpeakConfig(Paths.get("src/mengxu/algorithm/OnlineEvoSpeak/multipletreegp-dynamicLLMWarmStartMO.params"),
            "stat=ec.Statistics", "pop.subpop.0.species=" + FileBackedTestSpecies.class.getName());
        EvoSpeakEvolutionState state = (EvoSpeakEvolutionState) Evolve.initialize(config.parameters, 0);
        state.output.setThrowsErrors(true);
        try {
            state.setup(state, null);
            state.population = state.initializer.setupPopulation(state, 0);
            PopulationFactory factory = new PopulationFactory(state, config, null, null);
            String prompt = factory.generationPrompt(10);
            require(prompt.startsWith("# Prompt\n") && prompt.contains("### **Provided Information:**")
                && prompt.contains("1. **Reference Analysis (not returned)**:") && prompt.contains("2. **New Heuristic Generation**:")
                && prompt.contains("### **Output Requirements:**"), "Multi-objective prompt must follow the supplied structure.");
            require(prompt.contains("lambda1 * Fmean + lambda2 * WTmean, where lambda2 = 1 - lambda1.")
                && prompt.contains("Actual optimized score: 0.8 * (mean-flowtime / benchmark[0]) + 0.2 * (mean-weighted-tardiness / benchmark[1])"),
                "Distinguish the supplied raw formula from actual normalized GP settings.");
            JSONObject examples = new JSONObject(Files.readString(config.path("evospeak.examples-file", "")));
            require(prompt.contains("Number of Individuals: i5|") && prompt.contains("REFERENCE DATA ONLY"),
                "Keep the five reference individuals clearly separate from the output template.");
            for (Object item : examples.getJSONArray("individuals")) {
            JSONObject example = (JSONObject) item;
            require(prompt.contains(example.getString("sequencing")) && prompt.contains(example.getString("routing"))
                && prompt.contains(String.valueOf(example.getDouble("reportedFitness"))),
                "Preserve all supplied reference trees and reported scores.");
            }
            require(factory.systemPrompt().contains("Return only one complete plain-text ECJ subpopulation")
                && prompt.contains("Evaluated: F\nFitness: [d0|0.0| d0|0.0|]")
                && !prompt.contains("\n<START>") && !prompt.contains("\n<END>")
                && !prompt.contains("Reference IDs:") && !prompt.contains("Expected objective trade-off:"),
                "System and user prompts must request only the population, not annotations or wrapper blocks.");
            for (int count : new int[]{1, 10, 100}) {
                assertPopulationTemplate(factory.generationPrompt(count), count, 2);
            }
            config.set("evospeak.normalization", false);
            require(new PopulationFactory(state, config, null, null).generationPrompt(10)
                .contains("Actual optimized score: 0.8 * mean-flowtime + 0.2 * mean-weighted-tardiness"),
                "Raw multi-objective mode must use the supplied objective formula directly.");
            testExamplePrompt(state);
        } finally {
            state.output.close();
        }
        }

    private static void testSingleObjectivePrompt() throws Exception {
        EvoSpeakConfig config = new EvoSpeakConfig(Paths.get("src/mengxu/algorithm/OnlineEvoSpeak/multipletreegp-dynamicLLMWarmStart.params"),
                "stat=ec.Statistics", "pop.subpop.0.species=" + FileBackedTestSpecies.class.getName());
        EvoSpeakEvolutionState state = (EvoSpeakEvolutionState) Evolve.initialize(config.parameters, 0);
        state.output.setThrowsErrors(true);
        try {
            state.setup(state, null);
            state.population = state.initializer.setupPopulation(state, 0);
            PopulationFactory factory = new PopulationFactory(state, config, null, null);
            String prompt = factory.generationPrompt(10);
            require(prompt.startsWith("# Prompt\n") && prompt.contains("The goal is to minimize the single objective **mean weighted tardiness (WTmean)**")
                    && prompt.contains("Actual optimized score: 1.0 * mean-weighted-tardiness"),
                    "The single prompt must use the configured single objective, not a weighted combination.");
            require(!prompt.contains("lambda") && !prompt.contains("Fmean") && !prompt.contains("both objectives")
                    && !prompt.contains("weighted combination") && !prompt.contains("raw weighted sum")
                    && !prompt.contains("Expected objective effect:") && prompt.contains("Fitness: [d0|0.0|]\nTree 0:"),
                    "Remove all second-objective wording and use a one-objective fitness placeholder while retaining both trees.");
                require(factory.systemPrompt().equals(PopulationFactory.SYSTEM_PROMPT)
                    && factory.systemPrompt().contains("Return only one complete plain-text ECJ subpopulation"),
                    "Single and multi must share the pure TXT system prompt.");
                for (int count : new int[]{1, 10, 100}) {
                assertPopulationTemplate(factory.generationPrompt(count), count, 1);
                }
            EvoSpeakConfig multi = new EvoSpeakConfig(Paths.get("src/mengxu/algorithm/OnlineEvoSpeak/multipletreegp-dynamicLLMWarmStartMO.params"));
            ec.Fitness original = state.population.subpops[0].species.f_prototype;
            try {
                state.population.subpops[0].species.f_prototype = fitness(2, 0.8, 0.2);
                String multiPrompt = new PopulationFactory(state, multi, null, null).generationPrompt(10);
                String information = "### **Provided Information:**";
                String tasks = "### **Tasks:**";
                require(prompt.substring(prompt.indexOf(information), prompt.indexOf(tasks))
                        .equals(multiPrompt.substring(multiPrompt.indexOf(information), multiPrompt.indexOf(tasks))),
                        "Terminal meanings, all five reference rules and scores must be identical between objective modes.");
            } finally {
                state.population.subpops[0].species.f_prototype = original;
            }
            config.set("evospeak.normalization", true);
            require(new PopulationFactory(state, config, null, null).generationPrompt(10)
                    .contains("Actual optimized score: 1.0 * (mean-weighted-tardiness / benchmark[0])"),
                    "Single-objective normalization must be described according to params.");
            JSONObject expected = twoRuleResponse();
                String text = plainPopulationResponse(expected, 1);
                require(text.contains("Fitness: [d0|0.0|]\nTree 0:") && !text.contains("Expected objective effect:")
                    && RulePopulation.fromGeneratedText(text, 2, 1).size() == 2,
                    "Single-objective population replies must contain only the GP records.");
        } finally {
            state.output.close();
        }
    }

    private static String textGenerationResponse(JSONObject response) {
        return textGenerationResponse(response, 2);
    }

    private static String textGenerationResponse(JSONObject response, int objectiveCount) {
        StringBuilder text = new StringBuilder("## Insights Extraction\n");
        for (Object value : response.getJSONArray("insights")) {
            JSONObject insight = (JSONObject) value;
            text.append("- ").append(insight.getString("observation")).append(" (reference IDs: ")
                    .append(insight.getJSONArray("referenceIds")).append(")\n");
        }
        JSONArray individuals = response.getJSONArray("individuals");
        text.append("\n## New Heuristics\n<START>\nNumber of Individuals: i").append(individuals.length()).append("|\n");
        for (int index = 0; index < individuals.length(); index++) {
            JSONObject individual = individuals.getJSONObject(index);
            JSONObject explanation = individual.getJSONObject("explanation");
                text.append("Individual Number: i").append(index).append("|\nEvaluated: F\nFitness: ")
                    .append(objectiveCount == 1 ? "[d0|0.0|]" : "[d0|0.0| d0|0.0|]").append("\nTree 0:\n")
                    .append(individual.getString("sequencing")).append("\nTree 1:\n").append(individual.getString("routing"))
                    .append("\nSequencing: ").append(explanation.getString("sequencing"))
                    .append("\nRouting: ").append(explanation.getString("routing"))
                    .append(objectiveCount == 1 ? "\nExpected objective effect: " : "\nExpected objective trade-off: ")
                    .append(explanation.getString("objectiveTradeOff"))
                    .append("\nReference IDs: ").append(explanation.getJSONArray("referenceIds")).append("\n\n");
        }
        return text.append("<END>").toString();
    }

    private static void testPromptExport() throws Exception {
        Path root = Files.createTempDirectory("onlineevospeak-prompt-export-");
        for (String profile : new String[]{"multipletreegp-dynamicLLMWarmStart.params", "multipletreegp-dynamicLLMWarmStartMO.params"}) {
            Path parameters = Paths.get("src/mengxu/algorithm/OnlineEvoSpeak/" + profile);
            Path noRun = root.resolve(profile + "-no-run");
            Path output = root.resolve(profile + ".md");
            EvoSpeakConfig config = new EvoSpeakConfig(parameters,
                    "stat=ec.Statistics", "llm.provider=not-used-for-prompt-export",
                    "pop.subpop.0.species=" + FileBackedTestSpecies.class.getName(),
                    "evospeak.output-directory=" + noRun);
            EvoSpeakMain.main(new String[]{"--export-prompt", output.toString(), "-file", parameters.toString(),
                    "-p", "stat=ec.Statistics", "-p", "llm.provider=not-used-for-prompt-export",
                    "-p", "pop.subpop.0.species=" + FileBackedTestSpecies.class.getName(),
                    "-p", "evospeak.output-directory=" + noRun});
            String exported = Files.readString(output);
                String snapshotName = profile.contains("WarmStartMO") ? "multi-objective.md" : "single-objective.md";
                Path snapshot = Paths.get("src/mengxu/algorithm/OfflineEvoSpeak/prompts/" + snapshotName);
                    require(Files.readString(snapshot).replace("\r\n", "\n").equals(exported),
                    "The documented offline prompt must stay identical to the current online generator and profile.");
            EvoSpeakEvolutionState state = (EvoSpeakEvolutionState) Evolve.initialize(config.parameters, 0);
            state.output.setThrowsErrors(true);
            try {
                state.setup(state, null);
                state.population = state.initializer.setupPopulation(state, 0);
                PopulationFactory factory = new PopulationFactory(state, config, null, null);
                String prompt = factory.generationPrompt(10);
                require(exported.contains("```text\n" + factory.systemPrompt() + "\n```")
                        && exported.contains("```text\n" + prompt + "\n```"),
                        "Offline prompt export must exactly match both messages used by online generation.");
                require(prompt.contains("100 individuals") && prompt.contains("exactly 10 NEW, DISTINCT pairs")
                    && prompt.contains("reference data") && prompt.contains("Number of Individuals: i5|"),
                        "Export must include the configured population/batch sizes, actual references and terminal facts.");
            } finally {
                state.output.close();
            }
            require(!Files.exists(noRun), "Prompt export must not create GP run files or require a working LLM provider.");
            boolean overwriteBlocked = false;
            try {
                EvoSpeakMain.exportPrompt(config, output);
            } catch (java.nio.file.FileAlreadyExistsException expected) {
                overwriteBlocked = true;
            }
            require(overwriteBlocked && Files.readString(output).equals(exported), "Export must preserve an existing prompt file.");
        }
    }

    private static void testParameterProfiles() throws Exception {
        String base = "src/mengxu/algorithm/OnlineEvoSpeak/";
        String[] filenames = {"multipletreegp-dynamicLLMWarmStart.params", "multipletreegp-dynamicLLMWarmStartMO.params"};
        for (int index = 0; index < filenames.length; index++) {
            EvoSpeakConfig config = new EvoSpeakConfig(Paths.get(base + filenames[index]));
            int count = index + 1;
            require(config.integer("eval.problem.eval-model.objectives", 0) == count, "Profile evaluation dimensions");
            require(config.integer("pop.subpop.0.species.fitness.num-objectives", 0) == count, "Profile fitness dimensions");
            require(config.text("evospeak.objective.0", "").equals(index == 0 ? "mean-weighted-tardiness" : "mean-flowtime"),
                    "Profiles must preserve the original objectives.");
            require(config.flag("evospeak.normalization", true) == (index == 1), "Profiles must preserve raw single and normalized multi fitness.");
            require(config.integer("pop.subpop.0.size", 0) == 100 && config.integer("generations", 0) == 51, "Original GP budget");
            require(config.text("state", "").equals(EvoSpeakEvolutionState.class.getName()), "Profiles must use the V1 pipeline.");
            require(Files.isRegularFile(config.path("evospeak.examples-file", "")), "Relative profile references must resolve.");
                require(config.text("llm.provider", "").equals("azure-openai") && config.text("llm.api", "").equals("responses")
                        && config.text("llm.model", "").equals("gpt-5.6-luna"), "Both profiles must use the requested Azure Responses deployment.");
                    require(config.text("llm.endpoint", "").equals("https://hackathon-qh.cognitiveservices.azure.com/openai/responses?api-version=2025-04-01-preview"),
                    "Profiles must use the full request URL, not just the Azure base URL.");
                    require(config.text("llm.model-version", "").isEmpty(), "Do not inherit an unverified version from the previous deployment.");
                require(config.configuredApiKey().isEmpty() && config.text("llm.api-key-env", "").equals("AZURE_OPENAI_API_KEY"),
                    "Shared Azure profiles must stay secret-free and name the Azure key environment variable.");
            EvolutionState state = new EvolutionState();
            state.parameters = config.parameters;
            state.output = new Output(true);
            WeightedFitness fitness = new WeightedFitness();
            fitness.setup(state, new Parameter("pop.subpop.0.species.fitness"));
            requireClose(index == 0 ? 1.0 : 0.8, fitness.getWeights()[0], "Original objective-0 weight");
            if (index == 1) {
                requireClose(0.2, fitness.getWeights()[1], "Complementary objective-1 weight");
            }
            require(!config.parameters.exists(new Parameter("llm.api-key"), null), "ECJ must not retain the API-key parameter.");
            state.output.close();
            Path output = Files.createTempDirectory("evospeak-profile-smoke-");
            EvoSpeakConfig smoke = new EvoSpeakConfig(Paths.get(base + filenames[index]),
                    "evospeak.source=file", "evospeak.population-file=offline-example.json",
                    "evospeak.output-directory=" + output.toString().replace('\\', '/'),
                    "pop.subpop.0.size=2", "breed.elite.0=1", "generations=2",
                    "evospeak.validation.jobs=100", "evospeak.validation.warmup=10", "evospeak.validation.seeds=17001",
                    "eval.problem.eval-model.sim-models.0.num-jobs=200", "eval.problem.eval-model.sim-models.0.warmup-jobs=20");
            Path run = EvoSpeakMain.run(smoke, null);
            List<String> generations = Files.readAllLines(run.resolve("generations.jsonl"));
            require(generations.size() == 2, "Both new profiles must complete a real two-generation GP run.");
            for (String row : generations) {
                JSONObject metrics = new JSONObject(row);
                require(metrics.getJSONArray("objectives").length() == count, "Runtime objectives must match the selected profile.");
                JSONArray actualWeights = metrics.getJSONArray("weights");
                JSONArray baselines = metrics.getJSONArray("normalizationBaselines");
                double expectedScore = 0.0;
                for (int objective = 0; objective < count; objective++) {
                    requireClose(fitness.getWeights()[objective], actualWeights.getDouble(objective), "Runtime profile weights");
                    expectedScore += actualWeights.getDouble(objective) * metrics.getJSONArray("objectives").getDouble(objective)
                            / baselines.getDouble(objective);
                }
                requireClose(expectedScore, metrics.getDouble("weightedFitness"), "Runtime profile scoring");
                if (index == 0) {
                    requireClose(1.0, baselines.getDouble(0), "Single-objective profile must use the raw objective.");
                }
            }
        }
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
            testExamplePrompt(state);
            testGenerationContract(state);
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
                    "src/mengxu/algorithm/OfflineEvoSpeak/WarmStart/population_100_0.5_MO.txt"));
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

    private static void testExamplePrompt(GPRuleEvolutionState state) throws Exception {
        EvoSpeakConfig config = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                "evospeak.weight.0=3", "evospeak.weight.1=7", "evospeak.normalization=false");
        ec.Fitness original = state.population.subpops[0].species.f_prototype;
        try {
            state.population.subpops[0].species.f_prototype = fitness(2, 3.0, 7.0);
            String prompt = new PopulationFactory(state, config, null, null).generationPrompt(4);
            require(prompt.contains("(- MWT W)") && prompt.contains("2248.62368499086"), "The supplied reference heuristics must reach generation.");
            require(prompt.contains("terminal interactions") && prompt.contains("candidate-invariant"), "Examples require grounded analysis, not blind imitation.");
            require(prompt.contains("0.3 * mean-flowtime + 0.7 * mean-weighted-tardiness"), "Prompt must use normalized configured weights and the raw score.");
            require(prompt.contains("lambda2 = 1 - lambda1") && prompt.contains("unverified"), "Weight and fitness provenance must be explicit.");
            config.set("evospeak.normalization", true);
            prompt = new PopulationFactory(state, config, null, null).generationPrompt(4);
            require(prompt.contains("0.3 * (mean-flowtime / benchmark[0])"), "Normalized score must not be described as a raw sum.");
                require(prompt.contains("Fmean") && prompt.contains("WTmean"), "Include the supplied objective notation without inventing new objectives.");
                EvoSpeakConfig single = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                    "evospeak.objective-mode=single", "evospeak.objective.0=max-flowtime", "evospeak.normalization=false");
                state.population.subpops[0].species.f_prototype = fitness(1, 1.0, 0.0);
                prompt = new PopulationFactory(state, single, null, null).generationPrompt(4);
                require(prompt.contains("1.0 * max-flowtime") && prompt.contains("Single objective:")
                    && !prompt.contains("lambda2 = 1 - lambda1"), "Examples must not override single-objective configuration.");
        } finally {
            state.population.subpops[0].species.f_prototype = original;
        }
    }

    private static void testGenerationContract(GPRuleEvolutionState state) throws Exception {
        AtomicReference<String> response = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/contract", exchange -> {
            exchange.getRequestBody().readAllBytes();
            calls.incrementAndGet();
            byte[] bytes = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", "stop")
                    .put("message", new JSONObject().put("content", response.get())))).toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        Path root = Files.createTempDirectory("evospeak-generation-contract-");
        try {
            EvoSpeakConfig config = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                    "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/contract", "llm.timeout-seconds=5",
                    "evospeak.batch-size=2", "evospeak.max-batches=1", "pop.subpop.0.size=2", "breed.elite.0=1",
                    "evospeak.validation.jobs=100", "evospeak.validation.warmup=10", "evospeak.validation.seeds=17001");
            String valid = plainPopulationResponse(twoRuleResponse(), 2);
            Map<String, String> failures = new LinkedHashMap<>();
            failures.put("plain ECJ TXT", twoRuleResponse().toString());
            failures.put("seven nonblank lines", valid + "Insights: unwanted explanation\n");
            failures.put("Number of Individuals appears only once", valid.replace("Individual Number: i1|", "Number of Individuals: i1|"));
            failures.put("Evaluated: F", valid.replace("Evaluated: F", "Evaluated: T"));
            failures.put("Tree 0: and Tree 1:", valid.replace("Tree 1:", "Tree 2:"));
            failures.put("Do not copy historical fitness", valid.replace("d0|0.0|", "d12345678901234560104|2570.0|"));
            JSONObject shortBatch = twoRuleResponse();
            shortBatch.getJSONArray("individuals").remove(1);
            failures.put("Return exactly 2 individuals", plainPopulationResponse(shortBatch, 2));
            for (Map.Entry<String, String> failure : failures.entrySet()) {
                response.set(failure.getValue());
                Path output = Files.createTempDirectory(root, "rejected-");
                boolean rejected = false;
                try {
                    new PopulationFactory(state, config, new LlmClient(config, name -> "test-key"), output).create();
                } catch (java.io.IOException expected) {
                    rejected = true;
                }
                require(rejected, "Malformed generation response must be rejected: " + failure.getKey());
                require(!Files.exists(output.resolve("generated-population.txt")), "Incomplete batches cannot publish an ECJ population.");
                require(Files.readString(output.resolve("validation-report.json")).contains(failure.getKey()),
                        "Keep corrective feedback for " + failure.getKey());
            }

            Path examples = root.resolve("examples.json");
            Files.writeString(examples, new JSONObject().put("individuals", new JSONArray()
                    .put(new RulePopulation.Rules("PT", "WIQ").json())
                    .put(new RulePopulation.Rules("NOR", "PT").json())).toString());
            config.set("evospeak.examples-file", examples.toString());
            response.set(valid);
            Path copied = Files.createTempDirectory(root, "copied-");
            try {
                new PopulationFactory(state, config, new LlmClient(config, name -> "test-key"), copied).create();
                throw new AssertionError("Exact reference copies must not become generated warm-start individuals.");
            } catch (java.io.IOException expected) {
                require(Files.readString(copied.resolve("validation-report.json")).contains("Exact copy of a reference pair"),
                        "Reference copy rejection must be explicit.");
            }

            Files.writeString(examples, new JSONObject().put("individuals", new JSONArray()
                    .put(new RulePopulation.Rules("UNKNOWN", "WIQ").json())).toString());
            int previousCalls = calls.get();
            try {
                new PopulationFactory(state, config, new LlmClient(config, name -> "test-key"),
                        Files.createTempDirectory(root, "invalid-source-")).create();
                throw new AssertionError("Invalid reference syntax must fail before calling the LLM.");
            } catch (IllegalArgumentException expected) {
                require(calls.get() == previousCalls, "Do not send malformed examples to an external model.");
            }

            config.set("evospeak.examples-file", "");
            response.set(valid);
            List<GPIndividual> accepted = new PopulationFactory(state, config, new LlmClient(config, name -> "test-key"),
                    Files.createTempDirectory(root, "no-examples-")).create();
            require(accepted.size() == 2, "Optional example-free generation must continue to work.");
        } finally {
            server.stop(0);
        }
    }

    private static void testProxyTransport() throws Exception {
        AtomicReference<String> requestedUri = new AtomicReference<>();
        AtomicReference<String> probeAuthorization = new AtomicReference<>();
        AtomicInteger probeBodyLength = new AtomicInteger(-1);
        HttpServer proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            requestedUri.set(exchange.getRequestURI().toString());
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            if (exchange.getRequestMethod().equals("HEAD")) {
                probeAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                probeBodyLength.set(requestBody.length);
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            byte[] response = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", "stop")
                    .put("message", new JSONObject().put("content", "proxy-ok")))).toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        java.net.ProxySelector originalSelector = java.net.ProxySelector.getDefault();
        proxy.start();
        try (java.net.ServerSocket unavailableOrigin = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            int unavailablePort = unavailableOrigin.getLocalPort();
            unavailableOrigin.close();
            String endpoint = "http://127.0.0.1:" + unavailablePort + "/chat";
            EvoSpeakConfig config = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                    "llm.endpoint=" + endpoint, "llm.proxy=http://127.0.0.1:" + proxy.getAddress().getPort(),
                    "llm.max-attempts=1", "llm.connect-timeout-seconds=2", "llm.timeout-seconds=3");
            LlmClient client = new LlmClient(config, name -> "network-test-key");
            require(client.complete("Test", "Test").equals("proxy-ok"), "Explicit proxy must deliver the request without a reachable origin.");
            require(endpoint.equals(requestedUri.get()), "The HTTP proxy must receive the full target URI.");
                config.set("llm.api-key", "network-probe-test-key");
                String check = LlmClient.checkConnection(config);
                require(check.contains("HTTP 401") && check.contains("not API-key validity"), "A connection probe must distinguish transport from authentication.");
                require(probeAuthorization.get() == null && probeBodyLength.get() == 0, "Connection checks must never send keys or prompts.");
                Path unusedOutput = Files.createTempDirectory("evospeak-network-check-").resolve("not-created");
                EvoSpeakMain.main(new String[]{"--check-connection", "-file", EvoSpeakConfig.DEFAULT_PARAMS,
                    "-p", "llm.endpoint=" + endpoint, "-p", "llm.proxy=http://127.0.0.1:" + proxy.getAddress().getPort(),
                    "-p", "llm.api-key-env=", "-p", "evospeak.output-directory=" + unusedOutput});
                require(!Files.exists(unusedOutput), "The check-only entry point must not create GP runs or require a key.");
            java.net.ProxySelector.setDefault(java.net.ProxySelector.of(new InetSocketAddress("127.0.0.1", proxy.getAddress().getPort())));
            config.set("llm.proxy", "system");
            require(new LlmClient(config, name -> "network-test-key").complete("Test", "Test").equals("proxy-ok"),
                    "System mode must honor the JVM proxy selector.");
            config.set("llm.proxy", "direct");
            requestedUri.set(null);
            boolean connectionFailed = false;
            try {
                new LlmClient(config, name -> "network-test-key").complete("Test", "Test");
            } catch (java.io.IOException expected) {
                connectionFailed = expected.getMessage().contains("direct connection")
                        && expected.getMessage().contains("llm.proxy=") && !expected.getMessage().contains("network-test-key");
            }
            require(connectionFailed && requestedUri.get() == null, "Direct mode must bypass system proxies and report actionable connection errors.");
            for (String invalid : new String[]{"socks5://127.0.0.1:1080", "http://127.0.0.1", "http://127.0.0.1:65536",
                    "http://name:private-password@localhost:7890", "http://localhost:7890/path", "http://localhost:7890?token=secret"}) {
                config.set("llm.proxy", invalid);
                boolean rejected = false;
                try {
                    new LlmClient(config, name -> "network-test-key");
                } catch (IllegalArgumentException expected) {
                    rejected = expected.getMessage().contains("llm.proxy") && !expected.getMessage().contains("private-password");
                }
                require(rejected, "Unsupported or credential-bearing proxy URLs must be rejected safely.");
            }
            require(client.transportFailure(new java.net.http.HttpConnectTimeoutException("test")).getMessage().contains("TCP connection"),
                    "Connect timeout must not be misreported as model response latency.");
            require(client.transportFailure(new java.net.http.HttpTimeoutException("test")).getMessage().contains("request timed out"),
                    "Request timeout guidance must differ from TCP connection guidance.");
            require(client.transportFailure(new javax.net.ssl.SSLHandshakeException("test")).getMessage().contains("JVM trust store"),
                    "TLS failures must not suggest bypassing certificate validation.");
        } finally {
            java.net.ProxySelector.setDefault(originalSelector);
            proxy.stop(0);
        }
    }

    private static void testResponsesApi() throws Exception {
        AtomicReference<JSONObject> requestBody = new AtomicReference<>();
        AtomicReference<String> responseQuery = new AtomicReference<>();
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<JSONObject> responseBody = new AtomicReference<>(responsesEnvelope("responses-ok"));
        AtomicInteger authStatus = new AtomicInteger(200);
        AtomicInteger authRequests = new AtomicInteger();
        AtomicReference<String> authMethod = new AtomicReference<>();
        AtomicInteger authBodyLength = new AtomicInteger(-1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        com.sun.net.httpserver.HttpHandler responsesHandler = exchange -> {
            requestBody.set(new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            responseQuery.set(exchange.getRequestURI().getRawQuery());
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("api-key"));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = responseBody.get().toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        };
        server.createContext("/openai/v1/responses", responsesHandler);
        server.createContext("/openai/responses", responsesHandler);
            server.createContext("/openai/v1/models", exchange -> {
                authRequests.incrementAndGet();
                authMethod.set(exchange.getRequestMethod());
                authBodyLength.set(exchange.getRequestBody().readAllBytes().length);
                apiKeyHeader.set(exchange.getRequestHeaders().getFirst("api-key"));
                authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                JSONObject result = authStatus.get() == 200 ? new JSONObject().put("data", new JSONArray())
                    : new JSONObject().put("error", new JSONObject().put("code", "invalid_api_key")
                        .put("message", "azure-test-key"));
                byte[] response = result.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(authStatus.get(), response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
        server.start();
        try {
            EvoSpeakConfig config = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                    "llm.provider=azure-openai", "llm.api=responses", "llm.model=gpt-5.6-sol",
                    "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/responses",
                    "llm.proxy=direct", "llm.timeout-seconds=3", "llm.max-attempts=3", "llm.max-output-tokens=1234");
            LlmClient client = new LlmClient(config, name -> "azure-test-key");
            require(client.complete("Generation instructions", "Generation prompt").equals("responses-ok"),
                    "Responses text must be extracted after a leading reasoning item.");
            JSONObject request = requestBody.get();
            require(request.getString("model").equals("gpt-5.6-sol") && request.getString("instructions").equals("Generation instructions")
                    && request.getString("input").equals("Generation prompt"), "Responses API must preserve deployment and both prompt components.");
            require(request.getInt("max_output_tokens") == 1234 && !request.has("messages") && !request.has("max_tokens")
                    && !request.has("max_completion_tokens") && !request.has("temperature"), "Use only the Responses request fields, with optional temperature omitted.");
            require(!request.getBoolean("store") && !request.getBoolean("stream") && !request.getBoolean("background"),
                    "Rule generation must remain synchronous and stateless.");
            require("azure-test-key".equals(apiKeyHeader.get()) && authorizationHeader.get() == null,
                    "Azure resource-key authentication must use api-key, not an OpenAI Bearer key.");
                for (String profile : new String[]{"multipletreegp-dynamicLLMWarmStart.params", "multipletreegp-dynamicLLMWarmStartMO.params"}) {
                EvoSpeakConfig selected = new EvoSpeakConfig(Paths.get("src/mengxu/algorithm/OnlineEvoSpeak/" + profile),
                    "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/responses",
                    "llm.proxy=direct", "llm.timeout-seconds=3");
                selected.set("llm.api-key", "azure-test-key");
                require(new LlmClient(selected).complete("Test", "Test").equals("responses-ok"),
                    "Both public profiles must select the Responses adapter through normal parameter loading.");
                require(LlmClient.checkAuthentication(selected).contains("model-list request was accepted"),
                    "Azure v1 auth checks must resolve the sibling /openai/v1/models endpoint.");
                require("GET".equals(authMethod.get()) && authBodyLength.get() == 0
                    && "azure-test-key".equals(apiKeyHeader.get()) && authorizationHeader.get() == null,
                    "Azure auth checks must be read-only and use resource-key authentication without a prompt.");
                for (int status : new int[]{401, 403, 404, 405}) {
                    authStatus.set(status);
                    int requestsBefore = authRequests.get();
                    try {
                    LlmClient.checkAuthentication(selected);
                    throw new AssertionError("Failed Azure auth probes must not report success.");
                    } catch (java.io.IOException expected) {
                    require(!expected.getMessage().contains("azure-test-key"), "Azure auth errors must not reveal a key.");
                    require(status == 401 ? expected.getMessage().contains("Key1/Key2")
                        : expected.getMessage().contains("restricted or unsupported"), "Distinguish resource-key rejection from model-list permissions.");
                    }
                    require(authRequests.get() == requestsBefore + 1, "Azure auth checks must not retry or generate text.");
                }
                authStatus.set(200);
                selected.set("llm.endpoint", "http://127.0.0.1:" + server.getAddress().getPort()
                    + "/openai/responses?api-version=2025-04-01-preview");
                require(new LlmClient(selected).complete("Test", "Test").equals("responses-ok")
                    && "api-version=2025-04-01-preview".equals(responseQuery.get())
                    && requestBody.get().getString("model").equals("gpt-5.6-luna")
                    && "azure-test-key".equals(apiKeyHeader.get()) && authorizationHeader.get() == null,
                    "Dated Azure Responses must preserve the API-version query, deployment and resource-key header.");
                }
            config.set("llm.provider", "openai-compatible");
            require(new LlmClient(config, name -> "openai-test-key").complete("Test", "Test").equals("responses-ok")
                    && "Bearer openai-test-key".equals(authorizationHeader.get()) && apiKeyHeader.get() == null,
                    "Responses must also support the existing OpenAI-compatible authentication convention.");
                config.set("llm.api-key", "openai-test-key");
                require(LlmClient.checkAuthentication(config).contains("model-list request was accepted")
                    && "Bearer openai-test-key".equals(authorizationHeader.get()) && apiKeyHeader.get() == null,
                    "OpenAI Responses auth checks must also use the sibling models endpoint with Bearer authentication.");
            for (String status : new String[]{"incomplete", "failed", "queued", "in_progress", "cancelled"}) {
                JSONObject failure = responsesEnvelope("partial-secret-text").put("status", status);
                if (status.equals("incomplete")) {
                    failure.put("incomplete_details", new JSONObject().put("reason", "max_output_tokens"));
                }
                responseBody.set(failure);
                try {
                    client.complete("Test", "Test");
                    throw new AssertionError("Only completed Responses output may be consumed.");
                } catch (java.io.IOException expected) {
                    require(!expected.getMessage().contains("partial-secret-text"), "Never echo failed output in error messages.");
                    require(!status.equals("incomplete") || expected.getMessage().contains("truncated"), "Identify exhausted output-token budgets.");
                }
            }
            JSONObject refusal = responsesEnvelope("partial-secret-text");
            refusal.getJSONArray("output").getJSONObject(1).getJSONArray("content")
                    .put(new JSONObject().put("type", "refusal").put("refusal", "provider-refusal-text"));
            responseBody.set(refusal);
            try {
                client.complete("Test", "Test");
                throw new AssertionError("A mixed text/refusal response must be rejected.");
            } catch (java.io.IOException expected) {
                require(expected.getMessage().contains("refused") && !expected.getMessage().contains("provider-refusal-text"),
                        "Refusals must be detected without exposing provider text.");
            }
            responseBody.set(responsesEnvelope("").put("content_filters", new JSONArray().put(new JSONObject().put("blocked", true))));
            assertResponsesRejected(client, "filtered");
            responseBody.set(responsesEnvelope(""));
            assertResponsesRejected(client, "empty text");
            responseBody.set(responsesEnvelope("partial-secret-text").put("error", new JSONObject().put("message", "azure-test-key")));
            assertResponsesRejected(client, "did not complete");
            responseBody.set(responsesEnvelope("partial-secret-text"));
            responseBody.get().getJSONArray("output").getJSONObject(1).put("status", "incomplete");
            assertResponsesRejected(client, "incomplete assistant message");
            responseBody.set(responsesEnvelope("partial-secret-text").put("output", new JSONArray().put("azure-test-key")));
            assertResponsesRejected(client, "incompatible JSON response envelope");
            responseBody.set(responsesEnvelope("partial-secret-text"));
            responseBody.get().getJSONArray("output").put(new JSONObject().put("type", "function_call"));
            assertResponsesRejected(client, "non-text output item");
            for (String profile : new String[]{"multipletreegp-dynamicLLMWarmStart.params", "multipletreegp-dynamicLLMWarmStartMO.params"}) {
                Path workflowRoot = Files.createTempDirectory("evospeak-responses-workflow-");
                EvoSpeakConfig workflow = new EvoSpeakConfig(Paths.get("src/mengxu/algorithm/OnlineEvoSpeak/" + profile),
                    "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/openai/responses?api-version=2025-04-01-preview",
                    "llm.proxy=direct", "llm.timeout-seconds=3",
                    "pop.subpop.0.species=" + FileBackedTestSpecies.class.getName(),
                    "evospeak.output-directory=" + workflowRoot.toString().replace('\\', '/'),
                    "evospeak.batch-size=2", "evospeak.max-batches=1", "pop.subpop.0.size=2", "breed.elite.0=1", "generations=2",
                    "evospeak.validation.jobs=100", "evospeak.validation.warmup=10", "evospeak.validation.seeds=17001",
                    "eval.problem.eval-model.sim-models.0.num-jobs=200", "eval.problem.eval-model.sim-models.0.warmup-jobs=20");
                loadedWarmStartRules.clear();
                    int workflowObjectives = workflow.integer("eval.problem.eval-model.objectives", 1);
                    JSONObject expectedPopulation = twoRuleResponse();
                    String generationResponse = plainPopulationResponse(expectedPopulation, workflowObjectives);
                    responseBody.set(responsesEnvelope(generationResponse));
                Path run = EvoSpeakMain.run(workflow, new LlmClient(workflow, name -> "azure-test-key"));
                require(requestBody.get().getString("input").contains("mean-weighted-tardiness"),
                    "Responses population generation must preserve the configured objective prompt.");
                    require(requestBody.get().getString("instructions").equals(PopulationFactory.SYSTEM_PROMPT)
                        && requestBody.get().getString("instructions").contains("plain-text ECJ subpopulation"),
                        "Both objective modes must request only a pure ECJ population TXT.");
                    require(requestBody.get().getString("input").contains("Number of Individuals appears exactly ONCE")
                        && requestBody.get().getString("input").contains(RulePopulation.initialFitnessLine(workflowObjectives)),
                        "The actual prompt must prohibit repeated headers and prescribe the correct initial fitness dimension.");
                    require(Files.readString(run.resolve("generation-response-0.txt")).equals(generationResponse),
                        "Retain the original LLM reply in its requested format.");
                    JSONObject generatedRules = new JSONObject(Files.readString(run.resolve("generated-rules.json")));
                    require(generatedRules.getString("responseFormat").equals("ecj-population-txt")
                        && !generatedRules.getJSONArray("generationBatches").getJSONObject(0).has("insights"),
                        "Pure TXT generation must not require or fabricate insight content.");
                    for (int index = 0; index < 2; index++) {
                        require(!generatedRules.getJSONArray("individuals").getJSONObject(index).has("explanation")
                            && generatedRules.getJSONArray("individuals").getJSONObject(index).getInt("generationBatch") == 0,
                            "Save rule provenance without manufacturing explanations absent from the plain response.");
                    }
                JSONObject runStatus = new JSONObject(Files.readString(run.resolve("status.json")));
                require(runStatus.getString("state").equals("completed") && runStatus.getString("api").equals("responses")
                    && Files.readAllLines(run.resolve("generations.jsonl")).size() == 2,
                    "Azure Responses output must pass rule validation and reach real GP evolution.");
                    require(runStatus.getString("configuredModelVersion").isEmpty()
                        && "api-version=2025-04-01-preview".equals(responseQuery.get())
                        && !requestBody.get().has("model_version") && !requestBody.get().has("api-version"),
                        "Model version is declared run metadata, not a Responses request parameter or API version.");
                require(RulePopulation.read(run.resolve("generated-population.txt")).size() == 2,
                    "Responses-generated rules must retain the native ECJ population format.");
                String serialized = Files.readString(run.resolve("generated-population.txt"));
                require(serialized.replace("\r\n", "\n").equals(generationResponse),
                    "The published TXT must match the reference layout exactly, including blank lines and unindented trees.");
                require(serialized.startsWith("Number of Individuals: i2|")
                    && serialized.lines().filter(line -> line.equals("Evaluated: F")).count() == 2
                    && serialized.lines().filter(line -> line.startsWith("Fitness: [")).count() == 2
                    && serialized.lines().filter(line -> line.equals("Tree 0:")).count() == 2
                    && serialized.lines().filter(line -> line.equals("Tree 1:")).count() == 2,
                    "Initial TXT must use the standalone ECJ layout for both objective modes.");
                require(loadedWarmStartRules.size() == 2 && runStatus.getBoolean("initialPopulationLoaded")
                    && Paths.get(runStatus.getString("initialPopulationFile")).equals(run.resolve("generated-population.txt")),
                    "Both objective modes must initialize GP by reading the published TXT, not the generation list.");
                List<RulePopulation.Rules> saved = RulePopulation.read(run.resolve("generated-population.txt"));
                for (int index = 0; index < saved.size(); index++) {
                    require(saved.get(index).sequencing.equals(loadedWarmStartRules.get(index).sequencing)
                        && saved.get(index).routing.equals(loadedWarmStartRules.get(index).routing),
                        "The GP initial trees must match the saved initial population exactly and in order.");
                }
                workflow.set("pop.subpop.0.species", "ec.gp.GPSpecies");
                JSONObject explanation = new JSONObject().put("sequencing", "PT and W influence sequencing priorities.")
                    .put("routing", "WIQ measures candidate-machine queue work.")
                    .put("interaction", "Queue assignment and job ordering act together.")
                    .put("limitations", "These interpretations are not measured performance improvements.");
                responseBody.set(responsesEnvelope(explanation.toString()));
                Path report = run.resolve("responses-analysis.md");
                RuleAnalysisMain.analyze(workflow, new LlmClient(workflow, name -> "azure-test-key"),
                    run.resolve("generated-population.txt"), report);
                require(Files.readString(report).contains("Analysis complete") && requestBody.get().getString("input").contains("terminalsUsed"),
                    "The independent analyzer must use Responses while preserving verified terminal facts.");
                loadedWarmStartRules.clear();
                EvoSpeakConfig generationOnly = workflow.copy();
                generationOnly.set("pop.subpop.0.species", FileBackedTestSpecies.class.getName());
                generationOnly.set("evospeak.run-gp", false);
                generationOnly.set("evospeak.output-directory", workflowRoot.resolve("generate-only"));
                responseBody.set(responsesEnvelope(generationResponse));
                Path generatedOnly = EvoSpeakMain.run(generationOnly, new LlmClient(generationOnly, name -> "azure-test-key"));
                JSONObject generatedStatus = new JSONObject(Files.readString(generatedOnly.resolve("status.json")));
                require(generatedStatus.getString("state").equals("validated") && !generatedStatus.getBoolean("initialPopulationLoaded")
                    && loadedWarmStartRules.isEmpty() && RulePopulation.read(generatedOnly.resolve("generated-population.txt")).size() == 2,
                    "Generate-only mode must publish the complete TXT without initializing a GP population.");
                require(!Files.exists(Paths.get(generatedStatus.getString("statisticsFile")))
                    && !Files.exists(generatedOnly.resolve("generations.jsonl")),
                    "Generate-only mode must not initialize GP statistics or run generations.");
                EvoSpeakConfig offlineReply = generationOnly.copy();
                offlineReply.set("evospeak.source", "file");
                offlineReply.set("evospeak.population-file", run.resolve("generation-response-0.txt"));
                offlineReply.set("evospeak.output-directory", workflowRoot.resolve("offline-text-import"));
                Path imported = EvoSpeakMain.run(offlineReply, null);
                require(Files.readString(imported.resolve("generated-population.txt")).equals(serialized)
                    && loadedWarmStartRules.isEmpty(),
                    "An offline pure population response must validate into the same native TXT without any LLM call or GP initialization.");
                Path failureRoot = workflowRoot.resolve("failed-generation");
                generationOnly.set("evospeak.run-gp", true);
                generationOnly.set("evospeak.output-directory", failureRoot);
                JSONObject invalidPopulation = generatedResponse(new JSONArray()
                    .put(new RulePopulation.Rules("UNKNOWN", "WIQ").json())
                    .put(new RulePopulation.Rules("PT", "WIQ").json()));
                responseBody.set(responsesEnvelope(plainPopulationResponse(invalidPopulation, workflowObjectives)));
                boolean blocked = false;
                try {
                    EvoSpeakMain.run(generationOnly, new LlmClient(generationOnly, name -> "azure-test-key"));
                } catch (java.io.UncheckedIOException expected) {
                    blocked = true;
                }
                require(blocked && loadedWarmStartRules.isEmpty(), "An incomplete LLM population must stop before GP file loading.");
                try (java.util.stream.Stream<Path> directories = Files.list(failureRoot)) {
                    Path failed = directories.filter(Files::isDirectory).findFirst().orElseThrow();
                    JSONObject failedStatus = new JSONObject(Files.readString(failed.resolve("status.json")));
                    require(failedStatus.getString("state").equals("failed") && !failedStatus.getBoolean("initialPopulationLoaded")
                        && !Files.exists(failed.resolve("generated-population.txt"))
                        && !Files.exists(Paths.get(failedStatus.getString("statisticsFile")))
                        && !Files.exists(failed.resolve("generations.jsonl")),
                        "Generation failure must retain its audit status but publish neither a partial TXT nor GP output.");
                }
            }
            config.set("llm.api", "chat-completions");
            boolean mismatchRejected = false;
            try {
                new LlmClient(config, name -> "test-key");
            } catch (IllegalArgumentException expected) {
                mismatchRejected = expected.getMessage().contains("llm.api=responses");
            }
            require(mismatchRejected, "Endpoint/protocol mismatches must fail before network access.");
        } finally {
            server.stop(0);
        }
    }

    private static final List<RulePopulation.Rules> loadedWarmStartRules = new java.util.ArrayList<>();

    public static class FileBackedTestSpecies extends ec.gp.GPSpecies {
        @Override
        public ec.Individual newIndividual(EvolutionState state, int thread) {
            throw new AssertionError("The LLM warm start must not initialize random individuals before or after generation.");
        }

        @Override
        public ec.Individual newIndividual(EvolutionState state, java.io.LineNumberReader reader) throws java.io.IOException {
            Path file = state.parameters.getFile(new Parameter("pop.subpop.0.file"), null).toPath();
            require(Files.isRegularFile(file), "The initial TXT must be saved before GP reads any individual.");
            ec.Individual individual = super.newIndividual(state, reader);
            require(!individual.evaluated && individual.fitness instanceof WeightedFitness
                    && ((WeightedFitness) individual.fitness).getObjectives().length
                    == state.parameters.getInt(new Parameter("eval.problem.eval-model.objectives"), null),
                    "Loaded fitness must remain unevaluated and match the selected objective dimensions.");
            loadedWarmStartRules.add(RulePopulation.rules((GPIndividual) individual));
            return individual;
        }
    }

    private static void assertResponsesRejected(LlmClient client, String message) throws Exception {
        int requestsBefore = client.requestCount();
        try {
            client.complete("Test", "Test");
            throw new AssertionError("Invalid Responses output must not reach population generation.");
        } catch (java.io.IOException expected) {
            java.io.StringWriter trace = new java.io.StringWriter();
            expected.printStackTrace(new java.io.PrintWriter(trace));
            require(expected.getMessage().contains(message) && !trace.toString().contains("azure-test-key")
                    && !trace.toString().contains("partial-secret-text"), "Responses errors must be descriptive without leaking response data.");
        }
        require(client.requestCount() == requestsBefore + 1, "Invalid Responses output must not trigger transport retries.");
    }

    private static JSONObject responsesEnvelope(String text) {
        int middle = text.length() / 2;
        return new JSONObject().put("status", "completed").put("error", JSONObject.NULL)
                .put("output", new JSONArray().put(new JSONObject().put("type", "reasoning").put("summary", new JSONArray()))
                        .put(new JSONObject().put("type", "message").put("role", "assistant").put("status", "completed")
                                .put("content", new JSONArray().put(new JSONObject().put("type", "output_text").put("text", text.substring(0, middle)))
                                        .put(new JSONObject().put("type", "output_text").put("text", text.substring(middle))))));
    }

    private static void testLlmProviders() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
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
                AtomicInteger authStatus = new AtomicInteger(200);
                AtomicInteger authRequests = new AtomicInteger();
                AtomicReference<String> authMethod = new AtomicReference<>();
                AtomicReference<String> authHeader = new AtomicReference<>();
                AtomicInteger authBodyLength = new AtomicInteger(-1);
                server.createContext("/v1/models", exchange -> {
                authRequests.incrementAndGet();
                authMethod.set(exchange.getRequestMethod());
                authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                authBodyLength.set(exchange.getRequestBody().readAllBytes().length);
                JSONObject body = authStatus.get() == 200 ? new JSONObject().put("data", new JSONArray())
                    : new JSONObject().put("error", new JSONObject().put("code", "invalid_api_key")
                        .put("message", "Never print probe-test-key"));
                byte[] response = body.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(authStatus.get(), response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
                });
                EvoSpeakConfig authConfig = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS),
                    "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions",
                    "llm.proxy=direct", "llm.timeout-seconds=3");
                authConfig.set("llm.api-key", "probe-test-key");
                String authResult = LlmClient.checkAuthentication(authConfig);
                require(authResult.contains("model-list request was accepted") && !authResult.contains("probe-test-key"),
                    "Authentication check must return safe success guidance without echoing credentials.");
                require(authMethod.get().equals("GET") && authHeader.get().equals("Bearer probe-test-key") && authBodyLength.get() == 0,
                    "Auth check must use the same authentication header with a read-only request and no prompt.");
                for (int status : new int[]{401, 403, 404}) {
                authStatus.set(status);
                int requestsBefore = authRequests.get();
                try {
                    LlmClient.checkAuthentication(authConfig);
                    throw new AssertionError("Failed authentication/permission checks must not report success.");
                } catch (java.io.IOException expected) {
                    require(!expected.getMessage().contains("probe-test-key"), "Auth probe errors must be sanitized.");
                    require(status == 401 ? expected.getMessage().contains("invalid_api_key")
                            : expected.getMessage().contains("restricted or unsupported"),
                        "Differentiate invalid credentials from model-list permission or endpoint support.");
                }
                require(authRequests.get() == requestsBefore + 1, "Auth checks must make a single request without generation retries.");
                }
                authStatus.set(200);
                Path checkOutput = Files.createTempDirectory("evospeak-auth-check-").resolve("not-created");
                EvoSpeakMain.main(new String[]{"--check-auth", "-file", EvoSpeakConfig.DEFAULT_PARAMS, "-p", "llm.api-key=probe-test-key",
                    "-p", "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions",
                    "-p", "llm.proxy=direct", "-p", "evospeak.output-directory=" + checkOutput});
                require(!Files.exists(checkOutput), "Auth-only entry point must not initialize GP or create run files.");
            Path localParams = Files.createTempFile("evospeak-api-", ".local.params");
            try {
                String parent = Paths.get("src/mengxu/algorithm/OnlineEvoSpeak/multipletreegp-dynamicLLMWarmStartMO.params")
                        .toAbsolutePath().toString().replace('\\', '/');
                Files.writeString(localParams, "parent.0 = " + parent + "\nllm.api-key = local-test-key\nprint-params = true\n");
                java.io.ByteArrayOutputStream trace = new java.io.ByteArrayOutputStream();
                java.io.PrintStream previousErrors = System.err;
                EvoSpeakConfig local;
                try (java.io.PrintStream errors = new java.io.PrintStream(trace, true, StandardCharsets.UTF_8.name())) {
                    System.setErr(errors);
                    local = new EvoSpeakConfig(localParams,
                        "llm.provider=openai-compatible", "llm.api=chat-completions",
                        "llm.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/chat");
                } finally {
                    System.setErr(previousErrors);
                }
                require(!trace.toString(StandardCharsets.UTF_8.name()).contains("local-test-key"),
                    "Reading local credentials must not expose them through print-params diagnostics.");
                local.parameters.printState = ParameterDatabase.PS_NONE;
                local.set("print-params", false);
                require(!local.parameters.exists(new Parameter("llm.api-key"), null), "Strip local API keys from the ECJ database.");
                new LlmClient(local.copy(), name -> "environment-test-key").complete("Test", "Test");
                require("Bearer local-test-key".equals(authorization.get()), "Local keys must survive config copying and take precedence.");
                local.set("llm.api-key", "");
                new LlmClient(local, name -> "environment-test-key").complete("Test", "Test");
                require("Bearer environment-test-key".equals(authorization.get()), "An empty local key must fall back to the environment.");
                local.set("llm.api-key", "REPLACE_WITH_YOUR_API_KEY");
                boolean placeholderRejected = false;
                try {
                    new LlmClient(local, name -> null);
                } catch (IllegalArgumentException expected) {
                    placeholderRejected = true;
                }
                require(placeholderRejected, "The editable placeholder must never be sent as a real API key.");
                String unsafeKey = "local-test\u0001-key";
                local.set("llm.api-key", unsafeKey);
                boolean unsafeKeyRejected = false;
                try {
                    new LlmClient(local, name -> null);
                } catch (IllegalArgumentException expected) {
                    unsafeKeyRejected = !expected.getMessage().contains(unsafeKey);
                }
                require(unsafeKeyRejected, "Reject invalid header characters without echoing the credential.");
                for (String malformed : new String[]{"\"local-test-key\"", "'local-test-key'", "Bearer local-test-key"}) {
                    local.set("llm.api-key", malformed);
                    boolean formatRejected = false;
                    try {
                        new LlmClient(local, name -> null);
                    } catch (IllegalArgumentException expected) {
                        formatRejected = expected.getMessage().contains("without quotes or a Bearer prefix")
                                && !expected.getMessage().contains("local-test-key");
                    }
                    require(formatRejected, "Reject quoted tokens and duplicated Bearer prefixes before sending a request.");
                }
            } finally {
                Files.deleteIfExists(localParams);
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
                require(unauthorized.httpFailure(401, "<html>test-key</html>").getMessage().contains("Authentication was rejected"),
                    "Non-JSON errors still need actionable authentication guidance.");
                server.createContext("/invalid-key", exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    byte[] response = new JSONObject().put("error", new JSONObject().put("code", "invalid_api_key")
                        .put("type", "invalid_request_error").put("message", "Incorrect API key: local-test-key"))
                        .toString().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(401, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
                analysis.set("llm.endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/invalid-key");
                analysis.set("llm.api-key", "local-test-key");
                LlmClient invalid = new LlmClient(analysis, name -> "different-environment-key");
                try {
                    invalid.complete("Test", "Test");
                    throw new AssertionError("HTTP 401 must stop generation.");
                } catch (java.io.IOException expected) {
                    String message = expected.getMessage();
                    require(message.contains("invalid_api_key") && message.contains("llm.api-key in the selected params")
                        && message.contains("overrides the environment key") && !message.contains("local-test-key"),
                        "Expose only safe error codes and credential-source guidance, never echoed keys.");
                    require(invalid.requestCount() == 1, "Never retry a rejected credential.");
                }
                String reflectedSecret = new JSONObject().put("error", new JSONObject().put("code", "local-test-key")
                    .put("type", "another-secret").put("message", "local-test-key")).toString();
                require(!invalid.httpFailure(401, reflectedSecret).getMessage().contains("local-test-key")
                    && !invalid.httpFailure(401, reflectedSecret).getMessage().contains("another-secret"),
                    "Unrecognized provider code/type fields must not leak arbitrary response text.");
                require(invalid.httpFailure(407, "local-test-key").getMessage().contains("proxy requires authentication"),
                    "Proxy authentication is different from endpoint authentication.");
                EvoSpeakConfig official = new EvoSpeakConfig(Paths.get(EvoSpeakConfig.DEFAULT_PARAMS));
                String officialFailure = new LlmClient(official, name -> "test-key").httpFailure(401, "{}").getMessage();
                require(officialFailure.contains("official OpenAI API") && officialFailure.contains("DeepSeek"),
                    "Official OpenAI failures must explain that other providers' keys are not interchangeable.");
                analysis.set("llm.api-key", "");
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
        require(run.getFileName().toString().startsWith("job.0-"), "Artifact directories must identify the run seed.");
        require(Files.exists(run.resolve("job.0.out.stat")), "Default statistics must use the original job-ID naming convention.");
        require(config.text("stat.file", "").equals("$out.stat"), "Running must not modify the caller's statistics configuration.");
        require(RulePopulation.read(run.resolve("generated-population.txt")).size() == 2, "Validated warm-start size");
        require(Files.readAllLines(run.resolve("generations.jsonl")).size() == 2, "GP must run after validation.");
        checkTimingFiles(run, run, 0);
        require(Files.exists(run.resolve("best-rules.txt")), "Final interpretable rules must be saved.");
        require(new JSONObject(Files.readString(run.resolve("status.json"))).getString("state").equals("completed"), "Run completion status");
        config.set("seed.0", 7);
        Path explicitStatistics = directory.resolve("job.7.out.stat");
        config.set("stat.file", explicitStatistics);
        Path nextRun = EvoSpeakMain.run(config, null);
        require(Files.exists(explicitStatistics), "An explicitly requested statistics path must be honored.");
        require(nextRun.getFileName().toString().startsWith("job.7-"), "The next run must have its own artifact ID.");
        JSONObject status = new JSONObject(Files.readString(nextRun.resolve("status.json")));
        require(status.getLong("runId") == 7 && status.getString("seed").equals("7"), "Run metadata must match its seed.");
        require(Paths.get(status.getString("statisticsFile")).equals(explicitStatistics), "Record the actual statistics file location.");
        checkTimingFiles(directory, nextRun, 7);
        byte[] previousResults = Files.readAllBytes(explicitStatistics);
        boolean collisionRejected = false;
        try {
            EvoSpeakMain.run(config, null);
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            collisionRejected = true;
        }
        require(collisionRejected && Arrays.equals(previousResults, Files.readAllBytes(explicitStatistics)),
                "Existing run statistics must never be silently overwritten.");
        config.set("seed.0", 8);
        config.set("stat.file", directory.resolve("job.8.out.stat"));
        Path existingTiming = directory.resolve("job.8.time.csv");
        Files.writeString(existingTiming, "existing timing results");
        boolean timingCollisionRejected = false;
        try {
            EvoSpeakMain.run(config, null);
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            timingCollisionRejected = true;
        }
        require(timingCollisionRejected && Files.readString(existingTiming).equals("existing timing results")
            && !Files.exists(directory.resolve("job.8.out.stat")), "Timing collisions must be rejected before starting GP.");
        }

    private static void testGPMainRuns() throws Exception {
        Path directory = Files.createTempDirectory("evospeak-gpmain-test-");
        Path artifacts = directory.resolve("artifacts");
        String[] arguments = {"-file", "src/mengxu/algorithm/OnlineEvoSpeak/smoke.params", "-p", "generations=2",
                "-p", "evospeak.validation.seeds=17001", "-p", "evospeak.output-directory=" + artifacts,
                "-p", "seed.0=99", "-p", "stat.file=" + directory.resolve("unused.out.stat")};
        String[] originalArguments = arguments.clone();
        for (int runId = 31; runId <= 32; runId++) {
            GPMain.runExperiment(arguments, runId, directory);
            require(Files.exists(directory.resolve("job." + runId + ".out.stat")), "GPMain must preserve each run's statistics ID.");
            try (java.util.stream.Stream<Path> entries = Files.list(artifacts)) {
                String prefix = "job." + runId + "-";
                Path run = entries.filter(path -> path.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow();
                JSONObject status = new JSONObject(Files.readString(run.resolve("status.json")));
                require(status.getLong("runId") == runId && status.getString("seed").equals(String.valueOf(runId)),
                        "The launcher must pass its current ID, not a stale override.");
                require(status.getString("state").equals("completed") && Files.exists(run.resolve("generated-population.txt")),
                        "GPMain must run the warm-start validation pipeline before GP.");
                checkTimingFiles(directory, run, runId);
            }
        }
        require(Arrays.equals(arguments, originalArguments), "Batch runs must not mutate shared arguments.");
        require(!Files.exists(directory.resolve("unused.out.stat")), "A stale stat.file must not capture later runs.");
        testGPMainResultCollisions(arguments, directory, artifacts);
        require(Arrays.equals(arguments, originalArguments), "Collision handling must not change caller arguments.");
        GPMain.runExperiment(new String[]{"-file", "src/mengxu/algorithm/OnlineEvoSpeak/smoke.params",
                "-p", "state=" + GPLauncherProbeState.class.getName()}, 33, directory);
        require(Files.readString(directory.resolve("job.33.out.stat")).equals("33"),
                "Ordinary evolution states must still be dispatched through GPRun.");
    }

    private static void testGPMainResultCollisions(String[] arguments, Path directory, Path artifacts) throws Exception {
        String[] suffixes = {".out.stat", ".time.csv", ".timeSumGen.csv"};
        for (int index = 0; index < suffixes.length; index++) {
            int runId = 22 + index;
            String prefix = "job." + runId;
            Path existing = directory.resolve(prefix + suffixes[index]);
            String savedResult = "Previous experiment " + prefix + suffixes[index];
            Files.writeString(existing, savedResult);
            GPMain.runExperiment(arguments, runId, directory);
            require(Files.readString(existing).equals(savedResult), "Existing experiment output must remain untouched.");
            for (String suffix : suffixes) {
                Path oldLocation = directory.resolve(prefix + suffix);
                require(oldLocation.equals(existing) || !Files.exists(oldLocation),
                        "A collided run must not mix new files with previous results.");
            }
            try (java.util.stream.Stream<Path> entries = Files.list(artifacts)) {
                Path run = entries.filter(path -> path.getFileName().toString().startsWith(prefix + "-")).findFirst().orElseThrow();
                JSONObject status = new JSONObject(Files.readString(run.resolve("status.json")));
                require(status.getLong("runId") == runId && status.getString("seed").equals(String.valueOf(runId)),
                        "Automatic result isolation must retain the requested run ID and seed.");
                require(Paths.get(status.getString("statisticsFile")).equals(run.resolve(prefix + ".out.stat")),
                        "The new statistics path must be recorded in the fresh artifact directory.");
                require(status.getString("state").equals("completed") && Files.exists(run.resolve(prefix + ".out.stat")),
                        "The collided run must complete normally in the new directory.");
                checkTimingFiles(run, run, runId);
            }
        }
    }

    public static class GPLauncherProbeState extends EvolutionState {
        @Override
        public void run(int condition) {
            require(condition == C_STARTED_FRESH && runtimeArguments != null && job != null,
                    "GPRun must initialize the ordinary state's run metadata.");
            try {
                Files.writeString(parameters.getFile(new Parameter("stat.file"), null).toPath(),
                        parameters.getString(new Parameter("seed.0"), null));
            } catch (java.io.IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        }
    }

    private static void checkTimingFiles(Path resultDirectory, Path artifactDirectory, long runId) throws Exception {
        String prefix = "job." + runId;
        List<String> times = Files.readAllLines(resultDirectory.resolve(prefix + ".time.csv"));
        List<String> totals = Files.readAllLines(resultDirectory.resolve(prefix + ".timeSumGen.csv"));
        List<String> metrics = Files.readAllLines(artifactDirectory.resolve("generations.jsonl"));
        require(times.get(0).equals("Gen,Time") && totals.get(0).equals("Gen,timeSumGen"),
                "Timing CSV headers must match the original GP output format.");
        require(times.size() == metrics.size() + 1 && totals.size() == times.size(), "Every generation needs one timing row.");
        double elapsed = 0.0;
        for (int index = 0; index < metrics.size(); index++) {
            JSONObject generation = new JSONObject(metrics.get(index));
            String[] time = times.get(index + 1).split(",");
            String[] total = totals.get(index + 1).split(",");
            require(Integer.parseInt(time[0]) == index && Integer.parseInt(total[0]) == index, "Timing generation IDs must be sequential.");
            require(generation.getLong("runId") == runId, "Generation metrics must carry the current run ID.");
            double duration = Double.parseDouble(time[1]);
            require(duration >= 0.0, "Generation times must be nonnegative.");
            elapsed += duration;
            requireClose(generation.getDouble("seconds"), duration, "CSV and JSON generation timing");
            requireClose(elapsed, Double.parseDouble(total[1]), "Cumulative time must start from zero for each run");
            requireClose(elapsed, generation.getDouble("cumulativeSeconds"), "CSV and JSON cumulative timing");
        }
    }

        private static void testOnlinePipelineAndFailureGate() throws Exception {
            AtomicInteger requests = new AtomicInteger();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/generate", exchange -> {
                JSONObject request = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String prompt = request.getJSONArray("messages").getJSONObject(1).getString("content");
                int batch = requests.getAndIncrement();
                JSONArray individuals = new JSONArray();
                if (batch <= 1) {
                    if (batch == 1) {
                        require(prompt.contains("plain ECJ TXT") && prompt.contains("Number of Individuals appears exactly ONCE"),
                                "A rejected wrapper must provide both the format error and a complete indexed template in the next prompt.");
                    }
                    individuals.put(new RulePopulation.Rules("PT", "WIQ").json())
                            .put(new RulePopulation.Rules("(+ PT UNKNOWN)", "WIQ").json());
                } else {
                    require(prompt.contains("Unknown") || prompt.contains("UNKNOWN"), "Rejected rules must be fed back to the LLM.");
                    require(prompt.contains("exactly 1 NEW, DISTINCT pairs") && prompt.endsWith("ROUTING_RULE_0\n"),
                            "The final batch must request and template only the missing individual.");
                    individuals.put(new RulePopulation.Rules("(/ PT W)", "(+ WIQ TRANT)").json());
                }
                String generated = plainPopulationResponse(new JSONObject().put("individuals", individuals), 1);
                if (batch == 0) {
                    generated = "<START>\n" + generated + "<END>\n";
                }
                byte[] response = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", "stop")
                        .put("message", new JSONObject().put("content", generated))))
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
                        "evospeak.batch-size=2", "evospeak.max-batches=3", "evospeak.objective-mode=single",
                        "evospeak.normalization=false", "pop.subpop.0.size=2", "breed.elite.0=1", "generations=2",
                        "evospeak.validation.jobs=100", "evospeak.validation.warmup=10", "evospeak.validation.seeds=17001",
                        "eval.problem.eval-model.sim-models.0.num-jobs=200", "eval.problem.eval-model.sim-models.0.warmup-jobs=20");
                Path run = EvoSpeakMain.run(config, new LlmClient(config, name -> "test-key"));
                require(requests.get() == 3, "A format-rejected batch and an invalid candidate must receive bounded replacement batches.");
                JSONObject validation = new JSONObject(Files.readString(run.resolve("validation-report.json")));
                require(validation.getInt("accepted") == 2 && validation.getJSONArray("checks").length() == 4
                        && validation.getJSONArray("batches").getJSONObject(0).has("formatError"), "Validation audit counts");
                JSONObject generated = new JSONObject(Files.readString(run.resolve("generated-rules.json")));
                require(!generated.getJSONArray("individuals").getJSONObject(1).has("explanation")
                    && generated.getJSONArray("individuals").getJSONObject(1).getInt("generationBatch") == 2,
                    "Accepted rules must retain their source batch, without requiring generated explanation fields.");
                require(generated.getJSONArray("individuals").getJSONObject(1).getString("sequencing").equals("(/ PT W)"),
                    "Validated canonical expressions must stay aligned after replacement batches.");
                require(RulePopulation.read(run.resolve("generated-rules.json")).size() == 2, "Enriched JSON must remain compatible with offline reuse and analysis.");
                String warmStartReport = Files.readString(run.resolve("warm-start-report.md"));
                require(warmStartReport.contains("## Generation Batches") && warmStartReport.contains("## Validated Heuristics")
                    && warmStartReport.contains("Tree 0:") && !warmStartReport.contains("Expected objective trade-off:"),
                    "Warm-start report must record generation and validated rules, without fabricated explanations.");
                require(!warmStartReport.contains("UNKNOWN"), "Rejected candidates must not appear among accepted heuristics.");
                require(Files.exists(run.resolve("reference-heuristics.json")), "Save the reference facts used for generation.");
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

    private static JSONObject twoRuleResponse() {
        return generatedResponse(new JSONArray().put(new RulePopulation.Rules("PT", "WIQ").json())
                .put(new RulePopulation.Rules("(/ PT W)", "(+ WIQ TRANT)").json()));
    }

    private static JSONObject generatedResponse(JSONArray individuals) {
        for (Object item : individuals) {
            ((JSONObject) item).put("explanation", new JSONObject()
                    .put("sequencing", "PT and W affect smaller-score sequencing priorities; interpret signs in context.")
                    .put("routing", "WIQ and optional TRANT reflect candidate-machine queue work and transport time.")
                    .put("objectiveTradeOff", "The expected trade-off depends on configured weights and needs independent evaluation.")
                    .put("referenceIds", new JSONArray().put(1)));
        }
        return new JSONObject().put("individuals", individuals).put("insights", new JSONArray().put(new JSONObject()
                .put("observation", "MWT is constant across one machine queue, so (- MWT W) prioritizes larger W for finite values.")
                .put("referenceIds", new JSONArray().put(1))));
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