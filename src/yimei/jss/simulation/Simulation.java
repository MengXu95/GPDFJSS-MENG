package yimei.jss.simulation;

import ec.EvolutionState;
import ec.gp.GPNode;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.Job;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.Process;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.basic.SPT;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.rule.workcenter.basic.WIQ;
import yimei.jss.simulation.event.AbstractEvent;
import yimei.jss.simulation.event.ProcessStartEvent;
import yimei.jss.simulation.state.SystemState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * The abstract simulation class for evaluating rules.
 *
 * Created by yimei on 21/11/16.
 */
public abstract class Simulation {
    @Override
    public String toString() {
        return "Simulation{" +
                "sequencingRule=" + sequencingRule +
                ", routingRule=" + routingRule +
                ", systemState=" + systemState +
                ", eventQueue=" + eventQueue +
                ", numWorkCenters=" + numWorkCenters +
                ", numJobsRecorded=" + numJobsRecorded +
                ", warmupJobs=" + warmupJobs +
                ", numJobsArrived=" + numJobsArrived +
                ", throughput=" + throughput +
                '}';
    }

//    public int times_addToQueue = 0; //add by mengxu 2022.03.28
//    public int times_OVE = 0;
//    public int sequencingRuleUseTimes = 0;

    public int useSequencingTimes = 0;
    public int noUseSequencingTimes = 0;

    public EvolutionState state = null; //add by mengxu only for pareto set learning

    public int totalDecisionNumber = 0; //add by mengxu 2023.02.24

    protected AbstractRule sequencingRule;
    protected AbstractRule routingRule;
    protected SystemState systemState;
    protected PriorityQueue<AbstractEvent> eventQueue;

    //2025.2.20 by mengxu
    public boolean onlyConsiderTotalSameJobs = true;

    protected EnsembleRule ensembleRule = null; //modified by mengxu 2021.05.08

    protected List<List<AbstractRule>> multicaseEnsembleRules; //add by mengxu 2023.01.14
    protected int numJobsPerCase; //add by mengxu 2023.01.14

//    public List<List<Double>> operationNumAtDecisionPoints;

//    //=======modified by mengxu 20210527========================
    public boolean useLS = false;
    public boolean useMultiSubpopMultiCaseEnsemble = false;//add by mengxu 2023.01.17
    public boolean warmupSame = false; //modified 2021.12.09
    public boolean useMultiCaseEnsemble = false; //add by mengxu 2022.04.26 for check the multiCaseEnsemble results
//    //==========================================================

    //=======modified by mengxu 20230315========================
//    public boolean useLS = false;
//    public boolean useMultiSubpopMultiCaseEnsemble = false;//add by mengxu 2023.01.17
//    public boolean warmupSame = false; //modified 2021.12.09
//    public boolean useMultiCaseEnsemble = false; //add by mengxu 2022.04.26 for check the multiCaseEnsemble results
    //==========================================================

    protected AbstractRule sequencingRuleWarmup = new SPT(RuleType.SEQUENCING);
    protected AbstractRule routingRuleWarmup = new WIQ(RuleType.ROUTING);
    //------------------------------------------

    //modified by mengxu 20210709
    public List<Integer> sequencingDecisions = new ArrayList<>();
    public List<Integer> routingDecisions = new ArrayList<>();
    public List<Integer> AllDecisions = new ArrayList<>();
    public int decisionSize = 40;

    protected int numWorkCenters;
    protected int numJobsRecorded;
    protected int warmupJobs;
    protected int numJobsArrived;
    protected int throughput;
    //protected int[] jobStates;

    //2021.06.21 mengxu for curve, to store some information
    public List<Integer> x_Axis = new ArrayList<>();
    public List<List<Double>> y_Axis_List = new ArrayList<>();
    public List<List<Integer>> y_WorkCenter_Axis_List = new ArrayList<>();

    //fzhang 3.6.2018  discard the individual(rule) can not complete the whole jobs well, take a long time (prefer to do part of each job)
    int beforeThroughput; //save the throughput value before updated (a job finished)
    int afterThroughput; //save the throughput value after updated (a job finished)
    int count = 0;

    public boolean useVotingEnsemble = false;

    public boolean useMeanRankEnsemble = false;

    public boolean useLinearEnsemble = false;

    public Simulation(AbstractRule sequencingRule,
                      AbstractRule routingRule,
                      int numWorkCenters,
                      int numJobsRecorded,
                      int warmupJobs) {
        this.sequencingRule = sequencingRule;
        this.routingRule = routingRule;
        this.numWorkCenters = numWorkCenters;
        this.numJobsRecorded = numJobsRecorded;
        this.warmupJobs = warmupJobs;

        systemState = new SystemState();
        systemState.setSimulation(this);//add by mengxu 2021.05.08
        if(useLS || useMultiSubpopMultiCaseEnsemble){
            systemState.jobsCompleted = Arrays.asList(new Job[numJobsRecorded]);//add by mengxu for LS 2021.05.27

//            systemState.jobsCompleted = Arrays.asList(new Job[numJobsRecorded]);//add by mengxu for LS 2021.05.27
        }
        eventQueue = new PriorityQueue<>();
        this.totalDecisionNumber = 0;
//        int[] jobStates = new int[numJobsRecorded];
//        fill(jobStates, -1);
//        this.jobStates = jobStates;
    }

    public int getNumJobsArrived() { return numJobsArrived; }
    public int getNumJobsRecorded() {
        return numJobsRecorded;
    }

    public void setMulticaseEnsembleRules(List<List<AbstractRule>> multicaseEnsembleRules) {//add by mengxu 2023.01.14
        this.multicaseEnsembleRules = multicaseEnsembleRules;
    }

    public void setNumJobsPerCase(int numJobsPerCase) {//add by mengxu 2023.01.14
        this.numJobsPerCase = numJobsPerCase;
    }


    public AbstractRule getSequencingRule() {
        //modified by mengxu 2021.05.27
        if(useLS || useMultiSubpopMultiCaseEnsemble){
            if(warmupSame){
                if(numJobsArrived>=warmupJobs){
                    return sequencingRule;
                }
                else{
                    return sequencingRuleWarmup;
                }
            }
            else{
                return sequencingRule;
            }
        }

        if(warmupSame){
            if(numJobsArrived>=warmupJobs){
                return sequencingRule;
            }
            else{
                return sequencingRuleWarmup;
            }
        }
        else{
            return sequencingRule;
        }

        //original
//        return sequencingRule;
    }

    public AbstractRule getRoutingRule() {
        if(useLS || useMultiSubpopMultiCaseEnsemble) {
            //modified by mengxu 2021.05.27
            if(warmupSame){
                if (numJobsArrived >= warmupJobs) {
                    return routingRule;
                } else {
                    return routingRuleWarmup;
                }
            }
            else{
                return routingRule;
            }
        }

        if(warmupSame){
            if (numJobsArrived >= warmupJobs) {
                return routingRule;
            } else {
                return routingRuleWarmup;
            }
        }
        else{
            return routingRule;
        }
        //original
//        return routingRule;
    }

    public int getThroughput() {
        return throughput;
    }

    public int getWarmupJobs() {
        return warmupJobs;
    }

    public SystemState getSystemState() {
        return systemState;
    }

    public PriorityQueue<AbstractEvent> getEventQueue() {
        return eventQueue;
    }

    public void setSequencingRule(AbstractRule sequencingRule) {
        this.sequencingRule = sequencingRule;
    }

//    public void setJobStates(int[] jobStates) { this.jobStates = jobStates; }

    public void setRoutingRule(AbstractRule routingRule) {
        this.routingRule = routingRule;
        //need to reset state as well, as the operationoptions associated
        //with workcenters are chosen using this routing rule, so current
        //values are outdated
        if(!useMultiCaseEnsemble){ //modified by mengxu 2023.02.07 todo: need to check this!!!
            resetState();
        }
    }

    public void setEnsembleRule(EnsembleRule ensembleRule) {//modified by mengxu 2021.05.08
        this.ensembleRule = ensembleRule;
    }

    public EnsembleRule getEnsembleRule() {
        return ensembleRule;
    }

    public double getClockTime() {
        return systemState.getClockTime();
    }

    public void addEvent(AbstractEvent event) {
        eventQueue.add(event);
    }

    //modified by mengxu 2022.03.29
//    public boolean canAddToQueue(Process process) {
//        Iterator<AbstractEvent> e = eventQueue.iterator();
//
//        if(e.hasNext()){
//            AbstractEvent a = e.next();
//            if (a instanceof ProcessStartEvent) {
//                if (((ProcessStartEvent) a).getProcess().getWorkCenter().getId() ==
//                        process.getWorkCenter().getId()) {
//                    if(((ProcessStartEvent) a).getProcess().getStartTime() < process.getFinishTime() || ((ProcessStartEvent) a).getProcess().getFinishTime() > process.getStartTime()){
//                        return false;
//                    }
//                }
//            }
//        }
//        return true;
//    }

    //original
    public boolean canAddToQueue(Process process) {
        Iterator<AbstractEvent> e = eventQueue.iterator();
        if (e.hasNext()) {
            AbstractEvent a = e.next();
            if (a instanceof ProcessStartEvent) {
                if (((ProcessStartEvent) a).getProcess().getWorkCenter().getId() ==
                        process.getWorkCenter().getId()) {
                    return false;
                }
            }
        }
        return true;
    }

    //int countBadrun =0;
    public void run() {
        //todo: need to check
//        System.out.println("seed: " + ((DynamicSimulation)this).seed);
        boolean allJobsCompleted = false;
        this.count = 0;//todo:2022.03.22
//        operationNumAtDecisionPoints = new ArrayList<>();
//        System.out.println("Simulation: ");
        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) { //numJobsRecorded == 5000
            AbstractEvent nextEvent = eventQueue.poll(); // the head of this queue, or null if this queue is empty

//            System.out.println("EventQueue's size: " + eventQueue.size());
            //fzhang 3.6.2018  fix the stuck problem
        	beforeThroughput = throughput; //save the throughput value before updated (a job finished)

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.trigger(this); //nextEvent includes many different types of events

            afterThroughput = throughput; //save the throughput value after updated (a job finished)

            if(throughput > warmupJobs & afterThroughput - beforeThroughput == 0) { //if the value was not updated
          	   count++;
            }

            //System.out.println("count "+count);
            if(count > 100000) { //original Dynamic simulation
//            if(count > 400000) { //for heterogeneous simulation
            	 count = 0;
            	 systemState.setClockTime(Double.MAX_VALUE);
                 eventQueue.clear();
//                 System.out.println("]");
//                 System.out.println("bad run reason count > 100000!");
            }



//            List<Double> operationNum = new ArrayList<>();
//            for(WorkCenter w: systemState.getWorkCenters()) {
//                operationNum.add((double)w.numOpsInQueue());
//            }
//            operationNumAtDecisionPoints.add(operationNum);



            //todo: check this! 2021.05.28
            //===================ignore busy machine here==============================
            //when nextEvent was done, check the numOpsInQueue
            for (WorkCenter w: systemState.getWorkCenters()) {
//                if (w.numOpsInQueue() > 50){
//                    System.out.println("w.numOpsInQueue() > 50!");
//                }
                if (w.numOpsInQueue() > 100) {
                    systemState.setClockTime(Double.MAX_VALUE);
                    eventQueue.clear();
//                    System.out.println("]");
//                    System.out.println("bad run reason w.numOpsInQueue() > 100!");
                    //countBadrun++;
//                    System.out.println("bad run fitness (meanFlowtime): " + this.meanFlowtime());
                }
            }


//            //2021.06.21 to store some information
//            if(x_Axis.size() == 0){
//                x_Axis.add(numJobsArrived);
//                List<Double> y_Axis_NumOperations = new ArrayList<>();
//                y_Axis_NumOperations.add(systemState.getNumOfOperationInSystem());
//                y_Axis_List.add(y_Axis_NumOperations);
//                List<Double> y_Axis_ClockTime = new ArrayList<>();
//                y_Axis_ClockTime.add(systemState.getNumOfOperationWaitingToBeDispatch());
//                y_Axis_List.add(y_Axis_ClockTime);
//                for(int i=0; i< systemState.getWorkCenters().size(); i++){
//                    List<Integer> y_Axis_WorkCenter = new ArrayList<>();
//                    y_Axis_WorkCenter.add(systemState.getWorkCenters().get(i).numOpsInQueue());
//                    y_WorkCenter_Axis_List.add(y_Axis_WorkCenter);
//                }
//            }
//            else {
//                if (x_Axis.get(x_Axis.size() - 1) == numJobsArrived) {
//                    continue;
//                } else {
//                    x_Axis.add(numJobsArrived);
//                    y_Axis_List.get(0).add(systemState.getNumOfOperationInSystem());
//                    y_Axis_List.get(1).add(systemState.getNumOfOperationWaitingToBeDispatch());
////                    y_Axis_List.get(2).add(systemState.getNumOfOperationInSystem()-y_Axis_List.get(2).get(y_Axis_List.get(2).size()-1));
//                    for(int i=0; i< systemState.getWorkCenters().size(); i++){
//                        y_WorkCenter_Axis_List.get(i).add(systemState.getWorkCenters().get(i).numOpsInQueue());
//                    }
//                }
//            }


//                for(int i=0;i<systemState.getJobsInSystem().size();i++){
//                    if(x_Axis.size()==0){
//                        x_Axis.add(systemState.getJobsInSystem().get(i).getId());
//                        y_Axis_ArriveTime.add(systemState.getJobsInSystem().get(i).getArrivalTime());
//                        y_Axis_List.add(y_Axis_ArriveTime);
//                    }
//                    else if(!x_Axis.contains(systemState.getJobsInSystem().get(i).getId())){
//                        x_Axis.add(systemState.getJobsInSystem().get(i).getId());
//                        y_Axis_ArriveTime.add(systemState.getJobsInSystem().get(i).getArrivalTime()-systemState.getJobsInSystem().get(i-1).getArrivalTime());
//                        y_Axis_List.add(y_Axis_ArriveTime);
//                    }
//                }

            //2021.05.31 modified by mengxu, to make sure all the top 5000 jobs finished
//            allJobsCompleted = true;
//            for (int i=0; i<systemState.getJobsCompleted().size();i++) {
//                Job job = systemState.getJobsCompleted().get(i);
//                if(job == null) {
//                    allJobsCompleted = false;
//                    break;
//                }
//            }
        }
//        System.out.println("================================================");
        //modified by fzhang 18.04.2018
        /*if(countBadrun>0) {
        	 System.out.println("The number of badrun grasped in simulation: "+ countBadrun);
         }*/


        if (!systemState.getJobsInSystem().isEmpty() && !(this instanceof DynamicSimulation)) {
            System.out.println("Event queue is empty but simulation is not complete.");
            System.out.println("Makespan is garbage - cannot continue.");
            System.exit(0);
        }
    }


    //add by mengxu 2023.01.14
    public void runBasedOnMultiCaseEnsembleRules(List<List<AbstractRule>> multicaseEnsembleRules,
                                                 int numJobsPerCase) {
        this.multicaseEnsembleRules = multicaseEnsembleRules;
        this.numJobsPerCase = numJobsPerCase;
        this.count = 0;//todo:2022.03.22
        int curCase = 0;
        int maxCase = multicaseEnsembleRules.size();
        int numJobsRecordedForCurCase = maxCase*numJobsPerCase;
        List<Job> remainingJobNeedToBeFinished = new ArrayList<>();
        boolean remainingDone = false;

        while (!eventQueue.isEmpty() && throughput < maxCase*numJobsPerCase) { //numJobsRecorded == 5000

            //add 2023.01.14
            //todo: this evaluation way is wrong and will give bad run for most of the cases. by mengxu 2023.02.08
            if((this.systemState.getJobsInSystem().size()+this.systemState.getJobsCompleted().size()-this.warmupJobs) % numJobsPerCase == 0 &&
                    (this.systemState.getJobsInSystem().size()+this.systemState.getJobsCompleted().size()-this.warmupJobs) > 0 &&
                    curCase < maxCase - 1){
                List<AbstractRule> rulesForCurCase = multicaseEnsembleRules.get(curCase);
                this.setSequencingRule(rulesForCurCase.get(0)); //add 2022.04.26
                this.setRoutingRule(rulesForCurCase.get(1)); //add 2022.04.26
                curCase++;
                remainingJobNeedToBeFinished.addAll(this.systemState.getJobsInSystem());
                remainingDone = false;
            }
//            else if(throughput==(maxCase-1)*numJobsPerCase && maxCase > 1 && remainingJobNeedToBeFinished.size() == 0){
//                remainingJobNeedToBeFinished.addAll(this.systemState.getJobsInSystem());
//            }
            else{
                for(int i=0; i<remainingJobNeedToBeFinished.size(); i++){
                    if(remainingJobNeedToBeFinished.get(i).getProcessFinishEvents().size() == remainingJobNeedToBeFinished.get(i).getOperations().size()){
                        remainingJobNeedToBeFinished.remove(i);
                        i--;//todo:need to check if the remove is right! 2023.01.14
                    }
                }
                if(remainingJobNeedToBeFinished.size()==0){
                    remainingDone = true;
                }
            }

            AbstractEvent nextEvent = eventQueue.poll(); // the head of this queue, or null if this queue is empty

            beforeThroughput = throughput; //save the throughput value before updated (a job finished)

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.trigger(this); //nextEvent includes many different types of events

            afterThroughput = throughput; //save the throughput value after updated (a job finished)

            if (throughput > warmupJobs & afterThroughput - beforeThroughput == 0) { //if the value was not updated
                count++;
            }

            //System.out.println("count "+count);
            if (count > 100000) { //original Dynamic simulation
//            if(count > 400000) { //for heterogeneous simulation
                count = 0;
                systemState.setClockTime(Double.MAX_VALUE);
                eventQueue.clear();
//                 System.out.println("]");
//                 System.out.println("bad run reason count > 100000!");
            }

            //todo: check this! 2021.05.28
            //===================ignore busy machine here==============================
            //when nextEvent was done, check the numOpsInQueue
            for (WorkCenter w : systemState.getWorkCenters()) {
                if (w.numOpsInQueue() > 100) {
                    systemState.setClockTime(Double.MAX_VALUE);
                    eventQueue.clear();
                }
            }
        }

        if (!systemState.getJobsInSystem().isEmpty() && !(this instanceof DynamicSimulation)) {
            System.out.println("Event queue is empty but simulation is not complete.");
            System.out.println("Makespan is garbage - cannot continue.");
            System.exit(0);
        }
    }


    //run with ensembleRules 2022.04.26
    //int countBadrun =0;
    public void runWithEnsemble(int numCase) {
        //todo: need to check
//        System.out.println("seed: " + ((DynamicSimulation)this).seed);
        boolean allJobsCompleted = false;
        this.count = 0;//todo:2022.03.22
//        operationNumAtDecisionPoints = new ArrayList<>();
//        System.out.println("Simulation: ");
        this.setSequencingRule(this.ensembleRule.getEnsembleRoutingRule().get(0)); //add 2022.04.26
        this.setRoutingRule(this.ensembleRule.getEnsembleRoutingRule().get(0)); //add 2022.04.26
        int perJobsEachCase = this.numJobsRecorded/numCase;
        int curCase = 0;

        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) { //numJobsRecorded == 5000

            //add 2022.04.26
            if(this.systemState.getJobsCompleted().size() % perJobsEachCase == 0 &&
                    this.systemState.getJobsCompleted().size() > 0 &&
                    curCase < numCase - 1){
                curCase++;
                this.setSequencingRule(this.ensembleRule.getEnsembleRoutingRule().get(curCase)); //add 2022.04.26
                this.setRoutingRule(this.ensembleRule.getEnsembleRoutingRule().get(curCase)); //add 2022.04.26
            }

            AbstractEvent nextEvent = eventQueue.poll(); // the head of this queue, or null if this queue is empty

//            System.out.println("EventQueue's size: " + eventQueue.size());
            //fzhang 3.6.2018  fix the stuck problem
            beforeThroughput = throughput; //save the throughput value before updated (a job finished)

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.trigger(this); //nextEvent includes many different types of events

            afterThroughput = throughput; //save the throughput value after updated (a job finished)

            if (throughput > warmupJobs & afterThroughput - beforeThroughput == 0) { //if the value was not updated
                count++;
            }

            if (count > 100000) { //original Dynamic simulation
                count = 0;
                systemState.setClockTime(Double.MAX_VALUE);
                eventQueue.clear();
            }


            //todo: check this! 2021.05.28
            //===================ignore busy machine here==============================
            //when nextEvent was done, check the numOpsInQueue
            for (WorkCenter w : systemState.getWorkCenters()) {
                if (w.numOpsInQueue() > 100) {
                    systemState.setClockTime(Double.MAX_VALUE);
                    eventQueue.clear();
                }
            }

        }

        if (!systemState.getJobsInSystem().isEmpty() && !(this instanceof DynamicSimulation)) {
            System.out.println("Event queue is empty but simulation is not complete.");
            System.out.println("Makespan is garbage - cannot continue.");
            System.exit(0);
        }
    }

    //run with ensembleRules 2022.04.26
    //int countBadrun =0;
    public void runWithVotingEnsembleRecordSpread(double[] ensembleContribution, double[] ensembleSpread) {
        //todo: need to check
//        System.out.println("seed: " + ((DynamicSimulation)this).seed);
        boolean allJobsCompleted = false;
        this.count = 0;//todo:2022.03.22
//        operationNumAtDecisionPoints = new ArrayList<>();
//        System.out.println("Simulation: ");
        this.useVotingEnsemble = true;
        this.useMeanRankEnsemble = false;
        this.useLinearEnsemble = false;

        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) { //numJobsRecorded == 5000

            AbstractEvent nextEvent = eventQueue.poll(); // the head of this queue, or null if this queue is empty
//            System.out.println("EventQueue's size: " + eventQueue.size());
            //fzhang 3.6.2018  fix the stuck problem
            beforeThroughput = throughput; //save the throughput value before updated (a job finished)

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.trigger(this, ensembleContribution, ensembleSpread); //nextEvent includes many different types of events

            afterThroughput = throughput; //save the throughput value after updated (a job finished)

            if (throughput > warmupJobs & afterThroughput - beforeThroughput == 0) { //if the value was not updated
                count++;
            }

            if (count > 100000) { //original Dynamic simulation
                count = 0;
                systemState.setClockTime(Double.MAX_VALUE);
                eventQueue.clear();
            }


            //todo: check this! 2021.05.28
            //===================ignore busy machine here==============================
            //when nextEvent was done, check the numOpsInQueue
            for (WorkCenter w : systemState.getWorkCenters()) {
                if (w.numOpsInQueue() > 100) {
                    systemState.setClockTime(Double.MAX_VALUE);
                    eventQueue.clear();
                }
            }

        }

        if (!systemState.getJobsInSystem().isEmpty() && !(this instanceof DynamicSimulation)) {
            System.out.println("Event queue is empty but simulation is not complete.");
            System.out.println("Makespan is garbage - cannot continue.");
            System.exit(0);
        }
    }

    //run with preference 2024.3.25
    public void runWithPreferenceEnsemble(double[] ensembleContribution) {
        //todo: need to check
//        System.out.println("seed: " + ((DynamicSimulation)this).seed);
        boolean allJobsCompleted = false;
        this.count = 0;//todo:2022.03.22
        this.useVotingEnsemble = false;
        this.useMeanRankEnsemble = false;
        this.useLinearEnsemble = true;

        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) { //numJobsRecorded == 5000

            AbstractEvent nextEvent = eventQueue.poll(); // the head of this queue, or null if this queue is empty
//            System.out.println("EventQueue's size: " + eventQueue.size());
            //fzhang 3.6.2018  fix the stuck problem
            beforeThroughput = throughput; //save the throughput value before updated (a job finished)

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.trigger(this, ensembleContribution); //nextEvent includes many different types of events

            afterThroughput = throughput; //save the throughput value after updated (a job finished)

            if (throughput > warmupJobs & afterThroughput - beforeThroughput == 0) { //if the value was not updated
                count++;
            }

            if (count > 100000) { //original Dynamic simulation
                count = 0;
                systemState.setClockTime(Double.MAX_VALUE);
                eventQueue.clear();
            }


            //todo: check this! 2021.05.28
            //===================ignore busy machine here==============================
            //when nextEvent was done, check the numOpsInQueue
            for (WorkCenter w : systemState.getWorkCenters()) {
                if (w.numOpsInQueue() > 100) {
                    systemState.setClockTime(Double.MAX_VALUE);
                    eventQueue.clear();
                }
            }

        }

        if (!systemState.getJobsInSystem().isEmpty() && !(this instanceof DynamicSimulation)) {
            System.out.println("Event queue is empty but simulation is not complete.");
            System.out.println("Makespan is garbage - cannot continue.");
            System.exit(0);
        }
    }

    //run with ensembleRules 2022.04.26
    //int countBadrun =0;
    public void runWithVotingEnsemble(double[] ensembleContribution) {
        //todo: need to check
//        System.out.println("seed: " + ((DynamicSimulation)this).seed);
        boolean allJobsCompleted = false;
        this.count = 0;//todo:2022.03.22
//        operationNumAtDecisionPoints = new ArrayList<>();
//        System.out.println("Simulation: ");
        this.useVotingEnsemble = true;
        this.useMeanRankEnsemble = false;
        this.useLinearEnsemble = false;

        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) { //numJobsRecorded == 5000

            AbstractEvent nextEvent = eventQueue.poll(); // the head of this queue, or null if this queue is empty
//            System.out.println("EventQueue's size: " + eventQueue.size());
            //fzhang 3.6.2018  fix the stuck problem
            beforeThroughput = throughput; //save the throughput value before updated (a job finished)

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.trigger(this, ensembleContribution); //nextEvent includes many different types of events

            afterThroughput = throughput; //save the throughput value after updated (a job finished)

            if (throughput > warmupJobs & afterThroughput - beforeThroughput == 0) { //if the value was not updated
                count++;
            }

            if (count > 100000) { //original Dynamic simulation
                count = 0;
                systemState.setClockTime(Double.MAX_VALUE);
                eventQueue.clear();
            }


            //todo: check this! 2021.05.28
            //===================ignore busy machine here==============================
            //when nextEvent was done, check the numOpsInQueue
            for (WorkCenter w : systemState.getWorkCenters()) {
                if (w.numOpsInQueue() > 100) {
                    systemState.setClockTime(Double.MAX_VALUE);
                    eventQueue.clear();
                }
            }

        }

        if (!systemState.getJobsInSystem().isEmpty() && !(this instanceof DynamicSimulation)) {
            System.out.println("Event queue is empty but simulation is not complete.");
            System.out.println("Makespan is garbage - cannot continue.");
            System.exit(0);
        }
    }

    public void runWithMeanRankEnsemble(double[] ensembleContribution) {
        //todo: need to check
//        System.out.println("seed: " + ((DynamicSimulation)this).seed);
        boolean allJobsCompleted = false;
        this.count = 0;//todo:2022.03.22
//        operationNumAtDecisionPoints = new ArrayList<>();
//        System.out.println("Simulation: ");
        this.useVotingEnsemble = false;
        this.useMeanRankEnsemble = true;
        this.useLinearEnsemble = false;

        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) { //numJobsRecorded == 5000

            AbstractEvent nextEvent = eventQueue.poll(); // the head of this queue, or null if this queue is empty
//            System.out.println("EventQueue's size: " + eventQueue.size());
            //fzhang 3.6.2018  fix the stuck problem
            beforeThroughput = throughput; //save the throughput value before updated (a job finished)

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.trigger(this, ensembleContribution); //nextEvent includes many different types of events

            afterThroughput = throughput; //save the throughput value after updated (a job finished)

            if (throughput > warmupJobs & afterThroughput - beforeThroughput == 0) { //if the value was not updated
                count++;
            }

            if (count > 100000) { //original Dynamic simulation
                count = 0;
                systemState.setClockTime(Double.MAX_VALUE);
                eventQueue.clear();
            }


            //todo: check this! 2021.05.28
            //===================ignore busy machine here==============================
            //when nextEvent was done, check the numOpsInQueue
            for (WorkCenter w : systemState.getWorkCenters()) {
                if (w.numOpsInQueue() > 100) {
                    systemState.setClockTime(Double.MAX_VALUE);
                    eventQueue.clear();
                }
            }

        }

        if (!systemState.getJobsInSystem().isEmpty() && !(this instanceof DynamicSimulation)) {
            System.out.println("Event queue is empty but simulation is not complete.");
            System.out.println("Makespan is garbage - cannot continue.");
            System.exit(0);
        }
    }


    //run with linear ensembleRules 2023.03.27 by mengxu
    public void runWithLinearEnsemble(double[] ensembleContribution) {
        //todo: need to check
//        System.out.println("seed: " + ((DynamicSimulation)this).seed);
        boolean allJobsCompleted = false;
        this.count = 0;//todo:2022.03.22
//        operationNumAtDecisionPoints = new ArrayList<>();
//        System.out.println("Simulation: ");
        this.useVotingEnsemble = false;
        this.useMeanRankEnsemble = false;
        this.useLinearEnsemble = true;

        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) { //numJobsRecorded == 5000

            AbstractEvent nextEvent = eventQueue.poll(); // the head of this queue, or null if this queue is empty
//            System.out.println("EventQueue's size: " + eventQueue.size());
            //fzhang 3.6.2018  fix the stuck problem
            beforeThroughput = throughput; //save the throughput value before updated (a job finished)

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.trigger(this, ensembleContribution); //nextEvent includes many different types of events

            afterThroughput = throughput; //save the throughput value after updated (a job finished)

            if (throughput > warmupJobs & afterThroughput - beforeThroughput == 0) { //if the value was not updated
                count++;
            }

            if (count > 100000) { //original Dynamic simulation
                count = 0;
                systemState.setClockTime(Double.MAX_VALUE);
                eventQueue.clear();
            }


            //todo: check this! 2021.05.28
            //===================ignore busy machine here==============================
            //when nextEvent was done, check the numOpsInQueue
            for (WorkCenter w : systemState.getWorkCenters()) {
                if (w.numOpsInQueue() > 100) {
                    systemState.setClockTime(Double.MAX_VALUE);
                    eventQueue.clear();
                }
            }

        }

        if (!systemState.getJobsInSystem().isEmpty() && !(this instanceof DynamicSimulation)) {
            System.out.println("Event queue is empty but simulation is not complete.");
            System.out.println("Makespan is garbage - cannot continue.");
            System.exit(0);
        }
    }

    //2021.6.21 modified by mengxu
//    public void writeOperationsToFile(int gen, int times){
//        File diversities = new File("mean-flowtime95" + "-" + gen +".job." + times + ".operationsAtWorkCenter.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
//        try {
//            BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
//            writer.write("DecisionPoint, WC0, WC1, WC2, WC3, WC4, WC5, WC6, WC7, WC8, WC9");
//            writer.newLine();
//            for (int dp = 0; dp < operationNumAtDecisionPoints.size(); dp++) {
//                writer.write(dp + ",");
//                List<Double> operationNum = operationNumAtDecisionPoints.get(dp);
//                for(int wc=0; wc<operationNum.size()-1; wc++){
//                    if(wc == operationNum.size()-2){
//                        writer.write(operationNum.get(wc) + "," + operationNum.get(wc+1));
//                    }
//                    else{
//                        writer.write(operationNum.get(wc) + ",");
//                    }
//                }
//                writer.newLine();
//            }
//            writer.write("busyTime" + ",");
//            for(int wc=0; wc<systemState.getWorkCenters().size()-1; wc++){
//                if(wc == systemState.getWorkCenters().size()-2){
//                    writer.write(systemState.getWorkCenters().get(wc).getBusyTime() + "," + systemState.getWorkCenters().get(wc+1).getBusyTime());
//                }
//                else{
//                    writer.write(systemState.getWorkCenters().get(wc).getBusyTime() + ",");
//                }
//            }
//            writer.newLine();
//
//            writer.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    //2021.6.21 modified by mengxu
    public void writeCurvesToFile(Objective objective, double utilLevel, int jobSeed){
        File diversities = new File(objective.toString() + "-" + utilLevel +".job." + jobSeed + ".curves.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
            writer.write("NumJobsArrive, NumOfOperationInSystem, NumOfOperationWaitingToBeDispatch");
            writer.newLine();
            for (int ind = 0; ind < x_Axis.size(); ind++) {
                if(ind == 0){
                    writer.write(x_Axis.get(ind) + "," + y_Axis_List.get(0).get(ind)
                            + "," + y_Axis_List.get(1).get(ind));
                }
                else{
                    writer.write(x_Axis.get(ind) + "," + y_Axis_List.get(0).get(ind)
                            + "," + y_Axis_List.get(1).get(ind));
                }

                writer.newLine();
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //2021.6.22 modified by mengxu
    public void writeWorkCenterCurvesToFile(Objective objective, double utilLevel, int jobSeed){
        File diversities = new File(objective.toString() + "-" + utilLevel +".workCenter.job." + jobSeed + ".curves.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
//            writer.write("NumJobsArrive, workCenter0, workCenter1, workCenter2, workCenter3, workCenter4, workCenter5, workCenter6, workCenter7, workCenter8, workCenter9");
//            writer.newLine();
//            for (int ind = 0; ind < x_Axis.size(); ind++) {
//                writer.write(x_Axis.get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(0).get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(1).get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(2).get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(3).get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(4).get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(5).get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(6).get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(7).get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(8).get(ind)
//                        + "," + y_WorkCenter_Axis_List.get(9).get(ind));
//
//                writer.newLine();
//            }

            writer.write("NumJobsArrive, workCenter0, workCenter1, workCenter2, workCenter3, workCenter4");
            writer.newLine();
            for (int ind = 0; ind < x_Axis.size(); ind++) {
                writer.write(x_Axis.get(ind)
                        + "," + y_WorkCenter_Axis_List.get(0).get(ind)
                        + "," + y_WorkCenter_Axis_List.get(1).get(ind)
                        + "," + y_WorkCenter_Axis_List.get(2).get(ind)
                        + "," + y_WorkCenter_Axis_List.get(3).get(ind)
                        + "," + y_WorkCenter_Axis_List.get(4).get(ind));

                writer.newLine();
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    private boolean eventIsDuplicate(AbstractEvent event) {
//        if (event instanceof ProcessFinishEvent) {
//            Process p = ((ProcessFinishEvent) event).getProcess();
//            //want to check whether this operation has already been performed
//            int jobId = p.getOperationOption().getJob().getId();
//            if (jobId >= 0) {
//                int jobState = jobStates[jobId];
//                int opNum = p.getOperationOption().getOperation().getId();
//                if ((jobState+1) != opNum) {
//                    //upcoming event should only be the next job in the sequence,
//                    //not a job we've already done, or one ahead of the next one
//                    return true;
//                }
//            }
//        }
//        return false;
//    }

    public void rerun() {
    	//original
    	//fzhang 2018.11.5 this is used for generate different instances in a generation.
    	//if the replications is 1, does not have influence
    	resetState();
   	
    	//reset(): reset seed value, will get the same instance
        //reset();
        run();
    }

    //modified by mengxu 2022.04.26
    public void rerunWithEnsemble(int numCase) {
        //original
        //fzhang 2018.11.5 this is used for generate different instances in a generation.
        //if the replications is 1, does not have influence
        resetState();

        //reset(): reset seed value, will get the same instance
        //reset();
        runWithEnsemble(numCase);
    }

    public void rerunWithVotingEnsembleRecordSpread(double[] ensembleContribution, double[] ensembleSpread) {
        //original
        //fzhang 2018.11.5 this is used for generate different instances in a generation.
        //if the replications is 1, does not have influence
        resetState();

        //reset(): reset seed value, will get the same instance
        //reset();
        runWithVotingEnsembleRecordSpread(ensembleContribution, ensembleSpread);
    }

    public void rerunWithVotingEnsemble(double[] ensembleContribution) {
        //original
        //fzhang 2018.11.5 this is used for generate different instances in a generation.
        //if the replications is 1, does not have influence
        resetState();

        //reset(): reset seed value, will get the same instance
        //reset();
        runWithVotingEnsemble(ensembleContribution);
    }

    public void rerunWithMeanRankEnsemble(double[] ensembleContribution) {
        //original
        //fzhang 2018.11.5 this is used for generate different instances in a generation.
        //if the replications is 1, does not have influence
        resetState();

        //reset(): reset seed value, will get the same instance
        //reset();
        runWithMeanRankEnsemble(ensembleContribution);
    }

    public void rerunWithLinearEnsemble(double[] ensembleContribution) {
        //original
        //fzhang 2018.11.5 this is used for generate different instances in a generation.
        //if the replications is 1, does not have influence
        resetState();

        //reset(): reset seed value, will get the same instance
        //reset();
        runWithLinearEnsemble(ensembleContribution);
    }


    public void completeJob(Job job) {
        //modified by mengxu
        if(useLS || useMultiSubpopMultiCaseEnsemble){
            if(job.getId() >= warmupJobs && job.getId() < numJobsRecorded + warmupJobs){
                throughput++;  //before only have this line
                count = 0;
                systemState.getJobsCompleted().set(job.getId()-warmupJobs, job);
            }
            systemState.removeJobFromSystem(job);
        }
        else if(onlyConsiderTotalSameJobs){
            if (job.getId() >= warmupJobs && job.getId() < numJobsRecorded + warmupJobs) {
                throughput++;  //before only have this line
                count = 0;
                systemState.addCompletedJob(job);
            }
        }
        else{
            if (numJobsArrived > warmupJobs && job.getId() >= 0
                && job.getId() < numJobsRecorded + warmupJobs) {
            throughput++;  //before only have this line

            count = 0;


            systemState.addCompletedJob(job);

//            int a = systemState.getJobsCompleted().size();
//            System.out.println("The number of completed jobs: "+systemState.getJobsCompleted().size());
            }
            systemState.removeJobFromSystem(job);
        }

        //original
//        if (numJobsArrived > warmupJobs && job.getId() >= 0
//                && job.getId() < numJobsRecorded + warmupJobs) {
//            throughput++;  //before only have this line
//
//            count = 0;
//
//
//            systemState.addCompletedJob(job);
//
////            int a = systemState.getJobsCompleted().size();
////            System.out.println("The number of completed jobs: "+systemState.getJobsCompleted().size());
//        }
//        systemState.removeJobFromSystem(job);
    }

    public double makespan() {
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
//            System.out.println("bad run reason JobsCompleted().size() < numJobsRecorded!");
            return Double.POSITIVE_INFINITY;
        }
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job == null){
//                System.out.println("bad run reason job = null!");
                return Double.POSITIVE_INFINITY;
            }
            else{
                double tmp = job.getCompletionTime();
                value += tmp;
            }
        }

        return value;
    }

    public double meanFlowtimeOriginal() {
        //original------------
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            value += job.flowTime();
        }
        return value / numJobsRecorded;
    }

    //2024.7.26 add by mengxu
    public double meanEnergyConsumption() {
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
            return Double.POSITIVE_INFINITY;
        }
        //modified by mengxu
        double value = 0.0;
        for (WorkCenter workCenter : systemState.getWorkCenters()) {
            if(workCenter == null){
                return Double.POSITIVE_INFINITY;
            }
            else{
                value += workCenter.getBusyTime() * workCenter.getProcessingEnergyConsumption();
                value += (systemState.getClockTime()-workCenter.getBusyTime()) * workCenter.getIdleEnergyConsumption();
                if(systemState.getClockTime() >= Double.POSITIVE_INFINITY || systemState.getClockTime() >= Double.MAX_VALUE){
                    System.out.println("For objective meanEnergyConsumption systemState.getClockTime() should not be inf!!!");
                }
//                System.out.println("Busy Time: " + workCenter.getBusyTime());
//                System.out.println("Idle Time: " + (systemState.getClockTime()-workCenter.getBusyTime()));
            }
        }
        return value / systemState.getJobsCompleted().size(); //modified by mengxu
    }

    public double meanFlowtime() {
        //2021.09.20
//        double totalProcTime = 0;
//        int totalNUMOperation = 0;
//        for(Job job:systemState.getJobsCompleted()){
//            for(ProcessFinishEvent processFinishEvent: job.getProcessFinishEvents()){
//                double procTime = processFinishEvent.getProcess().getDuration();
//                totalProcTime += procTime;
//            }
//            totalNUMOperation += job.getOperations().size();
//        }
//        double meanProcTime = totalProcTime/totalNUMOperation;
//        System.out.println("The real meanProcTime: " + meanProcTime);
        //double check modified by mengxu 2021.06.02
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
//            System.out.println("bad run reason JobsCompleted().size() < numJobsRecorded!");
            return Double.POSITIVE_INFINITY;
        }
        //modified by mengxu
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job == null){
//                System.out.println("bad run reason job = null!");
                return Double.POSITIVE_INFINITY;
            }
            else{
                value += job.flowTime();
                //add by mengxu for check the code 2022.03.23
//                for(ProcessFinishEvent  p:job.getProcessFinishEvents()){
//                    double transferTime = p.getProcess().getOperationOption().getOperation().getTransferTime();
//                    double processTime = p.getProcess().getDuration();
//                    System.out.println("transfer and process: " + transferTime + " and " + processTime);
//                }
            }
        }
        return value / systemState.getJobsCompleted().size(); //modified by mengxu

//        //original------------
//        double value = 0.0;
//        for (Job job : systemState.getJobsCompleted()) {
//            value += job.flowTime();
//        }
//        return value / systemState.getJobsCompleted().size(); //modified by mengxu
    }

    public double maxFlowtime() {
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
//            System.out.println("bad run!!!");
//            System.out.println("bad run reason JobsCompleted().size() < numJobsRecorded!");
            return Double.POSITIVE_INFINITY;
        }
        //modified by mengxu
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job == null){
//                System.out.println("bad run reason job = null!");
//                System.out.println("bad run!!!");
                return Double.POSITIVE_INFINITY;
            }
            else{
                double tmp = job.flowTime();
                if (value < tmp)
                    value = tmp;
            }
        }
        return value;

//        return Arrays.stream(multiCaseMaxFlowtime(10)).sum()/10;

//        //original------------
//        double value = 0.0;
//        for (Job job : systemState.getJobsCompleted()) {
//            double tmp = job.flowTime();
//            if (value < tmp)
//                value = tmp;
//        }
//
//        return value;
    }

    public double meanWeightedFlowtime() {
        //double check modified by mengxu 2021.06.02
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
//            System.out.println("bad run reason JobsCompleted().size() < numJobsRecorded!");
            return Double.POSITIVE_INFINITY;
        }
        //modified by mengxu
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job == null){
//                System.out.println("bad run reason job = null!");
                return Double.POSITIVE_INFINITY;
            }
            else{
                value += job.weightedFlowTime();
            }
        }

//        return value / numJobsRecorded;
        return value / systemState.getJobsCompleted().size(); //modified by mengxu

//        //original------------
//        double value = 0.0;
//        for (Job job : systemState.getJobsCompleted()) {
//            value += job.weightedFlowTime();
//        }
//
////        return value / numJobsRecorded;
//        return value / systemState.getJobsCompleted().size(); //modified by mengxu
    }

    public double maxWeightedFlowtime() {
        //double check modified by mengxu 2021.06.02
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
//            System.out.println("bad run reason JobsCompleted().size() < numJobsRecorded!");
            return Double.POSITIVE_INFINITY;
        }
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job == null){
//                System.out.println("bad run reason job = null!");
                return Double.POSITIVE_INFINITY;
            }
            else{
                double tmp = job.weightedFlowTime();
                if (value < tmp)
                    value = tmp;
            }
        }
//        double value = 0.0;
//        for (Job job : systemState.getJobsCompleted()) {
//            double tmp = job.weightedFlowTime();
//            if (value < tmp)
//                value = tmp;
//        }

        return value;
//        return Arrays.stream(multiCaseMaxWeightedFlowtime(10)).sum()/10;
    }

    public double meanTardiness() {
        //double check modified by mengxu 2021.06.02
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
            return Double.POSITIVE_INFINITY;
        }
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job == null){
                return Double.POSITIVE_INFINITY;
            }
            else{
                value += job.tardiness();
            }
        }

//        return value / numJobsRecorded;
        return value / systemState.getJobsCompleted().size(); //modified by mengxu
    }

    public double maxTardiness() {
        //double check modified by mengxu 2021.06.02
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
            return Double.POSITIVE_INFINITY;
        }
        double value = 0.0;
//        int zeroTimes = 0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job == null){
                return Double.POSITIVE_INFINITY;
            }
            else{
//                if(job.tardiness() >= 0.0 && job.tardiness() <= 0.0){
//                    zeroTimes++;
//                }
                double tmp = job.tardiness();
                if (value < tmp)
                    value = tmp;
            }
        }
//        System.out.println("No tardy jobs: " + zeroTimes);

//        double value = 0.0;
//        for (Job job : systemState.getJobsCompleted()) {
//            double tmp = job.tardiness();
//
//            if (value < tmp)
//                value = tmp;
//        }

        return value;
//        return Arrays.stream(multiCaseMaxTardiness(10)).sum()/10;
    }

    public double meanWeightedTardiness() {
        //double check modified by mengxu 2021.06.02
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
            return Double.POSITIVE_INFINITY;
        }
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job == null){
                return Double.POSITIVE_INFINITY;
            }
            else{
                value += job.weightedTardiness();
            }
        }

//        double value = 0.0;
//        for (Job job : systemState.getJobsCompleted()) {
//            value += job.weightedTardiness();
//        }

//        return value / numJobsRecorded;
        return value / systemState.getJobsCompleted().size(); //modified by mengxu
    }

    public double maxWeightedTardiness() {
        //double check modified by mengxu 2021.06.02
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
            return Double.POSITIVE_INFINITY;
        }
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job == null){
                return Double.POSITIVE_INFINITY;
            }
            else{
                double tmp = job.weightedTardiness();
                if (value < tmp)
                    value = tmp;
            }
        }

//        double value = 0.0;
//        for (Job job : systemState.getJobsCompleted()) {
//            double tmp = job.weightedTardiness();
//
//            if (value < tmp)
//                value = tmp;
//        }

        return value;
    }

    public double propTardyJobs() {
        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            if (job.getCompletionTime() > job.getDueDate())
                value ++;
        }

//        return value / numJobsRecorded;
        return value / systemState.getJobsCompleted().size(); //modified by mengxu
    }

    
    //2018.12.20 define rule size as an objective
    public int rulesize() {
    	int value = 0;
    	GPRule seqRule = null;
    	GPRule routRule = null;
    	 
    	seqRule = (GPRule) this.getSequencingRule();
    	routRule = (GPRule) this.getRoutingRule();
    	int seqRuleSize = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
    	int routRuleSize = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
   
    	value = seqRuleSize + routRuleSize;   
    	/*System.out.println("==========================");
    	System.out.println("RuleSize "+value);*/
    	return value;
    }
    
    //2019.2.26 define routing rule size as an objective
    public int rulesizeR() {
    	int value = 0;
    	GPRule routRule = null;
    	routRule = (GPRule) this.getRoutingRule();
    	int routRuleSize = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
//    	System.out.println("routRuleSize "+routRuleSize);
    	value = routRuleSize;   	
    	return value;
    }
    
    public int rulesizeS() {
    	int value = 0;
    	GPRule seqRule = null;
    	seqRule = (GPRule) this.getSequencingRule();
    	int seqRuleSize = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
//    	System.out.println("seqRuleSize "+seqRuleSize);
    	value = seqRuleSize;   	
    	return value;
    }

    //modified by mengxu 2021.04.30
    public double[] multiCaseMakespan(int numCase) {
//        int numJobPerCase = numJobsRecorded / (numCase + 1);
        int numJobPerCase = (numJobsRecorded - 1000) / numCase;
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            double tmp;
            if(job == null || throughput != numJobsRecorded){
                tmp = Double.POSITIVE_INFINITY;
//                System.out.println("job" + i + "not done, bad result!");
            }
            else{
                tmp = job.getCompletionTime();
            }
//            double tmp = job.getCompletionTime();
            if (value < tmp)
                value = tmp;
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness;
    }

    public double[] multiCaseMeanFlowtime(int numCase) {
//        int numJobPerCase = numJobsRecorded / numCase;
        int numJobPerCase = (numJobsRecorded - 1000) / numCase;
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
//        System.out.println("numJobsArrived: " + numJobsArrived);
//        System.out.println("throughput: " + throughput);
        if(throughput != numJobsRecorded){
            for(int i=0; i< numCase; i++){
                numCaseFitness[i] = Double.POSITIVE_INFINITY;
            }
            return numCaseFitness;
        }
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            if(job == null){
                value += Double.POSITIVE_INFINITY;
//                System.out.println("job" + i + "not done, bad result!");
            }
            else{
                value += job.flowTime();
            }
//            value += job.flowTime();//original
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value/numJobPerCase;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness; //modified by mengxu
    }

    public double[] multiCaseMaxFlowtime(int numCase) {
        int numJobPerCase;
        if(useLS){
            numJobPerCase = (numJobsRecorded - 1000) / numCase;
        }
        else{
            numJobPerCase = numJobsRecorded/ numCase;
        }
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
//        System.out.println("numJobsArrived: " + numJobsArrived);
//        System.out.println("throughput: " + throughput);
        if(throughput != numJobsRecorded){
            for(int i=0; i< numCase; i++){
                numCaseFitness[i] = Double.POSITIVE_INFINITY;
            }
            return numCaseFitness;
        }
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            if(job == null){
                value = Double.POSITIVE_INFINITY;
            }
            else{
                double tmp = job.flowTime();
                if (value < tmp)
                    value = tmp;
            }
//            double tmp = job.flowTime();
//            if (value < tmp)
//                value = tmp;
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness;
    }

    public double[] multiCaseMeanWeightedFlowtime(int numCase) {
//        int numJobPerCase = numJobsRecorded / (numCase + 1);
        int numJobPerCase = (numJobsRecorded - 1000) / numCase;
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
        if(throughput != numJobsRecorded){
            for(int i=0; i< numCase; i++){
                numCaseFitness[i] = Double.MAX_VALUE;
            }
            return numCaseFitness;
        }
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            if(job == null){
                value += Double.POSITIVE_INFINITY;
            }
            else{
                value += job.weightedFlowTime();
            }
//            value += job.weightedFlowTime();
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value/numJobPerCase;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness; //modified by mengxu
    }

    public double[] multiCaseMaxWeightedFlowtime(int numCase) {
        int numJobPerCase;
        if(useLS){
            numJobPerCase = (numJobsRecorded - 1000) / numCase;
        }
        else{
            numJobPerCase = numJobsRecorded/ numCase;
        }
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
        if(throughput != numJobsRecorded){
            for(int i=0; i< numCase; i++){
                numCaseFitness[i] = Double.POSITIVE_INFINITY;
            }
            return numCaseFitness;
        }
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            double tmp = job.weightedFlowTime();
            if (value < tmp)
                value = tmp;
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness;
    }

    public double[] multiCaseMeanTardiness(int numCase) {
//        int numJobPerCase = numJobsRecorded / (numCase + 1);
        int numJobPerCase = (numJobsRecorded - 1000) / numCase;
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
        if(throughput != numJobsRecorded){
            for(int i=0; i< numCase; i++){
                numCaseFitness[i] = Double.POSITIVE_INFINITY;
            }
            return numCaseFitness;
        }
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            value += job.tardiness();
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value/numJobPerCase;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness; //modified by mengxu
    }

    public double[] multiCaseMaxTardiness(int numCase) {
        int numJobPerCase;
        if(useLS){
            numJobPerCase = (numJobsRecorded - 1000) / numCase;
        }
        else{
            numJobPerCase = numJobsRecorded/ numCase;
        }
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
        if(throughput != numJobsRecorded){
            for(int i=0; i< numCase; i++){
                numCaseFitness[i] = Double.POSITIVE_INFINITY;
            }
            return numCaseFitness;
        }
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            double tmp = job.tardiness();
            if (value < tmp)
                value = tmp;
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness;
    }

    public double[] multiCaseMeanWeightedTardiness(int numCase) {
//        int numJobPerCase = numJobsRecorded / (numCase + 1);
        int numJobPerCase = (numJobsRecorded - 1000) / numCase;
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
        if(throughput != numJobsRecorded){
            for(int i=0; i< numCase; i++){
                numCaseFitness[i] = Double.POSITIVE_INFINITY;
            }
            return numCaseFitness;
        }
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            value += job.weightedTardiness();
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value/numJobPerCase;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness; //modified by mengxu
    }

    public double[] multiCaseMaxWeightedTardiness(int numCase) {
        int numJobPerCase;
        if(useLS){
            numJobPerCase = (numJobsRecorded - 1000) / numCase;
        }
        else{
            numJobPerCase = numJobsRecorded/ numCase;
        }
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
        if(throughput != numJobsRecorded){
            for(int i=0; i< numCase; i++){
                numCaseFitness[i] = Double.POSITIVE_INFINITY;
            }
            return numCaseFitness;
        }
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            double tmp = job.weightedTardiness();
            if (value < tmp)
                value = tmp;
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness;
    }

    public double[] multiCasePropTardyJobs(int numCase) {
//        int numJobPerCase = numJobsRecorded / (numCase + 1);
        int numJobPerCase = (numJobsRecorded - 1000) / numCase;
        double[] numCaseFitness = new double[numCase];
        int indexCase = 0;
        double value = 0.0;
        if(throughput != numJobsRecorded){
            for(int i=0; i< numCase; i++){
                numCaseFitness[i] = Double.POSITIVE_INFINITY;
            }
            return numCaseFitness;
        }
        for (int i=0; i<systemState.getJobsCompleted().size();i++) {
            Job job = systemState.getJobsCompleted().get(i);
            if (job.getCompletionTime() > job.getDueDate())
                value ++;
            if(i == (indexCase+1) * numJobPerCase - 1){
                numCaseFitness[indexCase] = value/numJobPerCase;
                value = 0;
                if(indexCase == numCase-1){
                    break;
                }
                indexCase++;
            }
        }

        return numCaseFitness; //modified by mengxu
    }

    public double curCaseMaxFlowtime(int startJobID, int endJobID) {
        double value = 0.0;

        for(int i=startJobID; i<endJobID;i++) {
            Job job = systemState.getJobsCompleted().get(i);
            if(job == null){
                value = Double.POSITIVE_INFINITY;
                return value;
            }
            else{
                double tmp = job.flowTime();
                if (value < tmp)
                    value = tmp;
            }
        }
        return value;
    }

    public double curCaseMeanFlowtime(int startJobID, int endJobID) {
        double value = 0.0;

        for(int i=startJobID; i<endJobID;i++) {
            Job job = systemState.getJobsCompleted().get(i);
            if(job == null){
                value = Double.POSITIVE_INFINITY;
                return value;
            }
            else{
                double tmp = job.flowTime();
                value = value + tmp;
            }
        }
        return value/(endJobID-startJobID);
    }

    public double curCaseMeanWeightedFlowtime(int startJobID, int endJobID) {
        double value = 0.0;

        for(int i=startJobID; i<endJobID;i++) {
            Job job = systemState.getJobsCompleted().get(i);
            if(job == null){
                value = Double.POSITIVE_INFINITY;
                return value;
            }
            else{
                double tmp = job.weightedFlowTime();
                value = value + tmp;
            }
        }
        return value/(endJobID-startJobID);
    }
    
    public double objectiveValue(Objective objective) {
        switch (objective) {
            case MAKESPAN:
                return makespan();
            case MEAN_FLOWTIME:
                return meanFlowtime();
            case MAX_FLOWTIME:
                return maxFlowtime();
            case MEAN_WEIGHTED_FLOWTIME:
                return meanWeightedFlowtime();
            case MAX_WEIGHTED_FLOWTIME:
                return maxWeightedFlowtime();
            case MEAN_TARDINESS:
                return meanTardiness();
            case MAX_TARDINESS:
                return maxTardiness();
            case MEAN_WEIGHTED_TARDINESS:
                return meanWeightedTardiness();
            case MAX_WEIGHTED_TARDINESS:
                return maxWeightedTardiness();
            case PROP_TARDY_JOBS:
                return propTardyJobs();
            case MEAN_ENERGY_CONSUMPTION:
                return meanEnergyConsumption();
            case RULESIZE:
            	return rulesize();
            case RULESIZER:
            	return rulesizeR();
            case RULESIZES:
            	return rulesizeS();
        }

        return -1.0;
    }

    //add 2021.12.23 mengxu
    public double[] estimateTargetValue(Objective objective, int numCase) {
        switch (objective) {
            case MEAN_FLOWTIME:
                return multiCaseMeanFlowtimeTarget(numCase);
            case MAX_FLOWTIME:
                return multiCaseMaxFlowtimeTarget(numCase);
            case MEAN_WEIGHTED_TARDINESS:
                return multiCaseMeanWeightedTardinessTarget(numCase);
        }

        return null;
    }

    //add 2021.12.23 mengxu just for mean flowtime
    public double[] multiCaseMeanFlowtimeTarget(int numCase){
        double[] estimateTargets = new double[numCase];
        int numJobPerCase = (this.getNumJobsRecorded()-1000) / numCase;
        double estimateMeanProcTime = ((HeterogeneousSimulation)this).estimateMeanProctime();
        double estimateMeanTransformationTime = ((HeterogeneousSimulation)this).estimateMeanTransformationtime();
        double estimateMeanNumOperations = ((HeterogeneousSimulation)this).estimateMiddleNumOperations();
        List<Job> allCompletedJob = this.getSystemState().getJobsCompleted();

        double estimateFlowtime = estimateMeanProcTime * estimateMeanNumOperations + estimateMeanTransformationTime * (estimateMeanNumOperations + 1);

        double estimateMeanFlowtime = estimateFlowtime;

        System.out.println("estimate meanflowtime: " + estimateMeanFlowtime);
        //todo: = 589.75 not really good estimation, also this is not changed for different utilization level 0.85 and 0.95
        //      and not different for different random seed
        //todo: if this estimate value not changed, we need to set suitable satisfy rate for different utilization level

        Arrays.fill(estimateTargets,estimateMeanFlowtime);

        return estimateTargets;

        //        int indexCase = 0;
//        double numOperations = 0.0;
//
//
//        for (int i=0; i<allCompletedJob.size();i++) {
//            Job job = allCompletedJob.get(i);
//            numOperations += job.getOperations().size();
//
//            if(i == (indexCase+1) * numJobPerCase - 1){
//                double estimateFlowtime = estimateMinProcTime * numOperations + estimateMinTransformationTime * (numOperations + 1);
//                estimateTargets[indexCase] = estimateFlowtime/numJobPerCase;
//                numOperations = 0;
//                if(indexCase == numCase-1){
//                    break;
//                }
//                indexCase++;
//            }
//        }

    }

    //add 2021.12.23 mengxu just for max flowtime
    public double[] multiCaseMaxFlowtimeTarget(int numCase){
        double[] estimateTargets = new double[numCase];
        int numJobPerCase = (this.getNumJobsRecorded()-1000) / numCase;
        double estimateMaxProcTime = ((HeterogeneousSimulation)this).estimateMaxProctime();
        double estimateMaxTransformationTime = ((HeterogeneousSimulation)this).estimateMaxTransformationtime();
        double estimateMeanNumOperations = ((HeterogeneousSimulation)this).estimateMiddleNumOperations();
        List<Job> allCompletedJob = this.getSystemState().getJobsCompleted();

        double estimateFlowtime = estimateMaxProcTime * estimateMeanNumOperations + estimateMaxTransformationTime * (estimateMeanNumOperations + 1);

        double estimateMaxFlowtime = estimateFlowtime;

        System.out.println("estimate maxflowtime: " + estimateMaxFlowtime);
        //todo: = 2100 not good estimation, too bigger

        Arrays.fill(estimateTargets,estimateMaxFlowtime);

        return estimateTargets;
    }

    //add 2021.12.23 mengxu just for max flowtime
    public double[] multiCaseMeanWeightedTardinessTarget(int numCase){
        double[] estimateTargets = new double[numCase];
        int numJobPerCase = (this.getNumJobsRecorded()-1000) / numCase;
        double estimateMeanProcTime = ((HeterogeneousSimulation)this).estimateMeanProctime();
        double estimateMeanTransformationTime = ((HeterogeneousSimulation)this).estimateMeanTransformationtime();
        double estimateMeanNumOperations = ((HeterogeneousSimulation)this).estimateMiddleNumOperations();
        double estimateMeanWeighted = ((HeterogeneousSimulation) this).jobWeightSampler.getMean();
        double estimateMeanDueDate = ((HeterogeneousSimulation) this).dueDateFactor * estimateMeanProcTime * estimateMeanNumOperations;

        double estimateFlowtime = estimateMeanProcTime * estimateMeanNumOperations + estimateMeanTransformationTime * (estimateMeanNumOperations + 1);

        double estimateMeanTardiness = Math.max(estimateFlowtime - estimateMeanDueDate, 1);

        double estimateMeanWeightedTardiness = estimateMeanWeighted * estimateMeanTardiness;

        System.out.println("estimate mean weighted tardiness: " + estimateMeanWeightedTardiness);
        //todo: = -756.5 not good estimation

        Arrays.fill(estimateTargets,estimateMeanWeightedTardiness);

        return estimateTargets;
    }


    //modified by mengxu for one instance multi case evaluation.
    public double[] objectiveValueMultiCase(Objective objective, int numCase) {
        switch (objective) {
            case MAKESPAN:
                return multiCaseMakespan(numCase);
            case MEAN_FLOWTIME:
                return multiCaseMeanFlowtime(numCase);
            case MAX_FLOWTIME:
                return multiCaseMaxFlowtime(numCase);
            case MEAN_WEIGHTED_FLOWTIME:
                return multiCaseMeanWeightedFlowtime(numCase);
            case MAX_WEIGHTED_FLOWTIME:
                return multiCaseMaxWeightedFlowtime(numCase);
            case MEAN_TARDINESS:
                return multiCaseMeanTardiness(numCase);
            case MAX_TARDINESS:
                return multiCaseMaxTardiness(numCase);
            case MEAN_WEIGHTED_TARDINESS:
                return multiCaseMeanWeightedTardiness(numCase);
            case MAX_WEIGHTED_TARDINESS:
                return multiCaseMaxWeightedTardiness(numCase);
            case PROP_TARDY_JOBS:
                return multiCasePropTardyJobs(numCase);
//            case RULESIZE:
//                return rulesize();
//            case RULESIZER:
//                return rulesizeR();
//            case RULESIZES:
//                return rulesizeS();
        }

        double[] numCaseFitness = new double[numCase];
        Arrays.fill(numCaseFitness,-1);

        return numCaseFitness;
    }

    public double objectiveValueCurCase(Objective objective, int startJobID, int endJobID) {
        switch (objective) {
//            case MAKESPAN:
//                return curCaseMakespan(curCase);
            case MEAN_FLOWTIME:
                return curCaseMeanFlowtime(startJobID, endJobID);
            case MAX_FLOWTIME:
                return curCaseMaxFlowtime(startJobID, endJobID);
            case MEAN_WEIGHTED_FLOWTIME:
                return curCaseMeanWeightedFlowtime(startJobID, endJobID);
//            case MAX_WEIGHTED_FLOWTIME:
//                return curCaseMaxWeightedFlowtime(curCase);
//            case MEAN_TARDINESS:
//                return curCaseMeanTardiness(curCase);
//            case MAX_TARDINESS:
//                return curCaseMaxTardiness(curCase);
//            case MEAN_WEIGHTED_TARDINESS:
//                return curCaseMeanWeightedTardiness(curCase);
//            case MAX_WEIGHTED_TARDINESS:
//                return curCaseMaxWeightedTardiness(curCase);
//            case PROP_TARDY_JOBS:
//                return curCasePropTardyJobs(curCase);
        }

        return -1;
    }

    //modified 2021.11.22
    //modified by mengxu for one instance multi case evaluation.
    public double objectiveValueMultiCaseWhole(Objective objective, int numCase) {
        switch (objective) {
            case MAKESPAN:
                return Arrays.stream(multiCaseMakespan(numCase)).sum();
            case MEAN_FLOWTIME:
                return Arrays.stream(multiCaseMeanFlowtime(numCase)).average().getAsDouble();
            case MAX_FLOWTIME:
                return Arrays.stream(multiCaseMaxFlowtime(numCase)).max().getAsDouble();
            case MEAN_WEIGHTED_FLOWTIME:
                return Arrays.stream(multiCaseMeanWeightedFlowtime(numCase)).average().getAsDouble();
            case MAX_WEIGHTED_FLOWTIME:
                return Arrays.stream(multiCaseMaxWeightedFlowtime(numCase)).max().getAsDouble();
            case MEAN_TARDINESS:
                return Arrays.stream(multiCaseMeanTardiness(numCase)).average().getAsDouble();
            case MAX_TARDINESS:
                return Arrays.stream(multiCaseMaxTardiness(numCase)).max().getAsDouble();
            case MEAN_WEIGHTED_TARDINESS:
                return Arrays.stream(multiCaseMeanWeightedTardiness(numCase)).average().getAsDouble();
            case MAX_WEIGHTED_TARDINESS:
                return Arrays.stream(multiCaseMaxWeightedTardiness(numCase)).max().getAsDouble();
//            case RULESIZE:
//                return rulesize();
//            case RULESIZER:
//                return rulesizeR();
//            case RULESIZES:
//                return rulesizeS();
        }


        return -1;
    }

    public double workCenterUtilLevel(int idx) {
        return systemState.getWorkCenter(idx).getBusyTime() / getClockTime();
    }

    public String workCenterUtilLevelsToString() {
        String string = "[";
        for (int i = 0; i < systemState.getWorkCenters().size(); i++) {
            string += String.format("%.3f ", workCenterUtilLevel(i));
        }
        string += "]";

        return string;
    }

    public double realUtilLevel(){
        double totalBusyTime = systemState.getTotalBusyTime();

        double totalProcessTime = 0;
        for(int j=0; j < systemState.getJobsCompleted().size(); j++){
            totalProcessTime += systemState.getJobsCompleted().get(j).getRealTotalProcTime();
        }
        return totalProcessTime/totalBusyTime;
    }

    public abstract void setup();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Simulation that = (Simulation) o;

        if (numWorkCenters != that.numWorkCenters) return false;
        if (numJobsRecorded != that.numJobsRecorded) return false;
        if (warmupJobs != that.warmupJobs) return false;
        if (numJobsArrived != that.numJobsArrived) return false;
        if (throughput != that.throughput) return false;
        if (sequencingRule != null ? !sequencingRule.equals(that.sequencingRule) : that.sequencingRule != null)
            return false;
        if (routingRule != null ? !routingRule.equals(that.routingRule) : that.routingRule != null) return false;
        if (systemState != null ? !systemState.equals(that.systemState) : that.systemState != null) return false;
        return eventQueue != null ? eventQueue.equals(that.eventQueue) : that.eventQueue == null;
    }

    @Override
    public int hashCode() {
        int result = sequencingRule != null ? sequencingRule.hashCode() : 0;
        result = 31 * result + (routingRule != null ? routingRule.hashCode() : 0);
        result = 31 * result + (systemState != null ? systemState.hashCode() : 0);
        result = 31 * result + (eventQueue != null ? eventQueue.hashCode() : 0);
        result = 31 * result + numWorkCenters;
        result = 31 * result + numJobsRecorded;
        result = 31 * result + warmupJobs;
        result = 31 * result + numJobsArrived;
        result = 31 * result + throughput;
        return result;
    }

    public abstract void resetState();
    public abstract void reset();
    public abstract void rotateSeed();
    public abstract void generateJob();
    public abstract Simulation surrogate(int numWorkCenters, int numJobsRecorded,
                                         int warmupJobs);
    public abstract Simulation surrogateBusy(int numWorkCenters, int numJobsRecorded,
                                             int warmupJobs);
}