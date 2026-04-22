package yimei.jss.gp.terminal;

import ec.EvolutionState;
import ec.gp.*;
import ec.util.DecodeReturn;
import ec.util.Parameter;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import yimei.jss.gp.GPRuleEvolutionState;

import java.io.DataInput;
import java.io.IOException;

/**
 * The terminal ERC, with uniform selection.
 *
 * @author yimei
 */

public class TerminalERCUniform extends TerminalERC {

    @Override
    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        //Assume here we are dealing with simple gp
        int subPopNum = 0;
//        subPopNum = 1; //add by mengxu for checking 2022.07.26
        //todo: need modify when want to use more than one subpopulation 2022.07.26
        if (base.toString().endsWith("1")) {
            subPopNum = 1;
        }

        //modified by mengxu 2024.6.18
        if(state instanceof GPRuleEvolutionStatePSL){
            if(((GPRuleEvolutionStatePSL) state).preferenceTerminalHighPro){
                terminal = ((GPRuleEvolutionState)state).pickTerminalRandomWithPreferenceTerminalHighPro(subPopNum);
            }
            else{
                terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(subPopNum);
            }
        }
        else{
            terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(subPopNum);
        }

        //original
//        terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(subPopNum);
    }

    @Override
    public void resetNode(EvolutionState state, int thread) {
        //Assume here we are dealing with simple gp
        int subPopNum = 0;
//        subPopNum = 1; //add by mengxu for checking 2022.07.26
        //todo: need modify when want to use more than one subpopulation 2022.07.26
        //modified by mengxu 2024.6.18
        if(state instanceof GPRuleEvolutionStatePSL){
            if(((GPRuleEvolutionStatePSL) state).preferenceTerminalHighPro){
                terminal = ((GPRuleEvolutionState)state).pickTerminalRandomWithPreferenceTerminalHighPro(subPopNum);
            }
            else{
                terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(subPopNum);
            }
        }
        else{
            terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(subPopNum);
        }

        //original
//        terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(subPopNum);

        if (terminal instanceof ERC) {
            ERC ercTerminal = new DoubleERC();
            ercTerminal.resetNode(state, thread);
            terminal = ercTerminal;
        }
    }

    //============================use this one=================================
    //mutateERC will call this method
    @Override
    public void resetNode(EvolutionState state, int thread, GPFunctionSet set) {
        //Assume here we are dealing with simple gp
        int subPopNum = 0;
//        subPopNum = 1; //add by mengxu for checking 2022.07.26
        //todo: need modify when want to use more than one subpopulation 2022.07.26
        if (set.toString().endsWith("1")) {
            subPopNum = 1;
        }

        //fzhang random pick a terminal---original
        //modified by mengxu 2024.6.18
        if(state instanceof GPRuleEvolutionStatePSL){
            if(((GPRuleEvolutionStatePSL) state).preferenceTerminalHighPro){
                terminal = ((GPRuleEvolutionState)state).pickTerminalRandomWithPreferenceTerminalHighPro(subPopNum);
            }
            else{
                terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(subPopNum);
            }
        }
        else{
            terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(subPopNum);
        }

        //original
//        terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(subPopNum);

        //fzhang 2019.5.27 another terminal with different parameters
        //terminal = ((GPRuleEvolutionState)state).pickTerminalRandom(state, subPopNum);

        if (terminal instanceof ERC) {
            ERC ercTerminal = new DoubleERC();
            ercTerminal.resetNode(state, thread, set);
            terminal = ercTerminal;
        }
    }

    @Override
    public void mutateERC(EvolutionState state, int thread, GPFunctionSet set) {
        resetNode(state, thread, set);
    }

    //the following are added by mengxu 2024.1.9
    public String name() { return "TerminalERCUniform"; }

    /** Reads the node and its children from the form printed out by printRootedTree. */
    public static GPNode readRootedTree(int linenumber,
                                        DecodeReturn dret,
                                        GPType expectedType,
                                        GPFunctionSet set,
                                        GPNodeParent parent,
                                        int argposition,
                                        EvolutionState state)
    {
        final char REPLACEMENT_CHAR = '@';

        // eliminate whitespace if any
        boolean isTerminal = true;
        int len = dret.data.length();
        for(  ;  dret.pos < len &&
                Character.isWhitespace(dret.data.charAt(dret.pos)) ; dret.pos++);

        // if I'm out of space, complain

        if (dret.pos >= len)
            state.output.fatal("Reading line " + linenumber + ": " + "Premature end of tree structure -- did you forget a close-parenthesis?\nThe tree was" + dret.data);

        // if I've found a ')', complain
        if (dret.data.charAt(dret.pos) == ')')
        {
            StringBuilder sb = new StringBuilder(dret.data);
            sb.setCharAt(dret.pos,REPLACEMENT_CHAR);
            dret.data = sb.toString();
            state.output.fatal("Reading line " + linenumber + ": " + "Premature ')' which I have replaced with a '" + REPLACEMENT_CHAR + "', in tree:\n" + dret.data);
        }

        // determine if I'm a terminal or not
        if (dret.data.charAt(dret.pos) == '(')
        {
            isTerminal=false;
            dret.pos++;
            // strip following whitespace
            for(  ;  dret.pos < len &&
                    Character.isWhitespace(dret.data.charAt(dret.pos)) ; dret.pos++);
        }

        // check again if I'm out of space

        if (dret.pos >= len)
            state.output.fatal("Reading line " + linenumber + ": " + "Premature end of tree structure -- did you forget a close-parenthesis?\nThe tree was" + dret.data);

        // check again if I found a ')'
        if (dret.data.charAt(dret.pos) == ')')
        {
            StringBuilder sb = new StringBuilder(dret.data);
            sb.setCharAt(dret.pos,REPLACEMENT_CHAR);
            dret.data = sb.toString();
            state.output.fatal("Reading line " + linenumber + ": " + "Premature ')' which I have replaced with a '" + REPLACEMENT_CHAR + "', in tree:\n" + dret.data);
        }


        // find that node!
        GPNode[] gpfi = isTerminal ?
                ((GPRuleEvolutionState)state).getTerminals(0) :
                set.nonterminals[expectedType.type];

        GPNode node = null;
        for(int x=0;x<gpfi.length;x++)
            if ((node = gpfi[x].readNode(dret)) != null) break;

        // did I find one?

        if (node==null)
        {
            if (dret.pos!=0)
            {
                StringBuilder sb = new StringBuilder(dret.data);
                sb.setCharAt(dret.pos,REPLACEMENT_CHAR);
                dret.data = sb.toString();
            }
            else dret.data = "" + REPLACEMENT_CHAR + dret.data;
            state.output.fatal("Reading line " + linenumber + ": " + "I came across a symbol which I could not match up with a type-valid node.\nI have replaced the position immediately before the node in question with a '" + REPLACEMENT_CHAR + "':\n" + dret.data);
        }

        node.parent = parent;
        node.argposition = (byte)argposition;
        GPInitializer initializer = ((GPInitializer)state.initializer);

        // do its children
        for(int x=0;x<node.children.length;x++)
            node.children[x] = readRootedTree(linenumber,dret,node.constraints(initializer).childtypes[x],set,node,x,state);

        // if I'm not a terminal, look for a ')'

        if (!isTerminal)
        {
            // clear whitespace
            for(  ;  dret.pos < len &&
                    Character.isWhitespace(dret.data.charAt(dret.pos)) ; dret.pos++);

            if (dret.pos >= len)
                state.output.fatal("Reading line " + linenumber + ": " + "Premature end of tree structure -- did you forget a close-parenthesis?\nThe tree was" + dret.data);

            if (dret.data.charAt(dret.pos) != ')')
            {
                if (dret.pos!=0)
                {
                    StringBuilder sb = new StringBuilder(dret.data);
                    sb.setCharAt(dret.pos,REPLACEMENT_CHAR);
                    dret.data = sb.toString();
                }
                else dret.data = "" + REPLACEMENT_CHAR + dret.data;
                state.output.fatal("Reading line " + linenumber + ": " + "A nonterminal node has too many arguments.  I have put a '" +
                        REPLACEMENT_CHAR + "' just before the offending argument.\n" + dret.data);
            }
            else dret.pos++;  // get rid of the ')'
        }

        // return the node
        return node;
    }


//    public static GPNode readRootedTree(int linenumber,
//                           DecodeReturn dret,
//                           GPType expectedType,
//                           GPFunctionSet set,
//                           GPNodeParent parent,
//                           int argposition,
//                           EvolutionState state) {
//
//        int len = dret.data.length();
//        int originalPos = dret.pos;
//
//        // Skip leading whitespace
//        while (dret.pos < len && Character.isWhitespace(dret.data.charAt(dret.pos))) {
//            dret.pos++;
//        }
//
//        if (dret.pos >= len) {
//            state.output.fatal("Unexpected end of input while reading a node at line " + linenumber, new Parameter("gp.tree"));
//            return null;
//        }
//
//        char currentChar = dret.data.charAt(dret.pos);
//
//        // If it's an opening parenthesis, parse as a function node
//        if (currentChar == '(') {
//            dret.pos++; // Consume '('
//            // Skip whitespace after '('
//            while (dret.pos < len && Character.isWhitespace(dret.data.charAt(dret.pos))) {
//                dret.pos++;
//            }
//
//            // Read the function name
//            int start = dret.pos;
//            while (dret.pos < len && !Character.isWhitespace(dret.data.charAt(dret.pos)) && dret.data.charAt(dret.pos) != ')') {
//                dret.pos++;
//            }
//            String functionName = dret.data.substring(start, dret.pos);
//
//            // Look up the function node
//            GPNode[] functionSet = set.nonterminals[expectedType.type];
////            GPNode functionNode = set.forName(expectedType, functionName);
//            GPNode functionNode = null;
//            for(GPNode node:functionSet){
//                if(functionName.equals(node.name())){
//                    functionNode = node;
//                }
//            }
//            if (functionNode == null) {
//                state.output.fatal("Unknown function '" + functionName + "' at line " + linenumber, new Parameter("gp.tree"));
//                return null;
//            }
//
//            // Set the parent and argument position for the function node
//            functionNode.parent = parent;
//            functionNode.argposition = (byte) argposition;
//            GPInitializer initializer = ((GPInitializer)state.initializer);
//
//            // Parse children nodes
//            for (int i = 0; i < functionNode.children.length; i++) {
//                GPNode child = readRootedTree(linenumber, dret, functionNode.constraints(initializer).childtypes[i], set, functionNode, i, state);
//                if (child == null) {
//                    state.output.fatal("Failed to parse child node for function '" + functionName + "' at line " + linenumber, new Parameter("gp.tree"));
//                    return null;
//                }
//                functionNode.children[i] = child;
//            }
//
//            // Consume the closing parenthesis
//            if (dret.pos >= len || dret.data.charAt(dret.pos) != ')') {
//                state.output.fatal("Missing closing parenthesis for function '" + functionName + "' at line " + linenumber, new Parameter("gp.tree"));
//                return null;
//            }
//            dret.pos++; // Consume ')'
//
//            return functionNode;
//        }
//
//        // Otherwise, parse as a terminal node
//        int start = dret.pos;
//        while (dret.pos < len && !Character.isWhitespace(dret.data.charAt(dret.pos)) && dret.data.charAt(dret.pos) != ')') {
//            dret.pos++;
//        }
//        String terminalName = dret.data.substring(start, dret.pos);
//
//        // Look up the terminal node
//        GPNode[] terminalSet = ((GPRuleEvolutionState)state).getTerminals(0);
//        GPNode terminalNode = null;
//        for(GPNode node:terminalSet){
//            if(terminalName.equals(node.name())){
//                terminalNode = node;
//            }
//        }
//
//        if (terminalNode == null) {
//            state.output.fatal("Unknown terminal '" + terminalName + "' at line " + linenumber, new Parameter("gp.tree"));
//            return null;
//        }
//
//        // Set the parent and argument position for the terminal node
//        terminalNode.parent = parent;
//        terminalNode.argposition = (byte) argposition;
//
//        return terminalNode;
//    }

}
