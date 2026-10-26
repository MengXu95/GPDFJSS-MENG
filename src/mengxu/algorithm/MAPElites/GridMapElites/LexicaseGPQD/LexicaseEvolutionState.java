package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import ec.EvolutionState;
import ec.Individual;
import ec.util.Parameter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import mengxu.algorithm.lexicaseselection.GPRuleEvolutionStateOneInstanceMultiCase;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;

public final class LexicaseEvolutionState extends GPRuleEvolutionStateOneInstanceMultiCase {
    Path outputRoot;
    String method;
    int runId;
    int snapshotInterval;
    double evaluationSeconds;
    private boolean outputOwned;

    @Override public void setup(EvolutionState state, Parameter base) {
        super.setup(state, base);
        if (evalthreads != 1 || breedthreads != 1
                || parameters.getIntWithDefault(new Parameter("pop.subpops"), null, 0) != 1
                || parameters.getIntWithDefault(new Parameter("eval.num-tests"), null, 1) != 1
                || parameters.getBoolean(new Parameter("checkpoint"), null, false)
                || parameters.getBoolean(new Parameter("quit-on-run-complete"), null, false)
                || parameters.exists(new Parameter("evaluations"), null)
                || parameters.getIntWithDefault(new Parameter("pop.subpop.0.species.ind.numtrees"), null, 0) != 2
                || !"relative".equals(parameters.getString(new Parameter("terminals-from"), null))
                || parameters.getBoolean(new Parameter("include-erc"), null, false)) {
            throw new IllegalArgumentException("Require one population/thread/test, two trees, relative terminals, no ERC/checkpoint/early-stop and a generation-based budget");
        }
        if (!(evaluator.p_problem instanceof MultipleTreeRuleOptimizationProblem)
                || !(((MultipleTreeRuleOptimizationProblem) evaluator.p_problem).getEvaluationModel()
                instanceof OneInstanceEvaluation) || !(statistics instanceof PopulationStatistics)) {
            throw new IllegalArgumentException("Use the isolated Lexicase evaluation/statistics classes");
        }
        method = parameters.getString(new Parameter("lexqd.method"), null);
        if (!"LexicaseGPf".equals(method) && !"LexicaseGP".equals(method)) {
            throw new IllegalArgumentException("lexqd.method must be LexicaseGPf or LexicaseGP");
        }
        if (model().isRotatable() != method.equals("LexicaseGP")) {
            throw new IllegalArgumentException("Method label does not match fixed/rotating training");
        }
        runId = parameters.getIntWithDefault(new Parameter("lexqd.run-id"), null, 0);
        snapshotInterval = parameters.getIntWithDefault(new Parameter("lexqd.snapshot-interval"), null, 20);
        String target = parameters.getString(new Parameter("lexqd.output-root"), null);
        if (target == null || runId < 0 || snapshotInterval < 1) {
            throw new IllegalArgumentException("A new output directory, nonnegative run ID and positive snapshot interval are required");
        }
        outputRoot = Paths.get(target).toAbsolutePath().normalize();
        try {
            if (outputRoot.getParent() == null || Files.exists(outputRoot)) {
                throw new IllegalArgumentException("Refusing to overwrite output: " + outputRoot);
            }
            Files.createDirectories(outputRoot.getParent());
            Files.createDirectory(outputRoot);
            outputOwned = true;
        } catch (IOException e) { throw new UncheckedIOException(e); }
        genForUseLS = -1;
    }

    OneInstanceEvaluation model() {
        return (OneInstanceEvaluation) ((MultipleTreeRuleOptimizationProblem) evaluator.p_problem).getEvaluationModel();
    }

    @Override public int evolve() {
        statistics.preEvaluationStatistics(this);
        long start = System.nanoTime();
        evaluator.evaluatePopulation(this);
        evaluationSeconds = (System.nanoTime() - start) / 1e9;
        boolean anyValid = false;
        for (Individual individual : population.subpops[0].individuals) {
            LexicaseFitness fitness = (LexicaseFitness) individual.fitness;
            if (!individual.evaluated || fitness.multiInstanceMultiObjectiveFitness.length != model().caseCount()
                    || fitness.evaluatedGeneration() != generation
                    || fitness.trainingSeed() != model().trainingSeed()) {
                throw new IllegalStateException("Population members were evaluated on different batches");
            }
            anyValid |= fitness.isValidEvaluation();
        }
        statistics.postEvaluationStatistics(this);
        if (!anyValid) { throw new IllegalStateException("No valid member in evaluated generation " + generation); }
        if (generation == numGenerations - 1) { return R_FAILURE; }
        statistics.prePreBreedingExchangeStatistics(this);
        population = exchanger.preBreedingExchangePopulation(this);
        statistics.postPreBreedingExchangeStatistics(this);
        String shutdown = exchanger.runComplete(this);
        if (shutdown != null) { throw new IllegalStateException("Unexpected exchanger shutdown: " + shutdown); }
        parentIndex.clear();
        parentFitness.clear();
        parentsIndexPairForCrossover.clear();
        parentIndexForMutation.clear();
        allGenerationCaseNumber.add(new double[model().caseCount()]);
        statistics.preBreedingStatistics(this);
        population = breeder.breedPopulation(this);
        statistics.postBreedingStatistics(this);
        statistics.prePostBreedingExchangeStatistics(this);
        population = exchanger.postBreedingExchangePopulation(this);
        statistics.postPostBreedingExchangeStatistics(this);
        model().rotate();
        for (Individual individual : population.subpops[0].individuals) { individual.evaluated = false; }
        generation++;
        return R_NOTDONE;
    }

    @Override public void run(int condition) {
        if (condition != C_STARTED_FRESH) { throw new UnsupportedOperationException("Only fresh isolated runs are supported"); }
        try {
            startFresh();
            int result = R_NOTDONE;
            while (result == R_NOTDONE) { result = evolve(); }
            finish(result);
        } catch (RuntimeException failure) {
            if (outputOwned && statistics instanceof PopulationStatistics) {
                ((PopulationStatistics) statistics).failed(this, failure);
            }
            throw failure;
        }
    }
}
