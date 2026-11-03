package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class DcConfiguration {
    public static final String PROTOCOL = "qdgpf-dc-fixed-v1";
    public static final class Setting {
        public final int d;
        public final int c;
        public Setting(int d, int c) {
            if (d < 2 || d > 5 || c < 2 || c > 4) {
                throw new IllegalArgumentException("Require d=2..5 and c=2..4");
            }
            this.d = d; this.c = c;
        }
        public String id() { return "d" + d + "-c" + c; }
        public int side() { return DcDescriptorBank.side(d, c); }
        public int cells() { return Math.multiplyExact(side(), side()); }
    }

    public final Path sourcePath, paramsPath, trainingRoot, testRoot;
    public final List<Setting> settings;
    public final int runs, population, generations, elitism, jobs, warmup, machines;
    public final int descriptorJobs, descriptorWarmup, snapshotInterval;
    public final int validationInstances, testInstances, qualitySample;
    public final double utilisation, dueFactor;
    public final long gpSeedBase, trainingSeedBase, descriptorSeedBase, samplingSeedBase;
    public final long commonDescriptorSeed, commonSamplingSeed, validationSeed, testSeed, qualitySamplingSeed;
    public final boolean resume, dryRun;
    private final Properties options;
    private static final Set<String> KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "params", "training-output-root", "test-output-root", "settings", "runs", "population",
            "generations", "elitism", "recorded-jobs", "warmup-jobs", "machines", "utilisation",
            "due-factor", "descriptor-jobs", "descriptor-warmup", "snapshot-interval",
            "validation-instances", "test-instances", "quality-sample", "gp-seed-base",
            "training-seed-base", "descriptor-seed-base", "sampling-seed-base",
            "common-descriptor-seed", "common-sampling-seed", "validation-seed", "test-seed",
            "quality-sampling-seed")));
    private static final Set<String> TEST_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "training-output-root", "test-output-root", "validation-instances", "test-instances",
            "quality-sample", "common-descriptor-seed", "common-sampling-seed", "validation-seed",
            "test-seed", "quality-sampling-seed")));

    private DcConfiguration(Path source, Properties values, boolean resume, boolean dryRun) throws IOException {
        sourcePath = source;
        this.resume = resume; this.dryRun = dryRun;
        options = new Properties(); options.putAll(values);
        for (String key : options.stringPropertyNames()) {
            if (!KEYS.contains(key)) { throw new IllegalArgumentException("Unknown property: " + key); }
        }
        paramsPath = resolve(source.getParent(), required("params")).toRealPath();
        trainingRoot = resolve(source.getParent(), required("training-output-root")).toAbsolutePath().normalize();
        testRoot = resolve(source.getParent(), required("test-output-root")).toAbsolutePath().normalize();
        if (trainingRoot.startsWith(testRoot) || testRoot.startsWith(trainingRoot)) {
            throw new IllegalArgumentException("Training and test outputs must be separate, non-nested directories");
        }
        runs = integer("runs", 1, 1000);
        population = integer("population", 2, 100000);
        generations = integer("generations", 1, 100000);
        elitism = integer("elitism", 0, population - 1);
        jobs = integer("recorded-jobs", 1, 1000000);
        warmup = integer("warmup-jobs", 0, 1000000);
        machines = integer("machines", 4, 1000);
        descriptorJobs = integer("descriptor-jobs", 1, 1000000);
        descriptorWarmup = integer("descriptor-warmup", 0, 1000000);
        snapshotInterval = integer("snapshot-interval", 1, 100000);
        validationInstances = integer("validation-instances", 1, 10000);
        testInstances = integer("test-instances", 1, 10000);
        qualitySample = integer("quality-sample", 0, 1000000);
        utilisation = number("utilisation", 0, 1);
        dueFactor = number("due-factor", 0, Double.MAX_VALUE);
        gpSeedBase = seed("gp-seed-base");
        trainingSeedBase = seed("training-seed-base");
        descriptorSeedBase = seed("descriptor-seed-base");
        samplingSeedBase = seed("sampling-seed-base");
        commonDescriptorSeed = seed("common-descriptor-seed");
        commonSamplingSeed = seed("common-sampling-seed");
        validationSeed = seed("validation-seed");
        testSeed = seed("test-seed");
        qualitySamplingSeed = seed("quality-sampling-seed");
        List<Setting> parsed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String pair : required("settings").split(",", -1)) {
            String[] fields = pair.trim().split(":", -1);
            if (fields.length != 2) { throw new IllegalArgumentException("Use settings=d:c,d:c,..."); }
            Setting setting = new Setting(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]));
            if (!ids.add(setting.id())) { throw new IllegalArgumentException("Duplicate setting"); }
            parsed.add(setting);
        }
        if (parsed.isEmpty()) { throw new IllegalArgumentException("No descriptor settings"); }
        settings = Collections.unmodifiableList(parsed);
        List<long[]> ranges = Arrays.asList(range(trainingSeedBase, runs), range(descriptorSeedBase, runs),
                range(commonDescriptorSeed, 1), range(validationSeed, validationInstances), range(testSeed, testInstances));
        for (int i = 0; i < ranges.size(); i++) {
            for (int j = i + 1; j < ranges.size(); j++) {
                if (ranges.get(i)[0] <= ranges.get(j)[1] && ranges.get(j)[0] <= ranges.get(i)[1]) {
                    throw new IllegalArgumentException("Training/descriptor/common/validation/test simulation seeds overlap");
                }
            }
        }
        if (Math.addExact(gpSeedBase, runs - 1L) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("GP seeds must fit ECJ's non-negative integer range");
        }
        Math.addExact(samplingSeedBase, runs - 1L);
        Math.addExact(qualitySamplingSeed, runs - 1L);
        if (Math.multiplyExact((long) population, generations) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("The inherited fitness-call counter requires at most Integer.MAX_VALUE evaluations");
        }
    }

    public static DcConfiguration parse(String[] args) throws IOException {
        Path config = null;
        boolean resume = false, dry = false;
        List<String> overrides = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (key.equals("--resume") && !resume) { resume = true; }
            else if (key.equals("--dry-run") && !dry) { dry = true; }
            else if (key.equals("--config") && config == null && i + 1 < args.length) {
                config = Path.of(args[++i]).toRealPath();
            } else if (key.equals("--set") && i + 1 < args.length) { overrides.add(args[++i]); }
            else { throw new IllegalArgumentException("Unknown, duplicate or incomplete option: " + key); }
        }
        if (config == null) { throw new IllegalArgumentException("Use --config <dc-study.properties>"); }
        Properties values = DcFiles.read(config);
        for (String override : overrides) {
            int equals = override.indexOf('=');
            if (equals < 1 || equals == override.length() - 1) { throw new IllegalArgumentException("Use --set key=value"); }
            values.setProperty(override.substring(0, equals), override.substring(equals + 1));
        }
        return new DcConfiguration(config, values, resume, dry);
    }

    public Properties properties() {
        Properties result = new Properties(); result.putAll(options);
        result.setProperty("params", paramsPath.toString());
        result.setProperty("training-output-root", trainingRoot.toString());
        result.setProperty("test-output-root", testRoot.toString());
        return result;
    }

    public Properties trainingProperties() {
        Properties result = properties();
        for (String key : TEST_KEYS) { result.remove(key); }
        return result;
    }

    public long nominalEvaluations() { return Math.multiplyExact((long) population, generations); }
    public String scenario() { return "mean-tardiness-" + utilisation + "-" + dueFactor; }
    private static Path resolve(Path base, String value) {
        Path path = Path.of(value); return path.isAbsolute() ? path : base.resolve(path).normalize();
    }
    private String required(String key) {
        String value = options.getProperty(key);
        if (value == null || value.trim().isEmpty()) { throw new IllegalArgumentException("Missing property: " + key); }
        return value.trim();
    }
    private int integer(String key, int minimum, int maximum) {
        int value = Integer.parseInt(required(key));
        if (value < minimum || value > maximum) { throw new IllegalArgumentException("Invalid " + key); }
        return value;
    }
    private double number(String key, double minimum, double maximum) {
        double value = Double.parseDouble(required(key));
        if (!Double.isFinite(value) || value <= minimum || value >= maximum) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        return value;
    }
    private long seed(String key) {
        long value = Long.parseLong(required(key));
        if (value < 0) { throw new IllegalArgumentException("Seed must be non-negative: " + key); }
        return value;
    }
    private static long[] range(long first, int count) { return new long[]{first, Math.addExact(first, count - 1L)}; }
}
