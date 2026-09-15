package mengxu.algorithm.OnlineEvoSpeak;

import ec.EvolutionState;
import ec.Individual;
import ec.Subpopulation;
import ec.gp.GPIndividual;
import ec.gp.GPInitializer;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.util.DecodeReturn;
import ec.util.Output;
import org.json.JSONArray;
import org.json.JSONObject;
import yimei.jss.gp.terminal.TerminalERCUniform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RulePopulation {
    private static final Pattern COUNT = Pattern.compile("(?m)^Number of Individuals: i(\\d+)\\|\\h*$");
    private static final Pattern INDIVIDUAL = Pattern.compile("(?m)^Individual Number: i(\\d+)\\|\\h*$");
    private static final Pattern TREE = Pattern.compile("(?m)^Tree (\\d+):\\h*$");
        private static final Pattern INSIGHT = Pattern.compile("(?s)^-\\h+(.+?)\\h+\\(reference IDs:\\h*(\\[[^\\r\\n]*\\])\\)\\h*$");
        private static final Pattern ANNOTATED_RULE = Pattern.compile("(?s)\\s*Evaluated:\\h*F\\h*\\n"
            + "\\s*Fitness:\\h*\\[[^\\r\\n]*\\]\\h*\\n\\s*Tree 0:\\h*\\n(.*?)"
            + "\\n\\h*Tree 1:\\h*\\n(.*?)\\nSequencing:\\h*(.*?)\\nRouting:\\h*(.*?)"
            + "\\nExpected objective (?:trade-off|effect):\\h*(.*?)\\nReference IDs:\\h*(\\[[^\\r\\n]*\\])\\s*");

    private RulePopulation() { }

    public static final class Rules {
        public final String sequencing;
        public final String routing;

        public Rules(String sequencing, String routing) {
            this.sequencing = sequencing.trim();
            this.routing = routing.trim();
        }

        public JSONObject json() {
            return new JSONObject().put("sequencing", sequencing).put("routing", routing);
        }
    }

    public static List<Rules> fromJson(String response) {
        return fromJson(jsonObject(response));
    }

    static List<Rules> fromJson(JSONObject response) {
        JSONArray values = response.getJSONArray("individuals");
        if (values.length() == 0 || values.length() > 1000) {
            throw new IllegalArgumentException("LLM response must contain between 1 and 1000 individuals.");
        }
        List<Rules> rules = new ArrayList<>();
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.getJSONObject(index);
            rules.add(new Rules(value.getString("sequencing"), value.getString("routing")));
        }
        return rules;
    }

    public static JSONObject jsonObject(String response) {
        String content = response.trim();
        if (content.startsWith("```")) {
            int start = content.indexOf('\n');
            int end = content.lastIndexOf("```");
            if (start < 0 || end <= start || !content.substring(end + 3).trim().isEmpty()) {
                throw new IllegalArgumentException("Incomplete JSON code block.");
            }
            content = content.substring(start + 1, end).trim();
        }
        return new JSONObject(content);
    }

    public static JSONObject generationObject(String response) {
        String text = response.replace("\r\n", "\n").trim();
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1).trim();
        }
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start < 0 || end <= start || !text.substring(end + 3).trim().isEmpty()) {
                throw new IllegalArgumentException("Incomplete generation code block.");
            }
            text = text.substring(start + 1, end).trim();
        }
        if (text.startsWith("{")) {
            return jsonObject(text);
        }
        String[] start = text.split("(?m)^<START>\\h*$", -1);
        if (start.length != 2) {
            throw new IllegalArgumentException("Return exactly one <START>/<END> block for the new heuristics.");
        }
        String[] end = start[1].split("(?m)^<END>\\h*$", -1);
        if (end.length != 2 || !end[1].trim().isEmpty()) {
            throw new IllegalArgumentException("Missing <END> or unexpected text after the new heuristics.");
        }
        Matcher headings = Pattern.compile("(?s)^## Insights Extraction\\h*\\n(.*?)\\n## New Heuristics\\h*$")
                .matcher(start[0].trim());
        if (!headings.matches()) {
            throw new IllegalArgumentException("Use the exact headings ## Insights Extraction and ## New Heuristics.");
        }
        JSONArray insights = new JSONArray();
        for (String bullet : headings.group(1).trim().split("\\n(?=-\\h+)")) {
            Matcher insight = INSIGHT.matcher(bullet.trim());
            if (!insight.matches()) {
                throw new IllegalArgumentException("Each insight must be a bullet ending with (reference IDs: [integer IDs]).");
            }
            insights.put(new JSONObject().put("observation", insight.group(1).trim())
                    .put("referenceIds", new JSONArray(insight.group(2))));
        }
        String population = end[0].trim();
        Matcher count = COUNT.matcher(population);
        if (!count.find() || count.start() != 0) {
            throw new IllegalArgumentException("Missing ECJ Number of Individuals header in the generated block.");
        }
        int expected = Integer.parseInt(count.group(1));
        if (expected < 1 || expected > 1000) {
            throw new IllegalArgumentException("Generated block must contain between 1 and 1000 individuals.");
        }
        List<Integer> starts = new ArrayList<>();
        List<Integer> bodies = new ArrayList<>();
        Matcher records = INDIVIDUAL.matcher(population);
        while (records.find()) {
            if (Integer.parseInt(records.group(1)) != starts.size()) {
                throw new IllegalArgumentException("Generated individual numbers must be contiguous and start at zero.");
            }
            starts.add(records.start());
            bodies.add(records.end());
        }
        if (starts.size() != expected || !population.substring(count.end(), starts.get(0)).trim().isEmpty()) {
            throw new IllegalArgumentException("Generated population count or header does not match its records.");
        }
        JSONArray individuals = new JSONArray();
        for (int index = 0; index < starts.size(); index++) {
            int finish = index + 1 < starts.size() ? starts.get(index + 1) : population.length();
            Matcher record = ANNOTATED_RULE.matcher(population.substring(bodies.get(index), finish));
            if (!record.matches()) {
                throw new IllegalArgumentException("Individual " + index + " must have Evaluated: F, Fitness, Tree 0, Tree 1, "
                    + "Sequencing, Routing, Expected objective effect (or trade-off) and Reference IDs fields in that order.");
            }
            JSONObject explanation = new JSONObject().put("sequencing", record.group(3).trim())
                    .put("routing", record.group(4).trim()).put("objectiveTradeOff", record.group(5).trim())
                    .put("referenceIds", new JSONArray(record.group(6)));
            individuals.put(new Rules(record.group(1), record.group(2)).json().put("explanation", explanation));
        }
        return new JSONObject().put("insights", insights).put("individuals", individuals);
    }

    public static List<Rules> read(Path file) throws IOException {
        if (Files.size(file) > 10_000_000) {
            throw new IOException("Population file exceeds the 10 MB safety limit.");
        }
        String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (text.trim().startsWith("{")) {
            return fromJson(text);
        }
        if (text.contains("<START>")) {
            return fromJson(generationObject(text));
        }
        Matcher count = COUNT.matcher(text);
        if (!count.find()) {
            throw new IOException("Missing ECJ Number of Individuals header.");
        }
        int expected = Integer.parseInt(count.group(1));
        Matcher entries = INDIVIDUAL.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (entries.find()) {
            if (Integer.parseInt(entries.group(1)) != starts.size()) {
                throw new IOException("Individual numbers must be contiguous and start at zero.");
            }
            starts.add(entries.end());
        }
        if (expected != starts.size() || expected == 0) {
            throw new IOException("Declared population size does not match the number of records.");
        }
        List<Rules> rules = new ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            int end = index + 1 < starts.size() ? text.lastIndexOf("Individual Number:", starts.get(index + 1)) : text.length();
            String entry = text.substring(starts.get(index), end);
            Matcher trees = TREE.matcher(entry);
            if (!trees.find() || !trees.group(1).equals("0")) {
                throw new IOException("Individual " + index + " has no sequencing tree.");
            }
            int sequencingStart = trees.end();
            if (!trees.find() || !trees.group(1).equals("1")) {
                throw new IOException("Individual " + index + " has no routing tree.");
            }
            int sequencingEnd = trees.start();
            int routingStart = trees.end();
            if (trees.find()) {
                throw new IOException("Individual " + index + " must contain exactly two trees.");
            }
            rules.add(new Rules(entry.substring(sequencingStart, sequencingEnd), entry.substring(routingStart)));
        }
        return rules;
    }

    public static GPIndividual parse(EvolutionState state, Rules rules, int maxDepth, int maxNodes) {
        GPIndividual prototype = (GPIndividual) state.population.subpops[0].species.i_prototype;
        GPIndividual individual = prototype.lightClone();
        individual.species = state.population.subpops[0].species;
        individual.fitness = (ec.Fitness) individual.species.f_prototype.clone();
        String[] expressions = {rules.sequencing, rules.routing};
        Output originalOutput = state.output;
        Output parsingOutput = new Output(true);
        parsingOutput.setThrowsErrors(true);
        try {
            state.output = parsingOutput;
            for (int index = 0; index < expressions.length; index++) {
                String expression = expressions[index];
                checkBounds(expression, maxDepth, maxNodes);
                GPTree tree = individual.trees[index];
                GPInitializer initializer = (GPInitializer) state.initializer;
                DecodeReturn input = new DecodeReturn(expression);
                tree.child = TerminalERCUniform.readRootedTree(1, input, tree.constraints(initializer).treetype,
                        tree.constraints(initializer).functionset, tree, 0, state);
                if (!expression.substring(input.pos).trim().isEmpty()) {
                    throw new IllegalArgumentException("Trailing tokens after Tree " + index + ".");
                }
                if (tree.child.depth() > maxDepth || tree.child.numNodes(GPNode.NODESEARCH_ALL) > maxNodes) {
                    throw new IllegalArgumentException("Tree " + index + " exceeds configured depth or node count.");
                }
                tree.owner = individual;
            }
            individual.evaluated = false;
            return individual;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid GP rule: " + error.getMessage(), error);
        } finally {
            state.output = originalOutput;
            parsingOutput.close();
        }
    }

    private static void checkBounds(String expression, int maxDepth, int maxNodes) {
        if (expression.isEmpty() || expression.length() > Math.min(50000L, (long) maxNodes * 32)) {
            throw new IllegalArgumentException("Empty or oversized expression.");
        }
        int nesting = 0;
        for (int index = 0; index < expression.length(); index++) {
            char character = expression.charAt(index);
            if (character == '(' && ++nesting > maxDepth) {
                throw new IllegalArgumentException("Expression nesting exceeds the depth limit.");
            }
            if (character == ')' && --nesting < 0) {
                throw new IllegalArgumentException("Unexpected closing parenthesis.");
            }
        }
        if (nesting != 0) {
            throw new IllegalArgumentException("Unbalanced parentheses.");
        }
    }

    public static Rules rules(GPIndividual individual) {
        return new Rules(individual.trees[0].child.makeLispTree(), individual.trees[1].child.makeLispTree());
    }

    public static void write(Path file, EvolutionState state, List<? extends Individual> individuals) throws IOException {
        Subpopulation population = (Subpopulation) state.population.subpops[0].emptyClone();
        population.individuals = individuals.toArray(new Individual[0]);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            population.printSubpopulation(state, writer);
            if (writer.checkError()) {
                throw new IOException("Failed to write population: " + file);
            }
        }
    }
}