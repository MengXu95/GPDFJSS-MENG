package yimei.jss.simulation.event;

import yimei.jss.jobshop.*;
import yimei.jss.jobshop.Process;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

import java.util.List;

/**
 * Created by YiMei on 25/09/16.
 */
public class OperationVisitEvent extends AbstractEvent {

    private OperationOption operationOption;

    public OperationVisitEvent(double time, OperationOption operationOption) {
        super(time);
        this.operationOption = operationOption;
    }

    public OperationVisitEvent(OperationOption operation) {
        this(operation.getReadyTime(), operation);
    }

    @Override
    public void trigger(Simulation simulation) {
        operationOption.setReadyTime(time);

        WorkCenter workCenter = operationOption.getWorkCenter();
        Machine earliestMachine = workCenter.earliestReadyMachine();
        Process p = new Process(workCenter, earliestMachine.getId(), operationOption, time);

//        workCenter.addToQueue(operationOption);

//        double currentTime = simulation.getClockTime();
//        double eventTime = 0;
//        while(currentTime > eventTime && !workCenter.allProcessStartEventQueue.isEmpty()){
//            AbstractEvent nextEvent = workCenter.allProcessStartEventQueue.poll(); // the head of this queue, or null if this queue is empty
//            if(nextEvent instanceof ProcessStartEvent){
//                eventTime = ((ProcessStartEvent) nextEvent).getProcess().getFinishTime();
//            }
//            if(eventTime > currentTime){
//                workCenter.allProcessStartEventQueue.add(nextEvent);
//                break;
//            }
//        }

        //modified by mengxu 2021.11.04
        if (earliestMachine.getReadyTime() > time || !simulation.canAddToQueue(p) || workCenter.getNumOpsInQueue() >= 1) {//todo:modifiedy 2022.03.22
//        if (earliestMachine.getReadyTime() > time || !simulation.canAddToQueue(p)) {//original
            workCenter.addToQueue(operationOption);
//            simulation.times_addToQueue++;
        }
        else {
            ProcessStartEvent processStartEvent = new ProcessStartEvent(p);
//            workCenter.allProcessStartEventQueue.add(processStartEvent);

            simulation.addEvent(processStartEvent);
//            simulation.times_OVE++;
        }

        //original
//        if (earliestMachine.getReadyTime() > time || !simulation.canAddToQueue(p)) {
//            workCenter.addToQueue(operationOption);
//        }
//        else {
//            simulation.addEvent(new ProcessStartEvent(p));
//        }
    }

    @Override
    public void trigger(Simulation simulation, double[] ensembleContribution) {
        trigger(simulation);
    }

    @Override
    public void trigger(Simulation simulation, double[] ensembleContribution, double[] ensembleSpread) {
        trigger(simulation);
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
        trigger(simulation);
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
        trigger(simulation);
    }

    @Override
    public String toString() {
        return String.format("%.1f: job %d op %d visits.\n",
                time, operationOption.getJob().getId(), operationOption.getOperation().getId());
    }

    @Override
    public int compareTo(AbstractEvent other) {
        if (time < other.time)
            return -1;

        if (time > other.time)
            return 1;

        if (other instanceof JobArrivalEvent)
            return 1;

        if (other instanceof OperationVisitEvent)
            return 0;

        if(other instanceof FirstOperationUploadStartEvent) {
            return 1;
        }

        if(other instanceof FirstOperationUploadFinishEvent) {
            return 1;
        }

        return -1;
    }

    public OperationOption getOperationOption() {return operationOption; }
}
