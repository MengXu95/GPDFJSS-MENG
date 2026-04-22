package mengxu.algorithm.MAPElites.CVTMAPElites;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleProblemForm;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.util.PCspaceRandomGenerate;
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
import java.util.List;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author yimei
 *
 */

public class GPRuleEvolutionStateCVTMAPElites extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();

	public PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];

	public CVTPCmap CVTmap;

//	ArrayList<ArrayList<Double>> storeGenLocalSearchAndExplorationPro = new ArrayList<>(); //added by mengxu 2023.06.28
//	ArrayList<ArrayList<Double>> storeGenSeqAndRoutExplored = new ArrayList<>(); //added by mengxu 2023.06.28
//
//	ArrayList<ArrayList<Integer>> storeGenNumOffByLocalAndExplore = new ArrayList<>(); //added by mengxu 2023.06.28
//
//	ArrayList<double[]> historicalRecordForLocalSearch = new ArrayList<>(); //added by mengxu 2023.07.05
//	ArrayList<double[]> historicalRecordForExploration = new ArrayList<>(); //added by mengxu 2023.07.05
//	ArrayList<double[]> historicalRecordForNormalEvolution = new ArrayList<>(); //added by mengxu 2023.07.05
	public int historicalRecordLength = 10; //added by mengxu 2023.07.05

	public double reward = 0.15;

//	public boolean SPA_guided = true;
	public boolean entropy_diversity_guided = true;

	public boolean use_CVT_map;
	public boolean output_all_elites;
	public double entropyGuidedThreshold;

	public double[] medianZScoreNormalised;

	public double[] medianMinmaxNormalised;

	public double[] medianManuralRuleNormalised;

	public boolean recordFitnessNormalisation;
	public boolean usingZScoreNormalisation;

	public boolean usingManuralRuleNormalisation;

	public boolean parentsOnlyFromCVTMap;
	public boolean parentsFromCVTMapAndPop;

	public String measurement_dis;

	public int clearGap;

	public boolean CVTMapUpdateImmediate;

	public boolean tournamentSizeDynamic;

	//added by mengxu 2023.03.02
	public Individual[] parentsForCrossover = new Individual[2];
	public Individual parentsForMutation;
	public ArrayList<Individual> allIndividualsInCVTMap;

	double[] baselineObjectives;


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

//		phenoCharacterisation[0] =
//				SequencingPhenoCharacterisation.defaultPhenoCharacterisationNew(simulation);
//		phenoCharacterisation[1] =
//				RoutingPhenoCharacterisation.defaultPhenoCharacterisationNew(simulation);

		//generate the PC map, added by mengxu 2023.06.21
		Parameter useCVTMapParam = new Parameter("use-CVT-map");
		this.use_CVT_map = state.parameters.getBoolean(useCVTMapParam, false);

		Parameter CVTMapUpdateImmediateParam = new Parameter("CVT-map-update-immediate");
		this.CVTMapUpdateImmediate = state.parameters.getBoolean(CVTMapUpdateImmediateParam, false);

		Parameter tournamentSizeDynamicParam = new Parameter("tournament-size-dynamic");
		this.tournamentSizeDynamic = state.parameters.getBoolean(tournamentSizeDynamicParam, false);

		Parameter mapSizeParam = new Parameter("map-size");
		int mapSize = state.parameters.getInt(mapSizeParam,null,0);
		Parameter penaltyParam = new Parameter("penalty");
		double penalty = state.parameters.getDouble(penaltyParam,null,1);
		Parameter doMeasureDisParam = new Parameter("measurement-dis");
		this.measurement_dis = state.parameters.getString(doMeasureDisParam, null);
		if(this.use_CVT_map){
			this.CVTmap = new CVTPCmap(mapSize,40, mapSize, penalty, this.measurement_dis);
		}

		Parameter clearGapParam = new Parameter("clear-gap");
		this.clearGap = state.parameters.getInt(clearGapParam,null,1);


//		Parameter doSPAguidedParam = new Parameter("SPA-guided");
//		this.SPA_guided = state.parameters.getBoolean(doSPAguidedParam, false);

		Parameter parentsOnlyFromCVTMapParam = new Parameter("parents-only-from-CVTMap");
		this.parentsOnlyFromCVTMap = state.parameters.getBoolean(parentsOnlyFromCVTMapParam, false);

		Parameter parentsFromCVTMapAndPopParam = new Parameter("parents-from-CVTMap-and-pop");
		this.parentsFromCVTMapAndPop = state.parameters.getBoolean(parentsFromCVTMapAndPopParam, false);

		Parameter doEntropyDiversityGuidedParam = new Parameter("entropy-diversity-guided");
		this.entropy_diversity_guided = state.parameters.getBoolean(doEntropyDiversityGuidedParam, false);

		Parameter entropyGuidedThresholdParam = new Parameter("entropy-guided-threshold");
		this.entropyGuidedThreshold = state.parameters.getDouble(entropyGuidedThresholdParam,null,0);

		//added by mengxu 2023.07.11
		Parameter recordFitnessNormalisationParam = new Parameter("record-fitness-normalisation");
		this.recordFitnessNormalisation = state.parameters.getBoolean(recordFitnessNormalisationParam, false);

		Parameter usingZScoreNormalisationParam = new Parameter("using-zScore-normalisation");
		this.usingZScoreNormalisation = state.parameters.getBoolean(usingZScoreNormalisationParam, false);

		Parameter usingManuralRuleNormalisationParam = new Parameter("using-manural-rule-normalisation");
		this.usingManuralRuleNormalisation = state.parameters.getBoolean(usingManuralRuleNormalisationParam, false);

		Parameter outputAllElitesParam = new Parameter("output-all-elites");
		this.output_all_elites = state.parameters.getBoolean(outputAllElitesParam, false);

	}

    @Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

		if(this.use_CVT_map && generation==0){
			Parameter initialEmptyCVTMapParam = new Parameter("initial-empty-CVT-map");
			boolean initialEmptyCVTMap = this.parameters.getBoolean(initialEmptyCVTMapParam, false);
			Parameter initialCVTMapBasedOnRandomGenotypeParam = new Parameter("initial-CVT-map-based-on-random-genotype");
			boolean initialCVTMapBasedOnRandomGenotype = this.parameters.getBoolean(initialCVTMapBasedOnRandomGenotypeParam, true);
			Parameter initialCVTMapWithNumberParam = new Parameter("initial-CVT-map-with-number");
			int initialCVTMapWithNumber = this.parameters.getInt(initialCVTMapWithNumberParam, null,500);
			if(!initialEmptyCVTMap && !initialCVTMapBasedOnRandomGenotype){
				this.CVTmap.initialiseCVTPCMapDirectlyBasedOnRandomPCs(this);
			}
			else if(!initialEmptyCVTMap && initialCVTMapBasedOnRandomGenotype){
				this.CVTmap.initialiseCVTPCMapDirectlyBasedOnRandomPCsBasedOnRandomGenotype(this, initialCVTMapWithNumber);
			}
		}
	    //System.out.println("generation "+generation);
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

		if(this.use_CVT_map && this.CVTMapUpdateImmediate){
			if(generation==0){
				evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
			}
			else{
				//only evaluate the evaluate the elites that haven't been evaluate before
				int end = this.population.subpops[0].individuals.length;
				int start = end - 10;
				for(int i=start; i<end; i++){
					Individual eliteInd = this.population.subpops[0].individuals[i];
					SimpleProblemForm prob = (SimpleProblemForm) (this.evaluator.p_problem.clone());
					prob.evaluate(state, eliteInd, 0, 0);
				}
			}
		}
		else{
			evaluator.evaluatePopulation(this);
		}

		//added by mengxu 2023.09.25
		if(this.usingManuralRuleNormalisation){
			this.calculateManuralRuleFitness();
		}

		//following is the original 2023.09.25 by mengxu
//	    evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
//		//added by mengxu 2023.09.25
//		if(this.usingManuralRuleNormalisation){
//			this.calculateManuralRuleFitness();
//		}
		//original place
	    statistics.postEvaluationStatistics(this);

		PopulationUtils.sort(this.population);//added by mengxu 2023.08.11
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
		System.out.println("PC diversity: " + pcDvalue);

		if(this.use_CVT_map){//print MAP PC diversity
//			ArrayList<Individual> allindividualsInMAP = this.CVTmap.getAllIndividualsInCVTMap(this);
			ArrayList<double[]> allindividualsPCInMAP = this.CVTmap.getAllIndividualsPCInCVTMap(this);
			if(allindividualsPCInMAP.size()>0){
//				double[][][] indsCharListsMultiTreeInMAP = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisionsIndividuals(this, allindividualsInMAP, phenoCharacterisation, false);
//				PhenotypicCharacteristicDiversity pcDMAP1 = new PhenotypicCharacteristicDiversity();
//				double pcDvalueMAP1 = (double)pcDMAP1.phenotypicCharacteristicDiversity(indsCharListsMultiTreeInMAP[0]) / allindividualsInMAP.size();
//				System.out.println("MAP PC diversity recalculate: " + pcDvalueMAP1);
				//todo: error here, always should be 1
				PhenotypicCharacteristicDiversity pcDMAP2 = new PhenotypicCharacteristicDiversity();
				double pcDvalueMAP2 = (double)pcDMAP2.phenotypicCharacteristicDiversity(allindividualsPCInMAP) / allindividualsPCInMAP.size();
				System.out.println("MAP PC diversity: " + pcDvalueMAP2);
			}
		}

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

		if(this.use_CVT_map){
			//todo: need to update the probability of each type of evolution strategy here!!! 2023.07.05
			//add by mengxu 2023.07.11
			this.CVTmap.setRecordFitnessNormalisation(this.recordFitnessNormalisation);
			this.CVTmap.setzScoreNormalisation(this.usingZScoreNormalisation);
			//todo: a limitation here is that the PCA cannot handle the values that is related to the position of each value, which need to solve 2023.06.21
			if(this.CVTmap.isRecordFitnessNormalisation() && this.usingManuralRuleNormalisation){
				//manural rule Normalisation
				double[][] fitnessesForModelNormalised = manuralRuleNormalisation(fitnessesForModel);
				//update population fitness
				for(int i=0; i<this.population.subpops[0].individuals.length; i++){
					double[] objectives = new double[1];//only suitable for single objective problem
					objectives[0] = fitnessesForModelNormalised[0][i];
//					System.out.println("objectives[0]: " + objectives[0]);
					((MultiObjectiveFitness)this.population.subpops[0].individuals[i].fitness).setObjectives(this,objectives);
				}
//				this.CVTmap.updateCVTMapIndividualsWithPopulation(this, individuals,indsCharListsMultiTree[0],fitnessesForModelNormalised[0],1.0);//modified by mengxu 2023.09.27;
				this.CVTmap.updateCVTMapIndividualsWithPopulation(this, individuals,indsCharListsMultiTree[0],fitnessesForModelNormalised[0],medianManuralRuleNormalised[0]);
			}
			else if(this.CVTmap.isRecordFitnessNormalisation() && !this.CVTmap.zScoreNormalisation && !this.usingManuralRuleNormalisation){
				//min-max normalisation method
				double[][] fitnessesForModelNormalised = minMaxNormalisation(fitnessesForModel, lowBoundFit, upBoundFit);
				//update population fitness
				for(int i=0; i<this.population.subpops[0].individuals.length; i++){
					double[] objectives = new double[1];//only suitable for single objective problem
					objectives[0] = fitnessesForModelNormalised[0][i];
					((MultiObjectiveFitness)this.population.subpops[0].individuals[i].fitness).setObjectives(this,objectives);
				}
				this.CVTmap.updateCVTMapIndividualsWithPopulation(this, individuals,indsCharListsMultiTree[0],fitnessesForModelNormalised[0],medianMinmaxNormalised[0]);
			}
			else if(this.CVTmap.isRecordFitnessNormalisation() && this.CVTmap.zScoreNormalisation && !this.usingManuralRuleNormalisation) {
				//zScore Normalisation
				double[][] fitnessesForModelNormalised = zScoreSigmoidNormalisation(fitnessesForModel);
				//update population fitness
				for (int i = 0; i < this.population.subpops[0].individuals.length; i++) {
					double[] objectives = new double[1];//only suitable for single objective problem
					objectives[0] = fitnessesForModelNormalised[0][i];
					((MultiObjectiveFitness) this.population.subpops[0].individuals[i].fitness).setObjectives(this, objectives);
				}
				this.CVTmap.updateCVTMapIndividualsWithPopulation(this, individuals, indsCharListsMultiTree[0], fitnessesForModelNormalised[0], medianZScoreNormalised[0]);
			}
			else{
				this.CVTmap.updateCVTMapIndividualsWithPopulation(this, individuals,indsCharListsMultiTree[0],fitnessesForModel[0],medianZScoreNormalised[0]);
			}

			this.allIndividualsInCVTMap = this.CVTmap.getAllIndividualsInCVTMap(this);
//			ArrayList<double[]> allFitsInCVTMap = this.CVTmap.getAllIndividualsFitnessInCVTMap(this);
			int numInCVTMap = allIndividualsInCVTMap.size();
			System.out.println("Number in CVT map: " + numInCVTMap);

			if(this.parentsFromCVTMapAndPop){

			}
			else{
				if(this.parentsOnlyFromCVTMap){
					//update the current population by using individuals on the CVTMap, by mengxu 2023.07.26
					int popLength = this.population.subpops[0].individuals.length;
					if(numInCVTMap < popLength){
						PopulationUtils.sort(this.population);
						//todo: might need to remove some repeat individuals?
//						Individual[] newIndividuals = new Individual[popLength];
//						int i=0;
//						for(i=0; i<numInCVTMap; i++){
//							newIndividuals[i] = allIndividualsInCVTMap.get(i);
//						}
//						int j=0;
//						while(i<popLength && j<popLength){
//							Individual candidate = this.population.subpops[0].individuals[j];
//
//						}
						for(int i=popLength-numInCVTMap; i<popLength; i++){
							Individual individual = this.allIndividualsInCVTMap.get(i-(popLength-numInCVTMap));
							this.population.subpops[0].individuals[i] = (Individual)individual.clone();
						}
					}
					else{
						for(int i=0; i<popLength; i++){
							this.population.subpops[0].individuals[i] = (Individual)this.allIndividualsInCVTMap.get(i).clone();
						}
					}
				}
				else{
					//rank all the individuals from current population and CVT-map
					ArrayList<Individual> allIndividuals = new ArrayList<>();
					int popLength = this.population.subpops[0].individuals.length;
					for(int i=0; i<popLength; i++){
						allIndividuals.add(this.population.subpops[0].individuals[i]);
					}
					for(int i=0; i<numInCVTMap; i++){
						Individual individual = (Individual) this.allIndividualsInCVTMap.get(i).clone();
						allIndividuals.add(individual);
					}
					Individual[] allIndividualsSorted = PopulationUtils.sortInds(allIndividuals);
					for(int i=0; i<popLength; i++){
						this.population.subpops[0].individuals[i] = (Individual) allIndividualsSorted[i].clone();
					}
				}
			}
		}

//		statistics.postEvaluationStatistics(this); //move to here by mengxu 2023.10.04


		//double check the fitness by mengxu 2023.07.27
//		int popLength = this.population.subpops[0].individuals.length;
//		for(int i=0; i<popLength; i++){
//			System.out.println("Fitness: " + this.population.subpops[0].individuals[i].fitness.fitness());
//		}


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
			writePCsToFile(this, indsCharListsMultiTree[0], "population"); //output the PCs of population
			if(this.use_CVT_map){
				ArrayList<Individual> allindividualsInMAP = this.CVTmap.getAllIndividualsInCVTMap(this);
				double[][][] indsCharListsMultiTreeInMAP = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisionsIndividuals(this, allindividualsInMAP, phenoCharacterisation, false);
				writePCsToFile(this, indsCharListsMultiTreeInMAP[0], "MAP"); //output the PCs of individuals in MAP
			}
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

		if(this.use_CVT_map && this.CVTMapUpdateImmediate){
			// Generate new instances if needed
			RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
			if (problem.getEvaluationModel().isRotatable()) {
				problem.rotateEvaluationModel();
			}

			// INCREMENT GENERATION AND CHECKPOINT
			generation++;
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

		if(!this.use_CVT_map || !this.CVTMapUpdateImmediate){
			// Generate new instances if needed
			RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
			if (problem.getEvaluationModel().isRotatable()) {
				problem.rotateEvaluationModel();
			}

			// INCREMENT GENERATION AND CHECKPOINT
			generation++;
		}
		//original
	    // Generate new instances if needed
//		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
//	    if (problem.getEvaluationModel().isRotatable()) {
//			problem.rotateEvaluationModel();
//		}
//
//	    // INCREMENT GENERATION AND CHECKPOINT
//	    generation++;
	    if (checkpoint && generation%checkpointModulo == 0)
	        {
	        output.message("Checkpointing");
	        statistics.preCheckpointStatistics(this);
	        Checkpoint.setCheckpoint(this);
	        statistics.postCheckpointStatistics(this);
	        }

	    return R_NOTDONE;
	}

	public void calculateManuralRuleFitness(){
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

//		baselineInd.trees[0].
//		SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
//		prob.evaluate(state, individual, 0, 0);
		//for test
//		System.out.println("Individual fitness: "+ individual.fitness.fitness());


	}

	public double[][] manuralRuleNormalisation(double[][] fitnessesForModel){
		int numSubpop = fitnessesForModel.length;
		double[] medianNormalised = new double[numSubpop];
		this.medianManuralRuleNormalised = new double[numSubpop];

		List<List<Double>> objectives = new ArrayList<>();
		for(int j=0; j<numSubpop; j++){
			List<Double> obj = new ArrayList<>();
			objectives.add(obj);
		}

		for(int i=0; i<fitnessesForModel.length; i++){
			double[] objectiveValues = fitnessesForModel[i];
			for(int j=0; j<objectiveValues.length; j++){
				if(objectiveValues[j] >= Double.MAX_VALUE || objectiveValues[j] >= Double.POSITIVE_INFINITY){
					continue;
				}else{
//					System.out.println("objectiveValues.length: " + objectiveValues.length);
//					System.out.println("this.baselineObjectives.length: " + this.baselineObjectives.length);
					double normalisedFit = objectiveValues[j] / this.baselineObjectives[i];
					fitnessesForModel[i][j] = normalisedFit;
					objectives.get(i).add(normalisedFit);
				}
			}
		}

		for(int j=0; j<numSubpop; j++){
			List<Double> obj = objectives.get(j);
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
			medianNormalised[j] = median_j;
			//add by mengxu 2023.09.27
			if(medianNormalised[j] > 1.0){
				medianNormalised[j] = 1.0;
			}
		}
		this.medianManuralRuleNormalised = medianNormalised;
		return fitnessesForModel;
	}

	public double[][] minMaxNormalisation(double[][] fitnessesForModel, double[] lowBoundFit, double[] upBoundFit){
		int numSubpop = fitnessesForModel.length;
		double[] medianNormalised = new double[numSubpop];
		this.medianMinmaxNormalised = new double[numSubpop];

		List<List<Double>> objectives = new ArrayList<>();
		for(int j=0; j<numSubpop; j++){
			List<Double> obj = new ArrayList<>();
			objectives.add(obj);
		}

		for(int i=0; i<fitnessesForModel.length; i++){
			double[] objectiveValues = fitnessesForModel[i];
			for(int j=0; j<objectiveValues.length; j++){
				if(objectiveValues[j] >= Double.MAX_VALUE || objectiveValues[j] >= Double.POSITIVE_INFINITY){
					continue;
				}else{
					double normalisedFit = (objectiveValues[j] - lowBoundFit[i])/(upBoundFit[i] - lowBoundFit[i]);
					fitnessesForModel[i][j] = normalisedFit;
					objectives.get(i).add(normalisedFit);
				}
			}
		}

		for(int j=0; j<numSubpop; j++){
			List<Double> obj = objectives.get(j);
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
			medianNormalised[j] = median_j;
		}

		this.medianMinmaxNormalised = medianNormalised;
		return fitnessesForModel;
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
//			System.out.println("means[i]: " + means[i]);
//			System.out.println("stds[i]: " + stds[i]);
			for(int j=0; j<fitnessesForModelNormalised[i].length; j++){
				if(fitnessesForModel[i][j] >= Double.MAX_VALUE || fitnessesForModel[i][j] >= Double.POSITIVE_INFINITY){
					fitnessesForModelNormalised[i][j] = Double.MAX_VALUE;
				}else{
					double zScore = (fitnessesForModel[i][j]-means[i])/stds[i];
					double norm = sigmoid(zScore);
//					System.out.println("zScore: " + zScore);
//					System.out.println("norm: " + norm);
					fitnessesForModelNormalised[i][j] = norm;
				}
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
