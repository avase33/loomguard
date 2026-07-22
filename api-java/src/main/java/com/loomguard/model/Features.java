package com.loomguard.model;

/** Sliding-window features for one card at one point in time. */
public record Features(
        long count5m,
        double sum5m,
        double mean5m,
        double std5m,
        long velocity1m,
        double amount,
        double amountZScore,
        long distinctMerchants5m,
        long distinctCountries5m) {

    /** Canonical ordering used by the model. Must match {@link #toArray()}. */
    public static final String[] NAMES = {
            "count5m", "sum5m", "mean5m", "std5m", "velocity1m",
            "amount", "amountZScore", "distinctMerchants5m", "distinctCountries5m"
    };

    public double[] toArray() {
        return new double[] {
                count5m, sum5m, mean5m, std5m, velocity1m,
                amount, amountZScore, distinctMerchants5m, distinctCountries5m
        };
    }

    /** Raw value of a feature by canonical index — used for reason codes. */
    public double valueAt(int index) {
        return toArray()[index];
    }
}
