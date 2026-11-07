# LexGP rotating cumulative-map supplement

## What to run in IDEA

Use the existing OpenJDK 26.0.1 and `GPDFJSS-MENG` module.

1. **LexGP Rotating Cumulative v1 - 00 Preflight**: read-only verification of the
   original E family, all 30 rotating source runs, and historical rotating GP
   archive controls. No simulations or output writes.
2. **LexGP Rotating Cumulative v1 - 01 Verify Run0 Gen20**: one original seed,
   generations 0 through 20. This diagnostic is not a formal replicate.
3. **LexGP Rotating Cumulative v1 - 10 Replay Rotating T085 30**: the formal
   supplement, with five independent seed JVMs in parallel. It automatically
   generates the table-ready report after all 30 cases finish.

**02 Smoke 5** optionally checks five seeds for three generations each.
**20 Summarise 30** verifies already completed formal results without training;
it refuses incomplete input. All execution profiles have `--resume`.

Only **mean tardiness / utilisation 0.85**, rotating training, IDs 0..29, is
replayed, matching the scenario of the fixed cumulative-map tables. There are
201 evaluated populations of 500 members per run. Do not rerun all 180 rotating
or 360 original training cases. No new validation/held-out/Fuzzy test is needed:
the replay must exactly reproduce the original populations and their objectives.

The shared configuration is [e-cumulative-rotating.properties](e-cumulative-rotating.properties).
The coordinator uses 1 GB heap; at most five workers each use 2 GB. Allow roughly
11 GB heap plus JVM/native overhead and several GB of free disk. `workers`
(1..5) and `worker-heap` are execution-only controls.

## Source identity and safety

New entry point: `ERotatingCumulativeReplay`; new observer/map implementation:
`ERotatingCumulativeMap`. They reuse the existing JVM pool, watchdog, snapshot
verification and native map routines. The completed fixed runner and scientific
training classes are not changed, so existing LexGPf outputs keep their identity.
Separate protocol/classes are deliberate: extending the fixed runner would
invalidate its sealed bytecode fingerprint.

The original 360-run E root is read-only. Source-case mappings include previously
recovered attempts. Every generation must match its original training seed,
valid/failed counts, raw mean/minimum, simulation count, failed count and case
slots. At generations 0,20,...,200, all 500 members must additionally match in
order, both expressions, raw fitness, validity and all 25 case values.
Divergence is an error, never a success-shaped replacement study.

Verified completed rotating replay cases are skipped. Incomplete/failed cases
restart in fresh attempt directories; successful cases and failed attempts are
preserved. Corrupt outputs or changed controls/runtime/bytecode are rejected.
Original E v1/v2 results are source inputs, not completed cumulative replays.
Fixed-study outputs cannot be reused as rotating results.

## Native without-f map semantics

- Every evaluated population updates one cumulative, capacity-one 81 x 81 map.
  The native D=4/C=3 descriptor templates are frozen across generations, using
  their original seed-0 WSPT/WIQ templates and shuffle seed 8295342.
- The archive is observational only. LexGP still selects parents from its
  evaluated population using its unchanged adapted epsilon-lexicase selector.
- The physical shop is generated once at the original initial training seed.
  Only the job-stream seed rotates: initial seed + 10000 x generation.
- For each generation, a separate reference simulation uses that current seed,
  the same initial physical shop, and native
  `SchedulingSet.lowerBoundsFromBenchmarkRule`. The post-evaluation-reset,
  routing-setter and `rerun` phase is preserved. The reference must complete all
  recorded jobs with a positive finite objective. Reference work cannot consume
  training/GP random streams.
- Historical rotating controls require **age penalty 5**, no archive-wide
  reevaluation, and capacity one. Native replacement compares
  `stored_score * (1 + 5)^(current_generation - insertion_generation)`.
  Thus a newer rule can replace an older rule even with a worse stored score.
  This is not the fixed-study penalty-zero strict raw-score improvement rule.
- Coverage counts occupied cells, including failed retained entries, once.
- Mean is the native arithmetic mean of finite retained **birth-instance
  reference-normalised scores**. It is not a current-instance evaluation,
  held-out performance, or failure-inclusive quality.
- **Best has an important native meaning:** `best_normalised` is the stored
  score returned for the age-selected best archived member. It is **not**
  necessarily the minimum stored score. `min_stored_normalised` is exported
  separately as a diagnostic; `age_adjusted_best` and the selected member's
  insertion generation make the distinction auditable.

These are compatible with the existing capacity-one GP/QDGP/QDGP2 rotating
archive metrics. They do not certify the old binary or make historical/new
training controls matched. Five-per-cell variants require separate scrutiny:
their old retained-individual ratio must not be relabelled as occupied-cell
coverage. None of these stored rotating scores is a common-validation quality
assessment of all archived rules.

## Outputs and completion

Formal output in the manuscript repository:
`revision-results\experiment-E-lexgp-rotating-cumulative-v1`.
Diagnostic roots are separate. Use the root manifest's `report.path`:

- `all_runs.csv`: all 6,030 run/generation records, including current seeds,
  native metrics, finite/failed counts, references, costs and exact replay checks.
- `table_fragments.csv`: generations 20,...,200; mean and sample SD across
  30 runs for coverage, stored-score mean, native Best, and diagnostic minimum.
- `manifest.properties`: completion, hashes, metric semantics and cost.

Every generation's full CSV/stat population is retained. Checkpoint archive
CSVs preserve cell, insertion generation, birth seed, raw objective, birth
normaliser, stored/aged scores and both expressions. This supports later
figure generation without recreating intermediate populations.

Only COMPLETE FORMAL results (`publication-ready=true`) may be added to the
without-f comparison table. Smoke/parity data are not formal samples.
Formal selected-run cost is **3,015,000 fitness + 6,030 benchmark + 60 descriptor
reference simulations = 3,021,090**. Diagnostics, unit tests and interrupted
attempts are separate costs, not independent replicates.
The unchanged E `generations.csv` counters exclude observer references;
`cumulative_generations.csv` and replay manifests include them.

The manuscript and response are not automatically modified before the formal
results exist.

## Isolated verification

`build-cumulative-rotating.ps1 -BuildDirectory <NEW directory>` compiles only the
three new Java files against the existing original IDEA output and libraries.
It does not overwrite existing `bin`/`out` or recompile fixed/scientific classes.
With its `runtime-classpath.txt`, run `ERotatingCumulativeChecks <config>`.
Use separate output roots for isolated CLI versus IDEA builds: different compiler
debug settings can change bytecode identity. Never bypass a sealed fingerprint.

The bounded verification completed before delivery: native aging/reference
checks passed; five JVMs concurrently reproduced three original generations;
run 0 matched the original generation-20 population and all case values exactly.
All 102 smoke-result file hashes remained unchanged on complete resume; a partial
fixture preserved four cached cases and launched only the missing fifth seed.
The fixed LexGPf report remained fully verifiable with its original identity.
These diagnostic results are isolated under `revision-checks`; the IDEA output
roots above remain fresh and the formal 30-run study has not been started.
