package mengxu.algorithm.multiobjective.MOEADepsilonC;

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

public class MOEADeCBreeder extends SimpleBreeder {

	private static final long serialVersionUID = 1L;
	private MOEADeCInitializer.NeighborType neighborType;

	public Population breedPopulation(EvolutionState state) {
		MOEADeCInitializer init = (MOEADeCInitializer) state.initializer;

		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;
		Individual[] oldIndsCopy = oldPop.subpops[0].individuals.clone();//modified by mengxu
//		Population newPop = (Population) state.population.emptyClone();

//		Individual[] newInds = newPop.subpops[0].individuals;
//		newPop.subpops[0].individuals = newInds;

		// do regular breeding of this subpopulation
		BreedingPipeline bp = (BreedingPipeline) oldPop.subpops[0].species.pipe_prototype;

		//-------add by mengxu based on jMetal 2021.08.11
		int populationSize = state.population.subpops[0].individuals.length;
		int[] permutation = new int[populationSize];
		MOEADUtils.randomPermutation(state, 0, permutation, populationSize);

//		//update idealPoint
//		((MOEADeCInitializer)state.initializer).updateIdealPoint(state);
//		((MOEADeCInitializer)state.initializer).updateNadirPoint(state);

		//modified by mengxu 2021.08.11
		for (int i = 0; i < state.population.subpops[0].individuals.length; i++) {
			int probIndex = permutation[i];
			this.neighborType = chooseNeighborType(state, 0) ;
//			newInds[probIndex] = (Individual) oldInds[probIndex].clone();
			bp.produce(1, 1, probIndex, 0, oldInds, state, 0);
			MOEADeCMultiObjectiveFitness oldIndFitness = (MOEADeCMultiObjectiveFitness)oldInds[probIndex].fitness; //add 2021.12.08
			oldIndFitness.setSubProblem(init.allSubProblem.get(probIndex)); //add 2021.12.08
			//evaluate immediatelly!
//			System.out.println("evaluate immediatelly!");
			oldInds[probIndex].evaluated = false;
			SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
			prob.evaluate(state, oldInds[probIndex], 0, 0);
			//todo: after this, we need to mapping the individual with the subproblem! 2021.11.23
//			fitnessNormalization(state, newInds[probIndex]);//add 2021.10.15
			((MOEADeCInitializer)state.initializer).updateIdealPoint(oldInds[probIndex]);
//			((MOEADeCInitializer)state.initializer).updateMainObjective(oldInds[probIndex],newInds[probIndex]);
//			((MOEADeCInitializer)state.initializer).updateSubProblem(newInds[probIndex]);
//			checkOldAndNew(state, newInds, oldInd, probIndex); //2021.10.19
			checkOldAndNew(state, oldInds, oldIndsCopy, probIndex); //2021.10.19
//			fitnessNormalization(state, newInds[probIndex]);//add 2021.10.15
//			((MOEADInitializer)state.initializer).updateNadirPoint(newInds[probIndex]);
			updateNeighborhood(oldInds[probIndex], probIndex, neighborType, state, 0, oldInds);
			//updateOldInd 2021.11.24 for parent selection can select new inds
//			newInds[probIndex]=null;//modified by mengxu 2021.08.17
		}
//		checkNewInds(newInds,oldInds);
		oldPop.subpops[0].individuals = oldInds;
		return oldPop;

	}

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
////		((MOEADeCInitializer)state.initializer).updateIdealPoint(state);
////		((MOEADeCInitializer)state.initializer).updateNadirPoint(state);
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
//			//todo: after this, we need to mapping the individual with the subproblem! 2021.11.23
////			fitnessNormalization(state, newInds[probIndex]);//add 2021.10.15
//			((MOEADeCInitializer)state.initializer).updateIdealPoint(newInds[probIndex]);
//			((MOEADeCInitializer)state.initializer).updateMainObjective(oldInds[probIndex],newInds[probIndex]);
////			((MOEADeCInitializer)state.initializer).updateSubProblem(newInds[probIndex]);
//			checkOldAndNew(state, newInds, oldInds, probIndex); //2021.10.19
//			//updateOldInd 2021.11.24 for parent selection can select new inds
//			oldInds[probIndex] = newInds[probIndex];
////			((MOEADeCInitializer)state.initializer).updateNadirPoint(newInds[probIndex]);
//			updateNeighborhood(newInds[probIndex], probIndex, neighborType, state, 0, newInds);
////			newInds[probIndex]=null;//modified by mengxu 2021.08.17
//		}
////		checkNewInds(newInds,oldInds);
//		newPop.subpops[0].individuals = newInds;
//		return newPop;
//
//	}


	public void checkOldAndNew(EvolutionState state, Individual[] oldInds, Individual[] oldIndsCopy, int probId){
		MOEADeCInitializer init = (MOEADeCInitializer) state.initializer;
		double f1, f2;

		boolean oldBetterThanNew = false;

		MOEADeCMultiObjectiveFitness fitnessFirst = (MOEADeCMultiObjectiveFitness)oldIndsCopy[probId].fitness;
		MOEADeCMultiObjectiveFitness fitnessSecond = (MOEADeCMultiObjectiveFitness)oldInds[probId].fitness;
//		int subProbID = fitnessSecond.getSubProblem().getId();
//		MOEADeCMultiObjectiveFitness fitnessFirst = (MOEADeCMultiObjectiveFitness)oldInds[probId].fitness;

		if(fitnessFirst.getNumNotsatisfyConstraints()==0 && fitnessSecond.getNumNotsatisfyConstraints()==0){
			oldBetterThanNew = fitnessFirst.fitness() < fitnessSecond.fitness();
		}else if(fitnessFirst.getNumNotsatisfyConstraints()>0 && fitnessSecond.getNumNotsatisfyConstraints()==0){
			oldBetterThanNew = false;
		}
		else if(fitnessFirst.getNumNotsatisfyConstraints()==0 && fitnessSecond.getNumNotsatisfyConstraints()>0){
			oldBetterThanNew = true;
		}
		else if(fitnessFirst.getNumNotsatisfyConstraints()>0 && fitnessSecond.getNumNotsatisfyConstraints()>0){
			oldBetterThanNew = fitnessFirst.getNumNotsatisfyConstraints() < fitnessSecond.getNumNotsatisfyConstraints();
		}

		if (oldBetterThanNew) {
			oldInds[probId]=(Individual) oldIndsCopy[probId].clone();
		}
	}

	public MOEADeCInitializer.NeighborType getNeighborType() {
		return neighborType;
	}

	protected void checkNewInds(Individual[] newInds, Individual[] oldInds){
		for(int i=0; i< newInds.length; i++){
			if(newInds[i] == null){
				newInds[i] = (Individual)oldInds[i].clone();
			}
		}
	}

	protected MOEADeCInitializer.NeighborType chooseNeighborType(EvolutionState state, int thread) {
		double rnd = state.random[thread].nextDouble();
		MOEADeCInitializer.NeighborType neighborType;

		if (rnd < ((MOEADeCInitializer)state.initializer).getNeighborhoodSelectionProbability()) {
			neighborType = MOEADeCInitializer.NeighborType.NEIGHBOR;
		} else {
			neighborType = MOEADeCInitializer.NeighborType.POPULATION;
		}
		return neighborType ;
	}

	protected  void updateNeighborhood(Individual individual, int subProblemId, MOEADeCInitializer.NeighborType neighborType, EvolutionState state, int thread, Individual[] newInds){
		int size;
		int time;

		time = 0;
		MOEADeCInitializer init = (MOEADeCInitializer) state.initializer;

		if (time >= init.getMaximumNumberOfReplacedSolutions()) {
			return;
		}

		if (neighborType == MOEADeCInitializer.NeighborType.NEIGHBOR) {
			size = init.neighbourhood[subProblemId].length;
		} else {
			size = newInds.length;
		}
		int[] perm = new int[size];

		MOEADUtils.randomPermutation( state, thread, perm, size);

		for (int i = 0; i < size; i++) {
			int k;
			if (neighborType == MOEADeCInitializer.NeighborType.NEIGHBOR) {
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

				boolean oldBetterThanNew = false;

				Individual individualCopy = (Individual) individual.clone(); //modified by mengxu 2021.12.08
				MOEADeCMultiObjectiveFitness fitnessFirst = (MOEADeCMultiObjectiveFitness)individualCopy.fitness;
				MOEADeCMultiObjectiveFitness fitnessSecond = (MOEADeCMultiObjectiveFitness)newInds[k].fitness;
				fitnessFirst.setSubProblem(fitnessSecond.getSubProblem()); //modified by mengxu 2021.12.08

				if(fitnessFirst.getNumNotsatisfyConstraints()==0 && fitnessSecond.getNumNotsatisfyConstraints()==0){
					oldBetterThanNew = fitnessFirst.fitness() < fitnessSecond.fitness();
				}else if(fitnessFirst.getNumNotsatisfyConstraints()>0 && fitnessSecond.getNumNotsatisfyConstraints()==0){
					oldBetterThanNew = false;
				}
				else if(fitnessFirst.getNumNotsatisfyConstraints()==0 && fitnessSecond.getNumNotsatisfyConstraints()>0){
					oldBetterThanNew = true;
				}
				else if(fitnessFirst.getNumNotsatisfyConstraints()>0 && fitnessSecond.getNumNotsatisfyConstraints()>0){
					oldBetterThanNew = fitnessFirst.getNumNotsatisfyConstraints() < fitnessSecond.getNumNotsatisfyConstraints();
				}

				if (oldBetterThanNew) {
					newInds[k]=(Individual) individualCopy.clone();
					((MOEADeCInitializer)state.initializer).updateSubProb(newInds[k], k); //add 2021.11.24
					time++;
				}

			}

			if (time >= init.getMaximumNumberOfReplacedSolutions()) {
				return;
			}
		}
	}

	//modified 2021.10.15
	public void fitnessNormalization(EvolutionState state, Individual ind){
		double[] minFitness = ((GPRuleEvolutionStateMOEADeC)state).minFitnessGen;
		double[] maxFitness = ((GPRuleEvolutionStateMOEADeC)state).maxFitnessGen;
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
