package yimei.jss.gp.terminal;

import ec.EvolutionState;
import ec.Problem;
import ec.gp.*;
import ec.util.DecodeReturn;

import java.io.DataOutput;
import java.io.IOException;

/**
 * The ERC of the terminals.
 *
 * @author yimei
 */

public abstract class TerminalERC extends ERC {

    protected GPNode terminal;

    public String name() { return "TerminalERC"; }

    public void setTerminal(GPNode terminal) {
        this.terminal = terminal;
    }

    public GPNode getTerminal() {
        return terminal;
    }

    @Override
    public String toString() {
        return terminal.toString();
    }

    @Override
    public void eval(EvolutionState state, int thread, GPData input,
                     ADFStack stack, GPIndividual individual, Problem problem) {
        terminal.parent = parent;
        terminal.argposition = argposition;
        terminal.eval(state, thread, input, stack, individual, problem);
    }

    @Override
    public int hashCode() {
        return terminal.hashCode();
    }

    public boolean equals(Object other) {
        if (other instanceof TerminalERC) {
            TerminalERC o = (TerminalERC) other;
            return terminal.equals(o.terminal);
        }

        return false;
    }

    @Override
    public void writeNode(EvolutionState state, DataOutput output) throws IOException {
        terminal.writeNode(state, output);
    }

    @Override
    public boolean nodeEquals(GPNode node) {
        return equals(node);
    }

    @Override
    public String encode() {
        return terminal.toString();
    }

    @Override
    public GPNode readNode(final DecodeReturn dret)
    {
        int len = dret.data.length();
        int originalPos = dret.pos;

        // get my name
        String str2 = name() + "[";
        int len2 = str2.length();

        if (dret.pos + len2 >= len)  // uh oh, not enough space
            return null;

        // check it out
        for(int x=0; x < len2 ; x++)
            if (dret.data.charAt(dret.pos + x) != str2.charAt(x))
                return null;

        // looks good!  try to load this sucker.
        dret.pos += len2;
        ERC node = (ERC) lightClone();
        if (!node.decode(dret))
        { dret.pos = originalPos; return null; }  // couldn't decode it

        // the next item should be a "]"

        if (dret.pos >= len)
        { dret.pos = originalPos; return null; }
        if (dret.data.charAt(dret.pos) != ']')
        { dret.pos = originalPos; return null; }

        // Check to make sure that the ERC's all there is
        if (dret.data.length() > dret.pos+1)
        {
            char c = dret.data.charAt(dret.pos+1);
            if (!Character.isWhitespace(c) &&
                    c != ')' && c != '(') // uh oh
            { dret.pos = originalPos; return null; }
        }

        dret.pos++;

        return node;
    }
}
