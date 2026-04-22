package mengxu.algorithm.MAPElites.GridMapElites;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleProblemForm;
import mengxu.algorithm.MAPElites.CVTMAPElites.GPRuleEvolutionStateCVTMAPElites;
import mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans.Cluster;
import mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans.DistanceCompute;
import mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans.KMeansRun;
import mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans.Point;
import org.apache.commons.lang3.ObjectUtils;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

public class gridPCmap {

    public int grid_size = 17; // 17 is for log10, 100 is for direct

    public int[][] num_accept_each_cell;

    public ArrayList<Individual>[][] inds_each_cell;

    public ArrayList<Double>[][] fitnesses_each_cell;

    public ArrayList<double[]>[][] PCs_each_cell;

    public ArrayList<Integer>[][] indsFromGen_each_cell;

    public ArrayList<ArrayList<Double>> allResults;

    public int base;

//    double penalty;

    public gridPCmap(int grid_size, int base) {
        this.grid_size = grid_size;
        this.base = base;
        this.num_accept_each_cell = new int[grid_size][grid_size];
        this.inds_each_cell = new ArrayList[grid_size][grid_size];
        this.fitnesses_each_cell = new ArrayList[grid_size][grid_size];
        this.PCs_each_cell = new ArrayList[grid_size][grid_size];
        this.indsFromGen_each_cell = new ArrayList[grid_size][grid_size];
        this.allResults = new ArrayList<>(); // Added by mengxu 2024.12.24

        // Initialize all cells with default values
        for (int i = 0; i < grid_size; i++) {
            for (int j = 0; j < grid_size; j++) {
                this.num_accept_each_cell[i][j] = 1;
                this.inds_each_cell[i][j] = new ArrayList<>();
                this.fitnesses_each_cell[i][j] = new ArrayList<>();
                this.PCs_each_cell[i][j] = new ArrayList<>();
                this.indsFromGen_each_cell[i][j] = new ArrayList<>();
            }
        }
    }

    public gridPCmap(int grid_size, int base, int numAccept) {
        this.grid_size = grid_size;
        this.base = base;
        this.num_accept_each_cell = new int[grid_size][grid_size];
        this.inds_each_cell = new ArrayList[grid_size][grid_size];
        this.fitnesses_each_cell = new ArrayList[grid_size][grid_size];
        this.PCs_each_cell = new ArrayList[grid_size][grid_size];
        this.indsFromGen_each_cell = new ArrayList[grid_size][grid_size];
        this.allResults = new ArrayList<>(); // Added by mengxu 2024.12.24

        // Initialize all cells with default values
        for (int i = 0; i < grid_size; i++) {
            for (int j = 0; j < grid_size; j++) {
                this.num_accept_each_cell[i][j] = numAccept;
                this.inds_each_cell[i][j] = new ArrayList<>();
                this.fitnesses_each_cell[i][j] = new ArrayList<>();
                this.PCs_each_cell[i][j] = new ArrayList<>();
                this.indsFromGen_each_cell[i][j] = new ArrayList<>();
            }
        }
    }

    public int[] calculateUniqueValue(double[] values) {
        int numDecision = values.length / 2 - 1;
        double[] sequencingDecisions = Arrays.copyOfRange(values, 0, values.length / 2);
        double[] routingDecisions = Arrays.copyOfRange(values, values.length / 2, values.length);

        int seqUniqueValue = calculateUniqueValueForDecisions(sequencingDecisions, numDecision);
        int routUniqueValue = calculateUniqueValueForDecisions(routingDecisions, numDecision);

        return new int[]{seqUniqueValue, routUniqueValue};
    }

    private int calculateUniqueValueForDecisions(double[] decisions, int numDecision) {
        long uniqueValue = 0;
        long basePower = 1;

        for (int i = numDecision; i >= 0; i--) {
            uniqueValue += decisions[i] * basePower;
            basePower *= this.base;
        }

        return (int) uniqueValue;
    }


    public void reevaluateGridMap(EvolutionState state){
        for(int i=0; i<this.inds_each_cell.length; i++){
            for(int j=0; j<this.inds_each_cell[i].length; j++) {
                ArrayList<Individual> indsRef = this.inds_each_cell[i][j];
                for (int c = 0; c < indsRef.size(); c++) {
                    Individual ind = indsRef.get(c);
                    SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
                    prob.evaluate(state, ind, 0, 0);
                    double[] objectiveValues = ((MultiObjectiveFitness)ind.fitness).getObjectives();
                    if(((GPRuleEvolutionStateGridMAPElites)state).usingManualRuleNormalisation){
                        objectiveValues[0] = objectiveValues[0]/((GPRuleEvolutionStateGridMAPElites)state).baselineObjectives[0];
                    }
                    this.fitnesses_each_cell[i][j].set(c,objectiveValues[0]);
                }
            }
        }
    }

    public void updateGridMap(EvolutionState state, Individual[] individuals,
                              double[][] indsCharListsMultiTree,
                              double[] fitnessesForModel){ //todo: need double check 2023.09.25
        for(int i=0; i<indsCharListsMultiTree.length; i++){
            double[] indCharListsMultiTree = indsCharListsMultiTree[i];
            int[] location = calculateUniqueValue(indCharListsMultiTree);
            ArrayList<Double> allFitInCell = this.fitnesses_each_cell[location[0]][location[1]];
            int numAcceptInCell = this.num_accept_each_cell[location[0]][location[1]];
            if(allFitInCell.size() < numAcceptInCell){
                this.fitnesses_each_cell[location[0]][location[1]].add(fitnessesForModel[i]);
                this.inds_each_cell[location[0]][location[1]].add((Individual) individuals[i].clone());
                this.PCs_each_cell[location[0]][location[1]].add(indCharListsMultiTree.clone());
                this.indsFromGen_each_cell[location[0]][location[1]].add(state.generation);
                ((GPRuleEvolutionStateGridMAPElites)state).updateNum++;
            }
            else{
                //get the worst one in the cell
                int index_worst = 0;
                double fit_worst = -1;
                int indFromGen_worst = -1;
                for(int j=0; j<this.fitnesses_each_cell[location[0]][location[1]].size(); j++){
                    if(this.fitnesses_each_cell[location[0]][location[1]].get(j) > fit_worst){
                        index_worst = j;
                        fit_worst = this.fitnesses_each_cell[location[0]][location[1]].get(j);
                        indFromGen_worst = this.indsFromGen_each_cell[location[0]][location[1]].get(j);
                    }
                }
                //update the worst, new 2025.1.6
//                if(indFromGen_worst < state.generation){
//                    this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessesForModel[i]);
//                    this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individuals[i].clone());
//                    this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
//                    this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
//                }
//                else{
//                    if(betterThanBasedOnFitAndGen(fitnessesForModel[i],state.generation,fit_worst,indFromGen_worst,0,state,0)){
//                        this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessesForModel[i]);
//                        this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individuals[i].clone());
//                        this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
//                        this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
//                    }
//                }
                //update the worst, new 2025.1.3
                if(betterThanBasedOnFitAndGen(fitnessesForModel[i],state.generation,fit_worst,indFromGen_worst,0,state,0)){
                    this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessesForModel[i]);
                    this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individuals[i].clone());
                    this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
                    this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
                    ((GPRuleEvolutionStateGridMAPElites)state).updateNum++;
                }
//                //update the worst, new 2025.1.3
//                if(indFromGen_worst < state.generation){
//                    this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessesForModel[i]);
//                    this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individuals[i].clone());
//                    this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
//                    this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
//                }
//                else{
//                    if(fitnessesForModel[i] < fit_worst){
//                        this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessesForModel[i]);
//                        this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individuals[i].clone());
//                        this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
//                        this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
//                    }
//                }
            }
        }
    }

    public boolean betterThanBasedOnFitAndGen(double fit_first, int fromGen_first, double fit_second, int fromGen_second,
                                              int subpopulation, EvolutionState state, int thread)
    {
        if(((GPRuleEvolutionStateGridMAPElites)state).reevaluateGridMap){
            return fit_first<fit_second;
        }
        double penalty_level = ((GPRuleEvolutionStateGridMAPElites) state).penalty_level;
        double fit_first_penalty = fit_first * Math.pow((1+penalty_level), (state.generation-fromGen_first));
        double fit_second_penalty = fit_second * Math.pow((1+penalty_level), (state.generation-fromGen_second));
        return fit_first_penalty<fit_second_penalty;
    }

    public void updateGridMapWithIndividual(EvolutionState state, Individual individual,
                                            double[] indCharListsMultiTree,
                                            double fitnessForModel){ //todo: need double check 2023.09.25
        int[] location = calculateUniqueValue(indCharListsMultiTree);
        ArrayList<Double> allFitInCell = this.fitnesses_each_cell[location[0]][location[1]];
        int numAcceptInCell = this.num_accept_each_cell[location[0]][location[1]];
//        double[] fit_update = new double[1];
//        fit_update[0] = fitnessForModel;
//        ((MultiObjectiveFitness)individual.fitness).setObjectives(state, fit_update);
        if(allFitInCell.size() < numAcceptInCell){
            this.fitnesses_each_cell[location[0]][location[1]].add(fitnessForModel);
            this.inds_each_cell[location[0]][location[1]].add((Individual) individual.clone());
            this.PCs_each_cell[location[0]][location[1]].add(indCharListsMultiTree.clone());
            this.indsFromGen_each_cell[location[0]][location[1]].add(state.generation);
            ((GPRuleEvolutionStateGridMAPElites)state).updateNum++;
        }
        else{
            //get the worst one in the cell
            int index_worst = 0;
            double fit_worst = -1;
            int indFromGen_worst = -1;
            for(int j=0; j<this.fitnesses_each_cell[location[0]][location[1]].size(); j++){
                if(this.fitnesses_each_cell[location[0]][location[1]].get(j) > fit_worst){
                    index_worst = j;
                    fit_worst = this.fitnesses_each_cell[location[0]][location[1]].get(j);
                    indFromGen_worst = this.indsFromGen_each_cell[location[0]][location[1]].get(j);
                }
            }
            //update the worst, new 2025.1.6
//            if(indFromGen_worst < state.generation){
//                this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessForModel);
//                this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individual.clone());
//                this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
//                this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
//            }
//            else{
//                if(betterThanBasedOnFitAndGen(fitnessForModel,state.generation,fit_worst,indFromGen_worst,0,state,0)){
//                    this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessForModel);
//                    this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individual.clone());
//                    this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
//                    this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
//                }
//            }

            //update the worst, new 2025.1.3
            if(betterThanBasedOnFitAndGen(fitnessForModel,state.generation,fit_worst,indFromGen_worst,0,state,0)){
                this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessForModel);
                this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individual.clone());
                this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
                this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
                ((GPRuleEvolutionStateGridMAPElites)state).updateNum++;
            }
//            //update the worst, new 2025.1.2
//            if(indFromGen_worst < state.generation){
//                this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessForModel);
//                this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individual.clone());
//                this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
//                this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
//            }
//            else{
//                if(fitnessForModel < fit_worst){
//                    this.fitnesses_each_cell[location[0]][location[1]].set(index_worst, fitnessForModel);
//                    this.inds_each_cell[location[0]][location[1]].set(index_worst, (Individual) individual.clone());
//                    this.PCs_each_cell[location[0]][location[1]].set(index_worst, indCharListsMultiTree.clone());
//                    this.indsFromGen_each_cell[location[0]][location[1]].set(index_worst, state.generation);
//                }
//            }
        }
    }

    public int getNumberIndividualsInGridMap(){
        int number = 0;
        for(int i=0; i<this.fitnesses_each_cell.length; i++){
            for(int j=0; j<this.fitnesses_each_cell[i].length; j++){
                number = number + this.fitnesses_each_cell[i][j].size();
            }
        }
        return number;
    }

    public ArrayList<Individual> getAllIndividualsFromCurrentGen(EvolutionState state){
        ArrayList<Individual> inds = new ArrayList<>();
        for(int i=0; i<this.inds_each_cell.length; i++){
            for(int j=0; j<this.inds_each_cell[i].length; j++) {
                ArrayList<Individual> indsRef = this.inds_each_cell[i][j];
                ArrayList<Integer> indFromGensRef = this.indsFromGen_each_cell[i][j];
                for (int c = 0; c < indsRef.size(); c++) {
                    Individual ind = (Individual) indsRef.get(c).clone();
                    int indFromGen = indFromGensRef.get(c);
                    if(indFromGen == state.generation){
                        inds.add(ind);
                    }
                }
            }
        }
        return inds;
    }

    public ArrayList<Individual> getAllIndividualsInGridMap(){
        ArrayList<Individual> inds = new ArrayList<>();
        for(int i=0; i<this.inds_each_cell.length; i++){
            for(int j=0; j<this.inds_each_cell[i].length; j++) {
                ArrayList<Individual> indsRef = this.inds_each_cell[i][j];
                for (int c = 0; c < indsRef.size(); c++) {
                    Individual ind = (Individual) indsRef.get(c).clone();
                    inds.add(ind);
                }
            }
        }
        return inds;
    }
//
//    public ArrayList<Double> getAllFitnessesInGridMap(){
//        ArrayList<Double> fits = new ArrayList<>();
//        for(int i=0; i<this.fitnesses_each_cell.length; i++){
//            for(int j=0; j<this.fitnesses_each_cell[i].length; j++) {
//                ArrayList<Double> indsRef = this.fitnesses_each_cell[i][j];
//                for (int c = 0; c < indsRef.size(); c++) {
//                    double fit = indsRef.get(c);
//                    fits.add(fit);
//                }
//            }
//        }
//        return fits;
//    }
//
//    public ArrayList<Integer> getAllFromGenInGridMap(){
//        ArrayList<Integer> fromGens = new ArrayList<>();
//        for(int i=0; i<this.indsFromGen_each_cell.length; i++){
//            for(int j=0; j<this.indsFromGen_each_cell[i].length; j++) {
//                ArrayList<Integer> indsRef = this.indsFromGen_each_cell[i][j];
//                for (int c = 0; c < indsRef.size(); c++) {
//                    int fromGen = indsRef.get(c);
//                    fromGens.add(fromGen);
//                }
//            }
//        }
//        return fromGens;
//    }

    public ArrayList<ArrayList<Object>> getAllDataInGridMap() {
        ArrayList<Object> inds = new ArrayList<>();
        ArrayList<Object> fits = new ArrayList<>();
        ArrayList<Object> fromGens = new ArrayList<>();

        for (int i = 0; i < this.inds_each_cell.length; i++) {
            for (int j = 0; j < this.inds_each_cell[i].length; j++) {
                ArrayList<Individual> indsRef = this.inds_each_cell[i][j];
                ArrayList<Double> fitsRef = this.fitnesses_each_cell[i][j];
                ArrayList<Integer> fromGensRef = this.indsFromGen_each_cell[i][j];

                for (int c = 0; c < indsRef.size(); c++) {
                    Individual ind = (Individual) indsRef.get(c).clone();
                    double fit = fitsRef.get(c);
                    int fromGen = fromGensRef.get(c);

                    inds.add(ind);
                    fits.add(fit);
                    fromGens.add(fromGen);
                }
            }
        }

        ArrayList<ArrayList<Object>> allData = new ArrayList<>();
        allData.add(inds);
        allData.add(fits);
        allData.add(fromGens);

        return allData;
    }

    public void printAllFitnessInGridMap(){
        System.out.print("All fitness in MAP: ");
        ArrayList<Individual> inds = new ArrayList<>();
        for(int i=0; i<this.fitnesses_each_cell.length; i++){
            for(int j=0; j<this.fitnesses_each_cell[i].length; j++) {
                ArrayList<Double> indsRef = this.fitnesses_each_cell[i][j];
                for (int c = 0; c < indsRef.size(); c++) {
                    double fit = indsRef.get(c);
                    System.out.print(fit + ", ");
                }
            }
        }
    }

    public void writeAllFitnessInGridMapToCSV(EvolutionState state) {
        try (FileWriter csvWriter = new FileWriter( "/Users/mengxu/IdeaProjects/GPJSS-master/src/mengxu/algorithm/MAPElites/GridMapElites/GridMaps_QDGPf_05/gen_" + state.generation + "_fitness_grid_map.csv")) {
            // Write the header of the CSV
            csvWriter.append("x-axis,y-axis,fitness\n");

            // Iterate over the grid
            for (int i = 0; i < this.fitnesses_each_cell.length; i++) {
                for (int j = 0; j < this.fitnesses_each_cell[i].length; j++) {
                    ArrayList<Double> indsRef = this.fitnesses_each_cell[i][j];

                    // For each fitness value in the cell, write to CSV
                    for (double fit : indsRef) {
                        csvWriter.append(i + "," + j + "," + fit + "\n");
                    }
                }
            }

            System.out.println("Fitness data has been written to fitness_grid_map.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveCoverageAndMeanPerformanceAbdBestPerformanceToCSV(EvolutionState state, boolean output) {
        ArrayList<Double> resultCurGen = new ArrayList<>();
        double coverage = (double)this.getNumberIndividualsInGridMap()/(double)(this.grid_size*this.grid_size);
        double meanPerformance = this.calculateMeanPerformance();
        double bestPerformance = this.calculateBestPerformance(state);
        resultCurGen.add(coverage);
        resultCurGen.add(meanPerformance);
        resultCurGen.add(bestPerformance);
        this.allResults.add(resultCurGen);
        if(output){
            try (FileWriter csvWriter = new FileWriter( "job." + ((GPRuleEvolutionStateGridMAPElites)state).jobSeed + ".all_results_grid_map.csv")) {
                // Write the header of the CSV
                csvWriter.append("Generation,Coverage,Mean,Best\n");

                // Iterate over the grid
                for (int i = 0; i < this.allResults.size(); i++) {
                    ArrayList<Double> resultGen = this.allResults.get(i);
                    csvWriter.append(i + "," + resultGen.get(0) + "," + resultGen.get(1) + "," + resultGen.get(2) + "\n");
                }

                System.out.println("All results has been written to job." + ((GPRuleEvolutionStateGridMAPElites)state).jobSeed + ".all_results_grid_map.csv");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public double calculateMeanPerformance(){
        double meanPerformance = 0;
        double numPerformance = 0;
        // Iterate over the grid
        for (int i = 0; i < this.fitnesses_each_cell.length; i++) {
            for (int j = 0; j < this.fitnesses_each_cell[i].length; j++) {
                ArrayList<Double> indsRef = this.fitnesses_each_cell[i][j];
                int num = this.inds_each_cell[i][j].size();
                for(int m=0; m<num; m++){
                    if(indsRef.get(m) >= Double.POSITIVE_INFINITY || indsRef.get(m) >= Double.MAX_VALUE){

                    }
                    else{
                        meanPerformance = meanPerformance+indsRef.get(m);
                        numPerformance = numPerformance + 1;
                    }
                }
            }
        }
        meanPerformance = meanPerformance/numPerformance;
        return meanPerformance;
    }

    public double calculateBestPerformance(EvolutionState state){
        double bestPerformance = Double.MAX_VALUE;
        int bestFromGen = state.generation;
        // Iterate over the grid
        for (int i = 0; i < this.fitnesses_each_cell.length; i++) {
            for (int j = 0; j < this.fitnesses_each_cell[i].length; j++) {
                ArrayList<Double> indsRef = this.fitnesses_each_cell[i][j];
                int num = this.inds_each_cell[i][j].size();
                for(int m=0; m<num; m++){
                    double fit_m = indsRef.get(m);
                    int fromGen_m = this.indsFromGen_each_cell[i][j].get(m);
                    if(betterThanBasedOnFitAndGen(fit_m,fromGen_m,bestPerformance,bestFromGen,0,state,0)){
                        bestPerformance = fit_m;
                        bestFromGen = fromGen_m;
                    }
                }
            }
        }
        return bestPerformance;
    }

    public GPIndividual getBestIndividual(EvolutionState state){
        double bestPerformance = Double.POSITIVE_INFINITY;
        GPIndividual bestInd = null;
        int fromGenBestInd = state.generation;
        // Iterate over the grid
        for (int i = 0; i < this.fitnesses_each_cell.length; i++) {
            for (int j = 0; j < this.fitnesses_each_cell[i].length; j++) {
                ArrayList<Double> indsRef = this.fitnesses_each_cell[i][j];
                int num = this.inds_each_cell[i][j].size();
                for(int m=0; m<num; m++){
                    //new by mengxu 2025.1.3
                    double fitCandidate = indsRef.get(m);
                    int fromGenCandidate = this.indsFromGen_each_cell[i][j].get(m);
                    if(betterThanBasedOnFitAndGen(fitCandidate,fromGenCandidate,bestPerformance,fromGenBestInd,0,state,0)){
                        bestPerformance = indsRef.get(m);
                        bestInd = (GPIndividual) this.inds_each_cell[i][j].get(m).clone();
                        fromGenBestInd = fromGenCandidate;
                        double[] updatedFit = new double[1];
                        updatedFit[0] = bestPerformance;
                        ((MultiObjectiveFitness)bestInd.fitness).setObjectives(state,updatedFit);
                    }
                    //original
//                    if(indsRef.get(m) < bestPerformance){
//                        bestPerformance = indsRef.get(m);
//                        bestInd = (GPIndividual) this.inds_each_cell[i][j].get(m).clone();
////                        double[] updatedFit = new double[1];
////                        updatedFit[0] = bestPerformance;
////                        ((MultiObjectiveFitness)bestInd.fitness).setObjectives(state,updatedFit);
//                    }
                }
            }
        }
        return bestInd;
    }

    public GPIndividual getBestIndividualFromCurrentGen(EvolutionState state){
        double bestPerformance = Double.POSITIVE_INFINITY;
        GPIndividual bestInd = null;
        // Iterate over the grid
        for (int i = 0; i < this.fitnesses_each_cell.length; i++) {
            for (int j = 0; j < this.fitnesses_each_cell[i].length; j++) {
                ArrayList<Double> indsRef = this.fitnesses_each_cell[i][j];
                ArrayList<Integer> indFromCurrentGenRef = this.indsFromGen_each_cell[i][j];
                int num = this.inds_each_cell[i][j].size();
                for(int m=0; m<num; m++){
                    int indFromCurrentGen = indFromCurrentGenRef.get(m);
                    if(indFromCurrentGen == state.generation){
                        if(indsRef.get(m) < bestPerformance){
                            bestPerformance = indsRef.get(m);
                            bestInd = (GPIndividual) this.inds_each_cell[i][j].get(m).clone();
                            double[] updatedFit = new double[1];
                            updatedFit[0] = bestPerformance;
                            ((MultiObjectiveFitness)bestInd.fitness).setObjectives(state,updatedFit);
                        }
                    }
                }
            }
        }
        if(bestInd==null){
            System.out.println("Not from the current generation!!!");
            bestInd = getBestIndividual(state);
        }
        return bestInd;
    }

}
