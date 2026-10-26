package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a fuzzy variable with multiple linguistic terms
 */
public class FuzzyVariable {
    
    private String name;
    private List<FuzzyMembershipFunction> membershipFunctions;
    private Map<String, FuzzyMembershipFunction> termMap;
    private double minValue;
    private double maxValue;
    
    public FuzzyVariable(String name, double minValue, double maxValue) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A variable name is required");
        }
        if (!Double.isFinite(minValue) || !Double.isFinite(maxValue)
                || minValue >= maxValue || !Double.isFinite(maxValue - minValue)) {
            throw new IllegalArgumentException("Variable domain must be finite and increasing");
        }
        this.name = name;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.membershipFunctions = new ArrayList<>();
        this.termMap = new LinkedHashMap<>();
    }
    
    /**
     * Add a linguistic term with Gaussian membership function
     */
    public void addMembershipFunction(String term, double center, double sigma) {
        FuzzyMembershipFunction mf = new FuzzyMembershipFunction(term, center, sigma);
        if (center < minValue || center > maxValue || termMap.containsKey(term)) {
            throw new IllegalArgumentException("Term must be unique and its center must be inside the domain: " + term);
        }
        membershipFunctions.add(mf);
        termMap.put(term, mf);
    }
    
    /**
     * Get membership degrees for all linguistic terms given a crisp value
     */
    public Map<String, Double> fuzzify(double value) {
        validateValue(value);
        if (membershipFunctions.isEmpty()) {
            throw new IllegalStateException("Variable has no membership functions: " + name);
        }
        Map<String, Double> memberships = new LinkedHashMap<>();
        for (FuzzyMembershipFunction mf : membershipFunctions) {
            memberships.put(mf.getLinguisticTerm(), mf.getMembership(value));
        }
        return memberships;
    }
    
    /**
     * Get membership degree for a specific linguistic term
     */
    public double getMembership(double value, String term) {
        validateValue(value);
        FuzzyMembershipFunction mf = termMap.get(term);
        if (mf == null) {
            throw new IllegalArgumentException("Unknown linguistic term: " + term);
        }
        return mf.getMembership(value);
    }
    
    public String getName() {
        return name;
    }
    
    public List<FuzzyMembershipFunction> getMembershipFunctions() {
        return Collections.unmodifiableList(new ArrayList<>(membershipFunctions));
    }
    
    public List<String> getLinguisticTerms() {
        List<String> terms = new ArrayList<>();
        for (FuzzyMembershipFunction mf : membershipFunctions) {
            terms.add(mf.getLinguisticTerm());
        }
        return terms;
    }
    
    public double getMinValue() {
        return minValue;
    }
    
    public double getMaxValue() {
        return maxValue;
    }

    private void validateValue(double value) {
        if (!Double.isFinite(value) || value < minValue || value > maxValue) {
            throw new IllegalArgumentException("Input outside finite domain for " + name + ": " + value);
        }
    }
}
