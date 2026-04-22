package mengxu.algorithm.ensemble.eGP;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPInitializer;
import ec.gp.GPNode;
import ec.gp.GPTree;
import mengxu.algorithm.averageFitness.GPRuleEvolutionStateAverage;
import mengxu.algorithm.clusterselection.multiplecasecluster.GPRuleEvolutionStateMultiCaseCluster;
import mengxu.algorithm.diversepartnerselection.GPRuleEvolutionStateDPS;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.GPRuleEvolutionStateDPSNS;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.focusOnPoorCase.GPRuleEvolutionStateDPSPC;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.ensemble.GPRuleEvolutionStateBaseline;
import mengxu.algorithm.ensemble.GPRuleEvolutionStateCluster;
import mengxu.algorithm.lexicaseselection.GPRuleEvolutionStateOneInstanceMultiCase;
import mengxu.algorithm.lexicaseselection.LSmultiInstance.GPRuleEvolutionStateMultiInstance;
import mengxu.algorithm.lexicaseselection.LSmultiReplication.GPRuleEvolutionStateMultiReplication;
import mengxu.algorithm.multicasePareto.GPRuleEvolutionStateMCP;
import mengxu.algorithm.multiobjective.MOEAD.GPRuleEvolutionStateMOEAD;
import mengxu.algorithm.multiobjective.MOEADAdaptiveWeightStrategy.GPRuleEvolutionStateMOEADAWS;
import mengxu.algorithm.multiobjective.MOEADarchive.GPRuleEvolutionStateMOEADarchive;
import mengxu.algorithm.multiobjective.MOEADepsilonC.GPRuleEvolutionStateMOEADeC;
import mengxu.algorithm.multiobjective.MOEADmap.GPRuleEvolutionStateMOEADmap;
import mengxu.algorithm.semanticTournamentSelection.GPRuleEvolutionStateMCSTS;
import yimei.jss.algorithm.multipletreegp.AllIndexAllSwapCrossoverPipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mengxu on 01.03.2023.
 */
public class CrossoverIncludeEnsemblePipelineeGP extends AllIndexAllSwapCrossoverPipeline {
    /**
     * Overrides the normal crossover pipeline to use all pairs of the SAME TREE INDEX in each individual when performing crossover. This is necessary where the tree locations ~mean something~.
     * <p>
     * Unfortunately, this is an ugly extension due to the parent class design, but the changed parts from the parent can be found between the >>>>>>>START<<<<<<< and >>>>>>>>END<<<<<<<< tags.
     */
	
	//everytime, randomly choose one tree to do crossover. According to the setting, must be point crossover.
	//after that, swap the other tree. 
	//finally, get the two new offspring
	
	//one tree do point crossover, the other tree swap
    @Override
    public int produce(final int min,
                       final int max,
                       final int start,
                       final int subpopulation,
                       final Individual[] inds,
                       final EvolutionState state,
                       final int thread)

    {
        if(((GPRuleEvolutionStateeGP)state).breedIndividualPopulation){
            return produceIndividual(min,max,start,subpopulation,inds,state,thread);//todo: need to modify
        }
        else{
            return produceEnsemble(min,max,start,subpopulation,inds,state,thread);
        }
    }


    public int produceIndividual(final int min,
                       final int max,
                       final int start,
                       final int subpopulation,
                       final Individual[] inds,
                       final EvolutionState state,
                       final int thread)

    {

        // how many individuals should we make?
        int n = typicalIndsProduced();
        if (n < min) n = min;
        if (n > max) n = max;

        // should we bother?
        if (!state.random[thread].nextBoolean(likelihood))
            return reproduce(n, start, subpopulation, inds, state, thread, true);  // DO produce children from source -- we've not done so already



        GPInitializer initializer = ((GPInitializer)state.initializer);

        for(int q=start;q<n+start; /* no increment */)  // keep on going until we're filled up
        {
            //System.out.println("sources[0] "+sources[0]);
            //System.out.println("sources[1] "+sources[1]);
        	/*sources[0] ec.select.TournamentSelection@43814d18
        	sources[1] ec.select.TournamentSelection@43814d18*/
            //System.out.println(sources[0]==sources[1]);
            //grab two individuals from our sources
            if (sources[0]==sources[1])  // grab from the same source  always true, even tc0 and tc1 appear
//                sources[0].produce(2,2,0,subpopulation,parents,state,thread);  //max start subpopulation//original
                if(state instanceof GPRuleEvolutionStateMOEAD || state instanceof GPRuleEvolutionStateMOEADarchive || state instanceof GPRuleEvolutionStateMOEADmap || state instanceof GPRuleEvolutionStateMOEADAWS || state instanceof GPRuleEvolutionStateMOEADeC){
                    //this is a bug just find 2022.10.01
                    sources[0].produce(2,2,start,subpopulation,parents,state,thread);  //max start subpopulation
                }
                else{
                    sources[0].produce(2,2,0,subpopulation,parents,state,thread);  //max start subpopulation
                }
           /* Produces <i>n</i> individuals from the given subpopulation
            and puts them into inds[start...start+n-1]*/
            else // grab from different sources
            {
//                sources[0].produce(1,1,0,subpopulation,parents,state,thread);
//                sources[1].produce(1,1,1,subpopulation,parents,state,thread);
                if(state instanceof GPRuleEvolutionStateMOEAD || state instanceof GPRuleEvolutionStateMOEADarchive || state instanceof GPRuleEvolutionStateMOEADmap || state instanceof GPRuleEvolutionStateMOEADAWS || state instanceof GPRuleEvolutionStateMOEADeC){
                    //this is a bug just find 2022.10.01
                    sources[0].produce(2,2,start,subpopulation,parents,state,thread);  //max start subpopulation
                }
                else{
                    sources[0].produce(1,1,0,subpopulation,parents,state,thread);
                    sources[1].produce(1,1,1,subpopulation,parents,state,thread);
                }
            }

            //modified by mengxu 2023.03.02
            q = produceNormalCrossoverBetweenIndividuals(min, max, start, subpopulation, inds, state, thread, initializer, q, n);

//            boolean parent1Individual = false;
//            boolean parent2Individual = false;
//            if(parents[0] == null){
//                parent1Individual = false;
//            }
//            if(parents[1] == null){
//                parent2Individual = false;
//            }
//
//            if(parent1Individual && parent2Individual){
//                q = produceNormalCrossoverBetweenIndividuals(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
//            }
//            else if(!parent1Individual && !parent2Individual){
//                q = produceNormalCrossoverBetweenEnsembles(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
//                return 0;
//            }
//            else{
//                q = produceNormalCrossoverBetweenIndividualAndEnsemble(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
//                return 1;
//            }
////                q++;//todo: need to double check 2023.03.02
        }
        return n;
    }

    public int produceEnsemble(final int min,
                       final int max,
                       final int start,
                       final int subpopulation,
                       final Individual[] inds,
                       final EvolutionState state,
                       final int thread)

    {

        // how many individuals should we make?
        int n = typicalIndsProduced();
        if (n < min) n = min;
        if (n > max) n = max;

        // should we bother?
        if (!state.random[thread].nextBoolean(likelihood))
            return reproduce(n, start, subpopulation, inds, state, thread, true);  // DO produce children from source -- we've not done so already



        GPInitializer initializer = ((GPInitializer)state.initializer);

        for(int q=start;q<n+start; /* no increment */)  // keep on going until we're filled up
            {
            //System.out.println("sources[0] "+sources[0]);
            //System.out.println("sources[1] "+sources[1]);
        	/*sources[0] ec.select.TournamentSelection@43814d18
        	sources[1] ec.select.TournamentSelection@43814d18*/
        	//System.out.println(sources[0]==sources[1]);
            //grab two individuals from our sources
            if (sources[0]==sources[1])  // grab from the same source  always true, even tc0 and tc1 appear
//                sources[0].produce(2,2,0,subpopulation,parents,state,thread);  //max start subpopulation//original
                    if(state instanceof GPRuleEvolutionStateMOEAD || state instanceof GPRuleEvolutionStateMOEADarchive || state instanceof GPRuleEvolutionStateMOEADmap || state instanceof GPRuleEvolutionStateMOEADAWS || state instanceof GPRuleEvolutionStateMOEADeC){
                        //this is a bug just find 2022.10.01
                        sources[0].produce(2,2,start,subpopulation,parents,state,thread);  //max start subpopulation
                    }
                    else{
                        sources[0].produce(2,2,0,subpopulation,parents,state,thread);  //max start subpopulation
                    }
           /* Produces <i>n</i> individuals from the given subpopulation
            and puts them into inds[start...start+n-1]*/
            else // grab from different sources
                {
//                sources[0].produce(1,1,0,subpopulation,parents,state,thread);
//                sources[1].produce(1,1,1,subpopulation,parents,state,thread);
                    if(state instanceof GPRuleEvolutionStateMOEAD || state instanceof GPRuleEvolutionStateMOEADarchive || state instanceof GPRuleEvolutionStateMOEADmap || state instanceof GPRuleEvolutionStateMOEADAWS || state instanceof GPRuleEvolutionStateMOEADeC){
                        //this is a bug just find 2022.10.01
                        sources[0].produce(2,2,start,subpopulation,parents,state,thread);  //max start subpopulation
                    }
                    else{
                        sources[0].produce(1,1,0,subpopulation,parents,state,thread);
                        sources[1].produce(1,1,1,subpopulation,parents,state,thread);
                    }
                }

            //modified by mengxu 2023.03.02
            double randomPro = state.random[thread].nextDouble();
            if(randomPro < 0.5){
                produceNormalCrossoverBetweenEnsemblesStrategy1(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
            }
            else{
                produceNormalCrossoverBetweenEnsemblesStrategy2(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
            }
            q++;

//            boolean parent1Individual = false;
//            boolean parent2Individual = false;
//            if(parents[0] == null){
//                parent1Individual = false;
//            }
//            if(parents[1] == null){
//                parent2Individual = false;
//            }
//
//            if(parent1Individual && parent2Individual){
//                q = produceNormalCrossoverBetweenIndividuals(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
//            }
//            else if(!parent1Individual && !parent2Individual){
//                q = produceNormalCrossoverBetweenEnsembles(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
//                return 0;
//            }
//            else{
//                q = produceNormalCrossoverBetweenIndividualAndEnsemble(min, max, start, subpopulation, inds, state, thread, initializer, q, n);
//                return 1;
//            }
////                q++;//todo: need to double check 2023.03.02
            }
        return n;
        }

    public int produceNormalCrossoverBetweenIndividuals(final int min,
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

        // are our tree values valid?
        if (tree1!=TREE_UNFIXED && (tree1<0 || tree1 >= parents[0].trees.length))
            // uh oh
            state.output.fatal("GP Crossover Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");
        if (tree2!=TREE_UNFIXED && (tree2<0 || tree2 >= parents[1].trees.length))
            // uh oh
            state.output.fatal("GP Crossover Pipeline attempted to fix tree.1 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");

        int t1=0; int t2=0;

        if (tree1==TREE_UNFIXED || tree2==TREE_UNFIXED)
        {
            do
            // pick random trees  -- their GPTreeConstraints must be the same
            {
                if (tree1==TREE_UNFIXED)

                    if (parents[0].trees.length > 1)
                        t1 = state.random[thread].nextInt(parents[0].trees.length);
                    else
                    {t1 = 0;
                        //System.out.println(parents[0].trees.length);
                    }
                else
                    t1 = tree1;

                if (tree2==TREE_UNFIXED)
                    if (parents[1].trees.length>1)
                        t2 = state.random[thread].nextInt(parents[1].trees.length);
                    else t2 = 0;
                else
                    t2 = tree2;
            } while (parents[0].trees[t1].constraints(initializer) != parents[1].trees[t2].constraints(initializer));
            //while (false);
        }
        else
        {
            t1 = tree1;
            t2 = tree2;
            // make sure the constraints are okay
            if (parents[0].trees[t1].constraints(initializer)
                    != parents[1].trees[t2].constraints(initializer)) // uh oh
                state.output.fatal("GP Crossover Pipeline's two tree choices are both specified by the user -- but their GPTreeConstraints are not the same");
        }

        // validity results...
        boolean res1 = false;
        boolean res2 = false;


        // prepare the nodeselectors
        nodeselect1.reset();
        nodeselect2.reset();


        // pick some nodes

        GPNode p1=null;
        GPNode p2=null;

        for(int x=0;x<numTries;x++)
        {
            // pick a node in individual 1
            p1 = nodeselect1.pickNode(state,subpopulation,thread,parents[0],parents[0].trees[t1]);

            // pick a node in individual 2
            p2 = nodeselect2.pickNode(state,subpopulation,thread,parents[1],parents[1].trees[t2]);

            // check for depth and swap-compatibility limits
            res1 = verifyPoints(initializer,p2,p1);  // p2 can fill p1's spot -- order is important!
            if (n-(q-start)<2 || tossSecondParent) res2 = true;
            else res2 = verifyPoints(initializer,p1,p2);  // p1 can fill p2's spot -- order is important!

            // did we get something that had both nodes verified?
            // we reject if EITHER of them is invalid.  This is what lil-gp does.
            // Koza only has numTries set to 1, so it's compatible as well.
            if (res1 && res2) break;
        }
        // Create some new individuals based on the old ones -- since
        // GPTree doesn't deep-clone, this should be just fine.  Perhaps we
        // should change this to proto off of the main species prototype, but
        // we have to then copy so much stuff over; it's not worth it.

        GPIndividual j1 = (GPIndividual)(parents[0].lightClone());
        GPIndividual j2 = null;
        if (n-(q-start)>=2 && !tossSecondParent) j2 = (GPIndividual)(parents[1].lightClone());

        // Fill in various tree information that didn't get filled in there
        j1.trees = new GPTree[parents[0].trees.length];
        if (n-(q-start)>=2 && !tossSecondParent) j2.trees = new GPTree[parents[1].trees.length];

        // at this point, p1 or p2, or both, may be null.
        // If not, swap one in.  Else just copy the parent.

        for(int x=0;x<j1.trees.length;x++)
        {
            if (x==t1 && res1)  // we've got a tree with a kicking cross position!
            {
                j1.trees[x] = (GPTree)(parents[0].trees[x].lightClone());
                j1.trees[x].owner = j1;
                j1.trees[x].child = parents[0].trees[x].child.cloneReplacing(p2,p1); //p2 new, p1 old
                j1.trees[x].child.parent = j1.trees[x];
                j1.trees[x].child.argposition = 0;
                j1.evaluated = false;
            }  // it's changed
            else
            {
                //modified by fzhang 7.6.2018  randomly choose one tree to do crossover. According to the setting, must be point crossover.
                //after that, swap the other tree.
                j1.trees[x] = (GPTree)(parents[1].trees[x].lightClone());
                j1.trees[x].owner = j1;
                j1.trees[x].child = (GPNode)(parents[1].trees[x].child.clone());
                j1.trees[x].child.parent = j1.trees[x];
                j1.trees[x].child.argposition = 0;
            }
        }

        if (n - (q - start) >= 2 && !tossSecondParent)
            for (int x = 0; x < j2.trees.length; x++) {
                if (x == t2 && res2) // we've got a tree with a kicking cross position!
                {
                    j2.trees[x] = (GPTree) (parents[1].trees[x].lightClone());
                    j2.trees[x].owner = j2;
                    j2.trees[x].child = parents[1].trees[x].child.cloneReplacing(p1, p2);
                    j2.trees[x].child.parent = j2.trees[x];
                    j2.trees[x].child.argposition = 0;
                    j2.evaluated = false;
                } // it's changed
                else {
                    j2.trees[x] = (GPTree) (parents[0].trees[x].lightClone());
                    j2.trees[x].owner = j2;
                    j2.trees[x].child = (GPNode) (parents[0].trees[x].child.clone());
                    j2.trees[x].child.parent = j2.trees[x];
                    j2.trees[x].child.argposition = 0;
                }
            }

        // add the individuals to the population

        //2021.10.14 to find the difference with Tournament selection
        if(state instanceof GPRuleEvolutionStateBaseline){
            int index = ((GPRuleEvolutionStateBaseline) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateBaseline) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateAverage){
            int index = ((GPRuleEvolutionStateAverage) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateAverage) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateDPS){
            int index = ((GPRuleEvolutionStateDPS) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateDPS) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateDPSNS){
            int index = ((GPRuleEvolutionStateDPSNS) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateDPSNS) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateDPSPC){
            int index = ((GPRuleEvolutionStateDPSPC) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateDPSPC) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateMCP){
            int index = ((GPRuleEvolutionStateMCP) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateMCP) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateCluster){
            int index = ((GPRuleEvolutionStateCluster) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateCluster) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateMultiCaseCluster){
            int index = ((GPRuleEvolutionStateMultiCaseCluster) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateMultiCaseCluster) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateOneInstanceMultiCase){//this is for lexicase selection modified by mengxu 2022.02.28
            int index = ((GPRuleEvolutionStateOneInstanceMultiCase) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateOneInstanceMultiCase) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateMultiInstance){//this is for lexicase selection modified by mengxu 2022.02.28
            int index = ((GPRuleEvolutionStateMultiInstance) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateMultiInstance) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateMultiReplication){//this is for lexicase selection modified by mengxu 2022.02.28
            int index = ((GPRuleEvolutionStateMultiReplication) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateMultiReplication) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateMCSTS){//this is for lexicase selection modified by mengxu 2022.02.28
            int index = ((GPRuleEvolutionStateMCSTS) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateMCSTS) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }
        if(state instanceof GPRuleEvolutionStateeGP){//this is for lexicase selection modified by mengxu 2022.02.28
            int index = ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }


        inds[q] = j1;
        q++;
        if (q<n+start && !tossSecondParent)
        {
            //2021.10.14 to find the difference with Tournament selection
            if(state instanceof GPRuleEvolutionStateBaseline){
                int index = ((GPRuleEvolutionStateBaseline) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateBaseline) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateAverage){
                int index = ((GPRuleEvolutionStateAverage) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateAverage) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateDPS){
                int index = ((GPRuleEvolutionStateDPS) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateDPS) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateDPSNS){
                int index = ((GPRuleEvolutionStateDPSNS) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateDPSNS) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateMCP){
                int index = ((GPRuleEvolutionStateMCP) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateMCP) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateDPSPC){
                int index = ((GPRuleEvolutionStateDPSPC) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateDPSPC) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateCluster){
                int index = ((GPRuleEvolutionStateCluster) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateCluster) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateMultiCaseCluster){
                int index = ((GPRuleEvolutionStateMultiCaseCluster) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateMultiCaseCluster) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateOneInstanceMultiCase){//this is for lexicase selection modified by mengxu 2022.02.28
                int index = ((GPRuleEvolutionStateOneInstanceMultiCase) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateOneInstanceMultiCase) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateMultiInstance){//this is for lexicase selection modified by mengxu 2022.02.28
                int index = ((GPRuleEvolutionStateMultiInstance) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateMultiInstance) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateMultiReplication){//this is for lexicase selection modified by mengxu 2022.02.28
                int index = ((GPRuleEvolutionStateMultiReplication) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateMultiReplication) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateMCSTS){//this is for lexicase selection modified by mengxu 2022.02.28
                int index = ((GPRuleEvolutionStateMCSTS) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateMCSTS) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }
            if(state instanceof GPRuleEvolutionStateeGP){//this is for lexicase selection modified by mengxu 2022.02.28
                int index = ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }

            inds[q] = j2;
            q++;
        }
        return q;
    }

        public Individual[] produceNormalCrossoverBetweenIndividuals(final int min,
                                                            final int max,
                                                            final int start,
                                                            final int subpopulation,
                                                            final Individual[] inds,
                                                            final EvolutionState state,
                                                            final int thread,
                                                            final GPInitializer initializer,
                                                            int q, int n,
                                                            Individual[] parents)

        {

                // at this point, parents[] contains our two selected individuals
                Individual[] offsprings = new Individual[2];

                // are our tree values valid?
                if (tree1!=TREE_UNFIXED && (tree1<0 || tree1 >= ((GPIndividual)parents[0]).trees.length))
                    // uh oh
                    state.output.fatal("GP Crossover Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");
                if (tree2!=TREE_UNFIXED && (tree2<0 || tree2 >= ((GPIndividual)parents[1]).trees.length))
                    // uh oh
                    state.output.fatal("GP Crossover Pipeline attempted to fix tree.1 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");

                int t1=0; int t2=0;

                if (tree1==TREE_UNFIXED || tree2==TREE_UNFIXED)
                {
                    do
                    // pick random trees  -- their GPTreeConstraints must be the same
                    {
                        if (tree1==TREE_UNFIXED)

                            if (((GPIndividual)parents[0]).trees.length > 1)
                                t1 = state.random[thread].nextInt(((GPIndividual)parents[0]).trees.length);
                            else
                            {t1 = 0;
                                //System.out.println(parents[0].trees.length);
                            }
                        else
                            t1 = tree1;

                        if (tree2==TREE_UNFIXED)
                            if (((GPIndividual)parents[1]).trees.length>1)
                                t2 = state.random[thread].nextInt(((GPIndividual)parents[1]).trees.length);
                            else t2 = 0;
                        else
                            t2 = tree2;
                    } while (((GPIndividual)parents[0]).trees[t1].constraints(initializer) != ((GPIndividual)parents[1]).trees[t2].constraints(initializer));
                    //while (false);
                }
                else
                {
                    t1 = tree1;
                    t2 = tree2;
                    // make sure the constraints are okay
                    if (((GPIndividual)parents[0]).trees[t1].constraints(initializer)
                            != ((GPIndividual)parents[1]).trees[t2].constraints(initializer)) // uh oh
                        state.output.fatal("GP Crossover Pipeline's two tree choices are both specified by the user -- but their GPTreeConstraints are not the same");
                }

                // validity results...
                boolean res1 = false;
                boolean res2 = false;


                // prepare the nodeselectors
                nodeselect1.reset();
                nodeselect2.reset();


                // pick some nodes

                GPNode p1=null;
                GPNode p2=null;

                for(int x=0;x<numTries;x++)
                {
                    // pick a node in individual 1
                    p1 = nodeselect1.pickNode(state,subpopulation,thread,((GPIndividual)parents[0]),((GPIndividual)parents[0]).trees[t1]);

                    // pick a node in individual 2
                    p2 = nodeselect2.pickNode(state,subpopulation,thread,((GPIndividual)parents[1]),((GPIndividual)parents[1]).trees[t2]);

                    // check for depth and swap-compatibility limits
                    res1 = verifyPoints(initializer,p2,p1);  // p2 can fill p1's spot -- order is important!
                    if (n-(q-start)<2) res2 = true;
                    else res2 = verifyPoints(initializer,p1,p2);  // p1 can fill p2's spot -- order is important!

                    // did we get something that had both nodes verified?
                    // we reject if EITHER of them is invalid.  This is what lil-gp does.
                    // Koza only has numTries set to 1, so it's compatible as well.
                    if (res1 && res2) break;
                }
                // Create some new individuals based on the old ones -- since
                // GPTree doesn't deep-clone, this should be just fine.  Perhaps we
                // should change this to proto off of the main species prototype, but
                // we have to then copy so much stuff over; it's not worth it.

                GPIndividual j1 = (GPIndividual)(((GPIndividual)parents[0]).lightClone());
//                GPIndividual j2 = null;
                GPIndividual j2 = (GPIndividual)(((GPIndividual)parents[1]).lightClone());
//                if (n-(q-start)>=2) j2 = (GPIndividual)(((GPIndividual)parents[1]).lightClone());

                // Fill in various tree information that didn't get filled in there
                j1.trees = new GPTree[((GPIndividual)parents[0]).trees.length];
                if (n-(q-start)>=2) j2.trees = new GPTree[((GPIndividual)parents[1]).trees.length];

                // at this point, p1 or p2, or both, may be null.
                // If not, swap one in.  Else just copy the parent.

                for(int x=0;x<j1.trees.length;x++)
                {
                    if (x==t1 && res1)  // we've got a tree with a kicking cross position!
                    {
                        j1.trees[x] = (GPTree)(((GPIndividual)parents[0]).trees[x].lightClone());
                        j1.trees[x].owner = j1;
                        j1.trees[x].child = ((GPIndividual)parents[0]).trees[x].child.cloneReplacing(p2,p1); //p2 new, p1 old
                        j1.trees[x].child.parent = j1.trees[x];
                        j1.trees[x].child.argposition = 0;
                        j1.evaluated = false;
                    }  // it's changed
                    else
                    {
                        //modified by fzhang 7.6.2018  randomly choose one tree to do crossover. According to the setting, must be point crossover.
                        //after that, swap the other tree.
                        j1.trees[x] = (GPTree)(((GPIndividual)parents[1]).trees[x].lightClone());
                        j1.trees[x].owner = j1;
                        j1.trees[x].child = (GPNode)(((GPIndividual)parents[1]).trees[x].child.clone());
                        j1.trees[x].child.parent = j1.trees[x];
                        j1.trees[x].child.argposition = 0;
                    }
                }

                if (n - (q - start) >= 2)
                    for (int x = 0; x < j2.trees.length; x++) {
                        if (x == t2 && res2) // we've got a tree with a kicking cross position!
                        {
                            j2.trees[x] = (GPTree) (((GPIndividual)parents[1]).trees[x].lightClone());
                            j2.trees[x].owner = j2;
                            j2.trees[x].child = ((GPIndividual)parents[1]).trees[x].child.cloneReplacing(p1, p2);
                            j2.trees[x].child.parent = j2.trees[x];
                            j2.trees[x].child.argposition = 0;
                            j2.evaluated = false;
                        } // it's changed
                        else {
                            j2.trees[x] = (GPTree) (((GPIndividual)parents[0]).trees[x].lightClone());
                            j2.trees[x].owner = j2;
                            j2.trees[x].child = (GPNode) (((GPIndividual)parents[0]).trees[x].child.clone());
                            j2.trees[x].child.parent = j2.trees[x];
                            j2.trees[x].child.argposition = 0;
                        }
                    }


                offsprings[0] = j1;
                offsprings[1] = j2;

//                if (!tossSecondParent)
//                {
//                    offsprings[1] = j2;
//                }
            return offsprings;
        }


    public int produceNormalCrossoverBetweenEnsemblesStrategy1(final int min,
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

        EnsembleRule[] parentEnsemblesForCrossover = ((EnsembleEvaluatoreGP)state.evaluator).parentsForCrossover;

        EnsembleRule ensembleParent1 = parentEnsemblesForCrossover[0];
        EnsembleRule ensembleParent2 = parentEnsemblesForCrossover[1];

        List<Individual> allEnsembleOffspring1 = new ArrayList<>();
        List<Individual> allEnsembleOffspring2 = new ArrayList<>();

        int lengthParent1 = ensembleParent1.getEnsemble().size();
        int lengthParent2 = ensembleParent2.getEnsemble().size();

        int indexPointOfParent1 = state.random[thread].nextInt(lengthParent1);
        int indexPointOfParent2 = state.random[thread].nextInt(lengthParent2);

        Individual individualOfParent1 = (Individual)ensembleParent1.getEnsemble().get(indexPointOfParent1).clone();
        Individual individualOfParent2 = (Individual)ensembleParent2.getEnsemble().get(indexPointOfParent2).clone();

        Individual[] parentsFromEnsemble = new Individual[2];
        parentsFromEnsemble[0] = individualOfParent1;
        parentsFromEnsemble[1] = individualOfParent2;

        Individual[] offsprings = produceNormalCrossoverBetweenIndividuals(min, max, start, subpopulation, inds, state, thread, initializer, q, n, parentsFromEnsemble);

        for(int i=0; i<lengthParent1; i++){
            if(i == indexPointOfParent1){
                allEnsembleOffspring1.add(offsprings[1]); //cross the individual
            }
            else{
                allEnsembleOffspring1.add((Individual)ensembleParent1.getEnsemble().get(i).clone());
            }
        }

        for(int i=0; i<lengthParent2; i++){
            if(i == indexPointOfParent2){
                allEnsembleOffspring2.add(offsprings[0]); //cross the individual
            }
            else{
                allEnsembleOffspring2.add((Individual)ensembleParent2.getEnsemble().get(i).clone());
            }
        }

        EnsembleRule offspringEnsemble1 = new EnsembleRule(allEnsembleOffspring1, lengthParent1);
        EnsembleRule offspringEnsemble2 = new EnsembleRule(allEnsembleOffspring2, lengthParent2);

        ((EnsembleEvaluatoreGP)state.evaluator).ensembleOffspringPool.add(offspringEnsemble1);

        //todo: the following need to be modified

        // add the individuals to the population

        if(state instanceof GPRuleEvolutionStateeGP){//this is for lexicase selection modified by mengxu 2022.02.28
            int index = ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }


        //need to double check whether we should use the following two sentences
//        inds[q] = j1;
//        q++;
        if (q<n+start && !tossSecondParent)
        {
            ((EnsembleEvaluatoreGP)state.evaluator).ensembleOffspringPool.add(offspringEnsemble2);
            if(state instanceof GPRuleEvolutionStateeGP){//this is for lexicase selection modified by mengxu 2022.02.28
                int index = ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }

//            inds[q] = j2;
//            q++;
        }
        return q;
    }


    public int produceNormalCrossoverBetweenEnsemblesStrategy2(final int min,
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

        EnsembleRule[] parentEnsemblesForCrossover = ((EnsembleEvaluatoreGP)state.evaluator).parentsForCrossover;

        EnsembleRule ensembleParent1 = parentEnsemblesForCrossover[0];
        EnsembleRule ensembleParent2 = parentEnsemblesForCrossover[1];

        List<Individual> allEnsembleOffspring1 = new ArrayList<>();
        List<Individual> allEnsembleOffspring2 = new ArrayList<>();

        int lengthParent1 = ensembleParent1.getEnsemble().size();
        int lengthParent2 = ensembleParent2.getEnsemble().size();

        int indexPointOfParent1 = state.random[thread].nextInt(lengthParent1);
        int indexPointOfParent2 = state.random[thread].nextInt(lengthParent2);

        Individual individualOfParent1 = (Individual)ensembleParent1.getEnsemble().get(indexPointOfParent1).clone();
        Individual individualOfParent2 = (Individual)ensembleParent2.getEnsemble().get(indexPointOfParent2).clone();

        for(int i=0; i<lengthParent1; i++){
            if(i == indexPointOfParent1){
                allEnsembleOffspring1.add(individualOfParent2); //cross the individual
            }
            else{
                allEnsembleOffspring1.add((Individual)ensembleParent1.getEnsemble().get(i).clone());
            }
        }

        for(int i=0; i<lengthParent2; i++){
            if(i == indexPointOfParent2){
                allEnsembleOffspring2.add(individualOfParent1); //cross the individual
            }
            else{
                allEnsembleOffspring2.add((Individual)ensembleParent2.getEnsemble().get(i).clone());
            }
        }

        EnsembleRule offspringEnsemble1 = new EnsembleRule(allEnsembleOffspring1, lengthParent1);
        EnsembleRule offspringEnsemble2 = new EnsembleRule(allEnsembleOffspring2, lengthParent2);

        ((EnsembleEvaluatoreGP)state.evaluator).ensembleOffspringPool.add(offspringEnsemble1);

        //todo: the following need to be modified

        // add the individuals to the population

        if(state instanceof GPRuleEvolutionStateeGP){//this is for lexicase selection modified by mengxu 2022.02.28
            int index = ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }


        //need to double check whether we should use the following two sentences
//        inds[q] = j1;
//        q++;
        if (q<n+start && !tossSecondParent)
        {
            ((EnsembleEvaluatoreGP)state.evaluator).ensembleOffspringPool.add(offspringEnsemble2);
            if(state instanceof GPRuleEvolutionStateeGP){//this is for lexicase selection modified by mengxu 2022.02.28
                int index = ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
            }

//            inds[q] = j2;
//            q++;
        }
        return q;
    }

    public GPIndividual produceNormalCrossover(final int min,
                                    final int max,
                                    final int start,
                                    GPIndividual parent0,
                                    GPIndividual parent1,
                                    final int subpopulation,
                                    final EvolutionState state,
                                    final int thread,
                                    final GPInitializer initializer,
                                    int q, int n)

    {

        // at this point, parents[] contains our two selected individuals

        // are our tree values valid?
        if (tree1!=TREE_UNFIXED && (tree1<0 || tree1 >= parent0.trees.length))
            // uh oh
            state.output.fatal("GP Crossover Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");
        if (tree2!=TREE_UNFIXED && (tree2<0 || tree2 >= parent1.trees.length))
            // uh oh
            state.output.fatal("GP Crossover Pipeline attempted to fix tree.1 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");

        int t1=0; int t2=0;

        if (tree1==TREE_UNFIXED || tree2==TREE_UNFIXED)
        {
            do
            // pick random trees  -- their GPTreeConstraints must be the same
            {
                if (tree1==TREE_UNFIXED)

                    if (parent0.trees.length > 1)
                        t1 = state.random[thread].nextInt(parent0.trees.length);
                    else
                    {t1 = 0;
                        //System.out.println(parents[0].trees.length);
                    }
                else
                    t1 = tree1;

                if (tree2==TREE_UNFIXED)
                    if (parent1.trees.length>1)
                        t2 = state.random[thread].nextInt(parent1.trees.length);
                    else t2 = 0;
                else
                    t2 = tree2;
            } while (parent0.trees[t1].constraints(initializer) != parent1.trees[t2].constraints(initializer));
            //while (false);
        }
        else
        {
            t1 = tree1;
            t2 = tree2;
            // make sure the constraints are okay
            if (parent0.trees[t1].constraints(initializer)
                    != parent1.trees[t2].constraints(initializer)) // uh oh
                state.output.fatal("GP Crossover Pipeline's two tree choices are both specified by the user -- but their GPTreeConstraints are not the same");
        }

        // validity results...
        boolean res1 = false;
        boolean res2 = false;


        // prepare the nodeselectors
        nodeselect1.reset();
        nodeselect2.reset();


        // pick some nodes

        GPNode p1=null;
        GPNode p2=null;

        for(int x=0;x<numTries;x++)
        {
            // pick a node in individual 1
            p1 = nodeselect1.pickNode(state,subpopulation,thread,parent0,parent0.trees[t1]);

            // pick a node in individual 2
            p2 = nodeselect2.pickNode(state,subpopulation,thread,parent1,parent1.trees[t2]);

            // check for depth and swap-compatibility limits
            res1 = verifyPoints(initializer,p2,p1);  // p2 can fill p1's spot -- order is important!
            if (n-(q-start)<2 || tossSecondParent) res2 = true;
            else res2 = verifyPoints(initializer,p1,p2);  // p1 can fill p2's spot -- order is important!

            // did we get something that had both nodes verified?
            // we reject if EITHER of them is invalid.  This is what lil-gp does.
            // Koza only has numTries set to 1, so it's compatible as well.
            if (res1 && res2) break;
        }
        // Create some new individuals based on the old ones -- since
        // GPTree doesn't deep-clone, this should be just fine.  Perhaps we
        // should change this to proto off of the main species prototype, but
        // we have to then copy so much stuff over; it's not worth it.

        GPIndividual j1 = (GPIndividual)(parent0.lightClone());
        GPIndividual j2 = null;
        if (n-(q-start)>=2 && !tossSecondParent) j2 = (GPIndividual)(parent1.lightClone());

        // Fill in various tree information that didn't get filled in there
        j1.trees = new GPTree[parent0.trees.length];
        if (n-(q-start)>=2 && !tossSecondParent) j2.trees = new GPTree[parent1.trees.length];

        // at this point, p1 or p2, or both, may be null.
        // If not, swap one in.  Else just copy the parent.

        for(int x=0;x<j1.trees.length;x++)
        {
            if (x==t1 && res1)  // we've got a tree with a kicking cross position!
            {
                j1.trees[x] = (GPTree)(parent0.trees[x].lightClone());
                j1.trees[x].owner = j1;
                j1.trees[x].child = parent0.trees[x].child.cloneReplacing(p2,p1); //p2 new, p1 old
                j1.trees[x].child.parent = j1.trees[x];
                j1.trees[x].child.argposition = 0;
                j1.evaluated = false;
            }  // it's changed
            else
            {
                //modified by fzhang 7.6.2018  randomly choose one tree to do crossover. According to the setting, must be point crossover.
                //after that, swap the other tree.
                j1.trees[x] = (GPTree)(parent1.trees[x].lightClone());
                j1.trees[x].owner = j1;
                j1.trees[x].child = (GPNode)(parent1.trees[x].child.clone());
                j1.trees[x].child.parent = j1.trees[x];
                j1.trees[x].child.argposition = 0;
            }
        }

        if (n - (q - start) >= 2 && !tossSecondParent)
            for (int x = 0; x < j2.trees.length; x++) {
                if (x == t2 && res2) // we've got a tree with a kicking cross position!
                {
                    j2.trees[x] = (GPTree) (parent1.trees[x].lightClone());
                    j2.trees[x].owner = j2;
                    j2.trees[x].child = parent1.trees[x].child.cloneReplacing(p1, p2);
                    j2.trees[x].child.parent = j2.trees[x];
                    j2.trees[x].child.argposition = 0;
                    j2.evaluated = false;
                } // it's changed
                else {
                    j2.trees[x] = (GPTree) (parent0.trees[x].lightClone());
                    j2.trees[x].owner = j2;
                    j2.trees[x].child = (GPNode) (parent0.trees[x].child.clone());
                    j2.trees[x].child.parent = j2.trees[x];
                    j2.trees[x].child.argposition = 0;
                }
            }

        // add the individuals to the population

        if(state instanceof GPRuleEvolutionStateeGP){//this is for lexicase selection modified by mengxu 2022.02.28
            int index = ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.size()-1;
            ((GPRuleEvolutionStateeGP) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
        }

//        inds[q] = j1;
//        q++;
//        if (q<n+start && !tossSecondParent)
//        {
//            if(state instanceof GPRuleEvolutionStateEnsembleContribution){//this is for lexicase selection modified by mengxu 2022.02.28
//                int index = ((GPRuleEvolutionStateEnsembleContribution) state).parentsIndexPairForCrossover.size()-1;
//                ((GPRuleEvolutionStateEnsembleContribution) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
//            }
//
//            inds[q] = j2;
//            q++;
//        }
        return j1;
    }

}
