/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multiobjective.phenotypeNSGPII.compareOne;

import ec.EvolutionState;
import ec.Individual;
import ec.Subpopulation;
import ec.gp.*;
import ec.gp.koza.GPKozaDefaults;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2Evaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.SortComparator;
import ec.util.ThreadPool;
import mengxu.algorithm.multiobjective.GPRuleEvolutionStateMO;
import mengxu.algorithm.multiobjective.NSGPII.zScore.NSGA2MultiObjectiveFitnessNormalisation;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.UniqueTerminalsGatherer;

import java.util.ArrayList;
import java.util.List;

public class NSGA2EvaluatorNoEnvironmentalSelectionPhenotypeBreeding extends NSGA2Evaluator {

	public static final String P_RADIUS = "radius";
	public static final String P_CAPACITY = "capacity";

	protected double radius;
	protected int capacity;

//	public static PhenoCharacterisation[] phenoCharacterisation;

//	public static double[][][] phenotypicOfIntermedidatePop;

	public List<double[]> offspringCharLists = new ArrayList<>();

	List<double[]> sortedPopCharLists = new ArrayList<>();
	public ArrayList<Integer> clearedIndsArray = new ArrayList<>();

	public ArrayList<Integer> clearedIndsIndex = new ArrayList<>();

	public Individual[] ParetoFrontFromLastGeneration;

	public double crossoverRate = 0.8;
	public double mutationRate = 0.2;

	/** How the pipeline selects a node from individual 1 */
	public GPNodeSelector nodeselect1; //for crossover

	/** How the pipeline selects a node from individual 2 */
	public GPNodeSelector nodeselect2; //for crossover

	/** Standard parameter for node-selectors associated with a GPBreedingPipeline */
	public static final String P_NODESELECTOR = "ns";

	public static final String P_CROSSOVER = "xover";

	public static final String V_SAME = "same";

	/** How the pipeline builds a new subtree */
	public GPNodeBuilder builder;

	public static final String P_BUILDER = "build";

	public Parameter defaultBase() { return GPKozaDefaults.base().push(P_CROSSOVER); }

	public void setup(final EvolutionState state, final Parameter base) {
		super.setup(state, base);

		Parameter def = defaultBase();
		Parameter p = base.push(P_NODESELECTOR).push("0");
		Parameter d = def.push(P_NODESELECTOR).push("0");

		nodeselect1 = (GPNodeSelector)
				(state.parameters.getInstanceForParameter(
						p,d, GPNodeSelector.class));
		nodeselect1.setup(state,p);

		p = base.push(P_NODESELECTOR).push("1");
		d = def.push(P_NODESELECTOR).push("1");

		if (state.parameters.exists(p,d) &&
				state.parameters.getString(p,d).equals(V_SAME))
			// can't just copy it this time; the selectors
			// use internal caches.  So we have to clone it no matter what
			nodeselect2 = (GPNodeSelector)(nodeselect1.clone());
		else
		{
			nodeselect2 = (GPNodeSelector)
					(state.parameters.getInstanceForParameter(
							p,d, GPNodeSelector.class));
			nodeselect2.setup(state,p);
		}

//		p = base.push(P_BUILDER).push(""+0);
		p = new Parameter("gp.koza.mutate.build.0");
		d = def.push(P_BUILDER).push(""+0);

		builder = (GPNodeBuilder)
				(state.parameters.getInstanceForParameter(
						p,d, GPNodeBuilder.class));
		builder.setup(state,p);

		radius = state.parameters.getDoubleWithDefault(
				base.push(P_RADIUS), null, 0.0);
		capacity = state.parameters.getIntWithDefault(
				base.push(P_CAPACITY), null, 1);
		String filePath = state.parameters.getString(new Parameter("filePath"), null);
		//It's a little tricky to know whether we have 1 or 2 populations here, so we will assume
		//2 for the purpose of the phenoCharacterisation, and ignore the second object if only
		//1 is used
//		phenoCharacterisation = new PhenoCharacterisation[2];
//
//		//add by mengxu 2022.03.17
//		Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);
//
//		//this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
//		if (filePath == null) {
//			//dynamic simulation
//			phenoCharacterisation[0] =
//					SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
//			phenoCharacterisation[1] =
//					RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
//		} else {
//			//static simulation
//			phenoCharacterisation[0] =
//					SequencingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
//			phenoCharacterisation[1] =
//					RoutingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
//		}
	}

	public void evaluatePopulation(final EvolutionState state) {
//		if(state.generation > 0){
//			this.clearPopulation(state, radius, capacity, phenoCharacterisation);
//			this.fillTheClearedIndividualsBasedOnParetoFront(state); //add by mengxu 2023.05.30
//		}

		this.evaluatePopulationInside(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals
		//do z-score normalisation by mengxu 2023.04.21
		if(((GPRuleEvolutionStateMO)state).useZScoreNormalisation){
			this.zScoreSigmoidNormalisation(state);
		}

		for (int x = 0; x < state.population.subpops.length; x++)
			state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population

//		offspringCharLists.clear();//add by mengxu 2023.06.06
	}


	public boolean phenotypeSimilarityChecking(final EvolutionState state,
											   Individual individual){
		PhenoCharacterisation[] pc = ((GPRuleEvolutionStateMO)state).phenoCharacterisation;
		RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
		int[] charListSeq;
		int[] charListRout;
		int treeID = 0;
		PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
		RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
		charListSeq = phenoCharacterisation.characterise(  //characterise: calculate the distance
					new GPRule(ruleType, ((GPIndividual) individual).trees[treeID]));
		treeID = 1;
		phenoCharacterisation = pc[treeID];
		ruleType = ruleTypes[treeID];
		charListRout = phenoCharacterisation.characterise(  //characterise: calculate the distance
				new GPRule(ruleType, ((GPIndividual) individual).trees[treeID]));

		double[] combinePheChar = new double[charListSeq.length+charListRout.length];
//		double[] combinePheChar = ArrayUtils.addAll(charListSeq, charListRout);
		int i=0;
		for(i=0;i<charListSeq.length;i++){
			combinePheChar[i] = (double)charListSeq[i];
		}
		for(;i<charListSeq.length+charListRout.length;i++){
			combinePheChar[i] = (double)charListRout[i-charListSeq.length];
		}

		for(int ind=0; ind<this.offspringCharLists.size(); ind++){
			if(this.offspringCharLists.get(ind) == null){
				continue;
			}
			double[] pcInd = this.offspringCharLists.get(ind);
			double distance = 0;
			if(((GPRuleEvolutionStateMO) state).useEuclideanDistance){
				distance = PhenoCharacterisation.distance(pcInd, combinePheChar);
			}
			else{
				distance = PhenoCharacterisation.distanceBinaryBring(pcInd, combinePheChar);
			}
			if (distance > radius) {
				// Individual j is not in the niche
				continue; //if distance, means two individuals are the same, clear (below) the individual, get out of current loop
			}
			else{
//				System.out.println("Phenotype same!");
				return false;
			}
		}
		this.offspringCharLists.add(combinePheChar);
		return true;
	}

	public Individual produceForMutation(final EvolutionState state, int subpopulation,
								  GPIndividual parent)
	{
		GPInitializer initializer = ((GPInitializer)state.initializer);
		int tree = -1;
		int TREE_UNFIXED = -1;

		// now let's mutate 'em
//		for(int q=start; q < n+start; q++) //q=start = 0  q<1
//		{
			GPIndividual i = parent;

			if (tree!=TREE_UNFIXED && (tree<0 || tree >= i.trees.length))
				// uh oh
				state.output.fatal("GP Mutation Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");

			int t;
			// pick random tree
			if (tree==TREE_UNFIXED)
				if (i.trees.length>1) t = state.random[0].nextInt(i.trees.length);
				else t = 0;
			else t = tree;

			// validity result...
			boolean res = false;

			// prepare the nodeselector
			nodeselect1.reset();

			// pick a node

			GPNode p1=null;  // the node we pick
			GPNode p2=null;

			int numTries = 1;
			boolean equalSize = false;

			for(int x=0;x<numTries;x++)
			{
				// pick a node in individual 1
				p1 = nodeselect1.pickNode(state,subpopulation,0,i,i.trees[t]);

				// generate a tree swap-compatible with p1's position


				int size = GPNodeBuilder.NOSIZEGIVEN;
				if (equalSize) size = p1.numNodes(GPNode.NODESEARCH_ALL);

				p2 = builder.newRootedTree(state,
						p1.parentType(initializer),
						0,
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

			if (true)
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

			// add the new individual, replacing its previous source
			return j;
//		}
	}

	/** Returns true if inner1 can feasibly be swapped into inner2's position */

	public boolean verifyPoints(GPNode inner1, GPNode inner2)
	{
		// We know they're swap-compatible since we generated inner1
		// to be exactly that.  So don't bother.

		int maxDepth = 8;
		int maxSize = 1;
		int NO_SIZE_LIMIT = -1;

		// next check to see if inner1 can fit in inner2's spot
		if (inner1.depth()+inner2.atDepth() > maxDepth) return false;

		// check for size
		if (maxSize != NO_SIZE_LIMIT)
		{
			// first easy check
			int inner1size = inner1.numNodes(GPNode.NODESEARCH_ALL);
			int inner2size = inner2.numNodes(GPNode.NODESEARCH_ALL);
			if (inner1size > inner2size)  // need to test further
			{
				// let's keep on going for the more complex test
				GPNode root2 = ((GPTree)(inner2.rootParent())).child;
				int root2size = root2.numNodes(GPNode.NODESEARCH_ALL);
				if (root2size - inner2size + inner1size > maxSize)  // take root2, remove inner2 and swap in inner1.  Is it still small enough?
					return false;
			}
		}

		// checks done!
		return true;
	}

	public Individual[] produceForCrossover(final EvolutionState state, int subpopulation,
										  GPIndividual parent1, GPIndividual parent2) {//generate two offspring based on this crossover
		GPInitializer initializer = ((GPInitializer) state.initializer);
		Individual[] offspring = new Individual[2];

//		for(int q=0;q<2; /* no increment */)  // keep on going until we're filled up
//		{
		int tree1 = -1;
		int tree2 = -1;
		int TREE_UNFIXED = -1;

		int t1 = 0;
		int t2 = 0;
		//System.out.println("The first one: "+parents[0].trees[t1].constraints(initializer));
		// System.out.println("The second one: "+parents[1].trees[t2].constraints(initializer));
		// System.out.println(parents[0].trees.length); //1
		//  System.out.println(parents[1].trees.length);
		if (tree1 == TREE_UNFIXED || tree2 == TREE_UNFIXED) {
			do
			// pick random trees  -- their GPTreeConstraints must be the same
			{
				if (tree1 == TREE_UNFIXED)

					if (parent1.trees.length > 1)
						t1 = state.random[0].nextInt(parent1.trees.length);
					else {
						t1 = 0;
						//System.out.println(parents[0].trees.length);
					}
				else
					t1 = tree1;

				if (tree2 == TREE_UNFIXED)
					if (parent2.trees.length > 1)
						t2 = state.random[0].nextInt(parent2.trees.length);
					else t2 = 0;
				else
					t2 = tree2;
			} while (parent1.trees[t1].constraints(initializer) != parent2.trees[t2].constraints(initializer));
			//while (false);
		} else {
			t1 = tree1;
			t2 = tree2;
			// make sure the constraints are okay
			if (parent1.trees[t1].constraints(initializer)
					!= parent2.trees[t2].constraints(initializer)) // uh oh
				state.output.fatal("GP Crossover Pipeline's two tree choices are both specified by the user -- but their GPTreeConstraints are not the same");
		}

		// validity results...
		boolean res1 = false;
		boolean res2 = false;


		// prepare the nodeselectors
		nodeselect1.reset();
		nodeselect2.reset();


		// pick some nodes

		GPNode p1 = null;
		GPNode p2 = null;

		int numTries = 1;

		for (int x = 0; x < numTries; x++) {
			// pick a node in individual 1
			p1 = nodeselect1.pickNode(state, subpopulation, 0, parent1, parent1.trees[t1]);

			// pick a node in individual 2
			p2 = nodeselect2.pickNode(state, subpopulation, 0, parent2, parent2.trees[t2]);

			// check for depth and swap-compatibility limits
			res1 = verifyPoints(initializer, p2, p1);  // p2 can fill p1's spot -- order is important!
			res2 = verifyPoints(initializer, p1, p2);  // p1 can fill p2's spot -- order is important!

			// did we get something that had both nodes verified?
			// we reject if EITHER of them is invalid.  This is what lil-gp does.
			// Koza only has numTries set to 1, so it's compatible as well.
			if (res1 && res2) break;
		}
		// Create some new individuals based on the old ones -- since
		// GPTree doesn't deep-clone, this should be just fine.  Perhaps we
		// should change this to proto off of the main species prototype, but
		// we have to then copy so much stuff over; it's not worth it.

		GPIndividual j1 = (GPIndividual) (parent1.lightClone());
		GPIndividual j2 = null;
		j2 = (GPIndividual) (parent2.lightClone());

		// Fill in various tree information that didn't get filled in there
		j1.trees = new GPTree[parent1.trees.length];
		j2.trees = new GPTree[parent2.trees.length];

		// at this point, p1 or p2, or both, may be null.
		// If not, swap one in.  Else just copy the parent.

		for (int x = 0; x < j1.trees.length; x++) {
			if (x == t1 && res1)  // we've got a tree with a kicking cross position!
			{
				j1.trees[x] = (GPTree) (parent1.trees[x].lightClone());
				j1.trees[x].owner = j1;
				j1.trees[x].child = parent1.trees[x].child.cloneReplacing(p2, p1); //p2 new, p1 old
				j1.trees[x].child.parent = j1.trees[x];
				j1.trees[x].child.argposition = 0;
				j1.evaluated = false;
			}  // it's changed
			else {
				//modified by fzhang 7.6.2018  randomly choose one tree to do crossover. According to the setting, must be point crossover.
				//after that, swap the other tree.
				j1.trees[x] = (GPTree) (parent2.trees[x].lightClone());
				j1.trees[x].owner = j1;
				j1.trees[x].child = (GPNode) (parent2.trees[x].child.clone());
				j1.trees[x].child.parent = j1.trees[x];
				j1.trees[x].child.argposition = 0;
			}
		}

		for (int x = 0; x < j2.trees.length; x++) {
			if (x == t2 && res2) // we've got a tree with a kicking cross position!
			{
				j2.trees[x] = (GPTree) (parent2.trees[x].lightClone());
				j2.trees[x].owner = j2;
				j2.trees[x].child = parent2.trees[x].child.cloneReplacing(p1, p2);
				j2.trees[x].child.parent = j2.trees[x];
				j2.trees[x].child.argposition = 0;
				j2.evaluated = false;
			} // it's changed
			else {
				j2.trees[x] = (GPTree) (parent1.trees[x].lightClone());
				j2.trees[x].owner = j2;
				j2.trees[x].child = (GPNode) (parent1.trees[x].child.clone());
				j2.trees[x].child.parent = j2.trees[x];
				j2.trees[x].child.argposition = 0;
			}


			// add the individuals to the population
			offspring[0] = j1;
			offspring[1] = j2;
		}
			return offspring;
	}

	public boolean verifyPoints(final GPInitializer initializer, final GPNode inner1, final GPNode inner2)
	{
		int maxDepth = 8;
		int maxSize = 1;
		int NO_SIZE_LIMIT = -1;
		// first check to see if inner1 is swap-compatible with inner2
		// on a type basis
		if (!inner1.swapCompatibleWith(initializer, inner2)) return false;

		// next check to see if inner1 can fit in inner2's spot
		if (inner1.depth()+inner2.atDepth() > maxDepth) return false;

		// check for size
		// NOTE: this is done twice, which is more costly than it should be.  But
		// on the other hand it allows us to toss a child without testing both times
		// and it's simpler to have it all here in the verifyPoints code.
		if (maxSize != NO_SIZE_LIMIT)
		{
			// first easy check
			int inner1size = inner1.numNodes(GPNode.NODESEARCH_ALL);
			int inner2size = inner2.numNodes(GPNode.NODESEARCH_ALL);
			if (inner1size > inner2size)  // need to test further
			{
				// let's keep on going for the more complex test
				GPNode root2 = ((GPTree)(inner2.rootParent())).child;
				int root2size = root2.numNodes(GPNode.NODESEARCH_ALL);
				if (root2size - inner2size + inner1size > maxSize)  // take root2, remove inner2 and swap in inner1.  Is it still small enough?
					return false;
			}
		}

		// checks done!
		return true;
	}

	/** A simple evaluator that doesn't do any coevolutionary
	 evaluation.  Basically it applies evaluation pipelines,
	 one per thread, to various subchunks of a new population. */
	public void evaluatePopulationInside(final EvolutionState state)
	{
		if (numTests > 1)
			expand(state);

		// reset counters.  Only used in multithreading
		individualCounter = 0;
		subPopCounter = 0;

		// start up if single-threaded?
		if (state.evalthreads == 1)
		{
			int[] numinds = new int[state.population.subpops.length];
			int[] from = new int[numinds.length];

			for(int i = 0; i < numinds.length; i++)
			{
				numinds[i] =  state.population.subpops[i].individuals.length;
				from[i] = 0;
			}

			SimpleProblemForm prob = null;
			if (cloneProblem)
				prob = (SimpleProblemForm)(p_problem.clone());
			else
				prob = (SimpleProblemForm)(p_problem);  // just use the prototype
			evalPopChunk(state, numinds, from, 0, prob);
		}
		else
		{
			ThreadPool.Worker[] threads = new ThreadPool.Worker[state.evalthreads];
			for(int i = 0; i < threads.length; i++)
			{
				SimpleEvaluatorThread run = new SimpleEvaluatorThread();
				run.threadnum = i;
				run.state = state;
				run.prob = (SimpleProblemForm)(p_problem.clone());
				threads[i] = pool.start(run, "ECJ Evaluation Thread " + i);
			}

			// join
			pool.joinAll();
		}

		if (numTests > 1)
			contract(state);

		//added by mengxu to ensure same time of evaluations 2023.04.15
//        for(int i=0; i<state.population.subpops.length; i++){
//            state.totalEvaluationTime += state.population.subpops[i].individuals.length;
//        }
	}


	// delete the poor individuals      radius: control the range of each niche   capacity: determines the number of individuals in each niche.
	public void clearPopulation(final EvolutionState state,
									   double radius, int capacity,
									   PhenoCharacterisation[] pc) {
		//it is not correct, because it only contains one the pc of one subpopulation
		double[][][] phenotypicOfIntermedidatePop = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(state, pc, false);//modified by mengxu 2023.03.30 to change false to true
//        phenotypicOfIntermedidatePop = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(state, pc, false); //fzhang 2019.9.26  add true here to make consistent

		clearedIndsIndex.clear();
		sortedPopCharLists = new ArrayList<>();

		for (int subpopNum = 0; subpopNum < state.population.subpops.length; subpopNum++) {
			int clearedInds = 0;
			Subpopulation subpop = state.population.subpops[subpopNum];
			Individual[] sortedPop = subpop.individuals;

			//fzhang 2018.10.2 calculate the distance of each individual and the reference rule
			for (int i = 0;  i < phenotypicOfIntermedidatePop[subpopNum].length; i++) {
				//where the examined rule set the chosen operation by reference rule  for example: 3. means examined rule set the chosen operation by reference rule as the third one
				double[] charList = phenotypicOfIntermedidatePop[subpopNum][i];
				sortedPopCharLists.add(charList);
			}

			// clear this subpopulation
			for (int i = 0; i < sortedPop.length; i++) {
				// skip the cleared individuals
				if (sortedPop[i] == null) {
					continue;
				}

				int numWinners = 1;
				for (int j = i+1; j < sortedPop.length; j++) {
					// skip the cleared individuals
					if (sortedPop[j] == null) {
						continue;
					}

					// calculate the distance between individuals i and j
					double distance = PhenoCharacterisation.distance(
							sortedPopCharLists.get(i), sortedPopCharLists.get(j));
					if (distance > radius) {
						// Individual j is not in the niche
						continue; //if distance, means two individuals are the same, clear (below) the individual, get out of current loop
					}

					if (numWinners < capacity) { //when set capacity to 1, the code will never go to here
						numWinners ++;
					}
					else {
						// Clear the individual j
//						sortedPop[j] = null;
						//todo: compare the features and delete the one with fewer types of terminals or the one with smaller size
						//todo: implemented 2023.06.01
						int treeSize_i = ((GPTree)((GPIndividual)sortedPop[i]).trees[0]).child.numNodes(GPNode.NODESEARCH_ALL) +
								((GPTree)((GPIndividual)sortedPop[i]).trees[1]).child.numNodes(GPNode.NODESEARCH_ALL);
						int treeSize_j = ((GPTree)((GPIndividual)sortedPop[j]).trees[0]).child.numNodes(GPNode.NODESEARCH_ALL) +
								((GPTree)((GPIndividual)sortedPop[j]).trees[1]).child.numNodes(GPNode.NODESEARCH_ALL);
						if(treeSize_j > treeSize_i){
							//keep the one with a larger tree size
							sortedPop[i] = ((GPIndividual) sortedPop[j]).lightClone();
						}
						else if(treeSize_j == treeSize_i){
							UniqueTerminalsGatherer gatherer = new UniqueTerminalsGatherer();
							int numUniqueTerminals_i = ((GPTree)((GPIndividual)sortedPop[i]).trees[0]).child.numNodes(gatherer) +
									((GPTree)((GPIndividual)sortedPop[i]).trees[1]).child.numNodes(gatherer);
							int numUniqueTerminals_j = ((GPTree)((GPIndividual)sortedPop[j]).trees[0]).child.numNodes(gatherer) +
									((GPTree)((GPIndividual)sortedPop[j]).trees[1]).child.numNodes(gatherer);
							if(numUniqueTerminals_j > numUniqueTerminals_i){
								//keep the one with a larger tree size
								sortedPop[i] = ((GPIndividual) sortedPop[j]).lightClone();
							}
						}
//						int treeSize_i = ((GPIndividual)sortedPop[i]).trees[0].treeNumber()
//						sortedPopCharLists.set(i,null); //by mengxu 2023.05.30
						sortedPop[j] = null;
						clearedIndsIndex.add(j); //store the index if the cleared individual, used to set the new generate one by mengxu 2023.05.30
						clearedInds++;
					}
				}
			}
			clearedIndsArray.add(clearedInds);//save the number of cleared individuals of each subpop
			System.out.println("Cleared number: " + clearedInds);
		}

		//hidden by mengxu 2023.03.30, do not need to output
//        if(state.generation == state.numGenerations-1){ //do not need to breed in the last generation
//            jobSeed = ((GPRuleEvolutionStateBaseline)state).getJobSeed();
//            writeToFile(jobSeed, state.population.subpops.length);
//        }
	}

	public void assignSparsity(Individual[] front) {
		int numObjectives = ((NSGA2MultiObjectiveFitnessNormalisation) front[0].fitness).getObjectives().length;

		for (int i = 0; i < front.length; i++)
			((NSGA2MultiObjectiveFitnessNormalisation) front[i].fitness).sparsity = 0;

		for (int i = 0; i < numObjectives; i++) {
			final int o = i;
			// 1. Sort front by each objective.
			// 2. Sum the manhattan distance of an individual's neighbours over
			// each objective.
			// NOTE: No matter which objectives objective you sort by, the
			// first and last individuals will always be the same (they maybe
			// interchanged though). This is because a Pareto front's
			// objective values are strictly increasing/decreasing.
			ec.util.QuickSort.qsort(front, new SortComparator() {
				public boolean lt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((NSGA2MultiObjectiveFitnessNormalisation) i1.fitness)
							.getObjective(o) < ((NSGA2MultiObjectiveFitnessNormalisation) i2.fitness).getObjective(o));
				}

				public boolean gt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((NSGA2MultiObjectiveFitnessNormalisation) i1.fitness)
							.getObjective(o) > ((NSGA2MultiObjectiveFitnessNormalisation) i2.fitness).getObjective(o));
				}
			});

			//fzhang 2018.11.22 normize the objective to calculate the sparsity
			final double min = ((NSGA2MultiObjectiveFitnessNormalisation) front[0].fitness).getObjective(o);
			final double max = ((NSGA2MultiObjectiveFitnessNormalisation) front[front.length - 1].fitness).getObjective(o);
			//todo: this means the NSGPII still use the fitness normalisation 2023.02.14 by mengxu
			//todo: might modify this part

//            System.out.println("normalised min: " + min );
//            System.out.println("normalised max: " + max );

			// Compute and assign sparsity.
			// the first and last individuals are the sparsest.
			((NSGA2MultiObjectiveFitnessNormalisation) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
			((NSGA2MultiObjectiveFitnessNormalisation) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
			for (int j = 1; j < front.length - 1; j++) {
				NSGA2MultiObjectiveFitnessNormalisation f_j = (NSGA2MultiObjectiveFitnessNormalisation) (front[j].fitness);
				NSGA2MultiObjectiveFitnessNormalisation f_jplus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[j + 1].fitness);
				NSGA2MultiObjectiveFitnessNormalisation f_jminus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[j - 1].fitness);

//				System.out.println("original min: " + f_j.minObjective[o] );
//			    System.out.println("original max: " + f_j.maxObjective[o] );

				if (max ==  min)
				{
					f_j.sparsity += 0;
				}
				else {
					// store the NSGA2Sparsity in sparsity
//				if((f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min) < 0){
//					System.out.println("Error here!");
//				}
					f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min);
				}

				//original version
//				System.out.println(f_j.maxObjective[o] - f_j.minObjective[o]);  //1
				// store the NSGA2Sparsity in sparsity
				/*f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o))
						/ (f_j.maxObjective[o] - f_j.minObjective[o]);*/
			}
		}
	}

	public void assignSparsityWithZscoreNormalisation(Individual[] front) {
		int numObjectives = ((NSGA2MultiObjectiveFitnessNormalisation) front[0].fitness).getObjectives().length;

//		System.out.println("Front size: " + front.length);
		for (int i = 0; i < front.length; i++)
			((NSGA2MultiObjectiveFitnessNormalisation) front[i].fitness).sparsity = 0;

		for (int i = 0; i < numObjectives; i++) {
			final int o = i;
			// 1. Sort front by each objective.
			// 2. Sum the manhattan distance of an individual's neighbours over
			// each objective.
			// NOTE: No matter which objectives objective you sort by, the
			// first and last individuals will always be the same (they maybe
			// interchanged though). This is because a Pareto front's
			// objective values are strictly increasing/decreasing.
			ec.util.QuickSort.qsort(front, new SortComparator() {
				public boolean lt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((NSGA2MultiObjectiveFitnessNormalisation) i1.fitness)
							.getNormalisedObjectives(o) < ((NSGA2MultiObjectiveFitnessNormalisation) i2.fitness).getNormalisedObjectives(o));
				}

				public boolean gt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((NSGA2MultiObjectiveFitnessNormalisation) i1.fitness)
							.getNormalisedObjectives(o) > ((NSGA2MultiObjectiveFitnessNormalisation) i2.fitness).getNormalisedObjectives(o));
				}
			});

			//fzhang 2018.11.22 normize the objective to calculate the sparsity
			final double min = ((NSGA2MultiObjectiveFitnessNormalisation) front[0].fitness).getNormalisedObjectives(o);
			final double max = ((NSGA2MultiObjectiveFitnessNormalisation) front[front.length - 1].fitness).getNormalisedObjectives(o);
			//todo: this means the NSGPII still use the fitness normalisation 2023.02.14 by mengxu
			//todo: might modify this part

//            System.out.println("normalised min: " + min );
//            System.out.println("normalised max: " + max );

			// Compute and assign sparsity.
			// the first and last individuals are the sparsest.
			((NSGA2MultiObjectiveFitnessNormalisation) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
			((NSGA2MultiObjectiveFitnessNormalisation) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
			for (int j = 1; j < front.length - 1; j++) {
				NSGA2MultiObjectiveFitnessNormalisation f_j = (NSGA2MultiObjectiveFitnessNormalisation) (front[j].fitness);
				NSGA2MultiObjectiveFitnessNormalisation f_jplus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[j + 1].fitness);
				NSGA2MultiObjectiveFitnessNormalisation f_jminus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[j - 1].fitness);

				int index_plus = j+1;
				//modified by mengxu 2023.004.18
				while(index_plus < front.length-1 && f_jplus1.getNormalisedObjectives(o) == f_j.getNormalisedObjectives(o)){
					index_plus = index_plus + 1;
					f_jplus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[index_plus].fitness);
				}

				int index_minus = j-1;
				//modified by mengxu 2023.004.18
				while(index_minus > 0 && f_jminus1.getNormalisedObjectives(o) == f_j.getNormalisedObjectives(o)){
					index_minus = index_minus - 1;
					f_jminus1 = (NSGA2MultiObjectiveFitnessNormalisation) (front[index_minus].fitness);
				}

//				System.out.println("original min: " + f_j.minObjective[o] );
//			    System.out.println("original max: " + f_j.maxObjective[o] );

				if (max ==  min)
				{
					f_j.sparsity += 0;
				}
				else {
					// store the NSGA2Sparsity in sparsity
//				if((f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min) < 0){
//					System.out.println("biasWeight: " + ((NSGA2MultiObjectiveFitnessNormalisation)f_j).biasWeight[o]);
//				}
					f_j.sparsity += (f_jplus1.getNormalisedObjectives(o) - f_jminus1.getNormalisedObjectives(o)) * ((NSGA2MultiObjectiveFitnessNormalisation)f_j).biasWeight[o];
				}

				//original version
//				System.out.println(f_j.maxObjective[o] - f_j.minObjective[o]);  //1
				// store the NSGA2Sparsity in sparsity
				/*f_j.sparsity += (f_jplus1.getObjective(o) - f_jminus1.getObjective(o))
						/ (f_j.maxObjective[o] - f_j.minObjective[o]);*/
			}
		}
	}

	public ArrayList assignFrontRanks(Subpopulation subpop) {
		Individual[] inds = subpop.individuals; //inds includes all individuals
		ArrayList frontsByRank = MultiObjectiveFitness.partitionIntoRanks(inds);

		int numRanks = frontsByRank.size();
		for (int rank = 0; rank < numRanks; rank++) {
			ArrayList front = (ArrayList) (frontsByRank.get(rank));
			int numInds = front.size();
			for (int ind = 0; ind < numInds; ind++)
				((NSGA2MultiObjectiveFitnessNormalisation) (((Individual) (front.get(ind))).fitness)).rank = rank;
		}
		return frontsByRank;
	}

	public static ArrayList partitionIntoParetoFront(Individual[] inds, ArrayList front, ArrayList nonFront)
	{
		if (front == null)
			front = new ArrayList();

		// put the first guy in the front
		front.add(inds[0]);

		// iterate over all the remaining individuals
		for (int i = 1; i < inds.length; i++)
		{
			Individual ind = (Individual) (inds[i]);

			boolean noOneWasBetter = true;
			int frontSize = front.size();

			// iterate over the entire front
			for (int j = 0; j < frontSize; j++)
			{
				Individual frontmember = (Individual) (front.get(j));

				// if the front member is better than the individual, dump the individual and go to the next one
				if (((MultiObjectiveFitness) (frontmember.fitness)).paretoDominates((MultiObjectiveFitness) (ind.fitness)))
				{
					if (nonFront != null) nonFront.add(ind);
					noOneWasBetter = false;
					break;  // failed.  He's not in the front
				}
				// if the individual was better than the front member, dump the front member.  But look over the
				// other front members (don't break) because others might be dominated by the individual as well.
				else if (((MultiObjectiveFitness) (ind.fitness)).paretoDominates((MultiObjectiveFitness) (frontmember.fitness)))
				{
					yank(j, front);
					// a front member is dominated by the new individual.  Replace him
					frontSize--; // member got removed
					j--;  // because there's another guy we now need to consider in his place
					if (nonFront != null) nonFront.add(frontmember);
				}
			}
			if (noOneWasBetter)
				front.add(ind);
		}
		return front;
	}

	static void yank(int val, ArrayList list)
	{
		int size = list.size();
		list.set(val, list.get(size - 1));
		list.remove(size - 1);
	}

	/**
	 * Build the auxiliary fitness data and reduce the subpopulation to just the
	 * archive, which is returned.
	 */
	//achieve a archive has the same size of original population
	public Individual[] buildArchive(EvolutionState state, int subpop) {
		Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
		ArrayList ranks = assignFrontRanks(state.population.subpops[subpop]); //after this, get different several ranks
		//each rank cosists of the corresponding individuals
		ArrayList newSubpopulation = new ArrayList(); //a new one, size = 0
		int size = ranks.size();
		for (int i = 0; i < size; i++) { //do for each rank separately
			Individual[] rank = (Individual[]) ((ArrayList) (ranks.get(i))).toArray(dummy);
			if(i == 0){
				//store the Pareto front by mengxu 2023.05.30
				this.ParetoFrontFromLastGeneration = rank;//todo: need double check
			}
//			if(rank.length > 5){
//				System.out.print("rank size > 5");
//			}
//			assignSparsity(rank); //assign sparity value for each individual in this rank
//			System.out.println("Print HERE!!!!!!");
			if(((GPRuleEvolutionStateMO)state).useZScoreNormalisation){
				assignSparsityWithZscoreNormalisation(rank); //modified by mengxu 2023.04.21
			}
			else{
				assignSparsity(rank); //assign sparity value for each individual in this rank
			}
			if (rank.length + newSubpopulation.size() >= originalPopSize[subpop]) {
				// first sort the rank by sparsity---from the small one to large one
				ec.util.QuickSort.qsort(rank, new SortComparator() {
					public boolean lt(Object a, Object b) { //Returns true if a < b, else false
						Individual i1 = (Individual) a;
						Individual i2 = (Individual) b;
						return (((NSGA2MultiObjectiveFitnessNormalisation) i1.fitness).sparsity > ((NSGA2MultiObjectiveFitnessNormalisation) i2.fitness).sparsity);
					}

					public boolean gt(Object a, Object b) { //Returns true if a > b, else false
						Individual i1 = (Individual) a;
						Individual i2 = (Individual) b;
						return (((NSGA2MultiObjectiveFitnessNormalisation) i1.fitness).sparsity < ((NSGA2MultiObjectiveFitnessNormalisation) i2.fitness).sparsity);
					}
				});

				// then put the m sparsest individuals in the new population
				int m = originalPopSize[subpop] - newSubpopulation.size(); //how many positions left for new individuals
				for (int j = 0; j < m; j++)
					newSubpopulation.add(rank[j]); //add some individuals based on the sparisity

				// and bail
				break;
			} else {
				// dump in everyone
				for (int j = 0; j < rank.length; j++) //add the while rank directly
					newSubpopulation.add(rank[j]);
			}
		}

		Individual[] archive = (Individual[]) (newSubpopulation.toArray(dummy));

		// maybe force reevaluation
		NSGA2BreederNoEnvironmentalSelectionPhenotypeBreeding breeder = (NSGA2BreederNoEnvironmentalSelectionPhenotypeBreeding) (state.breeder);
		if (breeder.reevaluateElites[subpop])
			for (int i = 0; i < archive.length; i++)
				archive[i].evaluated = false;

		return archive;
	}

	public void evaluatePopulation(final EvolutionState state, Boolean rankIndsIntermediatePop) {
		for (int x = 0; x < state.population.subpops.length; x++)
			state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
	}

	public void zScoreSigmoidNormalisation(EvolutionState state){
		int numObjectives = ((MultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness).objectives.length;
		double[] means = new double[numObjectives];
		double[] stds = new double[numObjectives];

		Individual[] individuals = state.population.subpops[0].individuals;
		List<List<Double>> objectives = new ArrayList<>();
		for(int j=0; j<numObjectives; j++){
			List<Double> obj = new ArrayList<>();
			objectives.add(obj);
			means[j] = 0;
			stds[j] = 0;
		}

		for(int i=0; i<individuals.length; i++){
			double[] objectiveValues = ((MultiObjectiveFitness)state.population.subpops[0].individuals[i].fitness).objectives;
			for(int j=0; j<numObjectives; j++){
				List<Double> obj = objectives.get(j);
				if(objectiveValues[j] >= Double.MAX_VALUE || objectiveValues[j] >= Double.POSITIVE_INFINITY){
				}else{
					obj.add(objectiveValues[j]);
					means[j] += objectiveValues[j];
				}
			}
		}
		for(int j=0; j<numObjectives; j++){
			List<Double> obj = objectives.get(j);
			means[j] = means[j]/obj.size();
		}
		for(int i=0; i<individuals.length; i++){
			double[] objectiveValues = ((MultiObjectiveFitness)state.population.subpops[0].individuals[i].fitness).objectives;
			for(int j=0; j<numObjectives; j++){
				List<Double> obj = objectives.get(j);
				if(objectiveValues[j] >= Double.MAX_VALUE || objectiveValues[j] >= Double.POSITIVE_INFINITY){
				}else{
					stds[j] += Math.pow((objectiveValues[j]-means[j]),2);
				}
			}
		}
		for(int j=0; j<numObjectives; j++){
			List<Double> obj = objectives.get(j);
			stds[j] = Math.sqrt(stds[j]/obj.size());
		}
		for(int i=0; i<individuals.length; i++){
			NSGA2MultiObjectiveFitnessNormalisation fitnessNormalisation = (NSGA2MultiObjectiveFitnessNormalisation)state.population.subpops[0].individuals[i].fitness;
			fitnessNormalisation.normalisation(means, stds);
		}
	}
}
