/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package mengxu.algorithm.ensemble.M3GP;

import ec.BreedingPipeline;
import ec.EvolutionState;
import ec.Individual;
import ec.Species;
import ec.gp.*;
import ec.gp.koza.MutationPipeline;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.lexicaseselection.ParentForMutation;
import mengxu.algorithm.multicaseEnsemble.ensembleContribution.GPRuleEvolutionStateEnsembleContribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * MutationPipeline.java
 *
 * Created: Tue Oct 12 18:50:56 1999
 * By: Sean Luke
 */

/**
 * MutationPipeline is a GPBreedingPipeline which
 * implements a strongly-typed version of the
 * "IndexPoint Mutation" operator as described in Koza I.
 * Actually, that's not quite true.  Koza doesn't have any tree depth restrictions
 * on his mutation operator.  This one does -- if the tree gets deeper than
 * the maximum tree depth, then the new subtree is rejected and another one is
 * tried.  Similar to how the Crosssover operator is implemented.
 *
 * <p>Mutated trees are restricted to being <tt>maxdepth</tt> depth at
 * most and at most <tt>maxsize</tt> number of nodes.  If in
 * <tt>tries</tt> attemptes, the pipeline cannot come up with a
 * mutated tree within the depth limit, then it simply copies the
 * original individual wholesale with no mutation.
 *
 * <p>One additional feature: if <tt>equal</tt> is true, then MutationPipeline
 * will attempt to replace the subtree with a tree of approximately equal size.
 * How this is done exactly, and how close it is, is entirely up to the pipeline's
 * tree builder -- for example, Grow/Full/HalfBuilder don't support this at all, while
 * RandomBranch will replace it with a tree of the same size or "slightly smaller"
 * as described in the algorithm.

 <p><b>Typical Number of Individuals Produced Per <tt>produce(...)</tt> call</b><br>
 ...as many as the child produces

 <p><b>Number of Sources</b><br>
 1

 <p><b>Parameters</b><br>
 <table>
 <tr><td valign=top><i>base</i>.<tt>tries</tt><br>
 <font size=-1>int &gt;= 1</font></td>
 <td valign=top>(number of times to try finding valid pairs of nodes)</td></tr>

 <tr><td valign=top><i>base</i>.<tt>maxdepth</tt><br>
 <font size=-1>int &gt;= 1</font></td>
 <td valign=top>(maximum valid depth of a crossed-over subtree)</td></tr>

 <tr><td valign=top><i>base</i>.<tt>maxsize</tt><br>
 <font size=-1>int &gt;= 1</font></td>
 <td valign=top>(maximum valid size, in nodes, of a crossed-over subtree)</td></tr>

 <tr><td valign=top><i>base</i>.<tt>ns</tt><br>
 <font size=-1>classname, inherits and != GPNodeSelector</font></td>
 <td valign=top>(GPNodeSelector for tree)</td></tr>

 <tr><td valign=top><i>base</i>.<tt>build</tt>.0<br>
 <font size=-1>classname, inherits and != GPNodeBuilder</font></td>
 <td valign=top>(GPNodeBuilder for new subtree)</td></tr>

 <tr><td valign=top><tt>equal</tt><br>
 <font size=-1>bool = <tt>true</tt> or <tt>false</tt> (default)</td>
 <td valign=top>(do we attempt to replace the subtree with a new one of roughly the same size?)</td></tr>

 <tr><td valign=top><i>base</i>.<tt>tree.0</tt><br>
 <font size=-1>0 &lt; int &lt; (num trees in individuals), if exists</font></td>
 <td valign=top>(tree chosen for mutation; if parameter doesn't exist, tree is picked at random)</td></tr>

 </table>

 <p><b>Default Base</b><br>
 gp.koza.mutate

 <p><b>Parameter bases</b><br>
 <table>

 <tr><td valign=top><i>base</i>.<tt>ns</tt><br>
 <td>nodeselect</td></tr>

 <tr><td valign=top><i>base</i>.<tt>build</tt><br>
 <td>builder</td></tr>

 </table>



 * @author Sean Luke
 * @version 1.0
 */

public class MutationIncludeEnsemblePipelineM3GP extends MutationPipeline
    {

    public int produce(final int min,
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


        // should we bother?
        if (!state.random[thread].nextBoolean(likelihood))
            return reproduce(n, start, subpopulation, inds, state, thread, false);  // DON'T produce children from source -- we already did


        GPInitializer initializer = ((GPInitializer)state.initializer);

            // now let's mutate 'em
        for(int q=start; q < n+start; q++) //q=start = 0  q<1
            {
                //todo: 2023.03.02 need to double check the index q and start by mengxu
                double randomPro = state.random[thread].nextDouble();
                if(randomPro < ((double) 1.0/3.0)){
                    q = produceNormalMutationOfIndividualInEnsembleStrategy1(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
                }
                else if(randomPro >= ((double) 1.0/3.0) && randomPro < ((double) 2.0/3.0)){
                    q = produceNormalMutationOfIndividualInEnsembleStrategy2(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
                }
                else{
                    q = produceNormalMutationOfIndividualInEnsembleStrategy3(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
                }
            }
        return n;
        }

        public int produceNormalMutationOfIndividual(final int min,
                                                            final int max,
                                                            final int start,
                                                            final int subpopulation,
                                                            GPIndividual i,
                                                            final Individual[] inds,
                                                            final EvolutionState state,
                                                            final int thread,
                                                            final GPInitializer initializer,
                                                            int q, int n)

        {

            if (tree!=TREE_UNFIXED && (tree<0 || tree >= i.trees.length))
                // uh oh
                state.output.fatal("GP Mutation Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");

            if(state instanceof GPRuleEvolutionStateEnsembleContribution){
                ParentForMutation parentForMutation = new ParentForMutation(q, i.fitness.fitness(), q, -1);
                ((GPRuleEvolutionStateEnsembleContribution) state).parentIndexForMutation.add(parentForMutation);
            }

            int t;
            // pick random tree
            if (tree==TREE_UNFIXED)
                if (i.trees.length>1) t = state.random[thread].nextInt(i.trees.length);
                else t = 0;
            else t = tree;

            // validity result...
            boolean res = false;

            // prepare the nodeselector
            nodeselect.reset();

            // pick a node

            GPNode p1=null;  // the node we pick
            GPNode p2=null;

            for(int x=0;x<numTries;x++)
            {
                // pick a node in individual 1
                p1 = nodeselect.pickNode(state,subpopulation,thread,i,i.trees[t]);

                // generate a tree swap-compatible with p1's position


                int size = GPNodeBuilder.NOSIZEGIVEN;
                if (equalSize) size = p1.numNodes(GPNode.NODESEARCH_ALL);

                p2 = builder.newRootedTree(state,
                        p1.parentType(initializer),
                        thread,
                        p1.parent,
                        i.trees[t].constraints(initializer).functionset,
                        p1.argposition,
                        size);

                // check for depth and swap-compatibility limits
                res = verifyPoints(p2,p1);  // p2 can fit in p1's spot  -- the order is important!

                // did we get something that had both nodes verified?
                if (res) break;
            }

            GPIndividual j;

            if (sources[0] instanceof BreedingPipeline)
            // it's already a copy, so just smash the tree in
            {
                j=i;
                if (res)  // we're in business
                {
                    p2.parent = p1.parent;
                    p2.argposition = p1.argposition;
                    if (p2.parent instanceof GPNode)
                        ((GPNode)(p2.parent)).children[p2.argposition] = p2;
                    else ((GPTree)(p2.parent)).child = p2;
                    j.evaluated = false;  // we've modified it
                }
            }
            else // need to clone the individual
            {
                j = (GPIndividual)(i.lightClone());

                // Fill in various tree information that didn't get filled in there
                j.trees = new GPTree[i.trees.length];

                // at this point, p1 or p2, or both, may be null.
                // If not, swap one in.  Else just copy the parent.
                for(int x=0;x<j.trees.length;x++)
                {
                    if (x==t && res)  // we've got a tree with a kicking cross position!
                    {
                        j.trees[x] = (GPTree)(i.trees[x].lightClone());
                        j.trees[x].owner = j;
                        j.trees[x].child = i.trees[x].child.cloneReplacingNoSubclone(p2,p1);
                        j.trees[x].child.parent = j.trees[x];
                        j.trees[x].child.argposition = 0;
                        j.evaluated = false;
                    } // it's changed
                    else
                    {
                        j.trees[x] = (GPTree)(i.trees[x].lightClone());
                        j.trees[x].owner = j;
                        j.trees[x].child = (GPNode)(i.trees[x].child.clone());
                        j.trees[x].child.parent = j.trees[x];
                        j.trees[x].child.argposition = 0;
                    }
                }
            }

            inds[q] = j;
            return q;
        }


        public int produceNormalMutationOfIndividualInEnsembleStrategy1(final int min,
                                                          final int max,
                                                          final int start,
                                                          final int subpopulation,
                                                          final Individual[] inds,
                                                          final EvolutionState state,
                                                          final int thread,
                                                          final GPInitializer initializer,
                                                          int q, int n)

        {

            // at this point, parents[] contains our two selected individuals

//            EnsembleRule parentEnsemblesForMutation = ((EnsembleEvaluator)state.evaluator).parentsForMutation;

            EnsembleRule ensembleParent = ((EnsembleEvaluatorM3GP)state.evaluator).parentsForMutation;

            int lengthParent = ensembleParent.getEnsemble().size();

//            int indexPointOfParent = state.random[thread].nextInt(lengthParent);

            //modified by mengxu 2023.04.10
            int indexPointOfParent = state.random[thread].nextInt(lengthParent);

            GPIndividual individualOfParent = (GPIndividual)ensembleParent.getEnsemble().get(indexPointOfParent).clone();

            GPIndividual offspring = produceNormalMutation(min, max, start, subpopulation, individualOfParent, inds, state, thread, initializer, q, n);

            List<Individual> ensembleOffspringList = new ArrayList<>();
            for(int i=0; i<lengthParent; i++){
                if(i == indexPointOfParent){
                    ensembleOffspringList.add(offspring); //cross the individual
                }
                else{
                    ensembleOffspringList.add((Individual)ensembleParent.getEnsemble().get(i).clone());
                }
            }

            EnsembleRule offspringEnsemble = new EnsembleRule(ensembleOffspringList, lengthParent);

            ((EnsembleEvaluatorM3GP)state.evaluator).ensembleOffspringPool.add(offspringEnsemble);

            return q;
        }

        public int produceNormalMutationOfIndividualInEnsembleStrategy2(final int min,
                                                                        final int max,
                                                                        final int start,
                                                                        final int subpopulation,
                                                                        final Individual[] inds,
                                                                        final EvolutionState state,
                                                                        final int thread,
                                                                        final GPInitializer initializer,
                                                                        int q, int n)

        {

            // at this point, parents[] contains our two selected individuals

//            EnsembleRule parentEnsemblesForMutation = ((EnsembleEvaluator)state.evaluator).parentsForMutation;

            EnsembleRule ensembleParent = ((EnsembleEvaluatorM3GP)state.evaluator).parentsForMutation;

            int lengthParent = ensembleParent.getEnsemble().size();
            List<Individual> ensembleParentList = ensembleParent.getEnsemble();

            Species species = ensembleParentList.get(0).species;


            Individual newIndividual = species.newIndividual(state, 0);

            List<Individual> ensembleOffspringList = new ArrayList<>();
            for(int i=0; i<lengthParent; i++){
                ensembleOffspringList.add((Individual)ensembleParent.getEnsemble().get(i).clone());
            }
            ensembleOffspringList.add(newIndividual);

            EnsembleRule offspringEnsemble = new EnsembleRule(ensembleOffspringList, lengthParent+1);

            ((EnsembleEvaluatorM3GP)state.evaluator).ensembleOffspringPool.add(offspringEnsemble);

            return q;
        }

        public int produceNormalMutationOfIndividualInEnsembleStrategy3(final int min,
                                                                        final int max,
                                                                        final int start,
                                                                        final int subpopulation,
                                                                        final Individual[] inds,
                                                                        final EvolutionState state,
                                                                        final int thread,
                                                                        final GPInitializer initializer,
                                                                        int q, int n)

        {

            // at this point, parents[] contains our two selected individuals

//            EnsembleRule parentEnsemblesForMutation = ((EnsembleEvaluator)state.evaluator).parentsForMutation;

            EnsembleRule ensembleParent = ((EnsembleEvaluatorM3GP)state.evaluator).parentsForMutation;

            int lengthParent = ensembleParent.getEnsemble().size();
            List<Individual> ensembleParentList = ensembleParent.getEnsemble();

            //modified by mengxu 2023.04.10
            int indexPointOfParent = state.random[thread].nextInt(lengthParent);

            List<Individual> ensembleOffspringList = new ArrayList<>();
            for(int i=0; i<lengthParent; i++){
                if(i != indexPointOfParent || lengthParent == 1){
                    ensembleOffspringList.add((Individual)ensembleParent.getEnsemble().get(i).clone());
                }
            }

            EnsembleRule offspringEnsemble = new EnsembleRule(ensembleOffspringList, lengthParent+1);

            ((EnsembleEvaluatorM3GP)state.evaluator).ensembleOffspringPool.add(offspringEnsemble);

            return q;
        }

        public GPIndividual produceNormalMutation(final int min,
                                         final int max,
                                         final int start,
                                         final int subpopulation,
                                         GPIndividual i,
                                         final Individual[] inds,
                                         final EvolutionState state,
                                         final int thread,
                                         final GPInitializer initializer,
                                         int q, int n)

        {

            if (tree!=TREE_UNFIXED && (tree<0 || tree >= i.trees.length))
                // uh oh
                state.output.fatal("GP Mutation Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");

            if(state instanceof GPRuleEvolutionStateEnsembleContribution){
                ParentForMutation parentForMutation = new ParentForMutation(q, i.fitness.fitness(), q, -1);
                ((GPRuleEvolutionStateEnsembleContribution) state).parentIndexForMutation.add(parentForMutation);
            }

            int t;
            // pick random tree
            if (tree==TREE_UNFIXED)
                if (i.trees.length>1) t = state.random[thread].nextInt(i.trees.length);
                else t = 0;
            else t = tree;

            // validity result...
            boolean res = false;

            // prepare the nodeselector
            nodeselect.reset();

            // pick a node

            GPNode p1=null;  // the node we pick
            GPNode p2=null;

            for(int x=0;x<numTries;x++)
            {
                // pick a node in individual 1
                p1 = nodeselect.pickNode(state,subpopulation,thread,i,i.trees[t]);

                // generate a tree swap-compatible with p1's position


                int size = GPNodeBuilder.NOSIZEGIVEN;
                if (equalSize) size = p1.numNodes(GPNode.NODESEARCH_ALL);

                p2 = builder.newRootedTree(state,
                        p1.parentType(initializer),
                        thread,
                        p1.parent,
                        i.trees[t].constraints(initializer).functionset,
                        p1.argposition,
                        size);

                // check for depth and swap-compatibility limits
                res = verifyPoints(p2,p1);  // p2 can fit in p1's spot  -- the order is important!

                // did we get something that had both nodes verified?
                if (res) break;
            }

            GPIndividual j;

            if (sources[0] instanceof BreedingPipeline)
            // it's already a copy, so just smash the tree in
            {
                j=i;
                if (res)  // we're in business
                {
                    p2.parent = p1.parent;
                    p2.argposition = p1.argposition;
                    if (p2.parent instanceof GPNode)
                        ((GPNode)(p2.parent)).children[p2.argposition] = p2;
                    else ((GPTree)(p2.parent)).child = p2;
                    j.evaluated = false;  // we've modified it
                }
            }
            else // need to clone the individual
            {
                j = (GPIndividual)(i.lightClone());

                // Fill in various tree information that didn't get filled in there
                j.trees = new GPTree[i.trees.length];

                // at this point, p1 or p2, or both, may be null.
                // If not, swap one in.  Else just copy the parent.
                for(int x=0;x<j.trees.length;x++)
                {
                    if (x==t && res)  // we've got a tree with a kicking cross position!
                    {
                        j.trees[x] = (GPTree)(i.trees[x].lightClone());
                        j.trees[x].owner = j;
                        j.trees[x].child = i.trees[x].child.cloneReplacingNoSubclone(p2,p1);
                        j.trees[x].child.parent = j.trees[x];
                        j.trees[x].child.argposition = 0;
                        j.evaluated = false;
                    } // it's changed
                    else
                    {
                        j.trees[x] = (GPTree)(i.trees[x].lightClone());
                        j.trees[x].owner = j;
                        j.trees[x].child = (GPNode)(i.trees[x].child.clone());
                        j.trees[x].child.parent = j.trees[x];
                        j.trees[x].child.argposition = 0;
                    }
                }
            }

            return j;
        }

    }
