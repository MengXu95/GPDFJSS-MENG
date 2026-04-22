package mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodel;

import ec.EvolutionState;
import ec.Individual;
import ec.simple.SimpleProblemForm;
import mengxu.algorithm.multitaskHeuristicLearning.onlysurrogate.MultiObjectiveFitnessMultiTaskLearningOnlySurrogate;
import yimei.jss.algorithm.fsmultipletreegp.SimpleStatisticsSaveRulesizeGen;

public class SimpleStatisticsSaveRulesizeGenMultiTaskLearningSVR extends SimpleStatisticsSaveRulesizeGen {

	boolean warned = false;
	public void postEvaluationStatistics(final EvolutionState state)
	{
		for(int x=0;x<children.length;x++)
			children[x].postEvaluationStatistics(state);

		// for now we just print the best fitness per subpopulation.
		Individual[] best_i = new Individual[state.population.subpops.length];  // quiets compiler complaints
		int[] best_i_index = new int[state.population.subpops.length];  // quiets compiler complaints
		for(int x=0;x<state.population.subpops.length;x++)
		{
			best_i[x] = state.population.subpops[x].individuals[0];
			for(int y=1;y<state.population.subpops[x].individuals.length;y++)
			{
				if (state.population.subpops[x].individuals[y] == null)
				{
					if (!warned)
					{
						state.output.warnOnce("Null individuals found in subpopulation");
						warned = true;  // we do this rather than relying on warnOnce because it is much faster in a tight loop
					}
				}
				else if (best_i[x] == null || state.population.subpops[x].individuals[y].fitness.betterThan(best_i[x].fitness)){
					best_i[x] = state.population.subpops[x].individuals[y];
					best_i_index[x] = y;
				}
				if (best_i[x] == null)
				{
					if (!warned)
					{
						state.output.warnOnce("Null individuals found in subpopulation");
						warned = true;  // we do this rather than relying on warnOnce because it is much faster in a tight loop
					}
				}
			}

			// now test to see if it's the new best_of_run
			if (best_of_run[x]==null || best_i[x].fitness.betterThan(best_of_run[x].fitness))
				best_of_run[x] = (Individual)(best_i[x].clone());
		}

		// print the best-of-generation individual
		if (doGeneration) state.output.println("\nGeneration: " + state.generation,statisticslog);
		if (doGeneration) state.output.println("Best Individual:",statisticslog);

//		//---to find and only print the best among all the subpops for multi-task learning by mengxu 2024.8.15
//		for(int x=0;x<state.population.subpops.length;x++)
//		{
//			if (doGeneration) state.output.println("Subpopulation " + x + ":",statisticslog);
//			if (doGeneration) best_i[x].printIndividualForHumans(state,statisticslog);
//			if (doMessage && !silentPrint) state.output.message("Subpop " + x + " best fitness of generation" +
//					(best_i[x].evaluated ? " " : " (evaluated flag not set): ") +
//					best_i[x].fitness.fitnessToStringForHumans());
//
//			// describe the winner if there is a description
//			if (doGeneration && doPerGenerationDescription)
//			{
//				if (state.evaluator.p_problem instanceof SimpleProblemForm)
//					((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_i[x], x, 0, statisticslog);
//			}
//		}

//		// Strategy 1: only from the best individuals at each subpop
//		Individual best_allSubpop = best_i[0];
//		int subpop_best = 0;
//		int subpopIndex_best = best_i_index[0];
//		for(int x=1;x<state.population.subpops.length;x++)
//		{
//			int subpop_j = x;
//			int subpopIndex_j = best_i_index[x];
//			Individual ind_j = best_i[x];
//			if(((MultiObjectiveFitnessMultiTaskLearningSVR)best_i[x].fitness).betterThanBasedOnRelationshipModel(subpopIndex_j, ind_j, subpopIndex_best, best_allSubpop, subpop_j, subpop_best, state, 0)){
//				best_allSubpop = (Individual)(best_i[x].clone());
//				subpop_best = x;
//				subpopIndex_best = best_i_index[x];
//			}
//		}
//		if (doGeneration) state.output.println("Subpopulation " + subpop_best + ":",statisticslog);
//		if (doGeneration) best_allSubpop.printIndividualForHumans(state,statisticslog);
//		if (doMessage && !silentPrint) state.output.message("Subpop " + subpop_best + " best fitness of generation" +
//				(best_allSubpop.evaluated ? " " : " (evaluated flag not set): ") +
//				best_allSubpop.fitness.fitnessToStringForHumans());
//
//		// describe the winner if there is a description
//		if (doGeneration && doPerGenerationDescription)
//		{
//			if (state.evaluator.p_problem instanceof SimpleProblemForm)
//				((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_allSubpop, 0, 0, statisticslog);
//		}

		// Strategy 2: from the whole population
		Individual best_allSubpop = state.population.subpops[0].individuals[0];
		int subpop_best = 0;
		int subpopIndex_best = 0;
		for(int x=0;x<state.population.subpops.length;x++)
		{//todo: x should start from 1 or 0??? 2024.10.29
			Individual[] inds = state.population.subpops[x].individuals;
			for(int i=0; i<inds.length; i++){
				int subpop_j = x;
				int subpopIndex_j = i;
				Individual ind_j = inds[i];
				if(((MultiObjectiveFitnessMultiTaskLearningSVR)inds[i].fitness).betterThanBasedOnRelationshipModel(subpopIndex_j, ind_j, subpopIndex_best, best_allSubpop, subpop_j, subpop_best, state, 0)){
					best_allSubpop = (Individual)(inds[i].clone());
					subpop_best = x;
					subpopIndex_best = i;
				}
			}
		}
		if (doGeneration) state.output.println("Subpopulation " + subpop_best + ":",statisticslog);
		if (doGeneration) best_allSubpop.printIndividualForHumans(state,statisticslog);
		if (doMessage && !silentPrint) state.output.message("Subpop " + subpop_best + " best fitness of generation" +
				(best_allSubpop.evaluated ? " " : " (evaluated flag not set): ") +
				best_allSubpop.fitness.fitnessToStringForHumans());

		// describe the winner if there is a description
		if (doGeneration && doPerGenerationDescription)
		{
			if (state.evaluator.p_problem instanceof SimpleProblemForm)
				((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_allSubpop, 0, 0, statisticslog);
		}


		//---the following is the original to print all the best for each subpop
//		for(int x=0;x<state.population.subpops.length;x++)
//		{
//			if (doGeneration) state.output.println("Subpopulation " + x + ":",statisticslog);
//			if (doGeneration) best_i[x].printIndividualForHumans(state,statisticslog);
//			if (doMessage && !silentPrint) state.output.message("Subpop " + x + " best fitness of generation" +
//					(best_i[x].evaluated ? " " : " (evaluated flag not set): ") +
//					best_i[x].fitness.fitnessToStringForHumans());
//
//			// describe the winner if there is a description
//			if (doGeneration && doPerGenerationDescription)
//			{
//				if (state.evaluator.p_problem instanceof SimpleProblemForm)
//					((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_i[x], x, 0, statisticslog);
//			}
//		}
	}
	
}
