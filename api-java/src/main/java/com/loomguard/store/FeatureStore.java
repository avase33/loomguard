package com.loomguard.store;

import com.loomguard.model.FeatureVector;

import java.util.Optional;

/**
 * Low-latency read store for computed features.
 *
 * <p>The in-memory implementation is the default; the Cassandra implementation
 * is activated by the {@code cassandra} profile. Reads sit on the checkout hot
 * path, so both are point lookups by card id.
 */
public interface FeatureStore {

    void put(FeatureVector vector);

    Optional<FeatureVector> get(String cardId);

    long size();
}
