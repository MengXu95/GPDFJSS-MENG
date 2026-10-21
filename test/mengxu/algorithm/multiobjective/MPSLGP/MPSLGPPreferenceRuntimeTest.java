package mengxu.algorithm.multiobjective.MPSLGP;

import ec.EvolutionState;
import ec.gp.GPFunctionSet;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.gp.PreferenceStateAccess;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.gp.terminal.TerminalERCUniform;
import yimei.jss.jobshop.Job;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.Operation;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleevaluation.MultipleTreeMultipleRuleEvaluationModel;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.state.SystemState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public final class MPSLGPPreferenceRuntimeTest {
    private static final List<Objective> OBJECTIVES = Arrays.asList(
            Objective.get("max-flowtime"), Objective.get("max-weighted-tardiness"));
    private static final double[][] WEIGHTS = {{.2, .8}, {.8, .2}};
    private static int assertions;

    public static void main(String[] args) throws Exception {
        PreferenceStateAccess.verifyRuntime();
        testTerminalModes();
        testInvalidPreferenceContexts();
        testTrainingDispatch();
        testTestDispatch();
        testPhenotypes();
        testActualDecisionInputs();
        testTerminalSampling();
        testMixedBuildRejection();
        check(!PreferenceStateAccess.supports(new EvolutionState()), "ordinary GP is not a preference state");
        System.out.println("PASS: MPSLGPPreferenceRuntimeTest (" + assertions + " assertions)");
    }

    private static void testTerminalModes() {
        ProbeSimulation simulation = new ProbeSimulation();
        for (int mode : new int[]{0, 1, 2}) {
            EvolutionState legacy = state(false, mode), current = state(true, mode);
            for (int index : new int[]{0, 1, 0}) {
                for (EvolutionState state : new EvolutionState[]{legacy, current}) {
                    state.generation = 19;
                    PreferenceStateAccess.setPreferenceIndex(state, index);
                    simulation.state = state;
                    double scale = mode == 2 ? 100 : 1;
                    close(scale * WEIGHTS[index][0], priority("Pone", simulation), "Pone, mode " + mode);
                    close(scale * WEIGHTS[index][1], priority("Ptwo", simulation), "Ptwo, mode " + mode);
                }
                simulation.state = legacy;
                double expected = priority("PT", simulation);
                simulation.state = current;
                close(expected, priority("PT", simulation), "feature normalisation parity, mode " + mode);
                if (mode == 0) { close(.2, expected, "normalised processing time"); }
                if (mode == 1) { close(1 / (1 + Math.exp(-2)), expected, "sigmoid processing time"); }
                if (mode == 2) { close(2, expected, "raw processing time"); }
            }
        }
        simulation.state = new EvolutionState();
        expectStateFailure(() -> priority("Pone", simulation), "ordinary GP cannot silently treat a preference as a constant");
        expectStateFailure(() -> priority("Ptwo", simulation), "both preference terminals require a context");
        simulation.state = null;
        expectStateFailure(() -> priority("Pone", simulation), "missing simulation state cannot silently continue");
        simulation.state = new EvolutionState();
        close(2, priority("PT", simulation), "ordinary GP processing time is unchanged");
    }

    private static void testInvalidPreferenceContexts() {
        double[][][] invalid = {null, {}, {null}, {{.2}}, {{Double.NaN,1}}, {{Double.POSITIVE_INFINITY,0}},
                {{-.1,1.1}}, {{.2,.2}}, {{20,80}}, {{0,0}}};
        ProbeSimulation simulation = new ProbeSimulation();
        for (boolean multitask : new boolean[]{false,true}) {
            for (double[][] weights : invalid) {
                EvolutionState state = state(multitask, 2);
                if (multitask) { ((PSLInitializer)state.initializer).weights = weights; }
                else { ((mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer)state.initializer).weights = weights; }
                simulation.state = state;
                expectStateFailure(() -> priority("Pone", simulation), "invalid weight vector rejected");
            }
            EvolutionState missing = state(multitask, 2);
            missing.initializer = null;
            simulation.state = missing;
            expectStateFailure(() -> priority("Ptwo", simulation), "missing initializer rejected");
            EvolutionState badIndex = state(multitask, 2);
            PreferenceStateAccess.setPreferenceIndex(badIndex, 2);
            simulation.state = badIndex;
            expectStateFailure(() -> priority("Pone", simulation), "out-of-range preference index rejected");
        }
        expectStateFailure(() -> PreferenceStateAccess.forPreference(new EvolutionState(), new double[]{.2,.8}),
                "surrogate cannot silently accept an unsupported source state");
        double[] supplied = {.25,.75};
        EvolutionState context = PreferenceStateAccess.forPreference(state(true, 2), supplied);
        supplied[0] = .6;
        close(.25, PreferenceStateAccess.preference(context, 0), "temporary context owns its explicit weight vector");
        check(PreferenceStateAccess.runtimeProvenance().get("preference.runtime.check").equals("PASSED"),
                "runtime self-check success is recorded");
        check(PreferenceStateAccess.runtimeProvenance().containsKey("runtime.sha256.yimei.jss.gp.terminal.AttributeGPNode"),
                "actual GP class-file fingerprint recorded");
    }

    private static void testTrainingDispatch() {
        ProbeSimulation simulation = new ProbeSimulation();
        ProbeEvaluationModel model = new ProbeEvaluationModel(simulation);
        List<AbstractRule> rules = Arrays.asList(rule(RuleType.SEQUENCING, "Pone"), rule(RuleType.ROUTING, "Ptwo"));
        EvolutionState state = state(true, 2);
        for (boolean withIndividual : new boolean[]{false, true}) {
            for (int index : new int[]{0, 1, 0}) {
                PreferenceStateAccess.setPreferenceIndex(state, index);
                state.generation = 19;
                simulation.state = state(false, 0);
                simulation.observed.clear();
                MultiObjectiveFitness fitness = new MultiObjectiveFitness();
                if (withIndividual) {
                    model.evaluate(Collections.singletonList(fitness), rules, state, new GPIndividual());
                } else {
                    model.evaluate(Collections.singletonList(fitness), rules, state);
                }
                check(simulation.state == state, "training binds the current MPSLGP state");
                check(simulation.observed.size() == 2, "both training replications evaluated real GP nodes");
                check(state.generation == 19, "training does not replace the generation with a preference index");
                verifyScores(fitness, simulation, index);
            }
        }
    }

    private static void testTestDispatch() {
        ProbeSimulation simulation = new ProbeSimulation();
        SchedulingSet set = set(simulation);
        GPRule sequencing = rule(RuleType.SEQUENCING, "Pone"), routing = rule(RuleType.ROUTING, "Ptwo");
        for (boolean multitask : new boolean[]{false, true}) {
            EvolutionState state = state(multitask, 2);
            for (int index : new int[]{1, 0, 1}) {
                simulation.state = null;
                simulation.observed.clear();
                MultiObjectiveFitness fitness = new MultiObjectiveFitness();
                sequencing.calcFitnessWithPreference(fitness, state, set, routing, OBJECTIVES, index);
                check(simulation.state == state, "testing binds both supported state types");
                check(simulation.observed.size() == 2, "both test replications evaluated real GP nodes");
                verifyScores(fitness, simulation, index);
            }
        }
    }

    private static void verifyScores(MultiObjectiveFitness fitness, ProbeSimulation simulation, int index) {
        for (int objective = 0; objective < 2; objective++) {
            double expected = 100 * WEIGHTS[index][objective];
            close(expected, fitness.getObjectives()[objective], "fitness uses the requested preference");
            for (double[] observed : simulation.observed) {
                close(expected, observed[objective], "GP nodes see the requested preference in each replication");
            }
        }
    }

    private static void testPhenotypes() {
        for (boolean multitask : new boolean[]{false, true}) {
            for (int mode : new int[]{0, 1, 2}) {
                for (RuleType type : new RuleType[]{RuleType.SEQUENCING, RuleType.ROUTING}) {
                    ProbeSimulation simulation = new ProbeSimulation();
                    EvolutionState source = state(multitask, mode);
                    source.generation = 19;
                    PreferenceStateAccess.setPreferenceIndex(source, 1);
                    simulation.state = source;
                    List<OperationOption> queue = Arrays.asList(simulation.first, simulation.second);
                    AbstractRule reference = rule(type, "PT");
                    PhenoCharacterisation phenotype;
                    if (type == RuleType.ROUTING) {
                        phenotype = new RoutingPhenoCharacterisation(source, reference, Collections.singletonList(
                                new RoutingDecisionSituation(queue, simulation.getSystemState())));
                    } else {
                        phenotype = new SequencingPhenoCharacterisation(source, reference, Collections.singletonList(
                                new SequencingDecisionSituation(queue, simulation.first.getWorkCenter(), simulation.getSystemState())));
                    }
                    double scale = mode == 2 ? 100 : 1;
                    for (String terminal : new String[]{"Pone", "Ptwo"}) {
                        RecordingRule learned = new RecordingRule(type, terminal);
                        int[] vector = phenotype.characteriseWithPreference(source, learned, WEIGHTS[0]);
                        check(vector.length == 1 && learned.observed.size() == 2, "one phenotype decision evaluates both options");
                        double explicitWeight = terminal.equals("Pone") ? .2 : .8;
                        for (double value : learned.observed) {
                            close(scale * explicitWeight, value, "phenotype copies the source mode and uses its explicit preference");
                        }
                        check(source.generation == 19, "temporary phenotype context does not change source generation");
                        check(preferenceIndex(source) == 1, "temporary phenotype context does not change source preference index");
                        learned.observed.clear();
                        phenotype.setReferenceRuleWithPreference(source, learned);
                        for (double value : learned.observed) {
                            close(scale * (1 - explicitWeight), value, "reference phenotype uses the current source preference");
                        }
                    }
                }
            }
        }
    }

    private static void testActualDecisionInputs() {
        for (int mode : new int[]{0, 1, 2}) {
            for (int index : new int[]{0, 1}) {
                double[] legacyFitness = null;
                for (boolean multitask : new boolean[]{false, true}) {
                    EvolutionState state = state(multitask, mode);
                    state.generation = 19;
                    PreferenceStateAccess.setPreferenceIndex(state, index);
                    HeterogeneousSimulation simulation = HeterogeneousSimulation.standardMissing(
                            0, null, null, 2, 80, 10, .7, 1.5);
                    CheckingRule sequencing = new CheckingRule(RuleType.SEQUENCING, mode, index);
                    CheckingRule routing = new CheckingRule(RuleType.ROUTING, mode, index);
                    MultiObjectiveFitness fitness = new MultiObjectiveFitness();
                    new ProbeEvaluationModel(simulation).evaluate(Collections.singletonList(fitness),
                            Arrays.asList(sequencing, routing), state, new GPIndividual());
                    check(sequencing.calls > 0 && routing.calls > 0, "real scheduling exercises both decision paths");
                    if (legacyFitness == null) { legacyFitness = fitness.getObjectives().clone(); }
                    else {
                        check(Arrays.equals(legacyFitness, fitness.getObjectives()),
                                "real PSLGP/MPSLGP scheduling objectives match for mode " + mode + ", preference " + index);
                    }
                }
            }
        }
    }

    private static void testTerminalSampling() {
        SamplingState state = new SamplingState();
        TerminalERCUniform terminal = new TerminalERCUniform();
        GPFunctionSet functions = new GPFunctionSet();
        functions.name = "f1";
        state.preferenceTerminalHighPro = true;
        terminal.resetNode(state, 0);
        terminal.resetNode(state, 0, functions);
        terminal.mutateERC(state, 0, functions);
        check(state.preferencePicks == 3 && state.uniformPicks == 0, "MPSLGP honours preference-biased reset and mutation");
        check(state.lastSubpopulation == 1, "function-set-specific terminal selection is preserved");
        state.preferenceTerminalHighPro = false;
        terminal.resetNode(state, 0);
        check(state.uniformPicks == 1, "disabled preference bias retains uniform selection");
    }

    private static void testMixedBuildRejection() throws Exception {
        Path temp = Files.createTempDirectory("mpslgp-stale-runtime-");
        try {
            String staleTerminal = String.join("\n",
                    "package yimei.jss.gp.terminal;",
                    "public class AttributeGPNode extends ec.gp.GPNode {",
                    " private final JobShopAttribute attribute;",
                    " public AttributeGPNode(JobShopAttribute a){attribute=a;children=new ec.gp.GPNode[0];}",
                    " public JobShopAttribute getJobShopAttribute(){return attribute;}",
                    " public String toString(){return attribute.getName();}",
                    " public int expectedChildren(){return 0;}",
                    " public void eval(ec.EvolutionState s,int t,ec.gp.GPData d,ec.gp.ADFStack a,ec.gp.GPIndividual i,ec.Problem p){",
                    "  yimei.jss.gp.CalcPriorityProblem c=(yimei.jss.gp.CalcPriorityProblem)p;",
                    "  ((yimei.jss.gp.data.DoubleData)d).value=s instanceof mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL",
                    "    ?attribute.value_Pareto_set_learning(c.getOperation(),c.getWorkCenter(),c.getSystemState(),s)",
                    "    :attribute.value(c.getOperation(),c.getWorkCenter(),c.getSystemState());",
                    " }",
                    "}");
            assertStaleBuildRejected(temp.resolve("terminal"), "AttributeGPNode", staleTerminal, "expected 20.0");
            String stalePhenotype = String.join("\n",
                    "package yimei.jss.niching;",
                    "public class RoutingPhenoCharacterisation extends PhenoCharacterisation {",
                    " private final java.util.List<yimei.jss.simulation.RoutingDecisionSituation> situations;",
                    " public RoutingPhenoCharacterisation(yimei.jss.rule.AbstractRule r,java.util.List<yimei.jss.simulation.RoutingDecisionSituation> s){super(r);situations=s;}",
                    " protected void calcReferenceIndexes(){}",
                    " protected void calcReferenceIndexes(ec.EvolutionState s){}",
                    " protected void calcReferenceIndexesWithPreference(ec.EvolutionState s){}",
                    " public int[] characterise(yimei.jss.rule.AbstractRule r){return new int[1];}",
                    " public int[] decision(yimei.jss.rule.AbstractRule r){return new int[1];}",
                    " public int[][] characterisePermutation(yimei.jss.rule.AbstractRule r){return new int[1][1];}",
                    " public int[] characteriseForMultitask(ec.EvolutionState s,yimei.jss.rule.AbstractRule r,int t){return new int[1];}",
                    " public int[] characteriseWithPreference(ec.EvolutionState ignored,yimei.jss.rule.AbstractRule r,double[] p){",
                    "  mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL s=new mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL();",
                    "  mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer i=new mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer();",
                    "  i.weights=new double[][]{p};s.initializer=i;s.terminalNormalisation=0;",
                    "  for(yimei.jss.simulation.RoutingDecisionSituation d:situations){d.getSystemState().getSimulation().state=s;",
                    "   for(yimei.jss.jobshop.OperationOption o:d.getQueue()){r.priority(o,o.getWorkCenter(),d.getSystemState());}}",
                    "  return new int[1];",
                    " }",
                    "}");
            assertStaleBuildRejected(temp.resolve("phenotype"), "RoutingPhenoCharacterisation", stalePhenotype, "ROUTING phenotype");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(temp)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) { Files.delete(path); }
            }
        }
    }

    private static void assertStaleBuildRejected(Path root, String className, String source, String expectedMessage) throws Exception {
        Files.createDirectories(root);
        Path sourceFile = root.resolve(className + ".java"), classes = root.resolve("classes");
        Files.createDirectory(classes);
        Files.writeString(sourceFile, source);
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        check(compiler != null, "mixed-build regression requires a JDK");
        check(compiler.run(null, null, null, "-encoding", "UTF-8", "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(), sourceFile.toString()) == 0, "stale-build fixture compiled");
        Path log = root.resolve("check.log");
        Process process = new ProcessBuilder(PaperExperimentFiles.javaExecutable(), "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-cp", classes + java.io.File.pathSeparator + System.getProperty("java.class.path"),
                MPSLGPPreferenceRuntimeCheck.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            check(process.waitFor(20, TimeUnit.SECONDS), "stale-build check terminates promptly");
            String output = Files.readString(log, StandardCharsets.UTF_8);
            check(process.exitValue() != 0 && output.contains("self-check failed")
                    && output.contains("Rebuild Project") && output.contains(expectedMessage),
                    "actual stale classpath refused before training");
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); }
        }
    }

    private static void expectStateFailure(Runnable action, String message) {
        boolean failed = false;
        try { action.run(); }
        catch (IllegalStateException expected) { failed = true; }
        check(failed, message);
    }

    private static EvolutionState state(boolean multitask, int mode) {
        if (multitask) {
            GPRuleEvolutionStatePSL state = new GPRuleEvolutionStatePSL();
            PSLInitializer initializer = new PSLInitializer();
            initializer.weights = WEIGHTS;
            state.initializer = initializer;
            state.terminalNormalisation = mode;
            state.initialMaxTerminals();
            state.maxTerminals.put("PROC_TIME", 10.0);
            return state;
        }
        mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL state =
                new mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL();
        mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer initializer =
                new mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer();
        initializer.weights = WEIGHTS;
        state.initializer = initializer;
        state.terminalNormalisation = mode;
        state.initialMaxTerminals();
        state.maxTerminals.put("PROC_TIME", 10.0);
        return state;
    }

    private static int preferenceIndex(EvolutionState state) {
        return state instanceof GPRuleEvolutionStatePSL ? ((GPRuleEvolutionStatePSL) state).evaluatePreferenceIndex
                : ((mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL) state).evaluatePreferenceIndex;
    }

    private static double priority(String expression, ProbeSimulation simulation) {
        return rule(RuleType.ROUTING, expression).priority(simulation.first, simulation.first.getWorkCenter(), simulation.getSystemState());
    }

    private static GPRule rule(RuleType type, String expression) {
        return GPRule.readFromLispExpression(type, expression);
    }

    private static SchedulingSet set(HeterogeneousSimulation simulation) {
        return new SchedulingSet(Collections.singletonList(simulation), Collections.singletonList(2), OBJECTIVES);
    }

    private static final class ProbeEvaluationModel extends MultipleTreeMultipleRuleEvaluationModel {
        ProbeEvaluationModel(HeterogeneousSimulation simulation) {
            objectives = OBJECTIVES;
            schedulingSet = set(simulation);
        }
    }

    private static final class RecordingRule extends GPRule {
        final List<Double> observed = new ArrayList<>();
        RecordingRule(RuleType type, String expression) {
            super(type, rule(type, expression).getGPTree());
        }
        @Override
        public double priority(OperationOption operation, WorkCenter workCenter, SystemState systemState) {
            double value = super.priority(operation, workCenter, systemState);
            observed.add(value);
            return value;
        }
    }

    private static final class CheckingRule extends GPRule {
        private final int mode, index;
        int calls;
        CheckingRule(RuleType type, int mode, int index) {
            super(type, rule(type, "(+ PT Pone)").getGPTree());
            this.mode = mode;
            this.index = index;
        }
        @Override
        public double priority(OperationOption operation, WorkCenter workCenter, SystemState systemState) {
            double value = super.priority(operation, workCenter, systemState);
            double processing = operation.getProcTime();
            if (mode == 0) {
                List<OperationOption> candidates = getType() == RuleType.ROUTING
                        ? operation.getOperation().getOperationOptions() : workCenter.getQueue();
                double maximum = 0;
                for (OperationOption candidate : candidates) { maximum = Math.max(maximum, candidate.getProcTime()); }
                check(maximum > 0, "normalised decision has a positive processing-time bound");
                processing /= maximum;
            } else if (mode == 1) {
                processing = 1 / (1 + Math.exp(-processing));
            }
            close(processing + (mode == 2 ? 100 : 1) * WEIGHTS[index][0], value,
                    "real " + getType() + " decision uses its current bounds and preference");
            calls++;
            return value;
        }
    }

    private static final class SamplingState extends GPRuleEvolutionStatePSL {
        int preferencePicks, uniformPicks, lastSubpopulation;
        @Override
        public GPNode pickTerminalRandom(int subpopulation) {
            uniformPicks++;
            lastSubpopulation = subpopulation;
            return new AttributeGPNode(JobShopAttribute.PROC_TIME);
        }
        @Override
        public GPNode pickTerminalRandomWithPreferenceTerminalHighPro(int subpopulation) {
            preferencePicks++;
            lastSubpopulation = subpopulation;
            return new AttributeGPNode(JobShopAttribute.Preference_One);
        }
    }

    /** Runs real GP trees through the production evaluation loops without scheduling jobs. */
    private static final class ProbeSimulation extends HeterogeneousSimulation {
        final OperationOption first, second;
        final List<double[]> observed = new ArrayList<>();
        private double[] scores;
        ProbeSimulation() {
            super(0, null, null, 2, 1, 0, 1, 2, .7, 1.5, false);
            first = option(100, 2);
            second = option(101, 5);
        }
        private OperationOption option(int id, double processingTime) {
            Job job = new Job(id, new ArrayList<>(), 0, 0, 100, 1);
            Operation operation = new Operation(job, 0);
            job.addOperation(operation);
            OperationOption option = new OperationOption(operation, 0, processingTime, systemState.getWorkCenter(0));
            operation.addOperationOption(option);
            return option;
        }
        @Override
        public void run() {
            scores = new double[]{sequencingRule.priority(first, first.getWorkCenter(), systemState),
                    routingRule.priority(first, first.getWorkCenter(), systemState)};
            observed.add(scores.clone());
        }
        @Override
        public void rerun() { run(); }
        @Override
        public void reset() { }
        @Override
        public double objectiveValue(Objective objective) {
            return scores[OBJECTIVES.indexOf(objective)];
        }
    }

    private static void close(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 1e-12, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new AssertionError(message); }
    }
}
