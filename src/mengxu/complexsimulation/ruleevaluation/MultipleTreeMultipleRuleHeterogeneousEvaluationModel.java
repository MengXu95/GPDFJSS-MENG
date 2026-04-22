package mengxu.complexsimulation.ruleevaluation;

import ec.EvolutionState;
import ec.util.Parameter;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.FlexibleStaticInstance;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.MultipleTreeMultipleRuleEvaluationModel;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.StaticSimulation;

import java.util.ArrayList;
import java.util.List;

public class MultipleTreeMultipleRuleHeterogeneousEvaluationModel extends MultipleTreeMultipleRuleEvaluationModel {
    @Override
    public void setup(final ec.EvolutionState state, final Parameter base) {
//        super.setup(state, base);

        // Get the objectives.
        objectives = new ArrayList<>();
        Parameter p = base.push(P_OBJECTIVES);
        int numObjectives = state.parameters.getIntWithDefault(p, null, 0);

        if (numObjectives == 0) {
            System.err.println("ERROR:");
            System.err.println("No objective is specified.");
            System.exit(1);
        }

        for (int i = 0; i < numObjectives; i++) {
            p = base.push(P_OBJECTIVES).push("" + i);
            String objectiveName = state.parameters.getStringWithDefault(p, null, "");
            Objective objective = Objective.get(objectiveName);

            objectives.add(objective);
        }

        // Get the seed for the simulation.
//        Parameter p = base.push(P_SIM_SEED);
        p = base.push(P_SIM_SEED);
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
                //Dynamic Heterogeneous Simulation
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
        }

        schedulingSet = new SchedulingSet(trainSimulations, replications, objectives);

        p = base.push(P_ROTATE_SIM_SEED);
        rotateSimSeed = state.parameters.getBoolean(p, null, false);
    }
}
