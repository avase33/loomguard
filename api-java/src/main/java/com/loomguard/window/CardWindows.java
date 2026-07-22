package com.loomguard.window;

import com.loomguard.model.FeatureVector;
import com.loomguard.model.Transaction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one {@link SlidingWindow} per card.
 *
 * <p>Lookup is lock-free via {@link ConcurrentHashMap}; mutation is guarded by
 * the individual window's monitor, so two different cards never contend and a
 * single card's updates stay linearisable. This is the concurrency shape that
 * lets many virtual threads update the store at once.
 */
@Component
public class CardWindows {

    private final Map<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    public FeatureVector update(Transaction txn) {
        SlidingWindow window = windows.computeIfAbsent(txn.cardId(), k -> new SlidingWindow());
        synchronized (window) {
            var features = window.push(txn.ts(), txn.amount(), txn.merchant(), txn.country());
            return new FeatureVector(txn.cardId(), txn.ts(), features);
        }
    }

    public int activeCards() {
        return windows.size();
    }

    public void clear() {
        windows.clear();
    }
}
