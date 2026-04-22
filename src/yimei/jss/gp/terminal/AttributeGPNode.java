package yimei.jss.gp.terminal;

import ec.EvolutionState;
import ec.Problem;
import ec.gp.ADFStack;
import ec.gp.GPData;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.algorithm.multitaskHeuristicLearning.GPRuleEvolutionStateMultiTaskLearningSurrogate;
import mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodel.GPRuleEvolutionStateMultiTaskLearningSurrogateSVR;
import mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodelOneSubpop.GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop;
import mengxu.algorithm.multitaskHeuristicLearning.onlysurrogate.GPRuleEvolutionStateMultiTaskLearningOnlySurrogate;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.gp.CalcPriorityProblem;
import yimei.jss.gp.data.DoubleData;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

/**
 * The job shop attribute as terminal.
 *
 * @author yimei
 */

public class AttributeGPNode extends GPNode {

    private final JobShopAttribute attribute;

    public AttributeGPNode(JobShopAttribute attribute) {
        super();
        children = new GPNode[0];
        this.attribute = attribute;
    }

    public JobShopAttribute getJobShopAttribute() {
        return attribute;
    }

    @Override
    public String toString() {
        return attribute.getName();
    }

    @Override
    public int expectedChildren() {
        return 0;
    }

    @Override
    public void eval(EvolutionState state, int thread, GPData input,
                     ADFStack stack, GPIndividual individual, Problem problem) {
        // The problem is essentially a priority calculation.
        CalcPriorityProblem calcPrioProb = ((CalcPriorityProblem)problem);

        DoubleData data = ((DoubleData)input);
        if(calcPrioProb.getOperation() == null || calcPrioProb.getWorkCenter()== null || calcPrioProb.getSystemState() == null) {
        	System.out.println("null");
        }

        if(attribute == null){
            System.out.println("Error");
        }

        //mengxu 2024.3.11 for Pareto set learning
        if(state instanceof GPRuleEvolutionStatePSL){
            if(((GPRuleEvolutionStatePSL) state).terminalNormalisation == 0){
                data.value = attribute.value_Pareto_set_learning_normalisation(
                        calcPrioProb.getOperation(),
                        calcPrioProb.getWorkCenter(),
                        calcPrioProb.getSystemState(),
                        state);
            }
            else if(((GPRuleEvolutionStatePSL) state).terminalNormalisation == 1){
                data.value = attribute.value_Pareto_set_learning_sigmoid_normalisation(
                        calcPrioProb.getOperation(),
                        calcPrioProb.getWorkCenter(),
                        calcPrioProb.getSystemState(),
                        state);
            }
            else{
                data.value = attribute.value_Pareto_set_learning(
                        calcPrioProb.getOperation(),
                        calcPrioProb.getWorkCenter(),
                        calcPrioProb.getSystemState(),
                        state);
            }
        }
        else if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogate){
            data.value = attribute.value_multi_task_learning(
                    calcPrioProb.getOperation(),
                    calcPrioProb.getWorkCenter(),
                    calcPrioProb.getSystemState(),
                    state);
        }
        else if(state instanceof GPRuleEvolutionStateMultiTaskLearningOnlySurrogate){
            data.value = attribute.value_multi_task_learning(
                    calcPrioProb.getOperation(),
                    calcPrioProb.getWorkCenter(),
                    calcPrioProb.getSystemState(),
                    state);
        }
        else if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogateSVR){
            data.value = attribute.value_multi_task_learning(
                    calcPrioProb.getOperation(),
                    calcPrioProb.getWorkCenter(),
                    calcPrioProb.getSystemState(),
                    state);
        }
        else if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop){
            data.value = attribute.value_multi_task_learning(
                    calcPrioProb.getOperation(),
                    calcPrioProb.getWorkCenter(),
                    calcPrioProb.getSystemState(),
                    state);
        }
        else{
//            System.out.println("error here here!");
            data.value = attribute.value(
                    calcPrioProb.getOperation(),
                    calcPrioProb.getWorkCenter(),
                    calcPrioProb.getSystemState());
        }


//        //mengxu 2024.3.11 for Pareto set learning
//        if(this.Using_PSL || state instanceof GPRuleEvolutionStatePSL){
//            data.value = attribute.value_Pareto_set_learning(
//                    calcPrioProb.getOperation(),
//                    calcPrioProb.getWorkCenter(),
//                    calcPrioProb.getSystemState(),
//                    state);
//        }
//        else{
//            data.value = attribute.value(
//                    calcPrioProb.getOperation(),
//                    calcPrioProb.getWorkCenter(),
//                    calcPrioProb.getSystemState());
//        }


        //following is the original
//        data.value = attribute.value(
//                calcPrioProb.getOperation(),
//                calcPrioProb.getWorkCenter(),
//                calcPrioProb.getSystemState());
    }

    @Override
    public int hashCode() {
        return attribute.getName().hashCode();
    }

    public boolean equals(Object other) {
        if (other instanceof AttributeGPNode) {
            AttributeGPNode o = (AttributeGPNode)other;
            return (attribute == o.attribute);
        }

        return false;
    }
}
