/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package mengxu.algorithm.MAPElites.GridMapElites;

import ec.EvolutionState;
import ec.Individual;
import ec.select.SelectDefaults;
import ec.select.TournamentSelection;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.util.ArrayList;
import java.util.Collection;

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

public class ParentSelectionGridMAPElites extends TournamentSelection implements SteadyStateBSourceForm
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

    public boolean tournamentSizeDynamic;

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

        Parameter tournamentSizeDynamicParam = new Parameter("tournament-size-dynamic");
        this.tournamentSizeDynamic = state.parameters.getBoolean(tournamentSizeDynamicParam, false);
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

    public int getRandomIndividual(int number, int subpopulation, EvolutionState state, int thread, int numberCandidate)
        {
            return state.random[thread].nextInt(numberCandidate);
        }

    /** Returns true if *first* is a better (fitter, whatever) individual than *second*. */
    public boolean betterThan(Individual first, Individual second, int subpopulation, EvolutionState state, int thread)
        {
        return first.fitness.betterThan(second.fitness);
        }

    public boolean betterThanBasedOnFitAndGen(double fit_first, int fromGen_first, double fit_second, int fromGen_second,
                                              int subpopulation, EvolutionState state, int thread)
        {
            if(((GPRuleEvolutionStateGridMAPElites)state).reevaluateGridMap){
                return fit_first<fit_second;
            }
            double penalty_level = ((GPRuleEvolutionStateGridMAPElites) state).penalty_level;
            double fit_first_penalty = fit_first * Math.pow((1+penalty_level), (state.generation-fromGen_first));
            double fit_second_penalty = fit_second * Math.pow((1+penalty_level), (state.generation-fromGen_second));
            return fit_first_penalty<fit_second_penalty;
        }

        public int produce(final int subpopulation, final EvolutionState state, final int thread) {
            if (!((GPRuleEvolutionStateGridMAPElites) state).use_grid_map) {
                return selectFromPopulation(subpopulation, state, thread);
            }

            boolean parentsOnlyFromPop = ((GPRuleEvolutionStateGridMAPElites) state).parentsOnlyFromPop;
            boolean parentsOnlyFromGridMap = ((GPRuleEvolutionStateGridMAPElites) state).parentsOnlyFromGridMap;

            if (parentsOnlyFromPop || state.generation == 0) {
                return selectFromPopulation(subpopulation, state, thread);
            } else if (parentsOnlyFromGridMap) {
                if(this.tournamentSizeDynamic){
                    return selectFromGridMap(subpopulation, state, thread);
                }
                else{
                    return selectFromGridMapSubBatch(subpopulation, state, thread);
                }
//                return selectFromGridMap(subpopulation, state, thread);
            }

            System.out.println("Error in parent selection !!!");
            return -1;
        }

        private int selectFromPopulation(final int subpopulation, final EvolutionState state, final int thread) {
            Individual[] oldinds = state.population.subpops[subpopulation].individuals;
            int best = getRandomIndividual(0, subpopulation, state, thread);
            int s = getTournamentSizeToUse(state.random[thread]);

            for (int x = 1; x < s; x++) {
                int j = getRandomIndividual(x, subpopulation, state, thread);
                if ((pickWorst && !betterThan(oldinds[j], oldinds[best], subpopulation, state, thread)) ||
                        (!pickWorst && betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))) {
                    best = j;
                }
            }
            return best;
        }

        //Strategy new 2025.1.15 meng xu
        private int selectFromGridMapSubBatch(final int subpopulation, final EvolutionState state, final int thread) {
            ArrayList<ArrayList<Object>> allData = ((GPRuleEvolutionStateGridMAPElites) state).gridPCmap.getAllDataInGridMap();
            ArrayList<Object> allIndividuals = allData.get(0);
            ArrayList<Object> allFitnesses = allData.get(1);
            ArrayList<Object> allFromGens = allData.get(2);

            int numberAll = allIndividuals.size();
            int numberCandidate = state.population.subpops[0].individuals.length;
            ArrayList<Object> subBatchIndividuals = new ArrayList<>();

//            //Strategy 1
//            if (numberAll < numberCandidate) {
//                numberCandidate = numberAll;
//                subBatchIndividuals.addAll(allIndividuals);
//            }
//            else{
//                int[] perm = new int[numberAll];
//                MOEADUtils.randomPermutation(state,0,perm,numberAll);
//                for(int i=0; i<numberCandidate; i++){
//                    subBatchIndividuals.add(allIndividuals.get(perm[i]));
//                }
//            }

            //Strategy 2
            if (numberAll < numberCandidate) {
                return selectFromPopulation(subpopulation, state, thread);
            }
            else{
                int[] perm = new int[numberAll];
                MOEADUtils.randomPermutation(state,0,perm,numberAll);
                for(int i=0; i<numberCandidate; i++){
                    subBatchIndividuals.add(allIndividuals.get(perm[i]));
                }
            }

            int best = getRandomIndividual(0, subpopulation, state, thread, numberCandidate);
            int s = getTournamentSizeToUse(state.random[thread]);

            for (int x = 1; x < s; x++) {
                int j = getRandomIndividual(x, subpopulation, state, thread, numberCandidate);
                if ((pickWorst && !betterThanBasedOnFitAndGen(
                        (double) allFitnesses.get(j), (int) allFromGens.get(j),
                        (double) allFitnesses.get(best), (int) allFromGens.get(best),
                        subpopulation, state, thread)) ||
                        (!pickWorst && betterThanBasedOnFitAndGen(
                                (double) allFitnesses.get(j), (int) allFromGens.get(j),
                                (double) allFitnesses.get(best), (int) allFromGens.get(best),
                                subpopulation, state, thread))) {
                    best = j;
                }
            }
            return best;
        }

        //Strategy original
        private int selectFromGridMap(final int subpopulation, final EvolutionState state, final int thread) {
            ArrayList<ArrayList<Object>> allData = ((GPRuleEvolutionStateGridMAPElites) state).gridPCmap.getAllDataInGridMap();
            ArrayList<Object> allIndividuals = allData.get(0);
            ArrayList<Object> allFitnesses = allData.get(1);
            ArrayList<Object> allFromGens = allData.get(2);

            int numberCandidate = allIndividuals.size();
            int best = getRandomIndividual(0, subpopulation, state, thread, numberCandidate);
            int s = tournamentSizeDynamic
                    ? getTournamentSizeToUse(state.random[thread]) * numberCandidate / state.population.subpops[0].individuals.length
                    : getTournamentSizeToUse(state.random[thread]);

            double ref = state.random[0].nextDouble();
            if(ref < ((GPRuleEvolutionStateGridMAPElites) state).randomParentSelectionProbability){
                s=1; //add by mengxu 2025.1.3
            }

            for (int x = 1; x < s; x++) {
                int j = getRandomIndividual(x, subpopulation, state, thread, numberCandidate);
                if ((pickWorst && !betterThanBasedOnFitAndGen(
                        (double) allFitnesses.get(j), (int) allFromGens.get(j),
                        (double) allFitnesses.get(best), (int) allFromGens.get(best),
                        subpopulation, state, thread)) ||
                        (!pickWorst && betterThanBasedOnFitAndGen(
                                (double) allFitnesses.get(j), (int) allFromGens.get(j),
                                (double) allFitnesses.get(best), (int) allFromGens.get(best),
                                subpopulation, state, thread))) {
                    best = j;
                }
            }
            return best;
        }
                
//    public int produce(final int subpopulation,
//        final EvolutionState state,
//        final int thread)
//        {
//
//        if(((GPRuleEvolutionStateGridMAPElites)state).use_grid_map){
//            if(((GPRuleEvolutionStateGridMAPElites)state).parentsOnlyFromPop || state.generation == 0){
//                // pick size random individuals, then pick the best.
//                Individual[] oldinds = state.population.subpops[subpopulation].individuals;
//                int best = getRandomIndividual(0, subpopulation, state, thread);
//
//                //original
//                int s = getTournamentSizeToUse(state.random[thread]);
////        System.out.println("tournament size: " + s);
//
//                if (pickWorst)
//                    for (int x=1;x<s;x++)
//                    {
//                        int j = getRandomIndividual(x, subpopulation, state, thread);
//                        if (!betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is at least as bad as best
//                            best = j;
//                    }
//                else
//                    for (int x=1;x<s;x++)
//                    {
//                        int j = getRandomIndividual(x, subpopulation, state, thread);
//                        if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
//                            best = j;
//                    }
//                return best;
//            }
//            else if(((GPRuleEvolutionStateGridMAPElites)state).parentsOnlyFromGridMap){
//                // pick size random individuals, then pick the best.
//                ArrayList<ArrayList<Object>> allData = ((GPRuleEvolutionStateGridMAPElites)state).gridPCmap.getAllDataInGridMap();
////                ArrayList<Individual> allIndividualsInCVTMap = ((GPRuleEvolutionStateGridMAPElites)state).gridPCmap.getAllIndividualsInGridMap();
////                ArrayList<Double> allFitnessesInCVTMap = ((GPRuleEvolutionStateGridMAPElites)state).gridPCmap.getAllFitnessesInGridMap();
////                ArrayList<Integer> allFromGensInCVTMap = ((GPRuleEvolutionStateGridMAPElites)state).gridPCmap.getAllFromGenInGridMap();
//                ArrayList<Object> allIndividualsInCVTMap = allData.get(0);
//                ArrayList<Object> allFitnessesInCVTMap = allData.get(1);
//                ArrayList<Object> allFromGensInCVTMap = allData.get(2);
//                int numberCandidate = allIndividualsInCVTMap.size();
//                int best = getRandomIndividual(0, subpopulation, state, thread, numberCandidate);
//
////                //original
//                int s = getTournamentSizeToUse(state.random[thread]);
//                if(tournamentSizeDynamic){
//                    s = getTournamentSizeToUse(state.random[thread]) * numberCandidate / state.population.subpops[0].individuals.length;
//                }
//
////                int s = 1; //by mengxu 2024.11.21
//
//                if (pickWorst)
//                    for (int x=1;x<s;x++)
//                    {
//                        Individual indBest = null;
//                        Individual indCandidate = null;
//                        indBest = (Individual) allIndividualsInCVTMap.get(best);
//                        double indfit = (double)allFitnessesInCVTMap.get(best);
//                        int indfromGen = (int)allFromGensInCVTMap.get(best);
//
//                        int j = getRandomIndividual(x, subpopulation, state, thread, numberCandidate);
//                        indCandidate = (Individual)allIndividualsInCVTMap.get(j);
//                        double indCandidateFit = (double)allFitnessesInCVTMap.get(j);
//                        int indCandidatefromGen = (int)allFromGensInCVTMap.get(j);
//
//                        if(!betterThanBasedOnFitAndGen(indCandidateFit, indCandidatefromGen, indfit, indfromGen, subpopulation, state, thread)){
//                            best = j;
//                        }
////                        if(indCandidatefromGen>indfromGen){
////                            best = j;
////                        }
////                        else if (indCandidatefromGen==indfromGen) {  // j is at least as bad as best
////                            if(indCandidateFit>indfit) {  // j is at least as bad as best
////                                best = j;
////                            }
////                        }
//                    }
//                else
//                    for (int x=1;x<s;x++)
//                    {
//                        Individual indBest = null;
//                        Individual indCandidate = null;
//                        indBest = (Individual) allIndividualsInCVTMap.get(best);
//                        double indfit = (double)allFitnessesInCVTMap.get(best);
//                        int indfromGen = (int)allFromGensInCVTMap.get(best);
//
//                        int j = getRandomIndividual(x, subpopulation, state, thread, numberCandidate);
//                        indCandidate = (Individual) allIndividualsInCVTMap.get(j);
//                        double indCandidateFit = (double)allFitnessesInCVTMap.get(j);
//                        int indCandidatefromGen = (int)allFromGensInCVTMap.get(j);
//
//                        if(betterThanBasedOnFitAndGen(indCandidateFit, indCandidatefromGen, indfit, indfromGen, subpopulation, state, thread)){
//                            best = j;
//                        }
////                        if(indCandidatefromGen>indfromGen){
////                            best = j;
////                        }
////                        else if (indCandidatefromGen==indfromGen) {  // j is at least as bad as best
////                            if(indCandidateFit<indfit) {  // j is at least as bad as best
////                                best = j;
////                            }
////                        }
//                    }
//                return best;
//            }
//            else{
//                System.out.println("Error in parent selection !!!");
//                return -1;
//            }
//        }
//        else{
//            // pick size random individuals, then pick the best.
//            Individual[] oldinds = state.population.subpops[subpopulation].individuals;
//            int best = getRandomIndividual(0, subpopulation, state, thread);
//
//            //original
//            int s = getTournamentSizeToUse(state.random[thread]);
////        System.out.println("tournament size: " + s);
//
//            if (pickWorst)
//                for (int x=1;x<s;x++)
//                {
//                    int j = getRandomIndividual(x, subpopulation, state, thread);
//                    if (!betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is at least as bad as best
//                        best = j;
//                }
//            else
//                for (int x=1;x<s;x++)
//                {
//                    int j = getRandomIndividual(x, subpopulation, state, thread);
//                    if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
//                        best = j;
//                }
//            return best;
//        }
//        }

    // included for SteadyState
    public void individualReplaced(final SteadyStateEvolutionState state,
        final int subpopulation,
        final int thread,
        final int individual)
        { return; }
    
    public void sourcesAreProperForm(final SteadyStateEvolutionState state)
        { return; }
    
    }
