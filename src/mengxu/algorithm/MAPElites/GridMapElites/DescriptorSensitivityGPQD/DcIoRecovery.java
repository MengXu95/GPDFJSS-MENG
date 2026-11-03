package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public final class DcIoRecovery {
    private DcIoRecovery() { }

    static final class Audit {
        int complete, incomplete;
        String incompleteCases = "";
    }

    public static void main(String[] args) throws Exception {
        String mode = null;
        Path configPath = null, recovery = null;
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if ((key.equals("--capture") || key.equals("--prepare") || key.equals("--verify")) && mode == null) {
                mode = key;
            } else if (key.equals("--config") && configPath == null && i + 1 < args.length) {
                configPath = Path.of(args[++i]);
            } else if (key.equals("--recovery-root") && recovery == null && i + 1 < args.length) {
                recovery = Path.of(args[++i]).toAbsolutePath().normalize();
            } else { throw new IllegalArgumentException("Unknown/duplicate/incomplete recovery option: " + key); }
        }
        if (mode == null || configPath == null || recovery == null) {
            throw new IllegalArgumentException("DcIoRecovery --capture|--prepare|--verify --config FILE --recovery-root DIRECTORY");
        }
        DcConfiguration config = DcConfiguration.parse(new String[]{"--config", configPath.toString()});
        if (recovery.startsWith(config.trainingRoot) || config.trainingRoot.startsWith(recovery)
                || recovery.startsWith(config.testRoot) || config.testRoot.startsWith(recovery)) {
            throw new IOException("Recovery records must be separate from both training and test outputs");
        }
        try (FileChannel channel = FileChannel.open(config.trainingRoot.resolve(".training.lock"),
                StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) { throw new IOException("Training is active; recovery never modifies a running study"); }
            if (mode.equals("--capture")) { capture(config, recovery); }
            else if (mode.equals("--prepare")) { prepare(config, recovery); }
            else { verify(config, recovery); }
        }
    }

    private static void capture(DcConfiguration config, Path recovery) throws IOException {
        if (Files.exists(recovery)) { throw new IOException("Capture requires a NEW recovery directory"); }
        String declared = DcFiles.read(config.trainingRoot.resolve("manifest.properties")).getProperty("study-hash");
        String actual = DcTraining.fingerprint(config);
        if (declared == null || !declared.equals(actual)) {
            throw new IOException("The loaded pre-fix IDEA classes do not reproduce the original study hash; capture refused");
        }
        Audit audit = audit(config, declared, null);
        Files.createDirectories(recovery.getParent());
        Files.createDirectory(recovery);
        Files.write(recovery.resolve("legacy-DcFiles.class"), DcIoCompatibility.classBytes(DcFiles.class),
                StandardOpenOption.CREATE_NEW);
        Files.copy(config.trainingRoot.resolve("manifest.properties"), recovery.resolve("training-manifest-before.properties"));
        Files.copy(config.trainingRoot.resolve("resolved-study.properties"), recovery.resolve("resolved-study-before.properties"));
        Properties proof = new Properties();
        proof.setProperty("protocol", "qdgpf-dc-legacy-io-capture-v1");
        proof.setProperty("legacy-study-hash", actual);
        proof.setProperty("legacy-io-file", "legacy-DcFiles.class");
        proof.setProperty("legacy-io-sha256", DcFiles.hash(recovery.resolve("legacy-DcFiles.class")));
        proof.setProperty("training-root", config.trainingRoot.toRealPath().toString());
        proof.setProperty("java-runtime", System.getProperty("java.runtime.version"));
        proof.setProperty("captured-training-manifest-sha256",
                DcFiles.hash(recovery.resolve("training-manifest-before.properties")));
        proof.setProperty("captured-resolved-study-sha256", DcFiles.hash(recovery.resolve("resolved-study-before.properties")));
        proof.setProperty("completed-cases", Integer.toString(audit.complete));
        proof.setProperty("incomplete-cases", Integer.toString(audit.incomplete));
        proof.setProperty("incomplete-case-ids", audit.incompleteCases);
        DcFiles.write(recovery.resolve("capture.properties"), proof, false);
        audit(config, declared, recovery.resolve("capture-audit.csv"));
        System.out.println("LEGACY I/O CAPTURE VERIFIED: " + actual + "; complete=" + audit.complete
                + ", incomplete=" + audit.incomplete + " [" + audit.incompleteCases + "]. No training or result rewrite.");
    }

    private static Properties captureProof(DcConfiguration config, Path recovery) throws IOException {
        Properties proof = DcFiles.read(recovery.resolve("capture.properties"));
        if (!"qdgpf-dc-legacy-io-capture-v1".equals(proof.getProperty("protocol"))
                || !config.trainingRoot.toRealPath().toString().equals(proof.getProperty("training-root"))
                || !System.getProperty("java.runtime.version").equals(proof.getProperty("java-runtime"))) {
            throw new IOException("Recovery capture does not identify this study/runtime");
        }
        DcFiles.checkHash(recovery.resolve("legacy-DcFiles.class"), proof.getProperty("legacy-io-sha256"));
        DcFiles.checkHash(recovery.resolve("training-manifest-before.properties"),
                proof.getProperty("captured-training-manifest-sha256"));
        DcFiles.checkHash(recovery.resolve("resolved-study-before.properties"),
                proof.getProperty("captured-resolved-study-sha256"));
        return proof;
    }

    private static void prepare(DcConfiguration config, Path recovery) throws Exception {
        Properties capture = captureProof(config, recovery);
        Object version;
        try { version = DcFiles.class.getField("IO_VERSION").get(null); }
        catch (ReflectiveOperationException failure) {
            throw new IOException("The fixed DcFiles class is not loaded. Build the I/O hotfix first.", failure);
        }
        if (!"atomic-manifest-retry-v1".equals(version)) { throw new IOException("Unrecognised I/O hotfix version"); }
        String legacy = DcIoCompatibility.required(capture, "legacy-study-hash");
        Properties certificate = new Properties();
        certificate.putAll(capture);
        certificate.setProperty("protocol", DcIoCompatibility.CERTIFICATE_PROTOCOL);
        certificate.setProperty("approved-current-io-fingerprint", DcIoCompatibility.ioFingerprint());
        certificate.setProperty("scope", "replace only DcFiles.class bytes in legacy training digest; every other component/control/runtime must match exactly");
        Path pending = recovery.resolve(".certificate-check-" + java.util.UUID.randomUUID() + ".properties");
        Audit audit;
        try {
            DcFiles.write(pending, certificate, false);
            String previous = System.getProperty(DcIoCompatibility.PROPERTY);
            System.setProperty(DcIoCompatibility.PROPERTY, pending.toString());
            try {
                if (!legacy.equals(DcTraining.fingerprint(config))) {
                    throw new IOException("Non-I/O scientific compatibility check failed");
                }
                audit = audit(config, legacy, null);
            } finally { restore(previous); }
            Path published = recovery.resolve("compatibility.properties");
            if (Files.exists(published)) {
                Properties old = DcFiles.read(published);
                if (certificate.equals(old)) {
                    System.out.println("I/O certificate already matches the loaded build.");
                    verify(config, recovery);
                    return;
                }
                Path history = recovery.resolve("certificate-history");
                Files.createDirectories(history);
                Path archive = history.resolve("compatibility-" + DcFiles.hash(published) + ".properties");
                if (!Files.exists(archive)) { Files.copy(published, archive); }
            }
            DcFiles.write(published, certificate, Files.exists(published));
            System.out.println("I/O-ONLY RECOVERY PREPARED: " + legacy + "; " + audit.complete
                    + " completed runs verified unchanged, " + audit.incomplete + " incomplete [" + audit.incompleteCases
                    + "]. Certificate: " + published + ". No training started.");
        } finally { Files.deleteIfExists(pending); }
    }

    private static void verify(DcConfiguration config, Path recovery) throws IOException {
        Properties capture = captureProof(config, recovery);
        String previous = System.getProperty(DcIoCompatibility.PROPERTY);
        System.setProperty(DcIoCompatibility.PROPERTY, recovery.resolve("compatibility.properties").toString());
        try {
            String expected = DcIoCompatibility.required(capture, "legacy-study-hash");
            if (!expected.equals(DcTraining.fingerprint(config))) { throw new IOException("Recovery fingerprint mismatch"); }
            Audit audit = audit(config, expected, null);
            System.out.println("SAFE RESUME VERIFIED: complete=" + audit.complete + ", incomplete=" + audit.incomplete
                    + " [" + audit.incompleteCases + "]; unchanged controls, runtime and non-I/O implementation."
                    + " No simulations or result writes.");
        } finally { restore(previous); }
    }

    private static void restore(String previous) {
        if (previous == null) { System.clearProperty(DcIoCompatibility.PROPERTY); }
        else { System.setProperty(DcIoCompatibility.PROPERTY, previous); }
    }

    private static Audit audit(DcConfiguration config, String expected, Path csv) throws IOException {
        Path root = config.trainingRoot;
        Properties study = DcFiles.read(root.resolve("manifest.properties"));
        if (!DcConfiguration.PROTOCOL.equals(study.getProperty("protocol"))
                || !expected.equals(study.getProperty("study-hash"))) { throw new IOException("Wrong recovery study"); }
        DcFiles.verifyArtifacts(root, study);
        Audit result = new Audit();
        try (BufferedWriter writer = csv == null ? null : DcFiles.create(csv)) {
            if (writer != null) { DcFiles.row(writer, "case_id", "path", "status", "last_generation", "fitness_evaluations", "manifest_sha256"); }
            for (DcConfiguration.Setting setting : config.settings) {
                for (int run = 0; run < config.runs; run++) {
                    String key = DcTraining.caseKey(setting, run);
                    String name = study.getProperty(key + ".path");
                    if (name == null) {
                        result.incomplete++;
                        result.incompleteCases += (result.incompleteCases.isEmpty() ? "" : ",") + key;
                        if (writer != null) { DcFiles.row(writer, key, "", "NOT_STARTED", "", "", ""); }
                        continue;
                    }
                    Path directory = DcFiles.mapped(root, name);
                    Path saved = directory.resolve("manifest.properties");
                    Properties values = DcFiles.read(saved);
                    if (!expected.equals(values.getProperty("study-hash"))
                            || !Integer.toString(setting.d).equals(values.getProperty("d"))
                            || !Integer.toString(setting.c).equals(values.getProperty("c"))
                            || !Integer.toString(run).equals(values.getProperty("run-id"))) {
                        throw new IOException("Case identity mismatch: " + directory);
                    }
                    if ("COMPLETE".equals(values.getProperty("status"))) {
                        DcFiles.checkHash(saved, study.getProperty(key + ".manifest-sha256"));
                        DcFiles.verifyArtifacts(directory, values);
                        if (Long.parseLong(DcIoCompatibility.required(values, "fitness-evaluations")) != config.nominalEvaluations()
                                || Integer.parseInt(DcIoCompatibility.required(values, "grid-cells")) != setting.cells()) {
                            throw new IOException("Completed case budget or geometry mismatch");
                        }
                        result.complete++;
                    } else {
                        result.incomplete++;
                        result.incompleteCases += (result.incompleteCases.isEmpty() ? "" : ",") + key;
                    }
                    if (writer != null) {
                        DcFiles.row(writer, key, name, values.getProperty("status"),
                                values.getProperty("last-evaluated-generation"), values.getProperty("fitness-evaluations"),
                                DcFiles.hash(saved));
                    }
                }
            }
        }
        return result;
    }
}
