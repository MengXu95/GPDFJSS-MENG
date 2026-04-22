/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package ec.breed;
import ec.*;
import ec.util.*;
import mengxu.algorithm.diversepartnerselection.GPRuleEvolutionStateDPS;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.GPRuleEvolutionStateDPSNS;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNSurrogate.GPRuleEvolutionStateDPSNSBR;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNSurrogate.OffspringSelectionStrategyForTopTwoCrossover.GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy;

/*
 * MultiBreedingPipeline.java
 *
 * Created: December 28, 1999
 * By: Sean Luke
 */

/**
 * MultiBreedingPipeline is a BreedingPipeline stores some <i>n</i> child sources;
 * each time it must produce an individual or two,
 * it picks one of these sources at random and has it do the production.

 <p><b>Typical Number of Individuals Produced Per <tt>produce(...)</tt> call</b><br>
 If by <i>base</i>.<tt>generate-max</tt> is <tt>true</tt>, then always the maximum
 number of the typical numbers of any child source.  If <tt>false</itt>, then varies
 depending on the child source picked.

 <p><b>Number of Sources</b><br>
 Dynamic.  As many as the user specifies.

 <p><b>Parameters</b><br>
 <table>
 <tr><td valign=top><i>base</i>.<tt>generate-max</tt><br>
 <font size=-1> bool = <tt>true</tt> (default) or <tt>false</tt></font></td>
 <td valign=top>(Each time produce(...) is called, should the MultiBreedingPipeline
 force all its sources to produce exactly the same number of individuals as the largest
 typical number of individuals produced by any source in the group?)</td></tr>
 </table>

 <p><b>Default Base</b><br>
 breed.multibreed

 *
 * @author Sean Luke
 * @version 1.0
 */

public class MultiBreedingPipeline extends BreedingPipeline
    {
    public static final String P_GEN_MAX = "generate-max";
    public static final String P_MULTIBREED = "multibreed";

    public int maxGeneratable;
    public boolean generateMax;

    //fzhang 21.7.2018
    private int preGenerations;

    public Parameter defaultBase()
        {
        return BreedDefaults.base().push(P_MULTIBREED);
        }

    public int numSources() { return DYNAMIC_SOURCES; }

    public void setup(final EvolutionState state, final Parameter base)
        {
        super.setup(state,base); // load all the parameters about breeding pipeline and read the probability

        /*preGenerations = state.parameters.getIntWithDefault(
                new Parameter(P_PRE_GENERATIONS), null, -1);  //50
        //fzhang 21.7.2018
        if(state.generation >= preGenerations) {
        	 sources[0].probability = 0.15 + (0.8-0.15)*(state.generation-preGenerations)/(state.numGenerations-1-preGenerations);
             sources[1].probability = 0.8 - (0.8-0.15)*(state.generation-preGenerations)/(state.numGenerations-1-preGenerations);
             sources[2].probability = 0.05;
        }

        System.out.println(sources[0].probability);
        System.out.println(sources[1].probability);
        System.out.println(sources[2].probability);*/

        Parameter def = defaultBase();  // return BreedDefaults.base().push(P_MULTIBREED);
        //System.out.println(def); //breed.multibreed       breed.multibreed

        double total = 0.0;

        //System.out.println(sources.length);  //3  3
        if (sources.length == 0)  // uh oh
            state.output.fatal("num-sources must be provided and > 0 for MultiBreedingPipeline",
                base.push(P_NUMSOURCES), def.push(P_NUMSOURCES));

        for(int x=0;x<sources.length;x++)
            {
            // make sure the sources are actually breeding pipelines
            if (!(sources[x] instanceof BreedingPipeline))
                state.output.error("Source #" + x + "is not a BreedingPipeline",base);
            else if (sources[x].probability<0.0) // null checked from state.output.error above
                state.output.error("Pipe #" + x + " must have a probability >= 0.0",base);  // convenient that NO_PROBABILITY is -1...
            else total += sources[x].probability;
            }

        state.output.exitIfErrors();

        // Now check for nonzero probability (we know it's positive)
        if (total == 0.0)
            state.output.warning("MultiBreedingPipeline's children have all zero probabilities -- this will be treated as a uniform distribution.  This could be an error.", base);

        // allow all zero probabilities
        BreedingSource.setupProbabilities(sources);

        generateMax = state.parameters.getBoolean(base.push(P_GEN_MAX),def.push(P_GEN_MAX),true); //false: the maximum number of the typical numbers of
        //any child source varies depending on the child source picked.
        maxGeneratable=0;  // indicates that I don't know what it is yet.

        // declare that likelihood isn't used
        if (likelihood < 1.0)
            state.output.warning("MultiBreedingPipeline does not respond to the 'likelihood' parameter.",
                base.push(P_LIKELIHOOD), def.push(P_LIKELIHOOD));
        }

    /** Returns the max of typicalIndsProduced() of all its children */
    public int typicalIndsProduced()
        {
        if (maxGeneratable==0) // not determined yet
            maxGeneratable = maxChildProduction();
        return maxGeneratable;
        }


    public int produce(final int min,
        final int max,
        final int start,
        final int subpopulation,
        final Individual[] inds,
        final EvolutionState state,
        final int thread)

        {
        //modified by mengxu 2021.12.23
        //todo: need to check if this is right
        BreedingSource s;
        if(state instanceof GPRuleEvolutionStateDPSNSBR || state instanceof GPRuleEvolutionStateDPSNS || state instanceof GPRuleEvolutionStateDPS || state instanceof GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy){
            BreedingSource[] sources = state.population.subpops[0].species.pipe_prototype.sources;
            s = sources[BreedingSource.pickRandom(
                    sources,state.random[thread].nextDouble())];
        }
        else{
            s = sources[BreedingSource.pickRandom(
                sources,state.random[thread].nextDouble())]; //fzhang state.random[thread].nextDouble()): random prob
        }


        //original
//        BreedingSource s = sources[BreedingSource.pickRandom(
//                sources,state.random[thread].nextDouble())]; //fzhang state.random[thread].nextDouble()): random prob
        int total;

        if (generateMax) //false
            {
            if (maxGeneratable==0)
                maxGeneratable = maxChildProduction();
            int n = maxGeneratable;
            if (n < min) n = min;
            if (n > max) n = max;

            total = s.produce(
                n,n,start,subpopulation,inds,state,thread);
            }
        else
            {//total fzhang 2019.6.15 how many parents do we need? commment
            total = s.produce(
                min,max,start,subpopulation,inds,state,thread);
            }

        //add by meng xu to save the parents ID for crossover 2021.01.17
        //2021.10.14 to find the difference with Tournament selection


        // clone if necessary
        if (s instanceof SelectionMethod)
            for(int q=start; q < total+start; q++)
                inds[q] = (Individual)(inds[q].clone());

        return total;
        }

        public int produceFrac(final int min,
                           final int max,
                           final int start,
                           final int subpopulation,
                           final Individual[] inds,
                           final EvolutionState state,
                           final int thread)

        {
            BreedingSource s = sources[BreedingSource.pickRandom(
                    sources,state.random[thread].nextDouble())]; //fzhang state.random[thread].nextDouble()): random prob
            int total;

            if (generateMax) //false
            {
                if (maxGeneratable==0)
                    maxGeneratable = maxChildProduction();
                int n = maxGeneratable;
                if (n < min) n = min;
                if (n > max) n = max;

                total = s.produce(
                        n,n,start,subpopulation,inds,state,thread);
            }
            else
            {//total fzhang 2019.6.15 how many parents do we need? commment
                total = s.produce(
                        min,max,start,subpopulation,inds,state,thread);
            }

            // clone if necessary
            if (s instanceof SelectionMethod)
                for(int q=start; q < total+start; q++)
                    inds[q] = (Individual)(inds[q].clone());

            return total;
        }
    }
