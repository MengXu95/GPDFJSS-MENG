/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multiobjective.MPSLGP.FixedStructureGPIndividual;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleStatistics;
import ec.util.Output;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
import mengxu.algorithm.multiobjective.MOEADarchive.GPRuleEvolutionStateMOEADarchive;
import mengxu.algorithm.multiobjective.MOEADm2m.GPRuleEvolutionStateMOEADm2m;
import mengxu.algorithm.multiobjective.MOEADmap.GPRuleEvolutionStateMOEADmap;
import mengxu.algorithm.multiobjective.utils.Archive;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
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

public class PSLMultiObjectiveStatisticsFixedStructure extends SimpleStatistics
    {   
    /** front file parameter */
    public static final String P_PARETO_FRONT_FILE = "front";
    public static final String P_SILENT_FRONT_FILE = "silent.front";
        
    public boolean silentFront;

    public static final String P_COMBINE_SUBPOPS_OUTPUT = "combine-subpops-output"; //added by mengxu 2022.10.14
    public boolean combineSubpopsOutput; //added by mengxu 2022.10.14

    public static final String P_OUTPUT_SUBPOPS_TOGETHER = "output-subpops-together"; //added by mengxu 2022.10.24
    public boolean outputSubpopsTogether; //added by mengxu 2022.10.24

    /** The pareto front log */
    public int frontLog = 0;  // stdout by default

    public void setup(final EvolutionState state, final Parameter base)
        {
        super.setup(state,base);

        doMiddle = state.parameters.getBoolean(base.push(P_DO_MIDDLE),null,true);//add by mengxu 2022.08.05
        silentFront = state.parameters.getBoolean(base.push(P_SILENT), null, false);
        // yes, we're stating it a second time.  It's correct logic.
        silentFront = state.parameters.getBoolean(base.push(P_SILENT_FRONT_FILE), null, silentFront);

        combineSubpopsOutput = state.parameters.getBoolean(base.push(P_COMBINE_SUBPOPS_OUTPUT), null, false);
        outputSubpopsTogether = state.parameters.getBoolean(base.push(P_OUTPUT_SUBPOPS_TOGETHER), null, true);

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

        if(combineSubpopsOutput && !outputSubpopsTogether){
                Individual[] individualsFromAllSubpop = state.population.subpops[0].individuals;

                for (int s = 1; s < state.population.subpops.length; s++) {
                    Individual[] second = state.population.subpops[s].individuals;
                    individualsFromAllSubpop = (Individual[]) ArrayUtils.addAll(individualsFromAllSubpop, second);
                }

                MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[0].individuals[0].fitness);
                if (doFinal) state.output.println("\n\nPareto Front of Subpopulation " + 0, statisticslog);

                // build front
                ArrayList front = typicalFitness.partitionIntoParetoFront(individualsFromAllSubpop, null, null);

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
                        state.output.println("Subpopulation " + 0, frontLog);
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
            else if(!combineSubpopsOutput && outputSubpopsTogether){
//                Individual[] individualsFromAllSubpop = state.population.subpops[0].individuals;
                List<Individual> nonDominatedIndsFromAllSubpops = new ArrayList<>();

//                for (int s = 1; s < state.population.subpops.length; s++) {
//                    Individual[] second = state.population.subpops[s].individuals;
//                    individualsFromAllSubpop = (Individual[]) ArrayUtils.addAll(individualsFromAllSubpop, second);
//                }

                for (int s = 0; s < state.population.subpops.length; s++)
                {
                    MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[s].individuals[0].fitness);
//                    if (doFinal) state.output.println("\n\nPareto Front of Subpopulation " + s, statisticslog);

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

//                    Collections.addAll(nonDominatedIndsFromAllSubpops, (Individual[])sortedFront);

                    for(int i = 0; i < sortedFront.length; i++) {
                        Individual ind = (Individual) (sortedFront[i]);
                        nonDominatedIndsFromAllSubpops.add(ind);
                    }
                }

                if (doFinal) state.output.println("\n\nPareto Front of Subpopulation " + 0, statisticslog);
                // print out front to statistics log
                if (doFinal)
                    for (int i = 0; i < nonDominatedIndsFromAllSubpops.size(); i++)
                        nonDominatedIndsFromAllSubpops.get(i).printIndividualForHumans(state, statisticslog);


                // write short version of front out to disk
            // write short version of front out to disk
                if (!silentFront)
                {
                    if (state.population.subpops.length > 1)
                        state.output.println("Subpopulation " + 0, frontLog);
                    for (int i = 0; i < nonDominatedIndsFromAllSubpops.size(); i++)
                    {
                        Individual ind = nonDominatedIndsFromAllSubpops.get(i);
                        MultiObjectiveFitness mof = (MultiObjectiveFitness) (ind.fitness);
                        double[] objectives = mof.getObjectives();

                        String line = "";
                        for (int f = 0; f < objectives.length; f++)
                            line += (objectives[f] + " ");
                        state.output.println(line, frontLog);
                    }
                }
            }
            else{
                //original
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

                    if (doFinal) state.output.println("\n\nPareto Front of Subpopulation " + s, statisticslog);
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

        }


        /** Logs the best individual of the run in the middle generation.
         * Add by mengxu 2022.08.05.
         * */
        public void middleStatistics(final EvolutionState state, final int result)
        {
            bypassFinalStatistics(state, result);  // just call super.super.finalStatistics(...)

            //fzhang 15.11.2018  output seed information in out.stat
    /*    Parameter p;
		// Get the job seed.
		p = new Parameter("seed").push(""+0);
        double jobSeed = state.parameters.getLongWithDefault(p, null, 0);
        state.output.println("seed: "+jobSeed,statisticslog);
        */
            if (doMiddle) state.output.println("\n\n\n PARETO FRONTS", statisticslog);
            if(combineSubpopsOutput && !outputSubpopsTogether){
                Individual[] individualsFromAllSubpop = state.population.subpops[0].individuals;

                for (int s = 1; s < state.population.subpops.length; s++) {
                    Individual[] second = state.population.subpops[s].individuals;
                    individualsFromAllSubpop = (Individual[]) ArrayUtils.addAll(individualsFromAllSubpop, second);
                }

                MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[0].individuals[0].fitness);
                if (doMiddle) state.output.println("\n\nPareto Front of Subpopulation " + 0, statisticslog);

                // build front
                ArrayList front = typicalFitness.partitionIntoParetoFront(individualsFromAllSubpop, null, null);

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
                if (doMiddle)
                    for (int i = 0; i < sortedFront.length; i++)
                        ((Individual)(sortedFront[i])).printIndividualForHumans(state, statisticslog);

                // write short version of front out to disk
                if (!silentFront)
                {
                    if (state.population.subpops.length > 1)
                        state.output.println("Subpopulation " + 0, frontLog);
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
            else if(!combineSubpopsOutput && outputSubpopsTogether){
//                Individual[] individualsFromAllSubpop = state.population.subpops[0].individuals;
                List<Individual> nonDominatedIndsFromAllSubpops = new ArrayList<>();

//                for (int s = 1; s < state.population.subpops.length; s++) {
//                    Individual[] second = state.population.subpops[s].individuals;
//                    individualsFromAllSubpop = (Individual[]) ArrayUtils.addAll(individualsFromAllSubpop, second);
//                }

                for (int s = 0; s < state.population.subpops.length; s++)
                {
                    MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[s].individuals[0].fitness);
//                    if (doMiddle) state.output.println("\n\nPareto Front of Subpopulation " + s, statisticslog);

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

//                    Collections.addAll(nonDominatedIndsFromAllSubpops, (Individual[])sortedFront);

                    for(int i = 0; i < sortedFront.length; i++) {
                        Individual ind = (Individual) (sortedFront[i]);
                        nonDominatedIndsFromAllSubpops.add(ind);
                    }
                }

                if (doMiddle) state.output.println("\n\nPareto Front of Subpopulation " + 0, statisticslog);
                // print out front to statistics log
                if (doMiddle)
                    for (int i = 0; i < nonDominatedIndsFromAllSubpops.size(); i++)
                        nonDominatedIndsFromAllSubpops.get(i).printIndividualForHumans(state, statisticslog);


                // write short version of front out to disk
                // write short version of front out to disk
                if (!silentFront)
                {
                    if (state.population.subpops.length > 1)
                        state.output.println("Subpopulation " + 0, frontLog);
                    for (int i = 0; i < nonDominatedIndsFromAllSubpops.size(); i++)
                    {
                        Individual ind = nonDominatedIndsFromAllSubpops.get(i);
                        MultiObjectiveFitness mof = (MultiObjectiveFitness) (ind.fitness);
                        double[] objectives = mof.getObjectives();

                        String line = "";
                        for (int f = 0; f < objectives.length; f++)
                            line += (objectives[f] + " ");
                        state.output.println(line, frontLog);
                    }
                }
            }
            else{
                //original
                for (int s = 0; s < state.population.subpops.length; s++)
                {
                    MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[s].individuals[0].fitness);
                    if (doMiddle) state.output.println("\n\nPareto Front of Subpopulation " + s, statisticslog);

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
                    if (doMiddle)
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

//            for (int s = 0; s < state.population.subpops.length; s++)
//            {
//                MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(state.population.subpops[s].individuals[0].fitness);
//                if (doMiddle) state.output.println("\n\nPareto Front of Subpopulation " + s, statisticslog);
//
//                // build front
//                ArrayList front = typicalFitness.partitionIntoParetoFront(state.population.subpops[s].individuals, null, null);
//
//                // sort by objective[0]
//                Object[] sortedFront = front.toArray();
//                QuickSort.qsort(sortedFront, new SortComparator()
//                {
//                    public boolean lt(Object a, Object b)
//                    {
//                        return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) <
//                                (((MultiObjectiveFitness) ((Individual) b).fitness)).getObjective(0));
//                    }
//
//                    public boolean gt(Object a, Object b)
//                    {
//                        return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) >
//                                ((MultiObjectiveFitness) (((Individual) b).fitness)).getObjective(0));
//                    }
//                });
//
//                // print out front to statistics log
//                if (doMiddle)
//                    for (int i = 0; i < sortedFront.length; i++)
//                        ((Individual)(sortedFront[i])).printIndividualForHumans(state, statisticslog);
//
//                // write short version of front out to disk
//                if (!silentFront)
//                {
//                    if (state.population.subpops.length > 1)
//                        state.output.println("Subpopulation " + s, frontLog);
//                    for (int i = 0; i < sortedFront.length; i++)
//                    {
//                        Individual ind = (Individual)(sortedFront[i]);
//                        MultiObjectiveFitness mof = (MultiObjectiveFitness) (ind.fitness);
//                        double[] objectives = mof.getObjectives();
//
//                        String line = "";
//                        for (int f = 0; f < objectives.length; f++)
//                            line += (objectives[f] + " ");
//                        state.output.println(line, frontLog);
//                    }
//                }
//            }
        }


        /** Logs the all individuals from the external archive so far.
         * Add by mengxu 2022.09.02.
         * */
        public void externalArchiveStatistics(final EvolutionState state, final int result)
        {
            bypassFinalStatistics(state, result);  // just call super.super.finalStatistics(...)

            if (doMiddle) state.output.println("\n\n\n PARETO FRONTS", statisticslog);

            Archive externalArchive = new Archive();
            if(state instanceof GPRuleEvolutionStateMOEADmap){
                externalArchive = ((GPRuleEvolutionStateMOEADmap)state).externalArchive;
            }
            else if(state instanceof GPRuleEvolutionStateMOEADm2m){
                externalArchive = ((GPRuleEvolutionStateMOEADm2m)state).externalArchive;
            }
            else if(state instanceof GPRuleEvolutionStateMOEADarchive){
                externalArchive = ((GPRuleEvolutionStateMOEADarchive)state).externalArchive;
            }
//            Archive externalArchive = ((GPRuleEvolutionStateMOEADmap)state).externalArchive;
            //note that individuals in the external archive are evaluated on different instances (generation), so the fitness is not that meaningful.
            //need validation set to select individuals or directly test all the individuals in archive on test set.

            // print out front to statistics log
            if (doMiddle)
                state.output.println("\n\nPareto Front of Subpopulation 0", statisticslog);
                for (int i = 0; i < externalArchive.getArchive().size(); i++)
                    ((Individual)(externalArchive.getArchive().get(i))).printIndividualForHumans(state, statisticslog);

            // write short version of front out to disk
            if (!silentFront)
            {
                if (state.population.subpops.length > 1)
                    state.output.println("Subpopulation 0", frontLog);
                for (int i = 0; i < externalArchive.getArchive().size(); i++)
                {
                    Individual ind = (Individual)(externalArchive.getArchive().get(i));
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
