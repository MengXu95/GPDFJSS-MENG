package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.FinalArchiveReader;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots;

public final class ETrainingChecks {
    private ETrainingChecks() { }
    public static void main(String[] args) throws IOException {
        if (args.length != 2) { throw new IllegalArgumentException("Provide module directory and NEW fixture root"); }
        Path module = Path.of(args[0]).toRealPath(), fixture = Path.of(args[1]).toAbsolutePath().normalize();
        if (Files.exists(fixture)) { throw new IOException("Fixture directory already exists"); }
        Path configFile = module.resolve("e-study.properties");
        EConfiguration formal = EConfiguration.parse(new String[]{"--config", configFile.toString()});
        require(formal.requestedCases().size() == 360 && formal.runs == 30
                && formal.nominalEvaluations() == 100500, "formal 360-case controls");
        EConfiguration full = EConfiguration.parse(new String[]{"--config", configFile.toString(),
                "--set", "methods=LEXICASE,GP,QDGP"});
        require(full.requestedCases().size() == 1080, "optional full universe");
        String identity = ETraining.fingerprint(formal);
        require(identity.equals(ETraining.fingerprint(full)), "method dispatch is not a scientific control");
        EConfiguration fixed = EConfiguration.parse(new String[]{"--config", configFile.toString(),
                "--set", "regimes=fixed"});
        require(fixed.requestedCases().size() == 180 && identity.equals(ETraining.fingerprint(fixed)),
                "fixed/rotating scopes can be run separately");
        boolean rejected = false;
        try { EConfiguration.parse(new String[]{"--config", configFile.toString(),
                "--set", "test-seed-base=100000000"}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "overlapping physical simulation seed families rejected");
        EConfiguration.Case t0 = new EConfiguration.Case(EConfiguration.Method.LEXICASE,
                EConfiguration.Regime.rotating, EConfiguration.Scenario.T085, 29);
        EConfiguration.Case t1 = new EConfiguration.Case(EConfiguration.Method.QDGP,
                EConfiguration.Regime.rotating, EConfiguration.Scenario.T095, 0);
        require(formal.trainingSeed(t0) + 200 * formal.seedStep < formal.trainingSeed(t1),
                "rotated generations do not enter another scenario's data block");

        List<String> options = new ArrayList<>();
        java.util.Collections.addAll(options, "--config", configFile.toString(),
                "--set", "training-output-root=" + fixture.resolve("training"),
                "--set", "test-output-root=" + fixture.resolve("test"),
                "--set", "runs=1", "--set", "population=10", "--set", "elitism=2",
                "--set", "generations=2", "--resume");
        List<String> preflight = new ArrayList<>(options); preflight.add("--dry-run");
        ETraining.run(EConfiguration.parse(preflight.toArray(new String[0])));
        require(!Files.exists(fixture), "preflight creates no directories or simulations");
        EConfiguration small = EConfiguration.parse(options.toArray(new String[0]));
        ETraining.run(small);
        Properties lexOnly = EFiles.read(small.trainingRoot.resolve("manifest.properties"));
        require("12".equals(lexOnly.getProperty("completed-case-count")), "default smoke has 12 Lex cases");
        Properties originalCaseHashes = new Properties();
        for (EConfiguration.Case item : small.requestedCases()) {
            originalCaseHashes.setProperty(item.key(), lexOnly.getProperty(item.key() + ".manifest-sha256"));
        }
        List<String> controls = new ArrayList<>(options);
        java.util.Collections.addAll(controls, "--set", "methods=GP,QDGP");
        ETraining.run(EConfiguration.parse(controls.toArray(new String[0])));
        Properties completed = EFiles.read(small.trainingRoot.resolve("manifest.properties"));
        require("36".equals(completed.getProperty("completed-case-count")), "controls append without discarding Lex cases");
        for (String key : originalCaseHashes.stringPropertyNames()) {
            require(originalCaseHashes.getProperty(key).equals(completed.getProperty(key + ".manifest-sha256")),
                    "existing Lex manifest is immutable");
        }
        List<String> all = new ArrayList<>(options);
        java.util.Collections.addAll(all, "--set", "methods=LEXICASE,GP,QDGP");
        EConfiguration allConfig = EConfiguration.parse(all.toArray(new String[0]));
        for (EConfiguration.Case item : allConfig.requestedCases()) {
            Path directory = EFiles.mapped(small.trainingRoot, completed.getProperty(item.key() + ".path"));
            Properties saved = EFiles.read(directory.resolve("manifest.properties"));
            ETraining.validateCase(allConfig, item, completed.getProperty("family-hash"), saved);
            require("20".equals(saved.getProperty("fitness-evaluations")), "actual case evaluation budget");
            EFiles.verifyArtifacts(directory, saved);
            if (item.method == EConfiguration.Method.QDGP) {
                require(new FinalArchiveReader().read(directory.resolve(item.finalFile())).declaredCount()
                        == Integer.parseInt(saved.getProperty("final-members")), "native archive readable");
            } else {
                require(PopulationSnapshots.read(directory.resolve(item.finalFile())).candidates().size() == 10,
                        "final population remains a population");
            }
        }
        String before = EFiles.hash(small.trainingRoot.resolve("manifest.properties"));
        ETraining.run(EConfiguration.parse(controls.toArray(new String[0])));
        require(before.equals(EFiles.hash(small.trainingRoot.resolve("manifest.properties"))),
                "same-scope completed resume performs no writes");
        List<String> changed = new ArrayList<>(controls);
        java.util.Collections.addAll(changed, "--set", "generations=3");
        rejected = false;
        try { ETraining.run(EConfiguration.parse(changed.toArray(new String[0]))); }
        catch (IOException expected) { rejected = true; }
        require(rejected && before.equals(EFiles.hash(small.trainingRoot.resolve("manifest.properties"))),
                "changed scientific controls cannot reuse completed study");
        Path corrupt = fixture.resolve("missing-completed-manifest");
        Files.createDirectory(corrupt);
        Files.copy(small.trainingRoot.resolve("manifest.properties"), corrupt.resolve("manifest.properties"));
        Files.copy(small.trainingRoot.resolve("resolved-study.properties"), corrupt.resolve("resolved-study.properties"));
        Path report = EFiles.mapped(small.trainingRoot, completed.getProperty("report.path"));
        Path copiedReport = corrupt.resolve(report.getFileName());
        Files.createDirectory(copiedReport);
        Files.copy(report.resolve("manifest.properties"), copiedReport.resolve("manifest.properties"));
        Files.copy(report.resolve("training_runs.csv"), copiedReport.resolve("training_runs.csv"));
        List<String> missing = new ArrayList<>(options);
        java.util.Collections.addAll(missing, "--set", "training-output-root=" + corrupt,
                "--set", "scenarios=T085", "--set", "regimes=fixed");
        rejected = false;
        try { ETraining.run(EConfiguration.parse(missing.toArray(new String[0]))); }
        catch (IOException expected) { rejected = expected.getMessage().contains("completed case manifest is missing"); }
        require(rejected, "missing completed manifest must not trigger retraining");
        require(!Files.exists(corrupt.resolve(new EConfiguration.Case(EConfiguration.Method.LEXICASE,
                EConfiguration.Regime.fixed, EConfiguration.Scenario.T085, 0).stem())),
                "corrupt completed data is not replaced by a fresh run");
        System.out.println("E TRAINING CHECKS PASSED: all 6 scenarios / both regimes / all 3 methods, "
                + "360/1080 preflights, matched initial populations, counters, immutable reports and incremental resume");
        System.out.println("END-TO-END INPUT: " + small.trainingRoot);
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
