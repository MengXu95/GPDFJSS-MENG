package mengxu.algorithm.multitaskHeuristicLearning.onlysurrogate;

import ec.*;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.ThreadPool;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer;
import mengxu.algorithm.multitaskHeuristicLearning.NonlinearModel;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * The evaluator with multiple tasks (instances) for multiple subpops.
 * The evaluator is used for multitask.
 *
 * Created by Meng Xu on 14/08/2024.
 */
public class MultitaskEvaluatorOnlySurrogate extends SimpleEvaluator {

    public static final String P_PROBLEM_NUM = "problem_number";

    protected int problem_num;

    public Problem[] multiproblems;

    protected boolean multitask;

    public int numTrees;

    public double[][][] estimatedFitness;

    public double[][][] estimatedRank;

    public double[] lowerBound;

    public static final String P_FITNESS_BASED_ON_RANK = "fitness-based-on-rank";
    public boolean fitnessBasedOnRank;

    public void setup(final EvolutionState state, final Parameter base) {
//        super.setup(state, base);

        this.multitask = true;
        this.p_problem = null; //this is the original problem, to use multiproblems for multitasking, we make this null

        // Load my problem ---
        Parameter p = base.push(P_PROBLEM_NUM);
        this.problem_num = state.parameters.getInt(p, null, 1);
        this.multiproblems = new Problem[this.problem_num];
        for(int i=0; i<this.problem_num; i++){
            Parameter p_problem_para = base.push(P_PROBLEM + "." + i);
            this.multiproblems[i] = (Problem)(state.parameters.getInstanceForParameter(
                    p_problem_para,null,Problem.class));
            this.multiproblems[i].setup(state,p_problem_para);
        }


        //==========the original setup===============
        cloneProblem =state.parameters.getBoolean(base.push(P_CLONE_PROBLEM), null, true);
        if (!cloneProblem && (state.breedthreads > 1)) // uh oh, this can't be right
            state.output.fatal("The Evaluator is not cloning its Problem, but you have more than one thread.", base.push(P_CLONE_PROBLEM));

        numTests = state.parameters.getInt(base.push(P_NUM_TESTS), null, 1);
        if (numTests < 1) numTests = 1;
        else if (numTests > 1)
        {
            String m = state.parameters.getString(base.push(P_MERGE), null);
            if (m == null)
                state.output.warning("Merge method not provided to SimpleEvaluator.  Assuming 'mean'");
            else if (m.equals(V_MEAN))
                mergeForm = MERGE_MEAN;
            else if (m.equals(V_MEDIAN))
                mergeForm = MERGE_MEDIAN;
            else if (m.equals(V_BEST))
                mergeForm = MERGE_BEST;
            else
                state.output.fatal("Bad merge method: " + m, base.push(P_NUM_TESTS), null);
        }

        if (!state.parameters.exists(base.push(P_CHUNK_SIZE), null))
        {
            chunkSize = C_AUTO;
        }
        else if (state.parameters.getString(base.push(P_CHUNK_SIZE), null).equalsIgnoreCase(V_AUTO))
        {
            chunkSize = C_AUTO;
        }
        else
        {
            chunkSize = (state.parameters.getInt(base.push(P_CHUNK_SIZE), null, 1));
            if (chunkSize == 0)  // uh oh
                state.output.fatal("Chunk Size must be either an integer >= 1 or 'auto'", base.push(P_CHUNK_SIZE), null);
        }
        //==========the original setup===============

        Parameter fitnessBasedOnRankParam = new Parameter(P_FITNESS_BASED_ON_RANK);
        this.fitnessBasedOnRank = state.parameters.getBoolean(fitnessBasedOnRankParam,null,false);

        String P_TREESIZE = "num-trees";
        this.numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);
    }

    /** Called to set up remote evaluation network contacts when the run is started.  Mostly used for client/server evaluation (see MasterProblem).  By default calls p_problem.initializeContacts(state) */
    public void initializeContacts(EvolutionState state)
    {
        for(int i=0; i<this.multiproblems.length; i++){
            Problem problem = this.multiproblems[i];
            problem.initializeContacts(state);
        }
    }

    /**  Called to shut down remote evaluation network contacts when the run is completed.  Mostly used for client/server evaluation (see MasterProblem).  By default calls p_problem.closeContacts(state,result) */
    public void closeContacts(EvolutionState state, int result)
    {
        for(int i=0; i<this.multiproblems.length; i++){
            Problem problem = this.multiproblems[i];
            problem.closeContacts(state,result);
        }
    }

    @Override
    public void evaluatePopulation(final EvolutionState state) {
        multitaskEvaluation(state);

        //update each surrogate model with the best individual at each subpop
        Population pop = state.population;
        PopulationUtils.sort(pop);

//        state.statistics.postEvaluationStatistics(state);

//        if(state.generation>((GPRuleEvolutionStateMultiTaskLearningOnlySurrogate)state).switchGeneration){
            //Step 1: calculate PC on all tasks!
            double[][][][] PCOnAllTasks = calculatePCOnAllTasks(state);
//        System.out.println("Calculate PC done!");

            //Step 2: estimate fitness based on the PC
            this.estimatedFitness = fitnessEstimationOnAllTasks(state, PCOnAllTasks);
//        System.out.println("Fitness estimation done!");

            lowerBoundCalculation(state,estimatedFitness);

            if(this.fitnessBasedOnRank){
                this.estimatedRank = rankEstimationOnAllTasks(state, this.estimatedFitness);
            }

            //Step 2.1 (option): fitness normalisation
            //with normalisation is worse than without normalisation
//            double[][][] normalisedEstimatedFitness = fitnessManualRuleNormalisationOnAllTasks(state, estimatedFitness);

            //Step 3: fitness aggregation and update fitness
            //Give the subpop the individual original belongs to the highest percentage (50%), and the other share the remaining 50%
//        fitnessAggregationAndUpdate(state, estimatedFitness);
//            fitnessAggregationAndUpdate(state, normalisedEstimatedFitness);
//        System.out.println("fitnessAggregationAndUpdate done!");
//        }
    }


    public double[][][] fitnessMinMaxNormalisationOnAllTasks(EvolutionState state, double[][][] estimatedFitness){
        double[][][] normalisedFitness = new double[estimatedFitness.length][estimatedFitness[0].length][estimatedFitness[0][0].length];
        double[] maxFitness = new double[estimatedFitness.length];
        double[] minFitness = new double[estimatedFitness.length];

        for(int i=0; i<maxFitness.length; i++){
            maxFitness[i] = Double.NEGATIVE_INFINITY;
            minFitness[i] = Double.POSITIVE_INFINITY;
        }

        for(int subpop=0; subpop<estimatedFitness.length; subpop++){
            double[][] subpopFitness = estimatedFitness[subpop];
            for(int ind=0; ind<subpopFitness.length; ind++){
                if(subpopFitness[ind][subpop] >= Double.POSITIVE_INFINITY || subpopFitness[ind][subpop] >= Double.MAX_VALUE){
                    continue;
                }
                if(subpopFitness[ind][subpop] > maxFitness[subpop]){
                    maxFitness[subpop] = subpopFitness[ind][subpop];
                }
                if(subpopFitness[ind][subpop] < minFitness[subpop]){
                    minFitness[subpop] = subpopFitness[ind][subpop];
                }
            }
        }

        for(int subpop=0; subpop<estimatedFitness.length; subpop++){
            double[][] subpopFitness = estimatedFitness[subpop];
            for(int ind=0; ind<subpopFitness.length; ind++){
                for(int i=0; i<subpopFitness[ind].length; i++){
                    normalisedFitness[subpop][ind][i] = (estimatedFitness[subpop][ind][i] - minFitness[i])/(maxFitness[i] - minFitness[i]);
                }
            }
        }

        return normalisedFitness;
    }

    public void lowerBoundCalculation(EvolutionState state, double[][][] estimatedFitness){
        int numberObjective = state.population.subpops.length;
        double[] lowerBound = new double[numberObjective];
        for(int i=0; i<this.multiproblems.length; i++) {
            SimpleProblemForm prob = (SimpleProblemForm) (this.multiproblems[i].clone());
            AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem)prob).getEvaluationModel();
            SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel)evaluationModel).getSchedulingSet();
            curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
            RealMatrix lowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
            lowerBound[i] = lowerBoundMtx.getEntry(0,0);
            if(lowerBound[i] >= Double.MAX_VALUE || lowerBound[i] >= Double.POSITIVE_INFINITY || lowerBound[i] <= 0){
                System.out.println("Error in lowerbound!");
            }
        }
        this.lowerBound = lowerBound;
    }

    public double[][][] fitnessManualRuleNormalisationOnAllTasks(EvolutionState state, double[][][] estimatedFitness){
        double[] lowerBound = new double[estimatedFitness.length];
        for(int i=0; i<this.multiproblems.length; i++) {
            SimpleProblemForm prob = (SimpleProblemForm) (this.multiproblems[i].clone());
            AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem)prob).getEvaluationModel();
            SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel)evaluationModel).getSchedulingSet();
            curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
            RealMatrix lowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
            lowerBound[i] = lowerBoundMtx.getEntry(0,0);
            if(lowerBound[i] >= Double.MAX_VALUE || lowerBound[i] >= Double.POSITIVE_INFINITY || lowerBound[i] <= 0){
                System.out.println("Error in lowerbound!");
            }
        }

        double[][][] normalisedFitness = new double[estimatedFitness.length][estimatedFitness[0].length][estimatedFitness[0][0].length];
        for(int subpop=0; subpop<estimatedFitness.length; subpop++){
            double[][] subpopFitness = estimatedFitness[subpop];
            for(int ind=0; ind<subpopFitness.length; ind++){
                for(int i=0; i<subpopFitness[ind].length; i++){
                    if(estimatedFitness[subpop][ind][i] >= Double.MAX_VALUE || estimatedFitness[subpop][ind][i] >= Double.POSITIVE_INFINITY){
                        normalisedFitness[subpop][ind][i] = estimatedFitness[subpop][ind][i];
                    }
                    else{
                        normalisedFitness[subpop][ind][i] = estimatedFitness[subpop][ind][i]/lowerBound[i];
                    }
                    //todo: do we need to consider bad run?? 2024.10.9
                }
            }
        }

        return normalisedFitness;
    }

    public void fitnessAggregationAndUpdate(final EvolutionState state, double[][][] estimatedFitness){
        int numTasks = estimatedFitness.length;
        //Strategy 1:
        double weightOriginal = 0.5;
        double weightOthers = (1-weightOriginal)/(double)(numTasks-1);

//        //Strategy 2:
//        double weightOthers = (double)1/(double)numTasks;
//        double weightOriginal = weightOthers;

        //Strategy 3:
//        double weightOthers = 0;
//        double weightOriginal = 1;

        for(int subpop=0; subpop<estimatedFitness.length; subpop++){
            Individual[] inds = state.population.subpops[subpop].individuals;
            double[][] subpopFitness = estimatedFitness[subpop];
            for(int ind=0; ind<subpopFitness.length; ind++){
                double sumFitness = 0;
                for(int i=0;i<subpopFitness[ind].length; i++){
                    if(i==subpop){
                        sumFitness = sumFitness + weightOriginal * subpopFitness[ind][i];
                    }
                    else{
                        sumFitness = sumFitness + weightOthers * subpopFitness[ind][i];
                    }
                }
                double[] updatedObjectives = {sumFitness};
                ((MultiObjectiveFitness)inds[ind].fitness).setObjectives(state, updatedObjectives);
            }
        }
    }

    //2020.2.18 calculate the pc of the individuals in each subpopulation, respectively. And save them as KNN surrogate model.
    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
    public double[][][][] calculatePCOnAllTasks(final EvolutionState state) {
        PhenoCharacterisation[][] phenoCharacterisations = ((GPRuleEvolutionStateMultiTaskLearningOnlySurrogate)state).phenoCharacterisations;
        int numTasks = state.population.subpops.length;
        //Step 1: calculate the PC on all the subpops
        double[][][][] indsCharListsMultiTree = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][][];
        ArrayList<GPRule> benckmarkRule = new ArrayList<>();

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array

        //Step 1: update the surrogate model for each subpop
        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            Individual bestInd = inds[0];
            ((GPRuleEvolutionStateMultiTaskLearningOnlySurrogate)state).currentSubpop = subpop;

            GPRule[] referenceRule = new GPRule[numTrees];
            for (int treeID = 0; treeID < numTrees; treeID++) {
                referenceRule[treeID] = new GPRule(ruleTypes[treeID], ((GPIndividual) bestInd).trees[treeID]);
            }
            PhenoCharacterisation[] pc = phenoCharacterisations[subpop];
            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
                //this is more general because it handles both the sequencing rules and routing rules.
//                phenoCharacterisation.setReferenceRule(referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
                phenoCharacterisation.setReferenceRule(state, referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
                // todo: need double check by mengxu 2024.08.14
            }
        }

        for(int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            double[][][][] indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][(int) numTasks][]; //save the PC information

            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            ((GPRuleEvolutionStateMultiTaskLearningOnlySurrogate)state).currentSubpop = subpop;
            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                //each individual
                RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
                for (int ind = 0; ind < (int) (inds.length); ind++) {
                    //each task/subpop
                    for (int i = 0; i < state.population.subpops.length; i++) {
                        PhenoCharacterisation[] pc = phenoCharacterisations[i];
                        PhenoCharacterisation phenoCharacterisation = pc[treeID];
                        int[] charList = phenoCharacterisation.characteriseForMultitask(state,  //characterise: calculate the distance
                                new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]), i);
                        indsCharLists[treeID][ind][i] = new double[charList.length];

                        //each PC information---convert int[] to int[][]
                        for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                            indsCharLists[treeID][ind][i][numFeature] = charList[numFeature];
                        }
                    }
                }
            }

            //here, we get the pc of one subpopulation, we need to save it.
            //combine the phenotype of sequencing rule with the phenotype of routing rule
            for (int ind = 0; ind < (int) (inds.length); ind++) {
                double[][] indsCharListsMultiTreeTemp = new double[indsCharLists[0][ind].length][];
//                    indsFitnessesMultiTreeTemp = new double[indsCharLists[0][ind].length][];
                //indsCharLists[0].length == indsCharLists[1].length
                for (int i = 0; i < indsCharLists[0][ind].length; i++) {
                    double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][ind][i], indsCharLists[1][ind][i]);
                    indsCharListsMultiTreeTemp[i] = combinePheChar;

                    //todo: here need to estimate the fitness based on PC similarity
                }
                indsCharListsMultiTree[subpop][ind] = indsCharListsMultiTreeTemp;
            }
        }
        return indsCharListsMultiTree;
    }

    public double[][][] fitnessEstimationOnAllTasks(final EvolutionState state, double[][][][] PCOnAllTasks){
        double[][][] indsFitnessesMultiTree = new double[PCOnAllTasks.length][PCOnAllTasks[0].length][PCOnAllTasks[0][0].length];
        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            double[][][] indsPCsSubpop = PCOnAllTasks[subpop];
            for(int i=0; i<indsPCsSubpop.length; i++){
                double fitness = inds[i].fitness.fitness();
                double[][] eachIndPCsSubpop = indsPCsSubpop[i];
                for(int j=0; j< eachIndPCsSubpop.length; j++){
                    Individual[] inds_j = state.population.subpops[j].individuals; //only one subpop now
                    if(j==subpop){
                        indsFitnessesMultiTree[subpop][i][j] = fitness;
                    }
                    else{
                        double[] pc_i_j = eachIndPCsSubpop[j];
                        double min_dis = Double.MAX_VALUE;
                        int min_index = -1;
                        double[][][] indsPCs_j = PCOnAllTasks[j];
                        for(int m=0; m<indsPCs_j.length; m++){
                            double[] pc_m_0 = indsPCs_j[m][j];
                            double dis  = PhenoCharacterisation.distance(pc_i_j, pc_m_0);
                            if(dis==0){
                                min_dis = dis;
                                min_index = m;
                                break;
                            }
                            if(dis < min_dis){
                                min_dis = dis;
                                min_index = m;
                            }
                        }
                        double objective_j = inds_j[min_index].fitness.fitness();
                        indsFitnessesMultiTree[subpop][i][j] = objective_j;
                    }
                }
            }
        }
        return indsFitnessesMultiTree;
    }

    public double[][][] rankEstimationOnAllTasks(final EvolutionState state, double[][][] estimatedFitness){
        double[][][] indsRanksMultiTree = new double[estimatedFitness.length][estimatedFitness[0].length][];
        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            double[][] indsFitnesssSubpop = estimatedFitness[subpop];
            for(int i=0; i<indsFitnesssSubpop.length; i++){
                double fitness = inds[i].fitness.fitness();
                double[] eachIndFitnessesEachTask = indsFitnesssSubpop[i];
                indsRanksMultiTree[subpop][i] = calculateRanks(eachIndFitnessesEachTask);
            }
        }
        return indsRanksMultiTree;
    }

    public double[] calculateRanks(double[] eachIndFitnessesEachTask) {
        int n = eachIndFitnessesEachTask.length;

        // Create an array of indices from 0 to n-1
        Integer[] indices = IntStream.range(0, n).boxed().toArray(Integer[]::new);

        // Sort indices based on the values in eachIndFitnessesEachTask
        Arrays.sort(indices, Comparator.comparingDouble(i -> eachIndFitnessesEachTask[i]));

        double[] ranks = new double[n];
        // Assign ranks to the elements based on their order
        for (int i = 0; i < n; i++) {
            ranks[indices[i]] = i + 1;
        }

        return ranks;
    }

    public void multitaskEvaluation(EvolutionState state){
        if (numTests > 1)
            expand(state);

        // reset counters.  Only used in multithreading
        individualCounter = 0;
        subPopCounter = 0;

        // start up if single-threaded?
        if (state.evalthreads == 1)
        {
            int[] numinds = new int[state.population.subpops.length];
            int[] from = new int[numinds.length];
            SimpleProblemForm[] probs = new SimpleProblemForm[state.population.subpops.length];

            for(int i = 0; i < numinds.length; i++)
            {
                numinds[i] =  state.population.subpops[i].individuals.length;
                from[i] = 0;

                if (cloneProblem)
                    probs[i] = (SimpleProblemForm)(this.multiproblems[i].clone());
                else
                    probs[i] = (SimpleProblemForm)(this.multiproblems[i]);  // just use the prototype
            }
            evalPopChunk(state, numinds, from, 0, probs);
        }
        else
        {
            ThreadPool.Worker[] threads = new ThreadPool.Worker[state.evalthreads];
            for(int i = 0; i < threads.length; i++)
            {
                SimpleEvaluatorThread run = new SimpleEvaluatorThread();
                run.threadnum = i;
                run.state = state;
                run.prob = (SimpleProblemForm)(p_problem.clone());
                threads[i] = pool.start(run, "ECJ Evaluation Thread " + i);
            }

            // join
            pool.joinAll();
        }

        if (numTests > 1)
            contract(state);
    }

    /** A private helper function for evaluatePopulation which evaluates a chunk
     of individuals in a subpopulation for a given thread. Each subpop evaluate based on each task.
     This is for multitasking problem.
     Although this method is declared
     protected, you should not call it. */

    protected void evalPopChunk(EvolutionState state, int[] numinds, int[] from,
                                int threadnum, SimpleProblemForm[] probs)
    {
        Subpopulation[] subpops = state.population.subpops;
        int len = subpops.length;

        for(int pop=0;pop<len;pop++)
        {
            /** this is very important!!! Need to change for different subpops! by Meng Xu 2024.8.14**/
            ((GPRuleEvolutionStateMultiTaskLearningOnlySurrogate)state).currentSubpop = pop;
            ((Problem)probs[pop]).prepareToEvaluate(state,threadnum);
            // start evaluatin'!
            int fp = from[pop];
            int upperbound = fp+numinds[pop];
            Individual[] inds = subpops[pop].individuals;
            for (int x=fp;x<upperbound;x++)
                probs[pop].evaluate(state,inds[x], pop, threadnum);
            ((Problem)probs[pop]).finishEvaluating(state,threadnum); //todo: need double check by mengxu 2024.8.14
        }
    }

}
