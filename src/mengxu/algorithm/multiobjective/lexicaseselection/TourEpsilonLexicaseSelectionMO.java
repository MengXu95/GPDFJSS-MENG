package mengxu.algorithm.multiobjective.lexicaseselection;

import ec.EvolutionState;
import ec.Individual;
import ec.SelectionMethod;
import ec.multiobjective.MultiObjectiveFitness;
import ec.select.SelectDefaults;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import yimei.jss.helper.PopulationUtils;

import java.util.ArrayList;
import java.util.List;

public class TourEpsilonLexicaseSelectionMO extends SelectionMethod implements SteadyStateBSourceForm {

    /** default base */
    public static final String P_LEXICASE = "lexicase";

    /** tournament */
    /** default base */
    public static final String P_TOURNAMENT_SIZE = "tournament-size";

    public static final String P_PICKWORST = "pick-worst";

    /** size parameter */
//    public static final String P_SIZE = "size";

    /** Base size of the tournament; this may change.  */
    int size;

    /** Probablity of picking the size plus one */
    public double probabilityOfPickingSizePlusOne;

    /** Do we pick the worst instead of the best? */
    public boolean pickWorst;

//    public static final String P_NUMCASE = "num-case";
//    public int numCase;

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

//        int numIns;
        Parameter def = defaultBase();
//        numIns = state.parameters.getInt(new Parameter(P_NUMCASE), def.push(P_NUMCASE), 0);
//        if (numIns <= 0)
//            state.output.fatal("The number of cases must be an integer >= 1.", base.push(P_NUMCASE), def.push(P_NUMCASE));
//
//        this.numCase = numIns;

        //tournament
        double val = state.parameters.getDouble(new Parameter(P_TOURNAMENT_SIZE),null,1.0);
        if (val < 1.0)
            state.output.fatal("Tournament size must be >= 1.");
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


    }

    //todo: maybe can be modified!
    public List<Integer> getRandomSequenceInstance(EvolutionState state, int thread){
        List<Integer> Instance = new ArrayList<>();
        //modified by mengxu, 2021.05.31
        int num_objectives = ((MultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness).getNumObjectives();
        for(int i=0;i<num_objectives;i++){
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
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        List<Individual> allIndividuals = new ArrayList<>();
        List<Integer> allIndividualsIndex = produceTournament(subpopulation, state, thread);
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
                if(state instanceof GPRuleEvolutionStateMOLS){
                    ((GPRuleEvolutionStateMOLS)state).parentIndex.add(allIndividualsIndex.get(0));
                }
                return allIndividualsIndex.get(0);
            }

            int objectiveIndex = randomInstanceIndex.get(i);

            int indexBest = PopulationUtils.getIndexOfbestIndsObjective(allIndividuals,objectiveIndex);
            double bestFitness = ((MultiObjectiveFitness)allIndividuals.get(indexBest).fitness).objectives[objectiveIndex];

            //calculate the epsilon------------------------
            double epsilon = calculateEpsilon(allIndividuals,objectiveIndex);


            int j=0;
            while(j<allIndividuals.size()){
                if(allIndividuals.size() == 1){
//                    System.out.println("epsilon selection index: " + allIndividualsIndex.get(0));
                    if(state instanceof GPRuleEvolutionStateMOLS){
                        ((GPRuleEvolutionStateMOLS)state).parentIndex.add(allIndividualsIndex.get(0));
                    }
                    return allIndividualsIndex.get(0);
                }
                if(((MultiObjectiveFitness)allIndividuals.get(j).fitness).objectives[objectiveIndex]>bestFitness + epsilon){
                    allIndividualsIndex.remove(j);
                    allIndividuals.remove(j);
                }
                else{
                    j++;
                }
            }
        }
        int index = state.random[thread].nextInt(allIndividuals.size());
//        System.out.println("epsilon selection index: " + allIndividualsIndex.get(index));
        if(state instanceof GPRuleEvolutionStateMOLS){
            ((GPRuleEvolutionStateMOLS)state).parentIndex.add(allIndividualsIndex.get(index));
        }
        return allIndividualsIndex.get(index);
    }

//    @Override
//    public int produce(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
//        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
//        List<Individual> allIndividuals = new ArrayList<>();
//        List<Integer> allIndividualsIndex = produceTournament(subpopulation, state, thread);
//
//        List<double[]> oldindsNormalisation = ((GPRuleEvolutionStateMOLS)state).getNormalisationFitness();
//        List<double[]> allFitnessNormalisation = new ArrayList<>();
//
//        for(int i=0;i<allIndividualsIndex.size();i++){
//            allFitnessNormalisation.add(oldindsNormalisation.get(allIndividualsIndex.get(i)));
//            allIndividuals.add(oldinds[allIndividualsIndex.get(i)]);
////            allIndividualsIndex.add(i);
//            //test
////            System.out.print("individual " + i + ": [");
////            for(int f=0;f<5000;f++){
////                System.out.print(((MultiInstanceMultiObjectiveFitness)oldinds[i].fitness).getFitness(f,0) + ", ");
////            }
////            System.out.print("]");
//        }
//
//        List<Integer> randomInstanceIndex = this.getRandomSequenceInstance(state,thread);
//        for(int i=0;i<randomInstanceIndex.size();i++){
//            if(allIndividuals.size() == 1){
////                System.out.println("epsilon selection index: " + allIndividualsIndex.get(0));
//                if(state instanceof GPRuleEvolutionStateMOLS){
//                    ((GPRuleEvolutionStateMOLS)state).parentIndex.add(allIndividualsIndex.get(0));
//                }
//                return allIndividualsIndex.get(0);
//            }
//
//            int objectiveIndex = randomInstanceIndex.get(i);
//
//            int indexBest = PopulationUtils.getIndexOfbestIndsInstanceNormalisationMOLS(allFitnessNormalisation,objectiveIndex);
//            double bestFitness = allFitnessNormalisation.get(indexBest)[objectiveIndex];
//
//            //calculate the epsilon------------------------
//            double epsilon = calculateEpsilonNormalisation(allFitnessNormalisation,objectiveIndex);
//
//
//            int j=0;
//            while(j<allFitnessNormalisation.size()){
//                if(allFitnessNormalisation.size() == 1){
////                    System.out.println("epsilon selection index: " + allIndividualsIndex.get(0));
//                    if(state instanceof GPRuleEvolutionStateMOLS){
//                        ((GPRuleEvolutionStateMOLS)state).parentIndex.add(allIndividualsIndex.get(0));
//                    }
//                    return allIndividualsIndex.get(0);
//                }
//                if(allFitnessNormalisation.get(j)[objectiveIndex]>bestFitness + epsilon){
//                    allIndividualsIndex.remove(j);
//                    allFitnessNormalisation.remove(j);
//                }
//                else{
//                    j++;
//                }
//            }
//        }
//        int index = state.random[thread].nextInt(allFitnessNormalisation.size());
////        System.out.println("epsilon selection index: " + allIndividualsIndex.get(index));
//        if(state instanceof GPRuleEvolutionStateMOLS){
//            ((GPRuleEvolutionStateMOLS)state).parentIndex.add(allIndividualsIndex.get(index));
//        }
//        return allIndividualsIndex.get(index);
//    }

    public double calculateEpsilon(List<Individual> allIndividuals, int i){
        List<Double> allFitness = new ArrayList<>();
        for(int ref=0; ref<allIndividuals.size(); ref++){
            double fit = ((MultiObjectiveFitness)allIndividuals.get(ref).fitness).objectives[i];
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

    public double calculateEpsilonNormalisation(List<double[]> allIndividualsNormalisation, int i){
        List<Double> allFitness = new ArrayList<>();
        for(int ref=0; ref<allIndividualsNormalisation.size(); ref++){
            double fit = allIndividualsNormalisation.get(ref)[i];
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

//    /** Returns true if *first* is a better (fitter, whatever) individual than *second*. */
//    public boolean betterThan(Individual first, Individual second, int subpopulation, EvolutionState state, int thread)
//    {
//        return first.fitness.betterThan(second.fitness);
//    }

    public List<Integer> produceTournament(final int subpopulation,
                                 final EvolutionState state,
                                 final int thread)
    {
        // pick size random individuals, then pick the best.
        Individual[] oldinds = state.population.subpops[subpopulation].individuals;
        int best = getRandomIndividual(0, subpopulation, state, thread);

        int s = getTournamentSizeToUse(state.random[thread]);
//        System.out.println("tournament size for ELSMO:" + s);

        List<Integer> tourIndividuals = new ArrayList<>();

        if (pickWorst)
            for (int x=0;x<s;x++)
            {
                int j = getRandomIndividual(x, subpopulation, state, thread);
                tourIndividuals.add(j);
//                if (!betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is at least as bad as best
//                    best = j;
            }
        else
            for (int x=0;x<s;x++)
            {
                int j = getRandomIndividual(x, subpopulation, state, thread);
                Individual ind = state.population.subpops[0].individuals[j];
                double[] multiFitness = ((MultiObjectiveFitness)ind.fitness).objectives;
                if(multiFitness[0] >= Double.POSITIVE_INFINITY || multiFitness[0] >= Double.MAX_VALUE) {
                    x--;
                }else{
                    tourIndividuals.add(j);
                }


//                int j = getRandomIndividual(x, subpopulation, state, thread);
//                tourIndividuals.add(j);
//                if (betterThan(oldinds[j], oldinds[best], subpopulation, state, thread))  // j is better than best
//                    best = j;
            }
//            System.out.println("tournament size: " + tourIndividuals.size());

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
