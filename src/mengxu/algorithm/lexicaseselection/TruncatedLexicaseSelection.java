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

public class TruncatedLexicaseSelection extends SelectionMethod implements SteadyStateBSourceForm {

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
