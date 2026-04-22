/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodelOneSubpop;

import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.select.TournamentSelection;
import ec.util.Parameter;

import java.util.ArrayList;


/*
 * @author Meng Xu 2024.8.14
 * @version 1.0 
 */

public class ParentSelectionSurrogateSVROneSubpop extends TournamentSelection
    {
        public static final String P_CROSSOVER_FROM_OTHER_SUBPOP = "crossover-from-other-subpop";
        public double crossoverFromOtherSubpop;

        public static final String P_PARENT_SELECTION_BASE_RELATION_MODEL = "parent-selection-base-relation-model";
        public boolean parentSelectionBaseRelationModel;

        public static final String P_FITNESS_BASED_ON_RANK = "fitness-based-on-rank";
        public boolean fitnessBasedOnRank;
    
        public void setup(final EvolutionState state, final Parameter base)
        {
            super.setup(state,base);

            Parameter crossoverFromOtherSubpopParam = new Parameter(P_CROSSOVER_FROM_OTHER_SUBPOP);
            this.crossoverFromOtherSubpop = state.parameters.getDouble(crossoverFromOtherSubpopParam,null,0);

            Parameter parentSelectionBaseRelationModelParam = new Parameter(P_PARENT_SELECTION_BASE_RELATION_MODEL);
            this.parentSelectionBaseRelationModel = state.parameters.getBoolean(parentSelectionBaseRelationModelParam,null,false);
//            System.out.println("Use relationship model: " + this.parentSelectionBaseRelationModel);

            Parameter fitnessBasedOnRankParam = new Parameter(P_FITNESS_BASED_ON_RANK);
            this.fitnessBasedOnRank = state.parameters.getBoolean(fitnessBasedOnRankParam,null,false);
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

            if(state.generation>((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).switchGeneration){
                if(this.parentSelectionBaseRelationModel){
                    if(state.random[0].nextDouble()<this.crossoverFromOtherSubpop){
                        //n==2 represents the crossover operator
                        Individual[] allInds = state.population.subpops[subpopulation].individuals;
                        int subpopLength = allInds.length;
                        ArrayList<Integer> candidateSubpop = new ArrayList<>();
                        for (int i = 0; i < state.population.subpops.length; i++) {
                            candidateSubpop.add(i);
                        }
                        if(n==2){
                            int[] indexes = new int[2];
                            for(int q=0;q<n;q++)
                            {
                                //Strategy 2
//                                int index_sub = state.random[0].nextInt(candidateSubpop.size());
//                                int subpop = candidateSubpop.get(index_sub);
//                                int index = produceBasedOnRelationshipModel(subpop,state,thread);
////                                int index = produceBasedOnRelationshipModel_Double_TournamentSelection(subpop,state,thread);
//                                inds[start+q] = state.population.subpops[subpop].
//                                        individuals[index];

                                //Strategy 1
                                indexes[q] = produceBasedOnRelationshipModel(state,thread);
                                int subpop = indexes[q]/subpopLength;
                                int subpopIndex = indexes[q]%subpopLength;
                                inds[start+q] = state.population.subpops[subpop].
                                        individuals[subpopIndex];
                            }

                        }
                        else{//n==1 represents reproduction and mutation
                            for(int q=0;q<n;q++)
                            {
                                int index = produce(subpopulation,state,thread);
                                inds[start+q] = state.population.subpops[subpopulation].
                                        individuals[index];
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
                }
                else{
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

        //Strategy 1
        public int produceBasedOnRelationshipModel(final EvolutionState state,
                                                   final int thread)
        {
            //consider all the subpopulations
            // pick size random individuals, then pick the best.
            Individual[] inds = state.population.subpops[0].individuals;
            int best = state.random[thread].nextInt(inds.length);

            MultiObjectiveFitnessMultiTaskLearningSVROneSubpop fitness = (MultiObjectiveFitnessMultiTaskLearningSVROneSubpop)state.population.subpops[0].individuals[0].fitness;

            //modified by mengxu 2024.11.5
            int s = getTournamentSizeToUse(state.random[thread]);

            if (pickWorst)
                for (int x=1;x<s;x++)
                {
                    int j = state.random[thread].nextInt(inds.length);
                    if (!fitness.betterThanBasedOnRelationshipModel(j, best, state, thread))  // j is at least as bad as best
                        best = j;
                }
            else
                for (int x=1;x<s;x++)
                {
                    int j = state.random[thread].nextInt(inds.length);
                    if (fitness.betterThanBasedOnRelationshipModel(j, best, state, thread))  // j is at least as bad as best
                        best = j;
                }

            return best;
        }

        //Strategy 2
        public int produceBasedOnRelationshipModel_Double_TournamentSelection(final int subpopulation,
                                                   final EvolutionState state,
                                                   final int thread)
        {
            // pick size random individuals, then pick the best.
            Individual[] oldinds = state.population.subpops[subpopulation].individuals;

            MultiObjectiveFitnessMultiTaskLearningSVROneSubpop fitness = (MultiObjectiveFitnessMultiTaskLearningSVROneSubpop)state.population.subpops[0].individuals[0].fitness;

            int s = getTournamentSizeToUse(state.random[thread]);

            int num_candidates = s;
            ArrayList<Integer> candidates = new ArrayList<>();

            //Step 1: first tournament selection
            while(candidates.size()<num_candidates){
                int best = getRandomIndividual(0, subpopulation, state, thread);
                if (pickWorst){
                    for (int x=1;x<s;x++)
                    {
                        int j = getRandomIndividual(x, subpopulation, state, thread);
                        if (!betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is at least as bad as best
                            best = j;
                    }
                }
                else{
                    for (int x=1;x<s;x++)
                    {
                        int j = getRandomIndividual(x, subpopulation, state, thread);
                        if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
                            best = j;
                    }
                }
                candidates.add(best);
            }

            //Step 1: second selection
            int best = candidates.get(0);
            for(int i=1; i<candidates.size(); i++){
                if(pickWorst){
                    int j = candidates.get(i);
                    if (!fitness.betterThanBasedOnRelationshipModel(j, best, state, thread))  // j is at least as bad as best
                        best = j;
                }
                else{
                    int j = candidates.get(i);
                    if (fitness.betterThanBasedOnRelationshipModel(j, best, state, thread))  // j is at least as bad as best
                        best = j;
                }
            }

            return best;
        }

        //Strategy 2.0
        public int produceBasedOnRelationshipModel(final int subpopulation,
                                                   final EvolutionState state,
                                                   final int thread)
        {
            // pick size random individuals, then pick the best.
            Individual[] oldinds = state.population.subpops[subpopulation].individuals;
            int best = getRandomIndividual(0, subpopulation, state, thread);

            MultiObjectiveFitnessMultiTaskLearningSVROneSubpop fitness = (MultiObjectiveFitnessMultiTaskLearningSVROneSubpop)state.population.subpops[0].individuals[0].fitness;


            int s = getTournamentSizeToUse(state.random[thread]);

            if (pickWorst)
                for (int x=1;x<s;x++)
                {
                    int j = getRandomIndividual(x, subpopulation, state, thread);
                    if (!fitness.betterThanBasedOnRelationshipModel(j, best, state, thread))  // j is at least as bad as best
                        best = j;
                }
            else
                for (int x=1;x<s;x++)
                {
                    int j = getRandomIndividual(x, subpopulation, state, thread);
                    if (fitness.betterThanBasedOnRelationshipModel(j, best, state, thread))  // j is better than best
                        best = j;
                }


            return best;
        }
    
    }
