package mengxu.algorithm.multicaseEnsemble.ensembleContribution;

import ec.EvolutionState;
import ec.Individual;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.lexicaseselection.TournamentSelection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParentSelectionIncludeEnsemble extends TournamentSelection {

    public static final String P_PARENT_FROM_INDIVIDUAL_AND_ENSEMBLE = "parent-from-individual-and-ensemble";
    public boolean parentFromIndividualAndEnsemble;

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);
        parentFromIndividualAndEnsemble = state.parameters.getBoolean(new Parameter(P_PARENT_FROM_INDIVIDUAL_AND_ENSEMBLE), null, true);
    }

    @Override
    public int produce(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        int index = produceFitness(subpopulation, state, thread);
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluator)state.evaluator).ensemblePool;
        if(state instanceof GPRuleEvolutionStateEnsembleContribution){
            if(index < oldinds.length){
                double indexFitness = state.population.subpops[0].individuals[index].fitness.fitness();
                ((GPRuleEvolutionStateEnsembleContribution)state).parentIndex.add(index);
                ((GPRuleEvolutionStateEnsembleContribution)state).parentFitness.add(indexFitness);//add 2021.12.09
            }
            else{//this means we select ensemble as parent!!!
                double indexFitness = ensembles.get(index-oldinds.length).getFitness().fitness();
                ((GPRuleEvolutionStateEnsembleContribution)state).parentIndex.add(index);
                ((GPRuleEvolutionStateEnsembleContribution)state).parentFitness.add(indexFitness);//add 2021.12.09
            }
        }
        return index;
    }

    /**
     * Selection strategy 1: this only get individual as the selected parent, then just do normal crossover and mutation process
     */
    public int produceFitnessAndLocalEnsembleContribution(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluator)state.evaluator).ensemblePool;

        int totalIndsAndEnsemble = oldinds.length + ensembles.size();

        int best = state.random[thread].nextInt(totalIndsAndEnsemble);

        int s = getTournamentSizeToUse(state.random[thread]);

        for (int x=1;x<s;x++)
        {
            int randomIndex = state.random[thread].nextInt(totalIndsAndEnsemble);
            boolean firstIsEnsemble = false;
            boolean secondIsEnsemble = false;
            Individual firstInd = null;
            Individual secondInd = null;
            EnsembleRule firstEnsemble = null;
            EnsembleRule secondEnsemble = null;

            if(best < oldinds.length){
                secondInd = oldinds[best];
            }
            else{
                secondEnsemble = ensembles.get(best - oldinds.length);
                secondIsEnsemble = true;
            }
            if(randomIndex < oldinds.length){
                firstInd = oldinds[randomIndex];
            }
            else{
                firstEnsemble = ensembles.get(randomIndex - oldinds.length);
                firstIsEnsemble = true;
            }
            if(firstIsEnsemble && secondIsEnsemble){
                if(betterThanTwoEnsemble(firstEnsemble, secondEnsemble, subpopulation, state, thread)){
                    best = randomIndex;
                }
            }
            if(firstIsEnsemble && !secondIsEnsemble){
                if(betterThanEnsembleToIndividual(firstEnsemble, secondInd, subpopulation, state, thread)){
                    best = randomIndex;
                }
            }
            if(!firstIsEnsemble && secondIsEnsemble){
                if(betterThanIndividualToEnsemble(firstInd, secondEnsemble, subpopulation, state, thread)){
                    best = randomIndex;
                }
            }
            if(!firstIsEnsemble && !secondIsEnsemble){
                if(betterThanTwoindividual(firstInd, secondInd, subpopulation, state, thread)){
                    best = randomIndex;
                }
            }
        }
        if(best >= oldinds.length){
            EnsembleRule ensemble = ensembles.get(best - oldinds.length);
            //step 1: give selection probability based on the local ensemble contribution
            //step 2: select individual based on the probability
            double randomPro = state.random[thread].nextDouble(true,true);
            double[] allElements = ensemble.getEnsembleContribution();
            List<Integer> allElementsIndex = ensemble.getElementOriginalIndex();
            double totalContribution = Arrays.stream(allElements).sum();
            double pro = 0;
            for(int i=0; i< allElements.length; i++){
                pro += allElements[i]/totalContribution;
                if(randomPro < pro){
                    return allElementsIndex.get(i);
                }
            }
        }
        return best;
    }

    /**
     * Selection strategy 2: this not only can get individual as the selected parent, but also can get ensemble as parent
     */
    public int produceFitness(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluator)state.evaluator).ensemblePool;

        int totalIndsAndEnsemble = oldinds.length;
        if(this.parentFromIndividualAndEnsemble){
            totalIndsAndEnsemble = oldinds.length + ensembles.size();
        }


        int best = state.random[thread].nextInt(totalIndsAndEnsemble);

        int s = getTournamentSizeToUse(state.random[thread]);

        for (int x=1;x<s;x++)
        {
            int randomIndex = state.random[thread].nextInt(totalIndsAndEnsemble);
            boolean firstIsEnsemble = false;
            boolean secondIsEnsemble = false;
            Individual firstInd = null;
            Individual secondInd = null;
            EnsembleRule firstEnsemble = null;
            EnsembleRule secondEnsemble = null;

            if(best < oldinds.length){
                secondInd = oldinds[best];
            }
            else{
                secondEnsemble = ensembles.get(best - oldinds.length);
                secondIsEnsemble = true;
            }
            if(randomIndex < oldinds.length){
                firstInd = oldinds[randomIndex];
            }
            else{
                firstEnsemble = ensembles.get(randomIndex - oldinds.length);
                firstIsEnsemble = true;
            }
            if(firstIsEnsemble && secondIsEnsemble){
                if(betterThanTwoEnsemble(firstEnsemble, secondEnsemble, subpopulation, state, thread)){
                    best = randomIndex;
                }
            }
            if(firstIsEnsemble && !secondIsEnsemble){
                if(betterThanEnsembleToIndividual(firstEnsemble, secondInd, subpopulation, state, thread)){
                    best = randomIndex;
                }
            }
            if(!firstIsEnsemble && secondIsEnsemble){
                if(betterThanIndividualToEnsemble(firstInd, secondEnsemble, subpopulation, state, thread)){
                    best = randomIndex;
                }
            }
            if(!firstIsEnsemble && !secondIsEnsemble){
                if(betterThanTwoindividual(firstInd, secondInd, subpopulation, state, thread)){
                    best = randomIndex;
                }
            }
        }
        return best;
    }

    public boolean betterThanTwoEnsemble(EnsembleRule first, EnsembleRule second, int subpopulation, EvolutionState state, int thread)
    {
        return first.getFitness().fitness() < second.getFitness().fitness();
    }

    public boolean betterThanTwoindividual(Individual first, Individual second, int subpopulation, EvolutionState state, int thread)
    {
        return first.fitness.fitness() < second.fitness.fitness();
    }

    public boolean betterThanEnsembleToIndividual(EnsembleRule first, Individual second, int subpopulation, EvolutionState state, int thread)
    {
        return first.getFitness().fitness() < second.fitness.fitness();
    }

    public boolean betterThanIndividualToEnsemble(Individual first, EnsembleRule second, int subpopulation, EvolutionState state, int thread)
    {
        return first.fitness.fitness() < second.getFitness().fitness();
    }

}
