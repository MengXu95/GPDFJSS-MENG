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
mpslgp.transfer-start-generation = 1
mpslgp.adaptive-transfer = true
mpslgp.preference-regions = 5
mpslgp.adaptive-transfer-learning-rate = 0.2
mpslgp.adaptive-transfer-temperature = 0.2
mpslgp.min-transfer-probability = 0.0
mpslgp.max-transfer-probability = 0.8
mpslgp.transfer-improvement-weight = 1.0
mpslgp.transfer-survival-weight = 0.1
mpslgp.transfer-no-improvement-penalty = 0.05
```

When `normalisation = 1`, MPSLGP evaluators use the paper baseline-ratio protocol under rotating training seeds. The denominator is recomputed on the current scheduling set with the manual rule pairing FCFS/WSPT/EDD/WATC for Fmax/WFmax/Tmax/WTmax and WIQ routing. No generation-dependent scaling coefficient is applied.

Adaptive-transfer diagnostics are written to `job.<seed>.adaptiveTransfer.csv` with task pair, preference region, transfer count, region improvement, utility, and probability columns.

The original `mengxu.algorithm.multiobjective.ParetoSetLearning` package is not modified by this implementation.
