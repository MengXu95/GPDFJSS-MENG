package yimei.jss.niching;

import ec.EvolutionState;
import ec.Individual;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.lexicaseselection.LSmultiInstance.MultiInstanceMultiObjectiveFitness;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer;
import mengxu.algorithm.multitaskHeuristicLearning.GPRuleEvolutionStateMultiTaskLearningSurrogate;
import mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodel.GPRuleEvolutionStateMultiTaskLearningSurrogateSVR;
import mengxu.algorithm.multitaskHeuristicLearning.SVRrelationmodelOneSubpop.GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop;
import mengxu.algorithm.multitaskHeuristicLearning.onlysurrogate.GPRuleEvolutionStateMultiTaskLearningOnlySurrogate;
import mengxu.complexsimulation.HeterogeneousSimulation;
import yimei.jss.jobshop.FlexibleStaticInstance;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.weighted.WSPT;
//import yimei.jss.rule.workcenter.basic.WIQ;
import yimei.jss.rule.workcenter.basic.WIQ;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.StaticSimulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SequencingPhenoCharacterisation extends PhenoCharacterisation {
    public List<SequencingDecisionSituation> decisionSituations;
    private int[][] referenceIndexes;

    public SequencingPhenoCharacterisation(AbstractRule sequencingReferenceRule,
                                           List<SequencingDecisionSituation> decisionSituations) {
        super(sequencingReferenceRule);
        this.decisionSituations = decisionSituations;
        this.referenceIndexes = new int[decisionSituations.size()][];

        calcReferenceIndexes();
    }

    public SequencingPhenoCharacterisation(EvolutionState state, AbstractRule sequencingReferenceRule,
                                           List<SequencingDecisionSituation> decisionSituations) {
        super(sequencingReferenceRule);
        this.decisionSituations = decisionSituations;
        this.referenceIndexes = new int[decisionSituations.size()][];

        calcReferenceIndexes(state);
    }

    public int[] decision(AbstractRule rule) {
        int[] charList = new int[decisionSituations.size()];

        for (int i = 0; i < decisionSituations.size(); i++) {
            SequencingDecisionSituation situation = decisionSituations.get(i);
            List<OperationOption> queue = situation.getQueue();


            // Calculate the priority for all the operations.
            for (OperationOption op : queue) {
                op.setPriority(rule.priority(
                        op, situation.getWorkCenter(), situation.getSystemState()));
            }

            //get the index of best operation
            int idxBestOp = 0;
            for (int j = 0; j < queue.size(); j++) {
                if (queue.get(j).priorTo(queue.get(idxBestOp))) {
                    idxBestOp = j;
                }
            }
            charList[i] = idxBestOp;
        }

        return charList;
    }

    public int[] characterise(AbstractRule rule) {
        int[] charList = new int[decisionSituations.size()];

        for (int i = 0; i < decisionSituations.size(); i++) {
            SequencingDecisionSituation situation = decisionSituations.get(i);
            List<OperationOption> queue = situation.getQueue();


            // Calculate the priority for all the operations.
            for (OperationOption op : queue) {
                op.setPriority(rule.priority(
                        op, situation.getWorkCenter(), situation.getSystemState()));
            }

            //get the index of best operation
            int idxBestOp = 0;
            for (int j = 0; j < queue.size(); j++) {
                if (queue.get(j).priorTo(queue.get(idxBestOp))) {
                    idxBestOp = j;
                }
            }
            charList[i] = referenceIndexes[i][idxBestOp];
        }

        return charList;
    }

    //add by mengxu 2024.3.19 for Pareto Set Learning
    public int[] characteriseWithPreference(EvolutionState state, AbstractRule rule, double[] preference) {
        int[] charList = new int[decisionSituations.size()];

        EvolutionState preferenceState = new GPRuleEvolutionStatePSL();
        ((GPRuleEvolutionStatePSL)preferenceState).evaluatePreferenceIndex = 0;
        preferenceState.initializer = new PSLInitializer();
        double[][] weightsNew = new double[1][];
        weightsNew[0] = preference;
        ((PSLInitializer)preferenceState.initializer).weights = weightsNew;

        for (int i = 0; i < decisionSituations.size(); i++) {
            SequencingDecisionSituation situation = decisionSituations.get(i);

            //for normalising the terminals add by mengxu 2024.4.19
            if(state instanceof GPRuleEvolutionStatePSL){
                if(((GPRuleEvolutionStatePSL) state).terminalNormalisation==0){
                    ((GPRuleEvolutionStatePSL) preferenceState).terminalNormalisation=0;
                    ((GPRuleEvolutionStatePSL) preferenceState).initialMaxTerminals();
                    ((GPRuleEvolutionStatePSL) preferenceState).updateMaxTerminals(situation);
                }
                else if (((GPRuleEvolutionStatePSL) state).terminalNormalisation==1){
                    ((GPRuleEvolutionStatePSL) preferenceState).terminalNormalisation=1;
                }
                else{
                    ((GPRuleEvolutionStatePSL) preferenceState).terminalNormalisation=2;
                }
            }

            List<OperationOption> queue = situation.getQueue();

            situation.getSystemState().getSimulation().state = preferenceState;
            situation.getSystemState().getSimulation().state.generation = 0;

            // Calculate the priority for all the operations.
            for (OperationOption op : queue) {
                op.setPriority(rule.priority(
                        op, situation.getWorkCenter(), situation.getSystemState()));
            }

            //get the index of best operation
            int idxBestOp = 0;
            for (int j = 0; j < queue.size(); j++) {
                if (queue.get(j).priorTo(queue.get(idxBestOp))) {
                    idxBestOp = j;
                }
            }
            charList[i] = referenceIndexes[i][idxBestOp];
        }

        return charList;
    }

    //add by mengxu 2024.3.19 for Pareto Set Learning
    public int[] characteriseForMultitask(EvolutionState state, AbstractRule rule, int subpop) {
        int[] charList = new int[decisionSituations.size()];
        EvolutionState multitaskState = null;

        if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogate){
            multitaskState = new GPRuleEvolutionStateMultiTaskLearningSurrogate();
            ((GPRuleEvolutionStateMultiTaskLearningSurrogate)multitaskState).currentSubpop = subpop;
            ((GPRuleEvolutionStateMultiTaskLearningSurrogate)multitaskState).utilisationLevels = ((GPRuleEvolutionStateMultiTaskLearningSurrogate)state).utilisationLevels;
        }
        else if(state instanceof GPRuleEvolutionStateMultiTaskLearningOnlySurrogate){
            multitaskState = new GPRuleEvolutionStateMultiTaskLearningOnlySurrogate();
            ((GPRuleEvolutionStateMultiTaskLearningOnlySurrogate)multitaskState).currentSubpop = subpop;
            ((GPRuleEvolutionStateMultiTaskLearningOnlySurrogate)multitaskState).utilisationLevels = ((GPRuleEvolutionStateMultiTaskLearningOnlySurrogate)state).utilisationLevels;
        }
        else if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogateSVR){
            multitaskState = new GPRuleEvolutionStateMultiTaskLearningSurrogateSVR();
            ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVR)multitaskState).currentSubpop = subpop;
            ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVR)multitaskState).utilisationLevels = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVR)state).utilisationLevels;
        }
        else if(state instanceof GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop){
            multitaskState = new GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop();
            ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)multitaskState).currentUtilLevelIndex = subpop;
            ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)multitaskState).utilisationLevels = ((GPRuleEvolutionStateMultiTaskLearningSurrogateSVROneSubpop)state).utilisationLevels;
        }

        for (int i = 0; i < decisionSituations.size(); i++) {
            SequencingDecisionSituation situation = decisionSituations.get(i);

            List<OperationOption> queue = situation.getQueue();

            situation.getSystemState().getSimulation().state = multitaskState;

            // Calculate the priority for all the operations.
            for (OperationOption op : queue) {
                op.setPriority(rule.priority(
                        op, situation.getWorkCenter(), situation.getSystemState()));
            }

            //get the index of best operation
            int idxBestOp = 0;
            for (int j = 0; j < queue.size(); j++) {
                if (queue.get(j).priorTo(queue.get(idxBestOp))) {
                    idxBestOp = j;
                }
            }
            charList[i] = referenceIndexes[i][idxBestOp];
        }

        return charList;
    }

    //add by mengxu 2022.01.14
    public int[][] characterisePermutation(AbstractRule rule) {
        SequencingDecisionSituation sit = decisionSituations.get(0);
        List<OperationOption> que = sit.getQueue();
        int[][] charList = new int[decisionSituations.size()][que.size()];

        for (int i = 0; i < decisionSituations.size(); i++) {
            SequencingDecisionSituation situation = decisionSituations.get(i);
            List<OperationOption> queue = situation.getQueue();

            // Calculate the priority for all the operations.
            for (OperationOption op : queue) {
                op.setPriority(rule.priority(
                        op, situation.getWorkCenter(), situation.getSystemState()));
            }

            // Calculate the priority for all the operations.
            int[] opSort = optionSort(queue, referenceIndexes[i]);

            charList[i] = opSort.clone();
        }

        return charList;
    }

    protected void calcReferenceIndexes() {
        for (int i = 0; i < decisionSituations.size(); i++) {
            SequencingDecisionSituation situation = decisionSituations.get(i);
            int[] ranks = referenceRule.priorValueOperation(situation);

            //int index = situation.getQueue().indexOf(op);
            referenceIndexes[i] = ranks;
        }
    }

    protected void calcReferenceIndexes(EvolutionState state) {
        for (int i = 0; i < decisionSituations.size(); i++) {
            SequencingDecisionSituation situation = decisionSituations.get(i);

            //for normalising the terminals add by mengxu 2024.4.19
            if(state instanceof GPRuleEvolutionStatePSL){
                if(((GPRuleEvolutionStatePSL) state).terminalNormalisation==0){
                    ((GPRuleEvolutionStatePSL) state).updateMaxTerminals(situation);
                }
            }

            int[] ranks = referenceRule.priorValueOperation(situation);

            //int index = situation.getQueue().indexOf(op);
            referenceIndexes[i] = ranks;
        }
    }

    /**
     * Add by mengxu 2024.3.20, for Pareto set learning
     */
    protected void calcReferenceIndexesWithPreference(final EvolutionState state) {
        for (int i = 0; i < decisionSituations.size(); i++) {
            SequencingDecisionSituation situation = decisionSituations.get(i);

            situation.getSystemState().getSimulation().state = state;

            //for normalising the terminals add by mengxu 2024.4.19
            if(state instanceof GPRuleEvolutionStatePSL){
                if(((GPRuleEvolutionStatePSL) state).terminalNormalisation==0){
                    ((GPRuleEvolutionStatePSL) state).updateMaxTerminals(situation);
                }
            }

            int[] ranks = referenceRule.priorValueOperation(situation);

            //int index = situation.getQueue().indexOf(op);
            referenceIndexes[i] = ranks;
        }
    }

    //modified by mengxu 2021.07.09
    public static PhenoCharacterisation sameSimulationPhenoCharacterisation(long seed, int numDecisionSituations, double utilLevel, double dueDateFactor) {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING); //op.getProcTime() / op.getJob().getWeight();
        //the larger the weight, the smaller the WSPT value
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);

        //fzhang 2019.6.22 original
     /*   int minQueueLength = 8;
        int numDecisionSituations = 20;*/

        //fzhang 2019.6.22 change to 7, otherwise, can not get this kinds of simulations---because the simulation can not enough queue size as 7
        int minQueueLength = 7;
//        int numDecisionSituations = 20;//used for measuring the behavior of different rules

        long shuffleSeed = 8295342;

        /*DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
*/

        //modified by mengxu 2021.09.01
        HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(seed, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                utilLevel, dueDateFactor); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state


        //original
//        DynamicSimulation simulation = DynamicSimulation.standardFull(seed, defaultSequencingRule,
//                defaultRoutingRule, 10, 500, 0,
//                utilLevel, dueDateFactor); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state

        List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
        Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
        //randomly change the sorting of list of situations

        situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
        //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
        return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
    }

    //this one is new 2023.09.27 for test
    public static PhenoCharacterisation defaultPhenoCharacterisationNew(Simulation evoSimulation) {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING); //op.getProcTime() / op.getJob().getWeight();
        //the larger the weight, the smaller the WSPT value
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);

        //fzhang 2019.6.22 change to 7, otherwise, can not get this kinds of simulations---because the simulation can not enough queue size as 7
        int minQueueLength = 7;
        int numDecisionSituations = 20;//used for measuring the behavior of different rules

        long shuffleSeed = 8295342;

        /*DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
*/

        //modified by mengxu 2021.09.01
        if(evoSimulation instanceof HeterogeneousSimulation){
            HeterogeneousSimulation simulation = HeterogeneousSimulation.standardMissing(0, defaultSequencingRule,
                    defaultRoutingRule, 10, 5000, 1000,
                    0.99, 1.5);
//            HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(0, defaultSequencingRule,
//                    defaultRoutingRule, 10, 500, 0,
//                    0.99, 1.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
            List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
            Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
            //randomly change the sorting of list of situations

            situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
            //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
            return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
        }
        else{
            DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                    defaultRoutingRule, 10, 500, 0,
                    0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
            List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
            Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
            //randomly change the sorting of list of situations

            situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
            //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
            return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
        }
    }

    public static PhenoCharacterisation defaultPhenoCharacterisation(Simulation evoSimulation) {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING); //op.getProcTime() / op.getJob().getWeight();
        //the larger the weight, the smaller the WSPT value
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);

        //fzhang 2019.6.22 original
     /*   int minQueueLength = 8;
        int numDecisionSituations = 20;*/

        //fzhang 2019.6.22 change to 7, otherwise, can not get this kinds of simulations---because the simulation can not enough queue size as 7
        int minQueueLength = 7;
        int numDecisionSituations = 20;//used for measuring the behavior of different rules

        long shuffleSeed = 8295342;

        /*DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
*/

        //modified by mengxu 2021.09.01
        if(evoSimulation instanceof HeterogeneousSimulation){
            HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(0, defaultSequencingRule,
                    defaultRoutingRule, 10, 500, 0,
                    0.99, 1.0);
//            HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(0, defaultSequencingRule,
//                    defaultRoutingRule, 10, 500, 0,
//                    0.99, 1.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
            List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
            Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
            //randomly change the sorting of list of situations

            situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
            //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
            return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
        }
        else{
            DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                    defaultRoutingRule, 10, 500, 0,
                    0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
            List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
            Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
            //randomly change the sorting of list of situations

            situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
            //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
            return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
        }
    }

    public static PhenoCharacterisation defaultPhenoCharacterisation(Simulation evoSimulation, int minQueueLength, int numDecisionSituations) {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING); //op.getProcTime() / op.getJob().getWeight();
        //the larger the weight, the smaller the WSPT value
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);

        //fzhang 2019.6.22 original
     /*   int minQueueLength = 8;
        int numDecisionSituations = 20;*/

        //fzhang 2019.6.22 change to 7, otherwise, can not get this kinds of simulations---because the simulation can not enough queue size as 7
//        int minQueueLength = 7;
//        int numDecisionSituations = 20;//used for measuring the behavior of different rules

        long shuffleSeed = 8295342;

        /*DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
*/

        //modified by mengxu 2021.09.01
        if(evoSimulation instanceof HeterogeneousSimulation){
            HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(0, defaultSequencingRule,
                    defaultRoutingRule, 10, 500, 0,
                    0.99, 1.0);
//            HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(0, defaultSequencingRule,
//                    defaultRoutingRule, 10, 500, 0,
//                    0.99, 1.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
            List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
            Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
            //randomly change the sorting of list of situations

            situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
            //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
            return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
        }
        else{
            DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                    defaultRoutingRule, 10, 500, 0,
                    0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
            List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
            Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
            //randomly change the sorting of list of situations

            situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
            //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
            return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
        }
    }

    /**
     * add by mengxu 2024.4.19 for get decision points for each case
     * @return
     */
    public static List<PhenoCharacterisation> defaultPhenoCharacterisationAllCases(EvolutionState state, Simulation evoSimulation) {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING); //op.getProcTime() / op.getJob().getWeight();
        //the larger the weight, the smaller the WSPT value
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);

        int minQueueLength = 7;
        int numDecisionSituations = 5;//used for measuring the behavior of different rules

        long shuffleSeed = 8295342;

        //modified by mengxu 2021.09.01
        if(evoSimulation instanceof HeterogeneousSimulation){
            if(evoSimulation.useLS){
                Individual[] individuals = state.population.subpops[0].individuals;
                Individual ind = individuals[0];
                int numCases = ((OneInstanceMultiCaseMultiObjectiveFitness)ind.fitness).multiInstanceMultiObjectiveFitness.length;

                HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(1, defaultSequencingRule,
                        defaultRoutingRule, 10, 5000, 1000,
                        1.089, 1.0);
                simulation.useLS = true;
                simulation.warmupSame = true;

                List<List<SequencingDecisionSituation>> situations = simulation.sequencingDecisionSituationsAllCases(minQueueLength,numCases); //situations have 20 elements

                List<PhenoCharacterisation> phenoCharacterisationsAllCases = new ArrayList<>();
                for(int i=0; i<numCases; i++){
                    List<SequencingDecisionSituation> situationsCase = situations.get(i);
                    Collections.shuffle(situationsCase, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
                    //randomly change the sorting of list of situations

                    situationsCase = situationsCase.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
                    PhenoCharacterisation sequencingPhenoCharacterisation = new SequencingPhenoCharacterisation(defaultSequencingRule, situationsCase);
                    phenoCharacterisationsAllCases.add(sequencingPhenoCharacterisation);
                    //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
                }

                return phenoCharacterisationsAllCases;
            }
            else{
                System.out.println("Error defaultPhenoCharacterisationAllCases!");
                return null;
            }
        }
        else{
            System.out.println("Error defaultPhenoCharacterisationAllCases!");
            return null;
        }
    }

    public static PhenoCharacterisation defaultPhenoCharacterisation() {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING); //op.getProcTime() / op.getJob().getWeight();
        //the larger the weight, the smaller the WSPT value
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);

        //fzhang 2019.6.22 original
     /*   int minQueueLength = 8;
        int numDecisionSituations = 20;*/

        //fzhang 2019.6.22 change to 7, otherwise, can not get this kinds of simulations---because the simulation can not enough queue size as 7
        int minQueueLength = 7 ;//todo: error no length equal to 7!!! need to modify!
        int numDecisionSituations = 20;//used for measuring the behavior of different rules

        long shuffleSeed = 8295342;

        /*DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
*/

        //todo:2022.03.22
        //modified by mengxu 2021.09.01
        HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.99, 1.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state

//        DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
//                defaultRoutingRule, 10, 500, 0,
//                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state


        List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
        Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
        //randomly change the sorting of list of situations

        situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
        //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
        return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
    }

    public static PhenoCharacterisation defaultPhenoCharacterisation(EvolutionState state, int numDecisionSituations) {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING); //op.getProcTime() / op.getJob().getWeight();
        //the larger the weight, the smaller the WSPT value
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);

        //fzhang 2019.6.22 original
     /*   int minQueueLength = 8;
        int numDecisionSituations = 20;*/

        //fzhang 2019.6.22 change to 7, otherwise, can not get this kinds of simulations---because the simulation can not enough queue size as 7
        int minQueueLength = 7 ;//todo: error no length equal to 7!!! need to modify!
//        int numDecisionSituations = 20;//used for measuring the behavior of different rules

        long shuffleSeed = 8295342;

        /*DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
*/

        //todo:2022.03.22
        //modified by mengxu 2021.09.01
        HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.99, 1.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state

//        DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
//                defaultRoutingRule, 10, 500, 0,
//                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state

        simulation.state = state;//add by mengxu 2024.4.18
        List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
        Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
        //randomly change the sorting of list of situations

        situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
        //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
        return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
    }

    public static PhenoCharacterisation defaultPhenoCharacterisation(EvolutionState state) {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING); //op.getProcTime() / op.getJob().getWeight();
        //the larger the weight, the smaller the WSPT value
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);

        //fzhang 2019.6.22 original
     /*   int minQueueLength = 8;
        int numDecisionSituations = 20;*/

        //fzhang 2019.6.22 change to 7, otherwise, can not get this kinds of simulations---because the simulation can not enough queue size as 7
        int minQueueLength = 7 ;//todo: error no length equal to 7!!! need to modify!
        int numDecisionSituations = 20;//used for measuring the behavior of different rules

        long shuffleSeed = 8295342;

        /*DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
*/

        //todo:2022.03.22
        //modified by mengxu 2021.09.01
        HeterogeneousSimulation simulation = HeterogeneousSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.99, 1.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state

//        DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
//                defaultRoutingRule, 10, 500, 0,
//                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state

        simulation.state = state;//add by mengxu 2024.4.18
        List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
        Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
        //randomly change the sorting of list of situations

        situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
        //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
        return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
    }

    public static PhenoCharacterisation defaultPhenoCharacterisation(EvolutionState state, double util) {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING); //op.getProcTime() / op.getJob().getWeight();
        //the larger the weight, the smaller the WSPT value
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);

        //fzhang 2019.6.22 original
     /*   int minQueueLength = 8;
        int numDecisionSituations = 20;*/

        //fzhang 2019.6.22 change to 7, otherwise, can not get this kinds of simulations---because the simulation can not enough queue size as 7
        int minQueueLength = 5 ;//todo: error no length equal to 7!!! need to modify!
        int numDecisionSituations = 20;//used for measuring the behavior of different rules

        long shuffleSeed = 8295342;

        /*DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
                defaultRoutingRule, 10, 500, 0,
                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state
*/

        //todo:2022.03.22
        //modified by mengxu 2021.09.01
        HeterogeneousSimulation simulation = HeterogeneousSimulation.standardMissing(0, defaultSequencingRule,
                defaultRoutingRule, 10, 4000, 1000,
                util, 1.5); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state

//        DynamicSimulation simulation = DynamicSimulation.standardFull(0, defaultSequencingRule,
//                defaultRoutingRule, 10, 500, 0,
//                0.95, 4.0); //use this simulation, no warmup jobs? --- here, just to measure the behavior of rule, so need to get a steady state

        simulation.state = state;//add by mengxu 2024.4.18
        List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength); //situations have 20 elements
        Collections.shuffle(situations, new Random(shuffleSeed)); //Randomly permute the specified list using the specified source of randomness.
        //randomly change the sorting of list of situations
        System.out.println("situations: " + situations.size());
        situations = situations.subList(0, numDecisionSituations); //Returns a view of the portion of this list between the specified fromIndex,
        //inclusive, and toIndex, exclusive. (If fromIndex and toIndex are equal, the returned list is empty.)
        return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
    }

    public static PhenoCharacterisation defaultPhenoCharacterisation(String filePath) {
        AbstractRule defaultSequencingRule = new WSPT(RuleType.SEQUENCING);
        AbstractRule defaultRoutingRule = new WIQ(RuleType.ROUTING);
        FlexibleStaticInstance flexibleStaticInstance = FlexibleStaticInstance.readFromAbsPath(filePath);
        StaticSimulation simulation = new StaticSimulation(defaultSequencingRule, defaultRoutingRule,
                flexibleStaticInstance);

    /*    int minQueueLength = 8; // some flexible static instances will have short queues
        int numDecisionSituations = 20;*/

        int minQueueLength = 7; // some flexible static instances will have short queues
        int numDecisionSituations = 100;

        long shuffleSeed = 8295342;

        //the number of sequencing decisions available of a given queue length will vary
        //greatly for different statics instances, so we'll start with 8 and decrease
        //the min queue length until we can get at least 20
        List<SequencingDecisionSituation> situations = simulation.sequencingDecisionSituations(minQueueLength);
        while (situations.size() < numDecisionSituations && minQueueLength > 2) {
            minQueueLength--;
            situations = simulation.sequencingDecisionSituations(minQueueLength);
        }

        if (minQueueLength == 2 && situations.size() < 100) {
            //no point going to queue length of 1, as this will only have 1 outcome
            System.out.println("Only "+situations.size() +" instances available for sequencing pheno characterisation.");
            numDecisionSituations = situations.size();
        }

        Collections.shuffle(situations, new Random(shuffleSeed));

        situations = situations.subList(0, numDecisionSituations);
        return new SequencingPhenoCharacterisation(defaultSequencingRule, situations);
    }

    public List<SequencingDecisionSituation> getDecisionSituations() {
        return decisionSituations;
    }

    public int[][] getReferenceIndexes() {
        return referenceIndexes;
    }
}