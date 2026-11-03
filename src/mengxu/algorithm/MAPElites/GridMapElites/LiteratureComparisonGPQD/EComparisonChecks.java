package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.PairedStatistics;

public final class EComparisonChecks {
    private EComparisonChecks() { }

    static void runChecks(EConfiguration base, Path parent) throws IOException {
        near(PairedStatistics.signedRank(new double[]{1, 2, 3}).pValue, .25, "Exact positive signs");
        near(PairedStatistics.signedRank(new double[]{0, 1, -1, 2}).pValue, .75, "Wilcox zeros and tied absolute ranks");
        near(PairedStatistics.signedRank(new double[]{0, 0}).rankBiserial, 0, "All-zero effect");
        double[] family = new double[12];
        Arrays.fill(family, Double.NaN);
        family[0] = .01; family[3] = .03; family[7] = .04;
        double[] adjusted = PairedStatistics.holm(family);
        near(adjusted[0], .12, "All 12 planned slots retained");
        near(adjusted[3], .33, "Holm second");
        near(adjusted[7], .40, "Holm third");
        check(Double.isNaN(adjusted[1]), "Unavailability is not displayed as p=1");

        EConfiguration config = EConfiguration.parse(new String[]{"--config", base.sourcePath.toString(), "--set", "runs=30"});
        Map<String, Properties> cases = new LinkedHashMap<>();
        for (EConfiguration.Method method : EConfiguration.Method.values()) for (EConfiguration.Regime regime : EConfiguration.Regime.values()) {
            for (EConfiguration.Scenario scenario : EConfiguration.Scenario.values()) for (int run = 0; run < 30; run++) {
                EConfiguration.Case item = new EConfiguration.Case(method, regime, scenario, run);
                Properties p = new Properties();
                for (String metric : ETest.METRICS) { p.setProperty(metric, "0"); }
                if (method != EConfiguration.Method.QDGP) {
                    for (String metric : ETest.METRICS) {
                        if (metric.startsWith("native_")) { p.setProperty(metric, "NA"); }
                    }
                }
                p.setProperty("evaluation-family-hash", "same-evaluation"); p.setProperty("training-family-hash", "same-training");
                p.setProperty("objective", scenario.objective.getName()); p.setProperty("utilisation", "" + scenario.utilisation);
                p.setProperty("gp-seed", "" + config.gpSeed(item)); p.setProperty("training-seed", "" + config.trainingSeed(item));
                p.setProperty("initial-training-seed", "" + config.trainingSeed(item));
                p.setProperty("final-training-seed", "" + config.trainingSeed(item));
                p.setProperty("initial-genotype-sha256", ArchiveCandidate.sha256("genotype:" + scenario + ":" + run));
                p.setProperty("initial-evaluated-population-sha256", ArchiveCandidate.sha256("evaluated:" + scenario + ":" + run));
                p.setProperty("fitness_evaluations", "100500"); p.setProperty("run-id", "" + run);
                double cost = 100 + run;
                if (method == EConfiguration.Method.GP && scenario == EConfiguration.Scenario.T085 && run < 7) { cost += 2; }
                if (method == EConfiguration.Method.QDGP && regime == EConfiguration.Regime.fixed
                        && scenario == EConfiguration.Scenario.T095 && run == 0) {
                    cost = Double.POSITIVE_INFINITY; p.setProperty("test_failed_run", "1");
                }
                p.setProperty("mean_test_objective", Double.toString(cost));
                cases.put(item.key(), p);
            }
        }
        List<EComparison.Contrast> contrasts = EComparison.contrasts(30, cases);
        EComparison.Contrast fixed = find(contrasts, EConfiguration.Regime.fixed, EConfiguration.Scenario.T085, EConfiguration.Method.GP);
        EComparison.Contrast rotating = find(contrasts, EConfiguration.Regime.rotating, EConfiguration.Scenario.T085, EConfiguration.Method.GP);
        near(fixed.p, .015625, "Seven nonzero signs, remaining zero pairs removed");
        near(fixed.adjusted, .1875, "12 planned tests, not ten available tests");
        near(rotating.adjusted, .1875, "Separate regime family, not pooled 24 tests");
        check(fixed.testableFamily == 10 && rotating.testableFamily == 12, "Explicit testable-family counts");
        near(fixed.effect, 1, "Positive effect means QDGP lower/better");
        EComparison.Contrast failed = find(contrasts, EConfiguration.Regime.fixed, EConfiguration.Scenario.T095, EConfiguration.Method.GP);
        check(failed.status.equals("NONFINITE_TEST_OUTCOME") && failed.qd.size() == 30
                && Double.isNaN(failed.p) && Double.isNaN(failed.effect) && Double.isNaN(failed.adjusted),
                "Any failure disables the entire contrast; no selected finite subset");
        EComparison.Contrast zero = find(contrasts, EConfiguration.Regime.fixed, EConfiguration.Scenario.T085, EConfiguration.Method.LEXICASE);
        check(zero.status.equals("ALL_ZERO") && zero.p == 1 && zero.effect == 0, "All-zero contrast is explicit");
        Path full = Files.createDirectory(parent.resolve("comparison-full"));
        check(EComparison.write(full, config, cases).getProperty("comparison-status").equals("COMPARISON_COMPLETE"),
                "Formal completeness is about matched groups, not manufactured finite outcomes");

        String badKey = new EConfiguration.Case(EConfiguration.Method.GP, EConfiguration.Regime.fixed,
                EConfiguration.Scenario.T085, 3).key();
        cases.get(badKey).setProperty("gp-seed", "999");
        EComparison.Contrast bad = find(EComparison.contrasts(30, cases), EConfiguration.Regime.fixed,
                EConfiguration.Scenario.T085, EConfiguration.Method.GP);
        check(bad.status.equals("INCOMPATIBLE_MATCHED_INPUTS") && Double.isNaN(bad.p), "Unmatched controls cannot produce inference");

        Map<String, Properties> lexOnly = new LinkedHashMap<>();
        for (Map.Entry<String, Properties> entry : cases.entrySet()) {
            if (entry.getKey().startsWith("LEXICASE.")) { lexOnly.put(entry.getKey(), entry.getValue()); }
        }
        Path partial = Files.createDirectory(parent.resolve("comparison-lex-only"));
        check(EComparison.write(partial, config, lexOnly).getProperty("comparison-status").equals("COMPARISON_NOT_READY"),
                "Lexicase alone is never a completed matched comparison");
    }

    private static EComparison.Contrast find(List<EComparison.Contrast> rows, EConfiguration.Regime regime,
                                             EConfiguration.Scenario scenario, EConfiguration.Method baseline) {
        for (EComparison.Contrast row : rows) {
            if (row.regime == regime && row.scenario == scenario && row.baseline == baseline) { return row; }
        }
        throw new AssertionError("Missing contrast");
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-12, message + ": " + actual);
    }
    private static void check(boolean valid, String message) { if (!valid) { throw new AssertionError(message); } }
}
