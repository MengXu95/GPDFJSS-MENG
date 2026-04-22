package mengxu.algorithm.multitaskHeuristicLearning;

import org.apache.commons.math3.linear.*;

public class NonlinearModel {

    private RealVector alpha;

    public NonlinearModel(){
        this.alpha = null;
    }

    public void printModel(){
        System.out.println("Coefficients:");
        for (int i = 0; i < alpha.getDimension(); i++) {
            System.out.println("alpha" + (i + 1) + " = " + alpha.getEntry(i));
        }
    }

    public double estimateF3withF1andF2(double f1, double f2){
        double f3 = alpha.getEntry(0) * f1 + alpha.getEntry(1) * f2 + alpha.getEntry(2) * f1*f1 +
                alpha.getEntry(3) * f2*f2 + alpha.getEntry(4) * f1*f2;
        return f3;
    }

    public double estimateF1withF2andF3(double f2, double f3){
        // Coefficients of the quadratic equation
        double A = alpha.getEntry(2);
        double B = alpha.getEntry(0) + alpha.getEntry(4) * f2;
        double C = alpha.getEntry(1) * f2 + alpha.getEntry(3) * f2 * f2 - f3;

        // Calculate the discriminant
        double discriminant = B * B - 4 * A * C;

        if (discriminant < 0) {
            // No real solutions
            return Double.POSITIVE_INFINITY;
        }

        // Calculate the two possible solutions
        double sqrtDiscriminant = Math.sqrt(discriminant);
        double f1_solution1 = (-B + sqrtDiscriminant) / (2 * A);
        double f1_solution2 = (-B - sqrtDiscriminant) / (2 * A);

        // Return the positive solution
        if (f1_solution1 > 0 && f1_solution2 > 0) {
            return Math.min(f1_solution1, f1_solution2);  // Return the smaller positive value if both are positive
        } else if (f1_solution1 > 0) {
            return f1_solution1;  // Return the positive solution
        } else if (f1_solution2 > 0) {
            return f1_solution2;  // Return the positive solution
        } else {
            return Double.POSITIVE_INFINITY;  // No positive solution exists
        }
    }

    public double estimateF2withF1andF3(double f1, double f3){
        // Coefficients of the quadratic equation
        double A = alpha.getEntry(3);
        double B = alpha.getEntry(1) + alpha.getEntry(4) * f1;
        double C = alpha.getEntry(2) * f1 * f1 + alpha.getEntry(0) * f1 - f3;

        // Calculate the discriminant
        double discriminant = B * B - 4 * A * C;

        if (discriminant < 0) {
            // No real solutions
            return Double.POSITIVE_INFINITY;
        }

        // Calculate the two possible solutions
        double sqrtDiscriminant = Math.sqrt(discriminant);
        double f2_solution1 = (-B + sqrtDiscriminant) / (2 * A);
        double f2_solution2 = (-B - sqrtDiscriminant) / (2 * A);

        // Return the positive solution
        if (f2_solution1 > 0 && f2_solution2 > 0) {
            return Math.min(f2_solution1, f2_solution2);  // Return the smaller positive value if both are positive
        } else if (f2_solution1 > 0) {
            return f2_solution1;  // Return the positive solution
        } else if (f2_solution2 > 0) {
            return f2_solution2;  // Return the positive solution
        } else {
            return Double.POSITIVE_INFINITY;  // No positive solution exists
        }
    }

    public void learnModel(double[] f1, double[] f2, double[] f3) {
        // Example data
//        double[] f1 = {1.0, 2.0, 3.0, 4.0};
//        double[] f2 = {2.0, 3.0, 4.0, 5.0};
//        double[] f3 = {2.8, 6.1, 11.4, 18.7}; // Target values

        int n = f1.length;

        // Prepare the design matrix X (5 features: f1, f2, f1^2, f2^2, f1*f2)
        double[][] X = new double[n][5];
        for (int i = 0; i < n; i++) {
            X[i][0] = f1[i];
            X[i][1] = f2[i];
            X[i][2] = f1[i] * f1[i];
            X[i][3] = f2[i] * f2[i];
            X[i][4] = f1[i] * f2[i];
        }

        // Prepare the target vector y
        double[] y = f3;

        // Convert to RealMatrix and RealVector
        RealMatrix matrixX = new Array2DRowRealMatrix(X);
        RealVector vectorY = new ArrayRealVector(y);

        // Compute X^T * X
        RealMatrix matrixXT = matrixX.transpose();
        RealMatrix matrixXTX = matrixXT.multiply(matrixX);

        // Compute X^T * y
        RealVector vectorXTY = matrixXT.operate(vectorY);

        // Solve for alpha (normal equation: (X^T * X) * alpha = X^T * y)
        DecompositionSolver solver = new LUDecomposition(matrixXTX).getSolver();
        this.alpha = solver.solve(vectorXTY);

        // Output the coefficients
//        System.out.println("Coefficients:");
//        for (int i = 0; i < alpha.getDimension(); i++) {
//            System.out.println("alpha" + (i + 1) + " = " + alpha.getEntry(i));
//        }
    }
}
