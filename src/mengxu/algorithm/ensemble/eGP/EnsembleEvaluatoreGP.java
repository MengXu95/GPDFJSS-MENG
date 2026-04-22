package mengxu.algorithm.ensemble.eGP;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.multiobjective.MOEADarchive.MOEADarchiveInitializer;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class EnsembleEvaluatoreGP extends SimpleEvaluator {

    /**
     * The original population size is stored here so NSGA2 knows how large to
     * create the archive (it's the size of the original population -- keep in mind
     * that NSGA2Breeder had made the population larger to include the children.
     */
    public int originalPopSize[];

    public int ensembleSize[];
//    public int ensembleNumber[];

//    public EnsembleSelection ensembleSelection;

    public ArrayList<EnsembleRule> ensemblePool;

    public ArrayList<EnsembleRule> ensembleOffspringPool; //this is for the generated offspring by crossover and mutation
    public static final String P_ENSEMBLE_SIZE = "ensemble-size";

    public int numIndsInEachSubPop;
    public List<List<Integer>> indOriginalIndexForAllSubpop;
    public List<Integer> bestIndsIndexForEachSubpop;
//    public static final String P_ENSEMBLE_NUMBER = "ensemble-number";

    public static final String P_ENSEMBLE_VOTING = "ensemble-voting";

    public boolean ensembleVoting;

    public static final String P_ENSEMBLE_MEAN_RANK = "ensemble-mean-rank";

    public boolean ensembleMeanRank;


    //added by mengxu 2023.03.02
    public EnsembleRule[] parentsForCrossover = new EnsembleRule[2];
    public EnsembleRule parentsForMutation;

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        Parameter p = new Parameter(MOEADarchiveInitializer.P_POP);
        int subpopsLength = state.parameters.getInt(p.push(Population.P_SIZE), null, 1);
        Parameter p_subpop;
        ensembleVoting = state.parameters.getBoolean(new Parameter(P_ENSEMBLE_VOTING), null, true);
        ensembleMeanRank = state.parameters.getBoolean(new Parameter(P_ENSEMBLE_MEAN_RANK), null, false);
        originalPopSize = new int[subpopsLength];
        ensembleSize = new int[subpopsLength];
        for (int i = 0; i < subpopsLength; i++) {
            p_subpop = p.push(Population.P_SUBPOP).push("" + i).push(Subpopulation.P_SUBPOPSIZE);
            originalPopSize[i] = state.parameters.getInt(p_subpop, null, 1);
            ensembleSize[i] = state.parameters.getInt(new Parameter(P_ENSEMBLE_SIZE), null);
        }
        //to make sure the same times of evaluation
        numIndsInEachSubPop = originalPopSize[0]/(ensembleSize[0]);
//        numIndsInEachSubPop = originalPopSize[0]/(ensembleSize[0]*ensembleSize[0]);
        bestIndsIndexForEachSubpop = new ArrayList<>();
        this.indOriginalIndexForAllSubpop = new ArrayList<>();
        for(int i=0; i<ensembleSize[0]; i++){
            List<Integer> indOriginalIndexForEachSubpop = new ArrayList<>();
            for(int j=0; j<numIndsInEachSubPop; j++){
                indOriginalIndexForEachSubpop.add(i*numIndsInEachSubPop+j);
            }
            int randomIndex = 0; //todo: need to double check, should be random
//            int randomIndex = state.random[0].nextInt(this.numIndsInEachSubPop);
            int indIndex = indOriginalIndexForEachSubpop.get(randomIndex);
            bestIndsIndexForEachSubpop.add(indIndex);
            indOriginalIndexForAllSubpop.add(indOriginalIndexForEachSubpop);
        }
        ensemblePool = new ArrayList<>();
        ensembleOffspringPool = new ArrayList<>();
//        ensembleSelection = new EnsembleSelection();
//        ensembleSelection.setup(state, base);
    }

    /**
     * Evaluates the population, then builds the archive and reduces the population
     * to just the archive.
     */
    public void evaluatePopulation(final EvolutionState state) {
        super.evaluatePopulation(state);//the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals

        Problem p = (MultipleTreeRuleOptimizationProblem) this.p_problem;
        ((Problem)p).prepareToEvaluate(state,0);
        SchedulingSet schedulingSet = ((MultipleRuleEvaluationModel) ((MultipleTreeRuleOptimizationProblem) this.p_problem).getEvaluationModel()).getSchedulingSet();
        Individual[] individuals = state.population.subpops[0].individuals;
        if(state.generation == 0){
            //print total individuals number
            System.out.println("Total individual size: " + individuals.length*2);
            state.totalEvaluationTime += individuals.length*2;
            //Step1: Ensemble fitness evaluation
            int bestEnsembleIndex = -1;
            double bestEnsembleFitness = Double.POSITIVE_INFINITY;
            for (int i = 0; i < individuals.length; i++) {
                ArrayList<Individual> ensemble = new ArrayList<>();
                Individual individual = individuals[i];
                ensemble.add((Individual) individual.clone());
                EnsembleRule ensembleRule = new EnsembleRule(ensemble, ensemble.size());

                MultiObjectiveFitness ensembleFitness = (MultiObjectiveFitness)individual.fitness.clone();
                List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) this.p_problem).getObjectives();

                if (this.ensembleVoting) {
                    ensembleRule.calcFitnessByVotingEnsemble(ensembleFitness, state, schedulingSet, objectives);
                } else if (this.ensembleMeanRank) {
                    System.out.println("Error in EnsembleEvaluator.java!!!");
                } else {
                    System.out.println("Error in EnsembleEvaluator.java!!!");
                }

                this.ensemblePool.add(ensembleRule);
                if(bestEnsembleFitness > ensembleFitness.fitness()){
                    bestEnsembleIndex = i;
                    bestEnsembleFitness = ensembleFitness.fitness();
                }
            }

            System.out.println("Generation: " + state.generation + " best ensemble fitness: " + bestEnsembleFitness);

            //Step2: best ensemble prune
            EnsembleRule bestEnsembleRule = this.ensemblePool.get(bestEnsembleIndex);
            List<Individual> allIndividuals = bestEnsembleRule.getEnsembleClone();

            boolean pruned = true;
            while(pruned){
                if(allIndividuals.size() > 1){
                    List<Individual> allIndividualsPruned = new ArrayList<>();
                    for(int i=1; i<allIndividuals.size(); i++){
                        allIndividualsPruned.add(allIndividuals.get(i));
                    }
                    EnsembleRule ensembleRule = new EnsembleRule(allIndividualsPruned, allIndividualsPruned.size());
                    MultiObjectiveFitness ensembleFitness = (MultiObjectiveFitness)allIndividuals.get(0).fitness.clone();
                    List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) this.p_problem).getObjectives();

                    if (this.ensembleVoting) {
                        ensembleRule.calcFitnessByVotingEnsemble(ensembleFitness, state, schedulingSet, objectives);
                    } else if (this.ensembleMeanRank) {
                        System.out.println("Error in EnsembleEvaluator.java!!!");
                    } else {
                        System.out.println("Error in EnsembleEvaluator.java!!!");
                    }
                    double prunedEnsembleFitness = ensembleFitness.fitness();
                    if(prunedEnsembleFitness > bestEnsembleFitness){
                        pruned = false;
                    }else{
                        bestEnsembleRule = ensembleRule;
                        allIndividuals = bestEnsembleRule.getEnsembleClone();
                    }
                }
                else{
                    pruned = false;
                }
            }
            this.ensemblePool.set(bestEnsembleIndex,bestEnsembleRule);
            ((Problem)p).finishEvaluating(state,0);
        }
        else{
            int totalIndividualSize = individuals.length;
            state.totalEvaluationTime += individuals.length;
            int bestEnsembleIndex = -1;
            double bestEnsembleFitness = Double.POSITIVE_INFINITY;
            for(int i=0; i<this.ensemblePool.size(); i++){
                EnsembleRule ensembleRule = this.ensemblePool.get(i);
                Individual individual = ensembleRule.getEnsemble().get(0);
                MultiObjectiveFitness ensembleFitness = (MultiObjectiveFitness)individual.fitness.clone();
                List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) this.p_problem).getObjectives();

                totalIndividualSize += ensembleRule.getEnsemble().size();
                state.totalEvaluationTime += ensembleRule.getEnsemble().size();

                if (this.ensembleVoting) {
                    ensembleRule.calcFitnessByVotingEnsemble(ensembleFitness, state, schedulingSet, objectives);
                } else if (this.ensembleMeanRank) {
                    System.out.println("Error in EnsembleEvaluator.java!!!");
                } else {
                    System.out.println("Error in EnsembleEvaluator.java!!!");
                }

                if(bestEnsembleFitness > ensembleFitness.fitness()){
                    bestEnsembleIndex = i;
                    bestEnsembleFitness = ensembleFitness.fitness();
                }
            }

            System.out.println("Generation: " + state.generation + " best ensemble fitness: " + bestEnsembleFitness);

            //Step2: best ensemble prune
            EnsembleRule bestEnsembleRule = this.ensemblePool.get(bestEnsembleIndex);
            List<Individual> allIndividuals = bestEnsembleRule.getEnsembleClone();
            totalIndividualSize -= bestEnsembleRule.getEnsemble().size();

            boolean pruned = true;
            while(pruned){
                if(allIndividuals.size() > 1){
//                    allIndividuals.remove(0);
                    List<Individual> allIndividualsPruned = new ArrayList<>();
                    for(int i=1; i<allIndividuals.size(); i++){
                        allIndividualsPruned.add(allIndividuals.get(i));
                    }
                    EnsembleRule ensembleRule = new EnsembleRule(allIndividualsPruned, allIndividualsPruned.size());
                    MultiObjectiveFitness ensembleFitness = (MultiObjectiveFitness)allIndividuals.get(0).fitness.clone();
                    List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) this.p_problem).getObjectives();

                    if (this.ensembleVoting) {
                        ensembleRule.calcFitnessByVotingEnsemble(ensembleFitness, state, schedulingSet, objectives);
                    } else if (this.ensembleMeanRank) {
                        System.out.println("Error in EnsembleEvaluator.java!!!");
                    } else {
                        System.out.println("Error in EnsembleEvaluator.java!!!");
                    }
                    state.totalEvaluationTime += ensembleRule.getEnsemble().size();
                    double prunedEnsembleFitness = ensembleFitness.fitness();
                    if(prunedEnsembleFitness > bestEnsembleFitness){
                        pruned = false;
                    }else{
                        bestEnsembleRule = ensembleRule;
                        allIndividuals = bestEnsembleRule.getEnsembleClone();
                    }
                }
                else{
                    pruned = false;
                }
            }
            this.ensemblePool.set(bestEnsembleIndex,bestEnsembleRule);
            totalIndividualSize += bestEnsembleRule.getEnsemble().size();

            //print total individuals number
            System.out.println("Total individual size: " + totalIndividualSize);

            ((Problem)p).finishEvaluating(state,0);
        }

    }

    public void storeEnsembleElitism(){

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

    //todo: need to double check and modify by mengxu 2023.07.31
    public boolean runComplete(final EvolutionState state)
    {
        return false;
    }

}
