package mengxu.algorithm.diversepartnerselection;

import ec.EvolutionState;
import ec.Individual;
import ec.SelectionMethod;
import ec.select.SelectDefaults;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;

/**
 * Author: mengxu  2021.05.07
 */

public class DiversePartnerSelection extends SelectionMethod implements SteadyStateBSourceForm {

    /** default base */
    public static final String P_DPS = "dps";

    public static final String P_SIZE = "tournament-size";

    /** Base size of the tournament; this may change.  */
    int size;

    public static final String P_TRY_TIMES = "try-times";
    int tryTimes;

    /** modified by mengxu 2021.06.25 Base size of the cluster tournament; this may change.  */
//    public static final String P_CLUSTER_SIZE_TS = "cluster-size-TS";
//    int clusterSizeTS;
//
//    public static final String P_CLUSTER_WITH_TOURNAMENT = "cluster-with-tournament";
//    boolean clusterWithTournament;

    public static final String P_DYNAMIC_XOVER_PRO = "dynamic-xover-pro";
    private boolean xoverDynamic;

    public static final String P_XOVER_VALUE = "xover-value";
    public double XoverValue;

    /** Probablity of picking the size plus one */
    public double probabilityOfPickingSizePlusOne;

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        Parameter def = defaultBase();

        double val = state.parameters.getDouble(new Parameter(P_SIZE),null,1.0);
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
//        clusterSizeTS = state.parameters.getIntWithDefault(
//                new Parameter(P_CLUSTER_SIZE_TS), null, 1);
//        clusterWithTournament = state.parameters.getBoolean(new Parameter(P_CLUSTER_WITH_TOURNAMENT), false);

        tryTimes = state.parameters.getInt(new Parameter(P_TRY_TIMES),null,1);

        XoverValue = state.parameters.getDouble(new Parameter(P_XOVER_VALUE),null,0.00001);

        xoverDynamic = state.parameters.getBoolean(new Parameter(P_DYNAMIC_XOVER_PRO),null,false);
    }

    /** Returns a tournament size to use, at random, based on base size and probability of picking the size plus one. */
    public int getTournamentSizeToUse(MersenneTwisterFast random)
    {
        double p = probabilityOfPickingSizePlusOne;   // pulls us to under 35 bytes
        if (p == 0.0) return size;
        return size + (random.nextBoolean(p) ? 1 : 0);
    }

    public int getRandomIndividual(int number, int subpopulation, EvolutionState state, int thread)
    {
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int indIndex = state.random[thread].nextInt(oldinds.length);
        return indIndex;
    }

    /** Returns true if *first* is a better (fitter, whatever) individual than *second*. */
    public boolean betterThan(Individual first, Individual second, int subpopulation, EvolutionState state, int thread)
    {
        return first.fitness.betterThan(second.fitness);
    }

    /** Returns true if *first* is a better (fitter, whatever) individual than *second*. */
    public boolean betterThanBasedOnBinaryBring(Individual first, Individual second, int subpopulation, EvolutionState state, int thread)
    {
        return ((OneInstanceMultiCaseMultiObjectiveFitness)first.fitness).betterThanBasedOnBinaryBring(second.fitness);
    }

    @Override
    public int produce(final int subpopulation,
                       final EvolutionState state,
                       final int thread)
    {
        return -1;
    }

    public int produceTournament(final int subpopulation,
                                 final EvolutionState state,
                                 final int thread)
    {
        // pick size random individuals, then pick the best.
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int best = getRandomIndividual(0,subpopulation, state, thread);

        int s = getTournamentSizeToUse(state.random[thread]);

        for (int x=1;x<s;x++)
        {
            int j = getRandomIndividual(x, subpopulation, state, thread);
            if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
                best = j;
        }

        return best;
    }

    @Override //this is just for crossover
    public int[] produceTwo(final int subpopulation,
                                   final EvolutionState state,
                                   final int thread)
    {
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int recipientIndex = produceTournament(subpopulation, state, thread);
        int donorIndex = produceTournament(subpopulation, state, thread);
        if(betterThanBasedOnBinaryBring(oldinds[donorIndex], oldinds[recipientIndex], subpopulation, state, thread)){
            int ref = recipientIndex;
            recipientIndex = donorIndex;
            donorIndex = ref;
        }

        int times = 0;
        boolean canXover = false;
//        while(times < oldinds.length/20 && !canXover){
        while(times < tryTimes && !canXover){
            donorIndex = produceTournament(subpopulation, state, thread);
            canXover = getCanXover(oldinds, recipientIndex, donorIndex);
            if(canXover){
                break;
            }
            times++;
        }

        if(!canXover  && xoverDynamic){
            //decrease the xover rate by 1/popsize and increase mutation rate by 1/popsize
            //todo: need to modify this
            //todo: although this location change, when select xover/mutation/reproduction still not change!!!
//            state.setupResetProb(state,defaultBase());
            double originalXoverProb = state.population.subpops[0].species.pipe_prototype.sources[0].probability;
            double originalMutationProb = state.population.subpops[0].species.pipe_prototype.sources[1].probability - originalXoverProb;
            double originalReproducProb = state.population.subpops[0].species.pipe_prototype.sources[2].probability - originalMutationProb - originalXoverProb;
            //reset xover prob
            double newXoverProb = Math.max((originalXoverProb - XoverValue), 0);
//            double newXoverProb = Math.max((originalXoverProb - 1/((double)oldinds.length)), 0);
            state.population.subpops[0].species.pipe_prototype.sources[0].probability = newXoverProb;
            //reset mutation prob
            double newMutationProb = 1 - newXoverProb - originalReproducProb;
            state.population.subpops[0].species.pipe_prototype.sources[1].probability = newXoverProb + newMutationProb;
            //do not change reproduction prob

//            state.population.subpops[0].species.pipe_prototype.setProbability(state.population.subpops[0].species.pipe_prototype.sources[0],newXoverProb);
//            state.population.subpops[0].species.pipe_prototype.setProbability(state.population.subpops[0].species.pipe_prototype.sources[1],newXoverProb + newMutationProb);

//            System.out.println(state.population.subpops[0].species.pipe_prototype.sources[0].probability);
//            System.out.println(state.population.subpops[0].species.pipe_prototype.sources[1].probability);
//            System.out.println(state.population.subpops[0].species.pipe_prototype.sources[2].probability);
            // allow all zero probabilities

//            BreedingSource.setupProbabilities(state.population.subpops[0].species.pipe_prototype.sources);
//            BreedingSource.setupProbabilities(state.population.subpops[0].species.pipe_prototype.sources);

        }

        int[] best = new int[2];
        best[0] = recipientIndex;
        best[1] = donorIndex;
//        if(state instanceof GPRuleEvolutionStateDPS){
//            ((GPRuleEvolutionStateDPS)state).parentIndex.add(recipientIndex);
//            ((GPRuleEvolutionStateDPS)state).parentIndex.add(donorIndex);
//        }
        return best;
    }

    public boolean getCanXover(Individual[] oldinds, int recipientIndex, int donorIndex){
        Individual recipient = oldinds[recipientIndex];
        double[][] binaryBringRecipient = ((OneInstanceMultiCaseMultiObjectiveFitness)recipient.fitness).getMultiInstanceMultiObjectiveBinaryBring();
        Individual donor = oldinds[donorIndex];
        double[][] binaryBringDonor = ((OneInstanceMultiCaseMultiObjectiveFitness)donor.fitness).getMultiInstanceMultiObjectiveBinaryBring();


        double advantage = 0;
        double notB1 = 0;
        double B1 = 0;

        for(int i=0; i<binaryBringRecipient.length; i++){
            int bb0 = (int) binaryBringRecipient[i][0];
            int bb1 = (int) binaryBringDonor[i][0];

            if(bb0==0 && bb1==1){
                advantage++;
            }

            if(bb0==0){
                notB1++;
            }

            if(bb0==1){
                B1++;
            }

        }

        double alpha = advantage/notB1;
        double beta = 1 - B1/binaryBringRecipient.length;

        return alpha > beta;
    }



    @Override
    public Parameter defaultBase() {
        return SelectDefaults.base().push(P_DPS);
    }

    @Override
    public void individualReplaced(SteadyStateEvolutionState state, int subpopulation, int thread, int individual) {

    }

    @Override
    public void sourcesAreProperForm(SteadyStateEvolutionState state) {

    }
}
