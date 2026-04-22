package mengxu.algorithm.MAPElites.CVTMAPElites;

import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import mengxu.algorithm.ensemble.EnsembleRule;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.MultipleTreeTestResult;
import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.TestResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class finalOutputPC {

    public static final long simSeed = 968356;

    protected String trainPath; //the directory of training things
    protected RuleTypeV2 ruleType;
    protected int indexOfRun;
    protected String testScenario;
    protected String testSetName;
    protected List<Objective> objectives; // The objectives to test.
    protected int numTrees;

    public finalOutputPC(String trainPath, RuleTypeV2 ruleType, int indexOfRun,
                         String testScenario, String testSetName,
                         List<Objective> objectives, int numTrees) {
        this.trainPath = trainPath;
        this.ruleType = ruleType;
        this.indexOfRun = indexOfRun;
        this.testScenario = testScenario;
        this.testSetName = testSetName;
        this.objectives = objectives;
        this.numTrees = numTrees;
    }

    public finalOutputPC(String trainPath, RuleTypeV2 ruleType, int indexOfRun,
                         String testScenario, String testSetName, int numTreess) {
        this(trainPath, ruleType, indexOfRun, testScenario, testSetName, new ArrayList<>(), numTreess);
    }

    public double calculateEntropyDiversity(){
        SchedulingSet testSet = generateTestSet();
        File targetPath = new File(trainPath + "test"); //create a folder named "test" in trainPath
        if (!targetPath.exists()) {
            targetPath.mkdirs();
        }

        File csvFile = new File(targetPath + "/" + testSetName + "_allPC_" + indexOfRun + ".csv"); //create a .csv to save the test result

        int i = indexOfRun;
        System.out.println("Run "+ i);
        File sourceFile = new File(trainPath + "job." + i + ".out.stat");  //this file keeps the rule
//        TestResult result = MultipleTreeTestResult.readEnsembleContributionFromFile(sourceFile, ruleType, numTrees);
        TestResult result = MultipleTreeTestResult.readMAPElitesFromFile(sourceFile, ruleType, numTrees);

        long start = System.currentTimeMillis();

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
            writer.write("Run,Generation,Ind,D1,D2,D3,D4,D5,D6,D7,D8,D9,D10,D11,D12,D13,D14,D15,D16,D17,D18,D19,D20," +
                             "D21,D22,D23,D24,D25,D26,D27,D28,D29,D30,D31,D32,D33,D34,D35,D36,D37,D38,D39,D40");
            writer.newLine();

            for (int j = 0; j < result.getGenerationalRules().size() && j<=51; j++) {
                double totalEntropyDiversity = 0;
                ArrayList<Individual> individualsInEnsemble = new ArrayList<>();
                if(result.getGenerationalRules(j) != null){// single individual win
                    int decision = -1;
                    writer.write(i + "," + j + "," + "0");
                    for(int d=0; d<40; d++){
                        writer.write("," + decision);
                    }
                    writer.newLine();
                }
                else{//ensemble win todo: need double check
                    EnsembleRule generationalEnsembleRules = result.getGenerationalEnsembleRules().get(j);
                    Individual[] individuals = new Individual[generationalEnsembleRules.getEnsembleSequencingRule().size()];
                    if (numTrees == 2) {

                        //following is to get the test objective value of each element in ensemble
                        System.out.println("Final output size: " + generationalEnsembleRules.getEnsembleSequencingRule().size());
                        for(int e=0; e<generationalEnsembleRules.getEnsembleSequencingRule().size(); e++){
                            GPRule sequencingRule = generationalEnsembleRules.getEnsembleSequencingRule().get(e);
                            GPRule routingRule = generationalEnsembleRules.getEnsembleRoutingRule().get(e);

                            GPIndividual ind1 = new GPIndividual();
                            ind1.trees = new GPTree[2];
                            ind1.trees[0] = sequencingRule.getGPTree();
                            ind1.trees[1] = routingRule.getGPTree();
                            ind1.fitness = result.getGenerationalEnsembleEachTestFitnessesGen(j).get(e);
                            individuals[e] = ind1;
                            individualsInEnsemble.add(ind1);
                            //todo: during this process, also need to store the ensemble contribution of each element 2023.02.27
                        }
                    }

                    PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];;
                    //dynamic simulation
                    phenoCharacterisation[0] =
                            SequencingPhenoCharacterisation.defaultPhenoCharacterisation();
                    phenoCharacterisation[1] =
                            RoutingPhenoCharacterisation.defaultPhenoCharacterisation();

                    int[][] indsCharListsMultiTree0 = phenotypicForSurrogateV1.muchBetterPhenotypicPopulation(individualsInEnsemble, phenoCharacterisation); //3. calculate the phenotypic characteristic

                    for(int c=0; c<indsCharListsMultiTree0.length; c++){
                        writer.write(i + "," + j + "," + c);
                        for(int d=0; d<40; d++){
                            writer.write("," + indsCharListsMultiTree0[c][d]);
                        }
                        writer.newLine();
                    }

//                    System.out.println("Generation " + j + ": ensemble entropy diversity = " +
//                            entroDvalue);
                }

            }
            double totalEntropyDiversity = 0;
            writer.close();
        }catch (IOException e) {
            e.printStackTrace();
        }


        long finish = System.currentTimeMillis();
        long duration = (finish - start)/1000;
        System.out.println("Duration = " + duration + " s.");

        return 0;
    }


    public SchedulingSet generateTestSet() {
        return SchedulingSet.generateSet(simSeed, testScenario,
                testSetName, objectives, 50);
    }

    public void addObjective(Objective objective) {
        this.objectives.add(objective);
    }

    public void addObjective(String objective) {
        addObjective(Objective.get(objective));
    }

    /**
     * Call this main method with several parameters
     *
     * /Users/dyska/Desktop/Uni/COMP489/GPJSS/grid_results/dynamic/raw/coevolution-fixed/0.85-max-flowtime/
     * simple-rule
     * 30
     * dynamic-job-shop
     * missing-0.85-4.0
     * 2
     * 1
     * max-flowtime
     */
    public static void main(String[] args) {

//        String algo = "MCVTGPpop8Debug";
        String algo = "NichingGP";

        String  trainPath1 = "/home/xume/Desktop/" + algo + "/multipletreegp-dynamic/max-flowtime-0.85-1.5/";
        RuleTypeV2 ruleType1 =  RuleTypeV2.get("simple-rule");
        int indexofRun1 = 0;
        String testScenario1 = "dynamic-job-shop";
        String testSetName1 = "missing-0.85-1.5";
        int numTrees1 = 2;
        int numObjectives1 = 1;
        String obj1 = "max-flowtime";

        finalOutputPC multipletreeruleTest1 = new finalOutputPC(trainPath1, ruleType1, indexofRun1, testScenario1, testSetName1, numTrees1);

        for (int i = 0; i < numObjectives1; i++) {
            multipletreeruleTest1.addObjective(obj1);
//            idx ++;
        }

        double times1 = 0;
        double entropyDiversity1 = 0;
        for(int i=0;i<30;i++){
            multipletreeruleTest1.indexOfRun = i;
            double diversity = multipletreeruleTest1.calculateEntropyDiversity();
            if(diversity != 0){
                entropyDiversity1 += diversity;
                times1 = times1 + 1;
            }
        }
        double averageDiversity1 = entropyDiversity1/times1;
        System.out.println("Average entropy diversity = " + averageDiversity1);


        //
        String  trainPath2 = "/home/xume/Desktop/" + algo + "/multipletreegp-dynamic/max-flowtime-0.95-1.5/";
        RuleTypeV2 ruleType2 =  RuleTypeV2.get("simple-rule");
        int indexofRun2 = 0;
        String testScenario2 = "dynamic-job-shop";
        String testSetName2 = "missing-0.95-1.5";
        int numTrees2 = 2;
        int numObjectives2 = 1;
        String obj2 = "max-flowtime";

        finalOutputPC multipletreeruleTest2 = new finalOutputPC(trainPath2, ruleType2, indexofRun2, testScenario2, testSetName2, numTrees2);

        for (int i = 0; i < numObjectives2; i++) {
            multipletreeruleTest2.addObjective(obj2);
//            idx ++;
        }

        double times2 = 0;
        double entropyDiversity2 = 0;
        for(int i=0;i<30;i++){
            multipletreeruleTest2.indexOfRun = i;
            double diversity = multipletreeruleTest2.calculateEntropyDiversity();
            if(diversity != 0){
                entropyDiversity2 += diversity;
                times2 = times2 + 1;
            }
        }
        double averageDiversity2 = entropyDiversity2/times2;
        System.out.println("Average entropy diversity = " + averageDiversity2);


        //
        String  trainPath3 = "/home/xume/Desktop/" + algo + "/multipletreegp-dynamic/mean-flowtime-0.85-1.5/";
        RuleTypeV2 ruleType3 =  RuleTypeV2.get("simple-rule");
        int indexofRun3 = 0;
        String testScenario3 = "dynamic-job-shop";
        String testSetName3 = "missing-0.85-1.5";
        int numTrees3 = 2;
        int numObjectives3 = 1;
        String obj3 = "mean-flowtime";

        finalOutputPC multipletreeruleTest3 = new finalOutputPC(trainPath3, ruleType3, indexofRun3, testScenario3, testSetName3, numTrees3);

        for (int i = 0; i < numObjectives3; i++) {
            multipletreeruleTest3.addObjective(obj3);
//            idx ++;
        }

        double times3 = 0;
        double entropyDiversity3 = 0;
        for(int i=0;i<30;i++){
            multipletreeruleTest3.indexOfRun = i;
            double diversity = multipletreeruleTest3.calculateEntropyDiversity();
            if(diversity != 0){
                entropyDiversity3 += diversity;
                times3 = times3 + 1;
            }
        }
        double averageDiversity3 = entropyDiversity3/times3;
        System.out.println("Average entropy diversity = " + averageDiversity3);


        //
        String  trainPath4 = "/home/xume/Desktop/" + algo + "/multipletreegp-dynamic/mean-flowtime-0.95-1.5/";
        RuleTypeV2 ruleType4 =  RuleTypeV2.get("simple-rule");
        int indexofRun4 = 0;
        String testScenario4 = "dynamic-job-shop";
        String testSetName4 = "missing-0.95-1.5";
        int numTrees4 = 2;
        int numObjectives4 = 1;
        String obj4 = "mean-flowtime";

        finalOutputPC multipletreeruleTest4 = new finalOutputPC(trainPath4, ruleType4, indexofRun4, testScenario4, testSetName4, numTrees4);

        for (int i = 0; i < numObjectives4; i++) {
            multipletreeruleTest4.addObjective(obj4);
//            idx ++;
        }

        double times4 = 0;
        double entropyDiversity4 = 0;
        for(int i=0;i<30;i++){
            multipletreeruleTest4.indexOfRun = i;
            double diversity = multipletreeruleTest4.calculateEntropyDiversity();
            if(diversity != 0){
                entropyDiversity4 += diversity;
                times4 = times4 + 1;
            }
        }
        double averageDiversity4 = entropyDiversity4/times4;
        System.out.println("Average entropy diversity = " + averageDiversity4);


        //
        String  trainPath5 = "/home/xume/Desktop/" + algo + "/multipletreegp-dynamic/max-weighted-tardiness-0.85-1.5/";
        RuleTypeV2 ruleType5 =  RuleTypeV2.get("simple-rule");
        int indexofRun5 = 0;
        String testScenario5 = "dynamic-job-shop";
        String testSetName5 = "missing-0.85-1.5";
        int numTrees5 = 2;
        int numObjectives5 = 1;
        String obj5 = "max-weighted-tardiness";

        finalOutputPC multipletreeruleTest5 = new finalOutputPC(trainPath5, ruleType5, indexofRun5, testScenario5, testSetName5, numTrees5);

        for (int i = 0; i < numObjectives5; i++) {
            multipletreeruleTest5.addObjective(obj5);
//            idx ++;
        }

        double times5 = 0;
        double entropyDiversity5 = 0;
        for(int i=0;i<30;i++){
            multipletreeruleTest5.indexOfRun = i;
            double diversity = multipletreeruleTest5.calculateEntropyDiversity();
            if(diversity != 0){
                entropyDiversity5 += diversity;
                times5 = times5 + 1;
            }
        }
        double averageDiversity5 = entropyDiversity5/times5;
        System.out.println("Average entropy diversity = " + averageDiversity5);


        //
        String  trainPath6 = "/home/xume/Desktop/" + algo + "/multipletreegp-dynamic/max-weighted-tardiness-0.95-1.5/";
        RuleTypeV2 ruleType6 =  RuleTypeV2.get("simple-rule");
        int indexofRun6 = 0;
        String testScenario6 = "dynamic-job-shop";
        String testSetName6 = "missing-0.95-1.5";
        int numTrees6 = 2;
        int numObjectives6 = 1;
        String obj6 = "max-weighted-tardiness";

        finalOutputPC multipletreeruleTest6 = new finalOutputPC(trainPath6, ruleType6, indexofRun6, testScenario6, testSetName6, numTrees6);

        for (int i = 0; i < numObjectives6; i++) {
            multipletreeruleTest6.addObjective(obj6);
//            idx ++;
        }

        double times6 = 0;
        double entropyDiversity6 = 0;
        for(int i=0;i<30;i++){
            multipletreeruleTest6.indexOfRun = i;
            double diversity = multipletreeruleTest6.calculateEntropyDiversity();
            if(diversity != 0){
                entropyDiversity6 += diversity;
                times6 = times6 + 1;
            }
        }
        double averageDiversity6 = entropyDiversity6/times6;
        System.out.println("Average entropy diversity = " + averageDiversity6);
    }
}
