package mengxu.algorithm.averageFitness;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import yimei.jss.jobshop.Objective;

import java.util.Arrays;
import java.util.List;

public class AverageMultiCaseMultiObjectiveFitness extends MultiObjectiveFitness {
    /** parameter for size of objectives */
    public static final String P_NUMCASE = "num-case";
    public static final String P_WORKFOR = "work-for";
    String workFor;

    public double[][] averageMultiCaseMultiObjectiveFitness;
    public List<Objective> currentObjectives; //modified by mengxu

    public void setCurrentObjective(List<Objective> currentObjectives) {//modified by mengxu
        this.currentObjectives = currentObjectives;
    }

    public void setup(EvolutionState state, Parameter base)
    {
        super.setup(state, base); // unnecessary really

        Parameter def = defaultBase();

        int numCase = state.parameters.getInt(new Parameter(P_NUMCASE), def.push(P_NUMCASE), 0);
        if (numCase <= 0)
            state.output.fatal("The number of instances must be an integer >= 1.", base.push(P_NUMCASE), def.push(P_NUMCASE));

        int numObjectives = state.parameters.getInt(base.push(P_NUMOBJECTIVES), def.push(P_NUMOBJECTIVES), 0);
        if (numObjectives <= 0)
            state.output.fatal("The number of objectives must be an integer >= 1.", base.push(P_NUMOBJECTIVES), def.push(P_NUMOBJECTIVES));

        averageMultiCaseMultiObjectiveFitness = new double[numCase][numObjectives];

        workFor = state.parameters.getStringWithDefault(new Parameter(P_WORKFOR), def.push(P_WORKFOR), "elitismAndParent");


        state.output.exitIfErrors();
    }

    public double getFitness(int indexCase, int indexObjective)
    {
        return averageMultiCaseMultiObjectiveFitness[indexCase][indexObjective];
    }

    public void setMultiCaseFitness(final EvolutionState state, double[][] averageMultiCaseMultiObjectiveFitness)
    {
        if (averageMultiCaseMultiObjectiveFitness == null)
        {
            state.output.fatal("Null objective array provided to MultiObjectiveFitness.");
        }

        this.averageMultiCaseMultiObjectiveFitness = averageMultiCaseMultiObjectiveFitness;
//        //check
//        System.out.println("check 2: " + this.multiInstanceMultiObjectiveFitness.length);
//        for(int i=0; i<this.multiInstanceMultiObjectiveFitness.length;i++){
//            System.out.print(this.multiInstanceMultiObjectiveFitness[i][0] + "  ");
//        }
//        System.out.println();
    }

    public String fitnessToStringForHumansMax(){
        String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
        double maxFitness=-1;
        for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++) {
            if (maxFitness < averageMultiCaseMultiObjectiveFitness[x][0]) {
                maxFitness = averageMultiCaseMultiObjectiveFitness[x][0];
            }
        }
        s = s + maxFitness;
        return s + FITNESS_POSTAMBLE;
    }

    public String fitnessToStringForHumansMean(){
        String s = FITNESS_PREAMBLE + MULTI_FITNESS_POSTAMBLE;
        double sum = 0;
        for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++)
        {
            sum += averageMultiCaseMultiObjectiveFitness[x][0];
        }
        sum = sum / averageMultiCaseMultiObjectiveFitness.length;
        s = s + sum;
        return s + FITNESS_POSTAMBLE;
    }

    public double fitnessMax(){
        double maxFitness=-1;
        for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++) {
            if (maxFitness < averageMultiCaseMultiObjectiveFitness[x][0]) {
                maxFitness = averageMultiCaseMultiObjectiveFitness[x][0];
            }
        }
        return maxFitness;
    }

    public double fitnessMean(){
        double sum = 0;
        for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++)
        {
            sum += averageMultiCaseMultiObjectiveFitness[x][0];
        }
        sum = sum / averageMultiCaseMultiObjectiveFitness.length;
        return sum;
    }

    public double fitness() {
        Objective objective = currentObjectives.get(0);
        switch (objective) {
            case MEAN_FLOWTIME:
            case MEAN_WEIGHTED_FLOWTIME:
            case MEAN_TARDINESS:
            case MEAN_WEIGHTED_TARDINESS:
            case MAX_FLOWTIME:
            case MAX_WEIGHTED_FLOWTIME:
            case MAX_TARDINESS:
            case MAX_WEIGHTED_TARDINESS:
                return fitnessMean();
        }
        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");
        return Double.MAX_VALUE;
    }

    @Override
    public String fitnessToStringForHumans()
    {
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
        AverageMultiCaseMultiObjectiveFitness other = (AverageMultiCaseMultiObjectiveFitness) _fitness;
        boolean[] abeatsb = new boolean[averageMultiCaseMultiObjectiveFitness.length];
        Arrays.fill(abeatsb, false);
        boolean[] bbeatsa = new boolean[averageMultiCaseMultiObjectiveFitness.length];
        Arrays.fill(bbeatsa, false);
//        boolean bbeatsa = false;

        if (averageMultiCaseMultiObjectiveFitness.length != other.averageMultiCaseMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++)
        {
            if (maximize[0] != other.maximize[0])  // uh oh//modified by mengxu, just one objective
                throw new RuntimeException(
                        "Attempt made to compare two multiobjective fitnesses; but for objective #" + x +
                                ", one expects higher values to be better and the other expectes lower values to be better.");

            if (maximize[0])
            {
                if (averageMultiCaseMultiObjectiveFitness[x][0] > other.averageMultiCaseMultiObjectiveFitness[x][0])
                    abeatsb[x] = true;
                if (averageMultiCaseMultiObjectiveFitness[x][0] < other.averageMultiCaseMultiObjectiveFitness[x][0])
                    bbeatsa[x] = true;
//                if (abeatsb && bbeatsa)
//                    return true;
            }
            else
            {
                if (averageMultiCaseMultiObjectiveFitness[x][0] < other.averageMultiCaseMultiObjectiveFitness[x][0])
                    abeatsb[x] = true;
                if (averageMultiCaseMultiObjectiveFitness[x][0] > other.averageMultiCaseMultiObjectiveFitness[x][0])
                    bbeatsa[x] = true;
//                if (abeatsb && bbeatsa)
//                    return true;
            }
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

    public boolean betterThanAverage(Fitness fitness)
    {
        //just one objective
        Objective objective = currentObjectives.get(0);
        switch (objective) {
            case MEAN_FLOWTIME:
            case MEAN_WEIGHTED_FLOWTIME:
            case MEAN_TARDINESS:
            case MEAN_WEIGHTED_TARDINESS:
            case MAX_FLOWTIME:
            case MAX_WEIGHTED_FLOWTIME:
            case MAX_TARDINESS:
            case MAX_WEIGHTED_TARDINESS:
                return compareInstanceV2mean((AverageMultiCaseMultiObjectiveFitness)fitness);
        }
        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");

        return compareInstanceV2mean((AverageMultiCaseMultiObjectiveFitness)fitness);
    }

    public boolean betterThanNormal(Fitness fitness)
    {
        //just one objective
        Objective objective = currentObjectives.get(0);
        switch (objective) {
            case MEAN_FLOWTIME:
            case MEAN_WEIGHTED_FLOWTIME:
            case MEAN_TARDINESS:
            case MEAN_WEIGHTED_TARDINESS:
                return compareInstanceV2mean((AverageMultiCaseMultiObjectiveFitness)fitness);
            case MAX_FLOWTIME:
            case MAX_WEIGHTED_FLOWTIME:
            case MAX_TARDINESS:
            case MAX_WEIGHTED_TARDINESS:
                return compareInstanceV2max((AverageMultiCaseMultiObjectiveFitness)fitness);
        }
        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");

        return compareInstanceV2mean((AverageMultiCaseMultiObjectiveFitness)fitness);
    }

    /**
     * Returns true if I'm better than _fitness. The DEFAULT rule I'm using is this: if
     * I am better in one or more criteria, and we are equal in the others, then
     * betterThan is true, else it is false. Multiobjective optimization algorithms may
     * choose to override this to do something else.
     */

    @Override
    public boolean betterThan(Fitness fitness)
    {
        //just one objective
        Objective objective = currentObjectives.get(0);
        if(this.workFor.equals("parent")){
            return betterThanNormal(fitness);
        }
        else if(this.workFor.equals("elitismAndParent") || this.workFor.equals("elitism")){
            return betterThanAverage(fitness);
        }
        else{
            System.out.println("error happen, AverageMultiCaseMultiObjectiveFitness!");
            return betterThanNormal(fitness);
        }
    }

    public boolean compareInstanceV1(AverageMultiCaseMultiObjectiveFitness other)
    {
        boolean abeatsb = false;

        if (averageMultiCaseMultiObjectiveFitness.length != other.averageMultiCaseMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++)
        {
            if (maximize[0] != other.maximize[0])  // uh oh
                throw new RuntimeException(
                        "Attempt made to compare two multiobjective fitnesses; but for objective #" + x +
                                ", one expects higher values to be better and the other expectes lower values to be better.");

            if (maximize[0]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
            {
                if (averageMultiCaseMultiObjectiveFitness[x][0] > other.averageMultiCaseMultiObjectiveFitness[x][0])
                    abeatsb = true;
                else if (averageMultiCaseMultiObjectiveFitness[x][0] < other.averageMultiCaseMultiObjectiveFitness[x][0])
                    return false;
            }
            else
            {
                if (averageMultiCaseMultiObjectiveFitness[x][0] < other.averageMultiCaseMultiObjectiveFitness[x][0]) //objective[0] and objective[1]: [2514.5953504074932, 498.03951619189513]
                    abeatsb = true;
                else if (averageMultiCaseMultiObjectiveFitness[x][0] > other.averageMultiCaseMultiObjectiveFitness[x][0])
                    return false;
            }
        }

        return abeatsb;
    }

    public boolean compareInstanceV2mean(AverageMultiCaseMultiObjectiveFitness other)
    {
        boolean abeatsb = false;

        if (averageMultiCaseMultiObjectiveFitness.length != other.averageMultiCaseMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        if (maximize[0] != other.maximize[0])  // uh oh
            throw new RuntimeException(
                    "Attempt made to compare two multiobjective fitnesses; but for Instance #" + "all" +
                            ", one expects higher values to be better and the other expectes lower values to be better.");

        if (maximize[0]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
        {
            double sum=0;
            double sumOther=0;
            for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++){
                sum += averageMultiCaseMultiObjectiveFitness[x][0];//just one objective
                sumOther += other.averageMultiCaseMultiObjectiveFitness[x][0];
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
            for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++){
                sum += averageMultiCaseMultiObjectiveFitness[x][0];
                sumOther += other.averageMultiCaseMultiObjectiveFitness[x][0];
            }
            if ( sum < sumOther)
                abeatsb = true;
            else if (sum > sumOther)
                return false;
        }

        return abeatsb;
    }

    public boolean compareInstanceV2max(AverageMultiCaseMultiObjectiveFitness other)
    {
        boolean abeatsb = false;

        if (averageMultiCaseMultiObjectiveFitness.length != other.averageMultiCaseMultiObjectiveFitness.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        if (maximize[0] != other.maximize[0])  // uh oh
            throw new RuntimeException(
                    "Attempt made to compare two multiobjective fitnesses; but for Instance #" + "all" +
                            ", one expects higher values to be better and the other expectes lower values to be better.");

        if (maximize[0]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
        {
            double maxFitness=Double.MAX_VALUE;
            double maxFitnessOther=Double.MAX_VALUE;
            for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++){
                if(maxFitness > averageMultiCaseMultiObjectiveFitness[x][0]){
                    maxFitness = averageMultiCaseMultiObjectiveFitness[x][0];
                }
                if(maxFitnessOther > other.averageMultiCaseMultiObjectiveFitness[x][0]){
                    maxFitnessOther = other.averageMultiCaseMultiObjectiveFitness[x][0];
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
            for (int x = 0; x < averageMultiCaseMultiObjectiveFitness.length; x++){
                if(maxFitness < averageMultiCaseMultiObjectiveFitness[x][0]){
                    maxFitness = averageMultiCaseMultiObjectiveFitness[x][0];
                }
                if(maxFitnessOther < other.averageMultiCaseMultiObjectiveFitness[x][0]){
                    maxFitnessOther = other.averageMultiCaseMultiObjectiveFitness[x][0];
                }
            }
            if ( maxFitness < maxFitnessOther)
                abeatsb = true;
            else if (maxFitness > maxFitnessOther)
                return false;
        }

        return abeatsb;
    }

}
