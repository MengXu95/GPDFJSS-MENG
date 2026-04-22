package mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans;

import ec.Individual;

public class Point {
    private double[] localArray;
    private Individual individual;
    private double fitness;

    private int fromGen;
    private int id;
    private int clusterId;  // 标识属于哪个类中心。
    private double dist;     // 标识和所属类中心的距离。

    public Point(int id, double[] localArray, Individual individual, double fitness, int fromGen) {
        this.id = id;
        this.localArray = localArray.clone();
        this.individual = (Individual) individual.clone();
        this.fitness = fitness;
        this.fromGen = fromGen;
    }

    public Point(double[] localArray, Individual individual, double fitness, int fromGen) {
        this.id = -1; //表示不属于任意一个类
        this.localArray = localArray.clone();
        this.individual = (Individual) individual.clone();
        this.fitness = fitness;
        this.fromGen = fromGen;
    }

    public Point(double[] localArray) {
        this.id = -1; //表示不属于任意一个类
        this.localArray = localArray.clone();
        this.individual = null;
        this.fitness = Double.MAX_VALUE;
        this.fromGen = -1;
    }

    public double getFitness() {
        return fitness;
    }


    public Individual getIndividual() {
        return (Individual) individual.clone();
    }

    public double[] getlocalArray() {
        return localArray.clone();
    }

    public int getId() {
        return id;
    }

    public int getFromGen() {
        return fromGen;
    }

    public void setClusterId(int clusterId) {
        this.clusterId = clusterId;
    }

    public int getClusterid() {
        return clusterId;
    }

    public double getDist() {
        return dist;
    }

    public void setDist(double dist) {
        this.dist = dist;
    }

    @Override
    public String toString() {
        String result = "Point_id=" + id + "  [";
        for (int i = 0; i < localArray.length; i++) {
            result += localArray[i] + " ";
        }
        return result.trim()+"] clusterId: "+clusterId+" dist: "+dist;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass())
            return false;

        Point point = (Point) obj;
        if (point.localArray.length != localArray.length)
            return false;

        for (int i = 0; i < localArray.length; i++) {
            if (Double.compare(point.localArray[i], localArray[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        double x = localArray[0];
        double y = localArray[localArray.length - 1];
        long temp = x != +0.0d ? Double.doubleToLongBits(x) : 0L;
        int result = (int) (temp ^ (temp >>> 32));
        temp = y != +0.0d ? Double.doubleToLongBits(y) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
