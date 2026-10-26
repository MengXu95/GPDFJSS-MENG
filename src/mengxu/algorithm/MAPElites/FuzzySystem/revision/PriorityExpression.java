package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import ec.gp.GPNode;
import ec.gp.GPTree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.ConstantTerminal;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

public final class PriorityExpression {
    private PriorityExpression() { }

    public static GPRule parseRule(RuleType type, String expression) {
        if (type == null || expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Rule type and expression are required");
        }
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\(|\\)|[^\\s()]+").matcher(expression);
        while (matcher.find()) { tokens.add(matcher.group()); }
        Parser parser = new Parser(tokens);
        GPTree tree = new GPTree();
        tree.child = parser.node(0);
        tree.child.parent = tree;
        if (parser.position != tokens.size()) {
            throw new IllegalArgumentException("Trailing tokens in GP expression");
        }
        return new GPRule(type, tree, expression);
    }

    private static final class Parser {
        private final List<String> tokens;
        private int position;

        Parser(List<String> tokens) { this.tokens = tokens; }

        String next() {
            if (position >= tokens.size()) { throw new IllegalArgumentException("Incomplete GP expression"); }
            return tokens.get(position++);
        }

        GPNode node(int depth) {
            if (depth > 256) { throw new IllegalArgumentException("GP expression is too deeply nested"); }
            String token = next();
            if (token.equals("(")) {
                String operator = next();
                GPNode result;
                switch (operator.toLowerCase(Locale.ROOT)) {
                    case "+": result = new yimei.jss.gp.function.Add(); break;
                    case "-": result = new yimei.jss.gp.function.Sub(); break;
                    case "*": result = new yimei.jss.gp.function.Mul(); break;
                    case "/": result = new yimei.jss.gp.function.Div(); break;
                    case "max": result = new yimei.jss.gp.function.Max(); break;
                    case "min": result = new yimei.jss.gp.function.Min(); break;
                    default: throw new IllegalArgumentException("Unsupported GP operator: " + operator);
                }
                result.children = new GPNode[]{node(depth + 1), node(depth + 1)};
                if (!next().equals(")")) { throw new IllegalArgumentException("Expected binary GP operator"); }
                for (int i = 0; i < result.children.length; i++) {
                    result.children[i].parent = result;
                    result.children[i].argposition = (byte) i;
                }
                return result;
            }
            if (token.equals(")")) { throw new IllegalArgumentException("Unexpected closing parenthesis"); }
            if (isNumber(token)) { return new ConstantTerminal(Double.parseDouble(token)); }
            JobShopAttribute attribute = JobShopAttribute.get(token);
            if (attribute == null) { throw new IllegalArgumentException("Unknown GP terminal: " + token); }
            return new AttributeGPNode(attribute);
        }
    }

    public static Set<String> terminals(GPNode root) {
        TreeSet<String> result = new TreeSet<>();
        collect(root, result);
        return Collections.unmodifiableSet(result);
    }

    private static void collect(GPNode node, Set<String> names) {
        if (node == null) { throw new IllegalArgumentException("Missing GP node"); }
        int arity = node.children == null ? 0 : node.children.length;
        if (arity == 0) {
            if (!isNumber(node.name())) {
                if (JobShopAttribute.get(node.name()) == null) {
                    throw new IllegalArgumentException("Unknown terminal: " + node.name());
                }
                names.add(node.name());
            }
            return;
        }
        requireOperator(node, arity);
        for (GPNode child : node.children) { collect(child, names); }
    }

    private static boolean isNumber(String name) {
        try {
            double value = Double.parseDouble(name);
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Non-finite GP constant: " + name);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void requireOperator(GPNode node, int arity) {
        String operator = node.name().toLowerCase(Locale.ROOT);
        if (arity != 2 || !(operator.equals("+") || operator.equals("-")
                || operator.equals("*") || operator.equals("/")
                || operator.equals("max") || operator.equals("min"))) {
            throw new IllegalArgumentException("Unsupported priority operator/arity: " + node.name() + "/" + arity);
        }
    }

    public static double evaluate(GPNode node, Map<String, Double> inputs) {
        if (node == null || inputs == null) {
            throw new IllegalArgumentException("A tree and input state are required");
        }
        int arity = node.children == null ? 0 : node.children.length;
        double value;
        if (arity == 0) {
            if (isNumber(node.name())) {
                value = Double.parseDouble(node.name());
            } else {
                Double terminal = inputs.get(node.name());
                if (terminal == null) {
                    throw new IllegalArgumentException("Probe lacks terminal: " + node.name());
                }
                value = terminal;
            }
        } else {
            requireOperator(node, arity);
            double left = evaluate(node.children[0], inputs);
            double right = evaluate(node.children[1], inputs);
            switch (node.name().toLowerCase(Locale.ROOT)) {
                case "+": value = left + right; break;
                case "-": value = left - right; break;
                case "*": value = left * right; break;
                case "/": value = Double.compare(right, 0.0) == 0 ? 1.0 : left / right; break;
                // Native GP operators retain the right operand on ties, including signed zero.
                case "max": value = left > right ? left : right; break;
                case "min": value = left < right ? left : right; break;
                default: throw new IllegalArgumentException("Unsupported operator: " + node.name());
            }
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite priority at " + node.name());
        }
        return value;
    }
}
