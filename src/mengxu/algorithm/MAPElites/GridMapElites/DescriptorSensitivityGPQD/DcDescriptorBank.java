package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.rule.operation.weighted.WSPT;
import yimei.jss.rule.workcenter.basic.WIQ;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.state.SystemState;

/**
 * Frozen, without-replacement native ranking situations for a (d,c) descriptor study.
 * Each c gets two independent reference replays with the same simulation seed.
 * A role/c-specific java.util.Random shuffles eligible ordinals once, independently
 * of ECJ random streams and maxD; smaller d selects the same nested prefix.
 */
public final class DcDescriptorBank {
    private static final String[] TERMINALS = {
            "NIQ", "WIQ", "MWT", "PT", "NPT", "OWT", "WKR",
            "NOR", "W", "TIS", "rDD", "SL", "TRANT"
    };
    private static final String HEADER = "snapshot_id,role,c,position,candidate_index,reference_rank,"
            + "simulation_seed,sampling_seed,clock_time,job_id,operation_id,option_id,"
            + "work_center_id,candidate_work_center_id,ready_time,machine_ready_time,"
            + "machine_ids,machine_ready_times,reference_priority," + String.join(",", TERMINALS);

    private final int maxD;
    private final Map<Integer, Pool> pools;
    private final long simulationCount;

    private DcDescriptorBank(int maxD, Map<Integer, Pool> pools, long simulationCount) {
        this.maxD = maxD;
        this.pools = Collections.unmodifiableMap(new TreeMap<>(pools));
        this.simulationCount = simulationCount;
    }

    public static DcDescriptorBank collect(long simulationSeed, long samplingSeed,
                                           int machines, int jobs, int warmup,
                                           double utilisation, double dueFactor,
                                           int maxD, int... candidateCounts) {
        if (machines < 2 || jobs < 1 || warmup < 0 || maxD < 1
                || !Double.isFinite(utilisation) || utilisation <= 0.0 || utilisation >= 1.0
                || !Double.isFinite(dueFactor) || dueFactor <= 0.0
                || candidateCounts == null || candidateCounts.length == 0) {
            throw new IllegalArgumentException("Positive d/jobs/due factor, machines >= 2, warmup >= 0, "
                    + "0 < utilisation < 1 and candidate counts are required");
        }
        Map<Integer, Pool> pools = new TreeMap<>();
        for (int c : candidateCounts) {
            if (c < 2 || c > machines || pools.containsKey(c)) {
                throw new IllegalArgumentException("Candidate counts must be distinct and in [2,machines]: " + c);
            }
            side(maxD, c);
            pools.put(c, null);
        }
        long simulations = 0;
        for (int c : pools.keySet()) {
            HeterogeneousSimulation sequencing = reference(simulationSeed, machines, jobs, warmup,
                    utilisation, dueFactor);
            simulations++;
            List<SequencingDecisionSituation> seq = sequencing.sequencingDecisionSituations(c);
            List<Integer> seqEligible = new ArrayList<>();
            for (int i = 0; i < seq.size(); i++) {
                if (seq.get(i).getQueue().size() == c) {
                    seqEligible.add(i);
                }
            }
            int seqPoolSize = seqEligible.size();
            shuffle(seqEligible, maxD, samplingSeed, RuleType.SEQUENCING, c);
            List<Snapshot> frozenSeq = new ArrayList<>();
            for (int i = 0; i < maxD; i++) {
                int ordinal = seqEligible.get(i);
                frozenSeq.add(new Snapshot(copy(seq.get(ordinal)), null, ordinal, c, simulationSeed, samplingSeed));
            }

            HeterogeneousSimulation routing = reference(simulationSeed, machines, jobs, warmup,
                    utilisation, dueFactor);
            simulations++;
            List<RoutingDecisionSituation> rout = routing.routingDecisionSituations(c);
            List<Integer> routEligible = new ArrayList<>();
            for (int i = 0; i < rout.size(); i++) {
                if (rout.get(i).getQueue().size() == c) {
                    routEligible.add(i);
                }
            }
            int routPoolSize = routEligible.size();
            shuffle(routEligible, maxD, samplingSeed, RuleType.ROUTING, c);
            List<Snapshot> frozenRout = new ArrayList<>();
            for (int i = 0; i < maxD; i++) {
                int ordinal = routEligible.get(i);
                frozenRout.add(new Snapshot(null, copy(rout.get(ordinal)), ordinal, c, simulationSeed, samplingSeed));
            }
            pools.put(c, new Pool(frozenSeq, frozenRout, seqPoolSize, routPoolSize));
        }
        return new DcDescriptorBank(maxD, pools, simulations);
    }

    /**
     * Fresh native PCs, in sequencing/routing order. Queue lists, operation-option
     * priorities/ready times, work centers and state lists are detached with the native
     * clone policy; native job identities and simulation transfer-time context are retained.
     * No sorting or rule evaluation receives the bank's own queue or option objects.
     */
    public PhenoCharacterisation[] characterisations(int d, int c) {
        Pool pool = require(d, c);
        List<SequencingDecisionSituation> seq = new ArrayList<>();
        List<RoutingDecisionSituation> rout = new ArrayList<>();
        for (int i = 0; i < d; i++) {
            seq.add(copy(pool.sequencing.get(i).sequencing));
            rout.add(copy(pool.routing.get(i).routing));
        }
        return new PhenoCharacterisation[]{
                new SequencingPhenoCharacterisation(new WSPT(RuleType.SEQUENCING), seq),
                new RoutingPhenoCharacterisation(new WIQ(RuleType.ROUTING), rout)
        };
    }

    /** 2*d base-c digits: zero-based sequencing ranks followed by routing ranks. */
    public double[] coordinates(GPRule[] pair, int d, int c) {
        require(d, c);
        if (pair == null || pair.length != 2) {
            throw new IllegalArgumentException("Exactly one sequencing and one routing rule are required");
        }
        GPRule seq = null;
        GPRule rout = null;
        for (GPRule rule : pair) {
            if (rule == null || rule.getType() == null
                    || rule.getGPTree() == null || rule.getGPTree().child == null) {
                throw new IllegalArgumentException("A typed, nonempty GP rule is required");
            }
            if (rule.getType() == RuleType.SEQUENCING && seq == null) {
                seq = rule;
            } else if (rule.getType() == RuleType.ROUTING && rout == null) {
                rout = rule;
            } else {
                throw new IllegalArgumentException("Exactly one rule of each decision type is required");
            }
        }
        PhenoCharacterisation[] pcs = characterisations(d, c);
        int[] seqRanks = pcs[0].characterise(seq);
        int[] routRanks = pcs[1].characterise(rout);
        if (seqRanks.length != d || routRanks.length != d) {
            throw new IllegalStateException("Native descriptor has an unexpected number of ranks");
        }
        double[] result = new double[2 * d];
        for (int i = 0; i < d; i++) {
            result[i] = zeroBased(seqRanks[i], c);
            result[d + i] = zeroBased(routRanks[i], c);
        }
        return result;
    }

    /**
     * CREATE_NEW UTF-8 CSV, LF terminated, containing 2*d*c frozen candidate rows.
     * position and candidate_index are zero-based; reference_rank is the unmodified
     * one-based native reference rank. IDs use actual replay snapshot ordinals, not
     * sampled positions. Machine IDs are local to the exported work center.
     */
    public void write(Path output, int d, int c) throws IOException {
        Pool pool = require(d, c);
        if (output == null) {
            throw new IllegalArgumentException("A new descriptor CSV path is required");
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writer.write(HEADER);
            writer.write('\n');
            for (List<Snapshot> snapshots : java.util.Arrays.asList(pool.sequencing, pool.routing)) {
                for (int position = 0; position < d; position++) {
                    Snapshot snapshot = snapshots.get(position);
                    for (String row : snapshot.rows) {
                        writer.write(snapshot.id + "," + snapshot.role + "," + c + "," + position + "," + row);
                        writer.write('\n');
                    }
                }
            }
        }
    }

    public long simulationCount() {
        return simulationCount;
    }

    /** Number of all exact-c eligible snapshots before without-replacement sampling. */
    public int poolSize(RuleType role, int c) {
        if (role == null) {
            throw new IllegalArgumentException("A descriptor decision role is required");
        }
        Pool pool = require(1, c);
        return role == RuleType.SEQUENCING ? pool.sequencingPoolSize : pool.routingPoolSize;
    }

    /** Axis side c^d; the true two-role cell count is ((long) side(d,c))^2 = c^(2*d). */
    public static int side(int d, int c) {
        if (d < 1 || c < 2) {
            throw new IllegalArgumentException("Descriptor d must be positive and c must be at least two");
        }
        int result = 1;
        for (int i = 0; i < d; i++) {
            if (result > Integer.MAX_VALUE / c) {
                throw new IllegalArgumentException("Descriptor axis c^d exceeds the integer range");
            }
            result *= c;
        }
        return result;
    }

    private Pool require(int d, int c) {
        Pool pool = pools.get(c);
        if (d < 1 || d > maxD || pool == null) {
            throw new IllegalArgumentException("d must be in [1," + maxD + "] and c must be collected: " + d + "," + c);
        }
        return pool;
    }

    private static int zeroBased(int rank, int c) {
        if (rank < 1 || rank > c) {
            throw new IllegalStateException("Native rank outside [1,c]: " + rank);
        }
        return rank - 1;
    }

    private static HeterogeneousSimulation reference(long seed, int machines, int jobs, int warmup,
                                                     double utilisation, double dueFactor) {
        return HeterogeneousSimulation.standardMissing(seed, new WSPT(RuleType.SEQUENCING),
                new WIQ(RuleType.ROUTING), machines, jobs, warmup, utilisation, dueFactor);
    }

    private static void shuffle(List<Integer> eligible, int required, long samplingSeed, RuleType role, int c) {
        if (eligible.size() < required) {
            throw new IllegalArgumentException(role + " c=" + c + " has only " + eligible.size()
                    + " different exact-c snapshots; requested " + required + ". Increase descriptor jobs explicitly.");
        }
        long seed = samplingSeed + 0x9E3779B97F4A7C15L * c
                + (role == RuleType.SEQUENCING ? 0x53455155454E4345L : 0x524F5554494E4721L);
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        Collections.shuffle(eligible, new Random(seed ^ (seed >>> 31)));
    }

    private static List<OperationOption> options(List<OperationOption> source) {
        List<OperationOption> result = new ArrayList<>(source.size());
        for (OperationOption option : source) {
            result.add(option.clone());
        }
        return result;
    }

    private static SequencingDecisionSituation copy(SequencingDecisionSituation source) {
        return new SequencingDecisionSituation(options(source.getQueue()),
                source.getWorkCenter().clone(), source.getSystemState().clone());
    }

    private static RoutingDecisionSituation copy(RoutingDecisionSituation source) {
        return new RoutingDecisionSituation(options(source.getQueue()), source.getSystemState().clone());
    }

    private static void number(StringBuilder row, double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Non-finite frozen descriptor " + field + ": " + value);
        }
        row.append(',').append(Double.toString(value));
    }

    private static final class Pool {
        final List<Snapshot> sequencing;
        final List<Snapshot> routing;
        final int sequencingPoolSize;
        final int routingPoolSize;

        Pool(List<Snapshot> sequencing, List<Snapshot> routing, int sequencingPoolSize, int routingPoolSize) {
            this.sequencing = Collections.unmodifiableList(new ArrayList<>(sequencing));
            this.routing = Collections.unmodifiableList(new ArrayList<>(routing));
            this.sequencingPoolSize = sequencingPoolSize;
            this.routingPoolSize = routingPoolSize;
        }
    }

    private static final class Snapshot {
        final SequencingDecisionSituation sequencing;
        final RoutingDecisionSituation routing;
        final RuleType role;
        final String id;
        final List<String> rows;

        Snapshot(SequencingDecisionSituation sequencing, RoutingDecisionSituation routing, int ordinal,
                 int c, long simulationSeed, long samplingSeed) {
            this.sequencing = sequencing;
            this.routing = routing;
            role = sequencing != null ? RuleType.SEQUENCING : RuleType.ROUTING;
            id = (sequencing != null ? "seq-" : "rout-") + simulationSeed + "-c" + c + "-snapshot-" + ordinal;
            SequencingDecisionSituation seq = sequencing != null ? copy(sequencing) : null;
            RoutingDecisionSituation rout = routing != null ? copy(routing) : null;
            int[] ranks = seq != null ? new WSPT(role).priorValueOperation(seq)
                    : new WIQ(role).priorValueOperationMahcine(rout);
            List<OperationOption> queue = seq != null ? seq.getQueue() : rout.getQueue();
            SystemState state = seq != null ? seq.getSystemState() : rout.getSystemState();
            List<String> captured = new ArrayList<>();
            for (int i = 0; i < queue.size(); i++) {
                OperationOption option = queue.get(i);
                WorkCenter wc = seq != null ? seq.getWorkCenter() : option.getWorkCenter();
                zeroBased(ranks[i], c);
                StringBuilder row = new StringBuilder().append(i).append(',').append(ranks[i])
                        .append(',').append(simulationSeed).append(',').append(samplingSeed);
                number(row, state.getClockTime(), "clock_time");
                row.append(',').append(option.getJob().getId()).append(',').append(option.getOperation().getId())
                        .append(',').append(option.getOptionId()).append(',').append(wc.getId())
                        .append(',').append(option.getWorkCenter().getId());
                number(row, option.getReadyTime(), "ready_time");
                number(row, wc.getReadyTime(), "machine_ready_time");
                StringBuilder machineIds = new StringBuilder();
                StringBuilder machineTimes = new StringBuilder();
                for (int machine = 0; machine < wc.getNumMachines(); machine++) {
                    if (machine > 0) {
                        machineIds.append(';');
                        machineTimes.append(';');
                    }
                    double time = wc.getMachineReadyTime(machine);
                    if (!Double.isFinite(time)) {
                        throw new IllegalStateException("Non-finite frozen machine ready time");
                    }
                    machineIds.append(machine);
                    machineTimes.append(Double.toString(time));
                }
                row.append(',').append(machineIds).append(',').append(machineTimes);
                number(row, option.getPriority(), "reference_priority");
                for (String terminal : TERMINALS) {
                    JobShopAttribute attribute = JobShopAttribute.get(terminal);
                    if (attribute == null) {
                        throw new IllegalStateException("Missing native descriptor terminal: " + terminal);
                    }
                    number(row, attribute.value(option, wc, state), terminal);
                }
                captured.add(row.toString());
            }
            rows = Collections.unmodifiableList(captured);
        }
    }
}
