package mengxu.util;

import ec.Individual;

import java.util.ArrayList;
import java.util.List;

public class PopAnalyse {
    public PopAnalyse(){}

    public void fitnessDistribution(Individual[] individuals){
        ArrayList<Double> fitnesses = new ArrayList<>();
        for(Individual ind: individuals){
            double fit = ind.fitness.fitness();
            if(fit >= Double.MAX_VALUE || fit >= Double.POSITIVE_INFINITY){

            }
            else{
                fitnesses.add(fit);
            }
        }
        double best = calculateMin(fitnesses);
        double mean = calculateMean(fitnesses);
        double stdDev = calculateStandardDeviation(fitnesses);
        System.out.println("Best: " + best);
        System.out.println("Mean: " + mean);
        System.out.println("Standard Deviation: " + stdDev);
    }

    public double calculateMin(ArrayList<Double> fitnesses) {
        if (fitnesses == null || fitnesses.isEmpty()) {
            throw new IllegalArgumentException("Fitness list cannot be null or empty.");
        }

        double min = Double.POSITIVE_INFINITY;
        for (double fitness : fitnesses) {
            if(min > fitness){
                min = fitness;
            }
        }
        return min;
    }

    public double calculateMean(ArrayList<Double> fitnesses) {
        if (fitnesses == null || fitnesses.isEmpty()) {
            throw new IllegalArgumentException("Fitness list cannot be null or empty.");
        }

        double sum = 0;
        for (double fitness : fitnesses) {
            sum += fitness;
        }
        return sum / fitnesses.size();
    }


    public double calculateStandardDeviation(ArrayList<Double> fitnesses) {
        if (fitnesses == null || fitnesses.isEmpty()) {
            throw new IllegalArgumentException("Fitness list cannot be null or empty.");
        }

        double mean = calculateMean(fitnesses);
        double sumOfSquaredDifferences = 0;

        for (double fitness : fitnesses) {
            double difference = fitness - mean;
            sumOfSquaredDifferences += difference * difference;
        }

        double variance = sumOfSquaredDifferences / fitnesses.size();
        return Math.sqrt(variance);
    }

}
