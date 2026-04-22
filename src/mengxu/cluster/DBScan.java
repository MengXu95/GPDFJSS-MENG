package mengxu.cluster;

import mengxu.cluster.kmeans.DistanceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author mengxu
 *
 */
public class DBScan{
    //欧式距离
    private final double distance;
    //最低要求的寻找邻居数量
    private final int minPoints;

    private DistanceType distanceType;

    private final Map<CustomerPoint, PointStatus> visited = new HashMap<CustomerPoint, PointStatus>();
    //点的标记，point:聚合内的点，noise：噪音点
//    private enum PointStatus {
//        NOISE,POINT
//    }


    public DBScan(double distance, int minPoints, DistanceType distanceType)
            throws Exception {
        if (distance < 0.0d) {
            throw new Exception("距离小于0");
        }
        if (minPoints < 0) {
            throw new Exception("点数小于0");
        }
        this.distance = distance;
        this.minPoints = minPoints;
        this.distanceType = distanceType;
    }

    public double getDistance() {
        return distance;
    }

    public int getMinPoints() {
        return minPoints;
    }

    public Map<CustomerPoint, PointStatus> getVisited() {
        return visited;
    }
    /**
     * 返回customerPoint的多个聚合
     * @param points
     * @return
     */
    public List<List<CustomerPoint>> cluster(List<CustomerPoint> points){

        final List<List<CustomerPoint>> clusters = new ArrayList<List<CustomerPoint>>();

        for (CustomerPoint point : points) {//如果已经被标记
            if (visited.get(point) != null) {
                continue;
            }
            //modified 2022.01.13--------
            if(point.getFitness() >= Double.MAX_VALUE || point.getFitness() >= Double.POSITIVE_INFINITY){
                visited.put(point, PointStatus.NOISE);
                point.setStatus(PointStatus.NOISE);
            }
            //----------------------------
            List<CustomerPoint> neighbors = getNeighbors(point, points);

            if (neighbors.size() >= minPoints) {
                visited.put(point, PointStatus.POINT);
                point.setStatus(PointStatus.POINT);
                List<CustomerPoint> cluster = new ArrayList<CustomerPoint>();
                //遍历所有邻居继续拓展找点
                clusters.add(expandCluster(cluster, point, neighbors, points, visited));
            } else {
                visited.put(point, PointStatus.NOISE);
                point.setStatus(PointStatus.NOISE);
            }
        }

        return clusters;
    }


    private List<CustomerPoint> expandCluster( List<CustomerPoint> cluster,
                                               CustomerPoint point,
                                               List<CustomerPoint> neighbors,
                                               List<CustomerPoint> points,
                                               Map<CustomerPoint, PointStatus> visited) {
        cluster.add(point);
        visited.put(point, PointStatus.POINT);
        int index = 0;
        //遍历 所有的邻居
        while (index < neighbors.size()) {
            //移动当前的点
            CustomerPoint current = neighbors.get(index);
            PointStatus pStatus = visited.get(current);
            if (pStatus == null) {
                List<CustomerPoint> currentNeighbors = getNeighbors(current, points);
                neighbors.addAll(currentNeighbors);
            } //如果该点未被标记，将点进行标记并加入到集合中
            if (pStatus != PointStatus.POINT) {
                visited.put(current, PointStatus.POINT);
                cluster.add(current);
            }

            index++;
        }
        return cluster;
    }

    //找到所有的邻居
    private List<CustomerPoint> getNeighbors(CustomerPoint point,List<CustomerPoint> points) {
        List<CustomerPoint> neighbors = new ArrayList<CustomerPoint>();
        for (CustomerPoint neighbor : points) {
            if (visited.get(neighbor) != null) {
                continue;
            }
            if(point.getStatus() == PointStatus.NOISE){
                continue;
            }
            if(this.distanceType == DistanceType.Euclidean){
                if (neighbor.distanceFrom(point) <= distance) {
                    neighbors.add(neighbor);
                }
            }
            else if(this.distanceType == DistanceType.BinaryBring){
                if (neighbor.distanceFromBinaryBring(point) <= distance) {
                    neighbors.add(neighbor);
                }
            }
            else if(this.distanceType == DistanceType.PearsonCorrelation){
                if (neighbor.distanceFromPearson(point) <= distance) {
                    neighbors.add(neighbor);
                }
            }

        }
        return neighbors;
    }
    //做数据进行测试
    public static void main(String[] args) throws Exception {
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
        DBScan db = new DBScan(1.5, 1, DistanceType.BinaryBring); //返回结果并打印
        List<List<CustomerPoint>> aa =db.cluster(cs);
        for(int i =0;i<aa.size();i++) {
            for(int j=0;j<aa.get(i).size();j++) {
                System.out.print(aa.get(i).get(j).toString());
            }
            System.out.println();
        }
    }
}