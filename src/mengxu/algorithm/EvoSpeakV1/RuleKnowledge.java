package mengxu.algorithm.EvoSpeakV1;

import ec.gp.GPIndividual;
import ec.gp.GPNode;
import org.json.JSONArray;
import org.json.JSONObject;
import yimei.jss.gp.GPRuleEvolutionState;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RuleKnowledge {
    private static final Map<String, String> TERMINALS = new LinkedHashMap<>();

    static {
        TERMINALS.put("NIQ", "Number of operations in the candidate machine queue: workCenter.getQueue().size().");
        TERMINALS.put("WIQ", "Work in the candidate machine queue: workCenter.getWorkInQueue().");
        TERMINALS.put("MWT", "Current time minus machine ready time; may be negative while the machine is busy.");
        TERMINALS.put("PT", "Processing time of this operation option on this candidate machine: op.getProcTime().");
        TERMINALS.put("NPT", "Next-operation processing-time metadata stored on this option: op.getNextProcTime(); not a new look-ahead simulation.");
        TERMINALS.put("OWT", "Current time minus this operation option's ready time: its waiting time.");
        TERMINALS.put("WKR", "Remaining-work metadata of the job at this operation option: op.getWorkRemaining().");
        TERMINALS.put("NOR", "Remaining-operation-count metadata: op.getNumOpsRemaining().");
        TERMINALS.put("W", "Job importance weight, not an objective preference weight: op.getJob().getWeight().");
        TERMINALS.put("TIS", "Current time minus the job release time: time already spent in the system.");
        TERMINALS.put("rDD", "Job due date minus current time; negative for an overdue job.");
        TERMINALS.put("SL", "Job due date minus current time minus remaining work: slack, which can be negative.");
        TERMINALS.put("TRANT", "Heterogeneous-machine transport time to the candidate machine, including entry or exit transport when applicable; computed by JobShopAttribute.TRANSFER_TIME.");
    }

    private RuleKnowledge() { }

    public static JSONObject grammar(GPRuleEvolutionState state) {
        JSONObject terminals = new JSONObject();
        for (GPNode terminal : state.getTerminals(0)) {
            String name = terminal.toString();
            String meaning = TERMINALS.get(name);
            if (meaning == null) {
                throw new IllegalArgumentException("No verified EvoSpeakV1 glossary entry for terminal " + name
                        + ". Use terminals-from=relative or extend the reviewed glossary.");
            }
            terminals.put(name, meaning);
        }
        return new JSONObject().put("terminals", terminals)
                .put("binaryFunctions", new JSONArray(new String[]{"+", "-", "*", "/", "Min", "Max"}))
                .put("decision", "Both trees minimize priority. Tree 0 selects an operation in one machine queue; tree 1 selects a machine option for one operation.")
                .put("ties", "OperationOption.priorTo breaks equal priority by smaller job ID. Same-job routing ties keep the first encountered option.")
                .put("division", "Div.eval returns 1 when Double.compare(denominator, 0.0) == 0; otherwise it divides. Negative zero is not caught by that test. Overflow/non-finite scores are rejected in validation.")
                .put("context", "At a sequencing decision NIQ/WIQ/MWT can be constant across candidates. An additive constant alone cannot change their order. At routing decisions machine-dependent attributes can vary.")
                .put("syntax", "Case-sensitive Lisp expressions; binary arity only; no numbers, extra symbols, prose, code execution, or preference terminals.");
    }

    public static JSONObject facts(GPRuleEvolutionState state, GPIndividual individual) {
        return new JSONObject().put("semantics", grammar(state)).put("sequencing", treeFacts(individual.trees[0].child))
                .put("routing", treeFacts(individual.trees[1].child))
                .put("sources", new JSONArray(new String[]{"src/yimei/jss/gp/terminal/JobShopAttribute.java",
                        "src/yimei/jss/gp/function/Div.java", "src/yimei/jss/jobshop/OperationOption.java"}));
    }

    private static JSONObject treeFacts(GPNode root) {
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        JSONArray warnings = new JSONArray();
        visit(root, occurrences, warnings);
        JSONObject used = new JSONObject();
        for (Map.Entry<String, Integer> entry : occurrences.entrySet()) {
            used.put(entry.getKey(), new JSONObject().put("occurrences", entry.getValue())
                    .put("definition", TERMINALS.get(entry.getKey())));
        }
        return new JSONObject().put("expression", root.makeLispTree()).put("depth", root.depth())
                .put("nodes", root.numNodes(GPNode.NODESEARCH_ALL)).put("terminalsUsed", used).put("warnings", warnings);
    }

    private static void visit(GPNode node, Map<String, Integer> occurrences, JSONArray warnings) {
        if (node.children.length == 0) {
            occurrences.merge(node.toString(), 1, Integer::sum);
            return;
        }
        if (node.children.length == 2 && node.children[0].makeLispTree().equals(node.children[1].makeLispTree())) {
            if (node.toString().equals("-")) {
                warnings.put("Repeated subtraction subtree can cancel for finite values: " + node.makeLispTree());
            }
            if (node.toString().equals("/")) {
                warnings.put("Identical numerator/denominator often produces a constant; check zero, negative-zero and overflow behavior: " + node.makeLispTree());
            }
        }
        for (GPNode child : node.children) {
            visit(child, occurrences, warnings);
        }
    }
}