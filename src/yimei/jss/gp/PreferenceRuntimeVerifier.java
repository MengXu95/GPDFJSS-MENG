package yimei.jss.gp;

import ec.EvolutionState;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import yimei.jss.jobshop.Job;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.Operation;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleevaluation.MultipleTreeMultipleRuleEvaluationModel;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.state.SystemState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Executes production GP/evaluator/phenotype code on a fixed frame, without scheduling jobs. */
final class PreferenceRuntimeVerifier {
    private static final List<Objective> OBJECTIVES = Arrays.asList(
            Objective.get("max-flowtime"), Objective.get("max-weighted-tardiness"));

    private PreferenceRuntimeVerifier() { }

    static void verify(EvolutionState state) {
        Frame frame = new Frame();
        FrameModel model = new FrameModel(frame);
        for (boolean individual : new boolean[]{false, true}) {
            for (int index = 0; index < 2; index++) {
                PreferenceStateAccess.setPreferenceIndex(state, index);
                frame.state = null;
                MultiObjectiveFitness fitness = new MultiObjectiveFitness();
                if (individual) {
                    model.evaluate(Collections.singletonList(fitness), Arrays.asList(frame.getSequencingRule(), frame.getRoutingRule()),
                            state, new GPIndividual());
                } else {
                    model.evaluate(Collections.singletonList(fitness), Arrays.asList(frame.getSequencingRule(), frame.getRoutingRule()), state);
                }
                require(frame.state == state, "Training evaluator did not bind its current preference state.");
                double[] expected = index == 0 ? new double[]{20,80} : new double[]{80,20};
                require(Arrays.equals(fitness.getObjectives(), expected), "Training evaluator used incorrect preference inputs.");
            }
        }
        PreferenceStateAccess.setPreferenceIndex(state, 1);
        frame.state = state;
        List<OperationOption> queue = Arrays.asList(frame.first, frame.second);
        for (RuleType type : new RuleType[]{RuleType.SEQUENCING, RuleType.ROUTING}) {
            PhenoCharacterisation phenotype = type == RuleType.ROUTING
                    ? new RoutingPhenoCharacterisation(GPRule.readFromLispExpression(type, "PT"),
                    Collections.singletonList(new RoutingDecisionSituation(queue, frame.getSystemState())))
                    : new SequencingPhenoCharacterisation(GPRule.readFromLispExpression(type, "PT"),
                    Collections.singletonList(new SequencingDecisionSituation(queue, frame.first.getWorkCenter(), frame.getSystemState())));
            for (String terminal : new String[]{"Pone", "Ptwo"}) {
                RecordingRule rule = new RecordingRule(type, terminal);
                phenotype.characteriseWithPreference(state, rule, new double[]{.2,.8});
                require(rule.observed.size() == 2, type + " phenotype did not evaluate both GP candidates.");
                double expected = terminal.equals("Pone") ? 20 : 80;
                for (double value : rule.observed) {
                    require(value == expected, type + " phenotype did not copy the preference mode and explicit weights.");
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Preference runtime self-check failed. " + message
                    + " Use Build > Rebuild Project and check the classpath.");
        }
    }

    private static final class RecordingRule extends GPRule {
        final List<Double> observed = new ArrayList<>();
        RecordingRule(RuleType type, String expression) {
            super(type, GPRule.readFromLispExpression(type, expression).getGPTree());
        }
        @Override
        public double priority(OperationOption operation, WorkCenter center, SystemState systemState) {
            double value = super.priority(operation, center, systemState);
            observed.add(value);
            return value;
        }
    }

    private static final class FrameModel extends MultipleTreeMultipleRuleEvaluationModel {
        FrameModel(Frame frame) {
            objectives = OBJECTIVES;
            schedulingSet = new SchedulingSet(Collections.singletonList(frame), Collections.singletonList(1), OBJECTIVES);
        }
    }

    private static final class Frame extends Simulation {
        final OperationOption first, second;
        private double[] values;
        Frame() {
            super(GPRule.readFromLispExpression(RuleType.SEQUENCING, "Pone"),
                    GPRule.readFromLispExpression(RuleType.ROUTING, "Ptwo"), 1, 1, 0);
            WorkCenter center = new WorkCenter(0);
            systemState.addWorkCenter(center);
            first = option(0, 2, center); second = option(1, 5, center);
        }
        private OperationOption option(int id, double time, WorkCenter center) {
            Job job = new Job(id, new ArrayList<>());
            Operation operation = new Operation(job, 0);
            job.addOperation(operation);
            OperationOption option = new OperationOption(operation, 0, time, center);
            operation.addOperationOption(option);
            return option;
        }
        @Override public void run() {
            values = new double[]{sequencingRule.priority(first, first.getWorkCenter(), systemState),
                    routingRule.priority(first, first.getWorkCenter(), systemState)};
        }
        @Override public double objectiveValue(Objective objective) { return values[OBJECTIVES.indexOf(objective)]; }
        @Override public void rerun() { run(); }
        @Override public void reset() { }
        @Override public void setup() { throw new UnsupportedOperationException("The self-check does not schedule jobs."); }
        @Override public void resetState() { values = null; }
        @Override public void rotateSeed() { throw new UnsupportedOperationException("The self-check uses fixed inputs."); }
        @Override public void generateJob() { throw new UnsupportedOperationException("The self-check does not schedule jobs."); }
        @Override public Simulation surrogate(int a, int b, int c) { throw new UnsupportedOperationException("No surrogate simulation in self-check."); }
        @Override public Simulation surrogateBusy(int a, int b, int c) { throw new UnsupportedOperationException("No surrogate simulation in self-check."); }
    }
}
