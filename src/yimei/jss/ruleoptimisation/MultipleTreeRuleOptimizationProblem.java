package yimei.jss.ruleoptimisation;

import java.util.ArrayList;
import java.util.List;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.util.Parameter;
import mengxu.algorithm.diversepartnerselection.withBRandHalfShopSurrogate.HalfShopOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel;
import mengxu.algorithm.multiobjective.ParetoSetLearning.FixedStructureGPIndividual.GPIndividualPreferenceFixed;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import yimei.jss.jobshop.Objective;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleTreeMultipleRuleEvaluationModel;

public class MultipleTreeRuleOptimizationProblem extends RuleOptimizationProblem {

	   public static final String P_EVAL_MODEL = "eval-model";

	    private AbstractEvaluationModel evaluationModel;

	    public List<Objective> getObjectives() {
	        return evaluationModel.getObjectives();
	    }

	    public AbstractEvaluationModel getEvaluationModel() {
	        return evaluationModel;
	    }

	    public void rotateEvaluationModel() {
	        evaluationModel.rotate();
	    }
	 @Override
	    public void setup(final EvolutionState state, final Parameter base) {
	        super.setup(state, base);  //about ADFStack and ADFContext

	        Parameter p = base.push(P_EVAL_MODEL);  //yimei.jss.ruleevaluation.MultipleRuleEvaluationModel  here is different with before.
	        evaluationModel = (AbstractEvaluationModel)(
	                state.parameters.getInstanceForParameter(
	                        p, null, AbstractEvaluationModel.class));

	        evaluationModel.setup(state, p);
	    }

	 public void normObjective(EvolutionState state, Individual indi, int subpopulation, int threadnum) {

		 GPRule sequencingRule = new GPRule(RuleType.SEQUENCING, ((GPIndividual) indi).trees[0]);
		 GPRule routingRule = new GPRule(RuleType.ROUTING, ((GPIndividual) indi).trees[1]);

		 List rules = new ArrayList();
		 List fitnesses = new ArrayList();

		 rules.add(sequencingRule);
		 rules.add(routingRule);

		 fitnesses.add(indi.fitness);

		 evaluationModel.normObjective(fitnesses, rules, state);
	 }


	public void evaluate(EvolutionState state, Individual indi, int subpopulation, int threadnum) {
		//add by mengxu 2024.3.25
		if(indi instanceof GPIndividualPreferenceFixed){
			List rules = new ArrayList();
			List fitnesses = new ArrayList();

			for(int i=0; i<((GPIndividual) indi).trees.length; i++){
				if(i<((GPIndividual) indi).trees.length/2){
					GPRule sequencingRule = new GPRule(RuleType.SEQUENCING, ((GPIndividual) indi).trees[i]);
					rules.add(sequencingRule);
				}
				else{
					GPRule routingRule = new GPRule(RuleType.ROUTING, ((GPIndividual) indi).trees[i]);
					rules.add(routingRule);
				}
			}

			fitnesses.add(indi.fitness);

			//todo: need to check this when run experiments!
			//modified by mengxu 2021.09.01 to make this suitable for baseline too!
			if(evaluationModel instanceof HalfShopOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel){
				((HalfShopOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel) evaluationModel).useOriginal();
			}
			((MultipleTreeMultipleRuleEvaluationModel)evaluationModel).evaluateFixedPreference(fitnesses, rules, state, indi);
			indi.evaluated = true;
		}
		else{
			//modified by fzhang 23.5.2018  read two rules from one individual
			GPRule sequencingRule = new GPRule(RuleType.SEQUENCING, ((GPIndividual) indi).trees[0]);
			GPRule routingRule = new GPRule(RuleType.ROUTING, ((GPIndividual) indi).trees[1]);

			List rules = new ArrayList();
			List fitnesses = new ArrayList();

			//rules.add(rule);
			//modified by fzhang  to save two rules for evaluating from one individual
			rules.add(sequencingRule);
			rules.add(routingRule);

			fitnesses.add(indi.fitness);
			//todo: need to check this when run experiments!
			//modified by mengxu 2021.09.01 to make this suitable for baseline too!
			if(evaluationModel instanceof HalfShopOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel){
				((HalfShopOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel) evaluationModel).useOriginal();
			}

			if(state instanceof GPRuleEvolutionStatePSL){
				if(((GPRuleEvolutionStatePSL) state).evaluateOrder == 1){
					((MultipleTreeMultipleRuleEvaluationModel)evaluationModel).evaluatePreferenceOpposite(fitnesses, rules, state, indi);
				}
				else{
					evaluationModel.evaluate(fitnesses, rules, state, indi);
				}
			}
			else{
				evaluationModel.evaluate(fitnesses, rules, state, indi);
			}
//			evaluationModel.evaluate(fitnesses, rules, state, indi);
			indi.evaluated = true;
		}


		//the following is the original
		//modified by fzhang 23.5.2018  read two rules from one individual
//		GPRule sequencingRule = new GPRule(RuleType.SEQUENCING, ((GPIndividual) indi).trees[0]);
//		GPRule routingRule = new GPRule(RuleType.ROUTING, ((GPIndividual) indi).trees[1]);
//
//		List rules = new ArrayList();
//		List fitnesses = new ArrayList();
//
//		//rules.add(rule);
//		//modified by fzhang  to save two rules for evaluating from one individual
//		rules.add(sequencingRule);
//		rules.add(routingRule);
//
//		fitnesses.add(indi.fitness);
//
//		//todo: need to check this when run experiments!
//		//modified by mengxu 2021.09.01 to make this suitable for baseline too!
//		if(evaluationModel instanceof HalfShopOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel){
//			((HalfShopOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel) evaluationModel).useOriginal();
//		}
//		evaluationModel.evaluate(fitnesses, rules, state, indi);
//		indi.evaluated = true;
	}
}
