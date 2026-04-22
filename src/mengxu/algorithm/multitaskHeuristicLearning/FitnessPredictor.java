package mengxu.algorithm.multitaskHeuristicLearning;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

public class FitnessPredictor {

    private double[] coefficients;

    public FitnessPredictor(){
        this.coefficients = null;
    };

    public void trainModel(double[][][] inputData){
        // Count the number of individuals
        int numIndividuals = inputData.length;
        int numInputsPerIndividual = inputData[0].length;

        // Prepare the dataset for training
        int totalInputPairs = numIndividuals * numInputsPerIndividual * (numInputsPerIndividual - 1);
        double[][] inputs = new double[totalInputPairs][3]; // (w_j, w_i, fit_i)
        double[] targets = new double[totalInputPairs];     // target: fit_j

        int index = 0;
        // Loop through each individual
        for (int ind = 0; ind < numIndividuals; ind++) {
            for (int i = 0; i < numInputsPerIndividual; i++) {
                for (int j = 0; j < numInputsPerIndividual; j++) {
                    if (i != j) {
                        // (w_j, w_i, fit_i)
                        inputs[index][0] = inputData[ind][j][0]; // w_j
                        inputs[index][1] = inputData[ind][i][0]; // w_i
                        inputs[index][2] = inputData[ind][i][1]; // fit_i

                        // Target: fit_j
                        targets[index] = inputData[ind][j][1];   // fit_j
                        index++;
                    }
                }
            }
        }

        // Train a Polynomial Regression model using OLSMultipleLinearRegression
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.setNoIntercept(true); // Set to false if you want to include intercept

        // Prepare the input for the regression model
        RealMatrix matrix = MatrixUtils.createRealMatrix(inputs);
        regression.newSampleData(targets, matrix.getData());

        // Output coefficients for the polynomial model
        this.coefficients = regression.estimateRegressionParameters();
//        System.out.println("Estimated coefficients: ");
//        for (double coeff : coefficients) {
//            System.out.println(coeff);
//        }
    }

    public void main(String[] args) {
        // Sample data for multiple individuals
        double[][][] inputData = {
                {
                        {0.75, 1.2}, // (w1, fit1) for individual 1
                        {0.85, 1.5}, // (w2, fit2) for individual 1
                },
                {
                        {0.75, 1.1}, // (w1, fit1) for individual 2
                        {0.85, 1.4}, // (w2, fit2) for individual 2
                },
                // Add more individuals here...
        };

        this.trainModel(inputData);

        // Test the model with a new input: (w_j, w_i, fit_i)
        double[] newInput = {0.85, 0.75, 1.2};  // Example input
        double predictedFitness = predict(coefficients, newInput);
        System.out.println("Predicted fit_j: " + predictedFitness);
    }

    // Method to predict using the coefficients
    private static double predict(double[] coefficients, double[] newInput) {
        double prediction = 0;
        for (int i = 0; i < coefficients.length; i++) {
            prediction += coefficients[i] * newInput[i];
        }
        return prediction;
    }
}
