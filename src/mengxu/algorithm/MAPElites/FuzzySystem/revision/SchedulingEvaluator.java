package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.Objective;
import yimei.jss.rule.operation.evolved.GPRule;

public final class SchedulingEvaluator {
    public static final class Evaluation {
        private final List<Double> values;
        private final double mean;
        private final List<String> failures;

        Evaluation(List<Double> values, double mean) {
            this(values, mean, Collections.nCopies(values.size(), ""));
        }

        Evaluation(List<Double> values, double mean, List<String> failures) {
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
            this.mean = mean;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        public List<Double> values() { return values; }
        public double mean() { return mean; }
        public List<String> failures() { return failures; }
        public long failedCount() { return failures.stream().filter(value -> !value.isEmpty()).count(); }
    }

    private final int machines;
    private final int jobs;
    private final int warmup;
    private final double utilisation;
    private final double dueFactor;
    private final Objective objective;
    private long simulationCount;

    public SchedulingEvaluator(int machines, int jobs, int warmup, double utilisation,
                               double dueFactor, Objective objective) {
        if (machines < 1 || jobs < 1 || warmup < 0 || !Double.isFinite(utilisation)
                || utilisation <= 0 || utilisation >= 1 || !Double.isFinite(dueFactor)
                || dueFactor <= 0 || objective == null) {
            throw new IllegalArgumentException("Invalid scheduling-evaluation configuration");
        }
        this.machines = machines;
        this.jobs = jobs;
        this.warmup = warmup;
        this.utilisation = utilisation;
        this.dueFactor = dueFactor;
        this.objective = objective;
    }

    public Evaluation evaluate(ArchiveCandidate candidate, long firstSeed, int instances) {
        return evaluate(candidate, firstSeed, instances, true);
    }

    public Evaluation evaluateRecordingFailures(ArchiveCandidate candidate, long firstSeed, int instances) {
        return evaluate(candidate, firstSeed, instances, false);
    }

    private Evaluation evaluate(ArchiveCandidate candidate, long firstSeed, int instances, boolean failFast) {
        if (candidate == null || instances < 1) {
            throw new IllegalArgumentException("A candidate and positive instance count are required");
        }
        List<Double> values = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        double mean = 0;
        for (int i = 0; i < instances; i++) {
            long seed = Math.addExact(firstSeed, i);
            GPRule[] rules = candidate.rules();
            HeterogeneousSimulation simulation = HeterogeneousSimulation.standardMissing(seed,
                    rules[0], rules[1], machines, jobs, warmup, utilisation, dueFactor);
            simulationCount++;
            simulation.rerun();
            double value = simulation.objectiveValue(objective);
            String failure = "";
            if (!Double.isFinite(value) || value < 0 || value == Double.MAX_VALUE) {
                failure = "Failed scheduling evaluation: candidate="
                        + candidate.id() + ", seed=" + seed + ", objective=" + value;
                if (failFast) { throw new IllegalStateException(failure); }
                value = Double.POSITIVE_INFINITY;
            }
            values.add(value);
            failures.add(failure);
            if (Double.isFinite(value) && Double.isFinite(mean)) { mean += (value - mean) / (i + 1); }
            else { mean = Double.POSITIVE_INFINITY; }
        }
        return new Evaluation(values, mean, failures);
    }

    public long simulationCount() { return simulationCount; }
}
