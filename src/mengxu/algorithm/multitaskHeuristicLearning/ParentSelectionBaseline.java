/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package mengxu.algorithm.multitaskHeuristicLearning;

import ec.EvolutionState;
import ec.Individual;
import ec.select.TournamentSelection;
import ec.util.Parameter;
import mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodel.GPRuleEvolutionStateMultiTaskLearningSurrogateSVR;

import java.util.ArrayList;


/*
 * @author Meng Xu 2024.8.14
 * @version 1.0 
 */

public class ParentSelectionBaseline extends TournamentSelection
    {
        public static final String P_CROSSOVER_FROM_OTHER_SUBPOP = "crossover-from-other-subpop";
        public double crossoverFromOtherSubpop;

        public void setup(final EvolutionState state, final Parameter base)
        {
            super.setup(state,base);

            Parameter crossoverFromOtherSubpopParam = new Parameter(P_CROSSOVER_FROM_OTHER_SUBPOP);
            this.crossoverFromOtherSubpop = state.parameters.getDouble(crossoverFromOtherSubpopParam,null,0);
        }

        @Override
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

            if(state.generation>((GPRuleEvolutionStateMultiTaskLearningBaseline)state).switchGeneration){
                if(state.random[0].nextDouble()<this.crossoverFromOtherSubpop) {
                    ArrayList<Integer> candidateSubpop = new ArrayList<>();
                    for (int i = 0; i < state.population.subpops.length; i++) {
                        candidateSubpop.add(i);
                    }
                    if (n == 2) {
                        int[] indexes = new int[2];
                        for (int q = 0; q < n; q++) {
                            int index = state.random[0].nextInt(candidateSubpop.size());
                            int subpop = candidateSubpop.get(index);
                            indexes[q] = produce(subpop, state, thread);
                            inds[start + q] = state.population.subpops[subpop].
                                    individuals[indexes[q]];
                        }
                    } else {//n==1 represents reproduction and mutation
                        for (int q = 0; q < n; q++) {
                            //modified by mengxu for ensemble contribution 2023.03.02
                            int index = produce(subpopulation, state, thread);
                            inds[start + q] = state.population.subpops[subpopulation].
                                    individuals[index];
                        }
                    } //n==2 represents the crossover operator
                }
                else{
                    if(n==2){
                        int[] indexes = new int[2];
                        for(int q=0;q<n;q++)
                        {
                            indexes[q] = produce(subpopulation,state,thread);
                            inds[start+q] = state.population.subpops[subpopulation].
                                    individuals[indexes[q]];
                        }
                    }
                    else{//n==1 represents reproduction and mutation
                        for(int q=0;q<n;q++)
                        {
                            //modified by mengxu for ensemble contribution 2023.03.02
                            int index = produce(subpopulation,state,thread);
                            inds[start+q] = state.population.subpops[subpopulation].
                                    individuals[index];
                        }
                    }
                }
            }
            else{
                if(n==2){
                    int[] indexes = new int[2];
                    for(int q=0;q<n;q++)
                    {
                        indexes[q] = produce(subpopulation,state,thread);
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[indexes[q]];
                    }
                }
                else{//n==1 represents reproduction and mutation
                    for(int q=0;q<n;q++)
                    {
                        //modified by mengxu for ensemble contribution 2023.03.02
                        int index = produce(subpopulation,state,thread);
                        inds[start+q] = state.population.subpops[subpopulation].
                                individuals[index];
                    }
                }
            }
            return n;
        }

        public int produceBasedOnRelationshipModel(final int subpopulation,
                                                   final EvolutionState state,
                                                   final int thread,
                                                   final Individual parent1,
                                                   final int parent1_subpopulation)
        {
            // pick size random individuals, then pick the best.
            Individual[] oldinds = state.population.subpops[subpopulation].individuals;
            int best = getRandomIndividual(0, subpopulation, state, thread);

            int s = getTournamentSizeToUse(state.random[thread]);

            if (pickWorst)
                for (int x=1;x<s;x++)
                {
                    int j = getRandomIndividual(x, subpopulation, state, thread);
                    if (!betterThanBasedOnRelationshipModel(oldinds[j], oldinds[best], parent1, parent1_subpopulation, subpopulation, state, thread))  // j is at least as bad as best
                        best = j;
                }
            else
                for (int x=1;x<s;x++)
                {
                    int j = getRandomIndividual(x, subpopulation, state, thread);
                    if (betterThanBasedOnRelationshipModel(oldinds[j], oldinds[best], parent1, parent1_subpopulation, subpopulation, state, thread))  // j is better than best
                        best = j;
                }


            return best;
        }

        public int[] produceBasedOnRelationshipModel2(final ArrayList<Integer> candidateSubpop,
                                                    final EvolutionState state,
                                                    final int thread)
        {
            // pick size random individuals, then pick the best.
            int subpopulation_1 = candidateSubpop.get(0);
            Individual[] oldinds_1 = state.population.subpops[subpopulation_1].individuals;
            int best_1 = getRandomIndividual(0, subpopulation_1, state, thread);
            int subpopulation_2 = candidateSubpop.get(1);
            Individual[] oldinds_2 = state.population.subpops[subpopulation_2].individuals;
            int best_2 = getRandomIndividual(0, subpopulation_2, state, thread);

            int s = getTournamentSizeToUse(state.random[thread]);

            if (pickWorst)
                for (int x=1;x<s;x++)
                {
                    int j_1 = getRandomIndividual(x, subpopulation_1, state, thread);
                    int j_2 = getRandomIndividual(x, subpopulation_2, state, thread);
                    if (!betterThanBasedOnRelationshipModel2(oldinds_1[j_1], oldinds_1[best_1], oldinds_2[j_2], oldinds_2[best_2], subpopulation_1, subpopulation_2, state, thread))  // j is at least as bad as best
                    {
                        best_1 = j_1;
                        best_2 = j_2;
                    }

                }
            else
                for (int x=1;x<s;x++)
                {
                    int j_1 = getRandomIndividual(x, subpopulation_1, state, thread);
                    int j_2 = getRandomIndividual(x, subpopulation_2, state, thread);
                    if (betterThanBasedOnRelationshipModel2(oldinds_1[j_1], oldinds_1[best_1], oldinds_2[j_2], oldinds_2[best_2], subpopulation_1, subpopulation_2, state, thread))  // j is better than best
                    {
                        best_1 = j_1;
                        best_2 = j_2;
                    }
                }


            return new int[]{best_1, best_2};
        }

        public boolean betterThanBasedOnRelationshipModel(Individual first, Individual second, Individual parent1, int parent1_subpopulation,
                                                          int subpopulation, EvolutionState state, int thread)
        {
            double fit_first = first.fitness.fitness();
            double fit_second = second.fitness.fitness();
            NonlinearModel relationshipModel = ((GPRuleEvolutionStateMultiTaskLearningSurrogate)state).nonlinearModel;
            double fit_parent1 = parent1.fitness.fitness();
            double fit_first_other_estimated = 0;
            double fit_second_other_estimated = 0;

            if(parent1_subpopulation==0){
                if(subpopulation == 1){
                    fit_first_other_estimated = relationshipModel.estimateF3withF1andF2(fit_parent1,fit_first);
                    fit_second_other_estimated = relationshipModel.estimateF3withF1andF2(fit_parent1,fit_second);
                }
                else{
                    fit_first_other_estimated = relationshipModel.estimateF2withF1andF3(fit_parent1,fit_first);
                    fit_second_other_estimated = relationshipModel.estimateF2withF1andF3(fit_parent1,fit_second);
                }
            }
            else if(parent1_subpopulation==1){
                if(subpopulation == 0){
                    fit_first_other_estimated = relationshipModel.estimateF3withF1andF2(fit_first,fit_parent1);
                    fit_second_other_estimated = relationshipModel.estimateF3withF1andF2(fit_second,fit_parent1);
                }
                else{
                    fit_first_other_estimated = relationshipModel.estimateF1withF2andF3(fit_parent1,fit_first);
                    fit_second_other_estimated = relationshipModel.estimateF1withF2andF3(fit_parent1,fit_second);
                }
            }
            else if(parent1_subpopulation==2){
                if(subpopulation == 0){
                    fit_first_other_estimated = relationshipModel.estimateF2withF1andF3(fit_first,fit_parent1);
                    fit_second_other_estimated = relationshipModel.estimateF2withF1andF3(fit_second,fit_parent1);
                }
                else{
                    fit_first_other_estimated = relationshipModel.estimateF1withF2andF3(fit_first,fit_parent1);
                    fit_second_other_estimated = relationshipModel.estimateF1withF2andF3(fit_second,fit_parent1);
                }
            }
            else{
                System.out.println("Error in betterThanBasedOnRelationshipModel!");
            }

            return fit_first+fit_first_other_estimated<fit_second+fit_second_other_estimated;
        }

        public boolean betterThanBasedOnRelationshipModel2(Individual first_1, Individual first_2, Individual second_1, Individual second_2,
                                                           int subpopulation_1, int subpopulation_2, EvolutionState state, int thread)
        {
            double fit_first_1 = first_1.fitness.fitness();
            double fit_second_1 = second_1.fitness.fitness();
            double fit_first_2 = first_2.fitness.fitness();
            double fit_second_2 = second_2.fitness.fitness();
            NonlinearModel relationshipModel = ((GPRuleEvolutionStateMultiTaskLearningSurrogate)state).nonlinearModel;
            double fit_first_other_estimated = 0;
            double fit_second_other_estimated = 0;

            if(subpopulation_1==0){
                if(subpopulation_2 == 1){
                    fit_first_other_estimated = relationshipModel.estimateF3withF1andF2(fit_first_1,fit_first_2);
                    fit_second_other_estimated = relationshipModel.estimateF3withF1andF2(fit_second_1,fit_second_2);
                }
                else{
                    fit_first_other_estimated = relationshipModel.estimateF2withF1andF3(fit_first_1,fit_first_2);
                    fit_second_other_estimated = relationshipModel.estimateF2withF1andF3(fit_second_1,fit_second_2);
                }
            }
            else if(subpopulation_1==1){
                if(subpopulation_2 == 0){
                    fit_first_other_estimated = relationshipModel.estimateF3withF1andF2(fit_first_2,fit_first_1);
                    fit_second_other_estimated = relationshipModel.estimateF3withF1andF2(fit_second_2,fit_second_1);
                }
                else{
                    fit_first_other_estimated = relationshipModel.estimateF1withF2andF3(fit_first_1,fit_first_2);
                    fit_second_other_estimated = relationshipModel.estimateF1withF2andF3(fit_second_1,fit_second_2);
                }
            }
            else if(subpopulation_1==2){
                if(subpopulation_2 == 0){
                    fit_first_other_estimated = relationshipModel.estimateF2withF1andF3(fit_first_2,fit_first_1);
                    fit_second_other_estimated = relationshipModel.estimateF2withF1andF3(fit_second_2,fit_second_1);
                }
                else{
                    fit_first_other_estimated = relationshipModel.estimateF1withF2andF3(fit_first_2,fit_first_1);
                    fit_second_other_estimated = relationshipModel.estimateF1withF2andF3(fit_second_2,fit_second_1);
                }
            }
            else{
                System.out.println("Error in betterThanBasedOnRelationshipModel!");
            }

            double fit_first = fit_first_1 + fit_first_2 + fit_first_other_estimated;
            double fit_second = fit_second_1 + fit_second_2 + fit_second_other_estimated;

            return fit_first < fit_second;
        }
    
    }
