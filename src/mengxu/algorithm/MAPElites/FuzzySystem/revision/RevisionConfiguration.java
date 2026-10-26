package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import yimei.jss.jobshop.Objective;

public final class RevisionConfiguration {
    public final Path inputRoot;
    public final Path archiveDirectory;
    public final Path outputRoot;
    public final String method;
    public final String scenario;
    public final List<Integer> runs;
    public final int poolSize;
    public final int topK;
    public final int validationInstances;
    public final int testInstances;
    public final long validationSeed;
    public final long testSeed;
    public final long selectionSeed;
    public final long probeSeed;
    public final long probeSamplingSeed;
    public final int probeCount;
    public final int probeCandidates;
    public final int probeJobs;
    public final int probeWarmup;
    public final int machines;
    public final int jobs;
    public final int warmup;
    public final double epsilon;
    public final double utilisation;
    public final double dueFactor;
    public final Objective objective;
    public final boolean dryRun;
    public final boolean allMetrics;
    public final boolean legacyProfiles;
    public final boolean legacyNormalisation;
    private final Map<String, String> options;

    private RevisionConfiguration(Map<String, String> supplied) throws IOException {
        Map<String, String> values = defaults();
        for (Map.Entry<String, String> entry : supplied.entrySet()) {
            if (!values.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("Unknown option: " + entry.getKey());
            }
            values.put(entry.getKey(), entry.getValue());
        }
        options = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        inputRoot = Paths.get(required(values, "input-root")).toRealPath();
        if (!Files.isDirectory(inputRoot)) {
            throw new IllegalArgumentException("input-root is not a directory");
        }
        method = required(values, "method");
        scenario = required(values, "scenario");
        if (!method.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("method must be a single directory name");
        }
        int last = scenario.lastIndexOf('-');
        int previous = scenario.lastIndexOf('-', last - 1);
        if (previous <= 0 || last <= previous) {
            throw new IllegalArgumentException("scenario must be <objective>-<utilisation>-<due-factor>");
        }
        objective = Objective.get(scenario.substring(0, previous));
        if (objective != Objective.MAX_FLOWTIME && objective != Objective.MEAN_TARDINESS
                && objective != Objective.MEAN_WEIGHTED_FLOWTIME) {
            throw new IllegalArgumentException("Unsupported paper objective in " + scenario);
        }
        utilisation = Double.parseDouble(scenario.substring(previous + 1, last));
        dueFactor = Double.parseDouble(scenario.substring(last + 1));
        if (!Double.isFinite(utilisation) || utilisation <= 0 || utilisation >= 1
                || !Double.isFinite(dueFactor) || dueFactor <= 0) {
            throw new IllegalArgumentException("Invalid scenario utilisation or due-date factor");
        }
        String archiveOption = values.get("archive-dir");
        archiveDirectory = (archiveOption.isEmpty()
                ? inputRoot.resolve(method).resolve("multipletreegp-dynamic").resolve(scenario)
                : Paths.get(archiveOption)).toRealPath();
        if (!Files.isDirectory(archiveDirectory) || !archiveDirectory.startsWith(inputRoot)) {
            throw new IllegalArgumentException("archive-dir must be a directory inside input-root");
        }
        dryRun = booleanOption(values, "dry-run");
        String output = values.get("output-root");
        if (output.isEmpty() && !dryRun) {
            throw new IllegalArgumentException("output-root is required for execution");
        }
        outputRoot = output.isEmpty() ? null : checkedOutput(Paths.get(output), inputRoot);
        runs = parseRuns(required(values, "runs"));
        poolSize = positiveInt(values, "pool-size");
        topK = positiveInt(values, "top-k");
        if (topK > poolSize) { throw new IllegalArgumentException("top-k exceeds pool-size"); }
        validationInstances = positiveInt(values, "validation-instances");
        testInstances = positiveInt(values, "test-instances");
        validationSeed = Long.parseLong(required(values, "validation-seed"));
        testSeed = Long.parseLong(required(values, "test-seed"));
        long validationEnd = Math.addExact(validationSeed, validationInstances - 1);
        long testEnd = Math.addExact(testSeed, testInstances - 1);
        if (validationSeed <= testEnd && testSeed <= validationEnd) {
            throw new IllegalArgumentException("Validation and test seed ranges overlap");
        }
        selectionSeed = Long.parseLong(required(values, "selection-seed"));
        probeSeed = Long.parseLong(required(values, "probe-seed"));
        probeSamplingSeed = Long.parseLong(required(values, "probe-sampling-seed"));
        if ((probeSeed >= validationSeed && probeSeed <= validationEnd)
                || (probeSeed >= testSeed && probeSeed <= testEnd)) {
            throw new IllegalArgumentException("Probe seed overlaps validation or test seeds");
        }
        probeCount = positiveInt(values, "probe-count");
        probeCandidates = positiveInt(values, "probe-candidates");
        machines = positiveInt(values, "machines");
        jobs = positiveInt(values, "recorded-jobs");
        warmup = nonnegativeInt(values, "warmup-jobs");
        probeJobs = positiveInt(values, "probe-jobs");
        probeWarmup = nonnegativeInt(values, "probe-warmup");
        epsilon = Double.parseDouble(required(values, "epsilon"));
        new OutputSensitivityCalculator(epsilon);
        if (probeCandidates < 2 || probeCandidates > machines) {
            throw new IllegalArgumentException("probe-candidates must lie between 2 and machines");
        }
        String scope = required(values, "metrics-scope");
        if (!scope.equals("all") && !scope.equals("pool")) {
            throw new IllegalArgumentException("metrics-scope must be all or pool");
        }
        allMetrics = scope.equals("all");
        String profiles = required(values, "profiles");
        if (!profiles.equals("paper-v2") && !profiles.equals("legacy")) {
            throw new IllegalArgumentException("profiles must be paper-v2 or legacy");
        }
        legacyProfiles = profiles.equals("legacy");
        String normalisation = required(values, "normalisation");
        if (!normalisation.equals("all-minmax") && !normalisation.equals("legacy-reciprocal")) {
            throw new IllegalArgumentException("Unknown normalisation");
        }
        legacyNormalisation = normalisation.equals("legacy-reciprocal");
    }

    public Map<String, String> options() { return options; }

    public static RevisionConfiguration fromArgs(String[] args) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--config")) {
                if (++i >= args.length) { throw new IllegalArgumentException("--config needs a file"); }
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(Paths.get(args[i]), StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                for (String key : properties.stringPropertyNames()) {
                    values.put(key, properties.getProperty(key).trim());
                }
            }
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--config")) { i++; continue; }
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Expected --option, found " + args[i]);
            }
            String key = args[i].substring(2);
            if (key.isEmpty()) { throw new IllegalArgumentException("Empty option"); }
            if (key.equals("dry-run")) { values.put(key, "true"); continue; }
            if (++i >= args.length) { throw new IllegalArgumentException("Missing value for --" + key); }
            values.put(key, args[i]);
        }
        return new RevisionConfiguration(values);
    }

    static Path checkedOutput(Path output, Path input) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        if (Files.exists(absolute, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Refusing to overwrite existing output: " + absolute);
        }
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) { existing = existing.getParent(); }
        if (existing == null || !Files.isDirectory(existing)) {
            throw new IllegalArgumentException("Output has no existing parent directory");
        }
        Path resolved = existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
        if (resolved.startsWith(input) || input.startsWith(resolved)) {
            throw new IllegalArgumentException("Output must be outside the read-only input root");
        }
        return resolved;
    }

    private static List<Integer> parseRuns(String specification) {
        TreeSet<Integer> values = new TreeSet<>();
        if (specification.contains(":")) {
            String[] range = specification.split(":", -1);
            if (range.length != 2) { throw new IllegalArgumentException("Invalid runs range"); }
            int first = Integer.parseInt(range[0]);
            int last = Integer.parseInt(range[1]);
            if (first < 0 || last < first || (long) last - first > 999) {
                throw new IllegalArgumentException("runs range must contain 1..1000 non-negative IDs");
            }
            for (long i = first; i <= last; i++) { values.add((int) i); }
        } else {
            for (String part : specification.split(",", -1)) {
                int run = Integer.parseInt(part.trim());
                if (run < 0 || !values.add(run)) {
                    throw new IllegalArgumentException("Run IDs must be distinct and non-negative");
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing required option: " + key);
        }
        return value;
    }

    private static int positiveInt(Map<String, String> values, String key) {
        int value = Integer.parseInt(required(values, key));
        if (value < 1) { throw new IllegalArgumentException(key + " must be positive"); }
        return value;
    }

    private static int nonnegativeInt(Map<String, String> values, String key) {
        int value = Integer.parseInt(required(values, key));
        if (value < 0) { throw new IllegalArgumentException(key + " must be non-negative"); }
        return value;
    }

    private static boolean booleanOption(Map<String, String> values, String key) {
        String value = required(values, key);
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static Map<String, String> defaults() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("input-root", "");
        values.put("archive-dir", "");
        values.put("output-root", "");
        values.put("method", "NfixIns");
        values.put("scenario", "mean-tardiness-0.85-1.5");
        values.put("runs", "0:29");
        values.put("pool-size", "500");
        values.put("top-k", "3");
        values.put("validation-instances", "5");
        values.put("test-instances", "50");
        values.put("validation-seed", "718341");
        values.put("test-seed", "968356");
        values.put("selection-seed", "42");
        values.put("probe-seed", "777");
        values.put("probe-sampling-seed", "8295342");
        values.put("probe-count", "20");
        values.put("probe-candidates", "3");
        values.put("probe-jobs", "1000");
        values.put("probe-warmup", "500");
        values.put("machines", "10");
        values.put("recorded-jobs", "1000");
        values.put("warmup-jobs", "500");
        values.put("epsilon", "0.001");
        values.put("metrics-scope", "pool");
        values.put("profiles", "paper-v2");
        values.put("normalisation", "all-minmax");
        values.put("dry-run", "false");
        return values;
    }
}
