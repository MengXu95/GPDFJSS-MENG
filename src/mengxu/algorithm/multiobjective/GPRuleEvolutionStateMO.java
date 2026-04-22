package mengxu.algorithm.multiobjective;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Checkpoint;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.multiobjective.NSGPII.zScore.NSGA2MultiObjectiveFitnessNormalisation;
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

public class GPRuleEvolutionStateMO extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();
	public List<List<CustomerPoint>> cluster = new ArrayList<>();
	public List<Individual> ensemble = new ArrayList<>();
	public List<List<Individual>> ensembleAll = new ArrayList<>();

	public List<Integer> parentIndex = new ArrayList<>();
	public List<List<Integer>> allGenerationParentIndex = new ArrayList<>();

	//add 2021.11.3
	public List<double[]> objective0 = new ArrayList<>();
	public List<double[]> objective1 = new ArrayList<>();

	public PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];

	public int printGap;

	public boolean useZScoreNormalisation;

	//added by mengxu 2023.06.19
	public boolean useParentOffspringDistanceLimitation;
	public double distanceForParentOffspringLimitation;

	public boolean useEuclideanDistance;

//	@Override
//	public void setup(EvolutionState state, Parameter base) {
//
//		super.setup(this, base);
//
//	}

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

		Parameter printGapParam = new Parameter("print-gap"); //add by mengxu 2023.01.24
		printGap = state.parameters.getInt(printGapParam, null); //add by mengxu 2023.01.24

		Parameter useZScoreNormalisationParam = new Parameter("use-zscore-normalisation"); //add by mengxu 2023.01.24
		useZScoreNormalisation = state.parameters.getBoolean(useZScoreNormalisationParam, false); //add by mengxu 2023.01.24

		Parameter useEuclideanDistanceParam = new Parameter("use-euclidean-distance"); //add by mengxu 2023.01.24
		useEuclideanDistance = state.parameters.getBoolean(useEuclideanDistanceParam, false); //add by mengxu 2023.01.24

		//added by mengxu 2023.06.19
		Parameter useParentOffspringDistanceLimitationParam = new Parameter("use-parent-offspring-distance-limitation"); //add by mengxu 2023.01.24
		useParentOffspringDistanceLimitation = state.parameters.getBoolean(useParentOffspringDistanceLimitationParam, false); //add by mengxu 2023.01.24
		Parameter distanceForParentOffspringLimitationParam = new Parameter("distance-for-parent-offspring-limitation"); //add by mengxu 2023.01.24
		distanceForParentOffspringLimitation = state.parameters.getDoubleWithDefault(distanceForParentOffspringLimitationParam, null, Double.MAX_VALUE); //add by mengxu 2023.01.24

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



		Individual[] individualsAll = population.subpops[0].individuals;
		double num = ((MultiObjectiveFitness)(((Individual)(individualsAll[0])).fitness)).getObjectives().length;

//		for(int i=0; i<num; i++){
//			System.out.print("Objective [");
//		for(int j=0; j< individualsAll.length; j++){
//				double fitNormalised = ((NSGA2MultiObjectiveFitnessNormalisation)(((Individual)(individualsAll[j])).fitness)).getNormalisedObjectives(i);
//				if(j == individualsAll.length - 1){
//					System.out.println(fitNormalised + "]");
//				}
//				else{
//					System.out.print(fitNormalised + ", ");
//				}
//			}
//		}

		//modified by mengxu. Measure diversity. 2021.04.15-------------------------
		Individual[] individuals = this.population.subpops[0].individuals;
		GenotypeDiversity genoD = new GenotypeDiversity();
		double genoDvalue = (double)genoD.genotypeDiversity(individuals) / individuals.length;
//		System.out.println("Genotype diversity: " + genoDvalue);
		PhenotypeDiversity phenoD = new PhenotypeDiversity();
		double phenoDvalue = (double)phenoD.phenotypeDiversity(individuals) / individuals.length;
//		System.out.println("Phenotype diversity: " + phenoDvalue);
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

//		Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);

		//calculate the pc of each individual in each subpop and do cluster--------------
//		PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
//		//dynamic simulation
//		phenoCharacterisation[0] =
//					SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
//		phenoCharacterisation[1] =
//					RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, false); //3. calculate the phenotypic characteristic
		//get the fitness for training model
		double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];
		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
				//modify here as we have two individuals
				double[] objectives = ((MultiObjectiveFitness)this.population.subpops[subpop].individuals[ind].fitness).objectives;
				double sumFit = 0;
				for(int i=0; i<objectives.length; i++){
					sumFit = sumFit + objectives[i];
				}
				fitnessesForModel[subpop][ind] = sumFit;
				//original
//				fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
			}
		}
		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
		System.out.println("PC diversity: " + pcDvalue);

		//add by mengxu 2023.07.20 to store PC
		if(generation == 1 || generation == 25 || generation == 50){
			writePopulationPCToFile(indsCharListsMultiTree[0], fitnessesForModel[0]);
		}

		//add by mengxu 2023.06.06
//		double pcDvalue2 = (double)pcD.phenotypicCharacteristicDiversity(((NSGA2EvaluatorNoEnvironmentalSelectionPhenotypeBreeding)this.evaluator).offspringCharLists) / individuals.length;
//		System.out.println("PC diversity 2: " + pcDvalue2);
//		((NSGA2EvaluatorNoEnvironmentalSelectionPhenotypeBreeding)this.evaluator).offspringCharLists.clear();

		EntropyDiversityBasedOnPC entroD = new EntropyDiversityBasedOnPC();
		double entroDvalue = entroD.entropyDiversityBasedOnPC(individuals,indsCharListsMultiTree[0]);
		System.out.println("Entropy diversity: " + entroDvalue);

//		if(generation != 0){
//			List<Integer> parentIndexCopy = new ArrayList<>(parentIndex);
//			allGenerationParentIndex.add(parentIndexCopy);
//		}
//		ParentIndexDiversity parentIndexD = new ParentIndexDiversity();
//		double parentIndexDvalue = (double)parentIndexD.parentIndexDiversity(parentIndex) / individuals.length;
//		System.out.println("ParentIndex diversity: " + parentIndexDvalue);
//		parentIndex.clear();

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
		diversities.add(0.0);
//		diversities.add(pcDvalue);
//		diversities.add(parentIndexDvalue);
		storeGenDiversities.add(diversities);


		//2021.11.08 to save each objective
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


//		int badRun = 0;
//
//		for(int i=0; i < population.subpops[0].individuals.length; i++){
//			if(population.subpops[0].individuals[i].fitness.fitness() >= Double.POSITIVE_INFINITY ||
//					population.subpops[0].individuals[i].fitness.fitness() >= Double.MAX_VALUE){
//				badRun ++;
//			}
//		}
//		System.out.println("Generation " + generation + " bad run: " + badRun);




		statistics.postEvaluationStatistics(this); //log the best individual
		printFrontforGen();//modified by mengxu 2022.06.29
		//---------------------------------------------------------

		if(generation%printGap == 0){//add by mengxu 2022.08.05
			statistics.middleStatistics(this, this.R_NOTDONE);
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

	public void printFrontforGen(){
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
		for(int j=0; j< typicalFitness.getNumObjectives(); j++){
			System.out.print("Objective " + j + ": [");
			for (int i = 0; i < sortedFront.length; i++){
				double fit = ((MultiObjectiveFitness)(((Individual)(sortedFront[i])).fitness)).getObjective(j);
				if(((MultiObjectiveFitness)(((Individual)(sortedFront[i])).fitness)) instanceof NSGA2MultiObjectiveFitnessNormalisation && this.useZScoreNormalisation){
					double fitNormalised = ((NSGA2MultiObjectiveFitnessNormalisation)(((Individual)(sortedFront[i])).fitness)).getNormalisedObjectives(j);
					if(i == sortedFront.length - 1){
						System.out.println(fit + ", " + fitNormalised + "]");
					}
					else{
						System.out.print(fit + ", " + fitNormalised + ", ");
					}
				}
				else{
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


	//2023.7.20 add by mengxu
	public void writePopulationPCToFile(double[][] indsCharListsMultiTree, double[] fitnessForModel){
		File diversities = new File("job." + jobSeed + "_gen_" + this.generation + ".populationPC.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Index, Seq1, Seq2, Seq3, Seq4, Seq5, Seq6, Seq7, Seq8, Seq9, Seq10, Seq11, Seq12, Seq13, Seq14, Seq15, Seq16, Seq17, Seq18, Seq19, Seq20," +
					"Rout1, Rout2, Rout3, Rout4, Rout5, Rout6, Rout7, Rout8, Rout9, Rout10, Rout11, Rout12, Rout13, Rout14, Rout15, Rout16, Rout17, Rout18, Rout19, Rout20, fitness");
			writer.newLine();
			for (int ind = 0; ind < indsCharListsMultiTree.length; ind++) {
				double[] refPC = indsCharListsMultiTree[ind];
				double refFit = fitnessForModel[ind];
				writer.write(ind + "," + refPC[0] + "," + refPC[1] + "," + refPC[2] + "," + refPC[3] + "," + refPC[4]
						+ "," + refPC[5] + "," + refPC[6] + "," + refPC[7] + "," + refPC[8] + "," + refPC[9] + "," + refPC[10]
						+ "," + refPC[11] + "," + refPC[12] + "," + refPC[13] + "," + refPC[14] + "," + refPC[15] + "," + refPC[16]
						+ "," + refPC[17] + "," + refPC[18] + "," + refPC[19]
						+ "," + refPC[20] + "," + refPC[21] + "," + refPC[22] + "," + refPC[23] + "," + refPC[24]
						+ "," + refPC[25] + "," + refPC[26] + "," + refPC[27] + "," + refPC[28] + "," + refPC[29] + "," + refPC[30]
						+ "," + refPC[31] + "," + refPC[32] + "," + refPC[33] + "," + refPC[34] + "," + refPC[35] + "," + refPC[36]
						+ "," + refPC[37] + "," + refPC[38] + "," + refPC[39] + "," + refFit);
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
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


}
