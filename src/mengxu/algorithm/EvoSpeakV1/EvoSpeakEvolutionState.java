package mengxu.algorithm.EvoSpeakV1;

import ec.Individual;
import ec.gp.GPIndividual;
import org.json.JSONArray;
import org.json.JSONObject;
import yimei.jss.gp.GPRuleEvolutionState;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class EvoSpeakEvolutionState extends GPRuleEvolutionState {
    EvoSpeakConfig config;
    LlmClient client;
    Path directory;
    private JSONObject generationRecord;
    private GPIndividual bestOfGeneration;

    @Override
    public void run(int condition) {
        if (config == null || directory == null || condition != C_STARTED_FRESH) {
            throw new IllegalStateException("Run EvoSpeakMain to configure and validate the warm start before GP.");
        }
        totalTime = 0.0;
        try {
            startFresh();
            List<GPIndividual> initial = new PopulationFactory(this, config, client, directory).create();
            population.subpops[0].individuals = initial.toArray(new Individual[0]);
            if (!config.flag("evospeak.run-gp", true)) {
                output.message("Validated population generated. GP disabled by evospeak.run-gp=false.");
                return;
            }
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
                    generationRecord.put("seconds", elapsed);
                    metrics.write(generationRecord.toString());
                    metrics.newLine();
                    metrics.flush();
                }
            }
            finish(result);
            RulePopulation.write(directory.resolve("final-population.txt"), this, Arrays.asList(population.subpops[0].individuals));
            RulePopulation.write(directory.resolve("best-rules.txt"), this, Arrays.asList(bestOfGeneration));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EvoSpeakV1 cancelled before completion.", error);
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
        generationRecord = new JSONObject().put("generation", generation).put("validIndividuals", valid)
                .put("populationSize", individuals.length).put("weightedFitness", fitness.fitness())
            .put("objectives", objectiveValues).put("weights", new JSONArray(fitness.getWeights()))
                .put("normalizationBaselines", new JSONArray(baselines)).put("bestRules", RulePopulation.rules(bestOfGeneration).json());
    }
}