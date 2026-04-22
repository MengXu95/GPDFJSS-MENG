package mengxu.complexsimulation;

import org.apache.commons.math3.random.RandomDataGenerator;
import yimei.jss.jobshop.*;
import yimei.jss.rule.AbstractRule;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.event.AbstractEvent;
import yimei.jss.simulation.event.JobArrivalEvent;
import yimei.jss.simulation.event.ProcessFinishEvent;
import yimei.jss.simulation.state.SystemState;
import yimei.util.random.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.math.BigDecimal;

public class HeterogeneousSimulation extends DynamicSimulation {

    protected AbstractRealSampler workCenterProcessingRateSampler;
    protected AbstractRealSampler transferTimeSampler;
    protected AbstractRealSampler workloadSampler;

    protected AbstractRealSampler workCenterDistanceSampler;
    protected double[] entryWorkCenterTranserTime;
    protected double[] workCenterExitTranserTime;
    protected double[][] workCenterTranserTimeMatrix;
    protected double transferRate;

    protected int timeRotateSeedForLS = 0;
    protected int SEED_ROTATION_LS = 100;
    protected int jobNumberPerCase = -1;

    //2021.09.20
    private double meanNumOperations = 0;

    //2022.03.31
    private double realMeanTransportationTime;
    private double realMeanProcessingRate;

    //2024.7.26
    protected AbstractRealSampler workCenterProcessingEnergyConsumptionSampler;
    protected AbstractRealSampler workCenterIdleEnergyConsumptionSampler;

    private HeterogeneousSimulation(long seed,
                              AbstractRule sequencingRule,
                              AbstractRule routingRule,
                              int numWorkCenters,
                              int numJobsRecorded,
                              int warmupJobs,
                              int minNumOperations,
                              int maxNumOperations,
                              double utilLevel,
                              double dueDateFactor,
                              boolean revisit,
                              AbstractIntegerSampler numOperationsSampler,
                              AbstractRealSampler workloadSampler,
                              AbstractRealSampler interArrivalTimeSampler,
                              AbstractRealSampler jobWeightSampler) {
        super(seed, sequencingRule, routingRule, numWorkCenters, numJobsRecorded, warmupJobs,
                minNumOperations, maxNumOperations, utilLevel, dueDateFactor, revisit,
                numOperationsSampler,null,interArrivalTimeSampler,
                jobWeightSampler,false);
//        //initial transfer time between workCenters which are fixed after initialization.
//        this.transferTimeSampler = new UniformSampler(5, 15);
//        double[] firstOperationUploadTime = new double[numWorkCenters];
//        double[] lastOperationDownloadTime = new double[numWorkCenters];
//        double[][] tranferTimeMatrix = new double[numWorkCenters][numWorkCenters];
//        for(int i=0; i<numWorkCenters; i++){
//            for(int j=i; j<numWorkCenters; j++){
//                if(i==j){
//                    //when the parent operation and current operation are processed on same workCenter,
//                    //then the transfer time = 0;
//                    double tranferTime = 0;
//                    tranferTimeMatrix[i][j] = tranferTime;
//                }else{
//                    double tranferTime = this.transferTimeSampler.next(randomDataGenerator);
//                    tranferTimeMatrix[i][j] = tranferTime;
//                    tranferTimeMatrix[j][i] = tranferTime;
//                    //transfer time from Machine i to Machine j equal to transfer time from Machine j to Machine i
//                }
//            }
//        }

        this.workloadSampler = workloadSampler;
        this.workCenterDistanceSampler = new UniformSampler(35, 500);
        this.entryWorkCenterTranserTime = new double[numWorkCenters];
        this.workCenterExitTranserTime  = new double[numWorkCenters];
        this.workCenterTranserTimeMatrix = new double[numWorkCenters][numWorkCenters];
//        this.transferRate = 10;
        this.transferRate = 5;
        this.realMeanTransportationTime = 0;
        double numTransportation = 0;
        for(int i=0; i<numWorkCenters; i++){
            double entryTransferDistance = this.workCenterDistanceSampler.next(randomDataGenerator);
            double exitTransferDistance = this.workCenterDistanceSampler.next(randomDataGenerator);
            this.entryWorkCenterTranserTime[i] = entryTransferDistance/this.transferRate;
            this.workCenterExitTranserTime[i] = exitTransferDistance/this.transferRate;
            for(int j=i; j<numWorkCenters; j++){
                if(i==j){
                    //when the parent operation and current operation are processed on same workCenter,
                    //then the transfer time = 0;
                    double transferDistance = 0;
                    this.workCenterTranserTimeMatrix[i][j] = transferDistance/this.transferRate;
                }else{
                    double transferDistance = this.workCenterDistanceSampler.next(randomDataGenerator);
                    this.workCenterTranserTimeMatrix[i][j] = transferDistance/this.transferRate;
                    this.workCenterTranserTimeMatrix[j][i] = transferDistance/this.transferRate;
                    //transfer time from Machine i to Machine j equal to transfer time from Machine j to Machine i
                    this.realMeanTransportationTime += this.workCenterTranserTimeMatrix[i][j];
                    numTransportation = numTransportation + 1;
                }
            }
        }

        this.realMeanTransportationTime = this.realMeanTransportationTime/numTransportation;

        //-------2024.7.26 by mengxu----------------
        this.workCenterProcessingEnergyConsumptionSampler = new UniformSampler(10, 20);
        this.workCenterIdleEnergyConsumptionSampler = new UniformSampler(1, 5);
        //-------2024.7.26 by mengxu----------------

        //this.transferTimeSampler = new UniformSampler(5, 15);
        //make different workCenters have different processing time!
        this.realMeanProcessingRate = 0;
        this.workCenterProcessingRateSampler = new UniformSampler(10, 15);
        for(WorkCenter workCenter: systemState.getWorkCenters()){
            double speed = workCenterProcessingRateSampler.next(randomDataGenerator);
            workCenter.setProcessingRate(speed);
            this.realMeanProcessingRate += speed;
            //-------2024.7.26 by mengxu----------------
            double processingEnergyConsumption = workCenterProcessingEnergyConsumptionSampler.next(randomDataGenerator);
            double idleEnergyConsumption = workCenterIdleEnergyConsumptionSampler.next(randomDataGenerator);
            workCenter.setProcessingEnergyConsumption(processingEnergyConsumption);
            workCenter.setIdleEnergyConsumption(idleEnergyConsumption);
            //-------2024.7.26 by mengxu----------------
        }
        this.realMeanProcessingRate = this.realMeanProcessingRate / numWorkCenters;

        setInterArrivalTimeSamplerMean();

        setup();
    }

    public HeterogeneousSimulation(long seed,
                                   AbstractRule sequencingRule,
                                   AbstractRule routingRule,
                                   int numWorkCenters,
                                   int numJobsRecorded,
                                   int warmupJobs,
                                   int minNumOperations,
                                   int maxNumOperations,
                                   double utilLevel,
                                   double dueDateFactor,
                                   boolean revisit) {
        this(seed, sequencingRule, routingRule, numWorkCenters, numJobsRecorded, warmupJobs,
                minNumOperations, maxNumOperations, utilLevel, dueDateFactor, revisit,
                //here, specifiy the range of UniformIntegerSample to (1,10)
                new UniformIntegerSampler(minNumOperations, maxNumOperations), //these two values will be changed during the evolutionary process, because different models are called.
                //the surrogate model will set them to 1 and 5, but the original model will set them to 1 and 10
                //when calculate the phenotype, in this code, full simulation is used, they will be set to 10 and 10.
                //modified by fzhang 17.04.2018
                //new UniformIntegerSampler(1, numWorkCenters), //in this way, whether add this parameter or not is the same
                //new UniformIntegerSampler(1, 5), //one operation only can be processed at 5 machines

                new UniformSampler(100, 1000),
                new ExponentialSampler(),
                new TwoSixTwoSampler());
    }


    //add by mengxu 2022.03.31
//    public double calculateRealMeanTransportationTime(){
//        this.workCenterTranserTimeMatrix
//    }


    @Override
    public void generateJob() {
        //modified by mengxu 2025.2.20 to only consider top and stop generate job once all the jobs are generated
        if(onlyConsiderTotalSameJobs && numJobsArrived >= warmupJobs + numJobsRecorded){
            return;
        }
        //modified by mengxu 2021.09.22================
//        //todo: need to check
//        if(this instanceof HeterogeneousSimulation) {
//            if (numJobsArrived >= warmupJobs + jobNumberPerCase && numJobsArrived % jobNumberPerCase == 0) {//500 means the number of cases for LS
//                ((HeterogeneousSimulation) this).rotateSeedForLS();
//                ((HeterogeneousSimulation) this).reseed(((HeterogeneousSimulation) this).seed);
//                    System.out.println("seed: " + (seed));
//            }
//        }
        //=============================================
        //runExperiments();
        //modified by fzhang 15.5.2018  to avoid negative time  finallly decide to keep double type: to avoid same arrival time
        double arrivalTime = getClockTime()
                + interArrivalTimeSampler.next(randomDataGenerator);
        double weight = jobWeightSampler.next(randomDataGenerator);
        Job job = new Job(numJobsArrived, new ArrayList<>(),
                arrivalTime, arrivalTime, 0, weight);
        int numOperations = numOperationsSampler.next(randomDataGenerator);

        //modified by mengxu 2021.09.02
        double[] uploadTimeForEachAvailableWorkCenter;//only for the first operation
        double[][] transferTimeFromPrecedingOperationToThisForEachAvailableWorkCenter;
        double[] downloadTimeForEachAvailableWorkCenter;//only for the last operation

        for (int i = 0; i < numOperations; i++) {
            Operation o = new Operation(job, i);
            //modified by fzhang 17.04.2018
            //int numOptions = numOptionsSampler.next(randomDataGenerator);
            int numOptions = numOperationsSampler.next(randomDataGenerator);
            //System.out.println("numOptions: "+numOptions);

            //modified by mengxu 2021.09.02
//            if(i==0){
//                uploadTimeForEachAvailableWorkCenter = new double[numOptions];
//            }

            int[] route = randomDataGenerator.nextPermutation(numWorkCenters, numOptions);
            //nextPermutation(n,k)
            //Generates an integer array of length k whose entries are selected randomly, without repetition, from the integers 0, ..., n - 1 (inclusive).

            //modified by fzhang  14.5.2018  in order to avoid negative or positive time(equal = 0)  finallly decide to keep double type
            //double procTime = procTimeSampler.next(randomDataGenerator); //use same proc time for all options for now
            //================start==========
            double workload = workloadSampler.next(randomDataGenerator);
            for (int j = 0; j < numOptions; j++) {//9
                double procTimeForThis = workload/systemState.getWorkCenter(route[j]).getProcessingRate();
                o.addOperationOption(new OperationOption(o,j,procTimeForThis,systemState.getWorkCenter(route[j])));
            }
            //==========end===========

            //modified by fzhang  29.5.2018  set different processing time for different machines
           /* for (int j = 0; j < numOptions; j++) {
            	double procTime = procTimeSampler.next(randomDataGenerator);
                o.addOperationOption(new OperationOption(o,j,procTime,systemState.getWorkCenter(route[j])));
            }
*/

            //fzhang 2019.6.22 set different processtime to each machine
            //============================start========================================================
            /*double ptmean =  procTimeSampler.next(randomDataGenerator);// set processtime of each option
            AbstractRealSampler ptnsampler=new NormalSampler(ptmean, ptmean/10);

            for (int j = 0; j < numOptions; j++) {
                double procTime= ptnsampler.next(randomDataGenerator);
                o.addOperationOption(new OperationOption(o,j,procTime,systemState.getWorkCenter(route[j])));
            }*/
            //==============================end=================================================

            job.addOperation(o);
        }

        job.linkOperations();
        //just set totalProcTime to average value, as we don't know which option will be chosen
        //this is just used to define dueDate value
        //modified by mengxu 2021.07.27
        double totalProcTime = numOperations * workloadSampler.getMean()/workCenterProcessingRateSampler.getMean();
        double dueDate = job.getReleaseTime() + dueDateFactor * totalProcTime;

        job.setDueDate(dueDate);
//        if (job.getId() > 501) {
//            int a  = 1;
//        }

        systemState.addJobToSystem(job);
        numJobsArrived ++;

        eventQueue.add(new JobArrivalEvent(job));
    }

    public void setJobNumberPerCase(int jobNumberPerCase) {
        this.jobNumberPerCase = jobNumberPerCase;
    }

    public double getEntryWorkCenterTranserTime(int workCenterID) {
        return entryWorkCenterTranserTime[workCenterID];
    }

    public double getWorkCenterExitTranserTime(int workCenterID) {
        return workCenterExitTranserTime[workCenterID];
    }

    public double getWorkCenterTranserTime(int fromWorkCenterID, int toWorkCenterID) {
        return workCenterTranserTimeMatrix[fromWorkCenterID][toWorkCenterID];
    }

    //control the inter time of job arrival
    @Override
    public double interArrivalTimeMean(int numWorkCenters,
                                       int minNumOps,
                                       int maxNumOps,
                                       double utilLevel) {
        //        double meanProcTime = workloadSampler.getMean()/(((UniformSampler)workCenterProcessingRateSampler).getUpper()); //(1+99)/2=50   average processing time for a operation is 50

        //used frequently!!! 2022.05.03
        double meanNumOps = 0.5 * (minNumOps + maxNumOps); //(1+9)/2=5.5 average operations for a job is 5.5
//        double meanProcTime = workloadSampler.getMean()/(((((UniformSampler)workCenterProcessingRateSampler).getUpper() + workCenterProcessingRateSampler.getMean()))*0.5);
//        double meanProcTime = workloadSampler.getMean()/(((UniformSampler)workCenterProcessingRateSampler).getUpper()); //2022.05.03
        double meanProcTime = workloadSampler.getMean()/(((UniformSampler)workCenterProcessingRateSampler).getMean()); //often use!!!
        double meanTransportationTime = ((UniformSampler)workCenterDistanceSampler).getLower()/transferRate; // often use!!!
        double poison = (meanNumOps * (meanProcTime + meanTransportationTime))/ (utilLevel * numWorkCenters); //often use!!!
//        double poison = (meanNumOps * (meanProcTime))/ (utilLevel * numWorkCenters); //2022.05.03

        //for checking 2022.03.31
//        double meanProcTime = workloadSampler.getMean()/(((((UniformSampler)workCenterProcessingRateSampler).getUpper() + workCenterProcessingRateSampler.getMean()))*0.5);
//        System.out.println("mean Proc time: " + meanProcTime);
//        System.out.println("real Proc Time: " + workloadSampler.getMean()/this.realMeanProcessingRate);
////        double meanTransportationTime = ((UniformSampler)workCenterDistanceSampler).getUpper()/(2 * transferRate);
//        double realTranTime = this.realMeanTransportationTime / 2;
//        System.out.println("mean Tran Time: " + meanTransportationTime);
//        System.out.println("real Tran Time: " + realTranTime);
//        double poison = (meanNumOps * (meanProcTime + realTranTime))/ (utilLevel * numWorkCenters);
//        System.out.println("poison: " + poison);
        return poison;
//        System.out.println("The meanProcTime: " + meanProcTime);
//        double leftProcTime = workloadSampler.getMean()/(((UniformSampler)workCenterProcessingRateSampler).getUpper());
//        double rightProcTime = workloadSampler.getMean()/(((UniformSampler)workCenterProcessingRateSampler).getMean());
//        System.out.println("leftProcTime: " + leftProcTime);
//        System.out.println("rightProcTime: " + rightProcTime);
//        double meanProcTime = 43;
        //todo: need modified!!! 2021.09.08 workload.getMean()/processRate.getMax() (advice from Dr. Mei)
        //for machines with same capacity, this return value is the same.
        //for machines with different capacities, this return value is different because utilLevel is dynamic
//        return (meanNumOps * meanProcTime) / (utilLevel * numWorkCenters); // the time to processing a job on each workcenter
    }

    @Override
    public void setInterArrivalTimeSamplerMean() {
        double mean = interArrivalTimeMean(numWorkCenters, minNumOperations, maxNumOperations, utilLevel);
//        System.out.println("mean: " + mean);
        interArrivalTimeSampler.setMean(mean);
    }

    public void rotateSeedForLS() {//this is use for changing seed value in next generation
        //this only relates to generation
        seed += SEED_ROTATION_LS;
//        System.out.println("rotateSeedForLS: " + seed);
        timeRotateSeedForLS++;
//        reset();
        //System.out.println(seed);//when seed=0, after Gen0, the value is 10000, after Gen1, the value is 20000....
    }

    @Override
    public void rotateSeed() {//this is use for changing seed value in next generation
        //this only relates to generation

        seed += SEED_ROTATION;
        reset();
        //System.out.println(seed);//when seed=0, after Gen0, the value is 10000, after Gen1, the value is 20000....
    }

    @Override
    public void reset() {
        //todo: add 2021.09.22
        seed -= SEED_ROTATION_LS*timeRotateSeedForLS;
        timeRotateSeedForLS = 0;

        reset(seed);
    }

    public void writeScheduleForCaseStudyToFile(Objective objective){
        List<Job> completedJobs = systemState.getJobsCompleted();
        File schedule = new File("schedule." + this.seed + "." + objective.getName() + "." +utilLevel + ".results.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(schedule));
            writer.write("jobID,operationID,machineID,startTime,completeTime,transportTime");
            writer.newLine();
            for (int i = 0; i < completedJobs.size(); i++) {
                Job job = completedJobs.get(i);
                int jobID = job.getId();

                List<ProcessFinishEvent> processFinishEvents = job.getProcessFinishEvents();
                for(int j=0; j<processFinishEvents.size(); j++){
                    ProcessFinishEvent processFinishEvent = processFinishEvents.get(j);
                    int operationID = processFinishEvent.getProcess().getOperationOption().getOperation().getId();
                    int machineID = processFinishEvent.getProcess().getOperationOption().getWorkCenter().getId();
                    double startTime = processFinishEvent.getProcess().getStartTime();
                    double completeTime = processFinishEvent.getProcess().getFinishTime();
                    Operation operation = processFinishEvent.getProcess().getOperationOption().getOperation();
                    double transportTime = -1;
                    if(j==0){
                        transportTime = operation.getUploadTime();
                    }
                    else if(j==processFinishEvents.size()){
                        transportTime = operation.getDownloadTime();
                    }
                    else{
                        transportTime = operation.getTransferTime();
                    }

                    writer.write(jobID + "," + operationID + "," + machineID
                            + "," + startTime + "," + completeTime + "," + transportTime);
                    writer.newLine();
                }
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeSystemInformationForCaseStudyToFile(Objective objective){
        //output job information
        List<Job> completedJobs = systemState.getJobsCompleted();
        File schedule = new File("systemInformation." + this.seed + "." + objective.getName() + "." +utilLevel + ".results.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(schedule));
            writer.write("jobID,arrivalTime,operationID,availMachineID,processingTime");
            writer.newLine();
            for (int i = 0; i < completedJobs.size(); i++) {
                Job job = completedJobs.get(i);
                int jobID = job.getId();
                double arrivalTime = job.getReleaseTime();
                BigDecimal bp = new BigDecimal(arrivalTime);
                arrivalTime = bp.setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();

                List<Operation> operations = job.getOperations();
                for(int j=0; j<operations.size(); j++){
                    Operation operation = operations.get(j);
                    int operationID = operation.getId();
                    List<OperationOption> operationOptions = operation.getOperationOptions();
                    for(int op=0; op<operationOptions.size(); op++){
                        OperationOption operationOption = operationOptions.get(op);
                        int availMachineID = operationOption.getWorkCenter().getId();
                        double processingTime = operationOption.getProcTime();
                        BigDecimal bp2 = new BigDecimal(processingTime);
                        processingTime = bp2.setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
                        writer.write(jobID + "," + arrivalTime + "," + operationID
                                + "," + availMachineID + "," + processingTime );
                        writer.newLine();
                    }
                }
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //output machine information
//        List<Job> completedJobs = this.;
//        File schedule = new File("systemInformation." + this.seed + "." + objective.getName() + "." +utilLevel + ".results.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
//        try {
//            BufferedWriter writer = new BufferedWriter(new FileWriter(schedule));
//            writer.write("jobID,arrivalTime,operationID,availMachineID,processingTime");
//            writer.newLine();
//            for (int i = 0; i < completedJobs.size(); i++) {
//                Job job = completedJobs.get(i);
//                int jobID = job.getId();
//                double arrivalTime = job.getReleaseTime();
//
//                List<Operation> operations = job.getOperations();
//                for(int j=0; j<operations.size(); j++){
//                    Operation operation = operations.get(j);
//                    int operationID = operation.getId();
//                    List<OperationOption> operationOptions = operation.getOperationOptions();
//                    for(int op=0; op<operationOptions.size(); op++){
//                        OperationOption operationOption = operationOptions.get(op);
//                        int availMachineID = operationOption.getWorkCenter().getId();
//                        double processingTime = operationOption.getProcTime();
//                        writer.write(jobID + "," + arrivalTime + "," + operationID
//                                + "," + availMachineID + "," + processingTime );
//                        writer.newLine();
//                    }
//                }
//            }
//
//            writer.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    public double estimateMinFlowtime(){
        double minProcTime = ((UniformSampler)workloadSampler).getLower()/(((UniformSampler)workCenterProcessingRateSampler).getUpper());
        double minTransportationTime = ((UniformSampler)workCenterDistanceSampler).getLower()/transferRate;
        return minProcTime + minTransportationTime;
    }

    public double estimateMeanProctime(){
        double meanProcTime = ((UniformSampler)workloadSampler).getMean()/(((UniformSampler)workCenterProcessingRateSampler).getMean());
        return meanProcTime;
    }

    public double estimateMaxProctime(){
        double maxProcTime = ((UniformSampler)workloadSampler).getUpper()/(((UniformSampler)workCenterProcessingRateSampler).getLower());
        return maxProcTime;
    }

    public double estimateMeanTransformationtime(){
        double meanTransportationTime = ((UniformSampler)workCenterDistanceSampler).getMean()/transferRate;
        return meanTransportationTime;
    }

    public double estimateMaxTransformationtime(){
        double maxTransportationTime = ((UniformSampler)workCenterDistanceSampler).getUpper()/transferRate;
        return maxTransportationTime;
    }

    public double estimateMiddleNumOperations(){
        double mean = (double)(((UniformIntegerSampler)this.numOperationsSampler).getLower() + ((UniformIntegerSampler)this.numOperationsSampler).getUpper())/2;
        return mean;
    }

    public double estimateMaxNumOperations(){
        return this.maxNumOperations;
    }

    @Override
    public Simulation surrogate(int numWorkCenters, int numJobsRecorded,
                                int warmupJobs) {
        int surrogateMaxNumOperations = maxNumOperations;

        AbstractIntegerSampler surrogateNumOperationsSampler = numOperationsSampler.clone();
        AbstractIntegerSampler surrogateNumOptionsSampler = numOperationsSampler.clone();
        AbstractRealSampler surrogateInterArrivalTimeSampler = interArrivalTimeSampler.clone();

        if (surrogateMaxNumOperations > numWorkCenters) {
            surrogateMaxNumOperations = numWorkCenters;
            surrogateNumOperationsSampler.setUpper(surrogateMaxNumOperations);

            surrogateInterArrivalTimeSampler.setMean(interArrivalTimeMean(numWorkCenters,
                    minNumOperations, surrogateMaxNumOperations, utilLevel));
        }

        Simulation surrogate = new HeterogeneousSimulation(seed, sequencingRule, routingRule, numWorkCenters,
                numJobsRecorded, warmupJobs, minNumOperations, surrogateMaxNumOperations,
                utilLevel, dueDateFactor, revisit, surrogateNumOperationsSampler,
                workloadSampler, surrogateInterArrivalTimeSampler, jobWeightSampler);

        //modified by fzhang 17.04.2018
       /* Simulation surrogate = new DynamicSimulation(seed, sequencingRule, routingRule, numWorkCenters,
                numJobsRecorded, warmupJobs, minNumOperations, surrogateMaxNumOperations,
                utilLevel, dueDateFactor, revisit, surrogateNumOperationsSampler,
                numOptionsSampler, procTimeSampler, surrogateInterArrivalTimeSampler, jobWeightSampler);*/

        return surrogate;
    }

    public static HeterogeneousSimulation standardFull(
            long seed,
            AbstractRule sequencingRule,
            AbstractRule routingRule,
            int numWorkCenters,
            int numJobsRecorded,
            int warmupJobs,
            double utilLevel,
            double dueDateFactor) {
        return new HeterogeneousSimulation(seed, sequencingRule, routingRule, numWorkCenters, numJobsRecorded,
                warmupJobs, numWorkCenters, numWorkCenters, utilLevel,
                dueDateFactor, false);
    }

    public static HeterogeneousSimulation standardMissing(
            long seed,
            AbstractRule sequencingRule,
            AbstractRule routingRule,
            int numWorkCenters,
            int numJobsRecorded,
            int warmupJobs,
            double utilLevel,
            double dueDateFactor) {
        return new HeterogeneousSimulation(seed, sequencingRule, routingRule, numWorkCenters, numJobsRecorded,
                warmupJobs,1, numWorkCenters, utilLevel, dueDateFactor, false);
    }
}
