package mengxu.algorithm.semantic.modifyV1;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.multiobjective.NSGPII.zScore.NSGA2MultiObjectiveFitnessNormalisation;
import mengxu.util.PCspaceRandomGenerate;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
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
import java.util.List;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author yimei
 *
 */

public class GPRuleEvolutionStateSemanticV1 extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();

	public PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];

	PCspaceRandomGenerate PCMap;

	public boolean doSequencingLocalSearch = false;
	public boolean doSequencingExplore = false;

	public double localSearchAcceptDis = 5;
	public boolean doRoutingLocalSearch = false;
	public boolean doRoutingExplore = false;

	public double exploreAcceptDis = 10;

//	public double proForLocalSearchAndExplore = 0.5;

	public int generationForDoPCMap = 10;

	public boolean doLocalSearch;

	public boolean doExploration;

	public boolean focusOnWholeMap;

	public int timesDoLocalSearch; //added by mengxu 2023.06.27
	public int timesDoExploration; //added by mengxu 2023.06.27

	public double limitPortsDoLocalSearch; //added by mengxu 2023.06.28
	public double limitPortsDoExploration; //added by mengxu 2023.06.28
	public double limitPortsDoNormalEvolution; //added by mengxu 2023.06.28
	public int limitTimesDoLocalSearch; //added by mengxu 2023.06.28
	public int limitTimesDoExploration; //added by mengxu 2023.06.28

	public int frequencyForLocalSearch; //added by mengxu 2023.06.28
	public int frequencyForExploration; //added by mengxu 2023.06.28

	ArrayList<ArrayList<Double>> storeGenLocalSearchAndExplorationPro = new ArrayList<>(); //added by mengxu 2023.06.28
	ArrayList<ArrayList<Double>> storeGenSeqAndRoutExplored = new ArrayList<>(); //added by mengxu 2023.06.28

	ArrayList<ArrayList<Integer>> storeGenNumOffByLocalAndExplore = new ArrayList<>(); //added by mengxu 2023.06.28

	ArrayList<double[]> historicalRecordForLocalSearch = new ArrayList<>(); //added by mengxu 2023.07.05
	ArrayList<double[]> historicalRecordForExploration = new ArrayList<>(); //added by mengxu 2023.07.05
	ArrayList<double[]> historicalRecordForNormalEvolution = new ArrayList<>(); //added by mengxu 2023.07.05
	public int historicalRecordLength = 10; //added by mengxu 2023.07.05

	public double reward = 0.15;
	ArrayList<Integer> offspringIndexByLocalSearch = new ArrayList<>(); //added by mengxu 2023.07.05
	ArrayList<Integer> offspringIndexByExploration = new ArrayList<>(); //added by mengxu 2023.07.05
	ArrayList<Integer> offspringIndexByNormalEvolution = new ArrayList<>(); //added by mengxu 2023.07.05

	public boolean SPA_guided = true;
	public boolean entropy_diversity_guided = true;
	public double entropyGuidedThreshold;

	public double[] medianZScoreNormalised;

	public boolean recordFitnessNormalisation;
	public boolean usingZScoreNormalisation;

	@Override
	public void setup(EvolutionState state, Parameter base) {

		super.setup(this, base);

		//calculate the pc of each individual in each subpop and do cluster--------------
		this.phenoCharacterisation = new PhenoCharacterisation[2];
		Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);
		//dynamic simulation
		phenoCharacterisation[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		phenoCharacterisation[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);

		//generate the PC map, added by mengxu 2023.06.21
		Parameter mapSizeParam = new Parameter("map-size");
		int mapSize = state.parameters.getInt(mapSizeParam,null,0);
		this.PCMap = new PCspaceRandomGenerate(20,20,mapSize);
		this.PCMap.generatePCMap(this);

		Parameter doLocalSearchParam = new Parameter("do-local-search");
		this.doLocalSearch = state.parameters.getBoolean(doLocalSearchParam, false);

		Parameter doExplorationParam = new Parameter("do-exploration");
		this.doExploration = state.parameters.getBoolean(doExplorationParam, false);

		Parameter doSPAguidedParam = new Parameter("SPA-guided");
		this.SPA_guided = state.parameters.getBoolean(doSPAguidedParam, false);

		Parameter doEntropyDiversityGuidedParam = new Parameter("entropy-diversity-guided");
		this.entropy_diversity_guided = state.parameters.getBoolean(doEntropyDiversityGuidedParam, false);

		Parameter entropyGuidedThresholdParam = new Parameter("entropy-guided-threshold");
		this.entropyGuidedThreshold = state.parameters.getDouble(entropyGuidedThresholdParam,null,0);

		Parameter generationForDoPCMapParam = new Parameter("generation-for-using-semantic-map");
		this.generationForDoPCMap = state.parameters.getInt(generationForDoPCMapParam,null,0);

		this.timesDoExploration = 0;
		this.timesDoLocalSearch = 0;

		Parameter portsLocalSearchParam = new Parameter("ports-local-search");
		this.limitPortsDoLocalSearch = state.parameters.getDouble(portsLocalSearchParam,null,0);

		Parameter portsExplorationParam = new Parameter("ports-exploration");
		this.limitPortsDoExploration = state.parameters.getDouble(portsExplorationParam,null,0);

		this.limitPortsDoNormalEvolution = 1 - this.limitPortsDoLocalSearch - this.limitPortsDoExploration;

		Parameter populationSizeParam = new Parameter("pop.subpop.0.size");
		this.limitTimesDoLocalSearch = (int)(this.limitPortsDoLocalSearch * state.parameters.getInt(populationSizeParam,null,1));
		this.limitTimesDoExploration = (int)(this.limitPortsDoExploration * state.parameters.getInt(populationSizeParam,null,1));

		Parameter localSearchAcceptDisParam = new Parameter("local-search-accept-distance");
		this.localSearchAcceptDis = state.parameters.getDouble(localSearchAcceptDisParam,null,0);

		Parameter exploreAcceptDisParam = new Parameter("exploration-accept-distance");
		this.exploreAcceptDis = state.parameters.getDouble(exploreAcceptDisParam,null,0);

		Parameter frequencyForLocalSearchParam = new Parameter("frequency-for-local-search-region");
		this.frequencyForLocalSearch = state.parameters.getInt(frequencyForLocalSearchParam,null,0);

		Parameter frequencyForExplorationParam = new Parameter("frequency-for-exploration");
		this.frequencyForExploration = state.parameters.getInt(frequencyForExplorationParam,null,0);

		ArrayList<Double> GenSeqExplored = new ArrayList<>();
		ArrayList<Double> GenRoutExplored = new ArrayList<>();
		this.storeGenSeqAndRoutExplored.add(GenSeqExplored);
		this.storeGenSeqAndRoutExplored.add(GenRoutExplored);

		ArrayList<Integer> GenNumOffByLocal = new ArrayList<>();
		ArrayList<Integer> GenNumOfByExplore = new ArrayList<>();
		this.storeGenNumOffByLocalAndExplore.add(GenNumOffByLocal);
		this.storeGenNumOffByLocalAndExplore.add(GenNumOfByExplore);

		//added by mengxu 2023.07.11
		Parameter recordFitnessNormalisationParam = new Parameter("record-fitness-normalisation");
		this.recordFitnessNormalisation = state.parameters.getBoolean(recordFitnessNormalisationParam, false);

		Parameter usingZScoreNormalisationParam = new Parameter("using-zScore-normalisation");
		this.usingZScoreNormalisation = state.parameters.getBoolean(usingZScoreNormalisationParam, false);

		this.storeGenLocalSearchAndExplorationPro = new ArrayList<>(); //add by mengxu 2023.07.21

		Parameter focusOnWholeMapParam = new Parameter("focus-on-whole-map"); //add by mengxu 2023.07.21
		this.focusOnWholeMap = state.parameters.getBoolean(focusOnWholeMapParam, false);
	}

    @Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

	    //System.out.println("generation "+generation);
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

	    evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
	    statistics.postEvaluationStatistics(this);

		//modified by mengxu. Measure diversity. 2021.04.15
		Individual[] individuals = this.population.subpops[0].individuals;
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

		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, false); //3. calculate the phenotypic characteristic
		//get the fitness for training model
		double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];
		double lowBoundFit = Double.POSITIVE_INFINITY; //add by mengxu 2023.07.11
		double upBoundFit = Double.NEGATIVE_INFINITY; //add by mengxu 2023.07.11
		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
				double fit = this.population.subpops[subpop].individuals[ind].fitness.fitness();
				fitnessesForModel[subpop][ind] = fit;
				//add by mengxu 2023.07.11
				if(fit >= Double.POSITIVE_INFINITY || fit >= Double.MAX_VALUE){
					continue;
				}
				else{
					if(lowBoundFit>fit){
						lowBoundFit = fit;
					}
					if(upBoundFit<fit){
						upBoundFit = fit;
					}
				}
			}
		}
		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
		System.out.println("PC diversity: " + pcDvalue);

		//add by mengxu 2023.06.06
//		double pcDvalue2 = (double)pcD.phenotypicCharacteristicDiversity(((NSGA2EvaluatorNoEnvironmentalSelectionPhenotypeBreeding)this.evaluator).offspringCharLists) / individuals.length;
//		System.out.println("PC diversity 2: " + pcDvalue2);
//		((NSGA2EvaluatorNoEnvironmentalSelectionPhenotypeBreeding)this.evaluator).offspringCharLists.clear();

		EntropyDiversityBasedOnPC entroD = new EntropyDiversityBasedOnPC();
		double entroDvalue = entroD.entropyDiversityBasedOnPC(individuals,indsCharListsMultiTree[0]);
		System.out.println("Entropy diversity: " + entroDvalue);

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

		//todo: need to update the probability of each type of evolution strategy here!!! 2023.07.05
		//calculate the fitness limitation based on epsilon (get median twice, this idea is borrowed from the epsilon-LS)
		int indexBest = PopulationUtils.getIndexOfbestInds(this.population,0);
		double bestFit = this.population.subpops[0].individuals[indexBest].fitness.fitness();
//		double epsilon = calculateEpsilon(this.population.subpops[0].individuals);
//		double fitnessLimit = bestFit + epsilon;
		double median = calculateMedian(this.population.subpops[0].individuals);
		double fitnessLimit = median;
		if(this.PCMap.isRecordFitnessNormalisation()){
			fitnessLimit = (fitnessLimit - lowBoundFit) / (upBoundFit - lowBoundFit);
		}
		if(SPA_guided && !entropy_diversity_guided){
			this.updateProbabilityForEachEvolutionStrategy(individuals,fitnessLimit);
			this.limitTimesDoLocalSearch = (int)(this.limitPortsDoLocalSearch * individuals.length);
			this.limitTimesDoExploration = (int)(this.limitPortsDoExploration * individuals.length);
			System.out.println("Probability of local search: " + this.limitPortsDoLocalSearch);
			System.out.println("Probability of exploration: " + this.limitPortsDoExploration);
			System.out.println("Probability of normal evolution: " + this.limitPortsDoNormalEvolution);
		}
		else if(!SPA_guided && entropy_diversity_guided){
			if(entroDvalue > this.entropyGuidedThreshold){
				this.limitPortsDoLocalSearch = this.limitPortsDoLocalSearch + reward;
			}
			else{
				this.limitPortsDoExploration = this.limitPortsDoExploration + reward;
				this.limitPortsDoNormalEvolution = this.limitPortsDoNormalEvolution + reward;
			}
			//normalisation
			double newLimitPortsDoLocalSearch = this.limitPortsDoLocalSearch/(this.limitPortsDoLocalSearch + this.limitPortsDoExploration + this.limitPortsDoNormalEvolution);
			double newLimitPortsDoExploration = this.limitPortsDoExploration/(this.limitPortsDoLocalSearch + this.limitPortsDoExploration + this.limitPortsDoNormalEvolution);
			double newLimitPortsDoNormalEvolution = this.limitPortsDoNormalEvolution/(this.limitPortsDoLocalSearch + this.limitPortsDoExploration + this.limitPortsDoNormalEvolution);
			this.limitPortsDoLocalSearch = newLimitPortsDoLocalSearch;
			this.limitPortsDoExploration = newLimitPortsDoExploration;
			this.limitPortsDoNormalEvolution = newLimitPortsDoNormalEvolution;
			this.limitTimesDoLocalSearch = (int)(this.limitPortsDoLocalSearch * individuals.length);
			this.limitTimesDoExploration = (int)(this.limitPortsDoExploration * individuals.length);
			System.out.println("Probability of local search: " + this.limitPortsDoLocalSearch);
			System.out.println("Probability of exploration: " + this.limitPortsDoExploration);
			System.out.println("Probability of normal evolution: " + this.limitPortsDoNormalEvolution);
		}
		else if(SPA_guided && entropy_diversity_guided){
			this.updateProbabilityForEachEvolutionStrategy(individuals,fitnessLimit);
			if(entroDvalue > this.entropyGuidedThreshold){
				this.doLocalSearch = true;
				this.limitPortsDoLocalSearch = this.limitPortsDoLocalSearch + reward;
				this.limitPortsDoNormalEvolution = 0.0; //add by Mengxu 2023.07.21
//				this.limitPortsDoNormalEvolution = this.limitPortsDoNormalEvolution + reward; //hide by Mengxu 2023.07.21
			}
			else{
				this.doLocalSearch = false;
				this.limitPortsDoLocalSearch = 0.0;
				this.limitPortsDoExploration = 1.0;
				this.limitPortsDoNormalEvolution = 0.0;
//				this.limitPortsDoExploration = this.limitPortsDoExploration + reward;
//				this.limitPortsDoNormalEvolution = this.limitPortsDoNormalEvolution + reward;
			}
			double newLimitPortsDoLocalSearch = this.limitPortsDoLocalSearch/(this.limitPortsDoLocalSearch + this.limitPortsDoExploration + this.limitPortsDoNormalEvolution);
			double newLimitPortsDoExploration = this.limitPortsDoExploration/(this.limitPortsDoLocalSearch + this.limitPortsDoExploration + this.limitPortsDoNormalEvolution);
			double newLimitPortsDoNormalEvolution = this.limitPortsDoNormalEvolution/(this.limitPortsDoLocalSearch + this.limitPortsDoExploration + this.limitPortsDoNormalEvolution);
			this.limitPortsDoLocalSearch = newLimitPortsDoLocalSearch;
			this.limitPortsDoExploration = newLimitPortsDoExploration;
			this.limitPortsDoNormalEvolution = newLimitPortsDoNormalEvolution;
			this.limitTimesDoLocalSearch = (int)(this.limitPortsDoLocalSearch * individuals.length);
			this.limitTimesDoExploration = (int)(this.limitPortsDoExploration * individuals.length);
			System.out.println("Probability of local search: " + this.limitPortsDoLocalSearch);
			System.out.println("Probability of exploration: " + this.limitPortsDoExploration);
			System.out.println("Probability of normal evolution: " + this.limitPortsDoNormalEvolution);
		}

		//add by mengxu 2023.07.21
		ArrayList<Double> localAndExplorationPro = new ArrayList<>();
		localAndExplorationPro.add(this.limitPortsDoLocalSearch);
		localAndExplorationPro.add(this.limitPortsDoExploration);
		localAndExplorationPro.add(this.limitPortsDoNormalEvolution);
		this.storeGenLocalSearchAndExplorationPro.add(localAndExplorationPro);


		//add by mengxu 2023.06.21, PCA to build the map
		//step 1: divide sequencing decisions and routing decisisons
		double[][] sequencingDecisions = new double[indsCharListsMultiTree[0].length][indsCharListsMultiTree[0][0].length/2];
		double[][] routingDecisions = new double[indsCharListsMultiTree[0].length][indsCharListsMultiTree[0][0].length/2];
		for(int i=0; i<indsCharListsMultiTree[0].length; i++){
			double[] combinedDecisions = indsCharListsMultiTree[0][i];
			for(int j=0; j<combinedDecisions.length; j++){
				if(j<sequencingDecisions[0].length){
					sequencingDecisions[i][j] = combinedDecisions[j];
				}
				else{
					routingDecisions[i][j-sequencingDecisions[0].length] = combinedDecisions[j];
				}
			}
		}

		//add by mengxu 2023.07.11
		this.PCMap.setRecordFitnessNormalisation(this.recordFitnessNormalisation);
		this.PCMap.setzScoreNormalisation(this.usingZScoreNormalisation);
		//todo: a limitation here is that the PCA cannot handle the values that is related to the position of each value, which need to solve 2023.06.21
		if(this.PCMap.isRecordFitnessNormalisation() && !this.PCMap.zScoreNormalisation){
			//min-max normalisation method
			this.PCMap.updateSequencingMapWithNormalisedFitness(sequencingDecisions, fitnessesForModel[0], lowBoundFit, upBoundFit);
			this.PCMap.updateRoutingMapWithNormalisedFitness(routingDecisions, fitnessesForModel[0], lowBoundFit, upBoundFit);
		}
		else if(this.PCMap.isRecordFitnessNormalisation() && this.PCMap.zScoreNormalisation){
			//zScore Normalisation
			double[][] fitnessesForModelNormalised = zScoreSigmoidNormalisation(fitnessesForModel);
			this.PCMap.updateSequencingMap(sequencingDecisions, fitnessesForModelNormalised[0]);
			this.PCMap.updateRoutingMap(routingDecisions, fitnessesForModelNormalised[0]);
		}
		else{
			this.PCMap.updateSequencingMap(sequencingDecisions, fitnessesForModel[0]);
			this.PCMap.updateRoutingMap(routingDecisions, fitnessesForModel[0]);
		}

		this.PCMap.clearSequencingPCInCurrentGeneration();
		this.PCMap.clearRoutingPCInCurrentGeneration();

//		this.PCMap.expandSequencingPCMap(this, 100); //add by mengxu 2023.07.06
//		this.PCMap.expandRoutingPCMap(this,100); //add by mengxu 2023.07.06

//		double sequencingCoverage = this.PCMap.sequencingMapCoverage;
//		double routingCoverage = this.PCMap.routingMapCoverage;
//
//		System.out.println();
//		System.out.println("sequencingCoverage: " + sequencingCoverage);
//		System.out.println("routingCoverage: " + routingCoverage);

		double sequencingExplored = this.PCMap.getNumSequencingExplored();
		double routingExplored = this.PCMap.getNumRoutingExplored();
		this.storeGenSeqAndRoutExplored.get(0).add(sequencingExplored);
		this.storeGenSeqAndRoutExplored.get(1).add(routingExplored);
		System.out.println();
		System.out.println("sequencingExplored: " + sequencingExplored);
		System.out.println("routingExplored: " + routingExplored);

		//test added by mengxu 2023.06.27
		System.out.println("Number of offspring by local search: " + this.timesDoLocalSearch);
		System.out.println("Number of offspring by exploration: " + this.timesDoExploration);
		this.storeGenNumOffByLocalAndExplore.get(0).add(this.timesDoLocalSearch);
		this.storeGenNumOffByLocalAndExplore.get(1).add(this.timesDoExploration);
		this.timesDoLocalSearch = 0;
		this.timesDoExploration = 0;

//		if(this.generation == 5 || this.generation == 10 || this.generation == 20){
//			this.PCMap.printSequencingPCMap();
//			this.PCMap.printRoutingPCMap();
//		}

		if(this.generation > this.generationForDoPCMap){
//			//calculate the fitness limitation based on epsilon (get median twice, this idea is borrowed from the epsilon-LS)
//			int indexBest = PopulationUtils.getIndexOfbestInds(this.population,0);
//			double bestFit = this.population.subpops[0].individuals[indexBest].fitness.fitness();
//			double epsilon = calculateEpsilon(this.population.subpops[0].individuals);
//			double fitnessLimit = bestFit + epsilon;

			//calculate the frequencyForLocalSearch based on epsilon (get median twice, this idea is borrowed from the epsilon-LS)
			double sequencingFrequencyForLocalSearch = this.PCMap.calculateSequencingFrequencyForLocalSearch(false);
			double routingFrequencyForLocalSearch = this.PCMap.calculateRoutingFrequencyForLocalSearch(false);
			if(sequencingFrequencyForLocalSearch < this.frequencyForLocalSearch){ //add by mengxu 2023.07.06
				sequencingFrequencyForLocalSearch = this.frequencyForLocalSearch;
			}
			if(routingFrequencyForLocalSearch < this.frequencyForLocalSearch){ //add by mengxu 2023.07.06
				routingFrequencyForLocalSearch = this.frequencyForLocalSearch;
			}

			//do local search
			if(this.doLocalSearch){
//				this.PCMap.updateSequencingPCsForLocalSearch(this.frequencyForLocalSearch, fitnessLimit, true); //original by giving a fixed value of this.frequencyForLocalSearch
				if(this.PCMap.isRecordFitnessNormalisation() && this.PCMap.zScoreNormalisation){
					fitnessLimit = this.medianZScoreNormalised[0];
				}
				this.PCMap.updateSequencingPCsForLocalSearch(sequencingFrequencyForLocalSearch, fitnessLimit, true);
				if(this.PCMap.sequencingPCRegionForLocalSearch.size()>0){
					System.out.println("this.PCMap.sequencingPCRegionForLocalSearch.size(): " + this.PCMap.sequencingPCRegionForLocalSearch.size());
					this.doSequencingLocalSearch = true;
				}
				else{
					System.out.println("sequencingFrequencyForLocalSearch: " + sequencingFrequencyForLocalSearch);
					System.out.println("fitnessLimit: " + fitnessLimit);
					this.doSequencingLocalSearch = false;
				}
//				this.PCMap.updateRoutingPCsForLocalSearch(this.frequencyForLocalSearch, fitnessLimit,true); //original by giving a fixed value of this.frequencyForLocalSearch
				this.PCMap.updateRoutingPCsForLocalSearch(routingFrequencyForLocalSearch, fitnessLimit,true);
				if(this.PCMap.routingPCRegionForLocalSearch.size()>0){
					System.out.println("this.PCMap.routingPCRegionForLocalSearch.size(): " + this.PCMap.routingPCRegionForLocalSearch.size());
					this.doRoutingLocalSearch = true;
				}
				else{
					System.out.println("routingFrequencyForLocalSearch: " + routingFrequencyForLocalSearch);
					System.out.println("fitnessLimit: " + fitnessLimit);
					this.doRoutingLocalSearch = false;
				}
			}

			//do explore
			if(this.doExploration){
				this.doSequencingExplore = true;
				this.doRoutingExplore = true;
//				this.PCMap.updateSequencingPCsForExplore(this.frequencyForExploration, true);
//				if(this.PCMap.sequencingPCRegionForExplore.size()>0){
//					System.out.println("this.PCMap.sequencingPCRegionForExplore.size(): " + this.PCMap.sequencingPCRegionForExplore.size());
//					this.doSequencingExplore = true;
//				}
//				else{
//					this.doSequencingExplore = false;
//				}
//				this.PCMap.updateRoutingPCsForExplore(this.frequencyForExploration,true);
//				if(this.PCMap.routingPCRegionForExplore.size()>0){
//					System.out.println("this.PCMap.routingPCRegionForExplore.size(): " + this.PCMap.routingPCRegionForExplore.size());
//					this.doRoutingExplore = true;
//				}
//				else{
//					this.doRoutingExplore = false;
//				}
			}
		}


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
			writeSeqAndRoutExploredToFile();//added by mengxu 2023.06.28
			writeNumOffByLocalAndExploreToFile();//added by mengxu 2023.06.28
			writeLocalSearchAndExplorationProToFile();//added by mengxu 2023.07.21
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

	public double[][] zScoreSigmoidNormalisation(double[][] fitnessesForModel){
		int numSubpop = fitnessesForModel.length;
		double[] means = new double[numSubpop];
		double[] stds = new double[numSubpop];
		double[] median = new double[numSubpop];
		double[] medianNormalised = new double[numSubpop];
		this.medianZScoreNormalised = new double[numSubpop];

		List<List<Double>> objectives = new ArrayList<>();
		for(int j=0; j<numSubpop; j++){
			List<Double> obj = new ArrayList<>();
			objectives.add(obj);
			means[j] = 0;
			stds[j] = 0;
		}

		for(int i=0; i<fitnessesForModel.length; i++){
			double[] objectiveValues = fitnessesForModel[i];
			for(int j=0; j<objectiveValues.length; j++){
				if(objectiveValues[j] >= Double.MAX_VALUE || objectiveValues[j] >= Double.POSITIVE_INFINITY){
					continue;
				}else{
					objectives.get(i).add(objectiveValues[j]);
					means[i] += objectiveValues[j];
				}
			}
		}

		for(int j=0; j<fitnessesForModel.length; j++){
			List<Double> obj = objectives.get(j);
			means[j] = means[j]/obj.size();
			obj.sort(Double::compareTo);
			double median_j;
			if(obj.size() % 2 == 0){
				int indexMedian = obj.size() / 2;
				median_j = (obj.get(indexMedian-1) + obj.get(indexMedian))/2;
			}
			else{
				int indexMedian =obj.size() / 2;
				median_j = obj.get(indexMedian);
			}
			median[j] = median_j;
		}
		for(int i=0; i<fitnessesForModel.length; i++){
			double[] objectiveValues = fitnessesForModel[i];
			for(int j=0; j<objectiveValues.length; j++){
				List<Double> obj = objectives.get(i);
				if(objectiveValues[j] >= Double.MAX_VALUE || objectiveValues[j] >= Double.POSITIVE_INFINITY){
					continue;
				}else{
					stds[i] += Math.pow((objectiveValues[j]-means[i]),2);
				}
			}
		}
		for(int i=0; i<fitnessesForModel.length; i++){
			List<Double> obj = objectives.get(i);
			stds[i] = Math.sqrt(stds[i]/obj.size());
		}

		double[][] fitnessesForModelNormalised = new double[numSubpop][fitnessesForModel[0].length];
		for(int i=0; i<fitnessesForModelNormalised.length; i++){
			for(int j=0; j<fitnessesForModelNormalised[i].length; j++){
				double zScore = (fitnessesForModel[i][j]-means[i])/stds[i];
				double norm = sigmoid(zScore);
				fitnessesForModelNormalised[i][j] = norm;
			}
			double zScore_medianNormalised = (median[i]-means[i])/stds[i];
			double normMedian = sigmoid(zScore_medianNormalised);
			medianNormalised[i] = normMedian;
		}
		this.medianZScoreNormalised = medianNormalised;
		return fitnessesForModelNormalised;
	}

	public double sigmoid(double s)
	{
		return 1/(1+Math.exp(-s));
	}

	public void updateProbabilityForEachEvolutionStrategy(Individual[] individuals, double fitnessLimit){
		//update the historical record of local search
		double successCount = 0;
		double failureCount = 0;
		for(int i=0; i<this.offspringIndexByLocalSearch.size(); i++){
			int index = this.offspringIndexByLocalSearch.get(i);
			Individual individual = individuals[index];
			double fitness = individual.fitness.fitness();
			if(fitness <= fitnessLimit){
				successCount = successCount + 1;
			}
			else{
				failureCount = failureCount + 1;
			}
		}
		double[] successAndFailureRate = new double[2];
		successAndFailureRate[0] = successCount/(successCount+failureCount);
		successAndFailureRate[1] = failureCount/(successCount+failureCount);
		if(historicalRecordForLocalSearch.size()>=historicalRecordLength){
			historicalRecordForLocalSearch.remove(0);
		}
		historicalRecordForLocalSearch.add(successAndFailureRate);
		this.offspringIndexByLocalSearch.clear();

		//update the historical record of local search
		successCount = 0;
		failureCount = 0;
		for(int i=0; i<this.offspringIndexByExploration.size(); i++){
			int index = this.offspringIndexByExploration.get(i);
			Individual individual = individuals[index];
			double fitness = individual.fitness.fitness();
			if(fitness <= fitnessLimit){
				successCount = successCount + 1;
			}
			else{
				failureCount = failureCount + 1;
			}
		}
		successAndFailureRate[0] = successCount/(successCount+failureCount);
		successAndFailureRate[1] = failureCount/(successCount+failureCount);
		if(historicalRecordForExploration.size()>=historicalRecordLength){
			historicalRecordForExploration.remove(0);
		}
		historicalRecordForExploration.add(successAndFailureRate);
		this.offspringIndexByExploration.clear();

		//update the historical record of normal evolution
		successCount = 0;
		failureCount = 0;
		for(int i=0; i<this.offspringIndexByNormalEvolution.size(); i++){
			int index = this.offspringIndexByNormalEvolution.get(i);
			Individual individual = individuals[index];
			double fitness = individual.fitness.fitness();
			if(fitness <= fitnessLimit){
				successCount = successCount + 1;
			}
			else{
				failureCount = failureCount + 1;
			}
		}
		successAndFailureRate[0] = successCount/(successCount+failureCount);
		successAndFailureRate[1] = failureCount/(successCount+failureCount);
		if(historicalRecordForNormalEvolution.size()>=historicalRecordLength){
			historicalRecordForNormalEvolution.remove(0);
		}
		historicalRecordForNormalEvolution.add(successAndFailureRate);
		this.offspringIndexByNormalEvolution.clear();

		//update the probability of each type;
		double sum_successCount = 0;
		double sum_failureCount = 0;
		for(int i=0; i<this.historicalRecordForLocalSearch.size(); i++){
			sum_successCount = sum_successCount + historicalRecordForLocalSearch.get(i)[0];
			sum_failureCount = sum_failureCount + historicalRecordForLocalSearch.get(i)[1];
		}
		double successRateForLocalSearch = sum_successCount/(sum_successCount+sum_failureCount);
		if(successRateForLocalSearch/this.limitPortsDoLocalSearch > 1){
			this.limitPortsDoLocalSearch = this.limitPortsDoLocalSearch + this.reward;
		}

		sum_successCount = 0;
		sum_failureCount = 0;
		for(int i=0; i<this.historicalRecordForExploration.size(); i++){
			sum_successCount = sum_successCount + historicalRecordForExploration.get(i)[0];
			sum_failureCount = sum_failureCount + historicalRecordForExploration.get(i)[1];
		}
		double successRateForExploration = sum_successCount/(sum_successCount+sum_failureCount);
		if(successRateForExploration/this.limitPortsDoExploration > 1){
			this.limitPortsDoExploration = this.limitPortsDoExploration + this.reward;
		}

		sum_successCount = 0;
		sum_failureCount = 0;
		for(int i=0; i<this.historicalRecordForNormalEvolution.size(); i++){
			sum_successCount = sum_successCount + historicalRecordForNormalEvolution.get(i)[0];
			sum_failureCount = sum_failureCount + historicalRecordForNormalEvolution.get(i)[1];
		}
		double successRateForNormalEvolution = sum_successCount/(sum_successCount+sum_failureCount);
		if(successRateForNormalEvolution/this.limitPortsDoNormalEvolution > 1){
			this.limitPortsDoNormalEvolution = this.limitPortsDoNormalEvolution + this.reward;
		}

		//normalisation
		double newLimitPortsDoLocalSearch = this.limitPortsDoLocalSearch/(this.limitPortsDoLocalSearch + this.limitPortsDoExploration + this.limitPortsDoNormalEvolution);
		double newLimitPortsDoExploration = this.limitPortsDoExploration/(this.limitPortsDoLocalSearch + this.limitPortsDoExploration + this.limitPortsDoNormalEvolution);
		double newLimitPortsDoNormalEvolution = this.limitPortsDoNormalEvolution/(this.limitPortsDoLocalSearch + this.limitPortsDoExploration + this.limitPortsDoNormalEvolution);
		this.limitPortsDoLocalSearch = newLimitPortsDoLocalSearch;
		this.limitPortsDoExploration = newLimitPortsDoExploration;
		this.limitPortsDoNormalEvolution = newLimitPortsDoNormalEvolution;
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
	public void writeLocalSearchAndExplorationProToFile(){
		File SeqAndRoutExplored = new File("job." + jobSeed + ".LocalSearchAndExplorationPro.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(SeqAndRoutExplored));
			writer.write("Gen, local, exploration, normal");
			writer.newLine();

			for (int gen = 0; gen < this.storeGenLocalSearchAndExplorationPro.size(); gen++) {
				ArrayList<Double> ref = this.storeGenLocalSearchAndExplorationPro.get(gen);
				writer.write(gen + "," + ref.get(0) + "," + ref.get(1) + "," + ref.get(2));
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.2.15 modified by mengxu
	public void writeSeqAndRoutExploredToFile(){
		File SeqAndRoutExplored = new File("job." + jobSeed + ".SeqAndRoutExplored.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(SeqAndRoutExplored));
			writer.write("Gen, sequencing, routing");
			writer.newLine();
			ArrayList<Double> seqExplored = this.storeGenSeqAndRoutExplored.get(0);
			ArrayList<Double> routExplored = this.storeGenSeqAndRoutExplored.get(1);

			for (int ind = 0; ind < seqExplored.size(); ind++) {
				writer.write(ind + "," + seqExplored.get(ind) + "," + routExplored.get(ind));
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//2021.2.15 modified by mengxu
	public void writeNumOffByLocalAndExploreToFile(){
		File NumOffByLocalAndExplore = new File("job." + jobSeed + ".NumOffByLocalAndExplore.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(NumOffByLocalAndExplore));
			writer.write("Gen, local, explore");
			writer.newLine();
			ArrayList<Integer> numOffByLocal = this.storeGenNumOffByLocalAndExplore.get(0);
			ArrayList<Integer> numOffByExplore = this.storeGenNumOffByLocalAndExplore.get(1);

			for (int ind = 0; ind < numOffByLocal.size(); ind++) {
				writer.write(ind + "," + numOffByLocal.get(ind) + "," + numOffByExplore.get(ind));
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
