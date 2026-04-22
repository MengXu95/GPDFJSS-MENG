package mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNSurrogate.OffspringSelectionStrategyForTopTwoCrossover;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPInitializer;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.gp.koza.CrossoverPipeline;
import ec.util.Parameter;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNSurrogate.OneInstanceMultiCaseMultiObjectiveFitnessDPS;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.phenotypicForSurrogateV1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Created by fzhang on 26.05.2018.
 */
public class AllIndexAllSwapCrossoverPipelineWithNSBROffspringSelectionTop2 extends CrossoverPipeline {
    /**
     * Overrides the normal crossover pipeline to use all pairs of the SAME TREE INDEX in each individual when performing crossover. This is necessary where the tree locations ~mean something~.
     * <p>
     * Unfortunately, this is an ugly extension due to the parent class design, but the changed parts from the parent can be found between the >>>>>>>START<<<<<<< and >>>>>>>>END<<<<<<<< tags.
     */
	
	//everytime, randomly choose one tree to do crossover. According to the setting, must be point crossover.
	//after that, swap the other tree. 
	//finally, get the two new offspring

    public static final String P_NUM_NODES = "num-nodes";
    public int numNodes;

    public static final String P_DISTANCE_BB = "distance-BB";
    public boolean distanceBB;

    public void setup(final EvolutionState state, final Parameter base)
    {
        super.setup(state,base);
        numNodes = state.parameters.getInt(new Parameter(P_NUM_NODES), null, 1);
        distanceBB = state.parameters.getBoolean(new Parameter(P_DISTANCE_BB), null, false);
    }
	
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
                sources[0].produce(2,2,0,subpopulation,parents,state,thread);  //max start subpopulation
           /* Produces <i>n</i> individuals from the given subpopulation
            and puts them into inds[start...start+n-1]*/
            else // grab from different sources
                {
                sources[0].produce(1,1,0,subpopulation,parents,state,thread);
                sources[1].produce(1,1,1,subpopulation,parents,state,thread);
                }



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

            //modified by mengxu 2021.12.29 to pick a list of nodes and verify each nodes

            List<GPNode> p1_list=null;
            List<GPNode> p2_list=null;
//            int numNodes = 5;
            List<GPNode[]> GPNodePairs = new ArrayList<>();
            List<GPNode[]> selectedGPNodePairs = new ArrayList<>();

            for(int x=0;x<numTries;x++)
            {
                // pick a node in individual 1
                p1_list = nodeselect1.pickNodes(state,subpopulation,thread,parents[0],parents[0].trees[t1], numNodes);

                // pick a node in individual 2
                p2_list = nodeselect2.pickNodes(state,subpopulation,thread,parents[1],parents[1].trees[t2], numNodes);
                //todo:need modify (numNodes could > all number of nodes in the tree)
                // save the verify results
//                boolean[] verifies = {false,false,false,false,false};

                for(int i=0; i<p1_list.size(); i++){
                    for(int j=0; j<p2_list.size(); j++){
                        GPNode p1 = p1_list.get(i);
                        GPNode p2 = p2_list.get(j);
                        GPNode[] pair = new GPNode[2];
                        pair[0] = p1;
                        pair[1] = p2;
                        GPNodePairs.add(pair);
                    }
                }

                int pairPermSize = p1_list.size() * p2_list.size();
                int[] permutation = new int[pairPermSize];
                MOEADUtils.randomPermutation(state, thread, permutation, pairPermSize);

                for(int i=0; i<permutation.length; i++){
                    GPNode[] pair = GPNodePairs.get(i);
                    GPNode p1 = pair[0];
                    GPNode p2 = pair[1];

                    // check for depth and swap-compatibility limits
                    res1 = verifyPoints(initializer,p2,p1);  // p2 can fill p1's spot -- order is important!
                    if (n-(q-start)<2 || tossSecondParent) res2 = true;
                    else res2 = verifyPoints(initializer,p1,p2);  // p1 can fill p2's spot -- order is important!

                    // did we get something that had both nodes verified?
                    // we reject if EITHER of them is invalid.  This is what lil-gp does.
                    // Koza only has numTries set to 1, so it's compatible as well.
                    if (res1 && res2){
                        selectedGPNodePairs.add(pair);
//                        if(selectedGPNodePairs.size() == numNodes){
//                            break;
//                        }
                    }
                }
            }

            // Create some new individuals based on the old ones -- since
            // GPTree doesn't deep-clone, this should be just fine.  Perhaps we
            // should change this to proto off of the main species prototype, but
            // we have to then copy so much stuff over; it's not worth it.
//            GPIndividual[] offsprings = new GPIndividual[selectedGPNodePairs.size()*2];
            List<Individual> offsprings = new ArrayList<>();

            int pairPermSize = selectedGPNodePairs.size();
            int[] permutation = new int[pairPermSize];
            MOEADUtils.randomPermutation(state, thread, permutation, pairPermSize);

//            if (selectedGPNodePairs.size()<numNodes){
//                System.out.println("GPNodePairs Size: " + selectedGPNodePairs.size());
//            }
            for(int i=0; i<numNodes && i<selectedGPNodePairs.size(); i++){
                int index = permutation[i];
                GPNode[] pair = selectedGPNodePairs.get(index);
                GPNode p1 = pair[0];
                GPNode p2 = pair[1];

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
                offsprings.add(j1);
                if (n-(q-start)>=2 && !tossSecondParent){
                    offsprings.add(j2);
                }
//                offsprings[i*2] = j1;
//                if (q<n+start && !tossSecondParent){
//                    offsprings[i*2+1] = j2;
//                }
//                offsprings[i*2+1] = j2;
            }

            //todo: seems wrong here, double check 2024.3.28
            //evaluate all the offsprings based on KNN surrogate model and get the best two individuals to inherit to the next generation
            double[][] PCOffspring = evaluateOffspring(state, ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).indsCharListsMultiTreeGen, ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).fitnessesForModelGen, offsprings);


            //            Individual[] off = offsprings.toArray(new Individual[offsprings.size()]);
//            if(state.evaluator.p_problem instanceof MultipleTreeRuleOptimizationProblem){
//                AbstractEvaluationModel surrogate = ((MultipleTreeRuleOptimizationProblem) state.evaluator.p_problem).getEvaluationModel();
//                if(surrogate instanceof KNNOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel){
//                    ((KNNOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel) surrogate).useSurrogate();
//                    ((KNNOneInstanceMultiCaseMultipleRuleHetegeneousEvaluationModel) surrogate).evaluate(state, off);
//                }
//            }

            //original========
//            Individual[] off = offsprings.toArray(new Individual[offsprings.size()]);
//            sortOffspringBasedOnFitnessWithPC(off,PCOffspring);
////            clearOffspring(state, PCOffspring, off, 0, 1); //add by mengxu 2022.03.24
////            clearOffspring(state, PCOffspring, off, 0, 1); //add by mengxu 2022.03.24
////            sortOffspringBasedOnFitnessWithPC(off,PCOffspring);
//            boolean[] duplication = new boolean[off.length];
//            checkDuplicatedBestFitWithExistingOffspringByCrossoverInNewPop(state, PCOffspring, off, duplication);
//            ArrayList<Individual> selectedTopTwo = selectTopTwoOffspring(state, off, PCOffspring, duplication, n);
             //original========

            Individual[] off = offsprings.toArray(new Individual[offsprings.size()]);
            sortOffspringBasedOnFitnessWithPC(off,PCOffspring);
            clearOffspring(state, PCOffspring, off, 0, 1); //add by mengxu 2022.03.24
            sortOffspringBasedOnFitnessWithPC(off,PCOffspring);
            boolean[] duplication = new boolean[off.length];
            checkDuplicatedBestFitWithExistingOffspringByCrossoverInNewPop(state, PCOffspring, off, duplication);
//            sortOffspringBasedOnFitnessWithPCAndDup(off,PCOffspring,duplication);
            ArrayList<Individual> selectedTopTwo = selectTopTwoOffspring(state, off, PCOffspring, duplication, n);
//            multiCaseFitnessNormalisationForOffspring(state, off);
//            calculateAllBenefitScore(parents[0], parents[1], off);
//            sortOffspringBasedOnBenefitScoreWithPC(off, PCOffspring);

//            sortOffspringBasedOnBenefitScore(off);

            // add the individuals to the population

            //2021.10.14 to find the difference with Tournament selection
//            if(state instanceof GPRuleEvolutionStateDPSNSBR){
//                int index = ((GPRuleEvolutionStateDPSNSBR) state).parentsIndexPairForCrossover.size()-1;
//                ((GPRuleEvolutionStateDPSNSBR) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
//            }
//            else if(state instanceof GPRuleEvolutionStateDPSNS){
//                int index = ((GPRuleEvolutionStateDPSNS) state).parentsIndexPairForCrossover.size()-1;
//                ((GPRuleEvolutionStateDPSNS) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
//            }
    
            inds[q] = selectedTopTwo.get(0);
            if(state instanceof GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy){
                int index = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy) state).parentsIndexPairForCrossover.size()-1;
                ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy) state).parentsIndexPairForCrossover.get(index).setChild1ID(q);
            }
            q++;
            if (q<n+start && !tossSecondParent)
                {

                if(state instanceof GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy){
                    int index = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy) state).parentsIndexPairForCrossover.size()-1;
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy) state).parentsIndexPairForCrossover.get(index).setChild2ID(q);
                }


                inds[q] = selectedTopTwo.get(1);
                q++;
                }
            }
        return n;
    }

    public ArrayList<Individual> selectTopTwoOffspring(EvolutionState state, Individual[] off, double[][] PCOffspring,
                                                       boolean[] duplication, int numOffRequired){
        double probabilityForUsingMeanCaseFitness = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).probabilityForUsingMeanCaseFitness;
        ArrayList<Individual> nonDupIndividuals = new ArrayList<>();
        ArrayList<Individual> dupIndividuals = new ArrayList<>();
        ArrayList<double[]> nonDupPCs = new ArrayList<>();
        ArrayList<double[]> dupPCs = new ArrayList<>();
        OneInstanceMultiCaseMultiObjectiveFitness fitness0 = (OneInstanceMultiCaseMultiObjectiveFitness)off[0].fitness;
        double[][] objectives0 = fitness0.multiInstanceMultiObjectiveFitness;
        int numCase = objectives0.length;
        int[] perm = new int[numCase];
        MOEADUtils.randomPermutation(state, 0, perm, numCase);
        for(int i=0; i<off.length; i++){
            if(!duplication[i]){
                nonDupIndividuals.add(off[i]);
                nonDupPCs.add(PCOffspring[i]);
//                if(off[i].fitness.fitness()>=Double.MAX_VALUE || off[i].fitness.fitness()>=Double.POSITIVE_INFINITY){
//                    continue;
//                }
//                else{
//                    nonDupIndividuals.add(off[i]);
//                    nonDupPCs.add(PCOffspring[i]);
//                }
            }
            else{
                dupIndividuals.add(off[i]);
                dupPCs.add(PCOffspring[i]);
//                if(off[i].fitness.fitness()>=Double.MAX_VALUE || off[i].fitness.fitness()>=Double.POSITIVE_INFINITY){
//                    continue;
//                }
//                else{
//                    dupIndividuals.add(off[i]);
//                    dupPCs.add(PCOffspring[i]);
//                }
            }
        }
        Individual[] nonDup_off = nonDupIndividuals.toArray(new Individual[nonDupIndividuals.size()]);
        Individual[] dup_off = dupIndividuals.toArray(new Individual[dupIndividuals.size()]);


        if(nonDupIndividuals.size()==numOffRequired){
            ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.addAll(nonDupPCs);
            ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.addAll(nonDupIndividuals);
            return nonDupIndividuals;
        }
        else if(nonDupIndividuals.size()<numOffRequired){
            ArrayList<Individual> selectedTopTwo = new ArrayList<>();
            selectedTopTwo.addAll(nonDupIndividuals);
            ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.addAll(nonDupPCs);
            ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.addAll(nonDupIndividuals);
            int addIndex = 0;
            int permIndex = 0;
            while(selectedTopTwo.size()<numOffRequired){
                double random = state.random[0].nextDouble();
                if(random < probabilityForUsingMeanCaseFitness){
                    selectedTopTwo.add(dup_off[addIndex]);
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(dupPCs.get(addIndex));
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(dupIndividuals.get(addIndex));
                    addIndex++;
                }
                else{
                    //Strategy 2: add expert individual based on the medium
//                    permIndex++;
                    double best_caseFit = ((OneInstanceMultiCaseMultiObjectiveFitness)dup_off[0].fitness).fitnessMedian();
                    int best_index = 0;
                    for(int i=1; i<dup_off.length; i++){
                        OneInstanceMultiCaseMultiObjectiveFitness fit_dup_off_i = (OneInstanceMultiCaseMultiObjectiveFitness)dup_off[i].fitness;
                        double caseFit_dup_off_i = fit_dup_off_i.fitnessMedian();
                        if(caseFit_dup_off_i < best_caseFit){
                            best_caseFit = caseFit_dup_off_i;
                            best_index = i;
                        }
                    }
                    selectedTopTwo.add(dup_off[best_index]);
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(dupPCs.get(best_index));
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(dupIndividuals.get(best_index));

//                    //Strategy 1: add expert individual based on a random case
//                    int random_case = perm[permIndex];
//                    permIndex++;
//                    double best_caseFit = Double.POSITIVE_INFINITY;
//                    int best_index =-1;
//                    for(int i=0; i<dup_off.length; i++){
//                        OneInstanceMultiCaseMultiObjectiveFitness fit_dup_off_i = (OneInstanceMultiCaseMultiObjectiveFitness)dup_off[i].fitness;
//                        double caseFit_dup_off_i = fit_dup_off_i.multiInstanceMultiObjectiveFitness[random_case][0];
//                        if(caseFit_dup_off_i < best_caseFit){
//                            best_caseFit = caseFit_dup_off_i;
//                            best_index = i;
//                        }
//                    }
//                    selectedTopTwo.add(dup_off[best_index]);
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(dupPCs.get(best_index));
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(dupIndividuals.get(best_index));
                }
            }
            return selectedTopTwo;
        }
        else{
            ArrayList<Individual> selectedTopTwo = new ArrayList<>();
            int addIndex = 0;
            int permIndex = 0;
            while(selectedTopTwo.size()<numOffRequired){
                double random = state.random[0].nextDouble();
                if(random < probabilityForUsingMeanCaseFitness){
                    selectedTopTwo.add(nonDup_off[addIndex]);
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(nonDupPCs.get(addIndex));
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(nonDupIndividuals.get(addIndex));
                    addIndex++;
                }
                else{
                    //add expert individual based on a random case
                    int random_case = perm[permIndex];
                    permIndex++;
                    double best_caseFit = Double.POSITIVE_INFINITY;
                    int best_index = -1;
                    for(int i=0; i<nonDup_off.length; i++){
                        OneInstanceMultiCaseMultiObjectiveFitness fit_non_dup_off_i = (OneInstanceMultiCaseMultiObjectiveFitness)nonDup_off[i].fitness;
                        double caseFit_dup_off_i = fit_non_dup_off_i.multiInstanceMultiObjectiveFitness[random_case][0];
                        if(caseFit_dup_off_i < best_caseFit){
                            best_caseFit = caseFit_dup_off_i;
                            best_index = i;
                        }
                    }
                    selectedTopTwo.add(nonDup_off[best_index]);
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(nonDupPCs.get(best_index));
                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(nonDupIndividuals.get(best_index));
                }
            }
            return selectedTopTwo;
        }
    }

//    public ArrayList<Individual> selectTopTwoOffspring(EvolutionState state, Individual[] off, double[][] PCOffspring,
//                                                       boolean[] duplication, int numOffRequired){
//        double probabilityForUsingMeanCaseFitness = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).probabilityForUsingMeanCaseFitness;
//        ArrayList<Individual> nonDupIndividuals = new ArrayList<>();
//        ArrayList<Individual> dupIndividuals = new ArrayList<>();
//        ArrayList<double[]> nonDupPCs = new ArrayList<>();
//        ArrayList<double[]> dupPCs = new ArrayList<>();
//        OneInstanceMultiCaseMultiObjectiveFitness fitness0 = (OneInstanceMultiCaseMultiObjectiveFitness)off[0].fitness;
//        double[][] objectives0 = fitness0.multiInstanceMultiObjectiveFitness;
//        int numCase = objectives0.length;
//        int[] perm = new int[numCase];
//        MOEADUtils.randomPermutation(state, 0, perm, numCase);
//        for(int i=0; i<off.length; i++){
//            if(!duplication[i]){
//                nonDupIndividuals.add(off[i]);
//                nonDupPCs.add(PCOffspring[i]);
////                if(off[i].fitness.fitness()>=Double.MAX_VALUE || off[i].fitness.fitness()>=Double.POSITIVE_INFINITY){
////                    continue;
////                }
////                else{
////                    nonDupIndividuals.add(off[i]);
////                    nonDupPCs.add(PCOffspring[i]);
////                }
//            }
//            else{
//                dupIndividuals.add(off[i]);
//                dupPCs.add(PCOffspring[i]);
////                if(off[i].fitness.fitness()>=Double.MAX_VALUE || off[i].fitness.fitness()>=Double.POSITIVE_INFINITY){
////                    continue;
////                }
////                else{
////                    dupIndividuals.add(off[i]);
////                    dupPCs.add(PCOffspring[i]);
////                }
//            }
//        }
//        Individual[] nonDup_off = nonDupIndividuals.toArray(new Individual[nonDupIndividuals.size()]);
//        Individual[] dup_off = dupIndividuals.toArray(new Individual[dupIndividuals.size()]);
//
//
//        if(nonDupIndividuals.size()==numOffRequired){
//            ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.addAll(nonDupPCs);
//            ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.addAll(nonDupIndividuals);
//            return nonDupIndividuals;
//        }
//        else if(nonDupIndividuals.size()<numOffRequired){
//            ArrayList<Individual> selectedTopTwo = new ArrayList<>();
//            selectedTopTwo.addAll(nonDupIndividuals);
//            ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.addAll(nonDupPCs);
//            ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.addAll(nonDupIndividuals);
//            int addIndex = 0;
//            int permIndex = 0;
//            while(selectedTopTwo.size()<2){
//                double random = state.random[0].nextDouble();
//                if(random < probabilityForUsingMeanCaseFitness){
//                    selectedTopTwo.add(dup_off[addIndex]);
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(dupPCs.get(addIndex));
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(dupIndividuals.get(addIndex));
//                    addIndex++;
//                }
//                else{
//                    //Strategy 2: add expert individual based on the medium
////                    permIndex++;
//                    double best_caseFit = ((OneInstanceMultiCaseMultiObjectiveFitness)dup_off[0].fitness).fitnessMedian();
//                    int best_index = 0;
//                    for(int i=1; i<dup_off.length; i++){
//                        OneInstanceMultiCaseMultiObjectiveFitness fit_dup_off_i = (OneInstanceMultiCaseMultiObjectiveFitness)dup_off[i].fitness;
//                        double caseFit_dup_off_i = fit_dup_off_i.fitnessMedian();
//                        if(caseFit_dup_off_i < best_caseFit){
//                            best_caseFit = caseFit_dup_off_i;
//                            best_index = i;
//                        }
//                    }
//                    selectedTopTwo.add(dup_off[best_index]);
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(dupPCs.get(best_index));
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(dupIndividuals.get(best_index));
//
////                    //Strategy 1: add expert individual based on a random case
////                    int random_case = perm[permIndex];
////                    permIndex++;
////                    double best_caseFit = Double.POSITIVE_INFINITY;
////                    int best_index =-1;
////                    for(int i=0; i<dup_off.length; i++){
////                        OneInstanceMultiCaseMultiObjectiveFitness fit_dup_off_i = (OneInstanceMultiCaseMultiObjectiveFitness)dup_off[i].fitness;
////                        double caseFit_dup_off_i = fit_dup_off_i.multiInstanceMultiObjectiveFitness[random_case][0];
////                        if(caseFit_dup_off_i < best_caseFit){
////                            best_caseFit = caseFit_dup_off_i;
////                            best_index = i;
////                        }
////                    }
////                    selectedTopTwo.add(dup_off[best_index]);
////                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(dupPCs.get(best_index));
////                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(dupIndividuals.get(best_index));
//                }
//            }
//            return selectedTopTwo;
//        }
//        else{
//            ArrayList<Individual> selectedTopTwo = new ArrayList<>();
//            int addIndex = 0;
//            int permIndex = 0;
//            while(selectedTopTwo.size()<2){
//                double random = state.random[0].nextDouble();
//                if(random < probabilityForUsingMeanCaseFitness){
//                    selectedTopTwo.add(nonDup_off[addIndex]);
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(nonDupPCs.get(addIndex));
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(nonDupIndividuals.get(addIndex));
//                    addIndex++;
//                }
//                else{
//                    //add expert individual based on a random case
//                    int random_case = perm[permIndex];
//                    permIndex++;
//                    double best_caseFit = Double.POSITIVE_INFINITY;
//                    int best_index = -1;
//                    for(int i=0; i<nonDup_off.length; i++){
//                        OneInstanceMultiCaseMultiObjectiveFitness fit_non_dup_off_i = (OneInstanceMultiCaseMultiObjectiveFitness)nonDup_off[i].fitness;
//                        double caseFit_dup_off_i = fit_non_dup_off_i.multiInstanceMultiObjectiveFitness[random_case][0];
//                        if(caseFit_dup_off_i < best_caseFit){
//                            best_caseFit = caseFit_dup_off_i;
//                            best_index = i;
//                        }
//                    }
//                    selectedTopTwo.add(nonDup_off[best_index]);
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs.add(nonDupPCs.get(best_index));
//                    ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring.add(nonDupIndividuals.get(best_index));
//                }
//            }
//            return selectedTopTwo;
//        }
//    }
    public void checkDuplicationWithExistingOffspringByCrossoverInNewPop(final EvolutionState state, double[][] PCOffspring, boolean[] duplication){
        List<double[]> existingPCs = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs;
        int numDup = 0;
        for(int i=0; i<PCOffspring.length; i++){
//            double[] offPC_i = PCOffspring[i];
//            boolean dup = false;
//            for(int j=0; j<existingPCs.size(); j++){
//                double[] PC_j = existingPCs.get(j);
//                double distance = PhenoCharacterisation.distance(offPC_i, PC_j);
//                if(distance==0){
//                    dup = true;
//                    numDup++;
//                    break;
//                }
//            }
//            duplication[i] = dup; //right
            duplication[i] = false; //not right but only for test
        }
//        System.out.println("Number of duplication: " + numDup);
    }

    public void checkDuplicatedBestFitWithExistingOffspringByCrossoverInNewPop(final EvolutionState state, double[][] PCOffspring,
                                                                               Individual[] off, boolean[] duplicatedBestFit){
        List<double[]> existingPCs = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspringPCs;
        List<Individual> existingOffs = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).crossoverOffspring;
        int numDup = 0;
        for(int i=0; i<PCOffspring.length; i++){
            double[] offPC_i = PCOffspring[i];
            OneInstanceMultiCaseMultiObjectiveFitness off_i_fit = (OneInstanceMultiCaseMultiObjectiveFitness)off[i].fitness;
            double bestFit = Double.POSITIVE_INFINITY;
            for(int j=0; j<existingPCs.size(); j++){
                double[] PC_j = existingPCs.get(j);
                double distance = PhenoCharacterisation.distance(offPC_i, PC_j);
                if(distance==0){
                    OneInstanceMultiCaseMultiObjectiveFitness existingOffs_j_fit = (OneInstanceMultiCaseMultiObjectiveFitness)existingOffs.get(j).fitness;
                    if(existingOffs_j_fit.fitnessMean() < bestFit)
                        bestFit = existingOffs_j_fit.fitnessMean();
                }
            }
            if(off_i_fit.fitnessMean()<bestFit){
                duplicatedBestFit[i] = false; //right
            }
            else{
                duplicatedBestFit[i] = true; //right
            }

        }
//        System.out.println("Number of duplication: " + numDup);
    }


    public void multiCaseFitnessNormalisationForOffspring(EvolutionState state, Individual[] individuals){
        OneInstanceMultiCaseMultiObjectiveFitness fitness0 = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[0].fitness;
        double[][] objectives0 = fitness0.multiInstanceMultiObjectiveFitness;
        int length0 = objectives0.length;
        double[] bests = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).bests;
        double[] worsts = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).worsts;

        //for this method, the binary bring in fitness is used to store the nomatilation results
        for(int i=0; i<individuals.length; i++){
            OneInstanceMultiCaseMultiObjectiveFitness fitness = (OneInstanceMultiCaseMultiObjectiveFitness)individuals[i].fitness;
            double[][] objectives = fitness.multiInstanceMultiObjectiveFitness;
            int length = objectives.length;
            double[][] normalisation = new double[length][1];
            for(int j=0; j<length; j++){
                if(objectives[j][0] >= Double.MAX_VALUE || objectives[j][0] >= Double.POSITIVE_INFINITY){
                    normalisation[j][0] = 1.1;
                }
                else{
                    normalisation[j][0] = (objectives[j][0] - bests[j])/(worsts[j] - bests[j]);
                }
            }
            fitness.setMultiInstanceMultiObjectiveNormalisation(normalisation);
        }
    }


    //==========================================KNN========================================
    //2020.11.30 assign fitness to all the individuals based on KNN with subpopulation K, then sort the intermediate population, and select the top k as the offspring in subpopulation K.
    public double[][] evaluateOffspring(final EvolutionState state, double[][][] indsCharListsMultiTree, double[][][][] fitnessesForModel, List<Individual> offspring) {

//        phenoCharacterisation = new PhenoCharacterisation[2];

        double[][] poolIndPC = phenotypicForSurrogateV1.phenotypicIndividual(state, offspring);

        //2020.11.30 the PC of the individuals in the pool
//        double[][] poolIndPC = phenotypicForSurrogateV1.phenotypicIndividual(this, ClearingEvaluator.phenoCharacterisation, archiveInds.get(0));

        //step2: evaluate all individuals based on the first KNN
        for (int sub = 0; sub < state.population.subpops.length; sub++) {
            for (int i = 0; i < offspring.size(); i++) //
            {
                Individual individual = offspring.get(i); //the examined individual
                //if(indsCharListsMultiTree.length != 0){
                //KNN
                //===============================start==============================================
                double dMin  = Double.MAX_VALUE;
                int index = 0;
                double[] pcIntermediate = poolIndPC[i]; //the pc of the examined individual
                //calculate the fitness based on surrogate model
                for(int pc = 0; pc < indsCharListsMultiTree[sub].length; pc++){
                    double[] pcModel = indsCharListsMultiTree[sub][pc];
                    double d = 0;
                    if(distanceBB){
                        d  = PhenoCharacterisation.distanceBinaryBring(pcIntermediate, pcModel);
                    }
                    else{
                        d  = PhenoCharacterisation.distance(pcIntermediate, pcModel);
                    }

                    if(d == 0){
                        index = pc;
                        break;
                    }
                    if (d < dMin){
                        dMin = d;
                        index = pc;
                    }
                }
//                System.out.println("Index: " + index);
                ((OneInstanceMultiCaseMultiObjectiveFitness)individual.fitness).multiInstanceMultiObjectiveFitness=fitnessesForModel[sub][index].clone();
//                (individual.fitness).(fitnessesForModel[sub][index]);
            }
//            System.out.println("-----------------------------");
            //step3: choose the top |P| individuals as the offspring of the current subpop
            //sort the individuals based on the fitnesses
/*			Individual[] inds = PopulationUtils.sortInds(individuals);
			System.arraycopy(inds, 0, state.population.subpops[sub].individuals, 0, state.population.subpops[sub].individuals.length);*/
        }
//        clearOffspring(state, poolIndPC, offspring, 0, 1); //add by mengxu 2022.03.24

        return poolIndPC;
    }

    public void clearOffspring(final EvolutionState state, double[][] poolIndPC, List<Individual> offspring, double radius, int capacity) {
        // clear this subpopulation
        for (int i = 0; i < offspring.size(); i++) {
            // skip the cleared individuals
            if (offspring.get(i).fitness.fitness() >= Double.POSITIVE_INFINITY || offspring.get(i).fitness.fitness() >= Double.MAX_VALUE) {
                continue;
            }

            int numWinners = 1;
            for (int j = i+1; j < offspring.size(); j++) {
                // skip the cleared individuals
                if (offspring.get(i).fitness.fitness() >= Double.POSITIVE_INFINITY || offspring.get(i).fitness.fitness() >= Double.MAX_VALUE) {
                    continue;
                }

                // calculate the distance between individuals i and j
                double distance = PhenoCharacterisation.distance(
                        poolIndPC[i], poolIndPC[j]);
                if (distance > radius) {
                    // Individual j is not in the niche
                    continue; //if distance, means two individuals are the same, clear (below) the individual, get out of current loop
                }

                if (numWinners < capacity) { //when set capacity to 1, the code will never go to here
                    numWinners ++;
                }
                else {
                    // Clear the fitness of individual j
                    int caseNum = ((OneInstanceMultiCaseMultiObjectiveFitness)offspring.get(j).fitness).multiInstanceMultiObjectiveFitness.length;
                    double[][] caseFitness = new double[caseNum][1];
                    for(int c=0; c<caseNum; c++){
                       caseFitness[c][0] = Double.MAX_VALUE;
                    }
                    ((OneInstanceMultiCaseMultiObjectiveFitness)offspring.get(j).fitness).setMultiInstanceFitness(state, caseFitness);
                    //fzhang 2019.9.11
//                    offspring.get(j).evaluated = true;
                }
            }
        }
    }

    public void clearOffspring(EvolutionState state, double[][] poolIndPC, Individual[] offspring, double radius, int capacity) {
        // clear this subpopulation
        for (int i = 0; i < offspring.length; i++) {
            // skip the cleared individuals
            if (offspring[i].fitness.fitness() >= Double.POSITIVE_INFINITY || offspring[i].fitness.fitness() >= Double.MAX_VALUE) {
                continue;
            }

            int numWinners = 1;
            for (int j = i+1; j < offspring.length; j++) {
                // skip the cleared individuals
                if (offspring[i].fitness.fitness() >= Double.POSITIVE_INFINITY || offspring[i].fitness.fitness() >= Double.MAX_VALUE) {
                    continue;
                }

                // calculate the distance between individuals i and j
                double distance = PhenoCharacterisation.distance(
                        poolIndPC[i], poolIndPC[j]);
                if (distance > radius) {
                    // Individual j is not in the niche
                    continue; //if distance, means two individuals are the same, clear (below) the individual, get out of current loop
                }

                if (numWinners < capacity) { //when set capacity to 1, the code will never go to here
                    numWinners ++;
                }
                else {
                    // Clear the fitness of individual j
                    int caseNum = ((OneInstanceMultiCaseMultiObjectiveFitnessDPS)offspring[j].fitness).multiInstanceMultiObjectiveFitness.length;
                    double[][] caseFitness = new double[caseNum][1];
                    for(int c=0; c<caseNum; c++){
                        caseFitness[c][0] = Double.MAX_VALUE;
                    }
                    ((OneInstanceMultiCaseMultiObjectiveFitnessDPS)offspring[j].fitness).setMultiInstanceFitness(state, caseFitness);
                    //fzhang 2019.9.11
//                    offspring.get(j).evaluated = true;
                }
            }
        }
    }

    public void calculateAllBenefitScore(Individual parent1, Individual parent2, Individual[] offspring){
        for(int i=0; i<offspring.length; i++){
            Individual ind = offspring[i];
            double benefitScore1 = calculateBenefitScore(parent1, ind);
            double benefitScore2 = calculateBenefitScore(parent2, ind);
            ((OneInstanceMultiCaseMultiObjectiveFitness)ind.fitness).setBenefitScore(benefitScore1+benefitScore2);
        }
    }

    public double calculateBenefitScore(Individual parent, Individual offspring){
        double[][] normalisationParent = ((OneInstanceMultiCaseMultiObjectiveFitness)parent.fitness).getMultiInstanceMultiObjectiveNormalisation();
        double[][] normalisationOffspring = ((OneInstanceMultiCaseMultiObjectiveFitness)offspring.fitness).getMultiInstanceMultiObjectiveNormalisation();

        double advantage = 0;

        for(int i=0; i<normalisationParent.length; i++){
            double bb0 = normalisationParent[i][0];
            double bb1 = normalisationOffspring[i][0];

            double ref = bb0 - bb1;
            if(ref >= 0){
                if(bb1 == 0){
                    advantage += 1; //modified by mengxu 2022.03.07
                }
                else{
                    advantage += (bb0 - bb1) / bb0;
                }
            }
            else{
                if(bb1 == 0){//not happen
                    advantage += (-1); //modified by mengxu 2022.03.07
                    System.out.println("Should not access here!");
                }
                else{
                    advantage += (bb0 - bb1) / bb1;
                }
            }

            //the following is the original one
//            if(ref >= 0){
//                if(bb0 == 0){
//                    advantage += 1; //modified by mengxu 2022.03.07
//                }
//                else{
//                    advantage += (bb0 - bb1) / bb0;
//                }
//            }
//            else{
//                if(bb1 == 0){
//                    advantage += (-1); //modified by mengxu 2022.03.07
//                }
//                else{
//                    advantage += (bb0 - bb1) / bb1;
//                }
//            }

        }
//        System.out.println("advantage: " + advantage);
        return advantage ;
    }

    //todo: need to rank the other place that using the bubbling sort, for other place, I might make it wrong, 2024.5.24
    public static void sortOffspringBasedOnBenefitScoreWithPC(Individual[] ind, double[][] PCOffspring)
    {
        int n = ind.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                double benefitScore_j = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j].fitness).benefitScore;
                double benefitScore_j_1 = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j+1].fitness).benefitScore;
                if (benefitScore_j < benefitScore_j_1) {
                    // Swap elements if they are in the wrong order
                    //todo: need double check whether need clone 2024.5.14
                    Individual ind_temp = ind[j];
                    double[] PC_temp = PCOffspring[j];
                    ind[j] = ind[j + 1];
                    PCOffspring[j] = PCOffspring[j+1];
                    ind[j + 1] = ind_temp;
                    PCOffspring[j+1] = PC_temp;
                }
            }
        }
    }

    public static void sortOffspringBasedOnFitnessWithPC(Individual[] ind, double[][] PCOffspring)
    {
        int n = ind.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
//                double fitness_j = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j].fitness).fitness();
//                double fitness_j_1 = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j+1].fitness).fitness();
                double fitness_j = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j].fitness).fitnessMean();
                double tree_size_j = (((GPIndividual)ind[j]).trees[0]).child.numNodes(GPNode.NODESEARCH_ALL) + ((GPIndividual)ind[j]).trees[1].child.numNodes(GPNode.NODESEARCH_ALL);
                double fitness_j_1 = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j+1].fitness).fitnessMean();
                double tree_size_j_1 = ((GPIndividual)ind[j+1]).trees[0].child.numNodes(GPNode.NODESEARCH_ALL) + ((GPIndividual)ind[j+1]).trees[1].child.numNodes(GPNode.NODESEARCH_ALL);
//                double fitness_j = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j].fitness).fitnessMedian();
//                double fitness_j_1 = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j+1].fitness).fitnessMedian();
                if (fitness_j > fitness_j_1) {
                    // Swap elements if they are in the wrong order
                    //todo: need double check whether need clone 2024.5.14
                    Individual ind_temp = ind[j];
                    double[] PC_temp = PCOffspring[j];
                    ind[j] = ind[j + 1];
                    PCOffspring[j] = PCOffspring[j+1];
                    ind[j + 1] = ind_temp;
                    PCOffspring[j+1] = PC_temp;
                }
                else if(fitness_j == fitness_j_1){
                    if(tree_size_j < tree_size_j_1){
                        //todo: need double check whether need clone 2024.5.14
                        Individual ind_temp = ind[j];
                        double[] PC_temp = PCOffspring[j];
                        ind[j] = ind[j + 1];
                        PCOffspring[j] = PCOffspring[j+1];
                        ind[j + 1] = ind_temp;
                        PCOffspring[j+1] = PC_temp;
                    }
                }
            }
        }
    }

    public static void sortOffspringBasedOnFitnessWithPCAndDup(Individual[] ind, double[][] PCOffspring, boolean[] duplication)
    {
        int n = ind.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
//                double fitness_j = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j].fitness).fitness();
//                double fitness_j_1 = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j+1].fitness).fitness();
                double fitness_j = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j].fitness).fitnessMean();
                double fitness_j_1 = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j+1].fitness).fitnessMean();
//                double fitness_j = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j].fitness).fitnessMedian();
//                double fitness_j_1 = ((OneInstanceMultiCaseMultiObjectiveFitness)ind[j+1].fitness).fitnessMedian();
                if (fitness_j > fitness_j_1) {
                    // Swap elements if they are in the wrong order
                    //todo: need double check whether need clone 2024.5.14
                    Individual ind_temp = ind[j];
                    double[] PC_temp = PCOffspring[j];
                    boolean dup_temp = duplication[j];

                    ind[j] = ind[j + 1];
                    PCOffspring[j] = PCOffspring[j+1];
                    duplication[j] = duplication[j+1];

                    ind[j + 1] = ind_temp;
                    PCOffspring[j+1] = PC_temp;
                    duplication[j+1] = dup_temp;
                }
            }
        }
    }

    public static void sortOffspringBasedOnBenefitScore(Individual[] ind)
    {
        Comparator<Individual> comp = (Individual o1, Individual o2) ->
        {
            if(((OneInstanceMultiCaseMultiObjectiveFitness)o1.fitness).benefitScore < ((OneInstanceMultiCaseMultiObjectiveFitness)o2.fitness).benefitScore)
                return 1;
            if(((OneInstanceMultiCaseMultiObjectiveFitness)o1.fitness).benefitScore == ((OneInstanceMultiCaseMultiObjectiveFitness)o2.fitness).benefitScore)
                return 0;

            return -1;
        };

        Arrays.sort(ind, comp);
    }

    public static void sortOffspring(Individual[] ind)
    {
        Comparator<Individual> comp = (Individual o1, Individual o2) ->
        {
            if(o1.fitness.fitness() < o2.fitness.fitness())
                return -1;
            if(o1.fitness.fitness() == o2.fitness.fitness())
                return 0;

            return 1;
        };

        Arrays.sort(ind, comp);
    }

//    public static void sortOffspring(Individual[] ind)
//    {
//        Comparator<Individual> comp = (Individual o1, Individual o2) ->
//        {
//            if(((MultiInstanceMultiObjectiveFitness)o1.fitness).objectives[0] < ((MultiInstanceMultiObjectiveFitness)o2.fitness).objectives[0])
//                return -1;
//            if(((MultiInstanceMultiObjectiveFitness)o1.fitness).objectives[0] == ((MultiInstanceMultiObjectiveFitness)o2.fitness).objectives[0])
//                return 0;
//
//            return 1;
//        };
//
//        Arrays.sort(ind, comp);
//    }

}
