package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import mengxu.algorithm.MAPElites.FuzzySystem.TreeComplexityCalculator;
import yimei.jss.rule.operation.evolved.GPRule;

public final class RevisionMetrics {
    private final ArchiveCandidate candidate;
    private final double validationObjective;
    private final double complexity;
    private final double sensitivity;

    public RevisionMetrics(ArchiveCandidate candidate, double validationObjective,
                           double complexity, double sensitivity) {
        if (candidate == null || !Double.isFinite(validationObjective) || validationObjective < 0
                || !Double.isFinite(complexity) || complexity < 0
                || !Double.isFinite(sensitivity) || sensitivity < 0) {
            throw new IllegalArgumentException("Candidate metrics must be finite and non-negative");
        }
        this.candidate = candidate;
        this.validationObjective = validationObjective;
        this.complexity = complexity;
        this.sensitivity = sensitivity;
    }

    public ArchiveCandidate candidate() { return candidate; }
    public double validationObjective() { return validationObjective; }
    public double complexity() { return complexity; }
    public double sensitivity() { return sensitivity; }

    public static RevisionMetrics compute(ArchiveCandidate candidate, SensitivityProbeSet probes,
                                          double epsilon, double validationObjective) {
        GPRule[] rules = candidate.rules();
        TreeComplexityCalculator calculator = new TreeComplexityCalculator();
        double complexity = calculator.calculateStructuralComplexity(rules[0].getGPTree().child)
                + calculator.calculateStructuralComplexity(rules[1].getGPTree().child);
        double sensitivity = new OutputSensitivityCalculator(epsilon).calculate(rules, probes);
        return new RevisionMetrics(candidate, validationObjective, complexity, sensitivity);
    }
}
