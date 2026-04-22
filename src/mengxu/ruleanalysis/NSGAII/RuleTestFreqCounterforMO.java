package mengxu.ruleanalysis.NSGAII;

import ec.gp.GPNode;
import org.apache.commons.lang3.math.NumberUtils;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.jobshop.Objective;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.RuleTest;
import yimei.jss.ruleanalysis.RuleTypeV2;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yimei on 17/10/16.
 */
public class RuleTestFreqCounterforMO extends RuleTest {

    private String featureSetName;
    private List<GPNode> features;

    private int numTrees;

    public RuleTestFreqCounterforMO(String trainPath,
                                    RuleTypeV2 ruleType,
                                    int numRuns,
                                    String testScenario,
                                    String testSetName,
                                    List<Objective> objectives,
                                    String featureSetName,
                                    int numPopulations) {
        super(trainPath, ruleType, numRuns, testScenario, testSetName, objectives, numPopulations);
        this.featureSetName = featureSetName;
    }

    public RuleTestFreqCounterforMO(String trainPath,
                                    RuleTypeV2 ruleType,
                                    int numRuns,
                                    String testScenario,
                                    String testSetName,
                                    String featureSetName,
                                    int numPopulations) {
        this(trainPath, ruleType, numRuns, testScenario, testSetName,
                new ArrayList<>(), featureSetName, numPopulations);
    }

    public List<GPNode> featuresFromSetName() {
        List<GPNode> features = new ArrayList<>();

        switch (featureSetName) {
            case "basic-terminals":
                for (JobShopAttribute a : JobShopAttribute.basicAttributes()) {
                    features.add(new AttributeGPNode(a));
                }
                break;
            case "relative-terminals":
                for (JobShopAttribute a : JobShopAttribute.relativeAttributes()) {
                    features.add(new AttributeGPNode(a));
                }
                break;
            default:
                break;
        }

        return features;
    }

    public void countFeatureFreq(double[] featureFreq, GPNode tree) {
        if (tree.depth() == 1) {
            if (NumberUtils.isNumber(tree.toString()))
                return;

            int idx = -1;
            for (int i = 0; i < features.size(); i++) {
                if (features.get(i).toString().equals(tree.toString())) {
                    idx = i;
                    break;
                }
            }

            featureFreq[idx] ++;
        }
        else {
            for (GPNode child : tree.children)
                countFeatureFreq(featureFreq, child);
        }
    }

    @Override
    public void writeToCSV() {
        features = featuresFromSetName();
        numTrees = 2;

        File targetPath = new File(trainPath + "test");
        if (!targetPath.exists()) {
            targetPath.mkdirs();
        }

        File csvFile = new File(targetPath + "/feature-freq-Sequencing-Routing.csv");

//        ArrayList<double[][]> seqFeatureFreqMtxALL = new ArrayList<>();
//        ArrayList<double[]> seqNumTerminalsALL = new ArrayList<>();
//
//        ArrayList<double[][]> routFeatureFreqMtxALL = new ArrayList<>();
//        ArrayList<double[]> routNumTerminalsALL = new ArrayList<>();

        double[][] seqFeatureFreqMtx = new double[numRuns][features.size()];
        double[] seqNumTerminals = new double[numRuns];

        double[][] routFeatureFreqMtx = new double[numRuns][features.size()];
        double[] routNumTerminals = new double[numRuns];

        for (int i = 0; i < numRuns; i++) {
            System.out.println("run: " + i);
            File sourceFile = new File(trainPath + "job." + i + ".out.stat");

            //modified by mengxu 2022.08.11
            ParetoFrontTestResult result = NSGAIIMultipleTreeTestResult.readParetoFrontTestResultFromFile(sourceFile, ruleType, numTrees);

            List<GPRule[]> generationalParetoFrontRules = result.getGenerationalParetoFrontRulesFromFinalPop();

            for(int p = 0; p < generationalParetoFrontRules.size(); p++){
                GPRule[] generationalRules = generationalParetoFrontRules.get(p);
                if (numTrees == 2) {
                    GPRule seqRule = generationalRules[0];
                    GPRule routRule = generationalRules[1];
                    seqNumTerminals[i] = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_TERMINALS);
                    countFeatureFreq(seqFeatureFreqMtx[i], seqRule.getGPTree().child);
//                    seqFeatureFreqMtxALL.add(seqFeatureFreqMtx);
//                    seqNumTerminalsALL.add(seqNumTerminals);
                    routNumTerminals[i] = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_TERMINALS);
                    countFeatureFreq(routFeatureFreqMtx[i], routRule.getGPTree().child);
//                    routFeatureFreqMtxALL.add(routFeatureFreqMtx);
//                    routNumTerminalsALL.add(routNumTerminals);
                }
            }
            for (int j = 0; j < features.size(); j++) {
                seqFeatureFreqMtx[i][j] = seqFeatureFreqMtx[i][j]/generationalParetoFrontRules.size();
                routFeatureFreqMtx[i][j] = routFeatureFreqMtx[i][j]/generationalParetoFrontRules.size();
            }

        }

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
            writer.write("Run,Feature,seqFreq,routFreq");
            writer.newLine();
            for (int i = 0; i < numRuns; i++) {
                for (int j = 0; j < features.size(); j++) {
                    writer.write(i + "," + features.get(j).toString() + "," +
                            seqFeatureFreqMtx[i][j]+ "," +
                            routFeatureFreqMtx[i][j]);
                    writer.newLine();
                }
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String dir = "/run/media/xume/TOSHIBA EXT/PhD/xume/HeterogeneousSimulation/PaperMOGPD-manual-GECCO/";
        String[] algos = new String[]{"MOGPD-manual-pop100-gen250-T40-Tour4"};
        String[] fsNames = new String[]{"relative-terminals"};
        String[] scenarios = new String[]{"max-flowtime-mean-weighted-tardiness-0.85-1.5", "max-flowtime-mean-weighted-tardiness-0.95-1.5",
                "max-tardiness-mean-tardiness-0.85-1.5", "max-tardiness-mean-tardiness-0.95-1.5",
                "mean-flowtime-max-tardiness-0.85-1.5", "mean-flowtime-max-tardiness-0.95-1.5"};

        String trainPath = "";
        RuleTypeV2 ruleType = RuleTypeV2.get("simple-rule");
        int numRuns = 30;
        String testScenario = "";
        String testSetName = "";
        int numObjectives = 0;
        int numPopulations = 1;
        List<Objective> objectives = new ArrayList<>();
        String featureSetName = "";
        for (int i = 0; i < algos.length; i++) {
            for (String scenario : scenarios) {
                trainPath = dir + algos[i] + "/multipletreegp-dynamic/" + scenario + "/";
                featureSetName = fsNames[i];

                RuleTestFreqCounterforMO ruleTest = new RuleTestFreqCounterforMO(trainPath,
                        ruleType, numRuns, testScenario, testSetName, objectives, featureSetName, numPopulations);

                ruleTest.writeToCSV();
            }
        }



    }
}
