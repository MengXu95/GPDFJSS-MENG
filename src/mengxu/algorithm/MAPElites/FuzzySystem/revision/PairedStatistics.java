package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Run-block statistics; signed differences are Fuzzy minus the comparator. */
public final class PairedStatistics {
    public static final int MAX_PAIRS = 200;

    public static final class SignedRankResult {
        public final String status;
        public final int pairs;
        public final int nonzeroPairs;
        public final int zeroPairs;
        public final double wPlus;
        public final double pValue;
        public final double rankBiserial;

        private SignedRankResult(String status, int pairs, int nonzeroPairs,
                                 double wPlus, double pValue, double rankBiserial) {
            this.status = status;
            this.pairs = pairs;
            this.nonzeroPairs = nonzeroPairs;
            this.zeroPairs = pairs - nonzeroPairs;
            this.wPlus = wPlus;
            this.pValue = pValue;
            this.rankBiserial = rankBiserial;
        }
    }

    public static final class Interval {
        public final double lower;
        public final double upper;

        private Interval(double lower, double upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }

    private PairedStatistics() { }

    /**
     * Exact conditional sign distribution with tie-averaged ranks and wilcox
     * zero removal. Positive rank-biserial values favour Fuzzy (lower values).
     */
    public static SignedRankResult signedRank(double[] differences) {
        requireFinite(differences);
        if (differences.length > MAX_PAIRS) {
            throw new IllegalArgumentException("Exact signed-rank supports at most " + MAX_PAIRS + " run pairs");
        }
        List<Double> nonzero = new ArrayList<>();
        for (double difference : differences) {
            if (difference != 0.0) { nonzero.add(difference); }
        }
        nonzero.sort(Comparator.comparingDouble(value -> Math.abs(value)));
        int n = nonzero.size();
        if (n == 0) {
            return new SignedRankResult("ALL_ZERO", differences.length, 0, 0, 1, 0);
        }
        int[] ticks = new int[n];
        int observed = 0;
        for (int first = 0; first < n;) {
            int end = first + 1;
            while (end < n && Math.abs(nonzero.get(end)) == Math.abs(nonzero.get(first))) {
                end++;
            }
            // Ranks first+1 through end have twice-average rank first+1+end.
            int rank = first + 1 + end;
            for (int i = first; i < end; i++) {
                ticks[i] = rank;
                if (nonzero.get(i) > 0) { observed += rank; }
            }
            first = end;
        }
        int total = n * (n + 1);
        double[] probability = new double[total + 1];
        probability[0] = 1;
        int reachable = 0;
        for (int rank : ticks) {
            for (int sum = reachable + rank; sum >= 0; sum--) {
                probability[sum] = 0.5 * (probability[sum]
                        + (sum >= rank ? probability[sum - rank] : 0));
            }
            reachable += rank;
        }
        int tail = Math.min(observed, total - observed);
        double cumulative = 0;
        for (int sum = 0; sum <= tail; sum++) { cumulative += probability[sum]; }
        return new SignedRankResult("OK", differences.length, n, observed / 2.0,
                Math.min(1, 2 * cumulative), (total - 2.0 * observed) / total);
    }

    /**
     * The array length is the predeclared family size. NaN remains unavailable
     * in the result but counts as p=1 for multiplicity, never shrinking the family.
     */
    public static double[] holm(double[] pValues) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < pValues.length; i++) {
            if (Double.isNaN(pValues[i])) { continue; }
            if (!Double.isFinite(pValues[i]) || pValues[i] < 0 || pValues[i] > 1) {
                throw new IllegalArgumentException("Holm requires p-values in [0,1] or NaN for non-testable entries");
            }
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(index -> pValues[index]));
        double[] adjusted = new double[pValues.length];
        Arrays.fill(adjusted, Double.NaN);
        double previous = 0;
        for (int rank = 0; rank < order.size(); rank++) {
            int index = order.get(rank);
            previous = Math.max(previous, Math.min(1, (pValues.length - rank) * pValues[index]));
            adjusted[index] = previous;
        }
        return adjusted;
    }

    /** Percentile interval from whole-run paired differences, never individual rules or instances. */
    public static Interval bootstrapMean(double[] differences, int samples, long seed, double alpha) {
        requireFinite(differences);
        if (samples < 1 || samples > 1000000) {
            throw new IllegalArgumentException("bootstrap-samples must lie in [1,1000000]");
        }
        if (!Double.isFinite(alpha) || alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException("alpha must lie strictly between zero and one");
        }
        if (differences.length < 2) { return new Interval(Double.NaN, Double.NaN); }
        Random random = new Random(seed);
        double[] draws = new double[differences.length];
        double[] means = new double[samples];
        for (int sample = 0; sample < samples; sample++) {
            for (int run = 0; run < draws.length; run++) {
                draws[run] = differences[random.nextInt(differences.length)];
            }
            means[sample] = mean(draws);
        }
        Arrays.sort(means);
        return new Interval(percentile(means, alpha / 2), percentile(means, 1 - alpha / 2));
    }

    /** Empty samples have no descriptive mean. Scaling avoids overflow in the sum. */
    public static double mean(double[] values) {
        requireFinite(values);
        if (values.length == 0) { return Double.NaN; }
        double scale = 0;
        for (double value : values) { scale = Math.max(scale, Math.abs(value)); }
        if (scale == 0) { return 0; }
        double sum = 0;
        double correction = 0;
        for (double value : values) {
            double next = value / scale - correction;
            double updated = sum + next;
            correction = (updated - sum) - next;
            sum = updated;
        }
        return Math.max(-1, Math.min(1, sum / values.length)) * scale;
    }

    public static double sampleSd(double[] values) {
        requireFinite(values);
        if (values.length < 2) { return Double.NaN; }
        double scale = 0;
        for (double value : values) { scale = Math.max(scale, Math.abs(value)); }
        if (scale == 0) { return 0; }
        double centre = mean(values) / scale;
        double squared = 0;
        for (double value : values) {
            double distance = value / scale - centre;
            squared += distance * distance;
        }
        return scale * Math.sqrt(squared / (values.length - 1));
    }

    private static double percentile(double[] sorted, double probability) {
        double position = probability * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        double fraction = position - lower;
        // Convex interpolation, rather than subtracting potentially opposite large values.
        return sorted[lower] * (1 - fraction) + sorted[upper] * fraction;
    }

    private static void requireFinite(double[] values) {
        if (values == null) { throw new IllegalArgumentException("Values must not be null"); }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Statistics require finite complete run pairs");
            }
        }
    }
}
