package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.util.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.OneInstanceEvaluation;
import mengxu.complexsimulation.HeterogeneousSimulation;
import mengxu.complexsimulation.ruleevaluation.MultipleTreeMultipleRuleHeterogeneousEvaluationModel;
import yimei.jss.jobshop.Objective;
import yimei.jss.rule.AbstractRule;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

/** Reset adapter; fitness assignment, cleanup and native QD evaluation phases remain inherited. */
public final class EGridEvaluation extends MultipleTreeMultipleRuleHeterogeneousEvaluationModel {
    public static final class EvaluationRecord {
        public final long seed;
        public final int generation;
        public final double raw;
        private final double[] cases;
        EvaluationRecord(long seed, int generation, double raw, double[] cases) {
            this.seed = seed; this.generation = generation; this.raw = raw; this.cases = cases.clone();
        }
        public double[] cases() { return cases.clone(); }
    }

    private final Map<Fitness, EvaluationRecord> diagnostics = new WeakHashMap<>();
    private int cases, recorded, warmup;
    private long fitnessCalls, trainingRuns, referenceRuns, failures, caseSlots;
    private boolean evaluating;
    private EvaluationRecord observed;
    private int evaluatingGeneration;

    @Override public void setup(EvolutionState state, Parameter base) {
        if (state.parameters.getIntWithDefault(base.push("objectives"), null, 0) != 1
                || state.parameters.getIntWithDefault(base.push("sim-models"), null, 0) != 1
                || state.parameters.exists(new Parameter("filePath"), null)) {
            throw new IllegalArgumentException("One dynamic objective and training simulation are required");
        }
        super.setup(state, base);
        cases = EAdapterSupport.integer(state, "num-case");
        Parameter model = base.push("sim-models.0");
        recorded = state.parameters.getIntWithDefault(model.push("num-jobs"), null, 1000);
        warmup = state.parameters.getIntWithDefault(model.push("warmup-jobs"), null, 500);
        if (cases < 2 || recorded < cases || recorded % cases != 0 || warmup < 0
                || schedulingSet.getReplications().get(0) != 1
                || EAdapterSupport.seed(state, "e.seed-step") != 10000) {
            throw new IllegalArgumentException("Complete equal job-ID blocks, one replication and native seed-step=10000 required");
        }
        Objective objective = objectives.get(0);
        if (objective != Objective.MEAN_TARDINESS && objective != Objective.MAX_FLOWTIME
                && objective != Objective.MEAN_WEIGHTED_FLOWTIME) {
            throw new IllegalArgumentException("Unsupported study objective");
        }
        // Same constructor and seed as the native model; observation adds no RNG draws or simulations.
        schedulingSet.getSimulations().set(0, new ObservedSimulation(simSeed,
                state.parameters.getIntWithDefault(model.push("num-machines"), null, 10), recorded, warmup,
                state.parameters.getIntWithDefault(model.push("min-num-operations"), null, 1),
                state.parameters.getIntWithDefault(model.push("max-num-operations"), null, 10),
                state.parameters.getDoubleWithDefault(model.push("util-level"), null, .85),
                state.parameters.getDoubleWithDefault(model.push("due-date-factor"), null, 1.5)));
    }

    @Override public void evaluate(List<Fitness> fitnesses, List<AbstractRule> rules, EvolutionState state) {
        evaluateControlled(fitnesses, rules, state, null);
    }
    @Override public void evaluate(List<Fitness> fitnesses, List<AbstractRule> rules,
                                   EvolutionState state, Individual individual) {
        evaluateControlled(fitnesses, rules, state, individual);
    }
    private void evaluateControlled(List<Fitness> fitnesses, List<AbstractRule> rules,
                                    EvolutionState state, Individual individual) {
        if (fitnesses.size() != 1 || rules.size() != 2 || evaluating) {
            throw new IllegalArgumentException("One two-tree individual and nonnested evaluation required");
        }
        for (Simulation simulation : schedulingSet.getSimulations()) {
            // reset() generates the first job, so install this individual's rules before resetting.
            simulation.setSequencingRule(rules.get(0));
            simulation.setRoutingRule(rules.get(1));
            DynamicSimulation dynamic = (DynamicSimulation) simulation;
            dynamic.reset(dynamic.getSeed());
        }
        observed = null;
        evaluatingGeneration = state.generation;
        evaluating = true;
        fitnessCalls++;
        try {
            if (individual == null) { super.evaluate(fitnesses, rules, state); }
            else { super.evaluate(fitnesses, rules, state, individual); }
            if (observed == null) { throw new IllegalStateException("Native evaluator did not run the training simulation"); }
            double nativeRaw = fitnesses.get(0).fitness();
            if (EAdapterSupport.finite(observed.raw) != EAdapterSupport.finite(nativeRaw)
                    || (EAdapterSupport.finite(nativeRaw) && Double.compare(nativeRaw, observed.raw) != 0)) {
                throw new IllegalStateException("Native raw fitness differs from observed simulation objective");
            }
            diagnostics.put(fitnesses.get(0), observed);
            caseSlots = Math.addExact(caseSlots, observed.cases.length);
            if (!EAdapterSupport.finite(observed.raw)) { failures++; }
        } finally { evaluating = false; observed = null; }
    }

    public EvaluationRecord record(Individual individual) {
        EvaluationRecord record = diagnostics.get(individual.fitness);
        if (record == null) { throw new IllegalStateException("Missing diagnostics for a freshly evaluated individual"); }
        return record;
    }
    public long trainingSeed() {
        return ((DynamicSimulation) schedulingSet.getSimulations().get(0)).getSeed();
    }
    public long fitnessCalls() { return fitnessCalls; }
    public long simulationCount() { return trainingRuns; }
    public long referenceSimulationCount() { return referenceRuns; }
    public long failedCount() { return failures; }
    public long caseSlots() { return caseSlots; }
    public int caseCount() { return cases; }

    private final class ObservedSimulation extends HeterogeneousSimulation {
        ObservedSimulation(long seed, int machines, int jobs, int warmupJobs,
                           int minOperations, int maxOperations, double utilisation, double dueFactor) {
            super(seed, null, null, machines, jobs, warmupJobs, minOperations, maxOperations,
                    utilisation, dueFactor, false);
        }
        @Override public void run() {
            if (evaluating) {
                // The native delegate's setRoutingRule() calls resetState() without reseeding.
                // Reseed after that setter, immediately before running, to match OneInstanceEvaluation.
                reset(getSeed());
                trainingRuns++;
            } else { referenceRuns++; }
            super.run();
            if (!evaluating) { return; }
            double raw = objectiveValue(objectives.get(0));
            double[] values;
            if (getThroughput() != recorded || !EAdapterSupport.finite(raw)) {
                if (EAdapterSupport.finite(raw)) {
                    throw new IllegalStateException("Native evaluator returned finite fitness for an incomplete simulation");
                }
                values = new double[cases];
                Arrays.fill(values, Double.POSITIVE_INFINITY);
            } else {
                values = OneInstanceEvaluation.caseValues(getSystemState().getJobsCompleted(),
                        warmup, recorded, cases, objectives.get(0));
                double aggregated = OneInstanceEvaluation.aggregate(values, objectives.get(0));
                if (Math.abs(raw - aggregated) > 1e-9 * Math.max(1, Math.abs(raw))) {
                    throw new IllegalStateException("QD diagnostic cases do not aggregate to native raw fitness");
                }
            }
            observed = new EvaluationRecord(getSeed(), evaluatingGeneration, raw, values);
        }
    }
}
