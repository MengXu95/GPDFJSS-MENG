package yimei.jss.gp;

import ec.gp.GPNode;
import ec.gp.GPTree;
import yimei.jss.gp.function.Div;
import yimei.jss.gp.function.Mul;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;

public class GPTreeComparator {

    public static boolean TreeEquals(GPTree t1, GPTree t2){
        GPNode node1 = t1.child;
        GPNode node2 = t2.child;

        return equals(node1, node2);
//        return equals(node1, node2);
    }

    public static boolean equals(GPNode o1, GPNode o2) {
        if (o1.toString().equals(o2.toString())) {
            if (o1.children.length == o2.children.length) {
                if (o1.children.length == 0)
                    return true;

                switch (o1.toString()) {
                    case "+":
                        return sameChildrenUnordered(o1.children, o2.children);
                    case "-":
                        return sameChildrenOrdered(o1.children, o2.children);
                    case "*":
                        return sameChildrenUnordered(o1.children, o2.children);
                    case "/":
                        return sameChildrenOrdered(o1.children, o2.children);
                    case "Max":
                        return sameChildrenUnordered(o1.children, o2.children);
                    case "Min":
                        return sameChildrenUnordered(o1.children, o2.children);
                    case "If":
                        return sameChildrenOrdered(o1.children, o2.children);
                    default:
                        return false;
                }
            }
        }

        return false;
    }

    public static boolean sameChildrenOrdered(GPNode[] children1,
                                              GPNode[] children2) {
        for (int i = 0; i < children1.length; i++) {
            boolean same = equals(children1[i], children2[i]);

            if (!same)
                return false;
        }

        return true;
    }

    public static boolean sameChildrenUnordered(GPNode[] children1,
                                                GPNode[] children2) {
        boolean[] matched = new boolean[children2.length];

        for (int i = 0; i < children1.length; i++) {
            boolean foundSame = false;

            for (int j = 0; j < children2.length; j++) {
                if (matched[j])
                    continue;

                boolean same = equals(children1[i], children2[j]);

                if (same) {
                    foundSame = true;
                    matched[j] = true;
                    break;
                }
            }

            if (!foundSame)
                return false;
        }

        return true;
    }

    //2021.2.4 modified by meng xu.

}
