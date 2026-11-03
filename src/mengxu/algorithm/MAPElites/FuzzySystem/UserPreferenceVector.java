package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User Preference Vector (UPV) for encoding user scheduling preferences
 * Linguistic labels and explicit criterion-importance weights (not target strengths).
 * Target diagnostics may request any term; monotone importance requires beneficial labels.
 */
public class UserPreferenceVector {

    private Map<String, String> preferences;  // Variable name -> preferred linguistic term
    private Map<String, Double> weights;      // Variable name -> importance weight

    public UserPreferenceVector() {
        this.preferences = new LinkedHashMap<>();
        this.weights = new LinkedHashMap<>();
    }

    /**
     * Set preference for a specific variable
     * @param variableName Name of the fuzzy variable (e.g., "Performance", "Stability")
     * @param preferredTerm Preferred linguistic term (e.g., "High", "Stable")
     * @param weight Importance weight for this variable (default 1.0)
     */
    public UserPreferenceVector setPreference(String variableName, String preferredTerm, double weight) {
        if (variableName == null || variableName.trim().isEmpty()
                || preferredTerm == null || preferredTerm.trim().isEmpty()) {
            throw new IllegalArgumentException("Preference variable and target term are required");
        }
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException("Importance weights must be finite and nonnegative");
        }
        preferences.put(variableName, preferredTerm);
        weights.put(variableName, weight);
        return this;
    }

    /**
     * Set preference with default weight of 1.0
     */
    public UserPreferenceVector setPreference(String variableName, String preferredTerm) {
        return setPreference(variableName, preferredTerm, 1.0);
    }

    /**
     * Get preferred linguistic term for a variable
     */
    public String getPreferredTerm(String variableName) {
        return preferences.get(variableName);
    }

    /**
     * Get importance weight for a variable
     */
    public double getWeight(String variableName) {
        return weights.getOrDefault(variableName, 1.0);
    }

    /**
     * Get all preferences
     */
    public Map<String, String> getPreferences() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(preferences));
    }

    /**
     * Get all weights
     */
    public Map<String, Double> getWeights() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(weights));
    }

    /** A completed vector must assign positive importance to at least one target. */
    public void validate() {
        if (preferences.isEmpty() || weights.values().stream().noneMatch(weight -> weight > 0.0)) {
            throw new IllegalArgumentException("Preferences require a positive total importance weight");
        }
    }

    /**
     * Create a high-performance target-match diagnostic (not a monotone-importance profile).
     */
    public static UserPreferenceVector highPerformance() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "High", 2.0);
        upv.setPreference("Stability", "Moderate", 0.5);
        upv.setPreference("Interpretability", "Moderate", 0.5);
        return upv;
    }

    /**
     * Create a balanced target-match diagnostic (not a monotone-importance profile).
     */
    public static UserPreferenceVector balanced() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "Medium", 1.0);
        upv.setPreference("Stability", "Moderate", 1.0);
        upv.setPreference("Interpretability", "Moderate", 1.0);
        return upv;
    }

    /**
     * Create an interpretable target-match diagnostic (not a monotone-importance profile).
     */
    public static UserPreferenceVector interpretable() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "Medium", 0.5);
        upv.setPreference("Stability", "Stable", 1.5);
        upv.setPreference("Interpretability", "Easy", 2.0);
        return upv;
    }

    /**
     * Create a robust/stable target-match diagnostic (not a monotone-importance profile).
     */
    public static UserPreferenceVector robust() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "Medium", 1.0);
        upv.setPreference("Stability", "Stable", 2.0);
        upv.setPreference("Interpretability", "Moderate", 1.0);
        return upv;
    }

    // ========== Historical target-match diagnostics (not importance profiles) ==========

    /**
     * C1: High Performance, Low Stability
     * User Preference: Prioritize performance over stability
     * Fuzzy Variables: Performance: High, Stability: Unstable, Interpretability: Hard
     */
    public static UserPreferenceVector case1_HighPerformance() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "High", 5.0);
        upv.setPreference("Stability", "Unstable", 1.0);
        upv.setPreference("Interpretability", "Hard", 1.0);
        return upv;
    }

    /**
     * C2: High Stability, Low Performance
     * User Preference: Prioritize stability over performance
     * Fuzzy Variables: Performance: Low, Stability: Stable, Interpretability: Hard
     */
    public static UserPreferenceVector case2_HighStability() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "Low", 1.0);
        upv.setPreference("Stability", "Stable", 5.0);
        upv.setPreference("Interpretability", "Hard", 1.0);
        return upv;
    }

    /**
     * C3: High Interpretability, Low Performance, Low Stability
     */
    public static UserPreferenceVector case3_HighInterpretability() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "Low", 1.0);
        upv.setPreference("Stability", "Unstable", 1.0);
        upv.setPreference("Interpretability", "Easy", 5.0);
        return upv;
    }

    /**
     * C4: Balanced Performance and Stability
     * User Preference: Equal consideration of performance and stability
     * Fuzzy Variables: Performance: Medium, Stability: Moderate, Ease: Moderate
     */
    public static UserPreferenceVector case4_Balanced() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "Medium", 1.0);
        upv.setPreference("Stability", "Moderate", 1.0);
        upv.setPreference("Interpretability", "Moderate", 1.0);
        return upv;
    }

    /**
     * Legacy C5: High Performance, Unstable, Easy, with importance weights 5:1:5.
     */
    public static UserPreferenceVector case5_HighPerformanceHighInterpretability() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "High", 5.0);
        upv.setPreference("Stability", "Unstable", 1.0);
        upv.setPreference("Interpretability", "Easy", 5.0);
        return upv;
    }

    /**
     * C6: High Performance, Stable, Hard, with importance weights 5:5:1.
     */
    public static UserPreferenceVector case6_HighStabilityHighPerformanceHard() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "High", 5.0);
        upv.setPreference("Stability", "Stable", 5.0);
        upv.setPreference("Interpretability", "Hard", 1.0);
        return upv;
    }

    /**
     * C6: High Stability, Easy to Understand
     * User Preference: Prioritize robustness and interpretability
     * Fuzzy Variables: Performance: Low, Stability: Stable, Ease: Easy
     */
    public static UserPreferenceVector case6_StableAndEasy() {
        UserPreferenceVector upv = new UserPreferenceVector();
        upv.setPreference("Performance", "Low", 1.0);
        upv.setPreference("Stability", "Stable", 5.0);
        upv.setPreference("Interpretability", "Easy", 3.0);
        return upv;
    }

    /**
     * Historical paper-v2 target diagnostic C5: High/Moderate/Hard targets with weights
     * Performance=5, Stability=3, Interpretability=1. Not a monotone-importance case.
     */
    public static UserPreferenceVector case5_HighPerformanceModerateStability() {
        return new UserPreferenceVector()
                .setPreference("Performance", "High", 5.0)
                .setPreference("Stability", "Moderate", 3.0)
                .setPreference("Interpretability", "Hard", 1.0);
    }

    /** Fresh target-diagnostic vectors, ordered C1-C6, matching historical verification. */
    public static Map<String, UserPreferenceVector> legacyCases() {
        Map<String, UserPreferenceVector> cases = new LinkedHashMap<>();
        cases.put("C1", case1_HighPerformance());
        cases.put("C2", case2_HighStability());
        cases.put("C3", case3_HighInterpretability());
        cases.put("C4", case4_Balanced());
        cases.put("C5", case5_HighPerformanceHighInterpretability());
        cases.put("C6", case6_HighStabilityHighPerformanceHard());
        return Collections.unmodifiableMap(cases);
    }

    /** Historical paper-v2 target diagnostics: only C5 differs from legacyCases(). */
    public static Map<String, UserPreferenceVector> revisionCases() {
        Map<String, UserPreferenceVector> cases = new LinkedHashMap<>(legacyCases());
        cases.put("C5", case5_HighPerformanceModerateStability());
        return Collections.unmodifiableMap(cases);
    }

    /**
     * Fresh monotone-importance profiles, ordered C1-C6; weights below are in P,I,S order.
     * All labels are beneficial: High performance, Easy interpretation, Stable behavior.
     * C1 performance priority (5,1,1); C2 stability priority (1,1,5);
     * C3 interpretability priority (1,5,1); C4 equal importance (1,1,1);
     * C5 performance then stability (5,1,3); C6 joint performance/stability (5,1,5).
     */
    public static Map<String, UserPreferenceVector> importanceCases() {
        Map<String, UserPreferenceVector> cases = new LinkedHashMap<>();
        cases.put("C1", importance(5.0, 1.0, 1.0));
        cases.put("C2", importance(1.0, 1.0, 5.0));
        cases.put("C3", importance(1.0, 5.0, 1.0));
        cases.put("C4", importance(1.0, 1.0, 1.0));
        cases.put("C5", importance(5.0, 1.0, 3.0));
        cases.put("C6", importance(5.0, 1.0, 5.0));
        return Collections.unmodifiableMap(cases);
    }

    private static UserPreferenceVector importance(double performance, double interpretability, double stability) {
        return new UserPreferenceVector()
                .setPreference("Performance", "High", performance)
                .setPreference("Interpretability", "Easy", interpretability)
                .setPreference("Stability", "Stable", stability);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("UserPreferenceVector:\n");
        for (Map.Entry<String, String> entry : preferences.entrySet()) {
            String varName = entry.getKey();
            String term = entry.getValue();
            double weight = weights.get(varName);
            sb.append(String.format("  %s: %s (weight=%.2f)\n", varName, term, weight));
        }
        return sb.toString();
    }
}