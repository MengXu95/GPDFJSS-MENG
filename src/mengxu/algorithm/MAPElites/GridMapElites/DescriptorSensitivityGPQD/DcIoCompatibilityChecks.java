package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public final class DcIoCompatibilityChecks {
    private DcIoCompatibilityChecks() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) { throw new IllegalArgumentException("Provide config, prepared recovery directory, NEW fixture directory"); }
        Path configPath = Path.of(args[0]), recovery = Path.of(args[1]).toRealPath(), fixture = Path.of(args[2]);
        if (Files.exists(fixture)) { throw new IOException("Compatibility fixture must be new"); }
        Files.createDirectories(fixture);
        DcConfiguration config = DcConfiguration.parse(new String[]{"--config", configPath.toString()});
        String trainingBefore = DcFiles.hash(config.trainingRoot.resolve("manifest.properties"));
        String previous = System.getProperty(DcIoCompatibility.PROPERTY);
        Properties certificate = DcFiles.read(recovery.resolve("compatibility.properties"));
        String legacy = certificate.getProperty("legacy-study-hash");
        try {
            System.clearProperty(DcIoCompatibility.PROPERTY);
            require(!legacy.equals(DcTraining.fingerprint(config)), "new I/O bytecode normally changes the fingerprint");
            System.setProperty(DcIoCompatibility.PROPERTY, recovery.resolve("compatibility.properties").toString());
            require(legacy.equals(DcTraining.fingerprint(config)), "exact legacy scientific fingerprint restored");
            expectRejected(configPath, "--set", "generations=" + (config.generations + 1));
            expectRejected(configPath, "--set", "training-seed-base=" + (config.trainingSeedBase + 100));
            DcConfiguration onlyTestChanged = DcConfiguration.parse(new String[]{"--config", configPath.toString(),
                    "--set", "quality-sample=" + (config.qualitySample + 1)});
            require(legacy.equals(DcTraining.fingerprint(onlyTestChanged)), "evaluation-only options do not change training");
            Properties changedTypes = new Properties();
            changedTypes.setProperty("protocol", DcConfiguration.PROTOCOL);
            boolean rejected = false;
            try { DcFiles.fingerprint(changedTypes, DcTraining.class, DcFiles.class, DcIoCompatibilityChecks.class); }
            catch (IOException expected) { rejected = true; }
            require(rejected, "another scientific component list cannot use the certificate");
            Properties otherProtocol = new Properties(); otherProtocol.setProperty("protocol", "post-training-test");
            String withCertificate = DcFiles.fingerprint(otherProtocol, DcFiles.class);
            System.clearProperty(DcIoCompatibility.PROPERTY);
            require(withCertificate.equals(DcFiles.fingerprint(otherProtocol, DcFiles.class)),
                    "bridge never rewrites another protocol's identity");

            Files.copy(recovery.resolve("legacy-DcFiles.class"), fixture.resolve("legacy-DcFiles.class"));
            Properties bad = new Properties(); bad.putAll(certificate);
            bad.setProperty("approved-current-io-fingerprint", "0".repeat(64));
            DcFiles.write(fixture.resolve("bad-current.properties"), bad, false);
            System.setProperty(DcIoCompatibility.PROPERTY, fixture.resolve("bad-current.properties").toString());
            requireRejected(config, "changed I/O implementation rejected until explicit re-approval");

            DcFiles.write(fixture.resolve("bad-legacy.properties"), certificate, false);
            Files.write(fixture.resolve("legacy-DcFiles.class"), new byte[]{1}, StandardOpenOption.APPEND);
            System.setProperty(DcIoCompatibility.PROPERTY, fixture.resolve("bad-legacy.properties").toString());
            requireRejected(config, "tampered original I/O bytecode rejected");
            require(trainingBefore.equals(DcFiles.hash(config.trainingRoot.resolve("manifest.properties"))),
                    "compatibility checks never rewrite real training results");
            System.out.println("DC I/O COMPATIBILITY CHECKS PASSED: exact legacy identity, changed controls/components rejected,"
                    + " test-only identity isolated, tamper detection and read-only results");
        } finally {
            if (previous == null) { System.clearProperty(DcIoCompatibility.PROPERTY); }
            else { System.setProperty(DcIoCompatibility.PROPERTY, previous); }
        }
    }

    private static void expectRejected(Path config, String... extra) throws IOException {
        String[] args = new String[extra.length + 2];
        args[0] = "--config"; args[1] = config.toString();
        System.arraycopy(extra, 0, args, 2, extra.length);
        requireRejected(DcConfiguration.parse(args), "scientific control change rejected");
    }

    private static void requireRejected(DcConfiguration config, String message) throws IOException {
        boolean rejected = false;
        try { DcTraining.fingerprint(config); }
        catch (IOException expected) { rejected = true; }
        require(rejected, message);
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
