package mengxu.algorithm.multiobjective.MOEADAdaptiveWeightStrategy;

import ec.EvolutionState;
import ec.simple.SimpleEvaluator;
import mengxu.algorithm.multiobjective.MOEAD.MOEADInitializer;

public class MOEADAWSEvaluator extends SimpleEvaluator{
    public void evaluatePopulation(final EvolutionState state)
    {
        super.evaluatePopulation(state); //the same as normal evaluation

        //update idealPoint
        ((MOEADAWSInitializer)state.initializer).updateIdealPoint(state);
        ((MOEADAWSInitializer)state.initializer).updateNadirPoint(state);

        double[][] diff;
        if(state.generation !=0){
            System.out.println("Difference:");
            diff = ((MOEADAWSInitializer)state.initializer).differenceOfObjectiveBetweenGen(state.population, false);
            ((MOEADAWSInitializer)state.initializer).updateWeights(diff);
            ((MOEADAWSInitializer)state.initializer).updateNeighborhood();
        }

        //force revaluate
//        Individual[] inds = state.population.subpops[0].individuals;
//        for(Individual ind: inds){
//            ind.evaluated = false;
//        }

//        System.out.println("evaluatePopulation");
    }
}
