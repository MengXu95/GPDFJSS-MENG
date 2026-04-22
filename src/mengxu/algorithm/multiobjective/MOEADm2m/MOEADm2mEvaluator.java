package mengxu.algorithm.multiobjective.MOEADm2m;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
//import jdk.internal.org.jline.terminal.impl.LineDisciplineTerminal;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MOEADm2mEvaluator extends SimpleEvaluator{
    /**
     * The original population size is stored here so NSGA2 knows how large to
     * create the archive (it's the size of the original population -- keep in mind
     * that NSGA2Breeder had made the population larger to include the children.
     */
    public int originalPopSize[];

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        Parameter p = new Parameter(Initializer.P_POP);
        int subpopsLength = state.parameters.getInt(p.push(Population.P_SIZE), null, 1);
        Parameter p_subpop;
        originalPopSize = new int[subpopsLength];
        for (int i = 0; i < subpopsLength; i++) {
            p_subpop = p.push(Population.P_SUBPOP).push("" + i).push(Subpopulation.P_SUBPOPSIZE);
            originalPopSize[i] = state.parameters.getInt(p_subpop, null, 1);
        }
    }


    public void evaluatePopulation(final EvolutionState state)
    {
        super.evaluatePopulation(state); //the same as normal evaluation

        for (int x = 0; x < state.population.subpops.length; x++)
            state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
        //todo: currently we build archive and get the original popsize individuals and than do allocation I think this way would waste some individuals,
        //      could we using totally 2*popsize individuals to do allocation and then the remaining individuals are deleted
        //      I think this would be a good idea! 2022.10.17 by mengxu
        //      It seems that the results are getting even worse when comparing to not change the order of allocation and buildarchive, which is not expected and weired?
        //      need to check the reasons about this and find if it is the reason of my allocation strategy?
        //      in the original MOEA/D-m2m paper, the subpops are divided by dividing the decision space which is easy, but how to apply this to my problem?


        if(((MOEADm2mInitializer)state.initializer).normalisation && ((MOEADm2mInitializer)state.initializer).manualRuleNormalisation){//add by mengxu 2022.10.02
            SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
            AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem)prob).getEvaluationModel();
            SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel)evaluationModel).getSchedulingSet();
            curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
            ((MOEADm2mInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
        }
        else if(((MOEADm2mInitializer)state.initializer).normalisation && ((MOEADm2mInitializer)state.initializer).bestObjectiveNormalisation){//add by mengxu 2022.10.06
            RealMatrix adaptLowerBoundMtx = new Array2DRowRealMatrix(((MOEADm2mInitializer)state.initializer).numObjectives, 1);

            Individual[] inds = state.population.subpops[0].individuals;
            for(int j=0; j<((MOEADm2mInitializer)state.initializer).numObjectives; j++){
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

            ((MOEADm2mInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = adaptLowerBoundMtx;
        }


        //for MOEAD-m2m, need to set all the individuals to subpops
        allocationOfIndividualsToSubpopulations(state); //added by mengxu 2022.10.11


        //todo: it seems that after the allocation, the last subpop has so many bad run results as we allocate based on the index of subpop!!!
        //todo: need to check the influence of this 2022.10.14
        Object[] sortedParetoFront = buildParetoFront(state);
        ((MOEADm2mInitializer)state.initializer).updateNadirPoint(sortedParetoFront);

        if(((MOEADm2mInitializer)state.initializer).useMapping){
//            ((MOEADmapInitializer)state.initializer).solutionToSubProblemMatching(state);//add by mengxu 2022.08.08
            ((MOEADm2mInitializer)state.initializer).solutionToSubProblemMatchingBasedOnAngle(state);//add by mengxu 2022.08.08
        }


        //print ideal points and nadir points
        System.out.println("ideal points: [" + ((MOEADm2mInitializer)state.initializer).idealPoint[0] + ", " + ((MOEADm2mInitializer)state.initializer).idealPoint[1] + "]");

        System.out.println("nadir points: [" + ((MOEADm2mInitializer)state.initializer).nadirPoint[0] + ", " + ((MOEADm2mInitializer)state.initializer).nadirPoint[1] + "]");

        System.out.println("max objectives: [" + ((MOEADm2mInitializer)state.initializer).maxObjectives[0] + ", " + ((MOEADm2mInitializer)state.initializer).maxObjectives[1] + "]");

    }

//    public void evaluatePopulation(final EvolutionState state)
//    {
//        super.evaluatePopulation(state); //the same as normal evaluation
//
//        for (int x = 0; x < state.population.subpops.length; x++)
//            state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
//        //todo: currently we build archive and get the original popsize individuals and than do allocation I think this way would waste some individuals,
//        //      could we using totally 2*popsize individuals to do allocation and then the remaining individuals are deleted
//        //      I think this would be a good idea! 2022.10.17 by mengxu
//
//
//        if(((MOEADm2mInitializer)state.initializer).normalisation && ((MOEADm2mInitializer)state.initializer).manualRuleNormalisation){//add by mengxu 2022.10.02
//            SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
//            AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem)prob).getEvaluationModel();
//            SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel)evaluationModel).getSchedulingSet();
//            curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
//            ((MOEADm2mInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
////            Individual[] inds = state.population.subpops[0].individuals;
////            for(int i=0; i<inds.length; i++){
////                Individual ind = inds[i];
////                double[] objectivesBeforeNormalisation = ((MultiObjectiveFitness)ind.fitness).getObjectives();
////                double[] objectivesAfterNormalisation = new double[((MultiObjectiveFitness)ind.fitness).getNumObjectives()];
////                for(int j=0; j<((MultiObjectiveFitness)ind.fitness).getNumObjectives(); j++){
////                    //todo: need to modify when evaluate on simulation with more than one replication 2022.10.02
////                    objectivesAfterNormalisation[j] = objectivesBeforeNormalisation[j]/curSchedulingSet.getObjectiveLowerBound(j,0);//0 represent replication 0
////                }
////                ((MultiObjectiveFitness)ind.fitness).setObjectives(state,objectivesAfterNormalisation);
////            }
//        }
//        else if(((MOEADm2mInitializer)state.initializer).normalisation && ((MOEADm2mInitializer)state.initializer).bestObjectiveNormalisation){//add by mengxu 2022.10.06
//            RealMatrix adaptLowerBoundMtx = new Array2DRowRealMatrix(((MOEADm2mInitializer)state.initializer).numObjectives, 1);
//
//            Individual[] inds = state.population.subpops[0].individuals;
//            for(int j=0; j<((MOEADm2mInitializer)state.initializer).numObjectives; j++){
//                double max = Double.POSITIVE_INFINITY;
//                for(int i=0; i<inds.length; i++) {
//                    Individual ind = inds[i];
//                    double[] objectivesBeforeNormalisation = ((MultiObjectiveFitness) ind.fitness).getObjectives();
//                    if(objectivesBeforeNormalisation[j] < max){
//                        max = objectivesBeforeNormalisation[j];
//                    }
//                }
//                adaptLowerBoundMtx.setEntry(j,0,max);
//            }
//
//            ((MOEADm2mInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx = adaptLowerBoundMtx;
//        }
//
//        //update idealPoint 2021.11.03
//        //todo: need to check this box outliers, I think this has some problems!!! high priority 2022.09.14
////        if(((MOEADmapInitializer)state.initializer).useBoxOutliers){
////            ((MOEADmapInitializer)state.initializer).updateMultiBoxOutliers(state);//hide by mengxu 2022.09.14
////            ((MOEADmapInitializer)state.initializer).updateIdealPointBasedOnBoxOutlier(); //hide by mengxu 2022.08.08
////            ((MOEADmapInitializer)state.initializer).updateMaxObjectivesBasedOnBoxOutlier();
////        }
////        else{
////            ((MOEADmapInitializer)state.initializer).updateIdealPoint(state);
////            ((MOEADmapInitializer)state.initializer).updateMaxObjectives(state);
////        }
////
//
//        //for MOEAD-m2m, need to set all the individuals to subpops
//        allocationOfIndividualsToSubpopulations(state); //added by mengxu 2022.10.11
//        //todo: it seems that after the allocation, the last subpop has so many bad run results as we allocate based on the index of subpop!!!
//        //todo: need to check the influence of this 2022.10.14
//        Object[] sortedParetoFront = buildParetoFront(state);
//        ((MOEADm2mInitializer)state.initializer).updateNadirPoint(sortedParetoFront);
//
//        if(((MOEADm2mInitializer)state.initializer).useMapping){
////            ((MOEADmapInitializer)state.initializer).solutionToSubProblemMatching(state);//add by mengxu 2022.08.08
//            ((MOEADm2mInitializer)state.initializer).solutionToSubProblemMatchingBasedOnAngle(state);//add by mengxu 2022.08.08
//        }
//
//
//        //print ideal points and nadir points
//        System.out.println("ideal points: [" + ((MOEADm2mInitializer)state.initializer).idealPoint[0] + ", " + ((MOEADm2mInitializer)state.initializer).idealPoint[1] + "]");
//
//        System.out.println("nadir points: [" + ((MOEADm2mInitializer)state.initializer).nadirPoint[0] + ", " + ((MOEADm2mInitializer)state.initializer).nadirPoint[1] + "]");
//
//        System.out.println("max objectives: [" + ((MOEADm2mInitializer)state.initializer).maxObjectives[0] + ", " + ((MOEADm2mInitializer)state.initializer).maxObjectives[1] + "]");
//
////        //update idealPoint
////        ((MOEADInitializer)state.initializer).updateIdealPoint(state);
////        ((MOEADInitializer)state.initializer).updateNadirPoint(state);
//
//        //force revaluate
////        Individual[] inds = state.population.subpops[0].individuals;
////        for(Individual ind: inds){
////            ind.evaluated = false;
////        }
//
////        System.out.println("evaluatePopulation");
//    }

//    public void allocationOfIndividualsToSubpopulations(EvolutionState state){
//        List<Individual> allIndividuals = new ArrayList<>();
//
//        int numSubpop = state.population.subpops.length;
//        for(int i=0; i<numSubpop; i++){
//            Individual[] individuals = state.population.subpops[i].individuals;
//            allIndividuals.addAll(Arrays.asList(individuals));
//        }
//
//        List<List<Individual>> allIndividualsSubpop = new ArrayList<>(5);
//
//        for(int i=0; i<numSubpop; i++){//map the individuals with subpop
//            List<Individual> individualsSubpop = new ArrayList<>();
//
//            for(int j=0; j<allIndividuals.size(); j++){
//                if(ifInSubpopRegion(state, i, allIndividuals.get(j))){
//                    individualsSubpop.add(allIndividuals.get(j));
//                    allIndividuals.remove(j);
//                    j--;
//                }
//            }
//
//            allIndividualsSubpop.set(i,individualsSubpop);
//        }
//
//        List<Individual> remainingIndividuals = new ArrayList<>();
//        for(int i=0; i<numSubpop; i++){
//            Individual[] oldInds = state.population.subpops[i].individuals;
//            Individual[] newInds = state.population.subpops[i].individuals.clone();//modified by mengxu
//            List<Individual> individualsSubpop = allIndividualsSubpop.get(i);
//
//            if(individualsSubpop.size() > oldInds.length){
//                //build Pareto front
//                MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[0].individuals[0].fitness);
//                // build front
//                ArrayList front = typicalFitness.partitionIntoParetoFront((Individual[]) individualsSubpop.toArray(), null, null);
//
//                // sort by objective[0]
//                Object[] sortedFront = front.toArray();
//                QuickSort.qsort(sortedFront, new SortComparator()
//                {
//                    public boolean lt(Object a, Object b)
//                    {
//                        return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) <
//                                (((MultiObjectiveFitness) ((Individual) b).fitness)).getObjective(0));
//                    }
//
//                    public boolean gt(Object a, Object b)
//                    {
//                        return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) >
//                                ((MultiObjectiveFitness) (((Individual) b).fitness)).getObjective(0));
//                    }
//                });
//
//                int p = 0;
//                for(p = 0; p < newInds.length; p++){
//                    newInds[p] = (Individual) sortedFront[p];
//                }
//                while(p < sortedFront.length){
//                    remainingIndividuals.add((Individual) sortedFront[p]);
//                    p++;
//                }
//            }
//            state.population.subpops[i].individuals = newInds;
//        }
//
//        for(int i=0; i<numSubpop; i++){
//            Individual[] oldInds = state.population.subpops[i].individuals;
//            Individual[] newInds = state.population.subpops[i].individuals.clone();//modified by mengxu
//            List<Individual> individualsSubpop = allIndividualsSubpop.get(i);
//
//            if(individualsSubpop.size() < oldInds.length){
//                int p = 0;
//                for(p = 0; p < individualsSubpop.size(); p++){
//                    newInds[p] = individualsSubpop.get(p);
//                }
//                while(p < oldInds.length){
//                    int randomIndex = state.random[0].nextInt(remainingIndividuals.size());
//                    newInds[p] = remainingIndividuals.get(randomIndex);
//
//                    remainingIndividuals.remove(randomIndex);
//                    p++;
//                }
//            }
//            state.population.subpops[i].individuals = newInds;
//        }
//
//        //code on here, need to check below and keep writing the remaining part
//    }

    public void allocationOfIndividualsToSubpopulations(EvolutionState state){
        List<Individual> allIndividuals = new ArrayList<>();

        int numSubpop = state.population.subpops.length;
        for(int i=0; i<numSubpop; i++){
            Individual[] individuals = state.population.subpops[i].individuals;
            allIndividuals.addAll(Arrays.asList(individuals));
        }

        List<List<Individual>> allIndividualsSubpop = new ArrayList<>();
        for(int i=0; i<numSubpop; i++){
            List<Individual> individualsSubpop = new ArrayList<>();
            allIndividualsSubpop.add(individualsSubpop);
        }
//        List<List<Individual>> allIndividualsSubpop = new ArrayList<>();

        int[] permutation = new int[numSubpop]; //add by mengxu 2022.10.15
        MOEADUtils.randomPermutation(state, 0, permutation, numSubpop); //add by mengxu 2022.10.15

        for(int i=0; i<numSubpop; i++){//map the individuals with subpop
            int indexSubpop = permutation[i]; //add by mengxu 2022.10.15

            List<Individual> individualsSubpop = new ArrayList<>();

            for(int j=0; j<allIndividuals.size(); j++){
                if(ifInSubpopRegion(state, indexSubpop, allIndividuals.get(j))){
                    individualsSubpop.add(allIndividuals.get(j));
                    allIndividuals.remove(j);
                    j--;
                }
            }

            allIndividualsSubpop.set(indexSubpop,individualsSubpop);
        }

        int[] permutationAgain = new int[numSubpop]; //add by mengxu 2022.10.15
        MOEADUtils.randomPermutation(state, 0, permutationAgain, numSubpop); //add by mengxu 2022.10.15

        List<Individual> remainingIndividuals = new ArrayList<>();
        for(int i=0; i<numSubpop; i++){
            int indexSubpop = permutationAgain[i]; //add by mengxu 2022.10.15

//            Individual[] oldInds = state.population.subpops[indexSubpop].individuals;
//            Individual[] newInds = state.population.subpops[indexSubpop].individuals.clone();//modified by mengxu
            List<Individual> individualsSubpop = allIndividualsSubpop.get(indexSubpop);

            if(individualsSubpop.size() >= originalPopSize[indexSubpop]){
                Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
                //each rank cosists of the corresponding individuals
                ArrayList newSubpopulation = new ArrayList(); //a new one, size = 0
                //sort the individuals
                //todo: need to check if the sorting method is right
                Individual[] individualsSubpopAfterSort = PopulationUtils.sortIndsForMOEADm2m(individualsSubpop);

                int p = 0;
                for(p = 0; p < originalPopSize[indexSubpop]; p++){
//                    newInds[p] = individualsSubpopAfterSort[p];
                    newSubpopulation.add(individualsSubpopAfterSort[p]);
                }
                while(p < individualsSubpopAfterSort.length){
                    remainingIndividuals.add(individualsSubpopAfterSort[p]);
                    p++;
                }
                state.population.subpops[indexSubpop].individuals = (Individual[]) (newSubpopulation.toArray(dummy));
            }
//            state.population.subpops[indexSubpop].individuals = newInds;
        }
        //todo: there might remaining bad run in allIndividuals, need to add to the remainingIndividuals
        remainingIndividuals.addAll(allIndividuals);

        int[] permutationAgain2 = new int[numSubpop]; //add by mengxu 2022.10.15
        MOEADUtils.randomPermutation(state, 0, permutationAgain2, numSubpop); //add by mengxu 2022.10.15

        for(int i=0; i<numSubpop; i++){
            int indexSubpop = permutationAgain2[i]; //add by mengxu 2022.10.15

//            Individual[] oldInds = state.population.subpops[indexSubpop].individuals;
//            Individual[] newInds = state.population.subpops[indexSubpop].individuals.clone();//modified by mengxu

            List<Individual> individualsSubpop = allIndividualsSubpop.get(indexSubpop);

            if(individualsSubpop.size() < originalPopSize[indexSubpop]){
                Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
                //each rank cosists of the corresponding individuals
                ArrayList newSubpopulation = new ArrayList(); //a new one, size = 0
                int p = 0;
                for(p = 0; p < individualsSubpop.size(); p++){
//                    newInds[p] = individualsSubpop.get(p);
                    newSubpopulation.add(individualsSubpop.get(p));
                }
                while(p < originalPopSize[indexSubpop]){
                    int randomIndex = state.random[0].nextInt(remainingIndividuals.size());
//                    newInds[p] = remainingIndividuals.get(randomIndex);
                    newSubpopulation.add(remainingIndividuals.get(randomIndex));

                    remainingIndividuals.remove(randomIndex);
                    p++;
                }
                state.population.subpops[indexSubpop].individuals = (Individual[]) (newSubpopulation.toArray(dummy));
            }
        }

        //code on here, need to check below and keep writing the remaining part
    }


    //todo: still need to check if this is right, added by mengxu 2022.10.11
    public boolean ifInSubpopRegion(EvolutionState state, int subpopID, Individual individual){
        boolean inRegion = true;

        double[] problemWeightsUpper = ((MOEADm2mInitializer)state.initializer).weights[subpopID];
        double[] problemWeightsLower = ((MOEADm2mInitializer)state.initializer).weights[subpopID+1];

        int numObjectives = ((MOEADm2mInitializer)state.initializer).numObjectives;
        double[] idealPoint = ((MOEADm2mInitializer)state.initializer).idealPoint;

        boolean normalisation = ((MOEADm2mInitializer)state.initializer).normalisation;
        boolean manualRuleNormalisation = ((MOEADm2mInitializer)state.initializer).manualRuleNormalisation;
        boolean bestObjectiveNormalisation = ((MOEADm2mInitializer)state.initializer).bestObjectiveNormalisation;
        RealMatrix curSchedulingSetObjectiveLowerBoundMtx = ((MOEADm2mInitializer)state.initializer).curSchedulingSetObjectiveLowerBoundMtx;


        double[] fit = ((MultiObjectiveFitness)individual.fitness).getObjectives();

        if(fit[0] >= Double.POSITIVE_INFINITY || fit[0] >= Double.MAX_VALUE){
            return false;
        }

        for(int j=0; j<numObjectives; j++) {
            for(int m=j+1; m<numObjectives; m++) {
                if (j != m) {
                    if(normalisation && manualRuleNormalisation){//add by mengxu 2022.10.06
                        double objective_m = Math.abs(fit[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0) - idealPoint[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0)) - 1; // -1 is added by mengxu 2022.10.26
                        double objective_j = Math.abs(fit[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) - idealPoint[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0)) - 1; // -1 is added by mengxu 2022.10.26
                        double angle = Math.atan(objective_m/objective_j);

                        if(fit[j] == idealPoint[j] && fit[m] == idealPoint[m]){
                            angle = 1.0; //todo: need to check if this should be angle = 1.0??but I think as currently idealpoint is [0.0, 0.0] do not matter
                        }

                        //todo: need to check for the original MOEADmap with the angle methods 2022.10.11
                        //this gives really bad results
//                        double idealAngleUpper = Math.atan(problemWeightsUpper[m] / problemWeightsUpper[j]);
//                        double idealAngleLower = Math.atan(problemWeightsLower[m] / problemWeightsLower[j]);

                        //this is tried by mengxu 2022.10.25
                        double idealAngleLower = Math.atan(problemWeightsUpper[j] / problemWeightsUpper[m]);
                        double idealAngleUpper = Math.atan(problemWeightsLower[j] / problemWeightsLower[m]);


                        if(angle >= idealAngleLower && angle <= idealAngleUpper){
                            continue;
                        }
                        else{
                            return false;
                        }
                    }
                    else if(normalisation && bestObjectiveNormalisation){//add by mengxu 2022.10.06
                        double objective_m = Math.abs(fit[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0) - idealPoint[m]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(m,0)) - 1; // -1 is added by mengxu 2022.10.26
                        double objective_j = Math.abs(fit[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0) - idealPoint[j]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(j,0)) - 1; // -1 is added by mengxu 2022.10.26
                        double angle = Math.atan(objective_m/objective_j);
                        //todo: seems the mapping strategy is not good here!!! need to check again! 2022.10.17
                        //todo: it is weired! After the modification, it seems that the performance get worse !!!!
                        //todo this is not right! need to revise!!!
                        if(fit[j] == idealPoint[j] && fit[m] == idealPoint[m]){
                            angle = 1.0;
                        }

                        //this gives really bad results
//                        double idealAngleUpper = Math.atan(problemWeightsUpper[m] / problemWeightsUpper[j]);
//                        double idealAngleLower = Math.atan(problemWeightsLower[m] / problemWeightsLower[j]);

                        //this is tried by mengxu 2022.10.25
                        double idealAngleLower = Math.atan(problemWeightsUpper[j] / problemWeightsUpper[m]);
                        double idealAngleUpper = Math.atan(problemWeightsLower[j] / problemWeightsLower[m]);

                        if(angle > idealAngleLower && angle <= idealAngleUpper){
                            continue;
                        }
                        else{
                            return false;
                        }
                    }
                    else{
                        double angle = Math.abs(fit[m] - idealPoint[m]) / Math.abs(fit[j] - idealPoint[j]);
                        if (fit[j] == idealPoint[j] && fit[m] == idealPoint[m]) {
                            angle = 1.0;
                        }

                        //this gives really bad results
//                        double idealAngleUpper = Math.atan(problemWeightsUpper[m] / problemWeightsUpper[j]);
//                        double idealAngleLower = Math.atan(problemWeightsLower[m] / problemWeightsLower[j]);

                        //this is tried by mengxu 2022.10.25
                        double idealAngleLower = problemWeightsUpper[j] / problemWeightsUpper[m];
                        double idealAngleUpper = problemWeightsLower[j] / problemWeightsLower[m];

                        if(angle > idealAngleLower && angle <= idealAngleUpper){
                            continue;
                        }
                        else{
                            return false;
                        }

                        //the following is the original
//                        double angle = Math.atan(Math.abs(fit[m] - idealPoint[m]) / Math.abs(fit[j] - idealPoint[j]));
//                        if (fit[j] == idealPoint[j] && fit[m] == idealPoint[m]) {
//                            angle = 1.0;
//                        }
//
//                        //this gives really bad results
////                        double idealAngleUpper = Math.atan(problemWeightsUpper[m] / problemWeightsUpper[j]);
////                        double idealAngleLower = Math.atan(problemWeightsLower[m] / problemWeightsLower[j]);
//
//                        //this is tried by mengxu 2022.10.25
//                        double idealAngleLower = Math.atan(problemWeightsUpper[j] / problemWeightsUpper[m]);
//                        double idealAngleUpper = Math.atan(problemWeightsLower[j] / problemWeightsLower[m]);
//
//                        if(angle > idealAngleLower && angle <= idealAngleUpper){
//                            continue;
//                        }
//                        else{
//                            return false;
//                        }
                    }
                }
            }
        }
        return inRegion;
    }

    public Object[] buildParetoFront(EvolutionState state){//todo: need to modify for multi-subpop 2022.10.11
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
        return sortedFronts.get(0); //todo: only suitable for one subpopulation, need to modify when there are more than one subpopulations.
    }

    /**
     * Evaluates the population, then builds the archive and reduces the population
     * to just the archive.
     */
//    public void evaluatePopulation(final EvolutionState state) {
//        super.evaluatePopulation(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals
//        for (int x = 0; x < state.population.subpops.length; x++)
//            state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
//    }

    /**
     * Build the auxiliary fitness data and reduce the subpopulation to just the
     * archive, which is returned.
     */
    //achieve a archive has the same size of original population
    public Individual[] buildArchive(EvolutionState state, int subpop) {
        Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
        ArrayList ranks = assignFrontRanks(state.population.subpops[subpop]); //after this, get different several ranks
        //each rank cosists of the corresponding individuals

        ArrayList newSubpopulation = new ArrayList(); //a new one, size = 0
        int size = ranks.size();
        for (int i = 0; i < size; i++) { //do for each rank separately
            Individual[] rank = (Individual[]) ((ArrayList) (ranks.get(i))).toArray(dummy);
            assignSparsity(rank); //assign sparity value for each individual in this rank
//            if (rank.length + newSubpopulation.size() >= originalPopSize[subpop]) {
//                // first sort the rank by sparsity---from the small one to large one
//                ec.util.QuickSort.qsort(rank, new SortComparator() {
//                    public boolean lt(Object a, Object b) { //Returns true if a < b, else false
//                        Individual i1 = (Individual) a;
//                        Individual i2 = (Individual) b;
//                        return (((MOEADm2mMultiObjectiveFitness) i1.fitness).sparsity > ((MOEADm2mMultiObjectiveFitness) i2.fitness).sparsity);
//                    }
//
//                    public boolean gt(Object a, Object b) { //Returns true if a > b, else false
//                        Individual i1 = (Individual) a;
//                        Individual i2 = (Individual) b;
//                        return (((MOEADm2mMultiObjectiveFitness) i1.fitness).sparsity < ((MOEADm2mMultiObjectiveFitness) i2.fitness).sparsity);
//                    }
//                });
//
//                // then put the m sparsest individuals in the new population
//                int m = originalPopSize[subpop] - newSubpopulation.size(); //how many positions left for new individuals
//                for (int j = 0; j < m; j++)
//                    newSubpopulation.add(rank[j]); //add some individuals based on the sparisity

                // and bail
//                break;
//            } else {
//                // dump in everyone
                for (int j = 0; j < rank.length; j++) //add the while rank directly
                    newSubpopulation.add(rank[j]);
//            }
        }

        Individual[] archive = (Individual[]) (newSubpopulation.toArray(dummy));

//        // maybe force reevaluation
//        MOEADm2mBreeder breeder = (MOEADm2mBreeder) (state.breeder);
//        if (breeder.reevaluateElites[subpop])
//            for (int i = 0; i < archive.length; i++)
//                archive[i].evaluated = false;

        return archive;
    }


    /**
     * Build the auxiliary fitness data and reduce the subpopulation to just the
     * archive, which is returned.
     */
    //achieve a archive has the same size of original population
//    public Individual[] buildArchive(EvolutionState state, int subpop) {
//        Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
//        ArrayList ranks = assignFrontRanks(state.population.subpops[subpop]); //after this, get different several ranks
//        //each rank cosists of the corresponding individuals
//
//        ArrayList newSubpopulation = new ArrayList(); //a new one, size = 0
//        int size = ranks.size();
//        for (int i = 0; i < size; i++) { //do for each rank separately
//            Individual[] rank = (Individual[]) ((ArrayList) (ranks.get(i))).toArray(dummy);
//            assignSparsity(rank); //assign sparity value for each individual in this rank
//            if (rank.length + newSubpopulation.size() >= originalPopSize[subpop]) {
//                // first sort the rank by sparsity---from the small one to large one
//                ec.util.QuickSort.qsort(rank, new SortComparator() {
//                    public boolean lt(Object a, Object b) { //Returns true if a < b, else false
//                        Individual i1 = (Individual) a;
//                        Individual i2 = (Individual) b;
//                        return (((MOEADm2mMultiObjectiveFitness) i1.fitness).sparsity > ((MOEADm2mMultiObjectiveFitness) i2.fitness).sparsity);
//                    }
//
//                    public boolean gt(Object a, Object b) { //Returns true if a > b, else false
//                        Individual i1 = (Individual) a;
//                        Individual i2 = (Individual) b;
//                        return (((MOEADm2mMultiObjectiveFitness) i1.fitness).sparsity < ((MOEADm2mMultiObjectiveFitness) i2.fitness).sparsity);
//                    }
//                });
//
//                // then put the m sparsest individuals in the new population
//                int m = originalPopSize[subpop] - newSubpopulation.size(); //how many positions left for new individuals
//                for (int j = 0; j < m; j++)
//                    newSubpopulation.add(rank[j]); //add some individuals based on the sparisity
//
//                // and bail
//                break;
//            } else {
//                // dump in everyone
//                for (int j = 0; j < rank.length; j++) //add the while rank directly
//                    newSubpopulation.add(rank[j]);
//            }
//        }
//
//        Individual[] archive = (Individual[]) (newSubpopulation.toArray(dummy));
//
//        // maybe force reevaluation
//        MOEADm2mBreeder breeder = (MOEADm2mBreeder) (state.breeder);
//        if (breeder.reevaluateElites[subpop])
//            for (int i = 0; i < archive.length; i++)
//                archive[i].evaluated = false;
//
//        return archive;
//    }

    /**
     * Divides inds into ranks and assigns each individual's rank to be the rank it
     * was placed into. Each front is an ArrayList.
     */
    public ArrayList assignFrontRanks(Subpopulation subpop) {
        Individual[] inds = subpop.individuals; //inds includes all individuals
        ArrayList frontsByRank = MultiObjectiveFitness.partitionIntoRanks(inds);

        int numRanks = frontsByRank.size();
        for (int rank = 0; rank < numRanks; rank++) {
            ArrayList front = (ArrayList) (frontsByRank.get(rank));
            int numInds = front.size();
            for (int ind = 0; ind < numInds; ind++)
                ((MOEADm2mMultiObjectiveFitness) (((Individual) (front.get(ind))).fitness)).rank = rank;
        }
        return frontsByRank;
    }

    /**
     * Computes and assigns the sparsity values of a given front.
     */
    public void assignSparsity(Individual[] front) {
        int numObjectives = ((MOEADm2mMultiObjectiveFitness) front[0].fitness).getObjectives().length;

        //front here means different fronts
        for (int i = 0; i < front.length; i++) //front.length means how many individuals in this ranking(front)
            ((MOEADm2mMultiObjectiveFitness) front[i].fitness).sparsity = 0; //the first individual in this front, the sparsity
        //is assigned to 0

        for (int i = 0; i < numObjectives; i++) {
            final int o = i;
            // 1. Sort front by each objective.
            // 2. Sum the manhattan distance of an individual's neighbours over
            // each objective.
            // NOTE: No matter which objectives objective you sort by, the
            // first and last individuals will always be the same (they maybe
            // interchanged though). This is because a Pareto front's
            // objective values are strictly increasing/decreasing.
            ec.util.QuickSort.qsort(front, new SortComparator() {
                public boolean lt(Object a, Object b) { //less than
                    Individual i1 = (Individual) a;
                    Individual i2 = (Individual) b;
                    return (((MOEADm2mMultiObjectiveFitness) i1.fitness)
                            .getObjective(o) < ((MOEADm2mMultiObjectiveFitness) i2.fitness).getObjective(o));
                }

                public boolean gt(Object a, Object b) { //great than
                    Individual i1 = (Individual) a;
                    Individual i2 = (Individual) b;
                    return (((MOEADm2mMultiObjectiveFitness) i1.fitness)
                            .getObjective(o) > ((MOEADm2mMultiObjectiveFitness) i2.fitness).getObjective(o));
                }
            });

            // Compute and assign sparsity.
            // the first and last individuals are the sparsest.
            ((MOEADm2mMultiObjectiveFitness) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
            ((MOEADm2mMultiObjectiveFitness) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
            for (int j = 1; j < front.length - 1; j++) {
                MOEADm2mMultiObjectiveFitness f_j = (MOEADm2mMultiObjectiveFitness) (front[j].fitness);
                MOEADm2mMultiObjectiveFitness f_jplus1 = (MOEADm2mMultiObjectiveFitness) (front[j + 1].fitness);
                MOEADm2mMultiObjectiveFitness f_jminus1 = (MOEADm2mMultiObjectiveFitness) (front[j - 1].fitness);

//				System.out.println(f_j.maxObjective[o] - f_j.minObjective[o]);  //1
                // store the NSGA2Sparsity in sparsity
                f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o))
                        / (f_j.maxObjective[o] - f_j.minObjective[o]);
            }
        }
    }

    //fzhang 2018.11.6 NSGA-II
    public void evaluatePopulationgp(final EvolutionState state) {
        super.evaluatePopulation(state);// evaluate population for GP with NSGA2
    }

}
