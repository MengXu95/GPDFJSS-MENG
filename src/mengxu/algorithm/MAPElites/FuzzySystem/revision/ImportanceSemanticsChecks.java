package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyHeuristicSelector;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyInferenceSystem;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyVariable;
import mengxu.algorithm.MAPElites.FuzzySystem.Heuristic;
import mengxu.algorithm.MAPElites.FuzzySystem.MonotoneFuzzyUtility;
import mengxu.algorithm.MAPElites.FuzzySystem.UserPreferenceVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Pure-Java importance assertions for the parent harness; leaves target diagnostics intact. */
public final class ImportanceSemanticsChecks {
    private ImportanceSemanticsChecks() {
    }

    public static void runChecks() {
        checkOrderedUtilities();
        checkStableArithmeticAndGuards();
        checkProfiles();
        checkImportanceUtilityAccess();
        checkDominanceAndScaling();
        checkZeroAndConstantCriteria();
        checkModeGuards();
        checkTargetCompatibility();
    }

    private static void checkOrderedUtilities() {
        FuzzyInferenceSystem model = FuzzyModelFactory.createRevision();
        String[][] names = {
                {"Performance", "Low", "Medium", "High"},
                {"Interpretability", "Hard", "Moderate", "Easy"},
                {"Stability", "Unstable", "Moderate", "Stable"}
        };
        for (String[] terms : names) {
            MonotoneFuzzyUtility utility = new MonotoneFuzzyUtility(
                    model.getInputVariables().get(terms[0]), terms[1], terms[2], terms[3]);
            double endpoint = (0.5 * Math.exp(-0.25 / 0.045) + Math.exp(-0.64 / 0.045))
                    / (Math.exp(-0.04 / 0.045) + Math.exp(-0.25 / 0.045)
                    + Math.exp(-0.64 / 0.045));
            close(endpoint, utility.evaluate(0.0), "analytic low endpoint");
            close(1.0 - endpoint, utility.evaluate(1.0), "analytic high endpoint");
            close(0.5, utility.evaluate(0.5), "symmetric midpoint");
            require(utility.evaluate(0.0) > 0.0 && utility.evaluate(1.0) < 1.0,
                    "Gaussian ratio endpoints are not clipped");
            require(utility.evaluate(1.0) > utility.evaluate(0.8), "improvement beyond High center");
            double previous = utility.evaluate(0.0);
            for (int i = 1; i <= 10000; i++) {
                double z = i / 10000.0;
                double current = utility.evaluate(z);
                require(current > previous, "strict dense-grid monotonicity: " + terms[0] + " " + z);
                close(1.0, current + utility.evaluate(1.0 - z), "symmetric utility");
                close(directUtility(z, 0.15), current, "Gaussian ratio, not a replacement MF shape");
                previous = current;
            }
        }
        FuzzyVariable reordered = new FuzzyVariable("Performance", 0.0, 1.0);
        reordered.addMembershipFunction("High", 0.8, 0.15);
        reordered.addMembershipFunction("Low", 0.2, 0.15);
        reordered.addMembershipFunction("Medium", 0.5, 0.15);
        close(directUtility(0.7, 0.15), utility(reordered).evaluate(0.7),
                "term order is semantic, not insertion order");
    }

    private static void checkStableArithmeticAndGuards() {
        for (double width : new double[]{0.001, 1.0e-150}) {
            MonotoneFuzzyUtility utility = utility(performance(new double[]{0.2, 0.5, 0.8},
                    new double[]{width, width, width}, 0.0, 1.0));
            require(Math.exp(-0.5 * Math.pow((0.01 - 0.2) / width, 2)) == 0.0,
                    "direct memberships underflow in this fixture");
            close(0.0, utility.evaluate(0.01), "stable low utility with underflowed memberships");
            close(0.5, utility.evaluate(0.5), "stable central utility");
            close(1.0, utility.evaluate(0.99), "stable high utility with underflowed memberships");
            for (int i = 0; i <= 100; i++) {
                double value = utility.evaluate(i / 100.0);
                require(Double.isFinite(value) && value >= 0.0 && value <= 1.0,
                        "representable narrow-width ratio");
            }
        }
        for (double width : new double[]{Double.MIN_VALUE, 1.0e-160, Double.MAX_VALUE,
                0.0, -0.15, Double.NaN, Double.POSITIVE_INFINITY}) {
            fails(() -> utility(performance(new double[]{0.2, 0.5, 0.8},
                    new double[]{width, width, width}, 0.0, 1.0)), "invalid/unrepresentable width");
        }
        fails(() -> utility(performance(new double[]{0.2, 0.8, 0.5},
                widths(), 0.0, 1.0)), "unordered centers");
        fails(() -> utility(performance(new double[]{0.2, 0.5, 0.5},
                widths(), 0.0, 1.0)), "duplicate centers");
        fails(() -> utility(performance(new double[]{0.2, 0.5, 1.1},
                widths(), 0.0, 1.0)), "center outside domain");
        fails(() -> utility(performance(new double[]{0.2, 0.5, Double.NaN},
                widths(), 0.0, 1.0)), "nonfinite center");
        fails(() -> utility(performance(new double[]{0.2, 0.5, 0.8},
                new double[]{0.15, 0.16, 0.15}, 0.0, 1.0)), "unequal widths");
        fails(() -> utility(performance(new double[]{0.2, 0.5, 0.8},
                widths(), -0.1, 1.1)), "non-normalized model domain");
        MonotoneFuzzyUtility utility = utility(standardPerformance());
        for (double z : new double[]{-0.01, 1.01, Double.NaN, Double.NEGATIVE_INFINITY}) {
            fails(() -> utility.evaluate(z), "invalid desirability");
        }
        fails(() -> new MonotoneFuzzyUtility(null, "Low", "Medium", "High"), "null variable");
        fails(() -> new MonotoneFuzzyUtility(standardPerformance(), (String[]) null), "null term order");
        fails(() -> new MonotoneFuzzyUtility(standardPerformance(), "Low", "High"), "incomplete order");
        fails(() -> new MonotoneFuzzyUtility(standardPerformance(), "Low", "Low", "High"), "duplicate terms");
        fails(() -> new MonotoneFuzzyUtility(standardPerformance(), "Low", "Medium", "Missing"), "unknown term");
        fails(() -> new MonotoneFuzzyUtility(standardPerformance(), "High", "Medium", "Low"), "reversed order");
        FuzzyVariable extra = standardPerformance();
        extra.addMembershipFunction("Extra", 0.6, 0.15);
        fails(() -> utility(extra), "extra unordered term");
    }

    private static void checkProfiles() {
        Map<String, UserPreferenceVector> cases = UserPreferenceVector.importanceCases();
        require(new ArrayList<>(cases.keySet()).equals(Arrays.asList("C1", "C2", "C3", "C4", "C5", "C6")),
                "ordered C1-C6");
        double[][] expected = {{5, 1, 1}, {1, 1, 5}, {1, 5, 1}, {1, 1, 1}, {5, 1, 3}, {5, 1, 5}};
        int i = 0;
        for (UserPreferenceVector profile : cases.values()) {
            profile.validate();
            require(new ArrayList<>(profile.getPreferences().keySet()).equals(
                    Arrays.asList("Performance", "Interpretability", "Stability")), "P,I,S ordering");
            require("High".equals(profile.getPreferredTerm("Performance"))
                    && "Easy".equals(profile.getPreferredTerm("Interpretability"))
                    && "Stable".equals(profile.getPreferredTerm("Stability")), "beneficial labels");
            close(expected[i][0], profile.getWeight("Performance"), "P importance");
            close(expected[i][1], profile.getWeight("Interpretability"), "I importance");
            close(expected[i][2], profile.getWeight("Stability"), "S importance");
            i++;
        }
        fails(() -> cases.clear(), "immutable importance-case map");
        cases.get("C1").setPreference("Performance", "High", 99.0);
        close(5.0, UserPreferenceVector.importanceCases().get("C1").getWeight("Performance"), "fresh case vectors");
        close(1.0, cases.get("C2").getWeight("Performance"), "independent case vectors");
    }

    private static void checkImportanceUtilityAccess() {
        FuzzyHeuristicSelector selector = importanceSelector(FuzzyModelFactory.createRevision());
        selector.buildLibrary(values(0, 2, 10), values(0, 9.0 / 41.0, 9), values(0, 9.0 / 41.0, 9));
        Heuristic best = selector.getHeuristicLibrary().get(0);
        Map<String, Double> cached = selector.getImportanceUtilities(best);
        require(new ArrayList<>(cached.keySet()).equals(
                Arrays.asList("Performance", "Interpretability", "Stability")), "exported g keys in P,I,S order");
        for (Heuristic candidate : selector.getHeuristicLibrary()) {
            Map<String, Double> utilities = selector.getImportanceUtilities(candidate);
            for (Map.Entry<String, Double> entry : utilities.entrySet()) {
                close(directUtility(candidate.getNormalizedCriteria().get(entry.getKey()), 0.15),
                        entry.getValue(), "cached exported ordered-term utility");
            }
            for (UserPreferenceVector profile : UserPreferenceVector.importanceCases().values()) {
                double numerator = 0.0;
                double denominator = 0.0;
                for (Map.Entry<String, Double> entry : utilities.entrySet()) {
                    double weight = profile.getWeight(entry.getKey());
                    numerator += weight * entry.getValue();
                    denominator += weight;
                }
                close(numerator / denominator, selector.getPreferenceScore(candidate, profile),
                        "exported utilities reconstruct the recommendation score");
            }
        }
        fails(() -> cached.clear(), "utility map is read-only");
        fails(() -> cached.put("Performance", 0.0), "utility values cannot be replaced");
        fails(() -> cached.entrySet().iterator().next().setValue(0.0), "utility entries are read-only");
        fails(() -> selector.getImportanceUtilities(null), "null utility member");
        fails(() -> selector.getImportanceUtilities(new Heuristic(0, 0, 0, 0)), "foreign utility member");
        selector.buildLibrary(values(10, 0), values(9, 0), values(9, 0));
        fails(() -> selector.getImportanceUtilities(best), "stale utility member after rebuild");
        close(directUtility(1.0, 0.15), cached.get("Performance"), "exported snapshot survives rebuild");
        for (FuzzyHeuristicSelector target : Arrays.asList(
                new FuzzyHeuristicSelector(FuzzyModelFactory.createLegacy()),
                new FuzzyHeuristicSelector(FuzzyModelFactory.createRevision(),
                        FuzzyHeuristicSelector.NormalizationMode.MIN_MAX_ALL))) {
            target.buildLibrary(values(0, 10), values(0, 9), values(0, 9));
            fails(() -> target.getImportanceUtilities(target.getHeuristicLibrary().get(0)),
                    "target mode does not expose importance utilities");
        }
    }

    private static void checkDominanceAndScaling() {
        FuzzyHeuristicSelector selector = importanceSelector(FuzzyModelFactory.createRevision());
        // Raw minimization metrics yield z=(1,1,1), (0.8,0.8,0.8), (0,0,0).
        selector.buildLibrary(values(0, 2, 10), values(0, 9.0 / 41.0, 9), values(0, 9.0 / 41.0, 9));
        Heuristic best = selector.getHeuristicLibrary().get(0);
        Heuristic dominated = selector.getHeuristicLibrary().get(1);
        for (String variable : Arrays.asList("Performance", "Interpretability", "Stability")) {
            close(1.0, best.getNormalizedCriteria().get(variable), "beneficial raw minimum");
            close(0.8, dominated.getNormalizedCriteria().get(variable), "interior normalized desirability");
        }
        for (UserPreferenceVector profile : UserPreferenceVector.importanceCases().values()) {
            require(ids(selector.selectByPreference(profile, 3)).equals(Arrays.asList(0, 1, 2)),
                    "all six profiles preserve strict dominance, including 0.8 to 1");
            for (Heuristic candidate : selector.getHeuristicLibrary()) {
                double score = selector.getPreferenceScore(candidate, profile);
                close(score, selector.getPreferenceSimilarity(candidate, profile), "score alias");
                close(expectedScore(candidate, profile), score, "weighted ordered-term formula");
                for (double scale : new double[]{1.0e200, 1.0e-200}) {
                    UserPreferenceVector scaled = rescale(profile, scale);
                    close(score, selector.getPreferenceScore(candidate, scaled), "positive weight rescaling");
                    require(ids(selector.selectByPreference(profile, 3)).equals(
                            ids(selector.selectByPreference(scaled, 3))), "rescaled ranking");
                }
            }
        }
        UserPreferenceVector equal = UserPreferenceVector.importanceCases().get("C4");
        double cached = selector.getPreferenceScore(best, equal);
        best.addFuzzyMembership("Performance_High", 0.0);
        best.addFuzzyMembership("Interpretability_Easy", 0.0);
        best.addFuzzyMembership("Stability_Stable", 0.0);
        selector.getHeuristicLibrary().get(2).setSelectionIndex(Double.MAX_VALUE);
        close(cached, selector.getPreferenceScore(best, equal), "cached utilities ignore diagnostic memberships");
        require(selector.selectByPreference(equal, 1).get(0).getId() == 0, "recommendation ignores SI");

        selector.buildLibrary(values(10, 0), values(9, 0), values(9, 0));
        require(selector.selectByPreference(equal, 1).get(0).getId() == 1, "cache rebuilt with library");
        fails(() -> selector.getPreferenceScore(best, equal), "old library member rejected");

        // Trade-offs distinguish the priority profiles rather than just checking a dominance chain.
        selector.buildLibrary(values(0, 10, 10), values(9, 9, 0), values(9, 0, 9));
        List<List<Integer>> winners = Arrays.asList(Arrays.asList(0), Arrays.asList(1), Arrays.asList(2),
                Arrays.asList(0, 1, 2), Arrays.asList(0), Arrays.asList(0, 1));
        int index = 0;
        for (UserPreferenceVector profile : UserPreferenceVector.importanceCases().values()) {
            require(winners.get(index++).contains(selector.selectByPreference(profile, 1).get(0).getId()),
                    "priority-specific trade-off, allowing roundoff in mathematical ties");
        }
        for (String variable : Arrays.asList("Performance", "Interpretability", "Stability")) {
            selector.buildLibrary("Performance".equals(variable) ? values(0, 10) : values(1, 1),
                    "Interpretability".equals(variable) ? values(0, 9) : values(1, 1),
                    "Stability".equals(variable) ? values(0, 9) : values(1, 1));
            for (UserPreferenceVector profile : UserPreferenceVector.importanceCases().values()) {
                require(selector.getPreferenceScore(selector.getHeuristicLibrary().get(0), profile)
                        > selector.getPreferenceScore(selector.getHeuristicLibrary().get(1), profile),
                        "strict single-criterion dominance: " + variable);
            }
        }
    }

    private static void checkZeroAndConstantCriteria() {
        FuzzyHeuristicSelector selector = importanceSelector(FuzzyModelFactory.createRevision());
        selector.buildLibrary(values(10, 10), values(3, 3), values(4, 4));
        for (UserPreferenceVector profile : UserPreferenceVector.importanceCases().values()) {
            for (Heuristic candidate : selector.getHeuristicLibrary()) {
                for (double z : candidate.getNormalizedCriteria().values()) {
                    close(0.5, z, "constant criterion desirability");
                }
                close(0.5, selector.getPreferenceScore(candidate, profile), "constant criteria utility");
            }
            require(ids(selector.selectByPreference(profile, 2)).equals(Arrays.asList(0, 1)),
                    "constant scores use deterministic IDs");
        }
        selector.buildLibrary(values(0, 10), values(3, 3), values(4, 4));
        UserPreferenceVector ignorePerformance = profile(0.0, 1.0, 0.0);
        for (Heuristic candidate : selector.getHeuristicLibrary()) {
            close(0.5, selector.getPreferenceScore(candidate, ignorePerformance), "zero-weight criterion ignored");
        }
        require(selector.getPreferenceScore(selector.getHeuristicLibrary().get(0), profile(1, 0, 0))
                > selector.getPreferenceScore(selector.getHeuristicLibrary().get(1), profile(1, 0, 0)),
                "one positive criterion supports strict dominance");
        close(selector.getPreferenceScore(selector.getHeuristicLibrary().get(0), profile(1, 0, 0)),
                selector.getPreferenceScore(selector.getHeuristicLibrary().get(0),
                        new UserPreferenceVector().setPreference("Performance", "High", 1.0)),
                "unspecified criteria may be omitted");
        close(0.5, selector.getPreferenceScore(selector.getHeuristicLibrary().get(0),
                profile(0, Double.MIN_VALUE, 0)), "subnormal positive weight");
        close(selector.getPreferenceScore(selector.getHeuristicLibrary().get(0), profile(1, 1, 1)),
                selector.getPreferenceScore(selector.getHeuristicLibrary().get(0),
                        profile(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)),
                "finite huge weights do not overflow sum");
        fails(() -> selector.selectByPreference(profile(0, 0, 0), 1), "all zero weights");
        fails(() -> selector.selectByPreference(new UserPreferenceVector(), 1), "empty preferences");
        fails(() -> profile(-1, 1, 1), "negative weight");
        fails(() -> profile(Double.NaN, 1, 1), "nonfinite weight");
        selector.buildLibrary(values(0), values(0), values(0));
        close(0.5, selector.getPreferenceScore(selector.getHeuristicLibrary().get(0), profile(5, 1, 5)),
                "singleton and zero-valued raw metrics");
    }

    private static void checkModeGuards() {
        fails(() -> new FuzzyHeuristicSelector(FuzzyModelFactory.createRevision(),
                FuzzyHeuristicSelector.NormalizationMode.LEGACY_RECIPROCAL,
                FuzzyHeuristicSelector.PreferenceMode.MONOTONE_IMPORTANCE), "importance normalization guard");
        fails(() -> new FuzzyHeuristicSelector(FuzzyModelFactory.createRevision(),
                FuzzyHeuristicSelector.NormalizationMode.MIN_MAX_ALL, null), "null preference mode");
        fails(() -> importanceSelector(modelWithPerformance(performance(new double[]{0.2, 0.8, 0.5},
                widths(), 0.0, 1.0))), "selector rejects unordered centers");
        fails(() -> importanceSelector(modelWithPerformance(performance(new double[]{0.2, 0.5, 0.8},
                new double[]{0.15, 0.16, 0.15}, 0.0, 1.0))), "selector rejects unequal widths");
        fails(() -> importanceSelector(modelWithPerformance(performance(new double[]{0.2, 0.5, 0.8},
                widths(), -0.1, 1.1))), "selector rejects non-normalized domain");
        FuzzyHeuristicSelector selector = importanceSelector(FuzzyModelFactory.createRevision());
        selector.buildLibrary(values(0, 10), values(0, 9), values(0, 9));
        for (String[] target : new String[][]{
                {"Performance", "Low"}, {"Performance", "Medium"},
                {"Interpretability", "Hard"}, {"Interpretability", "Moderate"},
                {"Stability", "Unstable"}, {"Stability", "Moderate"}}) {
            for (double weight : new double[]{0.0, 1.0}) {
                UserPreferenceVector invalid = profile(1, 1, 1).setPreference(target[0], target[1], weight);
                fails(() -> selector.selectByPreference(invalid, 1), "reject adverse/interior target label");
                fails(() -> selector.getPreferenceSimilarity(selector.getHeuristicLibrary().get(0), invalid),
                        "score rejects target label too");
            }
        }
        fails(() -> selector.selectByPreference(new UserPreferenceVector()
                .setPreference("Unknown", "High"), 1), "unknown criterion");
        fails(() -> selector.selectByPreference(new UserPreferenceVector()
                .setPreference("Performance", "Unknown"), 1), "unknown label");
        fails(() -> selector.getPreferenceScore(selector.getHeuristicLibrary().get(0), null), "null preferences");
    }

    private static void checkTargetCompatibility() {
        FuzzyHeuristicSelector oneArgument = new FuzzyHeuristicSelector(FuzzyModelFactory.createLegacy());
        FuzzyHeuristicSelector explicitLegacy = new FuzzyHeuristicSelector(FuzzyModelFactory.createLegacy(),
                FuzzyHeuristicSelector.NormalizationMode.LEGACY_RECIPROCAL,
                FuzzyHeuristicSelector.PreferenceMode.TARGET_MATCH);
        require(oneArgument.getNormalizationMode() == FuzzyHeuristicSelector.NormalizationMode.LEGACY_RECIPROCAL
                && oneArgument.getPreferenceMode() == FuzzyHeuristicSelector.PreferenceMode.TARGET_MATCH,
                "one-argument constructor keeps historical defaults");
        FuzzyHeuristicSelector twoArguments = new FuzzyHeuristicSelector(FuzzyModelFactory.createRevision(),
                FuzzyHeuristicSelector.NormalizationMode.MIN_MAX_ALL);
        FuzzyHeuristicSelector explicitTarget = new FuzzyHeuristicSelector(FuzzyModelFactory.createRevision(),
                FuzzyHeuristicSelector.NormalizationMode.MIN_MAX_ALL,
                FuzzyHeuristicSelector.PreferenceMode.TARGET_MATCH);
        require(twoArguments.getPreferenceMode() == FuzzyHeuristicSelector.PreferenceMode.TARGET_MATCH,
                "two-argument constructor keeps target semantics");
        for (FuzzyHeuristicSelector selector : Arrays.asList(oneArgument, explicitLegacy, twoArguments, explicitTarget)) {
            selector.buildLibrary(values(0, 2, 10), values(0, 9.0 / 41.0, 9), values(0, 9.0 / 41.0, 9));
            UserPreferenceVector highTarget = new UserPreferenceVector().setPreference("Performance", "High");
            require(selector.selectByPreference(highTarget, 1).get(0).getId() == 1,
                    "legacy target still peaks at 0.8 rather than at 1");
            for (Map<String, UserPreferenceVector> cases : Arrays.asList(
                    UserPreferenceVector.legacyCases(), UserPreferenceVector.revisionCases())) {
                for (UserPreferenceVector target : cases.values()) {
                    for (Heuristic candidate : selector.getHeuristicLibrary()) {
                        double numerator = 0.0;
                        double denominator = 0.0;
                        for (Map.Entry<String, String> entry : target.getPreferences().entrySet()) {
                            double weight = target.getWeight(entry.getKey());
                            numerator += weight * candidate.getFuzzyProfile().get(entry.getKey() + "_" + entry.getValue());
                            denominator += weight;
                        }
                        close(numerator / denominator, selector.getPreferenceSimilarity(candidate, target),
                                "historical weighted target formula");
                    }
                }
            }
        }
        UserPreferenceVector target = UserPreferenceVector.case5_HighPerformanceModerateStability();
        for (int i = 0; i < 3; i++) {
            close(oneArgument.getPreferenceScore(oneArgument.getHeuristicLibrary().get(i), target),
                    explicitLegacy.getPreferenceScore(explicitLegacy.getHeuristicLibrary().get(i), target),
                    "one-argument constructor compatibility");
            close(twoArguments.getPreferenceScore(twoArguments.getHeuristicLibrary().get(i), target),
                    explicitTarget.getPreferenceScore(explicitTarget.getHeuristicLibrary().get(i), target),
                    "two-argument constructor compatibility");
        }
        require("Moderate".equals(UserPreferenceVector.revisionCases().get("C5").getPreferredTerm("Stability"))
                && "Hard".equals(UserPreferenceVector.revisionCases().get("C5").getPreferredTerm("Interpretability")),
                "old paper-v2 C5 stays a target diagnostic");
        require("Unstable".equals(UserPreferenceVector.legacyCases().get("C5").getPreferredTerm("Stability"))
                && "Easy".equals(UserPreferenceVector.legacyCases().get("C5").getPreferredTerm("Interpretability")),
                "historical legacy C5 preserved");
    }

    private static FuzzyHeuristicSelector importanceSelector(FuzzyInferenceSystem model) {
        return new FuzzyHeuristicSelector(model, FuzzyHeuristicSelector.NormalizationMode.MIN_MAX_ALL,
                FuzzyHeuristicSelector.PreferenceMode.MONOTONE_IMPORTANCE);
    }

    private static FuzzyInferenceSystem modelWithPerformance(FuzzyVariable performance) {
        FuzzyInferenceSystem baseline = FuzzyModelFactory.createRevision();
        FuzzyInferenceSystem model = new FuzzyInferenceSystem();
        model.addInputVariable(performance);
        model.addInputVariable(baseline.getInputVariables().get("Interpretability"));
        model.addInputVariable(baseline.getInputVariables().get("Stability"));
        model.setOutputVariable(baseline.getOutputVariable());
        baseline.getRules().forEach(model::addRule);
        return model;
    }

    private static FuzzyVariable performance(double[] centers, double[] sigmas, double min, double max) {
        FuzzyVariable variable = new FuzzyVariable("Performance", min, max);
        String[] terms = {"Low", "Medium", "High"};
        for (int i = 0; i < terms.length; i++) {
            variable.addMembershipFunction(terms[i], centers[i], sigmas[i]);
        }
        return variable;
    }

    private static FuzzyVariable standardPerformance() {
        return performance(new double[]{0.2, 0.5, 0.8}, widths(), 0.0, 1.0);
    }

    private static double[] widths() {
        return new double[]{0.15, 0.15, 0.15};
    }

    private static MonotoneFuzzyUtility utility(FuzzyVariable variable) {
        return new MonotoneFuzzyUtility(variable, "Low", "Medium", "High");
    }

    private static double directUtility(double z, double sigma) {
        double low = Math.exp(-0.5 * Math.pow((z - 0.2) / sigma, 2));
        double medium = Math.exp(-0.5 * Math.pow((z - 0.5) / sigma, 2));
        double high = Math.exp(-0.5 * Math.pow((z - 0.8) / sigma, 2));
        return (0.5 * medium + high) / (low + medium + high);
    }

    private static double expectedScore(Heuristic candidate, UserPreferenceVector profile) {
        double numerator = 0.0;
        double denominator = 0.0;
        for (Map.Entry<String, Double> criterion : candidate.getNormalizedCriteria().entrySet()) {
            double weight = profile.getWeight(criterion.getKey());
            numerator += weight * directUtility(criterion.getValue(), 0.15);
            denominator += weight;
        }
        return numerator / denominator;
    }

    private static UserPreferenceVector profile(double performance, double interpretability, double stability) {
        return new UserPreferenceVector().setPreference("Performance", "High", performance)
                .setPreference("Interpretability", "Easy", interpretability)
                .setPreference("Stability", "Stable", stability);
    }

    private static UserPreferenceVector rescale(UserPreferenceVector source, double scale) {
        UserPreferenceVector copy = new UserPreferenceVector();
        for (Map.Entry<String, String> entry : source.getPreferences().entrySet()) {
            copy.setPreference(entry.getKey(), entry.getValue(), source.getWeight(entry.getKey()) * scale);
        }
        return copy;
    }

    private static List<Double> values(double... values) {
        List<Double> result = new ArrayList<>();
        for (double value : values) {
            result.add(value);
        }
        return result;
    }

    private static List<Integer> ids(List<Heuristic> heuristics) {
        List<Integer> result = new ArrayList<>();
        for (Heuristic heuristic : heuristics) {
            result.add(heuristic.getId());
        }
        return result;
    }

    private static void close(double expected, double actual, String message) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) <= 1.0e-12,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void fails(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }
}
