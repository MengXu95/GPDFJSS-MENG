package mengxu.algorithm.MAPElites.FuzzySystem;

import ec.Fitness;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.RevisionArchiveAnalysis;
import yimei.jss.jobshop.Objective;
import yimei.jss.rule.AbstractRule;
import yimei.jss.ruleanalysis.MultipleTreeRuleTest;
import yimei.jss.ruleanalysis.RuleTypeV2;

public class FuzzySystemTestEnhanced extends MultipleTreeRuleTest {
    public List<List<Double>> indPerformance_allRun = new ArrayList<>();
    public List<List<Double>> indComplexity_allRun = new ArrayList<>();
    public List<List<Double>> indSensitivity_allRun = new ArrayList<>();
    private int poolSize = 500;

    public FuzzySystemTestEnhanced(String trainPath, RuleTypeV2 ruleType, int numRuns,
                                  String testScenario, String testSetName, List<Objective> objectives,
                                  String featureSetName, int numTrees) {
        super(trainPath, ruleType, numRuns, testScenario, testSetName, objectives, numTrees);
    }

    public FuzzySystemTestEnhanced(String trainPath, RuleTypeV2 ruleType, int numRuns,
                                  String testScenario, String testSetName,
                                  String featureSetName, int numTrees) {
        this(trainPath, ruleType, numRuns, testScenario, testSetName,
                new ArrayList<>(), featureSetName, numTrees);
    }

    public void rankRulesByFitness(List<AbstractRule[]> rules, List<Fitness> fitnesses, int count) {
        if (rules == null || fitnesses == null || rules.size() != fitnesses.size()
                || count < 1 || count > rules.size()) {
            throw new IllegalArgumentException("Aligned, non-empty rule/fitness lists and a valid count are required");
        }
        for (Fitness fitness : fitnesses) {
            if (fitness == null || !Double.isFinite(fitness.fitness()) || fitness.fitness() < 0) {
                throw new IllegalArgumentException("Cannot rank invalid fitness values");
            }
        }
        List<Integer> indices = IntStream.range(0, rules.size()).boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> fitnesses.get(i).fitness())
                        .thenComparingInt(Integer::intValue))
                .limit(count).collect(Collectors.toList());
        List<AbstractRule[]> selectedRules = indices.stream().map(rules::get).collect(Collectors.toList());
        List<Fitness> selectedFitness = indices.stream().map(fitnesses::get).collect(Collectors.toList());
        rules.clear();
        rules.addAll(selectedRules);
        fitnesses.clear();
        fitnesses.addAll(selectedFitness);
    }

    /** Stored fitness diagnostics; held-out results are produced by the revision runner. */
    public void HeuristicLibraryAnalysis(int numHeuristics) {
        RevisionArchiveAnalysis.TrainingDiagnostics diagnostics =
                RevisionArchiveAnalysis.analyze(trainPath, numRuns, numHeuristics, numTrees);
        poolSize = numHeuristics;
        indPerformance_allRun = diagnostics.storedPerformance;
        indComplexity_allRun = diagnostics.complexity;
        indSensitivity_allRun = diagnostics.sensitivity;
    }

    @Deprecated
    public void runPaperVerification(int topK) {
        throw new IllegalStateException("Use runPaperVerificationAndSave(outputDir, topK) or "
                + "RevisionExperiment with explicit input/output paths. Stored training fitness is not a held-out test.");
    }

    public void runPaperVerificationAndSave(String outputDir, int topK) {
        RevisionArchiveAnalysis.runHeldOut(trainPath, outputDir, numRuns, poolSize, topK, numTrees);
    }

    public static void main(String[] args) {
        RevisionArchiveAnalysis.launch(args);
    }
}
