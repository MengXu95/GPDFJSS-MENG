package yimei.jss.algorithm.multipletreegp;

import ec.EvolutionState;
import ec.Individual;
import ec.util.Checkpoint;
import ec.util.Parameter;
import ec.util.RandomChoice;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.util.ArrayList;
import java.util.List;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 */
public class GPRuleEvolutionStateV2 extends GPRuleEvolutionState {
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

	//2020.10.21
	public static int numObjectivesTemp;

	//2020.10.11 the best individual in the last generation
	Individual[] bestIndLastGen;
	double[][] reward ; //the reward for assisted task
	int[][] successedTransfer;
	ArrayList<Integer> accumulatedSuccessTransfer = new ArrayList<>();//totally 3 tasks. Each task has 2 possible assisted tasks

	double[] indexInds;
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
		indexInds = new double[100];
		for(int i = 0; i < 100; i++){
			indexInds[i] = (double) 1/(i+1);
		}

	}

	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

	    //System.out.println("generation "+generation);
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

	    evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
		//2020.11.23 manage the archive
		if (generation != numGenerations - 1){
			for (int sub = 0; sub < this.population.subpops.length; sub++) {
				ArrayList<Individual> OneDimArchiveInds = new ArrayList<>();

/*				for (int ind = 0; ind < this.population.subpops[sub].individuals.length; ind++) {
					if(generation == 0){//initialise
						//fill the archive with all the individuals in the population
						OneDimArchiveInds.add(this.population.subpops[sub].individuals[ind]);
					}
					else{
						double rnd = this.random[0].nextDouble();
						//update the archive!!!
						if (rnd < archiveUpdateRate) {
							if (OneDimArchiveInds.size() < archiveSize) {
								OneDimArchiveInds.add(this.population.subpops[sub].individuals[ind]);
							} else {
								OneDimArchiveInds.remove(this.random[0].nextInt(archiveInds.get(sub).size()));
								OneDimArchiveInds.add(this.population.subpops[sub].individuals[ind]);
							}
						}
					}
				}*/

				//2020.11.24 for the first generation, put all the individuals in the pool; for other generations, update the pool.
				if (generation == 0) {
					for (int numUpdatedInds = 0; numUpdatedInds < archiveUpdateRate * this.population.subpops[sub].individuals.length; numUpdatedInds++) {
						double[] usedIndexInds = indexInds.clone();
						RandomChoice.organizeDistribution(usedIndexInds);
						int chosenIndIndex = RandomChoice.pickFromDistribution(usedIndexInds, this.random[0].nextDouble());
						OneDimArchiveInds.add(this.population.subpops[sub].individuals[chosenIndIndex]);
					}
					archiveInds.add(OneDimArchiveInds);
				} else {
					OneDimArchiveInds = archiveInds.get(sub);//change the OneDimArchiveInds, will change the archive automatically
					for (int numUpdatedInds = 0; numUpdatedInds < archiveUpdateRate * this.population.subpops[sub].individuals.length; numUpdatedInds++) {
						//choose an individual based on the rank to update the pool
						double[] usedIndexInds = indexInds.clone();
						RandomChoice.organizeDistribution(usedIndexInds);
						int chosenIndIndex = RandomChoice.pickFromDistribution(usedIndexInds, this.random[0].nextDouble());
						//update the archive!!!
						if (OneDimArchiveInds.size() < archiveSize) {
							OneDimArchiveInds.add(this.population.subpops[sub].individuals[chosenIndIndex]);
						} else {
							OneDimArchiveInds.remove(this.random[0].nextInt(archiveInds.get(sub).size()));
							OneDimArchiveInds.add(this.population.subpops[sub].individuals[chosenIndIndex]);
						}
					}
				}

/*				for (int numUpdatedInds = 0; numUpdatedInds < archiveUpdateRate * this.population.subpops[sub].individuals.length; numUpdatedInds++) {
					//choose an individual based on the rank to update the pool
					double[] usedIndexInds = indexInds.clone();
					RandomChoice.organizeDistribution(usedIndexInds);
					int chosenIndIndex = RandomChoice.pickFromDistribution(usedIndexInds, this.random[0].nextDouble());

					if (generation == 0) {
						OneDimArchiveInds.add(this.population.subpops[sub].individuals[chosenIndIndex]);
					} else {
						OneDimArchiveInds = archiveInds.get(sub);//change the OneDimArchiveInds, will change the archive automatically
						//update the archive!!!
						if (OneDimArchiveInds.size() < archiveSize) {
							OneDimArchiveInds.add(this.population.subpops[sub].individuals[chosenIndIndex]);
						} else {
							OneDimArchiveInds.remove(this.random[0].nextInt(archiveInds.get(sub).size()));
							OneDimArchiveInds.add(this.population.subpops[sub].individuals[chosenIndIndex]);
						}
					}
				}*/

/*				if (generation == 0) {
					archiveInds.add(OneDimArchiveInds);
				}*/

				//2020.11.23 update archiveUpdateRate*this.population.subpops[sub].individuals.length inds
				/*for (int numUpdatedInds = 0; numUpdatedInds < archiveUpdateRate*this.population.subpops[sub].individuals.length; numUpdatedInds++) {
					//choose an individual based on the rank to update the pool
					double[] usedIndexInds = indexInds.clone();
					RandomChoice.organizeDistribution(usedIndexInds);
					int chosenIndIndex = RandomChoice.pickFromDistribution(usedIndexInds, this.random[0].nextDouble());

					if(generation == 0){//initialise
						//fill the archive with all the individuals in the population
						OneDimArchiveInds.add(this.population.subpops[sub].individuals[chosenIndIndex]);
					}
					else{
						//update the archive!!!
						if (OneDimArchiveInds.size() < archiveSize) {
							OneDimArchiveInds.add(this.population.subpops[sub].individuals[chosenIndIndex]);
						} else {
							OneDimArchiveInds.remove(this.random[0].nextInt(archiveInds.get(sub).size()));
							OneDimArchiveInds.add(this.population.subpops[sub].individuals[chosenIndIndex]);
						}
					}
				}*/
			}
		}


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
}
