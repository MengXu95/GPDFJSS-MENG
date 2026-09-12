package mengxu.algorithm.EvoSpeakV1;

import ec.EvolutionState;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import mengxu.complexsimulation.HeterogeneousSimulation;
import org.json.JSONArray;
import org.json.JSONObject;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.state.SystemState;

import java.util.List;

public final class RuleValidator {
    private final EvolutionState state;
    private final EvoSpeakConfig config;

    public RuleValidator(EvolutionState state, EvoSpeakConfig config) {
        this.state = state;
        this.config = config;
    }

    public static final class Result {
        public final GPIndividual individual;
        public final JSONObject evidence;

        Result(GPIndividual individual, JSONObject evidence) {
            this.individual = individual;
            this.evidence = evidence;
        }
    }

    public Result validate(RulePopulation.Rules rules) {
        GPIndividual individual = RulePopulation.parse(state, rules,
                config.integer("evospeak.max-tree-depth", 8), config.integer("evospeak.max-tree-nodes", 255));
        JSONObject evidence = new JSONObject().put("rules", RulePopulation.rules(individual).json());
        JSONArray evaluations = new JSONArray();
        evidence.put("evaluations", evaluations);
        String[] seeds = config.text("evospeak.validation.seeds", "17001,17002").split(",");
        if (seeds.length == 0 || seeds.length > 20) {
            throw new IllegalArgumentException("Validation requires between one and twenty seeds.");
        }
        List<Objective> objectives = ((MultipleTreeRuleOptimizationProblem) state.evaluator.p_problem).getObjectives();
        String base = "eval.problem.eval-model.sim-models.0.";
        for (String value : seeds) {
            long seed = Long.parseLong(value.trim());
            Budget budget = new Budget(config.integer("evospeak.validation.timeout-seconds", 20),
                    config.integer("evospeak.validation.max-decisions", 1000000));
            CheckedRule sequencing = new CheckedRule(RuleType.SEQUENCING, individual.trees[0], budget);
            CheckedRule routing = new CheckedRule(RuleType.ROUTING, individual.trees[1], budget);
            int machines = config.integer(base + "num-machines", 10);
            int warmup = Integer.parseInt(config.text("evospeak.validation.warmup", "100"));
            if (warmup < 0) {
                throw new IllegalArgumentException("Validation warmup must be nonnegative.");
            }
            HeterogeneousSimulation simulation = new HeterogeneousSimulation(seed, sequencing, routing, machines,
                    config.integer("evospeak.validation.jobs", 500), warmup,
                    config.integer(base + "min-num-operations", 1), config.integer(base + "max-num-operations", machines),
                    Double.parseDouble(config.text(base + "util-level", "0.85")),
                    Double.parseDouble(config.text(base + "due-date-factor", "1.5")), false);
            simulation.state = state;
            simulation.run();
            if (sequencing.calls == 0 || routing.calls == 0) {
                throw new IllegalArgumentException("Validation did not exercise both dispatching trees.");
            }
            JSONObject scores = new JSONObject();
            for (Objective objective : objectives) {
                double score = simulation.objectiveValue(objective);
                if (!Double.isFinite(score) || score < 0.0 || score >= Double.MAX_VALUE) {
                    throw new IllegalArgumentException("Rule failed simulation feasibility for " + objective + " at seed " + seed + ".");
                }
                scores.put(objective.toString(), score);
            }
            evaluations.put(new JSONObject().put("seed", seed).put("objectives", scores)
                    .put("sequencingPriorityCalls", sequencing.calls).put("routingPriorityCalls", routing.calls));
        }
        individual.evaluated = false;
        return new Result(individual, evidence.put("feasibleOnValidationScenarios", true));
    }

    private static final class Budget {
        final long deadline;
        final int maxDecisions;
        int decisions;

        Budget(int seconds, int maxDecisions) {
            deadline = System.nanoTime() + seconds * 1_000_000_000L;
            this.maxDecisions = maxDecisions;
        }

        void check() {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Rule validation was interrupted.");
            }
            if (++decisions > maxDecisions || System.nanoTime() > deadline) {
                throw new IllegalArgumentException("Rule validation exceeded its time or decision budget.");
            }
        }
    }

    private static final class CheckedRule extends GPRule {
        private final Budget budget;
        int calls;

        CheckedRule(RuleType type, GPTree tree, Budget budget) {
            super(type, tree);
            this.budget = budget;
        }

        @Override
        public double priority(OperationOption operation, WorkCenter machine, SystemState system) {
            budget.check();
            calls++;
            double priority = super.priority(operation, machine, system);
            if (!Double.isFinite(priority)) {
                throw new IllegalArgumentException("Non-finite " + getType() + " priority during validation.");
            }
            return priority;
        }
    }
}