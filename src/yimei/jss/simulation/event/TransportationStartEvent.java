package yimei.jss.simulation.event;

import yimei.jss.jobshop.FirstOperationUploadProcess;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.TransportationProcess;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.Simulation;

import java.util.List;

/**
 * Created by MengXu on 2021.09.02.
 */
public class TransportationStartEvent extends AbstractEvent{
    private TransportationProcess process;

    public TransportationStartEvent(double time, TransportationProcess process) {
        super(time);
        this.process = process;
    }

    public TransportationStartEvent(TransportationProcess process) {
        this(process.getStartTime(), process);
    }

    @Override
    public void trigger(Simulation simulation) {
        simulation.addEvent(new TransportationFinishEvent(process));
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
        return String.format("%.1f: job %d op %d transfer from workCenter %d to workCenter %d.\n",
                time, process.getOperationOption().getJob().getId(), process.getOperationOption().getOperation().getId(),
                process.getFromWorkCenter().getId(), process.getToWorkCenter().getId());
    }

    @Override
    public int compareTo(AbstractEvent other) {//todo: need to check 2021.09.02
        if (time < other.time)
            return -1;

        if (time > other.time)
            return 1;

//        if (other instanceof TransportationStartEvent) {
//            TransportationStartEvent otherJAE = (TransportationStartEvent)other;
//
//            if (process.getOperationOption().getJob().getId() < otherJAE.process.getOperationOption().getJob().getId())
//                return -1;
//
//            if (process.getOperationOption().getJob().getId() > otherJAE.process.getOperationOption().getJob().getId())
//                return 1;
//        }

        if(other instanceof TransportationStartEvent) {
            return 0;
        }

        if(other instanceof FirstOperationUploadStartEvent) {
            return 0;
        }

        if(other instanceof LastOperationDownloadStartEvent) {
            return 0;
        }

        if(other instanceof TransportationFinishEvent) {
            return -1;
        }

        if(other instanceof FirstOperationUploadFinishEvent) {
            return -1;
        }

        if(other instanceof LastOperationDownloadFinishEvent) {
            return -1;
        }

        if(other instanceof JobArrivalEvent) {
            return -1;
        }

        if(other instanceof OperationVisitEvent) {
            return -1;
        }

        if(other instanceof ProcessStartEvent) {
            return 1;
        }

        if(other instanceof ProcessFinishEvent) {
            return 1;
        }

        return -1;
    }

    public TransportationProcess getProcess() {
        return process;
    }
}
