package mengxu.algorithm.MAPElites.GridMapElites.LexicaseGPQD;

import ec.EvolutionState;
import ec.Evolve;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class LexicaseQDRun {
    private LexicaseQDRun() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("LexicaseQDRun --params <lexicase-gpf.params|lexicase-gp.params> "
                    + "--output-root <NEW directory> [--set key=value ...]");
            return;
        }
        Path file = null;
        Path output = null;
        List<String> overrides = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (i + 1 == args.length) { throw new IllegalArgumentException("Missing option value: " + args[i]); }
            String key = args[i++];
            String value = args[i];
            if (key.equals("--params") && file == null) { file = Paths.get(value).toRealPath(); }
            else if (key.equals("--output-root") && output == null) { output = Paths.get(value); }
            else if (key.equals("--set")) { overrides.add(value); }
            else { throw new IllegalArgumentException("Unknown or duplicate option: " + key); }
        }
        if (file == null || output == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("A parameter file and new output directory are required");
        }
        Path protectedRoot = file.getParent();
        for (Path parent = protectedRoot; parent != null; parent = parent.getParent()) {
            if (parent.getFileName() != null && parent.getFileName().toString().equalsIgnoreCase("src")) {
                protectedRoot = parent;
                break;
            }
        }
        output = LexicaseFiles.checkedOutput(output, protectedRoot);
        ParameterDatabase parameters = new ParameterDatabase(file.toFile());
        for (String override : overrides) {
            int separator = override.indexOf('=');
            if (separator < 1 || separator == override.length() - 1) {
                throw new IllegalArgumentException("Use --set key=value");
            }
            String name = override.substring(0, separator);
            if (name.equals("lexqd.output-root")) {
                throw new IllegalArgumentException("Use --output-root, not an output-root override");
            }
            parameters.set(new Parameter(name), override.substring(separator + 1));
        }
        parameters.set(new Parameter("lexqd.output-root"), output.toString());
        if (!LexicaseEvolutionState.class.getName().equals(parameters.getString(new Parameter("state"), null))) {
            throw new IllegalArgumentException("Only the isolated Lexicase population state may be launched");
        }
        EvolutionState state = Evolve.initialize(parameters, 0);
        try {
            state.run(EvolutionState.C_STARTED_FRESH);
        } finally { Evolve.cleanup(state); }
    }
}
