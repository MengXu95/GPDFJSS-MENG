# Uniform-parent archive GP baseline

This module implements the requested **selector-only** QDGP comparison in an
isolated folder. The original QDGP, Lexicase, Fuzzy and shared ECJ sources are not
modified.

## Variants

| Preset | Meaning |
|---|---|
| `uniform-gpf.params` | Fixed-instance uniform occupied-cell parent selection |
| `uniform-gp.params` | Rotating-instance uniform-parent QDGP adaptation |
| `matched-qdgpf.params` | Fixed-instance matched QDGP parent-policy control |
| `matched-qdgp.params` | Rotating-instance matched QDGP parent-policy control |

All four inherit the same QDGP representation, simulator/evaluation model,
initialization, immediate cell admission, variation, batch/elite lifecycle,
normalization and age-comparison code. Uniform presets set
`random-parent-selection-probability=1`; controls inherit the original parameter
file's value (currently 0.5). Set it to 0 for a pure adaptive-tournament control
if that is the QDGP variant being studied. The actual value is exported.

Archive capacity is restricted to **one per cell**. Thus sampling an occupied
cell uniformly is also uniform over retained elites; there is no fitness
weighting between cells when the probability is 1. Original within-cell
quality/age comparisons remain unchanged. The archive is live during breeding,
and every selected parent is cloned before variation.

The fixed version has the classical uniform-parent grid-search mechanism,
adapted to this GP and existing QDGP lifecycle. It is not claimed as a bit-for-bit
reproduction of a published implementation. The rotating version deliberately
keeps QDGP's cross-batch normalization/age rules; it is **not** called original
canonical MAP-Elites on a stationary objective.

## Why a selector adapter is necessary

Both the old selector and shared `ec.SelectionMethod` interpret generation-zero
indices as population indices. Merely changing the random-selection probability
therefore leaves the first offspring sourced from the population.

The new adapter selects and returns cloned archive members directly, including
the first breeding step. This correction applies equally to uniform and matched
controls. A deterministic occupied-cell index is shared by both policies; it is
maintained after original archive updates and does not change cell quality.
The adaptive/hybrid draw follows the existing tournament-size, integer scaling,
mixture and age-comparison rules. Index ordering and the first-breeding fix mean
the matched control is not an assertion of identical historical trajectories.

The state inherits the original `evolve()` rather than reimplementing its
algorithm. A standard statistics hook wraps only the empty initial archive to
redirect legacy hard-coded output and maintain the selection index. The run
wrapper redirects working-directory timing/diversity sidecars into isolated
metrics. Existing diversity calculations still execute; archive metrics are
reported in `generations.csv`.

## Controls and costs

Defaults come from the current QDGP source: population/batch 500, 201 evaluated
batches, initial depth 2..6, max depth 8, crossover/mutation/reproduction
0.85/0.15/0, and elitism 10. This does not certify the manuscript's different
200/.80/.15/.05 settings or historical JARs.

Do not remove elite reevaluation or alter update timing only for the baseline.
The inherited lifecycle evaluates offspring during breeding and rotates after
breeding, just as the source algorithm does. No new simulator reset policy is
introduced. Snapshot fitness may therefore come from different birth batches.
CSV exports raw fitness, stored normalized comparison score, age-adjusted score,
birth generation and nominal birth seed separately.

`generations.csv` records actual model fitness calls, manual-reference
simulations and two descriptor-reference simulations separately. One simulation
instance, replication and objective are required for this accounting. Invalid
reference objectives (including zero denominators) fail explicitly rather than
normalizing with a silent fallback.

Elapsed time includes setup/reference collection, initialization and search;
setup time is also recorded separately. Final all-failed archives are marked
FAILED rather than reported as a successful finite-quality result.

Fixed presets require age penalty 0. Rotating presets inherit `penalty-level`
(currently 0: inactive) and require reference normalization. For a study using a
nonzero age penalty, supply the **same verified value** to both rotating methods;
no historical value is invented.

Reproduction probability must remain 0, capacity 1, immediate updates enabled,
one evaluation/breeding thread, and archive-only parent selection enabled.
Unsupported combinations fail rather than silently changing the algorithm.

For fair results:

- Use identical resolved controls, GP/data seeds and initial populations; compare
  uniform versus the matched parent policy within each regime.
- Count actual evaluations and reference overhead, not just nominal generations.
- Distinguish inherited stored scores from independent collection quality.
- Evaluate final maps on the same separate validation/test instances and frozen
  analysis descriptor; preserve failed-member counts and native map size.
- Do not claim historical results/timings automatically satisfy these controls.
- Do not treat this same-grid-family ablation as a cross-family QD benchmark.

## Build and run

Compile from the project root into a new directory:

```powershell
& .\src\mengxu\algorithm\MAPElites\GridMapElites\UniformParentGPQD\build-uniform.ps1 `
  -JdkHome C:\Users\xumen\.jdks\openjdk-26.0.1 `
  -BuildDirectory D:\javaProject\GPDFJSS-MENG\revision-build\uniform-001
```

Use the generated `runtime-classpath.txt` with the IDE. Entry point:
`mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.UniformParentRun`.

Example program arguments:

```text
--params D:\javaProject\GPDFJSS-MENG\src\mengxu\algorithm\MAPElites\GridMapElites\UniformParentGPQD\uniform-gpf.params --output-root D:\research\Response\ACM-TELO-2025-90\revision-results\uniform-fixed\run-0 --set seed.0=0 --set uniform.run-id=0 --set eval.problem.eval-model.sim-seed=0
```

Choose the corresponding rotating/control preset and a **new** directory for
each other run. Use `--set key=value` to select the objective, utilization, seeds
and logging interval. The inherited default utilization is currently 0.95;
set it explicitly for each scenario. All training output stays in the new
directory; source/bin/out and existing results are not overwritten.

## Outputs and checks

- `initial_population.stat`: full evaluated initialization for matching checks.
- `archive.genXXXX.stat/.csv`: initial, interval-controlled and final map snapshots.
- `job.<run-id>.out.stat/.csv`: final native map, readable by `FinalArchiveReader`.
- `generations.csv`, `archive_curve.csv`: explicit costs, coverage and stored-score diagnostics.
- `resolved.params`, `manifest.properties`: settings and COMPLETE/FAILED status.

These are actual cumulative elite archives, unlike the final population output
of the separate Lexicase baseline. The CSV score labels must be respected:
historical raw/normalized/age-adjusted scores are not common validation results.

Checks entry: `mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.UniformBaselineChecks`.
Argument: this module's absolute directory. It uses disposable small runs to
check sampling frequencies, generation-zero parent sourcing, parent cloning,
fixed/age-adjusted replacement, both regimes, matched controls, logging
independence, all three objectives, seed rotation, exact evaluation counts and
native snapshot compatibility. It does not execute a formal 30-run study.

Verified with OpenJDK 26.0.1: full isolated compilation passed (52 existing
dependency warnings) and the check entry point passed. In 30,000 seeded
uniform draws, three occupied cells with sharply different scores were selected
10,142 / 9,923 / 9,935 times; the matched adaptive policy selected the best cell
944 times in 1,000 draws. Tests also confirmed identical final archives when
uniform and matched controls use the same parent probability, despite different
labels and snapshot intervals. Rotating checks included age penalty 0.01.

Additional two-generation, ten-individual runs used full-size simulations
(1,000 recorded jobs plus 500 warm-up): fixed training at utilization 0.85 and
rotating training at 0.95 both completed. Each made 20 fitness calls; including
reference overhead the totals were 24 and 25 simulations respectively.
These are implementation checks, not comparative empirical evidence.
