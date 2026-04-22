package mengxu.algorithm.multiobjective.MOEAD;

import ec.*;
import ec.multiobjective.nsga2.NSGA2Breeder;
import ec.multiobjective.nsga2.NSGA2MultiObjectiveFitness;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;
import ec.util.SortComparator;

import java.util.ArrayList;

public class MOEADEvaluator extends SimpleEvaluator{

    /**
     * The original population size is stored here so NSGA2 knows how large to
     * create the archive (it's the size of the original population -- keep in mind
     * that NSGA2Breeder had made the population larger to include the children.
     */
//    public int originalPopSize[];
//
//    public void setup(final EvolutionState state, final Parameter base) {
//        super.setup(state, base);
//
//        Parameter p = new Parameter(Initializer.P_POP);
//        int subpopsLength = state.parameters.getInt(p.push(Population.P_SIZE), null, 1);
//        Parameter p_subpop;
//        originalPopSize = new int[subpopsLength];
//        for (int i = 0; i < subpopsLength; i++) {
//            p_subpop = p.push(Population.P_SUBPOP).push("" + i).push(Subpopulation.P_SUBPOPSIZE);
//            originalPopSize[i] = state.parameters.getInt(p_subpop, null, 1);
//        }
//    }
//
//    /**
//     * Evaluates the population, then builds the archive and reduces the population
//     * to just the archive.
//     */
//    public void evaluatePopulation(final EvolutionState state) {
//        super.evaluatePopulation(state);  //the same with the simpleEvalutor, during the first generation, evaluate N individuals; after that, evaluate 2N individuals
//        for (int x = 0; x < state.population.subpops.length; x++)
//            state.population.subpops[x].individuals = buildArchive(state, x); // trade the individuals in archive as the population
//    }
//
//    /**
//     * Build the auxiliary fitness data and reduce the subpopulation to just the
//     * archive, which is returned.
//     */
//    //achieve a archive has the same size of original population
//    public Individual[] buildArchive(EvolutionState state, int subpop) {
//        Individual[] dummy = new Individual[0]; //allocates an array which has 0 elements.
//        ArrayList ranks = assignFrontRanks(state.population.subpops[subpop]); //after this, get different several ranks
//        //each rank cosists of the corresponding individuals
//
//        ArrayList newSubpopulation = new ArrayList(); //a new one, size = 0
//        int size = ranks.size();
//        for (int i = 0; i < size; i++) { //do for each rank separately
//            Individual[] rank = (Individual[]) ((ArrayList) (ranks.get(i))).toArray(dummy);
//            assignSparsity(rank); //assign sparity value for each individual in this rank
//            if (rank.length + newSubpopulation.size() >= originalPopSize[subpop]) {
//                // first sort the rank by sparsity---from the small one to large one
//                ec.util.QuickSort.qsort(rank, new SortComparator() {
//                    public boolean lt(Object a, Object b) { //Returns true if a < b, else false
//                        Individual i1 = (Individual) a;
//                        Individual i2 = (Individual) b;
//                        return (((NSGA2MultiObjectiveFitness) i1.fitness).sparsity > ((NSGA2MultiObjectiveFitness) i2.fitness).sparsity);
//                    }
//
//                    public boolean gt(Object a, Object b) { //Returns true if a > b, else false
//                        Individual i1 = (Individual) a;
//                        Individual i2 = (Individual) b;
//                        return (((NSGA2MultiObjectiveFitness) i1.fitness).sparsity < ((NSGA2MultiObjectiveFitness) i2.fitness).sparsity);
//                    }
//                });
//
//                // then put the m sparsest individuals in the new population
//                int m = originalPopSize[subpop] - newSubpopulation.size(); //how many positions left for new individuals
//                for (int j = 0; j < m; j++)
//                    newSubpopulation.add(rank[j]); //add some individuals based on the sparisity
//
//                // and bail
//                break;
//            } else {
//                // dump in everyone
//                for (int j = 0; j < rank.length; j++) //add the while rank directly
//                    newSubpopulation.add(rank[j]);
//            }
//        }
//
//        Individual[] archive = (Individual[]) (newSubpopulation.toArray(dummy));
//
//        // maybe force reevaluation
//        NSGA2Breeder breeder = (NSGA2Breeder) (state.breeder);
//        if (breeder.reevaluateElites[subpop])
//            for (int i = 0; i < archive.length; i++)
//                archive[i].evaluated = false;
//
//        return archive;
//    }

//    original
    public void evaluatePopulation(final EvolutionState state)
    {
        super.evaluatePopulation(state); //the same as normal evaluation

//        ((MOEADInitializer)state.initializer).updateIdealPoint(state);//original
//        ((MOEADInitializer)state.initializer).updateNadirPoint(state);//original

//        ((MOEADInitializer)state.initializer).updateIdealPointForEachGeneration(state);//modified by mengxu 2022.08.22
//        ((MOEADInitializer)state.initializer).updateNadirPointForNewGeneration(state);//modified by mengxu 2022.08.22

        //print ideal points and nadir points
        System.out.println("ideal points: [" + ((MOEADInitializer)state.initializer).idealPoint[0] + ", " + ((MOEADInitializer)state.initializer).idealPoint[1] + "]");

        System.out.println("nadir points: [" + ((MOEADInitializer)state.initializer).maxObjectives[0] + ", " + ((MOEADInitializer)state.initializer).maxObjectives[1] + "]");

//        //update idealPoint
//        ((MOEADInitializer)state.initializer).updateIdealPoint(state);
//        ((MOEADInitializer)state.initializer).updateNadirPoint(state);

        //force revaluate
//        Individual[] inds = state.population.subpops[0].individuals;
//        for(Individual ind: inds){
//            ind.evaluated = false;
//        }

//        System.out.println("evaluatePopulation");
    }

}
