package mengxu.ruleanalysis.MPSLGP;

import mengxu.ruleanalysis.MPSLGP.MPSLGPTestSummaryMain.Run;
import mengxu.util.qualityIndicator.Hypervolume;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.*;
import static mengxu.ruleanalysis.MPSLGP.MPSLGPTestSummaryMain.*;

/** Matched transfer-control comparisons with joint target-specific references and paired seed tests. */
public final class MPSLGPTransferAblationMain {
    private static final List<String> SUPPORTED = Arrays.asList("full", "no-transfer", "fixed-transfer");
    private static final String[] METRICS = {"HVJoint", "IGDJoint", "GDJoint"};
    private static final Set<String> PROFILE_FIELDS = new HashSet<>(Arrays.asList(
            "mpslgp.adaptive-transfer", "mpslgp.transfer-probability", "mpslgp.max-transfer-probability"));

    private MPSLGPTransferAblationMain() { }

    public static void main(String[] args) throws IOException {
        Config config = Config.parse(args);
        if (config.help) {
            System.out.println("MPSLGPTransferAblationMain [--output NEW_DIRECTORY] [--tests PATH] [--training-root PATH]");
            System.out.println("  [--scenarios R1,R2,R3,R4,R5,H1,H2,H3] [--runs 30] [--start-run 0]");
            System.out.println("  [--algorithms full,no-transfer,fixed-transfer]; default full,no-transfer");
            System.out.println("  [--invalid-outcomes valid-only|reject]; no training or simulations.");
            return;
        }
        MPSLGPPaperTestMain.Config scope = config.scope();
        if (config.output.startsWith(scope.output) || scope.output.startsWith(config.output)
                || config.output.startsWith(scope.training) || scope.training.startsWith(config.output)) {
            throw new IllegalArgumentException("Ablation output must be separate from training/test trees.");
        }
        if (Files.exists(config.output)) { throw new IOException("Use a new --output directory: " + config.output); }
        List<MPSLGPPaperTestMain.Job> jobs = MPSLGPPaperTestMain.plan(scope);
        List<Path> markers = new ArrayList<>();
        for (MPSLGPPaperTestMain.Job job : jobs) { markers.add(job.directory.resolve("completed.properties")); }
        requireCompletions(markers);
        List<Sample> samples = new ArrayList<>();
        List<Run> inputRuns = new ArrayList<>();
        Map<String, Map<String, Sample>> pairs = new LinkedHashMap<>();
        Map<Path, String> inputHashes = new TreeMap<>();
        for (MPSLGPPaperTestMain.Job job : jobs) {
            Run run = read(job, config.validOnly);
            inputRuns.add(run);
            Sample sample = new Sample(job.algorithm, run, readProperties(Paths.get(job.spec.get("source.params"))));
            samples.add(sample);
            String key = run.group + "/" + run.task + "/" + run.seed;
            if (pairs.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(sample.algorithm, sample) != null) {
                throw new IOException("Duplicate method/run: " + key);
            }
            inputHashes.putAll(run.inputs);
        }
        String runtimeVersion = requireUniformPreferenceRuntime(inputRuns);
        if (runtimeVersion.equals("legacy-v1")) {
            System.out.println("WARNING: legacy pre-correction training; ablation scores are historical, not corrected-method results.");
        }
        for (Map.Entry<String, Map<String, Sample>> entry : pairs.entrySet()) {
            if (entry.getValue().size() != config.methods.size()) { throw new IOException("Unpaired training seed: " + entry.getKey()); }
            Sample full = entry.getValue().get("full");
            for (String method : config.methods) {
                if (method.equals("full")) { continue; }
                Sample control = entry.getValue().get(method);
                if (full.run.scenario != control.run.scenario) { throw new IOException("Paired target mismatch."); }
                requireMatchedTraining(full.training, control.training, method);
            }
        }
        samples.sort(Comparator.comparingInt((Sample sample) -> scope.scenarios.indexOf(sample.run.group))
                .thenComparingInt(sample -> sample.run.task).thenComparingInt(sample -> sample.run.seed)
                .thenComparingInt(sample -> config.methods.indexOf(sample.algorithm)));
        Map<Integer, List<double[]>> pools = new TreeMap<>();
        for (Sample sample : samples) {
            pools.computeIfAbsent(sample.run.scenario, ignored -> new ArrayList<>()).addAll(Arrays.asList(sample.run.points));
        }
        Map<Integer, JointReference> references = new TreeMap<>();
        for (Map.Entry<Integer, List<double[]>> pool : pools.entrySet()) {
            references.put(pool.getKey(), new JointReference(pool.getValue()));
        }
        for (Sample sample : samples) {
            JointReference reference = references.get(sample.run.scenario);
            double[][] points = normalize(sample.run.points, reference.min, reference.max);
            double[][] front = nondominated(points);
            sample.metrics = new double[]{new Hypervolume().calculateHypervolumeNew(front,
                    new double[]{REFERENCE, REFERENCE}) / (REFERENCE * REFERENCE),
                    meanDistance(reference.front, points), meanDistance(points, reference.front)};
            for (double metric : sample.metrics) {
                if (!Double.isFinite(metric) || metric < 0) { throw new IOException("Invalid ablation quality metric."); }
            }
            sample.unique = uniqueCount(sample.run.points); sample.nondominated = front.length;
        }
        Map<String, Target> targets = new LinkedHashMap<>();
        for (Sample sample : samples) {
            targets.computeIfAbsent(sample.run.group + "/" + sample.run.task,
                    ignored -> new Target(sample.run, config.methods)).byMethod.get(sample.algorithm).add(sample);
        }
        List<Comparison> comparisons = new ArrayList<>();
        for (Target target : targets.values()) {
            for (List<Sample> method : target.byMethod.values()) {
                method.sort(Comparator.comparingInt(sample -> sample.run.seed));
                if (method.size() != scope.runs) { throw new IOException("Incomplete target/method run set."); }
            }
            for (String method : config.methods) {
                if (method.equals("full")) { continue; }
                for (int metric = 0; metric < METRICS.length; metric++) { comparisons.add(new Comparison(target, metric, method)); }
            }
        }
        double[] adjusted = MPSLGPPairedStatistics.holm(comparisons.stream().mapToDouble(test -> test.test.pValue).toArray());
        for (int i = 0; i < comparisons.size(); i++) { comparisons.get(i).adjusted = adjusted[i]; }
        Files.createDirectories(config.output);
        writeSamples(config.output, samples);
        writeTables(config.output, targets, config.methods);
        writeComparisons(config.output, comparisons);
        Map<String, String> protocol = new TreeMap<>();
        protocol.put("format", "mpslgp-transfer-ablation-v2");
        protocol.put("preference.runtime.version", runtimeVersion);
        protocol.put("scope", String.join(" versus ", config.methods) + "; IGD training; groups " + String.join(",", scope.scenarios)
                + "; seeds " + scope.startRun + "-" + (scope.startRun + scope.runs - 1));
        protocol.put("normalisation", "Per-target min/max over ALL selected methods, every selected group and seed, valid preference vectors only");
        protocol.put("reference.front", "Unique nondominated union of ALL selected methods' normalized valid outcomes for each target");
        protocol.put("HV", "Minimization HV of each run's unique nondominated outcomes; reference(1.1,1.1), divided by1.21");
        protocol.put("IGD", "Arithmetic mean nearest-outcome Euclidean distance from joint reference to a run's valid normalized outcomes");
        protocol.put("GD", "Arithmetic mean nearest-reference Euclidean distance over every valid normalized preference outcome");
        protocol.put("invalid.outcomes", config.validOnly
                ? "Conditional valid-only: remove vectors with either objective-1, retain all seeds; no plotting-outlier filtering"
                : "Reject invalid vectors");
        protocol.put("statistical.unit", "One paired training seed for a fixed group/task target");
        protocol.put("standard.deviation", "Sample standard deviation, denominator n-1; n=" + scope.runs);
        protocol.put("paired.test", "Exact two-sided conditional signed-rank test; zero gains omitted; absolute ties midranked; sign-permutation DP");
        protocol.put("multiple.testing", "Holm correction across ALL " + comparisons.size() + " group/task/metric comparisons; family-wise alpha0.05");
        protocol.put("gain.direction", "Positive means Full better: HVFull-HVControl; distancesControl-distancesFull");
        protocol.put("training.control", "No-transfer differs only in three transfer-control fields; fixed-transfer differs only in adaptive-transfer");
        protocol.put("table.references", "All selected methods recomputed jointly; not interchangeable with earlier Full-only or two-method scores");
        protocol.put("table.precision", "CSV five decimals; LaTeX " + (config.methods.size() > 2 ? 4 : 5)
                + " decimals" + (config.methods.size() > 2 ? ", leading zeros omitted" : "")
                + "; bold uses best unrounded means among all selected methods");
        protocol.put("task.runs", Integer.toString(samples.size()));
        for (String method : config.methods) {
            int invalid = samples.stream().filter(sample -> sample.algorithm.equals(method))
                    .mapToInt(sample -> sample.run.invalidPoints.size()).sum();
            int count = (int)samples.stream().filter(sample -> sample.algorithm.equals(method)).count();
            long affected = samples.stream().filter(sample -> sample.algorithm.equals(method) && !sample.run.invalidPoints.isEmpty()).count();
            protocol.put(method + ".preference.rows", Integer.toString(count * 200));
            protocol.put(method + ".valid.rows", Integer.toString(count * 200 - invalid));
            protocol.put(method + ".invalid.rows", Integer.toString(invalid));
            protocol.put(method + ".runs.with.invalid", Long.toString(affected));
        }
        for (Map.Entry<Integer, JointReference> entry : references.entrySet()) {
            String name = "scenario." + entry.getKey();
            JointReference reference = entry.getValue();
            protocol.put(name + ".min", Arrays.toString(reference.min));
            protocol.put(name + ".max", Arrays.toString(reference.max));
            protocol.put(name + ".reference.size", Integer.toString(reference.front.length));
            StringBuilder csv = new StringBuilder("NormalizedObjective0,NormalizedObjective1\n");
            for (double[] point : reference.front) { csv.append(point[0]).append(',').append(point[1]).append('\n'); }
            atomicWrite(config.output.resolve(name + ".reference.csv"), csv.toString());
        }
        atomicWrite(config.output.resolve("protocol.properties"), serialize(protocol));
        StringBuilder manifest = new StringBuilder("Algorithm,Group,Task,Seed,Scenario,Artifact,SHA256\n");
        for (Sample sample : samples) {
            for (Map.Entry<Path, String> input : sample.run.inputs.entrySet()) {
                manifest.append(csv(sample.algorithm, sample.run.group, sample.run.task, sample.run.seed,
                        sample.run.scenario, input.getKey(), input.getValue())).append('\n');
            }
        }
        atomicWrite(config.output.resolve("input-manifest.csv"), manifest.toString());
        for (Map.Entry<Path, String> input : inputHashes.entrySet()) {
            if (!fileHash(input.getKey()).equals(input.getValue())) { throw new IOException("Input changed during analysis: " + input.getKey()); }
        }
        Map<String, String> completed = new TreeMap<>();
        completed.put("status", "COMPLETED");
        completed.put("analysis.class.sha256", classHash(MPSLGPTransferAblationMain.class));
        completed.put("statistics.class.sha256", classHash(MPSLGPPairedStatistics.class));
        try (java.util.stream.Stream<Path> files = Files.list(config.output)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toArray(Path[]::new)) {
                completed.put("sha256." + file.getFileName(), fileHash(file));
            }
        }
        atomicWrite(config.output.resolve("completed.properties"), serialize(completed));
        System.out.println("Validated " + samples.size() + " task runs / " + pairs.size() + " matched group/task/seed sets.");
        System.out.println("Saved " + targets.size() + " target comparisons and " + comparisons.size()
                + " Holm-corrected paired tests: " + config.output);
    }

    static void requireMatchedTraining(Properties full, Properties none) throws IOException {
        requireMatchedTraining(full, none, "no-transfer");
    }

    static void requireMatchedTraining(Properties full, Properties control, String method) throws IOException {
        if (!Arrays.asList("no-transfer", "fixed-transfer").contains(method)) {
            throw new IOException("Unsupported ablation control: " + method);
        }
        if (!"true".equals(full.getProperty("mpslgp.adaptive-transfer"))
                || !"false".equals(control.getProperty("mpslgp.adaptive-transfer"))
                || (method.equals("no-transfer") && (!"0.0".equals(control.getProperty("mpslgp.transfer-probability"))
                || !"0.0".equals(control.getProperty("mpslgp.max-transfer-probability"))))) {
            throw new IOException("Incorrect Full/" + method + " profile controls.");
        }
        try {
            int tasks = Integer.parseInt(full.getProperty("mpslgp.num-tasks", ""));
            double maximum = Double.parseDouble(full.getProperty("mpslgp.max-transfer-probability", ""));
            double probability = Double.parseDouble(full.getProperty("mpslgp.transfer-probability", ""));
            if (tasks < 2 || !Double.isFinite(maximum) || maximum <= 0 || maximum > 1
                    || !Double.isFinite(probability) || Math.abs(probability - maximum / (tasks - 1)) > 1e-12) {
                throw new IOException("Full profile must have a positive donor-count-scaled transfer budget.");
            }
        } catch (NumberFormatException e) { throw new IOException("Invalid Full transfer controls.", e); }
        Set<String> keys = new HashSet<>(full.stringPropertyNames());
        keys.addAll(control.stringPropertyNames());
        keys.removeAll(method.equals("no-transfer") ? PROFILE_FIELDS : java.util.Collections.singleton("mpslgp.adaptive-transfer"));
        for (String key : keys) {
            if (!java.util.Objects.equals(full.getProperty(key), control.getProperty(key))) {
                throw new IOException("Non-ablation training parameter differs: " + key);
            }
        }
    }

    private static void writeSamples(Path output, List<Sample> samples) throws IOException {
        StringBuilder rows = new StringBuilder("Algorithm,Group,Task,Seed,Scenario,HVJoint,IGDJoint,GDJoint,ValidPreferences,InvalidPreferences,UniqueOutcomes,NondominatedOutcomes,SeqNodes,RoutingNodes,TrainingSeconds,TestingSeconds\n");
        StringBuilder invalid = new StringBuilder("Algorithm,Group,Task,Seed,Scenario,PreferenceIndex,Weight0,Weight1,Objective0,Objective1\n");
        for (Sample sample : samples) {
            Run run = sample.run;
            rows.append(csv(sample.algorithm, run.group, run.task, run.seed, run.scenario,
                    sample.metrics[0], sample.metrics[1], sample.metrics[2], run.points.length, run.invalidPoints.size(),
                    sample.unique, sample.nondominated, run.seqNodes, run.routingNodes, run.trainingSeconds, run.testingSeconds)).append('\n');
            for (Map.Entry<Integer, double[]> entry : run.invalidPoints.entrySet()) {
                int i = entry.getKey();
                invalid.append(csv(sample.algorithm, run.group, run.task, run.seed, run.scenario,
                        i, i / 199.0, (199 - i) / 199.0, entry.getValue()[0], entry.getValue()[1])).append('\n');
            }
        }
        atomicWrite(output.resolve("per-run.csv"), rows.toString());
        atomicWrite(output.resolve("invalid-preferences.csv"), invalid.toString());
    }

    private static void writeTables(Path output, Map<String, Target> targets, List<String> methods) throws IOException {
        StringBuilder means = new StringBuilder("Algorithm,Group,Task,Scenario,Runs,HVJointMean,HVJointStd,IGDJointMean,IGDJointStd,GDJointMean,GDJointStd\n");
        StringBuilder validity = new StringBuilder("Algorithm,Group,Task,Scenario,Runs,PreferenceRows,ValidPreferences,InvalidPreferences,InvalidFraction,RunsWithInvalid,SingleValidOutcomeRuns,MinimumValidPreferences\n");
        StringBuilder table = new StringBuilder("Group,Scenario,Runs");
        for (String metric : METRICS) {
            for (String method : methods) { table.append(',').append(columnName(method)).append(metric.replace("Joint", "")); }
        }
        table.append('\n');
        StringBuilder tex = new StringBuilder("\\newcommand{\\MPSLGPTransferAblationRows}{%\n");
        String previous = "";
        for (Target target : targets.values()) {
            if (!previous.isEmpty() && !previous.equals(target.group)) { tex.append("\\midrule\n"); }
            previous = target.group;
            table.append(target.group).append(',').append(target.scenario).append(',').append(target.byMethod.get("full").size());
            tex.append(target.group).append(" & ").append(target.scenario);
            for (String algorithm : methods) {
                List<Sample> method = target.byMethod.get(algorithm);
                means.append(csv(algorithm, target.group, target.task, target.scenario, method.size()));
                for (int m = 0; m < 3; m++) {
                    double[] values = values(method, m);
                    means.append(',').append(mean(values)).append(',').append(sampleStd(values));
                }
                means.append('\n');
                int bad = method.stream().mapToInt(sample -> sample.run.invalidPoints.size()).sum();
                validity.append(csv(algorithm, target.group, target.task, target.scenario, method.size(), method.size() * 200,
                        method.size() * 200 - bad, bad, bad / (method.size() * 200.0),
                        method.stream().filter(sample -> !sample.run.invalidPoints.isEmpty()).count(),
                        method.stream().filter(sample -> sample.unique == 1).count(),
                        method.stream().mapToInt(sample -> sample.run.points.length).min().orElseThrow(IllegalStateException::new))).append('\n');
            }
            for (int metric = 0; metric < 3; metric++) {
                double best = metric == 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                for (String method : methods) {
                    double average = mean(values(target.byMethod.get(method), metric));
                    best = metric == 0 ? Math.max(best, average) : Math.min(best, average);
                }
                for (String method : methods) {
                    double[] points = values(target.byMethod.get(method), metric);
                    double average = mean(points), std = sampleStd(points);
                    table.append(',').append(display(average, std));
                    String text = display(average, std, methods.size() > 2 ? 4 : 5);
                    if (methods.size() > 2) { text = text.replaceAll("(^|\\()0\\.", "$1."); }
                    tex.append(" & ").append(Double.compare(average, best) == 0 ? "\\textbf{" + text + "}" : text);
                }
            }
            table.append('\n'); tex.append(" \\\\\n");
        }
        tex.append("}\n");
        atomicWrite(output.resolve("mean-std.csv"), means.toString());
        atomicWrite(output.resolve("validity-summary.csv"), validity.toString());
        atomicWrite(output.resolve("table-comparison.csv"), table.toString());
        atomicWrite(output.resolve("table-rows.tex"), tex.toString());
    }

    private static void writeComparisons(Path output, List<Comparison> comparisons) throws IOException {
        StringBuilder tests = new StringBuilder("Group,Task,Scenario,Comparator,Metric,Runs,NonzeroPairs,FullMean,ControlMean,MeanGain,PositiveRankSum,NegativeRankSum,PValue,HolmPValue,Decision\n");
        StringBuilder gains = new StringBuilder("Group,Task,Scenario,Seed,Comparator,Metric,Full,Control,Gain\n");
        Map<String, int[][]> counts = new LinkedHashMap<>();
        for (Comparison comparison : comparisons) {
            Target target = comparison.target;
            List<Sample> full = target.byMethod.get("full"), control = target.byMethod.get(comparison.control);
            double f = mean(values(full, comparison.metric)), n = mean(values(control, comparison.metric));
            double averageGain = mean(comparison.gains);
            int direction = comparison.test.positiveRankSum > comparison.test.negativeRankSum ? 1 : -1;
            boolean significant = comparison.adjusted <= .05;
            String decision = significant ? (direction > 0 ? "Full better" : comparison.control + " better") : "Not significant";
            tests.append(csv(target.group, target.task, target.scenario, comparison.control, METRICS[comparison.metric], full.size(),
                    comparison.test.nonzeroPairs, f, n, averageGain, comparison.test.positiveRankSum,
                    comparison.test.negativeRankSum, comparison.test.pValue, comparison.adjusted, decision)).append('\n');
            int[][] count = counts.computeIfAbsent(comparison.control, ignored -> new int[3][6]);
            count[comparison.metric][averageGain > 0 ? 0 : averageGain < 0 ? 2 : 1]++;
            count[comparison.metric][significant ? (direction > 0 ? 3 : 5) : 4]++;
            for (int i = 0; i < full.size(); i++) {
                gains.append(csv(target.group, target.task, target.scenario, full.get(i).run.seed,
                        comparison.control, METRICS[comparison.metric], full.get(i).metrics[comparison.metric],
                        control.get(i).metrics[comparison.metric], comparison.gains[i])).append('\n');
            }
        }
        StringBuilder summary = new StringBuilder("Comparator,Metric,Comparisons,MeanBetter,MeanEqual,MeanWorse,SignificantlyBetter,NotSignificant,SignificantlyWorse\n");
        for (Map.Entry<String, int[][]> entry : counts.entrySet()) {
            for (int metric = 0; metric < 3; metric++) {
                int total = entry.getValue()[metric][0] + entry.getValue()[metric][1] + entry.getValue()[metric][2];
                summary.append(entry.getKey()).append(',').append(METRICS[metric]).append(',').append(total);
                for (int count : entry.getValue()[metric]) { summary.append(',').append(count); }
                summary.append('\n');
            }
        }
        atomicWrite(output.resolve("paired-tests.csv"), tests.toString());
        atomicWrite(output.resolve("paired-gains.csv"), gains.toString());
        atomicWrite(output.resolve("paired-test-summary.csv"), summary.toString());
    }

    private static String display(double mean, double std) { return display(mean, std, 5); }
    private static String display(double mean, double std, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f(%." + digits + "f)", mean, std);
    }
    private static String columnName(String method) {
        if (method.equals("full")) { return "Full"; }
        return method.equals("no-transfer") ? "NoTransfer" : "FixedTransfer";
    }
    private static double[] values(List<Sample> samples, int metric) { return samples.stream().mapToDouble(sample -> sample.metrics[metric]).toArray(); }
    private static String classHash(Class<?> type) throws IOException {
        try (java.io.InputStream input = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            if (input == null) { throw new IOException("Cannot fingerprint analysis class."); }
            return sha256(input.readAllBytes());
        }
    }

    private static final class JointReference {
        final double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        final double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        final double[][] front;
        JointReference(List<double[]> points) {
            for (double[] point : points) {
                for (int i = 0; i < 2; i++) { min[i] = Math.min(min[i], point[i]); max[i] = Math.max(max[i], point[i]); }
            }
            front = nondominated(normalize(points.toArray(new double[0][]), min, max));
        }
    }
    private static final class Sample {
        final String algorithm;
        final Run run;
        final Properties training;
        double[] metrics;
        int unique, nondominated;
        Sample(String algorithm, Run run, Properties training) { this.algorithm = algorithm; this.run = run; this.training = training; }
    }
    private static final class Target {
        final String group;
        final int task, scenario;
        final Map<String, List<Sample>> byMethod = new LinkedHashMap<>();
        Target(Run run, List<String> methods) {
            group = run.group; task = run.task; scenario = run.scenario;
            for (String method : methods) { byMethod.put(method, new ArrayList<>()); }
        }
    }
    private static final class Comparison {
        final Target target;
        final int metric;
        final String control;
        final double[] gains;
        final MPSLGPPairedStatistics.Result test;
        double adjusted;
        Comparison(Target target, int metric, String control) throws IOException {
            this.target = target; this.metric = metric; this.control = control;
            List<Sample> full = target.byMethod.get("full"), none = target.byMethod.get(control);
            gains = new double[full.size()];
            for (int i = 0; i < gains.length; i++) {
                if (full.get(i).run.seed != none.get(i).run.seed) { throw new IOException("Unmatched statistical pair."); }
                gains[i] = (metric == 0 ? 1 : -1) * (full.get(i).metrics[metric] - none.get(i).metrics[metric]);
            }
            test = MPSLGPPairedStatistics.wilcoxon(gains);
        }
    }

    static final class Config {
        Path tests = Paths.get("src", "mengxu", "ruleanalysis", "MPSLGP", "paper-tests").toAbsolutePath().normalize();
        Path training = tests.resolveSibling("paper-runs");
        Path output = Paths.get("src", "mengxu", "algorithm", "multiobjective", "MPSLGP",
                "paper-draft", "results", "full-vs-no-transfer-thirty-seeds").toAbsolutePath().normalize();
        String scenarios = "R1,R2,R3,R4,R5,H1,H2,H3", runs = "30", start = "0";
        List<String> methods = Arrays.asList("full", "no-transfer");
        boolean validOnly = true, help;
        static Config parse(String[] args) {
            Config config = new Config();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < args.length; i++) {
                String option = args[i];
                if (!seen.add(option)) { throw new IllegalArgumentException("Repeated option: " + option); }
                if (option.equals("--help")) { config.help = true; continue; }
                if (i + 1 == args.length || args[i + 1].startsWith("--")) { throw new IllegalArgumentException("Missing value for " + option); }
                String value = args[++i];
                switch (option) {
                    case "--tests": config.tests = Paths.get(value).toAbsolutePath().normalize(); break;
                    case "--training-root": config.training = Paths.get(value).toAbsolutePath().normalize(); break;
                    case "--output": config.output = Paths.get(value).toAbsolutePath().normalize(); break;
                    case "--scenarios": config.scenarios = value; break;
                    case "--runs": config.runs = value; break;
                    case "--start-run": config.start = value; break;
                    case "--algorithms":
                        List<String> methods = new ArrayList<>();
                        for (String item : value.toLowerCase(Locale.ROOT).split(",", -1)) {
                            String method = item.trim();
                            if (!SUPPORTED.contains(method) || methods.contains(method)) {
                                throw new IllegalArgumentException("Invalid or duplicate ablation algorithm: " + item);
                            }
                            methods.add(method);
                        }
                        if (!methods.contains("full") || methods.size() < 2) {
                            throw new IllegalArgumentException("Select Full and at least one transfer control.");
                        }
                        methods.sort(Comparator.comparingInt(SUPPORTED::indexOf));
                        config.methods = methods; break;
                    case "--invalid-outcomes":
                        if (!Arrays.asList("reject", "valid-only").contains(value)) { throw new IllegalArgumentException("Invalid outcome policy."); }
                        config.validOnly = value.equals("valid-only"); break;
                    default: throw new IllegalArgumentException("Unsupported option: " + option);
                }
                if (!seen.contains("--output") && config.methods.contains("fixed-transfer")) {
                    String name = config.methods.contains("no-transfer") ? "full-vs-no-vs-fixed-transfer-thirty-seeds"
                            : "full-vs-fixed-transfer-thirty-seeds";
                    config.output = config.output.resolveSibling(name);
                }
            }
            if (!config.help) {
                int runs = config.scope().runs;
                if (runs < 2 || runs > 60) { throw new IllegalArgumentException("Use 2--60 runs for sample SD and exact signed-rank tests."); }
            }
            return config;
        }
        MPSLGPPaperTestMain.Config scope() {
            return MPSLGPPaperTestMain.Config.parse(new String[]{"--training-root", training.toString(),
                    "--output", tests.toString(), "--algorithms", String.join(",", methods), "--indicators", "IGD",
                    "--scenarios", scenarios, "--runs", runs, "--start-run", start});
        }
    }
}
