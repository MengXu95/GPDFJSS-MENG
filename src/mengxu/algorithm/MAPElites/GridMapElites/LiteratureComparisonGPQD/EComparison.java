package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.PairedStatistics;

/** Comparisons consume verified completed cases only; no cross-regime or cross-scenario pooling. */
public final class EComparison {
    public static final double ALPHA = .05;
    public static final int PLANNED_TESTS = 12;
    private EComparison() { }

    static final class Contrast {
        final EConfiguration.Regime regime;
        final EConfiguration.Scenario scenario;
        final EConfiguration.Method baseline;
        final List<Properties> qd = new ArrayList<>(), other = new ArrayList<>();
        String status;
        int wins, losses, ties, unknownPairs, qdFailures, otherFailures, qdAvailable, otherAvailable, testableFamily;
        double p = Double.NaN, adjusted = Double.NaN, effect = Double.NaN;
        PairedStatistics.SignedRankResult rank;
        Contrast(EConfiguration.Regime regime, EConfiguration.Scenario scenario, EConfiguration.Method baseline) {
            this.regime = regime; this.scenario = scenario; this.baseline = baseline;
        }
    }

    static List<Contrast> contrasts(int runs, Map<String, Properties> cases) throws IOException {
        List<Contrast> output = new ArrayList<>();
        for (EConfiguration.Regime regime : EConfiguration.Regime.values()) {
            List<Contrast> family = new ArrayList<>();
            for (EConfiguration.Scenario scenario : EConfiguration.Scenario.values()) {
                for (EConfiguration.Method baseline : new EConfiguration.Method[]{
                        EConfiguration.Method.GP, EConfiguration.Method.LEXICASE}) {
                    Contrast contrast = new Contrast(regime, scenario, baseline);
                    boolean missing = false, compatible = true, nonfinite = false;
                    double[] differences = new double[runs];
                    for (int run = 0; run < runs; run++) {
                        Properties qd = cases.get(new EConfiguration.Case(EConfiguration.Method.QDGP, regime, scenario, run).key());
                        Properties other = cases.get(new EConfiguration.Case(baseline, regime, scenario, run).key());
                        if (qd != null) { contrast.qdAvailable++; }
                        if (other != null) { contrast.otherAvailable++; }
                        if (qd == null || other == null) { missing = true; continue; }
                        contrast.qd.add(qd); contrast.other.add(other);
                        for (String key : new String[]{"evaluation-family-hash", "training-family-hash",
                                "objective", "utilisation", "gp-seed", "training-seed",
                                "initial-training-seed", "fitness_evaluations",
                                "initial-genotype-sha256", "initial-evaluated-population-sha256"}) {
                            if (!EFiles.required(qd, key).equals(EFiles.required(other, key))) { compatible = false; }
                        }
                        double a = ETest.value(qd, "mean_test_objective"), b = ETest.value(other, "mean_test_objective");
                        if (Double.isNaN(a) || Double.isNaN(b)) { contrast.unknownPairs++; }
                        else if (a < b) { contrast.wins++; } else if (a > b) { contrast.losses++; } else { contrast.ties++; }
                        if (ETest.value(qd, "test_failed_run") > 0) { contrast.qdFailures++; }
                        if (ETest.value(other, "test_failed_run") > 0) { contrast.otherFailures++; }
                        if (!Double.isFinite(a) || !Double.isFinite(b)) { nonfinite = true; }
                        differences[run] = Double.isFinite(a) && Double.isFinite(b) ? a - b : Double.NaN;
                    }
                    if (missing) { contrast.status = "COMPARISON_NOT_READY_MISSING_GROUPS"; }
                    else if (!compatible) { contrast.status = "INCOMPATIBLE_MATCHED_INPUTS"; }
                    else if (nonfinite) { contrast.status = "NONFINITE_TEST_OUTCOME"; }
                    else if (runs != 30) { contrast.status = "DIAGNOSTIC_ONLY_NOT_FORMAL_30_RUNS"; }
                    else {
                        contrast.rank = PairedStatistics.signedRank(differences);
                        contrast.status = contrast.rank.status;
                        contrast.p = contrast.rank.pValue;
                        contrast.effect = contrast.rank.rankBiserial;
                    }
                    family.add(contrast);
                }
            }
            double[] p = new double[PLANNED_TESTS];
            int testable = 0;
            for (int i = 0; i < p.length; i++) {
                p[i] = family.get(i).p;
                if (!Double.isNaN(p[i])) { testable++; }
            }
            double[] adjusted = PairedStatistics.holm(p);
            for (int i = 0; i < p.length; i++) {
                family.get(i).adjusted = adjusted[i];
                family.get(i).testableFamily = testable;
            }
            output.addAll(family);
        }
        return output;
    }

    static Properties write(Path directory, EConfiguration config, Map<String, Properties> cases) throws IOException {
        List<Contrast> contrasts = contrasts(config.runs, cases);
        Properties status = new Properties();
        int ready = 0, missing = 0, incompatible = 0;
        try (BufferedWriter primary = EFiles.create(directory.resolve("primary_comparisons.csv"));
             BufferedWriter differences = EFiles.create(directory.resolve("paired_differences.csv"));
             BufferedWriter summaries = EFiles.create(directory.resolve("paired_summary.csv"))) {
            EFiles.row(primary, "regime", "scenario_id", "objective", "comparator",
                    "qdgp_available_runs", "comparator_available_runs", "run_pairs",
                    "qdgp_failure_runs", "comparator_failure_runs", "wins_lower_is_better", "losses", "ties", "unknown_pairs",
                    "status", "w_plus", "nonzero_pairs", "p_exact_two_sided", "rank_biserial_qdgp_better_positive",
                    "family_id", "family_planned_tests", "family_testable_tests", "holm_p", "alpha", "reject_holm");
            EFiles.row(differences, "regime", "scenario_id", "comparator", "run_id", "metric",
                    "qdgp_value", "comparator_value", "qdgp_minus_comparator", "status");
            ETest.descriptionHeader(summaries, true);
            for (Contrast contrast : contrasts) {
                if (contrast.status.startsWith("COMPARISON_NOT_READY")) { missing++; }
                else if (contrast.status.equals("INCOMPATIBLE_MATCHED_INPUTS")) { incompatible++; }
                else { ready++; }
                boolean comparable = !contrast.status.startsWith("COMPARISON_NOT_READY")
                        && !contrast.status.equals("INCOMPATIBLE_MATCHED_INPUTS");
                EFiles.row(primary, contrast.regime, contrast.scenario.id, contrast.scenario.objective.getName(),
                        contrast.baseline, contrast.qdAvailable, contrast.otherAvailable, contrast.qd.size(),
                        comparable ? contrast.qdFailures : "NA", comparable ? contrast.otherFailures : "NA",
                        comparable ? contrast.wins : "NA", comparable ? contrast.losses : "NA", comparable ? contrast.ties : "NA",
                        comparable ? contrast.unknownPairs : "NA", contrast.status,
                        contrast.rank == null ? "NA" : contrast.rank.wPlus,
                        contrast.rank == null ? "NA" : contrast.rank.nonzeroPairs,
                        ETest.numeric(contrast.p), ETest.numeric(contrast.effect),
                        contrast.regime + ":heldout_objective", PLANNED_TESTS, contrast.testableFamily,
                        ETest.numeric(contrast.adjusted), ALPHA,
                        Double.isNaN(contrast.adjusted) ? "NA" : Boolean.toString(contrast.adjusted <= ALPHA));
                if (contrast.status.startsWith("COMPARISON_NOT_READY")
                        || contrast.status.equals("INCOMPATIBLE_MATCHED_INPUTS")) { continue; }
                for (String metric : ETest.METRICS) {
                    List<Double> values = new ArrayList<>();
                    for (int i = 0; i < contrast.qd.size(); i++) {
                        Properties qd = contrast.qd.get(i), other = contrast.other.get(i);
                        double a = ETest.value(qd, metric), b = ETest.value(other, metric);
                        double difference = Double.isFinite(a) && Double.isFinite(b) ? a - b : Double.NaN;
                        values.add(difference);
                        EFiles.row(differences, contrast.regime, contrast.scenario.id, contrast.baseline,
                                qd.getProperty("run-id"), metric, ETest.numeric(a), ETest.numeric(b),
                                ETest.numeric(difference), Double.isFinite(difference) ? "FINITE"
                                        : metric.startsWith("native_") ? "NOT_APPLICABLE_OUTPUT_KINDS" : "NONFINITE_NO_NUMERIC_DIFFERENCE");
                    }
                    ETest.describe(summaries, "QDGP", contrast.regime.name(), contrast.scenario.id,
                            contrast.baseline.name(), metric, values);
                }
            }
        }
        status.setProperty("comparison-status", incompatible > 0 ? "INCOMPATIBLE_MATCHED_INPUTS"
                : missing > 0 ? "COMPARISON_NOT_READY"
                : config.runs != 30 ? "DIAGNOSTIC_COMPARISON_ONLY"
                : "COMPARISON_COMPLETE");
        status.setProperty("comparison-ready-contrasts", Integer.toString(ready));
        status.setProperty("comparison-missing-contrasts", Integer.toString(missing));
        status.setProperty("comparison-incompatible-contrasts", Integer.toString(incompatible));
        status.setProperty("comparison-inference", "PRIMARY_HELDOUT_OBJECTIVE_ONLY; EXACT_WILCOXON_WILCOX_ZEROS;"
                + " HOLM_12_PER_REGIME; UNAVAILABLE_SLOTS_RETAINED_AS_1_FOR_MULTIPLICITY_ONLY");
        EFiles.write(directory.resolve("comparison_status.properties"), status, false);
        return status;
    }
}
