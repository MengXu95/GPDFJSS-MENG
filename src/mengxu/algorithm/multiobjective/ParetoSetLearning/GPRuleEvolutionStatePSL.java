package mengxu.algorithm.multiobjective.ParetoSetLearning;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Checkpoint;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.multiobjective.utils.Archive;
import mengxu.cluster.CustomerPoint;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import yimei.jss.simulation.DecisionSituation;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.state.SystemState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author yimei
 *
 */

public class GPRuleEvolutionStatePSL extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();
	public List<List<CustomerPoint>> cluster = new ArrayList<>();

	public List<Integer> parentIndex = new ArrayList<>();
	public List<List<Integer>> allGenerationParentIndex = new ArrayList<>();
	public double[] minFitnessGen;
	public double[] maxFitnessGen;

	//add 2021.11.3
	public List<double[]> objective0 = new ArrayList<>();
	public List<double[]> objective1 = new ArrayList<>();

	public Archive externalArchive = new Archive(); //add by mengxu 2022.09.02
	public PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];

	public int printGap;

	public int terminalNormalisation;

	public Map<String,Double> maxTerminals = new HashMap<String,Double>();

	public int evaluatePreferenceIndex;
	public int evaluateOrder;

	public boolean doNichingClear;

	public int HVEstimateStrategy;

	public int outputTopN;

	public boolean topNFromParetoFront;

	public boolean preferenceTerminalHighPro;

	public boolean useBroodRecombination;


	@Override
	public void setup(EvolutionState state, Parameter base) {

		super.setup(this, base);

		Parameter printGapParam = new Parameter("print-gap"); //add by mengxu 2023.01.24
		printGap = state.parameters.getInt(printGapParam, null); //add by mengxu 2023.01.24

		Parameter doNichingClearParam = new Parameter("do-niching-clear"); //add by mengxu 2023.01.24
		this.doNichingClear = state.parameters.getBoolean(doNichingClearParam, true); //add by mengxu 2023.01.24

		Parameter outputTopNParam = new Parameter("output-top-N"); //add by mengxu 2023.01.24
		this.outputTopN = state.parameters.getInt(outputTopNParam, null,1); //add by mengxu 2023.01.24

		Parameter topNFromParetoFrontParam = new Parameter("topN-from-pareto-front"); //add by mengxu 2023.01.24
		this.topNFromParetoFront = state.parameters.getBoolean(topNFromParetoFrontParam, true); //add by mengxu 2023.01.24

		//add by mengxu 2024.4.18
		Parameter terminalNormalisationParam = new Parameter("terminal-normalisation"); //add by mengxu 2023.01.24
		this.terminalNormalisation = state.parameters.getIntWithDefault(terminalNormalisationParam, null,2); //add by mengxu 2023.01.24

		//add by mengxu 2024.5.24
		Parameter HVEstimateStrategyParam = new Parameter("HV-estimated-strategy"); //add by mengxu 2023.01.24
		this.HVEstimateStrategy = state.parameters.getIntWithDefault(HVEstimateStrategyParam, null,1); //add by mengxu 2023.01.24

		Parameter preferenceTerminalHighProParam = new Parameter("preference-terminal-high-pro"); //add by mengxu 2023.01.24
		preferenceTerminalHighPro = state.parameters.getBoolean(preferenceTerminalHighProParam, false); //add by mengxu 2023.01.24

		Parameter useBroodRecombinationParam = new Parameter("use-brood-recombination"); //add by mengxu 2023.01.24
		this.useBroodRecombination = state.parameters.getBoolean(useBroodRecombinationParam, null,false); //add by mengxu 2024.09.26

		//calculate the pc of each individual in each subpop and do cluster--------------
		this.phenoCharacterisation = new PhenoCharacterisation[2];
		//dynamic simulation
		phenoCharacterisation[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation(this,20);
		phenoCharacterisation[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation(this,20);

		initialMaxTerminals();

	}

	public void initialMaxTerminals(){
		this.maxTerminals.put("NUM_OPS_IN_QUEUE", -1.0);
		this.maxTerminals.put("WORK_IN_QUEUE", -1.0);
		this.maxTerminals.put("MACHINE_WAITING_TIME", -1.0);
		this.maxTerminals.put("PROC_TIME", -1.0);
		this.maxTerminals.put("NEXT_PROC_TIME", -1.0);
		this.maxTerminals.put("OP_WAITING_TIME", -1.0);
		this.maxTerminals.put("WORK_REMAINING", -1.0);
		this.maxTerminals.put("NUM_OPS_REMAINING", -1.0);
		this.maxTerminals.put("WEIGHT", -1.0);
		this.maxTerminals.put("TIME_IN_SYSTEM", -1.0);
		this.maxTerminals.put("RELATIVE_DUE_DATE", -1.0);
		this.maxTerminals.put("SLACK", -1.0);
		this.maxTerminals.put("TRANSFER_TIME", -1.0);
	}

	public void updateMaxTerminals(DecisionSituation decisionSituation){
		if(decisionSituation instanceof SequencingDecisionSituation){
			List<OperationOption> queue = decisionSituation.getQueue();
			WorkCenter workCenter = ((SequencingDecisionSituation) decisionSituation).getWorkCenter();
			SystemState systemState = decisionSituation.getSystemState();
			double maxNumOpsInQueue = workCenter.getQueue().size();
			double maxWorkInQueue = workCenter.getWorkInQueue();
			double maxMachineWaitingTime = systemState.getClockTime() - workCenter.getReadyTime();
			double maxProcTime = -1;
			double maxNextProcTime = -1;
			double maxOpWaitingTime = -1;
			double maxWorkRemaining = -1;
			double maxNumOpsRemaining = -1;
			double maxWeight = -1;
			double maxTimeInSystem = -1;
			double maxRelativeDueDate = -1;
			double maxSlack = -1;
			double maxTransferTime = -1;
			for(OperationOption op:queue){
				if(op.getProcTime() > maxProcTime){
					maxProcTime = op.getProcTime();
				}
				if(op.getNextProcTime() > maxNextProcTime){
					maxNextProcTime = op.getNextProcTime();
				}
				double opWaitingTime = systemState.getClockTime() - op.getReadyTime();
				if(opWaitingTime > maxOpWaitingTime){
					maxOpWaitingTime = opWaitingTime;
				}
				if(op.getWorkRemaining() > maxWorkRemaining){
					maxWorkRemaining = op.getWorkRemaining();
				}
				if(op.getNumOpsRemaining() > maxNumOpsRemaining){
					maxNumOpsRemaining = op.getNumOpsRemaining();
				}
				if(op.getJob().getWeight() > maxWeight){
					maxWeight = op.getJob().getWeight();
				}
				double timeInSystem = systemState.getClockTime() - op.getJob().getReleaseTime();
				if(timeInSystem > maxTimeInSystem){
					maxTimeInSystem = timeInSystem;
				}
				double relativeDueDate = op.getJob().getDueDate() - systemState.getClockTime();
				if(relativeDueDate > maxRelativeDueDate){
					maxRelativeDueDate = relativeDueDate;
				}
				double slack = op.getJob().getDueDate() - systemState.getClockTime() - op.getWorkRemaining();
				if(slack > maxSlack){
					maxSlack = slack;
				}
				//transfer time
				int opID = op.getOperation().getId();
				double value = -1;
				if(opID==0){
					value = ((HeterogeneousSimulation)systemState.getSimulation()).getEntryWorkCenterTranserTime(workCenter.getId());
				}
				else if(opID==op.getJob().getOperations().size()-1){
					int fromWorkCenterID = op.getJob().getOperation(opID-1).getSelectedWorkCenter().getId();
					double transferTime = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterTranserTime(fromWorkCenterID, workCenter.getId());
					double downloadTime = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterExitTranserTime(workCenter.getId());
					value = transferTime + downloadTime;
				}
				else{
					int fromWorkCenterID = op.getJob().getOperation(opID-1).getSelectedWorkCenter().getId();
					value = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterTranserTime(fromWorkCenterID, workCenter.getId());
				}
				if(value > maxTransferTime){
					maxTransferTime = value;
				}
			}
			this.maxTerminals.replace("NUM_OPS_IN_QUEUE", maxNumOpsInQueue);
			this.maxTerminals.replace("WORK_IN_QUEUE", maxWorkInQueue);
			this.maxTerminals.replace("MACHINE_WAITING_TIME", maxMachineWaitingTime);
			this.maxTerminals.replace("PROC_TIME", maxProcTime);
			this.maxTerminals.replace("NEXT_PROC_TIME", maxNextProcTime);
			this.maxTerminals.replace("OP_WAITING_TIME", maxOpWaitingTime);
			this.maxTerminals.replace("WORK_REMAINING", maxWorkRemaining);
			this.maxTerminals.replace("NUM_OPS_REMAINING", maxNumOpsRemaining);
			this.maxTerminals.replace("WEIGHT", maxWeight);
			this.maxTerminals.replace("TIME_IN_SYSTEM", maxTimeInSystem);
			this.maxTerminals.replace("RELATIVE_DUE_DATE", maxRelativeDueDate);
			this.maxTerminals.replace("SLACK", maxSlack);
			this.maxTerminals.replace("TRANSFER_TIME", maxTransferTime);
		}
		else if(decisionSituation instanceof RoutingDecisionSituation){
			List<OperationOption> queue = decisionSituation.getQueue();
//			WorkCenter workCenter = ((RoutingDecisionSituation) decisionSituation).getWorkCenter();
			SystemState systemState = decisionSituation.getSystemState();
			double maxNumOpsInQueue = -1;
			double maxWorkInQueue = -1;
			double maxMachineWaitingTime = -1;
			double maxProcTime = -1;
			double maxNextProcTime = -1;
			double maxOpWaitingTime = -1;
			double maxWorkRemaining = -1;
			double maxNumOpsRemaining = -1;
			double maxWeight = -1;
			double maxTimeInSystem = -1;
			double maxRelativeDueDate = -1;
			double maxSlack = -1;
			double maxTransferTime = -1;
			for(OperationOption op:queue){
				WorkCenter workCenter = op.getWorkCenter();
				double numOpsInQueue = workCenter.getQueue().size();
				if(numOpsInQueue > maxNumOpsInQueue){
					maxNumOpsInQueue = numOpsInQueue;
				}
				double workInQueue = workCenter.getWorkInQueue();
				if(workInQueue > maxWorkInQueue){
					maxWorkInQueue = workInQueue;
				}
				double machineWaitingTime = systemState.getClockTime() - workCenter.getReadyTime();
				if(machineWaitingTime > maxMachineWaitingTime){
					maxMachineWaitingTime = machineWaitingTime;
				}
				if(op.getProcTime() > maxProcTime){
					maxProcTime = op.getProcTime();
				}
				if(op.getNextProcTime() > maxNextProcTime){
					maxNextProcTime = op.getNextProcTime();
				}
				double opWaitingTime = systemState.getClockTime() - op.getReadyTime();
				if(opWaitingTime > maxOpWaitingTime){
					maxOpWaitingTime = opWaitingTime;
				}
				if(op.getWorkRemaining() > maxWorkRemaining){
					maxWorkRemaining = op.getWorkRemaining();
				}
				if(op.getNumOpsRemaining() > maxNumOpsRemaining){
					maxNumOpsRemaining = op.getNumOpsRemaining();
				}
				if(op.getJob().getWeight() > maxWeight){
					maxWeight = op.getJob().getWeight();
				}
				double timeInSystem = systemState.getClockTime() - op.getJob().getReleaseTime();
				if(timeInSystem > maxTimeInSystem){
					maxTimeInSystem = timeInSystem;
				}
				double relativeDueDate = op.getJob().getDueDate() - systemState.getClockTime();
				if(relativeDueDate > maxRelativeDueDate){
					maxRelativeDueDate = relativeDueDate;
				}
				double slack = op.getJob().getDueDate() - systemState.getClockTime() - op.getWorkRemaining();
				if(slack > maxSlack){
					maxSlack = slack;
				}
				//transfer time
				int opID = op.getOperation().getId();
				double value = -1;
				if(opID==0){
					value = ((HeterogeneousSimulation)systemState.getSimulation()).getEntryWorkCenterTranserTime(workCenter.getId());
				}
				else if(opID==op.getJob().getOperations().size()-1){
					int fromWorkCenterID = op.getJob().getOperation(opID-1).getSelectedWorkCenter().getId();
					double transferTime = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterTranserTime(fromWorkCenterID, workCenter.getId());
					double downloadTime = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterExitTranserTime(workCenter.getId());
					value = transferTime + downloadTime;
				}
				else{
					int fromWorkCenterID = op.getJob().getOperation(opID-1).getSelectedWorkCenter().getId();
					value = ((HeterogeneousSimulation)systemState.getSimulation()).getWorkCenterTranserTime(fromWorkCenterID, workCenter.getId());
				}
				if(value > maxTransferTime){
					maxTransferTime = value;
				}
			}
			this.maxTerminals.replace("NUM_OPS_IN_QUEUE", maxNumOpsInQueue);
			this.maxTerminals.replace("WORK_IN_QUEUE", maxWorkInQueue);
			this.maxTerminals.replace("MACHINE_WAITING_TIME", maxMachineWaitingTime);
			this.maxTerminals.replace("PROC_TIME", maxProcTime);
			this.maxTerminals.replace("NEXT_PROC_TIME", maxNextProcTime);
			this.maxTerminals.replace("OP_WAITING_TIME", maxOpWaitingTime);
			this.maxTerminals.replace("WORK_REMAINING", maxWorkRemaining);
			this.maxTerminals.replace("NUM_OPS_REMAINING", maxNumOpsRemaining);
			this.maxTerminals.replace("WEIGHT", maxWeight);
			this.maxTerminals.replace("TIME_IN_SYSTEM", maxTimeInSystem);
			this.maxTerminals.replace("RELATIVE_DUE_DATE", maxRelativeDueDate);
			this.maxTerminals.replace("SLACK", maxSlack);
			this.maxTerminals.replace("TRANSFER_TIME", maxTransferTime);
		}
	}

	public List<List<CustomerPoint>> getCluster() {
		return cluster;
	}


	@Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

	    //System.out.println("generation "+generation);
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

		this.evaluatePreferenceIndex = this.generation;
		this.evaluateOrder = 0;

	    evaluator.evaluatePopulation(this);
	      //// here, after this we evaluate the population
//	    statistics.postEvaluationStatistics(this); //log the best individual

		//modified by mengxu. Measure diversity. 2021.04.15-------------------------
		Individual[] individuals = this.population.subpops[0].individuals;
		GenotypeDiversity genoD = new GenotypeDiversity();
		double genoDvalue = (double)genoD.genotypeDiversity(individuals) / individuals.length;
//		System.out.println("Genotype diversity: " + genoDvalue);
		PhenotypeDiversity phenoD = new PhenotypeDiversity(); //todo: this should be modified for multi-objective GP
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

//		//calculate the pc of each individual in each subpop and do cluster--------------
//		PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
//		//dynamic simulation
//		phenoCharacterisation[0] =
//					SequencingPhenoCharacterisation.defaultPhenoCharacterisation();
//		phenoCharacterisation[1] =
//					RoutingPhenoCharacterisation.defaultPhenoCharacterisation();
//		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, true); //3. calculate the phenotypic characteristic
		//get the fitness for training model
//		double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];
//		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
//			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
//				fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
//			}
//		}
//		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
//		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
//		System.out.println("PC diversity: " + pcDvalue);

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
		System.out.println("Genotype diversity: " + genoDvalue);


		int popSize = population.subpops[0].individuals.length;
		double[] obj0 = new double[popSize];
		double[] obj1 = new double[popSize];
		for(int i=0; i < popSize; i++){
			MultiObjectiveFitness fit = (MultiObjectiveFitness)population.subpops[0].individuals[i].fitness;
			obj0[i] = fit.objectives[0];
			obj1[i] = fit.objectives[1];
		}
		objective0.add(obj0);
		objective1.add(obj1);



		statistics.postEvaluationStatistics(this); //log the best individual

		printFrontforGen(false, false);//add by mengxu 2022.06.29
		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;

		if(generation%printGap == 0){//add by mengxu 2022.08.05
			statistics.middleStatistics(this, this.R_NOTDONE);
		}

		//---------------------------------------------------------


		//add 2021.10.08
//		populationNormalisation();

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
			writeSelectParentIndexToFile();
			writeEachObjToFile();
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

		// Generate new instances if needed //modified by mengxu 2022.06.29
//		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
		if (problem.getEvaluationModel().isRotatable()) {
			problem.rotateEvaluationModel();
		}
//		evaluator.evaluatePopulation(this);//modified by mengxu 2022.06.29

	    // BREEDING
	    statistics.preBreedingStatistics(this);

	    population = breeder.breedPopulation(this); //!!!!!!   return newpop;  if it is NSGA-II, the population here is 2N

	    // POST-BREEDING EXCHANGING
	    statistics.postBreedingStatistics(this);   //position 1  here, a new pop has been generated.

	    // POST-BREEDING EXCHANGING
//	    statistics.prePostBreedingExchangeStatistics(this);
//	    population = exchanger.postBreedingExchangePopulation(this);   /** Simply returns state.population. */
//	    statistics.postPostBreedingExchangeStatistics(this);  //position 2

		//change the location of this
//	    // Generate new instances if needed //original place 2022.06.29
//		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
//	    if (problem.getEvaluationModel().isRotatable()) {
//			problem.rotateEvaluationModel();
//		}

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

	public void printFrontforGen(boolean updateBiasBalance, boolean normalisation){
		if(normalisation){
			System.out.println("\nPareto Front with normalisation of Subpopulation " + generation);

			MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(this.population.subpops[0].individuals[0].fitness);
			// build front
			ArrayList front = typicalFitness.partitionIntoParetoFront(this.population.subpops[0].individuals, null, null);

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

			double[] idealPoint = ((PSLInitializer)this.initializer).idealPoint;
			double[] maxObjectives = ((PSLInitializer)this.initializer).maxObjectives;

			// print out front to statistics log
			double[] diffs = new double[typicalFitness.getNumObjectives()];
			for(int j=0; j< typicalFitness.getNumObjectives(); j++){
				System.out.print("Objective " + j + ": [");
				if(updateBiasBalance) {
					double leftBound = 0;
					double rightBound = 0;
					for (int i = 0; i < sortedFront.length; i++) {
						double fit = ((MultiObjectiveFitness) (((Individual) (sortedFront[i])).fitness)).getObjective(j);
						double normalisaFit = Math.abs(fit - idealPoint[j]) / (maxObjectives[j] - idealPoint[j]);
						if (i == 0){
							leftBound = normalisaFit;
							System.out.print(normalisaFit + ", ");
						}
						else if (i == sortedFront.length - 1) {
							rightBound = normalisaFit;
							System.out.println(normalisaFit + "]");
						} else {
							System.out.print(normalisaFit + ", ");
						}
					}

					double diff = Math.abs(rightBound - leftBound);
					diffs[j] = 1/diff;//original

					System.out.println("diff " + j + ": " + diffs[j]);
				}
				else{
					for (int i = 0; i < sortedFront.length; i++){
						double fit = ((MultiObjectiveFitness)(((Individual)(sortedFront[i])).fitness)).getObjective(j);
						double normalisaFit = Math.abs(fit - idealPoint[j]) / (maxObjectives[j] - idealPoint[j]);
						if(i == sortedFront.length - 1){
							System.out.println(normalisaFit + "]");
						}
						else{
							System.out.print(normalisaFit + ", ");
						}
					}
				}
			}
		}
		else{
			System.out.println("\nPareto Front of Subpopulation " + generation);

			MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(this.population.subpops[0].individuals[0].fitness);
			// build front
			ArrayList front = typicalFitness.partitionIntoParetoFront(this.population.subpops[0].individuals, null, null);

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

			// print out front to statistics log
			double[] diffs = new double[typicalFitness.getNumObjectives()];
			for(int j=0; j< typicalFitness.getNumObjectives(); j++){
				System.out.print("Objective " + j + ": [");
				if(updateBiasBalance) {
					double leftBound = 0;
					double rightBound = 0;
					for (int i = 0; i < sortedFront.length; i++) {

						double fit = ((MultiObjectiveFitness) (((Individual) (sortedFront[i])).fitness)).getObjective(j);
						if (i == 0){
							leftBound = fit;
							System.out.print(fit + ", ");
						}
						else if (i == sortedFront.length - 1) {
							rightBound = fit;
							System.out.println(fit + "]");
						} else {
							System.out.print(fit + ", ");
						}
					}
					double diff = Math.abs(rightBound - leftBound);
					diffs[j] = 1/diff; //original
				}
				else{
					for (int i = 0; i < sortedFront.length; i++){
						double fit = ((MultiObjectiveFitness)(((Individual)(sortedFront[i])).fitness)).getObjective(j);
						if(i == sortedFront.length - 1){
							System.out.println(fit + "]");
						}
						else{
							System.out.print(fit + ", ");
						}
					}
				}
			}
		}
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

	//2021.11.03 modified by mengxu, to store the selected parent index of each generation
	public void writeEachObjToFile(){
		int objNum = 2;
		File obj0Fitness = new File("job." + jobSeed + ".obj0fitness.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(obj0Fitness));
			writer.write("Gen"+ "," + "Index" + "," + "Fitness");
			writer.newLine();
			for (int gen = 0; gen < numGenerations; gen++) {
				double[] ref = objective0.get(gen);
//				writer.write("gen"+ gen + ",");
				for(int i=0; i< ref.length; i++){
					if(ref[i] >= Double.POSITIVE_INFINITY || ref[i] >= Double.MAX_VALUE){
						continue;
					}
					else{
						writer.write("gen"+ gen + "," + i + "," + ref[i]);
						writer.newLine();
					}

//					if(i==ref.length-2){
//						writer.write(ref[i] + "," + ref[i+1]);
//					}
//					else{
//						writer.write(ref[i] + ",");
//					}
				}
//				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		File obj1Fitness = new File("job." + jobSeed + ".obj1fitness.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(obj1Fitness));
			writer.write("Gen"+ "," + "Index" + "," + "Fitness");
			writer.newLine();
			for (int gen = 0; gen < numGenerations; gen++) {
				double[] ref = objective1.get(gen);
//				writer.write("gen"+ gen + ",");
				for(int i=0; i< ref.length; i++){
					if(ref[i] >= Double.POSITIVE_INFINITY || ref[i] >= Double.MAX_VALUE){
						continue;
					}
					else{
						writer.write("gen"+ gen + "," + i + "," + ref[i]);
						writer.newLine();
					}
//					if(i==ref.length-2){
//						writer.write(ref[i] + "," + ref[i+1]);
//					}
//					else{
//						writer.write(ref[i] + ",");
//					}
				}
//				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	//add 2021.10.08
	public void populationNormalisation(){
//		this.normalisationFitness.clear();
		Individual[] oldinds = this.population.subpops[0].individuals.clone();
		int numObjective = ((MultiObjectiveFitness)oldinds[0].fitness).getNumObjectives();
		double[] minFitness = new double[numObjective];
		double[] maxFitness = new double[numObjective];
		for(int j=0; j<numObjective; j++){
			minFitness[j] = Double.POSITIVE_INFINITY;
			maxFitness[j] = 0;
		}

//		List<Double[][]> fitnessNormalisation = new ArrayList<>();
		for(int i=0; i<oldinds.length; i++){
			double[] multiFitness = ((MultiObjectiveFitness)oldinds[i].fitness).objectives;

			for(int j=0; j<numObjective; j++){
				if(multiFitness[j] >= Double.POSITIVE_INFINITY || multiFitness[j] >= Double.MAX_VALUE){
					continue;
				}
				else{
					if(multiFitness[j]>maxFitness[j]){
						maxFitness[j] = multiFitness[j];
					}
					if(multiFitness[j]<minFitness[j]){
						minFitness[j] = multiFitness[j];
					}
				}
			}
		}

		//modified 2021.10.15
		updateMinMaxFitness(minFitness, maxFitness);

//		System.out.print("minFitness and maxFitness: ");
//		for(int j=0; j<numCase; j++){
//			System.out.print("(" + minFitness[j] + ", " + maxFitness[j] + ")");
//		}
//		System.out.println();

		//todo: if used for multi-objective, need to modify.
		for(int i=0; i<oldinds.length; i++) {
			double[] multiFitness = ((MultiObjectiveFitness)oldinds[i].fitness).objectives;

			double[] normalisationFitness = new double[numObjective];
			for (int j = 0; j < numObjective; j++) {
				if (multiFitness[j] >= Double.POSITIVE_INFINITY || multiFitness[j] >= Double.MAX_VALUE) {
					double value = Double.POSITIVE_INFINITY;
					normalisationFitness[j] = value;
				} else if (maxFitness[j] - minFitness[j] <= 0) {
					System.out.println("Error! maxFitnessCase-minFitnessCase could not be 0.");
				} else {
					double value = (multiFitness[j] - minFitness[j]) / (maxFitness[j] - minFitness[j]);
					normalisationFitness[j] = value;
				}
			}
			((MultiObjectiveFitness)oldinds[i].fitness).objectives = normalisationFitness;
//			this.normalisationFitness.add(normalisationFitness);
		}
	}

	public void updateMinMaxFitness(double[] minFitness, double[] maxFitness){
		this.minFitnessGen = minFitness;
		this.maxFitnessGen = maxFitness;
	}
	
}
