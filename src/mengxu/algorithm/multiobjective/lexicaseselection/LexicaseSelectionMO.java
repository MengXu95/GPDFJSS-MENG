package mengxu.algorithm.multiobjective.lexicaseselection;

import ec.EvolutionState;
import ec.Individual;
import ec.SelectionMethod;
import ec.multiobjective.MultiObjectiveFitness;
import ec.select.SelectDefaults;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.Parameter;
import yimei.jss.helper.PopulationUtils;

import java.util.ArrayList;
import java.util.List;

public class LexicaseSelectionMO extends SelectionMethod implements SteadyStateBSourceForm {

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
//        //test
//        for(int i=0; i<randomInstance.size();i++){
//            System.out.print(randomInstance.get(i)+", ");
//        }
//        System.out.println();

//        RandomDataGenerator randomDataGenerator = new RandomDataGenerator();
//        randomDataGenerator.reSeed(0);
//
//        int[] route = randomDataGenerator.nextPermutation(this.numInstance, this.numInstance);
//        Collections.addAll(randomInstance,route);
        return randomInstance;
    }

    @Override
    public int produce(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        List<Individual> allIndividuals = new ArrayList<>();
        List<Integer> allIndividualsIndex = new ArrayList<>();
        for(int i=0;i<oldinds.length;i++){
            allIndividuals.add(oldinds[i]);
            allIndividualsIndex.add(i);
        }

        List<Integer> randomInstanceIndex = this.getRandomSequenceInstance(state,thread);
        for(int i=0;i<randomInstanceIndex.size();i++){
            if(allIndividuals.size() == 1){
//                System.out.println("find:" + i);
                return allIndividualsIndex.get(0);
            }
            int objectiveIndex = randomInstanceIndex.get(i);

            int indexBest = PopulationUtils.getIndexOfbestIndsObjective(allIndividuals,objectiveIndex);
            double bestFitness = ((MultiObjectiveFitness)allIndividuals.get(indexBest).fitness).objectives[objectiveIndex];
            int j=0;
            while(j<allIndividuals.size()){
                if(allIndividuals.size() == 1){
                    return allIndividualsIndex.get(0);
                }
                if(((MultiObjectiveFitness)allIndividuals.get(j).fitness).objectives[objectiveIndex]>bestFitness){
//                    System.out.println("indexFitness:" + ((MultiInstanceMultiObjectiveFitness)allIndividuals.get(j).fitness).getFitness(i,0) + "bestFitness:" + bestFitness);
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
