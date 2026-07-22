package com.loomguard.model;

import java.util.List;

/** The scoring result returned to the caller and pushed to the dashboard. */
public record FraudScore(
        String txnId,
        String cardId,
        long ts,
        double probability,
        String decision,
        List<String> reasons,
        double latencyMs) {

    public boolean isAlert() {
        return !"ALLOW".equals(decision);
    }
}
