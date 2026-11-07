# Fixed Lexicase native cumulative-map replay

This is a new observational replay, not an extension of the sparse-snapshot
population statistics and not a new held-out comparison. The original 360-run E
training/test roots and their results are read-only.

## Run in IDEA

Use the existing OpenJDK 26.0.1 runtime and module `GPDFJSS-MENG`.
The shared profiles are in the project's `.run` directory.

1. **Lex E Cumulative v1 - 00 Preflight** checks all 30 source cases and the
   original E scientific fingerprint. It performs no simulations or output writes.
2. **Lex E Cumulative v1 - 01 Verify Run0 Gen20** is a bounded diagnostic:
   one seed, generations 0 through 20. Both saved populations must match the old
   run exactly, including population order, expressions, raw objectives, validity,
   and all 25 case values.
3. **Lex E Cumulative v1 - 02 Smoke 5** runs five independent JVMs for three
   generations each. This is diagnostic only, never table-ready data.
4. **Lex E Cumulative v1 - 10 Replay Fixed T085 30** is the formal run. It
   automatically summarises all 30 completed runs. Only mean tardiness / 0.85,
   fixed training, is replayed; do not rerun the previous 360-run configuration.
5. **Lex E Cumulative v1 - 20 Summarise 30** only verifies/reports completed
   formal results. It refuses incomplete input and never starts training.

All execution profiles use `--resume`: hash-verified completed replay cases are
skipped; an interrupted case restarts in a new attempt directory. Original E v1/v2
runs supply the replay inputs, but cannot themselves count as completed cumulative
replays because they did not record this archive. Failed attempts are retained.
A changed scientific/runtime/replay fingerprint or corrupt sealed output is an
explicit error, not a reason to silently skip or overwrite it.

The coordinator uses 1 GB heap; at most five workers each use 2 GB. Allow roughly
11 GB heap plus JVM/native overhead and several GB of free disk. Adjust only
`workers` and `worker-heap` in [e-cumulative.properties](e-cumulative.properties)
if necessary. Every generation's complete population is now saved, so future
reanalysis does not again depend on missing intermediate populations.

## Matched map and metric definitions

- Replay the same seeds and unchanged E Lexicase selection/evaluation code.
  At every generation, verify the original finite/failed counts, raw mean/minimum,
  training seed and evaluation count. At generations 0,20,...,200 additionally
  verify every stored individual and all its case values exactly. Divergence
  fails the attempt rather than producing a replacement table.
- Feed **all 500 members of every generation** into a persistent map, starting
  at generation zero. Sort a separate array using native `PopulationUtils.sort`;
  never reorder the actual Lexicase population or use map parents.
- Use the native `configurePhenoCharacterisation`: D=4, C=3, 81 x 81. Its
  heterogeneous reference simulations use seed 0, 500 jobs, no warmup,
  utilisation 0.99, WSPT/WIQ, and shuffle seed 8295342; sequencing/routing retain
  their respective native simulation templates. This is **not** the independent
  common E analysis bank used by the earlier snapshot report.
- Use native `gridPCmap.updateGridMap`, capacity one, age penalty zero,
  no archive reevaluation. Worse rules never replace a cell elite; equal scores
  retain the earlier elite. Failed scores may occupy previously empty cells.
  The saved historical GPf parameters are checked; the intended capacity-one
  default is explicit even where that old file lacks the later parameter.
  Lexicase uses GPf-style observational population updates. Historical QDGPf and
  QDGPf2 additionally use immediate archive updates in their own evolution;
  their native timing/parent-selection behaviour is not imposed on Lexicase.
  The shared cumulative cell-retention and metric definitions are aligned,
  not the algorithms' distinct reproductive lifecycles.
- Normalise raw objectives with the native
  `SchedulingSet.lowerBoundsFromBenchmarkRule` ATC/WIQ reference. Restore the
  native post-evaluation-reset phase before that call, including its routing
  setter and `rerun` behaviour. Do **not** substitute the earlier post-setter
  final-reseed benchmark. This reference work uses a separate simulation and
  cannot consume the training model's or GP's RNG.
- Coverage = occupied cells / 6561 x 100. At capacity one the native
  retained-individual count equals occupied-cell count.
- Mean = native arithmetic mean of finite retained **cell elites**, not the
  population-weighted mean. Infinity/MAX_VALUE follow the native filtering;
  failed retained-cell counts and scores remain in the output.
- Best = native minimum retained normalised training fitness.
- Summarise each of these three run-level metrics using the 30-run mean and
  sample SD (denominator 29). A checkpoint or cell is not another training run.

This aligns map construction, native probes, benchmark procedure, and metric
definitions with the available historical implementation. It does not certify
the old binary or make the original training seeds/settings identical, and it
does not complete the missing matched GP/QDGP competitive controls.

## Outputs and cost

Formal output: `revision-results\experiment-E-lexicase-cumulative-v1` in the
manuscript repository. Diagnostic roots are separate. A root lock prevents two
coordinators writing the same study. The existing process pool supplies five-JVM
isolation, owned-PID cleanup, parent-death watchdogs, and failure draining.

Use `report.path` in the root manifest to locate the sealed report:

- `all_runs.csv`: all 6,030 run/generation cumulative-map records.
- `table_fragments.csv`: ten rows, generations 20,...,200, with the three means/SDs.
- `manifest.properties`: completion, hashes, cost and comparison limitations.

Each selected case also retains all 201 complete population CSV/stat snapshots,
checkpoint archive CSVs with cell coordinates, rule expressions, retained
generation and normalised fitness, plus per-generation failure counts, references
and exact replay checks. `cumulative_generations.csv` includes the observer's
reference costs; the unchanged E `generations.csv` evaluator counters exclude
that additional observer work.

Formal selected-run cost is **3,015,000 fitness simulations + 6,030 benchmark
simulations + 60 native descriptor-reference simulations = 3,021,090**.
There is no validation, held-out test or Fuzzy evaluation. Diagnostics, unit
checks and discarded interrupted attempts are additional, not extra replicates.
Only a COMPLETE FORMAL report (`publication-ready=true`) may replace the old
snapshot column. The manuscript/response numbers are not automatically changed.

## Isolated CLI validation

`build-cumulative.ps1 -BuildDirectory <NEW directory>` compiles only the new
helpers and the existing checkpoint CSV/statistics helper against the original
IDEA output. It does not overwrite existing `bin`/`out` or scientific kernels.
The build writes `runtime-classpath.txt`. With that classpath, run:

```text
ECumulativeChecks <path-to-e-cumulative.properties>
ECumulativeReplay --config <path-to-e-cumulative.properties> --preflight
```

Different compiler/debug settings can change bytecode identity. Use separate
diagnostic roots for CLI versus IDEA builds; never edit a sealed fingerprint to
bypass a mismatch. The original E scientific identity must still match.

## Completed manuscript analysis

The 2026-11-05 formal report contains all 30 runs and 6,030 generation records.
The manuscript repository's
`revision-analysis\lexicase-results\analyze_cumulative_results.ps1` independently
checks the original generation aggregates and 165,000 saved-checkpoint members,
recomputes all 330 checkpoint archive metrics and the 30-run summaries, and
exports a retained-cell failure audit with before/after input hashes.
Run it with `-ReplayRoot`, `-SourceRoot` (the original E root), and a fresh
`-OutputRoot`. It requires no scheduling simulations and does not change inputs.
The verified output is `analysis-manuscript-v1` beneath the formal replay root.
It replaces the earlier sparse-population statistics for the manuscript's
cumulative-map tables; the original final-population/test analyses remain separate.

## Rotating-training counterpart

For without-f LexGP, use the separate
[rotating cumulative replay guide](ROTATING_CUMULATIVE_REPLAY.md) and profiles
prefixed `LexGP Rotating Cumulative v1`. Its current-seed benchmark and native
age-5 archive/Best semantics differ from this fixed, penalty-zero study. Do not
change this fixed configuration to point at rotating data or reuse its result root.
