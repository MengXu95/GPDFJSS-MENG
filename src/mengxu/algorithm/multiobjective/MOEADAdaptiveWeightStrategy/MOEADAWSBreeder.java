package mengxu.algorithm.multiobjective.MOEADAdaptiveWeightStrategy;

import ec.BreedingPipeline;
import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleBreeder;
import ec.simple.SimpleProblemForm;
import mengxu.algorithm.multiobjective.MOEAD.GPRuleEvolutionStateMOEAD;
import mengxu.algorithm.multiobjective.MOEAD.MOEADInitializer;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;

public class MOEADAWSBreeder extends SimpleBreeder {

	private static final long serialVersionUID = 1L;
	private MOEADAWSInitializer.NeighborType neighborType;

	public Population breedPopulation(EvolutionState state) {

		Population oldPop = (Population) state.population;
//		Population newPop = (Population) state.population;//modified by mengxu
		Population newPop = (Population) state.population.emptyClone();

		Individual[] oldInds = oldPop.subpops[0].individuals;
//		Individual[] newInds = oldPop.subpops[0].individuals;//modified by mengxu
		Individual[] newInds = new Individual[oldPop.subpops[0].individuals.length];
		newPop.subpops[0].individuals = newInds;

		// do regular breeding of this subpopulation
		BreedingPipeline bp = (BreedingPipeline) newPop.subpops[0].species.pipe_prototype;

		//-------add by mengxu based on jMetal 2021.08.11
		int populationSize = state.population.subpops[0].individuals.length;
		int[] permutation = new int[populationSize];
		MOEADUtils.randomPermutation(state, 0, permutation, populationSize);

		//update idealPoint
		((MOEADAWSInitializer)state.initializer).updateIdealPoint(state);
		((MOEADAWSInitializer)state.initializer).updateNadirPoint(state);

		//modified by mengxu 2021.08.11
		for (int i = 0; i < state.population.subpops[0].individuals.length; i++) {
			int probIndex = permutation[i];
			this.neighborType = chooseNeighborType(state, 0) ;
			newInds[probIndex] = (Individual) oldInds[probIndex].clone();
			bp.produce(1, 1, probIndex, 0, newInds, state, 0);

			//todo: need to modify to evaluate immediately! by mengxu!
			//evaluate immediatelly!
//			System.out.println("evaluate immediatelly!");
			newInds[probIndex].evaluated = false;
			SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
			prob.evaluate(state, newInds[probIndex], 0, 0);
			checkOldAndNew(state, newInds, oldInds, probIndex); //2021.10.19
//			fitnessNormalization(state, newInds[probIndex]);//add 2021.10.15
			((MOEADAWSInitializer)state.initializer).updateIdealPoint(newInds[probIndex]);
			((MOEADAWSInitializer)state.initializer).updateNadirPoint(newInds[probIndex]);
			updateNeighborhood(newInds[probIndex], probIndex, neighborType, state, 0, newInds);
//			newInds[probIndex]=null;//modified by mengxu 2021.08.17
		}
//		checkNewInds(newInds,oldInds);
		updateOldFitness(newInds);
		newPop.subpops[0].individuals = newInds;
		return newPop;

		//-----------------------------------

		// Pass the probIndex as the starting point for each pipeline invocation
		//original by fangfang
//		for (int probIndex = 0; probIndex < state.population.subpops[0].individuals.length; probIndex++) {
//			newInds[probIndex] = (Individual) oldInds[probIndex].clone();
//			bp.produce(1, 1, probIndex, 0, newInds, state, 0);
//		}

//		Individual[] combinedInds = new Individual[oldPop.subpops[0].individuals.length
//				+ newPop.subpops[0].individuals.length];
//		System.arraycopy(newPop.subpops[0].individuals, 0, combinedInds, 0, newPop.subpops[0].individuals.length);
//		System.arraycopy(oldPop.subpops[0].individuals, 0, combinedInds, newPop.subpops[0].individuals.length,
//				oldPop.subpops[0].individuals.length);
//		newPop.subpops[0].individuals = combinedInds;

//		return newPop;
	}

	public void updateOldFitness(Individual[] inds){
		for(int i=0; i<inds.length; i++){
			MOEADAWSMultiObjectiveFitness fitness = (MOEADAWSMultiObjectiveFitness)inds[i].fitness;
			double[] objectives = fitness.objectives;
			fitness.setOldFitness(objectives);
		}
	}

	public void checkOldAndNew(EvolutionState state, Individual[] newInds, Individual[] oldInds, int probId){
		MOEADAWSInitializer init = (MOEADAWSInitializer) state.initializer;
		double f1, f2;

		if(init.tchebycheff){
			f1 = init.calculateTchebycheffScore(oldInds[probId], probId);
			f2 = init.calculateTchebycheffScore(newInds[probId], probId);
		}else{
			f1 = init.calculateWeightSumScore(oldInds[probId], probId);
			f2 = init.calculateWeightSumScore(newInds[probId], probId);
		}

		if (f1 < f2) {
			newInds[probId]=(Individual) oldInds[probId].clone();
		}
	}

	public MOEADAWSInitializer.NeighborType getNeighborType() {
		return neighborType;
	}

	protected void checkNewInds(Individual[] newInds, Individual[] oldInds){
		for(int i=0; i< newInds.length; i++){
			if(newInds[i] == null){
				newInds[i] = (Individual)oldInds[i].clone();
			}
		}
	}

	protected MOEADAWSInitializer.NeighborType chooseNeighborType(EvolutionState state, int thread) {
		double rnd = state.random[thread].nextDouble();
		MOEADAWSInitializer.NeighborType neighborType;

		if (rnd < ((MOEADAWSInitializer)state.initializer).getNeighborhoodSelectionProbability()) {
			neighborType = MOEADAWSInitializer.NeighborType.NEIGHBOR;
		} else {
			neighborType = MOEADAWSInitializer.NeighborType.POPULATION;
		}
		return neighborType ;
	}

	protected  void updateNeighborhood(Individual individual, int subProblemId, MOEADAWSInitializer.NeighborType neighborType, EvolutionState state, int thread, Individual[] newInds){
		int size;
		int time;

		time = 0;
		MOEADAWSInitializer init = (MOEADAWSInitializer) state.initializer;

		if (time >= init.getMaximumNumberOfReplacedSolutions()) {
			return;
		}

		if (neighborType == MOEADAWSInitializer.NeighborType.NEIGHBOR) {
			size = init.neighbourhood[subProblemId].length;
		} else {
			size = newInds.length;
		}
		int[] perm = new int[size];

		MOEADUtils.randomPermutation( state, thread, perm, size);

		for (int i = 0; i < size; i++) {
			int k;
			if (neighborType == MOEADAWSInitializer.NeighborType.NEIGHBOR) {
				k = init.neighbourhood[subProblemId][perm[i]];
			} else {
				k = perm[i];
			}
			double f1, f2;

			//modified by mengxu 2021.08.17
			if(newInds[k] == null){
				newInds[k]=(Individual) individual.clone();
				time++;
			}
			else{
				if(init.tchebycheff){
					f1 = init.calculateTchebycheffScore(newInds[k], k);
					f2 = init.calculateTchebycheffScore(individual, k);
				}else{
					f1 = init.calculateWeightSumScore(newInds[k], k);
					f2 = init.calculateWeightSumScore(individual, k);
				}

				if (f2 < f1) {
					newInds[k]=(Individual) individual.clone();
					time++;
				}
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
		double[] minFitness = ((GPRuleEvolutionStateMOEADAWS)state).minFitnessGen;
		double[] maxFitness = ((GPRuleEvolutionStateMOEADAWS)state).maxFitnessGen;
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
