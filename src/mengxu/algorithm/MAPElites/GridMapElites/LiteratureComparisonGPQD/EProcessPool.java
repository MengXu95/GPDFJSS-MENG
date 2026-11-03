package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * A coordinator-thread-only, group-barrier pool of independent JVMs.
 * The caller owns scientific requests, snapshots and commit validation.
 */
public final class EProcessPool {
    private EProcessPool() { }

    public static final class Work {
        public final String key, group;
        public Work(String key, String group) {
            this.key = Objects.requireNonNull(key, "key");
            this.group = Objects.requireNonNull(group, "group");
        }
    }

    public static final class Job {
        public final Work work;
        public final String mainClass;
        public final Path requestFile, consoleLog;
        /** consoleLog must be outside any case attempt directory that will be sealed. */
        public Job(Work work, String mainClass, Path requestFile, Path consoleLog) {
            this.work = Objects.requireNonNull(work, "work");
            this.mainClass = Objects.requireNonNull(mainClass, "mainClass");
            this.requestFile = Objects.requireNonNull(requestFile, "requestFile");
            this.consoleLog = Objects.requireNonNull(consoleLog, "consoleLog");
        }
    }

    @FunctionalInterface public interface Factory {
        /** Called on the coordinator immediately before launch; ordinal is one-based. */
        Job prepare(Work work, int ordinal) throws IOException;
    }

    @FunctionalInterface public interface Completion {
        /** Called on the coordinator for every launched worker exit, even on failure. */
        void accept(Result result) throws IOException;
    }

    public static final class Result {
        public final Job job;
        public final int exitCode;
        public final long pid, startedMillis, finishedMillis;
        private Result(Running child) {
            job = child.job;
            exitCode = child.process.exitValue();
            pid = child.process.pid();
            startedMillis = child.startedMillis;
            finishedMillis = System.currentTimeMillis();
        }
    }

    public static final class Summary implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final int launched, completed, maxActive, failures;
        private Summary(int launched, int completed, int maxActive, int failures) {
            this.launched = launched;
            this.completed = completed;
            this.maxActive = maxActive;
            this.failures = failures;
        }
    }

    public static final class BatchFailure extends IOException {
        private static final long serialVersionUID = 1L;
        public final Summary summary;
        private BatchFailure(Summary summary, List<String> failures) {
            super("E process batch failed after draining " + summary.completed + "/" + summary.launched
                    + " workers:\n" + String.join("\n", failures));
            this.summary = summary;
        }
    }

    public static Summary run(List<Work> work, EParallelOptions options, Path executionDirectory,
                              Factory factory, Completion completion) throws IOException {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(completion, "completion");
        Path execution = executionDirectory.toRealPath();
        if (!Files.isDirectory(execution)) { throw new IOException("Execution directory must already exist: " + execution); }
        Map<String, List<Work>> groups = new LinkedHashMap<>();
        for (Work item : work) {
            Objects.requireNonNull(item, "work item");
            groups.computeIfAbsent(item.group, unused -> new ArrayList<>()).add(item);
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toAbsolutePath();
        if (!Files.isRegularFile(java)) { throw new IOException("Missing current-runtime Java executable: " + java); }
        String classpath = absoluteClasspath(System.getProperty("java.class.path", ""), cwd);
        String parentStart = ProcessHandle.current().info().startInstant()
                .orElseThrow(() -> new IOException("Coordinator start instant is unavailable")).toString();
        List<String> failures = new ArrayList<>();
        Map<Process, Running> active = new LinkedHashMap<>();
        int launched = 0, completed = 0, maxActive = 0;
        boolean interrupted = Thread.interrupted();
        boolean restoreInterrupt = interrupted;
        try (Registry registry = new Registry(); Metadata metadata = new Metadata(execution, options)) {
            if (interrupted) { failure(failures, "Coordinator interrupted before dispatch"); }
            for (List<Work> group : groups.values()) {
                int next = 0;
                while ((next < group.size() && failures.isEmpty()) || !active.isEmpty()) {
                    if (Thread.interrupted()) {
                        interrupted = true; restoreInterrupt = true;
                        failure(failures, "Coordinator interrupted; cancelling only its tracked worker PIDs");
                        registry.terminate();
                    }
                    Iterator<Running> iterator = active.values().iterator();
                    while (iterator.hasNext()) {
                        Running child = iterator.next();
                        if (child.process.isAlive()) { continue; }
                        Result result = new Result(child);
                        iterator.remove();
                        registry.remove(child.process);
                        completed++;
                        if (result.exitCode != 0) {
                            failure(failures, "Worker " + child.job.work.key + " PID " + result.pid
                                    + " exited " + result.exitCode + "; console: " + absolute(child.job.consoleLog));
                        }
                        try { completion.accept(result); }
                        catch (IOException | RuntimeException validationFailure) {
                            failure(failures, "Completion validation failed for " + child.job.work.key
                                    + " PID " + result.pid + "; console: " + absolute(child.job.consoleLog)
                                    + "; " + validationFailure);
                        }
                        try { metadata.completed(child, result, launched, completed, maxActive); }
                        catch (IOException metadataFailure) {
                            failure(failures, "Cannot record worker " + child.job.work.key + ": " + metadataFailure);
                        }
                    }
                    while (!interrupted && failures.isEmpty()
                            && next < group.size() && active.size() < options.workers) {
                        // Check every exited worker before refilling, not just the first/oldest one.
                        if (active.keySet().stream().anyMatch(process -> !process.isAlive() && process.exitValue() != 0)) {
                            break;
                        }
                        Work item = group.get(next++);
                        Path plannedLog = null;
                        try {
                            Job job = Objects.requireNonNull(factory.prepare(item, launched + 1), "factory job");
                            plannedLog = job.consoleLog.toAbsolutePath().normalize();
                            if (!job.work.key.equals(item.key) || !job.work.group.equals(item.group)
                                    || job.mainClass.isEmpty()) {
                                throw new IOException("Factory returned a mismatched/invalid job for " + item.key);
                            }
                            Path request = job.requestFile.toRealPath();
                            if (!Files.isRegularFile(request)) { throw new IOException("Missing request: " + request); }
                            Files.createDirectories(plannedLog.getParent());
                            Files.createFile(plannedLog);
                            List<String> command = new ArrayList<>();
                            Collections.addAll(command, java.toString(), "-Xmx" + options.workerHeap,
                                    "--add-opens=java.base/java.lang=ALL-UNNAMED", "-Dfile.encoding=UTF-8",
                                    "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                                    "-D" + EWorkerLease.PARENT_PID + "=" + ProcessHandle.current().pid(),
                                    "-D" + EWorkerLease.PARENT_START + "=" + parentStart,
                                    "-cp", classpath, job.mainClass, "--request", request.toString());
                            ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile())
                                    .redirectErrorStream(true).redirectOutput(plannedLog.toFile());
                            // Environment-injected VM options must not reintroduce agents or heap settings.
                            builder.environment().remove("JAVA_TOOL_OPTIONS");
                            builder.environment().remove("_JAVA_OPTIONS");
                            builder.environment().remove("JDK_JAVA_OPTIONS");
                            if (Thread.interrupted()) {
                                interrupted = true; restoreInterrupt = true;
                                failure(failures, "Coordinator interrupted before launch of " + item.key);
                                registry.terminate();
                                break;
                            }
                            long started = System.currentTimeMillis();
                            Process process = registry.launch(builder);
                            Running child = new Running(job, process, launched + 1, started);
                            active.put(process, child);
                            launched++;
                            maxActive = Math.max(maxActive, active.size());
                            metadata.launched(child, launched, completed, maxActive);
                        } catch (IOException | RuntimeException dispatchFailure) {
                            failure(failures, "Dispatch failed for " + item.key
                                    + (plannedLog == null ? "" : "; console: " + plannedLog) + ": " + dispatchFailure);
                        }
                    }
                    if (!active.isEmpty()) {
                        try { Thread.sleep(25); }
                        catch (InterruptedException stop) {
                            interrupted = true; restoreInterrupt = true;
                            failure(failures, "Coordinator interrupted; cancelling only its tracked worker PIDs");
                            registry.terminate();
                        }
                    }
                }
                if (!failures.isEmpty()) { break; }
            }
            if (Thread.interrupted()) { interrupted = true; restoreInterrupt = true; }
            Summary summary = new Summary(launched, completed, maxActive, failures.size());
            try {
                metadata.finished(interrupted ? "INTERRUPTED" : failures.isEmpty() ? "SUCCESS" : "FAILED",
                        summary, failures);
            } catch (IOException metadataFailure) {
                failure(failures, "Cannot finalize execution metadata: " + metadataFailure);
                summary = new Summary(launched, completed, maxActive, failures.size());
            }
            if (interrupted) {
                throw new InterruptedIOException("E coordinator interrupted after draining " + completed
                        + "/" + launched + " workers; " + String.join("; ", failures));
            }
            if (!failures.isEmpty()) { throw new BatchFailure(summary, failures); }
            return summary;
        } finally {
            if (restoreInterrupt) { Thread.currentThread().interrupt(); }
        }
    }

    private static String absolute(Path path) { return path.toAbsolutePath().normalize().toString(); }

    static String absoluteClasspath(String classpath, Path cwd) {
        List<String> entries = new ArrayList<>();
        for (String entry : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator), -1)) {
            boolean wildcard = entry.equals("*") || entry.endsWith("\\*") || entry.endsWith("/*");
            String prefix = wildcard ? entry.substring(0, entry.length() - 1) : entry;
            Path path = prefix.isEmpty() ? cwd : Path.of(prefix);
            if (!path.isAbsolute()) { path = cwd.resolve(path); }
            String absolute = path.toAbsolutePath().normalize().toString();
            entries.add(wildcard ? absolute + File.separator + "*" : absolute);
        }
        return String.join(File.pathSeparator, entries);
    }

    private static void failure(List<String> failures, String message) {
        failures.add(message);
        System.err.println("E PROCESS ERROR: " + message);
    }

    private static final class Running {
        final Job job;
        final Process process;
        final int ordinal;
        final long startedMillis;
        Running(Job job, Process process, int ordinal, long startedMillis) {
            this.job = job; this.process = process; this.ordinal = ordinal; this.startedMillis = startedMillis;
        }
    }

    private static final class Registry implements AutoCloseable {
        private final List<Process> children = new ArrayList<>();
        private final Thread hook;
        private boolean closed;
        Registry() {
            hook = new Thread(this::terminate, "e-process-pool-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
        }
        synchronized Process launch(ProcessBuilder builder) throws IOException {
            if (closed) { throw new IOException("Process pool is shutting down"); }
            Process process = builder.start();
            children.add(process);
            return process;
        }
        synchronized void remove(Process process) { children.remove(process); }
        void terminate() {
            List<Process> owned;
            synchronized (this) { closed = true; owned = new ArrayList<>(children); }
            for (Process child : owned) {
                if (child.isAlive()) { child.destroy(); }
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline && owned.stream().anyMatch(Process::isAlive)) {
                try { Thread.sleep(20); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
            for (Process child : owned) {
                if (child.isAlive()) { child.destroyForcibly(); }
            }
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline && owned.stream().anyMatch(Process::isAlive)) {
                try { Thread.sleep(20); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
            for (Process child : owned) {
                if (child.isAlive()) {
                    System.err.println("E PROCESS ERROR: tracked worker PID " + child.pid()
                            + " has not acknowledged forcible termination.");
                }
            }
        }
        @Override public void close() {
            terminate();
            try { Runtime.getRuntime().removeShutdownHook(hook); }
            catch (IllegalStateException shuttingDown) {
                System.err.println("E process pool closing during JVM shutdown; its worker shutdown hook remains active.");
            }
        }
    }

    private static final class Metadata implements AutoCloseable {
        private final BufferedWriter csv;
        private final Path file;
        private final Properties values = new Properties();
        Metadata(Path directory, EParallelOptions options) throws IOException {
            file = directory.resolve("execution.properties");
            Files.createFile(file);
            csv = Files.newBufferedWriter(directory.resolve("processes.csv"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                csv.write("event,ordinal,key,group,pid,started-millis,finished-millis,exit-code,worker-heap,request-file,console-log\n");
                csv.flush();
                values.setProperty("status", "RUNNING");
                values.setProperty("started-millis", Long.toString(System.currentTimeMillis()));
                values.setProperty("started-at", Instant.now().toString());
                values.setProperty("coordinator-pid", Long.toString(ProcessHandle.current().pid()));
                values.setProperty("coordinator-start", ProcessHandle.current().info().startInstant().get().toString());
                values.setProperty("workers", Integer.toString(options.workers));
                values.setProperty("worker-heap", options.workerHeap);
                counts(0, 0, 0);
                save();
            } catch (IOException | RuntimeException invalid) {
                csv.close();
                throw invalid;
            }
        }
        void launched(Running child, int launched, int completed, int maxActive) throws IOException {
            String prefix = "process." + child.ordinal + ".";
            values.setProperty(prefix + "key", child.job.work.key);
            values.setProperty(prefix + "group", child.job.work.group);
            values.setProperty(prefix + "pid", Long.toString(child.process.pid()));
            values.setProperty(prefix + "started-millis", Long.toString(child.startedMillis));
            values.setProperty(prefix + "start-instant",
                    child.process.info().startInstant().map(Instant::toString).orElse("unavailable"));
            values.setProperty(prefix + "worker-heap", values.getProperty("worker-heap"));
            values.setProperty(prefix + "request-file", absolute(child.job.requestFile));
            values.setProperty(prefix + "console-log", absolute(child.job.consoleLog));
            counts(launched, completed, maxActive);
            row("START", child, null);
            save();
        }
        void completed(Running child, Result result, int launched, int completed, int maxActive) throws IOException {
            String prefix = "process." + child.ordinal + ".";
            values.setProperty(prefix + "finished-millis", Long.toString(result.finishedMillis));
            values.setProperty(prefix + "exit-code", Integer.toString(result.exitCode));
            counts(launched, completed, maxActive);
            row("EXIT", child, result);
            save();
        }
        void finished(String status, Summary summary, List<String> failures) throws IOException {
            values.setProperty("status", status);
            values.setProperty("finished-millis", Long.toString(System.currentTimeMillis()));
            values.setProperty("finished-at", Instant.now().toString());
            values.setProperty("failures", Integer.toString(failures.size()));
            for (int i = 0; i < failures.size(); i++) { values.setProperty("failure." + (i + 1), failures.get(i)); }
            counts(summary.launched, summary.completed, summary.maxActive);
            save();
        }
        private void counts(int launched, int completed, int maxActive) {
            values.setProperty("launched", Integer.toString(launched));
            values.setProperty("completed", Integer.toString(completed));
            values.setProperty("max-active", Integer.toString(maxActive));
        }
        private void save() throws IOException {
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                values.store(writer, "Execution metadata only; not a scientific fingerprint");
            }
        }
        private void row(String event, Running child, Result result) throws IOException {
            Object[] fields = {event, child.ordinal, child.job.work.key, child.job.work.group, child.process.pid(),
                    child.startedMillis, result == null ? "" : result.finishedMillis,
                    result == null ? "" : result.exitCode, values.getProperty("worker-heap"),
                    absolute(child.job.requestFile), absolute(child.job.consoleLog)};
            for (int i = 0; i < fields.length; i++) {
                if (i != 0) { csv.write(','); }
                csv.write("\"" + fields[i].toString().replace("\"", "\"\"") + "\"");
            }
            csv.write('\n');
            csv.flush();
        }
        @Override public void close() throws IOException { csv.close(); }
    }
}
