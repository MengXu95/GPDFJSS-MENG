package mengxu.ruleanalysis.MPSLGP;

import mengxu.util.qualityIndicator.Hypervolume;
import mengxu.util.qualityIndicator.util.MetricsUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.*;

/** Descriptive summaries of a complete selected Full/IGD held-out experiment. */
public final class MPSLGPTestSummaryMain {
    static final double REFERENCE = 1.1;
    private static final String[] METRICS = {"HVLocal", "IGDLocal", "GDLocal", "UniqueOutcomes",
            "NondominatedOutcomes", "Objective0Min", "Objective0Max", "Objective1Min", "Objective1Max",
            "SeqNodes", "RoutingNodes", "TrainingSeconds", "TestingSeconds"};

    private MPSLGPTestSummaryMain() { }

    public static void main(String[] args) throws IOException {
        selfTest();
        if (args.length == 1 && args[0].equals("--self-test")) {
            System.out.println("PASS: nondominance, hypervolume, mean-distance and sample-SD checks.");
            return;
        }
        MPSLGPPaperTestMain.Config config = selection(args);
        boolean validOnly = invalidOutcomePolicy(args).equals("valid-only");
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        if (output.startsWith(config.output) || output.startsWith(config.training)
                || config.output.startsWith(output) || config.training.startsWith(output)) {
            throw new IllegalArgumentException("Summary output must be separate from training and test trees.");
        }
        if (Files.exists(output)) { throw new IOException("Use a new output directory: " + output); }
        List<MPSLGPPaperTestMain.Job> jobs = MPSLGPPaperTestMain.plan(config);
        List<Path> markers = new ArrayList<>();
        for (MPSLGPPaperTestMain.Job job : jobs) { markers.add(job.directory.resolve("completed.properties")); }
        requireCompletions(markers);
        List<Run> runs = new ArrayList<>();
        for (MPSLGPPaperTestMain.Job job : jobs) { runs.add(read(job, validOnly)); }
        runs.sort(Comparator.comparingInt((Run run) -> config.scenarios.indexOf(run.group))
                .thenComparingInt(run -> run.task).thenComparingInt(run -> run.seed));
        Map<Integer, List<double[]>> pools = new TreeMap<>();
        for (Run run : runs) {
            pools.computeIfAbsent(run.scenario, key -> new ArrayList<>()).addAll(Arrays.asList(run.points));
        }
        Map<Integer, Reference> references = new TreeMap<>();
        for (Map.Entry<Integer, List<double[]>> pool : pools.entrySet()) {
            references.put(pool.getKey(), new Reference(pool.getValue().toArray(new double[0][])));
        }
        Files.createDirectories(output);
        StringBuilder manifest = new StringBuilder("Group,Task,Seed,Scenario,Artifact,SHA256\n");
        StringBuilder validity = new StringBuilder("Group,Task,Seed,Scenario,ValidPreferences,InvalidPreferences,InvalidFraction\n");
        StringBuilder invalidPreferences = new StringBuilder("Group,Task,Seed,Scenario,PreferenceIndex,Weight0,Weight1,Objective0,Objective1\n");
        StringBuilder perRun = new StringBuilder("Group,Task,Seed,Scenario,");
        perRun.append(String.join(",", METRICS)).append('\n');
        for (Run run : runs) {
            Reference reference = references.get(run.scenario);
            double[][] normalized = normalize(run.points, reference.min, reference.max);
            double[][] nondominated = nondominated(normalized);
            run.values = new double[]{new Hypervolume().calculateHypervolumeNew(nondominated,
                    new double[]{REFERENCE, REFERENCE}) / (REFERENCE * REFERENCE),
                    meanDistance(reference.front, normalized), meanDistance(normalized, reference.front),
                    uniqueCount(run.points), nondominated.length,
                    minimum(run.points, 0), maximum(run.points, 0), minimum(run.points, 1), maximum(run.points, 1),
                    run.seqNodes, run.routingNodes, run.trainingSeconds, run.testingSeconds};
            perRun.append(run.group).append(',').append(run.task).append(',').append(run.seed).append(',').append(run.scenario);
            for (double value : run.values) { perRun.append(',').append(value); }
            perRun.append('\n');
            validity.append(run.group).append(',').append(run.task).append(',').append(run.seed).append(',')
                    .append(run.scenario).append(',').append(run.points.length).append(',')
                    .append(run.invalidPoints.size()).append(',').append(run.invalidPoints.size() / 200.0).append('\n');
            for (Map.Entry<Integer, double[]> invalid : run.invalidPoints.entrySet()) {
                int index = invalid.getKey();
                invalidPreferences.append(run.group).append(',').append(run.task).append(',').append(run.seed).append(',')
                        .append(run.scenario).append(',').append(index).append(',').append(index / 199.0).append(',')
                        .append((199 - index) / 199.0).append(',').append(invalid.getValue()[0]).append(',')
                        .append(invalid.getValue()[1]).append('\n');
            }
            for (Map.Entry<Path, String> input : run.inputs.entrySet()) {
                manifest.append(csv(run.group, run.task, run.seed, run.scenario, input.getKey(), input.getValue())).append('\n');
            }
        }
        atomicWrite(output.resolve("per-run.csv"), perRun.toString());
        atomicWrite(output.resolve("input-manifest.csv"), manifest.toString());
        atomicWrite(output.resolve("validity-per-run.csv"), validity.toString());
        atomicWrite(output.resolve("invalid-preferences.csv"), invalidPreferences.toString());
        StringBuilder summary = new StringBuilder("Group,Task,Scenario,Runs");
        for (String metric : METRICS) { summary.append(',').append(metric).append("Mean,").append(metric).append("Std"); }
        summary.append('\n');
        StringBuilder tableVII = new StringBuilder("Group,Scenario,Runs,HVLocalMean,HVLocalStd,IGDLocalMean,IGDLocalStd,GDLocalMean,GDLocalStd\n");
        StringBuilder tableVIII = new StringBuilder("Group,Scenario,Runs,DistinctOutcomesMean,DistinctOutcomesStd,NondominatedOutcomesMean,NondominatedOutcomesStd,SequencingNodesMean,SequencingNodesStd,RoutingNodesMean,RoutingNodesStd\n");
        StringBuilder validitySummary = new StringBuilder("Group,Task,Scenario,Runs,PreferenceRows,ValidPreferences,InvalidPreferences,InvalidFraction,RunsWithInvalid,SingleValidOutcomeRuns,MinimumValidPreferences\n");
        Map<String, List<Run>> groups = new LinkedHashMap<>();
        for (Run run : runs) { groups.computeIfAbsent(run.group + "/" + run.task, key -> new ArrayList<>()).add(run); }
        for (List<Run> group : groups.values()) {
            Run first = group.get(0);
            summary.append(first.group).append(',').append(first.task).append(',').append(first.scenario).append(',').append(group.size());
            tableVII.append(first.group).append(',').append(first.scenario).append(',').append(group.size());
            tableVIII.append(first.group).append(',').append(first.scenario).append(',').append(group.size());
            StringBuilder displayed = new StringBuilder(first.group + " / scenario " + first.scenario + ": ");
            for (int index = 0; index < METRICS.length; index++) {
                double[] values = new double[group.size()];
                for (int i = 0; i < group.size(); i++) { values[i] = group.get(i).values[index]; }
                double mean = mean(values), sd = sampleStd(values);
                summary.append(',').append(mean).append(',').append(sd);
                if (index < 3) { tableVII.append(',').append(mean).append(',').append(sd); }
                if (index == 3 || index == 4 || index == 9 || index == 10) {
                    tableVIII.append(',').append(mean).append(',').append(sd);
                }
                displayed.append(METRICS[index]).append('=')
                        .append(String.format(Locale.ROOT, "%.3f(%.3f) ", mean, sd));
            }
            summary.append('\n');
            tableVII.append('\n');
            tableVIII.append('\n');
            int invalidRows = 0, affectedRuns = 0, singleOutcomes = 0, minimumValid = 200;
            for (Run run : group) {
                invalidRows += run.invalidPoints.size();
                if (!run.invalidPoints.isEmpty()) { affectedRuns++; }
                if (uniqueCount(run.points) == 1) { singleOutcomes++; }
                minimumValid = Math.min(minimumValid, run.points.length);
            }
            validitySummary.append(first.group).append(',').append(first.task).append(',').append(first.scenario)
                    .append(',').append(group.size()).append(',').append(group.size() * 200).append(',')
                    .append(group.size() * 200 - invalidRows).append(',').append(invalidRows).append(',')
                    .append(invalidRows / (group.size() * 200.0)).append(',').append(affectedRuns).append(',')
                    .append(singleOutcomes).append(',').append(minimumValid).append('\n');
            System.out.println(displayed);
        }
        atomicWrite(output.resolve("mean-std.csv"), summary.toString());
        atomicWrite(output.resolve("table-vii-mpslgp.csv"), tableVII.toString());
        atomicWrite(output.resolve("table-viii.csv"), tableVIII.toString());
        atomicWrite(output.resolve("validity-summary.csv"), validitySummary.toString());
        int invalidRows = runs.stream().mapToInt(run -> run.invalidPoints.size()).sum();
        Map<String, String> protocol = new TreeMap<>();
        protocol.put("scope", "Full MPSLGP, IGD-trained, groups " + String.join(",", config.scenarios)
                + ", seeds " + config.startRun + "-" + (config.startRun + config.runs - 1) + "; no PSLGP raw fronts available");
        protocol.put("task.runs", Integer.toString(runs.size()));
        protocol.put("preference.rows", Integer.toString(runs.size() * 200));
        protocol.put("valid.preference.rows", Integer.toString(runs.size() * 200 - invalidRows));
        protocol.put("invalid.preference.rows", Integer.toString(invalidRows));
        protocol.put("runs.with.invalid", Long.toString(runs.stream().filter(run -> !run.invalidPoints.isEmpty()).count()));
        protocol.put("invalid.outcome.policy", validOnly
                ? "Conditional valid-only metrics: exclude each vector with either objective -1; keep every selected seed; disclose all invalid rows separately"
                : "Reject invalid-fitness sentinels");
        protocol.put("test.root", config.output.toString());
        protocol.put("training.root", config.training.toString());
        protocol.put("table.vii", "MPSLGP-only descriptive indicators; published PSLGP-I columns are not recomputed or calibrated here");
        protocol.put("statistical.unit", "One trained heuristic per target task and training seed");
        protocol.put("standard.deviation", "Sample standard deviation, denominator n-1, n=" + config.runs);
        protocol.put("normalisation", "Per-target coordinatewise min/max over valid outcomes of all selected runs, pooling that target across selected groups");
        protocol.put("hypervolume", "2D minimisation hypervolume of unique nondominated normalized points, reference (1.1,1.1), divided by 1.21");
        protocol.put("reference.front", "Unique nondominated union of normalized observed MPSLGP outcomes for that target, including the evaluated runs");
        protocol.put("IGD", "Arithmetic mean Euclidean nearest-outcome distance from pooled valid reference front to valid normalized outcomes");
        protocol.put("GD", "Arithmetic mean Euclidean nearest-reference distance from valid normalized outcomes to pooled valid reference front; denominator is the run's valid preference count");
        protocol.put("distance.convention", "Matches paper equations (3)-(4), not the bundled sqrt(sum(squared distances))/n variant");
        protocol.put("limitations", "Descriptive in-sample empirical reference; valid-only metrics are conditional and do not penalize failed preferences; not calibrated to published PSLGP tables; no cross-method significance or claims of superiority");
        protocol.put("unique.outcomes", "Exact distinct 30-replication mean objective vectors, not distinct rules or decision phenotypes");
        protocol.put("training.seconds", "Full group runtime repeated on task rows; do not sum task rows as separate training costs");
        for (Map.Entry<Integer, Reference> entry : references.entrySet()) {
            Reference reference = entry.getValue();
            String key = "scenario." + entry.getKey();
            protocol.put(key + ".min", Arrays.toString(reference.min));
            protocol.put(key + ".max", Arrays.toString(reference.max));
            protocol.put(key + ".reference.size", Integer.toString(reference.front.length));
            StringBuilder front = new StringBuilder("NormalizedObjective0,NormalizedObjective1\n");
            for (double[] point : reference.front) { front.append(point[0]).append(',').append(point[1]).append('\n'); }
            atomicWrite(output.resolve(key + ".reference.csv"), front.toString());
        }
        atomicWrite(output.resolve("protocol.properties"), serialize(protocol));
        System.out.println("Validated " + runs.size() + " task runs / " + (runs.size() * 200)
                + " preference rows. Saved " + output);
        if (validOnly) {
            System.out.println("CONDITIONAL valid-only metrics: " + invalidRows
                    + " invalid preferences excluded, all selected seeds retained; see validity-summary.csv.");
        }
    }

    static MPSLGPPaperTestMain.Config selection(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: MPSLGPTestSummaryMain <paper-tests root> <new output directory>"
                    + " [--scenarios R1,H3] [--runs 5] [--start-run 0] [--training-root PATH]"
                    + " [--invalid-outcomes reject|valid-only]");
        }
        invalidOutcomePolicy(args);
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        List<String> options = new ArrayList<>();
        for (int i = 2; i < args.length; i += 2) {
            if (!Arrays.asList("--scenarios", "--runs", "--start-run", "--training-root", "--invalid-outcomes").contains(args[i])
                    || i + 1 >= args.length) {
                throw new IllegalArgumentException("Unsupported or incomplete summary option: " + args[i]);
            }
            if (args[i].equals("--invalid-outcomes")) { continue; }
            options.add(args[i]); options.add(args[i + 1]);
        }
        if (!options.contains("--training-root")) {
            options.add("--training-root"); options.add(root.resolveSibling("paper-runs").toString());
        }
        options.add("--output"); options.add(root.toString());
        MPSLGPPaperTestMain.Config config = MPSLGPPaperTestMain.Config.parse(options.toArray(new String[0]));
        if (config.runs < 2) { throw new IllegalArgumentException("At least two runs are required for sample standard deviation."); }
        return config;
    }

    static String invalidOutcomePolicy(String[] args) {
        String policy = "reject";
        boolean supplied = false;
        for (int i = 2; i < args.length; i += 2) {
            if (args[i].equals("--invalid-outcomes")) {
                if (supplied || i + 1 >= args.length
                        || !Arrays.asList("reject", "valid-only").contains(args[i + 1])) {
                    throw new IllegalArgumentException("Use --invalid-outcomes reject or valid-only exactly once.");
                }
                policy = args[i + 1]; supplied = true;
            }
        }
        return policy;
    }

    static void requireCompletions(List<Path> markers) throws IOException {
        List<Path> missing = new ArrayList<>();
        for (Path marker : markers) { if (!Files.isRegularFile(marker)) { missing.add(marker); } }
        if (!missing.isEmpty()) {
            StringBuilder message = new StringBuilder("Incomplete held-out experiment: ")
                    .append(missing.size()).append(" of ").append(markers.size())
                    .append(" task completions missing. No summary was written.");
            for (int i = 0; i < Math.min(10, missing.size()); i++) { message.append('\n').append(missing.get(i)); }
            throw new IOException(message.toString());
        }
    }

    static Run read(MPSLGPPaperTestMain.Job job, boolean validOnly) throws IOException {
        if (!Arrays.asList("full", "no-transfer", "fixed-transfer").contains(job.algorithm)) {
            throw new IOException("Unsupported summary profile: " + job.algorithm);
        }
        Path directory = job.directory;
        Properties completion = readProperties(directory.resolve("completed.properties"));
        require(completion, "status", "COMPLETED");
        String attemptName = completion.getProperty("attempt", "");
        if (!attemptName.matches("attempt-\\d{4,}")) { throw new IOException("Invalid attempt: " + directory); }
        Path attempt = directory.resolve(attemptName);
        Properties properties = readProperties(attempt.resolve("test.properties"));
        for (Map.Entry<String, String> field : job.spec.entrySet()) { require(properties, field.getKey(), field.getValue()); }
        require(completion, "configuration.sha256", job.fingerprint);
        Run run = new Run(job.scenario, job.task, job.run, Integer.parseInt(job.spec.get("benchmark.scenario")));
        Map<String, String> spec = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) { spec.put(key, properties.getProperty(key)); }
        int invalidRows = MPSLGPPaperTestMain.validateOutputs(attempt, spec);
        if (invalidRows > 0 && !validOnly) {
            throw new IOException("Cannot compute quality indicators: " + invalidRows
                    + " preference rows contain the -1 invalid-fitness sentinel in " + attempt
                    + ". Define an explicit failure-treatment protocol first; no summary was written.");
        }
        if (completion.containsKey("invalid.preference.rows")) {
            require(completion, "invalid.preference.rows", Integer.toString(invalidRows));
        }
        if (completion.containsKey("valid.preference.rows")) {
            require(completion, "valid.preference.rows", Integer.toString(200 - invalidRows));
        }
        String fileName = spec.get("output.csv");
        for (String name : Arrays.asList("test.properties", "selected-heuristic.properties", fileName,
                "preference-results.csv", "testing.log")) {
            Path file = attempt.resolve(name);
            String hash = fileHash(file);
            require(completion, "sha256." + name, hash);
            run.inputs.put(file, hash);
        }
        require(completion, "configuration.sha256", fileHash(attempt.resolve("test.properties")));
        for (String source : Arrays.asList("source.stats", "source.params")) {
            Path path = Paths.get(spec.get(source));
            String hash = fileHash(path);
            if (!hash.equals(spec.get(source + ".sha256"))) { throw new IOException("Training source changed: " + path); }
            run.inputs.put(path, hash);
        }
        Properties training = readProperties(Paths.get(spec.get("source.params")));
        for (String[] field : new String[][]{{"pop.subpop.0.size", "1000"}, {"generations", "50"},
                {"seed.0", Integer.toString(job.run)}, {"compare-criteria", "IGD"},
                {"mpslgp.adaptive-transfer", Boolean.toString(job.algorithm.equals("full"))},
                {"mpslgp.use-no-transfer-gate", "true"}}) {
            require(training, field[0], field[1]);
        }
        if (job.algorithm.equals("no-transfer")) {
            require(training, "mpslgp.transfer-probability", "0.0");
            require(training, "mpslgp.max-transfer-probability", "0.0");
        } else if (job.algorithm.equals("fixed-transfer")) {
            double probability = Double.parseDouble(training.getProperty("mpslgp.transfer-probability", "NaN"));
            double maximum = Double.parseDouble(training.getProperty("mpslgp.max-transfer-probability", "NaN"));
            if (!Double.isFinite(probability) || probability <= 0 || probability > 1
                    || !Double.isFinite(maximum) || maximum <= 0 || maximum > 1) {
                throw new IOException("Fixed-transfer requires a positive finite transfer probability and budget.");
            }
        }
        Path trainingCompletion = Paths.get(spec.get("source.stats")).getParent().getParent().resolve("completed.properties");
        run.trainingSeconds = Long.parseLong(readProperties(trainingCompletion).getProperty("duration.seconds"));
        run.testingSeconds = Long.parseLong(completion.getProperty("duration.seconds"));
        run.inputs.put(trainingCompletion, fileHash(trainingCompletion));
        run.inputs.put(directory.resolve("completed.properties"), fileHash(directory.resolve("completed.properties")));
        List<String> rows = Files.readAllLines(attempt.resolve(fileName), StandardCharsets.UTF_8);
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            String[] fields = rows.get(i + 1).split(",");
            double[] point = {Double.parseDouble(fields[8]), Double.parseDouble(fields[10])};
            if (point[0] == MPSLGPTaskEvaluator.INVALID_FITNESS || point[1] == MPSLGPTaskEvaluator.INVALID_FITNESS) {
                run.invalidPoints.put(i, point);
            } else { points.add(point); }
        }
        if (points.isEmpty()) { throw new IOException("No valid preferences in " + attempt + "; cannot retain this seed in quality statistics."); }
        run.points = points.toArray(new double[0][]);
        String[] fields = rows.get(1).split(",");
        run.seqNodes = Integer.parseInt(fields[2]);
        run.routingNodes = Integer.parseInt(fields[4]);
        return run;
    }

    private static void require(Properties properties, String key, String expected) throws IOException {
        if (!expected.equals(properties.getProperty(key))) { throw new IOException("Metadata mismatch: " + key); }
    }

    static final class Run {
        final String group;
        final int task, seed, scenario;
        final Map<Path, String> inputs = new TreeMap<>();
        final Map<Integer, double[]> invalidPoints = new TreeMap<>();
        double[][] points;
        double[] values;
        int seqNodes, routingNodes;
        long trainingSeconds, testingSeconds;
        Run(String group, int task, int seed, int scenario) {
            this.group = group; this.task = task; this.seed = seed; this.scenario = scenario;
        }
    }

    private static final class Reference {
        final double[] min = new double[2], max = new double[2];
        final double[][] front;
        Reference(double[][] points) {
            for (int i = 0; i < 2; i++) { min[i] = minimum(points, i); max[i] = maximum(points, i); }
            front = nondominated(normalize(points, min, max));
        }
    }

    private static double minimum(double[][] points, int dimension) {
        return Arrays.stream(points).mapToDouble(point -> point[dimension]).min().orElseThrow(IllegalArgumentException::new);
    }
    private static double maximum(double[][] points, int dimension) {
        return Arrays.stream(points).mapToDouble(point -> point[dimension]).max().orElseThrow(IllegalArgumentException::new);
    }
    static double[][] normalize(double[][] points, double[] min, double[] max) {
        double[][] result = new double[points.length][2];
        for (int i = 0; i < points.length; i++) {
            for (int j = 0; j < 2; j++) {
                if (!(max[j] > min[j])) { throw new IllegalArgumentException("Zero objective range; explicit protocol decision required."); }
                result[i][j] = (points[i][j] - min[j]) / (max[j] - min[j]);
            }
        }
        return result;
    }
    static int uniqueCount(double[][] points) {
        return (int)Arrays.stream(points).map(Arrays::toString).distinct().count();
    }
    static double[][] nondominated(double[][] points) {
        double[][] sorted = Arrays.stream(points).map(double[]::clone).toArray(double[][]::new);
        Arrays.sort(sorted, Comparator.comparingDouble((double[] point) -> point[0]).thenComparingDouble(point -> point[1]));
        List<double[]> result = new ArrayList<>();
        double bestY = Double.POSITIVE_INFINITY;
        for (double[] point : sorted) {
            if (point[1] < bestY) { result.add(point); bestY = point[1]; }
        }
        return result.toArray(new double[0][]);
    }
    static double meanDistance(double[][] from, double[][] to) {
        MetricsUtil utility = new MetricsUtil();
        double sum = 0;
        for (double[] point : from) { sum += utility.distanceToClosedPoint(point, to); }
        return sum / from.length;
    }
    static double mean(double[] values) { return Arrays.stream(values).average().orElseThrow(IllegalArgumentException::new); }
    static double sampleStd(double[] values) {
        double mean = mean(values), sum = 0;
        for (double value : values) { sum += (value - mean) * (value - mean); }
        return Math.sqrt(sum / (values.length - 1));
    }
    private static void selfTest() {
        double[][] front = nondominated(new double[][]{{.5,.4},{.2,.8},{.5,.4},{.8,.8}});
        if (front.length != 2 || Math.abs(new Hypervolume().calculateHypervolumeNew(front, new double[]{1.1,1.1}) - .51) > 1e-12
                || meanDistance(new double[][]{{0,0},{1,0}}, new double[][]{{0,0}}) != .5
                || mean(new double[]{1,2,3}) != 2 || sampleStd(new double[]{1,2,3}) != 1
                || Math.abs(sampleStd(java.util.stream.IntStream.rangeClosed(1, 30).asDoubleStream().toArray())
                        - Math.sqrt(77.5)) > 1e-12) {
            throw new IllegalStateException("Metric self-test failed.");
        }
    }
}
