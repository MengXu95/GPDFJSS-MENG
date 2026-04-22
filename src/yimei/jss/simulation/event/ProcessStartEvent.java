package yimei.jss.simulation.event;

import yimei.jss.jobshop.Process;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

import java.util.List;

/**
 * Created by YiMei on 25/09/16.
 */
public class ProcessStartEvent extends AbstractEvent {

    private Process process;

    public ProcessStartEvent(double time, Process process) {
        super(time);
        this.process = process;
    }

    public ProcessStartEvent(Process process) {
        this(process.getStartTime(), process);
    }

    public Process getProcess() {
        return process;
    }

    @Override
    public void trigger(Simulation simulation) {
        WorkCenter workCenter = process.getWorkCenter();
        workCenter.setMachineReadyTime(
                process.getMachineId(), process.getFinishTime());
        workCenter.incrementBusyTime(process.getDuration());

        simulation.addEvent(
                new ProcessFinishEvent(process.getFinishTime(), process));
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
        return String.format("%.1f: job %d op %d started on work center %d.\n",
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

        if (other instanceof ProcessStartEvent)
            return 0;

        if (other instanceof ProcessFinishEvent)
            return -1;

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
}
