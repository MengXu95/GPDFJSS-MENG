# PSLGP-I Generation-49 Retesting

`BaselineGenerationTestMain` currently tests only PSLGP-I. Requests for NSGPII, MOGP/D or mixed method lists are rejected before testing, including direct worker requests. It reads actual `Generation:` headers, not the historical CSV snapshot labels, and never substitutes a different generation.

## Source Selection

The default selection is the `Best Individual:` saved at actual generation 49. All 300 PSLGP-I logs in the supplied `PSLIGD_BR` archive contain this complete sequencing/routing rule pair and two training objectives. One fixed pair receives all 200 test preferences.

Generation 49 has no Top-N section in these logs. Thus, saved-best selection is explicitly recorded in output metadata; it is not silently presented as the unavailable generation-49 Top-N rank 0. `--pslgp-source top-n` remains an optional strict mode and reports missing data for this archive.

## IDEA Configurations

Use the `GPDFJSS-MENG` module and its configured JDK. Run **Build > Rebuild Project** first. The two PSLGP-I configurations are in IDEA's traditional `.idea/runConfigurations` directory and use the project directory as the working directory. The reader self-test remains under `.run`.

If the two PSLGP-I entries are not visible, use **File > Reload All from Disk** (or **Synchronize** in older IDEA versions), then open **Run > Edit Configurations**. If they are still absent, close and reopen the `D:\javaProject\GPDFJSS-MENG` project. The traditional configuration files are local and ignored with `.idea`; they are not duplicated under `.run`.

| Configuration | Purpose |
|---|---|
| Baseline Gen49 - 00 Reader Self Test | Synthetic reader checks; no scheduling simulations |
| Baseline Gen49 - 01 PSLGP-I Preflight | Check 300 PSLGP-I sources; no simulations or output writes |
| Baseline Gen49 - 10 PSLGP-I Test | Retest generation-49 saved-best rules using five parallel worker JVMs |

The test configuration covers ten scenarios and thirty training seeds, giving 300 jobs. At most five jobs execute together in isolated JVMs; each job tests one fixed heuristic over 200 preferences and thirty simulation replications per preference using simulation seed 968356. No training is performed. The default command-line settings are also PSLGP-I, saved-best selection, generation 49 and five workers.

Each worker has a 2 GB heap. Five workers may require more than 10 GB of available memory once JVM overhead and the coordinating process are included. Lower `--parallel` if necessary.

The input root is:

```text
C:\Users\xumen\Desktop\2026Paper\PSLGP\FinalResultsForPaper\FinalResultsForPaper
```

The runner reads only `PSLIGD_BR/multipletreegp-dynamic`, its parameter file and the original scenario directories with `job.<seed>.out.stat`. The archive is read-only. The former NSGPII/MOGP-D IDEA test configurations have been removed for now.

## Outputs and Safety

The default output root is:

```text
src/mengxu/ruleanalysis/MPSLGP/baseline-tests-generation49
```

Each method/scenario/run/generation directory contains `test.properties`, a `missing-...-ParetoFront.csv`, `test-outcomes.csv`, `testing.log`, and, after success, `completed.properties`. The CSV records the actual source generation. Input statistics and parameter hashes are checked before and after evaluation; workers independently re-read and verify the selected rules.

Existing result files are never overwritten. `--skip-existing` reuses completed results only when their configuration and CSV hashes match. The IDEA configurations also enable `--retry-incomplete`: if an interrupted directory has the identical test specification and no active worker, complete CSV/detail data are validated and a missing completion marker is restored without simulations. Recovery validates all preference rows, run/generation identity, weights, selected-tree counts, training objectives and paired test values. Recovered results are counted as reused/skipped in batch progress.

If matching interrupted results are incomplete, their entire directory is moved to a sibling such as `generation49.incomplete-<unique-id>` before a fresh test starts. The old files remain available for inspection. An incompatible configuration, an incorrect existing completion marker or an active worker still causes a refusal; use another output root for a different experiment. This retries interrupted tests from the beginning, not from an arbitrary partial preference row.

Output eligibility is checked for every job during preflight, before any workers start, so an old conflicting directory cannot trigger a cascade that interrupts unrelated new jobs. The preflight logs `[RECOVERY READY]` for verified complete data without a marker and `[RETRY READY]` for matching partial output. It makes no output changes. Smoke runs use a separate `smoke` subdirectory and are not paper results.

## Live Progress

The IDEA coordinator console now shows preflight progress, each job's scenario/run/generation, worker PID, and log path. Child processes send their source-validation, runtime-check and test-set preparation stages to the console. The first finished preference, every ten finished preferences, and the final preference report the completed count, percentage, weights, objectives and elapsed time.

`[BATCH PROGRESS]` shows finished jobs out of the planned total, including separate completed, verified-skipped, failed and running counts. A preference count concerns one job; the batch count concerns all 300 scenario/seed jobs. Every worker line is prefixed with its identity so five parallel jobs can be distinguished. Full worker output remains in that job's `testing.log`; progress is forwarded live without flooding the coordinator console with every simulation warning.

Progress messages are event-based. A long preference evaluation can pause progress output until that evaluation finishes. Test CSVs are flushed after each preference. Logging does not change the preference grid, replication count, rule selection or fitness computation.

An already-running old JVM cannot acquire these changes automatically. After the current batch completes, rebuild the project and start the test configuration again to use the new logging. Stopping an active batch may leave incomplete output directories, which are not treated as verified completed jobs by `--skip-existing`.

## Smaller Checks

Use the preflight configuration first. For a one-job preflight, replace its run-count argument with `--runs 1` and add `--scenarios 1`. For a five-job scheduling check, use:

```text
--methods PSLGP-I --generation 49 --pslgp-source best-individual --scenarios 1 --runs 5 --parallel 5 --heap 2g --smoke
```

The smoke check uses three preferences and one replication, writes only to the isolated `smoke` subdirectory, and is not paper data. The full 300-job batch is started only when the main test configuration is run.

## Compilation

For the workspace's existing Java setup, the focused compile command is:

```powershell
& 'C:/Users/xumen/.jdks/openjdk-26.0.1/bin/javac.exe' --release 11 -encoding UTF-8 -cp 'libraries/*' -sourcepath src -d out/baseline-generation-tests src/mengxu/ruleanalysis/MPSLGP/BaselineGenerationTestMain.java
```

Compilation does not start training or testing. The IDEA reader-self-test configuration is the runtime check for the new reader. No historical analysis CSV or manuscript is regenerated by this program.