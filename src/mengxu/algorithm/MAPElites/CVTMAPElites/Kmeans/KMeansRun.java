package mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans;

import ec.EvolutionState;
import ec.Individual;

import java.util.*;

public class KMeansRun {
    private int kNum;                             //簇的个数
    private int iterNum = 10;                     //迭代次数

    private int iterMaxTimes = 100000;            //单次迭代最大运行次数
    private int iterRunTimes = 0;                 //单次迭代实际运行次数

    //todo: should also modify this if using weight binary distance by mengxu 2023.08.04
    private float disDiff = (float) 0.01;         //单次迭代终止条件，两次运行中类中心的距离差

    private List<double[]> original_data =null;    //用于存放，原始数据集
    private List<Individual> original_Inds =null;    //用于存放，原始数据集
    private List<Double> original_Fits =null;    //用于存放，原始数据集
    private List<Integer> original_IndsFromGen =null;    //用于存放，原始数据集
    private static List<Point> pointList = null;  //用于存放，原始数据集所构建的点集
    private DistanceCompute disC = new DistanceCompute();
    private int len = 0;                          //用于记录每个数据点的维度

    public String distance_measurement;

//    public boolean use_weight_binary_dis = false; //add by mengxu 2023.08.05

    public KMeansRun(int k, List<double[]> original_PCs, List<Individual> original_Inds, List<Double> original_Fits, List<Integer> original_IndsFromGen, String distance_measurement) {
        this.kNum = k;
        this.original_data = original_PCs;
        this.original_Inds = original_Inds; //add by Mengxu
        this.original_Fits = original_Fits; //add by Mengxu
        this.original_IndsFromGen = original_IndsFromGen; //add by Mengxu
        this.len = original_data.get(0).length;
        this.distance_measurement = distance_measurement;
        //检查规范
        check();
        //初始化点集。
        init();
    }

    /**
     * 检查规范
     */
    private void check() {
        if (kNum == 0){
            throw new IllegalArgumentException("k must be the number > 0");
        }
        if (original_data == null){
            throw new IllegalArgumentException("program can't get real data");
        }
    }

    /**
     * 初始化数据集，把数组转化为Point类型。
     */
    private void init() {
        pointList = new ArrayList<Point>();
        for (int i = 0, j = original_data.size(); i < j; i++){
            pointList.add(new Point(i, original_data.get(i), original_Inds.get(i), original_Fits.get(i), original_IndsFromGen.get(i)));
        }
    }

    /**
     * 随机选取中心点，构建成中心类。
     */
    private Set<Cluster> chooseCenterCluster(EvolutionState state) {
        Set<Cluster> clusterSet = new HashSet<Cluster>();
//        Random random = new Random();
        for (int id = 0; id < kNum; ) {
            Point point = pointList.get(state.random[0].nextInt(pointList.size()));
            // 用于标记是否已经选择过该数据。
            boolean flag =true;
            for (Cluster cluster : clusterSet) {
                if (cluster.getCenter().equals(point)) {
                    flag = false;
                }
            }
            // 如果随机选取的点没有被选中过，则生成一个cluster
            if (flag) {
                Cluster cluster =new Cluster(id, point);
                clusterSet.add(cluster);
                id++;
            }
        }
        return clusterSet;
    }

    /**
     * 为每个点分配一个类！
     */
    public void cluster(Set<Cluster> clusterSet){
        // 计算每个点到K个中心的距离，并且为每个点标记类别号
        for (Point point : pointList) {
            double min_dis = Double.MAX_VALUE;
            for (Cluster cluster : clusterSet) {
//                double tmp_dis = (double) Math.min(disC.getEuclideanDis(point, cluster.getCenter()), min_dis); //original
                double tmp_dis = 0;
                if(Objects.equals(distance_measurement, "weight_binary_dis")){
                    tmp_dis = (double) Math.min(disC.getWeightBinaryDis(point, cluster.getCenter()), min_dis);
                }
                else if(Objects.equals(distance_measurement, "euclidean_dis")){
                    tmp_dis = (double) Math.min(disC.getEuclideanDis(point, cluster.getCenter()), min_dis);
                }
                else if(Objects.equals(distance_measurement, "correlation_dis")){
                    tmp_dis = (double) Math.min(disC.getCorrelationDis(point, cluster.getCenter()), min_dis);
                }
                if (tmp_dis < min_dis) {
                    min_dis = tmp_dis;
                    point.setClusterId(cluster.getId());
                    point.setDist(min_dis);
                }
            }
        }
        // 新清除原来所有的类中成员。把所有的点，分别加入每个类别
        for (Cluster cluster : clusterSet) {
            cluster.getMembers().clear();
            for (Point point : pointList) {
                if (point.getClusterid()==cluster.getId()) {
                    cluster.addPoint(point);
                }
            }
        }
    }

    /**
     * 计算每个类的中心位置！
     */
    public boolean calculateCenter(Set<Cluster> clusterSet) {
        boolean ifNeedIter = false;
        for (Cluster cluster : clusterSet) {
            List<Point> point_list = cluster.getMembers();
            double[] sumAll =new double[len];
            // 所有点，对应各个维度进行求和
            for (int i = 0; i < len; i++) {
                for (int j = 0; j < point_list.size(); j++) {
                    sumAll[i] += point_list.get(j).getlocalArray()[i];
                }
            }
            // 计算平均值
            for (int i = 0; i < sumAll.length; i++) {
                sumAll[i] = (float) sumAll[i]/point_list.size();
            }
            // 计算两个新、旧中心的距离，如果任意一个类中心移动的距离大于dis_diff则继续迭代。
            double distance = 0;
            if(Objects.equals(distance_measurement, "weight_binary_dis")){
                distance = disC.getWeightBinaryDis(cluster.getCenter(), new Point(sumAll));
            }
            else if(Objects.equals(distance_measurement, "euclidean_dis")){
                distance = disC.getEuclideanDis(cluster.getCenter(), new Point(sumAll));
            }
            else if(Objects.equals(distance_measurement, "correlation_dis")){
                distance = disC.getCorrelationDis(cluster.getCenter(), new Point(sumAll));
            }

            if(distance > disDiff){
                ifNeedIter = true;
            }
            // 设置新的类中心位置
            cluster.setCenter(new Point(sumAll));
        }
        return ifNeedIter;
    }

    /**
     * 运行 k-means
     */
    public Set<Cluster> run(EvolutionState state) {
        if(Objects.equals(distance_measurement, "weight_binary_dis")){
            disDiff = (float)2.0;//original = 1.0
        }
        else if(Objects.equals(distance_measurement, "euclidean_dis")){
            disDiff = (float)0.01;//original = 1.0
        }
        else if(Objects.equals(distance_measurement, "correlation_dis")){
            disDiff = (float)0.5;//original = 1.0
        }

        Set<Cluster> clusterSet= chooseCenterCluster(state);
        boolean ifNeedIter = true;
        while (ifNeedIter) {
            cluster(clusterSet);
            ifNeedIter = calculateCenter(clusterSet);
            iterRunTimes ++ ;
        }
        return clusterSet;
    }

    /**
     * 返回实际运行次数
     */
    public int getIterTimes() {
        return iterRunTimes;
    }
}