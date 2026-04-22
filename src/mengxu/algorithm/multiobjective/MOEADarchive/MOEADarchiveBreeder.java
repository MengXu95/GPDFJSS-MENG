package mengxu.algorithm.multiobjective.MOEADarchive;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleBreeder;
import ec.simple.SimpleProblemForm;
import mengxu.algorithm.multiobjective.MOEAD.MOEADInitializer;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;

public class MOEADarchiveBreeder extends SimpleBreeder {

	private static final long serialVersionUID = 1L;
	private MOEADarchiveInitializer.NeighborType neighborType;

	/**
	 * Override breedPopulation(). We take the result from the super method in
	 * SimpleBreeder and append it to the old population. Hence, after
	 * generation 0, every subsequent call to
	 * <code>NSGA2Evaluator.evaluatePopulation()</code> will be passed a
	 * population of 2x<code>originalPopSize</code> individuals.
	 */
	public Population breedPopulation(EvolutionState state)
	{

		if(((MOEADarchiveInitializer)state.initializer).useMapping){
			Population oldPop = (Population) state.population;
			Population newPop = super.breedPopulation(state); //offspring individuals obtained by simpleBreeder
			//modified by mengxu only for single objective
			Individual[] combinedInds;
			Subpopulation[] subpops = oldPop.subpops;
			Subpopulation oldSubpop;
			Subpopulation newSubpop;
			int subpopsLength = subpops.length;

			for (int i = 0; i < subpopsLength; i++)
			{
				oldSubpop = oldPop.subpops[i];
				newSubpop = newPop.subpops[i];
				combinedInds = new Individual[oldSubpop.individuals.length + newSubpop.individuals.length]; //parent and offsprings
				System.arraycopy(newSubpop.individuals, 0, combinedInds, 0,  newSubpop.individuals.length);
				System.arraycopy(oldSubpop.individuals, 0, combinedInds,  newSubpop.individuals.length, oldSubpop.individuals.length);
				newSubpop.individuals = combinedInds;
			}
			return newPop;
		}
		else{
			Population oldPop = (Population) state.population;

			Individual[] oldInds = oldPop.subpops[0].individuals;
			Individual[] oldIndsCopy = oldPop.subpops[0].individuals.clone();//modified by mengxu
			// do regular breeding of this subpopulation
			BreedingPipeline bp = (BreedingPipeline) oldPop.subpops[0].species.pipe_prototype;

			//-------add by mengxu based on jMetal 2021.08.11
			int populationSize = state.population.subpops[0].individuals.length;
			int[] permutation = new int[populationSize];
			MOEADUtils.randomPermutation(state, 0, permutation, populationSize);

			//modified by mengxu 2021.08.11
			for (int i = 0; i < state.population.subpops[0].individuals.length; i++) {
				int probIndex = permutation[i];
				this.neighborType = chooseNeighborType(state, 0) ;
				bp.produce(1, 1, probIndex, 0, oldInds, state, 0);
				//evaluate immediatelly!
				oldInds[probIndex].evaluated = false;
				SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
				prob.evaluate(state, oldInds[probIndex], 0, 0);
				if(this.neighborType == MOEADarchiveInitializer.NeighborType.NEIGHBOR){
					updateNeighborhood(oldInds[probIndex], probIndex, neighborType, state, 0, oldInds);
				}
				else{
					updatePopulation(oldInds[probIndex], probIndex, neighborType, state, 0, oldInds);
				}
//			updateNeighborhood(oldInds[probIndex], probIndex, neighborType, state, 0, oldInds);
//			newInds[probIndex]=null;//modified by mengxu 2021.08.17
			}
//		checkNewInds(newInds,oldInds);
			oldPop.subpops[0].individuals = oldInds;
			return oldPop;
		}
	}

//	public Population breedPopulation(EvolutionState state) {
//
//		Population oldPop = (Population) state.population;
////		Population newPop = (Population) state.population;//modified by mengxu
////		Population newPop = (Population) state.population.emptyClone();
//
//		Individual[] oldInds = oldPop.subpops[0].individuals;
//		Individual[] oldIndsCopy = oldPop.subpops[0].individuals.clone();//modified by mengxu
////		Individual[] newInds = new Individual[oldPop.subpops[0].individuals.length];
////		newPop.subpops[0].individuals = newInds;
//
//		// do regular breeding of this subpopulation
//		BreedingPipeline bp = (BreedingPipeline) oldPop.subpops[0].species.pipe_prototype;
//
//		//-------add by mengxu based on jMetal 2021.08.11
//		int populationSize = state.population.subpops[0].individuals.length;
//		int[] permutation = new int[populationSize];
//		MOEADUtils.randomPermutation(state, 0, permutation, populationSize);
//
////		//update idealPoint
////		((MOEADInitializer)state.initializer).updateIdealPoint(state);
////		((MOEADInitializer)state.initializer).updateNadirPoint(state);
//
//		//modified by mengxu 2021.08.11
//		for (int i = 0; i < state.population.subpops[0].individuals.length; i++) {
//			int probIndex = permutation[i];
//			this.neighborType = chooseNeighborType(state, 0) ;
////			oldInds[probIndex] = (Individual) oldInds[probIndex].clone();
//			bp.produce(1, 1, probIndex, 0, oldInds, state, 0);//modified by mengxu by change oldInds to oldIndsCopy
//
//			//todo: need to modify to evaluate immediately! by mengxu!
//			//evaluate immediatelly!
////			System.out.println("evaluate immediatelly!");
//			oldInds[probIndex].evaluated = false;//modified by mengxu by change oldInds to oldIndsCopy
//			SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
//			prob.evaluate(state, oldInds[probIndex], 0, 0);//modified by mengxu by change oldInds to oldIndsCopy
//			//the following one line is added by mengxu 2022.08.08
////			checkOldAndNew(state, oldInds, oldIndsCopy, probIndex); //2021.10.19 // hide by mengxu 2022.08.08
////			fitnessNormalization(state, newInds[probIndex]);//add 2021.10.15
//			((MOEADarchiveInitializer)state.initializer).updateCurSchedulingSetObjectiveLowerBoundMtx(oldInds[probIndex]);
////			((MOEADmapInitializer)state.initializer).updateIdealPoint(oldInds[probIndex]);
////			((MOEADInitializer)state.initializer).updateNadirPoint(newInds[probIndex]);
//			int matchingProbID = probIndex;
//			if(((MOEADarchiveInitializer)state.initializer).useMapping && ((MOEADarchiveInitializer)state.initializer).useMapAngle){
//				matchingProbID = ((MOEADarchiveInitializer)state.initializer).subProblemToSolutionMatchingBasedOnAngle(state, (Individual) oldInds[probIndex].clone());//modified by mengxu by change oldInds to oldIndsCopy
//			}
//			else if(((MOEADarchiveInitializer)state.initializer).useMapping && !((MOEADarchiveInitializer)state.initializer).useMapAngle){
//				matchingProbID = ((MOEADarchiveInitializer)state.initializer).subProblemToSolutionMatching(state, (Individual) oldInds[probIndex].clone());//modified by mengxu by change oldInds to oldIndsCopy
//			}
//
//			if(this.neighborType == MOEADarchiveInitializer.NeighborType.NEIGHBOR){
//				updateNeighborhood(oldInds[matchingProbID], matchingProbID, neighborType, state, 0, oldInds);
//			}
//			else{
//				updatePopulation(oldInds[matchingProbID], matchingProbID, neighborType, state, 0, oldInds);
//			}
//			//the following is the original one
////			updateNeighborhood(oldInds[matchingProbID], matchingProbID, neighborType, state, 0, oldInds);//modified by mengxu by change probIndex to matchingProbID
////			newInds[probIndex]=null;//modified by mengxu 2021.08.17
//		}
////		checkNewInds(newInds,oldInds);
//		oldPop.subpops[0].individuals = oldInds;
//		return oldPop;
//
//	}



	//added by mengxu 2022.10.01
	protected  void updatePopulation(Individual individual, int subProblemId, MOEADarchiveInitializer.NeighborType neighborType, EvolutionState state, int thread, Individual[] oldInds){
		int size;
		int time;

		time = 0;
		MOEADarchiveInitializer init = (MOEADarchiveInitializer) state.initializer;

		if (time >= init.getMaximumNumberOfReplacedSolutions()) {
			return;
		}

		size = oldInds.length;

		int[] perm = new int[size];

		MOEADUtils.randomPermutation( state, thread, perm, size);

		for (int i = 0; i < size; i++) {
			int k;
			k = perm[i];

			double f1, f2;

			//modified by mengxu 2021.08.17
			if(init.tchebycheff){
				f1 = init.calculateTchebycheffScore(oldInds[k], k);
				f2 = init.calculateTchebycheffScore(individual, k);
			}else{
				f1 = init.calculateWeightSumScore(oldInds[k], k);
				f2 = init.calculateWeightSumScore(individual, k);
			}

			if (f2 < f1) {
				oldInds[k]=(Individual) individual.clone();
				time++;
			}


//			//original------------------------------------
//			f1 = init.calculateTchebycheffScore(newInds[k], k);
//			f2 = init.calculateTchebycheffScore(individual, k);
//
//
////			f1 = fitnessFunction(newInds[k], ((MOEADInitializer)state.initializer).weights[k], state);
////			f2 = fitnessFunction(individual, ((MOEADInitializer)state.initializer).weights[k], state);
//
//			if (f2 < f1) {
//				newInds[k]=(Individual) individual.clone();
//				time++;
//			}
			//----------------------------------------------

			if (time >= init.getMaximumNumberOfReplacedSolutions()) {
				return;
			}
		}
	}


	public void checkOldAndNew(EvolutionState state, Individual[] oldInds, Individual[] oldIndsCopy, int probId){
		MOEADarchiveInitializer init = (MOEADarchiveInitializer) state.initializer;
		double f1, f2;

		if(init.tchebycheff){
			if(init.useOddEvenStrategy){
				f1 = init.calculateTchebycheffScoreBasedOnOddAndEven(oldInds[probId], probId);
				f2 = init.calculateTchebycheffScoreBasedOnOddAndEven(oldIndsCopy[probId], probId);
			}
			else{
				f1 = init.calculateTchebycheffScore(oldInds[probId], probId);
				f2 = init.calculateTchebycheffScore(oldIndsCopy[probId], probId);
			}
		}else{
			f1 = init.calculateWeightSumScore(oldInds[probId], probId);
			f2 = init.calculateWeightSumScore(oldIndsCopy[probId], probId);
		}

		if (f2 < f1) {
			oldInds[probId]=(Individual) oldIndsCopy[probId].clone();
		}
	}

	public MOEADarchiveInitializer.NeighborType getNeighborType() {
		return neighborType;
	}

	protected void checkNewInds(Individual[] newInds, Individual[] oldInds){
		for(int i=0; i< newInds.length; i++){
			if(newInds[i] == null){
				newInds[i] = (Individual)oldInds[i].clone();
			}
		}
	}

	protected MOEADarchiveInitializer.NeighborType chooseNeighborType(EvolutionState state, int thread) {
		double rnd = state.random[thread].nextDouble();
		MOEADarchiveInitializer.NeighborType neighborType;

		if (rnd < ((MOEADarchiveInitializer)state.initializer).getNeighborhoodSelectionProbability()) {
			neighborType = MOEADarchiveInitializer.NeighborType.NEIGHBOR;
		} else {
			neighborType = MOEADarchiveInitializer.NeighborType.POPULATION;
		}
		this.neighborType = neighborType;
		return neighborType ;
	}

	protected  void updateNeighborhood(Individual individual, int subProblemId, MOEADarchiveInitializer.NeighborType neighborType, EvolutionState state, int thread, Individual[] oldInds){
		int size;
		int time;

		time = 0;
		MOEADarchiveInitializer init = (MOEADarchiveInitializer) state.initializer;

		if (time >= init.getMaximumNumberOfReplacedSolutions()) {
			return;
		}

		if (neighborType == MOEADarchiveInitializer.NeighborType.NEIGHBOR) {
			size = init.neighbourhood[subProblemId].length;
		} else {
			size = oldInds.length;
		}
		int[] perm = new int[size];

		MOEADUtils.randomPermutation( state, thread, perm, size);

		for (int i = 0; i < size; i++) {
			int k;
			if (neighborType == MOEADarchiveInitializer.NeighborType.NEIGHBOR) {
				k = init.neighbourhood[subProblemId][perm[i]];
			} else {
				k = perm[i];
			}
			double f1, f2;

			//modified by mengxu 2021.08.17
			if(init.tchebycheff){
				if(init.useOddEvenStrategy){
					f1 = init.calculateTchebycheffScoreBasedOnOddAndEven(oldInds[k], k);
					f2 = init.calculateTchebycheffScoreBasedOnOddAndEven(individual, k);
				}
				else{
					f1 = init.calculateTchebycheffScore(oldInds[k], k);
					f2 = init.calculateTchebycheffScore(individual, k);
				}
			}else{
				f1 = init.calculateWeightSumScore(oldInds[k], k);
				f2 = init.calculateWeightSumScore(individual, k);
			}

			if (f2 < f1) {
				oldInds[k]=(Individual) individual.clone();
				time++;
			}


//			//original------------------------------------
//			f1 = init.calculateTchebycheffScore(newInds[k], k);
//			f2 = init.calculateTchebycheffScore(individual, k);
//
//
////			f1 = fitnessFunction(newInds[k], ((MOEADInitializer)state.initializer).weights[k], state);
////			f2 = fitnessFunction(individual, ((MOEADInitializer)state.initializer).weights[k], state);
//
//			if (f2 < f1) {
//				newInds[k]=(Individual) individual.clone();
//				time++;
//			}
			//----------------------------------------------

			if (time >= init.getMaximumNumberOfReplacedSolutions()) {
				return;
			}
		}
	}

	//modified 2021.10.15
	public void fitnessNormalization(EvolutionState state, Individual ind){
		double[] minFitness = ((GPRuleEvolutionStateMOEADarchive)state).minFitnessGen;
		double[] maxFitness = ((GPRuleEvolutionStateMOEADarchive)state).maxFitnessGen;
		double[] multiFitness = ((MultiObjectiveFitness)ind.fitness).objectives;
		int numObjective = multiFitness.length;

		double[] normalisationFitness = new double[numObjective];
		for (int j = 0; j < numObjective; j++) {
			if (multiFitness[j] >= Double.POSITIVE_INFINITY || multiFitness[j] >= Double.MAX_VALUE) {
				double value = Double.POSITIVE_INFINITY;
				normalisationFitness[j] = value;
			} else if (maxFitness[j] - minFitness[j] <= 0) {
				System.out.println("Error! maxFitnessCase-minFitnessCase could not be 0.");
			} else {
				if(multiFitness[j] > maxFitness[j]){
					maxFitness[j] = multiFitness[j];
					//not right if the upper and lower bound changed!!!
				}
				if(multiFitness[j] < minFitness[j]){
					minFitness[j] = multiFitness[j];
				}
				double value = (multiFitness[j] - minFitness[j]) / (maxFitness[j] - minFitness[j]);
				normalisationFitness[j] = value;
			}
		}
		((MultiObjectiveFitness)ind.fitness).objectives = normalisationFitness;
	}

//	public double fitnessFunction(Individual individual, double[] weights, EvolutionState state){
//		double fitness = 0;
//
//		if(((MOEADInitializer)state.initializer).tchebycheff) {
//			double maxFun = -1.0e+30;
//
//			double[] idealPoint = ((MOEADInitializer)state.initializer).idealPoint;
//			for (int n = 0; n < ((MOEADInitializer)state.initializer).numObjectives; n++) {
//				double diff = Math.abs(((MOEADMultiObjectiveFitness)individual.fitness).objectives[n] - idealPoint[n]);
//
//				double feval;
//				if (weights[n] == 0) {
//					feval = 0.0001 * diff;
//				} else {
//					feval = diff * weights[n];
//				}
//				if (feval > maxFun) {
//					maxFun = feval;
//				}
//			}
//
//			fitness = maxFun;
//		}
//		else {
//			System.out.println("Error! MOEAD.fitnessFunction: unknown type.");
//		}
//		return fitness;
//	}
}
