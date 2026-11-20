# Three-Method Actual-Generation-49 Comparison

Per-scenario min/max from only Full,PSLGP,NSGPII actual49 valid outcomes,all groups/seeds

Generation and test protocol aligned;not total population/evaluation-budget matched;conditioned single heuristics versus a multiheuristic front;TopN versus Best selection differs

## Mean and Sample SD
| Method   | Group       |   Task |   Scenario |   Runs |   HVMean |      HVStd |   IGDMean |     IGDStd |   InputRows |   InvalidRows |
|:---------|:------------|-------:|-----------:|-------:|---------:|-----------:|----------:|-----------:|------------:|--------------:|
| Full     | R1          |      0 |          1 |     30 | 0.96655  | 0.0128014  | 0.0186753 | 0.00840348 |        6000 |             0 |
| Full     | R1          |      1 |          2 |     30 | 0.9585   | 0.00849955 | 0.0237708 | 0.00644637 |        6000 |             0 |
| Full     | R2          |      0 |          3 |     30 | 0.965916 | 0.0117133  | 0.0185488 | 0.00812606 |        6000 |             0 |
| Full     | R2          |      1 |          4 |     30 | 0.786324 | 0.067168   | 0.142551  | 0.0580774  |        6000 |             0 |
| Full     | R3          |      0 |          6 |     30 | 0.828822 | 0.0526994  | 0.120316  | 0.0346901  |        6000 |             0 |
| Full     | R3          |      1 |          7 |     30 | 0.939831 | 0.0325594  | 0.0333191 | 0.0328411  |        6000 |             0 |
| Full     | R4          |      0 |          1 |     30 | 0.96654  | 0.0119646  | 0.0187161 | 0.00794877 |        6000 |             0 |
| Full     | R4          |      1 |          3 |     30 | 0.961711 | 0.0113191  | 0.0203591 | 0.00835149 |        6000 |             0 |
| Full     | R4          |      2 |          5 |     30 | 0.934944 | 0.0170275  | 0.0370865 | 0.0135318  |        6000 |             0 |
| Full     | R5          |      0 |          8 |     30 | 0.812201 | 0.0773019  | 0.116136  | 0.0684367  |        6000 |             0 |
| Full     | R5          |      1 |          9 |     30 | 0.928099 | 0.0159956  | 0.0493002 | 0.0101957  |        6000 |             0 |
| Full     | H1          |      0 |          1 |     30 | 0.968849 | 0.0131814  | 0.0205197 | 0.0156434  |        6000 |             0 |
| Full     | H1          |      1 |         10 |     30 | 0.768152 | 0.0845002  | 0.118051  | 0.0621219  |        6000 |             0 |
| Full     | H2          |      0 |          5 |     30 | 0.93875  | 0.0140924  | 0.0344093 | 0.0103749  |        6000 |             0 |
| Full     | H2          |      1 |          6 |     30 | 0.835705 | 0.0446792  | 0.113523  | 0.0373665  |        6000 |             0 |
| Full     | H3          |      0 |          1 |     30 | 0.969305 | 0.00982079 | 0.0172512 | 0.00511706 |        6000 |             0 |
| Full     | H3          |      1 |          5 |     30 | 0.930333 | 0.0171843  | 0.0395823 | 0.0154228  |        6000 |             0 |
| Full     | H3          |      2 |          8 |     30 | 0.792483 | 0.0713252  | 0.13      | 0.0695878  |        6000 |             0 |
| PSLGP    | Independent |     -1 |          1 |     30 | 0.970286 | 0.0139411  | 0.0169979 | 0.0107944  |        6000 |             0 |
| PSLGP    | Independent |     -1 |          2 |     30 | 0.964427 | 0.0120999  | 0.0193043 | 0.00684455 |        6000 |             0 |
| PSLGP    | Independent |     -1 |          3 |     30 | 0.968686 | 0.00910584 | 0.0165559 | 0.00745048 |        6000 |             0 |
| PSLGP    | Independent |     -1 |          4 |     30 | 0.844952 | 0.0529483  | 0.0995071 | 0.0426649  |        6000 |             0 |
| PSLGP    | Independent |     -1 |          5 |     30 | 0.948573 | 0.0129504  | 0.0276835 | 0.00771551 |        6000 |             2 |
| PSLGP    | Independent |     -1 |          6 |     30 | 0.86248  | 0.0397889  | 0.0938582 | 0.0245451  |        6000 |             0 |
| PSLGP    | Independent |     -1 |          7 |     30 | 0.950591 | 0.0130982  | 0.0239092 | 0.00831783 |        6000 |             0 |
| PSLGP    | Independent |     -1 |          8 |     30 | 0.851064 | 0.0510365  | 0.0808881 | 0.0383007  |        6000 |             0 |
| PSLGP    | Independent |     -1 |          9 |     30 | 0.942529 | 0.0225951  | 0.0420147 | 0.0181701  |        6000 |             0 |
| PSLGP    | Independent |     -1 |         10 |     30 | 0.783505 | 0.0822325  | 0.103654  | 0.0623841  |        6000 |             0 |
| NSGPII   | Independent |     -1 |          1 |     30 | 0.969173 | 0.0072787  | 0.0190048 | 0.00372292 |         295 |             0 |
| NSGPII   | Independent |     -1 |          2 |     30 | 0.963981 | 0.00694242 | 0.0208049 | 0.00401283 |         353 |             0 |
| NSGPII   | Independent |     -1 |          3 |     30 | 0.972667 | 0.00633755 | 0.0165248 | 0.00282372 |         412 |             0 |
| NSGPII   | Independent |     -1 |          4 |     30 | 0.871371 | 0.0246241  | 0.0922799 | 0.0221793  |         445 |             0 |
| NSGPII   | Independent |     -1 |          5 |     30 | 0.966874 | 0.0107594  | 0.0236776 | 0.00597481 |         564 |             0 |
| NSGPII   | Independent |     -1 |          6 |     30 | 0.833879 | 0.0349796  | 0.122489  | 0.0273097  |         326 |             0 |
| NSGPII   | Independent |     -1 |          7 |     30 | 0.94976  | 0.0108067  | 0.0328105 | 0.00701497 |         357 |             0 |
| NSGPII   | Independent |     -1 |          8 |     30 | 0.826473 | 0.0580811  | 0.112979  | 0.0482505  |         378 |             0 |
| NSGPII   | Independent |     -1 |          9 |     30 | 0.95755  | 0.0127424  | 0.0349027 | 0.00792096 |         393 |             0 |
| NSGPII   | Independent |     -1 |         10 |     30 | 0.821507 | 0.0365395  | 0.0998161 | 0.0340883  |         540 |             0 |

## Favourable Full Means
| Baseline   |   HV |   IGD |
|:-----------|-----:|------:|
| NSGPII     |    2 |     5 |
| PSLGP      |    0 |     0 |

## Holm-Significant Paired Comparisons
|                                 |   0 |
|:--------------------------------|----:|
| ('NSGPII', 'HV', 'Full worse')  |   6 |
| ('NSGPII', 'IGD', 'Full worse') |   5 |
| ('PSLGP', 'HV', 'Full worse')   |   3 |
| ('PSLGP', 'IGD', 'Full worse')  |   1 |

## Paired Tests
| Group   |   Scenario | Metric   | Baseline   |   FullMean |   BaselineMean | FullMeanBetter   |   Runs |   MedianPairedGain |      PValue |   HolmPValue | Direction   | Significant   |
|:--------|-----------:|:---------|:-----------|-----------:|---------------:|:-----------------|-------:|-------------------:|------------:|-------------:|:------------|:--------------|
| R1      |          1 | HV       | PSLGP      |  0.96655   |      0.970286  | False            |     30 |       -0.00919686  | 0.157948    |  1           | Full worse  | False         |
| R1      |          1 | HV       | NSGPII     |  0.96655   |      0.969173  | False            |     30 |       -0.00265364  | 0.529105    |  1           | Full worse  | False         |
| R1      |          1 | IGD      | PSLGP      |  0.0186753 |      0.0169979 | False            |     30 |       -0.00469946  | 0.129353    |  1           | Full worse  | False         |
| R1      |          1 | IGD      | NSGPII     |  0.0186753 |      0.0190048 | True             |     30 |        0.00185198  | 0.280087    |  1           | Full better | False         |
| R1      |          2 | HV       | PSLGP      |  0.9585    |      0.964427  | False            |     30 |       -0.00412513  | 0.0549216   |  1           | Full worse  | False         |
| R1      |          2 | HV       | NSGPII     |  0.9585    |      0.963981  | False            |     30 |       -0.0047341   | 0.0105983   |  0.476922    | Full worse  | False         |
| R1      |          2 | IGD      | PSLGP      |  0.0237708 |      0.0193043 | False            |     30 |       -0.00413269  | 0.0136632   |  0.587519    | Full worse  | False         |
| R1      |          2 | IGD      | NSGPII     |  0.0237708 |      0.0208049 | False            |     30 |       -0.00236102  | 0.0840655   |  1           | Full worse  | False         |
| R2      |          3 | HV       | PSLGP      |  0.965916  |      0.968686  | False            |     30 |       -0.000282207 | 0.349211    |  1           | Full worse  | False         |
| R2      |          3 | HV       | NSGPII     |  0.965916  |      0.972667  | False            |     30 |       -0.00666633  | 0.00870546  |  0.409157    | Full worse  | False         |
| R2      |          3 | IGD      | PSLGP      |  0.0185488 |      0.0165559 | False            |     30 |       -0.000379998 | 0.19093     |  1           | Full worse  | False         |
| R2      |          3 | IGD      | NSGPII     |  0.0185488 |      0.0165248 | False            |     30 |        0.000227678 | 0.823577    |  1           | Full better | False         |
| R2      |          4 | HV       | PSLGP      |  0.786324  |      0.844952  | False            |     30 |       -0.0498195   | 0.00103817  |  0.0591757   | Full worse  | False         |
| R2      |          4 | HV       | NSGPII     |  0.786324  |      0.871371  | False            |     30 |       -0.0625946   | 2.6077e-08  |  1.79932e-06 | Full worse  | True          |
| R2      |          4 | IGD      | PSLGP      |  0.142551  |      0.0995071 | False            |     30 |       -0.0267383   | 0.00322299  |  0.170818    | Full worse  | False         |
| R2      |          4 | IGD      | NSGPII     |  0.142551  |      0.0922799 | False            |     30 |       -0.0375936   | 3.45316e-05 |  0.00217549  | Full worse  | True          |
| R3      |          6 | HV       | PSLGP      |  0.828822  |      0.86248   | False            |     30 |       -0.0483609   | 0.0105983   |  0.476922    | Full worse  | False         |
| R3      |          6 | HV       | NSGPII     |  0.828822  |      0.833879  | False            |     30 |       -0.00099417  | 0.715133    |  1           | Full worse  | False         |
| R3      |          6 | IGD      | PSLGP      |  0.120316  |      0.0938582 | False            |     30 |       -0.0354534   | 0.00403179  |  0.20159     | Full worse  | False         |
| R3      |          6 | IGD      | NSGPII     |  0.120316  |      0.122489  | True             |     30 |        0.00950153  | 0.583757    |  1           | Full better | False         |
| R3      |          7 | HV       | PSLGP      |  0.939831  |      0.950591  | False            |     30 |       -0.00854503  | 0.0549216   |  1           | Full worse  | False         |
| R3      |          7 | HV       | NSGPII     |  0.939831  |      0.94976   | False            |     30 |       -0.00357886  | 0.129353    |  1           | Full worse  | False         |
| R3      |          7 | IGD      | PSLGP      |  0.0333191 |      0.0239092 | False            |     30 |       -0.00419269  | 0.0345367   |  1           | Full worse  | False         |
| R3      |          7 | IGD      | NSGPII     |  0.0333191 |      0.0328105 | False            |     30 |        0.00711944  | 0.0426517   |  1           | Full better | False         |
| R4      |          1 | HV       | PSLGP      |  0.96654   |      0.970286  | False            |     30 |       -1.69659e-05 | 0.404495    |  1           | Full worse  | False         |
| R4      |          1 | HV       | NSGPII     |  0.96654   |      0.969173  | False            |     30 |       -0.00289664  | 0.39305     |  1           | Full worse  | False         |
| R4      |          1 | IGD      | PSLGP      |  0.0187161 |      0.0169979 | False            |     30 |        0.000865058 | 0.39305     |  1           | Full better | False         |
| R4      |          1 | IGD      | NSGPII     |  0.0187161 |      0.0190048 | True             |     30 |        0.00125154  | 0.569858    |  1           | Full better | False         |
| R4      |          3 | HV       | PSLGP      |  0.961711  |      0.968686  | False            |     30 |       -0.00721234  | 0.00322299  |  0.170818    | Full worse  | False         |
| R4      |          3 | HV       | NSGPII     |  0.961711  |      0.972667  | False            |     30 |       -0.010979    | 1.21836e-05 |  0.000791932 | Full worse  | True          |
| R4      |          3 | IGD      | PSLGP      |  0.0203591 |      0.0165559 | False            |     30 |       -0.00312611  | 0.0234101   |  0.959816    | Full worse  | False         |
| R4      |          3 | IGD      | NSGPII     |  0.0203591 |      0.0165248 | False            |     30 |       -0.0020559   | 0.00814298  |  0.390863    | Full worse  | False         |
| R4      |          5 | HV       | PSLGP      |  0.934944  |      0.948573  | False            |     30 |       -0.01381     | 0.00158328  |  0.0870806   | Full worse  | False         |
| R4      |          5 | HV       | NSGPII     |  0.934944  |      0.966874  | False            |     30 |       -0.0342825   | 5.58794e-09 |  3.96743e-07 | Full worse  | True          |
| R4      |          5 | IGD      | PSLGP      |  0.0370865 |      0.0276835 | False            |     30 |       -0.00634904  | 0.0046648   |  0.228575    | Full worse  | False         |
| R4      |          5 | IGD      | NSGPII     |  0.0370865 |      0.0236776 | False            |     30 |       -0.00997069  | 1.21836e-05 |  0.000791932 | Full worse  | True          |
| R5      |          8 | HV       | PSLGP      |  0.812201  |      0.851064  | False            |     30 |       -0.0270354   | 0.0497101   |  1           | Full worse  | False         |
| R5      |          8 | HV       | NSGPII     |  0.812201  |      0.826473  | False            |     30 |       -0.00432798  | 0.489846    |  1           | Full worse  | False         |
| R5      |          8 | IGD      | PSLGP      |  0.116136  |      0.0808881 | False            |     30 |       -0.016456    | 0.0635565   |  1           | Full worse  | False         |
| R5      |          8 | IGD      | NSGPII     |  0.116136  |      0.112979  | False            |     30 |        0.009723    | 0.871208    |  1           | Full better | False         |
| R5      |          9 | HV       | PSLGP      |  0.928099  |      0.942529  | False            |     30 |       -0.0158398   | 0.000729602 |  0.0423169   | Full worse  | True          |
| R5      |          9 | HV       | NSGPII     |  0.928099  |      0.95755   | False            |     30 |       -0.0302863   | 3.53903e-08 |  2.40654e-06 | Full worse  | True          |
| R5      |          9 | IGD      | PSLGP      |  0.0493002 |      0.0420147 | False            |     30 |       -0.00887957  | 0.00347516  |  0.177233    | Full worse  | False         |
| R5      |          9 | IGD      | NSGPII     |  0.0493002 |      0.0349027 | False            |     30 |       -0.013159    | 1.41934e-06 |  9.36761e-05 | Full worse  | True          |
| H1      |          1 | HV       | PSLGP      |  0.968849  |      0.970286  | False            |     30 |       -0.00487028  | 0.597808    |  1           | Full worse  | False         |
| H1      |          1 | HV       | NSGPII     |  0.968849  |      0.969173  | False            |     30 |        0.00347094  | 0.983834    |  1           | Full better | False         |
| H1      |          1 | IGD      | PSLGP      |  0.0205197 |      0.0169979 | False            |     30 |       -0.00490346  | 0.109432    |  1           | Full worse  | False         |
| H1      |          1 | IGD      | NSGPII     |  0.0205197 |      0.0190048 | False            |     30 |        0.00266079  | 0.640825    |  1           | Full better | False         |
| H1      |         10 | HV       | PSLGP      |  0.768152  |      0.783505  | False            |     30 |       -0.00702677  | 0.404495    |  1           | Full worse  | False         |
| H1      |         10 | HV       | NSGPII     |  0.768152  |      0.821507  | False            |     30 |       -0.0334678   | 0.00218778  |  0.11814     | Full worse  | False         |
| H1      |         10 | IGD      | PSLGP      |  0.118051  |      0.103654  | False            |     30 |       -0.00846759  | 0.298844    |  1           | Full worse  | False         |
| H1      |         10 | IGD      | NSGPII     |  0.118051  |      0.0998161 | False            |     30 |       -0.00872718  | 0.262122    |  1           | Full worse  | False         |
| H2      |          5 | HV       | PSLGP      |  0.93875   |      0.948573  | False            |     30 |       -0.00580218  | 0.01546     |  0.64932     | Full worse  | False         |
| H2      |          5 | HV       | NSGPII     |  0.93875   |      0.966874  | False            |     30 |       -0.0283565   | 9.31323e-09 |  6.51926e-07 | Full worse  | True          |
| H2      |          5 | IGD      | PSLGP      |  0.0344093 |      0.0276835 | False            |     30 |       -0.0066042   | 0.00870546  |  0.409157    | Full worse  | False         |
| H2      |          5 | IGD      | NSGPII     |  0.0344093 |      0.0236776 | False            |     30 |       -0.0114125   | 8.85651e-05 |  0.00549103  | Full worse  | True          |
| H2      |          6 | HV       | PSLGP      |  0.835705  |      0.86248   | False            |     30 |       -0.026987    | 0.0309848   |  1           | Full worse  | False         |
| H2      |          6 | HV       | NSGPII     |  0.835705  |      0.833879  | True             |     30 |       -0.00107471  | 0.776569    |  1           | Full worse  | False         |
| H2      |          6 | IGD      | PSLGP      |  0.113523  |      0.0938582 | False            |     30 |       -0.0029355   | 0.0840655   |  1           | Full worse  | False         |
| H2      |          6 | IGD      | NSGPII     |  0.113523  |      0.122489  | True             |     30 |        0.00869886  | 0.198076    |  1           | Full better | False         |
| H3      |          1 | HV       | PSLGP      |  0.969305  |      0.970286  | False            |     30 |       -0.00343746  | 0.39305     |  1           | Full worse  | False         |
| H3      |          1 | HV       | NSGPII     |  0.969305  |      0.969173  | True             |     30 |       -4.88533e-05 | 0.887195    |  1           | Full worse  | False         |
| H3      |          1 | IGD      | PSLGP      |  0.0172512 |      0.0169979 | False            |     30 |       -0.00291159  | 0.489846    |  1           | Full worse  | False         |
| H3      |          1 | IGD      | NSGPII     |  0.0172512 |      0.0190048 | True             |     30 |        0.00359931  | 0.151887    |  1           | Full better | False         |
| H3      |          5 | HV       | PSLGP      |  0.930333  |      0.948573  | False            |     30 |       -0.0214086   | 0.000123337 |  0.00740021  | Full worse  | True          |
| H3      |          5 | HV       | NSGPII     |  0.930333  |      0.966874  | False            |     30 |       -0.036443    | 1.86265e-09 |  1.3411e-07  | Full worse  | True          |
| H3      |          5 | IGD      | PSLGP      |  0.0395823 |      0.0276835 | False            |     30 |       -0.00985047  | 0.00145748  |  0.0816187   | Full worse  | False         |
| H3      |          5 | IGD      | NSGPII     |  0.0395823 |      0.0236776 | False            |     30 |       -0.0120245   | 4.71249e-07 |  3.15737e-05 | Full worse  | True          |
| H3      |          8 | HV       | PSLGP      |  0.792483  |      0.851064  | False            |     30 |       -0.0477958   | 0.000209127 |  0.0123385   | Full worse  | True          |
| H3      |          8 | HV       | NSGPII     |  0.792483  |      0.826473  | False            |     30 |       -0.0275222   | 0.0549216   |  1           | Full worse  | False         |
| H3      |          8 | IGD      | PSLGP      |  0.13      |      0.0808881 | False            |     30 |       -0.0332452   | 9.90257e-05 |  0.00604057  | Full worse  | True          |
| H3      |          8 | IGD      | NSGPII     |  0.13      |      0.112979  | False            |     30 |       -0.0139938   | 0.280087    |  1           | Full worse  | False         |