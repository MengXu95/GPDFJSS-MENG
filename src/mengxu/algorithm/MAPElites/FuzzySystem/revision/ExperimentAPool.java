package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ExperimentAPool {
    static final class Entry {
        final ArchiveCandidate candidate;
        String status;
        String reason = "";
        String duplicateOf = "";
        SchedulingEvaluator.Evaluation validation;
        RevisionMetrics metrics;
        boolean pool;
        Entry(ArchiveCandidate candidate) { this.candidate = candidate; }
    }

    private ExperimentAPool() { }

    static List<RevisionMetrics> prepare(Path output, FinalArchiveReader.Archive archive,
                                        RevisionConfiguration config, SensitivityProbeSet probes,
                                        ExperimentAStudy.EvaluationService evaluator) throws IOException {
        List<Entry> all = new ArrayList<>();
        Map<String, Entry> unique = new LinkedHashMap<>();
        for (ArchiveCandidate candidate : archive.candidates()) {
            Entry entry = new Entry(candidate);
            all.add(entry);
            if (!candidate.hasFiniteStoredFitness()) { entry.status = "EXCLUDED_STORED_FAILURE"; continue; }
            Entry previous = unique.putIfAbsent(candidate.treeHash(), entry);
            if (previous != null) {
                entry.status = "EXCLUDED_DUPLICATE_EXPRESSION";
                entry.duplicateOf = previous.candidate.id();
                continue;
            }
            entry.validation = evaluator.evaluate(candidate, config.validationSeed, config.validationInstances);
            ExperimentAStudy.validateEvaluation(entry.validation, config.validationInstances);
            if (entry.validation.failedCount() > 0) {
                entry.status = "EXCLUDED_VALIDATION_FAILURE";
                entry.reason = entry.validation.failures().stream().filter(value -> !value.isEmpty()).findFirst().get();
            } else { entry.status = "VALIDATION_PASSED_NOT_CHARACTERISED"; }
            if (unique.size() % 50 == 0) {
                System.out.println(output.getFileName() + ": validation " + unique.size()
                        + " unique candidates, simulations=" + evaluator.count());
            }
        }
        List<Entry> ranked = unique.values().stream().filter(entry -> entry.validation.failedCount() == 0)
                .sorted(Comparator.comparingDouble((Entry entry) -> entry.validation.mean())
                        .thenComparingInt(entry -> entry.candidate.ordinal()))
                .collect(java.util.stream.Collectors.toList());
        List<RevisionMetrics> pool = new ArrayList<>();
        for (Entry entry : ranked) {
            if (pool.size() == config.poolSize && !config.allMetrics) { break; }
            try {
                entry.metrics = RevisionMetrics.compute(entry.candidate, probes, config.epsilon, entry.validation.mean());
                if (pool.size() < config.poolSize) {
                    entry.pool = true;
                    entry.status = "POOL";
                    pool.add(entry.metrics);
                } else { entry.status = "VALID_CHARACTERISED_NOT_IN_POOL"; }
            } catch (CandidateMetricException numericFailure) {
                entry.status = "EXCLUDED_METRIC_FAILURE";
                entry.reason = numericFailure.getMessage();
            }
        }
        try (RevisionCsv raw = new RevisionCsv(output.resolve("archive_members.csv"),
                    "candidate_id", "archive_ordinal", "tree_hash", "stored_fitness", "status",
                    "candidate_pool", "duplicate_of", "reason", "sequencing", "routing");
             RevisionCsv metrics = new RevisionCsv(output.resolve("candidate_metrics.csv"),
                    "candidate_id", "mean_validation_objective", "complexity", "sensitivity", "status", "reason")) {
            for (Entry entry : all) {
                raw.row(entry.candidate.id(), entry.candidate.ordinal(), entry.candidate.treeHash(),
                        entry.candidate.storedFitness(), entry.status, entry.pool, entry.duplicateOf,
                        entry.reason, entry.candidate.sequencing(), entry.candidate.routing());
                metrics.row(entry.candidate.id(), entry.validation == null ? "" : entry.validation.mean(),
                        entry.metrics == null ? "" : entry.metrics.complexity(),
                        entry.metrics == null ? "" : entry.metrics.sensitivity(), entry.status, entry.reason);
            }
        }
        if (pool.size() != config.poolSize) {
            throw new IllegalStateException("Only " + pool.size() + " fully eligible unique rules; required "
                    + config.poolSize + ". See exclusion CSVs. Do not silently shrink or fill using test outcomes.");
        }
        return pool;
    }
}
