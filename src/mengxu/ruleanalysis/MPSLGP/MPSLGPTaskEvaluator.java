package mengxu.ruleanalysis.MPSLGP;

import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer;
import mengxu.ruleanalysis.MPSLGP.ParetoSetLearningMultipleTreeResultFileReader.SelectedTaskHeuristic;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.UniqueTerminalsGatherer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Reuses PSLGP's test-set and preference generators and its preference-aware evaluator. */
public final class MPSLGPTaskEvaluator
        extends mengxu.ruleanalysis.ParetoSetLearning.ParetoSetLearningMultiTreeRuleTestMeng {
    public static final String CSV_HEADER = "Run,ParetoFromGeneration,SeqRuleSize,SeqRuleUniqueTerminals,"
            + "RoutRuleSize,RoutRuleUniqueTerminals,Obj,TrainFitness0,TestFitness0,TrainFitness1,TestFitness1";
    public static final String PREFERENCE_HEADER = "PreferenceIndex,Weight0,Weight1,TestFitness0,TestFitness1";
    private final int replications;

    public MPSLGPTaskEvaluator(String testSetName, List<Objective> objectives, int preferences, int replications) {
        super("", RuleTypeV2.get("simple-rule"), 1, "dynamic-job-shop", testSetName,
                new ArrayList<>(objectives), 2, preferences);
        if (objectives.size() != 2 || objectives.contains(null) || preferences < 2 || replications < 1) {
            throw new IllegalArgumentException("Testing requires two objectives, at least two preferences and positive replications.");
        }
        if (!testSetName.matches("missing-0\\.[0-9]+-1\\.5")) {
            throw new IllegalArgumentException("Unsupported PSLGP paper test set: " + testSetName);
        }
        this.replications = replications;
        generatePreferences(preferences);
    }

    @Override
    public SchedulingSet generateTestSet() {
        if (replications == 30) {
            return super.generateTestSet();
        }
        return SchedulingSet.generateSet(simSeed, testScenario, testSetName, objectives, replications);
    }

    public double[][] preferenceVectors() {
        double[][] copy = new double[preferences.length][];
        for (int i = 0; i < preferences.length; i++) { copy[i] = preferences[i].clone(); }
        return copy;
    }

    static void prepareFinalRuleTestSet(SchedulingSet set) {
        // Legacy PSLGP tests earlier generations first; its final-rule pass starts after reset.
        set.getSimulations().forEach(yimei.jss.simulation.Simulation::reset);
    }

    static GPRuleEvolutionStatePSL evaluationState(double[][] preferences) {
        GPRuleEvolutionStatePSL state = new GPRuleEvolutionStatePSL();
        PSLInitializer initializer = new PSLInitializer();
        initializer.weights = preferences;
        state.initializer = initializer;
        state.terminalNormalisation = 2;
        return state;
    }

    static double[] evaluatePreference(GPRule sequencing, GPRule routing, SchedulingSet set,
                                      List<Objective> objectives, GPRuleEvolutionStatePSL state, int index)
            throws IOException {
        state.evaluatePreferenceIndex = index;
        MultiObjectiveFitness fitness = new MultiObjectiveFitness();
        sequencing.calcFitnessWithPreference(fitness, state, set, routing, objectives, index);
        double[] result = fitness.getObjectives();
        if (result.length != 2) { throw new IOException("Expected two test objectives."); }
        for (double value : result) {
            if (!Double.isFinite(value) || value < 0) {
                throw new IOException("Invalid test result at preference " + index + ": " + value);
            }
        }
        return result.clone();
    }

    public void evaluate(SelectedTaskHeuristic selected, int run, Path output, Consumer<String> progress)
            throws IOException {
        SchedulingSet set = generateTestSet();
        if (set == null) { throw new IOException("PSLGP test-set generation failed."); }
        prepareFinalRuleTestSet(set);
        GPRule sequencing = selected.getSequencingRule();
        GPRule routing = selected.getRoutingRule();
        int seqSize = sequencing.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
        int routSize = routing.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
        int seqTerminals = sequencing.getGPTree().child.numNodes(new UniqueTerminalsGatherer());
        int routTerminals = routing.getGPTree().child.numNodes(new UniqueTerminalsGatherer());
        double[] train = selected.getTrainingObjectives();
        GPRuleEvolutionStatePSL state = evaluationState(preferences);
        try (BufferedWriter legacy = Files.newBufferedWriter(output.resolve(csvName(testSetName, run)), StandardCharsets.UTF_8);
             BufferedWriter detail = Files.newBufferedWriter(output.resolve("preference-results.csv"), StandardCharsets.UTF_8)) {
            legacy.write(CSV_HEADER + "\n");
            detail.write(PREFERENCE_HEADER + "\n");
            for (int i = 0; i < preferences.length; i++) {
                if (Thread.currentThread().isInterrupted()) { throw new IOException("Test interrupted."); }
                if (i % 10 == 0 || i == preferences.length - 1) {
                    progress.accept("Preference " + (i + 1) + "/" + preferences.length
                            + " weights=" + preferences[i][0] + "," + preferences[i][1]);
                }
                double[] result = evaluatePreference(sequencing, routing, set, objectives, state, i);
                legacy.write(run + "," + selected.getGeneration() + "," + seqSize + "," + seqTerminals
                        + "," + routSize + "," + routTerminals + ",2," + train[0] + "," + result[0]
                        + "," + train[1] + "," + result[1] + "\n");
                detail.write(i + "," + preferences[i][0] + "," + preferences[i][1]
                        + "," + result[0] + "," + result[1] + "\n");
                legacy.flush();
                detail.flush();
            }
        }
    }

    public static String csvName(String setName, int run) {
        return setName + "-" + run + "-ParetoFront.csv";
    }
}
