package mengxu.algorithm.diversitymeasure;

import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.gp.GPTree;
import yimei.jss.gp.function.Div;
import yimei.jss.gp.function.Mul;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;

import java.util.HashMap;
import java.util.Map;

/**
 * Author: MengXu 2021.04.14
 * Measure the PhenotypicCharacteristic(PC) diversity of population.
 * It has been verified that this algorithm is correct.
 */

public class ParentsCombinationForXcoverDiversity {
    public ParentsCombinationForXcoverDiversity(){

    }

    public int phenotypicCharacteristicDiversity(double[][] subpopIndsCharListsMultiTree){
        Map<double[], Integer> map = new HashMap<>();
        check(subpopIndsCharListsMultiTree, map);
        return map.size();
    }

    public void check(double[][] subpopIndsCharListsMultiTree, Map<double[], Integer> map){
        for(int i=0; i< subpopIndsCharListsMultiTree.length; i++){
            double[] indPC = subpopIndsCharListsMultiTree[i];
            int flag = 0;
            for(int j=0; j<i; j++){
                double[] indPCInpop = subpopIndsCharListsMultiTree[j];
                if(pcEqual(indPC,indPCInpop)){
                    flag = 1;
                    break;
                }
            }
            if(flag == 0){
                int count = 1;
                for(int j=i+1; j<subpopIndsCharListsMultiTree.length; j++){
                    double[] indPCInpop = subpopIndsCharListsMultiTree[j];
                    if(pcEqual(indPC,indPCInpop)){
                        count++;
                    }
                }
                map.put(indPC, count);
            }
        }
    }

    public boolean pcEqual(double[] indPC, double[] indPCInpop){
        if(indPC[0] == indPCInpop[0] && indPC[1] == indPCInpop[1]){
            return true;
        }
        if(indPC[0] == indPCInpop[1] && indPC[1] == indPCInpop[0]){
            return true;
        }

        return false;
    }

    public static void main(String[] args){//test if this is right


//        System.out.println(genotypeDiversity(individuals));
    }
}
