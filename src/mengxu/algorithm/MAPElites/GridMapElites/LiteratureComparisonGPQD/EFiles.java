package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD.DcAtomicProperties;
import mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD.DcFiles;
import mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.UniformFiles;

public final class EFiles {
    private EFiles() { }
    public static BufferedWriter create(Path path) throws IOException { return UniformFiles.create(path); }
    public static void row(BufferedWriter writer, Object... fields) throws IOException { UniformFiles.row(writer, fields); }
    public static Properties read(Path path) throws IOException {
        Properties result = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }
    public static void write(Path path, Properties values, boolean replace) throws IOException {
        Properties copy = new Properties(); copy.putAll(values);
        if (copy.containsKey("status")) { copy.setProperty("io-version", DcAtomicProperties.VERSION); }
        DcAtomicProperties.write(path, copy, replace);
    }
    public static String hash(Path path) throws IOException { return DcFiles.hash(path); }
    public static void checkHash(Path path, String expected) throws IOException {
        if (expected == null || !expected.equals(hash(path))) { throw new IOException("Missing or changed artifact: " + path); }
    }
    public static String required(Properties values, String key) throws IOException {
        String value = values.getProperty(key);
        if (value == null || value.isEmpty()) { throw new IOException("Missing metadata: " + key); }
        return value;
    }
    public static Path mapped(Path root, String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_.-]+") || name.equals(".") || name.equals("..")) {
            throw new IOException("Invalid output mapping");
        }
        Path path = root.resolve(name);
        if (Files.exists(path) && !path.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("Output mapping escapes root");
        }
        return path;
    }
    public static void verifyArtifacts(Path directory, Properties manifest) throws IOException {
        for (String key : manifest.stringPropertyNames()) {
            if (key.startsWith("data.") && key.endsWith(".sha256")) {
                String name = key.substring(5, key.length() - 7);
                if (!name.matches("[A-Za-z0-9_.-]+") || name.equals(".") || name.equals("..")) {
                    throw new IOException("Invalid artifact name");
                }
                checkHash(directory.resolve(name), manifest.getProperty(key));
            }
        }
    }
    public static String fingerprint(Properties values, Class<?>... components) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String key : values.stringPropertyNames()) { sorted.put(key, values.getProperty(key)); }
        sorted.put("java-runtime", System.getProperty("java.runtime.version"));
        for (java.util.Map.Entry<String, String> entry : sorted.entrySet()) {
            digest.update((entry.getKey() + "=" + entry.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (Class<?> type : components) { add(type, classes); }
        for (Class<?> type : classes) {
            String resource = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
            try (InputStream input = type.getResourceAsStream(resource)) {
                if (input == null) { throw new IOException("Missing scientific implementation resource: " + type); }
                digest.update(type.getName().getBytes(StandardCharsets.UTF_8));
                digest.update(input.readAllBytes());
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) { result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
        return result.toString();
    }
    private static void add(Class<?> type, Set<Class<?>> result) {
        if (!result.add(type)) { return; }
        Class<?>[] nested = type.getDeclaredClasses();
        Arrays.sort(nested, java.util.Comparator.comparing(Class::getName));
        for (Class<?> child : nested) { add(child, result); }
    }
}
