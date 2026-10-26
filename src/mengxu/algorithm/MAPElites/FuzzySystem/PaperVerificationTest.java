package mengxu.algorithm.MAPElites.FuzzySystem;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Legacy input-metric diagnostic, not a held-out scheduling evaluation.
 * Use revision.RevisionExperiment for publication experiments with separate
 * validation and test instances and run-level outputs.
 * Metrics compared: Performance, Structural Complexity, Behavioral Sensitivity
 */
public class PaperVerificationTest {

    private FuzzyInferenceSystem fuzzySystem;
    private Random random;

    // Results storage for each case
    private Map<String, CaseResults> resultsMap;

    public PaperVerificationTest(long seed) {
        this.random = new Random(seed);
        this.resultsMap = new LinkedHashMap<>();
        setupFuzzySystem();
    }

    /**
     * Setup the fuzzy inference system
     */
    private void setupFuzzySystem() {
        fuzzySystem = new FuzzyInferenceSystem();

        // Input variables
        FuzzyVariable performance = new FuzzyVariable("Performance", 0.0, 1.0);
        performance.addMembershipFunction("Low", 0.2, 0.15);
        performance.addMembershipFunction("Medium", 0.5, 0.15);
        performance.addMembershipFunction("High", 0.8, 0.15);
        fuzzySystem.addInputVariable(performance);

        FuzzyVariable stability = new FuzzyVariable("Stability", 0.0, 1.0);
        stability.addMembershipFunction("Unstable", 0.2, 0.15);
        stability.addMembershipFunction("Moderate", 0.5, 0.15);
        stability.addMembershipFunction("Stable", 0.8, 0.15);
        fuzzySystem.addInputVariable(stability);

        FuzzyVariable interpretability = new FuzzyVariable("Interpretability", 0.0, 1.0);
        interpretability.addMembershipFunction("Hard", 0.2, 0.15);
        interpretability.addMembershipFunction("Moderate", 0.5, 0.15);
        interpretability.addMembershipFunction("Easy", 0.8, 0.15);
        fuzzySystem.addInputVariable(interpretability);

        // Output variable
        FuzzyVariable selectionIndex = new FuzzyVariable("SelectionIndex", 0.0, 200.0);
        selectionIndex.addMembershipFunction("Unsuitable", 25.0, 15.0);
        selectionIndex.addMembershipFunction("Suitable", 50.0, 15.0);
        selectionIndex.addMembershipFunction("VerySuitable", 75.0, 15.0);
        fuzzySystem.setOutputVariable(selectionIndex);

        // Add fuzzy rules
        addFuzzyRules();
    }

    /**
     * Add comprehensive fuzzy rules
     */
    private void addFuzzyRules() {
        // Very Suitable rules
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Moderate")
                .setConsequent("SelectionIndex", "VerySuitable"));

        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "VerySuitable"));

        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Medium")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "VerySuitable"));

        // Suitable rules
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Moderate")
                .setConsequent("SelectionIndex", "Suitable"));

        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Medium")
                .addAntecedent("Stability", "Moderate")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "Suitable"));

        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Medium")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Moderate")
                .setConsequent("SelectionIndex", "Suitable"));

        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Unstable")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "Suitable"));

        // Unsuitable rules
        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Low")
                .addAntecedent("Stability", "Unstable")
                .setConsequent("SelectionIndex", "Unsuitable"));

        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Low")
                .addAntecedent("Stability", "Moderate")
                .setConsequent("SelectionIndex", "Unsuitable"));

        fuzzySystem.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Medium")
                .addAntecedent("Stability", "Unstable")
                .addAntecedent("Interpretability", "Hard")
                .setConsequent("SelectionIndex", "Unsuitable"));
    }

    /**
     * Run verification test on real data from multiple runs
     */
    public void runVerification(List<List<Double>> perfAllRun,
                                List<List<Double>> complexAllRun,
                                List<List<Double>> sensAllRun,
                                int topK) {

        int numRuns = perfAllRun.size();

        System.out.println("=".repeat(90));
        System.out.println("LEGACY INPUT-METRIC DIAGNOSTIC - NO HELD-OUT SCHEDULING TEST");
        System.out.println("=".repeat(90));
        System.out.println("Number of Runs: " + numRuns);
        System.out.println("Top-k Selection: " + topK);
        System.out.println("Comparison: Fuzzy Selection vs Random Selection");
        System.out.println("Metrics: Performance (raw), Structural Complexity, Behavioral Sensitivity");
        System.out.println("Note: All metrics are minimization (smaller is better)");
        System.out.println("=".repeat(90));

        // Define all 6 test cases
        Map<String, UserPreferenceVector> testCases = new LinkedHashMap<>();
        testCases.put("C1", UserPreferenceVector.case1_HighPerformance());
        testCases.put("C2", UserPreferenceVector.case2_HighStability());
        testCases.put("C3", UserPreferenceVector.case3_HighInterpretability());
        testCases.put("C4", UserPreferenceVector.case4_Balanced());
        testCases.put("C5", UserPreferenceVector.case5_HighPerformanceHighInterpretability());
        testCases.put("C6", UserPreferenceVector.case6_HighStabilityHighPerformanceHard());

        // Run test for each case
        for (Map.Entry<String, UserPreferenceVector> entry : testCases.entrySet()) {
            String caseName = entry.getKey();
            UserPreferenceVector upv = entry.getValue();

            CaseResults results = runSingleCase(caseName, upv, perfAllRun,
                    complexAllRun, sensAllRun, topK);
            resultsMap.put(caseName, results);
        }

        // Print comprehensive results
        printComparisonTable();
        printDetailedResults();
    }

    /**
     * Run test for a single case across all runs
     */
    private CaseResults runSingleCase(String caseName, UserPreferenceVector upv,
                                      List<List<Double>> perfAllRun,
                                      List<List<Double>> complexAllRun,
                                      List<List<Double>> sensAllRun,
                                      int topK) {

        System.out.println("\nProcessing " + caseName + "...");

        CaseResults results = new CaseResults(caseName);

        for (int runIdx = 0; runIdx < perfAllRun.size(); runIdx++) {
            // Create selector for this run
            FuzzyHeuristicSelector selector = new FuzzyHeuristicSelector(fuzzySystem);
            selector.buildLibrary(perfAllRun.get(runIdx),
                    complexAllRun.get(runIdx),
                    sensAllRun.get(runIdx));

            // Fuzzy selection
            List<Heuristic> fuzzySelected = selector.selectByPreference(upv, topK);

            // Random selection
            List<Heuristic> randomSelected = selector.selectRandomly(topK, random);

            // Compute metrics for fuzzy selection
            double fuzzyPerf = fuzzySelected.stream()
                    .mapToDouble(Heuristic::getPerformance).average().orElse(0.0);
            double fuzzyComplex = fuzzySelected.stream()
                    .mapToDouble(Heuristic::getStructuralComplexity).average().orElse(0.0);
            double fuzzySens = fuzzySelected.stream()
                    .mapToDouble(Heuristic::getBehavioralSensitivity).average().orElse(0.0);

            // Compute metrics for random selection
            double randomPerf = randomSelected.stream()
                    .mapToDouble(Heuristic::getPerformance).average().orElse(0.0);
            double randomComplex = randomSelected.stream()
                    .mapToDouble(Heuristic::getStructuralComplexity).average().orElse(0.0);
            double randomSens = randomSelected.stream()
                    .mapToDouble(Heuristic::getBehavioralSensitivity).average().orElse(0.0);

            // Store results
            results.addFuzzyResult(fuzzyPerf, fuzzyComplex, fuzzySens);
            results.addRandomResult(randomPerf, randomComplex, randomSens);
        }

        return results;
    }

    /**
     * Print comparison table (Paper Table format)
     */
    private void printComparisonTable() {
        System.out.println("\n" + "=".repeat(110));
        System.out.println("COMPARISON TABLE (Average across " +
                resultsMap.values().iterator().next().fuzzyPerf.size() + " runs)");
        System.out.println("=".repeat(110));

        // Print header
        System.out.println(String.format("%-6s | %-12s | %-12s | %-12s | %-12s | %-12s | %-12s | %-12s",
                "Case", "Method", "Performance", "Interpretability", "Stability",
                "Perf Imp%", "Inter Imp%", "Stab Imp%"));
        System.out.println("-".repeat(110));

        // Print results for each case
        for (Map.Entry<String, CaseResults> entry : resultsMap.entrySet()) {
            String caseName = entry.getKey();
            CaseResults results = entry.getValue();

            double avgFuzzyPerf = results.getAvgFuzzyPerformance();
            double avgFuzzyComplex = results.getAvgFuzzyComplexity();
            double avgFuzzySens = results.getAvgFuzzySensitivity();

            double avgRandomPerf = results.getAvgRandomPerformance();
            double avgRandomComplex = results.getAvgRandomComplexity();
            double avgRandomSens = results.getAvgRandomSensitivity();

            // Calculate improvements (for minimization: positive = fuzzy is better)
            double perfImp = ((avgRandomPerf - avgFuzzyPerf) / avgRandomPerf) * 100;
            double complexImp = ((avgRandomComplex - avgFuzzyComplex) / avgRandomComplex) * 100;
            double sensImp = ((avgRandomSens - avgFuzzySens) / avgRandomSens) * 100;

            // Print fuzzy results
            System.out.println(String.format("%-6s | %-12s | %12.4f | %12.4f | %12.4f | %12.2f | %12.2f | %12.2f",
                    caseName, "Fuzzy", avgFuzzyPerf, avgFuzzyComplex, avgFuzzySens,
                    perfImp, complexImp, sensImp));

            // Print random results
            System.out.println(String.format("%-6s | %-12s | %12.4f | %12.4f | %12.4f | %12s | %12s | %12s",
                    "", "Random", avgRandomPerf, avgRandomComplex, avgRandomSens,
                    "baseline", "baseline", "baseline"));

            System.out.println("-".repeat(110));
        }

        System.out.println("\nNote: Performance Improvement % and Complexity Improvement %");
        System.out.println("      Positive values indicate Fuzzy selection is better (lower raw values)");
        System.out.println("=".repeat(110));
    }

    /**
     * Print detailed results with statistical information
     */
    private void printDetailedResults() {
        System.out.println("\n" + "=".repeat(90));
        System.out.println("DETAILED STATISTICAL RESULTS");
        System.out.println("=".repeat(90));

        for (Map.Entry<String, CaseResults> entry : resultsMap.entrySet()) {
            String caseName = entry.getKey();
            CaseResults results = entry.getValue();

            System.out.println("\n" + caseName + " - Detailed Statistics:");
            System.out.println("-".repeat(90));

            // Performance
            System.out.println("Performance (raw, minimization):");
            System.out.println(String.format("  Fuzzy:  Mean=%.4f, StdDev=%.4f, Min=%.4f, Max=%.4f",
                    results.getAvgFuzzyPerformance(), results.getStdFuzzyPerformance(),
                    results.getMinFuzzyPerformance(), results.getMaxFuzzyPerformance()));
            System.out.println(String.format("  Random: Mean=%.4f, StdDev=%.4f, Min=%.4f, Max=%.4f",
                    results.getAvgRandomPerformance(), results.getStdRandomPerformance(),
                    results.getMinRandomPerformance(), results.getMaxRandomPerformance()));

            // Complexity
            System.out.println("Structural Complexity (minimization):");
            System.out.println(String.format("  Fuzzy:  Mean=%.4f, StdDev=%.4f, Min=%.4f, Max=%.4f",
                    results.getAvgFuzzyComplexity(), results.getStdFuzzyComplexity(),
                    results.getMinFuzzyComplexity(), results.getMaxFuzzyComplexity()));
            System.out.println(String.format("  Random: Mean=%.4f, StdDev=%.4f, Min=%.4f, Max=%.4f",
                    results.getAvgRandomComplexity(), results.getStdRandomComplexity(),
                    results.getMinRandomComplexity(), results.getMaxRandomComplexity()));

            // Sensitivity
            System.out.println("Behavioral Sensitivity (minimization):");
            System.out.println(String.format("  Fuzzy:  Mean=%.4f, StdDev=%.4f, Min=%.4f, Max=%.4f",
                    results.getAvgFuzzySensitivity(), results.getStdFuzzySensitivity(),
                    results.getMinFuzzySensitivity(), results.getMaxFuzzySensitivity()));
            System.out.println(String.format("  Random: Mean=%.4f, StdDev=%.4f, Min=%.4f, Max=%.4f",
                    results.getAvgRandomSensitivity(), results.getStdRandomSensitivity(),
                    results.getMinRandomSensitivity(), results.getMaxRandomSensitivity()));
        }

        System.out.println("\n" + "=".repeat(90));
    }

    /**
     * Export results to CSV for LaTeX table generation
     */
    public void exportToCSV(String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // Write header
            writer.write("Case,Method,Performance,Complexity,Sensitivity,PerfImp%,ComplexImp%,SensImp%\n");

            // Write data for each case
            for (Map.Entry<String, CaseResults> entry : resultsMap.entrySet()) {
                String caseName = entry.getKey();
                CaseResults results = entry.getValue();

                double avgFuzzyPerf = results.getAvgFuzzyPerformance();
                double avgFuzzyComplex = results.getAvgFuzzyComplexity();
                double avgFuzzySens = results.getAvgFuzzySensitivity();

                double avgRandomPerf = results.getAvgRandomPerformance();
                double avgRandomComplex = results.getAvgRandomComplexity();
                double avgRandomSens = results.getAvgRandomSensitivity();

                double perfImp = ((avgRandomPerf - avgFuzzyPerf) / avgRandomPerf) * 100;
                double complexImp = ((avgRandomComplex - avgFuzzyComplex) / avgRandomComplex) * 100;
                double sensImp = ((avgRandomSens - avgFuzzySens) / avgRandomSens) * 100;

                // Fuzzy row
                writer.write(String.format("%s,Fuzzy,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f\n",
                        caseName, avgFuzzyPerf, avgFuzzyComplex, avgFuzzySens,
                        perfImp, complexImp, sensImp));

                // Random row
                writer.write(String.format("%s,Random,%.4f,%.4f,%.4f,0.00,0.00,0.00\n",
                        caseName, avgRandomPerf, avgRandomComplex, avgRandomSens));
            }
        }

        System.out.println("\nResults exported to: " + filename);
    }

    /**
     * Inner class to store results for a single case
     */
    private static class CaseResults {
        String caseName;
        List<Double> fuzzyPerf = new ArrayList<>();
        List<Double> fuzzyComplex = new ArrayList<>();
        List<Double> fuzzySens = new ArrayList<>();
        List<Double> randomPerf = new ArrayList<>();
        List<Double> randomComplex = new ArrayList<>();
        List<Double> randomSens = new ArrayList<>();

        public CaseResults(String caseName) {
            this.caseName = caseName;
        }

        public void addFuzzyResult(double perf, double complex, double sens) {
            fuzzyPerf.add(perf);
            fuzzyComplex.add(complex);
            fuzzySens.add(sens);
        }

        public void addRandomResult(double perf, double complex, double sens) {
            randomPerf.add(perf);
            randomComplex.add(complex);
            randomSens.add(sens);
        }

        // Fuzzy statistics
        public double getAvgFuzzyPerformance() {
            return fuzzyPerf.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getStdFuzzyPerformance() {
            return calculateStdDev(fuzzyPerf);
        }

        public double getMinFuzzyPerformance() {
            return fuzzyPerf.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }

        public double getMaxFuzzyPerformance() {
            return fuzzyPerf.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        public double getAvgFuzzyComplexity() {
            return fuzzyComplex.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getStdFuzzyComplexity() {
            return calculateStdDev(fuzzyComplex);
        }

        public double getMinFuzzyComplexity() {
            return fuzzyComplex.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }

        public double getMaxFuzzyComplexity() {
            return fuzzyComplex.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        public double getAvgFuzzySensitivity() {
            return fuzzySens.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getStdFuzzySensitivity() {
            return calculateStdDev(fuzzySens);
        }

        public double getMinFuzzySensitivity() {
            return fuzzySens.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }

        public double getMaxFuzzySensitivity() {
            return fuzzySens.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        // Random statistics
        public double getAvgRandomPerformance() {
            return randomPerf.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getStdRandomPerformance() {
            return calculateStdDev(randomPerf);
        }

        public double getMinRandomPerformance() {
            return randomPerf.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }

        public double getMaxRandomPerformance() {
            return randomPerf.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        public double getAvgRandomComplexity() {
            return randomComplex.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getStdRandomComplexity() {
            return calculateStdDev(randomComplex);
        }

        public double getMinRandomComplexity() {
            return randomComplex.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }

        public double getMaxRandomComplexity() {
            return randomComplex.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        public double getAvgRandomSensitivity() {
            return randomSens.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getStdRandomSensitivity() {
            return calculateStdDev(randomSens);
        }

        public double getMinRandomSensitivity() {
            return randomSens.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }

        public double getMaxRandomSensitivity() {
            return randomSens.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        private double calculateStdDev(List<Double> values) {
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .average()
                    .orElse(0.0);
            return Math.sqrt(variance);
        }
    }

    public static void main(String[] args) {
        // Example usage with synthetic data
        System.out.println("Testing with synthetic data...\n");

        PaperVerificationTest test = new PaperVerificationTest(42);

        // Generate 30 runs of synthetic data
        int numRuns = 30;
        int numHeuristics = 200;
        List<List<Double>> perfAllRun = new ArrayList<>();
        List<List<Double>> complexAllRun = new ArrayList<>();
        List<List<Double>> sensAllRun = new ArrayList<>();

        Random rand = new Random(42);
        for (int i = 0; i < numRuns; i++) {
            List<Double> perfs = new ArrayList<>();
            List<Double> complexities = new ArrayList<>();
            List<Double> sensitivities = new ArrayList<>();

            for (int j = 0; j < numHeuristics; j++) {
                perfs.add(220 + rand.nextGaussian() * 20);      // Mean=50, StdDev=20
                complexities.add(20 + rand.nextGaussian() * 10); // Mean=20, StdDev=10
                sensitivities.add(0.25 + rand.nextGaussian() * 0.1); // Mean=0.5, StdDev=0.2
            }

            perfAllRun.add(perfs);
            complexAllRun.add(complexities);
            sensAllRun.add(sensitivities);
        }

        // Run verification
        test.runVerification(perfAllRun, complexAllRun, sensAllRun, 5);

        // Export to CSV
        try {
            test.exportToCSV("paper_verification_results.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}