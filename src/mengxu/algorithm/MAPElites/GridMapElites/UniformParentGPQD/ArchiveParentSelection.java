package mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD;

import ec.EvolutionState;
import ec.Individual;
import ec.util.Parameter;
import mengxu.algorithm.MAPElites.GridMapElites.GPRuleEvolutionStateGridMAPElites;
import mengxu.algorithm.MAPElites.GridMapElites.ParentSelectionGridMAPElites;

public final class ArchiveParentSelection extends ParentSelectionGridMAPElites {
    @Override public void setup(EvolutionState state, Parameter base) {
        super.setup(state, base);
        if (!tournamentSizeDynamic || pickWorst) {
            throw new IllegalArgumentException("Use dynamic tournaments and minimization parent selection");
        }
    }

    @Override public int produce(int subpopulation, EvolutionState state, int thread) {
        if (subpopulation != 0 || !(state instanceof GPRuleEvolutionStateGridMAPElites)) {
            throw new IllegalArgumentException("A single grid-archive population is required");
        }
        GPRuleEvolutionStateGridMAPElites owner = (GPRuleEvolutionStateGridMAPElites) state;
        if (!(owner.gridPCmap instanceof IsolatedGridMap)) {
            throw new IllegalStateException("Archive must be initialized before parent selection");
        }
        IsolatedGridMap map = (IsolatedGridMap) owner.gridPCmap;
        int count = map.occupiedCount();
        if (count == 0) { throw new IllegalStateException("Cannot select from an empty archive"); }
        double probability = owner.randomParentSelectionProbability;
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Random parent probability must be in [0,1]");
        }
        int best = state.random[thread].nextInt(count);
        int size = Math.max(1, getTournamentSizeToUse(state.random[thread]) * count
                / state.population.subpops[subpopulation].individuals.length);
        // Preserve the existing hybrid-selector random draw in both matched modes.
        if (state.random[0].nextDouble() < probability) { size = 1; }
        for (int i = 1; i < size; i++) {
            int candidate = state.random[thread].nextInt(count);
            if (betterThanBasedOnFitAndGen(map.scoreAt(candidate), map.generationAt(candidate),
                    map.scoreAt(best), map.generationAt(best), subpopulation, state, thread)) {
                best = candidate;
            }
        }
        return best;
    }

    @Override public int produce(int min, int max, int start, int subpopulation,
                                 Individual[] individuals, EvolutionState state, int thread) {
        if (min < 1 || max < min || start < 0 || start + min > individuals.length) {
            throw new IllegalArgumentException("Invalid parent-output bounds");
        }
        IsolatedGridMap map = (IsolatedGridMap) ((GPRuleEvolutionStateGridMAPElites) state).gridPCmap;
        for (int i = 0; i < min; i++) {
            int index = produce(subpopulation, state, thread);
            // Bypass the shared generation-zero population-index interpretation.
            individuals[start + i] = (Individual) map.individualAt(index).clone();
        }
        return min;
    }
}
