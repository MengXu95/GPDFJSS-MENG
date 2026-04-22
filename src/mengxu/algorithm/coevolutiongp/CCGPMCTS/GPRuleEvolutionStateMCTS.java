package mengxu.algorithm.coevolutiongp.CCGPMCTS;

import ec.EvolutionState;
import ec.Individual;
import ec.coevolve.MultiPopCoevolutionaryEvaluator;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.simple.SimpleEvolutionState;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.mcts.Mcts;
import yimei.jss.algorithm.coevolutiongp.MultiPopCoevolutionaryEvaluator1;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.gp.function.*;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.rule.AbstractRuleHelper;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author yimei
 *
 */

public class GPRuleEvolutionStateMCTS extends GPRuleEvolutionState {

	public static final String P_FUNCTIONSET = "fset";
	public static final String P_MCTS_POLICY = "mcts-policy";

	public String mctsPolicy;
	public double proHybird = 0;

	@Override
	public void setup(EvolutionState state, Parameter base) {
		super.setup(this, base);
		this.mctsPolicy = state.parameters.getString(new Parameter(P_MCTS_POLICY), null);

		// Load my function set
//		String s = state.parameters.getString(base.push(P_FUNCTIONSET),null);
//		String s = "f0";
//		if (s==null)
//			state.output.fatal("No function set given for the GPTreeConstraints ");
//		GPFunctionSet functionset = GPFunctionSet.functionSetFor(s,state);


		List<GPNode> availTerminalGPNodes0 = new ArrayList<>(Arrays.asList(this.getTerminals(0)));
//		Collections.addAll(availGPNodes0, functionset.nodes[0]);
		List<GPNode> availFunctionGPNodes0 = new ArrayList<>();
		availFunctionGPNodes0.add(new Add());
		availFunctionGPNodes0.add(new Div());
		availFunctionGPNodes0.add(new Max());
		availFunctionGPNodes0.add(new Min());
		availFunctionGPNodes0.add(new Mul());
		availFunctionGPNodes0.add(new Sub());
		this.mctses[0] = new Mcts(availTerminalGPNodes0, availFunctionGPNodes0,true);


		List<GPNode> availTerminalGPNodes1 = new ArrayList<>(Arrays.asList(this.getTerminals(0)));
//		Collections.addAll(availGPNodes1, functionset.nodes[1]);
		List<GPNode> availFunctionGPNodes1 = new ArrayList<>();
		availFunctionGPNodes1.add(new Add());
		availFunctionGPNodes1.add(new Div());
		availFunctionGPNodes1.add(new Max());
		availFunctionGPNodes1.add(new Min());
		availFunctionGPNodes1.add(new Mul());
		availFunctionGPNodes1.add(new Sub());
		this.mctses[1] = new Mcts(availTerminalGPNodes1, availFunctionGPNodes1, true);
	}

	public Mcts[] getMctses() {
		return mctses;
	}

	public double getProHybird() {
		return proHybird;
	}

    @Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

	    //System.out.println("generation "+generation);
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

	    if(generation == 0){
			evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
		}
	    else{
			((MultiPopCoevolutionaryEvaluator)evaluator).afterCoevolutionaryEvaluation(this);
		}
	    statistics.postEvaluationStatistics(this);

	    //modified by mengxu 2021.04.16. update totalreward of MCTSTree
		if(generation == 0){
			for(int sub=0; sub<population.subpops.length; sub++){
				Individual[] individuals = population.subpops[sub].individuals;
				if(this.mctsPolicy.equals("totalReward-div-visitCount-linked") ||
						this.mctsPolicy.equals("totalReward-div-visitCount")){
					for(int i=0; i<individuals.length; i++){
						Individual ind = individuals[i];
						GPTree gpTree0 = ((GPIndividual)ind).trees[0];
						this.mctses[sub].frontPropagateTotalReward(gpTree0, ind.fitness.fitness());
					}
				}
				else if(this.mctsPolicy.equals("num-infinity-reward") ||
						this.mctsPolicy.equals("mean-non-infinity-reward") ||
						this.mctsPolicy.equals("mean-non-infinity-reward-v2") ||
						this.mctsPolicy.equals("mean-non-infinity-reward-pro") ||
						this.mctsPolicy.equals("mean-non-infinity-reward-linked-v2") ||
						this.mctsPolicy.equals("num-infinity-reward-pro") ||
						this.mctsPolicy.equals("mean-non-infinity-reward-linked-pro") ||
						this.mctsPolicy.equals("mean-non-infinity-reward-linked-pro-v2")){
					int numInfinity = 0;
					for(int i=0; i<individuals.length; i++){
						Individual ind = individuals[i];
						GPTree gpTree0 = ((GPIndividual)ind).trees[0];
						if(ind.fitness.fitness() > this.mctses[0].getMctsRootNode().INFINITY){
							numInfinity++;
						}
						this.mctses[sub].frontPropagateAllReward(this.generation, gpTree0, ind.fitness.fitness());
					}
					System.out.println("numInfinity: " + numInfinity);
				}
				else if(this.mctsPolicy.equals("hybird")){//20210518
					for(int i=0; i<individuals.length; i++){
						Individual ind = individuals[i];
						GPTree gpTree0 = ((GPIndividual)ind).trees[0];
						this.mctses[sub].frontPropagateTotalReward(gpTree0, ind.fitness.fitness());
					}
				}
				else if(this.mctsPolicy.equals("hybirdv1")){//20210518
					for(int i=0; i<individuals.length; i++){
						Individual ind = individuals[i];
						GPTree gpTree0 = ((GPIndividual)ind).trees[0];
						this.mctses[sub].frontPropagateAllReward(this.generation, gpTree0, ind.fitness.fitness());
					}
				}
			}
		}

	    // SHOULD WE QUIT?
	    if (evaluator.runComplete(this) && quitOnRunComplete)
	        {
	        output.message("Found Ideal Individual");
	        return R_SUCCESS;
	        }
	    // SHOULD WE QUIT?
	    if (generation == numGenerations-1)
	        {
	    	generation++; // in this way, the last generation value will be printed properly.  fzhang 28.3.2018
	        return R_FAILURE;
	        }

	    // PRE-BREEDING EXCHANGING
	    statistics.prePreBreedingExchangeStatistics(this);
	    population = exchanger.preBreedingExchangePopulation(this);  /** Simply returns state.population. */
	    statistics.postPreBreedingExchangeStatistics(this);

	    String exchangerWantsToShutdown = exchanger.runComplete(this);  /** Always returns null */
	    if (exchangerWantsToShutdown!=null)
	        {
	        output.message(exchangerWantsToShutdown);
	        /*
	         * Don't really know what to return here.  The only place I could
	         * find where runComplete ever returns non-null is
	         * IslandExchange.  However, that can return non-null whether or
	         * not the ideal individual was found (for example, if there was
	         * a communication error with the server).
	         *
	         * Since the original version of this code didn't care, and the
	         * result was initialized to R_SUCCESS before the while loop, I'm
	         * just going to return R_SUCCESS here.
	         */

	        return R_SUCCESS;
	        }

		// Generate new instances if needed
		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
		if (problem.getEvaluationModel().isRotatable()) {
			problem.rotateEvaluationModel();
		}

	    // BREEDING
	    statistics.preBreedingStatistics(this);

	    population = breeder.breedPopulation(this); //!!!!!!   return newpop;  if it is NSGA-II, the population here is 2N

	    // POST-BREEDING EXCHANGING
	    statistics.postBreedingStatistics(this);   //position 1  here, a new pop has been generated.

	    // POST-BREEDING EXCHANGING
	    statistics.prePostBreedingExchangeStatistics(this);
	    population = exchanger.postBreedingExchangePopulation(this);   /** Simply returns state.population. */
	    statistics.postPostBreedingExchangeStatistics(this);  //position 2

//	    // Generate new instances if needed
//		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
//	    if (problem.getEvaluationModel().isRotatable()) {
//			problem.rotateEvaluationModel();
//		}

	    // INCREMENT GENERATION AND CHECKPOINT
	    generation++;
	    if (checkpoint && generation%checkpointModulo == 0)
	        {
	        output.message("Checkpointing");
	        statistics.preCheckpointStatistics(this);
	        Checkpoint.setCheckpoint(this);
	        statistics.postCheckpointStatistics(this);
	        }

	    return R_NOTDONE;
	}

	private void setupTerminals() {
        Parameter p;

        //Need to know how many populations we're expecting here, as will need
        //one terminal set per population
        int numSubPops = parameters.getInt(new Parameter("pop.subpops"),null);

        if (numSubPops == 1) {
            p = new Parameter(P_TERMINALS_FROM);

            terminalsFrom = new String[]{parameters.getStringWithDefault(p,
                    null, "relative")};

            p = new Parameter(P_INCLUDE_ERC);
            //includeErc seems like does not have influence.
            includeErc = new boolean[]{parameters.getBoolean(p, null, false)};
            initTerminalSet();
        } else if (numSubPops == 2) {
            terminalsFrom = new String[numSubPops];
            includeErc = new boolean[numSubPops];
            int subPopNum = 0;

            p = new Parameter(P_TERMINALS_FROM + "." + subPopNum);
            String subPop1TerminalSet = parameters.getStringWithDefault(p,
                    null, null);
            if (subPop1TerminalSet == null) {
                //might have provided other value by mistake, we should check for this
                p = new Parameter(P_TERMINALS_FROM);
                subPop1TerminalSet = parameters.getStringWithDefault(p,
                        null, "relative");
                output.warning("No terminal set for subpopulation 1 specified - using "+subPop1TerminalSet+".");

            }
            terminalsFrom[subPopNum] = subPop1TerminalSet;

            subPopNum++;
            p = new Parameter(P_TERMINALS_FROM + "." + subPopNum);
            String subPop2TerminalSet = parameters.getStringWithDefault(p,
                    null, null);
            if (subPop2TerminalSet == null) {
                //use whatever we settled on for first population
                subPop2TerminalSet = subPop1TerminalSet;
                output.warning("No terminal set for subpopulation 2 specified - using terminal set for subpopulation 1.");
            }
            terminalsFrom[subPopNum] = subPop2TerminalSet;
            //TODO: Add support for erc - will be false by default

            initTerminalSet(); //right
        }
    }
}
