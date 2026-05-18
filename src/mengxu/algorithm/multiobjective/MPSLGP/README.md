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
mpslgp.task-inheritance-probability = 1.00
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

Adaptive transfer uses a no-transfer baseline utility in the softmax decision. This prevents two-task experiments from degenerating into a fixed always-one-donor distribution: positive contribution increases the task-region transfer probability, while negative contribution decreases it toward same-task selection. In adaptive mode, `mpslgp.max-transfer-probability` is the transfer budget; `mpslgp.transfer-probability` is used by the fixed-transfer baseline. When `mpslgp.scale-transfer-budget-by-donor-count = true`, the adaptive transfer budget is divided by `mpslgp.num-tasks - 1`, making three-or-more-task runs more conservative without adding validation evaluations or training time.

Adaptive-transfer diagnostics are written to `job.<seed>.transferContribution.csv`. Each row records a receiving task, contributing task, and preference region at a generation, including transfer count, contribution, pairwise and total transfer probability before and after the utility update, no-transfer probability, utility before and after the update, raw region improvement, task population share, survival component, and negative-transfer penalty. These fields support later heatmaps and ablations for whether transfer succeeds because of task relatedness, preference-region fit, or simply high transfer frequency.

The original `mengxu.algorithm.multiobjective.ParetoSetLearning` package is not modified by this implementation.

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
