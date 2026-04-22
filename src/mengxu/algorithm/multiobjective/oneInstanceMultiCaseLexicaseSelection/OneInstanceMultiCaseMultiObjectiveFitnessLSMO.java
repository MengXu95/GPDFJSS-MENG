package mengxu.algorithm.multiobjective.oneInstanceMultiCaseLexicaseSelection;

import ec.EvolutionState;
import ec.Fitness;
//import ec.app.edge.func.Double;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;
import ec.util.Code;
import ec.util.Parameter;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.focusOnPoorCase.GPRuleEvolutionStateDPSPC;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import yimei.jss.jobshop.Objective;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static yimei.jss.jobshop.Objective.*;

public class OneInstanceMultiCaseMultiObjectiveFitnessLSMO extends MultiObjectiveFitness {
    /** parameter for size of objectives */
    public static final String P_NUMCASE = "num-case";
    public static final String P_USE_NOVELTY_SCORE = "use-novelty-score";

    public double[][] multiInstanceMultiObjectiveFitness;

    public double[][] normalisedmultiInstanceMultiObjectiveFitness; // values range from 0 (worst) to 1 INCLUSIVE added by mengxu 2023.04.20

    public int[][] multiInstanceMultiObjectiveFitnessRank;//add 2022.04.25

    public double[][] multiInstanceMultiObjectiveBinaryBring; //add 2021.12.23 for DPS selection

    public double[][] multiInstanceMultiObjectiveNormalisation; //add 2021.01.12 for DPS with new strategy selection

    public boolean useNoveltyScore;
    public double[][] multiInstanceNoveltyScore;//add 2021.12.10
    public List<Objective> currentObjectives; //modified by mengxu

    public EvolutionState currentState;

    public static final String NSGA2_RANK_PREAMBLE = "Rank: ";
    public static final String NSGA2_SPARSITY_PREAMBLE = "Sparsity: ";

    public String[] getAuxilliaryFitnessNames() { return new String[] { "Rank", "Sparsity" }; }
    public double[] getAuxilliaryFitnessValues() { return new double[] { rank, sparsity }; }

    /** Pareto front rank measure (lower ranks are better) */
    public double rank; //modified by mengxu from int to double

    /** Sparsity along front rank measure (higher sparsity is better) */
    public double sparsity;

    public double[] normalisedObjectives; // values range from 0 (worst) to 1 INCLUSIVE added by mengxu 2023.04.20

    public int[] multiCaseRank; //2023.04.06 by mengxu
    public double[] multiCaseSparsity; //2023.04.06 by mengxu



    public void setCurrentObjective(List<Objective> currentObjectives) {//modified by mengxu
        this.currentObjectives = currentObjectives;
    }

    public void setup(EvolutionState state, Parameter base)
    {
        super.setup(state, base); // unnecessary really

        this.currentState = state; //added by mengxu 2023.04.18

        Parameter def = defaultBase();
        int numInstance;

        int numIns;
        numInstance = state.parameters.getInt(new Parameter(P_NUMCASE), def.push(P_NUMCASE), 0);
        if (numInstance <= 0)
            state.output.fatal("The number of instances must be an integer >= 1.", base.push(P_NUMCASE), def.push(P_NUMCASE));

        int numFitnesses;

        numFitnesses = state.parameters.getInt(base.push(P_NUMOBJECTIVES), def.push(P_NUMOBJECTIVES), 0);
        if (numFitnesses <= 0)
            state.output.fatal("The number of objectives must be an integer >= 1.", base.push(P_NUMOBJECTIVES), def.push(P_NUMOBJECTIVES));


        multiInstanceMultiObjectiveFitness = new double[numInstance][numFitnesses];
        multiInstanceMultiObjectiveFitnessRank = new int[numInstance][numFitnesses];

        multiCaseRank = new int[numInstance];
        multiCaseSparsity = new double[numInstance];

        //add 2021.12.10
        useNoveltyScore = state.parameters.getBoolean(new Parameter(P_USE_NOVELTY_SCORE), null, false);
        if(useNoveltyScore){
            multiInstanceNoveltyScore = new double[numInstance][numFitnesses];
        }

        state.output.exitIfErrors();
    }

    public String fitnessToString()
    {
        return super.fitnessToString() + "\n" + NSGA2_RANK_PREAMBLE + Code.encode(rank) + "\n" + NSGA2_SPARSITY_PREAMBLE + Code.encode(sparsity);
    }

    public String fitnessToStringForHumans()
    {
        return super.fitnessToStringForHumans() + "\n" + NSGA2_RANK_PREAMBLE + rank + "\n" + NSGA2_SPARSITY_PREAMBLE + sparsity;
    }

    public void readFitness(final EvolutionState state, final LineNumberReader reader) throws IOException
    {
        super.readFitness(state, reader);
        rank = Code.readDoubleWithPreamble(NSGA2_RANK_PREAMBLE, state, reader);
        sparsity = Code.readDoubleWithPreamble(NSGA2_SPARSITY_PREAMBLE, state, reader);
    }

    public void writeFitness(final EvolutionState state, final DataOutput dataOutput) throws IOException
    {
        super.writeFitness(state, dataOutput);
//        dataOutput.writeInt(rank);
        dataOutput.writeDouble(rank);
        dataOutput.writeDouble(sparsity);
        writeTrials(state, dataOutput);
    }

    public void readFitness(final EvolutionState state, final DataInput dataInput) throws IOException
    {
        super.readFitness(state, dataInput);
        rank = dataInput.readDouble();
        sparsity = dataInput.readDouble();
        readTrials(state, dataInput);
    }

    public boolean equivalentTo(Fitness _fitness)
    {
        OneInstanceMultiCaseMultiObjectiveFitnessLSMO other = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness;
        return (rank == ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness).rank) &&
                (sparsity == other.sparsity);
    }

    public void normalisation(double[] means, double[] stds)
    {
        int length = this.objectives.length;
        this.normalisedObjectives = new double[length];
//        if(this.objectives[0] >= Double.POSITIVE_INFINITY || this.objectives[0] >= Double.MAX_VALUE){
//            for(int i=0; i<length; i++){
//                this.normalisedObjectives[i] = Double.POSITIVE_INFINITY;
//            }
//        }
//        else{
        for(int i=0; i<length; i++){
            double zScore = (this.objectives[i]-means[i])/stds[i];
//                System.out.println("Zscore: " + zScore);
            double norm = sigmoid(zScore);
//                System.out.println("norm: " + norm);
            this.normalisedObjectives[i] = norm;
        }
//        }
    }

    public double getNormalisedObjectives(int i){
        return this.normalisedObjectives[i];
    }

    public double sigmoid(double s)
    {
        return 1/(1+Math.exp(-s));
    }

    public void normalisationMultiCaseFitness(double[][] meansMulticase, double[][] stdsMulticase)
    {
        int length = this.objectives.length;
        int row = this.multiInstanceMultiObjectiveFitness.length;
        int col = this.multiInstanceMultiObjectiveFitness[0].length;

        this.normalisedmultiInstanceMultiObjectiveFitness = new double[row][col];
        for(int i=0; i<row; i++) {
            for (int j = 0; j < col; j++) {
                double zScore = (this.multiInstanceMultiObjectiveFitness[i][j] - meansMulticase[i][j]) / stdsMulticase[i][j];
//                System.out.println("Zscore: " + zScore);
                double norm = sigmoid(zScore);
//                System.out.println("norm: " + norm);
                this.normalisedmultiInstanceMultiObjectiveFitness[i][j] = norm;
            }
        }
    }


    /**
     * We specify the tournament selection criteria, Rank (lower
     * values are better) and Sparsity (higher values are better)
     */
    public boolean betterThan(Fitness _fitness)
    {
        OneInstanceMultiCaseMultiObjectiveFitnessLSMO other = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness;
        // Rank should always be minimized.
        if (rank < ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness).rank)
            return true;
        else if (rank > ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness).rank)
            return false;

        // otherwise try sparsity
        if (sparsity > ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness).sparsity)
            return true;
        else if (sparsity < ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness).sparsity)
            return false;
//        System.out.println("Better than random as Sparsity same!");
//        return true;
        return this.currentState.random[0].nextBoolean(0.5);
    }

    public boolean equivalentToCase(Fitness _fitness, int indexCase)
    {
        OneInstanceMultiCaseMultiObjectiveFitnessLSMO other = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness;
        return (multiCaseRank[indexCase] == ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness).multiCaseRank[indexCase]) &&
                (multiCaseSparsity[indexCase] == other.multiCaseSparsity[indexCase]);
    }

    /**
     * We specify the tournament selection criteria, Rank (lower
     * values are better) and Sparsity (higher values are better)
     */
    public boolean betterThanCase(Fitness _fitness, int indexCase)
    {
        OneInstanceMultiCaseMultiObjectiveFitnessLSMO other = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness;
        // Rank should always be minimized.
        if (multiCaseRank[indexCase] < ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness).multiCaseRank[indexCase])
            return true;
        else if (multiCaseRank[indexCase] > ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness).multiCaseRank[indexCase])
            return false;

        // otherwise try sparsity
        System.out.println("Better than based on Sparsity!");
        if (multiCaseSparsity[indexCase] > other.multiCaseSparsity[indexCase])
            return true;
        else if (multiCaseSparsity[indexCase] < other.multiCaseSparsity[indexCase])
            return false;
        return this.currentState.random[0].nextBoolean(0.5);
//        return (multiCaseSparsity[indexCase] > other.multiCaseSparsity[indexCase]);
    }



    public double getFitness(int indexInstance, int indexObjective)
    {
        //test
//        System.out.println("length: " +multiInstanceMultiObjectiveFitness.length);
        return multiInstanceMultiObjectiveFitness[indexInstance][indexObjective];
    }

    public double getNoveltyScore(int indexInstance, int indexObjective)
    {
        //test
//        System.out.println("length: " +multiInstanceMultiObjectiveFitness.length);
        return multiInstanceNoveltyScore[indexInstance][indexObjective];
    }

    public void setMultiInstanceFitness(final EvolutionState state, double[][] multiInstanceMultiObjectiveFitness)
    {
        if (multiInstanceMultiObjectiveFitness == null)
        {
            state.output.fatal("Null objective array provided to MultiObjectiveFitness.");
        }

        this.multiInstanceMultiObjectiveFitness = multiInstanceMultiObjectiveFitness;

        //modified by mengxu 2023.04.10
        this.multiCaseRank = new int[multiInstanceMultiObjectiveFitness.length];
        this.multiCaseSparsity = new double[multiInstanceMultiObjectiveFitness.length];

        //add by mengxu for multi-objective 2023.04.07
        for(int i=0; i<objectives.length; i++){
            objectives[i] = this.multiObjectiveFitness()[i];
        }

        //add by mengxu to avoid the same rank for all individuals 2022.04.25
        this.multiInstanceMultiObjectiveFitnessRank = new int[multiInstanceMultiObjectiveFitness.length][1];

//        //check
//        System.out.println("check 2: " + this.multiInstanceMultiObjectiveFitness.length);
//        for(int i=0; i<this.multiInstanceMultiObjectiveFitness.length;i++){
//            System.out.print(this.multiInstanceMultiObjectiveFitness[i][0] + "  ");
//        }
//        System.out.println();
    }

    public double[][] getMultiInstanceMultiObjectiveBinaryBring() {
        return multiInstanceMultiObjectiveBinaryBring;
    }

    public void setMultiInstanceMultiObjectiveBinaryBring(double[][] multiInstanceMultiObjectiveBinaryBring) {
        this.multiInstanceMultiObjectiveBinaryBring = multiInstanceMultiObjectiveBinaryBring;
    }

    public double[][] getMultiInstanceMultiObjectiveNormalisation() {
        return multiInstanceMultiObjectiveNormalisation;
    }

    public void setMultiInstanceMultiObjectiveNormalisation(double[][] multiInstanceMultiObjectiveNormalisation) {
        this.multiInstanceMultiObjectiveNormalisation = multiInstanceMultiObjectiveNormalisation;
    }

    public void setMultiInstanceNoveltyScore(final EvolutionState state, double[][] multiInstanceNoveltyScore)
    {
        this.multiInstanceNoveltyScore = multiInstanceNoveltyScore;
    }

    //added by Mengxu ro compare Pareto Dominates by Case 2023.04.06
    public boolean paretoDominatesCase(OneInstanceMultiCaseMultiObjectiveFitnessLSMO other, int indexInstance)
    {
        boolean abeatsb = false;

        if (objectives.length != other.objectives.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        for (int x = 0; x < objectives.length; x++)
        {
            if (maximize[x] != other.maximize[x])  // uh oh
                throw new RuntimeException(
                        "Attempt made to compare two multiobjective fitnesses; but for objective #" + x +
                                ", one expects higher values to be better and the other expectes lower values to be better.");

            if (maximize[x]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
            {
                if (multiInstanceMultiObjectiveFitness[indexInstance][x] > other.multiInstanceMultiObjectiveFitness[indexInstance][x])
                    abeatsb = true;
                else if (multiInstanceMultiObjectiveFitness[indexInstance][x] < other.multiInstanceMultiObjectiveFitness[indexInstance][x])
                    return false;
            }
            else
            {
                if (multiInstanceMultiObjectiveFitness[indexInstance][x] < other.multiInstanceMultiObjectiveFitness[indexInstance][x]) //objective[0] and objective[1]: [2514.5953504074932, 498.03951619189513]
                    abeatsb = true;
                else if (multiInstanceMultiObjectiveFitness[indexInstance][x] > other.multiInstanceMultiObjectiveFitness[indexInstance][x])
                    return false;
            }
        }

        return abeatsb;
    }

    public String fitnessToStringForHumansMax(int indexObjective){
        String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
        double maxFitness=-1;
        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++) {
            if (maxFitness < multiInstanceMultiObjectiveFitness[x][indexObjective]) {
                maxFitness = multiInstanceMultiObjectiveFitness[x][indexObjective];
            }
        }
        s = s + maxFitness;
        return s + FITNESS_POSTAMBLE;

//        String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
//        double[] maxFitness = new double[objectives.length];
//        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++) {
//            if (maxFitness[0] < multiInstanceMultiObjectiveFitness[x][0]) {
//                maxFitness[0] = multiInstanceMultiObjectiveFitness[x][0];
//                for(int i=1; i<objectives.length; i++){
//                    maxFitness[i] = multiInstanceMultiObjectiveFitness[x][i];
//                }
//            }
//        }
//        s = s + "[" + maxFitness[0] + ",";
//        for(int i=1; i<objectives.length; i++){
//            if(i == objectives.length-1){
//                s = s + maxFitness[i] + "]";
//            }
//            else{
//                s = s + maxFitness[i] + ",";
//            }
//        }
////        s = s + maxFitness;
//        return s + FITNESS_POSTAMBLE;
    }

    public String fitnessToStringForHumansMean(int indexObjective){
        String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
        double sum = 0;
        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
        {
            sum += multiInstanceMultiObjectiveFitness[x][indexObjective];
        }
        sum = sum / multiInstanceMultiObjectiveFitness.length;
        s = s + sum;
        return s + FITNESS_POSTAMBLE;

//        String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
////        double sum = 0;
//        double[] sum = new double[objectives.length];
//        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
//        {
//            sum[0] += multiInstanceMultiObjectiveFitness[x][0];
//            for(int i=1; i<objectives.length; i++){
//                sum[i] = multiInstanceMultiObjectiveFitness[x][i];
//            }
//        }
//        for(int i=0; i<objectives.length; i++) {
//            sum[i] = sum[i] / multiInstanceMultiObjectiveFitness.length;
//        }
////        s = s + sum;
//        s = s + "[" + sum[0] + ",";
//        for(int i=1; i<objectives.length; i++){
//            if(i == objectives.length-1){
//                s = s + sum[i] + "]";
//            }
//            else{
//                s = s + sum[i] + ",";
//            }
//        }
//        return s + FITNESS_POSTAMBLE;
    }

    public double fitnessMax(int indexObjective){
//        double[] maxFitness = new double[objectives.length];
//        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++) {
//            if (maxFitness[0] < multiInstanceMultiObjectiveFitness[x][0]) {
//                maxFitness[0] = multiInstanceMultiObjectiveFitness[x][0];
//                for(int i=1; i<objectives.length; i++){
//                    maxFitness[i] = multiInstanceMultiObjectiveFitness[x][i];
//                }
//            }
//        }
        double maxFitness=-1;
        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++) {
            if (maxFitness < multiInstanceMultiObjectiveFitness[x][indexObjective]) {
                maxFitness = multiInstanceMultiObjectiveFitness[x][indexObjective];
            }
        }
        return maxFitness;
    }

    public double fitnessMean(int indexObjective){
//        double[] sum = new double[objectives.length];
//        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
//        {
//            sum[0] += multiInstanceMultiObjectiveFitness[x][0];
//            for(int i=1; i<objectives.length; i++){
//                sum[i] = multiInstanceMultiObjectiveFitness[x][i];
//            }
//        }
//        for(int i=0; i<objectives.length; i++) {
//            sum[i] = sum[i] / multiInstanceMultiObjectiveFitness.length;
//        }
        double sum = 0;
        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
        {
            sum += multiInstanceMultiObjectiveFitness[x][indexObjective];
        }
        sum = sum / multiInstanceMultiObjectiveFitness.length;
        return sum;
    }

    public double[] multiObjectiveFitness() {
        double[] multiObjectiveFitness = new double[objectives.length];
        for(int i=0; i<objectives.length; i++) {
            Objective objective = currentObjectives.get(i);
            if (objective == MEAN_FLOWTIME || objective == MEAN_WEIGHTED_FLOWTIME || objective == MEAN_TARDINESS || objective == MEAN_WEIGHTED_TARDINESS) {
                multiObjectiveFitness[i] = fitnessMean(i);
            }
            else if(objective == MAX_FLOWTIME || objective == MAX_WEIGHTED_FLOWTIME || objective == MAX_TARDINESS || objective == MAX_WEIGHTED_TARDINESS){
                multiObjectiveFitness[i] = fitnessMax(i);
            }
        }
//        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");
//        double[] bad = new double[objectives.length];
//        for(int i=0; i<objectives.length; i++){
//            bad[i] = Double
//        }
        return multiObjectiveFitness;
    }

//    @Override
//    public String fitnessToStringForHumans()
//    {
//        //version 3
//        String s = "";
//        for(int i=0; i<objectives.length; i++) {
//            Objective objective = currentObjectives.get(i);
//            switch (objective) {
//                case MEAN_FLOWTIME:
//                case MEAN_WEIGHTED_FLOWTIME:
//                case MEAN_TARDINESS:
//                case MEAN_WEIGHTED_TARDINESS:
//                    s = s + fitnessToStringForHumansMean(i);
//                case MAX_FLOWTIME:
//                case MAX_WEIGHTED_FLOWTIME:
//                case MAX_TARDINESS:
//                case MAX_WEIGHTED_TARDINESS:
//                    s = s + fitnessToStringForHumansMax(i);
//            }
//        }
//        return s;
//    }

//    @Override
//    public boolean equivalentTo(Fitness _fitness)
//    {
//        OneInstanceMultiCaseMultiObjectiveFitnessLSMO other = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) _fitness;
//        boolean[][] abeatsb = new boolean[multiInstanceMultiObjectiveFitness.length][objectives.length];
//        Arrays.fill(abeatsb, false);
//        boolean[][] bbeatsa = new boolean[multiInstanceMultiObjectiveFitness.length][objectives.length];
//        Arrays.fill(bbeatsa, false);
////        boolean bbeatsa = false;
//
//        if (multiInstanceMultiObjectiveFitness.length != other.multiInstanceMultiObjectiveFitness.length)
//            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");
//
//        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
//        {
//            for(int i=0; i<objectives.length; i++) {
//                if (maximize[0] != other.maximize[0])  // uh oh//modified by mengxu, just one objective
//                    throw new RuntimeException(
//                            "Attempt made to compare two multiobjective fitnesses; but for objective #" + x +
//                                    ", one expects higher values to be better and the other expectes lower values to be better.");
//
//                if (maximize[0]) {
//                    if (multiInstanceMultiObjectiveFitness[x][i] > other.multiInstanceMultiObjectiveFitness[x][i])
//                        abeatsb[x][i] = true;
//                    if (multiInstanceMultiObjectiveFitness[x][i] < other.multiInstanceMultiObjectiveFitness[x][i])
//                        bbeatsa[x][i] = true;
////                if (abeatsb && bbeatsa)
////                    return true;
//                } else {
//                    if (multiInstanceMultiObjectiveFitness[x][i] < other.multiInstanceMultiObjectiveFitness[x][i])
//                        abeatsb[x][i] = true;
//                    if (multiInstanceMultiObjectiveFitness[x][i] > other.multiInstanceMultiObjectiveFitness[x][i])
//                        bbeatsa[x][i] = true;
////                if (abeatsb && bbeatsa)
////                    return true;
//                }
//            }
//        }
//        boolean allTrue = true;
//        for(int i = 0; i< abeatsb.length; i++){
//            for(int j=0; j<objectives.length; j++){
//                if(!(abeatsb[i][j] && bbeatsa[i][j])){
//                    allTrue = false;
//                    break;
//                }
//            }
//        }
//        if(allTrue){
//            return true;
//        }
//        for(int i = 0; i< abeatsb.length; i++){
//            for(int j=0; j<objectives.length; j++) {
//                if (abeatsb[i][j] || bbeatsa[i][j]) {
//                    return false;
//                }
//            }
//        }
//        return true;
//    }

    /**
     * Returns true if I'm better than _fitness. The DEFAULT rule I'm using is this: if
     * I am better in one or more criteria, and we are equal in the others, then
     * betterThan is true, else it is false. Multiobjective optimization algorithms may
     * choose to override this to do something else.
     */

//    @Override
//    public boolean betterThan(Fitness fitness)
//    {
//        boolean better = true;
//        for(int i=0; i<objectives.length; i++) {
//            Objective objective = currentObjectives.get(i);
//            switch (objective) {
//                case MEAN_FLOWTIME:
//                case MEAN_WEIGHTED_FLOWTIME:
//                case MEAN_TARDINESS:
//                case MEAN_WEIGHTED_TARDINESS:
//                    better = compareInstanceV2mean((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) fitness, i); //todo: double check
//                case MAX_FLOWTIME:
//                case MAX_WEIGHTED_FLOWTIME:
//                case MAX_TARDINESS:
//                case MAX_WEIGHTED_TARDINESS:
//                    better = compareInstanceV2max((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) fitness, i);
//            }
//            if(!better){
//                return false;
//            }
//        }
//        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");
//
//        return better;
//    }

    public boolean betterThanOnPoorCases(EvolutionState state, Fitness fitness)
    {
        //just one objective
        Objective objective = currentObjectives.get(0);
        switch (objective) {
            case MEAN_FLOWTIME:
            case MEAN_WEIGHTED_FLOWTIME:
            case MEAN_TARDINESS:
            case MEAN_WEIGHTED_TARDINESS:
                return compareInstanceV2meanOnPoorCases(state, (OneInstanceMultiCaseMultiObjectiveFitnessLSMO)fitness);
            case MAX_FLOWTIME:
            case MAX_WEIGHTED_FLOWTIME:
            case MAX_TARDINESS:
            case MAX_WEIGHTED_TARDINESS:
                return compareInstanceV2maxOnPoorCases(state, (OneInstanceMultiCaseMultiObjectiveFitnessLSMO)fitness);
        }
        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");

        return compareInstanceV2meanOnPoorCases(state, (OneInstanceMultiCaseMultiObjectiveFitnessLSMO)fitness);
    }

    //add 2021.12.27
    public boolean betterThanBasedOnBinaryBring(Fitness fitness){
        double[][] otherBinaryBring = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)fitness).getMultiInstanceMultiObjectiveBinaryBring();
        double advantage0 = 0;
        double advantage1 = 0;
        for(int i = 0; i< otherBinaryBring.length; i++){
            advantage0 += this.multiInstanceMultiObjectiveBinaryBring[i][0];
            advantage1 += otherBinaryBring[i][0];
        }
        if(advantage0 >= advantage1){
            return true;
        }

        return false;
    }

    //add 2021.01.12
    public boolean betterThanBasedOnNormalisation(Fitness fitness){
        double[][] otherNormalisation = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)fitness).getMultiInstanceMultiObjectiveNormalisation();
        double advantage0 = 0;
        double advantage1 = 0;
        for(int i = 0; i< otherNormalisation.length; i++){
            advantage0 += this.multiInstanceMultiObjectiveNormalisation[i][0];
            advantage1 += otherNormalisation[i][0];
        }
        if(advantage0 <= advantage1){
            return true;
        }

        return false;
    }


    //add 2021.01.12
    public boolean betterThanBasedOnNormalisationOnPoorCases(EvolutionState state, Fitness fitness){
        double[][] otherNormalisation = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)fitness).getMultiInstanceMultiObjectiveNormalisation();
        double advantage0 = 0;
        double advantage1 = 0;

        for(int i = 0; i< otherNormalisation.length; i++){
            advantage0 += this.multiInstanceMultiObjectiveNormalisation[i][0]*((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(i);
            advantage1 += otherNormalisation[i][0]*((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(i);
        }
        if(advantage0 <= advantage1){
            return true;
        }

        return false;
    }

    public boolean compareInstanceV1(OneInstanceMultiCaseMultiObjectiveFitnessLSMO other)
    {
        boolean abeatsb = false;

        if (multiInstanceMultiObjectiveFitness.length != other.multiInstanceMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
        {
            if (maximize[0] != other.maximize[0])  // uh oh
                throw new RuntimeException(
                        "Attempt made to compare two multiobjective fitnesses; but for objective #" + x +
                                ", one expects higher values to be better and the other expectes lower values to be better.");

            if (maximize[0]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
            {
                if (multiInstanceMultiObjectiveFitness[x][0] > other.multiInstanceMultiObjectiveFitness[x][0])
                    abeatsb = true;
                else if (multiInstanceMultiObjectiveFitness[x][0] < other.multiInstanceMultiObjectiveFitness[x][0])
                    return false;
            }
            else
            {
                if (multiInstanceMultiObjectiveFitness[x][0] < other.multiInstanceMultiObjectiveFitness[x][0]) //objective[0] and objective[1]: [2514.5953504074932, 498.03951619189513]
                    abeatsb = true;
                else if (multiInstanceMultiObjectiveFitness[x][0] > other.multiInstanceMultiObjectiveFitness[x][0])
                    return false;
            }
        }

        return abeatsb;
    }

    public boolean compareInstanceV2mean(OneInstanceMultiCaseMultiObjectiveFitnessLSMO other, int indexObjective)
    {
        boolean abeatsb = false;

        if (multiInstanceMultiObjectiveFitness.length != other.multiInstanceMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        if (maximize[0] != other.maximize[0])  // uh oh
            throw new RuntimeException(
                    "Attempt made to compare two multiobjective fitnesses; but for Instance #" + "all" +
                            ", one expects higher values to be better and the other expectes lower values to be better.");

        if (maximize[0]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
        {
            double sum=0;
            double sumOther=0;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                sum += multiInstanceMultiObjectiveFitness[x][indexObjective];//just one objective
                sumOther += other.multiInstanceMultiObjectiveFitness[x][indexObjective];
            }
            if ( sum > sumOther)
                abeatsb = true;
            else if (sum < sumOther)
                return false;
        }
        else
        {
            double sum=0;
            double sumOther=0;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                sum += multiInstanceMultiObjectiveFitness[x][indexObjective];
                sumOther += other.multiInstanceMultiObjectiveFitness[x][indexObjective];
            }
            if ( sum < sumOther)
                abeatsb = true;
            else if (sum > sumOther)
                return false;
        }

        return abeatsb;
    }

    public boolean compareInstanceV2meanOnPoorCases(EvolutionState state, OneInstanceMultiCaseMultiObjectiveFitnessLSMO other)
    {
        boolean abeatsb = false;

        if (multiInstanceMultiObjectiveFitness.length != other.multiInstanceMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        if (maximize[0] != other.maximize[0])  // uh oh
            throw new RuntimeException(
                    "Attempt made to compare two multiobjective fitnesses; but for Instance #" + "all" +
                            ", one expects higher values to be better and the other expectes lower values to be better.");

        if (maximize[0]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
        {
            double sum=0;
            double sumOther=0;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                sum += multiInstanceMultiObjectiveFitness[x][0]*((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(x);//just one objective
                sumOther += other.multiInstanceMultiObjectiveFitness[x][0]*((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(x);
            }
            if ( sum > sumOther)
                abeatsb = true;
            else if (sum < sumOther)
                return false;
        }
        else
        {
            double sum=0;
            double sumOther=0;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                sum += multiInstanceMultiObjectiveFitness[x][0]*((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(x);
                sumOther += other.multiInstanceMultiObjectiveFitness[x][0]*((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(x);
            }
            if ( sum < sumOther)
                abeatsb = true;
            else if (sum > sumOther)
                return false;
        }

        return abeatsb;
    }

    public boolean compareInstanceV2max(OneInstanceMultiCaseMultiObjectiveFitnessLSMO other, int indexObjective)
    {
        boolean abeatsb = false;

        if (multiInstanceMultiObjectiveFitness.length != other.multiInstanceMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        if (maximize[0] != other.maximize[0])  // uh oh
            throw new RuntimeException(
                    "Attempt made to compare two multiobjective fitnesses; but for Instance #" + "all" +
                            ", one expects higher values to be better and the other expectes lower values to be better.");

        if (maximize[0]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
        {
            double maxFitness=Double.MAX_VALUE;
            double maxFitnessOther=Double.MAX_VALUE;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                if(maxFitness > multiInstanceMultiObjectiveFitness[x][indexObjective]){
                    maxFitness = multiInstanceMultiObjectiveFitness[x][indexObjective];
                }
                if(maxFitnessOther > other.multiInstanceMultiObjectiveFitness[x][indexObjective]){
                    maxFitnessOther = other.multiInstanceMultiObjectiveFitness[x][indexObjective];
                }
            }
            if ( maxFitness > maxFitnessOther)
                abeatsb = true;
            else if (maxFitness < maxFitnessOther)
                return false;
        }
        else
        {
            double maxFitness=-1;
            double maxFitnessOther=-1;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                if(maxFitness < multiInstanceMultiObjectiveFitness[x][indexObjective]){
                    maxFitness = multiInstanceMultiObjectiveFitness[x][indexObjective];
                }
                if(maxFitnessOther < other.multiInstanceMultiObjectiveFitness[x][indexObjective]){
                    maxFitnessOther = other.multiInstanceMultiObjectiveFitness[x][indexObjective];
                }
            }
            if ( maxFitness < maxFitnessOther)
                abeatsb = true;
            else if (maxFitness > maxFitnessOther)
                return false;
        }

        return abeatsb;
    }

    public boolean compareInstanceV2maxOnPoorCases(EvolutionState state, OneInstanceMultiCaseMultiObjectiveFitnessLSMO other)
    {
        boolean abeatsb = false;

        if (multiInstanceMultiObjectiveFitness.length != other.multiInstanceMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        if (maximize[0] != other.maximize[0])  // uh oh
            throw new RuntimeException(
                    "Attempt made to compare two multiobjective fitnesses; but for Instance #" + "all" +
                            ", one expects higher values to be better and the other expectes lower values to be better.");

        if (maximize[0]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
        {
            double maxFitness=Double.MAX_VALUE;
            double maxFitnessOther=Double.MAX_VALUE;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                if(((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(x) == 1){
                    if(maxFitness > multiInstanceMultiObjectiveFitness[x][0]){
                        maxFitness = multiInstanceMultiObjectiveFitness[x][0];
                    }
                    if(maxFitnessOther > other.multiInstanceMultiObjectiveFitness[x][0]){
                        maxFitnessOther = other.multiInstanceMultiObjectiveFitness[x][0];
                    }
                }
            }
            if ( maxFitness > maxFitnessOther)
                abeatsb = true;
            else if (maxFitness < maxFitnessOther)
                return false;
        }
        else
        {
            double maxFitness=-1;
            double maxFitnessOther=-1;
            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                if(((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(x) == 1){
                    if(maxFitness < multiInstanceMultiObjectiveFitness[x][0]){
                        maxFitness = multiInstanceMultiObjectiveFitness[x][0];
                    }
                    if(maxFitnessOther < other.multiInstanceMultiObjectiveFitness[x][0]){
                        maxFitnessOther = other.multiInstanceMultiObjectiveFitness[x][0];
                    }
                }

            }
            if ( maxFitness < maxFitnessOther)
                abeatsb = true;
            else if (maxFitness > maxFitnessOther)
                return false;
        }

        return abeatsb;
    }

    public static ArrayList partitionIntoRanksCase(Individual[] inds, int indexCase)
    {
        Individual[] dummy = new Individual[0];
        ArrayList frontsByRank = new ArrayList();//each front is stored by one arraylist

        while(inds.length > 0) //the dominated individuals
        {
            ArrayList front = new ArrayList();
            ArrayList nonFront = new ArrayList();
            partitionIntoParetoFrontCase(inds, front, nonFront, indexCase); //after this, the individuals are divided into two groups: front and nonfront

            // build inds out of remainder
            inds = (Individual[]) nonFront.toArray(dummy);//copy the non-dominated individuals into inds
            frontsByRank.add(front); //add the non-dominated individuals into frontsByRank
        }
        return frontsByRank;
    }

    public static ArrayList partitionIntoParetoFront(Individual[] inds, ArrayList front, ArrayList nonFront)
    {
        if (front == null)
            front = new ArrayList();

        // put the first guy in the front
        front.add(inds[0]);

        // iterate over all the remaining individuals
        for (int i = 1; i < inds.length; i++)
        {
            Individual ind = (Individual) (inds[i]);

            boolean noOneWasBetter = true;
            int frontSize = front.size();

            // iterate over the entire front
            for (int j = 0; j < frontSize; j++)
            {
                Individual frontmember = (Individual) (front.get(j));

                // if the front member is better than the individual, dump the individual and go to the next one
                if (((MultiObjectiveFitness) (frontmember.fitness)).paretoDominates((MultiObjectiveFitness) (ind.fitness)))
                {
                    if (nonFront != null) nonFront.add(ind);
                    noOneWasBetter = false;
                    break;  // failed.  He's not in the front
                }
                // if the individual was better than the front member, dump the front member.  But look over the
                // other front members (don't break) because others might be dominated by the individual as well.
                else if (((MultiObjectiveFitness) (ind.fitness)).paretoDominates((MultiObjectiveFitness) (frontmember.fitness)))
                {
                    yank(j, front);
                    // a front member is dominated by the new individual.  Replace him
                    frontSize--; // member got removed
                    j--;  // because there's another guy we now need to consider in his place
                    if (nonFront != null) nonFront.add(frontmember);
                }
            }
            if (noOneWasBetter)
                front.add(ind);
        }
        return front;
    }

    public static ArrayList partitionIntoParetoFrontCase(Individual[] inds, ArrayList front, ArrayList nonFront, int indexCase)
    {
        if (front == null)
            front = new ArrayList();

        // put the first guy in the front
        front.add(inds[0]);

        // iterate over all the remaining individuals
        for (int i = 1; i < inds.length; i++)
        {
            Individual ind = (Individual) (inds[i]);

            boolean noOneWasBetter = true;
            int frontSize = front.size();

            // iterate over the entire front
            for (int j = 0; j < frontSize; j++)
            {
                Individual frontmember = (Individual) (front.get(j));

                // if the front member is better than the individual, dump the individual and go to the next one
                if (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (frontmember.fitness)).paretoDominatesCase((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (ind.fitness), indexCase))
                {
                    if (nonFront != null) nonFront.add(ind);
                    noOneWasBetter = false;
                    break;  // failed.  He's not in the front
                }
                // if the individual was better than the front member, dump the front member.  But look over the
                // other front members (don't break) because others might be dominated by the individual as well.
                else if (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (ind.fitness)).paretoDominatesCase((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (frontmember.fitness), indexCase))
                {
                    yank(j, front);
                    // a front member is dominated by the new individual.  Replace him
                    frontSize--; // member got removed
                    j--;  // because there's another guy we now need to consider in his place
                    if (nonFront != null) nonFront.add(frontmember);
                }
            }
            if (noOneWasBetter)
                front.add(ind);
        }
        return front;
    }

    static void yank(int val, ArrayList list)
    {
        int size = list.size();
        list.set(val, list.get(size - 1));
        list.remove(size - 1);
    }

    @Override
    public Object clone()
    {
        OneInstanceMultiCaseMultiObjectiveFitnessLSMO f = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (super.clone());
        f.objectives = (double[]) (objectives.clone()); // cloning an array
        f.multiInstanceMultiObjectiveFitness = (double[][]) (multiInstanceMultiObjectiveFitness.clone());
        f.multiCaseRank = (int[]) (multiCaseRank.clone());
        f.multiCaseSparsity = (double[]) (multiCaseSparsity.clone());

        // note that we do NOT clone max and min fitness, or maximizing -- they're shared
        return f;
    }

}
