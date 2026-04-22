package mengxu.algorithm.multiobjective.MOEADm2mcoverage;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Code;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.LineNumberReader;

public class MOEADm2mMultiObjectiveFitnesscoverage extends MultiObjectiveFitness{
    public static final String NSGA2_RANK_PREAMBLE = "Rank: ";
    public static final String NSGA2_SPARSITY_PREAMBLE = "Sparsity: ";

    public String[] getAuxilliaryFitnessNames() { return new String[] { "Rank", "Sparsity" }; }
    public double[] getAuxilliaryFitnessValues() { return new double[] { rank, sparsity }; }

    /** Pareto front rank measure (lower ranks are better) */
    public int rank;

    /** Sparsity along front rank measure (higher sparsity is better) */
    public double sparsity;

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
        MOEADm2mMultiObjectiveFitnesscoverage other = (MOEADm2mMultiObjectiveFitnesscoverage) _fitness;
        return (rank == ((MOEADm2mMultiObjectiveFitnesscoverage) _fitness).rank) &&
                (sparsity == other.sparsity);
    }

    /**
     * We specify the tournament selection criteria, Rank (lower
     * values are better) and Sparsity (higher values are better)
     */
    public boolean betterThan(Fitness _fitness)
    {
        MOEADm2mMultiObjectiveFitnesscoverage other = (MOEADm2mMultiObjectiveFitnesscoverage) _fitness;
        // Rank should always be minimized.
        if (rank < ((MOEADm2mMultiObjectiveFitnesscoverage) _fitness).rank)
            return true;
        else if (rank > ((MOEADm2mMultiObjectiveFitnesscoverage) _fitness).rank)
            return false;

        // otherwise try sparsity
        return (sparsity > other.sparsity);
    }
}
