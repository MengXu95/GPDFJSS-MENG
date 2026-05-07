/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multiobjective.MPSLGP;

import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.simple.SimpleBreeder;
import ec.util.Parameter;

/* 
 * NSGA2Breeder.java
 * 
 * Created: Thu Feb 04 2010
 * By: Faisal Abidi and Sean Luke
 */

/**
 * This SimpleBreeder subclass breeds a set of children from the Population, then
 * joins the original Population with the children in a (mu+mu) fashion.   An NSGA2Breeder
 * may have multiple threads for breeding.
 *
 * <p>NSGA-II has fixed archive size (the population size), and so ignores the 'elites'
 * declaration.  However it will adhere to the 'reevaluate-elites' parameter in SimpleBreeder
 * to determine whether to force fitness reevaluation.

 */

public class PSLBreederKeepParetoFront extends SimpleBreeder
    {
        boolean loasParetoFront;
    public void setup(final EvolutionState state, final Parameter base)
        {
        super.setup(state, base);
        // make sure SimpleBreeder's elites facility isn't being used
        for (int i = 0; i < elite.length; i++)  // we use elite.length here instead of pop.subpops.length because the population hasn't been made yet.
            if (usingElitism(i))
                state.output.warning("You're using elitism with NSGA2Breeder, which is not permitted and will be ignored.  However the reevaluate-elites parameter *will* bre recognized by NSGAEvaluator.",
                    base.push(P_ELITE).push(""+i));

//        for (int i = 0; i < state.population.subpops.length; i++)
//            if (reduceBy[i] != 0)
//                state.output.fatal("NSGA2Breeder does not support population reduction.", base.push(P_REDUCE_BY).push(""+i), null);
                        
        if (sequentialBreeding) // uh oh, haven't tested with this
            state.output.fatal("NSGA2Breeder does not support sequential evaluation.",
                base.push(P_SEQUENTIAL_BREEDING));

        if (!clonePipelineAndPopulation)
            state.output.fatal("clonePipelineAndPopulation must be true for NSGA2Breeder.");

        Parameter loadParetoFrontParam = new Parameter("load-Pareto-front"); //add by mengxu 2023.01.24
        this.loasParetoFront = state.parameters.getBoolean(loadParetoFrontParam, false); //add by mengxu 2023.01.24
        }

    /**
     * Override breedPopulation(). We take the result from the super method in
     * SimpleBreeder and append it to the old population. Hence, after
     * generation 0, every subsequent call to
     * <code>NSGA2Evaluator.evaluatePopulation()</code> will be passed a
     * population of 2x<code>originalPopSize</code> individuals.
     */
    public Population breedPopulation(EvolutionState state)
        {
            Population newpop = null;
            if(((GPRuleEvolutionStatePSL)state).useBroodRecombination){
                // generate 5 times population size individuals and then using surrogate to do preselection
                int times = 5;
                newpop = breedPopulationBroodRecombination(state, times);
            }
            else{
                //generate a number of population size individuals
                newpop = breedPopulationNormal(state);
            }
            if (state instanceof GPRuleEvolutionStatePSL) {
                ((GPRuleEvolutionStatePSL) state).assignTaskIndicesIfNeeded(newpop);
            }
            return newpop;
        }

        public Population breedPopulationBroodRecombination(EvolutionState state, int times)
        {
            Population newpop = null;
            if (clonePipelineAndPopulation) //default value: true
                //create a newpop with two subpopulations, but the individuals are empty.
                newpop = (Population) state.population.emptyClone();
            else //skip this part
            {
                if (backupPopulation == null)
                    backupPopulation = (Population) state.population.emptyClone();
                newpop = backupPopulation;
                newpop.clear(); //** Sets all Individuals in the Population to null, preparing it to be reused. */
                backupPopulation = state.population;  // swap in
            }

            // maybe resize?
            for(int i = 0; i < state.population.subpops.length; i++)
            {
                if (reduceBy[i] > 0) // 0 skip this
                {
                    int prospectiveSize = Math.max(
                            Math.max(state.population.subpops[i].individuals.length - reduceBy[i], minimumSize[i]),
                            numElites(state, i));
                    if (prospectiveSize < state.population.subpops[i].individuals.length)  // let's resize!
                    {
                        state.output.message("Subpop " + i + " reduced " + state.population.subpops[i].individuals.length + " -> " + prospectiveSize);
                        newpop.subpops[i].resize(prospectiveSize);
                    }
                }
            }

            //!!!!!
            // load elites into top of newpop
            loadElites(state, newpop);     /** A private helper function for breedPopulation which loads elites into a subpopulation. */
            if(this.loasParetoFront){
                loadParetoFrontElites(state, newpop); /** add by mengxu 2024.5.21 to also load Elites**/
            }


            // how many threads do we really need?  No more than the maximum number of individuals in any subpopulation
            int numThreads = 0;
            for(int x = 0; x < state.population.subpops.length; x++)
                numThreads = Math.max(numThreads, state.population.subpops[x].individuals.length); //numThreads = 2
            numThreads = Math.min(numThreads, state.breedthreads);
            //System.out.println(state.breedthreads);  //1  so,numThreads =1

            if (numThreads < state.breedthreads)
                state.output.warnOnce("Largest subpopulation size (" + numThreads +") is smaller than number of breedthreads (" + state.breedthreads + "), so fewer breedthreads will be created.");

            int numinds[][] =
                    new int[numThreads][state.population.subpops.length];
            int from[][] =
                    new int[numThreads][state.population.subpops.length];

            for(int x=0;x<state.population.subpops.length;x++)
            {
                int length = computeSubpopulationLengthConsiderBroodRecombination(state, newpop, x, 0,times);

                //add by mengxu to extend the original size to a new size
                Individual[] extendIndividuals = new Individual[newpop.subpops[x].individuals.length*times];
                // Copy the original array values into the new array
                System.arraycopy(newpop.subpops[x].individuals, 0, extendIndividuals, (extendIndividuals.length-newpop.subpops[x].individuals.length), newpop.subpops[x].individuals.length);
                newpop.subpops[x].individuals = extendIndividuals;

                // we will have some extra individuals.  We distribute these among the early subpopulations
                int individualsPerThread = length / numThreads;  // integer division
                int slop = length - numThreads * individualsPerThread;
                int currentFrom = 0;

                for(int y=0;y<numThreads;y++)
                {
                    if (slop > 0)
                    {
                        numinds[y][x] = individualsPerThread + 1;
                        slop--;
                    }
                    else
                        numinds[y][x] = individualsPerThread;

                    if (numinds[y][x] == 0)
                    {
                        state.output.warnOnce("More threads exist than can be used to breed some subpopulations (first example: subpopulation " + x + ")");
                    }

                    from[y][x] = currentFrom;
                    currentFrom += numinds[y][x];
                }
            }

            if (numThreads==1)
            {
                breedPopChunk(newpop,state,numinds[0],from[0],0);
            }
            else
            {
                System.out.println("Error in breeder!");
            }
            return newpop;
        }

        public Population breedPopulationNormal(EvolutionState state)
        {
            Population newpop = null;
            if (clonePipelineAndPopulation) //default value: true
                //create a newpop with two subpopulations, but the individuals are empty.
                newpop = (Population) state.population.emptyClone();
            else //skip this part
            {
                if (backupPopulation == null)
                    backupPopulation = (Population) state.population.emptyClone();
                newpop = backupPopulation;
                newpop.clear(); //** Sets all Individuals in the Population to null, preparing it to be reused. */
                backupPopulation = state.population;  // swap in
            }

            // maybe resize?
            for(int i = 0; i < state.population.subpops.length; i++)
            {
                if (reduceBy[i] > 0) // 0 skip this
                {
                    int prospectiveSize = Math.max(
                            Math.max(state.population.subpops[i].individuals.length - reduceBy[i], minimumSize[i]),
                            numElites(state, i));
                    if (prospectiveSize < state.population.subpops[i].individuals.length)  // let's resize!
                    {
                        state.output.message("Subpop " + i + " reduced " + state.population.subpops[i].individuals.length + " -> " + prospectiveSize);
                        newpop.subpops[i].resize(prospectiveSize);
                    }
                }
            }

            //!!!!!
            // load elites into top of newpop
            loadElites(state, newpop);     /** A private helper function for breedPopulation which loads elites into a subpopulation. */
            if(this.loasParetoFront){
                loadParetoFrontElites(state, newpop); /** add by mengxu 2024.5.21 to also load Elites**/
            }


            // how many threads do we really need?  No more than the maximum number of individuals in any subpopulation
            int numThreads = 0;
            for(int x = 0; x < state.population.subpops.length; x++)
                numThreads = Math.max(numThreads, state.population.subpops[x].individuals.length); //numThreads = 2
            numThreads = Math.min(numThreads, state.breedthreads);
            //System.out.println(state.breedthreads);  //1  so,numThreads =1

            if (numThreads < state.breedthreads)
                state.output.warnOnce("Largest subpopulation size (" + numThreads +") is smaller than number of breedthreads (" + state.breedthreads + "), so fewer breedthreads will be created.");

            int numinds[][] =
                    new int[numThreads][state.population.subpops.length];
            int from[][] =
                    new int[numThreads][state.population.subpops.length];

            for(int x=0;x<state.population.subpops.length;x++)
            {
                int length = computeSubpopulationLength(state, newpop, x, 0);

                // we will have some extra individuals.  We distribute these among the early subpopulations
                int individualsPerThread = length / numThreads;  // integer division
                int slop = length - numThreads * individualsPerThread;
                int currentFrom = 0;

                for(int y=0;y<numThreads;y++)
                {
                    if (slop > 0)
                    {
                        numinds[y][x] = individualsPerThread + 1;
                        slop--;
                    }
                    else
                        numinds[y][x] = individualsPerThread;

                    if (numinds[y][x] == 0)
                    {
                        state.output.warnOnce("More threads exist than can be used to breed some subpopulations (first example: subpopulation " + x + ")");
                    }

                    from[y][x] = currentFrom;
                    currentFrom += numinds[y][x];
                }
            }

            if (numThreads==1)
            {
                breedPopChunk(newpop,state,numinds[0],from[0],0);
            }
            else
            {
                System.out.println("Error in breeder!");
            }
            return newpop;
        }

        protected void loadParetoFrontElites(EvolutionState state, Population newpop)
        {
            Object[] trueParetofrontIndividual = ((KNNsurrogateClearingPSLEvaluatorbasedonHV)state.evaluator).trueParetofrontIndividuals;
            for(int sub=0;sub<state.population.subpops.length;sub++){
                Individual[] inds = newpop.subpops[sub].individuals; // has not value
                for(int i=0; i<trueParetofrontIndividual.length; i++){
                    int x = inds.length-numElites(state, sub)-trueParetofrontIndividual.length + i;
                    inds[x] = (Individual)((Individual)trueParetofrontIndividual[i]).clone();
                }
            }
            // optionally force reevaluation
            unmarkElitesEvaluated(state, newpop);
        }

        public int computeSubpopulationLength(EvolutionState state, Population newpop, int subpopulation, int threadnum)
        {
            if (!shouldBreedSubpop(state, subpopulation, threadnum))
                return newpop.subpops[subpopulation].individuals.length;  // we're not breeding the population, just copy over the whole thing

            int numInParetoFront = 0;
            if(this.loasParetoFront){
                //add by mengxu to add Pareto front individual as elites 2024.5.21
                Object[] trueParetofrontIndividual = ((KNNsurrogateClearingPSLEvaluatorbasedonHV)state.evaluator).trueParetofrontIndividuals;
                numInParetoFront = trueParetofrontIndividual.length;
            }

            return newpop.subpops[subpopulation].individuals.length - (numElites(state, subpopulation))-numInParetoFront; // we're breeding population, so elitism may have happened
        }

        public int computeSubpopulationLengthConsiderBroodRecombination(EvolutionState state, Population newpop, int subpopulation, int threadnum, int times)
        {
            if (!shouldBreedSubpop(state, subpopulation, threadnum))
                return newpop.subpops[subpopulation].individuals.length;  // we're not breeding the population, just copy over the whole thing

            int numInParetoFront = 0;
            if(this.loasParetoFront){
                //add by mengxu to add Pareto front individual as elites 2024.5.21
                Object[] trueParetofrontIndividual = ((KNNsurrogateClearingPSLEvaluatorbasedonHV)state.evaluator).trueParetofrontIndividuals;
                numInParetoFront = trueParetofrontIndividual.length;
            }

            return newpop.subpops[subpopulation].individuals.length*times - (numElites(state, subpopulation))-numInParetoFront; // we're breeding population, so elitism may have happened
        }
    }
