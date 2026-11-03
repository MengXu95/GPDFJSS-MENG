package mengxu.algorithm.MAPElites.GridMapElites.DescriptorSensitivityGPQD;

import ec.EvolutionState;
import ec.util.MersenneTwisterFast;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Bounded native replay checks, not ECJ training; zero args cleans its own temporary directory. */
public final class DcDescriptorChecks {
    private static final long SIMULATION_SEED = 763421L;
    private static final long SAMPLING_SEED = 982451653L;
    private static final String[] TERMINALS = {
            "NIQ", "WIQ", "MWT", "PT", "NPT", "OWT", "WKR",
            "NOR", "W", "TIS", "rDD", "SL", "TRANT"
    };

    private DcDescriptorChecks() { }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: DcDescriptorChecks [new-output-directory]");
        }
        boolean temporary = args.length == 0;
        Path output = temporary ? Files.createTempDirectory("dc-descriptor-checks-")
                : Files.createDirectory(Paths.get(args[0]).toAbsolutePath().normalize());
        try {
            runChecks(output);
            System.out.println("DC DESCRIPTOR CHECKS PASSED");
            if (!temporary) {
                System.out.println("Descriptor fixtures: " + output);
            }
        } finally {
            if (temporary) {
                try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(output)) {
                    for (Path file : files) {
                        Files.delete(file);
                    }
                }
                Files.delete(output);
            }
        }
    }

    /** The caller supplies an existing, empty fixture directory. */
    public static void runChecks(Path output) throws IOException {
        if (output == null || !Files.isDirectory(output)) {
            throw new IllegalArgumentException("An existing empty check-output directory is required");
        }
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(output)) {
            if (files.iterator().hasNext()) {
                throw new IllegalArgumentException("Check-output directory must be empty");
            }
        }
        checkGeometryAndProtocol();
        EvolutionState ecj = new EvolutionState();
        ecj.random = new MersenneTwisterFast[]{new MersenneTwisterFast(142857), new MersenneTwisterFast(271828)};
        MersenneTwisterFast[] controls = {
                new MersenneTwisterFast(142857), new MersenneTwisterFast(271828)
        };
        for (int i = 0; i < ecj.random.length; i++) {
            same(ecj.random[i].nextInt(), controls[i].nextInt(), "initial ECJ random stream");
        }

        DcDescriptorBank bank = collect(SIMULATION_SEED, SAMPLING_SEED, 5, 2, 3, 4);
        DcDescriptorBank paired = collect(SIMULATION_SEED, SAMPLING_SEED, 5, 4, 3, 2);
        require(bank.simulationCount() == 6L && paired.simulationCount() == 6L,
                "exactly two native replays per distinct c");
        for (int c : new int[]{2, 3, 4}) {
            for (RuleType role : RuleType.values()) {
                require(bank.poolSize(role, c) >= 5, "sufficient exact-c pool");
                same(bank.poolSize(role, c), paired.poolSize(role, c), "deterministic pool size");
            }
            Path first = output.resolve("bank-max-d5-c" + c + ".csv");
            Path second = output.resolve("paired-max-d5-c" + c + ".csv");
            bank.write(first, 5, c);
            paired.write(second, 5, c);
            sameFile(first, second, "paired collection and candidate-count argument order");
            checkRows(first, 5, c);
            checkNative(bank, first, 5, c);
        }
        checkSettings(bank, output);
        checkIsolation(bank, output);

        // maxD is not a sampling input: this is also the four-situation c=3 test-bank shape.
        DcDescriptorBank shorter = collect(SIMULATION_SEED, SAMPLING_SEED, 4, 3);
        require(shorter.simulationCount() == 2L, "one-c collection uses two native replays");
        Path shortened = output.resolve("separately-collected-d4-c3.csv");
        shorter.write(shortened, 4, 3);
        sameFile(shortened, output.resolve("selected-d4-c3.csv"), "separately collected maxD prefix");

        DcDescriptorBank resampled = collect(SIMULATION_SEED, SAMPLING_SEED + 1, 5, 3);
        Path resampledFile = output.resolve("resampled-d5-c3.csv");
        resampled.write(resampledFile, 5, 3);
        require(!snapshotIds(read(resampledFile)).equals(snapshotIds(read(output.resolve("bank-max-d5-c3.csv")))),
                "a different sampling seed changes selected snapshot IDs/order");
        for (RuleType role : RuleType.values()) {
            same(bank.poolSize(role, 3), resampled.poolSize(role, 3), "sampling seed does not change eligibility");
        }

        DcDescriptorBank independent = collect(SIMULATION_SEED + 71, SAMPLING_SEED + 101, 4, 3);
        Path independentFile = output.resolve("independent-test-max-d4-c3.csv");
        independent.write(independentFile, 4, 3);
        checkRows(independentFile, 4, 3);
        require(!numericVectors(read(independentFile)).equals(
                numericVectors(read(output.resolve("selected-d4-c3.csv")))), "independent simulation changes vectors");
        checkNative(independent, independentFile, 4, 3);
        for (int d : new int[]{2, 3, 4}) {
            double[] coordinates = independent.coordinates(pair("(+ PT WIQ)", "(+ WIQ TRANT)"), d, 3);
            require(coordinates.length == 2 * d, "common independent test-bank dimensions");
        }

        expectProtocol(() -> DcDescriptorBank.collect(SIMULATION_SEED, SAMPLING_SEED,
                4, 1, 0, 1e-9, 1.5, 5, 4), "insufficient exact-c snapshots", "exact-c snapshots");
        for (int i = 0; i < ecj.random.length; i++) {
            require(ecj.random[i].stateEquals(controls[i]), "collection/evaluation must not consume ECJ random streams");
            same(ecj.random[i].nextInt(), controls[i].nextInt(), "ECJ stream continuation");
        }
    }

    private static void checkGeometryAndProtocol() {
        int[][] settings = {{2, 3, 9, 81}, {3, 3, 27, 729}, {4, 3, 81, 6561},
                {5, 3, 243, 59049}, {4, 2, 16, 256}, {4, 4, 256, 65536}};
        for (int[] setting : settings) {
            int side = DcDescriptorBank.side(setting[0], setting[1]);
            same(setting[2], side, "true axis c^d");
            require((long) side * side == setting[3], "true two-role cell count c^(2d)");
        }
        expectProtocol(() -> DcDescriptorBank.side(0, 3), "zero d", null);
        expectProtocol(() -> DcDescriptorBank.side(4, 1), "one candidate", null);
        expectProtocol(() -> DcDescriptorBank.side(31, 2), "axis overflow", null);
        expectProtocol(() -> collect(1, 2, 0, 3), "zero maxD", null);
        expectProtocol(() -> collect(1, 2, 5), "missing c", null);
        expectProtocol(() -> collect(1, 2, 5, (int[]) null), "null c", null);
        expectProtocol(() -> collect(1, 2, 5, 1), "invalid c", null);
        expectProtocol(() -> collect(1, 2, 5, 11), "c exceeds machines", null);
        expectProtocol(() -> collect(1, 2, 5, 3, 3), "duplicate c", null);
        expectProtocol(() -> DcDescriptorBank.collect(1, 2, 10, 0, 0, .85, 1.5, 5, 3), "zero jobs", null);
        expectProtocol(() -> DcDescriptorBank.collect(1, 2, 10, 500, -1, .85, 1.5, 5, 3), "negative warmup", null);
        for (double utilisation : new double[]{0, -1, 1, Double.NaN, Double.POSITIVE_INFINITY}) {
            expectProtocol(() -> DcDescriptorBank.collect(1, 2, 10, 500, 0, utilisation, 1.5, 5, 3),
                    "invalid utilisation", null);
        }
        for (double due : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            expectProtocol(() -> DcDescriptorBank.collect(1, 2, 10, 500, 0, .85, due, 5, 3),
                    "invalid due factor", null);
        }
    }

    private static void checkSettings(DcDescriptorBank bank, Path output) throws IOException {
        GPRule[] reference = pair("(/ PT W)", "WIQ");
        GPRule[] example = pair("(+ PT (* 0.1 OWT))", "(+ WIQ TRANT)");
        double[] all = bank.coordinates(example, 5, 3);
        for (int[] setting : new int[][]{{2, 3}, {3, 3}, {4, 3}, {5, 3}, {4, 2}, {4, 4}}) {
            int d = setting[0];
            int c = setting[1];
            Path selected = output.resolve("selected-d" + d + "-c" + c + ".csv");
            bank.write(selected, d, c);
            checkRows(selected, d, c);
            Csv maximum = read(output.resolve("bank-max-d5-c" + c + ".csv"));
            Csv prefix = read(selected);
            List<String> expected = new ArrayList<>();
            for (String row : maximum.rows) {
                if (Integer.parseInt(maximum.value(row, "position")) < d) {
                    expected.add(row);
                }
            }
            require(prefix.header.equals(maximum.header) && prefix.rows.equals(expected), "nested exported d prefix");
            double[] coordinates = bank.coordinates(example, d, c);
            require(coordinates.length == 2 * d, "concatenated coordinate length");
            for (double coordinate : coordinates) {
                require(coordinate >= 0 && coordinate < c && coordinate == Math.rint(coordinate), "zero-based native digit");
            }
            if (c == 3) {
                for (int i = 0; i < d; i++) {
                    bits(all[i], coordinates[i], "nested sequencing coordinate");
                    bits(all[5 + i], coordinates[d + i], "nested routing coordinate");
                }
            }
            for (double rank : bank.coordinates(reference, d, c)) {
                bits(0.0, rank, "native reference pair selects reference rank one");
            }
            require(Arrays.equals(coordinates, bank.coordinates(new GPRule[]{example[1], example[0]}, d, c)),
                    "pair order resolved by native rule role");
        }
    }

    private static void checkIsolation(DcDescriptorBank bank, Path output) throws IOException {
        GPRule[] rules = pair("(+ PT OWT)", "(+ WIQ TRANT)");
        double[] before = bank.coordinates(rules, 4, 3);
        PhenoCharacterisation[] exposed = bank.characterisations(4, 3);
        require(exposed[0].getClass() == SequencingPhenoCharacterisation.class
                && exposed[1].getClass() == RoutingPhenoCharacterisation.class, "unmodified native PC classes");
        SequencingPhenoCharacterisation seq = (SequencingPhenoCharacterisation) exposed[0];
        RoutingPhenoCharacterisation rout = (RoutingPhenoCharacterisation) exposed[1];
        seq.characterisePermutation(rules[0]);
        rout.characterisePermutation(rules[1]);
        Collections.reverse(seq.decisionSituations.get(0).getQueue());
        Collections.reverse(rout.decisionSituations.get(0).getQueue());
        seq.decisionSituations.get(0).getQueue().get(0).setReadyTime(-123.0);
        rout.decisionSituations.get(0).getQueue().get(0).setPriority(Double.MAX_VALUE);
        seq.decisionSituations.get(0).getSystemState().setClockTime(-456.0);
        seq.decisionSituations.clear();
        rout.decisionSituations.clear();
        require(Arrays.equals(before, bank.coordinates(rules, 4, 3)), "returned PCs cannot mutate bank order or options");
        Path after = output.resolve("after-native-pc-mutation.csv");
        bank.write(after, 5, 3);
        sameFile(after, output.resolve("bank-max-d5-c3.csv"), "export is frozen, not reconstructed from evaluated PCs");
        byte[] original = Files.readAllBytes(after);
        try {
            bank.write(after, 5, 3);
            throw new AssertionError("Existing descriptor CSV was overwritten");
        } catch (FileAlreadyExistsException expected) {
            require(Arrays.equals(original, Files.readAllBytes(after)), "CREATE_NEW preserves existing bytes");
        }
        expectProtocol(() -> bank.characterisations(0, 3), "invalid d", null);
        expectProtocol(() -> bank.characterisations(6, 3), "d exceeds maxD", null);
        expectProtocol(() -> bank.characterisations(4, 5), "uncollected c", null);
        expectProtocol(() -> bank.poolSize(null, 3), "null role", null);
        expectProtocol(() -> bank.poolSize(RuleType.ROUTING, 5), "uncollected pool c", null);
        expectProtocol(() -> bank.coordinates(null, 4, 3), "null pair", null);
        expectProtocol(() -> bank.coordinates(new GPRule[]{rules[0]}, 4, 3), "short pair", null);
        expectProtocol(() -> bank.coordinates(new GPRule[]{rules[0], rules[0]}, 4, 3), "duplicate decision role", null);
        expectProtocol(() -> bank.coordinates(new GPRule[]{rules[0], null}, 4, 3), "null member", null);
    }

    private static void checkNative(DcDescriptorBank bank, Path export, int d, int c) throws IOException {
        PhenoCharacterisation[] supplied = bank.characterisations(d, c);
        SequencingPhenoCharacterisation seq = (SequencingPhenoCharacterisation) supplied[0];
        RoutingPhenoCharacterisation rout = (RoutingPhenoCharacterisation) supplied[1];
        List<SequencingDecisionSituation> sequencing = new ArrayList<>();
        List<RoutingDecisionSituation> routing = new ArrayList<>();
        for (int i = 0; i < d; i++) {
            sequencing.add(seq.decisionSituations.get(i).clone());
            routing.add(rout.decisionSituations.get(i).clone());
        }
        PhenoCharacterisation[] normal = {
                new SequencingPhenoCharacterisation(new WSPT(RuleType.SEQUENCING), sequencing),
                new RoutingPhenoCharacterisation(new WIQ(RuleType.ROUTING), routing)
        };
        for (GPRule[] pair : Arrays.asList(pair("0", "0"), pair("(/ PT W)", "WIQ"),
                pair("(- 0 PT)", "(- 0 WIQ)"), pair("(+ PT (* 0.1 OWT))", "(+ WIQ TRANT)"))) {
            int[] expectedSeq = normal[0].characterise(pair[0]);
            int[] expectedRout = normal[1].characterise(pair[1]);
            double[] actual = bank.coordinates(pair, d, c);
            for (int i = 0; i < d; i++) {
                bits(expectedSeq[i] - 1.0, actual[i], "normal native sequencing descriptor");
                bits(expectedRout[i] - 1.0, actual[d + i], "normal native routing descriptor");
            }
        }
        Csv csv = read(export);
        for (String row : csv.rows) {
            RuleType role = RuleType.valueOf(csv.value(row, "role"));
            int position = Integer.parseInt(csv.value(row, "position"));
            int candidate = Integer.parseInt(csv.value(row, "candidate_index"));
            List<OperationOption> queue;
            WorkCenter wc;
            SystemState state;
            int[] ranks;
            if (role == RuleType.SEQUENCING) {
                SequencingDecisionSituation situation = seq.decisionSituations.get(position);
                ranks = new WSPT(role).priorValueOperation(situation);
                queue = situation.getQueue();
                wc = situation.getWorkCenter();
                state = situation.getSystemState();
            } else {
                RoutingDecisionSituation situation = rout.decisionSituations.get(position);
                ranks = new WIQ(role).priorValueOperationMahcine(situation);
                queue = situation.getQueue();
                wc = queue.get(candidate).getWorkCenter();
                state = situation.getSystemState();
            }
            OperationOption option = queue.get(candidate);
            same(ranks[candidate], Integer.parseInt(csv.value(row, "reference_rank")), "exported native reference rank");
            same(option.getJob().getId(), Integer.parseInt(csv.value(row, "job_id")), "native priorTo job identity");
            same(option.getOperation().getId(), Integer.parseInt(csv.value(row, "operation_id")), "operation identity");
            same(option.getOptionId(), Integer.parseInt(csv.value(row, "option_id")), "option identity");
            same(wc.getId(), Integer.parseInt(csv.value(row, "work_center_id")), "native machine/work-center identity");
            bits(option.getReadyTime(), Double.parseDouble(csv.value(row, "ready_time")), "frozen ready time");
            bits(option.getPriority(), Double.parseDouble(csv.value(row, "reference_priority")), "native reference priority");
            for (String terminal : TERMINALS) {
                bits(JobShopAttribute.get(terminal).value(option, wc, state),
                        Double.parseDouble(csv.value(row, terminal)), "exported native terminal " + terminal);
            }
        }
        GPRule[] constants = pair("0", "0");
        double[] tieCoordinates = bank.coordinates(constants, d, c);
        for (int i = 0; i < d; i++) {
            checkNativeTie(seq.decisionSituations.get(i).getQueue(),
                    new WSPT(RuleType.SEQUENCING).priorValueOperation(seq.decisionSituations.get(i)),
                    tieCoordinates[i]);
            checkNativeTie(rout.decisionSituations.get(i).getQueue(),
                    new WIQ(RuleType.ROUTING).priorValueOperationMahcine(rout.decisionSituations.get(i)),
                    tieCoordinates[d + i]);
        }
    }

    private static void checkNativeTie(List<OperationOption> queue, int[] reference, double actual) {
        for (OperationOption option : queue) {
            option.setPriority(0.0);
        }
        int chosen = 0;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).priorTo(queue.get(chosen))) {
                chosen = i;
            }
        }
        bits(reference[chosen] - 1.0, actual, "native priorTo tie behavior, not an added comparator");
    }

    private static void checkRows(Path file, int d, int c) throws IOException {
        Csv csv = read(file);
        same(2 * d * c, csv.rows.size(), "actual frozen candidate row count");
        Set<String> selected = new HashSet<>();
        Set<String> candidateIds = new HashSet<>();
        Map<String, String> positions = new HashMap<>();
        Map<String, Set<Integer>> ranks = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (String row : csv.rows) {
            String snapshot = csv.value(row, "snapshot_id");
            RuleType role = RuleType.valueOf(csv.value(row, "role"));
            int position = Integer.parseInt(csv.value(row, "position"));
            int candidate = Integer.parseInt(csv.value(row, "candidate_index"));
            int rank = Integer.parseInt(csv.value(row, "reference_rank"));
            same(c, Integer.parseInt(csv.value(row, "c")), "exact candidate count");
            require(position >= 0 && position < d && candidate >= 0 && candidate < c, "exported index domains");
            require(rank >= 1 && rank <= c, "one-based native reference rank");
            String positionKey = role + ":" + position;
            String previous = positions.put(positionKey, snapshot);
            require(previous == null || previous.equals(snapshot), "one actual snapshot ID per role/position");
            if (previous == null) {
                require(selected.add(snapshot), "no repeated selected snapshot ID");
            }
            require(snapshot.contains("-c" + c + "-snapshot-"), "actual native replay ordinal in ID");
            require(candidateIds.add(snapshot + ":" + candidate), "no repeated candidate row");
            Set<Integer> snapshotRanks = ranks.get(snapshot);
            if (snapshotRanks == null) {
                snapshotRanks = new HashSet<>();
                ranks.put(snapshot, snapshotRanks);
            }
            require(snapshotRanks.add(rank), "reference ranks form a native permutation");
            counts.put(snapshot, counts.getOrDefault(snapshot, 0) + 1);
            for (String terminal : TERMINALS) {
                require(Double.isFinite(Double.parseDouble(csv.value(row, terminal))), "finite terminal " + terminal);
            }
            require(!csv.value(row, "machine_ids").isEmpty()
                    && !csv.value(row, "machine_ready_times").isEmpty(), "machine identity/readiness provenance");
        }
        same(2 * d, selected.size(), "different native snapshots for both roles");
        for (int count : counts.values()) {
            same(c, count, "exact queue size, not a min-length or truncated queue");
        }
    }

    private static DcDescriptorBank collect(long simulationSeed, long samplingSeed, int maxD, int... counts) {
        return DcDescriptorBank.collect(simulationSeed, samplingSeed, 10, 500, 0, .85, 1.5, maxD, counts);
    }

    private static GPRule[] pair(String sequencing, String routing) {
        return new GPRule[]{
                GPRule.readFromLispExpression(RuleType.SEQUENCING, sequencing),
                GPRule.readFromLispExpression(RuleType.ROUTING, routing)
        };
    }

    private static List<String> snapshotIds(Csv csv) {
        List<String> ids = new ArrayList<>();
        for (String row : csv.rows) {
            if (Integer.parseInt(csv.value(row, "candidate_index")) == 0) {
                ids.add(csv.value(row, "snapshot_id"));
            }
        }
        return ids;
    }

    private static List<String> numericVectors(Csv csv) {
        List<String> vectors = new ArrayList<>();
        for (String row : csv.rows) {
            StringBuilder values = new StringBuilder(csv.value(row, "clock_time"));
            for (String terminal : TERMINALS) {
                values.append(',').append(csv.value(row, terminal));
            }
            vectors.add(values.toString());
        }
        return vectors;
    }

    private static Csv read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        require(!lines.isEmpty(), "nonempty UTF-8 descriptor export");
        return new Csv(lines.get(0), new ArrayList<>(lines.subList(1, lines.size())));
    }

    private static void sameFile(Path first, Path second, String label) throws IOException {
        require(Arrays.equals(Files.readAllBytes(first), Files.readAllBytes(second)), label);
    }

    private static void expectProtocol(Runnable operation, String label, String messagePart) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            require(messagePart == null || expected.getMessage().contains(messagePart), label + ": contextual error");
            return;
        }
        throw new AssertionError("DC descriptor check failed to reject: " + label);
    }

    private static void same(int expected, int actual, String label) {
        require(expected == actual, label + ": expected " + expected + ", got " + actual);
    }

    private static void bits(double expected, double actual, String label) {
        require(Double.doubleToRawLongBits(expected) == Double.doubleToRawLongBits(actual),
                label + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("DC descriptor check failed: " + label);
        }
    }

    private static final class Csv {
        final String header;
        final List<String> rows;
        final Map<String, Integer> columns = new LinkedHashMap<>();

        Csv(String header, List<String> rows) {
            this.header = header;
            this.rows = rows;
            String[] names = header.split(",", -1);
            for (int i = 0; i < names.length; i++) {
                require(columns.put(names[i], i) == null, "unique CSV columns");
            }
            for (String row : rows) {
                same(names.length, row.split(",", -1).length, "CSV row shape");
            }
        }

        String value(String row, String name) {
            Integer column = columns.get(name);
            require(column != null, "required CSV column " + name);
            return row.split(",", -1)[column];
        }
    }
}
