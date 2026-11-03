package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import yimei.jss.jobshop.Objective;

public final class EConfiguration {
    public static final String PROTOCOL = "literature-comparison-all-scenes-v1";
    public enum Method { LEXICASE, GP, QDGP }
    public enum Regime { fixed, rotating }
    public enum Scenario {
        T085("mean-tardiness-u085", Objective.MEAN_TARDINESS, .85),
        T095("mean-tardiness-u095", Objective.MEAN_TARDINESS, .95),
        F085("max-flowtime-u085", Objective.MAX_FLOWTIME, .85),
        F095("max-flowtime-u095", Objective.MAX_FLOWTIME, .95),
        W085("mean-weighted-flowtime-u085", Objective.MEAN_WEIGHTED_FLOWTIME, .85),
        W095("mean-weighted-flowtime-u095", Objective.MEAN_WEIGHTED_FLOWTIME, .95);
        public final String id;
        public final Objective objective;
        public final double utilisation;
        Scenario(String id, Objective objective, double utilisation) {
            this.id = id; this.objective = objective; this.utilisation = utilisation;
        }
        public static Scenario parse(String value) {
            for (Scenario scenario : values()) {
                if (scenario.id.equals(value) || scenario.name().equals(value)) { return scenario; }
            }
            throw new IllegalArgumentException("Unknown scenario: " + value);
        }
    }
    public static final class Case {
        public final Method method;
        public final Regime regime;
        public final Scenario scenario;
        public final int run;
        public Case(Method method, Regime regime, Scenario scenario, int run) {
            if (run < 0) { throw new IllegalArgumentException("Negative run ID"); }
            this.method = method; this.regime = regime; this.scenario = scenario; this.run = run;
        }
        public String key() { return method.name() + "." + regime.name() + "." + scenario.id + ".run." + run; }
        public String stem() { return method.name().toLowerCase(java.util.Locale.ROOT) + "-" + regime.name()
                + "-" + scenario.id + "-run-" + String.format(java.util.Locale.ROOT, "%03d", run); }
        public String label() {
            String label = method == Method.LEXICASE ? "LexicaseGP" : method.name();
            return label + (regime == Regime.fixed ? "f" : "");
        }
        public String kind() { return method == Method.QDGP ? "GRID_ARCHIVE" : "POPULATION"; }
        public String finalFile() { return method == Method.QDGP ? "final_archive.stat" : "final_population.stat"; }
    }

    public final Path sourcePath, populationParams, archiveParams, trainingRoot, testRoot;
    public final List<Method> methods;
    public final List<Regime> regimes;
    public final List<Scenario> scenarios;
    public final int runs, population, generations, elitism, cases, jobs, warmup, machines;
    public final int descriptorJobs, descriptorWarmup, snapshotInterval, validationCandidates, validationInstances, testInstances;
    public final double dueFactor;
    public final long seedStep, gpSeedBase, trainingSeedBase, descriptorSeedBase, samplingSeedBase;
    public final long commonSeedBase, commonSamplingSeedBase, validationSeedBase, testSeedBase, subsetSeedBase, scenarioSeedStride;
    public final boolean resume, dryRun;
    private final Properties options;
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList("population-params", "archive-params",
            "training-output-root", "test-output-root", "methods", "regimes", "scenarios", "runs",
            "population", "generations", "elitism", "cases", "recorded-jobs", "warmup-jobs", "machines",
            "due-factor", "descriptor-jobs", "descriptor-warmup", "snapshot-interval", "validation-candidates",
            "validation-instances", "test-instances", "seed-step", "gp-seed-base", "training-seed-base",
            "descriptor-seed-base", "sampling-seed-base", "common-seed-base", "common-sampling-seed-base",
            "validation-seed-base", "test-seed-base", "subset-seed-base", "scenario-seed-stride"));
    private static final List<String> SELECTION = Arrays.asList("methods", "regimes", "scenarios");
    private static final List<String> TEST_ONLY = Arrays.asList("validation-candidates", "validation-instances",
            "test-instances", "common-seed-base", "common-sampling-seed-base", "validation-seed-base",
            "test-seed-base", "subset-seed-base");

    private EConfiguration(Path path, Properties source, boolean resume, boolean dryRun) throws IOException {
        sourcePath = path; options = new Properties(); options.putAll(source);
        this.resume = resume; this.dryRun = dryRun;
        for (String key : source.stringPropertyNames()) {
            if (!KEYS.contains(key)) { throw new IllegalArgumentException("Unknown study option: " + key); }
        }
        populationParams = resolve(path.getParent(), text("population-params")).toRealPath();
        archiveParams = resolve(path.getParent(), text("archive-params")).toRealPath();
        trainingRoot = resolve(path.getParent(), text("training-output-root")).toAbsolutePath().normalize();
        testRoot = resolve(path.getParent(), text("test-output-root")).toAbsolutePath().normalize();
        if (trainingRoot.startsWith(testRoot) || testRoot.startsWith(trainingRoot)) {
            throw new IllegalArgumentException("Training/test roots must be distinct non-nested directories");
        }
        runs = integer("runs", 1, 1000);
        population = integer("population", 2, 100000);
        generations = integer("generations", 1, 100000);
        elitism = integer("elitism", 0, population - 1);
        cases = integer("cases", 2, 10000);
        jobs = integer("recorded-jobs", cases, 1000000);
        warmup = integer("warmup-jobs", 0, 1000000);
        machines = integer("machines", 3, 1000);
        descriptorJobs = integer("descriptor-jobs", 1, 1000000);
        descriptorWarmup = integer("descriptor-warmup", 0, 1000000);
        snapshotInterval = integer("snapshot-interval", 1, 100000);
        validationCandidates = integer("validation-candidates", 1, 1000000);
        validationInstances = integer("validation-instances", 1, 10000);
        testInstances = integer("test-instances", 1, 10000);
        dueFactor = Double.parseDouble(text("due-factor"));
        if (!Double.isFinite(dueFactor) || dueFactor <= 0 || jobs % cases != 0
                || Math.multiplyExact((long) population, generations) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid case partition, due factor or inherited evaluation-counter capacity");
        }
        seedStep = seed("seed-step");
        if (seedStep != 10000) { throw new IllegalArgumentException("The matched source QDGP rotation requires seed-step=10000"); }
        gpSeedBase = seed("gp-seed-base"); trainingSeedBase = seed("training-seed-base");
        descriptorSeedBase = seed("descriptor-seed-base"); samplingSeedBase = seed("sampling-seed-base");
        commonSeedBase = seed("common-seed-base"); commonSamplingSeedBase = seed("common-sampling-seed-base");
        validationSeedBase = seed("validation-seed-base"); testSeedBase = seed("test-seed-base");
        subsetSeedBase = seed("subset-seed-base"); scenarioSeedStride = seed("scenario-seed-stride");
        if (scenarioSeedStride <= (generations - 1L) * seedStep + runs
                || scenarioSeedStride <= Math.max(validationInstances, testInstances)
                || Math.addExact(gpSeedBase, 5000L + runs - 1) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Scenario seed blocks overlap or GP seed exceeds ECJ integer range");
        }
        methods = parseList(text("methods"), value -> Method.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        regimes = parseList(text("regimes"), Regime::valueOf);
        scenarios = parseList(text("scenarios"), Scenario::parse);
        long lastScenario = Math.multiplyExact(5L, scenarioSeedStride);
        Math.addExact(samplingSeedBase, Math.addExact(lastScenario, runs - 1L));
        Math.addExact(commonSamplingSeedBase, lastScenario);
        Math.addExact(subsetSeedBase, Math.addExact(lastScenario, runs - 1L));
        long[][] ranges = {
            range(trainingSeedBase, Math.addExact(lastScenario, (generations - 1L) * seedStep + runs - 1L)),
            range(descriptorSeedBase, Math.addExact(lastScenario, runs - 1L)),
            range(commonSeedBase, lastScenario),
            range(validationSeedBase, Math.addExact(lastScenario, validationInstances - 1L)),
            range(testSeedBase, Math.addExact(lastScenario, testInstances - 1L))
        };
        for (int i = 0; i < ranges.length; i++) {
            for (int j = i + 1; j < ranges.length; j++) {
                if (ranges[i][0] <= ranges[j][1] && ranges[j][0] <= ranges[i][1]) {
                    throw new IllegalArgumentException("Training/descriptor/common/validation/test seed families overlap");
                }
            }
        }
    }

    public static EConfiguration parse(String[] args) throws IOException {
        Path config = null; boolean resume = false, dry = false;
        List<String> overrides = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--resume") && !resume) { resume = true; }
            else if (args[i].equals("--dry-run") && !dry) { dry = true; }
            else if (args[i].equals("--config") && config == null && i + 1 < args.length) {
                config = Path.of(args[++i]).toRealPath();
            } else if (args[i].equals("--set") && i + 1 < args.length) { overrides.add(args[++i]); }
            else { throw new IllegalArgumentException("Unknown/duplicate/incomplete option: " + args[i]); }
        }
        if (config == null) { throw new IllegalArgumentException("Use --config <e-study.properties>"); }
        Properties values = EFiles.read(config);
        for (String override : overrides) {
            int split = override.indexOf('=');
            if (split < 1 || split == override.length() - 1) { throw new IllegalArgumentException("Use --set key=value"); }
            values.setProperty(override.substring(0, split), override.substring(split + 1));
        }
        return new EConfiguration(config, values, resume, dry);
    }
    public List<Case> requestedCases() {
        List<Case> result = new ArrayList<>();
        for (Method method : methods) for (Regime regime : regimes) for (Scenario scenario : scenarios) {
            for (int run = 0; run < runs; run++) { result.add(new Case(method, regime, scenario, run)); }
        }
        return result;
    }
    public long nominalEvaluations() { return (long) population * generations; }
    public long gpSeed(Case item) { return gpSeedBase + item.scenario.ordinal() * 1000L + item.run; }
    public long trainingSeed(Case item) { return trainingSeedBase + item.scenario.ordinal() * scenarioSeedStride + item.run; }
    public long descriptorSeed(Case item) { return descriptorSeedBase + item.scenario.ordinal() * scenarioSeedStride + item.run; }
    public long samplingSeed(Case item) { return samplingSeedBase + item.scenario.ordinal() * scenarioSeedStride + item.run; }
    public long commonSeed(Scenario scene) { return commonSeedBase + scene.ordinal() * scenarioSeedStride; }
    public long commonSamplingSeed(Scenario scene) { return commonSamplingSeedBase + scene.ordinal() * scenarioSeedStride; }
    public long validationSeed(Scenario scene) { return validationSeedBase + scene.ordinal() * scenarioSeedStride; }
    public long testSeed(Scenario scene) { return testSeedBase + scene.ordinal() * scenarioSeedStride; }
    public long subsetSeed(Case item) { return subsetSeedBase + item.scenario.ordinal() * scenarioSeedStride + item.run; }
    public Properties properties() {
        Properties result = new Properties(); result.putAll(options);
        result.setProperty("population-params", populationParams.toString());
        result.setProperty("archive-params", archiveParams.toString());
        result.setProperty("training-output-root", trainingRoot.toString());
        result.setProperty("test-output-root", testRoot.toString());
        return result;
    }
    public Properties trainingProperties() {
        Properties result = properties();
        result.remove("training-output-root"); result.remove("test-output-root");
        for (String key : SELECTION) { result.remove(key); }
        for (String key : TEST_ONLY) { result.remove(key); }
        result.setProperty("scenario-universe", Arrays.toString(Scenario.values()));
        result.setProperty("method-universe", Arrays.toString(Method.values()));
        result.setProperty("regime-universe", Arrays.toString(Regime.values()));
        return result;
    }
    public Properties evaluationProperties() {
        Properties result = properties();
        result.remove("training-output-root"); result.remove("test-output-root");
        for (String key : SELECTION) { result.remove(key); }
        return result;
    }
    private String text(String key) {
        String value = options.getProperty(key);
        if (value == null || value.trim().isEmpty()) { throw new IllegalArgumentException("Missing option: " + key); }
        return value.trim();
    }
    private int integer(String key, int minimum, int maximum) {
        int value = Integer.parseInt(text(key));
        if (value < minimum || value > maximum) { throw new IllegalArgumentException("Invalid " + key); }
        return value;
    }
    private long seed(String key) {
        long value = Long.parseLong(text(key));
        if (value < 0) { throw new IllegalArgumentException("Negative seed/stride: " + key); }
        return value;
    }
    private static long[] range(long first, long width) { return new long[]{first, Math.addExact(first, width)}; }
    private static Path resolve(Path base, String value) {
        Path path = Path.of(value); return path.isAbsolute() ? path : base.resolve(path).normalize();
    }
    private static <T> List<T> parseList(String text, java.util.function.Function<String, T> parser) {
        List<T> result = new ArrayList<>();
        for (String value : text.split(",", -1)) {
            T item = parser.apply(value.trim());
            if (result.contains(item)) { throw new IllegalArgumentException("Duplicate selection: " + value); }
            result.add(item);
        }
        if (result.isEmpty()) { throw new IllegalArgumentException("Empty selection"); }
        return Collections.unmodifiableList(result);
    }
}
