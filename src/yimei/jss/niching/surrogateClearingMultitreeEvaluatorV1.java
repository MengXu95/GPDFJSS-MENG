package yimei.jss.niching;

import ec.EvolutionState;
import ec.Individual;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;
import org.apache.commons.lang3.ArrayUtils;
import yimei.jss.helper.PopulationUtils;

import java.util.ArrayList;


/**
 * Created by fzhang on 2019.9.24.
 */
//a class can not extend from more than one class
//2020.2.15 put the individuals in the intermediate together, and then mapping to different tasks
public class surrogateClearingMultitreeEvaluatorV1 extends SimpleEvaluator {
    public static final String P_RADIUS = "radius";
    public static final String P_CAPACITY = "capacity";

    //fzhang 2018.10.9 to get the pre-generation value
    public static final String P_PRE_GENERATIONS = "pre-generations";
    //ArrayList<Double[]> fitnessesForModel = new ArrayList<>();
    double[][] tempfitnessesForModel; //for transfer the fitness into the surrogate model to estimate the fitness
    double[][][] tempindsCharListsMultiTree;

    protected boolean clear = true;
    protected boolean nonIntermediatePop = true;

    protected double radius;
    protected int capacity;

    protected PhenoCharacterisation[] phenoCharacterisation;

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

        //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
        if (filePath == null) {
            //dynamic simulation
            phenoCharacterisation[0] =
                    SequencingPhenoCharacterisation.defaultPhenoCharacterisation();
            phenoCharacterisation[1] =
                    RoutingPhenoCharacterisation.defaultPhenoCharacterisation();
        } else {
            //static simulation
            phenoCharacterisation[0] =
                    SequencingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
            phenoCharacterisation[1] =
                    RoutingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
        }
    }

    @Override
    public void evaluatePopulation(final EvolutionState state) {

        //if it is a normal evaluation
        if (nonIntermediatePop) {
            super.evaluatePopulation(state);
            double[][][] indsCharListsMultiTree = phenotypicForSurrogate.phenotypicPopulation(state, phenoCharacterisation, nonIntermediatePop); //3. calculate the phenotypic characteristic
            //get the fitness for training model
            double[][] fitnessesForModel = new double[state.population.subpops.length][state.population.subpops[0].individuals.length];

            for(int subpop = 0; subpop < state.population.subpops.length; subpop++){
                for(int ind = 0; ind < state.population.subpops[subpop].individuals.length; ind++){
                    fitnessesForModel[subpop][ind] = state.population.subpops[subpop].individuals[ind].fitness.fitness();
                }

                // int removeIdx = 0;
                double[][] indsCharListsMultiTreeSubpop = indsCharListsMultiTree[subpop];
                double[] fitnessesForModelSubpop = fitnessesForModel[subpop];

                for (int i = 0; i < fitnessesForModelSubpop.length; i++) {
                    if (fitnessesForModelSubpop[i] == Double.MAX_VALUE) {
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
    public void evaluatePopulation(final EvolutionState state, double[][][] indsCharListsMultiTree, double[][] fitnessesForModel) {

        //double[][][] indsCharListsIntermediatePop = phenotypicForSurrogate.phenotypicPopulation(state, phenoCharacterisation, nonIntermediatePop); //3. calculate the phenotypic characteristic
        double[][][] indsCharListsIntermediatePop =  multitreeClearingFirst.phenotypicOfIntermedidatePop;//when clear the population, it is already be calculated.
        //2020.2.10 put the offspring of all the subpopulations together as a pool, and evaluate them based on surrogate model 1, and choose the top |P| to subpopulation 1
        //then, evaluate all the individuals based on surrogate model 2, and choose the top |P| to subpopulation 2
        //then, evaluate all the individuals based on surrogate model 3, and choose the top |P| to subpopulation 3 ... and so on

        //step1: put all the offspring and their pc together
        ArrayList<Individual> individuals = new ArrayList<>();//all the individuals in the population
        ArrayList<double[]> indsCharListsIntermediatePopAllPop = new ArrayList<>();//the all pc of individuals in the population
        for (int sub = 0; sub < state.population.subpops.length; sub++) {
            for (int ind = 0; ind < state.population.subpops[sub].individuals.length; ind++){
                individuals.add(state.population.subpops[sub].individuals[ind]);
                indsCharListsIntermediatePopAllPop.add(indsCharListsIntermediatePop[sub][ind]);
            }
        }

        //step2: evaluate all individuals based on the first KNN
        for (int sub = 0; sub < state.population.subpops.length; sub++) {
            for (int i = 0; i < individuals.size(); i++) //
            {
                Individual individual = individuals.get(i); //the examined individual
                //if(indsCharListsMultiTree.length != 0){
                    //KNN
                    //===============================start==============================================
                    double dMin  = Double.MAX_VALUE;
                    int index = 0;
                    if(individual.evaluated == false){
                        double[] pcIntermediate = indsCharListsIntermediatePopAllPop.get(i); //the pc of the examined individual
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
                        ((Clearable)individual.fitness).surrogateFitness(fitnessesForModel[sub][index]);
                        //individual.evaluated = true;
                        //individual.fitness.trials = new ArrayList();
                        //individual.fitness.trials.add(individual.fitness.fitness());
                    }
                }
/*                else{
                    ((Clearable)individual.fitness).surrogateFitness(Double.MAX_VALUE);
                }*/

    //step3: choose the top |P| individuals as the offspring of the current subpop
            //sort the individuals based on the fitnesses
            Individual[] inds = PopulationUtils.sortInds(individuals);
            System.arraycopy(inds, 0, state.population.subpops[sub].individuals, 0, state.population.subpops[sub].individuals.length);
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
}
