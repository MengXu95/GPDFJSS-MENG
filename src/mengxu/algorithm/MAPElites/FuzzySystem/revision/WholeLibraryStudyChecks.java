package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import yimei.jss.rule.operation.evolved.GPRule;

public final class WholeLibraryStudyChecks {
    private WholeLibraryStudyChecks() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) { throw new IllegalArgumentException("Provide a new fixture directory"); }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(root)) { throw new IOException("Fixture directory already exists"); }
        Files.createDirectories(root);
        Path fixed = fixture(root, "NfixIns"), rotating = fixture(root, "NrotateInsNoReeva");
        Path output = root.resolve("B");
        WholeLibraryStudy.run(Arrays.asList(fixed, rotating), output, 1, false);
        Properties manifest = ExperimentAFiles.read(output.resolve("manifest.properties"));
        require("COMPLETE".equals(manifest.getProperty("status")), "complete");
        require("10".equals(manifest.getProperty("retained-members")), "all members");
        require("2".equals(manifest.getProperty("quality-not-evaluated")), "unknown quality retained");
        require("2".equals(manifest.getProperty("quality-failed")), "failed quality retained");
        require("2".equals(manifest.getProperty("undefined-sensitivity")), "undefined sensitivity retained");
        require("0".equals(manifest.getProperty("scheduling-simulation-count")), "zero simulations");
        List<String> rows = Files.readAllLines(output.resolve("members.csv"), StandardCharsets.UTF_8);
        require(rows.size() == 11, "complete aggregate");
        List<String> duplicate = ExperimentAAnalysis.csvFields(rows.get(5));
        require(duplicate.get(5).equals("100.0") && duplicate.get(8).equals("VALIDATION_FINITE"),
                "duplicate quality reuses matching expression cache");
        String outputHash = RevisionCsv.fileHash(output.resolve("manifest.properties"));
        WholeLibraryStudy.run(Arrays.asList(fixed, rotating), output, 1, true);
        require(outputHash.equals(RevisionCsv.fileHash(output.resolve("manifest.properties"))),
                "completed resume does not alter output");
        boolean rejected = false;
        try { WholeLibraryStudy.run(Arrays.asList(fixed, rotating), output, 1, false); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "explicit resume required");
        Files.writeString(output.resolve("members.csv"), "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        rejected = false;
        try { WholeLibraryStudy.run(Arrays.asList(fixed, rotating), output, 1, true); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "tampered B output rejected");
        Path aMetrics = fixed.resolve("run-0").resolve("candidate_metrics.csv");
        Files.writeString(aMetrics, "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        rejected = false;
        try { WholeLibraryStudy.run(Arrays.asList(fixed, rotating), root.resolve("tampered-A"), 1, false); }
        catch (IOException expected) { rejected = true; }
        require(rejected && !Files.exists(root.resolve("tampered-A")), "tampered A rejected before output");
        System.out.println("WHOLE LIBRARY STUDY CHECKS PASSED (all members, caches, duplicates, unknown/failure, resume, hashes)");
    }

    private static Path fixture(Path root, String method) throws IOException {
        Path source = root.resolve("source").resolve(method).resolve("multipletreegp-dynamic")
                .resolve("fixture").resolve("job.0.out.stat");
        Files.createDirectories(source.getParent());
        String[] seq = {"PT", "(* PT 1.0E308)", "0.0", "(Max PT W)", "PT"};
        String[] route = {"W", "PT", "-0.0", "(+ W W)", "W"};
        double[] stored = {1, 2, Double.POSITIVE_INFINITY, 3, 1};
        StringBuilder text = new StringBuilder("All individuals in GridMAP:\nGridMAP number: 5\n");
        for (int i = 0; i < seq.length; i++) {
            text.append("Evaluated: true\nFitness: [").append(stored[i])
                    .append("]\nTree 0:\n").append(seq[i]).append("\nTree 1:\n").append(route[i]).append('\n');
        }
        text.append("Best Individual of Run:\n");
        Files.writeString(source, text, StandardCharsets.UTF_8);
        Path study = root.resolve(method), run = study.resolve("run-0");
        Files.createDirectories(run);
        Map<String, Double> values = new LinkedHashMap<>();
        for (String terminal : SensitivityProbeSet.supportedTerminals()) { values.put(terminal, 2.0); }
        List<SensitivityProbeSet.Probe> probes = new ArrayList<>();
        probes.add(new SensitivityProbeSet.Probe("fixture", values));
        SensitivityProbeSet frozen = new SensitivityProbeSet(probes, probes);
        RevisionExperiment.exportProbes(study, frozen);
        Files.copy(study.resolve("probes.csv"), run.resolve("probes.csv"));
        for (String artifact : Arrays.asList("profiles.csv", "selections.csv", "test_instances.csv", "run_results.csv")) {
            Files.writeString(run.resolve(artifact), "fixture\n", StandardCharsets.UTF_8);
        }
        Files.copy(run.resolve("profiles.csv"), study.resolve("profiles.csv"));
        Files.copy(run.resolve("run_results.csv"), study.resolve("run_results.csv"));
        List<ArchiveCandidate> candidates = new FinalArchiveReader().read(source).candidates();
        try (RevisionCsv members = new RevisionCsv(run.resolve("archive_members.csv"), "candidate_id",
                "archive_ordinal", "tree_hash", "stored_fitness", "status", "candidate_pool",
                "duplicate_of", "reason", "sequencing", "routing");
             RevisionCsv metrics = new RevisionCsv(run.resolve("candidate_metrics.csv"), "candidate_id",
                     "mean_validation_objective", "complexity", "sensitivity", "status", "reason")) {
            String[] status = {"POOL", "EXCLUDED_VALIDATION_FAILURE", "EXCLUDED_STORED_FAILURE",
                    "VALIDATION_PASSED_NOT_CHARACTERISED", "EXCLUDED_DUPLICATE_EXPRESSION"};
            for (int i = 0; i < candidates.size(); i++) {
                ArchiveCandidate candidate = candidates.get(i);
                members.row(candidate.id(), i, candidate.treeHash(), candidate.storedFitness(), status[i],
                        i == 0, i == 4 ? candidates.get(0).id() : "", "", seq[i], route[i]);
                GPRule[] rules = candidate.rules();
                metrics.row(candidate.id(), i == 2 || i == 4 ? "" : i == 1 ? Double.POSITIVE_INFINITY : 100.0,
                        i == 0 ? 2.0 : "", i == 0 ? new OutputSensitivityCalculator(.001).calculate(rules, frozen) : "",
                        status[i], "");
            }
        }
        Properties runManifest = new Properties();
        runManifest.setProperty("status", "COMPLETE");
        runManifest.setProperty("run-id", "0");
        runManifest.setProperty("study-hash", method + "-fixture");
        runManifest.setProperty("archive-sha256", RevisionCsv.fileHash(source));
        for (String artifact : Arrays.asList("profiles.csv", "probes.csv", "archive_members.csv",
                "candidate_metrics.csv", "selections.csv", "test_instances.csv", "run_results.csv")) {
            runManifest.setProperty("data." + artifact + ".sha256", RevisionCsv.fileHash(run.resolve(artifact)));
        }
        ExperimentAFiles.write(run.resolve("manifest.properties"), runManifest);
        Properties manifest = new Properties();
        manifest.setProperty("status", "COMPLETE");
        manifest.setProperty("protocol", ExperimentAStudy.PROTOCOL);
        manifest.setProperty("method", method);
        manifest.setProperty("num-runs", "1");
        manifest.setProperty("run-ids", "0");
        manifest.setProperty("run.0.path", "run-0");
        manifest.setProperty("study-hash", method + "-fixture");
        manifest.setProperty("input-root", root.resolve("source").toString());
        manifest.setProperty("scenario", "fixture");
        manifest.setProperty("machines", "10");
        manifest.setProperty("recorded-jobs", "1000");
        manifest.setProperty("warmup-jobs", "500");
        manifest.setProperty("validation-seed", "718341");
        manifest.setProperty("validation-instances", "5");
        manifest.setProperty("profiles", "importance-v1");
        manifest.setProperty("normalisation", "all-minmax");
        manifest.setProperty("epsilon", ".001");
        manifest.setProperty("results-sha256", RevisionCsv.fileHash(study.resolve("run_results.csv")));
        manifest.setProperty("profiles-sha256", RevisionCsv.fileHash(study.resolve("profiles.csv")));
        manifest.setProperty("probes-sha256", RevisionCsv.fileHash(study.resolve("probes.csv")));
        ExperimentAFiles.write(study.resolve("manifest.properties"), manifest);
        return study;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
