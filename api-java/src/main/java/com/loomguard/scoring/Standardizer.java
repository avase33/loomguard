package com.loomguard.scoring;

/**
 * Per-feature z-score standardisation, fitted on the training set.
 *
 * <p>Feature scales here differ by orders of magnitude ({@code sum5m} runs into
 * the thousands while {@code velocity1m} is single digits). Standardising makes
 * gradient descent converge and — just as importantly — makes the learned
 * weights comparable, which is what allows honest reason codes.
 */
public final class Standardizer {

    private final double[] means;
    private final double[] stds;

    private Standardizer(double[] means, double[] stds) {
        this.means = means;
        this.stds = stds;
    }

    public static Standardizer fit(double[][] x) {
        int n = x.length;
        int d = x[0].length;
        double[] means = new double[d];
        double[] stds = new double[d];

        for (double[] row : x) {
            for (int j = 0; j < d; j++) {
                means[j] += row[j];
            }
        }
        for (int j = 0; j < d; j++) {
            means[j] /= n;
        }
        for (double[] row : x) {
            for (int j = 0; j < d; j++) {
                double diff = row[j] - means[j];
                stds[j] += diff * diff;
            }
        }
        for (int j = 0; j < d; j++) {
            stds[j] = Math.sqrt(stds[j] / n);
            if (stds[j] < 1e-9) {
                stds[j] = 1.0; // constant feature: leave it centred at 0
            }
        }
        return new Standardizer(means, stds);
    }

    public double[] transform(double[] row) {
        double[] out = new double[row.length];
        for (int j = 0; j < row.length; j++) {
            out[j] = (row[j] - means[j]) / stds[j];
        }
        return out;
    }

    public double[][] transformAll(double[][] x) {
        double[][] out = new double[x.length][];
        for (int i = 0; i < x.length; i++) {
            out[i] = transform(x[i]);
        }
        return out;
    }
}
