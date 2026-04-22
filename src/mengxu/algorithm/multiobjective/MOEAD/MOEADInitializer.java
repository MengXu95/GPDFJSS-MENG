package mengxu.algorithm.multiobjective.MOEAD;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPInitializer;
import ec.multiobjective.MOEAD.IndexDistancePair;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import mengxu.util.BoxOutliers;


import java.util.*;

public class MOEADInitializer extends GPInitializer {

	// settings for MOEAD
	public enum NeighborType {NEIGHBOR, POPULATION}
	public double[] idealPoint;
	public double[] maxObjectives; //2022.09.20
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
//	protected double alpha = 0.8;
//	public  boolean useNormalisation = false;

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

		// Initialise the reference point
		initIdealPoint();
		initMaxObjectives();//2021.10.18
		multiBoxOutliers = new ArrayList<>(); //2021.11.03

//		if(tchebycheff)
//			initIdealPoint();
		// Create a set of uniformly spread weight vectors
		weights = new double[popSize][numObjectives];
		initWeights();
//		initWeights_new();
		// Identify the neighboring weights for each vector
		neighbourhood = new int[popSize][neighborSize];
		initializeNeighborhood(); //modified by mengxu 2021.08.11
//		identifyNeighbourWeights(); //original by fangfang
	}

	public double calculateTchebycheffScore(Individual ind, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
		double max_fun = -1 * Double.MAX_VALUE;

		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;

		for (int i = 0; i < numObjectives; i++) {
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(nadirPoint[i] - idealPoint[i]);
			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
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
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(nadirPoint[i] - idealPoint[i]);
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
			double diff = fit.getObjectives()[i];
			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			sum += (problemWeights[i]) * diff;
		}

		return sum;
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
			idealPoint[i] = 0.0; //original by Fangfang //advice to use [0.0, 0.0] by Fangfang and Bach
//			idealPoint[i] = Double.POSITIVE_INFINITY; //modified by mengxu based on Jmetal
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
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((MOEADMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < idealPoint.length; i++) {
					if (this.idealPoint[i] > objectives[i]) {
						this.idealPoint[i] = objectives[i]; //original
//						this.idealPoint[i] = objectives[i] * alpha; //modified by MengXu based on paper "MOEA/D for Flowshop Scheduling Problems"

					}
				}
			}
	}

	public void updateIdealPointForEachGeneration(EvolutionState state) {
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(int i = 0; i < idealPoint.length; i++){
			this.idealPoint[i] = Double.POSITIVE_INFINITY;
		}//add by mengxu 2022.08.22
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((MOEADMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < idealPoint.length; i++) {
				if (this.idealPoint[i] > objectives[i]) {
					this.idealPoint[i] = objectives[i]; //original
//						this.idealPoint[i] = objectives[i] * alpha; //modified by MengXu based on paper "MOEA/D for Flowshop Scheduling Problems"

				}
			}
		}
	}

	/**
	 * Update the ideal point add by mengxu 2021.11.03. Used after each population evaluation.
	 */
	public void updateIdealPoint() {
		//this is just suitable for one subpop.
		for(int i=0; i<numObjectives; i++){
			this.idealPoint[i] = multiBoxOutliers.get(i).getMin();//original
//			this.idealPoint[i] = multiBoxOutliers.get(i).getMin() * alpha; //modified by MengXu based on paper "MOEA/D for Flowshop Scheduling Problems"
		}
	}

	public void updateIdealPoint(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((MOEADMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < idealPoint.length; i++) {
			if (this.idealPoint[i] > objectives[i]) {
				this.idealPoint[i] = objectives[i];//original
//				this.idealPoint[i] = objectives[i] * alpha; //modified by MengXu based on paper "MOEA/D for Flowshop Scheduling Problems"
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
			double[] objectives = ((MOEADMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < maxObjectives.length; i++) {
				if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
					if (this.maxObjectives[i] < objectives[i]) {
						this.maxObjectives[i] = objectives[i];
					}
				}
			}
		}
	}

	//add by mengxu 2022.08.22
	public void updateMaxObjectivesForNewGeneration(EvolutionState state) {
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		for(int i = 0; i < maxObjectives.length; i++){
			this.maxObjectives[i] = 0;
		}//add by mengxu 2022.08.22
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((MOEADMultiObjectiveFitness)individual.fitness).objectives;
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
	public void updateMaxObjectives() {
		//this is just suitable for one subpop.
		for(int i=0; i<numObjectives; i++){
			this.maxObjectives[i] = multiBoxOutliers.get(i).getMaxInRegion();
		}
	}

	public void updateMaxObjectives(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((MOEADMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < maxObjectives.length; i++) {
			if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
				if (this.maxObjectives[i] < objectives[i]) {
					this.maxObjectives[i] = objectives[i];
				}
			}
		}
	}

	/**
	 * Normalise the objectives of individuals based on the ideal points and maximal points in region
	 * add by mengxu 2022.09.01
	 */
	public void normalisePopulation(EvolutionState state){
		Individual[] individuals = state.population.subpops[0].individuals;
		for(int i=0; i<individuals.length; i++){
			MultiObjectiveFitness fitness = (MultiObjectiveFitness)individuals[i].fitness;
			for(int j=0; j<fitness.getNumObjectives(); j++){
				fitness.objectives[j] = (fitness.objectives[j]-idealPoint[j])/(maxObjectives[j]-idealPoint[j]);
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
		for (int i = 0; i < popSize; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = i / (double) (popSize-1);
				weightVector[1] = (popSize -1 - i) / (double) (popSize-1);
//				weightVector[0] = (i * interval) / (double) popSize;
//				weightVector[1] = (popSize - (i * interval)) / (double) popSize;
				weights[i] = weightVector;
			} else if (numObjectives == 3) {
				for (int j = 0; j < popSize; j++) {
					if (i + j < popSize) {
						int k = popSize - i - j;
						double[] weightVector = new double[3];
						weightVector[0] = i / (double) popSize;
						weightVector[1] = j / (double) popSize;
						weightVector[2] = k / (double) popSize;
						weights[i] = weightVector;
					}
				}
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
