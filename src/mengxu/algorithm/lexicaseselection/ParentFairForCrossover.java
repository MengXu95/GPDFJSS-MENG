package mengxu.algorithm.lexicaseselection;

public class ParentFairForCrossover {
    private int parent1ID;
    private double parent1Fitness;

    private int parent2ID;
    private double parent2Fitness;

    private double parentsPearsonScore; //add 2021.12.28 meng xu

    private int child1ID;
    private double child1Fitness;
    private int child2ID;
    private double child2Fitness;

    public ParentFairForCrossover(int parent1ID, double parent1Fitness,
                                  int parent2ID, double parent2Fitness,
                                  int child1ID, double child1Fitness,
                                  int child2ID, double child2Fitness){
        this.parent1ID = parent1ID;
        this.parent1Fitness = parent1Fitness;
        this.parent2ID = parent2ID;
        this.parent2Fitness = parent2Fitness;
        this.child1ID = child1ID;
        this.child1Fitness = child1Fitness;
        this.child2ID = child1ID;
        this.child2Fitness = child1Fitness;
    }

    public void setChild1IDandFitness(int child1ID, double child1Fitness) {
        this.child1ID = child1ID;
        this.child1Fitness = child1Fitness;
    }

    public void setChild2IDandFitness(int child2ID, double child2Fitness) {
        this.child2ID = child2ID;
        this.child2Fitness = child2Fitness;
    }

    public void setParentsPearsonScore(double parentsPearsonScore) {
        this.parentsPearsonScore = parentsPearsonScore;
    }

    public double getParentsPearsonScore() {
        return parentsPearsonScore;
    }

    public void setChild1Fitness(double child1Fitness) {
        this.child1Fitness = child1Fitness;
    }

    public int getChild1ID() {
        return child1ID;
    }

    public void setChild2Fitness(double child2Fitness) {
        this.child2Fitness = child2Fitness;
    }

    public int getChild2ID() {
        return child2ID;
    }

    public void setChild1ID(int child1ID) {
        this.child1ID = child1ID;
    }

    public void setChild2ID(int child2ID) {
        this.child2ID = child2ID;
    }

    public int getParent1ID() {
        return parent1ID;
    }

    public int getParent2ID() {
        return parent2ID;
    }

    public double getParent1Fitness() {
        return parent1Fitness;
    }

    public double getParent2Fitness() {
        return parent2Fitness;
    }

    public double getChild1Fitness() {
        return child1Fitness;
    }
    public double getChild2Fitness() {
        return child2Fitness;
    }

    public void print(){
        System.out.println("[" + parent1Fitness +
                          ", " + parent2Fitness +
                          ", " + child1ID + ", " + child1Fitness + "]");
    }
}
