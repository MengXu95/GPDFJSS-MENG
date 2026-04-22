package mengxu.algorithm.multiobjective.ParetoSetLearning.PreferenceOpposite;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPInitializer;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.MOEAD.util.MOEADUtils;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLMultiObjectiveFitness;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.util.Arrays;

public class PSLInitializerPreOpposite extends GPInitializer {

	// settings for MOEAD
	public double[] idealPoint;
	public double[] maxObjectives; //2022.09.20
	public double[] nadirPoint;
	public double[][] weights;
	public int tchebycheff;
	public int numObjectives;
	public int popSize;
	public int numPreference;

	public int normalisation;

	public boolean preferenceOpposite;

	public double[] manualRuleObjectives;

	public RealMatrix curSchedulingSetObjectiveLowerBoundMtx;// add by mengxu 2022.10.02


	public void setup(final EvolutionState state, final Parameter base) {
		super.setup(state, base);

		Parameter tchebycheffParam = new Parameter("tchebycheff");
		Parameter numObjectivesParam = new Parameter("eval.problem.eval-model.objectives");
		Parameter popSizeParam = new Parameter("pop.subpop.0.size");
		Parameter numPreferenceParam = new Parameter("numPreferences");


		// Initializations for MOEAD settings
		tchebycheff = state.parameters.getInt(tchebycheffParam, null, 1);
		numObjectives = state.parameters.getInt(numObjectivesParam, null);
		popSize = state.parameters.getInt(popSizeParam, null);
		numPreference = state.parameters.getInt(numPreferenceParam, null);

		//add by mengxu 2022.09.07=================================================
		Parameter ifNormalisation = new Parameter("normalisation");
		normalisation = state.parameters.getInt(ifNormalisation, null);
		//add by mengxu 2022.09.07=================================================

		Parameter preferenceOppositeParam = new Parameter("preferenceOpposite");
		preferenceOpposite = state.parameters.getBoolean(preferenceOppositeParam, false);

		// Initialise the reference point
		initIdealPoint();
		initMaxObjectives();//2021.10.18
		initNadirPoint();

		// Create a set of uniformly spread weight vectors based on preference
		if(preferenceOpposite){
			initWeightsWithOppositePreferences(state);
		}
		else{
//			initWeights(state); //hide by mengxu 2024.4.9 to try a new strategy
			initWeightsWithBoundaryRelaxationStrategy_new(state);
		}


		if(normalisation == 1 || normalisation == 2){ //add by mengxu 2022.10.02 //todo:double-check for normalisation==2
			manualRuleObjectives = new double[numObjectives];
			curSchedulingSetObjectiveLowerBoundMtx = new Array2DRowRealMatrix(numObjectives, 1);
		}
	}


	public double calculateTchebycheffScore(MultiObjectiveFitness fit, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
		double max_fun = -1 * Double.MAX_VALUE;

//		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;

		for (int i = 0; i < numObjectives; i++) {
			//todo: need to change 2 parts if do normalisation similar to this
			double diff = 0;
			if(normalisation == 1){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - idealPoint[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 2){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - idealPoint[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 3){//add by mengxu 2024.3.19
				if(nadirPoint[i] == idealPoint[i]){
					diff = fit.getObjectives()[i]/1 - idealPoint[i]/1;
				}
				else{
					diff = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
				}
			}
			else if(normalisation == 0){
				diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
			}

			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			double feval;
			if (problemWeights[i] == 0) {
				feval = 0.00000001 * diff;
			}
			else{
				feval = problemWeights[i] * diff;
				if(i==0){
					feval = problemWeights[i] * diff;
				}
			}

			if (feval > max_fun)
				max_fun = feval;
		}
		return max_fun;
	}

	public double calculateTchebycheffScoreOpposite(MultiObjectiveFitness fit, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
		double max_fun = -1 * Double.MAX_VALUE;

//		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;

		for (int i = 0; i < numObjectives; i++) {
			//todo: need to change 2 parts if do normalisation similar to this
			double diff = 0;
			if(normalisation == 1){//add by mengxu 2022.10.02
				diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - idealPoint[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 2){//add by mengxu 2022.10.02
				diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - idealPoint[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 3){//add by mengxu 2024.3.19
				if(nadirPoint[i] == idealPoint[i]){
					diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/1 - idealPoint[i]/1;
				}
				else{
					diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
				}
			}
			else if(normalisation == 0){
				diff = Math.abs(((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i] - idealPoint[i]);
			}

			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			double feval;
			if (problemWeights[i] == 0) {
				feval = 0.00000001 * diff;
			}
			else{
				feval = problemWeights[i] * diff;
				if(i==0){
					feval = problemWeights[i] * diff;
				}
			}

			if (feval > max_fun)
				max_fun = feval;
		}
		return max_fun;
	}

	//added by mengxu to use a new augmented Tchebycheff
	public double calculateAugmentedTchebycheffScore(MultiObjectiveFitness fit, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
		double max_fun = -1 * Double.MAX_VALUE;
		double[] varepsilon = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++){
			varepsilon[i] = idealPoint[i] * 0.1;
		}
		double rho = 0.001;

		for (int i = 0; i < numObjectives; i++) {
			//todo: need to change 2 parts if do normalisation similar to this
			double diff = 0;
			if(normalisation == 1){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - (idealPoint[i]-varepsilon[i])/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 2){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - (idealPoint[i]-varepsilon[i])/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 3){//add by mengxu 2024.3.19
				if(nadirPoint[i] == idealPoint[i]){
					diff = fit.getObjectives()[i]/1 - idealPoint[i]/1;
				}
				else{
					diff = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
				}
			}
			else if(normalisation == 0){
				diff = Math.abs(fit.getObjectives()[i] - idealPoint[i]);
			}

			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			double feval;
			if (problemWeights[i] == 0) {
				feval = 0.00000001 * diff;
			}
			else{
				feval = problemWeights[i] * diff;
			}

			if (feval > max_fun)
				max_fun = feval;
		}

		double second_part = 0;
		for (int i = 0; i < numObjectives; i++) {
			//todo: need to change 2 parts if do normalisation similar to this
			double value = 0;
			if (normalisation == 1) {//add by mengxu 2022.10.02
				value = fit.getObjectives()[i] / curSchedulingSetObjectiveLowerBoundMtx.getEntry(i, 0);
			} else if (normalisation == 2) {//add by mengxu 2022.10.02
				value = fit.getObjectives()[i] / curSchedulingSetObjectiveLowerBoundMtx.getEntry(i, 0);
			} else if (normalisation == 3) {//add by mengxu 2024.3.19
				if(nadirPoint[i] == idealPoint[i]){
					value = fit.getObjectives()[i]/1 - idealPoint[i]/1;
				}
				else{
					value = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
				}
//				value = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
			} else if (normalisation == 0) {
				value = fit.getObjectives()[i];
			}

			if (value >= Double.POSITIVE_INFINITY || value >= Double.MAX_VALUE) {
				return Double.POSITIVE_INFINITY;
			}
			double feval;
			if (problemWeights[i] == 0) {
				feval = 0.00000001 * value;
			} else {
				feval = problemWeights[i] * value;
			}
			second_part += feval;
		}
		max_fun = max_fun + rho*second_part;
		return max_fun;
	}

	//added by mengxu to use a new augmented Tchebycheff
	public double calculateAugmentedTchebycheffScoreOpposite(MultiObjectiveFitness fit, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
		double max_fun = -1 * Double.MAX_VALUE;
		double[] varepsilon = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++){
			varepsilon[i] = idealPoint[i] * 0.1;
		}
		double rho = 0.001;

		for (int i = 0; i < numObjectives; i++) {
			//todo: need to change 2 parts if do normalisation similar to this
			double diff = 0;
			if(normalisation == 1){//add by mengxu 2022.10.02
				diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - (idealPoint[i]-varepsilon[i])/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 2){//add by mengxu 2022.10.02
				diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) - (idealPoint[i]-varepsilon[i])/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 3){//add by mengxu 2024.3.19
				if(nadirPoint[i] == idealPoint[i]){
					diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/1 - idealPoint[i]/1;
				}
				else{
					diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
				}
			}
			else if(normalisation == 0){
				diff = Math.abs(((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i] - idealPoint[i]);
			}

			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			double feval;
			if (problemWeights[i] == 0) {
				feval = 0.00000001 * diff;
			}
			else{
				feval = problemWeights[i] * diff;
			}

			if (feval > max_fun)
				max_fun = feval;
		}

		double second_part = 0;
		for (int i = 0; i < numObjectives; i++) {
			//todo: need to change 2 parts if do normalisation similar to this
			double value = 0;
			if (normalisation == 1) {//add by mengxu 2022.10.02
				value = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i] / curSchedulingSetObjectiveLowerBoundMtx.getEntry(i, 0);
			} else if (normalisation == 2) {//add by mengxu 2022.10.02
				value = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i] / curSchedulingSetObjectiveLowerBoundMtx.getEntry(i, 0);
			} else if (normalisation == 3) {//add by mengxu 2024.3.19
				if(nadirPoint[i] == idealPoint[i]){
					value = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/1 - idealPoint[i]/1;
				}
				else{
					value = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
				}
//				value = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
			} else if (normalisation == 0) {
				value = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i];
			}

			if (value >= Double.POSITIVE_INFINITY || value >= Double.MAX_VALUE) {
				return Double.POSITIVE_INFINITY;
			}
			double feval;
			if (problemWeights[i] == 0) {
				feval = 0.00000001 * value;
			} else {
				feval = problemWeights[i] * value;
			}
			second_part += feval;
		}
		max_fun = max_fun + rho*second_part;
		return max_fun;
	}

	/**
	 * Calculates the problem score for a given individual, using a given set of
	 * weights.
	 *
	 * @param ind
	 * @param problemIndex
	 *            - for retrieving weights
	 * @return score
	 */
	public double calculateWeightSumScore(Individual ind, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;

		double sum = 0;
		for(int i = 0; i < numObjectives; i++){
			double diff = 0;
			if(normalisation == 1){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 2){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 3){//add by mengxu 2022.10.02
				if(nadirPoint[i] == idealPoint[i]){
					diff = fit.getObjectives()[i]/1 - idealPoint[i]/1;
				}
				else{
					diff = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
				}
//				diff = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
			}
			else if(normalisation == 0){
				diff = Math.abs(fit.getObjectives()[i]); //todo: traditional MOEAD do not use reference point in weight sum approach
			}

			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			sum += (problemWeights[i]) * diff;
		}

		return sum;
	}

	public double calculateWeightSumScore(MultiObjectiveFitness fit, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
//		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;

		double sum = 0;
		for(int i = 0; i < numObjectives; i++){
			double diff = 0;
			if(normalisation == 1){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 2){//add by mengxu 2022.10.02
				diff = fit.getObjectives()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 3){//add by mengxu 2022.10.02
				if(nadirPoint[i] == idealPoint[i]){
					diff = fit.getObjectives()[i]/1 - idealPoint[i]/1;
				}
				else{
					diff = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
				}
//				diff = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
			}
			else if(normalisation == 0){
				diff = Math.abs(fit.getObjectives()[i]); //todo: traditional MOEAD do not use reference point in weight sum approach
			}

			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			sum += (problemWeights[i]) * diff;
		}

		return sum;
	}

	public double calculateWeightSumScoreOpposite(MultiObjectiveFitness fit, int problemIndex) {
		double[] problemWeights = weights[problemIndex];
//		MultiObjectiveFitness fit = (MultiObjectiveFitness) ind.fitness;

		double sum = 0;
		for(int i = 0; i < numObjectives; i++){
			double diff = 0;
			if(normalisation == 1){//add by mengxu 2022.10.02
				diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 2){//add by mengxu 2022.10.02
				diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0);
			}
			else if(normalisation == 3){//add by mengxu 2022.10.02
				if(nadirPoint[i] == idealPoint[i]){
					diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/1 - idealPoint[i]/1;
				}
				else{
					diff = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
				}
//				diff = fit.getObjectives()[i]/(nadirPoint[i]-idealPoint[i]) - idealPoint[i]/(nadirPoint[i]-idealPoint[i]);
			}
			else if(normalisation == 0){
				diff = Math.abs(((ClearingPSLMultiObjectiveFitnessPreOpppsite)fit).getObjectivesOpposite()[i]); //todo: traditional MOEAD do not use reference point in weight sum approach
			}

			if(diff >= Double.POSITIVE_INFINITY || diff >= Double.MAX_VALUE){
				return Double.POSITIVE_INFINITY;
			}
			sum += (problemWeights[i]) * diff;
		}

		return sum;
	}



	/**
	 * Calculates the Euclidean distance between two weight vectors.
	 *
	 * @param vector1
	 * @param vector2
	 * @return distance
	 */
	private double calculateDistance(double[] vector1, double[] vector2) {
		double sum = 0;
		for (int i = 0; i < vector1.length; i++) {
			sum += Math.pow((vector1[i] - vector2[i]), 2);
		}
		return Math.sqrt(sum);
	}

	/**
	 * Initialize the ideal point used for the Tchebycheff calculation.
	 */
	private void initIdealPoint() {
		idealPoint = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++) {
			idealPoint[i] = 0.0; //original by Fangfang
//			idealPoint[i] = Double.POSITIVE_INFINITY; //modified by mengxu based on Jmetal
		}
	}

	private void initNadirPoint() {
		nadirPoint = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++) {
//			idealPoint[i] = 0.0; //original by Fangfang
			nadirPoint[i] = Double.NEGATIVE_INFINITY; //modified by mengxu based on Jmetal
		}
	}

	public void updateNadirPoint(Object[] sortedParetoFront){
		for(int f = 0; f < nadirPoint.length; f++){
			nadirPoint[f] = Double.NEGATIVE_INFINITY;
		}

		for (int i = 0; i < sortedParetoFront.length; i++)
		{
			Individual ind = (Individual)(sortedParetoFront[i]);
			MultiObjectiveFitness mof = (MultiObjectiveFitness) (ind.fitness);
			double[] objectives = mof.getObjectives();

			for(int f = 0; f < objectives.length; f++){
				if(nadirPoint[f] < objectives[f]){
					nadirPoint[f] = objectives[f];
				}
			}
		}
	}

	public void updateNadirPointOpposite(Object[] sortedParetoFront){
		for (int i = 0; i < sortedParetoFront.length; i++)
		{
			Individual ind = (Individual)(sortedParetoFront[i]);
			ClearingPSLMultiObjectiveFitnessPreOpppsite mof = (ClearingPSLMultiObjectiveFitnessPreOpppsite) (ind.fitness);
			double[] objectives = mof.getObjectivesOpposite();

			for(int f = 0; f < objectives.length; f++){
				if(nadirPoint[f] < objectives[f]){
					nadirPoint[f] = objectives[f];
				}
			}
		}
	}

	/**
	 * Initialize the nadir point. 2021.10.18
	 */
	private void initMaxObjectives() {
		maxObjectives = new double[numObjectives];
		for (int i = 0; i < numObjectives; i++) {
			maxObjectives[i] = Double.NEGATIVE_INFINITY; //original by Fangfang
//			idealPoint[i] = Double.POSITIVE_INFINITY; //modified by mengxu based on Jmetal
		}
	}

	/**
	 * Update the ideal point add by mengxu 2021.08.11. Used after each population evaluation.
	 */
	public void updateIdealPoint(EvolutionState state) {
		RuleOptimizationProblem problem = (RuleOptimizationProblem)state.evaluator.p_problem;
		if(problem.getEvaluationModel().isRotatable()) {
			Arrays.fill(idealPoint, Double.POSITIVE_INFINITY);
		}
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((PSLMultiObjectiveFitness)individual.fitness).objectives;
			double[] objectivesOpposite = ((ClearingPSLMultiObjectiveFitnessPreOpppsite)individual.fitness).objectivesOpposite;
			for (int i = 0; i < idealPoint.length; i++) {
				if (this.idealPoint[i] > objectives[i]) {
					this.idealPoint[i] = objectives[i]; //original
				}
				if (this.idealPoint[i] > objectivesOpposite[i]) {
					this.idealPoint[i] = objectivesOpposite[i]; //original
				}
			}
		}
		for (int i = 0; i < idealPoint.length; i++) {
			this.idealPoint[i] = this.idealPoint[i] * 0.9;
		}
	}


	public void updateIdealPoint(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((PSLMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < idealPoint.length; i++) {
			if (this.idealPoint[i] > objectives[i]) {
				this.idealPoint[i] = objectives[i];//original
			}
		}
	}

	//2022.10.06
	public void updateCurSchedulingSetObjectiveLowerBoundMtx(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((PSLMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < objectives.length; i++) {
			if (this.curSchedulingSetObjectiveLowerBoundMtx.getEntry(i,0) > objectives[i]) {
//				this.idealPoint[i] = objectives[i];//original
				this.curSchedulingSetObjectiveLowerBoundMtx.setEntry(i,0,objectives[i]);
//				System.out.println("update one idealPoint!");
			}
		}
	}

	/**
	 * Update the ideal point add by mengxu 2021.08.11. Used after each population evaluation.
	 */
	public void updateMaxObjectives(EvolutionState state) {
		//this is just suitable for one subpop.
		Individual[] individuals = state.population.subpops[0].individuals;
		if(state.population.subpops.length>1){
			System.out.println("Warning!!! only one subpop allowed!!! by MengXu!");
		}
		for(Individual individual:individuals){
			//todo: need to check if should change to MOEADMultiObjectiveFitness.
			double[] objectives = ((PSLMultiObjectiveFitness)individual.fitness).objectives;
			for (int i = 0; i < maxObjectives.length; i++) {
				if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
					if (this.maxObjectives[i] < objectives[i]) {
						this.maxObjectives[i] = objectives[i];
					}
				}
			}
		}
	}


	public void updateMaxObjectives(Individual individual) {
		//todo: need to check if should change to MOEADMultiObjectiveFitness.
		double[] objectives = ((PSLMultiObjectiveFitness)individual.fitness).objectives;
		for (int i = 0; i < maxObjectives.length; i++) {
			if (!(objectives[i] >= Double.POSITIVE_INFINITY) && !(objectives[i] >= Double.MAX_VALUE)) {
				if (this.maxObjectives[i] < objectives[i]) {
					this.maxObjectives[i] = objectives[i];
				}
			}
		}
	}


	/**
	 * Initialize uniformely spread weight vectors. This code come from the authors'
	 * original code base.
	 */
	private void initWeights(final EvolutionState state) {
		weights = new double[state.numGenerations][numObjectives];
		int[] perm = new int[state.numGenerations];
		MOEADUtils.randomPermutation(state,0,perm,state.numGenerations);;
		for (int i = 0; i < state.numGenerations; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = i / (double) (state.numGenerations-1);
				weightVector[1] = (state.numGenerations -1 - i) / (double) (state.numGenerations-1);
				int index = perm[i];
				weights[index] = weightVector;
			} else if (numObjectives == 3) {
				int value_i = i;
				int value_j = state.random[0].nextInt(state.numGenerations-value_i);
				int value_k = state.numGenerations - value_i - value_j;
				double[] weightVector = new double[3];
				weightVector[0] = value_i / (double) state.numGenerations;
				weightVector[1] = value_j / (double) state.numGenerations;
				weightVector[2] = value_k / (double) state.numGenerations;
				int index = perm[i];
				weights[index] = weightVector;
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}
	}


	/**
	 * Initialize uniformely spread weight vectors. This code come from the authors'
	 * original code base.
	 * add by mengxu 2024.4.9 as the boundary relaxation strategy
	 */

	private void initWeightsWithBoundaryRelaxationStrategy(final EvolutionState state) {
		weights = new double[state.numGenerations][numObjectives];
		int[] perm = new int[state.numGenerations];
		MOEADUtils.randomPermutation(state,0,perm,state.numGenerations);
		int numOutsideBoundary = (int) (state.numGenerations * 0.1);
		int numInsideBoundary = state.numGenerations - numOutsideBoundary * 2;

		for (int i = 1; i < numOutsideBoundary+1; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = - i / (double) (numInsideBoundary-1);
				weightVector[1] = (numInsideBoundary -1 + i) / (double) (numInsideBoundary-1);
				int index = perm[i-1];
				weights[index] = weightVector;
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}

		for (int i = 0; i < numInsideBoundary; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = i / (double) (numInsideBoundary-1);
				weightVector[1] = (numInsideBoundary -1 - i) / (double) (numInsideBoundary-1);
				int index = perm[i + numOutsideBoundary];
				weights[index] = weightVector;
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}

		for (int i = 1; i < numOutsideBoundary+1; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = (numInsideBoundary -1 + i) / (double) (numInsideBoundary-1);
				weightVector[1] = - i / (double) (numInsideBoundary-1);
				int index = perm[i+numInsideBoundary + numOutsideBoundary-1];
				weights[index] = weightVector;
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}
	}

	/**
	 * Initialize uniformely spread weight vectors. This code come from the authors'
	 * original code base.
	 * add by mengxu 2024.4.9 as the boundary relaxation strategy
	 */

	private void initWeightsWithBoundaryRelaxationStrategy_new(final EvolutionState state) {
		weights = new double[state.numGenerations][numObjectives];
		int[] perm = new int[state.numGenerations];
		MOEADUtils.randomPermutation(state,0,perm,state.numGenerations);
		int numOutsideBoundary = (int) (state.numGenerations * 0.1);
		int numInsideBoundary = state.numGenerations - numOutsideBoundary * 2;

		for (int i = 1; i < numOutsideBoundary+1; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = 0;
				weightVector[1] = 1;
				int index = perm[i-1];
				weights[index] = weightVector;
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}

		for (int i = 0; i < numInsideBoundary; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = i / (double) (numInsideBoundary-1);
				weightVector[1] = (numInsideBoundary -1 - i) / (double) (numInsideBoundary-1);
				int index = perm[i + numOutsideBoundary];
				weights[index] = weightVector;
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}

		for (int i = 1; i < numOutsideBoundary+1; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = 1;
				weightVector[1] = 0;
				int index = perm[i+numInsideBoundary + numOutsideBoundary-1];
				weights[index] = weightVector;
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}
	}


	/**
	 * Initialize opposite weight vectors.
	 * add by mengxu 2024.4.9 as to get the opposite
	 */
	private void initWeightsWithOppositePreferences(final EvolutionState state) {
		weights = new double[state.numGenerations*2][numObjectives];
//		int[] perm = new int[state.numGenerations];
//		MOEADUtils.randomPermutation(state, 0, perm, state.numGenerations);
		;
		for (int i = 0; i < state.numGenerations; i++) {
			if (numObjectives == 2) {
				double[] weightVector = new double[2];
				weightVector[0] = i / (double) (state.numGenerations - 1);
				weightVector[1] = (state.numGenerations - 1 - i) / (double) (state.numGenerations - 1);
				int index = i;
				weights[index] = weightVector;
			} else {
				throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
			}
		}
	}

}
