package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** One leased JVM, one case, no canonical-root writes. */
public final class EParallelTestWorker {
    static final String REQUEST_PROTOCOL = "e-parallel-test-request-v1";
    private EParallelTestWorker() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !args[0].equals("--request")) {
            throw new IllegalArgumentException("EParallelTestWorker --request <properties>");
        }
        if (System.getProperty(EWorkerLease.PARENT_PID) == null) {
            throw new IOException("Test workers must be launched by the leased process pool");
        }
        try (EWorkerLease lease = EWorkerLease.start()) { execute(Path.of(args[1])); }
    }

    static void execute(Path requestPath) throws IOException {
        Path requestFile = requestPath.toRealPath();
        Properties request = EFiles.read(requestFile);
        require(request, "protocol", REQUEST_PROTOCOL);
        Path configuration = Path.of(EFiles.required(request, "configuration")).toRealPath();
        EFiles.checkHash(configuration, EFiles.required(request, "configuration-sha256"));
        EConfiguration c = EConfiguration.parse(new String[]{"--config", configuration.toString()});
        Path root = c.testRoot.toRealPath();
        Path execution = Path.of(EFiles.required(request, "execution-dir")).toRealPath();
        directChild(root, execution);
        if (!requestFile.getParent().equals(execution) || !configuration.getParent().equals(execution)) {
            throw new IOException("Worker request/configuration must be execution-local");
        }
        require(request, "coordinator-pid", System.getProperty(EWorkerLease.PARENT_PID));
        require(request, "coordinator-start", System.getProperty(EWorkerLease.PARENT_START));
        EConfiguration.Case item = find(c, EFiles.required(request, "case-key"));
        Path attempt = Path.of(EFiles.required(request, "attempt-dir")).toRealPath();
        directChild(root, attempt);
        if (!attempt.getFileName().toString().startsWith(item.stem() + "-attempt-")) {
            throw new IOException("Attempt does not belong to requested case");
        }
        Path journal = executionFile(execution, EFiles.required(request, "result-journal"));
        Path replay = executionFile(execution, EFiles.required(request, "replay-file"));
        Properties progress = new Properties();
        progress.setProperty("protocol", "e-parallel-test-worker-result-v1");
        progress.setProperty("case-key", item.key());
        progress.setProperty("evaluation-family-hash", EFiles.required(request, "evaluation-family-hash"));
        progress.setProperty("training-family-hash", EFiles.required(request, "training-family-hash"));
        progress.setProperty("attempt-dir", attempt.toString());
        progress.setProperty("worker-pid", Long.toString(ProcessHandle.current().pid()));
        progress.setProperty("worker-start", ProcessHandle.current().info().startInstant()
                .orElseThrow(() -> new IOException("Worker start instant unavailable")).toString());
        progress.setProperty("worker-max-heap-bytes", Long.toString(Runtime.getRuntime().maxMemory()));
        progress.setProperty("replay-count-status", "NOT_STARTED");
        progress.setProperty("replay-reference-simulations", "0");
        progress.setProperty("status", "RUNNING");
        EFiles.write(journal, progress, false);
        try {
            if (Files.exists(attempt.resolve("manifest.properties"))) {
                throw new IOException("Worker refuses an existing case manifest; coordinator must prepare a NEW attempt");
            }
            try (java.nio.file.DirectoryStream<Path> contents = Files.newDirectoryStream(attempt)) {
                if (contents.iterator().hasNext()) { throw new IOException("Worker case attempt is not empty"); }
            }
            ETest.RealEvaluator evaluator = new ETest.RealEvaluator(c);
            ETest.RealProjectionFactory factory = new ETest.RealProjectionFactory();
            Map<String, EConfiguration.Case> target = new LinkedHashMap<>();
            target.put(item.key(), item);
            ETest.Inputs inputs = ETest.Inputs.read(c, target);
            require(request, "training-family-hash", inputs.family);
            require(request, "training-root-manifest-sha256", inputs.rootHash);
            String family = ETest.evaluationFamily(c, inputs.family, evaluator, factory);
            require(request, "evaluation-family-hash", family);
            ETest.Source source = inputs.cases.get(item.key());
            require(request, "source-manifest-sha256", source.manifestHash);
            require(request, "source-case-hash", EFiles.required(source.manifest, "case-hash"));
            Path originalBank = Path.of(EFiles.required(request, "common-bank-dir")).toRealPath();
            directChild(root, originalBank);
            String bankHash = EFiles.required(request, "common-bank-sha256");
            EFiles.checkHash(originalBank.resolve("common_descriptor.csv"), bankHash);
            progress.setProperty("replay-count-status", "UNKNOWN_IF_INTERRUPTED");
            progress.remove("replay-reference-simulations");
            EFiles.write(journal, progress, true);
            ETest.ProjectionService bank = factory.collect(c, item.scenario);
            if (bank.simulationCount() < 0) { throw new IOException("Invalid replay simulation counter"); }
            progress.setProperty("replay-reference-simulations", Long.toString(bank.simulationCount()));
            progress.setProperty("replay-count-status", "RECORDED");
            EFiles.write(journal, progress, true);
            bank.write(replay);
            EFiles.checkHash(replay, bankHash);
            progress.setProperty("replay-file-sha256", bankHash);
            Properties result = ETest.evaluate(attempt, source, c, evaluator, bank, family, bankHash);
            inputs.verify();
            progress.setProperty("case-manifest-sha256", EFiles.hash(attempt.resolve("manifest.properties")));
            progress.setProperty("case-test-executed-count", EFiles.required(result, "test_executed_count"));
            progress.setProperty("case-validation-evaluation-count", EFiles.required(result, "validation_evaluation_count"));
            progress.setProperty("status", "COMPLETE");
            EFiles.write(journal, progress, true);
        } catch (IOException | RuntimeException | Error failure) {
            progress.setProperty("status", "FAILED"); progress.setProperty("failure", failure.toString());
            try { EFiles.write(journal, progress, true); }
            catch (IOException reporting) { failure.addSuppressed(reporting); }
            throw failure;
        }
    }

    static EConfiguration.Case find(EConfiguration c, String key) throws IOException {
        for (EConfiguration.Case item : c.requestedCases()) { if (item.key().equals(key)) { return item; } }
        throw new IOException("Worker case is outside the unmodified configured selector scope: " + key);
    }
    static void require(Properties p, String key, String expected) throws IOException {
        if (expected == null || !expected.equals(EFiles.required(p, key))) { throw new IOException("Worker request/result mismatch: " + key); }
    }
    private static Path executionFile(Path execution, String name) throws IOException {
        Path result = Path.of(name).toAbsolutePath().normalize();
        if (!result.getParent().equals(execution) || !EFiles.mapped(execution, result.getFileName().toString()).equals(result)) {
            throw new IOException("Worker journal/replay path escapes execution directory");
        }
        return result;
    }
    private static void directChild(Path root, Path path) throws IOException {
        if (!path.getParent().equals(root) || !EFiles.mapped(root, path.getFileName().toString()).toRealPath().equals(path)) {
            throw new IOException("Worker path is not a direct child of the prepared test root: " + path);
        }
    }
}
