# EvoSpeak

EvoSpeak is an offline LLM-assisted genetic programming (GP) method for dynamic flexible job shop scheduling. An LLM is used outside the Java experiment to propose dispatching heuristics. The saved heuristics supply the initial population, which GP then evaluates and evolves in a scheduling simulation.

This README describes the original implementation in this folder. It does not describe EvoSpeakV1's online API integration. The original training workflow does not require an API key and does not automatically contact an LLM.

## Workflow

1. Select the scheduling objectives and, for the two-objective variant, the objective weight.
2. Give an external LLM the available terminals, example heuristics and the intended objective trade-off.
3. Inspect the generated sequencing/routing expressions and prepare a valid ECJ population file.
4. Load that file with `pop.subpop.0.file` and evaluate the initial rules in the heterogeneous scheduling simulation.
5. Evolve the population using tournament selection, crossover, mutation and reproduction.
6. Save the learned rules and statistics, then evaluate their quality on independent test scenarios.

Each GP individual contains two trees:

- **Tree 0, sequencing:** chooses which waiting operation a machine processes next.
- **Tree 1, routing:** chooses a machine option for an operation.

Both rules compute a numerical priority; smaller values are preferred. The resulting heuristics are reusable decision rules, not a fixed schedule for one instance.

## Files

| File | Role |
|---|---|
| [WarmStart/multipletreegp-dynamicLLMWarmStart.params](WarmStart/multipletreegp-dynamicLLMWarmStart.params) | Single-objective experiment configuration. |
| [WarmStart/multipletreegp-dynamicLLMWarmStartMO.params](WarmStart/multipletreegp-dynamicLLMWarmStartMO.params) | Weighted two-objective experiment configuration. |
| [WarmStart/GPRuleEvolutionStateLLMWarmStart.java](WarmStart/GPRuleEvolutionStateLLMWarmStart.java) | Single-objective evolutionary loop and diagnostics. |
| [WarmStart/MOGPRuleEvolutionStateLLMWarmStart.java](WarmStart/MOGPRuleEvolutionStateLLMWarmStart.java) | Two-objective evolutionary loop and benchmark normalization. |
| [WarmStart/MultiObjectiveFitnessLLM.java](WarmStart/MultiObjectiveFitnessLLM.java) | Weighted-sum fitness comparison. |
| [WarmStart/LLMWarmStart.java](WarmStart/LLMWarmStart.java) | Preliminary text-reading utility; not a working LLM API client or population generator. |
| [WarmStart/LLMcode.java](WarmStart/LLMcode.java) | Standalone text-based rule analysis/transformation utility with hard-coded file paths. |
| [WarmStart/population_100_0.5_MO.txt](WarmStart/population_100_0.5_MO.txt) | Example offline-generated population containing 100 rule pairs. |

Other population text files under `WarmStart` are experiment inputs or intermediate artifacts. A weight or population size in a filename is a label, not an instruction to the evaluator. Always check the file contents and active params.

## Objectives

### Single Objective

The single-objective configuration currently minimizes mean weighted tardiness:

```properties
eval.problem.eval-model.objectives = 1
eval.problem.eval-model.objectives.0 = mean-weighted-tardiness
pop.subpop.0.species.fitness = ec.multiobjective.MultiObjectiveFitness
pop.subpop.0.species.fitness.num-objectives = 1
pop.subpop.0.species.fitness.maximize = false
```

Use the single-objective state/configuration for this mode. Changing only the objective count in the MO configuration is not supported by its current two-element weight initialization.

### Weighted Two Objectives

The MO configuration evaluates mean flow time (`Fmean`) and mean weighted tardiness (`WTmean`):

```properties
eval.problem.eval-model.objectives = 2
eval.problem.eval-model.objectives.0 = mean-flowtime
eval.problem.eval-model.objectives.1 = mean-weighted-tardiness
weight-objective0 = 0.8
manual-normalization = true
```

The first weight is `weight-objective0`; the second is its complement. With the active benchmark normalization, selection minimizes

$$
f = \lambda_1\frac{F_{\mathrm{mean}}}{B_F}
  + (1-\lambda_1)\frac{WT_{\mathrm{mean}}}{B_{WT}},
$$

where the benchmark-rule scores $B_F$ and $B_{WT}$ come from the current scheduling set. This is weighted-sum optimization, not NSGA-II or Pareto-front selection. A raw weighted-sum score and a benchmark-normalized score represent different trade-offs.

Keep `weight-objective0` in `[0, 1]` and verify finite, positive benchmark scores. The current MO state supplies weights and baselines to individuals only inside its normalization branch, so setting `manual-normalization=false` is not a supported working configuration without a code correction. Zero-weight objectives with infinite scores also require care in the original fitness implementation.

The active MO population filename contains `0.2`, while `weight-objective0` is currently `0.8`. Confirm that the offline prompt and chosen population match the intended experiment instead of inferring the active weight from the filename.

## GP and Simulation Settings

Both provided parameter files currently specify:

| Setting | Value |
|---|---|
| Population size | 100 |
| Generations | 51 |
| GP trees per individual | 2 |
| Elites | 2 |
| Tournament size | 4 |
| Crossover / mutation / reproduction | 0.80 / 0.15 / 0.05 |
| Maximum offspring tree depth | 8 |
| Initial random-tree depth range | 2 to 6 |
| Evaluation / breeding threads | 1 / 1 |
| Simulation utilization | 0.85 |
| Due-date factor | 1.5 |
| Recorded / warmup jobs | 5000 / 1000 |
| Rotate simulation seed | true |

The shared heterogeneous simulation models machine-dependent processing rates and transport times. The crossover operator performs subtree crossover on one selected tree and swaps the other tree. The configuration also includes `limit-total-evaluation-times=100000`; inspect the state's stopping logic when comparing evaluation budgets across methods.

`pop.subpop.0.extra-behavior=fill` allows a smaller loaded population to be filled with random individuals. The single-objective params currently point to a filename labelled as a 50-individual population although the configured size is 100. Verify the actual loaded count when reporting the proportion of LLM-generated individuals.

## Functions and Terminals

The function set contains binary `+`, `-`, `*`, `/`, `Min` and `Max`. Expressions are case-sensitive Lisp trees. The shared division operator returns 1 when its denominator compares equal to positive zero; its `Double.compare` check does not protect negative zero. Intermediate overflow and non-finite priorities should be checked before using an LLM-generated rule.

The active `relative` terminal set includes:

| Terminal | Meaning |
|---|---|
| `PT` | Processing time of the current operation option on its candidate machine. |
| `NPT` | Stored next-operation processing-time metadata. |
| `WKR` | Remaining-work metadata at the current operation. |
| `NOR` | Remaining-operation-count metadata. |
| `W` | Job importance weight, not an objective preference weight. |
| `NIQ` | Number of operations in the candidate machine queue. |
| `WIQ` | Work in that queue. |
| `MWT` | Current time minus machine ready time; it can be negative. |
| `OWT` | Current time minus the operation option's ready time. |
| `TIS` | Time since the job's release. |
| `rDD` | Job due date minus current time. |
| `SL` | Job due date minus current time minus remaining work. |
| `TRANT` | Transport time associated with the candidate machine, including entry/exit handling where applicable. |

Interpret terminal effects in the relevant decision context. For example, `MWT` is constant across jobs in one sequencing queue, so `(- MWT W)` favors larger job weights for finite values. Machine-related values can differ when comparing routing options. Nonlinear combinations and `Min`/`Max` branches do not generally have a single global monotonic interpretation.

## Population Format

The loader expects an ECJ text subpopulation containing a declared individual count, indexed individuals, evaluation flags, serialized fitness records and two trees per individual. A rule-only illustration is:

```text
Tree 0:
(/ PT W)
Tree 1:
(+ WIQ TRANT)
```

This illustration is not a complete population file. Create the surrounding records using ECJ's `printSubpopulation`/`printIndividual` serialization rather than asking an LLM to invent fitness encodings. The number of serialized objectives must match the selected single- or two-objective configuration. Warm-start individuals should be unevaluated (`Evaluated: F`) so imported scores are not treated as current training fitness.

The supplied population examples include placeholder fitness values. In particular, an `_MO` filename does not guarantee a valid two-objective serialized fitness record. Inspect or regenerate these records before loading them. The state field `readIndividualWithoutFitness` by itself is not proof that the shared ECJ loader will skip fitness parsing.

Check both syntax and scheduling behavior: allowed symbols, binary arity, balanced parentheses, tree depth, duplicate pairs, finite priorities and completed simulations on representative seeds. The original workflow does not automatically regenerate failed candidates or provide the online feasibility gate added in later versions. Passing syntax checks is not evidence of scheduling quality.

## Running in IntelliJ IDEA

This folder is part of the parent Java research project, not a standalone Maven/Gradle application. Open the parent workspace, configure its `src` source root and `libraries` dependencies, and use the generic main class `yimei.jss.gp.GPRun` for the selected EvoSpeak params. The two evolution-state classes are configured by ECJ; they are not standalone `main` entry points.

Before running:

1. Check the package layout. The copied Java files still declare `mengxu.algorithm.LLM.WarmStart`, and the params reference those names, although the files are physically under `EvoSpeak/WarmStart`. Ensure the declared classes are compiled and present on the runtime classpath; changing a params class name alone does not rename its Java package.
2. Replace the active `pop.subpop.0.file` path. The supplied paths refer to `/Users/mengxu/...` on another machine. An absolute local path is simplest; ordinary relative file parameters follow ECJ's defining-params-file directory rules.
3. Prepare a population with valid serialization for the selected objective count. Do not rely on placeholder fitness numbers or an unevaluated-looking filename.
4. Choose a fresh result directory and a seed/run ID. Set the working directory deliberately because auxiliary files are written there.
5. Run a small smoke experiment before the full population/generation budget. This README does not remove the original implementation's compatibility limitations.

After those checks, a single-objective **program-arguments template** for an IDEA `GPRun` configuration is:

```text
-file D:/javaProject/GPDFJSS-MENG/src/mengxu/algorithm/EvoSpeak/WarmStart/multipletreegp-dynamicLLMWarmStart.params
-p pop.subpop.0.file=D:/experiments/EvoSpeak/validated-single-population.txt
-p seed.0=0
-p stat.file=D:/experiments/EvoSpeak/job.0.out.stat
```

Replace the population/result locations with real prepared paths and create the output directory first. For the weighted variant, select the MO params and a valid two-objective population, then add `-p weight-objective0=0.5` if equal objective weights are intended. Keep `manual-normalization=true` for the original implementation as described above.

There is no `OPENAI_API_KEY` requirement for these offline configurations. An exception requesting an API key indicates that a different, online launcher/configuration was selected.

## Results and Reproducibility

`seed.0` controls the GP random seed. When launching individual runs, explicitly set `stat.file=job.<id>.out.stat` and use the same ID for the seed. Common outputs include:

| Output pattern | Content |
|---|---|
| `job.<id>.out.stat` | Evolution statistics and learned rules, at the configured `stat.file` location. |
| `job.<id>.time.csv` | Per-generation timing from the shared GP state. |
| `job.<id>.timeSumGen.csv` | Cumulative generation timing. |
| `job.<id>.diversities.csv` | Recorded diversity measures. |
| `job.<id>.combinationnumber.csv` | Parent-combination diversity diagnostics. |
| `job.<id>.gen_0.AllIndividuals.csv` | Initial evaluated-population output from the single-objective state. |
| `job.<id>.gen_0.AllIndividuals_n.csv` | Initial evaluated-population output from the MO state. |
| `job.<id>.<generation>.parentschild.csv` | Parent/child diagnostics at selected generations. |

Other writer methods exist but may have their call sites disabled. Changing `stat.file` does not redirect all auxiliary files: most use the process working directory. The original writers can overwrite existing files, so use fresh run directories. Some shared timing state is static; separate JVM runs are preferable when collecting independent timing measurements.

Archive the exact offline prompt/model response, validated population, parameter file, objective weights, normalization choice, GP seed and outputs. Keep GP randomness separate from the simulation-seed rotation protocol. LLM-generated rules and their natural-language rationales are hypotheses; test generalization and performance independently before making comparative claims.