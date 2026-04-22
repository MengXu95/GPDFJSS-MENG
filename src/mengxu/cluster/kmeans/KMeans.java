package mengxu.cluster.kmeans;


import mengxu.cluster.CustomerPoint;

import java.util.*;

public class KMeans {
    private int kNum;                             //簇的个数

    private int iterMaxTimes = 50;            //单次迭代最大运行次数
    private int iterRunTimes = 0;                 //单次迭代实际运行次数
    private double disDiff = 0.01;         //单次迭代终止条件，两次运行中类中心的距离差
    private double disDiffForBinaryBring = 2;         //单次迭代终止条件，两次运行中类中心的距离差
    //todo: this is not right for BinaryBring distance because the distance is not easy to arrive at smaller than 0.01

    private static List<CustomerPoint> pointList = null;  //用于存放，原始数据集所构建的点集
    private int len = 0;                          //用于记录每个数据点的维度

    private DistanceType distanceType;

    public KMeans(int k, List<CustomerPoint> pointList, DistanceType distanceType) {
        this.kNum = k;
        this.pointList = pointList;
        this.len = pointList.get(0).getPoint().length;
        this.distanceType = distanceType;
        //检查规范
        check();
    }



    /**
     * 检查规范
     */
    private void check() {
        if (kNum == 0){
            throw new IllegalArgumentException("k must be the number > 0");
        }
        if (pointList == null){
            throw new IllegalArgumentException("program can't get real data");
        }
    }


    /**
     * 随机选取中心点，构建成中心类。
     */
    private Set<Cluster> chooseCenterCluster() {
        Set<Cluster> clusterSet = new HashSet<Cluster>();
        Random random = new Random();
        for (int id = 0; id < kNum; ) {
            CustomerPoint point = pointList.get(random.nextInt(pointList.size()));
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
        for (CustomerPoint point : pointList) {
            double min_dis = Double.MAX_VALUE; //original: Integer.MAX_VALUE
            for (Cluster cluster : clusterSet) {
                double tmp_dis = 0;
                if(this.distanceType == DistanceType.BinaryBring){
                    tmp_dis = point.distanceFromBinaryBring(cluster.getCenter());//modified by mengxu 2021.12.28
                }
                else if(this.distanceType == DistanceType.Euclidean){
                    tmp_dis = point.distanceFrom(cluster.getCenter());//original
                }
                else if(this.distanceType == DistanceType.PearsonCorrelation){
                    tmp_dis = point.distanceFromPearson(cluster.getCenter());//modified by mengxu 2021.12.28
                }

                if(tmp_dis > min_dis){
                    tmp_dis = min_dis;
                }
//                float tmp_dis = (float) Math.min(disC.getEuclideanDis(point, cluster.getCenter()), min_dis);
                if (tmp_dis >= min_dis && tmp_dis <= min_dis){
                    continue;
                }
                else{
                    min_dis = tmp_dis;
                    point.setClusterId(cluster.getId());
                    point.setDist(min_dis);
                }

                //original
//                if(tmp_dis != min_dis) {
//                    min_dis = tmp_dis;
//                    point.setClusterId(cluster.getId());
//                    point.setDist(min_dis);
//                }
            }
        }
        // 新清除原来所有的类中成员。把所有的点，分别加入每个类别
        for (Cluster cluster : clusterSet) {
            cluster.getMembers().clear();
            for (CustomerPoint point : pointList) {
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
            List<CustomerPoint> point_list = cluster.getMembers();
            double[] sumAll =new double[len];
            // 所有点，对应各个维度进行求和
            for (int i = 0; i < len; i++) {
                for (int j = 0; j < point_list.size(); j++) {
                    sumAll[i] += point_list.get(j).getPoint()[i];
                }
            }
            // 计算平均值
            for (int i = 0; i < sumAll.length; i++) {
                sumAll[i] = sumAll[i]/point_list.size(); //original
                //for BinaryBringDistance this is not right!!!
                //todo: need modify for BinaryBringDistance
                //todo: a new idea, use the decision which appears the greatest times as the center for each decision point?
                //  2022.01.13
//                sumAll[i] = (int)(sumAll[i]/point_list.size()); //only for binaryBringDistance
            }
            // 计算两个新、旧中心的距离，如果任意一个类中心移动的距离大于dis_diff则继续迭代。
            if(this.distanceType == DistanceType.BinaryBring){
                if(cluster.getCenter().distanceFromBinaryBring(new CustomerPoint(sumAll)) > disDiffForBinaryBring){//modified by mengxu 2021.12.28
                    ifNeedIter = true;
                }
//                if(cluster.getCenter().distanceFromBinaryBring(new CustomerPoint(sumAll)) > disDiffForBinaryBring){//modified by mengxu 2021.12.28
//                    ifNeedIter = true;
//                }
            }
            else if(this.distanceType == DistanceType.Euclidean){
                if(cluster.getCenter().distanceFrom(new CustomerPoint(sumAll)) > disDiff){//original
                    ifNeedIter = true;
                }
            }
            else if(this.distanceType == DistanceType.PearsonCorrelation){
                if(cluster.getCenter().distanceFromPearson(new CustomerPoint(sumAll)) > disDiff){//modified by mengxu 2021.12.28
                    ifNeedIter = true;
                }
            }

            // 设置新的类中心位置
            cluster.setCenter(new CustomerPoint(sumAll));
        }
        return ifNeedIter;
    }

    /**
     * 运行 k-means
     */
    public Set<Cluster> run() {
        Set<Cluster> clusterSet= chooseCenterCluster();
        boolean ifNeedIter = true;
        while (ifNeedIter && iterRunTimes <= iterMaxTimes) {
            cluster(clusterSet);
            ifNeedIter = calculateCenter(clusterSet);
            iterRunTimes ++ ;
        }
        return clusterSet;
    }

    public List<List<CustomerPoint>> getCluster(Set<Cluster> allCluster){
        List<List<CustomerPoint>> clusterList = new ArrayList<>();
        for(Cluster cluster:allCluster){
            List<CustomerPoint> clu = cluster.getMembers();
            clusterList.add(clu);
        }
        return clusterList;
    }

    /**
     * 返回实际运行次数
     */
    public int getIterTimes() {
        return iterRunTimes;
    }

    public static void main(String[] args) {
        CustomerPoint customerPoint = new CustomerPoint(new double[] {3,8});
        CustomerPoint customerPoint1 = new CustomerPoint(new double[] {4,7});
        CustomerPoint customerPoint2 = new CustomerPoint(new double[] {4,8});
        CustomerPoint customerPoint3 = new CustomerPoint(new double[] {5,6});
        CustomerPoint customerPoint4 = new CustomerPoint(new double[] {3,9});
        CustomerPoint customerPoint5 = new CustomerPoint(new double[] {5,1});
        CustomerPoint customerPoint6 = new CustomerPoint(new double[] {5,2});
        CustomerPoint customerPoint7 = new CustomerPoint(new double[] {6,3});
        CustomerPoint customerPoint8 = new CustomerPoint(new double[] {7,3});
        CustomerPoint customerPoint9 = new CustomerPoint(new double[] {7,4});
        CustomerPoint customerPoint10 = new CustomerPoint(new double[] {0,2});
        CustomerPoint customerPoint11 = new CustomerPoint(new double[] {8,16});
        CustomerPoint customerPoint12 = new CustomerPoint(new double[] {1,1});
        CustomerPoint customerPoint13 = new CustomerPoint(new double[] {1,3});

        List<CustomerPoint> cs = new ArrayList<>();
        cs.add(customerPoint13);
        cs.add(customerPoint12);
        cs.add(customerPoint11);
        cs.add(customerPoint10);
        cs.add(customerPoint9);
        cs.add(customerPoint8);
        cs.add(customerPoint7);
        cs.add(customerPoint6);
        cs.add(customerPoint5);
        cs.add(customerPoint4);
        cs.add(customerPoint3);
        cs.add(customerPoint2);
        cs.add(customerPoint1);
        cs.add(customerPoint);//这里第一个参数为距离，第二个参数为最小邻居数量

        DistanceType type = DistanceType.BinaryBring;

        KMeans kRun =new KMeans(3, cs, type);

        Set<Cluster> clusterSet = kRun.run();
        System.out.println("单次迭代运行次数："+kRun.getIterTimes());
        for (Cluster cluster : clusterSet) {
            System.out.println(cluster);
        }
    }


}
