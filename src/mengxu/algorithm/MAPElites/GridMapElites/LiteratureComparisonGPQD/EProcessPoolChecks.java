package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Standalone bounded fixture checks. No simulator, formal training or scientific output is run. */
public final class EProcessPoolChecks {
    private EProcessPoolChecks() { }
    private static final String MAIN = EProcessPoolChecks.class.getName();

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--request".equals(args[0])) {
            fixture(read(Path.of(args[1])));
            return;
        }
        if (args.length != 2) {
            throw new IllegalArgumentException("EProcessPoolChecks <moduleDir> <NEWfixtureRoot>");
        }
        Path module = Path.of(args[0]).toRealPath();
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectory(root);
        Path config = module.resolve("e-study.properties").toRealPath();
        checkOptions(config);
        EParallelOptions options = options(config, 5);
        checkConcurrency(root.resolve("five workers with spaces"), options);
        checkSingleWorker(root.resolve("one diagnostic worker"), options(config, 1));
        checkEmpty(root.resolve("empty batch"), options);
        checkFailure(root.resolve("nonzero drain"), options, false);
        checkFailure(root.resolve("validation drain"), options, true);
        checkDispatchFailure(root.resolve("factory drain"), options);
        checkLogPreservation(root.resolve("log preservation"), options);
        checkInterrupt(root.resolve("interrupted"), options);
        checkLeaseIdentity(root.resolve("lease identity"));
        checkParentExit(root.resolve("shutdown hook"), config, "shutdown");
        checkParentExit(root.resolve("parent halt"), config, "halt");
        checkClasspath(root);
        String report = "E PROCESS POOL CHECKS PASSED: actual five distinct JVM PIDs overlapped across 10 jobs;"
                + " max-active=5; ordered group barriers; sliding-window refill; isolated seeds/config/UTF-8;"
                + " nonzero and validation failure stop dispatch but drain; factory/log failures;"
                + " interruption cleanup; shutdown hook and parent-halt orphan prevention;"
                + " PID/start identity; spaces and ordered absolute classpaths. No simulations.\n";
        Files.writeString(root.resolve("checks-result.txt"), report, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        System.out.print(report);
    }

    private static EParallelOptions options(Path config, int workers) throws IOException {
        return EParallelOptions.parse(new String[]{"--workers", Integer.toString(workers),
                "--config", config.toString(), "--worker-heap", "64m", "--set", "runs=9",
                "--set", "methods=GP,QDGP", "--set", "regimes=fixed", "--set", "scenarios=T085",
                "--resume", "--dry-run"});
    }

    private static void checkOptions(Path file) throws IOException {
        EParallelOptions defaults = EParallelOptions.parse(new String[]{"--config", file.toString()});
        require(defaults.workers == 5 && defaults.workerHeap.equals("2g"), "approved five / 2g defaults");
        EConfiguration original = EConfiguration.parse(new String[]{"--config", file.toString()});
        require(defaults.config.properties().equals(original.properties()), "defaults leave scientific source unchanged");
        EParallelOptions one = options(file, 1);
        EConfiguration direct = EConfiguration.parse(new String[]{"--config", file.toString(),
                "--set", "runs=9", "--set", "methods=GP,QDGP", "--set", "regimes=fixed",
                "--set", "scenarios=T085", "--resume", "--dry-run"});
        require(one.config.properties().equals(direct.properties()) && one.config.resume && one.config.dryRun,
                "all source arguments preserved");
        for (String heap : new String[]{"32M", "2G"}) {
            require(EParallelOptions.parse(new String[]{"--config", file.toString(),
                    "--worker-heap", heap}).workerHeap.equals(heap.toLowerCase(java.util.Locale.ROOT)), "heap units");
        }
        for (int workers = 1; workers <= 5; workers++) {
            require(options(file, workers).workers == workers, "all diagnostic worker counts 1..5");
        }
        for (String[] bad : new String[][]{
                {"--workers", "0"}, {"--workers", "6"}, {"--workers", "-1"}, {"--workers"},
                {"--workers", "2", "--workers", "3"}, {"--worker-heap", "0g"},
                {"--worker-heap", "2t"}, {"--worker-heap", "-Xmx2g"},
                {"--worker-heap", "2g", "--worker-heap", "1g"}, {"--worker-heap"},
                {"--worker-heap", "99999999999999999999999g"}, {"--unknown"}}) {
            List<String> args = new ArrayList<>();
            args.add("--config"); args.add(file.toString());
            java.util.Collections.addAll(args, bad);
            boolean rejected = false;
            try { EParallelOptions.parse(args.toArray(new String[0])); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "invalid parallel arguments rejected: " + args);
        }
    }

    private static void checkConcurrency(Path directory, EParallelOptions options) throws Exception {
        Files.createDirectory(directory);
        Path snapshot = directory.resolve("worker configuration with spaces.properties");
        write(snapshot, options.config.properties());
        Path ready = Files.createDirectory(directory.resolve("first five ready"));
        List<EProcessPool.Work> work = new ArrayList<>();
        // Interleaving proves the LinkedHashMap grouping contract, rather than contiguous-input grouping.
        for (int i = 0; i < 7; i++) {
            work.add(new EProcessPool.Work("A" + i, "method/regime/scenario A"));
            if (i < 3) { work.add(new EProcessPool.Work("B" + i, "method/regime/scenario B")); }
        }
        Thread coordinator = Thread.currentThread();
        List<EProcessPool.Result> results = new ArrayList<>();
        AtomicBoolean groupACommitted = new AtomicBoolean();
        EProcessPool.Summary summary = EProcessPool.run(work, options, directory, (item, ordinal) -> {
            require(Thread.currentThread() == coordinator, "factory runs only on coordinator");
            if (item.key.startsWith("B")) {
                require(groupACommitted.get(), "next group cannot launch before every first-group completion");
            }
            int index = Integer.parseInt(item.key.substring(1));
            Properties request = request(item.key, directory, snapshot);
            request.setProperty("seed", Long.toString(options.config.gpSeed(new EConfiguration.Case(
                    EConfiguration.Method.GP, EConfiguration.Regime.fixed, EConfiguration.Scenario.T085, index))));
            request.setProperty("delay-ms", item.key.equals("A0") ? "1800" : "120");
            if (item.key.startsWith("A") && index < 5) {
                request.setProperty("ready-dir", ready.toString());
                request.setProperty("barrier-count", "5");
            }
            return prepare(directory, item, ordinal, request);
        }, result -> {
            require(Thread.currentThread() == coordinator, "completion runs only on coordinator");
            require(result.exitCode == 0, "fake child succeeds: " + result.job.work.key);
            Properties report = report(directory, result.job.work.key);
            verifyReport(report, options, snapshot);
            Files.writeString(directory.resolve(result.job.work.key + ".committed"), "committed",
                    StandardOpenOption.CREATE_NEW);
            results.add(result);
            if (results.size() == 7) { groupACommitted.set(true); }
        });
        require(summary.launched == 10 && summary.completed == 10 && summary.maxActive == 5, "10 jobs / five slots");
        Set<Long> firstPids = new HashSet<>();
        long lastFirstStart = Long.MIN_VALUE, firstFinish = Long.MAX_VALUE;
        long lastGroupAFinish = Long.MIN_VALUE, firstGroupBStart = Long.MAX_VALUE;
        long slowFinish = 0, sixthStart = Long.MAX_VALUE;
        Set<Long> seeds = new HashSet<>();
        List<long[]> intervals = new ArrayList<>();
        for (EProcessPool.Result result : results) {
            String key = result.job.work.key;
            int index = Integer.parseInt(key.substring(1));
            Properties report = report(directory, key);
            long start = Long.parseLong(report.getProperty("started-millis"));
            long finish = Long.parseLong(report.getProperty("finished-millis"));
            intervals.add(new long[]{start, 1});
            intervals.add(new long[]{finish, -1});
            if (key.startsWith("A")) {
                seeds.add(Long.parseLong(report.getProperty("seed")));
                lastGroupAFinish = Math.max(lastGroupAFinish, result.finishedMillis);
                if (index < 5) {
                    firstPids.add(result.pid);
                    lastFirstStart = Math.max(lastFirstStart, start);
                    firstFinish = Math.min(firstFinish, finish);
                }
                if (index == 0) { slowFinish = finish; }
                if (index == 5) { sixthStart = start; }
            } else { firstGroupBStart = Math.min(firstGroupBStart, result.startedMillis); }
            String log = Files.readString(result.job.consoleLog, StandardCharsets.UTF_8);
            require(log.contains("fixture " + key) && log.contains("UTF-8 fixture \u03bb"),
                    "separate, non-clobbered UTF-8 logs");
            require(result.pid == Long.parseLong(report.getProperty("pid")), "recorded actual child PID");
        }
        require(firstPids.size() == 5 && lastFirstStart < firstFinish, "five actual distinct PIDs overlap");
        intervals.sort((left, right) -> {
            int time = Long.compare(left[0], right[0]);
            return time != 0 ? time : Long.compare(left[1], right[1]);
        });
        int measuredActive = 0, measuredPeak = 0;
        for (long[] event : intervals) {
            measuredActive += (int) event[1];
            measuredPeak = Math.max(measuredPeak, measuredActive);
        }
        require(measuredPeak == 5, "actual worker lifetime intervals have peak exactly five");
        require(seeds.size() == 7, "seven distinct scientific run seeds preserved");
        require(sixthStart < slowFinish, "exits other than first worker free a slot without starvation");
        require(firstGroupBStart >= lastGroupAFinish, "hard group barrier includes all completion validation");
        checkMetadata(directory, "SUCCESS", 10, 5);
        require(Files.readAllLines(directory.resolve("processes.csv")).size() == 21, "START/EXIT CSV rows");
        System.out.println("Observed concurrency: " + firstPids.size() + " distinct overlapping PIDs " + firstPids
                + "; max-active=" + summary.maxActive + "; 7+3 jobs, all committed.");
    }

    private static void checkFailure(Path directory, EParallelOptions options, boolean validation) throws Exception {
        Files.createDirectory(directory);
        Path ready = Files.createDirectory(directory.resolve("ready"));
        Path snapshot = directory.resolve("snapshot.properties");
        write(snapshot, options.config.properties());
        List<EProcessPool.Result> exited = new ArrayList<>();
        Set<String> committed = new HashSet<>();
        int[] prepared = {0};
        Thread coordinator = Thread.currentThread();
        EProcessPool.BatchFailure caught = null;
        try {
            EProcessPool.run(work(9, "failure"), options, directory, (item, ordinal) -> {
                require(Thread.currentThread() == coordinator, "failure factory thread");
                prepared[0]++;
                Properties request = request(item.key, directory, snapshot);
                request.setProperty("ready-dir", ready.toString());
                request.setProperty("barrier-count", "5");
                request.setProperty("delay-ms", item.key.equals("job1") ? "50" : "700");
                if (!validation && item.key.equals("job1")) { request.setProperty("exit-code", "23"); }
                return prepare(directory, item, ordinal, request);
            }, result -> {
                require(Thread.currentThread() == coordinator, "failure completion thread");
                exited.add(result);
                if (validation && result.job.work.key.equals("job1")) {
                    throw new IOException("injected fixture validation failure");
                }
                if (result.exitCode == 0) { committed.add(result.job.work.key); }
            });
        } catch (EProcessPool.BatchFailure expected) { caught = expected; }
        require(caught != null && caught.summary.launched == 5 && caught.summary.completed == 5
                && prepared[0] == 5, "first observed failure stops queue but drains five workers");
        require(exited.size() == 5 && committed.size() == 4, "every exit callback, four successes can commit");
        require(caught.getMessage().contains("console:") && caught.getMessage().contains("job1"), "actionable failure/log path");
        for (EProcessPool.Result result : exited) { require(!alive(result.pid), "failed batch has no remaining worker"); }
        checkMetadata(directory, "FAILED", 5, 5);
    }

    private static void checkSingleWorker(Path directory, EParallelOptions options) throws Exception {
        Files.createDirectory(directory);
        Path snapshot = directory.resolve("snapshot.properties");
        write(snapshot, options.config.properties());
        int[] completed = {0};
        EProcessPool.Summary summary = EProcessPool.run(work(3, "diagnostic"), options, directory, (item, ordinal) -> {
            require(completed[0] == ordinal - 1, "one-worker diagnostics do not prepare ahead");
            Properties request = request(item.key, directory, snapshot);
            request.setProperty("delay-ms", "25");
            return prepare(directory, item, ordinal, request);
        }, result -> {
            require(result.exitCode == 0, "serial diagnostic child succeeds");
            completed[0]++;
        });
        require(summary.completed == 3 && summary.maxActive == 1, "diagnostic --workers 1 is serial");
        checkMetadata(directory, "SUCCESS", 3, 1);
    }

    private static void checkEmpty(Path directory, EParallelOptions options) throws IOException {
        Files.createDirectory(directory);
        EProcessPool.Summary summary = EProcessPool.run(List.of(), options, directory,
                (item, ordinal) -> { throw new AssertionError("empty batch must not call factory"); },
                result -> { throw new AssertionError("empty batch must not call completion"); });
        require(summary.launched == 0 && summary.completed == 0 && summary.maxActive == 0, "empty batch");
        checkMetadata(directory, "SUCCESS", 0, 0);
    }

    private static void checkDispatchFailure(Path directory, EParallelOptions options) throws Exception {
        Files.createDirectory(directory);
        Path snapshot = directory.resolve("snapshot.properties");
        write(snapshot, options.config.properties());
        int[] prepared = {0}, completed = {0};
        EProcessPool.BatchFailure caught = null;
        try {
            EProcessPool.run(work(8, "dispatch"), options, directory, (item, ordinal) -> {
                prepared[0]++;
                if (ordinal == 3) { throw new IOException("injected factory failure"); }
                Properties request = request(item.key, directory, snapshot);
                request.setProperty("delay-ms", "100");
                return prepare(directory, item, ordinal, request);
            }, result -> { require(result.exitCode == 0, "prepared workers finish"); completed[0]++; });
        } catch (EProcessPool.BatchFailure expected) { caught = expected; }
        require(caught != null && caught.summary.launched == 2 && completed[0] == 2 && prepared[0] == 3,
                "factory failure drains previously launched children");
    }

    private static void checkLogPreservation(Path directory, EParallelOptions options) throws Exception {
        Files.createDirectory(directory);
        Path request = directory.resolve("request.properties");
        write(request, new Properties());
        Path log = directory.resolve("existing console.log");
        Files.writeString(log, "do not clobber", StandardOpenOption.CREATE_NEW);
        boolean failed = false;
        try {
            EProcessPool.run(work(1, "log"), options, directory,
                    (item, ordinal) -> new EProcessPool.Job(item, MAIN, request, log),
                    result -> { throw new AssertionError("A log collision must not launch"); });
        } catch (EProcessPool.BatchFailure expected) {
            failed = expected.summary.launched == 0 && expected.summary.completed == 0;
        }
        require(failed && Files.readString(log).equals("do not clobber"), "pre-existing logs fail explicitly, never truncate");
    }

    private static void checkInterrupt(Path directory, EParallelOptions options) throws Exception {
        Files.createDirectory(directory);
        Path ready = Files.createDirectory(directory.resolve("ready"));
        Path snapshot = directory.resolve("snapshot.properties");
        write(snapshot, options.config.properties());
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        AtomicBoolean restored = new AtomicBoolean();
        List<Long> completedPids = new ArrayList<>();
        Path sentinelRequest = directory.resolve("sentinel.properties");
        Properties sentinel = new Properties();
        sentinel.setProperty("mode", "sentinel");
        sentinel.setProperty("ready-file", directory.resolve("sentinel.ready").toString());
        write(sentinelRequest, sentinel);
        Process unrelated = direct(sentinelRequest, directory.resolve("sentinel.log"), List.of());
        try {
            await(() -> Files.exists(directory.resolve("sentinel.ready")), 5000, "independent fixture sentinel");
            Thread coordinator = new Thread(() -> {
                try {
                    EProcessPool.run(work(9, "interrupt"), options, directory, (item, ordinal) -> {
                        Properties request = request(item.key, directory, snapshot);
                        request.setProperty("ready-dir", ready.toString());
                        request.setProperty("barrier-count", "5");
                        request.setProperty("delay-ms", "30000");
                        return prepare(directory, item, ordinal, request);
                    }, result -> completedPids.add(result.pid));
                    outcome.set(new AssertionError("Interrupted pool returned success"));
                } catch (Throwable failure) {
                    outcome.set(failure);
                    restored.set(Thread.currentThread().isInterrupted());
                }
            }, "fixture-interrupted-coordinator");
            coordinator.start();
            try {
                await(() -> count(ready) == 5 || outcome.get() != null, 12000, "five interruption fixtures ready");
                require(outcome.get() == null, "coordinator reached five workers: " + outcome.get());
                coordinator.interrupt();
                coordinator.join(10000);
                require(!coordinator.isAlive(), "interrupted coordinator bounded cleanup");
                require(outcome.get() instanceof InterruptedIOException && restored.get(), "interrupted IO / flag restored");
                require(completedPids.size() == 5, "interrupted children still get every exit callback");
                for (long pid : completedPids) { require(!alive(pid), "cancelled worker PID is gone"); }
                require(unrelated.isAlive(), "cancellation never kills an unrelated fixture process");
                checkMetadata(directory, "INTERRUPTED", 5, 5);
            } finally {
                if (coordinator.isAlive()) { coordinator.interrupt(); coordinator.join(10000); }
            }
        } finally { stop(unrelated); }
    }

    private static void checkLeaseIdentity(Path directory) throws Exception {
        Files.createDirectory(directory);
        Path request = directory.resolve("mismatch.properties");
        Properties values = new Properties();
        values.setProperty("mode", "lease-identity");
        write(request, values);
        long parent = ProcessHandle.current().pid();
        Instant wrongStart = ProcessHandle.current().info().startInstant().orElseThrow().minusSeconds(1);
        Path log = directory.resolve("mismatch.log");
        Process mismatch = direct(request, log, List.of("-D" + EWorkerLease.PARENT_PID + "=" + parent,
                "-D" + EWorkerLease.PARENT_START + "=" + wrongStart));
        try {
            require(mismatch.waitFor(5, TimeUnit.SECONDS) && mismatch.exitValue() != 0,
                    "live PID with wrong start instant is not accepted");
            require(Files.readString(log).contains("different start instant"), "explicit parent identity error");
            require(ProcessHandle.current().isAlive(), "identity check does not kill the referenced process");
        } finally { stop(mismatch); }
        try (EWorkerLease inactive = EWorkerLease.start()) { require(inactive != null, "non-pool inert lease"); }
    }

    private static void checkParentExit(Path directory, Path config, String exitMode) throws Exception {
        Files.createDirectory(directory);
        Path ready = Files.createDirectory(directory.resolve("ready"));
        Path request = directory.resolve("coordinator request.properties");
        Properties values = new Properties();
        values.setProperty("mode", "coordinator");
        values.setProperty("directory", directory.toString());
        values.setProperty("config", config.toString());
        values.setProperty("ready-dir", ready.toString());
        values.setProperty("exit-mode", exitMode);
        write(request, values);
        Process parent = direct(request, directory.resolve("coordinator.log"), List.of());
        List<ProcessHandle> children = new ArrayList<>();
        List<Instant> starts = new ArrayList<>();
        try {
            await(() -> count(ready) == 2 || !parent.isAlive(), 12000, exitMode + " fixtures ready");
            require(count(ready) == 2, "fixture parent launched two leased children; log="
                    + Files.readString(directory.resolve("coordinator.log")));
            try (java.util.stream.Stream<Path> paths = Files.list(ready)) {
                for (Path path : paths.toList()) {
                    List<String> identity = Files.readAllLines(path);
                    ProcessHandle child = ProcessHandle.of(Long.parseLong(identity.get(0))).orElseThrow();
                    Instant start = Instant.parse(identity.get(1));
                    require(child.info().startInstant().map(start::equals).orElse(false), "fixture child identity");
                    children.add(child); starts.add(start);
                }
            }
            require(parent.waitFor(8, TimeUnit.SECONDS), "fixture coordinator exits within bound");
            require(parent.exitValue() == (exitMode.equals("halt") ? 91 : 0), "fixture expected coordinator exit");
            await(() -> children.stream().noneMatch(ProcessHandle::isAlive), 8000, exitMode + " children terminated");
            if (exitMode.equals("halt")) {
                for (int i = 0; i < 2; i++) {
                    String log = Files.readString(directory.resolve("job" + i + ".console.log"));
                    require(log.contains("lease lost coordinator"), "worker explicitly reports lost coordinator");
                }
            }
        } finally {
            stop(parent);
            for (int i = 0; i < children.size(); i++) {
                ProcessHandle child = children.get(i);
                Instant start = starts.get(i);
                if (child.isAlive() && child.info().startInstant().map(start::equals).orElse(false)) {
                    child.destroyForcibly();
                }
            }
        }
    }

    private static void checkClasspath(Path root) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        String input = "" + java.io.File.pathSeparator + "relative classes with spaces"
                + java.io.File.pathSeparator + "libraries\\*" + java.io.File.pathSeparator
                + root + java.io.File.pathSeparator + "*";
        String[] result = EProcessPool.absoluteClasspath(input, cwd).split(
                java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1);
        require(result.length == 5 && result[0].equals(cwd.toString())
                && result[1].equals(cwd.resolve("relative classes with spaces").toString())
                && result[2].equals(cwd.resolve("libraries").toString() + "\\*")
                && result[3].equals(root.toString()) && result[4].equals(cwd + "\\*"),
                "empty, relative, wildcard and absolute classpath entries retain order");
    }

    @SuppressWarnings("try")
    private static void fixture(Properties request) throws Exception {
        String mode = request.getProperty("mode", "worker");
        if (mode.equals("coordinator")) { fixtureCoordinator(request); return; }
        if (mode.equals("sentinel")) {
            Files.writeString(Path.of(request.getProperty("ready-file")), Long.toString(ProcessHandle.current().pid()),
                    StandardOpenOption.CREATE_NEW);
            Thread.sleep(45000);
            return;
        }
        try (EWorkerLease lease = EWorkerLease.start()) {
            if (mode.equals("lease-identity")) { return; }
            String id = request.getProperty("id");
            long started = System.currentTimeMillis();
            System.out.println("fixture " + id + " PID " + ProcessHandle.current().pid());
            System.out.println("UTF-8 fixture \u03bb");
            System.err.println("merged stderr " + id);
            Properties report = new Properties();
            report.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            report.setProperty("seed", request.getProperty("seed"));
            report.setProperty("started-millis", Long.toString(started));
            report.setProperty("vm-arguments", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()));
            report.setProperty("max-memory", Long.toString(Runtime.getRuntime().maxMemory()));
            report.setProperty("working-directory", Path.of("").toAbsolutePath().normalize().toString());
            report.setProperty("java-class-path", System.getProperty("java.class.path"));
            report.setProperty("parent-pid", System.getProperty(EWorkerLease.PARENT_PID, ""));
            EConfiguration snapshot = EConfiguration.parse(new String[]{"--config", request.getProperty("config"),
                    "--resume", "--dry-run"});
            report.setProperty("population", Integer.toString(snapshot.population));
            report.setProperty("runs", Integer.toString(snapshot.runs));
            report.setProperty("resume", Boolean.toString(snapshot.resume));
            report.setProperty("dry-run", Boolean.toString(snapshot.dryRun));
            report.setProperty("absolute-population-preset", snapshot.populationParams.toString());
            report.setProperty("absolute-archive-preset", snapshot.archiveParams.toString());
            if (request.containsKey("ready-dir")) {
                Path ready = Path.of(request.getProperty("ready-dir"));
                Path pending = ready.resolve(id + ".writing");
                Files.writeString(pending, ProcessHandle.current().pid() + "\n"
                                + ProcessHandle.current().info().startInstant().orElseThrow() + "\n",
                        StandardOpenOption.CREATE_NEW);
                Files.move(pending, ready.resolve(id + ".ready"));
                int expected = Integer.parseInt(request.getProperty("barrier-count"));
                await(() -> count(ready) >= expected, 12000, "all fixture workers alive before releasing barrier");
            }
            Thread.sleep(Long.parseLong(request.getProperty("delay-ms", "0")));
            report.setProperty("finished-millis", Long.toString(System.currentTimeMillis()));
            write(Path.of(request.getProperty("report-file")), report);
            int code = Integer.parseInt(request.getProperty("exit-code", "0"));
            if (code != 0) { System.exit(code); }
        }
    }

    private static void fixtureCoordinator(Properties request) throws Exception {
        Path directory = Path.of(request.getProperty("directory"));
        Path ready = Path.of(request.getProperty("ready-dir"));
        EParallelOptions options = options(Path.of(request.getProperty("config")), 2);
        Path snapshot = directory.resolve("snapshot.properties");
        write(snapshot, options.config.properties());
        Thread exit = new Thread(() -> {
            try {
                await(() -> count(ready) == 2, 12000, "fixture child readiness");
                Thread.sleep(400);
                if ("halt".equals(request.getProperty("exit-mode"))) { Runtime.getRuntime().halt(91); }
                else { System.exit(0); }
            } catch (Exception failed) {
                failed.printStackTrace();
                Runtime.getRuntime().halt(92);
            }
        }, "fixture-parent-exit");
        exit.setDaemon(true);
        exit.start();
        EProcessPool.run(work(2, "orphan"), options, directory, (item, ordinal) -> {
            Properties child = request(item.key, directory, snapshot);
            child.setProperty("ready-dir", ready.toString());
            child.setProperty("barrier-count", "2");
            child.setProperty("delay-ms", "30000");
            return prepare(directory, item, ordinal, child);
        }, result -> { });
        throw new AssertionError("Fixture coordinator should exit before workers finish");
    }

    private static Properties request(String id, Path directory, Path snapshot) {
        Properties values = new Properties();
        values.setProperty("mode", "worker");
        values.setProperty("id", id);
        values.setProperty("seed", Integer.toString(id.hashCode()));
        values.setProperty("config", snapshot.toString());
        values.setProperty("report-file", directory.resolve(id + ".report.properties").toString());
        return values;
    }

    private static EProcessPool.Job prepare(Path directory, EProcessPool.Work item, int ordinal,
                                           Properties request) throws IOException {
        request.setProperty("ordinal", Integer.toString(ordinal));
        Path file = directory.resolve(item.key + ".request.properties");
        write(file, request);
        // This fixture stands in for coordinator-owned root mappings, persisted before launch.
        Files.writeString(directory.resolve(item.key + ".mapping"), file.toString(), StandardOpenOption.CREATE_NEW);
        return new EProcessPool.Job(item, MAIN, file, directory.resolve(item.key + ".console.log"));
    }

    private static List<EProcessPool.Work> work(int count, String group) {
        List<EProcessPool.Work> result = new ArrayList<>();
        for (int i = 0; i < count; i++) { result.add(new EProcessPool.Work("job" + i, group)); }
        return result;
    }

    private static void verifyReport(Properties report, EParallelOptions options, Path snapshot) throws IOException {
        String vm = report.getProperty("vm-arguments");
        require(vm.contains("-Xmx64m") && vm.contains("--add-opens=java.base/java.lang=ALL-UNNAMED")
                && !vm.contains("-javaagent") && !vm.contains("jdwp"), "explicit independent worker VM arguments");
        require(Long.parseLong(report.getProperty("max-memory")) <= 70L * 1024 * 1024, "worker heap cap");
        require(report.getProperty("working-directory").equals(Path.of("").toAbsolutePath().normalize().toString()),
                "same working directory as coordinator");
        require(report.getProperty("population").equals(Integer.toString(options.config.population))
                && report.getProperty("runs").equals("9")
                && report.getProperty("resume").equals("true") && report.getProperty("dry-run").equals("true"),
                "source options/flags arrive through caller snapshot");
        Properties saved = read(snapshot);
        require(Path.of(saved.getProperty("population-params")).isAbsolute()
                && Path.of(saved.getProperty("archive-params")).isAbsolute(), "absolute preset snapshot paths");
        require(report.getProperty("absolute-population-preset").equals(options.config.populationParams.toString())
                && report.getProperty("absolute-archive-preset").equals(options.config.archiveParams.toString()),
                "worker resolves original presets");
        require(report.getProperty("parent-pid").equals(Long.toString(ProcessHandle.current().pid())), "lease PID provided");
        for (String entry : report.getProperty("java-class-path").split(
                java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1)) {
            require(Path.of(entry).isAbsolute(), "absolute effective worker classpath");
        }
    }

    private static void checkMetadata(Path directory, String status, int count, int maxActive) throws IOException {
        Properties values = read(directory.resolve("execution.properties"));
        require(values.getProperty("status").equals(status)
                && values.getProperty("launched").equals(Integer.toString(count))
                && values.getProperty("completed").equals(Integer.toString(count))
                && values.getProperty("max-active").equals(Integer.toString(maxActive))
                && values.getProperty("worker-heap").equals("64m"), "complete execution metadata");
        require(values.containsKey("started-at") && values.containsKey("finished-at"), "execution start/end");
        for (int i = 1; i <= count; i++) {
            require(Long.parseLong(values.getProperty("process." + i + ".pid")) > 0
                    && values.containsKey("process." + i + ".exit-code")
                    && values.containsKey("process." + i + ".finished-millis"), "actual PID / exit metadata");
        }
    }

    private static Process direct(Path request, Path log, List<String> vmArguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
        command.add("-Xmx64m");
        command.add("-Dfile.encoding=UTF-8");
        command.addAll(vmArguments);
        command.add("-cp");
        command.add(EProcessPool.absoluteClasspath(System.getProperty("java.class.path"), Path.of("").toAbsolutePath()));
        command.add(MAIN); command.add("--request"); command.add(request.toString());
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        return builder.start();
    }

    private static Properties report(Path directory, String key) throws IOException {
        return read(directory.resolve(key + ".report.properties"));
    }

    private static Properties read(Path path) throws IOException {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { values.load(reader); }
        return values;
    }

    private static void write(Path path, Properties values) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            values.store(writer, "Bounded E process fixture; no scientific simulation");
        }
    }

    private static long count(Path directory) {
        try (java.util.stream.Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".ready")).count();
        }
        catch (IOException missing) { throw new IllegalStateException(missing); }
    }

    private static boolean alive(long pid) { return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); }

    private static void await(BooleanSupplier condition, long milliseconds, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) { throw new IOException("Timed out: " + description); }
            Thread.sleep(25);
        }
    }

    private static void stop(Process process) throws InterruptedException {
        if (process.isAlive()) { process.destroy(); }
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            require(process.waitFor(3, TimeUnit.SECONDS), "own fixture process terminated");
        }
    }

    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
