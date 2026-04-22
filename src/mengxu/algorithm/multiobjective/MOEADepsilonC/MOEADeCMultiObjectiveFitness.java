package mengxu.algorithm.multiobjective.MOEADepsilonC;

import ec.EvolutionState;
import ec.Individual;
import ec.app.tutorial4.Sub;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;

import java.util.ArrayList;
import java.util.List;

public class MOEADeCMultiObjectiveFitness extends MultiObjectiveFitness{
    private int mainObjIndex;
    private SubProblem subProblem;
//    public double[] idealPoint;
//    public double[] nadirPoint; //2021.10.18
    private int numNotsatisfyConstraints;
    private EvolutionState state;

    public void setup(EvolutionState state, Parameter base)
    {
        super.setup(state, base); // unnecessary really

        this.state = state;
        this.numNotsatisfyConstraints = 0;
        state.output.exitIfErrors();
    }


	@Override
    public double fitness()
    {
        double fit = 0;
        boolean satisfyConstraints = true;
        List<Double> constrains = new ArrayList<>();
        for (int x = 0; x < objectives.length; x++){
            if(mainObjIndex!=x){
                fit += ((MOEADeCInitializer)state.initializer).delta * objectives[x];
                double constrain = (objectives[x]-((MOEADeCInitializer)state.initializer).idealPoint[x])/(((MOEADeCInitializer)state.initializer).nadirPoint[x]-((MOEADeCInitializer)state.initializer).idealPoint[x]);
                constrains.add(constrain);
            }
            else{
                fit += objectives[mainObjIndex];
            }
        }
        for(int i=0; i<constrains.size(); i++){
            double constrain = constrains.get(i);
            if(constrain > subProblem.getEpsilons()[i]){
                satisfyConstraints = false;
                break;
            }
        }

        if(satisfyConstraints){
            return fit;
        }
        return Double.POSITIVE_INFINITY;
    }

    public void setMainObjIndex(int mainObjIndex) {
        this.mainObjIndex = mainObjIndex;
    }

    public void setSubProblem(SubProblem subproblem) {
        this.subProblem = subproblem;
    }

//    public void setIdealPoint(double[] idealPoint) {
//        this.idealPoint = idealPoint;
//    }
//
//    public void setNadirPoint(double[] nadirPoint) {
//        this.nadirPoint = nadirPoint;
//    }

    public int getNumNotsatisfyConstraints() {
        this.numNotsatisfyConstraints = 0;
        List<Double> constrains = new ArrayList<>();
        for (int x = 0; x < objectives.length; x++){
            if(mainObjIndex!=x){
                double constrain = (objectives[x]-((MOEADeCInitializer)state.initializer).idealPoint[x])/(((MOEADeCInitializer)state.initializer).nadirPoint[x]-((MOEADeCInitializer)state.initializer).idealPoint[x]);
                constrains.add(constrain);
            }
        }
        for(int i=0; i<constrains.size(); i++){
            double constrain = constrains.get(i);
            if(constrain > subProblem.getEpsilons()[i]){
                numNotsatisfyConstraints++;
            }
        }
        return numNotsatisfyConstraints;
    }

//    public double[] getIdealPoint() {
//        return idealPoint;
//    }
//
//    public double[] getNadirPoint() {
//        return nadirPoint;
//    }

    public int getMainObjIndex() {
        return mainObjIndex;
    }

    public SubProblem getSubProblem() {
        return subProblem;
    }

}
