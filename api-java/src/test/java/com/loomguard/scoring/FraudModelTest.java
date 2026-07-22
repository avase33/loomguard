package com.loomguard.scoring;

import com.loomguard.model.Features;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudModelTest {

    private static FraudScorer scorer;

    static final Features CALM = new Features(
            3, 150.0, 50.0, 10.0, 1, 42.0, -0.8, 2, 1);

    static final Features FRAUD = new Features(
            11, 5400.0, 90.0, 40.0, 9, 1450.0, 4.2, 5, 3);

    @BeforeAll
    static void trainOnce() {
        scorer = new FraudScorer();
        scorer.train();
    }

    @Test
    void sigmoidIsStableAtExtremes() {
        assertTrue(LogisticModel.sigmoid(-1000) >= 0.0 && LogisticModel.sigmoid(-1000) < 1e-6);
        assertTrue(LogisticModel.sigmoid(1000) > 1 - 1e-6 && LogisticModel.sigmoid(1000) <= 1.0);
        assertEquals(0.5, LogisticModel.sigmoid(0.0), 1e-9);
    }

    @Test
    void separatesCalmFromFraud() {
        double calm = scorer.probability(CALM);
        double fraud = scorer.probability(FRAUD);

        assertTrue(calm < 0.5, "calm scored " + calm);
        assertTrue(fraud > 0.5, "fraud scored " + fraud);
        assertEquals("ALLOW", FraudScorer.decisionFor(calm));
        assertTrue(List.of("REVIEW", "BLOCK").contains(FraudScorer.decisionFor(fraud)));
    }

    @Test
    void producesReasonCodesForFraud() {
        List<String> reasons = scorer.reasons(FRAUD, 3);
        assertFalse(reasons.isEmpty(), "fraud should surface reasons");
        // every reason is rendered as name=value using a canonical feature name
        for (String reason : reasons) {
            assertTrue(reason.contains("="), reason);
            String name = reason.substring(0, reason.indexOf('='));
            assertTrue(List.of(Features.NAMES).contains(name), "unknown feature: " + name);
        }
    }

    @Test
    void holdoutAccuracyIsHigh() {
        var holdout = SyntheticData.generate(1200, 0.25, 999L);
        int correct = 0;
        for (int i = 0; i < holdout.x().length; i++) {
            double[] row = holdout.x()[i];
            Features f = new Features(
                    (long) row[0], row[1], row[2], row[3], (long) row[4],
                    row[5], row[6], (long) row[7], (long) row[8]);
            int predicted = scorer.probability(f) >= 0.5 ? 1 : 0;
            if (predicted == holdout.y()[i]) {
                correct++;
            }
        }
        double accuracy = correct / (double) holdout.x().length;
        assertTrue(accuracy > 0.90, "holdout accuracy was " + accuracy);
    }
}
