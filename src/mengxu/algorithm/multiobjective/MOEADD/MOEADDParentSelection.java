package mengxu.algorithm.multiobjective.MOEADD;

import ec.EvolutionState;
import ec.Individual;
import ec.select.TournamentSelection;
import mengxu.algorithm.multiobjective.MOEAD.GPRuleEvolutionStateMOEAD;
import mengxu.algorithm.multiobjective.MOEAD.MOEADBreeder;
import mengxu.algorithm.multiobjective.MOEAD.MOEADInitializer;

public class MOEADDParentSelection extends TournamentSelection {
    private static final long serialVersionUID = 1L;

	@Override
	public int produce(final int min, final int max, final int start, final int subpopulation, final Individual[] inds,
					   final EvolutionState state, final int thread) {
		//original by fangfang------------------------
//		int n = 1; //todo: not right, for crossover should be two. need to modify by mengxu
//
//		inds[start] = state.population.subpops[subpopulation].individuals[produceMOEAD(start, subpopulation, state,
//				thread)];
//		return n;
		//------------------------------------

		//the following is modified by mengxu.-------------------------
		int n=INDS_PRODUCED;
		if (n<min) n = min;
		if (n>max) n = max;
		for(int q=0;q<n;q++)
		{
			inds[start+q] = state.population.subpops[subpopulation].
					individuals[produceMOEAD(start, subpopulation, state, thread)];
		}

		return n;
	}

//	@Override
//	public int produce(final int min, final int max, final int start, final int subpopulation, final Individual[] inds,
//                       final EvolutionState state, final int thread) {
//		//original by fangfang------------------------
////		int n = 1; //todo: not right, for crossover should be two. need to modify by mengxu
////
////		inds[start] = state.population.subpops[subpopulation].individuals[produceMOEAD(start, subpopulation, state,
////				thread)];
////		return n;
//		//------------------------------------
//
//		//the following is modified by mengxu.-------------------------
//		int n=INDS_PRODUCED;
//		if (n<min) n = min;
//		if (n>max) n = max;
//		for(int q=0;q<n;q++)
//		{
//			inds[start+q] = state.population.subpops[subpopulation].
//					individuals[produceMOEAD(start, subpopulation, state, thread)];
//		}
//
//		return n;
//	}

	public int produceMOEAD(final int start, final int subpopulation, final EvolutionState state, final int thread) {
		Individual[] oldinds = state.population.subpops[subpopulation].individuals;
		int best = getRandomIndividual(start, subpopulation, state, thread);

		int s = getTournamentSizeToUse(state.random[thread]);

		if (pickWorst)
			for (int x = 1; x < s; x++) {
				//modified 2021.10.13
//				best = getRandomIndividual(start, subpopulation, state, thread);
				int j = getRandomIndividual(start, subpopulation, state, thread);
				if (!betterThan(start, oldinds[j], oldinds[best], subpopulation, state, thread)) // j is at least as bad// as best
					best = j;
			}
		else
			for (int x = 1; x < s; x++) {
				//modified 2021.10.13
//				best = getRandomIndividual(start, subpopulation, state, thread);
				int j = getRandomIndividual(start, subpopulation, state, thread);
				if (betterThan(start, oldinds[j], oldinds[best], subpopulation, state, thread)) // j is better than best
					best = j;
			}

			//store the selected parent index in order to calculate the parent selection diversity!
		if(state instanceof GPRuleEvolutionStateMOEAD){
			((GPRuleEvolutionStateMOEAD)state).parentIndex.add(best);
		}
		return best;
	}

	public int getRandomIndividual(int subproblem, int subpopulation, EvolutionState state, int thread) {
		MOEADBreeder breeder = (MOEADBreeder) state.breeder;
		MOEADInitializer init = (MOEADInitializer) state.initializer;
		if(breeder.getNeighborType() == MOEADInitializer.NeighborType.NEIGHBOR){
			int neighbourIndex = state.random[thread].nextInt(init.neighborSize);
			int populationIndex = init.neighbourhood[subproblem][neighbourIndex];
			return populationIndex;
		}
		else{
			Individual[] oldinds = state.population.subpops[subpopulation].individuals;
			int populationIndex = state.random[thread].nextInt(oldinds.length);
			return populationIndex;
		}
//		int neighbourIndex = state.random[thread].nextInt(init.neighborSize);
//		int populationIndex = init.neighbourhood[subproblem][neighbourIndex];
//		return populationIndex;
	}

	// public int getRandomIndividual(int number, int subpopulation, EvolutionState
	// state, int thread)
	// {
	// Individual[] oldinds = state.population.subpops[subpopulation].individuals;
	// return state.random[thread].nextInt(oldinds.length);
	// }

	/**
	 * Returns true if *first* is a better (fitter, whatever) individual than
	 * *second*.
	 */
	public boolean betterThan(int subproblem, Individual first, Individual second, int subpopulation,
			EvolutionState state, int thread) {
		int index = subproblem;

		MOEADInitializer init = (MOEADInitializer) state.initializer;
		double firstScore;
		double secondScore;

		if (init.tchebycheff) {
			firstScore = init.calculateTchebycheffScore(first, index);
			secondScore = init.calculateTchebycheffScore(second, index);
		} else {
			firstScore = init.calculateWeightSumScore(first, index);
			secondScore = init.calculateWeightSumScore(second, index);
		}
		boolean betterThan = firstScore < secondScore;
		return betterThan;
	}

	// public boolean betterThan(int subproblem, Individual first, Individual
	// second, int subpopulation, EvolutionState state, int thread)
	// {
	// return first.fitness.betterThan(second.fitness);
	// }
}
