package mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD;

import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.simple.SimpleFitness;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.ArchiveCandidate;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.FinalArchiveReader;
import mengxu.algorithm.MAPElites.FuzzySystem.revision.PriorityExpression;
import mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD.PopulationSnapshots;
import yimei.jss.gp.terminal.ConstantTerminal;
import yimei.jss.rule.RuleType;

public final class UniformBaselineChecks {
    private UniformBaselineChecks() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) { throw new IllegalArgumentException("UniformBaselineChecks <module-directory>"); }
        Path module = Paths.get(args[0]).toRealPath();
        Path root = Files.createTempDirectory("uniform-archive-checks-");
        try {
            selectionChecks();
            replacementChecks();
            for (boolean rotating : Arrays.asList(false, true)) {
                String suffix = rotating ? "rotating" : "fixed";
                Path uniform = root.resolve("uniform-" + suffix);
                Path control = root.resolve("matched-identical-" + suffix);
                Path adaptive = root.resolve("matched-default-" + suffix);
                train(module, uniform, rotating ? "uniform-gp.params" : "uniform-gpf.params", 1.0, 2, "mean-tardiness");
                train(module, control, rotating ? "matched-qdgp.params" : "matched-qdgpf.params", 1.0, 999, "mean-tardiness");
                train(module, adaptive, rotating ? "matched-qdgp.params" : "matched-qdgpf.params", 0.5, 2, "mean-tardiness");
                checkRun(uniform, rotating);
                checkRun(control, rotating);
                checkRun(adaptive, rotating);
                check(populationSignature(uniform.resolve("initial_population.stat"))
                        .equals(populationSignature(control.resolve("initial_population.stat"))), "Matched initialization");
                check(archiveSignature(uniform.resolve("job.0.out.stat"))
                        .equals(archiveSignature(control.resolve("job.0.out.stat"))),
                        "Same parent policy must produce identical final archive despite labels/logging frequency");
                check(Files.exists(uniform.resolve("archive.gen0000.stat"))
                        && !Files.exists(uniform.resolve("archive.gen0001.stat"))
                        && Files.exists(uniform.resolve("archive.gen0002.stat")), "Snapshot interval");
            }
            for (String objective : Arrays.asList("max-flowtime", "mean-weighted-flowtime")) {
                Path output = root.resolve(objective);
                train(module, output, "uniform-gpf.params", 1.0, 2, objective);
                checkRun(output, false);
            }
            try {
                train(module, root.resolve("uniform-fixed"), "uniform-gpf.params", 1.0, 2, "mean-tardiness");
                throw new AssertionError("Existing output was overwritten");
            } catch (IllegalArgumentException expected) { check(expected.getMessage().contains("overwrite"), "Output protection"); }
            System.out.println("UNIFORM BASELINE CHECKS PASSED: both regimes, matched controls, sampling, replacement and snapshots");
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.delete(path);
                }
            }
        }
    }

    private static GPIndividual individual(String expression, double value) {
        GPIndividual individual = new GPIndividual();
        individual.trees = new GPTree[]{
                PriorityExpression.parseRule(RuleType.SEQUENCING, expression).getGPTree(),
                PriorityExpression.parseRule(RuleType.ROUTING, "WIQ").getGPTree()};
        for (GPTree tree : individual.trees) { tree.owner = individual; }
        SimpleFitness fitness = new SimpleFitness();
        fitness.setFitness(null, value, false);
        individual.fitness = fitness;
        individual.evaluated = true;
        return individual;
    }

    private static UniformParentState unitState() {
        UniformParentState state = new UniformParentState();
        state.parameters = new ParameterDatabase();
        state.parameters.set(new Parameter("select.tournament.size"), "7");
        state.parameters.set(new Parameter("tournament-size-dynamic"), "true");
        state.random = new MersenneTwisterFast[]{new MersenneTwisterFast(42)};
        state.population = new Population();
        state.population.subpops = new Subpopulation[]{new Subpopulation()};
        state.population.subpops[0].individuals = new Individual[]{
                individual("999", 1), individual("999", 1), individual("999", 1)};
        state.gridPCmap = new IsolatedGridMap(3, 3);
        return state;
    }

    private static void selectionChecks() {
        UniformParentState state = unitState();
        IsolatedGridMap map = (IsolatedGridMap) state.gridPCmap;
        String[] expressions = {"PT", "W", "NIQ"};
        double[] scores = {1, 1000000, Double.POSITIVE_INFINITY};
        for (int i = 0; i < 3; i++) {
            map.updateGridMapWithIndividual(state, individual(expressions[i], i + 1),
                    new double[]{i, 0}, scores[i]);
        }
        ArchiveParentSelection selector = new ArchiveParentSelection();
        selector.setup(state, new Parameter("select.archive"));
        state.randomParentSelectionProbability = 1;
        int[] histogram = new int[3];
        for (int i = 0; i < 30000; i++) { histogram[selector.produce(0, state, 0)]++; }
        for (int count : histogram) {
            check(Math.abs(count / 30000.0 - 1.0 / 3) < 0.02, "Occupied-cell sampling must be uniform, not fitness-weighted");
        }
        Individual[] parents = new Individual[2];
        selector.produce(2, 2, 0, 0, parents, state, 0);
        check(!((GPIndividual) parents[0]).trees[0].child.makeLispTree().equals("999"),
                "Generation-zero parents must come from archive, not population");
        ((GPIndividual) parents[0]).trees[0].child = new ConstantTerminal(777);
        for (int i = 0; i < map.occupiedCount(); i++) {
            check(((GPIndividual) map.individualAt(i)).trees[0].child.makeLispTree().equals(expressions[i]),
                    "Parent variation must not mutate stored archive trees");
        }
        state.randomParentSelectionProbability = 0;
        int best = 0;
        for (int i = 0; i < 1000; i++) { if (selector.produce(0, state, 0) == 0) { best++; } }
        check(best > 850, "Matched adaptive control must retain fitness pressure");
        check(map.getNumberIndividualsInGridMap() == map.occupiedCount(), "Capacity-one occupancy");
        System.out.println("Uniform cell sampling counts: " + Arrays.toString(histogram) + "; adaptive best count=" + best);
    }

    private static void replacementChecks() {
        UniformParentState state = unitState();
        IsolatedGridMap map = (IsolatedGridMap) state.gridPCmap;
        double[] descriptor = {0, 0};
        map.updateGridMapWithIndividual(state, individual("PT", 1), descriptor, 1);
        map.updateGridMapWithIndividual(state, individual("W", 2), descriptor, 2);
        check(map.scoreAt(0) == 1 && map.occupiedCount() == 1, "Fixed cell keeps better score");
        state.generation = 100;
        state.penalty_level = 0.01;
        map.updateGridMapWithIndividual(state, individual("W", 2), descriptor, 2);
        check(map.scoreAt(0) == 2 && map.generationAt(0) == 100, "Inherited rotating age comparison");
        map.updateGridMapWithIndividual(state, individual("NIQ", 0.5), descriptor, 0.5);
        check(map.scoreAt(0) == 0.5 && map.occupiedCount() == 1, "Replacement must not duplicate occupied keys");
    }

    private static void train(Path module, Path output, String preset, double probability,
                              int interval, String objective) throws IOException {
        List<String> arguments = new ArrayList<>(Arrays.asList("--params", module.resolve(preset).toString(),
                "--output-root", output.toString()));
        String[] overrides = {"generations=3", "pop.subpop.0.size=10", "breed.elite.0=2", "seed.0=19",
                "eval.problem.eval-model.sim-seed=17", "eval.problem.eval-model.objectives.0=" + objective,
                "eval.problem.eval-model.sim-models.0.num-jobs=40",
                "eval.problem.eval-model.sim-models.0.warmup-jobs=0",
                "penalty-level=" + (preset.endsWith("-gp.params") || preset.equals("matched-qdgp.params") ? 0.01 : 0),
                "random-parent-selection-probability=" + probability, "uniform.snapshot-interval=" + interval};
        for (String value : overrides) { arguments.add("--set"); arguments.add(value); }
        UniformParentRun.main(arguments.toArray(new String[0]));
    }

    private static void checkRun(Path output, boolean rotating) throws IOException {
        List<String> rows = Files.readAllLines(output.resolve("generations.csv"), StandardCharsets.UTF_8);
        check(rows.size() == 4, "Three evaluated batches");
        for (int i = 0; i < 3; i++) {
            String[] row = rows.get(i + 1).split(",");
            check(Long.parseLong(row[1]) == 17L + (rotating ? i * 10000L : 0), "Inherited seed rotation");
            check(Integer.parseInt(row[2]) == Integer.parseInt(row[3]), "Occupied cells equal retained count at capacity one");
            check(Long.parseLong(row[7]) == (i + 1) * 10L, "Actual inherited fitness evaluation count");
            long manual = Long.parseLong(row[8]);
            check(manual > 0 && Long.parseLong(row[10]) == (i + 1) * 10L + manual + 2, "Reference costs are not hidden");
        }
        check(!new FinalArchiveReader().read(output.resolve("job.0.out.stat")).candidates().isEmpty(), "Compatible final archive");
        String manifest = new String(Files.readAllBytes(output.resolve("manifest.properties")), StandardCharsets.UTF_8);
        check(manifest.contains("status=COMPLETE"), "Completed run manifest");
    }

    private static List<String> archiveSignature(Path archive) throws IOException {
        List<String> members = new ArrayList<>();
        for (ArchiveCandidate candidate : new FinalArchiveReader().read(archive).candidates()) {
            members.add(candidate.treeHash() + ":" + candidate.storedFitness());
        }
        java.util.Collections.sort(members);
        return members;
    }

    private static List<String> populationSignature(Path population) throws IOException {
        List<String> members = new ArrayList<>();
        for (ArchiveCandidate candidate : PopulationSnapshots.read(population).candidates()) {
            members.add(candidate.treeHash() + ":" + candidate.storedFitness());
        }
        return members;
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
