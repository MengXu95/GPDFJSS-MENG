package mengxu.algorithm.multiobjective.MPSLGP;

import ec.EvolutionState;
import ec.Individual;
import ec.select.TournamentSelection;

public class PSLParentSelection extends TournamentSelection {
    private static final long serialVersionUID = 1L;
    private Individual[] candidatePopulation;
    private int[][] candidatesByTask;

    private int[][] taskPools(EvolutionState state, int subpopulation) {
        GPRuleEvolutionStatePSL multi = (GPRuleEvolutionStatePSL) state;
        Individual[] individuals = state.population.subpops[subpopulation].individuals;
        if (candidatePopulation != individuals || candidatesByTask == null || candidatesByTask.length != multi.numTasks) {
            int[] counts = new int[multi.numTasks];
            for (Individual individual : individuals) {
                int task = multi.getTaskIndex(individual);
                if (task < 0 || task >= counts.length) { throw new IllegalStateException("Invalid parent task " + task); }
                counts[task]++;
            }
            candidatesByTask = new int[counts.length][];
            for (int task = 0; task < counts.length; task++) { candidatesByTask[task] = new int[counts[task]]; }
            java.util.Arrays.fill(counts, 0);
            for (int index = 0; index < individuals.length; index++) {
                int task = multi.getTaskIndex(individuals[index]);
                candidatesByTask[task][counts[task]++] = index;
            }
            candidatePopulation = individuals;
        }
        return candidatesByTask;
    }

    private int drawTask(EvolutionState state, int subpopulation, int thread, int excludedTask) {
        int[][] pools = taskPools(state, subpopulation);
        int total = 0;
        for (int task = 0; task < pools.length; task++) {
            if (task != excludedTask) { total += pools[task].length; }
        }
        if (total == 0) { throw new IllegalStateException("No eligible parent task"); }
        int draw = state.random[thread].nextInt(total);
        for (int task = 0; task < pools.length; task++) {
            if (task == excludedTask) { continue; }
            if (draw < pools[task].length) { return task; }
            draw -= pools[task].length;
        }
        throw new IllegalStateException("Parent task allocation failed");
    }

    private int tournamentFromTask(int start, int subpopulation, EvolutionState state, int thread, int task) {
        int[] pool = taskPools(state, subpopulation)[task];
        if (pool.length == 0) { throw new IllegalStateException("No parent candidates in task " + task); }
        Individual[] individuals = state.population.subpops[subpopulation].individuals;
        int size = getTournamentSizeToUse(state.random[thread]);
        int best = pool[state.random[thread].nextInt(pool.length)];
        for (int trial = 1; trial < size; trial++) {
            int candidate = pool[state.random[thread].nextInt(pool.length)];
            boolean better = betterThan(start, individuals[candidate], individuals[best], subpopulation, state, thread);
            if (pickWorst ? !better : better) { best = candidate; }
        }
        ((GPRuleEvolutionStatePSL) state).recordSelectedParent(best);
        return best;
    }

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
		if (n == 2 && state instanceof GPRuleEvolutionStatePSL) {
			GPRuleEvolutionStatePSL mpslgpState = (GPRuleEvolutionStatePSL) state;
			int firstIndex = produceMOEAD(start, subpopulation, state, thread);
			int firstTask = mpslgpState.getTaskIndex(state.population.subpops[subpopulation].individuals[firstIndex]);
			int preferenceRegion = mpslgpState.preferenceRegionForSubproblem(state.generation + 1);
			boolean usePeerTaskTransfer = false;
			int donorTask = -1;
			if (mpslgpState.useAdaptiveTransfer()) {
				double transferProbability = mpslgpState.adaptiveTransferProbability(firstTask, preferenceRegion);
				usePeerTaskTransfer = state.random[thread].nextDouble() < transferProbability;
				if (usePeerTaskTransfer) {
					donorTask = mpslgpState.selectAdaptiveDonorTask(firstTask, preferenceRegion, thread);
				}
			}
			else {
				usePeerTaskTransfer = mpslgpState.usePeerTaskTransfer()
						&& state.random[thread].nextDouble() < mpslgpState.transferProbability;
			}
			int secondIndex;
			if (usePeerTaskTransfer && donorTask >= 0) {
				secondIndex = produceMOEADFromTask(start, subpopulation, state, thread, donorTask, firstIndex);
			}
			else if (usePeerTaskTransfer) {
				secondIndex = produceMOEADFromDifferentTask(start, subpopulation, state, thread, firstTask, firstIndex);
			}
			else {
				secondIndex = produceMOEADFromSameTask(start, subpopulation, state, thread, firstTask, firstIndex);
			}
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
										  final int thread, final int excludedTask, final int fallbackIndex) {
        if (FairComparisonProtocol.enabled(state)) {
            return tournamentFromTask(start, subpopulation, state, thread, drawTask(state, subpopulation, thread, excludedTask));
        }
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
			best = bestIndividualFromDifferentTask(start, subpopulation, state, thread, excludedTask, fallbackIndex);
		}
		if(state instanceof GPRuleEvolutionStatePSL){
			((GPRuleEvolutionStatePSL)state).recordSelectedParent(best);
		}
		return best;
	}

	public int produceMOEADFromSameTask(final int start, final int subpopulation, final EvolutionState state,
									  final int thread, final int requiredTask, final int fallbackIndex) {
        if (FairComparisonProtocol.enabled(state)) {
            return tournamentFromTask(start, subpopulation, state, thread, requiredTask);
        }
		Individual[] oldinds = state.population.subpops[subpopulation].individuals;
		int best = -1;
		int tournamentSize = getTournamentSizeToUse(state.random[thread]);
		for (int trial = 0; trial < tournamentSize; trial++) {
			int candidateIndex = getRandomIndividual(start, subpopulation, state, thread);
			if (((GPRuleEvolutionStatePSL) state).getTaskIndex(oldinds[candidateIndex]) != requiredTask) {
				continue;
			}
			if (best < 0 || betterThan(start, oldinds[candidateIndex], oldinds[best], subpopulation, state, thread)) {
				best = candidateIndex;
			}
		}
		if (best < 0) {
			best = bestIndividualFromTask(start, subpopulation, state, thread, requiredTask, fallbackIndex);
		}
		if(state instanceof GPRuleEvolutionStatePSL){
			((GPRuleEvolutionStatePSL)state).recordSelectedParent(best);
		}
		return best;
	}

	public int produceMOEADFromTask(final int start, final int subpopulation, final EvolutionState state,
								 final int thread, final int requiredTask, final int fallbackIndex) {
		return produceMOEADFromSameTask(start, subpopulation, state, thread, requiredTask, fallbackIndex);
	}

	private int bestIndividualFromTask(final int start, final int subpopulation, final EvolutionState state,
									 final int thread, final int requiredTask, final int fallbackIndex) {
		Individual[] oldinds = state.population.subpops[subpopulation].individuals;
		int best = -1;
		for (int candidateIndex = 0; candidateIndex < oldinds.length; candidateIndex++) {
			if (((GPRuleEvolutionStatePSL) state).getTaskIndex(oldinds[candidateIndex]) != requiredTask) {
				continue;
			}
			if (best < 0 || betterThan(start, oldinds[candidateIndex], oldinds[best], subpopulation, state, thread)) {
				best = candidateIndex;
			}
		}
		return best >= 0 ? best : fallbackIndex;
	}

	private int bestIndividualFromDifferentTask(final int start, final int subpopulation, final EvolutionState state,
										  final int thread, final int excludedTask, final int fallbackIndex) {
		Individual[] oldinds = state.population.subpops[subpopulation].individuals;
		int best = -1;
		for (int candidateIndex = 0; candidateIndex < oldinds.length; candidateIndex++) {
			if (((GPRuleEvolutionStatePSL) state).getTaskIndex(oldinds[candidateIndex]) == excludedTask) {
				continue;
			}
			if (best < 0 || betterThan(start, oldinds[candidateIndex], oldinds[best], subpopulation, state, thread)) {
				best = candidateIndex;
			}
		}
		return best >= 0 ? best : fallbackIndex;
	}



	public int produceMOEAD(final int start, final int subpopulation, final EvolutionState state, final int thread) {
        if (FairComparisonProtocol.enabled(state)) {
            int task = ((GPRuleEvolutionStatePSL) state).numTasks == 1 ? 0 : drawTask(state, subpopulation, thread, -1);
            return tournamentFromTask(start, subpopulation, state, thread, task);
        }
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
