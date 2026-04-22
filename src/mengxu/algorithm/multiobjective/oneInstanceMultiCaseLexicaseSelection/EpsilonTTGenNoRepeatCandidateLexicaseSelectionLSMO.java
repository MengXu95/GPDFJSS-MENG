package mengxu.algorithm.multiobjective.oneInstanceMultiCaseLexicaseSelection;

import ec.EvolutionState;
import ec.Individual;
import ec.SelectionMethod;
import ec.select.SelectDefaults;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import yimei.jss.helper.PopulationUtils;

import java.util.ArrayList;
import java.util.List;

public class EpsilonTTGenNoRepeatCandidateLexicaseSelectionLSMO extends SelectionMethod implements SteadyStateBSourceForm {

    /** default base */
    public static final String P_LEXICASE = "lexicase";

    public static final String P_NUMCASE = "num-case";
    public int numCase;
    public static final String P_PRO_LEXICASE = "pro-lexicase";
    public double proLexicase;
    public static final String P_RANDOM_PRO = "random-pro";
    public String randomPro;

    public static final String P_LEXICASE_BASED_ON_RANK_PLUS_SPARSITY = "lexicase-based-on-rank-plus-sparsity";
    public boolean lexicaseBasedOnRankPlusSparsity;

    public static final String P_ELITIST_SELECTION_RATE = "elitist-selection-rate";
    public double elitistSR;

//    public int genForUseLS = 5;

    /** tournament */
    /** default base */
    public static final String P_TOURNAMENT_SIZE = "tournament-size";

    public static final String P_CANDIDATE_SIZE = "candidate-size";

    public static final String P_PICKWORST = "pick-worst";

    /** size parameter */
//    public static final String P_SIZE = "size";

    /** Base size of the tournament; this may change.  */
    int tournamentSize;

    int candidateSize;

    /** Probablity of picking the size plus one */
    public double probabilityOfPickingSizePlusOne;

    /** Do we pick the worst instead of the best? */
    public boolean pickWorst;

//    public Parameter defaultBase()
//    {
//        return SelectDefaults.base().push(P_TOURNAMENT);
//    }

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

//        int numIns;
        Parameter def = defaultBase();
//        numIns = state.parameters.getInt(new Parameter(P_NUMCASE), def.push(P_NUMCASE), 0);
//        if (numIns <= 0)
//            state.output.fatal("The number of cases must be an integer >= 1.", base.push(P_NUMCASE), def.push(P_NUMCASE));

        this.numCase = state.parameters.getInt(new Parameter(P_NUMCASE), null, 1);

        proLexicase = state.parameters.getDouble(new Parameter(P_PRO_LEXICASE), null, 0);
        randomPro = state.parameters.getStringWithDefault(new Parameter(P_RANDOM_PRO), null, "no");


        //tournament
        double val = state.parameters.getDouble(new Parameter(P_TOURNAMENT_SIZE),null,1.0);
        if (val < 1.0)
            state.output.fatal("Tournament size must be >= 1.");
        else if (val == (int) val)  // easy, it's just an integer
        {
            tournamentSize = (int) val;
            probabilityOfPickingSizePlusOne = 0.0;
        }
        else
        {
            tournamentSize = (int) Math.floor(val);
            probabilityOfPickingSizePlusOne = val - tournamentSize;  // for example, if we have 5.4, then the probability of picking *6* is 0.4
        }

        //candidate for LS
        candidateSize = state.parameters.getInt(new Parameter(P_CANDIDATE_SIZE),null,1);

        pickWorst = state.parameters.getBoolean(base.push(P_PICKWORST),def.push(P_PICKWORST),false);

        elitistSR = state.parameters.getDoubleWithDefault(new Parameter(P_ELITIST_SELECTION_RATE),null,1.0);

        lexicaseBasedOnRankPlusSparsity = state.parameters.getBoolean(new Parameter(P_LEXICASE_BASED_ON_RANK_PLUS_SPARSITY),null,false);

    }

    //todo: maybe can be modified!
    public List<Integer> getRandomSequenceInstance(EvolutionState state, int thread){
        List<Integer> Instance = new ArrayList<>();
        for(int i=0;i<this.numCase;i++){
            Instance.add(i);
        }
        List<Integer> randomInstance = new ArrayList<>();
        while(!Instance.isEmpty()){
            int index = state.random[thread].nextInt(Instance.size());
            randomInstance.add(Instance.get(index));
            Instance.remove(index);
        }

////        test
//        for(int i=0; i<randomInstance.size();i++){
//            System.out.print(randomInstance.get(i)+", ");
//        }
//        System.out.println();

        return randomInstance;
    }

    @Override
    public int produce(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        if(state.generation>((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).genForUseLS){
//            System.out.println("Tournament selection index: " + produceTournament(subpopulation, state, thread));
//            int index = produceLS(subpopulation, state, thread);
            int index = produceLSBasedOnRankAndSparsity(subpopulation, state, thread);
            if(state instanceof GPRuleEvolutionStateOneInstanceMultiCaseLSMO){
                double indexFitness = state.population.subpops[0].individuals[index].fitness.fitness();
//                ((GPRuleEvolutionStateMV0)state).parentIndex.add(index);
                ((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).parentFitness.add(indexFitness);//add 2021.12.09
            }
            return index;
        }
        else{
//            System.out.println("Epsilon selection index: " + produceLS(subpopulation, state, thread));
            int index = produceTournament(subpopulation, state, thread);
            if(state instanceof GPRuleEvolutionStateOneInstanceMultiCaseLSMO){
                double indexFitness = state.population.subpops[0].individuals[index].fitness.fitness();
//                ((GPRuleEvolutionStateMV0)state).parentIndex.add(index);
                ((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).parentFitness.add(indexFitness);//add 2021.12.09
            }
            return index;
        }
    }


    //add 2021.11.19
    int bestIndexOfIndividual(List<Individual> individuals, List<Integer> individualIndex){
        int index = individualIndex.get(0);
        Individual bestIndividual = individuals.get(0);
        for(int i=1; i<individuals.size(); i++){
            if(individuals.get(i).fitness.betterThan(bestIndividual.fitness)){
                index = individualIndex.get(i);
                //modified by mengxu 2021.11.29
                bestIndividual = individuals.get(i);
            }
        }
        return index;
    }

    int bestIndexOfIndividualBasedOnWholeRankAndSparsity(List<Individual> individuals, List<Integer> individualIndex){
        int index = individualIndex.get(0);
        Individual bestIndividual = individuals.get(0);
        for(int i=1; i<individuals.size(); i++){
            if(individuals.get(i).fitness.betterThan(bestIndividual.fitness)){
                index = individualIndex.get(i);
                //modified by mengxu 2021.11.29
                bestIndividual = individuals.get(i);
            }
        }
        return index;
    }


    public int produceLSBasedOnRankAndSparsity(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        List<Individual> allIndividuals = new ArrayList<>();
        List<Integer> allIndividualsIndex = getCandidates(subpopulation, state, thread);
        for(int i=0;i<allIndividualsIndex.size();i++){
            allIndividuals.add(oldinds[allIndividualsIndex.get(i)]);
//            allIndividualsIndex.add(i);
            //test
//            System.out.print("individual " + i + ": [");
//            for(int f=0;f<5000;f++){
//                System.out.print(((MultiInstanceMultiObjectiveFitness)oldinds[i].fitness).getFitness(f,0) + ", ");
//            }
//            System.out.print("]");
        }

        List<Integer> randomInstanceIndex = this.getRandomSequenceInstance(state,thread);
        for(int i=0;i<randomInstanceIndex.size();i++){
            if(allIndividuals.size() == 1){
//                System.out.println("epsilon selection index: " + allIndividualsIndex.get(0));
                return allIndividualsIndex.get(0);
            }

            int instanceIndex = randomInstanceIndex.get(i);

            //add 2021.11.16-------------------------------
            if(state instanceof GPRuleEvolutionStateOneInstanceMultiCaseLSMO){
                double[] caseNumber = ((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).allGenerationCaseNumber.get(state.generation-((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).genForUseLS-1);
                caseNumber[instanceIndex]++;
                ((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).allGenerationCaseNumber.set(state.generation-((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).genForUseLS-1, caseNumber);
            }
            //---------------------------------------------

            //modified by mengxu 2023.04.24
            double bestRank = Double.MAX_VALUE;
            if(this.lexicaseBasedOnRankPlusSparsity){
                int indexBest = PopulationUtils.getIndexOfbestIndsInstanceBasedOnRankPlusSparsity(allIndividuals,instanceIndex);
                double rank = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(indexBest).fitness).multiCaseRank[instanceIndex];
                double sparsity = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(indexBest).fitness).multiCaseSparsity[instanceIndex];
                bestRank = rank + sparsity;
            }
            else{
                int indexBest = PopulationUtils.getIndexOfbestIndsInstanceBasedOnRank(allIndividuals,instanceIndex);
                bestRank = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(indexBest).fitness).multiCaseRank[instanceIndex];
            }
            //original
//            int indexBest = PopulationUtils.getIndexOfbestIndsInstanceBasedOnRank(allIndividuals,instanceIndex);
//            double bestRank = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(indexBest).fitness).multiCaseRank[instanceIndex];

            //calculate the epsilon------------------------
            //modified by mengxu 2023.04.24
            double epsilon = 0;
            if(this.lexicaseBasedOnRankPlusSparsity){
                epsilon = calculateEpsilonBasedOnRankPlusSparsity(allIndividuals,instanceIndex);
            }
            else{
                epsilon = calculateEpsilonBasedOnRank(allIndividuals,instanceIndex);
            }
            //original
//            double epsilon = calculateEpsilonBasedOnRank(allIndividuals,instanceIndex);
//            System.out.println("Epsilon: " + epsilon);

            int j=0;
            while(j<allIndividuals.size()){
                if(allIndividuals.size() == 1){
//                    System.out.println("Number case used: " + i);
                    return allIndividualsIndex.get(0);
                }

                double rank = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(j).fitness).multiCaseRank[instanceIndex];
                double sparsity = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(j).fitness).multiCaseSparsity[instanceIndex];
                double currentRank = rank;
                if(this.lexicaseBasedOnRankPlusSparsity){
                    currentRank = rank + sparsity;
                }
                if(currentRank > bestRank + epsilon){
                    allIndividualsIndex.remove(j);
                    allIndividuals.remove(j);
                }
                else{
                    j++;
                }
            }
        }

        //modified by mengxu 2021.11.19
        //modify this part by adding a strategy to select individual based on the number of best case of fitness.
//        int index = bestIndexOfIndividual(allIndividuals, allIndividualsIndex);
//        System.out.println("size: " + allIndividuals.size());
//        if(state instanceof GPRuleEvolutionStateMV0){
//            ((GPRuleEvolutionStateMV0)state).parentIndex.add(index);
//        }
//        return index;
        //based on the whole Sparsity added by mengxu 2023.04.07
//        System.out.println("size: " + allIndividuals.size());
//        System.out.println("Use all cases!");
        int index = bestIndexOfIndividualBasedOnWholeRankAndSparsity(allIndividuals, allIndividualsIndex);
        return index;
//        int index = state.random[thread].nextInt(allIndividuals.size());
//        System.out.println("size: " + allIndividuals.size());
//        System.out.println("epsilon selection index by random: " + allIndividualsIndex.get(index));
//        return allIndividualsIndex.get(index);
    }


    public int produceLS(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        List<Individual> allIndividuals = new ArrayList<>();
        List<Integer> allIndividualsIndex = getCandidates(subpopulation, state, thread);
        for(int i=0;i<allIndividualsIndex.size();i++){
            allIndividuals.add(oldinds[allIndividualsIndex.get(i)]);
//            allIndividualsIndex.add(i);
            //test
//            System.out.print("individual " + i + ": [");
//            for(int f=0;f<5000;f++){
//                System.out.print(((MultiInstanceMultiObjectiveFitness)oldinds[i].fitness).getFitness(f,0) + ", ");
//            }
//            System.out.print("]");
        }

        List<Integer> randomInstanceIndex = this.getRandomSequenceInstance(state,thread);
        for(int i=0;i<randomInstanceIndex.size();i++){
            if(allIndividuals.size() == 1){
//                System.out.println("epsilon selection index: " + allIndividualsIndex.get(0));
                return allIndividualsIndex.get(0);
            }

            int instanceIndex = randomInstanceIndex.get(i);

            //add 2021.11.16-------------------------------
            if(state instanceof GPRuleEvolutionStateOneInstanceMultiCaseLSMO){
                double[] caseNumber = ((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).allGenerationCaseNumber.get(state.generation-((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).genForUseLS-1);
                caseNumber[instanceIndex]++;
                ((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).allGenerationCaseNumber.set(state.generation-((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).genForUseLS-1, caseNumber);
            }
            //---------------------------------------------

            int indexBest = PopulationUtils.getIndexOfbestIndsInstance(allIndividuals,instanceIndex);
            double bestFitness = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(indexBest).fitness).getFitness(instanceIndex,0);

            //calculate the epsilon------------------------
            //todo: need to check if the location to calculate epsilon is right? 2021.10.14
            double epsilon = calculateEpsilon(allIndividuals,instanceIndex);
//            System.out.println("Epsilon: " + epsilon);

            int j=0;
            while(j<allIndividuals.size()){
                if(allIndividuals.size() == 1){
//                    System.out.println("epsilon selection index: " + allIndividualsIndex.get(0));

                    return allIndividualsIndex.get(0);
                }
                if(((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(j).fitness).getFitness(instanceIndex,0)>bestFitness + epsilon){
                    allIndividualsIndex.remove(j);
                    allIndividuals.remove(j);
                }
                else{
                    j++;
                }
            }
        }

        //modified by mengxu 2021.11.19
        //modify this part by adding a strategy to select individual based on the number of best case of fitness.
//        int index = bestIndexOfIndividual(allIndividuals, allIndividualsIndex);
//        System.out.println("size: " + allIndividuals.size());
//        if(state instanceof GPRuleEvolutionStateMV0){
//            ((GPRuleEvolutionStateMV0)state).parentIndex.add(index);
//        }
//        return index;

        int index = state.random[thread].nextInt(allIndividuals.size());
//        System.out.println("size: " + allIndividuals.size());
//        System.out.println("epsilon selection index by random: " + allIndividualsIndex.get(index));
        return allIndividualsIndex.get(index);
    }

    public int produceTournament(final int subpopulation,
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

        return best;
    }

    public double calculateEpsilon(List<Individual> allIndividuals, int i){
        List<Double> allFitness = new ArrayList<>();
        for(int ref=0; ref<allIndividuals.size(); ref++){
            double fit = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(ref).fitness).getFitness(i,0);
            allFitness.add(fit);
        }
        allFitness.sort(Double::compareTo);

//        //test
//        System.out.println("Fit sort: ");
//        for(double fit:allFitness){
//            System.out.print(fit + ", ");
//        }
//        System.out.println();

        double median;
        if(allFitness.size() % 2 == 0){
            int indexMedian = allFitness.size() / 2;
            median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
        }
        else{
            int indexMedian = allFitness.size() / 2;
            median = allFitness.get(indexMedian);
        }
        for(int ref2=0; ref2<allFitness.size(); ref2++){
            allFitness.set(ref2, Math.abs(allFitness.get(ref2) - median));
        }
        allFitness.sort(Double::compareTo);
        if(allFitness.size() % 2 == 0){
            int indexMedian = allFitness.size() / 2;
            median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
        }
        else{
            int indexMedian = allFitness.size() / 2;
            median = allFitness.get(indexMedian);
        }
        return median;
    }

    public double calculateEpsilonBasedOnRank(List<Individual> allIndividuals, int i){
        List<Double> allFitness = new ArrayList<>();
        for(int ref=0; ref<allIndividuals.size(); ref++){
            double fit = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(ref).fitness).multiCaseRank[i];
            allFitness.add(fit);
        }
        allFitness.sort(Double::compareTo);

//        //test
//        System.out.println("Fit sort: ");
//        for(double fit:allFitness){
//            System.out.print(fit + ", ");
//        }
//        System.out.println();

        double median;
        if(allFitness.size() % 2 == 0){
            int indexMedian = allFitness.size() / 2;
            median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
        }
        else{
            int indexMedian = allFitness.size() / 2;
            median = allFitness.get(indexMedian);
        }
        for(int ref2=0; ref2<allFitness.size(); ref2++){
            allFitness.set(ref2, Math.abs(allFitness.get(ref2) - median));
        }
        allFitness.sort(Double::compareTo);
        if(allFitness.size() % 2 == 0){
            int indexMedian = allFitness.size() / 2;
            median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
        }
        else{
            int indexMedian = allFitness.size() / 2;
            median = allFitness.get(indexMedian);
        }
        return median;
    }

    public double calculateEpsilonBasedOnRankPlusSparsity(List<Individual> allIndividuals, int i){
        List<Double> allFitness = new ArrayList<>();
        for(int ref=0; ref<allIndividuals.size(); ref++){
            double fit = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(ref).fitness).multiCaseRank[i] +
                    ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)allIndividuals.get(ref).fitness).multiCaseSparsity[i];
            allFitness.add(fit);
        }
        allFitness.sort(Double::compareTo);

        double median;
        if(allFitness.size() % 2 == 0){
            int indexMedian = allFitness.size() / 2;
            median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
        }
        else{
            int indexMedian = allFitness.size() / 2;
            median = allFitness.get(indexMedian);
        }
        for(int ref2=0; ref2<allFitness.size(); ref2++){
            allFitness.set(ref2, Math.abs(allFitness.get(ref2) - median));
        }
        allFitness.sort(Double::compareTo);
        if(allFitness.size() % 2 == 0){
            int indexMedian = allFitness.size() / 2;
            median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
        }
        else{
            int indexMedian = allFitness.size() / 2;
            median = allFitness.get(indexMedian);
        }
        return median;
    }



    public double calculateEpsilonNormalisation(List<double[][]> allIndividualsNormalisation, int i){
        List<Double> allFitness = new ArrayList<>();
        for(int ref=0; ref<allIndividualsNormalisation.size(); ref++){
            double fit = allIndividualsNormalisation.get(ref)[i][0];
            allFitness.add(fit);
        }
        allFitness.sort(Double::compareTo);

//        //test
//        System.out.println("Fit sort: ");
//        for(double fit:allFitness){
//            System.out.print(fit + ", ");
//        }
//        System.out.println();

        double median;
        if(allFitness.size() % 2 == 0){
            int indexMedian = allFitness.size() / 2;
            median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
        }
        else{
            int indexMedian = allFitness.size() / 2;
            median = allFitness.get(indexMedian);
        }
        for(int ref2=0; ref2<allFitness.size(); ref2++){
            allFitness.set(ref2, Math.abs(allFitness.get(ref2) - median));
        }
        allFitness.sort(Double::compareTo);
        if(allFitness.size() % 2 == 0){
            int indexMedian = allFitness.size() / 2;
            median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
        }
        else{
            int indexMedian = allFitness.size() / 2;
            median = allFitness.get(indexMedian);
        }
        return median;
    }


    /** Returns a tournament size to use, at random, based on base size and probability of picking the size plus one. */
    public int getTournamentSizeToUse(MersenneTwisterFast random)
    {
        double p = probabilityOfPickingSizePlusOne;   // pulls us to under 35 bytes
        if (p == 0.0) return tournamentSize;
        return tournamentSize + (random.nextBoolean(p) ? 1 : 0);
    }

    public int getCandidateSizeToUse(MersenneTwisterFast random)
    {
        return candidateSize;
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
        return first.fitness.betterThan(second.fitness);
    }

    public List<Integer> getCandidates(final int subpopulation,
                                           final EvolutionState state,
                                           final int thread)
    {
        int populationSize = state.population.subpops[0].individuals.length;

        int permSize = (int)(populationSize * elitistSR);
        int[] permutation = new int[permSize];
        MOEADUtils.randomPermutation(state, 0, permutation, permSize);

        // pick size random individuals, then pick the best.
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int best = getRandomIndividual(0, subpopulation, state, thread);

        int s = getCandidateSizeToUse(state.random[thread]);
//        System.out.println("tournament size for ELSMO:" + s);

        List<Integer> tourIndividuals = new ArrayList<>();

        for (int x=0;x<permSize;x++)
        {
            //modified by mengxu 2021.09.24
            //need to exclude the bad fitness, or will make error when calculate the epsilon.
            int j=permutation[x];
            Individual ind = state.population.subpops[0].individuals[j];
            double[][] multiCaseFitness = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)ind.fitness).multiInstanceMultiObjectiveFitness;
            if(multiCaseFitness[0][0] >= Double.POSITIVE_INFINITY || multiCaseFitness[0][0] >= Double.MAX_VALUE) {
                continue;
            }else{
                tourIndividuals.add(j);
            }
            if(tourIndividuals.size() == s){
                return tourIndividuals;
            }
        }

        System.out.println("TourIndividuals' size has not arrive at the set size in advance.");
        return tourIndividuals;
    }

    @Override
    public Parameter defaultBase() {
        return SelectDefaults.base().push(P_LEXICASE);
    }

    @Override
    public void individualReplaced(SteadyStateEvolutionState state, int subpopulation, int thread, int individual) {

    }

    @Override
    public void sourcesAreProperForm(SteadyStateEvolutionState state) {

    }
}
