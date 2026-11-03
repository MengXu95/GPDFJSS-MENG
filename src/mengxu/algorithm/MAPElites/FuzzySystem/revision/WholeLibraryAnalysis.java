package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/** Descriptive B reporting only: no evaluation, selection, imputation, or significance tests. */
public final class WholeLibraryAnalysis {
    static final String HEADER = "method,run_id,candidate_id,tree_hash,stored_fitness,"
            + "mean_validation_objective,complexity,sensitivity,quality_status,sensitivity_status,"
            + "a_status,candidate_pool,metric_source,sensitivity_reason";
    static final String[] METHODS = {"NfixIns", "NrotateInsNoReeva"};
    static final String[] SCOPES = {"ALL_RETAINED", "POOL_500"};
    static final String[] METRICS = {"validation_objective", "complexity", "sensitivity", "log10_1p_sensitivity"};
    static final int P = 0, C = 1, S = 2, LOG_S = 3;
    static final int PLOT_CAP = 150;
    static final long PLOT_SEED = 42;
    private static final String[] STATUS_DIMENSIONS = {
        "quality_status", "sensitivity_status", "a_status", "metric_source", "sensitivity_reason"
    };

    private WholeLibraryAnalysis() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("Usage: WholeLibraryAnalysis --input <Broot> --output-root <NEW directory>"
                    + " [--expected-runs 30] [--expected-pool-size 500]");
            System.out.println("Requires COMPLETE whole-library-attributes-v1 members.csv for both regimes,"
                    + " run IDs 0..expected-runs-1. Pool counts are checked in EVERY library."
                    + " Diagnostic fixtures must explicitly override the expected pool size if not 500."
                    + " Outputs are descriptive, finite-conditional and library-balanced; no member-level inference.");
            return;
        }
        analyze(args);
    }

    public static void analyze(String[] args) throws IOException {
        Options options = Options.parse(args);
        Data data = Data.read(options.input, options.runs, options.poolSize);
        Path output = RevisionConfiguration.checkedOutput(options.output, data.root);
        List<Description> descriptions = describe(data);
        List<Curve> curves = curves(descriptions);
        List<Sample> samples = samples(data, PLOT_CAP, PLOT_SEED);
        data.verifyUnchanged();
        output = RevisionConfiguration.checkedOutput(output, data.root);
        Files.createDirectories(output.getParent());
        Files.createDirectory(output);
        Path metadataPath = output.resolve("analysis.properties");
        Properties metadata = metadata(data);
        try {
            metadata.setProperty("status", "RUNNING");
            store(metadataPath, metadata);
            writeLibraries(output.resolve("library_summary.csv"), descriptions, data.poolSize);
            writeRegimes(output.resolve("regime_summary.csv"), descriptions, data.runs);
            writeStatuses(output.resolve("status_counts.csv"), data);
            writeCurves(output.resolve("ecdf_points.csv"), curves);
            writeSamples(output.resolve("plot_samples.csv"), samples);
            writeTable(output.resolve("descriptive_table.tex"), descriptions, data);
            writeCaptions(output.resolve("figure_captions.tex"), data);
            writeGuide(output.resolve("results_guide.properties"), data);
            WholeLibraryPlots.write(output, curves, samples, data.poolSize);
            for (String file : Arrays.asList("library_summary.csv", "regime_summary.csv", "status_counts.csv",
                    "ecdf_points.csv", "plot_samples.csv", "descriptive_table.tex", "figure_captions.tex",
                    "results_guide.properties", "run_balanced_ecdf.png", "quality_attribute_scatter.png")) {
                metadata.setProperty(file + ".sha256", RevisionCsv.fileHash(output.resolve(file)));
            }
            data.verifyUnchanged();
            metadata.setProperty("status", "COMPLETE");
            store(metadataPath, metadata);
        } catch (IOException | RuntimeException | Error failure) {
            metadata.setProperty("status", "FAILED");
            metadata.setProperty("failure", failure.getClass().getName() + ": " + failure.getMessage());
            try { store(metadataPath, metadata); }
            catch (IOException | RuntimeException reportFailure) { failure.addSuppressed(reportFailure); }
            throw failure;
        }
    }

    private static final class Options {
        Path input;
        Path output;
        int runs = 30;
        int poolSize = 500;

        static Options parse(String[] args) {
            Options result = new Options();
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                if (!Arrays.asList("--input", "--output-root", "--expected-runs", "--expected-pool-size").contains(key)
                        || seen.contains(key)) {
                    throw new IllegalArgumentException("Unknown or duplicate option: " + key);
                }
                seen.add(key);
                if (++i >= args.length || args[i].isEmpty() || args[i].startsWith("--")) {
                    throw new IllegalArgumentException(key + " needs a value");
                }
                switch (key) {
                    case "--input": result.input = Paths.get(args[i]); break;
                    case "--output-root": result.output = Paths.get(args[i]); break;
                    case "--expected-runs": result.runs = integer(args[i], key); break;
                    case "--expected-pool-size": result.poolSize = integer(args[i], key); break;
                    default: throw new AssertionError(key);
                }
            }
            if (result.input == null || result.output == null || result.runs < 1 || result.runs > 30
                    || result.poolSize < 1) {
                throw new IllegalArgumentException("Require --input, --output-root, expected-runs in [1,30]"
                        + " and positive expected-pool-size");
            }
            return result;
        }
    }

    static final class Member {
        final String method;
        final int run;
        final String id;
        final String treeHash;
        final double quality;
        final double complexity;
        final double sensitivity;
        final String qualityStatus;
        final String sensitivityStatus;
        final String aStatus;
        final boolean pool;
        final String metricSource;
        final String sensitivityReason;

        Member(List<String> cells) {
            if (cells.size() != 14) { throw new IllegalArgumentException("Expected 14 member columns"); }
            method = cells.get(0);
            if (!Arrays.asList(METHODS).contains(method)) { throw new IllegalArgumentException("Unknown regime: " + method); }
            run = integer(cells.get(1), "run_id");
            id = nonempty(cells.get(2), "candidate_id");
            treeHash = nonempty(cells.get(3), "tree_hash");
            number(cells.get(4), "stored_fitness"); // Provenance only; never substituted for validation quality.
            complexity = finiteNonnegative(cells.get(6), "complexity");
            qualityStatus = cells.get(8);
            switch (qualityStatus) {
                case "VALIDATION_FINITE":
                    quality = finiteNonnegative(cells.get(5), "mean_validation_objective");
                    break;
                case "VALIDATION_FAILED":
                    quality = number(cells.get(5), "mean_validation_objective");
                    if (quality != Double.POSITIVE_INFINITY) {
                        throw new IllegalArgumentException("VALIDATION_FAILED must retain +Infinity quality");
                    }
                    break;
                case "NOT_EVALUATED_STORED_FAILURE":
                    if (!cells.get(5).isEmpty()) { throw new IllegalArgumentException("Unknown validation quality must be blank"); }
                    quality = Double.NaN;
                    break;
                default: throw new IllegalArgumentException("Unknown quality_status: " + qualityStatus);
            }
            sensitivityStatus = cells.get(9);
            if (sensitivityStatus.equals("FINITE")) {
                sensitivity = finiteNonnegative(cells.get(7), "sensitivity");
            } else if (sensitivityStatus.equals("UNDEFINED_NUMERIC")) {
                if (!cells.get(7).isEmpty()) { throw new IllegalArgumentException("Undefined sensitivity must be blank"); }
                sensitivity = Double.NaN;
            } else {
                throw new IllegalArgumentException("Unknown sensitivity_status: " + sensitivityStatus);
            }
            aStatus = cells.get(10);
            if (!cells.get(11).equals("true") && !cells.get(11).equals("false")) {
                throw new IllegalArgumentException("candidate_pool must be true or false");
            }
            pool = Boolean.parseBoolean(cells.get(11));
            metricSource = cells.get(12);
            if (!Arrays.asList("A_CACHE", "B_COMPUTED", "B_HASH_CACHE").contains(metricSource)) {
                throw new IllegalArgumentException("Unknown metric_source: " + metricSource);
            }
            sensitivityReason = cells.get(13);
        }

        double value(int metric) {
            switch (metric) {
                case P: return quality;
                case C: return complexity;
                case S: return sensitivity;
                case LOG_S: return Double.isFinite(sensitivity) ? logSensitivity(sensitivity) : Double.NaN;
                default: throw new IllegalArgumentException("Unknown metric index: " + metric);
            }
        }

        String status(int dimension) {
            switch (dimension) {
                case 0: return qualityStatus;
                case 1: return sensitivityStatus;
                case 2: return aStatus;
                case 3: return metricSource;
                case 4: return sensitivityReason;
                default: throw new IllegalArgumentException("Unknown status dimension");
            }
        }
    }

    static final class Library {
        final String method;
        final int run;
        final Map<String, Member> members = new LinkedHashMap<>();
        int poolCount;

        Library(String method, int run) { this.method = method; this.run = run; }
    }

    static final class Data {
        final Path root;
        final int runs;
        final int poolSize;
        final String manifestHash;
        final String membersHash;
        final List<Library> libraries = new ArrayList<>();
        final Map<String, String> declaredCounts = new LinkedHashMap<>();

        private Data(Path root, int runs, int poolSize, String manifestHash, String membersHash) {
            this.root = root;
            this.runs = runs;
            this.poolSize = poolSize;
            this.manifestHash = manifestHash;
            this.membersHash = membersHash;
            for (String method : METHODS) {
                for (int run = 0; run < runs; run++) { libraries.add(new Library(method, run)); }
            }
        }

        static Data read(Path requested, int runs, int poolSize) throws IOException {
            if (runs < 1 || runs > 30 || poolSize < 1) { throw new IllegalArgumentException("Invalid expected B controls"); }
            Path root = requested.toRealPath();
            if (!Files.isDirectory(root)) { throw new IllegalArgumentException("Not a B study directory: " + root); }
            String manifestHash = RevisionCsv.fileHash(root.resolve("manifest.properties"));
            Properties manifest = new UniqueProperties();
            try (Reader reader = Files.newBufferedReader(root.resolve("manifest.properties"), StandardCharsets.UTF_8)) {
                manifest.load(reader);
            }
            equal(manifest.getProperty("protocol"), "whole-library-attributes-v1", "protocol");
            equal(manifest.getProperty("status"), "COMPLETE", "status");
            String declared = manifest.getProperty("members-sha256");
            if (declared == null || !declared.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Missing or invalid members-sha256");
            }
            String actual = RevisionCsv.fileHash(root.resolve("members.csv"));
            if (!actual.equalsIgnoreCase(declared)) { throw new IOException("members.csv SHA-256 mismatch: " + root); }
            Data result = new Data(root, runs, poolSize, manifestHash, actual);
            try (CsvReader reader = new CsvReader(root.resolve("members.csv"))) {
                List<String> cells;
                long record = 1;
                while ((cells = reader.read()) != null) {
                    record++;
                    Member member;
                    try { member = new Member(cells); }
                    catch (IllegalArgumentException failure) {
                        throw new IllegalArgumentException("Invalid members.csv record " + record + ": " + failure.getMessage(), failure);
                    }
                    if (member.run >= runs) { throw new IllegalArgumentException("Unexpected run_id: " + member.run); }
                    int regime = member.method.equals(METHODS[0]) ? 0 : 1;
                    Library library = result.libraries.get(regime * runs + member.run);
                    if (library.members.putIfAbsent(member.id, member) != null) {
                        throw new IllegalArgumentException("Duplicate candidate_id in " + member.method + "/" + member.run + ": " + member.id);
                    }
                    if (member.pool) { library.poolCount++; }
                }
            }
            for (Library library : result.libraries) {
                if (library.members.isEmpty() || library.poolCount != poolSize) {
                    throw new IllegalArgumentException("Missing library or incorrect pool count for "
                            + library.method + "/" + library.run + ": expected " + poolSize + ", found " + library.poolCount);
                }
            }
            long total = 0, pool = 0, unknown = 0, failed = 0, undefined = 0;
            for (Library library : result.libraries) {
                total += library.members.size();
                pool += library.poolCount;
                for (Member member : library.members.values()) {
                    if (member.qualityStatus.equals("NOT_EVALUATED_STORED_FAILURE")) { unknown++; }
                    if (member.qualityStatus.equals("VALIDATION_FAILED")) { failed++; }
                    if (member.sensitivityStatus.equals("UNDEFINED_NUMERIC")) { undefined++; }
                }
            }
            result.checkDeclared(manifest, "retained-members", total);
            result.checkDeclared(manifest, "pool-members", pool);
            result.checkDeclared(manifest, "quality-not-evaluated", unknown);
            result.checkDeclared(manifest, "quality-failed", failed);
            result.checkDeclared(manifest, "undefined-sensitivity", undefined);
            result.checkDeclared(manifest, "expected-runs-per-regime", runs);
            result.checkDeclared(manifest, "scheduling-simulation-count", 0);
            result.verifyUnchanged();
            return result;
        }

        private void checkDeclared(Properties manifest, String key, long observed) {
            String value = manifest.getProperty(key);
            if (value == null) { return; } // Optional counters in the original v1 input contract.
            if (!value.matches("[0-9]+") || Long.parseLong(value) != observed) {
                throw new IllegalArgumentException("Declared B " + key + " mismatch: expected " + observed + ", got " + value);
            }
            declaredCounts.put(key, value);
        }

        void verifyUnchanged() throws IOException {
            if (!manifestHash.equals(RevisionCsv.fileHash(root.resolve("manifest.properties")))
                    || !membersHash.equals(RevisionCsv.fileHash(root.resolve("members.csv")))) {
                throw new IOException("B inputs changed during analysis: " + root);
            }
        }
    }

    static final class Quantiles {
        final double min, q1, median, q3, max, iqr;

        Quantiles(double[] finite) {
            double[] sorted = finite.clone();
            Arrays.sort(sorted);
            min = percentile(sorted, 0);
            q1 = percentile(sorted, 0.25);
            median = percentile(sorted, 0.5);
            q3 = percentile(sorted, 0.75);
            max = percentile(sorted, 1);
            iqr = q3 - q1;
        }
    }

    static final class Description {
        final Library library;
        final int scope;
        final int metric;
        final int total;
        final int unknown;
        final int failed;
        final int undefined;
        final double[] finite;
        final Quantiles q;

        Description(Library library, int scope, int metric) {
            this.library = library;
            this.scope = scope;
            this.metric = metric;
            List<Double> values = new ArrayList<>();
            int count = 0, missing = 0, failures = 0, undefinedValues = 0;
            for (Member member : library.members.values()) {
                if (scope == 1 && !member.pool) { continue; }
                count++;
                double value = member.value(metric);
                if (Double.isFinite(value)) { values.add(value); }
                else if (metric == P && member.qualityStatus.equals("VALIDATION_FAILED")) { failures++; }
                else if (metric == P) { missing++; }
                else { undefinedValues++; }
            }
            total = count;
            unknown = missing;
            failed = failures;
            undefined = undefinedValues;
            finite = doubles(values);
            q = new Quantiles(finite);
        }
    }

    static List<Description> describe(Data data) {
        List<Description> result = new ArrayList<>();
        for (Library library : data.libraries) {
            for (int scope = 0; scope < SCOPES.length; scope++) {
                for (int metric = 0; metric < METRICS.length; metric++) {
                    result.add(new Description(library, scope, metric));
                }
            }
        }
        return result;
    }

    /** Type-7 sample quantile, including NA (NaN) for an empty finite sample. */
    public static double type7(double[] values, double probability) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Quantile probability must be in [0,1]");
        }
        double[] sorted = values.clone();
        for (double value : sorted) {
            if (!Double.isFinite(value)) { throw new IllegalArgumentException("Quantiles require an explicit finite subset"); }
        }
        Arrays.sort(sorted);
        return percentile(sorted, probability);
    }

    private static double percentile(double[] sorted, double probability) {
        if (sorted.length == 0) { return Double.NaN; }
        double position = probability * (sorted.length - 1);
        int lo = (int) Math.floor(position), hi = (int) Math.ceil(position);
        if (lo == hi || sorted[lo] == sorted[hi]) { return sorted[lo]; }
        double fraction = position - lo;
        return sorted[lo] * (1 - fraction) + sorted[hi] * fraction;
    }

    static double logSensitivity(double value) { return Math.log1p(value) / Math.log(10); }

    static final class Ecdf {
        final double[] x;
        final double[] y;
        final int libraries;
        final int emptyLibraries;
        final long finiteMembers;

        Ecdf(List<double[]> runs) {
            List<double[]> observations = new ArrayList<>();
            int eligible = 0;
            long members = 0;
            for (double[] run : runs) {
                if (run.length > 0) { eligible++; }
                members += run.length;
            }
            libraries = eligible;
            emptyLibraries = runs.size() - eligible;
            finiteMembers = members;
            for (double[] run : runs) {
                for (double value : run) {
                    if (!Double.isFinite(value)) { throw new IllegalArgumentException("ECDF requires finite conditional samples"); }
                    observations.add(new double[]{value, 1.0 / eligible / run.length});
                }
            }
            observations.sort(Comparator.comparingDouble(value -> value[0]));
            List<Double> xs = new ArrayList<>(), ys = new ArrayList<>();
            double cumulative = 0;
            for (int i = 0; i < observations.size();) {
                double value = observations.get(i)[0];
                int end = i;
                while (end < observations.size() && observations.get(end)[0] == value) {
                    cumulative += observations.get(end)[1];
                    end++;
                }
                xs.add(value);
                ys.add(Math.min(1, cumulative));
                i = end;
            }
            x = doubles(xs);
            y = doubles(ys);
            if (y.length > 0) { y[y.length - 1] = 1; }
        }
    }

    static final class Curve {
        final String method;
        final int scope;
        final int metric;
        final Ecdf ecdf;

        Curve(String method, int scope, int metric, List<double[]> runs) {
            this.method = method; this.scope = scope; this.metric = metric;
            ecdf = new Ecdf(runs);
        }
    }

    private static List<Curve> curves(List<Description> descriptions) {
        List<Curve> result = new ArrayList<>();
        for (int metric : new int[]{P, C, LOG_S}) {
            for (String method : METHODS) {
                for (int scope = 0; scope < SCOPES.length; scope++) {
                    List<double[]> runs = new ArrayList<>();
                    for (Description d : descriptions) {
                        if (d.library.method.equals(method) && d.scope == scope && d.metric == metric) { runs.add(d.finite); }
                    }
                    result.add(new Curve(method, scope, metric, runs));
                }
            }
        }
        return result;
    }

    static final class Sample {
        final Member member;
        final int scope;
        final int yMetric;
        Sample(Member member, int scope, int yMetric) { this.member = member; this.scope = scope; this.yMetric = yMetric; }
    }

    static List<Sample> samples(Data data, int cap, long seed) {
        if (cap < 1) { throw new IllegalArgumentException("Plot cap must be positive"); }
        List<Sample> result = new ArrayList<>();
        for (Library library : data.libraries) {
            for (int scope = 0; scope < SCOPES.length; scope++) {
                for (int metric : new int[]{C, LOG_S}) {
                    List<Member> eligible = new ArrayList<>();
                    for (Member member : library.members.values()) {
                        if ((scope == 0 || member.pool) && Double.isFinite(member.quality)
                                && Double.isFinite(member.value(metric))) { eligible.add(member); }
                    }
                    eligible.sort(Comparator.comparing(member -> member.id));
                    long derived = seed;
                    String identity = library.method + ":" + library.run + ":" + scope + ":" + metric;
                    for (char character : identity.toCharArray()) { derived = derived * 31 + character; }
                    Collections.shuffle(eligible, new Random(derived));
                    for (int i = 0; i < Math.min(cap, eligible.size()); i++) {
                        result.add(new Sample(eligible.get(i), scope, metric));
                    }
                }
            }
        }
        return result;
    }

    private static void writeLibraries(Path path, List<Description> descriptions, int expectedPool) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(path, "method", "run_id", "scope", "metric", "scope_member_count",
                "library_member_count", "pool_member_count", "expected_pool_size", "finite_count", "unknown_count",
                "failure_count", "undefined_count", "finite_min", "finite_q1", "finite_median", "finite_q3",
                "finite_max", "finite_iqr")) {
            for (Description d : descriptions) {
                csv.row(d.library.method, d.library.run, SCOPES[d.scope], METRICS[d.metric], d.total,
                        d.library.members.size(), d.library.poolCount, expectedPool, d.finite.length, d.unknown,
                        d.failed, d.undefined, numeric(d.q.min), numeric(d.q.q1), numeric(d.q.median),
                        numeric(d.q.q3), numeric(d.q.max), numeric(d.q.iqr));
            }
        }
    }

    private static List<Description> group(List<Description> descriptions, String method, int scope, int metric) {
        List<Description> result = new ArrayList<>();
        for (Description d : descriptions) {
            if (d.library.method.equals(method) && d.scope == scope && d.metric == metric) { result.add(d); }
        }
        return result;
    }

    private static double[] libraryValues(List<Description> group, boolean iqr) {
        List<Double> result = new ArrayList<>();
        for (Description d : group) {
            if (d.finite.length > 0) { result.add(iqr ? d.q.iqr : d.q.median); }
        }
        return doubles(result);
    }

    private static void writeRegimes(Path path, List<Description> descriptions, int runs) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(path, "method", "scope", "metric", "expected_libraries",
                "libraries_with_finite_metric", "libraries_without_finite_metric", "total_members",
                "finite_members", "unknown_members", "failure_members", "undefined_members",
                "run_median_min", "run_median_q1", "median_of_run_medians", "run_median_q3", "run_median_max",
                "run_iqr_min", "run_iqr_q1", "median_of_run_iqrs", "run_iqr_q3", "run_iqr_max")) {
            for (String method : METHODS) {
                for (int scope = 0; scope < SCOPES.length; scope++) {
                    for (int metric = 0; metric < METRICS.length; metric++) {
                        List<Description> group = group(descriptions, method, scope, metric);
                        double[] medians = libraryValues(group, false);
                        Quantiles m = new Quantiles(medians), i = new Quantiles(libraryValues(group, true));
                        long total = 0, finite = 0, unknown = 0, failed = 0, undefined = 0;
                        for (Description d : group) {
                            total += d.total; finite += d.finite.length; unknown += d.unknown;
                            failed += d.failed; undefined += d.undefined;
                        }
                        csv.row(method, SCOPES[scope], METRICS[metric], runs, medians.length, runs - medians.length,
                                total, finite, unknown, failed, undefined, numeric(m.min), numeric(m.q1),
                                numeric(m.median), numeric(m.q3), numeric(m.max), numeric(i.min), numeric(i.q1),
                                numeric(i.median), numeric(i.q3), numeric(i.max));
                    }
                }
            }
        }
    }

    private static void writeStatuses(Path path, Data data) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(path, "method", "run_id", "scope", "dimension", "status",
                "member_count", "scope_member_denominator", "member_fraction")) {
            for (String method : METHODS) {
                for (int run = -1; run < data.runs; run++) {
                    for (int scope = 0; scope < SCOPES.length; scope++) {
                        for (int dimension = 0; dimension < STATUS_DIMENSIONS.length; dimension++) {
                            Map<String, Long> counts = new java.util.TreeMap<>();
                            long denominator = 0;
                            for (Library library : data.libraries) {
                                if (!library.method.equals(method) || (run >= 0 && library.run != run)) { continue; }
                                for (Member member : library.members.values()) {
                                    if (scope == 1 && !member.pool) { continue; }
                                    counts.merge(member.status(dimension), 1L, Long::sum);
                                    denominator++;
                                }
                            }
                            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                                csv.row(method, run < 0 ? "ALL_LIBRARIES" : Integer.toString(run), SCOPES[scope],
                                        STATUS_DIMENSIONS[dimension], entry.getKey(), entry.getValue(), denominator,
                                        entry.getValue() / (double) denominator);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void writeCurves(Path path, List<Curve> curves) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(path, "method", "scope", "metric", "libraries_with_finite_metric",
                "libraries_without_finite_metric", "finite_members", "x", "run_balanced_ecdf")) {
            for (Curve curve : curves) {
                Ecdf e = curve.ecdf;
                if (e.x.length == 0) {
                    csv.row(curve.method, SCOPES[curve.scope], METRICS[curve.metric], 0, e.emptyLibraries, 0, "NA", "NA");
                }
                for (int i = 0; i < e.x.length; i++) {
                    csv.row(curve.method, SCOPES[curve.scope], METRICS[curve.metric], e.libraries, e.emptyLibraries,
                            e.finiteMembers, e.x[i], e.y[i]);
                }
            }
        }
    }

    private static void writeSamples(Path path, List<Sample> samples) throws IOException {
        try (RevisionCsv csv = new RevisionCsv(path, "method", "run_id", "scope", "candidate_id", "tree_hash",
                "x_metric", "y_metric", "x_value", "y_value", "per_library_panel_cap", "sampling_seed")) {
            for (Sample sample : samples) {
                Member m = sample.member;
                csv.row(m.method, m.run, SCOPES[sample.scope], m.id, m.treeHash, METRICS[P], METRICS[sample.yMetric],
                        m.quality, m.value(sample.yMetric), PLOT_CAP, PLOT_SEED);
            }
        }
    }

    private static void writeTable(Path path, List<Description> descriptions, Data data) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            out.write("\\begin{table}[htbp]\n\\centering\\small\n"
                    + "\\caption{Whole-retained and " + data.poolSize + "-pool attribute descriptions. "
                    + "Libraries, not members, are the independent units (" + data.runs + " per regime). "
                    + "P is available-subset validation quality only; C is complexity; S is raw sensitivity. "
                    + "Raw-S medians and IQRs are the primary sensitivity descriptions; chart logS is a monotone display only. "
                    + "L reports libraries with finite observations/total libraries. "
                    + "The center and range describe run medians; the last column is the median run IQR. "
                    + "Undefined or failed values are not imputed. No native-grid coverage or significance claim.}\n"
                    + "\\begin{tabular}{lllrrrr}\n\\hline\n"
                    + "Regime & Scope & Metric & L & Finite/total & Median [min,max] & Run IQR \\\\\n\\hline\n");
            for (String method : METHODS) {
                for (int scope = 0; scope < SCOPES.length; scope++) {
                    for (int metric : new int[]{P, C, S}) {
                        List<Description> group = group(descriptions, method, scope, metric);
                        double[] medians = libraryValues(group, false);
                        Quantiles m = new Quantiles(medians), i = new Quantiles(libraryValues(group, true));
                        long finite = 0, total = 0;
                        for (Description d : group) { finite += d.finite.length; total += d.total; }
                        out.write(method + " & " + (scope == 0 ? "Retained" : "Pool") + " & "
                                + new String[]{"P", "C", "S"}[metric] + " & " + medians.length + "/" + data.runs
                                + " & " + finite + "/" + total + " & " + compact(m.median) + " ["
                                + compact(m.min) + "," + compact(m.max) + "] & " + compact(i.median) + " \\\\\n");
                    }
                }
            }
            out.write("\\hline\n\\end{tabular}\n\\end{table}\n");
        }
    }

    private static void writeCaptions(Path path, Data data) throws IOException {
        List<String> lines = Arrays.asList(
                "\\paragraph{Run-balanced attribute ECDFs.} Whole retained with finite metric versus the "
                        + "top " + data.poolSize + " pool for QDGPf and QDGP. Each nonempty library contributes equal weight;"
                        + " within a library, finite members share that weight. Panels render available validation"
                        + " P on a display-only $\\log_{10}(1+P)$ axis, C on its raw scale, and $\\log_{10}(1+S)$."
                        + " P coordinates use the actual transformed finite range with padding, not a forced zero origin."
                        + " No finite observation is clipped or omitted. Libraries with no finite observations are explicitly counted in"
                        + " the underlying CSV. Neither unknown P nor undefined S is imputed. All curves use full data.",
                "\\paragraph{Sensitivity scale.} Raw-S run medians and run IQRs are the primary robust sensitivity"
                        + " descriptions. The monotone $\\log_{10}(1+S)$ axis is for display only, not a substitute"
                        + " for raw-scale IQRs; transformed-scale CSV summaries are supplementary.",
                "\\paragraph{Available-validation-quality--attribute scatter.} Available validation P versus C and $\\log_{10}(1+S)$"
                        + " for QDGPf and QDGP, distinguishing whole retained from the top " + data.poolSize + " pool."
                        + " The P coordinate is display-only $\\log_{10}(1+P)$, including P=0; raw CSV P values and statistics are unchanged."
                        + " Only finite pairs are eligible; deterministic samples are capped at " + PLOT_CAP
                        + " members per library, scope and panel (seed " + PLOT_SEED + "). Sampling is for plotting"
                        + " only; all descriptive statistics and ECDFs use every relevant finite member.",
                "\\paragraph{Coverage limitation.} Whole retained means the members in the raw export, not the"
                        + " original native grid. No native cell coordinates are available; native-grid coverage"
                        + " cannot be verified. The pool is a subset of whole retained, not an independent sample.");
        Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static void writeGuide(Path path, Data data) throws IOException {
        Properties guide = new Properties();
        guide.setProperty("protocol", "whole-library-analysis-guide-v1");
        guide.setProperty("units", data.runs + " libraries per regime, not members. No significance tests.");
        guide.setProperty("quality", "Validation quality is available subset only. Unknown P remains blank in the"
                + " source and is counted as unknown; validation failures retain Infinity and are counted separately."
                + " No full-library quality or quality derived from stored_fitness is invented."
                + " analysis.properties records observed global finite/unknown/failed counts for each scope.");
        guide.setProperty("quality-display-coordinate", "log10(1+P)");
        guide.setProperty("quality-csv-scale", "RAW_P");
        guide.setProperty("quality-display-interpretation", "Display-only log1p(P)/log(10) coordinates in the"
                + " ECDF P panel and both scatter x axes, including P=0. Ranges use the actual plotted transformed"
                + " min/max plus padding, not a forced zero origin or quantile clipping."
                + " ECDF probabilities, raw CSV P x values/metrics, numerical summaries and counts are unchanged.");
        guide.setProperty("scopes", "ALL_RETAINED includes every exported candidate membership. POOL_500 is its"
                + " candidate_pool=true subset. Every library must contain exactly expected-pool-size pool members"
                + " (default 500); diagnostic overrides change the expected count, not the scope identifier."
                + " Tree-hash reuse does not deduplicate candidate memberships.");
        guide.setProperty("expected-controls", "--expected-runs 30 and --expected-pool-size 500 by default."
                + " Both methods are required, with exactly run IDs 0..expected-runs-1 and nonempty libraries."
                + " For small fixtures use e.g. --expected-runs 2 --expected-pool-size 2."
                + " All inputs and counts are validated before creating the NEW output directory.");
        guide.setProperty("missing", "For each metric, finite + unknown + failure + undefined = scope members."
                + " Unknown/failure apply to P; undefined applies to S and logS. Complexity must always be finite."
                + " Empty finite subsets have NA quantiles. No failed/unknown member or whole run is silently discarded.");
        guide.setProperty("quantiles", "Type 7: position=(n-1)*p with linear interpolation; min,Q1,median,Q3,max"
                + " and IQR=Q3-Q1 use ALL finite values in each library/scope, not plot samples."
                + " logS=log1p(S)/log(10), numerically stable even for large or tiny finite S.");
        guide.setProperty("sensitivity-primary-scale", "RAW_S");
        guide.setProperty("sensitivity-scale-interpretation", "Raw-S run medians and run IQRs are the primary robust"
                + " sensitivity descriptions, with type-7 aggregation across libraries. log10(1+S) is a monotone"
                + " display transformation for ECDF/scatter only; transformed-scale CSV summaries are supplementary."
                + " Do not back-transform a log-scale IQR or replace raw-S robust descriptions with log-scale spreads.");
        guide.setProperty("regime_summary.csv", "Independent units are libraries. Quantiles of library medians"
                + " and library IQRs give equal library weight. Explicit eligible/empty-library counts and member"
                + " denominators accompany each metric; finite-conditional summaries never claim missing quality is known.");
        guide.setProperty("status_counts.csv", "Raw categories by library/scope and ALL_LIBRARIES regime totals."
                + " Fractions use member counts, not evaluation counts. a_status and sensitivity_reason are preserved"
                + " without guessing their vocabulary; an empty category means an actual blank source field."
                + " Duplicate evaluation reuse remains the original quality/sensitivity category with metric_source attribution.");
        guide.setProperty("ecdf_points.csv", "Exact run-balanced finite-conditional ECDF:"
                + " F(x)=sum_r(count_r(values<=x)/n_r)/L, over the L libraries with n_r>0."
                + " Each panel's absent libraries are explicitly reported; all observations are used."
                + " Curves with zero eligible libraries have NA x/F, never a fabricated zero curve."
                + " validation_objective x values remain raw P; only figure rendering uses log10(1+P).");
        guide.setProperty("plot_samples.csv", "Deterministic finite-pair samples: sort candidate IDs, shuffle with"
                + " java.util.Random using seed 42 derived by method/run/scope/panel, take at most 150 per library."
                + " Scatter density is not an inferential distribution; samples never feed the statistics or ECDFs."
                + " x_metric and x_value remain validation_objective and raw P, not transformed display coordinates.");
        guide.setProperty("figure-rendering", "publication-v2-logP");
        guide.setProperty("figure-regime-labels", "NfixIns=QDGPf; NrotateInsNoReeva=QDGP."
                + " Figure legends distinguish whole retained versus top expected-pool-size pool;"
                + " all CSV regime identifiers remain unchanged.");
        guide.setProperty("figures", "run_balanced_ecdf.png: 4200x1800, three panels with four curves each."
                + " quality_attribute_scatter.png: 3600x1800, two panels with four series."
                + " Publication fonts are 1.5 times the initial rendering: 36px ticks, 36/39px primary legends and 44px axis labels."
                + " Embedded caveats are shortened; complete protocol/coverage caveats are in figure_captions.tex."
                + " PNGs use only JDK Java2D/ImageIO, including headless environments; no extra dependencies.");
        guide.setProperty("coverage", "Whole retained with finite metric, not original native grid."
                + " Exported members have no native cell coordinates; native-grid coverage cannot be verified.");
        guide.setProperty("validity", "Only analysis.properties status=COMPLETE is usable. RUNNING/FAILED outputs"
                + " are incomplete; source manifest/members hashes are checked before and after reporting."
                + " Producer counters, when present, must equal observed member counts, run controls and zero scheduling simulations."
                + " Existing output and output within/above the source study are rejected.");
        store(path, guide);
    }

    private static Properties metadata(Data data) {
        Properties result = new Properties();
        result.setProperty("protocol", "whole-library-analysis-v1");
        result.setProperty("input-protocol", "whole-library-attributes-v1");
        result.setProperty("input-root", data.root.toString());
        result.setProperty("input-manifest-sha256", data.manifestHash);
        result.setProperty("input-members-sha256", data.membersHash);
        result.setProperty("expected-runs", Integer.toString(data.runs));
        result.setProperty("expected-pool-size", Integer.toString(data.poolSize));
        result.setProperty("control-scope", data.runs == 30 && data.poolSize == 500
                ? "FORMAL_DEFAULT_CONTROLS" : "DIAGNOSTIC_NONDEFAULT_CONTROLS");
        result.setProperty("plot-per-library-panel-cap", Integer.toString(PLOT_CAP));
        result.setProperty("plot-seed", Long.toString(PLOT_SEED));
        result.setProperty("quality-scope", "AVAILABLE_SUBSET_ONLY");
        result.setProperty("sensitivity-primary-scale", "RAW_S");
        result.setProperty("log-sensitivity-role", "MONOTONIC_DISPLAY_ONLY");
        result.setProperty("analysis-scheduling-simulation-count", "0");
        for (Map.Entry<String, String> entry : data.declaredCounts.entrySet()) {
            result.setProperty("input." + entry.getKey(), entry.getValue());
        }
        result.setProperty("inference", "NONE; libraries are the independent units");
        result.setProperty("native-grid-coverage", "NOT_VERIFIABLE_NO_CELL_COORDINATES");
        for (int scope = 0; scope < SCOPES.length; scope++) {
            long total = 0, finiteQuality = 0, unknownQuality = 0, failedQuality = 0, finiteS = 0;
            for (Library library : data.libraries) {
                for (Member member : library.members.values()) {
                    if (scope == 1 && !member.pool) { continue; }
                    total++;
                    if (Double.isFinite(member.quality)) { finiteQuality++; }
                    else if (Double.isNaN(member.quality)) { unknownQuality++; }
                    else { failedQuality++; }
                    if (Double.isFinite(member.sensitivity)) { finiteS++; }
                }
            }
            String prefix = SCOPES[scope] + ".";
            result.setProperty(prefix + "member-count", Long.toString(total));
            result.setProperty(prefix + "quality-finite", Long.toString(finiteQuality));
            result.setProperty(prefix + "quality-unknown", Long.toString(unknownQuality));
            result.setProperty(prefix + "quality-failed", Long.toString(failedQuality));
            result.setProperty(prefix + "sensitivity-finite", Long.toString(finiteS));
            result.setProperty(prefix + "sensitivity-undefined", Long.toString(total - finiteS));
        }
        return result;
    }

    private static final class UniqueProperties extends Properties {
        private static final long serialVersionUID = 1L;
        @Override public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) { throw new IllegalArgumentException("Duplicate manifest property: " + key); }
            return super.put(key, value);
        }
    }

    static final class CsvReader implements AutoCloseable {
        private final PushbackReader reader;

        CsvReader(Path path) throws IOException {
            this(path, HEADER);
        }

        CsvReader(Path path, String expectedHeader) throws IOException {
            BufferedReader source = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            try { equal(source.readLine(), expectedHeader, "CSV exact header"); }
            catch (IOException | RuntimeException failure) {
                try { source.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
            reader = new PushbackReader(source, 1);
        }

        List<String> read() throws IOException {
            List<String> fields = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean quoted = false, closed = false, any = false;
            int character;
            while ((character = reader.read()) != -1) {
                any = true;
                if (quoted) {
                    if (character == '"') {
                        int next = reader.read();
                        if (next == '"') { cell.append('"'); }
                        else {
                            quoted = false; closed = true;
                            if (next != -1) { reader.unread(next); }
                        }
                    } else { cell.append((char) character); }
                } else if (character == ',') {
                    fields.add(cell.toString()); cell.setLength(0); closed = false;
                } else if (character == '\n' || character == '\r') {
                    if (character == '\r') {
                        int next = reader.read();
                        if (next != '\n' && next != -1) { reader.unread(next); }
                    }
                    fields.add(cell.toString());
                    return fields;
                } else if (closed) { throw new IllegalArgumentException("Characters after closing CSV quote"); }
                else if (character == '"') {
                    if (cell.length() != 0) { throw new IllegalArgumentException("Quote in unquoted CSV field"); }
                    quoted = true;
                } else { cell.append((char) character); }
            }
            if (quoted) { throw new IllegalArgumentException("Unclosed CSV quote"); }
            if (!any) { return null; }
            fields.add(cell.toString());
            return fields;
        }

        @Override public void close() throws IOException { reader.close(); }
    }

    private static void store(Path path, Properties properties) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(writer, "Whole-library descriptive analysis; read-only inputs");
        }
    }

    private static int integer(String value, String label) {
        if (!value.matches("[0-9]+")) { throw new IllegalArgumentException(label + " must be a nonnegative integer"); }
        return Integer.parseInt(value);
    }

    private static String nonempty(String value, String label) {
        if (value.trim().isEmpty()) { throw new IllegalArgumentException("Missing " + label); }
        return value;
    }

    private static double number(String value, String label) {
        if (value.isEmpty() || !value.equals(value.trim())) { throw new IllegalArgumentException("Malformed " + label); }
        try { return Double.parseDouble(value); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("Malformed " + label, failure); }
    }

    private static double finiteNonnegative(String value, String label) {
        double result = number(value, label);
        if (!Double.isFinite(result) || result < 0) { throw new IllegalArgumentException(label + " must be finite and nonnegative"); }
        return result;
    }

    private static void equal(String actual, String expected, String label) {
        if (!expected.equals(actual)) { throw new IllegalArgumentException("Expected " + label + "=" + expected + ", got " + actual); }
    }

    private static double[] doubles(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) { result[i] = values.get(i); }
        return result;
    }

    private static String numeric(double value) { return Double.isNaN(value) ? "NA" : Double.toString(value); }
    private static String compact(double value) { return Double.isNaN(value) ? "--" : String.format(Locale.ROOT, "%.4g", value); }
}
