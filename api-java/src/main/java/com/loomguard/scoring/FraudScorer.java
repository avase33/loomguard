package com.loomguard.scoring;

import com.loomguard.model.Features;
import com.loomguard.model.FraudScore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Trains the logistic model at startup and scores live feature rows.
 *
 * <p>Reason codes are derived from the model itself: each feature's signed
 * contribution to the log-odds is {@code w_j · z_j}, so the features pushing a
 * transaction toward fraud are exactly the largest positive contributions.
 */
@Component
public class FraudScorer {

    private static final Logger log = LoggerFactory.getLogger(FraudScorer.class);

    public static final double REVIEW_THRESHOLD = 0.50;
    public static final double BLOCK_THRESHOLD = 0.85;

    private Standardizer standardizer;
    private LogisticModel model;

    @PostConstruct
    public void train() {
        var data = SyntheticData.generate(4000, 0.25, 7L);
        standardizer = Standardizer.fit(data.x());
        double[][] z = standardizer.transformAll(data.x());
        model = new LogisticModel(Features.NAMES.length).fit(z, data.y(), 400, 0.5, 1e-4);
        log.info("fraud model trained: {} features, bias={}", Features.NAMES.length,
                String.format("%.3f", model.bias()));
    }

    public double probability(Features features) {
        double[] z = standardizer.transform(features.toArray());
        return model.predictStandardized(z);
    }

    public static String decisionFor(double probability) {
        if (probability >= BLOCK_THRESHOLD) {
            return "BLOCK";
        }
        if (probability >= REVIEW_THRESHOLD) {
            return "REVIEW";
        }
        return "ALLOW";
    }

    /** Top features pushing this transaction toward fraud, as {@code name=value}. */
    public List<String> reasons(Features features, int limit) {
        double[] raw = features.toArray();
        double[] z = standardizer.transform(raw);
        double[] contributions = model.contributions(z);

        record Contribution(int index, double value) {
        }
        List<Contribution> ranked = new ArrayList<>();
        for (int j = 0; j < contributions.length; j++) {
            if (contributions[j] > 0) {
                ranked.add(new Contribution(j, contributions[j]));
            }
        }
        ranked.sort(Comparator.comparingDouble(Contribution::value).reversed());

        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, ranked.size()); i++) {
            int idx = ranked.get(i).index();
            out.add(Features.NAMES[idx] + "=" + trim(raw[idx]));
        }
        return out;
    }

    public FraudScore score(String txnId, String cardId, long ts, Features features, double latencyMs) {
        double p = probability(features);
        return new FraudScore(txnId, cardId, ts, round4(p), decisionFor(p), reasons(features, 3), latencyMs);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
