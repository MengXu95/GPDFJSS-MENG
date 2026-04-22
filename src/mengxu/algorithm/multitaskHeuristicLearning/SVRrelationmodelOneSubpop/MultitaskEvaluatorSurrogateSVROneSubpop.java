package mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodelOneSubpop;

import ec.*;
import ec.gp.GPIndividual;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.ThreadPool;
import mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodel.GPRuleEvolutionStateMultiTaskLearningSurrogateSVR;
import mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodel.SVRmodel;
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
public class MultitaskEvaluatorSurrogateSVROneSubpop extends SimpleEvaluator {

    public int numTrees;

    protected boolean multitask;

    public ArrayList<double[]> estimatedFitness;

    public double[] lowerBound;

    public static final String P_FITNESS_BASED_ON_RANK = "fitness-based-on-rank";
    public boolean fitnessBasedOnRank;

    public ArrayList<double[][]> PC_archive;
    public ArrayList<double[]> fit_archive;

    public int utilisationLevelNum;


    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        this.multitask = true;

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

        String P_UTIL_LEVEL_NUM = "util-level-number";
        Parameter p_util_level_num = new Parameter(P_UTIL_LEVEL_NUM);
        this.utilisationLevelNum = state.parameters.getInt(p_util_level_num,null,1);
        this.lowerBound = new double[this.utilisationLevelNum];

        this.PC_archive = new ArrayList<>(); // add by mengxu 2024.11.30
        this.fit_archive = new ArrayList<>();
    }

    @Override
    public void evaluatePopulation(final EvolutionState state){

        if(state.generation == 3){
            System.out.println("Generation 3");
        }

        super.evaluatePopulation(state);

        System.out.println("Generation: " + state.generation + " task ID: " + ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).currentUtilLevelIndex);
        //update each surrogate model with the best individual at each subpop
        Population pop = state.population;
        PopulationUtils.sort(pop);

//        state.statistics.postEvaluationStatistics(state);

        int numTasks = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).utilisationLevelNum;

        if(state.generation < numTasks){
            // for the first few generations, just collect date
            // double check
            if(this.PC_archive.size()<numTasks){
                //Step 1: calculate PC on current task!
                int currentUtilLevelIndex = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).currentUtilLevelIndex;
                double[][][] PCOnCurrentTask = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][];
                double[][] fitOnCurrentTask = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)];

                calculatePCOnCurrentTask(state, currentUtilLevelIndex, PCOnCurrentTask, fitOnCurrentTask);
                this.PC_archive.add(PCOnCurrentTask[0]);
                this.fit_archive.add(fitOnCurrentTask[0]);
                lowerBoundCalculation(state);
                //the PC_archive is to save the true PC from different generations, and will be used as baseline to find the
                //most similar individuals and their fitness should also be saved.
                //To be noted: the PC_archive is different from PCOnAllTasks, PCOnAllTasks is the PC estimated on all tasks using
                //the current pop.
            }
        }
        else{
            //Step 0: Update the PC_archive
            int currentUtilLevelIndex = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).currentUtilLevelIndex;
            double[][][] PCOnCurrentTask = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][];
            double[][] fitOnCurrentTask = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)];

            calculatePCOnCurrentTask(state, currentUtilLevelIndex, PCOnCurrentTask, fitOnCurrentTask);
            this.PC_archive.set(currentUtilLevelIndex,PCOnCurrentTask[0]);
            this.fit_archive.set(currentUtilLevelIndex,fitOnCurrentTask[0]);
            //Step 1: calculate PC on all tasks!
            ArrayList<double[][]> PCOnAllTasks = new ArrayList<>();
            double[] utilLevelAll = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).utilisationLevels;
            for(int i=0; i<utilLevelAll.length; i++){
                if(i==currentUtilLevelIndex){
                    PCOnAllTasks.add(PCOnCurrentTask[0]);
                }
                else{
//                    double[][][] PCOnOtherTask = calculatePCOnCurrentTask(state, i);
                    double[][][] PCOnOtherTask = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][];
                    double[][] fitOnOtherTask = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)];
                    // here the fitOnOtherTask need to be estimated by SVRmodel later, this one is still the fitness on current task
                    calculatePCOnCurrentTask(state, i, PCOnOtherTask, fitOnOtherTask);
                    PCOnAllTasks.add(PCOnOtherTask[0]);
                }
            }
            ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).PCOnAllTasks = PCOnAllTasks;
//            double[][][][] PCOnAllTasks = calculatePCOnAllTasks(state);
//            ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).PCOnAllTasks = PCOnAllTasks;
            lowerBoundCalculation(state);

            //Step 3.1: 2024.9.23 by mengxu
            SVRmodel svRModel = new SVRmodel();
            ArrayList<double[]> data = dataProcessing(state, this.PC_archive, this.fit_archive);
            try {
                svRModel.SVRmodelTrain(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).svRmodel = svRModel;
            //todo: with this model, how to do selection and breeding??

            this.estimatedFitness = fitnessEstimationOnAllTasksBasedOnSVRModel(state,PCOnAllTasks);
        }

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

    public ArrayList<double[]> dataProcessing(EvolutionState state, double[][][][] PCOnAllTasks){
        ArrayList<double[]> values = new ArrayList<>();
        double[] utilisationLevels = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).utilisationLevels;

        for(int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            //each subpop
            double util = utilisationLevels[subpop];
            for(int j=0; j<inds.length; j++){
                if(inds[j].fitness.fitness() >= Double.POSITIVE_INFINITY || inds[j].fitness.fitness() >= Double.MAX_VALUE){
                    continue;
                }
                else{
                    double[] value = new double[42];
                    value[0] = util;
                    for(int k=0; k<PCOnAllTasks[subpop][j][subpop].length; k++){
                        value[k+1] = PCOnAllTasks[subpop][j][subpop][k];
                    }
                    value[41] = inds[j].fitness.fitness();
                    values.add(value);
                }
            }
        }
        return values;
    }

    public ArrayList<double[]> dataProcessing(EvolutionState state, ArrayList<double[][]> PCOnAllTasks, ArrayList<double[]> fitOnAllTasks){
        ArrayList<double[]> values = new ArrayList<>();
        double[] utilisationLevels = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).utilisationLevels;

        for(int taskID=0; taskID<utilisationLevels.length; taskID++){
            //each task
            double util = utilisationLevels[taskID];
            double[][] PCOntaskID = PCOnAllTasks.get(taskID);
            double[] fitontaskID = fitOnAllTasks.get(taskID);
            for(int j=0; j<PCOntaskID.length; j++){
                if(fitontaskID[j] >= Double.POSITIVE_INFINITY || fitontaskID[j] >= Double.MAX_VALUE){
                    continue;
                }
                else{
                    double[] value = new double[42];
                    value[0] = util;
                    for(int k=0; k<PCOntaskID[j].length; k++){
                        value[k+1] = PCOntaskID[j][k];
                    }
                    value[41] = fitontaskID[j];
                    values.add(value);
                }
            }
        }

        return values;
    }

    // Strategy 1: original based on manual rule
    public void lowerBoundCalculation(EvolutionState state){
        int numTasks = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).utilisationLevelNum;
        //only need to calculate the lower bound for the current subpop
        int currentTaskID = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).currentUtilLevelIndex;
        SimpleProblemForm prob = (SimpleProblemForm) (this.p_problem.clone());
        AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem)prob).getEvaluationModel();
        SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel)evaluationModel).getSchedulingSet();
        curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
        RealMatrix lowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
        lowerBound[currentTaskID] = lowerBoundMtx.getEntry(0,0);
        if(lowerBound[currentTaskID] >= Double.MAX_VALUE || lowerBound[currentTaskID] >= Double.POSITIVE_INFINITY || lowerBound[currentTaskID] <= 0){
            System.out.println("Error in lowerbound!");
        }
    }

    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
    public double[][][][] calculatePCOnAllTasks(final EvolutionState state) {
        PhenoCharacterisation[][] phenoCharacterisations = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).phenoCharacterisations;
        int numTasks = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).utilisationLevelNum;
        double[][][][] indsCharListsMultiTree = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][][];

        //Step 1: calculate the PC on all the subpops
        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array

        //Step 1: update the surrogate model for current util-level/task
        Individual[] inds = state.population.subpops[0].individuals; //only one subpop now
        Individual bestInd = inds[0];
        int currentUtilLevelIndex = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).currentUtilLevelIndex;

        GPRule[] referenceRule = new GPRule[numTrees];
        for (int treeID = 0; treeID < numTrees; treeID++) {
            referenceRule[treeID] = new GPRule(ruleTypes[treeID], ((GPIndividual) bestInd).trees[treeID]);
        }
        PhenoCharacterisation[] pc_current = phenoCharacterisations[currentUtilLevelIndex];
        for (int treeID = 0; treeID < numTrees; treeID++) {//each population
            PhenoCharacterisation phenoCharacterisation = pc_current[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
            //this is more general because it handles both the sequencing rules and routing rules.
//                phenoCharacterisation.setReferenceRule(referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
            phenoCharacterisation.setReferenceRule(state, referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
            // todo: need double check by mengxu 2024.08.14
        }

        //Step 2: calculate the PC and estimate fitness on other tasks/utilisation levels
        double[][][][] indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][(int) numTasks][]; //save the PC information

        for (int treeID = 0; treeID < numTrees; treeID++) {//each population
            //each individual
            RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
            for (int ind = 0; ind < (int) (inds.length); ind++) {
                //each task/subpop
                for (int i = 0; i < numTasks; i++) {
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
            indsCharListsMultiTree[0][ind] = indsCharListsMultiTreeTemp;
        }
        return indsCharListsMultiTree;
    }

    //2020.2.18 calculate the pc of the individuals in each subpopulation, respectively. And save them as KNN surrogate model.
    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
    public void calculatePCOnCurrentTask(final EvolutionState state, int taskID,
                                                 double[][][] PCOnAllTasks,
                                                 double[][] fitOnAllTasks) {
        PhenoCharacterisation[][] phenoCharacterisations = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).phenoCharacterisations;

        //Step 1: calculate the PC on all the subpops
        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array

        //Step 1: update the surrogate model for current util-level/task
//        Individual[] inds = state.population.subpops[0].individuals; //only one subpop now
//        Individual bestInd = inds[0];
//
//        GPRule[] referenceRule = new GPRule[numTrees];
//        for (int treeID = 0; treeID < numTrees; treeID++) {
//            referenceRule[treeID] = new GPRule(ruleTypes[treeID], ((GPIndividual) bestInd).trees[treeID]);
//        }
//        PhenoCharacterisation[] pc = phenoCharacterisations[taskID];
//        for (int treeID = 0; treeID < numTrees; treeID++) {//each population
//            PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
//            //this is more general because it handles both the sequencing rules and routing rules.
////                phenoCharacterisation.setReferenceRule(referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
//            phenoCharacterisation.setReferenceRule(state, referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
//            // todo: need double check by mengxu 2024.08.14
//        }

        Individual[] inds = state.population.subpops[0].individuals; //only one subpop now
        PhenoCharacterisation[] pc = phenoCharacterisations[taskID];

        double[][][] indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][]; //save the PC information

        for (int treeID = 0; treeID < numTrees; treeID++) {//each population
            //each individual
            RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
            for (int ind = 0; ind < (int) (inds.length); ind++) {
                //current task/subpop only
                PhenoCharacterisation phenoCharacterisation = pc[treeID];
                int[] charList = phenoCharacterisation.characteriseForMultitask(state,  //characterise: calculate the distance
                        new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]), taskID);
                indsCharLists[treeID][ind] = new double[charList.length];

                //each PC information---convert int[] to int[][]
                for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                    indsCharLists[treeID][ind][numFeature] = charList[numFeature];
                }
            }
        }

        //here, we get the pc of one subpopulation, we need to save it.
        //combine the phenotype of sequencing rule with the phenotype of routing rule
        for (int ind = 0; ind < (int) (inds.length); ind++) {
            double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][ind], indsCharLists[1][ind]);
            PCOnAllTasks[0][ind] = combinePheChar;
            fitOnAllTasks[0][ind] = inds[ind].fitness.fitness();
        }
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

    public ArrayList<double[]> fitnessEstimationOnAllTasksBasedOnSVRModel(final EvolutionState state, ArrayList<double[][]> PCOnAllTasks){
        SVRmodel relationshipModel = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).svRmodel;
        double[] utilisationLevels = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).utilisationLevels;
        int currentTaskID = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).currentUtilLevelIndex;
        ArrayList<double[]> fitEstimatedOnAllTasks = new ArrayList<>();
        Individual[] inds = state.population.subpops[0].individuals; //only one subpop now
        for(int taskID=0; taskID<utilisationLevels.length; taskID++){
            double[] fitOnAllTasks = new double[inds.length];
            if(taskID == currentTaskID){
                for(int i=0; i<inds.length; i++) {
                    double fitness = inds[i].fitness.fitness();
                    fitOnAllTasks[i] = fitness;
                }
            }
            else{
                for(int i=0; i<inds.length; i++) {
                    double util = utilisationLevels[taskID];
                    double[] PC = PCOnAllTasks.get(taskID)[i];
                    double[] util_PC = new double[PC.length + 2];
                    util_PC[0] = util;
                    for (int k = 0; k < PC.length; k++) {
                        util_PC[k + 1] = PC[k];
                    }
                    util_PC[PC.length + 1] = -1;
                    double estimated_fit = Double.POSITIVE_INFINITY;
                    try {
                        estimated_fit = relationshipModel.predict(util_PC);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    fitOnAllTasks[i] = estimated_fit;
                }
            }
            fitEstimatedOnAllTasks.add(fitOnAllTasks);
        }

        return fitEstimatedOnAllTasks;
    }


    /** A private helper function for evaluatePopulation which evaluates a chunk
     of individuals in a subpopulation for a given thread. Each subpop evaluate based on each task.
     This is for multitasking problem.
     Although this method is declared
     protected, you should not call it. */

//    protected void evalPopChunk(EvolutionState state, int[] numinds, int[] from,
//                                int threadnum, SimpleProblemForm[] probs)
//    {
//        Subpopulation[] subpops = state.population.subpops;
//        int len = subpops.length;
//
//        for(int pop=0;pop<len;pop++)
//        {
//            /** this is very important!!! Need to change for different subpops! by Meng Xu 2024.8.14**/
//            ((Problem)probs[pop]).prepareToEvaluate(state,threadnum);
//            // start evaluatin'!
//            int fp = from[pop];
//            int upperbound = fp+numinds[pop];
//            Individual[] inds = subpops[pop].individuals;
//            for (int x=fp;x<upperbound;x++)
//                probs[pop].evaluate(state,inds[x], pop, threadnum);
//            ((Problem)probs[pop]).finishEvaluating(state,threadnum); //todo: need double check by mengxu 2024.8.14
//        }
//    }

}
