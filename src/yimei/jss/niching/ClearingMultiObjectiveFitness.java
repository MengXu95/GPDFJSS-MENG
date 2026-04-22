package yimei.jss.niching;

import ec.EvolutionState;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;

/**
 * The multi-objective fitness with clearing method for niching.
 *
 * Created by yimei on 21/11/16.
 */
public class ClearingMultiObjectiveFitness
        extends NSGA2MultiObjectiveFitness implements Clearable {

    private boolean cleared;
  
    @Override //when want to clear population, call method .clear()
    public void clear() {
        for (int i = 0; i < objectives.length; i++) {
            if (maximize[i]) {
                //fzhang 2019.8.22 when set the fitness to Double.POSITIVE_INFINITY, it will be detected by MultiObjectiveFitness (setObject),
                // so better to set it to a big number rather than POSITIVE_INFINITY
                //objectives[i] = Double.NEGATIVE_INFINITY; // when this is a maximize objective, set the bad objective to negative value
                objectives[i] = - Double.MAX_VALUE;
            }
            else {
                //objectives[i] = Double.POSITIVE_INFINITY; // when this is a minimize objective, set the bad objective to positive value

                //fzhang 2019.8.22 when set the fitness to Double.POSITIVE_INFINITY, it will be detected by MultiObjectiveFitness (setObject),
                // so better to set it to a big number rather than POSITIVE_INFINITY
                objectives[i] = Double.MAX_VALUE;
            }
        }

        cleared = true;
    }

    //fzhang 2019.8.25 set the estimated fitness to the individuals
    public void surrogateFitness(double estimatedFitness){
        for (int i = 0; i < objectives.length; i++){
            objectives[i] =  estimatedFitness;
        }
    }

    //fzhang 2021.4.15 estimated fitness for multiobjecitve
    public void surrogateFitnessMultiobjective(double[] estimatedFitness){
        for (int i = 0; i < objectives.length; i++){
            objectives[i] =  estimatedFitness[i];
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }

    public void setObjectives(final EvolutionState state, double[] newObjectives) {
//    	System.out.println("setObjectives");
        super.setObjectives(state, newObjectives);

        cleared = false; //fzhang    label whether it is cleared or not
    }

    public void setFitness() {
        for (int i = 0; i < objectives.length; i++) {
                objectives[i] = Double.MAX_VALUE;
            }
        }
}
