package mengxu.algorithm.MAPElites.FuzzySystem.revision;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Result analysis only: never evaluates, selects, repairs, or pools study runs. */
public final class ExperimentAAnalysis {
    static final String HEADER = "run_id,case_id,method,selected_count,mean_validation_objective,"
            + "mean_test_objective,mean_complexity,mean_sensitivity,test_failure_count,test_evaluation_count";
    static final String PROTOCOL = "experiment-a-v2-importance";
    static final String PROFILES = "importance-v1";
    static final String[] SELECTORS = {"FUZZY_IMPORTANCE", "MANUAL_WS", "RANDOM"};
    static final String[] IMPORTANCE_WEIGHTS = {"5,1,1", "1,1,5", "1,5,1", "1,1,1", "5,1,3", "5,1,5"};
    static final String[] METRICS = {"heldout_objective", "complexity", "sensitivity"};
    private static final String SCENARIO = "mean-tardiness-0.85-1.5";
    private static final int FAMILY_SIZE = 12;

    private ExperimentAAnalysis() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("Usage: ExperimentAAnalysis --input <studyRoot> [--input <secondRoot>]"
                    + " --output-root <NEW directory> [--expected-runs 30]"
                    + " [--expected-pool-size 500] [--expected-top-k 3] [--expected-test-instances 50] [--alpha 0.05]"
                    + " [--bootstrap-samples 10000] [--bootstrap-seed 42]");
            System.out.println("Requires complete " + PROTOCOL + " studies with profiles=" + PROFILES
                    + " and selectors=" + String.join(",", SELECTORS) + ". Results remain separate by"
                    + " regime/scenario/study-hash; MANUAL_WS is primary, RANDOM is a reference."
                    + " Legacy target diagnostics are not importance studies."
                    + " Nondefault expected controls explicitly opt into diagnostic-size inputs.");
            return;
        }
        analyze(args);
    }

    public static void analyze(String[] args) throws IOException {
        Options options = Options.parse(args);
        List<Study> studies = new ArrayList<>();
        Set<Path> roots = new HashSet<>();
        Set<String> identities = new HashSet<>();
        Path output = options.output;
        for (Path requested : options.inputs) {
            Study study = Study.read(requested, options.expectedRuns, options.expectedPoolSize,
                    options.expectedTopK, options.expectedTestInstances, "study_" + (studies.size() + 1));
            if (!roots.add(study.root)) {
                throw new IllegalArgumentException("Duplicate input study root: " + study.root);
            }
            if (!identities.add(study.identity())) {
                throw new IllegalArgumentException("Duplicate method/scenario/study-hash; refusing repeated study: "
                        + study.root);
            }
            output = RevisionConfiguration.checkedOutput(output, study.root);
            studies.add(study);
        }
        // All parsing, block-completeness checks and inference finish before creating output.
        List<Comparison> comparisons = new ArrayList<>();
        for (Study study : studies) {
            for (int metric = 0; metric < METRICS.length; metric++) {
                List<Comparison> family = new ArrayList<>();
                for (int profile = 1; profile <= 6; profile++) {
                    for (int selector = 1; selector < SELECTORS.length; selector++) {
                        family.add(new Comparison(study, "C" + profile, SELECTORS[selector], metric, options));
                    }
                }
                double[] pValues = new double[family.size()];
                int tested = 0;
                for (int i = 0; i < pValues.length; i++) {
                    pValues[i] = family.get(i).pValue;
                    if (!Double.isNaN(pValues[i])) { tested++; }
                }
                double[] adjusted = PairedStatistics.holm(pValues);
                for (int i = 0; i < family.size(); i++) {
                    family.get(i).adjustedP = adjusted[i];
                    family.get(i).familyTested = tested;
                }
                comparisons.addAll(family);
            }
        }
        for (Study study : studies) {
            study.verifyUnchanged();
            output = RevisionConfiguration.checkedOutput(output, study.root);
        }
        Files.createDirectories(output.getParent());
        Files.createDirectory(output);
        Properties metadata = metadata(studies, options);
        Path manifest = output.resolve("analysis.properties");
        try {
            metadata.setProperty("status", "RUNNING");
            store(manifest, metadata);
            writeSummary(output.resolve("summary.csv"), studies);
            writeComparisons(output.resolve("paired_comparisons.csv"), comparisons, options);
            writeDifferences(output.resolve("paired_differences.csv"), comparisons);
            writeLatex(output.resolve("descriptive_tables.tex"), studies);
            writeGuide(output.resolve("results_guide.properties"));
            for (String file : Arrays.asList("summary.csv", "paired_comparisons.csv",
                    "paired_differences.csv", "descriptive_tables.tex", "results_guide.properties")) {
                metadata.setProperty(file + ".sha256", RevisionCsv.fileHash(output.resolve(file)));
            }
            for (Study study : studies) { study.verifyUnchanged(); }
            metadata.setProperty("status", "COMPLETE");
            store(manifest, metadata);
        } catch (IOException | RuntimeException | Error failure) {
            metadata.setProperty("status", "FAILED");
            metadata.setProperty("failure", failure.getClass().getName() + ": " + failure.getMessage());
            try {
                store(manifest, metadata);
            } catch (IOException | RuntimeException metadataFailure) {
                failure.addSuppressed(metadataFailure);
            }
            throw failure;
        }
    }

    private static final class Options {
        final List<Path> inputs = new ArrayList<>();
        Path output;
        int expectedRuns = 30;
        int expectedPoolSize = 500;
        int expectedTopK = 3;
        int expectedTestInstances = 50;
        int bootstrapSamples = 10000;
        long bootstrapSeed = 42;
        double alpha = 0.05;

        static Options parse(String[] args) {
            Options result = new Options();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                if (!Arrays.asList("--input", "--output-root", "--expected-runs", "--alpha",
                        "--expected-pool-size", "--expected-top-k", "--expected-test-instances",
                        "--bootstrap-samples", "--bootstrap-seed").contains(key)) {
                    throw new IllegalArgumentException("Unknown analysis option: " + key);
                }
                if (!key.equals("--input") && !seen.add(key)) {
                    throw new IllegalArgumentException("Duplicate analysis option: " + key);
                }
                if (++i >= args.length || args[i].startsWith("--") || args[i].isEmpty()) {
                    throw new IllegalArgumentException(key + " needs a value");
                }
                String value = args[i];
                switch (key) {
                    case "--input": result.inputs.add(Paths.get(value)); break;
                    case "--output-root": result.output = Paths.get(value); break;
                    case "--expected-runs": result.expectedRuns = integer(value, key); break;
                    case "--expected-pool-size": result.expectedPoolSize = integer(value, key); break;
                    case "--expected-top-k": result.expectedTopK = integer(value, key); break;
                    case "--expected-test-instances": result.expectedTestInstances = integer(value, key); break;
                    case "--alpha": result.alpha = Double.parseDouble(value); break;
                    case "--bootstrap-samples": result.bootstrapSamples = integer(value, key); break;
                    case "--bootstrap-seed": result.bootstrapSeed = Long.parseLong(value); break;
                    default: throw new AssertionError(key);
                }
            }
            if (result.inputs.isEmpty() || result.output == null) {
                throw new IllegalArgumentException("--input and --output-root are required");
            }
            if (result.expectedRuns < 1 || result.expectedRuns > PairedStatistics.MAX_PAIRS) {
                throw new IllegalArgumentException("--expected-runs must lie in [1,"
                        + PairedStatistics.MAX_PAIRS + "]");
            }
            if (result.expectedPoolSize < 1 || result.expectedTopK < 1 || result.expectedTestInstances < 1
                    || result.expectedTopK > result.expectedPoolSize) {
                throw new IllegalArgumentException("Expected controls must be positive, with expected-top-k"
                        + " <= expected-pool-size");
            }
            if ((long) result.expectedTopK * result.expectedTestInstances > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("expected-top-k * expected-test-instances exceeds the supported count range");
            }
            if (result.bootstrapSamples < 1 || result.bootstrapSamples > 1000000) {
                throw new IllegalArgumentException("--bootstrap-samples must lie in [1,1000000]");
            }
            if (!Double.isFinite(result.alpha) || result.alpha <= 0 || result.alpha >= 1) {
                throw new IllegalArgumentException("--alpha must lie strictly between zero and one");
            }
            return result;
        }
    }

    static final class Study {
        final Path root;
        final String id;
        final String method;
        final String scenario;
        final String studyHash;
        final String manifestHash;
        final String resultsHash;
        final String status;
        final int poolSize;
        final int topK;
        final int testInstances;
        final List<Integer> runs;
        final Map<String, Row> rows = new LinkedHashMap<>();

        private Study(Path root, String id, Properties manifest, List<Integer> runs,
                      String manifestHash, String resultsHash) {
            this.root = root;
            this.id = id;
            method = required(manifest, "method");
            scenario = required(manifest, "scenario");
            studyHash = required(manifest, "study-hash");
            status = required(manifest, "status");
            poolSize = integer(required(manifest, "pool-size"), "pool-size");
            topK = integer(required(manifest, "top-k"), "top-k");
            testInstances = integer(required(manifest, "test-instances"), "test-instances");
            this.runs = runs;
            this.manifestHash = manifestHash;
            this.resultsHash = resultsHash;
        }

        String identity() { return method + "\u0000" + scenario + "\u0000" + studyHash; }

        static Study read(Path requested, int expectedRuns, String id) throws IOException {
            return read(requested, expectedRuns, 500, 3, 50, id);
        }

        static Study read(Path requested, int expectedRuns, int expectedPoolSize, int expectedTopK,
                          int expectedTestInstances, String id) throws IOException {
            if (!Files.isDirectory(requested)) {
                throw new IllegalArgumentException("Input study directory does not exist or is not a directory: "
                        + requested.toAbsolutePath().normalize()
                        + ". The current importance-study preset uses experiment-A-importance-v2, not "
                        + "experiment-A-validation-v1. Select 'Fuzzy A v2 - 20 Compare Results' in IDEA "
                        + "or update --input to the actual completed study root. "
                        + "Preflight does not create study results; do not create an empty directory to bypass this check.");
            }
            Path root = requested.toRealPath();
            Path manifestPath = root.resolve("manifest.properties");
            Path resultsPath = root.resolve("run_results.csv");
            if (!Files.isRegularFile(manifestPath)) {
                throw new IllegalArgumentException("No study manifest in " + root
                        + ". Run the Study configuration first and wait for COMPLETE or COMPLETE_WITH_TEST_FAILURES; "
                        + "an empty directory or a preflight is not a completed study.");
            }
            String manifestHash = RevisionCsv.fileHash(manifestPath);
            Properties manifest = new UniqueProperties();
            try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
                manifest.load(reader);
            }
            String protocol = required(manifest, "protocol");
            if (protocol.equals("experiment-a-v1")) {
                throw new IllegalArgumentException("legacy target diagnostic, not importance study: " + root
                        + "; expected protocol=" + PROTOCOL);
            }
            equal(protocol, PROTOCOL, "protocol");
            equal(required(manifest, "profiles"), PROFILES, "profiles");
            equal(required(manifest, "fuzzy-selector"), SELECTORS[0], "fuzzy-selector");
            equal(required(manifest, "selectors"), String.join(",", SELECTORS), "selectors");
            equal(required(manifest, "primary-baseline"), "MANUAL_WS", "primary-baseline");
            String status = required(manifest, "status");
            if (!status.equals("COMPLETE") && !status.equals("COMPLETE_WITH_TEST_FAILURES")) {
                throw new IllegalArgumentException("Study is not complete: " + root + " (" + status + ")");
            }
            String method = required(manifest, "method");
            if (!method.equals("NfixIns") && !method.equals("NrotateInsNoReeva")) {
                throw new IllegalArgumentException("Unexpected Experiment A regime: " + method);
            }
            equal(required(manifest, "scenario"), SCENARIO, "scenario");
            exactInteger(manifest, "num-runs", expectedRuns);
            exactInteger(manifest, "pool-size", expectedPoolSize);
            exactInteger(manifest, "top-k", expectedTopK);
            exactInteger(manifest, "test-instances", expectedTestInstances);
            required(manifest, "study-hash");
            TreeSet<Integer> runIds = new TreeSet<>();
            for (String token : required(manifest, "run-ids").split(",", -1)) {
                int run = integer(token.trim(), "run-ids");
                if (!runIds.add(run)) { throw new IllegalArgumentException("Duplicate manifest run ID: " + run); }
            }
            if (runIds.size() != expectedRuns) {
                throw new IllegalArgumentException("Manifest run ID count does not equal expected-runs: " + root);
            }
            String declaredHash = required(manifest, "results-sha256");
            if (!declaredHash.matches("[a-fA-F0-9]{64}")) {
                throw new IllegalArgumentException("Malformed results-sha256: " + root);
            }
            String actualHash = RevisionCsv.fileHash(resultsPath);
            if (!actualHash.equalsIgnoreCase(declaredHash)) {
                throw new IllegalArgumentException("run_results.csv SHA-256 mismatch: " + root);
            }
            Study study = new Study(root, id, manifest, new ArrayList<>(runIds), manifestHash, actualHash);
            boolean anyFailure = false;
            try (BufferedReader reader = Files.newBufferedReader(resultsPath, StandardCharsets.UTF_8)) {
                equal(reader.readLine(), HEADER, "run_results.csv exact header");
                String line;
                int lineNumber = 1;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    List<String> fields = csvFields(line);
                    if (fields.size() != 10) {
                        throw new IllegalArgumentException("Expected 10 columns at " + root + ":" + lineNumber);
                    }
                    Row row = new Row(fields, study.topK, Math.multiplyExact(study.topK, study.testInstances));
                    if (!runIds.contains(row.run) || !row.profile.matches("C[1-6]")
                            || !Arrays.asList(SELECTORS).contains(row.selector)) {
                        throw new IllegalArgumentException("Unexpected run/case/selector at " + root + ":" + lineNumber);
                    }
                    if (study.rows.put(key(row.run, row.profile, row.selector), row) != null) {
                        throw new IllegalArgumentException("Duplicate run/case/selector at " + root + ":" + lineNumber);
                    }
                    anyFailure |= row.failures > 0;
                }
            }
            for (int run : study.runs) {
                for (int profile = 1; profile <= 6; profile++) {
                    for (String selector : SELECTORS) { study.row(run, "C" + profile, selector); }
                }
            }
            if (study.rows.size() != expectedRuns * 6 * SELECTORS.length) {
                throw new IllegalArgumentException("Incorrect result row count: " + root);
            }
            equal(status, anyFailure ? "COMPLETE_WITH_TEST_FAILURES" : "COMPLETE", "study failure status");
            study.verifyUnchanged();
            return study;
        }

        Row row(int run, String profile, String selector) {
            Row result = rows.get(key(run, profile, selector));
            if (result == null) {
                throw new IllegalArgumentException("Missing complete run block: " + root + ", run=" + run
                        + ", case=" + profile + ", method=" + selector);
            }
            return result;
        }

        void verifyUnchanged() throws IOException {
            if (!manifestHash.equals(RevisionCsv.fileHash(root.resolve("manifest.properties")))
                    || !resultsHash.equals(RevisionCsv.fileHash(root.resolve("run_results.csv")))) {
                throw new IOException("Input study changed during analysis: " + root);
            }
        }
    }

    private static final class UniqueProperties extends Properties {
        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) { throw new IllegalArgumentException("Duplicate manifest property: " + key); }
            return super.put(key, value);
        }
    }

    private static final class Row {
        final int run;
        final String profile;
        final String selector;
        final double objective;
        final double complexity;
        final double sensitivity;
        final int failures;
        final int evaluations;

        Row(List<String> cells, int topK, int expectedEvaluations) {
            run = integer(cells.get(0), "run_id");
            profile = cells.get(1);
            selector = cells.get(2);
            if (integer(cells.get(3), "selected_count") != topK) {
                throw new IllegalArgumentException("selected_count must equal top-k=" + topK);
            }
            finiteNonnegative(cells.get(4), "mean_validation_objective");
            objective = number(cells.get(5), "mean_test_objective");
            complexity = finiteNonnegative(cells.get(6), "mean_complexity");
            sensitivity = finiteNonnegative(cells.get(7), "mean_sensitivity");
            failures = integer(cells.get(8), "test_failure_count");
            evaluations = integer(cells.get(9), "test_evaluation_count");
            if (evaluations != expectedEvaluations || failures > evaluations) {
                throw new IllegalArgumentException("Expected test_evaluation_count=" + expectedEvaluations
                        + " and 0<=failed<=total");
            }
            if (Double.isNaN(objective) || objective < 0
                    || (failures > 0) != (objective == Double.POSITIVE_INFINITY)) {
                throw new IllegalArgumentException("Test failures must be preserved as +Infinity, and only failures may be infinite");
            }
        }

        double value(int metric) {
            return metric == 0 ? objective : metric == 1 ? complexity : sensitivity;
        }
    }

    private static final class Description {
        final double[] finite;
        final int runs;
        final int failureRuns;
        final long failures;
        final long evaluations;
        final double mean;
        final double sd;

        Description(Study study, String profile, String selector, int metric) {
            runs = study.runs.size();
            List<Double> values = new ArrayList<>();
            int failedRuns = 0;
            long failed = 0;
            long evaluated = 0;
            for (int run : study.runs) {
                Row row = study.row(run, profile, selector);
                if (Double.isFinite(row.value(metric))) { values.add(row.value(metric)); }
                if (row.failures > 0) { failedRuns++; }
                failed += row.failures;
                evaluated += row.evaluations;
            }
            finite = doubles(values);
            failureRuns = failedRuns;
            failures = failed;
            evaluations = evaluated;
            mean = finite.length == runs ? PairedStatistics.mean(finite) : Double.POSITIVE_INFINITY;
            sd = finite.length == runs ? PairedStatistics.sampleSd(finite) : Double.NaN;
        }
    }

    private static final class Comparison {
        final Study study;
        final String profile;
        final String baseline;
        final int metric;
        final double[] differences;
        final double[] finiteDifferences;
        final int wins;
        final int losses;
        final int ties;
        final int fuzzyFailureRuns;
        final int baselineFailureRuns;
        final int failurePairRuns;
        final long fuzzyFailures;
        final long baselineFailures;
        final long seed;
        final String status;
        final PairedStatistics.SignedRankResult rank;
        final double pValue;
        final double effect;
        final double lower;
        final double upper;
        double adjustedP = Double.NaN;
        int familyTested;

        Comparison(Study study, String profile, String baseline, int metric, Options options) {
            this.study = study;
            this.profile = profile;
            this.baseline = baseline;
            this.metric = metric;
            seed = comparisonSeed(options.bootstrapSeed,
                    study.identity() + "\u0000" + profile + "\u0000" + baseline + "\u0000" + METRICS[metric]);
            differences = new double[study.runs.size()];
            List<Double> finite = new ArrayList<>();
            int win = 0, loss = 0, tie = 0, fuzzyRuns = 0, otherRuns = 0, failedPairs = 0;
            long fuzzyFailed = 0, otherFailed = 0;
            for (int i = 0; i < study.runs.size(); i++) {
                int run = study.runs.get(i);
                Row fuzzy = study.row(run, profile, SELECTORS[0]);
                Row other = study.row(run, profile, baseline);
                double a = fuzzy.value(metric);
                double b = other.value(metric);
                if (a < b) { win++; } else if (a > b) { loss++; } else { tie++; }
                if (fuzzy.failures > 0) { fuzzyRuns++; }
                if (other.failures > 0) { otherRuns++; }
                if (fuzzy.failures > 0 || other.failures > 0) { failedPairs++; }
                fuzzyFailed += fuzzy.failures;
                otherFailed += other.failures;
                differences[i] = Double.isFinite(a) && Double.isFinite(b) ? a - b : Double.NaN;
                if (Double.isFinite(differences[i])) { finite.add(differences[i]); }
            }
            finiteDifferences = doubles(finite);
            wins = win;
            losses = loss;
            ties = tie;
            fuzzyFailureRuns = fuzzyRuns;
            baselineFailureRuns = otherRuns;
            failurePairRuns = failedPairs;
            fuzzyFailures = fuzzyFailed;
            baselineFailures = otherFailed;
            if (finiteDifferences.length != differences.length) {
                status = "NONFINITE_TEST_OUTCOME";
                rank = null;
                pValue = effect = lower = upper = Double.NaN;
            } else {
                rank = PairedStatistics.signedRank(differences);
                status = rank.status;
                pValue = rank.pValue;
                effect = rank.rankBiserial;
                PairedStatistics.Interval interval = PairedStatistics.bootstrapMean(
                        differences, options.bootstrapSamples, seed, options.alpha);
                lower = interval.lower;
                upper = interval.upper;
            }
        }
    }

    private static void writeSummary(Path path, List<Study> studies) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(path, "study_id", "regime", "scenario", "study_hash",
                "case_id", "selector", "metric", "run_count", "test_failure_runs", "test_failure_count",
                "test_evaluation_count", "nonfinite_metric_runs", "all_run_mean", "all_run_sample_sd",
                "finite_run_count", "finite_only_mean", "finite_only_sample_sd", "descriptive_status")) {
            for (Study study : studies) {
                for (int profile = 1; profile <= 6; profile++) {
                    for (String selector : SELECTORS) {
                        for (int metric = 0; metric < METRICS.length; metric++) {
                            Description d = new Description(study, "C" + profile, selector, metric);
                            csv.row(study.id, study.method, study.scenario, study.studyHash, "C" + profile,
                                    selector, METRICS[metric], d.runs, d.failureRuns, d.failures, d.evaluations,
                                    d.runs - d.finite.length, numeric(d.mean), numeric(d.sd), d.finite.length,
                                    numeric(PairedStatistics.mean(d.finite)), numeric(PairedStatistics.sampleSd(d.finite)),
                                    d.finite.length == d.runs ? "ALL_FINITE" : "NONFINITE_TEST_OUTCOME");
                        }
                    }
                }
            }
        }
    }

    private static void writeComparisons(Path path, List<Comparison> comparisons, Options options) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(path, "study_id", "regime", "scenario", "study_hash",
                "case_id", "fuzzy_selector", "baseline_selector", "baseline_role", "metric",
                "run_pairs", "wins", "losses", "ties", "fuzzy_test_failure_runs", "baseline_test_failure_runs",
                "any_test_failure_pair_runs", "fuzzy_test_failure_count", "baseline_test_failure_count",
                "finite_pair_count", "all_run_mean_difference", "all_run_sample_sd_difference",
                "finite_only_mean_difference", "finite_only_sample_sd_difference", "signed_rank_nonzero_pairs",
                "signed_rank_zero_pairs", "w_plus", "p_two_sided_exact", "rank_biserial_fuzzy_better_positive",
                "bootstrap_ci_lower", "bootstrap_ci_upper", "bootstrap_confidence", "bootstrap_samples",
                "bootstrap_seed", "inference_status", "family_id", "family_planned_tests", "family_testable_tests",
                "holm_adjusted_p", "alpha", "reject_holm")) {
            for (Comparison c : comparisons) {
                boolean complete = c.rank != null;
                Study s = c.study;
                csv.row(s.id, s.method, s.scenario, s.studyHash, c.profile, SELECTORS[0], c.baseline,
                        baselineRole(c.baseline), METRICS[c.metric], c.differences.length, c.wins, c.losses, c.ties,
                        c.fuzzyFailureRuns, c.baselineFailureRuns, c.failurePairRuns, c.fuzzyFailures,
                        c.baselineFailures, c.finiteDifferences.length,
                        complete ? numeric(PairedStatistics.mean(c.differences)) : "NA",
                        complete ? numeric(PairedStatistics.sampleSd(c.differences)) : "NA",
                        numeric(PairedStatistics.mean(c.finiteDifferences)),
                        numeric(PairedStatistics.sampleSd(c.finiteDifferences)),
                        complete ? c.rank.nonzeroPairs : "NA", complete ? c.rank.zeroPairs : "NA",
                        complete ? numeric(c.rank.wPlus) : "NA", numeric(c.pValue), numeric(c.effect),
                        numeric(c.lower), numeric(c.upper), 1 - options.alpha, options.bootstrapSamples, c.seed,
                        c.status, s.id + ":" + METRICS[c.metric], FAMILY_SIZE, c.familyTested,
                        numeric(c.adjustedP), options.alpha,
                        Double.isNaN(c.adjustedP) ? "NA" : Boolean.toString(c.adjustedP <= options.alpha));
            }
        }
    }

    private static void writeDifferences(Path path, List<Comparison> comparisons) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(path, "study_id", "regime", "scenario", "study_hash",
                "run_id", "case_id", "fuzzy_selector", "baseline_selector", "metric",
                "fuzzy_value", "baseline_value", "fuzzy_minus_baseline", "lower_is_better_outcome",
                "fuzzy_test_failure_count", "baseline_test_failure_count", "difference_status")) {
            for (Comparison c : comparisons) {
                Study s = c.study;
                for (int i = 0; i < s.runs.size(); i++) {
                    int run = s.runs.get(i);
                    Row a = s.row(run, c.profile, SELECTORS[0]);
                    Row b = s.row(run, c.profile, c.baseline);
                    double av = a.value(c.metric);
                    double bv = b.value(c.metric);
                    csv.row(s.id, s.method, s.scenario, s.studyHash, run, c.profile, SELECTORS[0], c.baseline,
                            METRICS[c.metric], numeric(av), numeric(bv), numeric(c.differences[i]),
                            av < bv ? "WIN" : av > bv ? "LOSS" : "TIE", a.failures, b.failures,
                            Double.isNaN(c.differences[i]) ? "NONFINITE_TEST_OUTCOME" : "FINITE");
                }
            }
        }
    }

    private static void writeLatex(Path path, List<Study> studies) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            out.write("% Importance-study descriptive tables only; no automatic superiority or equivalence claims.\n");
            for (Study s : studies) {
                out.write("% " + s.id + " study-hash: " + s.studyHash.replace('\n', ' ').replace('\r', ' ') + "\n");
                out.write("\\begin{table}[htbp]\n\\centering\n\\small\n");
                out.write("\\caption{Experiment A (importance-v1), " + latex(s.method) + ", " + latex(s.scenario)
                        + ". All-run mean (sample SD) across " + s.runs.size()
                        + " runs. Failed held-out evaluations remain $+\\infty$; unavailable SD is --."
                        + " Failures show failed runs/total runs and failed evaluations/total evaluations."
                        + " WS (MANUAL\\_WS) is the primary manual comparator; Random is a reference."
                        + " Profiles vary importance weights (P,I,S), not criterion directions:"
                        + " smaller raw costs are better. The fixed nonlinear utility is not assumed superior.}\n");
                out.write("\\label{tab:experiment-a-" + s.id.replace('_', '-') + "}\n");
                out.write("\\begin{tabular}{llrrrr}\n\\hline\n"
                        + "Case $(w_P,w_I,w_S)$ & Selector & Held-out objective & Complexity & Sensitivity & Failures \\\\\n\\hline\n");
                for (int profile = 1; profile <= 6; profile++) {
                    for (String selector : SELECTORS) {
                        Description objective = new Description(s, "C" + profile, selector, 0);
                        out.write("C" + profile + " (" + IMPORTANCE_WEIGHTS[profile - 1] + ") & "
                                + shortSelector(selector) + " & " + latexDescription(objective)
                                + " & " + latexDescription(new Description(s, "C" + profile, selector, 1))
                                + " & " + latexDescription(new Description(s, "C" + profile, selector, 2))
                                + " & " + objective.failureRuns + "/" + objective.runs + "; "
                                + objective.failures + "/" + objective.evaluations + " \\\\\n");
                    }
                }
                out.write("\\hline\n\\end{tabular}\n\\end{table}\n\n");
            }
        }
    }

    private static void writeGuide(Path path) throws IOException {
        Properties guide = new Properties();
        guide.setProperty("protocol", "experiment-a-analysis-guide-v2-importance");
        guide.setProperty("input-protocol", PROTOCOL);
        guide.setProperty("profiles", PROFILES);
        guide.setProperty("fuzzy-selector", SELECTORS[0]);
        guide.setProperty("selectors", String.join(",", SELECTORS));
        guide.setProperty("source", "Read-only COMPLETE or COMPLETE_WITH_TEST_FAILURES " + PROTOCOL + " studies;"
                + " formal defaults require a common-validation top-500 eligible pool for BOTH historical regimes,"
                + " top-k=3, 50 test instances and 30 runs: 30*6*3=540 input rows."
                + " Diagnostic controls require explicit expected-control overrides."
                + " Legacy experiment-a-v1 target diagnostics are rejected, never relabelled.");
        guide.setProperty("importance-semantics", "DIRECTION CONSISTENT / IMPORTANCE DIFFERENT."
                + " P,I,S denote performance, interpretability and stability. Every criterion shares the same"
                + " preference direction: higher normalized desirability and smaller raw cost are better."
                + " Six profiles change importance weights only, not desired target values.");
        guide.setProperty("fuzzy-utility", "Fixed, not learned: for normalized desirability d, Gaussian terms"
                + " mu_t(d)=exp(-0.5*((d-c_t)/0.15)^2), centers c=(0.2,0.5,0.8), grades g=(0,0.5,1)."
                + " Criterion utility u(d)=sum_t(g_t*mu_t(d))/sum_t(mu_t(d)), followed by user importance"
                + " weighting across criteria. This is not Gaussian target membership."
                + " The analyzer reads results only and does not recompute or infer scorer implementations.");
        for (int profile = 1; profile <= IMPORTANCE_WEIGHTS.length; profile++) {
            guide.setProperty("C" + profile + ".importance-weights-P-I-S", IMPORTANCE_WEIGHTS[profile - 1]);
        }
        guide.setProperty("expected-controls", "--expected-runs defaults to 30; --expected-pool-size defaults to 500;"
                + " --expected-top-k defaults to 3; --expected-test-instances defaults to 50."
                + " Every input manifest must match these expectations exactly. selected_count must equal expected-top-k,"
                + " test_evaluation_count must equal expected-top-k * expected-test-instances, and failures cannot exceed it."
                + " Counts must be positive, top-k cannot exceed pool-size, and the evaluation product must fit an integer."
                + " analysis.properties records expected and actual controls and marks nondefault controls as DIAGNOSTIC_NONDEFAULT_CONTROLS;"
                + " diagnostic outputs must not be presented as the formal 30-run/500-pool/3-selected/50-instance study.");
        guide.setProperty("regimes", "NfixIns=historical QDGPf; NrotateInsNoReeva=historical QDGP."
                + " No pooled regime, scenario or study-hash inference.");
        guide.setProperty("primary-baseline", "MANUAL_WS");
        guide.setProperty("reference-baseline", "RANDOM");
        guide.setProperty("summary.csv", "One row per study/case/selector/metric; all_run_mean includes all runs."
                + " Infinity denotes preserved test failures. Sample SD is NA with any nonfinite value or n<2."
                + " finite_only_* are explicitly descriptive and never an inferential subset.");
        guide.setProperty("paired_comparisons.csv", "Fuzzy minus baseline across complete run blocks."
                + " Exact conditional two-sided signed-rank p-values, wilcox zero removal, tie-averaged ranks."
                + " Positive rank-biserial favours Fuzzy on a lower-is-better raw metric.");
        guide.setProperty("paired_differences.csv", "One row per study/run/case/comparator/metric."
                + " Any nonfinite operand has NA numeric difference, including Infinity minus Infinity."
                + " Identical infinities count as ties in raw lower-is-better win/loss/tie counts.");
        guide.setProperty("failures", "No reselection or discarded run blocks. Any nonfinite objective in either"
                + " member of any pair disables that whole objective comparison's p-value, effect and CI:"
                + " NONFINITE_TEST_OUTCOME. Complexity/sensitivity retain all runs and may still be tested."
                + " Validation/metric failures must have been excluded with reasons upstream while still retaining the"
                + " configured eligible pool size (500 for the formal defaults).");
        guide.setProperty("holm-family", "Predeclared separately for each study identity and metric:"
                + " 6 cases x 2 comparators = 12 planned tests. Non-testable entries retain NA displayed"
                + " p/effect/adjusted p/rejection, but count as p=1 for multiplicity only."
                + " Holm retains family_planned_tests=12; family_testable_tests is descriptive, not the correction denominator."
                + " Families are never pooled."
                + " ALL_ZERO remains testable with p=1 and effect=0.");
        guide.setProperty("bootstrap", "Paired run-block resampling of mean differences with replacement;"
                + " percentile endpoints use linear interpolation at alpha/2 and 1-alpha/2."
                + " CI is NA for n<2 or any nonfinite pair. Confidence=1-alpha; CIs are not multiplicity adjusted."
                + " java.util.Random uses the recorded per-comparison seed derived deterministically from the"
                + " requested seed and study identity/case/comparator/metric; run IDs are sorted numerically.");
        guide.setProperty("interpretation", "Wins/losses/ties and positive rank-biserial describe smaller Fuzzy"
                + " raw costs as better. Different importance weights permit criterion trade-offs; no claim that"
                + " every raw criterion must improve. Neither a fixed nonlinear utility nor learned utility is"
                + " inherently superior. Non-rejection is not equivalence; superiority requires outcome evidence.");
        guide.setProperty("descriptive_tables.tex", "LaTeX fragment, one descriptive table per study; standard tabular,"
                + " all-run mean (sample SD), failure counts, no significance annotation.");
        guide.setProperty("analysis.properties", "Only status=COMPLETE is a successful analysis. FAILED or RUNNING"
                + " outputs are incomplete and must not be used. Input and output SHA-256 hashes support provenance.");
        store(path, guide);
    }

    private static Properties metadata(List<Study> studies, Options options) {
        Properties result = new Properties();
        result.setProperty("protocol", "experiment-a-analysis-v2-importance");
        result.setProperty("input-protocol", PROTOCOL);
        result.setProperty("profiles", PROFILES);
        result.setProperty("fuzzy-selector", SELECTORS[0]);
        result.setProperty("selectors", String.join(",", SELECTORS));
        result.setProperty("preference-semantics", "direction-consistent-importance-different");
        for (int profile = 1; profile <= IMPORTANCE_WEIGHTS.length; profile++) {
            result.setProperty("C" + profile + ".importance-weights-P-I-S", IMPORTANCE_WEIGHTS[profile - 1]);
        }
        result.setProperty("expected-runs", Integer.toString(options.expectedRuns));
        result.setProperty("expected-pool-size", Integer.toString(options.expectedPoolSize));
        result.setProperty("expected-top-k", Integer.toString(options.expectedTopK));
        result.setProperty("expected-test-instances", Integer.toString(options.expectedTestInstances));
        result.setProperty("control-scope", options.expectedRuns == 30 && options.expectedPoolSize == 500
                && options.expectedTopK == 3 && options.expectedTestInstances == 50
                ? "FORMAL_DEFAULT_CONTROLS" : "DIAGNOSTIC_NONDEFAULT_CONTROLS");
        result.setProperty("alpha", Double.toString(options.alpha));
        result.setProperty("bootstrap-samples", Integer.toString(options.bootstrapSamples));
        result.setProperty("bootstrap-seed", Long.toString(options.bootstrapSeed));
        result.setProperty("study-count", Integer.toString(studies.size()));
        result.setProperty("primary-baseline", "MANUAL_WS");
        result.setProperty("reference-baseline", "RANDOM");
        result.setProperty("holm-planned-tests-per-study-metric", Integer.toString(FAMILY_SIZE));
        result.setProperty("holm-nontestable-policy", "Retain the planned 12-test family; non-testable contrasts"
                + " count as p=1 for multiplicity only and retain unavailable displayed p/effect/adjusted p/rejection");
        for (Study study : studies) {
            String prefix = study.id + ".";
            result.setProperty(prefix + "root", study.root.toString());
            result.setProperty(prefix + "input-protocol", PROTOCOL);
            result.setProperty(prefix + "profiles", PROFILES);
            result.setProperty(prefix + "fuzzy-selector", SELECTORS[0]);
            result.setProperty(prefix + "selectors", String.join(",", SELECTORS));
            result.setProperty(prefix + "primary-baseline", "MANUAL_WS");
            result.setProperty(prefix + "method", study.method);
            result.setProperty(prefix + "scenario", study.scenario);
            result.setProperty(prefix + "study-hash", study.studyHash);
            result.setProperty(prefix + "input-status", study.status);
            result.setProperty(prefix + "num-runs", Integer.toString(study.runs.size()));
            result.setProperty(prefix + "pool-size", Integer.toString(study.poolSize));
            result.setProperty(prefix + "top-k", Integer.toString(study.topK));
            result.setProperty(prefix + "test-instances", Integer.toString(study.testInstances));
            result.setProperty(prefix + "manifest-sha256", study.manifestHash);
            result.setProperty(prefix + "results-sha256", study.resultsHash);
            List<String> ids = new ArrayList<>();
            for (int run : study.runs) { ids.add(Integer.toString(run)); }
            result.setProperty(prefix + "run-ids", String.join(",", ids));
        }
        return result;
    }

    private static void store(Path path, Properties properties) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(writer, "Experiment A result analysis");
        }
    }

    static List<String> csvFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (quoted) {
                if (character == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closed = true;
                    }
                } else {
                    cell.append(character);
                }
            } else if (character == ',') {
                fields.add(cell.toString());
                cell.setLength(0);
                closed = false;
            } else if (closed) {
                throw new IllegalArgumentException("Characters after closing CSV quote");
            } else if (character == '"') {
                if (cell.length() != 0) { throw new IllegalArgumentException("Quote inside unquoted CSV field"); }
                quoted = true;
            } else {
                cell.append(character);
            }
        }
        if (quoted) { throw new IllegalArgumentException("Unclosed CSV quote"); }
        fields.add(cell.toString());
        return fields;
    }

    private static String key(int run, String profile, String selector) {
        return run + ":" + profile + ":" + selector;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing manifest property: " + key);
        }
        return value.trim();
    }

    private static void exactInteger(Properties properties, String key, int expected) {
        if (integer(required(properties, key), key) != expected) {
            throw new IllegalArgumentException("Expected " + key + "=" + expected);
        }
    }

    private static int integer(String value, String label) {
        if (!value.matches("[0-9]+")) { throw new IllegalArgumentException(label + " must be a nonnegative integer"); }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(label + " is outside the supported integer range", failure);
        }
    }

    private static double number(String value, String label) {
        if (!value.equals(value.trim()) || value.isEmpty()) {
            throw new IllegalArgumentException("Malformed numeric " + label);
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Malformed numeric " + label, failure);
        }
    }

    private static double finiteNonnegative(String value, String label) {
        double result = number(value, label);
        if (!Double.isFinite(result) || result < 0) {
            throw new IllegalArgumentException(label + " must be finite and nonnegative");
        }
        return result;
    }

    private static void equal(String actual, String expected, String label) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Expected " + label + "=" + expected + ", got " + actual);
        }
    }

    private static double[] doubles(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) { result[i] = values.get(i); }
        return result;
    }

    private static String numeric(double value) {
        return Double.isNaN(value) ? "NA" : Double.toString(value);
    }

    private static long comparisonSeed(long base, String identity) {
        long value = 0xcbf29ce484222325L ^ base;
        for (int i = 0; i < identity.length(); i++) {
            value ^= identity.charAt(i);
            value *= 0x100000001b3L;
        }
        return value;
    }

    private static String baselineRole(String selector) {
        return selector.equals("MANUAL_WS") ? "PRIMARY_MANUAL_WS" : "REFERENCE_RANDOM";
    }

    private static String shortSelector(String selector) {
        return selector.equals(SELECTORS[0]) ? "Fuzzy" : selector.equals("MANUAL_WS") ? "WS" : "Random";
    }

    private static String latexDescription(Description d) {
        return latexNumber(d.mean) + " (" + latexNumber(d.sd) + ")";
    }

    private static String latexNumber(double value) {
        if (Double.isNaN(value)) { return "--"; }
        if (value == Double.POSITIVE_INFINITY) { return "$+\\infty$"; }
        return String.format(Locale.ROOT, "%.6g", value);
    }

    private static String latex(String value) {
        StringBuilder result = new StringBuilder();
        for (char character : value.toCharArray()) {
            if ("#$%&_{}".indexOf(character) >= 0) { result.append('\\').append(character); }
            else if (character == '\\') { result.append("\\textbackslash{}"); }
            else if (character == '^') { result.append("\\textasciicircum{}"); }
            else if (character == '~') { result.append("\\textasciitilde{}"); }
            else { result.append(character); }
        }
        return result.toString();
    }
}
