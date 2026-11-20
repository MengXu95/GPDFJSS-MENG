# Three-Method Training Time

Sum of logged per-generation elapsed seconds for 0..49, divided by60; independently validated against timeSumGen.csv

MPSLGP joint run counted once;independent task times summed for each group and seed;not parallel batch completion time

Different hardware/concurrency and operators/population allocations;not a controlled speedup or equal-quality comparison;timing windows omit some setup/finalization

| Method   | Group   |   Runs |   Tasks |   MeanMinutes |   SDMinutes |
|:---------|:--------|-------:|--------:|--------------:|------------:|
| NSGPII   | R1      |     30 |       2 |      135.967  |    13.2624  |
| PSLGP    | R1      |     30 |       2 |      399.131  |    30.8406  |
| MPSLGP   | R1      |     30 |       2 |      145.614  |   118.085   |
| NSGPII   | R2      |     30 |       2 |      130.143  |    10.463   |
| PSLGP    | R2      |     30 |       2 |      398.909  |    33.826   |
| MPSLGP   | R2      |     30 |       2 |       88.5383 |    13.5427  |
| NSGPII   | R3      |     30 |       2 |      157.002  |    18.5633  |
| PSLGP    | R3      |     30 |       2 |      390.607  |    36.1355  |
| MPSLGP   | R3      |     30 |       2 |       87.3761 |     6.51799 |
| NSGPII   | R4      |     30 |       3 |      203.979  |    16.8246  |
| PSLGP    | R4      |     30 |       3 |      583.723  |    39.8216  |
| MPSLGP   | R4      |     30 |       3 |       80.0056 |     4.72826 |
| NSGPII   | R5      |     30 |       2 |      128.229  |    10.8622  |
| PSLGP    | R5      |     30 |       2 |      393.915  |    29.3417  |
| MPSLGP   | R5      |     30 |       2 |       82.5294 |     5.03848 |
| NSGPII   | H1      |     30 |       2 |      139.783  |    14.3934  |
| PSLGP    | H1      |     30 |       2 |      390.104  |    31.5896  |
| MPSLGP   | H1      |     30 |       2 |       83.3194 |     5.59637 |
| NSGPII   | H2      |     30 |       2 |      147.421  |    18.882   |
| PSLGP    | H2      |     30 |       2 |      392.528  |    27.7334  |
| MPSLGP   | H2      |     30 |       2 |       83.5583 |     5.66751 |
| NSGPII   | H3      |     30 |       3 |      205.298  |    19.9737  |
| PSLGP    | H3      |     30 |       3 |      587.314  |    43.1872  |
| MPSLGP   | H3      |     30 |       3 |       80.7572 |     4.84607 |