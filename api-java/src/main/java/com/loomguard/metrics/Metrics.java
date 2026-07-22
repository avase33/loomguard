package com.loomguard.metrics;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A lock-light latency reservoir plus a throughput counter.
 *
 * <p>Keeps the most recent {@code capacity} latency samples in a ring buffer;
 * percentiles are computed by copying and sorting on demand, so the hot write
 * path stays cheap.
 */
@Component
public class Metrics {

    private static final int CAPACITY = 4096;

    private final double[] samples = new double[CAPACITY];
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong alerts = new AtomicLong();
    private int index;
    private int filled;

    private long lastSnapshotAt = System.nanoTime();
    private long lastProcessed;

    public record Snapshot(double p50, double p99, double perSecond, long processed, long alerts) {
    }

    public void record(double latencyMs) {
        processed.incrementAndGet();
        synchronized (samples) {
            samples[index] = latencyMs;
            index = (index + 1) % CAPACITY;
            if (filled < CAPACITY) {
                filled++;
            }
        }
    }

    public void recordAlert() {
        alerts.incrementAndGet();
    }

    /** Percentiles over the reservoir and throughput since the previous call. */
    public Snapshot snapshot() {
        double[] copy;
        int n;
        synchronized (samples) {
            n = filled;
            copy = Arrays.copyOf(samples, n);
        }
        Arrays.sort(copy);

        long now = System.nanoTime();
        long total = processed.get();
        double seconds = (now - lastSnapshotAt) / 1_000_000_000.0;
        double perSecond = seconds > 0 ? (total - lastProcessed) / seconds : 0.0;
        lastSnapshotAt = now;
        lastProcessed = total;

        return new Snapshot(
                percentile(copy, 0.50),
                percentile(copy, 0.99),
                Math.round(perSecond * 10.0) / 10.0,
                total,
                alerts.get());
    }

    private static double percentile(double[] sorted, double q) {
        if (sorted.length == 0) {
            return 0.0;
        }
        int idx = (int) Math.floor(q * (sorted.length - 1));
        return Math.round(sorted[idx] * 100.0) / 100.0;
    }
}
