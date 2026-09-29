# MPSLGP

This package contains an isolated implementation of multitask Pareto set learning GP (MPSLGP) based on the original single-task PSLGP package.

Main entry points:

- `GPRuleEvolutionStatePSL`: MPSLGP evolution state with task-indexed individuals, preference-region adaptive transfer utilities, and transfer diagnostics.
- `PSLEvaluator` and `KNNsurrogateClearingPSLEvaluatorbasedonHV`: task-specific evaluation support through `eval.problem.0`, `eval.problem.1`, ... in the parameter file.
- `PSLParentSelection`: same-task or peer-task parent selection controlled by fixed transfer probability or by adaptive task-region probabilities.
- `multitreeClearingFirstMPSLGP`: local copy of the PSLGP clearing utility, kept inside this package to avoid changing the original PSLGP clearing behavior.
- `multipletreegp-dynamic-MPSLGP.params`: example two-task MPSLGP configuration.
- `MPSLGPPaperTrainingMain`: isolated training batches for the paper profiles, with per-run configuration snapshots and resumable attempts.

## Paper training batches

The runner performs **training only**. It does not launch held-out testing or run PSLGP. Reuse existing PSLGP results only when their training budget, task/objective definitions, workloads, seed protocol, normalization, and eventual held-out test protocol are compatible with the MPSLGP comparison. `--algorithms PSLGP` is rejected rather than silently starting a different baseline.

The default batch is **7 algorithms × 8 scenarios × 30 runs × 1 indicator = 1,680 training jobs**. The default indicator is IGD; selecting `--indicators IGD,HV,GD` expands the complete batch to **5,040 jobs**. These are separate training configurations, not three post-processing measurements of one trained population. Start with a dry run and a smoke batch before starting paper-scale training.

### Paper settings and algorithm profiles

All profiles share one total population of **1,000 individuals across all tasks**, **50 evaluated generations including the initial population**, configured elite count **10** (`breed.elite.0`), brood candidate population **5N**, and **101 preference samples**. The default indicator is **IGD**. `--smoke` uses a separate result namespace and overrides the population to **24**, generations to **3**, recorded jobs to **200**, warmup jobs to **50**, preference samples to **5**, configured elite count to **2**, and transfer start to **1**; smoke results are not paper results.

Paper-mode simulation workloads use **5,000 recorded jobs** and **1,000 warmup jobs**, with the minimum number of operations per job explicitly set to **2** (maximum **10**). Per-run reporting uses **`output-top-N = 1`** with **`topN-from-pareto-front = false`**; this is a training-output setting, not a request for held-out testing.

For `T` tasks, the paper transfer ceiling is `0.8`, with donor-count scaling giving the effective budget **`B = 0.8 / (T - 1)`**: `0.8` for two tasks and `0.4` for three. Adaptive profiles start transfer at generation **15**, use **K = 5** preference regions, learning rate **η = 0.1**, temperature **τ = 1**, local utility **U0 = 0.1**, reward weights **(improvement, survival, penalty) = (1, 0.1, 0.1)**, and contribution-aware inheritance threshold **θ = 0.65**. The gate can reduce the actual transfer probability below `B`.

Each row below is a complete profile relative to these shared settings, not an additional modifier to combine with another row:

| `--algorithms` value | Configuration relative to `full` |
| --- | --- |
| `full` | Adaptive directed task-pair/region utilities, no-transfer gate enabled, contribution-aware task inheritance. |
| `no-transfer` | Adaptive transfer disabled and cross-task transfer probability zero; mating stays local. |
| `fixed-transfer` | Adaptive transfer disabled; fixed cross-task probability is the effective `B`, not the unscaled `0.8` ceiling. |
| `task-pair` | Adaptive transfer with `K = 1`: directed task-pair utilities remain, but there is no preference-region partition. |
| `survival-only` | Adaptive reward weights `(0, 0.1, 0)`: only offspring survival contributes to the utility update. |
| `no-gate` | `mpslgp.use-no-transfer-gate = false`: spend exactly `B` on donors, allocated by donor-only utility softmax; local probability is `1 - B`. |
| `primary-task` | `mpslgp.contribution-aware-task-inheritance = false`: offspring retain the primary parent's task. |

The eight scenario IDs are **R1, R2, R3, R4, R5, H1, H2, H3**; all are selected by default. Inspect each generated `effective.params` for its task-specific objectives, utilization, and simulation settings before training.

Run indices are zero-based: `--start-run 0 --runs 30` schedules indices/seeds `0..29`; `--start-run 10 --runs 5` schedules `10..14`. The **GP RNG seed (`seed.0`)** equals the run index, so algorithms and indicators use matching GP seed indices. The **initial simulation-model seed (`sim-seed`) is 0**, consistently across methods, and simulation seeds rotate during training; it is not initialized from the run index. Each run sets `mpslgp.task-allocation-offset = run mod T`: initial round-robin task labels and balanced truncation quota remainders rotate together, rather than always giving the extra individual to task zero.

### Command-line options

| Option | Meaning / default |
| --- | --- |
| `--algorithms LIST` | Comma-separated profile names from the table; default all seven. |
| `--indicators LIST` | `IGD`, `HV`, or `GD`, comma-separated; default `IGD`. |
| `--scenarios LIST` | Comma-separated scenario IDs; default all eight. |
| `--runs N` | Number of runs per selected algorithm/indicator/scenario; default `30`. |
| `--start-run N` | First zero-based run index/seed; default `0`. |
| `--parallel N` | Concurrent child JVMs; default `1`. |
| `--heap SIZE` | Heap limit per child JVM; default `2g`. Account for all children and JVM overhead before increasing parallelism. |
| `--results PATH` | Result root; default repo-relative `src\mengxu\ruleanalysis\MPSLGP\paper-runs`. Smoke runs use a separate namespace. |
| `--param PATH` | Optional base parameter file. Profile, scenario, and command-line settings are materialized into each run's full `effective.params`; no source parameter file needs editing. |
| `--skip-existing` | Skip only a matching, verified successful run. The spelling `--skip existing` is also accepted. |
| `--dry-run` | Write per-run `effective.params`, `run.properties`, and `command.txt` without starting training. |
| `--smoke` | Use the small isolated training configuration described above. |
| `--population N`, `--generations N` | Override the total population and evaluated generation budget, respectively. |
| `--jobs N`, `--warmup N` | Override recorded and warmup jobs in the simulation workload. |
| `--preferences N` | Override the number of preference samples used by the indicator calculation. |
| `--transfer-start N`, `--regions N` | Override transfer start generation and the preference-region count, subject to the chosen profile (`task-pair` uses one region). |
| `--transfer-budget X` | Override the unscaled transfer ceiling; donor-count scaling determines `B`. The no-transfer profile still disables transfer. |
| `--learning-rate X`, `--temperature X` | Override adaptive utility learning rate and donor-softmax temperature. |
| `--inheritance-threshold X` | Override the donor contribution threshold; `primary-task` still retains the primary task. |

Sensitivity/probe settings are different configurations. Use a **different `--results` root** for each changed setting; do not overwrite the paper batch with a changed population, temperature, or other effective parameter.

### PowerShell examples

Build the project first (for example, **Build Project** in IntelliJ). Run from the repository root. Set `JAVA_HOME` to your JDK, or replace `$java` with the full path to its `java.exe`; adjust `$cp` if your compiled output directory differs:

```powershell
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$cp = 'out\production\GPDFJSS-MENG;libraries\*'
$main = 'mengxu.algorithm.multiobjective.MPSLGP.MPSLGPPaperTrainingMain'

# Inspect 56 effective configurations; no training processes.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --dry-run --runs 1 --skip-existing

# Inspect all three indicators: 7 x 8 x 1 x 3 = 168 effective configurations.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --dry-run --runs 1 --indicators IGD,HV,GD --skip-existing

# 14 short training jobs: all seven profiles, R1 and H3, one run each.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --smoke --scenarios R1,H3 --runs 1 --skip-existing

# Main paper batch: all profiles/scenarios, IGD, runs/seeds 0..29.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --skip-existing

# Extend or resume a selected block with matching seeds.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --algorithms full,no-gate --scenarios R1,H3 --start-run 10 --runs 5 --skip-existing

# Full-profile indicator robustness: 1 x 3 x 8 x 30 = 720 jobs.
# Matching completed full/IGD runs from the main batch can be skipped.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --algorithms full --indicators IGD,HV,GD --skip-existing

# Separate sensitivity namespace, not a replacement for the paper settings.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --algorithms full --scenarios R1 --runs 3 --temperature 0.5 --results 'src\mengxu\ruleanalysis\MPSLGP\probe-temperature-05' --skip-existing
```

To train every profile under every indicator, use `--indicators IGD,HV,GD` without restricting `--algorithms`; this schedules up to 5,040 jobs before verified skips.

### IntelliJ run configurations

The shared configurations in `.run` use module **GPDFJSS-MENG**, main class **`mengxu.algorithm.multiobjective.MPSLGP.MPSLGPPaperTrainingMain`**, working directory **`$PROJECT_DIR$`**, VM option **`--add-opens java.base/java.lang=ALL-UNNAMED`**, and **Make** before launch:

| Configuration | Program arguments | Intended use |
| --- | --- | --- |
| `MPSLGP Paper Dry Run` | `--dry-run --runs 1 --skip-existing` | Inspect all profiles/scenarios without training. |
| `MPSLGP Paper Training` | `--skip-existing` | Default 1,680-job IGD paper batch. |
| [MPSLGP Full R1 H3 - 5 Seeds](../../../../../.run/MPSLGP%20Full%20R1%20H3%205%20Seeds.run.xml) | `--algorithms full --indicators IGD --scenarios R1,H3 --runs 5 --start-run 0 --parallel 1 --skip-existing` | Full MPSLGP only: five seeds (0--4) for each of R1 and H3, ten jobs in total. |
| `MPSLGP Paper Smoke` | `--smoke --scenarios R1,H3 --runs 1 --skip-existing` | Isolated 14-job training smoke batch. |
| `MPSLGP Paper Indicator Robustness` | `--algorithms full --indicators IGD,HV,GD --skip-existing` | Full-profile aggregation-indicator comparison. |

The five-seed configuration uses the formal paper budget (total population 1,000 and 50 evaluated generations), not the smoke budget. R1 contains two same-objective tasks; H3 contains three tasks with mixed objective pairs. Each scenario runs seeds 0, 1, 2, 3, and 4 sequentially, and verified completed runs are skipped.

The child heap is controlled by the runner's `--heap`, not by increasing only the launcher's IntelliJ heap. These configurations do not start final held-out testing.

### Results, completion checks, and resuming

The result root contains one logical run folder per algorithm/indicator/scenario/run, for example `full\IGD\R1\run00`. A logical folder stores the full `effective.params`, run metadata (`run.properties`), launch command (`command.txt`), and a lock used to protect run ownership. Training itself writes into numbered **`attempt-NNNN`** subfolders. Failed attempts are preserved; a subsequent matching run resumes with a new attempt rather than reusing partially written outputs.

`completed.properties` is written only after the child exits with code zero **and** its final-generation statistics/front pass validation; successful output hashes are recorded. `--skip-existing` requires both a matching configuration and intact validated successful outputs. A folder, log, or exit code alone is not evidence of completion. A failed or incomplete matching run is retried; a changed configuration at an existing logical run path is an explicit error requiring another `--results` root.

Each invocation writes a separate batch report under **`batches\batch-*`**, including the summary and **`algorithm-configurations.csv`**. Summaries identify the actual attempt paths, including those referenced by skipped completed runs. Console events **START**, **GEN**, **DONE**, **FAILED**, and **SKIP** identify algorithm, indicator, scenario, run, and seed, so parallel jobs can be distinguished.

**Budget reporting is not a simulator-call audit.** A paper run has a configured retained-population budget of `1000 x 50 = 50,000` population slots across all tasks. The runner checks recorded output budgets and final statistics/fronts; these checks do not instrument every actual simulation call. Phenotype characterization, normalization baselines, and other evaluation work must not be inferred from that product alone. Transfer diagnostics describe observed offspring credit and survival, not an independent total-call counter. Compare paper-quality training results only after checking effective configurations and the compatible evaluation/testing protocol.

## Core implementation parameters

The following is a standalone two-task example, not a replacement for the runner's paper profile:

```properties
mpslgp.num-tasks = 2
mpslgp.task-allocation-offset = 0
mpslgp.transfer-probability = 0.80
mpslgp.contribution-aware-task-inheritance = true
mpslgp.donor-task-inheritance-threshold = 0.65
mpslgp.transfer-start-generation = 5
mpslgp.adaptive-transfer = true
mpslgp.use-no-transfer-gate = true
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

Adaptive transfer uses a no-transfer baseline utility in the softmax decision by default (`mpslgp.use-no-transfer-gate = true`). This prevents two-task experiments from degenerating into a fixed always-one-donor distribution: positive contribution increases the task-region transfer probability, while negative contribution decreases it toward same-task selection. In adaptive mode, `mpslgp.max-transfer-probability` is the transfer budget; `mpslgp.transfer-probability` is used by the fixed-transfer baseline. When `mpslgp.scale-transfer-budget-by-donor-count = true`, the adaptive transfer budget is divided by `mpslgp.num-tasks - 1`, making three-or-more-task runs more conservative without adding validation evaluations.

With `mpslgp.use-no-transfer-gate = false` and adaptive transfer enabled, `P(donor) = B * softmax(U[receivingTask][donorTask][region] / temperature)` over donor tasks only, and `P(local) = 1 - B`. `B` is the existing donor-count-scaled budget, clamped to `[0, 1]`. The no-gate ablation ignores both `mpslgp.no-transfer-utility` and per-donor `mpslgp.min-transfer-probability` floors so that the conditional donor probabilities remain the utility softmax. It still respects `mpslgp.transfer-start-generation`; fixed-transfer and no-transfer modes are unaffected. With two tasks, the only donor has conditional probability one, but cross-task mating still occurs with probability `B`, not necessarily one.

`mpslgp.task-allocation-offset` defaults to zero and is normalized modulo the task count, including negative offsets. It rotates initial round-robin task assignment and balanced survival quota remainders consistently, including tie-breaking when a missing task's quota must be redistributed. Already assigned offspring task labels are not overwritten. The offset is fixed within a run, not advanced each generation.

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
& $java -cp '<compiled-classes>;libraries\*' mengxu.algorithm.multiobjective.MPSLGP.MPSLGPRegressionTest --core-only
& $java -cp '<compiled-classes>;libraries\*' mengxu.algorithm.multiobjective.MPSLGP.MPSLGPRegressionTest
& $java -cp '<compiled-classes>;libraries\*' mengxu.algorithm.multiobjective.MPSLGP.MPSLGPRegressionTest --smoke
```

`--core-only` checks directed two-/three-task gated and ungated transfer mathematics, conditional donor sampling, budget scaling, task allocation, transfer modes, and offspring credit without configuring or running a simulator. The default suite also checks parameter setup and existing task-isolation regressions.

The regression runner's `--smoke` checks use 24 individuals, 200 recorded jobs, and three generations, including heterogeneous objectives, adaptive transfer, fixed transfer, no transfer, and single-task operation. They stop before final statistics are written. This is distinct from the paper batch runner's `--smoke`, which trains the selected profile/scenario matrix and validates final outputs. Run from the repository root, or set `-Dmpslgp.params=<absolute-path-to-3tasks-params>` and use absolute classpath entries.

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
