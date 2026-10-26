package mengxu.algorithm.MAPElites.FuzzySystem;

import ec.gp.GPNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class for calculating the structural complexity of a GP tree.
 * The complexity is calculated based on the type of operator at each node.
 */
public class TreeComplexityCalculator {

    private static final Set<String> LEVEL_1_OPS = new HashSet<>(Arrays.asList("+", "-"));
    private static final Set<String> LEVEL_2_OPS = new HashSet<>(Arrays.asList("*", "/"));
    private static final Set<String> LEVEL_3_OPS = new HashSet<>(Arrays.asList("max", "min"));

    /**
     * Recursively calculates the structural complexity of a GP tree.
     * The complexity is defined based on the operator's level:
     * Level 1 (+, -) = 2
     * Level 2 (*, /) = 3
     * Level 3 (max, min) = 4
     * Leaves (terminals/numeric constants) = 1; unknown non-leaf operators fail.
     *
     * @param tree The root node of the GP tree.
     * @return The total structural complexity of the tree.
     */
    public double calculateStructuralComplexity(GPNode tree) {
        if (tree == null) {
            throw new IllegalArgumentException("A GP tree is required");
        }
        double complexity = 0.0;
        String value = tree.name();
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("A named GP node is required");
        }
        value = value.toLowerCase(Locale.ROOT);

        if (tree.children == null || tree.children.length == 0) {
            if (LEVEL_1_OPS.contains(value) || LEVEL_2_OPS.contains(value) || LEVEL_3_OPS.contains(value)) {
                throw new IllegalArgumentException("Operator has no children: " + tree.name());
            }
            complexity = 1.0;
        } else if (LEVEL_1_OPS.contains(value)) {
            complexity = 2.0;
        } else if (LEVEL_2_OPS.contains(value)) {
            complexity = 3.0;
        } else if (LEVEL_3_OPS.contains(value)) {
            complexity = 4.0;
        } else {
            throw new IllegalArgumentException("Unknown non-leaf GP operator: " + tree.name());
        }

        if (tree.children != null) {
            for (GPNode child : tree.children) {
                complexity += calculateStructuralComplexity(child);
            }
        }

        return complexity;
    }
}
