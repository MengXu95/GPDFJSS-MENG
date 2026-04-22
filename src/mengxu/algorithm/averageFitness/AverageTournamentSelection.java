/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package mengxu.algorithm.averageFitness;

import ec.EvolutionState;
import ec.Individual;
import ec.SelectionMethod;
import ec.select.SelectDefaults;
import ec.select.TournamentSelection;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.GPRuleEvolutionStateBaseline;
import mengxu.algorithm.lexicaseselection.GPRuleEvolutionStateOneInstanceMultiCase;
import mengxu.algorithm.multiobjective.GPRuleEvolutionStateMO;
import mengxu.algorithm.multiobjective.lexicaseselection.GPRuleEvolutionStateMOLS;

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

public class AverageTournamentSelection extends TournamentSelection
    {
    /** default base */
    public static final String P_TOURNAMENT = "tournament";

    public static final String P_PICKWORST = "pick-worst";

    public static final String P_WORKFOR = "work-for";
    String workFor;

    /** size parameter */
    public static final String P_SIZE = "size";

    /** Base size of the tournament; this may change.  */
    int size;

    /** Probablity of picking the size plus one */
    public double probabilityOfPickingSizePlusOne;
    
    /** Do we pick the worst instead of the best? */
    public boolean pickWorst;

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

        workFor = state.parameters.getStringWithDefault(new Parameter(P_WORKFOR), def.push(P_WORKFOR), "elitismAndParent");

        pickWorst = state.parameters.getBoolean(base.push(P_PICKWORST),def.push(P_PICKWORST),false);
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

    /** Returns true if *first* is a better (fitter, whatever) individual than *second*. */
    public boolean betterThan(Individual first, Individual second, int subpopulation, EvolutionState state, int thread)
        {
            if(this.workFor.equals("elitismAndParent") || this.workFor.equals("parent")){
                return ((AverageMultiCaseMultiObjectiveFitness)first.fitness).betterThanAverage(second.fitness);
            }
            else if(this.workFor.equals("elitism")){
                return ((AverageMultiCaseMultiObjectiveFitness)first.fitness).betterThanNormal(second.fitness);
            }
            else{
                System.out.println("error happen, AverageTournamentSelection!");
                return ((AverageMultiCaseMultiObjectiveFitness)first.fitness).betterThanNormal(second.fitness);
            }
        }

        public boolean betterThanNormal(Individual first, Individual second, int subpopulation, EvolutionState state, int thread)
        {
            return ((AverageMultiCaseMultiObjectiveFitness)first.fitness).betterThanNormal(second.fitness);
        }

        @Override
        public int produce(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
            if(state.generation>((GPRuleEvolutionStateAverage)state).genForUseLS){
//            System.out.println("Tournament selection index: " + produceTournament(subpopulation, state, thread));
                int index = produceAfter(subpopulation, state, thread);
                return index;
            }
            else{
//            System.out.println("Epsilon selection index: " + produceLS(subpopulation, state, thread));
                int index = produceBefore(subpopulation, state, thread);
                return index;
            }
        }

    public int produceAfter(final int subpopulation,
        final EvolutionState state,
        final int thread)
        {
        // pick size random individuals, then pick the best.
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int best = getRandomIndividual(0, subpopulation, state, thread);
        
        int s = getTournamentSizeToUse(state.random[thread]);
                
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

        //modified by mengxu 2021.06.10
        return best;
        }

        public int produceBefore(final int subpopulation,
                                final EvolutionState state,
                                final int thread)
        {
            // pick size random individuals, then pick the best.
            Individual[] oldinds = state.population.subpops[subpopulation].individuals;
            int best = getRandomIndividual(0, subpopulation, state, thread);

            int s = getTournamentSizeToUse(state.random[thread]);

            if (pickWorst)
                for (int x=1;x<s;x++)
                {
                    int j = getRandomIndividual(x, subpopulation, state, thread);
                    if (!betterThanNormal(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is at least as bad as best
                        best = j;
                }
            else
                for (int x=1;x<s;x++)
                {
                    int j = getRandomIndividual(x, subpopulation, state, thread);
                    if (betterThanNormal(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
                        best = j;
                }

            //modified by mengxu 2021.06.10
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
