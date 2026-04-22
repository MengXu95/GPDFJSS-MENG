package yimei.jss.niching;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.util.Parameter;
import org.apache.commons.lang3.ArrayUtils;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

/**
2019.9.24 calculate the (phenotypy characteristic) PC of each individuals in each population
 not used in weighted feature idea
 */
public class phenotypicForSurrogate {
    static double[][][] indsCharLists = null; //save the PC information
    static double[][] indsCharListsMultiTreeTemp = null;
    static double[][][] indsCharListsMultiTree = null;
    public static final String P_TREESIZE = "num-trees";
    public static int numTrees;
    static GPRule[] benckmarkRule = new GPRule[2];

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

    //2020.2.8 calculate the pc of the individuals in each subpopulation, respectively. And save them as KNN surrogate model.
    public static double[][][] phenotypicPopulation(final EvolutionState state,
                                                    PhenoCharacterisation[] pc,
                                                    Boolean nonIntermediatePop) {

        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        numTrees = state.parameters.getIntWithDefault(new Parameter(P_TREESIZE), null, 1);

        indsCharLists = new double[numTrees][(int) (state.population.subpops[0].individuals.length)][];
        indsCharListsMultiTree = new double[state.population.subpops.length][][];


        for (int subpop = 0; subpop < state.population.subpops.length; subpop++) {
            Individual[] inds = state.population.subpops[subpop].individuals; //only one subpop now

            for (int treeID = 0; treeID < numTrees; treeID++) {//each population
                RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
                PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
          /*  if(treeID ==1){
                phenoCharacterisation.setReferenceRule(GPRule.readFromLispExpression(ruleType, "(+ WIQ MWT)"));  build a new GP Rule
            }*/

          //set the reference rules as the best rules in each subpopulation and each generation, and ensure that the intermediate population use the same base rules --- original
                if (nonIntermediatePop) {
                    phenoCharacterisation.setReferenceRule(new GPRule(ruleType, ((GPIndividual) inds[0]).trees[treeID]));
                    benckmarkRule[treeID] = new GPRule(ruleType, ((GPIndividual) inds[0]).trees[treeID]);
                } else {
                    phenoCharacterisation.setReferenceRule(benckmarkRule[treeID]);
                }

            //2020.2.10 set the reference rules as the fixed base rules---to make sure the PC in normal and intermediate, different subpopulation are comparable.
/*                if(treeID == 0){
                    phenoCharacterisation.setReferenceRule(GPRule.readFromLispExpression(ruleType, "(* W PT)"));
                }
                else{
                    phenoCharacterisation.setReferenceRule(GPRule.readFromLispExpression(ruleType, "WIQ"));
                }*/


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
}
