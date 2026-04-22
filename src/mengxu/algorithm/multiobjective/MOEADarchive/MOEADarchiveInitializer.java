package mengxu.algorithm.multiobjective.MOEADarchive;

import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.gp.GPInitializer;
import ec.multiobjective.MOEAD.IndexDistancePair;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import mengxu.util.BoxOutliers;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.basic.FCFS;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.util.*;

public class MOEADarchiveInitializer extends GPInitializer {

	// settings for MOEAD
	public enum NeighborType {NEIGHBOR, POPULATION}
	public double[] idealPoint;
	public double[] maxObjectives; //2022.09.20
	public double[] nadirPoint;
	public double[][] weights;
	public int[][] neighbourhood;
	public boolean tchebycheff;
	public int numObjectives;
	public int popSize;
	public int neighborSize;
	protected double neighborhoodSelectionProbability;
	/** nr in Zhang & Li paper */
	protected int maximumNumberOfReplacedSolutions;
//	public static int numLocalSearchTries;
//	public static int localSearchBound;

	private List<BoxOutliers> multiBoxOutliers;
	protected double alpha;
	public boolean normalisation;
	public boolean normalisationBias; //add by mengxu 2023.01.24
	public int topNumber; //add by mengxu 2023.01.24
	public int biasToObjective; //add by mengxu 2023.01.24
	public double biasLevel; //add by mengxu 2023.01.24

	public boolean manualRuleNormalisation; // add by mengxu 2022.10.02

	public boolean bestObjectiveNormalisation; // add by mengxu 2022.10.02

	public AbstractRule normalisationSequencingRule = new FCFS(RuleType.SEQUENCING); // add by mengxu 2022.10.02
	public AbstractRule normalisationRoutingRule = new FCFS(RuleType.ROUTING); // add by mengxu 2022.10.02
	public double[] manualRuleObjectives;

	public RealMatrix curSchedulingSetObjectiveLowerBoundMtx;// add by mengxu 2022.10.02
	public boolean useBias;
	public boolean normaliseBeforeBiasBalance; //add by mengxu 2022.09.01
	public boolean useArchive; //add by mengxu 2022.09.01
	public boolean archiveNoPCDuplicated; //add by mengxu 2022.09.01

	public boolean useBoxOutliers; //add by mengxu 2022.09.01
	public boolean useMapping;

	public boolean useMapAngle; //add by mengxu 2022.09.13

	public boolean multiDiff; //add by mengxu 2022.09.01

	public double[] bestFitOfSubProblem; //add by mengxu 2022.08.08
	protected double[] biasBalanceForObjectives; //add by mengxu 2022.08.08

	public boolean useOddEvenStrategy;

	public void setup(final EvolutionState state, final Parameter base) {
		super.setup(state, base);

		Parameter tchebycheffParam = new Parameter("tchebycheff");
		Parameter numObjectivesParam = new Parameter("eval.problem.eval-model.objectives");
		Parameter popSizeParam = new Parameter("pop.subpop.0.size");
		Parameter numNeighboursParam = new Parameter("numNeighbours");
		Parameter neighborhoodSelectionPro = new Parameter("neighborhoodSelectionProbability");
		Parameter maximumNumOfRS = new Parameter("maximumNumberOfReplacedSolutions");
//		Parameter numLocalSearchTriesParam = new Parameter("numLocalSearchTries");
//		Parameter localSearchBoundParam = new Parameter("localSearchBound");

		// Initializations for MOEAD settings
		tchebycheff = state.parameters.getBoolean(tchebycheffParam, null, false);
		numObjectives = state.parameters.getInt(numObjectivesParam, null);
		popSize = state.parameters.getInt(popSizeParam, null);
		neighborSize = state.parameters.getInt(numNeighboursParam, null);
		neighborhoodSelectionProbability = state.parameters.getDouble(neighborhoodSelectionPro, null);
		maximumNumberOfReplacedSolutions = state.parameters.getInt(maximumNumOfRS, null);
//		numLocalSearchTries = state.parameters.getInt(numLocalSearchTriesParam, null);
//		localSearchBound = state.parameters.getInt(localSearchBoundParam, null);

		//add by mengxu 2022.09.07=================================================
		Parameter alphaValue = new Parameter("alpha");
		alpha = state.parameters.getDouble(alphaValue, null);
		Parameter ifNormalisation = new Parameter("normalisation");
		normalisation = state.parameters.getBoolean(ifNormalisation, null, false);
		Parameter ifNormalisationBias = new Parameter("normalisation-bias");
		normalisationBias = state.parameters.getBoolean(ifNormalisationBias, null, false);
		Parameter topNumberforBias = new Parameter("normalisation-bias-top-number"); //add by mengxu 2023.01.24
		topNumber = state.parameters.getInt(topNumberforBias, null); //add by mengxu 2023.01.24
		Parameter biasLevelValue = new Parameter("normalisation-bias-level"); //add by mengxu 2023.01.24
		biasLevel = state.parameters.getDouble(biasLevelValue, null); //add by mengxu 2023.01.24
		Parameter usingManualRuleNormalisation = new Parameter("manual-rule-normalisation"); //add by mengxu 2022.10.02
		manualRuleNormalisation = state.parameters.getBoolean(usingManualRuleNormalisation, null, false);
		Parameter usingBestObjectiveNormalisation = new Parameter("best-objective-normalisation"); //add by mengxu 2022.10.02
		bestObjectiveNormalisation = state.parameters.getBoolean(usingBestObjectiveNormalisation, null, false);
		Parameter UseBias = new Parameter("use-bias");
		useBias = state.parameters.getBoolean(UseBias, null, false);
		Parameter NormaliseBeforeBias = new Parameter("normalise-before-bias");
		normaliseBeforeBiasBalance = state.parameters.getBoolean(NormaliseBeforeBias, null, false);
		Parameter USEArchive = new Parameter("use-archive");
		useArchive = state.parameters.getBoolean(USEArchive, null, false);
		Parameter archiveNoPCD = new Parameter("archive-pc");
		archiveNoPCDuplicated = state.parameters.getBoolean(archiveNoPCD, null, false);
		Parameter USEmap = new Parameter("use-map");
		useMapping = state.parameters.getBoolean(USEmap, null, false);
		Parameter USEmapAngle = new Parameter("use-map-angle");
		useMapAngle = state.parameters.getBoolean(USEmapAngle, null, false);
		Parameter MULTIDiff = new Parameter("multiply-diff");
		multiDiff = state.parameters.getBoolean(MULTIDiff, null, false);
		Parameter USEOddAndEven = new Parameter("use-odd-even"); //add by mengxu 2022.09.19
		useOddEvenStrategy = state.parameters.getBoolean(USEOddAndEven, null, false);
		Parameter USEBoxOutlier = new Parameter("use-box-outlier"); //add by mengxu 2022.09.19
		useBoxOutliers = state.parameters.getBoolean(USEBoxOutlier, null, false);
		//add by mengxu 2022.09.07=================================================

		// Initialise the reference point
		initIdealPoint();
		initMaxObjectives();//2021.10.18
		initNadirPoint();
		multiBoxOutliers = new ArrayList<>(); //2021.11.03

		bestFitOfSubProblem = new double[popSize];//add by mengxu 2022.08.08
		biasBalanceForObjectives = new double[numObjectives];
		for(int i=0; i<numObjectives; i++){//add by mengxu 2022.08.08
			biasBalanceForObjectives[i] = 1;
		}


//		if(tchebycheff)
//			initIdealPoint();
		// Create a set of uniformly spread weight vectors
		weights = new double[popSize][numObjectives];
//		initWeights();
		if(useOddEvenStrategy){
			initOddAndEvenWeights();
		}else{
			initWeights();
		}
//		initWeights_new();
		// Identify the neighboring weights for each vector
		neighbourhood = new int[popSize][neighborSize];
		initializeNeighborhood(); //modified by mengxu 2021.08.11
//		identifyNeighbourWeights(); //original by fangfang

		if(normalisation && manualRuleNormalisation){ //add by mengxu 2022.10.02
			manualRuleObjectives = new double[numObjectives];
			curSchedulingSetObjectiveLowerBoundMtx = new Array2DRowRealMatrix(numObjectives, 1);
		}
	}

	/**add by mengxu 2022.08.08
	 * Used to map the initialisation population to subproblems
	 * implemented based on Juan Li's paper
	 * DMOEA-εC: Decomposition-Based Multi-objective Evolutionary Algorithm with the ε-Constraint Framework
	 * http://dx.doi.org/10.1109/TEVC.2017.2671462
	 * @param state
	 */
	public Individual[] solutionToSubProblemMatching(EvolutionState state, int subpop, int popSize){
		Population oldPop = (Population) state.population;
		Individual[] oldInds = new Individual[popSize];
//		Individual[] oldInds = Arrays.copyOfRange(oldPop.subpops[subpop].individuals,0,popSize);
		Individual[] oldIndsCopy = oldPop.subpops[subpop].individuals.clone();
		List<Individual> allBadIndividuals =  new ArrayList<>();

		int[] permutation = new int[oldInds.length];
		MOEADUtils.randomPermutation(state, 0, permutation, oldInds.length);

		for(int i=0; i<oldInds.length; i++){
			int probId = permutation[i];
			double min = Double.POSITIVE_INFINITY;
			int bestIndID = -1;
			for(int j=0; j<oldIndsCopy.length; j++){
				if(i == 0)
				{
					MultiObjectiveFitness fitness = (MultiObjectiveFitness) oldIndsCopy[j].fitness;
					boolean allBad = true;
					for(int n=0; n<fitness.getNumObjectives(); n++){
						if(fitness.objectives[n] >= Double.POSITIVE_INFINITY || fitness.objectives[n] >= Double.MAX_VALUE){
							continue;
						}
						else{
							allBad = false;
						}
					}
					if(allBad){
						allBadIndividuals.add(oldIndsCopy[j]);
						oldIndsCopy[j] = null;
					}
				}
				double dis = 0;
				if(oldIndsCopy[j] != null){
					dis = calculateWeightSumScore(oldIndsCopy[j], probId);
					if(dis <= min){
						min = dis;
						bestIndID = j;
					}
					//matching do not care about using tchebycheff or using weightsum add by mengxu 2023.01.10
//					if(this.tchebycheff){
//						if(useOddEvenStrategy){
//							dis = calculateTchebycheffScoreBasedOnOddAndEven(oldIndsCopy[j], probId);
//						}
//						else{
//							dis = calculateTchebycheffScore(oldIndsCopy[j], probId);
//						}
//						if(dis <= min){
//							min = dis;
//							bestIndID = j;
//						}
//					}
//					else{
//						dis = calculateWeightSumScore(oldIndsCopy[j], probId);
//						if(dis <= min){
//							min = dis;
//							bestIndID = j;
//						}
//					}
				}
			}
			if(bestIndID == -1){
				//todo: currently I use random strategy, we can also use new generate individual.
				//Strategy 1: random
				int randomIndex = state.random[0].nextInt(allBadIndividuals.size());
				oldInds[probId] = allBadIndividuals.get(randomIndex);
				allBadIndividuals.remove(randomIndex);

				//Strategy 2: new generate individual
//				((GPIndividual)oldInds[probId]).trees[0].buildTree(state, 0);
//				((GPIndividual)oldInds[probId]).trees[1].buildTree(state, 0);
				//todo: not a very good strategy as we need to reevaluate and give a fitness fot the new individuals, as we need to use parent selection later
				//todo: we might use surrogate model to give a fitness???

			}
			else{
				oldInds[probId] = (Individual) oldIndsCopy[bestIndID].clone();
				oldIndsCopy[bestIndID] = null;
			}
		}
		state.population.subpops[0].individuals = oldInds;
		return oldInds;
	}


//	2022.10.07 by mengxu to mapping individuals with subproblems based on angle
	public Individual[] solutionToSubProblemMatchingBasedOnAngle(EvolutionState state, int subpop, int popSize){
		Population oldPop = (Population) state.population;
		Individual[] oldInds = Arrays.copyOfRange(oldPop.subpops[subpop].individuals,0,popSize);
		Individual[] oldIndsCopy = oldPop.subpops[subpop].individuals.clone();
		List<Individual> allBadIndividuals =  new ArrayList<>();

		int[] permutation = new int[oldInds.length];
		MOEADUtils.randomPermutation(state, 0, permutation, oldInds.length);

		for(int i=0; i<oldInds.length; i++){
			int probId = permutation[i];
			double min = Double.POSITIVE_INFINITY;
			int bestIndID = -1;
			for(int j=0; j<oldIndsCopy.length; j++){
				if(i == 0)
				{
					MultiObjectiveFitness fitness = (MultiObjectiveFitness) oldIndsCopy[j].fitness;
					boolean allBad = true;
					for(int n=0; n<fitness.getNumObjectives(); n++){
						if(fitness.objectives[n] >= Double.POSITIVE_INFINITY || fitness.objectives[n] >= Double.MAX_VALUE){
							continue;
						}
						else{
							allBad = false;
						}
					}
					if(allBad){
						allBadIndividuals.add(oldIndsCopy[j]);
						oldIndsCopy[j] = null;
					}
				}
				double dis = 0;
				if(oldIndsCopy[j] != null){
					if(this.tchebycheff){
						dis = mappingDisBasedOnAngle(oldIndsCopy[j], probId);
//						if(useOddEvenStrategy){
//							dis = calculateTchebycheffScoreBasedOnOddAndEven(oldIndsCopy[j], probId);
//						}
//						else{
//							dis = calculateTchebycheffScore(oldIndsCopy[j], probId);
//						}
						if(dis <= min){
							min = dis;
							bestIndID = j;
						}
					}
					else{
						dis = mappingDisBasedOnAngle(oldIndsCopy[j], probId);
//						dis = calculateWeightSumScore(oldIndsCopy[j], probId);
						if(dis <= min){
							min = dis;
							bestIndID = j;
						}
					}
				}
			}
			if(bestIndID == -1){
				//todo: currently I use random strategy, we can also use new generate individual.
				//Strategy 1: random
				int randomIndex = state.random[0].nextInt(allBadIndividuals.size());
				oldInds[probId] = allBadIndividuals.get(randomIndex);
				allBadIndividuals.remove(randomIndex);

				//Strategy 2: new generate individual
	//				((GPIndividual)oldInds[probId]).trees[0].buildTree(state, 0);
	//				((GPIndividual)oldInds[probId]).trees[1].buildTree(state, 0);
				//todo: not a very good strategy as we need to reevaluate and give a fitness fot the new individuals, as we need to use parent selection later
				//todo: we might use surrogate model to give a fitness???

			}
			else{
				oldInds[probId] = (Individual) oldIndsCopy[bestIndID].clone();
				oldIndsCopy[bestIndID] = null;
			}
		}
		state.population.subpops[subpop].individuals = oldInds;
		return oldInds;
	}

	/**add by mengxu 2022.08.08
	 * Used to map the generated offspring to subproblems
	 * implemented based on Juan Li's paper
	 * DMOEA-εC: Decomposition-Based Multi-objective Evolutionary Algorithm with the ε-Constraint Framework
	 * http://dx.doi.org/10.1109/TEVC.2017.2671462
	 * I modified which is different when calculate the min value.
	 * @param state
	 */
	public int subProblemToSolutionMatching(EvolutionState state, Individual offspring){
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;
		int bestProbID = -1;

		double[] offspringObjective = ((MOEADarchiveMultiObjectiveFitness)offspring.fitness).objectives;
		if(offspringObjective[0] >= Double.POSITIVE_INFINITY || offspringObjective[0] >= Double.MAX_VALUE){
			bestProbID = state.random[0].nextInt(oldInds.length);
			oldInds[bestProbID] = (Individual)offspring.clone();
			state.population.subpops[0].individuals = oldInds;
			return bestProbID;
		}

		for(int i=0; i<oldInds.length; i++){
			int probId = i;
			double min = Double.POSITIVE_INFINITY;
			bestProbID = -1;
			double dis = 0;
			if(this.tchebycheff){
				if(useOddEvenStrategy){
					dis = calculateTchebycheffScoreBasedOnOddAndEven(offspring, probId);
				}
				else{
					dis = calculateTchebycheffScore(offspring, probId);
				}
				if(dis <= min){
					min = dis;
					bestProbID = probId;
				}
			}
			else{
				dis = calculateWeightSumScore(offspring, probId);
				if(dis <= min){
					min = dis;
					bestProbID = probId;
				}
			}
		}
		oldInds[bestProbID] = (Individual)offspring.clone();
		state.population.subpops[0].individuals = oldInds;
		return bestProbID;
	}

	public double mappingDisBasedOnAngle(Individual individual, int subProblemID){
		double[] problemWeights = weights[subProblemID];
		double[] fit = ((MultiObjectiveFitness)individual.fitness).getObjectives();
		double dis = 0;
		if(this.tchebycheff){
			for(int j=0; j<numObjectives; j++){
				for(int m=j+1; m<numObjectives; m++){
					if(j != m){
						if(useOddEvenStrategy && subProblemID%2 == 0){
							double angle = Math.atan(Math.abs(fit[j] - idealPoint[j])/Math.abs(fit[m] - idealPoint[m]));
							if(fit[j] == idealPoint[j] && fit[m] == idealPoint[m]){
								angle = 1.0;
							}
							double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
							dis += Math.abs(angle - idealAngle);
						}
						else if(useOddEvenStrategy && subProblemID%2 == 1){
							double angle = Math.atan(Math.abs(nadirPoint[j] - fit[j])/Math.abs(nadirPoint[m] - fit[m]));
							if(fit[j] == nadirPoint[j] && fit[m] == nadirPoint[m]){
								angle = 1.0;
							}
							double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
							dis += Math.abs(angle - idealAngle);
						}
						else if(normalisation && manualRuleNormalisation){//add by mengxu 2022.10.06
							double angle = Math.atan(Math.abs(fit[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) - idealPoint[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0))/Math.abs(fit[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0) - idealPoint[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0)));
							if(fit[j] == idealPoint[j] && fit[m] == idealPoint[m]){
								angle = 1.0;
							}
							double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
							dis += Math.abs(angle - idealAngle);
						}
						else if(normalisation && bestObjectiveNormalisation){//add by mengxu 2022.10.06
							double angle = Math.atan(Math.abs(fit[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) - idealPoint[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0))/Math.abs(fit[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0) - idealPoint[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0)));
							if(fit[j] == idealPoint[j] && fit[m] == idealPoint[m]){
								angle = 1.0;
							}
							double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
							dis += Math.abs(angle - idealAngle);
						}
						else{
							double angle = Math.atan(Math.abs(fit[j] - idealPoint[j])/Math.abs(fit[m] - idealPoint[m]));
							if(fit[j] == idealPoint[j] && fit[m] == idealPoint[m]){
								angle = 1.0;
							}
							double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
//							if(Double.isNaN(angle) || Double.isNaN(idealAngle)){
//								System.out.println("Error here!");
//							}
							dis += Math.abs(angle - idealAngle);
						}
					}
				}
			}
		}
		else{
			for(int j=0; j<numObjectives; j++){
				for(int m=j+1; m<numObjectives; m++){
					if(j != m){
						double angle = Math.atan(Math.abs(fit[j])/Math.abs(fit[m]));
						double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
//							System.out.println("idealAngle: " + idealAngle);
//							if(problemWeights[m] == 0){
//								idealAngle = Math.atan(Double.POSITIVE_INFINITY);
//								System.out.println("idealAngle: " + idealAngle);
//							}
						dis += Math.abs(angle - idealAngle);
					}
				}
			}
		}
		return dis;
	}

	/**add by mengxu 2022.09.13
	 * Used to map the generated offspring to subproblems Version 2
	 * implemented based on Juan Li's paper
	 * DMOEA-εC: Decomposition-Based Multi-objective Evolutionary Algorithm with the ε-Constraint Framework
	 * http://dx.doi.org/10.1109/TEVC.2017.2671462
	 * I modified which is different when calculate the min value.
	 * @param state
	 */
	public int subProblemToSolutionMatchingBasedOnAngle(EvolutionState state, Individual offspring){
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;
		int bestProbID = -1;

		double[] offspringObjective = ((MOEADarchiveMultiObjectiveFitness)offspring.fitness).objectives;
		if(offspringObjective[0] >= Double.POSITIVE_INFINITY || offspringObjective[0] >= Double.MAX_VALUE){
			bestProbID = state.random[0].nextInt(oldInds.length);
			oldInds[bestProbID] = (Individual)offspring.clone();
			state.population.subpops[0].individuals = oldInds;
			return bestProbID;
		}

		for(int i=0; i<oldInds.length; i++){
			int probId = i;
			double[] problemWeights = weights[probId];
			double min = Double.POSITIVE_INFINITY;
			bestProbID = -1;
			double dis = 0;
			if(this.tchebycheff){
				for(int j=0; j<numObjectives; j++){
					for(int m=j+1; m<numObjectives; m++){
						if(j != m){
							if(useOddEvenStrategy && i%2 == 0){
								double angle = Math.atan(Math.abs(offspringObjective[j] - idealPoint[j])/Math.abs(offspringObjective[m] - idealPoint[m]));
								if(offspringObjective[j] == idealPoint[j] && offspringObjective[m] == idealPoint[m]){
									angle = 1.0;
								}
								double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
								dis += Math.abs(angle - idealAngle);
							}
							else if(useOddEvenStrategy && i%2 == 1){
								double angle = Math.atan(Math.abs(nadirPoint[j] - offspringObjective[j])/Math.abs(nadirPoint[m] - offspringObjective[m]));
								if(offspringObjective[j] == nadirPoint[j] && offspringObjective[m] == nadirPoint[m]){
									angle = 1.0;
								}
								double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
								dis += Math.abs(angle - idealAngle);
							}
							else if(normalisation && manualRuleNormalisation){//add by mengxu 2022.10.06
								double angle = Math.atan(Math.abs(offspringObjective[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) - idealPoint[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0))/Math.abs(offspringObjective[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0) - idealPoint[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0)));
								if(offspringObjective[j] == idealPoint[j] && offspringObjective[m] == idealPoint[m]){
									angle = 1.0;
								}
								double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
								dis += Math.abs(angle - idealAngle);
							}
							else if(normalisation && bestObjectiveNormalisation){//add by mengxu 2022.10.06
								double angle = Math.atan(Math.abs(offspringObjective[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) - idealPoint[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0))/Math.abs(offspringObjective[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0) - idealPoint[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0)));
								if(offspringObjective[j] == idealPoint[j] && offspringObjective[m] == idealPoint[m]){
									angle = 1.0;
								}
								double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
								dis += Math.abs(angle - idealAngle);
							}
							else{
								double angle = Math.atan(Math.abs(offspringObjective[j] - idealPoint[j])/Math.abs(offspringObjective[m] - idealPoint[m]));
								if(offspringObjective[j] == idealPoint[j] && offspringObjective[m] == idealPoint[m]){
									angle = 1.0;
								}
								double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
//							if(Double.isNaN(angle) || Double.isNaN(idealAngle)){
//								System.out.println("Error here!");
//							}
								dis += Math.abs(angle - idealAngle);
							}
						}
					}
				}
				if(dis <= min){
					min = dis;
					bestProbID = probId;
				}
			}
			else{
				for(int j=0; j<numObjectives; j++){
					for(int m=j+1; m<numObjectives; m++){
						if(j != m){
							double angle = Math.atan(Math.abs(offspringObjective[j])/Math.abs(offspringObjective[m]));
							double idealAngle = Math.atan(problemWeights[j]/problemWeights[m]);
//							System.out.println("idealAngle: " + idealAngle);
//							if(problemWeights[m] == 0){
//								idealAngle = Math.atan(Double.POSITIVE_INFINITY);
//								System.out.println("idealAngle: " + idealAngle);
//							}
							dis += Math.abs(angle - idealAngle);
						}
					}
				}
				if(dis <= min){
					min = dis;
					bestProbID = probId;
				}
			}
		}
		oldInds[bestProbID] = (Individual)offspring.clone();
		state.population.subpops[0].individuals = oldInds;
		return bestProbID;
	}

	/**
	 * add by mengxu 2022.08.08
	 * used to avoid the bias between objectives with different range and no optimal ideal points and unbound nadir points
	 */
	public double[] getBiasBalance() {
		return biasBalanceForObjectives;
	}

	/**
	 * add by mengxu 2022.08.08
	 * used to avoid the bias between objectives with different range and no optimal ideal points and unbound nadir points
	 */
	public void setBiasBalance(double[] biasBalance) {
		this.biasBalanceForObjectives = biasBalance;
	}

	public double calculateTchebycheffScore(Individual ind, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
		double max_fun = -1 * Double.MAX_VALUE;

		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;

		for (int i = 0; i < numObjectives; i++) {
			//todo: need to change 2 parts if do normalisation similar to this
			double diff = 0;
			if(normalisation && manualRuleNormalisation){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - idealPoint[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation && bestObjectiveNormalisation && !normalisationBias){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - idealPoint[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation && bestObjectiveNormalisation && normalisationBias){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - idealPoint[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
//				if(biasToObjective == i){
//					diff = fit.getObjectives()[i]/(curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0)*biasLevel) - idealPoint[i]/(curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0)*biasLevel);
//				}
			}
			else if(normalisation && useBias){
				diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) * biasBalanceForObjectives[i] / (maxObjectives[i] - idealPoint[i]); //better than the strategy below
			}
			else if(normalisation && !useBias){
				diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(maxObjectives[i] - idealPoint[i]); //better than the strategy below
			}
			else if(!normalisation && useBias){
				diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) * biasBalanceForObjectives[i];
			}
			else if(!normalisation && !useBias){
				diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
			}

//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) * biasBalanceForObjectives[i];
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(nadirPoint[i] - idealPoint[i]); //better than the strategy below
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
//			if(i==1){ //add by MengXu 2022.06.30
//				diff = diff * 0.85;
//			}
			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			double feval;
			if (problemWeights[i] == 0) {
				feval = 0.00000001 * diff;
			}
			else{
				feval = problemWeights[i] * diff;
				if(i==0){
					feval = problemWeights[i] * diff;
				}
			}

			if (feval > max_fun)
				max_fun = feval;
		}
		return max_fun;
	}

	/**
	 * Calculates the problem score for a given individual, using a given set of
	 * weights.
	 *
	 * @param ind
	 * @param problemIndex
	 *            - for retrieving weights
	 * @return score
	 */
	public double calculateWeightSumScore(Individual ind, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;

		double sum = 0;
		for(int i = 0; i < numObjectives; i++){
			double diff = 0;
			if(normalisation && manualRuleNormalisation){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation && bestObjectiveNormalisation){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation && useBias){
				diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) * biasBalanceForObjectives[i] / (maxObjectives[i] - idealPoint[i]); //better than the strategy below
			}
			else if(normalisation && !useBias){
				diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(maxObjectives[i] - idealPoint[i]); //better than the strategy below
			}
			else if(!normalisation && useBias){
				diff = Math.abs(fit.getObjectives()[i]) * biasBalanceForObjectives[i]; //todo: traditional MOEAD do not use reference point in weight sum approach
			}
			else if(!normalisation && !useBias){
				diff = Math.abs(fit.getObjectives()[i]); //todo: traditional MOEAD do not use reference point in weight sum approach
			}

//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) * biasBalanceForObjectives[i] / (nadirPoint[i] - idealPoint[i]);//better than the one strategy below
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) * biasBalanceForObjectives[i];
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(nadirPoint[i] - idealPoint[i]);//better than the one strategy below
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
//			if(i==0){
//				sum += 2 * (problemWeights[i]) * diff;
//			}
//			else{
//				sum += (problemWeights[i]) * diff;
//			}
			sum += (problemWeights[i]) * diff;
		}

		return sum;
	}

	/**
	 * For odd and even strategy
	 * @param ind
	 * @param problemIndex
	 * @return
	 */

	public double calculateTchebycheffScoreBasedOnOddAndEven(Individual ind, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
		double max_fun = -1 * Double.MAX_VALUE;

		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;

		for (int i = 0; i < numObjectives; i++) {
			//todo: need to change 2 parts if do normalisation similar to this
			double diff = 0;
			if(normalisation && useBias){
				if(problemIndex%2 == 0){
					diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) * biasBalanceForObjectives[i] / (maxObjectives[i] - idealPoint[i]); //better than the strategy below
				}
				else{
					diff = Math.abs(nadirPoint[i] - fit.getObjectives()[i]) * biasBalanceForObjectives[i] / (maxObjectives[i] - idealPoint[i]); //better than the strategy below
					//todo: need to modify the nadirpoints, currently the nadirpoints are the maximal objectives in the generation which is not right and different with the true principle of
					//      the nadirpoints
				}
			}
			else if(normalisation && !useBias){
				if(problemIndex%2 == 0){
					diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) / (nadirPoint[i] - idealPoint[i]); //better than the strategy below
				}
				else{
					diff = Math.abs(nadirPoint[i] - fit.getObjectives()[i]) / (nadirPoint[i] - idealPoint[i]); //better than the strategy below
					//todo: need to modify the nadirpoints, currently the nadirpoints are the maximal objectives in the generation which is not right and different with the true principle of
					//      the nadirpoints
				}
			}
			else if(!normalisation && useBias){
				if(problemIndex%2 == 0){
					diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) * biasBalanceForObjectives[i];
				}
				else{
					diff = Math.abs(nadirPoint[i] - fit.getObjectives()[i]) * biasBalanceForObjectives[i]; //better than the strategy below
					//todo: need to modify the nadirpoints, currently the nadirpoints are the maximal objectives in the generation which is not right and different with the true principle of
					//      the nadirpoints
				}
			}
			else if(!normalisation && !useBias){
				if(problemIndex%2 == 0){
					diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
				}
				else{
					diff = Math.abs(nadirPoint[i] - fit.getObjectives()[i]); //better than the strategy below
					//todo: need to modify the nadirpoints, currently the nadirpoints are the maximal objectives in the generation which is not right and different with the true principle of
					//      the nadirpoints
				}
			}

//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]) * biasBalanceForObjectives[i];
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(nadirPoint[i] - idealPoint[i]); //better than the strategy below
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
//			if(i==1){ //add by MengXu 2022.06.30
//				diff = diff * 0.85;
//			}
			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			double feval;
			if (problemWeights[i] == 0)
				feval = 0.00001 * diff;
			else
				feval = problemWeights[i] * diff;
			if (feval > max_fun)
				max_fun = feval;
		}
		return max_fun;
	}


	/**
	 * update 2021.11.3 mengxu
	 * @param state
	 */
	public void updateMultiBoxOutliers(EvolutionState state){
		multiBoxOutliers.clear();
		for(int i=0; i<numObjectives; i++){
			BoxOutliers boxOutliers = new BoxOutliers();
			boxOutliers.process(state, i);
			multiBoxOutliers.add(boxOutliers);
		}
	}

	/**
	 * Create a neighborhood for each weight vector, based on the Euclidean distance
	 * between each two vectors.
	 */
	private void identifyNeighbourWeights() {
		// Calculate distance between vectors
		double[][] distanceMatrix = new double[popSize][popSize];

		for (int i = 0; i < popSize; i++) {
			for (int j = 0; j < popSize; j++) {
				if (i != j)
					distanceMatrix[i][j] = calculateDistance(weights[i], weights[j]);
			}
		}

		// Use this information to build the neighborhood
		for (int i = 0; i < popSize; i++) {
			int[] neighbours = identifyNearestNeighbours(distanceMatrix[i], i);
			neighbourhood[i] = neighbours;
		}
	}

	/**
	 * Initialize neighborhoods add by mengxu 2021.08.11
	 */
	protected void initializeNeighborhood() {
		double[] x = new double[popSize];
		int[] idx = new int[popSize];

		for (int i = 0; i < popSize; i++) {
			// calculate the distances based on weight vectors
			for (int j = 0; j < popSize; j++) {
				x[j] = MOEADUtils.distVector(weights[i], weights[j]);
				idx[j] = j;
			}

			// find 'niche' nearest neighboring subproblems
			MOEADUtils.minFastSort(x, idx, popSize, neighborSize);

			System.arraycopy(idx, 0, neighbourhood[i], 0, neighborSize);
		}
	}

	/**
	 * Returns the indices for the nearest neighbors, according to their distance
	 * from the current vector.
	 *
	 * @param distances
	 *            - a list of distances from the other vectors
	 * @param currentIndex
	 *            - the index of the current vector
	 * @return indices of nearest neighbors
	 */
	private int[] identifyNearestNeighbours(double[] distances, int currentIndex) {
		Queue<IndexDistancePair> indexDistancePairs = new LinkedList<IndexDistancePair>();

		// Convert the vector of distances to a list of index-distance pairs.
		for (int i = 0; i < distances.length; i++) {
			indexDistancePairs.add(new IndexDistancePair(i, distances[i]));
		}
		// Sort the pairs according to the distance, from lowest to highest.
		Collections.sort((LinkedList<IndexDistancePair>) indexDistancePairs);

		// Get the indices for the required number of neighbours
		int[] neighbours = new int[neighborSize];

		// Get the neighbors, including the vector itself
		IndexDistancePair neighbourCandidate;
		for (int i = 0; i < neighborSize; i++) {
			neighbourCandidate = indexDistancePairs.poll();
			// Uncomment this if you want to exclude the vector itself from being considered
			// as part of the neighbourhood
			// while (neighbourCandidate.getIndex() == currentIndex)
			// neighbourCandidate = indexDistancePairs.poll();
			neighbours[i] = neighbourCandidate.getIndex();
		}
		return neighbours;
	}

	/**
	 * Calculates the Euclidean distance between two weight vectors.
	 *
	 * @param vector1
	 * @param vector2
	 * @return distance
	 */
	private double calculateDistance(double[] vector1, double[] vector2) {
		double sum = 0;
		for (int i = 0; i < vector1.length; i++) {
			sum += Math.pow((vector1[i] - vector2[i]), 2);
		}
		return Math.sqrt(sum);
	}

	/**
	 * Initialize the ideal point used for the Tchebycheff calculation.
	 */
	private void initIdealPoint() {
		idealPoint = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++) {
			idealPoint[i] = 0.0; //original by Fangfang
//			idealPoint[i] = Double.POSITIVE_INFINITY; //modified by mengxu based on Jmetal
		}
	}

	private void initNadirPoint() {
		nadirPoint = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++) {
//			idealPoint[i] = 0.0; //original by Fangfang
			nadirPoint[i] = Double.NEGATIVE_INFINITY; //modified by mengxu based on Jmetal
		}
	}

	public void updateNadirPoint(Object[] sortedParetoFront){
		for(int f = 0; f < numObjectives; f++){
			nadirPoint[f] = Double.NEGATIVE_INFINITY;
		}
		for (int i = 0; i < sortedParetoFront.length; i++)
		{
			Individual ind = (Individual)(sortedParetoFront[i]);
			MultiObjectiveFitness mof = (MultiObjectiveFitness) (ind.fitness);
			double[] objectives = mof.getObjectives();

			for(int f = 0; f < objectives.length; f++){
				if(nadirPoint[f] < objectives[f]){
					nadirPoint[f] = objectives[f];
				}
			}
		}
	}

	public void updateIdealPoint(Object[] sortedParetoFront){
		for(int f = 0; f < numObjectives; f++){
			idealPoint[f] = Double.POSITIVE_INFINITY;
		}
		for (int i = 0; i < sortedParetoFront.length; i++)
		{
			Individual ind = (Individual)(sortedParetoFront[i]);
			MultiObjectiveFitness mof = (MultiObjectiveFitness) (ind.fitness);
			double[] objectives = mof.getObjectives();

			for(int f = 0; f < objectives.length; f++){
				if(idealPoint[f] > objectives[f]){
					idealPoint[f] = objectives[f];
				}
			}
		}
	}

	/**
	 * Initialize the nadir point. 2021.10.18
	 */
	private void initMaxObjectives() {
		maxObjectives = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++) {
			maxObjectives[i] = Double.NEGATIVE_INFINITY; //original by Fangfang
//			idealPoint[i] = Double.POSITIVE_INFINITY; //modified by mengxu based on Jmetal
		}
	}

	/**
	 * Update the ideal point add by mengxu 2021.08.11. Used after each population evaluation.
	 */
	public void updateIdealPoint(EvolutionState state) {
		RuleOptimizationProblem problem = (RuleOptimizationProblem)state.evaluator.p_problem;
		if(problem.getEvaluationModel().isRotatable()) {
			Arrays.fill(idealPoint, Double.POSITIVE_INFINITY);
		}
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((MOEADarchiveMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < idealPoint.length; i++) {
					if (this.idealPoint[i] > objectives[i]) {
//						this.idealPoint[i] = objectives[i]; //original
						this.idealPoint[i] = objectives[i] * alpha; //modified by MengXu based on paper "MOEA/D for Flowshop Scheduling Problems"

					}
				}
			}
	}

	/**
	 * Update the ideal point add by mengxu 2021.11.03. Used after each population evaluation.
	 */
	public void updateIdealPointBasedOnBoxOutlier() {
		//this is just suitable for one subpop.
		for(int i=0; i<numObjectives; i++){
//			this.idealPoint[i] = multiBoxOutliers.get(i).getMin();//original
			this.idealPoint[i] = multiBoxOutliers.get(i).getMin() * alpha; //modified by MengXu based on paper "MOEA/D for Flowshop Scheduling Problems"
		}
	}

	public void updateIdealPoint(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((MOEADarchiveMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < idealPoint.length; i++) {
			if (this.idealPoint[i] > objectives[i]) {
//				this.idealPoint[i] = objectives[i];//original
				this.idealPoint[i] = objectives[i] * alpha; //modified by MengXu based on paper "MOEA/D for Flowshop Scheduling Problems"
//				System.out.println("update one idealPoint!");
			}
		}
	}

	//2022.10.06
	public void updateCurSchedulingSetObjectiveLowerBoundMtx(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((MOEADarchiveMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < objectives.length; i++) {
			if (this.curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) > objectives[i]) {
//				this.idealPoint[i] = objectives[i];//original
				this.curSchedulingSetObjectiveLowerBoundMtx.setEntry(i,0,objectives[i]);
//				System.out.println("update one idealPoint!");
			}
		}
	}

	/**
	 * Update the ideal point add by mengxu 2021.08.11. Used after each population evaluation.
	 */
	public void updateMaxObjectives(EvolutionState state) {
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((MOEADarchiveMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < maxObjectives.length; i++) {
				if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
					if (this.maxObjectives[i] < objectives[i]) {
						this.maxObjectives[i] = objectives[i];
					}
				}
			}
		}
	}

	/**
	 * Update the nadir point add by mengxu 2021.11.03. Used after each population evaluation.
	 */
	public void updateMaxObjectivesBasedOnBoxOutlier() {
		//this is just suitable for one subpop.
		for(int i=0; i<numObjectives; i++){
			this.maxObjectives[i] = multiBoxOutliers.get(i).getMaxInRegion();
		}
	}

	public void updateMaxObjectives(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((MOEADarchiveMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < maxObjectives.length; i++) {
			if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
				if (this.maxObjectives[i] < objectives[i]) {
					this.maxObjectives[i] = objectives[i];
				}
			}
		}
	}

	/**
	 * Initialize uniformely spread weight vectors. This code come from the authors'
	 * original code base.
	 */
	private void initWeights_new() {

		for (int i = 1; i <= popSize; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = (i - 1) / (double) (popSize - 1);
				weightVector[1] = (popSize - i) / (double) (popSize - 1);
				weights[i - 1] = weightVector;
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}
	}

	/**
	 * Initialize weight vectors add by mengxu 2021.08.11
	 */
	protected void initializeUniformWeight() {
		if (numObjectives == 2) {
			for (int n = 0; n < popSize; n++) {
				double a = 1.0 * n / (double)(popSize - 1);
				weights[n][0] = a;
				weights[n][1] = 1 - a;
			}
		} else {
			initWeights();//modified by mengxu.
		}
	}

	/**
	 * Initialize uniformely spread weight vectors. This code come from the authors'
	 * original code base.
	 */
	private void initWeights() {
		if(numObjectives == 2) {
			for (int i = 0; i < popSize; i++) {
				double[] weightVector = new double[2];
				weightVector[0] = i / (double) (popSize - 1);
				weightVector[1] = (popSize - 1 - i) / (double) (popSize - 1);
//				weightVector[0] = (i * interval) / (double) popSize;
//				weightVector[1] = (popSize - (i * interval)) / (double) popSize;
				weights[i] = weightVector;
			}
		} else if (numObjectives == 3) { //todo: need to double check and based on references to give the weights for 3-objectives 2023.01.13 by mengxu
			int H = calculateHbasedOnNandM(popSize, numObjectives);
			System.out.println("H: " + H);
			int t = 0;
//			while(t<popSize) {
				for (int i = 0; i <= H; i++) {
					for (int j = 0; j <= H; j++) {
						if (i + j <= H) {
							int k = H - i - j;
							double[] weightVector = new double[3];
							weightVector[0] = i / (double) H;
							weightVector[1] = j / (double) H;
							weightVector[2] = k / (double) H;
							if(t<popSize){
								weights[t] = weightVector;
								t++;
							}
							else{
								break;
							}
						}
					}
				}
//			}
		}
		else{
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
		}

//		for (int i = 0; i < popSize; i++) {
//			if (numObjectives == 2) {
//				double[] weightVector = new double[2];
//				weightVector[0] = i / (double) (popSize-1);
//				weightVector[1] = (popSize -1 - i) / (double) (popSize-1);
////				weightVector[0] = (i * interval) / (double) popSize;
////				weightVector[1] = (popSize - (i * interval)) / (double) popSize;
//				weights[i] = weightVector;
//			} else if (numObjectives == 3) { //todo: need to double check and based on references to give the weights for 3-objectives 2023.01.13 by mengxu
//				int H = calculateHbasedOnNandM(popSize,numObjectives);
//				for (int j = 0; j < H; j++) {
//					if (i + j < H) {
//						int k = H - i - j;
//						double[] weightVector = new double[3];
//						weightVector[0] = i / (double) popSize;
//						weightVector[1] = j / (double) popSize;
//						weightVector[2] = k / (double) popSize;
//						weights[i] = weightVector;
//					}
//				}
//			} else {
//				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
//			}
//		}
	}

	/**
	 * 2023.01.13 by mengxu
	 * This is used to decide the H for weight vectors based on popsize N and number of objective m
	 * based on function [W,N] = NBI(N,M) from this link: https://github.com/BIMK/PlatEMO/blob/master/PlatEMO/Algorithms/Utility%20functions/UniformPoint.m
	 * @return
	 */

	private int calculateHbasedOnNandM(int N, int M){
		int H = 1;
		while (nchoosek(H+M,M-1) <= N){
			H = H + 1;
		}
		return H;
	}

	private int nchoosek(int a, int b){ //todo: need to check and test if this is right 2022.01.13
		int big = 1;
		int small = 1;
		for(int i=0;i<b;i++){
			big = big * a;
			a--;
		}
		for(;b>0;b--){
			small = small * b;
		}
		return big/small;
	}


	/**
	 * This strategy is from paper "An improved MOEA/D algorithm for bi-objective optimization problems with complex Pareto fronts and its application to structural optimization"
	 * todo: need to implement and check if the implement is right as the original paper does not introduce clearly!
	 */
	private void initOddAndEvenWeights() {
		for (int i = 0; i < popSize-1; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = (double)i/2 / (double) (popSize/2-1);
				weightVector[1] = ((double)popSize/2 - 1 - (double)i/2) / (double) (popSize/2-1);
//				weightVector[0] = (i * interval) / (double) popSize;
//				weightVector[1] = (popSize - (i * interval)) / (double) popSize;
				weights[i] = weightVector;
				weights[i+1] = weightVector;
				i++;
			} else if (numObjectives == 3) {
//				for (int j = 0; j < popSize; j++) {
//					if (i + j < popSize) {
//						int k = popSize - i - j;
//						double[] weightVector = new double[3];
//						weightVector[0] = i / (double) popSize;
//						weightVector[1] = j / (double) popSize;
//						weightVector[2] = k / (double) popSize;
//						weights[i] = weightVector;
//					}
//				}
				//todo: check this strategy with two-objective firstly and then need to implement this later!
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}
	}

	public double getNeighborhoodSelectionProbability() {
		return neighborhoodSelectionProbability;
	}

	public int getMaximumNumberOfReplacedSolutions() {
		return maximumNumberOfReplacedSolutions;
	}
}
