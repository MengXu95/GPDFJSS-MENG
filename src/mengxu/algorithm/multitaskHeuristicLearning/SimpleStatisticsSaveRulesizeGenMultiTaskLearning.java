package mengxu.algorithm.multitaskHeuristicLearning;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.simple.SimpleProblemForm;
import ec.simple.SimpleStatistics;
import ec.util.Parameter;
import yimei.jss.algorithm.fsmultipletreegp.SimpleStatisticsSaveRulesizeGen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleStatisticsSaveRulesizeGenMultiTaskLearning extends SimpleStatisticsSaveRulesizeGen {

	boolean warned = false;
	public void postEvaluationStatistics(final EvolutionState state)
	{
		for(int x=0;x<children.length;x++)
			children[x].postEvaluationStatistics(state);

		// for now we just print the best fitness per subpopulation.
		Individual[] best_i = new Individual[state.population.subpops.length];  // quiets compiler complaints
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
				else if (best_i[x] == null || state.population.subpops[x].individuals[y].fitness.betterThan(best_i[x].fitness))
					best_i[x] = state.population.subpops[x].individuals[y];
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

		//---to find and only print the best among all the subpops for multi-task learning by mengxu 2024.8.15
		for(int x=0;x<state.population.subpops.length;x++)
		{
			if (doGeneration) state.output.println("Subpopulation " + x + ":",statisticslog);
			if (doGeneration) best_i[x].printIndividualForHumans(state,statisticslog);
			if (doMessage && !silentPrint) state.output.message("Subpop " + x + " best fitness of generation" +
					(best_i[x].evaluated ? " " : " (evaluated flag not set): ") +
					best_i[x].fitness.fitnessToStringForHumans());

			// describe the winner if there is a description
			if (doGeneration && doPerGenerationDescription)
			{
				if (state.evaluator.p_problem instanceof SimpleProblemForm)
					((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_i[x], x, 0, statisticslog);
			}
		}


//		Individual best_allSubpop = best_i[0];
//		for(int x=1;x<state.population.subpops.length;x++)
//		{
//			if(best_i[x].fitness.betterThan(best_allSubpop.fitness)){
//				best_allSubpop = (Individual)(best_i[x].clone());
//			}
//		}
//		if (doGeneration) state.output.println("Subpopulation " + "0" + ":",statisticslog);
//		if (doGeneration) best_allSubpop.printIndividualForHumans(state,statisticslog);
//		if (doMessage && !silentPrint) state.output.message("Subpop " + "0" + " best fitness of generation" +
//				(best_allSubpop.evaluated ? " " : " (evaluated flag not set): ") +
//				best_allSubpop.fitness.fitnessToStringForHumans());

//		// describe the winner if there is a description
//		if (doGeneration && doPerGenerationDescription)
//		{
//			if (state.evaluator.p_problem instanceof SimpleProblemForm)
//				((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_allSubpop, 0, 0, statisticslog);
//		}


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
