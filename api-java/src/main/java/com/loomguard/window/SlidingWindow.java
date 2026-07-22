package com.loomguard.window;

import com.loomguard.model.Features;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * A stateful sliding-window aggregator for one card.
 *
 * <p>Rolling {@code sum} and {@code sum of squares} are maintained
 * <b>incrementally</b> on every push and eviction, so {@code count / mean / std
 * / z-score} are O(1) per event regardless of how many events the window holds.
 * Distinct merchant and country counts use reference-counted maps (increment on
 * insert, decrement on evict, {@code distinct = map.size()}), also O(1)
 * amortised.
 *
 * <p>The only non-constant step is {@code velocity1m}, which walks backwards
 * from the newest event until it crosses the 60 s boundary — that touches at
 * most the events inside the one-minute window, which is exactly the quantity
 * being reported.
 *
 * <p>Instances are <b>not</b> thread-safe on their own; {@code CardWindows}
 * guards each window with its own monitor so different cards never contend.
 */
public final class SlidingWindow {

    public static final long WINDOW_5M_MILLIS = 300_000L;
    public static final long WINDOW_1M_MILLIS = 60_000L;

    private record Event(long ts, double amount, String merchant, String country) {
    }

    private final Deque<Event> events = new ArrayDeque<>();
    private final Map<String, Integer> merchants = new HashMap<>();
    private final Map<String, Integer> countries = new HashMap<>();
    private double sum;
    private double sumSquares;

    /** Ingests one transaction and returns the features computed at its timestamp. */
    public Features push(long ts, double amount, String merchant, String country) {
        sum += amount;
        sumSquares += amount * amount;
        merchants.merge(merchant, 1, Integer::sum);
        countries.merge(country, 1, Integer::sum);
        events.addLast(new Event(ts, amount, merchant, country));
        evictOlderThan(ts);
        return compute(ts, amount);
    }

    private void evictOlderThan(long now) {
        while (!events.isEmpty() && now - events.peekFirst().ts() > WINDOW_5M_MILLIS) {
            Event e = events.removeFirst();
            sum -= e.amount();
            sumSquares -= e.amount() * e.amount();
            decrement(merchants, e.merchant());
            decrement(countries, e.country());
        }
    }

    private static void decrement(Map<String, Integer> counts, String key) {
        Integer c = counts.get(key);
        if (c == null) {
            return;
        }
        if (c <= 1) {
            counts.remove(key);
        } else {
            counts.put(key, c - 1);
        }
    }

    private Features compute(long now, double amount) {
        int n = events.size();
        double mean = n > 0 ? sum / n : 0.0;
        // population variance, clamped to absorb floating-point cancellation
        double variance = n > 0 ? Math.max(sumSquares / n - mean * mean, 0.0) : 0.0;
        double std = Math.sqrt(variance);
        double z = std > 1e-9 ? (amount - mean) / std : 0.0;

        long velocity = 0;
        var it = events.descendingIterator();
        while (it.hasNext()) {
            if (now - it.next().ts() <= WINDOW_1M_MILLIS) {
                velocity++;
            } else {
                break;
            }
        }

        return new Features(
                n,
                round2(sum),
                round2(mean),
                round2(std),
                velocity,
                round2(amount),
                round2(z),
                merchants.size(),
                countries.size());
    }

    public int size() {
        return events.size();
    }

    private static double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }
}
