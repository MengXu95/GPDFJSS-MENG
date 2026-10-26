package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import ec.gp.GPNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import yimei.jss.rule.operation.evolved.GPRule;

public final class OutputSensitivityCalculator {
    public static final String VERSION = "priority-output-forward-absolute-v2";
    private final double epsilon;

    public OutputSensitivityCalculator(double epsilon) {
        if (!Double.isFinite(epsilon) || epsilon <= 0) {
            throw new IllegalArgumentException("epsilon must be finite and positive");
        }
        this.epsilon = epsilon;
    }

    public double epsilon() { return epsilon; }

    public double calculate(GPRule[] rules, SensitivityProbeSet probes) {
        if (rules == null || rules.length != 2 || probes == null) {
            throw new IllegalArgumentException("A sequencing/routing pair and probes are required");
        }
        GPRule sequencing = null;
        GPRule routing = null;
        for (GPRule rule : rules) {
            if (rule == null) { throw new IllegalArgumentException("Null rule"); }
            if (rule.getType() == yimei.jss.rule.RuleType.SEQUENCING) { sequencing = rule; }
            if (rule.getType() == yimei.jss.rule.RuleType.ROUTING) { routing = rule; }
        }
        if (sequencing == null || routing == null) {
            throw new IllegalArgumentException("Exactly one rule of each decision type is required");
        }
        return (calculateTree(sequencing.getGPTree().child, probes.sequencing())
                + calculateTree(routing.getGPTree().child, probes.routing())) / 2.0;
    }

    public double calculateTree(GPNode tree, List<SensitivityProbeSet.Probe> probes) {
        if (probes == null || probes.isEmpty()) {
            throw new IllegalArgumentException("Sensitivity needs non-empty probe states");
        }
        Set<String> terminals = PriorityExpression.terminals(tree);
        double mean = 0;
        long count = 0;
        for (SensitivityProbeSet.Probe probe : probes) {
            double baseline = PriorityExpression.evaluate(tree, probe.values());
            for (String terminal : terminals) {
                Double original = probe.values().get(terminal);
                if (original == null) {
                    throw new IllegalArgumentException("Probe " + probe.id() + " lacks " + terminal);
                }
                double perturbed = original + epsilon;
                if (!Double.isFinite(perturbed) || perturbed == original) {
                    throw new IllegalArgumentException("Unrepresentable perturbation for " + terminal);
                }
                Map<String, Double> modified = new LinkedHashMap<>(probe.values());
                modified.put(terminal, perturbed);
                double derivative = Math.abs(PriorityExpression.evaluate(tree, modified) - baseline) / epsilon;
                if (!Double.isFinite(derivative)) {
                    throw new IllegalArgumentException("Non-finite sensitivity at probe " + probe.id());
                }
                count++;
                mean += (derivative - mean) / count;
            }
        }
        // A constant rule has zero derivative with respect to every terminal.
        return terminals.isEmpty() ? 0.0 : mean;
    }
}
