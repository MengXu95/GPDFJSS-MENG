package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.TreeComplexityCalculator;
import yimei.jss.rule.operation.evolved.GPRule;

/** Post-training attributes of every retained member; never invokes a simulator. */
public final class WholeLibraryStudy {
    public static final String PROTOCOL = "whole-library-attributes-v1";
    static final String HEADER = "method,run_id,candidate_id,tree_hash,stored_fitness,"
            + "mean_validation_objective,complexity,sensitivity,quality_status,sensitivity_status,"
            + "a_status,candidate_pool,metric_source,sensitivity_reason";
    private static final String MEMBER_HEADER = "candidate_id,archive_ordinal,tree_hash,stored_fitness,"
            + "status,candidate_pool,duplicate_of,reason,sequencing,routing";
    private static final String METRIC_HEADER =
            "candidate_id,mean_validation_objective,complexity,sensitivity,status,reason";
    private static final List<String> A_ARTIFACTS = Arrays.asList("profiles.csv", "probes.csv",
            "archive_members.csv", "candidate_metrics.csv", "selections.csv", "test_instances.csv", "run_results.csv");

    private WholeLibraryStudy() { }

    public static void main(String[] args) throws IOException {
        List<Path> inputs = new ArrayList<>();
        Path output = null;
        int expectedRuns = 30;
        boolean resume = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input": inputs.add(Path.of(value(args, ++i))); break;
                case "--output-root":
                    if (output != null) { throw new IllegalArgumentException("Duplicate output root"); }
                    output = Path.of(value(args, ++i)); break;
                case "--expected-runs": expectedRuns = Integer.parseInt(value(args, ++i)); break;
                case "--resume":
                    if (resume) { throw new IllegalArgumentException("Duplicate --resume"); }
                    resume = true; break;
                case "--help":
                    System.out.println("WholeLibraryStudy --input <completed A fixed> --input <completed A rotating>"
                            + " --output-root <new directory> [--resume] [--expected-runs 30]");
                    return;
                default: throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (inputs.size() != 2 || output == null || expectedRuns < 1) {
            throw new IllegalArgumentException("Require exactly two completed A inputs, output root and positive expected-runs");
        }
        run(inputs, output, expectedRuns, resume);
    }

    private static String value(String[] args, int index) {
        if (index >= args.length) { throw new IllegalArgumentException("Missing option value"); }
        return args[index];
    }

    private static final class Run {
        final String method;
        final int id;
        final Path directory;
        final Path archive;
        final String archiveHash;
        Run(String method, int id, Path directory, Path archive, String archiveHash) {
            this.method = method; this.id = id; this.directory = directory;
            this.archive = archive; this.archiveHash = archiveHash;
        }
        String key() { return method + ".run." + id; }
    }

    private static final class Attribute {
        final double complexity;
        final Double sensitivity;
        final String reason;
        Attribute(double complexity, Double sensitivity, String reason) {
            this.complexity = complexity; this.sensitivity = sensitivity; this.reason = reason;
        }
    }

    private static final class Context {
        final Map<Path, String> hashes = new LinkedHashMap<>();
        final List<Run> runs = new ArrayList<>();
        final Map<String, Attribute> cache = new LinkedHashMap<>();
        SensitivityProbeSet probes;
        String probeHash;
        double epsilon;

        void record(Path path, String expected) throws IOException {
            ExperimentAFiles.checkHash(path, expected);
            String previous = hashes.putIfAbsent(path, expected);
            if (previous != null && !previous.equals(expected)) { throw new IOException("Conflicting input hashes"); }
        }
    }

    static void run(List<Path> inputs, Path requestedOutput, int expectedRuns, boolean resume) throws IOException {
        Context context = load(inputs, expectedRuns);
        String fingerprint = fingerprint(context);
        Path output = requestedOutput.toAbsolutePath().normalize();
        for (Path input : inputs) {
            if (output.startsWith(input.toRealPath())) {
                throw new IOException("B output must not be inside an immutable A study");
            }
        }
        Properties manifest;
        if (Files.exists(output)) {
            if (!resume) { throw new IOException("Existing B output requires --resume: " + output); }
            manifest = ExperimentAFiles.read(output.resolve("manifest.properties"));
            if (!fingerprint.equals(manifest.getProperty("study-hash"))) {
                throw new IOException("B input/implementation/runtime fingerprint changed; use a new output root");
            }
        } else {
            Files.createDirectories(output.getParent());
            Files.createDirectory(output);
            manifest = new Properties();
            manifest.setProperty("protocol", PROTOCOL);
            manifest.setProperty("study-hash", fingerprint);
            manifest.setProperty("probes-sha256", context.probeHash);
            manifest.setProperty("epsilon", Double.toString(context.epsilon));
            manifest.setProperty("expected-runs-per-regime", Integer.toString(expectedRuns));
            manifest.setProperty("scheduling-simulation-count", "0");
            manifest.setProperty("quality-scope", "cached validation only; stored failures are not evaluated");
            manifest.setProperty("sensitivity-scope", "frozen A raw-priority probes; undefined arithmetic retained explicitly");
            manifest.setProperty("native-grid-coverage", "not identifiable from historical archive exports");
            manifest.setProperty("status", "INITIALIZED");
            ExperimentAFiles.write(output.resolve("manifest.properties"), manifest);
        }
        try (FileChannel channel = FileChannel.open(output.resolve(".study.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) { throw new IOException("Another B process owns this output"); }
            try {
                if ("COMPLETE".equals(manifest.getProperty("status"))) {
                    verifyCompleted(output, manifest, context);
                    System.out.println("B output already COMPLETE and all hashes verified: " + output);
                    return;
                }
                manifest.setProperty("status", "RUNNING");
                ExperimentAFiles.write(output.resolve("manifest.properties"), manifest);
                for (Run run : context.runs) {
                    String mapped = manifest.getProperty(run.key());
                    if (mapped != null) {
                        Path directory = child(output, mapped);
                        Path saved = directory.resolve("manifest.properties");
                        if (Files.isRegularFile(saved)) {
                            Properties runManifest = ExperimentAFiles.read(saved);
                            if ("COMPLETE".equals(runManifest.getProperty("status"))
                                    && fingerprint.equals(runManifest.getProperty("study-hash"))) {
                                contextCheck(directory, runManifest);
                                System.out.println("B verified completed " + run.key());
                                continue;
                            }
                        }
                    }
                    String name = run.method + "-run-" + run.id;
                    Path directory = output.resolve(name);
                    for (int attempt = 2; Files.exists(directory); attempt++) {
                        directory = output.resolve(name + "-attempt-" + attempt);
                    }
                    Files.createDirectory(directory);
                    manifest.setProperty(run.key(), directory.getFileName().toString());
                    ExperimentAFiles.write(output.resolve("manifest.properties"), manifest);
                    execute(context, run, directory, fingerprint);
                }
                aggregate(output, manifest, context);
                verifyInputs(context);
                manifest.setProperty("status", "COMPLETE");
                ExperimentAFiles.write(output.resolve("manifest.properties"), manifest);
                System.out.println("B COMPLETE: retained=" + manifest.getProperty("retained-members")
                        + ", additional scheduling simulations=0; " + output);
            } catch (IOException | RuntimeException failure) {
                manifest.setProperty("status", "FAILED");
                manifest.setProperty("failure", failure.toString());
                try { ExperimentAFiles.write(output.resolve("manifest.properties"), manifest); }
                catch (IOException reportFailure) { failure.addSuppressed(reportFailure); }
                throw failure;
            }
        }
    }

    private static Context load(List<Path> inputs, int expectedRuns) throws IOException {
        Context context = new Context();
        java.util.Set<String> methods = new java.util.HashSet<>();
        Map<String, String> validationProtocol = new LinkedHashMap<>();
        for (Path requested : inputs) {
            Path root = requested.toRealPath();
            Path manifestPath = root.resolve("manifest.properties");
            Properties study = ExperimentAFiles.read(manifestPath);
            context.record(manifestPath, RevisionCsv.fileHash(manifestPath));
            if (!ExperimentAFiles.complete(study)
                    || !ExperimentAStudy.PROTOCOL.equals(required(study, "protocol"))) {
                throw new IOException("Need completed importance A study: " + root);
            }
            String method = required(study, "method");
            if ((!method.equals("NfixIns") && !method.equals("NrotateInsNoReeva")) || !methods.add(method)
                    || Integer.parseInt(required(study, "num-runs")) != expectedRuns) {
                throw new IOException("Invalid method or run count: " + root);
            }
            for (String key : Arrays.asList("scenario", "machines", "recorded-jobs", "warmup-jobs",
                    "validation-seed", "validation-instances", "profiles", "normalisation")) {
                String actual = required(study, key);
                String previous = validationProtocol.putIfAbsent(key, actual);
                if (previous != null && !previous.equals(actual)) {
                    throw new IOException("A validation protocols differ: " + key);
                }
            }
            context.record(root.resolve("run_results.csv"), required(study, "results-sha256"));
            context.record(root.resolve("profiles.csv"), required(study, "profiles-sha256"));
            Path probes = root.resolve("probes.csv");
            context.record(probes, required(study, "probes-sha256"));
            double epsilon = Double.parseDouble(required(study, "epsilon"));
            if (!Double.isFinite(epsilon) || epsilon <= 0) { throw new IOException("Invalid epsilon"); }
            if (context.probes == null) {
                context.probeHash = required(study, "probes-sha256");
                context.probes = ExperimentAFiles.readProbes(probes);
                context.epsilon = epsilon;
            } else if (!context.probeHash.equals(required(study, "probes-sha256"))
                    || context.epsilon != epsilon) {
                throw new IOException("B requires identical frozen probes and epsilon in both regimes");
            }
            java.util.Set<Integer> ids = new java.util.HashSet<>();
            for (String token : required(study, "run-ids").split(",", -1)) {
                int id = Integer.parseInt(token);
                if (id < 0 || !ids.add(id)) { throw new IOException("Invalid/duplicate run id"); }
                Path directory = child(root, required(study, "run." + id + ".path"));
                Path saved = directory.resolve("manifest.properties");
                Properties run = ExperimentAFiles.read(saved);
                context.record(saved, RevisionCsv.fileHash(saved));
                if (!ExperimentAFiles.complete(run) || !required(run, "study-hash").equals(required(study, "study-hash"))
                        || Integer.parseInt(required(run, "run-id")) != id) {
                    throw new IOException("Incomplete or mismatched A run: " + directory);
                }
                for (String artifact : A_ARTIFACTS) {
                    context.record(directory.resolve(artifact), required(run, "data." + artifact + ".sha256"));
                }
                if (!context.probeHash.equals(required(run, "data.probes.csv.sha256"))) {
                    throw new IOException("Run frozen probes differ");
                }
                Path archive = Path.of(required(study, "input-root")).resolve(method)
                        .resolve("multipletreegp-dynamic").resolve(required(study, "scenario"))
                        .resolve("job." + id + ".out.stat").toRealPath();
                context.record(archive, required(run, "archive-sha256"));
                Run item = new Run(method, id, directory, archive, required(run, "archive-sha256"));
                context.runs.add(item);
                Map<String, List<String>> members = readCsv(directory.resolve("archive_members.csv"), MEMBER_HEADER, 10);
                Map<String, List<String>> metrics = readCsv(directory.resolve("candidate_metrics.csv"), METRIC_HEADER, 6);
                if (!members.keySet().equals(metrics.keySet())) { throw new IOException("A member/metric IDs differ"); }
                for (List<String> member : members.values()) {
                    List<String> metric = metrics.get(member.get(0));
                    if (!member.get(4).equals(metric.get(4))) { throw new IOException("A status mismatch"); }
                    if (!metric.get(3).isEmpty()) {
                        double c = finite(metric.get(2)), s = finite(metric.get(3));
                        cache(context, member.get(2), new Attribute(c, s, ""));
                    }
                }
            }
            if (ids.size() != expectedRuns) { throw new IOException("Incomplete A run IDs"); }
        }
        return context;
    }

    private static void execute(Context context, Run run, Path output, String fingerprint) throws IOException {
        long start = System.nanoTime();
        FinalArchiveReader.Archive archive = new FinalArchiveReader().read(run.archive);
        if (!run.archiveHash.equals(archive.sourceHash())) { throw new IOException("Archive changed"); }
        Map<String, List<String>> members = readCsv(run.directory.resolve("archive_members.csv"), MEMBER_HEADER, 10);
        Map<String, List<String>> metrics = readCsv(run.directory.resolve("candidate_metrics.csv"), METRIC_HEADER, 6);
        if (members.size() != archive.declaredCount()) { throw new IOException("Archive/member count mismatch"); }
        TreeComplexityCalculator calculator = new TreeComplexityCalculator();
        long computed = 0, cached = 0, undefined = 0, unknown = 0, failed = 0, audits = 0, pool = 0;
        try (RevisionCsv csv = new RevisionCsv(output.resolve("members.csv"), (Object[]) HEADER.split(","))) {
            for (ArchiveCandidate candidate : archive.candidates()) {
                List<String> member = members.get(candidate.id()), metric = metrics.get(candidate.id());
                if (member == null || metric == null || candidate.ordinal() != Integer.parseInt(member.get(1))
                        || !candidate.treeHash().equals(member.get(2))
                        || Double.compare(candidate.storedFitness(), Double.parseDouble(member.get(3))) != 0
                        || !candidate.sequencing().equals(member.get(8)) || !candidate.routing().equals(member.get(9))) {
                    throw new IOException("Original rule identity differs from A: " + candidate.id());
                }
                String p = metric.get(1);
                if (p.isEmpty() && !member.get(6).isEmpty()) {
                    List<String> original = metrics.get(member.get(6));
                    if (original == null) { throw new IOException("Missing duplicate source"); }
                    p = original.get(1);
                }
                String quality;
                if (!candidate.hasFiniteStoredFitness()) {
                    if (!p.isEmpty()) { throw new IOException("Stored-failed member unexpectedly has A validation"); }
                    quality = "NOT_EVALUATED_STORED_FAILURE"; unknown++;
                } else if (p.isEmpty()) {
                    throw new IOException("Missing cached validation for stored-finite member");
                } else if (Double.parseDouble(p) == Double.POSITIVE_INFINITY) {
                    quality = "VALIDATION_FAILED"; failed++;
                } else {
                    finite(p); quality = "VALIDATION_FINITE";
                }
                GPRule[] rules = candidate.rules();
                double complexity = calculator.calculateStructuralComplexity(rules[0].getGPTree().child)
                        + calculator.calculateStructuralComplexity(rules[1].getGPTree().child);
                Attribute attribute = context.cache.get(candidate.treeHash());
                String source;
                if (!metric.get(3).isEmpty()) {
                    source = "A_CACHE"; cached++;
                } else if (attribute != null) {
                    source = "B_HASH_CACHE"; cached++;
                } else {
                    attribute = calculate(rules, complexity, context);
                    cache(context, candidate.treeHash(), attribute);
                    source = "B_COMPUTED"; computed++;
                }
                if (attribute == null || complexity != attribute.complexity) {
                    throw new IOException("Complexity differs from cached tree metric");
                }
                // Audit cached values at a deterministic interval, including the first member.
                if (!source.equals("B_COMPUTED") && candidate.ordinal() % 100 == 0) {
                    Attribute actual = calculate(rules, complexity, context);
                    equalAttribute(actual, attribute);
                    audits++;
                }
                if (member.get(4).equals("EXCLUDED_METRIC_FAILURE") && attribute.sensitivity != null) {
                    throw new IOException("A numeric-failure classification disagrees with frozen B evaluator");
                }
                if (attribute.sensitivity == null) { undefined++; }
                boolean inPool;
                if (member.get(5).equals("true")) { inPool = true; pool++; }
                else if (member.get(5).equals("false")) { inPool = false; }
                else { throw new IOException("Invalid candidate_pool flag"); }
                csv.row(run.method, run.id, candidate.id(), candidate.treeHash(), candidate.storedFitness(),
                        p, complexity, attribute.sensitivity, quality,
                        attribute.sensitivity == null ? "UNDEFINED_NUMERIC" : "FINITE",
                        member.get(4), inPool, source, attribute.reason);
            }
        }
        Properties saved = new Properties();
        saved.setProperty("status", "COMPLETE");
        saved.setProperty("study-hash", fingerprint);
        saved.setProperty("members-sha256", RevisionCsv.fileHash(output.resolve("members.csv")));
        saved.setProperty("retained-members", Integer.toString(archive.declaredCount()));
        saved.setProperty("pool-members", Long.toString(pool));
        saved.setProperty("computed-attributes", Long.toString(computed));
        saved.setProperty("reused-attributes", Long.toString(cached));
        saved.setProperty("audited-cached-attributes", Long.toString(audits));
        saved.setProperty("undefined-sensitivity", Long.toString(undefined));
        saved.setProperty("quality-not-evaluated", Long.toString(unknown));
        saved.setProperty("quality-failed", Long.toString(failed));
        saved.setProperty("elapsed-seconds", Double.toString((System.nanoTime() - start) / 1e9));
        ExperimentAFiles.write(output.resolve("manifest.properties"), saved);
        System.out.println("B " + run.key() + ": members=" + archive.declaredCount()
                + ", computed=" + computed + ", reused=" + cached + ", undefined S=" + undefined);
    }

    private static Attribute calculate(GPRule[] rules, double complexity, Context context) {
        try {
            double seq = FrozenPriorityProgram.compile(rules[0].getGPTree().child)
                    .sensitivity(context.probes.sequencing(), context.epsilon);
            double route = FrozenPriorityProgram.compile(rules[1].getGPTree().child)
                    .sensitivity(context.probes.routing(), context.epsilon);
            double sensitivity = (seq + route) / 2.0;
            if (!Double.isFinite(sensitivity) || sensitivity < 0) {
                throw new CandidateMetricException("Non-finite or negative aggregate sensitivity");
            }
            return new Attribute(complexity, sensitivity, "");
        } catch (CandidateMetricException failure) {
            return new Attribute(complexity, null, failure.getMessage());
        }
    }

    private static void cache(Context context, String hash, Attribute attribute) throws IOException {
        Attribute previous = context.cache.putIfAbsent(hash, attribute);
        if (previous != null) { equalAttribute(previous, attribute); }
    }

    private static void equalAttribute(Attribute first, Attribute second) throws IOException {
        if (first.complexity != second.complexity
                || (first.sensitivity == null) != (second.sensitivity == null)
                || (first.sensitivity != null
                && Double.doubleToLongBits(first.sensitivity) != Double.doubleToLongBits(second.sensitivity))) {
            throw new IOException("Conflicting metrics for identical expression pair");
        }
    }

    private static void aggregate(Path output, Properties manifest, Context context) throws IOException {
        Path all = output.resolve("members.csv");
        Path temporary = output.resolve("members-aggregate.tmp");
        Files.deleteIfExists(temporary);
        long rows = 0;
        Map<String, Long> totals = new LinkedHashMap<>();
        try (java.io.BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            writer.write(HEADER); writer.newLine();
            for (Run run : context.runs) {
                Path directory = child(output, required(manifest, run.key()));
                Properties saved = ExperimentAFiles.read(directory.resolve("manifest.properties"));
                contextCheck(directory, saved);
                for (String key : Arrays.asList("retained-members", "pool-members", "computed-attributes",
                        "reused-attributes", "audited-cached-attributes", "undefined-sensitivity",
                        "quality-not-evaluated", "quality-failed")) {
                    totals.merge(key, Long.parseLong(required(saved, key)), Long::sum);
                }
                try (BufferedReader reader = Files.newBufferedReader(directory.resolve("members.csv"), StandardCharsets.UTF_8)) {
                    if (!HEADER.equals(reader.readLine())) { throw new IOException("B output header mismatch"); }
                    String line;
                    while ((line = reader.readLine()) != null) { writer.write(line); writer.newLine(); rows++; }
                }
                manifest.setProperty(run.key() + ".manifest-sha256",
                        RevisionCsv.fileHash(directory.resolve("manifest.properties")));
            }
        }
        if (rows != totals.get("retained-members")) { throw new IOException("Aggregate B row count mismatch"); }
        if (Files.exists(all)) {
            if (!RevisionCsv.fileHash(all).equals(RevisionCsv.fileHash(temporary))) {
                throw new IOException("Existing B aggregate differs from verified runs");
            }
            Files.delete(temporary);
        } else { Files.move(temporary, all); }
        Path hashes = output.resolve("input_hashes.csv");
        if (!Files.exists(hashes)) {
            try (RevisionCsv csv = new RevisionCsv(hashes, "path", "sha256")) {
                for (Map.Entry<Path, String> entry : context.hashes.entrySet()) { csv.row(entry.getKey(), entry.getValue()); }
            }
        }
        for (Map.Entry<String, Long> total : totals.entrySet()) { manifest.setProperty(total.getKey(), total.getValue().toString()); }
        manifest.setProperty("members-sha256", RevisionCsv.fileHash(all));
        manifest.setProperty("input-hashes-sha256", RevisionCsv.fileHash(hashes));
        manifest.remove("failure");
    }

    private static void verifyCompleted(Path output, Properties manifest, Context context) throws IOException {
        ExperimentAFiles.checkHash(output.resolve("members.csv"), required(manifest, "members-sha256"));
        ExperimentAFiles.checkHash(output.resolve("input_hashes.csv"), required(manifest, "input-hashes-sha256"));
        for (Run run : context.runs) {
            Path directory = child(output, required(manifest, run.key()));
            ExperimentAFiles.checkHash(directory.resolve("manifest.properties"),
                    required(manifest, run.key() + ".manifest-sha256"));
            contextCheck(directory, ExperimentAFiles.read(directory.resolve("manifest.properties")));
        }
        verifyInputs(context);
    }

    private static void contextCheck(Path directory, Properties saved) throws IOException {
        if (!"COMPLETE".equals(required(saved, "status"))) { throw new IOException("Incomplete B run"); }
        ExperimentAFiles.checkHash(directory.resolve("members.csv"), required(saved, "members-sha256"));
    }

    private static void verifyInputs(Context context) throws IOException {
        for (Map.Entry<Path, String> entry : context.hashes.entrySet()) {
            ExperimentAFiles.checkHash(entry.getKey(), entry.getValue());
        }
    }

    private static String fingerprint(Context context) throws IOException {
        StringBuilder text = new StringBuilder(PROTOCOL).append('\n')
                .append(System.getProperty("java.runtime.version")).append('\n');
        for (Map.Entry<Path, String> entry : context.hashes.entrySet()) {
            text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        java.util.Set<Class<?>> implementations = new java.util.LinkedHashSet<>();
        for (Class<?> type : Arrays.asList(WholeLibraryStudy.class,
                FrozenPriorityProgram.class, TreeComplexityCalculator.class, PriorityExpression.class,
                ArchiveCandidate.class, FinalArchiveReader.class, SensitivityProbeSet.class, ExperimentAFiles.class)) {
            addClasses(type, implementations);
        }
        for (Class<?> type : implementations) {
            String resource = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
            try (InputStream input = type.getResourceAsStream(resource)) {
                if (input == null) { throw new IOException("Missing B implementation class: " + type); }
                text.append(type.getName()).append('=')
                        .append(java.util.Base64.getEncoder().encodeToString(input.readAllBytes())).append('\n');
            }
        }
        return ArchiveCandidate.sha256(text.toString());
    }

    private static void addClasses(Class<?> type, java.util.Set<Class<?>> classes) {
        if (classes.add(type)) {
            Class<?>[] nested = type.getDeclaredClasses();
            Arrays.sort(nested, java.util.Comparator.comparing(Class::getName));
            for (Class<?> child : nested) { addClasses(child, classes); }
        }
    }

    static Map<String, List<String>> readCsv(Path path, String header, int columns) throws IOException {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            if (!header.equals(reader.readLine())) { throw new IOException("Unexpected CSV header: " + path); }
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> fields = ExperimentAAnalysis.csvFields(line);
                if (fields.size() != columns || rows.putIfAbsent(fields.get(0), fields) != null) {
                    throw new IOException("Wrong CSV width or duplicate id: " + path);
                }
            }
        }
        return rows;
    }

    private static Path child(Path root, String name) throws IOException {
        if (!name.matches("[A-Za-z0-9_-]+")) { throw new IOException("Invalid run mapping"); }
        Path path = root.resolve(name);
        if (Files.exists(path) && !path.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("Run mapping escapes root");
        }
        return path;
    }

    private static double finite(String text) throws IOException {
        double value = Double.parseDouble(text);
        if (!Double.isFinite(value) || value < 0) { throw new IOException("Non-finite/negative cached metric"); }
        return value;
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) { throw new IOException("Missing manifest property: " + key); }
        return value;
    }
}
