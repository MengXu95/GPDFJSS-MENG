# Unified Transfer-Ablation Analysis

Common per-scenario min/max/reference front over Full/no-transfer/fixed-transfer ONLY; separate from main-experiment references

Exact two-sided paired Wilcoxon signed-rank;zero differences omitted;midranks;raw alpha0.05;no multiple-comparison correction

Arrows refer to Full relative to each control; no family-wise significance or equivalence is established.

| Control        |   GD |   HV |   IGD |
|:---------------|-----:|-----:|------:|
| fixed-transfer |    9 |    7 |     7 |
| no-transfer    |   17 |   15 |    14 |

|                                          |   0 |
|:-----------------------------------------|----:|
| ('fixed-transfer', 'GD', '=')            |  18 |
| ('fixed-transfer', 'HV', '=')            |  14 |
| ('fixed-transfer', 'HV', '\\downarrow')  |   3 |
| ('fixed-transfer', 'HV', '\\uparrow')    |   1 |
| ('fixed-transfer', 'IGD', '=')           |  16 |
| ('fixed-transfer', 'IGD', '\\downarrow') |   2 |
| ('no-transfer', 'GD', '=')               |  11 |
| ('no-transfer', 'GD', '\\uparrow')       |   7 |
| ('no-transfer', 'HV', '=')               |  15 |
| ('no-transfer', 'HV', '\\uparrow')       |   3 |
| ('no-transfer', 'IGD', '=')              |  16 |
| ('no-transfer', 'IGD', '\\uparrow')      |   2 |

| Group   |   Task |   Scenario | Metric   | Control        |   FullMean |   ControlMean | FullMeanBetter   |   Runs |      PValue |   Alpha | Correction   |   PositiveRankSum |   NegativeRankSum | Symbol     | Significant   |
|:--------|-------:|-----------:|:---------|:---------------|-----------:|--------------:|:-----------------|-------:|------------:|--------:|:-------------|------------------:|------------------:|:-----------|:--------------|
| R1      |      0 |          1 | HV       | no-transfer    | 0.931814   |    0.929812   | True             |     30 | 0.489846    |    0.05 | none         |               267 |               198 | =          | False         |
| R1      |      0 |          1 | HV       | fixed-transfer | 0.931814   |    0.943869   | False            |     30 | 0.00538278  |    0.05 | none         |               100 |               365 | \downarrow | True          |
| R1      |      0 |          1 | IGD      | no-transfer    | 0.0388283  |    0.0391804  | True             |     30 | 0.569858    |    0.05 | none         |               261 |               204 | =          | False         |
| R1      |      0 |          1 | IGD      | fixed-transfer | 0.0388283  |    0.0311774  | False            |     30 | 0.0208499   |    0.05 | none         |               121 |               344 | \downarrow | True          |
| R1      |      0 |          1 | GD       | no-transfer    | 0.0241113  |    0.0257092  | True             |     30 | 0.198076    |    0.05 | none         |               296 |               169 | =          | False         |
| R1      |      0 |          1 | GD       | fixed-transfer | 0.0241113  |    0.0221406  | False            |     30 | 0.640825    |    0.05 | none         |               209 |               256 | =          | False         |
| R1      |      1 |          2 | HV       | no-transfer    | 0.976332   |    0.97345    | True             |     30 | 0.381798    |    0.05 | none         |               276 |               189 | =          | False         |
| R1      |      1 |          2 | HV       | fixed-transfer | 0.976332   |    0.975715   | True             |     30 | 0.715133    |    0.05 | none         |               251 |               214 | =          | False         |
| R1      |      1 |          2 | IGD      | no-transfer    | 0.0122942  |    0.0171692  | True             |     30 | 0.220647    |    0.05 | none         |               293 |               172 | =          | False         |
| R1      |      1 |          2 | IGD      | fixed-transfer | 0.0122942  |    0.0128697  | True             |     30 | 0.792159    |    0.05 | none         |               246 |               219 | =          | False         |
| R1      |      1 |          2 | GD       | no-transfer    | 0.014259   |    0.0193378  | True             |     30 | 0.776569    |    0.05 | none         |               247 |               218 | =          | False         |
| R1      |      1 |          2 | GD       | fixed-transfer | 0.014259   |    0.0127024  | False            |     30 | 0.903226    |    0.05 | none         |               226 |               239 | =          | False         |
| R2      |      0 |          3 | HV       | no-transfer    | 0.97258    |    0.970182   | True             |     30 | 0.134735    |    0.05 | none         |               306 |               159 | =          | False         |
| R2      |      0 |          3 | HV       | fixed-transfer | 0.97258    |    0.970522   | True             |     30 | 0.452164    |    0.05 | none         |               270 |               195 | =          | False         |
| R2      |      0 |          3 | IGD      | no-transfer    | 0.0182988  |    0.0188936  | True             |     30 | 0.262122    |    0.05 | none         |               288 |               177 | =          | False         |
| R2      |      0 |          3 | IGD      | fixed-transfer | 0.0182988  |    0.0205399  | True             |     30 | 0.477106    |    0.05 | none         |               268 |               197 | =          | False         |
| R2      |      0 |          3 | GD       | no-transfer    | 0.0122334  |    0.0125686  | True             |     30 | 0.19093     |    0.05 | none         |               297 |               168 | =          | False         |
| R2      |      0 |          3 | GD       | fixed-transfer | 0.0122334  |    0.0141205  | True             |     30 | 0.730342    |    0.05 | none         |               250 |               215 | =          | False         |
| R2      |      1 |          4 | HV       | no-transfer    | 0.90952    |    0.91184    | False            |     30 | 0.655438    |    0.05 | none         |               210 |               255 | =          | False         |
| R2      |      1 |          4 | HV       | fixed-transfer | 0.90952    |    0.922235   | False            |     30 | 0.00814298  |    0.05 | none         |               106 |               359 | \downarrow | True          |
| R2      |      1 |          4 | IGD      | no-transfer    | 0.0533752  |    0.049427   | False            |     30 | 0.381798    |    0.05 | none         |               189 |               276 | =          | False         |
| R2      |      1 |          4 | IGD      | fixed-transfer | 0.0533752  |    0.0460151  | False            |     30 | 0.036435    |    0.05 | none         |               131 |               334 | \downarrow | True          |
| R2      |      1 |          4 | GD       | no-transfer    | 0.0345808  |    0.0441196  | True             |     30 | 0.145999    |    0.05 | none         |               304 |               161 | =          | False         |
| R2      |      1 |          4 | GD       | fixed-transfer | 0.0345808  |    0.0332172  | False            |     30 | 0.0522633   |    0.05 | none         |               138 |               327 | =          | False         |
| R3      |      0 |          6 | HV       | no-transfer    | 0.826553   |    0.829744   | False            |     30 | 0.359878    |    0.05 | none         |               187 |               278 | =          | False         |
| R3      |      0 |          6 | HV       | fixed-transfer | 0.826553   |    0.840848   | False            |     30 | 0.41613     |    0.05 | none         |               192 |               273 | =          | False         |
| R3      |      0 |          6 | IGD      | no-transfer    | 0.101232   |    0.098458   | False            |     30 | 0.41613     |    0.05 | none         |               192 |               273 | =          | False         |
| R3      |      0 |          6 | IGD      | fixed-transfer | 0.101232   |    0.0909938  | False            |     30 | 0.439967    |    0.05 | none         |               194 |               271 | =          | False         |
| R3      |      0 |          6 | GD       | no-transfer    | 0.110433   |    0.110579   | True             |     30 | 0.745655    |    0.05 | none         |               216 |               249 | =          | False         |
| R3      |      0 |          6 | GD       | fixed-transfer | 0.110433   |    0.0926687  | False            |     30 | 0.0698929   |    0.05 | none         |               144 |               321 | =          | False         |
| R3      |      1 |          7 | HV       | no-transfer    | 0.984247   |    0.983133   | True             |     30 | 0.244946    |    0.05 | none         |               290 |               175 | =          | False         |
| R3      |      1 |          7 | HV       | fixed-transfer | 0.984247   |    0.982284   | True             |     30 | 0.0449072   |    0.05 | none         |               330 |               135 | \uparrow   | True          |
| R3      |      1 |          7 | IGD      | no-transfer    | 0.00928804 |    0.00889347 | False            |     30 | 0.569858    |    0.05 | none         |               261 |               204 | =          | False         |
| R3      |      1 |          7 | IGD      | fixed-transfer | 0.00928804 |    0.0106243  | True             |     30 | 0.19093     |    0.05 | none         |               297 |               168 | =          | False         |
| R3      |      1 |          7 | GD       | no-transfer    | 0.00656983 |    0.0430023  | True             |     30 | 0.129353    |    0.05 | none         |               307 |               158 | =          | False         |
| R3      |      1 |          7 | GD       | fixed-transfer | 0.00656983 |    0.015072   | True             |     30 | 0.41613     |    0.05 | none         |               273 |               192 | =          | False         |
| R4      |      0 |          1 | HV       | no-transfer    | 0.931907   |    0.922773   | True             |     30 | 0.10484     |    0.05 | none         |               312 |               153 | =          | False         |
| R4      |      0 |          1 | HV       | fixed-transfer | 0.931907   |    0.935197   | False            |     30 | 0.626346    |    0.05 | none         |               208 |               257 | =          | False         |
| R4      |      0 |          1 | IGD      | no-transfer    | 0.0383432  |    0.0453836  | True             |     30 | 0.0803274   |    0.05 | none         |               318 |               147 | =          | False         |
| R4      |      0 |          1 | IGD      | fixed-transfer | 0.0383432  |    0.0367798  | False            |     30 | 0.903226    |    0.05 | none         |               226 |               239 | =          | False         |
| R4      |      0 |          1 | GD       | no-transfer    | 0.0258672  |    0.0568992  | True             |     30 | 0.00403179  |    0.05 | none         |               369 |                96 | \uparrow   | True          |
| R4      |      0 |          1 | GD       | fixed-transfer | 0.0258672  |    0.0257128  | False            |     30 | 0.700033    |    0.05 | none         |               252 |               213 | =          | False         |
| R4      |      1 |          3 | HV       | no-transfer    | 0.968901   |    0.962144   | True             |     30 | 0.00374443  |    0.05 | none         |               370 |                95 | \uparrow   | True          |
| R4      |      1 |          3 | HV       | fixed-transfer | 0.968901   |    0.96971    | False            |     30 | 0.370741    |    0.05 | none         |               188 |               277 | =          | False         |
| R4      |      1 |          3 | IGD      | no-transfer    | 0.0195102  |    0.0233234  | True             |     30 | 0.00711146  |    0.05 | none         |               361 |               104 | \uparrow   | True          |
| R4      |      1 |          3 | IGD      | fixed-transfer | 0.0195102  |    0.0190571  | False            |     30 | 0.271006    |    0.05 | none         |               178 |               287 | =          | False         |
| R4      |      1 |          3 | GD       | no-transfer    | 0.0143492  |    0.0182586  | True             |     30 | 0.000729602 |    0.05 | none         |               390 |                75 | \uparrow   | True          |
| R4      |      1 |          3 | GD       | fixed-transfer | 0.0143492  |    0.0146322  | True             |     30 | 0.39305     |    0.05 | none         |               190 |               275 | =          | False         |
| R4      |      2 |          5 | HV       | no-transfer    | 0.97506    |    0.973267   | True             |     30 | 0.41613     |    0.05 | none         |               273 |               192 | =          | False         |
| R4      |      2 |          5 | HV       | fixed-transfer | 0.97506    |    0.975982   | False            |     30 | 0.640825    |    0.05 | none         |               209 |               256 | =          | False         |
| R4      |      2 |          5 | IGD      | no-transfer    | 0.0145615  |    0.0155882  | True             |     30 | 0.140283    |    0.05 | none         |               305 |               160 | =          | False         |
| R4      |      2 |          5 | IGD      | fixed-transfer | 0.0145615  |    0.0135538  | False            |     30 | 0.556113    |    0.05 | none         |               203 |               262 | =          | False         |
| R4      |      2 |          5 | GD       | no-transfer    | 0.0139773  |    0.0147268  | True             |     30 | 0.515848    |    0.05 | none         |               265 |               200 | =          | False         |
| R4      |      2 |          5 | GD       | fixed-transfer | 0.0139773  |    0.0105059  | False            |     30 | 0.177193    |    0.05 | none         |               166 |               299 | =          | False         |
| R5      |      0 |          8 | HV       | no-transfer    | 0.963588   |    0.95505    | True             |     30 | 0.0247861   |    0.05 | none         |               341 |               124 | \uparrow   | True          |
| R5      |      0 |          8 | HV       | fixed-transfer | 0.963588   |    0.954698   | True             |     30 | 0.951526    |    0.05 | none         |               236 |               229 | =          | False         |
| R5      |      0 |          8 | IGD      | no-transfer    | 0.0220025  |    0.0288334  | True             |     30 | 0.0309848   |    0.05 | none         |               337 |               128 | \uparrow   | True          |
| R5      |      0 |          8 | IGD      | fixed-transfer | 0.0220025  |    0.0318395  | True             |     30 | 0.967674    |    0.05 | none         |               235 |               230 | =          | False         |
| R5      |      0 |          8 | GD       | no-transfer    | 0.0207697  |    0.0276312  | True             |     30 | 0.0105983   |    0.05 | none         |               355 |               110 | \uparrow   | True          |
| R5      |      0 |          8 | GD       | fixed-transfer | 0.0207697  |    0.0398749  | True             |     30 | 0.515848    |    0.05 | none         |               265 |               200 | =          | False         |
| R5      |      1 |          9 | HV       | no-transfer    | 0.986825   |    0.986918   | False            |     30 | 0.903226    |    0.05 | none         |               226 |               239 | =          | False         |
| R5      |      1 |          9 | HV       | fixed-transfer | 0.986825   |    0.989301   | False            |     30 | 0.00814298  |    0.05 | none         |               106 |               359 | \downarrow | True          |
| R5      |      1 |          9 | IGD      | no-transfer    | 0.00707546 |    0.00729935 | True             |     30 | 0.792159    |    0.05 | none         |               246 |               219 | =          | False         |
| R5      |      1 |          9 | IGD      | fixed-transfer | 0.00707546 |    0.00648987 | False            |     30 | 0.228553    |    0.05 | none         |               173 |               292 | =          | False         |
| R5      |      1 |          9 | GD       | no-transfer    | 0.00606177 |    0.00689724 | True             |     30 | 0.220647    |    0.05 | none         |               293 |               172 | =          | False         |
| R5      |      1 |          9 | GD       | fixed-transfer | 0.00606177 |    0.0298477  | True             |     30 | 0.244946    |    0.05 | none         |               175 |               290 | =          | False         |
| H1      |      0 |          1 | HV       | no-transfer    | 0.9362     |    0.93194    | True             |     30 | 0.612006    |    0.05 | none         |               258 |               207 | =          | False         |
| H1      |      0 |          1 | HV       | fixed-transfer | 0.9362     |    0.940825   | False            |     30 | 0.318396    |    0.05 | none         |               183 |               282 | =          | False         |
| H1      |      0 |          1 | IGD      | no-transfer    | 0.0405505  |    0.0394961  | False            |     30 | 0.887195    |    0.05 | none         |               240 |               225 | =          | False         |
| H1      |      0 |          1 | IGD      | fixed-transfer | 0.0405505  |    0.0335952  | False            |     30 | 0.41613     |    0.05 | none         |               192 |               273 | =          | False         |
| H1      |      0 |          1 | GD       | no-transfer    | 0.0295427  |    0.0277828  | False            |     30 | 0.183969    |    0.05 | none         |               298 |               167 | =          | False         |
| H1      |      0 |          1 | GD       | fixed-transfer | 0.0295427  |    0.0236656  | False            |     30 | 0.308521    |    0.05 | none         |               182 |               283 | =          | False         |
| H1      |      1 |         10 | HV       | no-transfer    | 0.952825   |    0.946935   | True             |     30 | 0.145999    |    0.05 | none         |               304 |               161 | =          | False         |
| H1      |      1 |         10 | HV       | fixed-transfer | 0.952825   |    0.947062   | True             |     30 | 0.145999    |    0.05 | none         |               304 |               161 | =          | False         |
| H1      |      1 |         10 | IGD      | no-transfer    | 0.0251309  |    0.029645   | True             |     30 | 0.228553    |    0.05 | none         |               292 |               173 | =          | False         |
| H1      |      1 |         10 | IGD      | fixed-transfer | 0.0251309  |    0.0294345  | True             |     30 | 0.170598    |    0.05 | none         |               300 |               165 | =          | False         |
| H1      |      1 |         10 | GD       | no-transfer    | 0.0181051  |    0.0246907  | True             |     30 | 0.0404897   |    0.05 | none         |               332 |               133 | \uparrow   | True          |
| H1      |      1 |         10 | GD       | fixed-transfer | 0.0181051  |    0.0573744  | True             |     30 | 0.183969    |    0.05 | none         |               298 |               167 | =          | False         |
| H2      |      0 |          5 | HV       | no-transfer    | 0.976421   |    0.973826   | True             |     30 | 0.183969    |    0.05 | none         |               298 |               167 | =          | False         |
| H2      |      0 |          5 | HV       | fixed-transfer | 0.976421   |    0.977392   | False            |     30 | 0.404495    |    0.05 | none         |               191 |               274 | =          | False         |
| H2      |      0 |          5 | IGD      | no-transfer    | 0.0132197  |    0.0154678  | True             |     30 | 0.151887    |    0.05 | none         |               303 |               162 | =          | False         |
| H2      |      0 |          5 | IGD      | fixed-transfer | 0.0132197  |    0.0127883  | False            |     30 | 0.427955    |    0.05 | none         |               193 |               272 | =          | False         |
| H2      |      0 |          5 | GD       | no-transfer    | 0.00919027 |    0.0283738  | True             |     30 | 0.00577649  |    0.05 | none         |               364 |               101 | \uparrow   | True          |
| H2      |      0 |          5 | GD       | fixed-transfer | 0.00919027 |    0.00940574 | True             |     30 | 0.569858    |    0.05 | none         |               204 |               261 | =          | False         |
| H2      |      1 |          6 | HV       | no-transfer    | 0.834126   |    0.815321   | True             |     30 | 0.228553    |    0.05 | none         |               292 |               173 | =          | False         |
| H2      |      1 |          6 | HV       | fixed-transfer | 0.834126   |    0.833956   | True             |     30 | 0.967674    |    0.05 | none         |               235 |               230 | =          | False         |
| H2      |      1 |          6 | IGD      | no-transfer    | 0.0938166  |    0.107658   | True             |     30 | 0.262122    |    0.05 | none         |               288 |               177 | =          | False         |
| H2      |      1 |          6 | IGD      | fixed-transfer | 0.0938166  |    0.0985729  | True             |     30 | 0.730342    |    0.05 | none         |               250 |               215 | =          | False         |
| H2      |      1 |          6 | GD       | no-transfer    | 0.106872   |    0.123598   | True             |     30 | 0.244946    |    0.05 | none         |               290 |               175 | =          | False         |
| H2      |      1 |          6 | GD       | fixed-transfer | 0.106872   |    0.10745    | True             |     30 | 0.823577    |    0.05 | none         |               244 |               221 | =          | False         |
| H3      |      0 |          1 | HV       | no-transfer    | 0.936856   |    0.929      | True             |     30 | 0.228553    |    0.05 | none         |               292 |               173 | =          | False         |
| H3      |      0 |          1 | HV       | fixed-transfer | 0.936856   |    0.93519    | True             |     30 | 0.839393    |    0.05 | none         |               243 |               222 | =          | False         |
| H3      |      0 |          1 | IGD      | no-transfer    | 0.035387   |    0.0404921  | True             |     30 | 0.228553    |    0.05 | none         |               292 |               173 | =          | False         |
| H3      |      0 |          1 | IGD      | fixed-transfer | 0.035387   |    0.0359679  | True             |     30 | 0.855272    |    0.05 | none         |               242 |               223 | =          | False         |
| H3      |      0 |          1 | GD       | no-transfer    | 0.0251719  |    0.0282914  | True             |     30 | 0.198076    |    0.05 | none         |               296 |               169 | =          | False         |
| H3      |      0 |          1 | GD       | fixed-transfer | 0.0251719  |    0.0321936  | True             |     30 | 0.919297    |    0.05 | none         |               227 |               238 | =          | False         |
| H3      |      1 |          5 | HV       | no-transfer    | 0.974563   |    0.9722     | True             |     30 | 0.100397    |    0.05 | none         |               313 |               152 | =          | False         |
| H3      |      1 |          5 | HV       | fixed-transfer | 0.974563   |    0.975372   | False            |     30 | 0.32847     |    0.05 | none         |               184 |               281 | =          | False         |
| H3      |      1 |          5 | IGD      | no-transfer    | 0.0138096  |    0.0164364  | True             |     30 | 0.177193    |    0.05 | none         |               299 |               166 | =          | False         |
| H3      |      1 |          5 | IGD      | fixed-transfer | 0.0138096  |    0.0134217  | False            |     30 | 0.220647    |    0.05 | none         |               172 |               293 | =          | False         |
| H3      |      1 |          5 | GD       | no-transfer    | 0.0122137  |    0.0155913  | True             |     30 | 0.0497101   |    0.05 | none         |               328 |               137 | \uparrow   | True          |
| H3      |      1 |          5 | GD       | fixed-transfer | 0.0122137  |    0.0120368  | False            |     30 | 0.685047    |    0.05 | none         |               212 |               253 | =          | False         |
| H3      |      2 |          8 | HV       | no-transfer    | 0.959388   |    0.952679   | True             |     30 | 0.0404897   |    0.05 | none         |               332 |               133 | \uparrow   | True          |
| H3      |      2 |          8 | HV       | fixed-transfer | 0.959388   |    0.960333   | False            |     30 | 0.871208    |    0.05 | none         |               224 |               241 | =          | False         |
| H3      |      2 |          8 | IGD      | no-transfer    | 0.0249829  |    0.0311521  | True             |     30 | 0.0635565   |    0.05 | none         |               323 |               142 | =          | False         |
| H3      |      2 |          8 | IGD      | fixed-transfer | 0.0249829  |    0.0238809  | False            |     30 | 0.80783     |    0.05 | none         |               220 |               245 | =          | False         |
| H3      |      2 |          8 | GD       | no-transfer    | 0.0237401  |    0.0345188  | True             |     30 | 0.01546     |    0.05 | none         |               349 |               116 | \uparrow   | True          |
| H3      |      2 |          8 | GD       | fixed-transfer | 0.0237401  |    0.0231926  | False            |     30 | 0.839393    |    0.05 | none         |               222 |               243 | =          | False         |