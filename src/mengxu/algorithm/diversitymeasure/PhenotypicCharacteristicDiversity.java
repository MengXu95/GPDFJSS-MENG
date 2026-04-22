package mengxu.algorithm.diversitymeasure;

import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.gp.GPTree;
import yimei.jss.gp.GPTreeComparator;
import yimei.jss.gp.function.Div;
import yimei.jss.gp.function.Mul;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: MengXu 2021.04.14
 * Measure the PhenotypicCharacteristic(PC) diversity of population.
 * It has been verified that this algorithm is correct.
 */

public class PhenotypicCharacteristicDiversity {
    public PhenotypicCharacteristicDiversity(){

    }

    public int phenotypicCharacteristicDiversity(double[][] subpopIndsCharListsMultiTree){
        Map<double[], Integer> map = new HashMap<>();
        check(subpopIndsCharListsMultiTree, map);
        return map.size();
    }

    public int phenotypicCharacteristicDiversity(List<double[]> subpopIndsCharListsMultiTree){
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

    public void check(List<double[]> subpopIndsCharListsMultiTree, Map<double[], Integer> map){
        for(int i=0; i< subpopIndsCharListsMultiTree.size(); i++){
            double[] indPC = subpopIndsCharListsMultiTree.get(i);
            int flag = 0;
            for(int j=0; j<i; j++){
                double[] indPCInpop = subpopIndsCharListsMultiTree.get(j);
                if(pcEqual(indPC,indPCInpop)){
                    flag = 1;
                    break;
                }
            }
            if(flag == 0){
                int count = 1;
                for(int j=i+1; j<subpopIndsCharListsMultiTree.size(); j++){
                    double[] indPCInpop = subpopIndsCharListsMultiTree.get(j);
                    if(pcEqual(indPC,indPCInpop)){
                        count++;
                    }
                }
                map.put(indPC, count);
            }
        }
    }

    public boolean pcEqual(double[] indPC, double[] indPCInpop){
        for(int i=0;i<indPC.length;i++){
            if(indPC[i] >= indPCInpop[i] && indPC[i] <= indPCInpop[i]){
//                System.out.println("equal: " + indPC[i] + ", " + indPCInpop[i]);
                continue;
            }
            else{
//                System.out.println("not equal: " + indPC[i] + ", " + indPCInpop[i]);
                return false;
            }
        }
        return true;
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
