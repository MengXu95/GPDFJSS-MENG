package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Bounded synthetic checks only; fixtures and analysis outputs stay under the supplied root. */
public final class ExperimentAAnalysisChecks {
    private ExperimentAAnalysisChecks() { }

    public static void runChecks(Path root) throws IOException {
        statisticalChecks();
        Files.createDirectories(root);
        Path base = Files.createDirectory(root.resolve("experiment-a-analysis-checks"));
        Path nonexistent = base.resolve("experiment-A-validation-v1").resolve("QDGPf");
        Path nonexistentOutput = base.resolve("missing-input-analysis");
        try {
            ExperimentAAnalysis.analyze(arguments(nonexistent, nonexistentOutput, 2));
            throw new AssertionError("Missing study input was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("experiment-A-importance-v2")
                    && expected.getMessage().contains("Fuzzy A v2 - 20 Compare Results"), "Actionable stale-path diagnostic");
            check(!Files.exists(nonexistentOutput), "Missing input must fail before output creation");
        }
        Path emptyInput = Files.createDirectory(base.resolve("empty-study"));
        try {
            ExperimentAAnalysis.analyze(arguments(emptyInput, base.resolve("empty-input-analysis"), 2));
            throw new AssertionError("Empty study input was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("No study manifest"), "Actionable empty-input diagnostic");
        }
        Path normal = fixture(base, "normal", "NfixIns", 2, false);
        Path second = fixture(base, "second", "NrotateInsNoReeva", 2, false);
        Path failed = fixture(base, "test-failures", "NfixIns", 2, true);
        String originalManifest = RevisionCsv.fileHash(normal.resolve("manifest.properties"));
        String originalResults = RevisionCsv.fileHash(normal.resolve("run_results.csv"));
        Path firstOutput = base.resolve("normal-analysis");
        ExperimentAAnalysis.analyze(arguments(normal, firstOutput, 2));
        check(properties(firstOutput.resolve("analysis.properties")).getProperty("status").equals("COMPLETE"),
                "Successful metadata");
        check(records(firstOutput.resolve("summary.csv")).size() == 54, "All 6*3*3 summary rows");
        check(records(firstOutput.resolve("paired_comparisons.csv")).size() == 36, "All 6*2*3 comparisons");
        check(records(firstOutput.resolve("paired_differences.csv")).size() == 72, "Every paired run retained");
        importanceOutputChecks(firstOutput);
        check(RevisionCsv.fileHash(normal.resolve("manifest.properties")).equals(originalManifest)
                && RevisionCsv.fileHash(normal.resolve("run_results.csv")).equals(originalResults),
                "Analysis must not mutate inputs");
        check(Files.size(firstOutput.resolve("descriptive_tables.tex")) > 0
                && Files.size(firstOutput.resolve("results_guide.properties")) > 0, "Paper table and guide");

        Path repeat = base.resolve("normal-analysis-repeat");
        ExperimentAAnalysis.analyze(arguments(normal, repeat, 2));
        for (String file : Arrays.asList("summary.csv", "paired_comparisons.csv", "paired_differences.csv")) {
            check(RevisionCsv.fileHash(firstOutput.resolve(file)).equals(RevisionCsv.fileHash(repeat.resolve(file))),
                    "Deterministic output: " + file);
        }
        Path multi = base.resolve("separate-regimes");
        ExperimentAAnalysis.analyze(new String[]{"--input", normal.toString(), "--input", second.toString(),
                "--output-root", multi.toString(), "--expected-runs", "2", "--bootstrap-samples", "200"});
        List<Map<String, String>> multiRows = records(multi.resolve("paired_comparisons.csv"));
        check(multiRows.size() == 72, "Two separate study comparison sets");
        for (Map<String, String> row : multiRows) {
            check(row.get("run_pairs").equals("2") && row.get("family_planned_tests").equals("12")
                    && row.get("family_testable_tests").equals("12"), "No pooling of runs or Holm families");
            check(row.get("family_id").startsWith(row.get("study_id") + ":"), "Study-local family");
        }
        Path three = fixture(base, "three-runs", "NfixIns", 3, false);
        Path threeOutput = base.resolve("three-runs-analysis");
        ExperimentAAnalysis.analyze(arguments(three, threeOutput, 3));
        Map<String, String> threeComparison = find(records(threeOutput.resolve("paired_comparisons.csv")),
                "case_id", "C1", "baseline_selector", "MANUAL_WS", "metric", "complexity");
        near(Double.parseDouble(threeComparison.get("p_two_sided_exact")), 0.25, "Three-run exact tied p");
        check(threeComparison.get("run_pairs").equals("3")
                && threeComparison.get("baseline_role").equals("PRIMARY_MANUAL_WS"),
                "Actual run IDs and primary baseline");

        failureChecks(base, failed);
        diagnosticControlChecks(base);
        importanceSchemaChecks(base);
        malformedChecks(base);
        pathChecks(base, normal, second);
        unchangedChecks(base);
    }

    private static void importanceOutputChecks(Path output) throws IOException {
        Properties metadata = properties(output.resolve("analysis.properties"));
        Properties guide = properties(output.resolve("results_guide.properties"));
        check(metadata.getProperty("protocol").equals("experiment-a-analysis-v2-importance"),
                "Analysis version identifies importance semantics");
        for (String[] entry : new String[][]{{"input-protocol", "experiment-a-v2-importance"},
                {"profiles", "importance-v1"}, {"fuzzy-selector", "FUZZY_IMPORTANCE"},
                {"selectors", "FUZZY_IMPORTANCE,MANUAL_WS,RANDOM"}, {"primary-baseline", "MANUAL_WS"},
                {"reference-baseline", "RANDOM"}}) {
            check(entry[1].equals(metadata.getProperty(entry[0])) && entry[1].equals(guide.getProperty(entry[0])),
                    "Importance output contract: " + entry[0]);
        }
        check(metadata.getProperty("secondary-baseline") == null, "No legacy secondary manual baseline");
        String[] weights = {"5,1,1", "1,1,5", "1,5,1", "1,1,1", "5,1,3", "5,1,5"};
        for (int i = 0; i < weights.length; i++) {
            String key = "C" + (i + 1) + ".importance-weights-P-I-S";
            check(weights[i].equals(metadata.getProperty(key)) && weights[i].equals(guide.getProperty(key)),
                    "Direction-consistent profile importance: " + key);
        }
        check(guide.getProperty("fuzzy-utility").contains("Fixed, not learned")
                && guide.getProperty("fuzzy-utility").contains("sum_t(g_t*mu_t(d))/sum_t(mu_t(d))")
                && guide.getProperty("fuzzy-utility").contains("not Gaussian target membership"),
                "Guide distinguishes fixed graded utility from target membership");
        for (Map<String, String> row : records(output.resolve("paired_comparisons.csv"))) {
            check(row.get("fuzzy_selector").equals("FUZZY_IMPORTANCE"), "No old Fuzzy selector relabelled");
            String baseline = row.get("baseline_selector");
            check(baseline.equals("MANUAL_WS") || baseline.equals("RANDOM"), "Exactly two importance comparators");
            check(row.get("baseline_role").equals(baseline.equals("MANUAL_WS")
                    ? "PRIMARY_MANUAL_WS" : "REFERENCE_RANDOM"), "Primary WS/reference Random roles");
            check(row.get("family_planned_tests").equals("12"), "Six cases times two comparators");
        }
        String table = new String(Files.readAllBytes(output.resolve("descriptive_tables.tex")), StandardCharsets.UTF_8);
        check(table.contains("importance-v1") && table.contains("5,1,3") && table.contains("5,1,5")
                && !table.contains("MANUAL\\_TARGET"), "Importance-only descriptive LaTeX");
    }

    private static void importanceSchemaChecks(Path base) throws IOException {
        Path legacy = fixture(base, "legacy-v1-target", "NfixIns", 2, false);
        legacyRows(legacy, false);
        changeProperty(legacy, "protocol", "experiment-a-v1");
        changeProperty(legacy, "profiles", "paper-v2");
        changeProperty(legacy, "fuzzy-selector", "FUZZY_PROFILE_MATCH");
        changeProperty(legacy, "selectors", "FUZZY_PROFILE_MATCH,MANUAL_WS,MANUAL_TARGET,RANDOM");
        changeProperty(legacy, "primary-baseline", "MANUAL_TARGET");
        Path legacyOutput = base.resolve("legacy-must-not-be-analysed");
        try {
            ExperimentAAnalysis.analyze(arguments(legacy, legacyOutput, 2));
            throw new AssertionError("Legacy target results must not become an importance study");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("legacy target diagnostic, not importance study"),
                    "Clear legacy-target rejection message");
        }
        check(!Files.exists(legacyOutput), "No output or silent legacy relabelling");

        Path oldFour = fixture(base, "old-four-selector-data", "NfixIns", 2, false);
        legacyRows(oldFour, false);
        invalidStudy(base, oldFour, "old-four-selector-data");
        Path retagged = fixture(base, "retagged-four-selector-target-data", "NfixIns", 2, false);
        legacyRows(retagged, true);
        invalidStudy(base, retagged, "retagged-four-selector-target-data");
        Path targetRow = fixture(base, "legacy-target-row", "NfixIns", 2, false);
        changeCell(targetRow, 2, "MANUAL_TARGET");
        invalidStudy(base, targetRow, "legacy-target-row");
        Path formal = fixture(base, "formal-importance-schema", "NfixIns", 30, false);
        ExperimentAAnalysis.Study study = ExperimentAAnalysis.Study.read(formal, 30, "formal_schema");
        check(study.runs.size() == 30 && study.rows.size() == 540, "Formal 30*6*3 input row contract");
    }

    private static void legacyRows(Path study, boolean retagFuzzy) throws IOException {
        List<String> original = Files.readAllLines(study.resolve("run_results.csv"), StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        List<String> targetRows = new ArrayList<>();
        lines.add(original.get(0));
        for (int i = 1; i < original.size(); i++) {
            List<String> cells = ExperimentAAnalysis.csvFields(original.get(i));
            if (cells.get(2).equals("FUZZY_IMPORTANCE") && !retagFuzzy) { cells.set(2, "FUZZY_PROFILE_MATCH"); }
            lines.add(String.join(",", cells));
            if (cells.get(2).equals("MANUAL_WS")) {
                cells.set(2, "MANUAL_TARGET");
                targetRows.add(String.join(",", cells));
            }
        }
        lines.addAll(targetRows);
        rewriteCsv(study, lines);
    }

    private static void diagnosticControlChecks(Path base) throws IOException {
        Path diagnostic = fixture(base, "diagnostic-controls", "NfixIns", 2, true, 3, 1, 2);
        Path output = base.resolve("diagnostic-control-analysis");
        ExperimentAAnalysis.analyze(arguments(diagnostic, output, 2, 3, 1, 2));
        Properties metadata = properties(output.resolve("analysis.properties"));
        check(metadata.getProperty("status").equals("COMPLETE")
                && metadata.getProperty("control-scope").equals("DIAGNOSTIC_NONDEFAULT_CONTROLS"),
                "Explicit diagnostic control scope");
        for (String[] control : new String[][]{{"pool-size", "3"}, {"top-k", "1"}, {"test-instances", "2"}}) {
            check(metadata.getProperty("expected-" + control[0]).equals(control[1])
                    && metadata.getProperty("study_1." + control[0]).equals(control[1]),
                    "Recorded expected and actual " + control[0]);
        }
        Map<String, String> summary = find(records(output.resolve("summary.csv")), "case_id", "C1",
                "selector", "FUZZY_IMPORTANCE", "metric", "heldout_objective");
        check(summary.get("run_count").equals("2") && summary.get("test_evaluation_count").equals("4")
                && summary.get("all_run_mean").equals("Infinity") && summary.get("test_failure_count").equals("1"),
                "Diagnostic evaluation counts and failures retained");
        check(properties(output.resolve("results_guide.properties")).getProperty("expected-controls")
                .contains("--expected-test-instances"), "Guide documents expected-control overrides");
        Path guarded = base.resolve("diagnostic-without-control-overrides");
        rejected(() -> ExperimentAAnalysis.analyze(arguments(diagnostic, guarded, 2)),
                "Formal default controls still reject diagnostic inputs");
        check(!Files.exists(guarded), "No implicit diagnostic fallback");

        int[][] mismatches = {{4, 1, 2}, {3, 2, 2}, {3, 1, 3}};
        for (int i = 0; i < mismatches.length; i++) {
            int[] controls = mismatches[i];
            Path rejectedOutput = base.resolve("diagnostic-control-mismatch-" + i);
            rejected(() -> ExperimentAAnalysis.analyze(arguments(diagnostic, rejectedOutput, 2,
                    controls[0], controls[1], controls[2])), "Manifest/expected-control mismatch " + i);
            check(!Files.exists(rejectedOutput), "Control mismatch must not create output");
        }
        int[] columns = {3, 9, 8};
        String[] wrongValues = {"2", "150", "3"};
        for (int i = 0; i < columns.length; i++) {
            Path study = fixture(base, "diagnostic-row-mismatch-" + i, "NfixIns", 2, true, 3, 1, 2);
            changeCell(study, columns[i], wrongValues[i]);
            Path rejectedOutput = base.resolve("diagnostic-row-rejected-" + i);
            rejected(() -> ExperimentAAnalysis.analyze(arguments(study, rejectedOutput, 2, 3, 1, 2)),
                    "Row must match diagnostic controls " + i);
            check(!Files.exists(rejectedOutput), "Diagnostic row mismatch must not create output");
        }
        int[][] invalid = {{0, 1, 2}, {3, 0, 2}, {3, 1, 0}, {1, 2, 2},
                {Integer.MAX_VALUE, Integer.MAX_VALUE, 2}};
        for (int i = 0; i < invalid.length; i++) {
            int[] controls = invalid[i];
            Path rejectedOutput = base.resolve("invalid-expected-controls-" + i);
            rejected(() -> ExperimentAAnalysis.analyze(arguments(diagnostic, rejectedOutput, 2,
                    controls[0], controls[1], controls[2])), "Positive compatible bounded expected controls " + i);
            check(!Files.exists(rejectedOutput), "Invalid expected controls must not create output");
        }
    }

    private static void statisticalChecks() throws IOException {
        PairedStatistics.SignedRankResult positive = PairedStatistics.signedRank(new double[]{1, 2, 3});
        near(positive.pValue, 0.25, "Three positive exact signs");
        near(positive.rankBiserial, -1, "Positive raw differences favour baseline");
        PairedStatistics.SignedRankResult negative = PairedStatistics.signedRank(new double[]{-1, -2, -3});
        near(negative.pValue, 0.25, "Three negative exact signs");
        near(negative.rankBiserial, 1, "Negative raw differences favour Fuzzy");
        PairedStatistics.SignedRankResult zeros = PairedStatistics.signedRank(new double[]{0, 1, 2, 0, 3});
        near(zeros.pValue, 0.25, "Wilcox zero removal");
        check(zeros.pairs == 5 && zeros.nonzeroPairs == 3 && zeros.zeroPairs == 2, "Zero counts");
        PairedStatistics.SignedRankResult tied = PairedStatistics.signedRank(new double[]{1, -1, 2});
        near(tied.wPlus, 4.5, "Tied average ranks");
        near(tied.pValue, 0.75, "Exact conditional tied-rank distribution");
        near(tied.rankBiserial, -0.5, "Tie-aware rank-biserial orientation");
        near(PairedStatistics.signedRank(new double[]{0, 1, -1, 2}).pValue, 0.75, "Ties plus zeros");
        near(PairedStatistics.signedRank(new double[]{1, 1, 1}).pValue, 0.25, "All absolute ranks tied");
        PairedStatistics.SignedRankResult allZero = PairedStatistics.signedRank(new double[]{0, -0.0, 0});
        near(allZero.pValue, 1, "All zero p");
        near(allZero.rankBiserial, 0, "All zero effect");
        check(allZero.status.equals("ALL_ZERO") && allZero.nonzeroPairs == 0, "Explicit all-zero status");
        double[] adjusted = PairedStatistics.holm(new double[]{0.01, 0.04, 0.03});
        near(adjusted[0], 0.03, "Holm first");
        near(adjusted[1], 0.06, "Holm monotone second");
        near(adjusted[2], 0.06, "Holm third");
        double[] excluded = PairedStatistics.holm(new double[]{0.01, Double.NaN, 0.04, 0.03});
        near(excluded[0], 0.04, "Non-testable contrast retains planned family size");
        check(Double.isNaN(excluded[1]), "Non-testable Holm stays unavailable");
        near(excluded[2], 0.09, "Planned-family Holm monotone adjustment");
        near(excluded[3], 0.09, "Planned-family Holm third finite p");
        double[] plannedFamily = new double[12];
        Arrays.fill(plannedFamily, Double.NaN);
        plannedFamily[0] = 0.01;
        plannedFamily[5] = 0.04;
        plannedFamily[11] = 0.03;
        double[] plannedAdjusted = PairedStatistics.holm(plannedFamily);
        near(plannedAdjusted[0], 0.12, "Mixed family retains all 12 planned hypotheses");
        near(plannedAdjusted[11], 0.33, "Mixed family retains 11 at the second step");
        near(plannedAdjusted[5], 0.40, "Mixed family retains 10 at the third step");
        check(Double.isNaN(plannedAdjusted[1]) && plannedAdjusted[0] > 0.05,
                "Untestable hypotheses stay unavailable and cannot make a finite contrast significant");
        double[] differences = {-5, -1, 0, 2, 8};
        PairedStatistics.Interval one = PairedStatistics.bootstrapMean(differences, 1000, 42, 0.05);
        PairedStatistics.Interval two = PairedStatistics.bootstrapMean(differences, 1000, 42, 0.05);
        check(Double.doubleToLongBits(one.lower) == Double.doubleToLongBits(two.lower)
                && Double.doubleToLongBits(one.upper) == Double.doubleToLongBits(two.upper),
                "Bootstrap reproducibility");
        check(one.lower <= one.upper && one.lower >= -5 && one.upper <= 8, "Bootstrap percentile bounds");
        PairedStatistics.Interval constant = PairedStatistics.bootstrapMean(new double[]{-2, -2}, 100, 4, 0.05);
        near(constant.lower, -2, "Constant bootstrap lower");
        near(constant.upper, -2, "Constant bootstrap upper");
        PairedStatistics.Interval singleton = PairedStatistics.bootstrapMean(new double[]{1}, 100, 4, 0.05);
        check(Double.isNaN(singleton.lower) && Double.isNaN(singleton.upper), "n<2 interval is unavailable");
        near(PairedStatistics.mean(new double[]{Double.MAX_VALUE, Double.MAX_VALUE}), Double.MAX_VALUE,
                "Overflow-resistant descriptive mean");
        near(PairedStatistics.sampleSd(new double[]{1, 3}), Math.sqrt(2), "Sample, not population SD");
        check(Double.isNaN(PairedStatistics.sampleSd(new double[]{1})), "Singleton sample SD unavailable");
        rejected(() -> PairedStatistics.signedRank(new double[201]), "Explicit exact-test size limit");
        rejected(() -> PairedStatistics.signedRank(new double[]{Double.POSITIVE_INFINITY}), "No nonfinite signed ranks");
        rejected(() -> PairedStatistics.bootstrapMean(new double[]{1, Double.NaN}, 100, 42, 0.05),
                "No selected finite-only bootstrap");
        rejected(() -> PairedStatistics.holm(new double[]{-0.1}), "Invalid p-value");
        rejected(() -> PairedStatistics.bootstrapMean(new double[]{1, 2}, 0, 42, 0.05), "Invalid sample count");
    }

    private static void failureChecks(Path base, Path failed) throws IOException {
        Path output = base.resolve("failure-analysis");
        ExperimentAAnalysis.analyze(arguments(failed, output, 2));
        List<Map<String, String>> comparisons = records(output.resolve("paired_comparisons.csv"));
        Map<String, String> manual = find(comparisons, "case_id", "C1",
                "baseline_selector", "MANUAL_WS", "metric", "heldout_objective");
        check(manual.get("inference_status").equals("NONFINITE_TEST_OUTCOME")
                && manual.get("run_pairs").equals("2") && manual.get("finite_pair_count").equals("1"),
                "Failure block preserved, not re-tested as a selected subset");
        for (String field : Arrays.asList("p_two_sided_exact", "holm_adjusted_p", "reject_holm",
                "rank_biserial_fuzzy_better_positive", "bootstrap_ci_lower", "bootstrap_ci_upper",
                "all_run_mean_difference", "signed_rank_nonzero_pairs")) {
            check(manual.get(field).equals("NA"), "Nonfinite objective suppresses " + field);
        }
        check(manual.get("family_planned_tests").equals("12")
                && manual.get("family_testable_tests").equals("10"), "Explicit testable-family count");
        check(manual.get("ties").equals("1") && manual.get("wins").equals("1")
                && manual.get("losses").equals("0"), "Identical infinities count as ties");
        check(manual.get("fuzzy_test_failure_count").equals("1")
                && manual.get("baseline_test_failure_count").equals("2"), "Actual failed evaluation counts");
        Map<String, String> complexity = find(comparisons, "case_id", "C1",
                "baseline_selector", "MANUAL_WS", "metric", "complexity");
        check(complexity.get("run_pairs").equals("2") && complexity.get("finite_pair_count").equals("2")
                && !complexity.get("p_two_sided_exact").equals("NA")
                && complexity.get("family_testable_tests").equals("12"), "All-run complexity test remains available");
        Map<String, String> sensitivity = find(comparisons, "case_id", "C1",
                "baseline_selector", "MANUAL_WS", "metric", "sensitivity");
        check(!sensitivity.get("p_two_sided_exact").equals("NA"), "All-run sensitivity remains available");
        Map<String, String> summary = find(records(output.resolve("summary.csv")), "case_id", "C1",
                "selector", "FUZZY_IMPORTANCE", "metric", "heldout_objective");
        check(summary.get("run_count").equals("2") && summary.get("all_run_mean").equals("Infinity")
                && summary.get("all_run_sample_sd").equals("NA") && summary.get("finite_run_count").equals("1")
                && !summary.get("finite_only_mean").equals("NA")
                && summary.get("finite_only_sample_sd").equals("NA")
                && summary.get("test_failure_runs").equals("1")
                && summary.get("test_evaluation_count").equals("300"), "Failure-aware descriptive summary");
        List<Map<String, String>> differences = records(output.resolve("paired_differences.csv"));
        check(differences.size() == 72, "No failed run omitted from per-run results");
        Map<String, String> infinity = find(differences, "case_id", "C1",
                "baseline_selector", "MANUAL_WS", "metric", "heldout_objective", "run_id", "4");
        check(infinity.get("fuzzy_minus_baseline").equals("NA")
                && infinity.get("lower_is_better_outcome").equals("TIE")
                && infinity.get("difference_status").equals("NONFINITE_TEST_OUTCOME"), "No Infinity-Infinity arithmetic");
    }

    private static void malformedChecks(Path base) throws IOException {
        Path missing = fixture(base, "missing-row", "NfixIns", 2, false);
        List<String> missingLines = Files.readAllLines(missing.resolve("run_results.csv"), StandardCharsets.UTF_8);
        missingLines.remove(missingLines.size() - 1);
        rewriteCsv(missing, missingLines);
        invalidStudy(base, missing, "missing-row");
        Path duplicate = fixture(base, "duplicate-row", "NfixIns", 2, false);
        List<String> duplicateLines = Files.readAllLines(duplicate.resolve("run_results.csv"), StandardCharsets.UTF_8);
        duplicateLines.set(2, duplicateLines.get(1));
        rewriteCsv(duplicate, duplicateLines);
        invalidStudy(base, duplicate, "duplicate-row");

        int[] columns = {0, 1, 2, 3, 4, 4, 5, 5, 5, 6, 6, 7, 7, 8, 8, 9};
        String[] invalidValues = {"99", "C7", "UNKNOWN", "2", "-1", "Infinity", "NaN", "Infinity",
                "-1", "-1", "Infinity", "-1", "NaN", "-1", "151", "149"};
        for (int i = 0; i < columns.length; i++) {
            Path study = fixture(base, "bad-cell-" + i, "NfixIns", 2, false);
            changeCell(study, columns[i], invalidValues[i]);
            invalidStudy(base, study, "bad-cell-" + i);
        }
        Path finiteFailure = fixture(base, "finite-failure", "NfixIns", 2, false);
        changeCell(finiteFailure, 8, "1");
        invalidStudy(base, finiteFailure, "finite-failure");
        Path wrongStatus = fixture(base, "wrong-failure-status", "NfixIns", 2, true);
        changeProperty(wrongStatus, "status", "COMPLETE");
        invalidStudy(base, wrongStatus, "wrong-failure-status");
        Path falseFailureStatus = fixture(base, "false-failure-status", "NfixIns", 2, false);
        changeProperty(falseFailureStatus, "status", "COMPLETE_WITH_TEST_FAILURES");
        invalidStudy(base, falseFailureStatus, "false-failure-status");

        String[][] invalidProperties = {{"protocol", "wrong"}, {"status", "PARTIAL"}, {"num-runs", "3"},
                {"profiles", "paper-v2"}, {"fuzzy-selector", "FUZZY_PROFILE_MATCH"},
                {"selectors", "FUZZY_IMPORTANCE,MANUAL_WS,MANUAL_TARGET,RANDOM"},
                {"selectors", "FUZZY_IMPORTANCE,MANUAL_WS"},
                {"selectors", "MANUAL_WS,FUZZY_IMPORTANCE,RANDOM"}, {"primary-baseline", "MANUAL_TARGET"},
                {"method", "QDGPf"}, {"scenario", "mean-tardiness-0.9-1.5"}, {"pool-size", "499"},
                {"top-k", "2"}, {"test-instances", "49"}, {"run-ids", "4,4"}, {"run-ids", "4,18"},
                {"results-sha256", "bad"}, {"results-sha256", repeat('0', 64)}};
        for (int i = 0; i < invalidProperties.length; i++) {
            Path study = fixture(base, "bad-manifest-" + i, "NfixIns", 2, false);
            changeProperty(study, invalidProperties[i][0], invalidProperties[i][1]);
            invalidStudy(base, study, "bad-manifest-" + i);
        }
        for (String property : Arrays.asList("protocol", "status", "method", "scenario", "run-ids",
                "profiles", "fuzzy-selector", "selectors", "primary-baseline",
                "num-runs", "pool-size", "top-k", "test-instances", "study-hash", "results-sha256")) {
            Path study = fixture(base, "missing-property-" + property, "NfixIns", 2, false);
            changeProperty(study, property, null);
            invalidStudy(base, study, "missing-property-" + property);
        }
        Path duplicateProperty = fixture(base, "duplicate-property", "NfixIns", 2, false);
        Files.write(duplicateProperty.resolve("manifest.properties"),
                Arrays.asList("protocol=experiment-a-v1"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        invalidStudy(base, duplicateProperty, "duplicate-property");
        Path absentFile = fixture(base, "missing-file", "NfixIns", 2, false);
        Files.delete(absentFile.resolve("run_results.csv"));
        invalidStudy(base, absentFile, "missing-file");
        Path malformed = fixture(base, "malformed-quote", "NfixIns", 2, false);
        List<String> quotedLines = Files.readAllLines(malformed.resolve("run_results.csv"), StandardCharsets.UTF_8);
        quotedLines.set(1, "\"unterminated");
        rewriteCsv(malformed, quotedLines);
        invalidStudy(base, malformed, "malformed-quote");
        Path header = fixture(base, "wrong-header", "NfixIns", 2, false);
        List<String> headerLines = Files.readAllLines(header.resolve("run_results.csv"), StandardCharsets.UTF_8);
        headerLines.set(0, headerLines.get(0) + ",extra");
        rewriteCsv(header, headerLines);
        invalidStudy(base, header, "wrong-header");
        Path extraColumn = fixture(base, "extra-column", "NfixIns", 2, false);
        List<String> extraLines = Files.readAllLines(extraColumn.resolve("run_results.csv"), StandardCharsets.UTF_8);
        extraLines.set(1, extraLines.get(1) + ",extra");
        rewriteCsv(extraColumn, extraLines);
        invalidStudy(base, extraColumn, "extra-column");
        Path hash = fixture(base, "changed-results", "NfixIns", 2, false);
        Files.write(hash.resolve("run_results.csv"), Arrays.asList(""), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        invalidStudy(base, hash, "changed-results");
    }

    private static void pathChecks(Path base, Path normal, Path second) throws IOException {
        Path inside = normal.resolve("forbidden-analysis");
        rejected(() -> ExperimentAAnalysis.analyze(arguments(normal, inside, 2)), "Output inside input");
        check(!Files.exists(inside), "No input-root output directory");
        Path insideSecond = second.resolve("forbidden-analysis");
        rejected(() -> ExperimentAAnalysis.analyze(new String[]{"--input", normal.toString(),
                "--input", second.toString(), "--output-root", insideSecond.toString(),
                "--expected-runs", "2", "--bootstrap-samples", "20"}), "Output inside ANY input");
        check(!Files.exists(insideSecond), "Second study remains read-only");
        Path existing = Files.createDirectory(base.resolve("existing-output"));
        Files.write(existing.resolve("sentinel"), Arrays.asList("keep"), StandardCharsets.UTF_8);
        rejected(() -> ExperimentAAnalysis.analyze(arguments(normal, existing, 2)), "Existing output rejection");
        check(Files.readAllLines(existing.resolve("sentinel"), StandardCharsets.UTF_8).equals(Arrays.asList("keep")),
                "Existing output unchanged");
        Path bad = fixture(base, "second-input-incomplete", "NrotateInsNoReeva", 2, false);
        changeProperty(bad, "status", "RUNNING");
        Path invalidAll = base.resolve("must-not-exist");
        rejected(() -> ExperimentAAnalysis.analyze(new String[]{"--input", normal.toString(),
                "--input", bad.toString(), "--output-root", invalidAll.toString(),
                "--expected-runs", "2", "--bootstrap-samples", "20"}), "Validate ALL inputs before output");
        check(!Files.exists(invalidAll), "No output for invalid second input");
        Path duplicateOutput = base.resolve("duplicate-input-analysis");
        rejected(() -> ExperimentAAnalysis.analyze(new String[]{"--input", normal.toString(),
                "--input", normal.toString(), "--output-root", duplicateOutput.toString(),
                "--expected-runs", "2", "--bootstrap-samples", "20"}), "Duplicate study rejection");
        check(!Files.exists(duplicateOutput), "No repeated-study inference");
        Path wrongExpected = base.resolve("default-expects-thirty");
        rejected(() -> ExperimentAAnalysis.analyze(new String[]{"--input", normal.toString(),
                "--output-root", wrongExpected.toString()}), "Default expects thirty actual runs");
        check(!Files.exists(wrongExpected), "No partial/default-count output");
        rejected(() -> ExperimentAAnalysis.analyze(new String[]{"--input", normal.toString(),
                "--output-root", base.resolve("bad-alpha").toString(), "--alpha", "NaN"}), "Invalid alpha");
    }

    private static void unchangedChecks(Path base) throws IOException {
        Path csv = fixture(base, "mutation-check-csv", "NfixIns", 2, false);
        ExperimentAAnalysis.Study snapshot = ExperimentAAnalysis.Study.read(csv, 2, "snapshot");
        Files.write(csv.resolve("run_results.csv"), Arrays.asList(""), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        rejected(snapshot::verifyUnchanged, "Post-read CSV hash verification");
        Path manifest = fixture(base, "mutation-check-manifest", "NfixIns", 2, false);
        ExperimentAAnalysis.Study manifestSnapshot = ExperimentAAnalysis.Study.read(manifest, 2, "snapshot");
        changeProperty(manifest, "study-hash", "modified-after-validation");
        rejected(manifestSnapshot::verifyUnchanged, "Post-read manifest hash verification");
    }

    private static Path fixture(Path base, String name, String method, int count, boolean failures) throws IOException {
        return fixture(base, name, method, count, failures, 500, 3, 50);
    }

    private static Path fixture(Path base, String name, String method, int count, boolean failures,
                                int poolSize, int topK, int testInstances) throws IOException {
        Path study = Files.createDirectory(base.resolve(name));
        int[] runs = new int[count];
        int[] firstRuns = {4, 17, 29};
        for (int i = 0; i < count; i++) { runs[i] = i < 3 ? firstRuns[i] : 29 + 13 * (i - 2); }
        try (RevisionCsv csv = new RevisionCsv(study.resolve("run_results.csv"),
                (Object[]) ExperimentAAnalysis.HEADER.split(","))) {
            for (int index = 0; index < count; index++) {
                for (int profile = 1; profile <= 6; profile++) {
                    for (int selector = 0; selector < ExperimentAAnalysis.SELECTORS.length; selector++) {
                        boolean failed = failures && index == 0 && profile == 1 && (selector == 0 || selector == 1);
                        int failedCount = failed ? selector == 0 ? 1 : Math.min(2, topK * testInstances) : 0;
                        csv.row(runs[index], "C" + profile, ExperimentAAnalysis.SELECTORS[selector], topK,
                                20 + profile + selector,
                                failed ? Double.POSITIVE_INFINITY : 100.0 + runs[index] + profile + selector * 2,
                                10.0 + profile + selector, 1.0 + index + selector * 0.25,
                                failedCount, topK * testInstances);
                    }
                }
            }
        }
        Properties manifest = new Properties();
        manifest.setProperty("protocol", "experiment-a-v2-importance");
        manifest.setProperty("profiles", "importance-v1");
        manifest.setProperty("fuzzy-selector", "FUZZY_IMPORTANCE");
        manifest.setProperty("selectors", "FUZZY_IMPORTANCE,MANUAL_WS,RANDOM");
        manifest.setProperty("primary-baseline", "MANUAL_WS");
        manifest.setProperty("status", failures ? "COMPLETE_WITH_TEST_FAILURES" : "COMPLETE");
        manifest.setProperty("method", method);
        manifest.setProperty("scenario", "mean-tardiness-0.85-1.5");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) { ids.add(Integer.toString(runs[i])); }
        manifest.setProperty("run-ids", String.join(",", ids));
        manifest.setProperty("num-runs", Integer.toString(count));
        manifest.setProperty("pool-size", Integer.toString(poolSize));
        manifest.setProperty("top-k", Integer.toString(topK));
        manifest.setProperty("test-instances", Integer.toString(testInstances));
        manifest.setProperty("study-hash", "synthetic-" + name);
        manifest.setProperty("results-sha256", RevisionCsv.fileHash(study.resolve("run_results.csv")));
        writeProperties(study.resolve("manifest.properties"), manifest);
        return study;
    }

    private static String[] arguments(Path input, Path output, int runs) {
        return new String[]{"--input", input.toString(), "--output-root", output.toString(),
                "--expected-runs", Integer.toString(runs), "--bootstrap-samples", "200",
                "--bootstrap-seed", "42"};
    }

    private static String[] arguments(Path input, Path output, int runs,
                                      int poolSize, int topK, int testInstances) {
        List<String> options = new ArrayList<>(Arrays.asList(arguments(input, output, runs)));
        options.addAll(Arrays.asList("--expected-pool-size", Integer.toString(poolSize),
                "--expected-top-k", Integer.toString(topK),
                "--expected-test-instances", Integer.toString(testInstances)));
        return options.toArray(new String[0]);
    }

    private static void invalidStudy(Path base, Path study, String label) throws IOException {
        Path output = base.resolve("rejected-output-" + label);
        rejected(() -> ExperimentAAnalysis.analyze(arguments(study, output, 2)), label);
        check(!Files.exists(output), "Invalid input must not create output: " + label);
    }

    private static void changeCell(Path study, int column, String value) throws IOException {
        List<String> lines = Files.readAllLines(study.resolve("run_results.csv"), StandardCharsets.UTF_8);
        List<String> cells = ExperimentAAnalysis.csvFields(lines.get(1));
        cells.set(column, value);
        lines.set(1, String.join(",", cells));
        rewriteCsv(study, lines);
    }

    private static void rewriteCsv(Path study, List<String> lines) throws IOException {
        Files.write(study.resolve("run_results.csv"), lines, StandardCharsets.UTF_8);
        changeProperty(study, "results-sha256", RevisionCsv.fileHash(study.resolve("run_results.csv")));
    }

    private static void changeProperty(Path study, String key, String value) throws IOException {
        Path path = study.resolve("manifest.properties");
        Properties manifest = properties(path);
        if (value == null) { manifest.remove(key); } else { manifest.setProperty(key, value); }
        writeProperties(path, manifest);
    }

    private static Properties properties(Path path) throws IOException {
        Properties result = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }

    private static void writeProperties(Path path, Properties properties) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(writer, "Synthetic Experiment A check fixture");
        }
    }

    private static List<Map<String, String>> records(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> header = ExperimentAAnalysis.csvFields(lines.get(0));
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = ExperimentAAnalysis.csvFields(lines.get(i));
            check(cells.size() == header.size(), "Output column count: " + path);
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) { row.put(header.get(column), cells.get(column)); }
            result.add(row);
        }
        return result;
    }

    private static Map<String, String> find(List<Map<String, String>> rows, String... pairs) {
        Map<String, String> found = null;
        for (Map<String, String> row : rows) {
            boolean match = true;
            for (int i = 0; i < pairs.length; i += 2) {
                match &= pairs[i + 1].equals(row.get(pairs[i]));
            }
            if (match) {
                check(found == null, "Duplicate matching analysis row");
                found = row;
            }
        }
        check(found != null, "Missing matching analysis row: " + Arrays.toString(pairs));
        return found;
    }

    private static String repeat(char value, int count) {
        char[] result = new char[count];
        Arrays.fill(result, value);
        return new String(result);
    }

    private interface CheckedAction { void run() throws IOException; }

    private static void rejected(CheckedAction action, String label) throws IOException {
        try {
            action.run();
        } catch (IllegalArgumentException | IOException expected) {
            return;
        }
        throw new AssertionError("Expected strict rejection: " + label);
    }

    private static void near(double actual, double expected, String label) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= 1.0e-12 * Math.max(1, Math.abs(expected)),
                label + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
