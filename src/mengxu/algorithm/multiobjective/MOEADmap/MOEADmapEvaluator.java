package mengxu.algorithm.multiobjective.MOEADmap;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.QuickSort;
import ec.util.SortComparator;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleevaluation.MultipleTreeMultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

import java.util.ArrayList;
import java.util.List;

public class MOEADmapEvaluator extends SimpleEvaluator{
    public void evaluatePopulation(final EvolutionState state)
    {
        super.evaluatePopulation(state); //the same as normal evaluation

        if(((MOEADmapInitializer)state.initializer).normalisation && ((MOEADmapInitializer)state.initializer).manualRuleNormalisation){//add by mengxu 2022.10.02
            SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
            AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem)prob).getEvaluationModel();
            SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel)evaluationModel).getSchedulingSet();
            curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
            ((MOEADmapInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
//            Individual[] inds = state.population.subpops[0].individuals;
//            for(int i=0; i<inds.length; i++){
//                Individual ind = inds[i];
//                double[] objectivesBeforeNormalisation = ((MultiObjectiveFitness)ind.fitness).getObjectives();
//                double[] objectivesAfterNormalisation = new double[((MultiObjectiveFitness)ind.fitness).getNumObjectives()];
//                for(int j=0; j<((MultiObjectiveFitness)ind.fitness).getNumObjectives(); j++){
//                    //todo: need to modify when evaluate on simulation with more than one replication 2022.10.02
//                    objectivesAfterNormalisation[j] = objectivesBeforeNormalisation[j]/curSchedulingSet.getObjectiveLowerBound(j,0);//0 represent replication 0
//                }
//                ((MultiObjectiveFitness)ind.fitness).setObjectives(state,objectivesAfterNormalisation);
//            }
        }
        else if(((MOEADmapInitializer)state.initializer).normalisation && ((MOEADmapInitializer)state.initializer).bestObjectiveNormalisation){//add by mengxu 2022.10.06
            RealMatrix adaptLowerBoundMtx = new Array2DRowRealMatrix(((MOEADmapInitializer)state.initializer).numObjectives, 1);

            Individual[] inds = state.population.subpops[0].individuals;
            for(int j=0; j<((MOEADmapInitializer)state.initializer).numObjectives; j++){
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

            ((MOEADmapInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = adaptLowerBoundMtx;
        }

        //update idealPoint 2021.11.03
        //todo: need to check this box outliers, I think this has some problems!!! high priority 2022.09.14
//        if(((MOEADmapInitializer)state.initializer).useBoxOutliers){
//            ((MOEADmapInitializer)state.initializer).updateMultiBoxOutliers(state);//hide by mengxu 2022.09.14
//            ((MOEADmapInitializer)state.initializer).updateIdealPointBasedOnBoxOutlier(); //hide by mengxu 2022.08.08
//            ((MOEADmapInitializer)state.initializer).updateMaxObjectivesBasedOnBoxOutlier();
//        }
//        else{
//            ((MOEADmapInitializer)state.initializer).updateIdealPoint(state);
//            ((MOEADmapInitializer)state.initializer).updateMaxObjectives(state);
//        }
//
        Object[] sortedParetoFront = buildParetoFront(state);
        ((MOEADmapInitializer)state.initializer).updateNadirPoint(sortedParetoFront);

        if(((MOEADmapInitializer)state.initializer).useMapping){
//            ((MOEADmapInitializer)state.initializer).solutionToSubProblemMatching(state);//add by mengxu 2022.08.08
            ((MOEADmapInitializer)state.initializer).solutionToSubProblemMatchingBasedOnAngle(state);//add by mengxu 2022.08.08
        }


        //print ideal points and nadir points
        System.out.println("ideal points: [" + ((MOEADmapInitializer)state.initializer).idealPoint[0] + ", " + ((MOEADmapInitializer)state.initializer).idealPoint[1] + "]");

        System.out.println("nadir points: [" + ((MOEADmapInitializer)state.initializer).nadirPoint[0] + ", " + ((MOEADmapInitializer)state.initializer).nadirPoint[1] + "]");

        System.out.println("max objectives: [" + ((MOEADmapInitializer)state.initializer).maxObjectives[0] + ", " + ((MOEADmapInitializer)state.initializer).maxObjectives[1] + "]");

//        //update idealPoint
//        ((MOEADInitializer)state.initializer).updateIdealPoint(state);
//        ((MOEADInitializer)state.initializer).updateNadirPoint(state);

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
