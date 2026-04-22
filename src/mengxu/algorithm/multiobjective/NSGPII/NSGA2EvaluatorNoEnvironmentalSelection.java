/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multiobjective.NSGPII;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
//import ec.multiobjective.nsga2.NSGA2Breeder;
import ec.multiobjective.nsga2.NSGA2Evaluator;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;
import ec.util.SortComparator;
import mengxu.algorithm.multiobjective.GPRuleEvolutionStateMO;
import mengxu.algorithm.multiobjective.NSGPII.zScore.NSGA2MultiObjectiveFitnessNormalisation;

import java.util.ArrayList;
import java.util.List;

public class NSGA2EvaluatorNoEnvironmentalSelection extends NSGA2Evaluator {

	public void evaluatePopulation(final EvolutionState state) {
		super.evaluatePopulation(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals
		//do z-score normalisation by mengxu 2023.04.21
		if(((GPRuleEvolutionStateMO)state).useZScoreNormalisation){
			this.zScoreSigmoidNormalisation(state);
		}

		for (int x = 0; x < state.population.subpops.length; x++)
			state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
	}

	public void assignSparsity(Individual[] front) {
		int numObjectives = ((NSGA2MultiObjectiveFitness) front[0].fitness).getObjectives().length;

		for (int i = 0; i < front.length; i++)
			((NSGA2MultiObjectiveFitness) front[i].fitness).sparsity = 0;

		for (int i = 0; i < numObjectives; i++) {
			final int o = i;
			// 1. Sort front by each objective.
			// 2. Sum the manhattan distance of an individual's neighbours over
			// each objective.
			// NOTE: No matter which objectives objective you sort by, the
			// first and last individuals will always be the same (they maybe
			// interchanged though). This is because a Pareto front's
			// objective values are strictly increasing/decreasing.
			ec.util.QuickSort.qsort(front, new SortComparator() {
				public boolean lt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((NSGA2MultiObjectiveFitness) i1.fitness)
							.getObjective(o) < ((NSGA2MultiObjectiveFitness) i2.fitness).getObjective(o));
				}

				public boolean gt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((NSGA2MultiObjectiveFitness) i1.fitness)
							.getObjective(o) > ((NSGA2MultiObjectiveFitness) i2.fitness).getObjective(o));
				}
			});

			//fzhang 2018.11.22 normize the objective to calculate the sparsity
			final double min = ((NSGA2MultiObjectiveFitness) front[0].fitness).getObjective(o);
			final double max = ((NSGA2MultiObjectiveFitness) front[front.length - 1].fitness).getObjective(o);
			//todo: this means the NSGPII still use the fitness normalisation 2023.02.14 by mengxu
			//todo: might modify this part

//            System.out.println("normalised min: " + min );
//            System.out.println("normalised max: " + max );

			// Compute and assign sparsity.
			// the first and last individuals are the sparsest.
			((NSGA2MultiObjectiveFitness) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
			((NSGA2MultiObjectiveFitness) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
			for (int j = 1; j < front.length - 1; j++) {
				NSGA2MultiObjectiveFitness f_j = (NSGA2MultiObjectiveFitness) (front[j].fitness);
				NSGA2MultiObjectiveFitness f_jplus1 = (NSGA2MultiObjectiveFitness) (front[j + 1].fitness);
				NSGA2MultiObjectiveFitness f_jminus1 = (NSGA2MultiObjectiveFitness) (front[j - 1].fitness);

//				System.out.println("original min: " + f_j.minObjective[o] );
//			    System.out.println("original max: " + f_j.maxObjective[o] );

				if (max ==  min)
				{
					f_j.sparsity += 0;
				}
				else {
					// store the NSGA2Sparsity in sparsity
//				if((f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min) < 0){
//					System.out.println("Error here!");
//				}
					f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min);
				}

				//original version
//				System.out.println(f_j.maxObjective[o] - f_j.minObjective[o]);  //1
				// store the NSGA2Sparsity in sparsity
				/*f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o))
						/ (f_j.maxObjective[o] - f_j.minObjective[o]);*/
			}
		}
	}

	public void assignSparsityWithZscoreNormalisation(Individual[] front) {
		int numObjectives = ((NSGA2MultiObjectiveFitnessNormalisation) front[0].fitness).getObjectives().length;

		for (int i = 0; i < front.length; i++)
			((NSGA2MultiObjectiveFitnessNormalisation) front[i].fitness).sparsity = 0;

		for (int i = 0; i < numObjectives; i++) {
			final int o = i;
			// 1. Sort front by each objective.
			// 2. Sum the manhattan distance of an individual's neighbours over
			// each objective.
			// NOTE: No matter which objectives objective you sort by, the
			// first and last individuals will always be the same (they maybe
			// interchanged though). This is because a Pareto front's
			// objective values are strictly increasing/decreasing.
			ec.util.QuickSort.qsort(front, new SortComparator() {
				public boolean lt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((NSGA2MultiObjectiveFitnessNormalisation) i1.fitness)
							.getNormalisedObjectives(o) < ((NSGA2MultiObjectiveFitnessNormalisation) i2.fitness).getNormalisedObjectives(o));
				}

				public boolean gt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((NSGA2MultiObjectiveFitnessNormalisation) i1.fitness)
							.getNormalisedObjectives(o) > ((NSGA2MultiObjectiveFitnessNormalisation) i2.fitness).getNormalisedObjectives(o));
				}
			});

			//fzhang 2018.11.22 normize the objective to calculate the sparsity
			final double min = ((NSGA2MultiObjectiveFitnessNormalisation) front[0].fitness).getNormalisedObjectives(o);
			final double max = ((NSGA2MultiObjectiveFitnessNormalisation) front[front.length - 1].fitness).getNormalisedObjectives(o);
			//todo: this means the NSGPII still use the fitness normalisation 2023.02.14 by mengxu
			//todo: might modify this part

//            System.out.println("normalised min: " + min );
//            System.out.println("normalised max: " + max );

			// Compute and assign sparsity.
			// the first and last individuals are the sparsest.
			((NSGA2MultiObjectiveFitnessNormalisation) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
			((NSGA2MultiObjectiveFitnessNormalisation) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
			for (int j = 1; j < front.length - 1; j++) {
				NSGA2MultiObjectiveFitnessNormalisation f_j = (NSGA2MultiObjectiveFitnessNormalisation) (front[j].fitness);
				NSGA2MultiObjectiveFitnessNormalisation f_jplus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[j + 1].fitness);
				NSGA2MultiObjectiveFitnessNormalisation f_jminus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[j - 1].fitness);

				int index_plus = j+1;
				//modified by mengxu 2023.004.18
				while(index_plus < front.length-1 && f_jplus1.getNormalisedObjectives(o) == f_j.getNormalisedObjectives(o)){
					index_plus = index_plus + 1;
					f_jplus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[index_plus].fitness);
				}

				int index_minus = j-1;
				//modified by mengxu 2023.004.18
				while(index_minus > 0 && f_jminus1.getNormalisedObjectives(o) == f_j.getNormalisedObjectives(o)){
					index_minus = index_minus - 1;
					f_jminus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[index_minus].fitness);
				}

//				System.out.println("original min: " + f_j.minObjective[o] );
//			    System.out.println("original max: " + f_j.maxObjective[o] );

				if (max ==  min)
				{
					f_j.sparsity += 0;
				}
				else {
					// store the NSGA2Sparsity in sparsity
//				if((f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min) < 0){
//					System.out.println("Error here!");
//				}
					f_j.sparsity += (f_jplus1.getNormalisedObjectives(o) - f_jminus1.getNormalisedObjectives(o));
				}

				//original version
//				System.out.println(f_j.maxObjective[o] - f_j.minObjective[o]);  //1
				// store the NSGA2Sparsity in sparsity
				/*f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o))
						/ (f_j.maxObjective[o] - f_j.minObjective[o]);*/
			}
		}
	}

	public ArrayList assignFrontRanks(Subpopulation subpop) {
		Individual[] inds = subpop.individuals; //inds includes all individuals
		ArrayList frontsByRank = MultiObjectiveFitness.partitionIntoRanks(inds);

		int numRanks = frontsByRank.size();
		for (int rank = 0; rank < numRanks; rank++) {
			ArrayList front = (ArrayList) (frontsByRank.get(rank));
			int numInds = front.size();
			for (int ind = 0; ind < numInds; ind++)
				((NSGA2MultiObjectiveFitness) (((Individual) (front.get(ind))).fitness)).rank = rank;
		}
		return frontsByRank;
	}

	public static ArrayList partitionIntoParetoFront(Individual[] inds, ArrayList front, ArrayList nonFront)
	{
		if (front == null)
			front = new ArrayList();

		// put the first guy in the front
		front.add(inds[0]);

		// iterate over all the remaining individuals
		for (int i = 1; i < inds.length; i++)
		{
			Individual ind = (Individual) (inds[i]);

			boolean noOneWasBetter = true;
			int frontSize = front.size();

			// iterate over the entire front
			for (int j = 0; j < frontSize; j++)
			{
				Individual frontmember = (Individual) (front.get(j));

				// if the front member is better than the individual, dump the individual and go to the next one
				if (((MultiObjectiveFitness) (frontmember.fitness)).paretoDominates((MultiObjectiveFitness) (ind.fitness)))
				{
					if (nonFront != null) nonFront.add(ind);
					noOneWasBetter = false;
					break;  // failed.  He's not in the front
				}
				// if the individual was better than the front member, dump the front member.  But look over the
				// other front members (don't break) because others might be dominated by the individual as well.
				else if (((MultiObjectiveFitness) (ind.fitness)).paretoDominates((MultiObjectiveFitness) (frontmember.fitness)))
				{
					yank(j, front);
					// a front member is dominated by the new individual.  Replace him
					frontSize--; // member got removed
					j--;  // because there's another guy we now need to consider in his place
					if (nonFront != null) nonFront.add(frontmember);
				}
			}
			if (noOneWasBetter)
				front.add(ind);
		}
		return front;
	}

	static void yank(int val, ArrayList list)
	{
		int size = list.size();
		list.set(val, list.get(size - 1));
		list.remove(size - 1);
	}

	/**
	 * Build the auxiliary fitness data and reduce the subpopulation to just the
	 * archive, which is returned.
	 */
	//achieve a archive has the same size of original population
	public Individual[] buildArchive(EvolutionState state, int subpop) {
		Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
		ArrayList ranks = assignFrontRanks(state.population.subpops[subpop]); //after this, get different several ranks
		//each rank cosists of the corresponding individuals
		ArrayList newSubpopulation = new ArrayList(); //a new one, size = 0
		int size = ranks.size();
		for (int i = 0; i < size; i++) { //do for each rank separately
			Individual[] rank = (Individual[]) ((ArrayList) (ranks.get(i))).toArray(dummy);
//			if(rank.length > 5){
//				System.out.print("rank size > 5");
//			}
//			assignSparsity(rank); //assign sparity value for each individual in this rank
//			System.out.println("Print HERE!!!!!!");
			if(((GPRuleEvolutionStateMO)state).useZScoreNormalisation){
				assignSparsityWithZscoreNormalisation(rank); //modified by mengxu 2023.04.21
			}
			else{
				assignSparsity(rank); //assign sparity value for each individual in this rank
			}
			if (rank.length + newSubpopulation.size() >= originalPopSize[subpop]) {
				// first sort the rank by sparsity---from the small one to large one
				ec.util.QuickSort.qsort(rank, new SortComparator() {
					public boolean lt(Object a, Object b) { //Returns true if a < b, else false
						Individual i1 = (Individual) a;
						Individual i2 = (Individual) b;
						return (((NSGA2MultiObjectiveFitness) i1.fitness).sparsity > ((NSGA2MultiObjectiveFitness) i2.fitness).sparsity);
					}

					public boolean gt(Object a, Object b) { //Returns true if a > b, else false
						Individual i1 = (Individual) a;
						Individual i2 = (Individual) b;
						return (((NSGA2MultiObjectiveFitness) i1.fitness).sparsity < ((NSGA2MultiObjectiveFitness) i2.fitness).sparsity);
					}
				});

				// then put the m sparsest individuals in the new population
				int m = originalPopSize[subpop] - newSubpopulation.size(); //how many positions left for new individuals
				for (int j = 0; j < m; j++)
					newSubpopulation.add(rank[j]); //add some individuals based on the sparisity

				// and bail
				break;
			} else {
				// dump in everyone
				for (int j = 0; j < rank.length; j++) //add the while rank directly
					newSubpopulation.add(rank[j]);
			}
		}

		Individual[] archive = (Individual[]) (newSubpopulation.toArray(dummy));

		// maybe force reevaluation
		NSGA2BreederNoEnvironmentalSelection breeder = (NSGA2BreederNoEnvironmentalSelection) (state.breeder);
		if (breeder.reevaluateElites[subpop])
			for (int i = 0; i < archive.length; i++)
				archive[i].evaluated = false;

		return archive;
	}

	public void evaluatePopulation(final EvolutionState state, Boolean rankIndsIntermediatePop) {
		for (int x = 0; x < state.population.subpops.length; x++)
			state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
	}

	public void zScoreSigmoidNormalisation(EvolutionState state){
		int numObjectives = ((MultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness).objectives.length;
		double[] means = new double[numObjectives];
		double[] stds = new double[numObjectives];

		Individual[] individuals = state.population.subpops[0].individuals;
		List<List<Double>> objectives = new ArrayList<>();
		for(int j=0; j<numObjectives; j++){
			List<Double> obj = new ArrayList<>();
			objectives.add(obj);
			means[j] = 0;
			stds[j] = 0;
		}

		for(int i=0; i<individuals.length; i++){
			double[] objectiveValues = ((MultiObjectiveFitness)state.population.subpops[0].individuals[i].fitness).objectives;
			for(int j=0; j<numObjectives; j++){
				List<Double> obj = objectives.get(j);
				if(objectiveValues[j] >= Double.MAX_VALUE || objectiveValues[j] >= Double.POSITIVE_INFINITY){
				}else{
					obj.add(objectiveValues[j]);
					means[j] += objectiveValues[j];
				}
			}
		}
		for(int j=0; j<numObjectives; j++){
			List<Double> obj = objectives.get(j);
			means[j] = means[j]/obj.size();
		}
		for(int i=0; i<individuals.length; i++){
			double[] objectiveValues = ((MultiObjectiveFitness)state.population.subpops[0].individuals[i].fitness).objectives;
			for(int j=0; j<numObjectives; j++){
				List<Double> obj = objectives.get(j);
				if(objectiveValues[j] >= Double.MAX_VALUE || objectiveValues[j] >= Double.POSITIVE_INFINITY){
				}else{
					stds[j] += Math.pow((objectiveValues[j]-means[j]),2);
				}
			}
		}
		for(int j=0; j<numObjectives; j++){
			List<Double> obj = objectives.get(j);
			stds[j] = Math.sqrt(stds[j]/obj.size());
		}
		for(int i=0; i<individuals.length; i++){
			NSGA2MultiObjectiveFitnessNormalisation fitnessNormalisation = (NSGA2MultiObjectiveFitnessNormalisation)state.population.subpops[0].individuals[i].fitness;
			fitnessNormalisation.normalisation(means, stds);
		}
	}
}
