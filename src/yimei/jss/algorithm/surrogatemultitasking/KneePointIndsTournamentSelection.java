package yimei.jss.algorithm.surrogatemultitasking;

import ec.EvolutionState;
import ec.Individual;
import ec.select.TournamentSelection;
import ec.util.Parameter;
import yimei.jss.algorithm.elbowSelectedFeatures.IndexPoint;
import yimei.jss.algorithm.multipletreegp.SameIndexCrossoverPipeline;
import yimei.jss.helper.PopulationUtils;

import java.util.ArrayList;

public class KneePointIndsTournamentSelection extends TournamentSelection {
    /** Produces the index of a (typically uniformly distributed) randomly chosen individual
     to fill the tournament.  <i>number</> is the position of the individual in the tournament.  */
    //fzhang 2019.11.19 save knee point individuals for tournament selection
    Individual[] oldinds = null;
    static ArrayList<Individual[]> oldindsTemp = new ArrayList<>();
    static int genForControlKneeInds = 0;//ignore calculate oldInds too many times
    static Boolean kneeInds = false;

    public final static String P_IND_PERCENT = "ind-Percent";
    public double indPercent;

    public int getRandomIndividual(int number, int subpopulation, EvolutionState state, int thread)

    {
/*        oldinds = getKneePointInds(state);
        if(genForControlKneeInds != state.generation){
            oldinds = getKneePointInds(state);
            genForControlKneeInds++;
        }*/
/*        if(SameIndexCrossoverPipeline.rnd > SameIndexCrossoverPipeline.transferprob){
            oldinds = state.population.subpops[subpopulation].individuals;
        }*/
/*        else{
            oldinds = state.population.subpops[subpopulation].individuals;
        }*/
        return state.random[thread].nextInt(oldinds.length);
    }

    public int produce(final int subpopulation,
                       final EvolutionState state,
                       final int thread)
    {
        // pick size random individuals, then pick the best.
        if(oldindsTemp.size() == 0){
            oldindsTemp = getKneePointInds(state);
            //oldindsTemp = getkNumInds(state);
        }

        if(genForControlKneeInds != state.generation){
            oldindsTemp = getKneePointInds(state);
            //oldindsTemp = getkNumInds(state);
            genForControlKneeInds++;
        }

        if (SameIndexCrossoverPipeline.rnd <= SameIndexCrossoverPipeline.transferprob) {
            //crossover between different subpopulations, use knee point to select individuals for tournament
            if (kneeInds == false) {
                oldinds = state.population.subpops[subpopulation].individuals;
                kneeInds = true;
            } else {
                oldinds = oldindsTemp.get(subpopulation);
                kneeInds = false;
            }
        }
        else {
            oldinds = state.population.subpops[subpopulation].individuals;
        }

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

        return best;
    }

    public ArrayList<Individual[]> getKneePointInds(final EvolutionState state){
        ArrayList<Individual[]> oldIndsSet = new ArrayList<>();
        PopulationUtils.sort(state.population);
        for (int subIndex = 0; subIndex < state.population.subpops.length; subIndex++) {
            Individual[] individuals = state.population.subpops[subIndex].individuals;
            ArrayList<IndexPoint> points = new ArrayList<IndexPoint>();

            for(int indIndex = 0; indIndex < state.population.subpops[subIndex].individuals.length; indIndex++){
                if(individuals[indIndex].fitness.fitness() != Double.POSITIVE_INFINITY & individuals[indIndex].fitness.fitness() != Double.MAX_VALUE)
                    points.add(new IndexPoint(new double[]{indIndex,individuals[indIndex].fitness.fitness()}));
            }
            IndexPoint p1 = points.get(0);  //the min Point
            IndexPoint p2 = points.get(points.size() - 1); //the max point

            //calculate the line factors
            double a = p2.position[1] - p1.position[1];
            double b = p1.position[0] - p2.position[0];
            double c = p2.position[0]*p1.position[1] - p1.position[0]*p2.position[1];
            double maxDistance = 0.0;
            int kneePoint = -1;

            for(int idxPoints = 1; idxPoints < points.size(); idxPoints++){
                IndexPoint p = points.get(idxPoints);
                double distance = Math.abs(a*p.position[0]+b*p.position[1]+c);
                if(distance > maxDistance){
                    maxDistance = distance;
                    kneePoint = idxPoints;
                }
            }
            oldinds = new Individual[kneePoint+1];
            System.arraycopy(individuals, 0, oldinds, 0, kneePoint+1);
            oldIndsSet.add(oldinds);
        }
        return oldIndsSet;
    }

    //fzhang 2019.11.22 get k% individuals
    public Individual[] getkNumInds(final EvolutionState state){
        PopulationUtils.sort(state.population);
        for (int subIndex = 0; subIndex < state.population.subpops.length; subIndex++) {
            Individual[] individuals = state.population.subpops[subIndex].individuals;
            indPercent = state.parameters.getDoubleWithDefault(new Parameter(P_IND_PERCENT), null, 1);
            int indNumForTransfer = (int)(state.population.subpops[subIndex].individuals.length * indPercent);
            oldinds = new Individual[indNumForTransfer];
            System.arraycopy(individuals, 0, oldinds, 0, indNumForTransfer);
        }
        return oldinds;
    }
}


