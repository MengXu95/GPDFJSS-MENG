/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multicasePareto;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import yimei.jss.jobshop.Objective;

import java.util.ArrayList;
import java.util.List;

/* 
 * NSGA2MultiObjectiveFitness.java
 * 
 * Created: Thu Feb 04 2010
 * By: Faisal Abidi and Sean Luke
 */

/**
 * NSGA2MultiObjectiveFitness is a subclass of MultiObjeciveFitness which
 * adds auxiliary fitness measures (sparsity, rank) largely used by MultiObjectiveStatistics.
 * It also redefines the comparison measures to compare based on rank, and break ties
 * based on sparsity. 
 *
 */

public class OneInstanceMultiCaseFitnessNSGAII extends MultiObjectiveFitness
    {
    public static final String NSGA2_RANK_PREAMBLE = "Rank: ";
    public static final String NSGA2_SPARSITY_PREAMBLE = "Sparsity: ";

    public String[] getAuxilliaryFitnessNames() { return new String[] { "Rank", "Sparsity" }; }
    public double[] getAuxilliaryFitnessValues() { return new double[] { rank, sparsity }; }
        
    /** Pareto front rank measure (lower ranks are better) */
    public int rank;

    /** Sparsity along front rank measure (higher sparsity is better) */
    public double sparsity;

        /** parameter for size of objectives */
        public static final String P_NUMCASE = "num-case";
        public static final String P_USE_NOVELTY_SCORE = "use-novelty-score";

        public double[][] multiInstanceMultiObjectiveFitness;

        public double[][] multiInstanceMultiObjectiveBinaryBring; //add 2021.12.23 for DPS selection

        public double[][] multiInstanceMultiObjectiveNormalisation; //add 2021.01.12 for DPS with new strategy selection

        public boolean useNoveltyScore;
        public double[][] multiInstanceNoveltyScore;//add 2021.12.10
        public List<Objective> currentObjectives; //modified by mengxu

        public void setCurrentObjective(List<Objective> currentObjectives) {//modified by mengxu
            this.currentObjectives = currentObjectives;
        }

        public void setup(EvolutionState state, Parameter base)
        {
            super.setup(state, base); // unnecessary really

            Parameter def = defaultBase();
            int numInstance;

            int numIns;
            numInstance = state.parameters.getInt(new Parameter(P_NUMCASE), def.push(P_NUMCASE), 0);
            if (numInstance <= 0)
                state.output.fatal("The number of instances must be an integer >= 1.", base.push(P_NUMCASE), def.push(P_NUMCASE));

            int numFitnesses;

            numFitnesses = state.parameters.getInt(base.push(P_NUMOBJECTIVES), def.push(P_NUMOBJECTIVES), 0);
            if (numFitnesses <= 0)
                state.output.fatal("The number of objectives must be an integer >= 1.", base.push(P_NUMOBJECTIVES), def.push(P_NUMOBJECTIVES));


            multiInstanceMultiObjectiveFitness = new double[numInstance][numFitnesses];

            //add 2021.12.10
            useNoveltyScore = state.parameters.getBoolean(new Parameter(P_USE_NOVELTY_SCORE), null, false);
            if(useNoveltyScore){
                multiInstanceNoveltyScore = new double[numInstance][numFitnesses];
            }

            state.output.exitIfErrors();
        }

        public double getFitness(int indexInstance, int indexObjective)
        {
            //test
//        System.out.println("length: " +multiInstanceMultiObjectiveFitness.length);
            return multiInstanceMultiObjectiveFitness[indexInstance][indexObjective];
        }

        public double getNoveltyScore(int indexInstance, int indexObjective)
        {
            //test
//        System.out.println("length: " +multiInstanceMultiObjectiveFitness.length);
            return multiInstanceNoveltyScore[indexInstance][indexObjective];
        }

        public void setMultiInstanceFitness(final EvolutionState state, double[][] multiInstanceMultiObjectiveFitness)
        {
            if (multiInstanceMultiObjectiveFitness == null)
            {
                state.output.fatal("Null objective array provided to MultiObjectiveFitness.");
            }

            this.multiInstanceMultiObjectiveFitness = multiInstanceMultiObjectiveFitness;

//        //check
//        System.out.println("check 2: " + this.multiInstanceMultiObjectiveFitness.length);
//        for(int i=0; i<this.multiInstanceMultiObjectiveFitness.length;i++){
//            System.out.print(this.multiInstanceMultiObjectiveFitness[i][0] + "  ");
//        }
//        System.out.println();
        }

        public double[][] getMultiInstanceMultiObjectiveBinaryBring() {
            return multiInstanceMultiObjectiveBinaryBring;
        }

        public void setMultiInstanceMultiObjectiveBinaryBring(double[][] multiInstanceMultiObjectiveBinaryBring) {
            this.multiInstanceMultiObjectiveBinaryBring = multiInstanceMultiObjectiveBinaryBring;
        }

        public double[][] getMultiInstanceMultiObjectiveNormalisation() {
            return multiInstanceMultiObjectiveNormalisation;
        }

        public void setMultiInstanceMultiObjectiveNormalisation(double[][] multiInstanceMultiObjectiveNormalisation) {
            this.multiInstanceMultiObjectiveNormalisation = multiInstanceMultiObjectiveNormalisation;
        }

        public void setMultiInstanceNoveltyScore(final EvolutionState state, double[][] multiInstanceNoveltyScore)
        {
            this.multiInstanceNoveltyScore = multiInstanceNoveltyScore;
        }

        public String fitnessToStringForHumansMax(){
            String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
            double maxFitness=-1;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++) {
                if (maxFitness < multiInstanceMultiObjectiveFitness[x][0]) {
                    maxFitness = multiInstanceMultiObjectiveFitness[x][0];
                }
            }
            s = s + maxFitness;
            return s + FITNESS_POSTAMBLE;
        }

        public String fitnessToStringForHumansMean(){
            String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
            double sum = 0;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
            {
                sum += multiInstanceMultiObjectiveFitness[x][0];
            }
            sum = sum / multiInstanceMultiObjectiveFitness.length;
            s = s + sum;
            return s + FITNESS_POSTAMBLE;
        }

        public double fitnessMax(){
            double maxFitness=-1;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++) {
                if (maxFitness < multiInstanceMultiObjectiveFitness[x][0]) {
                    maxFitness = multiInstanceMultiObjectiveFitness[x][0];
                }
            }
            return maxFitness;
        }

        public double fitnessMean(){
            double sum = 0;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
            {
                sum += multiInstanceMultiObjectiveFitness[x][0];
            }
            sum = sum / multiInstanceMultiObjectiveFitness.length;
            return sum;
        }

        public double fitness() {
            Objective objective = currentObjectives.get(0);
            switch (objective) {
                case MEAN_FLOWTIME:
                case MEAN_WEIGHTED_FLOWTIME:
                case MEAN_TARDINESS:
                case MEAN_WEIGHTED_TARDINESS:
                    return fitnessMean();
                case MAX_FLOWTIME:
                case MAX_WEIGHTED_FLOWTIME:
                case MAX_TARDINESS:
                case MAX_WEIGHTED_TARDINESS:
                    return fitnessMax();
            }
            System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");
            return Double.MAX_VALUE;
        }

        @Override
        public String fitnessToStringForHumans()
        {
            //version 3
            Objective objective = currentObjectives.get(0);
            switch (objective) {
                case MEAN_FLOWTIME:
                case MEAN_WEIGHTED_FLOWTIME:
                case MEAN_TARDINESS:
                case MEAN_WEIGHTED_TARDINESS:
                    return fitnessToStringForHumansMean();
                case MAX_FLOWTIME:
                case MAX_WEIGHTED_FLOWTIME:
                case MAX_TARDINESS:
                case MAX_WEIGHTED_TARDINESS:
                    return fitnessToStringForHumansMax();
            }
            System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");
            return null;
        }

    public boolean equivalentTo(Fitness _fitness)
        {
        OneInstanceMultiCaseFitnessNSGAII other = (OneInstanceMultiCaseFitnessNSGAII) _fitness;
        return (rank == ((OneInstanceMultiCaseFitnessNSGAII) _fitness).rank) &&
            (sparsity == other.sparsity);
        }

    /**
     * We specify the tournament selection criteria, Rank (lower
     * values are better) and Sparsity (higher values are better)
     */
    public boolean betterThan(Fitness _fitness)
        {
        OneInstanceMultiCaseFitnessNSGAII other = (OneInstanceMultiCaseFitnessNSGAII) _fitness;
        // Rank should always be minimized.
        if (rank < ((OneInstanceMultiCaseFitnessNSGAII) _fitness).rank)
            return true;
        else if (rank > ((OneInstanceMultiCaseFitnessNSGAII) _fitness).rank)
            return false;
                
        // otherwise try sparsity
        return (sparsity > other.sparsity);
        }


        public boolean paretoDominates(OneInstanceMultiCaseFitnessNSGAII other)
        {
            boolean abeatsb = false;

            if (multiInstanceMultiObjectiveFitness.length != other.multiInstanceMultiObjectiveFitness.length)
                throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
            {

                    if (multiInstanceMultiObjectiveFitness[x][0] < other.multiInstanceMultiObjectiveFitness[x][0]) //objective[0] and objective[1]: [2514.5953504074932, 498.03951619189513]
                        abeatsb = true;
                    else if (multiInstanceMultiObjectiveFitness[x][0] > other.multiInstanceMultiObjectiveFitness[x][0])
                        return false;

            }

            return abeatsb;
        }

        /**
         * Divides an array of Individuals into the Pareto front and the "nonFront" (everyone else).
         * The Pareto front is returned.  You may provide ArrayLists for the front and a nonFront.
         * If you provide null for the front, an ArrayList will be created for you.  If you provide
         * null for the nonFront, non-front individuals will not be added to it.  This algorithm is
         * O(n^2).
         */
        public static ArrayList partitionIntoParetoFront(Individual[] inds, ArrayList front, ArrayList nonFront)
        {
            if (front == null)
                front = new ArrayList();

            // put the first guy in the front
            front.add(inds[0]);

            // iterate over all the remaining individuals
            for (int i = 1; i < inds.length; i++)
            {
                Individual ind = (Individual) (inds[i]);

                boolean noOneWasBetter = true;
                int frontSize = front.size();

                // iterate over the entire front
                for (int j = 0; j < frontSize; j++)
                {
                    Individual frontmember = (Individual) (front.get(j));

                    // if the front member is better than the individual, dump the individual and go to the next one
                    if (((OneInstanceMultiCaseFitnessNSGAII) (frontmember.fitness)).paretoDominates((OneInstanceMultiCaseFitnessNSGAII) (ind.fitness)))
                    {
                        if (nonFront != null) nonFront.add(ind);
                        noOneWasBetter = false;
                        break;  // failed.  He's not in the front
                    }
                    // if the individual was better than the front member, dump the front member.  But look over the
                    // other front members (don't break) because others might be dominated by the individual as well.
                    else if (((OneInstanceMultiCaseFitnessNSGAII) (ind.fitness)).paretoDominates((OneInstanceMultiCaseFitnessNSGAII) (frontmember.fitness)))
                    {
                        yank(j, front);
                        // a front member is dominated by the new individual.  Replace him
                        frontSize--; // member got removed
                        j--;  // because there's another guy we now need to consider in his place
                        if (nonFront != null) nonFront.add(frontmember);
                    }
                }
                if (noOneWasBetter)
                    front.add(ind);
            }
            return front;
        }


        /** Divides inds into pareto front ranks (each an ArrayList), and returns them, in order,
         stored in an ArrayList. */
        public static ArrayList partitionIntoRanks(Individual[] inds)
        {
            Individual[] dummy = new Individual[0];
            ArrayList frontsByRank = new ArrayList();//each front is stored by one arraylist

            while(inds.length > 0) //the dominated individuals
            {
                ArrayList front = new ArrayList();
                ArrayList nonFront = new ArrayList();
                OneInstanceMultiCaseFitnessNSGAII.partitionIntoParetoFront(inds, front, nonFront); //after this, the individuals are divided into two groups: front and nonfront

                // build inds out of remainder
                inds = (Individual[]) nonFront.toArray(dummy);//copy the non-dominated individuals into inds
                frontsByRank.add(front); //add the non-dominated individuals into frontsByRank
            }
            return frontsByRank;
        }


        // Remove an individual from the ArrayList, shifting the topmost
        // individual in his place
        static void yank(int val, ArrayList list)
        {
            int size = list.size();
            list.set(val, list.get(size - 1));
            list.remove(size - 1);
        }
    }
