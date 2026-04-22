/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package mengxu.algorithm.multiobjective.phenotypeNSGPII.improvedCompareOne;

import ec.EvolutionState;
import ec.Individual;
import ec.SelectionMethod;
import ec.select.SelectDefaults;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;

/* 
 * TournamentSelection.java
 * 
 * Created: Mon Aug 30 19:27:15 1999
 * By: Sean Luke
 */

/**
 * Does a simple tournament selection, limited to the subpopulation it's
 * working in at the time.
 *
 * <p>Tournament selection works like this: first, <i>size</i> individuals
 * are chosen at random from the population.  Then of those individuals,
 * the one with the best fitness is selected.  
 * 
 * <p><i>size</i> can be any floating point value >= 1.0.  If it is a non-
 * integer value <i>x</i> then either a tournament of size ceil(x) is used
 * (with probability x - floor(x)), else a tournament of size floor(x) is used.
 *
 * <p>Common sizes for <i>size</i> include: 2, popular in Genetic Algorithms
 * circles, and 7, popularized in Genetic Programming by John Koza.
 * If the size is 1, then individuals are picked entirely at random.
 *
 * <p>Tournament selection is so simple that it doesn't need to maintain
 * a cache of any form, so many of the SelectionMethod methods just
 * don't do anything at all.
 *

 <p><b>Typical Number of Individuals Produced Per <tt>produce(...)</tt> call</b><br>
 Always 1.

 <p><b>Parameters</b><br>
 <table>
 <tr><td valign=top><i>base.</i><tt>size</tt><br>
 <font size=-1>double &gt;= 1</font></td>
 <td valign=top>(the tournament size)</td></tr>

 <tr><td valign=top><i>base.</i><tt>pick-worst</tt><br>
 <font size=-1> bool = <tt>true</tt> or <tt>false</tt> (default)</font></td>
 <td valign=top>(should we pick the <i>worst</i> individual in the tournament instead of the <i>best</i>?)</td></tr>

 </table>

 <p><b>Default Base</b><br>
 select.tournament

 *
 * @author Sean Luke
 * @version 1.0 
 */

public class TournamentSelectionSimilarityChecking extends SelectionMethod implements SteadyStateBSourceForm
    {
    /** default base */
    public static final String P_TOURNAMENT = "tournament";

    public static final String P_PICKWORST = "pick-worst";

    /** size parameter */
    public static final String P_SIZE = "size";

    /** Base size of the tournament; this may change.  */
    int size;

    /** Probablity of picking the size plus one */
    public double probabilityOfPickingSizePlusOne;
    
    /** Do we pick the worst instead of the best? */
    public boolean pickWorst;

    /** Probablity of using weighted-sum fitness function, add by mengxu 2023.06.19 */
    public static final String P_ProbabilityOfUsingWeightedSum = "probability-of-using-weighted-sum";
    public double probabilityOfUsingWeightedSum;

    public static final String P_UseWeightedSum = "use-weighted-sum";
    public boolean useWeightedSum;

    public Parameter defaultBase()
        {
        return SelectDefaults.base().push(P_TOURNAMENT);
        }
    
    public void setup(final EvolutionState state, final Parameter base)
        {
        super.setup(state,base);
        
        Parameter def = defaultBase();

        double val = state.parameters.getDouble(base.push(P_SIZE),def.push(P_SIZE),1.0);
        if (val < 1.0)
            state.output.fatal("Tournament size must be >= 1.",base.push(P_SIZE),def.push(P_SIZE));
        else if (val == (int) val)  // easy, it's just an integer
            {
            size = (int) val;
            probabilityOfPickingSizePlusOne = 0.0;
            }
        else
            {
            size = (int) Math.floor(val);
            probabilityOfPickingSizePlusOne = val - size;  // for example, if we have 5.4, then the probability of picking *6* is 0.4
            }

        pickWorst = state.parameters.getBoolean(base.push(P_PICKWORST),def.push(P_PICKWORST),false);

        probabilityOfUsingWeightedSum = state.parameters.getDoubleWithDefault(new Parameter(P_ProbabilityOfUsingWeightedSum),null,0.5);
        useWeightedSum = state.parameters.getBoolean(new Parameter(P_UseWeightedSum),null,false);

        }

    /** Returns a tournament size to use, at random, based on base size and probability of picking the size plus one. */
    public int getTournamentSizeToUse(MersenneTwisterFast random)
        {
        double p = probabilityOfPickingSizePlusOne;   // pulls us to under 35 bytes
        if (p == 0.0) return size;
        return size + (random.nextBoolean(p) ? 1 : 0);
        }


    /** Produces the index of a (typically uniformly distributed) randomly chosen individual
        to fill the tournament.  <i>number</> is the position of the individual in the tournament.  */
    public int getRandomIndividual(int number, int subpopulation, EvolutionState state, int thread)
        {
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        return state.random[thread].nextInt(oldinds.length);
        }

    private double[] generateRandomWeights(EvolutionState state, int numObjectives) {
//        int popSize = state.population.subpops[0].individuals.length;
            int range = 100; //todo: this parameter should be modified, I didn't find this parameter in Hisao's paper, I need to double check
        double[] weights = new double[numObjectives];
            if(numObjectives == 2) {
                int random_0 = state.random[0].nextInt(range);
                int random_1 = state.random[0].nextInt(range);
                weights[0] = (double)random_0 / ((double)random_0 + (double)random_1);
                weights[1] = (double)random_1 / ((double)random_0 + (double)random_1);
            }
            else if(numObjectives == 3) {
                int random_0 = state.random[0].nextInt(range);
                int random_1 = state.random[0].nextInt(range);
                int random_2 = state.random[0].nextInt(range);
                weights[0] = (double)random_0 / ((double)random_0 + (double)random_1 + (double)random_2);
                weights[1] = (double)random_1 / ((double)random_0 + (double)random_1 + (double)random_2);
                weights[2] = (double)random_2 / ((double)random_0 + (double)random_1 + (double)random_2);
            } else {
                throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
            }
            return weights;
        }

    /** Returns true if *first* is a better (fitter, whatever) individual than *second*. */
    public boolean betterThan(Individual first, Individual second, int subpopulation, EvolutionState state, int thread)
        {
        return first.fitness.betterThan(second.fitness);
        }

    public boolean betterThanBasedOnWeightedSum(Individual first, Individual second, int subpopulation, EvolutionState state, int thread, double[] newWeights)
        {
            return ((NSGA2MultiObjectiveFitnessNormalisationSimilarityChecking)first.fitness).betterThanBasedOnWeightedSum(second.fitness, newWeights);
        }
                
    public int produce(final int subpopulation,
        final EvolutionState state,
        final int thread)
        {
        // pick size random individuals, then pick the best.
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int best = getRandomIndividual(0, subpopulation, state, thread);
        
        int s = getTournamentSizeToUse(state.random[thread]);

        if(useWeightedSum){
            //added by Meng Xu 2023.06.19, compare based on weighted-sum objectives or based on rank and crowding distance of NSGPII
            double randomPro = state.random[0].nextDouble();
            if(randomPro <= probabilityOfUsingWeightedSum){
                int numObjectives = ((NSGA2MultiObjectiveFitnessNormalisationSimilarityChecking)oldinds[best].fitness).getNumObjectives();
                double[] newWeights = this.generateRandomWeights(state,numObjectives);
                if (pickWorst)
                    for (int x=1;x<s;x++)
                    {
                        int j = getRandomIndividual(x, subpopulation, state, thread);
                        if (!betterThanBasedOnWeightedSum(oldinds[j], oldinds[best], subpopulation, state, thread, newWeights))  // j is at least as bad as best
                            best = j;
                    }
                else
                    for (int x=1;x<s;x++)
                    {
                        int j = getRandomIndividual(x, subpopulation, state, thread);
                        if (betterThanBasedOnWeightedSum(oldinds[j], oldinds[best], subpopulation, state, thread, newWeights))  // j is better than best
                            best = j;
                    }
            }
            else{
                if (pickWorst)
                    for (int x=1;x<s;x++)
                    {
                        int j = getRandomIndividual(x, subpopulation, state, thread);
                        if (!betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is at least as bad as best
                            best = j;
                    }
                else
                    for (int x=1;x<s;x++)
                    {
                        int j = getRandomIndividual(x, subpopulation, state, thread);
                        if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
                            best = j;
                    }
            }
        }
        else{
            if (pickWorst)
                for (int x=1;x<s;x++)
                {
                    int j = getRandomIndividual(x, subpopulation, state, thread);
                    if (!betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is at least as bad as best
                        best = j;
                }
            else
                for (int x=1;x<s;x++)
                {
                    int j = getRandomIndividual(x, subpopulation, state, thread);
                    if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
                        best = j;
                }
        }


        //original
//        if (pickWorst)
//            for (int x=1;x<s;x++)
//                {
//                int j = getRandomIndividual(x, subpopulation, state, thread);
//                if (!betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is at least as bad as best
//                    best = j;
//                }
//        else
//            for (int x=1;x<s;x++)
//                {
//                int j = getRandomIndividual(x, subpopulation, state, thread);
//                if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
//                    best = j;
//                }


        return best;
        }

    // included for SteadyState
    public void individualReplaced(final SteadyStateEvolutionState state,
        final int subpopulation,
        final int thread,
        final int individual)
        { return; }
    
    public void sourcesAreProperForm(final SteadyStateEvolutionState state)
        { return; }
    
    }
