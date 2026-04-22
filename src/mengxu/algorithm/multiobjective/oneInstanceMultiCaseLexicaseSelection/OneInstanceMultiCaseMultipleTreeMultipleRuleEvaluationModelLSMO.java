package mengxu.algorithm.multiobjective.oneInstanceMultiCaseLexicaseSelection;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.util.Parameter;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.FlexibleStaticInstance;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.rule.AbstractRule;
import yimei.jss.ruleevaluation.MultipleTreeMultipleRuleEvaluationModel;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.StaticSimulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OneInstanceMultiCaseMultipleTreeMultipleRuleEvaluationModelLSMO extends MultipleTreeMultipleRuleEvaluationModel {

    public final static int SEED_ROTATION = 10000;//modified by mengxu

    public static final String P_NUMCASE = "num-case";
    public int numCase;

    @Override
    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        // Get the seed for the simulation.
        Parameter p = base.push(P_SIM_SEED);
        simSeed = state.parameters.getLongWithDefault(p, null, 0);

        // Get the simulation models.
        p = base.push(P_SIM_MODELS);
        int numSimModels = state.parameters.getIntWithDefault(p, null, 0);

        if (numSimModels == 0) {
            System.err.println("ERROR:");
            System.err.println("No simulation model is specified.");
            System.exit(1);
        }

        List<Simulation> trainSimulations = new ArrayList<>();
        List<Integer> replications = new ArrayList<>();
        for (int x = 0; x < numSimModels; x++) {
            // Read this simulation model
            Parameter b = base.push(P_SIM_MODELS).push("" + x);
            // Number of machines
            p = b.push(P_SIM_NUM_MACHINES);
            int numMachines = state.parameters.getIntWithDefault(p, null, 10);
            // Number of jobs
            p = b.push(P_SIM_NUM_JOBS);
            int numJobs = state.parameters.getIntWithDefault(p, null, 5000);
            // Number of warmup jobs
            p = b.push(P_SIM_WARMUP_JOBS);
            int warmupJobs = state.parameters.getIntWithDefault(p, null, 1000);
            // Min number of operations
            p = b.push(P_SIM_MIN_NUM_OPERATIONS);
            int minNumOperations = state.parameters.getIntWithDefault(p, null, 1);
            // Max number of operations
            p = b.push(P_SIM_MAX_NUM_OPERATIONS);
            int maxNumOperations = state.parameters.getIntWithDefault(p, null, numMachines);
            // Utilization level
            p = b.push(P_SIM_UTIL_LEVEL);
            double utilLevel = state.parameters.getDoubleWithDefault(p, null, 0.85);
            // Due date factor
            p = b.push(P_SIM_DUE_DATE_FACTOR);
            double dueDateFactor = state.parameters.getDoubleWithDefault(p, null, 4.0);
            // Number of replications
            p = b.push(P_SIM_REPLICATIONS);
            int rep = state.parameters.getIntWithDefault(p, null, 1);

            Simulation simulation = null;
            //only expecting filePath parameter for Static FJSS, so can use this
            String filePath = state.parameters.getString(new Parameter("filePath"), null);
            if (filePath == null) {
//                Simulation evoSimulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);


                //Dynamic Heterogeneous Simulation modified by mengxu
                simulation = new HeterogeneousSimulation(simSeed,
                        null, null, numMachines, numJobs, warmupJobs,
                        minNumOperations, maxNumOperations,
                        utilLevel, dueDateFactor, false);

                //Dynamic Simulation original
//                simulation = new DynamicSimulation(simSeed,
//                        null, null, numMachines, numJobs, warmupJobs,
//                        minNumOperations, maxNumOperations,
//                        utilLevel, dueDateFactor, false);


            } else {
                FlexibleStaticInstance instance = FlexibleStaticInstance.readFromAbsPath(filePath);
                simulation = new StaticSimulation(null, null, instance);
            }
            trainSimulations.add(simulation);
            replications.add(new Integer(rep));
            //modified by mengxu rotate simSeed
//            this.simSeed = this.simSeed + this.SEED_ROTATION;
        }

        schedulingSet = new SchedulingSet(trainSimulations, replications, objectives);

        p = base.push(P_ROTATE_SIM_SEED);
        rotateSimSeed = state.parameters.getBoolean(p, null, false);

        numCase = state.parameters.getInt(new Parameter(P_NUMCASE), null, 0);
        if (numCase <= 0)
            state.output.fatal("The number of instances must be an integer >= 1.");

    }

    @Override
    public void evaluate(List<Fitness> currentFitnesses,
                         List<AbstractRule> rules,
                         ec.EvolutionState state) {
        //expecting 2 rules here - one routing rule and one sequencing rule
        if (rules.size() != 2) {
            System.out.println("Rule evaluation failed!");
            System.out.println("Expecting 2 rules, only 1 found.");
            return;
        }

        //System.out.println(rules.size()); //2 repeat
        countInd++;

        AbstractRule sequencingRule = rules.get(0); // for each arraylist in list, they have two elements, the first one is sequencing rule and the second one is routing rule
        AbstractRule routingRule = rules.get(1);

//        double[] fitnesses = new double[objectives.size()];

        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;
        double[][] fitnesses = new double[numCase][objectives.size()];//modified by mengxu


        //System.out.println(simulations.size()); // 1 repeat
        //System.out.println(schedulingSet.getReplications().get(0)); //1 repeat

        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);

            //========================change here======================================
            simulation.setSequencingRule(sequencingRule); //indicate different individuals
            simulation.setRoutingRule(routingRule);
            //System.out.println(simulation);
            simulation.run();
//            //test
//            System.out.print("Job list: [");
//            for(Job job:simulation.getSystemState().getJobsCompleted()){
//                if(job != null){
//                    System.out.print(job.getId() + ",");
//                }
//                else{
//                    System.out.print( -1 + ",");
//                }
//
//            }
//            System.out.println("}");

            for (int i = 0; i < objectives.size(); i++) {
                //2018.10.23  cancel normalized process
//                double normObjValue = simulation.objectiveValue(objectives.get(i))  // this line: the value of makespan
//                        / schedulingSet.getObjectiveLowerBound(i, col);

                double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan

                //modified by mengxu 2022.01.06
//                if(state instanceof GPRuleEvolutionStateDPS){ //add 2021.12.23
//                    if(((GPRuleEvolutionStateDPS) state).isUseDPSSelection()){
//                        if(((GPRuleEvolutionStateDPS) state).allTargetObjectiveList.size() == state.generation){
//                            //have not add target for this generation
//                            double[] target = simulation.estimateTargetValue(objectives.get(i),numCase);
//                            ((GPRuleEvolutionStateDPS) state).allTargetObjectiveList.add(target);
//                        }
//                    }
//                }

                //in essence, here is useless. because if w.numOpsInQueue() > 100, the simulation has been canceled in run(). here is a double check
                for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
                    if (w.numOpsInQueue() > 100) {
                        //this was a bad run

                        //fzhang cancel normalized process
//                      normObjValue = Double.MAX_VALUE;
                        Arrays.fill(multiCaseObjValue,Double.MAX_VALUE);
//                        ObjValue = Double.MAX_VALUE;

                        //System.out.println(systemState.getJobsInSystem().size());
                        //System.out.println(systemState.getJobsCompleted().size());

                        //normObjValue = normObjValue*(systemState.getJobsInSystem().size()/systemState.getJobsCompleted().size());
                        countBadrun++;
                        break;
                    }
                }

                //2018.10.23  cancel normalized process
//                fitnesses[i] += normObjValue;  //the value of fitness is the normalization of the objective value
                for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
                    fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
                }

            }
            col++;

            //schedulingSet.getReplications().get(j) = 1, only calculate once, skip this part here
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                simulation.rerun();

                for (int i = 0; i < objectives.size(); i++) {
                    double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan

                    for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
                        fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
                    }
                }

                col++;
            }

            simulation.reset();
        }


        for (int j = 0; j < simulations.size(); j++) {
            for (int indexCase = 0; indexCase < fitnesses.length; indexCase++) {
                for (int i = 0; i < fitnesses[j].length; i++) {
                    fitnesses[indexCase][i] /= schedulingSet.getReplications().get(j);
//                    System.out.print(fitnesses[indexCase][i] + ", ");
                }
            }
        }

//        //check
//        System.out.println("check 1: " + fitnesses.length);
//        for(int i=0; i<fitnesses.length;i++){
//            System.out.print(fitnesses[i][0] + "  ");
//        }
//        System.out.println();

        //System.out.println(currentFitnesses.size()); //1
        for (Fitness fitness: currentFitnesses) {
            OneInstanceMultiCaseMultiObjectiveFitnessLSMO f = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) fitness;
            f.setCurrentObjective(objectives);
            f.setMultiInstanceFitness(state, fitnesses);
        }
    }

    public void evaluate(List<Fitness> currentFitnesses,
                         List<AbstractRule> rules,
                         EvolutionState state,
                         Individual individual) {
        //expecting 2 rules here - one routing rule and one sequencing rule
        if (rules.size() != 2) {
            System.out.println("Rule evaluation failed!");
            System.out.println("Expecting 2 rules, only 1 found.");
            return;
        }

        //System.out.println(rules.size()); //2 repeat
        countInd++;

        AbstractRule sequencingRule = rules.get(0); // for each arraylist in list, they have two elements, the first one is sequencing rule and the second one is routing rule
        AbstractRule routingRule = rules.get(1);

//        double[] fitnesses = new double[objectives.size()];

        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;
        double[][] fitnesses = new double[numCase][objectives.size()];//modified by mengxu


        //System.out.println(simulations.size()); // 1 repeat
        //System.out.println(schedulingSet.getReplications().get(0)); //1 repeat

        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);

            //========================change here======================================
            simulation.setSequencingRule(sequencingRule); //indicate different individuals
            simulation.setRoutingRule(routingRule);
            //System.out.println(simulation);
            simulation.run();

            //wrong!! todo: modify the same address problem!!!
//            ((GPIndividual)individual).setDecisionVector(sequencingVector, routingVector);
            List<Integer> AllVector = simulation.AllDecisions;
            ((GPIndividual)individual).setDecisionVector(AllVector);


            for (int i = 0; i < objectives.size(); i++) {
                //2018.10.23  cancel normalized process
//                double normObjValue = simulation.objectiveValue(objectives.get(i))  // this line: the value of makespan
//                        / schedulingSet.getObjectiveLowerBound(i, col);

                double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan

                //modified by mengxu 2022.01.06
//                if(state instanceof GPRuleEvolutionStateDPS){ //add 2021.12.23
//                    if(((GPRuleEvolutionStateDPS) state).isUseDPSSelection()){
//                        if(((GPRuleEvolutionStateDPS) state).allTargetObjectiveList.size() == state.generation){
//                            //have not add target for this generation
//                            double[] target = simulation.estimateTargetValue(objectives.get(i),numCase);
//                            ((GPRuleEvolutionStateDPS) state).allTargetObjectiveList.add(target);
//                        }
//                    }
//                }

                //in essence, here is useless. because if w.numOpsInQueue() > 100, the simulation has been canceled in run(). here is a double check
                for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
                    if (w.numOpsInQueue() > 100) {
                        //this was a bad run

                        //fzhang cancel normalized process
//                      normObjValue = Double.MAX_VALUE;
                        Arrays.fill(multiCaseObjValue,Double.MAX_VALUE);
//                        ObjValue = Double.MAX_VALUE;

                        //System.out.println(systemState.getJobsInSystem().size());
                        //System.out.println(systemState.getJobsCompleted().size());

                        //normObjValue = normObjValue*(systemState.getJobsInSystem().size()/systemState.getJobsCompleted().size());
                        countBadrun++;
                        break;
                    }
                }

                //2018.10.23  cancel normalized process
//                fitnesses[i] += normObjValue;  //the value of fitness is the normalization of the objective value
                for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
                    fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
                }

            }
            col++;

            //schedulingSet.getReplications().get(j) = 1, only calculate once, skip this part here
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                simulation.rerun();

                for (int i = 0; i < objectives.size(); i++) {
                    double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan

                    for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
                        fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
                    }
                }

                col++;
            }

            simulation.reset();
        }


        for (int j = 0; j < simulations.size(); j++) {
            for (int indexCase = 0; indexCase < fitnesses.length; indexCase++) {
                for (int i = 0; i < fitnesses[j].length; i++) {
                    fitnesses[indexCase][i] /= schedulingSet.getReplications().get(j);
//                    System.out.print(fitnesses[indexCase][i] + ", ");
                }
            }
        }

//        //check
//        System.out.println("check 1: " + fitnesses.length);
//        for(int i=0; i<fitnesses.length;i++){
//            System.out.print(fitnesses[i][0] + "  ");
//        }
//        System.out.println();

        //System.out.println(currentFitnesses.size()); //1
        for (Fitness fitness: currentFitnesses) {
            OneInstanceMultiCaseMultiObjectiveFitnessLSMO f = (OneInstanceMultiCaseMultiObjectiveFitnessLSMO) fitness;
            f.setCurrentObjective(objectives);
            f.setMultiInstanceFitness(state, fitnesses);
        }
    }

}
