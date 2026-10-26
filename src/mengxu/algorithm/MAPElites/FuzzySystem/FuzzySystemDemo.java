package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.*;

/**
 * Simple demonstration of the Fuzzy System for Heuristic Selection
 * Shows basic usage patterns and expected outputs
 */
public class FuzzySystemDemo {
    
    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("FUZZY SYSTEM FOR HEURISTIC SELECTION - DEMO");
        System.out.println("=".repeat(80));
        
        // Demo 1: Basic fuzzy inference
        demo1_BasicFuzzyInference();
        
        // Demo 2: Heuristic selection with different preferences
        demo2_HeuristicSelection();
        
        // Demo 3: Comparison with random selection
        demo3_ComparisonDemo();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEMO COMPLETED");
        System.out.println("=".repeat(80));
    }
    
    /**
     * Demo 1: Basic Fuzzy Inference
     * Shows how the fuzzy system computes Selection Index
     */
    private static void demo1_BasicFuzzyInference() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEMO 1: Basic Fuzzy Inference");
        System.out.println("=".repeat(80));
        
        // Create fuzzy inference system
        FuzzyInferenceSystem fis = createFuzzySystem();
        
        // Test different heuristic profiles
        Map<String, Double>[] testCases = new Map[]{
            createInput(0.8, 0.8, 0.7),  // High performance, stable, moderate interpretability
            createInput(0.5, 0.5, 0.8),  // Medium performance, moderate stability, easy
            createInput(0.3, 0.2, 0.3),  // Low performance, unstable, hard
            createInput(0.9, 0.4, 0.6),  // Very high performance, unstable, moderate
        };
        
        String[] descriptions = {
            "High Performance, Stable, Moderate Interpretability",
            "Medium Performance, Moderate Stability, Easy to Interpret",
            "Low Performance, Unstable, Hard to Interpret",
            "Very High Performance, Unstable, Moderate Interpretability"
        };
        
        for (int i = 0; i < testCases.length; i++) {
            System.out.println("\nTest Case " + (i + 1) + ": " + descriptions[i]);
            System.out.println("-".repeat(60));
            
            Map<String, Double> inputs = testCases[i];
            System.out.printf("Inputs: P=%.2f, S=%.2f, E=%.2f\n",
                    inputs.get("Performance"),
                    inputs.get("Stability"),
                    inputs.get("Interpretability"));
            
            // Get detailed inference results
            FuzzyInferenceSystem.InferenceResult result = fis.inferWithDetails(inputs);
            
            System.out.printf("Selection Index (SI): %.2f\n", result.output);
            System.out.println("\nFired Rules:");
            for (String rule : result.firedRules) {
                System.out.println("  " + rule);
            }
            
            // Interpret the SI value
            String interpretation;
            if (result.output < 35) {
                interpretation = "Unsuitable";
            } else if (result.output < 65) {
                interpretation = "Suitable";
            } else {
                interpretation = "Very Suitable";
            }
            System.out.println("Interpretation: " + interpretation);
        }
    }
    
    /**
     * Demo 2: Heuristic Selection with Different Preferences
     */
    private static void demo2_HeuristicSelection() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEMO 2: Heuristic Selection with User Preferences");
        System.out.println("=".repeat(80));
        
        // Create selector
        FuzzyInferenceSystem fis = createFuzzySystem();
        FuzzyHeuristicSelector selector = new FuzzyHeuristicSelector(fis);
        
        // Create sample heuristic library
        List<Double> performances = Arrays.asList(0.9, 0.7, 0.5, 0.8, 0.6, 0.4, 0.85, 0.75);
        List<Double> complexities = Arrays.asList(20.0, 15.0, 10.0, 25.0, 12.0, 8.0, 30.0, 18.0);
        List<Double> sensitivities = Arrays.asList(0.5, 0.3, 0.2, 0.8, 0.4, 0.1, 0.6, 0.35);
        
        selector.buildLibrary(performances, complexities, sensitivities);
        
        System.out.println("\nHeuristic Library Statistics:");
        System.out.println(selector.getStatistics());
        
        // Test different user preferences
        Map<String, UserPreferenceVector> preferences = new LinkedHashMap<>();
        preferences.put("High Performance Focus", UserPreferenceVector.highPerformance());
        preferences.put("Balanced Approach", UserPreferenceVector.balanced());
        preferences.put("Interpretability Focus", UserPreferenceVector.interpretable());
        preferences.put("Robustness Focus", UserPreferenceVector.robust());
        
        int topK = 3;
        for (Map.Entry<String, UserPreferenceVector> entry : preferences.entrySet()) {
            System.out.println("\n" + "-".repeat(60));
            System.out.println("Preference: " + entry.getKey());
            System.out.println("-".repeat(60));
            
            List<Heuristic> selected = selector.selectByPreference(entry.getValue(), topK);
            
            System.out.println("Top " + topK + " Selected Heuristics:");
            for (int i = 0; i < selected.size(); i++) {
                Heuristic h = selected.get(i);
                System.out.printf("%d. Heuristic %d: P=%.3f, C=%.1f, S=%.3f, SI=%.2f\n",
                        i + 1, h.getId(),
                        h.getPerformance(),
                        h.getStructuralComplexity(),
                        h.getBehavioralSensitivity(),
                        h.getSelectionIndex());
            }
        }
    }
    
    /**
     * Demo 3: Comparison with Random Selection
     */
    private static void demo3_ComparisonDemo() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEMO 3: Fuzzy Selection vs Random Selection");
        System.out.println("=".repeat(80));
        
        FuzzySystemComparison comparison = new FuzzySystemComparison(42);
        
        System.out.println("\nRunning quick comparison with 50 heuristics...");
        comparison.runSyntheticComparison(50, 10, 5);
    }
    
    /**
     * Helper method to create the fuzzy system
     */
    private static FuzzyInferenceSystem createFuzzySystem() {
        FuzzyInferenceSystem fis = new FuzzyInferenceSystem();
        
        // Input variables
        FuzzyVariable performance = new FuzzyVariable("Performance", 0.0, 1.0);
        performance.addMembershipFunction("Low", 0.2, 0.15);
        performance.addMembershipFunction("Medium", 0.5, 0.15);
        performance.addMembershipFunction("High", 0.8, 0.15);
        fis.addInputVariable(performance);
        
        FuzzyVariable stability = new FuzzyVariable("Stability", 0.0, 1.0);
        stability.addMembershipFunction("Unstable", 0.2, 0.15);
        stability.addMembershipFunction("Moderate", 0.5, 0.15);
        stability.addMembershipFunction("Stable", 0.8, 0.15);
        fis.addInputVariable(stability);
        
        FuzzyVariable interpretability = new FuzzyVariable("Interpretability", 0.0, 1.0);
        interpretability.addMembershipFunction("Hard", 0.2, 0.15);
        interpretability.addMembershipFunction("Moderate", 0.5, 0.15);
        interpretability.addMembershipFunction("Easy", 0.8, 0.15);
        fis.addInputVariable(interpretability);
        
        // Output variable
        FuzzyVariable selectionIndex = new FuzzyVariable("SelectionIndex", 0.0, 100.0);
        selectionIndex.addMembershipFunction("Unsuitable", 25.0, 15.0);
        selectionIndex.addMembershipFunction("Suitable", 50.0, 15.0);
        selectionIndex.addMembershipFunction("VerySuitable", 75.0, 15.0);
        fis.setOutputVariable(selectionIndex);
        
        // Add rules
        fis.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Moderate")
                .setConsequent("SelectionIndex", "VerySuitable"));
        
        fis.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Stable")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "VerySuitable"));
        
        fis.addRule(new FuzzyRule()
                .addAntecedent("Performance", "High")
                .addAntecedent("Stability", "Moderate")
                .setConsequent("SelectionIndex", "Suitable"));
        
        fis.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Medium")
                .addAntecedent("Stability", "Moderate")
                .addAntecedent("Interpretability", "Easy")
                .setConsequent("SelectionIndex", "Suitable"));
        
        fis.addRule(new FuzzyRule()
                .addAntecedent("Performance", "Low")
                .addAntecedent("Stability", "Unstable")
                .setConsequent("SelectionIndex", "Unsuitable"));
        
        return fis;
    }
    
    /**
     * Helper method to create input map
     */
    private static Map<String, Double> createInput(double p, double s, double e) {
        Map<String, Double> inputs = new HashMap<>();
        inputs.put("Performance", p);
        inputs.put("Stability", s);
        inputs.put("Interpretability", e);
        return inputs;
    }
}
