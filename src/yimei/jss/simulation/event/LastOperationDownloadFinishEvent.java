package yimei.jss.simulation.event;

import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.Job;
import yimei.jss.jobshop.LastOperationDownloadProcess;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.Simulation;

import java.util.List;

/**
 * Created by MengXu on 2021.09.02.
 */
public class LastOperationDownloadFinishEvent extends AbstractEvent{
    private LastOperationDownloadProcess process;

    public LastOperationDownloadFinishEvent(double time, LastOperationDownloadProcess process) {
        super(time);
        this.process = process;
    }

    public LastOperationDownloadFinishEvent(LastOperationDownloadProcess process) {
        this(process.getFinishTime(), process);
    }

    @Override
    public void trigger(Simulation simulation) {
        Job job = process.getOperationOption().getJob();
        job.setCompletionTime(process.getFinishTime());
        simulation.completeJob(job);
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
        return String.format("%.1f: job %d last op %d download from workCenter %d to Exit.\n",
                time, process.getOperationOption().getJob().getId(), process.getOperationOption().getOperation().getId(),
                process.getFromWorkCenter().getId());
    }

    @Override
    public int compareTo(AbstractEvent other) {//todo: need to check 2021.09.02
        if (time < other.time)
            return -1;

        if (time > other.time)
            return 1;

//        if (other instanceof LastOperationDownloadStartEvent)
//            return 1;
//
//        if (other instanceof LastOperationDownloadFinishEvent)
//            return 0;
//        return 1;

        //modified by mengxu
        if(other instanceof TransportationStartEvent) {
            return 1;
        }

        if(other instanceof FirstOperationUploadStartEvent) {
            return 1;
        }

        if(other instanceof LastOperationDownloadStartEvent) {
            return 1;
        }

        if(other instanceof TransportationFinishEvent) {
            return 0;
        }

        if(other instanceof FirstOperationUploadFinishEvent) {
            return 0;
        }

        if(other instanceof LastOperationDownloadFinishEvent) {
            return 0;
        }

        if(other instanceof JobArrivalEvent) {
            return 1;
        }

        if(other instanceof OperationVisitEvent) {
            return 1;
        }

        if(other instanceof ProcessStartEvent) {
            return 1;
        }

        if(other instanceof ProcessFinishEvent) {
            return 1;
        }

        return 1;
    }

    public LastOperationDownloadProcess getProcess() {
        return process;
    }
}
