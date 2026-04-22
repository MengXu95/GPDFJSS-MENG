package mengxu.algorithm.ensemble.M3GP;

import ec.EvolutionState;
import ec.Individual;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.lexicaseselection.TournamentSelection;
import mengxu.algorithm.multicaseEnsemble.ensembleContribution.GPRuleEvolutionStateEnsembleContribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParentSelectionIncludeEnsembleM3GP extends TournamentSelection {


    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);
    }

    @Override
    public int produce(int subpopulation, EvolutionState state, int thread) { //epsilon lexicase selection
        ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluatorM3GP)state.evaluator).ensemblePool;
        int totalEnsembles = ensembles.size();

        int best = state.random[thread].nextInt(totalEnsembles);

        int s = getTournamentSizeToUse(state.random[thread]);

        for (int x=1;x<s;x++)
        {
            int randomIndex = state.random[thread].nextInt(totalEnsembles);
            EnsembleRule firstEnsemble = ensembles.get(best);
            EnsembleRule secondEnsemble = ensembles.get(randomIndex);

            if(betterThanTwoEnsemble(firstEnsemble, secondEnsemble, subpopulation, state, thread)){
                best = randomIndex;
            }
        }
        return best;
    }

    public boolean betterThanTwoEnsemble(EnsembleRule first, EnsembleRule second, int subpopulation, EvolutionState state, int thread)
    {
        return first.getFitness().fitness() < second.getFitness().fitness();
    }

}
