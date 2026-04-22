package mengxu.util;

import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.multiobjective.MultiObjectiveFitness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 箱形图提供了一种只用5个点对数据集做简单总结的方式。这5个点包括中点、Q1、Q3、分部状态的高位和低位。箱形图很形象的分为中心、延伸以及分布状态的全部范围。
 * 箱形图中最重要的是对相关统计点的计算,相关统计点都可以通过百分位计算方法进行实现。
 * 箱形图的绘制步骤： [2]
 * 1、画数轴，度量单位大小和数据批的单位一致，起点比最小值稍小，长度比该数据批的全距稍长。
 * 2、画一个矩形盒，两端边的位置分别对应数据批的上下四分位数（Q3和Q1）。在矩形盒内部中位数（Xm）位置画一条线段为中位线。
 * 3、在Q3+1.5IQR和Q1－1.5IQR处画两条与中位线一样的线段，这两条线段为异常值截断点，称其为内限；在Q3+3IQR和Q1－3IQR处画两条线段，称其为外限。处于内限以外位置的点表示的数据都是异常值，其中在内限与外限之间的异常值为温和的异常值（mild outliers），在外限以外的为极端的异常值(extreme outliers)。四分位距IQR=Q3-Q1。.
 * ————————————————
 * 版权声明：本文为CSDN博主「Lv正」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/weixin_39792316/article/details/115734041
 */

public class BoxOutliers {
    //中位数
    private double median;
    private double min;
    private double max;
    //下四分位数  0.25
    private double Q1;
    //上四分位数 0.75
    private double Q3;
    //四分位距
    private double IQR;

    //内限
    private double maxInRegion;
    private double mixInRegion;
    //外限
    private double maxOutRegion;
    private double mixOutRegion;

//    //温和异常
//    private double[] mildOutlier;
//    //极端异常
//    private double[] extremeOutlier;

    public BoxOutliers(){

    }

    /**
     * 箱形图
     *
     * @param data
     */
    public void process(double[] data) {
        List<Double> collect = Arrays.stream(data).boxed().sorted().distinct().collect(Collectors.toList());
        data = collect.stream().mapToDouble(i -> i).toArray();

        if (data.length % 2 == 0) {

            median = (data[(data.length) / 2 - 1] + data[(data.length) / 2]) / 2;

            Q1 = (data[(data.length) / 4 - 1] + data[(data.length) / 4]) / 2;

            Q3 = (data[((data.length) * 3) / 4 - 1] + data[((data.length) * 3) / 4]) / 2;
        } else {
            median = data[(data.length) / 2];
            Q1 = data[(data.length) / 4];
            Q3 = data[(data.length * 3) / 4];
        }
        //最大值
        max = data[data.length - 1];
        //最小值
        min = data[0];
        IQR = Q3 - Q1;

        //内限
        maxInRegion = Q3 + 1.5 * IQR;
        mixInRegion = Q1 - 1.5 * IQR;
        //外限
        maxOutRegion = Q3 + 3 * IQR;
        mixOutRegion = Q1 - 3 * IQR;
    }

    public void process(EvolutionState state, int objIndex) {
        Population pop = (Population) state.population;
        Individual[] inds = pop.subpops[0].individuals;




        /**
         * 2022.08.11
         * distinct() will delete the same elements in tha data[], in this case, when there are so many same elements, this will get a small
         * number of elements resaved. That is the reason why sometimes will get bug like below.
         * Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException: -1
         * 	at mengxu.util.BoxOutliers.process(BoxOutliers.java:105)
         * 	at mengxu.algorithm.multiobjective.MOEADmap.MOEADmapInitializer.updateMultiBoxOutliers(MOEADmapInitializer.java:283)
         * 	at mengxu.algorithm.multiobjective.MOEADmap.MOEADmapEvaluator.evaluatePopulation(MOEADmapEvaluator.java:12)
         * 	at mengxu.algorithm.multiobjective.MOEADmap.GPRuleEvolutionStateMOEADmap.evolve(GPRuleEvolutionStateMOEADmap.java:233)
         * 	at yimei.jss.gp.GPRuleEvolutionState.run(GPRuleEvolutionStateBase.java:309)
         * 	at yimei.jss.gp.GPRun.main(GPRun.java:70)
         */
        //original
        double[] data = new double[inds.length];
        for(int i=0; i<inds.length; i++){
            MultiObjectiveFitness fitness = (MultiObjectiveFitness)inds[i].fitness;
            data[i] = fitness.objectives[objIndex];
        }
        List<Double> collect = Arrays.stream(data).boxed().sorted().distinct().collect(Collectors.toList());
        data = collect.stream().mapToDouble(i -> i).toArray();

//        //modified Version 1 by mengxu 2022.08.11
//        List<Double> dataList = new ArrayList<>();
//        for(int i=0; i<inds.length; i++){
//            MultiObjectiveFitness fitness = (MultiObjectiveFitness)inds[i].fitness;
//            //modified by mengxu 2022.08.11
//            if(fitness.objectives[objIndex] >= Double.POSITIVE_INFINITY || fitness.objectives[objIndex] >= Double.MAX_VALUE){
//                continue;
//            }
//            else{
//                dataList.add(fitness.objectives[objIndex]);
//            }
//        }
//        Double[] dataOld = dataList.toArray(new Double[0]);
//        List<Double> collect = Arrays.stream(dataOld).sorted().distinct().collect(Collectors.toList());
//        double[] data = collect.stream().mapToDouble(i -> i).toArray();


        //Based on one single run, I think the Version 2 is better than Version 1
        //modified Version 2 by mengxu 2022.08.11
        if(data.length == 1){
            median = data[0];
            Q1 = data[0];
            Q3 = data[0];
            if(data[0] >= Double.MAX_VALUE || data[0] >= Double.POSITIVE_INFINITY){
                System.out.println("All individuals are bad run, which is not right or get overfitting!!!");
            }
        }
        else if(data.length == 2){
            median = (data[0] + data[1]) / 2;
            Q1 = data[0];
            Q3 = data[1];
            if(data[1] >= Double.MAX_VALUE || data[1] >= Double.POSITIVE_INFINITY){
                median = data[0];
                Q1 = data[0];
                Q3 = data[0];
            }
        }
        else if (data.length % 2 == 0) {
        //original
//        if (data.length % 2 == 0) {
            //todo: if data.length/4-1 or data.length/4 equal to 0, then will get wrong. But when will it be 0 and why it would be 0??? 2022.08.10

            median = (data[(data.length) / 2 - 1] + data[(data.length) / 2]) / 2;

            Q1 = (data[(data.length) / 4 - 1] + data[(data.length) / 4]) / 2;

            Q3 = (data[((data.length) * 3) / 4 - 1] + data[((data.length) * 3) / 4]) / 2;
        } else {
            median = data[(data.length) / 2];
            Q1 = data[(data.length) / 4];
            Q3 = data[(data.length * 3) / 4];
        }
        //最大值
        max = data[data.length - 1];
        //最小值
        min = data[0];
        IQR = Q3 - Q1;

        //内限
        maxInRegion = Q3 + 1.5 * IQR;
        mixInRegion = Q1 - 1.5 * IQR;
        //外限
        maxOutRegion = Q3 + 3 * IQR;
        mixOutRegion = Q1 - 3 * IQR;
    }

    public double getIQR() {
        return IQR;
    }

    public double getMax() {
        return max;
    }

    public double getMaxInRegion() {
        return maxInRegion;
    }

    public double getMaxOutRegion() {
        return maxOutRegion;
    }

    public double getMedian() {
        return median;
    }

    public double getMin() {
        return min;
    }

    public double getMixInRegion() {
        return mixInRegion;
    }

    public double getMixOutRegion() {
        return mixOutRegion;
    }

    public double getQ1() {
        return Q1;
    }

    public double getQ3() {
        return Q3;
    }

}
