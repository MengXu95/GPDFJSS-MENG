package mengxu.algorithm.diversitymeasure;

import ec.Individual;
import ec.Population;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.gp.GPTree;
import yimei.jss.gp.GPTreeComparator;
import yimei.jss.gp.function.Div;
import yimei.jss.gp.function.Mul;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.rule.operation.evolved.GPRule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: MengXu 2021.04.14
 * Measure the genotype diversity of population.
 * It has been verified that this algorithm is correct.
 */

public class GenotypeDiversity {
    public GenotypeDiversity(){

    }

    public int genotypeDiversity(Individual[] individuals){
        Map<Individual, Integer> map = new HashMap<>();
        check(individuals, map);
        return map.size();
    }

    public void check(Individual[] individuals, Map<Individual, Integer> map){
        for(int i=0; i< individuals.length; i++){
            Individual ind = individuals[i];
            GPTree[] indTrees = ((GPIndividual)ind).trees;
            GPTree indTree0 = indTrees[0];
            GPTree indTree1 = indTrees[1];
            int flag = 0;
            for(int j=0; j<i; j++){
                Individual indInpop = individuals[j];
                GPTree[] gpTrees = ((GPIndividual)indInpop).trees;
                GPTree gpTree0 = gpTrees[0];
                GPTree gpTree1 = gpTrees[1];
                if(GPTreeComparator.TreeEquals(indTree0,gpTree0) &&
                        GPTreeComparator.TreeEquals(indTree1,gpTree1)){
                    flag = 1;
                    break;
                }
            }
            if(flag == 0){
                int count = 1;
                for(int j=i+1; j<individuals.length; j++){
                    Individual indInpop = individuals[j];
                    GPTree[] gpTrees = ((GPIndividual)indInpop).trees;
                    GPTree gpTree0 = gpTrees[0];
                    GPTree gpTree1 = gpTrees[1];
                    if(GPTreeComparator.TreeEquals(indTree0,gpTree0) &&
                            GPTreeComparator.TreeEquals(indTree1,gpTree1)){
                        count++;
                    }
                }
                map.put(ind, count);
            }
        }
    }


    public static void main(String[] args){//test if this is right
        GPTree tree1 = new GPTree();
        GPNode node1 = new Mul();
        node1.children = new GPNode[2];
        node1.children[0] = new Div();
        node1.children[0].children = new GPNode[2];
        node1.children[0].children[0] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
        node1.children[0].children[1] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
        node1.children[1] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
        tree1.child = node1;

        GPTree tree2 = new GPTree();
        GPNode node2 = new Mul();
        node2.children = new GPNode[2];
        node2.children[0] = new Mul();
        node2.children[0].children = new GPNode[2];
        node2.children[0].children[0] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
        node2.children[0].children[1] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
        node2.children[1] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
        tree2.child = node2;

        GPIndividual ind1 = new GPIndividual();
        ind1.trees = new GPTree[2];
        ind1.trees[0] = tree1;
        ind1.trees[1] = tree2;

        GPIndividual ind2 = new GPIndividual();
        ind2.trees = new GPTree[2];
        ind2.trees[0] = tree1;
        ind2.trees[1] = tree1;

        GPIndividual ind3 = new GPIndividual();
        ind3.trees = new GPTree[2];
        ind3.trees[0] = tree1;
        ind3.trees[1] = tree2;

        Individual[] individuals = new Individual[3];
        individuals[0]=ind1;
        individuals[1]=ind2;
        individuals[2]=ind3;

//        System.out.println(genotypeDiversity(individuals));
    }
}
