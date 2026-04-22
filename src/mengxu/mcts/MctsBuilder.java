package mengxu.mcts;

import ec.EvolutionState;
import ec.gp.GPFunctionSet;
import ec.gp.GPNode;
import ec.gp.GPNodeParent;
import ec.gp.GPType;
import ec.gp.koza.GPKozaDefaults;
import ec.gp.koza.KozaBuilder;
import ec.util.Parameter;

import java.util.ArrayList;
import java.util.List;

public class MctsBuilder extends KozaBuilder
    {
    public static final String P_HALFBUILDER = "half";
    public static final String P_PICKGROWPROBABILITY = "growp";

    /** The likelihood of using GROW over FULL. */
    public double pickGrowProbability;
    
    public Parameter defaultBase()
        {
        return GPKozaDefaults.base().push(P_HALFBUILDER);
        }

    public void setup(final EvolutionState state, final Parameter base)
        {
        super.setup(state,base);

        Parameter def = defaultBase();

        pickGrowProbability = state.parameters.getDoubleWithMax(
            base.push(P_PICKGROWPROBABILITY),
            def.push(P_PICKGROWPROBABILITY),0.0,1.0);
        if (pickGrowProbability < 0.0)
            state.output.fatal("The Pick-Grow Probability for HalfBuilder must be a double floating-point value between 0.0 and 1.0 inclusive.", base.push(P_MAXDEPTH),def.push(P_MAXDEPTH));
        }

        @Override
        public GPNode newRootedTree(EvolutionState state, GPType type, int thread, GPNodeParent parent, GPFunctionSet set, int argposition, int requestedSize) {
            return null;
        }

        @Override
        public GPNode newRootedTreeMcts(final EvolutionState state,
                                    final GPType type,
                                    final int thread,
                                    final GPNodeParent parent,
                                    final GPFunctionSet set,
                                    final int argposition,
                                    final int requestedSize,
                                    int treeNum
                                    ) {
            //todo: select the treeNum by mengxu 2021.04.08
            return growNodeBasedOnMcts(state, 0, state.random[thread].nextInt(maxDepth - minDepth + 1) + minDepth, type, thread, parent, argposition, set, treeNum, state.mctses.clone()[treeNum].getMctsRootNode());
        }

        @Override
        public GPNode newRootedTreeMctsMutation(final EvolutionState state,
                                        final GPType type,
                                        final int thread,
                                        final GPNodeParent parent,
                                        final GPFunctionSet set,
                                        final int argposition,
                                        final int requestedSize,
                                        int treeNum, MctsTreeNode mctsTreeNode
        ) {
            //todo: select the treeNum by mengxu 2021.04.08
            return growNodeBasedOnMcts(state, 0, state.random[thread].nextInt(maxDepth - minDepth + 1) + minDepth, type, thread, parent, argposition, set, treeNum, mctsTreeNode);
        }

    }