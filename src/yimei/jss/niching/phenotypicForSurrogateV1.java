package yimei.jss.niching;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.util.Parameter;
import mengxu.algorithm.diversepartnerselection.baselineTourWithBRandKNNSurrogate.GPRuleEvolutionStateBase;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNCaseSurrogate.GPRuleEvolutionStateDPSNSBRKNNCase;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNSurrogate.GPRuleEvolutionStateDPSNSBR;
import mengxu.algorithm.diversepartnerselection.withNewStrategy.withBRandKNNSurrogate.OffspringSelectionStrategyForTopTwoCrossover.GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy;
import mengxu.algorithm.lexicaseselection.OneInstanceMultiCaseMultiObjectiveFitness;
import org.apache.commons.lang3.ArrayUtils;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
2019.9.24 calculate the (phenotypy characteristic) PC of each individuals in each population
 not used in weighted feature idea
 */
public class phenotypicForSurrogateV1 {
    static double[][][] indsCharLists = null; //save the PC information
    static double[][] indsCharListsMultiTreeTemp = null;
    static double[][][] indsCharListsMultiTree = null;
    //2020.2.10 calculate the pc of individuals directly
    static double[][] indsCharListsMultiTreeAll = null;
    public static final String P_TREESIZE = "num-trees";
    public static int numTrees;
    //static GPRule[] benckmarkRule = new GPRule[2];
    static ArrayList<GPRule> benckmarkRule = new ArrayList<>();

    public static final String P_REPLICATIONS = "num-Rep";

    //modified by mengxu 2021.01.14---------------------
    static int[][][][] indsCharListsPermutation = null; //save the PC information
    static int[][][] indsCharListsPermutationMultiTreeTemp = null;
    static int[][][][] indsCharListsPermutationMultiTree = null;
    //--------------------------------------------------

    static int[][] oneIndCharLists = null; //save the PC information
    static int[] oneIndCharListsMultiTree = null;

    //public static int numRep;

    //previous PC calculation
   /* public static double[][] phenotypicPopulation(final EvolutionState state,
                                       PhenoCharacterisation[] pc) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);

        indsCharLists = new double[numTrees][(int)(state.population.subpops[0].individuals.length)][];

        for (int treeID = 0; treeID < numTrees; treeID++) {//each population
            RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
            PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
            Individual[] inds = state.population.subpops[0].individuals; //only one subpop now
            phenoCharacterisation.setReferenceRule(new GPRule(ruleType,((GPIndividual)inds[0]).trees[treeID]));

            //each individuals
            for(int ind = 0; ind < (int)(inds.length); ind ++){
                int[] charList = phenoCharacterisation.characterise(  //.characterise: calculate the distance
                        new GPRule(ruleType,((GPIndividual)inds[ind]).trees[treeID]));
                indsCharLists[treeID][ind] = new double[charList.length];

                //each PC information---convert int[] to int[][]
                for(int numFeature = 0; numFeature < charList.length; numFeature ++){
                    indsCharLists[treeID][ind][numFeature] = charList[numFeature];
                }
            }
        }

        //combine the phenotype of sequencing rule with the phenotype of routing rule
        indsCharListsMultiTree = new double[indsCharLists[0].length][];
        //indsCharLists[0].length == indsCharLists[1].length
        for(int i = 0; i < indsCharLists[0].length; i++){
                double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][i], indsCharLists[1][i]);
                indsCharListsMultiTree[i] = combinePheChar;
        }

        return indsCharListsMultiTree;
    }*/

/*    //2019.9.25 fzhang
    public static double[][] phenotypicPopulation(final EvolutionState state,
                                                  PhenoCharacterisation[] pc,
                                                  Boolean nonIntermediatePop) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);

        indsCharLists = new double[numTrees][(int)(state.population.subpops[0].individuals.length)][];

        for (int treeID = 0; treeID < numTrees; treeID++) {//each population
            RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
            PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
            Individual[] inds = state.population.subpops[0].individuals; //only one subpop now

          *//*  if(treeID ==1){
                phenoCharacterisation.setReferenceRule(GPRule.readFromLispExpression(ruleType, "(+ WIQ MWT)"));  build a new GP Rule
            }*//*

          if(nonIntermediatePop){
              phenoCharacterisation.setReferenceRule(new GPRule(ruleType,((GPIndividual)inds[0]).trees[treeID]));
              benckmarkRule[treeID] = new GPRule(ruleType,((GPIndividual)inds[0]).trees[treeID]);
          }
          else{
              phenoCharacterisation.setReferenceRule(benckmarkRule[treeID]);
          }

            //each individuals
            for(int ind = 0; ind < (int)(inds.length); ind ++){
                int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                        new GPRule(ruleType,((GPIndividual)inds[ind]).trees[treeID]));
                indsCharLists[treeID][ind] = new double[charList.length];

                //each PC information---convert int[] to int[][]
                for(int numFeature = 0; numFeature < charList.length; numFeature ++){
                    indsCharLists[treeID][ind][numFeature] = charList[numFeature];
                }
            }
        }

        //combine the phenotype of sequencing rule with the phenotype of routing rule
        indsCharListsMultiTree = new double[indsCharLists[0].length][];
        //indsCharLists[0].length == indsCharLists[1].length
        for(int i = 0; i < indsCharLists[0].length; i++){
            double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][i], indsCharLists[1][i]);
            indsCharListsMultiTree[i] = combinePheChar;
        }

        return indsCharListsMultiTree;
    }*/

    //2021.9.21 calculate the pc of inds from an arraylist
    //===============================start=================================
    public static int[][] muchBetterPhenotypicPopulation(ArrayList<Individual> inds, PhenoCharacterisation[] pc)
    {
        int[][] indsCharListsMultiTree = new int[inds.size()][];
        for(int i = 0; i < inds.size(); i++)
            indsCharListsMultiTree[i] = phenotypicPopulation(pc, inds.get(i));

        return indsCharListsMultiTree;
    }

    //2021.8.16 implemented by mazhar---calculate the phenotypic characteristic of each individual  this one is more efficient
    public static int[] phenotypicPopulation(PhenoCharacterisation[] pc,
                                             Individual ind) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        int numTrees = ((GPIndividual)ind).trees.length;
        oneIndCharLists = new int[numTrees][];

        for (int treeID = 0; treeID < numTrees; treeID++)
        {
            RuleType ruleType = ruleTypes[treeID];
            PhenoCharacterisation phenoCharacterisation = pc[treeID];

            oneIndCharLists[treeID] = phenoCharacterisation.characterise(new GPRule(ruleType, ((GPIndividual) ind).trees[treeID]));
        }

        //combine the phenotype of sequencing rule with the phenotype of routing rule
        oneIndCharListsMultiTree = Stream.of(oneIndCharLists).flatMapToInt(IntStream::of).toArray();

        return oneIndCharListsMultiTree;
    }
    //=======================================end==========================================

    //2020.2.8 calculate the pc of the individuals in each subpopulation, respectively. And save them as KNN surrogate model. Als, update the decision situation for each subpopulation.
    public static double[][][] phenotypicPopulation(final EvolutionState state,
                                                    PhenoCharacterisation[] pc,
                                                    Boolean nonIntermediatePop) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);

        indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][];
        indsCharListsMultiTree = new double[state.population.subpops.length][][];

        //2020.2.12 save the best rules of each subpops, and use them to generate decision situations later.
        //in this case, the decision situations are used for each subpop are generating with its best rule. It is expected to examine the pc of its individuals better.
        if(nonIntermediatePop){
            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
                for (int treeID = 0; treeID < numTrees; treeID++) {
                    if(benckmarkRule.size() < numTrees*state.population.subpops.length){
                        benckmarkRule.add(new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]));
                    }
                    else{
                        benckmarkRule.set(2*subpop + treeID, new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]));
                    }
                }
            }
        }

        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now
            //2020.2.12 this is for generating the decision situations for calculating the pc of individuals

            //update the decision situation here. If you do not want to update the decision situation, do not use it.
            String filePath = state.parameters.getString(new Parameter("filePath"), null);
            if (filePath == null) {
                //dynamic simulation
                ClearingEvaluator.phenoCharacterisation[0] =
                        SequencingPhenoCharacterisation.defaultPhenoCharacterisation();
                ClearingEvaluator.phenoCharacterisation[1] =
                        RoutingPhenoCharacterisation.defaultPhenoCharacterisation();
            } else {
                //static simulation
                ClearingEvaluator.phenoCharacterisation[0] =
                        SequencingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
                ClearingEvaluator.phenoCharacterisation[1] =
                        RoutingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
            }

            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
                PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
          /*  if(treeID ==1){
                phenoCharacterisation.setReferenceRule(GPRule.readFromLispExpression(ruleType, "(+ WIQ MWT)"));  build a new GP Rule
            }*/

          //set the reference rules as the best rules in each subpopulation and each generation, and ensure that the intermmediate population use the same base rules --- original
/*
                if (nonIntermediatePop) {
                    phenoCharacterisation.setReferenceRule(new GPRule(ruleType, ((GPIndividual) inds[0]).trees[treeID]));
                    benckmarkRule[treeID] = new GPRule(ruleType, ((GPIndividual) inds[0]).trees[treeID]);
                } else {
                    phenoCharacterisation.setReferenceRule(benckmarkRule[treeID]);
                }
*/

            //2020.2.10 set the reference rules as the fixed base rules---to make sure the PC in normal and intermediate, different subpopulation are comparable.
/*                if(treeID == 0){
                    phenoCharacterisation.setReferenceRule(GPRule.readFromLispExpression(ruleType, "(* W PT)"));
                }
                else{
                    phenoCharacterisation.setReferenceRule(GPRule.readFromLispExpression(ruleType, "WIQ"));
                }*/

             //2020.2.10 when calculating the pc of the subpopulation, the best rules in the current subpop will be used as reference rule
             //this is already set in surrogateClearingMultitreeEvaluatorV1.phenoCharacterisation[0] = sequencingPhenoCharacterisation.defaultPhenoCharacterisation(subpop);
/*
                if (nonIntermediatePop) {
                    phenoCharacterisation.setReferenceRule(new GPRule(ruleType, ((GPIndividual) inds[0]).trees[treeID]));//the reference rule for calculating the pc
                    //benckmarkRule[treeID] = new GPRule(ruleType, ((GPIndividual) inds[0]).trees[treeID]);
                    //benckmarkRule.add(new GPRule(ruleType, ((GPIndividual) inds[0]).trees[treeID]));
                } else {
                    //phenoCharacterisation.setReferenceRule(benckmarkRule[treeID]);
                    phenoCharacterisation.setReferenceRule(benckmarkRule.get(2*subpop + treeID));
                }
*/

                //each individuals
                for (int ind = 0; ind < (int) (inds.length); ind++) {
                    int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                            new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                    indsCharLists[treeID][ind] = new double[charList.length];

                    //each PC information---convert int[] to int[][]
                    for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                        indsCharLists[treeID][ind][numFeature] = charList[numFeature];
                    }
                }
            }
            //here, we get the pc of one subpopulation, we need to save it.
            //combine the phenotype of sequencing rule with the phenotype of routing rule
            indsCharListsMultiTreeTemp = new double[indsCharLists[0].length][];
            //indsCharLists[0].length == indsCharLists[1].length
            for (int i = 0; i < indsCharLists[0].length; i++) {
                double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][i], indsCharLists[1][i]);
                indsCharListsMultiTreeTemp[i] = combinePheChar;
            }
            indsCharListsMultiTree[subpop] = indsCharListsMultiTreeTemp;
        }

        return indsCharListsMultiTree;
    }




    public static double[][][] phenotypicPopulationFixedDecisionsIndividuals(final EvolutionState state,
                                                                             ArrayList<Individual> individuals,
                                                                              PhenoCharacterisation[] pc,
                                                                              Boolean nonIntermediatePop) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);

        double[][][] indsCharListsIndividuals = new double[numTrees][(int) (individuals.size())][];
        double[][][] indsCharListsMultiTreeIndividuals = new double[1][][];

        //2020.2.12 save the best rules of each subpops, and use them to generate decision situations later.
        //in this case, the decision situations are used for each subpop are generating with its best rule. It is expected to examine the pc of its individuals better.
        //todo:modified by mengxu 2022.03.22

//        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
//            //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
//            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now

            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
                PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation

                //each individuals
                for (int ind = 0; ind < (int) (individuals.size()); ind++) {
                    int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                            new GPRule(ruleType, ((GPIndividual) individuals.get(ind)).trees[treeID]));
                    indsCharListsIndividuals[treeID][ind] = new double[charList.length];

                    //each PC information---convert int[] to int[][]
                    for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                        indsCharListsIndividuals[treeID][ind][numFeature] = charList[numFeature];
                    }
                }
            }
            //here, we get the pc of one subpopulation, we need to save it.
            //combine the phenotype of sequencing rule with the phenotype of routing rule
            double[][] indsCharListsMultiTreeTempIndividuals = new double[indsCharListsIndividuals[0].length][];
            //indsCharLists[0].length == indsCharLists[1].length
            for (int i = 0; i < indsCharListsIndividuals[0].length; i++) {
                double[] combinePheChar = ArrayUtils.addAll(indsCharListsIndividuals[0][i], indsCharListsIndividuals[1][i]);
                indsCharListsMultiTreeTempIndividuals[i] = combinePheChar;
            }
            indsCharListsMultiTreeIndividuals[0] = indsCharListsMultiTreeTempIndividuals;
//        }

        return indsCharListsMultiTreeIndividuals;
    }

    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
    public static double[][][] decisionsPopulationFixedDecisions(final EvolutionState state,
                                                                  PhenoCharacterisation[] pc,
                                                                  Boolean nonIntermediatePop) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);

        indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][];
        indsCharListsMultiTree = new double[state.population.subpops.length][][];


        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now

            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
                PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation

                //each individuals
                for (int ind = 0; ind < (int) (inds.length); ind++) {
                    int[] charList = phenoCharacterisation.decision(  //characterise: calculate the distance
                            new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                    indsCharLists[treeID][ind] = new double[charList.length];

                    //each PC information---convert int[] to int[][]
                    for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                        indsCharLists[treeID][ind][numFeature] = charList[numFeature];
                    }
                }
            }
            //here, we get the pc of one subpopulation, we need to save it.
            //combine the phenotype of sequencing rule with the phenotype of routing rule
            indsCharListsMultiTreeTemp = new double[indsCharLists[0].length][];
            //indsCharLists[0].length == indsCharLists[1].length
            for (int i = 0; i < indsCharLists[0].length; i++) {
                double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][i], indsCharLists[1][i]);
                indsCharListsMultiTreeTemp[i] = combinePheChar;
            }
            indsCharListsMultiTree[subpop] = indsCharListsMultiTreeTemp;
        }

        return indsCharListsMultiTree;
    }

    //2020.09.02 calculate the pc of single individual in each subpopulation, respectively. And save them as KNN surrogate model.
    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
    public static double[] phenotypicIndividualFixedDecisions(Individual individual,
                                                              PhenoCharacterisation[] pc) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        int numTree = ((GPIndividual)individual).trees.length;
        double[][] indsCharTwoTree = new double[numTree][];

        for (int treeID = 0; treeID < numTree; treeID++) {//each population
            RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
            PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation

            //each individuals
            int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                    new GPRule(ruleType, ((GPIndividual) individual).trees[treeID]));

            //each PC information---convert int[] to int[][]
            indsCharTwoTree[treeID] = new double[charList.length];
            for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                indsCharTwoTree[treeID][numFeature] = charList[numFeature];
            }
        }
        double[] combinePheChar = ArrayUtils.addAll(indsCharTwoTree[0], indsCharTwoTree[1]);
        //here, we get the pc of one subpopulation, we need to save it.
        //combine the phenotype of sequencing rule with the phenotype of routing rule

        return combinePheChar;
    }

    //2020.2.18 calculate the pc of the individuals in each subpopulation, respectively. And save them as KNN surrogate model.
    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
    public static double[][][] phenotypicPopulationFixedDecisions(final EvolutionState state,
                                                    PhenoCharacterisation[] pc,
                                                    Boolean nonIntermediatePop) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);

        indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][];
        indsCharListsMultiTree = new double[state.population.subpops.length][][];

        //2020.2.12 save the best rules of each subpops, and use them to generate decision situations later.
        //in this case, the decision situations are used for each subpop are generating with its best rule. It is expected to examine the pc of its individuals better.
        //todo:modified by mengxu 2022.03.22
        if(nonIntermediatePop){
            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
                for (int treeID = 0; treeID < numTrees; treeID++) {
                    if(benckmarkRule.size() < numTrees*state.population.subpops.length){
                        benckmarkRule.add(new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]));
                    }
                    else{
                        benckmarkRule.set(2*subpop + treeID, new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]));
                    }
                }
            }
        }

        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now

            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
                PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation

                //this is more general because it handles both the sequencing rules and routing rules.
                if(nonIntermediatePop) {
                    phenoCharacterisation.setReferenceRule(benckmarkRule.get(numTrees * subpop + treeID)); //do use this if want to use SPT, WIQ as the reference rule
                }//todo:modified by mengxu 2022.03.22

                //each individuals
                for (int ind = 0; ind < (int) (inds.length); ind++) {
                    int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                            new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                    indsCharLists[treeID][ind] = new double[charList.length];

                    //each PC information---convert int[] to int[][]
                    for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                        indsCharLists[treeID][ind][numFeature] = charList[numFeature];
                    }
                }
            }
            //here, we get the pc of one subpopulation, we need to save it.
            //combine the phenotype of sequencing rule with the phenotype of routing rule
            indsCharListsMultiTreeTemp = new double[indsCharLists[0].length][];
            //indsCharLists[0].length == indsCharLists[1].length
            for (int i = 0; i < indsCharLists[0].length; i++) {
                double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][i], indsCharLists[1][i]);
                indsCharListsMultiTreeTemp[i] = combinePheChar;
            }
            indsCharListsMultiTree[subpop] = indsCharListsMultiTreeTemp;
        }

        return indsCharListsMultiTree;
    }

    //2020.2.18 calculate the pc of the individuals in each subpopulation, respectively. And save them as KNN surrogate model.
    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
    public static double[][][][] phenotypicPopulationFixedDecisionsAllCases(final EvolutionState state,
                                                                             List<PhenoCharacterisation> sequencingPhenoCharacterisation,
                                                                             List<PhenoCharacterisation> routingPhenoCharacterisation,
                                                                             Boolean nonIntermediatePop) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);

        int numCase = ((OneInstanceMultiCaseMultiObjectiveFitness)state.population.subpops[0].individuals[0].fitness).multiInstanceMultiObjectiveFitness.length;
        double[][][][] indsCharListsAllCases = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][numCase][];
        double[][][][] indsCharListsMultiTreeAllCases = new double[state.population.subpops.length][][][];

//        indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][];
//        indsCharListsMultiTree = new double[state.population.subpops.length][][];

        //2020.2.12 save the best rules of each subpops, and use them to generate decision situations later.
        //in this case, the decision situations are used for each subpop are generating with its best rule. It is expected to examine the pc of its individuals better.
        //todo:modified by mengxu 2022.03.22
        if(nonIntermediatePop){
            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
                for (int treeID = 0; treeID < numTrees; treeID++) {
                    if(benckmarkRule.size() < numTrees*state.population.subpops.length){
                        benckmarkRule.add(new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]));
                    }
                    else{
                        benckmarkRule.set(2*subpop + treeID, new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]));
                    }
                }
            }
        }

        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now

            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING

                if(treeID==0){
                    for(int c=0; c<sequencingPhenoCharacterisation.size(); c++){
                        PhenoCharacterisation phenoCharacterisation = sequencingPhenoCharacterisation.get(c);
                        //this is more general because it handles both the sequencing rules and routing rules.
                        if(nonIntermediatePop) {
                            phenoCharacterisation.setReferenceRule(benckmarkRule.get(numTrees * subpop + treeID)); //do use this if want to use SPT, WIQ as the reference rule
                        }//todo: need double-check by mengxu 2024.4.21

                        //each individuals
                        for (int ind = 0; ind < (int) (inds.length); ind++) {
                            int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                                    new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                            indsCharListsAllCases[treeID][ind][c] = new double[charList.length];

                            //each PC information---convert int[] to int[][]
                            for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                                indsCharListsAllCases[treeID][ind][c][numFeature] = charList[numFeature];
                            }
                        }
                    }
                }
                else{
                    for(int c=0; c<routingPhenoCharacterisation.size(); c++){
                        PhenoCharacterisation phenoCharacterisation = routingPhenoCharacterisation.get(c);
                        //this is more general because it handles both the sequencing rules and routing rules.
                        if(nonIntermediatePop) {
                            phenoCharacterisation.setReferenceRule(benckmarkRule.get(numTrees * subpop + treeID)); //do use this if want to use SPT, WIQ as the reference rule
                        }//todo: need double-check by mengxu 2024.4.21

                        //each individuals
                        for (int ind = 0; ind < (int) (inds.length); ind++) {
                            int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                                    new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                            indsCharListsAllCases[treeID][ind][c] = new double[charList.length];

                            //each PC information---convert int[] to int[][]
                            for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                                indsCharListsAllCases[treeID][ind][c][numFeature] = charList[numFeature];
                            }
                        }
                    }
                }
            }
            //here, we get the pc of one subpopulation, we need to save it.
            //combine the phenotype of sequencing rule with the phenotype of routing rule
            double[][][] indsCharListsMultiTreeTempAllCase = new double[indsCharListsAllCases[0].length][numCase][];
            //indsCharLists[0].length == indsCharLists[1].length
            for(int i = 0; i < indsCharListsAllCases[0].length; i++) {
                for(int j=0; j<indsCharListsAllCases[0][0].length; j++){
                    double[] combinePheChar = ArrayUtils.addAll(indsCharListsAllCases[0][i][j], indsCharListsAllCases[1][i][j]);
                    indsCharListsMultiTreeTempAllCase[i][j] = combinePheChar;
                }

            }
            indsCharListsMultiTreeAllCases[subpop] = indsCharListsMultiTreeTempAllCase;
        }

        return indsCharListsMultiTreeAllCases;
    }


    //modified by mengxu 2022.01.14
    //2020.2.18 calculate the pc of the individuals in each subpopulation, respectively. And save them as KNN surrogate model.
    //use the fixed rule to generate the decision situations, and use the bests rules to calculate pc.
    public static int[][][][] phenotypicPopulationFixedDecisionsPermutation(final EvolutionState state,
                                                                  PhenoCharacterisation[] pc,
                                                                  Boolean nonIntermediatePop) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);

        indsCharListsPermutation = new int[numTrees][(int) (state.population.subpops[0].individuals.length)][][];
        indsCharListsPermutationMultiTree = new int[state.population.subpops.length][][][];

        //2020.2.12 save the best rules of each subpops, and use them to generate decision situations later.
        //in this case, the decision situations are used for each subpop are generating with its best rule. It is expected to examine the pc of its individuals better.
/*        if(nonIntermediatePop){
            for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
                for (int treeID = 0; treeID < numTrees; treeID++) {
                    if(benckmarkRule.size() < numTrees*state.population.subpops.length){
                        benckmarkRule.add(new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]));
                    }
                    else{
                        benckmarkRule.set(2*subpop + treeID, new GPRule(ruleTypes[treeID], ((GPIndividual) state.population.subpops[subpop].individuals[0]).trees[treeID]));
                    }
                }
            }
        }*/

        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            //this is the baseline PhenoCharacterisation with baseline rule "SPT" "WIQ", it will be set again by the best rule, so it is useful here
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now

            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
                PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation

                //this is more general because it handles both the sequencing rules and routing rules.
                //phenoCharacterisation.setReferenceRule(benckmarkRule.get(numTrees * subpop + treeID)); //do use this if want to use SPT, WIQ as the reference rule

                //each individuals
                for (int ind = 0; ind < (int) (inds.length); ind++) {
                    int[][] charList = phenoCharacterisation.characterisePermutation(  //characterise: calculate the distance
                            new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                    indsCharListsPermutation[treeID][ind] = new int[charList.length][charList[0].length];

                    //each PC information---convert int[] to int[][]
                    for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                        indsCharListsPermutation[treeID][ind][numFeature] = charList[numFeature];
                    }
                }
            }
            //here, we get the pc of one subpopulation, we need to save it.
            //combine the phenotype of sequencing rule with the phenotype of routing rule
            indsCharListsPermutationMultiTreeTemp = new int[indsCharListsPermutation[0].length][indsCharListsPermutation[0][0].length][];
            //indsCharLists[0].length == indsCharLists[1].length
            for (int i = 0; i < indsCharListsPermutation[0].length; i++) {
                int[][] combinePheChar = ArrayUtils.addAll(indsCharListsPermutation[0][i], indsCharListsPermutation[1][i]);
                indsCharListsPermutationMultiTreeTemp[i] = combinePheChar;
            }
            indsCharListsPermutationMultiTree[subpop] = indsCharListsPermutationMultiTreeTemp;
        }

        return indsCharListsPermutationMultiTree;
    }

    //2020.2.10 calculate the pc of the individuals in the intermediate population
/*    public static double[][] phenotypicIndividual(final EvolutionState state,
                                                  int subpop,
                                                  PhenoCharacterisation[] pc,
                                                  ArrayList<Individual> individuals) {*/

    public static double[][] phenotypicIndividual(final EvolutionState state,
                                                  PhenoCharacterisation[] pc,
                                                  ArrayList<Individual> individuals) {
        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);
        //numRep = state.parameters.getIntWithDefault(new Parameter(P_REPLICATIONS), null, 1);
        //indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length * numRep)][];
        indsCharLists = new double[numTrees][(int) (individuals.size())][];
        Individual[] inds = individuals.toArray(new Individual[individuals.size()]);
            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
                ClearingEvaluator.phenoCharacterisation[0] =
                        SequencingPhenoCharacterisation.defaultPhenoCharacterisation();
                ClearingEvaluator.phenoCharacterisation[1] =
                        RoutingPhenoCharacterisation.defaultPhenoCharacterisation();

                PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
                //2020.2.10 when calculating the pc of the subpopulation, the best rules in the current subpop will be used as reference rules
                //phenoCharacterisation.setReferenceRule(benckmarkRule.get(2*subpop + treeID));

                //each individuals
                for (int ind = 0; ind < inds.length; ind++) {
                    int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                            new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                    indsCharLists[treeID][ind] = new double[charList.length];

                    //each PC information---convert int[] to int[][]
                    for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                        indsCharLists[treeID][ind][numFeature] = charList[numFeature];
                    }
                }
            }
            //here, we get the pc of one subpopulation, we need to save it.
            //combine the phenotype of sequencing rule with the phenotype of routing rule
            indsCharListsMultiTreeTemp = new double[indsCharLists[0].length][];
            //indsCharLists[0].length == indsCharLists[1].length
            for (int i = 0; i < indsCharLists[0].length; i++) {
                double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][i], indsCharLists[1][i]);
                indsCharListsMultiTreeTemp[i] = combinePheChar;
            }

        return indsCharListsMultiTreeTemp;
    }

    public static double[][] phenotypicIndividual(final EvolutionState state,
                                                  List<Individual> individuals) {
        //todo: need double check. seems wrong here, by mengxu 2024.3.27
//        Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);
        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);
        //numRep = state.parameters.getIntWithDefault(new Parameter(P_REPLICATIONS), null, 1);
        //indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length * numRep)][];
        double[][][] indsCharLists = new double[numTrees][(int) (individuals.size())][];
        Individual[] inds = individuals.toArray(new Individual[individuals.size()]);

        PhenoCharacterisation[] currentPhenoCharacterisation = null;
        if(state instanceof GPRuleEvolutionStateDPSNSBR){
            currentPhenoCharacterisation = ((GPRuleEvolutionStateDPSNSBR)state).phenoCharacterisation;
        }
        else if(state instanceof GPRuleEvolutionStateBase){
            currentPhenoCharacterisation = ((GPRuleEvolutionStateBase)state).phenoCharacterisation;
        }
        else{
            currentPhenoCharacterisation = ((GPRuleEvolutionStateDPSNSBROffspringSelectionStrategy)state).phenoCharacterisation;
        }


        for (int treeID = 0; treeID < numTrees; treeID++) {//each population
            RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING

            PhenoCharacterisation phenoCharacterisation = currentPhenoCharacterisation[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
            //2020.2.10 when calculating the pc of the subpopulation, the best rules in the current subpop will be used as reference rules
            //phenoCharacterisation.setReferenceRule(benckmarkRule.get(2*subpop + treeID));

            //each individuals
            for (int ind = 0; ind < inds.length; ind++) {
                int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                        new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                indsCharLists[treeID][ind] = new double[charList.length];

                //each PC information---convert int[] to int[][]
                for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                    indsCharLists[treeID][ind][numFeature] = charList[numFeature];
                }
            }
        }
        //here, we get the pc of one subpopulation, we need to save it.
        //combine the phenotype of sequencing rule with the phenotype of routing rule
        double[][] indsCharListsMultiTreeTemp = new double[indsCharLists[0].length][];
        //indsCharLists[0].length == indsCharLists[1].length
        for (int i = 0; i < indsCharLists[0].length; i++) {
            double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][i], indsCharLists[1][i]);
            indsCharListsMultiTreeTemp[i] = combinePheChar;
        }

        return indsCharListsMultiTreeTemp;
    }

    public static double[][][] phenotypicIndividualAllCases(final EvolutionState state,
                                                  List<Individual> individuals,
                                                  int numCases) {
        //todo: need double check. seems wrong here, by mengxu 2024.3.27
//        Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);
        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);
        //numRep = state.parameters.getIntWithDefault(new Parameter(P_REPLICATIONS), null, 1);
        //indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length * numRep)][];
        double[][][][] indsCharLists = new double[numTrees][(int) (individuals.size())][numCases][];
        Individual[] inds = individuals.toArray(new Individual[individuals.size()]);

        List<PhenoCharacterisation> currentSequencingPhenoCharacterisation = ((GPRuleEvolutionStateDPSNSBRKNNCase)state).sequencingPhenoCharacterisation;
        List<PhenoCharacterisation> currentRoutingPhenoCharacterisation = ((GPRuleEvolutionStateDPSNSBRKNNCase)state).routingPhenoCharacterisation;


        for (int treeID = 0; treeID < numTrees; treeID++) {//each population
            RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING

            if(treeID==0){
                for(int i=0;i<numCases; i++){
                    PhenoCharacterisation phenoCharacterisation = currentSequencingPhenoCharacterisation.get(i);//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
                    //2020.2.10 when calculating the pc of the subpopulation, the best rules in the current subpop will be used as reference rules
                    //phenoCharacterisation.setReferenceRule(benckmarkRule.get(2*subpop + treeID));
                    //each individuals
                    for (int ind = 0; ind < inds.length; ind++) {
                        int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                                new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                        indsCharLists[treeID][ind][i] = new double[charList.length];

                        //each PC information---convert int[] to int[][]
                        for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                            indsCharLists[treeID][ind][i][numFeature] = charList[numFeature];
                        }
                    }
                }
            }
            else{
                for(int i=0;i<numCases; i++){
                    PhenoCharacterisation phenoCharacterisation = currentRoutingPhenoCharacterisation.get(i);//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
                    //2020.2.10 when calculating the pc of the subpopulation, the best rules in the current subpop will be used as reference rules
                    //phenoCharacterisation.setReferenceRule(benckmarkRule.get(2*subpop + treeID));
                    //each individuals
                    for (int ind = 0; ind < inds.length; ind++) {
                        int[] charList = phenoCharacterisation.characterise(  //characterise: calculate the distance
                                new GPRule(ruleType, ((GPIndividual) inds[ind]).trees[treeID]));
                        indsCharLists[treeID][ind][i] = new double[charList.length];

                        //each PC information---convert int[] to int[][]
                        for (int numFeature = 0; numFeature < charList.length; numFeature++) {
                            indsCharLists[treeID][ind][i][numFeature] = charList[numFeature];
                        }
                    }
                }
            }
        }
        //here, we get the pc of one subpopulation, we need to save it.
        //combine the phenotype of sequencing rule with the phenotype of routing rule
        double[][][] indsCharListsMultiTreeTemp = new double[indsCharLists[0].length][numCases][];
        //indsCharLists[0].length == indsCharLists[1].length
        for (int i = 0; i < indsCharLists[0].length; i++) {
            for(int c=0; c<numCases; c++){
                double[] combinePheChar = ArrayUtils.addAll(indsCharLists[0][i][c], indsCharLists[1][i][c]);
                indsCharListsMultiTreeTemp[i][c] = combinePheChar;
            }
        }

        return indsCharListsMultiTreeTemp;
    }

}
