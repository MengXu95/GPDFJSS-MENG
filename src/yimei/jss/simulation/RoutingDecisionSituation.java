package yimei.jss.simulation;

import yimei.jss.jobshop.OperationOption;
import yimei.jss.simulation.state.SystemState;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by dyska on 7/09/17.
 */
public class RoutingDecisionSituation extends DecisionSituation {

    private List<OperationOption> queue;
    private SystemState systemState;

    public RoutingDecisionSituation(List<OperationOption> operationOptions, SystemState systemState) {
        this.queue = operationOptions;
        this.systemState = systemState;
    }

    public List<OperationOption> getQueue() {
        return queue;
    }

    public SystemState getSystemState() {
        return systemState;
    }

    public RoutingDecisionSituation clone() {

        //Yi//todo：2022.03.22
        List<OperationOption> clonedQ = new ArrayList<>();
        for (OperationOption op : queue) {
            clonedQ.add(op.clone());
        }
        SystemState clonedState = systemState.clone();

        //original
//        List<OperationOption> clonedQ = new ArrayList<>(queue);
//        SystemState clonedState = systemState.clone();

        return new RoutingDecisionSituation(clonedQ, clonedState);
    }
}
