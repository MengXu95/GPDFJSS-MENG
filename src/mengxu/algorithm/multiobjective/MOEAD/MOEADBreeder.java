package mengxu.algorithm.multiobjective.MOEAD;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2Breeder;
import ec.simple.SimpleBreeder;
import ec.simple.SimpleProblemForm;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;

public class MOEADBreeder extends SimpleBreeder {

	private static final long serialVersionUID = 1L;
	private MOEADInitializer.NeighborType neighborType;

	/**
	 * Override breedPopulation(). We take the result from the super method in
	 * SimpleBreeder and append it to the old population. Hence, after
	 * generation 0, every subsequent call to
	 * <code>NSGA2Evaluator.evaluatePopulation()</code> will be passed a
	 * population of 2x<code>originalPopSize</code> individuals.
	 */
//	public Population breedPopulation(EvolutionState state)
//	{
//		Population oldPop = (Population) state.population;
//		Population newPop = super.breedPopulation(state); //offspring individuals obtained by simpleBreeder
//
//		//modified by mengxu only for single objective
//		Individual[] combinedInds;
//		Subpopulation[] subpops = oldPop.subpops;
//		Subpopulation oldSubpop;
//		Subpopulation newSubpop;
//		int subpopsLength = subpops.length;
//
//		for (int i = 0; i < subpopsLength; i++)
//		{
//			oldSubpop = oldPop.subpops[i];
//			newSubpop = newPop.subpops[i];
//			combinedInds = new Individual[oldSubpop.individuals.length + newSubpop.individuals.length]; //parent and offsprings
//			System.arraycopy(newSubpop.individuals, 0, combinedInds, 0,  newSubpop.individuals.length);
//			System.arraycopy(oldSubpop.individuals, 0, combinedInds,  newSubpop.individuals.length, oldSubpop.individuals.length);
//			newSubpop.individuals = combinedInds;
//		}
//		return newPop;
//	}

//	original
	public Population breedPopulation(EvolutionState state) {

		Population oldPop = (Population) state.population;
//		Population newPop = (Population) state.population;//modified by mengxu
//		Population newPop = (Population) state.population.emptyClone();

		Individual[] oldInds = oldPop.subpops[0].individuals;
		Individual[] oldIndsCopy = oldPop.subpops[0].individuals.clone();//modified by mengxu
//		Individual[] newInds = new Individual[oldPop.subpops[0].individuals.length];
//		newPop.subpops[0].individuals = newInds;

		// do regular breeding of this subpopulation
		BreedingPipeline bp = (BreedingPipeline) oldPop.subpops[0].species.pipe_prototype;

		//-------add by mengxu based on jMetal 2021.08.11
		int populationSize = state.population.subpops[0].individuals.length;
		int[] permutation = new int[populationSize];
		MOEADUtils.randomPermutation(state, 0, permutation, populationSize);

//		//update idealPoint
//		((MOEADInitializer)state.initializer).updateIdealPoint(state);
//		((MOEADInitializer)state.initializer).updateNadirPoint(state);

		//modified by mengxu 2021.08.11
		for (int i = 0; i < state.population.subpops[0].individuals.length; i++) {
			int probIndex = permutation[i];
			this.neighborType = chooseNeighborType(state, 0) ;
//			oldInds[probIndex] = (Individual) oldInds[probIndex].clone();
			bp.produce(1, 1, probIndex, 0, oldInds, state, 0);
			//evaluate immediatelly!
//			System.out.println("evaluate immediatelly!");
			oldInds[probIndex].evaluated = false;
			SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
			prob.evaluate(state, oldInds[probIndex], 0, 0);
//			checkOldAndNew(state, oldInds, oldIndsCopy, probIndex); //2021.10.19//blind by mengxu 2022.09.06 based on Bach's advice, do not need this strategy
//			fitnessNormalization(state, newInds[probIndex]);//add 2021.10.15
//			((MOEADInitializer)state.initializer).updateIdealPoint(oldInds[probIndex]);
//			((MOEADInitializer)state.initializer).updateNadirPoint(newInds[probIndex]);
			if(this.neighborType == MOEADInitializer.NeighborType.NEIGHBOR){
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

//	original's original
//	public Population breedPopulation(EvolutionState state) {
//
//		Population oldPop = (Population) state.population;
////		Population newPop = (Population) state.population;//modified by mengxu
//		Population newPop = (Population) state.population.emptyClone();
//
//		Individual[] oldInds = oldPop.subpops[0].individuals;
////		Individual[] newInds = oldPop.subpops[0].individuals;//modified by mengxu
//		Individual[] newInds = new Individual[oldPop.subpops[0].individuals.length];
//		newPop.subpops[0].individuals = newInds;
//
//		// do regular breeding of this subpopulation
//		BreedingPipeline bp = (BreedingPipeline) newPop.subpops[0].species.pipe_prototype;
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
//			newInds[probIndex] = (Individual) oldInds[probIndex].clone();
//			bp.produce(1, 1, probIndex, 0, newInds, state, 0);
//
//			//todo: need to modify to evaluate immediately! by mengxu!
//			//evaluate immediatelly!
////			System.out.println("evaluate immediatelly!");
//			newInds[probIndex].evaluated = false;
//			SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
//			prob.evaluate(state, newInds[probIndex], 0, 0);
//			checkOldAndNew(state, newInds, oldInds, probIndex); //2021.10.19
////			fitnessNormalization(state, newInds[probIndex]);//add 2021.10.15
//			((MOEADInitializer)state.initializer).updateIdealPoint(newInds[probIndex]);
////			((MOEADInitializer)state.initializer).updateNadirPoint(newInds[probIndex]);
//			updateNeighborhood(newInds[probIndex], probIndex, neighborType, state, 0, newInds);
////			newInds[probIndex]=null;//modified by mengxu 2021.08.17
//		}
////		checkNewInds(newInds,oldInds);
//		newPop.subpops[0].individuals = newInds;
//		return newPop;
//
//	}

	public void checkOldAndNew(EvolutionState state, Individual[] oldInds, Individual[] oldIndsCopy, int probId){
		MOEADInitializer init = (MOEADInitializer) state.initializer;
		double f1, f2;

		if(init.tchebycheff){
			f1 = init.calculateTchebycheffScore(oldInds[probId], probId);
			f2 = init.calculateTchebycheffScore(oldIndsCopy[probId], probId);
		}else{
			f1 = init.calculateWeightSumScore(oldInds[probId], probId);
			f2 = init.calculateWeightSumScore(oldIndsCopy[probId], probId);
		}

		if (f2 < f1) {
			oldInds[probId]=(Individual) oldIndsCopy[probId].clone();
		}
	}

	public MOEADInitializer.NeighborType getNeighborType() {
		return neighborType;
	}

	protected void checkNewInds(Individual[] newInds, Individual[] oldInds){
		for(int i=0; i< newInds.length; i++){
			if(newInds[i] == null){
				newInds[i] = (Individual)oldInds[i].clone();
			}
		}
	}

	protected MOEADInitializer.NeighborType chooseNeighborType(EvolutionState state, int thread) {
		double rnd = state.random[thread].nextDouble();
		MOEADInitializer.NeighborType neighborType;

		if (rnd < ((MOEADInitializer)state.initializer).getNeighborhoodSelectionProbability()) {
			neighborType = MOEADInitializer.NeighborType.NEIGHBOR;
		} else {
			neighborType = MOEADInitializer.NeighborType.POPULATION;
		}
		return neighborType ;
	}

	protected  void updateNeighborhood(Individual individual, int subProblemId, MOEADInitializer.NeighborType neighborType, EvolutionState state, int thread, Individual[] oldInds){
		int size;
		int time;

		time = 0;
		MOEADInitializer init = (MOEADInitializer) state.initializer;

		if (time >= init.getMaximumNumberOfReplacedSolutions()) {
			return;
		}

		if (neighborType == MOEADInitializer.NeighborType.NEIGHBOR) {
			size = init.neighbourhood[subProblemId].length;
		} else {
			size = oldInds.length;
		}
		int[] perm = new int[size];

		MOEADUtils.randomPermutation( state, thread, perm, size);

		for (int i = 0; i < size; i++) {
			int k;
			if (neighborType == MOEADInitializer.NeighborType.NEIGHBOR) {
				k = init.neighbourhood[subProblemId][perm[i]];
			} else {
				k = perm[i];
			}
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

	//added by mengxu 2022.10.01
	protected  void updatePopulation(Individual individual, int subProblemId, MOEADInitializer.NeighborType neighborType, EvolutionState state, int thread, Individual[] oldInds){
		int size;
		int time;

		time = 0;
		MOEADInitializer init = (MOEADInitializer) state.initializer;

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

	//modified 2021.10.15
	public void fitnessNormalization(EvolutionState state, Individual ind){
		double[] minFitness = ((GPRuleEvolutionStateMOEAD)state).minFitnessGen;
		double[] maxFitness = ((GPRuleEvolutionStateMOEAD)state).maxFitnessGen;
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
