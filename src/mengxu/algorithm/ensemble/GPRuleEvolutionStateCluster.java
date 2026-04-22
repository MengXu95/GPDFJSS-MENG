package mengxu.algorithm.ensemble;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.algorithm.lexicaseselection.ParentFairForCrossover;
import mengxu.cluster.CustomerPoint;
import mengxu.cluster.CustomerPointPermutation;
import mengxu.cluster.DBScan;
import mengxu.cluster.kmeans.*;
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
import java.util.Set;

import static yimei.jss.niching.ClearingEvaluator.phenoCharacterisation;

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
//	public List<List<CustomerPoint>> cluster = new ArrayList<>();
	public List<List<CustomerPointPermutation>> cluster = new ArrayList<>();
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
	private int fixedClusterElementSize;

	public static final String P_CLUSTER_WITH_FITNESS = "cluster-with-fitness";
	private boolean clusterWithFitness;

	public List<ParentFairForCrossover> parentsIndexPairForCrossover = new ArrayList<>();
	public List<Double> combinationNumber = new ArrayList<>();
	public List<List<Double>> parentsPearsonScore = new ArrayList<>();

	public double currentPCDiversity;


	@Override
	public void setup(EvolutionState state, Parameter base) {
		super.setup(this, base);
		clusterSize = state.parameters.getInt(new Parameter(P_CLUSTER_SIZE), null);
		clusterThreshold = state.parameters.getInt(new Parameter(P_CLUSTER_THRESHOLD), null);
		clusterElementSize = state.parameters.getInt(new Parameter(P_CLUSTER_ELEMENT_SIZE), null);
		fixedClusterElementSize = clusterElementSize;
		clusterWithFitness = state.parameters.getBoolean(new Parameter(P_CLUSTER_WITH_FITNESS), false);
	}

	public List<List<CustomerPointPermutation>> getCluster() {
		return cluster;
	}
//	public List<List<CustomerPoint>> getCluster() {
//		return cluster;
//	}

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

		//2021.10.14 to find the difference with Tournament selection======
		Individual[] individuals = this.population.subpops[0].individuals;

//	    statistics.postEvaluationStatistics(this); //log the best individual
		PopulationUtils.sort(population); //2020.11.24 important!!!  otherwise, can not choose good inds based on rank
//		System.out.println("bestFitness: " + this.population.subpops[0].individuals[0].fitness.fitness());
//		System.out.println("bestFitness/baselineFitness: " + this.population.subpops[0].individuals[0].fitness.fitness()/baselineFitness[generation]);

		//modified by mengxu. Measure diversity. 2021.04.15-------------------------
		individuals = this.population.subpops[0].individuals;
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


//		//-------------------------------------------------------------------
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


		Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);

//		//calculate the pc of each individual in each subpop and do cluster============================
//		PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
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
//		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
//		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
//		System.out.println("PC diversity: " + pcDvalue);
//		//===========================================================================

		//calculate the pc of each individual in each subpop and do cluster============================
		PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
		phenoCharacterisation[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		phenoCharacterisation[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
		//in[subpop][ind][decisionlength][decision]
		int[][][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisionsPermutation(this, phenoCharacterisation, true); //3. calculate the phenotypic characteristic
		double[][][] indsCharListsMultiTreeBest = new double[indsCharListsMultiTree.length][indsCharListsMultiTree[0].length][indsCharListsMultiTree[0][0].length];

//		for(int i=0; i<1; i++){
		for(int i=0; i<indsCharListsMultiTree.length; i++){
			for(int ind=0; ind < indsCharListsMultiTree[i].length; ind++){
				for(int dec=0; dec < indsCharListsMultiTree[i][ind].length; dec++){
					indsCharListsMultiTreeBest[i][ind][dec] = indsCharListsMultiTree[i][ind][dec][0];
				}
			}
		}
		//get the fitness for training model
		double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];
		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
				fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
			}
		}
		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTreeBest[0]) / individuals.length;
		this.currentPCDiversity = pcDvalue;
		System.out.println("PC diversity: " + pcDvalue);
		//===========================================================================

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

		//modified by mengxu 2021.11.2 to find the index of bad run
//		int notBadMaxIndex = this.population.subpops[0].individuals.length;
//		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
//			for(int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
//				Fitness fitness = this.population.subpops[subpop].individuals[ind].fitness;
//				if(fitness.fitness() >= Double.MAX_VALUE || fitness.fitness() >= Double.POSITIVE_INFINITY){
//					notBadMaxIndex = ind;
//					break;
//				}
//			}
//		}
//		if(notBadMaxIndex < fixedClusterElementSize){
//			clusterElementSize = notBadMaxIndex;
//		}
//		else{
//			clusterElementSize = fixedClusterElementSize;
//		}

		clusterElementSize = fixedClusterElementSize;
//		clusterElementSize = notBadMaxIndex;
		System.out.println("clusterElementSize: " + clusterElementSize);


//		//modified by mengxu 2021.06.29 just use the top 300 to do parent=======
//		double[][] indsCharListsMultiTree300 = Arrays.copyOfRange(indsCharListsMultiTree[0],  0 ,  clusterElementSize );
//		double[] fitnessesForModel300 = Arrays.copyOfRange(fitnessesForModel[0],  0 ,  clusterElementSize);
////		System.out.println("length: " + fitnessesForModel300.length);
//		List<CustomerPoint> customerPoints = buildCustomerPoint(indsCharListsMultiTree300, fitnessesForModel300, clusterWithFitness);
//		//===================================

		//modified by mengxu 2021.06.29 just use the top 300 to do parent=======
		int[][][] indsCharListsMultiTree300 = Arrays.copyOfRange(indsCharListsMultiTree[0],  0 ,  clusterElementSize );
		double[] fitnessesForModel300 = Arrays.copyOfRange(fitnessesForModel[0],  0 ,  clusterElementSize);
//		System.out.println("length: " + fitnessesForModel300.length);
		List<CustomerPointPermutation> customerPoints = buildCustomerPointPermutation(indsCharListsMultiTree300, fitnessesForModel300, clusterWithFitness);
		//===================================

		//original
		//this is suitable for only one subpop
//		List<CustomerPoint> customerPoints = buildCustomerPoint(indsCharListsMultiTree[0], fitnessesForModel[0], true);

		//DBS cluster---------------------------------
//		DistanceType distanceType = DistanceType.BinaryBring;
//		DBScan db = null; //返回结果并打印
//		try {
//			db = new DBScan(0, 0, distanceType);//how to set the distance, the minPoints must be set to 0, because if not 0, then some point will be set as noise, and will not be included in the cluster
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		List<List<CustomerPoint>> aa = db.cluster(customerPoints);
//		cluster = aa;

		//DBS cluster end------------------------------

		//Kmeans cluster--------------------------------
//		DistanceType distanceType = DistanceType.BinaryBring;
//		KMeans kRun =new KMeans(clusterSize, customerPoints, distanceType);
//		Set<Cluster> clusterSet = kRun.run();
//		List<List<CustomerPoint>> aa = kRun.getCluster(clusterSet);
//		cluster = aa;
		//2021.06.23 modified by mengxu cluster size adaptation------
//		System.out.println("clusterThreshold: " + clusterThreshold);
//		List<CustomerPoint> centerList = new ArrayList<>();
//		for(Cluster clus:clusterSet){
//			centerList.add(clus.getCenter());
//		}
//		for(int i=0;i<aa.size();i++){
//			double minDis = Double.MAX_VALUE;
//			int minIndex = 0;
//			int eachClusterSize = aa.get(i).size();
//			if(eachClusterSize < clusterThreshold){
//				for(int ref=0; ref<eachClusterSize; ref++){
//					CustomerPoint p1 = aa.get(i).get(ref);
//					for(int j=0; j<centerList.size(); j++){
//						if(i != j){//modified by mengxu 2021.12.28
//							if(distanceType == DistanceType.PearsonCorrelation){
//								if(minDis > p1.distanceFromPearson(centerList.get(j))){
//									minDis = p1.distanceFromBinaryBring(centerList.get(j));
//									minIndex = j;
//								}
//							}
//							else if(distanceType == DistanceType.BinaryBring){
//								if(minDis > p1.distanceFromBinaryBring(centerList.get(j))){
//									minDis = p1.distanceFromBinaryBring(centerList.get(j));
//									minIndex = j;
//								}
//							}
//							else if(distanceType == DistanceType.Euclidean){
//								//original
//								if(minDis > p1.distanceFrom(centerList.get(j))){
//									minDis = p1.distanceFrom(centerList.get(j));
//									minIndex = j;
//								}
//							}
//						}
//					}
//					aa.get(minIndex).add(p1);
//				}
//				aa.remove(i);
//				centerList.remove(i);
//				i--;
//			}
//		}
		//------------------------------------------------
		//Kmeans cluster--------------------------------
		DistanceType distanceType = DistanceType.BinaryBring;
		KMeansPermutation kRun =new KMeansPermutation(clusterSize, customerPoints, distanceType);
		Set<ClusterPermutation> clusterSet = kRun.run();
		List<List<CustomerPointPermutation>> aa = kRun.getCluster(clusterSet);
		cluster = aa;
		//2021.06.23 modified by mengxu cluster size adaptation------
		System.out.println("clusterThreshold: " + clusterThreshold);
		List<CustomerPointPermutation> centerList = new ArrayList<>();
		for(ClusterPermutation clus:clusterSet){
			centerList.add(clus.getCenter());
		}
		//=======================================
//		double clusterSimilar = 0;
//		int number = 0;
//		for(int i=0; i<centerList.size(); i++){
//			for(int j=i+1; j<centerList.size(); j++){
//				if(i != j){
//					CustomerPointPermutation centeri = centerList.get(i);
//					CustomerPointPermutation centerj = centerList.get(j);
//					double dis = centeri.distanceFromPearsonForEachDecision(centerj);
//					clusterSimilar += dis;
//					number++;
//					System.out.println("center " + i + " and center " + j + " dis: " + dis);
//				}
//			}
//		}
//		double diversity = clusterSimilar/number;
//		System.out.println("diversity: " + diversity);
		//========================================
		for(int i=0;i<aa.size();i++){
			double minDis = Double.MAX_VALUE;
			int minIndex = 0;
			int eachClusterSize = aa.get(i).size();
			if(eachClusterSize < clusterThreshold){
				for(int ref=0; ref<eachClusterSize; ref++){
					CustomerPointPermutation p1 = aa.get(i).get(ref);
					for(int j=0; j<centerList.size(); j++){
						if(i != j){//modified by mengxu 2021.12.28
							if(minDis > p1.distanceFromPearsonForEachDecision(centerList.get(j))){
								minDis = p1.distanceFromPearsonForEachDecision(centerList.get(j));
								minIndex = j;
							}
						}
					}
					aa.get(minIndex).add(p1);
				}
				aa.remove(i);
				centerList.remove(i);
				i--;
			}
		}
		//------------------------------------------------
		System.out.println("Iter times: " + kRun.getIterTimes());


//		System.out.println("Cluster size set in advance: " + clusterSize);
		System.out.println("Cluster size: " + cluster.size());
//		this.clusterSize = cluster.size();
//		clusterMeanFitness.clear();
//		for(int i=0; i<cluster.size(); i++){
//			double meanFitness = 0;
//			if(cluster.get(i).size() == 1){
//				meanFitness = cluster.get(i).get(0).getFitness();
//			}
//			else{
//				int numNoBad = 0;
//				for(int j=0; j<cluster.get(i).size(); j++){
//					if(cluster.get(i).get(j).getFitness() >= Double.POSITIVE_INFINITY ||
//							cluster.get(i).get(j).getFitness() >= Double.MAX_VALUE){
//						continue;
//					}
//					else{
//						meanFitness += cluster.get(i).get(j).getFitness();
//						numNoBad++;
//					}
//				}
//				if(numNoBad == 0){
//					meanFitness = Double.POSITIVE_INFINITY;
//				}
//				else{
//					meanFitness = meanFitness / numNoBad;
//				}
//
//			}
//			clusterMeanFitness.add(meanFitness);
////			if(meanFitness >= Double.POSITIVE_INFINITY || meanFitness >= Double.MAX_VALUE){
////				cluster.remove(i);
////				i--;
////			}
////			else{
////				clusterMeanFitness.add(meanFitness);
////			}
//
//		}
		// kmeans end ---------------------------------------

//		System.out.println("Cluster size after adaptation: " + cluster.size());
////		writeClusterToFile();
//		//-----------------------------------------------------------
//
//		int numInCluster = 0;
//		System.out.print("each cluster size: ");
//		for(int i=0;i<aa.size();i++){
//			numInCluster += aa.get(i).size();
//			System.out.print("  [" + aa.get(i).size() + ", " + clusterMeanFitness.get(i) + "]");
//		}
//		System.out.println();
//		System.out.println("Number in cluster: " + numInCluster);

//		int maxEnsemble = 5;
//		List<Individual> allDiverseIndividualFromCluster = getAllDiverseIndividualFromCluster(aa);
//		ensemble = getEnsemble(allDiverseIndividualFromCluster,maxEnsemble);
//		ensembleAll.add(ensemble);
//		System.out.print("ensemble fitness: ");
//		for(int i=0; i< ensemble.size();i++){
//			System.out.print(ensemble.get(i).fitness.fitness() + ",");
//		}
//		System.out.println();


		if(generation == 1 || generation == 25 || generation == 45){
			writeClusterPermutationToFile();
			writeParentsAndChildToFile(individuals);
		}

		//2021.10.14 to find the difference with Tournament selection======
		double[][] combinaiton = getCombination();
		ParentsCombinationForXcoverDiversity parentsX = new ParentsCombinationForXcoverDiversity();
		double parentXvalue = (double)parentsX.phenotypicCharacteristicDiversity(combinaiton);
		this.combinationNumber.add(parentXvalue);
		System.out.println("parents combination diversity: " + parentXvalue);
		parentsIndexPairForCrossover.clear();
		//=================================================================


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
			writeCombinationNumberToFile();
//			writeClusterToFile();//modified by mengxu 2021.05.08
//			writeParentsPearsonScoreToFile();
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

	public List<CustomerPointPermutation> buildCustomerPointPermutation(int[][][] subpopIndsCharListsMultiTree, double[] subpopFitnessesForModel, boolean useFitness){
		List<CustomerPointPermutation> csAll = new ArrayList<>();
		for(int indIndex=0; indIndex<subpopIndsCharListsMultiTree.length; indIndex++){
			CustomerPointPermutation cp = new CustomerPointPermutation(indIndex,subpopIndsCharListsMultiTree[indIndex], subpopFitnessesForModel[indIndex]);
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

	//2021.2.15 modified by mengxu
//	public void writeClusterToFile(){
//		File diversities = new File("job." + jobSeed + "." + generation +".clusters.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
//		try {
//			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
//			writer.write("Ind, Cluster, Fitness");
//			writer.newLine();
//			for (int ind = 0; ind < cluster.size(); ind++) {
//				List<CustomerPoint> ref = cluster.get(ind);
//				for(int i=0; i<ref.size(); i++){
//					CustomerPoint individual = ref.get(i);
//					if(individual.getFitness() >= Double.MAX_VALUE || individual.getFitness() >= Double.POSITIVE_INFINITY){
//						continue;
//					}
//					else{
//						writer.write(individual.getIndIndex() + "," + individual.getClusterid() + "," + individual.getFitness());
//						writer.newLine();
//					}
//				}
//			}
//
//			writer.close();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

	//2021.2.15 modified by mengxu
	public void writeClusterPermutationToFile(){
		File diversities = new File("job." + jobSeed + "." + generation +".clusters.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Ind, Cluster, Fitness");
			writer.newLine();
			for (int ind = 0; ind < cluster.size(); ind++) {
				List<CustomerPointPermutation> ref = cluster.get(ind);
				for(int i=0; i<ref.size(); i++){
					CustomerPointPermutation individual = ref.get(i);
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

	//2021.12.28 modified by mengxu
	public void writeParentsPearsonScoreToFile(){
		File diversities = new File("job." + jobSeed + ".parentspearsonscore.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Gen, Score");
			writer.newLine();
			for(int gen = 0; gen < parentsPearsonScore.size(); gen++) {
				List<Double> ref = parentsPearsonScore.get(gen);
				for(int i=0; i<ref.size(); i++){
					writer.write(gen + "," + ref.get(i));
					writer.newLine();
				}
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
			writer.write("Parent1ID, Parent1Fitness, Parent2ID, Parent2Fitness, parentsPearson, Child1Ind, Child1Fitness, Child2Ind, Child2Fitness");
			writer.newLine();
			for(ParentFairForCrossover ref: parentsIndexPairForCrossover){
				ref.setChild1Fitness(individuals[ref.getChild1ID()].fitness.fitness());
				if(ref.getChild2ID() != -1){
					ref.setChild2Fitness(individuals[ref.getChild2ID()].fitness.fitness());
				}
				writer.write( ref.getParent1ID()+ "," + ref.getParent1Fitness()+ "," + ref.getParent2ID()+ "," + ref.getParent2Fitness() + "," + ref.getParentsPearsonScore() + "," + ref.getChild1ID() + "," + ref.getChild1Fitness() + "," + ref.getChild2ID() + "," + ref.getChild2Fitness());
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
