package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.FinalArchiveReader;

public final class DcTrainingChecks {
    private DcTrainingChecks() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) { throw new IllegalArgumentException("Provide module directory and NEW fixture root"); }
        Path module = Path.of(args[0]).toRealPath(), fixture = Path.of(args[1]).toAbsolutePath().normalize();
        if (Files.exists(fixture)) { throw new IOException("Fixture root already exists"); }
        Path configPath = module.resolve("dc-study.properties");
        DcConfiguration formal = DcConfiguration.parse(new String[]{"--config", configPath.toString()});
        require(formal.settings.size() == 6 && formal.runs == 5 && formal.generations == 201
                && formal.population == 500 && formal.nominalEvaluations() == 100500, "formal controls");
        int[] sides = {9, 27, 81, 243, 16, 256};
        for (int i = 0; i < sides.length; i++) {
            require(formal.settings.get(i).side() == sides[i], "axis size");
            require(formal.settings.get(i).cells() == sides[i] * sides[i], "cell count");
        }
        boolean rejected = false;
        try { DcConfiguration.parse(new String[]{"--config", configPath.toString(),
                "--set", "test-seed=100000"}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "overlapping held-out seed ranges rejected");
        rejected = false;
        try { DcConfiguration.parse(new String[]{"--config", configPath.toString(),
                "--set", "settings=4:3,4:3"}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "duplicate setting rejected");
        List<String> options = new ArrayList<>();
        java.util.Collections.addAll(options, "--config", configPath.toString(),
                "--set", "training-output-root=" + fixture.resolve("training"),
                "--set", "test-output-root=" + fixture.resolve("test"),
                "--set", "runs=1", "--set", "population=10", "--set", "elitism=2",
                "--set", "generations=2");
        List<String> preflight = new ArrayList<>(options); preflight.add("--dry-run");
        DcTraining.run(DcConfiguration.parse(preflight.toArray(new String[0])));
        require(!Files.exists(fixture), "preflight performs no output creation");
        DcConfiguration small = DcConfiguration.parse(options.toArray(new String[0]));
        DcTraining.run(small);
        Properties study = DcFiles.read(small.trainingRoot.resolve("manifest.properties"));
        require("COMPLETE".equals(study.getProperty("status")), "all six searches complete");
        for (DcConfiguration.Setting setting : small.settings) {
            String key = DcTraining.caseKey(setting, 0);
            Path directory = DcFiles.mapped(small.trainingRoot, study.getProperty(key + ".path"));
            Properties run = DcFiles.read(directory.resolve("manifest.properties"));
            require("20".equals(run.getProperty("fitness-evaluations")), "actual evaluated-batch budget");
            require("2".equals(run.getProperty("manual-reference-simulations")), "manual reference overhead");
            require("2".equals(run.getProperty("descriptor-reference-simulations")), "descriptor overhead");
            require(Integer.parseInt(run.getProperty("grid-cells")) == setting.cells(), "real native grid dimensions");
            FinalArchiveReader.Archive archive = new FinalArchiveReader().read(directory.resolve("job.0.out.stat"));
            require(archive.candidates().size() == Integer.parseInt(run.getProperty("native-occupied-cells")),
                    "capacity-one native archive count");
            DcFiles.verifyArtifacts(directory, run);
        }
        String hash = DcFiles.hash(small.trainingRoot.resolve("manifest.properties"));
        List<String> resume = new ArrayList<>(options); resume.add("--resume");
        DcTraining.run(DcConfiguration.parse(resume.toArray(new String[0])));
        require(hash.equals(DcFiles.hash(small.trainingRoot.resolve("manifest.properties"))),
                "completed resume verifies without mutating results");
        rejected = false;
        try { DcTraining.run(small); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "overwrite rejected");
        List<String> changed = new ArrayList<>(resume);
        java.util.Collections.addAll(changed, "--set", "generations=3");
        rejected = false;
        try { DcTraining.run(DcConfiguration.parse(changed.toArray(new String[0]))); }
        catch (IOException expected) { rejected = true; }
        require(rejected && hash.equals(DcFiles.hash(small.trainingRoot.resolve("manifest.properties"))),
                "changed protocol rejected without modifying completed study");
        System.out.println("DC TRAINING CHECKS PASSED: six true grid sizes, paired seeds/initial populations, "
                + "nested snapshots, 120 fitness calls, budgets, metadata, native exports and resume");
        System.out.println("END-TO-END TEST INPUT: " + small.trainingRoot);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
