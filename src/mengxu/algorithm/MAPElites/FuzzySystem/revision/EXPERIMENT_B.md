# B: whole-library attribute analysis

This analysis includes **every retained member of the final QDGPf and QDGP
archives**, not just the 500-member recommendation pools. It reuses the completed
importance-study A validation results and frozen probes. It does not retrain,
select new recommendations, run test instances, or invoke a scheduling simulator.
The original archives and A results are read-only.

## Run in IntelliJ IDEA

The formal B dataset and publication analysis are already complete; rerunning is
not necessary to use them. The reporting preset uses a separate fresh directory
so it will not overwrite the completed publication analysis.

1. Run **Fuzzy B - 10 Whole Library Attributes**. The preset uses `--resume`;
   a new output directory is initialized automatically, and a completed directory
   is verified without recomputing it. Partial attempts are retained and completed
   per-library outputs are checked before reuse.
2. After `B COMPLETE`, run **Fuzzy B - 20 Whole Library Analysis**.
   The analysis directory must be new and outside the input B directory. To repeat the reporting stage, change
   `--output-root` to a new directory rather than replacing published results.

The presets point to:

```text
A fixed:    D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-A-importance-v2\QDGPf
A rotating: D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-A-importance-v2\QDGP
B output:   D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-B-whole-library-v1
IDEA rerun: D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-B-whole-library-analysis-idea-v1
Published:  D:\research\Response\ACM-TELO-2025-90\revision-results\experiment-B-whole-library-analysis-v2
```

Entry points:

```text
mengxu.algorithm.MAPElites.FuzzySystem.revision.WholeLibraryStudy
mengxu.algorithm.MAPElites.FuzzySystem.revision.WholeLibraryAnalysis
```

No dependency installation is needed. The existing isolated `build-revision.ps1`
also builds these entry points and their standalone checks.

## What is and is not measured

- **Complexity:** sum of the two trees' weighted node counts, including every
  terminal/operator occurrence; the definition is unchanged from A.
- **Sensitivity:** the same raw-priority finite-difference measure, epsilon,
  saved probe ordering and equal sequencing/routing weighting as A. A separate
  compiled expression evaluator avoids repeated terminal parsing in the hot
  loop; differential checks and deterministic cache audits enforce agreement
  with the reference evaluator. Identical expression pairs share attribute
  computations, but every retained archive membership remains in the output.
- **Quality:** the cached mean validation objective on five instances, not
  stored training fitness and not test performance. Stored-failed members were
  never evaluated in A. Their quality stays blank and is explicitly classified
  as `NOT_EVALUATED_STORED_FAILURE`; no quality value is imputed.
- A failed validation retains positive infinity and `VALIDATION_FAILED`.
  Undefined numeric sensitivity retains a blank value, `UNDEFINED_NUMERIC`,
  and its reason. These members are counted, not silently deleted.
- Quantiles and empirical distributions are conditional on the corresponding
  finite metric. Denominators and failure/unknown counts are exported separately.
  Libraries, not individual programs, are the independent replication units.
- `log10(1+sensitivity)` is a visualization scale, not a change to the metric,
  recommendation algorithm, or its statistical analysis.
  The quality ECDF panel and scatter horizontal axes similarly display
  `log10(1+validation objective)` without changing raw CSV values or clipping
  observations. This keeps both the central distribution and long tails visible.
- Historical exports omit native cell coordinates. Attribute distributions
  therefore do **not** establish native grid coverage or descriptor superiority.
  They also do not replace a descriptor ablation or a cross-family QD comparison.

## Outputs and provenance

- `members.csv`: all retained memberships, identities, original stored fitness,
  cached validation quality, full-library complexity/sensitivity, A pool flag,
  numeric statuses and computation/cache source.
- Per-library directories: independently checkable member CSV and manifest.
- Root manifest: protocol `whole-library-attributes-v1`, source and implementation
  fingerprint, counts, audit counts, and **zero new scheduling simulations**.
- `input_hashes.csv`: hashes of A manifests/artifacts and original archive files.
- Analysis output: per-library/regime summaries, status counts, and distribution
  and trade-off charts. Numerical summaries use all finite observations;
  deterministic scatter subsampling affects rendering only.
  The main files are `library_summary.csv` (480 rows),
  `regime_summary.csv` (16 rows), `status_counts.csv`,
  `run_balanced_ecdf.png` and `quality_attribute_scatter.png`.

The producer checks all input hashes again before completion. Changed source
archives, cached results, frozen probes, runtime or producer implementation
require a new output root. A file lock prevents concurrent writes.
Compiler bytecode differences can also change the fingerprint. If switching from
the isolated build to an IDEA build or another JDK, use a fresh B output root
when the fingerprint differs; do not bypass the check or modify existing results.

Standalone checks are `FrozenPriorityProgramChecks`,
`WholeLibraryStudyChecks <new-fixture-directory>` and
`WholeLibraryAnalysisChecks <new-fixture-parent-directory>`.
Fixture outputs are synthetic checks, not paper data.

## Completed full-library dataset

The completed producer contains 169,227 memberships from 60 libraries:

| Quantity | QDGPf | QDGP |
|---|---:|---:|
| All retained members / finite complexity | 71,092 | 98,135 |
| Finite cached validation quality | 54,429 | 83,427 |
| Failed validation | 2,084 | 2,465 |
| Quality not evaluated | 14,579 | 12,243 |
| Finite sensitivity | 64,850 | 90,258 |
| Undefined numeric sensitivity | 6,242 | 7,877 |
| Recommendation-pool memberships | 15,000 | 15,000 |

The producer performed 136,235 new attribute computations, reused 32,992
expression-pair attribute results, and passed 379 deterministic cached-result
audits. Scheduling simulation count is zero. Every library contains exactly
500 admitted pool members. These are descriptive data, not evidence that the
larger archive or either training regime is statistically superior.

The v2 figures improve labels, fonts and display scales. All five numerical data
files (`library_summary.csv`, `regime_summary.csv`, `status_counts.csv`,
`ecdf_points.csv`, `plot_samples.csv`) are byte-identical to the initial analysis.
An independent reconstruction verified all 360 raw-metric library summaries and
12 raw-metric regime summaries.
