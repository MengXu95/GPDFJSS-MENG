# E: all-scenario Lexicase-GP literature comparison

## Five-seed parallel execution (Lex E v2)

Use the **Lex E v2** IDEA profiles for up to five independent seed JVMs.
The original v1 profiles remain sequential for compatibility and verification.
There are no five-thread ECJ populations: each seed has its own process,
static state, random generator and log. Within each method/regime/scenario,
up to five unfinished run IDs execute concurrently; the next group starts after
the current group's running workers finish.

The selected resource limits are **2 GB per worker** and **1 GB for the
coordinator**. Five workers can therefore use up to 10 GB of Java heap, plus
coordinator heap, native JVM memory and other applications. A smaller requested
scope or fewer pending seeds naturally launches fewer than five processes.

### Switching from the currently running sequential job

1. Stop the old training task yourself before building/starting the new one.
   The implementation does not terminate an existing user-launched run.
2. Select **Lex E v2 - 00 Parallel Preflight 5**. It checks the original study
   signature without changing results.
3. Optional: run **01 Parallel Smoke Train 10**, then **02 Parallel Smoke Test 10**.
   These use five seeds in each of two regimes, one scenario, population 10 and
   two batches, in a separate smoke directory.
4. Run **Lex E v2 - 10 Parallel Train Lex 360**.
5. After training completes, run **Lex E v2 - 20 Parallel Test Lex 360**.

The v2 formal profiles keep the **same training/test output roots and
scientific controls**, including all six scenarios and run IDs 0..29.
Completed training cases are hash-verified and skipped. An interrupted,
unsealed seed is preserved and restarted in a new attempt; generation-level
checkpoint recovery has not been added. Never change `runs=30` to `runs=5`
to request parallelism: five is the worker limit, not the number of repetitions.

At the initial 2026-11-02 audit, formal training was RUNNING and the test root
did not exist. The training algorithm, seed schedule and its scientific
fingerprint components are not changed by the scheduler. The new sequential
and parallel testing paths share the same validation/selection/projection
implementation and evaluation-family computation. An unexpected incompatible
existing test root is rejected, not overwritten.

### Skip-existing across v1 and v2 launchers

The v1 sequential and v2 parallel configurations use the same case registry and
output roots. With `--resume`, a matching COMPLETE case is reused regardless of
which launcher produced it. This applies to training and testing, including a
partially completed study: only unfinished keys are scheduled.

Explicit same-root checks passed in both directions. Complete sequential to
parallel and parallel to sequential handoffs did no new work and changed no
completed artifacts. A partial sequential test cache preserved four sealed
cases while parallel execution dispatched only the remaining six. A partial
parallel cache likewise preserved four worker successes while the sequential
launcher evaluated only six unfinished cases. Both launchers validate sealed
parallel worker journals; reference/replay accounting remains correct when
switching back to sequential recovery.

Here v1/v2 denote the launch configurations using the same current compiled
scientific/evaluation implementation. This is not permission to ignore changed
models, controls, seeds, input data, scientific code or corrupt checksums.
Incompatible or corrupted results fail explicitly; an existing folder alone is
not a completed result. Keep the existing roots and `--resume`; there is no
need to add an unsupported `--skip-existing` argument.

### Parallel profiles and command line

| Lex E v2 profile | Requested scope |
|---|---|
| `10 Parallel Train Lex 360` | Default Lexicase, six scenarios, both regimes, 30 seeds |
| `11 Parallel Train Fixed 180` | Fixed half only |
| `12 Parallel Train Rotating 180` | Rotating half only |
| `20 Parallel Test Lex 360` | Default separate evaluation |
| `30 Parallel Controls 720` | Optional matched GP/QDGP training |
| `31 Parallel Test All 1080` | All three methods' evaluation/comparison |
| `40 Parallel Train All 1080` | Optional combined training |

```text
mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD.EParallelTraining
mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD.EParallelTest
```

Both use:

```text
--config D:\javaProject\GPDFJSS-MENG\src\mengxu\algorithm\MAPElites\GridMapElites\LiteratureComparisonGPQD\e-study.properties --resume --workers 5 --worker-heap 2g
```

The coordinator's VM option is `-Xmx1g`. `--worker-heap 2g` applies to each child,
not the coordinator. Worker-count/heap options are administrative arguments,
not fields added to the scientific properties file. Child launchers use the
current Java runtime and absolute classpath; they do not inherit IDEA debugger
ports or the IDEA Java agent.

Only the coordinator publishes shared root manifests and report pointers.
Workers write separate case attempts; their process-console logs are outside
the sealed case data. A failed worker stops further dispatch but allows already
running workers to finish and commit valid results before the invocation reports
failure. Shutdown cancels only tracked children; workers also watch the
coordinator's PID/start identity to avoid orphan computation after a forced stop.

Parallel testing verifies the same frozen scenario descriptor. Worker-local
descriptor replays are independently hash-checked and counted separately;
they are extra reference simulations, not additional training/test replicates.
Successful-case metrics use unchanged seeds and sampling rules. Concurrent
wall-clock times are not directly comparable to old sequential timing values
and do not establish an algorithmic speedup factor.

Parallel verification passed on 2026-11-02:

- The process-pool check observed five distinct overlapping worker PIDs, a
  maximum active count of five, group barriers and correct failure draining.
- Five completed sequential fixed seeds were reused while five rotating seeds
  ran in separate JVMs. All ten final native populations/case vectors matched
  a fully sequential baseline byte-for-byte; completed resume launched nothing.
- Testing all ten cases matched 60 scientific case CSVs and five report CSVs
  byte-for-byte. A failed worker preserved four successful siblings; resume
  launched only the six unfinished cases and completed verification was
  write-free.
- Normal test replay accounting was 2 coordinator + 20 worker references;
  failure/resume recorded 4 + 20. These are explicit overhead, not extra
  independent repetitions.
- A scheduler-only build against the author's actual IDEA scientific classes
  reproduced the existing training signature exactly. No existing formal
  training case was re-executed or stopped during these checks.

## Scope and entry points

The default is **Lexicase only**, with both fixed and rotating training:

| Objective | Utilisations | Runs per regime/scenario |
|---|---|---:|
| Mean tardiness | 0.85, 0.95 | 30 |
| Maximum flowtime, not makespan | 0.85, 0.95 | 30 |
| Mean weighted flowtime | 0.85, 0.95 | 30 |

This is `6 scenarios x 2 regimes x 30 = 360` fresh training runs.
Optional new matched GP and QDGP controls add 720 runs; the full matrix is 1,080.
The default does **not** silently start the controls or claim that old
historical/C results meet the new matching conditions.

```text
mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD.ETraining
mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD.ETest
```

Both use the same `e-study.properties`:

```text
--config D:\javaProject\GPDFJSS-MENG\src\mengxu\algorithm\MAPElites\GridMapElites\LiteratureComparisonGPQD\e-study.properties --resume
```

## Run from IDEA

Open the existing `GPDFJSS-MENG` project and select the new **Lex E v1** profiles:

| Configuration | Action |
|---|---|
| `00 Preflight 360` | Check the default 360-case matrix without output/simulations |
| `01 Smoke Train 36` | All three methods, all six scenarios and both regimes; one run, population 10, two batches |
| `02 Smoke Test 36` | Evaluate those smoke outputs with two candidates, one validation instance and two test instances |
| `10 Train Lex ALL 360` | Formal LexicaseGPf and LexicaseGP, all six scenarios, 30 runs each |
| `11 Train Lex FIXED 180` | Only the fixed half of the same study |
| `12 Train Lex ROTATING 180` | Only the rotating half |
| `20 Test Lex ALL 360` | Separate post-training evaluation of the default 360 runs |
| `30 Train GP QD Controls 720` | Optional fresh matched GP/QDGP controls in the same training root |
| `31 Test Compare ALL 1080` | Evaluate all three methods and produce matched comparison reports |
| `40 Train ALL 1080` | Optional combined training; verified completed Lex cases are skipped |

Typical sequence:

1. Preflight.
2. Optional smoke training, then smoke testing.
3. Formal `10`, then `20`.
4. If adding controls, run `30`, then `31`. Existing verified Lex evaluations
   are reusable; adding controls does not itself require redoing them.

Do not run two training configurations against the same root concurrently.
Wait for the requested training scope to finish before its tests. The fixed and
rotating profiles may be run sequentially; they are filters, not different
scientific study identities. Keep the case count, budget, simulator and seed
controls unchanged when appending controls.

## Outputs and resume

```text
Formal training:
D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-E-lexicase-all-v1\training
Formal test:
D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-E-lexicase-all-v1\test
Smoke parent (separate):
D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-E-lexicase-smoke-v1
```

All formal training/test profiles contain `--resume`. Only a COMPLETE case with
matching identity and valid artifact hashes is reused. Failed/incomplete
attempts are preserved and restarted; there is no generation-level genetic/RNG
checkpoint. Output directories are never erased to resume.

The root manifest records the **last requested scope**, its completion and the
total completed cases already present. A COMPLETE Lex-only scope does not mean
all optional 1,080 cases have been trained. Always inspect the requested and
completed case counts.

Reports are immutable attempts. Read `report.path` in each root manifest to find
the current completed report (for training, its `training_runs.csv`). A later
control run creates a new report covering all completed cases while preserving
old reports and native outputs. Never concatenate unverified attempts.

The test driver retains metadata across cases, not every collection's programs.
It validates/loads one final output at a time; case-local program lists and
projection caches become eligible for collection after that case. Up to six
scenario banks are retained. This avoids holding millions of optional-control
programs at once, but is not an exact heap guarantee for arbitrary configurations.

Generic persistence is separate from the scientific fingerprint. Atomic
properties publication uses the bounded Windows AccessDenied retry already
tested for C; no truncation or non-atomic replacement fallback is used.
The **new E profiles do not use C's legacy I/O compatibility certificate**.
Changing actual scientific controls, seeds, kernels or Java runtime requires
a fresh scientific study; changing only the selected dispatch scope does not.

## Training controls and literature adaptation

- 201 evaluated batches, including initialisation; 500 individual evaluations
  per batch: **100,500 fitness calls per run**.
- Population 500, elitism 10, initial depth 2..6, maximum depth 8.
- Crossover/mutation/reproduction = 0.85/0.15/0.
- 10 machines, 1,000 recorded jobs, 500 additional warm-up jobs, due factor 1.5.
- One simulation instance/replication per fitness call; one evaluation/breeding thread.
- Fixed training reuses its job-stream seed. Rotating training advances the
  source seed by 10,000 per transition, keeping the shop configuration fixed.

Lexicase preserves the existing guarded
`EpsilonTTGenNoRepeatCandidateLexicaseSelection` variant. It is an **adaptation**,
not plain standard lexicase or an exact reproduction of every published setting.
Its 25 cases are equal blocks of released recorded-job IDs. Case means aggregate
to the native mean objective; case maxima aggregate to maximum flowtime.
Case values come from **one simulation**, not 25 independent training instances.
Candidate selection is capped at the available valid population without
duplicating individuals.

GP is a matched scalar tournament-selection control (size 7). It uses the same
whole-instance objective and diagnostic case bookkeeping but does not use cases
to select parents. QDGP uses the source immediate archive admission, capacity-one
81x81 grid, hybrid parent selection (random probability 0.5), reference
normalisation and age penalty 0. This is a declared current-source control,
not a certification of the original manuscript's historical binaries/settings.

The QD evaluation reset adapter resets the job stream before every individual,
as the population evaluator does. It also reseeds at the delegated simulation's
run boundary, after native rule setters that regenerate state; otherwise the
first generated job can be discarded without reseeding. Exact initial raw
fitness pairing is checked, not assumed. Inherent
lifecycle differences remain: QD offspring are evaluated/admitted before
rotation, whereas population methods evaluate the new population after rotation.
Record actual birth/training seeds rather than calling these exposure timings
identical. Fitness calls, manual-reference calls, descriptor simulations and
case-value slots are reported separately.

## Seeds and matched controls

Scenario ordinals are permanently defined:

```text
0 T085  mean-tardiness-u085
1 T095  mean-tardiness-u095
2 F085  max-flowtime-u085
3 F095  max-flowtime-u095
4 W085  mean-weighted-flowtime-u085
5 W095  mean-weighted-flowtime-u095
```

For scenario ordinal `s` and run ID `r`:

| Purpose | Seed |
|---|---|
| Genetic initialisation | `41000 + 1000*s + r` |
| Initial training job stream/shop | `100000000 + 10000000*s + r` |
| Rotated generation stream | initial seed + `10000*g` |
| Training-descriptor reference | `200000000 + 10000000*s + r` |
| Training-descriptor sampling | `260000000 + 10000000*s + r` |
| Common analysis reference | `300000000 + 10000000*s` |
| Common analysis sampling | `360000000 + 10000000*s` |
| Validation streams | `500000000 + 10000000*s + i`, `i=0..4` |
| Held-out streams | `700000000 + 10000000*s + i`, `i=0..49` |
| Validation-subset sampling | `900000000 + 10000000*s + r` |

The 10-million scenario stride leaves room for all 201 rotation seeds and
avoids leakage into another scenario or evaluation stream. Filtering/reordering
the requested scenarios does not renumber their streams.

All methods share the paired initialization/data seeds. Order-independent
genotype and raw-fitness multiset hashes are compared across methods/regimes;
the producer label and population sorting do not create false mismatches.
Failed raw fitness sentinels are identified explicitly in that check.

## Final populations are not historical QD maps

GP/Lexicase save complete population snapshots at declared intervals and a
separate final population. There is **no cumulative historical map** and no
archive feedback into Lexicase selection. The final population alone supplies
the primary collection evaluation. QDGP supplies its actual final archive.

Every native export keeps full expressions, identities, fitness and seed
provenance. Do not combine saved checkpoints to enlarge a method's output.
The later common-grid projection is observational evaluation, not a new
training archive or a different QD family.

## Separate validation, representative tests and repertoire evaluation

The user selected a cost-capped protocol:

1. Enumerate unique final expression pairs with finite stored training fitness.
2. Uniformly sample without replacement **up to 200** pairs, before validation.
   Record eligibility/sample counts; never pad or replace failed samples.
3. Evaluate each sampled pair on the same five validation streams for its
   scenario. Choose the lowest finite validation mean within this subset,
   with a declared deterministic tie rule.
4. Evaluate that same representative on 50 independent held-out streams.
   It is a **validation-selected subset representative**, not the global
   best rule of the entire final collection or the best on the test set.
5. Project every retained membership onto the same independent frozen `(4,3)`
   descriptor for that scenario, reporting all-retained and stored-finite
   coverage separately. Native output sizes/duplicate counts remain visible.

The 200-rule cap is the same across methods, but actual unique eligibility may
be smaller for some populations. Report that difference rather than claiming
every collection supplied exactly 200 candidates.

If no sampled rule has a finite validation result, the no-representative
outcome is explicit; there is no arbitrary fallback or test-based reselection.
Distinguish scheduled test slots from simulations actually executed.
Quality sample failures remain in the failure-inclusive objective and
denominators; conditional finite descriptions are labelled separately.

Default Lex-only post-training cost before caching is at most 360,000 validation
simulations plus 18,000 held-out tests and independent common-reference
collection. These are separate from 36,180,000 training fitness calls.

## Comparison readiness and statistics

Lex-only training/testing produces descriptive Lex results. It cannot establish
competitiveness against GP/QDGP until the matched controls are available.
Reports must identify missing comparison groups rather than fabricate baselines.

Read the test root's `report.path`, then use these files in that immutable
report directory:

| File | Meaning |
|---|---|
| `run_results.csv` | One completed method/regime/scenario/run block; 360 for Lex-only or 1,080 after all controls |
| `summary.csv` | Group-level mean/sample SD/range and explicit finite, missing and failure denominators |
| `paired_differences.csv`, `paired_summary.csv` | Matched QDGP-minus-comparator descriptive differences |
| `primary_comparisons.csv` | Primary objective inference or explicit not-ready/diagnostic/unavailable status |
| `comparison_status.properties` | Overall comparison readiness and the missing/incompatible contrast counts |
| `results_guide.properties` | Exact field, selection, failure and statistical definitions |

`mean_test_objective` is the held-out objective of the validation-selected
subset representative. `test_scheduled_count`, `test_executed_count` and
`test_unexecuted_count` distinguish real simulations from no-representative
slots. For GP/Lex populations, native-QD metrics are **NA**, not zero.
QD capacity uses the producer's `native-grid-cells`; common-grid coverage
uses the same 6,561-cell denominator for every method within the scenario.

The independent repetitions are 30 training runs, not rules, cells, 25 cases or
50 test streams. Keep objectives and fixed/rotating regimes separate.
The predeclared primary comparison is QDGP versus GP and versus Lexicase for
the held-out subset-representative objective. If inferential reporting is ready,
use exact paired signed-rank inference with a 12-contrast Holm family
(`6 scenarios x 2 comparators`) per regime and alpha 0.05.
Unavailable nonfinite contrasts retain their predeclared correction slots;
no failed training run is silently deleted to create a finite-only test.
Other repertoire/cost fields are descriptive unless separately predeclared.

## Isolated build and checks

```powershell
& .\src\mengxu\algorithm\MAPElites\GridMapElites\LiteratureComparisonGPQD\build-e.ps1 `
  -JdkHome C:\Users\xumen\.jdks\openjdk-26.0.1 `
  -BuildDirectory D:\javaProject\GPDFJSS-MENG\revision-build\e-001
```

The builder refuses an existing output or the original src/bin/out/.git
directories. It does not install dependencies or overwrite project classes.
The generated `runtime-classpath.txt` supports standalone checks.

Bounded verification entry points include `ETrainingChecks` (module directory
and NEW fixture root) and the adapter/test/comparison checks. Miniature fixtures
are software tests, not formal 30-run study results. The formal 360/720/1,080-run
experiments are not launched automatically by this implementation.

Verification completed for this implementation:

- The full isolated source build passed.
- All six scenarios, both regimes and all three methods passed real miniature
  training, exact paired-initialization checks, cost counters and native readers.
- Appending 24 miniature GP/QD cases preserved all 12 completed Lex cases.
  Same-scope training resume performed no additional evolution or metadata writes.
- Real miniature post-training validation/testing progressed from 12 Lex cases
  to 36 total cases without changing completed Lex manifests; a later Lex-only
  verification reused the 36-case report with zero evaluations/writes.
- Injected checks cover validation/test failures, no representative, missing
  controls, schema/geometry, input/report corruption, replay and resumed reports.
  `ETestChecks <module-directory> <NEW-fixture-root>` includes the comparison
  mathematics checks; `EComparisonChecks` is a helper, not a standalone main.
- Exact legacy fixed/rotating Lexicase final-population parity and logging
  interval independence passed. The existing inherited evolution loops were
  not rewritten.

These are bounded software checks, not formal performance results. No default
360-case or optional full study has been started by this implementation.
