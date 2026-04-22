package mengxu.algorithm.multiobjective.ParetoSetLearning.PreferenceOpposite;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLEvaluator;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLMultiObjectiveFitness;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.multitreeClearingFirst;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class surrogateClearingPSLEvaluatorPreOpposite extends PSLEvaluator {

    public static final String P_RADIUS = "radius";
    public static final String P_CAPACITY = "capacity";

    double[][][] tempfitnessesForModel; //for transfer the fitness into the surrogate model to estimate the fitness
    double[][][] tempindsCharListsMultiTree;

    protected boolean clear = true;

    public static List<Double> entropyDiversity = new ArrayList<>();
    protected static long jobSeed;

    protected double radius;
    protected int capacity;

    //protected PhenoCharacterisation[] phenoCharacterisation;
    public static PhenoCharacterisation[] phenoCharacterisation;

    public double getRadius() {
        return radius;
    }

    public int getCapacity() {
        return capacity;
    }

    public PhenoCharacterisation[] getPhenoCharacterisation() {
        return phenoCharacterisation;
    }

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        radius = state.parameters.getDoubleWithDefault(
                base.push(P_RADIUS), null, 0.0);
        capacity = state.parameters.getIntWithDefault(
                base.push(P_CAPACITY), null, 1);
    }


    public void evaluatePopulation(final EvolutionState state)
    {
            super.evaluatePopulation(state); //the same as normal evaluation

            if(((PSLInitializerPreOpposite)state.initializer).preferenceOpposite){
                ((GPRuleEvolutionStatePSL)state).evaluatePreferenceIndex = state.numGenerations*2-1-state.generation;
                ((GPRuleEvolutionStatePSL)state).evaluateOrder = 1;
                super.evaluatePopulation(state); //the same as normal evaluation todo: need double-check, otherwise will cover the fitness for the first preference
            }

            if (((PSLInitializerPreOpposite) state.initializer).normalisation == 1) {//add by mengxu 2022.10.02
                SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
                AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem) prob).getEvaluationModel();
                SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel) evaluationModel).getSchedulingSet();
                curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
                ((PSLInitializerPreOpposite) state.initializer).curSchedulingSetObjectiveLowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
            } else if (((PSLInitializerPreOpposite) state.initializer).normalisation == 2) {//add by mengxu 2022.10.06
                RealMatrix adaptLowerBoundMtx = new Array2DRowRealMatrix(((PSLInitializerPreOpposite) state.initializer).numObjectives, 1);

                Individual[] inds = state.population.subpops[0].individuals;
                for (int j = 0; j < ((PSLInitializerPreOpposite) state.initializer).numObjectives; j++) {
                    double max = Double.POSITIVE_INFINITY;
                    for (int i = 0; i < inds.length; i++) {
                        Individual ind = inds[i];
                        double[] objectivesBeforeNormalisation = ((MultiObjectiveFitness) ind.fitness).getObjectives();
                        if (objectivesBeforeNormalisation[j] < max) {
                            max = objectivesBeforeNormalisation[j];
                        }
                    }
                    adaptLowerBoundMtx.setEntry(j, 0, max);
                }

                ((PSLInitializerPreOpposite) state.initializer).curSchedulingSetObjectiveLowerBoundMtx = adaptLowerBoundMtx;
            }

            //update idealPoint 2021.11.03
            Object[] sortedParetoFront = buildParetoFront(state);
            Object[] sortedParetoFrontOpposite = buildParetoFrontOpposite(state);
            ((PSLInitializerPreOpposite) state.initializer).updateIdealPoint(state);
            ((PSLInitializerPreOpposite) state.initializer).updateNadirPoint(sortedParetoFront);
            ((PSLInitializerPreOpposite) state.initializer).updateNadirPointOpposite(sortedParetoFrontOpposite);
            ((PSLInitializerPreOpposite) state.initializer).updateMaxObjectives(state);

            //print ideal points and nadir points
            System.out.println("ideal points: [" + ((PSLInitializerPreOpposite) state.initializer).idealPoint[0] + ", " + ((PSLInitializerPreOpposite) state.initializer).idealPoint[1] + "]");

            System.out.println("nadir points: [" + ((PSLInitializerPreOpposite) state.initializer).nadirPoint[0] + ", " + ((PSLInitializerPreOpposite) state.initializer).nadirPoint[1] + "]");

            System.out.println("max objectives: [" + ((PSLInitializerPreOpposite) state.initializer).maxObjectives[0] + ", " + ((PSLInitializerPreOpposite) state.initializer).maxObjectives[1] + "]");

            //calculate the augmented fitness value by mengxu 2024.3.12
            Individual[] inds = state.population.subpops[0].individuals;
            for (int i = 0; i < inds.length; i++) {
                Individual ind = inds[i];
                ((PSLMultiObjectiveFitness) ind.fitness).calculatePSLFitness(state);
            }

//            calculatePC(state);//add by mengxu 2024.3.19
            int numObjective = ((MultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness).getNumObjectives();
            double[][] preferences = new double[10][numObjective];//todo: need to update this
            //the first preference should be the preference of the current preference
            preferences[0]= ((PSLInitializerPreOpposite)state.initializer).weights[state.generation];
            preferences[1]= ((PSLInitializerPreOpposite)state.initializer).weights[state.numGenerations*2-1-state.generation];
            for(int i=0; i< preferences.length-1-1; i++){
                if (numObjective == 2) {
                    double[] weightVector = new double[2];
                    weightVector[0] = i / (double) (preferences.length-1-1-1);
                    weightVector[1] = (preferences.length-1 -1 -1 - i) / (double) (preferences.length-1-1-1);
                    preferences[i+2] = weightVector;
                }
                else{
                    System.out.println("Error in surrogateClearingPSLEvaluator!");
                }
            }

            multitreeClearingFirst.clearPopulationForParetoSetLearning(state, radius, capacity,
                    ((GPRuleEvolutionStatePSL)state).phenoCharacterisation, preferences);

        //force revaluate
//        Individual[] inds = state.population.subpops[0].individuals;
//        for(Individual ind: inds){
//            ind.evaluated = false;
//        }

//        System.out.println("evaluatePopulation");
    }

    public void calculatePC(final EvolutionState state){
        int numObjectives = 1;//this fitness is not the multi-objective fitness but the fitness of augmented Tchebycheff one by mengxu 2024.3.19
        double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(state, phenoCharacterisation, true); //3. calculate the phenotypic characteristic
        //get the fitness for training model
        double[][][] fitnessesForModel = new double[state.population.subpops.length][state.population.subpops[0].individuals.length][numObjectives];

        //fzhang 2021.4.16 calculate the entropy diversity by using phenotypic characteristic
        double diversityValue = PopulationUtils.entropy(indsCharListsMultiTree);
        entropyDiversity.add(diversityValue);
        if(state.generation == state.numGenerations-2){ //do not need to breed in the last generation
            jobSeed = ((GPRuleEvolutionState)state).getJobSeed();
            writeDiversityToFile(jobSeed, state.numGenerations, state.population.subpops.length);
        }

        for(int subpop = 0; subpop < state.population.subpops.length; subpop++){
            for(int ind = 0; ind < state.population.subpops[subpop].individuals.length; ind++){
                for(int fitness = 0; fitness < numObjectives; fitness++){
                    fitnessesForModel[subpop][ind][fitness] = ((MultiObjectiveFitness)(state.population.subpops[subpop].individuals[ind].fitness)).getObjective(fitness);
                }
            }

            // int removeIdx = 0;
            double[][] indsCharListsMultiTreeSubpop = indsCharListsMultiTree[subpop];
            double[][] fitnessesForModelSubpop = fitnessesForModel[subpop];

            for (int i = 0; i < fitnessesForModelSubpop.length; i++) {
                //fzhang 2021.4.15 if either of an individual's objective value is Double.MAX_VALUE, we do not use this individuals in surrogate
                if (fitnessesForModelSubpop[i][0] == Double.MAX_VALUE || fitnessesForModelSubpop[i][0] == Double.POSITIVE_INFINITY) {
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


    public Object[] buildParetoFront(EvolutionState state){
        List<Object[]> sortedFronts = new ArrayList();
        for (int s = 0; s < state.population.subpops.length; s++)
        {
            MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[s].individuals[0].fitness);
            // build front
            ArrayList front = typicalFitness.partitionIntoParetoFront(state.population.subpops[s].individuals, null, null);

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
            sortedFronts.add(sortedFront);
        }
        return sortedFronts.get(0); //todo: only suitable for one subpopulation, need to modify when there are more than one subproblems.
    }

    public Object[] buildParetoFrontOpposite(EvolutionState state){
        List<Object[]> sortedFronts = new ArrayList();
        for (int s = 0; s < state.population.subpops.length; s++)
        {
            ClearingPSLMultiObjectiveFitnessPreOpppsite typicalFitness = (ClearingPSLMultiObjectiveFitnessPreOpppsite)(state.population.subpops[s].individuals[0].fitness);
            // build front
            ArrayList front = typicalFitness.partitionIntoParetoFrontOpposite(state.population.subpops[s].individuals, null, null);

            // sort by objective[0]
            Object[] sortedFront = front.toArray();
            QuickSort.qsort(sortedFront, new SortComparator()
            {
                public boolean lt(Object a, Object b)
                {
                    return (((ClearingPSLMultiObjectiveFitnessPreOpppsite) (((Individual) a).fitness)).getObjectivesOpposite()[0] <
                            (((ClearingPSLMultiObjectiveFitnessPreOpppsite) ((Individual) b).fitness)).getObjectivesOpposite()[0]);
                }

                public boolean gt(Object a, Object b)
                {
                    return (((ClearingPSLMultiObjectiveFitnessPreOpppsite) (((Individual) a).fitness)).getObjectivesOpposite()[0] >
                            ((ClearingPSLMultiObjectiveFitnessPreOpppsite) (((Individual) b).fitness)).getObjectivesOpposite()[0]);
                }
            });
            sortedFronts.add(sortedFront);
        }
        return sortedFronts.get(0); //todo: only suitable for one subpopulation, need to modify when there are more than one subproblems.
    }

    //2021.4.16 fzhang save the diversity value to csv
    public static void writeDiversityToFile(long jobSeed, int numGenerations, int numSubpops) {
        //fzhang 2019.5.21 save the number of cleared individuals
        File weightFile = new File("job." + jobSeed + ".diversity.csv"); // jobSeed = 0
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(weightFile));
            writer.write("Gen, diversitySubpop0");
            writer.newLine();
            for (int i = 0; i < entropyDiversity.size(); i += numSubpops) { //every two into one generation
                //writer.newLine();
                writer.write(i/numSubpops + ", " + entropyDiversity.get(i) + "\n");
            }
            writer.write(numGenerations -1 + ", " + 0 + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
