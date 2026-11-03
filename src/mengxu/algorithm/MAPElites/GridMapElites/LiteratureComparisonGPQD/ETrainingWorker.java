package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ETrainingWorker {
    private ETrainingWorker() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[0].equals("--request")) { throw new IllegalArgumentException("Internal worker: --request FILE"); }
        try (EWorkerLease lease = EWorkerLease.start()) {
            Properties request = EFiles.read(Path.of(args[1]));
            if (!"e-parallel-training-job-v1".equals(request.getProperty("protocol"))) {
                throw new IOException("Unrecognised training worker request");
            }
            EConfiguration config = EConfiguration.parse(new String[]{"--config", EFiles.required(request, "configuration")});
            String key = EFiles.required(request, "case-key");
            EConfiguration.Case item = null;
            for (EConfiguration.Case candidate : config.requestedCases()) {
                if (candidate.key().equals(key)) { item = candidate; break; }
            }
            if (item == null) { throw new IOException("Worker case is outside the requested configuration"); }
            String family = ETraining.fingerprint(config);
            if (!family.equals(request.getProperty("family-hash"))
                    || !ETraining.caseFingerprint(config, item, family).equals(request.getProperty("case-hash"))) {
                throw new IOException("Worker scientific identity changed after dispatch");
            }
            Path attempt = Path.of(EFiles.required(request, "attempt-directory")).toRealPath();
            if (!attempt.startsWith(config.trainingRoot.toRealPath())
                    || Files.exists(attempt.resolve("manifest.properties"))) {
                throw new IOException("Worker attempt is outside the owned root or was already started");
            }
            Properties root = EFiles.read(config.trainingRoot.resolve("manifest.properties"));
            if (!family.equals(root.getProperty("family-hash"))
                    || !attempt.getFileName().toString().equals(root.getProperty(item.key() + ".path"))) {
                throw new IOException("Coordinator no longer owns this worker mapping");
            }
            ETraining.execute(config, item, family, attempt);
            Properties saved = EFiles.read(attempt.resolve("manifest.properties"));
            ETraining.validateCase(config, item, family, saved);
            EFiles.verifyArtifacts(attempt, saved);
            System.out.println("TRAINING WORKER COMPLETE: " + item.key() + ", PID=" + ProcessHandle.current().pid());
        }
    }
}
