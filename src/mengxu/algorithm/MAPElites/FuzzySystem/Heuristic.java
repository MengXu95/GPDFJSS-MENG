package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;

/**
 * Represents a heuristic with its performance metrics and fuzzy characteristics
 *
 * IMPORTANT: All raw metrics are MINIMIZATION objectives (smaller is better):
 * - performance: smaller value = better scheduling performance (e.g., tardiness, makespan)
 * - structuralComplexity: smaller value = simpler = more interpretable
 * - behavioralSensitivity: smaller value = less sensitive = more stable
 *
 * The fuzzy system handles the normalization and inversion internally.
 */
public class Heuristic {

    private int id;
    private double performance;           // P_h: raw value (minimization), smaller is better
    private double structuralComplexity;  // C_h: raw value (minimization), smaller is better
    private double behavioralSensitivity; // S_h: raw value (minimization), smaller is better
    private double selectionIndex;        // SI: model-domain centroid, separate from preference matching
    private Map<String, Double> fuzzyProfile;  // Fuzzy memberships for each variable
    private final Map<String, Double> normalizedCriteria = new HashMap<>();

    public Heuristic(int id, double performance, double complexity, double sensitivity) {
        this.id = id;
        setPerformance(performance);
        setStructuralComplexity(complexity);
        setBehavioralSensitivity(sensitivity);
        this.selectionIndex = 0.0;
        this.fuzzyProfile = new HashMap<>();
    }

    /**
     * Get interpretability score (inverse of complexity)
     * Lower complexity means easier to interpret
     */
    public double getInterpretability() {
        return 1.0 / (1.0 + structuralComplexity);
    }

    /**
     * Get stability score (inverse of sensitivity)
     * Lower sensitivity means more stable
     */
    public double getStability() {
        return 1.0 / (1.0 + behavioralSensitivity);
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public double getPerformance() {
        return performance;
    }

    public void setPerformance(double performance) {
        validateRawMetric(performance);
        this.performance = performance;
    }

    public double getStructuralComplexity() {
        return structuralComplexity;
    }

    public void setStructuralComplexity(double structuralComplexity) {
        validateRawMetric(structuralComplexity);
        this.structuralComplexity = structuralComplexity;
    }

    public double getBehavioralSensitivity() {
        return behavioralSensitivity;
    }

    public void setBehavioralSensitivity(double behavioralSensitivity) {
        validateRawMetric(behavioralSensitivity);
        this.behavioralSensitivity = behavioralSensitivity;
    }

    public double getSelectionIndex() {
        return selectionIndex;
    }

    public void setSelectionIndex(double selectionIndex) {
        validateRawMetric(selectionIndex);
        this.selectionIndex = selectionIndex;
    }

    public Map<String, Double> getFuzzyProfile() {
        return Collections.unmodifiableMap(new HashMap<>(fuzzyProfile));
    }

    public void setFuzzyProfile(Map<String, Double> fuzzyProfile) {
        if (fuzzyProfile == null) {
            throw new IllegalArgumentException("Fuzzy profile is required");
        }
        Map<String, Double> copy = new HashMap<>();
        for (Map.Entry<String, Double> entry : fuzzyProfile.entrySet()) {
            validateMembership(entry.getKey(), entry.getValue());
            copy.put(entry.getKey(), entry.getValue());
        }
        this.fuzzyProfile = copy;
    }

    public void addFuzzyMembership(String variable, double membership) {
        validateMembership(variable, membership);
        this.fuzzyProfile.put(variable, membership);
    }

    /** Normalized model inputs; raw metric getters remain unchanged. */
    public Map<String, Double> getNormalizedCriteria() {
        return Collections.unmodifiableMap(new HashMap<>(normalizedCriteria));
    }

    void setNormalizedCriterion(String variable, double value) {
        validateMembership(variable, value);
        normalizedCriteria.put(variable, value);
    }

    private static void validateRawMetric(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Raw metrics and selection index must be finite and nonnegative");
        }
    }

    private static void validateMembership(String variable, Double membership) {
        if (variable == null || variable.trim().isEmpty() || membership == null
                || !Double.isFinite(membership) || membership < 0.0 || membership > 1.0) {
            throw new IllegalArgumentException("Named memberships must be finite and in [0,1]");
        }
    }

    @Override
    public String toString() {
        return String.format("Heuristic[id=%d, P=%.3f, C=%.3f, S=%.3f, SI=%.3f]",
                id, performance, structuralComplexity, behavioralSensitivity, selectionIndex);
    }

    /**
     * Get a detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Heuristic ID: %d\n", id));
        sb.append(String.format("  Performance: %.4f\n", performance));
        sb.append(String.format("  Structural Complexity: %.4f (Interpretability: %.4f)\n",
                structuralComplexity, getInterpretability()));
        sb.append(String.format("  Behavioral Sensitivity: %.4f (Stability: %.4f)\n",
                behavioralSensitivity, getStability()));
        sb.append(String.format("  Selection Index: %.4f\n", selectionIndex));
        return sb.toString();
    }
}