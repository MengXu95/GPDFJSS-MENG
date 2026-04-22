package mengxu.algorithm.MAPElites.GridMapElites;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPInitializer;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.gp.koza.CrossoverPipeline;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleProblemForm;
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
import mengxu.algorithm.multiobjective.MOEAD.GPRuleEvolutionStateMOEAD;
import mengxu.algorithm.multiobjective.MOEADAdaptiveWeightStrategy.GPRuleEvolutionStateMOEADAWS;
import mengxu.algorithm.multiobjective.MOEADarchive.GPRuleEvolutionStateMOEADarchive;
import mengxu.algorithm.multiobjective.MOEADepsilonC.GPRuleEvolutionStateMOEADeC;
import mengxu.algorithm.multiobjective.MOEADmap.GPRuleEvolutionStateMOEADmap;
import mengxu.algorithm.semanticTournamentSelection.GPRuleEvolutionStateMCSTS;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

/**
 * Created by fzhang on 26.05.2018.
 */
public class AllIndexAllSwapCrossoverPipelineGridMAPElites extends CrossoverPipeline {
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
                sources[0].produce(2,2,0,subpopulation,parents,state,thread);  //max start subpopulation
           /* Produces <i>n</i> individuals from the given subpopulation
            and puts them into inds[start...start+n-1]*/
            else // grab from different sources
                {
//                sources[0].produce(1,1,0,subpopulation,parents,state,thread);
//                sources[1].produce(1,1,1,subpopulation,parents,state,thread);
                    sources[0].produce(1,1,0,subpopulation,parents,state,thread);
                    sources[1].produce(1,1,1,subpopulation,parents,state,thread);
                }


            //add by mengxu 2023.09.21
            GPIndividual parent0 = parents[0];
            GPIndividual parent1 = parents[1];
            if(parent0 == null){
                parent0 = (GPIndividual) ((GPRuleEvolutionStateGridMAPElites)state).parentsForCrossover[0];
            }
            if(parent1 == null){
                parent1 = (GPIndividual) ((GPRuleEvolutionStateGridMAPElites)state).parentsForCrossover[1];
            }
            // at this point, parents[] contains our two selected individuals

            // are our tree values valid?
            if (tree1!=TREE_UNFIXED && (tree1<0 || tree1 >= parent0.trees.length))
                // uh oh
                state.output.fatal("GP Crossover Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");
            if (tree2!=TREE_UNFIXED && (tree2<0 || tree2 >= parent1.trees.length))
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

            //2021.10.14 to find the difference with Tournament selection

                //original
            inds[q] = j1;
                if(((GPRuleEvolutionStateGridMAPElites)state).use_grid_map && ((GPRuleEvolutionStateGridMAPElites)state).mapUpdateImmediate){//2023.09.25 by mengxu
//                    j1.evaluated = false;
                    ((GPRuleEvolutionStateGridMAPElites)state).IndividualFitnessEvaluation(state, j1);
//                    System.out.println("j1 fitness: " + j1.fitness.fitness());
                }
            q++;



            if (q<n+start && !tossSecondParent)
                {
                //2021.10.14 to find the difference with Tournament selection
                //original
                inds[q] = j2;
                if(((GPRuleEvolutionStateGridMAPElites)state).use_grid_map && ((GPRuleEvolutionStateGridMAPElites)state).mapUpdateImmediate){//2023.09.25 by mengxu
                    ((GPRuleEvolutionStateGridMAPElites)state).IndividualFitnessEvaluation(state, j2);
                }
                q++;
                }
            }
        //original
        return n;
        }

//    public void IndividualFitnessEvaluation(EvolutionState state, Individual individual){
//        //Individual fitness evaluation
//        SchedulingSet schedulingSet = ((MultipleRuleEvaluationModel) ((MultipleTreeRuleOptimizationProblem) state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet();
//
//        if (((GPRuleEvolutionStateGridMAPElites)state).CVTMapUpdateImmediate && ((GPRuleEvolutionStateCVTMAPElites)state).usingManuralRuleNormalisation) {
////                    System.out.println("Multi-tree evaluation!");
//            //for test
////            System.out.println("Individual fitness before: "+ individual.fitness.fitness());
//            SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
//            prob.evaluate(state, individual, 0, 0);
//            //for test
////            System.out.println("Individual fitness after: "+ individual.fitness.fitness());
//            double[] indCharListsMultiTree = phenotypicForSurrogateV1.phenotypicIndividualFixedDecisions(individual, ((GPRuleEvolutionStateCVTMAPElites)state).phenoCharacterisation);
//            double[] objectiveValues = ((MultiObjectiveFitness)individual.fitness).getObjectives();
//            double[] objectiveValuesNormalised = new double[objectiveValues.length];
//            for(int i=0; i<objectiveValues.length; i++){
//                double normalisedFit = objectiveValues[i] / ((GPRuleEvolutionStateCVTMAPElites)state).baselineObjectives[i];
//                objectiveValuesNormalised[i] = normalisedFit;
//            }
//            ((GPRuleEvolutionStateCVTMAPElites)state).CVTmap.updateCVTMapIndividualsWithIndividual(state, individual,indCharListsMultiTree,objectiveValuesNormalised[0]);
//            //Normalise the fitness of individual using manural rule normalisation
//
//        }
//    }

}
