package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;

/** Small synthetic checks only; every fixture and report stays under the supplied root. */
public final class WholeLibraryAnalysisChecks {
    private WholeLibraryAnalysisChecks() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("Usage: WholeLibraryAnalysisChecks <fixture-parent-directory>");
            return;
        }
        if (args.length != 1) { throw new IllegalArgumentException("Expected one fixture parent directory"); }
        runChecks(Paths.get(args[0]));
        System.out.println("WHOLE-LIBRARY ANALYSIS CHECKS PASSED");
    }

    public static void runChecks(Path root) throws IOException {
        numericChecks();
        plotRenderingChecks();
        Files.createDirectories(root);
        Path base = Files.createDirectory(root.resolve("whole-library-analysis-checks"));
        Path source = fixture(base, "normal", false);
        String manifestHash = RevisionCsv.fileHash(source.resolve("manifest.properties"));
        String membersHash = RevisionCsv.fileHash(source.resolve("members.csv"));
        WholeLibraryAnalysis.Data data = WholeLibraryAnalysis.Data.read(source, 2, 2);
        check(data.libraries.size() == 4, "Both regimes, exactly two libraries each");
        WholeLibraryAnalysis.Description p = description(data, "NfixIns", 0, 0, WholeLibraryAnalysis.P);
        check(p.total == 7 && p.finite.length == 4 && p.unknown == 2 && p.failed == 1 && p.undefined == 0,
                "Unknown and failed validation quality have separate denominators");
        near(p.q.min, 0, "Full finite-subset min");
        near(p.q.q1, 7.5, "Full finite-subset type-7 Q1");
        near(p.q.median, 10, "Full finite-subset median including retained duplicates");
        near(p.q.q3, 15, "Full finite-subset type-7 Q3");
        near(p.q.max, 30, "Full finite-subset max");
        WholeLibraryAnalysis.Description s = description(data, "NfixIns", 0, 0, WholeLibraryAnalysis.S);
        WholeLibraryAnalysis.Description logS = description(data, "NfixIns", 0, 0, WholeLibraryAnalysis.LOG_S);
        check(s.finite.length == 5 && s.undefined == 2 && logS.finite.length == 5 && logS.undefined == 2,
                "Raw/transformed sensitivity share explicit undefined denominator");
        near(s.q.median, 3, "Primary raw-S median");
        near(s.q.iqr, 4, "Primary raw-S type-7 IQR");
        near(description(data, "NfixIns", 0, 1, WholeLibraryAnalysis.LOG_S).q.q1, Math.log10(4) / 4,
                "Transform members before computing type-7 log-S quantiles, not raw-S quantiles afterwards");
        WholeLibraryAnalysis.Description c = description(data, "NfixIns", 0, 0, WholeLibraryAnalysis.C);
        check(c.finite.length == 7 && c.unknown == 0 && c.failed == 0 && c.undefined == 0, "All complexity retained");
        near(c.q.median, 6, "Complexity uses full data, not plotting sample");
        check(description(data, "NfixIns", 0, 1, WholeLibraryAnalysis.P).total == 2, "Pool subset only");
        samplingChecks(base, data);

        Path output = base.resolve("analysis");
        WholeLibraryAnalysis.analyze(arguments(source, output));
        outputChecks(output);
        check(manifestHash.equals(RevisionCsv.fileHash(source.resolve("manifest.properties")))
                && membersHash.equals(RevisionCsv.fileHash(source.resolve("members.csv"))), "Read-only input analysis");
        emptyQualityChecks(base);
        producerCounterChecks(base);
        malformedChecks(base);
        pathChecks(base, source);
        unchangedChecks(base);
    }

    private static void numericChecks() throws IOException {
        double[] values = {4, 1, 3, 2};
        near(WholeLibraryAnalysis.type7(values, 0), 1, "Type-7 min");
        near(WholeLibraryAnalysis.type7(values, 0.25), 1.75, "Type-7 Q1");
        near(WholeLibraryAnalysis.type7(values, 0.5), 2.5, "Type-7 median");
        near(WholeLibraryAnalysis.type7(values, 0.75), 3.25, "Type-7 Q3");
        near(WholeLibraryAnalysis.type7(values, 1), 4, "Type-7 max");
        near(WholeLibraryAnalysis.type7(new double[]{7}, 0.75), 7, "Singleton quantile");
        check(Double.isNaN(WholeLibraryAnalysis.type7(new double[0], 0.5)), "Empty finite sample has NA median");
        near(WholeLibraryAnalysis.type7(new double[]{2, 2, 2}, 0.25), 2, "Tied quantile");
        near(WholeLibraryAnalysis.type7(new double[]{Double.MAX_VALUE, Double.MAX_VALUE}, 0.25),
                Double.MAX_VALUE, "Large constant quantiles stay finite");
        check(Arrays.equals(values, new double[]{4, 1, 3, 2}), "Quantiles do not mutate inputs");
        near(WholeLibraryAnalysis.logSensitivity(0), 0, "S=0 transforms to zero");
        near(WholeLibraryAnalysis.logSensitivity(9), 1, "log10(1+S)");
        check(Double.isFinite(WholeLibraryAnalysis.logSensitivity(Double.MAX_VALUE)), "Stable large-S transformation");
        check(WholeLibraryAnalysis.logSensitivity(1.0e-20) > 0, "Stable small-S transformation");
        WholeLibraryAnalysis.Ecdf ecdf = new WholeLibraryAnalysis.Ecdf(
                Arrays.asList(new double[]{0}, new double[]{10, 10, 10}, new double[0]));
        check(ecdf.libraries == 2 && ecdf.emptyLibraries == 1 && ecdf.finiteMembers == 4, "ECDF eligible library counts");
        check(ecdf.x.length == 2, "ECDF groups ties exactly");
        near(ecdf.y[0], 0.5, "Unequal member counts cannot overpower the smaller library");
        near(ecdf.y[1], 1, "Run-balanced ECDF ends at one");
        WholeLibraryAnalysis.Ecdf empty = new WholeLibraryAnalysis.Ecdf(Arrays.asList(new double[0], new double[0]));
        check(empty.x.length == 0 && empty.y.length == 0 && empty.libraries == 0 && empty.emptyLibraries == 2,
                "No fabricated empty ECDF");
        rejected(() -> WholeLibraryAnalysis.type7(new double[]{1, Double.NaN}, 0.5), "No implicit finite filtering");
        rejected(() -> WholeLibraryAnalysis.type7(values, -0.1), "Invalid quantile probability");
        rejected(() -> new WholeLibraryAnalysis.Ecdf(Arrays.asList(new double[]{Double.POSITIVE_INFINITY})),
                "ECDF only accepts explicitly finite conditional samples");
    }

    private static void plotRenderingChecks() throws IOException {
        int p = WholeLibraryAnalysis.P;
        near(WholeLibraryPlots.displayCoordinate(p, 0), 0, "Display log-P supports zero quality");
        near(WholeLibraryPlots.displayCoordinate(p, 99), 2, "Display log10(1+P) coordinate");
        check(WholeLibraryPlots.displayCoordinate(p, 1.0e-20) > 0, "Display log-P retains small positive quality");
        check(Double.isFinite(WholeLibraryPlots.displayCoordinate(p, Double.MAX_VALUE)),
                "Display log-P supports extreme finite quality without clipping");
        near(WholeLibraryPlots.displayCoordinate(WholeLibraryAnalysis.C, 180), 180, "Complexity coordinates stay raw");
        near(WholeLibraryPlots.displayCoordinate(WholeLibraryAnalysis.LOG_S, 12.5), 12.5,
                "Already-transformed sensitivity is not logged twice");
        double min = WholeLibraryPlots.displayCoordinate(p, 150);
        double max = WholeLibraryPlots.displayCoordinate(p, 11900);
        WholeLibraryPlots.Range range = WholeLibraryPlots.Range.of(min, max);
        check(range.lo > 0 && range.lo < min && range.hi > max,
                "Log-P range follows observed min/max with padding, not a forced zero origin");
        for (double raw : new double[]{150, 200, 11900}) {
            double pixel = range.pixel(WholeLibraryPlots.displayCoordinate(p, raw), 0, 1000);
            check(pixel > 0 && pixel < 1000, "All plotted quality values, including the tail, fit inside padded bounds");
        }
        WholeLibraryPlots.Range zero = WholeLibraryPlots.Range.of(0, max);
        check(zero.pixel(0, 0, 1000) >= 0 && zero.pixel(max, 0, 1000) < 1000, "Zero quality is not discarded");
        WholeLibraryPlots.Range constant = WholeLibraryPlots.Range.of(0, 0);
        check(Double.isFinite(constant.pixel(0, 0, 1000)), "Constant zero-quality panel has valid display bounds");
        check(WholeLibraryPlots.seriesLabel("NfixIns", 0, 500).equals("QDGPf: whole retained"),
                "Friendly whole-retained QDGPf legend");
        check(WholeLibraryPlots.seriesLabel("NrotateInsNoReeva", 1, 500).equals("QDGP: top 500 pool"),
                "Friendly top-500 QDGP legend");
        check(WholeLibraryPlots.seriesLabel("NfixIns", 1, 2).equals("QDGPf: top 2 pool"),
                "Diagnostic pool labels preserve their actual size");
        check(WholeLibraryPlots.font(24).getSize() == 36 && WholeLibraryPlots.font(26).getSize() == 39
                && WholeLibraryPlots.font(29).getSize() == 44,
                "Publication tick/legend and axis-label font sizes increase by approximately 1.5x");
        rejected(() -> WholeLibraryPlots.displayCoordinate(p, Double.POSITIVE_INFINITY), "Nonfinite quality is not plotted");
        rejected(() -> WholeLibraryPlots.seriesLabel("UNKNOWN", 0, 500), "No invented figure-regime alias");
    }

    private static void samplingChecks(Path base, WholeLibraryAnalysis.Data data) throws IOException {
        List<WholeLibraryAnalysis.Sample> a = WholeLibraryAnalysis.samples(data, 1, 42);
        List<WholeLibraryAnalysis.Sample> b = WholeLibraryAnalysis.samples(data, 1, 42);
        check(sampleKeys(a).equals(sampleKeys(b)), "Deterministic capped samples");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (WholeLibraryAnalysis.Sample sample : a) {
            check(Double.isFinite(sample.member.quality) && Double.isFinite(sample.member.value(sample.yMetric)),
                    "Scatter pairs must be finite");
            String key = sample.member.method + ":" + sample.member.run + ":" + sample.scope + ":" + sample.yMetric;
            counts.merge(key, 1, Integer::sum);
        }
        for (int count : counts.values()) { check(count <= 1, "Per-library/scope/panel cap, not a pooled cap"); }
        check(counts.size() == 16, "Every eligible library has a sample in both scopes and panels");
        Path reversed = fixture(base, "reversed-order", false);
        List<List<String>> rows = memberRows(reversed);
        Collections.reverse(rows);
        rewrite(reversed, rows);
        check(sampleKeys(a).equals(sampleKeys(WholeLibraryAnalysis.samples(
                WholeLibraryAnalysis.Data.read(reversed, 2, 2), 1, 42))), "Row order cannot change deterministic plot sampling");
    }

    private static void outputChecks(Path output) throws IOException {
        Properties metadata = properties(output.resolve("analysis.properties"));
        check(metadata.getProperty("status").equals("COMPLETE")
                && metadata.getProperty("quality-scope").equals("AVAILABLE_SUBSET_ONLY")
                && metadata.getProperty("native-grid-coverage").equals("NOT_VERIFIABLE_NO_CELL_COORDINATES"),
                "Explicit reporting scope and successful metadata");
        check(metadata.getProperty("ALL_RETAINED.quality-unknown").equals("4")
                && metadata.getProperty("ALL_RETAINED.quality-failed").equals("2")
                && metadata.getProperty("ALL_RETAINED.sensitivity-undefined").equals("4"),
                "Observed global unknown/failure/undefined counts, not invented full quality");
        check(metadata.getProperty("sensitivity-primary-scale").equals("RAW_S")
                && metadata.getProperty("log-sensitivity-role").equals("MONOTONIC_DISPLAY_ONLY")
                && metadata.getProperty("input.retained-members").equals("18")
                && metadata.getProperty("input.scheduling-simulation-count").equals("0"),
                "Validated producer provenance and explicit primary sensitivity scale");
        List<Map<String, String>> libraries = records(output.resolve("library_summary.csv"));
        check(libraries.size() == 32, "4 libraries * 2 scopes * 4 metrics");
        Map<String, String> p = find(libraries, "method", "NfixIns", "run_id", "0", "scope", "ALL_RETAINED",
                "metric", "validation_objective");
        check(p.get("scope_member_count").equals("7") && p.get("finite_count").equals("4")
                && p.get("unknown_count").equals("2") && p.get("failure_count").equals("1"),
                "Exported quality denominators");
        List<Map<String, String>> regimes = records(output.resolve("regime_summary.csv"));
        check(regimes.size() == 16, "2 regimes * 2 scopes * 4 metrics");
        Map<String, String> aggregate = find(regimes, "method", "NfixIns", "scope", "ALL_RETAINED",
                "metric", "validation_objective");
        near(Double.parseDouble(aggregate.get("median_of_run_medians")), 80, "Equal-library median of run medians");
        near(Double.parseDouble(aggregate.get("median_of_run_iqrs")), 28.75, "Median of run IQRs");
        check(aggregate.get("total_members").equals("9") && aggregate.get("finite_members").equals("6")
                && aggregate.get("unknown_members").equals("2") && aggregate.get("failure_members").equals("1"),
                "Regime totals retain unknown and failed quality members");
        Map<String, String> rawS = find(regimes, "method", "NfixIns", "scope", "ALL_RETAINED", "metric", "sensitivity");
        near(Double.parseDouble(rawS.get("median_of_run_medians")), 3.75, "Type-7 aggregation of raw-S run medians");
        near(Double.parseDouble(rawS.get("median_of_run_iqrs")), 4.25, "Type-7 aggregation of raw-S run IQRs");
        near(Double.parseDouble(rawS.get("run_median_min")), 3, "Raw-S run median minimum");
        near(Double.parseDouble(rawS.get("run_median_max")), 4.5, "Raw-S run median maximum");
        Map<String, String> point = find(records(output.resolve("ecdf_points.csv")), "method", "NfixIns",
                "scope", "ALL_RETAINED", "metric", "validation_objective", "x", "30.0");
        near(Double.parseDouble(point.get("run_balanced_ecdf")), 0.5, "Exported ECDF is run balanced, not member pooled");
        Map<String, String> tail = find(records(output.resolve("ecdf_points.csv")), "method", "NfixIns",
                "scope", "ALL_RETAINED", "metric", "validation_objective", "x", "200.0");
        near(Double.parseDouble(tail.get("run_balanced_ecdf")), 1, "Raw ECDF export retains the finite tail");
        List<Map<String, String>> statuses = records(output.resolve("status_counts.csv"));
        Map<String, String> status = find(statuses, "method", "NfixIns", "run_id", "ALL_LIBRARIES",
                "scope", "ALL_RETAINED", "dimension", "quality_status", "status", "NOT_EVALUATED_STORED_FAILURE");
        check(status.get("member_count").equals("2") && status.get("scope_member_denominator").equals("9"),
                "Regime status counts are member counts, not evaluation counts");
        Map<String, String> reason = find(statuses, "method", "NfixIns", "run_id", "0", "scope", "ALL_RETAINED",
                "dimension", "sensitivity_reason", "status", "overflow, at \"division\"\nprobe=0");
        check(reason.get("member_count").equals("1"), "Quoted multiline reasons survive CSV round trip");
        Map<String, String> rawSample = find(records(output.resolve("plot_samples.csv")), "method", "NfixIns",
                "run_id", "1", "scope", "ALL_RETAINED", "candidate_id", "1", "y_metric", "complexity");
        check(rawSample.get("x_metric").equals("validation_objective")
                && rawSample.get("x_value").equals("200.0"), "Scatter CSV P remains raw despite log-P rendering");
        check(Files.size(output.resolve("descriptive_table.tex")) > 0
                && Files.size(output.resolve("figure_captions.tex")) > 0, "Table and captions");
        Properties guide = properties(output.resolve("results_guide.properties"));
        check(guide.getProperty("sensitivity-primary-scale").equals("RAW_S"),
                "Guide treats log sensitivity as display, not the primary robust scale");
        check(guide.getProperty("quality-display-coordinate").equals("log10(1+P)")
                && guide.getProperty("quality-csv-scale").equals("RAW_P")
                && guide.getProperty("figure-rendering").equals("publication-v2-logP"),
                "Guide explicitly separates publication coordinates from raw numerical data");
        String captions = new String(Files.readAllBytes(output.resolve("figure_captions.tex")), StandardCharsets.UTF_8);
        check(captions.contains("QDGPf") && captions.contains("QDGP") && captions.contains("\\log_{10}(1+P)")
                && !captions.contains("NfixIns") && !captions.contains("NrotateInsNoReeva"),
                "Publication captions use friendly names and explain display-only log-P");
        BufferedImage ecdf = ImageIO.read(output.resolve("run_balanced_ecdf.png").toFile());
        check(ecdf != null && ecdf.getWidth() == 4200 && ecdf.getHeight() == 1800, "High-resolution three-panel ECDF PNG");
        ecdf.flush();
        BufferedImage scatter = ImageIO.read(output.resolve("quality_attribute_scatter.png").toFile());
        check(scatter != null && scatter.getWidth() == 3600 && scatter.getHeight() == 1800, "High-resolution two-panel scatter PNG");
        scatter.flush();
        for (String file : Arrays.asList("library_summary.csv", "regime_summary.csv", "status_counts.csv",
                "ecdf_points.csv", "plot_samples.csv", "run_balanced_ecdf.png", "quality_attribute_scatter.png")) {
            check(RevisionCsv.fileHash(output.resolve(file)).equals(metadata.getProperty(file + ".sha256")),
                    "Persistent output integrity hash: " + file);
        }
    }

    private static void emptyQualityChecks(Path base) throws IOException {
        Path empty = fixture(base, "empty-quality-library", true);
        WholeLibraryAnalysis.Data data = WholeLibraryAnalysis.Data.read(empty, 2, 2);
        WholeLibraryAnalysis.Description p = description(data, "NfixIns", 1, 0, WholeLibraryAnalysis.P);
        check(p.total == 2 && p.finite.length == 0 && p.unknown == 2 && Double.isNaN(p.q.median),
                "Empty quality library remains present with NA statistics");
        WholeLibraryAnalysis.Ecdf ecdf = new WholeLibraryAnalysis.Ecdf(Arrays.asList(
                description(data, "NfixIns", 0, 0, WholeLibraryAnalysis.P).finite, p.finite));
        check(ecdf.libraries == 1 && ecdf.emptyLibraries == 1, "Conditional ECDF explicitly excludes and counts empty libraries");
    }

    private static void producerCounterChecks(Path base) throws IOException {
        Path single = fixture(base, "producer-sized-single-run", false);
        List<List<String>> rows = memberRows(single);
        rows.removeIf(row -> !row.get(1).equals("0") || Integer.parseInt(row.get(2)) >= 5);
        for (List<String> row : rows) {
            if (row.get(2).equals("1")) { row.set(11, "false"); }
        }
        rewrite(single, rows);
        declareCounts(single, 1);
        WholeLibraryAnalysis.Data data = WholeLibraryAnalysis.Data.read(single, 1, 1);
        check(data.libraries.size() == 2 && data.libraries.get(0).members.size() == 5
                && data.libraries.get(1).members.size() == 5 && data.libraries.get(0).poolCount == 1,
                "Producer smoke controls: one run, five retained members and one pool member per regime");
        WholeLibraryAnalysis.Description quality = description(data, "NfixIns", 0, 0, WholeLibraryAnalysis.P);
        check(quality.total == 5 && quality.finite.length == 3 && quality.unknown == 1 && quality.failed == 1,
                "Producer-sized P denominator distinguishes unknown from failed");
        Path outlier = fixture(base, "raw-sensitivity-outlier", false);
        List<List<String>> outlierRows = memberRows(outlier);
        outlierRows.get(4).set(7, "1.0E12");
        rewrite(outlier, outlierRows);
        WholeLibraryAnalysis.Description raw = description(WholeLibraryAnalysis.Data.read(outlier, 2, 2),
                "NfixIns", 0, 0, WholeLibraryAnalysis.S);
        near(raw.q.median, 3, "Raw-S median remains robust to an extreme tail");
        near(raw.q.iqr, 4, "Raw-S IQR remains robust to an extreme tail");
        near(raw.q.max, 1.0e12, "Raw-S maximum still reports the full observed tail");
        for (String[] counter : new String[][]{{"retained-members", "19"}, {"pool-members", "9"},
                {"quality-not-evaluated", "5"}, {"quality-failed", "3"}, {"undefined-sensitivity", "5"},
                {"expected-runs-per-regime", "1"}, {"scheduling-simulation-count", "1"}}) {
            Path source = fixture(base, "counter-mismatch-" + counter[0], false);
            changeProperty(source, counter[0], counter[1]);
            invalid(base, source, "counter-mismatch-" + counter[0]);
        }
    }

    private static void malformedChecks(Path base) throws IOException {
        int[] columns = {0, 1, 2, 3, 4, 5, 6, 6, 7, 7, 8, 9, 11, 12};
        String[] invalid = {"UNKNOWN", "2", "", "", "", "NaN", "Infinity", "-1", "Infinity", "",
                "UNKNOWN", "UNKNOWN", "TRUE", "UNKNOWN"};
        for (int i = 0; i < columns.length; i++) {
            Path source = fixture(base, "bad-cell-" + i, false);
            List<List<String>> rows = memberRows(source);
            rows.get(0).set(columns[i], invalid[i]);
            rewrite(source, rows);
            invalid(base, source, "bad-cell-" + i);
        }
        Path badUnknown = fixture(base, "unknown-with-number", false);
        List<List<String>> unknown = memberRows(badUnknown);
        unknown.get(2).set(5, "0");
        rewrite(badUnknown, unknown);
        invalid(base, badUnknown, "unknown-with-number");
        Path badUndefined = fixture(base, "undefined-with-number", false);
        List<List<String>> undefined = memberRows(badUndefined);
        undefined.get(2).set(7, "0");
        rewrite(badUndefined, undefined);
        invalid(base, badUndefined, "undefined-with-number");
        Path badFailure = fixture(base, "failure-with-finite", false);
        List<List<String>> failure = memberRows(badFailure);
        failure.get(3).set(5, "0");
        rewrite(badFailure, failure);
        invalid(base, badFailure, "failure-with-finite");
        Path duplicate = fixture(base, "duplicate-candidate", false);
        List<List<String>> duplicates = memberRows(duplicate);
        duplicates.add(new ArrayList<>(duplicates.get(0)));
        rewrite(duplicate, duplicates);
        invalid(base, duplicate, "duplicate-candidate");
        Path missing = fixture(base, "missing-library", false);
        List<List<String>> missingRows = memberRows(missing);
        missingRows.removeIf(row -> row.get(0).equals("NrotateInsNoReeva") && row.get(1).equals("1"));
        rewrite(missing, missingRows);
        invalid(base, missing, "missing-library");
        Path pool = fixture(base, "wrong-pool-count", false);
        List<List<String>> poolRows = memberRows(pool);
        poolRows.get(0).set(11, "false");
        rewrite(pool, poolRows);
        invalid(base, pool, "wrong-pool-count");
        for (String[] property : new String[][]{{"protocol", "wrong"}, {"status", "PARTIAL"},
                {"status", "COMPLETE_WITH_TEST_FAILURES"}, {"members-sha256", "bad"}, {"members-sha256", null}}) {
            Path source = fixture(base, "bad-manifest-" + (property[1] == null ? "missing-hash" : property[1]), false);
            changeProperty(source, property[0], property[1]);
            invalid(base, source, source.getFileName().toString());
        }
        Path hash = fixture(base, "hash-mismatch", false);
        Files.write(hash.resolve("members.csv"), Arrays.asList(""), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        invalid(base, hash, "hash-mismatch");
        Path header = fixture(base, "wrong-header", false);
        Files.write(header.resolve("members.csv"), Arrays.asList(WholeLibraryAnalysis.HEADER + ",extra"), StandardCharsets.UTF_8);
        refreshHash(header);
        invalid(base, header, "wrong-header");
        Path quote = fixture(base, "unclosed-quote", false);
        Files.write(quote.resolve("members.csv"), Arrays.asList(WholeLibraryAnalysis.HEADER, "\"unterminated"), StandardCharsets.UTF_8);
        refreshHash(quote);
        invalid(base, quote, "unclosed-quote");
        for (String key : Arrays.asList("protocol", "status")) {
            Path absent = fixture(base, "missing-" + key, false);
            changeProperty(absent, key, null);
            invalid(base, absent, "missing-" + key);
        }
        Path duplicateProperty = fixture(base, "duplicate-property", false);
        Files.write(duplicateProperty.resolve("manifest.properties"), Arrays.asList("status=COMPLETE"),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        invalid(base, duplicateProperty, "duplicate-property");
        Path missingFile = fixture(base, "missing-members", false);
        Files.delete(missingFile.resolve("members.csv"));
        invalid(base, missingFile, "missing-members");
    }

    private static void pathChecks(Path base, Path source) throws IOException {
        Path inside = source.resolve("forbidden");
        rejected(() -> WholeLibraryAnalysis.analyze(arguments(source, inside)), "Output inside B root");
        check(!Files.exists(inside), "No source-root output created");
        Path existing = Files.createDirectory(base.resolve("existing"));
        Files.write(existing.resolve("sentinel"), Arrays.asList("preserve"), StandardCharsets.UTF_8);
        rejected(() -> WholeLibraryAnalysis.analyze(arguments(source, existing)), "Existing output rejection");
        check(Files.readAllLines(existing.resolve("sentinel"), StandardCharsets.UTF_8).equals(Arrays.asList("preserve")),
                "No overwritten results");
        Path defaultPool = base.resolve("implicit-small-pool");
        rejected(() -> WholeLibraryAnalysis.analyze(new String[]{"--input", source.toString(), "--output-root",
                defaultPool.toString(), "--expected-runs", "2"}), "Default still requires 500 pool members per library");
        check(!Files.exists(defaultPool), "No silent pool-size relaxation");
        rejected(() -> WholeLibraryAnalysis.analyze(new String[]{"--input", source.toString(), "--output-root",
                base.resolve("bad-runs").toString(), "--expected-runs", "31"}), "Run IDs bounded by protocol");
    }

    private static void unchangedChecks(Path base) throws IOException {
        Path source = fixture(base, "post-read-csv-mutation", false);
        WholeLibraryAnalysis.Data data = WholeLibraryAnalysis.Data.read(source, 2, 2);
        Files.write(source.resolve("members.csv"), Arrays.asList(""), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        rejected(data::verifyUnchanged, "Post-read members hash verification");
        Path manifest = fixture(base, "post-read-manifest-mutation", false);
        WholeLibraryAnalysis.Data snapshot = WholeLibraryAnalysis.Data.read(manifest, 2, 2);
        changeProperty(manifest, "extra-provenance", "changed");
        rejected(snapshot::verifyUnchanged, "Post-read manifest hash verification");
    }

    private static Path fixture(Path base, String name, boolean emptyQualityLibrary) throws IOException {
        Path root = Files.createDirectory(base.resolve(name));
        List<List<String>> rows = new ArrayList<>();
        for (String method : WholeLibraryAnalysis.METHODS) {
            rows.add(row(method, 0, "0", "tree-0", "0", "0", "2", "0", "VALIDATION_FINITE", "FINITE", true, "A_CACHE", ""));
            rows.add(row(method, 0, "1", "tree-1", "1", "10", "4", "3", "VALIDATION_FINITE", "FINITE", true, "A_CACHE", ""));
            rows.add(row(method, 0, "2", "tree-2", "Infinity", "", "6", "", "NOT_EVALUATED_STORED_FAILURE",
                    "UNDEFINED_NUMERIC", false, "B_COMPUTED", "overflow, at \"division\"\nprobe=0"));
            rows.add(row(method, 0, "3", "tree-3", "3", "Infinity", "8", "7", "VALIDATION_FAILED",
                    "FINITE", false, "B_COMPUTED", ""));
            rows.add(row(method, 0, "4", "tree-4", "4", "30", "10", "15", "VALIDATION_FINITE",
                    "FINITE", false, "B_COMPUTED", ""));
            rows.add(row(method, 0, "5", "tree-5", "Infinity", "", "12", "", "NOT_EVALUATED_STORED_FAILURE",
                    "UNDEFINED_NUMERIC", false, "B_COMPUTED", ""));
            rows.add(row(method, 0, "6", "tree-1", "1", "10", "4", "3", "VALIDATION_FINITE",
                    "FINITE", false, "B_HASH_CACHE", ""));
            for (int id = 0; id < 2; id++) {
                boolean unknown = emptyQualityLibrary && method.equals("NfixIns");
                rows.add(row(method, 1, Integer.toString(id), "run1-tree-" + id, unknown ? "Infinity" : "1",
                        unknown ? "" : Integer.toString((id + 1) * 100), id == 0 ? "1" : "3", id == 0 ? "0" : "9",
                        unknown ? "NOT_EVALUATED_STORED_FAILURE" : "VALIDATION_FINITE", "FINITE", true, "A_CACHE", ""));
            }
        }
        Properties manifest = new Properties();
        manifest.setProperty("protocol", "whole-library-attributes-v1");
        manifest.setProperty("status", "COMPLETE");
        writeProperties(root.resolve("manifest.properties"), manifest);
        rewrite(root, rows);
        declareCounts(root, 2);
        return root;
    }

    private static void declareCounts(Path source, int runs) throws IOException {
        List<List<String>> rows = memberRows(source);
        long unknown = 0, failed = 0, undefined = 0, pool = 0;
        for (List<String> row : rows) {
            if (row.get(8).equals("NOT_EVALUATED_STORED_FAILURE")) { unknown++; }
            if (row.get(8).equals("VALIDATION_FAILED")) { failed++; }
            if (row.get(9).equals("UNDEFINED_NUMERIC")) { undefined++; }
            if (row.get(11).equals("true")) { pool++; }
        }
        Path path = source.resolve("manifest.properties");
        Properties manifest = properties(path);
        manifest.setProperty("retained-members", Integer.toString(rows.size()));
        manifest.setProperty("pool-members", Long.toString(pool));
        manifest.setProperty("quality-not-evaluated", Long.toString(unknown));
        manifest.setProperty("quality-failed", Long.toString(failed));
        manifest.setProperty("undefined-sensitivity", Long.toString(undefined));
        manifest.setProperty("expected-runs-per-regime", Integer.toString(runs));
        manifest.setProperty("scheduling-simulation-count", "0");
        writeProperties(path, manifest);
    }

    private static List<String> row(String method, int run, String id, String hash, String stored, String p,
                                    String c, String s, String quality, String sensitivity, boolean pool,
                                    String metricSource, String reason) {
        return new ArrayList<>(Arrays.asList(method, Integer.toString(run), id, hash, stored, p, c, s,
                quality, sensitivity, pool ? "ELIGIBLE" : "", Boolean.toString(pool), metricSource, reason));
    }

    private static WholeLibraryAnalysis.Description description(WholeLibraryAnalysis.Data data, String method,
                                                               int run, int scope, int metric) {
        for (WholeLibraryAnalysis.Description d : WholeLibraryAnalysis.describe(data)) {
            if (d.library.method.equals(method) && d.library.run == run && d.scope == scope && d.metric == metric) { return d; }
        }
        throw new AssertionError("Missing library description");
    }

    private static List<String> sampleKeys(List<WholeLibraryAnalysis.Sample> samples) {
        List<String> result = new ArrayList<>();
        for (WholeLibraryAnalysis.Sample sample : samples) {
            result.add(sample.member.method + ":" + sample.member.run + ":" + sample.scope + ":"
                    + sample.yMetric + ":" + sample.member.id);
        }
        return result;
    }

    private static String[] arguments(Path input, Path output) {
        return new String[]{"--input", input.toString(), "--output-root", output.toString(),
                "--expected-runs", "2", "--expected-pool-size", "2"};
    }

    private static List<List<String>> memberRows(Path source) throws IOException {
        List<List<String>> result = new ArrayList<>();
        try (WholeLibraryAnalysis.CsvReader reader = new WholeLibraryAnalysis.CsvReader(source.resolve("members.csv"))) {
            List<String> cells;
            while ((cells = reader.read()) != null) { result.add(cells); }
        }
        return result;
    }

    private static void rewrite(Path source, List<List<String>> rows) throws IOException {
        Path csv = source.resolve("members.csv");
        Files.deleteIfExists(csv);
        try (RevisionCsv writer = new RevisionCsv(csv, (Object[]) WholeLibraryAnalysis.HEADER.split(","))) {
            for (List<String> row : rows) { writer.row(row.toArray()); }
        }
        refreshHash(source);
    }

    private static void refreshHash(Path source) throws IOException {
        changeProperty(source, "members-sha256", RevisionCsv.fileHash(source.resolve("members.csv")));
    }

    private static void changeProperty(Path source, String key, String value) throws IOException {
        Path path = source.resolve("manifest.properties");
        Properties properties = properties(path);
        if (value == null) { properties.remove(key); } else { properties.setProperty(key, value); }
        writeProperties(path, properties);
    }

    private static Properties properties(Path path) throws IOException {
        Properties result = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }

    private static void writeProperties(Path path, Properties properties) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(writer, "Whole-library analysis synthetic fixture");
        }
    }

    private static List<Map<String, String>> records(Path path) throws IOException {
        String header;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { header = reader.readLine(); }
        List<String> names = Arrays.asList(header.split(",", -1));
        List<Map<String, String>> result = new ArrayList<>();
        try (WholeLibraryAnalysis.CsvReader reader = new WholeLibraryAnalysis.CsvReader(path, header)) {
            List<String> cells;
            while ((cells = reader.read()) != null) {
                check(cells.size() == names.size(), "Output column count");
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < names.size(); i++) { row.put(names.get(i), cells.get(i)); }
                result.add(row);
            }
        }
        return result;
    }

    private static Map<String, String> find(List<Map<String, String>> rows, String... pairs) {
        Map<String, String> result = null;
        for (Map<String, String> row : rows) {
            boolean match = true;
            for (int i = 0; i < pairs.length; i += 2) { match &= pairs[i + 1].equals(row.get(pairs[i])); }
            if (match) { check(result == null, "Duplicate matching report row"); result = row; }
        }
        check(result != null, "Missing report row: " + Arrays.toString(pairs));
        return result;
    }

    private static void invalid(Path base, Path source, String label) throws IOException {
        Path output = base.resolve("rejected-" + label);
        rejected(() -> WholeLibraryAnalysis.analyze(arguments(source, output)), label);
        check(!Files.exists(output), "Invalid input cannot create successful output: " + label);
    }

    private interface CheckedAction { void run() throws IOException; }
    private static void rejected(CheckedAction action, String label) throws IOException {
        try { action.run(); }
        catch (IOException | IllegalArgumentException expected) { return; }
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
