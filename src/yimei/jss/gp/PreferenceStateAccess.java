package yimei.jss.gp;

import ec.EvolutionState;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import yimei.jss.simulation.DecisionSituation;

/** Shared access for the separate PSLGP and MPSLGP state hierarchies. */
public final class PreferenceStateAccess {
    public static final String RUNTIME_VERSION = "2";
    public static final String RUNTIME_PARAMETER = "mpslgp.preference-runtime-version";

    private PreferenceStateAccess() { }

    public static boolean supports(EvolutionState state) {
        return state instanceof GPRuleEvolutionStatePSL
                || state instanceof mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL;
    }

    public static int terminalNormalisation(EvolutionState state) {
        if (state instanceof GPRuleEvolutionStatePSL) {
            return ((GPRuleEvolutionStatePSL) state).terminalNormalisation;
        }
        if (state instanceof mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) {
            return ((mengxu.algorithm.multiobjective.MPSLGP.GPRuleEvolutionStatePSL) state).terminalNormalisation;
        }
        throw unsupported(state);
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
