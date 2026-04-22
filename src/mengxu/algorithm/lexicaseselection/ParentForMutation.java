package mengxu.algorithm.lexicaseselection;

public class ParentForMutation {
    private int parentID;
    private double parentFitness;

    private int childID;
    private double childFitness;

    public ParentForMutation(int parentID, double parentFitness,
                             int childID, double childFitness){
        this.parentID = parentID;
        this.parentFitness = parentFitness;

        this.childID = childID;
        this.childFitness = childFitness;
    }

    public void setChildIDandFitness(int childID, double childFitness) {
        this.childID = childID;
        this.childFitness = childFitness;
    }

    public void setChildID(int childID) {
        this.childID = childID;
    }

    public void setChildFitness(double childFitness) {
        this.childFitness = childFitness;
    }

    public int getParentID() {
        return parentID;
    }

    public double getParentFitness() {
        return parentFitness;
    }

    public double getChildFitness() {
        return childFitness;
    }

    public int getChildID() {
        return childID;
    }

    public void print(){
        System.out.println("[" + parentFitness +
                          ", " + childID + ", " + childFitness + "]");
    }
}
