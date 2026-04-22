package mengxu.algorithm.multiobjective.indicatorBasedMO;

import ec.*;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import ec.util.SortComparator;
import mengxu.algorithm.multiobjective.GPRuleEvolutionStateMO;
import mengxu.algorithm.multiobjective.MOEADarchive.MOEADarchiveInitializer;
import mengxu.algorithm.multiobjective.NSGPII.zScore.NSGA2MultiObjectiveFitnessNormalisation;
import mengxu.algorithm.multiobjective.oneInstanceMultiCaseLexicaseSelection.OneInstanceMultiCaseMultiObjectiveFitnessLSMO;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

import java.util.ArrayList;
import java.util.List;

public class IBMOEvaluator extends SimpleEvaluator {
	/**
	 * The original population size is stored here so NSGA2 knows how large to
	 * create the archive (it's the size of the original population -- keep in mind
	 * that NSGA2Breeder had made the population larger to include the children.
	 */
	public int originalPopSize[];

	public boolean manualRuleNormalisation;
	public RealMatrix curSchedulingSetObjectiveLowerBoundMtx;// add by mengxu 2022.10.02

	public boolean useReferencePointBoundaryPoints; // add by mengxu 2024.7.11
	public double referencePointRatio; // add by mengxu 2024.7.11
	public double[] referencePoints;

	public void setup(final EvolutionState state, final Parameter base) {
		super.setup(state, base);

		Parameter p = new Parameter(Initializer.P_POP);
		int subpopsLength = state.parameters.getInt(p.push(Population.P_SIZE), null, 1);
		Parameter p_subpop;
		originalPopSize = new int[subpopsLength];
		for (int i = 0; i < subpopsLength; i++) {
			p_subpop = p.push(Population.P_SUBPOP).push("" + i).push(Subpopulation.P_SUBPOPSIZE);
			originalPopSize[i] = state.parameters.getInt(p_subpop, null, 1);
		}
		Parameter usingManualRuleNormalisation = new Parameter("manual-rule-normalisation"); //add by mengxu 2022.10.02
		manualRuleNormalisation = state.parameters.getBoolean(usingManualRuleNormalisation, null, false);

		Parameter useReferencePointBoundaryPointsParam = new Parameter("use-reference-point-boundary-points"); //add by mengxu 2022.10.02
		useReferencePointBoundaryPoints = state.parameters.getBoolean(useReferencePointBoundaryPointsParam, null, false);

		Parameter usingReferencePointRatioParam = new Parameter("reference-point-ratio"); //add by mengxu 2022.10.02
		referencePointRatio = state.parameters.getDouble(usingReferencePointRatioParam, null, 1.1);

	}

	/**
	 * Evaluates the population, then builds the archive and reduces the population
	 * to just the archive.
	 */
	public void evaluatePopulation(final EvolutionState state) {
		super.evaluatePopulation(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals

		//do z-score normalisation by mengxu 2023.04.21
		if(((GPRuleEvolutionStateIBMO)state).useZScoreNormalisation){
			this.zScoreSigmoidNormalisation(state);
		}
		else if(this.manualRuleNormalisation){//add by mengxu 2022.10.02
			SimpleProblemForm prob = (SimpleProblemForm) (state.evaluator.p_problem.clone());
			AbstractEvaluationModel evaluationModel = ((MultipleTreeRuleOptimizationProblem)prob).getEvaluationModel();
			SchedulingSet curSchedulingSet = ((MultipleRuleEvaluationModel)evaluationModel).getSchedulingSet();
			curSchedulingSet.lowerBoundsFromBenchmarkRule(evaluationModel.getObjectives());
			this.curSchedulingSetObjectiveLowerBoundMtx = curSchedulingSet.getObjectiveLowerBoundMtx();
		}

		for (int x = 0; x < state.population.subpops.length; x++)
			state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population

	}

	/**
	 * Build the auxiliary fitness data and reduce the subpopulation to just the
	 * archive, which is returned.
	 */
	//achieve a archive has the same size of original population
	public Individual[] buildArchive(EvolutionState state, int subpop) {
		Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
		ArrayList ranks = assignFrontRanks(state.population.subpops[subpop]); //after this, get different several ranks
		assignNumberOfDominatedBy(state.population.subpops[subpop].individuals); //added by mengxu to add number of dominated by 2023.03.20
		assignNumberOfDuplicatedBy(state.population.subpops[subpop].individuals); // add by mengxu 2024.7.10 to calculate the duplicated number
		//each rank cosists of the corresponding individuals

		ArrayList newSubpopulation = new ArrayList(); //a new one, size = 0
		int size = ranks.size();
		for (int i = 0; i < size; i++) { //do for each rank separately
			Individual[] rank = (Individual[]) ((ArrayList) (ranks.get(i))).toArray(dummy);
			updateReferencePoints(state, rank);//add by mengxu 2024.7.12
			if(this.manualRuleNormalisation){
				assignHVcontributionBasedOnManualRule(rank);
			}
			else if(((GPRuleEvolutionStateIBMO)state).useZScoreNormalisation){
				assignHVcontributionBasedOnZscoreNormalisation(rank);
			}
			else{
				assignHVcontribution(rank); //assign sparity value for each individual in this rank
			}
			if(i == 0){
//			assignSparsity(rank); //assign sparity value for each individual in this rank
				if (rank.length + newSubpopulation.size() >= originalPopSize[subpop]) {
					// first sort the rank by sparsity---from the small one to large one
					ec.util.QuickSort.qsort(rank, new SortComparator() {
						public boolean lt(Object a, Object b) { //Returns true if a < b, else false
							Individual i1 = (Individual) a;
							Individual i2 = (Individual) b;
							return (((IBMOMultiObjectiveFitness) i1.fitness).HVcontribution > ((IBMOMultiObjectiveFitness) i2.fitness).HVcontribution);
						}

						public boolean gt(Object a, Object b) { //Returns true if a > b, else false
							Individual i1 = (Individual) a;
							Individual i2 = (Individual) b;
							return (((IBMOMultiObjectiveFitness) i1.fitness).HVcontribution < ((IBMOMultiObjectiveFitness) i2.fitness).HVcontribution);
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
			else{
				if (rank.length + newSubpopulation.size() >= originalPopSize[subpop]) {
					// first sort the rank by sparsity---from the small one to large one
					ec.util.QuickSort.qsort(rank, new SortComparator() {
						public boolean lt(Object a, Object b) { //Returns true if a < b, else false
							Individual i1 = (Individual) a;
							Individual i2 = (Individual) b;
							return (((IBMOMultiObjectiveFitness) i1.fitness).DominatedBy < ((IBMOMultiObjectiveFitness) i2.fitness).DominatedBy);
						}

						public boolean gt(Object a, Object b) { //Returns true if a > b, else false
							Individual i1 = (Individual) a;
							Individual i2 = (Individual) b;
							return (((IBMOMultiObjectiveFitness) i1.fitness).DominatedBy > ((IBMOMultiObjectiveFitness) i2.fitness).DominatedBy);
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

		}

		Individual[] archive = (Individual[]) (newSubpopulation.toArray(dummy));

		// maybe force reevaluation
		IBMOBreeder breeder = (IBMOBreeder) (state.breeder);
		if (breeder.reevaluateElites[subpop])
			for (int i = 0; i < archive.length; i++)
				archive[i].evaluated = false;

		return archive;
	}

	public void updateReferencePoints(EvolutionState state, Individual[] rank){
		Individual ind = state.population.subpops[0].individuals[0];
		int numObjective = ((MultiObjectiveFitness)ind.fitness).objectives.length;
		this.referencePoints = new double[numObjective];

		for(int j=0; j<referencePoints.length; j++){
			referencePoints[j] = Double.NEGATIVE_INFINITY;
		}
		for(int i=0; i<rank.length; i++){
			double[] objective_i = ((MultiObjectiveFitness)rank[i].fitness).objectives;
			for(int j=0; j<objective_i.length; j++){
				if(objective_i[j] > referencePoints[j]){
					if(((GPRuleEvolutionStateIBMO)state).useZScoreNormalisation){
						referencePoints[j] = ((IBMOMultiObjectiveFitness)rank[i].fitness).getNormalisedObjectives(j);
					}
					else{
						referencePoints[j] = objective_i[j];
					}
				}
			}
		}
		for(int j=0; j<referencePoints.length; j++){
			referencePoints[j] = this.referencePointRatio*referencePoints[j];
		}
	}

	/**
	 * Divides inds into ranks and assigns each individual's rank to be the rank it
	 * was placed into. Each front is an ArrayList.
	 */
	public ArrayList assignFrontRanks(Subpopulation subpop) {
		Individual[] inds = subpop.individuals; //inds includes all individuals
		ArrayList frontsByRank = MultiObjectiveFitness.partitionIntoRanks(inds);

		int numRanks = frontsByRank.size();
		for (int rank = 0; rank < numRanks; rank++) {
			ArrayList front = (ArrayList) (frontsByRank.get(rank));
			int numInds = front.size();
			for (int ind = 0; ind < numInds; ind++)
				((IBMOMultiObjectiveFitness) (((Individual) (front.get(ind))).fitness)).rank = rank;
		}
		return frontsByRank;
	}


	//fzhang 2018.11.6 NSGA-II
	public void evaluatePopulationgp(final EvolutionState state) {
		super.evaluatePopulation(state);// evaluate population for GP with NSGA2
	}


	//mengxu 2023.03.20
	public void assignNumberOfDominatedBy(Individual[] individuals) {
		for(int i = 0; i < individuals.length; i++)
			((IBMOMultiObjectiveFitness) individuals[i].fitness).DominatedBy = 0;

		for(int i=0; i<individuals.length; i++){
			Individual ind_i = individuals[i];
			for(int j=0; j<individuals.length; j++){
				if(i != j){
					Individual ind_j = individuals[j];
					if(((MultiObjectiveFitness)ind_j.fitness).paretoDominates((MultiObjectiveFitness)ind_i.fitness)){
						((IBMOMultiObjectiveFitness) ind_i.fitness).DominatedBy += 1;
					}
				}
			}
		}
	}

	//mengxu 2023.03.20
	public void assignNumberOfDuplicatedBy(Individual[] individuals) {
		for(int i = 0; i < individuals.length; i++)
			((IBMOMultiObjectiveFitness) individuals[i].fitness).DuplicatedBy = 0;

		for(int i=0; i<individuals.length; i++){
			Individual ind_i = individuals[i];
			double[] objective_i = ((IBMOMultiObjectiveFitness) ind_i.fitness).objectives;
			for(int j=0; j<individuals.length; j++){
				if(i != j){
					Individual ind_j = individuals[j];
					double[] objective_j = ((IBMOMultiObjectiveFitness) ind_j.fitness).objectives;
					boolean same = true;
					for(int c=0; c<objective_i.length; c++){
						if(objective_i[c] != objective_j[c]){
							same = false;
						}
					}
					if(same){
						((IBMOMultiObjectiveFitness) ind_i.fitness).DuplicatedBy += 1;
					}
				}
			}
		}
	}

	public void assignHVcontributionBasedOnZscoreNormalisation(Individual[] front) {
		int numObjectives = ((IBMOMultiObjectiveFitness) front[0].fitness).getObjectives().length;

		for (int i = 0; i < front.length; i++)
			((IBMOMultiObjectiveFitness) front[i].fitness).HVcontribution = 1;

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
					return (((IBMOMultiObjectiveFitness) i1.fitness)
							.getNormalisedObjectives(o) < ((IBMOMultiObjectiveFitness) i2.fitness).getNormalisedObjectives(o));
				}

				public boolean gt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((IBMOMultiObjectiveFitness) i1.fitness)
							.getNormalisedObjectives(o) > ((IBMOMultiObjectiveFitness) i2.fitness).getNormalisedObjectives(o));
				}
			});

			//fzhang 2018.11.22 normize the objective to calculate the sparsity
			final double min = ((IBMOMultiObjectiveFitness) front[0].fitness).getNormalisedObjectives(o);
			final double max = ((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).getNormalisedObjectives(o);
//			final double min = ((IBMOMultiObjectiveFitness) front[0].fitness).getObjective(o);
//			final double max = ((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).getObjective(o);
			//todo: only normalise based on the min and max at each rank

//            System.out.println("normalised min: " + min );
//            System.out.println("normalised max: " + max );

			// Compute and assign HV contribution.
			// We think the first and last individuals give the most HV contribution, which is given 1 here.
			//todo: but we need to double think about this 2023.03.15
//			((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution = Double.POSITIVE_INFINITY;
//			((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution = Double.POSITIVE_INFINITY;
			if(this.useReferencePointBoundaryPoints){
				if(front.length>1 && max != min){
					int index_plus = 1;
					IBMOMultiObjectiveFitness f_j = (IBMOMultiObjectiveFitness) (front[0].fitness);
					IBMOMultiObjectiveFitness f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
					while(index_plus < front.length-1 && f_jplus1.getObjective(o) == f_j.getObjective(o)){
						index_plus = index_plus + 1;
						f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
					}
					((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution *= (f_jplus1.getNormalisedObjectives(o) - f_j.getNormalisedObjectives(o)); // only suitable for two objectives
					((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution *= (this.referencePoints[o] - f_j.getNormalisedObjectives(o));
				}
			}
			else{
				((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution = Double.POSITIVE_INFINITY;
				((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution = Double.POSITIVE_INFINITY;
			}
//			((IBMOMultiObjectiveFitness) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
//			((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;

			IBMOMultiObjectiveFitness f_jp0 = (IBMOMultiObjectiveFitness) (front[0].fitness);
			IBMOMultiObjectiveFitness f_jm0 = (IBMOMultiObjectiveFitness) (front[front.length - 1].fitness);

			int index_plus0 = 0;
			int index_minus0 = front.length - 1;
			IBMOMultiObjectiveFitness f_jplus0 = (IBMOMultiObjectiveFitness) (front[index_plus0].fitness);
			IBMOMultiObjectiveFitness f_jminus0 = (IBMOMultiObjectiveFitness) (front[index_minus0].fitness);

			//modified by mengxu 2023.004.18
			while(index_plus0 < front.length-1 && f_jplus0.getNormalisedObjectives(o) == f_jp0.getNormalisedObjectives(o)){
				((IBMOMultiObjectiveFitness) front[index_plus0].fitness).HVcontribution = Double.POSITIVE_INFINITY;
				index_plus0 = index_plus0 + 1;
				f_jplus0 = (IBMOMultiObjectiveFitness) (front[index_plus0].fitness);
			}

			while(index_minus0 > 0 && f_jminus0.getNormalisedObjectives(o) == f_jm0.getNormalisedObjectives(o)){
				((IBMOMultiObjectiveFitness) front[index_minus0].fitness).HVcontribution = Double.POSITIVE_INFINITY;
				index_minus0 = index_minus0 - 1;
				f_jminus0 = (IBMOMultiObjectiveFitness) (front[index_minus0].fitness);
			}
			for (int j = index_plus0; j < index_minus0; j++) {
				IBMOMultiObjectiveFitness f_j = (IBMOMultiObjectiveFitness) (front[j].fitness);
				IBMOMultiObjectiveFitness f_jplus1 = (IBMOMultiObjectiveFitness) (front[j + 1].fitness);
				int index_plus = j+1;

				//hidden by mengxu 2023.03.31
				while(index_plus < index_minus0-1 && f_jplus1.getNormalisedObjectives(o) == f_j.getNormalisedObjectives(o)){
					index_plus = index_plus + 1;
					f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
				}

				if (max ==  min)
				{
					f_j.HVcontribution *= 1;
				}
				else {
					//Strategy 1: using zScore normalisation
					f_j.HVcontribution *= (f_jplus1.getNormalisedObjectives(o) - f_j.getNormalisedObjectives(o));
				}
			}
		}
	}

	public void assignHVcontributionBasedOnManualRuleAndUseReferencePointBoundaryPoints(Individual[] front) {
		int numObjectives = ((IBMOMultiObjectiveFitness) front[0].fitness).getObjectives().length;

		for (int i = 0; i < front.length; i++)
			((IBMOMultiObjectiveFitness) front[i].fitness).HVcontribution = 1;
//			((IBMOMultiObjectiveFitness) front[i].fitness).sparsity = 0;

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
					return (((IBMOMultiObjectiveFitness) i1.fitness)
							.getObjective(o) < ((IBMOMultiObjectiveFitness) i2.fitness).getObjective(o));
				}

				public boolean gt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((IBMOMultiObjectiveFitness) i1.fitness)
							.getObjective(o) > ((IBMOMultiObjectiveFitness) i2.fitness).getObjective(o));
				}
			});

			//fzhang 2018.11.22 normize the objective to calculate the sparsity
			final double min = ((IBMOMultiObjectiveFitness) front[0].fitness).getObjective(o);
			final double max = ((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).getObjective(o);
			//todo: only normalise based on the min and max at each rank

//            System.out.println("normalised min: " + min );
//            System.out.println("normalised max: " + max );

			// Compute and assign HV contribution.
			// We think the first and last individuals give the most HV contribution, which is given 1 here.
			//todo: but we need to double think about this 2023.03.15
			((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution = Double.POSITIVE_INFINITY;
			((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution = Double.POSITIVE_INFINITY;
//			((IBMOMultiObjectiveFitness) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
//			((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
			for (int j = 1; j < front.length - 1; j++) {
				IBMOMultiObjectiveFitness f_j = (IBMOMultiObjectiveFitness) (front[j].fitness);
				IBMOMultiObjectiveFitness f_jplus1 = (IBMOMultiObjectiveFitness) (front[j + 1].fitness);
				int index_plus = j+1;

				//hidden by mengxu 2023.03.31
				while(index_plus < front.length-1 && f_jplus1.getObjective(o) == f_j.getObjective(o)){
					index_plus = index_plus + 1;
					f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
				}

				if (max ==  min)
				{
					f_j.HVcontribution *= 1;
//					f_j.sparsity += 0;
				}
				else {
					//Strategy 1: using manual rules
					f_j.HVcontribution *= (f_jplus1.getObjective(o) - f_j.getObjective(o)) / curSchedulingSetObjectiveLowerBoundMtx.getEntry(o,0);
					//Strategy 2: using max and min from current front
//					f_j.HVcontribution *= (f_jplus1.getObjective(o) - f_j.getObjective(o)) / (max- min);
//					f_j.HVcontribution *= (f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min);
				}
			}
		}
	}

	public void assignHVcontributionBasedOnManualRule(Individual[] front) {
		int numObjectives = ((IBMOMultiObjectiveFitness) front[0].fitness).getObjectives().length;

		for (int i = 0; i < front.length; i++)
			((IBMOMultiObjectiveFitness) front[i].fitness).HVcontribution = 1;
//			((IBMOMultiObjectiveFitness) front[i].fitness).sparsity = 0;

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
					return (((IBMOMultiObjectiveFitness) i1.fitness)
							.getObjective(o) < ((IBMOMultiObjectiveFitness) i2.fitness).getObjective(o));
				}

				public boolean gt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((IBMOMultiObjectiveFitness) i1.fitness)
							.getObjective(o) > ((IBMOMultiObjectiveFitness) i2.fitness).getObjective(o));
				}
			});

			//fzhang 2018.11.22 normize the objective to calculate the sparsity
			final double min = ((IBMOMultiObjectiveFitness) front[0].fitness).getObjective(o);
			final double max = ((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).getObjective(o);
			//todo: only normalise based on the min and max at each rank

//            System.out.println("normalised min: " + min );
//            System.out.println("normalised max: " + max );

			// Compute and assign HV contribution.
			// We think the first and last individuals give the most HV contribution, which is given 1 here.
			//todo: but we need to double think about this 2023.03.15
//			((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution = Double.POSITIVE_INFINITY;
//			((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution = Double.POSITIVE_INFINITY;
			if(this.useReferencePointBoundaryPoints){
				if(front.length>1 && max != min){
					int index_plus = 1;
					IBMOMultiObjectiveFitness f_j = (IBMOMultiObjectiveFitness) (front[0].fitness);
					IBMOMultiObjectiveFitness f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
					while(index_plus < front.length-1 && f_jplus1.getObjective(o) == f_j.getObjective(o)){
						index_plus = index_plus + 1;
						f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
					}
					((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution *= (f_jplus1.getObjective(o) - f_j.getObjective(o)) / curSchedulingSetObjectiveLowerBoundMtx.getEntry(o,0); // only suitable for two objectives
					((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution *= (this.referencePoints[o] - f_j.getObjective(o)) / curSchedulingSetObjectiveLowerBoundMtx.getEntry(o,0);
				}
			}
			else{
				((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution = Double.POSITIVE_INFINITY;
				((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution = Double.POSITIVE_INFINITY;
			}
//			((IBMOMultiObjectiveFitness) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
//			((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
			for (int j = 1; j < front.length - 1; j++) {
				IBMOMultiObjectiveFitness f_j = (IBMOMultiObjectiveFitness) (front[j].fitness);
				IBMOMultiObjectiveFitness f_jplus1 = (IBMOMultiObjectiveFitness) (front[j + 1].fitness);
				int index_plus = j+1;

				//hidden by mengxu 2023.03.31
				while(index_plus < front.length-1 && f_jplus1.getObjective(o) == f_j.getObjective(o)){
					index_plus = index_plus + 1;
					f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
				}

				if (max == min)
				{
					f_j.HVcontribution *= 1;
//					f_j.sparsity += 0;
				}
				else {
					//Strategy 1: using manual rules
					f_j.HVcontribution *= (f_jplus1.getObjective(o) - f_j.getObjective(o)) / curSchedulingSetObjectiveLowerBoundMtx.getEntry(o,0);
					//Strategy 2: using max and min from current front
//					f_j.HVcontribution *= (f_jplus1.getObjective(o) - f_j.getObjective(o)) / (max- min);
//					f_j.HVcontribution *= (f_jplus1.getObjective(o) - f_jminus1.getObjective(o)) / (max- min);
				}
			}
		}
	}

	public void assignHVcontribution(Individual[] front) {
		int numObjectives = ((IBMOMultiObjectiveFitness) front[0].fitness).getObjectives().length;

		for (int i = 0; i < front.length; i++)
			((IBMOMultiObjectiveFitness) front[i].fitness).HVcontribution = 1;
//			((IBMOMultiObjectiveFitness) front[i].fitness).sparsity = 0;

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
					return (((IBMOMultiObjectiveFitness) i1.fitness)
							.getObjective(o) < ((IBMOMultiObjectiveFitness) i2.fitness).getObjective(o));
				}

				public boolean gt(Object a, Object b) {
					Individual i1 = (Individual) a;
					Individual i2 = (Individual) b;
					return (((IBMOMultiObjectiveFitness) i1.fitness)
							.getObjective(o) > ((IBMOMultiObjectiveFitness) i2.fitness).getObjective(o));
				}
			});

			//fzhang 2018.11.22 normize the objective to calculate the sparsity
			final double min = ((IBMOMultiObjectiveFitness) front[0].fitness).getObjective(o);
			final double max = ((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).getObjective(o);
			//todo: only normalise based on the min and max at each rank

//            System.out.println("normalised min: " + min );
//            System.out.println("normalised max: " + max );

			// Compute and assign HV contribution.
			// We think the first and last individuals give the most HV contribution, which is given 1 here.
			//todo: but we need to double think about this 2023.03.15
//			((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution = Double.POSITIVE_INFINITY;
//			((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution = Double.POSITIVE_INFINITY;
			if(this.useReferencePointBoundaryPoints){
				if(front.length>1 && max != min){
					int index_plus = 1;
					IBMOMultiObjectiveFitness f_j = (IBMOMultiObjectiveFitness) (front[0].fitness);
					IBMOMultiObjectiveFitness f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
					while(index_plus < front.length-1 && f_jplus1.getObjective(o) == f_j.getObjective(o)){
						index_plus = index_plus + 1;
						f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
					}
					((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution *= (f_jplus1.getObjective(o) - f_j.getObjective(o)) / (max - min); // only suitable for two objectives
					((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution *= (this.referencePoints[o] - f_j.getObjective(o)) / (max - min);
				}
			}
			else{
				((IBMOMultiObjectiveFitness) front[0].fitness).HVcontribution = Double.POSITIVE_INFINITY;
				((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).HVcontribution = Double.POSITIVE_INFINITY;
			}
//			((IBMOMultiObjectiveFitness) front[0].fitness).sparsity = Double.POSITIVE_INFINITY;
//			((IBMOMultiObjectiveFitness) front[front.length - 1].fitness).sparsity = Double.POSITIVE_INFINITY;
			for (int j = 1; j < front.length - 1; j++) {
				IBMOMultiObjectiveFitness f_j = (IBMOMultiObjectiveFitness) (front[j].fitness);
				IBMOMultiObjectiveFitness f_jplus1 = (IBMOMultiObjectiveFitness) (front[j + 1].fitness);
				int index_plus = j+1;
				while(index_plus < front.length-1 && f_jplus1.getObjective(o) == f_j.getObjective(o)){
					index_plus = index_plus + 1;
					f_jplus1 = (IBMOMultiObjectiveFitness) (front[index_plus].fitness);
				}


				if (max ==  min)
				{
					f_j.HVcontribution *= 1;
//					f_j.sparsity += 0;
				}
				else {
					f_j.HVcontribution *= (f_jplus1.getObjective(o) - f_j.getObjective(o)) / (max - min);
				}
			}
		}
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
			IBMOMultiObjectiveFitness fitnessNormalisation = (IBMOMultiObjectiveFitness)state.population.subpops[0].individuals[i].fitness;
			fitnessNormalisation.normalisation(means, stds);
		}
	}
}
