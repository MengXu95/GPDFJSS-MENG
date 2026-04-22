# GPJDFJSS-MENG

This repository contains Java implementations of genetic programming (GP) methods for dynamic flexible job shop scheduling (DFJSS). The codebase extends the ECJ evolutionary computation framework and includes the algorithms used across a series of papers by Meng Xu, Yi Mei, Fangfang Zhang, and Mengjie Zhang.

This README is centered around the main experiment launcher in `src/yimei/jss/gp/GPMain.java`, so that users and researchers can quickly identify:

- which algorithm corresponds to which paper,
- which parameter file to run,
- how to switch between methods,
- and which runtime arguments are being passed to ECJ.

> [!NOTE]
> This repository is intended as a research-oriented GP platform for DFJSS, helping users reproduce, understand, and extend the published methods in this project.

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

---

## 4. How to run a method from `GPMain.java`

For dynamic experiments, `GPMain.java` works by:

1. adding `-file`,
2. selecting exactly one parameter file,
3. appending `-p` overrides such as seed and output file name,
4. then calling `GPRun.main(...)`.

### Run through the IDE

1. Open the project as a Java/IntelliJ project.
2. Ensure `src/` is configured as a source root and `libraries/` is added as dependencies.
3. Open `src/yimei/jss/gp/GPMain.java`.
4. In the dynamic section, comment/uncomment the desired parameter-file line.
5. Run the `main` method of `GPMain`.

You can replace the parameter file with any method listed in the table above.

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

The following dynamic methods are explicitly listed in `GPMain.java`.

> Most parameter files and algorithm codes are located under `src/mengxu/algorithm/`.

| Method in `GPMain.java` | Related paper / purpose | Parameter file |
|---|---|---|
| ensembleGP | Xu et al., 2023, *IEEE Transactions on Evolutionary Computation*, “Genetic programming for dynamic flexible job shop scheduling: Evolution with single individuals and ensembles” | `src/mengxu/algorithm/multicaseEnsemble/ensembleContribution/multipletreegp-dynamicEnsembleContributionCrossover.params` |
| GP with lexicase selection | Xu et al., 2023, *IEEE Transactions on Evolutionary Computation*, “Genetic programming with lexicase selection for large-scale dynamic flexible job shop scheduling” | `src/mengxu/algorithm/lexicaseselection/multipletreegp-dynamicOneInstanceMultiCase.params` |
| NSGPII with semantic diversity and semantic similarity | Xu et al., 2023, *AI 2023*, “A semantic genetic programming approach to evolving heuristics for multi-objective dynamic scheduling” | `src/mengxu/algorithm/multiobjective/phenotypeNSGPII/improvedCompareOne/multipletreegp-dynamic-NSGA2-no-environmental-selection-phenotypeBreeding-improved.params` |
| MOGPD | Xu et al., 2023, *GECCO Companion*, “Multi-objective genetic programming based on decomposition on evolving scheduling heuristics for dynamic scheduling” | `src/mengxu/algorithm/multiobjective/multipletreegp-dynamic-MOEADmap.params` |
| NSGPII | Multi-objective baseline method | `src/mengxu/algorithm/multiobjective/multipletreegp-dynamic-NSGA2-no-environmental-selection.params` |
| GP with diverse partner selection | Xu et al., 2022, *GECCO Companion*, “Genetic programming with diverse partner selection for dynamic flexible job shop scheduling” | `src/mengxu/algorithm/diversepartnerselection/multipletreegp-dynamicDPS.params` |
| GP with cluster selection | Xu et al., 2022, *CEC*, “Genetic Programming with Cluster Selection for Dynamic Flexible Job Shop Scheduling” | `src/mengxu/algorithm/clusterselection/multiplecasecluster/multipletreegp-dynamicMultiCaseCluster.params` |
| Pareto set learning GP | Xu et al., 2025, *IEEE Transactions on Evolutionary Computation*, “Pareto set learning through genetic programming for multi-objective dynamic scheduling” | `/src/mengxu/algorithm/multiobjective/ParetoSetLearning/multipletreegp-dynamic-PSLnichingBasedOnHV.params` |
| GP with multi-case fitness | Xu et al., 2022, *CEC*, “Genetic programming with multi-case fitness for dynamic flexible job shop scheduling” | `src/mengxu/algorithm/averageFitness/multipletreegp-dynamicAverage.params` |

> In the current `GPMain.java`, the active line points to the multi-case fitness configuration:
>
> `src/mengxu/algorithm/averageFitness/multipletreegp-dynamicAverage.params`
