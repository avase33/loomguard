package com.loomguard.scoring;

import java.util.Random;

/**
 * Labelled synthetic feature rows used to train and test the fraud model.
 *
 * <p>Encodes the domain intuition the feature store surfaces: fraud is a burst
 * of high-value cross-border charges (high {@code velocity1m}, large
 * {@code amountZScore}, several {@code distinctCountries5m}), while legitimate
 * activity is a calm trickle of small same-country purchases.
 */
public final class SyntheticData {

    public record Dataset(double[][] x, int[] y) {
    }

    private SyntheticData() {
    }

    public static Dataset generate(int n, double fraudRate, long seed) {
        Random rng = new Random(seed);
        double[][] x = new double[n][];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            boolean fraud = rng.nextDouble() < fraudRate;
            x[i] = fraud ? fraudRow(rng) : normalRow(rng);
            y[i] = fraud ? 1 : 0;
        }
        return new Dataset(x, y);
    }

    private static double[] normalRow(Random rng) {
        long count = 1 + rng.nextInt(8);
        double amount = 15 + rng.nextDouble() * 80;
        double mean = 25 + rng.nextDouble() * 55;
        double std = 3 + rng.nextDouble() * 22;
        double z = std > 0 ? (amount - mean) / std : 0;
        return new double[] {
                count,                       // count5m
                mean * count,                // sum5m
                mean,                        // mean5m
                std,                         // std5m
                1 + rng.nextInt(2),          // velocity1m
                amount,                      // amount
                z,                           // amountZScore
                1 + rng.nextInt(4),          // distinctMerchants5m
                1                            // distinctCountries5m
        };
    }

    private static double[] fraudRow(Random rng) {
        long count = 6 + rng.nextInt(11);
        double amount = 800 + rng.nextDouble() * 900;
        double mean = 40 + rng.nextDouble() * 80;
        double std = 20 + rng.nextDouble() * 40;
        double z = std > 0 ? (amount - mean) / std : 0;
        return new double[] {
                count,
                mean * count + amount,
                mean,
                std,
                5 + rng.nextInt(8),          // velocity1m: a burst
                amount,
                z,
                3 + rng.nextInt(5),
                2 + rng.nextInt(2)           // cross-border
        };
    }
}
