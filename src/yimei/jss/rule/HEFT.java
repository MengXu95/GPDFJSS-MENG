package yimei.jss.rule;


import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.simulation.state.SystemState;

public class HEFT extends AbstractRule {

    public HEFT(RuleType type) {
        name = "\"HEFT\"";
        this.type = type;
    }

    @Override
    public double priority(OperationOption taskOption, WorkCenter server, SystemState systemState) {
        if(this.type == RuleType.SEQUENCING){
            return -taskOption.getOperation().getUpwardRank();
        }
        else if(this.type == RuleType.ROUTING){
            return taskOption.getEarliestExecutionFinishTime();
        }
        else{
            System.out.println("Error! HEFT");
            return -1;
        }
    }
}