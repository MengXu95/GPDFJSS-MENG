package mengxu.mcts;

import ec.EvolutionState;
import ec.gp.GPNode;
import ec.gp.GPTree;

import java.util.List;

/**
 * Author: mengxu 2021.04.09
 * The first vision of MCTS based population initialization and mutation
 * Todo：This vision uses two many "for", need to optimize to save time.
 * Todo: The MCTS is just used during initialization and mutation process.
 *       So when I do test, I set the probabily of crossover = 0.0, mutation = 0.95 and reproduction = 0.05
 *       What I have to do next is to add "update the MCTS when do crossover".
 */

public class Mcts {
    private static final double NO_EXPLORATION = 0;
    private double explorationParameter;
    private MctsTreeNode mctsRootNode;
//    private List<MctsTreeNode> mctsTreeNodes;
    private List<GPNode> availTerminalGPNodes;
    private List<GPNode> availFunctionGPNodes;

    private boolean mctsUpdateLate;

    public Mcts(List<GPNode> availTerminalGPNodes, List<GPNode> availFunctionGPNodes, boolean mctsUpdateLate){
        RootNode rootNode = new RootNode();
        this.mctsUpdateLate = mctsUpdateLate;
        this.mctsRootNode = new MctsTreeNode(null, rootNode, -1, 8, availTerminalGPNodes, availFunctionGPNodes, mctsUpdateLate);
        this.availTerminalGPNodes = availTerminalGPNodes;
        this.availFunctionGPNodes = availFunctionGPNodes;
    }

    public MctsTreeNode getMctsRootNode() {
        return mctsRootNode;
    }

    public boolean isMctsUpdateLate() {
        return mctsUpdateLate;
    }

    public void setExplorationForSearch(double explorationParameter) {
        this.explorationParameter = explorationParameter;
    }

    public void frontPropagateVisitCount(GPTree gpTree, double fitness) {
        GPNode gpNode = gpTree.child;
        MctsTreeNode mctsTreeNode = findCorrespondingMctsTreeNode(gpNode);
        if(this.mctsUpdateLate){
            mctsTreeNode.updateVisitCount();
        }
        for(int i=0; i<gpNode.children.length; i++){
            GPNode child = gpNode.children[i];
            mctsTreeNode.frontPropagateVisitCount(child, fitness);
        }
    }

    public void frontPropagateTotalReward(GPTree gpTree, double fitness) {
        GPNode gpNode = gpTree.child;
        MctsTreeNode mctsTreeNode = findCorrespondingMctsTreeNode(gpNode);
        mctsTreeNode.updateTotalReward(fitness);
        if(this.mctsUpdateLate){
            mctsTreeNode.updateVisitCount();
        }
        for(int i=0; i<gpNode.children.length; i++){
            GPNode child = gpNode.children[i];
            mctsTreeNode.frontPropagateTotalReward(child, fitness);
        }
    }

    public void frontPropagateAllReward(int gen, GPTree gpTree, double fitness) {
        GPNode gpNode = gpTree.child;
        MctsTreeNode mctsTreeNode = findCorrespondingMctsTreeNode(gpNode);
        mctsTreeNode.updateAllReward(gen, fitness);
        if(this.mctsUpdateLate){
            mctsTreeNode.updateVisitCount();
        }
        for(int i=0; i<gpNode.children.length; i++){
            GPNode child = gpNode.children[i];
            mctsTreeNode.frontPropagateAllReward(gen, child, fitness);
        }
    }

    public MctsTreeNode findCorrespondingMctsTreeNode(GPNode gpNode){
        for(int i=0; i<this.mctsRootNode.getChildNodes().size(); i++){
            GPNode index = this.mctsRootNode.getChildNodes().get(i).getGpNode();
            if(gpNode.toString().equals(index.toString())){
                return this.mctsRootNode.getChildNodes().get(i);
            }
        }
        if(this.mctsUpdateLate){
            MctsTreeNode child = new MctsTreeNode(this.mctsRootNode, gpNode, 0, 8, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            this.mctsRootNode.getChildNodes().add(child);
            return child;
        }
        System.out.println("Do not find the mctsTreeNode!");
        return null;
    }

}
