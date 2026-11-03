package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import ec.gp.GPNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable, reentrant snapshot of the reference priority expression as a postfix program.
 * Compilation resolves names, numeric literals and sorted unique terminal slots once.
 * Evaluation retains every node and its left-to-right finite check; it does not fold constants.
 * This separate kernel does not change the historical experiment-A metric implementation.
 */
public final class FrozenPriorityProgram {
    private static final byte CONSTANT = 0;
    private static final byte TERMINAL = 1;
    private static final byte ADD = 2;
    private static final byte SUBTRACT = 3;
    private static final byte MULTIPLY = 4;
    private static final byte DIVIDE = 5;
    private static final byte MAXIMUM = 6;
    private static final byte MINIMUM = 7;

    private final byte[] opcodes;
    private final int[] terminalSlots;
    private final double[] constants;
    private final String[] labels;
    private final String[] terminalNames;
    private final int stackCapacity;

    private FrozenPriorityProgram(Compiler compiler) {
        int size = compiler.instructions.size();
        opcodes = new byte[size];
        terminalSlots = new int[size];
        constants = new double[size];
        labels = new String[size];
        terminalNames = compiler.terminals.toArray(new String[0]);
        stackCapacity = compiler.maximumStack;
        for (int i = 0; i < size; i++) {
            Instruction instruction = compiler.instructions.get(i);
            opcodes[i] = instruction.opcode;
            constants[i] = instruction.constant;
            labels[i] = instruction.label;
            if (instruction.opcode == TERMINAL) {
                terminalSlots[i] = Arrays.binarySearch(terminalNames, instruction.label);
            }
        }
    }

    /** Compile the same operator/terminal/numeric-name policy as PriorityExpression. */
    public static FrozenPriorityProgram compile(GPNode root) {
        Compiler compiler = new Compiler();
        compiler.emit(root);
        return new FrozenPriorityProgram(compiler);
    }

    /**
     * Evaluate a frozen state without modifying it. Missing terminals are protocol errors;
     * non-finite used inputs or intermediate arithmetic are CandidateMetricException.
     * Unused map entries are ignored, as in the reference evaluator.
     */
    public double evaluate(Map<String, Double> inputs) {
        if (inputs == null) {
            throw new IllegalArgumentException("A tree and input state are required");
        }
        return execute(inputs, null, new double[stackCapacity]);
    }

    /**
     * Absolute forward perturbations, in saved probe order and sorted unique terminal order.
     * Probe exposes id() and values(). Baselines fill dense terminal slots; perturbations
     * then reuse those slots and one stack, with no parsing, map copying or tree traversal.
     * Even a terminal-free program evaluates every baseline before returning positive zero.
     */
    public double sensitivity(List<SensitivityProbeSet.Probe> probes, double epsilon) {
        if (!Double.isFinite(epsilon) || epsilon <= 0.0) {
            throw new IllegalArgumentException("epsilon must be finite and positive");
        }
        if (probes == null || probes.isEmpty()) {
            throw new IllegalArgumentException("Sensitivity needs non-empty probe states");
        }
        double[] values = new double[terminalNames.length];
        double[] stack = new double[stackCapacity];
        double mean = 0.0;
        long count = 0;
        for (SensitivityProbeSet.Probe probe : probes) {
            if (probe == null) {
                throw new IllegalArgumentException("Null sensitivity probe");
            }
            double baseline = execute(probe.values(), values, stack);
            for (int terminal = 0; terminal < terminalNames.length; terminal++) {
                double original = values[terminal];
                double perturbed = original + epsilon;
                if (!Double.isFinite(perturbed) || perturbed == original) {
                    throw new IllegalArgumentException("Unrepresentable perturbation for " + terminalNames[terminal]);
                }
                values[terminal] = perturbed;
                double output = execute(null, values, stack);
                values[terminal] = original;
                double derivative = Math.abs(output - baseline) / epsilon;
                if (!Double.isFinite(derivative)) {
                    throw new CandidateMetricException("Non-finite sensitivity at probe " + probe.id());
                }
                count++;
                mean += (derivative - mean) / count;
            }
        }
        return terminalNames.length == 0 ? 0.0 : mean;
    }

    private double execute(Map<String, Double> inputs, double[] values, double[] stack) {
        int top = 0;
        for (int i = 0; i < opcodes.length; i++) {
            byte opcode = opcodes[i];
            double value;
            if (opcode == CONSTANT) {
                value = constants[i];
                top++;
            } else if (opcode == TERMINAL) {
                int slot = terminalSlots[i];
                if (inputs != null) {
                    Double input = inputs.get(terminalNames[slot]);
                    if (input == null) {
                        throw new IllegalArgumentException("Probe lacks terminal: " + terminalNames[slot]);
                    }
                    value = input;
                    if (values != null) {
                        values[slot] = value;
                    }
                } else {
                    value = values[slot];
                }
                top++;
            } else {
                double right = stack[--top];
                double left = stack[top - 1];
                switch (opcode) {
                    case ADD: value = left + right; break;
                    case SUBTRACT: value = left - right; break;
                    case MULTIPLY: value = left * right; break;
                    case DIVIDE: value = Double.compare(right, 0.0) == 0 ? 1.0 : left / right; break;
                    case MAXIMUM: value = left > right ? left : right; break;
                    case MINIMUM: value = left < right ? left : right; break;
                    default: throw new IllegalStateException("Invalid compiled priority opcode: " + opcode);
                }
            }
            if (!Double.isFinite(value)) {
                throw new CandidateMetricException("Non-finite priority at " + labels[i]);
            }
            stack[top - 1] = value;
        }
        return stack[0];
    }

    private static final class Instruction {
        final byte opcode;
        final String label;
        final double constant;

        Instruction(byte opcode, String label, double constant) {
            this.opcode = opcode;
            this.label = label;
            this.constant = constant;
        }
    }

    private static final class Compiler {
        final List<Instruction> instructions = new ArrayList<>();
        final Set<String> terminals = new TreeSet<>();
        final Set<GPNode> active = Collections.newSetFromMap(new IdentityHashMap<GPNode, Boolean>());
        int stack;
        int maximumStack;

        void emit(GPNode node) {
            if (node == null) {
                throw new IllegalArgumentException("Missing GP node");
            }
            if (!active.add(node)) {
                throw new IllegalArgumentException("Cyclic GP priority tree");
            }
            String label = node.name();
            if (label == null) {
                throw new IllegalArgumentException("Missing GP node name");
            }
            int arity = node.children == null ? 0 : node.children.length;
            if (arity == 0) {
                instructions.add(leaf(label));
                maximumStack = Math.max(maximumStack, ++stack);
            } else {
                byte opcode = operator(label, arity);
                emit(node.children[0]);
                emit(node.children[1]);
                instructions.add(new Instruction(opcode, label, 0.0));
                stack--;
            }
            active.remove(node);
        }

        private Instruction leaf(String label) {
            double value;
            try {
                value = Double.parseDouble(label);
            } catch (NumberFormatException notNumeric) {
                SensitivityProbeSet.requireSupportedTerminal(label);
                terminals.add(label);
                return new Instruction(TERMINAL, label, 0.0);
            }
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Non-finite GP constant: " + label);
            }
            return new Instruction(CONSTANT, label, value);
        }

        private byte operator(String label, int arity) {
            if (arity == 2) {
                switch (label.toLowerCase(Locale.ROOT)) {
                    case "+": return ADD;
                    case "-": return SUBTRACT;
                    case "*": return MULTIPLY;
                    case "/": return DIVIDE;
                    case "max": return MAXIMUM;
                    case "min": return MINIMUM;
                    default: break;
                }
            }
            throw new IllegalArgumentException("Unsupported priority operator/arity: " + label + "/" + arity);
        }
    }
}
