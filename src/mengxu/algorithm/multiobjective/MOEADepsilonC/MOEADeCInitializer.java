package mengxu.algorithm.multiobjective.MOEADepsilonC;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPInitializer;
import ec.multiobjective.MOEAD.IndexDistancePair;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import mengxu.util.BoxOutliers;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

public class MOEADeCInitializer extends GPInitializer {

	// settings for MOEAD
	public enum NeighborType {NEIGHBOR, POPULATION}
	public double[] idealPoint;
	public double[] nadirPoint; //2021.10.18
	public List<SubProblem> allSubProblem; //2021.11.10
	public double delta;
//	public double[][] weights;
	public int[][] neighbourhood;
	public boolean tchebycheff;
	public int numObjectives;
	public int popSize;
	public int neighborSize;
	protected double neighborhoodSelectionProbability;
	/** nr in Zhang & Li paper */
	protected int maximumNumberOfReplacedSolutions;


	private List<BoxOutliers> multiBoxOutliers;

	public void setup(final EvolutionState state, final Parameter base) {
		super.setup(state, base);

		Parameter tchebycheffParam = new Parameter("tchebycheff");
		Parameter numObjectivesParam = new Parameter("eval.problem.eval-model.objectives");
		Parameter popSizeParam = new Parameter("pop.subpop.0.size");
		Parameter numNeighboursParam = new Parameter("numNeighbours");
		Parameter neighborhoodSelectionPro = new Parameter("neighborhoodSelectionProbability");
		Parameter maximumNumOfRS = new Parameter("maximumNumberOfReplacedSolutions");
		Parameter deltaParam = new Parameter("delta");
//		Parameter intervalNumParam = new Parameter("interval-number"); //2021.11.10

		// Initializations for MOEAD settings
		tchebycheff = state.parameters.getBoolean(tchebycheffParam, null, false);
		numObjectives = state.parameters.getInt(numObjectivesParam, null);
		popSize = state.parameters.getInt(popSizeParam, null);
		neighborSize = state.parameters.getInt(numNeighboursParam, null);
		neighborhoodSelectionProbability = state.parameters.getDouble(neighborhoodSelectionPro, null);
		maximumNumberOfReplacedSolutions = state.parameters.getInt(maximumNumOfRS, null);
		delta = state.parameters.getDouble(deltaParam, null); //2021.11.10
//		intervalNum = state.parameters.getDouble(intervalNumParam, null); //2021.11.10

		// Initialise the reference point
		initIdealPoint();
		initNadirPoint();//2021.10.18
		multiBoxOutliers = new ArrayList<>(); //2021.11.03

		// Create a set of uniformly spread weight vectors
		allSubProblem = new ArrayList<>(popSize); //2021.11.10
//		weights = new double[popSize][numObjectives];
		initAllSubProblems(); //2021.11.10

		// Identify the neighboring weights for each vector
		neighbourhood = new int[popSize][neighborSize];
		initializeNeighborhood(); //modified by mengxu 2021.08.11
//		identifyNeighbourWeights(); //original by fangfang
	}

	public void updateMainObjective(EvolutionState state, int thread){
		int mainObjIndex = state.random[thread].nextInt(numObjectives);
		Individual[] inds = state.population.subpops[0].individuals;
		for(int i=0; i < inds.length; i++){
			MOEADeCMultiObjectiveFitness fitness = (MOEADeCMultiObjectiveFitness)inds[i].fitness;
			fitness.setMainObjIndex(mainObjIndex);
			fitness.setSubProblem(allSubProblem.get(i));//todo: need to check
//			fitness.setIdealPoint(idealPoint);
//			fitness.setNadirPoint(nadirPoint);
		}
	}



	/**
	 * this must happen after "updateMainObjective"
	 * add 2021.11.23
	 * @param state
	 * @param thread
	 */
	public void updateSubProblem(EvolutionState state, int thread){
		Individual[] inds = state.population.subpops[0].individuals;

		for(int i=0; i < inds.length; i++){
			MOEADeCMultiObjectiveFitness fitness = (MOEADeCMultiObjectiveFitness)inds[i].fitness;
			int mainObjIndex = fitness.getMainObjIndex();
			List<Double> consInd = new ArrayList<>();
			for(int j=0; j<numObjectives; j++){
				if(j != mainObjIndex){
					double ref = (fitness.getObjective(j)-idealPoint[j])/(nadirPoint[j]-idealPoint[j]);
					consInd.add(ref);
				}
			}
			int subprobIndex = mappingIndWithSubProblem(consInd);
			fitness.setSubProblem(allSubProblem.get(subprobIndex));
//			fitness.setIdealPoint(idealPoint);
//			fitness.setNadirPoint(nadirPoint);
		}
	}

	public void updateSubProblem(Individual ind){
			MOEADeCMultiObjectiveFitness fitness = (MOEADeCMultiObjectiveFitness)ind.fitness;
			int mainObjIndex = fitness.getMainObjIndex();
			List<Double> consInd = new ArrayList<>();
			for(int j=0; j<numObjectives; j++){
				if(j != mainObjIndex){
					double ref = (fitness.getObjective(j)-idealPoint[j])/(nadirPoint[j]-idealPoint[j]);
					consInd.add(ref);
				}
			}
			int subprobIndex = mappingIndWithSubProblem(consInd);
			fitness.setSubProblem(allSubProblem.get(subprobIndex));
//			fitness.setIdealPoint(idealPoint);
//			fitness.setNadirPoint(nadirPoint);
	}

	public int mappingIndWithSubProblem(List<Double> consInd){
		int subProbIndex = -1;
		double minDis = Double.POSITIVE_INFINITY;
		for(int i=0; i<allSubProblem.size(); i++){
			SubProblem subprob = allSubProblem.get(i);
			double dis = MOEADUtils.distVector(ArrayUtils.toPrimitive(consInd.toArray(new Double[0])), subprob.getEpsilons());
			//todo: need to consider the constraints, that is "it need to be smaller than the constraints!"
			if(dis < minDis){
				subProbIndex = i;
				minDis = dis;
			}
		}
		return subProbIndex;
	}

	public void updateMainObjective(Individual oldInd, Individual newInd){
		int mainObjIndex = ((MOEADeCMultiObjectiveFitness)oldInd.fitness).getMainObjIndex();
		SubProblem subproblem = ((MOEADeCMultiObjectiveFitness)oldInd.fitness).getSubProblem();
		MOEADeCMultiObjectiveFitness fitness = (MOEADeCMultiObjectiveFitness)newInd.fitness;
		fitness.setMainObjIndex(mainObjIndex);
		fitness.setSubProblem(subproblem);//todo: need to check
//		fitness.setIdealPoint(idealPoint);
//		fitness.setNadirPoint(nadirPoint);
	}

	//2021.11.24
	public void updateSubProb(Individual ind, int subprobID){
		SubProblem subproblem = allSubProblem.get(subprobID);
		MOEADeCMultiObjectiveFitness fitness = (MOEADeCMultiObjectiveFitness)ind.fitness;
//		fitness.setMainObjIndex(mainObjIndex);
		fitness.setSubProblem(subproblem);//todo: need to check
//		fitness.setIdealPoint(idealPoint);
//		fitness.setNadirPoint(nadirPoint);
	}

//	//todo: need to modify this based on Juan Li's paper.
//	public double calculateFitness(Individual ind, SubProblem subProb) {
//		double[] problemWeights = weights[problemIndex];
//		double max_fun = -1 * Double.MAX_VALUE;
//
//		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;
//
//		for (int i = 0; i < numObjectives; i++) {
////			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(nadirPoint[i] - idealPoint[i]);
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
//			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
//				return Double.POSITIVE_INFINITY;
//			}
//			double feval;
//			if (problemWeights[i] == 0)
//				feval = 0.00001 * diff;
//			else
//				feval = problemWeights[i] * diff;
//			if (feval > max_fun)
//				max_fun = feval;
//		}
//		return max_fun;
//	}
//
//	public double calculateTchebycheffScore(Individual ind, int problemIndex) {
//		double[] problemWeights = weights[problemIndex];
//		double max_fun = -1 * Double.MAX_VALUE;
//
//		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;
//
//		for (int i = 0; i < numObjectives; i++) {
////			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(nadirPoint[i] - idealPoint[i]);
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
//			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
//				return Double.POSITIVE_INFINITY;
//			}
//			double feval;
//			if (problemWeights[i] == 0)
//				feval = 0.00001 * diff;
//			else
//				feval = problemWeights[i] * diff;
//			if (feval > max_fun)
//				max_fun = feval;
//		}
//		return max_fun;
//	}
//
//	/**
//	 * Calculates the problem score for a given individual, using a given set of
//	 * weights.
//	 *
//	 * @param ind
//	 * @param problemIndex
//	 *            - for retrieving weights
//	 * @return score
//	 */
//	public double calculateWeightSumScore(Individual ind, int problemIndex) {
//		double[] problemWeights = weights[problemIndex];
//		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;
//
//		double sum = 0;
//		for(int i = 0; i < numObjectives; i++){
////			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i])/(nadirPoint[i] - idealPoint[i]);
//			double diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
//			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
//				return Double.POSITIVE_INFINITY;
//			}
//			sum += (problemWeights[i]) * diff;
//		}
//
//		return sum;
//	}

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
//	private void identifyNeighbourWeights() {
//		// Calculate distance between vectors
//		double[][] distanceMatrix = new double[popSize][popSize];
//
//		for (int i = 0; i < popSize; i++) {
//			for (int j = 0; j < popSize; j++) {
//				if (i != j)
//					distanceMatrix[i][j] = calculateDistance(weights[i], weights[j]);
//			}
//		}
//
//		// Use this information to build the neighborhood
//		for (int i = 0; i < popSize; i++) {
//			int[] neighbours = identifyNearestNeighbours(distanceMatrix[i], i);
//			neighbourhood[i] = neighbours;
//		}
//	}

	/**
	 * Initialize neighborhoods add by mengxu 2021.08.11
	 */
	protected void initializeNeighborhood() {
		double[] x = new double[popSize];
		int[] idx = new int[popSize];

		for (int i = 0; i < popSize; i++) {
			// calculate the distances based on weight vectors
			for (int j = 0; j < popSize; j++) {
				x[j] = MOEADUtils.distVector(allSubProblem.get(i).getEpsilons(), allSubProblem.get(j).getEpsilons());
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
//			idealPoint[i] = 0.0; //original by Fangfang
			idealPoint[i] = Double.POSITIVE_INFINITY; //modified by mengxu based on Jmetal
		}
	}

	/**
	 * Initialize the nadir point. 2021.10.18
	 */
	private void initNadirPoint() {
		nadirPoint = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++) {
			nadirPoint[i] = Double.NEGATIVE_INFINITY; //original by Fangfang
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
			double[] objectives = ((MOEADeCMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < idealPoint.length; i++) {
					if (this.idealPoint[i] > objectives[i]) {
						this.idealPoint[i] = objectives[i];
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
			this.idealPoint[i] = multiBoxOutliers.get(i).getMin();
		}
	}

	public void updateIdealPoint(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((MOEADeCMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < idealPoint.length; i++) {
			if (this.idealPoint[i] > objectives[i]) {
				this.idealPoint[i] = objectives[i];
//				System.out.println("update one idealPoint!");
			}
		}
	}

	/**
	 * Update the ideal point add by mengxu 2021.08.11. Used after each population evaluation.
	 */
	public void updateNadirPoint(EvolutionState state) {
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((MOEADeCMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < nadirPoint.length; i++) {
				if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
					if (this.nadirPoint[i] < objectives[i]) {
						this.nadirPoint[i] = objectives[i];
					}
				}
			}
		}
	}

	/**
	 * Update the nadir point add by mengxu 2021.11.03. Used after each population evaluation.
	 */
	public void updateNadirPoint() {
		//this is just suitable for one subpop.
		for(int i=0; i<numObjectives; i++){
			this.nadirPoint[i] = multiBoxOutliers.get(i).getMaxInRegion();
		}
	}

	public void updateNadirPoint(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((MOEADeCMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < nadirPoint.length; i++) {
			if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
				if (this.nadirPoint[i] < objectives[i]) {
					this.nadirPoint[i] = objectives[i];
				}
			}
		}
	}


	/**
	 * Initialize uniformely spread weight vectors. This code come from the authors'
	 * original code base.
	 */
	private void initAllSubProblems() {
		double interval = 0;
		if(numObjectives == 2){
			interval = 1/((double)(popSize-1));
		}
		else if(numObjectives == 3){
			interval = 1/((double)(popSize/2-1));
		}
		for (int i = 0; i < popSize; i++) {
			if(numObjectives == 2) {
				double[] epsilon = new double[1];
				epsilon[0] = i * interval;
				SubProblem subProb = new SubProblem(i, numObjectives, epsilon);
				allSubProblem.add(subProb);
			} else if(numObjectives == 3) {
				//todo: this is not right, need to make it suitable for the popSize
				double[] epsilon = new double[2];
				epsilon[0] = i * interval;
				epsilon[1] = i * interval;
				SubProblem subProb = new SubProblem(i, numObjectives, epsilon);
				allSubProblem.add(subProb);
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
