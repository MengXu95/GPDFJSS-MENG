package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PushbackReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import yimei.jss.jobshop.Objective;

/** Synthetic final outputs with injected outcomes/PCs. No scheduling or evolution is launched. */
public final class ETestChecks {
    private ETestChecks() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) { throw new IllegalArgumentException("ETestChecks <module directory> <NEW fixture root>"); }
        runChecks(Path.of(args[0]), Path.of(args[1]));
        System.out.println("E TEST/COMPARISON CHECKS PASSED (injected callbacks; zero scheduling/evolution)");
    }

    public static void runChecks(Path module, Path root) throws IOException {
        if (Files.exists(root)) { throw new IOException("Fixture root must be NEW"); }
        Files.createDirectories(root);
        EConfiguration formal = EConfiguration.parse(new String[]{"--config", module.resolve("e-study.properties").toString()});
        check(formal.requestedCases().size() == 360 && formal.methods.equals(Collections.singletonList(EConfiguration.Method.LEXICASE)),
                "Default is only 360 Lexicase fixed/rotating cases");
        check(EConfiguration.Scenario.F085.objective == Objective.MAX_FLOWTIME
                && EConfiguration.Scenario.F095.objective == Objective.MAX_FLOWTIME
                && EConfiguration.Scenario.W085.objective == Objective.MEAN_WEIGHTED_FLOWTIME, "Six native objective/scenario identities");
        Fixture f = fixture(formal, root.resolve("incremental"));
        coreChecks(f);
        Evaluator evaluator = new Evaluator(f.config, 0);
        Banks banks = new Banks();
        ETest.run(parse(f, "--dry-run"), evaluator, banks);
        check(evaluator.calls == 0 && banks.collections == 0 && !Files.exists(f.config.testRoot), "Preflight does no work");
        ETest.run(f.config, evaluator, banks);
        verifyLex(f, evaluator, banks);
        Properties lex = EFiles.read(f.config.testRoot.resolve("manifest.properties"));
        Path lexReport = reportPath(f.config.testRoot);
        String lexReportHash = EFiles.hash(lexReport.resolve("manifest.properties"));
        Properties lexReportManifest = EFiles.read(lexReport.resolve("manifest.properties"));
        String evaluationFamily = lex.getProperty("evaluation-family-hash");
        String lexCase = new EConfiguration.Case(EConfiguration.Method.LEXICASE, EConfiguration.Regime.fixed,
                EConfiguration.Scenario.T085, 0).key();
        String caseHash = lex.getProperty(lexCase + ".manifest-sha256");
        String completeRoot = EFiles.hash(f.config.testRoot.resolve("manifest.properties"));
        int calls = evaluator.calls, collections = banks.collections;
        ETest.run(parse(f, "--resume"), evaluator, banks);
        check(calls == evaluator.calls && collections == banks.collections
                && completeRoot.equals(EFiles.hash(f.config.testRoot.resolve("manifest.properties"))), "Complete resume is zero writes/evaluation");
        reject(() -> ETest.run(f.config, evaluator, banks), "Existing root needs resume");
        reject(() -> ETest.run(parse(f, "--resume", "--set", "validation-instances=1"), evaluator, banks), "Test controls change family");
        try (FileChannel channel = FileChannel.open(f.config.testRoot.resolve(".test.lock"), StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            check(lock != null, "Fixture lock acquired");
            reject(() -> ETest.run(parse(f, "--resume"), evaluator, banks), "Root lock prevents concurrent evaluation");
        }
        EConfiguration controls = parse(f, "--resume", "--set", "methods=GP,QDGP");
        reject(() -> ETest.run(controls, evaluator, banks), "Requested controls missing training => preflight failure");
        check(calls == evaluator.calls && collections == banks.collections
                && completeRoot.equals(EFiles.hash(f.config.testRoot.resolve("manifest.properties"))), "Missing controls cannot alter Lex results");
        String oldSourceRoot = EFiles.hash(f.config.trainingRoot.resolve("manifest.properties"));
        extend(f, controls);
        check(!oldSourceRoot.equals(EFiles.hash(f.config.trainingRoot.resolve("manifest.properties"))), "Training root legitimately extended");
        ETest.run(controls, evaluator, banks);
        Properties full = EFiles.read(f.config.testRoot.resolve("manifest.properties"));
        check(full.getProperty("evaluation-family-hash").equals(evaluationFamily), "Selectors/root manifest SHA excluded from evaluation family");
        check(full.getProperty(lexCase + ".manifest-sha256").equals(caseHash) && evaluator.lexCalls == calls,
                "All completed Lexicase cases reused without work or mutation");
        Path fullReport = reportPath(f.config.testRoot);
        List<Map<String, String>> fullRows = records(fullReport.resolve("run_results.csv"));
        check(fullRows.size() == 72
                && full.getProperty("completed-case-count").equals("72"), "Root report contains all completed methods, not just GP/QD invocation");
        for (Map<String, String> row : fullRows) {
            if (row.get("method").equals("QDGP")) {
                check(Double.parseDouble(row.get("native_grid_cells")) == 6561
                        && Double.parseDouble(row.get("native_coverage")) == 4.0 / 6561,
                        "Report denominator/coverage use the canonical producer native-grid-cells value");
            } else {
                check(row.get("native_grid_cells").equals("NA") && row.get("native_coverage").equals("NA"),
                        "Canonical native geometry does not introduce population-native labels");
            }
        }
        check(!fullReport.equals(lexReport) && lexReportHash.equals(EFiles.hash(lexReport.resolve("manifest.properties"))),
                "Extension publishes a new immutable report without rewriting the previous report");
        EFiles.verifyArtifacts(lexReport, lexReportManifest);
        check(full.getProperty("comparison-status").equals("DIAGNOSTIC_COMPARISON_ONLY"), "Small fixtures never claim formal inference complete");
        check(evaluator.calls == 3 * calls && banks.collections == 12, "Only new methods evaluated; one replay per pending scenario/invocation");
        calls = evaluator.calls; collections = banks.collections;
        String fullHash = EFiles.hash(f.config.testRoot.resolve("manifest.properties"));
        ETest.run(parse(f, "--resume"), evaluator, banks); // narrower Lex-only request must retain 72-case view.
        check(calls == evaluator.calls && collections == banks.collections
                && fullHash.equals(EFiles.hash(f.config.testRoot.resolve("manifest.properties"))), "Narrowed complete scope remains a zero-write verification");
        EComparisonChecks.runChecks(f.config, root);
        interrupted(formal, root);
        malformed(formal, root);
        canonicalNativeGeometry(formal, root);
        changedSourceDuringInvocation(formal, root);
        corruptComplete(f);
    }

    private static void coreChecks(Fixture f) throws IOException {
        List<ArchiveCandidate> all = members(f.config.trainingRoot.resolve("unused.stat"));
        List<ArchiveCandidate> draw = ETest.subset(all, 200, 42);
        check(draw.size() == 2 && draw.stream().map(ArchiveCandidate::treeHash).distinct().count() == 2,
                "min(200, UNIQUE_STORED_FINITE), no padding or failed-training members");
        List<ArchiveCandidate> reversed = new ArrayList<>(all); Collections.reverse(reversed);
        check(ids(draw).equals(ids(ETest.subset(reversed, 200, 42))), "Stable draw independent of final-output iteration order");
        Map<String, Double> means = new LinkedHashMap<>();
        for (ArchiveCandidate a : draw) { means.put(a.treeHash(), a.ordinal() == 0 ? 50.0 : 10.0); }
        check(ETest.select(draw, means).ordinal() == 1, "Validation subset choice, NOT minimum training fitness anchor");
        for (ArchiveCandidate a : draw) { means.put(a.treeHash(), 10.0); }
        ArchiveCandidate expected = draw.stream().min(java.util.Comparator.comparing(ArchiveCandidate::treeHash)).orElseThrow();
        check(ETest.select(draw, means).id().equals(expected.id()), "Validation ties use deterministic expression hash");
        for (ArchiveCandidate a : draw) { means.put(a.treeHash(), Double.POSITIVE_INFINITY); }
        check(ETest.select(draw, means) == null, "No finite validation mean => no arbitrary fallback");
        check(ETest.cells(new double[]{0,1,2,0,2,1,0,2})[0] == 15, "Common base-3 rank orientation");
        reject(() -> ETest.cells(new double[]{0,3,0,0,0,0,0,0}), "Out-of-alphabet projection");
        reject(() -> new ETest.Outcome(Double.POSITIVE_INFINITY, ""), "Numeric failures require reason");
    }

    private static void verifyLex(Fixture f, Evaluator evaluator, Banks banks) throws IOException {
        Properties root = EFiles.read(f.config.testRoot.resolve("manifest.properties"));
        check(root.getProperty("status").equals("COMPLETE") && root.getProperty("completed-case-count").equals("24")
                && root.getProperty("comparison-status").equals("COMPARISON_NOT_READY"), "Complete Lex scope, NOT complete matched comparison");
        check(evaluator.calls == 165 && evaluator.testCalls == 69 && evaluator.validationCalls == 96
                && banks.collections == 6, "All 24 cases; 96 validations + 69 executed test slots; six common banks");
        check(root.getProperty("test-scheduled-count").equals("72") && root.getProperty("test-unexecuted-count").equals("3")
                && root.getProperty("no-representative-cases").equals("1"), "No-representative denominators remain separate");
        for (EConfiguration.Case item : f.config.requestedCases()) {
            Path directory = EFiles.mapped(f.config.testRoot, root.getProperty(item.key() + ".path"));
            Properties result = EFiles.read(directory.resolve("manifest.properties"));
            check(result.getProperty("representative-policy").equals("VALIDATION_SELECTED_FROM_SUBSET")
                    && result.getProperty("native_coverage").equals("NA")
                    && result.getProperty("output-kind").equals("POPULATION"), "No population-native-QD labels");
            check(result.getProperty("subset_size").equals("2") && result.getProperty("subset_available_pairs").equals("2")
                    && records(directory.resolve("subset_plan.csv")).size() == 2, "Frozen unique subset metadata");
            List<Map<String, String>> members = records(directory.resolve("member_projection.csv"));
            check(members.size() == 4 && members.stream().anyMatch(row -> row.get("stored_status").equals("STORED_FAILURE")
                    && row.get("status").equals("PROJECTED")), "All actual final members include stored failures in common projection");
            if (noRepresentative(item)) {
                check(result.getProperty("mean_test_objective").equals("Infinity")
                        && result.getProperty("test_scheduled_count").equals("3") && result.getProperty("test_executed_count").equals("0")
                        && result.getProperty("test_unexecuted_count").equals("3") && result.getProperty("test_failure_count").equals("0"),
                        "NO_REPRESENTATIVE: Infinity objective, scheduled slots but zero fake simulations");
                check(records(directory.resolve("test_instance_outcomes.csv")).stream().allMatch(row ->
                        row.get("executed").equals("false") && row.get("status").equals("NOT_EXECUTED_NO_REPRESENTATIVE")),
                        "No representative has explicit unexecuted outcome rows");
            } else {
                check(result.getProperty("representative-id").startsWith("1-"), "No training-best anchor or held-out reselection");
                check(records(directory.resolve("test_instance_outcomes.csv")).size() == 3, "All held-out slots preserved");
            }
            if (partialFailure(item)) {
                check(result.getProperty("quality_mean_validation_objective").equals("Infinity")
                        && result.getProperty("validation_failure_count").equals("1")
                        && result.getProperty("test_failure_count").equals("1")
                        && result.getProperty("mean_test_objective").equals("Infinity"), "Mixed validation/test failures preserved without replacement");
            }
            if (testOnlyFailure(item)) {
                check(Double.isFinite(ETest.value(result, "quality_mean_validation_objective"))
                        && result.getProperty("mean_test_objective").equals("Infinity")
                        && result.getProperty("test_failure_count").equals("1"),
                        "A failed held-out representative is not replaced even when another validation-finite candidate exists");
            }
            EFiles.verifyArtifacts(directory, result);
        }
        EFiles.verifyArtifacts(f.config.testRoot, root);
        Path report = reportPath(f.config.testRoot);
        check(records(report.resolve("run_results.csv")).size() == 24, "Authoritative report contains all completed Lexicase cases");
        for (String file : ETest.REPORT_FILES) {
            check(!Files.exists(f.config.testRoot.resolve(file)), "No mutable root report view is created or required: " + file);
        }
    }

    private static void interrupted(EConfiguration formal, Path root) throws IOException {
        Fixture f = fixture(formal, root.resolve("interrupted"));
        Evaluator stopped = new Evaluator(f.config, 8);
        Banks banks = new Banks();
        reject(() -> ETest.run(f.config, stopped, banks), "Injected I/O interruption");
        Properties old = EFiles.read(f.config.testRoot.resolve("manifest.properties"));
        String first = f.config.requestedCases().get(0).key();
        String second = f.config.requestedCases().get(1).key();
        String oldHash = old.getProperty(first + ".manifest-sha256"), failedPath = old.getProperty(second + ".path");
        check(old.getProperty("status").equals("FAILED"), "Interrupted root marked FAILED");
        banks.corrupt = true;
        Evaluator noWork = new Evaluator(f.config, 0);
        reject(() -> ETest.run(parse(f, "--resume"), noWork, banks), "Replayed bank must match frozen hash");
        check(noWork.calls == 0, "Bad replay cannot cause scheduling work");
        banks.corrupt = false;
        Evaluator resumed = new Evaluator(f.config, 0);
        ETest.run(parse(f, "--resume"), resumed, banks);
        Properties complete = EFiles.read(f.config.testRoot.resolve("manifest.properties"));
        check(complete.getProperty(first + ".manifest-sha256").equals(oldHash)
                && !complete.getProperty(second + ".path").equals(failedPath)
                && Files.isDirectory(EFiles.mapped(f.config.testRoot, failedPath)), "Completed reused; incomplete attempts preserved, not overwritten");
        // An unpublished partial report must never replace or corrupt the previously committed report.
        Path previousReport = reportPath(f.config.testRoot);
        String previousReportHash = EFiles.hash(previousReport.resolve("manifest.properties"));
        complete.setProperty("status", "FAILED"); EFiles.write(f.config.testRoot.resolve("manifest.properties"), complete, true);
        Path orphan = Files.createDirectory(f.config.testRoot.resolve("report-attempt-0002"));
        Files.writeString(orphan.resolve("run_results.csv"), "UNPUBLISHED_PARTIAL_REPORT\n", StandardCharsets.UTF_8);
        int calls = resumed.calls, collections = banks.collections;
        ETest.run(parse(f, "--resume"), resumed, banks);
        Path recoveredReport = reportPath(f.config.testRoot);
        check(calls == resumed.calls && collections == banks.collections
                && records(recoveredReport.resolve("run_results.csv")).size() == 24
                && !recoveredReport.equals(previousReport) && !recoveredReport.equals(orphan),
                "Interrupted report publication creates a new report and atomically commits its pointer without simulation");
        check(previousReportHash.equals(EFiles.hash(previousReport.resolve("manifest.properties")))
                && Files.readString(orphan.resolve("run_results.csv")).equals("UNPUBLISHED_PARTIAL_REPORT\n"),
                "Both prior committed and unpublished incomplete report attempts remain unchanged");
    }

    private static void malformed(EConfiguration formal, Path root) throws IOException {
        Fixture bad = fixture(formal, root.resolve("bad-source"));
        Path directory = EFiles.mapped(bad.config.trainingRoot,
                EFiles.read(bad.config.trainingRoot.resolve("manifest.properties")).getProperty(bad.config.requestedCases().get(0).key() + ".path"));
        Files.write(directory.resolve("final_population.stat"), Arrays.asList("changed"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Evaluator e = new Evaluator(bad.config, 0); Banks b = new Banks();
        reject(() -> ETest.run(bad.config, e, b), "Input tampering fails before all work/output");
        check(e.calls == 0 && b.collections == 0 && !Files.exists(bad.config.testRoot), "No invalid-success fallback");
        Fixture incompatible = fixture(formal, root.resolve("unmatched-source"));
        EConfiguration.Case item = incompatible.config.requestedCases().get(1);
        Properties source = EFiles.read(incompatible.config.trainingRoot.resolve("manifest.properties"));
        Path path = EFiles.mapped(incompatible.config.trainingRoot, source.getProperty(item.key() + ".path"));
        Properties mf = EFiles.read(path.resolve("manifest.properties"));
        mf.setProperty("initial-genotype-sha256", ArchiveCandidate.sha256("unmatched"));
        EFiles.write(path.resolve("manifest.properties"), mf, true);
        source.setProperty(item.key() + ".manifest-sha256", EFiles.hash(path.resolve("manifest.properties")));
        EFiles.write(incompatible.config.trainingRoot.resolve("manifest.properties"), source, true);
        Evaluator unused = new Evaluator(incompatible.config, 0); Banks unusedBank = new Banks();
        // Another method/regime of the same block is needed to expose the incompatible initialization.
        extend(incompatible, parse(incompatible, "--set", "methods=GP"));
        reject(() -> ETest.run(parse(incompatible, "--set", "methods=LEXICASE,GP"), unused, unusedBank), "Incompatible matched controls rejected");
        check(unused.calls == 0 && unusedBank.collections == 0, "Matching validation precedes simulations");
    }

    private static void corruptComplete(Fixture f) throws IOException {
        Properties root = EFiles.read(f.config.testRoot.resolve("manifest.properties"));
        String before = EFiles.hash(f.config.testRoot.resolve("manifest.properties"));
        Path reportSummary = reportPath(f.config.testRoot).resolve("summary.csv");
        byte[] intact = Files.readAllBytes(reportSummary);
        Files.writeString(reportSummary, "BAD_COMMITTED_REPORT\n", StandardCharsets.UTF_8);
        Evaluator untouched = new Evaluator(f.config, 0); Banks untouchedBanks = new Banks();
        reject(() -> ETest.run(parse(f, "--resume"), untouched, untouchedBanks), "Corrupt committed report is fatal, not an automatic repair");
        check(untouched.calls == 0 && untouchedBanks.collections == 0, "Committed report tamper performs no work");
        Files.write(reportSummary, intact);
        Path path = EFiles.mapped(f.config.testRoot, root.getProperty(f.config.requestedCases().get(0).key() + ".path"));
        Files.write(path.resolve("subset_plan.csv"), Arrays.asList("corrupt"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Evaluator e = new Evaluator(f.config, 0); Banks b = new Banks();
        reject(() -> ETest.run(parse(f, "--resume"), e, b), "COMPLETE cache corruption is fatal");
        check(e.calls == 0 && b.collections == 0 && before.equals(EFiles.hash(f.config.testRoot.resolve("manifest.properties"))),
                "Corrupt completed cases never trigger replacement or metadata rewrite");
    }

    private static void canonicalNativeGeometry(EConfiguration formal, Path root) throws IOException {
        Fixture f = fixture(formal, root.resolve("canonical-native-geometry"));
        EConfiguration qd = parse(f, "--set", "methods=QDGP");
        extend(f, qd);
        EConfiguration.Case item = qd.requestedCases().get(0);
        Properties training = EFiles.read(qd.trainingRoot.resolve("manifest.properties"));
        Path directory = EFiles.mapped(qd.trainingRoot, training.getProperty(item.key() + ".path"));
        Properties valid = EFiles.read(directory.resolve("manifest.properties"));
        check(valid.getProperty("native-grid-cells").equals("6561") && valid.getProperty("grid-side").equals("81")
                && !valid.containsKey("grid-cells"), "Fixtures use actual producer's canonical native-grid-cells key");
        check(new ETest.Source(item, directory, valid).load(qd).size() == 4,
                "Canonical-only QD manifest loads without any legacy denominator alias");
        Properties wrong = new Properties(); wrong.putAll(valid);
        wrong.remove("native-grid-cells"); wrong.setProperty("grid-cells", "6561");
        EFiles.write(directory.resolve("manifest.properties"), wrong, true);
        training.setProperty(item.key() + ".manifest-sha256", EFiles.hash(directory.resolve("manifest.properties")));
        EFiles.write(qd.trainingRoot.resolve("manifest.properties"), training, true);
        Evaluator evaluator = new Evaluator(qd, 0); Banks banks = new Banks();
        try {
            ETest.run(qd, evaluator, banks);
            throw new AssertionError("Obsolete grid-cells key must never be accepted as a fallback");
        } catch (IOException expected) {
            check(expected.getMessage().contains("native-grid-cells"), "Missing canonical denominator is reported explicitly");
        }
        check(evaluator.calls == 0 && banks.collections == 0 && !Files.exists(qd.testRoot),
                "Canonical schema mismatch fails before evaluation, replay, or test-root creation");
        valid.setProperty("native-grid-cells", "6560");
        EFiles.write(directory.resolve("manifest.properties"), valid, true);
        training.setProperty(item.key() + ".manifest-sha256", EFiles.hash(directory.resolve("manifest.properties")));
        EFiles.write(qd.trainingRoot.resolve("manifest.properties"), training, true);
        reject(() -> ETest.run(qd, evaluator, banks), "Native denominator must equal the square of the actual grid side");
        check(evaluator.calls == 0 && banks.collections == 0, "Inconsistent geometry cannot trigger simulations");
    }

    private static void changedSourceDuringInvocation(EConfiguration formal, Path root) throws IOException {
        Fixture f = fixture(formal, root.resolve("source-mutated-mid-invocation"));
        Evaluator evaluator = new Evaluator(f.config, 0);
        evaluator.mutate = f.config.trainingRoot.resolve("manifest.properties");
        Banks banks = new Banks();
        reject(() -> ETest.run(f.config, evaluator, banks), "Current source-root SHA is immutable within an invocation");
        check(EFiles.read(f.config.testRoot.resolve("manifest.properties")).getProperty("status").equals("FAILED")
                && evaluator.calls == 7 && banks.collections == 1, "Source mutation stops before another case and never publishes COMPLETE");
    }

    private static boolean noRepresentative(EConfiguration.Case item) {
        return item.scenario == EConfiguration.Scenario.F095 && item.regime == EConfiguration.Regime.rotating && item.run == 1;
    }
    private static boolean partialFailure(EConfiguration.Case item) {
        return item.scenario == EConfiguration.Scenario.T085 && item.regime == EConfiguration.Regime.fixed && item.run == 0;
    }
    private static boolean testOnlyFailure(EConfiguration.Case item) {
        return item.scenario == EConfiguration.Scenario.T095 && item.regime == EConfiguration.Regime.fixed && item.run == 0;
    }
    private static final class Evaluator implements ETest.EvaluationService {
        final EConfiguration config;
        final int stopAt;
        int calls, lexCalls, testCalls, validationCalls;
        Path mutate;
        Evaluator(EConfiguration config, int stopAt) { this.config = config; this.stopAt = stopAt; }
        @Override public String identity() { return "CHECKS_ONLY_E_NATIVE_OBJECTIVE_OUTCOMES_V1"; }
        @Override public ETest.Outcome evaluate(EConfiguration.Case item, ArchiveCandidate candidate, long seed) throws IOException {
            calls++;
            if (calls == stopAt) { throw new IOException("Injected interruption"); }
            if (mutate != null) {
                Files.write(mutate, Arrays.asList("concurrent-source-change=true"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                mutate = null;
            }
            if (item.method == EConfiguration.Method.LEXICASE) { lexCalls++; }
            check(item.scenario.objective == EConfiguration.Scenario.parse(item.scenario.id).objective, "Scenario objective identity");
            boolean testing = seed >= config.testSeed(item.scenario) && seed < config.testSeed(item.scenario) + config.testInstances;
            double offset = item.scenario.ordinal() * 100 + item.run + (item.method == EConfiguration.Method.GP ? 5 : item.method == EConfiguration.Method.QDGP ? -2 : 0);
            if (testing) {
                testCalls++;
                check(!noRepresentative(item) && candidate.ordinal() == 1, "Only the within-subset validation-selected representative is tested");
                if ((partialFailure(item) || testOnlyFailure(item)) && seed == config.testSeed(item.scenario) + 1) {
                    return new ETest.Outcome(Double.POSITIVE_INFINITY, "synthetic held-out non-finite outcome");
                }
                return new ETest.Outcome(20 + offset, "");
            }
            validationCalls++;
            check(seed >= config.validationSeed(item.scenario) && seed < config.validationSeed(item.scenario) + config.validationInstances,
                    "Common validation seed family");
            check(candidate.hasFiniteStoredFitness() && (candidate.ordinal() == 0 || candidate.ordinal() == 1),
                    "No duplicate or stored-failure validation candidate");
            if (noRepresentative(item) || (partialFailure(item) && candidate.ordinal() == 0
                    && seed == config.validationSeed(item.scenario) + 1)) {
                return new ETest.Outcome(Double.POSITIVE_INFINITY, "synthetic validation failure, \"kept\"\nno redraw");
            }
            return new ETest.Outcome((candidate.ordinal() == 0 ? 50 : 10) + offset, "");
        }
    }
    private static final class Banks implements ETest.ProjectionFactory {
        int collections;
        boolean corrupt;
        @Override public String identity() { return "CHECKS_ONLY_SCENARIO_PC_V1"; }
        @Override public ETest.ProjectionService collect(EConfiguration c, EConfiguration.Scenario scenario) {
            collections++;
            return new Frozen(c.commonSeed(scenario), scenario.id);
        }
        private final class Frozen implements ETest.ProjectionService {
            final long seed; final String scenario;
            Frozen(long seed, String scenario) { this.seed = seed; this.scenario = scenario; }
            @Override public double[] coordinates(ArchiveCandidate candidate) {
                double[] digits = new double[8]; digits[3] = candidate.ordinal() % 3; digits[7] = candidate.ordinal() / 3; return digits;
            }
            @Override public void write(Path path) throws IOException {
                Files.write(path, Arrays.asList("CHECKS_ONLY_FROZEN_BANK", scenario, "seed=" + seed, corrupt ? "CORRUPT" : "STABLE"),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            @Override public long simulationCount() { return 0; }
        }
    }

    private static final class Fixture {
        final Path configPath; final EConfiguration config;
        Fixture(Path path, EConfiguration config) { configPath = path; this.config = config; }
    }
    private static Fixture fixture(EConfiguration formal, Path root) throws IOException {
        Files.createDirectory(root);
        Properties p = formal.properties();
        for (String[] option : new String[][]{{"training-output-root", root.resolve("training").toString()},
                {"test-output-root", root.resolve("test").toString()}, {"methods", "LEXICASE"},
                {"runs", "2"}, {"population", "4"}, {"generations", "2"}, {"elitism", "1"}, {"cases", "2"},
                {"recorded-jobs", "4"}, {"warmup-jobs", "0"}, {"machines", "4"},
                {"descriptor-jobs", "4"}, {"descriptor-warmup", "0"},
                {"validation-candidates", "2"}, {"validation-instances", "2"}, {"test-instances", "3"}}) {
            p.setProperty(option[0], option[1]);
        }
        Path config = root.resolve("fixture.properties"); EFiles.write(config, p, false);
        EConfiguration c = EConfiguration.parse(new String[]{"--config", config.toString()});
        Files.createDirectory(c.trainingRoot);
        EFiles.write(c.trainingRoot.resolve("resolved-study.properties"), c.properties(), false);
        Fixture fixture = new Fixture(config, c); extend(fixture, c); return fixture;
    }
    private static void extend(Fixture f, EConfiguration scope) throws IOException {
        Path marker = f.config.trainingRoot.resolve("manifest.properties");
        Properties root = Files.exists(marker) ? EFiles.read(marker) : new Properties();
        String family = ETraining.fingerprint(scope);
        if (root.containsKey("family-hash")) { check(family.equals(root.getProperty("family-hash")), "Training family excludes selectors"); }
        root.setProperty("protocol", EConfiguration.PROTOCOL); root.setProperty("status", "COMPLETE"); root.setProperty("family-hash", family);
        root.setProperty("data.resolved-study.properties.sha256", EFiles.hash(f.config.trainingRoot.resolve("resolved-study.properties")));
        for (EConfiguration.Case item : scope.requestedCases()) {
            if (root.containsKey(item.key() + ".path")) { continue; }
            Path directory = Files.createDirectory(f.config.trainingRoot.resolve(item.stem()));
            Path output = directory.resolve(item.finalFile());
            try (BufferedWriter writer = EFiles.create(output)) {
                long finalSeed = scope.trainingSeed(item) + (item.regime == EConfiguration.Regime.rotating ? (scope.generations - 1L) * scope.seedStep : 0);
                if (item.method == EConfiguration.Method.QDGP) { writer.write("All individuals in GridMAP:\nGridMAP number: 4\n"); }
                else { writer.write("Method: " + item.label() + "\nRun: " + item.run + "\nGeneration: 1\nTraining seed: "
                        + finalSeed + "\nAll individuals in Population:\nPopulation number: 4\n"); }
                for (ArchiveCandidate a : members(output)) {
                    writer.write("Evaluated: true\nFitness: [" + a.storedFitness() + "]\nTree 0:\n" + a.sequencing()
                            + "\nTree 1:\n" + a.routing() + "\n");
                }
            }
            Properties p = new Properties();
            p.setProperty("status", "COMPLETE"); p.setProperty("family-hash", family);
            p.setProperty("case-hash", ETraining.caseFingerprint(scope, item, family));
            p.setProperty("case-key", item.key()); p.setProperty("method", item.method.name()); p.setProperty("regime", item.regime.name());
            p.setProperty("scenario-id", item.scenario.id); p.setProperty("objective", item.scenario.objective.getName());
            p.setProperty("utilisation", "" + item.scenario.utilisation); p.setProperty("run-id", "" + item.run);
            p.setProperty("output-kind", item.kind()); p.setProperty("final-file", item.finalFile());
            p.setProperty("gp-seed", "" + scope.gpSeed(item)); p.setProperty("training-seed", "" + scope.trainingSeed(item));
            p.setProperty("descriptor-seed", "" + scope.descriptorSeed(item)); p.setProperty("sampling-seed", "" + scope.samplingSeed(item));
            p.setProperty("initial-training-seed", "" + scope.trainingSeed(item));
            p.setProperty("final-training-seed", "" + (scope.trainingSeed(item)
                    + (item.regime == EConfiguration.Regime.rotating ? scope.seedStep : 0)));
            p.setProperty("fitness-evaluations", "8"); p.setProperty("failed-training-evaluations", "1");
            p.setProperty("manual-reference-simulations", "0"); p.setProperty("descriptor-reference-simulations", "0");
            p.setProperty("total-training-simulations", "8"); p.setProperty("elapsed-training-seconds", ".01");
            p.setProperty("final-members", "4"); p.setProperty("stored-finite-members", "3");
            p.setProperty("initial-genotype-sha256", ArchiveCandidate.sha256("genotypes:" + item.scenario + ":" + item.run));
            p.setProperty("initial-evaluated-population-sha256", ArchiveCandidate.sha256("evaluated:" + item.scenario + ":" + item.run));
            if (item.method == EConfiguration.Method.QDGP) {
                p.setProperty("native-occupied-cells", "4");
                p.setProperty("native-grid-cells", "6561");
                p.setProperty("grid-side", "81");
            }
            p.setProperty("data." + item.finalFile() + ".sha256", EFiles.hash(output));
            EFiles.write(directory.resolve("manifest.properties"), p, false);
            root.setProperty(item.key() + ".path", directory.getFileName().toString());
            root.setProperty(item.key() + ".manifest-sha256", EFiles.hash(directory.resolve("manifest.properties")));
        }
        Path report = freshTrainingReport(f.config.trainingRoot);
        try (BufferedWriter writer = EFiles.create(report.resolve("training_runs.csv"))) {
            EFiles.row(writer, "case_key", "attempt_path");
            for (String field : new java.util.TreeSet<>(root.stringPropertyNames())) {
                if (field.contains(".run.") && field.endsWith(".path")) {
                    EFiles.row(writer, field.substring(0, field.length() - 5), root.getProperty(field));
                }
            }
        }
        Properties reportManifest = new Properties();
        reportManifest.setProperty("status", "COMPLETE"); reportManifest.setProperty("family-hash", family);
        reportManifest.setProperty("data.training_runs.csv.sha256", EFiles.hash(report.resolve("training_runs.csv")));
        EFiles.write(report.resolve("manifest.properties"), reportManifest, false);
        root.setProperty("report.path", report.getFileName().toString());
        root.setProperty("report.manifest-sha256", EFiles.hash(report.resolve("manifest.properties")));
        EFiles.write(marker, root, Files.exists(marker));
        check(!Files.exists(f.config.trainingRoot.resolve("training_runs.csv")), "Training report requires no root CSV");
    }
    private static Path freshTrainingReport(Path root) throws IOException {
        for (int i = 1; i < 1000; i++) {
            Path candidate = root.resolve(String.format(java.util.Locale.ROOT, "training-report-%04d", i));
            if (!Files.exists(candidate)) { return Files.createDirectory(candidate); }
        }
        throw new IOException("Fixture training report namespace exhausted");
    }
    private static Path reportPath(Path root) throws IOException {
        Properties manifest = EFiles.read(root.resolve("manifest.properties"));
        Path report = EFiles.mapped(root, EFiles.required(manifest, "report.path"));
        EFiles.checkHash(report.resolve("manifest.properties"), EFiles.required(manifest, "report.manifest-sha256"));
        Properties saved = EFiles.read(report.resolve("manifest.properties"));
        check(saved.getProperty("status").equals("COMPLETE"), "Referenced report is complete");
        EFiles.verifyArtifacts(report, saved);
        return report;
    }
    private static List<ArchiveCandidate> members(Path source) {
        List<ArchiveCandidate> result = new ArrayList<>();
        String[] seq = {"PT", "(+ PT 1)", "(+ PT 1)", "(+ PT 2)"};
        double[] raw = {1, 5, 5, Double.POSITIVE_INFINITY};
        for (int i = 0; i < raw.length; i++) { result.add(new ArchiveCandidate(source, i, raw[i], seq[i], "WIQ")); }
        return result;
    }
    private static EConfiguration parse(Fixture fixture, String... tail) throws IOException {
        List<String> options = new ArrayList<>(Arrays.asList("--config", fixture.configPath.toString()));
        Collections.addAll(options, tail); return EConfiguration.parse(options.toArray(new String[0]));
    }
    private static List<String> ids(List<ArchiveCandidate> members) {
        List<String> result = new ArrayList<>();
        for (ArchiveCandidate member : members) { result.add(member.id()); }
        return result;
    }
    private static List<Map<String, String>> records(Path path) throws IOException {
        List<List<String>> records = new ArrayList<>();
        try (PushbackReader reader = new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 1)) {
            List<String> row = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false; int value;
            while ((value = reader.read()) != -1) {
                if (quoted) {
                    if (value == '"') { int next = reader.read(); if (next == '"') { field.append('"'); } else { quoted = false; if (next != -1) { reader.unread(next); } } }
                    else { field.append((char) value); }
                } else if (value == '"') { quoted = true; }
                else if (value == ',') { row.add(field.toString()); field.setLength(0); }
                else if (value == '\n') { row.add(field.toString()); field.setLength(0); records.add(row); row = new ArrayList<>(); }
                else if (value != '\r') { field.append((char) value); }
            }
            check(!quoted && row.isEmpty() && field.length() == 0 && !records.isEmpty(), "Well-formed terminated report CSV");
        }
        List<String> header = records.remove(0);
        List<Map<String, String>> output = new ArrayList<>();
        for (List<String> row : records) {
            check(row.size() == header.size(), "CSV column count"); Map<String, String> record = new LinkedHashMap<>();
            for (int i = 0; i < row.size(); i++) { record.put(header.get(i), row.get(i)); }
            output.add(record);
        }
        return output;
    }
    private interface CheckedAction { void run() throws IOException; }
    private static void reject(CheckedAction action, String message) throws IOException {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected strict rejection: " + message);
    }
    private static void check(boolean valid, String message) { if (!valid) { throw new AssertionError(message); } }
}
