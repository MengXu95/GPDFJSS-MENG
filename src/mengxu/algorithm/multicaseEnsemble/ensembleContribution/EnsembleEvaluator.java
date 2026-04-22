package mengxu.algorithm.multicaseEnsemble.ensembleContribution;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import mengxu.algorithm.lexicaseselection.TournamentSelection;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import mengxu.algorithm.multiobjective.MOEADarchive.MOEADarchiveInitializer;
import mengxu.complexsimulation.HeterogeneousSimulation;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

import java.util.*;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

public class EnsembleEvaluator extends SimpleEvaluator {

    /**
     * The original population size is stored here so NSGA2 knows how large to
     * create the archive (it's the size of the original population -- keep in mind
     * that NSGA2Breeder had made the population larger to include the children.
     */
    public int originalPopSize[];

    public int ensembleSize[];
    public int ensembleNumber[];

//    public EnsembleSelection ensembleSelection;
    public SelectionMethod ensembleSelection;

    public ArrayList<EnsembleRule> ensemblePool;

    public ArrayList<EnsembleRule> ensembleOffspringPool; //this is for the generated offspring by crossover and mutation
    public static final String P_ENSEMBLE_SIZE = "ensemble-size";
    public static final String P_ENSEMBLE_NUMBER = "ensemble-number";

    public static final String P_ENSEMBLE_ELITISM = "ensemble-elitism";

    public static final String P_ENSEMBLE_OFFSPRING_PRO = "ensemble-offspring-pro";

    public double ensembleOffspringPro;

    public static final String P_ENSEMBLE_VOTING = "ensemble-voting";

    public boolean ensembleVoting;

    public static final String P_ENSEMBLE_MEAN_RANK = "ensemble-mean-rank";

    public static final String P_ENSEMBLE_SELECTION_SIMILARITY_CHECK = "ensemble-selection-similarity-check";
    public boolean ensembleSelectionSimilarityCheck;

    //added by meng xu 2023.03.24
    public static final String P_ENSEMBLE_SELECTION_SIMILARITY_CHECK_BASEDON_CORRELATION = "ensemble-selection-similarity-check-basedon-correlation";
    public boolean ensembleSelectionSimilarityCheckBasedonCorrelation;
    //added by meng xu 2023.03.24
    public static final String P_PERCENTAGE_INDIVIDUAL_CORRELATION_CHECK = "percentage-individual-correlation-check";
    public double percentageIndividualCorrelationCheck;

    public double[][] correlationBetweenCases;

    public static final String P_NUM_CASE_FOR_SIMILARITY_CHECK = "num-case-for-similarity-check";
    public int numCaseForSimilarityCheck;

    public boolean ensembleMeanRank;

    public int ensembleElitism;

    //added by mengxu 2023.03.02
    public EnsembleRule[] parentsForCrossover = new EnsembleRule[2];
    public EnsembleRule parentsForMutation;

    //add by mengxu 2023.04.26
    public static final String P_TOURNAMENT_SELECTION_FOR_ENSEMBLE = "tournament-selection-for-ensemble";
    public boolean tournamentSelectionForEnsemble;

    public static final String P_NUMCASE = "num-case";


    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        Parameter p = new Parameter(MOEADarchiveInitializer.P_POP);
        int subpopsLength = state.parameters.getInt(p.push(Population.P_SIZE), null, 1);
        Parameter p_subpop;
        ensembleSelectionSimilarityCheck = state.parameters.getBoolean(new Parameter(P_ENSEMBLE_SELECTION_SIMILARITY_CHECK), null, false);
        numCaseForSimilarityCheck = state.parameters.getInt(new Parameter(P_NUM_CASE_FOR_SIMILARITY_CHECK), null, 1);
        ensembleElitism = state.parameters.getInt(new Parameter(P_ENSEMBLE_ELITISM), null, 0);
        ensembleOffspringPro = state.parameters.getDouble(new Parameter(P_ENSEMBLE_OFFSPRING_PRO), null, 0);
        ensembleVoting = state.parameters.getBoolean(new Parameter(P_ENSEMBLE_VOTING), null, true);
        ensembleMeanRank = state.parameters.getBoolean(new Parameter(P_ENSEMBLE_MEAN_RANK), null, false);
        originalPopSize = new int[subpopsLength];
        ensembleSize = new int[subpopsLength];
        ensembleNumber = new int[subpopsLength];
        ensembleSelectionSimilarityCheckBasedonCorrelation = state.parameters.getBoolean(new Parameter(P_ENSEMBLE_SELECTION_SIMILARITY_CHECK_BASEDON_CORRELATION), null, false);
        percentageIndividualCorrelationCheck = state.parameters.getDouble(new Parameter(P_PERCENTAGE_INDIVIDUAL_CORRELATION_CHECK), null, 0);
        for (int i = 0; i < subpopsLength; i++) {
            p_subpop = p.push(Population.P_SUBPOP).push("" + i).push(Subpopulation.P_SUBPOPSIZE);
            originalPopSize[i] = state.parameters.getInt(p_subpop, null, 1);
            ensembleSize[i] = state.parameters.getInt(new Parameter(P_ENSEMBLE_SIZE), null);
            ensembleNumber[i] = state.parameters.getInt(new Parameter(P_ENSEMBLE_NUMBER), null);
        }
        ensemblePool = new ArrayList<>();
        ensembleOffspringPool = new ArrayList<>();
        tournamentSelectionForEnsemble = state.parameters.getBoolean(new Parameter(P_TOURNAMENT_SELECTION_FOR_ENSEMBLE), null, false);
        if(tournamentSelectionForEnsemble){
            ensembleSelection = new TournamentSelection();
        }
        else{
            ensembleSelection = new EnsembleSelection();
        }
//        ensembleSelection = new EnsembleSelection();
        ensembleSelection.setup(state, base);

        if(ensembleSelectionSimilarityCheckBasedonCorrelation) {
            int numCases = state.parameters.getInt(new Parameter(P_NUMCASE), null, 1);
            correlationBetweenCases = new double[numCases][numCases];
        }


    }

    /**
     * Evaluates the population, then builds the archive and reduces the population
     * to just the archive.
     */
    public void evaluatePopulation(final EvolutionState state) {
        super.evaluatePopulation(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals

        for(int i=0; i<state.population.subpops.length; i++){
            state.totalEvaluationTime += state.population.subpops[i].individuals.length;
        }

        if(ensembleSelectionSimilarityCheckBasedonCorrelation){
            Individual[] individuals = state.population.subpops[0].individuals.clone();
            calculateCorrelationBetweenCasesBasedOnTopNIndividuals(state, individuals);
        }

        //Step1: After multi-case fitness evaluation, we need to use Lexicase selection to select ensemble
        if(state.generation > 0) {
            EnsembleRule[] ensembleRules = sortEnsembles(ensemblePool);
            ensemblePool = new ArrayList<>();
            for (int i = 0; i < ensembleElitism; i++) {
                ensembleRules[i].ElementFitnessClear();
                ensemblePool.add(ensembleRules[i]);
            }
            //step0: Check the ensemble offspring first
            System.out.println("ensembleOffspringPool.size(): " + ensembleOffspringPool.size());
            int numFromEnsembleOffspring = (int) (ensembleOffspringPro * (ensembleNumber[0] - ensembleElitism));
            System.out.println("ensembleNumber[0]: " + ensembleNumber[0]);
            System.out.println("ensembleElitism: " + ensembleElitism);
            System.out.println("ensembleOffspringPro: " + ensembleOffspringPro);
            System.out.println("numFromEnsembleOffspring: " + numFromEnsembleOffspring);
            //step0: Check the ensemble offspring first
            if (ensembleOffspringPool.size() > numFromEnsembleOffspring) {
                int permSize = (int) (numFromEnsembleOffspring);
                int[] permutation = new int[permSize];
                MOEADUtils.randomPermutation(state, 0, permutation, permSize);
                for (int x = 0; x < permSize; x++) {
                    int j = permutation[x];
                    ensembleOffspringPool.get(j).ElementFitnessClear();
                    ensemblePool.add(ensembleOffspringPool.get(j));
                }
                //modified
//                int permSizeLimit = (int) (numFromEnsembleOffspring);
//                int permSize = ensembleOffspringPool.size();
//                int[] permutation = new int[permSize];
//                MOEADUtils.randomPermutation(state, 0, permutation, permSize);
//                for (int x = 0; x < permSizeLimit; x++) {
//                    int j = permutation[x];
//                    ensembleOffspringPool.get(j).ElementFitnessClear();
//                    ensemblePool.add(ensembleOffspringPool.get(j));
//                }
            } else {
                for (int x = 0; x < ensembleOffspringPool.size(); x++) {
                    ensembleOffspringPool.get(x).ElementFitnessClear();
                    ensemblePool.add(ensembleOffspringPool.get(x));
                }
            }
        }
        if(state.generation > -1) {
            int tryTimes = ensembleSize[0] * 3;
            int subpopsLength = ensembleSize.length;
            for (int i = 0; i < subpopsLength; i++) {
                Individual[] individuals = state.population.subpops[i].individuals;
                for (int j = ensemblePool.size(); j < ensembleNumber[i]; j++) {
                    ArrayList<Individual> ensemble = new ArrayList<>();
                    ArrayList<Integer> elementOriginalIndex = new ArrayList<>();
                    List<List<Integer>> elementSelectedBasedOnCaseOrder = new ArrayList<>();

                    int tryTime = 0;
                    while (ensemble.size() < ensembleSize[i] && tryTime < tryTimes) {
                        if(tournamentSelectionForEnsemble){
                            int index = ensembleSelection.produce(i,state,0);
                            //avoid duplication
                            if(!elementOriginalIndex.contains(index)){
                                ensemble.add(individuals[index]);
                                elementOriginalIndex.add(index);
                            }
                        }
                        else{
                            int index = ensembleSelection.produce(i,state,0);
                            if(ensembleSelectionSimilarityCheck && !ensembleSelectionSimilarityCheckBasedonCorrelation && state.generation>((GPRuleEvolutionStateEnsembleContribution)state).genForUseLS){//added by mengxu 2023.03.13
                                //check similarity between this individual with all the elements in the ensemble
                                boolean different = true;
                                //might just calculate the top 5 cases
                                //todo: might not only consider diversity, but also need to consider the complementarity!!!!!!
                                List<Integer> indACasePermutation = ((EnsembleSelection)ensembleSelection).usedRandomInstanceIndex.subList(0,this.numCaseForSimilarityCheck);
//                        List<Integer> indACasePermutation = ensembleSelection.usedRandomInstanceIndex;
                                for(int e=0; e<elementSelectedBasedOnCaseOrder.size(); e++){
                                    List<Integer> indBCasePermutation = elementSelectedBasedOnCaseOrder.get(e);
                                    double similarity = similarityBasedOnCasePermutation(indACasePermutation, indBCasePermutation);
                                    if(similarity > 0){//0 here is a parameter need to do sensitivity analysis, which is about the similarity between elements in an ensemble
                                        different = false;
                                    }
                                }
//                            if(different){
//                                System.out.println("Case order same!!!");
//                            }
                                //avoid duplication
                                if(!elementOriginalIndex.contains(index) && different){
                                    ensemble.add(individuals[index]);
                                    elementOriginalIndex.add(index);
                                    elementSelectedBasedOnCaseOrder.add(indACasePermutation);
                                }
                            }
                            else if(ensembleSelectionSimilarityCheck && ensembleSelectionSimilarityCheckBasedonCorrelation && state.generation>((GPRuleEvolutionStateEnsembleContribution)state).genForUseLS){//added by mengxu 2023.03.13
                                //check similarity between this individual with all the elements in the ensemble
                                boolean different = true;
                                //might just calculate the top 5 cases
                                //todo: might not only consider diversity, but also need to consider the complementarity!!!!!!
                                List<Integer> indACasePermutation = ((EnsembleSelection)ensembleSelection).usedRandomInstanceIndex.subList(0,this.numCaseForSimilarityCheck);
//                        List<Integer> indACasePermutation = ensembleSelection.usedRandomInstanceIndex;
                                for(int e=0; e<elementSelectedBasedOnCaseOrder.size(); e++){
                                    List<Integer> indBCasePermutation = elementSelectedBasedOnCaseOrder.get(e);
                                    double similarity = similarityBasedOnCaseCorrelation(indACasePermutation, indBCasePermutation);
//                                    if(similarity > 0){
                                    if(similarity > 0.5){//0 here is a parameter need to do sensitivity analysis, which is about the similarity between elements in an ensemble
                                        different = false;
                                    }
                                }
//                            if(different){
//                                System.out.println("Case order same!!!");
//                            }
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
                        }
                        tryTime++;
                    }
                    EnsembleRule ensembleRule = new EnsembleRule(ensemble, elementOriginalIndex, ensemble.size());
                    ensemblePool.add(ensembleRule);
                }
            }

            //Step2: Ensemble fitness evaluation
            SchedulingSet schedulingSet = ((MultipleRuleEvaluationModel) ((MultipleTreeRuleOptimizationProblem) this.p_problem).getEvaluationModel()).getSchedulingSet();
            for (int i = 0; i < ensemblePool.size(); i++) {
                EnsembleRule ensemble = ensemblePool.get(i);
//                Fitness ensembleFitness = new MultiObjectiveFitness();
                OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution ensembleFitness = new OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution();
                List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) this.p_problem).getObjectives();
                ensembleFitness.setCurrentObjective(objectives);
                //todo: need to double check 2023.03.07
//                double mean = ((HeterogeneousSimulation)schedulingSet.getSimulations().get(0)).getInterArrivalTimeSamplerMean();
//                System.out.println("mean: " + mean);
                if(this.ensembleVoting){
                    ensemble.calcFitnessByVotingEnsembleAndMultiCaseFitnesssEvaluation(ensembleFitness, state, schedulingSet, objectives);
                }
                else if(this.ensembleMeanRank){
                    ensemble.calcFitnessByMeanRankEnsembleAndMultiCaseFitnesssEvaluation(ensembleFitness, state, schedulingSet, objectives);
                }
                else{
                    System.out.println("Error in EnsembleEvaluator.java!!!");
                }

                state.totalEvaluationTime += ensemble.getEnsembleSize();
//                ensemble.calcFitnessByVotingEnsemble(ensembleFitness, state, schedulingSet, objectives);
                //during this process, we need to calculate the ensemble contribution of each individual
            }

            //step3: print the best individual/ensemble
            int bestIdInd = 0;
            double bestFitness = Double.POSITIVE_INFINITY;
            Individual[] individuals = state.population.subpops[0].individuals;
            for (int i = 0; i < individuals.length; i++) {
                double fitness = ((OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution) individuals[i].fitness).fitness();
                if (fitness < Double.MAX_VALUE && fitness < Double.POSITIVE_INFINITY) {
//                System.out.println("Best ind fitness: " + fitness);
                }
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    bestIdInd = i;
                }
            }
            System.out.println("Best ind fitness: " + bestFitness);

            int bestIdEnsemble = -1;
            double[] bestEnsembleContribution = new double[ensembleSize[0]];
            for (int i = 0; i < ensemblePool.size(); i++) {
                EnsembleRule ensemble = ensemblePool.get(i);
                Fitness ensembleFitness = ensemble.getFitness();
//            Fitness ensembleFitness = ensemble.getEnsembleFitness();
                double fitness = ensembleFitness.fitness();
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    bestIdEnsemble = i;
                    bestEnsembleContribution = ensemble.getEnsembleContribution();
                }
            }
            if (bestIdEnsemble != -1) {
                System.out.println("Best ensemble fitness: " + bestFitness);
                System.out.print("Ensemble contribution: [");
                for (int i = 0; i < bestEnsembleContribution.length - 1; i++) {
                    System.out.print(bestEnsembleContribution[i] + ", ");
                }
                System.out.println(bestEnsembleContribution[bestEnsembleContribution.length - 1] + "]");
            }

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
    }

    public void calculateCorrelationBetweenCasesBasedOnTopNIndividuals(EvolutionState state, Individual[] individuals){
        PopulationUtils.sort(individuals);
        int N = (int)(state.population.subpops[0].individuals.length * this.percentageIndividualCorrelationCheck);
//        List<List<Double>> topNIndividualCaseFitnessAll = new ArrayList<>();
        OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution fitness = (OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)individuals[0].fitness;
        int numCase = fitness.multiInstanceMultiObjectiveFitness.length;
        double[][] topNIndividualCaseFitnessAll = new double[numCase][N];
//        for(int i=0; i<numCase; i++){
//            List<Double> topNIndividualCaseFitnessEach = new ArrayList<>();
//            topNIndividualCaseFitnessAll.add(topNIndividualCaseFitnessEach);
//        }

        int numIndexChangetoBadRun = -1;
        for(int i=0; i<N; i++){
            OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution fitness_i = (OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)individuals[i].fitness;
            double[][] multiInstanceMultiObjectiveFitness = fitness_i.multiInstanceMultiObjectiveFitness;
            if(multiInstanceMultiObjectiveFitness[0][0] >= Double.POSITIVE_INFINITY || multiInstanceMultiObjectiveFitness[0][0] >= Double.MAX_VALUE){
                numIndexChangetoBadRun = i;
                break;
            }
            else{
                for(int j=0; j<numCase; j++){
                    topNIndividualCaseFitnessAll[j][i] = multiInstanceMultiObjectiveFitness[j][0];
                }
            }
        }

        //now need to calculate the correlation

        for(int i=0; i<numCase-1; i++){
            double[] caseFitness_i = topNIndividualCaseFitnessAll[i];
//            System.out.println("N: " + N);
            if(numIndexChangetoBadRun != -1){
//                System.out.println("numIndexChangetoBadRun: " + numIndexChangetoBadRun);
                caseFitness_i = new double[numIndexChangetoBadRun];
                System.arraycopy(topNIndividualCaseFitnessAll[i],0,caseFitness_i,0,numIndexChangetoBadRun);
            }

            for(int j=i; j<numCase; j++){
                if(i == j){
                    correlationBetweenCases[i][j] = 1;
                }
                else{
                    double[] caseFitness_j = topNIndividualCaseFitnessAll[j];
                    if(numIndexChangetoBadRun != -1){
                        caseFitness_j = new double[numIndexChangetoBadRun];
                        System.arraycopy(topNIndividualCaseFitnessAll[j],0,caseFitness_j,0,numIndexChangetoBadRun);
                    }

                    PearsonsCorrelation p = new PearsonsCorrelation();
                    correlationBetweenCases[i][j] = p.correlation(caseFitness_i,caseFitness_j);
                    correlationBetweenCases[j][i] = correlationBetweenCases[i][j];
//                    System.out.println("p.correlation between case " + i + " and case " + j + ": " + correlationBetweenCases[i][j]);
                }
            }
        }
    }

    //added by mengxu 2023.03.13
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

    //added by mengxu 2023.03.24
    public double similarityBasedOnCaseCorrelation(List<Integer> indACasePermutation, List<Integer> indBCasePermutation){
        double similarity = 0;
        double importance = 1;
        for(int i=0; i<indACasePermutation.size(); i++){
            double currentSimilarity = this.correlationBetweenCases[indACasePermutation.get(i)][indBCasePermutation.get(i)];
            similarity += currentSimilarity * (importance - (double)i/(double)indACasePermutation.size());
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
