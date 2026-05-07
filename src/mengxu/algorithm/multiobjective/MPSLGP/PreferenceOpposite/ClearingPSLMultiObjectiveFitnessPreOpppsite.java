package mengxu.algorithm.multiobjective.MPSLGP.PreferenceOpposite;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MPSLGP.PSLMultiObjectiveFitness;
import yimei.jss.niching.Clearable;

import java.util.ArrayList;

/**
 * The multi-objective fitness with clearing method for niching.
 *
 * Created by yimei on 21/11/16.
 */
public class ClearingPSLMultiObjectiveFitnessPreOpppsite
        extends PSLMultiObjectiveFitness implements Clearable {

    private boolean cleared;

    public boolean preferenceOpposite;

    /** The various fitnesses. */
    public double[] objectivesOpposite; // values range from 0 (worst) to 1 INCLUSIVE

    @Override
    public void setup(EvolutionState state, Parameter base) {
        super.setup(state, base);
        Parameter preferenceOppositeParam = new Parameter("preferenceOpposite");
        preferenceOpposite = state.parameters.getBoolean(preferenceOppositeParam, false);
        objectivesOpposite = new double[objectives.length];
    }

    @Override //when want to clear population, call method .clear()
    public void clear() {
        for (int i = 0; i < objectives.length; i++) {
            if (maximize[i]) {
                //fzhang 2019.8.22 when set the fitness to Double.POSITIVE_INFINITY, it will be detected by MultiObjectiveFitness (setObject),
                // so better to set it to a big number rather than POSITIVE_INFINITY
                //objectives[i] = Double.NEGATIVE_INFINITY; // when this is a maximize objective, set the bad objective to negative value
                objectives[i] = - Double.MAX_VALUE;
                objectivesOpposite[i] = - Double.MAX_VALUE;
            }
            else {
                //objectives[i] = Double.POSITIVE_INFINITY; // when this is a minimize objective, set the bad objective to positive value

                //fzhang 2019.8.22 when set the fitness to Double.POSITIVE_INFINITY, it will be detected by MultiObjectiveFitness (setObject),
                // so better to set it to a big number rather than POSITIVE_INFINITY
                objectives[i] = Double.MAX_VALUE;
                objectivesOpposite[i] = Double.MAX_VALUE;
            }
        }

        this.PSLFitness = Double.MAX_VALUE;// add by mengxu for Pareto set learning

        cleared = true;
    }

    //fzhang 2021.4.15 estimated fitness for multiobjecitve
    public void surrogateFitnessMultiobjective(double[] estimatedFitness, double[] estimatedFitnessOpposite){
        for (int i = 0; i < objectives.length; i++){
            objectives[i] =  estimatedFitness[i];
            objectivesOpposite[i] =  estimatedFitnessOpposite[i];
        }
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

    public void setObjectivesOpposite(final EvolutionState state, double[] newObjectivesOpposite) {
//    	System.out.println("setObjectives");
        this.objectivesOpposite = newObjectivesOpposite;

        cleared = false; //fzhang    label whether it is cleared or not
    }

    public void setFitness() {
        for (int i = 0; i < objectives.length; i++) {
                objectives[i] = Double.MAX_VALUE;
                objectivesOpposite[i] = Double.MAX_VALUE;
            }
        }

    private double PSLFitness;

    public boolean betterThan(Fitness fitness)
    {

        return this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness();
    }

    public double getPSLFitness(){
        return this.PSLFitness;
    }

    public double[] getObjectivesOpposite() {
        return objectivesOpposite;
    }

    public void calculatePSLFitness(EvolutionState state)
    {
        //for the first preference
        int index = state.generation;
        PSLInitializerPreOpposite init = (PSLInitializerPreOpposite) state.initializer;
        double fit;
        if(init.tchebycheff == 2){//augmented Tchebtcheff
            fit = init.calculateAugmentedTchebycheffScore(this, index);
        }
        else if (init.tchebycheff == 1) {//Tchebtcheff
            fit = init.calculateTchebycheffScore(this, index);
        } else {//weighted sum
            fit = init.calculateWeightSumScore(this, index);
        }

        //for the opposite preference
        int indexOpposite = state.numGenerations*2-1-state.generation;
        double fitOpposite;
        if(init.tchebycheff == 2){//augmented Tchebtcheff
            fitOpposite = init.calculateAugmentedTchebycheffScoreOpposite(this, indexOpposite);
        }
        else if (init.tchebycheff == 1) {//Tchebtcheff
            fitOpposite = init.calculateTchebycheffScoreOpposite(this, indexOpposite);
        } else {//weighted sum
            fitOpposite = init.calculateWeightSumScoreOpposite(this, indexOpposite);
        }

        this.PSLFitness = 0.5*fit + 0.5*fitOpposite;
    }

    public static ArrayList partitionIntoParetoFrontOpposite(Individual[] inds, ArrayList front, ArrayList nonFront)
    {
        if (front == null)
            front = new ArrayList();

        // put the first guy in the front
        front.add(inds[0]);

        // iterate over all the remaining individuals
        for (int i = 1; i < inds.length; i++)
        {
            Individual ind = (Individual) (inds[i]);

            boolean noOneWasBetter = true;
            int frontSize = front.size();

            // iterate over the entire front
            for (int j = 0; j < frontSize; j++)
            {
                Individual frontmember = (Individual) (front.get(j));

                // if the front member is better than the individual, dump the individual and go to the next one
                if (((ClearingPSLMultiObjectiveFitnessPreOpppsite) (frontmember.fitness)).paretoDominatesOpposite((ClearingPSLMultiObjectiveFitnessPreOpppsite) (ind.fitness)))
                {
                    if (nonFront != null) nonFront.add(ind);
                    noOneWasBetter = false;
                    break;  // failed.  He's not in the front
                }
                // if the individual was better than the front member, dump the front member.  But look over the
                // other front members (don't break) because others might be dominated by the individual as well.
                else if (((ClearingPSLMultiObjectiveFitnessPreOpppsite) (ind.fitness)).paretoDominatesOpposite((ClearingPSLMultiObjectiveFitnessPreOpppsite) (frontmember.fitness)))
                {
                    yank(j, front);
                    // a front member is dominated by the new individual.  Replace him
                    frontSize--; // member got removed
                    j--;  // because there's another guy we now need to consider in his place
                    if (nonFront != null) nonFront.add(frontmember);
                }
            }
            if (noOneWasBetter)
                front.add(ind);
        }
        return front;
    }

    static void yank(int val, ArrayList list)
    {
        int size = list.size();
        list.set(val, list.get(size - 1));
        list.remove(size - 1);
    }

    public boolean paretoDominatesOpposite(ClearingPSLMultiObjectiveFitnessPreOpppsite other)
    {
        boolean abeatsb = false;

        if (objectives.length != other.objectives.length)
            throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

        for (int x = 0; x < objectives.length; x++)
        {
            if (maximize[x] != other.maximize[x])  // uh oh
                throw new RuntimeException(
                        "Attempt made to compare two multiobjective fitnesses; but for objective #" + x +
                                ", one expects higher values to be better and the other expectes lower values to be better.");

            if (maximize[x]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
            {
                if (objectivesOpposite[x] > other.objectivesOpposite[x])
                    abeatsb = true;
                else if (objectivesOpposite[x] < other.objectivesOpposite[x])
                    return false;
            }
            else
            {
                if (objectivesOpposite[x] < other.objectivesOpposite[x]) //objective[0] and objective[1]: [2514.5953504074932, 498.03951619189513]
                    abeatsb = true;
                else if (objectivesOpposite[x] > other.objectivesOpposite[x])
                    return false;
            }
        }

        return abeatsb;
    }

}
