package mengxu.archive;

import ec.EvolutionState;
import ec.Individual;
import ec.Initializer;
import ec.Population;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import yimei.jss.surrogate.Surrogate;

import java.util.Arrays;
import java.util.List;

public class ArchiveEvaluator extends SimpleEvaluator {

    public static void evaluateArchive(final EvolutionState state, List<GPTree> archiveTree0, List<GPTree> archiveTree1) {
        Individual[] currentIndividuals = state.population.subpops[0].individuals;
//        PopulationUtils.sort(currentIndividuals);

//        Individual[] archiveIndividual = currentIndividuals.clone();
        Individual[] archiveIndividual = new Individual[archiveTree0.size() * archiveTree1.size()];

//            GPIndividual individual = new GPIndividual();
//            individual.trees = new GPTree[2];
//            GPIndividual individual = (GPIndividual) archiveIndividual[i];
        int index=0;
        for(int t0=0; t0<archiveTree0.size(); t0++){
            for(int t1=0; t1<archiveTree1.size(); t1++){
                GPIndividual individual = (GPIndividual) currentIndividuals[index].clone();
                individual.trees[0] = (GPTree)archiveTree0.get(t0).clone();
                individual.trees[1] = (GPTree)archiveTree1.get(t1).clone();
                individual.trees[0].owner = individual;
                individual.trees[1].owner = individual;
                individual.evaluated = false;
                archiveIndividual[index] = individual;
                index++;
            }
        }

        //save top all individuals in archiveIndividual
        int popSize = state.population.subpops[0].individuals.length;
        for(int i=0; i<archiveIndividual.length; i++){
            state.population.subpops[0].individuals[popSize-i-1]=(Individual)archiveIndividual[i].clone();
//            System.out.println("ind " + i + " fitness: " + archiveIndividual[i].fitness.fitness());
        }

//        long start, finish, duration;
//        start = System.currentTimeMillis();
//        ((HalfShopMultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)state.evaluator.p_problem).getEvaluationModel()).evaluate(state, archiveIndividual);
//        finish = System.currentTimeMillis();
//        duration = finish - start;
//        System.out.println("Duration = " + duration + " ms.");
//        //save top N individuals
//        PopulationUtils.sort(archiveIndividual);
//        int N = 100;
//        int popSize = state.population.subpops[0].individuals.length;
//        for(int i=0; i<N; i++){
//            state.population.subpops[0].individuals[popSize-i-1]=(Individual)archiveIndividual[i].clone();
////            System.out.println("ind " + i + " fitness: " + archiveIndividual[i].fitness.fitness());
//        }
    }
}
