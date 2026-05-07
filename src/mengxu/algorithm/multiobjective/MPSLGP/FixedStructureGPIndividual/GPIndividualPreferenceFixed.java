/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package mengxu.algorithm.multiobjective.MPSLGP.FixedStructureGPIndividual;

import ec.EvolutionState;
import ec.gp.*;
import ec.util.Code;
import ec.util.Parameter;

import java.io.*;
import java.util.Arrays;
import java.util.List;

/* 
 * GPIndividual.java
 * 
 * Created: Fri Aug 27 17:07:45 1999
 * By: Sean Luke
 */

/**
 * GPIndividual is an Individual used for GP evolution runs.
 * GPIndividuals contain, at the very least, a nonempty array of GPTrees.
 * You can use GPIndividual directly, or subclass it to extend it as
 * you see fit.
 
 * <P>GPIndividuals have two clone methods: clone() and lightClone().  clone() is
 * a deep clone method as usual.  lightClone() is a light clone which does not copy
 * the trees.
 *
 * <p>In addition to serialization for checkpointing, Individuals may read and write themselves to streams in three ways.
 *
 * <ul>
 * <li><b>writeIndividual(...,DataOutput)/readIndividual(...,DataInput)</b>&nbsp;&nbsp;&nbsp;This method
 * transmits or receives an individual in binary.  It is the most efficient approach to sending
 * individuals over networks, etc.  These methods write the evaluated flag and the fitness, then
 * call <b>readGenotype/writeGenotype</b>, which you must implement to write those parts of your 
 * Individual special to your function-- the default versions of readGenotype/writeGenotype throw errors.
 * You don't need to implement them if you don't plan on using read/writeIndividual.
 *
 * <li><b>printIndividual(...,PrintWriter)/readIndividual(...,LineNumberReader)</b>&nbsp;&nbsp;&nbsp;This
 * approach transmits or receives an indivdual in text encoded such that the individual is largely readable
 * by humans but can be read back in 100% by ECJ as well.  Because GPIndividuals are often very large,
 * <b>GPIndividual has overridden these methods -- they work differently than in Individual (the superclass).</b>  In specific:
 * <b>readIndividual</b> by default reads in the fitness and the evaluation flag, then calls <b>parseGenotype</b> 
 * to read in the trees (via GPTree.readTree(...)).
 * However <b>printIndividual</b> by default prints the fitness and evaluation flag, and prints all the trees
 * by calling GPTree.printTree(...).  It does not call <b>genotypeToString</b> at all.  This
 * is because it's very wasteful to build up a large string holding the printed form of the GPIndividual 
 * just to pump it out a stream once.
 *
 * <li><b>printIndividualForHumans(...,PrintWriter)</b>&nbsp;&nbsp;&nbsp;This
 * approach prints an individual in a fashion intended for human consumption only. Because GPIndividuals are often very large,
 * <b>GPIndividual has overridden this methods -- it works differently than in Individual (the superclass).</b>  In specific:
 * <b>printIndividual</b> by default prints the fitness and evaluation flag, and prints all the trees
 * by calling GPTree.printTreeForHumans(...).  It does not call <b>genotypeToStringForHumans</b> at all.  This
 * is because it's very wasteful to build up a large string holding the printed form of the GPIndividual 
 * just to pump it out a stream once.
 *
 * <p>In general, the various readers and writers do three things: they tell the Fitness to read/write itself,
 * they read/write the evaluated flag, and they read/write the GPTree array (by having each GPTree read/write
 * itself).  If you add instance variables to GPIndividual, you'll need to read/write those variables as well.


 <p><b>Parameters</b><br>
 <table>
 <tr><td valign=top><i>base</i>.<tt>numtrees</tt><br>
 <font size=-1>int &gt;= 1</font></td>
 <td valign=top>(number of trees in the GPIndividual)</td></tr>

 <tr><td valign=top><i>base</i>.<tt>tree.</tt><i>n</i><br>
 <font size=-1>classname, inherits or = ec.gp.GPTree</font></td>
 <td valign=top>(class of tree <i>n</i> in the individual)</td></tr>
 </table>

 <p><b>Default Base</b><br>
 gp.individual

 <p><b>Parameter bases</b><br>
 <table>
 <tr><td valign=top><i>base</i>.<tt>tree.</tt><i>n</i></td>
 <td>tree <i>n</i> in the individual</td></tr>
 </table>

 *
 * @author Sean Luke
 * @version 1.0 
 */

public class GPIndividualPreferenceFixed extends GPIndividual
    {
    public double[] preferences; //add by mengxu 2024.3.25

        /** Sets up a prototypical GPIndividual with those features which it
        shares with other GPIndividuals in its species, and nothing more. */

    public void setup(final EvolutionState state, final Parameter base)
        {
        super.setup(state,base);  // actually unnecessary (Individual.setup() is empty)
        }

        //add by mengxu 2024.3.25
        public void setPreferences(double[] preferences) {
            this.preferences = preferences;
        }


    /** Like clone(), but doesn't force the GPTrees to deep-clone themselves. */
    public GPIndividualPreferenceFixed lightClone()
        {
         GPIndividualPreferenceFixed myobj = (GPIndividualPreferenceFixed)super.lightClone();
        // a light clone
        return myobj;
        }

    }
