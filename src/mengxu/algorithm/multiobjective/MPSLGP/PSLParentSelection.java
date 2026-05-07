package mengxu.algorithm.multiobjective.MPSLGP;

import ec.EvolutionState;
import ec.Individual;
import ec.select.TournamentSelection;

public class PSLParentSelection extends TournamentSelection {
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
		if (n == 2 && state instanceof GPRuleEvolutionStatePSL
				&& ((GPRuleEvolutionStatePSL) state).usePeerTaskTransfer()
				&& state.random[thread].nextDouble() < ((GPRuleEvolutionStatePSL) state).transferProbability) {
			int firstIndex = produceMOEAD(start, subpopulation, state, thread);
			int firstTask = ((GPRuleEvolutionStatePSL) state).getTaskIndex(state.population.subpops[subpopulation].individuals[firstIndex]);
			int secondIndex = produceMOEADFromDifferentTask(start, subpopulation, state, thread, firstTask);
			inds[start] = state.population.subpops[subpopulation].individuals[firstIndex];
			inds[start + 1] = state.population.subpops[subpopulation].individuals[secondIndex];
			return n;
		}
		for(int q=0;q<n;q++)
		{
			if(n==2){
				inds[start + q] = state.population.subpops[subpopulation].
						individuals[produceMOEAD(start, subpopulation, state, thread)];
			}
			else{
				inds[start] = state.population.subpops[subpopulation].
						individuals[produceMOEAD(start, subpopulation, state, thread)];
			}
		}

		return n;
	}

	public int produceMOEADFromDifferentTask(final int start, final int subpopulation, final EvolutionState state,
										  final int thread, final int excludedTask) {
		Individual[] oldinds = state.population.subpops[subpopulation].individuals;
		int best = -1;
		int s = getTournamentSizeToUse(state.random[thread]);
		for (int x = 0; x < s; x++) {
			int j = getRandomIndividual(start, subpopulation, state, thread);
			if (((GPRuleEvolutionStatePSL) state).getTaskIndex(oldinds[j]) == excludedTask) {
				continue;
			}
			if (best < 0 || betterThan(start, oldinds[j], oldinds[best], subpopulation, state, thread)) {
				best = j;
			}
		}
		if (best < 0) {
			best = produceMOEAD(start, subpopulation, state, thread);
		}
		if(state instanceof GPRuleEvolutionStatePSL){
			((GPRuleEvolutionStatePSL)state).recordSelectedParent(best);
		}
		return best;
	}



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
		if(state instanceof GPRuleEvolutionStatePSL){
				((GPRuleEvolutionStatePSL)state).recordSelectedParent(best);
		}
		return best;
	}

	public int getRandomIndividual(int subproblem, int subpopulation, EvolutionState state, int thread) {
		Individual[] oldinds = state.population.subpops[subpopulation].individuals;
		int populationIndex = state.random[thread].nextInt(oldinds.length);
		return populationIndex;
	}

	/**
	 * Returns true if *first* is a better (fitter, whatever) individual than
	 * *second*.
	 */
	public boolean betterThan(int subproblem, Individual first, Individual second, int subpopulation,
			EvolutionState state, int thread) {

		return first.fitness.betterThan(second.fitness);

//		if(first.fitness instanceof ClearingPSLMultiObjectiveFitness){
//			if(((ClearingPSLMultiObjectiveFitness)first.fitness).getPSLFitness()==((ClearingPSLMultiObjectiveFitness)second.fitness).getPSLFitness()){
//				return ((ClearingPSLMultiObjectiveFitness)first.fitness).getPreferenceDiversity()>((ClearingPSLMultiObjectiveFitness)second.fitness).getPreferenceDiversity();
//			}
//			return ((ClearingPSLMultiObjectiveFitness)first.fitness).getPSLFitness()<((ClearingPSLMultiObjectiveFitness)second.fitness).getPSLFitness();
//		}
//		else{
//			return ((PSLMultiObjectiveFitness)first.fitness).getPSLFitness()<((PSLMultiObjectiveFitness)second.fitness).getPSLFitness();
//		}



//		int index = subproblem;
//		PSLInitializer init = (PSLInitializer) state.initializer;
//		double firstScore;
//		double secondScore;
//
//		if (init.tchebycheff) {
//			firstScore = init.calculateTchebycheffScore(first, index);
//			secondScore = init.calculateTchebycheffScore(second, index);
//		} else {
//			firstScore = init.calculateWeightSumScore(first, index);
//			secondScore = init.calculateWeightSumScore(second, index);
//		}
//		boolean betterThan = firstScore < secondScore;
//		return betterThan;
	}

}
