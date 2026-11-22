# Aligned Matched-Population MPSLGP, PSLGP and NSGPII Experiments

## Scope

The corrected `comparison.protocol=aligned-v2-generation50` requires fresh training and held-out testing of all three methods. Each method evaluates generations 0--50 (`generations=51`, including generation 0) and tests the final generation-50 output. Do not reuse the previous MPSLGP trees, SameBudget baselines or aligned-v1 generation-49 results: the source protocol and final generation must match. Existing training/test data and all manuscript Results remain historical records and must not be overwritten or relabelled. Manuscript changes belong only in `paper-draft-V2`; revised method/experiment settings use `\key{}`, while Results await the rerun.

The independent baseline entry point remains `mengxu.ruleanalysis.MPSLGP.NSGPIIPaperExperimentMain`: `--method PSLGP` selects PSLGP-I and `--method NSGPII` selects NSGPII. MPSLGP uses `mengxu.algorithm.multiobjective.MPSLGP.MPSLGPPaperTrainingMain` and `mengxu.ruleanalysis.MPSLGP.MPSLGPPaperTestMain`. Both builders fingerprint the aligned protocol and use a separate `same-population-budget-aligned-v2-generation50` root. MPSLGP testing refuses training without the current marker; baseline reuse requires the new configuration fingerprint.

All methods use `AllIndexAllSwapCrossoverPipeline`, crossover/mutation/reproduction probabilities 0.80/0.15/0.05, and fixed seven-candidate tournaments sampled with replacement. MPSLGP draws the primary task by its population share before the tournament, then draws all candidates directly from the selected task. The second tournament uses the same task or the selected donor task. Mutation and reproduction follow the primary-task rule. Fitness comparators remain native: PSLGP/MPSLGP retain their IGD-based comparator; NSGPII retains rank/crowding.

The explicit protected elite allowance is ten per group, split 5/5 or rotating 4/3/3 by seed using the same quota rule as the population. MPSLGP takes elites task-wise; independent tasks receive the corresponding share. The elite copies are reserved in the next evaluated population and are re-evaluated on the current instance/preference. In the conditioned learners, only non-elite brood offspring compete for the remaining slots. This avoids unequal elite counts or losing PSLGP/MPSLGP elite copies in surrogate preselection. Smoke runs retain this mechanism; their relatively large elite fraction is not a quality experiment.

PSLGP and MPSLGP share the original objective-specific benchmark mapping: MAX_FLOWTIME -> SPT, MAX_WEIGHTED_FLOWTIME -> WSPT, MAX_TARDINESS -> ATC and MAX_WEIGHTED_TARDINESS -> WATC, all with WIQ routing. NSGPII's crowding normalization and existing non-environmental-selection replacement variant are unchanged. Shared population allocation and 51 evaluated generations do not imply equal wall time or every underlying simulation call; native surrogate/preselection work remains method-specific.

## IDEA Run Order

Exactly six shared configurations remain in `.run`. Other project configurations in `.run` and `.idea/runConfigurations`, plus stale workspace RunManager entries, were removed while IDEA was closed. Reopen IDEA, then **Build > Rebuild Project**. The module is `GPDFJSS-MENG`, and the working directory is the project root. Do not rebuild while workers are running.

| Configuration | Purpose |
|---|---|
| MPSLGP Paper Training | Joint full/IGD training, 240 group/seed jobs, final generation 50 |
| MPSLGP Paper Test | Final task-ranked conditioned heuristics, 540 task jobs |
| PSLGP Training | Independent matched-budget training, 540 task/seed jobs, final generation 50 |
| PSLGP Test | Final training-ranked conditioned heuristic, 200 held-out preferences per task |
| NSGPII Training | Independent matched-budget training, 540 task/seed jobs, final generation 50 |
| NSGPII Test | All saved final-generation-50 population-front heuristics |

Run each method's Training to completion before its Test. All six configurations set `--parallel 8 --heap 2g` and `-Xmx2g` for the coordinator. Each worker is an isolated JVM with single-threaded algorithm evaluation/breeding. Training schedules up to eight seed runs; MPSLGP held-out testing schedules up to eight task jobs (not necessarily eight distinct seeds). The worker cap is per coordinator, not a machine-wide limit.

The inspected computer has a Core Ultra 9 275HX with 24 physical/logical cores and about 64 GB RAM, with about 26 GB free at inspection. Eight workers allow up to 16 GB worker heaps plus a 2 GB coordinator heap, JVM/native memory and OS/IDE headroom. Eight is feasible for one batch under that snapshot, but smoke tests are not proof of full-size peak memory or linear speedup. Do not launch multiple eight-worker batches simultaneously; if other programs consume the free memory, reduce concurrency to five or four or close them before starting. Fixed heap limits do not cover all native memory.

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

For seed 0, task 0 receives the extra individual and elite; seed 1 gives them to task 1; seed 2 to task 2. Each group/seed sums to 1,000 individuals and ten elites. The 18 group/task targets produce 540 jobs per independent baseline, plus 240 joint MPSLGP jobs: 1,320 new training jobs and 1,620 task tests in total. Records remain group-specific even for repeated scenarios.

## Native Templates and Protocol

Both templates are read-only and loaded from the existing archive:

```text
C:/Users/xumen/Desktop/2026Paper/PSLGP/FinalResultsForPaper/FinalResultsForPaper/
  PSLIGD_BR/multipletreegp-dynamic/params/multipletreegp-dynamic.params
  fNSGPII/multipletreegp-dynamic/params/multipletreegp-dynamic.params
```

Override `--template <absolute-path>` if the archive moves, consistently for training and testing. Native state/breeder/evaluator classes remain, while the shared operator probabilities and explicit elite handling are aligned as described above. Delegating statistics/problem subclasses accumulate individual evaluator calls without changing fitness. The count file is created once at the final generation, avoiding replacement on Windows. It does not count every setup/manual-rule/phenotype simulation.

Formal independent jobs use population 500 or 333/334; MPSLGP shares 1,000. All evaluate generations 0--50 without early termination, with one training replication, simulation seed 0 rotated by generation, 5,000 measured jobs and 1,000 warm-up jobs. PSLGP/MPSLGP retain 101 cross-preference estimation entries and preference runtime v2 (`100*w_i`). Population and elite quotas are checked separately.

PSLGP saves the native final-generation Top-N list and tests only its first training-ranked individual. This matches the MPSLGP final task-ranked selection convention; it is deliberately not the historical baseline retest's recorded-Best selection. No test score selects the heuristic. The fixed tree pair is swept across 200 preferences, including both endpoints. NSGPII tests every heuristic on the final population Pareto front, without an external archive or a union across generations. Both use held-out seed 968356 and 30 replications; invalid recorded outcomes remain explicit rather than dropping seeds.

Smoke runs remain available through command-line arguments, not saved IDEA configurations: use `--smoke --smoke-generations 51` for the baseline runner or `--smoke --generations 51 --preferences 101` for MPSLGP training. They use at most 24 group individuals, 200 measured jobs and 50 warm-up jobs; held-out smoke testing uses one replication and three preferences. They validate generation-50 serialization/testing, not scheduling quality or full-size resource use. A baseline `--self-test` still runs quota/recovery checks without simulations.

## Outputs and Safe Reuse

```text
src/mengxu/ruleanalysis/MPSLGP/same-population-budget-aligned-v2-generation50/
  PSLGP/matched/<group>/task<index>-S<scenario>/run<seed>/
  NSGPII/matched/<group>/task<index>-S<scenario>/run<seed>/
  MPSLGP/training/full/IGD/<group>/run<seed>/attempt-<index>/
  MPSLGP/tests/full/IGD/<group>/run<seed>/task<index>/attempt-<index>/
```

Each independent baseline job has `training/` and `test-generation50/`. Training includes `effective.params`, `experiment.properties`, native statistics, elapsed-time CSVs, logs, evaluation-call metadata and `completed.properties`. The completion record verifies configuration and source hashes. Testing includes `test.properties`, raw `test-results.csv`, logs and a completion marker. PSLGP also writes `test-outcomes.csv` with explicit preference indices/weights; its CSV schemas match the existing held-out evaluator. NSGPII writes one row per selected front member. MPSLGP uses separate joint-training and per-task-test attempts under its two roots.

`--skip-existing` reuses only complete compatible jobs. By itself, it still refuses incomplete training directories, with an explicit recovery message rather than a missing-file exception. Incompatible completed results are always refused.

The PSLGP and NSGPII Training configurations also enable `--retry-incomplete`. This option requires `--action train --skip-existing`. Before workers start, the coordinator validates all existing jobs. A training directory without `completed.properties` is retryable only when its saved `experiment.properties` and `effective.params` match the current configuration/template fingerprint and no active training worker is detected for its input. Missing metadata or configuration mismatches require manual inspection; they are not automatically replaced. MPSLGP retains its locked, numbered-attempt retry mechanism.

When a retry starts, the entire interrupted `runXX` directory is moved to the sibling `runXX.incomplete-<UUID>`, preserving its logs, partial statistics and any old test output. The original path is then recreated for the same seed and population budget. `[RETRY PRESERVED]` reports the backup path. Checkpoints are disabled, so this restarts the interrupted job from generation 0, not its last recorded generation. Completed compatible jobs are skipped; unstarted jobs run normally. No old results are deleted.

After an interruption, stop the old coordinator/workers, reload the IDEA configuration from disk, rebuild, and rerun the same training configuration with the same output root. Do not remove the 30-run setting or change `--start-run`: verified completed jobs will be reused. Testing retains its strict completion checks and does not accept `--retry-incomplete`; preserve incomplete test directories manually before retesting. A new output root starts a separate batch and does not inherit completed jobs from the old root. Never run two coordinators against the same output root at once.

The original NSGPII configurations and default output root are unchanged. The new formal output directories are ignored by Git; the local IDEA directory is also ignored by the repository's existing policy.

## Historical Completed Analysis (Before Alignment)

The pre-alignment V2 analysis is in [same-population-budget-20261120](../../algorithm/multiobjective/MPSLGP/paper-draft-V2/results/same-population-budget-20261120). It reads only the old generation-49 outputs and is not an analysis of `aligned-v2-generation50`. Do not change its input paths to mix protocols. From the repository root, using the existing scientific-analysis dependencies (`numpy`, `pandas`, `scipy`, `javaproperties`, `matplotlib`), its historical commands are:

```powershell
python src\mengxu\algorithm\multiobjective\MPSLGP\paper-draft-V2\results\same-population-budget-20261120\analyze_same_budget.py --self-test
python src\mengxu\algorithm\multiobjective\MPSLGP\paper-draft-V2\results\same-population-budget-20261120\analyze_same_budget.py
```

After compiling `paper-draft-V2/mpslgp-method.tex`, use the same analysis command with `--validate` to cross-check the indicators with dense pairwise distances, exact tests with SciPy, all formatted table cells and the compiled PDF. This final validation also uses the existing `pymupdf` dependency.

The script verifies the 1,080 new baseline training/test completion records and source/configuration hashes, population quotas, generation-49 selection, held-out protocol, and 540 reused MPSLGP task outcomes. Scenario-specific bounds/reference fronts are rebuilt from only these three matched-budget inputs; old normalized indicators are not reused. All 30 training seeds remain the independent units for each of the 18 group/scenario comparisons. Sample standard deviations use `ddof=1`; the 108 exact two-sided paired Wilcoxon tests omit exact-zero differences, use tied absolute midranks and alpha 0.05 without multiple-comparison correction, matching Table VI. Significant direction comes from paired rank sums, not the mean ordering.

Outputs include raw-source SHA256 provenance (`input-manifest.csv`), per-run metrics, 54 mean/SD summaries, paired-test details, comparison counts, ten scenario reference fronts, the protocol JSON and a Table VI-style LaTeX table. The V2 Results subsection includes the completed analysis and table while preserving the original unequal-allocation table.

The 76 invalid PSLGP preferences (one in R2/scenario 3, seed 26; 75 in R5/scenario 8, seed 23) are excluded only at the objective-pair level; neither seed is dropped. MPSLGP and NSGPII have no invalid outcomes. Reported indicators are conditional on valid outcomes. Group training time uses logged generation 0--49 elapsed seconds, counting joint MPSLGP once and summing independent task times; these cross-batch timings are not parallel makespan or proof of hardware-independent speedup.