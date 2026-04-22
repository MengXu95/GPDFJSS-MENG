package mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNSurrogate;

import ec.Fitness;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import yimei.jss.jobshop.Objective;

public class OneInstanceMultiCaseMultiObjectiveFitnessDPS extends OneInstanceMultiCaseMultiObjectiveFitness {
    /** parameter for size of objectives */

    public double fitness() {
//        return fitnessMeanNoBestNoWorst();
//        return fitnessMedian();
        //original
        Objective objective = currentObjectives.get(0);
        switch (objective) {
            case MEAN_FLOWTIME:
            case MEAN_WEIGHTED_FLOWTIME:
            case MEAN_TARDINESS:
            case MEAN_WEIGHTED_TARDINESS:
                return fitnessMean();
            case MAX_FLOWTIME:
            case MAX_WEIGHTED_FLOWTIME:
            case MAX_TARDINESS:
            case MAX_WEIGHTED_TARDINESS:
                return fitnessMax();
        }
        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");
        return Double.MAX_VALUE;
    }

    @Override
    public boolean betterThan(Fitness fitness)
    {
        return compareInstanceV2mean((OneInstanceMultiCaseMultiObjectiveFitnessDPS)fitness);
        //original
//        //just one objective
//        Objective objective = currentObjectives.get(0);
//        switch (objective) {
//            case MEAN_FLOWTIME:
//            case MEAN_WEIGHTED_FLOWTIME:
//            case MEAN_TARDINESS:
//            case MEAN_WEIGHTED_TARDINESS:
//                return compareInstanceV2mean((OneInstanceMultiCaseMultiObjectiveFitnessDPS)fitness);
//            case MAX_FLOWTIME:
//            case MAX_WEIGHTED_FLOWTIME:
//            case MAX_TARDINESS:
//            case MAX_WEIGHTED_TARDINESS:
//                return compareInstanceV2max((OneInstanceMultiCaseMultiObjectiveFitnessDPS)fitness);
//        }
//        System.out.println("Error, MultiInstanceMultiObjectiveFitness.betterThan()");
//
//        return compareInstanceV2mean((OneInstanceMultiCaseMultiObjectiveFitness)fitness);
    }


}
