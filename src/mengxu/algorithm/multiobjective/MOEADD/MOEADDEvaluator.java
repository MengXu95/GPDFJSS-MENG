package mengxu.algorithm.multiobjective.MOEADD;

import ec.EvolutionState;
import ec.Individual;
import ec.Population;
import ec.Subpopulation;
import ec.multiobjective.MultiObjectiveFitness;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
//import mengxu.algorithm.multiobjective.MOEAD.MOEADInitializer;
//import mengxu.algorithm.multiobjective.MOEAD.MOEADMultiObjectiveFitness;

import java.util.*;

public class MOEADDEvaluator extends SimpleEvaluator{
    public void evaluatePopulation(final EvolutionState state)
    {
        super.evaluatePopulation(state); //the same as normal evaluation

        MOEADDInitializer init =  ((MOEADDInitializer)state.initializer);

        //update idealPoint
        init.updateIdealPoint(state);
        //update idealPoint
        init.updateNadirPoint(state);
//        System.out.println("evaluatePopulation");

        Population oldPop = (Population) state.population;
        Individual[] oldInds = oldPop.subpops[0].individuals;

        // initialize the distance
        for (int i = 0; i < init.popSize; i++) {
            double distance = calculateDistance2(state, oldInds[i], init.weights[i], init.idealPoint, init.nadirPoint);
            init.subregionDist[i][i] = distance;
        }

        ArrayList ranking = assignFrontRanks(state.population.subpops[0]);

        // build a mapping of Individual -> index in inds array
        HashMap m = new HashMap();
        for(int i = 0; i < state.population.subpops[0].individuals.length; i++)
            m.put(state.population.subpops[0].individuals[i], Integer.valueOf(i));
        for (int curRank = 0; curRank < ranking.size(); curRank++) {
            ArrayList front = (ArrayList)(ranking.get(curRank));
//            List<S> front = ranking.getSubFront(curRank);
            for(int ind = 0; ind < front.size(); ind++) {
                int position = ((Integer)(m.get(front.get(ind)))).intValue();
                init.rankIdx[curRank][position] = 1; //todo: need to check
            }
        }

    }



    public double calculateDistance2(EvolutionState state, Individual ind, double[] weight,
                                     double[] z_, double[] nz_) {
        MOEADDInitializer init =  ((MOEADDInitializer)state.initializer);

        // normalize the weight vector (line segment)
        double nd = norm_vector(state, weight);
        for (int i = 0; i < init.numObjectives; i++) {
            weight[i] = weight[i] / nd;
        }

        double[] realA = new double[init.numObjectives];
        double[] realB = new double[init.numObjectives];

        // difference between current point and reference point
        for (int i = 0; i < init.numObjectives; i++) {
            realA[i] = (((MOEADDMultiObjectiveFitness)ind.fitness).getObjectives()[i] - z_[i]);
        }

        // distance along the line segment
        double d1 = Math.abs(innerproduct(realA, weight));

        // distance to the line segment
        for (int i = 0; i < init.numObjectives; i++) {
            realB[i] = (((MOEADDMultiObjectiveFitness)ind.fitness).getObjectives()[i] - (z_[i] + d1 * weight[i]));
        }

        double distance = norm_vector(state, realB);

        return distance;
    }

    /**
     * Calculate the dot product of two vectors
     */
    public double innerproduct(double[] vec1, double[] vec2) {
        double sum = 0;

        for (int i = 0; i < vec1.length; i++) {
            sum += vec1[i] * vec2[i];
        }

        return sum;
    }

    /**
     * Calculate the norm of the vector
     */
    public double norm_vector(EvolutionState state, double[] z) {
        double sum = 0;
        MOEADDInitializer init =  ((MOEADDInitializer)state.initializer);

        for (int i = 0; i < init.numObjectives; i++) {
            sum += z[i] * z[i];
        }

        return Math.sqrt(sum);
    }

    protected ArrayList computeRanking(Subpopulation subpop) {
        ArrayList ranking = assignFrontRanks(subpop);
//        Ranking<S> ranking = new FastNonDominatedSortRanking<S>();
//        ranking.compute(inds);
        return ranking;
    }

    /**
     * Divides inds into ranks and assigns each individual's rank to be the rank it
     * was placed into. Each front is an ArrayList.
     */
    public ArrayList assignFrontRanks(Subpopulation subpop) {
        Individual[] inds = subpop.individuals; //inds includes all individuals
        ArrayList frontsByRank = MultiObjectiveFitness.partitionIntoRanks(inds);

        int numRanks = frontsByRank.size();
        for (int rank = 0; rank < numRanks; rank++) {
            ArrayList front = (ArrayList) (frontsByRank.get(rank));
            int numInds = front.size();
            for (int ind = 0; ind < numInds; ind++)
                ((NSGA2MultiObjectiveFitness) (((Individual) (front.get(ind))).fitness)).rank = rank;
        }
        return frontsByRank;
    }

}
