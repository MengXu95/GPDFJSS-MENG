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
import java.util.List;
import java.util.Map;

/**
 * Author: MengXu 2021.04.14
 * Measure the phenotype diversity of population.
 * It has been verified that this algorithm may be correct. Be careful!!!
 */

public class ParentIndexDiversity {
//    private final double error = 0.0000000001;

    public ParentIndexDiversity(){

    }

    public int parentIndexDiversity(List<Integer> parentIndex){
        Map<Integer, Integer> map = new HashMap<>();
        check(parentIndex, map);
        return map.size();
    }

    public void check(List<Integer> parentIndex, Map<Integer, Integer> map){
        for(int i=0; i< parentIndex.size(); i++){
            int index = parentIndex.get(i);
            int flag = 0;
            for(int j=0; j<i; j++){
                int indexOld = parentIndex.get(j);
                if(index == indexOld){
                    flag = 1;
                    break;
                }
            }
            if(flag == 0){
                int count = 1;
                for(int j=i+1; j<parentIndex.size(); j++){
                    int indexOld = parentIndex.get(j);
                    if(index == indexOld){
                        count++;
                    }
                }
                map.put(index, count);
            }
        }
    }

    public static void main(String[] args){//test if this is right

//        System.out.println(phenotypeDiversity(individuals));
    }
}
