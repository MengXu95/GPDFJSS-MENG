package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.ArrayList;
import java.util.List;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.OutputSensitivityCalculator;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.SensitivityProbeSet;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.operation.evolved.GPRule;

/**
 * Paper-v2 priority-output sensitivity. Historical phenotype-matrix scores are a
 * different metric and must not be mixed with the values produced here.
 */
public class TreeBehavioralSensitivityCalculator {
    public PhenoCharacterisation[] phenoCharacterisation;
    private SensitivityProbeSet probes;
    private final double epsilon;

    public TreeBehavioralSensitivityCalculator() {
        this(1.0e-3);
    }

    public TreeBehavioralSensitivityCalculator(double epsilon) {
        new OutputSensitivityCalculator(epsilon);
        this.epsilon = epsilon;
    }

    public void setup() {
        probes = SensitivityProbeSet.collect(777, 8295342, 10, 1000, 500,
                0.85, 1.5, 3, 20);
    }

    public List<Double> calculateSensitivityCalculator(List<AbstractRule[]> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("A non-empty rule library is required");
        }
        SensitivityProbeSet current = phenoCharacterisation == null ? probes
                : SensitivityProbeSet.fromCharacterisations(phenoCharacterisation);
        if (current == null) {
            throw new IllegalStateException("Call setup() or supply frozen characterisations first");
        }
        OutputSensitivityCalculator calculator = new OutputSensitivityCalculator(epsilon);
        List<Double> values = new ArrayList<>();
        for (AbstractRule[] pair : rules) {
            if (pair == null || pair.length != 2 || !(pair[0] instanceof GPRule)
                    || !(pair[1] instanceof GPRule)) {
                throw new IllegalArgumentException("Sensitivity requires two GP rules per candidate");
            }
            values.add(calculator.calculate(new GPRule[]{(GPRule) pair[0], (GPRule) pair[1]}, current));
        }
        return values;
    }
}
