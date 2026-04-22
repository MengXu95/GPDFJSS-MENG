package mengxu.algorithm.multiobjective;

public class ParentFairForCrossoverMO {
//    private int parent1ID;
    private double[] parent1Fitness;

//    private int parent2ID;
    private double[] parent2Fitness;

    private int childID;
    private double[] childFitness;

    public ParentFairForCrossoverMO(double[] parent1Fitness,
                                    double[] parent2Fitness,
                                    int childID, double[] childFitness){
//        this.parent1ID = parent1ID;
        this.parent1Fitness = parent1Fitness;
//        this.parent2ID = parent2ID;
        this.parent2Fitness = parent2Fitness;
        this.childID = childID;
        this.childFitness = childFitness;
    }

    public void setChildIDandFitness(int childID, double[] childFitness) {
        this.childID = childID;
        this.childFitness = childFitness;
    }

    public void setChildFitness(double[] childFitness) {
        this.childFitness = childFitness;
    }

    public int getChildID() {
        return childID;
    }

    public void print(){
        System.out.println("[ [" + parent1Fitness[0] +
                           ", " + parent1Fitness[1] + "]" +
                           ", [" + parent2Fitness[0] +
                           ", " + parent2Fitness[1] + "]" +
                           ", " + childID + ", " +
                           "[" + childFitness[0] +
                           ", " + childFitness[1] + "] ]");
    }
}
