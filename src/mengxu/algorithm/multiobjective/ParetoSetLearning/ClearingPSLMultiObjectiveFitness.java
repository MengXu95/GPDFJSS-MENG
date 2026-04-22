package mengxu.algorithm.multiobjective.ParetoSetLearning;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;
import ec.util.Parameter;
import yimei.jss.niching.Clearable;

import java.util.Objects;

/**
 * The multi-objective fitness with clearing method for niching.
 *
 * Created by yimei on 21/11/16.
 */
public class ClearingPSLMultiObjectiveFitness
        extends PSLMultiObjectiveFitness implements Clearable {

    private boolean cleared;

    public double rank;

    public double sparsity; // as baseline to very the multi-criteria selection 2024.11.04

    public String compareCriteria; //add by mengxu 2024.10.1

    public boolean useMultiCriteriaSelection;

    public void setup(EvolutionState state, Parameter base)
    {
        super.setup(state, base); // unnecessary really

        compareCriteria = state.parameters.getString(new Parameter("compare-criteria"), null);

        Parameter useMultiCriteriaSelectionParam = new Parameter("use-multi-criteria-selection"); //add by mengxu 2023.01.24
        this.useMultiCriteriaSelection = state.parameters.getBoolean(useMultiCriteriaSelectionParam, null, true);

    }


    @Override //when want to clear population, call method .clear()
    public void clear() {
        for (int i = 0; i < objectives.length; i++) {
            if (maximize[i]) {
                //fzhang 2019.8.22 when set the fitness to Double.POSITIVE_INFINITY, it will be detected by MultiObjectiveFitness (setObject),
                // so better to set it to a big number rather than POSITIVE_INFINITY
                //objectives[i] = Double.NEGATIVE_INFINITY; // when this is a maximize objective, set the bad objective to negative value
                objectives[i] = Double.NEGATIVE_INFINITY;
            }
            else {
                //objectives[i] = Double.POSITIVE_INFINITY; // when this is a minimize objective, set the bad objective to positive value

                //fzhang 2019.8.22 when set the fitness to Double.POSITIVE_INFINITY, it will be detected by MultiObjectiveFitness (setObject),
                // so better to set it to a big number rather than POSITIVE_INFINITY
                objectives[i] = Double.POSITIVE_INFINITY;
            }
        }

        this.rank = Double.POSITIVE_INFINITY;// add by mengxu for Pareto set learning
        this.PSLFitness = Double.POSITIVE_INFINITY;// add by mengxu for Pareto set learning
        this.meanPreferenceRank = Double.POSITIVE_INFINITY;// add by mengxu for Pareto set learning
        this.HVvalue = Double.NEGATIVE_INFINITY;// add by mengxu for Pareto set learning
        this.IGDvalue = Double.POSITIVE_INFINITY;// add by mengxu for Pareto set learning
        this.GDvalue = Double.POSITIVE_INFINITY;// add by mengxu for Pareto set learning
        this.preferenceDiversity = Double.NEGATIVE_INFINITY;// add by mengxu for Pareto set learning
        this.meanDominatedBy = Double.POSITIVE_INFINITY;// add by mengxu for Pareto set learning
        this.directionAlignment = Double.NEGATIVE_INFINITY;// add by mengxu for Pareto set learning
        this.sparsity = Double.NEGATIVE_INFINITY;

        cleared = true;
    }

    //fzhang 2019.8.25 set the estimated fitness to the individuals
    public void surrogateFitness(double estimatedFitness){
        for (int i = 0; i < objectives.length; i++){
            objectives[i] =  estimatedFitness;
        }
    }

    public double getRank() {
        return rank;
    }

    //fzhang 2021.4.15 estimated fitness for multiobjecitve
    public void surrogateFitnessMultiobjective(double[] estimatedFitness){
        for (int i = 0; i < objectives.length; i++){
            objectives[i] =  estimatedFitness[i];
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }

    public void setObjectives(final EvolutionState state, double[] newObjectives) {
//    	System.out.println("setObjectives");
        super.setObjectives(state, newObjectives);

        cleared = false; //fzhang    label whether it is cleared or not
    }

    public void setFitness() {
        for (int i = 0; i < objectives.length; i++) {
                objectives[i] = Double.MAX_VALUE;
            }
        }

//    private double PSLFitness;

    private double meanPreferenceRank;

    private double preferenceDiversity;

    private double HVvalue;

    private double IGDvalue;

    private double GDvalue;

    private double meanDominatedBy; //2024.07.16

    private double directionAlignment; //add by mengxu 2024.08.13

    public double getDirectionAlignment() {
        return directionAlignment;
    }

    public void setDirectionAlignment(double directionAlignment) {
        this.directionAlignment = directionAlignment;
    }

    public void calculateMeanDirectionAlignment(EvolutionState state, double[][] fitnessOfIntermedidate) {
        // Strategy 1: give 5 fixed reference directions
        double[][] referenceDirections = {
                {0.2, 0.8},
                {0.4, 0.6},
                {0.5, 0.5},
                {0.6, 0.4},
                {0.8, 0.2}
        };

        double[] maxObj = ((PSLInitializer) state.initializer).maxObjectives;
        double[] minObj = ((PSLInitializer) state.initializer).minObjectives;
        double[] manualObj = new double[maxObj.length];
        if(((PSLInitializer) state.initializer).normalisation==1 || ((PSLInitializer) state.initializer).normalisation==2){
            for(int i=0; i< maxObj.length; i++){
                manualObj[i] = ((PSLInitializer) state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
            }
        }

        // Normalize objective vectors
        double[][] normalizedObjectiveVectors = new double[fitnessOfIntermedidate.length][manualObj.length];
        for (int i = 0; i < fitnessOfIntermedidate.length; i++) {
            if(((PSLInitializer) state.initializer).normalisation==0){
                normalizedObjectiveVectors[i] = fitnessOfIntermedidate[i];
            }
            else if(((PSLInitializer) state.initializer).normalisation==1 || ((PSLInitializer) state.initializer).normalisation==2){
                for(int j=0; j< manualObj.length; j++){
                    normalizedObjectiveVectors[i][j] = fitnessOfIntermedidate[i][j]/manualObj[j];
                }
            }
            else if(((PSLInitializer) state.initializer).normalisation==4){
                for(int j=0; j< manualObj.length; j++){
                    if(maxObj[j] == minObj[j]){
                        normalizedObjectiveVectors[i][j] = fitnessOfIntermedidate[i][j]/1 - minObj[j]/1;
                    }
                    else{
                        normalizedObjectiveVectors[i][j] = fitnessOfIntermedidate[i][j]/(maxObj[j]-minObj[j]) - minObj[j]/(maxObj[j]-minObj[j]);
                    }
                }
            }
            else if(((PSLInitializer) state.initializer).normalisation==3){
                System.out.println("Error here!");
            }
            else{
                System.out.println("Error here!");
            }
        }


        // Compute similarity of each objective vector with each reference direction
        //todo: need double check 2024.8.13
        double sumSimilarity = 0;
        int groupMemberNum = (normalizedObjectiveVectors.length-1) / referenceDirections.length;
        for (int i = 1; i < normalizedObjectiveVectors.length; i++) {
            int refIndex = (i-1) / groupMemberNum;
            double similarity;
            if(normalizedObjectiveVectors[i][0] >= Double.MAX_VALUE || normalizedObjectiveVectors[i][0] >= Double.POSITIVE_INFINITY){
                similarity = -1;
            }
            else{
                //Important!!! Normalise again to make the solution (s1, s2) to (s1', s2') and s1' + s2' = 1
                double[] normalisedSolution = normalisedToSumOne(state, normalizedObjectiveVectors[i]);
                similarity = cosineSimilarity(normalisedSolution, referenceDirections[refIndex]);
            }
            sumSimilarity = sumSimilarity + similarity;
        }
        this.directionAlignment = sumSimilarity/normalizedObjectiveVectors.length; //should be the larger the better.
    }

    public double[] normalisedToSumOne(EvolutionState state, double[] solution){
        double[] normalisedSolution = new double[solution.length];
        double sum = 0;
        for(int i=0; i<solution.length; i++){
            sum = sum + solution[i]*solution[i];
        }
        sum = Math.sqrt(sum);
        for(int i=0; i<solution.length; i++){
            normalisedSolution[i] = solution[i]/sum;
        }
        return normalisedSolution;
    }

    public void calculateMeanDirectionAlignment(EvolutionState state, double[][] fitnessOfIntermedidate, double[][] referenceDirections) {

        double[] maxObj = ((PSLInitializer) state.initializer).maxObjectives;
        double[] minObj = ((PSLInitializer) state.initializer).minObjectives;
        double[] manualObj = new double[maxObj.length];
        if(((PSLInitializer) state.initializer).normalisation==1 || ((PSLInitializer) state.initializer).normalisation==2){
            for(int i=0; i< maxObj.length; i++){
                manualObj[i] = ((PSLInitializer) state.initializer).curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
            }
        }

        // Normalize objective vectors
        double[][] normalizedObjectiveVectors = new double[fitnessOfIntermedidate.length][manualObj.length];
        for (int i = 0; i < fitnessOfIntermedidate.length; i++) {
            if(((PSLInitializer) state.initializer).normalisation==0){
                normalizedObjectiveVectors[i] = fitnessOfIntermedidate[i];
            }
            else if(((PSLInitializer) state.initializer).normalisation==1 || ((PSLInitializer) state.initializer).normalisation==2){
                for(int j=0; j< manualObj.length; j++){
                    normalizedObjectiveVectors[i][j] = fitnessOfIntermedidate[i][j]/manualObj[j];
                }
            }
            else if(((PSLInitializer) state.initializer).normalisation==4){
                for(int j=0; j< manualObj.length; j++){
                    if(maxObj[j] == minObj[j]){
                        normalizedObjectiveVectors[i][j] = fitnessOfIntermedidate[i][j]/1 - minObj[j]/1;
                    }
                    else{
                        normalizedObjectiveVectors[i][j] = fitnessOfIntermedidate[i][j]/(maxObj[j]-minObj[j]) - minObj[j]/(maxObj[j]-minObj[j]);
                    }
                }
            }
            else if(((PSLInitializer) state.initializer).normalisation==3){
                double[] idealPoints = ((PSLInitializer)(((GPRuleEvolutionStatePSL)state).initializer)).idealPoint;
                double[] nadirPoints = ((PSLInitializer)(((GPRuleEvolutionStatePSL)state).initializer)).nadirPoint;
                for(int j=0; j< idealPoints.length; j++){
                    if(idealPoints[j] == nadirPoints[j]){
                        normalizedObjectiveVectors[i][j] = fitnessOfIntermedidate[i][j]/1 - idealPoints[j]/1;
                    }
                    else{
                        normalizedObjectiveVectors[i][j] = fitnessOfIntermedidate[i][j]/(nadirPoints[j]-idealPoints[j]) - idealPoints[j]/(nadirPoints[j]-idealPoints[j]);
                    }
                }
            }
            else{
                System.out.println("Error in calculateMeanDirectionAlignment()!");
            }
        }

        // Compute similarity of each objective vector with each reference direction
        //todo: need double check 2024.8.13
        double sumSimilarity = 0;
        int groupMemberNum = (normalizedObjectiveVectors.length) / referenceDirections.length;
        for (int i = 1; i < normalizedObjectiveVectors.length; i++) {
            int refIndex = (i) / groupMemberNum;
            double similarity;
            if(normalizedObjectiveVectors[i][0] >= Double.MAX_VALUE || normalizedObjectiveVectors[i][0] >= Double.POSITIVE_INFINITY){
//                similarity = -1;
                continue;
            }
            else{
                //Important!!! Normalise again to make the solution (s1, s2) to (s1', s2') and s1' + s2' = 1
                double[] normalisedSolution = normalisedToSumOne(state, normalizedObjectiveVectors[i]);
                similarity = cosineSimilarity(normalisedSolution, referenceDirections[refIndex]);
            }
            sumSimilarity = sumSimilarity + similarity;
        }
        this.directionAlignment = sumSimilarity/normalizedObjectiveVectors.length; //should be the larger the better.
    }

    // Function to calculate the cosine similarity between two vectors
    public static double cosineSimilarity(double[] v1, double[] v2) {
        return (v1[0] * v2[0] + v1[1] * v2[1]) / (Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1]) * Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1]));
    }


    public boolean betterThan(Fitness fitness)
    {//modified by mengxu 2024.5.27
        //original Strategy 1
//        if(this.HVvalue > ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            return true;
//        }
//        else if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                return true;
//            }
//            else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 2: only PSLfitness value and preference diversity
//        if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//            return true;
//        }
//        else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//            return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//        }
//        else{
//            return false;
//        }

//        //only HV value and preference diversity
//        if(this.HVvalue > ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            return true;
//        }
//        else if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//        }
//        else{
//            return false;
//        }

//        //Strategy 3: good results by this! 2024.6.06 mengxu
//        if(this.rank < ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            return true;
//        }
//        else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                return true;
//            }
//            else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }



        //Strategy 4.1:
//        if(this.HVvalue > ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            return true;
//        }
//        else if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            if(this.rank < ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//                return true;
//            }
//            else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//                if(this.directionAlignment > ((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                    return true;
//                }
//                else if(this.directionAlignment == ((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                    return this.PSLFitness < ((ClearingPSLMultiObjectiveFitness)fitness).getPSLFitness();
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 4.2:
//        if(this.rank > ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            return true;
//        }
//        else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.HVvalue-this.directionAlignment < ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()-((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                return true;
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        if(!this.useMultiCriteriaSelection){
            ClearingPSLMultiObjectiveFitness other = (ClearingPSLMultiObjectiveFitness) fitness;
            // Rank should always be minimized.
            if (rank < other.rank)
                return true;
            else if (rank > other.rank)
                return false;

            // otherwise try sparsity
            return (sparsity > other.sparsity);
        }
        else{
            if(Objects.equals(this.compareCriteria, "HV")){
                //Strategy 4: a little bit better than strategy 3 but not significantly better2024.8.7
                if(this.rank < ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
                    return true;
                }
                else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
                    if(this.HVvalue > ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
                        return true;
                    }
                    else if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
                        if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                            return true;
                        }
                        else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                            return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                        }
                        else{
                            return false;
                        }
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }

            }
            else if(Objects.equals(this.compareCriteria, "IGD")){
                //Strategy 4.3: IGD
                if(this.rank < ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
                    return true;
                }
                else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
                    if(this.IGDvalue < ((ClearingPSLMultiObjectiveFitness)fitness).getIGDvalue()){
                        return true;
                    }
                    else if(this.IGDvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getIGDvalue()){
                        if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                            return true;
                        }
                        else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                            return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                        }
                        else{
                            return false;
                        }
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "GD")){//Strategy 4.4: GD
                if(this.rank < ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
                    return true;
                }
                else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
                    if(this.GDvalue < ((ClearingPSLMultiObjectiveFitness)fitness).getGDvalue()){
                        return true;
                    }
                    else if(this.GDvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getGDvalue()){
                        if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                            return true;
                        }
                        else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                            return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                        }
                        else{
                            return false;
                        }
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "HV_noRank")){//Strategy 4.4: GD
                if(this.HVvalue > ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
                    return true;
                }
                else if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
                    if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                        return true;
                    }
                    else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                        return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "IGD_noRank")){//Strategy 4.4: GD
                if(this.IGDvalue < ((ClearingPSLMultiObjectiveFitness)fitness).getIGDvalue()){
                    return true;
                }
                else if(this.IGDvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getIGDvalue()){
                    if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                        return true;
                    }
                    else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                        return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "GD_noRank")){//Strategy 4.4: GD
                if(this.GDvalue < ((ClearingPSLMultiObjectiveFitness)fitness).getGDvalue()){
                    return true;
                }
                else if(this.GDvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getGDvalue()){
                    if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                        return true;
                    }
                    else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                        return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "weight_sum")){//Strategy 4.4: GD
                if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                    return true;
                }
                else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                    return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                }
                else{
                    return false;
                }
            }
            else{
                System.out.println("Error in betterthan() in ClearingPSLMultiObjectiveFitness!");
                return false;
            }
        }

        //Strategy 9: the results are really bad! 2024.8.19
//        if(this.rank < ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            return true;
//        }
//        else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.directionAlignment > ((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                return true;
//            }
//            else if(this.directionAlignment == ((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                    return true;
//                }
//                else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                    return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 10:
//        if(this.rank < ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            return true;
//        }
//        else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.PSLFitness < ((ClearingPSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                return true;
//            }
//            else if(this.PSLFitness == ((ClearingPSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                if(this.directionAlignment > ((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                    return true;
//                }
//                else if(this.directionAlignment == ((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                    return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 5:
//        if(this.rank < ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            return true;
//        }
//        else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.meanPreferenceRank < ((ClearingPSLMultiObjectiveFitness)fitness).getMeanPreferenceRank()){
//                return true;
//            }
//            else if(this.meanPreferenceRank == ((ClearingPSLMultiObjectiveFitness)fitness).getMeanPreferenceRank()){
//                if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                    return true;
//                }
//                else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                    return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

//        //Strategy 7:
//        if(this.meanDominatedBy < ((ClearingPSLMultiObjectiveFitness)fitness).getMeanDominatedBy()){
//            return true;
//        }
//        else if(this.meanDominatedBy == ((ClearingPSLMultiObjectiveFitness)fitness).getMeanDominatedBy()){
//            if (this.HVvalue > ((ClearingPSLMultiObjectiveFitness) fitness).getHVvalue()) {
//                return true;
//            }
//            else if (this.HVvalue == ((ClearingPSLMultiObjectiveFitness) fitness).getHVvalue()) {
//                if (this.PSLFitness < ((PSLMultiObjectiveFitness) fitness).getPSLFitness()) {
//                    return true;
//                } else if (this.PSLFitness == ((PSLMultiObjectiveFitness) fitness).getPSLFitness()) {
//                    return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness) fitness).getPreferenceDiversity();
//                } else {
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 8: worse than Strategy 7
//        if(this.rank < ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            return true;
//        }
//        else if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.meanDominatedBy < ((ClearingPSLMultiObjectiveFitness)fitness).getMeanDominatedBy()){
//                return true;
//            }
//            else if(this.meanDominatedBy == ((ClearingPSLMultiObjectiveFitness)fitness).getMeanDominatedBy()){
//                if (this.HVvalue > ((ClearingPSLMultiObjectiveFitness) fitness).getHVvalue()) {
//                    return true;
//                }
//                else if (this.HVvalue == ((ClearingPSLMultiObjectiveFitness) fitness).getHVvalue()) {
//                    if (this.PSLFitness < ((PSLMultiObjectiveFitness) fitness).getPSLFitness()) {
//                        return true;
//                    } else if (this.PSLFitness == ((PSLMultiObjectiveFitness) fitness).getPSLFitness()) {
//                        return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness) fitness).getPreferenceDiversity();
//                    } else {
//                        return false;
//                    }
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }
    }

    public boolean equivalentTo(Fitness fitness)
    {//modified by mengxu 2024.5.27
        //original Strategy 1
//        if(this.HVvalue > ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            return true;
//        }
//        else if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                return true;
//            }
//            else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 2: only PSLfitness value and preference diversity
//        if(this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//            return true;
//        }
//        else if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//            return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//        }
//        else{
//            return false;
//        }

//        //only HV value and preference diversity
//        if(this.HVvalue > ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            return true;
//        }
//        else if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            return this.preferenceDiversity > ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//        }
//        else{
//            return false;
//        }

        //Strategy 3:
//        if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }



        //Strategy 4.1:
//        if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//            if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//                if(this.directionAlignment == ((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                    return this.PSLFitness == ((ClearingPSLMultiObjectiveFitness)fitness).getPSLFitness();
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 4.2:
//        if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.HVvalue-this.directionAlignment == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()-((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                return true;
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        if(!this.useMultiCriteriaSelection){
            ClearingPSLMultiObjectiveFitness other = (ClearingPSLMultiObjectiveFitness) fitness;
            // Rank should always be minimized.
            if(rank == other.rank){
                if(sparsity == other.sparsity){
                    return true;
                }
                else{
                    return false;
                }
            }
            return false;
        }
        else{
            if(Objects.equals(this.compareCriteria, "HV")){
                //Strategy 4:
                if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
                    if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
                        if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                            return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                        }
                        else{
                            return false;
                        }
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "IGD")){
                //Strategy 4.3: IGD
                if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
                    if(this.IGDvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getIGDvalue()){
                        if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                            return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                        }
                        else{
                            return false;
                        }
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "GD")){//Strategy 4.4: GD
                //Strategy 4.4: GD
                if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
                    if(this.GDvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getGDvalue()){
                        if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                            return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                        }
                        else{
                            return false;
                        }
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "HV_noRank")){//Strategy 4.4: GD
                if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
                    if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                        return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "IGD_noRank")){//Strategy 4.4: GD
                if(this.IGDvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getIGDvalue()){
                    if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                        return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "GD_noRank")){//Strategy 4.4: GD
                if(this.GDvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getGDvalue()){
                    if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                        return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                    }
                    else{
                        return false;
                    }
                }
                else{
                    return false;
                }
            }
            else if(Objects.equals(this.compareCriteria, "weight_sum")){//Strategy 4.4: GD
                if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
                    return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
                }
                else{
                    return false;
                }
            }
            else{
                System.out.println("Error in equivalentTo() in ClearingPSLMultiObjectiveFitness!");
                return false;
            }
        }



//        //Strategy 9:
//        if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.directionAlignment == ((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                    return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 10:
//        if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.PSLFitness == ((ClearingPSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                if(this.directionAlignment == ((ClearingPSLMultiObjectiveFitness)fitness).getDirectionAlignment()){
//                    return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 5:
//        if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.meanPreferenceRank == ((ClearingPSLMultiObjectiveFitness)fitness).getMeanPreferenceRank()){
//                if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                    return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 6:
//        if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.HVvalue == ((ClearingPSLMultiObjectiveFitness)fitness).getHVvalue()){
//                if(this.meanDominatedBy == ((ClearingPSLMultiObjectiveFitness)fitness).getMeanDominatedBy()){
//                    if(this.PSLFitness == ((PSLMultiObjectiveFitness)fitness).getPSLFitness()){
//                        return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness)fitness).getPreferenceDiversity();
//                    }
//                    else{
//                        return false;
//                    }
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{//consider dominated by 2024.07.16
//            return false;
//        }

//        //Strategy 7:
//        if(this.meanDominatedBy == ((ClearingPSLMultiObjectiveFitness)fitness).getMeanDominatedBy()){
//            if (this.HVvalue == ((ClearingPSLMultiObjectiveFitness) fitness).getHVvalue()) {
//                if (this.PSLFitness == ((PSLMultiObjectiveFitness) fitness).getPSLFitness()) {
//                    return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness) fitness).getPreferenceDiversity();
//                } else {
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }

        //Strategy 8: worse than Strategy 7
//        if(this.rank == ((ClearingPSLMultiObjectiveFitness)fitness).getRank()){
//            if(this.meanDominatedBy == ((ClearingPSLMultiObjectiveFitness)fitness).getMeanDominatedBy()){
//                if (this.HVvalue == ((ClearingPSLMultiObjectiveFitness) fitness).getHVvalue()) {
//                    if (this.PSLFitness == ((PSLMultiObjectiveFitness) fitness).getPSLFitness()) {
//                        return this.preferenceDiversity == ((ClearingPSLMultiObjectiveFitness) fitness).getPreferenceDiversity();
//                    } else {
//                        return false;
//                    }
//                }
//                else{
//                    return false;
//                }
//            }
//            else{
//                return false;
//            }
//        }
//        else{
//            return false;
//        }
    }

    public double getMeanDominatedBy() {
        return meanDominatedBy;
    }

    public void setMeanDominatedBy(double meanDominatedBy) {
        this.meanDominatedBy = meanDominatedBy;
    }

    public double getPSLFitness(){
        return this.PSLFitness;
    }

    public void setPSLFitness(double PSLFitness){
        this.PSLFitness = PSLFitness;
    }

    public double getMeanPreferenceRank() {
        return meanPreferenceRank;
    }

    public void setMeanPreferenceRank(double meanPreferenceRank) {
        this.meanPreferenceRank = meanPreferenceRank;
    }

    public void setHVvalue(double HVvalue){
        this.HVvalue = HVvalue;
    }

    public void setIGDvalue(double IGDvalue){
        this.IGDvalue = IGDvalue;
    }

    public void setGDvalue(double GDvalue){
        this.GDvalue = GDvalue;
    }

    public double getHVvalue() {
        return HVvalue;
    }

    public double getIGDvalue() {
        return IGDvalue;
    }

    public double getGDvalue() {
        return GDvalue;
    }

    public void setPreferenceDiversity(double preferenceDiversity){
        this.preferenceDiversity = preferenceDiversity;
    }

    public double getPreferenceDiversity(){
        return this.preferenceDiversity;
    }


    public void calculatePSLFitness(EvolutionState state)
    {
        int index = state.generation;

        PSLInitializer init = (PSLInitializer) state.initializer;
        double fit;
        if(init.tchebycheff == 3){//PBI add by mengxu 2024.6.10
            fit = init.calculatePenaltyBoundaryIntersectionScore(this, index);
        }
        else if(init.tchebycheff == 2){//augmented Tchebtcheff
            fit = init.calculateAugmentedTchebycheffScore(this, index);
        }
        else if (init.tchebycheff == 1) {//Tchebtcheff
            fit = init.calculateTchebycheffScore(this, index);
        } else {//weighted sum init.tchebycheff == 0
            fit = init.calculateWeightSumScore(this, index);
        }
        this.PSLFitness = fit;
    }

    public Object clone()
    {   // add by mengxu 2024.10.1
        ClearingPSLMultiObjectiveFitness f = (ClearingPSLMultiObjectiveFitness) (super.clone());
        f.objectives = (double[]) (objectives.clone()); // cloning an array
        f.rank = this.rank;// add by mengxu for Pareto set learning
        f.PSLFitness = this.PSLFitness;// add by mengxu for Pareto set learning
        f.meanPreferenceRank = this.meanPreferenceRank;// add by mengxu for Pareto set learning
        f.HVvalue = this.HVvalue;// add by mengxu for Pareto set learning
        f.IGDvalue = this.IGDvalue;// add by mengxu for Pareto set learning
        f.GDvalue = this.GDvalue;// add by mengxu for Pareto set learning
        f.preferenceDiversity = this.preferenceDiversity;// add by mengxu for Pareto set learning
        f.meanDominatedBy = this.meanDominatedBy;// add by mengxu for Pareto set learning
        f.directionAlignment = this.directionAlignment;// add by mengxu for Pareto set learning
        f.compareCriteria = this.compareCriteria;
        f.sparsity = this.sparsity;

        // note that we do NOT clone max and min fitness, or maximizing -- they're shared
        return f;
    }
}
