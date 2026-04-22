package yimei.jss.simulation.event;

import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

import java.util.List;

/**
 * Created by yimei on 22/09/16.
 */
public abstract class AbstractEvent implements Comparable<AbstractEvent> {

    protected double time;

    public AbstractEvent(double time) {
        this.time = time;
    }

    public double getTime() {
        return time;
    }

    public abstract void trigger(Simulation simulation);

    public abstract void trigger(Simulation simulation, double[] ensembleContribution); //add by mengxu 2023.02.24

    public abstract void trigger(Simulation simulation, double[] ensembleContribution, double[] ensembleSpread); //add by mengxu 2023.05.11

    public abstract void addSequencingDecisionSituation(Simulation simulation,
                                              List<SequencingDecisionSituation> situations,
                                              int minQueueLength);

    public abstract void addRoutingDecisionSituation(Simulation simulation,
                                              List<RoutingDecisionSituation> situations,
                                              int minOptions);

    public abstract void addSequencingDecisionSituationAllCases(Simulation simulation,
                                                                List<List<SequencingDecisionSituation>> situations,
                                                                int minQueueLength,
                                                                int jobsPerCase);

    public abstract void addRoutingDecisionSituationAllCases(Simulation simulation,
                                                             List<List<RoutingDecisionSituation>> situations,
                                                             int minOptions,
                                                             int jobsPerCase);

    @Override
    public int compareTo(AbstractEvent other) {
        if (time < other.time)
            return -1;

        if (time > other.time)
            return 1;

        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractEvent that = (AbstractEvent) o;

        return Double.compare(that.time, time) == 0;
    }

    @Override
    public int hashCode() {
        long temp = Double.doubleToLongBits(time);
        return (int) (temp ^ (temp >>> 32));
    }
}
