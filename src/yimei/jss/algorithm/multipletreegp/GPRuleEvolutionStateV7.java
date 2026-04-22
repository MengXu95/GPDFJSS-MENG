package yimei.jss.algorithm.multipletreegp;

import ec.EvolutionState;
import ec.Individual;
import ec.util.Checkpoint;
import ec.util.Parameter;
import ec.util.RandomChoice;
import org.apache.commons.lang3.ArrayUtils;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.niching.*;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static yimei.jss.niching.ClearingEvaluator.phenoCharacterisation;

/**
 * The evolution state of evolving dispatching rules with GP.
 remove the low ranked inds
 *
 */
public class GPRuleEvolutionStateV7 extends GPRuleEvolutionState {
	public static final String P_PRE_GENERATIONS = "pre-generations";
	public final static String P_NUMOBJECTIVE_TEMP = "numObjectives-temp";
	private int preGenerations;
	public static final String P_NUM_TOP_INDS = "num-topinds";
	private int topInds;

	public static final String P_ARCHIVE_SIZE = "archive-size";
	private int archiveSize;

	public static final String P_ARCHIVE_UPDATE_RATE = "archive-update-rate";
	private double archiveUpdateRate;

	public static final String P_SHRINK_RATE = "shrink-rate";
	private double shrinkRate;
	public static final String P_ATTENUATION_RATE = "attenuation-rate";
	private double attenuationRate;

	protected long jobSeed;
	//fzhang 2019.5.19 pick terminals based on weighting power
	double[][] frequency =  null;
	double[][] frequency10Inds = null;

	//2020.9.6 the correlation of tasks by Spearman correlation
	//public static double correlation;

	int[][] transferTimeSoFarForTask;

	//2020.10.2 save three correlation
	public static double[][] correlation;
	public static double[][] score;
	public static Boolean[][] correlationCalculated;
	List<Double> correlationGen = new ArrayList<>();
	ArrayList<double[][]> pcTasks;
	public static ArrayList<ArrayList<Individual>> archiveInds = new ArrayList<>();//save the individuals in the archive
	public static ArrayList<Double> OneDimArchiveIndsAddBase;//calculate the data to decide which inds to delete
	//2020.10.21
	public static int numObjectivesTemp;

	//2020.10.11 the best individual in the last generation
	Individual[] bestIndLastGen;
	double[][] reward ; //the reward for assisted task
	int[][] successedTransfer;
	ArrayList<Integer> accumulatedSuccessTransfer = new ArrayList<>();//totally 3 tasks. Each task has 2 possible assisted tasks

//	double[] indexInds;
	public static ArrayList<Double> selectedInsRank = new ArrayList<>();
	public static ArrayList<Double> OneDimArchiveIndsGens = new ArrayList<>(); //save which generation the individuals from

	double[][] tempfitnessesForModel; //for transfer the fitness into the surrogate model to estimate the fitness
	double[][][] tempindsCharListsMultiTree;
	@Override
	public void setup(EvolutionState state, Parameter base) {
		super.setup(state, base);
		preGenerations = state.parameters.getIntWithDefault(
				new Parameter(P_PRE_GENERATIONS), null, -1);  //50
		numObjectivesTemp = state.parameters.getIntWithDefault(new Parameter(P_NUMOBJECTIVE_TEMP), null, 1);
		topInds = state.parameters.getInt(new Parameter(P_NUM_TOP_INDS), null);
		archiveSize = state.parameters.getInt(new Parameter(P_ARCHIVE_SIZE), null);
		archiveUpdateRate = state.parameters.getDouble(new Parameter(P_ARCHIVE_UPDATE_RATE), null);
//		shrinkRate = state.parameters.getDouble(new Parameter(P_SHRINK_RATE), null);
//		attenuationRate = state.parameters.getDouble(new Parameter(P_ATTENUATION_RATE), null);

		reward = new double[numObjectivesTemp][numObjectivesTemp];
		successedTransfer = new int[numObjectivesTemp][numObjectivesTemp];
		score = new double[numObjectivesTemp][numObjectivesTemp];
		transferTimeSoFarForTask = new int[numObjectivesTemp][numObjectivesTemp];

		Parameter p;
		p = new Parameter("seed").push(""+0);
		jobSeed = state.parameters.getLongWithDefault(p, null, 0);

		//2020.11.23 index of inds
/*		indexInds = new double[archiveSize];
		for(int i = 0; i < archiveSize; i++){
			indexInds[i] = (double) 1/(i+1);
		}*/
	}

	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

	    //System.out.println("generation "+generation);
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

	    evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
		PopulationUtils.sort(population); //2020.11.24 important!!!  otherwise, can not choose good inds based on rank

		//2020.11.30 build the surrogate
		if(generation != 0){ //for the first generation, do not need to build surrogate
 			double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, true); //3. calculate the phenotypic characteristic
			//get the fitness for training model
			double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];

			for(int subpop = 0; subpop < this.population.subpops.length; subpop++){
				for(int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++){
					fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
				}

				// int removeIdx = 0;
				double[][] indsCharListsMultiTreeSubpop = indsCharListsMultiTree[subpop];
				double[] fitnessesForModelSubpop = fitnessesForModel[subpop];

				for (int i = 0; i < fitnessesForModelSubpop.length; i++) {
					if (fitnessesForModelSubpop[i] == Double.MAX_VALUE) {
                   /* indsCharListsMultiTree = ArrayUtils.remove(indsCharListsMultiTree, i - removeIdx);
                    fitnessesForModel = ArrayUtils.remove(fitnessesForModel, i - removeIdx);*/
						indsCharListsMultiTreeSubpop = ArrayUtils.remove(indsCharListsMultiTreeSubpop, i);
						fitnessesForModelSubpop = ArrayUtils.remove(fitnessesForModelSubpop, i);
						i--;
						// removeIdx++;
					}
				}
				indsCharListsMultiTree[subpop] = indsCharListsMultiTreeSubpop;
				fitnessesForModel[subpop] = fitnessesForModelSubpop;
			}

			tempfitnessesForModel = fitnessesForModel;
			tempindsCharListsMultiTree = indsCharListsMultiTree;
		}


		//2020.11.30 so far, get the individuals for the pool
		if(generation != 0){
			//2020.11.30 step 1: estimate the fitness of individuals
			evaluatePoolIndividual(this,tempindsCharListsMultiTree,tempfitnessesForModel);
			//step 2: combine the individuals in the pool with the individuals in the population
			Individual[] indsPopPool = new Individual[this.population.subpops[0].individuals.length+archiveInds.get(0).size()];
			for(int ind = 0; ind < this.population.subpops[0].individuals.length; ind++){
				indsPopPool[ind] = this.population.subpops[0].individuals[ind];
			}

			for(int ind = this.population.subpops[0].individuals.length; ind < indsPopPool.length; ind++){
				indsPopPool[ind] = archiveInds.get(0).get(ind-this.population.subpops[0].individuals.length);
			}
			//step 3: rank the indiviudals in the population
			PopulationUtils.sort(indsPopPool);
			//step 4: only keep popsize individuals for generating offspring
			for(int ind = 0; ind < this.population.subpops[0].individuals.length; ind++){
               this.population.subpops[0].individuals[ind] = indsPopPool[ind];
			}
		}

		//2020.11.23  manage the archive--add and delete. The good individuals in current population will be kept for next generation.
		if (generation != numGenerations - 1) {
			for (int sub = 0; sub < this.population.subpops.length; sub++) {
				ArrayList<Individual> OneDimArchiveInds = new ArrayList<>();//save good individuals
				//2020.11.25 add top x% inds to the archive
				if (generation != 0) {
					OneDimArchiveInds = archiveInds.get(sub);
				}

				int numAddInd = 0;
				//for (int numUpdatedInds = 0; numUpdatedInds < archiveUpdateRate * this.population.subpops[sub].individuals.length; numUpdatedInds++) {
				for (int numUpdatedInds = 0; numUpdatedInds < this.population.subpops[sub].individuals.length; numUpdatedInds++) {
				    if (numAddInd < archiveUpdateRate * this.population.subpops[sub].individuals.length) {//must add 10 inds in each generation
						if (OneDimArchiveInds.size() < archiveSize) {
							//2020.11.26 when add an individual, make sure the individual is identical based on PC
						/*double[][] pcArchiveInds;
						ArrayList<Individual> currentInd = new ArrayList<>();
						currentInd.add(this.population.subpops[sub].individuals[numUpdatedInds]);
						double[][] currentIndPC;
						Boolean repeated = false;
						if(OneDimArchiveInds.size() != 0){
							//this means there are individuals in the archive, need to check whether there are the same individual or not.
							//1. calculate the PC of individuals
							pcArchiveInds = phenotypicForSurrogateV1.phenotypicIndividual(this, ClearingEvaluator.phenoCharacterisation, OneDimArchiveInds);
							currentIndPC = phenotypicForSurrogateV1.phenotypicIndividual(this, ClearingEvaluator.phenoCharacterisation, currentInd);
							for(int repeat = 0; repeat < pcArchiveInds.length; repeat++){
								if (Arrays.equals(currentIndPC[0], pcArchiveInds[repeat])){
									repeated = true;
									break;
								}
							}
						}*/
							Boolean repeated = checkRepeat(OneDimArchiveInds, this.population.subpops[sub].individuals[numUpdatedInds]);
							if (repeated == false) {
								OneDimArchiveInds.add(this.population.subpops[sub].individuals[numUpdatedInds]);
								selectedInsRank.add(Double.valueOf(numUpdatedInds)); //selectedInsRank ---> selectedIndsRank
								OneDimArchiveIndsGens.add(Double.valueOf(generation));
								numAddInd++;
							}
						} else {
							//delete one individual and insert a new individual
							ArrayList<Double> OneDimArchiveIndsDeletedBase = new ArrayList<>();//calculate the data to decide which inds to delete
							for (int i = 0; i < OneDimArchiveInds.size(); i++) {
//								OneDimArchiveIndsDeletedBase.add((selectedInsRank.get(i) + 1) / (OneDimArchiveIndsGens.get(i) + 1));//rank/generation
								OneDimArchiveIndsDeletedBase.add((selectedInsRank.get(i)) + 1/(OneDimArchiveIndsGens.get(i) + 1));//rank/generation
							}
							double[] deleteIndIndex = OneDimArchiveIndsDeletedBase.stream().mapToDouble(Double::doubleValue).toArray();
							double[] usedDeleteIndIndex = deleteIndIndex.clone();
							int deleteInd = 0;

							int numZero = 0;
							for (int c = 0; c < usedDeleteIndIndex.length; c++) {
								if (usedDeleteIndIndex[c] == 0) {
									numZero++;
								}
							}
							//when all zeros, or all the elements are the same. Just use the original way.
							if (numZero == usedDeleteIndIndex.length || Arrays.stream(usedDeleteIndIndex).distinct().count() == 1) {
								//random choose one
								deleteInd = this.random[0].nextInt(usedDeleteIndIndex.length);// random choose one
							} else {
								RandomChoice.organizeDistribution(usedDeleteIndIndex);
								deleteInd = RandomChoice.pickFromDistribution(usedDeleteIndIndex, this.random[0].nextDouble());
							}
							OneDimArchiveInds.remove(deleteInd);
							selectedInsRank.remove(deleteInd);
							OneDimArchiveIndsGens.remove(deleteInd);

							//2020.11.25 when add one individual, check whether it is repeated or not.
							Boolean repeated = checkRepeat(OneDimArchiveInds, this.population.subpops[sub].individuals[numUpdatedInds]);
							if (repeated == false) {
								OneDimArchiveInds.add(this.population.subpops[sub].individuals[numUpdatedInds]);
								selectedInsRank.add(Double.valueOf(numUpdatedInds));
								OneDimArchiveIndsGens.add(Double.valueOf(generation));
							}
						}
					}
				    else{
				    	break;
					}
				}
				if (generation == 0) {
					archiveInds.add(OneDimArchiveInds);
				}
			}
		}

		//2020.11.29 for choosing parents in the pool for breeding offspring
/*		OneDimArchiveIndsAddBase = new ArrayList<>();
		for (int i = 0; i < archiveInds.get(0).size(); i++) {
			OneDimArchiveIndsAddBase.add((OneDimArchiveIndsGens.get(i) + 1) / (selectedInsRank.get(i) + 1));//rank/generation
		}*/


	    statistics.postEvaluationStatistics(this);

	    // SHOULD WE QUIT?
	    if (evaluator.runComplete(this) && quitOnRunComplete)
	        {
	        output.message("Found Ideal Individual");
	        return R_SUCCESS;
	        }
	    // SHOULD WE QUIT?
	    if (generation == numGenerations-1)
	        {
	    	generation++; // in this way, the last generation value will be printed properly.  fzhang 28.3.2018
		    writeGenRankToFile();
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

	    // BREEDING
	    statistics.preBreedingStatistics(this);

	    population = breeder.breedPopulation(this); //!!!!!!   return newpop;  if it is NSGA-II, the population here is 2N

	    // POST-BREEDING EXCHANGING
	    statistics.postBreedingStatistics(this);   //position 1  here, a new pop has been generated.

	    // POST-BREEDING EXCHANGING
	    statistics.prePostBreedingExchangeStatistics(this);
	    population = exchanger.postBreedingExchangePopulation(this);   /** Simply returns state.population. */
	    statistics.postPostBreedingExchangeStatistics(this);  //position 2

	    // Generate new instances if needed
		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
	    if (problem.getEvaluationModel().isRotatable()) {
			problem.rotateEvaluationModel();
		}

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

	//2020.11.25 check whether the new individual is repeated with the individuals in the archive or not
	//OneDimArchiveInds: the individuals in archive
	//ind: the current individual
	public Boolean checkRepeat(ArrayList<Individual> OneDimArchiveInds, Individual ind){
		//2020.11.26 when add an individual, make sure the individual is identical based on PC
		double[][] pcArchiveInds;
		ArrayList<Individual> currentInd = new ArrayList<>();
		currentInd.add(ind);
		double[][] currentIndPC;
		Boolean repeated = false;
		if(OneDimArchiveInds.size() != 0){
			//this means there are individuals in the archive, need to check whether there are the same individual or not.
			//1. calculate the PC of individuals
			pcArchiveInds = phenotypicForSurrogateV1.phenotypicIndividual(this, phenoCharacterisation, OneDimArchiveInds);
			currentIndPC = phenotypicForSurrogateV1.phenotypicIndividual(this, phenoCharacterisation, currentInd);
			for(int repeat = 0; repeat < pcArchiveInds.length; repeat++){
				if (Arrays.equals(currentIndPC[0], pcArchiveInds[repeat])){
					repeated = true;
					break;
				}
			}
		}
		return repeated;
	}

    //2020.11.29
	public void writeGenRankToFile(){
		File indPoolGenRankFile = new File("job." + jobSeed + ".indsInPoolGenRank.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(indPoolGenRankFile));
			writer.write("indIndex,fromGen,Rank");
			writer.newLine();
			for (int ind = 0; ind < OneDimArchiveIndsGens.size(); ind++) {
				writer.write(ind + "," + OneDimArchiveIndsGens.get(ind) + "," + selectedInsRank.get(ind));
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//==========================================KNN========================================
	//2020.11.30 assign fitness to all the individuals based on KNN with subpopulation K, then sort the intermediate population, and select the top k as the offspring in subpopulation K.
	public void evaluatePoolIndividual(final EvolutionState state, double[][][] indsCharListsMultiTree, double[][] fitnessesForModel) {

		//2020.11.30 the PC of the individuals in the pool
		double[][] poolIndPC = phenotypicForSurrogateV1.phenotypicIndividual(this, ClearingEvaluator.phenoCharacterisation, archiveInds.get(0));

		//step2: evaluate all individuals based on the first KNN
		for (int sub = 0; sub < state.population.subpops.length; sub++) {
			for (int i = 0; i < archiveInds.get(0).size(); i++) //
			{
				Individual individual = archiveInds.get(0).get(i); //the examined individual
				//if(indsCharListsMultiTree.length != 0){
				//KNN
				//===============================start==============================================
				double dMin  = Double.MAX_VALUE;
				int index = 0;
					double[] pcIntermediate = poolIndPC[i]; //the pc of the examined individual
					//calculate the fitness based on surrogate model
					for(int pc = 0; pc < indsCharListsMultiTree[sub].length; pc++){
						double[] pcModel = indsCharListsMultiTree[sub][pc];
						double d  = PhenoCharacterisation.distance(pcIntermediate, pcModel);
						if(d == 0){
							index = pc;
							break;
						}
						if (d < dMin){
							dMin = d;
							index = pc;
						}
					}
					((Clearable)individual.fitness).surrogateFitness(fitnessesForModel[sub][index]);
			}

			//step3: choose the top |P| individuals as the offspring of the current subpop
			//sort the individuals based on the fitnesses
/*			Individual[] inds = PopulationUtils.sortInds(individuals);
			System.arraycopy(inds, 0, state.population.subpops[sub].individuals, 0, state.population.subpops[sub].individuals.length);*/
		}
	}
}
