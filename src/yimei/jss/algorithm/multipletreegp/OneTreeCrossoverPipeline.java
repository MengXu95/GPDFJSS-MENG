package yimei.jss.algorithm.multipletreegp;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPInitializer;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.gp.koza.CrossoverPipeline;
import mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL;

public class OneTreeCrossoverPipeline extends CrossoverPipeline {

    @Override
    public int produce(final int min,
                       final int max,
                       final int start,
                       final int subpopulation,
                       final Individual[] inds,
                       final EvolutionState state,
                       final int thread) {

        int n = typicalIndsProduced();
        if (n < min) n = min;
        if (n > max) n = max;

        if (!state.random[thread].nextBoolean(likelihood)) {
            return reproduce(n, start, subpopulation, inds, state, thread, true);
        }

        GPInitializer initializer = (GPInitializer) state.initializer;

        for (int q = start; q < n + start; ) {
            if (sources[0] == sources[1]) {
                sources[0].produce(2, 2, 0, subpopulation, parents, state, thread);
            } else {
                sources[0].produce(1, 1, 0, subpopulation, parents, state, thread);
                sources[1].produce(1, 1, 1, subpopulation, parents, state, thread);
            }

            if (tree1 != TREE_UNFIXED && (tree1 < 0 || tree1 >= parents[0].trees.length)) {
                state.output.fatal("GP Crossover Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.");
            }
            if (tree2 != TREE_UNFIXED && (tree2 < 0 || tree2 >= parents[1].trees.length)) {
                state.output.fatal("GP Crossover Pipeline attempted to fix tree.1 to a value which was out of bounds of the array of the individual's trees.");
            }

            int t1;
            int t2;
            if (tree1 == TREE_UNFIXED || tree2 == TREE_UNFIXED) {
                do {
                    t1 = tree1 == TREE_UNFIXED ? randomTreeIndex((GPIndividual) parents[0], state, thread) : tree1;
                    t2 = tree2 == TREE_UNFIXED ? randomTreeIndex((GPIndividual) parents[1], state, thread) : tree2;
                } while (parents[0].trees[t1].constraints(initializer) != parents[1].trees[t2].constraints(initializer));
            } else {
                t1 = tree1;
                t2 = tree2;
                if (parents[0].trees[t1].constraints(initializer) != parents[1].trees[t2].constraints(initializer)) {
                    state.output.fatal("GP Crossover Pipeline's two tree choices are both specified by the user but their GPTreeConstraints are not the same.");
                }
            }

            boolean res1 = false;
            boolean res2 = false;
            GPNode p1 = null;
            GPNode p2 = null;

            nodeselect1.reset();
            nodeselect2.reset();

            for (int x = 0; x < numTries; x++) {
                p1 = nodeselect1.pickNode(state, subpopulation, thread, parents[0], parents[0].trees[t1]);
                p2 = nodeselect2.pickNode(state, subpopulation, thread, parents[1], parents[1].trees[t2]);

                res1 = verifyPoints(initializer, p2, p1);
                if (n - (q - start) < 2 || tossSecondParent) {
                    res2 = true;
                } else {
                    res2 = verifyPoints(initializer, p1, p2);
                }
                if (res1 && res2) break;
            }

            GPIndividual j1 = (GPIndividual) parents[0].lightClone();
            GPIndividual j2 = null;
            if (n - (q - start) >= 2 && !tossSecondParent) {
                j2 = (GPIndividual) parents[1].lightClone();
            }

            j1.trees = new GPTree[parents[0].trees.length];
            if (j2 != null) {
                j2.trees = new GPTree[parents[1].trees.length];
            }

            int parent0ContributionToJ1 = 0;
            int parent1ContributionToJ1 = 0;
            int parent0ContributionToJ2 = 0;
            int parent1ContributionToJ2 = 0;

            for (int x = 0; x < j1.trees.length; x++) {
                if (x == t1 && res1) {
                    parent0ContributionToJ1 += treeNodeCount((GPIndividual) parents[0], x) - subtreeNodeCount(p1);
                    parent1ContributionToJ1 += subtreeNodeCount(p2);
                    j1.trees[x] = (GPTree) parents[0].trees[x].lightClone();
                    j1.trees[x].owner = j1;
                    j1.trees[x].child = parents[0].trees[x].child.cloneReplacing(p2, p1);
                    j1.trees[x].child.parent = j1.trees[x];
                    j1.trees[x].child.argposition = 0;
                    j1.evaluated = false;
                } else {
                    parent0ContributionToJ1 += treeNodeCount((GPIndividual) parents[0], x);
                    copyTreeFromParent(j1, parents[0], x);
                }
            }

            if (j2 != null) {
                for (int x = 0; x < j2.trees.length; x++) {
                    if (x == t2 && res2) {
                        parent1ContributionToJ2 += treeNodeCount((GPIndividual) parents[1], x) - subtreeNodeCount(p2);
                        parent0ContributionToJ2 += subtreeNodeCount(p1);
                        j2.trees[x] = (GPTree) parents[1].trees[x].lightClone();
                        j2.trees[x].owner = j2;
                        j2.trees[x].child = parents[1].trees[x].child.cloneReplacing(p1, p2);
                        j2.trees[x].child.parent = j2.trees[x];
                        j2.trees[x].child.argposition = 0;
                        j2.evaluated = false;
                    } else {
                        parent1ContributionToJ2 += treeNodeCount((GPIndividual) parents[1], x);
                        copyTreeFromParent(j2, parents[1], x);
                    }
                }
            }

            setMPSLGPOffspringTaskIndex(state, j1, (GPIndividual) parents[0], parent0ContributionToJ1,
                (GPIndividual) parents[1], parent1ContributionToJ1);
            if (j2 != null) {
                setMPSLGPOffspringTaskIndex(state, j2, (GPIndividual) parents[1], parent1ContributionToJ2,
                    (GPIndividual) parents[0], parent0ContributionToJ2);
            }

            inds[q] = j1;
            q++;
            if (q < n + start && !tossSecondParent) {
                inds[q] = j2;
                q++;
            }
        }
        return n;
    }

    private int randomTreeIndex(GPIndividual individual, EvolutionState state, int thread) {
        return individual.trees.length > 1 ? state.random[thread].nextInt(individual.trees.length) : 0;
    }

    private void copyTreeFromParent(GPIndividual child, Individual parent, int treeIndex) {
        GPIndividual gpParent = (GPIndividual) parent;
        child.trees[treeIndex] = (GPTree) gpParent.trees[treeIndex].lightClone();
        child.trees[treeIndex].owner = child;
        child.trees[treeIndex].child = (GPNode) gpParent.trees[treeIndex].child.clone();
        child.trees[treeIndex].child.parent = child.trees[treeIndex];
        child.trees[treeIndex].child.argposition = 0;
    }

    private int treeNodeCount(GPIndividual individual, int treeIndex) {
        return individual.trees[treeIndex].child.numNodes(GPNode.NODESEARCH_ALL);
    }

    private int subtreeNodeCount(GPNode node) {
        return node == null ? 0 : node.numNodes(GPNode.NODESEARCH_ALL);
    }

    private void setMPSLGPOffspringTaskIndex(EvolutionState state, GPIndividual child,
                                             GPIndividual primaryParent, int primaryContribution,
                                             GPIndividual secondaryParent, int secondaryContribution) {
        if (!(state instanceof GPRuleEvolutionStatePSL)) {
            return;
        }
        GPRuleEvolutionStatePSL mpslgpState = (GPRuleEvolutionStatePSL) state;
        mpslgpState.setOffspringTaskIndexByContribution(child, primaryParent, primaryContribution,
            secondaryParent, secondaryContribution);
    }
}
