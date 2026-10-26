package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mamdani inference with max-min composition and the legacy 1000-point centroid.
 * Invalid/incomplete models or inputs fail explicitly rather than returning a midpoint.
 */
public class FuzzyInferenceSystem {
    private final Map<String, FuzzyVariable> inputVariables = new LinkedHashMap<>();
    private FuzzyVariable outputVariable;
    private final List<FuzzyRule> rules = new ArrayList<>();

    public void addInputVariable(FuzzyVariable variable) {
        if (variable == null || inputVariables.containsKey(variable.getName())) {
            throw new IllegalArgumentException("Input variable must be nonnull and unique");
        }
        inputVariables.put(variable.getName(), variable);
    }

    public void setOutputVariable(FuzzyVariable variable) {
        if (variable == null) {
            throw new IllegalArgumentException("Output variable is required");
        }
        outputVariable = variable;
    }

    public void addRule(FuzzyRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule is required");
        }
        rule.validate();
        rules.add(rule);
    }

    /** Validate references as well as model completeness, including later rule edits. */
    public void validate() {
        if (inputVariables.isEmpty() || outputVariable == null || rules.isEmpty()) {
            throw new IllegalStateException("Inference requires inputs, an output and rules");
        }
        if (inputVariables.containsKey(outputVariable.getName())
                || outputVariable.getMembershipFunctions().isEmpty()) {
            throw new IllegalStateException("Output must have terms and a distinct name");
        }
        for (FuzzyVariable variable : inputVariables.values()) {
            if (variable.getMembershipFunctions().isEmpty()) {
                throw new IllegalStateException("Input has no terms: " + variable.getName());
            }
        }
        for (FuzzyRule rule : rules) {
            rule.validate();
            for (Map.Entry<String, String> entry : rule.getAntecedents().entrySet()) {
                FuzzyVariable variable = inputVariables.get(entry.getKey());
                if (variable == null || !variable.getLinguisticTerms().contains(entry.getValue())) {
                    throw new IllegalStateException("Unknown rule input or term: " + entry);
                }
            }
            if (!outputVariable.getName().equals(rule.getConsequentVariable())
                    || !outputVariable.getLinguisticTerms().contains(rule.getConsequentTerm())) {
                throw new IllegalStateException("Unknown rule consequent: " + rule.getConsequentTerm());
            }
        }
    }

    public double infer(Map<String, Double> inputs) {
        return inferWithDetails(inputs).output;
    }

    public InferenceResult inferWithDetails(Map<String, Double> inputs) {
        validate();
        if (inputs == null || !inputs.keySet().equals(inputVariables.keySet())) {
            throw new IllegalArgumentException("Exactly all model input variables must be supplied");
        }
        Map<String, Map<String, Double>> fuzzified = new LinkedHashMap<>();
        for (Map.Entry<String, FuzzyVariable> entry : inputVariables.entrySet()) {
            Double value = inputs.get(entry.getKey());
            if (value == null) {
                throw new IllegalArgumentException("Null input: " + entry.getKey());
            }
            fuzzified.put(entry.getKey(), entry.getValue().fuzzify(value));
        }
        Map<String, Double> aggregated = new LinkedHashMap<>();
        List<String> fired = new ArrayList<>();
        for (FuzzyRule rule : rules) {
            double strength = rule.evaluate(fuzzified);
            if (strength > 0.0) {
                String term = rule.getConsequentTerm();
                aggregated.put(term, Math.max(aggregated.getOrDefault(term, 0.0), strength));
                fired.add(rule + " [strength=" + String.format("%.3f", strength) + "]");
            }
        }
        return new InferenceResult(defuzzify(aggregated), fuzzified, aggregated, fired);
    }

    private double defuzzify(Map<String, Double> aggregated) {
        if (aggregated.isEmpty()) {
            throw new IllegalStateException("No fuzzy rules fired");
        }
        double numerator = 0.0;
        double denominator = 0.0;
        int numSamples = 1000;
        double step = (outputVariable.getMaxValue() - outputVariable.getMinValue()) / numSamples;
        for (int i = 0; i < numSamples; i++) {
            double z = outputVariable.getMinValue() + i * step;
            double membership = 0.0;
            for (Map.Entry<String, Double> entry : aggregated.entrySet()) {
                membership = Math.max(membership,
                        Math.min(entry.getValue(), outputVariable.getMembership(z, entry.getKey())));
            }
            numerator += membership * z * step;
            denominator += membership * step;
        }
        double output = numerator / denominator;
        if (!(denominator > 0.0) || !Double.isFinite(denominator) || !Double.isFinite(output)) {
            throw new IllegalStateException("No finite centroid can be computed");
        }
        return output;
    }

    public Map<String, FuzzyVariable> getInputVariables() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(inputVariables));
    }

    public FuzzyVariable getOutputVariable() {
        return outputVariable;
    }

    public List<FuzzyRule> getRules() {
        return Collections.unmodifiableList(new ArrayList<>(rules));
    }

    public static class InferenceResult {
        public final double output;
        public final Map<String, Map<String, Double>> inputMemberships;
        public final Map<String, Double> outputMemberships;
        public final List<String> firedRules;

        public InferenceResult(double output, Map<String, Map<String, Double>> inputMemberships,
                               Map<String, Double> outputMemberships, List<String> firedRules) {
            this.output = output;
            Map<String, Map<String, Double>> copied = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Double>> entry : inputMemberships.entrySet()) {
                copied.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
            }
            this.inputMemberships = Collections.unmodifiableMap(copied);
            this.outputMemberships = Collections.unmodifiableMap(new LinkedHashMap<>(outputMemberships));
            this.firedRules = Collections.unmodifiableList(new ArrayList<>(firedRules));
        }
    }
}
