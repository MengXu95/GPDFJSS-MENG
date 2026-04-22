package mengxu.mcts;

import ec.EvolutionState;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.gp.koza.KozaNodeSelector;
import mengxu.algorithm.multipletreegp.GPRuleEvolutionStateUpdateImmediate;
import mengxu.algorithm.multipletreegp.GPRuleEvolutionStateUpdateLate;

public class MctsNodeSelector extends KozaNodeSelector {

    //todo: need to modify this to select poor node
    public GPNode pickNode(final EvolutionState state,
                           final int subpopulation,
                           final int thread,
                           final GPIndividual ind,
                           final GPTree tree,
                           Mcts mctsTree)
    {
        GPNode rootNode = tree.child;
        MctsTreeNode currentMctsTreeNode = mctsTree.findCorrespondingMctsTreeNode(rootNode);
        double currentScore;
        GPNode selectNode = rootNode;



        if (state instanceof GPRuleEvolutionStateUpdateLate) {
            String mctsPolicy = ((GPRuleEvolutionStateUpdateLate) state).mctsPolicy;
            boolean mctsUpdateLate = ((GPRuleEvolutionStateUpdateLate) state).mctses[0].isMctsUpdateLate();
            if (mctsUpdateLate) {
                if (mctsPolicy.equals("visitCount")) {
//                    currentScore = currentMctsTreeNode.getVisitCount();
//                    if(rootNode.children.length != 0){
//                        for(int i=0; i<rootNode.children.length; i++){
//                            GPNode child = rootNode.children[i];
//                            currentMctsTreeNode = currentMctsTreeNode.findCorrespondingMctsTreeNode(child);
//                            double score = currentMctsTreeNode.getVisitCount();
//                            if(score > currentScore){
//                                currentScore = score;
//                                selectNode = child;
//                            }
//                        }

                }
            }
        } else if (state instanceof GPRuleEvolutionStateUpdateImmediate) {
            String mctsPolicy = ((GPRuleEvolutionStateUpdateImmediate) state).mctsPolicy;
            boolean mctsUpdateLate = ((GPRuleEvolutionStateUpdateImmediate) state).mctses[0].isMctsUpdateLate();
            if (mctsUpdateLate) {
                if (mctsPolicy.equals("visitCount")) {
                    //todo: need to finish
                } else if (mctsPolicy.equals("totalReward-div-visitCount-linked") ||
                        mctsPolicy.equals("totalReward-div-visitCount")) {
                    //todo: need to finish
                } else if (mctsPolicy.equals("num-infinity-reward") ||
                        mctsPolicy.equals("mean-non-infinity-reward") ||
                        mctsPolicy.equals("mean-non-infinity-reward-v2") ||
                        mctsPolicy.equals("mean-non-infinity-reward-pro") ||
                        mctsPolicy.equals("mean-non-infinity-reward-linked-v2") ||
                        mctsPolicy.equals("num-infinity-reward-pro") ||
                        mctsPolicy.equals("mean-non-infinity-reward-linked-pro") ||
                        mctsPolicy.equals("mean-non-infinity-reward-linked-pro-v2")) {
                    //todo: need to finish
                } else if (mctsPolicy.equals("hybird")) {//20210518
                    //todo: need to finish
                } else if (mctsPolicy.equals("hybirdv1")) {//20210518
                    //todo: need to finish
                }
            }
        }


        double rnd = state.random[thread].nextDouble(); //probability  (0,1)

        if (rnd > nonterminalProbability + terminalProbability + rootProbability)  // pick anyone  nonterminalProbability = 0.9  terminalProbability=0.1
        // rnd > 0.9+0.1
        //nonterminalProbability + terminalProbability + rootProbability = 1  this will not happen
        {
            if (nodes==-1) nodes=tree.child.numNodes(GPNode.NODESEARCH_ALL); //nodes: the number of node in the tree
            //including the terminals    all the possible positions
            {
                return tree.child.nodeInPosition(state.random[thread].nextInt(nodes), GPNode.NODESEARCH_ALL);
                //randomly choose a node
            }
        }
        else if (rnd > nonterminalProbability + terminalProbability)  // pick the root
        {
            return tree.child; //for example: nonterminalProbability = 0.8 terminalProbability = 0.1, of rnd = 0.9. will choose the root
        }
        else if (rnd > nonterminalProbability)  // pick terminals  //nonterminalProbability = 0.9
        {
            if (terminals==-1) terminals = tree.child.numNodes(GPNode.NODESEARCH_TERMINALS);
            return tree.child.nodeInPosition(state.random[thread].nextInt(terminals), GPNode.NODESEARCH_TERMINALS);
            //choose the terminals
        }
        else  // pick nonterminals if you can
        {
            if (nonterminals==-1) nonterminals = tree.child.numNodes(GPNode.NODESEARCH_NONTERMINALS);
            //the number of non-terminals
            if (nonterminals > 0) // there are some nonterminals
            {
                return tree.child.nodeInPosition(state.random[thread].nextInt(nonterminals), GPNode.NODESEARCH_NONTERMINALS);
                //choose nodes
            }
            else // there ARE no nonterminals!  It must be the root node
            {
                return tree.child;
            }
        }
    }
}
