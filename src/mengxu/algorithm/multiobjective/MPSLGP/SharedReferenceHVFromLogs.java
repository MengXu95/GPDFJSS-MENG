package mengxu.algorithm.multiobjective.MPSLGP;

import java.io.BufferedWriter;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recomputes a screening hypervolume from final Pareto fronts printed in MPSLGP logs.
 */
public class SharedReferenceHVFromLogs {

    private static final Pattern FRONT_HEADER = Pattern.compile("Pareto Front of Subpopulation\\s+(\\d+)");
    private static final Pattern OBJECTIVE_LINE = Pattern.compile("Objective\\s+(\\d+):\\s*\\[(.*)");
    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[Ee][-+]?\\d+)?");

    private static final class FrontRecord {
        final String label;
        final Path source;
        final int generation;
        final double[][] points;
        final double[][] nondominatedPoints;
        final double hypervolume;

        FrontRecord(String label, Path source, int generation, double[][] points,
                    double[][] nondominatedPoints, double hypervolume) {
            this.label = label;
            this.source = source;
            this.generation = generation;
            this.points = points;
            this.nondominatedPoints = nondominatedPoints;
            this.hypervolume = hypervolume;
        }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Arguments arguments = Arguments.parse(args);
        if (arguments.inputs.isEmpty()) {
            printUsage();
            return;
        }

        List<ParsedFront> parsedFronts = new ArrayList<>();
        for (Map.Entry<String, Path> input : arguments.inputs.entrySet()) {
            parsedFronts.add(parseFront(input.getKey(), input.getValue(), arguments.generation));
        }

        int objectiveCount = parsedFronts.get(0).points[0].length;
        validateObjectiveCounts(parsedFronts, objectiveCount);

        double[] ideal = commonIdeal(parsedFronts, objectiveCount);
        double[] nadir = commonNadir(parsedFronts, objectiveCount);
        double[] reference = commonReference(ideal, nadir, arguments.referencePaddingRatio);

        List<FrontRecord> records = new ArrayList<>();
        for (ParsedFront parsedFront : parsedFronts) {
            double[][] nondominatedPoints = nondominatedForMinimization(parsedFront.points);
            double[][] normalisedMaxPoints = normaliseForMaximization(nondominatedPoints, ideal, reference);
            double hypervolume = hypervolume(normalisedMaxPoints, objectiveCount);
            records.add(new FrontRecord(parsedFront.label, parsedFront.source, parsedFront.generation,
                    parsedFront.points, nondominatedPoints, hypervolume));
        }

        records.sort(Comparator.comparingDouble((FrontRecord record) -> record.hypervolume).reversed());
        printReport(records, ideal, nadir, reference, arguments.referencePaddingRatio);

        if (arguments.output != null) {
            writeCsv(records, ideal, nadir, reference, arguments.output);
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java ... mengxu.algorithm.multiobjective.MPSLGP.SharedReferenceHVFromLogs [options] label=log.txt ...");
        System.out.println("Options:");
        System.out.println("  --generation <n>          Pareto-front generation to parse; default: last printed front in each log");
        System.out.println("  --reference-padding <r>   Shared reference padding ratio beyond common nadir; default: 0.01");
        System.out.println("  --out <file.csv>          Optional CSV output path");
        System.out.println("  --params <file.params>    Properties file with method.<label>=<log path> entries");
    }

    private static final class Arguments {
        final Map<String, Path> inputs = new LinkedHashMap<>();
        int generation = -1;
        double referencePaddingRatio = 0.01;
        Path output;

        static Arguments parse(String[] args) throws IOException {
            Arguments arguments = new Arguments();
            for (int argIndex = 0; argIndex < args.length; argIndex++) {
                String arg = args[argIndex];
                if ("--generation".equals(arg)) {
                    arguments.generation = Integer.parseInt(requireValue(args, ++argIndex, arg));
                } else if ("--reference-padding".equals(arg)) {
                    arguments.referencePaddingRatio = Double.parseDouble(requireValue(args, ++argIndex, arg));
                } else if ("--out".equals(arg)) {
                    arguments.output = Paths.get(requireValue(args, ++argIndex, arg));
                } else if ("--params".equals(arg)) {
                    arguments.loadParams(Paths.get(requireValue(args, ++argIndex, arg)));
                } else if (arg.contains("=")) {
                    int separator = arg.indexOf('=');
                    String label = arg.substring(0, separator).trim();
                    Path path = Paths.get(arg.substring(separator + 1).trim());
                    if (label.isEmpty()) {
                        throw new IllegalArgumentException("Empty label in argument: " + arg);
                    }
                    arguments.inputs.put(label, path);
                } else {
                    Path path = Paths.get(arg);
                    arguments.inputs.put(stripExtension(path.getFileName().toString()), path);
                }
            }
            return arguments;
        }

        private void loadParams(Path paramsFile) throws IOException {
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(paramsFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }

            String generationValue = properties.getProperty("generation");
            if (generationValue != null && !generationValue.trim().isEmpty()) {
                generation = Integer.parseInt(generationValue.trim());
            }

            String paddingValue = properties.getProperty("reference-padding");
            if (paddingValue != null && !paddingValue.trim().isEmpty()) {
                referencePaddingRatio = Double.parseDouble(paddingValue.trim());
            }

            String outputValue = properties.getProperty("out");
            if (outputValue != null && !outputValue.trim().isEmpty()) {
                output = resolveRelative(paramsFile, outputValue.trim());
            }

            properties.stringPropertyNames().stream()
                    .filter(propertyName -> propertyName.startsWith("method."))
                    .sorted()
                    .forEach(propertyName -> {
                        String label = propertyName.substring("method.".length());
                        String pathValue = properties.getProperty(propertyName).trim();
                        if (!pathValue.isEmpty()) {
                            inputs.put(label, resolveRelative(paramsFile, pathValue));
                        }
                    });
        }

        private static Path resolveRelative(Path paramsFile, String pathValue) {
            Path path = Paths.get(pathValue);
            if (path.isAbsolute()) {
                return path;
            }
            Path parent = paramsFile.toAbsolutePath().getParent();
            return parent == null ? path : parent.resolve(path).normalize();
        }

        private static String requireValue(String[] args, int argIndex, String option) {
            if (argIndex >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[argIndex];
        }
    }

    private static final class ParsedFront {
        final String label;
        final Path source;
        final int generation;
        final double[][] points;

        ParsedFront(String label, Path source, int generation, double[][] points) {
            this.label = label;
            this.source = source;
            this.generation = generation;
            this.points = points;
        }
    }

    private static ParsedFront parseFront(String label, Path source, int requestedGeneration) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Log file not found: " + source);
        }

        Map<Integer, Map<Integer, double[]>> frontsByGeneration = new TreeMap<>();
        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        int currentGeneration = -1;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex).trim();
            Matcher headerMatcher = FRONT_HEADER.matcher(line);
            if (headerMatcher.find()) {
                currentGeneration = Integer.parseInt(headerMatcher.group(1));
                frontsByGeneration.putIfAbsent(currentGeneration, new TreeMap<>());
                continue;
            }

            if (currentGeneration < 0) {
                continue;
            }

            Matcher objectiveMatcher = OBJECTIVE_LINE.matcher(line);
            if (!objectiveMatcher.find()) {
                continue;
            }

            int objectiveIndex = Integer.parseInt(objectiveMatcher.group(1));
            StringBuilder objectiveText = new StringBuilder(objectiveMatcher.group(2));
            while (objectiveText.indexOf("]") < 0 && lineIndex + 1 < lines.size()) {
                lineIndex++;
                objectiveText.append(' ').append(lines.get(lineIndex).trim());
            }
            frontsByGeneration.get(currentGeneration).put(objectiveIndex, parseNumbers(objectiveText.toString()));
        }

        if (frontsByGeneration.isEmpty()) {
            throw new IllegalArgumentException("No printed Pareto fronts found in: " + source);
        }

        int generation = requestedGeneration >= 0 ? requestedGeneration : frontsByGeneration.keySet().stream()
                .max(Integer::compareTo).orElseThrow(IllegalStateException::new);
        Map<Integer, double[]> objectiveArrays = frontsByGeneration.get(generation);
        if (objectiveArrays == null || objectiveArrays.isEmpty()) {
            throw new IllegalArgumentException("Generation " + generation + " was not found in: " + source);
        }

        return new ParsedFront(label, source, generation, zipObjectives(objectiveArrays, source));
    }

    private static double[] parseNumbers(String text) {
        Matcher numberMatcher = NUMBER.matcher(text);
        List<Double> values = new ArrayList<>();
        while (numberMatcher.find()) {
            values.add(Double.parseDouble(numberMatcher.group()));
        }
        double[] result = new double[values.size()];
        for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
            result[valueIndex] = values.get(valueIndex);
        }
        return result;
    }

    private static double[][] zipObjectives(Map<Integer, double[]> objectiveArrays, Path source) {
        int objectiveCount = objectiveArrays.size();
        for (int objectiveIndex = 0; objectiveIndex < objectiveCount; objectiveIndex++) {
            if (!objectiveArrays.containsKey(objectiveIndex)) {
                throw new IllegalArgumentException("Missing Objective " + objectiveIndex + " in: " + source);
            }
        }

        int pointCount = objectiveArrays.get(0).length;
        if (pointCount == 0) {
            throw new IllegalArgumentException("Empty Pareto front in: " + source);
        }
        for (int objectiveIndex = 1; objectiveIndex < objectiveCount; objectiveIndex++) {
            if (objectiveArrays.get(objectiveIndex).length != pointCount) {
                throw new IllegalArgumentException("Objective arrays have different lengths in: " + source);
            }
        }

        double[][] points = new double[pointCount][objectiveCount];
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            for (int objectiveIndex = 0; objectiveIndex < objectiveCount; objectiveIndex++) {
                points[pointIndex][objectiveIndex] = objectiveArrays.get(objectiveIndex)[pointIndex];
            }
        }
        return points;
    }

    private static void validateObjectiveCounts(List<ParsedFront> parsedFronts, int objectiveCount) {
        for (ParsedFront parsedFront : parsedFronts) {
            if (parsedFront.points[0].length != objectiveCount) {
                throw new IllegalArgumentException("Objective count mismatch for " + parsedFront.label);
            }
        }
    }

    private static double[] commonIdeal(List<ParsedFront> parsedFronts, int objectiveCount) {
        double[] ideal = new double[objectiveCount];
        Arrays.fill(ideal, Double.POSITIVE_INFINITY);
        for (ParsedFront parsedFront : parsedFronts) {
            for (double[] point : parsedFront.points) {
                for (int objectiveIndex = 0; objectiveIndex < objectiveCount; objectiveIndex++) {
                    ideal[objectiveIndex] = Math.min(ideal[objectiveIndex], point[objectiveIndex]);
                }
            }
        }
        return ideal;
    }

    private static double[] commonNadir(List<ParsedFront> parsedFronts, int objectiveCount) {
        double[] nadir = new double[objectiveCount];
        Arrays.fill(nadir, Double.NEGATIVE_INFINITY);
        for (ParsedFront parsedFront : parsedFronts) {
            for (double[] point : parsedFront.points) {
                for (int objectiveIndex = 0; objectiveIndex < objectiveCount; objectiveIndex++) {
                    nadir[objectiveIndex] = Math.max(nadir[objectiveIndex], point[objectiveIndex]);
                }
            }
        }
        return nadir;
    }

    private static double[] commonReference(double[] ideal, double[] nadir, double paddingRatio) {
        double[] reference = new double[ideal.length];
        for (int objectiveIndex = 0; objectiveIndex < ideal.length; objectiveIndex++) {
            double range = nadir[objectiveIndex] - ideal[objectiveIndex];
            reference[objectiveIndex] = range == 0.0 ? nadir[objectiveIndex] + 1.0 : nadir[objectiveIndex] + range * paddingRatio;
        }
        return reference;
    }

    private static double[][] nondominatedForMinimization(double[][] points) {
        List<double[]> nondominated = new ArrayList<>();
        for (int candidateIndex = 0; candidateIndex < points.length; candidateIndex++) {
            boolean dominated = false;
            for (int comparisonIndex = 0; comparisonIndex < points.length; comparisonIndex++) {
                if (candidateIndex != comparisonIndex && dominatesForMinimization(points[comparisonIndex], points[candidateIndex])) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated && !containsPoint(nondominated, points[candidateIndex])) {
                nondominated.add(points[candidateIndex].clone());
            }
        }
        return nondominated.toArray(new double[0][]);
    }

    private static boolean dominatesForMinimization(double[] left, double[] right) {
        boolean strictlyBetter = false;
        for (int objectiveIndex = 0; objectiveIndex < left.length; objectiveIndex++) {
            if (left[objectiveIndex] > right[objectiveIndex]) {
                return false;
            }
            if (left[objectiveIndex] < right[objectiveIndex]) {
                strictlyBetter = true;
            }
        }
        return strictlyBetter;
    }

    private static boolean containsPoint(List<double[]> points, double[] candidate) {
        for (double[] point : points) {
            if (Arrays.equals(point, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static double[][] normaliseForMaximization(double[][] points, double[] ideal, double[] reference) {
        double[][] normalised = new double[points.length][ideal.length];
        for (int pointIndex = 0; pointIndex < points.length; pointIndex++) {
            for (int objectiveIndex = 0; objectiveIndex < ideal.length; objectiveIndex++) {
                double denominator = reference[objectiveIndex] - ideal[objectiveIndex];
                double value = denominator == 0.0 ? 1.0 : (reference[objectiveIndex] - points[pointIndex][objectiveIndex]) / denominator;
                normalised[pointIndex][objectiveIndex] = Math.max(0.0, Math.min(1.0, value));
            }
        }
        return nondominatedForMaximization(normalised);
    }

    private static double[][] nondominatedForMaximization(double[][] points) {
        List<double[]> nondominated = new ArrayList<>();
        for (int candidateIndex = 0; candidateIndex < points.length; candidateIndex++) {
            boolean dominated = false;
            for (int comparisonIndex = 0; comparisonIndex < points.length; comparisonIndex++) {
                if (candidateIndex != comparisonIndex && dominatesForMaximization(points[comparisonIndex], points[candidateIndex])) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated && !containsPoint(nondominated, points[candidateIndex])) {
                nondominated.add(points[candidateIndex].clone());
            }
        }
        return nondominated.toArray(new double[0][]);
    }

    private static boolean dominatesForMaximization(double[] left, double[] right) {
        boolean strictlyBetter = false;
        for (int objectiveIndex = 0; objectiveIndex < left.length; objectiveIndex++) {
            if (left[objectiveIndex] < right[objectiveIndex]) {
                return false;
            }
            if (left[objectiveIndex] > right[objectiveIndex]) {
                strictlyBetter = true;
            }
        }
        return strictlyBetter;
    }

    private static double hypervolume(double[][] points, int dimensions) {
        if (points.length == 0) {
            return 0.0;
        }
        if (dimensions == 1) {
            double maximum = 0.0;
            for (double[] point : points) {
                maximum = Math.max(maximum, point[0]);
            }
            return maximum;
        }

        double[] heights = Arrays.stream(points)
                .mapToDouble(point -> point[dimensions - 1])
                .filter(height -> height > 0.0)
                .distinct()
                .sorted()
                .toArray();
        double volume = 0.0;
        double previousHeight = 0.0;
        for (double height : heights) {
            double sliceThickness = height - previousHeight;
            double[][] projected = Arrays.stream(points)
                    .filter(point -> point[dimensions - 1] >= height)
                    .map(point -> Arrays.copyOf(point, dimensions - 1))
                    .toArray(double[][]::new);
            volume += sliceThickness * hypervolume(nondominatedForMaximization(projected), dimensions - 1);
            previousHeight = height;
        }
        return volume;
    }

    private static void printReport(List<FrontRecord> records, double[] ideal, double[] nadir,
                                    double[] reference, double paddingRatio) {
        System.out.println("Shared-reference HV screening report");
        System.out.println("common ideal     = " + Arrays.toString(ideal));
        System.out.println("common nadir     = " + Arrays.toString(nadir));
        System.out.println("common reference = " + Arrays.toString(reference));
        System.out.println("reference padding ratio = " + paddingRatio);
        System.out.println();
        System.out.println("rank,label,generation,points,nondominated,shared_hv,source");
        for (int rankIndex = 0; rankIndex < records.size(); rankIndex++) {
            FrontRecord record = records.get(rankIndex);
            System.out.printf(Locale.US, "%d,%s,%d,%d,%d,%.12f,%s%n",
                    rankIndex + 1, record.label, record.generation, record.points.length,
                    record.nondominatedPoints.length, record.hypervolume, record.source);
        }
    }

    private static void writeCsv(List<FrontRecord> records, double[] ideal, double[] nadir,
                                 double[] reference, Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("rank,label,generation,points,nondominated,shared_hv,ideal,nadir,reference,source");
            writer.newLine();
            for (int rankIndex = 0; rankIndex < records.size(); rankIndex++) {
                FrontRecord record = records.get(rankIndex);
                writer.write(String.format(Locale.US, "%d,%s,%d,%d,%d,%.12f,\"%s\",\"%s\",\"%s\",%s",
                        rankIndex + 1, record.label, record.generation, record.points.length,
                        record.nondominatedPoints.length, record.hypervolume, Arrays.toString(ideal),
                        Arrays.toString(nadir), Arrays.toString(reference), record.source));
                writer.newLine();
            }
        }
        System.out.println("Wrote CSV: " + output);
    }

    private static String stripExtension(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator > 0 ? fileName.substring(0, separator) : fileName;
    }
}