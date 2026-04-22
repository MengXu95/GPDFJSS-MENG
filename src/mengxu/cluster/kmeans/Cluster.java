package mengxu.cluster.kmeans;

import mengxu.cluster.CustomerPoint;

import java.util.ArrayList;
import java.util.List;

public class Cluster {
    private int id;// 标识
    private CustomerPoint center;// 中心
    private List<CustomerPoint> members = new ArrayList<>();// 成员
    private double meanFitness;

    public Cluster(int id, CustomerPoint center) {
        this.id = id;
        this.center = center;
    }

    public Cluster(int id, CustomerPoint center, List<CustomerPoint> members) {
        this.id = id;
        this.center = center;
        this.members = members;
    }

    public void addPoint(CustomerPoint newPoint) {
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

    public CustomerPoint getCenter() {
        return center;
    }

    public void setCenter(CustomerPoint center) {
        this.center = center;
    }

    public List<CustomerPoint> getMembers() {
        return members;
    }

    public void calculateMeanFitness(){
        double sum = 0;
        for(CustomerPoint customerPoint:this.members){
            sum += customerPoint.getFitness();
        }
        this.meanFitness = sum/this.members.size();
    }

    public double getMeanFitness() {
        return meanFitness;
    }

    public boolean meanFitnessBetterThan(Cluster other){
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
        for (CustomerPoint point : members) {
            toString+="\n"+point.toString();
        }
        return toString+"\n";
    }
}