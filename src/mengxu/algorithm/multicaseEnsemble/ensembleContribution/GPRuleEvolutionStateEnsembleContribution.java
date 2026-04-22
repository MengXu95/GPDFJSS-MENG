package mengxu.algorithm.multicaseEnsemble.ensembleContribution;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import mengxu.algorithm.lexicaseselection.ParentFairForCrossover;
import mengxu.algorithm.lexicaseselection.ParentForMutation;
import org.apache.commons.math3.stat.descriptive.AggregateSummaryStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
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
import java.util.*;

import org.jfree.data.statistics.Statistics;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author yimei
 *
 */

public class GPRuleEvolutionStateEnsembleContribution extends GPRuleEvolutionState {

	private List<double[][]> normalisationFitness = new ArrayList<>();

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();
	public List<Integer> parentIndex = new ArrayList<>();
	public List<Double> parentFitness = new ArrayList<>(); //add 2021.12.09
	public List<List<Integer>> allGenerationParentIndex = new ArrayList<>();
	public List<List<Double>> allGenerationParentFitness = new ArrayList<>();//add 2021.12.09

	public List<double[]> allGenerationCaseNumber = new ArrayList<>();

	//2021.10.14 to find the difference with Tournament selection
	public List<ParentFairForCrossover> parentsIndexPairForCrossover = new ArrayList<>();
	public List<ParentForMutation> parentIndexForMutation = new ArrayList<>();
	public List<Double> combinationNumber = new ArrayList<>(); //this is used for store the combination pairs of crossover

	public static final String P_GEN_USE_LEXICASE_SELECTION= "gen-use-lexicase-selection";
	public int genForUseLS;//original is 5, I use -1 to as a comparison method.

	public static final String P_STATISTICAL_TEST_BETWEEN_ENSEMBLES_INDIVIDUALS= "statistical-test-between-ensembles-individuals";
	public boolean statisticalTestBetweenEnsemblesIndividuals;//original is 5, I use -1 to as a comparison method.

	public static final String P_CROSSOVER_USE_ENSEMBLE_CONTRIBUTION= "crossover-use-ensemble-contribution";
	public boolean crossoverUseEnsembleContribution;

	public static final String P_MUTATION_USE_ENSEMBLE_CONTRIBUTION= "mutation-use-ensemble-contribution";
	public boolean mutationUseEnsembleContribution;
	public List<Integer> allGenWilcoxonRankSumTest = new ArrayList<>();

	public static final String P_ENSEMBLE_ROULETTE_SELECTION = "ensemble-roulette-selection";
	public boolean ensembleRouletteSelection;

	public static final String P_LIMIT_TOTAL_EVALUATION_TIME = "limit-total-evaluation-times";
	public int limitTotalEvaluationTimes;

//	public List<Individual> ensemble = new ArrayList<>();
//	public List<List<Individual>> ensembleAll = new ArrayList<>();


	@Override
	public void setup(EvolutionState state, Parameter base) {

		super.setup(this, base);
		ensembleRouletteSelection = state.parameters.getBoolean(new Parameter(P_ENSEMBLE_ROULETTE_SELECTION), null, false);
		this.genForUseLS = state.parameters.getInt(new Parameter(P_GEN_USE_LEXICASE_SELECTION), null, -1);
		this.statisticalTestBetweenEnsemblesIndividuals = state.parameters.getBoolean(new Parameter(P_STATISTICAL_TEST_BETWEEN_ENSEMBLES_INDIVIDUALS), null, false);
		this.crossoverUseEnsembleContribution = state.parameters.getBoolean(new Parameter(P_CROSSOVER_USE_ENSEMBLE_CONTRIBUTION), null, false);
		this.mutationUseEnsembleContribution = state.parameters.getBoolean(new Parameter(P_MUTATION_USE_ENSEMBLE_CONTRIBUTION), null, false);

		this.limitTotalEvaluationTimes = state.parameters.getInt(new Parameter(P_LIMIT_TOTAL_EVALUATION_TIME), null, -1);
	}

    @Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

	    //System.out.println("generation "+generation);
		Individual[] individuals = this.population.subpops[0].individuals;
		if(generation != 0){
			List<Integer> parentIndexCopy = new ArrayList<>(parentIndex);
			allGenerationParentIndex.add(parentIndexCopy);
			List<Double> parentFitnessCopy = new ArrayList<>(parentFitness);
			allGenerationParentFitness.add(parentFitnessCopy);
		}
		ParentIndexDiversity parentIndexD = new ParentIndexDiversity();
		double parentIndexDvalue = (double)parentIndexD.parentIndexDiversity(parentIndex) / individuals.length;
//		System.out.println("Unique parentIndex: " + parentIndexDvalue * individuals.length);
		parentIndex.clear();
		parentFitness.clear();


	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

	    evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
	    statistics.postEvaluationStatistics(this);

		//added by mengxu 2023.03.27
		if(this.statisticalTestBetweenEnsemblesIndividuals){
			if(this.generation == 1 || this.generation == 25 || this.generation == 45 || this.generation == 50){
				int ref = DoStatisticalTestBetweenEnsemblesIndividuals(true);
				allGenWilcoxonRankSumTest.add(ref);
			}
			else{
				int ref = DoStatisticalTestBetweenEnsemblesIndividuals(false);
				allGenWilcoxonRankSumTest.add(ref);
			}
		}



		//add 2021.12.14=====================================
		individuals = this.population.subpops[0].individuals;
//		PopulationUtils.sort(individuals);
		//===================================================


		//----------------------------------------------------------

		//2022.04.07 mengxu
		//----------------------------------------------------------

		//2021.10.14 to find the difference with Tournament selection======
//		System.out.println("Crossover:");
//		for(ParentFairForCrossover ref: parentsIndexPairForCrossover){
//			ref.setChildFitness(individuals[ref.getChildID()].fitness.fitness());
//			ref.print();
//		}
//		System.out.println("Mutation:");
//		for(ParentForMutation ref: parentIndexForMutation){
//			ref.setChildFitness(individuals[ref.getChildID()].fitness.fitness());
//			ref.print();
//		}
//		parentsIndexPairForCrossover.clear();
//		parentIndexForMutation.clear();
		//================================================


		//==========for check difference of multi-case 2021.09.22================
//		Individual[] individuals = this.population.subpops[0].individuals;
//		PopulationUtils.sort(individuals);
////	    int indexBest = PopulationUtils.getIndexOfbestInds(this.population, 0);
////	    Individual indBest = this.population.subpops[0].individuals[indexBest];
//		for(int indIndex=0; indIndex<5; indIndex++){
//			Individual indForLS = this.population.subpops[0].individuals[indIndex];
//			double[][] multiCaseFitness = ((MultiInstanceMultiObjectiveFitness)indForLS.fitness).multiInstanceMultiObjectiveFitness;
//			System.out.print("Ind " + indIndex + " Fitness of multi-case: [");
//			for(int i=0; i<multiCaseFitness.length; i++){
//				if(i != multiCaseFitness.length-1){
//					System.out.print(multiCaseFitness[i][0] + ", ");
//				}
//				else{
//					System.out.println(multiCaseFitness[i][0] + "]");
//				}
//			}
//		}
		//=======================================================================

		//modified by mengxu. Measure diversity. 2021.04.15
//		Individual[] individuals = this.population.subpops[0].individuals;
		GenotypeDiversity genoD = new GenotypeDiversity();
		double genoDvalue = (double)genoD.genotypeDiversity(individuals) / individuals.length;
//		System.out.println("Genotype diversity: " + genoDvalue);
		PhenotypeDiversity phenoD = new PhenotypeDiversity();
		double phenoDvalue = (double)phenoD.phenotypeDiversity(individuals) / individuals.length;
//		System.out.println("Phenotype diversity: " + phenoDvalue);
//		EntropyDiversity entroD = new EntropyDiversity();
//		double entroDvalue = entroD.entropyDiversity(individuals);
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


		Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);

		//calculate the pc of each individual in each subpop and do cluster--------------
		PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
		//dynamic simulation
		phenoCharacterisation[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		phenoCharacterisation[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, false); //3. calculate the phenotypic characteristic
		//get the fitness for training model
		double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];
		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
				fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
			}
		}
		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
		System.out.println("PC diversity: " + pcDvalue);

		EntropyDiversityBasedOnPC entroD = new EntropyDiversityBasedOnPC();
		double entroDvalue = entroD.entropyDiversityBasedOnPC(individuals,indsCharListsMultiTree[0]);
		System.out.println("Entropy diversity: " + entroDvalue);

//		double entroDvalueo = PopulationUtils.entropy(indsCharListsMultiTree);
//		System.out.println("Entropy diversity old: " + entroDvalue);

		//-------------------------------------------------------------------
		//modified by mengxu 2021.07.09
//		//calculate the pc of each individual from current simulation in each subpop and do cluster--------------
//		double[][][] indsCharListsMultiTree = new double[this.population.subpops.length][][];
//		double[][] fitnessesForModel = new double[this.population.subpops.length][];
//		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
//			indsCharListsMultiTree[subpop] = new double[this.population.subpops[subpop].individuals.length][];
//			fitnessesForModel[subpop] = new double[this.population.subpops[subpop].individuals.length];
//			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
////				Double[] decisionVector = ((GPIndividual)this.population.subpops[subpop].individuals[ind]).decisionVector;
////				indsCharListsMultiTree[subpop][ind] = new double[decisionVector.size()];
////				for(int dec=0;dec<decisionVector.size();dec++){
////					indsCharListsMultiTree[subpop][ind][dec] = decisionVector.get(dec);
////				}
//				Double[] decisionVector = ((GPIndividual)this.population.subpops[subpop].individuals[ind]).decisionVector;
//				indsCharListsMultiTree[subpop][ind] = new double[decisionVector.length];
//				for(int dec=0;dec<decisionVector.length;dec++){
//					indsCharListsMultiTree[subpop][ind][dec] = decisionVector[dec];
//				}
//				fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
//			}
//		}
//		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
//		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]);
//		System.out.println("PC diversity: " + pcDvalue + " PC diversity percent: " + pcDvalue/ individuals.length);




		//modified by mengxu. save diversities.
		ArrayList<Double> diversities = new ArrayList<>();
		diversities.add((double)generation);
		diversities.add(genoDvalue);
		diversities.add(phenoDvalue);
		diversities.add(entroDvalue);
		diversities.add(pseIsoDvalue);
		diversities.add(edit1Dvalue);
		diversities.add(edit2Dvalue);
//		diversities.add(0.0);
		diversities.add(pcDvalue);
		diversities.add(parentIndexDvalue);
		storeGenDiversities.add(diversities);

		//add 2021.09.23
//		populationNormalisation();
//	    int indexBest = PopulationUtils.getIndexOfbestInds(this.population, 0);
//		Individual indBest = this.population.subpops[0].individuals[indexBest];
//
//		int time = 0;
//		for(int indIndex=0; indIndex<50; indIndex++){
//			Individual ind = this.population.subpops[0].individuals[indIndex];
//			double[][] multiCaseFitness = ((MultiInstanceMultiObjectiveFitness)ind.fitness).multiInstanceMultiObjectiveFitness;
//			if(multiCaseFitness[0][0] >= Double.POSITIVE_INFINITY || multiCaseFitness[0][0] >= Double.MAX_VALUE) {
//					continue;
//			}
////			double[][] multiCaseFitness = this.normalisationFitness.get(indIndex);
//			System.out.print("Ind " + indIndex + " Fitness of multi-case: [");
//			for(int i=0; i<multiCaseFitness.length; i++){
//				if(i != multiCaseFitness.length-1){
//					System.out.print(multiCaseFitness[i][0] + ", ");
//				}
//				else{
//					System.out.println(multiCaseFitness[i][0] + "]");
//				}
//			}
//			time ++;
//			if(time > 10){
//				break;
//			}
//		}

//	    for(int indIndex=0; indIndex<15; indIndex++){
//			double[][] multiCaseFitness = this.normalisationFitness.get(indIndex);
//			System.out.print("Ind " + indIndex + " Fitness of multi-case: [");
//			for(int i=0; i<multiCaseFitness.length; i++){
//				if(i != multiCaseFitness.length-1){
//					System.out.print(multiCaseFitness[i][0] + ", ");
//				}
//				else{
//					System.out.println(multiCaseFitness[i][0] + "]");
//				}
//			}
//		}



//		int badRun = 0;
//
//		for(int i=0; i < population.subpops[0].individuals.length; i++){
//			if(population.subpops[0].individuals[i].fitness.fitness() >= Double.POSITIVE_INFINITY ||
//					population.subpops[0].individuals[i].fitness.fitness() >= Double.MAX_VALUE){
//				badRun ++;
//			}
//		}
//		System.out.println("Generation " + generation + " bad run: " + badRun);

		updateIndividualRankForCases();
//		ensemble = calculateEnsembleForGen();
//		ensembleAll.add(calculateEnsembleForGen());

		if(generation == 1 || generation == 25 || generation == 45){
			writeParentsAndChildToFile(individuals);
			writeMultiCaseFitnessOfTop400IndividualToFile(individuals);
		}

		double[][] combinaiton = getCombination();
		ParentsCombinationForXcoverDiversity parentsX = new ParentsCombinationForXcoverDiversity();
		double parentXvalue = (double)parentsX.phenotypicCharacteristicDiversity(combinaiton);
		this.combinationNumber.add(parentXvalue);
		System.out.println("parents combination diversity: " + parentXvalue);
		parentsIndexPairForCrossover.clear();
		parentIndexForMutation.clear();

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
			writeSelectParentIndexAndFitnessToFile();
			writeCombinationNumberToFile();
			writeAllGenWilcoxonRankSumTestToFile();
//			writeCaseNumberToFile(); //hide by mengxu 2023.02.24

			return R_FAILURE;
	        }

		// SHOULD WE QUIT?
		if (totalEvaluationTime >= this.limitTotalEvaluationTimes)
		{
			System.out.println("totalEvaluationTime: " + totalEvaluationTime);
			System.out.println("limitTotalEvaluationTimes: " + limitTotalEvaluationTimes);
			generation++;
			writeDiversityToFile();//modified by mengxu	2021.04.15
			writeSelectParentIndexAndFitnessToFile();
			writeCombinationNumberToFile();
			writeAllGenWilcoxonRankSumTestToFile();
//			writeCaseNumberToFile(); //hide by mengxu 2023.02.24

			return R_FAILURE;
		}

		//2019.12.6 change the stop criteria to time
		if (totalTime > expectTrainingTime)
		{
			System.out.println("totalTime: " + totalTime);
			System.out.println("expectTrainingTime: " + expectTrainingTime);
			generation++; // in this way, the last generation value will be printed properly.  fzhang 28.3.2018
			writeDiversityToFile();//modified by mengxu	2021.04.15
			writeSelectParentIndexAndFitnessToFile();
			writeCombinationNumberToFile();
			writeAllGenWilcoxonRankSumTestToFile();
//			writeCaseNumberToFile(); //hide by mengxu 2023.02.24
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

	public void calculateNoveltyScore(Individual[] individuals, int numCase){
		for(int i=0; i< individuals.length; i++) {
			Individual ind = individuals[i];
			OneInstanceMultiCaseMultiObjectiveFitness indFitness = (OneInstanceMultiCaseMultiObjectiveFitness) ind.fitness;
			double[][] newNoveltyScore = new double[numCase][1];
			indFitness.setMultiInstanceNoveltyScore(this, newNoveltyScore);
		}

		for(int m = 0; m<numCase; m++){
		Map<Double, Integer> map = new HashMap<>();

			for(int i=0; i< individuals.length; i++){
				Individual ind = individuals[i];
				double indFitness = ((OneInstanceMultiCaseMultiObjectiveFitness)ind.fitness).multiInstanceMultiObjectiveFitness[m][0];

				int flag = 0;
				for(int j=0; j<i; j++){
					Individual indInpop = individuals[j];
					double indInpopFitness = ((OneInstanceMultiCaseMultiObjectiveFitness)indInpop.fitness).multiInstanceMultiObjectiveFitness[m][0];
					if(indFitness >= indInpopFitness && indFitness <= indInpopFitness){
						flag = 1;
						break;
					}
				}
				if(flag == 0){
					int count = 1;
					for(int j=i+1; j<individuals.length; j++){
						Individual indInpop = individuals[j];
						double indInpopFitness = ((OneInstanceMultiCaseMultiObjectiveFitness)indInpop.fitness).multiInstanceMultiObjectiveFitness[m][0];
						if(indFitness >= indInpopFitness && indFitness <= indInpopFitness){
							count++;
						}
					}
					map.put(indFitness, count);
				}
			}

			for(int i=0; i< individuals.length; i++){
				Individual ind = individuals[i];
				double indFitness = ((OneInstanceMultiCaseMultiObjectiveFitness)ind.fitness).multiInstanceMultiObjectiveFitness[m][0];
				((OneInstanceMultiCaseMultiObjectiveFitness)ind.fitness).multiInstanceNoveltyScore[m][0] = map.get(indFitness);
			}
		}

	}

//	public void writeEnsembleToFile(){
//		File ensembleFile = new File("job." + jobSeed + ".ensemble.stat");
//		try {
//			BufferedWriter writer = new BufferedWriter(new FileWriter(ensembleFile));
//			for(int i=0; i<ensembleAll.size(); i++){
//				writer.write("Generation: " + i);
//				writer.newLine();
//				writer.write("Ensemble size: \n" + ensembleAll.get(i).size());
//				writer.newLine();
//				for(int j=0; j<ensembleAll.get(i).size(); j++){
//					writer.write("Member: " + j);
//					writer.newLine();
//					writer.write("Fitness: [" + ensembleAll.get(i).get(j).fitness.fitness() + "]");
//					writer.newLine();
//					writer.write(((GPIndividual)ensembleAll.get(i).get(j)).toStringEnsemble(state));
////					writer.newLine();
//				}
//				writer.newLine();
//			}
//
//			writer.write("End");
//			writer.newLine();
//
//			writer.close();
//			System.out.println("write ensemble end in!");
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}


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

	//2021.12.28 modified by mengxu
	public void writeAllGenWilcoxonRankSumTestToFile(){
		File diversities = new File("job." + jobSeed + ".allGenWilcoxonRankSumTest.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			// 1 means significantly better, 0 means similar to, -1 represents significantly worse
			writer.write("Gen, WilcoxonRankSumTest");
			writer.newLine();
			for(int i=0; i<this.allGenWilcoxonRankSumTest.size(); i++){
				writer.write( i+ "," + this.allGenWilcoxonRankSumTest.get(i));
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2022.02.28 modified by mengxu
	//todo: need modify, this is only for crossover, need to add reproduction and mutation information!
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

			for(ParentForMutation ref: parentIndexForMutation){
				ref.setChildFitness(individuals[ref.getChildID()].fitness.fitness());
				writer.write( ref.getParentID()+ "," + ref.getParentFitness()+ "," + "null" + "," + "null" + "," + ref.getChildID() + "," + ref.getChildFitness() + "," + "null" + "," + "null");
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	public void writeMultiCaseFitnessOfTop400IndividualToFile(Individual[] individuals){
		PopulationUtils.sort(individuals);
		OneInstanceMultiCaseMultiObjectiveFitness fitness = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[0].fitness;
		int numCase = fitness.multiInstanceMultiObjectiveFitness.length;
		File diversities = new File("job." + jobSeed + "." + generation + ".multicasefitness.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("IndID, fitness, ");
			for(int i=0; i<numCase; i++){
				if(i == numCase-1){
					writer.write("case" + i);
				}
				else{
					writer.write("case" + i + ", ");
				}
			}
			writer.newLine();
			for(int ind=0; ind<400 && ind<individuals.length; ind++){
				fitness = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[ind].fitness;
				double[][] multiCaseFitness = fitness.multiInstanceMultiObjectiveFitness;
				writer.write(ind + ", " + fitness.fitness() + ", ");
				for(int i=0; i<numCase; i++){
					if(i == numCase-1){
						writer.write(""+multiCaseFitness[i][0]);
					}
					else{
						writer.write("" + multiCaseFitness[i][0] + ", ");
					}
				}
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

//	public void calculateNoveltyScore(Individual[] individuals, int numCase){
//		double error = 0.0000000001;
//
//		for(int m = 0; m<numCase; m++){
//			Map<Individual, Integer> map = new HashMap<>();
//
//			for(int i=0; i< individuals.length; i++){
//				Individual ind = individuals[i];
//				double indFitness = ((MultiInstanceMultiObjectiveFitness)ind.fitness).multiInstanceMultiObjectiveFitness[m][0];
//
//				int flag = 0;
//				for(int j=0; j<i; j++){
//					Individual indInpop = individuals[j];
//					double indInpopFitness = ((MultiInstanceMultiObjectiveFitness)indInpop.fitness).multiInstanceMultiObjectiveFitness[m][0];
//					if(Math.abs(indFitness - indInpopFitness) < error){
//						flag = 1;
//						break;
//					}
//				}
//				if(flag == 0){
//					int count = 1;
//					for(int j=i+1; j<individuals.length; j++){
//						Individual indInpop = individuals[j];
//						double indInpopFitness = ((MultiInstanceMultiObjectiveFitness)indInpop.fitness).multiInstanceMultiObjectiveFitness[m][0];
//						if(Math.abs(indFitness - indInpopFitness) < error){
//							count++;
//						}
//					}
//					map.put(ind, count);
//				}
//			}
//
//			for(int i=0; i< individuals.length; i++){
//				Individual ind = individuals[i];
//				double indFitness = ((MultiInstanceMultiObjectiveFitness)ind.fitness).multiInstanceMultiObjectiveFitness[m][0];
//
//				for(Individual indInMap: map.keySet()){
//					double indInMapFitness = ((MultiInstanceMultiObjectiveFitness)indInMap.fitness).multiInstanceMultiObjectiveFitness[m][0];
//					if(Math.abs(indFitness - indInMapFitness) < error){
//						((MultiInstanceMultiObjectiveFitness)ind.fitness).multiInstanceNoveltyScore[m][0] = map.get(indInMap);
//					}
//				}
//			}
//		}
//
//	}

	public int DoStatisticalTestBetweenEnsemblesIndividuals(boolean storeFitness){
		ArrayList<Double> individualsFitnessList = new ArrayList<>();
		ArrayList<Double> ensemblesFitnessList = new ArrayList<>();

		Individual[] individuals = this.population.subpops[0].individuals;
		ArrayList<EnsembleRule> ensemblePool = ((EnsembleEvaluator)this.evaluator).ensemblePool;

//		double[] individualsFitness = new double[individuals.length];
//		double[] ensemblesFitness = new double[ensemblePool.size()];

		for(int i=0; i<individuals.length; i++){
			OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution fitness = (OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)individuals[i].fitness;
			if(fitness.fitness() >= Double.MAX_VALUE || fitness.fitness() >= Double.POSITIVE_INFINITY){
				continue;
			}
			else{
//				individualsFitness[i]=fitness.fitness();
				individualsFitnessList.add(fitness.fitness());
			}
		}

		for(int i=0; i<ensemblePool.size(); i++){
			OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution fitness = (OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)ensemblePool.get(i).getFitness();
			if(fitness.fitness() >= Double.MAX_VALUE || fitness.fitness() >= Double.POSITIVE_INFINITY){
				continue;
			}
			else{
//				ensemblesFitness[i]=fitness.fitness();
				ensemblesFitnessList.add(fitness.fitness());
			}
		}

		double[] individualsFitness= individualsFitnessList.stream().mapToDouble(Double::doubleValue).toArray();
		double[] ensemblesFitness = ensemblesFitnessList.stream().mapToDouble(Double::doubleValue).toArray();

		if(storeFitness){
			writeIndividualAndEnsembleFitnessToFile(individualsFitness,ensemblesFitness);
		}

		//todo: do statistical test
		DoubleSummaryStatistics sampleStatsIndividual = Arrays.stream(individualsFitness).summaryStatistics();
		DoubleSummaryStatistics sampleStatsEnsemble = Arrays.stream(ensemblesFitness).summaryStatistics();

		//MannWhitneyUTest is the same with the wilcoxonRankSumTest
		MannWhitneyUTest wilcoxonRankSumTest = new MannWhitneyUTest();
		double ref =  wilcoxonRankSumTest.mannWhitneyUTest(ensemblesFitness,individualsFitness);

//		System.out.println("Ttest between ensembleFitness and individualFitness: " + ref);
//		System.out.println("ensembleFitness mean: " + sampleStatsEnsemble.getAverage());
//		System.out.println("individualFitness mean: " + sampleStatsIndividual.getAverage());


		if(ref < 0.05){
			if(sampleStatsEnsemble.getAverage() < sampleStatsIndividual.getAverage()){
				return 1;
			}
			else{
				return -1;
			}
		}
		else{
			return 0;
		}

	}

	public List<Individual> calculateEnsembleForGen(){
		List<Individual> ensembleForGen = new ArrayList<>();
		Individual[] individuals = this.population.subpops[0].individuals;
		List<int[]> allCaseFitRank = new ArrayList<>();
//		double[] caseFit = new double[individuals.length];
		OneInstanceMultiCaseMultiObjectiveFitness fitness0 = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[0].fitness;
		double[][] objectives0 = fitness0.multiInstanceMultiObjectiveFitness;
		int caseNum = objectives0.length;
		for(int i=0; i<caseNum; i++){
			for(int j=0; j<individuals.length; j++){
				OneInstanceMultiCaseMultiObjectiveFitness casefitness = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[j].fitness;
				int[][] eachCaseFitRank = casefitness.multiInstanceMultiObjectiveFitnessRank;
				if(eachCaseFitRank[i][0] == 0){
					ensembleForGen.add(individuals[j]);
					j=individuals.length;
				}
			}
		}
		return ensembleForGen;
	}

	public void updateIndividualRankForCases(){
		Individual[] individuals = this.population.subpops[0].individuals;
		List<int[]> allCaseFitRank = new ArrayList<>();
//		double[] caseFit = new double[individuals.length];
		OneInstanceMultiCaseMultiObjectiveFitness fitness0 = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[0].fitness;
		double[][] objectives0 = fitness0.multiInstanceMultiObjectiveFitness;
		int caseNum = objectives0.length;
		for(int i=0; i<caseNum; i++){
			double[] caseFit = new double[individuals.length];
			int[] caseFitRank = new int[individuals.length];
			for(int j=0; j<individuals.length; j++){
				OneInstanceMultiCaseMultiObjectiveFitness casefitness = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[j].fitness;
				double[][] eachCaseFit = casefitness.multiInstanceMultiObjectiveFitness;
				caseFit[j] = eachCaseFit[i][0];
				caseFitRank[j] = j;
			}
			int[] caseFitRankNew = sortforCaseReturnRank(caseFit, caseFitRank).clone();
			allCaseFitRank.add(caseFitRankNew);
		}

		for(int c=0; c<allCaseFitRank.size(); c++){
			int[] caseFitRank = allCaseFitRank.get(c);
			for(int j=0; j<caseFitRank.length; j++){
				int indId = caseFitRank[j];
				OneInstanceMultiCaseMultiObjectiveFitness casefitness = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[indId].fitness;
				casefitness.multiInstanceMultiObjectiveFitnessRank[c][0] = j;
			}
		}
	}

	//modified by mengxu 2022.04.25 sort individuals based on the instance index
	public int[] sortforCaseReturnRank(double[] caseFits, int[] caseFitRank) {
		//外层循环
		for (int i = 0; i < caseFits.length - 1; i++) {
			//内层循环
			for (int j = 0; j < caseFits.length - 1 - i; j++) {
				//两两比较
				if (caseFits[j] > caseFits[j + 1]) {
					double temp = caseFits[j];
					int tempRank = caseFitRank[j];
					caseFits[j] = caseFits[j + 1];
					caseFitRank[j] = caseFitRank[j + 1];
					caseFits[j + 1] = temp;
					caseFitRank[j + 1] = tempRank;
				}
			}
		}
		return caseFitRank;
	}

	//2021.12.16 modified by mengxu
	public void writeCaseNumberToFile(){
		File diversities = new File("job." + jobSeed + ".caseNumber.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Gen, Case, Times");
			writer.newLine();
			for (int ind = 0; ind < allGenerationCaseNumber.size(); ind++) {
				double[] ref = allGenerationCaseNumber.get(ind);
				for(int caseIndex=0; caseIndex < ref.length; caseIndex++ ){
					writer.write((ind + genForUseLS + 1) + "," + caseIndex + "," + ref[caseIndex]);
					writer.newLine();
				}
			}

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

	public void writeIndividualAndEnsembleFitnessToFile(double[] individualsFitness, double[] ensemblesFitness){
		File selectParentIndex = new File("job." + jobSeed + ".gen_" + this.generation + ".individualAndEnsembleFitness.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(selectParentIndex));
			writer.write("gen,index,ensemble,fitness");
			writer.newLine();
			for (int i = 0; i < individualsFitness.length; i++) {
				writer.write(this.generation + "," + i + ",false," + individualsFitness[i]);
				writer.newLine();
			}

			for (int i = 0; i < ensemblesFitness.length; i++) {
				int index = i+individualsFitness.length;
				writer.write(this.generation + "," + index + ",true," + ensemblesFitness[i]);
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.7.21 modified by mengxu, to store the selected parent index of each generation
	public void writeSelectParentIndexAndFitnessToFile(){
		File selectParentIndex = new File("job." + jobSeed + ".selectParentIndexAndFitness.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(selectParentIndex));
			for (int gen = 0; gen < allGenerationParentIndex.size(); gen++) {
				List<Integer> refIndex = allGenerationParentIndex.get(gen);
				List<Double> refFitness = allGenerationParentFitness.get(gen);
//				writer.write((gen+1) + ",");
				for(int i=0; i< refIndex.size()-1; i++){
					writer.write((gen+1) + "," + refIndex.get(i) + "," + refFitness.get(i));
					writer.newLine();
				}
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

	//add 2021.09.23
//	public void populationNormalisation(){
//		this.normalisationFitness.clear();
//		Individual[] oldinds = this.population.subpops[0].individuals.clone();
////		List<Double[][]> fitnessNormalisation = new ArrayList<>();
//		for(int i=0; i<oldinds.length; i++){
//			int numCase = ((MultiInstanceMultiObjectiveFitness)oldinds[i].fitness).multiInstanceMultiObjectiveFitness.length;
//			int numObjective = ((MultiInstanceMultiObjectiveFitness)oldinds[i].fitness).getNumObjectives();
//			double[][] multiCaseFitness = ((MultiInstanceMultiObjectiveFitness)oldinds[i].fitness).multiInstanceMultiObjectiveFitness;
//
//			double minFitnessCase = Double.POSITIVE_INFINITY;
//			double maxFitnessCase = 0;
//
//			for(int j=0; j<numCase; j++){
//				if(multiCaseFitness[j][0]>maxFitnessCase){
//					maxFitnessCase = multiCaseFitness[j][0];
//				}
//				if(multiCaseFitness[j][0]<minFitnessCase){
//					minFitnessCase = multiCaseFitness[j][0];
//				}
//			}
//
//			//todo: if used for multi-objective, need to modify.
//			double[][] normalisationFitness = new double[numCase][numObjective];
//			for(int j=0; j<numCase; j++){
//				if(maxFitnessCase >= Double.POSITIVE_INFINITY || maxFitnessCase >= Double.MAX_VALUE ||
//						minFitnessCase >= Double.POSITIVE_INFINITY || minFitnessCase >= Double.MAX_VALUE ){
//					double value = Double.POSITIVE_INFINITY;
//					normalisationFitness[j][0] = value;
//				}
//				else if(maxFitnessCase-minFitnessCase <= 0){
//					System.out.println("Error! maxFitnessCase-minFitnessCase could not be 0.");
//				}
//				else{
//					double value = (multiCaseFitness[j][0]-minFitnessCase)/(maxFitnessCase-minFitnessCase);
//					normalisationFitness[j][0] = value;
//				}
//			}
//			this.normalisationFitness.add(normalisationFitness);
//		}
//	}

	public List<double[][]> getNormalisationFitness() {
		return normalisationFitness;
	}

	//add 2021.09.24
	public void populationNormalisation(){
		this.normalisationFitness.clear();
		Individual[] oldinds = this.population.subpops[0].individuals.clone();
		int numCase = ((OneInstanceMultiCaseMultiObjectiveFitness)oldinds[0].fitness).multiInstanceMultiObjectiveFitness.length;
		int numObjective = ((OneInstanceMultiCaseMultiObjectiveFitness)oldinds[0].fitness).getNumObjectives();
		double[] minFitness = new double[numCase];
		double[] maxFitness = new double[numCase];
		for(int j=0; j<numCase; j++){
			minFitness[j] = Double.POSITIVE_INFINITY;
			maxFitness[j] = 0;
		}

//		List<Double[][]> fitnessNormalisation = new ArrayList<>();
		for(int i=0; i<oldinds.length; i++){
			double[][] multiCaseFitness = ((OneInstanceMultiCaseMultiObjectiveFitness)oldinds[i].fitness).multiInstanceMultiObjectiveFitness;

			for(int j=0; j<numCase; j++){
				if(multiCaseFitness[j][0] >= Double.POSITIVE_INFINITY || multiCaseFitness[j][0] >= Double.MAX_VALUE){
					continue;
				}
				else{
					if(multiCaseFitness[j][0]>maxFitness[j]){
						maxFitness[j] = multiCaseFitness[j][0];
					}
					if(multiCaseFitness[j][0]<minFitness[j]){
						minFitness[j] = multiCaseFitness[j][0];
					}
				}
			}
		}

//		System.out.print("minFitness and maxFitness: ");
//		for(int j=0; j<numCase; j++){
//			System.out.print("(" + minFitness[j] + ", " + maxFitness[j] + ")");
//		}
//		System.out.println();

		//todo: if used for multi-objective, need to modify.
		for(int i=0; i<oldinds.length; i++) {
			double[][] multiCaseFitness = ((OneInstanceMultiCaseMultiObjectiveFitness)oldinds[i].fitness).multiInstanceMultiObjectiveFitness;

			double[][] normalisationFitness = new double[numCase][numObjective];
			for (int j = 0; j < numCase; j++) {
				if (multiCaseFitness[j][0] >= Double.POSITIVE_INFINITY || multiCaseFitness[j][0] >= Double.MAX_VALUE) {
					double value = Double.POSITIVE_INFINITY;
					normalisationFitness[j][0] = value;
				} else if (maxFitness[j] - minFitness[j] <= 0) {
					System.out.println("Error! maxFitnessCase-minFitnessCase could not be 0.");
				} else {
					double value = (multiCaseFitness[j][0] - minFitness[j]) / (maxFitness[j] - minFitness[j]);
					normalisationFitness[j][0] = value;
				}
			}
			this.normalisationFitness.add(normalisationFitness);
		}
	}

}
