package yimei.jss.simulation.event;

import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.Process;
import yimei.jss.jobshop.*;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.Simulation;

import java.util.List;

/**
 * Created by yimei on 22/09/16.
 */
public class ProcessFinishEvent extends AbstractEvent {

    private Process process;
    //fzhang 29.8.2018 in order to record the completion time of jobs
    protected long jobSeed;

    public ProcessFinishEvent(double time, Process process) {
        super(time);
        this.process = process;
    }

    public ProcessFinishEvent(Process process) {
        this(process.getFinishTime(), process);
    }

    @Override
    public void trigger(Simulation simulation) {
        WorkCenter workCenter = process.getWorkCenter();
        process.getOperationOption().getJob().addProcessFinishEvent(this);

//        //modified by mengxu 2021.08.31 for heterogeneous-------
//        process.getOperationOption().getOperation().setSelectedWorkCenter(workCenter);
//        //------------------------------------------------------

        if (!workCenter.getQueue().isEmpty()) {
            SequencingDecisionSituation sequencingDecisionSituation =
                    new SequencingDecisionSituation(workCenter.getQueue(), workCenter,
                            simulation.getSystemState());

            //for normalising the terminals
            if(simulation.state instanceof GPRuleEvolutionStatePSL){
                if(((GPRuleEvolutionStatePSL) simulation.state).terminalNormalisation==0){
                    ((GPRuleEvolutionStatePSL) simulation.state).updateMaxTerminals(sequencingDecisionSituation);
                }
            }

//            //to check the size of waiting queue:
            if(workCenter.getQueue().size() > 1){
                simulation.useSequencingTimes++;
//                System.out.print(workCenter.getQueue().size() + ", ");
//                System.out.println("Waiting queue size when sequencing decision point arrive: " + workCenter.getQueue().size());
            }
            else{
                simulation.noUseSequencingTimes++;
            }

//            //test
//            System.out.print("SequencingDecisionSituation: ");
//            for(OperationOption op:sequencingDecisionSituation.getQueue()){
//                System.out.print(op.getOptionId() + ", ");
//            }
//            System.out.println();

            //System.out.println("=======================================sequencing==========================================");
            OperationOption dispatchedOp =
                    simulation.getSequencingRule().priorOperation(sequencingDecisionSituation);

            //for checking add by mengxu 2022.03.28
//            if(sequencingDecisionSituation.getQueue().size()>1){
//                simulation.sequencingRuleUseTimes++;
//            }


            if (simulation.sequencingDecisions.size()<20) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
                simulation.sequencingDecisions.add(dispatchedOp.getJob().getId()+dispatchedOp.getOperation().getId()+dispatchedOp.getOptionId());
            }
//            if (workCenter.getQueue().size() == 7 && simulation.getThroughput() > simulation.getWarmupJobs() && simulation.AllDecisions.size()<40) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
//                simulation.AllDecisions.add(dispatchedOp.getJob().getId()+dispatchedOp.getOperation().getId()+dispatchedOp.getOptionId());
//            }
//            if (workCenter.getQueue().size() == 7 && simulation.AllDecisions.size()<40) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
//                simulation.AllDecisions.add(dispatchedOp.getJob().getId()+dispatchedOp.getOperation().getId()+dispatchedOp.getOptionId());
//            }
            if (simulation.AllDecisions.size()<simulation.decisionSize) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
                simulation.AllDecisions.add(dispatchedOp.getJob().getId()+dispatchedOp.getOperation().getId()+dispatchedOp.getOptionId());
            }
//            if (simulation.AllDecisions.size()<simulation.decisionSize) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
//                simulation.AllDecisions.add(dispatchedOp.hashCode());
//            }


//            if(simulation.getEnsembleRule() != null){//modified by mengxu 2021.05.08 for ensemble method
//                dispatchedOp = simulation.getEnsembleRule().priorOperationVoting(sequencingDecisionSituation);
//            }

            workCenter.removeFromQueue(dispatchedOp);

            //must wait for machine to be ready
            double processStartTime = Math.max(workCenter.getReadyTime(), time);

            Process nextP = new Process(workCenter, process.getMachineId(),
                    dispatchedOp, processStartTime);
            simulation.addEvent(new ProcessStartEvent(nextP));
        }

        //change the order!!! modified by mengxu 2021.06.23
        OperationOption nextOp = process.getOperationOption().getNext(simulation.getSystemState(),
                simulation.getRoutingRule());

        //modified by mengxu 2021.07.09
        if (process.getOperationOption().getOperation().getNext() != null) {
            if (simulation.routingDecisions.size()<20) {
                simulation.routingDecisions.add(nextOp.getJob().getId()+nextOp.getOperation().getId()+nextOp.getOptionId());
            }
//            if (process.getOperationOption().getOperation().getNext().getOperationOptions().size()
//                    == 7 && simulation.getThroughput() > simulation.getWarmupJobs() && simulation.AllDecisions.size()<40) {
//                simulation.AllDecisions.add(nextOp.getJob().getId()+nextOp.getOperation().getId()+nextOp.getOptionId());
//            }
//            if (process.getOperationOption().getOperation().getNext().getOperationOptions().size()
//                    == 7  && simulation.AllDecisions.size()<40) {
//                simulation.AllDecisions.add(nextOp.getJob().getId()+nextOp.getOperation().getId()+nextOp.getOptionId());
//            }
            if (simulation.AllDecisions.size()<simulation.decisionSize) {
                simulation.AllDecisions.add(nextOp.getJob().getId()+nextOp.getOperation().getId()+nextOp.getOptionId());
            }
//            if (simulation.AllDecisions.size()<simulation.decisionSize) {
//                simulation.AllDecisions.add(nextOp.hashCode());
//            }
//            if (process.getOperationOption().getOperation().getNext().getOperationOptions().size()
//                    == 7 && simulation.routingDecisions.size()<20) {
//                simulation.routingDecisions.add(nextOp.getJob().getId()+nextOp.getOperation().getId()+nextOp.getOptionId());
//            }
        }

        if (nextOp == null) {
            if(simulation instanceof HeterogeneousSimulation) {
                int workCenterID = process.getOperationOption().getWorkCenter().getId();
                double downloadTime = ((HeterogeneousSimulation) simulation).getWorkCenterExitTranserTime(workCenterID);
                process.getOperationOption().getOperation().setDownloadTimeToSelectedWorkCenter(downloadTime);
                LastOperationDownloadProcess downloadProcess = new LastOperationDownloadProcess(process.getWorkCenter(), process.getOperationOption(), time);
                simulation.addEvent(new LastOperationDownloadStartEvent(downloadProcess));
            }
            else{
                Job job = process.getOperationOption().getJob();
                job.setCompletionTime(process.getFinishTime());
                simulation.completeJob(job);
            }
//            Job job = process.getOperationOption().getJob();
//            //modified by mengxu 2021.08.31 for transfer time
//            if(simulation instanceof HeterogeneousSimulation){
//                int workCenterID = process.getOperationOption().getWorkCenter().getId();
//                double downloadTime = ((HeterogeneousSimulation) simulation).getWorkCenterExitTranserTime(workCenterID);
//                job.setCompletionTime(process.getFinishTime() + downloadTime);
//            }
//            else{
//                job.setCompletionTime(process.getFinishTime());
//            }
//            //original
////            job.setCompletionTime(process.getFinishTime());
//            simulation.completeJob(job);
        }
        else {
            //todo: check if need add a "if current workCenter == selected workCenter, if so just use OperationVisitEvent"
            //modified by mengxu 2021.08.31 for heterogeneous-------
            if(simulation instanceof HeterogeneousSimulation){
                nextOp.getOperation().setSelectedWorkCenter(nextOp.getWorkCenter());
                int fromWorkCenterID = process.getOperationOption().getWorkCenter().getId();
                int toWorkCenterID = nextOp.getWorkCenter().getId();
                double transferTime = ((HeterogeneousSimulation) simulation).getWorkCenterTranserTime(fromWorkCenterID, toWorkCenterID);
                nextOp.getOperation().setTransferTimeToSelectedWorkCenter(transferTime);
                //------------------------------------------------------
                TransportationProcess transportationProcess = new TransportationProcess(process.getOperationOption().getWorkCenter(), nextOp.getWorkCenter(), nextOp ,time);
                simulation.addEvent(new TransportationStartEvent(transportationProcess));
            }
            else{
                simulation.addEvent(new OperationVisitEvent(time, nextOp));
            }
//            //modified by mengxu 2021.08.31 for transfer time
//            if(simulation instanceof HeterogeneousSimulation){
//                int fromWorkCenterID = process.getOperationOption().getWorkCenter().getId();
//                int toWorkCenterID = nextOp.getWorkCenter().getId();
//                double transferTime = ((HeterogeneousSimulation) simulation).getWorkCenterTranserTime(fromWorkCenterID, toWorkCenterID);
//                //modified by mengxu 2021.08.31 for heterogeneous-------
//                nextOp.getOperation().setSelectedWorkCenter(nextOp.getWorkCenter());
//                //------------------------------------------------------
//                simulation.addEvent(new OperationVisitEvent(time + transferTime, nextOp));
//            }
//            else{
//                simulation.addEvent(new OperationVisitEvent(time, nextOp));
//            }
            //original
//            simulation.addEvent(new OperationVisitEvent(time, nextOp));
        }

//        OperationOption nextOp = process.getOperationOption().getNext(simulation.getSystemState(),
//                simulation.getRoutingRule());
//
//        if (nextOp == null) {
//            Job job = process.getOperationOption().getJob();
//            job.setCompletionTime(process.getFinishTime());
//            simulation.completeJob(job);
//
//            //fzhang 29.8.2018 when a job is finished, record the completion time of this job. So, we have 5000 jobs, 5000 information
//            //too much information. This is suitable in test process and set the job number a relative smaller number.
//            /*System.out.println("Job ID: "+job.getId());
//            System.out.println("Number of Operations: "+job.getOperations().size());
//            System.out.println("Arrival Time: "+job.getArrivalTime());
//            System.out.println("Completion Time: "+job.getCompletionTime()); //getCompletionTime is a time point.  flowtime = completionTime - arrivalTime
//            System.out.println("Total Processing Time: "+job.getTotalProcTime());
//            System.out.println("Average Processing Time: "+job.getAvgProcTime()); //getTotalProcTime/numOfOperations
//            System.out.println("Flow Time: "+job.flowTime());
//            System.out.println("Waiting Time: "+job.getWaitingTime());*/
//        }
//        else {
//            simulation.addEvent(new OperationVisitEvent(time, nextOp));
//        }
    }

    @Override
    public void trigger(Simulation simulation, double[] ensembleContribution) {
        WorkCenter workCenter = process.getWorkCenter();
        process.getOperationOption().getJob().addProcessFinishEvent(this);

        if (!workCenter.getQueue().isEmpty()) {
            SequencingDecisionSituation sequencingDecisionSituation =
                    new SequencingDecisionSituation(workCenter.getQueue(), workCenter,
                            simulation.getSystemState());

            //for normalising the terminals
            if(simulation.state instanceof GPRuleEvolutionStatePSL){
                if(((GPRuleEvolutionStatePSL) simulation.state).terminalNormalisation==0){
                    ((GPRuleEvolutionStatePSL) simulation.state).updateMaxTerminals(sequencingDecisionSituation);
                }
            }

//            //to check the size of waiting queue:
            if(workCenter.getQueue().size() > 1){
                simulation.useSequencingTimes++;
            }
            else{
                simulation.noUseSequencingTimes++;
            }

            //System.out.println("=======================================sequencing==========================================");

            OperationOption dispatchedOp = null;

            if(simulation.warmupSame){
                if (simulation.getNumJobsArrived() >= simulation.getWarmupJobs()) {
                    if(simulation.useVotingEnsemble){
                        simulation.totalDecisionNumber++;
                        dispatchedOp = simulation.getEnsembleRule().priorOperationVoting(sequencingDecisionSituation, ensembleContribution);
                    }
                    else if(simulation.useMeanRankEnsemble){
                        simulation.totalDecisionNumber++;
                        dispatchedOp = simulation.getEnsembleRule().priorOperationMeanRank(sequencingDecisionSituation, ensembleContribution);
                    }
                    else if(simulation.useLinearEnsemble){
                        simulation.totalDecisionNumber++;
                        dispatchedOp = simulation.getEnsembleRule().priorOperationLinear(sequencingDecisionSituation, ensembleContribution);
                    }
                    else{
                        System.out.println("Error here!");
                    }
                } else {
                    dispatchedOp =
                            simulation.getSequencingRule().priorOperation(sequencingDecisionSituation);
                }
            }
            else{
                if(simulation.useVotingEnsemble){
                    simulation.totalDecisionNumber++;
                    dispatchedOp = simulation.getEnsembleRule().priorOperationVoting(sequencingDecisionSituation, ensembleContribution);
                }
                else if(simulation.useMeanRankEnsemble){
                    simulation.totalDecisionNumber++;
                    dispatchedOp = simulation.getEnsembleRule().priorOperationMeanRank(sequencingDecisionSituation, ensembleContribution);
                }
                else if(simulation.useLinearEnsemble){
                    simulation.totalDecisionNumber++;
                    dispatchedOp = simulation.getEnsembleRule().priorOperationLinear(sequencingDecisionSituation, ensembleContribution);
                }
                else{
                    System.out.println("Error here!");
                }
            }

//            if(simulation.useVotingEnsemble){
//                simulation.totalDecisionNumber++;
//                dispatchedOp = simulation.getEnsembleRule().priorOperationVoting(sequencingDecisionSituation, ensembleContribution);
//            }
//            else{
//                System.out.println("Error here!");
//            }


            if (simulation.sequencingDecisions.size()<20) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
                simulation.sequencingDecisions.add(dispatchedOp.getJob().getId()+dispatchedOp.getOperation().getId()+dispatchedOp.getOptionId());
            }
            if (simulation.AllDecisions.size()<simulation.decisionSize) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
                simulation.AllDecisions.add(dispatchedOp.getJob().getId()+dispatchedOp.getOperation().getId()+dispatchedOp.getOptionId());
            }


            workCenter.removeFromQueue(dispatchedOp);

            //must wait for machine to be ready
            double processStartTime = Math.max(workCenter.getReadyTime(), time);

            Process nextP = new Process(workCenter, process.getMachineId(),
                    dispatchedOp, processStartTime);
            simulation.addEvent(new ProcessStartEvent(nextP));
        }

        //change the order!!! modified by mengxu 2021.06.23

        OperationOption nextOp = null;

        if(simulation.warmupSame){
            if (simulation.getNumJobsArrived() >= simulation.getWarmupJobs()) {
                if(simulation.useVotingEnsemble){
                    nextOp = process.getOperationOption().getNextByEnsembleVoting(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
                    if(nextOp != null){
                        simulation.totalDecisionNumber++;
                    }
                }
                else if(simulation.useMeanRankEnsemble){
                    nextOp = process.getOperationOption().getNextByEnsembleMeanRank(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
                    if(nextOp != null){
                        simulation.totalDecisionNumber++;
                    }
                }
                else if(simulation.useLinearEnsemble){
                    nextOp = process.getOperationOption().getNextByEnsembleLinear(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
                    if(nextOp != null){
                        simulation.totalDecisionNumber++;
                    }
                }
                else{
                    System.out.println("Error here!");
                }
            } else {
                nextOp = process.getOperationOption().getNext(simulation.getSystemState(),
                        simulation.getRoutingRule());
            }
        }
        else{
            if(simulation.useVotingEnsemble){
                nextOp = process.getOperationOption().getNextByEnsembleVoting(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
                if(nextOp != null){
                    simulation.totalDecisionNumber++;
                }
            }
            else if(simulation.useMeanRankEnsemble){
                nextOp = process.getOperationOption().getNextByEnsembleMeanRank(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
                if(nextOp != null){
                    simulation.totalDecisionNumber++;
                }
            }
            else if(simulation.useLinearEnsemble){
                nextOp = process.getOperationOption().getNextByEnsembleLinear(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
                if(nextOp != null){
                    simulation.totalDecisionNumber++;
                }
            }
            else{
                System.out.println("Error here!");
            }
        }

//        if(simulation.useVotingEnsemble){
//            nextOp = process.getOperationOption().getNextByEnsemble(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
//            if(nextOp != null){
//                simulation.totalDecisionNumber++;
//            }
//        }
//        else{
//            System.out.println("Error here!");
//        }

        //modified by mengxu 2021.07.09
        if (process.getOperationOption().getOperation().getNext() != null) {
            if (simulation.routingDecisions.size()<20) {
                simulation.routingDecisions.add(nextOp.getJob().getId()+nextOp.getOperation().getId()+nextOp.getOptionId());
            }
            if (simulation.AllDecisions.size()<simulation.decisionSize) {
                simulation.AllDecisions.add(nextOp.getJob().getId()+nextOp.getOperation().getId()+nextOp.getOptionId());
            }
        }

        if (nextOp == null) {
            if(simulation instanceof HeterogeneousSimulation) {
                int workCenterID = process.getOperationOption().getWorkCenter().getId();
                double downloadTime = ((HeterogeneousSimulation) simulation).getWorkCenterExitTranserTime(workCenterID);
                process.getOperationOption().getOperation().setDownloadTimeToSelectedWorkCenter(downloadTime);
                LastOperationDownloadProcess downloadProcess = new LastOperationDownloadProcess(process.getWorkCenter(), process.getOperationOption(), time);
                simulation.addEvent(new LastOperationDownloadStartEvent(downloadProcess));
            }
            else{
                Job job = process.getOperationOption().getJob();
                job.setCompletionTime(process.getFinishTime());
                simulation.completeJob(job);
            }
        }
        else {
            //todo: check if need add a "if current workCenter == selected workCenter, if so just use OperationVisitEvent"
            //modified by mengxu 2021.08.31 for heterogeneous-------
            if(simulation instanceof HeterogeneousSimulation){
                nextOp.getOperation().setSelectedWorkCenter(nextOp.getWorkCenter());
                int fromWorkCenterID = process.getOperationOption().getWorkCenter().getId();
                int toWorkCenterID = nextOp.getWorkCenter().getId();
                double transferTime = ((HeterogeneousSimulation) simulation).getWorkCenterTranserTime(fromWorkCenterID, toWorkCenterID);
                nextOp.getOperation().setTransferTimeToSelectedWorkCenter(transferTime);
                //------------------------------------------------------
                TransportationProcess transportationProcess = new TransportationProcess(process.getOperationOption().getWorkCenter(), nextOp.getWorkCenter(), nextOp ,time);
                simulation.addEvent(new TransportationStartEvent(transportationProcess));
            }
            else{
                simulation.addEvent(new OperationVisitEvent(time, nextOp));
            }
        }
    }

    @Override
    public void trigger(Simulation simulation, double[] ensembleContribution, double[] ensembleSpread) {
        WorkCenter workCenter = process.getWorkCenter();
        process.getOperationOption().getJob().addProcessFinishEvent(this);

        if (!workCenter.getQueue().isEmpty()) {
            SequencingDecisionSituation sequencingDecisionSituation =
                    new SequencingDecisionSituation(workCenter.getQueue(), workCenter,
                            simulation.getSystemState());

            //for normalising the terminals
            if(simulation.state instanceof GPRuleEvolutionStatePSL){
                if(((GPRuleEvolutionStatePSL) simulation.state).terminalNormalisation==0){
                    ((GPRuleEvolutionStatePSL) simulation.state).updateMaxTerminals(sequencingDecisionSituation);
                }
            }

//            //to check the size of waiting queue:
            if(workCenter.getQueue().size() > 1){
                simulation.useSequencingTimes++;
            }
            else{
                simulation.noUseSequencingTimes++;
            }

            //System.out.println("=======================================sequencing==========================================");

            OperationOption dispatchedOp = null;

            if(simulation.warmupSame){
                if (simulation.getNumJobsArrived() >= simulation.getWarmupJobs()) {
                    if(simulation.useVotingEnsemble){
                        simulation.totalDecisionNumber++;
                        dispatchedOp = simulation.getEnsembleRule().priorOperationVotingRecordSpread(sequencingDecisionSituation, ensembleContribution, ensembleSpread);
                    }
                    else if(simulation.useMeanRankEnsemble){
                        simulation.totalDecisionNumber++;
                        dispatchedOp = simulation.getEnsembleRule().priorOperationMeanRank(sequencingDecisionSituation, ensembleContribution);
                    }
                    else if(simulation.useLinearEnsemble){
                        simulation.totalDecisionNumber++;
                        dispatchedOp = simulation.getEnsembleRule().priorOperationLinear(sequencingDecisionSituation, ensembleContribution);
                    }
                    else{
                        System.out.println("Error here!");
                    }
                } else {
                    dispatchedOp =
                            simulation.getSequencingRule().priorOperation(sequencingDecisionSituation);
                }
            }
            else{
                if(simulation.useVotingEnsemble){
                    simulation.totalDecisionNumber++;
                    dispatchedOp = simulation.getEnsembleRule().priorOperationVotingRecordSpread(sequencingDecisionSituation, ensembleContribution, ensembleSpread);
                }
                else if(simulation.useMeanRankEnsemble){
                    simulation.totalDecisionNumber++;
                    dispatchedOp = simulation.getEnsembleRule().priorOperationMeanRank(sequencingDecisionSituation, ensembleContribution);
                }
                else if(simulation.useLinearEnsemble){
                    simulation.totalDecisionNumber++;
                    dispatchedOp = simulation.getEnsembleRule().priorOperationLinear(sequencingDecisionSituation, ensembleContribution);
                }
                else{
                    System.out.println("Error here!");
                }
            }


            if (simulation.sequencingDecisions.size()<20) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
                simulation.sequencingDecisions.add(dispatchedOp.getJob().getId()+dispatchedOp.getOperation().getId()+dispatchedOp.getOptionId());
            }
            if (simulation.AllDecisions.size()<simulation.decisionSize) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
                simulation.AllDecisions.add(dispatchedOp.getJob().getId()+dispatchedOp.getOperation().getId()+dispatchedOp.getOptionId());
            }


            workCenter.removeFromQueue(dispatchedOp);

            //must wait for machine to be ready
            double processStartTime = Math.max(workCenter.getReadyTime(), time);

            Process nextP = new Process(workCenter, process.getMachineId(),
                    dispatchedOp, processStartTime);
            simulation.addEvent(new ProcessStartEvent(nextP));
        }

        //change the order!!! modified by mengxu 2021.06.23

        OperationOption nextOp = null;

        if(simulation.warmupSame){
            if (simulation.getNumJobsArrived() >= simulation.getWarmupJobs()) {
                if(simulation.useVotingEnsemble){
                    nextOp = process.getOperationOption().getNextByEnsembleVotingRecordSpread(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution, ensembleSpread);
                    if(nextOp != null){
                        simulation.totalDecisionNumber++;
                    }
                }
                else if(simulation.useMeanRankEnsemble){
                    nextOp = process.getOperationOption().getNextByEnsembleMeanRank(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
                    if(nextOp != null){
                        simulation.totalDecisionNumber++;
                    }
                }
                else{
                    System.out.println("Error here!");
                }
            } else {
                nextOp = process.getOperationOption().getNext(simulation.getSystemState(),
                        simulation.getRoutingRule());
            }
        }
        else{
            if(simulation.useVotingEnsemble){
                nextOp = process.getOperationOption().getNextByEnsembleVotingRecordSpread(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution, ensembleSpread);
                if(nextOp != null){
                    simulation.totalDecisionNumber++;
                }
            }
            else if(simulation.useMeanRankEnsemble){
                nextOp = process.getOperationOption().getNextByEnsembleMeanRank(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
                if(nextOp != null){
                    simulation.totalDecisionNumber++;
                }
            }
            else{
                System.out.println("Error here!");
            }
        }

//        if(simulation.useVotingEnsemble){
//            nextOp = process.getOperationOption().getNextByEnsemble(simulation.getSystemState(), simulation.getEnsembleRule(), ensembleContribution);
//            if(nextOp != null){
//                simulation.totalDecisionNumber++;
//            }
//        }
//        else{
//            System.out.println("Error here!");
//        }

        //modified by mengxu 2021.07.09
        if (process.getOperationOption().getOperation().getNext() != null) {
            if (simulation.routingDecisions.size()<20) {
                simulation.routingDecisions.add(nextOp.getJob().getId()+nextOp.getOperation().getId()+nextOp.getOptionId());
            }
            if (simulation.AllDecisions.size()<simulation.decisionSize) {
                simulation.AllDecisions.add(nextOp.getJob().getId()+nextOp.getOperation().getId()+nextOp.getOptionId());
            }
        }

        if (nextOp == null) {
            if(simulation instanceof HeterogeneousSimulation) {
                int workCenterID = process.getOperationOption().getWorkCenter().getId();
                double downloadTime = ((HeterogeneousSimulation) simulation).getWorkCenterExitTranserTime(workCenterID);
                process.getOperationOption().getOperation().setDownloadTimeToSelectedWorkCenter(downloadTime);
                LastOperationDownloadProcess downloadProcess = new LastOperationDownloadProcess(process.getWorkCenter(), process.getOperationOption(), time);
                simulation.addEvent(new LastOperationDownloadStartEvent(downloadProcess));
            }
            else{
                Job job = process.getOperationOption().getJob();
                job.setCompletionTime(process.getFinishTime());
                simulation.completeJob(job);
            }
        }
        else {
            //todo: check if need add a "if current workCenter == selected workCenter, if so just use OperationVisitEvent"
            //modified by mengxu 2021.08.31 for heterogeneous-------
            if(simulation instanceof HeterogeneousSimulation){
                nextOp.getOperation().setSelectedWorkCenter(nextOp.getWorkCenter());
                int fromWorkCenterID = process.getOperationOption().getWorkCenter().getId();
                int toWorkCenterID = nextOp.getWorkCenter().getId();
                double transferTime = ((HeterogeneousSimulation) simulation).getWorkCenterTranserTime(fromWorkCenterID, toWorkCenterID);
                nextOp.getOperation().setTransferTimeToSelectedWorkCenter(transferTime);
                //------------------------------------------------------
                TransportationProcess transportationProcess = new TransportationProcess(process.getOperationOption().getWorkCenter(), nextOp.getWorkCenter(), nextOp ,time);
                simulation.addEvent(new TransportationStartEvent(transportationProcess));
            }
            else{
                simulation.addEvent(new OperationVisitEvent(time, nextOp));
            }
        }
    }
 	
    @Override
    public void addSequencingDecisionSituation(Simulation simulation,
                                     List<SequencingDecisionSituation> situations,
                                     int minQueueLength) {
        WorkCenter workCenter = process.getWorkCenter();
        process.getOperationOption().getJob().addProcessFinishEvent(this);

        if (!workCenter.getQueue().isEmpty()) {
            SequencingDecisionSituation sequencingDecisionSituation =
                    new SequencingDecisionSituation(workCenter.getQueue(), workCenter,
                            simulation.getSystemState());

            //for normalising the terminals
            if(simulation.state instanceof GPRuleEvolutionStatePSL){
                if(((GPRuleEvolutionStatePSL) simulation.state).terminalNormalisation==0){
                    ((GPRuleEvolutionStatePSL) simulation.state).updateMaxTerminals(sequencingDecisionSituation);
                }
            }

          /*  if (workCenter.getQueue().size() >= minQueueLength) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
                situations.add(sequencingDecisionSituation.clone());
            }
*/
          //fzhang 2019.9.4 change all the decison size as minQueueLength, in order to keep the matrix has the same length
//            System.out.println("workCenter.getQueue().size(): " + workCenter.getQueue().size());
            if (workCenter.getQueue().size() == minQueueLength) { //when set operation with different processing time, the queue is hard to >= minQueueLength, an error happen here
                situations.add(sequencingDecisionSituation.clone());
            }

            OperationOption dispatchedOp =
                    simulation.getSequencingRule().priorOperation(sequencingDecisionSituation);

            workCenter.removeFromQueue(dispatchedOp);

            //must wait for machine to be ready
            double processStartTime = Math.max(workCenter.getReadyTime(), time);

            Process nextP = new Process(workCenter, process.getMachineId(),
                    dispatchedOp, processStartTime);
            simulation.addEvent(new ProcessStartEvent(nextP));
        }

        OperationOption nextOp = process.getOperationOption().getNext(simulation.getSystemState(),
                simulation.getRoutingRule());

        if (nextOp == null) {
            if(simulation instanceof HeterogeneousSimulation) {
                int workCenterID = process.getOperationOption().getWorkCenter().getId();
                double downloadTime = ((HeterogeneousSimulation) simulation).getWorkCenterExitTranserTime(workCenterID);
                process.getOperationOption().getOperation().setDownloadTimeToSelectedWorkCenter(downloadTime);
                LastOperationDownloadProcess downloadProcess = new LastOperationDownloadProcess(process.getWorkCenter(), process.getOperationOption(), time);
                simulation.addEvent(new LastOperationDownloadStartEvent(downloadProcess));
            }
            else{
                Job job = process.getOperationOption().getJob();
                job.setCompletionTime(process.getFinishTime());
                simulation.completeJob(job);
            }
        }
        else {
            //todo: check if need add a "if current workCenter == selected workCenter, if so just use OperationVisitEvent"
            //modified by mengxu 2021.08.31 for heterogeneous-------
            if(simulation instanceof HeterogeneousSimulation){
                nextOp.getOperation().setSelectedWorkCenter(nextOp.getWorkCenter());
                int fromWorkCenterID = process.getOperationOption().getWorkCenter().getId();
                int toWorkCenterID = nextOp.getWorkCenter().getId();
                double transferTime = ((HeterogeneousSimulation) simulation).getWorkCenterTranserTime(fromWorkCenterID, toWorkCenterID);
                nextOp.getOperation().setTransferTimeToSelectedWorkCenter(transferTime);
                //------------------------------------------------------
                TransportationProcess transportationProcess = new TransportationProcess(process.getOperationOption().getWorkCenter(), nextOp.getWorkCenter(), nextOp ,time);
                simulation.addEvent(new TransportationStartEvent(transportationProcess));
            }
            else{
                simulation.addEvent(new OperationVisitEvent(time, nextOp));
            }
            //original
//            simulation.addEvent(new OperationVisitEvent(time, nextOp));
        }

//        //original
//        if (nextOp == null) {
//            Job job = process.getOperationOption().getJob();
//            job.setCompletionTime(process.getFinishTime());
//            simulation.completeJob(job);
//        }
//        else {
//            simulation.addEvent(new OperationVisitEvent(time, nextOp));
//        }

    }

    @Override
    public void addRoutingDecisionSituation(Simulation simulation,
                                               List<RoutingDecisionSituation> situations,
                                               int minOptions) {
        WorkCenter workCenter = process.getWorkCenter();
        process.getOperationOption().getJob().addProcessFinishEvent(this);

        if (!workCenter.getQueue().isEmpty()) {
            SequencingDecisionSituation sequencingDecisionSituation =
                    new SequencingDecisionSituation(workCenter.getQueue(), workCenter,
                            simulation.getSystemState());

            OperationOption dispatchedOp =
                    simulation.getSequencingRule().priorOperation(sequencingDecisionSituation);

            workCenter.removeFromQueue(dispatchedOp);

            //must wait for machine to be ready
            double processStartTime = Math.max(workCenter.getReadyTime(), time);

            Process nextP = new Process(workCenter, process.getMachineId(),
                    dispatchedOp, processStartTime);
            simulation.addEvent(new ProcessStartEvent(nextP));
        }

        //fzhang 2019.9.25 change all the decison size as minQueueLength, in order to keep the matrix has the same length
        if (process.getOperationOption().getOperation().getNext() != null) {
            if (process.getOperationOption().getOperation().getNext().getOperationOptions().size()
                    == minOptions) {
                Operation o = process.getOperationOption().getOperation();
                RoutingDecisionSituation r = o.getNext().routingDecisionSituation(simulation.getSystemState());
                situations.add(r.clone());
            }
        }

        OperationOption nextOp = process.getOperationOption().getNext(simulation.getSystemState(),
                simulation.getRoutingRule());

        if (nextOp == null) {
            if(simulation instanceof HeterogeneousSimulation) {
                int workCenterID = process.getOperationOption().getWorkCenter().getId();
                double downloadTime = ((HeterogeneousSimulation) simulation).getWorkCenterExitTranserTime(workCenterID);
                process.getOperationOption().getOperation().setDownloadTimeToSelectedWorkCenter(downloadTime);
                LastOperationDownloadProcess downloadProcess = new LastOperationDownloadProcess(process.getWorkCenter(), process.getOperationOption(), time);
                simulation.addEvent(new LastOperationDownloadStartEvent(downloadProcess));
            }
            else{
                Job job = process.getOperationOption().getJob();
                job.setCompletionTime(process.getFinishTime());
                simulation.completeJob(job);
            }
        }
        else {
            //todo: check if need add a "if current workCenter == selected workCenter, if so just use OperationVisitEvent"
            //modified by mengxu 2021.08.31 for heterogeneous-------
            if(simulation instanceof HeterogeneousSimulation){
                nextOp.getOperation().setSelectedWorkCenter(nextOp.getWorkCenter());
                int fromWorkCenterID = process.getOperationOption().getWorkCenter().getId();
                int toWorkCenterID = nextOp.getWorkCenter().getId();
                double transferTime = ((HeterogeneousSimulation) simulation).getWorkCenterTranserTime(fromWorkCenterID, toWorkCenterID);
                nextOp.getOperation().setTransferTimeToSelectedWorkCenter(transferTime);
                //------------------------------------------------------
                TransportationProcess transportationProcess = new TransportationProcess(process.getOperationOption().getWorkCenter(), nextOp.getWorkCenter(), nextOp ,time);
                simulation.addEvent(new TransportationStartEvent(transportationProcess));
            }
            else{
                simulation.addEvent(new OperationVisitEvent(time, nextOp));
            }
            //original
//            simulation.addEvent(new OperationVisitEvent(time, nextOp));
        }

        //original
//        if (nextOp == null) {
//            Job job = process.getOperationOption().getJob();
//            job.setCompletionTime(process.getFinishTime());
//            simulation.completeJob(job);
//        }
//        else {
//            simulation.addEvent(new OperationVisitEvent(time, nextOp));
//        }

       /* if (process.getOperationOption().getOperation().getNext() != null) {
            if (process.getOperationOption().getOperation().getNext().getOperationOptions().size()
                    >= minOptions) {
                Operation o = process.getOperationOption().getOperation();
                RoutingDecisionSituation r = o.getNext().routingDecisionSituation(simulation.getSystemState());
                situations.add(r.clone());
            }
        }*/




    }

    @Override
    public void addSequencingDecisionSituationAllCases(Simulation simulation,
                                                       List<List<SequencingDecisionSituation>> situations,
                                                       int minQueueLength,
                                                       int jobsPerCase) {
        WorkCenter workCenter = process.getWorkCenter();
        process.getOperationOption().getJob().addProcessFinishEvent(this);

        if (!workCenter.getQueue().isEmpty()) {
            SequencingDecisionSituation sequencingDecisionSituation =
                    new SequencingDecisionSituation(workCenter.getQueue(), workCenter,
                            simulation.getSystemState());

            //for normalising the terminals
            if(simulation.state instanceof GPRuleEvolutionStatePSL){
                if(((GPRuleEvolutionStatePSL) simulation.state).terminalNormalisation==0){
                    ((GPRuleEvolutionStatePSL) simulation.state).updateMaxTerminals(sequencingDecisionSituation);
                }
            }

            //fzhang 2019.9.4 change all the decison size as minQueueLength, in order to keep the matrix has the same length
//            System.out.println("workCenter.getQueue().size(): " + workCenter.getQueue().size());

            if (workCenter.getQueue().size() == minQueueLength) {
                int index = (int)Math.floor(process.getOperationOption().getJob().getId()/jobsPerCase);
                if(index < 25){
                    situations.get(index).add(sequencingDecisionSituation.clone());
                }
            }

            OperationOption dispatchedOp =
                    simulation.getSequencingRule().priorOperation(sequencingDecisionSituation);

            workCenter.removeFromQueue(dispatchedOp);

            //must wait for machine to be ready
            double processStartTime = Math.max(workCenter.getReadyTime(), time);

            Process nextP = new Process(workCenter, process.getMachineId(),
                    dispatchedOp, processStartTime);
            simulation.addEvent(new ProcessStartEvent(nextP));
        }

        OperationOption nextOp = process.getOperationOption().getNext(simulation.getSystemState(),
                simulation.getRoutingRule());

        if (nextOp == null) {
            if(simulation instanceof HeterogeneousSimulation) {
                int workCenterID = process.getOperationOption().getWorkCenter().getId();
                double downloadTime = ((HeterogeneousSimulation) simulation).getWorkCenterExitTranserTime(workCenterID);
                process.getOperationOption().getOperation().setDownloadTimeToSelectedWorkCenter(downloadTime);
                LastOperationDownloadProcess downloadProcess = new LastOperationDownloadProcess(process.getWorkCenter(), process.getOperationOption(), time);
                simulation.addEvent(new LastOperationDownloadStartEvent(downloadProcess));
            }
            else{
                Job job = process.getOperationOption().getJob();
                job.setCompletionTime(process.getFinishTime());
                simulation.completeJob(job);
            }
        }
        else {
            //todo: check if need add a "if current workCenter == selected workCenter, if so just use OperationVisitEvent"
            //modified by mengxu 2021.08.31 for heterogeneous-------
            if(simulation instanceof HeterogeneousSimulation){
                nextOp.getOperation().setSelectedWorkCenter(nextOp.getWorkCenter());
                int fromWorkCenterID = process.getOperationOption().getWorkCenter().getId();
                int toWorkCenterID = nextOp.getWorkCenter().getId();
                double transferTime = ((HeterogeneousSimulation) simulation).getWorkCenterTranserTime(fromWorkCenterID, toWorkCenterID);
                nextOp.getOperation().setTransferTimeToSelectedWorkCenter(transferTime);
                //------------------------------------------------------
                TransportationProcess transportationProcess = new TransportationProcess(process.getOperationOption().getWorkCenter(), nextOp.getWorkCenter(), nextOp ,time);
                simulation.addEvent(new TransportationStartEvent(transportationProcess));
            }
            else{
                simulation.addEvent(new OperationVisitEvent(time, nextOp));
            }
            //original
//            simulation.addEvent(new OperationVisitEvent(time, nextOp));
        }
    }

    @Override
    public void addRoutingDecisionSituationAllCases(Simulation simulation,
                                                    List<List<RoutingDecisionSituation>> situations,
                                                    int minQueueLength,
                                                    int jobsPerCase) {
        WorkCenter workCenter = process.getWorkCenter();
        process.getOperationOption().getJob().addProcessFinishEvent(this);

        if (!workCenter.getQueue().isEmpty()) {
            SequencingDecisionSituation sequencingDecisionSituation =
                    new SequencingDecisionSituation(workCenter.getQueue(), workCenter,
                            simulation.getSystemState());

            OperationOption dispatchedOp =
                    simulation.getSequencingRule().priorOperation(sequencingDecisionSituation);

            workCenter.removeFromQueue(dispatchedOp);

            //must wait for machine to be ready
            double processStartTime = Math.max(workCenter.getReadyTime(), time);

            Process nextP = new Process(workCenter, process.getMachineId(),
                    dispatchedOp, processStartTime);
            simulation.addEvent(new ProcessStartEvent(nextP));
        }

        //fzhang 2019.9.25 change all the decison size as minQueueLength, in order to keep the matrix has the same length
        if (process.getOperationOption().getOperation().getNext() != null) {
            if (process.getOperationOption().getOperation().getNext().getOperationOptions().size()
                    == minQueueLength) {
                Operation o = process.getOperationOption().getOperation();
                if (o.getOperationOptions().size() == minQueueLength) {
                    int index = (int)Math.floor(o.getJob().getId()/jobsPerCase);
                    if(index < 25){
                        RoutingDecisionSituation r = o.getNext().routingDecisionSituation(simulation.getSystemState());
                        situations.get(index).add(r.clone());
                    }
                }
            }
        }

        OperationOption nextOp = process.getOperationOption().getNext(simulation.getSystemState(),
                simulation.getRoutingRule());

        if (nextOp == null) {
            if(simulation instanceof HeterogeneousSimulation) {
                int workCenterID = process.getOperationOption().getWorkCenter().getId();
                double downloadTime = ((HeterogeneousSimulation) simulation).getWorkCenterExitTranserTime(workCenterID);
                process.getOperationOption().getOperation().setDownloadTimeToSelectedWorkCenter(downloadTime);
                LastOperationDownloadProcess downloadProcess = new LastOperationDownloadProcess(process.getWorkCenter(), process.getOperationOption(), time);
                simulation.addEvent(new LastOperationDownloadStartEvent(downloadProcess));
            }
            else{
                Job job = process.getOperationOption().getJob();
                job.setCompletionTime(process.getFinishTime());
                simulation.completeJob(job);
            }
        }
        else {
            //todo: check if need add a "if current workCenter == selected workCenter, if so just use OperationVisitEvent"
            //modified by mengxu 2021.08.31 for heterogeneous-------
            if(simulation instanceof HeterogeneousSimulation){
                nextOp.getOperation().setSelectedWorkCenter(nextOp.getWorkCenter());
                int fromWorkCenterID = process.getOperationOption().getWorkCenter().getId();
                int toWorkCenterID = nextOp.getWorkCenter().getId();
                double transferTime = ((HeterogeneousSimulation) simulation).getWorkCenterTranserTime(fromWorkCenterID, toWorkCenterID);
                nextOp.getOperation().setTransferTimeToSelectedWorkCenter(transferTime);
                //------------------------------------------------------
                TransportationProcess transportationProcess = new TransportationProcess(process.getOperationOption().getWorkCenter(), nextOp.getWorkCenter(), nextOp ,time);
                simulation.addEvent(new TransportationStartEvent(transportationProcess));
            }
            else{
                simulation.addEvent(new OperationVisitEvent(time, nextOp));
            }
            //original
//            simulation.addEvent(new OperationVisitEvent(time, nextOp));
        }
    }


    @Override
    public String toString() {
        return String.format("%.1f: job %d op %d finished on work center %d.\n",
                time,
                process.getOperationOption().getJob().getId(),
                process.getOperationOption().getOperation().getId(),
                process.getWorkCenter().getId());
    }

    @Override
    public int compareTo(AbstractEvent other) {
        if (time < other.time)
            return -1;

        if (time > other.time)
            return 1;

        if (other instanceof ProcessFinishEvent) {
            ProcessFinishEvent otherPFE = (ProcessFinishEvent)other;

            if (process.getWorkCenter().getId() < otherPFE.process.getWorkCenter().getId())
                return -1;

            if (process.getWorkCenter().getId() > otherPFE.process.getWorkCenter().getId())
                return 1;
        }

        if (other instanceof JobArrivalEvent)
            return 1;

        if (other instanceof OperationVisitEvent)
            return 1;

        if(other instanceof FirstOperationUploadStartEvent) {
            return 1;
        }

        if(other instanceof FirstOperationUploadFinishEvent) {
            return 1;
        }

        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProcessFinishEvent that = (ProcessFinishEvent) o;

        return process != null ? process.equals(that.process) : that.process == null;
    }

    @Override
    public int hashCode() {
        return process != null ? process.hashCode() : 0;
    }


    public Process getProcess() {
        return process;
    }
}
