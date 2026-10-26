package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a fuzzy rule in the form:
 * IF (P is A) AND (S is B) AND (E is C) THEN (SI is D)
 */
public class FuzzyRule {
    
    private Map<String, String> antecedents;  // Variable name -> linguistic term
    private String consequentVariable;
    private String consequentTerm;
    private double weight;  // Rule weight (default 1.0)
    
    public FuzzyRule() {
        this.antecedents = new LinkedHashMap<>();
        this.weight = 1.0;
    }
    
    /**
     * Add an antecedent condition (IF part)
     */
    public FuzzyRule addAntecedent(String variableName, String linguisticTerm) {
        requireName(variableName);
        requireName(linguisticTerm);
        antecedents.put(variableName, linguisticTerm);
        return this;
    }
    
    /**
     * Set the consequent (THEN part)
     */
    public FuzzyRule setConsequent(String variableName, String linguisticTerm) {
        requireName(variableName);
        requireName(linguisticTerm);
        this.consequentVariable = variableName;
        this.consequentTerm = linguisticTerm;
        return this;
    }
    
    /**
     * Set rule weight
     */
    public FuzzyRule setWeight(double weight) {
        if (!Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("Rule weight must be finite and in [0,1]");
        }
        this.weight = weight;
        return this;
    }
    
    /**
     * Evaluate the rule using min (AND) operator
     * Returns the firing strength of the rule
     */
    public double evaluate(Map<String, Map<String, Double>> fuzzifiedInputs) {
        validate();
        if (fuzzifiedInputs == null) {
            throw new IllegalArgumentException("Fuzzified inputs are required");
        }
        double minMembership = 1.0;
        
        for (Map.Entry<String, String> antecedent : antecedents.entrySet()) {
            String varName = antecedent.getKey();
            String term = antecedent.getValue();
            
            Map<String, Double> varMemberships = fuzzifiedInputs.get(varName);
            if (varMemberships == null || !varMemberships.containsKey(term)) {
                throw new IllegalArgumentException("Missing rule input or term: " + varName + "_" + term);
            }
            
            Double membership = varMemberships.get(term);
            if (membership == null || !Double.isFinite(membership) || membership < 0.0 || membership > 1.0) {
                throw new IllegalArgumentException("Membership must be finite and in [0,1]");
            }
            minMembership = Math.min(minMembership, membership);
        }
        
        return minMembership * weight;
    }
    
    public Map<String, String> getAntecedents() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(antecedents));
    }
    
    public String getConsequentVariable() {
        return consequentVariable;
    }
    
    public String getConsequentTerm() {
        return consequentTerm;
    }
    
    public double getWeight() {
        return weight;
    }

    void validate() {
        if (antecedents.isEmpty() || consequentVariable == null || consequentTerm == null) {
            throw new IllegalStateException("Rule requires antecedents and a consequent");
        }
    }

    private static void requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Rule variable and term names are required");
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("IF ");
        boolean first = true;
        for (Map.Entry<String, String> entry : antecedents.entrySet()) {
            if (!first) sb.append(" AND ");
            sb.append(entry.getKey()).append(" is ").append(entry.getValue());
            first = false;
        }
        sb.append(" THEN ").append(consequentVariable).append(" is ").append(consequentTerm);
        return sb.toString();
    }
}
