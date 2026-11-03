package mengxu.algorithm.MAPElites.FuzzySystem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordered Gaussian-term utility with grades (0, 0.5, 1), not membership in a target term.
 * Strictly ordered centers and equal widths make its exact-arithmetic value strictly
 * increasing on [0,1]. Endpoint values are Gaussian ratios, not forced to 0 or 1.
 */
public final class MonotoneFuzzyUtility {
    private static final double[] GRADES = {0.0, 0.5, 1.0};
    private final double[] centers = new double[GRADES.length];
    private final double sigma;

    /**
     * Snapshot three terms in worst-to-best order from a normalized Gaussian variable.
     * The paper model uses centers (0.2, 0.5, 0.8) and common sigma 0.15.
     */
    public MonotoneFuzzyUtility(FuzzyVariable variable, String... orderedTerms) {
        if (variable == null || orderedTerms == null || orderedTerms.length != GRADES.length) {
            throw new IllegalArgumentException("A variable and three ordered terms are required");
        }
        if (variable.getMinValue() != 0.0 || variable.getMaxValue() != 1.0) {
            throw new IllegalArgumentException("Importance utility requires the [0,1] domain");
        }
        List<FuzzyMembershipFunction> functions = variable.getMembershipFunctions();
        if (functions.size() != GRADES.length) {
            throw new IllegalArgumentException("Importance utility requires exactly three Gaussian terms");
        }
        Set<String> seen = new HashSet<>();
        double commonSigma = Double.NaN;
        for (int i = 0; i < orderedTerms.length; i++) {
            if (orderedTerms[i] == null || !seen.add(orderedTerms[i])) {
                throw new IllegalArgumentException("Ordered terms must be nonnull and distinct");
            }
            FuzzyMembershipFunction found = null;
            for (FuzzyMembershipFunction function : functions) {
                if (orderedTerms[i].equals(function.getLinguisticTerm())) {
                    found = function;
                    break;
                }
            }
            if (found == null) {
                throw new IllegalArgumentException("Missing ordered Gaussian term: " + orderedTerms[i]);
            }
            centers[i] = found.getCenter();
            if (!Double.isFinite(centers[i]) || centers[i] < 0.0 || centers[i] > 1.0
                    || (i > 0 && centers[i] <= centers[i - 1])) {
                throw new IllegalArgumentException("Gaussian centers must be strictly ordered in [0,1]");
            }
            if (i == 0) {
                commonSigma = found.getSigma();
            } else if (Double.compare(commonSigma, found.getSigma()) != 0) {
                throw new IllegalArgumentException("Importance utility requires equal Gaussian widths");
            }
        }
        double variance = commonSigma * commonSigma;
        if (!Double.isFinite(commonSigma) || commonSigma <= 0.0
                || !Double.isFinite(variance) || variance <= 0.0
                || !Double.isFinite(1.0 / variance)) {
            throw new IllegalArgumentException("Gaussian width is not numerically representable");
        }
        sigma = commonSigma;
        // Guard the entire domain, not only the values in the current library.
        for (double center : centers) {
            logMembership(0.0, center);
            logMembership(1.0, center);
        }
    }

    /** Evaluate sum(a_l * mu_l(z)) / sum(mu_l(z)) by Gaussian log-sum-exp. */
    public double evaluate(double desirability) {
        if (!Double.isFinite(desirability) || desirability < 0.0 || desirability > 1.0) {
            throw new IllegalArgumentException("Desirability must be finite and in [0,1]");
        }
        double[] logs = new double[GRADES.length];
        double maximum = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < logs.length; i++) {
            logs[i] = logMembership(desirability, centers[i]);
            maximum = Math.max(maximum, logs[i]);
        }
        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 0; i < logs.length; i++) {
            double relativeMembership = Math.exp(logs[i] - maximum);
            numerator += GRADES[i] * relativeMembership;
            denominator += relativeMembership;
        }
        double utility = numerator / denominator;
        if (!(denominator > 0.0) || !Double.isFinite(denominator)
                || !Double.isFinite(utility) || utility < 0.0 || utility > 1.0) {
            throw new IllegalStateException("No finite ordered Gaussian utility can be computed");
        }
        return utility;
    }

    private double logMembership(double value, double center) {
        double standardized = (value - center) / sigma;
        double squared = standardized * standardized;
        if (!Double.isFinite(squared)) {
            throw new IllegalArgumentException("Gaussian log membership is not representable on [0,1]");
        }
        return -0.5 * squared;
    }
}
