package mengxu.algorithm.multitaskHeuristicLearning;

import ec.*;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.ThreadPool;
import mengxu.algorithm.multiobjective.ParetoSetLearning.ClearingPSLMultiObjectiveFitness;
import mengxu.algorithm.multiobjective.ParetoSetLearning.KNNsurrogateClearingPSLEvaluatorbasedonHV;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLMultiObjectiveFitness;
import org.apache.commons.lang3.ArrayUtils;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

import java.util.ArrayList;

/**
 * The evaluator with multiple tasks (instances) for multiple subpops.
 * The evaluator is used for multitask.
 *
 * Created by Meng Xu on 14/08/2024.
 */
public class MultitaskEvaluatorSurrogate extends SimpleEvaluator {

    public static final String P_PROBLEM_NUM = "problem_number";

    protected int problem_num;

    public Problem[] multiproblems;

    protected boolean multitask;


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

        //Step 1: calculate PC on all tasks!
        double[][][][] PCOnAllTasks = calculatePCOnAllTasks(state);
//        System.out.println("Calculate PC done!");

        //Step 2: estimate fitness based on the PC
        double[][][] estimatedFitness = fitnessEstimationOnAllTasks(state, PCOnAllTasks);
//        System.out.println("Fitness estimation done!");

        //Step 2.1 (option): fitness normalisation
        //with normalisation is worse than without normalisation
//        double[][][] normalisedEstimatedFitness = fitnessNormalisationOnAllTasks(state, estimatedFitness);

        //Step 3: fitness aggregation and update fitness
        //Give the subpop the individual original belongs to the highest percentage (50%), and the other share the remaining 50%
//        fitnessAggregationAndUpdate(state, estimatedFitness);

        //Step 3.1: 2024.9.23 by mengxu
        NonlinearModel nonlinearModel = new NonlinearModel();
        double[][] estimatedFitnessAfterProcessing = dataProcessing(estimatedFitness);
        nonlinearModel.learnModel(estimatedFitnessAfterProcessing[0], estimatedFitnessAfterProcessing[1], estimatedFitnessAfterProcessing[2]);
        ((GPRuleEvolutionStateMultiTaskLearningSurrogate)state).nonlinearModel = nonlinearModel;
        nonlinearModel.printModel();
        //todo: with this model, how to do selection and breeding??
    }

    public double[][] dataProcessing(double[][][] estimatedFitness){
        double[][] estimatedFitnessAfterProcessing = transpose(combineArrays(estimatedFitness[0], estimatedFitness[1], estimatedFitness[2]));

        ArrayList<ArrayList<Double>> estimatedFitnessAfterProcessingArray = new ArrayList<>();
        for(int i=0; i<estimatedFitnessAfterProcessing.length; i++){
            ArrayList<Double> element =  new ArrayList<>();
            estimatedFitnessAfterProcessingArray.add(element);
        }

        for(int i=0; i<estimatedFitnessAfterProcessing[0].length; i++){
            boolean test0 = (estimatedFitnessAfterProcessing[0][i] >= Double.POSITIVE_INFINITY || estimatedFitnessAfterProcessing[0][i] >= Double.MAX_VALUE);
            boolean test1 = (estimatedFitnessAfterProcessing[1][i] >= Double.POSITIVE_INFINITY || estimatedFitnessAfterProcessing[1][i] >= Double.MAX_VALUE);
            boolean test2 = (estimatedFitnessAfterProcessing[2][i] >= Double.POSITIVE_INFINITY || estimatedFitnessAfterProcessing[2][i] >= Double.MAX_VALUE);
            if(!test0 && !test1 && !test2){
                estimatedFitnessAfterProcessingArray.get(0).add(estimatedFitnessAfterProcessing[0][i]);
                estimatedFitnessAfterProcessingArray.get(1).add(estimatedFitnessAfterProcessing[1][i]);
                estimatedFitnessAfterProcessingArray.get(2).add(estimatedFitnessAfterProcessing[2][i]);
            }
        }

        return convertToDoubleArray(estimatedFitnessAfterProcessingArray);
    }

    public static double[][] transpose(double[][] array) {
        int rows = array.length;
        int cols = array[0].length;

        double[][] transposedArray = new double[cols][rows];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                transposedArray[j][i] = array[i][j];
            }
        }

        return transposedArray;
    }


    public double[][] convertToDoubleArray(ArrayList<ArrayList<Double>> arrayList) {
        // Determine the number of rows
        int rows = arrayList.size();
        // Determine the number of columns (assuming all rows are of the same size)
        int cols = arrayList.get(0).size();

        // Initialize the double[][] array
        double[][] result = new double[rows][cols];

        // Populate the double[][] array with values from the ArrayList
        for (int i = 0; i < rows; i++) {
            ArrayList<Double> row = arrayList.get(i);
            for (int j = 0; j < cols; j++) {
                result[i][j] = row.get(j);
            }
        }

        return result;
    }

    public double[][] combineArrays(double[][] array1, double[][] array2, double[][] array3) {
        int totalRows = array1.length + array2.length + array3.length;
        int cols = array1[0].length; // Assuming all arrays have the same number of columns

        double[][] combinedArray = new double[totalRows][cols];

        // Copy rows from array1
        int currentRow = 0;
        for (int i = 0; i < array1.length; i++) {
            System.arraycopy(array1[i], 0, combinedArray[currentRow++], 0, cols);
        }

        // Copy rows from array2
        for (int i = 0; i < array2.length; i++) {
            System.arraycopy(array2[i], 0, combinedArray[currentRow++], 0, cols);
        }

        // Copy rows from array3
        for (int i = 0; i < array3.length; i++) {
            System.arraycopy(array3[i], 0, combinedArray[currentRow++], 0, cols);
        }

        return combinedArray;
    }

    public double[][][] fitnessNormalisationOnAllTasks(EvolutionState state, double[][][] estimatedFitness){
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

    public void fitnessAggregationAndUpdate(final EvolutionState state, double[][][] estimatedFitness){
        int numTasks = estimatedFitness.length;
        //Strategy 1:
//        double weightOriginal = 0.5;
//        double weightOthers = (1-weightOriginal)/(double)(numTasks-1);

//        //Strategy 2:
//        double weightOthers = (double)1/(double)numTasks;
//        double weightOriginal = weightOthers;

        //Strategy 3:
        double weightOthers = 0;
        double weightOriginal = 1;

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
        PhenoCharacterisation[][] phenoCharacterisations = ((GPRuleEvolutionStateMultiTaskLearningSurrogate)state).phenoCharacterisations;
        String P_TREESIZE = "num-trees";
        int numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);
        int numTasks = state.population.subpops.length;
        //Step 1: calculate the PC on all the subpops
        double[][][][] indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][(int) numTasks][]; //save the PC information
        double[][] indsCharListsMultiTreeTemp = null;
        double[][][][] indsCharListsMultiTree = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][][];
        ArrayList<GPRule> benckmarkRule = new ArrayList<>();

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array

        //Step 1: update the surrogate model for each subpop
        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            Individual bestInd = inds[0];
            ((GPRuleEvolutionStateMultiTaskLearningSurrogate)state).currentSubpop = subpop;

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
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            ((GPRuleEvolutionStateMultiTaskLearningSurrogate)state).currentSubpop = subpop;
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
                indsCharListsMultiTreeTemp = new double[indsCharLists[0][ind].length][];
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
            ((GPRuleEvolutionStateMultiTaskLearningSurrogate)state).currentSubpop = pop;
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
