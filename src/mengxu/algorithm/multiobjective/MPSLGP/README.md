# MPSLGP

This package contains an isolated implementation of multitask Pareto set learning GP (MPSLGP) based on the original single-task PSLGP package.

Main entry points:

- `GPRuleEvolutionStatePSL`: MPSLGP evolution state with task-indexed individuals, preference-region adaptive transfer utilities, and transfer diagnostics.
- `PSLEvaluator` and `KNNsurrogateClearingPSLEvaluatorbasedonHV`: task-specific evaluation support through `eval.problem.0`, `eval.problem.1`, ... in the parameter file.
- `PSLParentSelection`: same-task or peer-task parent selection controlled by fixed transfer probability or by adaptive task-region probabilities.
- `multitreeClearingFirstMPSLGP`: local copy of the PSLGP clearing utility, kept inside this package to avoid changing the original PSLGP clearing behavior.
- `multipletreegp-dynamic-MPSLGP.params`: example two-task MPSLGP configuration.
- `MPSLGPPaperTrainingMain`: isolated training batches for the paper profiles, with per-run configuration snapshots and resumable attempts.
- [`MPSLGPPaperTestMain`](../../../ruleanalysis/MPSLGP/MPSLGPPaperTestMain.java): held-out testing of one final training-selected dual-tree heuristic per task from completed MPSLGP runs.

## Preference runtime v2

The old training path did not recognise the MPSLGP state when evaluating GP terminals. A direct call with that state returned `Pone = 100` for both `w1 = 0.2` and `w1 = 0.8`. The training evaluator also missed the MPSLGP simulation-state binding. Meanwhile, temporary phenotype contexts did not copy its terminal-normalisation mode. Formal held-out tests used the original PSLGP state and did receive the preference.

Shared terminal evaluation, simulation-state binding, decision-time normalisation bounds, phenotype contexts and preference-biased terminal sampling now recognise both state types. Paper mode remains `terminal-normalisation = 2`: `Pone = 100*w1` and `Ptwo = 100*w2`, in training and testing. Modes 0 and 1 retain their original unit-scale preference values. The test protocol and the meaning of its preference inputs have not changed.

**Fail-fast checks:** a GP tree containing `Pone` or `Ptwo` now requires a supported preference state. A missing state no longer produces a constant 100. Weight vectors must be present, finite, in `[0,1]`, sum to one and have a valid index. Temporary phenotype contexts copy the source mode and own a copy of the supplied vector. Unsupported source states and mixed legacy/v2 analysis inputs are errors.

Before training or testing, an automatic self-check runs the actual loaded GP nodes, both training-evaluator overloads and routing/sequencing phenotype code on fixed inputs. It checks `20/80` and `80/20`, rejects missing contexts, and starts no scheduling simulation. A stale terminal or phenotype class causes an explicit `Rebuild Project` error. The same check runs inside each worker JVM and in direct MPSLGP state setup.

Use **Build > Rebuild Project** before starting a batch. Both saved configurations run the preference self-check automatically. For a standalone check without an experiment, run the `mengxu.algorithm.multiobjective.MPSLGP.MPSLGPPreferenceRuntimeCheck` main class directly; it prints the loaded class location and class-file hashes. New run metadata records the check and critical class hashes. If those class files change during a training/test task, the task fails rather than being marked completed; do not rebuild while an experiment is running.

These checks guard the specified input and execution paths; they are not a proof that every future algorithm change is correct. The regressions deliberately place stale classes first on a child JVM's classpath and confirm that startup refuses them. Single-generation training uses the valid midpoint preference `(0.5,0.5)` rather than an undefined grid.

Final output is also checked with the strict task-rule reader before training is marked completed. The final generation writes its Top-N/front section once, even when it falls on a periodic reporting generation. Duplicate, incomplete or ambiguous selected-rule sections fail validation and remain retryable attempts.

**All MPSLGP profiles need fresh training and held-out testing.** Testing the old trees again cannot repair how they were trained. Keep the old runs and their analyses as legacy records; they are not evidence for the corrected learner. No old experiment or paper result is automatically overwritten or regenerated.

New training snapshots contain `mpslgp.preference-runtime-version = 2`, which changes their configuration fingerprints. The v1 file-format labels are unchanged; use the runtime marker to identify corrected runs. The default roots are now:

- Training: `src\mengxu\ruleanalysis\MPSLGP\paper-runs-preference-v2`.
- Held-out tests: `src\mengxu\ruleanalysis\MPSLGP\paper-tests-preference-v2`.

Pointing corrected training at a legacy run is a configuration error, including with `--skip-existing`. The test launcher rejects training without the v2 marker before writing test outputs or starting workers. The low-level worker checks it too. Historical analysis remains read-only, prints a legacy warning and records its version; it cannot pool legacy and corrected runs. For corrected summaries, pass both new roots explicitly. Analysis tools remain available as Java entry points rather than saved IDEA configurations.

Only two MPSLGP IDEA configurations are kept:

| Configuration | Work |
| --- | --- |
| [MPSLGP Paper Training](../../../../../.run/MPSLGP%20Paper%20Training.run.xml) | Selected algorithm, all eight groups, seeds 0--29, IGD; 240 training jobs per algorithm, up to five child JVMs. |
| [MPSLGP Paper Test](../../../../../.run/MPSLGP%20Paper%20Test.run.xml) | Matching 540 task tests per algorithm, each with 200 preferences and 30 replications; up to five child JVMs. Run only after the selected training finishes. |

Both default to `--algorithms full`, use the new roots explicitly, build before launch, and include `--parallel 5 --heap 2g --skip-existing`. Change the `--algorithms` value in **both** configurations to select a profile. The seven choices are `full`, `no-transfer`, `fixed-transfer`, `task-pair`, `survival-only`, `no-gate` and `primary-task`. A comma-separated list such as `full,no-transfer,fixed-transfer` schedules those methods under the same global five-worker cap. Do not change the roots back to the legacy directories. Other shared run configurations, including GPMain, are removed; their Java entry points remain available.

IDEA also stores temporary configurations locally in `.idea/workspace.xml`. To remove old entries there, close IDEA first, remove only obsolete RunManager configurations/list entries, then reopen the project. Keep the two shared configurations above; do not remove their program arguments. The project-local list has been cleaned to select Training by default.

Start with a dry run and a small smoke run, not the full batch. The regression checks below cover actual GP-node values, both evaluation-model overloads, replication loops, routing/sequencing phenotype contexts, original-test numerical parity, and rejection of legacy completion records.

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
| `--results PATH` | Result root; default repo-relative `src\mengxu\ruleanalysis\MPSLGP\paper-runs-preference-v2`. Smoke runs use a separate namespace. |
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

Select [MPSLGP Paper Training](../../../../../.run/MPSLGP%20Paper%20Training.run.xml). Its saved program arguments are:

```text
--algorithms full --indicators IGD --scenarios R1,R2,R3,R4,R5,H1,H2,H3 --runs 30 --start-run 0 --parallel 5 --heap 2g --skip-existing --results src\mengxu\ruleanalysis\MPSLGP\paper-runs-preference-v2
```

Edit `--algorithms` in **Run > Edit Configurations > Program arguments**. Changing it to `no-transfer`, for example, trains that profile across all eight groups and seeds 0--29. Each profile has 240 training jobs. Five independent group/seed jobs can overlap; a free worker starts the next queued job. The formal population, generation and simulation budgets are unchanged.

The child heap is controlled by `--heap`, not by the launcher's own IDEA heap. Five children can use **10 GB** of heap in total, plus JVM and IDE overhead. Training does not start held-out testing automatically. `--skip-existing` skips only matching, verified completed jobs; its supported alias is `--skip existing`, not `-skip existing`.

### Results, completion checks, and resuming

The result root contains one logical run folder per algorithm/indicator/scenario/run, for example `full\IGD\R1\run00`. A logical folder stores the full `effective.params`, run metadata (`run.properties`), launch command (`command.txt`), and a lock used to protect run ownership. Training itself writes into numbered **`attempt-NNNN`** subfolders. Failed attempts are preserved; a subsequent matching run resumes with a new attempt rather than reusing partially written outputs.

`completed.properties` is written only after the child exits with code zero **and** its final-generation statistics/front pass validation; successful output hashes are recorded. `--skip-existing` requires both a matching configuration and intact validated successful outputs. A folder, log, or exit code alone is not evidence of completion. A failed or incomplete matching run is retried; a changed configuration at an existing logical run path is an explicit error requiring another `--results` root.

Each invocation writes a separate batch report under **`batches\batch-*`**, including the summary and **`algorithm-configurations.csv`**. Summaries identify the actual attempt paths, including those referenced by skipped completed runs. Console events **START**, **GEN**, **DONE**, **FAILED**, and **SKIP** identify algorithm, indicator, scenario, run, and seed, so parallel jobs can be distinguished.

**Budget reporting is not a simulator-call audit.** A paper run has a configured retained-population budget of `1000 x 50 = 50,000` population slots across all tasks. The runner checks recorded output budgets and final statistics/fronts; these checks do not instrument every actual simulation call. Phenotype characterization, normalization baselines, and other evaluation work must not be inferred from that product alone. Transfer diagnostics describe observed offspring credit and survival, not an independent total-call counter. Compare paper-quality training results only after checking effective configurations and the compatible evaluation/testing protocol.

## Paper held-out testing

[`MPSLGPPaperTestMain`](../../../ruleanalysis/MPSLGP/MPSLGPPaperTestMain.java) tests **existing completed MPSLGP training artifacts only**. It does not retrain, execute PSLGP, modify training artifacts, or select individuals using test scores. There is no `--ensemble` option: each task uses exactly **one fixed sequencing/routing rule pair**, selected as the first training-ranked individual in that task's final `Top N individuals by MPSLGP Task:` block. Per-generation best records and printed Pareto fronts are not alternative selection sources. Tree 0 remains sequencing, Tree 1 remains routing, and the printed expressions and preference terminals are preserved without simplification.

For the completed **full / IGD / R1,H3 / seeds 0..4** training batch, the default test schedules **25 task jobs = (2 R1 tasks + 3 H3 tasks) x 5 runs**. Each task job evaluates **200 preferences x 30 replications = 6,000 replications**, for **150,000 replications** across the batch. These are not additional training runs.

### Test protocol and input validation

The test protocol uses the same `SchedulingSet.generateSet` factory and the original PSLGP `generatePreferences` and `calcFitnessWithPreference` helpers. It fixes the **test seed to 968356**, uses **`dynamic-job-shop` / `missing-<utilization>-1.5`** for each task's utilization and objectives, and retains **5,000 recorded jobs + 1,000 warmup jobs** and **`terminalNormalisation = 2`**. A separate child JVM per task job isolates mutable simulator state; `--parallel` controls how many such children run concurrently.

**Final-rule initialization alignment:** `HeterogeneousSimulation` construction consumes RNG draws for fixed transport and machine-rate constants; `DynamicSimulation.reset(seed)` reseeds without rebuilding those constants. Consequently, the first fresh-set evaluation can differ substantially from evaluations after reset. Legacy PSLGP readers evaluate earlier generations before the final rule, so their final-rule CSVs reflect a post-reset starting state. The MPSLGP final-only runner resets the newly generated scheduling set once before testing, then uses the unchanged PSLGP `calcFitnessWithPreference` helper. If an existing baseline CSV instead came from a custom final-only **fresh-set** evaluator, use matching initialization or retest before comparing results.

For each selected training run, the input `completed.properties` identifies the successful attempt directory; do not assume that `attempt-0001` is the successful attempt. Source hashes, generation, and task metadata must verify before evaluation. The completed formal runs have final evaluated **generation 49** (zero-based); the CSV records that actual generation, not 50 or a hard-coded replacement. A missing, altered, ambiguous, or incompatible input fails explicitly instead of falling back to another task, individual, or attempt.

The legacy evaluator excludes invalid replications when calculating fitness. When all replications for an objective are invalid, it returns **`-1`**. The test runner preserves this exact sentinel in both CSVs, emits an **`[INVALID FITNESS]`** warning with the preference index, and continues the remaining preferences. Other negative values and nonfinite final results remain errors. The existing PSLGP baseline source remains unchanged.

### Test command-line options

These defaults belong to the **test runner**, not the larger training matrix above.

| Option | Meaning / default |
| --- | --- |
| `--algorithms LIST` | Comma-separated MPSLGP profiles to test; default `full`. Does not launch PSLGP. |
| `--indicators LIST` | Training indicator namespaces to read; default `IGD`. This does not calculate a held-out HV/IGD/GD comparison by itself. |
| `--scenarios LIST` | Comma-separated scenario IDs; default `R1,H3`. |
| `--runs N` | Runs per algorithm/indicator/scenario; default `5`. |
| `--start-run N` | First zero-based training run index/seed; default `0`, giving seeds `0..4` with five runs. |
| `--tasks LIST` | Optional comma-separated, zero-based task selection; otherwise test all tasks in each selected scenario. |
| `--preferences N` | Preference samples for the fixed rule pair; default `200`, or `3` in smoke mode. |
| `--replications N` | Simulation replications per preference; default `30`, or `1` in smoke mode. |
| `--parallel N` | Concurrent child JVMs, one task job per child; default `1`. |
| `--heap SIZE` | Heap limit per child JVM; default `2g`. |
| `--training-root PATH` | Completed v2 training root; default repo-relative `src\mengxu\ruleanalysis\MPSLGP\paper-runs-preference-v2`. Alias: `--results PATH`. |
| `--output PATH` | Test output root; default repo-relative `src\mengxu\ruleanalysis\MPSLGP\paper-tests-preference-v2`. Set it explicitly for other experiment roots. |
| `--skip-existing` | Skip only matching, verified completed test jobs. Alias: `--skip existing`. |
| `--dry-run` | Inspect the planned test batch without launching evaluation children. |
| `--smoke` | Isolate outputs under `<output>\smoke` and default to three preferences and one replication. The simulator remains exactly 5,000 recorded + 1,000 warmup jobs. |

**Test smoke is not training smoke.** It reads the selected completed training runs and reduces only the default preference/replication counts, not the PSLGP simulator workload. The smoke configuration below selects seed 0 for both scenarios: five task jobs and 15 preference/replication evaluations by default. It is still a real simulation workload. Use dry run first, and obtain separate approval before launching the full 150,000-replication test batch.

### Test commands and IntelliJ configurations

Build the project first and run from the repository root, using the same JDK, classpath, and VM access option as training:

```powershell
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$cp = 'out\production\GPDFJSS-MENG;libraries\*'
$main = 'mengxu.ruleanalysis.MPSLGP.MPSLGPPaperTestMain'

# Inspect the formal 25-task batch; no evaluation children.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --algorithms full --indicators IGD --scenarios R1,H3 --runs 5 --start-run 0 --preferences 200 --replications 30 --parallel 1 --skip-existing --dry-run

# Bounded test smoke: seed 0, five task jobs, output\smoke; simulator workload unchanged.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --algorithms full --indicators IGD --scenarios R1,H3 --runs 1 --start-run 0 --smoke --parallel 1 --skip-existing

# Formal held-out tests: launch only when explicitly approved.
& $java --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp $main --algorithms full --indicators IGD --scenarios R1,H3 --runs 5 --start-run 0 --preferences 200 --replications 30 --parallel 1 --skip-existing
```

Select [MPSLGP Paper Test](../../../../../.run/MPSLGP%20Paper%20Test.run.xml). It uses module **GPDFJSS-MENG**, main class **`mengxu.ruleanalysis.MPSLGP.MPSLGPPaperTestMain`**, working directory **`$PROJECT_DIR$`**, VM option **`--add-opens java.base/java.lang=ALL-UNNAMED`**, and **Make** before launch. Its saved program arguments are:

```text
--algorithms full --indicators IGD --scenarios R1,R2,R3,R4,R5,H1,H2,H3 --runs 30 --start-run 0 --preferences 200 --replications 30 --parallel 5 --heap 2g --skip-existing --training-root src\mengxu\ruleanalysis\MPSLGP\paper-runs-preference-v2 --output src\mengxu\ruleanalysis\MPSLGP\paper-tests-preference-v2
```

Set `--algorithms` to the same selection as training. Each algorithm covers 18 group/task targets (six two-task groups and two three-task groups) over seeds 0--29: **540 task tests**, or 3,240,000 replications before verified skips. Missing training completions are errors; finish the selected training batch first. The configuration starts no training.

### Parallel testing of five or more heuristics

Select **MPSLGP Paper Test** in IDEA to test up to five task heuristics at once. It defaults to `full`; change `--algorithms` to test another completed profile without retraining it.

Each task job tests one final training-selected sequencing/routing pair in its **own child JVM**. Its 200 preferences and 30 replications per preference are unchanged. `--parallel 5` does not mean Top-5 candidates, five ensembles, or a reduced test set. The worker processes isolate the simulation and GP state. Completed jobs free a worker slot for the next queued task.

The runner accepts any positive `--parallel N`; the saved configuration uses five. With `--heap 2g`, five children can use 10 GB of heap; allow extra memory for JVM overhead and IDEA. The launcher's own VM heap setting does not replace `--heap`. Avoid running training and testing simultaneously: each has its own worker cap and builds before launch, so concurrent launchers can exceed the memory budget or change class files during an active experiment.

`--skip-existing` reuses verified results even if the concurrency changes. Stop an older test launcher before resuming the **same batch** with a parallel configuration, so two launchers do not compete for the same task locks. Existing failed attempts are preserved and matching unfinished tasks are retried. Do not manually copy or delete results to change the parallelism.

Append `--dry-run` temporarily to the training or test configuration to verify its plan without starting worker JVMs, then remove it for real runs. The test dry run still requires completed training. The startup log reports the worker count and heap. Synchronized regression workers verify that five training jobs and five test tasks can overlap without exceeding their caps; test coverage also checks eight-worker support and resuming after a concurrency change.

### Test outputs, aggregation, and resuming

Each task job writes to its own numbered attempt under the test root:

```text
<output>\<algorithm>\<indicator>\<scenario>\runNN\taskNN\attempt-NNNN\
```

The standard per-run CSV keeps the baseline filename, for example **`missing-0.7-1.5-0-ParetoFront.csv`** for utilization 0.7 and run 0. It has this exact single-heuristic header, including the baseline spelling `ParetoFromGeneration`:

```csv
Run,ParetoFromGeneration,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize,RoutRuleUniqueTerminals,Obj,TrainFitness0,TestFitness0,TrainFitness1,TestFitness1
```

A formal task job produces **200 data rows**, one per preference, all using the same selected rule pair and its recorded training objectives. The test fitness in each row comes from the 30-replication evaluation for that preference. This is not a collection of 200 independently selected individuals. A separate **`preference-results.csv`** records the preference index and both weights without changing the baseline CSV schema. **`test.properties`** and **`selected-heuristic.properties`** retain task/objective metadata, source hashes, and the exact selected expressions.

Only when **all selected task jobs succeed** does the batch produce per-target aggregated CSVs under:

```text
<output>\batches\batch-*\aggregated\<algorithm>\<indicator>\<scenario>\taskNN\
```

Each aggregate preserves the same baseline header and removes the run index from the filename, for example **`missing-0.7-1.5-ParetoFront.csv`**. The `Run` column preserves each source run's identity; different task targets are not pooled into one front.

Completed test markers hash the test specification and output files. `--skip-existing` skips only an intact successful result with a matching specification; changed configurations at an existing logical job path are explicit errors, not silently reused outputs. Use a separate `--output` root when changing a test specification. Failed attempts are preserved for diagnosis, and matching retries use new attempt directories. Smoke results remain isolated under `<output>\smoke`.

For newly completed attempts, `completed.properties` and `status.properties` also record `invalid.preference.rows` and `valid.preference.rows`; a row is invalid if either test objective is `-1`. **COMPLETED means the full preference grid was recorded and verified, not that every outcome is valid.** The batch summary message and `[TEST WARNING]` output disclose invalid rows, including when resuming. Old all-valid completions remain reusable without these new count fields. Aggregated raw CSVs retain `-1`, without replacing or dropping affected preferences.

### Comparing held-out results

Test fitness values are **raw objective values**, not independently normalized values for each method or run. A comparable **HV/IGD/GD** analysis needs **common per-target reference data and normalization/reference settings** across methods and runs. Keep heterogeneous task objective pairs separate; selecting a training indicator namespace alone does not establish a valid held-out comparison.

An equal test protocol does **not** erase training-budget differences: MPSLGP uses **1,000 total individuals across tasks**, whereas the PSLGP baseline uses **1,000 individuals per task**. Report that distinction explicitly. Likewise, evaluating one fixed heuristic over preferences is not the same comparison as evaluating a multi-individual Pareto front; disclose and align the selection/front protocol rather than interpreting an equal number of test rows as equivalent search effort or retained solutions.

### Descriptive result summaries

`mengxu.ruleanalysis.MPSLGP.MPSLGPTestSummaryMain` validates a complete selection of formal Full/IGD results and writes per-run indicators, mean/sample-standard-deviation summaries, normalized reference fronts, an explicit metric protocol, and input hashes. Its first two arguments are the test root and a **new** output directory. With no other options it preserves the original R1/H3 seeds 0--4 selection. Optional `--scenarios`, `--runs`, and `--start-run` select a different scope; `--training-root` overrides the default sibling `paper-runs` source. `--invalid-outcomes reject` is the default; `--invalid-outcomes valid-only` explicitly selects conditional metrics over valid preferences, retaining every seed and separately auditing excluded outcomes. At least two runs are required for sample standard deviation. A seed with no valid preferences remains an error. `--self-test` checks the metric arithmetic without loading results.

For the complete Full batch, first finish **MPSLGP Paper Test** with `--algorithms full`. Summary shortcuts are no longer saved in IDEA; run main class **`mengxu.ruleanalysis.MPSLGP.MPSLGPTestSummaryMain`** directly with these arguments and a new output directory:

```text
"$PROJECT_DIR$\src\mengxu\ruleanalysis\MPSLGP\paper-tests-preference-v2" "$PROJECT_DIR$\src\mengxu\algorithm\multiobjective\MPSLGP\paper-draft\results\full-all-groups-thirty-seeds-v2" --training-root "$PROJECT_DIR$\src\mengxu\ruleanalysis\MPSLGP\paper-runs-preference-v2" --scenarios R1,R2,R3,R4,R5,H1,H2,H3 --runs 30 --start-run 0 --invalid-outcomes valid-only
```

The summary launches no simulations. It verifies the selected training jobs and every expected held-out completion, protocol, source hash, and output before creating the summary directory. Missing tests are explicit errors; it never silently uses fewer seeds. Complete inputs produce 540 per-task run rows, 108,000 preference outcomes and 18 group/task summary rows. `table-vii-mpslgp.csv` contains the MPSLGP HV/IGD/GD means and sample standard deviations; `table-viii.csv` contains the distinct/nondominated outcome and tree-node summaries. Published PSLGP-I values are not recomputed by this program. Rerunning a summary requires a new output directory to preserve its reference data and provenance.

An otherwise complete test can contain invalid-fitness sentinels. The default summary rejects them before writing analysis. The full-batch paper analysis explicitly uses the agreed **valid-only** protocol: remove a preference vector if either objective is `-1`, construct bounds/references and calculate indicators from the remaining vectors, and retain all 30 seeds per target. `validity-per-run.csv`, `validity-summary.csv`, and `invalid-preferences.csv` expose all excluded rows. These are conditional quality scores, not failure-penalized scores or evidence that all preferences succeeded. The complete batch contains 114 invalid vectors among 108,000, in four task runs; no seed is excluded.

After the Java summary, generate the paper table rows and published contextual comparison with:

```powershell
& '.\src\mengxu\ruleanalysis\MPSLGP\BuildPaperResultTables.ps1' `
  -SummaryDirectory '.\src\mengxu\algorithm\multiobjective\MPSLGP\paper-draft\results\full-all-groups-thirty-seeds' `
  -PublishedContext '.\src\mengxu\algorithm\multiobjective\MPSLGP\paper-draft\published-pslgp-context.csv' `
  -ReferencePdf '.\src\mengxu\algorithm\multiobjective\MPSLGP\Reference\Pareto_Set_Learning_through_Genetic_Programming_for_Multi-Objective_Dynamic_Scheduling.pdf'
```

The formatter validates all 18 target rows and all ten published scenarios, preserves existing outputs, and saves `table-vii-comparison.csv`, `table-viii-full.csv`, their LaTeX row fragments, and comparison input hashes. Published PSLGP-I values come directly from reference Tables IV--VI, not a local rerun. Different indicator reference data and population allocations prevent a calibrated cross-method ranking.

The local indicators pool objective bounds and a nondominated empirical reference separately for each target scenario across the selected groups (scenario 1 pools R1 and H3 in the original five-seed selection, and R1/R4/H1/H3 in the full selection). HV uses reference `(1.1,1.1)` after min--max normalization and is divided by `1.21`. IGD/GD use arithmetic mean Euclidean distances as written in the paper, not the alternative `sqrt(sum(squared distances))/n` convention in some legacy helpers. GD includes every valid preference outcome, not just the nondominated subset; under valid-only scoring its denominator can be less than 200. Standard deviations are across the selected training seeds, not across preferences or simulation replications. Changing the selected pool also changes the reference data, so newly computed full-batch scores must replace, not be appended to, the initial five-seed scores.

These are **descriptive MPSLGP-only metrics**, not numbers calibrated to the published PSLGP tables. The empirical reference includes the evaluated runs and can be optimistic or sensitive to extreme outcomes. Use raw PSLGP fronts and shared reference settings before drawing cross-method rankings or significance conclusions. The initial paper summaries are saved under `paper-draft/results/full-r1-h3-five-seeds`; the complete conditional 30-seed analysis is under `paper-draft/results/full-all-groups-thirty-seeds`. Published context covers all ten scenarios in `paper-draft/published-pslgp-context.csv`. The paper's complete Results section and directly updated protocol/conclusion text are highlighted in blue.

## Transfer ablation

Run [`MPSLGPTransferAblationMain`](../../../ruleanalysis/MPSLGP/MPSLGPTransferAblationMain.java) directly for **full, no-transfer and fixed-transfer**; no analysis shortcut is saved in IDEA. It reads completed training/tests only and launches no simulations. For corrected results, pass the v2 roots and a new output directory:

```text
--algorithms full,no-transfer,fixed-transfer --tests src\mengxu\ruleanalysis\MPSLGP\paper-tests-preference-v2 --training-root src\mengxu\ruleanalysis\MPSLGP\paper-runs-preference-v2 --output src\mengxu\algorithm\multiobjective\MPSLGP\paper-draft\results\full-vs-no-vs-fixed-transfer-thirty-seeds-v2 --invalid-outcomes valid-only
```

Without `--algorithms`, the program keeps the original Full/no-transfer default; `--algorithms full,fixed-transfer` also supports a separate two-method comparison.

For every matched group/seed, the program verifies that No-transfer differs from Full only in its three transfer-control fields. Fixed-transfer may differ only in `mpslgp.adaptive-transfer`; its fixed transfer probability and budget must match Full's configured values. Source/output hashes, completion markers, final selected heuristics and all 200 preference rows are checked before analysis.

**All selected methods are recomputed jointly.** Each scenario has one set of coordinatewise min/max bounds and one unique nondominated empirical reference pooled over all methods, groups and seeds. HV uses reference `(1.1,1.1)` and is divided by `1.21`; IGD/GD use arithmetic mean nearest-point Euclidean distances, with GD including all valid preference outcomes. Vectors with either objective `-1` are excluded under valid-only scoring, but every seed remains in the summary. Plot-only omissions never enter the analysis.

Three-method output goes to a **new** `paper-draft\results\full-vs-no-vs-fixed-transfer-thirty-seeds` directory; the old two-method output is preserved. If the selected output already exists, choose another path:

```text
--algorithms full,no-transfer,fixed-transfer --output "D:\results\transfer-ablation-02"
```

Supported selectors are `--algorithms`, `--tests`, `--training-root`, `--output`, `--scenarios`, `--runs`, `--start-run`, and `--invalid-outcomes valid-only|reject`. Select Full and one or both supported controls. At least two and at most 60 runs are supported.

Outputs include:

- `per-run.csv`, `mean-std.csv`, `table-comparison.csv`, and `table-rows.tex`: all method/seed scores and the same metric-grouped 18-target table style. CSV values use five decimals; the expanded three-method LaTeX table uses four decimals and omits leading zeros to keep the table readable. Bold marks the best unrounded mean among the selected methods.
- `validity-summary.csv` and `invalid-preferences.csv`: failure counts/rates, affected runs and single-outcome response counts, retaining the original preference identities.
- `paired-gains.csv`, `paired-tests.csv`, and `paired-test-summary.csv`: `Comparator` identifies the control. Positive gains mean Full better (HV Full minus control; distances control minus Full). Exact two-sided signed-rank tests omit zero differences, use midranks for ties and a doubled-rank dynamic program.
- Holm adjustment covers the whole family at level 0.05: **108** tests for Full against both controls, or **54** for one control. No-transfer versus Fixed-transfer is not part of the paired test family. The unit is a matched training seed, not a preference or replication.
- `scenario.*.reference.csv`, `protocol.properties`, `input-manifest.csv`, and `completed.properties`: joint reference fronts/bounds, method definitions, input/output hashes and analysis fingerprints.

Adding Fixed-transfer changes the shared reference pool, so the three-method scores replace rather than extend the old two-method table. The Full-only analysis, older two-method files, preference/EAF plots and published PSLGP-I table remain unchanged. The revised ablation section and table stay red; earlier blue edits remain blue.

Dependency-free regression entry points are `MPSLGPTransferAblationMainTest` and `MPSLGPPairedStatisticsTest` under `test\mengxu\ruleanalysis\MPSLGP`.

## Joint-task preference analysis

[`MPSLGPJointTaskAnalysisMain`](../../../ruleanalysis/MPSLGP/MPSLGPJointTaskAnalysisMain.java) analyzes the final task-specific heuristics from the **same joint training run**. It reads the completed Full/IGD test data and the existing descriptive paper summary; it performs no training or simulations. It uses the already bundled JFreeChart and iText libraries, so no Python installation or additional packages are needed.

### Run directly in IntelliJ IDEA

No analysis shortcut is saved in IDEA. To inspect results, run main class **`mengxu.ruleanalysis.MPSLGP.MPSLGPJointTaskAnalysisMain`** directly with module **GPDFJSS-MENG** and working directory **`$PROJECT_DIR$`**, for example:

```text
--groups R4,H3 --focus-panels all --hide-outliers H3:5
```

R4 and H3 each contain three tasks. Both figures use two group rows and three task columns: R4 above H3, with scenarios 1, 5 and 3/8 from left to right. Every panel uses the same selected seed. These example arguments focus all scatter panels independently and apply display-only outlier filtering to H3/scenario 5. Response curves, quality indicators and EAFs use the full data and are unaffected.

To select a specific run:

```text
--groups R4,H3 --seed 15
```

Other examples:

```text
--groups R1,H1
--groups R1,H1,H2 --seed 1
--groups R4,H3 --runs 30 --start-run 0 --output "D:\results\joint-analysis-01"
```

The default summary directory is `paper-draft\results\full-all-groups-thirty-seeds`. With no `--output`, each invocation creates a new `analysis-*` directory under `<summary>\joint-task-analysis` and prints its full path. An explicit output path must be new; existing files are never overwritten.

After compiling, the same entry point can run from PowerShell:

```powershell
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$cp = 'out\production\GPDFJSS-MENG;libraries\*'
& $java '-Djava.awt.headless=true' --add-opens java.base/java.lang=ALL-UNNAMED -cp $cp mengxu.ruleanalysis.MPSLGP.MPSLGPJointTaskAnalysisMain --groups R4,H3
```

| Option | Default / meaning |
| --- | --- |
| `--groups LIST` | `R4,H3`; any distinct groups from R1--R5/H1--H3. |
| `--runs N` | `30` consecutive seeds per group; at least two for sample standard deviation. |
| `--start-run N` | `0`; first selected seed. |
| `--seed N` | Optional explicit common seed within the selected range. |
| `--focus-panels LIST` | `all`, or comma-separated `GROUP:SCENARIO` identifiers. Selected scatter panels use their own displayed-outcome bounds plus 8% padding instead of shared limits; constant coordinates receive 0.5% value padding. All specified panels must exist in the selected groups. |
| `--hide-outliers LIST` | Optional comma-separated panel identifiers, e.g. `H3:5`. Display only: omit dominated valid vectors outside either coordinate's `Q1-1.5*IQR` / `Q3+1.5*IQR` fences, using linearly interpolated quartiles. All nondominated vectors are retained. These panels also receive independent focused axes. |
| `--summary PATH` | Existing paper-summary directory, including `protocol.properties`, `per-run.csv`, reference CSVs and input manifest. |
| `--output PATH` | Optional new exact output directory; otherwise create an isolated `analysis-*` directory. |
| `--help` | Print usage without reading results or plotting. |

### Representative seed and validation

Without `--seed`, the code ranks IGD across seeds separately within each group/task, averages task ranks within a group and then averages groups with equal weight. Tied IGD values receive midranks. It selects the **lower median** score (15th of 30), breaking score ties by seed index. This is one common representative seed, not a separately selected best seed for each task. All selected seeds still contribute to the exported joint statistics.

The shared paper-test reader verifies completion metadata, source/output hashes, the final selected heuristic and the complete 200-preference grid. The analysis checks these inputs against the summary snapshot and recomputes HV/IGD/GD to confirm the stored scores and reference data. Missing, changed or incompatible data fail before output creation. A `completed.properties` marker is written only after all data and figure exports succeed.

### Figures and numerical outputs

The preference figures and a separate empirical-attainment comparison are exported as high-resolution PNGs and vector PDFs; joint reliability is exported as a compact LaTeX table:

The plotting style follows the paper's algorithm colors: blue `#2563EB`, orange `#F97316`, light blue/orange, purple, and slate gray. Typography uses the manuscript's **Latin Modern Roman** family, with the same outlines rendered to PNG and embedded in vector PDFs. Axis labels use 27 px, tick labels and legends 23 px, and panel labels 25 px. Legends are below the plots. Overall titles, seed/statistic subtitles and explanatory footers are omitted; the manuscript captions contain the explanations.

Fonts are located through TeX Live/MiKTeX `kpsewhich` on PATH. The loader reads `lmr10.pfb`, `lmr10.afm`, `lmbx10.pfb` and `lmbx10.afm` without modifying or installing fonts. If TeX is unavailable, set the IDEA VM option `-Dmpslgp.fontDirectory="<directory containing those four files>"`. Missing fonts produce an explicit error rather than a substitute typeface. Font paths and hashes are retained in the output protocol.

| Figure | Content |
| --- | --- |
| `joint-fronts` | Pure preference distribution in separate task panels, without reference fronts or group overlays. Continuous blue--purple--orange color indicates `w1`; circles show displayed outcomes and dark outlines mark nondominated outcomes. Explicit focus/outlier options control only the display; no sample coordinates are moved. |
| `preference-response` | Compact objective curves against `w1`, with the legend close below the last row. Values use the paper's task-specific bounds. Invalid preferences create line gaps and gray strips. |
| `empirical-attainment` | Separate performance comparison across all selected seeds, not only the plotted representative seed. Repeated target scenarios have their own panels; group colors identify exact 25/50/75% attainment staircases and a shaded 25--75% envelope. |
| `group-summary-rows.tex` | A `\MPSLGPJointSummaryRows` macro containing joint-valid preference counts/rates and all-task responsive run counts/rates, replacing the former bar-chart figure. Responsiveness means more than one distinct valid outcome in every task. |

The figure uses a continuous colorbar. The audit CSV additionally groups preference indices into five bands (0--39, 40--79, 80--119, 120--159 and 160--199), matching controller region `min(4,floor(5*w1))`. Nondominated membership is computed within each heuristic's valid outcomes; different tasks are never pooled into a Pareto front. A duplicate nondominated outcome can correspond to multiple preference indices, so the band CSV explicitly distinguishes nondominated **preferences** from unique outcome counts.

The EAF is the fraction of training runs whose valid within-run front weakly dominates a queried raw objective pair. At probability `p`, the exact boundary uses the `ceil(p*n)`-th smallest per-run attainable second objective at each unique first-objective coordinate. With 30 seeds, the nominal 25/50/75% levels require 8/15/23 runs. Boundaries are right-continuous staircases, never diagonal interpolations. EAFs do not use the paper's normalization bounds or empirical reference front. Different scenarios remain separate; this is an R4/H3 grouping comparison, not an EAF reconstructed from the published PSLGP-I means.

With multiple selected groups, the EAF figure includes scenarios appearing in at least two groups. A single selected group shows its own scenarios. If no repeated targets exist, the program reports that no EAF comparison figure is generated. Invalid preferences follow the explicitly recorded valid-only treatment, retaining every selected seed.

The output also contains:

- `task-run.csv`: per-task/per-seed validity, unique/nondominated outcomes and verified quality scores.
- `group-run.csv` and `group-summary.csv`: aligned joint validity, all-task responsiveness, and mean/worst-task local IGD; standard deviations are across training runs.
- `preference-outcomes.csv` and `preference-band-summary.csv`: all raw outcomes, validity flags, bands and nondominated membership.
- `seed-selection.csv`: every candidate seed's rank score and the plotted choice.
- `front-viewports.csv`: exact axis bounds and valid/visible/excluded preference counts for each task panel.
- `plot-excluded-preferences.csv`: every display-only omitted vector, its original preference index/weights/objective values, and the filtering rule. In the default paper plot, H3/scenario 5 omits four of 200 vectors (indices 1--4); all other panels show 200/200.
- `eaf-boundaries.csv` and `eaf-level-summary.csv`: exact raw staircase vertices, probability/rank thresholds, run denominators and boundary sizes.
- `input-manifest.csv`, `protocol.properties`, and `completed.properties`: input hashes, selection/metric definitions and output hashes.

The initial actual-data plots are saved under `paper-draft\results\joint-r4-h3`; the paper-styled versions used by the manuscript are under `paper-draft\results\joint-r4-h3-paper-style`. They use common seed **15**, selected by the rule above, and include all **180 task runs / 60 joint group runs / 36,000 preference outcomes** in the numerical exports. The numerical data and selected seed are unchanged by styling. Run `mengxu.ruleanalysis.MPSLGP.MPSLGPJointTaskAnalysisMainTest` after compiling the analysis and test sources for the dependency-free synthetic regression suite.

## Manuscript references

The paper keeps its references in `paper-draft\reference.bib`. Add or edit BibTeX entries there and use their keys in `\cite{...}` in `mpslgp-method.tex`. Journal papers use `@article`; conference papers use `@inproceedings`. The manuscript uses the `IEEEtran` bibliography style.

Build from the paper directory with `latexmk`, which runs BibTeX when needed:

```powershell
Set-Location 'src\mengxu\algorithm\multiobjective\MPSLGP\paper-draft'
latexmk -pdf -interaction=nonstopmode -halt-on-error mpslgp-method.tex
```

The standard `IEEEtran.bst` does not print `doi` fields, so each entry also keeps its printed DOI in `note`. Keep both fields consistent when updating a DOI.

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
& $java -cp '<compiled-classes>;libraries\*' mengxu.algorithm.multiobjective.MPSLGP.MPSLGPPreferenceRuntimeTest
& $java -cp '<compiled-classes>;libraries\*' mengxu.algorithm.multiobjective.MPSLGP.MPSLGPPaperTrainingMainTest
& $java -cp '<compiled-classes>;libraries\*' mengxu.ruleanalysis.MPSLGP.MPSLGPPaperTestMainTest --simulation
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
