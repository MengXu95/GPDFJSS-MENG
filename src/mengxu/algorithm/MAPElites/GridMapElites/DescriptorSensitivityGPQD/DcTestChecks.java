package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;

/** Synthetic archives and injected outcomes only: no evolution or scheduling simulations. */
public final class DcTestChecks {
    private DcTestChecks() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("DcTestChecks <NEW fixture-parent-directory>");
            return;
        }
        if (args.length != 1) { throw new IllegalArgumentException("Provide one NEW fixture parent"); }
        runChecks(Path.of(args[0]));
        System.out.println("DC TEST CHECKS PASSED (injected evaluations and common projection; no scheduling/evolution)");
    }

    public static void runChecks(Path root) throws IOException {
        if (Files.exists(root)) { throw new IOException("Fixture parent must be new: " + root); }
        Files.createDirectories(root);
        statisticsChecks(root);
        Fixture normal = fixture(root, "normal");
        Evaluator evaluator = new Evaluator(normal.config, 0);
        Projection projection = new Projection();
        DcConfiguration dry = parse(normal, "--dry-run");
        DcTest.run(dry, evaluator, projection);
        check(evaluator.calls == 0 && projection.collections == 0 && !Files.exists(normal.config.testRoot),
                "Dry run validates all inputs but performs no simulations or output creation");
        DcTest.run(normal.config, evaluator, projection);
        verifyReport(normal, evaluator, projection);
        String rootHash = DcFiles.hash(normal.config.testRoot.resolve("manifest.properties"));
        int calls = evaluator.calls, collections = projection.collections;
        DcTest.run(parse(normal, "--resume"), evaluator, projection);
        check(calls == evaluator.calls && collections == projection.collections
                && rootHash.equals(DcFiles.hash(normal.config.testRoot.resolve("manifest.properties"))),
                "COMPLETE resume is hash verification only, with no simulation or root rewrite");
        reject(() -> DcTest.run(normal.config, evaluator, projection), "Existing root without resume");
        reject(() -> DcTest.run(parse(normal, "--resume", "--set", "quality-sample=1"), evaluator, projection),
                "Changed test controls require a new output root");
        check(rootHash.equals(DcFiles.hash(normal.config.testRoot.resolve("manifest.properties"))),
                "Rejected resume cannot rewrite completed root");
        try (FileChannel channel = FileChannel.open(normal.config.testRoot.resolve(".test.lock"), StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            check(lock != null, "Fixture acquired the root lock");
            reject(() -> DcTest.run(parse(normal, "--resume"), evaluator, projection), "Concurrent test root ownership rejected");
        }
        check(calls == evaluator.calls && collections == projection.collections, "Locked root performs no work");

        Fixture exhaustive = fixture(root, "exhaustive");
        DcConfiguration full = parse(exhaustive, "--set", "quality-sample=0");
        Evaluator fullEvaluator = new Evaluator(full, 0);
        DcTest.run(full, fullEvaluator, new Projection());
        check(fullEvaluator.calls == 4 * (3 + 3 * 2), "Full-finite-unique mode evaluates all three unique pairs, not six members");
        Properties fullRoot = DcFiles.read(full.testRoot.resolve("manifest.properties"));
        check(fullRoot.getProperty("quality-mode").equals("EXHAUSTIVE_STORED_FINITE_UNIQUE_ONLY"),
                "Exhaustive mode explicitly describes its restricted domain");
        mixedStatuses(root);
        finiteConditionalStatuses(root);
        resumedAttempts(root);
        malformedInputs(root);
        changedInputDuringEvaluation(root);
        corruptCompletedOutput(normal);
    }

    private static void statisticsChecks(Path root) throws IOException {
        near(DcTest.mean(Arrays.asList(1.0, 3.0)), 2, "Run mean");
        near(DcTest.sd(Arrays.asList(1.0, 3.0)), Math.sqrt(2), "Sample, not population SD");
        check(Double.isNaN(DcTest.sd(Collections.singletonList(1.0))), "n=1 SD unavailable");
        check(DcTest.mean(Arrays.asList(1.0, Double.POSITIVE_INFINITY)) == Double.POSITIVE_INFINITY,
                "Failure run remains in all-run mean");
        check(Double.isNaN(DcTest.mean(Arrays.asList(1.0, Double.NaN))), "Unknown run is not silently discarded");
        check(Double.isNaN(DcTest.sd(Arrays.asList(1.0, Double.POSITIVE_INFINITY))), "Nonfinite SD unavailable");
        near(DcTest.finiteMedian(Arrays.asList(Double.POSITIVE_INFINITY, 1.0, 3.0)), 2, "Clearly conditional finite-rule median");
        check(Double.isNaN(DcTest.finiteMedian(Arrays.asList(Double.POSITIVE_INFINITY, Double.NaN))),
                "Empty finite-conditional subset has no invented median");
        near(DcTest.mean(Arrays.asList(Double.MAX_VALUE, Double.MAX_VALUE)), Double.MAX_VALUE, "Stable large mean");
        int[] cell = DcTest.cells(new double[]{0, 1, 2, 0, 2, 1, 0, 2}, 4, 3);
        check(cell[0] == 15 && cell[1] == 65, "Native base-c order, not reversed or one-based");
        reject(() -> DcTest.cells(new double[]{0, 3, 0, 0}, 2, 3), "Rank outside alphabet");
        reject(() -> DcTest.cells(new double[]{0, 0.5, 0, 0}, 2, 3), "Noninteger rank");
        reject(() -> DcTest.cells(new double[]{0, 0}, 2, 3), "Wrong PC length");
        reject(() -> new DcTest.Outcome(Double.POSITIVE_INFINITY, ""), "Failure must have a reason");
        reject(() -> new DcTest.Outcome(-1, ""), "Invalid negative objective cannot become success");
        List<ArchiveCandidate> candidates = candidates(root.resolve("sample.stat"));
        check(DcTest.representative(candidates).ordinal() == 1, "Representative chosen by raw training, stable tie ordinal");
        String early = "PT", later = "(+ PT 1)";
        ArchiveCandidate first = new ArchiveCandidate(root.resolve("tie.stat"), 0, 1, early, "WIQ");
        ArchiveCandidate second = new ArchiveCandidate(root.resolve("tie.stat"), 1, 1, later, "WIQ");
        if (first.treeHash().compareTo(second.treeHash()) < 0) {
            first = new ArchiveCandidate(root.resolve("tie.stat"), 0, 1, later, "WIQ");
            second = new ArchiveCandidate(root.resolve("tie.stat"), 1, 1, early, "WIQ");
        }
        check(first.treeHash().compareTo(second.treeHash()) > 0
                && DcTest.representative(Arrays.asList(second, first)).ordinal() == 0,
                "RAW training ties use ordinal, never expression-hash order or validation/test outcomes");
        List<ArchiveCandidate> a = DcTest.qualitySample(candidates, 2, 42);
        List<ArchiveCandidate> b = DcTest.qualitySample(candidates, 2, 42);
        check(ids(a).equals(ids(b)) && hashes(a).size() == 2, "Uniform sample is deterministic and expression-unique");
        List<ArchiveCandidate> reversed = new ArrayList<>(candidates);
        Collections.reverse(reversed);
        check(ids(a).equals(ids(DcTest.qualitySample(reversed, 2, 42))), "Sample independent of archive iteration order");
        check(DcTest.qualitySample(candidates, 200, 42).size() == 3, "At most eligible unique pairs, not padded to 200");
        check(DcTest.qualitySample(candidates, 0, 42).size() == 3, "Zero means all stored-finite unique pairs");
        check(DcTest.qualitySample(candidates, 2, 42).stream().allMatch(ArchiveCandidate::hasFiniteStoredFitness),
                "Infinity and MAX_VALUE stored failures are not quality-sampling units");
        List<ArchiveCandidate> failures = Arrays.asList(candidates.get(4), candidates.get(5));
        check(DcTest.representative(failures) == null && DcTest.qualitySample(failures, 2, 42).isEmpty(),
                "No finite raw candidate has an explicit absent representative/sample");
    }

    private static void verifyReport(Fixture f, Evaluator evaluator, Projection projection) throws IOException {
        Properties root = DcFiles.read(f.config.testRoot.resolve("manifest.properties"));
        check(root.getProperty("status").equals("COMPLETE"), "Complete root despite explicitly recorded outcome failures");
        check(root.getProperty("completed-case-test-evaluation-count").equals("12")
                && root.getProperty("completed-case-quality-evaluation-count").equals("16")
                && root.getProperty("completed-case-test-failure-count").equals("4")
                && root.getProperty("completed-case-quality-failure-count").equals("8"), "Aggregate attempt/failure counts");
        check(evaluator.calls == 4 * (3 + 2 * 2) && projection.collections == 1, "Exact bounded simulation budget and one common bank");
        check(projection.coordinates == 4 * 5, "All five expression pairs projected, including stored failures; duplicate reuse only");
        List<Map<String, String>> runRows = records(f.config.testRoot.resolve("run_results.csv"));
        check(runRows.size() == 4, "All setting/run blocks retained");
        for (DcConfiguration.Setting setting : f.config.settings) {
            for (int run = 0; run < f.config.runs; run++) {
                String key = setting.id() + ".run." + run;
                Path directory = DcFiles.mapped(f.config.testRoot, root.getProperty(key + ".path"));
                Properties result = DcFiles.read(directory.resolve("manifest.properties"));
                check(result.getProperty("representative-id").startsWith("1-"), "No test- or validation-based reselection");
                check(result.getProperty("selection-frozen-before-evaluation").equals("true")
                        && result.getProperty("selection-plan-sha256").matches("[a-f0-9]{64}")
                        && result.getProperty("representative-policy").equals(
                                "MIN_RAW_FIXED_TRAINING_THEN_ORDINAL; NO_VALIDATION_OR_TEST_RESELECTION"),
                        "Selection is frozen and audited before any held-out or validation outcome");
                check(result.getProperty("representative_mean_test_objective").equals("Infinity")
                        && result.getProperty("quality_mean_validation_objective").equals("Infinity"),
                        "Test and quality failures propagate to means without replacements");
                near(Double.parseDouble(result.getProperty("test_failure_count")), 1, "Test failure count");
                near(Double.parseDouble(result.getProperty("quality_failure_count")), 2, "Validation failure count");
                near(Double.parseDouble(result.getProperty("quality_failed_rule_count")), 2, "Failed sampled-rule count");
                near(Double.parseDouble(result.getProperty("quality_finite_rule_count")), 0, "No fully finite sampled rule");
                near(Double.parseDouble(result.getProperty("quality_finite_instance_count")), 2,
                        "Finite instances from failed rules are counted without inventing finite rule means");
                check(result.getProperty("quality_finite_conditional_rule_mean").equals("NA")
                        && result.getProperty("quality_finite_conditional_rule_median").equals("NA"),
                        "Failed rule means are not reconstructed from their selected finite instances");
                near(Double.parseDouble(result.getProperty("stored_failure_members")), 2, "Native stored-failure count");
                near(Double.parseDouble(result.getProperty("unique_stored_finite_pairs")), 3, "Unique finite sampling domain");
                near(Double.parseDouble(result.getProperty("duplicate_expression_members")), 1, "Duplicate memberships retained");
                check(result.getProperty("test-evaluation-count").equals("3")
                        && result.getProperty("quality-evaluation-count").equals("4")
                        && result.getProperty("quality-failed-pairs").equals("2"), "Attempted instance/pair denominators");
                List<Map<String, String>> tests = records(directory.resolve("test_instance_outcomes.csv"));
                check(tests.size() == 3, "All independent held-out instances exported");
                Set<String> representatives = new HashSet<>();
                for (int i = 0; i < tests.size(); i++) {
                    representatives.add(tests.get(i).get("candidate_id"));
                    check(Long.parseLong(tests.get(i).get("simulation_seed")) == f.config.testSeed + i, "Common independent test seeds");
                }
                check(representatives.size() == 1, "Same raw-training representative across all tests");
                List<Map<String, String>> sample = records(directory.resolve("quality_sample.csv"));
                check(sample.size() == 2 && sample.stream().map(row -> row.get("tree_hash")).distinct().count() == 2,
                        "Sample cap applies to unique finite expression pairs");
                check(records(directory.resolve("quality_validation_instances.csv")).size() == 4, "Every sampled pair gets every validation instance");
                List<Map<String, String>> members = records(directory.resolve("member_projection.csv"));
                check(members.size() == 6, "All retained memberships are present, including stored failures");
                check(members.stream().filter(row -> row.get("stored_status").equals("STORED_FAILURE")).count() == 2,
                        "Both sentinel kinds counted as stored failures");
                check(members.stream().filter(row -> row.get("projection_status").equals("PROJECTION_FAILED")).count() == 1,
                        "Projection failure is an explicit retained row");
                near(Double.parseDouble(result.getProperty("common_projected_members")), 5, "Projection denominator");
                near(Double.parseDouble(result.getProperty("common_occupied_cells_all_retained")), 4, "Common cell deduplication");
                near(Double.parseDouble(result.getProperty("common_coverage_all_retained")), 4.0 / 6561, "Fixed independent d4,c3 analysis geometry");
                near(Double.parseDouble(result.getProperty("common_coverage_stored_finite")), 3.0 / 6561,
                        "Stored-finite common coverage is separately labelled, never replacing all-retained coverage");
                near(Double.parseDouble(result.getProperty("common_projected_stored_finite_members")), 4,
                        "All stored-finite memberships project regardless of quality sample selection/failure");
                near(Double.parseDouble(result.getProperty("native_coverage_all_retained")), 6.0 / setting.cells(),
                        "Native all-retained coverage includes stored failures");
                long projectedStoredFailures = members.stream().filter(row -> row.get("stored_status").equals("STORED_FAILURE")
                        && row.get("projection_status").equals("PROJECTED")).count();
                check(projectedStoredFailures == 1, "Stored failures are not filtered from common projection");
                DcFiles.verifyArtifacts(directory, result);
            }
        }
        List<Map<String, String>> summary = records(f.config.testRoot.resolve("summary.csv"));
        Map<String, String> test = find(summary, "setting_id", "d2-c3", "metric", "representative_mean_test_objective");
        check(test.get("run_count").equals("2") && test.get("all_run_mean").equals("Infinity")
                && test.get("all_run_sample_sd").equals("NA") && test.get("nonfinite_run_count").equals("2"),
                "Failure runs are not dropped from descriptive summaries");
        check(test.get("failure_run_count").equals("2"), "Failed held-out runs counted explicitly");
        List<Map<String, String>> pairs = records(f.config.testRoot.resolve("paired_differences.csv"));
        Map<String, String> pair = find(pairs, "setting_id", "d2-c3", "run_id", "0", "metric", "representative_mean_test_objective");
        check(pair.get("setting_minus_baseline").equals("NA"), "Infinity-minus-Infinity has no numeric paired difference");
        check(summary.stream().allMatch(row -> row.get("inference").equals("DESCRIPTIVE_ONLY_NO_P_VALUES")),
                "No inferential significance claims for five-run design");
        check(DcFiles.read(f.config.testRoot.resolve("results_guide.properties")).getProperty("inference").contains("0.0625"),
                "n=5 exact two-sided resolution stated");
        f.inputs.verify();
        DcFiles.verifyArtifacts(f.config.testRoot, root);
    }

    private static void resumedAttempts(Path root) throws IOException {
        Fixture f = fixture(root, "interrupted");
        Evaluator stopped = new Evaluator(f.config, 8);
        Projection bank = new Projection();
        reject(() -> DcTest.run(f.config, stopped, bank), "Injected I/O interruption");
        Properties failed = DcFiles.read(f.config.testRoot.resolve("manifest.properties"));
        check(failed.getProperty("status").equals("FAILED"), "Interrupted test root explicitly fails");
        String firstKey = "d2-c3.run.0", failedKey = "d2-c3.run.1";
        String firstPath = failed.getProperty(firstKey + ".path");
        String firstHash = failed.getProperty(firstKey + ".manifest-sha256");
        String oldFailed = failed.getProperty(failedKey + ".path");
        check(DcFiles.read(DcFiles.mapped(f.config.testRoot, oldFailed).resolve("manifest.properties"))
                .getProperty("status").equals("FAILED"), "Failed attempt retained with failure metadata");
        bank.corruptReplay = true;
        Evaluator noWork = new Evaluator(f.config, 0);
        reject(() -> DcTest.run(parse(f, "--resume"), noWork, bank), "Changed common replay bytes cannot reuse completed cases");
        check(noWork.calls == 0, "Common-bank replay hash is checked before scheduling evaluations");
        bank.corruptReplay = false;
        Evaluator resumed = new Evaluator(f.config, 0);
        DcTest.run(parse(f, "--resume"), resumed, bank);
        Properties complete = DcFiles.read(f.config.testRoot.resolve("manifest.properties"));
        check(resumed.calls == 3 * 7 && bank.collections == 3, "Only unfinished cases evaluated; common bank replay once per pending invocation");
        check(complete.getProperty("common-reference-attempt-count").equals("3")
                && complete.getProperty("common-reference-unknown-attempts").equals("0"),
                "Successful and failed common-bank attempts are accounted for separately from case evaluations");
        check(firstPath.equals(complete.getProperty(firstKey + ".path"))
                && firstHash.equals(complete.getProperty(firstKey + ".manifest-sha256")), "Verified completed case reused byte-for-byte");
        check(!oldFailed.equals(complete.getProperty(failedKey + ".path"))
                && Files.isDirectory(DcFiles.mapped(f.config.testRoot, oldFailed)), "New attempt, no overwriting incomplete output");
        reject(() -> DcTest.run(parse(f, "--resume"), resumed, new Projection("DIFFERENT_SERVICE")),
                "Changed service identity cannot reuse cached results");
    }

    private static void mixedStatuses(Path root) throws IOException {
        Fixture f = fixture(root, "mixed-statuses");
        Evaluator evaluator = new Evaluator(f.config, 0, true);
        Projection projection = new Projection();
        DcTest.run(f.config, evaluator, projection);
        Properties manifest = DcFiles.read(f.config.testRoot.resolve("manifest.properties"));
        check(manifest.getProperty("status").equals("COMPLETE")
                && manifest.getProperty("completed-case-test-failure-count").equals("1")
                && manifest.getProperty("completed-case-quality-failure-count").equals("2"),
                "Mixed finite/failed cases retain exact failure counts without aborting or filtering runs");
        check(evaluator.calls == 28 && projection.collections == 1, "Mixed failures cause no replacement evaluations");
        List<Map<String, String>> runRows = records(f.config.testRoot.resolve("run_results.csv"));
        check(runRows.size() == 4, "Mixed outcome library/run blocks all retained");
        Map<String, String> failed = find(runRows, "setting_id", "d2-c3", "run_id", "0");
        Map<String, String> finite = find(runRows, "setting_id", "d2-c3", "run_id", "1");
        check(failed.get("representative_status").equals("TEST_FAILURES_PRESERVED")
                && finite.get("representative_status").equals("TEST_FINITE")
                && failed.get("quality_status").equals("VALIDATION_FAILURES_PRESERVED")
                && finite.get("quality_status").equals("VALIDATION_FINITE"),
                "Per-case statuses distinguish observed failures from finite evaluations");
        check(failed.get("representative_id").startsWith("1-") && finite.get("representative_id").startsWith("1-"),
                "Test failures never change the raw-training representative");
        check(runRows.stream().allMatch(row -> row.get("quality_mode").equals("SAMPLE_ESTIMATE_NOT_FULL_ARCHIVE_QUALITY")
                && row.get("quality_sample_size").equals("2") && row.get("quality_evaluation_count").equals("4")),
                "Mixed-case quality metadata preserves the sampled unique-pair domain and evaluation denominator");
        List<Map<String, String>> summary = records(f.config.testRoot.resolve("summary.csv"));
        Map<String, String> alternate = find(summary, "setting_id", "d2-c3", "metric", "representative_mean_test_objective");
        check(alternate.get("run_count").equals("2") && alternate.get("finite_run_count").equals("1")
                && alternate.get("nonfinite_run_count").equals("1") && alternate.get("all_run_mean").equals("Infinity")
                && alternate.get("all_run_sample_sd").equals("NA") && alternate.get("failure_run_count").equals("1"),
                "Mixed failure is not dropped from all-run summaries");
        near(Double.parseDouble(alternate.get("finite_only_mean")), 131, "Finite-only description is explicitly separate");
        Map<String, String> baseline = find(summary, "setting_id", "d4-c3", "metric", "representative_mean_test_objective");
        near(Double.parseDouble(baseline.get("all_run_mean")), 111, "Finite baseline run-level mean");
        near(Double.parseDouble(baseline.get("all_run_sample_sd")), Math.sqrt(200), "Finite baseline sample SD");
        List<Map<String, String>> differences = records(f.config.testRoot.resolve("paired_differences.csv"));
        Map<String, String> unavailable = find(differences, "setting_id", "d2-c3", "run_id", "0",
                "metric", "representative_mean_test_objective");
        Map<String, String> available = find(differences, "setting_id", "d2-c3", "run_id", "1",
                "metric", "representative_mean_test_objective");
        check(unavailable.get("setting_minus_baseline").equals("NA"), "Nonfinite mixed pair has no invented difference");
        near(Double.parseDouble(available.get("setting_minus_baseline")), 10, "Finite run-ID paired descriptive difference");
        String before = DcFiles.hash(f.config.testRoot.resolve("manifest.properties"));
        Evaluator unused = new Evaluator(f.config, 0, true);
        Projection unusedProjection = new Projection();
        DcTest.run(parse(f, "--resume"), unused, unusedProjection);
        check(unused.calls == 0 && unusedProjection.collections == 0
                && before.equals(DcFiles.hash(f.config.testRoot.resolve("manifest.properties"))),
                "Complete mixed-status study resume is verified without simulation or mutation");
    }

    private static void finiteConditionalStatuses(Path root) throws IOException {
        Fixture f = fixture(root, "finite-conditional-quality");
        Evaluator evaluator = new Evaluator(f.config, 0, false, true);
        DcTest.run(f.config, evaluator, new Projection());
        check(evaluator.calls == 28, "Partially failed quality samples cause no replacement evaluations");
        Properties manifest = DcFiles.read(f.config.testRoot.resolve("manifest.properties"));
        check(manifest.getProperty("completed-case-quality-failed-rule-count").equals("4"),
                "Failed-rule count is separate from failure-inclusive mean and finite-conditional descriptions");
        for (DcTest.CaseInput input : f.inputs.cases) {
            String key = input.setting.id() + ".run." + input.run;
            Path directory = DcFiles.mapped(f.config.testRoot, manifest.getProperty(key + ".path"));
            Properties result = DcFiles.read(directory.resolve("manifest.properties"));
            List<Map<String, String>> sample = records(directory.resolve("quality_sample.csv"));
            List<String> actualIds = new ArrayList<>();
            List<Double> finiteMeans = new ArrayList<>();
            for (Map<String, String> row : sample) {
                actualIds.add(row.get("candidate_id"));
                double mean = Double.parseDouble(row.get("mean_validation_objective"));
                if (Double.isFinite(mean)) { finiteMeans.add(mean); }
            }
            check(actualIds.equals(ids(DcTest.qualitySample(input.archive.candidates(), 2,
                    f.config.qualitySamplingSeed + input.run))), "Sample draw remains exactly the pre-validation draw");
            check(sample.size() == 2 && finiteMeans.size() == 1
                    && result.getProperty("quality_mean_validation_objective").equals("Infinity")
                    && result.getProperty("quality_failed_rule_count").equals("1")
                    && result.getProperty("quality_failure_count").equals("1")
                    && result.getProperty("quality_finite_rule_count").equals("1")
                    && result.getProperty("quality_finite_instance_count").equals("3"),
                    "Partial validation failure preserves sample size and each rule/instance denominator");
            near(Double.parseDouble(result.getProperty("quality_finite_conditional_rule_mean")), finiteMeans.get(0),
                    "Finite-conditional mean uses only complete finite rule means");
            near(Double.parseDouble(result.getProperty("quality_finite_conditional_rule_median")), finiteMeans.get(0),
                    "Finite-conditional median is explicitly separate from the failure-inclusive estimator");
            near(Double.parseDouble(result.getProperty("common_coverage_all_retained")), 4.0 / 6561,
                    "Validation failures cannot filter all-retained common coverage");
        }
        check(DcFiles.read(f.config.testRoot.resolve("results_guide.properties"))
                .getProperty("quality-finite-conditional").contains("not an unconditional sample estimator"),
                "Conditional summaries cannot be relabelled unconditional/full-archive estimates");
    }

    private static void malformedInputs(Path root) throws IOException {
        for (String[] bad : new String[][]{{"cell-capacity", "2"}, {"grid-cells", "10"},
                {"native-retained-members", "5"}, {"fitness-evaluations", "15"}, {"gp-seed", "1"},
                {"method", "QDGP"}, {"status", "RUNNING"}, {"best-training-fitness", "0"}}) {
            Fixture f = fixture(root, "bad-" + bad[0]);
            changeRun(f, bad[0], bad[1]);
            preflightRejected(f, "Wrong training identity/count/control " + bad[0]);
        }
        Fixture missing = fixture(root, "missing-artifact");
        changeRun(missing, "data.archive_curve.csv.sha256", null);
        preflightRejected(missing, "Missing declared required artifact");
        Fixture badHash = fixture(root, "bad-artifact-hash");
        Path run = firstTrainingRun(badHash);
        Files.write(run.resolve("job.0.out.stat"), Arrays.asList("changed"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        preflightRejected(badHash, "Bad source artifact hash");
        Fixture nativeCell = fixture(root, "bad-native-cell");
        Path nativePath = firstTrainingRun(nativeCell).resolve("job.0.out.csv");
        List<String> lines = Files.readAllLines(nativePath, StandardCharsets.UTF_8);
        String[] fields = lines.get(1).split(",", -1);
        fields[5] = "1";
        lines.set(1, String.join(",", fields));
        Files.write(nativePath, lines, StandardCharsets.UTF_8);
        changeRun(nativeCell, "data.job.0.out.csv.sha256", DcFiles.hash(nativePath));
        preflightRejected(nativeCell, "Native CSV geometry checked, not just its hash");
        Fixture params = fixture(root, "changed-original-parameters");
        Files.write(params.config.paramsPath, Arrays.asList("extra-fixture-input=changed"),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        preflightRejected(params, "Original resolved parameter input changed after training");
        Fixture configuration = fixture(root, "changed-training-controls");
        Evaluator e = new Evaluator(configuration.config, 0); Projection b = new Projection();
        reject(() -> DcTest.run(parse(configuration, "--set", "generations=3"), e, b), "Training changes not allowed in test overrides");
        reject(() -> DcTest.run(parse(configuration, "--set", "quality-sample=201"), e, b), "Sample estimate cap stays at 200");
        reject(() -> parse(configuration, "--set", "test-output-root=" + configuration.config.trainingRoot.resolve("nested")),
                "Test output cannot be nested in training input");
        check(e.calls == 0 && b.collections == 0 && !Files.exists(configuration.config.testRoot),
                "All training validation precedes expensive work and output creation");
    }

    private static void changedInputDuringEvaluation(Path root) throws IOException {
        Fixture f = fixture(root, "input-mutated-mid-test");
        Evaluator evaluator = new Evaluator(f.config, 0);
        evaluator.mutate = firstTrainingRun(f).resolve("generations.csv");
        reject(() -> DcTest.run(f.config, evaluator, new Projection()), "Before/after immutable input verification");
        check(DcFiles.read(f.config.testRoot.resolve("manifest.properties")).getProperty("status").equals("FAILED"),
                "Input mutation never produces successful test metadata");
    }

    private static void corruptCompletedOutput(Fixture f) throws IOException {
        Properties manifest = DcFiles.read(f.config.testRoot.resolve("manifest.properties"));
        String rootHash = DcFiles.hash(f.config.testRoot.resolve("manifest.properties"));
        Path run = DcFiles.mapped(f.config.testRoot, manifest.getProperty("d2-c3.run.0.path"));
        Files.write(run.resolve("member_projection.csv"), Arrays.asList("changed"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Evaluator e = new Evaluator(f.config, 0); Projection b = new Projection();
        reject(() -> DcTest.run(parse(f, "--resume"), e, b), "Corrupted COMPLETE case is fatal, not re-evaluated");
        check(e.calls == 0 && b.collections == 0 && rootHash.equals(DcFiles.hash(f.config.testRoot.resolve("manifest.properties"))),
                "Corrupt completed outputs cannot trigger reselection or overwrite");
    }

    static final class Evaluator implements DcTest.EvaluationService {
        final DcConfiguration config;
        final int stopAt;
        final boolean mixed;
        final boolean partialQuality;
        int calls;
        Path mutate;
        Evaluator(DcConfiguration config, int stopAt) { this(config, stopAt, false); }
        Evaluator(DcConfiguration config, int stopAt, boolean mixed) { this(config, stopAt, mixed, false); }
        Evaluator(DcConfiguration config, int stopAt, boolean mixed, boolean partialQuality) {
            this.config = config; this.stopAt = stopAt; this.mixed = mixed; this.partialQuality = partialQuality;
        }
        @Override public String identity() {
            return partialQuality ? "CHECKS_ONLY_PARTIAL_RULE_FAILURES_V1"
                    : mixed ? "CHECKS_ONLY_MIXED_OUTCOMES_V1" : "CHECKS_ONLY_INJECTED_OUTCOMES_V1";
        }
        @Override public DcTest.Outcome evaluate(ArchiveCandidate candidate, long seed) throws IOException {
            calls++;
            if (calls == stopAt) { throw new IOException("Injected interruption"); }
            if (mutate != null) {
                Files.write(mutate, Arrays.asList("mutated"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                mutate = null;
            }
            String library = candidate.source().getParent().getFileName().toString();
            boolean fail = !mixed || library.equals("d2-c3-run-0");
            double offset = mixed ? (library.startsWith("d2-c3-") ? 10 : 0) + (library.endsWith("-1") ? 20 : 0) : 0;
            if (seed >= config.testSeed && seed < config.testSeed + config.testInstances) {
                check(candidate.ordinal() == 1, "Evaluator never sees a reselected representative");
                return fail && seed == config.testSeed + 1 ? new DcTest.Outcome(Double.POSITIVE_INFINITY,
                        "synthetic held-out failure, \"kept\"\nno reselection")
                        : new DcTest.Outcome(100 + seed - config.testSeed + offset, "");
            }
            check(seed >= config.validationSeed && seed < config.validationSeed + config.validationInstances,
                    "Only independent common validation/test seeds used");
            check(candidate.hasFiniteStoredFitness(), "Stored failures must not be sampled for quality");
            if (partialQuality) {
                int run = Integer.parseInt(candidate.source().getFileName().toString().split("\\.")[1]);
                String failedPair = DcTest.qualitySample(candidates(candidate.source()), config.qualitySample,
                        config.qualitySamplingSeed + run).get(0).treeHash();
                fail = candidate.treeHash().equals(failedPair);
            }
            return fail && seed == config.validationSeed + 1 ? new DcTest.Outcome(Double.POSITIVE_INFINITY, "synthetic validation failure")
                    : new DcTest.Outcome(10 + candidate.ordinal() + offset, "");
        }
    }

    static final class Projection implements DcTest.ProjectionFactory {
        final String identity;
        int collections, coordinates;
        boolean corruptReplay;
        Projection() { this("CHECKS_ONLY_COMMON_BANK_V1"); }
        Projection(String identity) { this.identity = identity; }
        @Override public String identity() { return identity; }
        @Override public DcTest.ProjectionService collect(DcConfiguration config) {
            collections++;
            return new Frozen(config.commonDescriptorSeed);
        }
        private final class Frozen implements DcTest.ProjectionService {
            private final long seed;
            Frozen(long seed) { this.seed = seed; }
            @Override public double[] coordinates(ArchiveCandidate candidate) {
                coordinates++;
                if (candidate.ordinal() == 5) { throw new ArithmeticException("synthetic undefined projection, \"kept\"\nall retained"); }
                double[] digits = new double[8];
                digits[3] = candidate.ordinal() % 3; digits[7] = candidate.ordinal() / 3;
                return digits;
            }
            @Override public void write(Path path) throws IOException {
                Files.write(path, Arrays.asList("CHECKS_ONLY_FROZEN_D4_C3", "independent-seed=" + seed),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                if (corruptReplay) {
                    Files.write(path, Arrays.asList("CORRUPTED_REPLAY"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                }
            }
            @Override public long simulationCount() { return 0; }
        }
    }

    private static final class Fixture {
        final Path configPath;
        final DcConfiguration config;
        final DcTest.Inputs inputs;
        Fixture(Path configPath, DcConfiguration config, DcTest.Inputs inputs) {
            this.configPath = configPath; this.config = config; this.inputs = inputs;
        }
    }

    private static Fixture fixture(Path parent, String name) throws IOException {
        Path root = Files.createDirectory(parent.resolve(name));
        Path params = root.resolve("fixture.params");
        Files.write(params, Arrays.asList(
                "gp.koza.xover.source.0=mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.ArchiveParentSelection",
                "gp.koza.mutate.source.0=mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.ArchiveParentSelection",
                "breed.reproduce.source.0=mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD.ArchiveParentSelection",
                "eval.problem.eval-model.rotate-sim-seed=false", "number-accept=1",
                "random-parent-selection-probability=0.5", "penalty-level=0",
                "pop.subpop.0.species.pipe.source.0.prob=0.85",
                "pop.subpop.0.species.pipe.source.1.prob=0.15", "pop.subpop.0.species.pipe.source.2.prob=0"),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Properties p = new Properties();
        p.setProperty("params", params.toString());
        p.setProperty("training-output-root", root.resolve("training").toString());
        p.setProperty("test-output-root", root.resolve("test").toString());
        p.setProperty("settings", "2:3,4:3"); p.setProperty("runs", "2");
        for (String[] option : new String[][]{{"population", "8"}, {"generations", "2"}, {"elitism", "1"},
                {"recorded-jobs", "4"}, {"warmup-jobs", "0"}, {"machines", "4"}, {"utilisation", ".85"},
                {"due-factor", "1.5"}, {"descriptor-jobs", "4"}, {"descriptor-warmup", "0"},
                {"snapshot-interval", "1"}, {"validation-instances", "2"}, {"test-instances", "3"},
                {"quality-sample", "2"}, {"gp-seed-base", "12000"}, {"training-seed-base", "2000"},
                {"descriptor-seed-base", "3000"}, {"sampling-seed-base", "4000"},
                {"common-descriptor-seed", "5000"}, {"common-sampling-seed", "6000"},
                {"validation-seed", "7000"}, {"test-seed", "9000"}, {"quality-sampling-seed", "11000"}}) {
            p.setProperty(option[0], option[1]);
        }
        Path configPath = root.resolve("fixture.properties");
        DcFiles.write(configPath, p, false);
        DcConfiguration config = DcConfiguration.parse(new String[]{"--config", configPath.toString()});
        Files.createDirectory(config.trainingRoot);
        DcFiles.write(config.trainingRoot.resolve("resolved-study.properties"), config.properties(), false);
        String fingerprint = DcTraining.fingerprint(config);
        Properties manifest = new Properties();
        manifest.setProperty("protocol", DcConfiguration.PROTOCOL); manifest.setProperty("status", "COMPLETE");
        manifest.setProperty("study-hash", fingerprint); manifest.setProperty("scenario", config.scenario());
        manifest.setProperty("num-runs", "2"); manifest.setProperty("num-settings", "2");
        manifest.setProperty("data.resolved-study.properties.sha256", DcFiles.hash(config.trainingRoot.resolve("resolved-study.properties")));
        for (DcConfiguration.Setting setting : config.settings) {
            for (int run = 0; run < 2; run++) {
                Path directory = Files.createDirectory(config.trainingRoot.resolve(setting.id() + "-run-" + run));
                writeArchive(directory, setting, run);
                for (String file : new String[]{"initial_population.stat", "snapshot_bank.csv", "training_descriptor.csv",
                        "generations.csv", "archive_curve.csv", "resolved.params"}) {
                    Files.write(directory.resolve(file), Arrays.asList("SYNTHETIC INPUT " + file + " run=" + run),
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                }
                Properties saved = new Properties();
                saved.setProperty("status", "COMPLETE"); saved.setProperty("study-hash", fingerprint);
                saved.setProperty("method", "QDGPf"); saved.setProperty("scenario", config.scenario());
                for (String[] field : new String[][]{{"d", "" + setting.d}, {"c", "" + setting.c}, {"run-id", "" + run},
                        {"grid-side", "" + setting.side()}, {"grid-cells", "" + setting.cells()}, {"cell-capacity", "1"},
                        {"nominal-evaluations", "16"}, {"fitness-evaluations", "16"}, {"manual-reference-simulations", "2"},
                        {"descriptor-reference-simulations", "2"}, {"total-training-simulations", "20"},
                        {"elapsed-training-seconds", "0.5"}, {"peak-used-heap-bytes", "4096"}, {"native-occupied-cells", "6"},
                        {"native-retained-members", "6"}, {"finite-stored-members", "4"}, {"best-training-fitness", "1.0"},
                        {"gp-seed", "" + (config.gpSeedBase + run)}, {"training-seed", "" + (config.trainingSeedBase + run)},
                        {"descriptor-seed", "" + (config.descriptorSeedBase + run)}, {"sampling-seed", "" + (config.samplingSeedBase + run)}}) {
                    saved.setProperty(field[0], field[1]);
                }
                for (String file : new String[]{"job." + run + ".out.stat", "job." + run + ".out.csv",
                        "initial_population.stat", "snapshot_bank.csv", "training_descriptor.csv", "generations.csv",
                        "archive_curve.csv", "resolved.params"}) {
                    saved.setProperty("data." + file + ".sha256", DcFiles.hash(directory.resolve(file)));
                }
                DcFiles.write(directory.resolve("manifest.properties"), saved, false);
                String key = setting.id() + ".run." + run;
                manifest.setProperty(key + ".path", directory.getFileName().toString());
                manifest.setProperty(key + ".manifest-sha256", DcFiles.hash(directory.resolve("manifest.properties")));
            }
        }
        DcFiles.write(config.trainingRoot.resolve("manifest.properties"), manifest, false);
        return new Fixture(configPath, config, DcTest.Inputs.read(config));
    }

    private static List<ArchiveCandidate> candidates(Path path) {
        double[] fitness = {10, 1, 1, 5, Double.POSITIVE_INFINITY, Double.MAX_VALUE};
        String[] seq = {"PT", "(+ PT 1)", "(+ PT 1)", "(+ PT 2)", "(+ PT 3)", "(+ PT 4)"};
        List<ArchiveCandidate> result = new ArrayList<>();
        for (int i = 0; i < fitness.length; i++) { result.add(new ArchiveCandidate(path, i, fitness[i], seq[i], "WIQ")); }
        return result;
    }

    private static void writeArchive(Path directory, DcConfiguration.Setting setting, int run) throws IOException {
        Path stat = directory.resolve("job." + run + ".out.stat");
        try (BufferedWriter writer = DcFiles.create(stat);
             BufferedWriter csv = DcFiles.create(directory.resolve("job." + run + ".out.csv"))) {
            writer.write("All individuals in GridMAP:\nGridMAP number: 6\n");
            DcFiles.row(csv, "candidate_id", "run_id", "d", "c", "generation", "seq_cell", "rout_cell",
                    "descriptor_digits_zero_based", "raw_training_fitness", "stored_comparison_score",
                    "birth_generation", "tree_hash", "sequencing", "routing");
            for (ArchiveCandidate a : candidates(stat)) {
                writer.write("Evaluated: true\nFitness: [" + a.storedFitness() + "]\nTree 0:\n"
                        + a.sequencing() + "\nTree 1:\n" + a.routing() + "\n");
                int[] digits = new int[2 * setting.d];
                int value = a.ordinal();
                for (int i = digits.length - 1; i >= setting.d; i--) { digits[i] = value % setting.c; value /= setting.c; }
                List<String> text = new ArrayList<>();
                for (int digit : digits) { text.add(Integer.toString(digit)); }
                DcFiles.row(csv, a.id(), run, setting.d, setting.c, 1, 0, a.ordinal(), String.join(";", text),
                        a.storedFitness(), a.storedFitness(), 0, a.treeHash(), a.sequencing(), a.routing());
            }
        }
    }

    private static DcConfiguration parse(Fixture fixture, String... tail) throws IOException {
        List<String> args = new ArrayList<>(Arrays.asList("--config", fixture.configPath.toString()));
        Collections.addAll(args, tail);
        return DcConfiguration.parse(args.toArray(new String[0]));
    }
    private static Set<String> hashes(List<ArchiveCandidate> candidates) {
        Set<String> hashes = new java.util.LinkedHashSet<>();
        for (ArchiveCandidate candidate : candidates) { hashes.add(candidate.treeHash()); }
        return hashes;
    }
    private static List<String> ids(List<ArchiveCandidate> candidates) {
        List<String> result = new ArrayList<>();
        for (ArchiveCandidate candidate : candidates) { result.add(candidate.id()); }
        return result;
    }
    private static Path firstTrainingRun(Fixture f) throws IOException {
        Properties root = DcFiles.read(f.config.trainingRoot.resolve("manifest.properties"));
        return DcFiles.mapped(f.config.trainingRoot, root.getProperty("d2-c3.run.0.path"));
    }
    private static void changeRun(Fixture f, String field, String value) throws IOException {
        Path directory = firstTrainingRun(f);
        Properties saved = DcFiles.read(directory.resolve("manifest.properties"));
        if (value == null) { saved.remove(field); } else { saved.setProperty(field, value); }
        DcFiles.write(directory.resolve("manifest.properties"), saved, true);
        Properties root = DcFiles.read(f.config.trainingRoot.resolve("manifest.properties"));
        root.setProperty("d2-c3.run.0.manifest-sha256", DcFiles.hash(directory.resolve("manifest.properties")));
        DcFiles.write(f.config.trainingRoot.resolve("manifest.properties"), root, true);
    }
    private static void preflightRejected(Fixture f, String label) throws IOException {
        Evaluator e = new Evaluator(f.config, 0); Projection b = new Projection();
        reject(() -> DcTest.run(f.config, e, b), label);
        check(e.calls == 0 && b.collections == 0 && !Files.exists(f.config.testRoot), "Invalid input precedes all expensive work/output: " + label);
    }
    private static List<Map<String, String>> records(Path path) throws IOException {
        String header;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { header = reader.readLine(); }
        String[] names = header.split(",", -1);
        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> row : DcTest.csv(path, header)) {
            check(row.size() == names.length, "Output column count");
            Map<String, String> record = new LinkedHashMap<>();
            for (int i = 0; i < names.length; i++) { record.put(names[i], row.get(i)); }
            result.add(record);
        }
        return result;
    }
    private static Map<String, String> find(List<Map<String, String>> rows, String... fields) {
        Map<String, String> result = null;
        for (Map<String, String> row : rows) {
            boolean match = true;
            for (int i = 0; i < fields.length; i += 2) { match &= fields[i + 1].equals(row.get(fields[i])); }
            if (match) { check(result == null, "Duplicate matching row"); result = row; }
        }
        check(result != null, "Missing row " + Arrays.toString(fields));
        return result;
    }
    private interface CheckedAction { void run() throws IOException; }
    private static void reject(CheckedAction action, String label) throws IOException {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected strict rejection: " + label);
    }
    private static void near(double actual, double expected, String label) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= 1e-12 * Math.max(1, Math.abs(expected)),
                label + ": expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String label) { if (!condition) { throw new AssertionError(label); } }
}
