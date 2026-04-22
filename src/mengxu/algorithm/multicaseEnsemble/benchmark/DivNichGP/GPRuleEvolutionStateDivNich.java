package mengxu.algorithm.multicaseEnsemble.benchmark.DivNichGP;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.lexicaseselection.ParentFairForCrossover;
import mengxu.algorithm.multicaseEnsemble.ensembleContribution.OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution;
import mengxu.cluster.CustomerPoint;
import mengxu.complexsimulation.HeterogeneousSimulation;
import mengxu.niching.ClearingMultiObjectiveFitness;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.jobshop.FlexibleStaticInstance;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.StaticSimulation;

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

public class GPRuleEvolutionStateDivNich extends GPRuleEvolutionState {


	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();
	public List<List<CustomerPoint>> cluster = new ArrayList<>();
	public List<Individual> ensemble = new ArrayList<>();
	public List<List<Individual>> ensembleAll = new ArrayList<>();

	public List<Integer> parentIndex = new ArrayList<>();
	public List<List<Integer>> allGenerationParentIndex = new ArrayList<>();

	public List<Double> realUtilLevels = new ArrayList<>();

	public List<ParentFairForCrossover> parentsIndexPairForCrossover = new ArrayList<>();
	public List<Double> combinationNumber = new ArrayList<>();

	public EnsembleRule ensembleRule = null;

	//add by mengxu 2022.01.17
	public double[][][] indsCharListsMultiTreeGen;
	public double[][] fitnessesForModelGen;

	public boolean printEnsemble = false;

	public static final String P_LIMIT_TOTAL_EVALUATION_TIME = "limit-total-evaluation-times";
	public int limitTotalEvaluationTimes;


	@Override
	public void setup(EvolutionState state, Parameter base) {

		super.setup(this, base);
		this.limitTotalEvaluationTimes = state.parameters.getInt(new Parameter(P_LIMIT_TOTAL_EVALUATION_TIME), null, -1);

	}

	public List<List<CustomerPoint>> getCluster() {
		return cluster;
	}

	public List<Individual> getEnsemble() {
		return ensemble;
	}

	@Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

	    //System.out.println("generation "+generation);
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

	    evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
//	    statistics.postEvaluationStatistics(this); //log the best individual

		//todo: implement the greedy ensemble 2023.03.30
//		if(generation == numGenerations-1){
//			EnsembleRule ensembleRule = greedyEnsemble();
//			this.ensembleRule = ensembleRule;
//		}


//		double bestFitness = Double.MAX_VALUE;
//		int bestIndex = 0;
//		for(int i=0;i<this.population.subpops[0].individuals.length;i++){
//			if(this.population.subpops[0].individuals[i].fitness.fitness()<bestFitness){
//				bestFitness = this.population.subpops[0].individuals[i].fitness.fitness();
//				bestIndex = i;
//			}
//		}
//		System.out.println("bestFitness: " + bestFitness);
//		System.out.println("bestFitness/baselineFitness: " + bestFitness/baselineFitness[generation]);

		System.out.println("Measure diversity!");

		//modified by mengxu. Measure diversity. 2021.04.15-------------------------
		Individual[] individuals = this.population.subpops[0].individuals;
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


		//-------------------------------------------------------------------
		//modified by mengxu 2021.07.09
		//calculate the pc of each individual from current simulation in each subpop and do cluster--------------
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


		//2021.11.05 used to check if the work shop is busy!======================
//		Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);
//		PopulationUtils.sort(population);
//		Individual bestInd = population.subpops[0].individuals[0];
//		GPRule sequencingRule = new GPRule(RuleType.SEQUENCING, ((GPIndividual) bestInd).trees[0]);
//		GPRule routingRule = new GPRule(RuleType.ROUTING, ((GPIndividual) bestInd).trees[1]);
//		simulation.setSequencingRule(sequencingRule); //indicate different individuals
//		simulation.setRoutingRule(routingRule);
//		//System.out.println(simulation);
//		simulation.run();
//		System.out.println("realUtilLevel: " + simulation.realUtilLevel());
//		realUtilLevels.add(simulation.realUtilLevel());
		//========================================================================

		//==========================================hide by mengxu 2023.02.07========================================
		//note: the following PC calculate method is not suitable for the HeterogeneousSimulation with MAX arrival time
//		//calculate the pc of each individual in each subpop and do cluster--------------
//		PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
//		//dynamic simulation
//		phenoCharacterisation[0] =
//					SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
//		phenoCharacterisation[1] =
//					RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
//		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, true); //3. calculate the phenotypic characteristic
//		//get the fitness for training model
//		double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];
//		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
//			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
//				fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
//			}
//		}
//		this.indsCharListsMultiTreeGen = indsCharListsMultiTree;
//		this.fitnessesForModelGen = fitnessesForModel;
//		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
//		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
//		System.out.println("PC diversity: " + pcDvalue);
		//==========================================hide by mengxu 2023.02.07========================================

		if(generation != 0){
			List<Integer> parentIndexCopy = new ArrayList<>(parentIndex);
			allGenerationParentIndex.add(parentIndexCopy);
		}
		ParentIndexDiversity parentIndexD = new ParentIndexDiversity();
		double parentIndexDvalue = (double)parentIndexD.parentIndexDiversity(parentIndex) / individuals.length;
//		System.out.println("ParentIndex diversity: " + parentIndexDvalue);
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
		diversities.add(0.0);
//		diversities.add(pcDvalue);
		diversities.add(parentIndexDvalue);
		storeGenDiversities.add(diversities);

		int badRun = 0;

		for(int i=0; i < population.subpops[0].individuals.length; i++){
			if(population.subpops[0].individuals[i].fitness.fitness() >= Double.POSITIVE_INFINITY ||
					population.subpops[0].individuals[i].fitness.fitness() >= Double.MAX_VALUE){
				badRun ++;
			}
		}
		System.out.println("Generation " + generation + " bad run: " + badRun);


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

		if(this.limitTotalEvaluationTimes - totalEvaluationTime >=0 && this.limitTotalEvaluationTimes - totalEvaluationTime <= population.subpops[0].individuals.length){
			EnsembleRule ensembleRule = greedyEnsemble();
			this.ensembleRule = ensembleRule;
			writeDiversityToFile();//modified by mengxu	2021.04.15
//			writeSelectParentIndexToFile();
			writeCombinationNumberToFile();
			this.printEnsemble = true;
//			statistics.postEvaluationStatistics(this); //log the best individual
			return R_FAILURE;
		}
		else if(totalTime >= expectTrainingTime/1.5){
			EnsembleRule ensembleRule = greedyEnsemble();
			this.ensembleRule = ensembleRule;
			writeDiversityToFile();//modified by mengxu	2021.04.15
//			writeSelectParentIndexToFile();
			writeCombinationNumberToFile();
			this.printEnsemble = true;
//			statistics.postEvaluationStatistics(this); //log the best individual
			return R_FAILURE;
		}
		else if(generation == numGenerations-1){
			System.out.println("Enter from here!");
			EnsembleRule ensembleRule = greedyEnsemble();
			this.ensembleRule = ensembleRule;
			writeDiversityToFile();//modified by mengxu	2021.04.15
//			writeSelectParentIndexToFile();
			writeCombinationNumberToFile();
			this.printEnsemble = true;
//			statistics.postEvaluationStatistics(this); //log the best individual
			return R_FAILURE;
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
//			writeSelectParentIndexToFile();
			writeCombinationNumberToFile();

//			EnsembleRule ensembleRule = greedyEnsemble();
//			writeRealUtilLevelToFile();
//			writeEnsembleToFile();//modified by mengxu 2021.05.08
			return R_FAILURE;
	        }

//		// SHOULD WE QUIT?
//		if (totalEvaluationTime >= 250)
//		{
//			generation++; // in this way, the last generation value will be printed properly.  fzhang 28.3.2018
//			writeDiversityToFile();//modified by mengxu	2021.04.15
////			writeSelectParentIndexToFile();
//			writeCombinationNumberToFile();
//
////			EnsembleRule ensembleRule = greedyEnsemble();
////			writeRealUtilLevelToFile();
////			writeEnsembleToFile();//modified by mengxu 2021.05.08
//			return R_FAILURE;
//		}

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

	public EnsembleRule greedyEnsemble(){
		int evaTimes = 0;
		List<Individual> ensemble = new ArrayList<>();
		List<Integer> elementOriginalIndex = new ArrayList<>();
		int ensembleSize = 0;
		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
		List<Objective> objectives = problem.getObjectives();

		PopulationUtils.sort(population);
		Individual[] individuals = population.subpops[0].individuals;

		//modified by mengxu 2023.03.31
		PhenoCharacterisation[] phenoCharacterisationFinalGen = new PhenoCharacterisation[2];
		Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)this.evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);
		phenoCharacterisationFinalGen[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		phenoCharacterisationFinalGen[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisationFinalGen, true); //3. calculate the phenotypic characteristic

		//generate a validation set
		boolean sameWithTrainingSet = this.parameters.getBoolean(new Parameter("validation-set-same-with-training-set"), null, false);;
		int numRepInTrainingSet = this.parameters.getInt(new Parameter("num-instance-in-validation-set"), null, -1);;
		int ensembleSizeLimit = this.parameters.getInt(new Parameter("ensemble-size-limit"), null, -1);;
		SchedulingSet schedulingSet = getValidationSet(sameWithTrainingSet, numRepInTrainingSet);
		double threshold = 0;//todo: remain to be changed
		double bestFitness = Double.POSITIVE_INFINITY;
		EnsembleRule bestEnsembleRule = null;

		for(int i=0; i<individuals.length; i++){
			Individual individual = individuals[i];
			boolean inNiche = false;
			if(bestEnsembleRule != null){
				for(int j=0; j<bestEnsembleRule.getEnsemble().size(); j++){
					//todo:
					double[] pc_i = indsCharListsMultiTree[0][i];
					double[] pc_j = indsCharListsMultiTree[0][bestEnsembleRule.getElementOriginalIndex().get(j)];
					if(distance(pc_i,pc_j) <= threshold){
						inNiche = true;
						break;
					}
				}
			}

			if(!inNiche){
				ensemble.add(individual);
				elementOriginalIndex.add(i);
				ensembleSize = ensemble.size();
//				System.out.println("ensembleSize 0: " + ensembleSize);
				EnsembleRule ensembleRule = new EnsembleRule(ensemble,elementOriginalIndex,ensembleSize);
				//todo: evaluate ensemble on validation set
				ClearingMultiObjectiveFitness ensembleFitness = new ClearingMultiObjectiveFitness();
//				ensembleFitness.setCurrentObjective(objectives);
				//todo: need to double check 2023.03.07
				evaTimes += ensembleRule.getEnsemble().size() * 10;
				long start = System.currentTimeMillis();//yimei.util.Timer.getCpuTime();
				ensembleRule.calcFitnessByVotingEnsemble(ensembleFitness, this, schedulingSet, objectives);
				long finish =System.currentTimeMillis();// yimei.util.Timer.getCpuTime();
				double duration = (finish - start) / 1000.0;//000000;
				totalTime += duration;
				if(ensembleRule.getFitness().fitness() < bestFitness){
					bestFitness = ensembleRule.getFitness().fitness();
					bestEnsembleRule = ensembleRule;
				}
				else{
					ensemble.remove(individual);
					elementOriginalIndex.remove(ensembleSize-1);
				}
				totalEvaluationTime += ensembleRule.getEnsemble().size() * 10;
				//Save statisticas
				if(Math.floorDiv(totalEvaluationTime,1000) > genTimes.size())
				{
					System.out.println("totalEvaluationTime: " + totalEvaluationTime);
					double durationTime = totalTime-sumGenTimes.get(sumGenTimes.size()-1);
					genTimes.add(durationTime);
					sumGenTimes.add(totalTime);
					System.out.println("durationTime: " + durationTime);
					this.ensembleRule = bestEnsembleRule;
					this.printEnsemble = true;
					statistics.postEvaluationStatistics(this);
					// SHOULD WE QUIT?
					if (totalTime >= expectTrainingTime)
					{
						System.out.println("checked index: " + i);
//					writeDiversityToFile();//modified by mengxu	2021.04.15
//					writeCombinationNumberToFile();
						this.ensembleRule = bestEnsembleRule;
						return bestEnsembleRule;
					}
				}
				// SHOULD WE QUIT?
				if(totalEvaluationTime >= this.limitTotalEvaluationTimes)
				{
//					writeDiversityToFile();//modified by mengxu	2021.04.15
//					writeCombinationNumberToFile();
					this.ensembleRule = bestEnsembleRule;
					return bestEnsembleRule;
				}

			}

//			System.out.println("ensembleSize 1: " + bestEnsembleRule.getEnsembleSize());
//			if(bestEnsembleRule.getEnsembleSize() >= ensembleSizeLimit){
//				return bestEnsembleRule;
//			}
		}
		System.out.println("Evaluation times of greedy selection: " + evaTimes);
		return bestEnsembleRule;
	}

	public double distance(double[] charList1, double[] charList2) {
		double distance = 0.0;
//		System.out.println("\ncharList1: " + charList1.length);
		for (int i = 0; i < charList1.length; i++) {
//			System.out.println("charList1[i]: " + charList1[i]);
//			System.out.println("charList2[i]: " + charList1[i]);
			double diff = charList1[i] - charList2[i];
			distance += diff * diff;
		}
//		System.out.println("dis: " + Math.sqrt(distance));
		return Math.sqrt(distance);
	}

	public SchedulingSet getValidationSet(boolean sameWithTrainingSet, int numRep){
		int numSimModels = 1;
		List<Simulation> trainSimulations = new ArrayList<>();
		List<Integer> replications = new ArrayList<>();
		for (int x = 0; x < numSimModels; x++) {
			// Number of machines
			int numMachines = 10;
			// Number of jobs
			int numJobs = 5000;
			// Number of warmup jobs
			int warmupJobs = 1000;
			// Min number of operations
			int minNumOperations = 1;
			// Max number of operations
			int maxNumOperations = numMachines;
			// Utilization level
			double utilLevel = this.parameters.getDoubleWithDefault(new Parameter("eval.problem.eval-model.sim-models.0.util-level"), null, 0.85);
			// Due date factor
			double dueDateFactor = this.parameters.getDoubleWithDefault(new Parameter("eval.problem.eval-model.sim-models.0.due-date-factor"), null, 1.5);
			// Number of replications
			int rep = numRep;
			//Dynamic Heterogeneous Simulation
			long simSeed = 77777; //todo:double check
			if(sameWithTrainingSet){
				simSeed = 0;
			}
			Simulation simulation = new HeterogeneousSimulation(simSeed,
					null, null, numMachines, numJobs, warmupJobs,
					minNumOperations, maxNumOperations,
					utilLevel, dueDateFactor, false);
			trainSimulations.add(simulation);
			replications.add(new Integer(rep));
		}

		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
		List<Objective> objectives = problem.getObjectives();
		SchedulingSet schedulingSet = new SchedulingSet(trainSimulations, replications, objectives);
		return schedulingSet;
	}

	public List<CustomerPoint> buildCustomerPoint(double[][] subpopIndsCharListsMultiTree, double[] subpopFitnessesForModel){
		List<CustomerPoint> csAll = new ArrayList<>();
		for(int indIndex=0; indIndex<subpopIndsCharListsMultiTree.length; indIndex++){
			CustomerPoint cp = new CustomerPoint(indIndex,subpopIndsCharListsMultiTree[indIndex], subpopFitnessesForModel[indIndex]);
			csAll.add(cp);
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

	//2021.11.04 modified by mengxu
	public void writeRealUtilLevelToFile(){
		File diversities = new File("job." + jobSeed + ".realUtilLevels.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Gen, utilLevel");
			writer.newLine();
			for (int ind = 0; ind < realUtilLevels.size(); ind++) {
				writer.write(ind + "," + realUtilLevels.get(ind));
				writer.newLine();
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

	//2021.7.21 modified by mengxu, to store the selected parent index of each generation
	public void writeSelectParentIndexToFile(){
		File selectParentIndex = new File("job." + jobSeed + ".selectParentIndex.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(selectParentIndex));
			for (int gen = 0; gen < allGenerationParentIndex.size(); gen++) {
				List<Integer> ref = allGenerationParentIndex.get(gen);
				writer.write((gen+1) + ",");
				for(int i=0; i< ref.size()-1; i++){
					if(i==ref.size()-2){
						writer.write(ref.get(i) + "," + ref.get(i+1));
					}
					else{
						writer.write(ref.get(i) + ",");
					}
				}
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
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
	
}
