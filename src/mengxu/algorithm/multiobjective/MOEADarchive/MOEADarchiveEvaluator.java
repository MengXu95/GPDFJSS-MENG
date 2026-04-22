package mengxu.algorithm.multiobjective.MOEADarchive;

import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.multiobjective.MOEADmap.MOEADmapInitializer;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MOEADarchiveEvaluator extends SimpleEvaluator{

    /**
     * The original population size is stored here so NSGA2 knows how large to
     * create the archive (it's the size of the original population -- keep in mind
     * that NSGA2Breeder had made the population larger to include the children.
     */
    public int originalPopSize[];

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        Parameter p = new Parameter(MOEADarchiveInitializer.P_POP);
        int subpopsLength = state.parameters.getInt(p.push(Population.P_SIZE), null, 1);
        Parameter p_subpop;
        originalPopSize = new int[subpopsLength];
        for (int i = 0; i < subpopsLength; i++) {
            p_subpop = p.push(Population.P_SUBPOP).push("" + i).push(Subpopulation.P_SUBPOPSIZE);
            originalPopSize[i] = state.parameters.getInt(p_subpop, null, 1);
        }
    }

    /**
     * Evaluates the population, then builds the archive and reduces the population
     * to just the archive.
     */
    public void evaluatePopulation(final EvolutionState state) {
        super.evaluatePopulation(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals



        if(((MOEADarchiveInitializer)state.initializer).normalisation && ((MOEADarchiveInitializer)state.initializer).manualRuleNormalisation){//add by mengxu 2022.10.02
            SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
            AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem)prob).getEvaluationModel();
            SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel)evaluationModel).getSchedulingSet();
            curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
            ((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
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
        else if(((MOEADarchiveInitializer)state.initializer).normalisation && ((MOEADarchiveInitializer)state.initializer).bestObjectiveNormalisation){//add by mengxu 2022.10.06
            RealMatrix adaptLowerBoundMtx = new Array2DRowRealMatrix(((MOEADarchiveInitializer)state.initializer).numObjectives, 1);

            Individual[] inds = state.population.subpops[0].individuals;
            for(int j=0; j<((MOEADarchiveInitializer)state.initializer).numObjectives; j++){
                double max = Double.POSITIVE_INFINITY;
                for(int i=0; i<inds.length; i++) {
                    Individual ind = inds[i];
                    double[] objectivesBeforeNormalisation = ((MultiObjectiveFitness) ind.fitness).getObjectives();
                    if(objectivesBeforeNormalisation[j] < max){
                        max = objectivesBeforeNormalisation[j];
                    }
                }
                max = max * ((MOEADarchiveInitializer)state.initializer).alpha;
                adaptLowerBoundMtx.setEntry(j,0,max);
            }

            ((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = adaptLowerBoundMtx;
        }

        //add by mengxu 2023.01.24
        if(((MOEADarchiveInitializer)state.initializer).normalisation && ((MOEADarchiveInitializer)state.initializer).normalisationBias){
            ArrayList<ArrayList<Double>> allObjectivesAllInds = new ArrayList();
            for(int i=0; i<((MOEADarchiveInitializer)state.initializer).numObjectives; i++){
                ArrayList<Double> allObjectives = new ArrayList();
                allObjectivesAllInds.add(allObjectives);
            }
            Individual[] inds = state.population.subpops[0].individuals;
            for(int i=0; i<inds.length; i++) {
                Individual ind = inds[i];
                double[] objectivesBeforeNormalisation = ((MultiObjectiveFitness) ind.fitness).getObjectives();
                for(int j=0; j<((MOEADarchiveInitializer)state.initializer).numObjectives; j++){
                    allObjectivesAllInds.get(j).add(objectivesBeforeNormalisation[j]);
                }
            }
            //=============================calculate the stand divation of top 20 individuals with the best obj=============================
            if(state.generation>10){
                double std_vs_best = Double.POSITIVE_INFINITY;
                for(int j=0; j<((MOEADarchiveInitializer)state.initializer).numObjectives; j++){
                    Collections.sort(allObjectivesAllInds.get(j));
                    double std_j = calculateSTD(allObjectivesAllInds.get(j).subList(0, ((MOEADarchiveInitializer) state.initializer).topNumber));
                    System.out.println("Objective: " + j + ", " + ((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) + ", " + std_j);
                    double std_vs_best_j = std_j/((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0);
                    double adaptBest = ((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) + std_j;
                    ((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.setEntry(j,0,adaptBest);
                    if(std_vs_best_j<std_vs_best){
                        std_vs_best = std_vs_best_j;
                        ((MOEADarchiveInitializer)state.initializer).biasToObjective = j;
                    }
                }
                System.out.println("Should bias to :" + ((MOEADarchiveInitializer)state.initializer).biasToObjective);
//                for(int j=0; j<((MOEADarchiveInitializer)state.initializer).numObjectives; j++){
//                    if(((MOEADarchiveInitializer)state.initializer).biasToObjective == j){
//                        System.out.println("Should bias to :" + ((MOEADarchiveInitializer)state.initializer).biasToObjective);
//                        double adaptBest = ((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) * ((MOEADarchiveInitializer)state.initializer).biasLevel;
//                        ((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.setEntry(j,0,adaptBest);
//                    }
//                }
            }
            //=============================calculate the stand divation of top 20 individuals with the best obj=============================

            //=============================calculate the scope of top 20 individuals with the best obj=============================
//            double scope = Double.POSITIVE_INFINITY;
//            for(int j=0; j<((MOEADarchiveInitializer)state.initializer).numObjectives; j++){
//                Collections.sort(allObjectivesAllInds.get(j));
//                double scope_j = 0;
//                for(int i=0; i<((MOEADarchiveInitializer)state.initializer).topNumber; i++){
//                    scope_j += (allObjectivesAllInds.get(j).get(i)-((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0))/((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0);
//                }
//                scope_j = scope_j/((MOEADarchiveInitializer)state.initializer).topNumber;
//                if(scope_j<scope){
//                    scope = scope_j;
//                    ((MOEADarchiveInitializer)state.initializer).biasToObjective = j;
//                }
//            }
//            for(int j=0; j<((MOEADarchiveInitializer)state.initializer).numObjectives; j++){
//                if(((MOEADarchiveInitializer)state.initializer).biasToObjective == j){
//                    System.out.println("Bias to :" + ((MOEADarchiveInitializer)state.initializer).biasToObjective);
//                    double adaptBest = ((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) * ((MOEADarchiveInitializer)state.initializer).biasLevel;
//                    ((MOEADarchiveInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx.setEntry(j,0,adaptBest);
//                }
//            }
            //=============================calculate the scope of top 20 individuals with the best obj=============================
        }

        //update idealPoint 2021.11.03
        //todo: need to check this box outliers, I think this has some problems!!! high priority 2022.09.14
//        if(((MOEADarchiveInitializer)state.initializer).useBoxOutliers){
//            ((MOEADarchiveInitializer)state.initializer).updateMultiBoxOutliers(state);//hide by mengxu 2022.09.14
//            ((MOEADarchiveInitializer)state.initializer).updateIdealPointBasedOnBoxOutlier(); //hide by mengxu 2022.08.08
//            ((MOEADarchiveInitializer)state.initializer).updateMaxObjectivesBasedOnBoxOutlier();
//        }
//        else{
//            ((MOEADarchiveInitializer)state.initializer).updateIdealPoint(state);
//            ((MOEADarchiveInitializer)state.initializer).updateMaxObjectives(state);
//        }
//
        Object[] sortedParetoFront = buildParetoFront(state);
//        ((MOEADarchiveInitializer)state.initializer).updateIdealPoint(sortedParetoFront);
        ((MOEADarchiveInitializer)state.initializer).updateNadirPoint(sortedParetoFront);

        //print ideal points and nadir points
        System.out.println("ideal points: [" + ((MOEADarchiveInitializer)state.initializer).idealPoint[0] + ", " + ((MOEADarchiveInitializer)state.initializer).idealPoint[1] + "]");

        System.out.println("nadir points: [" + ((MOEADarchiveInitializer)state.initializer).nadirPoint[0] + ", " + ((MOEADarchiveInitializer)state.initializer).nadirPoint[1] + "]");

        System.out.println("max objectives: [" + ((MOEADarchiveInitializer)state.initializer).maxObjectives[0] + ", " + ((MOEADarchiveInitializer)state.initializer).maxObjectives[1] + "]");


        if(((MOEADarchiveInitializer)state.initializer).useMapping) {
            for (int x = 0; x < state.population.subpops.length; x++)
                state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
        }

    }

    public double calculateSTD(List<Double> data){
        double variance = 0;
        double mean = 0;
        for(int i=0; i< data.size(); i++){
            mean += data.get(i);
        }
        mean = mean/ data.size();
        for(int i=0; i< data.size(); i++){
            variance = variance + (Math.pow((data.get(i)-mean), 2));
        }
        variance = variance/ data.size();
        double std_dev = Math.sqrt(variance);
        return std_dev;
    }


    /**
     * Build the auxiliary fitness data and reduce the subpopulation to just the
     * archive, which is returned.
     */
    //achieve a archive has the same size of original population
    public Individual[] buildArchive(EvolutionState state, int subpop) {

        if(((MOEADarchiveInitializer)state.initializer).useMapping){
            return ((MOEADarchiveInitializer)state.initializer).solutionToSubProblemMatching(state, subpop, originalPopSize[subpop]);//add by mengxu 2022.08.08
//            return ((MOEADarchiveInitializer)state.initializer).solutionToSubProblemMatchingBasedOnAngle(state, subpop, originalPopSize[subpop]);//add by mengxu 2022.08.08
        }
        else {
            System.out.println("((MOEADarchiveInitializer)state.initializer).useMapping must be True!!!");
            return null;
        }
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
