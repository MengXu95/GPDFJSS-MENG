package mengxu.ruleanalysis.MPSLGP;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYShapeAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.Layer;
import org.jfree.ui.RectangleInsets;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.atomicWrite;
import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.csv;

final class MPSLGPJointTaskPlots {
    private static final int PREFERENCES = 200;
    private static final int MARGIN = 20;
    private static final int GAP = 16;
    private static final int GRID_TOP = 6;
    private static final Color INK = new Color(0x111827);
    private static final Color GRID = new Color(0xF3F4F6);
    private static final Color BLUE = new Color(0x2563EB);
    private static final Color ORANGE = new Color(0xF97316);
    private static final Color PURPLE = new Color(0x6E3FA8);

    static final class Panel {
        final String group;
        final int scenario;
        final int seed;
        final double[][] outcomes;
        final boolean[] nondominated;
        final double[][] empiricalFront;
        final double[] min;
        final double[] max;
        final int uniqueOutcomes;
        final int nondominatedOutcomes;

        Panel(String group, int scenario, int seed, double[][] outcomes,
                boolean[] nondominated, double[][] empiricalFront, double[] min,
                double[] max, int uniqueOutcomes, int nondominatedOutcomes) {
            this.group = group;
            this.scenario = scenario;
            this.seed = seed;
            this.outcomes = outcomes;
            this.nondominated = nondominated;
            this.empiricalFront = empiricalFront;
            this.min = min;
            this.max = max;
            this.uniqueOutcomes = uniqueOutcomes;
            this.nondominatedOutcomes = nondominatedOutcomes;
        }
    }

    static final class GroupSummary {
        final String group;
        final int runs;
        final int taskCount;
        final double jointValidFraction;
        final double allTasksResponsiveFraction;

        GroupSummary(String group, int runs, int taskCount,
                double jointValidFraction, double allTasksResponsiveFraction) {
            this.group = group;
            this.runs = runs;
            this.taskCount = taskCount;
            this.jointValidFraction = jointValidFraction;
            this.allTasksResponsiveFraction = allTasksResponsiveFraction;
        }
    }

    private MPSLGPJointTaskPlots() {
    }

    static Map<String, String> render(Path output, List<String> groups, List<Panel> panels,
            List<GroupSummary> summaries, int selectedSeed,
            List<MPSLGPEmpiricalAttainment.Scenario> attainment, Set<String> focusPanels,
            Set<String> hideOutlierPanels) throws IOException {
        if (output == null || !Files.isDirectory(output)) {
            throw new IOException("The figure output directory must already exist.");
        }
        Layout layout = new Layout(groups, panels, summaries, selectedSeed);
        MPSLGPPaperFonts fonts = MPSLGPPaperFonts.load();
        Map<Integer, TaskRanges> ranges = taskRanges(layout);
        Map<String, boolean[]> omitted = new HashMap<>();
        Set<String> independent = new LinkedHashSet<>(focusPanels);
        independent.addAll(hideOutlierPanels);
        for (Panel panel : panels) {
            String id = panel.group + ":" + panel.scenario;
            omitted.put(id, hideOutlierPanels.contains(id) ? outlierMask(panel) : new boolean[PREFERENCES]);
        }
        Figure fronts = new GridFigure(layout, ranges, fonts, false, independent, omitted);
        Figure responses = new GridFigure(layout, ranges, fonts, true, independent, omitted);
        export(output, "joint-fronts", fronts);
        export(output, "preference-response", responses);
        if (!attainment.isEmpty()) { export(output, "empirical-attainment", new AttainmentFigure(attainment, fonts)); }
        writeSummaryTable(output, layout.summaries);
        StringBuilder viewports = new StringBuilder("Group,Scenario,Seed,ValidPreferences,VisiblePreferences,XMin,XMax,YMin,YMax,ExcludedPreferences\n");
        StringBuilder exclusions = new StringBuilder("Group,Scenario,Seed,PreferenceIndex,Weight0,Weight1,Objective0,Objective1,Rule\n");
        for (List<Panel> row : layout.rows) {
            for (Panel panel : row) {
                boolean[] hidden = omitted.get(panel.group + ":" + panel.scenario);
                TaskRanges range = scatterRanges(panel, ranges.get(panel.scenario), independent, hidden);
                int valid = 0, inside = 0, excluded = 0;
                for (int i = 0; i < PREFERENCES; i++) {
                    double[] point = panel.outcomes[i];
                    if (valid(point)) {
                        valid++;
                        if (hidden[i]) {
                            excluded++;
                            exclusions.append(csv(panel.group, panel.scenario, panel.seed, i,
                                    i / 199.0, (199 - i) / 199.0, point[0], point[1],
                                    "Display-only coordinatewise 1.5IQR; nondominated points retained")).append('\n');
                        } else if (range.rawAxes[0].contains(point[0]) && range.rawAxes[1].contains(point[1])) { inside++; }
                    }
                }
                viewports.append(csv(panel.group, panel.scenario, panel.seed, valid, inside,
                        range.rawAxes[0].getLowerBound(), range.rawAxes[0].getUpperBound(),
                        range.rawAxes[1].getLowerBound(), range.rawAxes[1].getUpperBound(), excluded)).append('\n');
            }
        }
        atomicWrite(output.resolve("front-viewports.csv"), viewports.toString());
        atomicWrite(output.resolve("plot-excluded-preferences.csv"), exclusions.toString());
        return fonts.provenance();
    }

    private static void writeSummaryTable(Path output, List<GroupSummary> summaries) throws IOException {
        StringBuilder rows = new StringBuilder("\\newcommand{\\MPSLGPJointSummaryRows}{%\n");
        for (GroupSummary summary : summaries) {
            long queries = (long)PREFERENCES * summary.runs;
            rows.append(String.format(Locale.ROOT, "%s & %d/%d (%.1f\\%%) & %d/%d (%.1f\\%%) \\\\\n",
                    summary.group, Math.round(summary.jointValidFraction * queries), queries,
                    100 * summary.jointValidFraction, Math.round(summary.allTasksResponsiveFraction * summary.runs),
                    summary.runs, 100 * summary.allTasksResponsiveFraction));
        }
        rows.append("}\n");
        atomicWrite(output.resolve("group-summary-rows.tex"), rows.toString());
    }

    private static final class Layout {
        final List<String> groups;
        final List<List<Panel>> rows = new ArrayList<List<Panel>>();
        final List<GroupSummary> summaries = new ArrayList<GroupSummary>();
        int columns;

        Layout(List<String> groups, List<Panel> panels,
                List<GroupSummary> summaries, int selectedSeed) throws IOException {
            if (groups == null || groups.isEmpty() || panels == null || summaries == null) {
                throw new IOException("Groups, task panels and summaries are required.");
            }
            this.groups = new ArrayList<String>(groups);
            Map<String, Map<Integer, Panel>> byGroup =
                    new LinkedHashMap<String, Map<Integer, Panel>>();
            for (String group : groups) {
                if (group == null || group.isEmpty() || byGroup.containsKey(group)) {
                    throw new IOException("Group names must be nonempty and distinct.");
                }
                byGroup.put(group, new TreeMap<Integer, Panel>());
            }
            for (Panel panel : panels) {
                validate(panel, selectedSeed);
                Map<Integer, Panel> tasks = byGroup.get(panel.group);
                if (tasks == null || tasks.put(panel.scenario, panel) != null) {
                    throw new IOException("Unknown group or duplicate scenario in task panels.");
                }
            }
            Set<Integer> common = null;
            for (Map<Integer, Panel> tasks : byGroup.values()) {
                if (tasks.isEmpty()) {
                    throw new IOException("Each group must have at least one task panel.");
                }
                if (common == null) {
                    common = new TreeSet<Integer>(tasks.keySet());
                } else {
                    common.retainAll(tasks.keySet());
                }
            }
            // Only common tasks occupy fixed columns; each row's other tasks follow in order.
            for (Map<Integer, Panel> tasks : byGroup.values()) {
                List<Panel> row = new ArrayList<Panel>();
                for (int scenario : common) {
                    row.add(tasks.get(scenario));
                }
                for (Map.Entry<Integer, Panel> task : tasks.entrySet()) {
                    if (!common.contains(task.getKey())) {
                        row.add(task.getValue());
                    }
                }
                rows.add(row);
                columns = Math.max(columns, row.size());
            }
            Map<String, GroupSummary> bySummary = new HashMap<String, GroupSummary>();
            for (GroupSummary summary : summaries) {
                if (summary == null || !byGroup.containsKey(summary.group)
                        || bySummary.put(summary.group, summary) != null
                        || summary.runs < 0
                        || summary.taskCount != byGroup.get(summary.group).size()
                        || !fraction(summary.jointValidFraction)
                        || !fraction(summary.allTasksResponsiveFraction)
                        || (summary.runs == 0 && (summary.jointValidFraction != 0
                            || summary.allTasksResponsiveFraction != 0))) {
                    throw new IOException("Invalid group summary or fraction outside [0, 1].");
                }
            }
            for (String group : groups) {
                if (!bySummary.containsKey(group)) {
                    throw new IOException("Missing group summary: " + group);
                }
                this.summaries.add(bySummary.get(group));
            }
        }
    }

    private static boolean fraction(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    private static void validate(Panel panel, int selectedSeed) throws IOException {
        if (panel == null || panel.seed != selectedSeed || panel.scenario < 1
                || panel.outcomes == null || panel.outcomes.length != PREFERENCES
                || panel.nondominated == null || panel.nondominated.length != PREFERENCES
                || panel.empiricalFront == null || panel.min == null || panel.min.length != 2
                || panel.max == null || panel.max.length != 2
                || panel.uniqueOutcomes < 0 || panel.uniqueOutcomes > PREFERENCES
                || panel.nondominatedOutcomes < 0
                || panel.nondominatedOutcomes > PREFERENCES) {
            throw new IOException("Invalid task panel or inconsistent selected training seed.");
        }
        for (int objective = 0; objective < 2; objective++) {
            double span = panel.max[objective] - panel.min[objective];
            if (!Double.isFinite(panel.min[objective])
                    || !Double.isFinite(panel.max[objective])
                    || !Double.isFinite(span) || span <= 0) {
                throw new IOException("Paper normalization bounds must have finite positive spans.");
            }
        }
        for (double[] outcome : panel.outcomes) {
            if (outcome != null && outcome.length != 2) {
                throw new IOException("Each preference outcome must have two objectives.");
            }
        }
        for (double[] point : panel.empiricalFront) {
            if (!valid(point)) {
                throw new IOException("Empirical references must contain valid raw objective pairs.");
            }
        }
    }

    private static boolean valid(double[] pair) {
        return pair != null && pair.length == 2 && pair[0] != -1 && pair[1] != -1
                && Double.isFinite(pair[0]) && Double.isFinite(pair[1]);
    }

    private static double normalized(Panel panel, double[] outcome, int objective) {
        return (outcome[objective] - panel.min[objective])
                / (panel.max[objective] - panel.min[objective]);
    }

    private static final class Extent {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        void include(double value) throws IOException {
            if (!Double.isFinite(value)) {
                throw new IOException("An objective coordinate or normalization is not finite.");
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        Range padded(double fallbackMin, double fallbackMax) throws IOException {
            return padded(fallbackMin, fallbackMax, .05);
        }

        Range padded(double fallbackMin, double fallbackMax, double constantFraction) throws IOException {
            double lower = min == Double.POSITIVE_INFINITY ? fallbackMin : min;
            double upper = max == Double.NEGATIVE_INFINITY ? fallbackMax : max;
            double padding = lower == upper
                    ? Math.max(Math.abs(lower) * constantFraction, 0.000001)
                    : (upper - lower) * 0.08;
            if (!Double.isFinite(lower - padding) || !Double.isFinite(upper + padding)
                    || lower - padding >= upper + padding) {
                throw new IOException("Objective axis bounds cannot be represented.");
            }
            return new Range(lower - padding, upper + padding);
        }
    }

    private static final class TaskRanges {
        final Extent[] raw = {new Extent(), new Extent()};
        final Extent[] response = {new Extent(), new Extent()};
        final Extent[] paper = {new Extent(), new Extent()};
        final Range[] rawAxes = new Range[2];
        Range responseAxis;
    }

    private static Map<Integer, TaskRanges> taskRanges(Layout layout) throws IOException {
        Map<Integer, TaskRanges> ranges = new HashMap<Integer, TaskRanges>();
        for (List<Panel> row : layout.rows) {
            for (Panel panel : row) {
                TaskRanges task = ranges.get(panel.scenario);
                if (task == null) {
                    task = new TaskRanges();
                    ranges.put(panel.scenario, task);
                }
                for (int i = 0; i < panel.outcomes.length; i++) {
                    double[] outcome = panel.outcomes[i];
                    if (valid(outcome)) {
                        for (int j = 0; j < 2; j++) {
                            task.raw[j].include(outcome[j]);
                            task.response[j].include(normalized(panel, outcome, j));
                        }
                    }
                }
                for (int j = 0; j < 2; j++) {
                    task.paper[j].include(panel.min[j]);
                    task.paper[j].include(panel.max[j]);
                }
            }
        }
        for (TaskRanges task : ranges.values()) {
            for (int j = 0; j < 2; j++) {
                task.rawAxes[j] = task.raw[j].padded(task.paper[j].min, task.paper[j].max);
            }
            Extent bothResponses = new Extent();
            for (Extent response : task.response) {
                if (response.min != Double.POSITIVE_INFINITY) {
                    bothResponses.include(response.min);
                    bothResponses.include(response.max);
                }
            }
            task.responseAxis = bothResponses.padded(0, 1);
        }
        return ranges;
    }

    private static TaskRanges scatterRanges(Panel panel, TaskRanges shared, Set<String> focusPanels,
            boolean[] hidden) throws IOException {
        if (!focusPanels.contains(panel.group + ":" + panel.scenario)) { return shared; }
        TaskRanges focused = new TaskRanges();
        for (int i = 0; i < PREFERENCES; i++) {
            double[] outcome = panel.outcomes[i];
            if (valid(outcome) && !hidden[i]) {
                focused.raw[0].include(outcome[0]); focused.raw[1].include(outcome[1]);
            }
        }
        if (focused.raw[0].min == Double.POSITIVE_INFINITY) {
            throw new IOException("Cannot focus a panel with no valid outcomes: " + panel.group + ":" + panel.scenario);
        }
        for (int i = 0; i < 2; i++) { focused.rawAxes[i] = focused.raw[i].padded(panel.min[i], panel.max[i], .005); }
        return focused;
    }

    static boolean[] outlierMask(Panel panel) throws IOException {
        List<double[]> points = new ArrayList<>();
        for (double[] point : panel.outcomes) { if (valid(point)) { points.add(point); } }
        if (points.isEmpty()) { throw new IOException("No valid points for plotting-outlier selection."); }
        double[] lower = new double[2], upper = new double[2];
        for (int j = 0; j < 2; j++) {
            final int dimension = j;
            double[] sorted = points.stream().mapToDouble(point -> point[dimension]).sorted().toArray();
            double q1 = quantile(sorted, .25), q3 = quantile(sorted, .75), iqr = q3 - q1;
            lower[j] = q1 - 1.5 * iqr; upper[j] = q3 + 1.5 * iqr;
        }
        boolean[] hidden = new boolean[PREFERENCES];
        for (int i = 0; i < PREFERENCES; i++) {
            double[] point = panel.outcomes[i];
            if (valid(point) && !panel.nondominated[i]) {
                hidden[i] = point[0] < lower[0] || point[0] > upper[0] || point[1] < lower[1] || point[1] > upper[1];
            }
        }
        return hidden;
    }

    private static double quantile(double[] sorted, double fraction) {
        double position = fraction * (sorted.length - 1);
        int first = (int)Math.floor(position), second = (int)Math.ceil(position);
        return sorted[first] + (sorted[second] - sorted[first]) * (position - first);
    }

    private static String objective(Panel panel, int index) {
        return panel.scenario <= 5 ? (index == 0 ? "Fmax" : "WTmax")
                : (index == 0 ? "WFmax" : "Tmax");
    }

    private static NumberAxis axis(String label, Range range, MPSLGPPaperFonts fonts) {
        NumberAxis axis = new NumberAxis(label);
        axis.setLabelFont(fonts.label);
        axis.setTickLabelFont(fonts.tick);
        axis.setLabelPaint(INK);
        axis.setTickLabelPaint(INK);
        axis.setAutoRangeIncludesZero(false);
        axis.setRange(range);
        return axis;
    }

    private static void style(Plot plot) {
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setInsets(new RectangleInsets(8, 8, 8, 8));
        if (plot instanceof XYPlot) {
            XYPlot xy = (XYPlot) plot;
            xy.setDomainGridlinePaint(GRID);
            xy.setRangeGridlinePaint(GRID);
            xy.setAxisOffset(new RectangleInsets(4, 4, 4, 4));
        }
    }

    private static JFreeChart chart(Plot plot, MPSLGPPaperFonts fonts) {
        style(plot);
        JFreeChart chart = new JFreeChart(null, fonts.panel, plot, false);
        chart.setBackgroundPaint(Color.WHITE);
        chart.setBorderVisible(false);
        chart.setPadding(new RectangleInsets(8, 5, 6, 28));
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        return chart;
    }

    private static JFreeChart taskChart(Panel panel, TaskRanges ranges, boolean response,
            MPSLGPPaperFonts fonts, boolean[] hidden) {
        JFreeChart chart;
        if (response) {
            XYSeriesCollection data = new XYSeriesCollection();
            for (int objective = 0; objective < 2; objective++) {
                XYSeries series = new XYSeries("Objective " + (objective + 1), false, false);
                for (int i = 0; i < PREFERENCES; i++) {
                    double[] outcome = panel.outcomes[i];
                    // Null observations retain the preference index and break the line.
                    Number value = valid(outcome)
                            ? Double.valueOf(normalized(panel, outcome, objective)) : null;
                    series.add(i / 199.0, value);
                }
                data.addSeries(series);
            }
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
            renderer.setSeriesPaint(0, BLUE);
            renderer.setSeriesPaint(1, ORANGE);
            renderer.setSeriesStroke(0, new BasicStroke(2.6f));
            renderer.setSeriesStroke(1, new BasicStroke(2.6f));
            renderer.setSeriesShapesVisible(0, false);
            renderer.setSeriesShapesVisible(1, false);
            XYPlot plot = new XYPlot(data, axis("w1", new Range(0, 1), fonts),
                    axis("Normalized objective", ranges.responseAxis, fonts), renderer);
            for (int i = 0; i < PREFERENCES; i++) {
                if (!valid(panel.outcomes[i])) {
                    IntervalMarker strip = new IntervalMarker(
                            Math.max(0, (i - 0.5) / 199.0),
                            Math.min(1, (i + 0.5) / 199.0), new Color(0xE5E7EB));
                    strip.setAlpha(1.0f);
                    strip.setOutlinePaint(null);
                    plot.addDomainMarker(strip, Layer.BACKGROUND);
                }
            }
            chart = chart(plot, fonts);
        } else {
            XYSeriesCollection preferences = new XYSeriesCollection();
            List<List<Integer>> indices = new ArrayList<>();
            for (int seriesIndex = 0; seriesIndex < 2; seriesIndex++) {
                boolean nd = seriesIndex == 1;
                XYSeries series = new XYSeries(nd ? "Nondominated" : "Outcomes", false, true);
                List<Integer> selected = new ArrayList<>();
                for (int i = 0; i < PREFERENCES; i++) {
                    double[] outcome = panel.outcomes[i];
                    if (valid(outcome) && !hidden[i] && panel.nondominated[i] == nd) {
                        series.add(outcome[0], outcome[1]);
                        selected.add(i);
                    }
                }
                preferences.addSeries(series);
                indices.add(selected);
            }
            PreferenceRenderer renderer = new PreferenceRenderer(indices);
            XYPlot plot = new XYPlot(preferences, axis(objective(panel, 0), ranges.rawAxes[0], fonts),
                    axis(objective(panel, 1), ranges.rawAxes[1], fonts), renderer);
            plot.setSeriesRenderingOrder(org.jfree.chart.plot.SeriesRenderingOrder.FORWARD);
            plot.setDomainGridlinesVisible(false);
            plot.setRangeGridlinesVisible(false);
            chart = chart(plot, fonts);
        }
        return chart;
    }

    private static Color translucent(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    static Color preferenceColor(double weight) {
        if (!Double.isFinite(weight) || weight < 0 || weight > 1) {
            throw new IllegalArgumentException("Preference color weight must be in [0,1].");
        }
        Color first = weight <= .5 ? BLUE : PURPLE, second = weight <= .5 ? PURPLE : ORANGE;
        double fraction = weight <= .5 ? 2 * weight : 2 * weight - 1;
        return new Color((int)Math.round(first.getRed() + fraction * (second.getRed() - first.getRed())),
                (int)Math.round(first.getGreen() + fraction * (second.getGreen() - first.getGreen())),
                (int)Math.round(first.getBlue() + fraction * (second.getBlue() - first.getBlue())));
    }

    private static final class PreferenceRenderer extends XYLineAndShapeRenderer {
        private static final long serialVersionUID = 1L;
        private final List<List<Integer>> indices;

        PreferenceRenderer(List<List<Integer>> indices) {
            super(false, true);
            this.indices = indices;
            setDrawOutlines(true); setUseFillPaint(true); setUseOutlinePaint(true);
            for (int series = 0; series < 2; series++) {
                double diameter = series == 0 ? 5.2 : 6.4;
                setSeriesShape(series, new Ellipse2D.Double(-diameter / 2, -diameter / 2, diameter, diameter));
                setSeriesShapesFilled(series, true);
                setSeriesOutlineStroke(series, new BasicStroke(1.2f));
            }
        }

        @Override
        public java.awt.Paint getItemPaint(int series, int item) { return getItemFillPaint(series, item); }
        @Override
        public java.awt.Paint getItemFillPaint(int series, int item) {
            return translucent(preferenceColor(indices.get(series).get(item) / 199.0), series == 0 ? 135 : 210);
        }
        @Override
        public java.awt.Paint getItemOutlinePaint(int series, int item) {
            return series == 1 ? translucent(INK, 220) : new Color(0, 0, 0, 0);
        }
    }

    private abstract static class Figure {
        final int width;
        final int height;
        final MPSLGPPaperFonts fonts;

        Figure(int width, int height, MPSLGPPaperFonts fonts) {
            this.width = width;
            this.height = height;
            this.fonts = fonts;
        }

        abstract void draw(Graphics2D graphics);
    }

    private static final class GridFigure extends Figure {
        final Layout layout;
        final JFreeChart[][] charts;
        final boolean response;
        final int panelHeight;
        boolean hasInvalid;

        GridFigure(Layout layout, Map<Integer, TaskRanges> ranges, MPSLGPPaperFonts fonts, boolean response,
                Set<String> focusPanels, Map<String, boolean[]> omitted) throws IOException {
            super(Math.max(1500, layout.columns * 480 + 2 * MARGIN),
                    GRID_TOP + layout.rows.size() * ((response ? 380 : 400) + GAP) - GAP + (response ? 48 : 65), fonts);
            this.layout = layout;
            this.response = response;
            panelHeight = response ? 380 : 400;
            charts = new JFreeChart[layout.rows.size()][layout.columns];
            for (int row = 0; row < layout.rows.size(); row++) {
                for (int col = 0; col < layout.rows.get(row).size(); col++) {
                    Panel panel = layout.rows.get(row).get(col);
                    boolean[] hidden = response ? new boolean[PREFERENCES] : omitted.get(panel.group + ":" + panel.scenario);
                    TaskRanges selected = response ? ranges.get(panel.scenario)
                            : scatterRanges(panel, ranges.get(panel.scenario), focusPanels, hidden);
                    charts[row][col] = taskChart(panel, selected, response, fonts, hidden);
                    for (double[] outcome : panel.outcomes) { hasInvalid |= !valid(outcome); }
                }
            }
        }

        @Override
        void draw(Graphics2D graphics) {
            background(graphics, width, height);
            double cellWidth = (width - 2.0 * MARGIN - (layout.columns - 1) * GAP) / layout.columns;
            for (int row = 0; row < charts.length; row++) {
                for (int col = 0; col < layout.columns; col++) {
                    Rectangle2D card = new Rectangle2D.Double(
                            MARGIN + col * (cellWidth + GAP),
                            GRID_TOP + row * (panelHeight + GAP), cellWidth, panelHeight);
                    if (charts[row][col] != null) {
                        Panel panel = layout.rows.get(row).get(col);
                        text(graphics, "(" + (char)('a' + row * layout.columns + col) + ") "
                                + panel.group + " / S" + panel.scenario,
                                card.getX() + 12, card.getY() + 25, fonts.panel, INK);
                        charts[row][col].draw(graphics, new Rectangle2D.Double(card.getX(), card.getY() + 34,
                                card.getWidth(), card.getHeight() - 34));
                    }
                }
            }
            if (response) {
                int baseline = height - 18;
                lineLegend(graphics, hasInvalid ? 230 : 450, baseline, BLUE, "Objective 1", fonts);
                lineLegend(graphics, hasInvalid ? 590 : 810, baseline, ORANGE, "Objective 2", fonts);
                if (hasInvalid) {
                    graphics.setColor(new Color(0xE5E7EB));
                    graphics.fill(new Rectangle2D.Double(970, baseline - 18, 25, 20));
                    text(graphics, "Invalid", 1008, baseline, fonts.legend, INK);
                }
            } else {
                drawFrontLegend(graphics, width, height, fonts);
            }
        }
    }

    private static void drawFrontLegend(Graphics2D graphics, int width, int height, MPSLGPPaperFonts fonts) {
        int barY = height - 46;
        double offset = (width - 1050) / 2.0, barX = offset + 48;
        text(graphics, "w1:", offset, barY + 14, fonts.legend, INK);
        for (int step = 0; step < 600; step++) {
            graphics.setColor(preferenceColor(step / 599.0));
            graphics.fill(new Rectangle2D.Double(barX + step, barY, 1.1, 15));
        }
        for (int tick = 0; tick <= 4; tick++) {
            String label = String.format(Locale.ROOT, "%.2f", tick / 4.0);
            graphics.setFont(fonts.legend);
            double x = barX + tick * 599 / 4.0 - graphics.getFontMetrics().stringWidth(label) / 2.0;
            text(graphics, label, x, height - 8, fonts.legend, INK);
        }
        graphics.setColor(INK);
        graphics.setStroke(new BasicStroke(1.2f));
        graphics.draw(new Ellipse2D.Double(offset + 740, barY + 1, 13, 13));
        text(graphics, "Nondominated", offset + 766, barY + 14, fonts.legend, INK);
    }

    private static final Color[] EAF_COLORS = {BLUE, ORANGE, PURPLE, new Color(0x64748B),
        new Color(0x1D4ED8), new Color(0xC2410C), new Color(0x93C5FD), new Color(0xFDBA74)};

    private static BasicStroke attainmentStroke(int level) {
        if (level == 1) { return new BasicStroke(3.1f); }
        return new BasicStroke(1.9f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                level == 0 ? new float[]{3,5} : new float[]{10,6}, 0);
    }

    private static final class AttainmentFigure extends Figure {
        private static final int PANEL = 420;
        final List<MPSLGPEmpiricalAttainment.Scenario> scenarios;
        final JFreeChart[] charts;
        final int columns;
        final List<String> groups = new ArrayList<>();

        AttainmentFigure(List<MPSLGPEmpiricalAttainment.Scenario> scenarios, MPSLGPPaperFonts fonts) throws IOException {
            super(1500, GRID_TOP + ((scenarios.size() + Math.min(2, scenarios.size()) - 1) / Math.min(2, scenarios.size()))
                    * (PANEL + GAP) - GAP + 95, fonts);
            this.scenarios = scenarios;
            columns = Math.min(2, scenarios.size());
            charts = new JFreeChart[scenarios.size()];
            for (MPSLGPEmpiricalAttainment.Scenario scenario : scenarios) {
                for (MPSLGPEmpiricalAttainment.Group group : scenario.groups) {
                    if (!groups.contains(group.group)) { groups.add(group.group); }
                }
            }
            for (int index = 0; index < scenarios.size(); index++) {
                MPSLGPEmpiricalAttainment.Scenario scenario = scenarios.get(index);
                Extent x = new Extent(), y = new Extent();
                for (MPSLGPEmpiricalAttainment.Group group : scenario.groups) {
                    for (MPSLGPEmpiricalAttainment.Level level : group.levels) {
                        for (double[] point : level.boundary) { x.include(point[0]); y.include(point[1]); }
                    }
                }
                if (x.min == Double.POSITIVE_INFINITY) { throw new IOException("No finite EAF boundary for scenario " + scenario.scenario); }
                Range xRange = x.padded(x.min, x.max), yRange = y.padded(y.min, y.max);
                XYSeriesCollection data = new XYSeriesCollection();
                XYStepRenderer renderer = new XYStepRenderer();
                renderer.setStepPoint(1.0);
                int seriesIndex = 0;
                for (MPSLGPEmpiricalAttainment.Group group : scenario.groups) {
                    Color color = EAF_COLORS[groups.indexOf(group.group) % EAF_COLORS.length];
                    for (int levelIndex = 0; levelIndex < group.levels.size(); levelIndex++) {
                        MPSLGPEmpiricalAttainment.Level level = group.levels.get(levelIndex);
                        XYSeries series = new XYSeries(group.group + " " + level.probability, false, true);
                        if (level.boundary.length > 0) {
                            series.add(level.boundary[0][0], yRange.getUpperBound());
                            for (double[] point : level.boundary) { series.add(point[0], point[1]); }
                            double[] last = level.boundary[level.boundary.length - 1];
                            series.add(xRange.getUpperBound(), last[1]);
                        }
                        data.addSeries(series);
                        renderer.setSeriesPaint(seriesIndex, levelIndex == 1 ? color : translucent(color, 170));
                        renderer.setSeriesStroke(seriesIndex, attainmentStroke(levelIndex));
                        renderer.setSeriesShapesVisible(seriesIndex, false);
                        seriesIndex++;
                    }
                }
                XYPlot plot = new XYPlot(data, axis(scenario.scenario <= 5 ? "Fmax" : "WFmax", xRange, fonts),
                        axis(scenario.scenario <= 5 ? "WTmax" : "Tmax", yRange, fonts), renderer);
                for (MPSLGPEmpiricalAttainment.Group group : scenario.groups) {
                    double[][] lower = group.levels.get(0).boundary, upper = group.levels.get(2).boundary;
                    if (lower.length == 0 || upper.length == 0) { continue; }
                    double start = Math.max(lower[0][0], upper[0][0]), end = xRange.getUpperBound();
                    List<double[]> lowSteps = staircase(lower, start, end), highSteps = staircase(upper, start, end);
                    Path2D area = new Path2D.Double();
                    area.moveTo(lowSteps.get(0)[0], lowSteps.get(0)[1]);
                    for (double[] point : lowSteps) { area.lineTo(point[0], point[1]); }
                    for (int i = highSteps.size() - 1; i >= 0; i--) {
                        double[] point = highSteps.get(i); area.lineTo(point[0], point[1]);
                    }
                    area.closePath();
                    Color color = EAF_COLORS[groups.indexOf(group.group) % EAF_COLORS.length];
                    renderer.addAnnotation(new XYShapeAnnotation(area, null, null, translucent(color, 20)), Layer.BACKGROUND);
                }
                charts[index] = chart(plot, fonts);
            }
        }

        @Override
        void draw(Graphics2D graphics) {
            background(graphics, width, height);
            double cellWidth = (width - 2.0 * MARGIN - (columns - 1) * GAP) / columns;
            for (int i = 0; i < charts.length; i++) {
                double x = MARGIN + (i % columns) * (cellWidth + GAP), y = GRID_TOP + (i / columns) * (PANEL + GAP);
                text(graphics, "(" + (char)('a' + i) + ") S" + scenarios.get(i).scenario,
                        x + 12, y + 25, fonts.panel, INK);
                charts[i].draw(graphics, new Rectangle2D.Double(x, y + 34, cellWidth, PANEL - 34));
            }
            int baseline = height - 57;
            double left = (width - groups.size() * 180) / 2.0;
            for (int i = 0; i < groups.size(); i++) {
                lineLegend(graphics, (int)left + 180 * i, baseline, EAF_COLORS[i % EAF_COLORS.length], groups.get(i), fonts);
            }
            for (int level = 0; level < 3; level++) {
                int x = 430 + level * 220;
                graphics.setColor(INK); graphics.setStroke(attainmentStroke(level));
                graphics.drawLine(x, height - 26, x + 45, height - 26);
                text(graphics, (25 + 25 * level) + "%", x + 60, height - 20, fonts.legend, INK);
            }
        }
    }

    static List<double[]> staircase(double[][] boundary, double start, double end) {
        if (boundary.length == 0 || start < boundary[0][0] || end < start) {
            throw new IllegalArgumentException("Staircase requires a finite boundary and an attained x interval.");
        }
        double current = Double.POSITIVE_INFINITY;
        for (double[] point : boundary) { if (point[0] <= start) { current = point[1]; } }
        List<double[]> steps = new ArrayList<>();
        steps.add(new double[]{start, current});
        for (double[] point : boundary) {
            if (point[0] > start && point[0] < end) {
                steps.add(new double[]{point[0], current});
                steps.add(point.clone()); current = point[1];
            }
        }
        steps.add(new double[]{end, current});
        return steps;
    }

    private static void background(Graphics2D graphics, int width, int height) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
    }

    private static void text(Graphics2D graphics, String text, double x, double y, Font font, Color color) {
        graphics.setFont(font);
        graphics.setColor(color);
        graphics.drawString(text, (float) x, (float) y);
    }

    private static void lineLegend(Graphics2D graphics, int x, int baseline, Color paint, String label,
            MPSLGPPaperFonts fonts) {
        graphics.setColor(paint);
        graphics.setStroke(new BasicStroke(2.6f));
        graphics.drawLine(x, baseline - 5, x + 29, baseline - 5);
        text(graphics, label, x + 42, baseline, fonts.legend, INK);
    }

    private static void export(Path output, String name, Figure figure) throws IOException {
        exportPng(output.resolve(name + ".png"), figure);
        exportPdf(output.resolve(name + ".pdf"), figure);
    }

    private static void exportPng(Path file, Figure figure) throws IOException {
        BufferedImage image = new BufferedImage(figure.width, figure.height, BufferedImage.TYPE_INT_RGB);
        try {
            Graphics2D graphics = image.createGraphics();
            try {
                figure.draw(graphics);
            } finally {
                graphics.dispose();
            }
            try (OutputStream stream = Files.newOutputStream(file)) {
                if (!ImageIO.write(image, "png", stream)) {
                    throw new IOException("No PNG image writer is available for " + file);
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Failed to export PNG: " + file, exception);
        } finally {
            image.flush();
        }
    }

    private static final class PdfPage implements AutoCloseable {
        final Document document;

        PdfPage(float width, float height) {
            document = new Document(new com.lowagie.text.Rectangle(width, height), 0, 0, 0, 0);
        }

        @Override
        public void close() {
            document.close();
        }
    }

    private static void exportPdf(Path file, Figure figure) throws IOException {
        // 1500 screen pixels become 900 PDF points; all chart primitives remain vector graphics.
        float scale = 0.6f;
        float width = figure.width * scale;
        float height = figure.height * scale;
        try (OutputStream stream = Files.newOutputStream(file);
                PdfPage page = new PdfPage(width, height)) {
            PdfWriter writer = PdfWriter.getInstance(page.document, stream);
            writer.setCloseStream(false);
            page.document.open();
            Graphics2D graphics = writer.getDirectContent().createGraphics(width, height, figure.fonts.mapper);
            try {
                graphics.scale(scale, scale);
                figure.draw(graphics);
            } finally {
                graphics.dispose();
            }
        } catch (DocumentException | RuntimeException exception) {
            throw new IOException("Failed to export vector PDF: " + file, exception);
        }
    }
}
