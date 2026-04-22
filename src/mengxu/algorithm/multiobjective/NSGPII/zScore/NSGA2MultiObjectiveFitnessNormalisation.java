/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multiobjective.NSGPII.zScore;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Code;
import ec.util.Parameter;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;

/* 
 * NSGA2MultiObjectiveFitness.java
 * 
 * Created: Thu Feb 04 2010
 * By: Faisal Abidi and Sean Luke
 */

/**
 * NSGA2MultiObjectiveFitness is a subclass of MultiObjeciveFitness which
 * adds auxiliary fitness measures (sparsity, rank) largely used by MultiObjectiveStatistics.
 * It also redefines the comparison measures to compare based on rank, and break ties
 * based on sparsity. 
 *
 */

public class NSGA2MultiObjectiveFitnessNormalisation extends MultiObjectiveFitness
    {
    public static final String NSGA2_RANK_PREAMBLE = "Rank: ";
    public static final String NSGA2_SPARSITY_PREAMBLE = "Sparsity: ";

    public String[] getAuxilliaryFitnessNames() { return new String[] { "Rank", "Sparsity" }; }
    public double[] getAuxilliaryFitnessValues() { return new double[] { rank, sparsity }; }
        
    /** Pareto front rank measure (lower ranks are better) */
    public int rank;

    /** Sparsity along front rank measure (higher sparsity is better) */
    public double sparsity;

    /** The various fitnesses. */
    public double[] normalisedObjectives; // values range from 0 (worst) to 1 INCLUSIVE added by mengxu 2023.04.20

    public EvolutionState currentState;

    public double[] biasWeight;

    public void setup(EvolutionState state, Parameter base)
    {
        super.setup(state, base); // unnecessary really

        this.currentState = state; //added by mengxu 2023.04.18

        //add by mengu 2023.06.15
        biasWeight = new double[this.getNumObjectives()];
        for(int i=0; i<this.getNumObjectives(); i++){
            biasWeight[i] = 1;
        }
    }

    public String fitnessToString()
        {
        return super.fitnessToString() + "\n" + NSGA2_RANK_PREAMBLE + Code.encode(rank) + "\n" + NSGA2_SPARSITY_PREAMBLE + Code.encode(sparsity);
        }

    public String fitnessToStringForHumans()
        {
        return super.fitnessToStringForHumans() + "\n" + NSGA2_RANK_PREAMBLE + rank + "\n" + NSGA2_SPARSITY_PREAMBLE + sparsity;
        }

    public void readFitness(final EvolutionState state, final LineNumberReader reader) throws IOException
        {
        super.readFitness(state, reader);
        rank = Code.readIntegerWithPreamble(NSGA2_RANK_PREAMBLE, state, reader);
        sparsity = Code.readDoubleWithPreamble(NSGA2_SPARSITY_PREAMBLE, state, reader);
        }

    public void writeFitness(final EvolutionState state, final DataOutput dataOutput) throws IOException
        {
        super.writeFitness(state, dataOutput);
        dataOutput.writeInt(rank);
        dataOutput.writeDouble(sparsity);
        writeTrials(state, dataOutput);
        }

    public void readFitness(final EvolutionState state, final DataInput dataInput) throws IOException
        {
        super.readFitness(state, dataInput);
        rank = dataInput.readInt();
        sparsity = dataInput.readDouble();
        readTrials(state, dataInput);
        }

    public boolean equivalentTo(Fitness _fitness)
        {
        NSGA2MultiObjectiveFitnessNormalisation other = (NSGA2MultiObjectiveFitnessNormalisation) _fitness;
        return (rank == ((NSGA2MultiObjectiveFitnessNormalisation) _fitness).rank) &&
            (sparsity == other.sparsity);
        }

    /**
     * We specify the tournament selection criteria, Rank (lower
     * values are better) and Sparsity (higher values are better)
     */
    public boolean betterThan(Fitness _fitness)
        {
        NSGA2MultiObjectiveFitnessNormalisation other = (NSGA2MultiObjectiveFitnessNormalisation) _fitness;
        // Rank should always be minimized.
        if (rank < ((NSGA2MultiObjectiveFitnessNormalisation) _fitness).rank)
            return true;
        else if (rank > ((NSGA2MultiObjectiveFitnessNormalisation) _fitness).rank)
            return false;

//        return this.currentState.random[0].nextBoolean(0.5);
        // otherwise try sparsity
        return (sparsity > other.sparsity);
        }

    public void normalisation(double[] means, double[] stds)
        {
        int length = this.objectives.length;
        this.normalisedObjectives = new double[length];
//        if(this.objectives[0] >= Double.POSITIVE_INFINITY || this.objectives[0] >= Double.MAX_VALUE){
//            for(int i=0; i<length; i++){
//                this.normalisedObjectives[i] = Double.POSITIVE_INFINITY;
//            }
//        }
//        else{
            for(int i=0; i<length; i++){
                double zScore = (this.objectives[i]-means[i])/stds[i];
//                System.out.println("Zscore: " + zScore);
                double norm = sigmoid(zScore);
//                System.out.println("norm: " + norm);
                this.normalisedObjectives[i] = norm;

                //update the biasWeight, add by mengxu 2023.06.15
//                System.out.println("means: " + means[i]);
//                System.out.println("biasWeight: " + biasWeight[i]);
//                biasWeight[i] = stds[i]/(Arrays.stream(stds).sum()); //todo: need double check
//                biasWeight[i] = stds[length-1-i]/(Arrays.stream(stds).sum()); //todo: need double check
            }

//        }
        }

    public double getNormalisedObjectives(int i){
            return this.normalisedObjectives[i];
        }

    public double sigmoid(double s)
        {
            return 1/(1+Math.exp(-s));
        }


    public boolean paretoDominates(NSGA2MultiObjectiveFitnessNormalisation other)
        {
            boolean abeatsb = false;

            if (normalisedObjectives.length != other.normalisedObjectives.length)
                throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

            for (int x = 0; x < normalisedObjectives.length; x++)
            {
                if (maximize[x] != other.maximize[x])  // uh oh
                    throw new RuntimeException(
                            "Attempt made to compare two multiobjective fitnesses; but for objective #" + x +
                                    ", one expects higher values to be better and the other expectes lower values to be better.");

                if (maximize[x]) //fzhang 2018.11.2  maximize[0] and maximize[1] are false. It means we are looking at minimising.
                {
                    if (normalisedObjectives[x] > other.normalisedObjectives[x])
                        abeatsb = true;
                    else if (normalisedObjectives[x] < other.normalisedObjectives[x])
                        return false;
                }
                else
                {
                    if (normalisedObjectives[x] < other.normalisedObjectives[x]) //objective[0] and objective[1]: [2514.5953504074932, 498.03951619189513]
                        abeatsb = true;
                    else if (normalisedObjectives[x] > other.normalisedObjectives[x])
                        return false;
                }
            }

            return abeatsb;
        }
    }
