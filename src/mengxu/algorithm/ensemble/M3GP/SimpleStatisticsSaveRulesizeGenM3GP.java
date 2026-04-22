package mengxu.algorithm.ensemble.M3GP;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.simple.SimpleProblemForm;
import ec.simple.SimpleStatistics;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.ensemble.eGP.EnsembleEvaluatoreGP;
import yimei.jss.algorithm.multipletreegp.SimpleStatisticsSaveRulesizeGen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleStatisticsSaveRulesizeGenM3GP extends SimpleStatisticsSaveRulesizeGen {

	 //get seed
    protected long jobSeed;
	
    //fzhang 25.6.2018 in order to save the rulesize in each generation
    List<Long> aveSeqRulesizeTree0 = new ArrayList<>();
    List<Long> aveRouRulesizeTree1 = new ArrayList<>();

	public EnsembleRule[] best_of_run = null;

	public void postInitializationStatistics(final EvolutionState state)
	{
		super.postInitializationStatistics(state);

		// set up our best_of_run array -- can't do this in setup, because
		// we don't know if the number of subpopulations has been determined yet
		best_of_run = new EnsembleRule[state.population.subpops.length];
	}
    
    /** GENERATIONAL: Called immediately before evaluation occurs. */
    public void preEvaluationStatistics(final EvolutionState state)
        {
    	for(int x=0;x<children.length;x++)
            children[x].preEvaluationStatistics(state);
        
    	//fzhang 17.6.2018  get the seed value
    	Parameter p;
  		// Get the job seed.
  		p = new Parameter("seed").push(""+0);
        jobSeed = state.parameters.getLongWithDefault(p, null, 0);
          
        // mengxu 2023.07.31 save the ensemble size in population
 		// 2. calculate the average size of individuals in population
 		// check the average size of sequencing and routing rules in population
        //fzhang 15.6.2018  in order to check the average size of sequencing and routing rules in population
			long aveSeqSizeTree0 = 0;
			long aveRouSizeTree1 = 0;
			//int indSizePop = 0; // in order to check whether SeqSizePop1 and RouSizePop2 are calculated correctly
			// should be the sum of SeqSizePop1 and RouSizePop2
			List<EnsembleRule> ensemblePool = ((EnsembleEvaluatorM3GP)state.evaluator).ensemblePool;
			for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
				int SeqSizeTree0 = 0;
				int RouSizeTree1 = 0;
				for (int i = 0; i < ensemblePool.size(); i++) {
					EnsembleRule ensembleRule = ensemblePool.get(i);
					List<Individual> individualsList = ensembleRule.getEnsemble();
					for (int inds = 0; inds < individualsList.size(); inds++) {
						GPIndividual indi = (GPIndividual) individualsList.get(inds);
						SeqSizeTree0 += indi.trees[0].child.numNodes(GPNode.NODESEARCH_ALL);
						RouSizeTree1 += indi.trees[1].child.numNodes(GPNode.NODESEARCH_ALL);
					}
				}
				aveSeqSizeTree0 = SeqSizeTree0 / state.population.subpops[subpop].individuals.length;
				aveRouSizeTree1 = RouSizeTree1 / state.population.subpops[subpop].individuals.length;

				aveSeqRulesizeTree0.add(aveSeqSizeTree0);
				aveRouRulesizeTree1.add(aveRouSizeTree1);
			}
		
		if(state.generation == state.numGenerations-1) {
			//fzhang  15.6.2018  save the size of rules in each generation
		    File rulesizeFile = new File("job." + jobSeed + ".aveGenRulesize.csv"); // jobSeed = 0
		    
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(rulesizeFile));
				writer.write("Gen,aveSeqRuleSize,aveRouRuleSize,avePairSize");
				writer.newLine();
				for (int gen = 0; gen < aveSeqRulesizeTree0.size(); gen++) {
					writer.write(gen + "," + aveSeqRulesizeTree0.get(gen) + "," + aveRouRulesizeTree1.get(gen) + "," +
							(aveSeqRulesizeTree0.get(gen) + aveRouRulesizeTree1.get(gen))/2);
					writer.newLine();
				}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}	
        }

	/** Logs the best individual of the generation.
	 * Modified by mengxu 2023.07.31 to log the best ensemble
	 * */
	boolean warned = false;

	public void postEvaluationStatistics(final EvolutionState state)
	{

		for(int x=0;x<children.length;x++)
			children[x].postEvaluationStatistics(state);

		// for now we just print the best fitness per subpopulation.
		List<EnsembleRule> ensembleRuleList = ((EnsembleEvaluatorM3GP)state.evaluator).ensemblePool;
		EnsembleRule[] best_ens = new EnsembleRule[state.population.subpops.length];
		for(int x=0;x<state.population.subpops.length;x++)
		{
			best_ens[x] = ensembleRuleList.get(0);
			for(int y=1;y<ensembleRuleList.size();y++)
			{
				if (ensembleRuleList.get(y) == null)
				{
					if (!warned)
					{
						state.output.warnOnce("Null individuals found in subpopulation");
						warned = true;  // we do this rather than relying on warnOnce because it is much faster in a tight loop
					}
				}
				else if (best_ens[x] == null || ensembleRuleList.get(y).getFitness().betterThan(best_ens[x].getFitness()))
					best_ens[x] = ensembleRuleList.get(y);
				if (best_ens[x] == null)
				{
					if (!warned)
					{
						state.output.warnOnce("Null individuals found in subpopulation");
						warned = true;  // we do this rather than relying on warnOnce because it is much faster in a tight loop
					}
				}
			}

			// now test to see if it's the new best_of_run
			if (best_of_run[x]==null || best_ens[x].getFitness().betterThan(best_of_run[x].getFitness()))
				best_of_run[x] = best_ens[x].clone();
		}


		// print the best-of-generation individual
		if (doGeneration) state.output.println("\nGeneration: " + state.generation,statisticslog);
		if (doGeneration) state.output.println("Best Individual:",statisticslog);
		for(int x=0;x<state.population.subpops.length;x++)
		{
			//add by mengxu for ensemble fitness comparison
//			double bestFitness = best_i[x].fitness.fitness();
//			int bestIdEnsemble = -1;
//			List<EnsembleRule> ensemblePool = ((EnsembleEvaluator)state.evaluator).ensemblePool;
//			for(int i=0; i<ensemblePool.size(); i++){
//				EnsembleRule ensemble = ensemblePool.get(i);
//				Fitness ensembleFitness = ensemble.getFitness();
//				double fitness = ensembleFitness.fitness();
//				if(fitness < bestFitness){
//					bestFitness = fitness;
//					bestIdEnsemble = i;
//				}
//			}

			if (doGeneration) state.output.println("Subpopulation " + x + ":",statisticslog);
			if (doGeneration){//added by mengxu for bagGP output
				state.output.println("Ensemble win with Fitness: [" + best_ens[x].getFitness().fitness() + "]",statisticslog);

				String contribution_Str = "[";
				for(int i=0; i<best_ens[x].getEnsemble().size()-1; i++){
					contribution_Str += "-1, ";
				}
				contribution_Str += "-1]";
				state.output.println("Ensemble contribution: " + contribution_Str,statisticslog);

				for(int e=0; e<best_ens[x].getEnsemble().size(); e++){
					best_ens[x].getEnsemble().get(e).printIndividualForHumans(state,statisticslog);
				}
			}
			if (doMessage && !silentPrint) state.output.message("Subpop " + x + " best fitness of generation" +
					(best_ens[x].evaluated ? " " : " (evaluated flag not set): ") +
					best_ens[x].getFitness().fitnessToStringForHumans());

			// describe the winner if there is a description
			//hide the following part by mengxu 2023.08.01
//			if (doGeneration && doPerGenerationDescription)
//			{
//				if (state.evaluator.p_problem instanceof SimpleProblemForm)
//					((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_i[x], x, 0, statisticslog);
//			}
		}

	}

//	public void postEvaluationStatistics(final EvolutionState state)
//	{
////		super.postEvaluationStatistics(state);
//
//		for(int x=0;x<children.length;x++)
//			children[x].postEvaluationStatistics(state);
//
//		// for now we just print the best fitness per subpopulation.
//		List<EnsembleRule> ensembleRuleList = ((EnsembleEvaluatorM3GP)state.evaluator).ensemblePool;
//		EnsembleRule[] best_ens = new EnsembleRule[state.population.subpops.length];
//		for(int x=0;x<state.population.subpops.length;x++)
//		{
//			best_ens[x] = ensembleRuleList.get(0);
//			for(int y=1;y<ensembleRuleList.size();y++)
//			{
//				if (ensembleRuleList.get(y) == null)
//				{
//					if (!warned)
//					{
//						state.output.warnOnce("Null individuals found in subpopulation");
//						warned = true;  // we do this rather than relying on warnOnce because it is much faster in a tight loop
//					}
//				}
//				else if (best_ens[x] == null || ensembleRuleList.get(y).getFitness().betterThan(best_ens[x].getFitness()))
//					best_ens[x] = ensembleRuleList.get(y);
//				if (best_ens[x] == null)
//				{
//					if (!warned)
//					{
//						state.output.warnOnce("Null individuals found in subpopulation");
//						warned = true;  // we do this rather than relying on warnOnce because it is much faster in a tight loop
//					}
//				}
//			}
//
//			// now test to see if it's the new best_of_run
//			if (best_of_run[x]==null || best_ens[x].getFitness().betterThan(best_of_run[x].getFitness()))
//				best_of_run[x] = best_ens[x].clone();
//		}
//
//		// print the best-of-generation individual
//		if (doGeneration) state.output.println("\nGeneration: " + state.generation,statisticslog);
//		if (doGeneration) state.output.println("Best Ensemble:",statisticslog);
//		for(int x=0;x<state.population.subpops.length;x++)
//		{
//			if (doGeneration) state.output.println("Subpopulation " + x + ":",statisticslog);
//			if (doGeneration){
//				List<Individual> individualList = best_ens[x].getEnsemble();
//				for(int y=0; y< individualList.size(); y++){
//					individualList.get(y).printIndividualForHumans(state,statisticslog);
//				}
//			}
//			if (doMessage && !silentPrint) state.output.message("Subpop " + x + " best fitness of generation" +
//					(best_ens[x].evaluated ? " " : " (evaluated flag not set): ") +
//					best_ens[x].getFitness().fitnessToStringForHumans());
//
//			// describe the winner if there is a description
//			if (doGeneration && doPerGenerationDescription)
//			{
//				//hide the following part
////				if (state.evaluator.p_problem instanceof SimpleProblemForm)
////					((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_ens[x], x, 0, statisticslog);
//			}
//		}
//	}

	/** Logs the best individual of the run. */
	public void finalStatistics(final EvolutionState state, final int result)
	{
//		super.finalStatistics(state,result);

		for(int x=0;x<children.length;x++)
			children[x].finalStatistics(state, result);

		// for now we just print the best fitness

		if (doFinal) state.output.println("\nBest Individual of Run:",statisticslog);
		for(int x=0;x<state.population.subpops.length;x++ )
		{
			if (doFinal) state.output.println("Subpopulation " + x + ":",statisticslog);
			if (doFinal){
				List<Individual> individualList = best_of_run[x].getEnsemble();
				for(int y=0; y< individualList.size(); y++){
					individualList.get(y).printIndividualForHumans(state,statisticslog);
				}
			}
			if (doMessage && !silentPrint) state.output.message("Subpop " + x + " best fitness of run: " + best_of_run[x].getFitness().fitnessToStringForHumans());

			// finally describe the winner if there is a description
			//hide by mengxu
//			if (doFinal && doDescription)
//				if (state.evaluator.p_problem instanceof SimpleProblemForm)
//					((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_of_run[x], x, 0, statisticslog);
		}
	}
}
