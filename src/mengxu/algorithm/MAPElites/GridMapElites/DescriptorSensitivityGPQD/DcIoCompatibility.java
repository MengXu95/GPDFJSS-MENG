package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/** Explicit, pinned I/O-only bridge; every other legacy fingerprint byte must still match. */
public final class DcIoCompatibility {
    public static final String PROPERTY = "dc.io.compatibility";
    static final String CERTIFICATE_PROTOCOL = "qdgpf-dc-io-certificate-v1";
    private DcIoCompatibility() { }

    static final class Proof {
        final byte[] originalIo;
        final String legacyStudyHash;
        final Path trainingRoot;
        final String certificateHash;
        Proof(byte[] originalIo, String legacyStudyHash, Path trainingRoot, String certificateHash) {
            this.originalIo = originalIo; this.legacyStudyHash = legacyStudyHash;
            this.trainingRoot = trainingRoot; this.certificateHash = certificateHash;
        }
        void verify(String actual) throws IOException {
            if (!legacyStudyHash.equals(actual)) {
                throw new IOException("I/O-only recovery rejected: training controls, runtime, or a non-I/O "
                        + "implementation component changed. Expected " + legacyStudyHash + ", got " + actual
                        + ". Do not edit the existing study hash or discard completed runs.");
            }
        }
    }

    static Proof forFingerprint(Properties properties, Class<?>[] types) throws IOException {
        String path = System.getProperty(PROPERTY);
        if (path == null || path.isEmpty()
                || !DcConfiguration.PROTOCOL.equals(properties.getProperty("protocol"))
                || !Arrays.asList(types).contains(DcTraining.class)) { return null; }
        return load(Path.of(path));
    }

    static Proof load(Path requested) throws IOException {
        Path certificate = requested.toRealPath();
        Properties values = DcFiles.read(certificate);
        if (!CERTIFICATE_PROTOCOL.equals(values.getProperty("protocol"))
                || !System.getProperty("java.runtime.version").equals(values.getProperty("java-runtime"))) {
            throw new IOException("Wrong recovery certificate protocol/runtime: " + certificate);
        }
        if (!ioFingerprint().equals(values.getProperty("approved-current-io-fingerprint"))) {
            throw new IOException("The I/O fix was rebuilt or changed. Run 'QDGPf d-c - 05 Prepare IO Recovery' "
                    + "with the same IDEA/JDK build before resuming; scientific compatibility will be rechecked.");
        }
        String originalName = values.getProperty("legacy-io-file");
        if (!"legacy-DcFiles.class".equals(originalName)) { throw new IOException("Invalid legacy I/O resource"); }
        Path original = certificate.getParent().resolve(originalName).toRealPath();
        if (!original.startsWith(certificate.getParent().toRealPath())) {
            throw new IOException("Legacy I/O resource escapes the recovery directory");
        }
        DcFiles.checkHash(original, values.getProperty("legacy-io-sha256"));
        String study = required(values, "legacy-study-hash");
        if (!study.matches("[0-9a-f]{64}")) { throw new IOException("Invalid legacy study hash"); }
        return new Proof(Files.readAllBytes(original), study,
                Path.of(required(values, "training-root")).toRealPath(), DcFiles.hash(certificate));
    }

    static Properties annotate(Path target, Properties source) throws IOException {
        if (!source.containsKey("study-hash")) { return source; }
        Properties result = new Properties(); result.putAll(source);
        result.setProperty("io-implementation-fingerprint", ioFingerprint());
        result.setProperty("io-version", "atomic-manifest-retry-v1");
        String path = System.getProperty(PROPERTY);
        if (path != null && !path.isEmpty()) {
            Proof proof = load(Path.of(path));
            if (proof.legacyStudyHash.equals(source.getProperty("study-hash"))
                    && target.toAbsolutePath().normalize().startsWith(proof.trainingRoot)) {
                result.setProperty("io-compatibility-mode", "verified-legacy-io-only");
                result.setProperty("io-compatibility-certificate-sha256", proof.certificateHash);
            }
        }
        return result;
    }

    static String ioFingerprint() throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Set<Class<?>> classes = new LinkedHashSet<>();
            add(DcFiles.class, classes);
            add(DcIoCompatibility.class, classes);
            add(Class.forName(DcIoCompatibility.class.getPackage().getName() + ".DcAtomicProperties"), classes);
            for (Class<?> type : classes) {
                digest.update(type.getName().getBytes(StandardCharsets.UTF_8));
                digest.update(classBytes(type));
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            }
            return result.toString();
        } catch (ClassNotFoundException failure) {
            throw new IOException("Compile the atomic I/O hotfix before preparing recovery", failure);
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    static byte[] classBytes(Class<?> type) throws IOException {
        String name = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
        try (InputStream input = type.getResourceAsStream(name)) {
            if (input == null) { throw new IOException("Missing implementation resource: " + type.getName()); }
            return input.readAllBytes();
        }
    }

    private static void add(Class<?> type, Set<Class<?>> classes) {
        if (!classes.add(type)) { return; }
        Class<?>[] nested = type.getDeclaredClasses();
        Arrays.sort(nested, java.util.Comparator.comparing(Class::getName));
        for (Class<?> item : nested) { add(item, classes); }
    }

    static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) { throw new IOException("Missing recovery metadata: " + key); }
        return value;
    }
}
