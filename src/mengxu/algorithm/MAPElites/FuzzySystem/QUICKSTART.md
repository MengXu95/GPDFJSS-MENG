# Quick Start Guide - Fuzzy System for Heuristic Selection

## Quick Installation

1. Copy all Java files to your project directory:
   ```
   mengxu/algorithm/MAPElites/FuzzySystem/
   ```

2. Ensure you have the required dependencies from your existing framework:
   - yimei.jss.* packages
   - ec.* packages (ECJ framework)

## 5-Minute Quick Start

### Option 1: Run the Demo

```java
javac FuzzySystemDemo.java
java FuzzySystemDemo
```

This will show you:
- How fuzzy inference computes Selection Index
- How different user preferences affect selection
- Performance comparison vs random selection

### Option 2: Use with Your Data

```java
// Step 1: Create fuzzy system
FuzzySystemComparison comparison = new FuzzySystemComparison();

// Step 2: Run with your data
List<Double> performances = Arrays.asList(0.9, 0.7, 0.5, 0.8, 0.6);
List<Double> complexities = Arrays.asList(20.0, 15.0, 10.0, 25.0, 12.0);
List<Double> sensitivities = Arrays.asList(0.5, 0.3, 0.2, 0.8, 0.4);

// Step 3: Select heuristics
FuzzyHeuristicSelector selector = new FuzzyHeuristicSelector(createFuzzySystem());
selector.buildLibrary(performances, complexities, sensitivities);

// Step 4: Get recommendations
UserPreferenceVector preference = UserPreferenceVector.highPerformance();
List<Heuristic> selected = selector.selectByPreference(preference, 5);

// Step 5: View results
for (Heuristic h : selected) {
    System.out.println(h);
}
```

### Option 3: Integration with MAP-Elites

```java
// Step 1: Analyze your evolved heuristics
FuzzySystemTestEnhanced test = new FuzzySystemTestEnhanced(
    trainPath, ruleType, numRuns, testScenario, testSetName,
    objectives, featureSetName, numTrees
);

// Step 2: Run analysis
test.HeuristicLibraryAnalysis();

// Step 3: Compare fuzzy vs random selection
test.runFuzzyComparisonAllRuns(outputDir, 30, 10);
```

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   User Preferences                          │
│            (Performance, Stability, Interpretability)       │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              FuzzyInferenceSystem                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Fuzzification│→ │Rule Evaluation│→ │Defuzzification│    │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│              (Mamdani Inference)                            │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              Selection Index (SI)                           │
│         (Quantitative suitability score)                    │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│          FuzzyHeuristicSelector                             │
│  • Fuzzy Similarity Matching                                │
│  • Top-k Heuristic Retrieval                                │
│  • Comparison with Random Selection                         │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│           Selected Heuristics                               │
│      (Ranked by User Preference Match)                      │
└─────────────────────────────────────────────────────────────┘
```

## Key Components Flow

```
Heuristic Data (P, C, S)
         │
         ▼
┌────────────────────┐
│   Normalize &      │
│   Transform        │
│ • Stability = 1/(1+S)
│ • Interpret = 1/(1+C)
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│  Fuzzy Inference   │
│  (Mamdani)         │
│ • Fuzzify inputs   │
│ • Evaluate rules   │
│ • Defuzzify output │
└────────┬───────────┘
         │
         ▼
    Selection Index
         │
         ├─────────────────┐
         │                 │
         ▼                 ▼
┌────────────────┐  ┌────────────────┐
│ Fuzzy Matching │  │ Random Selection│
│ (User Prefs)   │  │   (Baseline)    │
└────────┬───────┘  └────────┬───────┘
         │                   │
         └────────┬──────────┘
                  │
                  ▼
         Comparison & Analysis
```

## Preset User Preferences

| Preference     | Focus              | When to Use                           |
|----------------|--------------------|-----------------------------------------|
| `highPerformance()` | Performance    | Need best scheduling results           |
| `balanced()`   | All factors equal  | General-purpose selection              |
| `interpretable()` | Understandability | Need explainable heuristics         |
| `robust()`     | Stability          | Uncertain/dynamic environments         |

## Common Use Cases

### Use Case 1: Find Best Performers
```java
List<Heuristic> best = selector.selectByPreference(
    UserPreferenceVector.highPerformance(), 10
);
```

### Use Case 2: Find Simple, Stable Heuristics
```java
UserPreferenceVector upv = new UserPreferenceVector()
    .setPreference("Interpretability", "Easy", 2.0)
    .setPreference("Stability", "Stable", 2.0)
    .setPreference("Performance", "Medium", 1.0);
List<Heuristic> simple = selector.selectByPreference(upv, 10);
```

### Use Case 3: Compare Selection Methods
```java
// Fuzzy selection
List<Heuristic> fuzzySelected = selector.selectByPreference(upv, 10);

// Random selection (baseline)
List<Heuristic> randomSelected = selector.selectRandomly(10, new Random());

// Compare average performance
double fuzzyPerf = fuzzySelected.stream()
    .mapToDouble(Heuristic::getPerformance).average().orElse(0);
double randomPerf = randomSelected.stream()
    .mapToDouble(Heuristic::getPerformance).average().orElse(0);
    
System.out.println("Improvement: " + 
    ((fuzzyPerf - randomPerf) / randomPerf * 100) + "%");
```

## Expected Output Format

```
================================================================================
FUZZY SYSTEM vs RANDOM SELECTION COMPARISON
================================================================================
Parameters:
  Number of heuristics: 100
  Number of trials: 30
  Top-k selection: 10

Library Statistics:
  Performance:  min=0.304, max=0.797, avg=0.551
  Complexity:   min=5.133, max=49.862, avg=27.498
  Sensitivity:  min=0.102, max=1.998, avg=1.050
  Selection Index: min=25.134, max=74.892, avg=50.012

Scenario: High Performance
--------------------------------------------------------------------------------
UserPreferenceVector:
  Performance: High (weight=2.00)
  Stability: Moderate (weight=0.50)
  Interpretability: Moderate (weight=0.50)

Results (averaged over 30 trials):
  Fuzzy Selection:
    Avg Performance: 0.7234
    Avg Selection Index: 68.45
  Random Selection:
    Avg Performance: 0.5512
    Avg Selection Index: 50.13
  Improvement:
    Performance: 31.23%
    Selection Index: 36.52%

  Top 3 Fuzzy-Selected Heuristics:
    1. Heuristic[id=23, P=0.797, C=15.234, S=0.234, SI=72.45]
    2. Heuristic[id=67, P=0.782, C=18.456, S=0.312, SI=70.23]
    3. Heuristic[id=12, P=0.756, C=12.890, S=0.189, SI=69.87]
```

## Troubleshooting

### Problem: No rules are firing
**Solution**: Check that input values are in the correct range [0, 1] for normalized inputs.

### Problem: All heuristics have similar SI values
**Solution**: Adjust membership function parameters (center, sigma) for better separation.

### Problem: Fuzzy selection not better than random
**Solution**: 
1. Check if your heuristic library has sufficient diversity
2. Verify performance metrics are correctly normalized
3. Ensure user preference matches the library characteristics

## Performance Tips

1. **Library Size**: Works best with 50-500 heuristics
2. **Top-k Selection**: Recommended k=5 to k=20 for practical use
3. **Trials**: Use 30+ trials for statistical significance
4. **Rule Base**: Start with provided rules, add domain-specific rules as needed

## Next Steps

1. Run `FuzzySystemDemo` to see the system in action
2. Modify user preferences to match your needs
3. Integrate with your MAP-Elites results using `FuzzySystemTestEnhanced`
4. Compare fuzzy selection with random baseline
5. Export results and analyze improvement patterns

## Support

For issues or questions:
- Check the comprehensive README.md
- Review the example code in FuzzySystemDemo.java
- Examine the comparison results in FuzzySystemComparison.java
