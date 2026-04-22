package mengxu.algorithm.diversepartnerselection.baselineTourWithBRandKNNSurrogate;

import ec.BreedingSource;
import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import mengxu.algorithm.lexicaseselection.ParentFairForCrossover;
import mengxu.cluster.CustomerPoint;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author yimei
 *
 */

public class GPRuleEvolutionStateBase extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();
	public List<List<CustomerPoint>> cluster = new ArrayList<>();
	public List<Double> clusterMeanFitness = new ArrayList<>();
	public List<Individual> ensemble = new ArrayList<>();
	public List<List<Individual>> ensembleAll = new ArrayList<>();

	public List<Integer> parentIndex = new ArrayList<>();

	//add 2021.12.23 store the estimated target fitness list
	public List<double[]> allTargetObjectiveList = new ArrayList<>();

	public static final String P_DPS_SELECTION = "DPS-selection";
	private boolean useDPSSelection;

	public static final String P_SATISFY_RATE = "satisfy-rate";
	private double satisfyRate;

	public static final String P_DYNAMIC_SATISFY_RATE = "dynamic-satisfy-rate";
	private boolean dynamicSatisfyRate;

	//add by mengxu 2022.01.17
	public double[][][] indsCharListsMultiTreeGen;
	public double[][][] fitnessesForModelGen;

	public List<ParentFairForCrossover> parentsIndexPairForCrossover = new ArrayList<>();
	public List<Double> XoverProbability = new ArrayList<>();
	public List<Double> combinationNumber = new ArrayList<>();

	public PhenoCharacterisation[] phenoCharacterisation;

	public double[] bests;
	public double[] worsts;

	public List<double[]> crossoverOffspringPCs = new ArrayList<>();
	public List<Individual> crossoverOffspring = new ArrayList<>();

	public static final String P_PRO_Using_MeanCaseFitness = "probability-for-using-meancasefitness";
	public double probabilityForUsingMeanCaseFitness = 1.1;


	@Override
	public void setup(EvolutionState state, Parameter base) {
		super.setup(this, base);
		useDPSSelection = state.parameters.getBoolean(new Parameter(P_DPS_SELECTION), false);
		satisfyRate = state.parameters.getDouble(new Parameter(P_SATISFY_RATE), null, 0);
		dynamicSatisfyRate = state.parameters.getBoolean(new Parameter(P_SATISFY_RATE), null, false);
		probabilityForUsingMeanCaseFitness = state.parameters.getDouble(new Parameter(P_PRO_Using_MeanCaseFitness), null, 0);
		this.phenoCharacterisation = new PhenoCharacterisation[2];
//		clusterSize = state.parameters.getInt(new Parameter(P_CLUSTER_SIZE), null);
//		clusterThreshold = state.parameters.getInt(new Parameter(P_CLUSTER_THRESHOLD), null);
//		clusterElementSize = state.parameters.getInt(new Parameter(P_CLUSTER_ELEMENT_SIZE), null);
//		clusterWithFitness = state.parameters.getBoolean(new Parameter(P_CLUSTER_WITH_FITNESS), false);
	}

	public boolean isUseDPSSelection() {
		return useDPSSelection;
	}

	public List<List<CustomerPoint>> getCluster() {
		return cluster;
	}

	public List<Double> getClusterMeanFitness() {
		return clusterMeanFitness;
	}

	public List<Individual> getEnsemble() {
		return ensemble;
	}

	@Override
	public int evolve() {
		if (generation > 0)
			output.message("Generation " + generation);

		//System.out.println("generation "+generation);

		//reset the sources prob 2021.12.23
		BreedingSource[] sources = this.population.subpops[0].species.pipe_prototype.sources;
		this.XoverProbability.add(sources[0].probability);
		sources[0].probability = 0.8;
		sources[1].probability = 0.95;
		sources[2].probability = 1.0;
		//-----------------------------------------


		// EVALUATION
		statistics.preEvaluationStatistics(this);

		evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population


		PopulationUtils.sort(this.population);//add by mengxu 2024.5.29

		//2021.12.10 get case number
		Individual[] individuals = this.population.subpops[0].individuals;

		//----------------------------------------------------------

		//add 2021.12.23 mengxu to estimate the target objective list
		Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);

//		multiCaseFitnessNormalisationForPopulation();
		//=================================================================

		//modified by mengxu. Measure diversity. 2021.04.15-------------------------
		GenotypeDiversity genoD = new GenotypeDiversity();
		double genoDvalue = (double)genoD.genotypeDiversity(individuals) / individuals.length;
//		System.out.println("Genotype diversity: " + genoDvalue);
		PhenotypeDiversity phenoD = new PhenotypeDiversity();
		double phenoDvalue = (double)phenoD.phenotypeDiversity(individuals) / individuals.length;
//		System.out.println("Phenotype diversity: " + phenoDvalue);
		EntropyDiversity entroD = new EntropyDiversity();
		double entroDvalue = entroD.entropyDiversity(individuals);
//		System.out.println("Entropy diversity: " + entroDvalue);
		PseudoIsomorphsDiversity pseIsoD = new PseudoIsomorphsDiversity();
		double pseIsoDvalue = (double)pseIsoD.pseudoIsomorphsDiversity(individuals) / individuals.length;
//		System.out.println("Pseudo isomorphs diversity: " + pseIsoDvalue);
		EditDistanceDiversityV1 edit1D = new EditDistanceDiversityV1();
		double edit1Dvalue = edit1D.editDistanceDiversityV1(individuals, bestIndi(0));//todo: need modified.
//		System.out.println("Edit 1 diversity: " + edit1Dvalue);
		EditDistanceDiversityV2 edit2D = new EditDistanceDiversityV2();
		double edit2Dvalue = edit2D.editDistanceDiversityV2(individuals, bestIndi(0));//todo: need modified.
//		System.out.println("Edit 2 diversity: " + edit2Dvalue);


//		//calculate the pc of each individual in each subpop and do cluster--------------
		this.phenoCharacterisation[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		this.phenoCharacterisation[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, this.phenoCharacterisation, true); //3. calculate the phenotypic characteristic
		//get the fitness for training model
		double[][][] multiCaseFitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length][];
		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
				multiCaseFitnessesForModel[subpop][ind] = ((MultiObjectiveFitness)this.population.subpops[subpop].individuals[ind].fitness).objectives.clone();
			}
		}
		this.indsCharListsMultiTreeGen = indsCharListsMultiTree;
		this.fitnessesForModelGen = multiCaseFitnessesForModel;
		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
		System.out.println("PC diversity: " + pcDvalue);

		ParentIndexDiversity parentIndexD = new ParentIndexDiversity();
		double parentIndexDvalue = (double)parentIndexD.parentIndexDiversity(parentIndex) / individuals.length;
//		System.out.println("Parent selection diversity: " + parentIndexDvalue);
		parentIndex.clear();

		//modified by mengxu. save diversities.
		ArrayList<Double> diversities = new ArrayList<>();
		diversities.add((double)generation);
		diversities.add(genoDvalue);
		diversities.add(phenoDvalue);
		diversities.add(entroDvalue);
		diversities.add(pseIsoDvalue);
		diversities.add(edit1Dvalue);
		diversities.add(edit2Dvalue);
		diversities.add(pcDvalue);
		diversities.add(parentIndexDvalue);
		storeGenDiversities.add(diversities);

		//modified by mengxu 2021.11.2 to find the index of bad run
		int notBadMaxIndex = this.population.subpops[0].individuals.length;
		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
			for(int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
				Fitness fitness = this.population.subpops[subpop].individuals[ind].fitness;
				if(fitness.fitness() >= Double.MAX_VALUE || fitness.fitness() >= Double.POSITIVE_INFINITY){
					notBadMaxIndex = ind;
					break;
				}
			}
		}

		//2021.10.14 to find the difference with Tournament selection======
		if(generation == 1 || generation == 25 || generation == 45){
			writeParentsAndChildToFile(individuals);
		}
		double[][] combinaiton = getCombination();
		ParentsCombinationForXcoverDiversity parentsX = new ParentsCombinationForXcoverDiversity();
		double parentXvalue = (double)parentsX.phenotypicCharacteristicDiversity(combinaiton);
		this.combinationNumber.add(parentXvalue);
		System.out.println("parents combination diversity: " + parentXvalue);
		parentsIndexPairForCrossover.clear();
		//=================================================================

		statistics.postEvaluationStatistics(this); //log the best individual
		//---------------------------------------------------------


		// SHOULD WE QUIT?
		if (evaluator.runComplete(this) && quitOnRunComplete)
		{
			output.message("Found Ideal Individual");
			return R_SUCCESS;
		}
		// SHOULD WE QUIT?
		if (generation == numGenerations-1)
		{
			generation++; // in this way, the last generation value will be printed properly.  fzhang 28.3.2018
			writeDiversityToFile();//modified by mengxu	2021.04.15
			writeCombinationNumberToFile();
			writeXoverProbabilityToFile();
//			writeEnsembleToFile();//modified by mengxu 2021.05.08
			return R_FAILURE;
		}

		// PRE-BREEDING EXCHANGING
		statistics.prePreBreedingExchangeStatistics(this);
		population = exchanger.preBreedingExchangePopulation(this);  /** Simply returns state.population. */
		statistics.postPreBreedingExchangeStatistics(this);

		String exchangerWantsToShutdown = exchanger.runComplete(this);  /** Always returns null */
		if (exchangerWantsToShutdown!=null)
		{
			output.message(exchangerWantsToShutdown);
			/*
			 * Don't really know what to return here.  The only place I could
			 * find where runComplete ever returns non-null is
			 * IslandExchange.  However, that can return non-null whether or
			 * not the ideal individual was found (for example, if there was
			 * a communication error with the server).
			 *
			 * Since the original version of this code didn't care, and the
			 * result was initialized to R_SUCCESS before the while loop, I'm
			 * just going to return R_SUCCESS here.
			 */

			return R_SUCCESS;
		}

		this.crossoverOffspringPCs.clear();
		this.crossoverOffspring.clear();

		// BREEDING
		statistics.preBreedingStatistics(this);

		population = breeder.breedPopulation(this); //!!!!!!   return newpop;  if it is NSGA-II, the population here is 2N

		// POST-BREEDING EXCHANGING
		statistics.postBreedingStatistics(this);   //position 1  here, a new pop has been generated.

		// POST-BREEDING EXCHANGING
		statistics.prePostBreedingExchangeStatistics(this);
		population = exchanger.postBreedingExchangePopulation(this);   /** Simply returns state.population. */
		statistics.postPostBreedingExchangeStatistics(this);  //position 2

		// Generate new instances if needed
		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
		if (problem.getEvaluationModel().isRotatable()) {
			problem.rotateEvaluationModel();
		}

		// INCREMENT GENERATION AND CHECKPOINT
		generation++;
		if (checkpoint && generation%checkpointModulo == 0)
		{
			output.message("Checkpointing");
			statistics.preCheckpointStatistics(this);
			Checkpoint.setCheckpoint(this);
			statistics.postCheckpointStatistics(this);
		}

		return R_NOTDONE;
	}

	public List<CustomerPoint> buildCustomerPoint(double[][] subpopIndsCharListsMultiTree, double[] subpopFitnessesForModel, boolean useFitness){
		List<CustomerPoint> csAll = new ArrayList<>();
		for(int indIndex=0; indIndex<subpopIndsCharListsMultiTree.length; indIndex++){
			if(useFitness){
				// 合并两个数组
				double maxDecision = Arrays.stream(subpopIndsCharListsMultiTree[indIndex]).max().getAsDouble();
//				System.out.println("maxDecision: " + maxDecision);
//				double addFitness = subpopFitnessesForModel[indIndex] % maxDecision;
//				System.out.println("Fitness: " + subpopFitnessesForModel[indIndex] + "; addFitness: " + addFitness);
//				System.out.println("subpopIndsCharListsMultiTree[indIndex].length: " + subpopIndsCharListsMultiTree[indIndex].length);
				double[] subpopIndsCharListsMultiTreeWithFitness = new double[subpopIndsCharListsMultiTree[indIndex].length + 1];
				System.arraycopy(subpopIndsCharListsMultiTree[indIndex], 0, subpopIndsCharListsMultiTreeWithFitness, 0, subpopIndsCharListsMultiTree[indIndex].length);
				subpopIndsCharListsMultiTreeWithFitness[subpopIndsCharListsMultiTreeWithFitness.length-1] = subpopFitnessesForModel[indIndex]/10;
				CustomerPoint cp = new CustomerPoint(indIndex,subpopIndsCharListsMultiTreeWithFitness, subpopFitnessesForModel[indIndex]);
				csAll.add(cp);
			}
			else{
				CustomerPoint cp = new CustomerPoint(indIndex,subpopIndsCharListsMultiTree[indIndex], subpopFitnessesForModel[indIndex]);
				csAll.add(cp);
			}
		}
		return csAll;
	}

	//modified by meng xu 2021.05.08
	public List<Individual> getAllDiverseIndividualFromCluster(List<List<CustomerPoint>> customerPointCluster){
		List<Individual> allDiverseIndividual = new ArrayList<>();
		for(int i =0;i<customerPointCluster.size();i++) {
			double minFitness = customerPointCluster.get(i).get(0).getFitness();;
			int index = 0;
			if(customerPointCluster.get(i).size()>1){
				for(int j=1;j<customerPointCluster.get(i).size();j++) {
					double fitness = customerPointCluster.get(i).get(j).getFitness();
					if(fitness < minFitness){
						minFitness = fitness;
						index = j;
					}
				}
			}
			int indIndex = customerPointCluster.get(i).get(index).getIndIndex();
			//this is for only one subpop
			allDiverseIndividual.add(this.population.subpops[0].individuals[indIndex]);
		}
		return allDiverseIndividual;
	}

	//modified by mengxu 2021.05.08
	public List<Individual> getEnsemble(List<Individual> allDiverseIndividual, int maxEnsembleSize){
		if(allDiverseIndividual.size()<maxEnsembleSize){
			return allDiverseIndividual;
		}
		Individual[] sortedIndividuals = PopulationUtils.sortInds(allDiverseIndividual);
		List<Individual> ensemble = new ArrayList<>(Arrays.asList(sortedIndividuals).subList(0, maxEnsembleSize));
		return ensemble;
	}

	public void writeEnsembleToFile(){
		File ensembleFile = new File("job." + jobSeed + ".ensemble.stat");
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(ensembleFile));
			for(int i=0; i<ensembleAll.size(); i++){
				writer.write("Generation: " + i);
				writer.newLine();
				writer.write("Ensemble size: \n" + ensembleAll.get(i).size());
				writer.newLine();
				for(int j=0; j<ensembleAll.get(i).size(); j++){
					writer.write("Member: " + j);
					writer.newLine();
					writer.write("Fitness: [" + ensembleAll.get(i).get(j).fitness.fitness() + "]");
					writer.newLine();
					writer.write(((GPIndividual)ensembleAll.get(i).get(j)).toStringEnsemble(state));
//					writer.newLine();
				}
				writer.newLine();
			}

			writer.write("End");
			writer.newLine();

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.2.15 modified by mengxu
	public void writeDiversityToFile(){
		File diversities = new File("job." + jobSeed + ".diversities.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Gen, Geno, Pheno, Entropy, PseIso, Edit 1, Edit 2, PC, ParentSelection");
			writer.newLine();
			for (int ind = 0; ind < storeGenDiversities.size(); ind++) {
				ArrayList<Double> ref = storeGenDiversities.get(ind);
				writer.write(ref.get(0) + "," + ref.get(1) + "," + ref.get(2)
						+ "," + ref.get(3) + "," + ref.get(4) + "," + ref.get(5)
						+ "," + ref.get(6) + "," + ref.get(7) + "," + ref.get(8));
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.2.15 modified by mengxu
	public void writeClusterToFile(){
		File diversities = new File("job." + jobSeed + "." + generation +".clusters.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Ind, Cluster, Fitness");
			writer.newLine();
			for (int ind = 0; ind < cluster.size(); ind++) {
				List<CustomerPoint> ref = cluster.get(ind);
				for(int i=0; i<ref.size(); i++){
					CustomerPoint individual = ref.get(i);
					if(individual.getFitness() >= Double.MAX_VALUE || individual.getFitness() >= Double.POSITIVE_INFINITY){
						continue;
					}
					else{
						writer.write(individual.getIndIndex() + "," + individual.getClusterid() + "," + individual.getFitness());
						writer.newLine();
					}
				}
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void multiCaseFitnessNormalisationForPopulation(){
		Individual[] individuals = this.population.subpops[0].individuals;

		OneInstanceMultiCaseMultiObjectiveFitness fitness0 = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[0].fitness;
		double[][] objectives0 = fitness0.multiInstanceMultiObjectiveFitness;
		int length0 = objectives0.length;
		this.bests = new double[length0];
		this.worsts = new double[length0];
//		System.out.print("Targets: [");
		for(int i=0; i<length0; i++){
			int indexBest = PopulationUtils.getIndexOfbestIndsInstance(individuals,i);
			double bestFit = ((OneInstanceMultiCaseMultiObjectiveFitness)individuals[indexBest].fitness).getFitness(i,0);
			int indexWorst = PopulationUtils.getIndexOfWorstIndsInstance(individuals,i);
			double worstFit = ((OneInstanceMultiCaseMultiObjectiveFitness)individuals[indexWorst].fitness).getFitness(i,0);

			this.bests[i] = bestFit;
			this.worsts[i] = worstFit;
		}
//		System.out.println("]");

		//for this method, the binary bring in fitness is used to store the nomatilation results
		for(int i=0; i<individuals.length; i++){
			OneInstanceMultiCaseMultiObjectiveFitness fitness = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[i].fitness;
			double[][] objectives = fitness.multiInstanceMultiObjectiveFitness;
			int length = objectives.length;
			double[][] normalisation = new double[length][1];
			for(int j=0; j<length; j++){
				if(objectives[j][0] >= Double.MAX_VALUE || objectives[j][0] >= Double.POSITIVE_INFINITY){
					normalisation[j][0] = 1.1;
				}
				else{
					normalisation[j][0] = (objectives[j][0] - this.bests[j])/(this.worsts[j] - this.bests[j]);
				}
			}
			fitness.setMultiInstanceMultiObjectiveNormalisation(normalisation);
		}
	}

	public void updateBinaryBringForPopulation(){
		Individual[] individuals = this.population.subpops[0].individuals;

		//add 2021.12.27
		if(dynamicSatisfyRate && generation != 0){
			satisfyRate = satisfyRate * 0.9;
			System.out.println("dynamic satisfy rate: " + satisfyRate);
		}

		OneInstanceMultiCaseMultiObjectiveFitness fitness0 = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[0].fitness;
		double[][] objectives0 = fitness0.multiInstanceMultiObjectiveFitness;
		int length0 = objectives0.length;
//		int[] bestIndexs = new int[length0];
//		double[] bestFitnesses = new double[length0];
		double[] targets = new double[length0];
//		System.out.print("Targets: [");
		for(int i=0; i<length0; i++){
			int indexBest = PopulationUtils.getIndexOfbestIndsInstance(individuals,i);
			double bestFit = ((OneInstanceMultiCaseMultiObjectiveFitness)individuals[indexBest].fitness).getFitness(i,0);
//			bestFitnesses[i] = bestFit;
			targets[i] = bestFit * (1 + satisfyRate);//todo: double check should be  1+satisfyRate or 1-satisfyRate, 2024.3.27
//			System.out.print(targets[i] + ", ");
		}
//		System.out.println("]");


		for(int i=0; i<individuals.length; i++){
			OneInstanceMultiCaseMultiObjectiveFitness fitness = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[i].fitness;
			double[][] objectives = fitness.multiInstanceMultiObjectiveFitness;
			int length = objectives.length;
			double[][] binaryBring = new double[length][1];
			for(int j=0; j<length; j++){
				if(objectives[j][0] < targets[j]){
					binaryBring[j][0] = 1;
				}
				else{
					binaryBring[j][0] = 0;
				}
			}
			fitness.setMultiInstanceMultiObjectiveBinaryBring(binaryBring);
		}
	}

	//2021.12.28 modified by mengxu
	public void writeParentsAndChildToFile(Individual[] individuals){
		File diversities = new File("job." + jobSeed + "." + generation + ".parentschild.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Parent1ID, Parent1Fitness, Parent2ID, Parent2Fitness, Child1Ind, Child1Fitness, Child2Ind, Child2Fitness");
			writer.newLine();
			for(ParentFairForCrossover ref: parentsIndexPairForCrossover){
				ref.setChild1Fitness(individuals[ref.getChild1ID()].fitness.fitness());
				if(ref.getChild2ID() != -1){
					ref.setChild2Fitness(individuals[ref.getChild2ID()].fitness.fitness());
				}
				writer.write( ref.getParent1ID()+ "," + ref.getParent1Fitness()+ "," + ref.getParent2ID()+ "," + ref.getParent2Fitness() + "," + ref.getChild1ID() + "," + ref.getChild1Fitness() + "," + ref.getChild2ID() + "," + ref.getChild2Fitness());
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.12.28 modified by mengxu
	public void writeXoverProbabilityToFile(){
		File diversities = new File("job." + jobSeed + ".xoverprobability.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Gen, XoverPro");
			writer.newLine();
			for(int i=0; i<this.XoverProbability.size(); i++){
				writer.write( i+ "," + this.XoverProbability.get(i));
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.12.28 modified by mengxu
	public void writeCombinationNumberToFile(){
		File diversities = new File("job." + jobSeed + ".combinationnumber.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Gen, combination");
			writer.newLine();
			for(int i=0; i<this.combinationNumber.size(); i++){
				writer.write( i+ "," + this.combinationNumber.get(i));
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public double[][] getCombination(){
		int len = this.parentsIndexPairForCrossover.size();
		double[][] combination = new double[len][2];
		for(int i=0; i<len; i++){
			ParentFairForCrossover ref = this.parentsIndexPairForCrossover.get(i);
			combination[i][0] = ref.getParent1ID();
			combination[i][1] = ref.getParent2ID();
		}
		return combination;
	}

//	public void updateBinaryBringForPopulation(){
//		Individual[] individuals = this.population.subpops[0].individuals;
////		double[] target = this.allTargetObjectiveList.get(generation);
//
////		//add 2021.12.27
////		if(dynamicSatisfyRate){
////			int bestIndex = PopulationUtils.getIndexOfbestInds(this.population, 0);
////			double bestFitness = individuals[bestIndex].fitness.fitness();
////			satisfyRate = bestFitness/target[0] - 1 + (1 - (double)generation/(double)numGenerations)/10;
////			System.out.println("dynamic satisfy rate: " + satisfyRate);
////		}
//
//		int bestIndex = PopulationUtils.getIndexOfbestInds(this.population, 0);
//		double bestFitness = individuals[bestIndex].fitness.fitness();
//
//		//add 2021.12.27
//		if(dynamicSatisfyRate && generation != 0){
//			satisfyRate = satisfyRate * 0.9;
//			System.out.println("dynamic satisfy rate: " + satisfyRate);
//		}
//
//		double target = bestFitness * (1 + satisfyRate);
//		System.out.println("Target: " + target);
//
//		for(int i=0; i<individuals.length; i++){
//			MultiInstanceMultiObjectiveFitness fitness = (MultiInstanceMultiObjectiveFitness)individuals[i].fitness;
//			double[][] objectives = fitness.multiInstanceMultiObjectiveFitness;
//			int length = objectives.length;
//			double[][] binaryBring = new double[length][1];
//			for(int j=0; j<length; j++){
//				if(objectives[j][0] < target * (1 + satisfyRate)){
//					binaryBring[j][0] = 1;
//				}
//				else{
//					binaryBring[j][0] = 0;
//				}
//			}
//			fitness.setMultiInstanceMultiObjectiveBinaryBring(binaryBring);
//		}
//	}

}
