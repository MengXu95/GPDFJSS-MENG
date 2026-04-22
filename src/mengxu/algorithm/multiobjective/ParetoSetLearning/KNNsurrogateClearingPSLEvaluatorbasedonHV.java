package mengxu.algorithm.multiobjective.ParetoSetLearning;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.multiobjective.GPRuleEvolutionStateMO;
import mengxu.algorithm.multiobjective.NSGPII.NSGA2BreederNoEnvironmentalSelection;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.niching.Clearable;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.multitreeClearingFirst;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class KNNsurrogateClearingPSLEvaluatorbasedonHV extends PSLEvaluator{

    public static final String P_RADIUS = "radius";
    public static final String P_CAPACITY = "capacity";

    double[][][] tempfitnessesForModel; //for transfer the fitness into the surrogate model to estimate the fitness
    double[][][] tempindsCharListsMultiTree;

    protected boolean clear = true;

    public static List<Double> entropyDiversity = new ArrayList<>();
    protected static long jobSeed;

    protected double radius;
    protected int capacity;

    public double[][] trueParetofront;

    public Object[] trueParetofrontIndividuals;

    //protected PhenoCharacterisation[] phenoCharacterisation;
    public static PhenoCharacterisation[] phenoCharacterisation;

    public double getRadius() {
        return radius;
    }

    public int getCapacity() {
        return capacity;
    }

    public int numPreferencesForHV;

    public boolean useKneePoint;
    public Individual kneePointIndividual;

    public boolean useMultiCriteriaSelection;

    public PhenoCharacterisation[] getPhenoCharacterisation() {
        return phenoCharacterisation;
    }

    public double[][][][] lastGenPhenotypicOfIntermedidatePopPSL = null;
    public double[][][][] lastGenFitnessOfIntermedidatePopPSL = null;
    //original for PSL
//    public int originalPopSize = -1;

    // as baseline to very the multi-criteria selection 2024.11.04
    public int originalPopSize[];

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        radius = state.parameters.getDoubleWithDefault(
                base.push(P_RADIUS), null, 0.0);
        capacity = state.parameters.getIntWithDefault(
                base.push(P_CAPACITY), null, 1);
        String filePath = state.parameters.getString(new Parameter("filePath"), null);

        Parameter printGapParam = new Parameter("num-preferences-for-HV"); //add by mengxu 2023.01.24
        this.numPreferencesForHV = state.parameters.getIntWithDefault(printGapParam, null, 11);

        Parameter useKneePointParam = new Parameter("use-knee-point"); //add by mengxu 2023.01.24
        this.useKneePoint = state.parameters.getBoolean(useKneePointParam, null, false);

        Parameter useMultiCriteriaSelectionParam = new Parameter("use-multi-criteria-selection"); //add by mengxu 2023.01.24
        this.useMultiCriteriaSelection = state.parameters.getBoolean(useMultiCriteriaSelectionParam, null, true);

        //original for PSL
//        Parameter originalPopSizeParam = new Parameter("pop.subpop.0.size"); //add by mengxu 2023.01.24
//        this.originalPopSize = state.parameters.getIntWithDefault(originalPopSizeParam, null, -1);

        // as baseline to very the multi-criteria selection 2024.11.04
        Parameter p = new Parameter(Initializer.P_POP);
        int subpopsLength = state.parameters.getInt(p.push(Population.P_SIZE), null, 1);
        Parameter p_subpop;
        originalPopSize = new int[subpopsLength];
        for (int i = 0; i < subpopsLength; i++) {
            p_subpop = p.push(Population.P_SUBPOP).push("" + i).push(Subpopulation.P_SUBPOPSIZE);
            originalPopSize[i] = state.parameters.getInt(p_subpop, null, 1);
        }
        // as baseline to very the multi-criteria selection 2024.11.04
    }


    public void evaluatePopulation(final EvolutionState state)
    {
            if(((GPRuleEvolutionStatePSL)state).useBroodRecombination && state.generation>0){
                //add by mengxu 2024.9.26 evaluate using surrogate and delete poor and duplicated individuals
                preselectionUsingSurrogate(state);
            }
           //CLear all the fitness related information
            Individual[] indsPop = state.population.subpops[0].individuals;
            for(Individual ind: indsPop){
                ind.evaluated = false;
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).rank = Double.POSITIVE_INFINITY;
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setHVvalue(Double.NEGATIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setIGDvalue(Double.POSITIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setGDvalue(Double.POSITIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setPSLFitness(Double.POSITIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setPreferenceDiversity(Double.NEGATIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setMeanDominatedBy(Double.NEGATIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setMeanPreferenceRank(Double.POSITIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setDirectionAlignment(Double.NEGATIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).sparsity = Double.NEGATIVE_INFINITY;
            }

            super.evaluatePopulation(state); //the same as normal evaluation

            if(!this.useMultiCriteriaSelection){
                // as baseline to very the multi-criteria selection 2024.11.04
                for (int x = 0; x < state.population.subpops.length; x++)
                    state.population.subpops[x].individuals = buildArchive(state, x);
                // as baseline to very the multi-criteria selection 2024.11.04
            }

        if (((PSLInitializer) state.initializer).normalisation == 1) {//add by mengxu 2022.10.02
            SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
            AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem) prob).getEvaluationModel();
            SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel) evaluationModel).getSchedulingSet();
            curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
            ((PSLInitializer) state.initializer).curSchedulingSetObjectiveLowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
        } else if (((PSLInitializer) state.initializer).normalisation == 2) {//add by mengxu 2022.10.06
            RealMatrix adaptLowerBoundMtx = new Array2DRowRealMatrix(((PSLInitializer) state.initializer).numObjectives, 1);

            Individual[] inds = state.population.subpops[0].individuals;
            for (int j = 0; j < ((PSLInitializer) state.initializer).numObjectives; j++) {
                double max = Double.POSITIVE_INFINITY;
                for (int i = 0; i < inds.length; i++) {
                    Individual ind = inds[i];
                    double[] objectivesBeforeNormalisation = ((MultiObjectiveFitness) ind.fitness).getObjectives();
                    if (objectivesBeforeNormalisation[j] < max) {
                        max = objectivesBeforeNormalisation[j];
                    }
                }
                adaptLowerBoundMtx.setEntry(j, 0, max);
            }

            ((PSLInitializer) state.initializer).curSchedulingSetObjectiveLowerBoundMtx = adaptLowerBoundMtx;
        }


        //update idealPoint 2021.11.03
        ArrayList ranks = assignFrontRanks(state.population.subpops[0]);//only suitable for one subpopulation!! by mengxu 2024.6.4
        ((PSLInitializer) state.initializer).updateIdealPoint(state);
//            Object[] sortedParetoFront = buildParetoFront(state);
        Individual[] dummy = new Individual[0];
        Individual[] sortedParetoFront = (Individual[]) ((ArrayList) (ranks.get(0))).toArray(dummy);
        ((PSLInitializer) state.initializer).updateNadirPoint(sortedParetoFront);
        ((PSLInitializer) state.initializer).updateMaxObjectives(state);
        ((PSLInitializer) state.initializer).updateMinObjectives(state);

        //add by mengxu 2024.8.13
        if(this.useKneePoint){
            int kneePointIndex = findKneePoint(state, sortedParetoFront);
            this.kneePointIndividual = sortedParetoFront[kneePointIndex];
        }

        //add by mengxu 2024.5.20
        this.trueParetofront = transferParetofront(sortedParetoFront);
        this.trueParetofrontIndividuals = sortedParetoFront;

        //print ideal points and nadir points
        System.out.println("ideal points: [" + ((PSLInitializer) state.initializer).idealPoint[0] + ", " + ((PSLInitializer) state.initializer).idealPoint[1] + "]");

        System.out.println("nadir points: [" + ((PSLInitializer) state.initializer).nadirPoint[0] + ", " + ((PSLInitializer) state.initializer).nadirPoint[1] + "]");

        System.out.println("max objectives: [" + ((PSLInitializer) state.initializer).maxObjectives[0] + ", " + ((PSLInitializer) state.initializer).maxObjectives[1] + "]");

        System.out.println("min objectives: [" + ((PSLInitializer) state.initializer).minObjectives[0] + ", " + ((PSLInitializer) state.initializer).minObjectives[1] + "]");

        //calculate the augmented fitness value by mengxu 2024.3.12
        Individual[] inds = state.population.subpops[0].individuals;
        for (int i = 0; i < inds.length; i++) {
            Individual ind = inds[i];
            ((ClearingPSLMultiObjectiveFitness) ind.fitness).calculatePSLFitness(state);
        }

        PopulationUtils.sortForParetoSortLearning(state.population);//todo: double check whether this is using the PSL fitness


        //-----generate random preference for calculating HV--strategy 1--------
        int numObjective = ((MultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness).getNumObjectives();
        double[][] preferences = new double[this.numPreferencesForHV][numObjective];//todo: need to update this
        //the first preference should be the preference of the current preference
        preferences[0]= ((PSLInitializer)state.initializer).weights[state.generation];
        for(int i=0; i< preferences.length-1; i++){
            if (numObjective == 2) {
                double[] weightVector = new double[2];
                weightVector[0] = i / (double) (preferences.length-1-1);
                weightVector[1] = (preferences.length-1 -1 - i) / (double) (preferences.length-1-1);
                preferences[i+1] = weightVector;
            }
            else{
                System.out.println("Error in surrogateClearingPSLEvaluator!");
            }
        }

        multitreeClearingFirst.clearPopulationForParetoSetLearningBasedOnHV(state, radius, capacity,
                ((GPRuleEvolutionStatePSL)state).phenoCharacterisation, preferences);

    }

    /**
     * Build the auxiliary fitness data and reduce the subpopulation to just the
     * archive, which is returned.
     */
    //achieve a archive has the same size of original population
    public Individual[] buildArchive(EvolutionState state, int subpop) {
        Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
        ArrayList ranks = assignFrontRanks(state.population.subpops[subpop]); //after this, get different several ranks
        //each rank cosists of the corresponding individuals
        ArrayList newSubpopulation = new ArrayList(); //a new one, size = 0
        int size = ranks.size();
        for (int i = 0; i < size; i++) { //do for each rank separately
            Individual[] rank = (Individual[]) ((ArrayList) (ranks.get(i))).toArray(dummy);
//			if(rank.length > 5){
//				System.out.print("rank size > 5");
//			}
//			assignSparsity(rank); //assign sparity value for each individual in this rank
//			System.out.println("Print HERE!!!!!!");
            assignSparsity(rank); //assign sparity value for each individual in this rank
            if (rank.length + newSubpopulation.size() >= originalPopSize[subpop]) {
                // first sort the rank by sparsity---from the small one to large one
                ec.util.QuickSort.qsort(rank, new SortComparator() {
                    public boolean lt(Object a, Object b) { //Returns true if a < b, else false
                        Individual i1 = (Individual) a;
                        Individual i2 = (Individual) b;
                        return (((ClearingPSLMultiObjectiveFitness) i1.fitness).sparsity > ((ClearingPSLMultiObjectiveFitness) i2.fitness).sparsity);
                    }

                    public boolean gt(Object a, Object b) { //Returns true if a > b, else false
                        Individual i1 = (Individual) a;
                        Individual i2 = (Individual) b;
                        return (((ClearingPSLMultiObjectiveFitness) i1.fitness).sparsity < ((ClearingPSLMultiObjectiveFitness) i2.fitness).sparsity);
                    }
                });

                // then put the m sparsest individuals in the new population
                int m = originalPopSize[subpop] - newSubpopulation.size(); //how many positions left for new individuals
                for (int j = 0; j < m; j++)
                    newSubpopulation.add(rank[j]); //add some individuals based on the sparisity

                // and bail
                break;
            } else {
                // dump in everyone
                for (int j = 0; j < rank.length; j++) //add the while rank directly
                    newSubpopulation.add(rank[j]);
            }
        }

        Individual[] archive = (Individual[]) (newSubpopulation.toArray(dummy));

        // maybe force reevaluation
        PSLBreederKeepParetoFront breeder = (PSLBreederKeepParetoFront) (state.breeder);
        if (breeder.reevaluateElites[subpop])
            for (int i = 0; i < archive.length; i++)
                archive[i].evaluated = false;

        return archive;
    }

    public void assignSparsity(Individual[] front) {
        int numObjectives = ((ClearingPSLMultiObjectiveFitness) front[0].fitness).getObjectives().length;

        for (int i = 0; i < front.length; i++)
            ((ClearingPSLMultiObjectiveFitness) front[i].fitness).sparsity = 0;

        for (int i = 0; i < numObjectives; i++) {
            final int o = i;
            // 1. Sort front by each objective.
            // 2. Sum the manhattan distance of an individual's neighbours over
            // each objective.
            // NOTE: No matter which objectives objective you sort by, the
            // first and last individuals will always be the same (they maybe
            // interchanged though). This is because a Pareto front's
            // objective values are strictly increasing/decreasing.
            ec.util.QuickSort.qsort(front, new SortComparator() {
                public boolean lt(Object a, Object b) {
                    Individual i1 = (Individual) a;
                    Individual i2 = (Individual) b;
                    return (((ClearingPSLMultiObjectiveFitness) i1.fitness)
                            .getObjective(o) < ((ClearingPSLMultiObjectiveFitness) i2.fitness).getObjective(o));
                }

                public boolean gt(Object a, Object b) {
                    Individual i1 = (Individual) a;
                    Individual i2 = (Individual) b;
                    return (((ClearingPSLMultiObjectiveFitness) i1.fitness)
                            .getObjective(o) > ((ClearingPSLMultiObjectiveFitness) i2.fitness).getObjective(o));
                }
            });

            //fzhang 2018.11.22 normize the objective to calculate the sparsity
            final double min = ((ClearingPSLMultiObjectiveFitness) front[0].fitness).getObjective(o);
            final double max = ((ClearingPSLMultiObjectiveFitness) front[front.length - 1].fitness).getObjective(o);
            //todo: this means the NSGPII still use the fitness normalisation 2023.02.14 by mengxu
            //todo: might modify this part

//            System.out.println("normalised min: " + min );
//            System.out.println("normalised max: " + max );

            // Compute and assign sparsity.
            // the first and last individuals are the sparsest.
            ((ClearingPSLMultiObjectiveFitness) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
            ((ClearingPSLMultiObjectiveFitness) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
            for (int j = 1; j < front.length - 1; j++) {
                ClearingPSLMultiObjectiveFitness f_j = (ClearingPSLMultiObjectiveFitness) (front[j].fitness);
                ClearingPSLMultiObjectiveFitness f_jplus1 = (ClearingPSLMultiObjectiveFitness) (front[j + 1].fitness);
                ClearingPSLMultiObjectiveFitness f_jminus1 = (ClearingPSLMultiObjectiveFitness) (front[j - 1].fitness);

//				System.out.println("original min: " + f_j.minObjective[o] );
//			    System.out.println("original max: " + f_j.maxObjective[o] );

                if (max ==  min)
                {
                    f_j.sparsity += 0;
                }
                else {
                    // store the NSGA2Sparsity in sparsity
//				if((f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min) < 0){
//					System.out.println("Error here!");
//				}
                    f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min);
                }

                //original version
//				System.out.println(f_j.maxObjective[o] - f_j.minObjective[o]);  //1
                // store the NSGA2Sparsity in sparsity
				/*f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o))
						/ (f_j.maxObjective[o] - f_j.minObjective[o]);*/
            }
        }
    }

    public void preselectionUsingSurrogate(EvolutionState state){
        //step 1: calculate PC
        int numObjective = ((MultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness).getNumObjectives();
        double[][] preferences = new double[1][numObjective];//todo: need to update this
        //the first preference should be the preference of the current preference
        preferences[0]= ((PSLInitializer)state.initializer).weights[state.generation];
        PhenoCharacterisation[] pc = ((GPRuleEvolutionStatePSL)state).phenoCharacterisation;
        double[][][][] phenotypicOfIntermedidatePopPSL = multitreeClearingFirst.phenotypicPopulationPreferenceFixedDecisions(state, preferences, pc,false);

        //step 2: estimate fitness
        for(int subpop=0; subpop<state.population.subpops.length; subpop++){
            Individual[] inds = state.population.subpops[subpop].individuals;
            double[][][] indsPCsSubpop = phenotypicOfIntermedidatePopPSL[subpop];
            double[][][] lastGenIndsPCsSubpop = this.lastGenPhenotypicOfIntermedidatePopPSL[subpop];
            for(int i=0; i<inds.length; i++){
                Individual ind = inds[i];
                double[] eachIndPCSubpop = indsPCsSubpop[i][0];
                double min_dis = Double.MAX_VALUE;
                int min_index = -1;
                for(int j=0; j<lastGenIndsPCsSubpop.length; j++){
                    double[] lastGenEachIndPCSubpop = lastGenIndsPCsSubpop[j][0];
                    double dis  = PhenoCharacterisation.distance(eachIndPCSubpop, lastGenEachIndPCSubpop);
                    if(dis==0){
                        min_dis = dis;
                        min_index = j;
                        break;
                    }
                    if(dis < min_dis){
                        min_dis = dis;
                        min_index = j;
                    }
                }
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).rank = this.lastGenFitnessOfIntermedidatePopPSL[subpop][min_index][0][3];
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setHVvalue(Double.NEGATIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setIGDvalue(Double.POSITIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setGDvalue(Double.POSITIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setPSLFitness(this.lastGenFitnessOfIntermedidatePopPSL[subpop][min_index][0][2]);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setPreferenceDiversity(Double.NEGATIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setMeanDominatedBy(Double.NEGATIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setMeanPreferenceRank(Double.POSITIVE_INFINITY);
                ((ClearingPSLMultiObjectiveFitness)ind.fitness).setDirectionAlignment(Double.NEGATIVE_INFINITY);
            }
        }

        //step 3: sort population and clear duplicated individuals
        boolean clear = true;
        if(clear) {
            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
                Individual[] sortedPop = state.population.subpops[subpop].individuals;

                //sort
                multitreeClearingFirst.sortPopBasedOnTheSameBetterThan(sortedPop, phenotypicOfIntermedidatePopPSL[subpop]);

                //fzhang 2018.10.2 calculate the distance of each individual and the reference rule
                List<double[]> sortedPopCharLists = new ArrayList<>();
                for (int i = 0;  i < phenotypicOfIntermedidatePopPSL[subpop].length; i++) {
                    //where the examined rule set the chosen operation by reference rule  for example: 3. means examined rule set the chosen operation by reference rule as the third one
                    double[] charList = phenotypicOfIntermedidatePopPSL[subpop][i][0];
                    sortedPopCharLists.add(charList);
                }

                int clearedInds = 0;
                // clear this subpopulation
                for (int i = 0; i < sortedPop.length; i++) {
                    // skip the cleared individuals
                    if (((Clearable) sortedPop[i].fitness).isCleared()) {
                        continue;
                    }

                    int numWinners = 1;
                    for (int j = i + 1; j < sortedPop.length; j++) {
                        // skip the cleared individuals
                        if (((Clearable) sortedPop[j].fitness).isCleared()) {
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
                            numWinners++;
                        } else {
                            // Clear the fitness of individual j
                            ((Clearable) sortedPop[j].fitness).clear();
                            //fzhang 2019.9.11
                            sortedPop[j].evaluated = true;
                            clearedInds++;
                        }
                    }
                }
                System.out.println("Preselection cleared number: " + clearedInds);
            }
        }

        //step 4: resize to the original size
        PopulationUtils.sortForParetoSortLearning(state.population);//todo: double check whether this is using the PSL fitness
        for(int subpop=0; subpop<state.population.subpops.length; subpop++){
            if (originalPopSize[subpop] < state.population.subpops[subpop].individuals.length)  // let's resize!
            {
                state.output.message("Subpop " + subpop + " reduced " + state.population.subpops[subpop].individuals.length + " -> " + originalPopSize[subpop]);
                state.population.subpops[subpop].resize(originalPopSize[subpop]);
            }
        }
    }

    // Function to find the knee point index
    public int findKneePoint(EvolutionState state, Individual[] sortedParetoFront) {
        if(sortedParetoFront.length==1){
            return 0;
        }
        else if (sortedParetoFront.length==2){
            if(state.random[0].nextBoolean()){
                return 0;
            }
            else{
                return 1;
            }
        }
        else if(sortedParetoFront.length==3){
            return 1;
        }

        List<Double> angles = new ArrayList<>();

        for (int i = 1; i < sortedParetoFront.length - 1; i++) {
            double[] p1 = ((ClearingPSLMultiObjectiveFitness)sortedParetoFront[i - 1].fitness).objectives;
            double[] p2 = ((ClearingPSLMultiObjectiveFitness)sortedParetoFront[i].fitness).objectives;
            double[] p3 = ((ClearingPSLMultiObjectiveFitness)sortedParetoFront[i + 1].fitness).objectives;

            // Euclidean distances
            double a = distance(p1, p2);
            double b = distance(p2, p3);
            double c = distance(p1, p3);

            // Using the law of cosines to calculate the angle
            double angle = Math.acos((a * a + b * b - c * c) / (2 * a * b));
            angles.add(angle);
        }

        // Find the point with the smallest angle (knee point)
        double minAngle = Double.POSITIVE_INFINITY;
        int minIndex = 0;

        for (int i = 0; i < angles.size(); i++) {
            if (angles.get(i) < minAngle) {
                minAngle = angles.get(i);
                minIndex = i + 1; // +1 because angles list starts from the second point
            }
        }

        return minIndex;
    }

    // Helper function to calculate Euclidean distance
    public double distance(double[] p1, double[] p2) {
        return Math.sqrt(Math.pow(p1[0] - p2[0], 2) + Math.pow(p1[1] - p2[1], 2));
    }


    public ArrayList assignFrontRanks(Subpopulation subpop) {
        Individual[] inds = subpop.individuals; //inds includes all individuals
        ArrayList frontsByRank = MultiObjectiveFitness.partitionIntoRanks(inds);

        int numRanks = frontsByRank.size();
        for (int rank = 0; rank < numRanks; rank++) {
            ArrayList front = (ArrayList) (frontsByRank.get(rank));
            int numInds = front.size();
            for (int ind = 0; ind < numInds; ind++)
                ((ClearingPSLMultiObjectiveFitness) (((Individual) (front.get(ind))).fitness)).rank = rank;
        }
        return frontsByRank;
    }

    public void calculatePC(final EvolutionState state){
        int numObjectives = 1;//this fitness is not the multi-objective fitness but the fitness of augmented Tchebycheff one by mengxu 2024.3.19
        double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(state, phenoCharacterisation, true); //3. calculate the phenotypic characteristic
        //get the fitness for training model
        double[][][] fitnessesForModel = new double[state.population.subpops.length][state.population.subpops[0].individuals.length][numObjectives];

        //fzhang 2021.4.16 calculate the entropy diversity by using phenotypic characteristic
        double diversityValue = PopulationUtils.entropy(indsCharListsMultiTree);
        entropyDiversity.add(diversityValue);
        if(state.generation == state.numGenerations-2){ //do not need to breed in the last generation
            jobSeed = ((GPRuleEvolutionState)state).getJobSeed();
            writeDiversityToFile(jobSeed, state.numGenerations, state.population.subpops.length);
        }

        for(int subpop = 0; subpop < state.population.subpops.length; subpop++){
            for(int ind = 0; ind < state.population.subpops[subpop].individuals.length; ind++){
                for(int fitness = 0; fitness < numObjectives; fitness++){
                    fitnessesForModel[subpop][ind][fitness] = ((MultiObjectiveFitness)(state.population.subpops[subpop].individuals[ind].fitness)).getObjective(fitness);
                }
            }

            // int removeIdx = 0;
            double[][] indsCharListsMultiTreeSubpop = indsCharListsMultiTree[subpop];
            double[][] fitnessesForModelSubpop = fitnessesForModel[subpop];

            for (int i = 0; i < fitnessesForModelSubpop.length; i++) {
                //fzhang 2021.4.15 if either of an individual's objective value is Double.MAX_VALUE, we do not use this individuals in surrogate
                if (fitnessesForModelSubpop[i][0] == Double.MAX_VALUE || fitnessesForModelSubpop[i][0] == Double.POSITIVE_INFINITY) {
                   /* indsCharListsMultiTree = ArrayUtils.remove(indsCharListsMultiTree, i - removeIdx);
                    fitnessesForModel = ArrayUtils.remove(fitnessesForModel, i - removeIdx);*/
                    indsCharListsMultiTreeSubpop = ArrayUtils.remove(indsCharListsMultiTreeSubpop, i);
                    fitnessesForModelSubpop = ArrayUtils.remove(fitnessesForModelSubpop, i);
                    i--;
                    // removeIdx++;
                }
            }
            indsCharListsMultiTree[subpop] = indsCharListsMultiTreeSubpop;
            fitnessesForModel[subpop] = fitnessesForModelSubpop;
        }

        tempfitnessesForModel = fitnessesForModel;
        tempindsCharListsMultiTree = indsCharListsMultiTree;
    }

    public double[][] transferParetofront(Object[] paretofront){
        double[][] pareto = new double[paretofront.length][2];
        for(int i=0; i<paretofront.length; i++){
            double objective0 = ((MultiObjectiveFitness) (((Individual) paretofront[i]).fitness)).getObjective(0);
            double objective1 = ((MultiObjectiveFitness) (((Individual) paretofront[i]).fitness)).getObjective(1);
            pareto[i][0] = objective0;
            pareto[i][1] = objective1;
        }
        return pareto;
    }


    public Object[] buildParetoFront(EvolutionState state){
        List<Object[]> sortedFronts = new ArrayList();
        for (int s = 0; s < state.population.subpops.length; s++)
        {
            MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[s].individuals[0].fitness);
            // build front
            ArrayList front = typicalFitness.partitionIntoParetoFront(state.population.subpops[s].individuals, null, null);

            // sort by objective[0]
            Object[] sortedFront = front.toArray();
            QuickSort.qsort(sortedFront, new SortComparator()
            {
                public boolean lt(Object a, Object b)
                {
                    return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) <
                            (((MultiObjectiveFitness) ((Individual) b).fitness)).getObjective(0));
                }

                public boolean gt(Object a, Object b)
                {
                    return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) >
                            ((MultiObjectiveFitness) (((Individual) b).fitness)).getObjective(0));
                }
            });
            sortedFronts.add(sortedFront);
        }
        return sortedFronts.get(0); //todo: only suitable for one subpopulation, need to modify when there are more than one subproblems.
    }

    //2021.4.16 fzhang save the diversity value to csv
    public static void writeDiversityToFile(long jobSeed, int numGenerations, int numSubpops) {
        //fzhang 2019.5.21 save the number of cleared individuals
        File weightFile = new File("job." + jobSeed + ".diversity.csv"); // jobSeed = 0
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(weightFile));
            writer.write("Gen, diversitySubpop0");
            writer.newLine();
            for (int i = 0; i < entropyDiversity.size(); i += numSubpops) { //every two into one generation
                //writer.newLine();
                writer.write(i/numSubpops + ", " + entropyDiversity.get(i) + "\n");
            }
            writer.write(numGenerations -1 + ", " + 0 + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
