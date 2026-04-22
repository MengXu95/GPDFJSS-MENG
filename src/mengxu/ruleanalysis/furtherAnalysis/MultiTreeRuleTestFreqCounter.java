package mengxu.ruleanalysis.furtherAnalysis;

import ec.gp.GPNode;
import mengxu.ruleanalysis.NSGAII.NSGAIIMultipleTreeTestResult;
import mengxu.ruleanalysis.NSGAII.ParetoFrontTestResult;
import org.apache.commons.lang3.math.NumberUtils;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.jobshop.Objective;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.MultipleTreeRuleTest;
import yimei.jss.ruleanalysis.MultipleTreeTestResult;
import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.TestResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yimei on 17/10/16.
 */
public class MultiTreeRuleTestFreqCounter extends MultipleTreeRuleTest {

    private String featureSetName;
    private List<GPNode> features;

    public MultiTreeRuleTestFreqCounter(String trainPath,
                                        RuleTypeV2 ruleType,
                                        int numRuns,
                                        String testScenario,
                                        String testSetName,
                                        List<Objective> objectives,
                                        String featureSetName,
                                        int numTrees) {
        super(trainPath, ruleType, numRuns, testScenario, testSetName, objectives, numTrees);
        this.featureSetName = featureSetName;
    }

    public MultiTreeRuleTestFreqCounter(String trainPath,
                                        RuleTypeV2 ruleType,
                                        int numRuns,
                                        String testScenario,
                                        String testSetName,
                                        String featureSetName,
                                        int numTrees) {
        this(trainPath, ruleType, numRuns, testScenario, testSetName,
                new ArrayList<>(), featureSetName, numTrees);
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

    public void writeToCSV() {
        features = featuresFromSetName();

        File targetPath = new File(trainPath + "test");
        if (!targetPath.exists()) {
            targetPath.mkdirs();
        }

        File csvFileForSeqRule = new File(targetPath + "/feature-freq-seqRule.csv");
        File csvFileForRoutRule = new File(targetPath + "/feature-freq-routRule.csv");

        double[][] featureFreqMtxForSeqRule = new double[numRuns][features.size()];
        double[][] featureFreqMtxForRoutRule = new double[numRuns][features.size()];
        double[] numTerminalsForSeqRule = new double[numRuns];
        double[] numTerminalsForRoutRule = new double[numRuns];

        for (int i = 0; i < numRuns; i++) {
            System.out.println("run: " + i);
            File sourceFile = new File(trainPath + "job." + i + ".out.stat");
            //modified by mengxu 2022.08.11
            ParetoFrontTestResult result = NSGAIIMultipleTreeTestResult.readParetoFrontTestResultFromFile(sourceFile, ruleType, numTrees);
            List<GPRule[]> bestParetoFront = result.getBestParetoFrontRules();
            double averageNumTerminalsForSeqRule = 0;
            double averageNumTerminalsForRoutRule = 0;
            double[] featureFreqMtxForSeqRulePareto = new double[features.size()];
            double[] featureFreqMtxForRoutRulePareto = new double[features.size()];
            double[] featureFreqMtxForSeqRulePer = new double[features.size()];
            double[] featureFreqMtxForRoutRulePer = new double[features.size()];
            for(int m=0; m<features.size(); m++){
                featureFreqMtxForSeqRulePareto[m] =0;
                featureFreqMtxForRoutRulePareto[m] =0;
                featureFreqMtxForSeqRulePer[m] =0;
                featureFreqMtxForRoutRulePer[m] =0;
            }

            for(int j=0; j<bestParetoFront.size(); j++){
                GPRule[] bestRules = bestParetoFront.get(j);
                GPRule seqRule = null;
                GPRule routRule = null;
                if (numTrees == 2) {
                    if (bestRules[0].getType() == RuleType.SEQUENCING) {
                        seqRule = bestRules[0];
                        routRule = bestRules[1];
                    } else {
                        seqRule = bestRules[1];
                        routRule = bestRules[0];
                    }
                } else {
                    seqRule = bestRules[0];
                }
                averageNumTerminalsForSeqRule += seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_TERMINALS);
                averageNumTerminalsForRoutRule += routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_TERMINALS);
                countFeatureFreq(featureFreqMtxForSeqRulePer, seqRule.getGPTree().child);
                countFeatureFreq(featureFreqMtxForRoutRulePer, routRule.getGPTree().child);
                for(int m=0; m<features.size(); m++){
                    featureFreqMtxForSeqRulePareto[m] += featureFreqMtxForSeqRulePer[m];
                    featureFreqMtxForRoutRulePareto[m] += featureFreqMtxForRoutRulePer[m];
                }
            }
            numTerminalsForSeqRule[i] = averageNumTerminalsForSeqRule / bestParetoFront.size();
            numTerminalsForRoutRule[i] = averageNumTerminalsForRoutRule / bestParetoFront.size();
            for(int m=0; m<features.size(); m++){
                featureFreqMtxForSeqRule[i][m] = featureFreqMtxForSeqRulePareto[m]/bestParetoFront.size();
                featureFreqMtxForRoutRule[i][m] = featureFreqMtxForRoutRulePareto[m]/bestParetoFront.size();
            }


            //the following is used for single objective
//            TestResult result = MultipleTreeTestResult.readFromFile(sourceFile, ruleType, numTrees);
//            GPRule[] bestRules = (GPRule[]) (result.getBestRules());
//            GPRule seqRule = null;
//            GPRule routRule = null;
//            if (numTrees == 2) {
//                if (bestRules[0].getType() == RuleType.SEQUENCING) {
//                    seqRule = bestRules[0];
//                    routRule = bestRules[1];
//                } else {
//                    seqRule = bestRules[1];
//                    routRule = bestRules[0];
//                }
//            } else {
//                seqRule = bestRules[0];
//            }
//            numTerminalsForSeqRule[i] = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_TERMINALS);
//            numTerminalsForRoutRule[i] = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_TERMINALS);
//            countFeatureFreq(featureFreqMtxForSeqRule[i], seqRule.getGPTree().child);
//            countFeatureFreq(featureFreqMtxForRoutRule[i], routRule.getGPTree().child);
        }

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(csvFileForSeqRule.getAbsoluteFile()));
            writer.write("Run,Feature,Freq");
            writer.newLine();
            for (int i = 0; i < numRuns; i++) {
                for (int j = 0; j < features.size(); j++) {
                    writer.write(i + "," + features.get(j).toString() + "," +
                            featureFreqMtxForSeqRule[i][j]);
                    writer.newLine();
                }
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(csvFileForRoutRule.getAbsoluteFile()));
            writer.write("Run,Feature,Freq");
            writer.newLine();
            for (int i = 0; i < numRuns; i++) {
                for (int j = 0; j < features.size(); j++) {
                    writer.write(i + "," + features.get(j).toString() + "," +
                            featureFreqMtxForRoutRule[i][j]);
                    writer.newLine();
                }
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String dir = "/Users/mengxu/Desktop/PhD_research/2024_PhD_paper/PaperIBMO/";
        String[] algos = new String[]{"IBMO"};
        String[] fsNames = new String[]{"relative-terminals"};
        String[] scenarios = new String[]{"max-flowtime-max-weighted-tardiness-0.85-1.5",
                                          "max-flowtime-max-weighted-tardiness-0.95-1.5",
                                          "max-weighted-flowtime-max-tardiness-0.85-1.5",
                                          "max-weighted-flowtime-max-tardiness-0.95-1.5",
                                          "mean-flowtime-mean-weighted-tardiness-0.85-1.5",
                                          "mean-flowtime-mean-weighted-tardiness-0.95-1.5"};

        String trainPath = "";
        RuleTypeV2 ruleType = RuleTypeV2.get("simple-rule");
        int numRuns = 30;
        String testScenario = "";
        String testSetName = "";
        int numObjectives = 0;
        int numTrees = 2;
        List<Objective> objectives = new ArrayList<>();
        String featureSetName = "";
        for (int i = 0; i < algos.length; i++) {
            for (String scenario : scenarios) {
                trainPath = dir + algos[i] + "/multipletreegp-dynamic/" + "/" + scenario + "/";
                featureSetName = fsNames[0];

                MultiTreeRuleTestFreqCounter ruleTest = new MultiTreeRuleTestFreqCounter(trainPath,
                        ruleType, numRuns, testScenario, testSetName, objectives, featureSetName, numTrees);

                ruleTest.writeToCSV();
            }
        }

    }
}
