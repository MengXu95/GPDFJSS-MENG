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

/** Descriptive summaries of the completed Full/IGD R1 and H3 five-seed experiment. */
public final class MPSLGPTestSummaryMain {
    private static final double REFERENCE = 1.1;
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
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: MPSLGPTestSummaryMain <paper-tests root> <new output directory>");
        }
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        if (Files.exists(output)) { throw new IOException("Use a new output directory: " + output); }
        List<Run> runs = new ArrayList<>();
        for (String group : Arrays.asList("R1", "H3")) {
            for (int task = 0; task < (group.equals("R1") ? 2 : 3); task++) {
                for (int seed = 0; seed < 5; seed++) { runs.add(read(root, group, task, seed)); }
            }
        }
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
            for (Map.Entry<Path, String> input : run.inputs.entrySet()) {
                manifest.append(csv(run.group, run.task, run.seed, run.scenario, input.getKey(), input.getValue())).append('\n');
            }
        }
        atomicWrite(output.resolve("per-run.csv"), perRun.toString());
        atomicWrite(output.resolve("input-manifest.csv"), manifest.toString());
        StringBuilder summary = new StringBuilder("Group,Task,Scenario,Runs");
        for (String metric : METRICS) { summary.append(',').append(metric).append("Mean,").append(metric).append("Std"); }
        summary.append('\n');
        Map<String, List<Run>> groups = new LinkedHashMap<>();
        for (Run run : runs) { groups.computeIfAbsent(run.group + "/" + run.task, key -> new ArrayList<>()).add(run); }
        for (List<Run> group : groups.values()) {
            Run first = group.get(0);
            summary.append(first.group).append(',').append(first.task).append(',').append(first.scenario).append(",5");
            StringBuilder displayed = new StringBuilder(first.group + " / scenario " + first.scenario + ": ");
            for (int index = 0; index < METRICS.length; index++) {
                double[] values = new double[5];
                for (int i = 0; i < 5; i++) { values[i] = group.get(i).values[index]; }
                double mean = mean(values), sd = sampleStd(values);
                summary.append(',').append(mean).append(',').append(sd);
                displayed.append(METRICS[index]).append('=')
                        .append(String.format(Locale.ROOT, "%.3f(%.3f) ", mean, sd));
            }
            summary.append('\n');
            System.out.println(displayed);
        }
        atomicWrite(output.resolve("mean-std.csv"), summary.toString());
        Map<String, String> protocol = new TreeMap<>();
        protocol.put("scope", "Full MPSLGP, IGD-trained, R1/H3, seeds 0-4; no PSLGP raw fronts available");
        protocol.put("statistical.unit", "One trained heuristic per target task and training seed");
        protocol.put("standard.deviation", "Sample standard deviation, denominator n-1, n=5");
        protocol.put("normalisation", "Per-target coordinatewise min/max over all 200 outcomes of all available runs; scenario1 pools R1 and H3");
        protocol.put("hypervolume", "2D minimisation hypervolume of unique nondominated normalized points, reference (1.1,1.1), divided by 1.21");
        protocol.put("reference.front", "Unique nondominated union of normalized observed MPSLGP outcomes for that target, including the evaluated runs");
        protocol.put("IGD", "Arithmetic mean Euclidean nearest-outcome distance from pooled reference front to all 200 normalized outcomes");
        protocol.put("GD", "Arithmetic mean Euclidean nearest-reference distance from all 200 normalized outcomes to pooled reference front");
        protocol.put("distance.convention", "Matches paper equations (3)-(4), not the bundled sqrt(sum(squared distances))/n variant");
        protocol.put("limitations", "Descriptive in-sample empirical reference; not calibrated to published PSLGP tables; no cross-method significance or claims of superiority");
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
        System.out.println("Validated 25 task runs / 5000 preference rows. Saved " + output);
    }

    private static Run read(Path root, String group, int task, int seed) throws IOException {
        Path directory = root.resolve("full/IGD").resolve(group)
                .resolve(String.format(Locale.ROOT, "run%02d/task%02d", seed, task));
        Properties completion = readProperties(directory.resolve("completed.properties"));
        require(completion, "status", "COMPLETED");
        String attemptName = completion.getProperty("attempt", "");
        if (!attemptName.matches("attempt-\\d{4,}")) { throw new IOException("Invalid attempt: " + directory); }
        Path attempt = directory.resolve(attemptName);
        Properties properties = readProperties(attempt.resolve("test.properties"));
        for (String[] field : new String[][]{{"algorithm", "full"}, {"indicator", "IGD"}, {"scenario", group},
                {"task", Integer.toString(task)}, {"run", Integer.toString(seed)}, {"preferences", "200"},
                {"replications", "30"}, {"generation", "49"}, {"smoke", "false"}, {"test.seed", "968356"}}) {
            require(properties, field[0], field[1]);
        }
        Run run = new Run(group, task, seed, Integer.parseInt(properties.getProperty("benchmark.scenario")));
        Map<String, String> spec = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) { spec.put(key, properties.getProperty(key)); }
        MPSLGPPaperTestMain.validateOutputs(attempt, spec);
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
                {"seed.0", Integer.toString(seed)}, {"compare-criteria", "IGD"},
                {"mpslgp.adaptive-transfer", "true"}, {"mpslgp.use-no-transfer-gate", "true"}}) {
            require(training, field[0], field[1]);
        }
        Path trainingCompletion = Paths.get(spec.get("source.stats")).getParent().getParent().resolve("completed.properties");
        run.trainingSeconds = Long.parseLong(readProperties(trainingCompletion).getProperty("duration.seconds"));
        run.testingSeconds = Long.parseLong(completion.getProperty("duration.seconds"));
        run.inputs.put(trainingCompletion, fileHash(trainingCompletion));
        run.inputs.put(directory.resolve("completed.properties"), fileHash(directory.resolve("completed.properties")));
        List<String> rows = Files.readAllLines(attempt.resolve(fileName), StandardCharsets.UTF_8);
        run.points = new double[200][2];
        for (int i = 0; i < 200; i++) {
            String[] fields = rows.get(i + 1).split(",");
            run.points[i][0] = Double.parseDouble(fields[8]);
            run.points[i][1] = Double.parseDouble(fields[10]);
        }
        String[] fields = rows.get(1).split(",");
        run.seqNodes = Integer.parseInt(fields[2]);
        run.routingNodes = Integer.parseInt(fields[4]);
        return run;
    }

    private static void require(Properties properties, String key, String expected) throws IOException {
        if (!expected.equals(properties.getProperty(key))) { throw new IOException("Metadata mismatch: " + key); }
    }

    private static final class Run {
        final String group;
        final int task, seed, scenario;
        final Map<Path, String> inputs = new TreeMap<>();
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
    private static double[][] normalize(double[][] points, double[] min, double[] max) {
        double[][] result = new double[points.length][2];
        for (int i = 0; i < points.length; i++) {
            for (int j = 0; j < 2; j++) {
                if (!(max[j] > min[j])) { throw new IllegalArgumentException("Zero objective range; explicit protocol decision required."); }
                result[i][j] = (points[i][j] - min[j]) / (max[j] - min[j]);
            }
        }
        return result;
    }
    private static int uniqueCount(double[][] points) {
        return (int)Arrays.stream(points).map(Arrays::toString).distinct().count();
    }
    private static double[][] nondominated(double[][] points) {
        double[][] sorted = Arrays.stream(points).map(double[]::clone).toArray(double[][]::new);
        Arrays.sort(sorted, Comparator.comparingDouble((double[] point) -> point[0]).thenComparingDouble(point -> point[1]));
        List<double[]> result = new ArrayList<>();
        double bestY = Double.POSITIVE_INFINITY;
        for (double[] point : sorted) {
            if (point[1] < bestY) { result.add(point); bestY = point[1]; }
        }
        return result.toArray(new double[0][]);
    }
    private static double meanDistance(double[][] from, double[][] to) {
        MetricsUtil utility = new MetricsUtil();
        double sum = 0;
        for (double[] point : from) { sum += utility.distanceToClosedPoint(point, to); }
        return sum / from.length;
    }
    private static double mean(double[] values) { return Arrays.stream(values).average().orElseThrow(IllegalArgumentException::new); }
    private static double sampleStd(double[] values) {
        double mean = mean(values), sum = 0;
        for (double value : values) { sum += (value - mean) * (value - mean); }
        return Math.sqrt(sum / (values.length - 1));
    }
    private static void selfTest() {
        double[][] front = nondominated(new double[][]{{.5,.4},{.2,.8},{.5,.4},{.8,.8}});
        if (front.length != 2 || Math.abs(new Hypervolume().calculateHypervolumeNew(front, new double[]{1.1,1.1}) - .51) > 1e-12
                || meanDistance(new double[][]{{0,0},{1,0}}, new double[][]{{0,0}}) != .5
                || mean(new double[]{1,2,3}) != 2 || sampleStd(new double[]{1,2,3}) != 1) {
            throw new IllegalStateException("Metric self-test failed.");
        }
    }
}
