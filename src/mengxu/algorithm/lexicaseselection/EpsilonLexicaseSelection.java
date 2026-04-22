package mengxu.algorithm.lexicaseselection;

import ec.EvolutionState;
import ec.Individual;
import ec.SelectionMethod;
import ec.select.SelectDefaults;
import ec.steadystate.SteadyStateBSourceForm;
import ec.steadystate.SteadyStateEvolutionState;
import ec.util.Parameter;
import yimei.jss.helper.PopulationUtils;

import java.util.ArrayList;
import java.util.List;

public class EpsilonLexicaseSelection extends SelectionMethod implements SteadyStateBSourceForm {

    /** default base */
    public static final String P_LEXICASE = "lexicase";

    public static final String P_NUMCASE = "num-case";
    public int numCase;

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

//        int numIns;
        Parameter def = defaultBase();
//        numIns = state.parameters.getInt(new Parameter(P_NUMCASE), def.push(P_NUMCASE), 0);
//        if (numIns <= 0)
//            state.output.fatal("The number of cases must be an integer >= 1.", base.push(P_NUMCASE), def.push(P_NUMCASE));

        this.numCase = state.parameters.getInt(new Parameter(P_NUMCASE), null, 1);

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
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        List<Individual> allIndividuals = new ArrayList<>();
        List<Integer> allIndividualsIndex = new ArrayList<>();
        for(int i=0;i<oldinds.length;i++){
            //modified by mengxu 2021.09.24
            //need to exclude the bad fitness, or will make error when calculate the epsilon.
            Individual ind = oldinds[i];
            double[][] multiCaseFitness = ((OneInstanceMultiCaseMultiObjectiveFitness)ind.fitness).multiInstanceMultiObjectiveFitness;
            if(multiCaseFitness[0][0] >= Double.POSITIVE_INFINITY || multiCaseFitness[0][0] >= Double.MAX_VALUE) {
                continue;
            }else{
                allIndividuals.add(oldinds[i]);
                allIndividualsIndex.add(i);
            }
            //test
//            System.out.print("individual " + i + ": [");
//            for(int f=0;f<this.numCase;f++){
//                System.out.print(((MultiInstanceMultiObjectiveFitness)oldinds[i].fitness).getFitness(f,0) + ", ");
//            }
//            System.out.print("]");
        }

        List<Integer> randomInstanceIndex = this.getRandomSequenceInstance(state,thread);
        for(int i=0;i<randomInstanceIndex.size();i++){
            if(allIndividuals.size() == 1){
//                System.out.println("epsilon selection index: " + allIndividualsIndex.get(0));
//                if(state instanceof GPRuleEvolutionStateMV0){
//                    ((GPRuleEvolutionStateMV0)state).parentIndex.add(allIndividualsIndex.get(0));
//                }
                return allIndividualsIndex.get(0);
            }

            int instanceIndex = randomInstanceIndex.get(i);

            int indexBest = PopulationUtils.getIndexOfbestIndsInstance(allIndividuals,instanceIndex);
            double bestFitness = ((OneInstanceMultiCaseMultiObjectiveFitness)allIndividuals.get(indexBest).fitness).getFitness(instanceIndex,0);

            //calculate the epsilon------------------------
            double epsilon = calculateEpsilon(allIndividuals,instanceIndex);


            int j=0;
            while(j<allIndividuals.size()){
                if(allIndividuals.size() == 1){
//                    System.out.println("epsilon selection index: " + allIndividualsIndex.get(0));
//                    if(state instanceof GPRuleEvolutionStateMV0){
//                        ((GPRuleEvolutionStateMV0)state).parentIndex.add(allIndividualsIndex.get(0));
//                    }
                    return allIndividualsIndex.get(0);
                }
                if(((OneInstanceMultiCaseMultiObjectiveFitness)allIndividuals.get(j).fitness).getFitness(instanceIndex,0)>bestFitness + epsilon){
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
//        if(state instanceof GPRuleEvolutionStateMV0){
//            ((GPRuleEvolutionStateMV0)state).parentIndex.add(allIndividualsIndex.get(index));
//        }
        return allIndividualsIndex.get(index);
    }

    public double calculateEpsilon(List<Individual> allIndividuals, int i){
        List<Double> allFitness = new ArrayList<>();
        for(int ref=0; ref<allIndividuals.size(); ref++){
            double fit = ((OneInstanceMultiCaseMultiObjectiveFitness)allIndividuals.get(ref).fitness).getFitness(i,0);
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
