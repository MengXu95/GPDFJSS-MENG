package mengxu.ruleanalysis.MultitaskLearning;

import ec.EvolutionState;
import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.multitaskHeuristicLearning.GPRuleEvolutionStateMultiTaskLearningSurrogate;
import mengxu.ruleanalysis.NSGAII.NSGAIIMultipleTreeTestResult;
import mengxu.ruleanalysis.NSGAII.ParetoFrontTestResult;
import org.apache.commons.lang3.math.NumberUtils;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yimei on 17/10/16.
 */
public class MultiTreeRuleTestFreqCounterMultiTask extends MultipleTreeRuleTest {

    private String featureSetName;
    private List<GPNode> features;

    public MultiTreeRuleTestFreqCounterMultiTask(String trainPath,
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

    public MultiTreeRuleTestFreqCounterMultiTask(String trainPath,
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
            case "relative-terminals-multi-task":
                for (JobShopAttribute a : JobShopAttribute.relativeForMultiTaskAttributes()) {
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

        for(int indexOfRun=0; indexOfRun<this.numRuns; indexOfRun++){
            System.out.println("Run "+ indexOfRun);
            File sourceFile = new File(trainPath + "job." + indexOfRun + ".out.stat");  //this file keeps the rule
            TestResult result = MultipleTreeTestResult.readFromFile(sourceFile, ruleType, numTrees);
            int bestIndex = result.getGenerationalRules().size()-1;
            GPRule[] bestRules = (GPRule[])result.getGenerationalRules().get(bestIndex);
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
            numTerminalsForSeqRule[indexOfRun] = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_TERMINALS);
            numTerminalsForRoutRule[indexOfRun] = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_TERMINALS);
            countFeatureFreq(featureFreqMtxForSeqRule[indexOfRun], seqRule.getGPTree().child);
            countFeatureFreq(featureFreqMtxForRoutRule[indexOfRun], routRule.getGPTree().child);
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
        String dir = "/Users/mengxu/Desktop/NTU工作/MultiTaskLearning8/";
        String[] algos = new String[]{"T3CGP-Sw"};
        String[] fsNames = new String[]{"relative-terminals-multi-task"};
        String[] scenarios = new String[]{"max-flowtime-0.75-0.85-0.95-1.5",
                "mean-flowtime-0.75-0.85-0.95-1.5",
                "mean-weighted-flowtime-0.75-0.85-0.95-1.5",
                "max-tardiness-0.75-0.85-0.95-1.5",
                "mean-tardiness-0.75-0.85-0.95-1.5",
                "mean-weighted-tardiness-0.75-0.85-0.95-1.5",
                "max-flowtime-0.75-0.80-0.85-0.90-0.95-1.5",
                "mean-flowtime-0.75-0.80-0.85-0.90-0.95-1.5",
                "mean-weighted-flowtime-0.75-0.80-0.85-0.90-0.95-1.5",
                "max-tardiness-0.75-0.80-0.85-0.90-0.95-1.5",
                "mean-tardiness-0.75-0.80-0.85-0.90-0.95-1.5",
                "mean-weighted-tardiness-0.75-0.80-0.85-0.90-0.95-1.5"};

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

                MultiTreeRuleTestFreqCounterMultiTask ruleTest = new MultiTreeRuleTestFreqCounterMultiTask(trainPath,
                        ruleType, numRuns, testScenario, testSetName, objectives, featureSetName, numTrees);

                ruleTest.writeToCSV();
            }
        }

    }
}
