package mengxu.algorithm.lexicaseselection;

import ec.EvolutionState;
import ec.Individual;
import ec.SelectionMethod;
import ec.select.SelectDefaults;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import yimei.jss.helper.PopulationUtils;

import java.util.ArrayList;
import java.util.List;

public class TournamentTruncatedLexicaseSelection extends SelectionMethod implements SteadyStateBSourceForm {

    /** default base */
    public static final String P_LEXICASE = "lexicase";

    public static final String P_NUMCASE = "num-case";
    public int numCase;
    public static final String P_PRO_LEXICASE = "pro-lexicase";
    public double proLexicase;
    public static final String P_RANDOM_PRO = "random-pro";
    public String randomPro;


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
        if(randomPro.equals("yes")){
            if(state.random[thread].nextBoolean((double)state.generation/state.numGenerations)){
//            System.out.println("Tournament selection index: " + produceTournament(subpopulation, state, thread));
                return produceLS(subpopulation, state, thread);
            }
            else{
//            System.out.println("Epsilon selection index: " + produceLS(subpopulation, state, thread));
                return produceTournament(subpopulation, state, thread);
            }
        }
        else{
            if(state.random[thread].nextBoolean(proLexicase)){
//            System.out.println("Tournament selection index: " + produceTournament(subpopulation, state, thread));
                return produceLS(subpopulation, state, thread);
            }
            else{
//            System.out.println("Epsilon selection index: " + produceLS(subpopulation, state, thread));
                return produceTournament(subpopulation, state, thread);
            }
        }
    }

    public int produceLS(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        List<Individual> allIndividuals = new ArrayList<>();
        List<Integer> allIndividualsIndex = new ArrayList<>();
        for(int i=0;i<oldinds.length;i++){
            allIndividuals.add(oldinds[i]);
            allIndividualsIndex.add(i);
        }

        double percentTruncated = 0.25;
        //based on traditional LS, just set the percentTruncated = 0.25;
        //I think could also based on epsilon LS, random threshold LS, MADCAP epsilon LS

        List<Integer> randomInstanceIndex = this.getRandomSequenceInstance(state,thread);
        for(int i=0;i<randomInstanceIndex.size() * percentTruncated;i++){
            if(allIndividuals.size() == 1){
                return allIndividualsIndex.get(0);
            }

            int instanceIndex = randomInstanceIndex.get(i);
            int indexBest = PopulationUtils.getIndexOfbestIndsInstance(allIndividuals,instanceIndex);
            double bestFitness = ((OneInstanceMultiCaseMultiObjectiveFitness)allIndividuals.get(indexBest).fitness).getFitness(instanceIndex,0);
            int j=0;
            while(j<allIndividuals.size()){
                if(allIndividuals.size() == 1){
                    return allIndividualsIndex.get(0);
                }
                if(((OneInstanceMultiCaseMultiObjectiveFitness)allIndividuals.get(j).fitness).getFitness(instanceIndex,0)>bestFitness){
                    allIndividualsIndex.remove(j);
                    allIndividuals.remove(j);
                }
                else{
                    j++;
                }
            }
        }
        int index = state.random[thread].nextInt(allIndividuals.size());
        return allIndividualsIndex.get(index);
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
        return first.fitness.betterThan(second.fitness);
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
