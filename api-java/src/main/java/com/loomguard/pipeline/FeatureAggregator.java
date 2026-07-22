package com.loomguard.pipeline;

import com.loomguard.bus.EventBus;
import com.loomguard.metrics.Metrics;
import com.loomguard.model.FeatureVector;
import com.loomguard.model.FraudScore;
import com.loomguard.model.Transaction;
import com.loomguard.scoring.FraudScorer;
import com.loomguard.store.FeatureStore;
import com.loomguard.window.CardWindows;
import com.loomguard.ws.AlertSocketHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * The consumer side of the pipeline.
 *
 * <p>Subscribes to the bus and, for every transaction: updates that card's
 * sliding window, writes the computed features to the feature store, scores
 * them, and pushes an alert to the dashboard when the decision is not
 * {@code ALLOW}. This runs off the request thread, so a slow store or a slow
 * dashboard never adds latency to checkout.
 */
@Component
public class FeatureAggregator {

    private final EventBus bus;
    private final CardWindows windows;
    private final FeatureStore store;
    private final FraudScorer scorer;
    private final AlertSocketHandler alerts;
    private final Metrics metrics;

    public FeatureAggregator(EventBus bus, CardWindows windows, FeatureStore store,
                             FraudScorer scorer, AlertSocketHandler alerts, Metrics metrics) {
        this.bus = bus;
        this.windows = windows;
        this.store = store;
        this.scorer = scorer;
        this.alerts = alerts;
        this.metrics = metrics;
    }

    @PostConstruct
    void subscribe() {
        bus.subscribe(this::handle);
    }

    void handle(Transaction txn) {
        long start = System.nanoTime();

        FeatureVector vector = windows.update(txn);
        store.put(vector);

        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
        FraudScore score = scorer.score(txn.txnId(), txn.cardId(), txn.ts(),
                vector.features(), round2(latencyMs));

        metrics.record(latencyMs);
        if (score.isAlert()) {
            metrics.recordAlert();
            alerts.broadcastAlert(score);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
