package mengxu.algorithm.multicaseEnsemble.ensembleContribution;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import mengxu.algorithm.multiobjective.MOEADarchive.MOEADarchiveInitializer;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class EnsembleEvaluatorIncludeEnsemble extends SimpleEvaluator {

    /**
     * The original population size is stored here so NSGA2 knows how large to
     * create the archive (it's the size of the original population -- keep in mind
     * that NSGA2Breeder had made the population larger to include the children.
     */
    public int originalPopSize[];

    public int ensembleSize[];
    public int ensembleNumber[];

    public EnsembleSelection ensembleSelection;

    public ArrayList<EnsembleRule> ensemblePool;

    public ArrayList<EnsembleRule> ensembleOffspringPool; //this is for the generated offspring by crossover and mutation
    public static final String P_ENSEMBLE_SIZE = "ensemble-size";
    public static final String P_ENSEMBLE_NUMBER = "ensemble-number";

    public static final String P_ENSEMBLE_ELITISM = "ensemble-elitism";

    public static final String P_ENSEMBLE_SELECTION_SIMILARITY_CHECK = "ensemble-selection-similarity-check";
    public boolean ensembleSelectionSimilarityCheck;

    public int ensembleElitism;

    //added by mengxu 2023.03.02
    public EnsembleRule[] parentsForCrossover = new EnsembleRule[2];
    public EnsembleRule parentsForMutation;

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        Parameter p = new Parameter(MOEADarchiveInitializer.P_POP);
        int subpopsLength = state.parameters.getInt(p.push(Population.P_SIZE), null, 1);
        Parameter p_subpop;
        ensembleSelectionSimilarityCheck = state.parameters.getBoolean(new Parameter(P_ENSEMBLE_SELECTION_SIMILARITY_CHECK), null, false);
        ensembleElitism = state.parameters.getInt(new Parameter(P_ENSEMBLE_ELITISM), null, 0);
        originalPopSize = new int[subpopsLength];
        ensembleSize = new int[subpopsLength];
        ensembleNumber = new int[subpopsLength];
        for (int i = 0; i < subpopsLength; i++) {
            p_subpop = p.push(Population.P_SUBPOP).push("" + i).push(Subpopulation.P_SUBPOPSIZE);
            originalPopSize[i] = state.parameters.getInt(p_subpop, null, 1);
            ensembleSize[i] = state.parameters.getInt(new Parameter(P_ENSEMBLE_SIZE), null);
            ensembleNumber[i] = state.parameters.getInt(new Parameter(P_ENSEMBLE_NUMBER), null);
        }
        ensemblePool = new ArrayList<>();
        ensembleOffspringPool = new ArrayList<>();
        ensembleSelection = new EnsembleSelection();
        ensembleSelection.setup(state, base);
    }

    /**
     * Evaluates the population, then builds the archive and reduces the population
     * to just the archive.
     */
    public void evaluatePopulation(final EvolutionState state) {
        super.evaluatePopulation(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals

        //Step1: After multi-case fitness evaluation, we need to use Lexicase selection to select ensemble
        if(state.generation != 0){
            EnsembleRule[] ensembleRules = sortEnsembles(ensemblePool);
            ensemblePool = new ArrayList<>();
            int i=0;
            for(i=0; i<ensembleElitism; i++){
                ensemblePool.add(ensembleRules[i]);
            }
            //step0: Check the ensemble offspring first
            if(ensembleOffspringPool.size() >= ensembleNumber[0]-ensembleElitism){
                int permSize = (int)(ensembleNumber[0]-ensembleElitism);
                int[] permutation = new int[permSize];
                MOEADUtils.randomPermutation(state, 0, permutation, permSize);
                for (int x=0;x<permSize;x++) {
                    int j = permutation[x];
                    ensemblePool.add(ensembleOffspringPool.get(j));
                }
            }
            else{
                ensemblePool.addAll(ensembleOffspringPool);
            }
        }
        int tryTimes = ensembleSize[0] * 3;
        int subpopsLength = ensembleSize.length;
        for(int i=0; i<subpopsLength; i++){
            Individual[] individuals = state.population.subpops[i].individuals;
            for(int j=ensemblePool.size(); j<ensembleNumber[i]; j++){
                ArrayList<Individual> ensemble = new ArrayList<>();
                ArrayList<Integer> elementOriginalIndex = new ArrayList<>();
                List<List<Integer>> elementSelectedBasedOnCaseOrder = new ArrayList<>();
                int tryTime = 0;
                while(ensemble.size() < ensembleSize[i] && tryTime < tryTimes){
                    int index = ensembleSelection.produce(i,state,0);
                    if(ensembleSelectionSimilarityCheck){//added by mengxu 2023.03.13
                        //check similarity between this individual with all the elements in the ensemble
                        boolean different = true;
                        //might just calculate the top 5 cases
                        //todo: might not only consider diversity, but also need to consider the complementarity!!!!!!
                        List<Integer> indACasePermutation = ensembleSelection.usedRandomInstanceIndex.subList(0,1);
//                        List<Integer> indACasePermutation = ensembleSelection.usedRandomInstanceIndex;
                        for(int e=0; e<elementSelectedBasedOnCaseOrder.size(); e++){
                            List<Integer> indBCasePermutation = elementSelectedBasedOnCaseOrder.get(e);
                            double similarity = similarityBasedOnCasePermutation(indACasePermutation, indBCasePermutation);
                            if(similarity > 0){//0 here is a parameter need to do sensitivity analysis, which is about the similarity between elements in an ensemble
                                different = false;
                            }
                        }
                        if(different){
                            System.out.println("Case order same!!!");
                        }
                        //avoid duplication
                        if(!elementOriginalIndex.contains(index) && different){
                            ensemble.add(individuals[index]);
                            elementOriginalIndex.add(index);
                            elementSelectedBasedOnCaseOrder.add(indACasePermutation);
                        }
                    }
                    else{
                        //avoid duplication
                        if(!elementOriginalIndex.contains(index)){
                            ensemble.add(individuals[index]);
                            elementOriginalIndex.add(index);
                        }
                    }
                    tryTime++;
                }
                EnsembleRule ensembleRule = new EnsembleRule(ensemble,elementOriginalIndex, ensembleSize[i]);
                if(ensembleSelectionSimilarityCheck){//added by mengxu 2023.03.13
                    ensembleRule.setElementSelectedBasedOnCaseOrder(elementSelectedBasedOnCaseOrder);
                }
                ensemblePool.add(ensembleRule);
            }
        }

        //Step2: Ensemble fitness evaluation
        SchedulingSet schedulingSet = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)this.p_problem).getEvaluationModel()).getSchedulingSet();
        for(int i=0; i<ensemblePool.size(); i++){
            EnsembleRule ensemble = ensemblePool.get(i);
            Fitness ensembleFitness = new MultiObjectiveFitness();
            List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) this.p_problem).getObjectives();
            ensemble.calcFitnessByVotingEnsemble(ensembleFitness, state, schedulingSet, objectives);
            //during this process, we need to calculate the ensemble contribution of each individual
        }

        //step3: print the best individual/ensemble
        int bestIdInd = 0;
        double bestFitness = Double.POSITIVE_INFINITY;
        Individual[] individuals = state.population.subpops[0].individuals;
        for(int i=0; i<individuals.length; i++) {
            double fitness = ((OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)individuals[i].fitness).fitness();
            if(fitness < Double.MAX_VALUE && fitness < Double.POSITIVE_INFINITY ){
//                System.out.println("Best ind fitness: " + fitness);
            }
            if(fitness < bestFitness){
                bestFitness = fitness;
                bestIdInd = i;
            }
        }
        System.out.println("Best ind fitness: " + bestFitness);

        int bestIdEnsemble = -1;
        double[] bestEnsembleContribution = new double[ensembleSize[0]];
        for(int i=0; i<ensemblePool.size(); i++){
            EnsembleRule ensemble = ensemblePool.get(i);
            Fitness ensembleFitness = ensemble.getFitness();
            double fitness = ensembleFitness.fitness();
            if(fitness < bestFitness){
                bestFitness = fitness;
                bestIdEnsemble = i;
                bestEnsembleContribution = ensemble.getEnsembleContribution();
            }
        }
        if(bestIdEnsemble != -1){
            System.out.println("Best ensemble fitness: " + bestFitness);
            System.out.print("Ensemble contribution: [");
            for(int i=0; i<bestEnsembleContribution.length-1; i++){
                System.out.print(bestEnsembleContribution[i] + ", ");
            }
            System.out.println(bestEnsembleContribution[ensembleSize[0]-1] + "]");
        }

        ensembleOffspringPool.clear();
    }

    //added bymengxu 2023.03.13
    public double similarityBasedOnCasePermutation(List<Integer> indACasePermutation, List<Integer> indBCasePermutation){
        double similarity = 0;
        double importance = 1;
        for(int i=0; i<indACasePermutation.size(); i++){
            if(indACasePermutation.get(i) == indBCasePermutation.get(i)){
                //todo: need to double think about the similarity calculation way
                similarity += 1 * (importance - (double)i/(double)indACasePermutation.size());
            }
        }
        return similarity;
    }

    public void storeEnsembleElitism(){

    }

    public static EnsembleRule[] sortEnsembles(List<EnsembleRule> ensembles) {
        Comparator<EnsembleRule> comp = (EnsembleRule o1, EnsembleRule o2) ->
        {
            if (o1.getFitness().fitness() < o2.getFitness().fitness())
                return -1;
            if (o1.getFitness().fitness() == o2.getFitness().fitness())
                return 0;
            return 1;
        };
        //convert arraylist to array
        EnsembleRule[] ensembleRules = ensembles.toArray(new EnsembleRule[ensembles.size()]);
        Arrays.sort(ensembleRules, comp);
        return ensembleRules;
    }

}
