/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package ec.breed;
import ec.*;
import ec.coevolve.MultiPopCoevolutionaryEvaluator;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.simple.SimpleProblemForm;
import ec.util.*;
import mengxu.algorithm.coevolutiongp.CCGPMCTS.GPRuleEvolutionStateMCTS;
import mengxu.algorithm.multipletreegp.GPRuleEvolutionStateUpdateImmediate;
import mengxu.algorithm.multipletreegp.GPRuleEvolutionStateUpdateLate;

/* 
 * ReproductionPipeline.java
 * 
 * Created: Thu Nov  8 13:39:32 EST 2001
 * By: Sean Luke
 */

/**
 * ReproductionPipeline is a BreedingPipeline which simply makes a copy
 * of the individuals it recieves from its source.  If the source is another
 * BreedingPipeline, the individuals have already been cloned, so ReproductionPipeline
 * won't clone them again...unless you force it to do so by turning on the <tt>must-clone</tt>
 * parameter.
 *
 <p><b>Typical Number of Individuals Produced Per <tt>produce(...)</tt> call</b><br>
 ...as many as the child produces

 <p><b>Number of Sources</b><br>
 1

 <p><b>Parameters</b><br>
 <table>
 <tr><td valign=top><i>base.</i><tt>must-clone</tt><br>
 <font size=-1>bool =  <tt>true</tt> or <tt>false</tt> (default)</font></td>
 <td valign=top>(do we <i>always</i> clone our individuals, or only clone if the individual hasn't already been cloned by our source?  Typically you want <tt>false</tt>)</td></tr>

 </table>
 <p><b>Default Base</b><br>
 breed.reproduce

 * @author Sean Luke
 * @version 1.0 
 */

public class ReproductionPipeline extends BreedingPipeline
    {
    public static final String P_REPRODUCE = "reproduce";
    public static final String P_MUSTCLONE = "must-clone";
    public static final int NUM_SOURCES = 1;
    
    public boolean mustClone;

    public static final String P_INDIVIDUAL_EVALUATION_IMMEDIATE = "individual-evaluation-immediate";
    public boolean individualEvaluationImmediate;
    
    public Parameter defaultBase() { return BreedDefaults.base().push(P_REPRODUCE); }

    public int numSources() { return NUM_SOURCES; }

    public void setup(final EvolutionState state, final Parameter base)
        {
        super.setup(state,base);
        Parameter def = defaultBase();
        mustClone = state.parameters.getBoolean(base.push(P_MUSTCLONE), def.push(P_MUSTCLONE),false);

        //modified by mengxu 2021.07.05
        individualEvaluationImmediate = state.parameters.getBoolean(new Parameter(P_INDIVIDUAL_EVALUATION_IMMEDIATE), false);

        if (likelihood != 1.0)
        state.output.warning("ReproductionPipeline given a likelihood other than 1.0.  This is nonsensical and will be ignored.",
            base.push(P_LIKELIHOOD),
            def.push(P_LIKELIHOOD));
        }
        
    public int produce(
        final int min, 
        final int max, 
        final int start,
        final int subpopulation,
        final Individual[] inds,
        final EvolutionState state,
        final int thread) 
        {
        // grab individuals from our source and stick 'em right into inds.
        // we'll modify them from there
        int n = sources[0].produce(min,max,start,subpopulation,inds,state,thread);
                
        if (mustClone || sources[0] instanceof SelectionMethod)
            for(int q=start; q < n+start; q++) {
                inds[q] = (Individual) (inds[q].clone());
                //modified by mengxu for MCTS to evaluate individual immediatelly!
                inds[q].evaluated = false;
                if(individualEvaluationImmediate){
                    if (state.population.subpops.length == 1 && ((GPIndividual) state.population.subpops[0].individuals[0]).trees.length == 2) {
                        SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
                        prob.evaluate(state, inds[q], 0, 0);
                    }
                    else if(state.population.subpops.length == 2 && ((GPIndividual) state.population.subpops[0].individuals[0]).trees.length == 1){
                        ((MultiPopCoevolutionaryEvaluator)state.evaluator).evaluateIndividual(state,inds[q],subpopulation);
                    }
                }
                //modified by mengxu 20210520---------------------
//                System.out.println("individual fitness: " + inds[q].fitness.fitness());
                if (state.population.subpops.length == 1 && ((GPIndividual) state.population.subpops[0].individuals[0]).trees.length == 2) {
                    if (state instanceof GPRuleEvolutionStateUpdateLate) {
                        String mctsPolicy = ((GPRuleEvolutionStateUpdateLate) state).mctsPolicy;
                        boolean mctsUpdateLate = ((GPRuleEvolutionStateUpdateLate) state).mctses[0].isMctsUpdateLate();
                        if (mctsUpdateLate) {
                            if (mctsPolicy.equals("visitCount")) {
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                GPTree gpTree1 = ((GPIndividual) inds[q]).trees[1];
                                ((GPRuleEvolutionStateUpdateLate) state).mctses[0].frontPropagateVisitCount(gpTree0, inds[q].fitness.fitness());
                                ((GPRuleEvolutionStateUpdateLate) state).mctses[1].frontPropagateVisitCount(gpTree1, inds[q].fitness.fitness());
                            }
                        }
                    } else if (state instanceof GPRuleEvolutionStateUpdateImmediate) {
                        String mctsPolicy = ((GPRuleEvolutionStateUpdateImmediate) state).mctsPolicy;
                        boolean mctsUpdateLate = ((GPRuleEvolutionStateUpdateImmediate) state).mctses[0].isMctsUpdateLate();
                        if (mctsUpdateLate) {
                            if (mctsPolicy.equals("visitCount")) {
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                GPTree gpTree1 = ((GPIndividual) inds[q]).trees[1];
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[0].frontPropagateVisitCount(gpTree0, inds[q].fitness.fitness());
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[1].frontPropagateVisitCount(gpTree1, inds[q].fitness.fitness());
                            } else if (mctsPolicy.equals("totalReward-div-visitCount-linked") ||
                                    mctsPolicy.equals("totalReward-div-visitCount")) {
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                GPTree gpTree1 = ((GPIndividual) inds[q]).trees[1];
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[0].frontPropagateTotalReward(gpTree0, inds[q].fitness.fitness());
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[1].frontPropagateTotalReward(gpTree1, inds[q].fitness.fitness());
                            } else if (mctsPolicy.equals("num-infinity-reward") ||
                                    mctsPolicy.equals("mean-non-infinity-reward") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-v2") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-pro") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-linked-v2") ||
                                    mctsPolicy.equals("num-infinity-reward-pro") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-linked-pro") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-linked-pro-v2")) {
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                GPTree gpTree1 = ((GPIndividual) inds[q]).trees[1];
                                if (inds[q].fitness.fitness() > ((GPRuleEvolutionStateUpdateImmediate) state).mctses[0].getMctsRootNode().INFINITY) {
                                }
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[0].frontPropagateAllReward(state.generation, gpTree0, inds[q].fitness.fitness());
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[1].frontPropagateAllReward(state.generation, gpTree1, inds[q].fitness.fitness());
                            } else if (mctsPolicy.equals("hybird")) {//20210518
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                GPTree gpTree1 = ((GPIndividual) inds[q]).trees[1];
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[0].frontPropagateTotalReward(gpTree0, inds[q].fitness.fitness());
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[1].frontPropagateTotalReward(gpTree1, inds[q].fitness.fitness());
                            } else if (mctsPolicy.equals("hybirdv1")) {//20210518
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                GPTree gpTree1 = ((GPIndividual) inds[q]).trees[1];
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[0].frontPropagateAllReward(state.generation, gpTree0, inds[q].fitness.fitness());
                                ((GPRuleEvolutionStateUpdateImmediate) state).mctses[1].frontPropagateAllReward(state.generation, gpTree1, inds[q].fitness.fitness());
                            }
                        }
                    }
                }else if(state.population.subpops.length == 2 && ((GPIndividual) state.population.subpops[0].individuals[0]).trees.length == 1) {
                    //CCGP, one tree for sequencing rule, one for routing rule
//                System.out.println("Is CCGP!");
                    if (individualEvaluationImmediate) {
//                    System.out.println("CCGP evaluation!");
                        ((MultiPopCoevolutionaryEvaluator)state.evaluator).evaluateIndividual(state,inds[q],subpopulation);
                    }
                    //modified by mengxu 20210520---------------------
//                System.out.println("individual fitness: " + inds[q].fitness.fitness());
                    if (state instanceof GPRuleEvolutionStateMCTS) {
                        String mctsPolicy = ((GPRuleEvolutionStateMCTS) state).mctsPolicy;
                        boolean mctsUpdateLate = state.mctses[subpopulation].isMctsUpdateLate();
                        if (mctsUpdateLate) {
                            if (mctsPolicy.equals("visitCount")) {
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                state.mctses[subpopulation].frontPropagateVisitCount(gpTree0, inds[q].fitness.fitness());
                            }else if (mctsPolicy.equals("totalReward-div-visitCount-linked") ||
                                    mctsPolicy.equals("totalReward-div-visitCount")) {
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                state.mctses[subpopulation].frontPropagateTotalReward(gpTree0, inds[q].fitness.fitness());
                            } else if (mctsPolicy.equals("num-infinity-reward") ||
                                    mctsPolicy.equals("mean-non-infinity-reward") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-v2") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-pro") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-linked-v2") ||
                                    mctsPolicy.equals("num-infinity-reward-pro") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-linked-pro") ||
                                    mctsPolicy.equals("mean-non-infinity-reward-linked-pro-v2")) {
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                state.mctses[subpopulation].frontPropagateAllReward(state.generation, gpTree0, inds[q].fitness.fitness());
                            } else if (mctsPolicy.equals("hybird")) {//20210518
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                state.mctses[subpopulation].frontPropagateTotalReward(gpTree0, inds[q].fitness.fitness());
                            } else if (mctsPolicy.equals("hybirdv1")) {//20210518
                                GPTree gpTree0 = ((GPIndividual) inds[q]).trees[0];
                                state.mctses[subpopulation].frontPropagateAllReward(state.generation, gpTree0, inds[q].fitness.fitness());
                            }
                        }
                    }
                }
            }
        return n;
        }

        //fzhang 2019.6.15
        @Override
        public int produceFrac(int min, int max, int start, int subpopulation, Individual[] inds, EvolutionState state, int thread) {
            return 0;
        }
    }
