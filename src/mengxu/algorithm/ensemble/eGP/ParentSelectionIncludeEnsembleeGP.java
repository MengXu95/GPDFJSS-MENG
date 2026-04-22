package mengxu.algorithm.ensemble.eGP;

import ec.EvolutionState;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.lexicaseselection.TournamentSelection;

import java.util.ArrayList;

public class ParentSelectionIncludeEnsembleeGP extends TournamentSelection {


    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);
    }

    @Override
    public int produce(int subpopulation, EvolutionState state, int thread) {

        if(((GPRuleEvolutionStateeGP)state).breedIndividualPopulation){
            //select individuals as parents and do individuals breeding
            int index = produceTournament(subpopulation, state, thread);
            return index;
        }
        else{
            //select ensembles as parents and do ensembles breeding
            ArrayList<EnsembleRule> ensembles = ((EnsembleEvaluatoreGP)state.evaluator).ensemblePool;
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
    }

    public boolean betterThanTwoEnsemble(EnsembleRule first, EnsembleRule second, int subpopulation, EvolutionState state, int thread)
    {
        return first.getFitness().fitness() < second.getFitness().fitness();
    }

}
