package mengxu.algorithm.EvoSpeakV1;

import ec.EvolutionState;
import ec.Individual;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

import java.util.Arrays;

public class EvoSpeakEvaluator extends SimpleEvaluator {
    @Override
    public void evaluatePopulation(EvolutionState state) {
        for (Individual individual : state.population.subpops[0].individuals) {
            individual.evaluated = false;
        }
        MultipleRuleEvaluationModel model = (MultipleRuleEvaluationModel)
                ((MultipleTreeRuleOptimizationProblem) p_problem).getEvaluationModel();
        SchedulingSet scheduling = model.getSchedulingSet();
        for (Simulation simulation : scheduling.getSimulations()) {
            simulation.state = state;
        }
        super.evaluatePopulation(state);
        double[] baselines = new double[model.getObjectives().size()];
        Arrays.fill(baselines, 1.0);
        if (state.parameters.getBoolean(new Parameter("evospeak.normalization"), null, true)) {
            scheduling.lowerBoundsFromBenchmarkRule(model.getObjectives());
            for (int objective = 0; objective < baselines.length; objective++) {
                double total = 0.0;
                int replications = scheduling.getObjectiveLowerBoundMtx().getColumnDimension();
                for (int replication = 0; replication < replications; replication++) {
                    total += scheduling.getObjectiveLowerBound(objective, replication);
                }
                double baseline = total / replications;
                if (!Double.isFinite(baseline) || baseline < 0.0) {
                    throw new IllegalStateException("The benchmark rule produced an invalid normalization baseline.");
                }
                baselines[objective] = baseline > 1.0e-12 ? baseline : 1.0;
            }
        }
        for (Individual individual : state.population.subpops[0].individuals) {
            ((WeightedFitness) individual.fitness).setBaselines(baselines);
        }
        ((EvoSpeakEvolutionState) state).recordEvaluation(baselines);
    }
}