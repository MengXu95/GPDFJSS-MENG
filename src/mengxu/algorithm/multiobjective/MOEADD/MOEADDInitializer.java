package mengxu.algorithm.multiobjective.MOEADD;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPInitializer;
import ec.multiobjective.MOEAD.IndexDistancePair;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MOEAD.MOEADInitializer;
import mengxu.algorithm.multiobjective.MOEAD.MOEADMultiObjectiveFitness;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

public class MOEADDInitializer extends MOEADInitializer {

//	public double[] nadirPoint;
	protected int[] ranking;
	protected int[][] rankIdx;      // index matrix for the non-domination levels
	protected int[][] subregionIdx;    // index matrix for subregion record
	protected double[][] subregionDist;  // distance matrix for perpendicular distance
	protected int numRanks;

	public double[] nadirPoint;

	public void setup(final EvolutionState state, final Parameter base) {
		super.setup(state, base);
		rankIdx = new int[popSize][popSize];
		subregionIdx = new int[popSize][popSize];
		subregionDist = new double[popSize][popSize];
		initNadirPoint();
	}

	/**
	 * Initialize the ideal point used for the Tchebycheff calculation.
	 */
	private void initIdealPoint() {
		idealPoint = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++) {
//			idealPoint[i] = 0.0; //original by Fangfang
			idealPoint[i] = Double.POSITIVE_INFINITY; //modified by mengxu based on Jmetal
		}
	}

	/**
	 * Initialize the nadir point. 2021.10.18
	 */
	private void initNadirPoint() {
		nadirPoint = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++) {
			nadirPoint[i] = Double.NEGATIVE_INFINITY; //original by Fangfang
//			idealPoint[i] = Double.POSITIVE_INFINITY; //modified by mengxu based on Jmetal
		}
	}

	/**
	 * Update the ideal point add by mengxu 2021.08.11. Used after each population evaluation.
	 */
	public void updateIdealPoint(EvolutionState state) {
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((MOEADDMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < idealPoint.length; i++) {
				if (this.idealPoint[i] > objectives[i]) {
					this.idealPoint[i] = objectives[i];
				}
			}
		}
	}

	public void updateIdealPoint(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((MOEADDMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < idealPoint.length; i++) {
			if (this.idealPoint[i] > objectives[i]) {
				this.idealPoint[i] = objectives[i];
//				System.out.println("update one idealPoint!");
			}
		}
	}

	/**
	 * Update the ideal point add by mengxu 2021.08.11. Used after each population evaluation.
	 */
	public void updateNadirPoint(EvolutionState state) {
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((MOEADDMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < nadirPoint.length; i++) {
				if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
					if (this.nadirPoint[i] < objectives[i]) {
						this.nadirPoint[i] = objectives[i];
					}
				}
			}
		}
	}

	public void updateNadirPoint(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((MOEADDMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < nadirPoint.length; i++) {
			if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
				if (this.nadirPoint[i] < objectives[i]) {
					this.nadirPoint[i] = objectives[i];
				}
			}
		}
	}

	/**
	 * Initialize the ideal point used for the Tchebycheff calculation.
	 */
//	private void initNadirPoint() {
//		nadirPoint = new double[numObjectives];
//		for (int i = 0; i < numObjectives; i++) {
////			idealPoint[i] = 0.0; //original by Fangfang
//			nadirPoint[i] = Double.NEGATIVE_INFINITY; //modified by mengxu based on Jmetal
//		}
//	}

	/**
	 * Update the ideal point add by mengxu 2021.08.11. Used after each population evaluation.
	 */
//	public void updateNadirPoint(EvolutionState state) {
//		//this is just suitable for one subpop.
//		Individual[] individuals = state.population.subpops[0].individuals;
//		if(state.population.subpops.length>1){
//			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
//		}
//		for(Individual individual:individuals){
//			//todo: need to check if should change to MOEADMultiObjectiveFitness.
//			double[] objectives = ((MOEADMultiObjectiveFitness)individual.fitness).objectives;
//			for (int i = 0; i < nadirPoint.length; i++) {
//				if (this.nadirPoint[i] < objectives[i]) {
//					this.nadirPoint[i] = objectives[i];
//				}
//			}
//		}
//	}
//
//	public void updateNadirPoint(Individual individual) {
//		//todo: need to check if should change to MOEADMultiObjectiveFitness.
//		double[] objectives = ((MOEADMultiObjectiveFitness)individual.fitness).objectives;
//		for (int i = 0; i < nadirPoint.length; i++) {
//			if (this.nadirPoint[i] < objectives[i]) {
//				this.nadirPoint[i] = objectives[i];
////				System.out.println("update one idealPoint!");
//			}
//		}
//	}


}