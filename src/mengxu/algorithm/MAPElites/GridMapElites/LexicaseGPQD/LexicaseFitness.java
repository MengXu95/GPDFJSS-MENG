package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import ec.Fitness;
import java.util.Collections;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import yimei.jss.jobshop.Objective;

public final class LexicaseFitness extends OneInstanceMultiCaseMultiObjectiveFitness {
    private double rawObjective = Double.POSITIVE_INFINITY;
    private int evaluatedGeneration = -1;
    private long trainingSeed;

    public void setEvaluation(double raw, double[] cases, Objective objective, int generation, long seed) {
        if (cases == null || cases.length < 2 || objective == null || Double.isNaN(raw) || raw < 0) {
            throw new IllegalArgumentException("Invalid one-instance multi-case evaluation");
        }
        boolean valid = Double.isFinite(raw) && raw < Double.MAX_VALUE;
        multiInstanceMultiObjectiveFitness = new double[cases.length][1];
        multiInstanceMultiObjectiveFitnessRank = new int[cases.length][1];
        for (int i = 0; i < cases.length; i++) {
            if (valid && (!Double.isFinite(cases[i]) || cases[i] < 0 || cases[i] >= Double.MAX_VALUE)) {
                throw new IllegalArgumentException("Invalid case objective at " + i);
            }
            multiInstanceMultiObjectiveFitness[i][0] = valid ? cases[i] : Double.POSITIVE_INFINITY;
        }
        rawObjective = valid ? raw : Double.POSITIVE_INFINITY;
        objectives = new double[]{rawObjective};
        currentObjectives = Collections.singletonList(objective);
        evaluatedGeneration = generation;
        trainingSeed = seed;
    }

    public boolean isValidEvaluation() { return Double.isFinite(rawObjective); }
    public int evaluatedGeneration() { return evaluatedGeneration; }
    public long trainingSeed() { return trainingSeed; }
    @Override public double fitness() { return rawObjective; }
    @Override public double[] getObjectives() { return new double[]{rawObjective}; }
    @Override public String fitnessToStringForHumans() { return "Fitness: [" + rawObjective + "]"; }

    @Override public boolean betterThan(Fitness other) {
        if (!(other instanceof LexicaseFitness)) {
            throw new IllegalArgumentException("Incompatible fitness in Lexicase comparison");
        }
        return rawObjective < ((LexicaseFitness) other).rawObjective;
    }

    @Override public boolean equivalentTo(Fitness other) {
        return other instanceof LexicaseFitness
                && Double.compare(rawObjective, ((LexicaseFitness) other).rawObjective) == 0;
    }

    @Override public Object clone() {
        LexicaseFitness copy = (LexicaseFitness) super.clone();
        if (multiInstanceMultiObjectiveFitness != null) {
            copy.multiInstanceMultiObjectiveFitness = new double[multiInstanceMultiObjectiveFitness.length][];
            copy.multiInstanceMultiObjectiveFitnessRank = new int[multiInstanceMultiObjectiveFitness.length][];
            for (int i = 0; i < multiInstanceMultiObjectiveFitness.length; i++) {
                copy.multiInstanceMultiObjectiveFitness[i] = multiInstanceMultiObjectiveFitness[i].clone();
                copy.multiInstanceMultiObjectiveFitnessRank[i] = multiInstanceMultiObjectiveFitnessRank[i].clone();
            }
        }
        return copy;
    }
}
