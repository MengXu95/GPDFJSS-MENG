package mengxu.algorithm.MAPElites.FuzzySystem;

/**
 * Gaussian membership function for fuzzy variables
 * μ_X(x) = exp(-(x-c_X)^2 / (2*σ_X^2))
 */
public class FuzzyMembershipFunction {
    
    private final String linguisticTerm;
    private final double center;      // c_X: center of the Gaussian
    private final double sigma;       // σ_X: spread of the Gaussian
    
    public FuzzyMembershipFunction(String linguisticTerm, double center, double sigma) {
        if (linguisticTerm == null || linguisticTerm.trim().isEmpty()) {
            throw new IllegalArgumentException("A linguistic term is required");
        }
        if (!Double.isFinite(center) || !Double.isFinite(sigma) || sigma <= 0.0) {
            throw new IllegalArgumentException("Gaussian center must be finite and sigma must be finite and positive");
        }
        this.linguisticTerm = linguisticTerm;
        this.center = center;
        this.sigma = sigma;
    }
    
    /**
     * Calculate membership degree for a given value
     * @param x Input value
     * @return Membership degree [0, 1]
     */
    public double getMembership(double x) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("Membership input must be finite");
        }
        double distance = (x - center) / sigma;
        return Math.exp(-0.5 * distance * distance);
    }
    
    public String getLinguisticTerm() {
        return linguisticTerm;
    }
    
    public double getCenter() {
        return center;
    }
    
    public double getSigma() {
        return sigma;
    }
    
    @Override
    public String toString() {
        return String.format("%s(c=%.2f, σ=%.2f)", linguisticTerm, center, sigma);
    }
}
