package com.loomguard.store;

import com.loomguard.model.FeatureVector;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Default feature store: a concurrent map. No external service required. */
@Component
@Profile("!cassandra")
public class InMemoryFeatureStore implements FeatureStore {

    private final Map<String, FeatureVector> rows = new ConcurrentHashMap<>();

    @Override
    public void put(FeatureVector vector) {
        rows.put(vector.cardId(), vector);
    }

    @Override
    public Optional<FeatureVector> get(String cardId) {
        return Optional.ofNullable(rows.get(cardId));
    }

    @Override
    public long size() {
        return rows.size();
    }
}
