# NSGPII: 50 Evaluated Generations and Generation-49 Testing

## IDEA Run Configurations

The configurations are in `.idea/runConfigurations`. Use **File > Reload All from Disk** if they are not visible, and **Build > Rebuild Project** before starting. Do not rebuild while workers are running.

| Configuration | Purpose |
|---|---|
| NSGPII Gen49 - 00 Plan Matched | Read-only plan for all groups and 30 seeds; no simulations or output directories |
| NSGPII Gen49 - 01 Smoke Train 10 Seeds | One instance, seeds 0--9, ten isolated JVMs, small population/jobs, 50 evaluated generations |
| NSGPII Gen49 - 02 Smoke Test 10 Seeds | Test the smoke runs' actual generation-49 population fronts |
| NSGPII Gen49 - 10 Train Matched Budget | Main population-budget-matched training, all groups and 30 seeds, at most ten workers |
| NSGPII Gen49 - 11 Test Matched Budget | Test every saved generation-49 front heuristic, using 30 replications |
| NSGPII Gen49 - 20 Train Independent 1000 | Optional original-population reference: 1,000 per task, 50 generations |
| NSGPII Gen49 - 21 Test Independent 1000 | Generation-49 testing for that optional reference |

Recommended order: **00**, then **01**, then **02**. After checking smoke outputs, run **10** and wait for it to finish before **11**. The optional **20/21** pair is a different experiment and has a separate output root. The full paper batches are not started by creating these configurations.

## Existing Algorithm, Not a Replacement NSGA-II

The runner uses the archive template:

```text
C:/Users/xumen/Desktop/2026Paper/PSLGP/FinalResultsForPaper/FinalResultsForPaper/fNSGPII/multipletreegp-dynamic/params/multipletreegp-dynamic.params
```

It preserves the existing `GPRuleEvolutionStateMO`, `NSGA2BreederNoEnvironmentalSelection`, and `NSGA2EvaluatorNoEnvironmentalSelection`. The native fitness, terminal set, crossover, mutation and survivor selection remain unchanged. Two small subclasses delegate the original problem/statistics calls and record individual objective-evaluation calls once per generation; they do not choose individuals or alter fitness.

Formal training has `generations=50`, so evaluated generations are **0--49**. Early success termination is disabled. Generation 49's complete population Pareto front is saved, and the tester validates the actual last generation before reading it. It does not use a historical CSV index, a best individual, an external archive, or a union across generations.

## Population Budgets and Instances

The main `--budget matched` mode shares a total allocation of 1,000 independent individuals across the tasks of each MPSLGP group:

| Group | Benchmark scenarios in task order | Independent task populations |
|---|---|---|
| R1 | 1, 2 | 500, 500 |
| R2 | 3, 4 | 500, 500 |
| R3 | 6, 7 | 500, 500 |
| R4 | 1, 3, 5 | 334/333/333, with the extra individual rotated by seed |
| R5 | 8, 9 | 500, 500 |
| H1 | 1, 10 | 500, 500 |
| H2 | 5, 6 | 500, 500 |
| H3 | 1, 5, 8 | 334/333/333, with the extra individual rotated by seed |

For three tasks, seed 0 gives the extra individual to task 0, seed 1 to task 1, and seed 2 to task 2, repeating thereafter. All 18 group/task targets and 30 seeds give **540 independent training runs**. A scenario appearing in several groups receives a group-specific baseline; do not silently reuse a 1,000-population result as a 500/333-population baseline.

The optional `--budget independent` mode uses 1,000 for each benchmark scenario and deduplicates scenarios across groups, giving **300 runs**. This mode aligns population size with the existing PSLGP runs, not with MPSLGP's per-task allocation. It is not the main population-budget-matched control.

To run only a particular instance, use, for example:

```text
--action train --budget matched --groups R1 --scenarios 1 --runs 30 --start-run 0 --parallel 10 --heap 2g --skip-existing
```

Use the same arguments with `--action test` after training. `--runs 30 --parallel 10` means 30 seeds are completed in a queue with at most ten active, not only ten total runs. Use `--runs 10` for seeds 0--9 only. Scheduling is instance-first, then seed, so the first ten workers process ten different seeds of one instance.

## Training and Held-Out Test Protocol

Formal training matches the MPSLGP paper scenarios: 10 machines, 5,000 recorded jobs, 1,000 warmup jobs, one training replication, simulation seed 0 with rotation, utilisation 0.70--0.90, and due-date factor 1.5. Scenarios 1--5 use maximum flowtime/maximum weighted tardiness; 6--10 use maximum weighted flowtime/maximum tardiness.

Held-out testing reuses the project scheduling-set generator, seed **968356**, and **30 replications**. It evaluates each final-front sequencing/routing pair once on that held-out set. NSGPII is not preference-conditioned, so it does not manufacture 200 preference evaluations of the same heuristic. Tests record raw objective pairs and preserve invalid `-1` sentinels. An output completion marker means all selected rules were recorded, not that every simulation replication was finite.

The later comparison must recompute shared per-scenario normalization bounds and HV/IGD reference fronts from the compared methods' raw test outcomes. These training/test configurations do not reuse published normalized scores or regenerate manuscript tables automatically.

## Outputs, Completion and Safety

Default formal outputs:

```text
src/mengxu/ruleanalysis/MPSLGP/nsgpii-generation49/matched/<group>/task<index>-S<scenario>/run<seed>/training/
src/mengxu/ruleanalysis/MPSLGP/nsgpii-generation49/matched/<group>/task<index>-S<scenario>/run<seed>/test-generation49/
src/mengxu/ruleanalysis/MPSLGP/nsgpii-generation49/independent/Independent/S<scenario>/run<seed>/
```

`training/` contains `effective.params`, `experiment.properties`, `out.stat`, `front.stat`, `training.log`, `evaluation-count.properties`, and `completed.properties`. The completion record includes source hashes, actual final generation, final front size, individual-evaluation calls and whole-task training wall time.

`test-generation49/` contains `test.properties`, `test-results.csv`, `testing.log`, and `completed.properties`. Every CSV row records the seed, actual generation, candidate index, tree sizes, training objectives and raw test objectives. Row count and selected-rule identities are checked before completion.

Historical archives and existing MPSLGP/PSLGP results are read-only. Complete compatible jobs can be reused with `--skip-existing`; incomplete or incompatible output is refused, not deleted or overwritten. Use a new `--output` root for a different experiment or after a failed partial run. Training output eligibility is checked before workers start. Test input completion is checked before any testing workers start.

Progress includes the group, task, instance, seed, population, child PID, generation, per-front-rule test completion and batch completed/failed counts. One worker failure is reported without cancelling unrelated jobs; the coordinator reports failure after collecting all jobs. A shutdown hook stops active children if the coordinator is terminated.

## Resources and Fairness Limits

Ten formal workers each have a 2 GB maximum heap. The coordinator and native/JVM overhead require additional memory beyond the combined 20 GB heap limits. Reduce `--parallel` or `--heap` if necessary. Concurrent wall time is not a hardware-independent speedup measurement.

`individual.evaluation.calls` counts calls to the existing training problem's individual objective evaluator. It does not count all setup/manual-rule/phenotypic-characterisation simulations, so it is an effort diagnostic, not proof of equal total simulation cost. Equal population allocation and generation count do not automatically equalize evaluation budgets across NSGPII and surrogate-assisted MPSLGP/PSLGP.

The existing PSLGP generation-49 retests still originate from 1,000-per-task training and recorded-Best selection. To claim a fully population-matched three-method comparison, independent PSLGP must also be trained at the corresponding 500 or rotating 333/334 quotas. The new NSGPII experiments alone do not repair that difference. Native operator differences also remain explicit.

## Smoke Scope

Smoke outputs live under a separate `smoke/` directory. They use at most 24 total individuals, 200 training jobs, 50 warmup jobs and one held-out replication. They are never paper data. Smoke defaults to three generations; `--smoke-generations 50` exercises the real final-generation-49 path with small workloads. The supplied ten-seed IDEA smoke pair uses that option.