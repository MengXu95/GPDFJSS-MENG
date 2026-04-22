package mengxu.cluster;

import java.util.Collection;

import static mengxu.algorithm.clusterselection.PearsonDemo.getPearsonCorrelationScore;

//import bsh.This;

/**
 * @author mengxu 2021.05.07
 *
 */
public class CustomerPointPermutation {


    private int indIndex;
    private final int[][] point;
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


    public CustomerPointPermutation(int[][] point) {
        this.point = point;
    }

    public CustomerPointPermutation(int indIndex, int[][] point, double fitness) {
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

    public int[][] getPoint() {
        return point;
    }



    //add by mengxu 2022.01.14
    public double distanceFromPearsonForEachDecision(final CustomerPointPermutation other) {
        double sum = 0;
        for(int i = 0; i < point.length; i++){
            double score = getPearsonCorrelationScore(this.point[i], other.getPoint()[i]);
            sum += 1-score;
        }
        double dis = sum/point.length;
        return dis;
    }




    public CustomerPointPermutation centroidOf(final Collection<CustomerPointPermutation> points) {
        int[][] centroid = new int[getPoint().length][];
        for (CustomerPointPermutation p : points) {
            for (int i = 0; i < centroid.length; i++) {
                for(int j=0; j< centroid[0].length; j++){
                    centroid[i][j] += p.getPoint()[i][j];
                }
            }
        }
        for (int i = 0; i < centroid.length; i++) {
            for(int j=0; j< centroid[0].length; j++) {
                centroid[i][j] /= points.size();
            }
        }
        return new CustomerPointPermutation(centroid);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof CustomerPointPermutation)) {
            return false;
        }
        final int[][] otherPoint = ((CustomerPointPermutation) other).getPoint();
        if (point.length != otherPoint.length) {
            return false;
        }
        if (point[0].length != otherPoint[0].length) {
            return false;
        }
        if(this.indIndex != ((CustomerPointPermutation) other).getIndIndex()){
            return false;
        }
        for (int i = 0; i < point.length; i++) {
            for(int j = 0; j < point[0].length; j++){
                if (point[i][j] != otherPoint[i][j]) {
                    return false;
                }
            }

        }
        return true;
    }
    @Override
    public String toString() {
        final StringBuffer buff = new StringBuffer("{");
        final int[][] coordinates = getPoint();
        buff.append("lat:"+coordinates[0][0]+",");
        buff.append("lng:"+coordinates[1][0]+",");
        buff.append("fitness:"+this.getFitness()+",");
        buff.append("indIndex:"+this.getIndIndex());
        buff.append("}");
        return buff.toString();
    }
}