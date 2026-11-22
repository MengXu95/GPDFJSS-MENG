/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multiobjective.NSGPII;

import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.simple.SimpleBreeder;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MPSLGP.FairComparisonProtocol;

/* 
 * NSGA2Breeder.java
 * 
 * Created: Thu Feb 04 2010
 * By: Faisal Abidi and Sean Luke
 */

/**
 * Offspring-replacement NSGPII without a parent--offspring population merge.
 * Aligned comparisons retain explicit protected elite copies.

 */

public class NSGA2BreederNoEnvironmentalSelection extends SimpleBreeder
    {
    public void setup(final EvolutionState state, final Parameter base)
        {
        super.setup(state, base);
        // make sure SimpleBreeder's elites facility isn't being used
        for (int i = 0; i < elite.length; i++)  // we use elite.length here instead of pop.subpops.length because the population hasn't been made yet.
            if (usingElitism(i) && !FairComparisonProtocol.enabled(state))
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
        }

    @Override
    protected void loadElites(EvolutionState state, Population newpop) {
        if (FairComparisonProtocol.enabled(state)) { FairComparisonProtocol.loadElites(state, newpop); }
        else { super.loadElites(state, newpop); }
    }

    public Population breedPopulation(EvolutionState state)
        {
        Population oldPop = (Population) state.population;
        Population newPop = super.breedPopulation(state); //offspring individuals obtained by simpleBreeder

        //modified by mengxu to do not do environmental selection
//        Individual[] combinedInds;
//        Subpopulation[] subpops = oldPop.subpops;
//        Subpopulation oldSubpop;
//        Subpopulation newSubpop;
//        int subpopsLength = subpops.length;
//
//        for (int i = 0; i < subpopsLength; i++)
//            {
//            oldSubpop = oldPop.subpops[i];
//            newSubpop = newPop.subpops[i];
//            combinedInds = new Individual[oldSubpop.individuals.length + newSubpop.individuals.length]; //parent and offsprings
//            System.arraycopy(newSubpop.individuals, 0, combinedInds, 0,  newSubpop.individuals.length);
//            System.arraycopy(oldSubpop.individuals, 0, combinedInds,  newSubpop.individuals.length, oldSubpop.individuals.length);
//            newSubpop.individuals = combinedInds;
//            }
        return newPop;
        }
    }
