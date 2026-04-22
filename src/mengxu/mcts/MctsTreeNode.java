package mengxu.mcts;

import ec.EvolutionState;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.util.Parameter;
import mengxu.algorithm.multipletreegp.GPRuleEvolutionStateHybird;
import yimei.jss.gp.terminal.JobShopAttribute;

import java.util.*;

/**
 * Author: mengxu 2021.04.09
 * The first vision of MCTS based population initialization and mutation
 * Five policies:  1. random policy: first select the untried GPNode, if no untried GPNode, then select based on random.
 *                 2. min visitCount policy: first select the untried GPNode, if no untried GPNode, then select based on min visitCount.
 *                 3. min TRDivVC policy: first select the untried GPNode, if no untried GPNode, then select based on min totalReward div visitCount.
 *                                        if all the GPNode's totalRewardDivVisitCount is Infinity, then select based on min visitCount.
 *                 4. min linked TRDicVC policy: first select based on TRDivVC policy on this depth, if all the GPNode's totalRewardDivVisitCount is Infinity,
 *                                               then select based on TRDivVC policy on next depth, finally if all the linked children's totalRewardDivVisitCount is Infinity,
 *                                               select based on min visitCount.
 *                 5. min NumInfinityReward policy: first select the untried GPNode, if no untried GPNode, then select based on min percentage of number of infinity reward.
 *                 6. min MeanNonInfinityReward policy:
 *                 todo: could consider probablity!!!
 * Todo：This vision uses two many "for", need to optimize to save time.
 * Todo: The MCTS is just used during initialization and mutation process.
 *       So when I do test, I set the probabily of crossover = 0.0, mutation = 0.95 and reproduction = 0.05
 *       What I have to do next is to add "update the MCTS when do crossover".
 */

public class MctsTreeNode {
    private MctsTreeNode parentNode;
    private GPNode gpNode;
    private double totalReward;
    private Map<Integer, List<Double>> allReward;
    private int visitCount;
    private List<MctsTreeNode> childNodes;
    private int currentDepth;
    private int maxDepth;
    private int availableNumChildNodes;
    private List<GPNode> availTerminalGPNodes;
    private List<GPNode> availFunctionGPNodes;
    private List<GPNode> availTerminalAndFunctionGPNodes;
    private MctsTreeNode currentChild;
    private double expandTerminalProb;

    public boolean mctsUpdateLate;
    public final double INFINITY = 1000000000;

    public MctsTreeNode(MctsTreeNode parentNode, GPNode gpNode, int currentDepth, int maxDepth, List<GPNode> availTerminalGPNodes, List<GPNode> availFunctionGPNodes, boolean mctsUpdateLate){
        this.setup(parentNode, gpNode, currentDepth, maxDepth, availTerminalGPNodes, availFunctionGPNodes, mctsUpdateLate);
    }

    public void setup(MctsTreeNode parentNode, GPNode gpNode, int currentDepth, int maxDepth, List<GPNode> availTerminalGPNodes, List<GPNode> availFunctionGPNodes, boolean mctsUpdateLate){
        this.parentNode = parentNode;
        this.gpNode = gpNode;
        this.totalReward = 0;
        this.visitCount = 0;
        this.childNodes = new ArrayList<>();
        this.currentDepth = currentDepth;
        this.maxDepth = maxDepth;
        this.availTerminalGPNodes = availTerminalGPNodes;
        this.availFunctionGPNodes = availFunctionGPNodes;
        this.availTerminalAndFunctionGPNodes = new ArrayList<>();
        this.availTerminalAndFunctionGPNodes.addAll(availTerminalGPNodes);
        this.availTerminalAndFunctionGPNodes.addAll(availFunctionGPNodes);
        this.availableNumChildNodes = availTerminalGPNodes.size() + availFunctionGPNodes.size();
        this.allReward = new HashMap<Integer,List<Double>>();
        this.expandTerminalProb = 0.1;

        this.mctsUpdateLate = mctsUpdateLate;

    }

    public GPNode getGpNode() {
        return gpNode;
    }

    public MctsTreeNode getParentNode() {
        return parentNode;
    }

    public List<MctsTreeNode> getChildNodes() {
        return childNodes;
    }

//    public boolean isFullyExpanded(){
//        if(this.gpNode.expectedChildren() == 0){
//            return true;//terminal has no children.
//        }
//        else{
//            if(this.childNodes.size() == availableNumChildNodes) {
//                for (MctsTreeNode childNode : this.childNodes) {
//                    if (!childNode.isFullyExpanded()) {
//                        return false;
//                    }
//                }
//            }
//        }
//        return true;
//    }

    //2021.07.14, this version does not give terminal and function
    // different probability to be selected at the beginning.
    public GPNode expandNewChildBasedOnMinMeanNonInfinityRewardV2(EvolutionState state, int thread){
        return expandNewTerminalOrFunctionBasedOnMinMeanNonInfinityReward(state, thread);
//        if(state.random[thread].nextDouble() <= this.expandTerminalProb){
//            return expandNewTerminalBasedOnMinMeanNonInfinityReward(state, thread);
//        }
//        else{
//            return expandNewFunctionBasedOnMinMeanNonInfinityReward(state, thread);
//        }
    }

    //2021.06.18, this version does not give terminal and function
    // different probability to be selected at the beginning.
    public GPNode expandNewChildBasedOnMinMeanNonInfinityRewardLinkedV2(EvolutionState state, int thread) {
        return expandNewTerminalOrFunctionBasedOnMinMeanNonInfinityRewardLinked(state, thread);
//        if (state.random[thread].nextDouble() <= this.expandTerminalProb) {
//            return expandNewTerminalBasedOnMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
//        } else {
//            return expandNewFunctionBasedOnMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
//        }
    }

    //2021.06.18, this version does not give terminal and function
    // different probability to be selected at the beginning.
    public GPNode expandNewChildBasedOnMinMeanNonInfinityRewardLinkedWithProbabilityV2(EvolutionState state, int thread) {
        return expandNewTerminalOrFunctionBasedOnMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
//        if (state.random[thread].nextDouble() <= this.expandTerminalProb) {
//            return expandNewTerminalBasedOnMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
//        } else {
//            return expandNewFunctionBasedOnMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
//        }
    }

    //2021.05.25
    public GPNode expandNewChildBasedOnMinMeanNonInfinityRewardLinkedWithProbability(EvolutionState state, int thread) {
        if (state.random[thread].nextDouble() <= this.expandTerminalProb) {
            return expandNewTerminalBasedOnMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
        } else {
            return expandNewFunctionBasedOnMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
        }
    }

    //2021.05.21
    public GPNode expandNewChildBasedOnMinMeanNonInfinityRewardWithProbability(EvolutionState state, int thread) {
        if (state.random[thread].nextDouble() <= this.expandTerminalProb) {
            return expandNewTerminalBasedOnMinMeanNonInfinityRewardWithProbability(state, thread);
        } else {
            return expandNewFunctionBasedOnMinMeanNonInfinityRewardWithProbability(state, thread);
        }
    }

    //2021.05.18
    public GPNode expandNewChildHybird(EvolutionState state, int thread) {
        if(state instanceof GPRuleEvolutionStateHybird){
            double proExpendNewChildBasedOnTRandVC = ((GPRuleEvolutionStateHybird)state).getProHybird();
            if(state.random[thread].nextDouble() <= proExpendNewChildBasedOnTRandVC){
                return expandNewChildBasedOnTRandVCV1(state, thread);
            }
            else{
                return expandNewChildRandom(state, thread);
            }
        }
        else{
            System.out.println("Error!!! The state should be GPRuleEvolutionStateHybird!");
            return null;
        }
    }

    public GPNode expandNewTerminalHybird(EvolutionState state, int thread){
        if(state instanceof GPRuleEvolutionStateHybird){
            double proExpendNewChildBasedOnTRandVC = ((GPRuleEvolutionStateHybird)state).getProHybird();
            if(state.random[thread].nextDouble() <= proExpendNewChildBasedOnTRandVC){
                return expandNewTerminalBasedOnTRandVCV1(state, thread);
            }
            else{
                return expandNewTerminalRandom(state, thread);
            }
        }
        else{
            System.out.println("Error!!! The state should be GPRuleEvolutionStateHybird!");
            return null;
        }
    }

    //2021.05.18
    public GPNode expandNewChildHybirdV1(EvolutionState state, int thread) {
        if(state instanceof GPRuleEvolutionStateHybird){
            double proExpendNewChildBasedOnTRandVC = ((GPRuleEvolutionStateHybird)state).getProHybird();
            if(state.random[thread].nextDouble() <= proExpendNewChildBasedOnTRandVC){
                return expandNewChildBasedOnMinMeanNonInfinityReward(state, thread);
            }
            else{
                return expandNewChildRandom(state, thread);
            }
        }
        else{
            System.out.println("Error!!! The state should be GPRuleEvolutionStateHybird!");
            return null;
        }
    }

    public GPNode expandNewTerminalHybirdV1(EvolutionState state, int thread){
        if(state instanceof GPRuleEvolutionStateHybird){
            double proExpendNewChildBasedOnTRandVC = ((GPRuleEvolutionStateHybird)state).getProHybird();
            if(state.random[thread].nextDouble() <= proExpendNewChildBasedOnTRandVC){
                return expandNewTerminalBasedOnMinMeanNonInfinityReward(state, thread);
            }
            else{
                return expandNewTerminalRandom(state, thread);
            }
        }
        else{
            System.out.println("Error!!! The state should be GPRuleEvolutionStateHybird!");
            return null;
        }
    }

    public GPNode expandNewChildRandom(EvolutionState state, int thread){
//        if(this.currentDepth < 2){ //the min-Depth = 2
//            return expandNewFunctionRandom(state, thread);
//        }
//        else{
            if(state.random[thread].nextDouble() <= this.expandTerminalProb){
                return expandNewTerminalRandom(state, thread);
            }
            else{
                return expandNewFunctionRandom(state, thread);
            }

//            //------------original------------------
//            if(getUntriedTerminalGPNodes().size()==0 && getUntriedFunctionGPNodes().size()!=0){
//                return expandNewFunctionRandom(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()==0){
//                return expandNewTerminalRandom(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()!=0){
//                if(state.random[thread].nextDouble() <= this.expandTerminalProb){
//                    return expandNewTerminalRandom(state, thread);
//                }
//                else{
//                    return expandNewFunctionRandom(state, thread);
//                }
//            }
//            else{
//                return expandNewFunctionRandom(state, thread);
//            }
//            //---------------original------------------
//        }
    }

    public GPNode expandNewChildBasedOnMaxVisitCount(EvolutionState state, int thread) {
        if (state.random[thread].nextDouble() <= this.expandTerminalProb) {
            return expandNewTerminalBasedOnMaxVisitCount(state, thread);
        } else {
            return expandNewFunctionBasedOnMaxVisitCount(state, thread);
        }
    }

    public GPNode expandNewChildBasedOnVisitCount(EvolutionState state, int thread){
//        if(this.currentDepth < 2){ //the min-Depth = 2
//            return expandNewFunctionBasedOnVisitCount(state, thread);
//        }
//        else{
            if(state.random[thread].nextDouble() <= this.expandTerminalProb){
                return expandNewTerminalBasedOnVisitCount(state, thread);
            }
            else{
                return expandNewFunctionBasedOnVisitCount(state, thread);
            }

//            //------------original------------------
//            if(getUntriedTerminalGPNodes().size()==0 && getUntriedFunctionGPNodes().size()!=0){
//                return expandNewFunctionBasedOnVisitCount(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()==0){
//                return expandNewTerminalBasedOnVisitCount(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()!=0){
//                if(state.random[thread].nextDouble() <= this.expandTerminalProb){
//                    return expandNewTerminalBasedOnVisitCount(state, thread);
//                }
//                else{
//                    return expandNewFunctionBasedOnVisitCount(state, thread);
//                }
//            }
//            else{ //todo: this makes the probabily to select function higher which is needed to modeify.
//                return expandNewFunctionBasedOnVisitCount(state, thread);
//            }
//            //------------original------------------
//        }
    }

    public GPNode expandNewChildBasedOnMinMeanNonInfinityReward(EvolutionState state, int thread){
//        if(this.currentDepth < 2){ //the min-Depth = 2
//            return expandNewFunctionBasedOnMinMeanNonInfinityReward(state, thread);
//        }
//        else{
            if(state.random[thread].nextDouble() <= this.expandTerminalProb){
                return expandNewTerminalBasedOnMinMeanNonInfinityReward(state, thread);
            }
            else{
                return expandNewFunctionBasedOnMinMeanNonInfinityReward(state, thread);
            }

//            //------------original------------------
//            if(getUntriedTerminalGPNodes().size()==0 && getUntriedFunctionGPNodes().size()!=0){
//                return expandNewFunctionBasedOnMinMeanNonInfinityReward(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()==0){
//                return expandNewTerminalBasedOnMinMeanNonInfinityReward(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()!=0){
//                if(state.random[thread].nextDouble() <= this.expandTerminalProb){
//                    return expandNewTerminalBasedOnMinMeanNonInfinityReward(state, thread);
//                }
//                else{
//                    return expandNewFunctionBasedOnMinMeanNonInfinityReward(state, thread);
//                }
//            }
//            else{
//                return expandNewFunctionBasedOnMinMeanNonInfinityReward(state, thread);
//            }
//            //------------original------------------
//        }
    }



    //2021.05.25
    public GPNode expandNewChildBasedOnNumInfinityRewardWithProbability(EvolutionState state, int thread) {
        if (state.random[thread].nextDouble() <= this.expandTerminalProb) {
            return expandNewTerminalBasedOnNumInfinityRewardWithProbability(state, thread);
        } else {
            return expandNewFunctionBasedOnNumInfinityRewardWithProbability(state, thread);
        }
    }

    public GPNode expandNewChildBasedOnNumInfinityReward(EvolutionState state, int thread){
//        if(this.currentDepth < 2){ //the min-Depth = 2
//            return expandNewFunctionBasedOnNumInfinityReward(state, thread);
//        }
//        else{
            if(state.random[thread].nextDouble() <= this.expandTerminalProb){
                return expandNewTerminalBasedOnNumInfinityReward(state, thread);
            }
            else{
                return expandNewFunctionBasedOnNumInfinityReward(state, thread);
            }

//            //------------original------------------
//            if(getUntriedTerminalGPNodes().size()==0 && getUntriedFunctionGPNodes().size()!=0){
//                return expandNewFunctionBasedOnNumInfinityReward(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()==0){
//                return expandNewTerminalBasedOnNumInfinityReward(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()!=0){
//                if(state.random[thread].nextDouble() <= this.expandTerminalProb){
//                    return expandNewTerminalBasedOnNumInfinityReward(state, thread);
//                }
//                else{
//                    return expandNewFunctionBasedOnNumInfinityReward(state, thread);
//                }
//            }
//            else{
//                return expandNewFunctionBasedOnNumInfinityReward(state, thread);
//            }
//            //------------original------------------
//        }
    }

    public GPNode expandNewChildBasedOnTRandVCV1(EvolutionState state, int thread){
//        if(this.currentDepth < 2){ //the min-Depth = 2
//            return expandNewFunctionBasedOnTRandVCV1(state, thread);
//        }
//        else{
            if(state.random[thread].nextDouble() <= this.expandTerminalProb){
                return expandNewTerminalBasedOnTRandVCV1(state, thread);
            }
            else{
                return expandNewFunctionBasedOnTRandVCV1(state, thread);
            }

//            //------------original------------------
//            if(getUntriedTerminalGPNodes().size()==0 && getUntriedFunctionGPNodes().size()!=0){
//                return expandNewFunctionBasedOnTRandVCV1(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()==0){
//                return expandNewTerminalBasedOnTRandVCV1(state, thread);
//            }
//            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()!=0){
//                if(state.random[thread].nextDouble() <= this.expandTerminalProb){
//                    return expandNewTerminalBasedOnTRandVCV1(state, thread);
//                }
//                else{
//                    return expandNewFunctionBasedOnTRandVCV1(state, thread);
//                }
//            }
//            else{
//                return expandNewFunctionBasedOnTRandVCV1(state, thread);
//            }
//            //------------original------------------
//        }
    }

    //todo: change policy in KozaBuilder
    public GPNode expandNewChildBasedOnTRandVC(EvolutionState state, int thread){
        if(state.random[thread].nextDouble() <= this.expandTerminalProb){
            return expandNewTerminalBasedOnTRandVC(state, thread);
        }
        else{
            return expandNewFunctionBasedOnTRandVC(state, thread);
        }
//        if(this.currentDepth < 2){ //the min-Depth = 2
//            return expandNewFunctionBasedOnTRandVC(state, thread);
//        }
//        else{
//            if(state.random[thread].nextDouble() <= this.expandTerminalProb){
//                return expandNewTerminalBasedOnTRandVC(state, thread);
//            }
//            else{
//                return expandNewFunctionBasedOnTRandVC(state, thread);
//            }
//
////            //------------original------------------
////            //todo: need to check if this node has been explored fully to avoid always select terminal.
////            if(getUntriedTerminalGPNodes().size()==0 && getUntriedFunctionGPNodes().size()!=0){
////                return expandNewFunctionBasedOnTRandVC(state, thread);
////            }
////            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()==0){
////                return expandNewTerminalBasedOnTRandVC(state, thread);
////            }
////            else if(getUntriedTerminalGPNodes().size()!=0 && getUntriedFunctionGPNodes().size()!=0){
////                if(state.random[thread].nextDouble() <= this.expandTerminalProb){
////                    return expandNewTerminalBasedOnTRandVC(state, thread);
////                }
////                else{
////                    return expandNewFunctionBasedOnTRandVC(state, thread);
////                }
////            }
////            else{
////                return expandNewFunctionBasedOnTRandVC(state, thread);
////            }
////            //------------original------------------
//        }
        //todo: select terminal or select function based on
        // if there are untried GP nodes of each kind.
        // not based on the random probability which is used now
    }

    //2021.06.18
    public GPNode expandNewTerminalOrFunctionRandom(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        untriedGPNodes.addAll(getUntriedFunctionGPNodes());

        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            int index = state.random[thread].nextInt(this.availTerminalAndFunctionGPNodes.size());
            gpNode = this.availTerminalAndFunctionGPNodes.get(index);
            MctsTreeNode child = findChildBasedOnGPNode(gpNode);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewTerminalRandom(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            int index = state.random[thread].nextInt(this.availTerminalGPNodes.size());
            gpNode = this.availTerminalGPNodes.get(index);
            MctsTreeNode child = findChildBasedOnGPNode(gpNode);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }



    public GPNode expandNewFunctionBasedOnTRandVCV1(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildFunctionWithMinTotalrewardDivVisitCountV1() == null){
                if(findChildFunctionWithMinVisitcount() == null){
                    return expandNewFunctionRandom(state, thread);
                }
                else{
                    return expandNewFunctionBasedOnVisitCount(state, thread);
                }
            }
            MctsTreeNode child = findChildFunctionWithMinTotalrewardDivVisitCountV1();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{//todo:wrong for mctsUpdateLate 20210526 need modify!!!
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    //expand new terminal based ont totalReward and visitCount
    public GPNode expandNewTerminalBasedOnTRandVCV1(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildTerminalWithMinTotalrewardDivVisitCountV1() == null){
                if(findChildTerminalWithMinVisitcount() == null){
                    return expandNewTerminalRandom(state, thread);
                }
                else{
                    return expandNewTerminalBasedOnVisitCount(state, thread);
                }
            }
            MctsTreeNode child = findChildTerminalWithMinTotalrewardDivVisitCountV1();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            if(gpNode.expectedChildren()!=0){
                System.out.println("Error: return function, expect terminal!");
            }
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewTerminalBasedOnTRandVC(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildTerminalWithMinTotalrewardDivVisitCount() == null){
                if(findChildTerminalWithMinVisitcount() == null){
                    return expandNewTerminalRandom(state, thread);
                }
                else{
                    return expandNewTerminalBasedOnVisitCount(state, thread);
                }
            }
            MctsTreeNode child = findChildTerminalWithMinTotalrewardDivVisitCount();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            if(gpNode.expectedChildren()!=0){
                System.out.println("Error: return function, expect terminal!");
            }
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewFunctionBasedOnTRandVC(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildFunctionWithMinTotalrewardDivVisitCount() == null){
                if(findChildFunctionWithMinVisitcount() == null){
                    return expandNewFunctionRandom(state, thread);
                }
                else{
                    return expandNewFunctionBasedOnVisitCount(state, thread);
                }
            }
            MctsTreeNode child = findChildFunctionWithMinTotalrewardDivVisitCount();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    /** expand new terminal based on visitcount, the smaller the visitcount, the higher the priority. **/
    public GPNode expandNewTerminalBasedOnVisitCount(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildTerminalWithMinVisitcount() == null){
                return expandNewTerminalRandom(state, thread);
            }
            MctsTreeNode child = findChildTerminalWithMinVisitcount();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewFunctionBasedOnMaxVisitCount(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildFunctionWithMaxVisitcount() == null){
                return expandNewFunctionRandom(state, thread);
            }
            MctsTreeNode child = findChildFunctionWithMaxVisitcount();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewTerminalBasedOnMaxVisitCount(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildTerminalWithMaxVisitcount() == null){
                return expandNewTerminalRandom(state, thread);
            }
            MctsTreeNode child = findChildTerminalWithMaxVisitcount();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewFunctionBasedOnVisitCount(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildFunctionWithMinVisitcount() == null){
                return expandNewFunctionRandom(state, thread);
            }
            MctsTreeNode child = findChildFunctionWithMinVisitcount();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewTerminalBasedOnNumInfinityReward(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildTerminalWithMinNumInfinityReward() == null){
                return expandNewTerminalRandom(state, thread);
            }
            MctsTreeNode child = findChildTerminalWithMinNumInfinityReward();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewFunctionBasedOnNumInfinityReward(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildFunctionWithMinNumInfinityReward() == null){
                return expandNewFunctionRandom(state, thread);
            }
            MctsTreeNode child = findChildFunctionWithMinNumInfinityReward();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    //2021.05.24
    public GPNode expandNewTerminalBasedOnNumInfinityRewardWithProbability(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            MctsTreeNode child = findChildTerminalWithMinNumInfinityRewardWithProbability(state, thread);
            if(child == null){
                return expandNewTerminalRandom(state, thread);
            }
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewFunctionBasedOnNumInfinityRewardWithProbability(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){

            MctsTreeNode child = findChildFunctionWithMinNumInfinityRewardWithProbability(state, thread);
            if(child == null){
                return expandNewFunctionRandom(state, thread);
            }

            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    //2021.05.21
    public GPNode expandNewTerminalBasedOnMinMeanNonInfinityReward(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildTerminalWithMinMeanNonInfinityReward() == null){
                return expandNewTerminalRandom(state, thread);
            }
            MctsTreeNode child = findChildTerminalWithMinMeanNonInfinityReward();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    //2021.07.14
    public GPNode expandNewTerminalOrFunctionBasedOnMinMeanNonInfinityReward(EvolutionState state, int thread){
        //First, mix all terminals and functions
        List<GPNode> untriedGPNodes = getUntriedTerminalAndFunctionGPNodes();
//        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
//            double p = state.random[thread].nextDouble();
            MctsTreeNode child = findChildTerminalOrFunctionWithMinMeanNonInfinityRewardLinked(state, thread);
            if(child == null){
                System.out.println("Wrong! Do not find ChildTerminalOrFunctionWithMinMeanNonInfinityRewardLinked!");
                return expandNewTerminalOrFunctionRandom(state, thread);
            }

            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    //2021.06.18
    public GPNode expandNewTerminalOrFunctionBasedOnMinMeanNonInfinityRewardLinked(EvolutionState state, int thread){
        //First, mix all terminals and functions
        List<GPNode> untriedGPNodes = getUntriedTerminalAndFunctionGPNodes();
//        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
//            double p = state.random[thread].nextDouble();
            MctsTreeNode child = findChildTerminalOrFunctionWithMinMeanNonInfinityRewardLinked(state, thread);
            if(child == null){
                return expandNewTerminalOrFunctionRandom(state, thread);
            }

            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    //2021.06.18
    public GPNode expandNewTerminalOrFunctionBasedOnMinMeanNonInfinityRewardLinkedWithProbability(EvolutionState state, int thread){
        //First, mix all terminals and functions
        List<GPNode> untriedGPNodes = getUntriedTerminalAndFunctionGPNodes();
//        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
//            double p = state.random[thread].nextDouble();
            MctsTreeNode child = findChildTerminalOrFunctionWithMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
            if(child == null){
                return expandNewTerminalOrFunctionRandom(state, thread);
            }

            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    //2021.05.25
    public GPNode expandNewFunctionBasedOnMinMeanNonInfinityRewardLinkedWithProbability(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
//            double p = state.random[thread].nextDouble();
            MctsTreeNode child = findChildFunctionWithMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
            if(child == null){
                return expandNewFunctionRandom(state, thread);
            }
//            MctsTreeNode child = findChildFunctionWithMinMeanNonInfinityRewardWithProbability(state, thread);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewTerminalBasedOnMinMeanNonInfinityRewardLinked(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
//            double p = state.random[thread].nextDouble();
            MctsTreeNode child = findChildTerminalWithMinMeanNonInfinityRewardLinked(state, thread);
            if(child == null){
                return expandNewTerminalRandom(state, thread);
            }

            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    //2021.05.25
    public GPNode expandNewTerminalBasedOnMinMeanNonInfinityRewardLinkedWithProbability(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
//            double p = state.random[thread].nextDouble();
            MctsTreeNode child = findChildTerminalWithMinMeanNonInfinityRewardLinkedWithProbability(state, thread);
            if(child == null){
                return expandNewTerminalRandom(state, thread);
            }

            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewFunctionBasedOnMinMeanNonInfinityRewardWithProbability(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
//            double p = state.random[thread].nextDouble();
            MctsTreeNode child = findChildFunctionWithMinMeanNonInfinityRewardWithProbability(state, thread);
            if(child == null){
                return expandNewFunctionRandom(state, thread);
            }
//            MctsTreeNode child = findChildFunctionWithMinMeanNonInfinityRewardWithProbability(state, thread);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }


    public GPNode expandNewTerminalBasedOnMinMeanNonInfinityRewardWithProbability(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedTerminalGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
//            double p = state.random[thread].nextDouble();
            MctsTreeNode child = findChildTerminalWithMinMeanNonInfinityRewardWithProbability(state, thread);
            if(child == null){
                return expandNewTerminalRandom(state, thread);
            }

            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewFunctionBasedOnMinMeanNonInfinityReward(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            if(findChildFunctionWithMinMeanNonInfinityReward() == null){
                return expandNewFunctionRandom(state, thread);
            }
            MctsTreeNode child = findChildFunctionWithMinMeanNonInfinityReward();
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            gpNode = child.gpNode;
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }

        return gpNode.lightCloneMcts();
    }

    public GPNode expandNewFunctionRandom(EvolutionState state, int thread){
        List<GPNode> untriedGPNodes = getUntriedFunctionGPNodes();
        GPNode gpNode;
        if(untriedGPNodes.size()==0){
            int index = state.random[thread].nextInt(this.availFunctionGPNodes.size());
            gpNode = this.availFunctionGPNodes.get(index);
            MctsTreeNode child = findChildBasedOnGPNode(gpNode);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }
        else{
            int index = state.random[thread].nextInt(untriedGPNodes.size());
            gpNode = untriedGPNodes.get(index);
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            if(!this.mctsUpdateLate){
                child.updateVisitCount();
            }
            this.currentChild = child;
        }
        return gpNode.lightCloneMcts();
    }

    public MctsTreeNode getCurrentChild() {
        return this.currentChild;
    }

    public MctsTreeNode findChildBasedOnGPNode(GPNode gpNode){
        for(int i=0; i<this.childNodes.size(); i++){
            if(this.childNodes.get(i).gpNode.equals(gpNode)){
                return this.childNodes.get(i);
            }
        }
        return null;
    }

    public MctsTreeNode findChildFunctionWithMinVisitcount(){
        int index = 0;
        int minVisitcount = (int)INFINITY;
        boolean firstIndexTerminal = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()){//check if this node is fully expand
                if(this.childNodes.get(i).gpNode.expectedChildren() == 2){
                    if(this.childNodes.get(i).visitCount < minVisitcount){
                        index = i;
                        minVisitcount = this.childNodes.get(i).visitCount;
                        firstIndexTerminal = true;
                    }
                }
//            }
        }
        if(firstIndexTerminal){
            return this.childNodes.get(index);
        }
        return null;
    }

    public MctsTreeNode findChildTerminalWithMinVisitcount(){
        int index = 0;
        int minVisitcount = (int)INFINITY;
        boolean firstIndexTerminal = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
                if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                    if (this.childNodes.get(i).visitCount < minVisitcount) {
                        index = i;
                        minVisitcount = this.childNodes.get(i).visitCount;
                        firstIndexTerminal = true;
                    }
                }
//            }
        }
        if(firstIndexTerminal){
            return this.childNodes.get(index);
        }
        return null;
    }

    public MctsTreeNode findChildFunctionWithMaxVisitcount(){
        int index = 0;
        int maxVisitcount = -1;
        boolean firstIndexTerminal = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()){//check if this node is fully expand
            if(this.childNodes.get(i).gpNode.expectedChildren() == 2){
                if(this.childNodes.get(i).visitCount > maxVisitcount){
                    index = i;
                    maxVisitcount = this.childNodes.get(i).visitCount;
                    firstIndexTerminal = true;
                }
            }
//            }
        }
        if(firstIndexTerminal){
            return this.childNodes.get(index);
        }
        return null;
    }

    public MctsTreeNode findChildTerminalWithMaxVisitcount(){
        int index = 0;
        int maxVisitcount = -1;
        boolean firstIndexTerminal = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
            if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                if (this.childNodes.get(i).visitCount > maxVisitcount) {
                    index = i;
                    maxVisitcount = this.childNodes.get(i).visitCount;
                    firstIndexTerminal = true;
                }
            }
//            }
        }
        if(firstIndexTerminal){
            return this.childNodes.get(index);
        }
        return null;
    }

    public MctsTreeNode findChildTerminalWithMinTotalrewardDivVisitCount(){
        int index = 0;
        double minTotalrewardDivVisitCount = INFINITY;
        boolean firstIndexTerminal = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
                if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                    if (this.childNodes.get(i).getTotalRewardDivVisitCount() < minTotalrewardDivVisitCount) {
                        index = i;
                        minTotalrewardDivVisitCount = this.childNodes.get(i).getTotalRewardDivVisitCount();
                        firstIndexTerminal = true;
                    }
                }
//            }
        }
        if(firstIndexTerminal){
            return this.childNodes.get(index);
        }
        return null;
        //check if this will return function, if not find function, just return null.
    }

    public MctsTreeNode findChildFunctionWithMinTotalrewardDivVisitCount(){
        int index = 0;
        double minTotalrewardDivVisitCount = INFINITY;
        boolean firstIndexFunction = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
                if (this.childNodes.get(i).gpNode.expectedChildren() == 2) {
                    if (this.childNodes.get(i).getTotalRewardDivVisitCount() < minTotalrewardDivVisitCount) {
                        index = i;
                        minTotalrewardDivVisitCount = this.childNodes.get(i).getTotalRewardDivVisitCount();
                        firstIndexFunction = true;
                    }
//                }
            }
        }
        if(firstIndexFunction){
            return this.childNodes.get(index);
        }
        return null;
    }

    //todo: need to verify if this is right.
    public MctsTreeNode findChildFunctionWithMinTotalrewardDivVisitCountV1(){
        int index = 0;
        double minTotalrewardDivVisitCount = INFINITY;
        boolean firstIndexFunction = false;
        List<Integer> functionIndex = new ArrayList<>();//save all the function index which is not fully expand
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
                if (this.childNodes.get(i).gpNode.expectedChildren() == 2) {
                    functionIndex.add(i);
                    if (this.childNodes.get(i).getTotalRewardDivVisitCount() < minTotalrewardDivVisitCount) {
                        index = i;
                        minTotalrewardDivVisitCount = this.childNodes.get(i).getTotalRewardDivVisitCount();
                        firstIndexFunction = true;
                    }
                }
//            }
        }
        if(firstIndexFunction){//if there is at least one function that the TRDivVC is not infinity.
            return this.childNodes.get(index);
        }
        else{
            double secondMinTotalrewardDivVisitCount = INFINITY;
            int secondIndex = 0;
            boolean secondFirstIndexFunction = false;
            for(int i=0; i<functionIndex.size();i++){
                int ref = functionIndex.get(i);
                int childrenSize = this.childNodes.get(ref).childNodes.size();
                for(int j=0; j<childrenSize; j++){
                    MctsTreeNode indexMctsNode = this.childNodes.get(ref).childNodes.get(j);
                    if(indexMctsNode.findChildFunctionWithMinTotalrewardDivVisitCountV1()!=null){
                        if(indexMctsNode.findChildFunctionWithMinTotalrewardDivVisitCountV1().getTotalRewardDivVisitCount() < secondMinTotalrewardDivVisitCount){
                            secondIndex = ref;
                            secondMinTotalrewardDivVisitCount = indexMctsNode.findChildFunctionWithMinTotalrewardDivVisitCountV1().getTotalRewardDivVisitCount();
                            secondFirstIndexFunction = true;
                        }
                    }
                    //todo: need to modify this!!!
                }
            }
            if(secondFirstIndexFunction){
                return this.childNodes.get(secondIndex);
            }
            else{
                return null;
            }
        }
    }

    //todo: need to verify if this is right.
    public MctsTreeNode findChildTerminalWithMinTotalrewardDivVisitCountV1(){
        int index = 0;
        double minTotalrewardDivVisitCount = INFINITY;
        boolean firstIndexTerminal = false;
        List<Integer> terminalIndex = new ArrayList<>();
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
                if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                    terminalIndex.add(i);
                    if (this.childNodes.get(i).getTotalRewardDivVisitCount() < minTotalrewardDivVisitCount) {
                        index = i;
                        minTotalrewardDivVisitCount = this.childNodes.get(i).getTotalRewardDivVisitCount();
                        firstIndexTerminal = true;
                    }
                }
//            }
        }
        if(firstIndexTerminal){//if there is at least one function that the TRDivVC is not infinity.
            return this.childNodes.get(index);
        }
        else{
            double secondMinTotalrewardDivVisitCount = INFINITY;
            int secondIndex = 0;
            boolean secondFirstIndexTerminal = false;
            for(int i=0; i<terminalIndex.size();i++){
                int ref = terminalIndex.get(i);
                int childrenSize = this.childNodes.get(ref).childNodes.size();
                for(int j=0; j<childrenSize; j++){
                    MctsTreeNode indexMctsNode = this.childNodes.get(ref).childNodes.get(j);
                    if(indexMctsNode.findChildTerminalWithMinTotalrewardDivVisitCountV1()!=null){
                        if(indexMctsNode.findChildTerminalWithMinTotalrewardDivVisitCountV1().getTotalRewardDivVisitCount() < secondMinTotalrewardDivVisitCount){
                            secondIndex = ref;
                            secondMinTotalrewardDivVisitCount = indexMctsNode.findChildTerminalWithMinTotalrewardDivVisitCountV1().getTotalRewardDivVisitCount();
                            secondFirstIndexTerminal = true;
                        }
                    }
                    //todo: need to modify this!!!
                }
            }
            if(secondFirstIndexTerminal){
                return this.childNodes.get(secondIndex);
            }
            else{
                return null;
            }
        }
    }

    public MctsTreeNode findChildFunctionWithMinNumInfinityReward(){
        int index = 0;
        double minNumInfinity = INFINITY;
        boolean firstIndexFunction = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
                if (this.childNodes.get(i).gpNode.expectedChildren() == 2) {
                    double percent = (double) this.childNodes.get(i).getNumInfinityReward() / this.childNodes.get(i).visitCount;
                    if (percent < minNumInfinity) {
                        index = i;
                        minNumInfinity = percent;
                        firstIndexFunction = true;
                    }
                }
//            }
        }
        if(firstIndexFunction){
            return this.childNodes.get(index);
        }
        return null;
    }

    public MctsTreeNode findChildTerminalWithMinNumInfinityReward(){
        int index = 0;
        double minNumInfinity = INFINITY;
        boolean firstIndexTerminal = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
                if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                    double percent = (double) this.childNodes.get(i).getNumInfinityReward() / this.childNodes.get(i).visitCount;
                    if (percent < minNumInfinity) {
                        index = i;
                        minNumInfinity = percent;
                        firstIndexTerminal = true;
                    }
                }
//            }
        }
        if(firstIndexTerminal){
            return this.childNodes.get(index);
        }
        return null;
    }

    //2021.05.25
    public MctsTreeNode findChildFunctionWithMinNumInfinityRewardWithProbability(EvolutionState state, int thread){
        List<Integer> allIndex = new ArrayList<>();
        List<Double> allNumInfinityReward = new ArrayList<>();

        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
            if (this.childNodes.get(i).gpNode.expectedChildren() == 2) {
                allIndex.add(i);
                double percent = (double) this.childNodes.get(i).getNumInfinityReward() / this.childNodes.get(i).visitCount;
                allNumInfinityReward.add(percent);
            }
//            }
        }
        if(allNumInfinityReward.size()==0){
            return null;
        }
        else if(allNumInfinityReward.size()==1){
            return this.childNodes.get(allIndex.get(0));
        }
        while(true){
            if(allNumInfinityReward.size()==1){
                return this.childNodes.get(allIndex.get(0));
            }
            else{
                int index = findMinIndex(allNumInfinityReward);
                if(state.random[thread].nextBoolean()){
                    return this.childNodes.get(allIndex.get(index));
                }
                else{
                    allNumInfinityReward.remove(index);
                    allIndex.remove(index);
                }
            }
        }
    }

    public MctsTreeNode findChildTerminalWithMinNumInfinityRewardWithProbability(EvolutionState state, int thread){
        List<Integer> allIndex = new ArrayList<>();
        List<Double> allNumInfinityReward = new ArrayList<>();

        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
            if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                allIndex.add(i);
                double percent = (double) this.childNodes.get(i).getNumInfinityReward() / this.childNodes.get(i).visitCount;
                allNumInfinityReward.add(percent);
            }
//            }
        }
        if(allNumInfinityReward.size()==0){
            return null;
        }
        else if(allNumInfinityReward.size()==1){
            return this.childNodes.get(allIndex.get(0));
        }
        while(true){
            if(allNumInfinityReward.size()==1){
                return this.childNodes.get(allIndex.get(0));
            }
            else{
                int index = findMinIndex(allNumInfinityReward);
                if(state.random[thread].nextBoolean()){
                    return this.childNodes.get(allIndex.get(index));
                }
                else{
                    allNumInfinityReward.remove(index);
                    allIndex.remove(index);
                }
            }
        }
    }

    //2021.05.25
    public MctsTreeNode findChildFunctionWithMinMeanNonInfinityRewardLinkedWithProbability(EvolutionState state, int thread){
        List<Integer> allIndex = new ArrayList<>();
        List<Double> allMeanNonInfinityReward = new ArrayList<>();

        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
            if (this.childNodes.get(i).gpNode.expectedChildren() == 2) {
                allIndex.add(i);
                double percent = (double) this.childNodes.get(i).getMeanNonInfinityRewardLinked();
                allMeanNonInfinityReward.add(percent);
            }
//            }
        }
        if(allMeanNonInfinityReward.size()==0){
            System.out.println("error!!!");
            return null;
        }
        else if(allMeanNonInfinityReward.size()==1){
            return this.childNodes.get(allIndex.get(0));
        }
        while(true){
            if(allMeanNonInfinityReward.size()==1){
                return this.childNodes.get(allIndex.get(0));
            }
            else{
                int index = findMinIndex(allMeanNonInfinityReward);
                if(state.random[thread].nextBoolean()){
                    return this.childNodes.get(allIndex.get(index));
                }
                else{
                    allMeanNonInfinityReward.remove(index);
                    allIndex.remove(index);
                }
            }
        }
    }

    //2021.07.14
    public MctsTreeNode findChildTerminalOrFunctionWithMinMeanNonInfinityReward(EvolutionState state, int thread){
        int index = 0;
        double minNumInfinity = INFINITY;
        boolean firstIndexFunction = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
//            if (this.childNodes.get(i).gpNode.expectedChildren() == 2) {
                double percent = (double) this.childNodes.get(i).getMeanNonInfinityReward();
                if (percent < minNumInfinity) {
                    index = i;
                    minNumInfinity = percent;
                    firstIndexFunction = true;
                }
//            }
//            }
        }
        if(firstIndexFunction){
            return this.childNodes.get(index);
        }
        return null;
    }

    //2021.06.18
    public MctsTreeNode findChildTerminalOrFunctionWithMinMeanNonInfinityRewardLinked(EvolutionState state, int thread){
        List<Integer> allIndex = new ArrayList<>();
        List<Double> allMeanNonInfinityReward = new ArrayList<>();
        double sumMeanNonInfinityReward = 0;

        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
//            if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
            allIndex.add(i);
            double percent = (double) this.childNodes.get(i).getMeanNonInfinityReward();
            allMeanNonInfinityReward.add(percent);
            sumMeanNonInfinityReward +=percent;
//            }
//            }
        }
        if(allMeanNonInfinityReward.size()==0){
            return null;
        }
        else if(allMeanNonInfinityReward.size()==1){
            return this.childNodes.get(allIndex.get(0));
        }
//        while(true){
        int index = findMinIndex(allMeanNonInfinityReward);
        return this.childNodes.get(allIndex.get(index));
        //        }
//        System.out.println("Error!");
//        return null;
    }

    //2021.06.18
    public MctsTreeNode findChildTerminalOrFunctionWithMinMeanNonInfinityRewardLinkedWithProbability(EvolutionState state, int thread){
        List<Integer> allIndex = new ArrayList<>();
        List<Double> allMeanNonInfinityReward = new ArrayList<>();
        double sumMeanNonInfinityReward = 0;

        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
//            if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                allIndex.add(i);
                double percent = (double) this.childNodes.get(i).getMeanNonInfinityRewardLinked();
                allMeanNonInfinityReward.add(percent);
                sumMeanNonInfinityReward +=percent;
//            }
//            }
        }
        if(allMeanNonInfinityReward.size()==0){
            return null;
        }
        else if(allMeanNonInfinityReward.size()==1){
            return this.childNodes.get(allIndex.get(0));
        }
        while(true){
            if(allMeanNonInfinityReward.size()==1){
                return this.childNodes.get(allIndex.get(0));
            }
            else{
                int index = findMinIndex(allMeanNonInfinityReward);
                if(state.random[thread].nextBoolean()){
                    return this.childNodes.get(allIndex.get(index));
                }
                else{
                    allMeanNonInfinityReward.remove(index);
                    allIndex.remove(index);
                }
            }
        }
//        System.out.println("Error!");
//        return null;
    }

    //2021.05.25
    public MctsTreeNode findChildTerminalWithMinMeanNonInfinityRewardLinked(EvolutionState state, int thread){
        List<Integer> allIndex = new ArrayList<>();
        List<Double> allMeanNonInfinityReward = new ArrayList<>();
        double sumMeanNonInfinityReward = 0;

        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
            if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                allIndex.add(i);
                double percent = (double) this.childNodes.get(i).getMeanNonInfinityRewardLinked();
                allMeanNonInfinityReward.add(percent);
                sumMeanNonInfinityReward +=percent;
            }
//            }
        }
        if(allMeanNonInfinityReward.size()==0){
            return null;
        }
        else if(allMeanNonInfinityReward.size()==1){
            return this.childNodes.get(allIndex.get(0));
        }
//        while(true){
        int index = findMinIndex(allMeanNonInfinityReward);
        return this.childNodes.get(allIndex.get(index));
        //        }
//        System.out.println("Error!");
//        return null;
    }

    //2021.05.25
    public MctsTreeNode findChildTerminalWithMinMeanNonInfinityRewardLinkedWithProbability(EvolutionState state, int thread){
        List<Integer> allIndex = new ArrayList<>();
        List<Double> allMeanNonInfinityReward = new ArrayList<>();
        double sumMeanNonInfinityReward = 0;

        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
            if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                allIndex.add(i);
                double percent = (double) this.childNodes.get(i).getMeanNonInfinityRewardLinked();
                allMeanNonInfinityReward.add(percent);
                sumMeanNonInfinityReward +=percent;
            }
//            }
        }
        if(allMeanNonInfinityReward.size()==0){
            return null;
        }
        else if(allMeanNonInfinityReward.size()==1){
            return this.childNodes.get(allIndex.get(0));
        }
        while(true){
            if(allMeanNonInfinityReward.size()==1){
                return this.childNodes.get(allIndex.get(0));
            }
            else{
                int index = findMinIndex(allMeanNonInfinityReward);
                if(state.random[thread].nextBoolean()){
                    return this.childNodes.get(allIndex.get(index));
                }
                else{
                    allMeanNonInfinityReward.remove(index);
                    allIndex.remove(index);
                }
            }
        }
//        System.out.println("Error!");
//        return null;
    }

    //2021.05.21
    public MctsTreeNode findChildFunctionWithMinMeanNonInfinityRewardWithProbability(EvolutionState state, int thread){
        List<Integer> allIndex = new ArrayList<>();
        List<Double> allMeanNonInfinityReward = new ArrayList<>();
        double sumMeanNonInfinityReward = 0;

        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
            if (this.childNodes.get(i).gpNode.expectedChildren() == 2) {
                allIndex.add(i);
                double percent = (double) this.childNodes.get(i).getMeanNonInfinityRewardGen(state.generation);
                allMeanNonInfinityReward.add(percent);
                sumMeanNonInfinityReward +=percent;
            }
//            }
        }
        if(allMeanNonInfinityReward.size()==0){
            return null;
        }
        else if(allMeanNonInfinityReward.size()==1){
            return this.childNodes.get(allIndex.get(0));
        }
        while(true){
            if(allMeanNonInfinityReward.size()==1){
                return this.childNodes.get(allIndex.get(0));
            }
            else{
                int index = findMinIndex(allMeanNonInfinityReward);
                if(state.random[thread].nextBoolean()){
                    return this.childNodes.get(allIndex.get(index));
                }
                else{
                    allMeanNonInfinityReward.remove(index);
                    allIndex.remove(index);
                }
            }
        }
    }

    //2021.05.21
    public MctsTreeNode findChildTerminalWithMinMeanNonInfinityRewardWithProbability(EvolutionState state, int thread){
        List<Integer> allIndex = new ArrayList<>();
        List<Double> allMeanNonInfinityReward = new ArrayList<>();
        double sumMeanNonInfinityReward = 0;

        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
            if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                allIndex.add(i);
                double percent = (double) this.childNodes.get(i).getMeanNonInfinityRewardGen(state.generation);
                allMeanNonInfinityReward.add(percent);
                sumMeanNonInfinityReward +=percent;
            }
//            }
        }
        if(allMeanNonInfinityReward.size()==0){
            return null;
        }
        else if(allMeanNonInfinityReward.size()==1){
            return this.childNodes.get(allIndex.get(0));
        }
        while(true){
            if(allMeanNonInfinityReward.size()==1){
                return this.childNodes.get(allIndex.get(0));
            }
            else{
                int index = findMinIndex(allMeanNonInfinityReward);
                if(state.random[thread].nextBoolean()){
                    return this.childNodes.get(allIndex.get(index));
                }
                else{
                    allMeanNonInfinityReward.remove(index);
                    allIndex.remove(index);
                }
            }
        }
//        System.out.println("Error!");
//        return null;
    }

    //2021.05.21
    public int findMinIndex(List<Double> allMeanNonInfinityReward){
        int index = 0;
        double min = allMeanNonInfinityReward.get(0);
        for(int i=1; i< allMeanNonInfinityReward.size();i++){
            if(allMeanNonInfinityReward.get(i) < min){
                min = allMeanNonInfinityReward.get(i);
                index = i;
            }
        }
        return index;
    }

    public MctsTreeNode findChildFunctionWithMinMeanNonInfinityReward(){
        int index = 0;
        double minNumInfinity = INFINITY;
        boolean firstIndexFunction = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
                if (this.childNodes.get(i).gpNode.expectedChildren() == 2) {
                    double percent = (double) this.childNodes.get(i).getMeanNonInfinityReward();
                    if (percent < minNumInfinity) {
                        index = i;
                        minNumInfinity = percent;
                        firstIndexFunction = true;
                    }
                }
//            }
        }
        if(firstIndexFunction){
            return this.childNodes.get(index);
        }
        return null;
    }

    public MctsTreeNode findChildTerminalWithMinMeanNonInfinityReward(){
        int index = 0;
        double minNumInfinity = INFINITY;
        boolean firstIndexTerminal = false;
        for(int i=0; i<this.childNodes.size(); i++){
//            if(!this.isFullyExpanded()) {//check if this node is fully expand
                if (this.childNodes.get(i).gpNode.expectedChildren() == 0) {
                    double percent = (double) this.childNodes.get(i).getMeanNonInfinityReward();
                    if (percent < minNumInfinity) {
                        index = i;
                        minNumInfinity = percent;
                        firstIndexTerminal = true;
                    }
                }
//            }
        }
        if(firstIndexTerminal){
            return this.childNodes.get(index);
        }
        return null;
    }

    public int getNumInfinityReward(){
        int num = 0;
        for(List<Double> genRewards :this.allReward.values()){
            for(Double reward: genRewards){
                if(reward > INFINITY){
                    num++;
                }
            }
        }
        return num;
    }

    public double getMeanNonInfinityReward(){
        int num = 0;
        double nonSumInfinityReward = 0;
        boolean triedAtLeastOnce = false;
        for(List<Double> genRewards :this.allReward.values()){
            for(Double reward: genRewards){
                triedAtLeastOnce = true;
                if(!(reward > INFINITY)){
                    num++;
                    nonSumInfinityReward += reward;
                }
            }
        }
        if(num == 0 && triedAtLeastOnce){//all reward is infinity
            return Double.POSITIVE_INFINITY;
        }
        else if(this.allReward.size()==0){
            //this will happen when add a untried node but have not update the allReward.
//            System.out.println("error! getMeanNonInfinityReward()");
            return Double.POSITIVE_INFINITY;
        }
        else if(num==0){
            System.out.println("error! num = 0");
            return Double.POSITIVE_INFINITY;
        }
        return nonSumInfinityReward / num;
    }

    //2021.05.21
    public double getMeanNonInfinityRewardLinked(){
        Double linkedMeanNonInfinityReward = 0.0;
        linkedMeanNonInfinityReward +=getMeanNonInfinityReward();
        double minValue = Double.MAX_VALUE;
        for(int i=0; i<this.childNodes.size(); i++){
            if(this.childNodes.get(i).getMeanNonInfinityRewardLinked()<minValue){
                minValue = this.childNodes.get(i).getMeanNonInfinityRewardLinked();
            }
        }
        if(this.childNodes.size() == 0){
            minValue = 0;
        }
        linkedMeanNonInfinityReward += minValue;
//        if(linkedMeanNonInfinityReward.isInfinite()){
//            System.out.println("error: getMeanNonInfinityRewardLinked()");
//        }
        //todo:2021.05.25 wrong! need to modify.
        return linkedMeanNonInfinityReward;
    }

    public double getMeanNonInfinityRewardGen(int gen){
        int num = 0;
        double nonSumInfinityReward = 0;
        if(gen >= 6){
            for(int i = gen - 5; i<gen; i++){
                if(this.allReward.containsKey(i)){
                    List<Double> genRewards = this.allReward.get(i);
                    for(Double reward: genRewards){
                        if(!(reward > INFINITY)){
                            num++;
                            nonSumInfinityReward += reward;
                        }
                    }
                }
            }
        }
        else{
            for(List<Double> genRewards :this.allReward.values()){
                for(Double reward: genRewards){
                    if(!(reward > INFINITY)){
                        num++;
                        nonSumInfinityReward += reward;
                    }
                }
            }
        }

        if(num == 0){//all reward is infinity
            return Double.POSITIVE_INFINITY;
        }
        return nonSumInfinityReward / num;
    }

    public double getTotalRewardDivVisitCount(){
        return this.totalReward/this.visitCount;
    }

    public void setAvailableTerminalGPNodes(List<GPNode> gpNodes) {
        this.availTerminalGPNodes = gpNodes;
    }

    public void setAvailableFunctionGPNodes(List<GPNode> gpNodes) {
        this.availFunctionGPNodes = gpNodes;
    }

    public List<GPNode> getAvailableTerminalGPNodes() {
        return this.availTerminalGPNodes;
    }

    public List<GPNode> getAvailableFunctionGPNodes() {
        return this.availFunctionGPNodes;
    }

    //2021.06.18
    public List<GPNode> getUntriedTerminalAndFunctionGPNodes(){
//        List<GPNode> untriedGPNodes = this.availTerminalGPNodes;
        List<GPNode> untriedTerminalGPNodes = new ArrayList<>(this.availTerminalGPNodes);
        List<GPNode> untriedFunctionGPNodes = new ArrayList<>(this.availFunctionGPNodes);
        List<GPNode> untriedGPNodes = new ArrayList<>();
        untriedGPNodes.addAll(untriedFunctionGPNodes);
        untriedGPNodes.addAll(untriedTerminalGPNodes);

        List<GPNode> triedGPNodes = new ArrayList<>();
        for (MctsTreeNode childNode : childNodes) {
            triedGPNodes.add(childNode.gpNode);
        }
        untriedGPNodes.removeAll(triedGPNodes);
        return untriedGPNodes;
    }

    public List<GPNode> getUntriedTerminalGPNodes(){
//        List<GPNode> untriedGPNodes = this.availTerminalGPNodes;
        List<GPNode> untriedGPNodes = new ArrayList<>(this.availTerminalGPNodes);
        List<GPNode> triedGPNodes = new ArrayList<>();
        for (MctsTreeNode childNode : childNodes) {
            if (childNode.gpNode.expectedChildren() == 0) {
                triedGPNodes.add(childNode.gpNode);
            }
        }
        untriedGPNodes.removeAll(triedGPNodes);
        return untriedGPNodes;
    }

    public List<GPNode> getUntriedFunctionGPNodes(){
//        List<GPNode> untriedGPNodes = this.availFunctionGPNodes;
        List<GPNode> untriedGPNodes = new ArrayList<>(this.availFunctionGPNodes);
        List<GPNode> triedGPNodes = new ArrayList<>();
        for (MctsTreeNode childNode : childNodes) {
            if (childNode.gpNode.expectedChildren() == 2) {
                triedGPNodes.add(childNode.gpNode);
            }
        }
        untriedGPNodes.removeAll(triedGPNodes);
        return untriedGPNodes;
    }

    public void updateVisitCount() {
        visitCount += 1;
    }

    public void updateTotalReward(double rewardAddend) {
        totalReward += rewardAddend;
    }

    public void updateAllReward(int generation, double reward){
        if(allReward.containsKey(generation)){
            allReward.get(generation).add(reward);
        }
        else{
            List<Double> rewardValue = new ArrayList<>();
            rewardValue.add(reward);
            allReward.put(generation, rewardValue);
        }
    }

    public double getTotalReward() {
        return totalReward;
    }

    public MctsTreeNode findCorrespondingMctsTreeNode(GPNode gpNode){
        for(int i=0; i<this.childNodes.size(); i++){
            GPNode index = this.childNodes.get(i).getGpNode();
            if(gpNode.toString().equals(index.toString())){
                return this.childNodes.get(i);
            }
        }
        if(this.mctsUpdateLate){
            MctsTreeNode child = new MctsTreeNode(this, gpNode, this.currentDepth+1, this.maxDepth, this.availTerminalGPNodes, this.availFunctionGPNodes, this.mctsUpdateLate);
            childNodes.add(child);
            return child;
        }
        System.out.println("Do not find the mctsTreeNode!");
        return null;
    }

    public void frontPropagateVisitCount(GPNode gpNode, double fitness) {
        MctsTreeNode mctsTreeNode = findCorrespondingMctsTreeNode(gpNode);
        if(this.mctsUpdateLate){
            mctsTreeNode.updateVisitCount();
        }
        for(int i=0; i<gpNode.children.length; i++){
            GPNode child = gpNode.children[i];
            mctsTreeNode.frontPropagateVisitCount(child, fitness);
        }
    }

    public void frontPropagateTotalReward(GPNode gpNode, double fitness) {
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

    public void frontPropagateAllReward(int gen, GPNode gpNode, double fitness) {
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

    public int getVisitCount() {
        return visitCount;
    }
}
