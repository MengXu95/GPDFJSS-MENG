package mengxu.algorithm.multicaseEnsemble.multisubpopmulticaseEnsemble;

import ec.*;
import ec.gp.GPInitializer;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.QuickSort;
import ec.util.SortComparator;
import ec.util.ThreadPool;
import mengxu.algorithm.multiobjective.MOEADarchive.MOEADarchiveInitializer;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.util.ArrayList;
import java.util.List;

public class MSMCEEvaluator extends SimpleEvaluator{

    public static final String P_NUMCASE = "num-case";
    int numCase;
    int numJobsPerCase;

    public List<Individual> bestIndividualsForPrecedingCases = new ArrayList<>();

    public static final String P_SUBPOP_0_SIZE = "subpop0-size";
    int subpop0size;

    /**
     * The original population size is stored here so NSGA2 knows how large to
     * create the archive (it's the size of the original population -- keep in mind
     * that NSGA2Breeder had made the population larger to include the children.
     */
    public int[] originalPopSize;
    public int[] multicaseSubPopSize;
    public List<List<Integer>> indIndexForSubPop = new ArrayList<>();

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        numCase = state.parameters.getInt(new Parameter(P_NUMCASE), null, 0);
        numJobsPerCase = 5000/numCase;
        if (numCase < 0)
            state.output.fatal("The number of cases must be an integer >= 0.", base.push(P_NUMCASE));
        subpop0size = state.parameters.getInt(new Parameter(P_SUBPOP_0_SIZE), null, 1);

        //this only suitable for one sub-pop by mengxu 2023.01.14
        Parameter p = new Parameter(GPInitializer.P_POP);
        int subpopsLength = state.parameters.getInt(p.push(Population.P_SIZE), null, 1);
        Parameter p_subpop;
        originalPopSize = new int[subpopsLength];
        for (int i = 0; i < subpopsLength; i++) {
            p_subpop = p.push(Population.P_SUBPOP).push("" + i).push(Subpopulation.P_SUBPOPSIZE);
            originalPopSize[i] = state.parameters.getInt(p_subpop, null, 1);
        }
        if(subpopsLength==1){
            multicaseSubPopSize = new int[numCase+1];
            multicaseSubPopSize[0] = subpop0size;
            List<Integer> indIndexForSubPop_0 = new ArrayList<>();
            for(int j=0; j<subpop0size; j++){
                indIndexForSubPop_0.add(j);
            }
            indIndexForSubPop.add(indIndexForSubPop_0);
            int subpop_size = (originalPopSize[0]-subpop0size)/numCase;
            for(int i=1; i<numCase+1; i++){
                multicaseSubPopSize[i] = subpop_size;
                List<Integer> indIndexForSubPop_i = new ArrayList<>();
                for(int j=subpop0size+(i-1)*subpop_size; j<subpop0size+i*subpop_size; j++){
                    indIndexForSubPop_i.add(j);
                }
                indIndexForSubPop.add(indIndexForSubPop_i);
            }
        }
    }

    /**
     * Evaluates the population, then builds the archive and reduces the population
     * to just the archive.
     */
    public void evaluatePopulation(final EvolutionState state) {
        bestIndividualsForPrecedingCases.clear();
        //evaluate individuals by subpop for each case
        for(int i=0; i<numCase+1; i++){
            if(i==0){//evaluate the whole simulation
                evaluatePopulationForCase(state, i);
                //todo:need to add the best ind obtained into bestIndividualsForPrecedingCases
            }else{//evaluate mainly for each case
                evaluatePopulationForCase(state, i);
            }
        }

        //todo: need to map individuals into subpop for case by mengxu 2023.01.14

    }

    /** A simple evaluator that doesn't do any coevolutionary
     evaluation.  Basically it applies evaluation pipelines,
     one per thread, to various subchunks of a new population. */
    public void evaluatePopulationForCase(final EvolutionState state, int caseIndex)
    {
        if (this.numTests > 1)
            this.expand(state);

        // reset counters.  Only used in multithreading
        this.individualCounter = 0;
        this.subPopCounter = 0;

        // start up if single-threaded?
        if (state.evalthreads == 1)
        {
            int[] numinds = new int[state.population.subpops.length];
            int[] from = new int[numinds.length];

            for(int i = 0; i < numinds.length; i++)
            {
                numinds[i] =  state.population.subpops[i].individuals.length;
                from[i] = 0;
            }

            SimpleProblemForm prob = null;
            if (cloneProblem)
                prob = (SimpleProblemForm)(p_problem.clone());
            else
                prob = (SimpleProblemForm)(p_problem);  // just use the prototype
            evalPopChunkForEachCase(state, numinds, from, 0, prob, caseIndex);
        }
        else
        {
            ThreadPool.Worker[] threads = new ThreadPool.Worker[state.evalthreads];
            for(int i = 0; i < threads.length; i++)
            {
                SimpleEvaluatorThread run = new SimpleEvaluatorThread();
                run.threadnum = i;
                run.state = state;
                run.prob = (SimpleProblemForm)(p_problem.clone());
                threads[i] = pool.start(run, "ECJ Evaluation Thread " + i);
            }

            // join
            pool.joinAll();
        }

        if (numTests > 1)
            contract(state);
    }

    protected void evalPopChunkForEachCase(EvolutionState state, int[] numinds, int[] from,
                                int threadnum, SimpleProblemForm p, int caseIndex)
    {
        ((ec.Problem)p).prepareToEvaluate(state,threadnum);

        Subpopulation[] subpops = state.population.subpops;
        int len = subpops.length;


        for(int pop=0;pop<len;pop++)
        {
            // start evaluatin'!
            List<Integer> indsIndexForCase = indIndexForSubPop.get(caseIndex);
            int fp = 0;
            int upperbound = fp+indsIndexForCase.size();
            Individual[] inds = subpops[pop].individuals;
            Individual bestInd = inds[indsIndexForCase.get(0)];
            for (int x=fp;x<upperbound;x++) {
                int index = indsIndexForCase.get(x);
                ((MSMCEMultipleTreeRuleOptimizationProblem)p).evaluate(state, inds[index], pop, threadnum, bestIndividualsForPrecedingCases, numJobsPerCase);
                Fitness fitness = inds[index].fitness;
                Fitness bestFitness = bestInd.fitness;
                if(fitness.betterThan(bestFitness)){
                    bestInd = inds[index];
                }
            }
            if(caseIndex>0){
                bestIndividualsForPrecedingCases.add(bestInd);
            }
        }

        ((ec.Problem)p).finishEvaluating(state,threadnum);
    }


}
