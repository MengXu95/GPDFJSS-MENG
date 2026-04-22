package mengxu.algorithm.multiobjective.ParetoSetLearning.withArchive;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.QuickSort;
import ec.util.SortComparator;
import yimei.jss.gp.GPTreeComparator;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement by mengxu 2022.08.08
 * Used as a unbound archive to store all feasible individuals from all generations
 * todo: need to check if the unbound will make the list has too many individuals and make Jova stop run
 */

public class ArchiveforPSL {
    List<List<Individual>> archive; //based on generation

    List<List<double[]>> PC;

    public ArchiveforPSL(){
        this.archive = new ArrayList<>();
        this.PC = new ArrayList<>();
    }

    public void addIndividualToArchive(EvolutionState state, Individual individual){
        archive.get(state.generation).add(individual);
    }

//    public void printArchive(EvolutionState state){
//        System.out.println("\nPareto front in archive");
//        MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness;
//        for(int j=0; j< typicalFitness.getNumObjectives(); j++) {
//            System.out.print("Objective " + j + ": [");
//            for(int i=0; i<this.archive.size(); i++){
//                double fit = ((MultiObjectiveFitness)(((Individual)(this.archive.get(i))).fitness)).getObjective(j);
//                if(i == this.archive.size() - 1){
//                    System.out.println(fit + "]");
//                }
//                else{
//                    System.out.print(fit + ", ");
//                }
//            }
//        }
//    }

//    public void updateArchive(EvolutionState state){
//        //check again the Pareto front, if not belong to the Pareto front, then just delete
//        RuleOptimizationProblem problem = (RuleOptimizationProblem)state.evaluator.p_problem;
//        if(problem.getEvaluationModel().isRotatable()){ //if rotate seed
//            //just delete the individuals from the former generations and add the current one if the number of individuals in the archive has exceeded the popsize
//            while(this.archive.size()>state.population.subpops[0].individuals.length){
//                this.archive.remove(0);
//            }
//        }
//        else{ //if not rotate seed
//            MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness;
//            // build front
//            ArrayList front = typicalFitness.partitionIntoParetoFront(this.archive.toArray(new Individual[0]), null, null);
//
//            this.archive.clear();
//
//            // sort by objective[0]
//            Object[] sortedFront = front.toArray();
//            QuickSort.qsort(sortedFront, new SortComparator()
//            {
//                public boolean lt(Object a, Object b)
//                {
//                    return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) <
//                            (((MultiObjectiveFitness) ((Individual) b).fitness)).getObjective(0));
//                }
//
//                public boolean gt(Object a, Object b)
//                {
//                    return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) >
//                            ((MultiObjectiveFitness) (((Individual) b).fitness)).getObjective(0));
//                }
//            });
//
//            for (int i = 0; i < sortedFront.length; i++){
//                this.archive.add((Individual)sortedFront[i]);
//            }
//            //just delete the individuals from the former generations and add the current one if the number of individuals in the archive has exceeded the popsize
//            //todo: also need to check actually, but should be by different ways
////            while(this.archive.size()>state.population.subpops[0].individuals.length){
////                this.archive.remove(0);
////            }
//        }
//    }

    public List<List<Individual>> getArchive() {
        return archive;
    }

    public void addPC(EvolutionState state, double[] pc){
        PC.get(state.generation).add(pc);
    }

    public List<List<double[]>> getPC() {
        return PC;
    }

//    public void addIndividualAndPCToArchive(EvolutionState state, Individual individual, double[] pc, boolean checkPhenotypeDuplicated){
//        if(checkPhenotypeDuplicated){
//            int isPhenotypeDuplicated = isPhenotypeDuplicatedWithIndividualInArchive(pc);
//            if(isPhenotypeDuplicated == -1){
//                archive.get(state.generation).add(individual);
//                PC.get(state.generation).add(pc);
//            }
//            else{//if is duplicated, replace the original with this new one, as this is from the generation far away the initialisation
//                archive.get(state.generation).set(isPhenotypeDuplicated, individual);
//                PC.get(state.generation).set(isPhenotypeDuplicated, pc);
//            }
//        }
//        else{
//            archive.get(state.generation).add(individual);
//            PC.get(state.generation).add(pc);
//        }
//    }


//    public int isPhenotypeDuplicatedWithIndividualInArchive(double[] pc){
//        int isPhenotypeDuplicated = -1; //-1 represents no duplicated individuals
//        for(int i=0; i<PC.size(); i++){
//            double[] pcInArchive = PC.get(i);
//            boolean duplicated = true;
//            for(int j=0; j< pcInArchive.length; j++){
//                if(pcInArchive[j] != pc[j]){
//                    duplicated = false;
//                    j = pcInArchive.length;
//                }
//            }
//            if(duplicated){
//                isPhenotypeDuplicated = i;
//                return isPhenotypeDuplicated;
//            }
//        }
//        return isPhenotypeDuplicated;
//    }
//
//
//    public List<Individual> getTopNumIndividualsBasedOnUnSeenInstances(int Num, List<Objective> objectives, SchedulingSet schedulingSet){
//        //todo: need to implement 2022.08.08
//        //todo: this process takes a long time, so we might use surrogate model to evaluate and select individuals
//
//        for(int i=0; i<archive.size(); i++){
//            Individual indi = archive.get(i);
//            GPRule sequencingRule = new GPRule(RuleType.SEQUENCING, ((GPIndividual) indi).trees[0]);
//            GPRule routingRule = new GPRule(RuleType.ROUTING, ((GPIndividual) indi).trees[1]);
//
//            List rules = new ArrayList();
//            List fitnesses = new ArrayList();
//
//            //rules.add(rule);
//            //modified by fzhang  to save two rules for evaluating from one individual
//            rules.add(sequencingRule);
//            rules.add(routingRule);
//
//            fitnesses.add(indi.fitness);
//
//            evaluate(fitnesses, rules, null, objectives, schedulingSet);
//
//            indi.evaluated = true;
//        }
//
//        MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness)(archive.get(0).fitness);
//        Individual[] archiveList = archive.toArray(new Individual[0]);
//        // build front
//        ArrayList front = typicalFitness.partitionIntoParetoFront(archiveList, null, null);
//
//        // sort by objective[0]
//        Object[] sortedFront = front.toArray();
//        QuickSort.qsort(sortedFront, new SortComparator()
//        {
//            public boolean lt(Object a, Object b)
//            {
//                return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) <
//                        (((MultiObjectiveFitness) ((Individual) b).fitness)).getObjective(0));
//            }
//
//            public boolean gt(Object a, Object b)
//            {
//                return (((MultiObjectiveFitness) (((Individual) a).fitness)).getObjective(0) >
//                        ((MultiObjectiveFitness) (((Individual) b).fitness)).getObjective(0));
//            }
//        });
//
//        // print out front to statistics log
//        int count = 0;
//        List<Individual> subSetIndividual = new ArrayList<>();
////        for(int j=0; j< typicalFitness.getNumObjectives(); j++){
//            for (int i = 0; i < sortedFront.length; i++) {
//                subSetIndividual.add((Individual)sortedFront[i]);
//                count++;
//                if(count == Num){
//                    return subSetIndividual;
//                }
//            }
////        }
//
//        //todo: need to Pareto front selection strategy here to select top N individuals
//        return subSetIndividual;
//
//    }
//
////    public List<Individual> getTopNumIndividualsBasedOnSubsetSelection(){
////        //todo: need to implement 2022.08.08
////    }
//
//
//    public void evaluate(List<Fitness> currentFitnesses,
//                         List<AbstractRule> rules,
//                         EvolutionState state,
//                         List<Objective> objectives,
//                         SchedulingSet schedulingSet) {
//        //expecting 2 rules here - one routing rule and one sequencing rule
//        if (rules.size() != 2) {
//            System.out.println("Rule evaluation failed!");
//            System.out.println("Expecting 2 rules, only 1 found.");
//            return;
//        }
//
//        AbstractRule sequencingRule = rules.get(0); // for each arraylist in list, they have two elements, the first one is sequencing rule and the second one is routing rule
//        AbstractRule routingRule = rules.get(1);
//
//        int numObjective = objectives.size();
//
//        double[] fitnesses = new double[numObjective];
//
//        List<Simulation> simulations = schedulingSet.getSimulations();
//        int col = 0;
//
//        //System.out.println(simulations.size()); // 1 repeat
//        //System.out.println(schedulingSet.getReplications().get(0)); //1 repeat
//
//        for (int j = 0; j < simulations.size(); j++) {
//            Simulation simulation = simulations.get(j);
//
//            //========================change here======================================
//            simulation.setSequencingRule(sequencingRule); //indicate different individuals
//            simulation.setRoutingRule(routingRule);
//            //System.out.println(simulation);
//            simulation.run();
//            for (int i = 0; i < objectives.size(); i++) {
//                double ObjValue = simulation.objectiveValue(objectives.get(i)); // this line: the value of makespan
//                fitnesses[i] += ObjValue;
//            }
//            col++;
//
//            //schedulingSet.getReplications().get(j) = 1, only calculate once, skip this part here
//            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
//                simulation.rerun();
//
//                for (int i = 0; i < objectives.size(); i++) {
//                    //2018.10.23  cancel normalized process
//                    double ObjValue = simulation.objectiveValue(objectives.get(i));
//                    fitnesses[i] += ObjValue;
//                }
//
//                col++;
//            }
//
//            simulation.reset();
//        }
//
//        //modified by fzhang 18.04.2018  in order to check this loop works or not after add filter part: does not work
//        // if(countBadrun>0) {
//        //System.out.println(state.generation);
//        //System.out.println("The number of badrun grasped in model: "+ countBadrun);
//        // }
//
//        for (int i = 0; i < fitnesses.length; i++) {
//            fitnesses[i] /= col;
//        }
//
//        //System.out.println(currentFitnesses.size()); //1
//        for (Fitness fitness: currentFitnesses) {
//            MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
//            f.setObjectives(state, fitnesses);
//        }
//
//    }
}
