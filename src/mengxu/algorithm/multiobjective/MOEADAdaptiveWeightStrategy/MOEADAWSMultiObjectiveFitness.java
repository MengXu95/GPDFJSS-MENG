package mengxu.algorithm.multiobjective.MOEADAdaptiveWeightStrategy;

import ec.multiobjective.MultiObjectiveFitness;

public class MOEADAWSMultiObjectiveFitness extends MultiObjectiveFitness{
    /** The fitness of last generation the Individual.
     * this is only used for MOEAD
     * 2021.10.28 mengxu
     * */
    public double[] oldObjectives;

    public void setOldFitness(double[] objectives) {
        oldObjectives = objectives;
    }

    public double[] getOldObjectives() {
        return oldObjectives;
    }

    public double[] getDifferenceBetweenFitness(){
        int numObj = getNumObjectives();
        double[] diff = new double[numObj+1];  //the last is the angle
        for(int i=0; i<numObj; i++){
            diff[i] = this.objectives[i] - oldObjectives[i];
        }
        double angle = Math.atan2(this.objectives[1], this.objectives[0]) - Math.atan2(oldObjectives[1], oldObjectives[0]);
        diff[numObj] = angle;
        return diff;
    }
}
