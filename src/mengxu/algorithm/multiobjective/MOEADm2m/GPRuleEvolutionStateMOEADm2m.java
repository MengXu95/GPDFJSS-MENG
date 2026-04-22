package mengxu.algorithm.multiobjective.MOEADm2m;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.MultiObjectiveStatistics;
import ec.util.Checkpoint;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.multiobjective.utils.Archive;
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

public class GPRuleEvolutionStateMOEADm2m extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();
	public List<List<CustomerPoint>> cluster = new ArrayList<>();
	public List<Individual> ensemble = new ArrayList<>();
	public List<List<Individual>> ensembleAll = new ArrayList<>();

	public List<Integer> parentIndex = new ArrayList<>();
	public List<List<Integer>> allGenerationParentIndex = new ArrayList<>();
	public double[] minFitnessGen;
	public double[] maxFitnessGen;

	//add 2021.11.3
	public List<double[]> objective0 = new ArrayList<>();
	public List<double[]> objective1 = new ArrayList<>();

	//add by mengxu 2022.08.08
//	public boolean useBiasBalance = true;
//	public boolean normaliseBeforeBiasBalance = false; //add by mengxu 2022.09.01
//	public boolean useArchive = false; //add by mengxu 2022.09.01
	public Archive externalArchive = new Archive(); //add by mengxu 2022.09.02
	public PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
//	public boolean normaliseBeforeBreeding = false; //add by mengxu 2022.09.01


	@Override
	public void setup(EvolutionState state, Parameter base) {

		super.setup(this, base);

		//calculate the pc of each individual in each subpop and do cluster--------------
		this.phenoCharacterisation = new PhenoCharacterisation[2];
		//dynamic simulation
		phenoCharacterisation[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation();
		phenoCharacterisation[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation();

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

//	    if(generation == 0){ //modified for MOEAD by mengxu //modified by mengxu 2022.06.29
		evaluator.evaluatePopulation(this);
//		}
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

		boolean useBiasBalance = ((MOEADm2mInitializer)this.initializer).useBias;
		boolean normaliseBeforeBiasBalance = ((MOEADm2mInitializer)this.initializer).normaliseBeforeBiasBalance;
		boolean useArchive = ((MOEADm2mInitializer)this.initializer).useArchive;

		printFrontforGen(useBiasBalance, normaliseBeforeBiasBalance);//add by mengxu 2022.06.29
		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
		if(!problem.getEvaluationModel().isRotatable()){
			this.externalArchive.printArchive(this);
		}
		System.out.println("External archive size: " + externalArchive.getArchive().size()); //for checking 2022.09.22

		if(generation%5 == 0){//add by mengxu 2022.08.05
			if(useArchive){
				//Output all the individuals in the external archive
				((MultiObjectiveStatistics)statistics).externalArchiveStatistics(this, this.R_NOTDONE);
			}
			else{
				//Just output the Pareto front from the current generation
				statistics.middleStatistics(this, this.R_NOTDONE);
			}
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
//		if (problem.getEvaluationModel().isRotatable()) {
//			problem.rotateEvaluationModel();
//		}
//		evaluator.evaluatePopulation(this);//modified by mengxu 2022.06.29

	    // BREEDING
	    statistics.preBreedingStatistics(this);

	    population = breeder.breedPopulation(this); //!!!!!!   return newpop;  if it is NSGA-II, the population here is 2N

	    // POST-BREEDING EXCHANGING
	    statistics.postBreedingStatistics(this);   //position 1  here, a new pop has been generated.

	    // POST-BREEDING EXCHANGING
	    statistics.prePostBreedingExchangeStatistics(this);
	    population = exchanger.postBreedingExchangePopulation(this);   /** Simply returns state.population. */
	    statistics.postPostBreedingExchangeStatistics(this);  //position 2

		//change the location of this
//	    // Generate new instances if needed //original place 2022.06.29
//		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
//	    if (problem.getEvaluationModel().isRotatable()) {
//			problem.rotateEvaluationModel();
//		}

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

	public void printFrontforGen(boolean updateBiasBalance, boolean normalisation){
		boolean useArchive = ((MOEADm2mInitializer)this.initializer).useArchive;
		boolean archiveNoPCDuplicated = ((MOEADm2mInitializer)this.initializer).archiveNoPCDuplicated;
		boolean multiDiff = ((MOEADm2mInitializer)this.initializer).multiDiff;

		List<Individual> allParetoFrontFromAllSubpop = new ArrayList<>();

		for(int s=0; s<this.population.subpops.length; s++){
			System.out.println("\nSubpopulation: " + s);
			if(normalisation){
				System.out.println("\nPareto Front with normalisation of Subpopulation " + generation);

				MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(this.population.subpops[s].individuals[0].fitness);
				// build front
				ArrayList front = typicalFitness.partitionIntoParetoFront(this.population.subpops[s].individuals, null, null);

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

				double[] idealPoint = ((MOEADm2mInitializer)this.initializer).idealPoint;
				double[] maxObjectives = ((MOEADm2mInitializer)this.initializer).maxObjectives;

				// print out front to statistics log
				double[] diffs = new double[typicalFitness.getNumObjectives()];
				for(int j=0; j< typicalFitness.getNumObjectives(); j++){
					System.out.print("Objective " + j + ": [");
					if(updateBiasBalance) {
						double leftBound = 0;
						double rightBound = 0;
						for (int i = 0; i < sortedFront.length; i++) {
							if(useArchive){
								if(archiveNoPCDuplicated){ //add individuals into archive if no PC duplicated
									double[] pc = phenotypicForSurrogateV1.phenotypicIndividualFixedDecisions((Individual) (sortedFront[i]), this.phenoCharacterisation);
									this.externalArchive.addIndividualAndPCToArchive((Individual) (sortedFront[i]), pc, true);
								}
								else{
									this.externalArchive.addIndividualToArchiveNoGenotypeDuplicated((Individual) (sortedFront[i]));
									this.externalArchive.updateArchive(this);
								}
							}
							if(this.population.subpops.length > 1 && j == 0){//added by mengxu 2022.10.14
								allParetoFrontFromAllSubpop.add((Individual) (sortedFront[i]));
							}
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
						if(multiDiff){
							diffs[j] = diff;//modified by mengxu 2022.09.07
						}
						else{
							diffs[j] = 1/diff;//original
						}
//					diffs[j] = 1/diff;//original

						System.out.println("diff " + j + ": " + diffs[j]);
					}
					else{
						for (int i = 0; i < sortedFront.length; i++){
							if(useArchive){
								if(archiveNoPCDuplicated){ //add individuals into archive if no PC duplicated
									double[] pc = phenotypicForSurrogateV1.phenotypicIndividualFixedDecisions((Individual) (sortedFront[i]), this.phenoCharacterisation);
									this.externalArchive.addIndividualAndPCToArchive((Individual) (sortedFront[i]), pc, true);
								}
								else{
									this.externalArchive.addIndividualToArchiveNoGenotypeDuplicated((Individual) (sortedFront[i]));
									this.externalArchive.updateArchive(this);
								}

//							double[] pc = phenotypicForSurrogateV1.phenotypicIndividualFixedDecisions((Individual) (sortedFront[i]), this.phenoCharacterisation);
//							this.externalArchive.addIndividualAndPCToArchive((Individual) (sortedFront[i]), pc, true);
							}
							if(this.population.subpops.length > 1 && j == 0){//added by mengxu 2022.10.14
								allParetoFrontFromAllSubpop.add((Individual) (sortedFront[i]));
							}
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

				//currently, this is only for test on two objectives
				if(updateBiasBalance) {
					((MOEADm2mInitializer) this.initializer).setBiasBalance(diffs);
				}
			}
			else{
				System.out.println("\nPareto Front of Subpopulation " + generation);

				MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(this.population.subpops[s].individuals[0].fitness);
				// build front
				ArrayList front = typicalFitness.partitionIntoParetoFront(this.population.subpops[s].individuals, null, null);

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
							if(useArchive){
								if(archiveNoPCDuplicated){ //add individuals into archive if no PC duplicated
									double[] pc = phenotypicForSurrogateV1.phenotypicIndividualFixedDecisions((Individual) (sortedFront[i]), this.phenoCharacterisation);
									this.externalArchive.addIndividualAndPCToArchive((Individual) (sortedFront[i]), pc, true);
								}
								else{
									this.externalArchive.addIndividualToArchiveNoGenotypeDuplicated((Individual) (sortedFront[i]));
									this.externalArchive.updateArchive(this);
								}
//							double[] pc = phenotypicForSurrogateV1.phenotypicIndividualFixedDecisions((Individual) (sortedFront[i]), this.phenoCharacterisation);
//							this.externalArchive.addIndividualAndPCToArchive((Individual) (sortedFront[i]), pc, true);
							}
							if(this.population.subpops.length > 1 && j == 0){//added by mengxu 2022.10.14
								allParetoFrontFromAllSubpop.add((Individual) (sortedFront[i]));
							}
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
						if(multiDiff){
							diffs[j] = diff;//modified by mengxu 2022.09.07
						}
						else{
							diffs[j] = 1/diff;//original
						}
//					diffs[j] = 1/diff; //original
					}
					else{
						for (int i = 0; i < sortedFront.length; i++){
							if(useArchive){
								if(archiveNoPCDuplicated){ //add individuals into archive if no PC duplicated
									double[] pc = phenotypicForSurrogateV1.phenotypicIndividualFixedDecisions((Individual) (sortedFront[i]), this.phenoCharacterisation);
									this.externalArchive.addIndividualAndPCToArchive((Individual) (sortedFront[i]), pc, true);
								}
								else{
									this.externalArchive.addIndividualToArchiveNoGenotypeDuplicated((Individual) (sortedFront[i]));
									this.externalArchive.updateArchive(this);
								}
//							double[] pc = phenotypicForSurrogateV1.phenotypicIndividualFixedDecisions((Individual) (sortedFront[i]), this.phenoCharacterisation);
//							this.externalArchive.addIndividualAndPCToArchive((Individual) (sortedFront[i]), pc, true);
							}
							if(this.population.subpops.length > 1 && j == 0){//added by mengxu 2022.10.14
								allParetoFrontFromAllSubpop.add((Individual) (sortedFront[i]));
							}
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

				//currently, this is only for test on two objectives
				if(updateBiasBalance) {
					((MOEADm2mInitializer) this.initializer).setBiasBalance(diffs);
				}
			}
		}

		if(this.population.subpops.length > 1){//added by mengxu 2022.10.14
			System.out.println("\nPareto Front of all the Subpopulation " + generation);

			MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(this.population.subpops[0].individuals[0].fitness);
			// build front
			ArrayList frontForAllSubPop = typicalFitness.partitionIntoParetoFront(allParetoFrontFromAllSubpop.toArray(new Individual[0]), null, null);

			// sort by objective[0]
			Object[] sortedFrontForAllSubPop = frontForAllSubPop.toArray();
			QuickSort.qsort(sortedFrontForAllSubPop, new SortComparator()
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
			for(int j=0; j< typicalFitness.getNumObjectives(); j++) {
				System.out.print("Objective " + j + ": [");
				for (int i = 0; i < sortedFrontForAllSubPop.length; i++) {
					double fit = ((MultiObjectiveFitness) (((Individual) (sortedFrontForAllSubPop[i])).fitness)).getObjective(j);
					if (i == sortedFrontForAllSubPop.length - 1) {
						System.out.println(fit + "]");
					} else {
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
