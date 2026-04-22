/*
  Copyright 2010 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package mengxu.algorithm.multiobjective.indicatorBasedMO;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Code;
import ec.util.Parameter;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.LineNumberReader;

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

public class IBMOMultiObjectiveFitness extends MultiObjectiveFitness
    {
    public static final String NSGA2_RANK_PREAMBLE = "Rank: ";
    public static final String NSGA2_HVCONTRIBUTION_PREAMBLE = "Sparsity: ";

    public String[] getAuxilliaryFitnessNames() { return new String[] { "Rank", "HVcontribution" }; }
    public double[] getAuxilliaryFitnessValues() { return new double[] { rank, HVcontribution }; }
        
    /** Pareto front rank measure (lower ranks are better) */
    public int rank;

    /** Sparsity along front rank measure (higher sparsity is better) */
//    public double sparsity;

    public double HVcontribution;

    public double DominatedBy;

    public double DuplicatedBy; // add by mengxu 2024.7.10

    public EvolutionState currentState;

    public double[] normalisedObjectives; // values range from 0 (worst) to 1 INCLUSIVE added by mengxu 2023.04.20

    public void setup(EvolutionState state, Parameter base)
    {
        super.setup(state, base); // unnecessary really

        this.currentState = state; //added by mengxu 2023.04.18
    }

    public String fitnessToString()
        {
        return super.fitnessToString() + "\n" + NSGA2_RANK_PREAMBLE + Code.encode(rank) + "\n" + NSGA2_HVCONTRIBUTION_PREAMBLE + Code.encode(HVcontribution);
        }

    public String fitnessToStringForHumans()
        {
        return super.fitnessToStringForHumans() + "\n" + NSGA2_RANK_PREAMBLE + rank + "\n" + NSGA2_HVCONTRIBUTION_PREAMBLE + HVcontribution;
        }

    public void readFitness(final EvolutionState state, final LineNumberReader reader) throws IOException
        {
        super.readFitness(state, reader);
        rank = Code.readIntegerWithPreamble(NSGA2_RANK_PREAMBLE, state, reader);
        HVcontribution = Code.readDoubleWithPreamble(NSGA2_HVCONTRIBUTION_PREAMBLE, state, reader);
        }

    public void writeFitness(final EvolutionState state, final DataOutput dataOutput) throws IOException
        {
        super.writeFitness(state, dataOutput);
        dataOutput.writeInt(rank);
        dataOutput.writeDouble(HVcontribution);
        writeTrials(state, dataOutput);
        }

    public void readFitness(final EvolutionState state, final DataInput dataInput) throws IOException
        {
        super.readFitness(state, dataInput);
        rank = dataInput.readInt();
        HVcontribution = dataInput.readDouble();
        readTrials(state, dataInput);
        }

    public boolean equivalentTo(Fitness _fitness)
        {
        IBMOMultiObjectiveFitness other = (IBMOMultiObjectiveFitness) _fitness;
        return (rank == ((IBMOMultiObjectiveFitness) _fitness).rank) &&
            (HVcontribution == other.HVcontribution);
        }

    /**
     * We specify the tournament selection criteria, Rank (lower
     * values are better) and Sparsity (higher values are better)
     */
    public boolean betterThan(Fitness _fitness)
        {
        IBMOMultiObjectiveFitness other = (IBMOMultiObjectiveFitness) _fitness;
        // Rank should always be minimized.
//        if (rank < ((IBMOMultiObjectiveFitness) _fitness).rank)
//            return true;
//        else if (rank > ((IBMOMultiObjectiveFitness) _fitness).rank)
//            return false;
                
        // otherwise try HVcontribution
        if(rank == 0 && ((IBMOMultiObjectiveFitness) _fitness).rank == 0){
            if(HVcontribution > other.HVcontribution){
                return true;
            }
            else if(HVcontribution < other.HVcontribution){
                return false;
            }
            else{
                if(DuplicatedBy < other.DuplicatedBy){
                    return true;
                }
                else{
                    return false;
                }
//                return false;
//                return this.currentState.random[0].nextBoolean(0.5);
            }
        }
        else{
            if(DominatedBy < other.DominatedBy){
                return true;
            }
            else if(DominatedBy > other.DominatedBy){
                return false;
            }
            else{
                if(DuplicatedBy < other.DuplicatedBy){
                    return true;
                }
                else{
                    return false;
                }
//                return false;
//                return this.currentState.random[0].nextBoolean(0.5);
            }
        }
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

    }
