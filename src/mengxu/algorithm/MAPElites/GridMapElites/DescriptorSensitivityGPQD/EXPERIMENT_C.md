# C: QDGPf descriptor-resolution sensitivity

## Frozen scope

This is a new fixed-instance QDGPf experiment, not a reuse or relabelling of
historical training. It uses mean tardiness, utilisation 0.85, due-date factor
1.5, 10 machines, 1,000 recorded jobs and 500 additional warm-up jobs.
There is no rotating-instance QDGP variant in this study.

| d per decision type | c candidates per situation | Axis size c^d | Cells c^(2d) | Runs |
|---:|---:|---:|---:|---:|
| 2 | 3 | 9 | 81 | 5 |
| 3 | 3 | 27 | 729 | 5 |
| 4 | 3 | 81 | 6,561 | 5 |
| 5 | 3 | 243 | 59,049 | 5 |
| 4 | 2 | 16 | 256 | 5 |
| 4 | 4 | 256 | 65,536 | 5 |

All **30 runs are trained afresh**, including the default `(4,3)`. Each map
really has the specified geometry during parent selection and cell admission;
this is not a post-training remapping experiment. Every cell holds one rule.

The user-approved source protocol is 201 evaluated batches, including
initialisation, with 500 fitness evaluations per batch: **100,500 per run**.
Crossover/mutation/reproduction are **0.85/0.15/0**, with 10 population elites.
The source's scaled adaptive tournament / random-parent mixture is retained
(random-parent probability 0.5), as are immediate archive admission and
reference-fitness normalisation. Age penalty is zero and training data do not
rotate. Actual fitness calls and reference simulations are counted separately.
These controls do not certify the manuscript's historical 200-batch and
0.80/0.15/0.05 settings.

## IntelliJ IDEA: one configuration per stage

### Recovering the 2026-10-29 Windows manifest-write interruption

The original IDEA/JDK implementation exactly reproduced the recorded study
fingerprint. Every artifact of the **29 COMPLETE runs** was hash-verified.
Only `d4-c4.run.4` is incomplete; its last persisted generation is 112.
The failed attempt and all completed results remain unchanged.

The error was an `AccessDeniedException` during atomic manifest replacement.
The files are not read-only. A transient Windows deny-delete sharing handle can
cause this; the exception alone does not identify which application held it.
The fix retries that specific move error for a bounded period, after closing the
temporary writer (40 attempts, at most 9.35 seconds of configured backoff).
It never truncates the previous manifest or falls back to
non-atomic copying. A persistent failure reports and retains the fully written
pending temporary file; interruption is not ignored.

After IDEA has built the updated code:

1. Run **QDGPf d-c - 05 Prepare IO Recovery**. It verifies all completed artifacts
   and reconstructs the exact original training fingerprint, substituting only
   the preserved pre-fix I/O class. Every scientific component, training control
   and Java runtime must still match. No training or testing occurs.
2. Select **QDGPf d-c - 11 Resume Training IO v1**. The verified 29 runs are skipped;
   only the incomplete last run restarts in a new attempt directory.
3. After training COMPLETE, run **QDGPf d-c - 21 Test All IO v1**.

The formal 10/20 and versioned 11/21 presets explicitly point to the I/O compatibility
certificate. Recovery metadata is in the separate `io-recovery-v1` directory;
original manifests and the legacy I/O resource are preserved, not re-labelled.
Keep this recovery directory: the preserved old class bytes are used only to
reconstruct the old fingerprint, never to execute the broken writer.
The certificate pins the approved fixed I/O implementation. Re-running 05 after
an IDEA rebuild can approve a bytecode-only rebuild only after the same strict
scientific checks; any previous certificate is archived.

The versioned 11/21 names avoid reusing an older cached IDEA profile. The reported
09:27 launch contained `--resume` but omitted the compatibility VM option even
though the saved shared configuration contained it. If using the old profile,
open **Run > Edit Configurations > Modify options > Add VM options** and add:

```text
-Ddc.io.compatibility=D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-C-qdgpf-dc-v1\io-recovery-v1\compatibility.properties
```

This belongs in **VM options**, not Program arguments. The actual launch command
must show it before the main class. Keep `--resume` in Program arguments and
keep the original training output root. A generic fingerprint error without
that VM option is not a reason to create a fresh root or discard completed data.

**Do not change study hashes, remove failed attempts, use a generation-100
archive as a completed run, or restart all 30 runs.** Generation-level recovery
is unavailable because the full genetic/RNG state was not checkpointed.
Closing manifest previews or other persistent file readers may help if the OS
keeps denying replacement; the code does not disable security software or alter
file permissions.

For a genuinely new experiment, use fresh training/test roots and remove the
legacy `-Ddc.io.compatibility=...` VM argument. Old smoke outputs likewise need
fresh output roots after an implementation change. Keep the compatibility
argument for this existing recovered study and its post-training test.

Verification reproduced a real Windows deny-delete sharing lock: four atomic
replacement denials were followed by successful atomic replacement after the
holder released the file. A persistent-lock test exhausted its bounded budget
while preserving the old target bytes and a fully readable pending file.
Compatibility checks rejected changed generation budgets, training seeds,
component lists and tampered certificates, while preserving evaluation-only
parameter independence. Post-training metadata/resume regressions also passed
with injected services and no scheduling or evolution.

### Normal stage sequence

Open the existing `GPDFJSS-MENG` project, let IDEA load the shared `.run`
configurations, and run in this order:

1. **QDGPf d-c - 00 Preflight**: checks settings and real map dimensions without
   simulations or output creation.
2. Optional **QDGPf d-c - 01 Smoke Training**, then
   **QDGPf d-c - 02 Smoke Test**: all six map sizes, but only one run per setting,
   population 10, two batches, and a tiny post-training evaluation.
   The simulation generator still uses the declared 1,000 + 500 jobs.
3. **QDGPf d-c - 10 Train All**: runs all six settings and five run IDs.
4. Wait for `TRAINING COMPLETE AND VERIFIED`, then run
   **QDGPf d-c - 20 Test All** separately.

The formal stage configurations share `dc-study.properties`. They use
`--resume`: missing output is initialized, completed attempts are verified and
skipped, and incomplete attempts are retained before a fresh attempt.
They do not overwrite historical results or the smoke outputs.
Resume is **per independent run**, not per generation: an interrupted run is
restarted in a new attempt directory; already completed runs are not repeated.

```text
Training:
D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-C-qdgpf-dc-v1\training
Test:
D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-C-qdgpf-dc-v1\test
Smoke parent (separate):
D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-C-qdgpf-dc-smoke-v1
```

Changing the training controls, parameter files, runtime or producer bytecode
requires a new training root. A different evaluation protocol requires a new
test root, but does not require retraining if the training controls still match.
Do not edit manifests or delete completed runs to bypass hash checks.

## Separate entry points

```text
mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD.DcTraining
mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD.DcTest
```

Both use:

```text
--config D:\javaProject\GPDFJSS-MENG\src\mengxu\algorithm\MAPElites\GridMapElites\DescriptorSensitivityGPQD\dc-study.properties --resume
```

`DcTraining` also supports `--dry-run`. Overrides use `--set key=value`, for
example `--set snapshot-interval=200` to keep only the initial/final archive
snapshots. Do not mix smoke training with the formal test configuration.

An isolated compilation is available:

```powershell
& .\src\mengxu\algorithm\MAPElites\GridMapElites\DescriptorSensitivityGPQD\build-dc.ps1 `
  -JdkHome C:\Users\xumen\.jdks\openjdk-26.0.1 `
  -BuildDirectory D:\javaProject\GPDFJSS-MENG\revision-build\dc-001
```

It refuses existing build directories and never writes to the original
`src`, `bin`, `out` or `.git`. Use its generated `runtime-classpath.txt` for
standalone Java invocation. No additional libraries are installed.

## Matching and descriptor sampling

Within run ID `r`, every setting uses the same GP seed and fixed training
instance seed. Descriptor collection does not consume ECJ's genetic RNG.
The driver checks byte-identical evaluated initial populations across settings.
For each fixed `c`, it also checks identical max-five snapshot-bank exports;
the first `d` sequencing and first `d` routing situations form nested descriptors.

Only situations containing **exactly c candidates** are eligible. Selected
situations must be distinct; insufficient snapshots cause an explicit error
rather than repetition, implicit candidate truncation or silent changes.
The native WSPT/WIQ reference rankings and native GP decision semantics are used.

| Stream | Default seed(s) |
|---|---|
| Genetic initialisation/variation | 41,000 + run ID |
| Fixed training instance | 100,000 + run ID |
| Training-descriptor reference instance | 200,000 + run ID |
| Descriptor situation sampling | 210,000 + run ID |
| Common analysis reference instance | 300,000 |
| Common analysis sampling | 310,000 |
| Common validation | 400,000 .. 400,004 |
| Held-out representative tests | 600,000 .. 600,049 |
| Rule-library quality sampling | 700,000 + run ID |

Simulation seed ranges are checked for overlap. Native snapshots stay frozen
throughout each search. The independent common `(4,3)` analysis descriptor is
used only after training and never feeds back into the archive.

## What the test stage measures

- **Representative rule:** choose the finite rule with the lowest raw fixed
  training objective, breaking ties by original archive ordinal. Test that rule
  on 50 independent instances. Do not choose or replace it using test outcomes.
- **Native repertoire:** actual occupied cells, retained members, nominal
  capacity and coverage. Native cell coordinates and all `2d` zero-based
  descriptor digits are exported during training.
- **Common-grid repertoire:** project all retained members onto the same
  independent frozen `(4,3)` analysis grid. This makes coverage comparable
  across different training map capacities, without pretending the projection
  is the original native map.
- **Sampled quality:** uniformly sample up to 200 unique stored-finite
  expression pairs from each final library before validation. Evaluate each
  on the same five validation instances. Report sample size and eligibility
  counts; this is a sampling estimate, not the exact quality of every retained
  member. Never replace sampled rules after a validation failure.
- **Costs:** report fitness evaluations, manual/reference simulations,
  training time and maximum observed used JVM heap. The latter is sampled at
  generation boundaries, not process peak RSS. Runs share a JVM, so GC timing
  and retained allocations affect this coarse heap observation; it is not an
  isolated-process memory benchmark.

Training failures and post-training numeric failures must remain explicit.
No failed test rule is reselected, and failed/undefined results are not replaced
by zero. Conditional finite summaries, if reported, retain their denominators
and do not replace failure-inclusive outcomes.

At default settings the post-training upper bound before expression caching
is 30,000 validation simulations + 1,500 representative tests, plus common
descriptor collection. This is separate from the training budget.

## Outputs and interpretation

Training root: `resolved-study.properties`, `manifest.properties`,
`training_runs.csv`, and independently resumable run-attempt directories.
Each run saves `training.log`, `resolved.params`, `initial_population.stat`,
`snapshot_bank.csv`, `training_descriptor.csv`, `generations.csv`,
`archive_curve.csv`, periodic full archives, and `job.<run>.out.stat/.csv`.
The final native archive is compatible with `FinalArchiveReader`.

The test stage exports run-level outcomes, instance outcomes, sampled-rule
quality and common-grid projections, followed by setting-level descriptions.
Only COMPLETE outputs are results; bounded smoke/check outputs are not paper
data. All source artifacts are hash-checked and remain read-only.

| Test output | Use |
|---|---|
| `run_results.csv` | One row per setting/run: 30 rows in the formal default study |
| `summary.csv` | One row per setting/metric: all-run mean, sample SD, min/max, failure/missing counts and explicitly separate finite-only descriptions |
| `paired_differences.csv`, `paired_summary.csv` | Matched descriptive differences from the default `(4,3)` |
| `results_guide.properties` | Exact metric definitions, failure policy, sampling and inference restrictions |
| Per-attempt `test_instance_outcomes.csv` | The chosen representative's individual held-out outcomes |
| Per-attempt `quality_sample.csv`, `quality_validation_instances.csv` | Frozen sample identities and every sampled-rule validation outcome |
| Per-attempt `member_projection.csv`, `common_cells.csv` | All-retained and stored-finite common-grid descriptions |

For best-rule performance, filter `summary.csv` to
`metric=representative_mean_test_objective`. Inspect `failure_run_count` before
interpreting `all_run_mean` and `all_run_sample_sd`. For collection quality, use
`quality_mean_validation_objective` together with sample size and failure counts;
`quality_finite_conditional_rule_mean/median` are conditional descriptions, not
replacements for failed samples. Native coverage is setting-specific;
`common_coverage_all_retained` and `common_coverage_stored_finite` use the same
6,561-cell denominator.

`--set quality-sample=0` requests exhaustive validation of unique stored-finite
pairs, not of stored-failed members. Use a new test root if changing that option.
Test root manifests map preserved common-bank, case and report attempts; the root
CSVs expose the verified completed report. Do not mix files from failed attempts
with COMPLETE results.

The independent repetitions are **five training runs per setting**, not
individual rules, cells or test instances. Report means, sample SDs, ranges
and matched run differences descriptively. With only five nonzero pairs, even
an all-same-direction result has minimum two-sided exact signed-rank p=0.0625.
Do not claim p<0.05 evidence from such a comparison or generalise this one-scenario
study to all DFJSS objectives and loads.

The original QDGP evolution lifecycle is inherited. A protected descriptor
initialisation hook preserves the original `(4,3)` default path; the existing
capacity-one map and archive-safe parent selector are reused. The new study
uses the same first-breeding archive-parent adapter for all settings, so it
does not claim bit-for-bit equivalence with historical trajectories.

## Verification performed

The isolated current-source build passed. Descriptor checks cover exact-c
eligibility, native decision ranks, detached snapshots, deterministic exports
and nested prefixes. Six real-geometry miniature training runs passed actual
evaluation counters, matched evaluated initial populations, artifact hashes,
native exports and completed-run resume checks. Existing uniform/matched
archive-parent algorithm regression checks also passed.

Post-training checks cover injected failures, frozen sampling, no reselection,
nonfinite summaries, common projection, hashing, locking and interrupted/completed
resume. A real six-grid training-to-test smoke run passed with zero
test/validation/projection failures; completed test resume performed no
simulations or writes. These are software checks, not results from the formal
30-run study. The author subsequently ran the formal study: the recovery audit
on 2026-10-29 verified 29 completed runs and one interrupted last run. The I/O
hotfix does not automatically launch the remaining run or any formal testing.
