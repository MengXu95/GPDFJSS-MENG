package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fuzzy-based selector with explicit target-matching and monotone-importance modes.
 * Both recommendation scores are separate from the preference-independent SI diagnostic.
 */
public class FuzzyHeuristicSelector {

    public enum NormalizationMode {
        LEGACY_RECIPROCAL,
        MIN_MAX_ALL
    }

    public enum PreferenceMode {
        TARGET_MATCH,
        MONOTONE_IMPORTANCE
    }

    private static final String[][] ORDERED_TERMS = {
            {"Performance", "Low", "Medium", "High"},
            {"Interpretability", "Hard", "Moderate", "Easy"},
            {"Stability", "Unstable", "Moderate", "Stable"}
    };

    private final FuzzyInferenceSystem fuzzySystem;
    private final NormalizationMode normalizationMode;
    private final PreferenceMode preferenceMode;
    private final List<Heuristic> heuristicLibrary;
    private final Map<Heuristic, Map<String, Double>> orderedTermUtilities = new IdentityHashMap<>();

    public FuzzyHeuristicSelector(FuzzyInferenceSystem fuzzySystem) {
        this(fuzzySystem, NormalizationMode.LEGACY_RECIPROCAL);
    }

    public FuzzyHeuristicSelector(FuzzyInferenceSystem fuzzySystem, NormalizationMode normalizationMode) {
        this(fuzzySystem, normalizationMode, PreferenceMode.TARGET_MATCH);
    }

    /**
     * Importance mode uses min-max desirabilities and cached ordered-term utilities.
     * Existing one- and two-argument constructors retain target-membership semantics.
     */
    public FuzzyHeuristicSelector(FuzzyInferenceSystem fuzzySystem, NormalizationMode normalizationMode,
                                  PreferenceMode preferenceMode) {
        if (fuzzySystem == null || normalizationMode == null || preferenceMode == null) {
            throw new IllegalArgumentException("Fuzzy engine, normalization and preference modes are required");
        }
        if (preferenceMode == PreferenceMode.MONOTONE_IMPORTANCE
                && normalizationMode != NormalizationMode.MIN_MAX_ALL) {
            throw new IllegalArgumentException("Monotone importance requires MIN_MAX_ALL normalization");
        }
        this.fuzzySystem = fuzzySystem;
        this.normalizationMode = normalizationMode;
        this.preferenceMode = preferenceMode;
        this.heuristicLibrary = new ArrayList<>();
        validateEngine();
    }

    /**
     * Build the heuristic library from performance data
     * Note: All raw metrics are minimization objectives (smaller is better)
     * - Performance: smaller value = better (e.g., tardiness, makespan)
     * - Complexity: smaller value = more interpretable
     * - Sensitivity: smaller value = more stable
     */
    public void buildLibrary(List<Double> performances,
                             List<Double> complexities,
                             List<Double> sensitivities) {
        validateEngine();
        validateMetrics(performances);
        validateMetrics(complexities);
        validateMetrics(sensitivities);
        if (performances.size() != complexities.size() ||
                performances.size() != sensitivities.size()) {
            throw new IllegalArgumentException("All metric lists must have the same size");
        }

        double minPerf = Collections.min(performances);
        double maxPerf = Collections.max(performances);
        List<Double> interpretabilities = complexities.stream()
                .map(value -> 1.0 / (1.0 + value)).collect(Collectors.toList());
        List<Double> stabilities = sensitivities.stream()
                .map(value -> 1.0 / (1.0 + value)).collect(Collectors.toList());
        double minEase = Collections.min(interpretabilities);
        double maxEase = Collections.max(interpretabilities);
        double minStability = Collections.min(stabilities);
        double maxStability = Collections.max(stabilities);
        List<Heuristic> built = new ArrayList<>();
        Map<Heuristic, Map<String, Double>> builtUtilities = new IdentityHashMap<>();
        Map<String, MonotoneFuzzyUtility> utilityModels = new LinkedHashMap<>();
        if (preferenceMode == PreferenceMode.MONOTONE_IMPORTANCE) {
            for (String[] terms : ORDERED_TERMS) {
                utilityModels.put(terms[0], new MonotoneFuzzyUtility(
                        fuzzySystem.getInputVariables().get(terms[0]), terms[1], terms[2], terms[3]));
            }
        }

        for (int i = 0; i < performances.size(); i++) {
            Heuristic h = new Heuristic(i, performances.get(i),
                    complexities.get(i), sensitivities.get(i));

            boolean revised = normalizationMode == NormalizationMode.MIN_MAX_ALL;
            double normalizedPerformance = maxPerf == minPerf ? (revised ? 0.5 : 1.0)
                    : 1.0 - (performances.get(i) - minPerf) / (maxPerf - minPerf);

            // Compute Selection Index using fuzzy inference
            Map<String, Double> inputs = new HashMap<>();
            inputs.put("Performance", normalizedPerformance);  // Already normalized [0,1], higher is better
            inputs.put("Stability", revised
                    ? minMax(stabilities.get(i), minStability, maxStability) : h.getStability());
            inputs.put("Interpretability", revised
                    ? minMax(interpretabilities.get(i), minEase, maxEase) : h.getInterpretability());
            for (Map.Entry<String, Double> entry : inputs.entrySet()) {
                h.setNormalizedCriterion(entry.getKey(), entry.getValue());
            }
            if (preferenceMode == PreferenceMode.MONOTONE_IMPORTANCE) {
                Map<String, Double> utilities = new LinkedHashMap<>();
                for (Map.Entry<String, MonotoneFuzzyUtility> model : utilityModels.entrySet()) {
                    utilities.put(model.getKey(), model.getValue().evaluate(inputs.get(model.getKey())));
                }
                builtUtilities.put(h, Collections.unmodifiableMap(utilities));
            }

            double si = fuzzySystem.infer(inputs);
            h.setSelectionIndex(si);

            // Store fuzzy profile for similarity matching
            Map<String, FuzzyVariable> inputVars = fuzzySystem.getInputVariables();
            for (Map.Entry<String, Double> entry : inputs.entrySet()) {
                String varName = entry.getKey();
                double value = entry.getValue();
                FuzzyVariable var = inputVars.get(varName);
                Map<String, Double> memberships = var.fuzzify(value);
                for (Map.Entry<String, Double> mEntry : memberships.entrySet()) {
                    h.addFuzzyMembership(varName + "_" + mEntry.getKey(), mEntry.getValue());
                }
            }

            built.add(h);
        }
        heuristicLibrary.clear();
        heuristicLibrary.addAll(built);
        orderedTermUtilities.clear();
        orderedTermUtilities.putAll(builtUtilities);
    }

    /**
     * Select top-k heuristics based on user preference vector
     * Uses the configured target-match or monotone-importance score, never SI.
     */
    public List<Heuristic> selectByPreference(UserPreferenceVector upv, int k) {
        validateK(k);
        validatePreferences(upv);
        // Compute similarity for each heuristic
        List<HeuristicWithSimilarity> rankedHeuristics = new ArrayList<>();

        for (Heuristic h : heuristicLibrary) {
            double similarity = computePreferenceScore(h, upv);
            rankedHeuristics.add(new HeuristicWithSimilarity(h, similarity));
        }

        // Sort by similarity (descending) and return top-k
        return rankedHeuristics.stream()
                .sorted(Comparator.comparingDouble((HeuristicWithSimilarity item) -> item.similarity)
                        .reversed().thenComparingInt(item -> item.heuristic.getId()))
                .limit(k)
                .map(hws -> hws.heuristic)
                .collect(Collectors.toList());
    }

    /**
     * Select top-k heuristics based on Selection Index only
     */
    public List<Heuristic> selectBySelectionIndex(int k) {
        validateK(k);
        return heuristicLibrary.stream()
                .sorted(Comparator.comparingDouble(Heuristic::getSelectionIndex)
                        .reversed().thenComparingInt(Heuristic::getId))
                .limit(k)
                .collect(Collectors.toList());
    }

    /**
     * Random selection of k heuristics (baseline)
     */
    public List<Heuristic> selectRandomly(int k, Random random) {
        validateK(k);
        if (random == null) {
            throw new IllegalArgumentException("A random generator is required");
        }
        List<Heuristic> shuffled = new ArrayList<>(heuristicLibrary);
        Collections.shuffle(shuffled, random);
        return shuffled.stream().limit(k).collect(Collectors.toList());
    }

    /**
     * Compute fuzzy similarity between heuristic and user preference
     * Sim(h, UPV) = Σ w_X · min(μ_X^h, μ_X^u) / Σ w_X
     */
    private double computeFuzzySimilarity(Heuristic h, UserPreferenceVector upv) {
        double numerator = 0.0;
        double denominator = 0.0;

        double maxWeight = Collections.max(upv.getWeights().values());

        for (Map.Entry<String, String> pref : upv.getPreferences().entrySet()) {
            String varName = pref.getKey();
            String preferredTerm = pref.getValue();
            double weight = upv.getWeight(varName) / maxWeight;

            // Get heuristic's membership for this term
            String membershipKey = varName + "_" + preferredTerm;
            Double heuristicMembership = h.getFuzzyProfile().get(membershipKey);
            if (heuristicMembership == null) {
                throw new IllegalStateException("Missing heuristic membership: " + membershipKey);
            }

            // User's membership is 1.0 for the preferred term
            double userMembership = 1.0;

            // Apply min operator and weight
            numerator += weight * Math.min(heuristicMembership, userMembership);
            denominator += weight;
        }

        return numerator / denominator;
    }

    private double computePreferenceScore(Heuristic heuristic, UserPreferenceVector upv) {
        if (preferenceMode == PreferenceMode.TARGET_MATCH) {
            return computeFuzzySimilarity(heuristic, upv);
        }
        Map<String, Double> utilities = orderedTermUtilities.get(heuristic);
        if (utilities == null) {
            throw new IllegalStateException("Missing cached ordered-term utilities");
        }
        double maxWeight = Collections.max(upv.getWeights().values());
        double numerator = 0.0;
        double denominator = 0.0;
        for (String variable : upv.getPreferences().keySet()) {
            double weight = upv.getWeight(variable) / maxWeight;
            Double utility = utilities.get(variable);
            if (utility == null) {
                throw new IllegalStateException("Missing cached utility: " + variable);
            }
            numerator += weight * utility;
            denominator += weight;
        }
        double score = numerator / denominator;
        if (!(denominator > 0.0) || !Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalStateException("No finite importance score can be computed");
        }
        return score;
    }

    /** The configured recommendation score; the name is retained for compatibility. */
    public double getPreferenceSimilarity(Heuristic heuristic, UserPreferenceVector upv) {
        if (heuristic == null || !heuristicLibrary.contains(heuristic)) {
            throw new IllegalArgumentException("Heuristic must belong to this library");
        }
        validatePreferences(upv);
        return computePreferenceScore(heuristic, upv);
    }

    /** Clearer alias for either target-match similarity or monotone-importance utility. */
    public double getPreferenceScore(Heuristic heuristic, UserPreferenceVector upv) {
        return getPreferenceSimilarity(heuristic, upv);
    }

    /**
     * Read-only cached g values keyed by Performance, Interpretability and Stability,
     * in that order. Available only for a current library member in importance mode;
     * no utility or diagnostic inference is recomputed.
     */
    public Map<String, Double> getImportanceUtilities(Heuristic heuristic) {
        if (preferenceMode != PreferenceMode.MONOTONE_IMPORTANCE) {
            throw new IllegalStateException("Ordered-term utilities require MONOTONE_IMPORTANCE mode");
        }
        if (heuristic == null || !heuristicLibrary.contains(heuristic)) {
            throw new IllegalArgumentException("Heuristic must belong to this library");
        }
        Map<String, Double> utilities = orderedTermUtilities.get(heuristic);
        if (utilities == null) {
            throw new IllegalStateException("Missing cached ordered-term utilities");
        }
        return utilities;
    }

    private static double minMax(double value, double min, double max) {
        return max == min ? 0.5 : (value - min) / (max - min);
    }

    private static void validateMetrics(List<Double> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("Metric lists must be nonempty");
        }
        for (Double metric : metrics) {
            if (metric == null || !Double.isFinite(metric) || metric < 0.0) {
                throw new IllegalArgumentException("Raw metrics must be finite and nonnegative");
            }
        }
    }

    private void validateEngine() {
        fuzzySystem.validate();
        Map<String, FuzzyVariable> variables = fuzzySystem.getInputVariables();
        if (variables.size() != ORDERED_TERMS.length) {
            throw new IllegalArgumentException("Selector requires exactly three criterion variables");
        }
        for (String[] names : ORDERED_TERMS) {
            FuzzyVariable variable = variables.get(names[0]);
            if (variable == null || variable.getMinValue() > 0.0 || variable.getMaxValue() < 1.0
                    || !variable.getLinguisticTerms().containsAll(Arrays.asList(names).subList(1, names.length))) {
                throw new IllegalArgumentException("Missing selector variable, terms or [0,1] domain: " + names[0]);
            }
            if (preferenceMode == PreferenceMode.MONOTONE_IMPORTANCE) {
                new MonotoneFuzzyUtility(variable, names[1], names[2], names[3]);
            }
        }
    }

    private void validatePreferences(UserPreferenceVector upv) {
        validateEngine();
        if (upv == null) {
            throw new IllegalArgumentException("A preference vector is required");
        }
        upv.validate();
        Map<String, FuzzyVariable> variables = fuzzySystem.getInputVariables();
        for (Map.Entry<String, String> preference : upv.getPreferences().entrySet()) {
            FuzzyVariable variable = variables.get(preference.getKey());
            if (variable == null || !variable.getLinguisticTerms().contains(preference.getValue())) {
                throw new IllegalArgumentException("Unknown preference variable or term: " + preference);
            }
            if (preferenceMode == PreferenceMode.MONOTONE_IMPORTANCE) {
                for (String[] terms : ORDERED_TERMS) {
                    if (terms[0].equals(preference.getKey()) && !terms[3].equals(preference.getValue())) {
                        throw new IllegalArgumentException(
                                "Importance weights require beneficial High/Easy/Stable labels: " + preference);
                    }
                }
            }
        }
    }

    private void validateK(int k) {
        if (heuristicLibrary.isEmpty() || k < 1 || k > heuristicLibrary.size()) {
            throw new IllegalArgumentException("k must be in [1, library size] for a built nonempty library");
        }
    }

    /**
     * Get statistics about the heuristic library
     */
    public LibraryStatistics getStatistics() {
        if (heuristicLibrary.isEmpty()) {
            return new LibraryStatistics();
        }

        DoubleSummaryStatistics perfStats = heuristicLibrary.stream()
                .mapToDouble(Heuristic::getPerformance)
                .summaryStatistics();

        DoubleSummaryStatistics complexStats = heuristicLibrary.stream()
                .mapToDouble(Heuristic::getStructuralComplexity)
                .summaryStatistics();

        DoubleSummaryStatistics sensStats = heuristicLibrary.stream()
                .mapToDouble(Heuristic::getBehavioralSensitivity)
                .summaryStatistics();

        DoubleSummaryStatistics siStats = heuristicLibrary.stream()
                .mapToDouble(Heuristic::getSelectionIndex)
                .summaryStatistics();

        return new LibraryStatistics(perfStats, complexStats, sensStats, siStats);
    }

    public List<Heuristic> getHeuristicLibrary() {
        return Collections.unmodifiableList(new ArrayList<>(heuristicLibrary));
    }

    public NormalizationMode getNormalizationMode() {
        return normalizationMode;
    }

    public PreferenceMode getPreferenceMode() {
        return preferenceMode;
    }

    /**
     * Inner class to hold heuristic with its similarity score
     */
    private static class HeuristicWithSimilarity {
        Heuristic heuristic;
        double similarity;

        HeuristicWithSimilarity(Heuristic heuristic, double similarity) {
            this.heuristic = heuristic;
            this.similarity = similarity;
        }
    }

    /**
     * Inner class to hold library statistics
     */
    public static class LibraryStatistics {
        public DoubleSummaryStatistics performance;
        public DoubleSummaryStatistics complexity;
        public DoubleSummaryStatistics sensitivity;
        public DoubleSummaryStatistics selectionIndex;

        public LibraryStatistics() {
            this.performance = new DoubleSummaryStatistics();
            this.complexity = new DoubleSummaryStatistics();
            this.sensitivity = new DoubleSummaryStatistics();
            this.selectionIndex = new DoubleSummaryStatistics();
        }

        public LibraryStatistics(DoubleSummaryStatistics performance,
                                 DoubleSummaryStatistics complexity,
                                 DoubleSummaryStatistics sensitivity,
                                 DoubleSummaryStatistics selectionIndex) {
            this.performance = performance;
            this.complexity = complexity;
            this.sensitivity = sensitivity;
            this.selectionIndex = selectionIndex;
        }

        @Override
        public String toString() {
            return String.format(
                    "Library Statistics:\n" +
                            "  Performance:  min=%.3f, max=%.3f, avg=%.3f\n" +
                            "  Complexity:   min=%.3f, max=%.3f, avg=%.3f\n" +
                            "  Sensitivity:  min=%.3f, max=%.3f, avg=%.3f\n" +
                            "  Selection Index: min=%.3f, max=%.3f, avg=%.3f",
                    performance.getMin(), performance.getMax(), performance.getAverage(),
                    complexity.getMin(), complexity.getMax(), complexity.getAverage(),
                    sensitivity.getMin(), sensitivity.getMax(), sensitivity.getAverage(),
                    selectionIndex.getMin(), selectionIndex.getMax(), selectionIndex.getAverage()
            );
        }
    }
}