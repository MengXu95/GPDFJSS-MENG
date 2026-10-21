package yimei.jss.gp;

import ec.EvolutionState;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import yimei.jss.gp.data.DoubleData;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.simulation.DecisionSituation;
import yimei.jss.simulation.state.SystemState;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

/** Shared access for the separate PSLGP and MPSLGP state hierarchies. */
public final class PreferenceStateAccess {
    public static final String RUNTIME_VERSION = "2";
    public static final String RUNTIME_PARAMETER = "mpslgp.preference-runtime-version";
    private static volatile boolean verified;

    private PreferenceStateAccess() { }

    public static boolean supports(EvolutionState state) {
        return state instanceof GPRuleEvolutionStatePSL
                || state instanceof mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL;
    }

    public static double preference(EvolutionState state, int objectiveIndex) {
        double[][] weights;
        int index;
        if (state instanceof GPRuleEvolutionStatePSL) {
            if (!(state.initializer instanceof mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer)) {
                throw new IllegalStateException("PSLGP preference state has no matching PSLInitializer.");
            }
            weights = ((mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer) state.initializer).weights;
            index = ((GPRuleEvolutionStatePSL) state).evaluatePreferenceIndex;
        } else if (state instanceof mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) {
            if (!(state.initializer instanceof mengxu.algorithm.multiobjective.MPSLGP.PSLInitializer)) {
                throw new IllegalStateException("MPSLGP preference state has no matching PSLInitializer.");
            }
            weights = ((mengxu.algorithm.multiobjective.MPSLGP.PSLInitializer) state.initializer).weights;
            index = ((mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) state).evaluatePreferenceIndex;
        } else {
            throw new IllegalStateException("A preference terminal needs a PSLGP or MPSLGP state; check simulation-state binding.");
        }
        if (weights == null || index < 0 || index >= weights.length || weights[index] == null || weights[index].length < 2) {
            throw new IllegalStateException("Missing preference vector or invalid preference index " + index + ".");
        }
        double[] vector = weights[index];
        double sum = 0;
        for (double weight : vector) {
            if (!Double.isFinite(weight) || weight < 0 || weight > 1) {
                throw new IllegalStateException("Preference weights must be finite and in [0,1] at index " + index + ".");
            }
            sum += weight;
        }
        if (Math.abs(sum - 1) > 1e-9) {
            throw new IllegalStateException("Preference weights must sum to one at index " + index + "; got " + sum + ".");
        }
        if (objectiveIndex < 0 || objectiveIndex >= vector.length) {
            throw new IllegalArgumentException("Preference objective index is outside the vector: " + objectiveIndex);
        }
        return vector[objectiveIndex];
    }

    public static void validateCurrentPreference(EvolutionState state) {
        terminalNormalisation(state);
        preference(state, 0);
    }

    public static GPRuleEvolutionStatePSL forPreference(EvolutionState source, double[] vector) {
        if (!supports(source) || vector == null) {
            throw new IllegalStateException("Preference characterisation needs a supported source state and explicit weights.");
        }
        GPRuleEvolutionStatePSL context = new GPRuleEvolutionStatePSL();
        mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer initializer =
                new mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer();
        initializer.weights = new double[][]{vector.clone()};
        context.initializer = initializer;
        context.terminalNormalisation = terminalNormalisation(source);
        validateCurrentPreference(context);
        return context;
    }

    public static synchronized void verifyRuntime() {
        if (verified) { return; }
        double[][] weights = {{.2,.8},{.8,.2}};
        mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL multi =
                new mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL();
        mengxu.algorithm.multiobjective.MPSLGP.PSLInitializer multiInitializer =
                new mengxu.algorithm.multiobjective.MPSLGP.PSLInitializer();
        multiInitializer.weights = weights; multi.initializer = multiInitializer; multi.terminalNormalisation = 2;
        GPRuleEvolutionStatePSL single = new GPRuleEvolutionStatePSL();
        mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer singleInitializer =
                new mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer();
        singleInitializer.weights = weights; single.initializer = singleInitializer; single.terminalNormalisation = 2;
        for (EvolutionState state : new EvolutionState[]{multi, single}) {
            state.generation = 19;
            for (int index = 0; index < weights.length; index++) {
                setPreferenceIndex(state, index);
                verifyValue(state, "Pone", weights[index][0] * 100);
                verifyValue(state, "Ptwo", weights[index][1] * 100);
                verifyValue(state, "(+ Pone Ptwo)", 100);
            }
        }
        PreferenceRuntimeVerifier.verify(multi);
        PreferenceRuntimeVerifier.verify(single);
        boolean rejected = false;
        try { nodeValue(new EvolutionState(), "Pone"); }
        catch (IllegalStateException expected) { rejected = true; }
        if (!rejected) { throw runtimeFailure("A preference terminal silently accepted a non-preference state."); }
        verified = true;
    }

    private static void verifyValue(EvolutionState state, String expression, double expected) {
        double actual = nodeValue(state, expression);
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1e-12) {
            throw runtimeFailure(state.getClass().getName() + " evaluates " + expression
                    + " as " + actual + ", expected " + expected + ".");
        }
    }

    private static double nodeValue(EvolutionState state, String expression) {
        WorkCenter center = new WorkCenter(0);
        CalcPriorityProblem context = new CalcPriorityProblem(
                new OperationOption(null, 0, 1, center), center, new SystemState());
        DoubleData result = new DoubleData();
        GPRule.readFromLispExpression(RuleType.ROUTING, expression).getGPTree().child.eval(
                state, 0, result, null, null, context);
        return result.value;
    }

    private static IllegalStateException runtimeFailure(String detail) {
        return new IllegalStateException("Preference runtime self-check failed. " + detail
                + " Use Build > Rebuild Project and check the classpath. AttributeGPNode loaded from "
                + AttributeGPNode.class.getProtectionDomain().getCodeSource().getLocation());
    }

    public static Map<String, String> runtimeProvenance() {
        verifyRuntime();
        Map<String, String> result = new TreeMap<>();
        result.put("preference.runtime.version", RUNTIME_VERSION);
        result.put("preference.runtime.check", "PASSED");
        Class<?>[] components = {PreferenceStateAccess.class, AttributeGPNode.class, JobShopAttribute.class,
                PreferenceRuntimeVerifier.class,
                mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL.class,
                mengxu.algorithm.multiobjective.MPSLGP.PSLInitializer.class,
                yimei.jss.ruleevaluation.MultipleTreeMultipleRuleEvaluationModel.class,
                yimei.jss.niching.RoutingPhenoCharacterisation.class,
                yimei.jss.niching.SequencingPhenoCharacterisation.class,
                yimei.jss.jobshop.Operation.class, yimei.jss.simulation.event.JobArrivalEvent.class,
                yimei.jss.simulation.event.ProcessFinishEvent.class};
        for (Class<?> component : components) {
            try (InputStream input = component.getResourceAsStream(component.getSimpleName() + ".class")) {
                if (input == null) { throw new IllegalStateException("Cannot fingerprint " + component.getName()); }
                result.put("runtime.sha256." + component.getName(),
                        mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.sha256(input.readAllBytes()));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot record preference runtime class " + component.getName(), e);
            }
        }
        return result;
    }

    public static int terminalNormalisation(EvolutionState state) {
        int mode;
        if (state instanceof GPRuleEvolutionStatePSL) {
            mode = ((GPRuleEvolutionStatePSL) state).terminalNormalisation;
        } else if (state instanceof mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) {
            mode = ((mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) state).terminalNormalisation;
        } else {
            throw unsupported(state);
        }
        if (mode < 0 || mode > 2) { throw new IllegalStateException("Unsupported terminal-normalisation mode: " + mode); }
        return mode;
    }

    public static boolean preferenceTerminalHighPro(EvolutionState state) {
        if (state instanceof GPRuleEvolutionStatePSL) {
            return ((GPRuleEvolutionStatePSL) state).preferenceTerminalHighPro;
        }
        if (state instanceof mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) {
            return ((mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) state).preferenceTerminalHighPro;
        }
        throw unsupported(state);
    }

    public static void setPreferenceIndex(EvolutionState state, int index) {
        if (state instanceof GPRuleEvolutionStatePSL) {
            ((GPRuleEvolutionStatePSL) state).evaluatePreferenceIndex = index;
        } else if (state instanceof mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) {
            ((mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) state).evaluatePreferenceIndex = index;
        } else {
            throw unsupported(state);
        }
    }

    public static void initialMaxTerminals(EvolutionState state) {
        if (state instanceof GPRuleEvolutionStatePSL) {
            ((GPRuleEvolutionStatePSL) state).initialMaxTerminals();
        } else if (state instanceof mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) {
            ((mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) state).initialMaxTerminals();
        } else {
            throw unsupported(state);
        }
    }

    public static void updateMaxTerminals(EvolutionState state, DecisionSituation situation) {
        if (state instanceof GPRuleEvolutionStatePSL) {
            ((GPRuleEvolutionStatePSL) state).updateMaxTerminals(situation);
        } else if (state instanceof mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) {
            ((mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) state).updateMaxTerminals(situation);
        } else {
            throw unsupported(state);
        }
    }

    public static void updateNormalisationBounds(EvolutionState state, DecisionSituation situation) {
        if (supports(state) && terminalNormalisation(state) == 0) {
            updateMaxTerminals(state, situation);
        }
    }

    private static IllegalArgumentException unsupported(EvolutionState state) {
        return new IllegalArgumentException("Expected a PSLGP or MPSLGP preference state, got "
                + (state == null ? "null" : state.getClass().getName()));
    }
}
