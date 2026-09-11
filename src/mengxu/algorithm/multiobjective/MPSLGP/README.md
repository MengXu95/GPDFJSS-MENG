# MPSLGP

This package contains an isolated implementation of multitask Pareto set learning GP (MPSLGP) based on the original single-task PSLGP package.

Main entry points:

- `GPRuleEvolutionStatePSL`: MPSLGP evolution state with task-indexed individuals, preference-region adaptive transfer utilities, and transfer diagnostics.
- `PSLEvaluator` and `KNNsurrogateClearingPSLEvaluatorbasedonHV`: task-specific evaluation support through `eval.problem.0`, `eval.problem.1`, ... in the parameter file.
- `PSLParentSelection`: same-task or peer-task parent selection controlled by fixed transfer probability or by adaptive task-region probabilities.
- `multitreeClearingFirstMPSLGP`: local copy of the PSLGP clearing utility, kept inside this package to avoid changing the original PSLGP clearing behavior.
- `multipletreegp-dynamic-MPSLGP.params`: example two-task MPSLGP configuration.

Important parameters:

```properties
mpslgp.num-tasks = 2
mpslgp.transfer-probability = 0.80
mpslgp.contribution-aware-task-inheritance = true
mpslgp.donor-task-inheritance-threshold = 0.65
mpslgp.transfer-start-generation = 5
mpslgp.adaptive-transfer = true
mpslgp.preference-regions = 5
mpslgp.adaptive-transfer-learning-rate = 0.1
mpslgp.adaptive-transfer-temperature = 0.5
mpslgp.min-transfer-probability = 0.0
mpslgp.max-transfer-probability = 0.8
mpslgp.scale-transfer-budget-by-donor-count = false
mpslgp.transfer-improvement-weight = 1.0
mpslgp.transfer-survival-weight = 0.1
mpslgp.transfer-no-improvement-penalty = 0.05
mpslgp.no-transfer-utility = 0.0
```

When `normalisation = 1`, MPSLGP evaluators use the paper baseline-ratio protocol under rotating training seeds. The denominator is recomputed on the current scheduling set with the manual rule pairing FCFS/WSPT/EDD/WATC for Fmax/WFmax/Tmax/WTmax and WIQ routing. No generation-dependent scaling coefficient is applied.

The active MPSLGP params use `yimei.jss.algorithm.multipletreegp.OneTreeCrossoverPipeline`. It performs subtree crossover on one selected GP tree only and keeps the unselected tree from the primary parent. The previous `AllIndexAllSwapCrossoverPipeline` is retained for ablation experiments where the unselected tree is swapped wholesale.

When `mpslgp.contribution-aware-task-inheritance = true`, crossover offspring inherit the primary parent's task by default. If the secondary parent's GP-node contribution reaches `mpslgp.donor-task-inheritance-threshold`, the offspring inherits the secondary parent's task instead. This keeps task identity tied to dominant genetic contribution without forcing every small transferred subtree to change the receiving task label.

Adaptive transfer uses a no-transfer baseline utility in the softmax decision. This prevents two-task experiments from degenerating into a fixed always-one-donor distribution: positive contribution increases the task-region transfer probability, while negative contribution decreases it toward same-task selection. In adaptive mode, `mpslgp.max-transfer-probability` is the transfer budget; `mpslgp.transfer-probability` is used by the fixed-transfer baseline. When `mpslgp.scale-transfer-budget-by-donor-count = true`, the adaptive transfer budget is divided by `mpslgp.num-tasks - 1`, making three-or-more-task runs more conservative without adding validation evaluations or training time.

Adaptive-transfer credit is attached to actual crossover offspring, not parent-selection attempts. Failed crossover and same-task mating do not create transfer events. Each offspring has a unique event ID that survives cloning and sorting, and can receive credit once. The receiving task is the offspring's inherited task; the other contributing parent supplies the donor task. Selection and feedback use the preference region of the next generation, when the offspring is evaluated.

For each receiving-task/donor-task/preference-region group, let `N` be the number of generated transfer offspring and `S` the number retained after preselection and evaluation with valid, uncleared fitness. Each surviving offspring is compared against the best valid non-transfer individual of the same task in the same generation, using the current preference and real objective values. The relative improvement is `clip((baseline - offspring) / max(abs(baseline), 1e-12), -1, 1)`. This avoids comparing different training seeds or proxy scores. If no non-transfer baseline survives, improvement and the non-improvement penalty for evaluated offspring are omitted; survival is still observable.

The contribution is `improvementWeight * sum(relativeImprovement) / N + survivalWeight * S / N - penaltyWeight * (N - S + nonImprovingSurvivors) / N`. No additional simulation evaluations are required. A donor's utility is updated only when it generated offspring. Task population share is retained as a diagnostic and is not used as offspring survival.

Adaptive-transfer diagnostics are written to `job.<seed>.transferContribution.csv`. `TransferCount` now counts actual generated offspring. `BaselinePSLFitness`, `MeanOffspringPSLFitness`, and `RelativeImprovementPerOffspring` replace the former cross-generation best-fitness fields. `SurvivedCount`, `LocalBaselineCount`, and `SurvivalRate` expose the evidence behind each donor's reward. An absent baseline is recorded as infinity; a group without surviving offspring has a NaN mean offspring fitness. Utility and probability fields remain finite.

The original `mengxu.algorithm.multiobjective.ParetoSetLearning` package is not modified by this implementation.

## Task isolation and regression checks

Task count is read before ECJ initializes the evaluator. Multi-task configurations must supply every `eval.problem.<task>` entry; missing entries fail initialization instead of silently using the default problem. Every task rotates its own scheduling set according to its `rotate-sim-seed` setting.

Surrogate caches contain independent, aligned snapshots of phenotypes, fitnesses, and task labels. Diversity statistics reuse the current phenotype reference rules instead of changing the representation after caching. Nearest-neighbor lookup and duplicate clearing operate within a task. Pareto ranking, normalization references, and HV/GD/IGD reference fronts are task-local. HV receives copies of reference ranges, so computing one individual's indicator cannot change the next individual's reference point. Phenotype decision situations remain shared, while objective samples are task-specific.

`test/mengxu/algorithm/multiobjective/MPSLGP/MPSLGPRegressionTest.java` is a dependency-free Java regression runner. After compiling the project classes and this test with `libraries/*` on the classpath, run:

```powershell
java -cp "<compiled-classes>;libraries/*" mengxu.algorithm.multiobjective.MPSLGP.MPSLGPRegressionTest
java -cp "<compiled-classes>;libraries/*" mengxu.algorithm.multiobjective.MPSLGP.MPSLGPRegressionTest --smoke
```

The smoke checks use 24 individuals, 200 recorded jobs, and three generations, including heterogeneous objectives, adaptive transfer, fixed transfer, no transfer, and single-task operation. They stop before final statistics are written. Run from the repository root, or set `-Dmpslgp.params=<absolute-path-to-3tasks-params>` and use absolute classpath entries.

## Shared-reference HV screening from printed logs

`SharedReferenceHVFromLogs` recomputes a cheap, comparable training-screening HV from the printed `Pareto Front of Subpopulation <generation>` blocks in multiple console logs. It pools all parsed final-front points, builds one common ideal/nadir/reference point, normalises every method with that shared reference, and reports a minimisation HV ranking. This is intended for deciding whether an expensive test run is worth doing; it is not a replacement for held-out test evaluation.

Example command:

```powershell
java -cp "out\production\GPDFJSS-MENG" mengxu.algorithm.multiobjective.MPSLGP.SharedReferenceHVFromLogs --params src\mengxu\algorithm\multiobjective\MPSLGP\shared-reference-hv-3tasks.params
```

The two template parameter files are:

- `shared-reference-hv-2tasks.params`
- `shared-reference-hv-3tasks.params`

Replace the `method.*` paths with saved console logs for no transfer, fixed transfer, and adaptive-gated transfer. The parser works for any number of tasks because it reads the printed final Pareto front, not task-specific internals. It also supports any number of objectives if the log prints matching `Objective 0`, `Objective 1`, ... arrays.
