package mengxu.ruleanalysis.forEnsemble;


import mengxu.algorithm.ensemble.EnsembleRule;
import yimei.jss.ruleanalysis.MultipleTreeTestResult;
import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.TestResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UniqueMemberInEnsemble {

    protected String trainPath; //the directory of training things
    protected RuleTypeV2 ruleType;
    protected int numRuns;
    protected int numTrees;
    protected List<List<Integer>> uniqueForAllGen;

    public UniqueMemberInEnsemble(String trainPath, RuleTypeV2 ruleType, int numRuns,
                                  int numTrees){
        this.trainPath = trainPath;
        this.ruleType = ruleType;
        this.numRuns = numRuns;
        this.numTrees = numTrees;
        this.uniqueForAllGen = new ArrayList<>();
    }

    public void calculateUniqueInEnsemble(){

        for (int i = 0; i < numRuns; i++) {
            List<Integer> uniqueforGen = new ArrayList<>();
            File ensembleSourceFile = new File(trainPath + "job." + i + ".ensemble.stat");

            TestResult ensembleResult = MultipleTreeTestResult.readEnsembleFromFile(ensembleSourceFile, ruleType, numTrees);

            for (int j = 0; j < ensembleResult.getGenerationalEnsembleRules().size(); j++) {
                EnsembleRule generationalEnsembleRules = ensembleResult.getGenerationalEnsembleRules().get(j);

                int unique = generationalEnsembleRules.getUniqueMembers();

                uniqueforGen.add(unique);

                System.out.println("Generation " + j + ": unique = " + unique);
            }
            uniqueForAllGen.add(uniqueforGen);
        }

        writeUniqueNumberToFile();
    }

    //2021.2.15 modified by mengxu
    public void writeUniqueNumberToFile(){
        File diversities = new File(trainPath+"unique.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
            writer.write("Run, Gen, Number");
            writer.newLine();
            for (int r = 0; r < uniqueForAllGen.size(); r++) {
                List<Integer> uniqueForGen = uniqueForAllGen.get(r);
                for(int ind=0; ind<uniqueForGen.size(); ind++){
                    writer.write(r + "," + ind + "," + uniqueForGen.get(ind));
                    writer.newLine();
                }
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Call this main method with several parameters
     *
     * /Users/dyska/Desktop/Uni/COMP489/GPJSS/grid_results/dynamic/raw/coevolution-fixed/0.85-max-flowtime/
     * simple-rule
     * 30
     * 2
     */
    public static void main(String[] args) {
        int idx = 0;
        String trainPath = args[idx];
        idx ++;
        RuleTypeV2 ruleType = RuleTypeV2.get(args[idx]);
        idx ++;
        int numRuns = Integer.valueOf(args[idx]); //30
        idx ++;
        int numTrees = Integer.valueOf(args[idx]); //2
        idx ++;

        //RuleTest ruleTest = new RuleTest(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees);
        //modified by fzhang  24.5.2018  use multipleTreeRuleTest
        UniqueMemberInEnsemble uniqueMemberInEnsemble = new UniqueMemberInEnsemble(trainPath, ruleType, numRuns, numTrees);

        uniqueMemberInEnsemble.calculateUniqueInEnsemble();

    }
}
