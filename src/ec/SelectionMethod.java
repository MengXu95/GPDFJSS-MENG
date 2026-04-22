/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package ec;

/* 
 * SelectionMethod.java
 * 
 * Created: Mon Aug 30 19:19:56 1999
 * By: Sean Luke
 */

import ec.util.Parameter;
import mengxu.algorithm.MAPElites.CVTMAPElites.GPRuleEvolutionStateCVTMAPElites;
import mengxu.algorithm.MAPElites.CVTMAPElites.MultiCaseBehaviour.GPRuleEvolutionStateCVTMultiCaseMAPElites;
import mengxu.algorithm.MAPElites.GridMapElites.GPRuleEvolutionStateGridMAPElites;
import mengxu.algorithm.averageFitness.AverageMultiCaseMultiObjectiveFitness;
import mengxu.algorithm.averageFitness.GPRuleEvolutionStateAverage;
import mengxu.algorithm.clusterselection.ClusterSelection;
import mengxu.algorithm.clusterselection.SameClusterSelection;
import mengxu.algorithm.clusterselection.multiplecasecluster.ClusterSelectionMultiCase;
import mengxu.algorithm.clusterselection.multiplecasecluster.GPRuleEvolutionStateMultiCaseCluster;
import mengxu.algorithm.diversepartnerselection.DiversePartnerSelection;
import mengxu.algorithm.diversepartnerselection.GPRuleEvolutionStateDPS;
import mengxu.algorithm.diversepartnerselection.baselineTourWithBRandKNNSurrogate.GPRuleEvolutionStateBase;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.DiversePartnerSelectionNS;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.GPRuleEvolutionStateDPSNS;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.focusOnPoorCase.DiversePartnerSelectionPC;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.focusOnPoorCase.GPRuleEvolutionStateDPSPC;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNCaseSurrogate.GPRuleEvolutionStateDPSNSBRKNNCase;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNSurrogate.GPRuleEvolutionStateDPSNSBR;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNSurrogate.OffspringSelectionStrategyForTopTwoCrossover.GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.ensemble.GPRuleEvolutionStateBaseline;
import mengxu.algorithm.ensemble.GPRuleEvolutionStateCluster;
import mengxu.algorithm.ensemble.M3GP.EnsembleEvaluatorM3GP;
import mengxu.algorithm.ensemble.M3GP.GPRuleEvolutionStateM3GP;
import mengxu.algorithm.ensemble.eGP.EnsembleEvaluatoreGP;
import mengxu.algorithm.ensemble.eGP.GPRuleEvolutionStateeGP;
import mengxu.algorithm.lexicaseselection.GPRuleEvolutionStateOneInstanceMultiCase;
import mengxu.algorithm.lexicaseselection.LSmultiInstance.GPRuleEvolutionStateMultiInstance;
import mengxu.algorithm.lexicaseselection.LSmultiReplication.GPRuleEvolutionStateMultiReplication;
import mengxu.algorithm.lexicaseselection.ParentFairForCrossover;
import mengxu.algorithm.multicaseEnsemble.ensembleContribution.EnsembleEvaluator;
import mengxu.algorithm.multicaseEnsemble.ensembleContribution.GPRuleEvolutionStateEnsembleContribution;
import mengxu.algorithm.multicasePareto.GPRuleEvolutionStateMCP;
import mengxu.algorithm.semanticTournamentSelection.GPRuleEvolutionStateMCSTS;
import yimei.jss.feature.FeatureUtil;
import yimei.jss.jobshop.Objective;

import java.util.ArrayList;
import java.util.Objects;

/**
 * A SelectionMethod is a BreedingSource which provides direct IMMUTABLE pointers
 * to original individuals in an old population, not fresh mutable copies.
 * If you use a SelectionMethod as your BreedingSource, you must 
 * SelectionMethods might include Tournament Selection, Fitness Proportional Selection, etc.
 * SelectionMethods don't have parent sources.
 *
 <p><b>Typical Number of Individuals Produced Per <tt>produce(...)</tt> call</b><br>
 Always 1.

 * @author Sean Luke
 * @version 1.0 
 */

public abstract class SelectionMethod extends BreedingSource
    {
    public static final int INDS_PRODUCED = 1;

    public static final String P_PRE_GENERATIONS = "pre-generations";
    private int preGenerations;

    //added by mengxu 2023.03.02
//    public EnsembleRule[] parentsForCrossover = new EnsembleRule[2];
//    public EnsembleRule parentsForMutation;

    /** Returns 1 (the typical default value) */
    public int typicalIndsProduced() { return INDS_PRODUCED; }

    /** A default version of produces -- this method always returns
        true under the assumption that the selection method works
        with all Fitnesses.  If this isn't the case, you should override
        this to return your own assessment. */
    public boolean produces(final EvolutionState state,
        final Population newpop,
        final int subpopulation,
        final int thread)
        {
        return true;
        }


    /** A default version of prepareToProduce which does nothing.  */
    public void prepareToProduce(final EvolutionState s,
        final int subpopulation,
        final int thread)
        { return; }

    /** A default version of finishProducing, which does nothing. */
    public void finishProducing(final EvolutionState s,
        final int subpopulation,
        final int thread)
        { return; }


    public int produce(final int min, //produce here means get two individuals for crossover
        final int max, 
        final int start,
        final int subpopulation,
        final Individual[] inds,
        final EvolutionState state,
        final int thread) 
        {
        int n=INDS_PRODUCED;
        if (n<min) n = min;
        if (n>max) n = max;

//        //the original
//        for(int q=0;q<n;q++)
//        {
//            inds[start+q] = state.population.subpops[subpopulation].
//                    individuals[produce(subpopulation,state,thread)];
//        }

            //n==2 represents the crossover operator
        Individual[] allInds = state.population.subpops[subpopulation].individuals;
        if(this instanceof ClusterSelection && n==2){ //modified by mengxu 2021.05.07
            int[] twoIndexfromDifferentCluster = produceTwo(subpopulation,state,thread);
            inds[start+0] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[0]];
            inds[start+1] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[1]];
            int index1 = twoIndexfromDifferentCluster[0];
            int index2 = twoIndexfromDifferentCluster[1];
            ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(index1, allInds[index1].fitness.fitness(), index2, allInds[index2].fitness.fitness(), -1, -1,-1,-1);
            double pearsonScore = ((ClusterSelection) this).calculatePearsonScoreOnEachDecisionForParents(twoIndexfromDifferentCluster[0],twoIndexfromDifferentCluster[1]);
            parentFairForCrossover.setParentsPearsonScore(pearsonScore);
            ((GPRuleEvolutionStateCluster) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
            ((GPRuleEvolutionStateCluster)state).parentIndex.add(twoIndexfromDifferentCluster[0]);
            ((GPRuleEvolutionStateCluster)state).parentIndex.add(twoIndexfromDifferentCluster[1]);
        }
        else if(this instanceof SameClusterSelection && n==2){ //modified by mengxu 2021.05.07
            int[] twoIndexfromDifferentCluster = produceTwo(subpopulation,state,thread);
            inds[start+0] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[0]];
            inds[start+1] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[1]];
            int index1 = twoIndexfromDifferentCluster[0];
            int index2 = twoIndexfromDifferentCluster[1];
            ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(index1, allInds[index1].fitness.fitness(), index2, allInds[index2].fitness.fitness(), -1, -1,-1,-1);
            double pearsonScore = ((SameClusterSelection) this).calculatePearsonScoreOnEachDecisionForParents(twoIndexfromDifferentCluster[0],twoIndexfromDifferentCluster[1]);
            parentFairForCrossover.setParentsPearsonScore(pearsonScore);
            ((GPRuleEvolutionStateCluster) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
            ((GPRuleEvolutionStateCluster)state).parentIndex.add(twoIndexfromDifferentCluster[0]);
            ((GPRuleEvolutionStateCluster)state).parentIndex.add(twoIndexfromDifferentCluster[1]);
        }
        else if(this instanceof ClusterSelectionMultiCase && n==2){ //modified by mengxu 2021.05.07
            int[] twoIndexfromDifferentCluster = produceTwo(subpopulation,state,thread);
            inds[start+0] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[0]];
            inds[start+1] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[1]];
            int index1 = twoIndexfromDifferentCluster[0];
            int index2 = twoIndexfromDifferentCluster[1];
            ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(index1, allInds[index1].fitness.fitness(), index2, allInds[index2].fitness.fitness(), -1, -1,-1,-1);
//            double pearsonScore = ((ClusterSelectionMultiCase) this).calculatePearsonScoreOnEachDecisionForParents(twoIndexfromDifferentCluster[0],twoIndexfromDifferentCluster[1]);
//            parentFairForCrossover.setParentsPearsonScore(pearsonScore);
            ((GPRuleEvolutionStateMultiCaseCluster) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
            ((GPRuleEvolutionStateMultiCaseCluster)state).parentIndex.add(twoIndexfromDifferentCluster[0]);
            ((GPRuleEvolutionStateMultiCaseCluster)state).parentIndex.add(twoIndexfromDifferentCluster[1]);
        }
        else if(this instanceof DiversePartnerSelectionNS && n==2){ //modified by mengxu 2021.12.23
            int[] twoIndexfromDifferentCluster = produceTwo(subpopulation,state,thread);
            inds[start+0] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[0]];
            inds[start+1] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[1]];
            int index1 = twoIndexfromDifferentCluster[0];
            int index2 = twoIndexfromDifferentCluster[1];
            ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(index1, allInds[index1].fitness.fitness(), index2, allInds[index2].fitness.fitness(), -1, -1,-1,-1);
            if(state instanceof GPRuleEvolutionStateDPSNSBR){
                ((GPRuleEvolutionStateDPSNSBR) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateDPSNSBR)state).parentIndex.add(twoIndexfromDifferentCluster[0]);
                ((GPRuleEvolutionStateDPSNSBR)state).parentIndex.add(twoIndexfromDifferentCluster[1]);
            }
            else if(state instanceof GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy){
                ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).parentIndex.add(twoIndexfromDifferentCluster[0]);
                ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).parentIndex.add(twoIndexfromDifferentCluster[1]);
            }
            else if(state instanceof GPRuleEvolutionStateDPSNSBRKNNCase){
                ((GPRuleEvolutionStateDPSNSBRKNNCase) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateDPSNSBRKNNCase)state).parentIndex.add(twoIndexfromDifferentCluster[0]);
                ((GPRuleEvolutionStateDPSNSBRKNNCase)state).parentIndex.add(twoIndexfromDifferentCluster[1]);
            }
            else if(state instanceof GPRuleEvolutionStateDPSNS){
                ((GPRuleEvolutionStateDPSNS) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateDPSNS)state).parentIndex.add(twoIndexfromDifferentCluster[0]);
                ((GPRuleEvolutionStateDPSNS)state).parentIndex.add(twoIndexfromDifferentCluster[1]);
            }
        }
        else if(this instanceof DiversePartnerSelectionPC && n==2){ //modified by mengxu 2021.12.23
            int[] twoIndexfromDifferentCluster = produceTwo(subpopulation,state,thread);
            inds[start+0] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[0]];
            inds[start+1] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[1]];
            int index1 = twoIndexfromDifferentCluster[0];
            int index2 = twoIndexfromDifferentCluster[1];
            ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(index1, allInds[index1].fitness.fitness(), index2, allInds[index2].fitness.fitness(), -1, -1,-1,-1);
            if(state instanceof GPRuleEvolutionStateDPSPC){
                ((GPRuleEvolutionStateDPSPC) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateDPSPC)state).parentIndex.add(twoIndexfromDifferentCluster[0]);
                ((GPRuleEvolutionStateDPSPC)state).parentIndex.add(twoIndexfromDifferentCluster[1]);
            }
        }
        else if(this instanceof DiversePartnerSelection && n==2){ //modified by mengxu 2021.12.23
            int[] twoIndexfromDifferentCluster = produceTwo(subpopulation,state,thread);
            inds[start+0] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[0]];
            inds[start+1] = state.population.subpops[subpopulation].
                    individuals[twoIndexfromDifferentCluster[1]];
            int index1 = twoIndexfromDifferentCluster[0];
            int index2 = twoIndexfromDifferentCluster[1];
            ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(index1, allInds[index1].fitness.fitness(), index2, allInds[index2].fitness.fitness(), -1, -1,-1,-1);
            if(state instanceof GPRuleEvolutionStateDPS){
                ((GPRuleEvolutionStateDPS) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateDPS)state).parentIndex.add(twoIndexfromDifferentCluster[0]);
                ((GPRuleEvolutionStateDPS)state).parentIndex.add(twoIndexfromDifferentCluster[1]);
            }
        }
        else if(n==2){
            int[] indexes = new int[2];
            for(int q=0;q<n;q++)
            {
                //for ensemble by mengxu 2023.03.02
                if(state instanceof GPRuleEvolutionStateEnsembleContribution){
                    indexes[q] = produce(subpopulation,state,thread);
                    if(indexes[q] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluator)state.evaluator).ensemblePool;
                        ((EnsembleEvaluator)state.evaluator).parentsForCrossover[start+q] = ensembles.get(indexes[q]-state.population.subpops[0].individuals.length);
                    }
                    else{
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[indexes[q]];
                        ((EnsembleEvaluator)state.evaluator).parentsForCrossover[start+q] = null;
                    }
                }
                else if(state instanceof GPRuleEvolutionStateCVTMAPElites){
                    indexes[q] = produce(subpopulation,state,thread);
                    if(indexes[q] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ((GPRuleEvolutionStateCVTMAPElites)state).parentsForCrossover[start+q] = ((GPRuleEvolutionStateCVTMAPElites)state).CVTmap.getAllIndividualsInCVTMap(state).get(indexes[q]-state.population.subpops[0].individuals.length);
//                        ((GPRuleEvolutionStateCVTMAPElites)state).parentsForCrossover[start+q] = ((GPRuleEvolutionStateCVTMAPElites)state).allIndividualsInCVTMap.get(indexes[q]-state.population.subpops[0].individuals.length);
                    }
                    else{
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[indexes[q]];
                        ((GPRuleEvolutionStateCVTMAPElites)state).parentsForCrossover[start+q] = null;
                    }
                }
                else if(state instanceof GPRuleEvolutionStateGridMAPElites){
                    indexes[q] = produce(subpopulation,state,thread);
                    if(((GPRuleEvolutionStateGridMAPElites) state).parentsOnlyFromGridMap && state.generation>0){//select an ensemble as parent
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ((GPRuleEvolutionStateGridMAPElites)state).parentsForCrossover[start+q] = ((GPRuleEvolutionStateGridMAPElites)state).gridPCmap.getAllIndividualsInGridMap().get(indexes[q]);
                    }
                    else{
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[indexes[q]];
                        ((GPRuleEvolutionStateGridMAPElites)state).parentsForCrossover[start+q] = null;
                    }
//                    if(indexes[q] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
//                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
//                        ((GPRuleEvolutionStateGridMAPElites)state).parentsForCrossover[start+q] = ((GPRuleEvolutionStateGridMAPElites)state).gridPCmap.getAllIndividualsInGridMap().get(indexes[q]-state.population.subpops[0].individuals.length);
////                        ((GPRuleEvolutionStateCVTMAPElites)state).parentsForCrossover[start+q] = ((GPRuleEvolutionStateCVTMAPElites)state).allIndividualsInCVTMap.get(indexes[q]-state.population.subpops[0].individuals.length);
//                    }
//                    else{
//                        inds[start+q] = state.population.subpops[subpopulation].
//                                individuals[indexes[q]];
//                        ((GPRuleEvolutionStateGridMAPElites)state).parentsForCrossover[start+q] = null;
//                    }
                }
                else if(state instanceof GPRuleEvolutionStateCVTMultiCaseMAPElites){
                    indexes[q] = produce(subpopulation,state,thread);
                    if(indexes[q] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).parentsForCrossover[start+q] = ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).CVTMultiCasemap.getAllIndividualsInCVTMap(state).get(indexes[q]-state.population.subpops[0].individuals.length);
//                        ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).parentsForCrossover[start+q] = ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).allIndividualsInCVTMap.get(indexes[q]-state.population.subpops[0].individuals.length);
                    }
                    else{
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[indexes[q]];
                        ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).parentsForCrossover[start+q] = null;
                    }
                }
                else if(state instanceof GPRuleEvolutionStateeGP){
                    indexes[q] = produce(subpopulation,state,thread);
                    if(((GPRuleEvolutionStateeGP)state).breedIndividualPopulation){//select an ensemble as parent
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[indexes[q]];
                        ((EnsembleEvaluatoreGP)state.evaluator).parentsForCrossover[start+q] = null;
                    }
                    else{
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluatoreGP)state.evaluator).ensemblePool;
                        ((EnsembleEvaluatoreGP)state.evaluator).parentsForCrossover[start+q] = ensembles.get(indexes[q]);
                    }
                }
                else if(state instanceof GPRuleEvolutionStateM3GP){ //add by mengxu 2023.07.30
                    indexes[q] = produce(subpopulation,state,thread);
                    inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                    ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluatorM3GP)state.evaluator).ensemblePool;
                    ((EnsembleEvaluatorM3GP)state.evaluator).parentsForCrossover[start+q] = ensembles.get(indexes[q]);
                }
                else{
                    indexes[q] = produce(subpopulation,state,thread);
                    inds[start+q] = state.population.subpops[subpopulation].
                            individuals[indexes[q]];
                }

                //following is the original method
//                indexes[q] = produce(subpopulation,state,thread);
//                inds[start+q] = state.population.subpops[subpopulation].
//                        individuals[indexes[q]];
            }
            if(state instanceof GPRuleEvolutionStateBaseline){
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], allInds[indexes[0]].fitness.fitness(), indexes[1], allInds[indexes[1]].fitness.fitness(), -1, -1,-1,-1);
                ((GPRuleEvolutionStateBaseline) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateBaseline)state).parentIndex.add(indexes[0]);
                ((GPRuleEvolutionStateBaseline)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateBase){
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], allInds[indexes[0]].fitness.fitness(), indexes[1], allInds[indexes[1]].fitness.fitness(), -1, -1,-1,-1);
                ((GPRuleEvolutionStateBase) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateBase)state).parentIndex.add(indexes[0]);
                ((GPRuleEvolutionStateBase)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateEnsembleContribution){
                ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluator)state.evaluator).ensemblePool;
                double parent0fitness = -1;
                double parent1fitness = -1;
                if(indexes[0] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                    parent0fitness = ensembles.get(indexes[0]-state.population.subpops[0].individuals.length).getFitness().fitness();
                }
                else{
                    parent0fitness = allInds[indexes[0]].fitness.fitness();
                }
                if(indexes[1] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                    parent1fitness = ensembles.get(indexes[1]-state.population.subpops[0].individuals.length).getFitness().fitness();
                }
                else{
                    parent1fitness = allInds[indexes[1]].fitness.fitness();
                }
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], parent0fitness, indexes[1], parent1fitness, -1, -1,-1,-1);
                ((GPRuleEvolutionStateEnsembleContribution) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
//                ((GPRuleEvolutionStateEnsembleContribution)state).parentIndex.add(indexes[0]);
//                ((GPRuleEvolutionStateEnsembleContribution)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateCVTMAPElites){
                ArrayList<Individual> allIndividualsInCVTMap = new ArrayList<>();
                if(((GPRuleEvolutionStateCVTMAPElites) state).use_CVT_map){
                    allIndividualsInCVTMap = ((GPRuleEvolutionStateCVTMAPElites)state).CVTmap.getAllIndividualsInCVTMap((GPRuleEvolutionStateCVTMAPElites)state);
                }
//                ArrayList<Individual> allIndividualsInCVTMap = ((GPRuleEvolutionStateCVTMAPElites)state).CVTmap.getAllIndividualsInCVTMap((GPRuleEvolutionStateCVTMAPElites)state);
                double parent0fitness = -1;
                double parent1fitness = -1;
                if(indexes[0] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                    parent0fitness = allIndividualsInCVTMap.get(indexes[0]-state.population.subpops[0].individuals.length).fitness.fitness();
                }
                else{
                    parent0fitness = allInds[indexes[0]].fitness.fitness();
                }
                if(indexes[1] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                    parent1fitness = allIndividualsInCVTMap.get(indexes[1]-state.population.subpops[0].individuals.length).fitness.fitness();
                }
                else{
                    parent1fitness = allInds[indexes[1]].fitness.fitness();
                }
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], parent0fitness, indexes[1], parent1fitness, -1, -1,-1,-1);
                //todo: might need to store this information if want to do analyses about this
//                ((GPRuleEvolutionStateCVTMAPElites) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
//                ((GPRuleEvolutionStateEnsembleContribution)state).parentIndex.add(indexes[0]);
//                ((GPRuleEvolutionStateEnsembleContribution)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateCVTMultiCaseMAPElites){
                ArrayList<Individual> allIndividualsInCVTMap = new ArrayList<>();
                if(((GPRuleEvolutionStateCVTMultiCaseMAPElites) state).use_CVT_map){
                    allIndividualsInCVTMap = ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).CVTMultiCasemap.getAllIndividualsInCVTMap((GPRuleEvolutionStateCVTMultiCaseMAPElites)state);
                }
//                ArrayList<Individual> allIndividualsInCVTMap = ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).CVTmap.getAllIndividualsInCVTMap((GPRuleEvolutionStateCVTMultiCaseMAPElites)state);
                double parent0fitness = -1;
                double parent1fitness = -1;
                if(indexes[0] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                    parent0fitness = allIndividualsInCVTMap.get(indexes[0]-state.population.subpops[0].individuals.length).fitness.fitness();
                }
                else{
                    parent0fitness = allInds[indexes[0]].fitness.fitness();
                }
                if(indexes[1] >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                    parent1fitness = allIndividualsInCVTMap.get(indexes[1]-state.population.subpops[0].individuals.length).fitness.fitness();
                }
                else{
                    parent1fitness = allInds[indexes[1]].fitness.fitness();
                }
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], parent0fitness, indexes[1], parent1fitness, -1, -1,-1,-1);
                //todo: might need to store this information if want to do analyses about this
//                ((GPRuleEvolutionStateCVTMultiCaseMAPElites) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
//                ((GPRuleEvolutionStateEnsembleContribution)state).parentIndex.add(indexes[0]);
//                ((GPRuleEvolutionStateEnsembleContribution)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateeGP){
                if(((GPRuleEvolutionStateeGP)state).breedIndividualPopulation){
                    ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluatoreGP)state.evaluator).ensemblePool;
                    double parent0fitness = allInds[indexes[0]].fitness.fitness();
                    double parent1fitness = allInds[indexes[1]].fitness.fitness();
                    ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], parent0fitness, indexes[1], parent1fitness, -1, -1,-1,-1);
                    ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                }
                else{
                    ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluatoreGP)state.evaluator).ensemblePool;
                    double parent0fitness = -1;
                    double parent1fitness = -1;
                    parent0fitness = ensembles.get(indexes[0]).getFitness().fitness();
                    parent1fitness = ensembles.get(indexes[1]).getFitness().fitness();

                    ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], parent0fitness, indexes[1], parent1fitness, -1, -1,-1,-1);
                    ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                }
            }
            if(state instanceof GPRuleEvolutionStateM3GP){
                ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluatorM3GP)state.evaluator).ensemblePool;
                double parent0fitness = -1;
                double parent1fitness = -1;
                parent0fitness = ensembles.get(indexes[0]).getFitness().fitness();
                parent1fitness = ensembles.get(indexes[1]).getFitness().fitness();

                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], parent0fitness, indexes[1], parent1fitness, -1, -1,-1,-1);
                ((GPRuleEvolutionStateM3GP) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
            }
            if(state instanceof GPRuleEvolutionStateMCP){
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], allInds[indexes[0]].fitness.fitness(), indexes[1], allInds[indexes[1]].fitness.fitness(), -1, -1,-1,-1);
                ((GPRuleEvolutionStateMCP) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateMCP)state).parentIndex.add(indexes[0]);
                ((GPRuleEvolutionStateMCP)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateOneInstanceMultiCase){//this is for lexicase selection modified by mengxu 2022.02.28
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], allInds[indexes[0]].fitness.fitness(), indexes[1], allInds[indexes[1]].fitness.fitness(), -1, -1,-1,-1);
                ((GPRuleEvolutionStateOneInstanceMultiCase) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateOneInstanceMultiCase)state).parentIndex.add(indexes[0]);
                ((GPRuleEvolutionStateOneInstanceMultiCase)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateMultiInstance){//this is for lexicase selection modified by mengxu 2022.02.28
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], allInds[indexes[0]].fitness.fitness(), indexes[1], allInds[indexes[1]].fitness.fitness(), -1, -1,-1,-1);
                ((GPRuleEvolutionStateMultiInstance) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateMultiInstance)state).parentIndex.add(indexes[0]);
                ((GPRuleEvolutionStateMultiInstance)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateMultiReplication){//this is for lexicase selection modified by mengxu 2022.02.28
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], allInds[indexes[0]].fitness.fitness(), indexes[1], allInds[indexes[1]].fitness.fitness(), -1, -1,-1,-1);
                ((GPRuleEvolutionStateMultiReplication) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateMultiReplication)state).parentIndex.add(indexes[0]);
                ((GPRuleEvolutionStateMultiReplication)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateMCSTS){//this is for lexicase selection modified by mengxu 2022.02.28
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], allInds[indexes[0]].fitness.fitness(), indexes[1], allInds[indexes[1]].fitness.fitness(), -1, -1,-1,-1);
                ((GPRuleEvolutionStateMCSTS) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateMCSTS)state).parentIndex.add(indexes[0]);
                ((GPRuleEvolutionStateMCSTS)state).parentIndex.add(indexes[1]);
            }
            if(state instanceof GPRuleEvolutionStateAverage){//this is for lexicase selection modified by mengxu 2022.02.28
                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], allInds[indexes[0]].fitness.fitness(), indexes[1], allInds[indexes[1]].fitness.fitness(), -1, -1,-1,-1);
                ((GPRuleEvolutionStateAverage) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
                ((GPRuleEvolutionStateAverage)state).parentIndex.add(indexes[0]);
                ((GPRuleEvolutionStateAverage)state).parentIndex.add(indexes[1]);
                Objective objective = ((AverageMultiCaseMultiObjectiveFitness)state.population.subpops[0].individuals[indexes[0]].fitness).currentObjectives.get(0);
                double indexFitness0 = 0;
                double indexFitness1 = 0;
                if(Objects.equals(objective.getName(), "mean-flowtime") || Objects.equals(objective.getName(), "mean-weighted-flowtime") ||
                        Objects.equals(objective.getName(), "mean-tardiness") || Objects.equals(objective.getName(), "mean-weighted-tardiness")) {
                    indexFitness0 = ((AverageMultiCaseMultiObjectiveFitness) state.population.subpops[0].individuals[indexes[0]].fitness).fitnessMean();
                    indexFitness1 = ((AverageMultiCaseMultiObjectiveFitness) state.population.subpops[0].individuals[indexes[1]].fitness).fitnessMean();
                    ((GPRuleEvolutionStateAverage) state).parentFitness.add(indexFitness0);//add 2021.12.09
                    ((GPRuleEvolutionStateAverage) state).parentFitness.add(indexFitness1);//add 2021.12.09
                }
                else if(Objects.equals(objective.getName(), "max-flowtime") || Objects.equals(objective.getName(), "max-weighted-flowtime") ||
                        Objects.equals(objective.getName(), "max-tardiness") || Objects.equals(objective.getName(), "max-weighted-tardiness")) {
                    indexFitness0 = ((AverageMultiCaseMultiObjectiveFitness) state.population.subpops[0].individuals[indexes[0]].fitness).fitnessMax();
                    indexFitness1 = ((AverageMultiCaseMultiObjectiveFitness) state.population.subpops[0].individuals[indexes[1]].fitness).fitnessMax();
                    ((GPRuleEvolutionStateAverage)state).parentFitness.add(indexFitness0);//add 2021.12.09
                    ((GPRuleEvolutionStateAverage)state).parentFitness.add(indexFitness1);//add 2021.12.09
                }

            }
//            if(state instanceof GPRuleEvolutionStateCluster && n==2) {
//                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(indexes[0], allInds[indexes[0]].fitness.fitness(), indexes[1], allInds[indexes[1]].fitness.fitness(), -1, -1,-1,-1);
//                ((GPRuleEvolutionStateCluster) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
//                ((GPRuleEvolutionStateCluster)state).parentIndex.add(indexes[0]);
//                ((GPRuleEvolutionStateCluster)state).parentIndex.add(indexes[1]);
//            }
        }
        else{//n==1 represents reproduction and mutation
            for(int q=0;q<n;q++)
            {

                //modified by mengxu for ensemble contribution 2023.03.02
                int index = produce(subpopulation,state,thread);
                if(state instanceof GPRuleEvolutionStateEnsembleContribution){
                    if(index >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluator)state.evaluator).ensemblePool;
                        ((EnsembleEvaluator)state.evaluator).parentsForMutation = ensembles.get(index-state.population.subpops[0].individuals.length);
                    }
                    else{
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[index];
                        ((EnsembleEvaluator)state.evaluator).parentsForMutation = null;
                    }
                }
                else if(state instanceof GPRuleEvolutionStateCVTMAPElites){
                    if(index >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ArrayList<Individual> allIndividualsInCVTMap = ((GPRuleEvolutionStateCVTMAPElites) state).CVTmap.getAllIndividualsInCVTMap(state);
                        ((GPRuleEvolutionStateCVTMAPElites)state).parentsForMutation = allIndividualsInCVTMap.get(index-state.population.subpops[0].individuals.length);
//                        ArrayList<Individual> allIndividualsInCVTMap = ((GPRuleEvolutionStateCVTMAPElites) state).allIndividualsInCVTMap;
//                        ((GPRuleEvolutionStateCVTMAPElites)state).parentsForMutation = allIndividualsInCVTMap.get(index-state.population.subpops[0].individuals.length);
                    }
                    else{
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[index];
                        ((GPRuleEvolutionStateCVTMAPElites)state).parentsForMutation = null;
                    }
                }
                else if(state instanceof GPRuleEvolutionStateGridMAPElites){
                    if(((GPRuleEvolutionStateGridMAPElites) state).parentsOnlyFromGridMap && state.generation>0){//select an ensemble as parent
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ArrayList<Individual> allIndividualsInGridMap = ((GPRuleEvolutionStateGridMAPElites) state).gridPCmap.getAllIndividualsInGridMap();
                        ((GPRuleEvolutionStateGridMAPElites)state).parentsForMutation = allIndividualsInGridMap.get(index);
                    }
                    else{
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[index];
                        ((GPRuleEvolutionStateGridMAPElites)state).parentsForMutation = null;
                    }

//                    if(index >= state.population.subpops[0].individuals.length){//select an ensemble as parent
//                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
//                        ArrayList<Individual> allIndividualsInGridMap = ((GPRuleEvolutionStateGridMAPElites) state).gridPCmap.getAllIndividualsInGridMap();
//                        ((GPRuleEvolutionStateGridMAPElites)state).parentsForMutation = allIndividualsInGridMap.get(index-state.population.subpops[0].individuals.length);
////                        ArrayList<Individual> allIndividualsInCVTMap = ((GPRuleEvolutionStateCVTMAPElites) state).allIndividualsInCVTMap;
////                        ((GPRuleEvolutionStateCVTMAPElites)state).parentsForMutation = allIndividualsInCVTMap.get(index-state.population.subpops[0].individuals.length);
//                    }
//                    else{
//                        inds[start+q] = state.population.subpops[subpopulation].
//                                individuals[index];
//                        ((GPRuleEvolutionStateGridMAPElites)state).parentsForMutation = null;
//                    }
                }
                else if(state instanceof GPRuleEvolutionStateCVTMultiCaseMAPElites){
                    if(index >= state.population.subpops[0].individuals.length){//select an ensemble as parent
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ArrayList<Individual> allIndividualsInCVTMap = ((GPRuleEvolutionStateCVTMultiCaseMAPElites) state).CVTMultiCasemap.getAllIndividualsInCVTMap(state);
                        ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).parentsForMutation = allIndividualsInCVTMap.get(index-state.population.subpops[0].individuals.length);
//                        ArrayList<Individual> allIndividualsInCVTMap = ((GPRuleEvolutionStateCVTMultiCaseMAPElites) state).allIndividualsInCVTMap;
//                        ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).parentsForMutation = allIndividualsInCVTMap.get(index-state.population.subpops[0].individuals.length);
                    }
                    else{
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[index];
                        ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).parentsForMutation = null;
                    }
                }
                else if(state instanceof GPRuleEvolutionStateeGP){
                    if(((GPRuleEvolutionStateeGP)state).breedIndividualPopulation){//select an ensemble as parent
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[index];
                        ((EnsembleEvaluatoreGP)state.evaluator).parentsForMutation = null;
                    }
                    else{
                        inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                        ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluatoreGP)state.evaluator).ensemblePool;
                        ((EnsembleEvaluatoreGP)state.evaluator).parentsForMutation = ensembles.get(index);
                    }
                }
                else if(state instanceof GPRuleEvolutionStateM3GP){
                    inds[start+q] = null; //there is another place to store the ensemble used for crossover/mutation
                    ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluatorM3GP)state.evaluator).ensemblePool;
                    ((EnsembleEvaluatorM3GP)state.evaluator).parentsForMutation = ensembles.get(index);
                }
                else{
                    inds[start+q] = state.population.subpops[subpopulation].
                        individuals[index];
                }

                //following is the original
//                int index = produce(subpopulation,state,thread);
//                inds[start+q] = state.population.subpops[subpopulation].
//                        individuals[index];


                if(state instanceof GPRuleEvolutionStateBaseline){
                   ((GPRuleEvolutionStateBaseline)state).parentIndex.add(index);
                }
                if(state instanceof GPRuleEvolutionStateAverage){
                    ((GPRuleEvolutionStateAverage)state).parentIndex.add(index);
//                    double indexFitness = state.population.subpops[0].individuals[index].fitness.fitness();
//                    ((GPRuleEvolutionStateAverage)state).parentFitness.add(indexFitness);//add 2021.12.09
                    Objective objective = ((AverageMultiCaseMultiObjectiveFitness)state.population.subpops[0].individuals[index].fitness).currentObjectives.get(0);
                    if(Objects.equals(objective.getName(), "mean-flowtime") || Objects.equals(objective.getName(), "mean-weighted-flowtime") ||
                            Objects.equals(objective.getName(), "mean-tardiness") || Objects.equals(objective.getName(), "mean-weighted-tardiness")) {
                        double indexFitness = ((AverageMultiCaseMultiObjectiveFitness) state.population.subpops[0].individuals[index].fitness).fitnessMean();
                        ((GPRuleEvolutionStateAverage) state).parentFitness.add(indexFitness);//add 2021.12.09
                    }
                    else if(Objects.equals(objective.getName(), "max-flowtime") || Objects.equals(objective.getName(), "max-weighted-flowtime") ||
                            Objects.equals(objective.getName(), "max-tardiness") || Objects.equals(objective.getName(), "max-weighted-tardiness")) {
                        double indexFitness = ((AverageMultiCaseMultiObjectiveFitness) state.population.subpops[0].individuals[index].fitness).fitnessMax();
                        ((GPRuleEvolutionStateAverage)state).parentFitness.add(indexFitness);//add 2021.12.09
                    }
                }
                if(state instanceof GPRuleEvolutionStateMCP){
                    ((GPRuleEvolutionStateMCP)state).parentIndex.add(index);
                }
                else if(state instanceof GPRuleEvolutionStateBase){
                    ((GPRuleEvolutionStateBase)state).parentIndex.add(index);
                }
                else if(state instanceof GPRuleEvolutionStateDPS){
                    ((GPRuleEvolutionStateDPS)state).parentIndex.add(index);
                }
                else if(state instanceof GPRuleEvolutionStateDPSNS){
                    ((GPRuleEvolutionStateDPSNS)state).parentIndex.add(index);
                }
                else if(state instanceof GPRuleEvolutionStateDPSNSBR){
                    ((GPRuleEvolutionStateDPSNSBR)state).parentIndex.add(index);
                }
                else if(state instanceof GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy){
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).parentIndex.add(index);
                }
                else if(state instanceof GPRuleEvolutionStateDPSNSBRKNNCase){
                    ((GPRuleEvolutionStateDPSNSBRKNNCase)state).parentIndex.add(index);
                }
                else if(state instanceof GPRuleEvolutionStateDPSPC){
                    ((GPRuleEvolutionStateDPSPC)state).parentIndex.add(index);
                }
                else if(state instanceof GPRuleEvolutionStateCluster){
                    ((GPRuleEvolutionStateCluster)state).parentIndex.add(index);
                }else if(state instanceof GPRuleEvolutionStateOneInstanceMultiCase){//this is for lexicase selection modified by mengxu 2022.02.28
                    //can not recognise this is for reproduction or for mutation, so add directly in mutationPipline.
//                    ParentForMutation parentForMutation = new ParentForMutation(index, allInds[index].fitness.fitness(), -1, -1);
//                    ((GPRuleEvolutionStateMV0) state).parentIndexForMutation.add(parentForMutation);
                    ((GPRuleEvolutionStateOneInstanceMultiCase)state).parentIndex.add(index);
                }else if(state instanceof GPRuleEvolutionStateMultiInstance){//this is for lexicase selection modified by mengxu 2022.02.28
                    //can not recognise this is for reproduction or for mutation, so add directly in mutationPipline.
//                    ParentForMutation parentForMutation = new ParentForMutation(index, allInds[index].fitness.fitness(), -1, -1);
//                    ((GPRuleEvolutionStateMV0) state).parentIndexForMutation.add(parentForMutation);
                    ((GPRuleEvolutionStateMultiInstance)state).parentIndex.add(index);
                }else if(state instanceof GPRuleEvolutionStateMultiReplication){//this is for lexicase selection modified by mengxu 2022.02.28
                    //can not recognise this is for reproduction or for mutation, so add directly in mutationPipline.
//                    ParentForMutation parentForMutation = new ParentForMutation(index, allInds[index].fitness.fitness(), -1, -1);
//                    ((GPRuleEvolutionStateMV0) state).parentIndexForMutation.add(parentForMutation);
                    ((GPRuleEvolutionStateMultiReplication)state).parentIndex.add(index);
                }else if(state instanceof GPRuleEvolutionStateMCSTS){//this is for lexicase selection modified by mengxu 2022.02.28
                    //can not recognise this is for reproduction or for mutation, so add directly in mutationPipline.
    //                    ParentForMutation parentForMutation = new ParentForMutation(index, allInds[index].fitness.fitness(), -1, -1);
    //                    ((GPRuleEvolutionStateMV0) state).parentIndexForMutation.add(parentForMutation);
                    ((GPRuleEvolutionStateMCSTS)state).parentIndex.add(index);
                }
            }
        }




            return n;
        }


        //fzhang 2019.6.15 in the defined generation, when do crossover and mutation, select individuals from specific individuals
        public int produceFrac(final int min, //produce here means get two individuals for crossover
                           final int max,
                           final int start,
                           final int subpopulation,
                           final Individual[] inds,
                           final EvolutionState state,
                           final int thread)
        {
            int n=INDS_PRODUCED;
            if (n<min) n = min;
            if (n>max) n = max;

            preGenerations = state.parameters.getIntWithDefault(new Parameter(P_PRE_GENERATIONS), null, -1);  //50

            for(int q=0;q<n;q++){
                if(state.generation == preGenerations)
                    inds[start+q] = FeatureUtil.getNewpop().subpops[subpopulation].
                            individuals[produce(subpopulation,state,thread)];
                else
                    inds[start+q] = state.population.subpops[subpopulation].
                            individuals[produce(subpopulation,state,thread)];
            }
            return n;
        }

    /** An alternative form of "produce" special to Selection Methods;
        selects an individual from the given subpopulation and 
        returns its position in that subpopulation. */
    public abstract int produce(final int subpopulation,
        final EvolutionState state,
        final int thread);

    //modified by mengxu 2021.05.07
    public int[] produceTwo(final int subpopulation,
                                final EvolutionState state,
                                final int thread){
        return null;
    }

    }



