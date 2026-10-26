package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.util.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.Job;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.AbstractRule;
import yimei.jss.ruleevaluation.MultipleTreeMultipleRuleEvaluationModel;

public final class OneInstanceEvaluation extends MultipleTreeMultipleRuleEvaluationModel {
    private HeterogeneousSimulation simulation;
    private Objective objective;
    private int cases;
    private int recordedJobs;
    private int warmupJobs;
    private long currentSeed;
    private long seedStep;
    private long simulations;
    private long failures;

    @Override public void setup(EvolutionState state, Parameter base) {
        if (state.parameters.getIntWithDefault(base.push("objectives"), null, 0) != 1
                || state.parameters.getIntWithDefault(base.push("sim-models"), null, 0) != 1) {
            throw new IllegalArgumentException("One objective and one simulation instance are required");
        }
        objective = Objective.get(state.parameters.getString(base.push("objectives.0"), null));
        if (objective != Objective.MEAN_TARDINESS && objective != Objective.MAX_FLOWTIME
                && objective != Objective.MEAN_WEIGHTED_FLOWTIME) {
            throw new IllegalArgumentException("Unsupported comparison objective");
        }
        objectives = Collections.singletonList(objective);
        Parameter model = base.push("sim-models.0");
        int machines = state.parameters.getIntWithDefault(model.push("num-machines"), null, 10);
        recordedJobs = state.parameters.getIntWithDefault(model.push("num-jobs"), null, 1000);
        warmupJobs = state.parameters.getIntWithDefault(model.push("warmup-jobs"), null, 500);
        cases = state.parameters.getIntWithDefault(new Parameter("num-case"), null, 25);
        int minimumOperations = state.parameters.getIntWithDefault(model.push("min-num-operations"), null, 1);
        int maximumOperations = state.parameters.getIntWithDefault(model.push("max-num-operations"), null, machines);
        double utilisation = state.parameters.getDoubleWithDefault(model.push("util-level"), null, 0.85);
        double dueFactor = state.parameters.getDoubleWithDefault(model.push("due-date-factor"), null, 1.5);
        int replications = state.parameters.getIntWithDefault(model.push("replications"), null, 1);
        if (machines < 2 || cases < 2 || recordedJobs < cases || recordedJobs % cases != 0
                || warmupJobs < 0 || replications != 1 || minimumOperations < 1
                || maximumOperations < minimumOperations || maximumOperations > machines
                || !Double.isFinite(utilisation) || utilisation <= 0 || utilisation >= 1
                || !Double.isFinite(dueFactor) || dueFactor <= 0) {
            throw new IllegalArgumentException("Invalid simulation/case configuration; recorded jobs must divide into equal cases");
        }
        simSeed = state.parameters.getLongWithDefault(base.push("sim-seed"), null, 0);
        currentSeed = simSeed;
        rotateSimSeed = state.parameters.getBoolean(base.push("rotate-sim-seed"), null, false);
        seedStep = state.parameters.getLongWithDefault(new Parameter("lexqd.seed-step"), null, 10000);
        if (seedStep <= 0) { throw new IllegalArgumentException("lexqd.seed-step must be positive"); }
        simulation = new HeterogeneousSimulation(simSeed, null, null, machines, recordedJobs,
                warmupJobs, minimumOperations, maximumOperations, utilisation, dueFactor, false);
        schedulingSet = new SchedulingSet(Collections.singletonList(simulation),
                Collections.singletonList(1), objectives);
    }

    @Override public void evaluate(List<Fitness> fitnesses, List<AbstractRule> rules, EvolutionState state) {
        if (rules.size() != 2 || fitnesses.size() != 1 || !(fitnesses.get(0) instanceof LexicaseFitness)) {
            throw new IllegalArgumentException("One two-tree Lexicase individual is required");
        }
        simulation.setSequencingRule(rules.get(0));
        simulation.setRoutingRule(rules.get(1));
        // Reset before every individual, including the first: shared shop, identical job stream within a batch.
        simulation.reset(currentSeed);
        simulations++;
        countInd++;
        simulation.run();
        double raw = simulation.objectiveValue(objective);
        double[] values;
        if (simulation.getThroughput() != recordedJobs || !Double.isFinite(raw) || raw >= Double.MAX_VALUE) {
            values = new double[cases];
            Arrays.fill(values, Double.POSITIVE_INFINITY);
            raw = Double.POSITIVE_INFINITY;
            failures++;
            countBadrun++;
        } else {
            values = caseValues(simulation.getSystemState().getJobsCompleted(),
                    warmupJobs, recordedJobs, cases, objective);
            double combined = aggregate(values, objective);
            if (Math.abs(raw - combined) > 1e-9 * Math.max(1.0, Math.abs(raw))) {
                throw new IllegalStateException("Case aggregation disagrees with the whole-instance objective");
            }
        }
        ((LexicaseFitness) fitnesses.get(0)).setEvaluation(raw, values, objective, state.generation, currentSeed);
    }

    @Override public void evaluate(List<Fitness> fitnesses, List<AbstractRule> rules,
                                   EvolutionState state, Individual individual) {
        evaluate(fitnesses, rules, state);
    }

    public static double[] caseValues(List<Job> jobs, int warmup, int recorded, int count, Objective objective) {
        if (count < 1 || recorded < count || recorded % count != 0 || warmup < 0 || jobs.size() != recorded) {
            throw new IllegalArgumentException("Complete, equally sized job-ID cases are required");
        }
        if (objective != Objective.MAX_FLOWTIME && objective != Objective.MEAN_TARDINESS
                && objective != Objective.MEAN_WEIGHTED_FLOWTIME) {
            throw new IllegalArgumentException("Unsupported case objective");
        }
        double[] values = new double[count];
        boolean[] seen = new boolean[recorded];
        int jobsPerCase = recorded / count;
        for (Job job : jobs) {
            if (job == null) { throw new IllegalArgumentException("Missing recorded job"); }
            int id = job.getId() - warmup;
            if (id < 0 || id >= recorded || seen[id]) {
                throw new IllegalArgumentException("Unexpected or duplicate recorded job ID: " + job.getId());
            }
            seen[id] = true;
            double value = objective == Objective.MAX_FLOWTIME ? job.flowTime()
                    : objective == Objective.MEAN_TARDINESS ? job.tardiness() : job.weightedFlowTime();
            if (!Double.isFinite(value) || value < 0) { throw new IllegalArgumentException("Invalid completed-job objective"); }
            int index = id / jobsPerCase;
            if (objective == Objective.MAX_FLOWTIME) { values[index] = Math.max(values[index], value); }
            else { values[index] += value / jobsPerCase; }
        }
        return values;
    }

    public static double aggregate(double[] values, Objective objective) {
        if (values == null || values.length == 0) { throw new IllegalArgumentException("No cases"); }
        if (objective != Objective.MEAN_TARDINESS && objective != Objective.MEAN_WEIGHTED_FLOWTIME
                && objective != Objective.MAX_FLOWTIME) { throw new IllegalArgumentException("Unsupported case objective"); }
        if (objective == Objective.MAX_FLOWTIME) { return Arrays.stream(values).max().getAsDouble(); }
        return Arrays.stream(values).average().getAsDouble();
    }

    @Override public boolean isRotatable() { return rotateSimSeed; }
    @Override public void rotate() {
        if (rotateSimSeed) { currentSeed = Math.addExact(currentSeed, seedStep); }
    }
    @Override public void normObjective(List<Fitness> fitnesses, List<AbstractRule> rules, EvolutionState state) {
        throw new UnsupportedOperationException("Lexicase uses raw case objectives, not cross-batch normalization");
    }
    public long trainingSeed() { return currentSeed; }
    public long simulationCount() { return simulations; }
    public long failedCount() { return failures; }
    public int caseCount() { return cases; }
}
