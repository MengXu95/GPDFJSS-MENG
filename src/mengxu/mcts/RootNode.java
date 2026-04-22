package mengxu.mcts;

import ec.EvolutionState;
import ec.Problem;
import ec.gp.ADFStack;
import ec.gp.GPData;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import yimei.jss.gp.data.DoubleData;

public class RootNode extends GPNode {

    public String toString() {
        return "mctsRoot";
    }

    @Override
    public void eval(EvolutionState state, int thread, GPData input, ADFStack stack, GPIndividual individual, Problem problem) {

    }

    /*
      public void checkConstraints(final EvolutionState state,
      final int tree,
      final GPIndividual typicalIndividual,
      final Parameter individualBase)
      {
      super.checkConstraints(state,tree,typicalIndividual,individualBase);
      if (children.length!=2)
      state.output.error("Incorrect number of children for node " +
      toStringForError() + " at " +
      individualBase);
      }
    */

//    public int expectedChildren() {
//        return 2;
//    }

}