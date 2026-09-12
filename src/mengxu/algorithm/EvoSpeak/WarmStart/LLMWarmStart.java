package mengxu.algorithm.LLM.WarmStart;

import ec.EvolutionState;
import ec.Individual;
import ec.Initializer;
import ec.gp.GPIndividual;
import ec.util.Parameter;
import yimei.jss.rule.operation.evolved.GPRule;

import java.util.ArrayList;

public class LLMWarmStart {

    private String textLLM;

    public LLMWarmStart(){

    }

    public ArrayList<Individual> getWarmStartIndividuals(EvolutionState state){
        ArrayList<Individual> individuals = new ArrayList<>();
//        Parameter parameter = state.parameters;

        // Use a scanner to read the string line by line
        java.util.Scanner scanner = new java.util.Scanner(this.textLLM);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim(); // Read the current line and trim whitespace

//            if (line.startsWith("Tree 0:")) {
//                System.out.println("Read Tree 0 as the sequencing rule!");
//                line = scanner.nextLine().trim();
//                // sequencing rule
//                GPRule sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
//            } else if (line.startsWith("Tree 1:")) {
//                System.out.println("Read Tree 1 as the routing rule!");
//                line = scanner.nextLine().trim();
//                // routing rule
//                GPRule routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);
//                GPIndividual individual = new GPIndividual();
//                individual.setup(state, );
//                individual.readIndividual();
//            }

            // Print the line for clarity (optional)
            System.out.println(line);
        }

//        while (scanner.hasNextLine()) {
//            String line = scanner.nextLine();
//            // sequencing rule
//            GPRule sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
//            GPRule routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);
//            System.out.println(line);
//        }

        scanner.close();

        return individuals;

    }

    public void getTextLLM(){
        this.textLLM = "<START> \n" +
                "Scheduling Heuristic 7\n" +
                "Tree 0: \n" +
                "- (- (- (- (* NIQ PT) (Max TRANT WKR)) (Max (Max (* (/ W PT) (- TRANT TRANT)) WKR) (/ TIS TRANT))) (- TIS WIQ)) \n" +
                "Tree 1:\n" +
                "(Min (+ TRANT (+ WIQ TRANT)) (+ WIQ (Max (+ WIQ (* OWT WKR)) (* NIQ (Min PT (+ (* TRANT NIQ) (+ WIQ TRANT)))))))\n" +
                "Scheduling Heuristic 8\n" +
                "Tree 0:\n" +
                "(- (- (- WIQ (Max NIQ OWT)) (+ NIQ (+ (Max (+ NIQ (Max NIQ OWT)) OWT) (Min (+ MWT WKR) (Min (+ MWT WKR) (Max PT TIS)))))) (Max (Max (- TIS WIQ) WKR) (- (Max (Max (- TIS WIQ) WKR) (- TIS WIQ)) (+ NIQ (Max NIQ OWT)))))\n" +
                "Tree 1:\n" +
                "(+ (+ PT (+ (- (Max (- PT W) TRANT) (- NOR (Min NOR TIS))) (+ TRANT WIQ))) NPT)\n" +
                "<END> \n";
    }
}
