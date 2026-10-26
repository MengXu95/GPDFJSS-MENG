# Fuzzy System for Heuristic Selection - Implementation

This implementation provides a complete fuzzy inference system for selecting heuristics from a QD (Quality-Diversity) evolved heuristic library based on user preferences.

## Overview

The implementation consists of 7 main Java classes that work together to:
1. Define fuzzy variables and membership functions
2. Create and evaluate fuzzy rules using Mamdani inference
3. Compute Selection Index (SI) for heuristics
4. Match heuristics to user preferences
5. Compare fuzzy-based selection against random selection

## Architecture

### Core Fuzzy System Components

1. **FuzzyMembershipFunction.java**
   - Implements Gaussian membership functions: μ(x) = exp(-(x-c)²/(2σ²))
   - Used to fuzzify crisp values into linguistic terms

2. **FuzzyVariable.java**
   - Represents fuzzy variables (Performance, Stability, Interpretability, Selection Index)
   - Contains multiple membership functions for different linguistic terms
   - Handles fuzzification of crisp input values

3. **FuzzyRule.java**
   - Represents IF-THEN fuzzy rules
   - Evaluates rule firing strength using min (AND) operator
   - Example: IF (P is High) AND (S is Stable) THEN (SI is Very Suitable)

4. **FuzzyInferenceSystem.java**
   - Main Mamdani fuzzy inference engine
   - Implements three-step inference: Fuzzification → Rule Evaluation → Defuzzification
   - Uses centroid method for defuzzification
   - Supports max-min composition for rule aggregation

### Heuristic Selection Components

5. **Heuristic.java**
   - Represents a heuristic with its metrics:
     - Performance (P_h): Scheduling performance
     - Structural Complexity (C_h): Syntactic complexity
     - Behavioral Sensitivity (S_h): Responsiveness to input changes
     - Selection Index (SI): Overall suitability score
   - Computes derived metrics (Stability, Interpretability)

6. **UserPreferenceVector.java**
   - Encodes user preferences in fuzzy space
   - Provides preset preferences: highPerformance(), balanced(), interpretable(), robust()
   - Supports custom preferences with variable importance weights

7. **FuzzyHeuristicSelector.java**
   - Builds heuristic library from performance data
   - Computes Selection Index for all heuristics
   - Implements fuzzy similarity matching: Sim(h, UPV) = Σ w_X · min(μ_X^h, μ_X^u) / Σ w_X
   - Supports three selection methods:
     - Fuzzy-based selection (by user preference)
     - Selection Index-based selection
     - Random selection (baseline)

### Testing and Comparison

8. **FuzzySystemComparison.java**
   - Comprehensive comparison framework
   - Tests fuzzy selection vs random selection
   - Supports both synthetic and real data
   - Exports results to CSV
   - Runs multiple trials for statistical significance

9. **FuzzySystemTestEnhanced.java**
   - Integration with existing MAP-Elites framework
   - Analyzes heuristic libraries from evolved rules
   - Runs fuzzy comparison across multiple runs
   - Saves aggregated results

## Fuzzy System Configuration

### Input Variables (normalized to [0, 1])

1. **Performance** - {Low, Medium, High}
   - Low: Gaussian(center=0.2, σ=0.15)
   - Medium: Gaussian(center=0.5, σ=0.15)
   - High: Gaussian(center=0.8, σ=0.15)

2. **Stability** (= 1/(1 + Sensitivity)) - {Unstable, Moderate, Stable}
   - Unstable: Gaussian(center=0.2, σ=0.15)
   - Moderate: Gaussian(center=0.5, σ=0.15)
   - Stable: Gaussian(center=0.8, σ=0.15)

3. **Interpretability** (= 1/(1 + Complexity)) - {Hard, Moderate, Easy}
   - Hard: Gaussian(center=0.2, σ=0.15)
   - Moderate: Gaussian(center=0.5, σ=0.15)
   - Easy: Gaussian(center=0.8, σ=0.15)

### Output Variable

**Selection Index** - {Unsuitable, Suitable, Very Suitable} [0, 100]
- Unsuitable: Gaussian(center=25, σ=15)
- Suitable: Gaussian(center=50, σ=15)
- Very Suitable: Gaussian(center=75, σ=15)

### Fuzzy Rule Base

The system includes 10 fuzzy rules covering different combinations:

1. IF (P is High) AND (S is Stable) AND (E is Moderate) THEN (SI is Very Suitable)
2. IF (P is High) AND (S is Stable) AND (E is Easy) THEN (SI is Very Suitable)
3. IF (P is High) AND (S is Moderate) THEN (SI is Suitable)
4. IF (P is Medium) AND (S is Moderate) AND (E is Easy) THEN (SI is Suitable)
5. IF (P is Medium) AND (S is Stable) AND (E is Easy) THEN (SI is Very Suitable)
6. IF (P is Low) AND (S is Unstable) THEN (SI is Unsuitable)
7. IF (P is Low) AND (S is Moderate) THEN (SI is Unsuitable)
8. IF (P is Medium) AND (S is Unstable) AND (E is Hard) THEN (SI is Unsuitable)
9. IF (P is High) AND (S is Unstable) AND (E is Easy) THEN (SI is Suitable)
10. IF (P is Medium) AND (S is Stable) AND (E is Moderate) THEN (SI is Suitable)

## Usage Examples

### Example 1: Basic Fuzzy System Test

```java
// Create comparison instance
FuzzySystemComparison comparison = new FuzzySystemComparison(42);

// Run synthetic comparison
comparison.runSyntheticComparison(
    100,  // Number of heuristics
    30,   // Number of trials
    10    // Top-k selection
);
```

### Example 2: Custom User Preference

```java
// Create custom user preference
UserPreferenceVector upv = new UserPreferenceVector()
    .setPreference("Performance", "High", 2.0)
    .setPreference("Stability", "Stable", 1.5)
    .setPreference("Interpretability", "Easy", 1.0);

// Select heuristics
FuzzyHeuristicSelector selector = new FuzzyHeuristicSelector(fuzzySystem);
selector.buildLibrary(performances, complexities, sensitivities);
List<Heuristic> selected = selector.selectByPreference(upv, 10);
```

### Example 3: Integration with MAP-Elites

```java
// Create enhanced test instance
FuzzySystemTestEnhanced ruleTest = new FuzzySystemTestEnhanced(
    trainPath, ruleType, numRuns, testScenario, testSetName, 
    objectives, featureSetName, numTrees
);

// Analyze heuristic library
ruleTest.HeuristicLibraryAnalysis();

// Run fuzzy comparison
ruleTest.runFuzzyComparisonAllRuns(outputDir, 30, 10);
```

### Example 4: Export Results

```java
// Export selected heuristics to CSV
comparison.exportResultsToCSV(
    "fuzzy_selection_results.csv",
    UserPreferenceVector.highPerformance(),
    10
);
```

## Preset User Preferences

The system provides four preset preference profiles:

1. **High Performance**: Prioritizes scheduling performance
   - Performance: High (weight=2.0)
   - Stability: Moderate (weight=0.5)
   - Interpretability: Moderate (weight=0.5)

2. **Balanced**: Equal consideration of all factors
   - Performance: Medium (weight=1.0)
   - Stability: Moderate (weight=1.0)
   - Interpretability: Moderate (weight=1.0)

3. **Interpretable**: Prioritizes ease of understanding
   - Performance: Medium (weight=0.5)
   - Stability: Stable (weight=1.5)
   - Interpretability: Easy (weight=2.0)

4. **Robust**: Prioritizes stability and reliability
   - Performance: Medium (weight=1.0)
   - Stability: Stable (weight=2.0)
   - Interpretability: Moderate (weight=1.0)

## Comparison Metrics

The system evaluates selection methods using:

1. **Average Performance**: Mean performance of selected heuristics
2. **Average Selection Index**: Mean SI score of selected heuristics
3. **Improvement Percentage**: Relative improvement over random selection

## Output Files

1. **fuzzy_selection_results.csv**
   - Selected heuristics with all metrics
   - Format: ID, Performance, Complexity, Sensitivity, Stability, Interpretability, SelectionIndex

2. **fuzzy_comparison_results.csv**
   - Aggregated results across all runs
   - Format: Run, NumHeuristics, AvgPerformance, AvgComplexity, AvgSensitivity

## Key Features

1. ✅ **Complete Mamdani Fuzzy Inference**: Proper implementation with centroid defuzzification
2. ✅ **User-Guided Selection**: Flexible preference encoding with weighted importance
3. ✅ **Fuzzy Similarity Matching**: Min-based similarity computation for heuristic retrieval
4. ✅ **Comprehensive Comparison**: Statistical comparison with random baseline
5. ✅ **Multiple Selection Methods**: Fuzzy-based, SI-based, and random selection
6. ✅ **Real Data Integration**: Works with MAP-Elites evolved heuristics
7. ✅ **Extensible Rule Base**: Easy to add or modify fuzzy rules
8. ✅ **Export Capabilities**: CSV export for further analysis

## Mathematical Foundation

The implementation follows the paper's mathematical framework:

1. **Membership Functions**: μ_X(x) = exp(-(x-c_X)²/(2σ_X²))
2. **Rule Evaluation**: min(μ_1, μ_2, ..., μ_n) for AND operations
3. **Rule Aggregation**: max(firing_strength_1, firing_strength_2, ...)
4. **Defuzzification**: SI(h) = ∫ μ_SI(z)·z dz / ∫ μ_SI(z) dz
5. **Fuzzy Similarity**: Sim(h, UPV) = Σ w_X·min(μ_X^h, μ_X^u) / Σ w_X

## Dependencies

Required imports from your existing framework:
- ec.Fitness
- yimei.jss.* (job shop scheduling framework)
- yimei.jss.ruleanalysis.* (rule analysis tools)

Standard Java libraries:
- java.util.*
- java.io.*

## Testing

Run the main test class:

```bash
javac mengxu/algorithm/MAPElites/FuzzySystem/*.java
java mengxu.algorithm.MAPElites.FuzzySystem.FuzzySystemComparison
```

Expected output:
- Library statistics
- Comparison results for each scenario
- Performance improvement percentages
- Top selected heuristics
- Exported CSV files

## Customization

### Adding New Fuzzy Rules

```java
fuzzySystem.addRule(new FuzzyRule()
    .addAntecedent("Performance", "Medium")
    .addAntecedent("Stability", "Stable")
    .addAntecedent("Interpretability", "Moderate")
    .setConsequent("SelectionIndex", "Suitable")
    .setWeight(1.0));
```

### Modifying Membership Functions

```java
FuzzyVariable performance = new FuzzyVariable("Performance", 0.0, 1.0);
performance.addMembershipFunction("VeryLow", 0.1, 0.1);
performance.addMembershipFunction("Low", 0.3, 0.1);
performance.addMembershipFunction("Medium", 0.5, 0.1);
performance.addMembershipFunction("High", 0.7, 0.1);
performance.addMembershipFunction("VeryHigh", 0.9, 0.1);
```

## References

This implementation is based on the paper:
"Fuzzy Analysis and Interpretation of the Heuristic Library"

Key concepts:
- Mamdani fuzzy inference [Chai et al., 2009]
- Gaussian membership functions [Ali et al., 2015]
- Quality-Diversity algorithms (MAP-Elites)
- User-guided heuristic selection

## Author

Implementation created for the GECCO 2025 paper on fuzzy-enhanced MAP-Elites for job shop scheduling.
