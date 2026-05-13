package yimei.jss.gp;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static yimei.jss.FJSSMain.getFileNames;

/**
 * Created by dyska on 21/05/17.
 */
public class GPMain {

    public static void main(String[] args) {
        List<String> gpRunArgs = new ArrayList<>();
        boolean isTest = true;
        int maxTests = 29;

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
        gpRunArgs.add("./src/mengxu/algorithm/multiobjective/MPSLGP/multipletreegp-dynamic-MPSLGP-3tasks.params");
        //GP with multi-case fitness -- need to set useLS = true; and warmupSame = true; in Simulation.java
//        gpRunArgs.add("./src/mengxu/algorithm/averageFitness/multipletreegp-dynamicAverage.params");

        gpRunArgs.add("-p");

        for (int i = 23; i <= 23 && i <= maxTests; ++i) {
            gpRunArgs.add("seed.0="+String.valueOf(i-1));
            gpRunArgs.add("-p");
            gpRunArgs.add("stat.file="+"job."+String.valueOf(i-1)+".out.stat");
            //convert list to array
            GPRun.main(gpRunArgs.toArray(new String[0]));
            //now remove the seed, we will add new value in next loop
            gpRunArgs.remove(gpRunArgs.size()-3);
        }
    }
}
