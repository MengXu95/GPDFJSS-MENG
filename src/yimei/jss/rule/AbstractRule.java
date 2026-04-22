package yimei.jss.rule;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.algorithm.multitaskHeuristicLearning.GPRuleEvolutionStateMultiTaskLearningSurrogate;
import mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodel.GPRuleEvolutionStateMultiTaskLearningSurrogateSVR;
import mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodelOneSubpop.GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop;
import mengxu.algorithm.multitaskHeuristicLearning.onlysurrogate.GPRuleEvolutionStateMultiTaskLearningOnlySurrogate;
import mengxu.complexsimulation.HeterogeneousSimulation;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.state.SystemState;

import java.util.ArrayList;
import java.util.List;

/**
 * The abstract dispatching rule for job shop scheduling.
 * <p>
 * Created by yimei on 22/09/16.
 */
public abstract class AbstractRule extends EvolutionState{

	protected String name;
	protected RuleType type;

	//fzhang 2018.10.10  get seed
    protected long jobSeed;

	public String getName() {
		return name;
	}

	public RuleType getType() {
		return type;
	}

	@Override
	public String toString() {
		return name;
	}

	public RealMatrix objectiveValueMatrix(SchedulingSet schedulingSet, List<Objective> objectives) {
		int rows = schedulingSet.getObjectiveLowerBoundMtx().getRowDimension();
		int cols = schedulingSet.getObjectiveLowerBoundMtx().getColumnDimension();

		RealMatrix matrix = new Array2DRowRealMatrix(rows, cols);
		List<Simulation> simulations = schedulingSet.getSimulations();
		int col = 0;

		for (int j = 0; j < simulations.size(); j++) {
			Simulation simulation = simulations.get(j);
			simulation.setSequencingRule(this);

			simulation.run();
			// System.out.println(simulation.workCenterUtilLevelsToString());

			for (int i = 0; i < objectives.size(); i++) {
				matrix.setEntry(i, col, simulation.objectiveValue(objectives.get(i)));
			}

			col++;

			for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
				simulation.rerun();
				// System.out.println(simulation.workCenterUtilLevelsToString());

				for (int i = 0; i < objectives.size(); i++) {
					matrix.setEntry(i, col, simulation.objectiveValue(objectives.get(i)));
				}

				col++;
			}

			simulation.reset();
		}

		return matrix;
	}

	public void calcFitnessWithPreference(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet, AbstractRule otherRule,
							List<Objective> objectives, int preference_index) {
		// whenever fitness is calculated, need a routing rule and a sequencing rule
		if (this.getType() == otherRule.getType()) {
			System.out.println(
					"We need one routing rule and one sequencing rule, not 2 " + otherRule.getType() + " rules.");
			return;
		}
		AbstractRule routingRule;
		AbstractRule sequencingRule;
		// check type, not here
		if (this.getType() == RuleType.ROUTING) {
			routingRule = this;
			sequencingRule = otherRule;
		} else {
			routingRule = otherRule;
			sequencingRule = this;
		}

		double[] fitnesses = new double[objectives.size()];

		List<Simulation> simulations = schedulingSet.getSimulations();

		//modified by mengxu 2021.07.27
		int[] col = new int[objectives.size()];

		for (int j = 0; j < simulations.size(); j++) {
			Simulation simulation = simulations.get(j);
			simulation.setSequencingRule(sequencingRule);
			simulation.setRoutingRule(routingRule);

			if(state instanceof GPRuleEvolutionStatePSL){
				simulation.state = state;
				simulation.state.generation = preference_index;
			}

			simulation.rerun();

			simulation.useSequencingTimes = 0;
			simulation.noUseSequencingTimes = 0;

			for (int i = 0; i < objectives.size(); i++) {

				double ObjValue = simulation.objectiveValue(objectives.get(i));

				//modified by mengxu 2021.07.15
				if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
//					System.out.println("bad 0 fitness: " + ObjValue);
				}else{
					fitnesses[i] += ObjValue;
					col[i]++;
				}
			}

			//System.out.println("The value of replication is "+schedulingSet.getReplications()); //50
			for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {

				if(state instanceof GPRuleEvolutionStatePSL){
					simulation.state = state;
					simulation.state.generation = preference_index;
				}

				simulation.rerun();

				for (int i = 0; i < objectives.size(); i++) {

					double ObjValue = simulation.objectiveValue(objectives.get(i));

					//modified by mengxu 2021.07.15
					if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
						System.out.println("bad " + k +" fitness: " + ObjValue);
					}
					else{
						fitnesses[i] += ObjValue;
						col[i]++;
					}

				}

			}

			simulation.reset();
		}

		//modified by mengxu 2021.07.27
		for (int i = 0; i < fitnesses.length; i++) {
			if(col[i] == 0){
				fitnesses[i] = -1;//all test are bad run!!!
			}
			else{
				fitnesses[i] /= col[i];
			}
		}
		MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
		f.setObjectives(state, fitnesses);
	}

	public void calcFitnessForMultiTask(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet, AbstractRule otherRule,
										  List<Objective> objectives) {
		// whenever fitness is calculated, need a routing rule and a sequencing rule
		if (this.getType() == otherRule.getType()) {
			System.out.println(
					"We need one routing rule and one sequencing rule, not 2 " + otherRule.getType() + " rules.");
			return;
		}
		AbstractRule routingRule;
		AbstractRule sequencingRule;
		// check type, not here
		if (this.getType() == RuleType.ROUTING) {
			routingRule = this;
			sequencingRule = otherRule;
		} else {
			routingRule = otherRule;
			sequencingRule = this;
		}

		double[] fitnesses = new double[objectives.size()];

		List<Simulation> simulations = schedulingSet.getSimulations();

		//modified by mengxu 2021.07.27
		int[] col = new int[objectives.size()];

		for (int j = 0; j < simulations.size(); j++) {
			Simulation simulation = simulations.get(j);
			simulation.setSequencingRule(sequencingRule);
			simulation.setRoutingRule(routingRule);

			if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogate){
				simulation.state = state;
			}
			else if(state instanceof GPRuleEvolutionStateMultiTaskLearningOnlySurrogate){
				simulation.state = state;
			}
			else if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogateSVR){
				simulation.state = state;
			}
			else if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop){
				simulation.state = state;
			}

			simulation.rerun();

			simulation.useSequencingTimes = 0;
			simulation.noUseSequencingTimes = 0;

			for (int i = 0; i < objectives.size(); i++) {

				double ObjValue = simulation.objectiveValue(objectives.get(i));

				//modified by mengxu 2021.07.15
				if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
//					System.out.println("bad 0 fitness: " + ObjValue);
				}else{
					fitnesses[i] += ObjValue;
					col[i]++;
				}
			}

			//System.out.println("The value of replication is "+schedulingSet.getReplications()); //50
			for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {

				if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogate){
					simulation.state = state;
				}

				simulation.rerun();

				for (int i = 0; i < objectives.size(); i++) {

					double ObjValue = simulation.objectiveValue(objectives.get(i));

					//modified by mengxu 2021.07.15
					if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
						System.out.println("bad " + k +" fitness: " + ObjValue);
					}
					else{
						fitnesses[i] += ObjValue;
						col[i]++;
					}

				}

			}

			simulation.reset();
		}

		//modified by mengxu 2021.07.27
		for (int i = 0; i < fitnesses.length; i++) {
			if(col[i] == 0){
				fitnesses[i] = -1;//all test are bad run!!!
			}
			else{
				fitnesses[i] /= col[i];
			}
		}
		MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
		f.setObjectives(state, fitnesses);
	}

	public void calcFitness(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet, AbstractRule otherRule,
			List<Objective> objectives) {
		// whenever fitness is calculated, need a routing rule and a sequencing rule
		if (this.getType() == otherRule.getType()) {
			System.out.println(
					"We need one routing rule and one sequencing rule, not 2 " + otherRule.getType() + " rules.");
			return;
		}
		AbstractRule routingRule;
		AbstractRule sequencingRule;
		// check type, not here
		if (this.getType() == RuleType.ROUTING) {
			routingRule = this;
			sequencingRule = otherRule;
		} else {
			routingRule = otherRule;
			sequencingRule = this;
		}

		double[] fitnesses = new double[objectives.size()];

		List<Simulation> simulations = schedulingSet.getSimulations();
//		int col = 0;

		//modified by mengxu 2021.07.27
		int[] col = new int[objectives.size()];

		//System.out.println("The simulation size is "+simulations.size()); //1
		for (int j = 0; j < simulations.size(); j++) {
			Simulation simulation = simulations.get(j);
			simulation.setSequencingRule(sequencingRule);
			simulation.setRoutingRule(routingRule);
			// }
			simulation.rerun();

			//add by mengxu 2023.09.28 for output schedule results
//			((HeterogeneousSimulation)simulation).writeSystemInformationForCaseStudyToFile(objectives.get(0));//todo: remember to hide when do not use 2023.09.23
//			((HeterogeneousSimulation)simulation).writeScheduleForCaseStudyToFile(objectives.get(0));//todo: remember to hide when do not use 2023.09.23

//			System.out.println("Use sequencing times: " + simulation.useSequencingTimes);
//			System.out.println("Not use sequencing times: " + simulation.noUseSequencingTimes);
//			System.out.println();
			simulation.useSequencingTimes = 0;
			simulation.noUseSequencingTimes = 0;


			//to check the busy time of each machine, 2022.03.28============================
//			List<WorkCenter> workCenters = simulation.getSystemState().getWorkCenters();
//			double totalBusyTime = 0;
//			for(int i=0; i< workCenters.size(); i++){
//				System.out.println("WorkCenter " + i + " busy time * processing rate: " + workCenters.get(i).getBusyTime() * workCenters.get(i).getProcessingRate());
//				totalBusyTime += workCenters.get(i).getBusyTime();
//			}
//			System.out.println("Total busy time: " + totalBusyTime);
//			System.out.println("Add to queue time: " + simulation.times_addToQueue);
//			System.out.println("Directly process time: " + simulation.times_OVE);
//
//			simulation.times_addToQueue = 0;
//			simulation.times_OVE = 0;

//			System.out.println("Sequencing rule use time: " + simulation.sequencingRuleUseTimes);
			//===============================================================================

			for (int i = 0; i < objectives.size(); i++) {
				// System.out.println("Makespan:
				// "+simulation.objectiveValue(objectives.get(i)));
				// System.out.println("Benchmark makespan:
				// "+schedulingSet.getObjectiveLowerBound(i, col));
				
				//fzhang 2018.10.23  cancel normalizing objective
//				double normObjValue = simulation.objectiveValue(objectives.get(i))
//						/ schedulingSet.getObjectiveLowerBound(i, col);

				double ObjValue = simulation.objectiveValue(objectives.get(i));

//				//in essence, here is useless. because if w.numOpsInQueue() > 100, the simulation has been canceled in run(). here is a double check
//				for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
//					if (w.numOpsInQueue() > 100) {
//						//this was a bad run
//
//						//fzhang cancel normalized process
////                      normObjValue = Double.MAX_VALUE;
//						ObjValue = Double.MAX_VALUE;
//
//						//System.out.println(systemState.getJobsInSystem().size());
//						//System.out.println(systemState.getJobsCompleted().size());
//						break;
//					}
//				}

				//modified by fzhang, 26.4.2018  check in test process, whether there is ba
				//fzhang 2018.10.23  cancel normalizing objective
//				fitnesses[i] += normObjValue;

				//modified by mengxu 2021.07.15
				if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
//					System.out.println("bad 0 fitness: " + ObjValue);
				}else{
					fitnesses[i] += ObjValue;
					col[i]++;
				}
				//2021.06.21 modified by mengxu
//				simulation.writeCurvesToFile(objectives.get(i),((DynamicSimulation)simulation).getUtilLevel(),0);
//				simulation.writeWorkCenterCurvesToFile(objectives.get(i),((DynamicSimulation)simulation).getUtilLevel(),0);
			}

//			col++;

			//System.out.println("The value of replication is "+schedulingSet.getReplications()); //50
			for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
				simulation.rerun();

				for (int i = 0; i < objectives.size(); i++) {
//					double normObjValue = simulation.objectiveValue(objectives.get(i))
//							/ schedulingSet.getObjectiveLowerBound(i, col);
//					fitnesses[i] += normObjValue;
				
					//fzhang 2018.10.23  cancel normalizing objective
					double ObjValue = simulation.objectiveValue(objectives.get(i));

//					System.out.println("run " + k +" fitness: " + ObjValue);
//					for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
//						if (w.numOpsInQueue() > 100) {
//							//this was a bad run
//
//							//fzhang cancel normalized process
////                      normObjValue = Double.MAX_VALUE;
//							ObjValue = Double.MAX_VALUE;
//
//							//System.out.println(systemState.getJobsInSystem().size());
//							//System.out.println(systemState.getJobsCompleted().size());
//							break;
//						}
//					}
					//modified by mengxu 2021.07.15
					if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
						System.out.println("bad " + k +" fitness: " + ObjValue);
					}
					else{
						fitnesses[i] += ObjValue;
						col[i]++;
					}

				}

//				col++;
			}



			simulation.reset();
		}

		//original
//		for (int i = 0; i < fitnesses.length; i++) {
//			fitnesses[i] /= col;
//		}

		//modified by mengxu 2021.07.27
		for (int i = 0; i < fitnesses.length; i++) {
			if(col[i] == 0){
				fitnesses[i] = -1;//all test are bad run!!!
			}
			else{
				fitnesses[i] /= col[i];
			}
		}
		MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
		f.setObjectives(state, fitnesses);
	}


	//add by mengxu 2022.04.06
	public void calcMultiCaseFitness(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet, AbstractRule otherRule,
							List<Objective> objectives) {
		// whenever fitness is calculated, need a routing rule and a sequencing rule
		if (this.getType() == otherRule.getType()) {
			System.out.println(
					"We need one routing rule and one sequencing rule, not 2 " + otherRule.getType() + " rules.");
			return;
		}
		AbstractRule routingRule;
		AbstractRule sequencingRule;
		// check type, not here
		if (this.getType() == RuleType.ROUTING) {
			routingRule = this;
			sequencingRule = otherRule;
		} else {
			routingRule = otherRule;
			sequencingRule = this;
		}

//		double[] fitnesses = new double[objectives.size()];

		int numCase = 25;

		double[][] fitnesses = new double[numCase][objectives.size()];//modified by mengxu

		List<Simulation> simulations = schedulingSet.getSimulations();
//		int col = 0;

		//modified by mengxu 2021.07.27
		int[] col = new int[objectives.size()];

		//System.out.println("The simulation size is "+simulations.size()); //1
		for (int j = 0; j < simulations.size(); j++) {
			Simulation simulation = simulations.get(j);
			simulation.setSequencingRule(sequencingRule);
			simulation.setRoutingRule(routingRule);
			// }
			simulation.rerun();

			System.out.println("Use sequencing times: " + simulation.useSequencingTimes);
			System.out.println("Not use sequencing times: " + simulation.noUseSequencingTimes);
			System.out.println();
			simulation.useSequencingTimes = 0;
			simulation.noUseSequencingTimes = 0;


			//to check the busy time of each machine, 2022.03.28============================
//			List<WorkCenter> workCenters = simulation.getSystemState().getWorkCenters();
//			double totalBusyTime = 0;
//			for(int i=0; i< workCenters.size(); i++){
//				System.out.println("WorkCenter " + i + " busy time * processing rate: " + workCenters.get(i).getBusyTime() * workCenters.get(i).getProcessingRate());
//				totalBusyTime += workCenters.get(i).getBusyTime();
//			}
//			System.out.println("Total busy time: " + totalBusyTime);
//			System.out.println("Add to queue time: " + simulation.times_addToQueue);
//			System.out.println("Directly process time: " + simulation.times_OVE);
//
//			simulation.times_addToQueue = 0;
//			simulation.times_OVE = 0;

//			System.out.println("Sequencing rule use time: " + simulation.sequencingRuleUseTimes);
			//===============================================================================

			for (int i = 0; i < objectives.size(); i++) {
				// System.out.println("Makespan:
				// "+simulation.objectiveValue(objectives.get(i)));
				// System.out.println("Benchmark makespan:
				// "+schedulingSet.getObjectiveLowerBound(i, col));

				//fzhang 2018.10.23  cancel normalizing objective
//				double normObjValue = simulation.objectiveValue(objectives.get(i))
//						/ schedulingSet.getObjectiveLowerBound(i, col);

//				double ObjValue = simulation.objectiveValue(objectives.get(i));
				double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan


//				//in essence, here is useless. because if w.numOpsInQueue() > 100, the simulation has been canceled in run(). here is a double check
//				for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
//					if (w.numOpsInQueue() > 100) {
//						//this was a bad run
//
//						//fzhang cancel normalized process
////                      normObjValue = Double.MAX_VALUE;
//						ObjValue = Double.MAX_VALUE;
//
//						//System.out.println(systemState.getJobsInSystem().size());
//						//System.out.println(systemState.getJobsCompleted().size());
//						break;
//					}
//				}

				//modified by fzhang, 26.4.2018  check in test process, whether there is ba
				//fzhang 2018.10.23  cancel normalizing objective
//				fitnesses[i] += normObjValue;

				for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
//					fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
					//modified by mengxu 2021.07.15
					if(multiCaseObjValue[indexCase] >= Double.POSITIVE_INFINITY || multiCaseObjValue[indexCase] >= Double.MAX_VALUE){
//					System.out.println("bad 0 fitness: " + ObjValue);
					}else{
						fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
						col[i]++;
					}
				}

				//2021.06.21 modified by mengxu
//				simulation.writeCurvesToFile(objectives.get(i),((DynamicSimulation)simulation).getUtilLevel(),0);
//				simulation.writeWorkCenterCurvesToFile(objectives.get(i),((DynamicSimulation)simulation).getUtilLevel(),0);
			}

//			col++;

			//System.out.println("The value of replication is "+schedulingSet.getReplications()); //50
			for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
				simulation.rerun();

				for (int i = 0; i < objectives.size(); i++) {
//					double normObjValue = simulation.objectiveValue(objectives.get(i))
//							/ schedulingSet.getObjectiveLowerBound(i, col);
//					fitnesses[i] += normObjValue;

					//fzhang 2018.10.23  cancel normalizing objective
//					double ObjValue = simulation.objectiveValue(objectives.get(i));

					double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan

					for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
//						fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
						//modified by mengxu 2021.07.15
						if(multiCaseObjValue[indexCase] >= Double.POSITIVE_INFINITY || multiCaseObjValue[indexCase] >= Double.MAX_VALUE){
//					System.out.println("bad 0 fitness: " + ObjValue);
						}else{
							fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
							col[i]++;
						}
					}

//					System.out.println("run " + k +" fitness: " + ObjValue);
//					for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
//						if (w.numOpsInQueue() > 100) {
//							//this was a bad run
//
//							//fzhang cancel normalized process
////                      normObjValue = Double.MAX_VALUE;
//							ObjValue = Double.MAX_VALUE;
//
//							//System.out.println(systemState.getJobsInSystem().size());
//							//System.out.println(systemState.getJobsCompleted().size());
//							break;
//						}
//					}
//					//modified by mengxu 2021.07.15
//					if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
//						System.out.println("bad " + k +" fitness: " + ObjValue);
//					}
//					else{
//						fitnesses[i] += ObjValue;
//						col[i]++;
//					}

				}

//				col++;
			}



			simulation.reset();
		}

		//original
//		for (int i = 0; i < fitnesses.length; i++) {
//			fitnesses[i] /= col;
//		}

		for (int j = 0; j < simulations.size(); j++) {
			for (int indexCase = 0; indexCase < fitnesses.length; indexCase++) {
				for (int i = 0; i < fitnesses[j].length; i++) {
					if(col[i] == 0){
						fitnesses[indexCase][i] = -1;//all test are bad run!!!
					}
					else{
						fitnesses[indexCase][i] /= col[i];
					}
//					fitnesses[indexCase][i] /= schedulingSet.getReplications().get(j);
//                    System.out.print(fitnesses[indexCase][i] + ", ");
				}
			}
		}

		OneInstanceMultiCaseMultiObjectiveFitness f = (OneInstanceMultiCaseMultiObjectiveFitness) fitness;
		f.setMultiInstanceFitness(state, fitnesses);
		f.setCurrentObjective(objectives);


		//modified by mengxu 2021.07.27
//		for (int i = 0; i < fitnesses.length; i++) {
//			if(col[i] == 0){
//				fitnesses[i] = -1;//all test are bad run!!!
//			}
//			else{
//				fitnesses[i] /= col[i];
//			}
//		}
//		MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
//		f.setObjectives(state, fitnesses);
	}
	
	public OperationOption priorOperation(SequencingDecisionSituation sequencingDecisionSituation) {
		
		List<OperationOption> queue = sequencingDecisionSituation.getQueue();
		WorkCenter workCenter = sequencingDecisionSituation.getWorkCenter();
		SystemState systemState = sequencingDecisionSituation.getSystemState();

		//fzhang 2018.10.23  original one
		//============================start==============================	  
		OperationOption priorOp = queue.get(0);
		priorOp.setPriority(priority(priorOp, workCenter, systemState));

		for (int i = 1; i < queue.size(); i++) {
			OperationOption op = queue.get(i);
			op.setPriority(priority(op, workCenter, systemState));

			if (op.priorTo(priorOp))
				priorOp = op;
		}

		return priorOp;
		}
		//============================end==============================

		//fzhang 2019.9.25 calculate the priority values of each operations, then get the rank of all operations
	//==============================================start=====================================================
		public int[] priorValueOperation(SequencingDecisionSituation sequencingDecisionSituation) {

			List<OperationOption> queue = sequencingDecisionSituation.getQueue();
			WorkCenter workCenter = sequencingDecisionSituation.getWorkCenter();
			SystemState systemState = sequencingDecisionSituation.getSystemState();

			//============================start=============================
			int[] ranks = new int[queue.size()];
			for (int i = 0; i < queue.size(); i++) {
				OperationOption op = queue.get(i);
				op.setPriority(priority(op, workCenter, systemState));
				//priorityValue[i] = op.getPriority();
			}

			int rank = 1;
			for (int i = 0; i < queue.size(); i++) {
				for (int j = 0; j < queue.size(); j++) {
					if (queue.get(j).priorTo(queue.get(i))) {
						rank++;
					}
				}
				ranks[i] = rank;
				rank = 1;
			}

			//do not have effect here, that means the phenotypic characterisation is fine for sequencing rule
			//but safe to put it here
			int count = 0;
			for(int i = ranks.length-1; i >= 0; i--){
				for(int j = 0; j < i; j++){
					if (ranks[j] == ranks[i]){
						count++;
					}
				}
				ranks[i] += count;
				count = 0;
			}

			return ranks;
		}

		//add by mengxu for sequencing decision
//	public int[] priorValueOperationLinearEnsemble(SequencingDecisionSituation sequencingDecisionSituation) {
//		List<GPRule> ensembleSequencingRule = new ArrayList<>();
//		List<GPRule> ensembleRoutingRule = new ArrayList<>();
//		for(int i=0; i<this..size(); i++){
//			if(i< rules.size()/2){
//				ensembleSequencingRule.add((GPRule) rules.get(i));
//			}
//			else{
//				ensembleRoutingRule.add((GPRule) rules.get(i));
//			}
//		}
//		EnsembleRule ensembleRule = new EnsembleRule(ensembleSequencingRule,ensembleRoutingRule);
//
//		double[] ensembleContribution = new double[ensembleSequencingRule.size()];
//
//		List<OperationOption> queue = sequencingDecisionSituation.getQueue();
//		WorkCenter workCenter = sequencingDecisionSituation.getWorkCenter();
//		SystemState systemState = sequencingDecisionSituation.getSystemState();
//
//		//============================start=============================
//		int[] ranks = new int[queue.size()];
//		for (int i = 0; i < queue.size(); i++) {
//			OperationOption op = queue.get(i);
//			op.setPriority(priority(op, workCenter, systemState));
//			//priorityValue[i] = op.getPriority();
//		}
//
//		int rank = 1;
//		for (int i = 0; i < queue.size(); i++) {
//			for (int j = 0; j < queue.size(); j++) {
//				if (queue.get(j).priorTo(queue.get(i))) {
//					rank++;
//				}
//			}
//			ranks[i] = rank;
//			rank = 1;
//		}
//
//		//do not have effect here, that means the phenotypic characterisation is fine for sequencing rule
//		//but safe to put it here
//		int count = 0;
//		for(int i = ranks.length-1; i >= 0; i--){
//			for(int j = 0; j < i; j++){
//				if (ranks[j] == ranks[i]){
//					count++;
//				}
//			}
//			ranks[i] += count;
//			count = 0;
//		}
//
//		return ranks;
//	}
//==================================================end=======================================================
	
	//fzhang 2018.10.10  get the seed value
	public long getSeed(final Parameter base) {
		 Parameter p;
			// Get the job seed.
			p = new Parameter("seed").push(""+0);
	        return jobSeed = state.parameters.getLongWithDefault(p, null, 0);
	}

	//fzhang 2019.9.25 calculate the priority values of each operations, then get the rank of all machines
	public int[] priorValueOperationMahcine(RoutingDecisionSituation routingDecisionSituation) {

		List<OperationOption> queue = routingDecisionSituation.getQueue();
		SystemState systemState = routingDecisionSituation.getSystemState();
		int[] ranks = new int[queue.size()];

		for (int i = 0; i < queue.size(); i++) {
			OperationOption operationOption = queue.get(i);
			operationOption.setPriority(priority(operationOption, operationOption.getWorkCenter(), systemState));
		}

		int rank = 1;
		for (int i = 0; i < queue.size(); i++) {
			for (int j = 0; j < queue.size(); j++) {
				if (queue.get(j).priorTo(queue.get(i))) {
					rank++;
				}
			}
			ranks[i] = rank;
			rank = 1;
		}

		int count = 0;
		for(int i = ranks.length-1; i >= 0; i--){
			for(int j = 0; j < i; j++){
                if (ranks[j] == ranks[i]){
                	count++;
				}
			}
			ranks[i] += count;
			count = 0;
		}

		return ranks;
	}
	//============end============================================


	public OperationOption nextOperationOption(RoutingDecisionSituation routingDecisionSituation) {

		List<OperationOption> queue = routingDecisionSituation.getQueue();
		SystemState systemState = routingDecisionSituation.getSystemState();
		//================original=================
		//==================start==================
		OperationOption bestOperationOption = queue.get(0);
		bestOperationOption
				.setPriority(priority(bestOperationOption, bestOperationOption.getWorkCenter(), systemState));
		// loop all the options, save the best one as "selected" one
		for (int i = 1; i < queue.size(); i++) {
			OperationOption operationOption = queue.get(i);
			operationOption.setPriority(priority(operationOption, operationOption.getWorkCenter(), systemState));

			if (operationOption.priorTo(bestOperationOption)) {
				bestOperationOption = operationOption;
			}
		}
		return bestOperationOption;// this links which machine will be chosen.
	}


	
		//===========================================AAAI2019========================================
		//fzhang 6.8.2018  incorporating knowledge (workload) into dispatching rule---start from here
		//=========================================start============================================
/*		double totalProcessTimeOfWorkCenterInSystem = 0;

		for (WorkCenter w : systemState.getWorkCenters()) {
			totalProcessTimeOfWorkCenterInSystem += w.getWorkInQueue();
		}

		OperationOption bestOperationOption = queue.get(0);

		//fzhang 4.6.2018 set the priority value related to workload/workloadInSystem
		//setPriority(): this method is set a double value as priority value to OperationOption
		double bestWorkLoadRatio = 0;
		if(bestOperationOption.getWorkCenter().getWorkInQueue() != 0) {
		     bestWorkLoadRatio = bestOperationOption.getWorkCenter().getWorkInQueue()
					/ totalProcessTimeOfWorkCenterInSystem;
		     bestOperationOption
				.setPriority(1/(1-bestWorkLoadRatio)*priority(bestOperationOption, bestOperationOption.getWorkCenter(), systemState));
		}
		else
			bestOperationOption
			.setPriority(priority(bestOperationOption, bestOperationOption.getWorkCenter(), systemState));
		
		
		// loop all the options, save the best one as "selected" one

		for (int i = 1; i < queue.size(); i++) {
			OperationOption operationOption = queue.get(i);
			double workLoadRatio = 0;
			
			if(operationOption.getWorkCenter().getWorkInQueue() != 0) {
				workLoadRatio = operationOption.getWorkCenter().getWorkInQueue()
						/ totalProcessTimeOfWorkCenterInSystem;
					operationOption.setPriority(1/(1-workLoadRatio) * priority(operationOption, operationOption.getWorkCenter(), systemState));
			}
			else
			  operationOption.setPriority(priority(operationOption, operationOption.getWorkCenter(), systemState));
			
			if (operationOption.priorTo(bestOperationOption)) {
				bestOperationOption = operationOption;
			}
		}

		return bestOperationOption;// this links which machine will be chosen.
}*/
//=========================================================end================================================
	public abstract double priority(OperationOption op, WorkCenter workCenter, SystemState systemState);
}
