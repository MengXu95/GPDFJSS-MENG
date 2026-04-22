package mengxu.algorithm.multicaseEnsemble.ensembleContribution;

import ec.EvolutionState;
import ec.Individual;
import ec.util.Parameter;
import mengxu.algorithm.lexicaseselection.EpsilonTTGenNoRepeatCandidateLexicaseSelection;
import mengxu.algorithm.lexicaseselection.GPRuleEvolutionStateOneInstanceMultiCase;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import yimei.jss.helper.PopulationUtils;

import java.util.ArrayList;
import java.util.List;

public class EnsembleSelection extends EpsilonTTGenNoRepeatCandidateLexicaseSelection {

    public List<Integer> usedRandomInstanceIndex;

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);
        usedRandomInstanceIndex = null;
    }

    @Override
    public int produce(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        if(state.generation>((GPRuleEvolutionStateEnsembleContribution)state).genForUseLS){
//            System.out.println("Tournament selection index: " + produceTournament(subpopulation, state, thread));
            int index = produceLS(subpopulation, state, thread);
            if(state instanceof GPRuleEvolutionStateEnsembleContribution){
                double indexFitness = state.population.subpops[0].individuals[index].fitness.fitness();
//                ((GPRuleEvolutionStateMV0)state).parentIndex.add(index);
                ((GPRuleEvolutionStateEnsembleContribution)state).parentFitness.add(indexFitness);//add 2021.12.09
            }
            return index;
        }
        else{
//            System.out.println("Epsilon selection index: " + produceLS(subpopulation, state, thread));
            int index = produceTournament(subpopulation, state, thread);
            this.usedRandomInstanceIndex = null;
            if(state instanceof GPRuleEvolutionStateEnsembleContribution){
                double indexFitness = state.population.subpops[0].individuals[index].fitness.fitness();
//                ((GPRuleEvolutionStateMV0)state).parentIndex.add(index);
                ((GPRuleEvolutionStateEnsembleContribution)state).parentFitness.add(indexFitness);//add 2021.12.09
            }
            return index;
        }
    }

    public int produceLS(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        Individual[] oldinds = state.population.subpops[subpopulation].individuals.clone();
        List<Individual> allIndividuals = new ArrayList<>();
        List<Integer> allIndividualsIndex = getCandidates(subpopulation, state, thread);
        for(int i=0;i<allIndividualsIndex.size();i++){
            allIndividuals.add(oldinds[allIndividualsIndex.get(i)]);
        }

        List<Integer> randomInstanceIndex = this.getRandomSequenceInstance(state,thread);
        this.usedRandomInstanceIndex = new ArrayList<>();
        this.usedRandomInstanceIndex.addAll(randomInstanceIndex);//todo: double check whether all the same
        for(int i=0;i<randomInstanceIndex.size();i++){
            if(allIndividuals.size() == 1){
                return allIndividualsIndex.get(0);
            }

            int instanceIndex = randomInstanceIndex.get(i);

            //add 2021.11.16-------------------------------
//            if(state instanceof GPRuleEvolutionStateEnsembleContribution){
//                double[] caseNumber = ((GPRuleEvolutionStateEnsembleContribution)state).allGenerationCaseNumber.get(state.generation-((GPRuleEvolutionStateEnsembleContribution)state).genForUseLS-1);
//                caseNumber[instanceIndex]++;
//                ((GPRuleEvolutionStateEnsembleContribution)state).allGenerationCaseNumber.set(state.generation-((GPRuleEvolutionStateEnsembleContribution)state).genForUseLS-1, caseNumber);
//            }
            //---------------------------------------------

            int indexBest = PopulationUtils.getIndexOfbestIndsInstance(allIndividuals,instanceIndex);
            double bestFitness = ((OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)allIndividuals.get(indexBest).fitness).getFitness(instanceIndex,0);

            //calculate the epsilon------------------------
            //todo: need to check if the location to calculate epsilon is right? 2021.10.14
            double epsilon = calculateEpsilon(allIndividuals,instanceIndex);

            int j=0;
            while(j<allIndividuals.size()){
                if(allIndividuals.size() == 1){
                    return allIndividualsIndex.get(0);
                }
                if(((OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)allIndividuals.get(j).fitness).getFitness(instanceIndex,0)>bestFitness + epsilon){
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

        List<Integer> tourIndividuals = new ArrayList<>();

        for (int x=0;x<permSize;x++)
        {
            //modified by mengxu 2021.09.24
            //need to exclude the bad fitness, or will make error when calculate the epsilon.
            int j=permutation[x];
            Individual ind = state.population.subpops[0].individuals[j];
            double[][] multiCaseFitness = ((OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)ind.fitness).multiInstanceMultiObjectiveFitness;
            if(multiCaseFitness[0][0] >= Double.POSITIVE_INFINITY || multiCaseFitness[0][0] >= Double.MAX_VALUE) {
                continue;
            }else{
                tourIndividuals.add(j);
            }
            if(tourIndividuals.size() == s){
                return tourIndividuals;
            }
        }

//        System.out.println("TourIndividuals' size has not arrive at the set size in advance.");
        return tourIndividuals;
    }

    public double calculateEpsilon(List<Individual> allIndividuals, int i){
        List<Double> allFitness = new ArrayList<>();
        for(int ref=0; ref<allIndividuals.size(); ref++){
            double fit = ((OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)allIndividuals.get(ref).fitness).getFitness(i,0);
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
}
