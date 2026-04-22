package mengxu.algorithm.multitaskHeuristicLearning;

import ec.*;
import ec.eval.MasterProblem;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.ThreadPool;
import mengxu.algorithm.MAPElites.CVTMAPElites.GPRuleEvolutionStateCVTMAPElites;
import mengxu.algorithm.MAPElites.CVTMAPElites.MultiCaseBehaviour.GPRuleEvolutionStateCVTMultiCaseMAPElites;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.niching.multitreeClearingFirst;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

/**
 * The evaluator with multiple tasks (instances) for multiple subpops.
 * The evaluator is used for multitask.
 *
 * Created by Meng Xu on 14/08/2024.
 */
public class MultitaskEvaluatorBaseline extends SimpleEvaluator {

    public static final String P_PROBLEM_NUM = "problem_number";

    protected int problem_num;

    public Problem[] multiproblems;

    protected boolean multitask;


    public void setup(final EvolutionState state, final Parameter base) {
//        super.setup(state, base);

        this.multitask = true;
        this.p_problem = null; //this is the original problem, to use multiproblems for multitasking, we make this null

        // Load my problem ---
        Parameter p = base.push(P_PROBLEM_NUM);
        this.problem_num = state.parameters.getInt(p, null, 1);
        this.multiproblems = new Problem[this.problem_num];
        for(int i=0; i<this.problem_num; i++){
            Parameter p_problem_para = base.push(P_PROBLEM + "." + i);
            this.multiproblems[i] = (Problem)(state.parameters.getInstanceForParameter(
                    p_problem_para,null,Problem.class));
            this.multiproblems[i].setup(state,p_problem_para);
        }


        //==========the original setup===============
        cloneProblem =state.parameters.getBoolean(base.push(P_CLONE_PROBLEM), null, true);
        if (!cloneProblem && (state.breedthreads > 1)) // uh oh, this can't be right
            state.output.fatal("The Evaluator is not cloning its Problem, but you have more than one thread.", base.push(P_CLONE_PROBLEM));

        numTests = state.parameters.getInt(base.push(P_NUM_TESTS), null, 1);
        if (numTests < 1) numTests = 1;
        else if (numTests > 1)
        {
            String m = state.parameters.getString(base.push(P_MERGE), null);
            if (m == null)
                state.output.warning("Merge method not provided to SimpleEvaluator.  Assuming 'mean'");
            else if (m.equals(V_MEAN))
                mergeForm = MERGE_MEAN;
            else if (m.equals(V_MEDIAN))
                mergeForm = MERGE_MEDIAN;
            else if (m.equals(V_BEST))
                mergeForm = MERGE_BEST;
            else
                state.output.fatal("Bad merge method: " + m, base.push(P_NUM_TESTS), null);
        }

        if (!state.parameters.exists(base.push(P_CHUNK_SIZE), null))
        {
            chunkSize = C_AUTO;
        }
        else if (state.parameters.getString(base.push(P_CHUNK_SIZE), null).equalsIgnoreCase(V_AUTO))
        {
            chunkSize = C_AUTO;
        }
        else
        {
            chunkSize = (state.parameters.getInt(base.push(P_CHUNK_SIZE), null, 1));
            if (chunkSize == 0)  // uh oh
                state.output.fatal("Chunk Size must be either an integer >= 1 or 'auto'", base.push(P_CHUNK_SIZE), null);
        }
        //==========the original setup===============
    }

    /** Called to set up remote evaluation network contacts when the run is started.  Mostly used for client/server evaluation (see MasterProblem).  By default calls p_problem.initializeContacts(state) */
    public void initializeContacts(EvolutionState state)
    {
        for(int i=0; i<this.multiproblems.length; i++){
            Problem problem = this.multiproblems[i];
            problem.initializeContacts(state);
        }
    }

    /**  Called to shut down remote evaluation network contacts when the run is completed.  Mostly used for client/server evaluation (see MasterProblem).  By default calls p_problem.closeContacts(state,result) */
    public void closeContacts(EvolutionState state, int result)
    {
        for(int i=0; i<this.multiproblems.length; i++){
            Problem problem = this.multiproblems[i];
            problem.closeContacts(state,result);
        }
    }

    @Override
    public void evaluatePopulation(final EvolutionState state) {
        multitaskEvaluation(state);
    }

    public void multitaskEvaluation(EvolutionState state){
        if (numTests > 1)
            expand(state);

        // reset counters.  Only used in multithreading
        individualCounter = 0;
        subPopCounter = 0;

        // start up if single-threaded?
        if (state.evalthreads == 1)
        {
            int[] numinds = new int[state.population.subpops.length];
            int[] from = new int[numinds.length];
            SimpleProblemForm[] probs = new SimpleProblemForm[state.population.subpops.length];

            for(int i = 0; i < numinds.length; i++)
            {
                numinds[i] =  state.population.subpops[i].individuals.length;
                from[i] = 0;

                if (cloneProblem)
                    probs[i] = (SimpleProblemForm)(this.multiproblems[i].clone());
                else
                    probs[i] = (SimpleProblemForm)(this.multiproblems[i]);  // just use the prototype
            }
            evalPopChunk(state, numinds, from, 0, probs);
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

    /** A private helper function for evaluatePopulation which evaluates a chunk
     of individuals in a subpopulation for a given thread. Each subpop evaluate based on each task.
     This is for multitasking problem.
     Although this method is declared
     protected, you should not call it. */

    protected void evalPopChunk(EvolutionState state, int[] numinds, int[] from,
                                int threadnum, SimpleProblemForm[] probs)
    {
        Subpopulation[] subpops = state.population.subpops;
        int len = subpops.length;

        for(int pop=0;pop<len;pop++)
        {
            ((ec.Problem)probs[pop]).prepareToEvaluate(state,threadnum);
            // start evaluatin'!
            int fp = from[pop];
            int upperbound = fp+numinds[pop];
            Individual[] inds = subpops[pop].individuals;
            for (int x=fp;x<upperbound;x++)
                probs[pop].evaluate(state,inds[x], pop, threadnum);
            ((ec.Problem)probs[pop]).finishEvaluating(state,threadnum); //todo: need double check by mengxu 2024.8.14
        }
    }

}
