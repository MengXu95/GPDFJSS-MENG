# Latest MPSLGP results (2026-11-03)

All methods use completed preference-runtime-v2 experiments. Raw experiments are read-only.

Audit: 720 training runs; 1,620 task tests; 324,000 preference outcomes; 108 paired tests.

## Full versus published PSLGP-I

Main figure uses newly recomputed Full-only descriptive references. Published PSLGP-I values retain their original references; no cross-paper significance or common-reference superiority is claimed.

Numerically favorable Full rows (HV/IGD/GD): {'HV': 11, 'IGD': 11, 'GD': 10}. These are not statistical wins.

## Transfer ablations

Full has better mean HV/IGD/GD than no transfer on {'HV': 15, 'IGD': 14, 'GD': 17} targets, and versus fixed transfer on {'HV': 7, 'IGD': 7, 'GD': 9} targets.

No comparison survives the 108-test Holm family: minimum unadjusted p=0.00072960; adjusted p=0.07879700.

Quality indicators are conditional on valid preferences for controls. Every seed is retained. Invalid outcomes are reported separately, not silently dropped.

```csv
Algorithm,TrainingRuns,TaskRuns,PreferenceRows,InvalidPreferences,RunsWithInvalid,SingleOutcomeRuns,MeanDistinctOutcomes,MeanNondominatedOutcomes
full,240,540,108000,0,0,48,170.52962962962962,5.65
no-transfer,240,540,108000,192,5,39,170.0962962962963,5.688888888888889
fixed-transfer,240,540,108000,171,4,57,166.2074074074074,5.366666666666666
```

Representative preference-response seed: 11, selected by the lower median of equal-group mean task IGD ranks for R4/H3; no cherry-picked optimum.

Runtime is whole-group wall time under concurrent jobs, counted once per method/group/seed; not a hardware-independent speedup.

Shared normalization changes absolute values: Full-only and three-method scores must not be mixed. Raw empirical attainment avoids these normalization bounds.