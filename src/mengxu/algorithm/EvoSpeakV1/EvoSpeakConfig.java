package mengxu.algorithm.EvoSpeakV1;

import ec.util.Parameter;
import ec.util.ParameterDatabase;
import yimei.jss.jobshop.Objective;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class EvoSpeakConfig {
    public static final String DEFAULT_PARAMS = "src/mengxu/algorithm/EvoSpeakV1/evospeak.params";
    public final ParameterDatabase parameters;
    public final Path parameterFile;

    public EvoSpeakConfig(Path file, String... overrides) throws IOException {
        parameterFile = file.toAbsolutePath().normalize();
        List<String> arguments = new ArrayList<>();
        for (String override : overrides) {
            arguments.add("-p");
            arguments.add(override);
        }
        parameters = new ParameterDatabase(parameterFile.toFile(), arguments.toArray(new String[0]));
        String mode = text("evospeak.objective-mode", "multi");
        if (!mode.equals("single") && !mode.equals("multi")) {
            throw new IllegalArgumentException("evospeak.objective-mode must be single or multi.");
        }
        int count = mode.equals("single") ? 1 : 2;
        set("pop.subpop.0.species.fitness.num-objectives", count);
        set("pop.subpop.0.species.fitness.maximize", false);
        set("eval.problem.eval-model.objectives", count);
        for (int index = 0; index < count; index++) {
            String objective = text("evospeak.objective." + index, index == 0 ? "mean-flowtime" : "mean-weighted-tardiness");
            if (Objective.get(objective) == null) {
                throw new IllegalArgumentException("Unknown scheduling objective: " + objective);
            }
            set("eval.problem.eval-model.objectives." + index, objective);
        }
        if (integer("pop.subpops", 1) != 1 || integer("num-trees", 2) != 2
                || integer("breedthreads", 1) != 1 || integer("evalthreads", 1) != 1) {
            throw new IllegalArgumentException("EvoSpeakV1 currently uses one population, two trees, and one evaluation/breeding thread.");
        }
        if (integer("eval.problem.eval-model.sim-models", 1) != 1) {
            throw new IllegalArgumentException("EvoSpeakV1 currently validates one configured scheduling scenario.");
        }
        if (!text("terminals-from", "relative").equals("relative")
                || !text("eval.problem.eval-model", "").equals(
                "mengxu.complexsimulation.ruleevaluation.MultipleTreeMultipleRuleHeterogeneousEvaluationModel")) {
            throw new IllegalArgumentException("EvoSpeakV1 requires relative terminals and the heterogeneous evaluation model used by its validator.");
        }
        int populationSize = integer("pop.subpop.0.size", 100);
        int elites = Integer.parseInt(text("breed.elite.0", "2"));
        if (elites < 0 || elites > populationSize) {
            throw new IllegalArgumentException("breed.elite.0 must be between zero and the population size.");
        }
        double weightSum = 0.0;
        for (int index = 0; index < count; index++) {
            double weight = count == 1 ? 1.0 : Double.parseDouble(text("evospeak.weight." + index, "0.5"));
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("Objective weights must be finite and nonnegative.");
            }
            weightSum += weight;
        }
        if (!Double.isFinite(weightSum) || weightSum <= 0.0) {
            throw new IllegalArgumentException("At least one objective weight must be positive.");
        }
        String scenario = "eval.problem.eval-model.sim-models.0.";
        double utilization = Double.parseDouble(text(scenario + "util-level", "0.85"));
        double dueDateFactor = Double.parseDouble(text(scenario + "due-date-factor", "1.5"));
        int machines = integer(scenario + "num-machines", 10);
        int minOperations = integer(scenario + "min-num-operations", 1);
        int maxOperations = integer(scenario + "max-num-operations", machines);
        if (!Double.isFinite(utilization) || utilization <= 0.0 || utilization >= 1.0
                || !Double.isFinite(dueDateFactor) || dueDateFactor <= 0.0
                || minOperations > maxOperations || maxOperations > machines || machines < 2) {
            throw new IllegalArgumentException("Invalid heterogeneous scheduling scenario: check utilization, due date factor and machine/operation counts.");
        }
        String[] seeds = text("evospeak.validation.seeds", "17001,17002").split(",");
        if (seeds.length < 1 || seeds.length > 20) {
            throw new IllegalArgumentException("Specify between one and twenty validation seeds.");
        }
        for (String seed : seeds) {
            Long.parseLong(seed.trim());
        }
        if (integer("evospeak.max-tree-depth", 8) > integer("gp.koza.xover.maxdepth", 8)
                || integer("evospeak.max-tree-depth", 8) > integer("gp.koza.mutate.maxdepth", 8)) {
            throw new IllegalArgumentException("Warm-start tree depth cannot exceed GP operator depth limits.");
        }
        if (parameters.exists(new Parameter("llm.api-key"), null)) {
            throw new IllegalArgumentException("Use llm.api-key-env and an environment variable, not a plaintext API key in params.");
        }
    }

    public static EvoSpeakConfig fromArgs(String[] args) throws IOException {
        Path file = Paths.get(DEFAULT_PARAMS);
        List<String> overrides = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            if (args[index].equals("-file") && index + 1 < args.length) {
                file = Paths.get(args[++index]);
            } else if (args[index].equals("-p") && index + 1 < args.length) {
                overrides.add(args[++index]);
            } else {
                throw new IllegalArgumentException("Usage: -file <params> [-p name=value ...]");
            }
        }
        return new EvoSpeakConfig(file, overrides.toArray(new String[0]));
    }

    private EvoSpeakConfig(EvoSpeakConfig source) {
        parameterFile = source.parameterFile;
        parameters = new ParameterDatabase();
        parameters.addParent(source.parameters);
    }

    public EvoSpeakConfig copy() {
        return new EvoSpeakConfig(this);
    }

    public String text(String key, String fallback) {
        return parameters.getStringWithDefault(new Parameter(key), null, fallback).trim();
    }

    public int integer(String key, int fallback) {
        int value = parameters.getIntWithDefault(new Parameter(key), null, fallback);
        if (value < 1) {
            throw new IllegalArgumentException(key + " must be positive.");
        }
        return value;
    }

    public boolean flag(String key, boolean fallback) {
        return parameters.getBoolean(new Parameter(key), null, fallback);
    }

    public Path path(String key, String fallback) {
        Path path = Paths.get(text(key, fallback));
        return (path.isAbsolute() ? path : parameterFile.getParent().resolve(path)).normalize();
    }

    public void set(String key, Object value) {
        parameters.set(new Parameter(key), String.valueOf(value));
    }
}