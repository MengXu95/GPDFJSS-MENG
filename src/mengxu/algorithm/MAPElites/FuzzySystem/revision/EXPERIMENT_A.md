# Running and interpreting importance-based Fuzzy experiment A (v2)

## What this experiment does

No GP training is performed. The study reads the historical final archives:

- QDGPf: `NfixIns`;
- QDGP: `NrotateInsNoReeva`.

Both use `mean-tardiness-0.85-1.5`, runs 0..29, six `importance-v1` profiles,
and exactly three selectors: `FUZZY_IMPORTANCE`, `MANUAL_WS`, `RANDOM`.
All criteria point in the beneficial direction; weights express importance.
SI is a fixed legacy diagnostic, not the personalized ranking score. Linguistic
inputs are structured importance labels/weights, not a free-text language model.

## Semantics: better directions, different importance

| Case | Meaning | Performance weight | Interpretability weight | Stability weight |
|---|---|---:|---:|---:|
| C1 | Performance priority | 5 | 1 | 1 |
| C2 | Stability priority | 1 | 1 | 5 |
| C3 | Interpretability priority | 1 | 5 | 1 |
| C4 | Equal importance | 1 | 1 | 1 |
| C5 | Performance, then stability | 5 | 1 | 3 |
| C6 | Joint performance/stability | 5 | 1 | 5 |

Normalize the validation objective by reverse min-max, and min-max the reciprocal
scores `1/(1+complexity)` and `1/(1+sensitivity)`. Every resulting `z` increases
with desirability; a constant criterion is 0.5.

Keep the three Gaussian descriptions (centers 0.2/0.5/0.8, common sigma 0.15).
Assign ordered grades `a=(0,0.5,1)` to Low/Medium/High, Hard/Moderate/Easy, or
Unstable/Moderate/Stable. Compute:

```text
g_X(z) = sum_l a_l * mu_Xl(z) / sum_l mu_Xl(z)
Fuzzy(h,w) = sum_X w_X * g_X(z_X(h)) / sum_X w_X
WS(h,w)    = sum_X w_X * z_X(h) / sum_X w_X
```

Normalized Gaussian grades are calculated stably using log-sum-exp and cached
once per heuristic. Ordered centers and equal widths make `g_X` strictly
increasing; improving from 0.8 to 1.0 is not penalized. Low/Medium/High still
describe rules, but are not requests for poor regions. A lower importance does
not mean that a worse attribute should be rewarded. No parameter is learned from
test data, and the nonlinear score is not assumed to outperform WS.

Manual-Target is excluded from the main study. With beneficial ideal targets at
1 its linear target utility is just `1-|z-1|=z`, duplicating WS. Old finite-target
profiles remain explicit diagnostics only, never new importance-based evidence.

The user-approved admission rule is identical for both regimes:

1. Keep unique expression pairs with finite stored training fitness; retain all
   original members and exclusion labels in the raw export.
2. Reevaluate every such pair on the same five validation streams.
3. Sort by mean validation objective, then original archive ordinal.
4. Compute corrected complexity and output sensitivity in that order. Exclude
   numeric metric failures explicitly, continuing until 500 eligible rules exist.
5. Apply all three selectors to that same pool; each returns three distinct rules.
6. Only then evaluate the selected union on the same 50 held-out streams.

Stored fitness is **not** used to rank the rotating-instance archive. The pool is
newly recomputed under this protocol; this is not reproduction of old Fuzzy tables.

Validation failures and numeric metric failures are recorded and excluded.
Insufficient eligible rules stops the run, rather than shrinking the pool or
padding duplicates. Unexpected program errors and invalid probe/configuration
inputs remain fatal.

Test failures NEVER trigger reselection. Their instance objective and affected
run-level test mean are positive infinity; failure reasons/counts remain visible.
Such completed studies have status `COMPLETE_WITH_TEST_FAILURES`, not a fabricated
finite objective.

## IntelliJ IDEA: exact run order

Open the **Java project** `D:\javaProject\GPDFJSS-MENG`, not the manuscript folder.
The shared configurations are in the project's `.run` directory; they use module
`GPDFJSS-MENG`, project working directory, Build before launch and a 4 GB heap for
studies. Use the project JDK; source-level checks were tested with OpenJDK 26.0.1.
No existing MPSLGP run configuration is changed.

Run these configurations in order:

1. **Fuzzy A - 00 Preflight QDGPf**
2. **Fuzzy A - 01 Preflight QDGP**
3. **Fuzzy A - 02 Smoke QDGPf**, optionally **03 Smoke QDGP**
4. **Fuzzy A - 10 Study QDGPf**
5. **Fuzzy A - 11 Study QDGP**
6. **Fuzzy A v2 - 20 Compare Results**

Preflight reads all 30 archives without simulations or output creation. It checks
syntax, counts, supported terminals, stored-finite uniqueness and paths. It does
not certify numerical sensitivity or final metric eligibility.

Smoke uses only run 0, pool 3, top-1, one validation stream, two test streams,
40 recorded jobs, no warm-up and small probes. It still validates all eligible
members of that one archive to preserve the validation-admission procedure.
Never use its numbers as paper evidence.

The formal properties files are:

- [experiment-a-qdgpf.properties](./experiment-a-qdgpf.properties)
- [experiment-a-qdgp.properties](./experiment-a-qdgp.properties)

Their defaults are 30 runs, pool 500, top-3, five validation streams, 50 test
streams, 1,000 recorded jobs plus 500 warm-up jobs. Validation seeds are
718341..718345, test seeds 968356..968405, probe seed 777. The author still needs
to verify their independence from historical training; the files cannot establish
historical seed provenance.
The test base also appears in historical test scripts. Reusing the published
benchmark is different from claiming an untouched new hold-out set; reserve new
test seeds before the formal study if that stronger claim is required, and keep
them identical across selectors and regimes.

Do not tune settings on test outcomes. If changing a protocol setting, make the
same intended change in both regimes and use new output directories.

## Cost: this is not a small top-500-only reevaluation

Read-only preflight of the supplied data found:

| Library | Sum of unique stored-finite pairs across 30 runs | Five-case validation calls |
|---|---:|---:|
| QDGPf | 56,513 | 282,565 |
| QDGP | 85,892 | 429,460 |
| Total | 142,405 | 712,025 |

These are sums of within-run unique pairs. The current runner does not share
validation caches across independent runs. Test simulation caching is within
each run across all profiles/selectors. At most 54 distinct selected pairs per
run need 50 tests, so the two studies (60 library runs) can add at most 162,000 test simulations,
plus four shared reference-probe simulations. Actual counts are exported.
Retries add work and are not hidden in successful-attempt totals.

Use the first completed run's `elapsed-seconds` to assess machine-specific cost.
No unmeasured wall-clock estimate is promised. Sequential formal studies are
recommended unless there is enough CPU/RAM; concurrent timing is not a clean
method-runtime comparison.

## Resume and integrity

Formal IDEA configurations include `--resume`. First use creates a new study.
Later use verifies complete run artifacts and skips their simulations.

- Frozen numerical probes are reloaded, not resimulated.
- Completed runs require matching input hashes, configuration/runtime/critical
  implementation fingerprint, and every recorded artifact hash.
- An incomplete attempt is retained. A fresh `run-N-attempt-K` is used for that
  run only; complete runs are not repeated.
- An exclusive study-root lock prevents two processes from running/resuming the
  same output concurrently. The two library roots may run independently.
- Changing source, JDK, parameters or source archives can invalidate resume.
  Keep the original version or choose a new output root; never bypass the guard.
- Do not edit raw CSVs in Excel and save over them. Copy them for exploration.

The two formal output roots are:

```text
D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-A-importance-v2\QDGPf
D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-A-importance-v2\QDGP
```

Each root contains `manifest.properties`, `profiles.csv`, `probes.csv`, per-run
attempt directories, and an aggregate `run_results.csv` after all runs complete.
Each run exports the full archive membership/exclusion audit, validation/metric
rows, selected IDs/scores, instance-level test outcomes and 18 run/case/method rows.
Thirty runs therefore give 540 aggregate result rows per library.
The protocol is `experiment-a-v2-importance`; old v1 target results are not
relabelled or resumed as this study. Keep old output directories unchanged.

## Comparing completed results

The **Fuzzy A v2 - 20 Compare Results** configuration analyzes both roots separately and writes:

```text
...\experiment-A-importance-v2\comparison-v2
```

Output must be new. For another analysis, change the analysis output directory.
The analyzer rejects partial studies, altered hashes, duplicate/missing rows and
unexpected run/pool/selection/test counts.

If IDEA still launches an old `experiment-A-validation-v1` path, it is using a
stale configuration or a local copy. Reload shared `.run` files and select the
explicitly versioned **Fuzzy A v2 - 20 Compare Results**, or replace the program
parameters with the v2 paths. Do not edit `.idea/workspace.xml` while IDEA runs.
The analysis entry point reads completed results only; it does not run training,
preflight or the recommendation study.

Read outputs in this order:

1. **summary.csv**: per-library/profile/selector means, sample SD and failure counts.
2. **paired_comparisons.csv**: Fuzzy minus each baseline, paired uncertainty,
   exact signed-rank p-values, rank-biserial effect and Holm-adjusted results.
3. **paired_differences.csv**: run-level differences for audit and paired plots.
4. **descriptive_tables.tex**: table fragments; insert only after checking scope.
5. **results_guide.properties** / **analysis.properties**: conventions and provenance.

Manual-WS is the primary transparent comparator and Random is a reference.
Fuzzy uses monotone Gaussian-derived utilities, whereas WS uses the same
normalized desirabilities directly. Their weights, directions, pool and test
data match, but their utility curves differ; no superiority is assumed.

Raw difference is **Fuzzy minus baseline**: negative means smaller raw objective,
complexity or sensitivity. Positive rank-biserial is oriented toward smaller
Fuzzy values. All six profiles favor beneficial directions, but weight their
trade-offs differently. Do not require improvement on every criterion at once,
or claim preference superiority from Fuzzy's own score by construction.

The independent unit is a trained-library run, not 3 selected rules or 50 test
instances. Inference uses exact conditional Wilcoxon signed ranks with averaged
tied ranks and zero differences removed. All-zero differences are explicitly
labelled. Holm families are predefined separately for each study/library and
raw metric across six profiles times two comparator tests. Confidence intervals
bootstrap paired run differences, not individual rules or test instances.
Untestable contrasts still count toward the planned 12-test Holm family (p=1
for correction only), while their displayed inference stays unavailable.
Signed-rank inference still relies on the appropriate symmetry assumption for
paired differences; inspect the paired-difference data. Bootstrap intervals are
pointwise, not Holm-adjusted simultaneous intervals.

When any paired objective is nonfinite, objective inference is marked unavailable
rather than dropping failed runs. Inspect failures first. Finite-only descriptive
means are conditional summaries, not substitutes for full-study performance.
Complexity/sensitivity remain separately analyzable if their run-level values
are finite. A non-significant test is not evidence of equivalence.

QDGPf and QDGP results remain separate; this study compares selectors within each
learned library regime, not a pooled causal test of fixed versus rotating training.

For diagnostic analysis of the smoke roots, explicitly override:

```text
--expected-runs 1 --expected-pool-size 3 --expected-top-k 1 --expected-test-instances 2
```

The formal Compare configuration intentionally keeps 30 / 500 / 3 / 50.

## Build/check outside IDEA

Use [build-revision.ps1](./build-revision.ps1) with a new isolated build directory.
The produced `runtime-classpath.txt` contains compiled revision classes first.
The entry points are:

```text
mengxu.algorithm.MAPElites.FuzzySystem.revision.ExperimentAStudy
mengxu.algorithm.MAPElites.FuzzySystem.revision.ExperimentAAnalysis
mengxu.algorithm.MAPElites.FuzzySystem.revision.ExperimentAWorkflowChecks
```

Workflow checks use disposable fixtures and a tiny physical scheduling study.
They cover admission, exclusion reasons, preserved test failures, no reselection,
resume/incomplete attempts, raw integrity and the statistics pipeline. Existing
`RevisionChecks --smoke` remains a separate strict backwards-compatibility check.

Previous target-protocol checks are not new importance results. Importance-mode
verification additionally tests dense-grid monotonicity, the 0.8-to-1.0 case,
dominance, positive weight rescaling and legacy-constructor compatibility.
Both real archive sources passed a one-run physical importance smoke study.
Their combined diagnostic analysis produced 108 descriptive rows and 72 paired
comparison rows; exported scores reconstruct from the cached per-criterion
utilities and recorded weights. These used pool 3/top-1/one validation/two test
streams and 40 recorded jobs, not paper settings. The seven IDEA configurations
now point to the importance protocol and fresh v2 output roots.
The initial implementation checks did not execute formal 30-run studies.

On 2026-10-27, both user-run formal importance studies were verified COMPLETE:
30 runs and 540 result records per library. A path-fix verification analyzed
those existing results successfully into
`comparison-v2-pathfix-20261027` under the v2 study base (108 descriptive rows,
72 paired comparisons and 2,160 paired-difference rows). This analysis did not
repeat recommendation simulations. The normal versioned IDEA configuration
still uses a separate, new `comparison-v2` output directory.
