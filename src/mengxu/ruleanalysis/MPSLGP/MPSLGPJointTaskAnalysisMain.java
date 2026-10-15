package mengxu.ruleanalysis.MPSLGP;

import mengxu.ruleanalysis.MPSLGP.MPSLGPTestSummaryMain.Run;
import mengxu.util.qualityIndicator.Hypervolume;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.*;
import static mengxu.ruleanalysis.MPSLGP.MPSLGPTestSummaryMain.*;

/** Read-only joint analysis of one trained rule pair per task, aligned by group and seed. */
public final class MPSLGPJointTaskAnalysisMain {
    private MPSLGPJointTaskAnalysisMain() { }

    public static void main(String[] args) throws Exception {
        execute(args, System.out);
    }

    static Path execute(String[] args, PrintStream out) throws Exception {
        Config config = Config.parse(args);
        if (config.help) {
            out.println("MPSLGPJointTaskAnalysisMain [--groups R4,H3] [--seed N] [--runs 30] [--start-run 0]");
            out.println("  [--summary PATH] [--output NEW_DIRECTORY] [--focus-panels all|R4:5] [--hide-outliers H3:5]");
            out.println("Without --seed, select one common lower-median seed by equal-group mean task IGD rank.");
            out.println("Default output: a new analysis-* directory under <summary>/joint-task-analysis.");
            return null;
        }
        Map<Path, String> inputs = new TreeMap<>();
        Path protocolPath = config.summary.resolve("protocol.properties");
        Properties protocol = readProperties(protocolPath);
        remember(inputs, protocolPath);
        MPSLGPPaperTestMain.Config testing = MPSLGPPaperTestMain.Config.parse(new String[]{
                "--training-root", required(protocol, "training.root"),
                "--output", required(protocol, "test.root"),
                "--scenarios", String.join(",", config.groups),
                "--runs", Integer.toString(config.runs), "--start-run", Integer.toString(config.startRun)});
        if (config.output != null) {
            protectOutput(config.output, config.summary, testing.training, testing.output);
            if (Files.exists(config.output)) { throw new IOException("Use a new --output directory: " + config.output); }
        }
        Path metricPath = config.summary.resolve("per-run.csv"), manifestPath = config.summary.resolve("input-manifest.csv");
        Map<String, Map<String, String>> metrics = new TreeMap<>();
        for (Map<String, String> row : readCsv(metricPath)) {
            String key = identity(required(row, "Group"), integer(row, "Task"), integer(row, "Seed"));
            if (metrics.put(key, row) != null) { throw new IOException("Duplicate metric row: " + key); }
        }
        remember(inputs, metricPath); remember(inputs, manifestPath);
        Map<String, String> expectedInputs = new TreeMap<>();
        for (Map<String, String> row : readCsv(manifestPath)) {
            String key = identity(required(row, "Group"), integer(row, "Task"), integer(row, "Seed"))
                    + "|" + Paths.get(required(row, "Artifact")).toAbsolutePath().normalize();
            if (expectedInputs.put(key, required(row, "SHA256")) != null) { throw new IOException("Duplicate input manifest row: " + key); }
        }
        List<MPSLGPPaperTestMain.Job> jobs = MPSLGPPaperTestMain.plan(testing);
        List<Path> completions = new ArrayList<>();
        for (MPSLGPPaperTestMain.Job job : jobs) { completions.add(job.directory.resolve("completed.properties")); }
        requireCompletions(completions);
        Map<Integer, TargetReference> references = new TreeMap<>();
        List<TaskRun> tasks = new ArrayList<>();
        for (MPSLGPPaperTestMain.Job job : jobs) {
            Run run = read(job, true);
            String key = identity(run.group, run.task, run.seed);
            Map<String, String> metric = metrics.get(key);
            if (metric == null || integer(metric, "Scenario") != run.scenario) {
                throw new IOException("Missing or mismatched summary metric: " + key);
            }
            for (Map.Entry<Path, String> source : run.inputs.entrySet()) {
                String expected = expectedInputs.get(key + "|" + source.getKey().toAbsolutePath().normalize());
                if (!source.getValue().equals(expected)) { throw new IOException("Test differs from summary snapshot: " + source.getKey()); }
                inputs.put(source.getKey(), source.getValue());
            }
            TargetReference reference = references.get(run.scenario);
            if (reference == null) {
                reference = TargetReference.read(config.summary, protocol, run.scenario, inputs);
                references.put(run.scenario, reference);
            }
            tasks.add(new TaskRun(run, reference, metric));
        }
        List<GroupRun> groupRuns = align(tasks, config);
        if (config.focusAll) {
            for (TaskRun task : tasks) { config.focusPanels.add(task.run.group + ":" + task.run.scenario); }
        }
        Set<String> plotOverrides = new LinkedHashSet<>(config.focusPanels);
        plotOverrides.addAll(config.hideOutlierPanels);
        for (String focus : plotOverrides) {
            if (tasks.stream().noneMatch(task -> focus.equals(task.run.group + ":" + task.run.scenario))) {
                throw new IllegalArgumentException("Focused panel is not in the selected data: " + focus);
            }
        }
        List<MPSLGPEmpiricalAttainment.Scenario> attainment =
                MPSLGPEmpiricalAttainment.analyze(tasks, config.groups, config.runs);
        Map<Integer, Double> scores = seedScores(tasks, config);
        int selectedSeed = config.seed == null ? medianSeed(scores) : config.seed;
        List<MPSLGPJointTaskPlots.Panel> panels = new ArrayList<>();
        for (TaskRun task : tasks) {
            if (task.run.seed == selectedSeed) {
                panels.add(new MPSLGPJointTaskPlots.Panel(task.run.group, task.run.scenario, selectedSeed,
                        task.outcomes, task.nondominatedPreferences, task.reference.raw,
                        task.reference.min, task.reference.max, task.unique, task.front.length));
            }
        }
        List<MPSLGPJointTaskPlots.GroupSummary> chartSummaries = new ArrayList<>();
        for (String group : config.groups) {
            List<GroupRun> selected = new ArrayList<>();
            for (GroupRun run : groupRuns) { if (run.group.equals(group)) { selected.add(run); } }
            double valid = selected.stream().mapToInt(run -> run.jointValid).sum() / (config.runs * 200.0);
            double responsive = selected.stream().filter(run -> run.allResponsive).count() / (double)config.runs;
            chartSummaries.add(new MPSLGPJointTaskPlots.GroupSummary(group, config.runs,
                    selected.get(0).tasks.size(), valid, responsive));
        }
        Path output = config.output;
        if (output == null) {
            Path root = config.summary.resolve("joint-task-analysis");
            Files.createDirectories(root);
            output = Files.createTempDirectory(root, "analysis-");
        } else { Files.createDirectories(output); }
        writeData(output, tasks, groupRuns, scores, config, selectedSeed);
        MPSLGPEmpiricalAttainment.write(output, attainment);
        Map<String, String> fontSettings = MPSLGPJointTaskPlots.render(output, config.groups, panels,
                chartSummaries, selectedSeed, attainment, config.focusPanels, config.hideOutlierPanels);
        for (Map.Entry<Path, String> source : inputs.entrySet()) {
            if (!source.getValue().equals(fileHash(source.getKey()))) {
                throw new IOException("Input changed during analysis: " + source.getKey());
            }
        }
        StringBuilder manifest = new StringBuilder("Artifact,SHA256\n");
        for (Map.Entry<Path, String> source : inputs.entrySet()) {
            manifest.append(csv(source.getKey(), source.getValue())).append('\n');
        }
        atomicWrite(output.resolve("input-manifest.csv"), manifest.toString());
        Map<String, String> settings = new TreeMap<>();
        settings.put("format", "mpslgp-joint-task-analysis-v1");
        settings.put("groups", String.join(",", config.groups));
        settings.put("runs.per.group", Integer.toString(config.runs));
        settings.put("start.seed", Integer.toString(config.startRun));
        settings.put("selected.common.seed", Integer.toString(selectedSeed));
        settings.put("seed.selection", config.seed == null
                ? "Average IGD midrank within each task; equal task weights within group; equal group weights; lower median score, ties by seed"
                : "Explicit --seed; same seed for every group/task panel");
        settings.put("preference.bands", "Five bands of 40 ordered preferences; band=min(4,floor(5*w1)); w1=index/199");
        settings.put("invalid.outcomes", "Either objective -1 invalidates the vector; excluded from fronts/quality, retained in validity summaries and response gaps");
        settings.put("joint.validity", "A preference is jointly valid only if every task in the same group/run is valid at that index");
        settings.put("joint.responsiveness", "A run is jointly responsive if every task has more than one distinct valid outcome; this is not a front-quality threshold");
        settings.put("fronts", "Within-task only; empirical references and normalization are taken from the validated paper summary; shared scenarios use shared plot limits");
        settings.put("summary", config.summary.toString());
        settings.put("analysis.class.sha256", classHash(MPSLGPJointTaskAnalysisMain.class));
        settings.put("renderer.class.sha256", classHash(MPSLGPJointTaskPlots.class));
        settings.put("eaf.class.sha256", classHash(MPSLGPEmpiricalAttainment.class));
        settings.put("figure.style", "Paper palette; Latin Modern Roman; bottom legends; no overall titles or prose; explanations in manuscript captions");
        settings.put("figure.palette", "Blue #2563EB; light blue #93C5FD; purple #6E3FA8; light orange #FDBA74; orange #F97316; gray #64748B");
        settings.put("figure.fonts", "Axis labels 27px; tick labels and legends 23px; panel labels 25px");
        settings.put("figure.front.layout", "Pure preference distribution: separate task panels, outcome-only ranges shared per scenario except explicitly focused panels; bottom continuous w1 colorbar");
        settings.put("figure.focus.panels", String.join(",", config.focusPanels));
        settings.put("figure.hide.outlier.panels", String.join(",", config.hideOutlierPanels));
        settings.put("figure.outlier.rule", "Display only: omit dominated valid vectors outside either coordinate's Q1-1.5IQR/Q3+1.5IQR fences, linear quartiles; retain all nondominated vectors; raw metrics and EAF unchanged");
        settings.put("eaf.definition", "2D minimization empirical attainment over independent training seeds; one front per seed, all valid preferences; no objective normalization or empirical reference");
        settings.put("eaf.levels", "25%,50%,75%; required runs=ceil(probability*n); exact right-continuous staircase, no diagonal smoothing");
        settings.put("eaf.figures", attainment.isEmpty()
                ? "No repeated scenario among selected groups; no EAF comparison figure generated"
                : "empirical-attainment.pdf/png: repeated target scenarios only; a single selected group shows its own scenarios");
        settings.put("eaf.failure.treatment", "Same conditional valid-preference fronts as task summaries; all selected seeds retained");
        settings.put("figure.scatter", "Continuous blue-purple-orange weight color; circles5.2px opacity135/255; nondominated circles6.4px opacity210/255 with dark outlines");
        settings.put("joint.summary.display", "Compact LaTeX rows in group-summary-rows.tex, replacing the summary figure");
        settings.putAll(fontSettings);
        atomicWrite(output.resolve("protocol.properties"), serialize(settings));
        Map<String, String> completed = new TreeMap<>();
        completed.put("status", "COMPLETED");
        try (java.util.stream.Stream<Path> files = Files.list(output)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toArray(Path[]::new)) {
                completed.put("sha256." + file.getFileName(), fileHash(file));
            }
        }
        atomicWrite(output.resolve("completed.properties"), serialize(completed));
        out.println("Validated " + tasks.size() + " task runs, " + groupRuns.size() + " joint group runs.");
        out.println("Selected common seed " + selectedSeed + (config.seed == null ? " (prespecified lower-median rank rule)." : " (--seed)."));
        out.println("EAF target scenarios: " + attainment.size() + (attainment.isEmpty() ? "; no repeated target, comparison plot not generated." : "; all selected seeds, not only the representative seed."));
        for (MPSLGPJointTaskPlots.GroupSummary summary : chartSummaries) {
            out.printf(Locale.ROOT, "%s: joint-valid preferences %.4f%%; all-task responsive runs %.2f%%%n",
                    summary.group, 100 * summary.jointValidFraction, 100 * summary.allTasksResponsiveFraction);
        }
        out.println("Saved figures and analysis: " + output);
        return output;
    }

    static final class Config {
        Path summary = Paths.get("src", "mengxu", "algorithm", "multiobjective", "MPSLGP",
                "paper-draft", "results", "full-all-groups-thirty-seeds").toAbsolutePath().normalize();
        Path output;
        List<String> groups = Arrays.asList("R4", "H3");
        Set<String> focusPanels = new LinkedHashSet<>();
        Set<String> hideOutlierPanels = new LinkedHashSet<>();
        int runs = 30, startRun;
        Integer seed;
        boolean help, focusAll;

        static Config parse(String[] args) {
            Config config = new Config();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < args.length; i++) {
                String flag = args[i];
                if (!seen.add(flag)) { throw new IllegalArgumentException("Repeated option: " + flag); }
                if (flag.equals("--help")) { config.help = true; continue; }
                if (i + 1 == args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for " + flag);
                }
                String value = args[++i];
                switch (flag) {
                    case "--summary": config.summary = Paths.get(value).toAbsolutePath().normalize(); break;
                    case "--output": config.output = Paths.get(value).toAbsolutePath().normalize(); break;
                    case "--groups":
                        List<String> groups = new ArrayList<>();
                        for (String item : value.toUpperCase(Locale.ROOT).split(",", -1)) {
                            String name = item.trim();
                            if (!name.matches("R[1-5]|H[1-3]") || groups.contains(name)) {
                                throw new IllegalArgumentException("Invalid or duplicate group: " + item);
                            }
                            groups.add(name);
                        }
                        config.groups = groups; break;
                    case "--focus-panels":
                        if (value.equalsIgnoreCase("all")) { config.focusAll = true; }
                        else { addPanels(value, config.focusPanels); }
                        break;
                    case "--hide-outliers": addPanels(value, config.hideOutlierPanels); break;
                    case "--runs": config.runs = nonnegative(flag, value); break;
                    case "--start-run": config.startRun = nonnegative(flag, value); break;
                    case "--seed": config.seed = nonnegative(flag, value); break;
                    default: throw new IllegalArgumentException("Unknown option: " + flag);
                }
            }
            if (config.runs < 2 || (long)config.startRun + config.runs > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Use at least two runs and a valid seed range.");
            }
            if (config.seed != null && (config.seed < config.startRun || config.seed >= config.startRun + config.runs)) {
                throw new IllegalArgumentException("--seed must belong to the selected run range.");
            }
            Set<String> overrides = new LinkedHashSet<>(config.focusPanels);
            overrides.addAll(config.hideOutlierPanels);
            for (String panel : overrides) {
                if (!config.groups.contains(panel.substring(0, panel.indexOf(':')))) {
                    throw new IllegalArgumentException("Focused panel group is not selected: " + panel);
                }
            }
            return config;
        }

        private static void addPanels(String value, Set<String> panels) {
            for (String item : value.toUpperCase(Locale.ROOT).split(",", -1)) {
                String panel = item.trim();
                if (!panel.matches("(?:R[1-5]|H[1-3]):(?:[1-9]|10)") || !panels.add(panel)) {
                    throw new IllegalArgumentException("Invalid or duplicate plot panel: " + item);
                }
            }
        }
    }

    static final class TargetReference {
        final double[] min, max;
        final double[][] normalized, raw;

        TargetReference(double[] min, double[] max, double[][] normalized) throws IOException {
            this.min = min; this.max = max; this.normalized = normalized;
            if (normalized.length == 0 || nondominated(normalized).length != normalized.length) {
                throw new IOException("Reference must be nonempty, unique and nondominated.");
            }
            Arrays.sort(normalized, Comparator.comparingDouble(point -> point[0]));
            raw = new double[normalized.length][2];
            for (int i = 0; i < normalized.length; i++) {
                for (int j = 0; j < 2; j++) {
                    if (!Double.isFinite(min[j]) || !Double.isFinite(max[j]) || !(max[j] > min[j])
                            || !Double.isFinite(normalized[i][j]) || normalized[i][j] < -1e-10 || normalized[i][j] > 1 + 1e-10) {
                        throw new IOException("Invalid reference bounds or coordinates.");
                    }
                    raw[i][j] = min[j] + normalized[i][j] * (max[j] - min[j]);
                }
            }
        }

        static TargetReference read(Path summary, Properties protocol, int scenario, Map<Path, String> inputs) throws IOException {
            String prefix = "scenario." + scenario;
            double[] min = pair(required(protocol, prefix + ".min")), max = pair(required(protocol, prefix + ".max"));
            Path path = summary.resolve(prefix + ".reference.csv");
            List<Map<String, String>> rows = readCsv(path);
            if (rows.size() != nonnegative(prefix + ".reference.size", required(protocol, prefix + ".reference.size"))) {
                throw new IOException("Reference size mismatch: " + path);
            }
            double[][] points = new double[rows.size()][2];
            for (int i = 0; i < rows.size(); i++) {
                points[i][0] = number(rows.get(i), "NormalizedObjective0");
                points[i][1] = number(rows.get(i), "NormalizedObjective1");
            }
            remember(inputs, path);
            return new TargetReference(min, max, points);
        }
    }

    static final class TaskRun {
        final Run run;
        final TargetReference reference;
        final double[][] outcomes = new double[200][2];
        final boolean[] nondominatedPreferences = new boolean[200];
        final double[][] front;
        final double hv, igd, gd;
        final int unique;

        TaskRun(Run run, TargetReference reference, Map<String, String> metric) throws IOException {
            this.run = run; this.reference = reference;
            double[][] normalized = normalize(run.points, reference.min, reference.max);
            for (double[] point : normalized) {
                for (double value : point) {
                    if (!Double.isFinite(value) || value < -1e-10 || value > 1 + 1e-10) {
                        throw new IOException("Test outcome outside the paper summary's bounds.");
                    }
                }
            }
            front = nondominated(normalized);
            hv = new Hypervolume().calculateHypervolumeNew(front, new double[]{REFERENCE, REFERENCE}) / (REFERENCE * REFERENCE);
            igd = meanDistance(reference.normalized, normalized);
            gd = meanDistance(normalized, reference.normalized);
            unique = uniqueCount(run.points);
            checkMetric(metric, "HVLocal", hv); checkMetric(metric, "IGDLocal", igd);
            checkMetric(metric, "GDLocal", gd); checkMetric(metric, "UniqueOutcomes", unique);
            checkMetric(metric, "NondominatedOutcomes", front.length);
            int valid = 0;
            for (int i = 0; i < 200; i++) {
                if (run.invalidPoints.containsKey(i)) { outcomes[i] = run.invalidPoints.get(i).clone(); }
                else {
                    outcomes[i] = run.points[valid].clone();
                    for (double[] point : front) {
                        if (Arrays.equals(point, normalized[valid])) { nondominatedPreferences[i] = true; break; }
                    }
                    valid++;
                }
            }
        }

        boolean valid(int index) {
            return outcomes[index][0] != MPSLGPTaskEvaluator.INVALID_FITNESS
                    && outcomes[index][1] != MPSLGPTaskEvaluator.INVALID_FITNESS;
        }
    }

    static final class GroupRun {
        final String group;
        final int seed;
        final List<TaskRun> tasks = new ArrayList<>();
        int jointValid;
        boolean allResponsive = true;
        double meanIGD, worstIGD;

        GroupRun(String group, int seed) { this.group = group; this.seed = seed; }

        void finish() {
            for (int i = 0; i < 200; i++) {
                boolean valid = true;
                for (TaskRun task : tasks) { valid &= task.valid(i); }
                if (valid) { jointValid++; }
            }
            for (TaskRun task : tasks) {
                allResponsive &= task.unique > 1;
                meanIGD += task.igd;
                worstIGD = Math.max(worstIGD, task.igd);
            }
            meanIGD /= tasks.size();
        }
    }

    static List<GroupRun> align(List<TaskRun> tasks, Config config) throws IOException {
        List<GroupRun> result = new ArrayList<>();
        for (String group : config.groups) {
            Set<Integer> expectedTasks = new HashSet<>();
            for (TaskRun task : tasks) { if (task.run.group.equals(group)) { expectedTasks.add(task.run.task); } }
            if (expectedTasks.isEmpty()) { throw new IOException("No tasks for " + group); }
            for (int seed = config.startRun; seed < config.startRun + config.runs; seed++) {
                GroupRun run = new GroupRun(group, seed);
                Set<Integer> present = new HashSet<>();
                for (TaskRun task : tasks) {
                    if (task.run.group.equals(group) && task.run.seed == seed) {
                        if (!present.add(task.run.task)) { throw new IOException("Duplicate task in group/run."); }
                        run.tasks.add(task);
                    }
                }
                if (!present.equals(expectedTasks)) { throw new IOException("Incomplete aligned task set: " + group + " seed=" + seed); }
                run.tasks.sort(Comparator.comparingInt(task -> task.run.task));
                run.finish(); result.add(run);
            }
        }
        return result;
    }

    static Map<Integer, Double> seedScores(List<TaskRun> tasks, Config config) throws IOException {
        Map<Integer, Double> scores = new TreeMap<>();
        for (int seed = config.startRun; seed < config.startRun + config.runs; seed++) { scores.put(seed, 0.0); }
        for (String group : config.groups) {
            Map<Integer, List<TaskRun>> byTask = new TreeMap<>();
            for (TaskRun task : tasks) {
                if (task.run.group.equals(group)) { byTask.computeIfAbsent(task.run.task, key -> new ArrayList<>()).add(task); }
            }
            if (byTask.size() != 2 && byTask.size() != 3) { throw new IOException("Paper groups must contain two or three tasks."); }
            for (List<TaskRun> samples : byTask.values()) {
                if (samples.size() != config.runs) { throw new IOException("Incomplete seed-selection sample."); }
                samples.sort(Comparator.comparingDouble((TaskRun task) -> task.igd).thenComparingInt(task -> task.run.seed));
                double[] values = samples.stream().mapToDouble(task -> task.igd).toArray();
                double[] ranks = midranks(values);
                for (int i = 0; i < samples.size(); i++) {
                    int seed = samples.get(i).run.seed;
                    // A common denominator keeps half-integer rank ties exact until the final division.
                    scores.put(seed, scores.get(seed) + ranks[i] * (6 / byTask.size()));
                }
            }
        }
        for (int seed : scores.keySet()) { scores.put(seed, scores.get(seed) / (6 * (double)config.groups.size())); }
        return scores;
    }

    static double[] midranks(double[] sorted) {
        double[] ranks = new double[sorted.length];
        for (int i = 0; i < sorted.length;) {
            int end = i + 1;
            while (end < sorted.length && Double.compare(sorted[end], sorted[i]) == 0) { end++; }
            Arrays.fill(ranks, i, end, ((i + 1) + end) / 2.0);
            i = end;
        }
        return ranks;
    }

    static int medianSeed(Map<Integer, Double> scores) {
        if (scores.isEmpty()) { throw new IllegalArgumentException("No seeds available."); }
        List<Integer> seeds = new ArrayList<>(scores.keySet());
        seeds.sort(Comparator.comparingDouble((Integer seed) -> scores.get(seed)).thenComparingInt(Integer::intValue));
        return seeds.get((seeds.size() - 1) / 2);
    }

    private static void writeData(Path output, List<TaskRun> tasks, List<GroupRun> runs,
                                  Map<Integer, Double> scores, Config config, int selectedSeed) throws IOException {
        StringBuilder taskRows = new StringBuilder("Group,Task,Seed,Scenario,ValidPreferences,InvalidPreferences,UniqueOutcomes,NondominatedOutcomes,HVLocal,IGDLocal,GDLocal,SelectedForPlots\n");
        StringBuilder preferences = new StringBuilder("Group,Task,Seed,Scenario,PreferenceIndex,Band,Weight0,Weight1,Objective0,Objective1,Valid,Nondominated\n");
        StringBuilder bands = new StringBuilder("Group,Task,Seed,Scenario,Band,ValidPreferences,InvalidPreferences,UniqueOutcomes,NondominatedPreferences\n");
        for (TaskRun task : tasks) {
            Run run = task.run;
            taskRows.append(csv(run.group, run.task, run.seed, run.scenario, run.points.length, run.invalidPoints.size(),
                    task.unique, task.front.length, task.hv, task.igd, task.gd, run.seed == selectedSeed)).append('\n');
            for (int band = 0; band < 5; band++) {
                List<double[]> points = new ArrayList<>();
                int nd = 0;
                for (int i = band * 40; i < (band + 1) * 40; i++) {
                    boolean valid = task.valid(i);
                    if (valid) { points.add(task.outcomes[i]); }
                    if (task.nondominatedPreferences[i]) { nd++; }
                    preferences.append(csv(run.group, run.task, run.seed, run.scenario, i, band,
                            i / 199.0, (199 - i) / 199.0, task.outcomes[i][0], task.outcomes[i][1],
                            valid, task.nondominatedPreferences[i])).append('\n');
                }
                bands.append(csv(run.group, run.task, run.seed, run.scenario, band, points.size(), 40 - points.size(),
                        uniqueCount(points.toArray(new double[0][])), nd)).append('\n');
            }
        }
        StringBuilder groupRows = new StringBuilder("Group,Seed,Tasks,JointValidPreferences,JointInvalidPreferences,JointValidFraction,AllTasksResponsive,MeanTaskIGD,WorstTaskIGD,SelectedForPlots\n");
        StringBuilder summary = new StringBuilder("Group,Runs,Tasks,JointPreferenceQueries,JointValidPreferences,JointInvalidPreferences,JointValidFraction,JointValidFractionStd,AllTasksResponsiveRuns,AllTasksResponsiveFraction,MeanTaskIGDMean,MeanTaskIGDStd,WorstTaskIGDMean,WorstTaskIGDStd\n");
        for (GroupRun run : runs) {
            groupRows.append(csv(run.group, run.seed, run.tasks.size(), run.jointValid, 200 - run.jointValid,
                    run.jointValid / 200.0, run.allResponsive, run.meanIGD, run.worstIGD, run.seed == selectedSeed)).append('\n');
        }
        for (String group : config.groups) {
            List<GroupRun> selected = new ArrayList<>();
            for (GroupRun run : runs) { if (run.group.equals(group)) { selected.add(run); } }
            double[] valid = selected.stream().mapToDouble(run -> run.jointValid / 200.0).toArray();
            double[] means = selected.stream().mapToDouble(run -> run.meanIGD).toArray();
            double[] worst = selected.stream().mapToDouble(run -> run.worstIGD).toArray();
            int validCount = selected.stream().mapToInt(run -> run.jointValid).sum();
            long responsive = selected.stream().filter(run -> run.allResponsive).count();
            summary.append(csv(group, selected.size(), selected.get(0).tasks.size(), 200 * selected.size(),
                    validCount, 200 * selected.size() - validCount, mean(valid), sampleStd(valid),
                    responsive, responsive / (double)selected.size(), mean(means), sampleStd(means), mean(worst), sampleStd(worst))).append('\n');
        }
        StringBuilder selection = new StringBuilder("Seed,EqualGroupMeanTaskIGDRank,Selected\n");
        for (Map.Entry<Integer, Double> score : scores.entrySet()) {
            selection.append(csv(score.getKey(), score.getValue(), score.getKey() == selectedSeed)).append('\n');
        }
        atomicWrite(output.resolve("task-run.csv"), taskRows.toString());
        atomicWrite(output.resolve("group-run.csv"), groupRows.toString());
        atomicWrite(output.resolve("group-summary.csv"), summary.toString());
        atomicWrite(output.resolve("preference-outcomes.csv"), preferences.toString());
        atomicWrite(output.resolve("preference-band-summary.csv"), bands.toString());
        atomicWrite(output.resolve("seed-selection.csv"), selection.toString());
    }

    static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) { throw new IOException("Empty CSV: " + path); }
        List<String> header = csvFields(lines.get(0));
        if (new HashSet<>(header).size() != header.size() || header.contains("")) { throw new IOException("Invalid CSV header: " + path); }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = csvFields(lines.get(i));
            if (fields.size() != header.size()) { throw new IOException("Wrong column count at " + path + ":" + (i + 1)); }
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) { row.put(header.get(j), fields.get(j)); }
            rows.add(row);
        }
        return rows;
    }

    static List<String> csvFields(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else { field.append(c); }
            } else if (c == ',') {
                fields.add(field.toString()); field.setLength(0); closed = false;
            } else if (c == '"' && field.length() == 0 && !closed) { quoted = true; }
            else if (c == '"' || closed) { throw new IOException("Malformed CSV quoting."); }
            else { field.append(c); }
        }
        if (quoted) { throw new IOException("Unterminated CSV field."); }
        fields.add(field.toString());
        return fields;
    }

    private static void protectOutput(Path output, Path summary, Path training, Path tests) {
        if (summary.startsWith(output) || output.startsWith(training) || training.startsWith(output)
                || output.startsWith(tests) || tests.startsWith(output)) {
            throw new IllegalArgumentException("Analysis output must not overwrite summary inputs or training/test trees.");
        }
    }

    private static void remember(Map<Path, String> inputs, Path path) throws IOException {
        inputs.put(path.toAbsolutePath().normalize(), fileHash(path));
    }

    private static String classHash(Class<?> type) throws IOException {
        try (InputStream input = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            if (input == null) { throw new IOException("Cannot fingerprint analysis class."); }
            return sha256(input.readAllBytes());
        }
    }

    private static String identity(String group, int task, int seed) { return group + "/" + task + "/" + seed; }
    private static int nonnegative(String field, String value) {
        try {
            int number = Integer.parseInt(value);
            if (number >= 0) { return number; }
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid integer for " + field + ": " + value, e); }
        throw new IllegalArgumentException("Negative integer for " + field + ": " + value);
    }
    private static int integer(Map<String, String> row, String field) throws IOException {
        return nonnegative(field, required(row, field));
    }
    private static double number(Map<String, String> row, String field) throws IOException {
        try {
            double value = Double.parseDouble(required(row, field));
            if (!Double.isFinite(value)) { throw new IOException("Nonfinite " + field); }
            return value;
        } catch (NumberFormatException e) { throw new IOException("Invalid number: " + field, e); }
    }
    private static void checkMetric(Map<String, String> row, String field, double actual) throws IOException {
        double expected = number(row, field);
        if (Math.abs(expected - actual) > 1e-10 * Math.max(1, Math.abs(expected))) {
            throw new IOException("Summary metric differs from validated test outcomes: " + field);
        }
    }
    private static String required(Properties values, String field) throws IOException {
        String value = values.getProperty(field);
        if (value == null || value.isEmpty()) { throw new IOException("Missing protocol field: " + field); }
        return value;
    }
    private static String required(Map<String, String> values, String field) throws IOException {
        String value = values.get(field);
        if (value == null || value.isEmpty()) { throw new IOException("Missing CSV field: " + field); }
        return value;
    }
    private static double[] pair(String text) throws IOException {
        if (!text.startsWith("[") || !text.endsWith("]")) { throw new IOException("Malformed objective bounds: " + text); }
        String[] fields = text.substring(1, text.length() - 1).split(",", -1);
        if (fields.length != 2) { throw new IOException("Expected two objective bounds."); }
        try { return new double[]{Double.parseDouble(fields[0].trim()), Double.parseDouble(fields[1].trim())}; }
        catch (NumberFormatException e) { throw new IOException("Invalid objective bounds.", e); }
    }
}
