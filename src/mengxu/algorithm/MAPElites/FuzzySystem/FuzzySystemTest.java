package mengxu.algorithm.MAPElites.FuzzySystem;

import ec.Fitness;
import ec.Individual;
import ec.gp.GPNode;
import org.apache.commons.lang3.math.NumberUtils;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.jobshop.Objective;
import yimei.jss.rule.AbstractRule;
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
import mengxu.algorithm.MAPElites.FuzzySystem.revision.RevisionArchiveAnalysis;

/**
 * Created by yimei on 17/10/16.
 */
public class FuzzySystemTest extends MultipleTreeRuleTest {

    private String featureSetName;
    private List<GPNode> features;
    public List<List<Double>> indPerformance_allRun;
    public List<List<Double>> indComplexity_allRun;
    public List<List<Double>> indSensitivity_allRun;

    public FuzzySystemTest(String trainPath,
                           RuleTypeV2 ruleType,
                           int numRuns,
                           String testScenario,
                           String testSetName,
                           List<Objective> objectives,
                           String featureSetName,
                           int numTrees) {
        super(trainPath, ruleType, numRuns, testScenario, testSetName, objectives, numTrees);
        this.featureSetName = featureSetName;
        this.indPerformance_allRun = new ArrayList<>();
        this.indComplexity_allRun = new ArrayList<>();
        this.indSensitivity_allRun = new ArrayList<>();
    }

    public FuzzySystemTest(String trainPath,
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

    public void HeuristicLibraryAnalysis() {
        features = featuresFromSetName();
        RevisionArchiveAnalysis.TrainingDiagnostics diagnostics =
                RevisionArchiveAnalysis.analyze(trainPath, numRuns, Integer.MAX_VALUE, numTrees);
        indPerformance_allRun = diagnostics.storedPerformance;
        indComplexity_allRun = diagnostics.complexity;
        indSensitivity_allRun = diagnostics.sensitivity;
    }

    public static void main(String[] args) {
        RevisionArchiveAnalysis.launch(args);
    }
}
