package mengxu.algorithm.MAPElites.GridMapElites;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleProblemForm;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.MAPElites.CVTMAPElites.CVTPCmap;
import mengxu.algorithm.MAPElites.CVTMAPElites.GPRuleEvolutionStateCVTMAPElites;
import mengxu.algorithm.diversitymeasure.*;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
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

public class GPRuleEvolutionStateGridMAPElites extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();

	public PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];

	public gridPCmap gridPCmap;

	public int gridSIze;

	public double reward = 0.15;

	public boolean use_grid_map;
	public boolean output_all_elites;

	public boolean parentsOnlyFromGridMap;
	public boolean parentsOnlyFromPop;

	//added by mengxu 2023.03.02
	public Individual[] parentsForCrossover = new Individual[2];
	public Individual parentsForMutation;
	public ArrayList<Individual> allIndividualsInGridMap;

	double[] baselineObjectives;

	public int base;

	public boolean mapUpdateImmediate;

	public boolean reevaluateGridMap;

	public boolean usingManualRuleNormalisation;

	public int elites_num;

	public double penalty_level;

	public double randomParentSelectionProbability;

	public double previousBaseline = -1;
	public double newBaseline = -1;

	public int updateNum = 0;

	public int num_accept;


	@Override
	public void setup(EvolutionState state, Parameter base) {

		super.setup(this, base);

		//calculate the pc of each individual in each subpop and do cluster--------------
		this.phenoCharacterisation = new PhenoCharacterisation[2];
		Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);
//		//dynamic simulation original consider 20 routing and 20 sequencing and 7 candidates
//		phenoCharacterisation[0] =
//				SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
//		phenoCharacterisation[1] =
//				RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);

		//dynamic simulation
		int minQueueLength = 3;
		int numDecisionSituations = 4;
		this.base = minQueueLength;
		this.gridSIze = (int) Math.pow(minQueueLength, numDecisionSituations);
		phenoCharacterisation[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation, minQueueLength, numDecisionSituations);
		phenoCharacterisation[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation,minQueueLength, numDecisionSituations);

		//generate the PC map, added by mengxu 2023.06.21
		Parameter useGridMapParam = new Parameter("use-grid-map");
		this.use_grid_map = state.parameters.getBoolean(useGridMapParam, false);

		Parameter parentsOnlyFromGridMapParam = new Parameter("parents-only-from-GridMap");
		this.parentsOnlyFromGridMap = state.parameters.getBoolean(parentsOnlyFromGridMapParam, false);

		Parameter parentsOnlyFromPopParam = new Parameter("parents-only-from-pop");
		this.parentsOnlyFromPop = state.parameters.getBoolean(parentsOnlyFromPopParam, false);

		Parameter outputAllElitesParam = new Parameter("output-all-elites");
		this.output_all_elites = state.parameters.getBoolean(outputAllElitesParam, false);

		Parameter mapUpdateImmediateParam = new Parameter("map-update-immediate");
		this.mapUpdateImmediate = state.parameters.getBoolean(mapUpdateImmediateParam, false);

		Parameter reevaluateGridMapParam = new Parameter("reevaluate-grid-map");
		this.reevaluateGridMap = state.parameters.getBoolean(reevaluateGridMapParam, false);

		Parameter usingManualRuleNormalisationParam = new Parameter("using-manual-rule-normalisation");
		this.usingManualRuleNormalisation = state.parameters.getBoolean(usingManualRuleNormalisationParam, false);

		Parameter elitesNumParam = new Parameter("breed.elite.0");
		this.elites_num = state.parameters.getInt(elitesNumParam, null,0);

		Parameter penalty_levelParam = new Parameter("penalty-level");
		this.penalty_level = state.parameters.getDouble(penalty_levelParam, null,0);
//		System.out.println("penalty-level = " + this.penalty_level);

		Parameter randomParentSelectionProbabilityParam = new Parameter("random-parent-selection-probability");
		this.randomParentSelectionProbability = state.parameters.getDouble(randomParentSelectionProbabilityParam, null,0);

		//add by mengxu 2025.1.15
		Parameter numAcceptParam = new Parameter("number-accept");
		this.num_accept = state.parameters.getInt(numAcceptParam, null,1);
	}

	// Utility method to print a double array
	public static void printArray(double[] array) {
		for (double value : array) {
			System.out.print(value + " ");
		}
		System.out.println(); // New line after printing the array
	}

    @Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

		if(this.use_grid_map && generation==0){
			this.gridPCmap = new gridPCmap(gridSIze,base,num_accept); // initilise the PC map
		}

	    // EVALUATION
	    statistics.preEvaluationStatistics(this);
//		evaluator.evaluatePopulation(this);

		if(this.use_grid_map && this.mapUpdateImmediate){
			if(generation==0){
				evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
			}
			else{
				//only evaluate the evaluate the elites that haven't been evaluate before
				int end = this.population.subpops[0].individuals.length;
				int start = end - this.elites_num;
//				double[] previousFit = new double[end-start];
//				double[] newFit = new double[end-start];
				for(int j=start; j<end; j++){
					Individual eliteInd = this.population.subpops[0].individuals[j];
//					previousFit[j-start] = eliteInd.fitness.fitness()/this.previousBaseline;
					SimpleProblemForm prob = (SimpleProblemForm) (this.evaluator.p_problem.clone());
					prob.evaluate(this, eliteInd, 0, 0);
					eliteInd.evaluated = true;
//					newFit[j-start] = eliteInd.fitness.fitness()/this.newBaseline;
				}
//				this.baselineObjectives[0] = Arrays.stream(newFit).sum()/(end-start);
//				System.out.println("Baseline fitness: " + this.baselineObjectives[0]);
				// Print the arrays
//				System.out.println("this.previousBaseline: " + this.previousBaseline);
//				System.out.println("Previous Fit: ");
//				printArray(previousFit);
//
//				System.out.println("this.newBaseline: " + this.newBaseline);
//				System.out.println("New Fit: ");
//				printArray(newFit);
			}
		}
		else{
			evaluator.evaluatePopulation(this);
		}

		if(this.usingManualRuleNormalisation){
			this.previousBaseline = this.calculateManualRuleFitness();
//			this.previousBaseline = this.calculateSetOfManuralRuleFitness();
		}

		PopulationUtils.sort(this.population);//added by mengxu 2023.08.11
		//modified by mengxu. Measure diversity. 2021.04.15
		Individual[] individuals = this.population.subpops[0].individuals;
		GenotypeDiversity genoD = new GenotypeDiversity();
		double genoDvalue = (double)genoD.genotypeDiversity(individuals) / individuals.length;
//		System.out.println("Genotype diversity: " + genoDvalue);
		PhenotypeDiversity phenoD = new PhenotypeDiversity();
		double phenoDvalue = (double)phenoD.phenotypeDiversity(individuals) / individuals.length;
		PseudoIsomorphsDiversity pseIsoD = new PseudoIsomorphsDiversity();
		double pseIsoDvalue = (double)pseIsoD.pseudoIsomorphsDiversity(individuals) / individuals.length;
//		System.out.println("Pseudo isomorphs diversity: " + pseIsoDvalue);
		EditDistanceDiversityV1 edit1D = new EditDistanceDiversityV1();
		double edit1Dvalue = edit1D.editDistanceDiversityV1(individuals, bestIndi(0));//todo: need modified.
//		System.out.println("Edit 1 diversity: " + edit1Dvalue);
		EditDistanceDiversityV2 edit2D = new EditDistanceDiversityV2();
		double edit2Dvalue = edit2D.editDistanceDiversityV2(individuals, bestIndi(0));//todo: need modified.
//		System.out.println("Edit 2 diversity: " + edit2Dvalue);

		// Strategy 1: using the index that represent the selection of the best rule
		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, false); //3. calculate the phenotypic characteristic
		for (int i = 0; i < indsCharListsMultiTree.length; i++) {
			for (int j = 0; j < indsCharListsMultiTree[i].length; j++) {
				for (int k = 0; k < indsCharListsMultiTree[i][j].length; k++) {
					indsCharListsMultiTree[i][j][k] -= 1; // Equivalent to array[i][j][k] = array[i][j][k] - 1
				}
			}
		}
		// Strategy 2: using the index of machine/operation directly 2024.9.6
//		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.decisionsPopulationFixedDecisions(this, phenoCharacterisation, false); //3. calculate the phenotypic characteristic
//		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.decisionsPopulationFixedDecisions(this, phenoCharacterisation, false);

		//get the fitness for training model
		double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];
		int subpoplen = fitnessesForModel.length;
		double[] lowBoundFit = new double[subpoplen];
		double[] upBoundFit = new double[subpoplen];
		for(int i=0; i<subpoplen; i++){
			lowBoundFit[i] = Double.POSITIVE_INFINITY; //add by mengxu 2023.07.11
			upBoundFit[i] = Double.NEGATIVE_INFINITY; //add by mengxu 2023.07.11
		}
		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
				double fit = this.population.subpops[subpop].individuals[ind].fitness.fitness();
				fitnessesForModel[subpop][ind] = fit;
				if(this.usingManualRuleNormalisation){
					fitnessesForModel[subpop][ind] = fit / this.baselineObjectives[0];
				}
				//add by mengxu 2023.07.11
				if(fit >= Double.POSITIVE_INFINITY || fit >= Double.MAX_VALUE){
					continue;
				}
				else{
					if(lowBoundFit[subpop]>fit){
						lowBoundFit[subpop] = fit;
					}
					if(upBoundFit[subpop]<fit){
						upBoundFit[subpop] = fit;
					}
				}
			}
		}
		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
//		System.out.println("PC diversity: " + pcDvalue);

		EntropyDiversityBasedOnPC entroD = new EntropyDiversityBasedOnPC();
		double entroDvalue = entroD.entropyDiversityBasedOnPC(individuals,indsCharListsMultiTree[0]);
//		System.out.println("Entropy diversity: " + entroDvalue);

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
		storeGenDiversities.add(diversities);

		if(this.use_grid_map){
			//todo: need to update the probability of each type of evolution strategy here!!! 2023.07.05
			//add by mengxu 2023.07.11
			if(!this.mapUpdateImmediate || this.generation==0){
				this.updateNum = 0;
				this.gridPCmap.updateGridMap(this, individuals, indsCharListsMultiTree[0], fitnessesForModel[0]);
				System.out.println("UpdateNum all: " + this.updateNum);
			}

			this.allIndividualsInGridMap = this.gridPCmap.getAllIndividualsInGridMap();
//			ArrayList<double[]> allFitsInCVTMap = this.CVTmap.getAllIndividualsFitnessInCVTMap(this);
			int numInCVTMap = allIndividualsInGridMap.size();
//			allIndividualsInGridMap = PopulationUtils.sortIndsAndReturnArray(allIndividualsInGridMap);//todo: this is only suitable for DO NOT rotate instance!!!
			System.out.println("Number in grid map: " + numInCVTMap);
//			this.gridPCmap.printAllFitnessInGridMap();
			this.gridPCmap.writeAllFitnessInGridMapToCSV(this);//original by mengxu 2024.12.24
			if(this.generation == this.numGenerations-1){
				this.gridPCmap.saveCoverageAndMeanPerformanceAbdBestPerformanceToCSV(this,true);
			}
			else{
				this.gridPCmap.saveCoverageAndMeanPerformanceAbdBestPerformanceToCSV(this,false);
			}
		}

		//original place
		statistics.postEvaluationStatistics(this);


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
//			writePCsToFile(this, indsCharListsMultiTree[0], "population"); //output the PCs of population
//			if(this.use_grid_map){
//				ArrayList<Individual> allindividualsInMAP = this.gridPCmap.getAllIndividualsInGridMap();
//				double[][][] indsCharListsMultiTreeInMAP = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisionsIndividuals(this, allindividualsInMAP, phenoCharacterisation, false);
//				writePCsToFile(this, indsCharListsMultiTreeInMAP[0], "MAP"); //output the PCs of individuals in MAP
//			}
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

		this.updateNum = 0;

	    // BREEDING
	    statistics.preBreedingStatistics(this);

	    population = breeder.breedPopulation(this); //!!!!!!   return newpop;  if it is NSGA-II, the population here is 2N

	    // POST-BREEDING EXCHANGING
	    statistics.postBreedingStatistics(this);   //position 1  here, a new pop has been generated.

		System.out.println("UpdateNum: " + this.updateNum);

	    // POST-BREEDING EXCHANGING
	    statistics.prePostBreedingExchangeStatistics(this);
	    population = exchanger.postBreedingExchangePopulation(this);   /** Simply returns state.population. */
	    statistics.postPostBreedingExchangeStatistics(this);  //position 2

		// Generate new instances if needed
		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
		if (problem.getEvaluationModel().isRotatable()) {
			problem.rotateEvaluationModel();
			if(this.usingManualRuleNormalisation){
				this.newBaseline = this.calculateManualRuleFitness();
//				this.newBaseline = this.calculateSetOfManuralRuleFitness();
			}
			//add by mengxu 2024.11.22 to reevaluate all the individuals in the grid map
			if(reevaluateGridMap){
				this.gridPCmap.reevaluateGridMap(this);
			}
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


	public void IndividualFitnessEvaluation(EvolutionState state, Individual individual){
		//Individual fitness evaluation
		SchedulingSet schedulingSet = ((MultipleRuleEvaluationModel) ((MultipleTreeRuleOptimizationProblem) state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet();

		if (((GPRuleEvolutionStateGridMAPElites)state).mapUpdateImmediate) {
			//for test
//            System.out.println("Individual fitness before: "+ individual.fitness.fitness());
			SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
			prob.evaluate(state, individual, 0, 0);
			individual.evaluated = true; //this is important, this is for avoid evaluate twice
			//for test
//            System.out.println("Individual fitness after: "+ individual.fitness.fitness());
			double[] indCharListsMultiTree = phenotypicForSurrogateV1.phenotypicIndividualFixedDecisions(individual, ((GPRuleEvolutionStateGridMAPElites)state).phenoCharacterisation);
			for (int j = 0; j < indCharListsMultiTree.length; j++) {
				indCharListsMultiTree[j] -= 1; // Equivalent to array[i][j][k] = array[i][j][k] - 1
			}
			double[] objectiveValues = ((MultiObjectiveFitness)individual.fitness).getObjectives();
			if(((GPRuleEvolutionStateGridMAPElites)state).usingManualRuleNormalisation){
				double[] objectiveValuesNormalised = new double[objectiveValues.length];
				for(int i=0; i<objectiveValues.length; i++){
					double normalisedFit = objectiveValues[i] / ((GPRuleEvolutionStateGridMAPElites)state).baselineObjectives[i];
					objectiveValuesNormalised[i] = normalisedFit;
				}
				((GPRuleEvolutionStateGridMAPElites)state).gridPCmap.updateGridMapWithIndividual(state, individual,indCharListsMultiTree,objectiveValuesNormalised[0]);
				//Normalise the fitness of individual using manual rule normalisation
			}
			else{
				((GPRuleEvolutionStateGridMAPElites)state).gridPCmap.updateGridMapWithIndividual(state, individual,indCharListsMultiTree,objectiveValues[0]);
				//Normalise the fitness of individual using manual rule normalisation
			}
		}
	}


	public double calculateManualRuleFitness(){
		//Individual fitness evaluation
		this.baselineObjectives = new double[this.population.subpops.length];
		SchedulingSet schedulingSet = ((MultipleRuleEvaluationModel) ((MultipleTreeRuleOptimizationProblem) this.evaluator.p_problem).getEvaluationModel()).getSchedulingSet();

		List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) this.evaluator.p_problem).getObjectives();
		schedulingSet.lowerBoundsFromBenchmarkRule(objectives);
		RealMatrix objectiveLowerBoundMtx = schedulingSet.getObjectiveLowerBoundMtx();
		this.baselineObjectives = objectiveLowerBoundMtx.getData()[0];
		for(int i=0; i<baselineObjectives.length; i++){
			System.out.println("Baseline fitness: "+ baselineObjectives[i]);
		}

		return baselineObjectives[0];
	}

	//add by mengxu 2025.1.6
	public double calculateSetOfManuralRuleFitness(){
		//Individual fitness evaluation
		this.baselineObjectives = new double[this.population.subpops.length];
		SchedulingSet schedulingSet = ((MultipleRuleEvaluationModel) ((MultipleTreeRuleOptimizationProblem) this.evaluator.p_problem).getEvaluationModel()).getSchedulingSet();

		List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) this.evaluator.p_problem).getObjectives();
		schedulingSet.lowerBoundsFromBenchmarkRule(objectives);
//		RealMatrix objectiveLowerBoundMtx = schedulingSet.getObjectiveLowerBoundMtx();
		this.baselineObjectives[0] = schedulingSet.lowerBoundsFromSetOfBenchmarkRule(objectives);;
		for(int i=0; i<baselineObjectives.length; i++){
			System.out.println("Baseline fitness: "+ baselineObjectives[i]);
		}

		return baselineObjectives[0];
	}

	public List<double[]> calculatePC(Individual individual){
		PhenoCharacterisation[] pc = this.phenoCharacterisation;
		RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
		int[] charListSeq;
		int[] charListRout;
		int treeID = 0;
		PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
		RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
		charListSeq = phenoCharacterisation.characterise(  //characterise: calculate the distance
				new GPRule(ruleType, ((GPIndividual) individual).trees[treeID]));
		treeID = 1;
		phenoCharacterisation = pc[treeID];
		ruleType = ruleTypes[treeID];
		charListRout = phenoCharacterisation.characterise(  //characterise: calculate the distance
				new GPRule(ruleType, ((GPIndividual) individual).trees[treeID]));

		double[] charListSeqDouble = new double[charListSeq.length];
		double[] charListRoutDouble = new double[charListRout.length];

		for(int i=0;i<charListSeq.length;i++){
			charListSeqDouble[i] = (double)charListSeq[i];
			charListRoutDouble[i] = (double)charListRout[i];
		}
		List<double[]> PC = new ArrayList<>();
		PC.add(charListSeqDouble);
		PC.add(charListRoutDouble);
		return PC;
	}

	public double calculateEpsilon(Individual[] allIndividuals){
		List<Double> allFitness = new ArrayList<>();
		for(int ref=0; ref<allIndividuals.length; ref++){
			double fit = allIndividuals[ref].fitness.fitness();
			if(fit >= Double.MAX_VALUE || fit >= Double.POSITIVE_INFINITY){
				continue;
			}
			else{
				allFitness.add(fit);
			}
		}
		allFitness.sort(Double::compareTo);

		double median;
		if(allFitness.size() % 2 == 0){
			int indexMedian = allFitness.size() / 2;
			median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
		}
		else{
			int indexMedian = allFitness.size() / 2;
			median = allFitness.get(indexMedian);
		}
		for(int ref2=0; ref2<allFitness.size(); ref2++){
			allFitness.set(ref2, Math.abs(allFitness.get(ref2) - median));
		}
		allFitness.sort(Double::compareTo);
		if(allFitness.size() % 2 == 0){
			int indexMedian = allFitness.size() / 2;
			median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
		}
		else{
			int indexMedian = allFitness.size() / 2;
			median = allFitness.get(indexMedian);
		}
		return median;
	}

	public double calculateMedian(Individual[] allIndividuals){
		List<Double> allFitness = new ArrayList<>();
		for(int ref=0; ref<allIndividuals.length; ref++){
			double fit = allIndividuals[ref].fitness.fitness();
			if(fit >= Double.MAX_VALUE || fit >= Double.POSITIVE_INFINITY){
				continue;
			}
			else{
				allFitness.add(fit);
			}
		}
		allFitness.sort(Double::compareTo);

		double median;
		if(allFitness.size() % 2 == 0){
			int indexMedian = allFitness.size() / 2;
			median = (allFitness.get(indexMedian-1) + allFitness.get(indexMedian))/2;
		}
		else{
			int indexMedian = allFitness.size() / 2;
			median = allFitness.get(indexMedian);
		}
		return median;
	}

	//2021.2.15 modified by mengxu
	public void writeDiversityToFile(){
		File diversities = new File("job." + jobSeed + ".diversities.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Gen, Geno, Pheno, Entropy, PseIso, Edit 1, Edit 2, PC");
			writer.newLine();
			for (int ind = 0; ind < storeGenDiversities.size(); ind++) {
				ArrayList<Double> ref = storeGenDiversities.get(ind);
				writer.write(ref.get(0) + "," + ref.get(1) + "," + ref.get(2)
						+ "," + ref.get(3) + "," + ref.get(4) + "," + ref.get(5)
						+ "," + ref.get(6)+ "," + ref.get(7));
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.2.15 modified by mengxu
	public void writePCsToFile(EvolutionState state, double[][] indsCharListsMultiTree0, String filename){
		File diversities = new File("job." + jobSeed + "." + filename + ".PCs.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Run,Generation,Ind,D1,D2,D3,D4,D5,D6,D7,D8,D9,D10,D11,D12,D13,D14,D15,D16,D17,D18,D19,D20," +
					"D21,D22,D23,D24,D25,D26,D27,D28,D29,D30,D31,D32,D33,D34,D35,D36,D37,D38,D39,D40");
			writer.newLine();
			for(int c=0; c<indsCharListsMultiTree0.length; c++){
				writer.write(jobSeed + "," + state.generation + "," + c);
				for(int d=0; d<40; d++){
					writer.write("," + indsCharListsMultiTree0[c][d]);
				}
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
}
