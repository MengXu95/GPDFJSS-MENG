package mengxu.algorithm.multiobjective.MPSLGP;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Checkpoint;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.multiobjective.utils.Archive;
import mengxu.cluster.CustomerPoint;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import yimei.jss.simulation.DecisionSituation;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.state.SystemState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author yimei
 *
 */

public class GPRuleEvolutionStatePSL extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();
	List<DiversityRecord> storeTaskGenDiversities = new ArrayList<>();
	public List<List<CustomerPoint>> cluster = new ArrayList<>();

	public List<Integer> parentIndex = new ArrayList<>();
	public List<Integer> parentTaskIndex = new ArrayList<>();
	public List<List<Integer>> allGenerationParentIndex = new ArrayList<>();
	public List<List<Integer>> allGenerationParentTaskIndex = new ArrayList<>();
	public double[] minFitnessGen;
	public double[] maxFitnessGen;

	//add 2021.11.3
	public List<double[]> objective0 = new ArrayList<>();
	public List<double[]> objective1 = new ArrayList<>();
	public List<int[]> objectiveTaskIndex = new ArrayList<>();
	public List<TopMetricRecord> topMetricRecords = new ArrayList<>();

	public Archive externalArchive = new Archive(); //add by mengxu 2022.09.02
	public PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];

	public int printGap;

	public int terminalNormalisation;

	public Map<String,Double> maxTerminals = new HashMap<String,Double>();

	public int evaluatePreferenceIndex;
	public int evaluateOrder;

	public boolean doNichingClear;

	public int HVEstimateStrategy;

	public int outputTopN;

	public boolean topNFromParetoFront;

	public boolean preferenceTerminalHighPro;

	public boolean useBroodRecombination;

	public int numTasks;

	public double transferProbability;

	public double taskInheritanceProbability;

	public int transferStartGeneration;

	public boolean adaptiveTransfer;

	public int preferenceRegions;

	public double adaptiveTransferLearningRate;

	public double adaptiveTransferTemperature;

	public double minAdaptiveTransferProbability;

	public double maxAdaptiveTransferProbability;

	public double transferImprovementWeight;

	public double transferSurvivalWeight;

	public double transferNoImprovementPenalty;

	private double[][][] transferUtility;

	private int[][][] pendingTransferCounts;

	private double[][] bestTaskRegionFitness;

	public List<TransferDiagnosticRecord> transferDiagnosticRecords = new ArrayList<>();

	private static class DiversityRecord {
		String scope;
		int taskIndex;
		int individualCount;
		double generation;
		double genotypeDiversity;
		double phenotypeDiversity;
		double entropyDiversity;
		double pseudoIsomorphsDiversity;
		double editOneDiversity;
		double editTwoDiversity;
		double pcDiversity;
		double parentSelectionDiversity;

		DiversityRecord(String scope, int taskIndex, int individualCount, double generation, double genotypeDiversity,
						 double phenotypeDiversity, double entropyDiversity, double pseudoIsomorphsDiversity,
						 double editOneDiversity, double editTwoDiversity, double pcDiversity,
						 double parentSelectionDiversity) {
			this.scope = scope;
			this.taskIndex = taskIndex;
			this.individualCount = individualCount;
			this.generation = generation;
			this.genotypeDiversity = genotypeDiversity;
			this.phenotypeDiversity = phenotypeDiversity;
			this.entropyDiversity = entropyDiversity;
			this.pseudoIsomorphsDiversity = pseudoIsomorphsDiversity;
			this.editOneDiversity = editOneDiversity;
			this.editTwoDiversity = editTwoDiversity;
			this.pcDiversity = pcDiversity;
			this.parentSelectionDiversity = parentSelectionDiversity;
		}
	}

	private static class TopMetricRecord {
		double generation;
		int taskIndex;
		int rank;
		int individualIndex;
		String metricName;
		double metricValue;
		double pslFitness;
		double preferenceDiversity;

		TopMetricRecord(double generation, int taskIndex, int rank, int individualIndex, String metricName,
						double metricValue, double pslFitness, double preferenceDiversity) {
			this.generation = generation;
			this.taskIndex = taskIndex;
			this.rank = rank;
			this.individualIndex = individualIndex;
			this.metricName = metricName;
			this.metricValue = metricValue;
			this.pslFitness = pslFitness;
			this.preferenceDiversity = preferenceDiversity;
		}
	}

	private static class TransferDiagnosticRecord {
		double generation;
		int receivingTask;
		int contributingTask;
		int preferenceRegion;
		int transferCount;
		double contribution;
		double probabilityBeforeUpdate;
		double probabilityAfterUpdate;
		double utilityBeforeUpdate;
		double utilityAfterUpdate;
		double previousBest;
		double currentBest;
		double improvement;
		double improvementComponent;
		double taskPopulationShare;
		double survivalComponent;
		double negativeTransferPenalty;
		double learningRate;
		double temperature;
		double maxTransferProbability;

		TransferDiagnosticRecord(double generation, int receivingTask, int contributingTask, int preferenceRegion,
							 int transferCount, double contribution, double probabilityBeforeUpdate,
							 double probabilityAfterUpdate, double utilityBeforeUpdate, double utilityAfterUpdate,
							 double previousBest, double currentBest, double improvement, double improvementComponent,
							 double taskPopulationShare, double survivalComponent, double negativeTransferPenalty,
							 double learningRate, double temperature, double maxTransferProbability) {
			this.generation = generation;
			this.receivingTask = receivingTask;
			this.contributingTask = contributingTask;
			this.preferenceRegion = preferenceRegion;
			this.transferCount = transferCount;
			this.contribution = contribution;
			this.probabilityBeforeUpdate = probabilityBeforeUpdate;
			this.probabilityAfterUpdate = probabilityAfterUpdate;
			this.utilityBeforeUpdate = utilityBeforeUpdate;
			this.utilityAfterUpdate = utilityAfterUpdate;
			this.previousBest = previousBest;
			this.currentBest = currentBest;
			this.improvement = improvement;
			this.improvementComponent = improvementComponent;
			this.taskPopulationShare = taskPopulationShare;
			this.survivalComponent = survivalComponent;
			this.negativeTransferPenalty = negativeTransferPenalty;
			this.learningRate = learningRate;
			this.temperature = temperature;
			this.maxTransferProbability = maxTransferProbability;
		}
	}


	@Override
	public void setup(EvolutionState state, Parameter base) {

		super.setup(this, base);

		Parameter printGapParam = new Parameter("print-gap"); //add by mengxu 2023.01.24
		printGap = state.parameters.getInt(printGapParam, null); //add by mengxu 2023.01.24

		Parameter doNichingClearParam = new Parameter("do-niching-clear"); //add by mengxu 2023.01.24
		this.doNichingClear = state.parameters.getBoolean(doNichingClearParam, true); //add by mengxu 2023.01.24

		Parameter outputTopNParam = new Parameter("output-top-N"); //add by mengxu 2023.01.24
		this.outputTopN = state.parameters.getInt(outputTopNParam, null,1); //add by mengxu 2023.01.24

		Parameter topNFromParetoFrontParam = new Parameter("topN-from-pareto-front"); //add by mengxu 2023.01.24
		this.topNFromParetoFront = state.parameters.getBoolean(topNFromParetoFrontParam, true); //add by mengxu 2023.01.24

		//add by mengxu 2024.4.18
		Parameter terminalNormalisationParam = new Parameter("terminal-normalisation"); //add by mengxu 2023.01.24
		this.terminalNormalisation = state.parameters.getIntWithDefault(terminalNormalisationParam, null,2); //add by mengxu 2023.01.24

		//add by mengxu 2024.5.24
		Parameter HVEstimateStrategyParam = new Parameter("HV-estimated-strategy"); //add by mengxu 2023.01.24
		this.HVEstimateStrategy = state.parameters.getIntWithDefault(HVEstimateStrategyParam, null,1); //add by mengxu 2023.01.24

		Parameter preferenceTerminalHighProParam = new Parameter("preference-terminal-high-pro"); //add by mengxu 2023.01.24
		preferenceTerminalHighPro = state.parameters.getBoolean(preferenceTerminalHighProParam, false); //add by mengxu 2023.01.24

		Parameter useBroodRecombinationParam = new Parameter("use-brood-recombination"); //add by mengxu 2023.01.24
		this.useBroodRecombination = state.parameters.getBoolean(useBroodRecombinationParam, null,false); //add by mengxu 2024.09.26

		Parameter numTasksParam = new Parameter("mpslgp.num-tasks");
		this.numTasks = state.parameters.getIntWithDefault(numTasksParam, null, 1);

		Parameter transferProbabilityParam = new Parameter("mpslgp.transfer-probability");
		this.transferProbability = state.parameters.getDoubleWithDefault(transferProbabilityParam, null, 0.0);

		Parameter taskInheritanceProbabilityParam = new Parameter("mpslgp.task-inheritance-probability");
		this.taskInheritanceProbability = state.parameters.getDoubleWithDefault(taskInheritanceProbabilityParam, null, 0.5);

		Parameter transferStartGenerationParam = new Parameter("mpslgp.transfer-start-generation");
		this.transferStartGeneration = state.parameters.getIntWithDefault(transferStartGenerationParam, null, 1);

		Parameter adaptiveTransferParam = new Parameter("mpslgp.adaptive-transfer");
		this.adaptiveTransfer = state.parameters.getBoolean(adaptiveTransferParam, null, false);

		Parameter preferenceRegionsParam = new Parameter("mpslgp.preference-regions");
		this.preferenceRegions = Math.max(1, state.parameters.getIntWithDefault(preferenceRegionsParam, null, 5));

		Parameter adaptiveTransferLearningRateParam = new Parameter("mpslgp.adaptive-transfer-learning-rate");
		this.adaptiveTransferLearningRate = state.parameters.getDoubleWithDefault(adaptiveTransferLearningRateParam, null, 0.2);

		Parameter adaptiveTransferTemperatureParam = new Parameter("mpslgp.adaptive-transfer-temperature");
		this.adaptiveTransferTemperature = Math.max(1.0e-12,
				state.parameters.getDoubleWithDefault(adaptiveTransferTemperatureParam, null, 0.2));

		Parameter minAdaptiveTransferProbabilityParam = new Parameter("mpslgp.min-transfer-probability");
		this.minAdaptiveTransferProbability = state.parameters.getDoubleWithDefault(minAdaptiveTransferProbabilityParam, null, 0.0);

		Parameter maxAdaptiveTransferProbabilityParam = new Parameter("mpslgp.max-transfer-probability");
		this.maxAdaptiveTransferProbability = state.parameters.getDoubleWithDefault(maxAdaptiveTransferProbabilityParam, null,
				this.transferProbability);

		Parameter transferImprovementWeightParam = new Parameter("mpslgp.transfer-improvement-weight");
		this.transferImprovementWeight = state.parameters.getDoubleWithDefault(transferImprovementWeightParam, null, 1.0);

		Parameter transferSurvivalWeightParam = new Parameter("mpslgp.transfer-survival-weight");
		this.transferSurvivalWeight = state.parameters.getDoubleWithDefault(transferSurvivalWeightParam, null, 0.1);

		Parameter transferNoImprovementPenaltyParam = new Parameter("mpslgp.transfer-no-improvement-penalty");
		this.transferNoImprovementPenalty = state.parameters.getDoubleWithDefault(transferNoImprovementPenaltyParam, null, 0.05);

		initialAdaptiveTransferState();

		//calculate the pc of each individual in each subpop and do cluster--------------
		this.phenoCharacterisation = new PhenoCharacterisation[2];
		//dynamic simulation
		phenoCharacterisation[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation(this,20);
		phenoCharacterisation[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation(this,20);

		initialMaxTerminals();

	}

	public boolean usePeerTaskTransfer() {
		return numTasks > 1 && generation >= transferStartGeneration && transferProbability > 0;
	}

	public boolean useAdaptiveTransfer() {
		return usePeerTaskTransfer() && adaptiveTransfer && transferUtility != null;
	}

	private void initialAdaptiveTransferState() {
		int tasks = Math.max(1, numTasks);
		int regions = Math.max(1, preferenceRegions);
		transferUtility = new double[tasks][tasks][regions];
		pendingTransferCounts = new int[tasks][tasks][regions];
		bestTaskRegionFitness = new double[tasks][regions];
		for (int task = 0; task < tasks; task++) {
			for (int region = 0; region < regions; region++) {
				bestTaskRegionFitness[task][region] = Double.POSITIVE_INFINITY;
			}
		}
	}

	public int preferenceRegionForSubproblem(int subproblemIndex) {
		if (!(initializer instanceof PSLInitializer)) {
			return 0;
		}
		PSLInitializer init = (PSLInitializer) initializer;
		if (init.weights == null || init.weights.length == 0) {
			return 0;
		}
		double[] preference = init.weights[Math.floorMod(subproblemIndex, init.weights.length)];
		if (preference == null || preference.length == 0) {
			return 0;
		}
		double firstObjectiveWeight = Math.max(0.0, Math.min(1.0, preference[0]));
		int region = (int) Math.floor(firstObjectiveWeight * preferenceRegions);
		return Math.max(0, Math.min(preferenceRegions - 1, region));
	}

	public int selectAdaptiveDonorTask(int receivingTask, int region, int thread) {
		double[] donorWeights = donorSoftmaxWeights(receivingTask, region);
		double draw = random[thread].nextDouble();
		double cumulative = 0.0;
		for (int donorTask = 0; donorTask < donorWeights.length; donorTask++) {
			if (donorTask == receivingTask) {
				continue;
			}
			cumulative += donorWeights[donorTask];
			if (draw <= cumulative) {
				return donorTask;
			}
		}
		for (int donorTask = 0; donorTask < Math.max(1, numTasks); donorTask++) {
			if (donorTask != receivingTask) {
				return donorTask;
			}
		}
		return receivingTask;
	}

	public double adaptiveTransferProbability(int receivingTask, int donorTask, int region) {
		if (!useAdaptiveTransfer() || receivingTask == donorTask) {
			return 0.0;
		}
		double[] donorWeights = donorSoftmaxWeights(receivingTask, region);
		double probability = boundedMaxTransferProbability() * donorWeights[donorTask];
		return Math.max(minAdaptiveTransferProbability, Math.min(maxAdaptiveTransferProbability, probability));
	}

	public double adaptiveTransferProbability(int receivingTask, int region) {
		if (!useAdaptiveTransfer()) {
			return transferProbability;
		}
		return boundedMaxTransferProbability();
	}

	private double boundedMaxTransferProbability() {
		return Math.max(0.0, Math.min(1.0, maxAdaptiveTransferProbability));
	}

	private double[] donorSoftmaxWeights(int receivingTask, int region) {
		return donorSoftmaxWeights(transferUtility, receivingTask, region);
	}

	private double[] donorSoftmaxWeights(double[][][] utilityMatrix, int receivingTask, int region) {
		int tasks = Math.max(1, numTasks);
		double[] weights = new double[tasks];
		double maxUtility = -Double.MAX_VALUE;
		for (int donorTask = 0; donorTask < tasks; donorTask++) {
			if (donorTask == receivingTask) {
				continue;
			}
			maxUtility = Math.max(maxUtility, utilityMatrix[receivingTask][donorTask][region]);
		}
		double total = 0.0;
		for (int donorTask = 0; donorTask < tasks; donorTask++) {
			if (donorTask == receivingTask) {
				weights[donorTask] = 0.0;
				continue;
			}
			weights[donorTask] = Math.exp((utilityMatrix[receivingTask][donorTask][region] - maxUtility) /
					adaptiveTransferTemperature);
			total += weights[donorTask];
		}
		if (total <= 0.0 || Double.isNaN(total)) {
			double uniform = 1.0 / Math.max(1, tasks - 1);
			for (int donorTask = 0; donorTask < tasks; donorTask++) {
				weights[donorTask] = donorTask == receivingTask ? 0.0 : uniform;
			}
			return weights;
		}
		for (int donorTask = 0; donorTask < tasks; donorTask++) {
			weights[donorTask] /= total;
		}
		return weights;
	}

	private double adaptiveTransferProbabilityFromUtilities(double[][][] utilityMatrix, int receivingTask, int donorTask, int region) {
		if (receivingTask == donorTask) {
			return 0.0;
		}
		double[] donorWeights = donorSoftmaxWeights(utilityMatrix, receivingTask, region);
		double probability = boundedMaxTransferProbability() * donorWeights[donorTask];
		return Math.max(minAdaptiveTransferProbability, Math.min(maxAdaptiveTransferProbability, probability));
	}

	private double[][][] copyTransferUtility() {
		int tasks = Math.max(1, numTasks);
		double[][][] copy = new double[tasks][tasks][preferenceRegions];
		for (int receivingTask = 0; receivingTask < tasks; receivingTask++) {
			for (int donorTask = 0; donorTask < tasks; donorTask++) {
				System.arraycopy(transferUtility[receivingTask][donorTask], 0,
						copy[receivingTask][donorTask], 0, preferenceRegions);
			}
		}
		return copy;
	}

	public void recordTransferEvent(int receivingTask, int donorTask, int region) {
		if (!useAdaptiveTransfer() || receivingTask == donorTask) {
			return;
		}
		if (receivingTask < 0 || receivingTask >= pendingTransferCounts.length || donorTask < 0 || donorTask >= pendingTransferCounts.length) {
			return;
		}
		int boundedRegion = Math.max(0, Math.min(preferenceRegions - 1, region));
		pendingTransferCounts[receivingTask][donorTask][boundedRegion]++;
	}

	private void updateAdaptiveTransferUtilitiesAfterEvaluation() {
		if (!useAdaptiveTransfer()) {
			return;
		}
		int tasks = Math.max(1, numTasks);
		int[] taskCounts = taskCounts(population.subpops[0].individuals);
		double[][][] utilityBeforeUpdate = copyTransferUtility();
		double[][][] utilityAfterUpdate = copyTransferUtility();
		for (int receivingTask = 0; receivingTask < tasks; receivingTask++) {
			for (int donorTask = 0; donorTask < tasks; donorTask++) {
				for (int region = 0; region < preferenceRegions; region++) {
					int transferCount = pendingTransferCounts[receivingTask][donorTask][region];
					if (receivingTask == donorTask) {
						continue;
					}
					double currentBest = bestPSLFitnessForTask(receivingTask);
					double previousBest = bestTaskRegionFitness[receivingTask][region];
					double improvement = Double.isInfinite(previousBest) ? 0.0 : previousBest - currentBest;
					double taskPopulationShare = (double) taskCounts[receivingTask] / Math.max(1, population.subpops[0].individuals.length);
					double improvementComponent = 0.0;
					double survivalComponent = 0.0;
					double negativeTransferPenalty = 0.0;
					double contribution = 0.0;
					double oldUtility = utilityBeforeUpdate[receivingTask][donorTask][region];
					double newUtility = oldUtility;
					if (transferCount > 0) {
						improvementComponent = transferImprovementWeight * improvement;
						survivalComponent = transferSurvivalWeight * taskPopulationShare;
						negativeTransferPenalty = improvement <= 0.0 ? transferNoImprovementPenalty : 0.0;
						contribution = improvementComponent + survivalComponent - negativeTransferPenalty;
						newUtility = (1.0 - adaptiveTransferLearningRate) * oldUtility + adaptiveTransferLearningRate * contribution;
					}
					utilityAfterUpdate[receivingTask][donorTask][region] = newUtility;
				}
			}
		}
		for (int receivingTask = 0; receivingTask < tasks; receivingTask++) {
			for (int donorTask = 0; donorTask < tasks; donorTask++) {
				for (int region = 0; region < preferenceRegions; region++) {
					if (receivingTask == donorTask) {
						continue;
					}
					int transferCount = pendingTransferCounts[receivingTask][donorTask][region];
					double currentBest = bestPSLFitnessForTask(receivingTask);
					double previousBest = bestTaskRegionFitness[receivingTask][region];
					double improvement = Double.isInfinite(previousBest) ? 0.0 : previousBest - currentBest;
					double taskPopulationShare = (double) taskCounts[receivingTask] / Math.max(1, population.subpops[0].individuals.length);
					double improvementComponent = transferCount > 0 ? transferImprovementWeight * improvement : 0.0;
					double survivalComponent = transferCount > 0 ? transferSurvivalWeight * taskPopulationShare : 0.0;
					double negativeTransferPenalty = transferCount > 0 && improvement <= 0.0 ? transferNoImprovementPenalty : 0.0;
					double contribution = improvementComponent + survivalComponent - negativeTransferPenalty;
					transferUtility[receivingTask][donorTask][region] = utilityAfterUpdate[receivingTask][donorTask][region];
					transferDiagnosticRecords.add(new TransferDiagnosticRecord(generation, receivingTask, donorTask, region,
							transferCount, contribution,
							adaptiveTransferProbabilityFromUtilities(utilityBeforeUpdate, receivingTask, donorTask, region),
							adaptiveTransferProbabilityFromUtilities(utilityAfterUpdate, receivingTask, donorTask, region),
							utilityBeforeUpdate[receivingTask][donorTask][region],
							utilityAfterUpdate[receivingTask][donorTask][region], previousBest, currentBest,
							improvement, improvementComponent, taskPopulationShare, survivalComponent,
							negativeTransferPenalty, adaptiveTransferLearningRate, adaptiveTransferTemperature,
							boundedMaxTransferProbability()));
				}
			}
		}
		for (int task = 0; task < tasks; task++) {
			int currentRegion = preferenceRegionForSubproblem(generation);
			double currentBest = bestPSLFitnessForTask(task);
			if (currentBest < bestTaskRegionFitness[task][currentRegion]) {
				bestTaskRegionFitness[task][currentRegion] = currentBest;
			}
		}
		clearPendingTransferCounts();
	}

	private void clearPendingTransferCounts() {
		for (int receivingTask = 0; receivingTask < pendingTransferCounts.length; receivingTask++) {
			for (int donorTask = 0; donorTask < pendingTransferCounts[receivingTask].length; donorTask++) {
				for (int region = 0; region < pendingTransferCounts[receivingTask][donorTask].length; region++) {
					pendingTransferCounts[receivingTask][donorTask][region] = 0;
				}
			}
		}
	}

	private double bestPSLFitnessForTask(int taskIndex) {
		double best = Double.POSITIVE_INFINITY;
		for (Individual individual : population.subpops[0].individuals) {
			if (getTaskIndex(individual) == taskIndex && individual.fitness instanceof PSLMultiObjectiveFitness) {
				best = Math.min(best, ((PSLMultiObjectiveFitness) individual.fitness).getPSLFitness());
			}
		}
		return best;
	}

	public void assignTaskIndicesIfNeeded() {
		assignTaskIndicesIfNeeded(population);
	}

	public void assignTaskIndicesIfNeeded(ec.Population targetPopulation) {
		if (targetPopulation == null || numTasks <= 0) {
			return;
		}
		for (int subpop = 0; subpop < targetPopulation.subpops.length; subpop++) {
			Individual[] individuals = targetPopulation.subpops[subpop].individuals;
			for (int i = 0; i < individuals.length; i++) {
				assignTaskIndexIfNeeded(individuals[i], i);
			}
		}
	}

	public void assignTaskIndexIfNeeded(Individual individual, int index) {
		if (individual != null && individual.fitness instanceof PSLMultiObjectiveFitness) {
			PSLMultiObjectiveFitness fitness = (PSLMultiObjectiveFitness) individual.fitness;
			if (fitness.getTaskIndex() < 0) {
				fitness.setTaskIndex(index % Math.max(1, numTasks));
			}
		}
	}

	public int getTaskIndex(Individual individual) {
		if (individual != null && individual.fitness instanceof PSLMultiObjectiveFitness) {
			return ((PSLMultiObjectiveFitness) individual.fitness).getTaskIndex();
		}
		return 0;
	}

	public void setTaskIndex(Individual individual, int taskIndex) {
		if (individual != null && individual.fitness instanceof PSLMultiObjectiveFitness) {
			((PSLMultiObjectiveFitness) individual.fitness).setTaskIndex(taskIndex);
		}
	}

	public void balanceTaskDistributionForSurvival(int subpop, int targetSize) {
		if (numTasks <= 1 || population == null || population.subpops == null || subpop >= population.subpops.length) {
			return;
		}
		Individual[] sortedIndividuals = population.subpops[subpop].individuals;
		if (sortedIndividuals == null || sortedIndividuals.length <= targetSize) {
			return;
		}

		int[] available = taskCounts(sortedIndividuals);
		int[] desired = balancedTaskTargets(available, targetSize);
		int[] selected = new int[Math.max(1, numTasks)];
		boolean[] keep = new boolean[sortedIndividuals.length];
		int keepSize = 0;

		for (int i = 0; i < sortedIndividuals.length && keepSize < targetSize; i++) {
			int task = getTaskIndex(sortedIndividuals[i]);
			if (task >= 0 && task < selected.length && selected[task] < desired[task]) {
				keep[i] = true;
				selected[task]++;
				keepSize++;
			}
		}

		for (int i = 0; i < sortedIndividuals.length && keepSize < targetSize; i++) {
			if (!keep[i]) {
				keep[i] = true;
				keepSize++;
			}
		}

		Individual[] balancedIndividuals = new Individual[sortedIndividuals.length];
		int index = 0;
		for (int i = 0; i < sortedIndividuals.length; i++) {
			if (keep[i]) {
				balancedIndividuals[index++] = sortedIndividuals[i];
			}
		}
		for (int i = 0; i < sortedIndividuals.length; i++) {
			if (!keep[i]) {
				balancedIndividuals[index++] = sortedIndividuals[i];
			}
		}
		population.subpops[subpop].individuals = balancedIndividuals;

		int[] survivorCounts = new int[Math.max(1, numTasks)];
		for (int i = 0; i < Math.min(targetSize, balancedIndividuals.length); i++) {
			int task = getTaskIndex(balancedIndividuals[i]);
			if (task >= 0 && task < survivorCounts.length) {
				survivorCounts[task]++;
			}
		}
		System.out.println("MPSLGP task-balanced survival counts: " + taskCountsToString(survivorCounts));
	}

	private int[] taskCounts(Individual[] individuals) {
		int[] counts = new int[Math.max(1, numTasks)];
		for (Individual individual : individuals) {
			int task = getTaskIndex(individual);
			if (task >= 0 && task < counts.length) {
				counts[task]++;
			}
		}
		return counts;
	}

	private int[] balancedTaskTargets(int[] available, int targetSize) {
		int tasks = Math.max(1, numTasks);
		int[] desired = new int[tasks];
		int base = targetSize / tasks;
		int remainder = targetSize % tasks;
		int shortage = 0;
		for (int task = 0; task < tasks; task++) {
			desired[task] = base + (task < remainder ? 1 : 0);
			if (available[task] < desired[task]) {
				shortage += desired[task] - available[task];
				desired[task] = available[task];
			}
		}

		while (shortage > 0) {
			int bestTask = -1;
			int bestSurplus = 0;
			for (int task = 0; task < tasks; task++) {
				int surplus = available[task] - desired[task];
				if (surplus > bestSurplus) {
					bestSurplus = surplus;
					bestTask = task;
				}
			}
			if (bestTask < 0) {
				break;
			}
			desired[bestTask]++;
			shortage--;
		}
		return desired;
	}

	private String taskCountsToString(int[] counts) {
		StringBuilder builder = new StringBuilder("[");
		for (int task = 0; task < counts.length; task++) {
			if (task > 0) {
				builder.append(", ");
			}
			builder.append("task ").append(task).append("=").append(counts[task]);
		}
		builder.append("]");
		return builder.toString();
	}

	public void recordSelectedParent(int parentIndex) {
		this.parentIndex.add(parentIndex);
		Individual[] individuals = this.population.subpops[0].individuals;
		int taskIndex = -1;
		if (parentIndex >= 0 && parentIndex < individuals.length) {
			taskIndex = getTaskIndex(individuals[parentIndex]);
		}
		this.parentTaskIndex.add(taskIndex);
	}

	public void initialMaxTerminals(){
		this.maxTerminals.put("NUM_OPS_IN_QUEUE", -1.0);
		this.maxTerminals.put("WORK_IN_QUEUE", -1.0);
		this.maxTerminals.put("MACHINE_WAITING_TIME", -1.0);
		this.maxTerminals.put("PROC_TIME", -1.0);
		this.maxTerminals.put("NEXT_PROC_TIME", -1.0);
		this.maxTerminals.put("OP_WAITING_TIME", -1.0);
		this.maxTerminals.put("WORK_REMAINING", -1.0);
		this.maxTerminals.put("NUM_OPS_REMAINING", -1.0);
		this.maxTerminals.put("WEIGHT", -1.0);
		this.maxTerminals.put("TIME_IN_SYSTEM", -1.0);
		this.maxTerminals.put("RELATIVE_DUE_DATE", -1.0);
		this.maxTerminals.put("SLACK", -1.0);
		this.maxTerminals.put("TRANSFER_TIME", -1.0);
	}

	public void updateMaxTerminals(DecisionSituation decisionSituation){
		if(decisionSituation instanceof SequencingDecisionSituation){
			List<OperationOption> queue = decisionSituation.getQueue();
			WorkCenter workCenter = ((SequencingDecisionSituation) decisionSituation).getWorkCenter();
			SystemState systemState = decisionSituation.getSystemState();
			double maxNumOpsInQueue = workCenter.getQueue().size();
			double maxWorkInQueue = workCenter.getWorkInQueue();
			double maxMachineWaitingTime = systemState.getClockTime() - workCenter.getReadyTime();
			double maxProcTime = -1;
			double maxNextProcTime = -1;
			double maxOpWaitingTime = -1;
			double maxWorkRemaining = -1;
			double maxNumOpsRemaining = -1;
			double maxWeight = -1;
			double maxTimeInSystem = -1;
			double maxRelativeDueDate = -1;
			double maxSlack = -1;
			double maxTransferTime = -1;
			for(OperationOption op:queue){
				if(op.getProcTime() > maxProcTime){
					maxProcTime = op.getProcTime();
				}
				if(op.getNextProcTime() > maxNextProcTime){
					maxNextProcTime = op.getNextProcTime();
				}
				double opWaitingTime = systemState.getClockTime() - op.getReadyTime();
				if(opWaitingTime > maxOpWaitingTime){
					maxOpWaitingTime = opWaitingTime;
				}
				if(op.getWorkRemaining() > maxWorkRemaining){
					maxWorkRemaining = op.getWorkRemaining();
				}
				if(op.getNumOpsRemaining() > maxNumOpsRemaining){
					maxNumOpsRemaining = op.getNumOpsRemaining();
				}
				if(op.getJob().getWeight() > maxWeight){
					maxWeight = op.getJob().getWeight();
				}
				double timeInSystem = systemState.getClockTime() - op.getJob().getReleaseTime();
				if(timeInSystem > maxTimeInSystem){
					maxTimeInSystem = timeInSystem;
				}
				double relativeDueDate = op.getJob().getDueDate() - systemState.getClockTime();
				if(relativeDueDate > maxRelativeDueDate){
					maxRelativeDueDate = relativeDueDate;
				}
				double slack = op.getJob().getDueDate() - systemState.getClockTime() - op.getWorkRemaining();
				if(slack > maxSlack){
					maxSlack = slack;
				}
				//transfer time
				int opID = op.getOperation().getId();
				double value = -1;
				if(opID==0){
					value = ((HeterogeneousSimulation)systemState.getSimulation()).getEntryWorkCenterTranserTime(workCenter.getId());
				}
				else if(opID==op.getJob().getOperations().size()-1){
					int fromWorkCenterID = op.getJob().getOperation(opID-1).getSelectedWorkCenter().getId();
					double transferTime = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterTranserTime(fromWorkCenterID, workCenter.getId());
					double downloadTime = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterExitTranserTime(workCenter.getId());
					value = transferTime + downloadTime;
				}
				else{
					int fromWorkCenterID = op.getJob().getOperation(opID-1).getSelectedWorkCenter().getId();
					value = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterTranserTime(fromWorkCenterID, workCenter.getId());
				}
				if(value > maxTransferTime){
					maxTransferTime = value;
				}
			}
			this.maxTerminals.replace("NUM_OPS_IN_QUEUE", maxNumOpsInQueue);
			this.maxTerminals.replace("WORK_IN_QUEUE", maxWorkInQueue);
			this.maxTerminals.replace("MACHINE_WAITING_TIME", maxMachineWaitingTime);
			this.maxTerminals.replace("PROC_TIME", maxProcTime);
			this.maxTerminals.replace("NEXT_PROC_TIME", maxNextProcTime);
			this.maxTerminals.replace("OP_WAITING_TIME", maxOpWaitingTime);
			this.maxTerminals.replace("WORK_REMAINING", maxWorkRemaining);
			this.maxTerminals.replace("NUM_OPS_REMAINING", maxNumOpsRemaining);
			this.maxTerminals.replace("WEIGHT", maxWeight);
			this.maxTerminals.replace("TIME_IN_SYSTEM", maxTimeInSystem);
			this.maxTerminals.replace("RELATIVE_DUE_DATE", maxRelativeDueDate);
			this.maxTerminals.replace("SLACK", maxSlack);
			this.maxTerminals.replace("TRANSFER_TIME", maxTransferTime);
		}
		else if(decisionSituation instanceof RoutingDecisionSituation){
			List<OperationOption> queue = decisionSituation.getQueue();
//			WorkCenter workCenter = ((RoutingDecisionSituation) decisionSituation).getWorkCenter();
			SystemState systemState = decisionSituation.getSystemState();
			double maxNumOpsInQueue = -1;
			double maxWorkInQueue = -1;
			double maxMachineWaitingTime = -1;
			double maxProcTime = -1;
			double maxNextProcTime = -1;
			double maxOpWaitingTime = -1;
			double maxWorkRemaining = -1;
			double maxNumOpsRemaining = -1;
			double maxWeight = -1;
			double maxTimeInSystem = -1;
			double maxRelativeDueDate = -1;
			double maxSlack = -1;
			double maxTransferTime = -1;
			for(OperationOption op:queue){
				WorkCenter workCenter = op.getWorkCenter();
				double numOpsInQueue = workCenter.getQueue().size();
				if(numOpsInQueue > maxNumOpsInQueue){
					maxNumOpsInQueue = numOpsInQueue;
				}
				double workInQueue = workCenter.getWorkInQueue();
				if(workInQueue > maxWorkInQueue){
					maxWorkInQueue = workInQueue;
				}
				double machineWaitingTime = systemState.getClockTime() - workCenter.getReadyTime();
				if(machineWaitingTime > maxMachineWaitingTime){
					maxMachineWaitingTime = machineWaitingTime;
				}
				if(op.getProcTime() > maxProcTime){
					maxProcTime = op.getProcTime();
				}
				if(op.getNextProcTime() > maxNextProcTime){
					maxNextProcTime = op.getNextProcTime();
				}
				double opWaitingTime = systemState.getClockTime() - op.getReadyTime();
				if(opWaitingTime > maxOpWaitingTime){
					maxOpWaitingTime = opWaitingTime;
				}
				if(op.getWorkRemaining() > maxWorkRemaining){
					maxWorkRemaining = op.getWorkRemaining();
				}
				if(op.getNumOpsRemaining() > maxNumOpsRemaining){
					maxNumOpsRemaining = op.getNumOpsRemaining();
				}
				if(op.getJob().getWeight() > maxWeight){
					maxWeight = op.getJob().getWeight();
				}
				double timeInSystem = systemState.getClockTime() - op.getJob().getReleaseTime();
				if(timeInSystem > maxTimeInSystem){
					maxTimeInSystem = timeInSystem;
				}
				double relativeDueDate = op.getJob().getDueDate() - systemState.getClockTime();
				if(relativeDueDate > maxRelativeDueDate){
					maxRelativeDueDate = relativeDueDate;
				}
				double slack = op.getJob().getDueDate() - systemState.getClockTime() - op.getWorkRemaining();
				if(slack > maxSlack){
					maxSlack = slack;
				}
				//transfer time
				int opID = op.getOperation().getId();
				double value = -1;
				if(opID==0){
					value = ((HeterogeneousSimulation)systemState.getSimulation()).getEntryWorkCenterTranserTime(workCenter.getId());
				}
				else if(opID==op.getJob().getOperations().size()-1){
					int fromWorkCenterID = op.getJob().getOperation(opID-1).getSelectedWorkCenter().getId();
					double transferTime = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterTranserTime(fromWorkCenterID, workCenter.getId());
					double downloadTime = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterExitTranserTime(workCenter.getId());
					value = transferTime + downloadTime;
				}
				else{
					int fromWorkCenterID = op.getJob().getOperation(opID-1).getSelectedWorkCenter().getId();
					value = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterTranserTime(fromWorkCenterID, workCenter.getId());
				}
				if(value > maxTransferTime){
					maxTransferTime = value;
				}
			}
			this.maxTerminals.replace("NUM_OPS_IN_QUEUE", maxNumOpsInQueue);
			this.maxTerminals.replace("WORK_IN_QUEUE", maxWorkInQueue);
			this.maxTerminals.replace("MACHINE_WAITING_TIME", maxMachineWaitingTime);
			this.maxTerminals.replace("PROC_TIME", maxProcTime);
			this.maxTerminals.replace("NEXT_PROC_TIME", maxNextProcTime);
			this.maxTerminals.replace("OP_WAITING_TIME", maxOpWaitingTime);
			this.maxTerminals.replace("WORK_REMAINING", maxWorkRemaining);
			this.maxTerminals.replace("NUM_OPS_REMAINING", maxNumOpsRemaining);
			this.maxTerminals.replace("WEIGHT", maxWeight);
			this.maxTerminals.replace("TIME_IN_SYSTEM", maxTimeInSystem);
			this.maxTerminals.replace("RELATIVE_DUE_DATE", maxRelativeDueDate);
			this.maxTerminals.replace("SLACK", maxSlack);
			this.maxTerminals.replace("TRANSFER_TIME", maxTransferTime);
		}
	}

	public List<List<CustomerPoint>> getCluster() {
		return cluster;
	}


	@Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

	    //System.out.println("generation "+generation);
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

		this.evaluatePreferenceIndex = this.generation;
		this.evaluateOrder = 0;

	    evaluator.evaluatePopulation(this);
		updateAdaptiveTransferUtilitiesAfterEvaluation();
	      //// here, after this we evaluate the population
//	    statistics.postEvaluationStatistics(this); //log the best individual

		if(generation != 0){
			List<Integer> parentIndexCopy = new ArrayList<>(parentIndex);
			allGenerationParentIndex.add(parentIndexCopy);
			List<Integer> parentTaskIndexCopy = new ArrayList<>(parentTaskIndex);
			allGenerationParentTaskIndex.add(parentTaskIndexCopy);
		}
		Individual[] individuals = this.population.subpops[0].individuals;
		double[][][] pcBySubpop = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, true);
		recordDiversityForPopulation("ALL", -1, individuals, pcBySubpop[0], parentIndex, bestIndi(0));
		for (int task = 0; task < Math.max(1, numTasks); task++) {
			Individual[] taskIndividuals = individualsForTask(task);
			double[][] taskPc = pcForTask(pcBySubpop[0], task);
			List<Integer> taskParentIndex = parentIndicesForTask(task);
			recordDiversityForPopulation("TASK", task, taskIndividuals, taskPc, taskParentIndex, bestIndividualForTask(task));
		}
		parentIndex.clear();
		parentTaskIndex.clear();
		if (!storeTaskGenDiversities.isEmpty()) {
			DiversityRecord latest = storeTaskGenDiversities.get(storeTaskGenDiversities.size() - Math.max(1, numTasks) - 1);
			System.out.println("Genotype diversity: " + latest.genotypeDiversity);
			System.out.println("Entropy diversity: " + latest.entropyDiversity);
			System.out.println("PC diversity: " + latest.pcDiversity);
		}


		int popSize = population.subpops[0].individuals.length;
		double[] obj0 = new double[popSize];
		double[] obj1 = new double[popSize];
		for(int i=0; i < popSize; i++){
			MultiObjectiveFitness fit = (MultiObjectiveFitness)population.subpops[0].individuals[i].fitness;
			obj0[i] = fit.objectives[0];
			obj1[i] = fit.objectives[1];
		}
		int[] taskIndexByIndividual = new int[popSize];
		for(int i=0; i < popSize; i++){
			taskIndexByIndividual[i] = getTaskIndex(population.subpops[0].individuals[i]);
		}
		objective0.add(obj0);
		objective1.add(obj1);
		objectiveTaskIndex.add(taskIndexByIndividual);
		recordTopMetricForTasks(5);



		statistics.postEvaluationStatistics(this); //log the best individual

		printFrontforGen(false, false);//add by mengxu 2022.06.29
		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;

		if(generation%printGap == 0){//add by mengxu 2022.08.05
			statistics.middleStatistics(this, this.R_NOTDONE);
		}

		//---------------------------------------------------------


		//add 2021.10.08
//		populationNormalisation();

		// SHOULD WE QUIT?
	    if (evaluator.runComplete(this) && quitOnRunComplete)
	        {
			writeDiversityToFile();
			writeSelectParentIndexToFile();
			writeEachObjToFile();
			writeTopMetricToFile();
			writeTransferContributionToFile();
	        output.message("Found Ideal Individual");
	        return R_SUCCESS;
	        }
	    // SHOULD WE QUIT?
	    if (generation == numGenerations-1)
	        {
	    	generation++; // in this way, the last generation value will be printed properly.  fzhang 28.3.2018
			writeDiversityToFile();//modified by mengxu	2021.04.15
			writeSelectParentIndexToFile();
			writeEachObjToFile();
			writeTopMetricToFile();
			writeTransferContributionToFile();
//			writeEnsembleToFile();//modified by mengxu 2021.05.08
			return R_FAILURE;
	        }

	    // PRE-BREEDING EXCHANGING
	    statistics.prePreBreedingExchangeStatistics(this);
	    population = exchanger.preBreedingExchangePopulation(this);  /** Simply returns state.population. */
	    statistics.postPreBreedingExchangeStatistics(this);

	    String exchangerWantsToShutdown = exchanger.runComplete(this);  /** Always returns null */
	    if (exchangerWantsToShutdown!=null)
	        {
	        output.message(exchangerWantsToShutdown);
	        /*
	         * Don't really know what to return here.  The only place I could
	         * find where runComplete ever returns non-null is
	         * IslandExchange.  However, that can return non-null whether or
	         * not the ideal individual was found (for example, if there was
	         * a communication error with the server).
	         *
	         * Since the original version of this code didn't care, and the
	         * result was initialized to R_SUCCESS before the while loop, I'm
	         * just going to return R_SUCCESS here.
	         */

	        return R_SUCCESS;
	        }

		// Generate new instances if needed //modified by mengxu 2022.06.29
//		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
		if (problem.getEvaluationModel().isRotatable()) {
			problem.rotateEvaluationModel();
		}
//		evaluator.evaluatePopulation(this);//modified by mengxu 2022.06.29

	    // BREEDING
	    statistics.preBreedingStatistics(this);

	    population = breeder.breedPopulation(this); //!!!!!!   return newpop;  if it is NSGA-II, the population here is 2N

	    // POST-BREEDING EXCHANGING
	    statistics.postBreedingStatistics(this);   //position 1  here, a new pop has been generated.

	    // POST-BREEDING EXCHANGING
//	    statistics.prePostBreedingExchangeStatistics(this);
//	    population = exchanger.postBreedingExchangePopulation(this);   /** Simply returns state.population. */
//	    statistics.postPostBreedingExchangeStatistics(this);  //position 2

		//change the location of this
//	    // Generate new instances if needed //original place 2022.06.29
//		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
//	    if (problem.getEvaluationModel().isRotatable()) {
//			problem.rotateEvaluationModel();
//		}

	    // INCREMENT GENERATION AND CHECKPOINT
	    generation++;
	    if (checkpoint && generation%checkpointModulo == 0)
	        {
	        output.message("Checkpointing");
	        statistics.preCheckpointStatistics(this);
	        Checkpoint.setCheckpoint(this);
	        statistics.postCheckpointStatistics(this);
	        }

	    return R_NOTDONE;
	}

	public void printFrontforGen(boolean updateBiasBalance, boolean normalisation){
		if(normalisation){
			System.out.println("\nPareto Front with normalisation of Subpopulation " + generation);

			MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(this.population.subpops[0].individuals[0].fitness);
			// build front
			ArrayList front = typicalFitness.partitionIntoParetoFront(this.population.subpops[0].individuals, null, null);

			// sort by objective[0]
			Object[] sortedFront = front.toArray();
			QuickSort.qsort(sortedFront, new SortComparator()
			{
				public boolean lt(Object a, Object b)
				{
					return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) <
							(((MultiObjectiveFitness) ((Individual) b).fitness)).getObjective(0));
				}

				public boolean gt(Object a, Object b)
				{
					return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) >
							((MultiObjectiveFitness) (((Individual) b).fitness)).getObjective(0));
				}
			});

			double[] idealPoint = ((PSLInitializer)this.initializer).idealPoint;
			double[] maxObjectives = ((PSLInitializer)this.initializer).maxObjectives;

			// print out front to statistics log
			double[] diffs = new double[typicalFitness.getNumObjectives()];
			for(int j=0; j< typicalFitness.getNumObjectives(); j++){
				System.out.print("Objective " + j + ": [");
				if(updateBiasBalance) {
					double leftBound = 0;
					double rightBound = 0;
					for (int i = 0; i < sortedFront.length; i++) {
						double fit = ((MultiObjectiveFitness) (((Individual) (sortedFront[i])).fitness)).getObjective(j);
						double normalisaFit = Math.abs(fit - idealPoint[j]) / (maxObjectives[j] - idealPoint[j]);
						if (i == 0){
							leftBound = normalisaFit;
							System.out.print(normalisaFit + ", ");
						}
						else if (i == sortedFront.length - 1) {
							rightBound = normalisaFit;
							System.out.println(normalisaFit + "]");
						} else {
							System.out.print(normalisaFit + ", ");
						}
					}

					double diff = Math.abs(rightBound - leftBound);
					diffs[j] = 1/diff;//original

					System.out.println("diff " + j + ": " + diffs[j]);
				}
				else{
					for (int i = 0; i < sortedFront.length; i++){
						double fit = ((MultiObjectiveFitness)(((Individual)(sortedFront[i])).fitness)).getObjective(j);
						double normalisaFit = Math.abs(fit - idealPoint[j]) / (maxObjectives[j] - idealPoint[j]);
						if(i == sortedFront.length - 1){
							System.out.println(normalisaFit + "]");
						}
						else{
							System.out.print(normalisaFit + ", ");
						}
					}
				}
			}
		}
		else{
			System.out.println("\nPareto Front of Subpopulation " + generation);

			MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(this.population.subpops[0].individuals[0].fitness);
			// build front
			ArrayList front = typicalFitness.partitionIntoParetoFront(this.population.subpops[0].individuals, null, null);

			// sort by objective[0]
			Object[] sortedFront = front.toArray();
			QuickSort.qsort(sortedFront, new SortComparator()
			{
				public boolean lt(Object a, Object b)
				{
					return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) <
							(((MultiObjectiveFitness) ((Individual) b).fitness)).getObjective(0));
				}

				public boolean gt(Object a, Object b)
				{
					return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) >
							((MultiObjectiveFitness) (((Individual) b).fitness)).getObjective(0));
				}
			});

			// print out front to statistics log
			double[] diffs = new double[typicalFitness.getNumObjectives()];
			for(int j=0; j< typicalFitness.getNumObjectives(); j++){
				System.out.print("Objective " + j + ": [");
				if(updateBiasBalance) {
					double leftBound = 0;
					double rightBound = 0;
					for (int i = 0; i < sortedFront.length; i++) {

						double fit = ((MultiObjectiveFitness) (((Individual) (sortedFront[i])).fitness)).getObjective(j);
						if (i == 0){
							leftBound = fit;
							System.out.print(fit + ", ");
						}
						else if (i == sortedFront.length - 1) {
							rightBound = fit;
							System.out.println(fit + "]");
						} else {
							System.out.print(fit + ", ");
						}
					}
					double diff = Math.abs(rightBound - leftBound);
					diffs[j] = 1/diff; //original
				}
				else{
					for (int i = 0; i < sortedFront.length; i++){
						double fit = ((MultiObjectiveFitness)(((Individual)(sortedFront[i])).fitness)).getObjective(j);
						if(i == sortedFront.length - 1){
							System.out.println(fit + "]");
						}
						else{
							System.out.print(fit + ", ");
						}
					}
				}
			}
		}
	}


	//modified by meng xu 2021.05.08
	public List<Individual> getAllDiverseIndividualFromCluster(List<List<CustomerPoint>> customerPointCluster){
		List<Individual> allDiverseIndividual = new ArrayList<>();
		for(int i =0;i<customerPointCluster.size();i++) {
			double minFitness = customerPointCluster.get(i).get(0).getFitness();;
			int index = 0;
			if(customerPointCluster.get(i).size()>1){
				for(int j=1;j<customerPointCluster.get(i).size();j++) {
					double fitness = customerPointCluster.get(i).get(j).getFitness();
					if(fitness < minFitness){
						minFitness = fitness;
						index = j;
					}
				}
			}
			int indIndex = customerPointCluster.get(i).get(index).getIndIndex();
			//this is for only one subpop
			allDiverseIndividual.add(this.population.subpops[0].individuals[indIndex]);
		}
		return allDiverseIndividual;
	}

	private Individual[] individualsForTask(int taskIndex) {
		List<Individual> taskIndividuals = new ArrayList<>();
		for (Individual individual : population.subpops[0].individuals) {
			if (getTaskIndex(individual) == taskIndex) {
				taskIndividuals.add(individual);
			}
		}
		return taskIndividuals.toArray(new Individual[0]);
	}

	private double[][] pcForTask(double[][] pcByIndividual, int taskIndex) {
		List<double[]> taskPc = new ArrayList<>();
		Individual[] individuals = population.subpops[0].individuals;
		for (int i = 0; i < individuals.length && i < pcByIndividual.length; i++) {
			if (getTaskIndex(individuals[i]) == taskIndex) {
				taskPc.add(pcByIndividual[i]);
			}
		}
		return taskPc.toArray(new double[0][]);
	}

	private List<Integer> parentIndicesForTask(int taskIndex) {
		List<Integer> taskParentIndex = new ArrayList<>();
		for (int i = 0; i < parentIndex.size() && i < parentTaskIndex.size(); i++) {
			if (parentTaskIndex.get(i) == taskIndex) {
				taskParentIndex.add(parentIndex.get(i));
			}
		}
		return taskParentIndex;
	}

	private Individual bestIndividualForTask(int taskIndex) {
		Individual best = null;
		for (Individual individual : population.subpops[0].individuals) {
			if (getTaskIndex(individual) != taskIndex) {
				continue;
			}
			if (best == null || individual.fitness.betterThan(best.fitness)) {
				best = individual;
			}
		}
		return best;
	}

	private void recordTopMetricForTasks(int topN) {
		String metricName = currentMetricName();
		if (metricName == null) {
			return;
		}
		System.out.println("MPSLGP top " + topN + " " + metricName + " by task at generation " + generation + ":");
		for (int task = 0; task < Math.max(1, numTasks); task++) {
			TopMetricCandidate[] topCandidates = topMetricCandidatesForTask(task, topN, metricName);
			if (topCandidates.length == 0) {
				System.out.println("  Task " + task + ": no individuals");
				continue;
			}
			StringBuilder line = new StringBuilder("  Task ").append(task).append(": ");
			for (int rank = 0; rank < topCandidates.length; rank++) {
				TopMetricCandidate candidate = topCandidates[rank];
				ClearingPSLMultiObjectiveFitness fitness = (ClearingPSLMultiObjectiveFitness) candidate.individual.fitness;
				topMetricRecords.add(new TopMetricRecord(generation, task, rank + 1, candidate.individualIndex,
						metricName, candidate.metricValue, fitness.getPSLFitness(), fitness.getPreferenceDiversity()));
				if (rank > 0) {
					line.append("; ");
				}
				line.append("#").append(rank + 1)
						.append(" ind=").append(candidate.individualIndex)
						.append(" ").append(metricName).append("=").append(candidate.metricValue);
			}
			System.out.println(line);
		}
	}

	private static class TopMetricCandidate {
		Individual individual;
		int individualIndex;
		double metricValue;

		TopMetricCandidate(Individual individual, int individualIndex, double metricValue) {
			this.individual = individual;
			this.individualIndex = individualIndex;
			this.metricValue = metricValue;
		}
	}

	private TopMetricCandidate[] topMetricCandidatesForTask(int taskIndex, int topN, String metricName) {
		List<TopMetricCandidate> candidates = new ArrayList<>();
		Individual[] individuals = population.subpops[0].individuals;
		for (int individualIndex = 0; individualIndex < individuals.length; individualIndex++) {
			Individual individual = individuals[individualIndex];
			if (getTaskIndex(individual) != taskIndex || !(individual.fitness instanceof ClearingPSLMultiObjectiveFitness)) {
				continue;
			}
			double metricValue = metricValue((ClearingPSLMultiObjectiveFitness) individual.fitness, metricName);
			if (isValidMetricValue(metricValue, metricName)) {
				candidates.add(new TopMetricCandidate(individual, individualIndex, metricValue));
			}
		}
		TopMetricCandidate[] sortedCandidates = candidates.toArray(new TopMetricCandidate[0]);
		QuickSort.qsort(sortedCandidates, new SortComparator() {
			public boolean lt(Object first, Object second) {
				return betterMetric((TopMetricCandidate) first, (TopMetricCandidate) second, metricName);
			}

			public boolean gt(Object first, Object second) {
				return betterMetric((TopMetricCandidate) second, (TopMetricCandidate) first, metricName);
			}
		});
		int length = Math.min(topN, sortedCandidates.length);
		TopMetricCandidate[] topCandidates = new TopMetricCandidate[length];
		System.arraycopy(sortedCandidates, 0, topCandidates, 0, length);
		return topCandidates;
	}

	private boolean betterMetric(TopMetricCandidate first, TopMetricCandidate second, String metricName) {
		if ("HV".equals(metricName)) {
			if (first.metricValue != second.metricValue) {
				return first.metricValue > second.metricValue;
			}
		}
		else {
			if (first.metricValue != second.metricValue) {
				return first.metricValue < second.metricValue;
			}
		}
		return first.individual.fitness.betterThan(second.individual.fitness);
	}

	private String currentMetricName() {
		if (population == null || population.subpops == null || population.subpops.length == 0 ||
				population.subpops[0].individuals.length == 0 ||
				!(population.subpops[0].individuals[0].fitness instanceof ClearingPSLMultiObjectiveFitness)) {
			return null;
		}
		String compareCriteria = ((ClearingPSLMultiObjectiveFitness) population.subpops[0].individuals[0].fitness).compareCriteria;
		if ("HV".equals(compareCriteria) || "HV_noRank".equals(compareCriteria)) {
			return "HV";
		}
		if ("IGD".equals(compareCriteria) || "IGD_noRank".equals(compareCriteria)) {
			return "IGD";
		}
		if ("GD".equals(compareCriteria) || "GD_noRank".equals(compareCriteria)) {
			return "GD";
		}
		return null;
	}

	private double metricValue(ClearingPSLMultiObjectiveFitness fitness, String metricName) {
		if ("HV".equals(metricName)) {
			return fitness.getHVvalue();
		}
		if ("IGD".equals(metricName)) {
			return fitness.getIGDvalue();
		}
		if ("GD".equals(metricName)) {
			return fitness.getGDvalue();
		}
		return Double.NaN;
	}

	private boolean isValidMetricValue(double metricValue, String metricName) {
		if (Double.isNaN(metricValue)) {
			return false;
		}
		if ("HV".equals(metricName)) {
			return metricValue > Double.NEGATIVE_INFINITY;
		}
		return metricValue < Double.POSITIVE_INFINITY && metricValue < Double.MAX_VALUE;
	}

	private void recordDiversityForPopulation(String scope, int taskIndex, Individual[] individuals,
										  double[][] pcByIndividual, List<Integer> selectedParentIndex,
										  Individual bestIndividual) {
		if (individuals.length == 0) {
			storeTaskGenDiversities.add(new DiversityRecord(scope, taskIndex, 0, generation, 0.0, 0.0, 0.0, 0.0,
					0.0, 0.0, 0.0, 0.0));
			return;
		}

		GenotypeDiversity genotypeDiversity = new GenotypeDiversity();
		double genotypeDiversityValue = (double) genotypeDiversity.genotypeDiversity(individuals) / individuals.length;
		PhenotypeDiversity phenotypeDiversity = new PhenotypeDiversity();
		double phenotypeDiversityValue = (double) phenotypeDiversity.phenotypeDiversity(individuals) / individuals.length;
		double entropyDiversityValue = fitnessEntropyDiversity(individuals);
		PseudoIsomorphsDiversity pseudoIsomorphsDiversity = new PseudoIsomorphsDiversity();
		double pseudoIsomorphsDiversityValue = (double) pseudoIsomorphsDiversity.pseudoIsomorphsDiversity(individuals) / individuals.length;
		double editOneDiversityValue = 0.0;
		double editTwoDiversityValue = 0.0;
		if (bestIndividual != null) {
			EditDistanceDiversityV1 editOneDiversity = new EditDistanceDiversityV1();
			editOneDiversityValue = editOneDiversity.editDistanceDiversityV1(individuals, bestIndividual);
			EditDistanceDiversityV2 editTwoDiversity = new EditDistanceDiversityV2();
			editTwoDiversityValue = editTwoDiversity.editDistanceDiversityV2(individuals, bestIndividual);
		}
		PhenotypicCharacteristicDiversity pcDiversity = new PhenotypicCharacteristicDiversity();
		double pcDiversityValue = pcByIndividual.length == 0 ? 0.0 :
				(double) pcDiversity.phenotypicCharacteristicDiversity(pcByIndividual) / pcByIndividual.length;
		ParentIndexDiversity parentIndexDiversity = new ParentIndexDiversity();
		double parentIndexDiversityValue = selectedParentIndex.isEmpty() ? 0.0 :
				(double) parentIndexDiversity.parentIndexDiversity(selectedParentIndex) / selectedParentIndex.size();

		storeTaskGenDiversities.add(new DiversityRecord(scope, taskIndex, individuals.length, generation, genotypeDiversityValue,
				phenotypeDiversityValue, entropyDiversityValue, pseudoIsomorphsDiversityValue,
				editOneDiversityValue, editTwoDiversityValue, pcDiversityValue, parentIndexDiversityValue));
		if ("ALL".equals(scope)) {
			ArrayList<Double> diversities = new ArrayList<>();
			diversities.add((double) generation);
			diversities.add(genotypeDiversityValue);
			diversities.add(phenotypeDiversityValue);
			diversities.add(entropyDiversityValue);
			diversities.add(pseudoIsomorphsDiversityValue);
			diversities.add(editOneDiversityValue);
			diversities.add(editTwoDiversityValue);
			diversities.add(pcDiversityValue);
			diversities.add(parentIndexDiversityValue);
			storeGenDiversities.add(diversities);
		}
	}

	private double fitnessEntropyDiversity(Individual[] individuals) {
		Map<String, Integer> counts = new HashMap<>();
		for (Individual individual : individuals) {
			String key = fitnessKey(individual);
			counts.put(key, counts.getOrDefault(key, 0) + 1);
		}
		double entropy = 0.0;
		for (Integer count : counts.values()) {
			double proportion = (double) count / individuals.length;
			entropy += proportion * Math.log(proportion);
		}
		return -entropy;
	}

	private String fitnessKey(Individual individual) {
		if (individual.fitness instanceof MultiObjectiveFitness) {
			double[] objectives = ((MultiObjectiveFitness) individual.fitness).getObjectives();
			StringBuilder key = new StringBuilder();
			for (double objective : objectives) {
				key.append(Math.round(objective * 10000000000.0)).append('|');
			}
			return key.toString();
		}
		return String.valueOf(Math.round(individual.fitness.fitness() * 10000000000.0));
	}

	//2021.2.15 modified by mengxu
	public void writeDiversityToFile(){
		File diversities = new File("job." + jobSeed + ".diversities.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Gen,Scope,Task,Size,Geno,Pheno,Entropy,PseIso,Edit 1,Edit 2,PC,ParentSelection");
			writer.newLine();
			for (DiversityRecord ref : storeTaskGenDiversities) {
				writer.write(ref.generation + "," + ref.scope + "," + ref.taskIndex + "," + ref.individualCount + "," + ref.genotypeDiversity
						+ "," + ref.phenotypeDiversity + "," + ref.entropyDiversity + ","
						+ ref.pseudoIsomorphsDiversity + "," + ref.editOneDiversity + ","
						+ ref.editTwoDiversity + "," + ref.pcDiversity + "," + ref.parentSelectionDiversity);
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.7.21 modified by mengxu, to store the selected parent index of each generation
	public void writeSelectParentIndexToFile(){
		File selectParentIndex = new File("job." + jobSeed + ".selectParentIndex.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(selectParentIndex));
			writer.write("Gen,SelectionOrder,ParentIndex,ParentTask");
			writer.newLine();
			for (int gen = 0; gen < allGenerationParentIndex.size(); gen++) {
				List<Integer> ref = allGenerationParentIndex.get(gen);
				List<Integer> taskRef = gen < allGenerationParentTaskIndex.size() ? allGenerationParentTaskIndex.get(gen) : new ArrayList<Integer>();
				for(int i=0; i< ref.size(); i++){
					int taskIndex = i < taskRef.size() ? taskRef.get(i) : -1;
					writer.write((gen+1) + "," + i + "," + ref.get(i) + "," + taskIndex);
					writer.newLine();
				}
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.11.03 modified by mengxu, to store the selected parent index of each generation
	public void writeEachObjToFile(){
		int objNum = 2;
		File obj0Fitness = new File("job." + jobSeed + ".obj0fitness.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(obj0Fitness));
			writer.write("Gen"+ "," + "Index" + "," + "Task" + "," + "Fitness");
			writer.newLine();
			for (int gen = 0; gen < objective0.size(); gen++) {
				double[] ref = objective0.get(gen);
				int[] taskRef = objectiveTaskIndex.get(gen);
//				writer.write("gen"+ gen + ",");
				for(int i=0; i< ref.length; i++){
					if(ref[i] >= Double.POSITIVE_INFINITY || ref[i] >= Double.MAX_VALUE){
						continue;
					}
					else{
						writer.write("gen"+ gen + "," + i + "," + taskRef[i] + "," + ref[i]);
						writer.newLine();
					}

//					if(i==ref.length-2){
//						writer.write(ref[i] + "," + ref[i+1]);
//					}
//					else{
//						writer.write(ref[i] + ",");
//					}
				}
//				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		File obj1Fitness = new File("job." + jobSeed + ".obj1fitness.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(obj1Fitness));
			writer.write("Gen"+ "," + "Index" + "," + "Task" + "," + "Fitness");
			writer.newLine();
			for (int gen = 0; gen < objective1.size(); gen++) {
				double[] ref = objective1.get(gen);
				int[] taskRef = objectiveTaskIndex.get(gen);
//				writer.write("gen"+ gen + ",");
				for(int i=0; i< ref.length; i++){
					if(ref[i] >= Double.POSITIVE_INFINITY || ref[i] >= Double.MAX_VALUE){
						continue;
					}
					else{
						writer.write("gen"+ gen + "," + i + "," + taskRef[i] + "," + ref[i]);
						writer.newLine();
					}
//					if(i==ref.length-2){
//						writer.write(ref[i] + "," + ref[i+1]);
//					}
//					else{
//						writer.write(ref[i] + ",");
//					}
				}
//				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void writeTopMetricToFile(){
		File topMetricFile = new File("job." + jobSeed + ".top5metric.csv");
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(topMetricFile));
			writer.write("Gen,Task,Rank,IndividualIndex,Metric,Value,PSLFitness,PreferenceDiversity");
			writer.newLine();
			for (TopMetricRecord record : topMetricRecords) {
				writer.write(record.generation + "," + record.taskIndex + "," + record.rank + ","
						+ record.individualIndex + "," + record.metricName + "," + record.metricValue + ","
						+ record.pslFitness + "," + record.preferenceDiversity);
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void writeTransferContributionToFile(){
		if (!adaptiveTransfer || transferDiagnosticRecords.isEmpty()) {
			return;
		}
		File transferFile = new File("job." + jobSeed + ".transferContribution.csv");
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(transferFile));
			writer.write("Gen,ReceivingTask,ContributingTask,PreferenceRegion,TransferCount,Contribution,ProbabilityBeforeUpdate,ProbabilityAfterUpdate,UtilityBeforeUpdate,UtilityAfterUpdate,PreviousBestPSLFitness,CurrentBestPSLFitness,RawImprovement,ImprovementComponent,TaskPopulationShare,SurvivalComponent,NegativeTransferPenalty,LearningRate,Temperature,MaxTransferProbability");
			writer.newLine();
			for (TransferDiagnosticRecord record : transferDiagnosticRecords) {
				writer.write(record.generation + "," + record.receivingTask + "," + record.contributingTask + ","
						+ record.preferenceRegion + "," + record.transferCount + "," + record.contribution + ","
						+ record.probabilityBeforeUpdate + "," + record.probabilityAfterUpdate + ","
						+ record.utilityBeforeUpdate + "," + record.utilityAfterUpdate + "," + record.previousBest + ","
						+ record.currentBest + "," + record.improvement + "," + record.improvementComponent + ","
						+ record.taskPopulationShare + "," + record.survivalComponent + ","
						+ record.negativeTransferPenalty + "," + record.learningRate + "," + record.temperature + ","
						+ record.maxTransferProbability);
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//add 2021.10.08
	public void populationNormalisation(){
//		this.normalisationFitness.clear();
		Individual[] oldinds = this.population.subpops[0].individuals.clone();
		int numObjective = ((MultiObjectiveFitness)oldinds[0].fitness).getNumObjectives();
		double[] minFitness = new double[numObjective];
		double[] maxFitness = new double[numObjective];
		for(int j=0; j<numObjective; j++){
			minFitness[j] = Double.POSITIVE_INFINITY;
			maxFitness[j] = 0;
		}

//		List<Double[][]> fitnessNormalisation = new ArrayList<>();
		for(int i=0; i<oldinds.length; i++){
			double[] multiFitness = ((MultiObjectiveFitness)oldinds[i].fitness).objectives;

			for(int j=0; j<numObjective; j++){
				if(multiFitness[j] >= Double.POSITIVE_INFINITY || multiFitness[j] >= Double.MAX_VALUE){
					continue;
				}
				else{
					if(multiFitness[j]>maxFitness[j]){
						maxFitness[j] = multiFitness[j];
					}
					if(multiFitness[j]<minFitness[j]){
						minFitness[j] = multiFitness[j];
					}
				}
			}
		}

		//modified 2021.10.15
		updateMinMaxFitness(minFitness, maxFitness);

//		System.out.print("minFitness and maxFitness: ");
//		for(int j=0; j<numCase; j++){
//			System.out.print("(" + minFitness[j] + ", " + maxFitness[j] + ")");
//		}
//		System.out.println();

		//todo: if used for multi-objective, need to modify.
		for(int i=0; i<oldinds.length; i++) {
			double[] multiFitness = ((MultiObjectiveFitness)oldinds[i].fitness).objectives;

			double[] normalisationFitness = new double[numObjective];
			for (int j = 0; j < numObjective; j++) {
				if (multiFitness[j] >= Double.POSITIVE_INFINITY || multiFitness[j] >= Double.MAX_VALUE) {
					double value = Double.POSITIVE_INFINITY;
					normalisationFitness[j] = value;
				} else if (maxFitness[j] - minFitness[j] <= 0) {
					System.out.println("Error! maxFitnessCase-minFitnessCase could not be 0.");
				} else {
					double value = (multiFitness[j] - minFitness[j]) / (maxFitness[j] - minFitness[j]);
					normalisationFitness[j] = value;
				}
			}
			((MultiObjectiveFitness)oldinds[i].fitness).objectives = normalisationFitness;
//			this.normalisationFitness.add(normalisationFitness);
		}
	}

	public void updateMinMaxFitness(double[] minFitness, double[] maxFitness){
		this.minFitnessGen = minFitness;
		this.maxFitnessGen = maxFitness;
	}
	
}
