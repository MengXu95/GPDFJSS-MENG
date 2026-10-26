package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import ec.EvolutionState;
import ec.Problem;
import ec.gp.ADFStack;
import ec.gp.GPData;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyHeuristicSelector;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyInferenceSystem;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyMembershipFunction;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyRule;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyVariable;
import mengxu.algorithm.MAPElites.FuzzySystem.Heuristic;
import mengxu.algorithm.MAPElites.FuzzySystem.TreeComplexityCalculator;
import mengxu.algorithm.MAPElites.FuzzySystem.UserPreferenceVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Dependency-free assertions invoked by RevisionChecks; no simulator or file output. */
public final class FuzzyCoreChecks {
    private FuzzyCoreChecks() {
    }

    public static void runChecks() {
        checkComplexity();
        checkModels();
        checkNormalizationAndRanking();
        checkPreferences();
        checkFailures();
    }

    private static void checkComplexity() {
        TreeComplexityCalculator calculator = new TreeComplexityCalculator();
        close(6.0, calculator.calculateStructuralComplexity(node("Max", node("PT"), node("WIQ"))), "Max");
        close(6.0, calculator.calculateStructuralComplexity(node("Min", node("PT"), node("WIQ"))), "Min");
        close(6.0, calculator.calculateStructuralComplexity(node("mAx", node("PT"), node("WIQ"))), "case");
        close(4.0, calculator.calculateStructuralComplexity(node("+", node("PT"), node("PT"))), "repetition");
        close(4.0, calculator.calculateStructuralComplexity(node("-", node("PT"), node("2.5"))), "minus");
        close(5.0, calculator.calculateStructuralComplexity(node("*", node("PT"), node("-2.5"))), "multiply");
        close(5.0, calculator.calculateStructuralComplexity(node("/", node("PT"), node("WIQ"))), "divide");
        close(9.0, calculator.calculateStructuralComplexity(
                node("+", node("Max", node("PT"), node("PT")), node("3"))), "nested");
        fails(() -> calculator.calculateStructuralComplexity(node("sin", node("PT"))), "unknown operator");
        fails(() -> calculator.calculateStructuralComplexity(null), "null tree");
    }

    private static void checkModels() {
        FuzzyInferenceSystem legacy = FuzzyModelFactory.createLegacy();
        FuzzyInferenceSystem revised = FuzzyModelFactory.createRevision();
        close(200.0, legacy.getOutputVariable().getMaxValue(), "legacy domain");
        close(100.0, revised.getOutputVariable().getMaxValue(), "revision domain");
        require(legacy.getRules().size() == 10 && revised.getRules().size() == 10, "ten rules");
        for (int i = 0; i < legacy.getRules().size(); i++) {
            require(legacy.getRules().get(i).toString().equals(revised.getRules().get(i).toString()),
                    "identical rule " + i);
        }
        String[] expectedRules = {
                "High|Stable|Moderate|VerySuitable", "High|Stable|Easy|VerySuitable",
                "Medium|Stable|Easy|VerySuitable", "High|Moderate|null|Suitable",
                "Medium|Moderate|Easy|Suitable", "Medium|Stable|Moderate|Suitable",
                "High|Unstable|Easy|Suitable", "Low|Unstable|null|Unsuitable",
                "Low|Moderate|null|Unsuitable", "Medium|Unstable|Hard|Unsuitable"
        };
        for (int i = 0; i < expectedRules.length; i++) {
            FuzzyRule rule = legacy.getRules().get(i);
            String signature = rule.getAntecedents().get("Performance") + "|"
                    + rule.getAntecedents().get("Stability") + "|"
                    + rule.getAntecedents().get("Interpretability") + "|" + rule.getConsequentTerm();
            require(expectedRules[i].equals(signature), "paper rule " + i);
        }
        for (FuzzyVariable variable : revised.getInputVariables().values()) {
            double[] centers = {0.2, 0.5, 0.8};
            for (int i = 0; i < centers.length; i++) {
                FuzzyMembershipFunction mf = variable.getMembershipFunctions().get(i);
                close(centers[i], mf.getCenter(), "input center");
                close(0.15, mf.getSigma(), "input sigma");
                close(1.0, mf.getMembership(centers[i]), "peak membership");
            }
        }
        Map<String, Double> inputs = inputs(0.8, 0.8, 0.8);
        double si = revised.infer(inputs);
        require(si > 50.0 && si < 100.0, "finite revised SI");
        close(si, revised.inferWithDetails(inputs).output, "detailed inference");
        require(Math.abs(legacy.infer(inputs) - si) > 0.01, "domain affects centroid");
        fails(() -> revised.getRules().clear(), "read-only rules");
        fails(() -> revised.getInputVariables().clear(), "read-only variables");
    }

    private static void checkNormalizationAndRanking() {
        FuzzyHeuristicSelector revised = selector(true);
        revised.buildLibrary(values(10, 20, 30), values(1, 3, 7), values(0, 1, 3));
        double[][] expected = {{1, 1, 1}, {0.5, 1.0 / 3.0, 1.0 / 3.0}, {0, 0, 0}};
        for (int i = 0; i < 3; i++) {
            Heuristic heuristic = revised.getHeuristicLibrary().get(i);
            close(expected[i][0], heuristic.getNormalizedCriteria().get("Performance"), "minmax performance");
            close(expected[i][1], heuristic.getNormalizedCriteria().get("Stability"), "minmax stability");
            close(expected[i][2], heuristic.getNormalizedCriteria().get("Interpretability"), "minmax ease");
            close(10.0 * (i + 1), heuristic.getPerformance(), "raw performance retained");
            close(values(1, 3, 7).get(i), heuristic.getStructuralComplexity(), "raw complexity retained");
            close(values(0, 1, 3).get(i), heuristic.getBehavioralSensitivity(), "raw sensitivity retained");
        }
        UserPreferenceVector low = new UserPreferenceVector()
                .setPreference("Performance", "Low")
                .setPreference("Stability", "Unstable")
                .setPreference("Interpretability", "Hard");
        // Low/Unstable/Hard peak at 0.2, not at the normalized boundary 0.
        double[] expectedLowScores = {
                Math.exp(-128.0 / 9.0),
                (Math.exp(-2.0) + 2.0 * Math.exp(-32.0 / 81.0)) / 3.0,
                Math.exp(-8.0 / 9.0)
        };
        for (int i = 0; i < expectedLowScores.length; i++) {
            close(expectedLowScores[i], revised.getPreferenceSimilarity(
                    revised.getHeuristicLibrary().get(i), low), "analytic low-target score " + i);
        }
        require(ids(revised.selectByPreference(low, 3)).equals(Arrays.asList(1, 2, 0)),
                "Gaussian target preference, not monotonic raw-criterion ranking");
        require(revised.selectBySelectionIndex(1).get(0).getId() == 0, "SI remains separate");
        FuzzyHeuristicSelector endpoints = selector(true);
        endpoints.buildLibrary(values(10, 30), values(1, 7), values(0, 3));
        require(endpoints.selectByPreference(low, 1).get(0).getId() == 1, "low-target endpoint preference");
        require(endpoints.selectBySelectionIndex(1).get(0).getId() == 0, "endpoint SI remains separate");
        Heuristic candidate = revised.getHeuristicLibrary().get(1);
        UserPreferenceVector c5 = UserPreferenceVector.case5_HighPerformanceModerateStability();
        double expectedC5 = (5 * candidate.getFuzzyProfile().get("Performance_High")
                + 3 * candidate.getFuzzyProfile().get("Stability_Moderate")
                + candidate.getFuzzyProfile().get("Interpretability_Hard")) / 9.0;
        close(expectedC5, revised.getPreferenceSimilarity(candidate, c5), "weighted target membership");

        FuzzyHeuristicSelector legacy = selector(false);
        legacy.buildLibrary(values(10, 20, 30), values(1, 3, 7), values(0, 1, 3));
        close(0.25, legacy.getHeuristicLibrary().get(1).getNormalizedCriteria().get("Interpretability"),
                "legacy reciprocal complexity");
        close(0.5, legacy.getHeuristicLibrary().get(1).getNormalizedCriteria().get("Stability"),
                "legacy reciprocal sensitivity");
        close(0.5, legacy.getHeuristicLibrary().get(1).getNormalizedCriteria().get("Performance"),
                "legacy inverted performance");
        revised.buildLibrary(values(7, 7, 7), values(4, 4, 4), values(2, 2, 2));
        for (Heuristic heuristic : revised.getHeuristicLibrary()) {
            for (double value : heuristic.getNormalizedCriteria().values()) {
                close(0.5, value, "constant criterion neutral");
            }
        }
        require(ids(revised.selectByPreference(c5, 3)).equals(Arrays.asList(0, 1, 2)), "preference ties");
        require(ids(revised.selectBySelectionIndex(3)).equals(Arrays.asList(0, 1, 2)), "SI ties");
        legacy.buildLibrary(values(7, 7), values(4, 4), values(2, 2));
        close(1.0, legacy.getHeuristicLibrary().get(0).getNormalizedCriteria().get("Performance"),
                "legacy constant performance retained");
        close(0.2, legacy.getHeuristicLibrary().get(0).getNormalizedCriteria().get("Interpretability"),
                "legacy constant reciprocal retained");
        fails(() -> revised.getHeuristicLibrary().clear(), "read-only library");
        fails(() -> candidate.getNormalizedCriteria().clear(), "read-only criteria");
    }

    private static void checkPreferences() {
        Map<String, UserPreferenceVector> legacy = UserPreferenceVector.legacyCases();
        Map<String, UserPreferenceVector> revised = UserPreferenceVector.revisionCases();
        require(new ArrayList<>(revised.keySet()).equals(Arrays.asList("C1", "C2", "C3", "C4", "C5", "C6")),
                "ordered revision cases");
        for (String id : revised.keySet()) {
            if (!id.equals("C5")) {
                require(legacy.get(id).getPreferences().equals(revised.get(id).getPreferences()), "retained " + id);
                require(legacy.get(id).getWeights().equals(revised.get(id).getWeights()), "retained weights " + id);
            }
        }
        UserPreferenceVector c5 = revised.get("C5");
        require("High".equals(c5.getPreferredTerm("Performance"))
                && "Moderate".equals(c5.getPreferredTerm("Stability"))
                && "Hard".equals(c5.getPreferredTerm("Interpretability")), "revision C5 targets");
        close(5, c5.getWeight("Performance"), "C5 performance importance");
        close(3, c5.getWeight("Stability"), "C5 stability importance");
        close(1, c5.getWeight("Interpretability"), "C5 interpretability importance");
        require("Easy".equals(legacy.get("C5").getPreferredTerm("Interpretability")), "legacy C5 retained");
        Map<String, String> snapshot = c5.getPreferences();
        c5.setPreference("Performance", "Low");
        require("High".equals(snapshot.get("Performance")), "defensive preference snapshot");
        require("High".equals(UserPreferenceVector.revisionCases().get("C5").getPreferredTerm("Performance")),
                "fresh cases");
        fails(() -> revised.clear(), "read-only cases");
        fails(() -> c5.getPreferences().clear(), "read-only preferences");
        fails(() -> c5.getWeights().clear(), "read-only weights");
    }

    private static void checkFailures() {
        FuzzyHeuristicSelector selector = selector(true);
        fails(() -> selector.selectBySelectionIndex(1), "unbuilt library");
        fails(() -> selector.buildLibrary(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                "empty metrics");
        fails(() -> selector.buildLibrary(null, values(1), values(1)), "null metrics");
        fails(() -> selector.buildLibrary(values(1, 2), values(1), values(1)), "unequal metrics");
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            fails(() -> selector.buildLibrary(values(invalid), values(1), values(1)), "invalid performance");
            fails(() -> selector.buildLibrary(values(1), values(invalid), values(1)), "invalid complexity");
            fails(() -> selector.buildLibrary(values(1), values(1), values(invalid)), "invalid sensitivity");
            fails(() -> new Heuristic(1, invalid, 1, 1), "invalid raw heuristic");
            fails(() -> new UserPreferenceVector().setPreference("Performance", "High", invalid), "invalid weight");
        }
        fails(() -> selector.buildLibrary(Arrays.asList((Double) null), values(1), values(1)), "null metric");
        selector.buildLibrary(values(1, 2), values(1, 2), values(1, 2));
        for (int k : new int[]{-1, 0, 3}) {
            fails(() -> selector.selectBySelectionIndex(k), "invalid SI k");
            fails(() -> selector.selectByPreference(UserPreferenceVector.balanced(), k), "invalid preference k");
            fails(() -> selector.selectRandomly(k, new Random(1)), "invalid random k");
        }
        fails(() -> selector.selectRandomly(1, null), "null random");
        fails(() -> selector.selectByPreference(null, 1), "null preference");
        fails(() -> selector.selectByPreference(new UserPreferenceVector(), 1), "empty preference");
        fails(() -> selector.selectByPreference(
                new UserPreferenceVector().setPreference("Performance", "High", 0), 1), "zero importance");
        fails(() -> selector.selectByPreference(
                new UserPreferenceVector().setPreference("Performance", "Typo"), 1), "unknown preference term");
        fails(() -> selector.selectByPreference(
                new UserPreferenceVector().setPreference("Typo", "High"), 1), "unknown preference variable");
        fails(() -> new UserPreferenceVector().setPreference("", "High"), "empty variable");
        fails(() -> new UserPreferenceVector().setPreference("Performance", ""), "empty term");
        fails(() -> new FuzzyMembershipFunction("X", 0.5, 0), "zero sigma");
        fails(() -> new FuzzyMembershipFunction("X", 0.5, -1), "negative sigma");
        fails(() -> new FuzzyMembershipFunction("X", Double.NaN, 1), "NaN center");
        fails(() -> new FuzzyMembershipFunction("X", 0.5, Double.NaN), "NaN sigma");
        fails(() -> new FuzzyMembershipFunction("X", 0.5, Double.POSITIVE_INFINITY), "infinite sigma");
        fails(() -> new FuzzyMembershipFunction("X", 0.5, 1).getMembership(Double.NaN), "NaN membership");
        fails(() -> new FuzzyVariable("X", 1, 1), "zero domain");
        fails(() -> new FuzzyVariable("X", 1, 0), "reversed domain");
        fails(() -> new FuzzyVariable("X", 0, Double.POSITIVE_INFINITY), "infinite domain");
        fails(() -> new FuzzyVariable("X", 0, 1).addMembershipFunction("X", 2, 1), "center outside domain");
        fails(() -> new FuzzyVariable("X", 0, 1).fuzzify(0.5), "empty variable");
        FuzzyInferenceSystem model = FuzzyModelFactory.createRevision();
        fails(() -> new FuzzyInferenceSystem().infer(Collections.emptyMap()), "empty inference");
        fails(() -> model.infer(Collections.singletonMap("Performance", 0.5)), "missing input");
        fails(() -> model.infer(inputs(Double.NaN, 0.5, 0.5)), "NaN input");
        fails(() -> model.infer(inputs(-1, 0.5, 0.5)), "outside domain");
        Map<String, Double> extra = inputs(0.5, 0.5, 0.5);
        extra.put("Typo", 0.5);
        fails(() -> model.infer(extra), "extra input");
        fails(() -> model.getInputVariables().get("Performance").getMembership(0.5, "Typo"), "unknown term");
        fails(() -> new FuzzyRule().setWeight(Double.NaN), "NaN rule weight");
        fails(() -> new FuzzyRule().setWeight(2), "excessive rule weight");
        fails(() -> new FuzzyRule().setWeight(-1), "negative rule weight");
        fails(() -> model.addRule(new FuzzyRule()), "incomplete rule");
        fails(() -> new FuzzyRule().addAntecedent("Performance", "High")
                .setConsequent("SelectionIndex", "Suitable").evaluate(Collections.emptyMap()), "missing rule input");
        FuzzyInferenceSystem badRule = FuzzyModelFactory.createRevision();
        badRule.addRule(new FuzzyRule().addAntecedent("Performance", "Typo")
                .setConsequent("SelectionIndex", "Suitable"));
        fails(() -> badRule.infer(inputs(0.5, 0.5, 0.5)), "unknown antecedent");
        FuzzyInferenceSystem badOutput = FuzzyModelFactory.createRevision();
        badOutput.getRules().get(0).setConsequent("Typo", "Suitable");
        fails(() -> badOutput.infer(inputs(0.5, 0.5, 0.5)), "unknown consequent variable");
        FuzzyInferenceSystem badTerm = FuzzyModelFactory.createRevision();
        badTerm.getRules().get(0).setConsequent("SelectionIndex", "Typo");
        fails(() -> badTerm.infer(inputs(0.5, 0.5, 0.5)), "unknown consequent term");
        FuzzyInferenceSystem noFiring = FuzzyModelFactory.createRevision();
        for (FuzzyRule rule : noFiring.getRules()) {
            rule.setWeight(0);
        }
        fails(() -> noFiring.infer(inputs(0.5, 0.5, 0.5)), "no successful midpoint fallback");
        fails(() -> new FuzzyHeuristicSelector(null), "null selector engine");
        fails(() -> new FuzzyHeuristicSelector(new FuzzyInferenceSystem()), "invalid selector engine");
        FuzzyInferenceSystem missing = new FuzzyInferenceSystem();
        FuzzyVariable input = new FuzzyVariable("Performance", 0, 1);
        input.addMembershipFunction("High", 0.8, 0.15);
        missing.addInputVariable(input);
        FuzzyVariable output = new FuzzyVariable("SelectionIndex", 0, 100);
        output.addMembershipFunction("Suitable", 50, 15);
        missing.setOutputVariable(output);
        missing.addRule(new FuzzyRule().addAntecedent("Performance", "High")
                .setConsequent("SelectionIndex", "Suitable"));
        fails(() -> new FuzzyHeuristicSelector(missing), "missing selector variables");
        FuzzyInferenceSystem missingTerm = FuzzyModelFactory.createRevision();
        FuzzyVariable missingHigh = new FuzzyVariable("Performance", 0, 1);
        missingHigh.addMembershipFunction("Low", 0.2, 0.15);
        missingHigh.addMembershipFunction("Medium", 0.5, 0.15);
        // A separate valid engine isolates selector term validation from invalid rule references.
        FuzzyInferenceSystem incompleteTerms = new FuzzyInferenceSystem();
        incompleteTerms.addInputVariable(missingHigh);
        incompleteTerms.addInputVariable(missingTerm.getInputVariables().get("Stability"));
        incompleteTerms.addInputVariable(missingTerm.getInputVariables().get("Interpretability"));
        incompleteTerms.setOutputVariable(missingTerm.getOutputVariable());
        incompleteTerms.addRule(new FuzzyRule().addAntecedent("Performance", "Low")
                .setConsequent("SelectionIndex", "Suitable"));
        fails(() -> new FuzzyHeuristicSelector(incompleteTerms), "missing selector term");
    }

    private static FuzzyHeuristicSelector selector(boolean revised) {
        return revised ? new FuzzyHeuristicSelector(FuzzyModelFactory.createRevision(),
                FuzzyHeuristicSelector.NormalizationMode.MIN_MAX_ALL)
                : new FuzzyHeuristicSelector(FuzzyModelFactory.createLegacy());
    }

    private static List<Double> values(double... values) {
        List<Double> result = new ArrayList<>();
        for (double value : values) {
            result.add(value);
        }
        return result;
    }

    private static Map<String, Double> inputs(double performance, double stability, double ease) {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("Performance", performance);
        result.put("Stability", stability);
        result.put("Interpretability", ease);
        return result;
    }

    private static List<Integer> ids(List<Heuristic> heuristics) {
        List<Integer> result = new ArrayList<>();
        for (Heuristic heuristic : heuristics) {
            result.add(heuristic.getId());
        }
        return result;
    }

    private static void close(double expected, double actual, String label) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) < 1e-10,
                label + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Fuzzy core check failed: " + label);
        }
    }

    private static void fails(Runnable operation, String label) {
        try {
            operation.run();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Fuzzy core check failed to reject: " + label);
    }

    private static GPNode node(String name, GPNode... children) {
        return new CheckNode(name, children);
    }

    private static final class CheckNode extends GPNode {
        private final String label;

        private CheckNode(String label, GPNode[] children) {
            this.label = label;
            this.children = children;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public void eval(EvolutionState state, int thread, GPData input, ADFStack stack,
                         GPIndividual individual, Problem problem) {
            throw new UnsupportedOperationException("Structural test node is not executable");
        }
    }
}
