package com.loomguard.scoring;

/**
 * Binary logistic regression, trained with batch gradient descent — written
 * from scratch, no ML dependency.
 *
 * <p>For weights {@code w}, bias {@code b} and standardised features {@code z}:
 *
 * <pre>
 *   p = sigmoid(w · z + b)
 *   loss = -1/n Σ [ y·log(p) + (1-y)·log(1-p) ]  + λ/2·|w|²
 *   ∂loss/∂w = 1/n Σ (p - y)·z  + λ·w
 *   ∂loss/∂b = 1/n Σ (p - y)
 * </pre>
 *
 * L2 regularisation keeps any single feature from dominating, which matters
 * because the reason codes are read straight off the weights.
 */
public final class LogisticModel {

    private final double[] weights;
    private double bias;

    public LogisticModel(int dimensions) {
        this.weights = new double[dimensions];
    }

    public static double sigmoid(double x) {
        // numerically stable on both tails
        if (x >= 0) {
            double e = Math.exp(-x);
            return 1.0 / (1.0 + e);
        }
        double e = Math.exp(x);
        return e / (1.0 + e);
    }

    /** Fits the model in place. {@code z} must already be standardised. */
    public LogisticModel fit(double[][] z, int[] y, int epochs, double learningRate, double l2) {
        int n = z.length;
        int d = weights.length;
        double[] gradW = new double[d];

        for (int epoch = 0; epoch < epochs; epoch++) {
            java.util.Arrays.fill(gradW, 0.0);
            double gradB = 0.0;

            for (int i = 0; i < n; i++) {
                double p = predictStandardized(z[i]);
                double error = p - y[i];
                for (int j = 0; j < d; j++) {
                    gradW[j] += error * z[i][j];
                }
                gradB += error;
            }

            for (int j = 0; j < d; j++) {
                weights[j] -= learningRate * (gradW[j] / n + l2 * weights[j]);
            }
            bias -= learningRate * (gradB / n);
        }
        return this;
    }

    public double predictStandardized(double[] z) {
        double sum = bias;
        for (int j = 0; j < weights.length; j++) {
            sum += weights[j] * z[j];
        }
        return sigmoid(sum);
    }

    /** Signed per-feature contribution {@code w_j · z_j} to the log-odds. */
    public double[] contributions(double[] z) {
        double[] out = new double[weights.length];
        for (int j = 0; j < weights.length; j++) {
            out[j] = weights[j] * z[j];
        }
        return out;
    }

    public double[] weights() {
        return weights.clone();
    }

    public double bias() {
        return bias;
    }
}
