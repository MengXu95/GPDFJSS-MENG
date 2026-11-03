package mengxu.algorithm.MAPElites.GridMapElites.LiteratureComparisonGPQD;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class EConfigurationChecks {
    private EConfigurationChecks() { }
    public static void main(String[] args) throws IOException {
        if (args.length != 1) { throw new IllegalArgumentException("Provide e-study.properties"); }
        Path file = Path.of(args[0]).toRealPath();
        EConfiguration normal = EConfiguration.parse(new String[]{"--config", file.toString()});
        require(normal.requestedCases().size() == 360 && normal.nominalEvaluations() == 100500, "default 360 / 100500");
        require(normal.validationCandidates == 200 && normal.validationInstances == 5 && normal.testInstances == 50,
                "user-approved subset validation/test budget");
        EConfiguration controls = EConfiguration.parse(new String[]{"--config", file.toString(),
                "--set", "methods=GP,QDGP"});
        EConfiguration all = EConfiguration.parse(new String[]{"--config", file.toString(),
                "--set", "methods=LEXICASE,GP,QDGP"});
        EConfiguration fixed = EConfiguration.parse(new String[]{"--config", file.toString(), "--set", "regimes=fixed"});
        require(controls.requestedCases().size() == 720 && all.requestedCases().size() == 1080
                && fixed.requestedCases().size() == 180, "optional matrix sizes");
        require(normal.trainingProperties().equals(controls.trainingProperties())
                && normal.trainingProperties().equals(fixed.trainingProperties()), "dispatch filters do not alter science");
        require(normal.evaluationProperties().equals(all.evaluationProperties()), "controls append without invalidating evaluation family");
        Set<String> keys = new HashSet<>();
        for (EConfiguration.Case item : all.requestedCases()) {
            require(keys.add(item.key()), "duplicate case key");
            require(all.gpSeed(item) >= 0 && all.gpSeed(item) <= Integer.MAX_VALUE, "ECJ GP seed");
            require(all.trainingSeed(item) + 200 * all.seedStep < all.validationSeed(item.scenario), "training/validation separation");
            require(all.validationSeed(item.scenario) + 4 < all.testSeed(item.scenario), "validation/test separation");
            require(item.kind().equals(item.method == EConfiguration.Method.QDGP ? "GRID_ARCHIVE" : "POPULATION"),
                    "native output kind");
        }
        EConfiguration.Method[] methods = EConfiguration.Method.values();
        for (EConfiguration.Scenario scenario : EConfiguration.Scenario.values()) {
            EConfiguration.Case first = new EConfiguration.Case(methods[0], EConfiguration.Regime.fixed, scenario, 0);
            for (EConfiguration.Method method : methods) for (EConfiguration.Regime regime : EConfiguration.Regime.values()) {
                EConfiguration.Case other = new EConfiguration.Case(method, regime, scenario, 0);
                require(all.gpSeed(first) == all.gpSeed(other) && all.trainingSeed(first) == all.trainingSeed(other),
                        "paired initial streams");
            }
            System.out.println(scenario.id + ": Lexicase fixed 30 + rotating 30; objective="
                    + scenario.objective.getName() + ", utilisation=" + scenario.utilisation);
        }
        reject(file, "scenario-seed-stride=1000");
        reject(file, "validation-seed-base=100000000");
        reject(file, "cases=24");
        reject(file, "methods=LEXICASE,LEXICASE");
        System.out.println("E CONFIGURATION CHECKS PASSED: 360 defaults / 720 controls / 1080 all,"
                + " distinct scenario blocks, identical paired streams and no output/simulations");
    }
    private static void reject(Path file, String override) throws IOException {
        boolean failed = false;
        try { EConfiguration.parse(new String[]{"--config", file.toString(), "--set", override}); }
        catch (IllegalArgumentException expected) { failed = true; }
        require(failed, "invalid config accepted: " + override);
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
