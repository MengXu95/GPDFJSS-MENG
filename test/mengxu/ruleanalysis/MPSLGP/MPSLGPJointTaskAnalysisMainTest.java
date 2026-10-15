package mengxu.ruleanalysis.MPSLGP;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.*;

public final class MPSLGPJointTaskAnalysisMainTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testArguments();
        testCsvAndSelection();
        testPlotOutliers();
        Path temp = Files.createTempDirectory("mpslgp-joint-analysis-test-");
        try {
            Path training = temp.resolve("paper-runs"), tests = temp.resolve("paper-tests"), summary = temp.resolve("summary");
            for (int seed = 0; seed < 4; seed++) {
                MPSLGPTestSummaryMainTest.trainingFixture(training, "R4", new int[]{1,3,5}, seed);
                MPSLGPTestSummaryMainTest.trainingFixture(training, "H3", new int[]{1,5,8}, seed);
            }
            String[] summaryArgs = {tests.toString(), summary.toString(), "--scenarios", "R4,H3", "--runs", "4",
                    "--invalid-outcomes", "valid-only"};
            List<MPSLGPPaperTestMain.Job> jobs = MPSLGPPaperTestMain.plan(MPSLGPTestSummaryMain.selection(summaryArgs));
            for (MPSLGPPaperTestMain.Job job : jobs) {
                MPSLGPTestSummaryMainTest.testFixture(job,
                        job.scenario.equals("H3") && job.run == 3 && job.task == 0 ? 5 : 0);
                if (job.scenario.equals("R4") && job.run == 2 && job.task == 0) { collapse(job); }
            }
            MPSLGPTestSummaryMain.main(summaryArgs);
            Path output = temp.resolve("figures");
            String[] analysis = {"--summary", summary.toString(), "--groups", "R4,H3", "--runs", "4",
                    "--seed", "3", "--output", output.toString()};
            ByteArrayOutputStream log = new ByteArrayOutputStream();
            check(MPSLGPJointTaskAnalysisMain.execute(analysis, new PrintStream(log)).equals(output), "explicit output directory");
            check(log.toString("UTF-8").contains("Selected common seed 3"), "one common explicit seed");
            List<Map<String, String>> taskRows = rows(output, "task-run.csv");
            List<Map<String, String>> groupRows = rows(output, "group-run.csv");
            check(taskRows.size() == 24 && groupRows.size() == 8, "complete task/run and group/run shapes");
            check(rows(output, "preference-outcomes.csv").size() == 4800
                    && rows(output, "preference-band-summary.csv").size() == 120, "all preferences and five bands exported");
            check(taskRows.stream().filter(row -> row.get("SelectedForPlots").equals("true")).count() == 6,
                    "all six selected task panels use the same seed");
            List<Map<String, String>> groupSummary = rows(output, "group-summary.csv");
            check(groupSummary.size() == 2, "one summary per group");
            check(groupSummary.get(0).get("JointInvalidPreferences").equals("0")
                    && groupSummary.get(0).get("AllTasksResponsiveRuns").equals("3"), "joint collapse detected independently of validity");
            check(groupSummary.get(1).get("JointInvalidPreferences").equals("5")
                    && groupSummary.get(1).get("AllTasksResponsiveRuns").equals("4"), "partial invalidity preserved without discarding a seed");
            check(Math.abs(Double.parseDouble(groupSummary.get(1).get("JointValidFraction")) - 795 / 800.0) < 1e-12,
                    "joint validity denominator is group preferences, not task rows");
            for (String name : Arrays.asList("joint-fronts", "preference-response", "empirical-attainment")) {
                for (String extension : Arrays.asList(".png", ".pdf")) {
                    check(Files.size(output.resolve(name + extension)) > 1000, "nonempty plot " + name + extension);
                }
                java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(output.resolve(name + ".png").toFile());
                check(image != null && image.getWidth() >= 1000, "readable PNG dimensions " + name);
                check(image.getHeight() <= 930, "compact panels and bottom legend " + name);
                check(colorCount(image, 0x2563EB) > 10 && colorCount(image, 0xF97316) > 10,
                        "paper blue/orange palette " + name);
            }
            String table = Files.readString(output.resolve("group-summary-rows.tex"));
            check(table.contains("R4 & 800/800 (100.0\\%) & 3/4 (75.0\\%)")
                    && table.contains("H3 & 795/800 (99.4\\%) & 4/4 (100.0\\%)"),
                    "LaTeX summary preserves exact joint and run denominators");
            check(!Files.exists(output.resolve("group-summary.pdf"))
                    && !Files.exists(output.resolve("group-summary.png")), "summary exported as LaTeX rather than a figure");
            String scatterPdf = new String(Files.readAllBytes(output.resolve("joint-fronts.pdf")),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            check(java.util.regex.Pattern.compile("/ca\\s+(?:0?\\.\\d+)").matcher(scatterPdf).find(),
                    "scatter transparency preserved in vector PDF");
            List<Map<String, String>> levels = rows(output, "eaf-level-summary.csv");
            check(levels.size() == 12, "two shared scenarios times two groups times three EAF levels");
            check(levels.stream().allMatch(row -> row.get("Scenario").equals("1") || row.get("Scenario").equals("5")),
                    "EAF never pools different target scenarios");
            check(levels.stream().allMatch(row -> row.get("TotalRuns").equals("4")),
                    "EAF includes every training seed rather than only selected seed");
            Properties style = readProperties(output.resolve("protocol.properties"));
            check(style.getProperty("figure.fonts").contains("27px")
                    && style.getProperty("figure.style").contains("no overall titles"), "large fonts and caption-oriented style recorded");
            check(style.getProperty("figure.font.family").contains("Latin Modern Roman")
                    && style.getProperty("figure.pdf.fonts").contains("Embedded"), "manuscript fonts embedded in PDF");
            List<Map<String, String>> viewports = rows(output, "front-viewports.csv");
            check(viewports.size() == 6, "one full-range viewport audit per task panel");
            List<Map<String, String>> points = rows(output, "preference-outcomes.csv");
            for (Map<String, String> viewport : viewports) {
                check(viewport.get("VisiblePreferences").equals(viewport.get("ValidPreferences")), "every valid sample remains visible");
                for (Map<String, String> point : points) {
                    if (!point.get("Group").equals(viewport.get("Group")) || !point.get("Scenario").equals(viewport.get("Scenario"))
                            || !point.get("Seed").equals(viewport.get("Seed")) || !point.get("Valid").equals("true")) { continue; }
                    double x = Double.parseDouble(point.get("Objective0")), y = Double.parseDouble(point.get("Objective1"));
                    check(inRange(x, viewport, "XMin", "XMax") && inRange(y, viewport, "YMin", "YMax"),
                            "all valid points visible in full-range plot");
                }
            }
            Properties completion = readProperties(output.resolve("completed.properties"));
            check(completion.getProperty("status").equals("COMPLETED"), "analysis completion marker");
            for (String key : completion.stringPropertyNames()) {
                if (key.startsWith("sha256.")) {
                    check(fileHash(output.resolve(key.substring(7))).equals(completion.getProperty(key)), "output hash " + key);
                }
            }
            String[] focused = Arrays.copyOf(analysis, analysis.length + 2);
            Path focusOutput = temp.resolve("focused");
            focused[Arrays.asList(focused).indexOf("--output") + 1] = focusOutput.toString();
            focused[Arrays.asList(focused).indexOf("--seed") + 1] = "2";
            focused[focused.length - 2] = "--focus-panels"; focused[focused.length - 1] = "all";
            MPSLGPJointTaskAnalysisMain.execute(focused, new PrintStream(log));
            List<Map<String, String>> focusViews = rows(focusOutput, "front-viewports.csv");
            Map<String, String> own = focusViews.stream().filter(row -> row.get("Group").equals("R4")
                    && row.get("Scenario").equals("1")).findFirst().orElseThrow(AssertionError::new);
            Map<String, String> peer = focusViews.stream().filter(row -> row.get("Group").equals("H3")
                    && row.get("Scenario").equals("1")).findFirst().orElseThrow(AssertionError::new);
            check(Double.parseDouble(own.get("XMax")) - Double.parseDouble(own.get("XMin"))
                    < Double.parseDouble(peer.get("XMax")) - Double.parseDouble(peer.get("XMin")),
                    "focused task uses its own bounds instead of shared-scenario bounds");
            check(own.get("VisiblePreferences").equals("200"), "focused panel retains every preference outcome");
            check(fileHash(focusOutput.resolve("eaf-boundaries.csv")).equals(fileHash(output.resolve("eaf-boundaries.csv"))),
                    "focus and representative seed do not change all-run EAF analysis");
            check(readProperties(focusOutput.resolve("protocol.properties")).getProperty("figure.focus.panels").split(",").length == 6,
                    "focus all resolves to all six panels");
            expectFailure(() -> MPSLGPJointTaskAnalysisMain.execute(analysis, new PrintStream(log)), "new --output");
            String[] protectedArgs = analysis.clone();
            protectedArgs[protectedArgs.length - 1] = training.toString();
            expectFailure(() -> MPSLGPJointTaskAnalysisMain.execute(protectedArgs, new PrintStream(log)), "must not overwrite");
            MPSLGPPaperTestMain.Job job = jobs.get(0);
            Path source = job.directory.resolve("attempt-0001").resolve(job.csvName);
            List<String> changed = Files.readAllLines(source);
            String[] firstRow = changed.get(1).split(",");
            firstRow[8] = " " + firstRow[8];
            changed.set(1, String.join(",", firstRow));
            Files.write(source, changed);
            String[] tampered = analysis.clone();
            Path noOutput = temp.resolve("tampered-output");
            tampered[tampered.length - 1] = noOutput.toString();
            expectFailure(() -> MPSLGPJointTaskAnalysisMain.execute(tampered, new PrintStream(log)), "sha256.");
            check(!Files.exists(noOutput), "corrupt inputs rejected before writing results");
            System.out.println("PASS: MPSLGPJointTaskAnalysisMainTest (" + assertions + " assertions)");
        } finally {
            try (Stream<Path> paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) { Files.delete(path); }
            }
        }
    }

    private static void testArguments() throws Exception {
        MPSLGPJointTaskAnalysisMain.Config config = MPSLGPJointTaskAnalysisMain.Config.parse(new String[0]);
        check(config.groups.equals(Arrays.asList("R4", "H3")) && config.runs == 30 && config.seed == null,
                "default complete R4/H3 automatic representative selection");
        config = MPSLGPJointTaskAnalysisMain.Config.parse(new String[]{"--focus-panels", "all", "--hide-outliers", "H3:5"});
        check(config.focusAll && config.hideOutlierPanels.contains("H3:5"), "explicit all-focus and display-only outlier options");
        for (String[] invalid : Arrays.asList(new String[]{"--groups", "R4,R4"}, new String[]{"--groups", "H4"},
                new String[]{"--groups", ""}, new String[]{"--groups"}, new String[]{"--runs", "1"},
                new String[]{"--seed", "-1"}, new String[]{"--seed", "30"},
                new String[]{"--runs", "4", "--runs", "5"}, new String[]{"--bad", "value"},
                new String[]{"--focus-panels", "R4:5,R4:5"}, new String[]{"--focus-panels", "R4:11"},
                new String[]{"--focus-panels", "H1:1"},
                new String[]{"--hide-outliers", "H3:5,H3:5"}, new String[]{"--hide-outliers", ""},
                new String[]{"--hide-outliers", "R4:11"}, new String[]{"--hide-outliers", "H1:1"},
                new String[]{"--start-run", "2147483647"})) {
            boolean rejected = false;
            try { MPSLGPJointTaskAnalysisMain.Config.parse(invalid); }
            catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "reject " + Arrays.toString(invalid));
        }
        String fontDirectory = System.getProperty("mpslgp.fontDirectory");
        try {
            System.setProperty("mpslgp.fontDirectory",
                    java.nio.file.Paths.get("missing-latin-modern-test-fonts").toAbsolutePath().toString());
            expectFailure(MPSLGPPaperFonts::load, "Missing or incorrect manuscript font file");
        } finally {
            if (fontDirectory == null) { System.clearProperty("mpslgp.fontDirectory"); }
            else { System.setProperty("mpslgp.fontDirectory", fontDirectory); }
        }
        check(MPSLGPJointTaskAnalysisMain.execute(new String[]{"--help"},
                new PrintStream(new ByteArrayOutputStream())) == null, "help needs no result data");
    }

    private static void testPlotOutliers() throws IOException {
        double[][] points = new double[200][2];
        boolean[] nd = new boolean[200];
        nd[0] = true;
        for (int i = 0; i < 196; i++) { points[i] = new double[]{100 + i / 100.0, 200 + i / 200.0}; }
        for (int i = 196; i < 200; i++) { points[i] = new double[]{200 + i, 400 + i}; }
        MPSLGPJointTaskPlots.Panel panel = new MPSLGPJointTaskPlots.Panel("H3", 5, 0, points, nd,
                new double[][]{{100,200}}, new double[]{50,100}, new double[]{500,700}, 200, 1);
        boolean[] mask = MPSLGPJointTaskPlots.outlierMask(panel);
        int excluded = 0;
        for (int i = 0; i < mask.length; i++) { if (mask[i]) { excluded++; check(i >= 196, "only distant dominated vectors omitted"); } }
        check(excluded == 4 && points[196][0] == 396, "deterministic four-point filtering without data mutation");
        points[199] = new double[]{50,700}; nd[199] = true;
        mask = MPSLGPJointTaskPlots.outlierMask(panel);
        check(!mask[199], "a nondominated plotting outlier is always retained");
        for (int i = 0; i < 200; i++) { points[i] = new double[]{100,200}; }
        mask = MPSLGPJointTaskPlots.outlierMask(panel);
        check(!mask[0] && !mask[199], "zero-IQR collapsed responses are not discarded");
    }

    private static void testCsvAndSelection() throws Exception {
        check(MPSLGPJointTaskAnalysisMain.csvFields("\"R4\",\"D:\\data,a\",\"a\"\"b\",")
                .equals(Arrays.asList("R4", "D:\\data,a", "a\"b", "")), "quoted CSV paths and escaped quotes");
        expectFailure(() -> MPSLGPJointTaskAnalysisMain.csvFields("\"unclosed"), "Unterminated");
        expectFailure(() -> MPSLGPJointTaskAnalysisMain.csvFields("\"closed\"extra"), "Malformed");
        check(Arrays.equals(MPSLGPJointTaskAnalysisMain.midranks(new double[]{1,1,3,4}), new double[]{1.5,1.5,3,4}),
                "tie-aware ranks");
        Map<Integer, Double> scores = new TreeMap<>();
        scores.put(0, 2.0); scores.put(1, 1.0); scores.put(2, 2.0); scores.put(3, 3.0);
        check(MPSLGPJointTaskAnalysisMain.medianSeed(scores) == 0, "lower median, deterministic seed tie-break, not best seed");
        check(MPSLGPJointTaskPlots.preferenceColor(0).getRGB() == new java.awt.Color(0x2563EB).getRGB()
                && MPSLGPJointTaskPlots.preferenceColor(.5).getRGB() == new java.awt.Color(0x6E3FA8).getRGB()
                && MPSLGPJointTaskPlots.preferenceColor(1).getRGB() == new java.awt.Color(0xF97316).getRGB(),
                "continuous preference scale matches paper colors");
        expectFailure(() -> MPSLGPJointTaskPlots.preferenceColor(-.1), "in [0,1]");
        List<double[]> steps = MPSLGPJointTaskPlots.staircase(new double[][]{{1,5},{3,2}}, 1, 4);
        check(steps.size() == 4 && Arrays.equals(steps.get(1), new double[]{3,5})
                && Arrays.equals(steps.get(2), new double[]{3,2}), "attainment envelope uses staircase corners, not diagonal interpolation");
        expectFailure(() -> new MPSLGPJointTaskAnalysisMain.TargetReference(
                new double[]{0,0}, new double[]{0,1}, new double[][]{{0,0}}), "Invalid reference");
        expectFailure(() -> new MPSLGPJointTaskAnalysisMain.TargetReference(
                new double[]{0,0}, new double[]{1,1}, new double[][]{{.1,.1},{.5,.5}}), "nondominated");
    }

    private static void collapse(MPSLGPPaperTestMain.Job job) throws IOException {
        Path attempt = job.directory.resolve("attempt-0001"), csv = attempt.resolve(job.csvName);
        Path details = attempt.resolve("preference-results.csv");
        List<String> rows = Files.readAllLines(csv), preferences = Files.readAllLines(details);
        for (int i = 1; i < rows.size(); i++) {
            String[] fields = rows.get(i).split(","), weights = preferences.get(i).split(",");
            fields[8] = weights[3] = "2.25"; fields[10] = weights[4] = "2.75";
            rows.set(i, String.join(",", fields)); preferences.set(i, String.join(",", weights));
        }
        Files.write(csv, rows); Files.write(details, preferences);
        Path marker = job.directory.resolve("completed.properties");
        Properties old = readProperties(marker);
        Map<String, String> updated = new TreeMap<>();
        for (String key : old.stringPropertyNames()) { updated.put(key, old.getProperty(key)); }
        updated.put("sha256." + job.csvName, fileHash(csv));
        updated.put("sha256.preference-results.csv", fileHash(details));
        atomicWrite(marker, serialize(updated));
    }

    private static List<Map<String, String>> rows(Path output, String name) throws IOException {
        return MPSLGPJointTaskAnalysisMain.readCsv(output.resolve(name));
    }
    private static int colorCount(java.awt.image.BufferedImage image, int rgb) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) { if ((image.getRGB(x, y) & 0xFFFFFF) == rgb) { count++; } }
        }
        return count;
    }
    private static boolean inRange(double value, Map<String, String> viewport, String min, String max) {
        return value >= Double.parseDouble(viewport.get(min)) && value <= Double.parseDouble(viewport.get(max));
    }
    private interface Action { void run() throws Exception; }
    private static void expectFailure(Action action, String text) throws Exception {
        boolean rejected = false;
        try { action.run(); }
        catch (IOException | IllegalArgumentException e) { rejected = e.getMessage().contains(text); }
        check(rejected, "explicit rejection containing " + text);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new AssertionError(message); }
    }
}
