# Unified Transfer-Ablation Analysis

Common per-scenario min/max/reference front over Full/no-transfer ONLY; separate from main-experiment and historical three-profile references

Exact two-sided paired Wilcoxon signed-rank;zero differences omitted;midranks;raw alpha0.05;no multiple-comparison correction

Arrows refer to Full relative to each control; no family-wise significance or equivalence is established.

| Control     |   GD |   HV |   IGD |
|:------------|-----:|-----:|------:|
| no-transfer |   16 |   15 |    14 |

|                                     |   0 |
|:------------------------------------|----:|
| ('no-transfer', 'GD', '=')          |  13 |
| ('no-transfer', 'GD', '\\uparrow')  |   5 |
| ('no-transfer', 'HV', '=')          |  16 |
| ('no-transfer', 'HV', '\\uparrow')  |   2 |
| ('no-transfer', 'IGD', '=')         |  15 |
| ('no-transfer', 'IGD', '\\uparrow') |   3 |

| Group   |   Task |   Scenario | Metric   | Control     |   FullMean |   ControlMean | FullMeanBetter   |   Runs |      PValue |   Alpha | Correction   |   PositiveRankSum |   NegativeRankSum | Symbol   | Significant   |
|:--------|-------:|-----------:|:---------|:------------|-----------:|--------------:|:-----------------|-------:|------------:|--------:|:-------------|------------------:|------------------:|:---------|:--------------|
| R1      |      0 |          1 | HV       | no-transfer | 0.905883   |    0.899708   | True             |     30 | 0.318396    |    0.05 | none         |               282 |               183 | =        | False         |
| R1      |      0 |          1 | IGD      | no-transfer | 0.0523968  |    0.0580418  | True             |     30 | 0.280087    |    0.05 | none         |               286 |               179 | =        | False         |
| R1      |      0 |          1 | GD       | no-transfer | 0.0540372  |    0.0598563  | True             |     30 | 0.183969    |    0.05 | none         |               298 |               167 | =        | False         |
| R1      |      1 |          2 | HV       | no-transfer | 0.977806   |    0.97492    | True             |     30 | 0.381798    |    0.05 | none         |               276 |               189 | =        | False         |
| R1      |      1 |          2 | IGD      | no-transfer | 0.0201912  |    0.0242569  | True             |     30 | 0.124134    |    0.05 | none         |               308 |               157 | =        | False         |
| R1      |      1 |          2 | GD       | no-transfer | 0.0132313  |    0.0135129  | True             |     30 | 0.935399    |    0.05 | none         |               237 |               228 | =        | False         |
| R2      |      0 |          3 | HV       | no-transfer | 0.967937   |    0.965241   | True             |     30 | 0.145999    |    0.05 | none         |               304 |               161 | =        | False         |
| R2      |      0 |          3 | IGD      | no-transfer | 0.0219172  |    0.0226143  | True             |     30 | 0.318396    |    0.05 | none         |               282 |               183 | =        | False         |
| R2      |      0 |          3 | GD       | no-transfer | 0.0135362  |    0.0137978  | True             |     30 | 0.157948    |    0.05 | none         |               302 |               163 | =        | False         |
| R2      |      1 |          4 | HV       | no-transfer | 0.920105   |    0.922452   | False            |     30 | 0.655438    |    0.05 | none         |               210 |               255 | =        | False         |
| R2      |      1 |          4 | IGD      | no-transfer | 0.0550004  |    0.0503918  | False            |     30 | 0.262122    |    0.05 | none         |               177 |               288 | =        | False         |
| R2      |      1 |          4 | GD       | no-transfer | 0.0265332  |    0.0346899  | True             |     30 | 0.439967    |    0.05 | none         |               271 |               194 | =        | False         |
| R3      |      0 |          6 | HV       | no-transfer | 0.802201   |    0.803067   | False            |     30 | 0.612006    |    0.05 | none         |               207 |               258 | =        | False         |
| R3      |      0 |          6 | IGD      | no-transfer | 0.126725   |    0.12124    | False            |     30 | 0.308521    |    0.05 | none         |               182 |               283 | =        | False         |
| R3      |      0 |          6 | GD       | no-transfer | 0.122699   |    0.115533   | False            |     30 | 0.129353    |    0.05 | none         |               158 |               307 | =        | False         |
| R3      |      1 |          7 | HV       | no-transfer | 0.98615    |    0.985034   | True             |     30 | 0.244946    |    0.05 | none         |               290 |               175 | =        | False         |
| R3      |      1 |          7 | IGD      | no-transfer | 0.00746525 |    0.00739225 | False            |     30 | 0.318396    |    0.05 | none         |               282 |               183 | =        | False         |
| R3      |      1 |          7 | GD       | no-transfer | 0.00710421 |    0.0437519  | True             |     30 | 0.109432    |    0.05 | none         |               311 |               154 | =        | False         |
| R4      |      0 |          1 | HV       | no-transfer | 0.904832   |    0.884958   | True             |     30 | 0.0128337   |    0.05 | none         |               352 |               113 | \uparrow | True          |
| R4      |      0 |          1 | IGD      | no-transfer | 0.0538478  |    0.0728297  | True             |     30 | 0.00577649  |    0.05 | none         |               364 |               101 | \uparrow | True          |
| R4      |      0 |          1 | GD       | no-transfer | 0.0572892  |    0.113464   | True             |     30 | 0.0013406   |    0.05 | none         |               383 |                82 | \uparrow | True          |
| R4      |      1 |          3 | HV       | no-transfer | 0.963723   |    0.955837   | True             |     30 | 0.00501261  |    0.05 | none         |               366 |                99 | \uparrow | True          |
| R4      |      1 |          3 | IGD      | no-transfer | 0.0230977  |    0.0272211  | True             |     30 | 0.00711146  |    0.05 | none         |               361 |               104 | \uparrow | True          |
| R4      |      1 |          3 | GD       | no-transfer | 0.0159544  |    0.0210121  | True             |     30 | 3.45316e-05 |    0.05 | none         |               419 |                46 | \uparrow | True          |
| R4      |      2 |          5 | HV       | no-transfer | 0.975121   |    0.973327   | True             |     30 | 0.41613     |    0.05 | none         |               273 |               192 | =        | False         |
| R4      |      2 |          5 | IGD      | no-transfer | 0.0147686  |    0.0157186  | True             |     30 | 0.177193    |    0.05 | none         |               299 |               166 | =        | False         |
| R4      |      2 |          5 | GD       | no-transfer | 0.0112243  |    0.0138146  | True             |     30 | 0.100397    |    0.05 | none         |               313 |               152 | =        | False         |
| R5      |      0 |          8 | HV       | no-transfer | 0.883829   |    0.872083   | True             |     30 | 0.183969    |    0.05 | none         |               298 |               167 | =        | False         |
| R5      |      0 |          8 | IGD      | no-transfer | 0.0656492  |    0.0735855  | True             |     30 | 0.145999    |    0.05 | none         |               304 |               161 | =        | False         |
| R5      |      0 |          8 | GD       | no-transfer | 0.0495073  |    0.0631927  | True             |     30 | 0.0404897   |    0.05 | none         |               332 |               133 | \uparrow | True          |
| R5      |      1 |          9 | HV       | no-transfer | 0.944361   |    0.944553   | False            |     30 | 1           |    0.05 | none         |               232 |               233 | =        | False         |
| R5      |      1 |          9 | IGD      | no-transfer | 0.0309283  |    0.031356   | True             |     30 | 0.855272    |    0.05 | none         |               242 |               223 | =        | False         |
| R5      |      1 |          9 | GD       | no-transfer | 0.0263082  |    0.02901    | True             |     30 | 0.452164    |    0.05 | none         |               270 |               195 | =        | False         |
| H1      |      0 |          1 | HV       | no-transfer | 0.904864   |    0.902235   | True             |     30 | 0.556113    |    0.05 | none         |               262 |               203 | =        | False         |
| H1      |      0 |          1 | IGD      | no-transfer | 0.0591184  |    0.0584359  | False            |     30 | 0.685047    |    0.05 | none         |               253 |               212 | =        | False         |
| H1      |      0 |          1 | GD       | no-transfer | 0.0729688  |    0.0597802  | False            |     30 | 0.151887    |    0.05 | none         |               303 |               162 | =        | False         |
| H1      |      1 |         10 | HV       | no-transfer | 0.919757   |    0.909872   | True             |     30 | 0.129353    |    0.05 | none         |               307 |               158 | =        | False         |
| H1      |      1 |         10 | IGD      | no-transfer | 0.0435072  |    0.0510107  | True             |     30 | 0.262122    |    0.05 | none         |               288 |               177 | =        | False         |
| H1      |      1 |         10 | GD       | no-transfer | 0.0303819  |    0.0414662  | True             |     30 | 0.0605652   |    0.05 | none         |               324 |               141 | =        | False         |
| H2      |      0 |          5 | HV       | no-transfer | 0.976482   |    0.973887   | True             |     30 | 0.183969    |    0.05 | none         |               298 |               167 | =        | False         |
| H2      |      0 |          5 | IGD      | no-transfer | 0.0138137  |    0.0158334  | True             |     30 | 0.170598    |    0.05 | none         |               300 |               165 | =        | False         |
| H2      |      0 |          5 | GD       | no-transfer | 0.00808914 |    0.025741   | True             |     30 | 0.00374443  |    0.05 | none         |               370 |                95 | \uparrow | True          |
| H2      |      1 |          6 | HV       | no-transfer | 0.813143   |    0.786725   | True             |     30 | 0.212933    |    0.05 | none         |               294 |               171 | =        | False         |
| H2      |      1 |          6 | IGD      | no-transfer | 0.114216   |    0.131617   | True             |     30 | 0.244946    |    0.05 | none         |               290 |               175 | =        | False         |
| H2      |      1 |          6 | GD       | no-transfer | 0.114589   |    0.129126   | True             |     30 | 0.502761    |    0.05 | none         |               266 |               199 | =        | False         |
| H3      |      0 |          1 | HV       | no-transfer | 0.910915   |    0.89812    | True             |     30 | 0.0522633   |    0.05 | none         |               327 |               138 | =        | False         |
| H3      |      0 |          1 | IGD      | no-transfer | 0.0505004  |    0.0600101  | True             |     30 | 0.0277414   |    0.05 | none         |               339 |               126 | \uparrow | True          |
| H3      |      0 |          1 | GD       | no-transfer | 0.0579985  |    0.0633199  | True             |     30 | 0.477106    |    0.05 | none         |               268 |               197 | =        | False         |
| H3      |      1 |          5 | HV       | no-transfer | 0.974624   |    0.97226    | True             |     30 | 0.100397    |    0.05 | none         |               313 |               152 | =        | False         |
| H3      |      1 |          5 | IGD      | no-transfer | 0.0142977  |    0.0165711  | True             |     30 | 0.253436    |    0.05 | none         |               289 |               176 | =        | False         |
| H3      |      1 |          5 | GD       | no-transfer | 0.0118132  |    0.0134173  | True             |     30 | 0.349211    |    0.05 | none         |               279 |               186 | =        | False         |
| H3      |      2 |          8 | HV       | no-transfer | 0.871064   |    0.855782   | True             |     30 | 0.0840655   |    0.05 | none         |               317 |               148 | =        | False         |
| H3      |      2 |          8 | IGD      | no-transfer | 0.07377    |    0.0884061  | True             |     30 | 0.0698929   |    0.05 | none         |               321 |               144 | =        | False         |
| H3      |      2 |          8 | GD       | no-transfer | 0.0561228  |    0.0825719  | True             |     30 | 0.0120476   |    0.05 | none         |               353 |               112 | \uparrow | True          |