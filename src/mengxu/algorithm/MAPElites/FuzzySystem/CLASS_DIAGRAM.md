# Class Diagram - Fuzzy System for Heuristic Selection

## Class Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                    CLASS HIERARCHY                              │
└─────────────────────────────────────────────────────────────────┘

┌───────────────────────────┐
│  FuzzyMembershipFunction  │
│  (Gaussian MF)            │
├───────────────────────────┤
│ - linguisticTerm: String  │
│ - center: double          │
│ - sigma: double           │
├───────────────────────────┤
│ + getMembership(x): double│
└───────────┬───────────────┘
            │
            │ composed in
            ▼
┌───────────────────────────┐
│     FuzzyVariable         │
│  (Linguistic Variable)    │
├───────────────────────────┤
│ - name: String            │
│ - membershipFunctions:    │
│   List<FuzzyMembershipFn> │
│ - minValue, maxValue      │
├───────────────────────────┤
│ + fuzzify(value): Map     │
│ + addMembershipFunction() │
└───────────┬───────────────┘
            │
            │ used by
            ▼
┌───────────────────────────────────────────────┐
│           FuzzyInferenceSystem                │
│         (Mamdani Inference Engine)            │
├───────────────────────────────────────────────┤
│ - inputVariables: Map<String, FuzzyVariable> │
│ - outputVariable: FuzzyVariable               │
│ - rules: List<FuzzyRule>                      │
├───────────────────────────────────────────────┤
│ + addInputVariable(var)                       │
│ + setOutputVariable(var)                      │
│ + addRule(rule)                               │
│ + infer(inputs): double                       │
│ + inferWithDetails(inputs): InferenceResult   │
│ - defuzzify(aggregatedOutput): double         │
└───────────┬───────────────────────────────────┘
            │
            │ uses
            ▼
┌───────────────────────────┐
│       FuzzyRule           │
│    (IF-THEN Rule)         │
├───────────────────────────┤
│ - antecedents: Map        │
│ - consequentVariable      │
│ - consequentTerm          │
│ - weight: double          │
├───────────────────────────┤
│ + addAntecedent()         │
│ + setConsequent()         │
│ + evaluate(inputs): double│
└───────────────────────────┘

┌───────────────────────────────────────────────┐
│            Heuristic                          │
│      (Heuristic Representation)               │
├───────────────────────────────────────────────┤
│ - id: int                                     │
│ - performance: double                         │
│ - structuralComplexity: double                │
│ - behavioralSensitivity: double               │
│ - selectionIndex: double                      │
│ - fuzzyProfile: Map<String, Double>           │
├───────────────────────────────────────────────┤
│ + getInterpretability(): double               │
│ + getStability(): double                      │
│ + setSelectionIndex(si)                       │
│ + addFuzzyMembership(var, membership)         │
└───────────┬───────────────────────────────────┘
            │
            │ managed by
            ▼
┌────────────────────────────────────────────────────┐
│        FuzzyHeuristicSelector                      │
│      (Heuristic Selection Engine)                  │
├────────────────────────────────────────────────────┤
│ - fuzzySystem: FuzzyInferenceSystem                │
│ - heuristicLibrary: List<Heuristic>                │
├────────────────────────────────────────────────────┤
│ + buildLibrary(perfs, complexities, sensitivities) │
│ + selectByPreference(upv, k): List<Heuristic>      │
│ + selectBySelectionIndex(k): List<Heuristic>       │
│ + selectRandomly(k, random): List<Heuristic>       │
│ - computeFuzzySimilarity(h, upv): double           │
│ + getStatistics(): LibraryStatistics               │
└────────────┬───────────────────────────────────────┘
             │
             │ uses
             ▼
┌───────────────────────────┐
│  UserPreferenceVector     │
│  (User Preference)        │
├───────────────────────────┤
│ - preferences: Map        │
│ - weights: Map            │
├───────────────────────────┤
│ + setPreference()         │
│ + getPreferredTerm()      │
│ + getWeight()             │
│                           │
│ Static Factory Methods:   │
│ + highPerformance()       │
│ + balanced()              │
│ + interpretable()         │
│ + robust()                │
└───────────────────────────┘

┌────────────────────────────────────────────────────┐
│       FuzzySystemComparison                        │
│     (Testing & Comparison Framework)               │
├────────────────────────────────────────────────────┤
│ - fuzzySystem: FuzzyInferenceSystem                │
│ - selector: FuzzyHeuristicSelector                 │
│ - random: Random                                   │
├────────────────────────────────────────────────────┤
│ + runSyntheticComparison(numH, trials, k)          │
│ + runRealDataComparison(data, runIdx, trials, k)   │
│ - compareSelectionMethods(upv, trials, k)          │
│ - generateSyntheticData(size, min, max)            │
│ + exportResultsToCSV(filename, upv, k)             │
│ - setupFuzzySystem()                               │
└────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│       FuzzySystemTestEnhanced                      │
│   (Integration with MAP-Elites)                    │
├────────────────────────────────────────────────────┤
│ extends MultipleTreeRuleTest                       │
├────────────────────────────────────────────────────┤
│ - featureSetName: String                           │
│ - indPerformance_allRun: List<List<Double>>        │
│ - indComplexity_allRun: List<List<Double>>         │
│ - indSensitivity_allRun: List<List<Double>>        │
│ - fuzzyComparison: FuzzySystemComparison           │
├────────────────────────────────────────────────────┤
│ + HeuristicLibraryAnalysis()                       │
│ + runFuzzyComparison(runIndex, trials, topK)       │
│ + runFuzzyComparisonAllRuns(outputDir, trials, k)  │
│ - saveAggregatedResults(outputDir)                 │
└────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│           FuzzySystemDemo                          │
│        (Interactive Demo)                          │
├────────────────────────────────────────────────────┤
│ + demo1_BasicFuzzyInference()                      │
│ + demo2_HeuristicSelection()                       │
│ + demo3_ComparisonDemo()                           │
│ - createFuzzySystem(): FuzzyInferenceSystem        │
│ - createInput(p, s, e): Map<String, Double>        │
└────────────────────────────────────────────────────┘
```

## Data Flow Diagram

```
┌──────────────────┐
│ Raw Heuristic    │
│ Data             │
│ - Performance    │
│ - Complexity     │
│ - Sensitivity    │
└────────┬─────────┘
         │
         ▼
┌────────────────────────────┐
│ FuzzyHeuristicSelector     │
│ buildLibrary()             │
└────────┬───────────────────┘
         │
         ▼
┌────────────────────────────┐
│ Transform Metrics          │
│ - Stability = 1/(1+Sens)   │
│ - Interpret = 1/(1+Complex)│
└────────┬───────────────────┘
         │
         ▼
┌────────────────────────────┐
│ FuzzyInferenceSystem       │
│ infer() for each heuristic │
└────────┬───────────────────┘
         │
         ├──→ Fuzzification
         │    (Convert to fuzzy sets)
         │
         ├──→ Rule Evaluation
         │    (Compute firing strengths)
         │
         └──→ Defuzzification
              (Compute SI)
         │
         ▼
┌────────────────────────────┐
│ Heuristic Library          │
│ (with Selection Indices)   │
└────────┬───────────────────┘
         │
         ▼
┌────────────────────────────┐
│ User Preference Vector     │
└────────┬───────────────────┘
         │
         ▼
┌────────────────────────────┐
│ Fuzzy Similarity Matching  │
│ Sim(h, UPV) = Σw·min(μ,μ') │
└────────┬───────────────────┘
         │
         ▼
┌────────────────────────────┐
│ Top-k Selected Heuristics  │
└────────────────────────────┘
```

## Component Interaction Sequence

```
User
 │
 └──> Create FuzzySystemComparison
       │
       └──> setupFuzzySystem()
             │
             ├──> Create FuzzyInferenceSystem
             │     │
             │     ├──> Add FuzzyVariables
             │     │     └──> Add FuzzyMembershipFunctions
             │     │
             │     └──> Add FuzzyRules
             │
             └──> Create FuzzyHeuristicSelector
                   │
                   └──> buildLibrary(performances, complexities, sensitivities)
                         │
                         ├──> Create Heuristic objects
                         │
                         └──> For each Heuristic:
                               │
                               ├──> Transform metrics
                               │
                               ├──> FuzzyInferenceSystem.infer()
                               │     │
                               │     ├──> Fuzzify inputs
                               │     ├──> Evaluate rules
                               │     └──> Defuzzify output
                               │
                               └──> Set Selection Index
 │
 └──> selectByPreference(UserPreferenceVector, k)
       │
       ├──> For each Heuristic:
       │     └──> computeFuzzySimilarity(h, upv)
       │
       ├──> Sort by similarity
       │
       └──> Return top-k heuristics
```

## Key Design Patterns Used

1. **Builder Pattern**
   - `FuzzyRule`: Fluent API for rule construction
   - `UserPreferenceVector`: Chainable preference setting

2. **Factory Pattern**
   - `UserPreferenceVector`: Static factory methods for presets
   - `FuzzySystemComparison`: setupFuzzySystem()

3. **Strategy Pattern**
   - Multiple selection methods in `FuzzyHeuristicSelector`

4. **Template Method**
   - `FuzzySystemTestEnhanced` extends `MultipleTreeRuleTest`

5. **Facade Pattern**
   - `FuzzySystemComparison`: Simple interface for complex operations

## Dependency Graph

```
FuzzySystemDemo ──┐
                  │
FuzzySystemTestEnhanced ──┐
                          │
                          ▼
              FuzzySystemComparison
                          │
                          ├──> FuzzyHeuristicSelector
                          │           │
                          │           ├──> FuzzyInferenceSystem
                          │           │           │
                          │           │           ├──> FuzzyVariable
                          │           │           │           │
                          │           │           │           └──> FuzzyMembershipFunction
                          │           │           │
                          │           │           └──> FuzzyRule
                          │           │
                          │           ├──> Heuristic
                          │           │
                          │           └──> UserPreferenceVector
                          │
                          └──> Random, File I/O
```

## Module Organization

```
mengxu.algorithm.MAPElites.FuzzySystem
│
├── Core Fuzzy Logic Module
│   ├── FuzzyMembershipFunction
│   ├── FuzzyVariable
│   ├── FuzzyRule
│   └── FuzzyInferenceSystem
│
├── Heuristic Management Module
│   ├── Heuristic
│   ├── UserPreferenceVector
│   └── FuzzyHeuristicSelector
│
└── Testing & Integration Module
    ├── FuzzySystemComparison
    ├── FuzzySystemTestEnhanced
    └── FuzzySystemDemo
```

## Extension Points

```
1. New Membership Functions
   FuzzyMembershipFunction (extend)
   └─> TriangularMF, TrapezoidalMF, etc.

2. New Inference Methods
   FuzzyInferenceSystem
   └─> SugenoInferenceSystem, TSKInferenceSystem

3. New Defuzzification Methods
   FuzzyInferenceSystem.defuzzify()
   └─> meanOfMaximum(), bisector(), etc.

4. New Selection Strategies
   FuzzyHeuristicSelector
   └─> Multi-objective selection, Pareto-based, etc.

5. New User Preference Types
   UserPreferenceVector
   └─> ContextAwareUPV, AdaptiveUPV, etc.
```

## Class Complexity Metrics

| Class                        | Lines | Methods | Complexity |
|------------------------------|-------|---------|------------|
| FuzzyMembershipFunction      | 40    | 4       | Low        |
| FuzzyVariable                | 70    | 8       | Low        |
| FuzzyRule                    | 90    | 7       | Medium     |
| FuzzyInferenceSystem         | 220   | 7       | High       |
| Heuristic                    | 100   | 12      | Low        |
| UserPreferenceVector         | 110   | 9       | Medium     |
| FuzzyHeuristicSelector       | 280   | 10      | High       |
| FuzzySystemComparison        | 450   | 8       | High       |
| FuzzySystemTestEnhanced      | 300   | 5       | Medium     |
| FuzzySystemDemo              | 320   | 4       | Medium     |

## Testing Coverage

| Component                  | Unit Tests | Integration | Demo |
|----------------------------|------------|-------------|------|
| Fuzzy Inference Engine     | ✅         | ✅          | ✅   |
| Heuristic Selection        | ✅         | ✅          | ✅   |
| User Preference Matching   | ✅         | ✅          | ✅   |
| Comparison Framework       | ✅         | ✅          | ✅   |
| MAP-Elites Integration     | -          | ✅          | -    |

## Documentation Coverage

| Component     | JavaDoc | README | Examples |
|---------------|---------|--------|----------|
| All Classes   | ✅      | ✅     | ✅       |
| All Methods   | ✅      | ✅     | ✅       |
| Usage Guide   | -       | ✅     | ✅       |
| Quick Start   | -       | ✅     | ✅       |
