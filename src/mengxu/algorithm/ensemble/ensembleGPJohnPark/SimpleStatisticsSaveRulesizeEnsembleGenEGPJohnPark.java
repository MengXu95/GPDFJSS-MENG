package mengxu.algorithm.ensemble.ensembleGPJohnPark;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.BagGP.GPRuleEvolutionStateBagGP;
import yimei.jss.algorithm.multipletreegp.SimpleStatisticsSaveRulesizeGen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleStatisticsSaveRulesizeEnsembleGenEGPJohnPark extends SimpleStatisticsSaveRulesizeGen {

	 //get seed
    protected long jobSeed;
	
    //fzhang 25.6.2018 in order to save the rulesize in each generation
    List<Long> aveSeqRulesizeTree0 = new ArrayList<>();
    List<Long> aveRouRulesizeTree1 = new ArrayList<>();
    
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
          
        // fzhang 15.6.2018 1. save the individual size in population
 		// 2. calculate the average size of individuals in population
 		// check the average size of sequencing and routing rules in population
        //fzhang 15.6.2018  in order to check the average size of sequencing and routing rules in population
			long aveSeqSizeTree0 = 0;
			long aveRouSizeTree1 = 0;
			//int indSizePop = 0; // in order to check whether SeqSizePop1 and RouSizePop2 are calculated correctly
			// should be the sum of SeqSizePop1 and RouSizePop2
			for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
				int SeqSizeTree0 = 0;
				int RouSizeTree1 = 0;
				for (int inds = 0; inds < state.population.subpops[subpop].individuals.length; inds++) {
					GPIndividual indi = (GPIndividual) state.population.subpops[subpop].individuals[inds];
					SeqSizeTree0 += indi.trees[0].child.numNodes(GPNode.NODESEARCH_ALL);
					RouSizeTree1 += indi.trees[1].child.numNodes(GPNode.NODESEARCH_ALL);
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
	 			
			/*System.out.println(SeqSizeTree0);
			System.out.println(RouSizeTree1);
			System.out.println(aveSeqRulesizeTree0.get(state.generation));
			System.out.println(aveRouRulesizeTree1.get(state.generation));*/
	 		
	 	//fzhang 15.6.2018 in order to check whether SeqSizePop1 and RouSizePop2 are calculated correctly (YES)
	 	/*	for (int pop = 0; pop < state.population.subpops.length; pop++) {
	 			for (int ind = 0; ind < state.population.subpops[pop].individuals.length; ind++) {
	 				indSizePop += state.population.subpops[pop].individuals[ind].size();
	 			}
	 		}
	 		System.out.println(indSizePop);*/
		}	
        }

	/** Logs the best individual of the generation. */
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
			if (doGeneration && state.generation != state.numGenerations-1) {
				state.output.println("Individual win with " + best_i[x].fitness.fitnessToStringForHumans(),statisticslog);
				best_i[x].printIndividualForHumans(state,statisticslog);
			}
			if (doGeneration && state.generation == state.numGenerations-1){//added by mengxu for bagGP output
				state.output.println("Ensemble win with Fitness: [-1]",statisticslog);
				state.output.println("Ensemble contribution: [-1, -1, -1, -1, -1]",statisticslog);
				List<Integer> ensembleElementIndex = ((EnsembleEvaluatorJohnPark)state.evaluator).bestIndsIndexForEachSubpop;

				for(int e=0; e<ensembleElementIndex.size(); e++){
					Individual individual = state.population.subpops[0].individuals[ensembleElementIndex.get(e)];
					individual.printIndividualForHumans(state,statisticslog);
				}
			}
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

	}
	
}
