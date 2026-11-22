package mengxu.algorithm.multiobjective.MPSLGP;

import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.util.Parameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class FairComparisonProtocol {
    public static final String PARAMETER = "comparison.protocol";
    public static final String VERSION = "aligned-v2-generation50";
    public static final String OUTPUT_ROOT = "src/mengxu/ruleanalysis/MPSLGP/same-population-budget-aligned-v2-generation50";
    public static final int FINAL_GENERATION = 50;
    public static final int EVALUATED_GENERATIONS = FINAL_GENERATION + 1;
    public static final int RECOMMENDED_PARALLEL = 8;
    public static final String CROSSOVER = "yimei.jss.algorithm.multipletreegp.AllIndexAllSwapCrossoverPipeline";

    private FairComparisonProtocol() { }

    public static boolean enabled(EvolutionState state) {
        if (state.parameters == null) { return false; }
        String version = state.parameters.getString(new Parameter(PARAMETER), null);
        if (version == null) { return false; }
        if (!VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported comparison protocol: " + version);
        }
        return true;
    }

    public static int quota(int total, int tasks, int task, int offset) {
        if (total < 0 || tasks < 1 || task < 0 || task >= tasks) {
            throw new IllegalArgumentException("Invalid population/elite allocation");
        }
        return total / tasks + (Math.floorMod(task - Math.floorMod(offset, tasks), tasks) < total % tasks ? 1 : 0);
    }

    public static void configure(Map<String, String> parameters, int population, int tasks, int seed, int task) {
        int elites = Math.min(10, Math.max(0, population - tasks));
        parameters.put(PARAMETER, VERSION);
        parameters.put("comparison.population.total", Integer.toString(population));
        parameters.put("comparison.elites.total", Integer.toString(elites));
        parameters.put("comparison.tasks", Integer.toString(tasks));
        parameters.put("comparison.task", Integer.toString(task));
        parameters.put("breed.elite.0", Integer.toString(task < 0 ? elites : quota(elites, tasks, task, seed)));
        parameters.put("select.tournament.size", "7");
        parameters.put("select.tournament.pick-worst", "false");
        parameters.put("load-Pareto-front", "false");
        parameters.put("pop.subpop.0.species.pipe.source.0", CROSSOVER);
        parameters.put("pop.subpop.0.species.pipe.source.0.prob", "0.80");
        parameters.put("pop.subpop.0.species.pipe.source.1.prob", "0.15");
        parameters.put("pop.subpop.0.species.pipe.source.2.prob", "0.05");
    }

    private static int tasks(EvolutionState state) {
        return state instanceof GPRuleEvolutionStatePSL ? ((GPRuleEvolutionStatePSL) state).numTasks : 1;
    }

    private static int offset(EvolutionState state) {
        return state instanceof GPRuleEvolutionStatePSL ? ((GPRuleEvolutionStatePSL) state).taskAllocationOffset : 0;
    }

    private static int task(EvolutionState state, Individual individual) {
        return state instanceof GPRuleEvolutionStatePSL
                ? ((GPRuleEvolutionStatePSL) state).getTaskIndex(individual) : 0;
    }

    private static int eliteCount(EvolutionState state) {
        return state.parameters.getIntWithDefault(new Parameter("breed.elite.0"), null, 0);
    }

    private static void requireSinglePopulation(EvolutionState state) {
        if (state.population.subpops.length != 1 || state.breedthreads != 1) {
            throw new IllegalArgumentException("The aligned protocol requires one population and one breeding thread");
        }
    }

    public static void loadElites(EvolutionState state, Population newPopulation) {
        requireSinglePopulation(state);
        int elites = eliteCount(state);
        Individual[] output = newPopulation.subpops[0].individuals;
        if (elites < 0 || elites >= output.length) {
            throw new IllegalArgumentException("Elite allowance must be smaller than the population");
        }
        int write = output.length - elites;
        for (int task = 0; task < tasks(state); task++) {
            List<Individual> candidates = new ArrayList<>();
            for (Individual individual : state.population.subpops[0].individuals) {
                if (task(state, individual) == task) { candidates.add(individual); }
            }
            candidates.sort((first, second) -> first.fitness.betterThan(second.fitness) ? -1
                    : second.fitness.betterThan(first.fitness) ? 1 : 0);
            int count = quota(elites, tasks(state), task, offset(state));
            if (candidates.size() < count) {
                throw new IllegalStateException("Insufficient task " + task + " candidates for " + count + " elites");
            }
            for (int index = 0; index < count; index++) {
                Individual copy = (Individual) candidates.get(index).clone();
                copy.evaluated = false;
                output[write++] = copy;
            }
        }
    }

    public static Individual[] detachElites(EvolutionState state) {
        requireSinglePopulation(state);
        Individual[] candidates = state.population.subpops[0].individuals;
        int elites = eliteCount(state);
        if (elites < 0 || elites >= candidates.length) {
            throw new IllegalArgumentException("Invalid protected elite count");
        }
        Individual[] protectedElites = Arrays.copyOfRange(candidates, candidates.length - elites, candidates.length);
        for (Individual elite : protectedElites) {
            if (elite == null) { throw new IllegalStateException("Missing protected elite copy"); }
        }
        state.population.subpops[0].individuals = Arrays.copyOf(candidates, candidates.length - elites);
        return protectedElites;
    }

    public static void retainWithElites(EvolutionState state, Individual[] protectedElites, int populationSize) {
        int tasks = tasks(state);
        int[] remaining = new int[tasks];
        for (int task = 0; task < tasks; task++) {
            remaining[task] = quota(populationSize, tasks, task, offset(state));
        }
        for (Individual elite : protectedElites) {
            int task = task(state, elite);
            if (task < 0 || task >= tasks || --remaining[task] < 0) {
                throw new IllegalStateException("Protected elites exceed a task's population quota");
            }
        }
        Individual[] survivors = new Individual[populationSize];
        int count = 0;
        for (Individual child : state.population.subpops[0].individuals) {
            int task = task(state, child);
            if (task < 0 || task >= tasks) { throw new IllegalStateException("Invalid offspring task " + task); }
            if (remaining[task] > 0) {
                survivors[count++] = child;
                remaining[task]--;
            }
        }
        if (count + protectedElites.length != populationSize) {
            throw new IllegalStateException("Insufficient offspring for aligned task quotas: " + Arrays.toString(remaining));
        }
        System.arraycopy(protectedElites, 0, survivors, count, protectedElites.length);
        state.population.subpops[0].individuals = survivors;
    }
}
