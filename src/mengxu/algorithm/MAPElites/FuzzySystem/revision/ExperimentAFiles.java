package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

final class ExperimentAFiles {
    private ExperimentAFiles() { }

    static Properties read(Path path) throws IOException {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { values.load(reader); }
        return values;
    }

    static void write(Path path, Properties values) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".study-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "Fuzzy experiment A; raw results are immutable");
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    static String fingerprint(RevisionConfiguration configuration,
                              Map<Integer, FinalArchiveReader.Archive> archives) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            TreeMap<String, String> values = new TreeMap<>(configuration.options());
            values.remove("output-root");
            values.remove("dry-run");
            values.put("study-protocol", ExperimentAStudy.PROTOCOL);
            values.put("java-runtime", System.getProperty("java.runtime.version"));
            for (Map.Entry<String, String> entry : values.entrySet()) {
                digest.update((entry.getKey() + "=" + entry.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            for (Map.Entry<Integer, FinalArchiveReader.Archive> entry : archives.entrySet()) {
                digest.update((entry.getKey() + "=" + entry.getValue().sourceHash() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            Class<?>[] classes = {ExperimentAStudy.class, ExperimentAStudy.PhysicalEvaluation.class,
                    ExperimentAPool.class, ExperimentAPool.Entry.class, ExperimentAFiles.class,
                    SchedulingEvaluator.class, SchedulingEvaluator.Evaluation.class, RevisionMetrics.class,
                    ArchiveCandidate.class, FinalArchiveReader.class, RevisionConfiguration.class,
                    RevisionExperiment.class, CandidateMetricException.class, SensitivityProbeSet.Probe.class,
                    mengxu.algorithm.MAPElites.FuzzySystem.TreeComplexityCalculator.class,
                    mengxu.algorithm.MAPElites.FuzzySystem.Heuristic.class,
                    mengxu.algorithm.MAPElites.FuzzySystem.MonotoneFuzzyUtility.class,
                    mengxu.algorithm.MAPElites.FuzzySystem.FuzzyHeuristicSelector.PreferenceMode.class,
                    mengxu.algorithm.MAPElites.FuzzySystem.FuzzyVariable.class,
                    mengxu.algorithm.MAPElites.FuzzySystem.FuzzyRule.class,
                    PriorityExpression.class, OutputSensitivityCalculator.class, SensitivityProbeSet.class,
                    FuzzyModelFactory.class, mengxu.algorithm.MAPElites.FuzzySystem.FuzzyHeuristicSelector.class,
                    mengxu.algorithm.MAPElites.FuzzySystem.UserPreferenceVector.class,
                    mengxu.algorithm.MAPElites.FuzzySystem.FuzzyInferenceSystem.class,
                    mengxu.algorithm.MAPElites.FuzzySystem.FuzzyMembershipFunction.class,
                    mengxu.complexsimulation.HeterogeneousSimulation.class,
                    yimei.jss.simulation.DynamicSimulation.class, yimei.jss.simulation.Simulation.class,
                    yimei.jss.jobshop.Job.class, yimei.jss.jobshop.WorkCenter.class,
                    yimei.jss.jobshop.Operation.class, yimei.jss.jobshop.OperationOption.class,
                    yimei.jss.rule.operation.evolved.GPRule.class,
                    yimei.jss.rule.AbstractRule.class,
                    yimei.jss.gp.terminal.AttributeGPNode.class, yimei.jss.gp.terminal.ConstantTerminal.class,
                    yimei.jss.simulation.state.SystemState.class,
                    yimei.jss.simulation.event.JobArrivalEvent.class,
                    yimei.jss.simulation.event.ProcessFinishEvent.class,
                    yimei.jss.gp.function.Add.class, yimei.jss.gp.function.Sub.class,
                    yimei.jss.gp.function.Mul.class,
                    yimei.jss.gp.terminal.JobShopAttribute.class, yimei.jss.gp.function.Min.class,
                    yimei.jss.gp.function.Max.class, yimei.jss.gp.function.Div.class};
            for (Class<?> type : classes) {
                String name = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
                try (InputStream input = type.getResourceAsStream(name)) {
                    if (input == null) { throw new IOException("Missing implementation fingerprint resource: " + name); }
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); }
                }
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) { result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static boolean complete(Properties manifest) {
        String status = manifest.getProperty("status");
        return "COMPLETE".equals(status) || "COMPLETE_WITH_TEST_FAILURES".equals(status);
    }

    static void checkHash(Path path, String expected) throws IOException {
        if (expected == null || !expected.equals(RevisionCsv.fileHash(path))) {
            throw new IOException("Missing or changed verified artifact: " + path);
        }
    }

    static SensitivityProbeSet readProbes(Path path) throws IOException {
        Map<String, Map<String, Double>> sequencing = new LinkedHashMap<>();
        Map<String, Map<String, Double>> routing = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("role,probe_id,terminal,raw_value")) {
            throw new IOException("Invalid frozen-probe header");
        }
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split(",", -1);
            if (fields.length != 4) { throw new IOException("Invalid frozen-probe row"); }
            Map<String, Map<String, Double>> role;
            if (fields[0].equals("SEQUENCING")) { role = sequencing; }
            else if (fields[0].equals("ROUTING")) { role = routing; }
            else { throw new IOException("Unknown probe role"); }
            Map<String, Double> values = role.computeIfAbsent(fields[1], ignored -> new LinkedHashMap<>());
            if (values.putIfAbsent(fields[2], Double.parseDouble(fields[3])) != null) {
                throw new IOException("Duplicate probe terminal");
            }
        }
        return new SensitivityProbeSet(probes(sequencing), probes(routing));
    }

    private static List<SensitivityProbeSet.Probe> probes(Map<String, Map<String, Double>> source) throws IOException {
        List<SensitivityProbeSet.Probe> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> entry : source.entrySet()) {
            if (!entry.getValue().keySet().containsAll(SensitivityProbeSet.supportedTerminals())
                    || entry.getValue().size() != SensitivityProbeSet.supportedTerminals().size()) {
                throw new IOException("Incomplete frozen probe: " + entry.getKey());
            }
            result.add(new SensitivityProbeSet.Probe(entry.getKey(), entry.getValue()));
        }
        return result;
    }
}
