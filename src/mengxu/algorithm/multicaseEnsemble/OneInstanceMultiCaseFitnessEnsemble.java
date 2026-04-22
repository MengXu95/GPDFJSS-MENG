package mengxu.algorithm.multicaseEnsemble;

import ec.EvolutionState;
import ec.Fitness;
import ec.util.Parameter;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.focusOnPoorCase.GPRuleEvolutionStateDPSPC;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import yimei.jss.jobshop.Objective;

import java.util.Arrays;
import java.util.List;

public class OneInstanceMultiCaseFitnessEnsemble extends OneInstanceMultiCaseMultiObjectiveFitness {
    /** parameter for size of objectives */
    public static final String P_NUMCASE = "num-case";
    public static final String P_USE_NOVELTY_SCORE = "use-novelty-score";

    public double[][] multiInstanceMultiObjectiveFitness;

    public double[][] multiInstanceMultiObjectiveBinaryBring; //add 2021.12.23 for DPS selection

    public double[][] multiInstanceMultiObjectiveNormalisation; //add 2021.01.12 for DPS with new strategy selection

    public boolean useNoveltyScore;
    public double[][] multiInstanceNoveltyScore;//add 2021.12.10
    public List<Objective> currentObjectives; //modified by mengxu

    public int[][] multiInstanceMultiObjectiveFitnessRank;//add 2022.04.25


    public void setCurrentObjective(List<Objective> currentObjectives) {//modified by mengxu
        this.currentObjectives = currentObjectives;
    }

    public void setup(EvolutionState state, Parameter base)
    {
        super.setup(state, base); // unnecessary really

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

        //add 2021.12.10
        useNoveltyScore = state.parameters.getBoolean(new Parameter(P_USE_NOVELTY_SCORE), null, false);
        if(useNoveltyScore){
            multiInstanceNoveltyScore = new double[numInstance][numFitnesses];
        }

        state.output.exitIfErrors();
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

    public String fitnessToStringForHumansMax(){
        String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
        double maxFitness=-1;
        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++) {
            if (maxFitness < multiInstanceMultiObjectiveFitness[x][0]) {
                maxFitness = multiInstanceMultiObjectiveFitness[x][0];
            }
        }
        s = s + maxFitness;
        return s + FITNESS_POSTAMBLE;
    }

    public String fitnessToStringForHumansMean(){
        String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
        double sum = 0;
        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
        {
            sum += multiInstanceMultiObjectiveFitness[x][0];
        }
        sum = sum / multiInstanceMultiObjectiveFitness.length;
        s = s + sum;
        return s + FITNESS_POSTAMBLE;
    }

    public double fitnessMax(){
        double maxFitness=-1;
        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++) {
            if (maxFitness < multiInstanceMultiObjectiveFitness[x][0]) {
                maxFitness = multiInstanceMultiObjectiveFitness[x][0];
            }
        }
        return maxFitness;
    }

    public double fitnessMean(){
        double sum = 0;
        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
        {
            sum += multiInstanceMultiObjectiveFitness[x][0];
        }
        sum = sum / multiInstanceMultiObjectiveFitness.length;
        return sum;
    }

    public double fitness() {
        Objective objective = currentObjectives.get(0);
        switch (objective) {
            case MEAN_FLOWTIME:
            case MEAN_WEIGHTED_FLOWTIME:
            case MEAN_TARDINESS:
            case MEAN_WEIGHTED_TARDINESS:
                return fitnessMean();
            case MAX_FLOWTIME:
            case MAX_WEIGHTED_FLOWTIME:
            case MAX_TARDINESS:
            case MAX_WEIGHTED_TARDINESS:
                return fitnessMax();
        }
        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");
        return Double.MAX_VALUE;
    }

    @Override
    public String fitnessToStringForHumans()
    {
        //version 3
        Objective objective = currentObjectives.get(0);
        switch (objective) {
            case MEAN_FLOWTIME:
            case MEAN_WEIGHTED_FLOWTIME:
            case MEAN_TARDINESS:
            case MEAN_WEIGHTED_TARDINESS:
                return fitnessToStringForHumansMean();
            case MAX_FLOWTIME:
            case MAX_WEIGHTED_FLOWTIME:
            case MAX_TARDINESS:
            case MAX_WEIGHTED_TARDINESS:
                return fitnessToStringForHumansMax();
        }
        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");
        return null;
    }

    @Override
    public boolean equivalentTo(Fitness _fitness)
    {
        OneInstanceMultiCaseFitnessEnsemble other = (OneInstanceMultiCaseFitnessEnsemble) _fitness;
        boolean[] abeatsb = new boolean[multiInstanceMultiObjectiveFitness.length];
        Arrays.fill(abeatsb, false);
        boolean[] bbeatsa = new boolean[multiInstanceMultiObjectiveFitness.length];
        Arrays.fill(bbeatsa, false);
//        boolean bbeatsa = false;

        if (multiInstanceMultiObjectiveFitness.length != other.multiInstanceMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
        {

                if (multiInstanceMultiObjectiveFitness[x][0] < other.multiInstanceMultiObjectiveFitness[x][0])
                    abeatsb[x] = true;
                if (multiInstanceMultiObjectiveFitness[x][0] > other.multiInstanceMultiObjectiveFitness[x][0])
                    bbeatsa[x] = true;

        }
        boolean allTrue = true;
        for(int i = 0; i< abeatsb.length; i++){
            if(!(abeatsb[i] && bbeatsa[i])){
                allTrue = false;
                break;
            }
        }
        if(allTrue){
            return true;
        }
        for(int i = 0; i< abeatsb.length; i++){
            if(abeatsb[i] || bbeatsa[i]){
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if I'm better than _fitness. The DEFAULT rule I'm using is this: if
     * I am better in one or more criteria, and we are equal in the others, then
     * betterThan is true, else it is false. Multiobjective optimization algorithms may
     * choose to override this to do something else.
     */


    /**
     * Returns true if I'm better than _fitness. The DEFAULT rule I'm using is this: if
     * I am better in one or more criteria, and we are equal in the others, then
     * betterThan is true, else it is false. Multiobjective optimization algorithms may
     * choose to override this to do something else.
     */
    @Override
    public boolean betterThan(Fitness fitness)
    {
        return paretoDominates((OneInstanceMultiCaseFitnessEnsemble)fitness);
    }

    /**
     * Returns true if I'm better than _fitness. The rule I'm using is this: if
     * I am better in one or more criteria, and we are equal in the others, then
     * betterThan is true, else it is false.
     */

    public boolean paretoDominates(OneInstanceMultiCaseFitnessEnsemble other)
    {
        boolean abeatsb = false;

        if (multiInstanceMultiObjectiveFitness.length != other.multiInstanceMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++)
        {
            if (multiInstanceMultiObjectiveFitness[x][0] < other.multiInstanceMultiObjectiveFitness[x][0]) //objective[0] and objective[1]: [2514.5953504074932, 498.03951619189513]
                abeatsb = true;
            else if (multiInstanceMultiObjectiveFitness[x][0] > other.multiInstanceMultiObjectiveFitness[x][0])
                return false;

        }

        return abeatsb;
    }

    public boolean betterThanOnCase(EvolutionState state, Fitness fitness, int caseId)
    {
        //just one objective
        Objective objective = currentObjectives.get(0);
        switch (objective) {
            case MEAN_FLOWTIME:
            case MEAN_WEIGHTED_FLOWTIME:
            case MEAN_TARDINESS:
            case MEAN_WEIGHTED_TARDINESS:
                return compareInstanceV2meanOnCase(state, (OneInstanceMultiCaseFitnessEnsemble)fitness, caseId);
            case MAX_FLOWTIME:
            case MAX_WEIGHTED_FLOWTIME:
            case MAX_TARDINESS:
            case MAX_WEIGHTED_TARDINESS:
                return compareInstanceV2maxOnCase(state, (OneInstanceMultiCaseFitnessEnsemble)fitness, caseId);
        }
        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");

        return compareInstanceV2mean((OneInstanceMultiCaseFitnessEnsemble)fitness);
    }

    //add 2021.12.27
    public boolean betterThanBasedOnBinaryBring(Fitness fitness){
        double[][] otherBinaryBring = ((OneInstanceMultiCaseFitnessEnsemble)fitness).getMultiInstanceMultiObjectiveBinaryBring();
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
        double[][] otherNormalisation = ((OneInstanceMultiCaseFitnessEnsemble)fitness).getMultiInstanceMultiObjectiveNormalisation();
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
        double[][] otherNormalisation = ((OneInstanceMultiCaseFitnessEnsemble)fitness).getMultiInstanceMultiObjectiveNormalisation();
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

    public boolean compareInstanceV1(OneInstanceMultiCaseFitnessEnsemble other)
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

    public boolean compareInstanceV2mean(OneInstanceMultiCaseFitnessEnsemble other)
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
                sum += multiInstanceMultiObjectiveFitness[x][0];//just one objective
                sumOther += other.multiInstanceMultiObjectiveFitness[x][0];
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
                sum += multiInstanceMultiObjectiveFitness[x][0];
                sumOther += other.multiInstanceMultiObjectiveFitness[x][0];
            }
            if ( sum < sumOther)
                abeatsb = true;
            else if (sum > sumOther)
                return false;
        }

        return abeatsb;
    }

    public boolean compareInstanceV2meanOnCase(EvolutionState state, OneInstanceMultiCaseFitnessEnsemble other, int caseId)
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
//            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                sum = multiInstanceMultiObjectiveFitness[caseId][0];//just one objective
                sumOther = other.multiInstanceMultiObjectiveFitness[caseId][0];
//            }
            if ( sum > sumOther)
                abeatsb = true;
            else if (sum < sumOther)
                return false;
        }
        else
        {
            double sum=0;
            double sumOther=0;
//            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
                sum = multiInstanceMultiObjectiveFitness[caseId][0];
                sumOther = other.multiInstanceMultiObjectiveFitness[caseId][0];
//            }
            if ( sum < sumOther)
                abeatsb = true;
            else if (sum > sumOther)
                return false;
        }

        return abeatsb;
    }

    public boolean compareInstanceV2max(OneInstanceMultiCaseFitnessEnsemble other)
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
                if(maxFitness > multiInstanceMultiObjectiveFitness[x][0]){
                    maxFitness = multiInstanceMultiObjectiveFitness[x][0];
                }
                if(maxFitnessOther > other.multiInstanceMultiObjectiveFitness[x][0]){
                    maxFitnessOther = other.multiInstanceMultiObjectiveFitness[x][0];
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
                if(maxFitness < multiInstanceMultiObjectiveFitness[x][0]){
                    maxFitness = multiInstanceMultiObjectiveFitness[x][0];
                }
                if(maxFitnessOther < other.multiInstanceMultiObjectiveFitness[x][0]){
                    maxFitnessOther = other.multiInstanceMultiObjectiveFitness[x][0];
                }
            }
            if ( maxFitness < maxFitnessOther)
                abeatsb = true;
            else if (maxFitness > maxFitnessOther)
                return false;
        }

        return abeatsb;
    }

    public boolean compareInstanceV2maxOnCase(EvolutionState state, OneInstanceMultiCaseFitnessEnsemble other, int caseId)
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
            maxFitness = multiInstanceMultiObjectiveFitness[caseId][0];
            maxFitnessOther = other.multiInstanceMultiObjectiveFitness[caseId][0];
//            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
//                if(((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(x) == 1){
//                    if(maxFitness > multiInstanceMultiObjectiveFitness[x][0]){
//                        maxFitness = multiInstanceMultiObjectiveFitness[x][0];
//                    }
//                    if(maxFitnessOther > other.multiInstanceMultiObjectiveFitness[x][0]){
//                        maxFitnessOther = other.multiInstanceMultiObjectiveFitness[x][0];
//                    }
//                }
//            }
            if ( maxFitness > maxFitnessOther)
                abeatsb = true;
            else if (maxFitness < maxFitnessOther)
                return false;
        }
        else
        {
            double maxFitness=-1;
            double maxFitnessOther=-1;
            maxFitness = multiInstanceMultiObjectiveFitness[caseId][0];
            maxFitnessOther = other.multiInstanceMultiObjectiveFitness[caseId][0];
//            for (int x = 0; x < multiInstanceMultiObjectiveFitness.length; x++){
//                if(((GPRuleEvolutionStateDPSPC)state).poorCasesForGen.get(x) == 1){
//                    if(maxFitness < multiInstanceMultiObjectiveFitness[x][0]){
//                        maxFitness = multiInstanceMultiObjectiveFitness[x][0];
//                    }
//                    if(maxFitnessOther < other.multiInstanceMultiObjectiveFitness[x][0]){
//                        maxFitnessOther = other.multiInstanceMultiObjectiveFitness[x][0];
//                    }
//                }
//
//            }
            if ( maxFitness < maxFitnessOther)
                abeatsb = true;
            else if (maxFitness > maxFitnessOther)
                return false;
        }

        return abeatsb;
    }

}
