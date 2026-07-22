package com.loomguard.model;

/** A card's feature row as written to the feature store. */
public record FeatureVector(String cardId, long ts, Features features) {
}
