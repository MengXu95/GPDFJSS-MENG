package mengxu.algorithm.multiobjective.lexicaseselection;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.multiobjective.ParentFairForCrossoverMO;
import mengxu.algorithm.multiobjective.ParentForMutationMO;
import mengxu.cluster.CustomerPoint;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

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

public class GPRuleEvolutionStateMOLS extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();
	public List<List<CustomerPoint>> cluster = new ArrayList<>();
	public List<Individual> ensemble = new ArrayList<>();
	public List<List<Individual>> ensembleAll = new ArrayList<>();

	public List<Integer> parentIndex = new ArrayList<>();
	public List<List<Integer>> allGenerationParentIndex = new ArrayList<>();

	private List<double[]> normalisationFitness = new ArrayList<>();

	//2021.10.14 to find the difference with Tournament selection
	public List<ParentFairForCrossoverMO> parentsIndexPairForCrossover = new ArrayList<>();
	public List<ParentForMutationMO> parentIndexForMutation = new ArrayList<>();



	@Override
	public void setup(EvolutionState state, Parameter base) {

		super.setup(this, base);

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

		//calculate the pc of each individual in each subpop and do cluster--------------
		PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
		//dynamic simulation
		phenoCharacterisation[0] =
					SequencingPhenoCharacterisation.defaultPhenoCharacterisation();
		phenoCharacterisation[1] =
					RoutingPhenoCharacterisation.defaultPhenoCharacterisation();
		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, true); //3. calculate the phenotypic characteristic
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

		if(generation != 0){
			List<Integer> parentIndexCopy = new ArrayList<>(parentIndex);
			allGenerationParentIndex.add(parentIndexCopy);
		}
		ParentIndexDiversity parentIndexD = new ParentIndexDiversity();
		double parentIndexDvalue = (double)parentIndexD.parentIndexDiversity(parentIndex) / individuals.length;
		System.out.println("ParentIndex diversity: " + parentIndexDvalue);
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

		int badRun = 0;

		for(int i=0; i < population.subpops[0].individuals.length; i++){
			if(population.subpops[0].individuals[i].fitness.fitness() >= Double.POSITIVE_INFINITY ||
					population.subpops[0].individuals[i].fitness.fitness() >= Double.MAX_VALUE){
				badRun ++;
			}
		}
		System.out.println("Generation " + generation + " bad run: " + badRun);
		//there are too many bad run!

		//add 2021.10.08
//		populationNormalisation();


		statistics.postEvaluationStatistics(this); //log the best individual
		//---------------------------------------------------------

		//2021.10.14 to find the difference with Tournament selection======
//		Individual[] individuals = this.population.subpops[0].individuals;
		System.out.println("Crossover:");
		for(ParentFairForCrossoverMO ref: parentsIndexPairForCrossover){
			ref.setChildFitness(((MultiObjectiveFitness)individuals[ref.getChildID()].fitness).objectives);
			ref.print();
		}
		System.out.println("Mutation:");
		for(ParentForMutationMO ref: parentIndexForMutation){
			ref.setChildFitness(((MultiObjectiveFitness)individuals[ref.getChildID()].fitness).objectives);
			ref.print();
		}
		parentsIndexPairForCrossover.clear();
		parentIndexForMutation.clear();
		//================================================

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
			writeSelectParentIndexToFile(); //modified by mengxu 2021.07.29
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

	public List<double[]> getNormalisationFitness() {
		return normalisationFitness;
	}

	//add 2021.10.08
	public void populationNormalisation(){
		this.normalisationFitness.clear();
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
			this.normalisationFitness.add(normalisationFitness);
		}
	}
	
}
