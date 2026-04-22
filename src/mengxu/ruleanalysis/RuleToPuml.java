package mengxu.ruleanalysis;

import ec.gp.GPTree;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

public class RuleToPuml {

    public static void main(String[] args) {
        // node [shape=rectangle, fontsize=20];
        // n10[label = "Ptwo", fillcolor="lightgreen", style="filled"];
//        GPRule sequencingRule = GPRule.readFromLispExpression(RuleType.SEQUENCING,"(Max (* (- (- (* WKR PT) (/ (- (Max NOR NIQ) (Min W TIS)) (* (- SL OWT) (Max OWT UTIL)))) (Min SL (- (- (* WKR PT) W) (/ (* WKR PT) (* WKR PT))))) PT) (Min (* (Min (Min (Max SL PT) (Max rDD TIS)) (Max rDD TIS)) (Min (+ (Min WIQ WIQ) (/ NIQ W)) OWT)) (Max (Min MWT (/ NPT TIS)) (- (* (- (Max W W) (- OWT OWT)) PT) W))))");
//        GPTree tree = sequencingRule.getGPTree();
        GPRule routingRule = GPRule.readFromLispExpression(RuleType.ROUTING,"(+ (+ (Min (+ (Max NIQ PT) (Max MWT UTIL)) (- (- (Max NIQ NIQ) (* NOR OWT)) TIS)) (+ (+ (Max NIQ PT) (Max MWT UTIL)) WIQ)) (+ (+ (+ (- TRANT MWT) (* NOR WKR)) (+ (* UTIL WIQ) (Min (- TRANT OWT) NIQ))) (- (* NOR (Max NIQ NIQ)) rDD)))");
        GPTree tree = routingRule.getGPTree();
//        String expression = LispSimplifier.simplifyExpression(expression);
//        GPTree tree = LispParser.parseJobShopRule(expression);

        System.out.println("@startuml");
        System.out.print(tree.child.makeGraphvizTree());
        System.out.println("@enduml");

    }
}
