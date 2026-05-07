/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multiobjective.MPSLGP;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleProblemForm;
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
import yimei.jss.helper.PopulationUtils;

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

public class PSLMultiObjectiveStatisticsTopN extends SimpleStatistics
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

    private int taskIndexOf(Individual ind) {
        if (ind != null && ind.fitness instanceof PSLMultiObjectiveFitness) {
            return ((PSLMultiObjectiveFitness) ind.fitness).getTaskIndex();
        }
        return -1;
    }

    private void printTaskAnnotatedIndividual(EvolutionState state, Individual ind, int log) {
        state.output.println("MPSLGP Task: " + taskIndexOf(ind), log);
        ind.printIndividualForHumans(state, log);
    }

    private void printIndividualInTaskGroup(EvolutionState state, Individual ind, int log) {
        ind.printIndividualForHumans(state, log);
    }

    private String frontLineWithTask(Individual ind) {
        MultiObjectiveFitness mof = (MultiObjectiveFitness) (ind.fitness);
        double[] objectives = mof.getObjectives();
        StringBuilder line = new StringBuilder();
        line.append(taskIndexOf(ind));
        for (int f = 0; f < objectives.length; f++) {
            line.append(' ').append(objectives[f]);
        }
        return line.toString();
    }

    private boolean useTaskAwareOutput(EvolutionState state) {
        return state instanceof GPRuleEvolutionStatePSL && ((GPRuleEvolutionStatePSL) state).numTasks > 1;
    }

    private int numTasks(EvolutionState state) {
        if (state instanceof GPRuleEvolutionStatePSL) {
            return Math.max(1, ((GPRuleEvolutionStatePSL) state).numTasks);
        }
        return 1;
    }

    private Individual[] individualsForTask(EvolutionState state, int taskIndex) {
        List<Individual> individuals = new ArrayList<>();
        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            for (Individual ind : state.population.subpops[subpop].individuals) {
                if (ind != null && taskIndexOf(ind) == taskIndex) {
                    individuals.add(ind);
                }
            }
        }
        return individuals.toArray(new Individual[0]);
    }

    private Object[] paretoFrontForTask(EvolutionState state, int taskIndex) {
        Individual[] individuals = individualsForTask(state, taskIndex);
        if (individuals.length == 0) {
            return new Object[0];
        }
        MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness) individuals[0].fitness;
        ArrayList front = typicalFitness.partitionIntoParetoFront(individuals, null, null);
        Object[] sortedFront = front.toArray();
        sortByObjective0(sortedFront);
        return sortedFront;
    }

    private void sortByObjective0(Object[] individuals) {
        QuickSort.qsort(individuals, new SortComparator()
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
    }

    private void sortByTaskFitness(Individual[] individuals) {
        QuickSort.qsort(individuals, new SortComparator()
        {
            public boolean lt(Object a, Object b)
            {
                return ((Individual) a).fitness.betterThan(((Individual) b).fitness);
            }

            public boolean gt(Object a, Object b)
            {
                return ((Individual) b).fitness.betterThan(((Individual) a).fitness);
            }
        });
    }

    private Individual[] topIndividualsForTask(EvolutionState state, int taskIndex, int topN, boolean amongParetoFront) {
        Individual[] candidates;
        if (amongParetoFront) {
            Object[] front = paretoFrontForTask(state, taskIndex);
            candidates = new Individual[front.length];
            for (int i = 0; i < front.length; i++) {
                candidates[i] = (Individual) front[i];
            }
        }
        else {
            candidates = individualsForTask(state, taskIndex);
        }
        if (candidates.length == 0) {
            return candidates;
        }
        sortByTaskFitness(candidates);
        int length = Math.min(topN, candidates.length);
        Individual[] topIndividuals = new Individual[length];
        System.arraycopy(candidates, 0, topIndividuals, 0, length);
        return topIndividuals;
    }

    private void postEvaluationStatisticsByTask(final EvolutionState state) {
        boolean amongParetoFront = ((GPRuleEvolutionStatePSL)state).topNFromParetoFront;
        if (doGeneration) state.output.println("\nGeneration: " + state.generation,statisticslog);
        if (doGeneration) state.output.println("Best Individual by MPSLGP Task:",statisticslog);
        for (int task = 0; task < numTasks(state); task++) {
            Individual[] topIndividuals = topIndividualsForTask(state, task, 1, amongParetoFront);
            if (topIndividuals.length == 0) {
                if (doGeneration) state.output.println("MPSLGP Task " + task + ": no individuals", statisticslog);
                if (doMessage && !silentPrint) state.output.message("MPSLGP task " + task + " has no individuals in this generation.");
                continue;
            }
            Individual best = topIndividuals[0];
            if (doGeneration) state.output.println("MPSLGP Task " + task + ":",statisticslog);
                if (doGeneration) printIndividualInTaskGroup(state, best, statisticslog);
            if (doMessage && !silentPrint) state.output.message("MPSLGP task " + task + " best fitness of generation" +
                    (best.evaluated ? " " : " (evaluated flag not set): ") +
                    best.fitness.fitnessToStringForHumans());
        }
    }

    private void printTaskGroupedTopN(EvolutionState state, boolean printOutput) {
        if (!printOutput) {
            return;
        }
        int topN = ((GPRuleEvolutionStatePSL) state).outputTopN;
        boolean amongParetoFront = ((GPRuleEvolutionStatePSL) state).topNFromParetoFront;
        state.output.println("\nTop N individuals by MPSLGP Task:",statisticslog);
        for (int task = 0; task < numTasks(state); task++) {
            Individual[] topIndividuals = topIndividualsForTask(state, task, topN, amongParetoFront);
            state.output.println("\nMPSLGP Task " + task + " Top " + topIndividuals.length + " individuals:", statisticslog);
            for (Individual ind : topIndividuals) {
                printIndividualInTaskGroup(state, ind, statisticslog);
            }
        }
    }

    private void printTaskGroupedParetoFronts(EvolutionState state, boolean printOutput) {
        for (int task = 0; task < numTasks(state); task++) {
            Object[] sortedFront = paretoFrontForTask(state, task);
            if (printOutput) {
                state.output.println("\n\nPareto Front for MPSLGP Task " + task, statisticslog);
                for (Object element : sortedFront) {
                    printIndividualInTaskGroup(state, (Individual) element, statisticslog);
                }
            }
            if (!silentFront) {
                for (Object element : sortedFront) {
                    state.output.println(frontLineWithTask((Individual) element), frontLog);
                }
            }
        }
    }

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


        /** Logs the best individual of the generation. */
        boolean warned = false;
        public void postEvaluationStatistics(final EvolutionState state)
        {
            if (useTaskAwareOutput(state)) {
                postEvaluationStatisticsByTask(state);
                return;
            }
            boolean amongParetoFront = ((GPRuleEvolutionStatePSL)state).topNFromParetoFront;
            if(amongParetoFront){
                int numTopN = ((GPRuleEvolutionStatePSL)state).outputTopN;
                Individual[] sortedParetoFront = PopulationUtils.sortForParetoSortLearningOnlyParetoFront(state.population,numTopN);
                // for now we just print the best fitness per subpopulation.
                Individual[] best_i = new Individual[state.population.subpops.length];  // quiets compiler complaints
                for(int x=0;x<state.population.subpops.length;x++)//todo: only for one subpop by mengxu 2024.5.30
                {
                    best_i[x] = sortedParetoFront[0];
                    for(int y=1;y<sortedParetoFront.length;y++)
                    {
                        if (sortedParetoFront[y] == null)
                        {
                            if (!warned)
                            {
                                state.output.warnOnce("Null individuals found in subpopulation");
                                warned = true;  // we do this rather than relying on warnOnce because it is much faster in a tight loop
                            }
                        }
                        else if (best_i[x] == null || sortedParetoFront[y].fitness.betterThan(best_i[x].fitness))
                            best_i[x] = sortedParetoFront[y];
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
                    if (doGeneration) state.output.println("Subpopulation " + x + ":",statisticslog);
                        if (doGeneration) printTaskAnnotatedIndividual(state, best_i[x], statisticslog);
                    if (doMessage && !silentPrint) state.output.message("Subpop " + x + " best fitness of generation" +
                            " [task " + taskIndexOf(best_i[x]) + "]" +
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
            else{
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
                    if (doGeneration) state.output.println("Subpopulation " + x + ":",statisticslog);
                        if (doGeneration) printTaskAnnotatedIndividual(state, best_i[x], statisticslog);
                    if (doMessage && !silentPrint) state.output.message("Subpop " + x + " best fitness of generation" +
                            " [task " + taskIndexOf(best_i[x]) + "]" +
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



    /** Logs the best individual of the run. */
    public void finalStatistics(final EvolutionState state, final int result)
        {
        bypassFinalStatistics(state, result);  // just call super.super.finalStatistics(...)

        if (useTaskAwareOutput(state)) {
            if (doFinal) state.output.println("\n\n\n PARETO FRONTS BY MPSLGP TASK", statisticslog);
            printTaskGroupedParetoFronts(state, doFinal);
            return;
        }
        
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
                        printTaskAnnotatedIndividual(state, (Individual)(sortedFront[i]), statisticslog);

                // write short version of front out to disk
                if (!silentFront)
                {
                    if (state.population.subpops.length > 1)
                        state.output.println("Subpopulation " + 0, frontLog);
                    for (int i = 0; i < sortedFront.length; i++)
                    {
                        Individual ind = (Individual)(sortedFront[i]);
                        state.output.println(frontLineWithTask(ind), frontLog);
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
                        printTaskAnnotatedIndividual(state, nonDominatedIndsFromAllSubpops.get(i), statisticslog);


                // write short version of front out to disk
            // write short version of front out to disk
                if (!silentFront)
                {
                    if (state.population.subpops.length > 1)
                        state.output.println("Subpopulation " + 0, frontLog);
                    for (int i = 0; i < nonDominatedIndsFromAllSubpops.size(); i++)
                    {
                        Individual ind = nonDominatedIndsFromAllSubpops.get(i);
                        state.output.println(frontLineWithTask(ind), frontLog);
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
                            printTaskAnnotatedIndividual(state, (Individual)(sortedFront[i]), statisticslog);

                    // write short version of front out to disk
                    if (!silentFront)
                    {
                        if (state.population.subpops.length > 1)
                            state.output.println("Subpopulation " + s, frontLog);
                        for (int i = 0; i < sortedFront.length; i++)
                        {
                            Individual ind = (Individual)(sortedFront[i]);
                            state.output.println(frontLineWithTask(ind), frontLog);
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

            if (useTaskAwareOutput(state)) {
                if (doMiddle) state.output.println("\n\n\n PARETO FRONTS BY MPSLGP TASK", statisticslog);
                printTaskGroupedParetoFronts(state, doMiddle);
                return;
            }

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
                        printTaskAnnotatedIndividual(state, (Individual)(sortedFront[i]), statisticslog);

                // write short version of front out to disk
                if (!silentFront)
                {
                    if (state.population.subpops.length > 1)
                        state.output.println("Subpopulation " + 0, frontLog);
                    for (int i = 0; i < sortedFront.length; i++)
                    {
                        Individual ind = (Individual)(sortedFront[i]);
                        state.output.println(frontLineWithTask(ind), frontLog);
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
                        printTaskAnnotatedIndividual(state, nonDominatedIndsFromAllSubpops.get(i), statisticslog);


                // write short version of front out to disk
                // write short version of front out to disk
                if (!silentFront)
                {
                    if (state.population.subpops.length > 1)
                        state.output.println("Subpopulation " + 0, frontLog);
                    for (int i = 0; i < nonDominatedIndsFromAllSubpops.size(); i++)
                    {
                        Individual ind = nonDominatedIndsFromAllSubpops.get(i);
                        state.output.println(frontLineWithTask(ind), frontLog);
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
                            printTaskAnnotatedIndividual(state, (Individual)(sortedFront[i]), statisticslog);

                    // write short version of front out to disk
                    if (!silentFront)
                    {
                        if (state.population.subpops.length > 1)
                            state.output.println("Subpopulation " + s, frontLog);
                        for (int i = 0; i < sortedFront.length; i++)
                        {
                            Individual ind = (Individual)(sortedFront[i]);
                            state.output.println(frontLineWithTask(ind), frontLog);
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
                    printTaskAnnotatedIndividual(state, (Individual)(externalArchive.getArchive().get(i)), statisticslog);

            // write short version of front out to disk
            if (!silentFront)
            {
                if (state.population.subpops.length > 1)
                    state.output.println("Subpopulation 0", frontLog);
                for (int i = 0; i < externalArchive.getArchive().size(); i++)
                {
                    Individual ind = (Individual)(externalArchive.getArchive().get(i));
                    state.output.println(frontLineWithTask(ind), frontLog);
                }
            }
        }

        /** Allows MultiObjectiveStatistics etc. to call super.super.finalStatistics(...) without
         calling super.finalStatistics(...) */
        protected void bypassFinalStatistics(EvolutionState state, int result)
        {
            if (useTaskAwareOutput(state)) {
                printTaskGroupedTopN(state, doFinal);
                return;
            }
            // for now we just print the best fitness
            //sort pop based on PSL fitness
            boolean amongParetoFront = ((GPRuleEvolutionStatePSL)state).topNFromParetoFront;
            if(amongParetoFront){
                int numTopN = ((GPRuleEvolutionStatePSL)state).outputTopN;
                Individual[] sortedParetoFront = PopulationUtils.sortForParetoSortLearningOnlyParetoFront(state.population,numTopN);
                if (doFinal) state.output.println("\nTop N individuals:",statisticslog);
                for(int x=0;x<state.population.subpops.length;x++ )
                {
                    if(state instanceof GPRuleEvolutionStatePSL){//add by mengxu 2024.5.21
                        if(((GPRuleEvolutionStatePSL)state).outputTopN > 1){
//                        if (doFinal) state.output.println("\nTop N individuals:",statisticslog);
                            for(int i=0; i<((GPRuleEvolutionStatePSL)state).outputTopN ; i++){
                                if (doFinal) printTaskAnnotatedIndividual(state, sortedParetoFront[i], statisticslog);
                            }
                        }
                    }
                }
            }
            else{
                PopulationUtils.sortForParetoSortLearning(state.population);

//            if (doFinal) state.output.println("\nBest Individual of Run:",statisticslog);
                if (doFinal) state.output.println("\nTop N individuals:",statisticslog);
                for(int x=0;x<state.population.subpops.length;x++ )
                {
//                best_of_run[x] = state.population.subpops[x].individuals[0];//add by mengxu 2024.5.21
//                if (doFinal) state.output.println("Subpopulation " + x + ":",statisticslog);
//                if (doFinal) best_of_run[x].printIndividualForHumans(state,statisticslog);
//                if (doMessage && !silentPrint) state.output.message("Subpop " + x + " best fitness of run: " + best_of_run[x].fitness.fitnessToStringForHumans());
//
//                // finally describe the winner if there is a description
//                if (doFinal && doDescription)
//                    if (state.evaluator.p_problem instanceof SimpleProblemForm)
//                        ((SimpleProblemForm)(state.evaluator.p_problem.clone())).describe(state, best_of_run[x], x, 0, statisticslog);

                    if(state instanceof GPRuleEvolutionStatePSL){//add by mengxu 2024.5.21
                        if(((GPRuleEvolutionStatePSL)state).outputTopN > 1){
//                        if (doFinal) state.output.println("\nTop N individuals:",statisticslog);
                            for(int i=0; i<((GPRuleEvolutionStatePSL)state).outputTopN; i++){
                                if (doFinal) printTaskAnnotatedIndividual(state, state.population.subpops[x].individuals[i], statisticslog);
                            }
                        }
                    }
                }
            }
        }

    }
