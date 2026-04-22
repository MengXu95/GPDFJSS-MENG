package yimei.jss.algorithm.surrogatemultitasking;

import ec.EvolutionState;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.simple.SimpleStatistics;
import ec.util.Parameter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleStatisticsSaveRulesizeGen extends SimpleStatistics{

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
		int SeqSizeTree0 = 0;
		int RouSizeTree1 = 0;
        //int indSizePop = 0; // in order to check whether SeqSizePop1 and RouSizePop2 are calculated correctly
                        // should be the sum of SeqSizePop1 and RouSizePop2
		long aveSeqSizeTree0 = 0;
		long aveRouSizeTree1 = 0;

		//note: the average rule size of the first subpop
/*		for (int ind = 0; ind < state.population.subpops[0].individuals.length; ind++) {
			GPIndividual indi = (GPIndividual) state.population.subpops[0].individuals[ind];
			SeqSizeTree0 += indi.trees[0].child.numNodes(GPNode.NODESEARCH_ALL);
			RouSizeTree1 += indi.trees[1].child.numNodes(GPNode.NODESEARCH_ALL);
		}
		aveSeqSizeTree0 = SeqSizeTree0 / state.population.subpops[0].individuals.length;
		aveRouSizeTree1 = RouSizeTree1 / state.population.subpops[0].individuals.length;
		
		aveSeqRulesizeTree0.add(aveSeqSizeTree0);
		aveRouRulesizeTree1.add(aveRouSizeTree1);*/

		//2020.2.12 save the average routing and sequencing rule size for all subpops, respectively.
        for (int subpop = 0; subpop < state.population.subpops.length; subpop++){
        	for(int inds = 0; inds < state.population.subpops[subpop].individuals.length; inds++){
				GPIndividual indi = (GPIndividual) state.population.subpops[0].individuals[inds];
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

			//save the information for the first subpop
/*			try {
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
	 			*/

//2020.2.12 save the information of all the subpops
//			try {
//				BufferedWriter writer = new BufferedWriter(new FileWriter(rulesizeFile));
//				writer.write("Gen,aveSeqRuleSize0,aveRouRuleSize0,aveSeqRuleSize1,aveRouRuleSize1,aveSeqRuleSize2,aveRouRuleSize2");
//				writer.newLine();
//				for (int gen = 0; gen < aveSeqRulesizeTree0.size(); gen+=state.population.subpops.length) {
//					writer.write(gen/state.population.subpops.length + "," + aveSeqRulesizeTree0.get(gen) + "," + aveRouRulesizeTree1.get(gen) + "," +
//							aveSeqRulesizeTree0.get(gen+1) + "," + aveRouRulesizeTree1.get(gen+1) + "," +
//							aveSeqRulesizeTree0.get(gen+2) + "," + aveRouRulesizeTree1.get(gen+2));
//					writer.newLine();
//				}
//				writer.close();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}

			//2021.4.6 surrogate multiobjective --- one task
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(rulesizeFile));
				writer.write("Gen,aveSeqRuleSize0,aveRouRuleSize0");
				writer.newLine();
				for (int gen = 0; gen < aveSeqRulesizeTree0.size(); gen+=state.population.subpops.length) {
					writer.write(gen/state.population.subpops.length + "," + aveSeqRulesizeTree0.get(gen) + "," + aveRouRulesizeTree1.get(gen));
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
	
}
