package mengxu.algorithm.ensemble;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.diversitymeasure.GenotypeDiversity;
import mengxu.algorithm.multicaseEnsemble.ensembleContribution.OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.state.SystemState;

import java.util.*;

/**
 * Author: MengXu
 */

public class EnsembleRule {

    private List<Individual> ensemble;
    private List<Integer> elementOriginalIndex;

    private List<List<Integer>> elementSelectedBasedOnCaseOrder = null;
    private List<GPRule> ensembleRoutingRule;
    private List<GPRule> ensembleSequencingRule;

    private int ensembleSize;

    private double[] ensembleContribution;

    private double[] ensembleSpread;

    private MultiObjectiveFitness fitness;

    public boolean evaluated;

    public EnsembleRule(List<Individual> ensemble, List<Integer> elementOriginalIndex, int ensembleSize){
        this.ensemble = new ArrayList<>();
        this.elementOriginalIndex = new ArrayList<>();
        this.ensembleSize = ensembleSize;
        ensembleRoutingRule = new ArrayList<>();
        ensembleSequencingRule = new ArrayList<>();
        fitness = new MultiObjectiveFitness();
        ensembleContribution = new double[ensemble.size()];
        this.evaluated = false;

        for(int i=0; i<ensemble.size(); i++){
            Individual indi = (GPIndividual)ensemble.get(i).clone();
            this.ensemble.add(indi);
            this.elementOriginalIndex.add(elementOriginalIndex.get(i));
            GPRule sequencingRule = new GPRule(RuleType.SEQUENCING, ((GPIndividual) indi).trees[0]);
            GPRule routingRule = new GPRule(RuleType.ROUTING, ((GPIndividual) indi).trees[1]);

            ensembleSequencingRule.add(sequencingRule);
            ensembleRoutingRule.add(routingRule);

            ensembleContribution[i] = 0;
        }

        //original, might be suitable for test hide by mengxu 2023.02.24
//        for(int i=0; i<ensemble.size(); i++){
//            String sequencingRuleString = ((GPIndividual)ensemble.get(i)).trees[0].toString();
//            System.out.println(sequencingRuleString);//check
//            GPRule sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, sequencingRuleString);
//            ensembleSequencingRule.add(sequencingRule);
//
//            String routingRuleString = ((GPIndividual)ensemble.get(i)).trees[1].toString();
//            System.out.println(routingRuleString);//check
//            GPRule routingRule = GPRule.readFromLispExpression(RuleType.ROUTING, routingRuleString);
//            ensembleRoutingRule.add(routingRule);
//
//            ensembleContribution[i] = 0;
//        }
    }

    public EnsembleRule(List<Individual> ensemble, int ensembleSize){
        this.ensemble = ensemble;
        this.elementOriginalIndex = null;
        this.ensembleSize = ensembleSize;
        ensembleRoutingRule = new ArrayList<>();
        ensembleSequencingRule = new ArrayList<>();
        fitness = new MultiObjectiveFitness();
        ensembleContribution = new double[ensemble.size()];
        this.evaluated = false;

        for(int i=0; i<ensemble.size(); i++){
            Individual indi = (GPIndividual)ensemble.get(i);
            GPRule sequencingRule = new GPRule(RuleType.SEQUENCING, ((GPIndividual) indi).trees[0]);
            GPRule routingRule = new GPRule(RuleType.ROUTING, ((GPIndividual) indi).trees[1]);

            ensembleSequencingRule.add(sequencingRule);
            ensembleRoutingRule.add(routingRule);

            ensembleContribution[i] = 0;
        }

        //original, might be suitable for test hide by mengxu 2023.02.24
//        for(int i=0; i<ensemble.size(); i++){
//            String sequencingRuleString = ((GPIndividual)ensemble.get(i)).trees[0].toString();
//            System.out.println(sequencingRuleString);//check
//            GPRule sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, sequencingRuleString);
//            ensembleSequencingRule.add(sequencingRule);
//
//            String routingRuleString = ((GPIndividual)ensemble.get(i)).trees[1].toString();
//            System.out.println(routingRuleString);//check
//            GPRule routingRule = GPRule.readFromLispExpression(RuleType.ROUTING, routingRuleString);
//            ensembleRoutingRule.add(routingRule);
//
//            ensembleContribution[i] = 0;
//        }
    }

    public EnsembleRule(List<GPRule> ensembleSequencingRule, List<GPRule> ensembleRoutingRule){
        this.ensemble = null;
        this.ensembleSequencingRule = ensembleSequencingRule;
        this.ensembleRoutingRule = ensembleRoutingRule;
        fitness = new MultiObjectiveFitness();
        ensembleContribution = new double[ensembleSequencingRule.size()];
        this.evaluated = false;
    }

    public void ElementFitnessClear(){
        for(int i=0; i<this.ensemble.size(); i++){
            Individual individual = this.ensemble.get(i);
            individual.evaluated = false;
            if(this.elementOriginalIndex != null){
                this.elementOriginalIndex.set(i, -1);
            }
            ((OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)individual.fitness).fitnessClear();
        }
    }

    public List<List<Integer>> getElementSelectedBasedOnCaseOrder() {
        return elementSelectedBasedOnCaseOrder;
    }

    public void setElementSelectedBasedOnCaseOrder(List<List<Integer>> elementSelectedBasedOnCaseOrder) {
        this.elementSelectedBasedOnCaseOrder = elementSelectedBasedOnCaseOrder;
    }

    public int getEnsembleSize() {
        return ensembleSize;
    }

    public List<GPRule> getEnsembleRoutingRule() {
        return ensembleRoutingRule;
    }

    public List<GPRule> getEnsembleSequencingRule() {
        return ensembleSequencingRule;
    }

    public List<Individual> getEnsemble() {
        return ensemble;
    }

    public List<Individual> getEnsembleClone() {
        List<Individual> ensembleClone = new ArrayList<>();
        for(int i=0; i<ensemble.size(); i++){
            Individual individualClone = (Individual) ensemble.get(i).clone();
            ensembleClone.add(individualClone);
        }
        return ensembleClone;
    }

    public MultiObjectiveFitness getFitness() {
        return fitness;
    }

    public void printEnsembleContribution(final EvolutionState state,final int log){
        state.output.print("Ensemble contribution: [", log);
        for(int i=0; i<this.ensembleContribution.length-1; i++){
            state.output.print(this.ensembleContribution[i] + ", ", log);
        }
        state.output.println(this.ensembleContribution[this.ensemble.size()-1] + "]", log);
    }

    public int getUniqueMembers(){
        int num = this.ensembleSequencingRule.size();
        Individual[] individuals = new Individual[num];
        for(int i=0; i<num; i++){
            GPTree sequencing = this.ensembleSequencingRule.get(i).getGPTree();
            GPTree routing = this.ensembleRoutingRule.get(i).getGPTree();
            GPIndividual individual = new GPIndividual();
            individual.trees = new GPTree[2];
            individual.trees[0] = sequencing;
            individual.trees[1] = routing;
            individuals[i] = individual;
        }
        GenotypeDiversity genoD = new GenotypeDiversity();
        int genoDvalue = (int)genoD.genotypeDiversity(individuals);
        return genoDvalue;
    }

    public double[] getEnsembleContribution(){
        return ensembleContribution;
    }

    public double[] getEnsembleSpread(){
        return ensembleSpread;
    }

    public List<Integer> getElementOriginalIndex() {
        return elementOriginalIndex;
    }


    public void calcFitness(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet,
                            List<Objective> objectives) {

        AbstractRule routingRule;
        AbstractRule sequencingRule;

        double[] fitnesses = new double[objectives.size()];

        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;

        int numCase = this.ensembleRoutingRule.size();

        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);
            simulation.setEnsembleRule(this);
            simulation.useMultiCaseEnsemble = true; // add by mengxu 2022.04.26
            simulation.rerunWithEnsemble(numCase);

            for (int i = 0; i < objectives.size(); i++) {
                double ObjValue = simulation.objectiveValue(objectives.get(i));
                fitnesses[i] += ObjValue;
            }

            col++;

            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                simulation.rerunWithEnsemble(numCase);

                for (int i = 0; i < objectives.size(); i++) {
                    double ObjValue = simulation.objectiveValue(objectives.get(i));
                    fitnesses[i] += ObjValue;
                }

                col++;
            }

            simulation.reset();
        }

        for (int i = 0; i < fitnesses.length; i++) {
            fitnesses[i] /= col;
        }
        MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
        f.setObjectives(state, fitnesses);
        this.fitness = (MultiObjectiveFitness)f.clone();
        this.evaluated = true;
    }

    public void calcFitnessByVotingEnsemble(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet,
                                                                          List<Objective> objectives) {

        AbstractRule routingRule;
        AbstractRule sequencingRule;


        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;
//        int numCase = 25; //todo: need to double check

        double[] fitnesses = new double[objectives.size()];

        //System.out.println("The simulation size is "+simulations.size()); //1
        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);
            simulation.setEnsembleRule(this);
            simulation.setSequencingRule(null);
            simulation.setRoutingRule(null);
            simulation.useVotingEnsemble = true;
//            simulation.setRoutingRule(routingRule);
            // }
            simulation.runWithVotingEnsemble(this.ensembleContribution);
            for(int k=0; k<this.ensembleContribution.length; k++){
                this.ensembleContribution[k] = this.ensembleContribution[k]/simulation.totalDecisionNumber;
            }


            for (int i = 0; i < objectives.size(); i++) {
                // System.out.println("Makespan:
                // "+simulation.objectiveValue(objectives.get(i)));
                // System.out.println("Benchmark makespan:
                // "+schedulingSet.getObjectiveLowerBound(i, col));

                //fzhang 2018.10.23  cancel normalizing objective
//				double normObjValue = simulation.objectiveValue(objectives.get(i))
//						/ schedulingSet.getObjectiveLowerBound(i, col);
                double ObjValue = simulation.objectiveValue(objectives.get(i));

                //modified by fzhang, 26.4.2018  check in test process, whether there is ba
                //fzhang 2018.10.23  cancel normalizing objective
//				fitnesses[i] += normObjValue;

                fitnesses[i] += ObjValue;

            }


            col++;

            //System.out.println("The value of replication is "+schedulingSet.getReplications()); //50
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                double[] ensembleContribution_k = new double[this.ensembleSequencingRule.size()];
                simulation.rerunWithVotingEnsemble(ensembleContribution_k);
                for(int m=0; m<ensembleContribution_k.length; m++){
                    ensembleContribution_k[m] = ensembleContribution_k[m]/simulation.totalDecisionNumber;
                    this.ensembleContribution[m] += ensembleContribution_k[m];
                }

                for (int i = 0; i < objectives.size(); i++) {
                    double ObjValue = simulation.objectiveValue(objectives.get(i));
                    fitnesses[i] += ObjValue;
                }

                col++;
            }

            simulation.reset();
        }

        for (int i = 0; i < fitnesses.length; i++) {
            fitnesses[i] /= col;
        }
        for(int m=0; m<this.ensembleContribution.length; m++){
            this.ensembleContribution[m] = this.ensembleContribution[m]/col;
        }
        MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
        f.setObjectives(state, fitnesses);
        this.fitness = (MultiObjectiveFitness)f.clone();
        this.evaluated = true;
    }

    public void calcFitnessByVotingEnsembleWithPreference(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet,
                                            List<Objective> objectives, int preference_index) {

        AbstractRule routingRule;
        AbstractRule sequencingRule;


        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;
//        int numCase = 25; //todo: need to double check

        double[] fitnesses = new double[objectives.size()];

        //System.out.println("The simulation size is "+simulations.size()); //1
        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);
            simulation.setEnsembleRule(this);
            simulation.setSequencingRule(null);
            simulation.setRoutingRule(null);
            simulation.useVotingEnsemble = true;
//            simulation.setRoutingRule(routingRule);
            // }

            if(state instanceof GPRuleEvolutionStatePSL){
                simulation.state = state;
                simulation.state.generation = preference_index;
            }

            simulation.runWithVotingEnsemble(this.ensembleContribution);
            for(int k=0; k<this.ensembleContribution.length; k++){
                this.ensembleContribution[k] = this.ensembleContribution[k]/simulation.totalDecisionNumber;
            }


            for (int i = 0; i < objectives.size(); i++) {
                // System.out.println("Makespan:
                // "+simulation.objectiveValue(objectives.get(i)));
                // System.out.println("Benchmark makespan:
                // "+schedulingSet.getObjectiveLowerBound(i, col));

                //fzhang 2018.10.23  cancel normalizing objective
//				double normObjValue = simulation.objectiveValue(objectives.get(i))
//						/ schedulingSet.getObjectiveLowerBound(i, col);
                double ObjValue = simulation.objectiveValue(objectives.get(i));

                //modified by fzhang, 26.4.2018  check in test process, whether there is ba
                //fzhang 2018.10.23  cancel normalizing objective
//				fitnesses[i] += normObjValue;

                fitnesses[i] += ObjValue;

            }


            col++;

            //System.out.println("The value of replication is "+schedulingSet.getReplications()); //50
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                double[] ensembleContribution_k = new double[this.ensembleSequencingRule.size()];
                simulation.rerunWithVotingEnsemble(ensembleContribution_k);
                for(int m=0; m<ensembleContribution_k.length; m++){
                    ensembleContribution_k[m] = ensembleContribution_k[m]/simulation.totalDecisionNumber;
                    this.ensembleContribution[m] += ensembleContribution_k[m];
                }

                for (int i = 0; i < objectives.size(); i++) {
                    double ObjValue = simulation.objectiveValue(objectives.get(i));
                    fitnesses[i] += ObjValue;
                }

                col++;
            }

            simulation.reset();
        }

        for (int i = 0; i < fitnesses.length; i++) {
            fitnesses[i] /= col;
        }
        for(int m=0; m<this.ensembleContribution.length; m++){
            this.ensembleContribution[m] = this.ensembleContribution[m]/col;
        }
        MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
        f.setObjectives(state, fitnesses);
        this.fitness = (MultiObjectiveFitness)f.clone();
        this.evaluated = true;
    }

    public void calcFitnessAndSpreadByVotingEnsemble(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet,
                                            List<Objective> objectives) {
        this.ensembleSpread = new double[this.ensembleSequencingRule.size()];
        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;

        double[] fitnesses = new double[objectives.size()];

        //System.out.println("The simulation size is "+simulations.size()); //1
        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);
            simulation.setEnsembleRule(this);
            simulation.setSequencingRule(null);
            simulation.setRoutingRule(null);
            simulation.useVotingEnsemble = true;

//            simulation.runWithVotingEnsemble(this.ensembleContribution);
            simulation.runWithVotingEnsembleRecordSpread(this.ensembleContribution, this.ensembleSpread);
            for(int k=0; k<this.ensembleContribution.length; k++){
                this.ensembleContribution[k] = this.ensembleContribution[k]/simulation.totalDecisionNumber;
                this.ensembleSpread[k] = this.ensembleSpread[k]/simulation.totalDecisionNumber;
            }

            for (int i = 0; i < objectives.size(); i++) {

                double ObjValue = simulation.objectiveValue(objectives.get(i));
                fitnesses[i] += ObjValue;

            }

            col++;

            //System.out.println("The value of replication is "+schedulingSet.getReplications()); //50
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                double[] ensembleContribution_k = new double[this.ensembleSequencingRule.size()];
                double[] ensembleSpread_k = new double[this.ensembleSequencingRule.size()];
                simulation.rerunWithVotingEnsembleRecordSpread(ensembleContribution_k, ensembleSpread_k);
                for(int m=0; m<ensembleContribution_k.length; m++){
                    ensembleContribution_k[m] = ensembleContribution_k[m]/simulation.totalDecisionNumber;
                    this.ensembleContribution[m] += ensembleContribution_k[m];

                    ensembleSpread_k[m] = ensembleSpread_k[m]/simulation.totalDecisionNumber;
                    this.ensembleSpread[m] += ensembleSpread_k[m];
                }

                for (int i = 0; i < objectives.size(); i++) {
                    double ObjValue = simulation.objectiveValue(objectives.get(i));
                    fitnesses[i] += ObjValue;
                }

                col++;
            }

            simulation.reset();
        }

        for (int i = 0; i < fitnesses.length; i++) {
            fitnesses[i] /= col;
        }
        for(int m=0; m<this.ensembleContribution.length; m++){
            this.ensembleContribution[m] = this.ensembleContribution[m]/col;
            this.ensembleSpread[m] = this.ensembleSpread[m]/col;
        }
        MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
        f.setObjectives(state, fitnesses);
        this.fitness = (MultiObjectiveFitness)f.clone();
        this.evaluated = true;
    }

    public void calcFitnessByVotingEnsembleAndMultiCaseFitnesssEvaluation(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet,
                                            List<Objective> objectives) {

        AbstractRule routingRule;
        AbstractRule sequencingRule;


        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;
        int numCase = 25; //todo: need to double check

        double[][] fitnesses = new double[numCase][objectives.size()];//modified by mengxu

        //System.out.println("The simulation size is "+simulations.size()); //1
        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);
            simulation.setEnsembleRule(this);
            simulation.setSequencingRule(null);
            simulation.setRoutingRule(null);
            simulation.useVotingEnsemble = true;
//            simulation.setRoutingRule(routingRule);
            // }
            simulation.runWithVotingEnsemble(this.ensembleContribution);
            for(int k=0; k<this.ensembleContribution.length; k++){
                this.ensembleContribution[k] = this.ensembleContribution[k]/simulation.totalDecisionNumber;
            }


            for (int i = 0; i < objectives.size(); i++) {
                // System.out.println("Makespan:
                // "+simulation.objectiveValue(objectives.get(i)));
                // System.out.println("Benchmark makespan:
                // "+schedulingSet.getObjectiveLowerBound(i, col));

                //fzhang 2018.10.23  cancel normalizing objective
//				double normObjValue = simulation.objectiveValue(objectives.get(i))
//						/ schedulingSet.getObjectiveLowerBound(i, col);
                double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan

                for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
                    if (w.numOpsInQueue() > 100) {
                        Arrays.fill(multiCaseObjValue,Double.MAX_VALUE);
                        break;
                    }
                }

                for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
                    fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
                }

                //modified by fzhang, 26.4.2018  check in test process, whether there is ba
                //fzhang 2018.10.23  cancel normalizing objective
//				fitnesses[i] += normObjValue;

            }


            col++;

            //System.out.println("The value of replication is "+schedulingSet.getReplications()); //50
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                double[] ensembleContribution_k = new double[this.ensembleSequencingRule.size()];
                simulation.rerunWithVotingEnsemble(ensembleContribution_k);
                for(int m=0; m<ensembleContribution_k.length; m++){
                    ensembleContribution_k[m] = ensembleContribution_k[m]/simulation.totalDecisionNumber;
                    this.ensembleContribution[m] += ensembleContribution_k[m];
                }

                for (int i = 0; i < objectives.size(); i++) {
                    double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan

                    for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
                        if (w.numOpsInQueue() > 100) {
                            Arrays.fill(multiCaseObjValue,Double.MAX_VALUE);
                            break;
                        }
                    }

                    for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
                        fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
                    }
                }

                col++;
            }

            simulation.reset();
        }

        for (int j = 0; j < simulations.size(); j++) {
            for (int indexCase = 0; indexCase < fitnesses.length; indexCase++) {
                for (int i = 0; i < fitnesses[j].length; i++) {
                    fitnesses[indexCase][i] /= schedulingSet.getReplications().get(j);
//                    System.out.print(fitnesses[indexCase][i] + ", ");
                }
            }
        }

        for (int i = 0; i < this.ensembleContribution.length; i++) {
            this.ensembleContribution[i] = this.ensembleContribution[i]/col;
        }

        //System.out.println(currentFitnesses.size()); //1
        OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution f = (OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution) fitness;
        f.setMultiInstanceFitness(state, fitnesses);
        f.setCurrentObjective(objectives);
        this.fitness = f;
        this.evaluated = true;
//        this.fitness = (OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution)f.clone();

    }

    public void calcFitnessByMeanRankEnsembleAndMultiCaseFitnesssEvaluation(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet,
                                                                          List<Objective> objectives) {

        AbstractRule routingRule;
        AbstractRule sequencingRule;

        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;
        int numCase = 25; //todo: need to double check

        double[][] fitnesses = new double[numCase][objectives.size()];//modified by mengxu

        //System.out.println("The simulation size is "+simulations.size()); //1
        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);
            simulation.setEnsembleRule(this);
            simulation.setSequencingRule(null);
            simulation.setRoutingRule(null);
            simulation.useMeanRankEnsemble = true;

            simulation.runWithMeanRankEnsemble(this.ensembleContribution);
            for(int k=0; k<this.ensembleContribution.length; k++){
                this.ensembleContribution[k] = this.ensembleContribution[k]/simulation.totalDecisionNumber;
            }

            for (int i = 0; i < objectives.size(); i++) {
                double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan

                for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
                    if (w.numOpsInQueue() > 100) {
                        Arrays.fill(multiCaseObjValue,Double.MAX_VALUE);
                        break;
                    }
                }

                for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
                    fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
                }

            }

            col++;

            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                double[] ensembleContribution_k = new double[this.ensembleSequencingRule.size()];
                simulation.rerunWithMeanRankEnsemble(ensembleContribution_k);
                for(int m=0; m<ensembleContribution_k.length; m++){
                    ensembleContribution_k[m] = ensembleContribution_k[m]/simulation.totalDecisionNumber;
                    this.ensembleContribution[m] += ensembleContribution_k[m];
                }

                for (int i = 0; i < objectives.size(); i++) {
                    double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan

                    for (WorkCenter w: simulation.getSystemState().getWorkCenters()) {
                        if (w.numOpsInQueue() > 100) {
                            Arrays.fill(multiCaseObjValue,Double.MAX_VALUE);
                            break;
                        }
                    }

                    for(int indexCase=0; indexCase<multiCaseObjValue.length; indexCase++){
                        fitnesses[indexCase][i] += multiCaseObjValue[indexCase];
                    }
                }

                col++;
            }

            simulation.reset();
        }

        for (int j = 0; j < simulations.size(); j++) {
            for (int indexCase = 0; indexCase < fitnesses.length; indexCase++) {
                for (int i = 0; i < fitnesses[j].length; i++) {
                    fitnesses[indexCase][i] /= schedulingSet.getReplications().get(j);
                }
            }
        }

        for (int i = 0; i < this.ensembleContribution.length; i++) {
            this.ensembleContribution[i] = this.ensembleContribution[i]/col;
        }

        //System.out.println(currentFitnesses.size()); //1
        OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution f = (OneInstanceMultiCaseMultiObjectiveFitnessEnsembleContribution) fitness;
        f.setMultiInstanceFitness(state, fitnesses);
        f.setCurrentObjective(objectives);
        this.fitness = f;
        this.evaluated = true;
    }

    public OperationOption nextOperationOption(RoutingDecisionSituation routingDecisionSituation) {

        List<OperationOption> queue = routingDecisionSituation.getQueue();
        SystemState systemState = routingDecisionSituation.getSystemState();
        //================original=================
        //==================start==================
        OperationOption bestOperationOption = queue.get(0);
        bestOperationOption
                .setPriority(priority(bestOperationOption, bestOperationOption.getWorkCenter(), systemState));
        // loop all the options, save the best one as "selected" one
        for (int i = 1; i < queue.size(); i++) {
            OperationOption operationOption = queue.get(i);
            operationOption.setPriority(priority(operationOption, operationOption.getWorkCenter(), systemState));

            if (operationOption.priorTo(bestOperationOption)) {
                bestOperationOption = operationOption;
            }
        }
        return bestOperationOption;// this links which machine will be chosen.
    }

    public double priority(OperationOption op, WorkCenter workCenter, SystemState systemState){
        return calcPriorityV1(op, workCenter, systemState);
    }

    public double calcPriorityV1(OperationOption op, WorkCenter workCenter, SystemState systemState){
        double priorityValue = 0;
        for (int i=0; i<ensembleRoutingRule.size();i++){
            GPRule routingRule = ensembleRoutingRule.get(i);
            priorityValue += routingRule.priority(op, workCenter, systemState);
        }
        return priorityValue;
    }

    public OperationOption nextOperationOptionVoting(RoutingDecisionSituation routingDecisionSituation, double[] ensembleContribution) {

//        System.out.println("use ensemble to select machine");
        List<OperationOption> queue = routingDecisionSituation.getQueue();
        SystemState systemState = routingDecisionSituation.getSystemState();
        int[] eachElementDecision = new int[ensembleRoutingRule.size()];
        //================original=================
        //==================start==================
        Map<Integer, Integer> OpOptionAndVoting = new HashMap<>();
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            GPRule routingRule = ensembleRoutingRule.get(ensembleIndex);
            OperationOption bestOperationOption = queue.get(0);
            bestOperationOption
                    .setPriority(routingRule.priority(bestOperationOption, bestOperationOption.getWorkCenter(), systemState));
//            System.out.println("\nbestOperationOption.setPriority: " + bestOperationOption.getPriority());
            // loop all the options, save the best one as "selected" one
            int bestId = 0;
            for (int i = 1; i < queue.size(); i++) {
                OperationOption operationOption = queue.get(i);
                operationOption.setPriority(routingRule.priority(operationOption, operationOption.getWorkCenter(), systemState));
//                System.out.println("operationOption.setPriority: " + operationOption.getPriority());
                if (operationOption.priorTo(bestOperationOption)) {
                    bestOperationOption = operationOption;
                    bestId = i;
                }
            }
            eachElementDecision[ensembleIndex] = bestId;
            if(OpOptionAndVoting.containsKey(bestId)){
                int oldVoting = OpOptionAndVoting.get(bestId);
                OpOptionAndVoting.replace(bestId, oldVoting+1);
            }
            else{
                OpOptionAndVoting.put(bestId,1);
            }
        }

        OperationOption bestOperationOptionVoting = null;//todo: need to modified
        int maxVoting = 0;
        int finalBestId = 0;
        for(int opOp: OpOptionAndVoting.keySet()){
            if(OpOptionAndVoting.get(opOp)>maxVoting){
                maxVoting = OpOptionAndVoting.get(opOp);
                finalBestId = opOp;
                bestOperationOptionVoting = queue.get(opOp);//todo:need to add the same voting selection strategy
            }
        }

        //add by mengxu 2023.02.24
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            if(eachElementDecision[ensembleIndex] == finalBestId){
                ensembleContribution[ensembleIndex] += 1;
            }
        }

        return bestOperationOptionVoting;// this links which machine will be chosen.
    }

    public OperationOption nextOperationOptionVotingRecordSpread(RoutingDecisionSituation routingDecisionSituation,
                                                                 double[] ensembleContribution, double[] ensembleSpread) {

//        System.out.println("use ensemble to select machine");
        List<OperationOption> queue = routingDecisionSituation.getQueue();
        SystemState systemState = routingDecisionSituation.getSystemState();
        int[] eachElementDecision = new int[ensembleRoutingRule.size()];
        //================original=================
        //==================start==================
        Map<Integer, Integer> OpOptionAndVoting = new HashMap<>();
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            GPRule routingRule = ensembleRoutingRule.get(ensembleIndex);
            OperationOption bestOperationOption = queue.get(0);
            bestOperationOption
                    .setPriority(routingRule.priority(bestOperationOption, bestOperationOption.getWorkCenter(), systemState));
//            System.out.println("\nbestOperationOption.setPriority: " + bestOperationOption.getPriority());
            // loop all the options, save the best one as "selected" one
            int bestId = 0;
            for (int i = 1; i < queue.size(); i++) {
                OperationOption operationOption = queue.get(i);
                operationOption.setPriority(routingRule.priority(operationOption, operationOption.getWorkCenter(), systemState));
//                System.out.println("operationOption.setPriority: " + operationOption.getPriority());
                if (operationOption.priorTo(bestOperationOption)) {
                    bestOperationOption = operationOption;
                    bestId = i;
                }
            }
            eachElementDecision[ensembleIndex] = bestId;
            if(OpOptionAndVoting.containsKey(bestId)){
                int oldVoting = OpOptionAndVoting.get(bestId);
                OpOptionAndVoting.replace(bestId, oldVoting+1);
            }
            else{
                OpOptionAndVoting.put(bestId,1);
            }
        }

        OperationOption bestOperationOptionVoting = null;//todo: need to modified
        int maxVoting = 0;
        int finalBestId = 0;
        for(int opOp: OpOptionAndVoting.keySet()){
            if(OpOptionAndVoting.get(opOp)>maxVoting){
                maxVoting = OpOptionAndVoting.get(opOp);
                finalBestId = opOp;
                bestOperationOptionVoting = queue.get(opOp);//todo:need to add the same voting selection strategy
            }
        }

        //calculate the rank of candidates by the ensemble
        double[] candidateRank = new double[queue.size()];
        for(int i=0; i< OpOptionAndVoting.size(); i++) {
            finalBestId = 0;
            for (int opOp : OpOptionAndVoting.keySet()) {
                if (OpOptionAndVoting.get(opOp) > maxVoting) {
                    maxVoting = OpOptionAndVoting.get(opOp);
                    finalBestId = opOp;
                }
            }
            candidateRank[finalBestId] = i+1;
            OpOptionAndVoting.replace(finalBestId, -1);
        }

        //calculate the spread
        for(int i=0; i<eachElementDecision.length; i++) {
            int candidateId = eachElementDecision[i];
            double decisionRank = candidateRank[candidateId];
            double normalisedDecisionRank = (double)(decisionRank/(double)queue.size());
            ensembleSpread[i] += normalisedDecisionRank;
        }

        //add by mengxu 2023.02.24
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            if(eachElementDecision[ensembleIndex] == finalBestId){
                ensembleContribution[ensembleIndex] += 1;
            }
        }

        return bestOperationOptionVoting;// this links which machine will be chosen.
    }

    public OperationOption nextOperationOptionMeanRank(RoutingDecisionSituation routingDecisionSituation, double[] ensembleContribution) {

//        System.out.println("use ensemble to select machine");
        List<OperationOption> queue = routingDecisionSituation.getQueue();
        SystemState systemState = routingDecisionSituation.getSystemState();
        int[] eachElementDecision = new int[ensembleRoutingRule.size()];
        int[][] eachElementRank = new int[ensembleRoutingRule.size()][queue.size()];
        //================original=================
        //==================start==================
        Map<Integer, Integer> OpOptionAndVoting = new HashMap<>();
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            GPRule routingRule = ensembleRoutingRule.get(ensembleIndex);
            OperationOption bestOperationOption = queue.get(0);
            bestOperationOption
                    .setPriority(routingRule.priority(bestOperationOption, bestOperationOption.getWorkCenter(), systemState));
//            System.out.println("\nbestOperationOption.setPriority: " + bestOperationOption.getPriority());
            // loop all the options, save the best one as "selected" one
            int bestId = 0;
            for (int i = 1; i < queue.size(); i++) {
                OperationOption operationOption = queue.get(i);
                operationOption.setPriority(routingRule.priority(operationOption, operationOption.getWorkCenter(), systemState));
//                System.out.println("operationOption.setPriority: " + operationOption.getPriority());
                if (operationOption.priorTo(bestOperationOption)) {
                    bestOperationOption = operationOption;
                    bestId = i;
                }
            }
            eachElementDecision[ensembleIndex] = bestId;
            if(OpOptionAndVoting.containsKey(bestId)){
                int oldVoting = OpOptionAndVoting.get(bestId);
                OpOptionAndVoting.replace(bestId, oldVoting+1);
            }
            else{
                OpOptionAndVoting.put(bestId,1);
            }

            int rank = 0;
            for (int i = 0; i < queue.size(); i++) {
                for (int j = 0; j < queue.size(); j++) {
                    if (i != j && queue.get(i).priorTo(queue.get(j))) {
                        rank++; //a higher priority gives a higher rank
                    }
                }
                eachElementRank[ensembleIndex][i] = rank;
                rank = 0;
            }
        }

        int[] allElementRankSum = new int[queue.size()];
        for(int ensembleIndex=0; ensembleIndex<ensembleSequencingRule.size();ensembleIndex++) {
            for (int i = 0; i < queue.size(); i++) {
                if(ensembleIndex == 0){
                    allElementRankSum[i] = eachElementRank[ensembleIndex][i];
                }
                else{
                    allElementRankSum[i] += eachElementRank[ensembleIndex][i];
                }
            }
        }

        OperationOption bestOperationOptionMeanRank = null;//todo: need to modified
        int bestMeanRank = -1;
        int finalBestId = -1;
        for (int i = 0; i < queue.size(); i++) {
            if(allElementRankSum[i] > bestMeanRank){
                finalBestId = i;
                bestMeanRank = allElementRankSum[i];
                bestOperationOptionMeanRank = queue.get(i);
            }
        }

        //add by mengxu 2023.02.24
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            if(eachElementDecision[ensembleIndex] == finalBestId){
                ensembleContribution[ensembleIndex] += 1;
            }
        }

        return bestOperationOptionMeanRank;// this links which machine will be chosen.
    }

//    public OperationOption priorOperationVoting(SequencingDecisionSituation sequencingDecisionSituation) {
//
////        System.out.println("use ensemble to select operation");
//        List<OperationOption> queue = sequencingDecisionSituation.getQueue();
//        WorkCenter workCenter = sequencingDecisionSituation.getWorkCenter();
//        SystemState systemState = sequencingDecisionSituation.getSystemState();
//
//        //fzhang 2018.10.23  original one
//        //============================start==============================
//        Map<Integer, Integer> OpOptionAndVoting = new HashMap<>();
//        for(int ensembleIndex=0; ensembleIndex<ensembleSequencingRule.size();ensembleIndex++) {
//            GPRule sequencingRule = ensembleSequencingRule.get(ensembleIndex);
//            OperationOption priorOp = queue.get(0);
//            priorOp.setPriority(sequencingRule.priority(priorOp, workCenter, systemState));
//
//            int bestId = 0;
//            for (int i = 1; i < queue.size(); i++) {
//                OperationOption op = queue.get(i);
//                op.setPriority(sequencingRule.priority(op, workCenter, systemState));
//
//                if (op.priorTo(priorOp)){
//                    priorOp = op;
//                    bestId = i;
//                }
//
//            }
//
//            if(OpOptionAndVoting.containsKey(bestId)){
//                int oldVoting = OpOptionAndVoting.get(bestId);
//                OpOptionAndVoting.replace(bestId, oldVoting+1);
//            }
//            else{
//                OpOptionAndVoting.put(bestId,1);
//            }
//        }
//
//        OperationOption bestOperationOptionVoting = null;//todo: need to modified
//        int maxVoting = 0;
//
//        for(int opOp: OpOptionAndVoting.keySet()){
//            if(OpOptionAndVoting.get(opOp)>maxVoting){
//                maxVoting = OpOptionAndVoting.get(opOp);
//                bestOperationOptionVoting = queue.get(opOp);//todo:need to add the same voting selection strategy
//            }
//        }
//
//        return bestOperationOptionVoting;// this links which operation will be chosen.
//    }

    public OperationOption priorOperationVotingRecordSpread(SequencingDecisionSituation sequencingDecisionSituation,
                                                            double[] ensembleContribution, double[] ensembleSpread) {

//        System.out.println("use ensemble to select operation");
        List<OperationOption> queue = sequencingDecisionSituation.getQueue();
        WorkCenter workCenter = sequencingDecisionSituation.getWorkCenter();
        SystemState systemState = sequencingDecisionSituation.getSystemState();
        int[] eachElementDecision = new int[ensembleRoutingRule.size()];

        //fzhang 2018.10.23  original one
        //============================start==============================
        Map<Integer, Integer> OpOptionAndVoting = new HashMap<>();
        for(int ensembleIndex=0; ensembleIndex<ensembleSequencingRule.size();ensembleIndex++) {
            GPRule sequencingRule = ensembleSequencingRule.get(ensembleIndex);
            OperationOption priorOp = queue.get(0);
            priorOp.setPriority(sequencingRule.priority(priorOp, workCenter, systemState));

            int bestId = 0;
            for (int i = 1; i < queue.size(); i++) {
                OperationOption op = queue.get(i);
                op.setPriority(sequencingRule.priority(op, workCenter, systemState));

                if (op.priorTo(priorOp)){
                    priorOp = op;
                    bestId = i;
                }

            }
            eachElementDecision[ensembleIndex] = bestId;
            if(OpOptionAndVoting.containsKey(bestId)){
                int oldVoting = OpOptionAndVoting.get(bestId);
                OpOptionAndVoting.replace(bestId, oldVoting+1);
            }
            else{
                OpOptionAndVoting.put(bestId,1);
            }
        }

        OperationOption bestOperationOptionVoting = null;//todo: need to modified
        int maxVoting = 0;
        int finalBestId = 0;
        for(int opOp: OpOptionAndVoting.keySet()){
            if(OpOptionAndVoting.get(opOp)>maxVoting){
                maxVoting = OpOptionAndVoting.get(opOp);
                finalBestId = opOp;
                bestOperationOptionVoting = queue.get(opOp);//todo:need to add the same voting selection strategy
            }
        }

        //calculate the rank of candidates by the ensemble
        double[] candidateRank = new double[queue.size()];
        for(int i=0; i< OpOptionAndVoting.size(); i++) {
            finalBestId = 0;
            for (int opOp : OpOptionAndVoting.keySet()) {
                if (OpOptionAndVoting.get(opOp) > maxVoting) {
                    maxVoting = OpOptionAndVoting.get(opOp);
                    finalBestId = opOp;
                }
            }
            candidateRank[finalBestId] = i+1;
            OpOptionAndVoting.replace(finalBestId, -1);
        }

        //calculate the spread
        for(int i=0; i<eachElementDecision.length; i++) {
            int candidateId = eachElementDecision[i];
            double decisionRank = candidateRank[candidateId];
            double normalisedDecisionRank = (double)(decisionRank/(double)queue.size());
            ensembleSpread[i] += normalisedDecisionRank;
        }

        //add by mengxu 2023.02.24
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            if(eachElementDecision[ensembleIndex] == finalBestId){
                ensembleContribution[ensembleIndex] += 1;
            }
        }

        return bestOperationOptionVoting;// this links which operation will be chosen.
    }

    public OperationOption priorOperationVoting(SequencingDecisionSituation sequencingDecisionSituation, double[] ensembleContribution) {

//        System.out.println("use ensemble to select operation");
        List<OperationOption> queue = sequencingDecisionSituation.getQueue();
        WorkCenter workCenter = sequencingDecisionSituation.getWorkCenter();
        SystemState systemState = sequencingDecisionSituation.getSystemState();
        int[] eachElementDecision = new int[ensembleRoutingRule.size()];

        //fzhang 2018.10.23  original one
        //============================start==============================
        Map<Integer, Integer> OpOptionAndVoting = new HashMap<>();
        for(int ensembleIndex=0; ensembleIndex<ensembleSequencingRule.size();ensembleIndex++) {
            GPRule sequencingRule = ensembleSequencingRule.get(ensembleIndex);
            OperationOption priorOp = queue.get(0);
            priorOp.setPriority(sequencingRule.priority(priorOp, workCenter, systemState));

            int bestId = 0;
            for (int i = 1; i < queue.size(); i++) {
                OperationOption op = queue.get(i);
                op.setPriority(sequencingRule.priority(op, workCenter, systemState));

                if (op.priorTo(priorOp)){
                    priorOp = op;
                    bestId = i;
                }

            }
            eachElementDecision[ensembleIndex] = bestId;
            if(OpOptionAndVoting.containsKey(bestId)){
                int oldVoting = OpOptionAndVoting.get(bestId);
                OpOptionAndVoting.replace(bestId, oldVoting+1);
            }
            else{
                OpOptionAndVoting.put(bestId,1);
            }
        }

        OperationOption bestOperationOptionVoting = null;//todo: need to modified
        int maxVoting = 0;
        int finalBestId = 0;
        for(int opOp: OpOptionAndVoting.keySet()){
            if(OpOptionAndVoting.get(opOp)>maxVoting){
                maxVoting = OpOptionAndVoting.get(opOp);
                finalBestId = opOp;
                bestOperationOptionVoting = queue.get(opOp);//todo:need to add the same voting selection strategy
            }
        }

        //add by mengxu 2023.02.24
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            if(eachElementDecision[ensembleIndex] == finalBestId){
                ensembleContribution[ensembleIndex] += 1;
            }
        }

        return bestOperationOptionVoting;// this links which operation will be chosen.
    }



    public OperationOption priorOperationMeanRank(SequencingDecisionSituation sequencingDecisionSituation, double[] ensembleContribution) {

//        System.out.println("use ensemble to select operation");
        List<OperationOption> queue = sequencingDecisionSituation.getQueue();
        WorkCenter workCenter = sequencingDecisionSituation.getWorkCenter();
        SystemState systemState = sequencingDecisionSituation.getSystemState();
        int[] eachElementDecision = new int[ensembleRoutingRule.size()];
        int[][] eachElementRank = new int[ensembleRoutingRule.size()][queue.size()];

        //fzhang 2018.10.23  original one
        //============================start==============================
        Map<Integer, Integer> OpOptionAndVoting = new HashMap<>();
        for(int ensembleIndex=0; ensembleIndex<ensembleSequencingRule.size();ensembleIndex++) {
            GPRule sequencingRule = ensembleSequencingRule.get(ensembleIndex);
            OperationOption priorOp = queue.get(0);
            priorOp.setPriority(sequencingRule.priority(priorOp, workCenter, systemState));

            int bestId = 0;
            for (int i = 1; i < queue.size(); i++) {
                OperationOption op = queue.get(i);
                op.setPriority(sequencingRule.priority(op, workCenter, systemState));

                if (op.priorTo(priorOp)){
                    priorOp = op;
                    bestId = i;
                }

            }
            eachElementDecision[ensembleIndex] = bestId;
            if(OpOptionAndVoting.containsKey(bestId)){
                int oldVoting = OpOptionAndVoting.get(bestId);
                OpOptionAndVoting.replace(bestId, oldVoting+1);
            }
            else{
                OpOptionAndVoting.put(bestId,1);
            }

            int rank = 0;
            for (int i = 0; i < queue.size(); i++) {
                for (int j = 0; j < queue.size(); j++) {
                    if (i != j && queue.get(i).priorTo(queue.get(j))) {
                        rank++; //a higher priority gives a higher rank
                    }
                }
                eachElementRank[ensembleIndex][i] = rank;
                rank = 0;
            }
        }

        int[] allElementRankSum = new int[queue.size()];
        for(int ensembleIndex=0; ensembleIndex<ensembleSequencingRule.size();ensembleIndex++) {
            for (int i = 0; i < queue.size(); i++) {
                if(ensembleIndex == 0){
                    allElementRankSum[i] = eachElementRank[ensembleIndex][i];
                }
                else{
                    allElementRankSum[i] += eachElementRank[ensembleIndex][i];
                }
            }
        }

        OperationOption bestOperationOptionMeanRank = null;//todo: need to modified
        int bestMeanRank = -1;
        int finalBestId = -1;
        for (int i = 0; i < queue.size(); i++) {
            if(allElementRankSum[i] > bestMeanRank){
                finalBestId = i;
                bestMeanRank = allElementRankSum[i];
                bestOperationOptionMeanRank = queue.get(i);
            }
        }

        //add by mengxu 2023.02.24
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            if(eachElementDecision[ensembleIndex] == finalBestId){
                ensembleContribution[ensembleIndex] += 1;
            }
        }

        return bestOperationOptionMeanRank;// this links which operation will be chosen.
    }

    //added by mengxu 2023.03.07
    public void calcFitnessByLinearEnsemble(Fitness fitness, EvolutionState state, SchedulingSet schedulingSet,
                                            List<Objective> objectives) {

        AbstractRule routingRule;
        AbstractRule sequencingRule;

        double[] fitnesses = new double[objectives.size()];

        List<Simulation> simulations = schedulingSet.getSimulations();
        int col = 0;

        int numRule = this.ensembleRoutingRule.size();

        //System.out.println("The simulation size is "+simulations.size()); //1
        for (int j = 0; j < simulations.size(); j++) {
            Simulation simulation = simulations.get(j);
            simulation.setEnsembleRule(this);
            simulation.useLinearEnsemble = true;
            simulation.setSequencingRule(null);
            simulation.setRoutingRule(null);

            if(state instanceof GPRuleEvolutionStatePSL){
                simulation.state = state; //add by mengxu 2024.3.11
            }
//            simulation.setRoutingRule(routingRule);
            // }
            simulation.runWithLinearEnsemble(this.ensembleContribution);
            for(int k=0; k<this.ensembleContribution.length; k++){
                this.ensembleContribution[k] = this.ensembleContribution[k]/simulation.totalDecisionNumber;
            }


            for (int i = 0; i < objectives.size(); i++) {
                // System.out.println("Makespan:
                // "+simulation.objectiveValue(objectives.get(i)));
                // System.out.println("Benchmark makespan:
                // "+schedulingSet.getObjectiveLowerBound(i, col));

                //fzhang 2018.10.23  cancel normalizing objective
//				double normObjValue = simulation.objectiveValue(objectives.get(i))
//						/ schedulingSet.getObjectiveLowerBound(i, col);

                double ObjValue = simulation.objectiveValue(objectives.get(i));

                //modified by fzhang, 26.4.2018  check in test process, whether there is ba
                //fzhang 2018.10.23  cancel normalizing objective
//				fitnesses[i] += normObjValue;

                fitnesses[i] += ObjValue;
            }

            col++;

            //System.out.println("The value of replication is "+schedulingSet.getReplications()); //50
            for (int k = 1; k < schedulingSet.getReplications().get(j); k++) {
                double[] ensembleContribution_k = new double[this.ensembleSequencingRule.size()];
                if(state instanceof GPRuleEvolutionStatePSL){
                    simulation.state = state; //add by mengxu 2024.3.11
                }
                simulation.rerunWithLinearEnsemble(ensembleContribution_k);
                for(int m=0; m<ensembleContribution_k.length; m++){
                    ensembleContribution_k[m] = ensembleContribution_k[m]/simulation.totalDecisionNumber;
                    this.ensembleContribution[m] += ensembleContribution_k[m];
                }

                for (int i = 0; i < objectives.size(); i++) {
//					double normObjValue = simulation.objectiveValue(objectives.get(i))
//							/ schedulingSet.getObjectiveLowerBound(i, col);
//					fitnesses[i] += normObjValue;

                    //fzhang 2018.10.23  cancel normalizing objective
                    double ObjValue = simulation.objectiveValue(objectives.get(i));
                    fitnesses[i] += ObjValue;
                }

                col++;
            }

            simulation.reset();
        }

        for (int i = 0; i < fitnesses.length; i++) {
            fitnesses[i] /= col;
            this.ensembleContribution[i] = this.ensembleContribution[i]/col;
        }
        MultiObjectiveFitness f = (MultiObjectiveFitness) fitness;
        f.setObjectives(state, fitnesses);
        this.fitness = (MultiObjectiveFitness)f.clone();
    }

    public OperationOption priorOperationLinear(SequencingDecisionSituation sequencingDecisionSituation, double[] ensembleContribution) {

//        System.out.println("use ensemble to select operation");
        List<OperationOption> queue = sequencingDecisionSituation.getQueue();
        WorkCenter workCenter = sequencingDecisionSituation.getWorkCenter();
        SystemState systemState = sequencingDecisionSituation.getSystemState();
        int[] eachElementDecision = new int[ensembleRoutingRule.size()];
        double[] preferences = new double[ensembleRoutingRule.size()];
        if(systemState.getSimulation().state instanceof GPRuleEvolutionStatePSL){
            int currentGen = systemState.getSimulation().state.generation;
            preferences = ((PSLInitializer)systemState.getSimulation().state.initializer).weights[currentGen];
        }
        else{
            for(int i=0; i<preferences.length; i++){
                preferences[i] = 1;
            }
        }

        //fzhang 2018.10.23  original one
        //============================start==============================
        double totalPriority = 0;
        OperationOption priorOp = queue.get(0);
        for(int ensembleIndex=0; ensembleIndex<ensembleSequencingRule.size();ensembleIndex++) {
            GPRule sequencingRule = ensembleSequencingRule.get(ensembleIndex);
            totalPriority += preferences[ensembleIndex]*sequencingRule.priority(priorOp, workCenter, systemState);
        }
        priorOp.setPriority(totalPriority);
        int bestId = 0;

            for (int i = 1; i < queue.size(); i++) {
                OperationOption op = queue.get(i);
                totalPriority = 0;
                for(int ensembleIndex=0; ensembleIndex<ensembleSequencingRule.size();ensembleIndex++) {
                    GPRule sequencingRule = ensembleSequencingRule.get(ensembleIndex);
                    totalPriority += preferences[ensembleIndex]*sequencingRule.priority(op, workCenter, systemState);
                }
                op.setPriority(totalPriority);

                if (op.priorTo(priorOp)){
                    priorOp = op;
                    bestId = i;
                }

            }


        //add by mengxu 2023.02.24
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            if(eachElementDecision[ensembleIndex] == bestId){
                ensembleContribution[ensembleIndex] += 1;
            }
        }

        return queue.get(bestId);// this links which operation will be chosen.
    }

    public OperationOption nextOperationOptionLinear(RoutingDecisionSituation routingDecisionSituation, double[] ensembleContribution) {
        // currently, using the easiest way, just to sum all the priority
//        System.out.println("use ensemble to select machine");
        List<OperationOption> queue = routingDecisionSituation.getQueue();
        SystemState systemState = routingDecisionSituation.getSystemState();
        int[] eachElementDecision = new int[ensembleRoutingRule.size()];
        double[] preferences = new double[ensembleRoutingRule.size()];
        if(systemState.getSimulation().state instanceof GPRuleEvolutionStatePSL){
            int currentGen = systemState.getSimulation().state.generation;
            preferences = ((PSLInitializer)systemState.getSimulation().state.initializer).weights[currentGen];
        }
        else{
            for(int i=0; i<preferences.length; i++){
                preferences[i] = 1;
            }
        }
//        int currentGen = systemState.getSimulation().state.generation;
//        double[] preferences = ((PSLInitializer)systemState.getSimulation().state.initializer).weights[currentGen];
        //================original=================
        //==================start==================
//        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
//            GPRule routingRule = ensembleRoutingRule.get(ensembleIndex);
//            OperationOption bestOperationOption = queue.get(0);
//            bestOperationOption
//                    .setPriority(routingRule.priority(bestOperationOption, bestOperationOption.getWorkCenter(), systemState));
//            // loop all the options, save the best one as "selected" one
//            int bestId_each_element = 0;
//            for (int i = 1; i < queue.size(); i++) {
//                OperationOption operationOption = queue.get(i);
//                operationOption.setPriority(routingRule.priority(operationOption, operationOption.getWorkCenter(), systemState));
//
//                if (operationOption.priorTo(bestOperationOption)) {
//                    bestOperationOption = operationOption;
//                    bestId_each_element = i;
//                }
//            }
//            eachElementDecision[ensembleIndex] = bestId_each_element;
//        }

        //the ensemble decision

        OperationOption bestOperationOption = queue.get(0);
        double totalPriority = 0;
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            GPRule routingRule = ensembleRoutingRule.get(ensembleIndex);
            totalPriority += preferences[ensembleIndex]*routingRule.priority(bestOperationOption, bestOperationOption.getWorkCenter(), systemState);
        }
        bestOperationOption
                .setPriority(totalPriority);
        // loop all the options, save the best one as "selected" one
        int bestId = 0;
        for (int i = 1; i < queue.size(); i++) {
            OperationOption operationOption = queue.get(i);
            totalPriority = 0;
            for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
                GPRule routingRule = ensembleRoutingRule.get(ensembleIndex);
                totalPriority += preferences[ensembleIndex]*routingRule.priority(operationOption, operationOption.getWorkCenter(), systemState);
            }
            operationOption.setPriority(totalPriority);

            if (operationOption.priorTo(bestOperationOption)) {
                bestOperationOption = operationOption;
                bestId = i;
            }
        }

        OperationOption bestOperationOptionVoting = queue.get(bestId);//todo: need to double check 2023.03.07

        //add by mengxu 2023.02.24
        for(int ensembleIndex=0; ensembleIndex<ensembleRoutingRule.size();ensembleIndex++){
            if(eachElementDecision[ensembleIndex] == bestId){
                ensembleContribution[ensembleIndex] += 1;
            }
        }

        return bestOperationOptionVoting;// this links which machine will be chosen.
    }

    public EnsembleRule clone(){
        List<Individual> ensembleClone = new ArrayList<>();
        List<Integer> elementOriginalIndexClone = new ArrayList<>();
        if(this.elementOriginalIndex == null){
            elementOriginalIndexClone = null;
        }
//        List<GPRule> ensembleSequencingRuleClone = new ArrayList<>();
//        List<GPRule> ensembleRoutingRuleClone = new ArrayList<>();
        int ensembleSizeClone = this.ensemble.size();

        for(int i=0; i<ensembleSizeClone; i++){
            Individual indi = (GPIndividual)ensemble.get(i).clone();
//            GPRule sequencingRule = new GPRule(RuleType.SEQUENCING, ((GPIndividual) indi).trees[0]);
//            GPRule routingRule = new GPRule(RuleType.ROUTING, ((GPIndividual) indi).trees[1]);

            ensembleClone.add(indi);
            if(this.elementOriginalIndex != null) {
                elementOriginalIndexClone.add(this.elementOriginalIndex.get(i));
            }
//            ensembleSequencingRuleClone.add(sequencingRule);
//            ensembleRoutingRuleClone.add(routingRule);
        }
        if(this.elementOriginalIndex != null) {
            EnsembleRule ensembleRuleClone = new EnsembleRule(ensembleClone, elementOriginalIndexClone, ensembleSizeClone);
            ensembleRuleClone.fitness = (MultiObjectiveFitness) this.fitness.clone();
            ensembleRuleClone.ensembleContribution = this.ensembleContribution.clone();
            return ensembleRuleClone;
        }
        else{
            EnsembleRule ensembleRuleClone = new EnsembleRule(ensembleClone, ensembleSizeClone);
            if(this.evaluated){
                ensembleRuleClone.fitness = (MultiObjectiveFitness) this.fitness.clone(); //modified by mengxu 2023.07.31
                ensembleRuleClone.evaluated = true;
            }
            else{
                ensembleRuleClone.fitness = null;
                ensembleRuleClone.evaluated = false;
            }
            ensembleRuleClone.ensembleContribution = this.ensembleContribution.clone();
//            ensembleRuleClone.fitness = (MultiObjectiveFitness) this.fitness.clone();
//            ensembleRuleClone.ensembleContribution = this.ensembleContribution.clone();
            return ensembleRuleClone;
        }
    }

    public static void main(String[] args){

    }
}
