package com.loomguard.window;

import com.loomguard.model.Features;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowTest {

    private static final long SEC = 1000L;

    @Test
    void computesCountSumAndDistincts() {
        SlidingWindow w = new SlidingWindow();
        w.push(0, 100.0, "m1", "US");
        w.push(SEC, 200.0, "m2", "US");
        Features f = w.push(2 * SEC, 300.0, "m1", "CA");

        assertEquals(3, f.count5m());
        assertEquals(600.0, f.sum5m());
        assertEquals(200.0, f.mean5m());
        assertEquals(2, f.distinctMerchants5m());
        assertEquals(2, f.distinctCountries5m());
    }

    @Test
    void evictsEventsOlderThanFiveMinutes() {
        SlidingWindow w = new SlidingWindow();
        w.push(0, 100.0, "m1", "US");
        w.push(10 * SEC, 100.0, "m1", "US");

        // 400 s later both earlier events are outside the 300 s window
        Features f = w.push(400 * SEC, 50.0, "m2", "GB");

        assertEquals(1, f.count5m());
        assertEquals(50.0, f.sum5m());
        assertEquals(1, f.distinctMerchants5m());
        assertEquals(1, f.distinctCountries5m());
    }

    @Test
    void velocityCountsOnlyTheLastMinute() {
        SlidingWindow w = new SlidingWindow();
        w.push(0, 10.0, "m", "US");
        w.push(30 * SEC, 10.0, "m", "US");
        w.push(50 * SEC, 10.0, "m", "US");

        // at t=120s only this event is within 60 s of the previous one at 50 s
        Features f = w.push(120 * SEC, 10.0, "m", "US");

        assertEquals(1, f.velocity1m());
        assertEquals(4, f.count5m()); // all four are still inside 300 s
    }

    @Test
    void zScoreIsZeroWithoutVariance() {
        SlidingWindow w = new SlidingWindow();
        w.push(0, 100.0, "m", "US");
        Features f = w.push(SEC, 100.0, "m", "US");

        assertEquals(0.0, f.std5m());
        assertEquals(0.0, f.amountZScore());
    }

    @Test
    void zScoreFlagsAnOutlier() {
        SlidingWindow w = new SlidingWindow();
        for (int i = 0; i < 9; i++) {
            w.push(i * SEC, 100.0, "m", "US");
        }
        Features f = w.push(9 * SEC, 1000.0, "m", "US");

        assertTrue(f.amountZScore() > 2.0, "expected a large positive z, got " + f.amountZScore());
    }

    @Test
    void tenThousandEventsStayFast() {
        SlidingWindow w = new SlidingWindow();
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            w.push(i * 10L, 100.0, "m", "US");
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        // documents the O(1)-per-event design; generous bound, not a CI gate
        assertTrue(millis < 1000, "10k pushes took " + millis + "ms");
    }
}
