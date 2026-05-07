package mengxu.algorithm.multiobjective.MPSLGP;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;

public class PSLMultiObjectiveFitness extends MultiObjectiveFitness{

    public double PSLFitness;

    private int taskIndex = -1;

    public boolean betterThan(Fitness fitness)
    {

        return this.PSLFitness < ((PSLMultiObjectiveFitness)fitness).getPSLFitness();
    }

    public double getPSLFitness(){
        return this.PSLFitness;
    }

    public void setPSLFitness(double PSLFitness){
        this.PSLFitness = PSLFitness;
    }

    public int getTaskIndex() {
        return taskIndex;
    }

    public void setTaskIndex(int taskIndex) {
        this.taskIndex = taskIndex;
    }


    public void calculatePSLFitness(EvolutionState state)
    {
        int index = state.generation;

        PSLInitializer init = (PSLInitializer) state.initializer;
        double fit;
        if(init.tchebycheff == 3){//PBI add by mengxu 2024.6.10
            fit = init.calculatePenaltyBoundaryIntersectionScore(this, index);
        }
        else if(init.tchebycheff == 2){//augmented Tchebtcheff
            fit = init.calculateAugmentedTchebycheffScore(this, index);
        }
        else if (init.tchebycheff == 1) {//Tchebtcheff
            fit = init.calculateTchebycheffScore(this, index);
        } else {//weighted sum
            fit = init.calculateWeightSumScore(this, index);
        }
        this.PSLFitness = fit;
    }
	
}
