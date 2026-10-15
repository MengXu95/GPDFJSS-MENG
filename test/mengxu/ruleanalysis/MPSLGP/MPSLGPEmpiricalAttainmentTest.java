package mengxu.ruleanalysis.MPSLGP;

import mengxu.util.qualityIndicator.Hypervolume;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static mengxu.ruleanalysis.MPSLGP.MPSLGPEmpiricalAttainment.boundary;
import static mengxu.ruleanalysis.MPSLGP.MPSLGPTestSummaryMain.*;

/** Dependency-free runner: execute main with the project classes on the classpath. */
public final class MPSLGPEmpiricalAttainmentTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testKnownBoundaries();
        testThirtyRuns();
        testTiesAndDominance();
        testEmptyFronts();
        testInvalidInputs();
        testProperties();
        testAnalyze();
        testExport();
        System.out.println("PASS: " + assertions + " empirical-attainment assertions.");
    }

    private static void testKnownBoundaries() {
        List<double[][]> fronts = Arrays.asList(
                new double[][]{{1, 9}, {4, 4}, {8, 1}},
                new double[][]{{2, 8}, {5, 3}, {9, .5}},
                new double[][]{{3, 7}, {6, 2}, {10, .25}},
                new double[][]{{1, 10}, {4, 5}, {7, 1.5}});
        equal(boundary(fronts, .25), new double[][]{
                {1, 9}, {2, 8}, {3, 7}, {4, 4}, {5, 3}, {6, 2}, {7, 1.5}, {8, 1}, {9, .5}, {10, .25}});
        equal(boundary(fronts, .5), new double[][]{
                {1, 10}, {2, 9}, {3, 8}, {4, 5}, {5, 4}, {6, 3}, {7, 2}, {8, 1.5}, {9, 1}, {10, .5}});
        equal(boundary(fronts, .75), new double[][]{
                {2, 10}, {3, 9}, {4, 7}, {5, 5}, {6, 4}, {7, 3}, {8, 2}, {9, 1.5}, {10, 1}});
        equal(boundary(fronts, 1), new double[][]{{3, 10}, {4, 8}, {5, 7}, {6, 5}, {7, 4}, {8, 3}, {9, 2}, {10, 1.5}});

        List<double[][]> crossed = Arrays.asList(
                new double[][]{{1, 9}, {5, 2}}, new double[][]{{2, 6}, {4, 3}, {8, 1}});
        equal(boundary(crossed, .5), new double[][]{{1, 9}, {2, 6}, {4, 3}, {5, 2}, {8, 1}});
        equal(boundary(crossed, .75), new double[][]{{2, 9}, {5, 3}, {8, 2}});
        check(attainment(crossed, 3, 7) == 1, "staircase does not interpolate a diagonal");
        check(attainment(crossed, 100, 2) == 2, "final horizontal ray remains attained");
    }

    private static void testThirtyRuns() {
        List<double[][]> fronts = new ArrayList<>();
        for (int i = 1; i <= 30; i++) { fronts.add(new double[][]{{i, i}}); }
        equal(boundary(fronts, .25), new double[][]{{8, 8}});
        equal(boundary(fronts, .5), new double[][]{{15, 15}});
        equal(boundary(fronts, .75), new double[][]{{23, 23}});
        equal(boundary(fronts, 1), new double[][]{{30, 30}});
        equal(boundary(fronts, Math.nextUp(0.0)), new double[][]{{1, 1}});
    }

    private static void testTiesAndDominance() {
        List<double[][]> fronts = Arrays.asList(
                new double[][]{{3, 3}, {1, 8}, {1, 5}, {1, 5}, {3, 7}, {2, 5}, {9, 9}},
                new double[][]{{3, 2}, {1, 5}, {3, 3}, {3, 2}},
                new double[][]{{3, 3}, {1, 5}, {8, 3}});
        double[][][] snapshot = copy(fronts);
        equal(boundary(fronts, .5), new double[][]{{1, 5}, {3, 3}});
        equal(boundary(fronts, 1), new double[][]{{1, 5}, {3, 3}});
        equal(boundary(fronts, .25), new double[][]{{1, 5}, {3, 2}});
        for (int i = 0; i < fronts.size(); i++) {
            check(Arrays.deepEquals(fronts.get(i), snapshot[i]), "inputs are not sorted or mutated");
        }
        equal(boundary(List.<double[][]>of(new double[][]{{-0.0, 4}, {0.0, 1}, {2, -0.0}}), 1),
                new double[][]{{0, 1}, {2, 0}});
        equal(boundary(List.<double[][]>of(new double[][]{
                {Double.MAX_VALUE, Double.MIN_VALUE}, {Double.MIN_VALUE, Double.MAX_VALUE}}), 1),
                new double[][]{{Double.MIN_VALUE, Double.MAX_VALUE}, {Double.MAX_VALUE, Double.MIN_VALUE}});
        List<double[][]> repeated = Arrays.asList(
                new double[][]{{1, 1}, {1, 1}, {1, 1}}, new double[0][]);
        equal(boundary(repeated, .75), new double[0][]);
    }

    private static void testEmptyFronts() {
        List<double[][]> fronts = Arrays.asList(
                new double[][]{{1, 5}, {3, 2}}, new double[0][],
                new double[][]{{2, 4}, {4, 1}}, new double[0][]);
        equal(boundary(fronts, .25), new double[][]{{1, 5}, {2, 4}, {3, 2}, {4, 1}});
        equal(boundary(fronts, .5), new double[][]{{2, 5}, {3, 4}, {4, 2}});
        equal(boundary(fronts, .75), new double[0][]);
        equal(boundary(fronts, 1), new double[0][]);
        equal(boundary(List.<double[][]>of(new double[0][]), .25), new double[0][]);
    }

    private static void testInvalidInputs() throws Exception {
        List<double[][]> good = List.<double[][]>of(new double[][]{{1, 2}});
        for (double probability : new double[]{0, -1, Math.nextDown(0.0), Math.nextUp(1.0),
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            invalid(() -> boundary(good, probability), "invalid probability " + probability);
        }
        for (double value : new double[]{-1, -Double.MIN_VALUE, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (int objective = 0; objective < 2; objective++) {
                double[] point = {1, 1};
                point[objective] = value;
                invalid(() -> boundary(List.<double[][]>of(new double[][]{{0, 0}, point}), .5),
                        "even dominated invalid points rejected");
            }
        }
        invalid(() -> boundary(good, Double.MIN_VALUE * -1), "negative tiny probability");
        invalid(() -> boundary(List.of(), .5), "undefined zero-run denominator");
        invalid(() -> boundary(null, .5), "null fronts");
        invalid(() -> boundary(Arrays.<double[][]>asList((double[][])null), .5), "null per-run front");
        for (double[] point : new double[][]{null, {}, {1}, {1, 2, 3}}) {
            invalid(() -> boundary(List.<double[][]>of(new double[][]{point}), .5), "malformed objective vector");
        }
    }

    private static void testProperties() {
        Random random = new Random(42917);
        for (int sample = 0; sample < 160; sample++) {
            int runs = 1 + random.nextInt(9);
            List<double[][]> fronts = new ArrayList<>();
            for (int run = 0; run < runs; run++) {
                double[][] points = new double[random.nextInt(10)][2];
                for (double[] point : points) {
                    point[0] = random.nextInt(8);
                    point[1] = random.nextInt(8);
                }
                fronts.add(points);
            }
            for (double probability : new double[]{.25, .5, .75, 1}) {
                double[][] vertices = boundary(fronts, probability);
                int required = (int)Math.ceil(probability * runs);
                for (int i = 0; i < vertices.length; i++) {
                    double[] vertex = vertices[i];
                    check(attainment(fronts, vertex[0], vertex[1]) >= required, "vertex reaches kth threshold");
                    check(attainment(fronts, vertex[0], Math.nextDown(vertex[1])) < required,
                            "epsilon-lower y fails kth threshold");
                    if (i > 0) {
                        check(vertex[0] > vertices[i - 1][0] && vertex[1] < vertices[i - 1][1],
                                "strictly increasing x and decreasing y");
                    }
                }
                // Independent brute-force grid checks both completeness and right continuity, not just emitted vertices.
                for (double x = -.5; x <= 8; x += .5) {
                    for (double y = -.5; y <= 8; y += .5) {
                        boolean represented = false;
                        for (double[] vertex : vertices) {
                            if (vertex[0] <= x && vertex[1] <= y) { represented = true; break; }
                        }
                        check(represented == (attainment(fronts, x, y) >= required), "exact staircase grid region");
                    }
                }
            }
        }
    }

    private static void testAnalyze() throws Exception {
        MPSLGPJointTaskAnalysisMain.TaskRun a1 = task("A", 1, 4, 10, 80);
        MPSLGPJointTaskAnalysisMain.TaskRun a2 = task("A", 1, 9, 20, 60);
        MPSLGPJointTaskAnalysisMain.TaskRun b1 = task("B", 1, 4, 30, 50);
        MPSLGPJointTaskAnalysisMain.TaskRun b2 = task("B", 1, 9, 40, 40);
        MPSLGPJointTaskAnalysisMain.TaskRun a3 = task("A", 3, 4, 5, 90);
        MPSLGPJointTaskAnalysisMain.TaskRun a4 = task("A", 3, 9, 15, 70);
        MPSLGPJointTaskAnalysisMain.TaskRun b3 = task("B", 3, 4, 25, 60);
        MPSLGPJointTaskAnalysisMain.TaskRun b4 = task("B", 3, 9, 35, 50);
        MPSLGPJointTaskAnalysisMain.TaskRun exclusive = task("A", 2, 4, 1, 1);
        List<MPSLGPJointTaskAnalysisMain.TaskRun> tasks = new ArrayList<>(
                Arrays.asList(a3, b4, exclusive, b2, a1, b3, a4, b1, a2, task("Unselected", 1, 4, 0, 0)));
        List<MPSLGPEmpiricalAttainment.Scenario> scenarios =
                MPSLGPEmpiricalAttainment.analyze(tasks, Arrays.asList("B", "A", "Absent"), 2);
        check(scenarios.size() == 2 && scenarios.get(0).scenario == 1 && scenarios.get(1).scenario == 3,
                "only shared selected scenarios, sorted ascending");
        for (MPSLGPEmpiricalAttainment.Scenario scenario : scenarios) {
            check(scenario.groups.size() == 2 && scenario.groups.get(0).group.equals("B")
                    && scenario.groups.get(1).group.equals("A"), "requested group order, absent group omitted");
            for (MPSLGPEmpiricalAttainment.Group group : scenario.groups) {
                check(group.runs == 2 && group.levels.size() == 3, "seed denominator, not preferences");
                for (int i = 0; i < 3; i++) {
                    MPSLGPEmpiricalAttainment.Level level = group.levels.get(i);
                    check(level.probability == new double[]{.25, .5, .75}[i]
                            && level.requiredRuns == new int[]{1, 1, 2}[i], "DTO probability and ceiling");
                }
            }
        }
        equal(scenarios.get(0).groups.get(1).levels.get(2).boundary, new double[][]{{20, 80}});
        equal(scenarios.get(1).groups.get(1).levels.get(2).boundary, new double[][]{{15, 90}});
        MPSLGPJointTaskAnalysisMain.TaskRun exclusiveSecond = task("A", 2, 9, 2, 2);
        List<MPSLGPEmpiricalAttainment.Scenario> single = MPSLGPEmpiricalAttainment.analyze(
                Arrays.asList(a3, a4, a1, a2, exclusive, exclusiveSecond), List.of("A"), 2);
        check(single.size() == 3 && single.get(0).scenario == 1
                        && single.get(1).scenario == 2 && single.get(2).scenario == 3,
                "single selected group includes even exclusive scenarios");
        ioFailure(() -> MPSLGPEmpiricalAttainment.analyze(tasks, List.of("A"), 2), "incomplete exclusive scenario");
        ioFailure(() -> MPSLGPEmpiricalAttainment.analyze(Arrays.asList(a1, b1, b2), Arrays.asList("A", "B"), 2),
                "incomplete eligible group");
        ioFailure(() -> MPSLGPEmpiricalAttainment.analyze(Arrays.asList(a1, a1, b1, b2),
                Arrays.asList("A", "B"), 2), "duplicate seed cannot replace missing seed");
        ioFailure(() -> MPSLGPEmpiricalAttainment.analyze(Arrays.asList(a1, a2, b1, b2),
                Arrays.asList("A", "B"), 1), "extra seeds rejected");
        ioFailure(() -> MPSLGPEmpiricalAttainment.analyze(Arrays.asList(a1, a3), List.of("A"), 2),
                "different scenarios never pooled to meet run count");
        invalid(() -> MPSLGPEmpiricalAttainment.analyze(tasks, Arrays.asList("A", "A"), 2), "duplicate groups");
        invalid(() -> MPSLGPEmpiricalAttainment.analyze(tasks, List.of(), 2), "no selected groups");
        invalid(() -> MPSLGPEmpiricalAttainment.analyze(tasks, List.of("A"), 0), "nonpositive expected runs");
        a1.run.points[0][0] = Double.NaN;
        ioFailure(() -> MPSLGPEmpiricalAttainment.analyze(Arrays.asList(a1, a2, b1, b2),
                Arrays.asList("A", "B"), 2), "analysis does not silently drop invalid points");

        MPSLGPJointTaskAnalysisMain.TaskRun empty = task("Empty", 1, 9, 1, 1);
        empty.run.points = new double[0][];
        MPSLGPEmpiricalAttainment.Group withEmpty = MPSLGPEmpiricalAttainment.analyze(
                Arrays.asList(task("Empty", 1, 4, 2, 3), empty), List.of("Empty"), 2).get(0).groups.get(0);
        check(withEmpty.runs == 2, "analysis retains empty-front seed in denominator");
        equal(withEmpty.levels.get(0).boundary, new double[][]{{2, 3}});
        equal(withEmpty.levels.get(1).boundary, new double[][]{{2, 3}});
        equal(withEmpty.levels.get(2).boundary, new double[0][]);

        List<MPSLGPJointTaskAnalysisMain.TaskRun> thirty = new ArrayList<>();
        for (int seed = 0; seed < 30; seed++) { thirty.add(task("A", 8, seed, seed + 1, seed + 1)); }
        MPSLGPEmpiricalAttainment.Group group =
                MPSLGPEmpiricalAttainment.analyze(thirty, List.of("A"), 30).get(0).groups.get(0);
        for (int i = 0; i < 3; i++) {
            int required = new int[]{8, 15, 23}[i];
            check(group.levels.get(i).requiredRuns == required && group.runs == 30, "30-run DTO counts");
            equal(group.levels.get(i).boundary, new double[][]{{required, required}});
        }
    }

    private static MPSLGPJointTaskAnalysisMain.TaskRun task(String group, int scenario, int seed,
                                                           double x, double y) throws IOException {
        Run run = new Run(group, scenario, seed, scenario);
        run.invalidPoints.put(199, new double[]{-1, -1});
        run.points = new double[199][2];
        for (double[] point : run.points) { point[0] = x; point[1] = y; }
        MPSLGPJointTaskAnalysisMain.TargetReference reference = new MPSLGPJointTaskAnalysisMain.TargetReference(
                new double[]{0, 0}, new double[]{100, 100}, new double[][]{{0, 1}, {1, 0}});
        double[][] normalized = normalize(run.points, reference.min, reference.max);
        double[][] front = nondominated(normalized);
        Map<String, String> metrics = new HashMap<>();
        metrics.put("HVLocal", Double.toString(
                new Hypervolume().calculateHypervolumeNew(front, new double[]{REFERENCE, REFERENCE})
                        / (REFERENCE * REFERENCE)));
        metrics.put("IGDLocal", Double.toString(meanDistance(reference.normalized, normalized)));
        metrics.put("GDLocal", Double.toString(meanDistance(normalized, reference.normalized)));
        metrics.put("UniqueOutcomes", "1");
        metrics.put("NondominatedOutcomes", "1");
        return new MPSLGPJointTaskAnalysisMain.TaskRun(run, reference, metrics);
    }

    private static void testExport() throws Exception {
        Path output = Paths.get(".eaf-test-" + UUID.randomUUID()).toAbsolutePath();
        try {
            double[][] points = {{2, 4}, {5, 1}};
            MPSLGPEmpiricalAttainment.Level level = new MPSLGPEmpiricalAttainment.Level(.25, 1, points);
            points[0][0] = 99;
            check(level.boundary[0][0] == 2, "DTO protects supplied boundary from mutation");
            String name = "A,\"quoted\"";
            MPSLGPEmpiricalAttainment.Group group = new MPSLGPEmpiricalAttainment.Group(name, 4,
                    Arrays.asList(level, new MPSLGPEmpiricalAttainment.Level(.5, 2, new double[][]{{5, 4}}),
                            new MPSLGPEmpiricalAttainment.Level(.75, 3, new double[0][])));
            MPSLGPEmpiricalAttainment.write(output,
                    List.of(new MPSLGPEmpiricalAttainment.Scenario(7, List.of(group))));
            List<String> vertices = Files.readAllLines(output.resolve("eaf-boundaries.csv"));
            List<String> summary = Files.readAllLines(output.resolve("eaf-level-summary.csv"));
            check(vertices.get(0).equals("Scenario,Group,Probability,RequiredRuns,TotalRuns,Vertex,Objective0,Objective1"),
                    "boundary schema");
            check(summary.get(0).equals("Scenario,Group,Probability,RequiredRuns,TotalRuns,BoundaryVertices"),
                    "summary schema");
            check(vertices.size() == 4 && summary.size() == 4, "empty level has metadata but no artificial vertices");
            List<String> row = MPSLGPJointTaskAnalysisMain.csvFields(vertices.get(1));
            check(row.equals(Arrays.asList("7", name, "0.25", "1", "4", "1", "2.0", "4.0")),
                    "raw coordinates, CSV escaping, metadata, one-based vertex index");
            check(MPSLGPJointTaskAnalysisMain.csvFields(vertices.get(2)).get(5).equals("2"), "vertex ordering");
            check(MPSLGPJointTaskAnalysisMain.csvFields(summary.get(3))
                    .equals(Arrays.asList("7", name, "0.75", "3", "4", "0")), "empty boundary explicitly recorded");
            MPSLGPEmpiricalAttainment.write(output, List.of());
            check(Files.readAllLines(output.resolve("eaf-boundaries.csv")).size() == 1
                    && Files.readAllLines(output.resolve("eaf-level-summary.csv")).size() == 1,
                    "atomic rewrites do not leave obsolete vertices");
            try (Stream<Path> files = Files.list(output)) {
                check(files.count() == 2, "only requested CSV exports, no leftover atomic-write files");
            }
        } finally {
            if (Files.exists(output)) {
                try (Stream<Path> files = Files.walk(output)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                        Files.delete(file);
                    }
                }
            }
        }
    }

    private static int attainment(List<double[][]> fronts, double x, double y) {
        int count = 0;
        for (double[][] front : fronts) {
            for (double[] point : front) {
                if (point[0] <= x && point[1] <= y) { count++; break; }
            }
        }
        return count;
    }

    private static double[][][] copy(List<double[][]> fronts) {
        return fronts.stream().map(front -> Arrays.stream(front).map(double[]::clone).toArray(double[][]::new))
                .toArray(double[][][]::new);
    }

    private static void equal(double[][] actual, double[][] expected) {
        check(Arrays.deepEquals(actual, expected),
                "expected " + Arrays.deepToString(expected) + ", got " + Arrays.deepToString(actual));
    }

    private static void invalid(Action action, String message) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Expected IllegalArgumentException: " + message);
    }

    private static void ioFailure(Action action, String message) throws Exception {
        try { action.run(); } catch (IOException expected) { assertions++; return; }
        throw new AssertionError("Expected IOException: " + message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new AssertionError(message); }
    }

    @FunctionalInterface
    private interface Action { void run() throws Exception; }
}
