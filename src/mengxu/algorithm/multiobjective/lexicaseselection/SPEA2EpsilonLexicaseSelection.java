package mengxu.algorithm.multiobjective.lexicaseselection;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.spea2.SPEA2MultiObjectiveFitness;
import ec.multiobjective.spea2.SPEA2TournamentSelection;
import ec.select.SelectDefaults;
import ec.simple.SimpleBreeder;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.Parameter;
import yimei.jss.helper.PopulationUtils;

import java.util.ArrayList;
import java.util.List;

public class SPEA2EpsilonLexicaseSelection extends SPEA2TournamentSelection {
    /** default base */
    public static final String P_LEXICASE = "lexicase";

//    public static final String P_NUMCASE = "num-case";
//    public int numCase;

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

//        int numIns;
//        Parameter def = defaultBase();
//        numIns = state.parameters.getInt(new Parameter(P_NUMCASE), def.push(P_NUMCASE), 0);
//        if (numIns <= 0)
//            state.output.fatal("The number of cases must be an integer >= 1.", base.push(P_NUMCASE), def.push(P_NUMCASE));
//
//        this.numCase = numIns;

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

    public List<Integer> getAllIndividualInArchive(int subpopulation, EvolutionState state, int thread){
        int archiveSize = ((SimpleBreeder)(state.breeder)).numElites(state, subpopulation);
        int archiveStart = state.population.subpops[subpopulation].individuals.length - archiveSize;
        int popSIze = state.population.subpops[subpopulation].individuals.length;
        List<Integer> allIndividualInArchiveIndex = new ArrayList<>();
        for(int i=archiveStart; i<popSIze; i++){
            allIndividualInArchiveIndex.add(i);
        }
        return allIndividualInArchiveIndex;
    }

    @Override
    public int produce(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        List<Individual> allIndividuals = new ArrayList<>();
        List<Integer> allIndividualsIndex = getAllIndividualInArchive(subpopulation, state, thread);
        for(int i=0;i<allIndividualsIndex.size();i++){
            //modified by mengxu 2021.10.08
            //need to exclude the bad fitness, or will make error when calculate the epsilon.
            Individual ind = oldinds[allIndividualsIndex.get(i)];
            double[] multiFitness = ((SPEA2MultiObjectiveFitness)ind.fitness).objectives;
            if(multiFitness[0] >= Double.POSITIVE_INFINITY || multiFitness[0] >= Double.MAX_VALUE) {
                allIndividualsIndex.remove(i);
                i--;
            }else{
                allIndividuals.add(ind);
            }
            //todo: need to check if the above is right by run with debug! 2021.10.08
//            allIndividuals.add(oldinds[allIndividualsIndex.get(i)]);

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
            double bestFitness = ((SPEA2MultiObjectiveFitness)allIndividuals.get(indexBest).fitness).objectives[objectiveIndex];

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

    public double calculateEpsilon(List<Individual> allIndividuals, int i){
        List<Double> allFitness = new ArrayList<>();
        for(int ref=0; ref<allIndividuals.size(); ref++){
            double fit = ((SPEA2MultiObjectiveFitness)allIndividuals.get(ref).fitness).objectives[i];
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
