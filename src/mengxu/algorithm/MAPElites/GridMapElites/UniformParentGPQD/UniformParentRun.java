package mengxu.algorithm.MAPElites.GridMapElites.UniformParentGPQD;

import ec.EvolutionState;
import ec.Evolve;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class UniformParentRun {
    private UniformParentRun() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("UniformParentRun --params <preset.params> --output-root <NEW directory> [--set key=value ...]");
            return;
        }
        Path config = null;
        Path output = null;
        List<String> overrides = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (i + 1 == args.length) { throw new IllegalArgumentException("Missing option value"); }
            String option = args[i++];
            String value = args[i];
            if (option.equals("--params") && config == null) { config = Paths.get(value).toRealPath(); }
            else if (option.equals("--output-root") && output == null) { output = Paths.get(value); }
            else if (option.equals("--set")) { overrides.add(value); }
            else { throw new IllegalArgumentException("Unknown or duplicate option: " + option); }
        }
        if (config == null || output == null) { throw new IllegalArgumentException("Parameter file and output are required"); }
        Path protectedRoot = config.getParent();
        for (Path parent = protectedRoot; parent != null; parent = parent.getParent()) {
            if (parent.getFileName() != null && parent.getFileName().toString().equalsIgnoreCase("src")) {
                protectedRoot = parent;
                break;
            }
        }
        output = UniformFiles.checkedOutput(output, protectedRoot);
        ParameterDatabase parameters = new ParameterDatabase(config.toFile());
        for (String override : overrides) {
            int equals = override.indexOf('=');
            if (equals < 1 || equals == override.length() - 1) { throw new IllegalArgumentException("Use --set key=value"); }
            String key = override.substring(0, equals);
            if (key.equals("uniform.output-root")) { throw new IllegalArgumentException("Use --output-root"); }
            parameters.set(new Parameter(key), override.substring(equals + 1));
        }
        parameters.set(new Parameter("uniform.output-root"), output.toString());
        if (!UniformParentState.class.getName().equals(parameters.getString(new Parameter("state"), null))) {
            throw new IllegalArgumentException("Use the isolated archive-parent state");
        }
        EvolutionState state = Evolve.initialize(parameters, 0);
        try { state.run(EvolutionState.C_STARTED_FRESH); }
        finally { Evolve.cleanup(state); }
    }
}
