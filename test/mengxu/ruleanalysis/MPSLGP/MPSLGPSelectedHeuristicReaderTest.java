package mengxu.ruleanalysis.MPSLGP;

import ec.gp.GPNode;
import mengxu.ruleanalysis.MPSLGP.ParetoSetLearningMultipleTreeResultFileReader.SelectedTaskHeuristic;
import yimei.jss.gp.data.DoubleData;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** Dependency-free regression tests; --actual [IGD-directory] also reads all ten completed paper runs. */
public final class MPSLGPSelectedHeuristicReaderTest {
    private static final String TOP = "Top N individuals by MPSLGP Task:\n";
    private static final String FRONTS = " PARETO FRONTS BY MPSLGP TASK\n";
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length > 2 || (args.length > 0 && !"--actual".equals(args[0]))) {
            throw new IllegalArgumentException("Usage: MPSLGPSelectedHeuristicReaderTest [--actual [IGD-directory]]");
        }
        Path directory = Files.createTempDirectory("mpslgp-selected-reader-");
        Path file = directory.resolve("fixture.stat");
        try {
            testSelection(file);
            testExpressionSemantics(file);
            testMissingAndAmbiguousSections(file);
            testTaskLabelsAndCounts(file);
            testMalformedIndividuals(file);
            testInvalidInputs(file, directory.resolve("missing.stat"));
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
        if (args.length > 0) {
            Path root = args.length == 2 ? Paths.get(args[1]) : Paths.get("src", "mengxu",
                    "ruleanalysis", "MPSLGP", "paper-runs", "full", "IGD");
            testActualSamples(root);
        }
        System.out.println("MPSLGP selected heuristic reader tests passed (" + checks + " checks).");
    }

    private static String individual(String objectives, String sequencing, String routing) {
        return "Evaluated: true\nFitness: [" + objectives + "]\nTree 0:\n " + sequencing
                + "\nTree 1:\n " + routing + "\n";
    }

    private static String task(int task, int count, String individuals) {
        return "\nMPSLGP Task " + task + " Top " + count + " individuals:\n" + individuals;
    }

    private static String generationBest() {
        StringBuilder best = new StringBuilder("Best Individual by MPSLGP Task:\n");
        for (int task = 0; task < 3; task++) {
            best.append("MPSLGP Task ").append(task).append(":\n")
                    .append(individual("-10 -20", "PT", "WIQ"));
        }
        return best.toString();
    }

    private static String finalTasks() {
        return task(0, 2, individual("100 200", "(+ (Max PT Pone) (Min WIQ Ptwo))", "(- WIQ PT)")
                + individual("1 2", "PT", "WIQ"))
                + task(1, 2, individual("300 400", "(+ Pone Ptwo)", "(/ 0 0)")
                + individual("0 0", "NPT", "NIQ"))
                + task(2, 1, individual("500 600", "(if Pone Ptwo PT)", "(* WIQ 2)"));
    }

    private static String complete(String tasks) {
        return "Generation: 49\n" + TOP + tasks + "\n" + FRONTS;
    }

    private static String single() {
        return complete(task(0, 1, individual("1 2", "(+ PT Pone)", "(- WIQ Ptwo)")));
    }

    private static void write(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static SelectedTaskHeuristic read(Path file, int task, int generation) throws IOException {
        return ParetoSetLearningMultipleTreeResultFileReader.readFinalTaskHeuristic(file, task, generation);
    }

    private static void testSelection(Path file) throws Exception {
        String earlier = "Generation: 0\n" + TOP
                + task(0, 1, individual("9 9", "PT", "NPT")) + FRONTS;
        String paretoAndTestDecoys = "\nPareto Front for MPSLGP Task 1\n"
                + individual("-100 -200", "NIQ", "WIQ")
                + "Test Fitness: [-999 -999]\n";
        write(file, earlier + "Generation: 49\n" + generationBest() + TOP + finalTasks()
                + FRONTS + paretoAndTestDecoys);
        String[] sequencing = {"(+ (Max PT Pone) (Min WIQ Ptwo))", "(+ Pone Ptwo)", "(if Pone Ptwo PT)"};
        String[] routing = {"(- WIQ PT)", "(/ 0 0)", "(* WIQ 2)"};
        for (int task = 0; task < 3; task++) {
            SelectedTaskHeuristic result = read(file, task, 49);
            require(result.getTaskIndex() == task && result.getGeneration() == 49, "Task/generation identity");
            assertRule(result.getSequencingRule(), RuleType.SEQUENCING, sequencing[task]);
            assertRule(result.getRoutingRule(), RuleType.ROUTING, routing[task]);
            double[] expected = {100 + 200 * task, 200 + 200 * task};
            require(Arrays.equals(expected, result.getTrainingObjectives()), "First training-ranked objectives");
            double[] exposed = result.getTrainingObjectives();
            exposed[0] = -999;
            require(Arrays.equals(expected, result.getTrainingObjectives()), "Defensive objective copy");
        }
        expectIOException(file, 3, 49, "Missing requested task");
        expectIOException(file, 0, 48, "Expected final generation");
        expectIOException(file, 0, 50, "Expected final generation");

        write(file, "Generation: 49\n" + TOP + finalTasks());
        require(read(file, 2, 49).getTrainingObjectives()[0] == 500, "Complete section can end at EOF");
        write(file, complete(task(1, 1, individual("3 4", "Pone", "Ptwo"))
                + task(0, 1, individual("1 2", "PT", "WIQ"))).replace("\n", "\r\n"));
        require(read(file, 1, 49).getTrainingObjectives()[0] == 3, "Task labels, not physical block order");
        write(file, single().replace("Generation: 49", "Generation: 0"));
        require(read(file, 0, 0).getGeneration() == 0, "Generation zero is valid");
    }

    private static void testExpressionSemantics(Path file) throws Exception {
        String sequencing = "(+  (/ 0 0)\t(- Pone Ptwo))";
        write(file, complete(task(0, 1, individual("1e2 -2.5E-1", sequencing, "(if 1 (Max 2 3) (Min 4 5))"))));
        SelectedTaskHeuristic selected = read(file, 0, 49);
        assertRule(selected.getSequencingRule(), RuleType.SEQUENCING, sequencing);
        require(Arrays.equals(new double[]{100, -0.25}, selected.getTrainingObjectives()), "Scientific objectives");
        GPNode root = selected.getSequencingRule().getGPTree().child;
        require("+".equals(root.toString()) && root.children.length == 2, "No algebraic simplification");
        DoubleData data = new DoubleData();
        root.children[0].eval(null, 0, data, null, null, null);
        require(data.value == 1, "Protected zero division retains printed GP semantics");
        assertPreference(root.children[1].children[0], JobShopAttribute.Preference_One);
        assertPreference(root.children[1].children[1], JobShopAttribute.Preference_Two);
        selected.getRoutingRule().getGPTree().child.eval(null, 0, data, null, null, null);
        require(data.value == 3, "if/Max/Min retain parser semantics");
        require(root.parent == selected.getSequencingRule().getGPTree(), "Tree root parent");
        require(root.children[1].parent == root && root.children[1].argposition == 1, "Child parent/position");
    }

    private static void testMissingAndAmbiguousSections(Path file) throws Exception {
        String valid = single();
        String[] invalid = {
                "",
                valid.replace("Generation: 49\n", ""),
                valid.replace("Generation: 49", "Generation: invalid"),
                valid.replace("Generation: 49", "Generation: -1"),
                valid.replace("Generation: 49", "Generation: 2147483648"),
                "Generation: 49\n" + generationBest() + FRONTS,
                "Generation: 49\n" + TOP + FRONTS,
                valid.replace(TOP, "Top N individuals:\n"),
                valid.replace(TOP, "Top N individuals by MPSLGP Task\n"),
                valid + TOP + task(0, 1, individual("3 4", "PT", "WIQ")),
                valid + "Generation: 50\n" + generationBest(),
                valid + "Generation: 48\n",
                valid + "Generation: 49\n",
                valid.replace(TOP, FRONTS + TOP),
                valid + task(0, 1, individual("3 4", "PT", "WIQ")),
                valid.replace(FRONTS, "PARETO FRONTS\n")
        };
        for (String text : invalid) {
            write(file, text);
            expectIOException(file, 0, 49, null);
        }
        write(file, valid.replace("Generation: 49", "Generation: 40")
                + "Generation: 49\n" + generationBest() + FRONTS);
        expectIOException(file, 0, 49, "Missing Top N section");
        write(file, "Generation: 49\n" + generationBest() + TOP
                + task(0, 1, individual("1 2", "PT", "WIQ")) + FRONTS);
        expectIOException(file, 0, 49, "do not match");
    }

    private static void testTaskLabelsAndCounts(Path file) throws Exception {
        String valid = single();
        String[] badHeaders = {
                "MPSLGP Task -1 Top 1 individuals:", "MPSLGP Task x Top 1 individuals:",
                "MPSLGP Task 2147483648 Top 1 individuals:", "MPSLGP Task 1 Top 1 individuals:",
                "MPSLGP Task 0 Top 0 individuals:", "MPSLGP Task 0 Top -1 individuals:",
                "MPSLGP Task 0 Top x individuals:", "MPSLGP Task 0 Top 2147483648 individuals:",
                "MPSLGP Task 0 Top 2 individuals:", "MPSLGP Task 0:",
                "MPSLGP Task 0 Top 1 individuals: extra"
        };
        for (String header : badHeaders) {
            write(file, valid.replace("MPSLGP Task 0 Top 1 individuals:", header));
            expectIOException(file, 0, 49, null);
        }
        String[] badTasks = {
                task(0, 1, individual("1 2", "PT", "WIQ")) + task(0, 1, individual("3 4", "Pone", "Ptwo")),
                task(0, 1, individual("1 2", "PT", "WIQ")) + task(2, 1, individual("3 4", "Pone", "Ptwo")),
                task(0, 1, individual("1 2", "PT", "WIQ") + individual("3 4", "Pone", "Ptwo")),
                task(0, 2, individual("1 2", "PT", "WIQ")) + task(1, 1, individual("3 4", "Pone", "Ptwo")),
                task(0, 1, individual("1 2", "PT", "WIQ")) + task(1, 0, ""),
                task(0, 1, individual("1 2", "PT", "WIQ")) + task(1, 1, individual("NaN 4", "Pone", "Ptwo")),
                task(0, 2, individual("1 2", "PT", "WIQ") + individual("3 4", "(+ Pone)", "Ptwo"))
        };
        for (String tasks : badTasks) {
            write(file, complete(tasks));
            expectIOException(file, 0, 49, null);
        }
        write(file, complete(finalTasks()).replace("MPSLGP Task 1 Top 2", "MPSLGP Task 0 Top 2"));
        expectIOException(file, 2, 49, "Duplicate task");
        for (String best : new String[]{generationBest().replace("MPSLGP Task 1:", "MPSLGP Task 0:"),
                generationBest().replace("MPSLGP Task 1:", "MPSLGP Task bad:")}) {
            write(file, "Generation: 49\n" + best + TOP + finalTasks() + FRONTS);
            expectIOException(file, 0, 49, "final generation task label");
        }
    }

    private static void testMalformedIndividuals(Path file) throws Exception {
        String valid = single();
        String[][] replacements = {
                {"Evaluated: true", "Evaluated: false"}, {"Evaluated: true", "Evaluated: TRUE"},
                {"Evaluated: true\n", ""}, {"Fitness: [1 2]", "Fitness: []"},
                {"Fitness: [1 2]", "Fitness: [1]"}, {"Fitness: [1 2]", "Fitness: [1 2 3]"},
                {"Fitness: [1 2]", "Fitness: [NaN 2]"}, {"Fitness: [1 2]", "Fitness: [1 Infinity]"},
                {"Fitness: [1 2]", "Fitness: [-Infinity 2]"}, {"Fitness: [1 2]", "Fitness: [1e999 2]"},
                {"Fitness: [1 2]", "Fitness: [one 2]"}, {"Fitness: [1 2]", "Fitness: [1, 2]"},
                {"Fitness: [1 2]", "Fitness: [1 2] extra"}, {"Fitness: [1 2]", "Fitness: [1 2"},
                {"Fitness: [1 2]\n", ""}, {"Tree 0:", "Tree 1:"}, {"Tree 1:", "Tree 0:"},
                {"Tree 0:\n", ""}, {"Tree 1:\n", ""}, {" (+ PT Pone)\n", ""},
                {" (- WIQ Ptwo)\n", ""}, {FRONTS, "Tree 2:\nPT\n" + FRONTS},
                {"Tree 1:\n", "Rank: 0\nTree 1:\n"}
        };
        for (String[] replacement : replacements) {
            write(file, valid.replace(replacement[0], replacement[1]));
            expectIOException(file, 0, 49, null);
        }
        String[] badExpressions = {
                "(", ")", "()", "(+ PT)", "(+ PT Pone WIQ)", "(+ PT (* WIQ Pone)",
                "(+ PT Pone))", "(+ PT Pone) WIQ", "PT WIQ", "(if PT WIQ)",
                "(if PT WIQ Pone Ptwo)", "(unknown PT WIQ)", "UNKNOWN", "(+ PT UNKNOWN)",
                "NaN", "Infinity", "1e999", "0x10", "(+ PT ())", "(PT)"
        };
        for (String expression : badExpressions) {
            for (String original : new String[]{"(+ PT Pone)", "(- WIQ Ptwo)"}) {
                write(file, valid.replace(original, expression));
                expectIOException(file, 0, 49, null);
            }
        }
        List<String> lines = Arrays.asList(valid.split("\n"));
        for (int end = 0; end < lines.size(); end++) {
            String truncated = String.join("\n", lines.subList(0, end));
            if (!truncated.contains("(- WIQ Ptwo)")) {
                write(file, truncated);
                expectIOException(file, 0, 49, null);
            }
        }
    }

    private static void testInvalidInputs(Path file, Path missing) throws Exception {
        expectIOException(missing, 0, 49, null);
        expectIOException(file.getParent(), 0, 49, null);
        write(file, single());
        for (int[] indices : new int[][]{{-1, 49}, {0, -1}}) {
            try {
                read(file, indices[0], indices[1]);
                throw new AssertionError("Negative indices must be rejected");
            } catch (IllegalArgumentException expected) {
                checks++;
            }
        }
        try {
            read(null, 0, 49);
            throw new AssertionError("Null path must be rejected");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void testActualSamples(Path root) throws Exception {
        int samples = 0;
        for (String scenario : new String[]{"R1", "H3"}) {
            int taskCount = "R1".equals(scenario) ? 2 : 3;
            for (int seed = 0; seed < 5; seed++) {
                Path file = root.resolve(scenario).resolve("run0" + seed).resolve("attempt-0001")
                        .resolve("job." + seed + ".out.stat");
                byte[] before = Files.readAllBytes(file);
                String text = new String(before, StandardCharsets.UTF_8).replace("\r\n", "\n");
                int top = text.lastIndexOf(TOP);
                int fronts = text.indexOf(FRONTS.trim(), top);
                require(top >= 0 && fronts > top, "Actual final Top N section: " + file);
                String section = text.substring(top, fronts);
                for (int task = 0; task < taskCount; task++) {
                    String header = "MPSLGP Task " + task + " Top 1 individuals:\n";
                    int start = section.indexOf(header);
                    require(start >= 0 && start == section.lastIndexOf(header), "Actual unique task header");
                    String[] record = section.substring(start + header.length()).split("\n");
                    require("Evaluated: true".equals(record[0]), "Actual evaluated flag");
                    require("Tree 0:".equals(record[2]) && "Tree 1:".equals(record[4]), "Actual tree order");
                    String[] objectives = record[1].substring("Fitness: [".length(), record[1].length() - 1).split(" ");
                    SelectedTaskHeuristic selected = read(file, task, 49);
                    require(selected.getTaskIndex() == task && selected.getGeneration() == 49, "Actual task/generation");
                    require(Arrays.equals(new double[]{Double.parseDouble(objectives[0]), Double.parseDouble(objectives[1])},
                            selected.getTrainingObjectives()), "Actual printed objective values");
                    assertRule(selected.getSequencingRule(), RuleType.SEQUENCING, record[3].trim());
                    assertRule(selected.getRoutingRule(), RuleType.ROUTING, record[5].trim());
                    require(selected.getSequencingRule().getGPTree().child.makeLispTree().equals(record[3].trim()),
                            "Actual sequencing tree structure unchanged");
                    require(selected.getRoutingRule().getGPTree().child.makeLispTree().equals(record[5].trim()),
                            "Actual routing tree structure unchanged");
                    if ("H3".equals(scenario) && seed == 0) {
                        double[][] known = {{1356.8388324916596, 2357.119104141253},
                                {1601.0266853311477, 4357.065992146701}, {4843.293476424296, 981.6164996046282}};
                        require(Arrays.equals(known[task], selected.getTrainingObjectives()), "Known H3 run00 objectives");
                    }
                }
                expectIOException(file, taskCount, 49, "Missing requested task");
                require(Arrays.equals(before, Files.readAllBytes(file)), "Actual artifact remains byte-for-byte unchanged");
                samples++;
            }
        }
        System.out.println("Read-only actual samples passed: " + samples + " runs, 25 task heuristics.");
    }

    private static void assertRule(GPRule rule, RuleType type, String expression) {
        require(rule.getType() == type, "Rule role " + type);
        require(expression.equals(rule.getLispString()), "Printed expression preserved: " + expression);
        require(rule.getGPTree() != null && rule.getGPTree().child != null, "Complete parsed GP tree");
    }

    private static void assertPreference(GPNode node, JobShopAttribute expected) {
        require(node instanceof AttributeGPNode
                && ((AttributeGPNode) node).getJobShopAttribute() == expected, "Preference terminal " + expected);
    }

    private static void expectIOException(Path file, int task, int generation, String message) throws Exception {
        try {
            read(file, task, generation);
            throw new AssertionError("Expected IOException for task " + task + ", generation " + generation + ": " + file);
        } catch (IOException expected) {
            require(expected.getMessage() != null && !expected.getMessage().isEmpty(), "Explicit read error");
            if (message != null) {
                require(expected.getMessage().contains(message), "Expected error '" + message + "', got " + expected);
            }
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
