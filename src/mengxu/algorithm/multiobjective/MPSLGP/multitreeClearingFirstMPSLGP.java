package mengxu.algorithm.multiobjective.MPSLGP;

import yimei.jss.niching.*;

import ec.EvolutionState;
import ec.Individual;
import ec.Subpopulation;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MPSLGP.*;
import mengxu.util.qualityIndicator.GenerationalDistance;
import mengxu.util.qualityIndicator.Hypervolume;
import mengxu.util.qualityIndicator.InvertedGenerationalDistance;
import org.apache.commons.lang3.ArrayUtils;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The clearing method for niching.
 *
 * Created by fzhang on 2019.9.12.
 */
public class multitreeClearingFirstMPSLGP {

    static ArrayList<Integer> clearedIndsArray = new ArrayList<>();
    protected static long jobSeed;
    public static double[][][] phenotypicOfIntermedidatePop;
	// delete the poor individuals      radius: control the range of each niche   capacity: determines the number of individuals in each niche.
    public static void clearPopulation(final EvolutionState state,
                                       double radius, int capacity,
                                       PhenoCharacterisation[] pc) {
        //it is not correct, because it only contains one the pc of one subpopulation
        phenotypicOfIntermedidatePop = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(state, pc, true);//modified by mengxu 2023.03.30 to change false to true
//        phenotypicOfIntermedidatePop = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(state, pc, false); //fzhang 2019.9.26  add true here to make consistent

        for (int subpopNum = 0; subpopNum < state.population.subpops.length; subpopNum++) {
                int clearedInds = 0;
                Subpopulation subpop = state.population.subpops[subpopNum];
                Individual[] sortedPop = subpop.individuals;

            //fzhang 2018.10.2 calculate the distance of each individual and the reference rule
                List<double[]> sortedPopCharLists = new ArrayList<>();
                for (int i = 0;  i < phenotypicOfIntermedidatePop[subpopNum].length; i++) {
                    //where the examined rule set the chosen operation by reference rule  for example: 3. means examined rule set the chosen operation by reference rule as the third one
                    double[] charList = phenotypicOfIntermedidatePop[subpopNum][i];
                    sortedPopCharLists.add(charList);
                }

                // clear this subpopulation
                for (int i = 0; i < sortedPop.length; i++) {
                    // skip the cleared individuals
                    if (((Clearable)sortedPop[i].fitness).isCleared()) {
                        continue;
                    }

                    int numWinners = 1;
                    for (int j = i+1; j < sortedPop.length; j++) {
                        // skip the cleared individuals
                        if (((Clearable)sortedPop[j].fitness).isCleared()) {
                            continue;
                        }

                        // calculate the distance between individuals i and j
                        double distance = PhenoCharacterisation.distance(
                                sortedPopCharLists.get(i), sortedPopCharLists.get(j));
                        if (distance > radius) {
                            // Individual j is not in the niche
                            continue; //if distance, means two individuals are the same, clear (below) the individual, get out of current loop
                        }

                        if (numWinners < capacity) { //when set capacity to 1, the code will never go to here
                            numWinners ++;
                        }
                        else {
                            // Clear the fitness of individual j
                            ((Clearable)sortedPop[j].fitness).clear();
                            //fzhang 2019.9.11
                            sortedPop[j].evaluated = true;
                            clearedInds++;
                        }
                    }
                }
                clearedIndsArray.add(clearedInds);//save the number of cleared individuals of each subpop
                System.out.println("Cleared number: " + clearedInds);
        }

        //hidden by mengxu 2023.03.30, do not need to output
//        if(state.generation == state.numGenerations-1){ //do not need to breed in the last generation
//            jobSeed = ((GPRuleEvolutionStateBaseline)state).getJobSeed();
//            writeToFile(jobSeed, state.population.subpops.length);
//        }
    }

    public static void clearPopulationForParetoSetLearningBasedOnHV(final EvolutionState state,
                                                                    double radius, int capacity,
                                                                    PhenoCharacterisation[] pc,
                                                                    double[][] preferences) {
        //it is not correct, because it only contains one the pc of one subpopulation
        double[][][][] phenotypicOfIntermedidatePopPSL = phenotypicPopulationPreferenceFixedDecisions(state, preferences, pc, true);//modified by mengxu 2023.03.30 to change false to true
//        double[][][][] fitnessOfIntermedidatePopPSL = fitnessPopulationPreferenceFixedDecisions(state, preferences, phenotypicOfIntermedidatePopPSL);
        double[][][][] fitnessOfIntermedidatePopPSL = fitnessPopulationPreferenceFixedDecisionsConsiderAverageRank(state, preferences, phenotypicOfIntermedidatePopPSL);
        if(((GPRuleEvolutionStatePSL)state).useBroodRecombination){
            ((KNNsurrogateClearingPSLEvaluatorbasedonHV)state.evaluator).lastGenPhenotypicOfIntermedidatePopPSL = phenotypicOfIntermedidatePopPSL;
            ((KNNsurrogateClearingPSLEvaluatorbasedonHV)state.evaluator).lastGenFitnessOfIntermedidatePopPSL = fitnessOfIntermedidatePopPSL;
        }
        String compareCriteria = ((ClearingPSLMultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness).compareCriteria;

        double[][] HVOfIntermedidatePopPSL = null;
        double[][] GDOfIntermedidatePopPSL = null;
        double[][] IGDOfIntermedidatePopPSL = null;

        if(Objects.equals(compareCriteria, "HV") || Objects.equals(compareCriteria, "HV_noRank")){
            HVOfIntermedidatePopPSL = HVPopulationPreferenceDecisions(state, fitnessOfIntermedidatePopPSL);
            System.out.println("HVOfIntermedidatePopPSL: " + Arrays.stream(HVOfIntermedidatePopPSL[0]).summaryStatistics());
        }
        else if(Objects.equals(compareCriteria, "IGD") || Objects.equals(compareCriteria, "IGD_noRank")){
            IGDOfIntermedidatePopPSL = IGDPopulationPreferenceDecisions(state, fitnessOfIntermedidatePopPSL);
            System.out.println("IGDOfIntermedidatePopPSL: " + Arrays.stream(IGDOfIntermedidatePopPSL[0]).summaryStatistics());
        }
        else if(Objects.equals(compareCriteria, "GD") || Objects.equals(compareCriteria, "GD_noRank")){
            GDOfIntermedidatePopPSL = GDPopulationPreferenceDecisions(state, fitnessOfIntermedidatePopPSL);
            System.out.println("GDOfIntermedidatePopPSL: " + Arrays.stream(GDOfIntermedidatePopPSL[0]).summaryStatistics());
        }
        else if(Objects.equals(compareCriteria, "weight_sum")){
        }
        else{
            System.out.println("Error in multitreeClearingFirst");
        }

//        phenotypicOfIntermedidatePop = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(state, pc, false); //fzhang 2019.9.26  add true here to make consistent
        double[][] preferenceDiversityPopAll = updatePSLFitnessWithPreferenceDiversityPopulationForParetoSetLearning(state,phenotypicOfIntermedidatePopPSL);
        System.out.println("preferenceDiversityPopAll: " + Arrays.stream(preferenceDiversityPopAll[0]).summaryStatistics());
        //add by mengxu to calculate the number of dominatedby
//        double[][] meanDominatedByOfIntermedidatePopPSL = meanDominatedByPopulation(state, fitnessOfIntermedidatePopPSL);

        for (int subpopNum = 0; subpopNum < state.population.subpops.length; subpopNum++) {
            int clearedInds = 0;
            Subpopulation subpop = state.population.subpops[subpopNum];
            Individual[] sortedPop = subpop.individuals;
            //sort the pop based on PSLfitness and preference diversity
//            double[] PSLFitnessWithPreferenceDiversity = new double[sortedPop.length];
//            sortPopBasedOnMeanPSLFitness(sortedPop, phenotypicOfIntermedidatePopPSL[0], HVOfIntermedidatePopPSL[0], fitnessOfIntermedidatePopPSL[0], preferenceDiversityPopAll[0]);
//            sortPopBasedOnHV(sortedPop, phenotypicOfIntermedidatePopPSL[0], HVOfIntermedidatePopPSL[0], fitnessOfIntermedidatePopPSL[0], preferenceDiversityPopAll[0]);
            //update the PSLfitness using the -HV, modified by mengxu 2024.5.20
            for(int i=0; i<sortedPop.length; i++){
//                ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).calculateMeanDirectionAlignment(state,fitnessOfIntermedidatePopPSL[0][i]); //add by mengxu 2024.8.13
//                ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).calculateMeanDirectionAlignment(state,fitnessOfIntermedidatePopPSL[0][i], preferences); //add by mengxu 2024.8.13

                if(Objects.equals(compareCriteria, "HV") || Objects.equals(compareCriteria, "HV_noRank")){
                    ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).setHVvalue(HVOfIntermedidatePopPSL[0][i]);
                }
                else if(Objects.equals(compareCriteria, "IGD") || Objects.equals(compareCriteria, "IGD_noRank")){
                    ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).setIGDvalue(IGDOfIntermedidatePopPSL[0][i]);
                }
                else if(Objects.equals(compareCriteria, "GD") || Objects.equals(compareCriteria, "GD_noRank")){
                    ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).setGDvalue(GDOfIntermedidatePopPSL[0][i]);
                }
                else if(Objects.equals(compareCriteria, "weight_sum")){
                }
                else{
                    System.out.println("Error in multitreeClearingFirst");
                }

//                ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).setMeanDominatedBy(meanDominatedByOfIntermedidatePopPSL[0][i]);
                double mean_performance = 0;
                double mean_preferenceRank = 0;
                for(int j=0; j<fitnessOfIntermedidatePopPSL[0][i].length; j++){
                    mean_performance += fitnessOfIntermedidatePopPSL[0][i][j][2];
                    mean_preferenceRank += fitnessOfIntermedidatePopPSL[0][i][j][3];
                }
                mean_performance = mean_performance/fitnessOfIntermedidatePopPSL[0][i].length;
                mean_preferenceRank = mean_preferenceRank/fitnessOfIntermedidatePopPSL[0][i].length;
                ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).setPSLFitness(mean_performance);
                ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).setMeanPreferenceRank(mean_preferenceRank);
                ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).setPreferenceDiversity(preferenceDiversityPopAll[0][i]);
            }

            sortPopBasedOnTheSameBetterThan(sortedPop, phenotypicOfIntermedidatePopPSL[0]);
//            System.out.println("PSLFitnessWithPreferenceDiversity: " + Arrays.stream(PSLFitnessWithPreferenceDiversity).summaryStatistics());
            //todo: need to double check whether these vector has been changed when sorting, they should be changed
            //      by meng xu 2024.3.20

            //fzhang 2018.10.2 calculate the distance of each individual and the reference rule
            List<double[]> sortedPopCharLists = new ArrayList<>();
            for (int i = 0;  i < phenotypicOfIntermedidatePopPSL[subpopNum].length; i++) {
                //where the examined rule set the chosen operation by reference rule  for example: 3. means examined rule set the chosen operation by reference rule as the third one
                double[] charList = phenotypicOfIntermedidatePopPSL[subpopNum][i][0];
                sortedPopCharLists.add(charList);
            }

            boolean clear = ((GPRuleEvolutionStatePSL)state).doNichingClear;
            if(clear){
                // clear this subpopulation
                for (int i = 0; i < sortedPop.length; i++) {
                    // skip the cleared individuals
                    if (((Clearable)sortedPop[i].fitness).isCleared()) {
                        continue;
                    }

                    int numWinners = 1;
                    for (int j = i+1; j < sortedPop.length; j++) {
                        // skip the cleared individuals
                        if (((Clearable)sortedPop[j].fitness).isCleared()) {
                            continue;
                        }

                        // calculate the distance between individuals i and j
                        double distance = PhenoCharacterisation.distance(
                                sortedPopCharLists.get(i), sortedPopCharLists.get(j));
                        if (distance > radius) {
                            // Individual j is not in the niche
                            continue; //if distance, means two individuals are the same, clear (below) the individual, get out of current loop
                        }

                        if (numWinners < capacity) { //when set capacity to 1, the code will never go to here
                            numWinners ++;
                        }
                        else {
                            // Clear the fitness of individual j
                            ((Clearable)sortedPop[j].fitness).clear();
                            //fzhang 2019.9.11
                            sortedPop[j].evaluated = true;
                            clearedInds++;
                        }
                    }
                }
                clearedIndsArray.add(clearedInds);//save the number of cleared individuals of each subpop
                System.out.println("Cleared number: " + clearedInds);
            }

            state.population.subpops[subpopNum].individuals = sortedPop;//add by mengxu 2024.5.20
        }

    }

    /**
     add by mengxu 2024.3.20 for Pareto set learning
     to penalty individuals with poor (Tchebycheff fitness + preference diversity)
     here, the preference diversity is defined as the how many preferences give different PC, we test 10 preferences here
     */
    public static void clearPopulationForParetoSetLearning(final EvolutionState state,
                                                           double radius, int capacity,
                                                           PhenoCharacterisation[] pc,
                                                           double[][] preferences) {
        //it is not correct, because it only contains one the pc of one subpopulation
        double[][][][] phenotypicOfIntermedidatePopPSL = phenotypicPopulationPreferenceFixedDecisions(state, preferences, pc,true);//modified by mengxu 2023.03.30 to change false to true
        double[][][][] fitnessOfIntermedidatePopPSL = fitnessPopulationPreferenceFixedDecisions(state, preferences, phenotypicOfIntermedidatePopPSL);
//        double[][] HVOfIntermedidatePopPSL = HVPopulationPreferenceFixedDecisions(state, fitnessOfIntermedidatePopPSL);
//        phenotypicOfIntermedidatePop = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(state, pc, false); //fzhang 2019.9.26  add true here to make consistent
        double[][] preferenceDiversityPopAll = updatePSLFitnessWithPreferenceDiversityPopulationForParetoSetLearning(state,phenotypicOfIntermedidatePopPSL);
        System.out.println("preferenceDiversityPopAll: " + Arrays.stream(preferenceDiversityPopAll[0]).summaryStatistics());

//        if(state.generation==30){
//            System.out.println("Finish fitness estimation!");
//        }

        for (int subpopNum = 0; subpopNum < state.population.subpops.length; subpopNum++) {
            int clearedInds = 0;
            Subpopulation subpop = state.population.subpops[subpopNum];
            Individual[] sortedPop = subpop.individuals;
            //sort the pop based on PSLfitness and preference diversity
            double[] PSLFitnessWithPreferenceDiversity = new double[sortedPop.length];
            sortPop(sortedPop, phenotypicOfIntermedidatePopPSL[0], fitnessOfIntermedidatePopPSL[0], preferenceDiversityPopAll[0], PSLFitnessWithPreferenceDiversity);

//            for(int i=0; i<sortedPop.length; i++){
//                ((ClearingPSLMultiObjectiveFitness)sortedPop[i].fitness).setPSLFitness(PSLFitnessWithPreferenceDiversity[i]);
//            }

            System.out.println("PSLFitnessWithPreferenceDiversity: " + Arrays.stream(PSLFitnessWithPreferenceDiversity).summaryStatistics());
            //todo: need to double check whether these vector has been changed when sorting, they should be changed
            //      by meng xu 2024.3.20

            //fzhang 2018.10.2 calculate the distance of each individual and the reference rule
            List<double[]> sortedPopCharLists = new ArrayList<>();
            for (int i = 0;  i < phenotypicOfIntermedidatePopPSL[subpopNum].length; i++) {
                //where the examined rule set the chosen operation by reference rule  for example: 3. means examined rule set the chosen operation by reference rule as the third one
                double[] charList = phenotypicOfIntermedidatePopPSL[subpopNum][i][0];
                sortedPopCharLists.add(charList);
            }

            // clear this subpopulation
            for (int i = 0; i < sortedPop.length; i++) {
                // skip the cleared individuals
                if (((Clearable)sortedPop[i].fitness).isCleared()) {
                    continue;
                }

                int numWinners = 1;
                for (int j = i+1; j < sortedPop.length; j++) {
                    // skip the cleared individuals
                    if (((Clearable)sortedPop[j].fitness).isCleared()) {
                        continue;
                    }

                    // calculate the distance between individuals i and j
                    double distance = PhenoCharacterisation.distance(
                            sortedPopCharLists.get(i), sortedPopCharLists.get(j));
                    if (distance > radius) {
                        // Individual j is not in the niche
                        continue; //if distance, means two individuals are the same, clear (below) the individual, get out of current loop
                    }

                    if (numWinners < capacity) { //when set capacity to 1, the code will never go to here
                        numWinners ++;
                    }
                    else {
                        // Clear the fitness of individual j
                        ((Clearable)sortedPop[j].fitness).clear();
                        //fzhang 2019.9.11
                        sortedPop[j].evaluated = true;
                        clearedInds++;
                    }
                }
            }
            clearedIndsArray.add(clearedInds);//save the number of cleared individuals of each subpop
            System.out.println("Cleared number: " + clearedInds);
            state.population.subpops[subpopNum].individuals = sortedPop;//add by mengxu 2024.5.20
        }

    }

    public static double[][] HVPopulationPreferenceDecisions(final EvolutionState state,
                                                                  double[][][][] fitnessOfIntermedidatePopPSL){
        int HVEstimateStrategy = ((GPRuleEvolutionStatePSL)state).HVEstimateStrategy;
        double[] maxObj = ((PSLInitializer) state.initializer).maxObjectives;
        double[] minObj = ((PSLInitializer) state.initializer).minObjectives;
        int numObj = minObj.length;
        double[] manualObj = new double[maxObj.length];
        //todo: need modify to fit different normalisation strategy, for examply ideal-nadir point normalisation instead of only manual normalisation
        //      2024.9.19
        if(((PSLInitializer) state.initializer).normalisation==1 || ((PSLInitializer) state.initializer).normalisation==2){
            for(int i=0; i< maxObj.length; i++){
                manualObj[i] = ((PSLInitializer) state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
            }
        }
        double[][] HV = new double[fitnessOfIntermedidatePopPSL.length][fitnessOfIntermedidatePopPSL[0].length];
        for (int subpop = 0; subpop < fitnessOfIntermedidatePopPSL.length; subpop++) {
            for (int ind = 0; ind < fitnessOfIntermedidatePopPSL[0].length; ind++){
//                double[][] nonDup = clearDuplicatedPoint(fitnessOfIntermedidatePopPSL[subpop][ind]);
                double[][] nonDup = getNonDominatedPoints(fitnessOfIntermedidatePopPSL[subpop][ind],numObj);//modified by mengxu 2024.5.27
                //todo: should get the non-dominated results
                Hypervolume HVIndicator = new Hypervolume();
                if(HVEstimateStrategy==0 || HVEstimateStrategy==2){//max-min normalisation
                    double value = HVIndicator.hypervolume_MaxMin(nonDup, maxObj, minObj);
//                double value = HVIndicator.hypervolume(nonDup, trueParetofront,2);
                    HV[subpop][ind] = value;
                }
                else if(HVEstimateStrategy==1 && (((PSLInitializer) state.initializer).normalisation==1 || ((PSLInitializer) state.initializer).normalisation==2)){//manual rule normalisation
                    double value = HVIndicator.hypervolume_Manual(nonDup, manualObj);
                    HV[subpop][ind] = value;
                }
            }
        }
        return HV;
    }

    public static double[][] IGDPopulationPreferenceDecisions(final EvolutionState state,
                                                              double[][][][] fitnessOfIntermedidatePopPSL) {
        // Retrieve the true Pareto front and other necessary parameters from the state
        double[][] trueParetoFront = ((KNNsurrogateClearingPSLEvaluatorbasedonHV)
                ((GPRuleEvolutionStatePSL) state).evaluator).trueParetofront;
        int HVEstimateStrategy = ((GPRuleEvolutionStatePSL)state).HVEstimateStrategy;
        double[] maxObj = ((PSLInitializer) state.initializer).maxObjectives;
        double[] minObj = ((PSLInitializer) state.initializer).minObjectives;
        int numObj = minObj.length;

        // Manual objectives initialization
        double[] manualObj = new double[maxObj.length];
        if (((PSLInitializer) state.initializer).normalisation == 1 ||
                ((PSLInitializer) state.initializer).normalisation == 2) {
            for (int i = 0; i < maxObj.length; i++) {
                manualObj[i] = ((PSLInitializer) state.initializer)
                        .curSchedulingSetObjectiveLowerBoundMtx.getEntry(i, 0);
            }
        }

        // IGD calculation array
        double[][] IGDValues = new double[fitnessOfIntermedidatePopPSL.length][fitnessOfIntermedidatePopPSL[0].length];

        for (int subpop = 0; subpop < fitnessOfIntermedidatePopPSL.length; subpop++) {
            for (int ind = 0; ind < fitnessOfIntermedidatePopPSL[0].length; ind++) {
                // Get non-dominated points
//                double[][] nonDup = getNonDominatedPoints(fitnessOfIntermedidatePopPSL[subpop][ind], numObj);

                // Get all points
                double[][] allPoints = fitnessOfIntermedidatePopPSL[subpop][ind];
                // todo: require normalisation

                // IGD calculation based on true Pareto front and non-dominated points
                InvertedGenerationalDistance IGDCalculator = new InvertedGenerationalDistance();
                if(HVEstimateStrategy==0 || HVEstimateStrategy==2) {//max-min normalisation
                    double igdValue = IGDCalculator.invertedGenerationalDistance_MaxMin(
                            state, allPoints, trueParetoFront, numObj);
                    IGDValues[subpop][ind] = igdValue;
                }
                else if(HVEstimateStrategy==1 && (((PSLInitializer) state.initializer).normalisation==1 || ((PSLInitializer) state.initializer).normalisation==2)){//manual rule normalisation
                    double igdValue = IGDCalculator.invertedGenerationalDistance_Manual(
                            state, allPoints, trueParetoFront, manualObj, numObj);
                    IGDValues[subpop][ind] = igdValue;
                }
            }
        }

        return IGDValues;
    }

    public static double[][] GDPopulationPreferenceDecisions(final EvolutionState state,
                                                              double[][][][] fitnessOfIntermedidatePopPSL) {
        // Retrieve the true Pareto front and other necessary parameters from the state
        double[][] trueParetoFront = ((KNNsurrogateClearingPSLEvaluatorbasedonHV)
                ((GPRuleEvolutionStatePSL) state).evaluator).trueParetofront;
        int HVEstimateStrategy = ((GPRuleEvolutionStatePSL)state).HVEstimateStrategy;
        double[] maxObj = ((PSLInitializer) state.initializer).maxObjectives;
        double[] minObj = ((PSLInitializer) state.initializer).minObjectives;
        int numObj = minObj.length;

        // Manual objectives initialization
        double[] manualObj = new double[maxObj.length];
        if (((PSLInitializer) state.initializer).normalisation == 1 ||
                ((PSLInitializer) state.initializer).normalisation == 2) {
            for (int i = 0; i < maxObj.length; i++) {
                manualObj[i] = ((PSLInitializer) state.initializer)
                        .curSchedulingSetObjectiveLowerBoundMtx.getEntry(i, 0);
            }
        }

        // IGD calculation array
        double[][] IGDValues = new double[fitnessOfIntermedidatePopPSL.length][fitnessOfIntermedidatePopPSL[0].length];

        for (int subpop = 0; subpop < fitnessOfIntermedidatePopPSL.length; subpop++) {
            for (int ind = 0; ind < fitnessOfIntermedidatePopPSL[0].length; ind++) {
                // Get non-dominated points
//                double[][] nonDup = getNonDominatedPoints(fitnessOfIntermedidatePopPSL[subpop][ind], numObj);

                // Get all points
                double[][] allPoints = fitnessOfIntermedidatePopPSL[subpop][ind];
                // todo: require normalisation

                // IGD calculation based on true Pareto front and non-dominated points
                GenerationalDistance GDCalculator = new GenerationalDistance();
                if(HVEstimateStrategy==0 || HVEstimateStrategy==2) {//max-min normalisation
                    double igdValue = GDCalculator.generationalDistance_MaxMin(
                            state, allPoints, trueParetoFront, numObj);
                    IGDValues[subpop][ind] = igdValue;
                }
                else if(HVEstimateStrategy==1 && (((PSLInitializer) state.initializer).normalisation==1 || ((PSLInitializer) state.initializer).normalisation==2)){//manual rule normalisation
                    double igdValue = GDCalculator.generationalDistance_Manual(
                            state, allPoints, trueParetoFront, manualObj, numObj);
                    IGDValues[subpop][ind] = igdValue;
                }
            }
        }

        return IGDValues;
    }


    public static boolean dominatedBy(double[] ind1, double[] ind2){
        boolean dominatedBy = true;
        boolean same = true;
        for(int i=0; i<ind2.length; i++){
            if(ind2[i] != ind1[i]){
                same = false;
                break;
            }
        }
        if(same){
            return false;
        }
        for(int i=0; i<ind2.length; i++){
            if(ind2[i] > ind1[i]) {
                dominatedBy = false;
                break;
            }
        }
        return dominatedBy;
    }

    //add by mengxu 2024.7.16 todo: need double check 2024.7.16
    public static double[][] meanDominatedByPopulation(final EvolutionState state,
                                                       double[][][][] fitnessOfIntermedidatePopPSL){
        double[][] meanDominatedBy = new double[fitnessOfIntermedidatePopPSL.length][fitnessOfIntermedidatePopPSL[0].length];
        for (int subpop = 0; subpop < fitnessOfIntermedidatePopPSL.length; subpop++) {
            for (int ind = 0; ind < fitnessOfIntermedidatePopPSL[subpop].length; ind++){
                for(int pre=0; pre < fitnessOfIntermedidatePopPSL[subpop][ind].length; pre++){
                    double[] ind_fitness_pre = fitnessOfIntermedidatePopPSL[subpop][ind][pre];
                    for(int ind2=0; ind2 < fitnessOfIntermedidatePopPSL[subpop].length; ind2++){
                        if(ind != ind2){
                            double[] ind2_fitness_pre = fitnessOfIntermedidatePopPSL[subpop][ind2][pre];
                            if(dominatedBy(ind_fitness_pre, ind2_fitness_pre)){
                                meanDominatedBy[subpop][ind] += 1;
                            }
                        }
                    }
                }
                meanDominatedBy[subpop][ind] = meanDominatedBy[subpop][ind]/fitnessOfIntermedidatePopPSL[subpop][ind].length;
            }
        }
        return meanDominatedBy;
    }

    public static double[][] clearDuplicatedPoint(double[][] objectives){
        ArrayList<double[]> objectivesArray = new ArrayList<>();
        for (int i = 0; i < objectives.length; i++){
            objectivesArray.add(objectives[i]);
        }

        // clear this subpopulation
        for (int i = 0; i < objectivesArray.size(); i++) {
            for (int j = i+1; j < objectivesArray.size(); j++) {
                if(objectivesArray.get(i)[0] == objectivesArray.get(j)[0] && objectivesArray.get(i)[1] == objectivesArray.get(j)[1]){
                    objectivesArray.remove(j);
                    j--;
                }
            }
        }
        double[][] nonDuplication = new double[objectivesArray.size()][2];
        for(int i=0; i<objectivesArray.size(); i++){
            nonDuplication[i][0] = objectivesArray.get(i)[0];
            nonDuplication[i][1] = objectivesArray.get(i)[1];
        }
        return nonDuplication;
    }

    //by mengxu 2024.5.27

    public static double[][] getNonDominatedPoints(double[][] points, int numObj) {
//        double[][] points = clearDuplicatedPoint(dupPoints);
        ArrayList<double[]> paretoFrontList = new ArrayList<>();

        for (int i = 0; i < points.length; i++) {
            boolean isDominated = false;
            for (int j = 0; j < points.length; j++) {
                if (i != j && dominates(points[j], points[i], numObj)) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                boolean dup = false;
                for(int c=0; c<paretoFrontList.size(); c++){
                    if(points[i][0] == paretoFrontList.get(c)[0] && points[i][1] == paretoFrontList.get(c)[1]){
                        dup = true;
                    }
                }
                if(!dup){
                    paretoFrontList.add(points[i]);
                }
            }
        }

        // Convert ArrayList to double[][]
        double[][] paretoFront = new double[paretoFrontList.size()][numObj];
        for (int i = 0; i < paretoFrontList.size(); i++) {
            paretoFront[i][0] = paretoFrontList.get(i)[0];
            paretoFront[i][1] = paretoFrontList.get(i)[1];
        }

        return paretoFront;
    }

    public static boolean dominates(double[] pointA, double[] pointB, int numObj) {
        boolean strictlyBetter = false;
        for (int i = 0; i < numObj; i++) {
            if (pointA[i] > pointB[i]) {
                return false;
            } else if (pointA[i] < pointB[i]) {
                strictlyBetter = true;
            }
        }
        return strictlyBetter;
    }

    public static double calculatePSLFitnessWithPreferencesAndObjectives(EvolutionState state, double[] preferences, double[] objectives)
    {
        int index = state.generation;

        PSLInitializer init = (PSLInitializer) state.initializer;
        double fit;
        if(init.tchebycheff == 3){//augmented Tchebtcheff
            fit = init.calculatePenaltyBoundaryIntersectionScoreWithPreferencesAndObjectives(preferences, objectives);
        }
        else if(init.tchebycheff == 2){//augmented Tchebtcheff
            fit = init.calculateAugmentedTchebycheffScoreWithPreferencesAndObjectives(preferences, objectives);
        }
        else if (init.tchebycheff == 1) {//Tchebtcheff
            fit = init.calculateTchebycheffScoreWithPreferencesAndObjectives(preferences, objectives);
        } else {//weighted sum
            fit = init.calculateWeightSumScoreWithPreferencesAndObjectives(preferences, objectives);
        }
        return fit;
    }

    public static double[][][][] fitnessPopulationPreferenceFixedDecisions(final EvolutionState state, double[][] preferences,
                                                                           double[][][][] phenotypicOfIntermedidatePopPSL){
        double[][][][] indsFitnessesMultiTree = new double[phenotypicOfIntermedidatePopPSL.length][phenotypicOfIntermedidatePopPSL[0].length][phenotypicOfIntermedidatePopPSL[0][0].length][3];
        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            double[][][] indsPCsSubpop = phenotypicOfIntermedidatePopPSL[subpop];
            for(int i=0; i<indsPCsSubpop.length; i++){
                double[] objectives = ((PSLMultiObjectiveFitness)inds[i].fitness).objectives;
                double PSLFitness = ((PSLMultiObjectiveFitness)inds[i].fitness).getPSLFitness();
                double[][] eachIndPCsSubpop = indsPCsSubpop[i];
                for(int j=0; j< eachIndPCsSubpop.length; j++){
                    if(j==0){
                        indsFitnessesMultiTree[subpop][i][j][0] = objectives[0];
                        indsFitnessesMultiTree[subpop][i][j][1] = objectives[1];
                        indsFitnessesMultiTree[subpop][i][j][2] = PSLFitness;
                    }
                    else{
                        double[] pc_i_j = eachIndPCsSubpop[j];
                        double min_dis = Double.MAX_VALUE;
                        int min_index = -1;
                        for(int m=0; m<indsPCsSubpop.length; m++){
                            double[] pc_m_0 = indsPCsSubpop[m][0];
                            double dis  = PhenoCharacterisation.distance(pc_i_j, pc_m_0);
                            if(dis==0){
                                min_dis = dis;
                                min_index = m;
                                break;
                            }
                            if(dis < min_dis){
                                min_dis = dis;
                                min_index = m;
                            }
                        }
                        double[] objectives_min_index = ((PSLMultiObjectiveFitness)inds[min_index].fitness).objectives;
                        double[] preference = preferences[j];
                        indsFitnessesMultiTree[subpop][i][j][0] = objectives_min_index[0];
                        indsFitnessesMultiTree[subpop][i][j][1] = objectives_min_index[1];
                        indsFitnessesMultiTree[subpop][i][j][2] = calculatePSLFitnessWithPreferencesAndObjectives(state, preference, objectives_min_index);
                    }
                }
            }
        }
        return indsFitnessesMultiTree;
    }


    public static double[][][][] fitnessPopulationPreferenceFixedDecisionsConsiderAverageRank(final EvolutionState state, double[][] preferences,
                                                                                            double[][][][] phenotypicOfIntermedidatePopPSL){
        double[][][][] indsFitnessesMultiTree = new double[phenotypicOfIntermedidatePopPSL.length][phenotypicOfIntermedidatePopPSL[0].length][phenotypicOfIntermedidatePopPSL[0][0].length][4];
        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            double[][][] indsPCsSubpop = phenotypicOfIntermedidatePopPSL[subpop];
            for(int i=0; i<indsPCsSubpop.length; i++){
                double[] objectives = ((PSLMultiObjectiveFitness)inds[i].fitness).objectives;
                double PSLFitness = ((PSLMultiObjectiveFitness)inds[i].fitness).getPSLFitness();
                double rank = ((ClearingPSLMultiObjectiveFitness)inds[i].fitness).getRank();
                double[][] eachIndPCsSubpop = indsPCsSubpop[i];
                for(int j=0; j< eachIndPCsSubpop.length; j++){
                    if(j==0){
                        indsFitnessesMultiTree[subpop][i][j][0] = objectives[0];
                        indsFitnessesMultiTree[subpop][i][j][1] = objectives[1];
                        indsFitnessesMultiTree[subpop][i][j][2] = PSLFitness;
                        indsFitnessesMultiTree[subpop][i][j][3] = rank;
                    }
                    else{
                        double[] pc_i_j = eachIndPCsSubpop[j];
                        double min_dis = Double.MAX_VALUE;
                        int min_index = -1;
                        for(int m=0; m<indsPCsSubpop.length; m++){
                            double[] pc_m_0 = indsPCsSubpop[m][0];
                            double dis  = PhenoCharacterisation.distance(pc_i_j, pc_m_0);
                            if(dis==0){
                                min_dis = dis;
                                min_index = m;
                                break;
                            }
                            if(dis < min_dis){
                                //todo: should rank the population first before here? 2024.8.12
                                //todo: should double check the fitness estimation and HV calculation 2024.8.12
                                min_dis = dis;
                                min_index = m;
                            }
                        }
                        double[] objectives_min_index = ((PSLMultiObjectiveFitness)inds[min_index].fitness).objectives;
                        double rank_min_index = ((ClearingPSLMultiObjectiveFitness)inds[min_index].fitness).getRank();
                        double[] preference = preferences[j];
                        indsFitnessesMultiTree[subpop][i][j][0] = objectives_min_index[0];
                        indsFitnessesMultiTree[subpop][i][j][1] = objectives_min_index[1];
                        indsFitnessesMultiTree[subpop][i][j][2] = calculatePSLFitnessWithPreferencesAndObjectives(state, preference, objectives_min_index);
                        indsFitnessesMultiTree[subpop][i][j][3] = rank_min_index;
                    }
                }
            }
        }
        return indsFitnessesMultiTree;
    }


    /**
     * sort the pop based on PSLfitness and preference diversity by mengxu 2024.3.20
     * @param pop
     * @param phenotypicOfIntermedidatePopPSL
     * @param preferenceDiversityPopAll
     */
    public static void sortPop(Individual[] pop, double[][][] phenotypicOfIntermedidatePopPSL,
                               double[][][] fitnessOfIntermedidatePopPSL, double[] preferenceDiversityPopAll, double[] PSLFitnessWithPreferenceDiversity){
        for(int i=0; i<pop.length; i++){
            double mean_performance = 0;
            for(int j=0; j<fitnessOfIntermedidatePopPSL[i].length; j++){
                mean_performance += fitnessOfIntermedidatePopPSL[i][j][2];
            }
            mean_performance = mean_performance/fitnessOfIntermedidatePopPSL[i].length;
//            ((PSLMultiObjectiveFitness)pop[i].fitness).setPSLFitness(mean_performance); //todo: add by mengxu, need to check whether to add this
//            System.out.println("mean_performance: " + mean_performance);
            //todo: need to check whether there is a bad run among the preferences
            double newFit = 0.8*mean_performance - 0.2*preferenceDiversityPopAll[i];
            PSLFitnessWithPreferenceDiversity[i] = newFit;
            ((PSLMultiObjectiveFitness)pop[i].fitness).setPSLFitness(newFit); //todo: add by mengxu, need to check whether to add this
        }

        for(int i=0; i<pop.length-1; i++){
            for(int j=0; j< pop.length-1-i; j++){
                if(PSLFitnessWithPreferenceDiversity[j] > PSLFitnessWithPreferenceDiversity[j+1]){
                    double tempFit = PSLFitnessWithPreferenceDiversity[j];
                    Individual tempInd = (Individual) pop[j].clone();
                    double[][] tempPhenotypicOfIntermedidatePopPSL = phenotypicOfIntermedidatePopPSL[j].clone();
                    double tempPreferenceDiversityPopAll = preferenceDiversityPopAll[j];

                    PSLFitnessWithPreferenceDiversity[j] = PSLFitnessWithPreferenceDiversity[j+1];
                    pop[j] = (Individual) pop[j+1].clone();//todo: need to check whether clone will lose some information? 2024.5.14
                    phenotypicOfIntermedidatePopPSL[j] = phenotypicOfIntermedidatePopPSL[j+1].clone();
                    preferenceDiversityPopAll[j] = preferenceDiversityPopAll[j+1];

                    PSLFitnessWithPreferenceDiversity[j+1] = tempFit;
                    pop[j+1] = tempInd;
                    phenotypicOfIntermedidatePopPSL[j+1] = tempPhenotypicOfIntermedidatePopPSL;
                    preferenceDiversityPopAll[j+1] = tempPreferenceDiversityPopAll;
                }
            }
        }
    }

    /**
     * sort the pop based on PSLfitness and preference diversity by mengxu 2024.3.20
     * @param pop
     * @param phenotypicOfIntermedidatePopPSL
     * @param HVOfIntermedidatePopPSL
     */
    public static void sortPopBasedOnHV(Individual[] pop, double[][][] phenotypicOfIntermedidatePopPSL,
                                        double[] HVOfIntermedidatePopPSL, double[][][] fitnessOfIntermedidatePopPSL,
                                        double[] preferenceDiversityPopAll){
        for(int i=0; i<pop.length; i++){
            double mean_performance = 0;
            for(int j=0; j<fitnessOfIntermedidatePopPSL[i].length; j++){
                mean_performance += fitnessOfIntermedidatePopPSL[i][j][2];
            }
            mean_performance = mean_performance/fitnessOfIntermedidatePopPSL[i].length;
//            ((PSLMultiObjectiveFitness)pop[i].fitness).setPSLFitness(mean_performance); //todo: add by mengxu, need to check whether to add this
//            System.out.println("mean_performance: " + mean_performance);
            //todo: need to check whether there is a bad run among the preferences
            double newFit = 0.8*mean_performance - 0.2*preferenceDiversityPopAll[i];
            ((PSLMultiObjectiveFitness)pop[i].fitness).setPSLFitness(newFit); //todo: add by mengxu, need to check whether to add this
        }

        for(int i=0; i<pop.length-1; i++){
            for(int j=0; j< pop.length-1-i; j++){
                //for HV, the bigger the better
                if(HVOfIntermedidatePopPSL[j] < HVOfIntermedidatePopPSL[j+1]){
                    double tempHV = HVOfIntermedidatePopPSL[j];
                    Individual tempInd = (Individual) pop[j].clone();
                    double[][] tempPhenotypicOfIntermedidatePopPSL = phenotypicOfIntermedidatePopPSL[j].clone();
                    double[][] tempFitnessOfIntermedidatePopPSL = fitnessOfIntermedidatePopPSL[j].clone();
                    double tempPreferenceDiversityPopAll = preferenceDiversityPopAll[j];

                    HVOfIntermedidatePopPSL[j] = HVOfIntermedidatePopPSL[j+1];
                    pop[j] = (Individual) pop[j+1].clone();//todo: need to check whether clone will lose some information? 2024.5.14
                    phenotypicOfIntermedidatePopPSL[j] = phenotypicOfIntermedidatePopPSL[j+1].clone();
                    fitnessOfIntermedidatePopPSL[j] = fitnessOfIntermedidatePopPSL[j+1].clone();
                    preferenceDiversityPopAll[j] = preferenceDiversityPopAll[j+1];

                    HVOfIntermedidatePopPSL[j+1] = tempHV;
                    pop[j+1] = tempInd;
                    phenotypicOfIntermedidatePopPSL[j+1] = tempPhenotypicOfIntermedidatePopPSL;
                    fitnessOfIntermedidatePopPSL[j+1] = tempFitnessOfIntermedidatePopPSL;
                    preferenceDiversityPopAll[j+1] = tempPreferenceDiversityPopAll;
                }
            }
        }
    }

    /**
     * sort the pop based on mean PSLfitness by mengxu 2024.3.20
     * @param pop
     * @param phenotypicOfIntermedidatePopPSL
     */
    public static void sortPopBasedOnTheSameBetterThan(Individual[] pop, double[][][] phenotypicOfIntermedidatePopPSL){
        for(int i=0; i<pop.length-1; i++){
            for(int j=0; j< pop.length-1-i; j++){
                if(!pop[j].fitness.betterThan(pop[j+1].fitness)){
                    Individual tempInd = (Individual) pop[j].clone();
                    double[][] tempPhenotypicOfIntermedidatePopPSL = phenotypicOfIntermedidatePopPSL[j].clone();

                    pop[j] = (Individual) pop[j+1].clone();//todo: need to check whether clone will lose some information? 2024.5.14
                    phenotypicOfIntermedidatePopPSL[j] = phenotypicOfIntermedidatePopPSL[j+1].clone();

                    pop[j+1] = tempInd;
                    phenotypicOfIntermedidatePopPSL[j+1] = tempPhenotypicOfIntermedidatePopPSL;
                }
            }
        }


        //consider tree size by mengxu 2024.6.06
//        for(int i=0; i<pop.length-1; i++){
//            for(int j=0; j< pop.length-1-i; j++){
//                //for HV, the bigger the better
//                if(!pop[j].fitness.betterThan(pop[j+1].fitness)){
//                    if(pop[j].fitness.equivalentTo(pop[j+1].fitness)){
//                        double tree_size_j = (((GPIndividual)pop[j]).trees[0]).child.numNodes(GPNode.NODESEARCH_ALL) + ((GPIndividual)pop[j]).trees[1].child.numNodes(GPNode.NODESEARCH_ALL);
//                        double tree_size_j_1 = (((GPIndividual)pop[j+1]).trees[0]).child.numNodes(GPNode.NODESEARCH_ALL) + ((GPIndividual)pop[j+1]).trees[1].child.numNodes(GPNode.NODESEARCH_ALL);
//                        if(tree_size_j < tree_size_j_1){
//                            Individual tempInd = (Individual) pop[j].clone();
//                            double[][] tempPhenotypicOfIntermedidatePopPSL = phenotypicOfIntermedidatePopPSL[j].clone();
//
//                            pop[j] = (Individual) pop[j+1].clone();//todo: need to check whether clone will lose some information? 2024.5.14
//                            phenotypicOfIntermedidatePopPSL[j] = phenotypicOfIntermedidatePopPSL[j+1].clone();
//
//                            pop[j+1] = tempInd;
//                            phenotypicOfIntermedidatePopPSL[j+1] = tempPhenotypicOfIntermedidatePopPSL;
//                        }
//                    }
//                    else{
//                        Individual tempInd = (Individual) pop[j].clone();
//                        double[][] tempPhenotypicOfIntermedidatePopPSL = phenotypicOfIntermedidatePopPSL[j].clone();
//
//                        pop[j] = (Individual) pop[j+1].clone();//todo: need to check whether clone will lose some information? 2024.5.14
//                        phenotypicOfIntermedidatePopPSL[j] = phenotypicOfIntermedidatePopPSL[j+1].clone();
//
//                        pop[j+1] = tempInd;
//                        phenotypicOfIntermedidatePopPSL[j+1] = tempPhenotypicOfIntermedidatePopPSL;
//                    }
//                }
//            }
//        }
    }

    /**
     * sort the pop based on mean PSLfitness by mengxu 2024.3.20
     * @param pop
     * @param phenotypicOfIntermedidatePopPSL
     * @param HVOfIntermedidatePopPSL
     */
    public static void sortPopBasedOnMeanPSLFitness(Individual[] pop, double[][][] phenotypicOfIntermedidatePopPSL,
                                                    double[] HVOfIntermedidatePopPSL, double[][][] fitnessOfIntermedidatePopPSL,
                                                    double[] preferenceDiversityPopAll){
        double[] meanPSLFitness = new double[preferenceDiversityPopAll.length];
        for(int i=0; i<pop.length; i++){
            double mean_performance = 0;
            for(int j=0; j<fitnessOfIntermedidatePopPSL[i].length; j++){
                mean_performance += fitnessOfIntermedidatePopPSL[i][j][2];
            }
            mean_performance = mean_performance/fitnessOfIntermedidatePopPSL[i].length;
            double newFit = mean_performance;
            meanPSLFitness[i] = newFit;
            ((PSLMultiObjectiveFitness)pop[i].fitness).setPSLFitness(newFit); //todo: add by mengxu, need to check whether to add this
        }
        for(int i=0; i<pop.length-1; i++){
            for(int j=0; j< pop.length-1-i; j++){
                //for HV, the bigger the better
                if(meanPSLFitness[j] > meanPSLFitness[j+1]){
                    double tempHV = HVOfIntermedidatePopPSL[j];
                    double tempMeanPSLFitness = meanPSLFitness[j];
                    Individual tempInd = (Individual) pop[j].clone();
                    double[][] tempPhenotypicOfIntermedidatePopPSL = phenotypicOfIntermedidatePopPSL[j].clone();
                    double[][] tempFitnessOfIntermedidatePopPSL = fitnessOfIntermedidatePopPSL[j].clone();
                    double tempPreferenceDiversityPopAll = preferenceDiversityPopAll[j];

                    HVOfIntermedidatePopPSL[j] = HVOfIntermedidatePopPSL[j+1];
                    meanPSLFitness[j] = meanPSLFitness[j+1];
                    pop[j] = (Individual) pop[j+1].clone();//todo: need to check whether clone will lose some information? 2024.5.14
                    phenotypicOfIntermedidatePopPSL[j] = phenotypicOfIntermedidatePopPSL[j+1].clone();
                    fitnessOfIntermedidatePopPSL[j] = fitnessOfIntermedidatePopPSL[j+1].clone();
                    preferenceDiversityPopAll[j] = preferenceDiversityPopAll[j+1];

                    HVOfIntermedidatePopPSL[j+1] = tempHV;
                    meanPSLFitness[j+1] = tempMeanPSLFitness;
                    pop[j+1] = tempInd;
                    phenotypicOfIntermedidatePopPSL[j+1] = tempPhenotypicOfIntermedidatePopPSL;
                    fitnessOfIntermedidatePopPSL[j+1] = tempFitnessOfIntermedidatePopPSL;
                    preferenceDiversityPopAll[j+1] = tempPreferenceDiversityPopAll;
                }
            }
        }
    }

    public static double[][] updatePSLFitnessWithPreferenceDiversityPopulationForParetoSetLearning(final EvolutionState state,
                                                                                                   double[][][][] phenotypicOfIntermedidatePopPSL) {
        int numObjective = ((MultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness).getNumObjectives();
        double[][] preferenceDiversityPopAll = new double[state.population.subpops.length][];

        for (int subpopNum = 0; subpopNum < state.population.subpops.length; subpopNum++) {
            int clearedInds = 0;
            Subpopulation subpop = state.population.subpops[subpopNum];
            Individual[] sortedPop = subpop.individuals.clone();
            preferenceDiversityPopAll[subpopNum] = new double[sortedPop.length];

            //fzhang 2018.10.2 calculate the distance of each individual and the reference rule
            List<double[][]> sortedPopCharLists = new ArrayList<>();
            for (int i = 0;  i < phenotypicOfIntermedidatePopPSL[subpopNum].length; i++) {
                //where the examined rule set the chosen operation by reference rule  for example: 3. means examined rule set the chosen operation by reference rule as the third one
                double[][] charList = phenotypicOfIntermedidatePopPSL[subpopNum][i];
                double sumDistance = 0;
                double number = 0;
                for(int j=0; j<charList.length-1; j++){
                    for(int p=j+1; p<charList.length; p++){
                        // calculate the distance between individuals i and j
                        double distance = PhenoCharacterisation.distance(
                                charList[j], charList[p]);
                        sumDistance += distance;
                        number += 1;
                    }
                }
                double preferenceDiversity = sumDistance/(number*charList.length);
                preferenceDiversityPopAll[subpopNum][i] = preferenceDiversity;
            }
        }
        return preferenceDiversityPopAll;
    }

//    //2020.2.18 calculate the pc of the individuals in each subpopulation, respectively. And save them as KNN surrogate model.
//    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
//    public static double[][][][] phenotypicPopulationPreferenceFixedDecisions(final EvolutionState state, double[][] preferences,
//                                                                            PhenoCharacterisation[] pc) {
//        String P_TREESIZE = "num-trees";
//        int numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);
//        int numPreference = preferences.length;
//
//        if(numTrees<=2){
//            double[][][][] indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][(int) numPreference][]; //save the PC information
//            double[][] indsCharListsMultiTreeTemp = null;
//            double[][][][] indsCharListsMultiTree = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][][];
//            ArrayList<GPRule> benckmarkRule = new ArrayList<>();
//
//            RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
//
//            PopulationUtils.sortForParetoSortLearning(state.population);//todo: double check whether this is using the PSL fitness
//            GPRule[] referenceRule = new GPRule[numTrees];
//
//            //2020.2.12 save the best rules of each subpops, and use them to generate decision situations later.
//            //in this case, the decision situations are used for each subpop are generating with its best rule. It is expected to examine the pc of its individuals better.
//            //todo:modified by mengxu 2022.03.22
//            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
//                for (int treeID = 0; treeID < numTrees; treeID++) {
//                    referenceRule[treeID] = new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]);
//                }
//            }
//
//            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
//                //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
//
//                Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
//
//                for (int treeID = 0; treeID < numTrees; treeID++) {//each population
//
//                    RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
//
//                    PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
//
//                    //this is more general because it handles both the sequencing rules and routing rules.
////                phenoCharacterisation.setReferenceRule(referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
//                    phenoCharacterisation.setReferenceRuleWithPreference(state,referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
//                    // todo:modified by mengxu 2022.03.22
//
//                    //each individual
//                    for (int ind = 0; ind < (int) (inds.length); ind++) {
//                        //each preference
//                        for (int i = 0; i < numPreference; i++) {
//                            int[] charList = phenoCharacterisation.characteriseWithPreference(state,  //characterise: calculate the distance
//                                    new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]), preferences[i]);
//                            indsCharLists[treeID][ind][i] = new double[charList.length];
//
//                            //each PC information---convert int[] to int[][]
//                            for (int numFeature = 0; numFeature < charList.length; numFeature++) {
//                                indsCharLists[treeID][ind][i][numFeature] = charList[numFeature];
//                            }
//                        }
//                    }
//                }
//
//                //here, we get the pc of one subpopulation, we need to save it.
//                //combine the phenotype of sequencing rule with the phenotype of routing rule
//                for (int ind = 0; ind < (int) (inds.length); ind++) {
//                    indsCharListsMultiTreeTemp = new double[indsCharLists[0][ind].length][];
//                    //indsCharLists[0].length == indsCharLists[1].length
//                    for (int i = 0; i < indsCharLists[0][ind].length; i++) {
//                        double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][ind][i], indsCharLists[1][ind][i]);
//                        indsCharListsMultiTreeTemp[i] = combinePheChar;
//                        //todo: here need to estimate the fitness based on PC similarity
//                    }
//                    indsCharListsMultiTree[subpop][ind] = indsCharListsMultiTreeTemp;
//                }
//            }
//            return indsCharListsMultiTree;
//        }
//        else{
//            double[][][][] indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][(int) numPreference][]; //save the PC information
//            double[][] indsCharListsMultiTreeTemp = null;
//            double[][][][] indsCharListsMultiTree = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][][];
//            ArrayList<GPRule> benckmarkRule = new ArrayList<>();
//            RuleType[] ruleTypes = new RuleType[numTrees]; //ruleType is an array
//            for (int treeID = 0; treeID < numTrees; treeID++) {
//                if(treeID<numTrees/2){
//                    ruleTypes[treeID] = RuleType.SEQUENCING;
//                }
//                else{
//                    ruleTypes[treeID] = RuleType.ROUTING;
//                }
//            }
//
//            PopulationUtils.sortForParetoSortLearning(state.population);//todo: double check whether this is using the PSL fitness
//            GPRule[] referenceRule = new GPRule[numTrees];
//
//            //2020.2.12 save the best rules of each subpops, and use them to generate decision situations later.
//            //in this case, the decision situations are used for each subpop are generating with its best rule. It is expected to examine the pc of its individuals better.
//            //todo:modified by mengxu 2022.03.22
//            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
//                for (int treeID = 0; treeID < numTrees; treeID++) {
//                    referenceRule[treeID] = new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]);
//                }
//            }
//
//
//            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
//                //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
//
//                Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
//
//                for (int treeID = 0; treeID < numTrees; treeID++) {//each population
//                    RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
//
//                    PhenoCharacterisation phenoCharacterisation = null;//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
//                    if(treeID<numTrees/2){
//                        phenoCharacterisation = pc[0];
//                    }
//                    else{
//                        phenoCharacterisation = pc[1];
//                    }
//
//                    //this is more general because it handles both the sequencing rules and routing rules.
////                phenoCharacterisation.setReferenceRule(referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
//                    phenoCharacterisation.setReferenceRuleWithPreference(state,referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
//                    // todo:modified by mengxu 2022.03.22
//
//                    //each individual
//                    for (int ind = 0; ind < (int) (inds.length); ind++) {
//                        //each preference
//                        for (int i = 0; i < numPreference; i++) {
//                            int[] charList = phenoCharacterisation.characteriseWithPreference(state,  //characterise: calculate the distance
//                                    new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]), preferences[i]);
//                            indsCharLists[treeID][ind][i] = new double[charList.length];
//
//                            //each PC information---convert int[] to int[][]
//                            for (int numFeature = 0; numFeature < charList.length; numFeature++) {
//                                indsCharLists[treeID][ind][i][numFeature] = charList[numFeature];
//                            }
//                        }
//                    }
//                }
//                for (int ind = 0; ind < (int) (inds.length); ind++) {
//                    //here, we get the pc of one subpopulation, we need to save it.
//                    //combine the phenotype of sequencing rule with the phenotype of routing rule
//                    indsCharListsMultiTreeTemp = new double[indsCharLists[0][ind].length][];
//                    //indsCharLists[0].length == indsCharLists[1].length
//                    for (int i = 0; i < indsCharLists[0][ind].length; i++) {
//                        double[] combinePheChar = new double[0];
//                        for(int treeID=0; treeID<numTrees; treeID++){
//                            combinePheChar = ArrayUtils.addAll(combinePheChar, indsCharLists[treeID][ind][i]);
////                            combinePheChar = ArrayUtils.addAll(indsCharLists[0][ind][i], indsCharLists[1][ind][i]);
//                        }
//                        indsCharListsMultiTreeTemp[i] = combinePheChar;
//                    }
//                    indsCharListsMultiTree[subpop][ind] = indsCharListsMultiTreeTemp;
//                }
//            }
//            return indsCharListsMultiTree;
//        }
//    }

    //2020.2.18 calculate the pc of the individuals in each subpopulation, respectively. And save them as KNN surrogate model.
    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
    public static double[][][][] phenotypicPopulationPreferenceFixedDecisions(final EvolutionState state, double[][] preferences,
                                                                              PhenoCharacterisation[] pc, boolean updateReference) {
        String P_TREESIZE = "num-trees";
        int numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);
        int numPreference = preferences.length;

        if(numTrees<=2){
            double[][][][] indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][(int) numPreference][]; //save the PC information
            double[][] indsCharListsMultiTreeTemp = null;
//            double[][] indsFitnessesMultiTreeTemp = null;
            double[][][][] indsCharListsMultiTree = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][][];
//            double[][][][] indsFitnessesMultiTree = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][][];//add by mengxu 2024.5.24
            ArrayList<GPRule> benckmarkRule = new ArrayList<>();

            RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array

            GPRule[] referenceRule = new GPRule[numTrees];

            //2020.2.12 save the best rules of eac subpops, and use them to generate decision situations later.
            //in this case, the decision situations are used for each subpop are generating with its best rule. It is expected to examine the pc of its individuals better.
            if(updateReference){
                //todo:modified by mengxu 2022.03.22
                for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
                    Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
                    for (int treeID = 0; treeID < numTrees; treeID++) {
                        if(((KNNsurrogateClearingPSLEvaluatorbasedonHV)state.evaluator).useKneePoint){
                            //use the knee point
                            referenceRule[treeID] = new GPRule(ruleTypes[treeID], ((GPIndividual) ((KNNsurrogateClearingPSLEvaluatorbasedonHV)state.evaluator).kneePointIndividual).trees[treeID]);
                        }
                        else{
                            //use the best individual
                            referenceRule[treeID] = new GPRule(ruleTypes[treeID], ((GPIndividual) inds[0]).trees[treeID]);
                        }
                    }
                }
            }


            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
                //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here

                Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now

                for (int treeID = 0; treeID < numTrees; treeID++) {//each population

                    RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING

                    PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation

                    //this is more general because it handles both the sequencing rules and routing rules.
//                phenoCharacterisation.setReferenceRule(referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
                    if(updateReference){
                        phenoCharacterisation.setReferenceRuleWithPreference(state,referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
                        // todo:modified by mengxu 2022.03.22
                    }

                    //each individual
                    for (int ind = 0; ind < (int) (inds.length); ind++) {
                        //each preference
                        for (int i = 0; i < numPreference; i++) {
                            int[] charList = phenoCharacterisation.characteriseWithPreference(state,  //characterise: calculate the distance
                                    new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]), preferences[i]);
                            indsCharLists[treeID][ind][i] = new double[charList.length];

                            //each PC information---convert int[] to int[][]
                            for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                                indsCharLists[treeID][ind][i][numFeature] = charList[numFeature];
                            }
                        }
                    }
                }

                //here, we get the pc of one subpopulation, we need to save it.
                //combine the phenotype of sequencing rule with the phenotype of routing rule
                for (int ind = 0; ind < (int) (inds.length); ind++) {
                    indsCharListsMultiTreeTemp = new double[indsCharLists[0][ind].length][];
//                    indsFitnessesMultiTreeTemp = new double[indsCharLists[0][ind].length][];
                    //indsCharLists[0].length == indsCharLists[1].length
                    for (int i = 0; i < indsCharLists[0][ind].length; i++) {
                        double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][ind][i], indsCharLists[1][ind][i]);
                        indsCharListsMultiTreeTemp[i] = combinePheChar;

                        //todo: here need to estimate the fitness based on PC similarity
                    }
                    indsCharListsMultiTree[subpop][ind] = indsCharListsMultiTreeTemp;
                }
            }
            return indsCharListsMultiTree;
        }
        else{
            double[][][][] indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][(int) numPreference][]; //save the PC information
            double[][] indsCharListsMultiTreeTemp = null;
            double[][][][] indsCharListsMultiTree = new double[state.population.subpops.length][(int) (state.population.subpops[0].individuals.length)][][];
            ArrayList<GPRule> benckmarkRule = new ArrayList<>();
            RuleType[] ruleTypes = new RuleType[numTrees]; //ruleType is an array
            for (int treeID = 0; treeID < numTrees; treeID++) {
                if(treeID<numTrees/2){
                    ruleTypes[treeID] = RuleType.SEQUENCING;
                }
                else{
                    ruleTypes[treeID] = RuleType.ROUTING;
                }
            }

            PopulationUtils.sortForParetoSortLearning(state.population);//todo: double check whether this is using the PSL fitness
            GPRule[] referenceRule = new GPRule[numTrees];

            //2020.2.12 save the best rules of each subpops, and use them to generate decision situations later.
            //in this case, the decision situations are used for each subpop are generating with its best rule. It is expected to examine the pc of its individuals better.
            //todo:modified by mengxu 2022.03.22
            if(updateReference)
            {
                for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
                    for (int treeID = 0; treeID < numTrees; treeID++) {
                        referenceRule[treeID] = new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]);
                    }
                }

            }

            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
                //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here

                Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now

                for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                    RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING

                    PhenoCharacterisation phenoCharacterisation = null;//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
                    if(treeID<numTrees/2){
                        phenoCharacterisation = pc[0];
                    }
                    else{
                        phenoCharacterisation = pc[1];
                    }

                    //this is more general because it handles both the sequencing rules and routing rules.
//                phenoCharacterisation.setReferenceRule(referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
                    if(updateReference){
                        phenoCharacterisation.setReferenceRuleWithPreference(state,referenceRule[treeID]); //do use this if want to use SPT, WIQ as the reference rule
                        // todo:modified by mengxu 2022.03.22
                    }

                    //each individual
                    for (int ind = 0; ind < (int) (inds.length); ind++) {
                        //each preference
                        for (int i = 0; i < numPreference; i++) {
                            int[] charList = phenoCharacterisation.characteriseWithPreference(state,  //characterise: calculate the distance
                                    new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]), preferences[i]);
                            indsCharLists[treeID][ind][i] = new double[charList.length];

                            //each PC information---convert int[] to int[][]
                            for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                                indsCharLists[treeID][ind][i][numFeature] = charList[numFeature];
                            }
                        }
                    }
                }
                for (int ind = 0; ind < (int) (inds.length); ind++) {
                    //here, we get the pc of one subpopulation, we need to save it.
                    //combine the phenotype of sequencing rule with the phenotype of routing rule
                    indsCharListsMultiTreeTemp = new double[indsCharLists[0][ind].length][];
                    //indsCharLists[0].length == indsCharLists[1].length
                    for (int i = 0; i < indsCharLists[0][ind].length; i++) {
                        double[] combinePheChar = new double[0];
                        for(int treeID=0; treeID<numTrees; treeID++){
                            combinePheChar = ArrayUtils.addAll(combinePheChar, indsCharLists[treeID][ind][i]);
//                            combinePheChar = ArrayUtils.addAll(indsCharLists[0][ind][i], indsCharLists[1][ind][i]);
                        }
                        indsCharListsMultiTreeTemp[i] = combinePheChar;
                    }
                    indsCharListsMultiTree[subpop][ind] = indsCharListsMultiTreeTemp;
                }
            }
            return indsCharListsMultiTree;
        }
    }

    public static void writeToFile(long jobSeed, int numSubpops) {
        //fzhang 2019.5.21 save the number of cleared individuals
        File weightFile = new File("job." + jobSeed + ".clearedInds.csv"); // jobSeed = 0
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(weightFile));
            writer.write("Gen, ClearedIndsSubpop0");
            writer.newLine();
            for (int i = 0; i < clearedIndsArray.size(); i += numSubpops) { //every two into one generation
                //writer.newLine();
                writer.write(i + ", " + clearedIndsArray.get(i));
                writer.newLine();
            }
//            writer.write(numGenerations -1 + ", " + 0 + ", " + 0 + ", " + 0 + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //original
//    public static void writeToFile(long jobSeed, int numGenerations, int numSubpops) {
//        //fzhang 2019.5.21 save the number of cleared individuals
//        File weightFile = new File("job." + jobSeed + ".clearedInds.csv"); // jobSeed = 0
//        try {
//            BufferedWriter writer = new BufferedWriter(new FileWriter(weightFile));
//            writer.write("Gen, ClearedIndsSubpop0, ClearedIndsSubpop1, ClearedIndsSubpop2");
//            writer.newLine();
//            for (int i = 0; i < clearedIndsArray.size(); i += numSubpops) { //every two into one generation
//                //writer.newLine();
//                writer.write(i/numSubpops + ", " + clearedIndsArray.get(i) + ", "+ clearedIndsArray.get(i+1) + ", " + clearedIndsArray.get(i+2) + "\n");
//            }
//            writer.write(numGenerations -1 + ", " + 0 + ", " + 0 + ", " + 0 + "\n");
//            writer.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
    //===================================end=================================
}
