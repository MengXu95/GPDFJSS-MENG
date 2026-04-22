package mengxu.algorithm.ensemble.ensembleGPJohnPark;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.multicaseEnsemble.ensembleContribution.EnsembleSelection;
import mengxu.algorithm.multicaseEnsemble.ensembleContribution.OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution;
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

public class EnsembleEvaluatorJohnPark extends SimpleEvaluator {

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
        super.evaluatePopulation(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals

            //Step1: Ensemble fitness evaluation
            Individual[] individuals = state.population.subpops[0].individuals;
            ec.Problem p = (MultipleTreeRuleOptimizationProblem) this.p_problem;
            ((ec.Problem)p).prepareToEvaluate(state,0);
            SchedulingSet schedulingSet = ((MultipleRuleEvaluationModel) ((MultipleTreeRuleOptimizationProblem) this.p_problem).getEvaluationModel()).getSchedulingSet();
            for (int i = 0; i < this.indOriginalIndexForAllSubpop.size(); i++) {
                List<Integer> indOriginalIndexForEachSubpop = this.indOriginalIndexForAllSubpop.get(i);
                List<Individual> bestIndsFromOtherSubpop = new ArrayList<>();
                for (int m = 0; m < this.bestIndsIndexForEachSubpop.size(); m++) {
                    if (m != i) {
                        bestIndsFromOtherSubpop.add(individuals[this.bestIndsIndexForEachSubpop.get(m)]);
                    }
                }
                for (int j = 0; j < indOriginalIndexForEachSubpop.size(); j++) {
                    ArrayList<Individual> ensemble = new ArrayList<>();
                    ensemble.addAll(bestIndsFromOtherSubpop);
                    int index = indOriginalIndexForEachSubpop.get(j);
                    Individual individual = individuals[index];
                    ensemble.add((Individual) individual.clone());
                    EnsembleRule ensembleRule = new EnsembleRule(ensemble, ensemble.size());

                    //                  Fitness ensembleFitness = new MultiObjectiveFitness();
                    MultiObjectiveFitness ensembleFitness = (MultiObjectiveFitness)individual.fitness.clone();
                    List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) this.p_problem).getObjectives();
//                    ensembleFitness.setObjectives(state,(Objective[])objectives.toArray());
                    //todo: need to double check 2023.03.07
                    //                double mean = ((HeterogeneousSimulation)schedulingSet.getSimulations().get(0)).getInterArrivalTimeSamplerMean();
                    //                System.out.println("mean: " + mean);
                    if (this.ensembleVoting) {
                        ensembleRule.calcFitnessByVotingEnsemble(ensembleFitness, state, schedulingSet, objectives);
                    } else if (this.ensembleMeanRank) {
                        System.out.println("Error in EnsembleEvaluator.java!!!");
//                        ensembleRule.calcFitnessByMeanRankEnsembleAndMultiCaseFitnesssEvaluation(ensembleFitness, state, schedulingSet, objectives);
                    } else {
                        System.out.println("Error in EnsembleEvaluator.java!!!");
                    }
//                    MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
//                    f.setObjectives(state, fitnesses);
//                    this.fitness = (MultiObjectiveFitness)f.clone();
                    individual.fitness = (MultiObjectiveFitness) ensembleRule.getFitness().clone();
                    individual.evaluated = true;
                    //                ensemble.calcFitnessByVotingEnsemble(ensembleFitness, state, schedulingSet, objectives);
                    //during this process, we need to calculate the ensemble contribution of each individual
                }
            }
            ((ec.Problem)p).finishEvaluating(state,0);

            //Step2: best individual update for each subpop
            for(int i=0; i<ensembleSize[0]; i++){
                List<Integer> indOriginalIndexForEachSubpop = this.indOriginalIndexForAllSubpop.get(i);
                int bestIndex = indOriginalIndexForEachSubpop.get(0);
                Individual bestInd = individuals[bestIndex];
                double bestFit = bestInd.fitness.fitness();
                for(int j=0; j<indOriginalIndexForEachSubpop.size(); j++){
                    int index = indOriginalIndexForEachSubpop.get(j);
                    Individual ind = individuals[index];
                    double fit = ind.fitness.fitness();
                    if(fit < bestFit){
                        bestInd = ind;
                        fit = bestFit;
                        bestIndex = index;
                    }
                }
                bestIndsIndexForEachSubpop.set(i,bestIndex);
            }

//            //step3: print the best individual/ensemble
//            int bestIdInd = 0;
//            double bestFitness = Double.POSITIVE_INFINITY;
//            Individual[] individuals = state.population.subpops[0].individuals;
//            for (int i = 0; i < individuals.length; i++) {
//                double fitness = ((OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution) individuals[i].fitness).fitness();
//                if (fitness < Double.MAX_VALUE && fitness < Double.POSITIVE_INFINITY) {
////                System.out.println("Best ind fitness: " + fitness);
//                }
//                if (fitness < bestFitness) {
//                    bestFitness = fitness;
//                    bestIdInd = i;
//                }
//            }
//            System.out.println("Best ind fitness: " + bestFitness);
//
//            int bestIdEnsemble = -1;
//            double[] bestEnsembleContribution = new double[ensembleSize[0]];
//            for (int i = 0; i < ensemblePool.size(); i++) {
//                EnsembleRule ensemble = ensemblePool.get(i);
//                Fitness ensembleFitness = ensemble.getFitness();
////            Fitness ensembleFitness = ensemble.getEnsembleFitness();
//                double fitness = ensembleFitness.fitness();
//                if (fitness < bestFitness) {
//                    bestFitness = fitness;
//                    bestIdEnsemble = i;
//                    bestEnsembleContribution = ensemble.getEnsembleContribution();
//                }
//            }
//            if (bestIdEnsemble != -1) {
//                System.out.println("Best ensemble fitness: " + bestFitness);
//                System.out.print("Ensemble contribution: [");
//                for (int i = 0; i < bestEnsembleContribution.length - 1; i++) {
//                    System.out.print(bestEnsembleContribution[i] + ", ");
//                }
//                System.out.println(bestEnsembleContribution[bestEnsembleContribution.length - 1] + "]");
//            }

            /**
             * for test by mengxu: print all the individuals and ensemble
             */
//            for(int i=0; i<individuals.length; i++){
//                individuals[i].printIndividualForHumans(state,0);
//                System.out.println("");
//            }
//
//            for(int i=0; i<ensemblePool.size(); i++){
//                System.out.println("Ensemble " + i + ":");
//                System.out.println("Ensemble fitness: " + ensemblePool.get(i).getFitness().fitness());
//                System.out.print("Ensemble contribution: [");
//                for (int c = 0; c < ensemblePool.get(i).getEnsembleContribution().length - 1; c++) {
//                    System.out.print(ensemblePool.get(i).getEnsembleContribution()[i] + ", ");
//                }
//                System.out.println(ensemblePool.get(i).getEnsembleContribution()[ensemblePool.get(i).getEnsembleContribution().length - 1] + "]");
//                List<Individual> ensemble = ensemblePool.get(i).getEnsemble();
//                for(int j=0; j<ensemble.size(); j++){
//                    ensemble.get(j).printIndividualForHumans(state,0);
//                }
//                System.out.println("");
//            }
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

}
