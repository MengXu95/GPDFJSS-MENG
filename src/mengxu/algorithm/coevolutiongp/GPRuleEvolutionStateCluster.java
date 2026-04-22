package mengxu.algorithm.coevolutiongp;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.cluster.CustomerPoint;
import mengxu.cluster.kmeans.Cluster;
import mengxu.cluster.kmeans.DistanceType;
import mengxu.cluster.kmeans.KMeans;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author yimei
 *
 */

public class GPRuleEvolutionStateCluster extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();
	public List<List<CustomerPoint>> cluster = new ArrayList<>();
	public List<Double> clusterMeanFitness = new ArrayList<>();
	public List<Individual> ensemble = new ArrayList<>();
	public List<List<Individual>> ensembleAll = new ArrayList<>();

	public List<Integer> parentIndex = new ArrayList<>();

	public static final String P_CLUSTER_SIZE = "cluster-size";
	private int clusterSize;

	public static final String P_CLUSTER_THRESHOLD = "cluster-threshold";
	private int clusterThreshold;

	public static final String P_CLUSTER_ELEMENT_SIZE = "cluster-element-size";
	private int clusterElementSize;

	public static final String P_CLUSTER_WITH_FITNESS = "cluster-with-fitness";
	private boolean clusterWithFitness;

	public double[] baselineFitness = {380.7879630623341, 382.0929513553887, 392.6358249764665,
			412.1279608021726, 382.3035622162548, 382.39090551102413,
			372.9016649729468, 383.50393380851904, 404.06249228143207,
			367.60639173694733, 376.27445796611545, 398.12979286961337,
			363.61677343081243, 381.06402167641744, 382.380787485658,
			377.7879158213285, 356.349605115855, 407.47497162867035,
			370.6942820767157, 369.6175900429756, 357.84691443359935,
			364.44925945383517, 371.3051152904222, 362.7045486408833,
			425.41422131883337, 390.68050834719344, 371.7283746436932,
			410.0347725485139, 352.2897178707909, 368.8340727739875,
			354.4755295408049, 360.19383460361036, 374.2380818791683,
			364.95127915964605, 367.81810554708574, 365.2254039993963,
			372.4726363624875, 391.78464317483235, 381.46061802842314,
			383.3115584161221, 380.17992403826895, 376.44241226145954,
			356.28570208027145, 373.59108127508557, 383.04416432769534,
			376.3103722384967, 366.5943704760435, 399.40522399222937,
			378.4421915110579, 386.55251146355187, 407.6087038529212};

	@Override
	public void setup(EvolutionState state, Parameter base) {
		super.setup(this, base);
		clusterSize = state.parameters.getInt(new Parameter(P_CLUSTER_SIZE), null);
		clusterThreshold = state.parameters.getInt(new Parameter(P_CLUSTER_THRESHOLD), null);
		clusterElementSize = state.parameters.getInt(new Parameter(P_CLUSTER_ELEMENT_SIZE), null);
		clusterWithFitness = state.parameters.getBoolean(new Parameter(P_CLUSTER_WITH_FITNESS), false);
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
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

	    evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
//	    statistics.postEvaluationStatistics(this); //log the best individual
		PopulationUtils.sort(population); //2020.11.24 important!!!  otherwise, can not choose good inds based on rank
//		System.out.println("bestFitness: " + this.population.subpops[0].individuals[0].fitness.fitness());
//		System.out.println("bestFitness/baselineFitness: " + this.population.subpops[0].individuals[0].fitness.fitness()/baselineFitness[generation]);

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
		double[][][] indsCharListsMultiTree = new double[this.population.subpops.length][][];
		double[][] fitnessesForModel = new double[this.population.subpops.length][];
		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
			indsCharListsMultiTree[subpop] = new double[this.population.subpops[subpop].individuals.length][];
			fitnessesForModel[subpop] = new double[this.population.subpops[subpop].individuals.length];
			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
//				Double[] decisionVector = ((GPIndividual)this.population.subpops[subpop].individuals[ind]).decisionVector;
//				indsCharListsMultiTree[subpop][ind] = new double[decisionVector.size()];
//				for(int dec=0;dec<decisionVector.size();dec++){
//					indsCharListsMultiTree[subpop][ind][dec] = decisionVector.get(dec);
//				}
				Double[] decisionVector = ((GPIndividual)this.population.subpops[subpop].individuals[ind]).decisionVector;
				indsCharListsMultiTree[subpop][ind] = new double[decisionVector.length];
				for(int dec=0;dec<decisionVector.length;dec++){
					indsCharListsMultiTree[subpop][ind][dec] = decisionVector[dec];
				}
				fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
			}
		}
		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]);
//		System.out.println("PC diversity: " + pcDvalue + " PC diversity percent: " + pcDvalue/ individuals.length);

//		//calculate the pc of each individual in each subpop and do cluster--------------
//		PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
////		//dynamic simulation
////		phenoCharacterisation[0] =
////				SequencingPhenoCharacterisation.sameSimulationPhenoCharacterisation(0,20,0.95,4);
////		phenoCharacterisation[1] =
////				RoutingPhenoCharacterisation.sameSimulationPhenoCharacterisation(0,20,0.95,4);
//		//dynamic simulation
//		phenoCharacterisation[0] =
//					SequencingPhenoCharacterisation.defaultPhenoCharacterisation();
//		phenoCharacterisation[1] =
//					RoutingPhenoCharacterisation.defaultPhenoCharacterisation();
//		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, true); //3. calculate the phenotypic characteristic
//		//get the fitness for training model
//		double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];
//		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
//			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
//				fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
//			}
//		}
//		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
//		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
//		System.out.println("PC diversity: " + pcDvalue);

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

		//modified by mengxu 2021.06.29 just use the top 300 to do parent
		double[][] indsCharListsMultiTree300 = Arrays.copyOfRange(indsCharListsMultiTree[0],  0 ,  clusterElementSize );
		double[] fitnessesForModel300 = Arrays.copyOfRange(fitnessesForModel[0],  0 ,  clusterElementSize);
//		System.out.println("length: " + fitnessesForModel300.length);
		List<CustomerPoint> customerPoints = buildCustomerPoint(indsCharListsMultiTree300, fitnessesForModel300, clusterWithFitness);

		//original
		//this is suitable for only one subpop
//		List<CustomerPoint> customerPoints = buildCustomerPoint(indsCharListsMultiTree[0], fitnessesForModel[0], true);

		//DBS cluster---------------------------------
//		DBScan db = null; //返回结果并打印
//		try {
//			db = new DBScan(7, 0);//how to set the distance, the minPoints must be set to 0, because if not 0, then some point will be set as noise, and will not be included in the cluster
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		List<List<CustomerPoint>> aa =db.cluster(customerPoints);

		//Kmeans cluster--------------------------------
		DistanceType type = DistanceType.BinaryBring;
		KMeans kRun =new KMeans(clusterSize, customerPoints, type);
		Set<Cluster> clusterSet = kRun.run();
		List<List<CustomerPoint>> aa = kRun.getCluster(clusterSet);

//		System.out.println("Cluster size: " + aa.size());
		cluster = aa;



		//2021.06.23 modified by mengxu cluster size adaptation------
//		int threshold = 10;
//		System.out.println("clusterThreshold: " + clusterThreshold);
		List<CustomerPoint> centerList = new ArrayList<>();
		for(Cluster cluster:clusterSet){
			centerList.add(cluster.getCenter());
		}
		for(int i=0;i<aa.size();i++){
			double minDis = Double.MAX_VALUE;
			int minIndex = 0;
			int eachClusterSize = aa.get(i).size();
			if(eachClusterSize < clusterThreshold){
				for(int ref=0; ref<eachClusterSize; ref++){
					CustomerPoint p1 = aa.get(i).get(ref);
					for(int j=0; j<centerList.size(); j++){
						if(i != j){//modified by mengxu 2021.12.27
//							if(minDis > p1.distanceFromPearson(centerList.get(j))){
//								minDis = p1.distanceFromPearson(centerList.get(j));
							if(minDis > p1.distanceFromBinaryBring(centerList.get(j))){
								minDis = p1.distanceFromBinaryBring(centerList.get(j));
								minIndex = j;
							}
							//original
//							if(minDis > p1.distanceFrom(centerList.get(j))){
//								minDis = p1.distanceFrom(centerList.get(j));
//								minIndex = j;
//							}
						}
					}
					aa.get(minIndex).add(p1);
				}
				aa.remove(i);
				centerList.remove(i);
				i--;
			}
		}
//		System.out.println("Cluster size after adaptation: " + cluster.size());
//		this.clusterSize = cluster.size();
//		clusterMeanFitness.clear();
//		for(int i=0; i<cluster.size(); i++){
//			double meanFitness = 0;
//			for(int j=0; j<cluster.get(i).size(); j++){
//					if(cluster.get(i).get(j).getFitness() >= Double.POSITIVE_INFINITY ||
//							cluster.get(i).get(j).getFitness() >= Double.MAX_VALUE){
//						continue;
//					}
//					else{
//						meanFitness += cluster.get(i).get(j).getFitness();
//					}
//			}
//			meanFitness = meanFitness / cluster.get(i).size();
//			clusterMeanFitness.add(meanFitness);
//		}
//		writeClusterToFile();
		//-----------------------------------------------------------

//		int numInCluster = 0;
//		System.out.print("each cluster size: ");
//		for(int i=0;i<aa.size();i++){
//			numInCluster += aa.get(i).size();
//			System.out.print("  [" + aa.get(i).size() + ", " + clusterMeanFitness.get(i) + "]");
//		}
//		System.out.println();
//		System.out.println("Number in cluster: " + numInCluster);

		int maxEnsemble = 5;
		List<Individual> allDiverseIndividualFromCluster = getAllDiverseIndividualFromCluster(aa);
		ensemble = getEnsemble(allDiverseIndividualFromCluster,maxEnsemble);
		ensembleAll.add(ensemble);
//		System.out.print("ensemble fitness: ");
//		for(int i=0; i< ensemble.size();i++){
//			System.out.print(ensemble.get(i).fitness.fitness() + ",");
//		}
//		System.out.println();




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
			writeEnsembleToFile();//modified by mengxu 2021.05.08
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
	
}
