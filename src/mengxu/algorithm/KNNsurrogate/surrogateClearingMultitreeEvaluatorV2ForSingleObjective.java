package mengxu.algorithm.KNNsurrogate;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import org.apache.commons.lang3.ArrayUtils;
import yimei.jss.algorithm.multiobjective.NSGA2EvaluatorAssignSparsity;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.niching.*;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by fzhang on 2019.9.24.
 */
//a class can not extend from more than one class
//2020.2.13 using KNN surrogate separately for each subpop.
public class surrogateClearingMultitreeEvaluatorV2ForSingleObjective extends NSGA2EvaluatorAssignSparsity {
    public static final String P_RADIUS = "radius";
    public static final String P_CAPACITY = "capacity";

    public static final String P_NUMOBJECTIVES = "num-objectives";
    //fzhang 2018.10.9 to get the pre-generation value
    public static final String P_PRE_GENERATIONS = "pre-generations";
    //ArrayList<Double[]> fitnessesForModel = new ArrayList<>();
    double[][][] tempfitnessesForModel; //for transfer the fitness into the surrogate model to estimate the fitness
    double[][][] tempindsCharListsMultiTree;

    protected boolean clear = true;
    protected boolean nonIntermediatePop = true;

    public static List<Double> entropyDiversity = new ArrayList<>();
    protected static long jobSeed;

    protected double radius;
    protected int capacity;

    //protected PhenoCharacterisation[] phenoCharacterisation;
    public static PhenoCharacterisation[] phenoCharacterisation;

    public double getRadius() {
        return radius;
    }

    public int getCapacity() {
        return capacity;
    }

    public PhenoCharacterisation[] getPhenoCharacterisation() {
        return phenoCharacterisation;
    }

    public PhenoCharacterisation getPhenoCharacterisation(int index) {
        return phenoCharacterisation[index];
    }
    int numObjectives = -1;
    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        radius = state.parameters.getDoubleWithDefault(
                base.push(P_RADIUS), null, 0.0);
        capacity = state.parameters.getIntWithDefault(
                base.push(P_CAPACITY), null, 1);
        String filePath = state.parameters.getString(new Parameter("filePath"), null);
        //It's a little tricky to know whether we have 1 or 2 populations here, so we will assume
        //2 for the purpose of the phenoCharacterisation, and ignore the second object if only
        //1 is used
        phenoCharacterisation = new PhenoCharacterisation[2];

        //add by mengxu 2022.03.17
        Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);

        //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
        if (filePath == null) {
            //dynamic simulation
            phenoCharacterisation[0] =
                    SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
            phenoCharacterisation[1] =
                    RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
        } else {
            //static simulation
            phenoCharacterisation[0] =
                    SequencingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
            phenoCharacterisation[1] =
                    RoutingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
        }

        //2021.4.15 get the number of objectives
        numObjectives = state.parameters.getIntWithDefault(new Parameter(P_NUMOBJECTIVES), null, 0);
        if (numObjectives == 0) {
            System.err.println("ERROR:");
            System.err.println("No objective is specified.");
            System.exit(1);
        }
    }

    @Override
    public void evaluatePopulation(final EvolutionState state) {

        //if it is a normal evaluation
        if (nonIntermediatePop) {
            super.evaluatePopulation(state);  //the pop is sorted automatically
            //2021.4.15 for multi-objective, it does not make sense to sort the population. This code is used for single objective.
            //if used for multi-objective, the individuals will be sorted based on the first objective.
            //PopulationUtils.sort(state.population);
            double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(state, phenoCharacterisation, nonIntermediatePop); //3. calculate the phenotypic characteristic
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
            nonIntermediatePop = false;
        } else {
            //clear is because there are many dumplicated individuals in the intermediate population
            multitreeClearingFirst.clearPopulation(state, radius, capacity, phenoCharacterisation);//the individuals do not have fitness or they are evaluated yet
                                                                                                   //but has a fitness there
            this.evaluatePopulation(state, tempindsCharListsMultiTree, tempfitnessesForModel);
            nonIntermediatePop = true;
            }
        }

    //==========================================KNN========================================
    //2020.2.8 assign fitness to all the individuals based on KNN with subpopulation K, then sort the intermediate population, and select the top k as the offspring in subpopulation K.
    public void evaluatePopulation(final EvolutionState state, double[][][] indsCharListsMultiTree, double[][][] fitnessesForModel) {

        //double[][][] indsCharListsIntermediatePop = phenotypicForSurrogate.phenotypicPopulation(state, phenoCharacterisation, nonIntermediatePop); //3. calculate the phenotypic characteristic
        double[][][] indsCharListsIntermediatePop =  multitreeClearingFirst.phenotypicOfIntermedidatePop;//when clear the population, it is already be calculated.
        //2020.2.10 put the offspring of all the subpopulations together as a pool, and evaluate them based on surrogate model 1, and choose the top |P| to subpopulation 1
        //then, evaluate all the individuals based on surrogate model 2, and choose the top |P| to subpopulation 2
        //then, evaluate all the individuals based on surrogate model 3, and choose the top |P| to subpopulation 3 ... and so on

        //step1: put all the offspring and their pc together
/*        ArrayList<Individual> individuals = new ArrayList<>();//all the individuals in the population
        ArrayList<double[]> indsCharListsIntermediatePopAllPop = new ArrayList<>();//the all pc of individuals in the population
        for (int sub = 0; sub < state.population.subpops.length; sub++) {
            for (int ind = 0; ind < state.population.subpops[sub].individuals.length; ind++){
                individuals.add(state.population.subpops[sub].individuals[ind]);
                indsCharListsIntermediatePopAllPop.add(indsCharListsIntermediatePop[sub][ind]);
            }
        }*/

        //step2: evaluate all individuals based on the first KNN
        for (int sub = 0; sub < state.population.subpops.length; sub++) {
            for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //
            {
                Individual individual = state.population.subpops[sub].individuals[i]; //the examined individual
                //if(indsCharListsMultiTree.length != 0){
                    //KNN
                    //===============================start==============================================
                    double dMin  = Double.MAX_VALUE;
                    int index = 0;
                    if(individual.evaluated == false){
                        double[] pcIntermediate = indsCharListsIntermediatePop[sub][i]; //the pc of the examined individual
                        //calculate the fitness based on surrogate model
                        for(int pc = 0; pc < indsCharListsMultiTree[sub].length; pc++){
                            double[] pcModel = indsCharListsMultiTree[sub][pc];
                            double d  = PhenoCharacterisation.distance(pcIntermediate, pcModel);
                            if(d == 0){
                                index = pc;
                                break;
                            }
                            if (d < dMin){
                                dMin = d;
                                index = pc;
                            }
                        }
                        double[] estimatedFitness = new double[numObjectives];
                        for (int obj = 0; obj < numObjectives; obj++){
                            estimatedFitness[obj] = fitnessesForModel[sub][index][obj];
                        }

                       ((Clearable)individual.fitness).surrogateFitnessMultiobjective(estimatedFitness); //2021.4.15 for mutliobjective
                        //((Clearable)individual.fitness).surrogateFitness(fitnessesForModel[sub][index]); //fzhang 2021.4.15 for single objective
                        //individual.evaluated = true;
                        //individual.fitness.trials = new ArrayList();
                        //individual.fitness.trials.add(individual.fitness.fitness());

                        //for check the accuracy of the KNN surrogate model by meng 2022.03.28===========
//                        System.out.println("KNN estimate fitness: " + estimatedFitness[0]);
//                        individual.evaluated = false;
//                        SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
//                        prob.evaluate(state, individual, 0, 0);
//                        System.out.println("Real fitness: " + ((ClearingMultiObjectiveFitness)individual.fitness).objectives[0]);
//                        System.out.println("error: " + (((ClearingMultiObjectiveFitness)individual.fitness).objectives[0] - estimatedFitness[0]));
//                        System.out.println();
                        //================================================================================

                    }
                }
/*                else{
                    ((Clearable)individual.fitness).surrogateFitness(Double.MAX_VALUE);
                }*/

    //step3: choose the top |P| individuals as the offspring of the current subpop
            //sort the individuals based on the fitnesses
            //Individual[] inds = PopulationUtils.sortInds(individuals);
            //System.arraycopy(inds, 0, state.population.subpops[sub].individuals, 0, state.population.subpops[sub].individuals.length);
            }
        }
    //}

    //===================SVM=======================
/*    public void evaluatePopulation(final EvolutionState state, double[][] indsCharListsMultiTree, double[] fitnessesForModel) {

        double[][] indsCharListsIntermediatePop = phenotypicForSurrogate.phenotypicPopulation(state, phenoCharacterisation, nonIntermediatePop); //3. calculate the phenotypic characteristic

        for (int sub = 0; sub < state.population.subpops.length; sub++) {
            //if there is training data, train the model. Otherwise, do not need to train model, just assign them the same fitness (Double.MAX_VALUE)
            if (indsCharListsMultiTree.length != 0) {
                //===============================start==============================================
                GaussianKernel mercerKernel = new GaussianKernel(100);
                SVR.Trainer<double[]> trainer = new SVR.Trainer<double[]>(mercerKernel, 0.001, 1); //change the C from 1 to 10, no effect on fitness
                SVR<double[]> network = trainer.train(indsCharListsMultiTree, fitnessesForModel);

                for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
                {
                    Individual individual = state.population.subpops[sub].individuals[i];
                    if (individual.evaluated == false) {
                        double[] pcIntermediate = indsCharListsIntermediatePop[i];
                        //calculate the fitness based on surrogate model
                        double estimatedFitness = network.predict(pcIntermediate);
                        ((Clearable) individual.fitness).surrogateFitness(estimatedFitness);
                    }
                }
            } else {// do not use the surrogate, in this way, equals to use the original way to get offsprings
                for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
                {
                    Individual individual = state.population.subpops[sub].individuals[i];
                    if (individual.evaluated == false) {
                        ((Clearable) individual.fitness).surrogateFitness(Double.MAX_VALUE - 1000); //just remove the duplicated ones
                    }
                }
            }
        }
    }*/


  //=====================================RBF==========================================2019.9.15
/*  public void evaluatePopulation(final EvolutionState state, double[][] indsCharListsMultiTree, double[] fitnessesForModel) {

      double[][] indsCharListsIntermediatePop = phenotypicForSurrogate.phenotypicPopulation(state, phenoCharacterisation, nonIntermediatePop); //3. calculate the phenotypic characteristic

      for (int sub = 0; sub < state.population.subpops.length; sub++) {
          //if there is training data, train the model. Otherwise, do not need to train model, just assign them the same fitness (Double.MAX_VALUE)
          int maxNeighbour = (int) Math.round(0.01 * 10 * (indsCharListsMultiTree.length - 10)); //insure the RBF can work
          if (indsCharListsMultiTree.length != 0 && maxNeighbour >= 1) {
              //===============================start==============================================
              Metric<double[]> metric = new EuclideanDistance();
              RBFNetwork.Trainer<double[]> trainer = new RBFNetwork.Trainer<double[]>(metric);
              RBFNetwork<double[]> network = trainer.train(indsCharListsMultiTree, fitnessesForModel);

              for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
              {
                  Individual individual = state.population.subpops[sub].individuals[i];
                  if (individual.evaluated == false) {
                      double[] pcIntermediate = indsCharListsIntermediatePop[i];
                      //calculate the fitness based on surrogate model
                      double estimatedFitness = network.predict(pcIntermediate);
                      ((Clearable) individual.fitness).surrogateFitness(estimatedFitness);

                        if(estimatedFitness <= 0){
                            ((Clearable) individual.fitness).surrogateFitness(Double.MAX_VALUE);
                        }
                        else{
                            ((Clearable) individual.fitness).surrogateFitness(estimatedFitness);
                        }
                  }
              }
          } else {
              for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
              {
                  Individual individual = state.population.subpops[sub].individuals[i];
                  if (individual.evaluated == false) {
                      ((Clearable) individual.fitness).surrogateFitness(Double.MAX_VALUE - 1000); //just remove the duplicated ones
                  }
              }
          }
      }
  }*/

//2019.9.16
    //=======================================Gaussian Process Regression==============================================
/*    public void performCoevolutionaryEvaluation( final EvolutionState state,
                                                 final Population population,
                                                 final GroupedProblemForm prob,
                                                 ArrayList<double[][]> indsCharListsForModel) {

        double[][][] indsCharListsIntermediatePop = surrogateClearing.phenotypicPopulation(state, phenoCharacterisation); //3. calculate the phenotypic characteristic

        for (int sub = 0; sub < state.population.subpops.length; sub++) {
            //if there is training data, train the model. Otherwise, do not need to train model, just assign them the same fitness (Double.MAX_VALUE)
            if (indsCharListsForModel.get(sub).length != 0) {
                //===============================start==============================================
                MercerKernel<double[]> mercerKernel = new MercerKernel<double[]>() {
                    @Override
                    public double k(double[] x, double[] y) {
                        return 0;
                    }
                };
                GaussianProcessRegression.Trainer<double[]> trainer = new GaussianProcessRegression.Trainer<double[]>(mercerKernel,0.1);
                double[] fitnessForModel = ArrayUtils.toPrimitive(fitnessesForModel.get(sub));
                GaussianProcessRegression<double[]> network = trainer.train(indsCharListsForModel.get(sub), fitnessForModel);

                for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
                {
                    Individual individual = state.population.subpops[sub].individuals[i];
                    if (individual.evaluated == false) {
                        double[] pcIntermediate = indsCharListsIntermediatePop[sub][i];
                        //calculate the fitness based on surrogate model
                        double estimatedFitness = network.predict(pcIntermediate);
                        ((Clearable) individual.fitness).surrogateFitness(estimatedFitness);
                    }
                }
            } else {
                for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
                {
                    Individual individual = state.population.subpops[sub].individuals[i];
                    if(individual.evaluated == false){
                        ((Clearable) individual.fitness).surrogateFitness(Double.MAX_VALUE - 1000); //just remove the duplicated ones
                    }
                }
            }
        }
    }*/


    //LASSO
   /* public void evaluatePopulation(final EvolutionState state, double[][] indsCharListsMultiTree, double[] fitnessesForModel) {

        double[][] indsCharListsIntermediatePop = phenotypicForSurrogate.phenotypicPopulation(state, phenoCharacterisation, nonIntermediatePop); //3. calculate the phenotypic characteristic

        for (int sub = 0; sub < state.population.subpops.length; sub++) {
            //if there is training data, train the model. Otherwise, do not need to train model, just assign them the same fitness (Double.MAX_VALUE)
            if (indsCharListsMultiTree.length != 0) {
                //===============================start==============================================
                LASSO.Trainer trainer = new LASSO.Trainer(1);
                LASSO network = trainer.train(indsCharListsMultiTree, fitnessesForModel);

                for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
                {
                    Individual individual = state.population.subpops[sub].individuals[i];
                    if (individual.evaluated == false) {//large uncertainty
                        double[] pcIntermediate = indsCharListsIntermediatePop[i];
                        //calculate the fitness based on surrogate model
                        double estimatedFitness = network.predict(pcIntermediate);
                        ((Clearable) individual.fitness).surrogateFitness(estimatedFitness);
                        if (estimatedFitness <= 0) {
                            ((Clearable) individual.fitness).surrogateFitness(Double.MAX_VALUE);
                        } else {
                            ((Clearable) individual.fitness).surrogateFitness(estimatedFitness);
                        }
                    }
                }
            } else {
                for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
                {
                    Individual individual = state.population.subpops[sub].individuals[i];
                    if (individual.evaluated == false) {
                        ((Clearable) individual.fitness).surrogateFitness(Double.MAX_VALUE - 1000); //just make it different with the duplicated ones
                    }
                }
            }
        }
    }*/

    //NN
   /* public void performCoevolutionaryEvaluation( final EvolutionState state,
                                                 final Population population,
                                                 final GroupedProblemForm prob,
                                                 ArrayList<double[][]> indsCharListsForModel) {

        double[][][] indsCharListsIntermediatePop = surrogateClearing.phenotypicPopulation(state, phenoCharacterisation); //3. calculate the phenotypic characteristic

        for (int sub = 0; sub < state.population.subpops.length; sub++) {
            //if there is training data, train the model. Otherwise, do not need to train model, just assign them the same fitness (Double.MAX_VALUE)
            if (indsCharListsForModel.get(sub).length != 0) {
                //===============================start==============================================

                int numInput = indsCharListsForModel.get(sub)[0].length;
                NeuralNetwork.Trainer trainer = new NeuralNetwork.Trainer(numInput,200, 200, 200,1);//the number of input, ...the number of nodes in each layer..., the nunmber of output
                double[] fitnessForModel = ArrayUtils.toPrimitive(fitnessesForModel.get(sub));
                NeuralNetwork network = trainer.train(indsCharListsForModel.get(sub), fitnessForModel);

                for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
                {
                    Individual individual = state.population.subpops[sub].individuals[i];
                    if (individual.evaluated == false) {
                        double[] pcIntermediate = indsCharListsIntermediatePop[sub][i];
                        //calculate the fitness based on surrogate model
                        double estimatedFitness = network.predict(pcIntermediate);
                        ((Clearable) individual.fitness).surrogateFitness(estimatedFitness);
                    }
                }
            } else {
                for (int i = 0; i < state.population.subpops[sub].individuals.length; i++) //512
                {
                    Individual individual = state.population.subpops[sub].individuals[i];
                    if(individual.evaluated == false){
                        ((Clearable) individual.fitness).surrogateFitness(Double.MAX_VALUE - 1000); //just remove the duplicated ones
                    }
                }
            }
        }
    }*/

    public void setClear(boolean clear) {
        this.clear = clear;
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
