/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package yimei.jss.algorithm.surrogatemultitasking;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleStatistics;
import ec.util.Output;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/* 
 * MultiObjectiveStatistics.java
 * 
 * Created: Thu Feb 04 2010
 * By: Faisal Abidi and Sean Luke
 *
 */

/*
 * MultiObjectiveStatistics are a SimpleStatistics subclass which overrides the finalStatistics
 * method to output the current Pareto Front in various ways:
 *
 * <ul>
 * <li><p>Every individual in the Pareto Front is written to the end of the statistics log.
 * <li><p>A summary of the objective values of the Pareto Front is written to stdout.
 * <li><p>The objective values of the Pareto Front are written in tabular form to a special
 * Pareto Front file specified with the parameters below.  This file can be easily read by
 * gnuplot or Excel etc. to display the Front (if it's 2D or perhaps 3D).
 * 
 * <p>
 * <b>Parameters</b><br>
 * <table>
 * <tr>
 * <td valign=top><i>base</i>.<tt>front</tt><br>
 * <font size=-1>String (a filename)</font></td>
 * <td valign=top>(The Pareto Front file, if any)</td>
 * </tr>
 * </table>
 */
//2020.5.14 this class has been changed by adding the code for calculating the average rule size of subpopulation.
public class MultiObjectiveStatistics extends SimpleStatistics
    {   
    /** front file parameter */
    public static final String P_PARETO_FRONT_FILE = "front";
    public static final String P_SILENT_FRONT_FILE = "silent.front";
        
    public boolean silentFront;

    /** The pareto front log */
    public int frontLog = 0;  // stdout by default

    public void setup(final EvolutionState state, final Parameter base)
        {
        super.setup(state,base);

        silentFront = state.parameters.getBoolean(base.push(P_SILENT), null, false);
        // yes, we're stating it a second time.  It's correct logic.
        silentFront = state.parameters.getBoolean(base.push(P_SILENT_FRONT_FILE), null, silentFront);
        
        File frontFile = state.parameters.getFile(base.push(P_PARETO_FRONT_FILE),null);

        if (silentFront)
            {
            frontLog = Output.NO_LOGS;
            }
        else if (frontFile!=null)
            {
            try
                {
                frontLog = state.output.addLog(frontFile, !compress, compress);
                }
            catch (IOException i)
                {
                state.output.fatal("An IOException occurred while trying to create the log " + frontFile + ":\n" + i);
                }
            }
        else state.output.warning("No Pareto Front statistics file specified, printing to stdout at end.", base.push(P_PARETO_FRONT_FILE));
        }



    /** Logs the best individual of the run. */
    public void finalStatistics(final EvolutionState state, final int result)
        {
        bypassFinalStatistics(state, result);  // just call super.super.finalStatistics(...)
        
        //fzhang 15.11.2018  output seed information in out.stat
    /*    Parameter p;
		// Get the job seed.
		p = new Parameter("seed").push(""+0);
        double jobSeed = state.parameters.getLongWithDefault(p, null, 0);
        state.output.println("seed: "+jobSeed,statisticslog);
        */
        if (doFinal) state.output.println("\n\n\n PARETO FRONTS", statisticslog);
        for (int s = 0; s < state.population.subpops.length; s++)
            {
            MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[s].individuals[0].fitness);
            if (doFinal) state.output.println("\n\nPareto Front of Subpopulation " + s, statisticslog);

            // build front
            ArrayList front = typicalFitness.partitionIntoParetoFront(state.population.subpops[s].individuals, null, null);

            // sort by objective[0]
            Object[] sortedFront = front.toArray();
            QuickSort.qsort(sortedFront, new SortComparator()
                {
                public boolean lt(Object a, Object b)
                    {
                    return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) < 
                        (((MultiObjectiveFitness) ((Individual) b).fitness)).getObjective(0));
                    }
                
                public boolean gt(Object a, Object b)
                    {
                    return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) > 
                        ((MultiObjectiveFitness) (((Individual) b).fitness)).getObjective(0));
                    }
                });
                        
            // print out front to statistics log
            if (doFinal)
                for (int i = 0; i < sortedFront.length; i++)
                    ((Individual)(sortedFront[i])).printIndividualForHumans(state, statisticslog);
                
            // write short version of front out to disk
            if (!silentFront)
                {
                if (state.population.subpops.length > 1)
                    state.output.println("Subpopulation " + s, frontLog);
                for (int i = 0; i < sortedFront.length; i++)
                    {
                    Individual ind = (Individual)(sortedFront[i]);
                    MultiObjectiveFitness mof = (MultiObjectiveFitness) (ind.fitness);
                    double[] objectives = mof.getObjectives();
        
                    String line = "";
                    for (int f = 0; f < objectives.length; f++)
                        line += (objectives[f] + " ");
                    state.output.println(line, frontLog);
                    }
                }
            }
        }

        //get seed
        protected long jobSeed;

        //fzhang 25.6.2018 in order to save the rulesize in each generation
        List<Long> aveSeqRulesizeTree0 = new ArrayList<>();
        List<Long> aveRouRulesizeTree1 = new ArrayList<>();
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

			/*try {
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
			}*/

/*                //2020.2.12 save the information of all the 3 subpops
                try {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(rulesizeFile));
                    writer.write("Gen,aveSeqRuleSize0,aveRouRuleSize0,aveSeqRuleSize1,aveRouRuleSize1,aveSeqRuleSize2,aveRouRuleSize2");
                    writer.newLine();
                    for (int gen = 0; gen < aveSeqRulesizeTree0.size(); gen+=state.population.subpops.length) {
                        writer.write(gen/state.population.subpops.length + "," + aveSeqRulesizeTree0.get(gen) + "," + aveRouRulesizeTree1.get(gen) + "," +
                                aveSeqRulesizeTree0.get(gen+1) + "," + aveRouRulesizeTree1.get(gen+1) + "," +
                                aveSeqRulesizeTree0.get(gen+2) + "," + aveRouRulesizeTree1.get(gen+2));
                        writer.newLine();
                    }
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }*/


                //2020.7.31 save the information of all the 2 subpops
/*                try {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(rulesizeFile));
                    writer.write("Gen,aveSeqRuleSize0,aveRouRuleSize0,aveSeqRuleSize1,aveRouRuleSize1");
                    writer.newLine();
                    for (int gen = 0; gen < aveSeqRulesizeTree0.size(); gen+=state.population.subpops.length) {
                        writer.write(gen/state.population.subpops.length + "," + aveSeqRulesizeTree0.get(gen) + "," + aveRouRulesizeTree1.get(gen) + "," +
                                aveSeqRulesizeTree0.get(gen+1) + "," + aveRouRulesizeTree1.get(gen+1));
                        writer.newLine();
                    }
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }*/

                //2021.4.15 save the information of one population
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
