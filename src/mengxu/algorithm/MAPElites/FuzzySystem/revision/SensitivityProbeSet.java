package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.weighted.WSPT;
import yimei.jss.rule.workcenter.basic.WIQ;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.state.SystemState;

public final class SensitivityProbeSet {
    private static final List<String> SUPPORTED_TERMINALS = Collections.unmodifiableList(Arrays.asList(
        "NIQ", "WIQ", "MWT", "PT", "NPT", "OWT", "WKR",
        "NOR", "W", "TIS", "rDD", "SL", "TRANT"
    ));

    /** Compatibility snapshot; use supportedTerminals() for the immutable policy. */
    @Deprecated
    public static final String[] TERMINALS = SUPPORTED_TERMINALS.toArray(new String[0]);

    public static List<String> supportedTerminals() { return SUPPORTED_TERMINALS; }

    static JobShopAttribute requireSupportedTerminal(String terminal) {
        if (!SUPPORTED_TERMINALS.contains(terminal)) {
            throw new IllegalArgumentException("Unsupported revision terminal: " + terminal
                    + "; supported terminals: " + SUPPORTED_TERMINALS);
        }
        JobShopAttribute attribute = JobShopAttribute.get(terminal);
        if (attribute == null) {
            throw new IllegalStateException("Configured probe terminal is unavailable: " + terminal);
        }
        return attribute;
    }

    public static final class Probe {
        private final String id;
        private final Map<String, Double> values;

        public Probe(String id, Map<String, Double> values) {
            if (id == null || values == null || values.isEmpty()) {
                throw new IllegalArgumentException("Probe ID and terminal values are required");
            }
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null
                        || !Double.isFinite(entry.getValue())) {
                    throw new IllegalArgumentException("Non-finite probe terminal in " + id);
                }
                requireSupportedTerminal(entry.getKey());
            }
            this.id = id;
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        public String id() { return id; }
        public Map<String, Double> values() { return values; }
    }

    private final List<Probe> sequencing;
    private final List<Probe> routing;

    public SensitivityProbeSet(List<Probe> sequencing, List<Probe> routing) {
        if (sequencing == null || routing == null || sequencing.isEmpty() || routing.isEmpty()) {
            throw new IllegalArgumentException("Both decision types need non-empty probe sets");
        }
        this.sequencing = Collections.unmodifiableList(new ArrayList<>(sequencing));
        this.routing = Collections.unmodifiableList(new ArrayList<>(routing));
    }

    public List<Probe> sequencing() { return sequencing; }
    public List<Probe> routing() { return routing; }

    public static SensitivityProbeSet collect(long instanceSeed, long samplingSeed,
                                             int machines, int jobs, int warmup,
                                             double utilisation, double dueFactor,
                                             int candidates, int situations) {
        if (candidates < 2 || candidates > machines || situations < 1) {
            throw new IllegalArgumentException("Invalid probe candidate/situation counts");
        }
        HeterogeneousSimulation seqSimulation = reference(
                instanceSeed, machines, jobs, warmup, utilisation, dueFactor);
        List<SequencingDecisionSituation> seq = seqSimulation.sequencingDecisionSituations(candidates);
        List<Probe> seqProbes = sampleSequencing(seq, candidates, situations, samplingSeed);
        HeterogeneousSimulation routSimulation = reference(
                instanceSeed, machines, jobs, warmup, utilisation, dueFactor);
        List<RoutingDecisionSituation> rout = routSimulation.routingDecisionSituations(candidates);
        List<Probe> routProbes = sampleRouting(rout, candidates, situations, samplingSeed ^ 0x5DEECE66DL);
        return new SensitivityProbeSet(seqProbes, routProbes);
    }

    private static HeterogeneousSimulation reference(long seed, int machines, int jobs,
                                                     int warmup, double utilisation, double dueFactor) {
        return HeterogeneousSimulation.standardMissing(seed,
                new WSPT(RuleType.SEQUENCING), new WIQ(RuleType.ROUTING),
                machines, jobs, warmup, utilisation, dueFactor);
    }

    private static List<Integer> eligible(List<Integer> indices, int required, long seed, String role) {
        if (indices.size() < required) {
            throw new IllegalArgumentException(role + " has only " + indices.size()
                    + " eligible snapshots; requested " + required + ". Increase probe-jobs explicitly.");
        }
        Collections.shuffle(indices, new Random(seed));
        return indices.subList(0, required);
    }

    private static List<Probe> sampleSequencing(List<SequencingDecisionSituation> situations,
                                              int candidates, int required, long seed) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < situations.size(); i++) {
            if (situations.get(i).getQueue().size() == candidates) { indices.add(i); }
        }
        List<Probe> result = new ArrayList<>();
        for (int index : eligible(indices, required, seed, "sequencing")) {
            SequencingDecisionSituation situation = situations.get(index);
            for (int j = 0; j < candidates; j++) {
                result.add(capture("seq-" + index + "-" + j, situation.getQueue().get(j),
                        situation.getWorkCenter(), situation.getSystemState()));
            }
        }
        return result;
    }

    private static List<Probe> sampleRouting(List<RoutingDecisionSituation> situations,
                                           int candidates, int required, long seed) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < situations.size(); i++) {
            if (situations.get(i).getQueue().size() == candidates) { indices.add(i); }
        }
        List<Probe> result = new ArrayList<>();
        for (int index : eligible(indices, required, seed, "routing")) {
            RoutingDecisionSituation situation = situations.get(index);
            for (int j = 0; j < candidates; j++) {
                OperationOption operation = situation.getQueue().get(j);
                result.add(capture("rout-" + index + "-" + j, operation,
                        operation.getWorkCenter(), situation.getSystemState()));
            }
        }
        return result;
    }

    private static Probe capture(String id, OperationOption operation, WorkCenter machine, SystemState state) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String terminal : SUPPORTED_TERMINALS) {
            values.put(terminal, requireSupportedTerminal(terminal).value(operation, machine, state));
        }
        return new Probe(id, values);
    }

    public static SensitivityProbeSet fromCharacterisations(PhenoCharacterisation[] characterisations) {
        if (characterisations == null || characterisations.length != 2
                || !(characterisations[0] instanceof SequencingPhenoCharacterisation)
                || !(characterisations[1] instanceof RoutingPhenoCharacterisation)) {
            throw new IllegalArgumentException("Sequencing and routing characterisations are required");
        }
        List<Probe> seq = new ArrayList<>();
        int index = 0;
        for (SequencingDecisionSituation situation :
                ((SequencingPhenoCharacterisation) characterisations[0]).decisionSituations) {
            int candidate = 0;
            for (OperationOption operation : situation.getQueue()) {
                seq.add(capture("seq-" + index + "-" + candidate++, operation,
                        situation.getWorkCenter(), situation.getSystemState()));
            }
            index++;
        }
        List<Probe> rout = new ArrayList<>();
        index = 0;
        for (RoutingDecisionSituation situation :
                ((RoutingPhenoCharacterisation) characterisations[1]).decisionSituations) {
            int candidate = 0;
            for (OperationOption operation : situation.getQueue()) {
                rout.add(capture("rout-" + index + "-" + candidate++, operation,
                        operation.getWorkCenter(), situation.getSystemState()));
            }
            index++;
        }
        return new SensitivityProbeSet(seq, rout);
    }
}
