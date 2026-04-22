package yimei.jss.simulation.event;

import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.*;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.Simulation;

import java.util.List;

/**
 * Created by yimei on 22/09/16.
 */
public class JobArrivalEvent extends AbstractEvent {

    private Job job;

    public JobArrivalEvent(double time, Job job) {
        super(time);
        this.job = job;
    }

    public JobArrivalEvent(Job job) {
        this(job.getArrivalTime(), job);
    }

    @Override
    public void trigger(Simulation simulation) {
        //Job has just arrived, get first operation out
        Operation operation = job.getOperation(0);

        //yimei 2019.7.30 set the readytime of each operation to current time. It will be used to calculate operation waiting time in the queue of operation
        //before ther is a bug that all the readytime of operation is 0 and never update---the OWT = system.clocktime
        for (OperationOption op : operation.getOperationOptions())
            op.setReadyTime(job.getReleaseTime());
        //todo: need to check if need modify setReadyTime when consider transfer time.

        //get options of operation, and SystemState
        RoutingDecisionSituation decisionSituation = new RoutingDecisionSituation(
                operation.getOperationOptions(), simulation.getSystemState());

        //for normalising the terminals
        if(simulation.state instanceof GPRuleEvolutionStatePSL){
            if(((GPRuleEvolutionStatePSL) simulation.state).terminalNormalisation==0){
                ((GPRuleEvolutionStatePSL) simulation.state).updateMaxTerminals(decisionSituation);
            }
        }

//        //test
//        System.out.print("RoutingDecisionSituation: ");
//        for(OperationOption op:decisionSituation.getQueue()){
//            System.out.print(op.getOptionId() + ", ");
//        }
//        System.out.println();



        //System.out.println("===================routing=============");
        //use routing rule to decide which option we will use !!!!!
        //original
        OperationOption operationOption =
                simulation.getRoutingRule().nextOperationOption(decisionSituation);
        //operationOption.setReadyTime(job.getReleaseTime());  //yimei 2019.7.30 move it to above   before routing, the ready time should be set to clocktime


        //modified by mengxu 2021.07.09
        if (simulation.routingDecisions.size()<20) {
            simulation.routingDecisions.add(operationOption.getJob().getId()+operationOption.getOperation().getId()+operationOption.getOptionId());
        }
//        if (operation.getOperationOptions().size() == 7 && simulation.getThroughput() > simulation.getWarmupJobs() && simulation.AllDecisions.size()<40) {
//            simulation.AllDecisions.add(operationOption.getJob().getId()+operationOption.getOperation().getId()+operationOption.getOptionId());
//        }
//        if (operation.getOperationOptions().size() == 7 && simulation.AllDecisions.size()<40) {
//            simulation.AllDecisions.add(operationOption.getJob().getId()+operationOption.getOperation().getId()+operationOption.getOptionId());
//        }
        if (simulation.AllDecisions.size()<simulation.decisionSize) {
            simulation.AllDecisions.add(operationOption.getJob().getId()+operationOption.getOperation().getId()+operationOption.getOptionId());
        }
//        if (simulation.AllDecisions.size()<simulation.decisionSize) {
//            simulation.AllDecisions.add(operationOption.hashCode());
//        }

        //modified by mengxu 2021.05.08 for ensemble method
//        if(simulation.getEnsembleRule() != null){
//            operationOption = simulation.getEnsembleRule().nextOperationOptionVoting(decisionSituation);
//        }


        //modified by mengxu 2021.08.31 for transfer time
        if(simulation instanceof HeterogeneousSimulation){
            int selectedWorkCenterID = operationOption.getWorkCenter().getId();
            double uploadTime = ((HeterogeneousSimulation)simulation).getEntryWorkCenterTranserTime(selectedWorkCenterID);
            //modified by mengxu 2021.08.31 for heterogeneous-------
            operationOption.getOperation().setSelectedWorkCenter(operationOption.getWorkCenter());
            operationOption.getOperation().setUploadTimeToSelectedWorkCenter(uploadTime);
            //------------------------------------------------------
            FirstOperationUploadProcess process = new FirstOperationUploadProcess(operationOption.getWorkCenter(),operationOption,job.getReleaseTime());
            simulation.addEvent(new FirstOperationUploadStartEvent(process));
        }
        else{
            simulation.addEvent(new OperationVisitEvent(job.getReleaseTime(), operationOption));
        }

        //original
//        simulation.addEvent(new OperationVisitEvent(job.getReleaseTime(), operationOption));
        simulation.generateJob();
    }

    @Override
    public void trigger(Simulation simulation, double[] ensembleContribution) {
        //Job has just arrived, get first operation out
        Operation operation = job.getOperation(0);

        //yimei 2019.7.30 set the readytime of each operation to current time. It will be used to calculate operation waiting time in the queue of operation
        //before ther is a bug that all the readytime of operation is 0 and never update---the OWT = system.clocktime
        for (OperationOption op : operation.getOperationOptions())
            op.setReadyTime(job.getReleaseTime());
        //todo: need to check if need modify setReadyTime when consider transfer time.

        //get options of operation, and SystemState
        RoutingDecisionSituation decisionSituation = new RoutingDecisionSituation(
                operation.getOperationOptions(), simulation.getSystemState());

        //add by mengxu 2024.4.18
        if(simulation.state instanceof GPRuleEvolutionStatePSL){
            int useTerminalNormalisation = ((GPRuleEvolutionStatePSL)simulation.state).terminalNormalisation;
            if(useTerminalNormalisation==0){
                ((GPRuleEvolutionStatePSL)simulation.state).updateMaxTerminals(decisionSituation);
            }
        }


        //modified by mengxu 2023.02.24
        OperationOption operationOption = null;

        if(simulation.warmupSame){
            if (simulation.getNumJobsArrived() >= simulation.getWarmupJobs()) {
                if(simulation.useVotingEnsemble){
                    simulation.totalDecisionNumber++;
                    operationOption =
                            simulation.getEnsembleRule().nextOperationOptionVoting(decisionSituation, ensembleContribution);
                }
                else if(simulation.useMeanRankEnsemble){
                    simulation.totalDecisionNumber++;
                    operationOption =
                            simulation.getEnsembleRule().nextOperationOptionMeanRank(decisionSituation, ensembleContribution);
                }
                else if(simulation.useLinearEnsemble){
                    simulation.totalDecisionNumber++;
                    operationOption =
                            simulation.getEnsembleRule().nextOperationOptionLinear(decisionSituation, ensembleContribution);
                }
                else{
                    System.out.println("Error here!");
                }
            } else {
                operationOption = simulation.getRoutingRule().nextOperationOption(decisionSituation);
            }
        }
        else{
            if(simulation.useVotingEnsemble){
                simulation.totalDecisionNumber++;
                operationOption =
                        simulation.getEnsembleRule().nextOperationOptionVoting(decisionSituation, ensembleContribution);
            }
            else if(simulation.useMeanRankEnsemble){
                simulation.totalDecisionNumber++;
                operationOption =
                        simulation.getEnsembleRule().nextOperationOptionMeanRank(decisionSituation, ensembleContribution);
            }
            else if(simulation.useLinearEnsemble){
                simulation.totalDecisionNumber++;
                operationOption =
                        simulation.getEnsembleRule().nextOperationOptionLinear(decisionSituation, ensembleContribution);
            }
            else{
                System.out.println("Error here!");
            }
        }

//        if(simulation.useVotingEnsemble){
//            simulation.totalDecisionNumber++;
//            operationOption =
//                    simulation.getEnsembleRule().nextOperationOptionVoting(decisionSituation, ensembleContribution);
//        }
//        else if(simulation.useLinearEnsemble){
//            simulation.totalDecisionNumber++;
//            operationOption =
//                    simulation.getEnsembleRule().nextOperationOptionLinear(decisionSituation, ensembleContribution);
//        }
//        else{
//            System.out.println("Error here!");
//        }

        //modified by mengxu 2021.07.09
        if (simulation.routingDecisions.size()<20) {
            simulation.routingDecisions.add(operationOption.getJob().getId()+operationOption.getOperation().getId()+operationOption.getOptionId());
        }

        if (simulation.AllDecisions.size()<simulation.decisionSize) {
            simulation.AllDecisions.add(operationOption.getJob().getId()+operationOption.getOperation().getId()+operationOption.getOptionId());
        }

        //modified by mengxu 2021.08.31 for transfer time
        if(simulation instanceof HeterogeneousSimulation){
            int selectedWorkCenterID = operationOption.getWorkCenter().getId();
            double uploadTime = ((HeterogeneousSimulation)simulation).getEntryWorkCenterTranserTime(selectedWorkCenterID);
            //modified by mengxu 2021.08.31 for heterogeneous-------
            operationOption.getOperation().setSelectedWorkCenter(operationOption.getWorkCenter());
            operationOption.getOperation().setUploadTimeToSelectedWorkCenter(uploadTime);
            //------------------------------------------------------
            FirstOperationUploadProcess process = new FirstOperationUploadProcess(operationOption.getWorkCenter(),operationOption,job.getReleaseTime());
            simulation.addEvent(new FirstOperationUploadStartEvent(process));
        }
        else{
            simulation.addEvent(new OperationVisitEvent(job.getReleaseTime(), operationOption));
        }

        simulation.generateJob();
    }

    @Override
    public void trigger(Simulation simulation, double[] ensembleContribution, double[] ensembleSpread) {
        //Job has just arrived, get first operation out
        Operation operation = job.getOperation(0);

        //yimei 2019.7.30 set the readytime of each operation to current time. It will be used to calculate operation waiting time in the queue of operation
        //before ther is a bug that all the readytime of operation is 0 and never update---the OWT = system.clocktime
        for (OperationOption op : operation.getOperationOptions())
            op.setReadyTime(job.getReleaseTime());
        //todo: need to check if need modify setReadyTime when consider transfer time.

        //get options of operation, and SystemState
        RoutingDecisionSituation decisionSituation = new RoutingDecisionSituation(
                operation.getOperationOptions(), simulation.getSystemState());

        //for normalising the terminals
        if(simulation.state instanceof GPRuleEvolutionStatePSL){
            if(((GPRuleEvolutionStatePSL) simulation.state).terminalNormalisation==0){
                ((GPRuleEvolutionStatePSL) simulation.state).updateMaxTerminals(decisionSituation);
            }
        }


        //modified by mengxu 2023.02.24
        OperationOption operationOption = null;

        if(simulation.warmupSame){
            if (simulation.getNumJobsArrived() >= simulation.getWarmupJobs()) {
                if(simulation.useVotingEnsemble){
                    simulation.totalDecisionNumber++;
                    operationOption =
                            simulation.getEnsembleRule().nextOperationOptionVotingRecordSpread(decisionSituation, ensembleContribution, ensembleSpread);
                }
                else if(simulation.useMeanRankEnsemble){
                    simulation.totalDecisionNumber++;
                    operationOption =
                            simulation.getEnsembleRule().nextOperationOptionMeanRank(decisionSituation, ensembleContribution);
                }
                else if(simulation.useLinearEnsemble){
                    simulation.totalDecisionNumber++;
                    operationOption =
                            simulation.getEnsembleRule().nextOperationOptionLinear(decisionSituation, ensembleContribution);
                }
                else{
                    System.out.println("Error here!");
                }
            } else {
                operationOption = simulation.getRoutingRule().nextOperationOption(decisionSituation);
            }
        }
        else{
            if(simulation.useVotingEnsemble){
                simulation.totalDecisionNumber++;
                operationOption =
                        simulation.getEnsembleRule().nextOperationOptionVotingRecordSpread(decisionSituation, ensembleContribution, ensembleSpread);
            }
            else if(simulation.useMeanRankEnsemble){
                simulation.totalDecisionNumber++;
                operationOption =
                        simulation.getEnsembleRule().nextOperationOptionMeanRank(decisionSituation, ensembleContribution);
            }
            else if(simulation.useLinearEnsemble){
                simulation.totalDecisionNumber++;
                operationOption =
                        simulation.getEnsembleRule().nextOperationOptionLinear(decisionSituation, ensembleContribution);
            }
            else{
                System.out.println("Error here!");
            }
        }

        //modified by mengxu 2021.07.09
        if (simulation.routingDecisions.size()<20) {
            simulation.routingDecisions.add(operationOption.getJob().getId()+operationOption.getOperation().getId()+operationOption.getOptionId());
        }

        if (simulation.AllDecisions.size()<simulation.decisionSize) {
            simulation.AllDecisions.add(operationOption.getJob().getId()+operationOption.getOperation().getId()+operationOption.getOptionId());
        }

        //modified by mengxu 2021.08.31 for transfer time
        if(simulation instanceof HeterogeneousSimulation){
            int selectedWorkCenterID = operationOption.getWorkCenter().getId();
            double uploadTime = ((HeterogeneousSimulation)simulation).getEntryWorkCenterTranserTime(selectedWorkCenterID);
            //modified by mengxu 2021.08.31 for heterogeneous-------
            operationOption.getOperation().setSelectedWorkCenter(operationOption.getWorkCenter());
            operationOption.getOperation().setUploadTimeToSelectedWorkCenter(uploadTime);
            //------------------------------------------------------
            FirstOperationUploadProcess process = new FirstOperationUploadProcess(operationOption.getWorkCenter(),operationOption,job.getReleaseTime());
            simulation.addEvent(new FirstOperationUploadStartEvent(process));
        }
        else{
            simulation.addEvent(new OperationVisitEvent(job.getReleaseTime(), operationOption));
        }

        simulation.generateJob();
    }

    @Override
    public void addSequencingDecisionSituation(Simulation simulation,
                                     List<SequencingDecisionSituation> situations,
                                     int minQueueLength) {
        trigger(simulation);
    }

    @Override
    public void addRoutingDecisionSituation(Simulation simulation,
                                               List<RoutingDecisionSituation> situations,
                                               int minQueueLength) {
        //System.out.println("============================here========================");

        //Job has just arrived, get first operation out
        Operation operation = job.getOperation(0);

        //yimei 2019.7.30 set the readytime of each operation to current time. It will be used to calculate operation waiting time in the queue of operation
        //before ther is a bug that all the readytime of operation is 0 and never update---the OWT = system.clocktime
        for (OperationOption op : operation.getOperationOptions())
            op.setReadyTime(job.getReleaseTime());

        RoutingDecisionSituation decisionSituation = new RoutingDecisionSituation(
                operation.getOperationOptions(), simulation.getSystemState());

        //for normalising the terminals
        if(simulation.state instanceof GPRuleEvolutionStatePSL){
            if(((GPRuleEvolutionStatePSL) simulation.state).terminalNormalisation==0){
                ((GPRuleEvolutionStatePSL) simulation.state).updateMaxTerminals(decisionSituation);
            }
        }

//        if (operation.getOperationOptions().size() >= minQueueLength) {
//            situations.add(decisionSituation.clone());
//        }

        //fzhang 2019.9.4 in order to get same length matrix
//        System.out.println("operation.getOperationOptions().size(): " + operation.getOperationOptions().size());
        if (operation.getOperationOptions().size() == minQueueLength) {
            situations.add(decisionSituation.clone());
        }

        OperationOption operationOption =
                simulation.getRoutingRule().nextOperationOption(decisionSituation);
        //operationOption.setReadyTime(job.getReleaseTime());  //yimei 2019.7.30 move it to above

        //modified by mengxu 2021.09.01 for transfer time
        if(simulation instanceof HeterogeneousSimulation){
            int selectedWorkCenterID = operationOption.getWorkCenter().getId();
            double uploadTime = ((HeterogeneousSimulation)simulation).getEntryWorkCenterTranserTime(selectedWorkCenterID);
            //modified by mengxu 2021.08.31 for heterogeneous-------
            operationOption.getOperation().setSelectedWorkCenter(operationOption.getWorkCenter());
            operationOption.getOperation().setUploadTimeToSelectedWorkCenter(uploadTime);
            //------------------------------------------------------
            FirstOperationUploadProcess process = new FirstOperationUploadProcess(operationOption.getWorkCenter(),operationOption,job.getReleaseTime());
            simulation.addEvent(new FirstOperationUploadStartEvent(process));
        }
        else{
            simulation.addEvent(new OperationVisitEvent(job.getReleaseTime(), operationOption));
        }

//
//        //original
//        simulation.addEvent(new OperationVisitEvent(job.getReleaseTime(), operationOption));
        simulation.generateJob();
    }

    @Override
    public void addSequencingDecisionSituationAllCases(Simulation simulation,
                                                       List<List<SequencingDecisionSituation>> situations,
                                                       int minQueueLength,
                                                       int jobsPerCase) {
        trigger(simulation);
    }

    @Override
    public void addRoutingDecisionSituationAllCases(Simulation simulation,
                                                    List<List<RoutingDecisionSituation>> situations,
                                                    int minQueueLength,
                                                    int jobsPerCase) {
        //System.out.println("============================here========================");

        //Job has just arrived, get first operation out
        Operation operation = job.getOperation(0);

        //yimei 2019.7.30 set the readytime of each operation to current time. It will be used to calculate operation waiting time in the queue of operation
        //before ther is a bug that all the readytime of operation is 0 and never update---the OWT = system.clocktime
        for (OperationOption op : operation.getOperationOptions())
            op.setReadyTime(job.getReleaseTime());

        RoutingDecisionSituation decisionSituation = new RoutingDecisionSituation(
                operation.getOperationOptions(), simulation.getSystemState());

        //for normalising the terminals
        if(simulation.state instanceof GPRuleEvolutionStatePSL){
            if(((GPRuleEvolutionStatePSL) simulation.state).terminalNormalisation==0){
                ((GPRuleEvolutionStatePSL) simulation.state).updateMaxTerminals(decisionSituation);
            }
        }

        //fzhang 2019.9.4 in order to get same length matrix
//        System.out.println("operation.getOperationOptions().size(): " + operation.getOperationOptions().size());
        if (operation.getOperationOptions().size() == minQueueLength) {
            int index = (int)Math.floor(operation.getJob().getId()/jobsPerCase);
            if(index < 25){
                situations.get(index).add(decisionSituation.clone());
            }
        }

        OperationOption operationOption =
                simulation.getRoutingRule().nextOperationOption(decisionSituation);
        //operationOption.setReadyTime(job.getReleaseTime());  //yimei 2019.7.30 move it to above

        //modified by mengxu 2021.09.01 for transfer time
        if(simulation instanceof HeterogeneousSimulation){
            int selectedWorkCenterID = operationOption.getWorkCenter().getId();
            double uploadTime = ((HeterogeneousSimulation)simulation).getEntryWorkCenterTranserTime(selectedWorkCenterID);
            //modified by mengxu 2021.08.31 for heterogeneous-------
            operationOption.getOperation().setSelectedWorkCenter(operationOption.getWorkCenter());
            operationOption.getOperation().setUploadTimeToSelectedWorkCenter(uploadTime);
            //------------------------------------------------------
            FirstOperationUploadProcess process = new FirstOperationUploadProcess(operationOption.getWorkCenter(),operationOption,job.getReleaseTime());
            simulation.addEvent(new FirstOperationUploadStartEvent(process));
        }
        else{
            simulation.addEvent(new OperationVisitEvent(job.getReleaseTime(), operationOption));
        }

        simulation.generateJob();
    }

    @Override
    public String toString() {
        return String.format("%.1f: job %d arrives.\n", time, job.getId());
    }

    @Override
    public int compareTo(AbstractEvent other) {
        if (time < other.time)
            return -1;

        if (time > other.time)
            return 1;

        if (other instanceof JobArrivalEvent) {
            JobArrivalEvent otherJAE = (JobArrivalEvent)other;

            if (job.getId() < otherJAE.job.getId())
                return -1;

            if (job.getId() > otherJAE.job.getId())
                return 1;
        }

        return -1;
    }
}
