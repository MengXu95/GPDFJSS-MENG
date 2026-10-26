package mengxu.algorithm.MAPElites.FuzzySystem.revision;

import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyInferenceSystem;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyRule;
import mengxu.algorithm.MAPElites.FuzzySystem.FuzzyVariable;

/** Pure-Java versions of the ten-rule PaperVerificationTest model. */
public final class FuzzyModelFactory {
    private FuzzyModelFactory() {
    }

    /** Historical model, including its [0,200] centroid integration domain. */
    public static FuzzyInferenceSystem createLegacy() {
        return create(200.0);
    }

    /** Revised-paper model: identical terms/rules, but SI domain [0,100]. */
    public static FuzzyInferenceSystem createRevision() {
        return create(100.0);
    }

    private static FuzzyInferenceSystem create(double outputMax) {
        FuzzyInferenceSystem model = new FuzzyInferenceSystem();
        model.addInputVariable(input("Performance", "Low", "Medium", "High"));
        model.addInputVariable(input("Stability", "Unstable", "Moderate", "Stable"));
        model.addInputVariable(input("Interpretability", "Hard", "Moderate", "Easy"));
        FuzzyVariable output = new FuzzyVariable("SelectionIndex", 0.0, outputMax);
        output.addMembershipFunction("Unsuitable", 25.0, 15.0);
        output.addMembershipFunction("Suitable", 50.0, 15.0);
        output.addMembershipFunction("VerySuitable", 75.0, 15.0);
        model.setOutputVariable(output);

        model.addRule(rule("High", "Stable", "Moderate", "VerySuitable"));
        model.addRule(rule("High", "Stable", "Easy", "VerySuitable"));
        model.addRule(rule("Medium", "Stable", "Easy", "VerySuitable"));
        model.addRule(rule("High", "Moderate", null, "Suitable"));
        model.addRule(rule("Medium", "Moderate", "Easy", "Suitable"));
        model.addRule(rule("Medium", "Stable", "Moderate", "Suitable"));
        model.addRule(rule("High", "Unstable", "Easy", "Suitable"));
        model.addRule(rule("Low", "Unstable", null, "Unsuitable"));
        model.addRule(rule("Low", "Moderate", null, "Unsuitable"));
        model.addRule(rule("Medium", "Unstable", "Hard", "Unsuitable"));
        model.validate();
        return model;
    }

    private static FuzzyVariable input(String name, String low, String medium, String high) {
        FuzzyVariable variable = new FuzzyVariable(name, 0.0, 1.0);
        variable.addMembershipFunction(low, 0.2, 0.15);
        variable.addMembershipFunction(medium, 0.5, 0.15);
        variable.addMembershipFunction(high, 0.8, 0.15);
        return variable;
    }

    private static FuzzyRule rule(String performance, String stability,
                                  String interpretability, String consequent) {
        FuzzyRule rule = new FuzzyRule()
                .addAntecedent("Performance", performance)
                .addAntecedent("Stability", stability);
        if (interpretability != null) {
            rule.addAntecedent("Interpretability", interpretability);
        }
        return rule.setConsequent("SelectionIndex", consequent);
    }
}
