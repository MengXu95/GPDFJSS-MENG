package mengxu.algorithm.MAPElites.FuzzySystem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Comparison test between fuzzy-based heuristic selection and random selection
 * Evaluates the effectiveness of the fuzzy inference system
 */
public class FuzzySystemComparison {

    private FuzzyInferenceSystem fuzzySystem;
    private FuzzyHeuristicSelector selector;
    private Random random;

    public FuzzySystemComparison(long seed) {
        this.random = new Random(seed);
        setupFuzzySystem();
    }

    public FuzzySystemComparison() {
        this(42);
    }

    /**
     * Setup the fuzzy inference system with variables and rules
     */
    private void setupFuzzySystem() {
        fuzzySystem = new FuzzyInferenceSystem();

        // Define input fuzzy variables based on Table in the paper
        // Performance: {Low, Medium, High}
        FuzzyVariable performance = new FuzzyVariable("Performance", 0.0, 1.0);
        performance.addMembershipFunction("Low", 0.2, 0.15);
        performance.addMembershipFunction("Medium", 0.5, 0.15);
        performance.addMembershipFunction("High", 0.8, 0.15);
        fuzzySystem.addInputVariable(performance);

        // Stability: {Unstable, Moderate, Stable}
        // Note: Stability = 1/(1 + Sensitivity), so it's also in [0, 1]
        FuzzyVariable stability = new FuzzyVariable("Stability", 0.0, 1.0);
        stability.addMembershipFunction("Unstable", 0.2, 0.15);
        stability.addMembershipFunction("Moderate", 0.5, 0.15);
        stability.addMembershipFunction("Stable", 0.8, 0.15);
        fuzzySystem.addInputVariable(stability);

        // Interpretability: {Hard, Moderate, Easy}
        // Note: Interpretability = 1/(1 + Complexity), so it's also in [0, 1]
        FuzzyVariable interpretability = new FuzzyVariable("Interpretability", 0.0, 1.0);
        interpretability.addMembershipFunction("Hard", 0.2, 0.15);
        interpretability.addMembershipFunction("Moderate", 0.5, 0.15);
        interpretability.addMembershipFunction("Easy", 0.8, 0.15);
        fuzzySystem.addInputVariable(interpretability);

        // Output variable: Selection Index {Unsuitable, Suitable, Very Suitable}
        FuzzyVariable selectionIndex = new FuzzyVariable("SelectionIndex", 0.0, 100.0);
        selectionIndex.addMembershipFunction("Unsuitable", 25.0, 15.0);
        selectionIndex.addMembershipFunction("Suitable", 50.0, 15.0);
        selectionIndex.addMembershipFunction("VerySuitable", 75.0, 15.0);
        fuzzySystem.setOutputVariable(selectionIndex);

        // Define fuzzy rules based on the paper examples
        // Rule 1: High performance, stable, moderate interpretability -> Very Suitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Moderate")
                .setConsequent("SelectionIndex", "VerySuitable"));

        // Rule 2: High performance, stable, easy -> Very Suitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "VerySuitable"));

        // Rule 3: High performance, moderate stability -> Suitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Moderate")
                .setConsequent("SelectionIndex", "Suitable"));

        // Rule 4: Medium performance, moderate stability, easy interpretability -> Suitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Medium")
                .addAntecedent("Stability", "Moderate")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "Suitable"));

        // Rule 5: Medium performance, stable, easy -> Very Suitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Medium")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "VerySuitable"));

        // Rule 6: Low performance, unstable -> Unsuitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Low")
                .addAntecedent("Stability", "Unstable")
                .setConsequent("SelectionIndex", "Unsuitable"));

        // Rule 7: Low performance, moderate -> Unsuitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Low")
                .addAntecedent("Stability", "Moderate")
                .setConsequent("SelectionIndex", "Unsuitable"));

        // Rule 8: Medium performance, unstable, hard -> Unsuitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Medium")
                .addAntecedent("Stability", "Unstable")
                .addAntecedent("Interpretability", "Hard")
                .setConsequent("SelectionIndex", "Unsuitable"));

        // Additional rules for better coverage
        // Rule 9: High performance, unstable, easy -> Suitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Unstable")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "Suitable"));

        // Rule 10: Medium performance, stable, moderate -> Suitable
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Medium")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Moderate")
                .setConsequent("SelectionIndex", "Suitable"));

        selector = new FuzzyHeuristicSelector(fuzzySystem);
    }

    /**
     * Run comparison with synthetic data
     */
    public void runSyntheticComparison(int numHeuristics, int numTrials, int k) {
        System.out.println("=".repeat(80));
        System.out.println("FUZZY SYSTEM vs RANDOM SELECTION COMPARISON");
        System.out.println("=".repeat(80));
        System.out.println("Parameters:");
        System.out.println("  Number of heuristics: " + numHeuristics);
        System.out.println("  Number of trials: " + numTrials);
        System.out.println("  Top-k selection: " + k);
        System.out.println();

        // Generate synthetic heuristic data
        List<Double> performances = generateSyntheticData(numHeuristics, 0.3, 0.8);
        List<Double> complexities = generateSyntheticData(numHeuristics, 5.0, 50.0);
        List<Double> sensitivities = generateSyntheticData(numHeuristics, 0.1, 2.0);

        // Build heuristic library
        selector.buildLibrary(performances, complexities, sensitivities);

        System.out.println(selector.getStatistics());
        System.out.println("\n" + "=".repeat(80));

        // Test different user preference scenarios
        Map<String, UserPreferenceVector> scenarios = new LinkedHashMap<>();
        scenarios.put("High Performance", UserPreferenceVector.highPerformance());
        scenarios.put("Balanced", UserPreferenceVector.balanced());
        scenarios.put("Interpretable", UserPreferenceVector.interpretable());
        scenarios.put("Robust", UserPreferenceVector.robust());

        for (Map.Entry<String, UserPreferenceVector> scenario : scenarios.entrySet()) {
            System.out.println("\nScenario: " + scenario.getKey());
            System.out.println("-".repeat(80));
            System.out.println(scenario.getValue());

            compareSelectionMethods(scenario.getValue(), numTrials, k);
        }
    }

    /**
     * Compare fuzzy selection vs random selection
     * Note: Raw performance is minimization objective (smaller is better)
     */
    private void compareSelectionMethods(UserPreferenceVector upv, int numTrials, int k) {
        double fuzzyAvgPerf = 0.0, fuzzyAvgSI = 0.0;
        double randomAvgPerf = 0.0, randomAvgSI = 0.0;

        List<Heuristic> fuzzySelected = selector.selectByPreference(upv, k);
        List<Heuristic> randomSelected_final = selector.selectRandomly(k, random);

        for (int trial = 0; trial < numTrials; trial++) {
            List<Heuristic> randomSelected = selector.selectRandomly(k, random);
            randomSelected_final = randomSelected;

            // Compute metrics for fuzzy selection
            // Note: Performance is raw value (smaller is better)
            double fuzzyPerf = fuzzySelected.stream()
                    .mapToDouble(Heuristic::getPerformance).average().orElse(0.0);
            double fuzzySI = fuzzySelected.stream()
                    .mapToDouble(Heuristic::getSelectionIndex).average().orElse(0.0);

            // Compute metrics for random selection
            double randomPerf = randomSelected.stream()
                    .mapToDouble(Heuristic::getPerformance).average().orElse(0.0);
            double randomSI = randomSelected.stream()
                    .mapToDouble(Heuristic::getSelectionIndex).average().orElse(0.0);

            fuzzyAvgPerf += fuzzyPerf;
            fuzzyAvgSI += fuzzySI;
            randomAvgPerf += randomPerf;
            randomAvgSI += randomSI;
        }

        fuzzyAvgPerf /= numTrials;
        fuzzyAvgSI /= numTrials;
        randomAvgPerf /= numTrials;
        randomAvgSI /= numTrials;

        System.out.println("\nResults (averaged over " + numTrials + " trials):");
        System.out.println("  [Note: Performance values are raw (minimization), smaller is better]");
        System.out.println("  Fuzzy Selection:");
        System.out.println(String.format("    Avg Performance (raw): %.4f", fuzzyAvgPerf));
        System.out.println(String.format("    Avg Selection Index: %.4f", fuzzyAvgSI));
        System.out.println("  Random Selection:");
        System.out.println(String.format("    Avg Performance (raw): %.4f", randomAvgPerf));
        System.out.println(String.format("    Avg Selection Index: %.4f", randomAvgSI));
        System.out.println("  Improvement:");
        // For minimization: improvement = (random - fuzzy) / random * 100
        // Positive value means fuzzy is better (lower raw value)
        double perfImprovement = ((randomAvgPerf - fuzzyAvgPerf) / randomAvgPerf) * 100;
        double siImprovement = ((fuzzyAvgSI - randomAvgSI) / randomAvgSI) * 100;
        System.out.println(String.format("    Performance: %.2f%% (positive = fuzzy is better)", perfImprovement));
        System.out.println(String.format("    Selection Index: %.2f%%", siImprovement));

        // Show top 3 fuzzy selected heuristics
        System.out.println("\n  Top 3 Fuzzy-Selected Heuristics:");
        for (int i = 0; i < Math.min(3, fuzzySelected.size()); i++) {
            System.out.println("    " + (i + 1) + ". " + fuzzySelected.get(i));
        }

        // Show top 3 random selected heuristics
        System.out.println("\n  Top 3 Random-Selected Heuristics:");
        for (int i = 0; i < Math.min(3, randomSelected_final.size()); i++) {
            System.out.println("    " + (i + 1) + ". " + randomSelected_final.get(i));
        }
    }

    /**
     * Run comparison with real data from FuzzySystemTest
     */
    public void runRealDataComparison(List<List<Double>> perfAllRun,
                                      List<List<Double>> complexAllRun,
                                      List<List<Double>> sensAllRun,
                                      int runIndex,
                                      int numTrials,
                                      int k) {
        if (runIndex >= perfAllRun.size()) {
            System.err.println("Invalid run index: " + runIndex);
            return;
        }

        List<Double> performances = perfAllRun.get(runIndex);
        List<Double> complexities = complexAllRun.get(runIndex);
        List<Double> sensitivities = sensAllRun.get(runIndex);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("REAL DATA COMPARISON - Run " + runIndex);
        System.out.println("=".repeat(80));

        selector.buildLibrary(performances, complexities, sensitivities);
        System.out.println(selector.getStatistics());
        System.out.println();

        // Test with balanced preference
        System.out.println("\n" + "=".repeat(80));
        UserPreferenceVector upv = UserPreferenceVector.balanced();
        System.out.println("Testing with Balanced Preference");
        compareSelectionMethods(upv, numTrials, k);

        // Test with high performance
        System.out.println("\n" + "=".repeat(80));
        upv = UserPreferenceVector.highPerformance();
        System.out.println("Testing with High Performance");
        compareSelectionMethods(upv, numTrials, k);

        // Test with high performance
        upv = UserPreferenceVector.interpretable();
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Testing with High Interpretability");
        compareSelectionMethods(upv, numTrials, k);
    }

    /**
     * Generate synthetic data following a normal distribution
     */
    private List<Double> generateSyntheticData(int size, double min, double max) {
        List<Double> data = new ArrayList<>();
        double mean = (min + max) / 2.0;
        double stdDev = (max - min) / 4.0;

        for (int i = 0; i < size; i++) {
            double value = mean + random.nextGaussian() * stdDev;
            value = Math.max(min, Math.min(max, value));  // Clip to range
            data.add(value);
        }

        return data;
    }

    /**
     * Export results to CSV file
     */
    public void exportResultsToCSV(String filename, UserPreferenceVector upv, int k) throws IOException {
        List<Heuristic> selected = selector.selectByPreference(upv, k);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // Write header
            writer.write("ID,Performance,Complexity,Sensitivity,Stability,Interpretability,SelectionIndex\n");

            // Write data
            for (Heuristic h : selected) {
                writer.write(String.format("%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f\n",
                        h.getId(),
                        h.getPerformance(),
                        h.getStructuralComplexity(),
                        h.getBehavioralSensitivity(),
                        h.getStability(),
                        h.getInterpretability(),
                        h.getSelectionIndex()));
            }
        }

        System.out.println("Results exported to: " + filename);
    }

    /**
     * Main test method
     */
    public static void main(String[] args) {
        FuzzySystemComparison comparison = new FuzzySystemComparison(42);

        // Run synthetic data comparison
        comparison.runSyntheticComparison(100, 30, 10);

        // Example: Export results for high-performance scenario
        try {
            comparison.exportResultsToCSV(
                    "fuzzy_selection_results.csv",
                    UserPreferenceVector.highPerformance(),
                    10
            );
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("Comparison completed!");
        System.out.println("=".repeat(80));
    }
}