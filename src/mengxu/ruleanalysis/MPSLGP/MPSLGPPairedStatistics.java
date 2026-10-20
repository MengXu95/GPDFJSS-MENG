package mengxu.ruleanalysis.MPSLGP;

import java.util.Arrays;
import java.util.Comparator;

/** Exact paired signed-rank inference for gains oriented positive = Full better. */
final class MPSLGPPairedStatistics {
    private MPSLGPPairedStatistics() {}

    static final class Result {
        final int nonzeroPairs;
        final double positiveRankSum, negativeRankSum, pValue;

        private Result(int nonzeroPairs, double positiveRankSum, double negativeRankSum, double pValue) {
            this.nonzeroPairs = nonzeroPairs;
            this.positiveRankSum = positiveRankSum;
            this.negativeRankSum = negativeRankSum;
            this.pValue = pValue;
        }
    }

    /**
     * Conditional two-sided sign-permutation test, with absolute midranks and
     * exact zeros omitted (Wilcox convention). Empty/all-zero samples return p = 1.
     * At most 60 nonzero pairs are supported so all subset counts fit in a long.
     *
     * @throws IllegalArgumentException for null, nonfinite, or oversized samples
     */
    static Result wilcoxon(double[] gains) {
        if (gains == null) { throw new IllegalArgumentException("Gains must not be null."); }
        int nonzero = 0;
        for (double gain : gains) {
            if (!Double.isFinite(gain)) { throw new IllegalArgumentException("Gains must be finite."); }
            if (gain != 0.0) { nonzero++; }
        }
        if (nonzero > 60) { throw new IllegalArgumentException("At most 60 nonzero pairs are supported."); }
        if (nonzero == 0) { return new Result(0, 0, 0, 1); }

        double[] magnitudes = new double[nonzero];
        int index = 0;
        for (double gain : gains) {
            if (gain != 0.0) { magnitudes[index++] = Math.abs(gain); }
        }
        Arrays.sort(magnitudes);
        double[] ranks = MPSLGPJointTaskAnalysisMain.midranks(magnitudes);
        int positive = 0;
        for (double gain : gains) {
            if (gain > 0.0) {
                positive += (int)(2 * ranks[Arrays.binarySearch(magnitudes, Math.abs(gain))]);
            }
        }
        int total = nonzero * (nonzero + 1);
        int negative = total - positive;
        long[] counts = new long[total + 1];
        counts[0] = 1;
        int reachable = 0;
        for (double rank : ranks) {
            int doubledRank = (int)(2 * rank);
            // Descending updates use each pair once; tied pairs remain distinct signs.
            for (int sum = reachable; sum >= 0; sum--) {
                counts[sum + doubledRank] += counts[sum];
            }
            reachable += doubledRank;
        }
        long lowerTail = 0;
        for (int sum = 0; sum <= Math.min(positive, negative); sum++) {
            lowerTail += counts[sum];
        }
        double pValue = Math.min(1.0, 2.0 * lowerTail / (1L << nonzero));
        return new Result(nonzero, positive / 2.0, negative / 2.0, pValue);
    }

    /**
     * Holm step-down adjusted p-values in the original input order, without mutation.
     *
     * @throws IllegalArgumentException for null or values outside finite [0, 1]
     */
    static double[] holm(double[] pValues) {
        if (pValues == null) { throw new IllegalArgumentException("P-values must not be null."); }
        Integer[] order = new Integer[pValues.length];
        for (int i = 0; i < pValues.length; i++) {
            double p = pValues[i];
            if (!Double.isFinite(p) || p < 0.0 || p > 1.0) {
                throw new IllegalArgumentException("P-values must be finite and in [0, 1].");
            }
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> pValues[i]));
        double[] adjusted = new double[pValues.length];
        double previous = 0;
        for (int i = 0; i < order.length; i++) {
            previous = Math.max(previous, Math.min(1.0, (order.length - i) * pValues[order[i]]));
            adjusted[order[i]] = previous;
        }
        return adjusted;
    }
}
