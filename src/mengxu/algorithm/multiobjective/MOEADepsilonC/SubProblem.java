package mengxu.algorithm.multiobjective.MOEADepsilonC;

public class SubProblem {
    private int id;
    private int numberObj;
    private double[] epsilons;

    public SubProblem(int id, int numberObj, double[] epsilons){
        this.id = id;
        this.numberObj = numberObj;
        this.epsilons = epsilons;
    }

    public int getId() {
        return id;
    }

    public double[] getEpsilons() {
        return epsilons;
    }

    public int getNumberObj() {
        return numberObj;
    }
}
