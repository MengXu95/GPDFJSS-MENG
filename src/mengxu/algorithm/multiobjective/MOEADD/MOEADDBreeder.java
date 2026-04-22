package mengxu.algorithm.multiobjective.MOEADD;

import ec.BreedingPipeline;
import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;
import ec.simple.SimpleBreeder;
import ec.simple.SimpleProblemForm;
import mengxu.algorithm.multiobjective.MOEAD.MOEADInitializer;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;

import java.util.ArrayList;

public class MOEADDBreeder extends SimpleBreeder {

	private static final long serialVersionUID = 1L;

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
		((MOEADDInitializer)state.initializer).updateIdealPoint(state);

		//modified by mengxu 2021.08.11
		for (int i = 0; i < state.population.subpops[0].individuals.length; i++) {
			int probIndex = permutation[i];
			MOEADDInitializer.NeighborType neighborType = chooseNeighborType(state, 0) ;
			newInds[probIndex] = (Individual) oldInds[probIndex].clone();
			bp.produce(1, 1, probIndex, 0, newInds, state, 0);

			//todo: need to modify to evaluate immediately! by mengxu!
			//evaluate immediatelly!
//			System.out.println("evaluate immediatelly!");
			newInds[probIndex].evaluated = false;
			SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
			prob.evaluate(state, newInds[probIndex], 0, 0);
			((MOEADDInitializer)state.initializer).updateIdealPoint(newInds[probIndex]);
			((MOEADDInitializer)state.initializer).updateNadirPoint(newInds[probIndex]);
//			updateNeighborhood(newInds[probIndex], probIndex, neighborType, state, 0, newInds);
			updateArchive(state,newInds[probIndex]);// todo: need to modifiedy, how to implement the function "updateArchive(child)"
		}
//		checkNewInds(newInds,oldInds);
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

	protected void checkNewInds(Individual[] newInds, Individual[] oldInds){
		for(int i=0; i< newInds.length; i++){
			if(newInds[i] == null){
				newInds[i] = (Individual)oldInds[i].clone();
			}
		}
	}

	protected MOEADDInitializer.NeighborType chooseNeighborType(EvolutionState state, int thread) {
		double rnd = state.random[thread].nextDouble();
		MOEADDInitializer.NeighborType neighborType;

		if (rnd < ((MOEADDInitializer)state.initializer).getNeighborhoodSelectionProbability()) {
			neighborType = MOEADDInitializer.NeighborType.NEIGHBOR;
		} else {
			neighborType = MOEADDInitializer.NeighborType.POPULATION;
		}
		return neighborType ;
	}

	protected  void updateNeighborhood(Individual individual, int subProblemId, MOEADDInitializer.NeighborType neighborType, EvolutionState state, int thread, Individual[] newInds){
		int size;
		int time;

		time = 0;
		MOEADDInitializer init = (MOEADDInitializer) state.initializer;

		if (neighborType == MOEADDInitializer.NeighborType.NEIGHBOR) {
			size = init.neighbourhood[subProblemId].length;
		} else {
			size = newInds.length;
		}
		int[] perm = new int[size];

		MOEADUtils.randomPermutation( state, thread, perm, size);

		for (int i = 0; i < size; i++) {
			int k;
			if (neighborType == MOEADDInitializer.NeighborType.NEIGHBOR) {
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
				}
				else{
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
////			f1 = fitnessFunction(newInds[k], ((MOEADDInitializer)state.initializer).weights[k], state);
////			f2 = fitnessFunction(individual, ((MOEADDInitializer)state.initializer).weights[k], state);
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



	/**
	 * update the parent population by using the ENLU method, instead of fast non-dominated sorting
	 * 2021.10.19
	 */
	public void updateArchive(EvolutionState state, Individual indiv) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		// find the location of 'indiv'
		int location = setLocation(state, indiv, init.idealPoint, init.nadirPoint);
//		int location = (int) indiv.attributes().get("region");//todo: need to check 3021.10.19

		init.numRanks = nondominated_sorting_add(state, indiv);

		if (init.numRanks == 1) {
			deleteRankOne(state, indiv, location);
		} else {
			ArrayList<Individual> lastFront = new ArrayList<>(populationSize);
			int frontSize = countRankOnes(state, init.numRanks - 1);
			if (frontSize == 0) {  // the last non-domination level only contains 'indiv'
				frontSize++;
				lastFront.add(indiv);
			} else {
				for (int i = 0; i < populationSize; i++) {
					if (init.rankIdx[init.numRanks - 1][i] == 1) {
						lastFront.add((Individual) oldInds[i]);
					}
				}
				if ((((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank) == (init.numRanks - 1)) {
//				if ((ranking.getRank(indiv)) == (numRanks - 1)) {
//        if (rankSolution.getOrDefault(indiv, 0) == (numRanks - 1)) {
					frontSize++;
					lastFront.add(indiv);
				}
			}

			if (frontSize == 1 && lastFront.get(0).equals(indiv)) {  // the last non-domination level only has 'indiv'
				int curNC = countOnes(state, location);
				if (curNC > 0) {  // if the subregion of 'indiv' has other solution, drop 'indiv'
					nondominated_sorting_delete(state, indiv);
				} else {  // if the subregion of 'indiv' has no solution, keep 'indiv'
					deleteCrowdRegion1(state, indiv, location);
				}
			} else if (frontSize == 1 && !lastFront.get(0).equals(indiv)) { // the last non-domination level only has one solution, but not 'indiv'
				int targetIdx = findPosition(state, lastFront.get(0));
				int parentLocation = findRegion(state, targetIdx);
				int curNC = countOnes(state, parentLocation);
				if (parentLocation == location) {
					curNC++;
				}

				if (curNC == 1) {  // the subregion only has the solution 'targetIdx', keep solution 'targetIdx'
					deleteCrowdRegion2(state,indiv, location);
				} else {  // the subregion contains some other solutions, drop solution 'targetIdx'
					int indivRank = (((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank);
					int targetRank = (((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[targetIdx])).fitness)).rank);

//					int indivRank = ranking.getRank(indiv);
//					int targetRank = ranking.getRank(population.get(targetIdx));
					init.rankIdx[targetRank][targetIdx] = 0;
					init.rankIdx[indivRank][targetIdx] = 1;

					Individual targetSol = oldInds[targetIdx];

					replace(state, targetIdx, indiv);
					init.subregionIdx[parentLocation][targetIdx] = 0;
					init.subregionIdx[location][targetIdx] = 1;

					// update the non-domination level structure
					nondominated_sorting_delete(state,targetSol);
				}
			} else {

				double indivFitness = init.calculateWeightSumScore(indiv, location);
//				double indivFitness = fitnessFunction(indiv, lambda[location]);

				// find the index of the solution in the last non-domination level, and its corresponding subregion
				int[] idxArray = new int[frontSize];
				int[] regionArray = new int[frontSize];

				for (int i = 0; i < frontSize; i++) {
					idxArray[i] = findPosition(state,lastFront.get(i));
					if (idxArray[i] == -1) {
						regionArray[i] = location;
					} else {
						regionArray[i] = findRegion(state,idxArray[i]);
					}
				}

				// find the most crowded subregion, if more than one exist, keep them in 'crowdList'
				ArrayList<Integer> crowdList = new ArrayList<>();
				int crowdIdx;
				int nicheCount = countOnes(state,regionArray[0]);
				if (regionArray[0] == location) {
					nicheCount++;
				}
				crowdList.add(regionArray[0]);
				for (int i = 1; i < frontSize; i++) {
					int curSize = countOnes(state,regionArray[i]);
					if (regionArray[i] == location) {
						curSize++;
					}
					if (curSize > nicheCount) {
						crowdList.clear();
						nicheCount = curSize;
						crowdList.add(regionArray[i]);
					} else if (curSize == nicheCount) {
						crowdList.add(regionArray[i]);
					}
				}
				// find the index of the most crowded subregion
				if (crowdList.size() == 1) {
					crowdIdx = crowdList.get(0);
				} else {
					int listLength = crowdList.size();
					crowdIdx = crowdList.get(0);
					double sumFitness = sumFitness(state,crowdIdx);
					if (crowdIdx == location) {
						sumFitness = sumFitness + indivFitness;
					}
					for (int i = 1; i < listLength; i++) {
						int curIdx = crowdList.get(i);
						double curFitness = sumFitness(state,curIdx);
						if (curIdx == location) {
							curFitness = curFitness + indivFitness;
						}
						if (curFitness > sumFitness) {
							crowdIdx = curIdx;
							sumFitness = curFitness;
						}
					}
				}

				switch (nicheCount) {
					case 0:
						System.out.println("Impossible empty subregion!!!");
						break;
					case 1:
						// if the subregion of each solution in the last non-domination level only has one solution, keep them all
						deleteCrowdRegion2(state,indiv, location);
						break;
					default:
						// delete the worst solution from the most crowded subregion in the last non-domination level
						ArrayList<Integer> list = new ArrayList<>();
						for (int i = 0; i < frontSize; i++) {
							if (regionArray[i] == crowdIdx) {
								list.add(i);
							}
						}
						if (list.isEmpty()) {
							System.out.println("Cannot happen!!!");
						} else {
							double maxFitness, curFitness;
							int targetIdx = list.get(0);
							if (idxArray[targetIdx] == -1) {
								maxFitness = indivFitness;
							} else {
								maxFitness = init.calculateWeightSumScore(oldInds[idxArray[targetIdx]], crowdIdx);
//								maxFitness = fitnessFunction(population.get(idxArray[targetIdx]), lambda[crowdIdx]);
							}
							for (int i = 1; i < list.size(); i++) {
								int curIdx = list.get(i);
								if (idxArray[curIdx] == -1) {
									curFitness = indivFitness;
								} else {
									curFitness = init.calculateWeightSumScore(oldInds[idxArray[curIdx]], crowdIdx);
//									curFitness = fitnessFunction(population.get(idxArray[curIdx]), lambda[crowdIdx]);
								}
								if (curFitness > maxFitness) {
									targetIdx = curIdx;
									maxFitness = curFitness;
								}
							}
							if (idxArray[targetIdx] == -1) {
								nondominated_sorting_delete(state,indiv);
							} else {
								//indiv.getRank();
								int indivRank = (((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank);
								int targetRank = (((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[idxArray[targetIdx]])).fitness)).rank);

//								int indivRank = ranking.getRank(indiv);

								//int targetRank = ((DoubleSolution) population.get(idxArray[targetIdx])).getRank();
//								int targetRank = ranking.getRank(population.get(idxArray[targetIdx])) ;

								init.rankIdx[targetRank][idxArray[targetIdx]] = 0;
								init.rankIdx[indivRank][idxArray[targetIdx]] = 1;

								Individual targetSol = oldInds[idxArray[targetIdx]];

								replace(state,idxArray[targetIdx], indiv);
								init.subregionIdx[crowdIdx][idxArray[targetIdx]] = 0;
								init.subregionIdx[location][idxArray[targetIdx]] = 1;

								// update the non-domination level structure
								nondominated_sorting_delete(state, targetSol);
							}
						}
						break;
				}
			}
		}
	}

	/**
	 * delete a solution from the most crowded subregion (this function happens when: it should delete
	 * the solution in the 'parentLocation' subregion, but since this subregion only has one solution,
	 * it should be kept)
	 */
	public void deleteCrowdRegion2(EvolutionState state, Individual indiv, int location) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		double indivFitness = init.calculateWeightSumScore(indiv, location);

//		double indivFitness = fitnessFunction(indiv, lambda[location]);

		// find the most crowded subregion, if there are more than one, keep them in crowdList
		ArrayList<Integer> crowdList = new ArrayList<>();
		int crowdIdx;
		int nicheCount = countOnes(state,0);
		if (location == 0) {
			nicheCount++;
		}
		crowdList.add(0);
		for (int i = 1; i < populationSize; i++) {
			int curSize = countOnes(state,i);
			if (location == i) {
				curSize++;
			}
			if (curSize > nicheCount) {
				crowdList.clear();
				nicheCount = curSize;
				crowdList.add(i);
			} else if (curSize == nicheCount) {
				crowdList.add(i);
			}
		}
		// determine the index of the crowded subregion
		if (crowdList.size() == 1) {
			crowdIdx = crowdList.get(0);
		} else {
			int listLength = crowdList.size();
			crowdIdx = crowdList.get(0);
			double sumFitness = sumFitness(state,crowdIdx);
			if (crowdIdx == location) {
				sumFitness = sumFitness + indivFitness;
			}
			for (int i = 1; i < listLength; i++) {
				int curIdx = crowdList.get(i);
				double curFitness = sumFitness(state,curIdx);
				if (curIdx == location) {
					curFitness = curFitness + indivFitness;
				}
				if (curFitness > sumFitness) {
					crowdIdx = curIdx;
					sumFitness = curFitness;
				}
			}
		}

		// find the solution indices within the 'crowdIdx' subregion
		ArrayList<Integer> indList = new ArrayList<>();
		for (int i = 0; i < populationSize; i++) {
			if (init.subregionIdx[crowdIdx][i] == 1) {
				indList.add(i);
			}
		}
		if (crowdIdx == location) {
			int temp = -1;
			indList.add(temp);
		}

		// find the solution with the largest rank
		ArrayList<Integer> maxRankList = new ArrayList<>();
		//int maxRank = ((DoubleSolution) population.get(indList.get(0))).getRank();
		int maxRank = ((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[indList.get(0)])).fitness)).rank;
//		int maxRank = (int) population.get(indList.get(0)).attributes().get(ranking.getAttributedId());
		maxRankList.add(indList.get(0));
		for (int i = 1; i < indList.size(); i++) {
			int curRank;
			if (indList.get(i) == -1) {
				//curRank = indiv.getRank();
				curRank = ((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank;
//				curRank = (int) indiv.attributes().get(ranking.getAttributedId());
			} else {
				//curRank = ((DoubleSolution) population.get(indList.get(i))).getRank();
				curRank = ((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[indList.get(i)])).fitness)).rank;
//				curRank = (int) population.get(indList.get(i)).attributes().get(ranking.getAttributedId());
			}

			if (curRank > maxRank) {
				maxRankList.clear();
				maxRank = curRank;
				maxRankList.add(indList.get(i));
			} else if (curRank == maxRank) {
				maxRankList.add(indList.get(i));
			}
		}

		double maxFitness;
		int rankSize = maxRankList.size();
		int targetIdx = maxRankList.get(0);
		if (targetIdx == -1) {
			maxFitness = indivFitness;
		} else {
			maxFitness = init.calculateWeightSumScore(oldInds[targetIdx], crowdIdx);
//			maxFitness = fitnessFunction(population.get(targetIdx), lambda[crowdIdx]);
		}
		for (int i = 1; i < rankSize; i++) {
			double curFitness;
			int curIdx = maxRankList.get(i);
			if (curIdx == -1) {
				curFitness = indivFitness;
			} else {
				curFitness = init.calculateWeightSumScore(oldInds[curIdx], crowdIdx);
//				curFitness = fitnessFunction(population.get(curIdx), lambda[crowdIdx]);
			}

			if (curFitness > maxFitness) {
				targetIdx = curIdx;
				maxFitness = curFitness;
			}
		}

		if (targetIdx == -1) {

			nondominated_sorting_delete(state,indiv);

		} else {
			//int indivRank = indiv.getRank();
			int indivRank = ((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank;
//			int indivRank = (int) indiv.attributes().get(ranking.getAttributedId());
			//int targetRank = ((DoubleSolution) population.get(targetIdx)).getRank();
			int targetRank = ((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[targetIdx])).fitness)).rank;
//			int targetRank = (int) population.get(targetIdx).attributes().get(ranking.getAttributedId());
			init.rankIdx[targetRank][targetIdx] = 0;
			init.rankIdx[indivRank][targetIdx] = 1;

			Individual targetSol = oldInds[targetIdx];

			replace(state,targetIdx, indiv);
			init.subregionIdx[crowdIdx][targetIdx] = 0;
			init.subregionIdx[location][targetIdx] = 1;

			// update the non-domination level structure of the population
			nondominated_sorting_delete(state,targetSol);
		}

	}

	/**
	 * find the index of the solution 'indiv' in the population
	 */
	public int findPosition(EvolutionState state, Individual indiv) {
		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		for (int i = 0; i < populationSize; i++) {
			if (indiv.equals(oldInds[i])) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * find the subregion of the 'idx'th solution in the population
	 */
	public int findRegion(EvolutionState state, int idx) {
		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		for (int i = 0; i < populationSize; i++) {
			if (init.subregionIdx[i][idx] == 1) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * Delete a solution from the most crowded subregion (this function only happens when: it should
	 * delete 'indiv' based on traditional method. However, the subregion of 'indiv' only has one
	 * solution, so it should be kept)
	 */
	public void deleteCrowdRegion1(EvolutionState state, Individual indiv, int location) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		// find the most crowded subregion, if more than one such subregion exists, keep them in the crowdList
		ArrayList<Integer> crowdList = new ArrayList<>();
		int crowdIdx;
		int nicheCount = countOnes(state,0);
		crowdList.add(0);
		for (int i = 1; i < populationSize; i++) {
			int curSize = countOnes(state,i);
			if (curSize > nicheCount) {
				crowdList.clear();
				nicheCount = curSize;
				crowdList.add(i);
			} else if (curSize == nicheCount) {
				crowdList.add(i);
			}
		}
		// find the index of the crowded subregion
		if (crowdList.size() == 1) {
			crowdIdx = crowdList.get(0);
		} else {
			int listLength = crowdList.size();
			crowdIdx = crowdList.get(0);
			double sumFitness = sumFitness(state,crowdIdx);
			for (int i = 1; i < listLength; i++) {
				int curIdx = crowdList.get(i);
				double curFitness = sumFitness(state,curIdx);
				if (curFitness > sumFitness) {
					crowdIdx = curIdx;
					sumFitness = curFitness;
				}
			}
		}

		// find the solution indices within the 'crowdIdx' subregion
		ArrayList<Integer> indList = new ArrayList<>();
		for (int i = 0; i < populationSize; i++) {
			if (init.subregionIdx[crowdIdx][i] == 1) {
				indList.add(i);
			}
		}

		// find the solution with the largest rank
		ArrayList<Integer> maxRankList = new ArrayList<>();
		//int maxRank = ((DoubleSolution) population.get(indList.get(0))).getRank();
		int maxRank = ((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[indList.get(0)])).fitness)).rank;
//		int maxRank = (int) population.get(indList.get(0)).attributes().get(ranking.getAttributedId());
		maxRankList.add(indList.get(0));
		for (int i = 1; i < indList.size(); i++) {
			//int curRank = ((DoubleSolution) population.get(indList.get(i))).getRank();
			int curRank = ((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[indList.get(i)])).fitness)).rank;
//			int curRank = (int) population.get(indList.get(i)).attributes().get(ranking.getAttributedId());
			if (curRank > maxRank) {
				maxRankList.clear();
				maxRank = curRank;
				maxRankList.add(indList.get(i));
			} else if (curRank == maxRank) {
				maxRankList.add(indList.get(i));
			}
		}

		// find the solution with the largest rank and worst fitness
		int rankSize = maxRankList.size();
		int targetIdx = maxRankList.get(0);
//		double maxFitness = fitnessFunction(population.get(targetIdx), lambda[crowdIdx]);
		double maxFitness = init.calculateWeightSumScore(oldInds[targetIdx], crowdIdx);
		for (int i = 1; i < rankSize; i++) {
			int curIdx = maxRankList.get(i);
			double curFitness = init.calculateWeightSumScore(oldInds[curIdx], crowdIdx);
//			double curFitness = fitnessFunction(population.get(curIdx), lambda[crowdIdx]);
			if (curFitness > maxFitness) {
				targetIdx = curIdx;
				maxFitness = curFitness;
			}
		}

		//int indivRank = indiv.getRank();
		int indivRank = ((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank;
//		int indivRank = (int) indiv.attributes().get(ranking.getAttributedId());
		//int targetRank = ((DoubleSolution) population.get(targetIdx)).getRank();
		int targetRank = ((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[targetIdx])).fitness)).rank;
//		int targetRank = (int) population.get(targetIdx).attributes().get(ranking.getAttributedId());
		init.rankIdx[targetRank][targetIdx] = 0;
		init.rankIdx[indivRank][targetIdx] = 1;

		Individual targetSol = oldInds[targetIdx];

		replace(state,targetIdx, indiv);
		init.subregionIdx[crowdIdx][targetIdx] = 0;
		init.subregionIdx[location][targetIdx] = 1;

		// update the non-domination level structure
		nondominated_sorting_delete(state,targetSol);

	}

	/**
	 * update the non-domination level structure after deleting a solution
	 */
	public void nondominated_sorting_delete(EvolutionState state, Individual indiv) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		// find the non-domination level of 'indiv'
		//int indivRank = indiv.getRank();
		int indivRank = ((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank;
//		int indivRank = ranking.getRank(indiv);

		ArrayList<Integer> curLevel = new ArrayList<>();  // used to keep the solutions in the current non-domination level
		ArrayList<Integer> dominateList = new ArrayList<>();  // used to keep the solutions need to be moved

		for (int i = 0; i < populationSize; i++) {
			if (init.rankIdx[indivRank][i] == 1) {
				curLevel.add(i);
			}
		}

		int flag;
		// find the solutions belonging to the 'indivRank+1'th level and are dominated by 'indiv'
		int investigateRank = indivRank + 1;
		if (investigateRank < init.numRanks) {
			for (int i = 0; i < populationSize; i++) {
				if (init.rankIdx[investigateRank][i] == 1) {
					flag = 0;
					if (checkDominance(state, indiv, oldInds[i]) == 1) {
						for (int j = 0; j < curLevel.size(); j++) {
							if (checkDominance(state, oldInds[i], oldInds[curLevel.get(j)]) == -1) {
								flag = 1;
								break;
							}
						}
						if (flag == 0) {  // the ith solution can move to the prior level
							dominateList.add(i);
							init.rankIdx[investigateRank][i] = 0;
							init.rankIdx[investigateRank - 1][i] = 1;
							//((DoubleSolution) population.get(i)).setRank(investigateRank - 1);
							((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[i])).fitness)).rank = investigateRank - 1;
//							population.get(i).attributes().put(ranking.getAttributedId(), investigateRank - 1);
						}
					}
				}
			}
		}

		int curIdx;
		int curListSize = dominateList.size();
		while (curListSize != 0) {
			curLevel.clear();
			for (int i = 0; i < populationSize; i++) {
				if (init.rankIdx[investigateRank][i] == 1) {
					curLevel.add(i);
				}
			}
			investigateRank = investigateRank + 1;

			if (investigateRank < init.numRanks) {
				for (int i = 0; i < curListSize; i++) {
					curIdx = dominateList.get(i);
					for (int j = 0; j < populationSize; j++) {
						if (j == populationSize) {
							System.err.println("There are problems");
						}
						if (init.rankIdx[investigateRank][j] == 1) {
							flag = 0;
							if (checkDominance(state, oldInds[curIdx], oldInds[j]) == 1) {
								for (int k = 0; k < curLevel.size(); k++) {
									if (checkDominance(state, oldInds[j], oldInds[curLevel.get(k)]) == -1) {
										flag = 1;
										break;
									}
								}
								if (flag == 0) {
									dominateList.add(j);
									init.rankIdx[investigateRank][j] = 0;
									init.rankIdx[investigateRank - 1][j] = 1;
									//((DoubleSolution) population.get(j)).setRank(investigateRank - 1);
									((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[j])).fitness)).rank = investigateRank - 1;
//									population.get(j).attributes().put(ranking.getAttributedId(), investigateRank - 1);
								}
							}
						}
					}
				}
			}
			for (int i = 0; i < curListSize; i++) {
				dominateList.remove(0);
			}

			curListSize = dominateList.size();
		}

	}

	/**
	 * if there is only one non-domination level (i.e., all solutions are non-dominated with each
	 * other), we should delete a solution from the most crowded subregion
	 */
	public void deleteRankOne(EvolutionState state, Individual indiv, int location) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		double indivFitness = init.calculateWeightSumScore(indiv, location);
//		double indivFitness = fitnessFunction(indiv, lambda[location]);

		// find the most crowded subregion, if there are more than one, keep them in crowdList
		ArrayList<Integer> crowdList = new ArrayList<>();
		int crowdIdx;
		int nicheCount = countOnes(state,0);
		if (location == 0) {
			nicheCount++;
		}
		crowdList.add(0);
		for (int i = 1; i < populationSize; i++) {
			int curSize = countOnes(state, i);
			if (location == i) {
				curSize++;
			}
			if (curSize > nicheCount) {
				crowdList.clear();
				nicheCount = curSize;
				crowdList.add(i);
			} else if (curSize == nicheCount) {
				crowdList.add(i);
			}
		}
		// determine the index of the crowded subregion
		if (crowdList.size() == 1) {
			crowdIdx = crowdList.get(0);
		} else {
			int listLength = crowdList.size();
			crowdIdx = crowdList.get(0);
			double sumFitness = sumFitness(state, crowdIdx);
			if (crowdIdx == location) {
				sumFitness = sumFitness + indivFitness;
			}
			for (int i = 1; i < listLength; i++) {
				int curIdx = crowdList.get(i);
				double curFitness = sumFitness(state, curIdx);
				if (curIdx == location) {
					curFitness = curFitness + indivFitness;
				}
				if (curFitness > sumFitness) {
					crowdIdx = curIdx;
					sumFitness = curFitness;
				}
			}
		}

		switch (nicheCount) {
			case 0:
				System.out.println("Empty subregion!!!");
				break;
			case 1:
				// if every subregion only contains one solution, delete the worst from indiv's subregion
				int targetIdx;
				for (targetIdx = 0; targetIdx < populationSize; targetIdx++) {
					if (init.subregionIdx[location][targetIdx] == 1) {
						break;
					}
				}
//				double prev_func = fitnessFunction(population.get(targetIdx), lambda[location]);
				double prev_func = init.calculateWeightSumScore(oldInds[targetIdx], location);
				if (indivFitness < prev_func) {
					replace(state, targetIdx, indiv);
				}
				break;
			default:
				if (location == crowdIdx) {  // if indiv's subregion is the most crowded one
					deleteCrowdIndiv_same(state, location, nicheCount, indivFitness, indiv);
				} else {
					int curNC = countOnes(state, location);
					int crowdNC = countOnes(state, crowdIdx);

					if (crowdNC > (curNC + 1)) {  // if the crowdIdx subregion is more crowded, delete one from this subregion
						deleteCrowdIndiv_diff(state, crowdIdx, location, crowdNC, indiv);
					} else if (crowdNC < (curNC + 1)) { // crowdNC == curNC, delete one from indiv's subregion
						deleteCrowdIndiv_same(state, location, curNC, indivFitness, indiv);
					} else { // crowdNC == (curNC + 1)
						if (curNC == 0) {
							deleteCrowdIndiv_diff(state, crowdIdx, location, crowdNC, indiv);
						} else {
							double rnd = state.random[0].nextDouble();
							if (rnd < 0.5) {
								deleteCrowdIndiv_diff(state, crowdIdx, location, crowdNC, indiv);
							} else {
								deleteCrowdIndiv_same(state, location, curNC, indivFitness, indiv);
							}
						}
					}
				}
				break;
		}

	}

	/**
	 * delete one solution from the most crowded subregion, which is different from indiv's subregion.
	 * just use indiv to replace the worst solution in that subregion
	 */
	public void deleteCrowdIndiv_diff(EvolutionState state, int crowdIdx, int curLocation, int nicheCount, Individual indiv) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		// find the solution indices within this crowdIdx subregion
		ArrayList<Integer> indList = new ArrayList<>();
		for (int i = 0; i < populationSize; i++) {
			if (init.subregionIdx[crowdIdx][i] == 1) {
				indList.add(i);
			}
		}

		// find the solution with the worst fitness value
		int worstIdx = indList.get(0);
		double maxFitness = init.calculateWeightSumScore(oldInds[worstIdx], crowdIdx);
//		double maxFitness = fitnessFunction(population.get(worstIdx), lambda[crowdIdx]);
		for (int i = 1; i < nicheCount; i++) {
			int curIdx = indList.get(i);
			double curFitness = init.calculateWeightSumScore(oldInds[curIdx], crowdIdx);
//			double curFitness = fitnessFunction(population.get(curIdx), lambda[crowdIdx]);
			if (curFitness > maxFitness) {
				worstIdx = curIdx;
				maxFitness = curFitness;
			}
		}

		// use indiv to replace the worst one
		replace(state, worstIdx, indiv);
		init.subregionIdx[crowdIdx][worstIdx] = 0;
		init.subregionIdx[curLocation][worstIdx] = 1;

	}


	/**
	 * delete one solution from the most crowded subregion, which is indiv's subregion. Compare
	 * indiv's fitness value and the worst one in this subregion
	 */
	public void deleteCrowdIndiv_same(EvolutionState state, int crowdIdx, int nicheCount, double indivFitness, Individual indiv) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		// find the solution indices within this crowdIdx subregion
		ArrayList<Integer> indList = new ArrayList<>();
		for (int i = 0; i < populationSize; i++) {
			if (init.subregionIdx[crowdIdx][i] == 1) {
				indList.add(i);
			}
		}

		// find the solution with the worst fitness value
		int listSize = indList.size();
		int worstIdx = indList.get(0);
//		double maxFitness = fitnessFunction(population.get(worstIdx), lambda[crowdIdx]);
		double maxFitness = init.calculateWeightSumScore(oldInds[worstIdx], crowdIdx);
		for (int i = 1; i < listSize; i++) {
			int curIdx = indList.get(i);
			double curFitness = init.calculateWeightSumScore(oldInds[curIdx], crowdIdx);
//			double curFitness = fitnessFunction(population.get(curIdx), lambda[crowdIdx]);
			if (curFitness > maxFitness) {
				worstIdx = curIdx;
				maxFitness = curFitness;
			}
		}

		// if indiv has a better fitness, use indiv to replace the worst one
		if (indivFitness < maxFitness) {
			replace(state, worstIdx, indiv);
		}
	}

	public void replace(EvolutionState state, int position, Individual solution) {
		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		oldInds[position] = solution;

//		if (position > this.population.size()) {
//			population.add(solution);
//		} else {
//			S toRemove = population.get(position);
//			population.remove(toRemove);
//			population.add(position, solution);
//		}
	}

	/**
	 * calculate the sum of fitnesses of solutions in the location subregion
	 */
	public double sumFitness(EvolutionState state, int location) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		double sum = 0;
		for (int i = 0; i < populationSize; i++) {
			if (init.subregionIdx[location][i] == 1) {
				sum = sum + init.calculateWeightSumScore(oldInds[i], location);
//				sum = sum + fitnessFunction(population.get(i), lambda[location]);
			}
		}

		return sum;
	}

	/**
	 * Count the number of 1s in the 'location'th subregion
	 */
	public int countOnes(EvolutionState state, int location) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;
		int count = 0;
		for (int i = 0; i < populationSize; i++) {
			if (init.subregionIdx[location][i] == 1) {
				count++;
			}
		}

		return count;
	}



	/**
	 * update the non-domination level when adding a solution
	 */
	public int nondominated_sorting_add(EvolutionState state, Individual indiv) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		Population oldPop = (Population) state.population;
		Individual[] oldInds = oldPop.subpops[0].individuals;

		int flag = 0;
		int flag1, flag2, flag3;
		int populationSize = init.popSize;

		// count the number of non-domination levels
		int num_ranks = 0;
		ArrayList<Integer> frontSize = new ArrayList<>();
		for (int i = 0; i < populationSize; i++) {
			int rankCount = countRankOnes(state, i);
			if (rankCount != 0) {
				frontSize.add(rankCount);
				num_ranks++;
			} else {
				break;
			}
		}

		ArrayList<Integer> dominateList = new ArrayList<>();  // used to keep the solutions dominated by 'indiv'
		int level = 0;
		for (int i = 0; i < num_ranks; i++) {
			level = i;
			if (flag == 1) {  // 'indiv' is non-dominated with all solutions in the ith non-domination level, then 'indiv' belongs to the ith level
				//indiv.setRank(i - 1);
				((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank = i-1;
//				indiv.attributes().put(init.ranking.getAttributedId(), i - 1);
				return num_ranks;
			} else if (flag == 2) {  // 'indiv' dominates some solutions in the ith level, but is non-dominated with some others, then 'indiv' belongs to the ith level, and move the dominated solutions to the next level
				//indiv.setRank(i - 1);
				((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank = i-1;
//				indiv.attributes().put(init.ranking.getAttributedId(), i - 1);
				int prevRank = i - 1;

				// process the solutions belong to 'prevRank'th level and are dominated by 'indiv' ==> move them to 'prevRank+1'th level and find the solutions dominated by them
				int curIdx;
				int newRank = prevRank + 1;
				int curListSize = dominateList.size();
				for (int j = 0; j < curListSize; j++) {
					curIdx = dominateList.get(j);
					init.rankIdx[prevRank][curIdx] = 0;
					init.rankIdx[newRank][curIdx] = 1;
					//((DoubleSolution) population.get(curIdx)).setRank(newRank);
					((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[curIdx])).fitness)).rank = newRank;
//					oldInds[curIdx].attributes().put(init.ranking.getAttributedId(), newRank);
				}
				for (int j = 0; j < populationSize; j++) {
					if (init.rankIdx[newRank][j] == 1) {
						for (int k = 0; k < curListSize; k++) {
							curIdx = dominateList.get(k);
							if (checkDominance(state, oldInds[curIdx], oldInds[j]) == 1) {
								dominateList.add(j);
								break;
							}

						}
					}
				}
				for (int j = 0; j < curListSize; j++) {
					dominateList.remove(0);
				}

				// if there are still some other solutions moved to the next level, check their domination situation in their new level
				prevRank = newRank;
				newRank = newRank + 1;
				curListSize = dominateList.size();
				if (curListSize == 0) {
					return num_ranks;
				} else {
					int allFlag = 0;
					do {
						for (int j = 0; j < curListSize; j++) {
							curIdx = dominateList.get(j);
							init.rankIdx[prevRank][curIdx] = 0;
							init.rankIdx[newRank][curIdx] = 1;
							//((DoubleSolution) population.get(curIdx)).setRank(newRank);
							((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[curIdx])).fitness)).rank = newRank;
//							population.get(curIdx).attributes().put(init.ranking.getAttributedId(), newRank);
						}
						for (int j = 0; j < populationSize; j++) {
							if (init.rankIdx[newRank][j] == 1) {
								for (int k = 0; k < curListSize; k++) {
									curIdx = dominateList.get(k);
									if (checkDominance(state, oldInds[curIdx], oldInds[j]) == 1) {
										dominateList.add(j);
										break;
									}
								}
							}
						}
						for (int j = 0; j < curListSize; j++) {
							dominateList.remove(0);
						}

						curListSize = dominateList.size();
						if (curListSize != 0) {
							prevRank = newRank;
							newRank = newRank + 1;
							if (frontSize.size() > prevRank && curListSize == frontSize.get(prevRank)) {  // if all solutions in the 'prevRank'th level are dominated by the newly added solution, move them all to the next level
								allFlag = 1;
								break;
							}
						}
					} while (curListSize != 0);

					if (allFlag == 1) {  // move the solutions after the 'prevRank'th level to their next levels
						int remainSize = num_ranks - prevRank;
						int[][] tempRecord = new int[remainSize][populationSize];

						int tempIdx = 0;
						for (int j = 0; j < dominateList.size(); j++) {
							tempRecord[0][tempIdx] = dominateList.get(j);
							tempIdx++;
						}

						int k = 1;
						int curRank = prevRank + 1;
						while (curRank < num_ranks) {
							tempIdx = 0;
							for (int j = 0; j < populationSize; j++) {
								if (init.rankIdx[curRank][j] == 1) {
									tempRecord[k][tempIdx] = j;
									tempIdx++;
								}
							}
							curRank++;
							k++;
						}

						k = 0;
						curRank = prevRank;
						while (curRank < num_ranks) {
							int level_size = frontSize.get(curRank);

							int tempRank;
							for (int j = 0; j < level_size; j++) {
								curIdx = tempRecord[k][j];
								//tempRank = ((DoubleSolution) population.get(curIdx)).getRank();
								tempRank = ((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[curIdx])).fitness)).rank;
//								tempRank = (int) population.get(curIdx).attributes().get(init.ranking.getAttributedId());
								newRank = tempRank + 1;
								//((DoubleSolution) population.get(curIdx)).setRank(newRank);
								((NSGA2MultiObjectiveFitness) (((Individual) (oldInds[curIdx])).fitness)).rank = newRank;
//								population.get(curIdx).attributes().put(init.ranking.getAttributedId(), newRank);
								init.rankIdx[tempRank][curIdx] = 0;
								init.rankIdx[newRank][curIdx] = 1;
							}
							curRank++;
							k++;
						}
						num_ranks++;
					}

					if (newRank == num_ranks) {
						num_ranks++;
					}

					return num_ranks;
				}
			} else if (flag == 3 || flag == 0) {  // if 'indiv' is dominated by some solutions in the ith level, skip it, and term to the next level
				flag1 = flag2 = flag3 = 0;
				for (int j = 0; j < populationSize; j++) {
					if (init.rankIdx[i][j] == 1) {
						switch (checkDominance(state, indiv, oldInds[j])) {
							case 1: {
								flag1 = 1;
								dominateList.add(j);
								break;
							}
							case 0: {
								flag2 = 1;
								break;
							}
							case -1: {
								flag3 = 1;
								break;
							}
						}

						if (flag3 == 1) {
							flag = 3;
							break;
						} else if (flag1 == 0 && flag2 == 1) {
							flag = 1;
						} else if (flag1 == 1 && flag2 == 1) {
							flag = 2;
						} else if (flag1 == 1 && flag2 == 0) {
							flag = 4;
						} else {
						}
					}
				}

			} else {  // (flag == 4) if 'indiv' dominates all solutions in the ith level, solutions in the current level and beyond move their current next levels
				//indiv.setRank(i - 1);
				((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank = i-1;
//				indiv.attributes().put(init.ranking.getAttributedId(), i - 1);
				i = i - 1;
				int remainSize = num_ranks - i;
				int[][] tempRecord = new int[remainSize][populationSize];

				int k = 0;
				while (i < num_ranks) {
					int tempIdx = 0;
					for (int j = 0; j < populationSize; j++) {
						if (init.rankIdx[i][j] == 1) {
							tempRecord[k][tempIdx] = j;
							tempIdx++;
						}
					}
					i++;
					k++;
				}

				k = 0;
				//i = indiv.getRank();
				i = ((NSGA2MultiObjectiveFitness) (((Individual) (indiv)).fitness)).rank;
//				i = (int) indiv.attributes().get(init.ranking.getAttributedId());
				while (i < num_ranks) {
					int level_size = frontSize.get(i);

					int curIdx;
					int curRank, newRank;
					for (int j = 0; j < level_size; j++) {
						curIdx = tempRecord[k][j];
						//curRank = ((DoubleSolution) population.get(curIdx)).getRank();
						curRank = ((NSGA2MultiObjectiveFitness) (((Individual) oldInds[curIdx]).fitness)).rank;
//						curRank = (int) population.get(curIdx).attributes().get(init.ranking.getAttributedId());
						newRank = curRank + 1;
						//((DoubleSolution) population.get(curIdx)).setRank(newRank);
						((NSGA2MultiObjectiveFitness) (((Individual) oldInds[curIdx]).fitness)).rank = newRank;
//						population.get(curIdx).attributes().put(init.ranking.getAttributedId(), newRank);

						init.rankIdx[curRank][curIdx] = 0;
						init.rankIdx[newRank][curIdx] = 1;
					}
					i++;
					k++;
				}
				num_ranks++;

				return num_ranks;
			}
		}
		// if flag is still 3 after the for-loop, it means that 'indiv' is in the current last level
		switch (flag) {
			case 1:
				//indiv.setRank(level);
				((NSGA2MultiObjectiveFitness) (((Individual) indiv).fitness)).rank = level;
//				indiv.attributes().put(init.ranking.getAttributedId(), level);
				break;
			case 2:
				//indiv.setRank(level);
				((NSGA2MultiObjectiveFitness) (((Individual) indiv).fitness)).rank = level;
//				indiv.attributes().put(init.ranking.getAttributedId(), level);
				int curIdx;
				int tempSize = dominateList.size();
				for (int i = 0; i < tempSize; i++) {
					curIdx = dominateList.get(i);
					//((DoubleSolution) population.get(curIdx)).setRank(level + 1);
					((NSGA2MultiObjectiveFitness) (((Individual) oldInds[curIdx]).fitness)).rank = level + 1;
//					population.get(curIdx).attributes().put(init.ranking.getAttributedId(), level + 1);

					init.rankIdx[level][curIdx] = 0;
					init.rankIdx[level + 1][curIdx] = 1;
				}
				num_ranks++;
				break;
			case 3:
				//indiv.setRank(level + 1);
				((NSGA2MultiObjectiveFitness) (((Individual) indiv).fitness)).rank = level + 1;
//				indiv.attributes().put(init.ranking.getAttributedId(), level + 1);
				num_ranks++;
				break;
			default:
				//indiv.setRank(level);
				((NSGA2MultiObjectiveFitness) (((Individual) indiv).fitness)).rank = level;
//				indiv.attributes().put(init.ranking.getAttributedId(), level);
				for (int i = 0; i < populationSize; i++) {
					if (init.rankIdx[level][i] == 1) {
						//((DoubleSolution) population.get(i)).setRank(level + 1);
						((NSGA2MultiObjectiveFitness) (((Individual) oldInds[i]).fitness)).rank = level + 1;
//						population.get(i).attributes().put(init.ranking.getAttributedId(), level + 1);
						init.rankIdx[level][i] = 0;
						init.rankIdx[level + 1][i] = 1;
					}
				}
				num_ranks++;
				break;
		}

		return num_ranks;
	}

	/**
	 * check the dominance relationship between a and b: 1 -> a dominates b, -1 -> b dominates a 0 ->
	 * non-dominated with each other
	 */
	public int checkDominance(EvolutionState state, Individual a, Individual b) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;

		int flag1 = 0;
		int flag2 = 0;

		MultiObjectiveFitness afit = (MultiObjectiveFitness) a.fitness;
		MultiObjectiveFitness bfit = (MultiObjectiveFitness) b.fitness;

		for (int i = 0; i < init.numObjectives; i++) {
			if (afit.getObjectives()[i] < bfit.getObjectives()[i]) {
				flag1 = 1;
			} else {
				if (afit.getObjectives()[i] > bfit.getObjectives()[i]) {
					flag2 = 1;
				}
			}
		}
		if (flag1 == 1 && flag2 == 0) {
			return 1;
		} else {
			if (flag1 == 0 && flag2 == 1) {
				return -1;
			} else {
				return 0;
			}
		}
	}

	/**
	 * count the number of 1s in a row of rank matrix
	 */
	public int countRankOnes(EvolutionState state, int location) {

		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;

		int count = 0;
		for (int i = 0; i < populationSize; i++) {
			if (init.rankIdx[location][i] == 1) {
				count++;
			}
		}

		return count;
	}



	/**
	 * Set the location of a solution based on the orthogonal distance
	 */
	public int setLocation(EvolutionState state, Individual indiv, double[] z_, double[] nz_) {

		int minIdx;
		double distance, minDist;
		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		int populationSize = init.popSize;

		minIdx = 0;
		distance = calculateDistance2(state, indiv, init.weights[0], z_, nz_);
		minDist = distance;
		for (int i = 1; i < populationSize; i++) {
			distance = calculateDistance2(state, indiv, init.weights[i], z_, nz_);
			if (distance < minDist) {
				minIdx = i;
				minDist = distance;
			}
		}

//		indiv.attributes().put("region", minIdx);
		return minIdx;

	}

	public double calculateDistance2(EvolutionState state, Individual indiv, double[] lambda,
									 double[] z_, double[] nz_) {

		// normalize the weight vector (line segment)
		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		double nd = norm_vector(state, lambda);
		for (int i = 0; i < init.numObjectives; i++) {
			lambda[i] = lambda[i] / nd;
		}

		double[] realA = new double[init.numObjectives];
		double[] realB = new double[init.numObjectives];

		MultiObjectiveFitness fit = (MultiObjectiveFitness) indiv.fitness;

		// difference between current point and reference point
		for (int i = 0; i < init.numObjectives; i++) {
			realA[i] = (fit.getObjectives()[i] - z_[i]);
		}

		// distance along the line segment
		double d1 = Math.abs(innerproduct(realA, lambda));

		// distance to the line segment
		for (int i = 0; i < init.numObjectives; i++) {
			realB[i] = (fit.getObjectives()[i] - (z_[i] + d1 * lambda[i]));
		}

		double distance = norm_vector(state, realB);

		return distance;
	}

	/**
	 * Calculate the norm of the vector
	 */
	public double norm_vector(EvolutionState state, double[] z) {
		double sum = 0;
		MOEADDInitializer init = (MOEADDInitializer)state.initializer;
		for (int i = 0; i < init.numObjectives; i++) {
			sum += z[i] * z[i];
		}

		return Math.sqrt(sum);
	}

	/**
	 * Calculate the dot product of two vectors
	 */
	public double innerproduct(double[] vec1, double[] vec2) {
		double sum = 0;

		for (int i = 0; i < vec1.length; i++) {
			sum += vec1[i] * vec2[i];
		}

		return sum;
	}

//	public double fitnessFunction(Individual individual, double[] weights, EvolutionState state){
//		double fitness = 0;
//
//		if(((MOEADDInitializer)state.initializer).tchebycheff) {
//			double maxFun = -1.0e+30;
//
//			double[] idealPoint = ((MOEADDInitializer)state.initializer).idealPoint;
//			for (int n = 0; n < ((MOEADDInitializer)state.initializer).numObjectives; n++) {
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
