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
import java.util.Map;

/**
 * Author: MengXu 2021.04.14
 * Measure the edit distance diversity 2 of population.
 * It has been verified that this algorithm may be correct. Be careful!!!
 */

public class EditDistanceDiversityV2 {

    public EditDistanceDiversityV2(){

    }

    public double editDistanceDiversityV2(Individual[] individuals, Individual bestInd){
        Map<Individual, Integer> map = new HashMap<>();
        double maxDis = 0;
        for(int i=0; i< individuals.length; i++){
            Individual ind = individuals[i];
            double index = dis(ind, bestInd);
            if(index > maxDis){
                maxDis = index;
            }
        }

        return maxDis;
    }

    public double dis(Individual ind, Individual bestInd){
        GPTree[] indTrees = ((GPIndividual)ind).trees;
        GPTree indTree0 = indTrees[0];
        GPTree indTree1 = indTrees[1];

        GPTree[] bestIndTrees = ((GPIndividual)bestInd).trees;
        GPTree bestIndTree0 = bestIndTrees[0];
        GPTree bestIndTree1 = bestIndTrees[1];

        double disTree0 = equals(indTree0.child, bestIndTree0.child)[0] + equals(indTree0.child, bestIndTree0.child)[1];
        double sizeTree0 = indTree0.child.numNodes(GPNode.NODESEARCH_ALL);
        if(sizeTree0 > bestIndTree0.child.numNodes(GPNode.NODESEARCH_ALL)){
            sizeTree0 = bestIndTree0.child.numNodes(GPNode.NODESEARCH_ALL);
        }
        disTree0 = disTree0 / sizeTree0;

        double disTree1 = equals(indTree1.child, bestIndTree1.child)[0] + equals(indTree1.child, bestIndTree1.child)[1];
        double sizeTree1 = indTree1.child.numNodes(GPNode.NODESEARCH_ALL);
        if(sizeTree1 > bestIndTree1.child.numNodes(GPNode.NODESEARCH_ALL)){
            sizeTree1 = bestIndTree1.child.numNodes(GPNode.NODESEARCH_ALL);
        }
        disTree1 = disTree1 / sizeTree1;
        return disTree0 + disTree1;
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

    public int[] equals(GPNode o1, GPNode o2) {
        int[] equalAndDis = new int[2];
        int dis = 0;
        if (o1.toString().equals(o2.toString())) {
            equalAndDis[0] = 0;
            if (o1.children.length == o2.children.length) {
                if (o1.children.length == 0)
                    return equalAndDis;

                switch (o1.toString()) {
                    case "+":
                        dis += sameChildrenOrdered(o1.children, o2.children);
                        break;
                    case "-":
                        dis += sameChildrenOrdered(o1.children, o2.children);
                        break;
                    case "*":
                        dis += sameChildrenOrdered(o1.children, o2.children);
                        break;
                    case "/":
                        dis += sameChildrenOrdered(o1.children, o2.children);
                        break;
                    case "max":
                        dis += sameChildrenOrdered(o1.children, o2.children);
                        break;
                    case "min":
                        dis += sameChildrenOrdered(o1.children, o2.children);
                        break;
                    case "if":
                        dis += sameChildrenOrdered(o1.children, o2.children);
                        break;
                }
            }
        }
        else{
            equalAndDis[0] = 1;
        }

        equalAndDis[1] = dis;

        return equalAndDis;
    }

    public double sameChildrenOrdered(GPNode[] children1,
                                          GPNode[] children2) {
        double dis = 0;
        double K = 0.5;
        for (int i = 0; i < children1.length; i++) {
            int[] same = equals(children1[i], children2[i]);

            if (same[0]==1)//if non equal
                dis += K * (same[1]+1);
            else
                dis += K * same[1];
        }

        return dis;
    }

    public static void main(String[] args){//test if this is right
        GPTree tree1 = new GPTree();
        GPNode node1 = new Mul();
        node1.children = new GPNode[2];
        node1.children[0] = new Div();
        node1.children[0].children = new GPNode[2];
        node1.children[0].children[0] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
        node1.children[0].children[1] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
        node1.children[1] = new Div();
        node1.children[1].children = new GPNode[2];
        node1.children[1].children[0] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
        node1.children[1].children[1] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
//        node1.children[1] = new AttributeGPNode(JobShopAttribute.PROC_TIME);
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
        ind2.trees[0] = tree2;
        ind2.trees[1] = tree1;

        GPIndividual ind3 = new GPIndividual();
        ind3.trees = new GPTree[2];
        ind3.trees[0] = tree1;
        ind3.trees[1] = tree2;

        Individual[] individuals = new Individual[3];
        individuals[0]=ind1;
        individuals[1]=ind2;
        individuals[2]=ind3;

//        System.out.println(editDistanceDiversityV2(individuals, ind1));
    }
}
