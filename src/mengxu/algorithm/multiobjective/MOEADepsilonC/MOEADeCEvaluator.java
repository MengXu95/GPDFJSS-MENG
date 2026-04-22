package mengxu.algorithm.multiobjective.MOEADepsilonC;

import ec.EvolutionState;
import ec.simple.SimpleEvaluator;
import mengxu.algorithm.multiobjective.MOEAD.MOEADInitializer;

public class MOEADeCEvaluator extends SimpleEvaluator{
    public void evaluatePopulation(final EvolutionState state)
    {
        super.evaluatePopulation(state); //the same as normal evaluation

        //update idealPoint 2021.11.03
//        ((MOEADeCInitializer)state.initializer).updateMultiBoxOutliers(state);
        ((MOEADeCInitializer)state.initializer).updateIdealPoint(state);
        ((MOEADeCInitializer)state.initializer).updateNadirPoint(state);
        ((MOEADeCInitializer)state.initializer).updateMainObjective(state, 0);

        //todo: after this, we need to mapping the individual with the subproblem! 2021.12.08

//        //update idealPoint
//        ((MOEADInitializer)state.initializer).updateIdealPoint(state);
//        ((MOEADInitializer)state.initializer).updateNadirPoint(state);

        //force revaluate
//        Individual[] inds = state.population.subpops[0].individuals;
//        for(Individual ind: inds){
//            ind.evaluated = false;
//        }

//        System.out.println("evaluatePopulation");
    }
}
