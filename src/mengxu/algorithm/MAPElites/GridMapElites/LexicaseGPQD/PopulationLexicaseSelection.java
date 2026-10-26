package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import ec.EvolutionState;
import ec.Individual;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import mengxu.algorithm.lexicaseselection.EpsilonTTGenNoRepeatCandidateLexicaseSelection;

public final class PopulationLexicaseSelection extends EpsilonTTGenNoRepeatCandidateLexicaseSelection {
    private int available;

    @Override public void setup(EvolutionState state, Parameter base) {
        super.setup(state, base);
        int population = state.parameters.getIntWithDefault(new Parameter("pop.subpop.0.size"), null, 0);
        int candidates = state.parameters.getIntWithDefault(new Parameter("candidate-size"), null, 0);
        if (numCase < 2 || elitistSR != 1.0 || candidates < 1 || candidates > population) {
            throw new IllegalArgumentException("Require >=2 cases, elitist-selection-rate=1 and 1<=candidate-size<=population");
        }
    }

    @Override public int produce(int subpopulation, EvolutionState state, int thread) {
        available = 0;
        for (Individual individual : state.population.subpops[subpopulation].individuals) {
            if (!(individual.fitness instanceof LexicaseFitness)) {
                throw new IllegalArgumentException("LexicaseFitness is required");
            }
            if (((LexicaseFitness) individual.fitness).isValidEvaluation()) { available++; }
        }
        if (available == 0) { throw new IllegalStateException("No valid population member can be selected"); }
        return super.produce(subpopulation, state, thread);
    }

    @Override public int getCandidateSizeToUse(MersenneTwisterFast random) {
        return Math.min(super.getCandidateSizeToUse(random), available);
    }
}
