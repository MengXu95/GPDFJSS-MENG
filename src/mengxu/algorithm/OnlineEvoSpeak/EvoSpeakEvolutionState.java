package mengxu.algorithm.OnlineEvoSpeak;

import ec.Individual;
import ec.gp.GPIndividual;
import ec.util.Parameter;
import org.json.JSONArray;
import org.json.JSONObject;
import yimei.jss.gp.GPRuleEvolutionState;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class EvoSpeakEvolutionState extends GPRuleEvolutionState {
    EvoSpeakConfig config;
    LlmClient client;
    Path directory;
    boolean initialPopulationLoaded;
    private JSONObject generationRecord;
    private GPIndividual bestOfGeneration;

    @Override
    public void run(int condition) {
        if (config == null || directory == null || condition != C_STARTED_FRESH) {
            throw new IllegalStateException("Run EvoSpeakMain to configure and validate the warm start before GP.");
        }
        totalTime = 0.0;
        genTimes.clear();
        sumGenTimes.clear();
        initialPopulationLoaded = false;
        try {
            Path initialPopulationFile = directory.resolve("generated-population.txt");
            if (!Files.isRegularFile(initialPopulationFile)) {
                throw new IOException("The validated initial population TXT must exist before GP initialization.");
            }
            output.message("OnlineEvoSpeak stage 2/2: initializing GP from TXT: " + initialPopulationFile);
            startFresh();
            if (population.subpops[0].individuals.length != config.integer("pop.subpop.0.size", 100)) {
                throw new IOException("Initial population TXT count differs from the configured GP population size.");
            }
            for (Individual individual : population.subpops[0].individuals) {
                individual.evaluated = false;
            }
            initialPopulationLoaded = true;
            int result = R_NOTDONE;
            try (BufferedWriter metrics = Files.newBufferedWriter(directory.resolve("generations.jsonl"), StandardCharsets.UTF_8)) {
                while (result == R_NOTDONE) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Evolution interrupted.");
                    }
                    long started = System.nanoTime();
                    result = evolve();
                    double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
                    totalTime += elapsed;
                    genTimes.add(elapsed);
                    sumGenTimes.add(totalTime);
                    generationRecord.put("seconds", elapsed).put("cumulativeSeconds", totalTime);
                    metrics.write(generationRecord.toString());
                    metrics.newLine();
                    metrics.flush();
                }
            }
            finish(result);
            writeTimingFiles();
            RulePopulation.write(directory.resolve("final-population.txt"), this, Arrays.asList(population.subpops[0].individuals));
            RulePopulation.write(directory.resolve("best-rules.txt"), this, Arrays.asList(bestOfGeneration));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OnlineEvoSpeak cancelled before completion.", error);
        }
    }

    private void writeTimingFiles() throws IOException {
        Path statistics = parameters.getFile(new Parameter("stat.file"), null).toPath();
        String prefix = "job." + jobSeed;
        try (BufferedWriter times = Files.newBufferedWriter(statistics.resolveSibling(prefix + ".time.csv"),
            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             BufferedWriter cumulative = Files.newBufferedWriter(statistics.resolveSibling(prefix + ".timeSumGen.csv"),
                 StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            times.write("Gen,Time");
            times.newLine();
            cumulative.write("Gen,timeSumGen");
            cumulative.newLine();
            for (int index = 0; index < genTimes.size(); index++) {
                times.write(index + "," + genTimes.get(index));
                times.newLine();
                cumulative.write(index + "," + sumGenTimes.get(index));
                cumulative.newLine();
            }
        }
    }

    void recordEvaluation(double[] baselines) {
        Individual[] individuals = population.subpops[0].individuals;
        Individual best = individuals[0];
        int valid = 0;
        for (Individual individual : individuals) {
            if (Double.isFinite(individual.fitness.fitness())) {
                valid++;
            }
            if (individual.fitness.betterThan(best.fitness)) {
                best = individual;
            }
        }
        if (valid == 0) {
            throw new IllegalStateException("No valid scheduling rules remain in generation " + generation + ".");
        }
        bestOfGeneration = (GPIndividual) best.clone();
        WeightedFitness fitness = (WeightedFitness) best.fitness;
        JSONArray objectiveValues = new JSONArray();
        for (double value : fitness.getObjectives()) {
            objectiveValues.put(Double.isFinite(value) ? value : JSONObject.NULL);
        }
        generationRecord = new JSONObject().put("runId", jobSeed).put("generation", generation).put("validIndividuals", valid)
                .put("populationSize", individuals.length).put("weightedFitness", fitness.fitness())
            .put("objectives", objectiveValues).put("weights", new JSONArray(fitness.getWeights()))
                .put("normalizationBaselines", new JSONArray(baselines)).put("bestRules", RulePopulation.rules(bestOfGeneration).json());
    }
}