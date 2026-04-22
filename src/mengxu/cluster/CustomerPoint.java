package mengxu.cluster;

import java.util.Collection;
import org.apache.commons.math3.stat.clustering.Clusterable;
import org.apache.commons.math3.util.MathUtils;

import static mengxu.algorithm.clusterselection.PearsonDemo.getPearsonCorrelationScore;

//import bsh.This;

/**
 * @author mengxu 2021.05.07
 *
 */
public class CustomerPoint{


    private int indIndex;
    private final double[] point;
    private double fitness;

    private int clusterId;  // 标识属于哪个类中心。
    private double dist;     // 标识和所属类中心的距离。

    private PointStatus status;


    public int getIndIndex() {
        return indIndex;
    }
    public void setIndIndex(int indIndex) {
        this.indIndex = indIndex;
    }

    public void setClusterId(int clusterId) {
        this.clusterId = clusterId;
    }

    public int getClusterid() {
        return clusterId;
    }

    public PointStatus getStatus() {
        return status;
    }

    public void setStatus(PointStatus status) {
        this.status = status;
    }

    public double getDist() {
        return dist;
    }

    public void setDist(double dist) {
        this.dist = dist;
    }


    public CustomerPoint(double[] point) {
        this.point = point;
    }

    public CustomerPoint(int indIndex, double[] point, double fitness) {
        this(point);
        this.indIndex = indIndex;
        this.fitness = fitness;
    }

    public double getFitness() {
        return fitness;
    }
    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    public double[] getPoint() {
        return point;
    }

    public double distanceFrom(final CustomerPoint other) {
        double dis = 0;
        for(int i=0; i<this.point.length; i++){
            dis += (point[i]-other.getPoint()[i])*(point[i]-other.getPoint()[i]);
        }

        dis = Math.sqrt(dis);
        return dis;
    }

    //add by mengxu 2021.01.13
    public double distanceFromBinaryBring(final CustomerPoint other) {
        double dis = 0;
        for(int i=0; i<this.point.length; i++){
            if(Math.abs(point[i] - other.getPoint()[i]) < 1){
                dis += 0;
            }
            else{
                dis += 1;
            }
        }

        return dis;
    }

    //add by mengxu 2021.12.28
//    public double distanceFromBinaryBring(final CustomerPoint other) {
//        double dis = 0;
//        for(int i=0; i<this.point.length; i++){
//            if(point[i] >= other.getPoint()[i] && point[i] <= other.getPoint()[i]){
//                dis += 0;
//            }
//            else{
//                dis += 1;
//            }
//        }
//
//        return dis;
//    }

    //add by mengxu 2021.12.28
    public double distanceFromPearson(final CustomerPoint other) {

        double score = getPearsonCorrelationScore(this.point, other.getPoint());
        double dis = Math.abs(1 - score);

        return dis;
    }


    //add by mengxu 2022.01.14
    public double distanceFromPearsonForEachDecision(final CustomerPoint other) {

        double score = getPearsonCorrelationScore(this.point, other.getPoint());
        double dis = Math.abs(1 - score);

        return dis;
    }


    public double distanceFromCenter(double[] other) {
        double dis = 0;
        for(int i=0; i<this.point.length; i++){
            dis += (point[i]-other[i])*(point[i]-other[i]);
        }

        dis = Math.sqrt(dis);
        return dis;
    }


    public CustomerPoint centroidOf(final Collection<CustomerPoint> points) {
        double[] centroid = new double[getPoint().length];
        for (CustomerPoint p : points) {
            for (int i = 0; i < centroid.length; i++) {
                centroid[i] += p.getPoint()[i];
            }
        }
        for (int i = 0; i < centroid.length; i++) {
            centroid[i] /= points.size();
        }
        return new CustomerPoint(centroid);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof CustomerPoint)) {
            return false;
        }
        final double[] otherPoint = ((CustomerPoint) other).getPoint();
        if (point.length != otherPoint.length) {
            return false;
        }
        if(this.indIndex != ((CustomerPoint) other).getIndIndex()){
            return false;
        }
        for (int i = 0; i < point.length; i++) {
            if (point[i] != otherPoint[i]) {
                return false;
            }
        }
        return true;
    }
    @Override
    public String toString() {
        final StringBuffer buff = new StringBuffer("{");
        final double[] coordinates = getPoint();
        buff.append("lat:"+coordinates[0]+",");
        buff.append("lng:"+coordinates[1]+",");
        buff.append("fitness:"+this.getFitness()+",");
        buff.append("indIndex:"+this.getIndIndex());
        buff.append("}");
        return buff.toString();
    }
}