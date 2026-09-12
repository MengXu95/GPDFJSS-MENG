package mengxu.algorithm.EvoSpeakV1;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;

public class WeightedFitness extends MultiObjectiveFitness {
    private double[] weights;
    private double[] baselines;

    @Override
    public void setup(EvolutionState state, Parameter base) {
        super.setup(state, base);
        if (objectives.length < 1 || objectives.length > 2) {
            throw new IllegalArgumentException("EvoSpeakV1 requires one or two objectives.");
        }
        weights = new double[objectives.length];
        baselines = new double[objectives.length];
        double total = 0.0;
        for (int index = 0; index < weights.length; index++) {
            double weight = weights.length == 1 ? 1.0 : state.parameters.getDoubleWithDefault(
                    new Parameter("evospeak.weight." + index), null, 0.5);
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("Objective weights must be finite and nonnegative.");
            }
            weights[index] = weight;
            baselines[index] = 1.0;
            total += weight;
        }
        if (!Double.isFinite(total) || total <= 0.0) {
            throw new IllegalArgumentException("At least one objective weight must be positive.");
        }
        for (int index = 0; index < weights.length; index++) {
            weights[index] /= total;
        }
    }

    public void setBaselines(double[] values) {
        if (values.length != objectives.length) {
            throw new IllegalArgumentException("Baseline and objective dimensions differ.");
        }
        for (double value : values) {
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException("Normalization baselines must be finite and positive.");
            }
        }
        baselines = values.clone();
    }

    public double[] getWeights() {
        return weights.clone();
    }

    @Override
    public double fitness() {
        double score = 0.0;
        for (int index = 0; index < objectives.length; index++) {
            if (weights[index] == 0.0) {
                continue;
            }
            if (!Double.isFinite(objectives[index]) || objectives[index] >= Double.MAX_VALUE) {
                return Double.POSITIVE_INFINITY;
            }
            score += weights[index] * (objectives[index] / baselines[index]);
        }
        return Double.isFinite(score) ? score : Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean betterThan(Fitness other) {
        return fitness() < other.fitness();
    }

    @Override
    public boolean equivalentTo(Fitness other) {
        return Double.compare(fitness(), other.fitness()) == 0;
    }

    @Override
    public Object clone() {
        WeightedFitness copy = (WeightedFitness) super.clone();
        copy.weights = weights == null ? null : weights.clone();
        copy.baselines = baselines == null ? null : baselines.clone();
        return copy;
    }
}