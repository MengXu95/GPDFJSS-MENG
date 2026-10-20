package mengxu.ruleanalysis.MPSLGP;

import java.util.Arrays;
import java.util.Random;

import static mengxu.ruleanalysis.MPSLGP.MPSLGPPairedStatistics.holm;
import static mengxu.ruleanalysis.MPSLGP.MPSLGPPairedStatistics.wilcoxon;

/** Dependency-free runner; main performs synthetic mathematical checks, not experiments. */
public final class MPSLGPPairedStatisticsTest {
    private static int assertions;
    private static int enumeratedCases;

    public static void main(String[] args) {
        testKnownSignedRanks();
        testBruteForce();
        testLongCountsAndThresholds();
        testInvalidSignedRanks();
        testKnownHolm();
        testHolmProperties();
        testInvalidHolm();
        System.out.println("PASS: " + assertions + " paired-statistics assertions; "
                + enumeratedCases + " independent sign-enumeration cases.");
    }

    private static void testKnownSignedRanks() {
        result(new double[0], 0, 0, 0, 1);
        result(new double[]{0, -0.0, 0}, 0, 0, 0, 1);
        result(new double[]{2}, 1, 1, 0, 1);
        result(new double[]{-2}, 1, 0, 1, 1);
        result(new double[]{1, 2}, 2, 3, 0, .5);
        result(new double[]{1, -2, 3}, 3, 4, 2, .75);
        result(new double[]{1, -2, -3}, 3, 1, 5, .5);
        result(new double[]{1, -1}, 2, 1.5, 1.5, 1);
        result(new double[]{0, 1, -1, -0.0, 2}, 3, 4.5, 1.5, .75);
        result(new double[]{1, 1, -2, -2}, 4, 3, 7, .5);
        result(new double[]{Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE},
                3, 4.5, 1.5, .75);
        result(new double[]{1, -Math.nextUp(1.0), 2}, 3, 4, 2, .75);
        result(new double[]{Double.MIN_VALUE, -2 * Double.MIN_VALUE, Double.MAX_VALUE},
                3, 4, 2, .75);
    }

    private static void testBruteForce() {
        // Exhaust every vector over a tied/zero alphabet through length five.
        for (int length = 0; length <= 5; length++) {
            int vectors = 1;
            for (int i = 0; i < length; i++) { vectors *= 5; }
            for (int code = 0; code < vectors; code++) {
                double[] gains = new double[length];
                int remaining = code;
                for (int i = 0; i < length; i++) {
                    gains[i] = remaining % 5 - 2;
                    remaining /= 5;
                }
                compareEnumeration(gains);
            }
        }
        Random random = new Random(937162);
        for (int length = 0; length <= 16; length++) {
            for (int sample = 0; sample < 24; sample++) {
                double[] gains = new double[length];
                for (int i = 0; i < length; i++) {
                    double magnitude = sample % 3 == 0 ? i + 1 : 1 + random.nextInt(5);
                    gains[i] = random.nextBoolean() ? magnitude : -magnitude;
                    if (sample % 3 == 2 && random.nextInt(4) == 0) { gains[i] = -0.0; }
                }
                compareEnumeration(gains);
                MPSLGPPairedStatistics.Result original = wilcoxon(gains);
                double[] reversedSigns = new double[length];
                double[] reordered = new double[length];
                double[] scaled = new double[length];
                for (int i = 0; i < length; i++) {
                    reversedSigns[i] = -gains[i];
                    reordered[i] = gains[length - i - 1];
                    scaled[i] = 8 * gains[i];
                }
                MPSLGPPairedStatistics.Result reversed = wilcoxon(reversedSigns);
                equal(reversed.pValue, original.pValue, "sign reversal p-value");
                equal(reversed.positiveRankSum, original.negativeRankSum, "sign reversal positive ranks");
                equal(reversed.negativeRankSum, original.positiveRankSum, "sign reversal negative ranks");
                sameResult(wilcoxon(reordered), original, "input order invariance");
                sameResult(wilcoxon(scaled), original, "positive scaling invariance");
            }
        }
    }

    private static void compareEnumeration(double[] gains) {
        double[] snapshot = gains.clone();
        MPSLGPPairedStatistics.Result actual = wilcoxon(gains);
        int n = 0;
        int positive = 0;
        int total = 0;
        int[] doubledRanks = new int[gains.length];
        // Independent ranking by pairwise comparisons, with no sorting or shared helper.
        for (double gain : gains) {
            if (gain == 0.0) { continue; }
            int smaller = 0;
            int tied = 0;
            for (double other : gains) {
                if (other == 0.0) { continue; }
                if (Math.abs(other) < Math.abs(gain)) { smaller++; }
                if (Math.abs(other) == Math.abs(gain)) { tied++; }
            }
            int rank = 2 * smaller + tied + 1;
            doubledRanks[n++] = rank;
            total += rank;
            if (gain > 0) { positive += rank; }
        }
        check(n <= 16, "oracle limited to sixteen signs");
        int tail = 0;
        int assignments = 1 << n;
        for (int mask = 0; mask < assignments; mask++) {
            int sum = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) { sum += doubledRanks[i]; }
            }
            if (sum <= Math.min(positive, total - positive)) { tail++; }
        }
        check(actual.nonzeroPairs == n, "enumerated nonzero count");
        equal(actual.positiveRankSum, positive / 2.0, "enumerated positive ranks");
        equal(actual.negativeRankSum, (total - positive) / 2.0, "enumerated negative ranks");
        equal(actual.pValue, Math.min(1.0, 2.0 * tail / assignments), "enumerated exact p-value");
        equal(actual.positiveRankSum + actual.negativeRankSum, n * (n + 1) / 2.0, "rank conservation");
        check(actual.pValue <= 1 && actual.pValue >= (n == 0 ? 1 : Math.scalb(2.0, -n)),
                "two-sided probability bounds");
        double lattice = Math.scalb(actual.pValue, Math.max(0, n - 1));
        equal(lattice, Math.rint(lattice), "exact sign-permutation probability lattice");
        unchanged(gains, snapshot, "Wilcoxon does not mutate input");
        enumeratedCases++;
    }

    private static void testLongCountsAndThresholds() {
        for (int n = 1; n <= 60; n++) {
            double[] gains = new double[n];
            for (int i = 0; i < n; i++) { gains[i] = i + 1; }
            result(gains, n, n * (n + 1) / 2.0, 0, Math.scalb(2.0, -n));
            for (int i = 0; i < n; i++) { gains[i] = -gains[i]; }
            result(gains, n, 0, n * (n + 1) / 2.0, Math.scalb(2.0, -n));
        }
        for (int n : new int[]{30, 60}) {
            for (int positive = 0; positive <= n; positive++) {
                double[] gains = new double[n];
                Arrays.fill(gains, -1);
                Arrays.fill(gains, 0, positive, 1);
                int threshold = Math.min(positive, n - positive);
                long tail = 0;
                long coefficient = 1;
                // Equal midranks reduce independently to a Binomial(n, 1/2) tail.
                for (int k = 0; k <= threshold; k++) {
                    tail += coefficient;
                    if (k < threshold) { coefficient = coefficient * (n - k) / (k + 1); }
                }
                result(gains, n, positive * (n + 1) / 2.0, (n - positive) * (n + 1) / 2.0,
                        Math.min(1.0, 2.0 * tail / (1L << n)));
            }
        }
        double[] thirty = new double[30];
        Arrays.fill(thirty, 1);
        equal(wilcoxon(thirty).pValue, 2.0 / (1L << 30), "thirty same-sign exact probability");
        check(wilcoxon(thirty).pValue < 1e-8, "thirty-seed tail is not rounded to zero or approximated");
        check(wilcoxon(new double[]{1, 2, 3, 4, 5}).pValue > .05, "five signs fail two-sided 5% threshold");
        check(wilcoxon(new double[]{1, 2, 3, 4, 5, 6}).pValue < .05, "six signs pass two-sided 5% threshold");
        double[] padded = new double[121];
        Arrays.fill(padded, -0.0);
        Arrays.fill(padded, 0, 60, 1);
        result(padded, 60, 1830, 0, Math.scalb(2.0, -60));
        result(new double[100], 0, 0, 0, 1);
    }

    private static void testInvalidSignedRanks() {
        invalid(() -> wilcoxon(null), "null gains");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double[] gains = {0, 1, bad, -2};
            double[] snapshot = gains.clone();
            invalid(() -> wilcoxon(gains), "nonfinite gain");
            unchanged(gains, snapshot, "invalid gains not mutated");
        }
        double[] tooMany = new double[61];
        Arrays.fill(tooMany, 1);
        double[] snapshot = tooMany.clone();
        invalid(() -> wilcoxon(tooMany), "sixty-one nonzero pairs rejected");
        unchanged(tooMany, snapshot, "oversized positive gains not mutated");
        Arrays.fill(tooMany, -1);
        snapshot = tooMany.clone();
        invalid(() -> wilcoxon(tooMany), "negative nonzero pairs also count toward cap");
        unchanged(tooMany, snapshot, "oversized negative gains not mutated");
    }

    private static void testKnownHolm() {
        vector(holm(new double[]{.01, .04, .03}), new double[]{.03, .06, .06}, "known Holm vector");
        vector(holm(new double[]{.04, .03, .01}), new double[]{.06, .06, .03}, "Holm original ordering");
        vector(holm(new double[]{.02, .02, .01}), new double[]{.04, .04, .03}, "Holm tied p-values");
        vector(holm(new double[]{.02, .021, .022}), new double[]{.06, .06, .06}, "running maximum");
        vector(holm(new double[]{.8, .9, 1}), new double[]{1, 1, 1}, "Holm capped at one");
        vector(holm(new double[]{0, -0.0, 1}), new double[]{0, 0, 1}, "Holm endpoint probabilities");
        vector(holm(new double[]{.375}), new double[]{.375}, "singleton Holm");
        vector(holm(new double[0]), new double[0], "empty Holm");
        vector(holm(new double[]{.001, .012, .024, .2}), new double[]{.004, .036, .048, .2},
                "step-down thresholds");
        double[] adjusted = holm(new double[]{.001, .012, .024, .2});
        check(adjusted[0] <= .05 && adjusted[1] <= .05 && adjusted[2] <= .05 && adjusted[3] > .05,
                "Holm 5% family-wise decisions");
    }

    private static void testHolmProperties() {
        Random random = new Random(319451);
        for (int length = 0; length <= 30; length++) {
            for (int sample = 0; sample < 20; sample++) {
                double[] raw = new double[length];
                for (int i = 0; i < length; i++) { raw[i] = random.nextInt(101) / 100.0; }
                double[] snapshot = raw.clone();
                double[] adjusted = holm(raw);
                check(adjusted != raw, "Holm returns a new array");
                check(adjusted.length == raw.length, "Holm preserves length");
                unchanged(raw, snapshot, "Holm does not mutate input");
                for (int i = 0; i < length; i++) {
                    double expected = 0;
                    // Closed-form maximum over candidate thresholds, independent of sorted step-down.
                    for (double candidate : raw) {
                        if (candidate > raw[i]) { continue; }
                        int smaller = 0;
                        for (double value : raw) {
                            if (value < candidate) { smaller++; }
                        }
                        expected = Math.max(expected, Math.min(1.0, (length - smaller) * candidate));
                    }
                    equal(adjusted[i], expected, "Holm independent threshold maximum");
                    check(adjusted[i] >= raw[i] && adjusted[i] <= 1, "Holm probability bounds");
                    for (int j = 0; j < length; j++) {
                        if (raw[i] <= raw[j]) {
                            check(adjusted[i] <= adjusted[j], "Holm monotonicity including ties");
                        }
                    }
                }
                double[] reversed = new double[length];
                for (int i = 0; i < length; i++) { reversed[i] = raw[length - i - 1]; }
                double[] reversedAdjusted = holm(reversed);
                for (int i = 0; i < length; i++) {
                    equal(reversedAdjusted[i], adjusted[length - i - 1], "Holm permutation equivariance");
                }
            }
        }
    }

    private static void testInvalidHolm() {
        invalid(() -> holm(null), "null p-values");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                -1, -Double.MIN_VALUE, Math.nextUp(1.0)}) {
            double[] values = {.01, bad, .03};
            double[] snapshot = values.clone();
            invalid(() -> holm(values), "invalid p-value");
            unchanged(values, snapshot, "invalid p-values not mutated");
        }
    }

    private static void result(double[] gains, int n, double positive, double negative, double p) {
        double[] snapshot = gains.clone();
        MPSLGPPairedStatistics.Result actual = wilcoxon(gains);
        check(actual.nonzeroPairs == n, "known nonzero pairs");
        equal(actual.positiveRankSum, positive, "known positive rank sum");
        equal(actual.negativeRankSum, negative, "known negative rank sum");
        equal(actual.pValue, p, "known exact p-value");
        unchanged(gains, snapshot, "known input not mutated");
    }

    private static void sameResult(MPSLGPPairedStatistics.Result actual,
                                   MPSLGPPairedStatistics.Result expected, String message) {
        check(actual.nonzeroPairs == expected.nonzeroPairs, message + ": count");
        equal(actual.positiveRankSum, expected.positiveRankSum, message + ": positive");
        equal(actual.negativeRankSum, expected.negativeRankSum, message + ": negative");
        equal(actual.pValue, expected.pValue, message + ": p");
    }

    private static void vector(double[] actual, double[] expected, String message) {
        check(actual.length == expected.length, message + ": length");
        for (int i = 0; i < expected.length; i++) {
            check(Math.abs(actual[i] - expected[i]) <= 1e-15, message + ": index " + i);
        }
    }

    private static void unchanged(double[] actual, double[] expected, String message) {
        check(Arrays.equals(actual, expected), message);
    }

    private static void invalid(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage() != null && !expected.getMessage().isEmpty(), message);
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException: " + message);
    }

    private static void equal(double actual, double expected, String message) {
        check(actual == expected, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new AssertionError(message); }
    }
}
