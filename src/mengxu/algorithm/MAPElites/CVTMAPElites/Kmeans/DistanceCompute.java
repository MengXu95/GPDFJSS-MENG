package mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
public class DistanceCompute {
    /**
     * 求欧式距离
     */
    public double getEuclideanDis(Point p1, Point p2) {
        double count_dis = 0;
        double[] p1_local_array = p1.getlocalArray();
        double[] p2_local_array = p2.getlocalArray();

        if (p1_local_array.length != p2_local_array.length) {
            throw new IllegalArgumentException("length of array must be equal!");
        }

        for (int i = 0; i < p1_local_array.length; i++) {
            count_dis += Math.pow(p1_local_array[i] - p2_local_array[i], 2);
        }

        return Math.sqrt(count_dis);
    }

    /**
     * 求Correlation距离: 1-correlation
     */
    public double getCorrelationDis(Point p1, Point p2) {
        PearsonsCorrelation correlation = new PearsonsCorrelation();
        double[] p1_local_array = p1.getlocalArray();
        double[] p2_local_array = p2.getlocalArray();

        if (p1_local_array.length != p2_local_array.length) {
            throw new IllegalArgumentException("length of array must be equal!");
        }

        double count_dis = 1-correlation.correlation(p1_local_array, p2_local_array);

        if(Double.isNaN(count_dis)){
            count_dis = 2;
        }

//        System.out.println("Correlation distance: " + count_dis);

        return count_dis;
    }

    /**
     * calculate the weight binary distance, proposed by mengxu 2023.08.05
     */
    public double getWeightBinaryDis(Point p1, Point p2) {
        double count_dis = 0;
        double[] p1_local_array = p1.getlocalArray();
        double[] p2_local_array = p2.getlocalArray();

        if (p1_local_array.length != p2_local_array.length) {
            throw new IllegalArgumentException("length of array must be equal!");
        }

        for (int i = 0; i < p1_local_array.length; i++) {
//            if(p1_local_array[i] != p2_local_array[i]){//original
            if(Math.abs(p1_local_array[i] - p2_local_array[i])<1){
                //todo: double check, this modification is to use for kmeans and cluster
                count_dis += (i+1);
            }
        }

        return count_dis;
    }

}
