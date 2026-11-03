package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

/** Headless, dependency-free high-resolution PNGs; underlying plot data are exported separately. */
final class WholeLibraryPlots {
    private static final Color[] COLORS = {
        new Color(31, 90, 170), new Color(0, 142, 128),
        new Color(213, 106, 20), new Color(143, 67, 170)
    };

    private WholeLibraryPlots() { }

    static void write(Path output, List<WholeLibraryAnalysis.Curve> curves,
                      List<WholeLibraryAnalysis.Sample> samples, int poolSize) throws IOException {
        ecdf(output.resolve("run_balanced_ecdf.png"), curves, poolSize);
        scatter(output.resolve("quality_attribute_scatter.png"), samples, poolSize);
    }

    private static void ecdf(Path path, List<WholeLibraryAnalysis.Curve> curves, int poolSize) throws IOException {
        BufferedImage image = new BufferedImage(4200, 1800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        try {
            heading(g, "Run-balanced ECDFs",
                    "Finite metrics only: QDGPf / QDGP; whole retained vs top " + poolSize + " pool");
            int panel = 0;
            for (int metric : new int[]{WholeLibraryAnalysis.P, WholeLibraryAnalysis.C, WholeLibraryAnalysis.LOG_S}) {
                Rectangle plot = new Rectangle(220 + panel * 1380, 300, 1050, 900);
                List<WholeLibraryAnalysis.Curve> series = new ArrayList<>();
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                for (WholeLibraryAnalysis.Curve curve : curves) {
                    if (curve.metric != metric) { continue; }
                    series.add(curve);
                    for (double value : curve.ecdf.x) {
                        double coordinate = displayCoordinate(metric, value);
                        min = Math.min(min, coordinate);
                        max = Math.max(max, coordinate);
                    }
                }
                Range x = Range.of(min, max);
                axes(g, plot, x, new Range(0, 1, false), "(" + (char) ('a' + panel) + ") " + panelLabel(metric),
                        label(metric), "Run-balanced ECDF");
                Shape oldClip = g.getClip();
                g.clip(plot);
                for (WholeLibraryAnalysis.Curve curve : series) {
                    WholeLibraryAnalysis.Ecdf e = curve.ecdf;
                    if (e.x.length == 0) { continue; }
                    g.setColor(COLORS[index(curve.method, curve.scope)]);
                    g.setStroke(stroke(curve.scope));
                    Path2D line = new Path2D.Double();
                    line.moveTo(plot.x, plot.y + plot.height);
                    double previous = 0;
                    for (int i = 0; i < e.x.length; i++) {
                        double px = x.pixel(displayCoordinate(metric, e.x[i]), plot.x, plot.width);
                        line.lineTo(px, plot.y + plot.height * (1 - previous));
                        line.lineTo(px, plot.y + plot.height * (1 - e.y[i]));
                        previous = e.y[i];
                    }
                    line.lineTo(plot.x + plot.width, plot.y + plot.height * (1 - previous));
                    g.draw(line);
                }
                g.setClip(oldClip);
                if (x.empty) { empty(g, plot, "No finite observations"); }
                int row = 0;
                for (WholeLibraryAnalysis.Curve curve : series) {
                    int y = 1395 + row++ * 90;
                    g.setColor(COLORS[index(curve.method, curve.scope)]);
                    g.setStroke(stroke(curve.scope));
                    g.drawLine(plot.x, y - 12, plot.x + 90, y - 12);
                    g.setFont(font(24));
                    g.drawString(seriesLabel(curve.method, curve.scope, poolSize), plot.x + 110, y);
                    g.setColor(Color.DARK_GRAY);
                    g.setFont(font(21));
                    g.drawString("finite libraries " + curve.ecdf.libraries + "; empty " + curve.ecdf.emptyLibraries,
                            plot.x + 110, y + 42);
                }
                panel++;
            }
            footer(g, "Log-P / log-S axes are display only; all finite ECDF data and raw-S summaries are retained.", 1770);
        } finally { g.dispose(); }
        png(path, image);
    }

    private static void scatter(Path path, List<WholeLibraryAnalysis.Sample> samples, int poolSize) throws IOException {
        BufferedImage image = new BufferedImage(3600, 1800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        try {
            heading(g, "Available-quality attribute scatter",
                    "Finite pairs: QDGPf / QDGP; cap " + WholeLibraryAnalysis.PLOT_CAP + " per library/scope/panel");
            int panel = 0;
            for (int metric : new int[]{WholeLibraryAnalysis.C, WholeLibraryAnalysis.LOG_S}) {
                Rectangle plot = new Rectangle(230 + panel * 1770, 300, 1420, 900);
                double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
                for (WholeLibraryAnalysis.Sample sample : samples) {
                    if (sample.yMetric != metric) { continue; }
                    double coordinate = displayCoordinate(WholeLibraryAnalysis.P, sample.member.quality);
                    minX = Math.min(minX, coordinate); maxX = Math.max(maxX, coordinate);
                    minY = Math.min(minY, sample.member.value(metric)); maxY = Math.max(maxY, sample.member.value(metric));
                }
                Range x = Range.of(minX, maxX), y = Range.of(minY, maxY);
                axes(g, plot, x, y, "(" + (char) ('a' + panel) + ") P vs " + panelLabel(metric),
                        label(WholeLibraryAnalysis.P), label(metric));
                Shape oldClip = g.getClip();
                g.clip(plot);
                for (WholeLibraryAnalysis.Sample sample : samples) {
                    if (sample.yMetric != metric) { continue; }
                    Color color = COLORS[index(sample.member.method, sample.scope)];
                    g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 95));
                    double px = x.pixel(displayCoordinate(WholeLibraryAnalysis.P, sample.member.quality), plot.x, plot.width);
                    double py = plot.y + plot.height - y.pixel(sample.member.value(metric), 0, plot.height);
                    if (sample.scope == 0) {
                        g.fill(new Ellipse2D.Double(px - 5, py - 5, 10, 10));
                    } else {
                        Path2D triangle = new Path2D.Double();
                        triangle.moveTo(px, py - 7); triangle.lineTo(px - 6, py + 5);
                        triangle.lineTo(px + 6, py + 5); triangle.closePath();
                        g.setStroke(new BasicStroke(1.7f));
                        g.draw(triangle);
                    }
                }
                g.setClip(oldClip);
                if (x.empty) { empty(g, plot, "No finite validation/attribute pairs"); }
                int row = 0;
                for (String method : WholeLibraryAnalysis.METHODS) {
                    for (int scope = 0; scope < 2; scope++) {
                        int count = 0;
                        Set<Integer> runs = new HashSet<>();
                        for (WholeLibraryAnalysis.Sample sample : samples) {
                            if (sample.yMetric == metric && sample.scope == scope && sample.member.method.equals(method)) {
                                count++; runs.add(sample.member.run);
                            }
                        }
                        int ly = 1395 + row++ * 90;
                        g.setColor(COLORS[index(method, scope)]);
                        g.setFont(font(26));
                        g.drawString(seriesLabel(method, scope, poolSize), plot.x, ly);
                        g.setFont(font(22));
                        g.drawString("plotted pairs " + count + ", represented libraries " + runs.size(),
                                plot.x, ly + 42);
                    }
                }
                panel++;
            }
            footer(g, "Log axes are display only; CSV P stays raw. Statistics use full data, not plot samples.", 1770);
        } finally { g.dispose(); }
        png(path, image);
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static void heading(Graphics2D g, String title, String subtitle) {
        g.setColor(Color.BLACK);
        g.setFont(font(49).deriveFont(Font.BOLD));
        g.drawString(title, 100, 95);
        g.setFont(font(31));
        g.drawString(subtitle, 100, 175);
    }

    private static void axes(Graphics2D g, Rectangle p, Range x, Range y, String title, String xLabel, String yLabel) {
        g.setFont(font(24));
        for (int tick = 0; tick <= 5; tick++) {
            double fraction = tick / 5.0;
            int px = p.x + (int) Math.round(p.width * fraction);
            int py = p.y + p.height - (int) Math.round(p.height * fraction);
            g.setStroke(new BasicStroke(1));
            g.setColor(new Color(225, 225, 225));
            g.draw(new Line2D.Double(px, p.y, px, p.y + p.height));
            g.draw(new Line2D.Double(p.x, py, p.x + p.width, py));
            g.setColor(Color.DARK_GRAY);
            String xt = format(x.at(fraction)), yt = format(y.at(fraction));
            g.drawString(xt, px - g.getFontMetrics().stringWidth(xt) / 2, p.y + p.height + 55);
            g.drawString(yt, p.x - g.getFontMetrics().stringWidth(yt) - 16, py + 8);
        }
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(Color.BLACK);
        g.draw(p);
        g.setFont(font(34).deriveFont(Font.BOLD));
        g.drawString(title, p.x, p.y - 45);
        g.setFont(font(29));
        g.drawString(xLabel, p.x + (p.width - g.getFontMetrics().stringWidth(xLabel)) / 2, p.y + p.height + 115);
        java.awt.geom.AffineTransform transform = g.getTransform();
        g.rotate(-Math.PI / 2);
        g.drawString(yLabel, -p.y - (p.height + g.getFontMetrics().stringWidth(yLabel)) / 2, p.x - 155);
        g.setTransform(transform);
    }

    private static void empty(Graphics2D g, Rectangle plot, String label) {
        g.setColor(Color.DARK_GRAY);
        g.setFont(font(30));
        g.drawString(label, plot.x + 45, plot.y + plot.height / 2);
    }

    private static void footer(Graphics2D g, String label, int y) {
        g.setColor(Color.DARK_GRAY);
        g.setFont(font(27));
        g.drawString(label, 100, y);
    }

    static Font font(int size) { return new Font("SansSerif", Font.PLAIN, (int) Math.round(size * 1.5)); }
    private static String format(double value) { return String.format(Locale.ROOT, "%.3g", value); }
    private static int index(String method, int scope) { return (method.equals(WholeLibraryAnalysis.METHODS[0]) ? 0 : 2) + scope; }
    static String seriesLabel(String method, int scope, int poolSize) {
        String friendly;
        if (method.equals("NfixIns")) { friendly = "QDGPf"; }
        else if (method.equals("NrotateInsNoReeva")) { friendly = "QDGP"; }
        else { throw new IllegalArgumentException("Unknown figure regime: " + method); }
        return friendly + (scope == 0 ? ": whole retained" : ": top " + poolSize + " pool");
    }
    private static String label(int metric) {
        if (metric == WholeLibraryAnalysis.P) { return "Display log10(1 + P)"; }
        if (metric == WholeLibraryAnalysis.C) { return "Complexity C"; }
        return "Display log10(1 + S)";
    }
    private static String panelLabel(int metric) {
        if (metric == WholeLibraryAnalysis.P) { return "Available validation P"; }
        if (metric == WholeLibraryAnalysis.C) { return "Complexity C"; }
        return "Sensitivity display";
    }
    static double displayCoordinate(int metric, double rawValue) {
        if (!Double.isFinite(rawValue) || rawValue < 0) {
            throw new IllegalArgumentException("Plot coordinates require finite nonnegative metric values");
        }
        return metric == WholeLibraryAnalysis.P ? Math.log1p(rawValue) / Math.log(10) : rawValue;
    }
    private static BasicStroke stroke(int scope) {
        return scope == 0 ? new BasicStroke(6.75f)
                : new BasicStroke(6.75f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[]{25, 16}, 0);
    }
    private static void png(Path path, BufferedImage image) throws IOException {
        try (OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            if (!ImageIO.write(image, "png", stream)) { throw new IOException("No JDK PNG writer available"); }
        }
    }

    static final class Range {
        final double lo, hi;
        final boolean empty;
        Range(double lo, double hi, boolean empty) { this.lo = lo; this.hi = hi; this.empty = empty; }
        static Range of(double min, double max) {
            if (!Double.isFinite(min) || !Double.isFinite(max)) { return new Range(0, 1, true); }
            double pad = max > min ? (max - min) * 0.04
                    : max == 0 ? 1 : Math.max(max * 0.05, Math.ulp(max) * 4);
            double lo = Math.max(0, min - pad);
            double hi = Double.isFinite(max + pad) ? max + pad : max;
            if (hi <= lo) { lo = 0; hi = max > 0 ? max : 1; }
            return new Range(lo, hi, false);
        }
        double at(double fraction) { return lo * (1 - fraction) + hi * fraction; }
        double pixel(double value, double start, double length) { return start + (value - lo) / (hi - lo) * length; }
    }
}
