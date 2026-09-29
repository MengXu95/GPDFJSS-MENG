package mengxu.algorithm.multiobjective.MPSLGP;

import ec.Evolve;
import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.DynamicSimulation;

public class MPSLGPRegressionTest {
    private static final String PARAMETER_FILE =
            "src/mengxu/algorithm/multiobjective/MPSLGP/multipletreegp-dynamic-MPSLGP-3tasks.params";

    public static void main(String[] args) throws Exception {
        testAdaptiveTransferProbabilities();
        testTransferModeSelection();
        testUngatedTransferCreditWithoutSimulation();
        testTaskAllocationOffset();
        if (java.util.Arrays.asList(args).contains("--core-only")) {
            System.out.println("MPSLGP core regression tests passed (no simulations).");
            return;
        }
        testTaskInitialization();
        testConfigurationOverrides();
        testSurrogateCacheAlignment();
        testTaskLocalSurrogate();
        testTaskReferenceIsolation();
        if (java.util.Arrays.asList(args).contains("--smoke")) {
            testSmallEvolution(3, true, 0.8);
            testSmallEvolution(2, false, 0.4);
            testSmallEvolution(3, false, 0.0);
            testSmallEvolution(1, false, 0.0);
        }
        System.out.println("MPSLGP regression tests passed.");
    }

    private static ParameterDatabase parameters() {
        return Evolve.loadParameterDatabase(new String[]{"-file", System.getProperty("mpslgp.params", PARAMETER_FILE)});
    }

    private static GPRuleEvolutionStatePSL initializedState(String... overrides) {
        ParameterDatabase parameters = parameters();
        parameters.set(new Parameter("stat"), "ec.Statistics");
        for (int index = 0; index < overrides.length; index += 2) {
            parameters.set(new Parameter(overrides[index]), overrides[index + 1]);
        }
        GPRuleEvolutionStatePSL state = (GPRuleEvolutionStatePSL) Evolve.initialize(parameters, 0);
        state.setup(state, null);
        return state;
    }

    private static void testTaskInitialization() {
        GPRuleEvolutionStatePSL state = initializedState();
        try {
            PSLEvaluator evaluator = (PSLEvaluator) state.evaluator;
            require(state.numTasks == 3, "Expected three configured tasks.");
            require(state.useNoTransferGate, "The no-transfer gate must remain enabled by default.");
            require(state.taskAllocationOffset == 0, "Task allocation must start at task zero by default.");
            require(evaluator.mpslgpProblems != null && evaluator.mpslgpProblems.length == 3,
                    "Each task must have its own evaluation problem.");
            double[] expectedUtilizations = {0.70, 0.75, 0.80};
            for (int task = 0; task < expectedUtilizations.length; task++) {
                MultipleTreeRuleOptimizationProblem problem =
                        (MultipleTreeRuleOptimizationProblem) evaluator.mpslgpProblems[task];
                MultipleRuleEvaluationModel model = (MultipleRuleEvaluationModel) problem.getEvaluationModel();
                DynamicSimulation simulation = (DynamicSimulation) model.getSchedulingSet().getSimulations().get(0);
                requireClose(expectedUtilizations[task], simulation.getUtilLevel(), "Task utilization " + task);
            }
            testTaskSeedRotation(state);
            testTransferProvenance(state);
        } finally {
            state.output.close();
        }
    }

    private static void testConfigurationOverrides() {
        GPRuleEvolutionStatePSL state = initializedState("mpslgp.use-no-transfer-gate", "false",
                "mpslgp.task-allocation-offset", "-4");
        try {
            require(!state.useNoTransferGate, "The parameter must disable the no-transfer gate.");
            require(state.taskAllocationOffset == 2, "Configured offsets must be normalized to the task count.");
        } finally {
            state.output.close();
        }
    }

    private static GPRuleEvolutionStatePSL adaptiveState(double[][][] utilities) throws ReflectiveOperationException {
        GPRuleEvolutionStatePSL state = new GPRuleEvolutionStatePSL();
        state.numTasks = utilities.length;
        state.preferenceRegions = utilities[0][0].length;
        state.adaptiveTransfer = true;
        state.transferStartGeneration = 0;
        state.transferProbability = 0.4;
        state.maxAdaptiveTransferProbability = 0.8;
        state.adaptiveTransferTemperature = 0.4;
        java.lang.reflect.Field field = GPRuleEvolutionStatePSL.class.getDeclaredField("transferUtility");
        field.setAccessible(true);
        field.set(state, utilities);
        return state;
    }

    private static void testAdaptiveTransferProbabilities() throws ReflectiveOperationException {
        for (int tasks : new int[]{2, 3}) {
            double[][][] utilities = new double[tasks][tasks][2];
            double[][] directedUtilities = {{0.0, 1.1, -0.6}, {-1.3, 0.0, -0.2}, {0.4, 0.8, 0.0}};
            for (int receiver = 0; receiver < tasks; receiver++) {
                for (int donor = 0; donor < tasks; donor++) {
                    utilities[receiver][donor][0] = directedUtilities[receiver][donor];
                    utilities[receiver][donor][1] = -0.5 * directedUtilities[receiver][donor] - 0.2;
                }
            }
            GPRuleEvolutionStatePSL state = adaptiveState(utilities);
            require(state.useNoTransferGate, "A newly constructed state must preserve the enabled gate.");
            for (boolean gate : new boolean[]{true, false}) {
                state.useNoTransferGate = gate;
                for (boolean scale : new boolean[]{false, true}) {
                    state.scaleTransferBudgetByDonorCount = scale;
                    double budget = scale ? 0.8 / (tasks - 1) : 0.8;
                    for (double temperature : new double[]{0.4, 1.2}) {
                        state.adaptiveTransferTemperature = temperature;
                        for (double minimum : new double[]{0.0, 0.35}) {
                            state.minAdaptiveTransferProbability = minimum;
                            for (double localUtility : new double[]{-0.3, 0.3}) {
                                state.noTransferUtility = localUtility;
                                for (int receiver = 0; receiver < tasks; receiver++) {
                                    for (int region = 0; region < 2; region++) {
                                        assertTransferProbabilities(state, utilities, receiver, region, budget);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            state.useNoTransferGate = false;
            state.scaleTransferBudgetByDonorCount = false;
            state.maxAdaptiveTransferProbability = 1.0e-15;
            for (int receiver = 0; receiver < tasks; receiver++) {
                assertTransferProbabilities(state, utilities, receiver, 0, 1.0e-15);
            }
            state.maxAdaptiveTransferProbability = 0.8;
            state.adaptiveTransferTemperature = 1.0e-4;
            for (double utility : new double[]{-1000.0, 1000.0}) {
                state.noTransferUtility = -utility;
                for (int donor = 1; donor < tasks; donor++) {
                    utilities[0][donor][0] = utility;
                }
                for (int donor = 1; donor < tasks; donor++) {
                    requireClose(0.8 / (tasks - 1), state.adaptiveTransferProbability(0, donor, 0),
                            "Donor-only softmax must remain stable independently of U0 and utility sign");
                }
                requireClose(0.8, state.adaptiveTransferProbability(0, 0),
                        "Ungated total must equal the budget even when all utilities are negative");
            }
        }
    }

    private static void assertTransferProbabilities(GPRuleEvolutionStatePSL state, double[][][] utilities,
                                                     int receiver, int region, double budget) {
        double[] masses = new double[state.numTasks];
        double donorMass = 0.0;
        for (int donor = 0; donor < state.numTasks; donor++) {
            if (donor != receiver) {
                masses[donor] = Math.exp(utilities[receiver][donor][region] / state.adaptiveTransferTemperature);
                donorMass += masses[donor];
            }
        }
        double denominator = donorMass + (state.useNoTransferGate
                ? Math.exp(state.noTransferUtility / state.adaptiveTransferTemperature) : 0.0);
        double[] expected = new double[state.numTasks];
        double total = 0.0;
        for (int donor = 0; donor < state.numTasks; donor++) {
            if (donor != receiver) {
                expected[donor] = budget * masses[donor] / denominator;
                if (state.useNoTransferGate) {
                    expected[donor] = Math.max(state.minAdaptiveTransferProbability,
                            Math.min(state.maxAdaptiveTransferProbability, expected[donor]));
                }
                total += expected[donor];
            }
        }
        double actualTotal = state.adaptiveTransferProbability(receiver, region);
        requireClose(Math.min(budget, total), actualTotal, "Total directed donor probability");
        double conditionalSum = 0.0;
        for (int donor = 0; donor < state.numTasks; donor++) {
            double probability = state.adaptiveTransferProbability(receiver, donor, region);
            requireClose(expected[donor] * (total > budget ? budget / total : 1.0), probability,
                    "Directed probability for receiver " + receiver + ", donor " + donor + ", region " + region);
            if (donor != receiver) {
                double conditional = probability / actualTotal;
                if (!state.useNoTransferGate) {
                    requireClose(masses[donor] / donorMass, conditional, "Donor conditional softmax");
                }
                state.random = new MersenneTwisterFast[]{new FixedDrawRandom(conditionalSum + conditional / 2.0)};
                require(state.selectAdaptiveDonorTask(receiver, region, 0) == donor,
                        "Donor sampling must use conditional, not unconditional probabilities.");
                conditionalSum += conditional;
            }
        }
        requireClose(1.0, conditionalSum, "Conditional donor probabilities must sum to one");
        if (!state.useNoTransferGate) {
            requireClose(budget, actualTotal, "Disabling the gate must spend the effective transfer budget");
            requireClose(1.0 - budget, 1.0 - actualTotal, "Ungated local probability");
        }
    }

    private static final class FixedDrawRandom extends MersenneTwisterFast {
        private final double draw;

        FixedDrawRandom(double draw) {
            super(1234);
            this.draw = draw;
        }

        @Override
        public double nextDouble() {
            return draw;
        }
    }

    private static void testTransferModeSelection() throws ReflectiveOperationException {
        GPRuleEvolutionStatePSL state = adaptiveState(new double[3][3][1]);
        state.population = populationState(individual(1.0, 0.0, 0), individual(2.0, 0.0, 1),
                individual(3.0, 0.0, 2)).population;
        state.transferStartGeneration = 5;
        state.random = new MersenneTwisterFast[]{new FixedDrawRandom(0.1)};
        PSLParentSelection selection = new PSLParentSelection() {
            @Override
            public int produceMOEAD(int start, int subpopulation, ec.EvolutionState state, int thread) {
                return 0;
            }

            @Override
            public int produceMOEADFromSameTask(int start, int subpopulation, ec.EvolutionState state,
                                              int thread, int requiredTask, int fallbackIndex) {
                return requiredTask;
            }

            @Override
            public int produceMOEADFromDifferentTask(int start, int subpopulation, ec.EvolutionState state,
                                                   int thread, int excludedTask, int fallbackIndex) {
                return 1;
            }
        };
        Individual[] parents = new Individual[2];
        for (boolean gate : new boolean[]{true, false}) {
            state.useNoTransferGate = gate;
            state.adaptiveTransfer = true;
            state.transferProbability = 0.4;
            state.maxAdaptiveTransferProbability = 0.8;
            state.numTasks = 3;
            state.generation = 4;
            require(!state.usePeerTaskTransfer() && !state.useAdaptiveTransfer(),
                    "The gate switch must not bypass transfer-start-generation.");
            selection.produce(2, 2, 0, 0, parents, state, 0);
            require(state.getTaskIndex(parents[1]) == 0, "Warmup must select a local parent.");
            state.generation = 5;
            selection.produce(2, 2, 0, 0, parents, state, 0);
            require(state.getTaskIndex(parents[1]) == 1, "Adaptive transfer must activate at the start generation.");
            state.adaptiveTransfer = false;
            requireClose(0.4, state.adaptiveTransferProbability(0, 0), "Fixed transfer probability");
            selection.produce(2, 2, 0, 0, parents, state, 0);
            require(state.getTaskIndex(parents[1]) == 1, "The switch must not change fixed-transfer selection.");
            state.transferProbability = 0.0;
            selection.produce(2, 2, 0, 0, parents, state, 0);
            require(state.getTaskIndex(parents[1]) == 0, "No-transfer mode must remain local.");
            state.adaptiveTransfer = true;
            state.maxAdaptiveTransferProbability = 0.0;
            require(!state.useAdaptiveTransfer(), "A zero adaptive budget must disable transfer.");
            selection.produce(2, 2, 0, 0, parents, state, 0);
            require(state.getTaskIndex(parents[1]) == 0, "A zero adaptive budget must select a local parent.");
            state.maxAdaptiveTransferProbability = 0.8;
            state.numTasks = 1;
            selection.produce(2, 2, 0, 0, parents, state, 0);
            require(state.getTaskIndex(parents[1]) == 0, "A single task must not transfer.");
        }
    }

    private static void testUngatedTransferCreditWithoutSimulation() throws ReflectiveOperationException {
        GPRuleEvolutionStatePSL state = adaptiveState(new double[3][3][2]);
        state.useNoTransferGate = false;
        state.scaleTransferBudgetByDonorCount = true;
        state.contributionAwareTaskInheritance = true;
        state.donorTaskInheritanceThreshold = 0.65;
        state.adaptiveTransferLearningRate = 0.2;
        state.transferImprovementWeight = 1.0;
        state.transferSurvivalWeight = 0.1;
        state.transferNoImprovementPenalty = 0.1;
        PSLInitializer initializer = new PSLInitializer();
        initializer.numObjectives = 2;
        initializer.weights = new double[][]{{1.0, 0.0}, {1.0, 0.0}, {1.0, 0.0}};
        state.initializer = initializer;
        testTransferProvenance(state);
        require(state.evaluator == null, "Transfer credit must work from existing fitness without a simulator/evaluator.");
        for (GPRuleEvolutionStatePSL.TransferDiagnosticRecord record : state.transferDiagnosticRecords) {
            requireClose(0.4, record.totalTransferProbabilityBeforeUpdate, "Ungated budget before learning");
            requireClose(0.4, record.totalTransferProbabilityAfterUpdate, "Ungated budget after learning");
            requireClose(0.6, record.noTransferProbabilityBeforeUpdate, "Local probability before learning");
            requireClose(0.6, record.noTransferProbabilityAfterUpdate, "Local probability after learning");
        }
    }

    private static void testTaskAllocationOffset() {
        for (int tasks : new int[]{1, 2, 3}) {
            for (int offset : new int[]{0, 1, 2, -1, 4, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                for (int size = 1; size <= 2 * tasks + 1; size++) {
                    Individual[] initial = new Individual[size];
                    for (int index = 0; index < size; index++) {
                        initial[index] = individual(index, 0.0, -1);
                    }
                    GPRuleEvolutionStatePSL state = populationState(initial);
                    state.numTasks = tasks;
                    state.taskAllocationOffset = offset;
                    state.assignTaskIndicesIfNeeded();
                    int[] expected = new int[tasks];
                    for (int index = 0; index < size; index++) {
                        int task = (int) Math.floorMod((long) index + offset, (long) tasks);
                        require(state.getTaskIndex(initial[index]) == task, "Initial round-robin rotation");
                        expected[task]++;
                    }
                    state.assignTaskIndexIfNeeded(initial[0], size + 1);
                    require(state.getTaskIndex(initial[0]) == Math.floorMod(offset, tasks),
                            "An inherited task label must not be reassigned.");
                    Individual[] candidates = new Individual[tasks * (size + 1)];
                    for (int index = 0; index < candidates.length; index++) {
                        candidates[index] = individual(index, 0.0, index % tasks);
                    }
                    state.population.subpops[0].individuals = candidates;
                    state.balanceTaskDistributionForSurvival(0, size);
                    int[] survivors = new int[tasks];
                    int previousScore = -1;
                    for (int index = 0; index < size; index++) {
                        Individual survivor = state.population.subpops[0].individuals[index];
                        survivors[state.getTaskIndex(survivor)]++;
                        int score = (int) ((PSLMultiObjectiveFitness) survivor.fitness).getObjectives()[0];
                        require(score > previousScore, "Balanced truncation must retain ranking order.");
                        previousScore = score;
                    }
                    require(java.util.Arrays.equals(expected, survivors),
                            "Initial allocation and truncation quota remainders must rotate together.");
                }
            }
        }
        GPRuleEvolutionStatePSL shortage = populationState(individual(1.0, 0.0, 0), individual(2.0, 0.0, 0),
                individual(3.0, 0.0, 0), individual(4.0, 0.0, 1), individual(5.0, 0.0, 1),
                individual(6.0, 0.0, 1));
        shortage.taskAllocationOffset = 1;
        shortage.balanceTaskDistributionForSurvival(0, 4);
        int[] counts = new int[3];
        for (int index = 0; index < 4; index++) {
            counts[shortage.getTaskIndex(shortage.population.subpops[0].individuals[index])]++;
        }
        require(java.util.Arrays.equals(new int[]{2, 2, 0}, counts),
                "A missing task's quota must be redistributed to available tasks.");
    }

    private static void testTransferProvenance(GPRuleEvolutionStatePSL state) {
        state.adaptiveTransfer = true;
        state.generation = state.transferStartGeneration;
        GPIndividual primary = individual(10.0, 0.0, 0);
        GPIndividual donor = individual(20.0, 0.0, 1);
        GPIndividual child = individual(8.0, 0.0, 0);
        state.setOffspringTaskIndexByContribution(child, primary, 8, donor, 2);
        PSLMultiObjectiveFitness fitness = (PSLMultiObjectiveFitness) child.fitness;
        require(fitness.getTaskIndex() == 0 && fitness.getTransferEventId() >= 0, "Actual cross-task offspring must be tracked.");
        PSLMultiObjectiveFitness cloneFitness = (PSLMultiObjectiveFitness) ((Individual) child.clone()).fitness;
        require(cloneFitness.getTransferEventId() == fitness.getTransferEventId(), "Sorting clones must retain their transfer identity.");
        GPIndividual failed = individual(10.0, 0.0, 0);
        state.setOffspringTaskIndexByContribution(failed, primary, 10, donor, 0);
        require(((PSLMultiObjectiveFitness) failed.fitness).getTransferEventId() < 0, "Failed crossover must not count as transfer.");
        GPIndividual local = individual(10.0, 0.0, 0);
        state.setOffspringTaskIndexByContribution(local, primary, 8, primary, 2);
        require(((PSLMultiObjectiveFitness) local.fitness).getTransferEventId() < 0, "Same-task crossover must not count as transfer.");
        GPIndividual inherited = individual(10.0, 0.0, 0);
        state.setOffspringTaskIndexByContribution(inherited, primary, 2, donor, 8);
        require(state.getTaskIndex(inherited) == 1, "The dominant contributor must determine the offspring task.");
        GPIndividual otherDonor = individual(20.0, 0.0, 2);
        GPIndividual harmful = individual(14.0, 0.0, 0);
        GPIndividual discarded = individual(5.0, 0.0, 0);
        state.setOffspringTaskIndexByContribution(harmful, primary, 8, otherDonor, 2);
        state.setOffspringTaskIndexByContribution(discarded, primary, 8, otherDonor, 2);
        PSLInitializer initializer = (PSLInitializer) state.initializer;
        initializer.normalisation = 0;
        initializer.weights[state.generation] = new double[]{1.0, 0.0};
        state.population = populationState(primary, donor, otherDonor, (Individual) child.clone(),
                (Individual) child.clone(), harmful, inherited).population;
        for (Individual individual : state.population.subpops[0].individuals) {
            individual.evaluated = true;
            ((PSLMultiObjectiveFitness) individual.fitness).setPSLFitness(9999.0);
        }
        state.generation++;
        state.updateAdaptiveTransferUtilitiesAfterEvaluation();
        int region = state.preferenceRegionForSubproblem(state.generation);
        GPRuleEvolutionStatePSL.TransferDiagnosticRecord helpfulRecord = transferRecord(state, 0, 1, region);
        GPRuleEvolutionStatePSL.TransferDiagnosticRecord harmfulRecord = transferRecord(state, 0, 2, region);
        require(helpfulRecord.transferCount == 1 && helpfulRecord.survivedCount == 1, "Clones must not duplicate transfer credit.");
        require(harmfulRecord.transferCount == 2 && harmfulRecord.survivedCount == 1, "Discarded offspring must reduce donor survival.");
        requireClose(10.0, helpfulRecord.baselineFitness, "Baseline must use same-generation non-transfer evaluations");
        requireClose(8.0, helpfulRecord.meanOffspringFitness, "Credit must use real objectives, not overwritten proxy scores");
        requireClose(0.2, helpfulRecord.improvement, "Helpful donor relative improvement");
        requireClose(-0.2, harmfulRecord.improvement, "Harmful donor improvement per generated offspring");
        requireClose(0.5, harmfulRecord.survivalRate, "Donor-specific offspring survival rate");
        require(helpfulRecord.utilityAfterUpdate > helpfulRecord.utilityBeforeUpdate, "Helpful donor utility must increase.");
        require(harmfulRecord.utilityAfterUpdate < harmfulRecord.utilityBeforeUpdate, "Harmful donor utility must decrease.");
        require(helpfulRecord.probabilityAfterUpdate > harmfulRecord.probabilityAfterUpdate, "Donors with different outcomes need different probabilities.");
        require(transferRecord(state, 1, 0, region).transferCount == 1, "Task inheritance must also redirect credit attribution.");
        double previousUtility = helpfulRecord.utilityAfterUpdate;
        state.updateAdaptiveTransferUtilitiesAfterEvaluation();
        requireClose(previousUtility, transferRecord(state, 0, 1, region).utilityAfterUpdate, "Old offspring must not be credited again");
    }

    private static GPRuleEvolutionStatePSL.TransferDiagnosticRecord transferRecord(
            GPRuleEvolutionStatePSL state, int receivingTask, int donorTask, int region) {
        for (int index = state.transferDiagnosticRecords.size() - 1; index >= 0; index--) {
            GPRuleEvolutionStatePSL.TransferDiagnosticRecord record = state.transferDiagnosticRecords.get(index);
            if (record.receivingTask == receivingTask && record.contributingTask == donorTask && record.preferenceRegion == region) {
                return record;
            }
        }
        throw new AssertionError("Missing transfer diagnostic record.");
    }

    private static void testTaskSeedRotation(GPRuleEvolutionStatePSL state) {
        PSLEvaluator evaluator = (PSLEvaluator) state.evaluator;
        long[] originalSeeds = taskSeeds(evaluator);
        evaluator.rotateEvaluationModels();
        long[] rotatedSeeds = taskSeeds(evaluator);
        long seedIncrement = rotatedSeeds[0] - originalSeeds[0];
        require(seedIncrement > 0, "Task seeds must rotate.");
        for (int task = 0; task < originalSeeds.length; task++) {
            require(rotatedSeeds[task] - originalSeeds[task] == seedIncrement,
                    "Each rotating task must advance exactly once.");
        }
        state.parameters.set(new Parameter("eval.problem.1.eval-model.rotate-sim-seed"), "false");
        MultipleTreeRuleOptimizationProblem fixedProblem =
                (MultipleTreeRuleOptimizationProblem) evaluator.mpslgpProblems[1];
        fixedProblem.getEvaluationModel().setup(state, new Parameter("eval.problem.1.eval-model"));
        originalSeeds = taskSeeds(evaluator);
        evaluator.rotateEvaluationModels();
        rotatedSeeds = taskSeeds(evaluator);
        require(rotatedSeeds[1] == originalSeeds[1], "A task with rotation disabled must retain its seed.");
        require(rotatedSeeds[0] - originalSeeds[0] == seedIncrement, "Other tasks must keep rotating.");
        require(rotatedSeeds[2] - originalSeeds[2] == seedIncrement, "The final task must keep rotating.");
    }

    private static long[] taskSeeds(PSLEvaluator evaluator) {
        long[] seeds = new long[evaluator.mpslgpProblems.length];
        for (int task = 0; task < seeds.length; task++) {
            MultipleTreeRuleOptimizationProblem problem =
                    (MultipleTreeRuleOptimizationProblem) evaluator.mpslgpProblems[task];
            MultipleRuleEvaluationModel model = (MultipleRuleEvaluationModel) problem.getEvaluationModel();
            seeds[task] = ((DynamicSimulation) model.getSchedulingSet().getSimulations().get(0)).getSeed();
        }
        return seeds;
    }

    private static void testSurrogateCacheAlignment() {
        GPRuleEvolutionStatePSL state = populationState(
                individual(10.0, 1.0, 0), individual(20.0, 0.0, 1));
        double[][][][] phenotypes = {{{{11.0}}, {{22.0}}}};
        double[][][][] fitnesses = {{{{10.0, 10.0, 10.0, 1.0}}, {{20.0, 20.0, 20.0, 0.0}}}};
        KNNsurrogateClearingPSLEvaluatorbasedonHV evaluator = new KNNsurrogateClearingPSLEvaluatorbasedonHV();
        evaluator.cacheSurrogatePopulation(state, phenotypes, fitnesses);
        multitreeClearingFirstMPSLGP.sortPopBasedOnTheSameBetterThan(
                state.population.subpops[0].individuals, phenotypes[0]);
        requireClose(22.0, phenotypes[0][0][0][0], "The test must reorder the population");
        requireClose(11.0, evaluator.lastGenPhenotypicOfIntermedidatePopPSL[0][0][0][0], "Cached phenotype");
        requireClose(10.0, evaluator.lastGenFitnessOfIntermedidatePopPSL[0][0][0][2], "Matching cached fitness");
        require(evaluator.lastGenTaskIndices[0][0] == 0, "Cached task must match its phenotype.");
        phenotypes[0][1][0][0] = -1.0;
        fitnesses[0][0][0][2] = -1.0;
        requireClose(11.0, evaluator.lastGenPhenotypicOfIntermedidatePopPSL[0][0][0][0], "Independent phenotype snapshot");
        requireClose(10.0, evaluator.lastGenFitnessOfIntermedidatePopPSL[0][0][0][2], "Independent fitness snapshot");
    }

        private static void testTaskLocalSurrogate() {
        GPRuleEvolutionStatePSL state = populationState(individual(10.0, 1.0, 0), individual(1.0, 0.0, 1));
        PSLInitializer initializer = new PSLInitializer();
        initializer.numObjectives = 2;
        state.initializer = initializer;
        double[][] preferences = {{0.5, 0.5}, {0.25, 0.75}};
        double[][][][] phenotypes = {{{{10.0}, {20.0}}, {{20.0}, {10.0}}}};
        double[][][][] estimates = multitreeClearingFirstMPSLGP.fitnessPopulationPreferenceFixedDecisionsConsiderAverageRank(
            state, preferences, phenotypes);
        requireClose(10.0, estimates[0][0][1][0], "Cross-task exact match must not supply objective values");
        requireClose(1.0, estimates[0][1][1][0], "Each task must retain its own scale");
        require(multitreeClearingFirstMPSLGP.nearestNeighbor(new double[]{10.0}, phenotypes[0], new int[]{0, 1}, 2) == -1,
            "Missing same-task samples must not fall back to another task.");
        Individual[] duplicates = {individual(1.0, 0.0, 0), individual(1.0, 0.0, 1), individual(2.0, 1.0, 0)};
        int cleared = multitreeClearingFirstMPSLGP.clearTaskDuplicates(duplicates, new double[][][]{{{1.0}}, {{1.0}}, {{1.0}}}, 0.0, 1);
        require(cleared == 1, "Only the within-task duplicate should be cleared.");
        require(!((ClearingPSLMultiObjectiveFitness) duplicates[1].fitness).isCleared(), "Other-task duplicate must survive.");
        }

        private static void testTaskReferenceIsolation() {
            GPRuleEvolutionStatePSL state = populationState(individual(10.0, 0.0, 0), individual(1000.0, 0.0, 1));
            PSLInitializer initializer = new PSLInitializer();
            initializer.numObjectives = 2;
            initializer.normalisation = 1;
            initializer.weights = new double[][]{{0.5, 0.5}};
            initializer.taskSchedulingSetObjectiveLowerBoundMtx = new RealMatrix[]{
                    new Array2DRowRealMatrix(new double[][]{{10.0}, {20.0}}),
                    new Array2DRowRealMatrix(new double[][]{{1000.0}, {2000.0}}),
                    new Array2DRowRealMatrix(new double[][]{{1.0}, {1.0}})};
            state.initializer = initializer;
            KNNsurrogateClearingPSLEvaluatorbasedonHV evaluator = new KNNsurrogateClearingPSLEvaluatorbasedonHV();
            state.evaluator = evaluator;
            evaluator.assignFrontRanks(state.population.subpops[0]);
            evaluator.updateTaskReferencePoints(state);
            for (Individual individual : state.population.subpops[0].individuals) {
                ClearingPSLMultiObjectiveFitness fitness = (ClearingPSLMultiObjectiveFitness) individual.fitness;
                requireClose(0.0, fitness.rank, "Ranks must be task-local even for very different objective scales");
                fitness.calculatePSLFitness(state);
                requireClose(0.75, fitness.getPSLFitness(), "Task-specific baseline normalization");
            }
            requireClose(10.0, initializer.forTask(0).minObjectives[0], "Task 0 reference scale");
            requireClose(1000.0, initializer.forTask(1).minObjectives[0], "Task 1 reference scale");
            double score = multitreeClearingFirstMPSLGP.calculatePSLFitnessWithPreferencesAndObjectives(
                    state, new double[]{0.25, 0.75}, new double[]{1000.0, 1000.0}, 1);
            requireClose(0.625, score, "Surrogate preferences must use the receiving task normalization");
            double[][][][] estimates = {{{{10.0, 10.0, 0.75, 0.0}}, {{1000.0, 1000.0, 0.75, 0.0}}}};
            double[][] hypervolumes = multitreeClearingFirstMPSLGP.HVPopulationPreferenceDecisions(state, estimates);
            double[][] repeatedHypervolumes = multitreeClearingFirstMPSLGP.HVPopulationPreferenceDecisions(state, estimates);
            for (int individual = 0; individual < 2; individual++) {
                requireClose(0.25, hypervolumes[0][individual], "Task-local HV range");
                requireClose(hypervolumes[0][individual], repeatedHypervolumes[0][individual], "HV must not mutate reference ranges");
            }
            requireClose(10.0, initializer.forTask(0).minObjectives[0], "HV must preserve the minimum");
            requireClose(10.0, initializer.forTask(0).maxObjectives[0], "HV must preserve the maximum");
            for (int strategy = 0; strategy <= 1; strategy++) {
                state.HVEstimateStrategy = strategy;
                double[][] distances = multitreeClearingFirstMPSLGP.GDPopulationPreferenceDecisions(state, estimates);
                double[][] inverseDistances = multitreeClearingFirstMPSLGP.IGDPopulationPreferenceDecisions(state, estimates);
                for (int individual = 0; individual < 2; individual++) {
                    requireClose(0.0, distances[0][individual], "GD must use the same-task reference front");
                    requireClose(0.0, inverseDistances[0][individual], "IGD must use the same-task reference front");
                }
            }
        }

        private static void testSmallEvolution(int tasks, boolean adaptive, double transferProbability) {
            ParameterDatabase parameters = parameters();
            parameters.set(new Parameter("stat"), "ec.Statistics");
            parameters.set(new Parameter("pop.subpop.0.size"), "24");
            parameters.set(new Parameter("breed.elite.0"), "3");
            parameters.set(new Parameter("num-preferences-for-HV"), "11");
            parameters.set(new Parameter("mpslgp.num-tasks"), String.valueOf(tasks));
            parameters.set(new Parameter("mpslgp.adaptive-transfer"), String.valueOf(adaptive));
            parameters.set(new Parameter("mpslgp.transfer-probability"), String.valueOf(transferProbability));
            parameters.set(new Parameter("mpslgp.transfer-start-generation"), "0");
            parameters.set(new Parameter("mpslgp.max-transfer-probability"), "1.0");
            parameters.set(new Parameter("mpslgp.scale-transfer-budget-by-donor-count"), "false");
            for (int task = -1; task < tasks; task++) {
                String base = task < 0 ? "eval.problem" : "eval.problem." + task;
                parameters.set(new Parameter(base + ".eval-model.sim-models.0.num-jobs"), "200");
                parameters.set(new Parameter(base + ".eval-model.sim-models.0.warmup-jobs"), "20");
            }
            if (tasks > 1) {
                parameters.set(new Parameter("eval.problem.1.eval-model.objectives.0"), "max-flowtime");
                parameters.set(new Parameter("eval.problem.1.eval-model.objectives.1"), "max-weighted-tardiness");
            }
            GPRuleEvolutionStatePSL state = (GPRuleEvolutionStatePSL) Evolve.initialize(parameters, 0);
            try {
                state.startFresh();
                long[] seeds = tasks > 1 ? taskSeeds((PSLEvaluator) state.evaluator) : null;
                for (int generation = 0; generation < 3; generation++) {
                    require(state.evolve() == ec.EvolutionState.R_NOTDONE, "Smoke run must stay inside its training budget.");
                    KNNsurrogateClearingPSLEvaluatorbasedonHV evaluator = (KNNsurrogateClearingPSLEvaluatorbasedonHV) state.evaluator;
                    for (int tree = 0; tree < 2; tree++) {
                        yimei.jss.rule.operation.evolved.GPRule reference =
                                (yimei.jss.rule.operation.evolved.GPRule) state.phenoCharacterisation[tree].getReferenceRule();
                        require(reference.getGPTree() == ((GPIndividual) evaluator.kneePointIndividual).trees[tree],
                                "Diversity statistics must not replace the cached surrogate reference rule.");
                    }
                    int[] counts = new int[tasks];
                    int[] validCounts = new int[tasks];
                    int[] labels = state.objectiveTaskIndex.get(generation);
                    require(labels.length == 24, "Preselection must restore the configured population size.");
                    for (int index = 0; index < labels.length; index++) {
                        require(labels[index] >= 0 && labels[index] < tasks, "Every evaluated individual needs a valid task.");
                        counts[labels[index]]++;
                        double objective0 = state.objective0.get(generation)[index];
                        double objective1 = state.objective1.get(generation)[index];
                        require(!Double.isNaN(objective0) && !Double.isNaN(objective1), "Objectives must not be NaN.");
                        if (Double.isFinite(objective0) && Double.isFinite(objective1)
                                && objective0 < Double.MAX_VALUE && objective1 < Double.MAX_VALUE) {
                            validCounts[labels[index]]++;
                        }
                    }
                    for (int task = 0; task < tasks; task++) {
                        require(counts[task] > 0, "Every task must retain evaluated individuals.");
                        require(validCounts[task] > 0, "Every task must retain valid scheduling rules.");
                    }
                }
                if (tasks > 1) {
                    long[] finalSeeds = taskSeeds((PSLEvaluator) state.evaluator);
                    for (int task = 0; task < tasks; task++) {
                        require(finalSeeds[task] > seeds[task], "The evolution loop must rotate every task.");
                    }
                }
                int recordedTransfers = 0;
                for (GPRuleEvolutionStatePSL.TransferDiagnosticRecord record : state.transferDiagnosticRecords) {
                    recordedTransfers += record.transferCount;
                    require(record.survivedCount <= record.transferCount, "Survival cannot exceed generated offspring.");
                    require(Double.isFinite(record.utilityAfterUpdate), "Adaptive utilities must remain finite.");
                    requireClose(1.0, record.totalTransferProbabilityAfterUpdate + record.noTransferProbabilityAfterUpdate,
                            "Transfer and no-transfer probabilities must sum to one");
                }
                require(!adaptive || recordedTransfers > 0, "Adaptive smoke run must exercise actual cross-task offspring.");
                require(adaptive || recordedTransfers == 0, "Fixed and no-transfer modes must not update adaptive credit.");
                System.out.println("SMOKE PASSED: tasks=" + tasks + ", adaptive=" + adaptive
                        + ", transfer=" + transferProbability + ", recordedOffspring=" + recordedTransfers);
            } finally {
                state.output.close();
            }
        }

        private static GPIndividual individual(double score, double rank, int taskIndex) {
        GPIndividual individual = new GPIndividual();
        individual.trees = new GPTree[0];
        ClearingPSLMultiObjectiveFitness fitness = new ClearingPSLMultiObjectiveFitness();
        fitness.objectives = new double[]{score, score};
        fitness.maximize = new boolean[2];
        fitness.rank = rank;
        fitness.compareCriteria = "HV";
        fitness.useMultiCriteriaSelection = true;
        fitness.setPSLFitness(score);
        fitness.setTaskIndex(taskIndex);
        individual.fitness = fitness;
        return individual;
    }

    private static GPRuleEvolutionStatePSL populationState(Individual... individuals) {
        GPRuleEvolutionStatePSL state = new GPRuleEvolutionStatePSL();
        state.numTasks = 3;
        state.population = new Population();
        state.population.subpops = new Subpopulation[]{new Subpopulation()};
        state.population.subpops[0].individuals = individuals;
        return state;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) < 1.0e-9,
                message + ": expected " + expected + ", got " + actual);
    }
}