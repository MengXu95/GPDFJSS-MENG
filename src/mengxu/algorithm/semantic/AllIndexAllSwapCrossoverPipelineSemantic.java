package mengxu.algorithm.semantic;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPInitializer;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.gp.koza.CrossoverPipeline;
import mengxu.algorithm.averageFitness.GPRuleEvolutionStateAverage;
import mengxu.algorithm.clusterselection.multiplecasecluster.GPRuleEvolutionStateMultiCaseCluster;
import mengxu.algorithm.diversepartnerselection.GPRuleEvolutionStateDPS;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.GPRuleEvolutionStateDPSNS;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.focusOnPoorCase.GPRuleEvolutionStateDPSPC;
import mengxu.algorithm.ensemble.GPRuleEvolutionStateBaseline;
import mengxu.algorithm.ensemble.GPRuleEvolutionStateCluster;
import mengxu.algorithm.lexicaseselection.GPRuleEvolutionStateOneInstanceMultiCase;
import mengxu.algorithm.lexicaseselection.LSmultiInstance.GPRuleEvolutionStateMultiInstance;
import mengxu.algorithm.lexicaseselection.LSmultiReplication.GPRuleEvolutionStateMultiReplication;
import mengxu.algorithm.multicaseEnsemble.ensembleContribution.GPRuleEvolutionStateEnsembleContribution;
import mengxu.algorithm.multicasePareto.GPRuleEvolutionStateMCP;
import mengxu.algorithm.multiobjective.GPRuleEvolutionStateMO;
import mengxu.algorithm.multiobjective.MOEAD.GPRuleEvolutionStateMOEAD;
import mengxu.algorithm.multiobjective.MOEADAdaptiveWeightStrategy.GPRuleEvolutionStateMOEADAWS;
import mengxu.algorithm.multiobjective.MOEADarchive.GPRuleEvolutionStateMOEADarchive;
import mengxu.algorithm.multiobjective.MOEADepsilonC.GPRuleEvolutionStateMOEADeC;
import mengxu.algorithm.multiobjective.MOEADmap.GPRuleEvolutionStateMOEADmap;
import mengxu.algorithm.multiobjective.phenotypeNSGPII.improvedCompareOne.NSGA2EvaluatorNoEnvironmentalSelectionPhenotypeBreedingSimilarityChecking;
import mengxu.algorithm.semanticTournamentSelection.GPRuleEvolutionStateMCSTS;

import java.util.List;

/**
 * Created by fzhang on 26.05.2018.
 */
public class AllIndexAllSwapCrossoverPipelineSemantic extends CrossoverPipeline {
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

        //add by mengxu 2023.06.06
        boolean nonduplicate_j1 = false;
        boolean nonduplicate_j2 = false;

        boolean similarWithParent_j1 = false;
        boolean similarWithParent_j2 = false;
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
            // grab two individuals from our sources
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

            //2021.10.14 to find the difference with Tournament selection
//            if(state instanceof GPRuleEvolutionStateMV0){
//                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(parents[0].fitness.fitness(), parents[1].fitness.fitness(), q, -1);
//                ((GPRuleEvolutionStateMV0) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
//            }
//            else if(state instanceof GPRuleEvolutionStateMOLS){
//                ParentFairForCrossoverMO parentFairForCrossover = new ParentFairForCrossoverMO(((MultiObjectiveFitness)parents[0].fitness).objectives, ((MultiObjectiveFitness)parents[1].fitness).objectives, q, null);
//                ((GPRuleEvolutionStateMOLS) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
//            }
//            else if(state instanceof GPRuleEvolutionStateCluster){
//                ParentFairForCrossover parentFairForCrossover = new ParentFairForCrossover(parents[0].fitness.fitness(), parents[1].fitness.fitness(), q, -1);
//                ((GPRuleEvolutionStateCluster) state).parentsIndexPairForCrossover.add(parentFairForCrossover);
//            }

            // at this point, parents[] contains our two selected individuals

            // are our tree values valid?
            if (tree1!=TREE_UNFIXED && (tree1<0 || tree1 >= parents[0].trees.length))
                // uh oh
                state.output.fatal("GP Crossover Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");
            if (tree2!=TREE_UNFIXED && (tree2<0 || tree2 >= parents[1].trees.length))
                // uh oh
                state.output.fatal("GP Crossover Pipeline attempted to fix tree.1 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");

            int t1=0; int t2=0;
        	//System.out.println("The first one: "+parents[0].trees[t1].constraints(initializer));
           // System.out.println("The second one: "+parents[1].trees[t2].constraints(initializer));
           // System.out.println(parents[0].trees.length); //1
          //  System.out.println(parents[1].trees.length);
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
            if(state instanceof GPRuleEvolutionStateEnsembleContribution){//this is for lexicase selection modified by mengxu 2022.02.28
                int index = ((GPRuleEvolutionStateEnsembleContribution) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateEnsembleContribution) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
            }

            //add by mengxu 2023.06.22
            double randPro = state.random[0].nextDouble();
            if(((GPRuleEvolutionStateSemantic)state).generation > ((GPRuleEvolutionStateSemantic)state).generationForDoPCMap) {
                if(randPro < ((GPRuleEvolutionStateSemantic)state).limitPortsDoLocalSearch){
                    boolean acceptLocalSearchSeq_j1 = false;
                    boolean acceptLocalSearchRout_j1 = false;

                    List<double[]> PC_j1 = ((GPRuleEvolutionStateSemantic) state).calculatePC(j1);
                    double[] sequencingPC_j1 = PC_j1.get(0);
                    double[] routingPC_j1 = PC_j1.get(1);

                    if(((GPRuleEvolutionStateSemantic)state).timesDoLocalSearch > ((GPRuleEvolutionStateSemantic)state).limitTimesDoLocalSearch){
                        ((GPRuleEvolutionStateSemantic) state).doSequencingLocalSearch = false;
                        ((GPRuleEvolutionStateSemantic) state).doRoutingLocalSearch = false;
                    }

                    if (((GPRuleEvolutionStateSemantic) state).doSequencingLocalSearch) {
                        acceptLocalSearchSeq_j1 = ((GPRuleEvolutionStateSemantic) state).PCMap.localSearchAcceptSequencing(sequencingPC_j1, ((GPRuleEvolutionStateSemantic) state).localSearchAcceptDis);
                    }
                    if (((GPRuleEvolutionStateSemantic) state).doRoutingLocalSearch) {
                        acceptLocalSearchRout_j1 = ((GPRuleEvolutionStateSemantic) state).PCMap.localSearchAcceptRouting(routingPC_j1, ((GPRuleEvolutionStateSemantic) state).localSearchAcceptDis);
                    }

                    if ((((GPRuleEvolutionStateSemantic) state).doSequencingLocalSearch && acceptLocalSearchSeq_j1) || (((GPRuleEvolutionStateSemantic) state).doRoutingLocalSearch && acceptLocalSearchRout_j1))
                    {
                        inds[q] = j1;
                        ((GPRuleEvolutionStateSemantic)state).offspringIndexByLocalSearch.add(q); //add by mengxu 2023.07.06
                        q++;
                        ((GPRuleEvolutionStateSemantic)state).timesDoLocalSearch++;
                    }
                    else{
                        n--;
                    }
                }
                else if(randPro >= ((GPRuleEvolutionStateSemantic)state).limitPortsDoLocalSearch && randPro < ((GPRuleEvolutionStateSemantic)state).limitPortsDoLocalSearch + ((GPRuleEvolutionStateSemantic)state).limitPortsDoExploration){
                    boolean acceptExploreSeq_j1 = false;
                    boolean acceptExploreRout_j1 = false;
                    List<double[]> PC_j1 = ((GPRuleEvolutionStateSemantic) state).calculatePC(j1);
                    double[] sequencingPC_j1 = PC_j1.get(0);
                    double[] routingPC_j1 = PC_j1.get(1);

                    if(((GPRuleEvolutionStateSemantic)state).timesDoExploration > ((GPRuleEvolutionStateSemantic)state).limitTimesDoExploration){
                        ((GPRuleEvolutionStateSemantic) state).doSequencingExplore = false;
                        ((GPRuleEvolutionStateSemantic) state).doRoutingExplore = false;
                    }

                    if (((GPRuleEvolutionStateSemantic) state).doSequencingExplore) {
                        acceptExploreSeq_j1 = ((GPRuleEvolutionStateSemantic) state).PCMap.exploreAcceptSequencing(sequencingPC_j1, ((GPRuleEvolutionStateSemantic) state).exploreAcceptDis);
                    }
                    if (((GPRuleEvolutionStateSemantic) state).doRoutingExplore) {
                        acceptExploreRout_j1 = ((GPRuleEvolutionStateSemantic) state).PCMap.exploreAcceptRouting(routingPC_j1, ((GPRuleEvolutionStateSemantic) state).exploreAcceptDis);
                    }

                    if ((acceptExploreSeq_j1 || acceptExploreRout_j1)) {
                        inds[q] = j1;
                        ((GPRuleEvolutionStateSemantic)state).offspringIndexByExploration.add(q); //add by mengxu 2023.07.06
                        q++;
                        ((GPRuleEvolutionStateSemantic)state).timesDoExploration++;
                    }
                    else{
                        n--;
                    }
                }
                else{
                    inds[q] = j1;
                    ((GPRuleEvolutionStateSemantic)state).offspringIndexByNormalEvolution.add(q); //add by mengxu 2023.07.06
                    q++;
                }
            }
            else{
                inds[q] = j1;
                ((GPRuleEvolutionStateSemantic)state).offspringIndexByNormalEvolution.add(q); //add by mengxu 2023.07.06
                q++;
            }


//            if(((GPRuleEvolutionStateSemantic)state).doSequencingLocalSearch && randPro_j1 < ((GPRuleEvolutionStateSemantic)state).proForLocalSearchAndExplore){
//                List<double[]> PC_j1 = ((GPRuleEvolutionStateSemantic)state).calculatePC(j1);
//                double[] sequencingPC_j1 = PC_j1.get(0);
//                double[] routingPC_j1 = PC_j1.get(1);
//                boolean acceptLocalSearchSeq_j1 = ((GPRuleEvolutionStateSemantic)state).PCMap.localSearchAcceptSequencing(sequencingPC_j1, ((GPRuleEvolutionStateSemantic)state).localSearchAcceptDis);
//                boolean acceptLocalSearchRout_j1 = ((GPRuleEvolutionStateSemantic)state).PCMap.localSearchAcceptRouting(routingPC_j1, ((GPRuleEvolutionStateSemantic)state).localSearchAcceptDis);
//                boolean acceptExploreSeq_j1 = ((GPRuleEvolutionStateSemantic)state).PCMap.exploreAcceptSequencing(sequencingPC_j1, ((GPRuleEvolutionStateSemantic)state).exploreAcceptDis);
//                boolean acceptExploreRout_j1 = ((GPRuleEvolutionStateSemantic)state).PCMap.exploreAcceptRouting(routingPC_j1, ((GPRuleEvolutionStateSemantic)state).exploreAcceptDis);
//                if(acceptLocalSearchSeq_j1 || acceptLocalSearchRout_j1 || acceptExploreSeq_j1 || acceptExploreRout_j1){
//                    inds[q] = j1;
//                    q++;
//                }
//                else{
//                    n--;
//                }
//            }else{
//                inds[q] = j1;
//                q++;
//            }

            //original
//            inds[q] = j1;
//            q++;


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
                if(state instanceof GPRuleEvolutionStateEnsembleContribution){//this is for lexicase selection modified by mengxu 2022.02.28
                    int index = ((GPRuleEvolutionStateEnsembleContribution) state).parentsIndexPairForCrossover.size()-1;
                    ((GPRuleEvolutionStateEnsembleContribution) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
                }

//                double randPro_j2 = state.random[0].nextDouble();
                if(((GPRuleEvolutionStateSemantic)state).generation > ((GPRuleEvolutionStateSemantic)state).generationForDoPCMap){
                    if(randPro < ((GPRuleEvolutionStateSemantic)state).limitPortsDoLocalSearch){
//                    if(randPro_j2 < ((GPRuleEvolutionStateSemantic)state).proForLocalSearchAndExplore){
                        boolean acceptLocalSearchSeq_j2 = false;
                        boolean acceptLocalSearchRout_j2 = false;

                        List<double[]> PC_j2 = ((GPRuleEvolutionStateSemantic)state).calculatePC(j2);
                        double[] sequencingPC_j2 = PC_j2.get(0);
                        double[] routingPC_j2 = PC_j2.get(1);

                        if(((GPRuleEvolutionStateSemantic)state).timesDoLocalSearch > ((GPRuleEvolutionStateSemantic)state).limitTimesDoLocalSearch){
                            ((GPRuleEvolutionStateSemantic) state).doSequencingLocalSearch = false;
                            ((GPRuleEvolutionStateSemantic) state).doRoutingLocalSearch = false;
                        }

                        if(((GPRuleEvolutionStateSemantic)state).doSequencingLocalSearch) {
                            acceptLocalSearchSeq_j2 = ((GPRuleEvolutionStateSemantic) state).PCMap.localSearchAcceptSequencing(sequencingPC_j2, ((GPRuleEvolutionStateSemantic) state).localSearchAcceptDis);
                        }
                        if(((GPRuleEvolutionStateSemantic)state).doRoutingLocalSearch) {
                            acceptLocalSearchRout_j2 = ((GPRuleEvolutionStateSemantic)state).PCMap.localSearchAcceptRouting(routingPC_j2, ((GPRuleEvolutionStateSemantic)state).localSearchAcceptDis);
                        }

//                        if(acceptLocalSearchSeq_j2 && acceptLocalSearchRout_j2){
                        if ((((GPRuleEvolutionStateSemantic) state).doSequencingLocalSearch && acceptLocalSearchSeq_j2) || (((GPRuleEvolutionStateSemantic) state).doRoutingLocalSearch && acceptLocalSearchRout_j2))
                        {
                            inds[q] = j2;
                            ((GPRuleEvolutionStateSemantic)state).offspringIndexByLocalSearch.add(q); //add by mengxu 2023.07.06
                            q++;
                            ((GPRuleEvolutionStateSemantic)state).timesDoLocalSearch++;
                        }
                        else{
                            n--;
                        }
                    }
                    else if(randPro >= ((GPRuleEvolutionStateSemantic)state).limitPortsDoLocalSearch && randPro < ((GPRuleEvolutionStateSemantic)state).limitPortsDoLocalSearch + ((GPRuleEvolutionStateSemantic)state).limitPortsDoExploration){
//                    if(randPro_j2 < ((GPRuleEvolutionStateSemantic)state).proForLocalSearchAndExplore){
                        boolean acceptExploreSeq_j2 = false;
                        boolean acceptExploreRout_j2 = false;
                        List<double[]> PC_j2 = ((GPRuleEvolutionStateSemantic)state).calculatePC(j2);
                        double[] sequencingPC_j2 = PC_j2.get(0);
                        double[] routingPC_j2 = PC_j2.get(1);


                        if(((GPRuleEvolutionStateSemantic)state).timesDoExploration > ((GPRuleEvolutionStateSemantic)state).limitTimesDoExploration){
                            ((GPRuleEvolutionStateSemantic) state).doSequencingExplore = false;
                            ((GPRuleEvolutionStateSemantic) state).doRoutingExplore = false;
                        }

                        if(((GPRuleEvolutionStateSemantic)state).doSequencingExplore) {
                            acceptExploreSeq_j2 = ((GPRuleEvolutionStateSemantic)state).PCMap.exploreAcceptSequencing(sequencingPC_j2, ((GPRuleEvolutionStateSemantic)state).exploreAcceptDis);
                        }
                        if(((GPRuleEvolutionStateSemantic)state).doRoutingExplore) {
                            acceptExploreRout_j2 = ((GPRuleEvolutionStateSemantic)state).PCMap.exploreAcceptRouting(routingPC_j2, ((GPRuleEvolutionStateSemantic)state).exploreAcceptDis);
                        }

                        if(acceptExploreSeq_j2 || acceptExploreRout_j2){
                            inds[q] = j2;
                            ((GPRuleEvolutionStateSemantic)state).offspringIndexByExploration.add(q); //add by mengxu 2023.07.06
                            q++;
                            ((GPRuleEvolutionStateSemantic)state).timesDoExploration++;
                        }
                        else{
                            n--;
                        }
                    }
                    else{
                        inds[q] = j2;
                        ((GPRuleEvolutionStateSemantic)state).offspringIndexByNormalEvolution.add(q); //add by mengxu 2023.07.06
                        q++;
                    }
                }
                else{
                    inds[q] = j2;
                    ((GPRuleEvolutionStateSemantic)state).offspringIndexByNormalEvolution.add(q); //add by mengxu 2023.07.06
                    q++;
                }


                //add by mengxu 2023.06.22
//                double randPro_j2 = state.random[0].nextDouble();
//                if(((GPRuleEvolutionStateSemantic)state).doSequencingLocalSearch && randPro_j2 < ((GPRuleEvolutionStateSemantic)state).proForLocalSearchAndExplore){
//                    List<double[]> PC_j2 = ((GPRuleEvolutionStateSemantic)state).calculatePC(j2);
//                    double[] sequencingPC_j2 = PC_j2.get(0);
//                    double[] routingPC_j2 = PC_j2.get(1);
//                    boolean acceptLocalSearchSeq_j2 = ((GPRuleEvolutionStateSemantic)state).PCMap.localSearchAcceptSequencing(sequencingPC_j2, ((GPRuleEvolutionStateSemantic)state).localSearchAcceptDis);
//                    boolean acceptLocalSearchRout_j2 = ((GPRuleEvolutionStateSemantic)state).PCMap.localSearchAcceptRouting(routingPC_j2, ((GPRuleEvolutionStateSemantic)state).localSearchAcceptDis);
//                    boolean acceptExploreSeq_j2 = ((GPRuleEvolutionStateSemantic)state).PCMap.exploreAcceptSequencing(sequencingPC_j2, ((GPRuleEvolutionStateSemantic)state).exploreAcceptDis);
//                    boolean acceptExploreRout_j2 = ((GPRuleEvolutionStateSemantic)state).PCMap.exploreAcceptRouting(routingPC_j2, ((GPRuleEvolutionStateSemantic)state).exploreAcceptDis);
//                    if(acceptLocalSearchSeq_j2 || acceptLocalSearchRout_j2 || acceptExploreSeq_j2 || acceptExploreRout_j2){
//                        inds[q] = j2;
//                        q++;
//                    }
//                    else{
//                        n--;
//                    }
//                }else{
//                    inds[q] = j2;
//                    q++;
//                }

                //original
//                inds[q] = j2;
//                q++;
                }
            }
        //add by mengxu 2023.06.06
//        if(nonduplicate_j1 && nonduplicate_j2){
//            return n;
//        }
//        else if(nonduplicate_j1 || nonduplicate_j2){
//            return n-1;
//        }
//        else{
//            return n-2;
//        }
        //original
        return n;
        }

}
