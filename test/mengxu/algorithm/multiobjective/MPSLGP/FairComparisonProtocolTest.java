package mengxu.algorithm.multiobjective.MPSLGP;

import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;
import ec.util.MersenneTwisterFast;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import yimei.jss.jobshop.Objective;
import yimei.jss.rule.operation.composite.ATC;
import yimei.jss.rule.operation.basic.SPT;
import yimei.jss.rule.operation.weighted.WATC;
import yimei.jss.rule.operation.weighted.WSPT;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class FairComparisonProtocolTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        savedConfigurations();
        allocations();
        nativeCache();
        tournaments();
        eliteCopies();
        check(Objective.MAX_FLOWTIME.benchmarkSequencingRule() instanceof SPT, "SPT normalization");
        check(Objective.MAX_TARDINESS.benchmarkSequencingRule() instanceof ATC, "ATC normalization");
        check(Objective.MAX_WEIGHTED_FLOWTIME.benchmarkSequencingRule() instanceof WSPT, "WSPT normalization");
        check(Objective.MAX_WEIGHTED_TARDINESS.benchmarkSequencingRule() instanceof WATC, "WATC normalization");
        System.out.println("PASS: FairComparisonProtocolTest (" + assertions + " assertions; no simulations)");
    }

    private static void savedConfigurations() throws Exception {
        String[] names = {"MPSLGP Paper Training", "MPSLGP Paper Test", "PSLGP Training", "PSLGP Test",
                "NSGPII Training", "NSGPII Test"};
        try (Stream<Path> files = Files.list(Path.of(".run"))) {
            check(files.filter(path -> path.getFileName().toString().endsWith(".run.xml")).count() == 6,
                    "Exactly six shared IDEA configurations");
        }
        if (Files.isDirectory(Path.of(".idea", "runConfigurations"))) {
            try (Stream<Path> files = Files.list(Path.of(".idea", "runConfigurations"))) {
                check(files.noneMatch(path -> path.getFileName().toString().endsWith(".xml")),
                        "No duplicate or obsolete .idea configurations");
            }
        }
        for (String name : names) {
            org.w3c.dom.Document xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(Path.of(".run", name + ".run.xml").toFile());
            org.w3c.dom.Element configuration = (org.w3c.dom.Element) xml.getElementsByTagName("configuration").item(0);
            check(configuration.getAttribute("name").equals(name), "Saved configuration identity");
            Map<String, String> options = new TreeMap<>();
            org.w3c.dom.NodeList nodes = configuration.getElementsByTagName("option");
            for (int index = 0; index < nodes.getLength(); index++) {
                org.w3c.dom.Element node = (org.w3c.dom.Element) nodes.item(index);
                options.put(node.getAttribute("name"), node.getAttribute("value"));
                if (node.getAttribute("name").equals("Make")) {
                    check(node.getAttribute("enabled").equals("true"), "Build before launch");
                }
            }
            String args = options.get("PROGRAM_PARAMETERS");
            check(args.contains("--parallel 8") && args.contains("--heap 2g"), "Eight isolated worker JVMs");
            check(args.contains("--runs 30") && args.contains("--start-run 0"), "All thirty seeds retained");
            check(args.contains("same-population-budget-aligned-v2-generation50"), "Generation50 isolated output root");
            check(options.get("VM_PARAMETERS").contains("-Xmx2g"), "Bounded coordinator heap");
            check(options.get("WORKING_DIRECTORY").equals("$PROJECT_DIR$"), "Project working directory");
            if (name.startsWith("MPSLGP")) {
                check(args.contains("--algorithms full") && args.contains("--indicators IGD"), "Full MPSLGP comparison");
                if (name.endsWith("Training")) { check(args.contains("--generations 51"), "Final generation50 includes generation0"); }
            } else {
                check(args.contains("--method " + name.split(" ")[0]) && args.contains("--budget matched"), "Independent matched baseline");
                check(args.contains("--action " + (name.endsWith("Training") ? "train" : "test")), "Matching training/test action");
            }
        }
        check(FairComparisonProtocol.FINAL_GENERATION == 50 && FairComparisonProtocol.EVALUATED_GENERATIONS == 51,
                "Final-generation convention is not confused with evaluated-generation count");
        check(!FairComparisonProtocol.VERSION.equals("aligned-v1"), "Generation49 fingerprints must not be reused");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new AssertionError(message); }
    }

    private static void allocations() {
        for (int tasks : new int[]{2, 3}) {
            int[] totals = new int[tasks];
            for (int seed = 0; seed < 30; seed++) {
                int population = 0, elites = 0;
                for (int task = 0; task < tasks; task++) {
                    Map<String, String> settings = new TreeMap<>();
                    FairComparisonProtocol.configure(settings, 1000, tasks, seed, task);
                    population += FairComparisonProtocol.quota(1000, tasks, task, seed);
                    int elite = Integer.parseInt(settings.get("breed.elite.0"));
                    elites += elite;
                    totals[task] += elite;
                    check(settings.get(FairComparisonProtocol.PARAMETER).equals(FairComparisonProtocol.VERSION), "Protocol fingerprint");
                    check(settings.get("select.tournament.size").equals("7"), "Seven candidates");
                    check(settings.get("select.tournament.pick-worst").equals("false"), "Best-of-seven selection");
                    check(settings.get("load-Pareto-front").equals("false"), "No extra Pareto-front elite copies");
                    check(settings.get("pop.subpop.0.species.pipe.source.0").equals(FairComparisonProtocol.CROSSOVER), "Shared crossover");
                    check(settings.get("pop.subpop.0.species.pipe.source.0.prob").equals("0.80")
                            && settings.get("pop.subpop.0.species.pipe.source.1.prob").equals("0.15")
                            && settings.get("pop.subpop.0.species.pipe.source.2.prob").equals("0.05"), "Shared operator probabilities");
                }
                check(population == 1000 && elites == 10, "Exact population and elite group totals");
            }
            for (int total : totals) { check(total == 300 / tasks, "Rotating elite allocation over thirty seeds"); }
        }
    }

    private static Individual individual(String method, int task, double value) {
        GPIndividual individual = new GPIndividual();
        individual.trees = new GPTree[0];
        MultiObjectiveFitness fitness;
        if (method.equals("MPSLGP")) {
            ClearingPSLMultiObjectiveFitness multi = new ClearingPSLMultiObjectiveFitness();
            multi.setTaskIndex(task);
            multi.setPSLFitness(value);
            multi.setIGDvalue(value);
            multi.compareCriteria = "IGD";
            multi.useMultiCriteriaSelection = true;
            multi.rank = 0;
            fitness = multi;
        } else if (method.equals("PSLGP")) {
            mengxu.algorithm.multiobjective.ParetoSetLearning.ClearingPSLMultiObjectiveFitness single =
                    new mengxu.algorithm.multiobjective.ParetoSetLearning.ClearingPSLMultiObjectiveFitness();
            single.setPSLFitness(value);
            single.setIGDvalue(value);
            single.compareCriteria = "IGD";
            single.useMultiCriteriaSelection = true;
            single.rank = 0;
            fitness = single;
        } else {
            NSGA2MultiObjectiveFitness ns = new NSGA2MultiObjectiveFitness();
            ns.rank = 0;
            ns.sparsity = -value;
            fitness = ns;
        }
        fitness.objectives = new double[]{value, value};
        fitness.maximize = new boolean[]{false, false};
        individual.fitness = fitness;
        individual.evaluated = true;
        return individual;
    }

    private static void nativeCache() {
        Individual[] individuals = {individual("PSLGP", 0, 20), individual("PSLGP", 0, 10)};
        double[][][][] phenotypes = {{{{11}}, {{22}}}};
        double[][][][] fitnesses = {{{{20, 20, 20, 0}}, {{10, 10, 10, 0}}}};
        mengxu.algorithm.multiobjective.ParetoSetLearning.KNNsurrogateClearingPSLEvaluatorbasedonHV evaluator =
                new mengxu.algorithm.multiobjective.ParetoSetLearning.KNNsurrogateClearingPSLEvaluatorbasedonHV();
        evaluator.cacheSurrogatePopulation(phenotypes, fitnesses);
        yimei.jss.niching.multitreeClearingFirst.sortPopBasedOnTheSameBetterThan(individuals, phenotypes[0]);
        check(phenotypes[0][0][0][0] == 22, "Test actually reordered phenotypes");
        check(evaluator.lastGenPhenotypicOfIntermedidatePopPSL[0][0][0][0] == 11
                && evaluator.lastGenFitnessOfIntermedidatePopPSL[0][0][0][2] == 20, "Aligned native cache after sorting");
        phenotypes[0][1][0][0] = -1;
        fitnesses[0][0][0][2] = -1;
        check(evaluator.lastGenPhenotypicOfIntermedidatePopPSL[0][0][0][0] == 11
                && evaluator.lastGenFitnessOfIntermedidatePopPSL[0][0][0][2] == 20, "Independent native cache snapshots");
    }

    private static EvolutionState state(String method, int tasks, int elites, int seed, Individual[] individuals) {
        EvolutionState state = method.equals("MPSLGP") ? new GPRuleEvolutionStatePSL()
                : method.equals("PSLGP") ? new mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL()
                : new mengxu.algorithm.multiobjective.GPRuleEvolutionStateMO();
        state.parameters = new ParameterDatabase();
        state.parameters.set(new Parameter(FairComparisonProtocol.PARAMETER), FairComparisonProtocol.VERSION);
        state.parameters.set(new Parameter("breed.elite.0"), Integer.toString(elites));
        state.parameters.set(new Parameter("select.tournament.size"), "7");
        state.breedthreads = state.evalthreads = 1;
        state.random = new MersenneTwisterFast[]{new CountingRandom(seed)};
        state.population = new Population();
        state.population.subpops = new Subpopulation[]{new Subpopulation()};
        state.population.subpops[0].individuals = individuals;
        if (state instanceof GPRuleEvolutionStatePSL) {
            ((GPRuleEvolutionStatePSL) state).numTasks = tasks;
            ((GPRuleEvolutionStatePSL) state).taskAllocationOffset = seed % tasks;
        }
        return state;
    }

    private static final class CountingRandom extends MersenneTwisterFast {
        int draws;
        CountingRandom(long seed) { super(seed); }
        @Override public int nextInt(int bound) { draws++; return super.nextInt(bound); }
    }

    private static void tournaments() {
        Individual[] mixed = {individual("MPSLGP", 0, 100), individual("MPSLGP", 0, 101),
                individual("MPSLGP", 1, 0), individual("MPSLGP", 1, 1),
                individual("MPSLGP", 2, 200), individual("MPSLGP", 2, 201)};
        GPRuleEvolutionStatePSL state = (GPRuleEvolutionStatePSL) state("MPSLGP", 3, 0, 2, mixed);
        PSLParentSelection selection = new PSLParentSelection();
        selection.setup(state, new Parameter("select.tournament"));
        for (int task = 0; task < 3; task++) {
            CountingRandom random = (CountingRandom) state.random[0];
            int before = random.draws;
            int selected = selection.produceMOEADFromTask(0, 0, state, 0, task, 0);
            check(random.draws - before == 7 && state.getTaskIndex(mixed[selected]) == task, "Seven actual task candidates, no global fallback");
        }
        int[] taskCounts = new int[3];
        for (int trial = 0; trial < 6000; trial++) {
            taskCounts[state.getTaskIndex(mixed[selection.produceMOEAD(0, 0, state, 0)])]++;
        }
        for (int count : taskCounts) { check(count > 1700 && count < 2300, "Primary task draw follows allocation, not global fitness"); }
        for (int seed = 0; seed < 100; seed++) {
            Individual[] multi = {individual("MPSLGP", 0, 20), individual("MPSLGP", 0, 10), individual("MPSLGP", 0, 30)};
            Individual[] single = {individual("PSLGP", 0, 20), individual("PSLGP", 0, 10), individual("PSLGP", 0, 30)};
            EvolutionState ms = state("MPSLGP", 1, 0, seed, multi), ps = state("PSLGP", 1, 0, seed, single);
            EvolutionState ns = state("NSGPII", 1, 0, seed, new Individual[]{
                    individual("NSGPII", 0, 20), individual("NSGPII", 0, 10), individual("NSGPII", 0, 30)});
            PSLParentSelection mp = new PSLParentSelection();
            mengxu.algorithm.multiobjective.ParetoSetLearning.PSLParentSelection sp =
                    new mengxu.algorithm.multiobjective.ParetoSetLearning.PSLParentSelection();
            ec.select.TournamentSelection np = new ec.select.TournamentSelection();
            mp.setup(ms, new Parameter("select.tournament"));
            sp.setup(ps, new Parameter("select.tournament"));
            np.setup(ns, new Parameter("select.tournament"));
            int selected = mp.produceMOEAD(0, 0, ms, 0);
            int singleSelected = sp.produceMOEAD(0, 0, ps, 0), nsSelected = np.produce(0, ns, 0);
            check(selected == singleSelected && selected == nsSelected,
                    "Single-task selection seed=" + seed + " MPSLGP=" + selected + " PSLGP=" + singleSelected
                            + " NSGPII=" + nsSelected + " draws=" + ((CountingRandom) ns.random[0]).draws);
            check(((CountingRandom) ms.random[0]).draws == 7 && ((CountingRandom) ps.random[0]).draws == 7
                    && ((CountingRandom) ns.random[0]).draws == 7, "Seven actual candidates in all three methods");
        }
    }

    private static final class MultiBreeder extends PSLBreederKeepParetoFront {
        void copy(EvolutionState state, Population output) { loadElites(state, output); }
    }
    private static final class SingleBreeder extends mengxu.algorithm.multiobjective.ParetoSetLearning.PSLBreederKeepParetoFront {
        void copy(EvolutionState state, Population output) { loadElites(state, output); }
    }
    private static final class NSBreeder extends mengxu.algorithm.multiobjective.NSGPII.NSGA2BreederNoEnvironmentalSelection {
        void copy(EvolutionState state, Population output) { loadElites(state, output); }
    }

    private static void eliteCopies() {
        for (String method : new String[]{"MPSLGP", "PSLGP", "NSGPII"}) {
            int tasks = method.equals("MPSLGP") ? 3 : 1;
            int population = 5 * tasks, elites = tasks == 3 ? 10 : 3;
            Individual[] parents = new Individual[population];
            for (int index = 0; index < population; index++) {
                parents[index] = individual(method, index % tasks, index / tasks);
            }
            EvolutionState state = state(method, tasks, elites, 0, parents);
            Population output = (Population) state.population.emptyClone();
            if (method.equals("MPSLGP")) { new MultiBreeder().copy(state, output); }
            else if (method.equals("PSLGP")) { new SingleBreeder().copy(state, output); }
            else { new NSBreeder().copy(state, output); }
            Individual[] protectedCopies = Arrays.copyOfRange(output.subpops[0].individuals, population - elites, population);
            int[] counts = new int[tasks];
            for (Individual copy : protectedCopies) {
                check(copy != null && !copy.evaluated, "Elite must be re-evaluated on the current instance/preference");
                check(Arrays.stream(parents).noneMatch(parent -> parent == copy), "Independent elite copy");
                int task = method.equals("MPSLGP") ? ((PSLMultiObjectiveFitness) copy.fitness).getTaskIndex() : 0;
                counts[task]++;
            }
            for (int task = 0; task < tasks; task++) {
                check(counts[task] == FairComparisonProtocol.quota(elites, tasks, task, 0), "Per-task elite quotas");
            }
            Individual[] brood = new Individual[5 * population];
            for (int index = 0; index < brood.length - elites; index++) {
                brood[index] = individual(method, index % tasks, 100 + index);
            }
            System.arraycopy(protectedCopies, 0, brood, brood.length - elites, elites);
            state.population.subpops[0].individuals = brood;
            Individual[] detached = FairComparisonProtocol.detachElites(state);
            check(state.population.subpops[0].individuals.length == 5 * population - elites, "Only non-elites compete in preselection");
            FairComparisonProtocol.retainWithElites(state, detached, population);
            Individual[] survivors = state.population.subpops[0].individuals;
            check(survivors.length == population, "Exact retained population");
            int[] survivorCounts = new int[tasks];
            for (Individual survivor : survivors) {
                survivorCounts[method.equals("MPSLGP") ? ((PSLMultiObjectiveFitness) survivor.fitness).getTaskIndex() : 0]++;
            }
            for (int task = 0; task < tasks; task++) {
                check(survivorCounts[task] == FairComparisonProtocol.quota(population, tasks, task, 0),
                        "Protected elites do not change retained task population quotas");
            }
            for (Individual copy : protectedCopies) {
                check(Arrays.stream(survivors).anyMatch(individual -> individual == copy), "Elite survived even when children have different rankings");
            }
        }
    }
}
