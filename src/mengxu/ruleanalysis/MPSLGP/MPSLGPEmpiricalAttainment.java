package mengxu.ruleanalysis.MPSLGP;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.atomicWrite;
import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.csv;
import static mengxu.ruleanalysis.MPSLGP.MPSLGPTestSummaryMain.nondominated;

/** Exact raw-objective empirical attainment for two-objective minimization. */
final class MPSLGPEmpiricalAttainment {
    private static final double[] PROBABILITIES = {.25, .5, .75};

    private MPSLGPEmpiricalAttainment() { }

    static final class Scenario {
        final int scenario;
        final List<Group> groups;

        Scenario(int scenario, List<Group> groups) {
            this.scenario = scenario;
            this.groups = List.copyOf(groups);
        }
    }

    static final class Group {
        final String group;
        final int runs;
        final List<Level> levels;

        Group(String group, int runs, List<Level> levels) {
            this.group = group;
            this.runs = runs;
            this.levels = List.copyOf(levels);
        }
    }

    static final class Level {
        final double probability;
        final int requiredRuns;
        final double[][] boundary;

        Level(double probability, int requiredRuns, double[][] boundary) {
            this.probability = probability;
            this.requiredRuns = requiredRuns;
            this.boundary = Arrays.stream(boundary).map(double[]::clone).toArray(double[][]::new);
        }
    }

    static List<Scenario> analyze(List<MPSLGPJointTaskAnalysisMain.TaskRun> tasks,
                                  List<String> groups, int expectedRuns) throws IOException {
        if (tasks == null || groups == null || groups.isEmpty() || expectedRuns <= 0) {
            throw new IllegalArgumentException("Select groups and a positive expected run count.");
        }
        Set<String> selected = new HashSet<>();
        for (String group : groups) {
            if (group == null || group.isBlank() || !selected.add(group)) {
                throw new IllegalArgumentException("Group names must be nonempty and distinct.");
            }
        }
        Map<Integer, Map<String, List<MPSLGPJointTaskAnalysisMain.TaskRun>>> byScenario = new TreeMap<>();
        for (MPSLGPJointTaskAnalysisMain.TaskRun task : tasks) {
            if (task == null || task.run == null) { throw new IOException("Missing task run."); }
            if (!selected.contains(task.run.group)) { continue; }
            byScenario.computeIfAbsent(task.run.scenario, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(task.run.group, ignored -> new ArrayList<>()).add(task);
        }
        List<Scenario> result = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, List<MPSLGPJointTaskAnalysisMain.TaskRun>>> entry
                : byScenario.entrySet()) {
            Map<String, List<MPSLGPJointTaskAnalysisMain.TaskRun>> scenarioGroups = entry.getValue();
            if (groups.size() > 1 && scenarioGroups.size() < 2) { continue; }
            List<Group> comparisons = new ArrayList<>();
            for (String group : groups) {
                List<MPSLGPJointTaskAnalysisMain.TaskRun> runs = scenarioGroups.get(group);
                if (runs == null) { continue; }
                String context = "group " + group + ", scenario " + entry.getKey();
                Set<Integer> seeds = new HashSet<>();
                for (MPSLGPJointTaskAnalysisMain.TaskRun task : runs) {
                    if (!seeds.add(task.run.seed)) {
                        throw new IOException("Duplicate seed " + task.run.seed + " for " + context + ".");
                    }
                }
                if (seeds.size() != expectedRuns) {
                    throw new IOException("Expected " + expectedRuns + " distinct seeds for " + context
                            + ", found " + seeds.size() + ".");
                }
                List<double[][]> fronts = new ArrayList<>();
                for (MPSLGPJointTaskAnalysisMain.TaskRun task : runs) {
                    try {
                        // Only valid raw points belong here; invalid preferences are not runs or penalties.
                        fronts.add(rawFront(task.run.points));
                    } catch (IllegalArgumentException e) {
                        throw new IOException("Invalid raw objectives for " + context
                                + ", seed " + task.run.seed + ".", e);
                    }
                }
                List<Level> levels = new ArrayList<>();
                for (double probability : PROBABILITIES) {
                    levels.add(new Level(probability, requiredRuns(probability, fronts.size()),
                            sweep(fronts, probability)));
                }
                comparisons.add(new Group(group, fronts.size(), levels));
            }
            result.add(new Scenario(entry.getKey(), comparisons));
        }
        return List.copyOf(result);
    }

    /**
     * Returns finite vertices of a right-continuous staircase. At a vertex x, its y applies
     * until the next vertex x; the final horizontal segment extends to positive infinity.
     * Empty individual fronts remain in the denominator, so an unattainable level is empty.
     */
    static double[][] boundary(List<double[][]> fronts, double probability) {
        if (fronts == null || fronts.isEmpty()) {
            throw new IllegalArgumentException("At least one run is required.");
        }
        requiredRuns(probability, fronts.size());
        List<double[][]> validated = new ArrayList<>();
        for (double[][] front : fronts) { validated.add(rawFront(front)); }
        return sweep(validated, probability);
    }

    private static double[][] rawFront(double[][] points) {
        if (points == null) { throw new IllegalArgumentException("Missing run front."); }
        double[][] copy = new double[points.length][2];
        for (int i = 0; i < points.length; i++) {
            if (points[i] == null || points[i].length != 2) {
                throw new IllegalArgumentException("Exactly two objectives are required.");
            }
            for (int objective = 0; objective < 2; objective++) {
                double value = points[i][objective];
                if (!Double.isFinite(value) || value < 0) {
                    throw new IllegalArgumentException("Objectives must be finite and nonnegative.");
                }
                copy[i][objective] = value == 0 ? 0.0 : value;
            }
        }
        return nondominated(copy);
    }

    private static int requiredRuns(double probability, int runs) {
        if (!Double.isFinite(probability) || probability <= 0 || probability > 1) {
            throw new IllegalArgumentException("Probability must be in (0, 1].");
        }
        return (int)Math.ceil(probability * runs);
    }

    private static double[][] sweep(List<double[][]> fronts, double probability) {
        int required = requiredRuns(probability, fronts.size());
        Map<Double, List<Event>> events = new TreeMap<>();
        for (int run = 0; run < fronts.size(); run++) {
            for (double[] point : fronts.get(run)) {
                events.computeIfAbsent(point[0], ignored -> new ArrayList<>()).add(new Event(run, point[1]));
            }
        }
        double[] bestY = new double[fronts.size()];
        Arrays.fill(bestY, Double.POSITIVE_INFINITY);
        List<double[]> vertices = new ArrayList<>();
        double previousY = Double.POSITIVE_INFINITY;
        for (Map.Entry<Double, List<Event>> entry : events.entrySet()) {
            // Apply all equal-x updates before measuring the order statistic (right continuity).
            for (Event event : entry.getValue()) {
                bestY[event.run] = Math.min(bestY[event.run], event.y);
            }
            double[] sortedY = bestY.clone();
            Arrays.sort(sortedY);
            double y = sortedY[required - 1];
            if (Double.isFinite(y) && y < previousY) {
                vertices.add(new double[]{entry.getKey(), y});
                previousY = y;
            }
        }
        return vertices.toArray(new double[0][]);
    }

    private static final class Event {
        final int run;
        final double y;

        Event(int run, double y) { this.run = run; this.y = y; }
    }

    /** Exports vertices (not diagonal interpolation) and metadata for every level, including empty ones. */
    static void write(Path output, List<Scenario> scenarios) throws IOException {
        StringBuilder boundaries = new StringBuilder(
                "Scenario,Group,Probability,RequiredRuns,TotalRuns,Vertex,Objective0,Objective1\n");
        StringBuilder summary = new StringBuilder(
                "Scenario,Group,Probability,RequiredRuns,TotalRuns,BoundaryVertices\n");
        for (Scenario scenario : scenarios) {
            for (Group group : scenario.groups) {
                for (Level level : group.levels) {
                    summary.append(csv(scenario.scenario, group.group, level.probability,
                            level.requiredRuns, group.runs, level.boundary.length)).append('\n');
                    for (int vertex = 0; vertex < level.boundary.length; vertex++) {
                        double[] point = level.boundary[vertex];
                        boundaries.append(csv(scenario.scenario, group.group, level.probability,
                                level.requiredRuns, group.runs, vertex + 1, point[0], point[1])).append('\n');
                    }
                }
            }
        }
        Files.createDirectories(output);
        atomicWrite(output.resolve("eaf-boundaries.csv"), boundaries.toString());
        atomicWrite(output.resolve("eaf-level-summary.csv"), summary.toString());
    }
}
