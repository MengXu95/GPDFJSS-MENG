/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.LLM.WarmStart;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.multiobjective.MultiObjectiveDefaults;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Code;
import ec.util.DecodeReturn;
import ec.util.Parameter;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;

/*
 * MultiObjectiveFitness.java
 *
 * Created: Tue Aug 10 20:27:38 1999
 * By: Sean Luke
 */

/**
 * MultiObjectiveFitness is a subclass of Fitness which implements basic
 * multi-objective mechanisms suitable for being used with a variety of
 * multi-objective selection mechanisms, including ones using pareto-optimality.
 *
 * <p>
 * The object contains two items: an array of floating point values representing
 * the various multiple fitnesses, and a flag (maximize) indicating whether
 * higher is considered better. By default, isIdealFitness() always returns
 * false; you might want to override that, though it'd be unusual -- what is the
 * ideal fitness from the perspective of a pareto front?
 *
 * <p>
 * The object also contains maximum and minimum fitness values suggested for the
 * problem, on a per-objective basis. By default the maximum values are all 1.0
 * and the minimum values are all 0.0, but you can change these. Note that
 * maximum does not mean "best" unless maximize is true.
 *
 * <p>The class also contains utility methods or computing pareto dominance,
 * Pareto Fronts and Pareto Front Ranks, and distance in multiobjective space.
 * The default comparison operators use Pareto Dominance, though this is often
 * overridden by subclasses.
 *
 * <p>The fitness() method returns the maximum of the fitness values, which is
 * clearly nonsensical: you should not be using this method.
 *
 * <p>Subclasses of this class may add certain auxiliary fitness measures which
 * are printed out by MultiObjectiveStatistics along with the multiple objectives.
 * To have these values printed out, override the getAuxiliaryFitnessNames()
 * and getAuxiliaryFitnessValues() methods.
 *
 * <p>
 * <b>Parameters</b><br>
 * <table>
 * <tr>
 * <td valign=top><i>base</i>.<tt>num-objectives</tt><br>
 * (else)<tt>multi.num-objectives</tt><br>
 * <font size=-1>int &gt;= 1</font></td>
 * <td valign=top>(the number of fitnesses in the objectives array)</td>
 * </tr>
 *
 * <tr>
 * <td valign=top><i>base</i>.<tt>maximize</tt><br>
 * <font size=-1> bool = <tt>true</tt> (default) or <tt>false</tt></font></td>
 * <td valign=top>(are higher values considered "better"?)
 * </table>
 *
 * <tr>
 * <td valign=top><i>base</i>.<tt>maximize</tt>.<i>i</i<br>
 * <font size=-1> bool = <tt>true</tt> (default) or <tt>false</tt></font></td>
 * <td valign=top>(are higher values considered "better"?).  Overrides the
 * all-objecgive maximization setting.
 * </table>
 *
 * <tr>
 * <td valign=top><i>base</i>.<tt>max</tt><br>
 * <font size=-1> double (<tt>1.0</tt> default)</font></td>
 * <td valign=top>(maximum fitness value for all objectives)</table>
 *
 * <tr>
 * <td valign=top><i>base</i>.<tt>max</tt>.<i>i</i><br>
 * <font size=-1> double (<tt>1.0</tt> default)</font></td>
 * <td valign=top>(maximum fitness value for objective <i>i</i>. Overrides the
 * all-objective maximum fitness.)</table>
 *
 * <tr>
 * <td valign=top><i>base</i>.<tt>min</tt><br>
 * <font size=-1> double (<tt>0.0</tt> (default)</font></td>
 * <td valign=top>(minimum fitness value for all objectives)</table>
 *
 * <tr>
 * <td valign=top><i>base</i>.<tt>min</tt>.<i>i</i><br>
 * <font size=-1> double = <tt>0.0</tt> (default)</font></td>
 * <td valign=top>(minimum fitness value for objective <i>i</i>. Overrides the
 * all-objective minimum fitness.)</table>
 *
 * <p>
 * <b>Default Base</b><br>
 * multi.fitness
 *
 * @author Sean Luke
 * @version 1.1
 */

public class MultiObjectiveFitnessLLM extends MultiObjectiveFitness
    {

    public double[] weights;
    public double[] baseObjectives;
    public boolean normalization;

    public void setWeightsAndBaseObjectives(double[] weights, double[] baseObjectives, boolean normalization){
        this.weights = weights;
        this.baseObjectives = baseObjectives;
        this.normalization = normalization;
    }
    public double fitness()
        {
            if(weights.length != objectives.length){
                System.out.println("Error in ec.multiobjective.MultiObjectiveFitness! weights.length != objectives.length");
                return Double.POSITIVE_INFINITY;
            }
            else{
                if(normalization){
                    double fit = 0;
                    for (int x = 0; x < objectives.length; x++){
                        fit = fit + weights[x] * objectives[x]/baseObjectives[x];
                    }
                    return fit;
                }
                else{
                    double fit = 0;
                    for (int x = 0; x < objectives.length; x++){
                        fit = fit + weights[x] * objectives[x];
                    }
                    return fit;
                }
            }
        }

        public boolean betterThan(Fitness fitness)
        {
            return this.fitness() < fitness.fitness();
        }

    }
