package mengxu.algorithm.lexicaseselection.LSmultiReplication;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.util.Parameter;
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

public class MultiReplicationMultipleTreeMultipleRuleEvaluationModel extends MultipleTreeMultipleRuleEvaluationModel {

    public final static int SEED_ROTATION = 100;//modified by mengxu

//    public static final String P_NUMINSTANCE = "num-instance";
//    public int numInstance;
    public int replications;

    @Override
    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        // Get the seed for the simulation.
        Parameter p = base.push(P_SIM_SEED);
        simSeed = state.parameters.getLongWithDefault(p, null, 0);

//        numInstance = state.parameters.getInt(new Parameter(P_NUMINSTANCE), null, 0);
//        if (numInstance <= 0)
//            state.output.fatal("The number of instances must be an integer >= 1.");

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
        for (int x = 0; x < numSimModels; x++) {//modified by mengxu
//        for (int x = 0; x < numInstance; x++) {
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
            //todo: need to modify this parameter initialization in .params
            p = b.push(P_SIM_UTIL_LEVEL);
            double utilLevel = state.parameters.getDoubleWithDefault(p, null, 0.85);
            // Due date factor
            p = b.push(P_SIM_DUE_DATE_FACTOR);
            double dueDateFactor = state.parameters.getDoubleWithDefault(p, null, 4.0);
            // Number of replications
            p = b.push(P_SIM_REPLICATIONS);
            int rep = state.parameters.getIntWithDefault(p, null, 1);
            this.replications = rep;

            Simulation simulation = null;
            //only expecting filePath parameter for Static FJSS, so can use this
            String filePath = state.parameters.getString(new Parameter("filePath"), null);
            if (filePath == null) {
                //Dynamic Simulation
                simulation = new HeterogeneousSimulation(simSeed,
                        null, null, numMachines, numJobs, warmupJobs,
                        minNumOperations, maxNumOperations,
                        utilLevel, dueDateFactor, false);
            } else {
                FlexibleStaticInstance instance = FlexibleStaticInstance.readFromAbsPath(filePath);
                simulation = new StaticSimulation(null, null, instance);
            }
            trainSimulations.add(simulation);
            replications.add(new Integer(rep));
            //modified by mengxu rotate simSeed
            this.simSeed = this.simSeed + this.SEED_ROTATION;
        }

        schedulingSet = new SchedulingSet(trainSimulations, replications, objectives);

        p = base.push(P_ROTATE_SIM_SEED);
        rotateSimSeed = state.parameters.getBoolean(p, null, false);


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
        double[][] fitnesses = new double[this.replications][objectives.size()];//modified by mengxu

        double[] multiCaseObjValue = new double[this.replications];

        //System.out.println(simulations.size()); // 1 repeat
        //System.out.println(schedulingSet.getReplications().get(0)); //1 repeat

        boolean badTrue = false;

        //System.out.println(simulations.size()); // 1 repeat
        //System.out.println(schedulingSet.getReplications().get(0)); //1 repeat

        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);

            //========================change here======================================
            simulation.setSequencingRule(sequencingRule); //indicate different individuals
            simulation.setRoutingRule(routingRule);
            //System.out.println(simulation);
            simulation.run();

            for (int i = 0; i < objectives.size(); i++) {
                //2018.10.23  cancel normalized process
//                double normObjValue = simulation.objectiveValue(objectives.get(i))  // this line: the value of makespan
//                        / schedulingSet.getObjectiveLowerBound(i, col);

                double ObjValue = simulation.objectiveValue(objectives.get(i)); // this line: the value of makespan

                System.out.println("Job arrive number: " + simulation.getNumJobsArrived());

                //in essence, here is useless. because if w.numOpsInQueue() > 100, the simulation has been canceled in run(). here is a double check
                for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
                    if (w.numOpsInQueue() > 100) {
                        //this was a bad run

                        //fzhang cancel normalized process
//                      normObjValue = Double.MAX_VALUE;
                        ObjValue = Double.MAX_VALUE;

                        //System.out.println(systemState.getJobsInSystem().size());
                        //System.out.println(systemState.getJobsCompleted().size());

                        //normObjValue = normObjValue*(systemState.getJobsInSystem().size()/systemState.getJobsCompleted().size());
                        countBadrun++;
                        break;
                    }
                }

                multiCaseObjValue[j] = ObjValue;//add 2022.03.09
                if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
                    badTrue = true;
//                    System.out.println("bad run");
                }

                //2018.10.23  cancel normalized process
//                fitnesses[i] += normObjValue;  //the value of fitness is the normalization of the objective value
                fitnesses[j][i] += ObjValue;
            }
            col++;

            //schedulingSet.getReplications().get(j) = 1, only calculate once, skip this part here
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                simulation.rerun();

                for (int i = 0; i < objectives.size(); i++) {
                    double ObjValue = simulation.objectiveValue(objectives.get(i));
//                    System.out.println("Job arrive number: " + simulation.getNumJobsArrived());
                    if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
                        badTrue = true;
//                        System.out.println("bad run");
                    }
                    multiCaseObjValue[k] = ObjValue;//add 2022.03.09
                    fitnesses[j][i] += ObjValue;
                }

                col++;
            }

            simulation.reset();
        }


        for(int j=0;j<fitnesses.length;j++){
            for (int i = 0; i < fitnesses[j].length; i++) {
                fitnesses[j][i] /= schedulingSet.getReplications().get(j);
            }
        }

        if(badTrue){
            for(int j=0;j<fitnesses.length;j++){
                Arrays.fill(fitnesses[j], Double.MAX_VALUE);
            }
        }

        //System.out.println(currentFitnesses.size()); //1
        for (Fitness fitness: currentFitnesses) {
            MultiReplicationMultiObjectiveFitness f = (MultiReplicationMultiObjectiveFitness) fitness;
            f.setMultiInstanceFitness(state, fitnesses);
            f.setCurrentObjective(objectives);
        }
    }

    public void evaluate(List<Fitness> currentFitnesses,
                         List<AbstractRule> rules,
                         ec.EvolutionState state,
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
        double[][] fitnesses = new double[this.replications][objectives.size()];//modified by mengxu

        double[] multiCaseObjValue = new double[this.replications];

        //System.out.println(simulations.size()); // 1 repeat
        //System.out.println(schedulingSet.getReplications().get(0)); //1 repeat

        boolean badTrue = false;

//        System.out.println("One simulation ===================================");

        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);

            //========================change here======================================
            simulation.setSequencingRule(sequencingRule); //indicate different individuals
            simulation.setRoutingRule(routingRule);
            //System.out.println(simulation);
            simulation.run();

//            List<Integer> AllVector = simulation.AllDecisions;
//            ((GPIndividual)individual).setDecisionVector(AllVector);

            for (int i = 0; i < objectives.size(); i++) {
                //2018.10.23  cancel normalized process
//                double normObjValue = simulation.objectiveValue(objectives.get(i))  // this line: the value of makespan
//                        / schedulingSet.getObjectiveLowerBound(i, col);

                double ObjValue = simulation.objectiveValue(objectives.get(i)); // this line: the value of makespan

//                System.out.println("Job arrive number: " + simulation.getNumJobsArrived());

                //in essence, here is useless. because if w.numOpsInQueue() > 100, the simulation has been canceled in run(). here is a double check
                for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
                    if (w.numOpsInQueue() > 100) {
                        //this was a bad run

                        //fzhang cancel normalized process
//                      normObjValue = Double.MAX_VALUE;
                        ObjValue = Double.MAX_VALUE;

                        //System.out.println(systemState.getJobsInSystem().size());
                        //System.out.println(systemState.getJobsCompleted().size());

                        //normObjValue = normObjValue*(systemState.getJobsInSystem().size()/systemState.getJobsCompleted().size());
                        countBadrun++;
                        break;
                    }
                }

                multiCaseObjValue[j] = ObjValue;//add 2022.03.09
                if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
                    badTrue = true;
//                    System.out.println("bad run");
                }

                //2018.10.23  cancel normalized process
//                fitnesses[i] += normObjValue;  //the value of fitness is the normalization of the objective value
                fitnesses[j][i] += ObjValue;
            }
            col++;

            //schedulingSet.getReplications().get(j) = 1, only calculate once, skip this part here
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                simulation.rerun();

                for (int i = 0; i < objectives.size(); i++) {
                    double ObjValue = simulation.objectiveValue(objectives.get(i));
//                    System.out.println("Job arrive number: " + simulation.getNumJobsArrived());
                    if(ObjValue >= Double.POSITIVE_INFINITY || ObjValue >= Double.MAX_VALUE){
                        badTrue = true;
//                        System.out.println("bad run");
                    }
                    multiCaseObjValue[k] = ObjValue;//add 2022.03.09
                    fitnesses[k][i] += ObjValue;
                }

                col++;
            }

            simulation.reset();
        }


        if(badTrue){
            for(int j=0;j<fitnesses.length;j++){
                Arrays.fill(fitnesses[j], Double.MAX_VALUE);
            }
        }

        //System.out.println(currentFitnesses.size()); //1
        for (Fitness fitness: currentFitnesses) {
            MultiReplicationMultiObjectiveFitness f = (MultiReplicationMultiObjectiveFitness) fitness;
            f.setMultiInstanceFitness(state, fitnesses);
            f.setCurrentObjective(objectives);
        }
    }
}
