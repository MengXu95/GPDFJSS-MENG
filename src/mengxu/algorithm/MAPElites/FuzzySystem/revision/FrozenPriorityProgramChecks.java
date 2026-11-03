package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import ec.EvolutionState;
import ec.Problem;
import ec.gp.ADFStack;
import ec.gp.GPData;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;
import yimei.jss.gp.data.DoubleData;
import yimei.jss.gp.terminal.ConstantTerminal;
import yimei.jss.rule.RuleType;

/** Standalone, deterministic differential checks; no archives, simulators or file output. */
public final class FrozenPriorityProgramChecks {
    private FrozenPriorityProgramChecks() { }

    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: FrozenPriorityProgramChecks");
        }
        runChecks();
        System.out.println("FROZEN PRIORITY PROGRAM CHECKS PASSED");
    }

    public static void runChecks() {
        checkKnownTrees();
        checkRandomNativeTrees();
        checkSignedZerosAndDivision();
        checkFiniteArithmetic();
        checkProtocolErrorsAndOrdering();
        checkFrozenSnapshot();
    }

    private static void checkKnownTrees() {
        List<SensitivityProbeSet.Probe> probes = randomProbes(78123L, 7);
        for (String expression : Arrays.asList(
                "7", "-0.0", "PT", "(+ PT PT)", "(- PT PT)",
                "(+ PT WIQ)", "(* (+ PT WIQ) (- W NPT))", "(/ PT (+ 1 (* W W)))",
                "(Max (Min PT WIQ) (+ (* rDD SL) (/ OWT WKR)))",
                "(+ (* PT PT) (+ PT WIQ))",
                "(/ (Max (* (- PT WIQ) W) NPT) (+ 1 (* NOR NOR)))",
                "(Min (Max TRANT TIS) (+ NIQ MWT))",
                "(+ (* 1e15 PT) (+ WIQ (* 1e-7 W)))")) {
            compareTree(parse(expression), probes, expression, true);
        }
        for (String terminal : SensitivityProbeSet.supportedTerminals()) {
            compareTree(parse(terminal), probes, terminal, true);
        }
        compareTree(node("mAx", node("PT"), node("WIQ")), probes, "mixed-case node name", false);
        compareTree(node("+", node("0x1.0p-3"), node("PT")), probes, "hex numeric node name", false);
        compareTree(node("+", node("2.0d"), node("PT")), probes, "numeric suffix node name", false);
        CheckNode nullChildren = node("-0.0");
        nullChildren.children = null;
        sameBits(-0.0, FrozenPriorityProgram.compile(nullChildren).evaluate(Collections.emptyMap()),
                "null child array is a leaf");

        SensitivityProbeSet.Probe sample = probe("repeated", "PT", 2.0);
        sameBits(2.0, FrozenPriorityProgram.compile(parse("(+ PT PT)"))
                .sensitivity(Collections.singletonList(sample), 0.125), "unique repeated-terminal derivative");
        List<SensitivityProbeSet.Probe> ordered = Arrays.asList(
                probe("large", "PT", 1.0e15), probe("small", "PT", 0.125),
                probe("tiny", "PT", 1.0e-15), probe("medium", "PT", 1.0e6),
                probe("negative", "PT", -7.5));
        compareTree(parse("(* PT PT)"), ordered, "online mean in saved order", true);
        List<SensitivityProbeSet.Probe> reversed = new ArrayList<>(ordered);
        Collections.reverse(reversed);
        compareTree(parse("(* PT PT)"), reversed, "online mean in reversed saved order", true);
    }

    private static void checkRandomNativeTrees() {
        Random random = new Random(0x46524F5A454EL);
        List<SensitivityProbeSet.Probe> probes = randomProbes(0x50524F424553L, 8);
        for (int i = 0; i < 160; i++) {
            String expression = randomExpression(random, 3 + random.nextInt(3));
            compareTree(parse(expression), probes, "random native tree " + i + ": " + expression, true);
        }
    }

    private static void checkSignedZerosAndDivision() {
        double[][] operands = {{-2, 2}, {2, -2}, {2, 2}, {0.0, -0.0}, {-0.0, 0.0},
                {0.0, 0.0}, {-0.0, -0.0}};
        for (String operator : Arrays.asList("Max", "Min", "max", "mIn")) {
            for (double[] pair : operands) {
                GPNode tree = parse("(" + operator + " " + Double.toString(pair[0])
                        + " " + Double.toString(pair[1]) + ")");
                FrozenPriorityProgram compiled = FrozenPriorityProgram.compile(tree);
                double expected = operator.equalsIgnoreCase("max")
                        ? (pair[0] > pair[1] ? pair[0] : pair[1])
                        : (pair[0] < pair[1] ? pair[0] : pair[1]);
                sameBits(expected, compiled.evaluate(Collections.emptyMap()), "right operand on native ties");
                sameBits(nativeValue(tree, Collections.emptyMap()), compiled.evaluate(Collections.emptyMap()),
                        "native signed-zero Max/Min");
            }
        }
        for (String expression : Arrays.asList(
                "(/ 2 0.0)", "(/ -2 0.0)", "(/ 0.0 0.0)", "(/ -0.0 0.0)",
                "(/ -0.0 1)", "(/ 0.0 -1)", "(/ 2 (Min -0.0 0.0))",
                "(/ 2 -0.0)", "(/ 0.0 -0.0)", "(/ -2 -0.0)",
                "(/ 2 (Max 0.0 -0.0))", "(/ 0 (Max 0.0 -0.0))",
                "(/ 2 (* -1 (- 2 2)))", "(/ 2 (Min (* (- 2 2) -1) (- 2 2)))")) {
            GPNode tree = parse(expression);
            FrozenPriorityProgram compiled = FrozenPriorityProgram.compile(tree);
            Outcome expected = compare(() -> PriorityExpression.evaluate(tree, Collections.emptyMap()),
                    () -> compiled.evaluate(Collections.emptyMap()), expression);
            double nativeOutput = nativeValue(tree, Collections.emptyMap());
            if (expected.failure == null) {
                sameBits(nativeOutput, expected.value, "native protected division: " + expression);
            } else {
                require(expected.failure.getClass() == CandidateMetricException.class
                        && !Double.isFinite(nativeOutput), "negative zero is not protected: " + expression);
            }
            compare(() -> new OutputSensitivityCalculator(0.125).calculateTree(tree,
                            Collections.singletonList(probe("constant-zero", "PT", 1.0))),
                    () -> compiled.sensitivity(Collections.singletonList(probe("constant-zero", "PT", 1.0)), 0.125),
                    "constant expression still evaluated: " + expression);
        }
        for (double original : new double[]{0.0, -0.0}) {
            List<SensitivityProbeSet.Probe> probes = Collections.singletonList(probe("zero", "PT", original));
            FrozenPriorityProgram compiled = FrozenPriorityProgram.compile(parse("PT"));
            sameBits(1.0, compiled.sensitivity(probes, Double.MIN_VALUE), "representable subnormal delta");
            sameBits(1.0, compiled.sensitivity(probes, Double.MAX_VALUE), "representable maximum delta");
        }
    }

    private static void checkFiniteArithmetic() {
        List<SensitivityProbeSet.Probe> probes = Collections.singletonList(probe("finite", "PT", 2.0));
        for (String expression : Arrays.asList(
                "(* 1e308 1e308)", "(- 1e308 -1e308)", "(/ 1 -0.0)",
                "(/ (* 1e308 1e308) 0)", "(Min (* 1e308 1e308) 1)",
                "(* 0 (* 1e308 1e308))", "(- (* 1e308 1e308) (* 1e308 1e308))")) {
            GPNode tree = parse(expression);
            FrozenPriorityProgram compiled = FrozenPriorityProgram.compile(tree);
            compareTree(tree, probes, "intermediate finite guard: " + expression, false);
            candidate(() -> compiled.evaluate(Collections.emptyMap()), "priority", expression);
            candidate(() -> compiled.sensitivity(probes, 0.125), "priority",
                    "constant sensitivity must not bypass baseline arithmetic");
        }
        GPNode tree = parse("PT");
        FrozenPriorityProgram terminal = FrozenPriorityProgram.compile(tree);
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            Map<String, Double> inputs = Collections.singletonMap("PT", value);
            compare(() -> PriorityExpression.evaluate(tree, inputs), () -> terminal.evaluate(inputs),
                    "non-finite used input");
            candidate(() -> terminal.evaluate(inputs), "priority", "non-finite input classification");
        }
        sameBits(7.0, FrozenPriorityProgram.compile(parse("7")).evaluate(
                Collections.singletonMap("PT", Double.NaN)), "unused non-finite map entry ignored");

        compareSensitivityFailure("(/ 1e308 (+ 1 PT))", probe("subtraction-overflow", "PT", -2.0),
                2.0, "sensitivity");
        compareSensitivityFailure("(/ 1 (Max 1e-300 PT))", probe("derivative-overflow", "PT", 1e-300),
                1e-310, "sensitivity");
        compareSensitivityFailure("(* 1e308 PT)", probe("perturbed-output-overflow", "PT", 0.0),
                2.0, "priority");

        Map<String, Double> partial = Collections.singletonMap("PT", 2.0);
        for (String expression : Arrays.asList("(+ (* PT 1e308) WIQ)", "(+ WIQ (* PT 1e308))")) {
            GPNode orderedTree = parse(expression);
            FrozenPriorityProgram compiled = FrozenPriorityProgram.compile(orderedTree);
            compare(() -> PriorityExpression.evaluate(orderedTree, partial), () -> compiled.evaluate(partial),
                    "left-to-right missing/overflow precedence");
            compare(() -> new OutputSensitivityCalculator(0.125).calculateTree(orderedTree,
                            Collections.singletonList(new SensitivityProbeSet.Probe("partial", partial))),
                    () -> compiled.sensitivity(
                            Collections.singletonList(new SensitivityProbeSet.Probe("partial", partial)), 0.125),
                    "baseline preserves left-to-right error precedence");
        }
    }

    private static void checkProtocolErrorsAndOrdering() {
        GPNode tree = parse("(+ PT WIQ)");
        FrozenPriorityProgram compiled = FrozenPriorityProgram.compile(tree);
        Map<String, Double> missing = Collections.singletonMap("PT", 1.0);
        protocol(() -> compiled.evaluate(missing), "missing terminal");
        protocol(() -> compiled.evaluate(null), "null inputs");
        Map<String, Double> nullValue = new LinkedHashMap<>();
        nullValue.put("PT", null);
        protocol(() -> compiled.evaluate(nullValue), "null terminal value");
        List<SensitivityProbeSet.Probe> partial = Collections.singletonList(
                new SensitivityProbeSet.Probe("partial", missing));
        compare(() -> new OutputSensitivityCalculator(0.125).calculateTree(tree, partial),
                () -> compiled.sensitivity(partial, 0.125), "missing probe terminal");
        protocol(() -> compiled.sensitivity(partial, 0.125), "missing probe terminal is a protocol error");
        List<SensitivityProbeSet.Probe> valid = randomProbes(8, 2);
        for (double epsilon : new double[]{0.0, -0.0, -1.0, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            compare(() -> new OutputSensitivityCalculator(epsilon).calculateTree(tree, valid),
                    () -> compiled.sensitivity(valid, epsilon), "invalid epsilon");
            protocol(() -> compiled.sensitivity(valid, epsilon), "invalid epsilon is a protocol error");
        }
        protocol(() -> compiled.sensitivity(null, 0.125), "null probes");
        protocol(() -> compiled.sensitivity(Collections.emptyList(), 0.125), "empty probes");
        protocol(() -> compiled.sensitivity(Collections.singletonList(null), 0.125), "null probe");

        FrozenPriorityProgram identity = FrozenPriorityProgram.compile(parse("PT"));
        for (double[] pair : new double[][]{{1, Double.MIN_VALUE}, {Double.MAX_VALUE, 1},
                {Double.MAX_VALUE, Double.MAX_VALUE}}) {
            List<SensitivityProbeSet.Probe> probes = Collections.singletonList(probe("delta", "PT", pair[0]));
            compare(() -> new OutputSensitivityCalculator(pair[1]).calculateTree(parse("PT"), probes),
                    () -> identity.sensitivity(probes, pair[1]), "unrepresentable absolute delta");
            protocol(() -> identity.sensitivity(probes, pair[1]), "delta failure is not candidate exclusion");
        }
        FrozenPriorityProgram cancelled = FrozenPriorityProgram.compile(parse("(- PT PT)"));
        protocol(() -> cancelled.sensitivity(Collections.singletonList(probe("cancelled", "PT", Double.MAX_VALUE)),
                1.0), "algebraically cancelled terminal still perturbed");

        // WIQ is visited first in the tree, but PT must be perturbed first alphabetically.
        GPNode sorted = parse("(+ WIQ (* PT 1e308))");
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("WIQ", Double.MAX_VALUE);
        values.put("PT", 0.0);
        List<SensitivityProbeSet.Probe> probes = Collections.singletonList(new SensitivityProbeSet.Probe("sorted", values));
        FrozenPriorityProgram sortedProgram = FrozenPriorityProgram.compile(sorted);
        compare(() -> new OutputSensitivityCalculator(1000).calculateTree(sorted, probes),
                () -> sortedProgram.sensitivity(probes, 1000), "sorted terminal perturbation order");
        candidate(() -> sortedProgram.sensitivity(probes, 1000), "priority", "sorted order before WIQ delta failure");

        GPNode reciprocal = parse("(/ 1 PT)");
        FrozenPriorityProgram reciprocalProgram = FrozenPriorityProgram.compile(reciprocal);
        List<SensitivityProbeSet.Probe> saved = Arrays.asList(
                probe("delta-first", "PT", Double.MAX_VALUE), probe("candidate-second", "PT", -0.0));
        compare(() -> new OutputSensitivityCalculator(1).calculateTree(reciprocal, saved),
                () -> reciprocalProgram.sensitivity(saved, 1), "saved probe ordering");
        protocol(() -> reciprocalProgram.sensitivity(saved, 1), "earlier protocol failure wins");
        List<SensitivityProbeSet.Probe> reverse = Arrays.asList(saved.get(1), saved.get(0));
        compare(() -> new OutputSensitivityCalculator(1).calculateTree(reciprocal, reverse),
                () -> reciprocalProgram.sensitivity(reverse, 1), "reversed probe ordering");
        candidate(() -> reciprocalProgram.sensitivity(reverse, 1), "priority", "earlier baseline failure wins");

        for (GPNode invalid : Arrays.asList(null, node("DD"), node("TerminalERC"), node("NaN"),
                node("Infinity"), node("-Infinity"), node("1e309"), node("+", node("PT")),
                node("+", node("PT"), node("WIQ"), node("W")), node("sin", node("PT"), node("WIQ")),
                node("+", node("PT"), null))) {
            compare(() -> { PriorityExpression.terminals(invalid); return 0.0; },
                    () -> { FrozenPriorityProgram.compile(invalid); return 0.0; }, "compile-time protocol guard");
            protocol(() -> { FrozenPriorityProgram.compile(invalid); return 0.0; },
                    "compile errors are not candidate exclusions");
        }
        CheckNode cycle = node("+", node("PT"), node("1"));
        cycle.children[1] = cycle;
        protocol(() -> { FrozenPriorityProgram.compile(cycle); return 0.0; }, "cyclic tree");
    }

    private static void checkFrozenSnapshot() {
        CheckNode terminal = node("PT");
        CheckNode constant = node("2");
        CheckNode root = node("+", terminal, constant);
        FrozenPriorityProgram compiled = FrozenPriorityProgram.compile(root);
        int names = root.nameCalls + terminal.nameCalls + constant.nameCalls;
        root.label = "unsupported";
        root.children = null;
        terminal.label = "unknown";
        constant.label = "NaN";
        Map<String, Double> inputs = new LinkedHashMap<>();
        inputs.put("PT", -0.0);
        inputs.put("WIQ", 9.0);
        List<SensitivityProbeSet.Probe> probes = Collections.singletonList(new SensitivityProbeSet.Probe("frozen", inputs));
        for (int i = 0; i < 20; i++) {
            sameBits(2.0, compiled.evaluate(inputs), "compiled tree survives source mutation");
            sameBits(1.0, compiled.sensitivity(probes, 0.125), "compiled sensitivity survives source mutation");
        }
        require(names == root.nameCalls + terminal.nameCalls + constant.nameCalls,
                "evaluation never resolves a GP node name again");
        sameBits(-0.0, inputs.get("PT"), "caller map not mutated");
        sameBits(-0.0, probes.get(0).values().get("PT"), "probe map not mutated");
        sameBits(9.0, inputs.get("WIQ"), "unused caller value retained");
        sameBits(0.0, FrozenPriorityProgram.compile(parse("-0.0")).sensitivity(probes, 0.125),
                "constant sensitivity returns positive zero");
    }

    private static void compareTree(GPNode tree, List<SensitivityProbeSet.Probe> probes,
                                    String label, boolean compareNative) {
        FrozenPriorityProgram compiled = FrozenPriorityProgram.compile(tree);
        for (SensitivityProbeSet.Probe probe : probes) {
            Outcome expected = compare(() -> PriorityExpression.evaluate(tree, probe.values()),
                    () -> compiled.evaluate(probe.values()), label + " / " + probe.id());
            if (compareNative && expected.failure == null) {
                sameBits(expected.value, nativeValue(tree, probe.values()), label + " / native");
            }
            for (String terminal : PriorityExpression.terminals(tree)) {
                double perturbed = probe.values().get(terminal) + 1e-4;
                if (!Double.isFinite(perturbed) || perturbed == probe.values().get(terminal)) {
                    continue;
                }
                Map<String, Double> modified = new LinkedHashMap<>(probe.values());
                modified.put(terminal, perturbed);
                compare(() -> PriorityExpression.evaluate(tree, modified), () -> compiled.evaluate(modified),
                        label + " / perturb " + terminal);
            }
        }
        for (double epsilon : new double[]{1e-4, 0.125}) {
            compare(() -> new OutputSensitivityCalculator(epsilon).calculateTree(tree, probes),
                    () -> compiled.sensitivity(probes, epsilon), label + " / sensitivity " + epsilon);
        }
    }

    private static void compareSensitivityFailure(String expression, SensitivityProbeSet.Probe probe,
                                                  double epsilon, String messagePart) {
        GPNode tree = parse(expression);
        FrozenPriorityProgram compiled = FrozenPriorityProgram.compile(tree);
        List<SensitivityProbeSet.Probe> probes = Collections.singletonList(probe);
        compare(() -> new OutputSensitivityCalculator(epsilon).calculateTree(tree, probes),
                () -> compiled.sensitivity(probes, epsilon), expression);
        candidate(() -> compiled.sensitivity(probes, epsilon), messagePart, expression);
    }

    private static GPNode parse(String expression) {
        return PriorityExpression.parseRule(RuleType.SEQUENCING, expression).getGPTree().child;
    }

    private static String randomExpression(Random random, int depth) {
        if (depth == 0 || random.nextInt(5) == 0) {
            if (random.nextInt(4) == 0) {
                String[] constants = {"-3", "-1", "-0.0", "0.0", "0.25", "2", "7"};
                return constants[random.nextInt(constants.length)];
            }
            List<String> terminals = SensitivityProbeSet.supportedTerminals();
            return terminals.get(random.nextInt(terminals.size()));
        }
        String[] operators = {"+", "-", "*", "/", "Max", "Min"};
        return "(" + operators[random.nextInt(operators.length)] + " "
                + randomExpression(random, depth - 1) + " " + randomExpression(random, depth - 1) + ")";
    }

    private static List<SensitivityProbeSet.Probe> randomProbes(long seed, int count) {
        Random random = new Random(seed);
        List<SensitivityProbeSet.Probe> probes = new ArrayList<>();
        List<String> terminals = new ArrayList<>(SensitivityProbeSet.supportedTerminals());
        Collections.reverse(terminals);
        for (int i = 0; i < count; i++) {
            Map<String, Double> values = new LinkedHashMap<>();
            for (String terminal : terminals) {
                double value = (random.nextInt(41) - 20) / 4.0;
                if (value == 0.0 && random.nextBoolean()) {
                    value = -0.0;
                }
                values.put(terminal, value);
            }
            probes.add(new SensitivityProbeSet.Probe("random-" + i, values));
        }
        return probes;
    }

    private static SensitivityProbeSet.Probe probe(String id, String terminal, double value) {
        return new SensitivityProbeSet.Probe(id, Collections.singletonMap(terminal, value));
    }

    private static double nativeValue(GPNode tree, Map<String, Double> inputs) {
        DoubleData result = new DoubleData();
        nativeBoundTree(tree, inputs).eval(null, 0, result, null, null, null);
        return result.value;
    }

    private static GPNode nativeBoundTree(GPNode node, Map<String, Double> inputs) {
        if (node.children == null || node.children.length == 0) {
            Double terminal = inputs.get(node.name());
            return new ConstantTerminal(terminal != null ? terminal : Double.parseDouble(node.name()));
        }
        GPNode copy = node.lightClone();
        copy.children = new GPNode[node.children.length];
        for (int i = 0; i < copy.children.length; i++) {
            copy.children[i] = nativeBoundTree(node.children[i], inputs);
        }
        return copy;
    }

    private static Outcome compare(DoubleSupplier reference, DoubleSupplier compiled, String label) {
        Outcome expected = attempt(reference);
        Outcome actual = attempt(compiled);
        if (expected.failure != null) {
            require(actual.failure != null && actual.failure.getClass() == expected.failure.getClass(),
                    label + ": expected " + expected.failure + ", got "
                            + (actual.failure == null ? actual.value : actual.failure));
        } else {
            require(actual.failure == null, label + ": unexpected " + actual.failure);
            sameBits(expected.value, actual.value, label);
        }
        return expected;
    }

    private static Outcome attempt(DoubleSupplier operation) {
        try {
            return new Outcome(operation.getAsDouble(), null);
        } catch (IllegalArgumentException failure) {
            return new Outcome(Double.NaN, failure);
        }
    }

    private static void protocol(DoubleSupplier operation, String label) {
        Outcome outcome = attempt(operation);
        require(outcome.failure != null && outcome.failure.getClass() == IllegalArgumentException.class,
                label + ": expected protocol IllegalArgumentException, got "
                        + (outcome.failure == null ? outcome.value : outcome.failure));
    }

    private static void candidate(DoubleSupplier operation, String messagePart, String label) {
        Outcome outcome = attempt(operation);
        require(outcome.failure != null && outcome.failure.getClass() == CandidateMetricException.class
                && outcome.failure.getMessage().contains(messagePart),
                label + ": expected candidate " + messagePart + " failure, got "
                        + (outcome.failure == null ? outcome.value : outcome.failure));
    }

    private static void sameBits(double expected, double actual, String label) {
        require(Double.doubleToRawLongBits(expected) == Double.doubleToRawLongBits(actual),
                label + ": expected " + expected + " [0x" + Long.toHexString(Double.doubleToRawLongBits(expected))
                        + "], got " + actual + " [0x" + Long.toHexString(Double.doubleToRawLongBits(actual)) + "]");
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Frozen priority check failed: " + label);
        }
    }

    private static final class Outcome {
        final double value;
        final IllegalArgumentException failure;

        Outcome(double value, IllegalArgumentException failure) {
            this.value = value;
            this.failure = failure;
        }
    }

    private static CheckNode node(String label, GPNode... children) {
        return new CheckNode(label, children);
    }

    private static final class CheckNode extends GPNode {
        private String label;
        private int nameCalls;

        CheckNode(String label, GPNode[] children) {
            this.label = label;
            this.children = children;
        }

        @Override
        public String name() {
            nameCalls++;
            return label;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public void eval(EvolutionState state, int thread, GPData input, ADFStack stack,
                         GPIndividual individual, Problem problem) {
            throw new UnsupportedOperationException("Synthetic nodes are only for reference interpretation");
        }
    }
}
