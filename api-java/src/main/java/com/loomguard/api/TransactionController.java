package com.loomguard.api;

import com.loomguard.bus.EventBus;
import com.loomguard.metrics.Metrics;
import com.loomguard.model.FeatureVector;
import com.loomguard.model.FraudScore;
import com.loomguard.model.Transaction;
import com.loomguard.scoring.FraudScorer;
import com.loomguard.store.FeatureStore;
import com.loomguard.window.CardWindows;
import com.loomguard.ws.AlertSocketHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The checkout-facing API. Every handler runs on a virtual thread.
 *
 * <p>{@code POST /api/transactions} does the minimum on the request path:
 * publish to the bus, read the card's last known features, score. Window
 * updates and stores happen asynchronously in {@code FeatureAggregator}, which
 * keeps the response fast and predictable under load.
 */
@RestController
@RequestMapping("/api")
public class TransactionController {

    private final EventBus bus;
    private final FeatureStore store;
    private final FraudScorer scorer;
    private final CardWindows windows;
    private final Metrics metrics;
    private final AlertSocketHandler alerts;

    public TransactionController(EventBus bus, FeatureStore store, FraudScorer scorer,
                                 CardWindows windows, Metrics metrics, AlertSocketHandler alerts) {
        this.bus = bus;
        this.store = store;
        this.scorer = scorer;
        this.windows = windows;
        this.metrics = metrics;
        this.alerts = alerts;
    }

    @PostMapping("/transactions")
    public FraudScore ingest(@RequestBody Transaction body) {
        long start = System.nanoTime();
        Transaction txn = body.normalized(System.currentTimeMillis());

        bus.publish(txn);

        // Score against the freshest features we already hold for this card.
        // A brand-new card has no history yet, so it scores on this event alone.
        var features = store.get(txn.cardId())
                .map(FeatureVector::features)
                .orElseGet(() -> windows.update(txn).features());

        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
        return scorer.score(txn.txnId(), txn.cardId(), txn.ts(), features,
                Math.round(latencyMs * 100.0) / 100.0);
    }

    @GetMapping("/features/{cardId}")
    public ResponseEntity<FeatureVector> features(@PathVariable String cardId) {
        return store.get(cardId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Metrics.Snapshot s = metrics.snapshot();
        return Map.of(
                "processed", s.processed(),
                "alerts", s.alerts(),
                "ingestPerSec", s.perSecond(),
                "p50LatencyMs", s.p50(),
                "p99LatencyMs", s.p99(),
                "activeCards", windows.activeCards(),
                "storedCards", store.size(),
                "droppedEvents", bus.dropped(),
                "dashboardClients", alerts.clientCount());
    }
}
