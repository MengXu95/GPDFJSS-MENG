package mengxu.cluster.kmeans;

import mengxu.cluster.CustomerPoint;
import mengxu.cluster.CustomerPointPermutation;

import java.util.ArrayList;
import java.util.List;

public class ClusterPermutation {
    private int id;// 标识
    private CustomerPointPermutation center;// 中心
    private List<CustomerPointPermutation> members = new ArrayList<>();// 成员
    private double meanFitness;

    public ClusterPermutation(int id, CustomerPointPermutation center) {
        this.id = id;
        this.center = center;
    }

    public ClusterPermutation(int id, CustomerPointPermutation center, List<CustomerPointPermutation> members) {
        this.id = id;
        this.center = center;
        this.members = members;
    }

    public void addPoint(CustomerPointPermutation newPoint) {
        members.add(newPoint);
//        if (!members.contains(newPoint)){
//            members.add(newPoint);
//        }else{
//            System.out.println("样本数据点 {"+newPoint.toString()+"} 已经存在！");
//        }
    }

    public int getId() {
        return id;
    }

    public CustomerPointPermutation getCenter() {
        return center;
    }

    public void setCenter(CustomerPointPermutation center) {
        this.center = center;
    }

    public List<CustomerPointPermutation> getMembers() {
        return members;
    }

    public void calculateMeanFitness(){
        double sum = 0;
        for(CustomerPointPermutation customerPoint:this.members){
            sum += customerPoint.getFitness();
        }
        this.meanFitness = sum/this.members.size();
    }

    public double getMeanFitness() {
        return meanFitness;
    }

    public boolean meanFitnessBetterThan(ClusterPermutation other){
        if(this.meanFitness < other.getMeanFitness()){
            return true;
        }
        else{
            return false;
        }
    }

    @Override
    public String toString() {
        String toString = "Cluster \n" + "Cluster_id=" + this.id + ", center:{" + this.center.toString()+"}";
        for (CustomerPointPermutation point : members) {
            toString+="\n"+point.toString();
        }
        return toString+"\n";
    }
}