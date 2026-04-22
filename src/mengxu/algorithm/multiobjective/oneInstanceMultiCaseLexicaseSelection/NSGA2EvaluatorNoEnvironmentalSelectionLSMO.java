/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multiobjective.oneInstanceMultiCaseLexicaseSelection;

import ec.EvolutionState;
import ec.Individual;
import ec.Subpopulation;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2Evaluator;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.multiobjective.GPRuleEvolutionStateMO;
import mengxu.algorithm.multiobjective.NSGPII.NSGA2BreederNoEnvironmentalSelection;
import mengxu.algorithm.multiobjective.NSGPII.zScore.NSGA2MultiObjectiveFitnessNormalisation;
import mengxu.algorithm.multiobjective.indicatorBasedMO.IBMOMultiObjectiveFitness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NSGA2EvaluatorNoEnvironmentalSelectionLSMO extends NSGA2Evaluator {

	public void evaluatePopulation(final EvolutionState state) {
		super.evaluatePopulation(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals
		//do z-score normalisation by mengxu 2023.04.21
		if(((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).useZScoreNormalisation){
			this.zScoreSigmoidNormalisation(state);
			this.zScoreSigmoidNormalisationMultiCase(state);
		}

		for (int x = 0; x < state.population.subpops.length; x++)
			state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
	}
	public void assignSparsityMultiCase(Individual[] front, final int indexCase) {
		int numObjectives = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).getObjectives().length;
		int numCase = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).multiInstanceMultiObjectiveFitness.length;

//		for(int c=0; c<numCase; c++){
//			final int indexCase = c; //todo: double check
			for (int i = 0; i < front.length; i++) {
				((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[i].fitness).multiCaseSparsity[indexCase] = 0;
			}

			for (int i = 0; i < numObjectives; i++) {
				final int o = i; //todo: double check
				// 1. Sort front by each objective.
				// 2. Sum the manhattan distance of an individual's neighbours over
				// each objective.
				// NOTE: No matter which objectives objective you sort by, the
				// first and last individuals will always be the same (they maybe
				// interchanged though). This is because a Pareto front's
				// objective values are strictly increasing/decreasing.
				QuickSort.qsort(front, new SortComparator() {
					public boolean lt(Object a, Object b) {
						Individual i1 = (Individual) a;
						Individual i2 = (Individual) b;
						return (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i1.fitness)
								.multiInstanceMultiObjectiveFitness[indexCase][o] < ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i2.fitness).multiInstanceMultiObjectiveFitness[indexCase][o]);
					}

					public boolean gt(Object a, Object b) {
						Individual i1 = (Individual) a;
						Individual i2 = (Individual) b;
						return (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i1.fitness)
								.multiInstanceMultiObjectiveFitness[indexCase][o] > ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i2.fitness).multiInstanceMultiObjectiveFitness[indexCase][o]);
					}
				});

				//fzhang 2018.11.22 normize the objective to calculate the sparsity
				final double min = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).multiInstanceMultiObjectiveFitness[indexCase][o];
				final double max = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[front.length - 1].fitness).multiInstanceMultiObjectiveFitness[indexCase][o];
				//todo: this means the NSGPII still use the fitness normalisation 2023.02.14 by mengxu
				//todo: might modify this part

				// Compute and assign sparsity.
				// the first and last individuals are the sparsest.
				((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).multiCaseSparsity[indexCase] = Double.POSITIVE_INFINITY;
				((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[front.length - 1].fitness).multiCaseSparsity[indexCase] = Double.POSITIVE_INFINITY;
				for (int j = 1; j < front.length - 1; j++) {
					OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_j = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j].fitness);
					OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jplus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j + 1].fitness);
					OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jminus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j - 1].fitness);

					int index_plus = j+1;
					//modified by mengxu 2023.004.18
					while(index_plus < front.length-1 && f_jplus1.multiInstanceMultiObjectiveFitness[indexCase][o] == f_j.multiInstanceMultiObjectiveFitness[indexCase][o]){
						index_plus = index_plus + 1;
						f_jplus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_plus].fitness);
					}

					int index_minus = j-1;
					//modified by mengxu 2023.004.18
					while(index_minus > 0 && f_jminus1.multiInstanceMultiObjectiveFitness[indexCase][o] == f_j.multiInstanceMultiObjectiveFitness[indexCase][o]){
						index_minus = index_minus - 1;
						f_jminus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_minus].fitness);
					}

					if (max ==  min)
					{
						f_j.multiCaseSparsity[indexCase] += 0;
//						f_j.sparsity += 0;
					}
					else {
						f_j.multiCaseSparsity[indexCase] += (f_jplus1.multiInstanceMultiObjectiveFitness[indexCase][o] - f_jminus1.multiInstanceMultiObjectiveFitness[indexCase][o]) / (max- min);
//						f_j.sparsity += 0;
					}
				}
			}
//		}

		for (int j = 1; j < front.length; j++) {
			double sumSparsity = -1;
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_j = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j].fitness);
			for(int c=0; c<numCase; c++){
				if (!(f_j.multiCaseSparsity[c] >= Double.POSITIVE_INFINITY) && !(f_j.multiCaseSparsity[c] >= Double.MAX_VALUE)) {
					sumSparsity += f_j.multiCaseSparsity[c];
				}
			}
			if(sumSparsity == -1){
				f_j.sparsity = Double.POSITIVE_INFINITY;
			}
			else{
				f_j.sparsity = sumSparsity / numCase;
			}

		}
	}

	public void assignSparsityMultiCaseZScoreNormalisation(Individual[] front, final int indexCase) {
		int numObjectives = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).getObjectives().length;
		int numCase = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).multiInstanceMultiObjectiveFitness.length;

//		for(int c=0; c<numCase; c++){
//			final int indexCase = c; //todo: double check
		for (int i = 0; i < front.length; i++) {
			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[i].fitness).multiCaseSparsity[indexCase] = 0;
		}

		for (int i = 0; i < numObjectives; i++) {
			final int o = i; //todo: double check
			// 1. Sort front by each objective.
			// 2. Sum the manhattan distance of an individual's neighbours over
			// each objective.
			// NOTE: No matter which objectives objective you sort by, the
			// first and last individuals will always be the same (they maybe
			// interchanged though). This is because a Pareto front's
			// objective values are strictly increasing/decreasing.
			QuickSort.qsort(front, new SortComparator() {
				public boolean lt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i1.fitness)
							.multiInstanceMultiObjectiveFitness[indexCase][o] < ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i2.fitness).multiInstanceMultiObjectiveFitness[indexCase][o]);
				}

				public boolean gt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i1.fitness)
							.multiInstanceMultiObjectiveFitness[indexCase][o] > ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i2.fitness).multiInstanceMultiObjectiveFitness[indexCase][o]);
				}
			});

			//fzhang 2018.11.22 normize the objective to calculate the sparsity
			final double min = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).multiInstanceMultiObjectiveFitness[indexCase][o];
			final double max = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[front.length - 1].fitness).multiInstanceMultiObjectiveFitness[indexCase][o];
			//todo: this means the NSGPII still use the fitness normalisation 2023.02.14 by mengxu
			//todo: might modify this part

			// Compute and assign sparsity.
			// the first and last individuals are the sparsest.
//			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).multiCaseSparsity[indexCase] = Double.POSITIVE_INFINITY;
//			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[front.length - 1].fitness).multiCaseSparsity[indexCase] = Double.POSITIVE_INFINITY;

			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).multiCaseSparsity[indexCase] = 2;
			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[front.length - 1].fitness).multiCaseSparsity[indexCase] = 2;

			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jp0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[0].fitness);
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jm0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[front.length - 1].fitness);

			int index_plus0 = 0;
			int index_minus0 = front.length - 1;
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jplus0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_plus0].fitness);
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jminus0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_minus0].fitness);

			//modified by mengxu 2023.004.18
			while(index_plus0 < front.length-1 && f_jplus0.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o] == f_jp0.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o]){
				((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[index_plus0].fitness).multiCaseSparsity[indexCase] = 2;
				index_plus0 = index_plus0 + 1;
				f_jplus0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_plus0].fitness);
			}

			while(index_minus0 > 0 && f_jminus0.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o] == f_jm0.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o]){
				((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[index_minus0].fitness).multiCaseSparsity[indexCase] = 2;
				index_minus0 = index_minus0 - 1;
				f_jminus0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_minus0].fitness);
			}
			for (int j = index_plus0; j < index_minus0; j++) {
				OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_j = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j].fitness);
				OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jplus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j + 1].fitness);
				OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jminus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j - 1].fitness);

				int index_plus = j+1;
				//modified by mengxu 2023.004.18
				while(index_plus < index_minus0-1 && f_jplus1.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o] == f_j.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o]){
					index_plus = index_plus + 1;
					f_jplus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_plus].fitness);
				}

				int index_minus = j-1;
				//modified by mengxu 2023.004.18
				while(index_minus > index_plus+1 && f_jminus1.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o] == f_j.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o]){
					index_minus = index_minus - 1;
					f_jminus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_minus].fitness);
				}

				if (max ==  min)
				{
					f_j.multiCaseSparsity[indexCase] += 0;
//						f_j.sparsity += 0;
				}
				else {
					f_j.multiCaseSparsity[indexCase] += (f_jplus1.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o] - f_jminus1.normalisedmultiInstanceMultiObjectiveFitness[indexCase][o]);
//						f_j.sparsity += 0;
				}
			}
		}
//		}

		for (int j = 1; j < front.length; j++) {
			double sumSparsity = -1;
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_j = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j].fitness);
			for(int c=0; c<numCase; c++){
				if (!(f_j.multiCaseSparsity[c] >= Double.POSITIVE_INFINITY) && !(f_j.multiCaseSparsity[c] >= Double.MAX_VALUE)) {
					sumSparsity += f_j.multiCaseSparsity[c];
				}
			}
			if(sumSparsity == -1){
				f_j.sparsity = Double.POSITIVE_INFINITY;
			}
			else{
				f_j.sparsity = sumSparsity / numCase;
			}

		}
	}

	public ArrayList<ArrayList> assignFrontRanksMultiCase(Subpopulation subpop) {
		Individual[] inds = subpop.individuals; //inds includes all individuals
		ArrayList<ArrayList> frontsByRankMultiCase = new ArrayList<>();
		int numCase = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)inds[0].fitness).multiInstanceMultiObjectiveFitness.length;
		for(int i=0; i<numCase; i++){
			ArrayList frontsByRank = OneInstanceMultiCaseMultiObjectiveFitnessLSMO.partitionIntoRanksCase(inds, i);

			int numRanks = frontsByRank.size();
			for (int rank = 0; rank < numRanks; rank++) {
				ArrayList front = (ArrayList) (frontsByRank.get(rank));
				int numInds = front.size();
				for (int ind = 0; ind < numInds; ind++){
					//assign rank for each case
					((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (((Individual) (front.get(ind))).fitness)).multiCaseRank[i] = rank;
//					((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (((Individual) (front.get(ind))).fitness)).rank = rank;
				}
			}
			frontsByRankMultiCase.add(frontsByRank);
		}
		//alternatively by mengxu, to assign the rank of the individual on the whole instance is the mean of multicaseRank
		//todo: double check 2023.04.07
//		for (int ind = 0; ind < inds.length; ind++){
//			int[] multiCaseRank = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (((Individual) (inds[ind])).fitness)).multiCaseRank;
//			double sumRank = (double)Arrays.stream(multiCaseRank).sum()/multiCaseRank.length;
//			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (((Individual) (inds[ind])).fitness)).rank = sumRank;
//		}
		return frontsByRankMultiCase;
	}

	public ArrayList assignFrontRanks(Subpopulation subpop) {
		Individual[] inds = subpop.individuals; //inds includes all individuals
		ArrayList frontsByRank = MultiObjectiveFitness.partitionIntoRanks(inds);

		int numRanks = frontsByRank.size();
		for (int rank = 0; rank < numRanks; rank++) {
			ArrayList front = (ArrayList) (frontsByRank.get(rank));
			int numInds = front.size();
			for (int ind = 0; ind < numInds; ind++)
				((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (((Individual) (front.get(ind))).fitness)).rank = rank;
		}
		return frontsByRank;
	}

	/**
	 * Build the auxiliary fitness data and reduce the subpopulation to just the
	 * archive, which is returned.
	 */
	//achieve a archive has the same size of original population
	public Individual[] buildArchive(EvolutionState state, int subpop) {
		Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
		ArrayList ranksMultiCase = assignFrontRanksMultiCase(state.population.subpops[subpop]); //after this, get different several ranks
		//each rank cosists of the corresponding individuals
		//as we not use environmental selection anymore, we do not need to  build archive,
		//but still need to assign sparsity for each case and the whole sparsity by get the mean of sparsity of each case
		for(int r=0; r<ranksMultiCase.size(); r++){
			ArrayList ranksEachCase = (ArrayList)ranksMultiCase.get(r);
			int size = ranksEachCase.size();
			for (int i = 0; i < size; i++) { //do for each rank separately
				Individual[] rank = (Individual[]) ((ArrayList) (ranksEachCase.get(i))).toArray(dummy);
				if(((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).useZScoreNormalisation){
					assignSparsityMultiCaseZScoreNormalisation(rank, r); //modified by mengxu 2023.04.24
				}
				else{
					assignSparsityMultiCase(rank, r); //assign sparity value for each individual in this rank
				}
//				assignSparsityMultiCase(rank, r); //assign sparity value for each individual in this rank
			}
		}

		//added by mengxu 2023.04.12 for the whole population rank and sparsity assignment
		ArrayList ranks = assignFrontRanks(state.population.subpops[subpop]); //after this, get different several ranks
		//each rank cosists of the corresponding individuals
		int size = ranks.size();
		for (int i = 0; i < size; i++) { //do for each rank separately
			Individual[] rank = (Individual[]) ((ArrayList) (ranks.get(i))).toArray(dummy);
			if(((GPRuleEvolutionStateOneInstanceMultiCaseLSMO)state).useZScoreNormalisation){
				assignSparsityZScoreNormalisation(rank); //modified by mengxu 2023.04.24
			}
			else{
				assignSparsity(rank); //assign sparity value for each individual in this rank
			}
//			assignSparsity(rank); //assign sparity value for each individual in this rank
		}

		// maybe force reevaluation
		NSGA2BreederNoEnvironmentalSelection breeder = (NSGA2BreederNoEnvironmentalSelection) (state.breeder);
		if (breeder.reevaluateElites[subpop])
			for (int i = 0; i < state.population.subpops[subpop].individuals.length; i++)
				state.population.subpops[subpop].individuals[i].evaluated = false;

		return state.population.subpops[subpop].individuals;
	}

	public void assignSparsity(Individual[] front) {
		int numObjectives = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).getObjectives().length;

		//front here means different fronts
		for (int i = 0; i < front.length; i++) //front.length means how many individuals in this ranking(front)
			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[i].fitness).sparsity = 0; //the first individual in this front, the sparsity
		//is assigned to 0

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
				public boolean lt(Object a, Object b) { //less than
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i1.fitness)
							.getObjective(o) < ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i2.fitness).getObjective(o));
				}

				public boolean gt(Object a, Object b) { //great than
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i1.fitness)
							.getObjective(o) > ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i2.fitness).getObjective(o));
				}
			});

			// Compute and assign sparsity.
			// the first and last individuals are the sparsest.
			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
			for (int j = 1; j < front.length - 1; j++) {
				OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_j = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j].fitness);
				OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jplus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j + 1].fitness);
				OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jminus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j - 1].fitness);

//				System.out.println(f_j.maxObjective[o] - f_j.minObjective[o]);  //1
				// store the NSGA2Sparsity in sparsity
				f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o))
						/ (f_j.maxObjective[o] - f_j.minObjective[o]);
			}
		}
	}

	public void assignSparsityZScoreNormalisation(Individual[] front) {
		int numObjectives = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).getObjectives().length;

		//front here means different fronts
		for (int i = 0; i < front.length; i++) //front.length means how many individuals in this ranking(front)
			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[i].fitness).sparsity = 0; //the first individual in this front, the sparsity
		//is assigned to 0

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
				public boolean lt(Object a, Object b) { //less than
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i1.fitness)
							.getObjective(o) < ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i2.fitness).getObjective(o));
				}

				public boolean gt(Object a, Object b) { //great than
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i1.fitness)
							.getObjective(o) > ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) i2.fitness).getObjective(o));
				}
			});

			// Compute and assign sparsity.
			// the first and last individuals are the sparsest.
//			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
//			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
			//modified by mengxu 2023.04.24
			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[0].fitness).sparsity = 2;
			((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[front.length - 1].fitness).sparsity = 2;
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jp0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[0].fitness);
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jm0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[front.length - 1].fitness);
			int index_plus0 = 0;
			int index_minus0 = front.length - 1;
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jplus0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_plus0].fitness);
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jminus0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_minus0].fitness);

			//modified by mengxu 2023.004.18
			while(index_plus0 < front.length-1 && f_jplus0.getNormalisedObjectives(o) == f_jp0.getNormalisedObjectives(o)){
				((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[index_plus0].fitness).sparsity = 2;
				index_plus0 = index_plus0 + 1;
				f_jplus0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_plus0].fitness);
			}

			while(index_minus0 > 0 && f_jminus0.getNormalisedObjectives(o) == f_jm0.getNormalisedObjectives(o)){
				((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) front[index_minus0].fitness).sparsity = 2;
				index_minus0 = index_minus0 - 1;
				f_jminus0 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_minus0].fitness);
			}

			for (int j = index_plus0; j < index_minus0; j++) {
				OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_j = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j].fitness);
				OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jplus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j + 1].fitness);
				OneInstanceMultiCaseMultiObjectiveFitnessLSMO f_jminus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[j - 1].fitness);

				int index_plus = j+1;
				//modified by mengxu 2023.004.18
				while(index_plus < front.length-1 && f_jplus1.getNormalisedObjectives(o) == f_j.getNormalisedObjectives(o)){
					index_plus = index_plus + 1;
					f_jplus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_plus].fitness);
				}

				int index_minus = j-1;
				//modified by mengxu 2023.004.18
				while(index_minus > 0 && f_jminus1.getNormalisedObjectives(o) == f_j.getNormalisedObjectives(o)){
					index_minus = index_minus - 1;
					f_jminus1 = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) (front[index_minus].fitness);
				}

//				System.out.println(f_j.maxObjective[o] - f_j.minObjective[o]);  //1
				// store the NSGA2Sparsity in sparsity
				f_j.sparsity += (f_jplus1.getNormalisedObjectives(o) - f_jminus1.getNormalisedObjectives(o));
//				f_j.sparsity += (f_jplus1.getNormalisedObjectives(o) - f_jminus1.getNormalisedObjectives(o))
//						/ (f_j.maxObjective[o] - f_j.minObjective[o]);
			}
		}
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
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO fitnessNormalisation = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO)state.population.subpops[0].individuals[i].fitness;
			fitnessNormalisation.normalisation(means, stds);
		}
	}

	public void zScoreSigmoidNormalisationMultiCase(EvolutionState state){
		int row = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)state.population.subpops[0].individuals[0].fitness).multiInstanceMultiObjectiveFitness.length;
		int col = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)state.population.subpops[0].individuals[0].fitness).multiInstanceMultiObjectiveFitness[0].length;
		double[][] meansMultiCase = new double[row][col];
		double[][] stdsMultiCase = new double[row][col];

		Individual[] individuals = state.population.subpops[0].individuals;
		int numWithoutBadRun = 0;
		for(int i=0; i<row; i++) {
			for (int j = 0; j < col; j++) {
				meansMultiCase[i][j] = 0;
				stdsMultiCase[i][j] = 0;
			}
		}

		for(int i=0; i<individuals.length; i++) {
			double[][] oneInstanceMultiObjectiveFitness = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO) state.population.subpops[0].individuals[i].fitness).multiInstanceMultiObjectiveFitness;
			for (int r = 0; r < row; r++) {
				for (int c = 0; c < col; c++) {
					if (oneInstanceMultiObjectiveFitness[r][c] >= Double.MAX_VALUE || oneInstanceMultiObjectiveFitness[r][c] >= Double.POSITIVE_INFINITY) {
					} else {
						meansMultiCase[r][c] += oneInstanceMultiObjectiveFitness[r][c];
						numWithoutBadRun++;
					}
				}
			}
		}
		for(int r=0; r<row; r++) {
			for (int c = 0; c < col; c++) {
				meansMultiCase[r][c] = meansMultiCase[r][c] / numWithoutBadRun;
			}
		}
		for(int i=0; i<individuals.length; i++){
			double[][] oneInstanceMultiObjectiveFitness = ((OneInstanceMultiCaseMultiObjectiveFitnessLSMO)state.population.subpops[0].individuals[i].fitness).multiInstanceMultiObjectiveFitness;
			for(int r=0; r<row; r++) {
				for (int c = 0; c < col; c++) {
					if (oneInstanceMultiObjectiveFitness[r][c] >= Double.MAX_VALUE || oneInstanceMultiObjectiveFitness[r][c] >= Double.POSITIVE_INFINITY) {
					} else {
						stdsMultiCase[r][c] += Math.pow((oneInstanceMultiObjectiveFitness[r][c] - meansMultiCase[r][c]), 2);
					}
				}
			}
		}
		for(int r=0; r<row; r++) {
			for (int c = 0; c < col; c++) {
				stdsMultiCase[r][c] = Math.sqrt(stdsMultiCase[r][c] / numWithoutBadRun);
			}
		}
		for(int i=0; i<individuals.length; i++){
			OneInstanceMultiCaseMultiObjectiveFitnessLSMO fitnessNormalisation = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO)state.population.subpops[0].individuals[i].fitness;
			fitnessNormalisation.normalisationMultiCaseFitness(meansMultiCase, stdsMultiCase);
		}
	}

	public void evaluatePopulation(final EvolutionState state, Boolean rankIndsIntermediatePop) {
		for (int x = 0; x < state.population.subpops.length; x++)
			state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
	}
}
