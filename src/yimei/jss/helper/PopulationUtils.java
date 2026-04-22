package yimei.jss.helper;

import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.lexicaseselection.LSmultiInstance.MultiInstanceMultiObjectiveFitness;
import mengxu.algorithm.lexicaseselection.LSmultiReplication.MultiReplicationMultiObjectiveFitness;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import mengxu.algorithm.multiobjective.oneInstanceMultiCaseLexicaseSelection.OneInstanceMultiCaseMultiObjectiveFitnessLSMO;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.Clusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.util.MathUtils;
import yimei.jss.gp.terminal.TerminalERCUniform;

import java.io.*;
import java.util.*;

public class PopulationUtils {
	/**
	 * Sorts individuals based on their fitness. The method iterates over all subpopulations
	 * and sorts the array of individuals in them based on their fitness so that the first
	 * individual has the best (i.e. least) fitness.
	 *
	 * @param pop a population to sort. Can't be {@code null}.
	 * @return the given pop with individuals sorted.
	 */
	public static Population sort(Population pop) {
		Comparator<Individual> comp = (Individual o1, Individual o2) ->
		{
			if (o1.fitness.fitness() < o2.fitness.fitness())
				return -1;
			if (o1.fitness.fitness() == o2.fitness.fitness())
				return 0;

			return 1;
		};

		for (Subpopulation subpop : pop.subpops)
			Arrays.sort(subpop.individuals, comp);

		return pop;
	}

	/**
	 * @param pop add by mengxu 2024.3.20 for Pareto set learning, sort based on PSLfitness
	 * @return
	 */
	public static Population sortForParetoSortLearning(Population pop) {
		Comparator<Individual> comp = (Individual o1, Individual o2) ->
		{
			//Strategy 0
			if(o1.fitness.betterThan(o2.fitness)){
				return -1;
			}
			else if(o1.fitness.equivalentTo(o2.fitness)){
				return 0;
			}
			else{
				return 1;
			}
			//strategy 1
//			if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getHVvalue() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getHVvalue()){
//				return -1;
//			}
//			else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getHVvalue() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getHVvalue()){
//				if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					return -1;
//				}
//				else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return -1;
//					}
//					else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return 0;
//					}
//					else{
//						return 1;
//					}
//				}
//				else{
//					return 1;
//				}
//			}
//			else{
//				return 1;
//			}

			//strategy 2
//			if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					return -1;
//				}
//				else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return -1;
//					}
//					else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return 0;
//					}
//					else{
//						return 1;
//					}
//				}
//				else{
//					return 1;
//				}

//			//Strategy 3
//			if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getRank() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getRank()){
//				return -1;
//			}
//			else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getRank() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getRank()){
//				if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					return -1;
//				}
//				else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return -1;
//					}
//					else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return 0;
//					}
//					else{
//						return 1;
//					}
//				}
//				else{
//					return 1;
//				}
//			}
//			else{
//				return 1;
//			}

//			//Strategy 4
//			if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getRank() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getRank()){
//				return -1;
//			}
//			else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getRank() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getRank()){
//				if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getHVvalue() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getHVvalue()){
//					return -1;
//				}
//				else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getHVvalue() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getHVvalue()){
//					if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//						return -1;
//					}
//					else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//						if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//							return -1;
//						}
//						else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//							return 0;
//						}
//						else{
//							return 1;
//						}
//					}
//					else{
//						return 1;
//					}
//				}
//				else{
//					return 1;
//				}
//			}
//			else{
//				return 1;
//			}
		};

		for (Subpopulation subpop : pop.subpops)
			Arrays.sort(subpop.individuals, comp);

		return pop;
	}

	public static Individual[] sortForParetoSortLearningOnlyParetoFront(Population pop, int numTopN) {
		Comparator<Individual> comp = (Individual o1, Individual o2) ->
		{
			//Strategy 0
			if(o1.fitness.betterThan(o2.fitness)){
				return -1;
			}
			else if(o1.fitness.equivalentTo(o2.fitness)){
				return 0;
			}
			else{
				return 1;
			}
			//Strategy 1
//			if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getHVvalue() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getHVvalue()){
//				return -1;
//			}
//			else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getHVvalue() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getHVvalue()){
//				if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					return -1;
//				}
//				else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return -1;
//					}
//					else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return 0;
//					}
//					else{
//						return 1;
//					}
//				}
//				else{
//					return 1;
//				}
//			}
//			else{
//				return 1;
//			}

			//Strategy 2
//			if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//				return -1;
//			}
//			else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//				if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//					return -1;
//				}
//				else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//					return 0;
//				}
//				else{
//					return 1;
//				}
//			}
//			else{
//				return 1;
//			}

//			//Strategy 3
//			if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getRank() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getRank()){
//				return -1;
//			}
//			else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getRank() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getRank()){
//				if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					return -1;
//				}
//				else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//					if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return -1;
//					}
//					else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//						return 0;
//					}
//					else{
//						return 1;
//					}
//				}
//				else{
//					return 1;
//				}
//			}
//			else{
//				return 1;
//			}

//			//Strategy 4
//			if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getRank() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getRank()){
//				return -1;
//			}
//			else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getRank() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getRank()){
//				if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getHVvalue() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getHVvalue()){
//					return -1;
//				}
//				else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getHVvalue() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getHVvalue()){
//					if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() < ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//						return -1;
//					}
//					else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPSLFitness() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPSLFitness()){
//						if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() > ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//							return -1;
//						}
//						else if(((ClearingPSLMultiObjectiveFitness)o1.fitness).getPreferenceDiversity() == ((ClearingPSLMultiObjectiveFitness)o2.fitness).getPreferenceDiversity()){
//							return 0;
//						}
//						else{
//							return 1;
//						}
//					}
//					else{
//						return 1;
//					}
//				}
//				else{
//					return 1;
//				}
//			}
//			else{
//				return 1;
//			}
		};

		MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(pop.subpops[0].individuals[0].fitness);
		// build front
		ArrayList front = typicalFitness.partitionIntoParetoFront(pop.subpops[0].individuals, null, null);

		if(front.size()>numTopN){
			Individual[] individualsInFront = new Individual[front.size()];
			for(int i=0; i<front.size(); i++){
				individualsInFront[i] = (Individual) front.get(i);
			}
			Arrays.sort(individualsInFront, comp);
			return individualsInFront;
		}
		else{
			Individual[] individualsInFront = new Individual[numTopN];
			for(int i=0; i<front.size(); i++){
				individualsInFront[i] = (Individual) front.get(i);
			}
			for (Subpopulation subpop : pop.subpops)
				Arrays.sort(subpop.individuals, comp);
			int start = front.size();
			while(start<numTopN){
				individualsInFront[start] = pop.subpops[0].individuals[start-front.size()];
				start++;
			}
			return individualsInFront;
		}
	}

	public static ArrayList<Individual> sortIndsAndReturnArray(ArrayList<Individual> individuals) {
		// Define the comparator for sorting based on fitness
		Comparator<Individual> comp = (Individual o1, Individual o2) -> {
			if (o1.fitness.fitness() < o2.fitness.fitness()) {
				return -1;
			}
			if (o1.fitness.fitness() == o2.fitness.fitness()) {
				return 0;
			}
			return 1;
		};

		// Sort the ArrayList using the comparator
		Collections.sort(individuals, comp);

		// Return the sorted ArrayList
		return individuals;
	}


	//2020.2.10 sort the individuals in the arraylist
	public static Individual[] sortInds(ArrayList<Individual> individuals) {
		Comparator<Individual> comp = (Individual o1, Individual o2) ->
		{
			if (o1.fitness.fitness() < o2.fitness.fitness())
				return -1;
			if (o1.fitness.fitness() == o2.fitness.fitness())
				return 0;

			return 1;
		};
		//convert arraylist to array
		Individual[] inds = individuals.toArray(new Individual[individuals.size()]);
		Arrays.sort(inds, comp);
		return inds;
	}

	//2021.5.8 sort the individuals in the list
	public static Individual[] sortInds(List<Individual> individuals) {
		Comparator<Individual> comp = (Individual o1, Individual o2) ->
		{
			if (o1.fitness.fitness() < o2.fitness.fitness())
				return -1;
			if (o1.fitness.fitness() == o2.fitness.fitness())
				return 0;

			return 1;
		};
		//convert arraylist to array
		Individual[] inds = individuals.toArray(new Individual[individuals.size()]);
		Arrays.sort(inds, comp);
		return inds;
	}

	//added by mengxu 2022.10.11
	public static Individual[] sortIndsForMOEADm2m(List<Individual> individuals) {
		Comparator<Individual> comp = (Individual o1, Individual o2) ->
		{
			if (o1.fitness.betterThan(o2.fitness))
				return 1;
			if (o1.fitness.equivalentTo(o2.fitness))
				return 0;

			return -1;
		};
		//convert arraylist to array
		Individual[] inds = individuals.toArray(new Individual[individuals.size()]);
		Arrays.sort(inds, comp);
		return inds;
	}

	static void Frequency(TerminalsStats stats, GPNode node) {
		if (node == null) {
			return;
		}

		if (node.children == null || node.children.length == 0) {  //1. a node does not have child is a terminal
			//2. the length of node's child is 0 (empty array)---it is a terminal
			stats.update(((TerminalERCUniform) node).getTerminal().name()); //read terminals
			return;
		}

		for (GPNode child : node.children) //repeat to check the terminals
			Frequency(stats, child);
	}

	public static ArrayList<HashMap<String, Integer>> Frequency(Population pop, int topInds) {
		sort(pop); //sort the subpop separately

		ArrayList<HashMap<String, Integer>> retval = new ArrayList<HashMap<String, Integer>>();
		for (Subpopulation subpop : pop.subpops)
			for (int i = 0; i < topInds; i++) {
				TerminalsStats stats = new TerminalsStats();
				Frequency(stats, ((GPIndividual) (subpop.individuals[i])).trees[0].child);
				retval.add(stats.getStats());
			}
		return retval;
	}


	public static void sort(Individual[] ind) {
		Comparator<Individual> comp = (Individual o1, Individual o2) ->
		{
			if (o1.fitness.fitness() < o2.fitness.fitness())
				return -1;
			if (o1.fitness.fitness() == o2.fitness.fitness())
				return 0;

			return 1;
		};

		Arrays.sort(ind, comp);
	}

	//modified by mengxu 2021.04.25 sort individuals based on the instance index
	public static void sortwithInstance(Individual[] ind, int indexInstance, int indexObjective) {
		Comparator<Individual> comp = (Individual o1, Individual o2) ->
		{
			if (((OneInstanceMultiCaseMultiObjectiveFitness) o1.fitness).getFitness(indexInstance, indexObjective) <
					((OneInstanceMultiCaseMultiObjectiveFitness) o2.fitness).getFitness(indexInstance, indexObjective))
				return -1;
			if (((OneInstanceMultiCaseMultiObjectiveFitness) o1.fitness).getFitness(indexInstance, indexObjective) ==
					((OneInstanceMultiCaseMultiObjectiveFitness) o2.fitness).getFitness(indexInstance, indexObjective))
				return 0;

			return 1;
		};

		Arrays.sort(ind, comp);
	}



//	public void prepareForWriting(Population population, Subpopulation sub) throws IOException
//	{
//		if(!isSaving)
//			throw new IOException("This object is not initialized for saving objects.");
//		if(output == null)
//		{
//			output = new ObjectOutputStream(new FileOutputStream(file));
//			output.writeInt(population.subpops.length);
//		}
//
//		output.writeInt(sub.individuals.length);
//	}

//	public void write(Individual ind) throws IOException
//	{
//		if(!isSaving)
//			throw new IOException("This object is not initialized for saving objects.");
//
//		output.writeObject(ind);
//	}

	public static void savePopulation(Population pop, String fileName)
			throws FileNotFoundException, IOException {
		File file = new File(fileName);
		if (file.exists())
			file.delete();
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
			int nSubPops = pop.subpops.length;
			oos.writeInt(nSubPops);
			for (Subpopulation subpop : pop.subpops) {
				int nInds = subpop.individuals.length;
				oos.writeInt(nInds);
				for (Individual ind : subpop.individuals) {
					oos.writeObject(ind);
				}
			}
		}
	}

	public static Population loadPopulation(File file)
			throws FileNotFoundException, IOException, ClassNotFoundException, InvalidObjectException {
		Population retval = new Population();
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
			int numSub = ois.readInt();
			retval.subpops = new Subpopulation[numSub];
			for (int subInd = 0; subInd < numSub; subInd++) {
				int numInd = ois.readInt();
				retval.subpops[subInd] = new Subpopulation();
				retval.subpops[subInd].individuals = new Individual[numInd];
				for (int indIndex = 0; indIndex < numInd; indIndex++) {
					Object ind = ois.readObject();
					if (!(ind instanceof Individual))
						throw new InvalidObjectException("The file contains an object that is not "
								+ "instance of Individual: " + ind.getClass().toString());
					retval.subpops[subInd].individuals[indIndex] = (Individual) ind;
				}
			}
		}

		return retval;
	}
//
//	public static ArrayList<Population> loadPopulation(String inputFileNamePath, int numGenerations)
//	{
//		ArrayList<Population> retval = new ArrayList<>();
//
//		for(int i = 0; i < numGenerations; i++)
//		{
//			Population p = PopulationUtils.loadPopulation(
//					Paths.get(inputFileNamePath, "population.gen." + i + ".bin").toFile());
//			p = PopulationUtils.sort(p);
//			double fit = p.subpops[0].individuals[0].fitness.fitness();
//			if(fit > maxFit)
//				maxFit = fit;
//			if(fit < minFit)
//				minFit = fit;
//			popList.add(p);
//		}
//
//		return retval;
//	}

	public static Population loadPopulation(String fileName)
			throws FileNotFoundException, IOException, ClassNotFoundException, InvalidObjectException {
		File file = new File(fileName);
		return loadPopulation(file);
	}


	//fzhang 2019.6.6 get the index of best individuals
	public static int getIndexOfbestInds(Population pop, int numSubPop) {
		{
			int best_index = 0;
			double best_fitness = pop.subpops[numSubPop].individuals[0].fitness.fitness();
			for (int ind = 0; ind < pop.subpops[numSubPop].individuals.length; ind++) {
				if (pop.subpops[numSubPop].individuals[ind].fitness.fitness() < best_fitness) {
					best_fitness = pop.subpops[numSubPop].individuals[ind].fitness.fitness();
					best_index = ind;
				}
			}
			return best_index;
		}
	}

	//modified by mengxu 2021.09.23
	public static int getIndexOfbestIndsInstanceNormalisation(List<double[][]> individualsFitness, int indexInstance) {
		int best_index = 0;
		double best_fitness = individualsFitness.get(0)[indexInstance][0];
		for (int ind = 0; ind < individualsFitness.size(); ind++) {
			if (individualsFitness.get(ind)[indexInstance][0] < best_fitness) {
				best_fitness = individualsFitness.get(ind)[indexInstance][0];
				best_index = ind;
			}
		}
		return best_index;
	}

	//modified by mengxu 2021.10.08
	public static int getIndexOfbestIndsInstanceNormalisationMOLS(List<double[]> individualsFitness, int indexInstance) {
		int best_index = 0;
		double best_fitness = individualsFitness.get(0)[indexInstance];
		for (int ind = 0; ind < individualsFitness.size(); ind++) {
			if (individualsFitness.get(ind)[indexInstance] < best_fitness) {
				best_fitness = individualsFitness.get(ind)[indexInstance];
				best_index = ind;
			}
		}
		return best_index;
	}


	//modified by mengxu 2021.01.11
	public static int getIndexOfWorstIndsInstance(Individual[] individuals, int indexInstance) {
		int worst_index = 0;
		double worst_fitness = 0;
		for (int ind = 0; ind < individuals.length; ind++) {
			double fit = ((OneInstanceMultiCaseMultiObjectiveFitness) individuals[ind].fitness).getFitness(indexInstance, 0);
			if (fit >= Double.MAX_VALUE || fit >= Double.POSITIVE_INFINITY) {
				continue;
			} else {
				if (fit > worst_fitness) {
					worst_fitness = fit;
					worst_index = ind;
				}
			}

		}
		return worst_index;
	}

	//modified by mengxu
	public static int getIndexOfbestNoveltyScoreIndsInstance(List<Individual> individuals, int indexInstance) {
		int best_index = 0;
		double best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitness) individuals.get(0).fitness).getNoveltyScore(indexInstance, 0);
		for (int ind = 0; ind < individuals.size(); ind++) {
			if (((OneInstanceMultiCaseMultiObjectiveFitness) individuals.get(ind).fitness).getNoveltyScore(indexInstance, 0) < best_fitness) {
				best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitness) individuals.get(ind).fitness).getNoveltyScore(indexInstance, 0);
				best_index = ind;
			}
		}
		return best_index;
	}

	//modified by mengxu 2021.01.11
	public static int getIndexOfbestIndsInstance(Individual[] individuals, int indexInstance) {
		int best_index = 0;
		double best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitness) individuals[0].fitness).getFitness(indexInstance, 0);
		for (int ind = 0; ind < individuals.length; ind++) {
			if (((OneInstanceMultiCaseMultiObjectiveFitness) individuals[ind].fitness).getFitness(indexInstance, 0) < best_fitness) {
				best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitness) individuals[ind].fitness).getFitness(indexInstance, 0);
				best_index = ind;
			}
		}
		return best_index;
	}

	//modified by mengxu 2021.05.31
	public static int getIndexOfbestIndsObjective(Individual[] individuals, int indexObjective) {
		{
			int best_index = 0;
			double best_fitness = ((MultiObjectiveFitness) individuals[0].fitness).objectives[indexObjective];
			for (int ind = 0; ind < individuals.length; ind++) {
				if (((MultiObjectiveFitness) individuals[ind].fitness).objectives[indexObjective] < best_fitness) {
					best_fitness = ((MultiObjectiveFitness) individuals[ind].fitness).objectives[indexObjective];
					best_index = ind;
				}
			}
			return best_index;
		}
	}

	//modified by mengxu
	public static int getIndexOfbestIndsInstance(List<Individual> individuals, int indexInstance) {
		if(individuals.get(0).fitness instanceof OneInstanceMultiCaseMultiObjectiveFitness){
			int best_index = 0;
			double best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitness) individuals.get(0).fitness).getFitness(indexInstance, 0);
			for (int ind = 0; ind < individuals.size(); ind++) {
				if (((OneInstanceMultiCaseMultiObjectiveFitness) individuals.get(ind).fitness).getFitness(indexInstance, 0) < best_fitness) {
					best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitness) individuals.get(ind).fitness).getFitness(indexInstance, 0);
					best_index = ind;
				}
			}
			return best_index;
		}
		else if(individuals.get(0).fitness instanceof OneInstanceMultiCaseMultiObjectiveFitnessLSMO){
			int best_index = 0;
			double best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(0).fitness).getFitness(indexInstance, 0);
			for (int ind = 0; ind < individuals.size(); ind++) {
				if (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(ind).fitness).getFitness(indexInstance, 0) < best_fitness) {
					best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(ind).fitness).getFitness(indexInstance, 0);
					best_index = ind;
				}
			}
			return best_index;
		}
		else if(individuals.get(0).fitness instanceof MultiReplicationMultiObjectiveFitness){
			int best_index = 0;
			double best_fitness = ((MultiReplicationMultiObjectiveFitness) individuals.get(0).fitness).getFitness(indexInstance, 0);
			for (int ind = 0; ind < individuals.size(); ind++) {
				if (((MultiReplicationMultiObjectiveFitness) individuals.get(ind).fitness).getFitness(indexInstance, 0) < best_fitness) {
					best_fitness = ((MultiReplicationMultiObjectiveFitness) individuals.get(ind).fitness).getFitness(indexInstance, 0);
					best_index = ind;
				}
			}
			return best_index;
		}
		else if(individuals.get(0).fitness instanceof MultiInstanceMultiObjectiveFitness){
			int best_index = 0;
			double best_fitness = ((MultiInstanceMultiObjectiveFitness) individuals.get(0).fitness).getFitness(indexInstance, 0);
			for (int ind = 0; ind < individuals.size(); ind++) {
				if (((MultiInstanceMultiObjectiveFitness) individuals.get(ind).fitness).getFitness(indexInstance, 0) < best_fitness) {
					best_fitness = ((MultiInstanceMultiObjectiveFitness) individuals.get(ind).fitness).getFitness(indexInstance, 0);
					best_index = ind;
				}
			}
			return best_index;
		}
		System.out.println("Error in PopulationUtils.java -> getIndexOfbestIndsInstance!");
		return 0;
	}

	//by Mengxu 2023.04.07
	public static int getIndexOfbestIndsInstanceBasedOnRank(List<Individual> individuals, int indexInstance) {
		if(individuals.get(0).fitness instanceof OneInstanceMultiCaseMultiObjectiveFitnessLSMO){
			int best_index = 0;
			double best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(0).fitness).multiCaseRank[indexInstance];
			for (int ind = 0; ind < individuals.size(); ind++) {
				if (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(ind).fitness).multiCaseRank[indexInstance] < best_fitness) {
					best_fitness = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(ind).fitness).multiCaseRank[indexInstance];
					best_index = ind;
				}
			}
			return best_index;
		}
		System.out.println("Error in PopulationUtils.java -> getIndexOfbestIndsInstance!");
		return 0;
	}

	//by Mengxu 2023.04.24
	public static int getIndexOfbestIndsInstanceBasedOnRankPlusSparsity(List<Individual> individuals, int indexInstance) {
		if(individuals.get(0).fitness instanceof OneInstanceMultiCaseMultiObjectiveFitnessLSMO){
			int best_index = 0;
			double rank = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(0).fitness).multiCaseRank[indexInstance];
			double sparsity = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(0).fitness).multiCaseSparsity[indexInstance];
			double best_fitness = rank + sparsity;
			for (int ind = 0; ind < individuals.size(); ind++) {
				double rank_ind = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(0).fitness).multiCaseRank[indexInstance];
				double sparsity_ind = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) individuals.get(0).fitness).multiCaseSparsity[indexInstance];
				double ind_fitness = rank_ind + sparsity_ind;
				if (ind_fitness < best_fitness) {
					best_fitness = ind_fitness;
					best_index = ind;
				}
			}
			return best_index;
		}
		System.out.println("Error in PopulationUtils.java -> getIndexOfbestIndsInstance!");
		return 0;
	}

	//modified by mengxu 2021.05.31
	public static int getIndexOfbestIndsObjective(List<Individual> individuals, int indexObjective) {
		{
			int best_index = 0;
			double best_fitness = ((MultiObjectiveFitness) individuals.get(0).fitness).objectives[indexObjective];
			for (int ind = 0; ind < individuals.size(); ind++) {
				if (((MultiObjectiveFitness) individuals.get(ind).fitness).objectives[indexObjective] < best_fitness) {
					best_fitness = ((MultiObjectiveFitness) individuals.get(ind).fitness).objectives[indexObjective];
					best_index = ind;
				}
			}
			return best_index;
		}
	}

	public static double correlationDiversity(int[][] PC)
	{
		double[][] PC_double = new double[PC.length][];
		//change to double
		for(int i=0; i<PC.length; i++){
			PC_double[i] = new double[PC[i].length];
			for(int k=0; k<PC[i].length; k++){
				PC_double[i][k] = (double)PC[i][k];
			}
		}

		ArrayList<Double> correlationList = new ArrayList<>();
		double correlationSum = 0;
		for(int i=0; i<PC_double.length; i++){
			double[] PC_double_i = PC_double[i];
			for(int j=i+1; j<PC_double.length; j++){
				double[] PC_double_j = PC_double[j];
				PearsonsCorrelation p = new PearsonsCorrelation();
				double correlationDiversity = 1 - p.correlation(PC_double_i,PC_double_j);
				correlationList.add(correlationDiversity);
				correlationSum += correlationDiversity;
			}
		}

		return correlationSum/correlationList.size(); //a larger entropy indicates a better diversity
	}

	public static double entropy(int[][] PC)
	{
//		List<GPIndsClusterable> pool = Arrays.stream(inds).map(
//				i -> new GPIndsClusterable(i, tree, filter, metric)).collect(Collectors.toList());

		List<DoublePoint> listPC = new ArrayList<>();
		for(int indpc = 0; indpc < PC.length; indpc++){
			listPC.add(new DoublePoint(PC[indpc]));
		}

		DBSCANClusterer<DoublePoint> clusterer = new DBSCANClusterer<>(0, 1);
		List<Cluster<DoublePoint>> clusters = clusterer.cluster(listPC);

		double entropy = 0;
		ArrayList<DoublePoint> noises = clusterer.getNoises();

		for(Cluster<DoublePoint> cluster : clusters)
		{
			int clusterSize = cluster.getPoints().size();
			if(clusterSize == 0) // for any reason
				continue;
			double p = clusterSize / ((double)listPC.size()); //there is the clusters have more than one points
			entropy += p * Math.log(p);
		}

		for(int i = 0; i < noises.size(); i++)
		{
			double p = 1 / ((double)listPC.size()); //noises indicate the clusters have only one point
			entropy += p * Math.log(p);
		}
//		nClusters = clusters.size();
//		nNoise = noises.size();

		return -entropy; //a larger entropy indicates a better diversity
	}

		//2021.4.16 calculate the diversity of individuals--based on phenotypic characteristic
		public static double entropy ( double[][][] PC)
		{
//		List<GPIndsClusterable> pool = Arrays.stream(inds).map(
//				i -> new GPIndsClusterable(i, tree, filter, metric)).collect(Collectors.toList());

			List<DoublePoint> listPC = new ArrayList<>();
			for (int indpc = 0; indpc < PC[0].length; indpc++) {
				listPC.add(new DoublePoint(PC[0][indpc]));
			}

			DBSCANClusterer<DoublePoint> clusterer = new DBSCANClusterer<>(0, 1);
			List<Cluster<DoublePoint>> clusters = clusterer.cluster(listPC);

			double entropy = 0;
			ArrayList<DoublePoint> noises = clusterer.getNoises();

			for (Cluster<DoublePoint> cluster : clusters) {
				int clusterSize = cluster.getPoints().size();
				if (clusterSize == 0) // for any reason
					continue;
				double p = clusterSize / ((double) listPC.size()); //there is the clusters have more than one points
				entropy += p * Math.log(p);
			}

			for (int i = 0; i < noises.size(); i++) {
				double p = 1 / ((double) listPC.size()); //noises indicate the clusters have only one point
				entropy += p * Math.log(p);
			}
//		nClusters = clusters.size();
//		nNoise = noises.size();

			return -entropy; //a larger entropy indicates a better diversity
		}
	}


class DBSCANClusterer<T extends Clusterable> extends Clusterer<T> {
	private final double eps;
	private final int minPts;

	public DBSCANClusterer(double eps, int minPts) throws NotPositiveException {
		this(eps, minPts, new EuclideanDistance());
	}

	public DBSCANClusterer(double eps, int minPts, DistanceMeasure measure) throws NotPositiveException {
		super(measure);
		if (eps < 0.0D) {
			throw new NotPositiveException(eps);
		} else if (minPts < 0) {
			throw new NotPositiveException(minPts);
		} else {
			this.eps = eps;
			this.minPts = minPts;
		}
	}

	public double getEps() {
		return this.eps;
	}

	public int getMinPts() {
		return this.minPts;
	}

	Map<T, PointStatus> visited;

	public List<Cluster<T>> cluster(Collection<T> points) throws NullArgumentException {
		MathUtils.checkNotNull(points);
		List<Cluster<T>> clusters = new ArrayList();
		Iterator<T> i = points.iterator();
		visited = new HashMap<>();

		while(i.hasNext()) {
			T point = i.next();
			if (visited.get(point) == null) {
				List<T> neighbors = this.getNeighbors(point, points);
				if (neighbors.size() >= this.minPts) {
					Cluster<T> cluster = new Cluster<>();
					clusters.add(this.expandCluster(cluster, point, neighbors, points, visited));
				} else {
					visited.put(point, PointStatus.NOISE);
				}
			}
		}

		return clusters;
	}

	private Cluster<T> expandCluster(Cluster<T> cluster, T point, List<T> neighbors, Collection<T> points, Map<T, PointStatus> visited) {
		cluster.addPoint(point);
		visited.put(point, PointStatus.PART_OF_CLUSTER);
		List<T> seeds = new ArrayList(neighbors);

		for(int index = 0; index < (seeds).size(); ++index) {
			T current = (seeds).get(index);
			PointStatus pStatus = (PointStatus)visited.get(current);
			if (pStatus == null) {
				List<T> currentNeighbors = this.getNeighbors(current, points);
				if (currentNeighbors.size() >= this.minPts) {
					seeds = this.merge(seeds, currentNeighbors);
				}
			}

			if (pStatus != PointStatus.PART_OF_CLUSTER) {
				visited.put(current, PointStatus.PART_OF_CLUSTER);
				cluster.addPoint(current);
			}
		}

		return cluster;
	}

	private List<T> getNeighbors(T point, Collection<T> points) {
		List<T> neighbors = new ArrayList();
		Iterator<T> i$ = points.iterator();

		while(i$.hasNext()) {
			T neighbor = i$.next();
			if (point != neighbor && this.distance(neighbor, point) <= this.eps) {
				neighbors.add(neighbor);
			}
		}

		return neighbors;
	}

	private List<T> merge(List<T> one, List<T> two) {
		Set<T> oneSet = new HashSet<T>(one);
		Iterator<T> i$ = two.iterator();

		while(i$.hasNext()) {
			T item = i$.next();
			if (!oneSet.contains(item)) {
				one.add(item);
			}
		}

		return one;
	}

	public ArrayList<T> getNoises()
	{
		ArrayList<T> retval = new ArrayList<>();
		if(visited == null)
			return retval;

		visited.forEach((clusterable, pointStatus) -> {if(pointStatus == PointStatus.NOISE) retval.add(clusterable);});

		return retval;
	}

	private static enum PointStatus {
		NOISE,
		PART_OF_CLUSTER;

		private PointStatus() {
		}
	}
}



class TerminalsStats {
	private HashMap<String, Integer> stats = new HashMap<>();

	public void update(String nodeName) {
		if (stats.containsKey(nodeName) == false) {
			stats.put(nodeName, 0); // put: set the value
		}

		stats.put(nodeName, stats.get(nodeName) + 1);
	}

	public HashMap<String, Integer> getStats() {
		return stats;
	}
}


