# QDGP Fuzzy revision experiments

This module separates final-archive parsing, preference selection, and held-out scheduling evaluation. Existing training algorithms, shared `yimei` readers/helpers, original result files, and MPSLGP run configurations are not modified by this module.

## Protocol

- Default protocol: **paper-v2**. These are new experiments, not a reconstruction of the old tables.
- Read only the final `All individuals in GridMAP` section; enforce its declared count. Do not substitute generation-best rules.
- Parsing, probe capture and sensitivity use one immutable terminal policy: `NIQ, WIQ, MWT, PT, NPT, OWT, WKR, NOR, W, TIS, rDD, SL, TRANT`. Other registered attributes, such as `DD` or `Pone`, are rejected during preflight, including dry-run, before any experiment output is created. Additional terminals require explicit capture/runtime support and a protocol update.
- Preserve original archive ordinal, two tree expressions, tree hash and stored fitness. Positive infinity is retained in the raw export and explicitly excluded from the candidate pool.
- Build the pool from the best finite stored fitness values, breaking ties by original ordinal. Require the requested pool size; do not silently shrink it.
- Evaluate pool members on separate validation instances for scoring and normalisation. Tests are run only after selection.
- Defaults are suggested new settings: five validation instances, 50 test instances, and independent explicit seed ranges. Confirm their separation from historical training before publication.
- A test instance uses a fresh heterogeneous simulation with seed `test-seed + index`; this is explicit and reproducible, but is not claimed to reproduce a historical JAR's seed progression.
- `recorded-jobs=1000` and `warmup-jobs=500` are separate simulator arguments.

### Metrics

Complexity counts each node occurrence with costs terminal=1, `+/-`=2, `*` and protected `/`=3, `Max/Min`=4. Operator names are matched consistently; unknown internal operators fail explicitly.

Priority evaluation accepts `Max`/`Min` case-insensitively and follows the native GP operators: equal operands retain the right operand, including its signed zero. This preserves their behavior inside protected division. Differential checks compare both operators against actual GP-node evaluation, including signed-zero ties and nested division. Non-finite native priorities remain explicit errors.

Sensitivity is `priority-output-forward-absolute-v2`: collect frozen candidate input vectors, perturb one used terminal at a time by positive absolute epsilon, calculate `abs(h(x + epsilon e_j) - h(x)) / epsilon`, and average over probe/unique-terminal pairs. Average the sequencing and routing scores with equal weight. Constant rules have derivative zero. Inputs and priority outputs are raw, with no implicit feature or output rescaling. Consequently, this is a scale-dependent local output measure, not a guarantee about decision rankings or performance variance.

The default epsilon is `0.001` in each terminal's raw unit. Probe collection uses matching, independently repeated reference simulations with the same instance seed and reference WSPT/WIQ policy for the two decision types. Sample exact-candidate-count situations and freeze their numerical terminal values. Original pool indices and candidate indices form probe IDs. `probes.csv` records the actual values used.

The old phenotype-matrix-distance sensitivity used helper methods absent from the current project. It is not silently emulated. Recover those historical helpers separately if exact legacy metric reproduction is needed.

### Preferences and ranking

- `profiles=paper-v2`: ordered C1-C6, with C5 targets High performance / Moderate stability / Hard interpretability. Its declared importance weights are P=5, S=3, I=1. These are revised experimental settings.
- `profiles=legacy`: retains the old profiles, including C5 High performance / Unstable / Easy with P=5, S=1, I=5.
- `normalisation=all-minmax`: reverse-min-max raw performance, min-max inverse complexity and inverse sensitivity; constant criteria map to 0.5.
- `normalisation=legacy-reciprocal`: preserve the earlier performance inversion and unscaled inverse complexity/sensitivity.
- Legacy profile/normalisation options still use the **new sensitivity metric**, and do not constitute exact reproduction of old results.

Four selectors run on identical candidates:

1. `FUZZY_PROFILE_MATCH`: weighted target membership. SI is calculated for diagnostics but is not its ranking score.
2. `MANUAL_WS`: weighted consistently oriented desirabilities.
3. `MANUAL_TARGET`: weighted `1 - abs(desirability - targetCentre)`, using the same Gaussian target centres.
4. `RANDOM`: without replacement, with a separate reproducible seed for each run and case.

Importance weights are not inferred from target levels. The monotone `MANUAL_WS` and target-matching `MANUAL_TARGET` answer distinct comparisons; retain this distinction in the paper. Linguistic inputs are structured labels and weights, not an unrestricted natural-language parser.

## Entry points

- `mengxu.algorithm.MAPElites.FuzzySystem.revision.RevisionExperiment`
- `mengxu.algorithm.MAPElites.FuzzySystem.revision.RevisionChecks`

Configure the first entry point with program arguments:

```text
--config D:\javaProject\GPDFJSS-MENG\src\mengxu\algorithm\MAPElites\FuzzySystem\revision\paper-v2.properties --dry-run
```

Dry-run validates inputs and archive contents without running simulations or creating outputs. Remove `--dry-run` only when the protocol, seeds, cost and new output path have been checked. CLI options override the properties file.

For a bounded smoke experiment rather than paper data, override:

```text
--runs 0 --pool-size 3 --top-k 1 --validation-instances 1 --test-instances 1 --recorded-jobs 20 --warmup-jobs 0 --probe-jobs 200 --probe-warmup 0 --probe-count 1 --probe-candidates 2 --output-root D:\research\Response\ACM-TELO-2025-90\revision-results\smoke-not-paper
```

Use a new output directory each time. Outputs inside the read-only input root, output paths already present, overlapping validation/test seed ranges, missing files, unsupported trees and invalid metrics are rejected. Simulation failures stop the experiment with a `FAILED` manifest; they are not converted to zero objective values.

The default processes a potentially expensive 30-run experiment. Start with one run. `metrics-scope=pool` characterises only the admitted pool; `metrics-scope=all` evaluates all finite archive members for distributions and can be substantially more expensive. Neither option changes how the top pool is chosen.

## Outputs

- `manifest.properties`: protocol, exact options, seed ranges, input hashes, stage timings, simulation count, completion/failure status.
- `archive_members.csv`: complete archived expressions and raw stored fitness, pool membership and explicit exclusions.
- `candidate_metrics.csv`: validation objective, corrected complexity and new sensitivity.
- `profiles.csv`: actual target labels and importance weights.
- `probes.csv`: frozen numerical input states and provenance IDs.
- `selections.csv`: selected candidate IDs, selector score, SI diagnostic and held-out outcomes.
- `test_instances.csv`: instance-level test outcomes, reusable by all selector comparisons.
- `run_results.csv`: run/case/method averages of individually tested rules. This is not ensemble execution.

Selected expression hashes are cached within each run to avoid duplicate test simulations. Statistical inference should use independent run-level blocks, not individual rules or repeated test rows. This module exports the paired data; it does not claim that a one-run smoke experiment supplies statistical evidence.

## Compatibility

The original three Fuzzy test `main` methods now forward to the explicit revision CLI instead of using hard-coded macOS paths. `FuzzySystemTestEnhanced.runPaperVerificationAndSave` runs held-out revision evaluation into a new directory. The old no-output `runPaperVerification` method rejects ambiguous use and directs callers to the safe entry point. Scalar `PaperVerificationTest` remains a clearly labelled legacy input-metric diagnostic, not a scheduling test.

`PaperVerificationTest` draws synthetic metrics from nonnegative truncated Gaussians by rejection sampling, not by clipping invalid values to zero. Raw experimental inputs remain subject to strict validation. Its optional argument is a new output CSV path; without it, the filename is `paper_verification_results.csv` in the working directory. Exports use UTF-8, locale-independent numbers and refuse to overwrite existing files. I/O failures are propagated. `runSyntheticVerification` supports bounded deterministic checks; none of these synthetic outputs are paper experiment results.

All new implementation files are under this revision package. Compile into a separate output directory rather than overwriting existing `bin/out`; include the project's ECJ sources and required libraries. Core checks use no additional test dependency.

From the Java project root, the supplied compile-only script can prepare an isolated classpath:

```powershell
& .\src\mengxu\algorithm\MAPElites\FuzzySystem\revision\build-revision.ps1 `
    -JdkHome C:\Users\xumen\.jdks\openjdk-26.0.1 `
    -BuildDirectory D:\javaProject\GPDFJSS-MENG\revision-build\paper-v2-001
```

Select an unused build directory; the script rejects existing destinations and destinations inside `src/bin/out/.git`. It resolves project sources with the installed JDK and writes the resulting runtime classpath to `runtime-classpath.txt`. Point the Java debugger/IDE at that classpath and one of the entry points above. The script itself runs no simulation or training.

`RevisionChecks` runs deterministic core, archive, sensitivity and path-safety checks. Add `--smoke` to also run a tiny synthetic two-candidate scheduling experiment in a freshly created temporary directory. Its fixtures and outputs are removed on completion. An optional `--report <new file>` records the result for automated checking. No historical data is modified.

For varying-instance historical archives, raw stored fitness may come from different training batches. This module records that the pool is preselected by stored fitness; it does not reconstruct missing age/normalisation metadata. Verify that preselection policy or design a separately documented common-validation preselection experiment before drawing cross-regime conclusions.

## Implementation verification

- Fresh isolated compilation with OpenJDK 26.0.1 succeeded, producing 474 class files including required project dependencies. The compiler reported 54 warnings in existing ECJ dependency sources.
- Deterministic core, strict archive/parser, numerical sensitivity, input protection and seed-disjointness checks passed. The synthetic end-to-end scheduling smoke test also passed.
- Follow-up regressions cover native `Max`/`Min` signed-zero behavior, all 13 supported terminals, unsupported-terminal rejection before output creation, and reproducible nonnegative synthetic diagnostics. The legacy CLI's default 30 synthetic runs with 200 candidates also completed under a German locale with valid locale-independent CSV output; these are diagnostic checks, not scheduling experiments.
- A read-only check of `NfixIns`, `mean-tardiness-0.85-1.5`, run 0 parsed all 2,601 final archive members: 1,964 finite candidates and 637 stored failures. The input SHA-256 remained unchanged.
- A real-archive integration check with three candidates, top-1, one validation seed and one test seed completed. It produced 24 case/method result records (six profiles times four selectors), three candidate-metric records and three candidate/test records. Six validation/test simulations plus two reference probe simulations were executed; the manifest reported `COMPLETE`.
- That integration check used 20 recorded jobs, no warm-up, and one probe per role from a 200-job reference simulation. It verifies plumbing only. No full 30-run study, parameter sweep, statistical conclusion or exact legacy-table reproduction is claimed.
