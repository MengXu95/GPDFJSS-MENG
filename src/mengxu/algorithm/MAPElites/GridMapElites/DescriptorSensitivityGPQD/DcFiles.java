package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.TreeMap;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.UniformFiles;

public final class DcFiles {
    public static final String IO_VERSION = "atomic-manifest-retry-v1";
    private DcFiles() { }
    public static BufferedWriter create(Path path) throws IOException { return UniformFiles.create(path); }
    public static void row(BufferedWriter writer, Object... fields) throws IOException { UniformFiles.row(writer, fields); }
    public static Properties read(Path path) throws IOException {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { values.load(reader); }
        return values;
    }
    public static void write(Path path, Properties values, boolean replace) throws IOException {
        DcAtomicProperties.write(path, DcIoCompatibility.annotate(path, values), replace);
    }
    public static String hash(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); }
        }
        return hex(digest.digest());
    }
    public static void checkHash(Path path, String expected) throws IOException {
        if (expected == null || !expected.equals(hash(path))) { throw new IOException("Missing or changed artifact: " + path); }
    }
    public static String fingerprint(Properties properties, Class<?>... types) throws IOException {
        DcIoCompatibility.Proof proof = DcIoCompatibility.forFingerprint(properties, types);
        MessageDigest digest = digest();
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) { sorted.put(key, properties.getProperty(key)); }
        sorted.put("java-runtime", System.getProperty("java.runtime.version"));
        for (java.util.Map.Entry<String, String> entry : sorted.entrySet()) {
            digest.update((entry.getKey() + "=" + entry.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        java.util.Set<Class<?>> classes = new java.util.LinkedHashSet<>();
        for (Class<?> type : types) { addClasses(type, classes); }
        for (Class<?> type : classes) {
            if (proof != null && type == DcFiles.class) {
                digest.update(type.getName().getBytes(StandardCharsets.UTF_8));
                digest.update(proof.originalIo);
                continue;
            }
            String name = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
            try (InputStream input = type.getResourceAsStream(name)) {
                if (input == null) { throw new IOException("Missing fingerprint resource: " + type); }
                digest.update(type.getName().getBytes(StandardCharsets.UTF_8));
                digest.update(input.readAllBytes());
            }
        }
        String result = hex(digest.digest());
        if (proof != null) {
            proof.verify(result);
            System.err.println("VERIFIED I/O-only compatibility with original training fingerprint " + result);
        }
        return result;
    }
    private static void addClasses(Class<?> type, java.util.Set<Class<?>> output) {
        if (!output.add(type)) { return; }
        Class<?>[] nested = type.getDeclaredClasses();
        java.util.Arrays.sort(nested, java.util.Comparator.comparing(Class::getName));
        for (Class<?> item : nested) { addClasses(item, output); }
    }
    public static Path mapped(Path root, String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_-]+")) { throw new IOException("Invalid output mapping"); }
        Path result = root.resolve(name);
        if (Files.exists(result) && !result.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("Output mapping escapes root");
        }
        return result;
    }
    public static void verifyArtifacts(Path directory, Properties manifest) throws IOException {
        for (String key : manifest.stringPropertyNames()) {
            if (key.startsWith("data.") && key.endsWith(".sha256")) {
                String name = key.substring(5, key.length() - 7);
                if (!name.matches("[A-Za-z0-9_.-]+")) { throw new IOException("Invalid artifact name"); }
                checkHash(directory.resolve(name), manifest.getProperty(key));
            }
        }
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) { text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
        return text.toString();
    }
}
