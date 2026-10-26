package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.ArrayList;
import java.util.List;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.RevisionArchiveAnalysis;
import yimei.jss.jobshop.Objective;
import yimei.jss.ruleanalysis.MultipleTreeRuleTest;
import yimei.jss.ruleanalysis.RuleTypeV2;

/** Compatibility entry point. Publication results use the held-out revision runner. */
public class FuzzySystemTestEnhanced_V0 extends MultipleTreeRuleTest {
    public List<List<Double>> indPerformance_allRun = new ArrayList<>();
    public List<List<Double>> indComplexity_allRun = new ArrayList<>();
    public List<List<Double>> indSensitivity_allRun = new ArrayList<>();

    public FuzzySystemTestEnhanced_V0(String trainPath, RuleTypeV2 ruleType, int numRuns,
                                     String testScenario, String testSetName, List<Objective> objectives,
                                     String featureSetName, int numTrees) {
        super(trainPath, ruleType, numRuns, testScenario, testSetName, objectives, numTrees);
    }

    public FuzzySystemTestEnhanced_V0(String trainPath, RuleTypeV2 ruleType, int numRuns,
                                     String testScenario, String testSetName,
                                     String featureSetName, int numTrees) {
        this(trainPath, ruleType, numRuns, testScenario, testSetName,
                new ArrayList<>(), featureSetName, numTrees);
    }

    public void HeuristicLibraryAnalysis() {
        RevisionArchiveAnalysis.TrainingDiagnostics diagnostics =
                RevisionArchiveAnalysis.analyze(trainPath, numRuns, Integer.MAX_VALUE, numTrees);
        indPerformance_allRun = diagnostics.storedPerformance;
        indComplexity_allRun = diagnostics.complexity;
        indSensitivity_allRun = diagnostics.sensitivity;
    }

    public void runFuzzyComparison(int runIndex, int numTrials, int topK) {
        if (runIndex < 0 || runIndex >= indPerformance_allRun.size()) {
            throw new IllegalArgumentException("Run archive analysis first and select a valid run index");
        }
        System.out.println("INPUT-METRIC DIAGNOSTIC: no scheduling test is performed by this method.");
        new FuzzySystemComparison(42).runRealDataComparison(indPerformance_allRun,
                indComplexity_allRun, indSensitivity_allRun, runIndex, numTrials, topK);
    }

    public void runFuzzyComparisonAllRuns(String outputDir, int numTrials, int topK) {
        if (numTrials != 1) {
            throw new IllegalArgumentException("Revision evaluation uses independent run-level blocks, "
                    + "not repeated selections counted as independent training runs; use numTrials=1.");
        }
        RevisionArchiveAnalysis.runHeldOut(trainPath, outputDir, numRuns, 500, topK, numTrees);
    }

    public static void main(String[] args) {
        RevisionArchiveAnalysis.launch(args);
    }
}
