# Matched-Population PSLGP and NSGPII Experiments

## Scope

Run only the independent PSLGP and NSGPII baselines. Reuse the completed MPSLGP `full/IGD` training and generation-49 held-out outcomes in `paper-runs-preference-v2` and `paper-tests-preference-v2`. Do not retrain MPSLGP or change historical experiments. Subsequent manuscript changes belong only in `paper-draft-V2`.

The shared entry point is `mengxu.ruleanalysis.MPSLGP.NSGPIIPaperExperimentMain`; its historical class name is retained for compatibility. `--method PSLGP` selects native independent PSLGP-I; `--method NSGPII` selects native NSGPII. Without `--method`, the original NSGPII behaviour is preserved.

This experiment matches the configured population allocation and 50 evaluated generations, not wall time or every underlying simulation call. Brood preselection, phenotype/surrogate work, operators and native selection remain algorithm-specific. The existing Full/no-transfer ablation addresses transfer within MPSLGP; this baseline comparison assesses the complete learning methods under matched population shares.

## IDEA Run Order

The new configurations are in `.idea/runConfigurations`, with names beginning `SameBudget`. In IDEA, use **File > Reload All from Disk** if needed, then **Build > Rebuild Project**. The module is `GPDFJSS-MENG`, and the working directory is the project root. Do not rebuild while workers are running.

| Configuration | Purpose |
|---|---|
| SameBudget - 00 Self Test | Check quotas, defaults and safe interrupted-job retries using temporary fixtures; no simulations |
| SameBudget - 01 PSLGP Plan | List all 540 PSLGP jobs; no simulations or output directories |
| SameBudget - 02 NSGPII Plan | List all 540 NSGPII jobs; no simulations or output directories |
| SameBudget - 03 PSLGP Smoke Train | R4, seed 0, three small task runs through generation 49 |
| SameBudget - 04 PSLGP Smoke Test | Test those three saved conditioned heuristics |
| SameBudget - 05 NSGPII Smoke Train | R4, seed 0, three small task runs through generation 49 |
| SameBudget - 06 NSGPII Smoke Test | Test those three saved population fronts |
| SameBudget - 10 PSLGP Train | Formal independent PSLGP training, 540 jobs |
| SameBudget - 11 PSLGP Test | Formal PSLGP held-out preference sweeps |
| SameBudget - 20 NSGPII Train | Formal independent NSGPII training, 540 jobs |
| SameBudget - 21 NSGPII Test | Formal NSGPII final-front held-out testing |

Run 00, 01 and 02 first. Run 03 before 04 and 05 before 06. Once smoke checks complete, run 10 and wait for completion before 11; run 20 and wait for completion before 21. Formal configurations use at most five isolated workers, each with a 2 GB maximum heap, plus a 2 GB coordinator and JVM/native overhead. Avoid running the two formal batches simultaneously unless resources allow it. Changing concurrency does not change each worker's single-threaded algorithm.

## Matched Allocations

| Group | Task scenarios in order | Independent task populations |
|---|---|---|
| R1 | 1, 2 | 500, 500 |
| R2 | 3, 4 | 500, 500 |
| R3 | 6, 7 | 500, 500 |
| R4 | 1, 3, 5 | 334/333/333, rotating by seed |
| R5 | 8, 9 | 500, 500 |
| H1 | 1, 10 | 500, 500 |
| H2 | 5, 6 | 500, 500 |
| H3 | 1, 5, 8 | 334/333/333, rotating by seed |

For seed 0, task 0 receives the extra individual; for seed 1, task 1; for seed 2, task 2. Repeat this cycle for seeds 0--29. Each group/seed sums to 1,000, matching MPSLGP's configured allocation. The 18 group/task targets produce 540 jobs per baseline and 1,080 new training jobs in total. Records remain group-specific even for scenarios repeated across groups; the new results must not be replaced with historical 1,000-per-task results.

## Native Templates and Protocol

Both templates are read-only and loaded from the existing archive:

```text
C:/Users/xumen/Desktop/2026Paper/PSLGP/FinalResultsForPaper/FinalResultsForPaper/
  PSLIGD_BR/multipletreegp-dynamic/params/multipletreegp-dynamic.params
  fNSGPII/multipletreegp-dynamic/params/multipletreegp-dynamic.params
```

Override `--template <absolute-path>` if the archive moves, consistently for training and testing. The plan checks native state, breeder, evaluator and terminal configuration; PSLGP must use the IGD template. Native classes and operators are preserved. Delegating statistics/problem subclasses accumulate individual evaluator calls without changing fitness. The count file is created once at the final evaluated generation, avoiding repeated file replacement on Windows. This counter does not count every setup/manual-rule/phenotypic-characterisation simulation.

Formal jobs use population 500 or 333/334, 50 evaluated generations (0--49), disabled early-success termination, one training replication, simulation seed 0 rotated by generation, 5,000 measured jobs, 1,000 warm-up jobs and the existing ten benchmark scenarios. PSLGP retains its brood/surrogate procedure, 101 training preference entries, elite allowance 10, raw state terminals and the v2 preference encoding with terminal values `100*w_i`.

PSLGP saves the native final-generation Top-N list and tests only its first training-ranked individual. This matches the MPSLGP final task-ranked selection convention; it is deliberately not the historical baseline retest's recorded-Best selection. No test score selects the heuristic. The fixed tree pair is swept across 200 preferences, including both endpoints. NSGPII tests every heuristic on the final population Pareto front, without an external archive or a union across generations. Both use held-out seed 968356 and 30 replications; invalid recorded outcomes remain explicit rather than dropping seeds.

Smoke jobs are separate under `smoke/`: at most 24 group individuals (8 per R4 task), 200 training jobs, 50 warm-up jobs, 50 generations, one held-out replication and three PSLGP preferences. They validate generation-49 serialization/testing, not scheduling quality or full-size resource use.

## Outputs and Safe Reuse

```text
src/mengxu/ruleanalysis/MPSLGP/same-population-budget/
  PSLGP/matched/<group>/task<index>-S<scenario>/run<seed>/
  NSGPII/matched/<group>/task<index>-S<scenario>/run<seed>/
```

Each job has `training/` and `test-generation49/`. Training includes `effective.params`, `experiment.properties`, native statistics, elapsed-time CSVs, logs, evaluation-call metadata and `completed.properties`. The completion record verifies configuration and source hashes. Testing includes `test.properties`, raw `test-results.csv`, logs and a completion marker. PSLGP also writes `test-outcomes.csv` with explicit preference indices/weights; its CSV schemas match the existing held-out evaluator. NSGPII writes one row per selected front member.

`--skip-existing` reuses only complete compatible jobs. By itself, it still refuses incomplete training directories, with an explicit recovery message rather than a missing-file exception. Incompatible completed results are always refused.

The four SameBudget training configurations (03, 05, 10 and 20) also enable `--retry-incomplete`. This option requires `--action train --skip-existing`. Before workers start, the coordinator validates all existing jobs. A training directory without `completed.properties` is retryable only when its saved `experiment.properties` and `effective.params` match the current configuration/template fingerprint and no active training worker is detected for its input. Missing metadata or configuration mismatches require manual inspection; they are not automatically replaced.

When a retry starts, the entire interrupted `runXX` directory is moved to the sibling `runXX.incomplete-<UUID>`, preserving its logs, partial statistics and any old test output. The original path is then recreated for the same seed and population budget. `[RETRY PRESERVED]` reports the backup path. Checkpoints are disabled, so this restarts the interrupted job from generation 0, not its last recorded generation. Completed compatible jobs are skipped; unstarted jobs run normally. No old results are deleted.

After an interruption, stop the old coordinator/workers, reload the IDEA configuration from disk, rebuild, and rerun the same training configuration with the same output root. Do not remove the 30-run setting or change `--start-run`: verified completed jobs will be reused. Testing retains its strict completion checks and does not accept `--retry-incomplete`; preserve incomplete test directories manually before retesting. A new output root starts a separate batch and does not inherit completed jobs from the old root. Never run two coordinators against the same output root at once.

The original NSGPII configurations and default output root are unchanged. The new formal output directories are ignored by Git; the local IDEA directory is also ignored by the repository's existing policy.

## Completed Analysis

The completed V2 analysis is in [same-population-budget-20261120](../../algorithm/multiobjective/MPSLGP/paper-draft-V2/results/same-population-budget-20261120). From the repository root, using Python with the existing scientific-analysis dependencies (`numpy`, `pandas`, `scipy`, `javaproperties`, `matplotlib`), run:

```powershell
python src\mengxu\algorithm\multiobjective\MPSLGP\paper-draft-V2\results\same-population-budget-20261120\analyze_same_budget.py --self-test
python src\mengxu\algorithm\multiobjective\MPSLGP\paper-draft-V2\results\same-population-budget-20261120\analyze_same_budget.py
```

After compiling `paper-draft-V2/mpslgp-method.tex`, use the same analysis command with `--validate` to cross-check the indicators with dense pairwise distances, exact tests with SciPy, all formatted table cells and the compiled PDF. This final validation also uses the existing `pymupdf` dependency.

The script verifies the 1,080 new baseline training/test completion records and source/configuration hashes, population quotas, generation-49 selection, held-out protocol, and 540 reused MPSLGP task outcomes. Scenario-specific bounds/reference fronts are rebuilt from only these three matched-budget inputs; old normalized indicators are not reused. All 30 training seeds remain the independent units for each of the 18 group/scenario comparisons. Sample standard deviations use `ddof=1`; the 108 exact two-sided paired Wilcoxon tests omit exact-zero differences, use tied absolute midranks and alpha 0.05 without multiple-comparison correction, matching Table VI. Significant direction comes from paired rank sums, not the mean ordering.

Outputs include raw-source SHA256 provenance (`input-manifest.csv`), per-run metrics, 54 mean/SD summaries, paired-test details, comparison counts, ten scenario reference fronts, the protocol JSON and a Table VI-style LaTeX table. The V2 Results subsection includes the completed analysis and table while preserving the original unequal-allocation table.

The 76 invalid PSLGP preferences (one in R2/scenario 3, seed 26; 75 in R5/scenario 8, seed 23) are excluded only at the objective-pair level; neither seed is dropped. MPSLGP and NSGPII have no invalid outcomes. Reported indicators are conditional on valid outcomes. Group training time uses logged generation 0--49 elapsed seconds, counting joint MPSLGP once and summing independent task times; these cross-batch timings are not parallel makespan or proof of hardware-independent speedup.