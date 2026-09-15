# GPJDFJSS-MENG

This repository contains Java implementations of genetic programming (GP) methods for dynamic flexible job shop scheduling (DFJSS). The codebase extends the ECJ evolutionary computation framework and includes the algorithms used across a series of papers by Meng Xu, Yi Mei, Fangfang Zhang, and Mengjie Zhang.

This README is centered around the main experiment launcher in `src/yimei/jss/gp/GPMain.java`, so that users and researchers can quickly identify:

- which algorithm corresponds to which paper,
- which parameter file to run,
- how to switch between methods,
- and which runtime arguments are being passed to ECJ.

> [!NOTE]
> This repository is intended as a research-oriented GP platform for DFJSS, helping users reproduce, understand, and extend the published methods in this project.

> [!IMPORTANT]
> **Our EvoSpeak paper is based on OfflineEvoSpeak.** OnlineEvoSpeak was implemented **after the paper was accepted** to make the workflow easier and more straightforward to run. It is a convenience extension, not the implementation used to produce the paper's reported results.

---

## 1. Repository purpose

The project supports research on automatically evolving dispatching and routing rules for DFJSS using GP. It includes:

- baseline GP methods,
- dynamic DFJSS simulation-based evaluation,
- multi-objective GP,
- GP with lexicase selection,
- GP with ensemble learning,
- GP with diverse partner selection,
- GP with cluster-based selection,
- Pareto set learning GP,
- LLM-assisted GP with offline and online EvoSpeak,
- and GP with multi-case fitness evaluation.

The implementation is built on top of ECJ:

- ECJ project: https://cs.gmu.edu/~eclab/projects/ecj/
- ECJ manual: https://cs.gmu.edu/~eclab/projects/ecj/docs/manual/manual.pdf

---

## 2. Project layout

The most relevant folders are:

- `src/ec/`: ECJ source code.
- `src/yimei/jss/`: core DFJSS implementation and scheduling support code.
- `src/yimei/jss/gp/`: GP entry points and execution logic.
- `src/yimei/jss/simulation/`: dynamic scheduling simulation.
- `src/yimei/jss/rule/`: rule representation.
- `src/yimei/jss/ruleanalysis/`: post-hoc analysis utilities.
- `src/mengxu/algorithm/`: parameter files and method-specific algorithm configurations.
- [OfflineEvoSpeak](src/mengxu/algorithm/OfflineEvoSpeak/README.md): the offline EvoSpeak implementation used in the paper, with instructions and prompts for preparing TXT initial populations.
- [OnlineEvoSpeak](src/mengxu/algorithm/OnlineEvoSpeak/README.md): the online extension added after acceptance, with automatic LLM API calls, population validation, TXT generation and GP initialization.
- `libraries/`: project dependencies.

---

## 3. Main entry points

### `GPRun.java`

`src/yimei/jss/gp/GPRun.java` is the generic ECJ-based GP runner. It loads a parameter database and executes one or more jobs.

Typical usage pattern:

```bash
java yimei.jss.gp.GPRun -file <path-to-params-file> -p seed.0=0 -p stat.file=job.0.out.stat
```

### `GPMain.java`

`src/yimei/jss/gp/GPMain.java` is a convenient experiment launcher for batch runs. It shows how the authors selected parameter files and runtime arguments for different papers.

Key switches in `GPMain.java`:

- `int maxTests = 29;`
  - controls the maximum number of seed-based runs.

### EvoSpeak: Offline and Online

| Version | Relationship to the paper | Workflow |
|---|---|---|
| [OfflineEvoSpeak](src/mengxu/algorithm/OfflineEvoSpeak/README.md) | **Implementation used in the EvoSpeak paper.** | Use an LLM separately to generate rules, prepare a TXT initial population, then load it into GP. |
| [OnlineEvoSpeak](src/mengxu/algorithm/OnlineEvoSpeak/README.md) | **Convenience extension implemented after paper acceptance.** | The program calls the configured LLM API, validates the generated rules, writes the TXT initial population, then starts GP automatically. |

**OfflineEvoSpeak: a simple workflow without API integration.** You can use free-to-access LLM models or free web/chat interfaces to generate the initial heuristics, subject to the provider's availability, terms and usage limits. The Java training program does not require an LLM API key: it reads the prepared population locally. Here, "offline" means that LLM generation is separate from the GP run; the external model can be hosted or local and may still require an account or Internet access.

To get started, use the [single-objective prompt](src/mengxu/algorithm/OfflineEvoSpeak/prompts/single-objective.md) or [multi-objective prompt](src/mengxu/algorithm/OfflineEvoSpeak/prompts/multi-objective.md), follow the [offline instructions](src/mengxu/algorithm/OfflineEvoSpeak/README.md) to validate and prepare the TXT, and run `yimei.jss.gp.GPRun` with the corresponding OfflineEvoSpeak params and `pop.subpop.0.file`. The prompts can be adapted to your objectives and scheduling scenario; record any changes when comparing experiments.

**OnlineEvoSpeak: automatic generation and training.** After acceptance, we implemented this version to remove the manual steps of submitting generation requests, handling replacement batches and preparing the validated TXT. For a hosted API service, **you must provide your own API key**, together with the matching endpoint and model/deployment name. The repository does not supply credentials or API credits. API calls may incur charges and are subject to account quotas; access to a free chat interface does not necessarily include free API access. Store credentials in the documented Git-ignored local params or environment variables, never in committed files. Follow the [online setup guide](src/mengxu/algorithm/OnlineEvoSpeak/README.md), then run `mengxu.algorithm.OnlineEvoSpeak.EvoSpeakMain` or select OnlineEvoSpeak in `GPMain`.

**Results depend on the model and experimental settings.** In both workflows, the initial heuristics and final GP results depend on the LLM provider, model/version, prompt, reference examples, generation settings and stochastic outputs, as well as the GP seed, objectives, weights, simulation scenario and evaluation budget. A different free model or the online extension is not guaranteed to reproduce the paper's numerical results or improve on them. For paper reproduction, use **OfflineEvoSpeak** with the model, prompts, initial populations and experimental settings specified in the paper. Treat runs with changed models or settings as new experiments. Archive the actual prompt, model/version, response, validated TXT, params and seeds; a fixed GP seed alone does not make LLM generation reproducible. Online same-seed reruns overwrite their generated outputs, so archive results before repeating a seed when needed.

---

## 4. How to run a method from `GPMain.java`

For dynamic experiments, `GPMain.java` works by:

1. adding `-file`,
2. selecting exactly one parameter file,
3. appending `-p` overrides such as seed and output file name,
4. then calling `GPRun.main(...)` for the original methods, including OfflineEvoSpeak, or `EvoSpeakMain.main(...)` for OnlineEvoSpeak so LLM generation and validation occur before GP initialization.

### Run through the IDE

1. Open the project as a Java/IntelliJ project.
2. Ensure `src/` is configured as a source root and `libraries/` is added as dependencies.
3. Open `src/yimei/jss/gp/GPMain.java`.
4. In the dynamic section, comment/uncomment the desired parameter-file line.
5. Run the `main` method of `GPMain`.

You can replace the parameter file with a method listed in the method-to-paper table below. To reproduce the EvoSpeak paper, select the OfflineEvoSpeak params and supply a prepared TXT population; selecting OnlineEvoSpeak instead invokes the post-acceptance API workflow.

---

## 5. Important runtime arguments

The main arguments visible in `GPMain.java` are:

### `-file`

Specifies the ECJ parameter file to load.

Example:

```text
-file src/mengxu/algorithm/averageFitness/multipletreegp-dynamicAverage.params
```

### `-p`

Overrides a parameter from the command line.

Examples used in `GPMain.java`:

```text
-p seed.0=7
-p stat.file=job.7.out.stat
-p filePath=<path-to-instance.fjs>
```

### `seed.0`

Sets the random seed for the run.

### `stat.file`

Controls the ECJ statistics output file. In the dynamic branch the naming pattern is:

```text
job.<seed>.out.stat
```

### `filePath`

This appears in the legacy code path, but the experiments documented in this README focus on DFJSS.

---

## 6. Notes for DFJSS experiments

Several comments in `GPMain.java` indicate additional simulation settings that should be checked before reproducing some papers.

For the following methods, the code comments note that you should set both of the following in the simulation configuration:

- `useLS = true`
- `warmupSame = true`

This note applies to:

- ensembleGP,
- GP with lexicase selection,
- GP with diverse partner selection,
- GP with multi-case fitness.

Researchers reproducing published results should verify these settings in the simulation-related classes before large experimental runs.

---

## 7. Scope of this repository

This repository and the papers listed below are focused on DFJSS rather than static JSS.

If you are primarily interested in static JSS or static FJSS experiments, please refer to the original codebase by Yi Mei and collaborators, on which this repository was built.

---

## 8. Output files

The most common outputs are:

- `job.<id>.out.stat`: ECJ statistics log for a run.
- CSV outputs generated by analysis/test utilities.
- other run artifacts depending on the selected parameter file and evaluator.

These outputs are usually written to the project root unless redirected in the parameter file.

---

## 9. Recommended workflow for researchers

1. Start from `GPMain.java` to identify the published method you want.
2. Confirm the associated parameter file from the table above.
3. Run one seed first as a smoke test.
4. Check the generated `job.*.out.stat` file.
5. Increase the number of seeds by editing the loop bounds or by scripting repeated `GPRun` calls.
6. Archive both the parameter file and the output logs for reproducibility.

---

## 10. Publications represented in this repository

### EvoSpeak

**Meng Xu**, Jiao Liu, and Yew Soon Ong. "EvoSpeak: Large Language Models for Interpretable Genetic Programming-Evolved Heuristics." *IEEE Transactions on Evolutionary Computation*, 2026. DOI: [10.1109/TEVC.2026.3705492](https://doi.org/10.1109/TEVC.2026.3705492).

The paper is based on [OfflineEvoSpeak](src/mengxu/algorithm/OfflineEvoSpeak/README.md). [OnlineEvoSpeak](src/mengxu/algorithm/OnlineEvoSpeak/README.md) is an additional implementation developed after acceptance for easier automated use, not the source of the results reported in the paper.

### Ensemble GP

Xu, M., Mei, Y., Zhang, F., & Zhang, M. (2023). Genetic programming for dynamic flexible job shop scheduling: Evolution with single individuals and ensembles. *IEEE Transactions on Evolutionary Computation*, 28(6), 1761-1775.

### Lexicase selection GP

Xu, M., Mei, Y., Zhang, F., & Zhang, M. (2023). Genetic programming with lexicase selection for large-scale dynamic flexible job shop scheduling. *IEEE Transactions on Evolutionary Computation*, 28(5), 1235-1249.

### Semantic NSGPII

Xu, M., Mei, Y., Zhang, F., & Zhang, M. (2023). A semantic genetic programming approach to evolving heuristics for multi-objective dynamic scheduling. In *Australasian Joint Conference on Artificial Intelligence* (pp. 403-415). Springer.

### MOGPD

Xu, M., Mei, Y., Zhang, F., & Zhang, M. (2023). Multi-objective genetic programming based on decomposition on evolving scheduling heuristics for dynamic scheduling. In *Proceedings of the Companion Conference on Genetic and Evolutionary Computation* (pp. 427-430).

### GP with diverse partner selection

Xu, M., Mei, Y., Zhang, F., & Zhang, M. (2022). Genetic programming with diverse partner selection for dynamic flexible job shop scheduling. In *Proceedings of the Genetic and Evolutionary Computation Conference Companion* (pp. 615-618).

### GP with cluster selection

Xu, M., Mei, Y., Zhang, F., & Zhang, M. (2022). Genetic Programming with Cluster Selection for Dynamic Flexible Job Shop Scheduling. In *2022 IEEE Congress on Evolutionary Computation (CEC)*. DOI: 10.1109/CEC55065.2022.9870431.

### Pareto set learning GP

Xu M, Mei Y, Zhang F, et al. (2025). Pareto set learning through genetic programming for multi-objective dynamic scheduling. *IEEE Transactions on Evolutionary Computation*, DOI=10.1109/TEVC.2025.3568375.

### GP with multi-case fitness

Xu, M., Zhang, F., Mei, Y., & Zhang, M. (2022). Genetic programming with multi-case fitness for dynamic flexible job shop scheduling. In *2022 IEEE Congress on Evolutionary Computation (CEC)* (pp. 1-8).

> [!IMPORTANT]
> **If this code is useful for your research, please consider citing the relevant papers above.**  
> 🌟 Good luck with your research!

---

## 11. Contact and acknowledgement

This codebase builds on the original JSS/GP framework developed by Yi Mei and collaborators, with additional algorithm implementations and experiment configurations for subsequent DFJSS research.

For ECJ-specific details, consult the official ECJ documentation. For DFJSS paper reproduction, use the parameter files listed above as the primary starting point. For static JSS-related work, please use the original Yi Mei codebase.

---

## 12. Method-to-paper mapping

The following dynamic methods are available in this repository. Select the appropriate parameter file in [GPMain](src/yimei/jss/gp/GPMain.java), or use the method's documented entry point.

> Most parameter files and algorithm codes are located under `src/mengxu/algorithm/`.

| Method | Related paper / purpose | Parameter file |
|---|---|---|
| OfflineEvoSpeak | Xu, Liu, and Ong, 2026, *IEEE Transactions on Evolutionary Computation*, "EvoSpeak: Large Language Models for Interpretable Genetic Programming-Evolved Heuristics." **The paper's implementation.** | [Single objective](src/mengxu/algorithm/OfflineEvoSpeak/WarmStart/multipletreegp-dynamicLLMWarmStart.params), [weighted two objectives](src/mengxu/algorithm/OfflineEvoSpeak/WarmStart/multipletreegp-dynamicLLMWarmStartMO.params) |
| OnlineEvoSpeak | Automated LLM-to-TXT-to-GP extension implemented **after paper acceptance**. Hosted API use requires your own credentials; results depend on the model and settings. | [Single objective](src/mengxu/algorithm/OnlineEvoSpeak/multipletreegp-dynamicLLMWarmStart.params), [weighted two objectives](src/mengxu/algorithm/OnlineEvoSpeak/multipletreegp-dynamicLLMWarmStartMO.params); see the online guide for private local overrides. |
| ensembleGP | Xu et al., 2023, *IEEE Transactions on Evolutionary Computation*, “Genetic programming for dynamic flexible job shop scheduling: Evolution with single individuals and ensembles” | `src/mengxu/algorithm/multicaseEnsemble/ensembleContribution/multipletreegp-dynamicEnsembleContributionCrossover.params` |
| GP with lexicase selection | Xu et al., 2023, *IEEE Transactions on Evolutionary Computation*, “Genetic programming with lexicase selection for large-scale dynamic flexible job shop scheduling” | `src/mengxu/algorithm/lexicaseselection/multipletreegp-dynamicOneInstanceMultiCase.params` |
| NSGPII with semantic diversity and semantic similarity | Xu et al., 2023, *AI 2023*, “A semantic genetic programming approach to evolving heuristics for multi-objective dynamic scheduling” | `src/mengxu/algorithm/multiobjective/phenotypeNSGPII/improvedCompareOne/multipletreegp-dynamic-NSGA2-no-environmental-selection-phenotypeBreeding-improved.params` |
| MOGPD | Xu et al., 2023, *GECCO Companion*, “Multi-objective genetic programming based on decomposition on evolving scheduling heuristics for dynamic scheduling” | `src/mengxu/algorithm/multiobjective/multipletreegp-dynamic-MOEADmap.params` |
| NSGPII | Multi-objective baseline method | `src/mengxu/algorithm/multiobjective/multipletreegp-dynamic-NSGA2-no-environmental-selection.params` |
| GP with diverse partner selection | Xu et al., 2022, *GECCO Companion*, “Genetic programming with diverse partner selection for dynamic flexible job shop scheduling” | `src/mengxu/algorithm/diversepartnerselection/multipletreegp-dynamicDPS.params` |
| GP with cluster selection | Xu et al., 2022, *CEC*, “Genetic Programming with Cluster Selection for Dynamic Flexible Job Shop Scheduling” | `src/mengxu/algorithm/clusterselection/multiplecasecluster/multipletreegp-dynamicMultiCaseCluster.params` |
| Pareto set learning GP | Xu et al., 2025, *IEEE Transactions on Evolutionary Computation*, “Pareto set learning through genetic programming for multi-objective dynamic scheduling” | `/src/mengxu/algorithm/multiobjective/ParetoSetLearning/multipletreegp-dynamic-PSLnichingBasedOnHV.params` |
| GP with multi-case fitness | Xu et al., 2022, *CEC*, “Genetic programming with multi-case fitness for dynamic flexible job shop scheduling” | `src/mengxu/algorithm/averageFitness/multipletreegp-dynamicAverage.params` |

> In the current [GPMain](src/yimei/jss/gp/GPMain.java), the active line selects the **OnlineEvoSpeak multi-objective** private profile, which inherits the [public MO configuration](src/mengxu/algorithm/OnlineEvoSpeak/multipletreegp-dynamicLLMWarmStartMO.params). This is not the paper-reproduction workflow.
>
> Check the selected parameter-file line before running. For the EvoSpeak paper, use the OfflineEvoSpeak configuration and prepared initial population described above. Private credential files are not included in a fresh checkout; create them locally only when using a hosted API, following the OnlineEvoSpeak guide.
