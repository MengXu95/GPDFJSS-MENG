package mengxu.algorithm.multiobjective.ParetoSetLearning;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PreferenceOpposite.surrogateClearingPSLEvaluatorPreOpposite;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.util.ArrayList;
import java.util.List;

public class PSLEvaluator extends SimpleEvaluator{


    public void evaluatePopulation(final EvolutionState state)
    {
        super.evaluatePopulation(state); //the same as normal evaluation

        if (!(this instanceof KNNsurrogateClearingPSLEvaluator) && !(this instanceof surrogateClearingPSLEvaluatorPreOpposite) && !(this instanceof KNNsurrogateClearingPSLEvaluatorbasedonHV)){
            if(((PSLInitializer)state.initializer).normalisation == 1){//add by mengxu 2022.10.02
                SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
                AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem)prob).getEvaluationModel();
                SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel)evaluationModel).getSchedulingSet();
                curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
                ((PSLInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
            }
            else if(((PSLInitializer)state.initializer).normalisation == 2){//add by mengxu 2022.10.06
                RealMatrix adaptLowerBoundMtx = new Array2DRowRealMatrix(((PSLInitializer)state.initializer).numObjectives, 1);

                Individual[] inds = state.population.subpops[0].individuals;
                for(int j=0; j<((PSLInitializer)state.initializer).numObjectives; j++){
                    double max = Double.POSITIVE_INFINITY;
                    for(int i=0; i<inds.length; i++) {
                        Individual ind = inds[i];
                        double[] objectivesBeforeNormalisation = ((MultiObjectiveFitness) ind.fitness).getObjectives();
                        if(objectivesBeforeNormalisation[j] < max){
                            max = objectivesBeforeNormalisation[j];
                        }
                    }
                    adaptLowerBoundMtx.setEntry(j,0,max);
                }

                ((PSLInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = adaptLowerBoundMtx;
            }

            //update idealPoint 2021.11.03
            ((PSLInitializer)state.initializer).updateIdealPoint(state);
            Object[] sortedParetoFront = buildParetoFront(state);
            ((PSLInitializer)state.initializer).updateNadirPoint(sortedParetoFront);
            ((PSLInitializer)state.initializer).updateMaxObjectives(state);

            //print ideal points and nadir points
            System.out.println("ideal points: [" + ((PSLInitializer)state.initializer).idealPoint[0] + ", " + ((PSLInitializer)state.initializer).idealPoint[1] + "]");

            System.out.println("nadir points: [" + ((PSLInitializer)state.initializer).nadirPoint[0] + ", " + ((PSLInitializer)state.initializer).nadirPoint[1] + "]");

            System.out.println("max objectives: [" + ((PSLInitializer)state.initializer).maxObjectives[0] + ", " + ((PSLInitializer)state.initializer).maxObjectives[1] + "]");

            //calculate the augmented fitness value by mengxu 2024.3.12
            Individual[] inds = state.population.subpops[0].individuals;
            for(int i=0; i<inds.length; i++) {
                Individual ind = inds[i];
                ((PSLMultiObjectiveFitness) ind.fitness).calculatePSLFitness(state);
            }
        }


        //force revaluate
//        Individual[] inds = state.population.subpops[0].individuals;
//        for(Individual ind: inds){
//            ind.evaluated = false;
//        }

//        System.out.println("evaluatePopulation");
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

}
