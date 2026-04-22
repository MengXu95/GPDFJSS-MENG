package mengxu.algorithm.multiobjective;

public class ParentForMutationMO {
//    private int parentID;
    private double[] parentFitness;

    private int childID;
    private double[] childFitness;

    public ParentForMutationMO(double[] parentFitness,
                               int childID, double[] childFitness){
//        this.parentID = parentID;
        this.parentFitness = parentFitness;

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
        System.out.println("[ [" + parentFitness[0] +
                           ", " + parentFitness[1] + "]" +
                           ", " + childID + ", " +
                           "[" + childFitness[0] +
                           ", " + childFitness[1] + "] ]");
    }
}
