package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class EParallelTrainingChecks {
    private EParallelTrainingChecks() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) { throw new IllegalArgumentException("Provide module directory and NEW fixture root"); }
        Path module = Path.of(args[0]).toRealPath(), fixture = Path.of(args[1]).toAbsolutePath().normalize();
        if (Files.exists(fixture)) { throw new IOException("Fixture root already exists"); }
        List<String> base = new ArrayList<>();
        java.util.Collections.addAll(base, "--config", module.resolve("e-study.properties").toString(),
                "--resume", "--set", "scenarios=T085", "--set", "methods=LEXICASE",
                "--set", "runs=5", "--set", "population=10", "--set", "elitism=2",
                "--set", "generations=2");
        List<String> sequential = withRoots(base, fixture.resolve("sequential-training"), fixture.resolve("sequential-test"));
        ETraining.run(EConfiguration.parse(sequential.toArray(new String[0])));
        List<String> mixed = withRoots(base, fixture.resolve("mixed-training"), fixture.resolve("mixed-test"));
        List<String> fixed = new ArrayList<>(mixed);
        java.util.Collections.addAll(fixed, "--set", "regimes=fixed");
        ETraining.run(EConfiguration.parse(fixed.toArray(new String[0])));
        Path mixedRoot = fixture.resolve("mixed-training");
        Properties fixedManifest = EFiles.read(mixedRoot.resolve("manifest.properties"));
        Properties fixedHashes = new Properties();
        for (String key : fixedManifest.stringPropertyNames()) {
            if (key.startsWith("LEXICASE.fixed.") && key.endsWith(".manifest-sha256")) {
                fixedHashes.setProperty(key, fixedManifest.getProperty(key));
            }
        }
        List<String> parallel = new ArrayList<>(mixed);
        java.util.Collections.addAll(parallel, "--workers", "5", "--worker-heap", "2g");
        ETraining.runParallel(EParallelOptions.parse(parallel.toArray(new String[0])));
        Properties done = EFiles.read(mixedRoot.resolve("manifest.properties"));
        require("COMPLETE".equals(done.getProperty("status")), "parallel requested scope completed");
        require("5".equals(done.getProperty("parallel-max-active")), "five live worker processes observed");
        require("10".equals(done.getProperty("completed-case-count")), "five fixed cached and five rotating worker seeds");
        for (String key : fixedHashes.stringPropertyNames()) {
            require(fixedHashes.getProperty(key).equals(done.getProperty(key)), "completed sequential seed preserved");
        }
        EConfiguration config = EConfiguration.parse(sequential.toArray(new String[0]));
        Properties baseline = EFiles.read(config.trainingRoot.resolve("manifest.properties"));
        require(baseline.getProperty("family-hash").equals(done.getProperty("family-hash")),
                "scheduler and output location do not change scientific family");
        for (EConfiguration.Case item : config.requestedCases()) {
            Path a = EFiles.mapped(config.trainingRoot, baseline.getProperty(item.key() + ".path"));
            Path b = EFiles.mapped(mixedRoot, done.getProperty(item.key() + ".path"));
            for (String artifact : new String[]{item.finalFile(), "final_population.csv", "initial_population.stat"}) {
                require(EFiles.hash(a.resolve(artifact)).equals(EFiles.hash(b.resolve(artifact))),
                        "sequential/process-isolated same-seed artifact differs: " + item.key() + "/" + artifact);
            }
            EFiles.verifyArtifacts(b, EFiles.read(b.resolve("manifest.properties")));
        }
        String before = EFiles.hash(mixedRoot.resolve("manifest.properties"));
        ETraining.runParallel(EParallelOptions.parse(parallel.toArray(new String[0])));
        require(before.equals(EFiles.hash(mixedRoot.resolve("manifest.properties"))),
                "fully completed parallel resume performs no root writes or worker launch");
        ETraining.run(EConfiguration.parse(mixed.toArray(new String[0])));
        require(before.equals(EFiles.hash(mixedRoot.resolve("manifest.properties"))),
                "v1 sequential launcher skips v2-created complete seeds without any root writes");
        for (String key : fixedHashes.stringPropertyNames()) {
            Properties resumed = EFiles.read(mixedRoot.resolve("manifest.properties"));
            require(fixedHashes.getProperty(key).equals(resumed.getProperty(key)),
                    "v1/v2 mixed-source cache keeps original completed manifest hashes");
        }
        System.out.println("PARALLEL TRAINING CHECKS PASSED: five observed processes, sequential-to-parallel cached seeds,"
                + " bit-identical native outcomes, v2-to-v1 cached seeds and zero-write completed resume");
        System.out.println("TEST PARITY TRAINING INPUT: " + mixedRoot);
    }

    private static List<String> withRoots(List<String> base, Path training, Path test) {
        List<String> result = new ArrayList<>(base);
        java.util.Collections.addAll(result, "--set", "training-output-root=" + training,
                "--set", "test-output-root=" + test);
        return result;
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
