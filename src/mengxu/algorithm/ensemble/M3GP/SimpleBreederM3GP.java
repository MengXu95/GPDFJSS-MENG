/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package mengxu.algorithm.ensemble.M3GP;

import ec.*;
import ec.coevolve.MultiPopCoevolutionaryEvaluator;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.multiobjective.spea2.SPEA2Breeder;
import ec.simple.SimpleBreeder;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparatorL;
import ec.util.ThreadPool;
import mengxu.algorithm.coevolutiongp.CCGPMCTS.GPRuleEvolutionStateMCTS;
import mengxu.algorithm.multipletreegp.GPRuleEvolutionStateUpdateImmediate;
import mengxu.algorithm.multipletreegp.GPRuleEvolutionStateUpdateLate;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

/*
 * SimpleBreeder.java
 *
 * Created: Tue Aug 10 21:00:11 1999
 * By: Sean Luke
 */

/**
 * Breeds each subpopulation separately, with no inter-population exchange,
 * and using a generational approach.  A SimpleBreeder may have multiple
 * threads; it divvys up a subpopulation into chunks and hands one chunk
 * to each thread to populate.  One array of BreedingPipelines is obtained
 * from a population's Species for each operating breeding thread.
 *
 * <p>Prior to breeding a subpopulation, a SimpleBreeder may first fill part of the new
 * subpopulation up with the best <i>n</i> individuals from the old subpopulation.
 * By default, <i>n</i> is 0 for each subpopulation (that is, this "elitism"
 * is not done).  The elitist step is performed by a single thread.
 *
 * <p>If the <i>sequential</i> parameter below is true, then breeding is done specially:
 * instead of breeding all Subpopulations each generation, we only breed one each generation.
 * The subpopulation index to breed is determined by taking the generation number, modulo the
 * total number of subpopulations.  Use of this parameter outside of a coevolutionary context
 * (see ec.coevolve.MultiPopCoevolutionaryEvaluator) is very rare indeed.
 *
 * <p>SimpleBreeder adheres to the default-subpop parameter in Population: if either an 'elite'
 * or 'reevaluate-elites' parameter is missing, it will use the default subpopulation's value
 * and signal a warning.
 *
 <p><b>Parameters</b><br>
 <table>
 <tr><td valign=top><tt><i>base</i>.elite.<i>i</i></tt><br>
 <font size=-1>int >= 0 (default=0)</font></td>
 <td valign=top>(the number of elitist individuals for subpopulation <i>i</i>)</td></tr>
 <tr><td valign=top><tt><i>base</i>.reduce-by.<i>i</i></tt><br>
 <font size=-1>int >= 0 (default=0)</font></td>
 <td valign=top>(how many to reduce subpopulation <i>i</i> by each generation)</td></tr>
 <tr><td valign=top><tt><i>base</i>.minimum-size.<i>i</i></tt><br>
 <font size=-1>int >= 2 (default=2)</font></td>
 <td valign=top>(the minimum size for subpopulation <i>i</i> regardless of reduction)</td></tr>
 <tr><td valign=top><tt><i>base</i>.reevaluate-elites.<i>i</i></tt><br>
 <font size=-1>boolean (default = false)</font></td>
 <td valign=top>(should we reevaluate the elites of subpopulation <i>i</i> each generation?)</td></tr>
 <tr><td valign=top><tt><i>base</i>.sequential</tt><br>
 <font size=-1>boolean (default = false)</font></td>
 <td valign=top>(should we breed just one subpopulation each generation (as opposed to all of them)?)</td></tr>
 </table>
 *
 *
 * @author Sean Luke
 * @version 1.0
 */

public class SimpleBreederM3GP extends SimpleBreeder
    {

    /** A simple breeder that doesn't attempt to do any cross-
        population breeding.  Basically it applies pipelines,
        one per thread, to various subchunks of a new population. */
    public Population breedPopulation(EvolutionState state)
        {
        Population newpop = (Population) state.population;

//        Population newpop = null;
//        if (clonePipelineAndPopulation) //default value: true
//        	//create a newpop with two subpopulations, but the individuals are empty.
//            newpop = (Population) state.population.emptyClone();
//        else //skip this part
//            {
//            if (backupPopulation == null)
//                backupPopulation = (Population) state.population.emptyClone();
//            newpop = backupPopulation;
//            newpop.clear(); //** Sets all Individuals in the Population to null, preparing it to be reused. */
//            backupPopulation = state.population;  // swap in
//            }
//
//        // maybe resize?
//        for(int i = 0; i < state.population.subpops.length; i++)
//            {
//            if (reduceBy[i] > 0) // 0 skip this
//                {
//                int prospectiveSize = Math.max(
//                    Math.max(state.population.subpops[i].individuals.length - reduceBy[i], minimumSize[i]),
//                    numElites(state, i));
//                if (prospectiveSize < state.population.subpops[i].individuals.length)  // let's resize!
//                    {
//                    state.output.message("Subpop " + i + " reduced " + state.population.subpops[i].individuals.length + " -> " + prospectiveSize);
//                    newpop.subpops[i].resize(prospectiveSize);
//                    }
//                }
//            }
//
//        //!!!!!
//        // load elites into top of newpop
//        loadElites(state, newpop);     /** A private helper function for breedPopulation which loads elites into
//        a subpopulation. */

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
            // start up the threads
            for(int y=0;y<numThreads;y++)
                {
                SimpleBreederM3GPThread r = new SimpleBreederM3GPThread();
                r.threadnum = y;
                r.newpop = newpop;
                r.numinds = numinds[y];
                r.from = from[y];
                r.me = this;
                r.state = state;
                pool.start(r, "ECJ Breeding Thread " + y );
                }

            pool.joinAll();
            }
        return newpop;
        }


        /**
         *
         * @param pop: evaluate population directly rather than state.population
         * @return fzhang 2019.6.9
         */
        public Population breedPopulation(EvolutionState state, Population pop)
        {
            //newpop is the generated offspring population
            Population newpop = null;
            if (clonePipelineAndPopulation) //default value: true
                //create a newpop with two subpopulations, but the individuals are empty.
                newpop = (Population) pop.emptyClone();
            else //skip this part
            {
                if (backupPopulation == null)
                    backupPopulation = (Population) pop.emptyClone();
                newpop = backupPopulation;
                newpop.clear(); //** Sets all Individuals in the Population to null, preparing it to be reused. */
                backupPopulation = pop;  // swap in
            }

            // maybe resize?
            for(int i = 0; i < state.population.subpops.length; i++)
            {
                if (reduceBy[i] > 0) // 0 skip this
                {
                    int prospectiveSize = Math.max(
                            Math.max(pop.subpops[i].individuals.length - reduceBy[i], minimumSize[i]),
                            numElites(state, i));
                    if (prospectiveSize < pop.subpops[i].individuals.length)  // let's resize!
                    {
                        state.output.message("Subpop " + i + " reduced " + pop.subpops[i].individuals.length + " -> " + prospectiveSize);
                        newpop.subpops[i].resize(prospectiveSize);
                    }
                }
            }

           loadElitesFrac(state, pop, newpop);
            // how many threads do we really need?  No more than the maximum number of individuals in any subpopulation
            int numThreads = 0;
            for(int x = 0; x < pop.subpops.length; x++)
                numThreads = Math.max(numThreads, pop.subpops[x].individuals.length); //numThreads = 2
            numThreads = Math.min(numThreads, state.breedthreads);
            //System.out.println(state.breedthreads);  //1  so,numThreads =1

            if (numThreads < state.breedthreads)
                state.output.warnOnce("Largest subpopulation size (" + numThreads +") is smaller than number of breedthreads (" + state.breedthreads + "), so fewer breedthreads will be created.");

            int numinds[][] =
                    new int[numThreads][pop.subpops.length];
            int from[][] =
                    new int[numThreads][pop.subpops.length];

            for(int x=0;x<pop.subpops.length;x++)
            {
                //int length = computeSubpopulationLength(state, newpop, x, 0);
                int length = pop.subpops[x].individuals.length - (numElitesFrac(state, x));

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
                // start up the threads
                for(int y=0;y<numThreads;y++)
                {
                    SimpleBreederM3GPThread r = new SimpleBreederM3GPThread();
                    r.threadnum = y;
                    r.newpop = newpop;
                    r.numinds = numinds[y];
                    r.from = from[y];
                    r.me = this;
                    r.state = state;
                    pool.start(r, "ECJ Breeding Thread " + y );
                }

                pool.joinAll();
            }
            return newpop;
        }

        /** A private helper function for breedPopulation which breeds a chunk
         of individuals in a subpopulation for a given thread.
         Although this method is declared
         public (for the benefit of a private helper class in this file),
         you should not call it. */

        protected void breedPopChunk(Population newpop, EvolutionState state, int[] numinds, int[] from, int threadnum)
        {
            for(int subpop=0;subpop<newpop.subpops.length;subpop++)
            {
                // if it's subpop's turn and we're doing sequential breeding...
                if (!shouldBreedSubpop(state, subpop, threadnum))
                {
                    // instead of breeding, we should just copy forward this subpopulation.  We'll copy the part we're assigned
                    for(int ind=from[subpop] ; ind < numinds[subpop] - from[subpop]; ind++)
                        // newpop.subpops[subpop].individuals[ind] = (Individual)(state.population.subpops[subpop].individuals[ind].clone());
                        // this could get dangerous
                        newpop.subpops[subpop].individuals[ind] = state.population.subpops[subpop].individuals[ind];
                }
                else
                {
                    // do regular breeding of this subpopulation
                    BreedingPipeline bp = null;
                    if (clonePipelineAndPopulation)
                        bp = (BreedingPipeline)newpop.subpops[subpop].species.pipe_prototype.clone();
                    else
                        bp = (BreedingPipeline)newpop.subpops[subpop].species.pipe_prototype;

                    // check to make sure that the breeding pipeline produces
                    // the right kind of individuals.  Don't want a mistake there! :-)
                    int x;
                    if (!bp.produces(state,newpop,subpop,threadnum))
                        state.output.fatal("The Breeding Pipeline of subpopulation " + subpop + " does not produce individuals of the expected species " + newpop.subpops[subpop].species.getClass().getName() + " or fitness " + newpop.subpops[subpop].species.f_prototype );
                    bp.prepareToProduce(state,subpop,threadnum);

                    // start breedin'!

                    x=from[subpop];
                    int upperbound = from[subpop]+numinds[subpop];
                    while(x<upperbound) { //8   x = 0...7
                        x += bp.produce(1,upperbound-x,x,subpop,
                                newpop.subpops[subpop].individuals,
                                state,threadnum);
                    }
                    if (x>upperbound) // uh oh!  Someone blew it!
                        state.output.fatal("Whoa!  A breeding pipeline overwrote the space of another pipeline in subpopulation " + subpop + ".  You need to check your breeding pipeline code (in produce() ).");

                    bp.finishProducing(state,subpop,threadnum);
                }
            }
        }

    }


/** A private helper class for implementing multithreaded breeding */
class SimpleBreederM3GPThread implements Runnable
    {
    Population newpop;
    public int[] numinds;
    public int[] from;
    public SimpleBreederM3GP me;
    public EvolutionState state;
    public int threadnum;
    public void run()
        {
        me.breedPopChunk(newpop,state,numinds,from,threadnum);
        }
    }
