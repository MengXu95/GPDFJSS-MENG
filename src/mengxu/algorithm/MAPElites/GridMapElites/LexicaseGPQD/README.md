# Lexicase-GP population baseline for QDGP comparisons

This is an isolated adaptation of the existing **one-instance multi-case**
`EpsilonTTGenNoRepeatCandidateLexicaseSelection`. It is not standard lexicase,
not NSLC, and not an archive-driven QD algorithm.

The agreed output policy is **periodic complete populations and the final
population only**. There is no cumulative history map and no archive feedback
into parent selection. Do not concatenate checkpoints and call the result the
algorithm's final repertoire.

## Two regimes

| Preset | Label | Training instance |
|---|---|---|
| `lexicase-gpf.params` | LexicaseGPf | Fixed job-stream seed |
| `lexicase-gp.params` | LexicaseGP | Seed increases by 10,000 between generations |

Within each generation every individual, **including elites**, is evaluated on
the same instance and obtains a fresh case vector before parent selection.
Breeding uses that evaluated population; rotation happens after breeding and
before the next population evaluation. The machine/transport configuration is
fixed at the initial simulation seed, matching the shared simulator's rotation
convention. Every evaluation resets the job-stream seed, including the first.

## One instance, multiple cases

Defaults: 1,000 recorded jobs, 500 additional warm-up jobs, and 25 cases.
Cases are equal blocks of **released job IDs** after warm-up, not completion
order and not separate simulation instances. Every recorded job belongs to
exactly one case; recorded jobs must be divisible by the case count.

This fixes two old hazards without editing the original implementation:

- The shared legacy mean-tardiness/weighted-flowtime case methods subtract a
  hardcoded 1,000 jobs. They are not used here.
- The original fitness `getObjectives()` switch falls through from means to
  maxima. The isolated fitness exposes the true whole-instance objective.

Mean objectives aggregate equal-size case means; max-flowtime aggregates case
maxima. Their aggregate is checked against the native whole-instance objective.
Failed simulations are explicitly counted and assigned infinite fitness for all
cases; they are excluded from the existing selector's candidate pool. If all
members fail, the run fails rather than selecting an arbitrary rule.

The existing epsilon filtering/MAD and case-order selection are reused. The
adapter restricts `elitist-selection-rate=1` and caps the effective candidate
count at the available valid members, without duplicating individuals. Defaults
use 500 candidates for a population of 500; the old 800-candidate setting was for
a different population size and is not silently described as identical.

## Configuration and fair controls

The common preset inherits the current GridMapElites representation and limits:
500 individuals, elitism 10, 201 evaluated batches (indices 0..200), initial
depth 2..6, maximum depth 8, crossover/mutation/reproduction 0.85/0.15/0.
It uses the non-grid counterpart of the same two-tree crossover and standard
subtree mutation. Snapshot interval defaults to 20; generation 0 and the last
evaluated generation are always saved.

These settings match the **current source defaults**, not a certification of
historical JARs or the manuscript's differing 200/.80/.15/.05 settings. The
default run evaluates 100,500 individuals in 100,500 simulations; 25 case values
come from each single simulation, not 25 simulations.

For a controlled comparison:

1. Match GP initialization seeds, job-stream seeds, scenario, representation,
   recorded/warm-up jobs, operators, depth, batch count and actual costs.
2. Compare LexicaseGPf to QDGPf and LexicaseGP to QDGP.
3. Compare the complete **final population** to the actual final QD map, retaining
   their sizes and duplicate counts. A population is not a grid archive.
4. Evaluate both on identical independent validation/test instances. Never rank
   the two collections by their stored training fitness, especially with rotation.
5. Report coverage in a common frozen analysis descriptor. Post-hoc projection
   does not turn the lexicase learner into MAP-Elites.
6. If selecting one representative, use a declared training/validation convention,
   not the best held-out result. If applying Fuzzy, use a predeclared common pool
   size no larger than either eligible pool; do not pad duplicates to reach 500.

The old QDGP initialization/reset details, immediate archive updates, age policy,
and historical budgets still require explicit control. This module does not
silently patch or claim to rerun those algorithms.

## Build and run

From the Java project root, compile to a **new** isolated directory:

```powershell
& .\src\mengxu\algorithm\MAPElites\GridMapElites\LexicaseGPQD\build-lexicase.ps1 `
  -JdkHome C:\Users\xumen\.jdks\openjdk-26.0.1 `
  -BuildDirectory D:\javaProject\GPDFJSS-MENG\revision-build\lexicase-001
```

Use the resulting `runtime-classpath.txt` with your IDE/debugger. Entry point:
`mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseQDRun`.

Program arguments for a fixed-instance run:

```text
--params D:\javaProject\GPDFJSS-MENG\src\mengxu\algorithm\MAPElites\GridMapElites\LexicaseGPQD\lexicase-gpf.params --output-root D:\research\Response\ACM-TELO-2025-90\revision-results\LexicaseGPf\run-0 --set seed.0=0 --set lexqd.run-id=0 --set eval.problem.eval-model.sim-seed=0
```

For rotating training choose `lexicase-gp.params` and a different, new output
directory. Set utilization/objective explicitly for each of the six paper
scenarios, for example `--set eval.problem.eval-model.sim-models.0.util-level=0.95`.
Use a separate output directory per run and preserve resolved parameters.

Outputs:

- `population.genXXXX.stat/.csv`: every population member, not just elites;
- `final_population.stat/.csv`: complete last evaluated population;
- `generations.csv`: seed, valid/failed counts, scalar quality, actual simulations,
  case-value slots, timing and snapshot decisions;
- `resolved.params`, `manifest.properties`: protocol and completion/failure status.

CSV includes individual identity, both full expressions, expression-pair hash and
every case value. Native snapshots say **Population**, not **GridMAP**.
Existing output directories/files are never overwritten.

## Checks

Entry point: `mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.LexicaseBaselineChecks`.
Its argument is the absolute path to this module directory. It uses disposable
fixtures and tiny runs, verifies both regimes and all three objectives, tests
case partitioning/fitness cloning, exact simulation counts, final snapshots,
logging-frequency independence and offline comparison. It is not a paper study.

Verified with OpenJDK 26.0.1: isolated compilation passed (52 existing dependency
warnings); the full check entry point passed. Checks included the default
1,000-recorded/500-warmup/25-case evaluation, matched initialization across regimes,
fresh rotating case vectors, all three objectives, complete final populations,
and unchanged search results when only snapshot frequency changed. No full
30-run training study has been executed.

## Offline comparison

`LexicasePopulationComparison` compares a native population snapshot with a
QDGP final archive on shared fresh validation streams. It also projects both
onto a common frozen PC grid for evaluation only. Supply `--population`,
`--qd-archive`, and `--output-root`; `--help` lists the evaluation controls.
Keep this analysis separate from training and from any final held-out test.

The new output directory's parent must already exist; output is forbidden inside
either input directory. Optional controls are `--objective`, `--utilisation`,
`--due-factor`, `--seed`, `--instances`, `--recorded-jobs`, `--warmup-jobs`, and
`--machines`. Defaults are mean-tardiness, 0.85, 1.5, 718341, 5, 1000, 500, 10.
Use fresh, independently reserved seeds for a separate final test, rather than
selecting a representative on the same streams reported as test performance.

Outputs are `manifest.csv`, `members.csv`, `instance-outcomes.csv`,
`collection-summary.csv`, `post-hoc-common-grid.csv`, and `validation-seeds.csv`.
Identical rule pairs share simulations but retain their original memberships.
Common-grid counts are occupied cells, not total population size.

The descriptor projection reuses the current QDGP PC factories: sequencing uses
its `standardFull` reference with seed 0, utilization 0.99 and due factor 1;
routing uses its `standardMissing` reference with seed 0, utilization 0.99 and
due factor 4. These are two different reference trajectories. The resulting
coverage describes that shared PC space, not an independent unseen-descriptor
generalization experiment.
