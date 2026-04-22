package mengxu.algorithm;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.util.Checkpoint;
import ec.util.Parameter;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.gp.GPTreeComparator;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The evolution state of evolving dispatching rules with GP.
 remove the low ranked inds
 *
 */
public class GPRuleEvolutionStateV12 extends GPRuleEvolutionState {
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
    //Todo: change the archive to store important subtrees for mutation
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

    public static ArrayList<ArrayList<Individual>> getArchiveInds() {
        return archiveInds;
    }

    public int evolve() {
        if (generation > 0)
            output.message("Generation " + generation);

        //System.out.println("generation "+generation);
        // EVALUATION
        statistics.preEvaluationStatistics(this);

        evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
        PopulationUtils.sort(population); //2020.11.24 important!!!  otherwise, can not choose good inds based on rank

        //compare to population, delete the repeated individuals in archive
        for (int sub = 0; sub < this.population.subpops.length; sub++) {
            ArrayList<Individual> OneDimArchiveIndsCheck = new ArrayList<>();
            if (generation != 0) {
                OneDimArchiveIndsCheck = archiveInds.get(sub);
            }
            int repeatNum = 0;
            for(int i=0;i<OneDimArchiveIndsCheck.size();i++){
                boolean repeatedpop = checkRepeatPopV2(OneDimArchiveIndsCheck.get(i));
                if(repeatedpop==true){
                    repeatNum++;
                }
            }
            if (generation != 0) {
                output.message("The number of individuals in the archive that duplicate the population: " + repeatNum + "/" + archiveInds.get(sub).size());
            }
        }


        //2020.11.30 so far, get the individuals for the pool
        if(generation != 0){
            //2020.11.30 step 1: estimate the fitness of individuals
            evaluatePoolIndividual(this);
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

                //2021.1.25 modified by meng xu. to make sure add archiveUpdateRate * this.population.subpops[sub].individuals.length inds
                //2020.12.8 just check the archiveUpdateRate * this.population.subpops[sub].individuals.length inds, if they meet the condition, use. Otherwise, ignore.
                int numUpdatedInds = 0;
                int updateNums = 0;
                while (numUpdatedInds < this.population.subpops[sub].individuals.length && updateNums < archiveUpdateRate * this.population.subpops[sub].individuals.length) {//must add 10 inds in each generation
                    if (OneDimArchiveInds.size() < archiveSize) {
                        Boolean repeated = checkRepeat(OneDimArchiveInds, this.population.subpops[sub].individuals[numUpdatedInds]);
//                        Boolean repeatedPop = checkRepeatPop(this.population.subpops[sub].individuals[numUpdatedInds]);
                        if (repeated == false) {
                            OneDimArchiveInds.add(this.population.subpops[sub].individuals[numUpdatedInds]);
                            selectedInsRank.add(Double.valueOf(numUpdatedInds)); //selectedInsRank ---> selectedIndsRank
                            OneDimArchiveIndsGens.add(Double.valueOf(generation));
                            numUpdatedInds++;
                            updateNums++;
                        }
                        else{
                            numUpdatedInds++;
                        }
                    } else {
                        ArrayList<Double> FitnessAll = new ArrayList<>();//to save the current fitness
                        for (Individual oneDimArchiveInd : OneDimArchiveInds) {
                            FitnessAll.add(oneDimArchiveInd.fitness.fitness());
                        }
                        ArrayList<Double> OneDimArchiveIndsDeletedBase = new ArrayList<>();//calculate the data to decide which inds to delete
                        //delete one individual and insert a new individual
                        double minFitness = Collections.min(FitnessAll);
                        double maxFitness = Collections.max(FitnessAll);
                        double minGeneration = Collections.min(OneDimArchiveIndsGens);
                        double maxGeneration = Collections.max(OneDimArchiveIndsGens);

//                        double maxFitness = Collections.max()

                        int index = 0;
                        for (int i = 0; i < OneDimArchiveInds.size(); i++) {
//								OneDimArchiveIndsDeletedBase.add((selectedInsRank.get(i) + 1) / (OneDimArchiveIndsGens.get(i) + 1));//rank/generation
                            //2020.12.3 need to nornamise the rank and genration
                            double normalisedFitness = 0.0;
                            if(maxFitness - minFitness == 0){//this means all the values are the same.
                                normalisedFitness = selectedInsRank.get(i);
                            }
                            else{
                                normalisedFitness = (selectedInsRank.get(i) - minFitness)/(maxFitness - minFitness);
                            }

                            double normalisedGeneration = 0.0;
                            if(maxGeneration - minGeneration == 0){
                                normalisedGeneration = OneDimArchiveIndsGens.get(i);
                            }
                            else{
                                normalisedGeneration = (OneDimArchiveIndsGens.get(i) - minGeneration) / (maxGeneration - minGeneration);
                            }

//							OneDimArchiveIndsDeletedBase.add(normalisedRank + 1/(normalisedGeneration + 1));//rank/generation
                            OneDimArchiveIndsDeletedBase.add(normalisedFitness + 1/(normalisedGeneration + 1));//rank/generation //modified by meng, add a "()"
                        }

                        //2020.12.4 remove the one with the largest rank+1/(generation+1)
                        int deleteInd = OneDimArchiveIndsDeletedBase.indexOf(Collections.max(OneDimArchiveIndsDeletedBase));
                        OneDimArchiveInds.remove(deleteInd);
                        selectedInsRank.remove(deleteInd);
                        OneDimArchiveIndsGens.remove(deleteInd);

                        //2020.11.25 when add one individual, check whether it is repeated or not.
                        Boolean repeated = checkRepeat(OneDimArchiveInds, this.population.subpops[sub].individuals[numUpdatedInds]);
                        if (repeated == false) {
                            OneDimArchiveInds.add(this.population.subpops[sub].individuals[numUpdatedInds]);
                            selectedInsRank.add(Double.valueOf(numUpdatedInds));
                            OneDimArchiveIndsGens.add(Double.valueOf(generation));
                            numUpdatedInds++;
                            updateNums++;
                        }
                        else{
                            numUpdatedInds++;
                        }
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
        Boolean repeated = false;
        GPTree[] indTrees = ((GPIndividual)ind).trees;
        GPTree indTree0 = indTrees[0];
        GPTree indTree1 = indTrees[1];
        if(OneDimArchiveInds.size() != 0){
            // modified by meng xu.
            for(int i=0;i<OneDimArchiveInds.size();i++){
                Individual indInpool = OneDimArchiveInds.get(i);
                GPTree[] gpTrees = ((GPIndividual)indInpool).trees;
                GPTree gpTree0 = gpTrees[0];
                GPTree gpTree1 = gpTrees[1];
                if(GPTreeComparator.TreeEquals(indTree0,gpTree0) &&
                        GPTreeComparator.TreeEquals(indTree1,gpTree1)){
                    repeated = true;
                    break;
                }
                if(indInpool.distanceTo(ind)==0){
                    repeated = true;
                    break;
                }
            }
        }
        return repeated;
    }

    //2020.11.25 check whether the new individual is repeated with the individuals in population or not
    //ind: the current individual
    public Boolean checkRepeatPop(Individual ind){
        //2020.11.26 when add an individual, make sure the individual is identical based on PC
        Boolean repeated = false;
        for(int i=0;i<population.subpops[0].individuals.length;i++){
            Individual indInpop = population.subpops[0].individuals[i];
            if(indInpop.distanceTo(ind)==0){
                repeated = true;
                break;
            }
        }
        return repeated;
    }

    public Boolean checkRepeatPopV2(Individual ind){
        //2020.11.26 when add an individual, make sure the individual is identical
        Boolean repeated = false;
        GPTree[] indTrees = ((GPIndividual)ind).trees;
        GPTree indTree0 = indTrees[0];
        GPTree indTree1 = indTrees[1];
        for(int i=0;i<population.subpops[0].individuals.length;i++){
            Individual indInpop = population.subpops[0].individuals[i];
            GPTree[] gpTrees = ((GPIndividual)indInpop).trees;
            GPTree gpTree0 = gpTrees[0];
            GPTree gpTree1 = gpTrees[1];
            if(GPTreeComparator.TreeEquals(indTree0,gpTree0) &&
                    GPTreeComparator.TreeEquals(indTree1,gpTree1)){
                repeated = true;
                break;
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

    //==========================================simple evaluate========================================
    //2020.2.2 modified by meng xu. evaluate based on the current simulation
    public void evaluatePoolIndividual(final EvolutionState state) {

        for(int gen=0;gen<archiveInds.size();gen++){
            for(Individual indi:archiveInds.get(0)){

                MultipleTreeRuleOptimizationProblem p = (MultipleTreeRuleOptimizationProblem)evaluator.p_problem.clone();

                p.evaluate(state, indi, 0,1);

                indi.evaluated = true;
            }
        }
    }


}
