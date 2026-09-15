package yimei.jss.gp;

import ec.Evolve;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import mengxu.algorithm.OnlineEvoSpeak.EvoSpeakEvolutionState;
import mengxu.algorithm.OnlineEvoSpeak.EvoSpeakMain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by dyska on 21/05/17.
 */
public class GPMain {

    public static void main(String[] args) {
        List<String> gpRunArgs = new ArrayList<>();
        int maxTests = 29;
        int firstRunId = 22;
        int lastRunId = 22;
        Path resultsDirectory = Paths.get(".");

        //include path to params file
        gpRunArgs.add("-file");

        //ensembleGP -- need to set useLS = true; and warmupSame = true; in Simulation.java
//            gpRunArgs.add("./src/mengxu/algorithm/multicaseEnsemble/ensembleContribution/multipletreegp-dynamicEnsembleContributionCrossover.params");
        //GP with lexicase selection -- need to set useLS = true; and warmupSame = true; in Simulation.java
//            gpRunArgs.add("./src/mengxu/algorithm/lexicaseselection/multipletreegp-dynamicOneInstanceMultiCase.params");
        //NSGPII with semantic diversity and semantic similarity
//            gpRunArgs.add("./src/mengxu/algorithm/multiobjective/phenotypeNSGPII/improvedCompareOne/multipletreegp-dynamic-NSGA2-no-environmental-selection-phenotypeBreeding-improved.params");
        //MOGPD
//            gpRunArgs.add("./src/mengxu/algorithm/multiobjective/multipletreegp-dynamic-MOEADmap.params");
        //NSGPII
//            gpRunArgs.add("./src/mengxu/algorithm/multiobjective/multipletreegp-dynamic-NSGA2-no-environmental-selection.params");
        //GP with diverse partner selection -- need to set useLS = true; and warmupSame = true; in Simulation.java
//            gpRunArgs.add("./src/mengxu/algorithm/diversepartnerselection/multipletreegp-dynamicDPS.params");
        //GP with cluster selection
//            gpRunArgs.add("./src/mengxu/algorithm/clusterselection/multiplecasecluster/multipletreegp-dynamicMultiCaseCluster.params");
        //Pareto set learning GP
//            gpRunArgs.add("./src/mengxu/algorithm/multiobjective/ParetoSetLearning/multipletreegp-dynamic-PSLnichingBasedOnHV.params");
        //Multitask Pareto set learning GP - 2026.5.7
//            gpRunArgs.add("./src/mengxu/algorithm/multiobjective/MPSLGP/multipletreegp-dynamic-MPSLGP.params");
//        gpRunArgs.add("./src/mengxu/algorithm/multiobjective/MPSLGP/multipletreegp-dynamic-MPSLGP-3tasks.params");
        gpRunArgs.add("./src/mengxu/algorithm/OnlineEvoSpeak/multipletreegp-dynamicLLMWarmStart.local.params");
        //GP with multi-case fitness -- need to set useLS = true; and warmupSame = true; in Simulation.java
//        gpRunArgs.add("./src/mengxu/algorithm/averageFitness/multipletreegp-dynamicAverage.params");

                for (int runId = firstRunId; runId <= lastRunId && runId <= maxTests; runId++) {
                        try {
                                runExperiment(gpRunArgs.toArray(new String[0]), runId, resultsDirectory);
                        } catch (Exception error) {
                                throw new IllegalStateException("GP run " + runId + " failed.", error);
                        }
        }
    }

        public static void runExperiment(String[] baseArguments, int runId, Path resultsDirectory) throws Exception {
                if (runId < 0) {
                        throw new IllegalArgumentException("Run IDs must be nonnegative.");
                }
                Path directory = resultsDirectory.toAbsolutePath().normalize();
                Files.createDirectories(directory);
                List<String> runArguments = new ArrayList<>(Arrays.asList(baseArguments));
                runArguments.add("-p");
                runArguments.add("seed.0=" + runId);
                runArguments.add("-p");
                runArguments.add("stat.file=" + directory.resolve("job." + runId + ".out.stat"));
                String[] arguments = runArguments.toArray(new String[0]);
                ParameterDatabase parameters = Evolve.loadParameterDatabase(arguments);
                if (EvoSpeakEvolutionState.class.getName().equals(parameters.getString(new Parameter("state"), null))) {
                        String prefix = "job." + runId;
                        boolean existingResults = Files.exists(directory.resolve(prefix + ".out.stat"))
                                || Files.exists(directory.resolve(prefix + ".time.csv"))
                                || Files.exists(directory.resolve(prefix + ".timeSumGen.csv"));
                        if (existingResults) {
                                System.out.println("OnlineEvoSpeak run " + runId + " already has results in " + directory
                                        + ". Existing files will be kept; this attempt will use a new " + prefix
                                        + "-<unique-suffix> directory under evospeak.output-directory with the same seed.");
                                runArguments.add("-p");
                                runArguments.add("stat.file=$out.stat");
                        }
                        EvoSpeakMain.main(runArguments.toArray(new String[0]));
                } else {
                        GPRun.main(arguments);
                }
        }
}
