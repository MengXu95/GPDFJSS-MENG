# MPSLGP

This package contains an isolated implementation of multitask Pareto set learning GP (MPSLGP) based on the original single-task PSLGP package.

Main entry points:

- `GPRuleEvolutionStatePSL`: MPSLGP evolution state with task-indexed individuals and peer-task transfer parameters.
- `PSLEvaluator` and `KNNsurrogateClearingPSLEvaluatorbasedonHV`: task-specific evaluation support through `eval.problem.0`, `eval.problem.1`, ... in the parameter file.
- `PSLParentSelection`: peer-task parent selection controlled by `mpslgp.transfer-probability`.
- `multitreeClearingFirstMPSLGP`: local copy of the PSLGP clearing utility, kept inside this package to avoid changing the original PSLGP clearing behavior.
- `multipletreegp-dynamic-MPSLGP.params`: example two-task MPSLGP configuration.

Important parameters:

```properties
mpslgp.num-tasks = 2
mpslgp.transfer-probability = 0.30
mpslgp.task-inheritance-probability = 0.50
mpslgp.transfer-start-generation = 1
```

The original `mengxu.algorithm.multiobjective.ParetoSetLearning` package is not modified by this implementation.
