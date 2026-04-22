package yimei.jss.niching;

/**
 * Created by YiMei on 3/10/16.
 */
public interface Clearable {

    public void clear();
    public boolean isCleared();
    public void surrogateFitness(double estimatedFitness);
    public void surrogateFitnessMultiobjective(double[] estimatedFitness);
}
