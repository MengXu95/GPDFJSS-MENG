package mengxu.algorithm.clusterselection;

import ec.EvolutionState;
import ec.Individual;
import ec.SelectionMethod;
import ec.select.SelectDefaults;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.GPRuleEvolutionStateCluster;
import mengxu.cluster.CustomerPointPermutation;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: mengxu  2021.05.07
 */

public class ClusterSelection extends SelectionMethod implements SteadyStateBSourceForm {

    /** default base */
    public static final String P_CLUSTER = "cluster";


    public static final String P_SIZE = "size";

    /** Base size of the tournament; this may change.  */
    int size;

    /** modified by mengxu 2021.06.25 Base size of the cluster tournament; this may change.  */
    public static final String P_CLUSTER_SIZE_TS = "cluster-size-TS";
    int clusterSizeTS;

    public static final String P_CLUSTER_WITH_TOURNAMENT = "cluster-with-tournament";
    boolean clusterWithTournament;

    /** Probablity of picking the size plus one */
    public double probabilityOfPickingSizePlusOne;

    private List<CustomerPointPermutation> currentClusterOne;
    private List<CustomerPointPermutation> currentClusterTwo;

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

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
        clusterSizeTS = state.parameters.getIntWithDefault(
                new Parameter(P_CLUSTER_SIZE_TS), null, 1);
        clusterWithTournament = state.parameters.getBoolean(new Parameter(P_CLUSTER_WITH_TOURNAMENT), false);


    }

    /** Returns a tournament size to use, at random, based on base size and probability of picking the size plus one. */
    public int getTournamentSizeToUse(MersenneTwisterFast random)
    {
        double p = probabilityOfPickingSizePlusOne;   // pulls us to under 35 bytes
        if (p == 0.0) return size;
        return size + (random.nextBoolean(p) ? 1 : 0);
    }

//    public int getRandomIndividual(List<CustomerPoint> subCluster , int subpopulation, EvolutionState state, int thread)
//    {
//        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
//        int subClusterSize = subCluster.size();
//        int clusterIndex = state.random[thread].nextInt(subClusterSize);
//        int indIndex = subCluster.get(clusterIndex).getIndIndex();
//        return indIndex;
//    }

    public int getRandomIndividual(List<CustomerPointPermutation> subCluster , int subpopulation, EvolutionState state, int thread)
    {
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int subClusterSize = subCluster.size();
        int clusterIndex = state.random[thread].nextInt(subClusterSize);
        int indIndex = subCluster.get(clusterIndex).getIndIndex();
        return indIndex;
    }

    public int getRandomIndividualFromPop(int number, int subpopulation, EvolutionState state, int thread)
    {
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        return state.random[thread].nextInt(oldinds.length);
    }

    /** Returns true if *first* is a better (fitter, whatever) individual than *second*. */
    public boolean betterThan(Individual first, Individual second, int subpopulation, EvolutionState state, int thread)
    {
        return first.fitness.betterThan(second.fitness);
    }

//    @Override
//    public int produce(final int subpopulation,
//                       final EvolutionState state,
//                       final int thread)
//    {
//        return -1;
//    }

    @Override //this is just for crossover
    public int[] produceTwo(final int subpopulation,
                                   final EvolutionState state,
                                   final int thread)
    {
//        boolean ref = state.random[thread].nextBoolean();
//        if(((GPRuleEvolutionStateCluster)state).currentPCDiversity < 0.25){
//            return produceClusterSelection(subpopulation,state,thread);
//        }
//        if(((GPRuleEvolutionStateCluster)state).generation >= 10){
//            return produceClusterSelection(subpopulation,state,thread);
//        }
//        else{
//            int[] indexs = new int[2];
//            indexs[0] = produceTournament(subpopulation,state,thread);
//            indexs[1] = produceTournament(subpopulation,state,thread);
//            return indexs;
//        }
        return produceClusterSelection(subpopulation,state,thread);
    }

    public int[] produceClusterSelection(final int subpopulation,
                                         final EvolutionState state,
                                         final int thread){
        //pick random cluster
        int clusterIndexOne = -1;
        int clusterIndexTwo = -1;
        List<CustomerPointPermutation> subClusterOne = new ArrayList<>();
        List<CustomerPointPermutation> subClusterTwo = new ArrayList<>();
        if(state instanceof GPRuleEvolutionStateCluster){
            int clusterSize = ((GPRuleEvolutionStateCluster) state).getCluster().size();
//            List<List<CustomerPoint>> cluster = ((GPRuleEvolutionStateCluster) state).getCluster();
            List<Double> clusterMeanFitness = ((GPRuleEvolutionStateCluster) state).getClusterMeanFitness();

            //modified by mengxu 2021.06.23 use the idea of tournament selection to select cluster
            clusterIndexOne = state.random[thread].nextInt(clusterSize);
//            System.out.println("clusterSizeTS: " + clusterSizeTS);
            for(int i=1; i<clusterSize/clusterSizeTS; i++){
                int randomClusterIndex = state.random[thread].nextInt(clusterSize);
                if(clusterMeanFitness.get(clusterIndexOne) > clusterMeanFitness.get(randomClusterIndex)){
                    clusterIndexOne = randomClusterIndex;
                }
            }
            clusterIndexTwo = state.random[thread].nextInt(clusterSize);
            for(int i=1; i<clusterSize/clusterSizeTS; i++){
                int randomClusterIndex = state.random[thread].nextInt(clusterSize);
                if(clusterMeanFitness.get(clusterIndexTwo) > clusterMeanFitness.get(randomClusterIndex)){
                    clusterIndexTwo = randomClusterIndex;
                }
            }
            subClusterOne = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexOne);
            subClusterTwo = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexTwo);
            //-------------------------------------------------------------------

            //original
//            if(clusterSize>1){
//                clusterIndexOne = state.random[thread].nextInt(clusterSize);
//                clusterIndexTwo = state.random[thread].nextInt(clusterSize-clusterIndexOne);//todo: need modify to random select two different cluster
//                subClusterOne = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexOne);
//                subClusterTwo = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexTwo);
//            }
//            else{
//                clusterIndexOne = state.random[thread].nextInt(clusterSize);
//                clusterIndexTwo = clusterIndexOne;//todo: need modify to random select two different cluster
//                subClusterOne = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexOne);
//                subClusterTwo = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexTwo);
//            }

        }
        // pick size random individuals, then pick the best from this cluster.
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;

        int bestOne = getRandomIndividual(subClusterOne, subpopulation, state, thread);
        int bestTwo = getRandomIndividual(subClusterTwo, subpopulation, state, thread);
        if(clusterWithTournament){
            int s = getTournamentSizeToUse(state.random[thread]);
//            System.out.println("s: " + s);
            for (int x=1;x<s;x++)
            {
                int j = getRandomIndividual(subClusterOne, subpopulation, state, thread);
                if (betterThan(oldinds[j], oldinds[bestOne], subpopulation, state, thread))  // j is better than best
                    bestOne = j;
            }

            for (int x=1;x<s;x++)
            {
                int j = getRandomIndividual(subClusterTwo, subpopulation, state, thread);
                if (betterThan(oldinds[j], oldinds[bestTwo], subpopulation, state, thread))  // j is better than best
                    bestTwo = j;
            }
        }



//        //add by mengxu 2021.12.28 to calculate the pearson score for parents pair
//        double score = calculatePearsonScoreForParents(subClusterOne, bestOne, subClusterTwo, bestTwo);
//        if(state instanceof GPRuleEvolutionStateCluster){
//            ((GPRuleEvolutionStateCluster)state).parentIndex.add(bestOne);
//            ((GPRuleEvolutionStateCluster)state).parentIndex.add(bestTwo);
//            if(((GPRuleEvolutionStateCluster) state).parentsPearsonScore.size() == state.generation){
//                List<Double> genPearsonScore = new ArrayList<>();
//                genPearsonScore.add(score);
//                ((GPRuleEvolutionStateCluster) state).parentsPearsonScore.add(genPearsonScore);
//            }
//            else{
//                ((GPRuleEvolutionStateCluster) state).parentsPearsonScore.get(state.generation).add(score);
//            }
//
//        }

        this.currentClusterOne = subClusterOne;
        this.currentClusterTwo = subClusterTwo;
        int[] best = new int[2];
        best[0] = bestOne;
        best[1] = bestTwo;
        return best;
    }

    @Override
    public int produce(final int subpopulation,
                                 final EvolutionState state,
                                 final int thread)
    {
        // pick size random individuals, then pick the best.
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int best = getRandomIndividualFromPop(0, subpopulation, state, thread);

        int s = getTournamentSizeToUse(state.random[thread]);


        for (int x=1;x<s;x++)
        {
            int j = getRandomIndividualFromPop(x, subpopulation, state, thread);
            if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
                best = j;
        }

        return best;
    }

    public int produceTournament(final int subpopulation,
                                 final EvolutionState state,
                                 final int thread)
    {
        // pick size random individuals, then pick the best.
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int best = getRandomIndividualFromPop(0, subpopulation, state, thread);

        int s = getTournamentSizeToUse(state.random[thread]);


            for (int x=1;x<s;x++)
            {
                int j = getRandomIndividualFromPop(x, subpopulation, state, thread);
                if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
                    best = j;
            }

        return best;
    }

//    @Override //this is just for crossover
//    public int[] produceTwo(final int subpopulation,
//                            final EvolutionState state,
//                            final int thread)
//    {
//        //pick random cluster
//        int clusterIndexOne = -1;
//        int clusterIndexTwo = -1;
//        List<CustomerPoint> subClusterOne = new ArrayList<>();
//        List<CustomerPoint> subClusterTwo = new ArrayList<>();
//        if(state instanceof GPRuleEvolutionStateCluster){
//            int clusterSize = ((GPRuleEvolutionStateCluster) state).getCluster().size();
////            List<List<CustomerPoint>> cluster = ((GPRuleEvolutionStateCluster) state).getCluster();
//            List<Double> clusterMeanFitness = ((GPRuleEvolutionStateCluster) state).getClusterMeanFitness();
//
//            //modified by mengxu 2021.06.23 use the idea of tournament selection to select cluster
//            clusterIndexOne = state.random[thread].nextInt(clusterSize);
////            System.out.println("clusterSizeTS: " + clusterSizeTS);
//            for(int i=1; i<clusterSize/clusterSizeTS; i++){
//                int randomClusterIndex = state.random[thread].nextInt(clusterSize);
//                if(clusterMeanFitness.get(clusterIndexOne) > clusterMeanFitness.get(randomClusterIndex)){
//                    clusterIndexOne = randomClusterIndex;
//                }
//            }
//            clusterIndexTwo = state.random[thread].nextInt(clusterSize);
//            for(int i=1; i<clusterSize/clusterSizeTS; i++){
//                int randomClusterIndex = state.random[thread].nextInt(clusterSize);
//                if(clusterMeanFitness.get(clusterIndexTwo) > clusterMeanFitness.get(randomClusterIndex)){
//                    clusterIndexTwo = randomClusterIndex;
//                }
//            }
//            subClusterOne = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexOne);
//            subClusterTwo = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexTwo);
//            //-------------------------------------------------------------------
//
//            //original
////            if(clusterSize>1){
////                clusterIndexOne = state.random[thread].nextInt(clusterSize);
////                clusterIndexTwo = state.random[thread].nextInt(clusterSize-clusterIndexOne);//todo: need modify to random select two different cluster
////                subClusterOne = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexOne);
////                subClusterTwo = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexTwo);
////            }
////            else{
////                clusterIndexOne = state.random[thread].nextInt(clusterSize);
////                clusterIndexTwo = clusterIndexOne;//todo: need modify to random select two different cluster
////                subClusterOne = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexOne);
////                subClusterTwo = ((GPRuleEvolutionStateCluster) state).getCluster().get(clusterIndexTwo);
////            }
//
//        }
//        // pick size random individuals, then pick the best from this cluster.
//        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
//
//        int bestOne = getRandomIndividual(subClusterOne, subpopulation, state, thread);
//        int bestTwo = getRandomIndividual(subClusterTwo, subpopulation, state, thread);
//        if(clusterWithTournament){
//            int s = getTournamentSizeToUse(state.random[thread]);
////            System.out.println("s: " + s);
//            for (int x=1;x<s;x++)
//            {
//                int j = getRandomIndividual(subClusterOne, subpopulation, state, thread);
//                if (betterThan(oldinds[j], oldinds[bestOne], subpopulation, state, thread))  // j is better than best
//                    bestOne = j;
//            }
//
//            for (int x=1;x<s;x++)
//            {
//                int j = getRandomIndividual(subClusterTwo, subpopulation, state, thread);
//                if (betterThan(oldinds[j], oldinds[bestTwo], subpopulation, state, thread))  // j is better than best
//                    bestTwo = j;
//            }
//        }
//
//        //add by mengxu 2021.12.28 to calculate the pearson score for parents pair
//        double score = calculatePearsonScoreForParents(subClusterOne, bestOne, subClusterTwo, bestTwo);
//        if(state instanceof GPRuleEvolutionStateCluster){
//            ((GPRuleEvolutionStateCluster)state).parentIndex.add(bestOne);
//            ((GPRuleEvolutionStateCluster)state).parentIndex.add(bestTwo);
//            if(((GPRuleEvolutionStateCluster) state).parentsPearsonScore.size() == state.generation){
//                List<Double> genPearsonScore = new ArrayList<>();
//                genPearsonScore.add(score);
//                ((GPRuleEvolutionStateCluster) state).parentsPearsonScore.add(genPearsonScore);
//            }
//            else{
//                ((GPRuleEvolutionStateCluster) state).parentsPearsonScore.get(state.generation).add(score);
//            }
//
//        }
//
//        int[] best = new int[2];
//        best[0] = bestOne;
//        best[1] = bestTwo;
//        return best;
//    }


//    public double calculatePearsonScoreForParents(List<CustomerPoint> subClusterOne, int bestOne, List<CustomerPoint> subClusterTwo, int bestTwo){
//        double[] pointsForBestOne = new double[40];
//        double[] pointsForBestTwo = new double[40];
//        for(CustomerPoint cp: subClusterOne){
//            if(cp.getIndIndex() == bestOne){
//                pointsForBestOne = cp.getPoint();
//                break;
//            }
//        }
//        for(CustomerPoint cp: subClusterTwo){
//            if(cp.getIndIndex() == bestTwo){
//                pointsForBestTwo = cp.getPoint();
//                break;
//            }
//        }
//        double score = PearsonDemo.getPearsonCorrelationScore(pointsForBestOne,pointsForBestTwo);
//        return score;
//    }

    public double calculatePearsonScoreOnEachDecisionForParents(int bestOne, int bestTwo){
        int[][] pointsForBestOne = new int[40][7];
        int[][] pointsForBestTwo = new int[40][7];
        for(CustomerPointPermutation cp: this.currentClusterOne){
            if(cp.getIndIndex() == bestOne){
                pointsForBestOne = cp.getPoint();
                break;
            }
        }
        for(CustomerPointPermutation cp: this.currentClusterTwo){
            if(cp.getIndIndex() == bestTwo){
                pointsForBestTwo = cp.getPoint();
                break;
            }
        }

        double score = 0;
        for(int i=0; i<40; i++){
            score += 1-PearsonDemo.getPearsonCorrelationScore(pointsForBestOne[i],pointsForBestTwo[i]);
        }
        score = score/40;
        return score;
    }



    @Override
    public Parameter defaultBase() {
        return SelectDefaults.base().push(P_CLUSTER);
    }

    @Override
    public void individualReplaced(SteadyStateEvolutionState state, int subpopulation, int thread, int individual) {

    }

    @Override
    public void sourcesAreProperForm(SteadyStateEvolutionState state) {

    }
}
